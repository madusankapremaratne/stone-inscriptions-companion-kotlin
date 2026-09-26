"""
Content-database DDL in the exact form Room generates for the entities in
app/src/main/java/org/sellipi/companion/data/local/entity/.

Room opens the prepackaged asset only if every table matches its entity: column names,
type affinity, NOT NULL (including on primary keys), primary-key columns, foreign keys and
the set of indices (Room names them index_<table>_<columns>). Keep this file in step with
the entities, then verify with scripts/check_room_schema.py after a Gradle build.

Mapping used by Room: String -> TEXT, Int/Long/Boolean -> INTEGER, Float/Double -> REAL;
non-null Kotlin types -> NOT NULL. No DEFAULT clauses: the entities declare none.
"""

# Parent tables first so foreign keys resolve when rows are copied in order.
TABLES = [
    ('sites', '''CREATE TABLE IF NOT EXISTS `sites` (`id` TEXT NOT NULL, `name_si` TEXT NOT NULL, `name_ta` TEXT NOT NULL, `name_en` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `geofence_radius_m` REAL NOT NULL, `permit_reference` TEXT NOT NULL, PRIMARY KEY(`id`))'''),
    ('periods', '''CREATE TABLE IF NOT EXISTS `periods` (`id` TEXT NOT NULL, `period_number` INTEGER NOT NULL, `label_si` TEXT NOT NULL, `label_ta` TEXT NOT NULL, `label_en` TEXT NOT NULL, `year_start` INTEGER NOT NULL, `year_end` INTEGER NOT NULL, `chart_column_refs` TEXT NOT NULL, PRIMARY KEY(`id`))'''),
    ('letters', '''CREATE TABLE IF NOT EXISTS `letters` (`id` TEXT NOT NULL, `row_index` INTEGER NOT NULL, `modern_sinhala_codepoint` TEXT NOT NULL, `romanisation` TEXT NOT NULL, `letter_name` TEXT NOT NULL, `glyph_image_path` TEXT, PRIMARY KEY(`id`))'''),
    ('letter_forms', '''CREATE TABLE IF NOT EXISTS `letter_forms` (`id` TEXT NOT NULL, `letter_id` TEXT NOT NULL, `period_id` TEXT NOT NULL, `grid_row` INTEGER NOT NULL, `grid_col` INTEGER NOT NULL, `vector_path` TEXT, `image_asset_path` TEXT, `is_attested` INTEGER NOT NULL, `is_reconstructed` INTEGER NOT NULL, `source_inscription_ref` TEXT, `provenance` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`letter_id`) REFERENCES `letters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`period_id`) REFERENCES `periods`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )'''),
    ('inscriptions', '''CREATE TABLE IF NOT EXISTS `inscriptions` (`id` TEXT NOT NULL, `site_id` TEXT NOT NULL, `name_si` TEXT NOT NULL, `name_ta` TEXT NOT NULL, `name_en` TEXT NOT NULL, `date_range_start` INTEGER NOT NULL, `date_range_end` INTEGER NOT NULL, `dating_basis` TEXT NOT NULL, `primary_period_id` TEXT NOT NULL, `reference_photo` TEXT, `arcore_target_score` INTEGER NOT NULL, `alignment_strategy` TEXT NOT NULL, `source_citation` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`site_id`) REFERENCES `sites`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`primary_period_id`) REFERENCES `periods`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )'''),
    ('transcription_lines', '''CREATE TABLE IF NOT EXISTS `transcription_lines` (`id` TEXT NOT NULL, `inscription_id` TEXT NOT NULL, `line_number` INTEGER NOT NULL, `text_original` TEXT NOT NULL, `text_modern_sinhala` TEXT NOT NULL, `translation_si` TEXT NOT NULL, `translation_ta` TEXT NOT NULL, `translation_en` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`inscription_id`) REFERENCES `inscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )'''),
    ('glyph_occurrences', '''CREATE TABLE IF NOT EXISTS `glyph_occurrences` (`id` TEXT NOT NULL, `transcription_line_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `letter_id` TEXT NOT NULL, `bbox_x` REAL NOT NULL, `bbox_y` REAL NOT NULL, `bbox_w` REAL NOT NULL, `bbox_h` REAL NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`transcription_line_id`) REFERENCES `transcription_lines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`letter_id`) REFERENCES `letters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )'''),
    ('capture_sessions', '''CREATE TABLE IF NOT EXISTS `capture_sessions` (`id` TEXT NOT NULL, `inscription_id` TEXT NOT NULL, `device_model` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `gps_lat` REAL NOT NULL, `gps_lon` REAL NOT NULL, `gps_accuracy` REAL NOT NULL, `pose_matrix` TEXT NOT NULL, `scale_mm_per_px` REAL NOT NULL, `image_paths` TEXT NOT NULL, `permit_reference` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`inscription_id`) REFERENCES `inscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )'''),
]

INDICES = [
    'CREATE INDEX IF NOT EXISTS `index_letter_forms_letter_id_period_id` ON `letter_forms` (`letter_id`, `period_id`)',
    'CREATE INDEX IF NOT EXISTS `index_inscriptions_site_id` ON `inscriptions` (`site_id`)',
    'CREATE INDEX IF NOT EXISTS `index_inscriptions_primary_period_id` ON `inscriptions` (`primary_period_id`)',
    'CREATE INDEX IF NOT EXISTS `index_transcription_lines_inscription_id` ON `transcription_lines` (`inscription_id`)',
    'CREATE INDEX IF NOT EXISTS `index_glyph_occurrences_transcription_line_id` ON `glyph_occurrences` (`transcription_line_id`)',
    'CREATE INDEX IF NOT EXISTS `index_glyph_occurrences_letter_id` ON `glyph_occurrences` (`letter_id`)',
    'CREATE INDEX IF NOT EXISTS `index_capture_sessions_inscription_id` ON `capture_sessions` (`inscription_id`)',
]

# Knowledge pack tables (KnowledgeEntities.kt); written by build_knowledge_pack.py.
KNOWLEDGE_TABLES = [
    ('knowledge_cards', '''CREATE TABLE IF NOT EXISTS `knowledge_cards` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `title_en` TEXT NOT NULL, `title_si` TEXT, `title_ta` TEXT, `body_en` TEXT NOT NULL, `body_si` TEXT, `body_ta` TEXT, `sources` TEXT NOT NULL, `confidence_note` TEXT, `curation_status` TEXT NOT NULL, PRIMARY KEY(`id`))'''),
    ('entity_aliases', '''CREATE TABLE IF NOT EXISTS `entity_aliases` (`alias` TEXT NOT NULL, `card_id` TEXT NOT NULL, `lang` TEXT NOT NULL, `ambiguous` INTEGER NOT NULL, PRIMARY KEY(`alias`, `card_id`))'''),
    ('card_links', '''CREATE TABLE IF NOT EXISTS `card_links` (`card_id` TEXT NOT NULL, `target_type` TEXT NOT NULL, `target_id` TEXT NOT NULL, PRIMARY KEY(`card_id`, `target_type`, `target_id`))'''),
]


def create_content_schema(cur):
    for _, ddl in TABLES:
        cur.execute(ddl)
    for ddl in INDICES:
        cur.execute(ddl)
