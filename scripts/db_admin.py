#!/usr/bin/env python3
"""Manage only the dedicated local yashandbtest cluster."""
import argparse
import json
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('action', choices=['status', 'start', 'stop', 'init-user'])
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
home = Path('/home/yashantest')
dbhome = home / 'yasdb_home/23.4.1.109'
secrets = json.loads((root / 'config/database-secrets.json').read_text())
env = os.environ.copy()
env.update(YASDB_HOME=str(dbhome), YASDB_DATA=str(home / 'data/db-1-1'),
           LD_LIBRARY_PATH=str(dbhome / 'lib'), LANG='en_US.UTF-8')
prefix = ['runuser', '--user', 'yashantest', '--']
if args.action in ('start', 'stop'):
    command = prefix + [str(dbhome / 'bin/yasboot'), 'cluster', args.action,
                        '-c', 'yashandbtest', '--disable']
    p = subprocess.run(command, cwd=home, env=env, text=True,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120)
else:
    command = prefix + [str(dbhome / 'bin/yasql'), '-S',
                        'sys/' + secrets['sys_password'] + '@127.0.0.1:1688']
    if args.action == 'init-user':
        sql = 'CREATE USER "sqlancer" IDENTIFIED BY \'' + secrets['sqlancer_password'] + "';\n"
        sql += 'GRANT ALL ON *.* TO sqlancer;\nexit;\n'
    else:
        sql = 'SELECT status FROM v$instance;\nSHOW PARAMETER COMPAT_VECTOR;\nexit;\n'
    p = subprocess.run(command, cwd=home, env=env, input=sql, text=True,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
output = p.stdout
for secret in secrets.values():
    output = output.replace(secret, '<redacted>')
print(output)
if p.returncode or 'YAS-' in output:
    raise SystemExit(p.returncode or 1)
if args.action == 'init-user':
    cnf = root / 'config/mysql.cnf'
    cnf.write_text('[client]\nuser=sqlancer\npassword="' + secrets['sqlancer_password']
                   + '"\nhost=127.0.0.1\nport=1690\nprotocol=tcp\n')
    cnf.chmod(0o600)
