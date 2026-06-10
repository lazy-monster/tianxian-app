# Development guide

Everything for building, releasing, and working on Scholar. Users don't need any of this —
see the [README](../README.md).

## Tech stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin 2.0 · Jetpack Compose (Material 3) |
| Architecture | Single activity · Compose Navigation · manual DI |
| Storage | Room (your progress) + bundled read-only SQLite (content) |
| SRS | Native FSRS-6 |
| Ebooks | Native EPUB/TXT/MOBI parsers · PDFBox-Android · ML Kit OCR |
| Audio | On-device Chinese text-to-speech |
| Widgets / reminders | Jetpack Glance · WorkManager |
| Min / target SDK | 26 / 35 |

## Building from source

You need **JDK 17** and the **Android SDK** (via Android Studio or the command-line tools).

### Android Studio (simplest)

Open the project folder, let Gradle sync, and press **Run**. Android Studio provides the
SDK and generates the Gradle wrapper automatically.

### Command line

```bash
# Android SDK command-line tools, then:
export ANDROID_HOME=$HOME/android-sdk
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle wrapper            # generate the wrapper (jar is not committed)
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Debug builds install
**side-by-side** with the release app (as `com.scholar.app.debug`), so testing never clobbers
a real install.

## Release build (signed APK)

A *release* APK is what users install. It needs a signing key — create one once:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias scholar
```

Keep `release.jks` **private and backed up** (it's git-ignored). Android only accepts updates
signed with the same key as the installed app: lose the keystore and every user must
uninstall/reinstall; leak it and anyone can sign a malicious "update". Then either:

- **Locally** — create a git-ignored `keystore.properties` in the repo root:

  ```properties
  storeFile=release.jks
  storePassword=…
  keyAlias=scholar
  keyPassword=…
  ```

  and run `./gradlew assembleRelease`. The signed APK lands in
  `app/build/outputs/apk/release/app-release.apk`.

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

## Cutting a release

Bump `versionCode`/`versionName` in `app/build.gradle.kts`, then tag and push:

```bash
git tag v0.9.0 && git push origin v0.9.0
```

`.github/workflows/release.yml` builds the signed release APK and publishes it as a GitHub
**Release** named after the tag, with auto-generated notes, so the repo's Download link always
points at the newest build.

## Continuous integration

- `.github/workflows/build.yml` — builds a **debug** APK on every push and uploads it as the
  `scholar-debug-apk` artifact, so you can grab a build without a local toolchain.
- `.github/workflows/release.yml` — on a `v*` tag, builds the **signed release** APK and
  publishes it as a GitHub Release (and fails rather than publish an unsigned one).

## The bundled data

The dictionary database (`app/src/main/assets/content.db`, ~37 MB) is compiled from open
datasets by `data-build/build_content_db.py`. **It is already built and committed** — you
only need the script to rebuild or change the data:

```bash
cd data-build
pip install pycccedict wordfreq
python build_content_db.py
```

The example-sentence bank ships as a *separate* `app/src/main/assets/sentences.db` (~5 MB) so
the main `content.db` is never touched. Rebuild it with `python data-build/build_sentences_db.py`.
Source datasets and licenses are listed in the [README](../README.md#open-data).

## Project structure

```
.
├─ app/src/main/
│  ├─ assets/content.db              # bundled dictionary / characters / strokes / HSK / genre
│  ├─ assets/sentences.db            # Tatoeba translated example sentences (separate, optional)
│  ├─ java/com/scholar/app/
│  │  ├─ data/content/               # read-only content DB + pinyin conversion + gloss cleanup
│  │  ├─ data/segment/               # maximal-match word segmenter
│  │  ├─ data/user/                  # Room: cards, reviews, known chars, books
│  │  ├─ data/repo/                  # repositories
│  │  ├─ srs/                        # native FSRS-6 + scheduler
│  │  ├─ reader/ingest/              # EPUB / TXT / MOBI / PDF / OCR parsers
│  │  ├─ audio/                      # text-to-speech
│  │  ├─ ui/screen/                  # Learn, Today, Reader, Review, Dictionary, Guide, …
│  │  ├─ ui/onboarding/              # first-run intro
│  │  ├─ ui/Breakthrough.kt          # cultivation breakthrough overlay
│  │  └─ ui/theme/                   # themes + accent washes
│  └─ AndroidManifest.xml
├─ data-build/                       # Python pipeline that builds content.db
└─ .github/workflows/
   ├─ build.yml                      # CI: debug APK on every push
   └─ release.yml                    # CI: signed release APK on a v* tag
```
