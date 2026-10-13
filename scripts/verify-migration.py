#!/usr/bin/env python3
import json, pathlib, re, sqlite3
root = pathlib.Path(__file__).resolve().parents[1]
schemas = root / 'app/schemas/dev.lad.data.LadDatabase'
old = json.loads((schemas/'1.json').read_text())['database']
new = json.loads((schemas/'3.json').read_text())['database']
db = sqlite3.connect(':memory:')
for entity in old['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    for index in entity.get('indices', []):
        db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
db.execute("INSERT INTO tracks VALUES(123,'content://test/123','Song','Artist','Album',7,60000,1,2020,'Rock','Music/',0,1,1)")
db.execute("INSERT INTO playlists VALUES(1,'Mine','123',NULL)")
db.execute("INSERT INTO listens VALUES('session',123,'Song','Artist','Album','Rock','',1000,40000,1)")
source = (root/'app/src/main/java/dev/lad/data/Database.kt').read_text().split('val MIGRATION_1_2 =',1)[1]
for statement in re.findall(r'db\.execSQL\(\s*"([^"]+)"', source): db.execute(statement)
expected = sqlite3.connect(':memory:')
for entity in new['entities']:
    expected.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    def columns(connection):
        return sorted((row[1],row[2],row[3],row[5]) for row in connection.execute(f"PRAGMA table_info('{entity['tableName']}')"))
    assert columns(db) == columns(expected), entity['tableName']
assert db.execute('SELECT favorite FROM tracks').fetchone() == (1,)
assert db.execute('SELECT tracks FROM playlists').fetchone() == ('123',)
assert db.execute('SELECT listenedMs FROM listens').fetchone() == (40000,)
print('Migration 1 -> 3: schema and existing user data verified')
