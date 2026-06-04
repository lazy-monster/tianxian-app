<div align="center">

# Scholar

**A free, offline-first Android app for learning to read Chinese — built for web novels.**

Pinyin and tones first, characters by their components, frequency-ordered vocabulary,
spaced repetition from day one, and a friction-free reader for your own ebooks. Progress
is measured in the characters you can actually read, not exam levels.

学 · 拼 · 木 · 书

[![Latest release](https://img.shields.io/github/v/release/lazy-monster/scholar-app?label=download&sort=semver)](https://github.com/lazy-monster/scholar-app/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/lazy-monster/scholar-app/total)](https://github.com/lazy-monster/scholar-app/releases)
[![Build](https://github.com/lazy-monster/scholar-app/actions/workflows/build.yml/badge.svg)](https://github.com/lazy-monster/scholar-app/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](#license)

### → [**Download the latest APK**](https://github.com/lazy-monster/scholar-app/releases/latest) ←

No account, no ads, no tracking — works fully offline. If Scholar helps you, a ⭐ on the repo is appreciated.

</div>

---

## Install

1. Grab `scholar-<version>.apk` from the [**latest release**](https://github.com/lazy-monster/scholar-app/releases/latest).
2. Open it on your Android phone (Android 8.0 / API 26 or newer).
3. Allow "install from unknown sources" if prompted — Scholar isn't on the Play Store, so your
   phone asks once. The APK is built in the open by GitHub Actions ([release workflow](.github/workflows/release.yml)).

That's it. Everything runs on-device; back up your progress to a single file any time from
**Settings → Backup**.

---

## What it is

Most apps optimise for the HSK exam. Scholar optimises for the moment you can open a
*xianxia* or *wuxia* web novel and read it. It bundles a full dictionary, a structured
curriculum, a spaced-repetition system, and a reader into one app that works completely
offline. No account, no subscription, no ads.

## Features

**A real curriculum (not just tools).** A guided path from zero:

- **Pinyin & Tones** — the sound system, every example tappable to hear it.
- **Radicals & Components** — all 214 Kangxi radicals with meanings, each expandable to
  show the common characters built from it. A **flashcard** mode plus **Cultivation trials**:
  the radicals split into batches (size configurable, default 20) and drilled in *all four
  directions* — shape→meaning, meaning→shape, **hear→shape**, and **shape→reading** — so each
  trial tests recognition, recall *and* pronunciation. Score 85%+ to *break through* and unlock
  the next trial. Each radical shows its everyday teaching name (氵 *三点水*, 宀 *宝盖头*)
  alongside the formal reading; every character screen breaks the character into its components
  (好 = 女 *woman* + 子 *child*), so you learn structure, not strokes.
- **Vocabulary by Level** — HSK 1 → 9 word lists, ordered by real-world frequency. Mine words
  freely one-tap, or take the **guided cultivation track**: learn 20 at a time (sounds and
  meanings), pass a trial that quizzes all four directions including audio, and the group is
  *sealed into your review deck* and the next group unlocks. A structured way to measure progress
  on top of free mining.
- **Handwriting** — stroke-order animation plus finger-tracing practice for ~2,000 of the
  most common characters.
- **Cultivation** — your learning progress *is* your rank. Characters known and words mastered
  (FSRS) from **review**, plus radicals and character groups cleared on the **study tracks**,
  blend into a cultivation base on the realm ladder (炼气 Qi Refining → 渡劫 Tribulation). Both
  paths count — passing a trial advances your rank immediately, so a day spent studying moves you
  forward even when you have no time to review. Crossing a sub-stage or realm boundary triggers a
  **breakthrough** screen. A long climb where comfortable native-novel reading sits near the peak.

**A reader for your own books.** Import EPUB, TXT, MOBI, PDF, CBZ comics, or a photo of a page.
Tap any word for its reading and meaning, mine it into spaced repetition, or mark it known. Words
you already know fade so your eye goes to what's new. Each book shows what percentage of its
characters you can already read. The reader is fully customizable — choose font (serif, sans,
Kai 楷, or mono), text size, line spacing, and a reading theme (app / ink / paper / sepia / black)
— and it renders **images**, not just text: illustrated novels' inline pictures, scanned PDFs and
photographed pages (with the OCR'd text still tappable beneath), and CBZ picture/comic books page by
page. A **read-aloud** mode speaks the chapter with the current sentence highlighted, auto-scrolling
and auto-advancing through chapters at an adjustable speed, and it resumes where you left off. The
controls auto-hide as you read, and scrolling past the end of a chapter flows straight into the next.

**Spaced repetition done right.** A from-scratch implementation of **FSRS-6**, the modern
scheduler that needs ~20–30% fewer reviews than classic SM-2 for the same retention. When you're
caught up you can **review ahead** to drill the next cards early (they reschedule normally), and a
gentle daily **reminder** nudges you when reviews are ready or you've gone a day without reviewing
*or* studying — cultivation trials count, so a study-only day won't trigger the "you haven't
cultivated today" nudge. Your full progress (cards, history, known characters, and all track
progress) exports to one portable JSON file from **Settings → Backup**, with optional auto-backup.

**On your home screen.** Two optional widgets (Jetpack Glance): a *character of the moment* that
rotates and opens the character when tapped, and a *cultivation status* widget showing your rank,
characters known, and reviews due (or the time until the next). The in-app home also surfaces a
fresh character each time, tap for another.

**A dictionary that works.** 122,143 CC-CEDICT entries, searchable in Chinese, pinyin, or
English, with tone-marked readings and on-device audio for any word.

**Designed to be pleasant.** An "Ink & Jade" theme — ink-wash dark or rice-paper light — with a
serif reader and an optional bundled LXGW WenKai (霞鹜文楷) Kai brush-script reading font. See
`xianxia-app-prototype.html` for the visual reference.

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

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it on a device
with "install from unknown sources" enabled.

### Release build (signed APK)

A *release* APK is what you distribute to users. It needs a signing key — create one once:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias scholar
```

Keep `release.jks` **private** (it's git-ignored). Then either:

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

> Without a keystore, `assembleRelease` still works but falls back to **debug signing** — fine for
> testing, not for sharing. The signing config reads from `keystore.properties` or the matching
> `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` environment variables.

### Cutting a release

Tag a commit and push the tag — that's the whole flow:

```bash
git tag v0.8.0 && git push origin v0.8.0
```

`.github/workflows/release.yml` then builds the signed release APK and publishes it as a GitHub
**Release** named after the tag, with auto-generated notes, so the
[Download](https://github.com/lazy-monster/scholar-app/releases/latest) link always points at the
newest build. Bump `versionCode`/`versionName` in `app/build.gradle.kts` before tagging.

### Continuous integration

- `.github/workflows/build.yml` — builds a **debug** APK on every push and uploads it as the
  `scholar-debug-apk` artifact, so you can grab a build without a local toolchain.
- `.github/workflows/release.yml` — on a `v*` tag, builds the **signed release** APK and publishes
  it as a GitHub Release.

## The data is real and open

The dictionary database (`app/src/main/assets/content.db`, ~37 MB) is compiled from open
datasets by `data-build/build_content_db.py`. **It is already built and committed** — you
only need to run the script to rebuild or change the data:

```bash
cd data-build
pip install pycccedict wordfreq
python build_content_db.py
```

| Source | License | Provides |
|---|---|---|
| [CC-CEDICT](https://cc-cedict.org/) | CC BY-SA 4.0 | 122,143 dictionary entries |
| [Make Me a Hanzi](https://github.com/skishore/makemeahanzi) | Arphic Public License | decomposition, radicals, stroke data |
| [complete-hsk-vocabulary](https://github.com/drkameleon/complete-hsk-vocabulary) | MIT | HSK 2.0 + 3.0 levels and meanings |
| [wordfreq](https://github.com/rspeer/wordfreq) | MIT | frequency ordering and reading coverage |
| [Tatoeba](https://tatoeba.org/) | CC BY 2.0 FR | 64,658 translated example sentences (`sentences.db`) |
| [LXGW WenKai](https://github.com/lxgw/LxgwWenKaiGB) | SIL OFL 1.1 | bundled Kai reading font (`res/font/lxgw_wenkai.ttf`, subset to the app's character set; license in `third-party/`) |
| 214 Kangxi radicals | curated | radical meanings (in `content.db`) and everyday teaching names (in `RadicalNames.kt`) for the components lessons |

The example-sentence bank ships as a *separate* `app/src/main/assets/sentences.db` (~5 MB) so
the main `content.db` is never touched. Rebuild it with `python data-build/build_sentences_db.py`.

## Project structure

```
.
├─ app/src/main/
│  ├─ assets/content.db              # bundled dictionary / characters / strokes / HSK / genre
│  ├─ assets/sentences.db            # Tatoeba translated example sentences (separate, optional)
│  ├─ java/com/scholar/app/
│  │  ├─ data/content/               # read-only content DB + pinyin conversion
│  │  ├─ data/segment/               # maximal-match word segmenter
│  │  ├─ data/user/                  # Room: cards, reviews, known chars, books
│  │  ├─ data/repo/                  # repositories
│  │  ├─ srs/                        # native FSRS-6 + scheduler
│  │  ├─ reader/ingest/              # EPUB / TXT / MOBI / PDF / OCR parsers
│  │  ├─ audio/                      # text-to-speech
│  │  ├─ ui/screen/                  # Learn, Today, Reader, Review, Dictionary, …
│  │  ├─ ui/onboarding/              # first-run intro
│  │  ├─ ui/Breakthrough.kt          # cultivation breakthrough overlay
│  │  └─ ui/theme/                   # Ink & Jade theme
│  └─ AndroidManifest.xml
├─ data-build/                       # Python pipeline that builds content.db
└─ .github/workflows/
   ├─ build.yml                      # CI: debug APK on every push
   └─ release.yml                    # CI: signed release APK on a v* tag
```

## Roadmap

- Pronunciation trainer with per-syllable audio and tone drills
- Grammar lessons
- Stroke-by-stroke scoring for handwriting
- HUFF/CDIC MOBI and KF8/AZW3 support (currently: convert to EPUB first)
- FSRS weight optimisation from your own review history

## Known limitations

- Prebuilt APKs are published on the [Releases](https://github.com/lazy-monster/scholar-app/releases)
  page, not committed to the tree. The Gradle wrapper jar is also not committed; Android Studio or
  `gradle wrapper` generates it.
- Handwriting and stroke data cover the ~2,000 most frequent characters (plus all HSK),
  not every character in the dictionary.
- MOBI support covers the common PalmDOC case; convert HUFF/CDIC or KF8/AZW3 files to EPUB.
- ML Kit downloads its Chinese OCR model on first use.

## License

Application code: **MIT**. Bundled data keeps its upstream licenses (see the table above);
note that CC-CEDICT is **CC BY-SA 4.0**, which governs redistribution of `content.db`. No
copyrighted novels are included — you supply your own reading material.

---

<div align="center">
<sub>Built for readers who'd rather be reading. 加油！</sub>
</div>
