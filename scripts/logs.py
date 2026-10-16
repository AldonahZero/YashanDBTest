#!/usr/bin/env python3
"""Follow one worker's console or current SQL, including hourly run changes."""
import argparse
import json
from pathlib import Path
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--worker', type=int, default=0)
parser.add_argument('--sql', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
status_path = root / 'campaigns/tlp-7d/status.json'
current = None
position = 0
try:
    while True:
        status = json.loads(status_path.read_text())
        workers = [w for w in status['active'] if w['worker'] == args.worker]
        if not workers:
            if status['state'] != 'running':
                print('Campaign:', status['state'], flush=True)
                break
            time.sleep(1)
            continue
        lines = Path(workers[0]['launcher_log']).read_text().splitlines()
        if not lines or not lines[0].startswith(str(root / 'results')):
            time.sleep(1)
            continue
        run = Path(lines[0])
        path = run / 'console.log'
        if args.sql:
            candidates = list((run / 'logs/yashandb').glob('*-cur.log'))
            if not candidates:
                time.sleep(1)
                continue
            path = max(candidates, key=lambda p: p.stat().st_mtime)
        if not path.exists():
            time.sleep(1)
            continue
        size = path.stat().st_size
        if path != current:
            print('\nFollowing:', path, flush=True)
            current = path
            position = max(0, size - 8192)
        if size < position:
            position = 0
        with path.open('rb') as handle:
            handle.seek(position)
            content = handle.read()
            position = handle.tell()
        if content:
            print(content.decode(errors='replace'), end='', flush=True)
        time.sleep(1)
except KeyboardInterrupt:
    pass
