#!/usr/bin/env python3
"""
Sellipi AR Site Companion - Database Seeding & Asset Extractor
Extracts all letterforms and period headers from docs/Ancient_Letter_Table.xlsx
and builds the pre-seeded SQLite database for Android Room.
"""

import os
import io
import zipfile
import sqlite3
import xml.etree.ElementTree as ET

def build_database():
    excel_path = 'docs/Ancient_Letter_Table.xlsx'
    if not os.path.exists(excel_path):
        raise FileNotFoundError(f"Source Excel workbook not found at {excel_path}")

    db_dir = 'app/src/main/assets/databases'
    assets_glyph_dir = 'app/src/main/assets/glyph_images'
    data_dir = 'data'

    os.makedirs(db_dir, exist_ok=True)
    os.makedirs(assets_glyph_dir, exist_ok=True)
    os.makedirs(os.path.join(assets_glyph_dir, 'headers'), exist_ok=True)
    os.makedirs(data_dir, exist_ok=True)

    db_path = os.path.join(db_dir, 'sellipi.db')
    if os.path.exists(db_path):
        os.remove(db_path)

    # 1. Open Excel zip and extract drawing matrix
    z = zipfile.ZipFile(excel_path)
    rels_root = ET.fromstring(z.read('xl/drawings/_rels/drawing1.xml.rels'))
    rid_to_target = {rel.attrib['Id']: rel.attrib['Target'] for rel in rels_root}
    d_root = ET.fromstring(z.read('xl/drawings/drawing1.xml'))

    grid_images = {}
    for child in d_root:
        from_el = child.find('{http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing}from')
        blip_el = child.find('.//{http://schemas.openxmlformats.org/drawingml/2006/main}blip')
        if from_el is None or blip_el is None:
            continue
        embed_id = blip_el.attrib.get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}embed')
        target_img = rid_to_target.get(embed_id, '').replace('../', 'xl/')
        r = int(from_el.find('{http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing}row').text)
        c = int(from_el.find('{http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing}col').text)
        grid_images[(r, c)] = target_img

    print(f"Discovered {len(grid_images)} glyph images embedded in Excel drawing matrix.")

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    cur.execute('PRAGMA foreign_keys = ON;')

    # 2. Schema Creation
    cur.executescript('''
    CREATE TABLE IF NOT EXISTS sites (
        id TEXT PRIMARY KEY,
        name_si TEXT NOT NULL,
        name_ta TEXT NOT NULL,
        name_en TEXT NOT NULL,
        latitude REAL NOT NULL,
        longitude REAL NOT NULL,
        geofence_radius_m REAL NOT NULL,
        permit_reference TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS periods (
        id TEXT PRIMARY KEY,
        period_number INTEGER NOT NULL,
        label_si TEXT NOT NULL,
        label_ta TEXT NOT NULL,
        label_en TEXT NOT NULL,
        year_start INTEGER NOT NULL,
        year_end INTEGER NOT NULL,
        chart_column_refs TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS letters (
        id TEXT PRIMARY KEY,
        row_index INTEGER NOT NULL,
        modern_sinhala_codepoint TEXT NOT NULL,
        romanisation TEXT NOT NULL,
        letter_name TEXT NOT NULL,
        glyph_image_path TEXT
    );

    CREATE TABLE IF NOT EXISTS letter_forms (
        id TEXT PRIMARY KEY,
        letter_id TEXT NOT NULL,
        period_id TEXT NOT NULL,
        grid_row INTEGER NOT NULL,
        grid_col INTEGER NOT NULL,
        vector_path TEXT,
        image_asset_path TEXT,
        is_attested INTEGER NOT NULL DEFAULT 0,
        is_reconstructed INTEGER NOT NULL DEFAULT 0,
        source_inscription_ref TEXT,
        provenance TEXT NOT NULL DEFAULT 'authentic_epigraphic',
        FOREIGN KEY(letter_id) REFERENCES letters(id) ON DELETE CASCADE,
        FOREIGN KEY(period_id) REFERENCES periods(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS inscriptions (
        id TEXT PRIMARY KEY,
        site_id TEXT NOT NULL,
        name_si TEXT NOT NULL,
        name_ta TEXT NOT NULL,
        name_en TEXT NOT NULL,
        date_range_start INTEGER NOT NULL,
        date_range_end INTEGER NOT NULL,
        dating_basis TEXT NOT NULL,
        primary_period_id TEXT NOT NULL,
        reference_photo TEXT,
        arcore_target_score INTEGER NOT NULL DEFAULT 0,
        alignment_strategy TEXT NOT NULL,
        source_citation TEXT NOT NULL,
        FOREIGN KEY(site_id) REFERENCES sites(id) ON DELETE CASCADE,
        FOREIGN KEY(primary_period_id) REFERENCES periods(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS transcription_lines (
        id TEXT PRIMARY KEY,
        inscription_id TEXT NOT NULL,
        line_number INTEGER NOT NULL,
        text_original TEXT NOT NULL,
        text_modern_sinhala TEXT NOT NULL,
        translation_si TEXT NOT NULL,
        translation_ta TEXT NOT NULL,
        translation_en TEXT NOT NULL,
        FOREIGN KEY(inscription_id) REFERENCES inscriptions(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS glyph_occurrences (
        id TEXT PRIMARY KEY,
        transcription_line_id TEXT NOT NULL,
        position INTEGER NOT NULL,
        letter_id TEXT NOT NULL,
        bbox_x REAL NOT NULL,
        bbox_y REAL NOT NULL,
        bbox_w REAL NOT NULL,
        bbox_h REAL NOT NULL,
        FOREIGN KEY(transcription_line_id) REFERENCES transcription_lines(id) ON DELETE CASCADE,
        FOREIGN KEY(letter_id) REFERENCES letters(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS capture_sessions (
        id TEXT PRIMARY KEY,
        inscription_id TEXT NOT NULL,
        device_model TEXT NOT NULL,
        timestamp INTEGER NOT NULL,
        gps_lat REAL NOT NULL,
        gps_lon REAL NOT NULL,
        gps_accuracy REAL NOT NULL,
        pose_matrix TEXT NOT NULL,
        scale_mm_per_px REAL NOT NULL,
        image_paths TEXT NOT NULL,
        permit_reference TEXT NOT NULL,
        FOREIGN KEY(inscription_id) REFERENCES inscriptions(id) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_inscriptions_site ON inscriptions(site_id);
    CREATE INDEX IF NOT EXISTS idx_transcription_lines_insc ON transcription_lines(inscription_id);
    CREATE INDEX IF NOT EXISTS idx_glyph_occurrences_line ON glyph_occurrences(transcription_line_id);
    CREATE INDEX IF NOT EXISTS idx_glyph_occurrences_letter ON glyph_occurrences(letter_id);
    CREATE INDEX IF NOT EXISTS idx_letter_forms_letter_period ON letter_forms(letter_id, period_id);
    ''')

    # 3. Seed Periods
    period_data = [
        ('P01', 1, 'ක්‍රි.පූ. 3 වන සියවස (මුල් බ්‍රාහ්මී)', 'கி.மு. 3ஆம் நூற்றாண்டு (ஆரம்ப பிராமி)', '3rd c. BCE (Early Brahmi)', -300, -201, 'Col 1'),
        ('P02', 2, 'ක්‍රි.පූ. 2 වන සියවස (මුල් බ්‍රාහ්මී)', 'கி.மு. 2ஆம் நூற்றாண்டு (ஆரம்ப பிராமி)', '2nd c. BCE (Early Brahmi)', -200, -101, 'Col 2'),
        ('P03', 3, 'ක්‍රි.පූ. 1 වන සියවස (මුල් බ්‍රාහ්මී)', 'கி.மு. 1ஆம் நூற்றாண்டு (ஆරம்ப பிராமி)', '1st c. BCE (Early Brahmi)', -100, -1, 'Col 3'),
        ('P04', 4, 'ක්‍රි.ව. 1 වන සියවස (මුල් බ්‍රාහ්මී)', 'கி.பி. 1ஆம் நூற்றாண்டு (ஆரம்ப பிராமி)', '1st c. CE (Early Brahmi)', 1, 100, 'Col 4'),
        ('P05', 5, 'ක්‍රි.ව. 2 වන සියවස - මුල් භාගය', 'கி.பி. 2ஆம் நூற்றாண்டு - முற்பகுதி', '2nd c. CE (Early Phase)', 101, 150, 'Col 5'),
        ('P06', 6, 'ක්‍රි.ව. 2 වන සියවස - පසු භාගය', 'கி.பி. 2ஆம் நூற்றாண்டு - பிற்பகுதி', '2nd c. CE (Late Phase)', 151, 200, 'Col 6'),
        ('P07', 7, 'ක්‍රි.ව. 3 වන සියවස - මුල් භාගය', 'கி.பி. 3ஆம் நூற்றாண்டு - முற்பகுதி', '3rd c. CE (Early Phase)', 201, 250, 'Col 7'),
        ('P08', 8, 'ක්‍රි.ව. 3 වන සියවස - පසු භාගය', 'கி.பி. 3ஆம் நூற்றாண்டு - பிற்பகுதி', '3rd c. CE (Late Phase)', 251, 300, 'Col 8'),
        ('P09', 9, 'ක්‍රි.ව. 4 වන සියවස - මුල් භාගය', 'கி.பி. 4ஆம் நூற்றாண்டு - முற்பகுதி', '4th c. CE (Early Phase)', 301, 350, 'Col 9'),
        ('P10', 10, 'ක්‍රි.ව. 4 වන සියවස - පසු භාගය', 'கி.பி. 4ஆம் நூற்றாண்டு - பிற்பகுதி', '4th c. CE (Late Phase)', 351, 400, 'Col 10'),
        ('P11', 11, 'ක්‍රි.ව. 5 වන සියවස - මුල් භාගය', 'கி.பி. 5ஆம் நூற்றாண்டு - முற்பகுதி', '5th c. CE (Early Phase)', 401, 450, 'Col 11'),
        ('P12', 12, 'ක්‍රි.ව. 5 වන සියවස - පසු භාගය', 'கி.பி. 5ஆம் நூற்றாண்டு - பிற்பகுதி', '5th c. CE (Late Phase)', 451, 500, 'Col 12'),
        ('P13', 13, 'ක්‍රි.ව. 6 වන සියවස - මුල් භාගය', 'கி.பி. 6ஆம் நூற்றாண்டு - முற்பகுதி', '6th c. CE (Early Phase)', 501, 550, 'Col 13'),
        ('P14', 14, 'ක්‍රි.ව. 6 වන සියවස - පසු භාගය', 'கி.பி. 6ஆம் நூற்றாண்டு - பிற்பகுதி', '6th c. CE (Late Phase)', 551, 600, 'Col 14'),
        ('P15', 15, 'ක්‍රි.ව. 7 වන සියවස - මුල් භාගය', 'கி.பி. 7ஆம் நூற்றாண்டு - முற்பகுதி', '7th c. CE (Early Phase)', 601, 650, 'Col 15'),
        ('P16', 16, 'ක්‍රි.ව. 7 වන සියවස - පසු භාගය', 'கி.பி. 7ஆம் நூற்றாண்டு - பிற்பகுதி', '7th c. CE (Late Phase)', 651, 700, 'Col 16'),
        ('P17', 17, 'ක්‍රි.ව. 8 වන සියවස (මධ්‍යතන)', 'கி.பி. 8ஆம் நூற்றாண்டு', '8th c. CE (Medieval Phase)', 701, 800, 'Col 17'),
        ('P18', 18, 'ක්‍රි.ව. 9-10 වන සියවස (සම්භාව්‍ය)', 'கி.பி. 9-10ஆம் நூற்றாண்டு', '9th-10th c. CE (Classical Phase)', 801, 1000, 'Col 18')
    ]
    cur.executemany('''
    INSERT INTO periods (id, period_number, label_si, label_ta, label_en, year_start, year_end, chart_column_refs)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    ''', period_data)

    # 4. Seed Letters
    letter_definitions = [
        (1, 'අ', 'a', 'Akaraya (Vowel A)'),
        (2, 'ආ', 'ā', 'Aakaraya (Long Vowel Aa)'),
        (3, 'ඇ', 'æ', 'Aekaraya (Vowel Ae)'),
        (4, 'ඉ', 'i', 'Ikaraya (Vowel I)'),
        (5, 'ඊ', 'ī', 'Iikaraya (Long Vowel Ii)'),
        (6, 'උ', 'u', 'Ukaraya (Vowel U)'),
        (7, 'ඌ', 'ū', 'Uukaraya (Long Vowel Uu)'),
        (8, 'එ', 'e', 'Ekaraya (Vowel E)'),
        (9, 'ඒ', 'ē', 'Eekaraya (Long Vowel Ee)'),
        (10, 'ඔ', 'o', 'Okaraya (Vowel O)'),
        (11, 'ක', 'ka', 'Kayanna (Velar Plosive Ka)'),
        (12, 'ඛ', 'kha', 'Mahaprana Khayanna (Kha)'),
        (13, 'ග', 'ga', 'Gayanna (Voiced Velar Ga)'),
        (14, 'ඝ', 'gha', 'Mahaprana Ghayanna (Gha)'),
        (15, 'ඞ', 'ṅa', 'Kantaja Sangyaka Nga'),
        (16, 'ච', 'ca', 'Cayanna (Palatal Plosive Ca)'),
        (17, 'ඡ', 'cha', 'Mahaprana Chayanna (Cha)'),
        (18, 'ජ', 'ja', 'Jayanna (Voiced Palatal Ja)'),
        (19, 'ඣ', 'jha', 'Mahaprana Jhayanna (Jha)'),
        (20, 'ඤ', 'ña', 'Talaja Nyayanna (Nya)'),
        (21, 'ට', 'ṭa', 'Tayanna (Retroflex Plosive Ta)'),
        (22, 'ඨ', 'ṭha', 'Mahaprana Tthayanna (Ttha)'),
        (23, 'ඩ', 'ḍa', 'Dayanna (Voiced Retroflex Da)'),
        (24, 'ඪ', 'ḍha', 'Mahaprana Ddhayanna (Ddha)'),
        (25, 'ණ', 'ṇa', 'Murdhaja Nayanna (Retroflex Na)'),
        (26, 'ත', 'ta', 'Tayanna (Dental Plosive Ta)'),
        (27, 'ථ', 'tha', 'Mahaprana Thayanna (Tha)'),
        (28, 'ද', 'da', 'Dayanna (Voiced Dental Da)'),
        (29, 'ධ', 'dha', 'Mahaprana Dhayanna (Dha)'),
        (30, 'න', 'na', 'Dantaja Nayanna (Dental Na)'),
        (31, 'ප', 'pa', 'Payanna (Labial Plosive Pa)'),
        (32, 'ඵ', 'pha', 'Mahaprana Phayanna (Pha)'),
        (33, 'බ', 'ba', 'Bayanna (Voiced Labial Ba)'),
        (34, 'භ', 'bha', 'Mahaprana Bhayanna (Bha)'),
        (35, 'ම', 'ma', 'Mayanna (Labial Nasal Ma)'),
        (36, 'ය', 'ya', 'Yayanna (Approximant Ya)'),
        (37, 'ර', 'ra', 'Rayanna (Rhotic Ra)')
    ]

    letters_records = []
    for r_idx, cp, rom, name in letter_definitions:
        l_id = f'L{r_idx:02d}'
        img_asset = None
        if (r_idx, 0) in grid_images:
            img_bytes = z.read(grid_images[(r_idx, 0)])
            img_filename = f'letter_{l_id}_modern.png'
            out_p = os.path.join(assets_glyph_dir, img_filename)
            with open(out_p, 'wb') as f:
                f.write(img_bytes)
            img_asset = f'glyph_images/{img_filename}'
        letters_records.append((l_id, r_idx, cp, rom, name, img_asset))

    cur.executemany('''
    INSERT INTO letters (id, row_index, modern_sinhala_codepoint, romanisation, letter_name, glyph_image_path)
    VALUES (?, ?, ?, ?, ?, ?)
    ''', letters_records)

    # 5. Extract Header Images & LetterForm matrix
    for c in range(1, 19):
        if (0, c) in grid_images:
            img_bytes = z.read(grid_images[(0, c)])
            with open(os.path.join(assets_glyph_dir, 'headers', f'col_{c:02d}.png'), 'wb') as f:
                f.write(img_bytes)

    letter_forms_records = []
    for r_idx in range(1, 38):
        l_id = f'L{r_idx:02d}'
        for c_idx in range(1, 19):
            p_id = f'P{c_idx:02d}'
            lf_id = f'LF_{l_id}_{p_id}'
            is_attested = 1 if (r_idx, c_idx) in grid_images else 0
            img_asset = None
            if is_attested:
                img_bytes = z.read(grid_images[(r_idx, c_idx)])
                img_filename = f'glyph_{l_id}_{p_id}.png'
                out_p = os.path.join(assets_glyph_dir, img_filename)
                with open(out_p, 'wb') as f:
                    f.write(img_bytes)
                img_asset = f'glyph_images/{img_filename}'
            
            src_ref = f'Aksharamalawa Matrix Row {r_idx}, Col {c_idx}' if is_attested else None
            letter_forms_records.append((
                lf_id, l_id, p_id, r_idx, c_idx, None, img_asset, is_attested, 0, src_ref, 'authentic_epigraphic'
            ))

    cur.executemany('''
    INSERT INTO letter_forms (id, letter_id, period_id, grid_row, grid_col, vector_path, image_asset_path, is_attested, is_reconstructed, source_inscription_ref, provenance)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ''', letter_forms_records)

    # 6. Seed Sites
    sites_data = [
        ('SITE_MIHINTALE', 'මිහින්තලේ පුරාවිද්‍යා පරිශ්‍රය', 'மிஹிந்தலை தொல்பொருள் வளாகம்', 'Mihintale Archaeological Reserve', 8.3508, 80.5097, 750.0, 'ARCH-PERMIT-2026-MIH-001'),
        ('SITE_ANURADHAPURA', 'අනුරාධපුර මහා විහාර පරිශ්‍රය', 'அனுராதபுரம் மகா விகாரை வளாகம்', 'Anuradhapura Mahavihara Sanctuary', 8.3536, 80.3965, 1200.0, 'ARCH-PERMIT-2026-ANP-042'),
        ('SITE_POLONNARUWA', 'පොළොන්නරුව ආළාහණ පිරිවෙන', 'பொலன்னறுவை ஆலகன பிரிவென', 'Polonnaruwa Alahana Parivena', 7.9403, 81.0003, 900.0, 'ARCH-PERMIT-2026-POL-018'),
        ('SITE_RITIGALA', 'රිටිගල ආරණ්‍ය සේනාසනය', 'ரிட்டிகல வன ஆசிரமம்', 'Ritigala Strict Nature & Monastic Reserve', 8.1189, 80.6558, 600.0, 'ARCH-PERMIT-2026-RIT-007'),
        ('SITE_SIGIRIYA', 'සීගිරිය රාජකීය පරිශ්‍රය', 'சீகிரியா அரச வளாகம்', 'Sigiriya Royal Complex & Inscriptions', 7.9570, 80.7603, 800.0, 'ARCH-PERMIT-2026-SIG-011')
    ]
    cur.executemany('''
    INSERT INTO sites (id, name_si, name_ta, name_en, latitude, longitude, geofence_radius_m, permit_reference)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    ''', sites_data)

    # 7. Seed Inscriptions
    inscriptions_data = [
        (
            'INSC_MIH_01', 'SITE_MIHINTALE',
            'මිහින්තලේ 4 වන මිහිඳු රජුගේ පුවරු ලිපිය',
            'மிஹிந்தலை 4ஆம் மஹிந்த மன்னனின் பலகை கல்வெட்டு',
            'Mihintale Slab Inscription of King Mahinda IV',
            956, 972, 'regnal', 'P18',
            'reference_targets/mihintale_slab_ref.jpg', 88, 'image_target',
            'Epigraphia Zeylanica, Vol. I, pp. 75-113; Inscriptions of Ceylon Vol. V'
        ),
        (
            'INSC_ANP_01', 'SITE_ANURADHAPURA',
            'වෙස්සගිරිය ප්‍රාථමික ලෙන් ලිපි (දෙවනපිය තිස්ස රජ සමය)',
            'வெஸ்ஸகிரிய ஆரம்ப குகை கல்வெட்டு (தேவனம்பிய திஸ்ஸ)',
            'Vessagiriya Early Cave Inscriptions (Devanampiya Tissa)',
            -250, -210, 'palaeographic', 'P01',
            'reference_targets/vessagiriya_cave_ref.jpg', 92, 'image_target',
            'Epigraphia Zeylanica, Vol. I, pp. 10-39; Inscriptions of Ceylon Vol. I, No. 1-28'
        ),
        (
            'INSC_ANP_02', 'SITE_ANURADHAPURA',
            'ථූපාරාම ස්ලැබ් ලිපිය (ගජබාහු රජ සමය)',
            'தூபாராம பலகை கல்வெட்டு (முதலாம் கஜபாகு)',
            'Thuparama Slab Inscription of King Gajabahu I',
            114, 136, 'epigraphic', 'P05',
            'reference_targets/thuparama_slab_ref.jpg', 84, 'image_target',
            'Epigraphia Zeylanica, Vol. III, pp. 114-119'
        ),
        (
            'INSC_POL_01', 'SITE_POLONNARUWA',
            'පොළොන්නරුව ගල්පොත ශිලා ලිපිය (නිශ්ශංකමල්ල රජ)',
            'பொலன்னறுவை கல் பொத்த கல்வெட்டு (நிஸங்க மல்லன்)',
            'Polonnaruwa Gal Potha Stone Book Inscription (Nissanka Malla)',
            1187, 1196, 'regnal', 'P18',
            'reference_targets/gal_potha_ref.jpg', 79, 'marker_offset',
            'Epigraphia Zeylanica, Vol. II, pp. 98-123'
        ),
        (
            'INSC_RIT_01', 'SITE_RITIGALA',
            'රිටිගල ලෙන් ලිපිය (මහාදාඨික මහානාග රජ)',
            'ரிட்டிகல குகை கல்வெட்டு (மஹாதாதிக மகாநாகன்)',
            'Ritigala Cave Inscription of King Mahadathika Mahanaga',
            7, 19, 'stratigraphic', 'P04',
            'reference_targets/ritigala_cave_ref.jpg', 68, 'manual',
            'Epigraphia Zeylanica, Vol. I, pp. 135-153'
        )
    ]
    cur.executemany('''
    INSERT INTO inscriptions (id, site_id, name_si, name_ta, name_en, date_range_start, date_range_end, dating_basis, primary_period_id, reference_photo, arcore_target_score, alignment_strategy, source_citation)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ''', inscriptions_data)

    # 8. Seed Sample Transcription Lines
    lines_data = [
        (
            'LINE_VESS_01', 'INSC_ANP_01', 1,
            'දෙවනපිය මහරඣහ ගමිණි තිශහ ලෙණෙ',
            'දේවානම්පිය මහරජ ගාමිණී තිස්සගේ ලෙන',
            'දේවානම්පිය මහරජ ගාමිණී තිස්සගේ ලෙන සංඝයාට පූජා කරන ලදී.',
            'தேவனாம்பிரிய மஹாராஜா காமிணி திஸ்ஸவின் குகை',
            'The cave of the Great King Gaminie Tissa, Friend of the Gods, is dedicated to the Sangha.'
        ),
        (
            'LINE_VESS_02', 'INSC_ANP_01', 2,
            'අගත අනගත චතුදිශ සගශ දිනෙ',
            'පැමිණි නොපැමිණි සිව්දිග සංඝයාට පූජා කරන ලදී',
            'පැමිණි නොපැමිණි සිව්දිග මහා සංඝයා වහන්සේලා උදෙසා පූජා කරන ලදී.',
            'நான்கு திசைகளிலிருந்தும் வந்த மற்றும் வரும் சங்கத்தினருக்கு அர்ப்பணிக்கப்பட்டது',
            'Dedicated to the Sangha of the four quarters, present and absent.'
        )
    ]
    cur.executemany('''
    INSERT INTO transcription_lines (id, inscription_id, line_number, text_original, text_modern_sinhala, translation_si, translation_ta, translation_en)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    ''', lines_data)

    # 9. Seed Glyph Occurrences
    sample_glyphs = [
        ('GLYPH_V1_01', 'LINE_VESS_01', 1, 'L28', 0.08, 0.32, 0.07, 0.36),
        ('GLYPH_V1_02', 'LINE_VESS_01', 2, 'L37', 0.16, 0.30, 0.07, 0.38),
        ('GLYPH_V1_03', 'LINE_VESS_01', 3, 'L30', 0.24, 0.31, 0.07, 0.37),
        ('GLYPH_V1_04', 'LINE_VESS_01', 4, 'L31', 0.32, 0.29, 0.07, 0.40),
        ('GLYPH_V1_05', 'LINE_VESS_01', 5, 'L36', 0.40, 0.33, 0.08, 0.35),
        ('GLYPH_V1_06', 'LINE_VESS_01', 6, 'L35', 0.49, 0.32, 0.07, 0.36),
        ('GLYPH_V1_07', 'LINE_VESS_01', 7, 'L37', 0.57, 0.28, 0.07, 0.42),
        ('GLYPH_V1_08', 'LINE_VESS_01', 8, 'L37', 0.65, 0.30, 0.07, 0.38),
        ('GLYPH_V1_09', 'LINE_VESS_01', 9, 'L19', 0.73, 0.29, 0.08, 0.40),
        ('GLYPH_V1_10', 'LINE_VESS_01', 10, 'L37', 0.82, 0.28, 0.07, 0.42)
    ]
    cur.executemany('''
    INSERT INTO glyph_occurrences (id, transcription_line_id, position, letter_id, bbox_x, bbox_y, bbox_w, bbox_h)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    ''', sample_glyphs)

    conn.commit()

    import shutil
    shutil.copyfile(db_path, 'data/sellipi.db')
    print("Database built successfully at:")
    print("  -> app/src/main/assets/databases/sellipi.db")
    print("  -> data/sellipi.db")
    conn.close()

if __name__ == '__main__':
    build_database()
