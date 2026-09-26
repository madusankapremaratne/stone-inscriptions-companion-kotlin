#!/usr/bin/env python3
"""
Validate curated knowledge cards and write them into the prepackaged content database.

Usage (from the repository root):
    python3 scripts/build_knowledge_pack.py           # validate + write
    python3 scripts/build_knowledge_pack.py --check   # validate only (use before committing cards)

Cards live in data/knowledge/cards/<id>.json, one card per file. See data/knowledge/README.md.

This step is incremental: it replaces only the knowledge tables, so curators can rebuild the
pack without the letter-table workbook that scripts/build_database.py needs.
"""
import argparse
import glob
import json
import os
import re
import shutil
import sqlite3
import sys

from room_schema import KNOWLEDGE_TABLES

ASSET_DB = 'app/src/main/assets/databases/sellipi.db'
DATA_DB_COPY = 'data/sellipi.db'
CARDS_DIR = 'data/knowledge/cards'

KINDS = {'person', 'place', 'term', 'period', 'event'}
STATUSES = {'draft', 'verified'}
ALIAS_LANGS = {'en', 'si', 'ta', 'roman'}
LANGS = ('en', 'si', 'ta')
MAX_BODY_WORDS = 150
ID_PATTERN = re.compile(r'^(person|place|term|period|event)\.[a-z0-9_]+$')


def fold(text):
    """Case/space-insensitive comparison key for alias collision checks (not the app's matcher)."""
    return re.sub(r'\s+', ' ', text.strip().lower())


def load_cards():
    cards = []
    for path in sorted(glob.glob(os.path.join(CARDS_DIR, '*.json'))):
        with open(path, encoding='utf-8') as f:
            try:
                card = json.load(f)
            except json.JSONDecodeError as e:
                raise SystemExit(f'{path}: invalid JSON: {e}')
        card['_path'] = path
        cards.append(card)
    return cards


def validate(cards, site_ids, inscription_ids):
    errors, warnings = [], []

    def err(card, msg):
        errors.append(f"{card.get('_path')}: {msg}")

    def warn(card, msg):
        warnings.append(f"{card.get('_path')}: {msg}")

    seen_ids = set()
    unambiguous_owner = {}

    for card in cards:
        cid = card.get('id', '')
        if not ID_PATTERN.match(cid):
            err(card, f"id '{cid}' must look like kind.snake_case_name")
        elif os.path.basename(card['_path']) != f'{cid}.json':
            err(card, f"file name must be '{cid}.json'")
        if cid in seen_ids:
            err(card, f"duplicate id '{cid}'")
        seen_ids.add(cid)

        kind = card.get('kind')
        if kind not in KINDS:
            err(card, f"kind '{kind}' not in {sorted(KINDS)}")
        elif cid and not cid.startswith(kind + '.'):
            err(card, f"id prefix must match kind '{kind}'")

        status = card.get('status')
        if status not in STATUSES:
            err(card, f"status '{status}' not in {sorted(STATUSES)}")

        title = card.get('title') or {}
        body = card.get('body') or {}
        if not (title.get('en') or '').strip():
            err(card, 'title.en is required')
        if not (body.get('en') or '').strip():
            err(card, 'body.en is required')
        for lang in LANGS:
            words = len((body.get(lang) or '').split())
            if words > MAX_BODY_WORDS:
                err(card, f'body.{lang} has {words} words (max {MAX_BODY_WORDS})')
            if status == 'verified' and lang != 'en' and not (body.get(lang) or '').strip():
                warn(card, f'verified card has no body.{lang}; the app will show English with a note')

        sources = card.get('sources') or []
        if not sources or not all(isinstance(s, str) and s.strip() for s in sources):
            err(card, 'sources must be a non-empty list of citations')

        aliases = card.get('aliases') or []
        alias_texts = set()
        for alias in aliases:
            text = (alias.get('text') or '').strip()
            lang = alias.get('lang')
            if not text:
                err(card, 'alias with empty text')
                continue
            if lang not in ALIAS_LANGS:
                err(card, f"alias '{text}' has lang '{lang}' not in {sorted(ALIAS_LANGS)}")
            key = fold(text)
            if key in alias_texts:
                err(card, f"duplicate alias '{text}'")
            alias_texts.add(key)
            if not alias.get('ambiguous', False):
                owner = unambiguous_owner.get(key)
                if owner and owner != cid:
                    err(card, f"alias '{text}' also names '{owner}'; mark it ambiguous on both cards")
                unambiguous_owner[key] = cid
        for lang in LANGS:
            t = (title.get(lang) or '').strip()
            if t and fold(t) not in alias_texts:
                err(card, f"title.{lang} '{t}' must also be listed in aliases")

        links = card.get('links') or {}
        for site in links.get('sites', []):
            if site not in site_ids:
                err(card, f"links.sites: unknown site '{site}'")
        for insc in links.get('inscriptions', []):
            if insc not in inscription_ids:
                err(card, f"links.inscriptions: unknown inscription '{insc}'")

    # Ambiguous on one card but unambiguous on another is also a collision.
    for card in cards:
        for alias in card.get('aliases') or []:
            if alias.get('ambiguous', False):
                owner = unambiguous_owner.get(fold(alias.get('text') or ''))
                if owner and owner != card.get('id'):
                    err(card, f"alias '{alias['text']}' is unambiguous on '{owner}'; mark it ambiguous there too")

    return errors, warnings


def write(conn, cards):
    cur = conn.cursor()
    cur.executescript('''
        DROP TABLE IF EXISTS knowledge_cards;
        DROP TABLE IF EXISTS entity_aliases;
        DROP TABLE IF EXISTS card_links;
    ''')
    for _, ddl in KNOWLEDGE_TABLES:
        cur.execute(ddl)
    for card in cards:
        title, body = card['title'], card['body']
        cur.execute(
            'INSERT INTO knowledge_cards VALUES (?,?,?,?,?,?,?,?,?,?,?)',
            (card['id'], card['kind'],
             title['en'].strip(), (title.get('si') or None), (title.get('ta') or None),
             body['en'].strip(), (body.get('si') or None), (body.get('ta') or None),
             '\n'.join(s.strip() for s in card['sources']),
             card.get('confidence_note') or None,
             card['status']))
        for alias in card.get('aliases') or []:
            cur.execute('INSERT INTO entity_aliases VALUES (?,?,?,?)',
                        (alias['text'].strip(), card['id'], alias['lang'], 1 if alias.get('ambiguous') else 0))
        links = card.get('links') or {}
        for site in links.get('sites', []):
            cur.execute('INSERT INTO card_links VALUES (?,?,?)', (card['id'], 'SITE', site))
        for insc in links.get('inscriptions', []):
            cur.execute('INSERT INTO card_links VALUES (?,?,?)', (card['id'], 'INSCRIPTION', insc))
    # PRAGMA user_version is deliberately left untouched (0). With 0, Room treats the copied
    # asset as new and runs its CREATE ... IF NOT EXISTS statements (creating its own indices)
    # before validating. See docs/slm-lessons-architecture.md §8.2.
    conn.commit()


def build_knowledge_pack(check_only=False):
    if not os.path.exists(ASSET_DB):
        raise SystemExit(f'{ASSET_DB} not found; run from the repository root')
    cards = load_cards()
    conn = sqlite3.connect(ASSET_DB)
    try:
        site_ids = {r[0] for r in conn.execute('SELECT id FROM sites')}
        inscription_ids = {r[0] for r in conn.execute('SELECT id FROM inscriptions')}
        errors, warnings = validate(cards, site_ids, inscription_ids)
        for w in warnings:
            print(f'warning: {w}')
        if errors:
            for e in errors:
                print(f'error: {e}', file=sys.stderr)
            raise SystemExit(f'{len(errors)} error(s); knowledge pack not written')

        verified = sum(1 for c in cards if c['status'] == 'verified')
        summary = (f"{len(cards)} cards ({verified} verified, {len(cards) - verified} draft), "
                   f"{sum(len(c.get('aliases') or []) for c in cards)} aliases")
        if check_only:
            print(f'OK: {summary}')
            return
        write(conn, cards)
        conn.execute('VACUUM')
    finally:
        conn.close()
    shutil.copyfile(ASSET_DB, DATA_DB_COPY)
    print(f'Knowledge pack written: {summary}')
    print(f'  -> {ASSET_DB}')
    print(f'  -> {DATA_DB_COPY}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true', help='validate cards without writing')
    build_knowledge_pack(check_only=parser.parse_args().check)
