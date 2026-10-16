#!/usr/bin/env python3
"""Supervise bounded TLP workers until a persistent seven-day deadline."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
STOP = False


def atomic_json(path, data):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(data, indent=2))
    temporary.replace(path)


def stopping(_signal, _frame):
    global STOP
    STOP = True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--days', type=float, default=7)
    parser.add_argument('--workers', type=int, default=2)
    parser.add_argument('--seed', type=int, default=2026101600)
    parser.add_argument('--quantum', type=int, default=3600)
    args = parser.parse_args()
    if not 1 <= args.workers <= 4 or args.days <= 0 or args.quantum < 10:
        parser.error('Invalid worker count, duration, or quantum')
    directory = ROOT / 'campaigns/tlp-7d'
    directory.mkdir(parents=True, exist_ok=True)
    config_path = directory / 'config.json'
    if config_path.exists():
        config = json.loads(config_path.read_text())
    else:
        now = time.time()
        config = {'start_epoch': now, 'deadline_epoch': now + args.days * 86400,
                  'workers': args.workers, 'seed': args.seed, 'quantum_seconds': args.quantum,
                  'oracle': 'TLP_WHERE', 'jar_sha256': __import__('hashlib').sha256(
                      (ROOT / 'sqlancer/target/sqlancer-2.0.0.jar').read_bytes()).hexdigest()}
        atomic_json(config_path, config)
    signal.signal(signal.SIGTERM, stopping)
    signal.signal(signal.SIGINT, stopping)
    history_path = directory / 'history.json'
    history = json.loads(history_path.read_text()) if history_path.exists() else []
    active = {}
    open_logs = {}
    short_failures = {i: 0 for i in range(config['workers'])}
    attention = None
    next_number = len(history)
    try:
        while not STOP and time.time() < config['deadline_epoch']:
            now = time.time()
            if shutil.disk_usage(ROOT).free < 20 * 1024 ** 3:
                attention = 'Disk free space below 20 GiB; preserved logs and stopped workers'
                break
            for worker in range(config['workers']):
                if worker in active:
                    process, record = active[worker]
                    if process.poll() is not None:
                        open_logs.pop(worker).close()
                        record['launcher_exit_code'] = process.returncode
                        record['end_epoch'] = time.time()
                        lines = Path(record['launcher_log']).read_text().splitlines()
                        if lines and lines[0].startswith(str(ROOT / 'results')):
                            record['run_directory'] = lines[0]
                            summary = Path(lines[0]) / 'summary.json'
                            if summary.exists():
                                record['summary'] = json.loads(summary.read_text())
                        history.append(record)
                        atomic_json(history_path, history)
                        del active[worker]
                        completed = record.get('summary', {}).get('oracle_metrics', {}).get('completed', 0)
                        if process.returncode != 0 and completed < 100:
                            short_failures[worker] += 1
                        else:
                            short_failures[worker] = 0
                        print(json.dumps({'event': 'worker_finished', **record}), flush=True)
                        if short_failures[worker] >= 3:
                            attention = f'Worker {worker}: three consecutive failures before 100 checks'
                            break
                if worker not in active:
                    remaining = int(config['deadline_epoch'] - time.time())
                    if remaining < 1:
                        break
                    seconds = min(config['quantum_seconds'], remaining)
                    next_number += 1
                    logfile = directory / f'worker-{worker}-attempt-{next_number}.log'
                    handle = logfile.open('w')
                    seed = config['seed'] + next_number
                    command = [sys.executable, '-u', str(ROOT / 'scripts/run.py'), '--seconds', str(seconds),
                               '--queries', '1000', '--seed', str(seed), '--threads', '1',
                               '--oracle', 'TLP_WHERE', '--label', 'tlp-7d']
                    process = subprocess.Popen(command, stdout=handle, stderr=subprocess.STDOUT,
                                               cwd=ROOT, start_new_session=True)
                    record = {'worker': worker, 'attempt': next_number, 'seed': seed,
                              'launcher_pid': process.pid, 'start_epoch': time.time(),
                              'launcher_log': str(logfile)}
                    active[worker] = process, record
                    open_logs[worker] = handle
                    print(json.dumps({'event': 'worker_started', **record}), flush=True)
            state = {'state': 'needs_attention' if attention else 'running',
                     'supervisor_pid': os.getpid(), 'updated_epoch': time.time(),
                     'config': config, 'active': [record for _, record in active.values()],
                     'finished_attempts': len(history), 'attention': attention}
            atomic_json(directory / 'status.json', state)
            if attention:
                break
            time.sleep(5)
    finally:
        for process, _ in active.values():
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
        for process, record in active.values():
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            record['end_epoch'] = time.time()
            record['termination'] = 'supervisor_shutdown'
            history.append(record)
        for handle in open_logs.values():
            handle.close()
        atomic_json(history_path, history)
        state_name = 'needs_attention' if attention else (
            'stopped' if STOP else 'completed')
        atomic_json(directory / 'status.json', {
            'state': state_name, 'updated_epoch': time.time(), 'config': config,
            'active': [], 'finished_attempts': len(history), 'attention': attention})
        print(json.dumps({'event': state_name, 'attention': attention}), flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
