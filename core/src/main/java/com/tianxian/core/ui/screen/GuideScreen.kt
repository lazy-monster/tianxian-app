package com.tianxian.core.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianxian.core.ui.theme.SerifSC
import com.tianxian.core.ui.theme.Theme
import com.tianxian.core.ui.theme.heroWash

/**
 * The in-app handbook: why the curriculum is shaped the way it is, the recommended day-to-day
 * workflow for someone whose goal is reading webnovels, and honest notes about the tools
 * (review buttons, TTS quirks, backups). Reached from Settings.
 */
@Composable
fun GuideScreen(appName: String, onBack: () -> Unit) {
    val x = Theme.x
    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        item {
            ScreenHeader("$appName's Path · 指南",
                "How to use $appName when the goal is reading webnovels — what to learn, in what order, and what to skip.", onBack)
        }

        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(x.heroWash()).padding(18.dp)) {
                Text("Do I need the sounds, or just the meanings?", fontFamily = SerifSC,
                    fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = x.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Learn the sound *with* the meaning — but only to recognition level. You never need " +
                    "perfect pronunciation to read. You do need to know roughly how a character sounds, " +
                    "for three reasons:\n\n" +
                    "1.  Memory. A character anchored to a spoken syllable has two retrieval paths instead " +
                    "of one. Shape-only memorisation works for the first few hundred characters, then " +
                    "collapses — too many characters look alike.\n\n" +
                    "2.  The system is phonetic. Around 80% of characters are meaning-part + sound-part. " +
                    "Once you know 青 is qīng, you can guess 清, 晴, 请 and 情 — that one regularity is " +
                    "worth more than any mnemonic, and it's invisible if you skip sounds.\n\n" +
                    "3.  Speed. Fluent readers hear the words as they read. Readers who learned shapes " +
                    "silently hit a wall: every sentence stays a decoding puzzle instead of becoming speech.\n\n" +
                    "So: when a trial plays a word, listen — that's the whole investment. Don't drill " +
                    "tone production, don't do pronunciation exercises, don't let sound-perfectionism " +
                    "slow your vocabulary down. Hear it, recognise it, move on.",
                    color = x.textSoft, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }

        item { GuideSection("The recommended path", x.gold,
            "1.  One afternoon on Pinyin & Tones (Learn → Pinyin). Goal: recognise the four tones " +
            "and know what the letters sound like. Don't memorise the tables — they'll absorb on " +
            "their own through every word you hear later.\n\n" +
            "2.  Radical trials, a batch every day or two (Learn → Radicals). The 214 radicals are " +
            "the alphabet of meaning; with them, characters become assembled pieces instead of " +
            "random strokes. Run these alongside step 3, not before it.\n\n" +
            "3.  The character track (Learn → Vocabulary → Guided cultivation). One group of 20 a " +
            "day is a strong pace; the trial seals the group into your review deck automatically.\n\n" +
            "4.  Reviews every single day, *before* new material. Ten focused minutes beats an hour " +
            "of cramming — the schedule does the remembering for you.\n\n" +
            "5.  The Genre Lexicon early (Learn → Cultivation). Webnovel vocabulary is brutally " +
            "repetitive: 修炼, 灵气, 丹田, 长老 appear on every page. A few dozen genre terms buy " +
            "more reading comfort than a hundred general words.") }

        item { GuideSection("Start reading absurdly early", x.cinnabar,
            "Import your first novel after roughly 300–500 words (HSK 1–2), not when you feel " +
            "\"ready\" — nobody ever feels ready. The first chapters are heavy tapping; that's the " +
            "method, not a failure:\n\n" +
            "•  Tap any word for its meaning. If you've met it before, add it to your deck (+).\n" +
            "•  Mark character and place names as known — they're labels, not vocabulary.\n" +
            "•  Re-read the same chapter the next day. Webnovels repeat themselves; your second " +
            "pass will feel shockingly easier.\n\n" +
            "The coverage bar is honest: 80% known feels like a fight, 90% is workable with the " +
            "tap-to-look-up, 95% is comfortable, 98% disappears into the story. Your first novel " +
            "is the hardest thing you'll ever read — the second is half the work.") }

        item { GuideSection("The daily loop", x.jade,
            "Reviews first (Review tab) → one trial group (radicals or vocabulary) → read a little. " +
            "On busy days, do only the reviews — protecting the streak of reviews matters more than " +
            "new material. If daily reviews grow past what's comfortable, stop adding new groups " +
            "for a few days; the deck will settle.") }

        item { GuideSection("Grading yourself honestly", x.gold,
            "Again — couldn't recall it. The card returns in minutes and relearns. No shame: " +
            "lapses are the system working.\n" +
            "Hard — recalled, but slowly or partially.\n" +
            "Good — recalled with normal effort. Your default button.\n" +
            "Easy — instant, automatic. Use sparingly: grading Easy out of optimism is the one " +
            "way to break spaced repetition.\n\n" +
            "The recall-target slider in Settings trades workload for safety: higher means more " +
            "frequent reviews. 92% is a sane default; lower it if the daily pile feels heavy.") }

        item { GuideSection("About the audio", x.cinnabar,
            "Some characters have multiple readings (多音字): 行 is xíng in 行走 but háng in 银行; " +
            "还 is hái and huán. A device voice given a lone character can only guess its most " +
            "common reading — so when the reading you're studying is a secondary one, $appName " +
            "speaks a common word that carries it instead (还 huán plays 归还). If you hear a " +
            "two-character word after tapping 🔊 on a single character, that's deliberate: it's " +
            "the only way to make the audio match the listed pinyin.\n\n" +
            "Voice quality itself comes from your device's text-to-speech engine (usually Google " +
            "Speech Services); installing or updating its Chinese voice data in system settings " +
            "improves every sound in the app.") }

        item { GuideSection("Housekeeping", x.jade,
            "Backup: Settings → Backup exports everything (cards, history, known characters, book " +
            "list and track progress) to one JSON file. Turn on automatic backups to a folder and " +
            "forget about it.\n\n" +
            "Updating: install updates from the same source every time (the GitHub release APK). " +
            "Debug/test builds are signed differently and install as a separate app rather than " +
            "updating the release one. If Android ever refuses an update outright, export a " +
            "backup, uninstall, install the new APK, and import — two minutes, nothing lost.") }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun GuideSection(title: String, accent: Color, body: String) {
    val x = Theme.x
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(x.surface).padding(18.dp)) {
        Text(title, fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = accent)
        Spacer(Modifier.height(8.dp))
        Text(body, color = x.textSoft, fontSize = 14.sp, lineHeight = 21.sp)
    }
}
