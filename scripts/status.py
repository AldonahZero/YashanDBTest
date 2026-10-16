#!/usr/bin/env python3
"""Print the seven-day campaign status and live oracle counters."""
import datetime as dt
import json
import os
from pathlib import Path

root = Path(__file__).resolve().parents[1]
folder = root / 'campaigns/tlp-7d'
path = folder / 'status.json'
if not path.exists():
    print('Seven-day campaign has not started.')
    raise SystemExit(1)
status = json.loads(path.read_text())
zone = dt.timezone(dt.timedelta(hours=8))
config = status['config']
print('State:', status['state'])
for label, key in [('Started', 'start_epoch'), ('Deadline', 'deadline_epoch')]:
    print(label + ':', dt.datetime.fromtimestamp(config[key], zone).isoformat())
print('Finished attempts:', status['finished_attempts'])
if status.get('attention'):
    print('Needs attention:', status['attention'])
for worker in status['active']:
    lines = Path(worker['launcher_log']).read_text().splitlines()
    print('\nWorker', worker['worker'], 'seed', worker['seed'], 'launcher PID', worker['launcher_pid'])
    if lines and lines[0].startswith(str(root / 'results')):
        run = Path(lines[0])
        print('Run:', run)
        if (run / 'oracle-metrics.json').exists():
            try:
                print('Oracle:', (run / 'oracle-metrics.json').read_text().strip())
            except FileNotFoundError:
                pass
        if (run / 'sqlancer.pid').exists():
            pid = int((run / 'sqlancer.pid').read_text())
            print('SQLancer PID:', pid, 'alive:', Path('/proc', str(pid)).exists())
