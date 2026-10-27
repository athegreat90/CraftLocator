#!/usr/bin/env python3
"""Read-only readiness check for the local development Minecraft server."""
import json
import socket
import struct
import sys
from pathlib import Path


def varint(value):
    result = bytearray()
    while True:
        byte = value & 127
        value >>= 7
        result.append(byte | (128 if value else 0))
        if not value:
            return bytes(result)


def read_exact(connection, size):
    result = bytearray()
    while len(result) < size:
        data = connection.recv(size - len(result))
        if not data:
            raise ConnectionError('Minecraft closed the status connection')
        result.extend(data)
    return bytes(result)


def read_varint(connection):
    result = 0
    for index in range(5):
        byte = read_exact(connection, 1)[0]
        result |= (byte & 127) << (7 * index)
        if byte < 128:
            return result
    raise ValueError('Invalid Minecraft VarInt')


def main():
    properties = dict(line.split('=', 1) for line in (Path(__file__).resolve().parents[1] / 'gradle.properties').read_text().splitlines() if '=' in line)
    expected_version = sys.argv[1] if len(sys.argv) > 1 else properties['minecraft_version']
    with socket.create_connection(('127.0.0.1', 25565), timeout=5) as connection:
        host = b'127.0.0.1'
        packet = varint(0) + varint(777) + varint(len(host)) + host + struct.pack('>H', 25565) + varint(1)
        connection.sendall(varint(len(packet)) + packet + b'\x01\x00')
        length = read_varint(connection)
        if not 1 <= length <= 65536 or read_varint(connection) != 0:
            raise ValueError('Invalid Minecraft status packet')
        size = read_varint(connection)
        if not 1 <= size <= length:
            raise ValueError('Invalid Minecraft status JSON length')
        status = json.loads(read_exact(connection, size))
        if status['version']['name'] != expected_version:
            raise ValueError('Expected Minecraft ' + expected_version)
        print(f"Minecraft {expected_version} ready; online players: {status['players']['online']}")


if __name__ == '__main__':
    main()
