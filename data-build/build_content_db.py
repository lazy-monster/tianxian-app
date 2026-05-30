#!/usr/bin/env python3
"""
Scholar · content.db builder
=============================
Pulls open datasets through code and compiles one read-only SQLite database that
ships in app/src/main/assets/. Run from data-build/:

    pip install pycccedict wordfreq
    python build_content_db.py

Sources (all free / open):
  CC-CEDICT (pycccedict) ......... CC BY-SA ....... dictionary
  Make Me a Hanzi dictionary.txt . Arphic ......... decomposition, radicals
  Make Me a Hanzi graphics.txt ... Arphic ......... stroke-order data (handwriting)
  complete-hsk-vocabulary ........ MIT ............ HSK 2.0+3.0 levels & meanings
  wordfreq ....................... MIT ............ frequency ordering
  radicals.py .................... curated ........ 214 Kangxi radicals + meanings
  genre_terms.py ................. curated ........ xianxia/wuxia module
"""
import json, os, sqlite3, urllib.request
from pycccedict.cccedict import CcCedict
from wordfreq import top_n_list
import genre_terms, radicals

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets"))
DB = os.path.join(ASSETS, "content.db")
MMAH_DICT = "https://raw.githubusercontent.com/skishore/makemeahanzi/master/dictionary.txt"
MMAH_GFX = "https://raw.githubusercontent.com/skishore/makemeahanzi/master/graphics.txt"
HSK_URL = "https://raw.githubusercontent.com/drkameleon/complete-hsk-vocabulary/main/complete.json"
TOP_WORDS = 40000
STROKE_FREQ_LIMIT = 4000   # bundle stroke data for chars at least this frequent (+ all HSK)


def fetch(url, cache):
    path = os.path.join(HERE, cache)
    if not os.path.exists(path):
        print(f"  ↓ {url}")
        urllib.request.urlretrieve(url, path)
    return path


def main():
    os.makedirs(ASSETS, exist_ok=True)
    if os.path.exists(DB):
        os.remove(DB)
    db = sqlite3.connect(DB)
    c = db.cursor()
    c.executescript("""
        PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF;
        CREATE TABLE dict_entry(
            id INTEGER PRIMARY KEY, simplified TEXT, traditional TEXT,
            pinyin TEXT, pinyin_numeric TEXT, gloss TEXT, freq_rank INTEGER);
        CREATE INDEX idx_simp ON dict_entry(simplified);
        CREATE VIRTUAL TABLE dict_fts USING fts5(
            simplified, traditional, pinyin, gloss, content='dict_entry', content_rowid='id');
        CREATE TABLE character(
            char TEXT PRIMARY KEY, pinyin TEXT, definition TEXT,
            decomposition TEXT, radical TEXT, stroke_count INTEGER,
            hsk2 INTEGER, hsk3 INTEGER, freq_rank INTEGER);
        CREATE TABLE word_freq(word TEXT PRIMARY KEY, rank INTEGER);
        CREATE TABLE hsk_word(word TEXT, level TEXT, pinyin TEXT, meaning TEXT, freq_rank INTEGER);
        CREATE INDEX idx_hsk_word ON hsk_word(word);
        CREATE INDEX idx_hsk_level ON hsk_word(level);
        CREATE TABLE genre_term(
            word TEXT PRIMARY KEY, category TEXT, pinyin TEXT, gloss TEXT, realm_rank INTEGER);
        CREATE TABLE radical(
            number INTEGER PRIMARY KEY, radical TEXT, pinyin TEXT, meaning TEXT);
        CREATE TABLE char_strokes(
            char TEXT PRIMARY KEY, strokes TEXT, medians TEXT);
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
    """)

    # 1. word frequency
    print("• word frequency …")
    freq = top_n_list("zh", TOP_WORDS)
    rank_of = {w: i + 1 for i, w in enumerate(freq)}
    c.executemany("INSERT OR IGNORE INTO word_freq VALUES (?,?)", [(w, i + 1) for i, w in enumerate(freq)])
    char_rank = {}
    for w, r in rank_of.items():
        for ch in w:
            if "\u4e00" <= ch <= "\u9fff" and (ch not in char_rank or r < char_rank[ch]):
                char_rank[ch] = r

    # 2. CC-CEDICT
    print("• CC-CEDICT …")
    entries = CcCedict().get_entries()
    rows = [(e["simplified"], e["traditional"], e["pinyin"], e["pinyin"],
             "; ".join(e["definitions"]), rank_of.get(e["simplified"])) for e in entries]
    c.executemany("INSERT INTO dict_entry(simplified,traditional,pinyin,pinyin_numeric,gloss,freq_rank) "
                  "VALUES (?,?,?,?,?,?)", rows)
    c.execute("INSERT INTO dict_fts(rowid,simplified,traditional,pinyin,gloss) "
              "SELECT id,simplified,traditional,pinyin,gloss FROM dict_entry")
    print(f"    {len(rows):,} entries")

    # 3. characters (decomposition/radical + stroke count from MMaH)
    print("• Make Me a Hanzi (characters) …")
    char_info = {}
    with open(fetch(MMAH_DICT, "mmah_dictionary.txt"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            d = json.loads(line)
            char_info[d["character"]] = {
                "pinyin": ", ".join(d.get("pinyin", []) or []),
                "definition": d.get("definition") or "",
                "decomposition": d.get("decomposition") or "",
                "radical": d.get("radical") or "",
            }

    # 4. HSK
    print("• complete-hsk-vocabulary …")
    hsk = json.load(open(fetch(HSK_URL, "hsk_complete.json"), encoding="utf-8"))
    hsk_char_level, hsk_rows = {}, []
    for item in hsk:
        simp = item["simplified"]
        levels = item.get("level", [])
        hsk2 = next((int(l) for l in levels if l.isdigit()), None)
        hsk3 = None
        for l in levels:
            if l.startswith("new-"):
                hsk3 = int(l.split("-")[1])
            elif l.isdigit():
                hsk3 = hsk3 or int(l)
        form = (item.get("forms") or [{}])[0]
        py = form.get("transcriptions", {}).get("pinyin", "")
        meaning = "; ".join(form.get("meanings", []) or [])
        hsk_rows.append((simp, ",".join(levels), py, meaning, item.get("frequency")))
        if len(simp) == 1:
            hsk_char_level[simp] = (hsk2, hsk3)
    c.executemany("INSERT INTO hsk_word VALUES (?,?,?,?,?)", hsk_rows)
    print(f"    {len(hsk_rows):,} HSK items")

    all_chars = set(char_info) | set(char_rank)
    crows = []
    for ch in all_chars:
        info = char_info.get(ch, {})
        h2, h3 = hsk_char_level.get(ch, (None, None))
        crows.append((ch, info.get("pinyin", ""), info.get("definition", ""),
                      info.get("decomposition", ""), info.get("radical", ""), None,
                      h2, h3, char_rank.get(ch)))
    c.executemany("INSERT OR REPLACE INTO character VALUES (?,?,?,?,?,?,?,?,?)", crows)
    print(f"    {len(crows):,} characters")

    # 5. stroke-order data (handwriting) — subset to common chars to control size
    print("• Make Me a Hanzi (strokes) …")
    keep = {ch for ch, r in char_rank.items() if r <= STROKE_FREQ_LIMIT} | set(hsk_char_level)
    srows = []
    with open(fetch(MMAH_GFX, "graphics.txt"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            d = json.loads(line)
            ch = d["character"]
            if ch in keep:
                srows.append((ch, json.dumps(d.get("strokes", [])), json.dumps(d.get("medians", []))))
    c.executemany("INSERT OR REPLACE INTO char_strokes VALUES (?,?,?)", srows)
    # backfill stroke_count from the stroke data
    for ch, strokes_json, _ in srows:
        c.execute("UPDATE character SET stroke_count=? WHERE char=?", (len(json.loads(strokes_json)), ch))
    print(f"    {len(srows):,} characters with stroke data")

    # 6. radicals
    print("• radicals …")
    c.executemany("INSERT INTO radical VALUES (?,?,?,?)", radicals.RADICALS)
    print(f"    {len(radicals.RADICALS)} radicals")

    # 7. genre module
    print("• genre terms …")
    dict_by_simp = {}
    for e in entries:
        dict_by_simp.setdefault(e["simplified"], e)
    grows = []
    for word, cat, py, gloss in genre_terms.TERMS:
        de = dict_by_simp.get(word)
        grows.append((word, cat, py or (de["pinyin"] if de else ""),
                      gloss or ("; ".join(de["definitions"]) if de else ""), None))
    for word, rank, py, gloss in genre_terms.REALMS:
        grows.append((word, "realm", py, gloss, rank))
    c.executemany("INSERT OR REPLACE INTO genre_term VALUES (?,?,?,?,?)", grows)
    print(f"    {len(grows)} genre terms")

    c.executemany("INSERT INTO meta VALUES (?,?)", [
        ("schema_version", "2"),
        ("dict_entries", str(len(rows))),
        ("characters", str(len(crows))),
        ("stroke_chars", str(len(srows))),
        ("radicals", str(len(radicals.RADICALS))),
        ("sources", "CC-CEDICT (CC BY-SA); Make Me a Hanzi (Arphic); "
                    "complete-hsk-vocabulary (MIT); wordfreq (MIT)"),
    ])
    db.commit()
    c.execute("VACUUM")
    db.commit()
    db.close()
    print(f"\n✓ wrote {DB}  ({os.path.getsize(DB)/1e6:.1f} MB)")


if __name__ == "__main__":
    main()
