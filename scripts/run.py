#!/usr/bin/env python3
"""Run one bounded SQLancer experiment in a fresh directory; never log credentials."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seconds', type=int, default=600)
    parser.add_argument('--threads', type=int, default=1)
    parser.add_argument('--queries', type=int, default=1000)
    parser.add_argument('--seed', type=int, default=2026101601)
    parser.add_argument('--oracle', choices=['TLP_WHERE', 'NOREC'], default='TLP_WHERE')
    parser.add_argument('--provider', choices=['yashandb', 'mysql'], default='yashandb')
    parser.add_argument('--max-databases', type=int, default=-1)
    parser.add_argument('--label', default='')
    args = parser.parse_args()
    if args.seconds < 1 or not 1 <= args.threads <= 8 or args.queries < 1:
        parser.error('Invalid duration, thread count or query count')
    if args.provider == 'mysql' and args.oracle != 'TLP_WHERE':
        parser.error('The upstream MySQL provider has no NOREC oracle')
    root = Path(__file__).resolve().parents[1]
    secrets = json.loads((root / 'config/database-secrets.json').read_text())
    stamp = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).strftime('%Y%m%d_%H%M%S_%f')
    run_id = f'{stamp}_{args.provider}_{args.oracle.lower()}'
    run = root / 'results' / run_id
    run.mkdir(parents=True, exist_ok=False)
    jar = root / 'sqlancer/target/sqlancer-2.0.0.jar'
    java = '/usr/lib/jvm/java-11-openjdk-amd64/bin/java'
    command = [java, '-ea', '-Xmx2g', '-jar', str(jar),
               '--host', '127.0.0.1', '--port', '1690', '--username', 'sqlancer',
               '--password', secrets['sqlancer_password'],
               '--database-prefix', 'ys_' + stamp.replace('_', '') + '_',
               '--random-seed', str(args.seed), '--num-threads', str(args.threads),
               '--num-tries', '1', '--num-queries', str(args.queries),
               '--max-generated-databases', str(args.max_databases),
               '--timeout-seconds', str(args.seconds), '--print-progress-summary', 'true',
               '--exit-code-error', '3', args.provider, '--oracle', args.oracle]
    provenance_path = root / 'docs/source-provenance.json'
    provenance = json.loads(provenance_path.read_text()) if provenance_path.exists() else {}
    manifest = {'run_id': run_id, 'arguments': vars(args), 'upstream': provenance,
                'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                'jdbc_version': '8.4.0', 'database_version': '23.4.1.109',
                'database_edition': 'Enterprise evaluation download',
                'compatibility_mode': 'mysql', 'start_time': dt.datetime.now(dt.timezone.utc).isoformat(),
                'working_directory': str(run), 'launcher_pid': os.getpid(),
                'command': [('<redacted>' if x == secrets['sqlancer_password'] else x) for x in command]}
    (run / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    print(str(run), flush=True)
    started = time.monotonic()
    reason = 'process_exit'
    with (run / 'console.log').open('w') as log:
        process = subprocess.Popen(command, cwd=run, stdout=log, stderr=subprocess.STDOUT)
        (run / 'sqlancer.pid').write_text(str(process.pid) + '\n')
        try:
            rc = process.wait(timeout=args.seconds + 30)
        except subprocess.TimeoutExpired:
            reason = 'outer_timeout'
            process.terminate()
            try:
                rc = process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                rc = process.wait()
    summary = {'exit_code': rc, 'termination_reason': reason,
               'elapsed_seconds': round(time.monotonic() - started, 3),
               'end_time': dt.datetime.now(dt.timezone.utc).isoformat()}
    metrics = run / 'oracle-metrics.json'
    if metrics.exists():
        summary['oracle_metrics'] = json.loads(metrics.read_text())
    (run / 'summary.json').write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary), flush=True)
    return 0 if rc == 0 and reason == 'process_exit' else 1


if __name__ == '__main__':
    raise SystemExit(main())
