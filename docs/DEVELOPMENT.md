# Development guide

Everything for building, releasing, and working on Tianxian (天仙) and its Japanese twin
Tensen. Users don't need any of this — see the [README](../README.md).

## Modules

One codebase ships two apps from a shared library:

| Module | Type | What it holds |
|---|---|---|
| `:core` | Android library (`com.tianxian.core`) | All logic and UI: SRS, reader, dictionary, screens, theming, widgets, backup, updater. Carries no app name or release repo. |
| `:app-zh` | Application (`com.tianxian.app`) | **Tianxian** — the Chinese app. Identity (`AppConfig`), launcher branding, bundled Chinese `content.db`. |
| `:app-ja` | Application (`com.tensen.app`) | **Tensen** — the Japanese app. Same `:core`; ships a *stub* `content.db` until the Japanese data + language layer land (see "Japanese, in progress"). |

Per-app differences flow through `core/.../di/AppConfig.kt`, built in each module's
`Application` (`TianxianApp` / `TensenApp`).

## Tech stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin 2.0 · Jetpack Compose (Material 3) |
| Architecture | `:core` library + two app shells · single activity · Compose Navigation · manual DI |
| Storage | Room (your progress) + bundled read-only SQLite (content) |
| SRS | Native FSRS-6 |
| Ebooks | Native EPUB/TXT/MOBI parsers · PDFBox-Android · ML Kit OCR |
| Audio | On-device text-to-speech |
| Widgets / reminders | Jetpack Glance · WorkManager |
| Min / target SDK | 26 / 35 |

## Building from source

You need **JDK 17** and the **Android SDK** (via Android Studio or the command-line tools).

### Android Studio (simplest)

Open the project folder, let Gradle sync, and run the **app-zh** (or **app-ja**) configuration.
Android Studio provides the SDK and generates the Gradle wrapper automatically.

### Command line

```bash
# Android SDK command-line tools, then:
export ANDROID_HOME=$HOME/android-sdk
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle wrapper                 # generate the wrapper (jar is not committed)
./gradlew :app-zh:assembleDebug    # Tianxian
./gradlew :app-ja:assembleDebug    # Tensen (stub content)
```

The Tianxian APK is written to `app-zh/build/outputs/apk/debug/app-zh-debug.apk`. Debug builds
install **side-by-side** with the release app (as `com.tianxian.app.debug`), so testing never
clobbers a real install.

## Release build (signed APK)

A *release* APK is what users install. It needs a signing key — create one once:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias tianxian
```

Keep `release.jks` **private and backed up** (it's git-ignored). Android only accepts updates
signed with the same key as the installed app: lose the keystore and every user must
uninstall/reinstall; leak it and anyone can sign a malicious "update". Then either:

- **Locally** — create a git-ignored `keystore.properties` in the repo root:

  ```properties
  storeFile=release.jks
  storePassword=…
  keyAlias=tianxian
  keyPassword=…
  ```

  and run `./gradlew :app-zh:assembleRelease`. The signed APK lands in
  `app-zh/build/outputs/apk/release/app-zh-release.apk`.

- **In CI** — set four repository **Secrets** (Settings → Secrets and variables → Actions):
  `KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

> A *local* `assembleRelease` without a keystore falls back to **debug signing** — fine for
> testing on your own device. The **release workflow refuses to publish unsigned**: a
> debug-signed "release" carries a fresh throwaway key on every CI run, so users would hit
> *"package conflicts with existing package"* on every update. Every published release must be
> signed with the one keystore — the workflow prints the certificate's SHA-256 in its log so
> this is easy to verify across releases. The signing config reads from `keystore.properties`
> or the matching `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
> environment variables.

> **Rebrand note:** Tianxian uses a new `applicationId` (`com.tianxian.app`). Android treats it
> as a different app from the old `com.scholar.app`, so existing installs can't auto-update to it —
> users migrate with a one-time manual install. Their backups still restore: import accepts the
> legacy `"scholar"` id (see `AppConfig.acceptedBackupIds`).

## Cutting a release

Both apps ship from this one repo (**Option B**). The **tag prefix picks the app**; just tag and
push — no manual version bump:

```bash
git tag v0.11.1   && git push origin v0.11.1     # Tianxian (:app-zh) — bare "v" or "zh-v"
git tag ja-v0.1.0 && git push origin ja-v0.1.0   # Tensen   (:app-ja)
```

`.github/workflows/release.yml` reads the version *from the tag*, injects it into the build
(`-PappVersionName`), builds the matching signed module, and publishes a GitHub **Release** named
after the tag with the APK attached as `<slug>-v<version>.apk` (`tianxian-…` / `tensen-…`).

- **Version name comes from the tag**, so the built APK can never disagree with its release tag.
  (That mismatch is exactly what broke v0.11.0: the tag said 0.11.0 but `build.gradle.kts` still
  said 0.10.5, so the updater nagged forever. The hard-coded `versionName` is now only a
  local/debug default, overridden in CI.)
- **Version code is `1000 + CI run number`** — monotonic, above all historical hand-set codes, no
  manual bumping.
- The in-app updater polls `AppConfig.updateRepo` (the shared repo for both apps) and selects its
  own release by the `<slug>-…apk` asset name (`Updater.appApk`), so one app never offers the
  other's build.

> The first corrected Tianxian release must be **v0.11.1** (v0.11.0 is taken and shipped the wrong
> version): existing installs report 0.10.5, see v0.11.1 > 0.10.5, update once, and then match — the
> phantom-update loop ends.

## Continuous integration

- `.github/workflows/build.yml` — builds **debug** APKs for both apps on every push and uploads
  them as the `tianxian-debug-apk` and `tensen-debug-apk` artifacts.
- `.github/workflows/release.yml` — on a `v*` / `zh-v*` / `ja-v*` tag, builds the **signed release**
  APK for the matching app (version from the tag) and publishes it as a GitHub Release (and fails
  rather than publish an unsigned one).

## The bundled data

The Chinese dictionary database (`app-zh/src/main/assets/content.db`, ~37 MB) is compiled from
open datasets by `data-build/build_content_db.py`. **It is already built and committed** — you
only need the script to rebuild or change the data:

```bash
cd data-build
pip install pycccedict wordfreq
python build_content_db.py
```

The example-sentence bank ships as a *separate* `app-zh/src/main/assets/sentences.db` (~5 MB) so
the main `content.db` is never touched. Rebuild it with `python data-build/build_sentences_db.py`.
Source datasets and licenses are listed in the [README](../README.md#open-data).

## Japanese, in progress

`:app-ja` (Tensen) is a working shell on top of `:core`. It builds and launches, but ships an
empty-schema **stub** `content.db`, so the dictionary, levels and home card are blank. Reaching
parity is the remaining workstream:

- Build a Japanese `content.db`/`sentences.db` from **JMdict/JMnedict**, **KANJIDIC2**,
  **KanjiVG**, JLPT N5–N1 lists, and **Tatoeba ja→en** (new `data-build` pipeline → `app-ja`).
- Adapt the language layer in `:core` (today Chinese-shaped): `ContentStore` schema, `Gloss.kt`
  (JMdict senses), `MaxMatchSegmenter` (kana), retire `Pinyin.kt` (furigana instead), JLPT
  labels, and inject a Japanese ML Kit OCR recognizer. These branch on `AppConfig.language`.

## Project structure

```
.
├─ core/src/main/                     # shared library — com.tianxian.core
│  ├─ java/com/tianxian/core/
│  │  ├─ data/content/                # read-only content DB + pinyin conversion + gloss cleanup
│  │  ├─ data/segment/                # maximal-match word segmenter
│  │  ├─ data/user/                   # Room: cards, reviews, known chars, books
│  │  ├─ data/repo/                   # repositories
│  │  ├─ di/                          # AppGraph, AppConfig, GraphHolder
│  │  ├─ srs/                         # native FSRS-6 + scheduler
│  │  ├─ reader/ingest/               # EPUB / TXT / MOBI / PDF / OCR parsers
│  │  ├─ audio/                       # text-to-speech
│  │  ├─ update/                      # in-app updater
│  │  ├─ widget/                      # Glance home-screen widgets
│  │  ├─ MainActivity.kt              # single activity (shared)
│  │  ├─ ui/screen/                   # Learn, Today, Reader, Review, Dictionary, Guide, …
│  │  ├─ ui/onboarding/               # first-run intro
│  │  ├─ ui/Breakthrough.kt           # cultivation breakthrough overlay
│  │  └─ ui/theme/                    # themes + accent washes
│  ├─ res/                            # themes, fonts, widget xml, launcher icon
│  └─ AndroidManifest.xml             # shared permissions, widget receivers, FileProvider
├─ app-zh/src/main/                   # Tianxian — com.tianxian.app
│  ├─ assets/{content.db,sentences.db}  # bundled Chinese data
│  ├─ java/com/tianxian/app/TianxianApp.kt
│  ├─ res/values/strings.xml          # app_name 天仙
│  └─ AndroidManifest.xml             # application + launcher activity
├─ app-ja/src/main/                   # Tensen — com.tensen.app
│  ├─ assets/content.db               # stub (Japanese data pending)
│  ├─ java/com/tensen/app/TensenApp.kt
│  ├─ res/values/strings.xml
│  └─ AndroidManifest.xml
├─ data-build/                        # Python pipeline that builds content.db / sentences.db
└─ .github/workflows/
   ├─ build.yml                       # CI: debug APKs on every push
   └─ release.yml                     # CI: signed release APK on a v* tag
```
