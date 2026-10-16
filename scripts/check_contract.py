#!/usr/bin/env python3
"""Check fixed NULL/duplicate/empty/join fixtures against independent expected counts."""
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
cnf = root / 'config/mysql.cnf'
database = 'ys_contract_fixture'
setup = f'''
DROP DATABASE IF EXISTS {database};
CREATE DATABASE {database};
USE {database};
CREATE TABLE t0(c0 INT, c1 INT);
CREATE TABLE t1(c0 INT);
CREATE TABLE t2(c0 INT);
INSERT INTO t0 VALUES (-1,NULL),(0,0),(1,1),(1,1),(NULL,NULL);
INSERT INTO t1 VALUES (1),(1),(NULL);
CREATE INDEX i0 ON t0(c0);
'''
command = ['mysql', '--defaults-extra-file=' + str(cnf), '--batch', '--raw', '--skip-column-names']
subprocess.run(command, input=setup, text=True, check=True, capture_output=True)
checks = [
    ('fixture_rows', 'SELECT COUNT(*) FROM t0', 5),
    ('predicate_true', 'SELECT COUNT(*) FROM t0 WHERE c1 > 0', 2),
    ('predicate_false', 'SELECT COUNT(*) FROM t0 WHERE NOT(c1 > 0)', 1),
    ('predicate_unknown', 'SELECT COUNT(*) FROM t0 WHERE CASE WHEN (c1 > 0) THEN 0 '
     'WHEN NOT(c1 > 0) THEN 0 ELSE 1 END = 1', 2),
    ('compound_unknown', 'SELECT COUNT(*) FROM t0 WHERE CASE WHEN (c1 > 0 OR c0 > 0) THEN 0 '
     'WHEN NOT(c1 > 0 OR c0 > 0) THEN 0 ELSE 1 END = 1', 2),
    ('norec', 'SELECT COALESCE(SUM(CASE WHEN c1 > 0 THEN 1 ELSE 0 END),0) FROM t0', 2),
    ('typed_nullif', 'SELECT COUNT(*) FROM t0 WHERE COALESCE(NULLIF(c0,c0),0)=0', 5),
    ('empty_count', 'SELECT COUNT(*) FROM t2 WHERE c0 > 0', 0),
    ('empty_norec', 'SELECT COALESCE(SUM(CASE WHEN c0 > 0 THEN 1 ELSE 0 END),0) FROM t2', 0),
    ('left_join_rows', 'SELECT COUNT(*) FROM t0 LEFT JOIN t1 ON t0.c0=t1.c0', 7),
    ('left_join_unmatched', 'SELECT COUNT(*) FROM t0 LEFT JOIN t1 ON t0.c0=t1.c0 WHERE t1.c0 IS NULL', 3),
    ('left_join_norec', 'SELECT COALESCE(SUM(CASE WHEN t1.c0 IS NULL THEN 1 ELSE 0 END),0) '
     'FROM t0 LEFT JOIN t1 ON t0.c0=t1.c0', 3)
]
results = []
try:
    for name, sql, expected in checks:
        p = subprocess.run(command + ['--database=' + database, '--execute=' + sql],
                           text=True, capture_output=True, check=True, timeout=30)
        actual = int(p.stdout.strip())
        results.append({'name': name, 'sql': sql, 'expected': expected, 'actual': actual})
        if actual != expected:
            raise AssertionError(f'{name}: expected {expected}, got {actual}')
finally:
    (root / 'docs/contract-check.json').write_text(json.dumps(results, indent=2))
print(json.dumps({'passed': len(results), 'total': len(checks)}))
