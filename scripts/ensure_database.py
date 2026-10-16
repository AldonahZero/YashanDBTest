#!/usr/bin/env python3
import socket,subprocess
try:
    with socket.create_connection(('127.0.0.1',1690),timeout=3):pass
except OSError:
    subprocess.run(['/usr/bin/python3','/root/YashanDBTest/scripts/db_admin.py','start'],check=True)
