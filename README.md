<div align="center">

# Scholar

**A free, offline-first Android app for learning to read Chinese — built for web novels.**

Pinyin and tones first, characters by their components, frequency-ordered vocabulary,
spaced repetition from day one, and a friction-free reader for your own ebooks. Progress
is measured in the characters you can actually read, not exam levels.

学 · 拼 · 木 · 书

</div>

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
  show the common characters built from it. Every character screen breaks the character
  into its components (好 = 女 *woman* + 子 *child*), so you learn structure, not strokes.
- **Vocabulary by Level** — HSK 1 → 9 word lists, ordered by real-world frequency, with
  one-tap mining into your review deck.
- **Handwriting** — stroke-order animation plus finger-tracing practice for ~2,000 of the
  most common characters.
- **Genre Path** — the cultivation realm ladder (炼气 → 渡劫) explained tier by tier, plus
  curated xianxia/wuxia vocabulary grouped by theme.

**A reader for your own books.** Import EPUB, TXT, MOBI, PDF, or a photo of a page. Tap any
word for its reading and meaning, mine it into spaced repetition, or mark it known. Words
you already know fade so your eye goes to what's new. Each book shows what percentage of
its characters you can already read.

**Spaced repetition done right.** A from-scratch implementation of **FSRS-6**, the modern
scheduler that needs ~20–30% fewer reviews than classic SM-2 for the same retention.

**A dictionary that works.** 122,143 CC-CEDICT entries, searchable in Chinese, pinyin, or
English, with tone-marked readings and on-device audio for any word.

**Designed to be pleasant.** An "Ink & Jade" theme — ink-wash dark or rice-paper light —
with a serif reader. See `prototype.html` for the visual reference.

## Tech stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin 2.0 · Jetpack Compose (Material 3) |
| Architecture | Single activity · Compose Navigation · manual DI |
| Storage | Room (your progress) + bundled read-only SQLite (content) |
| SRS | Native FSRS-6 |
| Ebooks | Native EPUB/TXT/MOBI parsers · PDFBox-Android · ML Kit OCR |
| Audio | On-device Chinese text-to-speech |
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

### Continuous integration

`.github/workflows/build.yml` builds a debug APK on every push and uploads it as the
`scholar-debug-apk` artifact, so you can grab a build without a local toolchain.

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
| 214 Kangxi radicals | curated | radical meanings for the components lessons |

## Project structure

```
.
├─ app/src/main/
│  ├─ assets/content.db              # bundled dictionary / characters / strokes / HSK / genre
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
│  │  └─ ui/theme/                   # Ink & Jade theme
│  └─ AndroidManifest.xml
├─ data-build/                       # Python pipeline that builds content.db
└─ .github/workflows/build.yml       # CI: builds the APK
```

## Roadmap

- Pronunciation trainer with per-syllable audio and tone drills
- Grammar lessons
- Stroke-by-stroke scoring for handwriting
- HUFF/CDIC MOBI and KF8/AZW3 support (currently: convert to EPUB first)
- FSRS weight optimisation from your own review history

## Known limitations

- No APK is committed — build it with any method above. The Gradle wrapper jar is not
  committed; Android Studio or `gradle wrapper` generates it.
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
