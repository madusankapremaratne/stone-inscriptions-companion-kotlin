#!/usr/bin/env python3
"""
Rebuild the prepackaged content database with the Room-exact schema, keeping every row.

For when the asset was built with older DDL and the source workbook needed by
scripts/build_database.py is not at hand. Safe to re-run: it always rebuilds from the
current asset into a fresh file, verifies row counts and foreign keys, then replaces it.

Usage (from the repository root):
    python3 scripts/migrate_asset_schema.py
"""
import os
import shutil
import sqlite3
import sys

from room_schema import INDICES, KNOWLEDGE_TABLES, TABLES

ASSET_DB = 'app/src/main/assets/databases/sellipi.db'
DATA_DB_COPY = 'data/sellipi.db'


def columns(conn, schema, table):
    return [r[1] for r in conn.execute(f'PRAGMA {schema}.table_info(`{table}`)')]


def migrate():
    if not os.path.exists(ASSET_DB):
        raise SystemExit(f'{ASSET_DB} not found; run from the repository root')

    tmp_path = ASSET_DB + '.migrating'
    if os.path.exists(tmp_path):
        os.remove(tmp_path)

    conn = sqlite3.connect(tmp_path)
    try:
        conn.execute('ATTACH DATABASE ? AS old', (ASSET_DB,))
        old_tables = {r[0] for r in conn.execute("SELECT name FROM old.sqlite_master WHERE type='table'")}
        user_version = conn.execute('PRAGMA old.user_version').fetchone()[0]

        # Foreign keys off while copying; integrity is checked explicitly afterwards.
        conn.execute('PRAGMA foreign_keys = OFF')
        report = []
        for table, ddl in TABLES + KNOWLEDGE_TABLES:
            conn.execute(ddl)
            if table not in old_tables:
                report.append(f'{table}: not in old asset, created empty')
                continue
            new_cols = columns(conn, 'main', table)
            old_cols = columns(conn, 'old', table)
            if set(new_cols) != set(old_cols):
                raise SystemExit(f'{table}: column mismatch, old={old_cols} new={new_cols}; fix room_schema.py first')
            col_list = ', '.join(f'`{c}`' for c in new_cols)
            conn.execute(f'INSERT INTO main.`{table}` ({col_list}) SELECT {col_list} FROM old.`{table}`')
            n_new = conn.execute(f'SELECT COUNT(*) FROM main.`{table}`').fetchone()[0]
            n_old = conn.execute(f'SELECT COUNT(*) FROM old.`{table}`').fetchone()[0]
            if n_new != n_old:
                raise SystemExit(f'{table}: copied {n_new} of {n_old} rows')
            report.append(f'{table}: {n_new} rows')

        for ddl in INDICES:
            conn.execute(ddl)

        dropped = sorted(old_tables - {t for t, _ in TABLES + KNOWLEDGE_TABLES} - {'sqlite_sequence'})
        if dropped:
            report.append(f'not carried over (no Room entity): {dropped}')

        violations = conn.execute('PRAGMA main.foreign_key_check').fetchall()
        if violations:
            raise SystemExit(f'foreign key violations after copy: {violations[:10]}')

        # Preserve user_version (0): see docs/slm-lessons-architecture.md §8.2.
        conn.execute(f'PRAGMA main.user_version = {int(user_version)}')
        conn.commit()
        conn.execute('DETACH DATABASE old')
        conn.execute('VACUUM')
    except BaseException:
        conn.close()
        os.remove(tmp_path)
        raise
    conn.close()

    os.replace(tmp_path, ASSET_DB)
    shutil.copyfile(ASSET_DB, DATA_DB_COPY)
    for line in report:
        print(f'  {line}')
    print(f'Migrated {ASSET_DB} (and {DATA_DB_COPY}) to the Room schema.')


if __name__ == '__main__':
    sys.exit(migrate())
