<div align="center">

# 天仙 Tianxian

**Learn to read Chinese webnovels. Free, offline, no account.**

学 · 拼 · 木 · 书

[![Latest release](https://img.shields.io/github/v/release/lazy-monster/tianxian-app?label=download&sort=semver)](https://github.com/lazy-monster/tianxian-app/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/lazy-monster/tianxian-app/total)](https://github.com/lazy-monster/tianxian-app/releases)
[![Build](https://github.com/lazy-monster/tianxian-app/actions/workflows/build.yml/badge.svg)](https://github.com/lazy-monster/tianxian-app/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](#license)

### → [**Download the APK**](https://github.com/lazy-monster/tianxian-app/releases/latest) ←

No ads, no tracking, no subscription. If Tianxian helps you, a ⭐ is appreciated.

</div>

---

> **天仙 — two apps, one codebase.** **Tianxian** (the Mandarin reading of 天仙, "Heavenly
> Immortal") is the Chinese reading app described below. **Tensen** — the Japanese reading of the
> same characters — is the parallel Japanese app built from the shared `:core` library; its
> Japanese content is still in progress. See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for the
> module layout (`:core`, `:app-zh`, `:app-ja`).

## Why Tianxian

Most apps train you for the HSK exam. Tianxian trains you for the moment you open a *xianxia*
novel and just… read it. Dictionary, curriculum, spaced repetition, and an ebook reader in one
offline app — and your progress is measured in characters you can actually read, not exam levels.

## Install

1. Download `tianxian-<version>.apk` from the [latest release](https://github.com/lazy-monster/tianxian-app/releases/latest).
2. Open it on your phone (Android 8.0+). Allow "install from unknown sources" when asked — that's
   normal for apps outside the Play Store.
3. Done. After that, **Settings → Updates** checks for new releases and installs them in place —
   no need to come back here.

> Your progress lives only on your device. Back it up to a single file any time from
> **Settings → Backup** — restoring it on a new phone picks up exactly where you left off.

## What's inside

🗺️ **A guided path, gamified as cultivation.** Pinyin and tones → the 214 radicals → vocabulary
in groups of 20, each gated by a trial that quizzes shape, sound, and meaning. Pass to *break
through*: the words seal into your review deck and your rank climbs the realm ladder, from
炼气 Qi Refining toward 渡劫 Tribulation. Comfortable native reading sits near the peak.

📖 **A reader for your own books.** Import EPUB, TXT, MOBI, PDF, comics (CBZ), or a photo of a
page. Tap any word for its meaning, add it to your deck, or mark it known — known words fade so
your eye goes to what's new. Each book shows how much of it you can already read. Read-aloud
narrates chapters with the current sentence highlighted, hands-free.

🃏 **Spaced repetition done right.** A modern FSRS-6 scheduler keeps reviews short — minutes a
day, not hours. Review ahead when you're keen; get a gentle reminder when you're not.

📚 **A real dictionary.** 122,000+ entries searchable in Chinese, pinyin, or English, with audio
for everything, stroke-order animations and finger-tracing for the most common ~2,000 characters,
and a lexicon of the genre vocabulary (修炼, 丹田, 长老…) that webnovels live on.

🏠 **On your home screen.** Optional widgets show your rank, reviews due, and a rotating
character of the moment. Six themes, from ink-wash dark to rice-paper light.

## How to learn with it

The short version:

1. Spend one afternoon on **Pinyin & Tones** — recognition, not perfection.
2. Run the **radical** and **vocabulary trials** daily; do your **reviews** first, always.
3. Import a novel *far* earlier than feels reasonable and tap your way through it.

The full recommended workflow — what to learn in what order, when to start reading, how to grade
yourself — lives **inside the app**: *Settings → Tianxian's Path (指南)*.

## Open data

Tianxian is built on open datasets, bundled and ready — nothing downloads at runtime.

| Source | License | Provides |
|---|---|---|
| [CC-CEDICT](https://cc-cedict.org/) | CC BY-SA 4.0 | dictionary entries |
| [Make Me a Hanzi](https://github.com/skishore/makemeahanzi) | Arphic | character decomposition & strokes |
| [complete-hsk-vocabulary](https://github.com/drkameleon/complete-hsk-vocabulary) | MIT | HSK levels & meanings |
| [wordfreq](https://github.com/rspeer/wordfreq) | MIT | frequency ordering |
| [Tatoeba](https://tatoeba.org/) | CC BY 2.0 FR | translated example sentences |
| [LXGW WenKai](https://github.com/lxgw/LxgwWenKaiGB) | SIL OFL 1.1 | the bundled Kai reading font |

## Good to know

- Audio uses your device's Chinese text-to-speech voice (usually Google Speech Services) —
  updating its Chinese voice data in system settings improves every sound in the app.
- MOBI support covers the common case; convert HUFF/CDIC or KF8/AZW3 files to EPUB first.
- Scanned-page OCR (ML Kit) uses its bundled Chinese model.
- No novels are included — you bring your own books.

## For developers

Kotlin · Jetpack Compose · Room · a from-scratch FSRS-6. Build, release, and data-pipeline
docs live in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## License

Application code is **MIT**. Bundled data keeps its upstream licenses (table above); CC-CEDICT
is CC BY-SA 4.0, which governs redistribution of the dictionary database.

---

<div align="center">
<sub>Built for readers who'd rather be reading. 加油！</sub>
</div>
