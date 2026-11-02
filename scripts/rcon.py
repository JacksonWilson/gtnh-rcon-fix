"""Send console commands to a Minecraft server over RCON and print the replies.

Reads the address, port and password from a server.properties file, so the password never goes on the command line.
    python rcon.py --properties run/server/server.properties "list" "tp Alex 0 80 0"
Exit code 1 if the login fails or the server closes the connection.
Needs RCON Fix on a 1.7.10 server: vanilla 1.7.10 closes the connection right after login.
"""
import argparse
import pathlib
import socket
import struct
import sys


def read_properties(path):
    out = {}
    for line in pathlib.Path(path).read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            out[key.strip()] = value.strip()
    return out


def packet(req_id, kind, body):
    payload = struct.pack("<ii", req_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def read_exact(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError("server closed the RCON connection")
        data += chunk
    return data


def read_packet(sock):
    (length,) = struct.unpack("<i", read_exact(sock, 4))
    data = read_exact(sock, length)
    req_id, kind = struct.unpack("<ii", data[:8])
    return req_id, kind, data[8:-2].decode("utf-8", "replace")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--properties", default="run/server/server.properties", help="server.properties to read")
    parser.add_argument("--host", help="override the address (default: server-ip, or 127.0.0.1)")
    parser.add_argument("commands", nargs="+")
    args = parser.parse_args()

    props = read_properties(args.properties)
    host = args.host or props.get("server-ip") or "127.0.0.1"
    port = int(props.get("rcon.port") or 0) or int(props.get("server-port", "25565")) + 10
    with socket.create_connection((host, port), timeout=60) as sock:
        sock.sendall(packet(1, 3, props.get("rcon.password", "")))
        if read_packet(sock)[0] == -1:
            sys.exit("RCON login failed")
        for i, command in enumerate(args.commands, start=2):
            sock.sendall(packet(i, 2, command))
            _, _, body = read_packet(sock)
            print(f"> {command}")
            if body:
                print(body.rstrip())


if __name__ == "__main__":
    try:
        main()
    except (ConnectionError, OSError) as e:
        sys.exit(f"RCON: {e}")
