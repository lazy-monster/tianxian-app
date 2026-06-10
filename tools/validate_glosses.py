#!/usr/bin/env python3
"""Decisive gloss validator — runs the EXACT Gloss + hskWords-fallback logic over every row of
the bundled content.db and asserts that no learner-facing meaning leads with cross-reference
NOISE ("surname Du", "variant of …", "see …", "CL:…", "abbr. for …").

Faithful Python mirror of:
  - app/src/main/java/com/scholar/app/data/content/Gloss.kt
  - the hsk_word fallback in ContentStore.hskWords (merged dict_entry, then cross-ref target).

Two sense tiers (this is the key design point):
  • NOISE   — surname/variant/see/CL/abbr/pr cross-references. Never a learnable answer; if it's
              all an entry has, fall back to a better source. hasRealSense == has a non-noise sense.
  • GRAMMAR — "classifier for…", "particle…", "interjection…", bare "(adverb of degree)" etc.
              For a function word this description IS the meaning, so it is KEPT (just demoted
              below concrete senses). Not a failure when it leads.

The Kotlin unit test guards the real Gloss code; this script guards the DATA across every row.
Run:  python3 tools/validate_glosses.py   (exit 0 clean, 1 with offending rows)
"""
import sqlite3, sys, os, re

DB = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "content.db")

NOISE_PREFIXES = (
    "surname ", "variant of ", "old variant of ", "archaic variant of ", "erhua variant",
    "euphemistic variant", "see ", "also written", "abbr. for ", "cl:", "taiwan pr", "also pr",
    "used in ", "used as ",          # cross-references to a compound, no standalone meaning
)
GRAMMAR_PREFIXES = (
    "classifier for", "measure word", "particle ", "conjunction ", "auxiliary ", "modal particle",
    "structural particle", "aspect particle", "sentence-final", "interjection ",
    "radical ", "kangxi radical", "(old)", "(archaic)", "(literary)", "(bound form)",
    "(dialect)", "(onom.)", "(interj", "(particle", "(grammatical", "(prefix", "(suffix",
)

def senses(raw):                       # NB: do NOT split on '|' — it is part of trad|simp refs
    out = []
    for part in raw.replace('；', '/').replace(';', '/').split('/'):
        t = part.strip().strip(',').strip('，').strip()
        if t:
            out.append(t)
    return out

def _unannotated(sense):                 # strip leading "(...)" groups; MAY return "" (pure annot.)
    t = sense.strip()
    while t.startswith("("):
        close = t.find(")")
        if close < 0:
            break
        t = t[close + 1:].strip()
    return t

def strip_annotation(sense):             # for DISPLAY: keep original if stripping empties it
    return _unannotated(sense) or sense

def _classify_text(sense):               # text used to classify a sense by prefix
    u = _unannotated(sense)              # text outside any leading "(…)" annotation
    if u:
        return u
    s = sense.strip()                    # pure parenthetical → look inside, e.g. "(used in …)"
    return s[1:-1].strip() if s.startswith("(") and s.endswith(")") else s

def is_noise(sense):                     # cross-reference with no standalone meaning
    return _classify_text(sense).lower().startswith(NOISE_PREFIXES)

def is_grammar(sense):
    if is_noise(sense):
        return False
    if _unannotated(sense) == "":        # pure parenthetical that isn't noise, e.g. "(adverb of degree)"
        return True
    return _classify_text(sense).lower().startswith(GRAMMAR_PREFIXES)

def ordered(raw):                                  # concrete, then grammar, then noise
    s = senses(raw)
    concrete = [x for x in s if not is_noise(x) and not is_grammar(x)]
    grammar  = [x for x in s if is_grammar(x)]
    noise    = [x for x in s if is_noise(x)]
    return concrete + grammar + noise

def has_real_sense(raw):                           # a non-noise sense exists (grammar counts)
    return any(not is_noise(x) for x in senses(raw))

def primary(raw, max_len=40):
    o = ordered(raw)
    if not o:
        return raw[:max_len]
    return strip_annotation(o[0])[:max_len].strip()

def core(raw):
    real = [x for x in ordered(raw) if not is_noise(x)] or ordered(raw)   # concrete-first, no noise
    if not real:
        return raw
    parts = []
    for sense in real[:2]:
        syn = [x.strip() for x in strip_annotation(sense).replace('，', ',').split(',') if x.strip()][:3]
        parts.append(", ".join(syn))
    c = "; ".join(parts)
    return c if c.strip() else real[0]

# Extract the simplified word a cross-reference points at: "variant of 擋|挡[dang3]" -> "挡".
_REF = re.compile(r'(?:variant of|old variant of|archaic variant of|see|abbr\. for)\s+([^,\[;/]+)', re.I)
def crossref_target(raw):
    for s in senses(raw):
        if is_noise(s):
            m = _REF.search(s)
            if m:
                ref = m.group(1).strip()
                if '|' in ref:           # trad|simp -> simplified
                    ref = ref.split('|')[-1]
                ref = ref.split('[')[0].strip()
                if ref:
                    return ref
    return None

# ── the trial label / card back exactly as the UI builds them ─────────────────
def short_gloss(meaning):
    return primary(meaning) or meaning[:40]

def leaks(label):
    return label.strip() == "" or is_noise(label)

def merged(cur, word):
    cur.execute("SELECT group_concat(gloss, ' / ') FROM dict_entry WHERE simplified=?", (word,))
    r = cur.fetchone()
    return (r[0] or "") if r else ""

def resolved_meaning(cur, word, raw):
    """Mirror of ContentStore.hskWords fallback chain."""
    if has_real_sense(raw):
        return raw
    m = merged(cur, word)
    if has_real_sense(m):
        return m
    tgt = crossref_target(m or raw)
    if tgt:
        mt = merged(cur, tgt)
        if has_real_sense(mt):
            return mt
    return raw

def main():
    con = sqlite3.connect(DB); cur = con.cursor()
    failures, data_gaps = [], []

    # A leak is a BUG only if a real meaning was available but noise was shown anyway. If no source
    # has a real sense (e.g. CEDICT only knows 大厦 as "(used in the names of grand buildings …)"),
    # showing that honest annotation is the best we can do — a data gap, not a regression.
    def check(surface, key, raw, final, shown):
        if leaks(shown):
            (failures if has_real_sense(final) else data_gaps).append((surface, key, repr(raw), repr(shown)))

    cur.execute("SELECT word, meaning FROM hsk_word")
    hsk = cur.fetchall()
    for word, meaning in hsk:
        m = resolved_meaning(cur, word, meaning or "")
        check("hsk label", word, meaning, m, short_gloss(m))
        check("hsk card", word, meaning, m, core(m))

    cur.execute("SELECT word, gloss FROM genre_term")
    for word, gloss in cur.fetchall():
        check("genre", word, gloss, gloss or "", primary(gloss or ""))

    cur.execute("SELECT char, definition FROM character WHERE definition IS NOT NULL AND definition<>''")
    chars = cur.fetchall()
    for ch, defn in chars:
        check("character", ch, defn, defn or "", primary(defn or ""))

    print("Spot-checks (after fallback):")
    for w in ("都", "还", "王", "上", "家", "个", "万", "恶", "联系", "涌", "档", "火暴"):
        cur.execute("SELECT meaning FROM hsk_word WHERE word=?", (w,))
        r = cur.fetchone()
        m = resolved_meaning(cur, w, (r[0] if r else "") or "")
        print(f"  {w}: label={short_gloss(m)!r}  card={core(m)!r}")
    print(f"  很: label={short_gloss('(adverb of degree); quite; very; awfully')!r}")
    print(f"  棵(classifier): label={short_gloss('classifier for trees, cabbages, plants etc')!r}")

    print(f"\nScanned {len(hsk)} hsk_word + genre_term + {len(chars)} character rows.")
    if data_gaps:
        print(f"\nData gaps ({len(data_gaps)}) — no learnable meaning in any source, honest annotation shown:")
        for kind, key, raw, val in data_gaps[:25]:
            print(f"  [{kind}] {key}: {raw} -> {val}")
        if len(data_gaps) > 25:
            print(f"  … and {len(data_gaps) - 25} more")
    if failures:
        print(f"\nFAIL: {len(failures)} rows show noise despite a real meaning being available:")
        for kind, key, raw, val in failures[:80]:
            print(f"  [{kind}] {key}: {raw} -> {val}")
        sys.exit(1)
    print("\nPASS: wherever a real meaning exists, it is shown (no surname/variant/used-in leaks).")

if __name__ == "__main__":
    main()
