#!/usr/bin/env python3
"""
Compare the prepackaged content database with the schema Room expects.

Room refuses to open a prepackaged database whose tables differ from its entities
("Pre-packaged database has an invalid schema"). Run this after any change to entities or
to the build scripts, once a Gradle build has exported the schema:

    ./gradlew :app:kspDebugKotlin          # or any build; writes app/schemas/...
    python3 scripts/check_room_schema.py

Exit code 0 = compatible; 1 = mismatches found (each one is printed).
"""
import glob
import json
import os
import sqlite3
import sys

ASSET_DB = 'app/src/main/assets/databases/sellipi.db'
SCHEMA_DIR = 'app/schemas/org.sellipi.companion.data.local.database.SellipiDatabase'


def affinity(declared):
    """SQLite type-affinity rules, which is what Room compares."""
    t = (declared or '').upper()
    if 'INT' in t:
        return 'INTEGER'
    if any(k in t for k in ('CHAR', 'CLOB', 'TEXT')):
        return 'TEXT'
    if 'BLOB' in t or not t:
        return 'BLOB'
    if any(k in t for k in ('REAL', 'FLOA', 'DOUB')):
        return 'REAL'
    return 'NUMERIC'


def latest_schema():
    files = glob.glob(os.path.join(SCHEMA_DIR, '*.json'))
    if not files:
        raise SystemExit(f'No exported schema in {SCHEMA_DIR}. Build the app once (exportSchema=true) first.')
    path = max(files, key=lambda f: int(os.path.splitext(os.path.basename(f))[0]))
    with open(path, encoding='utf-8') as f:
        return path, json.load(f)['database']


def main():
    schema_path, schema = latest_schema()
    conn = sqlite3.connect(ASSET_DB)
    problems = []
    notes = []

    for entity in schema['entities']:
        table = entity['tableName']
        cols = {r[1]: r for r in conn.execute(f'PRAGMA table_info(`{table}`)')}
        if not cols:
            problems.append(f'{table}: table missing from asset (Room would create it EMPTY)')
            continue

        expected_pk = entity['primaryKey']['columnNames']
        actual_pk = [r[1] for r in sorted((r for r in cols.values() if r[5] > 0), key=lambda r: r[5])]
        if expected_pk != actual_pk:
            problems.append(f'{table}: primary key {actual_pk}, Room expects {expected_pk}')

        expected_cols = {f['columnName']: f for f in entity['fields']}
        for name, field in expected_cols.items():
            row = cols.get(name)
            if row is None:
                problems.append(f'{table}.{name}: column missing')
                continue
            if affinity(row[2]) != field['affinity']:
                problems.append(f'{table}.{name}: type {row[2]} ({affinity(row[2])}), Room expects {field["affinity"]}')
            if bool(row[3]) != field['notNull']:
                problems.append(f'{table}.{name}: NOT NULL is {bool(row[3])}, Room expects {field["notNull"]}')
        for name in cols.keys() - expected_cols.keys():
            problems.append(f'{table}.{name}: column not in entity')

        expected_idx = {i['name'] for i in entity.get('indices', [])}
        actual_idx = {r[1] for r in conn.execute(f'PRAGMA index_list(`{table}`)') if r[3] == 'c'}
        for name in sorted(actual_idx - expected_idx):
            problems.append(f'{table}: extra index {name} (Room compares index sets)')
        for name in sorted(expected_idx - actual_idx):
            notes.append(f'{table}: index {name} absent; Room creates it on first open (asset user_version 0)')

        expected_fks = {(fk['table'], tuple(fk['columns']), tuple(fk['referencedColumns'])) for fk in entity.get('foreignKeys', [])}
        rows = list(conn.execute(f'PRAGMA foreign_key_list(`{table}`)'))
        actual_fks = {}
        for r in rows:
            actual_fks.setdefault(r[0], [r[2], [], []])
            actual_fks[r[0]][1].append(r[3])
            actual_fks[r[0]][2].append(r[4])
        actual_fks = {(t, tuple(c), tuple(rc)) for t, c, rc in actual_fks.values()}
        if expected_fks != actual_fks:
            problems.append(f'{table}: foreign keys {sorted(actual_fks)}, Room expects {sorted(expected_fks)}')

    version = conn.execute('PRAGMA user_version').fetchone()[0]
    if version != 0:
        notes.append(f'asset user_version is {version}; Room will not run CREATE IF NOT EXISTS on first open')
    conn.close()

    print(f'Checked {ASSET_DB} against {schema_path}')
    for n in notes:
        print(f'note: {n}')
    for p in problems:
        print(f'MISMATCH: {p}')
    print('OK: compatible' if not problems else f'{len(problems)} mismatch(es)')
    sys.exit(1 if problems else 0)


if __name__ == '__main__':
    main()
