#!/usr/bin/env python3
"""
Build sentences.db — a compact, translated example-sentence bank for Scholar.

Source: Tatoeba (CC-BY 2.0 FR). We download the Mandarin and English sentence exports
plus the link table, join Mandarin sentences to their English translations, and store the
pairs in a tiny SQLite file. This DB is *separate* from content.db on purpose, so the main
content database is never touched.

Output schema:
    sentence(zh TEXT, en TEXT, n INTEGER)   -- n = han-character length, for "shortest first"

Lookup at runtime is a plain `WHERE zh LIKE '%词%'` scan — with ~tens of thousands of short
rows that is a sub-millisecond query, so no index/FTS is needed.

Usage:
    cd data-build && python build_sentences_db.py
"""
import bz2
import os
import sqlite3
import tarfile
import urllib.request

BASE = "https://downloads.tatoeba.org/exports"
CMN = f"{BASE}/per_language/cmn/cmn_sentences.tsv.bz2"
ENG = f"{BASE}/per_language/eng/eng_sentences.tsv.bz2"
LINKS = f"{BASE}/links.tar.bz2"

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "sentences.db")
CACHE = os.path.join(os.path.dirname(__file__), ".tatoeba")
MAX_PER_WORD_LEN = 40          # skip very long sentences — poor for flashcard study
MAX_TRANSLATIONS = 2           # keep up to this many English translations per Mandarin sentence


def fetch(url, name):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name)
    if not os.path.exists(path) or os.path.getsize(path) == 0:
        print(f"  downloading {name} …")
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req) as r, open(path, "wb") as f:
            f.write(r.read())
    return path


def read_sentences(path):
    """yield (id:int, text:str) from a *_sentences.tsv.bz2 export."""
    with bz2.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3:
                try:
                    yield int(parts[0]), parts[2]
                except ValueError:
                    continue


def main():
    print("Fetching Tatoeba exports…")
    cmn_path = fetch(CMN, "cmn_sentences.tsv.bz2")
    eng_path = fetch(ENG, "eng_sentences.tsv.bz2")
    links_path = fetch(LINKS, "links.tar.bz2")

    print("Loading Mandarin sentences…")
    cmn = {sid: text for sid, text in read_sentences(cmn_path)}
    print(f"  {len(cmn):,} Mandarin sentences")

    print("Scanning links for cmn↔eng pairs…")
    # cmn_id -> list of eng_ids
    want = {}
    needed_eng = set()
    with tarfile.open(links_path, "r:bz2") as tar:
        member = next(m for m in tar.getmembers() if m.name.endswith("links.csv"))
        f = tar.extractfile(member)
        for raw in f:
            a, _, b = raw.decode("utf-8").rstrip("\n").partition("\t")
            try:
                a = int(a); b = int(b)
            except ValueError:
                continue
            if a in cmn:
                want.setdefault(a, []).append(b)
                needed_eng.add(b)
    print(f"  {len(want):,} Mandarin sentences have at least one link")

    print("Loading needed English translations…")
    eng = {}
    for sid, text in read_sentences(eng_path):
        if sid in needed_eng:
            eng[sid] = text
    print(f"  resolved {len(eng):,} English sentences")

    print("Joining and writing sentences.db…")
    if os.path.exists(OUT):
        os.remove(OUT)
    db = sqlite3.connect(OUT)
    db.execute("CREATE TABLE sentence (zh TEXT NOT NULL, en TEXT NOT NULL, n INTEGER NOT NULL)")
    rows = 0
    seen = set()
    for cid, zh in cmn.items():
        han = sum(1 for ch in zh if "一" <= ch <= "鿿")
        if han == 0 or han > MAX_PER_WORD_LEN:
            continue
        translations = []
        for eid in want.get(cid, []):
            if eid in eng:
                translations.append(eng[eid])
            if len(translations) >= MAX_TRANSLATIONS:
                break
        if not translations:
            continue
        if zh in seen:
            continue
        seen.add(zh)
        db.execute("INSERT INTO sentence (zh, en, n) VALUES (?,?,?)",
                   (zh, " / ".join(translations), han))
        rows += 1
    db.commit()
    db.execute("VACUUM")
    db.commit()
    db.close()
    size = os.path.getsize(OUT) / 1_000_000
    print(f"Done: {rows:,} translated sentences → {OUT} ({size:.1f} MB)")


if __name__ == "__main__":
    main()
