"""Host SQLite integration check against the SQL in the actual Kotlin migrations.

Reads exported Room schemas and executes populated upgrades with legacy ALTER mode and foreign keys off during upgrade.
Android MigrationTestHelper remains the authoritative Room/device gate.
"""
from pathlib import Path
import json
import re
import sqlite3
import time

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / 'app/schemas/com.singam.lionlibrary.data.local.db.MediaDatabase'
SOURCE = (ROOT / 'app/src/main/java/com/singam/lionlibrary/data/local/db/DatabaseMigrations.kt').read_text(encoding='utf-8')
REBUILDS = re.findall(r'rebuild\(\s*db,\s*"(\w+)",\s*"([^"]+)",\s*linkedMapOf\((.*?)\n\s*\),?\s*\)', SOURCE, re.S)
FINAL = re.findall(r'db.execSQL\(\s*"((?:DROP TABLE|ALTER TABLE|CREATE TABLE `|INSERT INTO `(?:episodes|seasons)`|CREATE (?:UNIQUE )?INDEX)[^"]+)"', SOURCE)
assert len(REBUILDS) == 4, 'Migration SQL could not be extracted; update this integration harness'


def create_schema(db, schema):
    for entity in schema['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))


def seed(db, schema):
    fixtures = {
        'media': {'id': 42, 'tmdbId': 123, 'title': '千と千尋 😀', 'mediaType': 'TV_SHOW', 'matchConfidence': 1, 'isUnidentified': 0, 'lastUpdated': 123,
                  'preferredEngine': 'LIBVLC', 'externalSubtitlePath': 'content://fixture/subtitle', 'letterboxdRating': 4.5},
        'seasons': {'id': 51, 'showId': 42, 'seasonNumber': 1, 'name': 'Season 1'},
        'episodes': {'id': 61, 'showId': 42, 'seasonNumber': 1, 'episodeNumber': 1, 'filePath': 'content://fixture/episode', 'preferredEngine': 'LIBVLC'},
        'watch_progress': {'mediaId': 42, 'episodeId': 61, 'progress': .5, 'lastPositionMs': 500, 'durationMs': 1000, 'lastWatched': 123, 'completed': 0, 'isHidden': 1},
    }
    for entity in schema['entities']:
        fields = {f['columnName'] for f in entity['fields']}
        values = {k: v for k, v in fixtures[entity['tableName']].items() if k in fields}
        db.execute(f"INSERT INTO {entity['tableName']} ({','.join(values)}) VALUES ({','.join('?' for _ in values)})", list(values.values()))


def upgrade(db):
    for table, create, defaults in REBUILDS:
        columns = {row[1] for row in db.execute(f'PRAGMA table_info({table})')}
        values = re.findall(r'"(\w+)" to "([^"]*)"', defaults)
        db.execute(create)
        select = [f'`{name}`' if name in columns else f'`{fallback}`' if fallback in columns else fallback for name, fallback in values]
        db.execute(f"INSERT INTO {table}_new ({','.join('`'+name+'`' for name,_ in values)}) SELECT {','.join(select)} FROM {table}")
    for sql in FINAL:
        db.execute(sql)


def signature(db):
    return {table: (list(db.execute(f'PRAGMA table_info({table})')), list(db.execute(f'PRAGMA foreign_key_list({table})')),
                    list(db.execute(f'PRAGMA index_list({table})'))) for table, _, _ in REBUILDS}


def main():
    target = json.loads((SCHEMAS / '13.json').read_text(encoding='utf-8'))['database']
    reference = sqlite3.connect(':memory:')
    create_schema(reference, target)
    expected = signature(reference)
    for version in range(6, 13):
        schema = json.loads((SCHEMAS / f'{version}.json').read_text(encoding='utf-8'))['database']
        db = sqlite3.connect(':memory:')
        db.execute('PRAGMA foreign_keys=OFF')
        db.execute('PRAGMA legacy_alter_table=ON')
        create_schema(db, schema)
        seed(db, schema)
        upgrade(db)
        assert signature(db) == expected, f'Schema {version} differs from exported schema 13'
        assert db.execute('SELECT id,title,addedAt,isWatchlisted FROM media').fetchone() == (42, '千と千尋 😀', 123, 0)
        assert db.execute('SELECT id,showId FROM episodes').fetchone() == (61, 42)
        assert db.execute('SELECT mediaId,episodeId,lastPositionMs FROM watch_progress').fetchone() == (42, 61, 500)
        assert db.execute('PRAGMA foreign_key_check').fetchall() == []
        assert db.execute('SELECT preferredEngine FROM episodes').fetchone()[0] == ('LIBVLC' if version >= 10 else 'EXOPLAYER')
        db.close()
        print(f'PASS populated schema {version} -> 13')
    # Real query performance smoke: synthetic 1k titles and 10k episodes, on host SQLite.
    db = reference
    for index in range(1, 1001):
        db.execute("INSERT INTO media(id,title,mediaType,matchConfidence,isUnidentified,lastUpdated,preferredEngine) VALUES(?,?, 'TV_SHOW',1,0,1,'EXOPLAYER')", (index, f'Title {index:04}'))
        for episode in range(1, 11):
            db.execute("INSERT INTO episodes(showId,seasonNumber,episodeNumber,filePath,preferredEngine) VALUES(?,1,?,?,'EXOPLAYER')", (index, episode, f'content://fixture/{index}/{episode}'))
    dao = (ROOT/'app/src/main/java/com/singam/lionlibrary/data/local/db/dao/MediaDao.kt').read_text(encoding='utf-8')
    query = re.search(r'@Query\(\s*"""(\s*SELECT m\.\*.*?)"""', dao, re.S)[1]
    start = time.perf_counter()
    rows = db.execute(query, dict(text='', pattern='%%', type='ALL', genre='', year=None, watch='ALL', sort='TITLE_ASC', watchlist=0)).fetchall()
    duration = (time.perf_counter() - start) * 1000
    assert len(rows) == 1000
    print(f'PASS 1,000 titles / 10,000 episodes browse: {duration:.1f} ms (host SQLite; not an Android benchmark)')


if __name__ == '__main__':
    main()
