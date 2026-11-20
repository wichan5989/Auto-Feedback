"""Read-only OSC probe for Midas M32; never writes mixer settings."""
import argparse
import json
import socket
import struct
import time


def osc_string(value):
    raw = value.encode("utf-8") + b"\x00"
    return raw + b"\x00" * ((-len(raw)) % 4)


def osc_message(address, *args):
    tags = "," + "".join("s" if isinstance(v, str) else "i" for v in args)
    data = osc_string(address) + osc_string(tags)
    for value in args:
        data += osc_string(value) if isinstance(value, str) else struct.pack(">i", value)
    return data


def read_string(data, pos):
    end = data.index(b"\x00", pos)
    return data[pos:end].decode("utf-8", "replace"), (end + 4) & ~3


def decode_osc(data):
    address, pos = read_string(data, 0)
    tags, pos = read_string(data, pos)
    if not tags.startswith(","):
        raise ValueError("Invalid OSC type tags")
    values = []
    for tag in tags[1:]:
        if tag == "s":
            value, pos = read_string(data, pos)
        elif tag == "i":
            value = struct.unpack_from(">i", data, pos)[0]
            pos += 4
        elif tag == "f":
            value = struct.unpack_from(">f", data, pos)[0]
            pos += 4
        elif tag == "b":
            size = struct.unpack_from(">I", data, pos)[0]
            pos += 4
            value = data[pos:pos + size]
            pos += (size + 3) & ~3
        else:
            raise ValueError("Unsupported OSC tag " + tag)
        values.append(value)
    return address, values


def probe(ip, seconds=20):
    counts = {}
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.bind(("0.0.0.0", 0))
        sock.settimeout(0.5)
        started = time.monotonic()
        next_request = started
        while time.monotonic() - started < seconds:
            if time.monotonic() >= next_request:
                for address in ("/xinfo", "/-prefs/rta/source"):
                    sock.sendto(osc_message(address), (ip, 10023))
                # Diagnostic candidate streams; actual RTA content is unverified.
                for stream in ("/meters/15", "/meters/16"):
                    sock.sendto(osc_message("/meters", stream), (ip, 10023))
                next_request = time.monotonic() + 5
            try:
                payload, peer = sock.recvfrom(65535)
                if peer[0] != ip:
                    continue
                address, values = decode_osc(payload)
                counts[address] = counts.get(address, 0) + 1
                summary = [
                    {"blob_bytes": len(v), "hex_prefix": v[:16].hex()} if isinstance(v, bytes) else v
                    for v in values
                ]
                print(json.dumps({"address": address, "args": summary}, ensure_ascii=False), flush=True)
            except socket.timeout:
                pass
            except (ValueError, IndexError, struct.error) as exc:
                print(json.dumps({"decode_error": str(exc)}), flush=True)
    print(json.dumps({"packet_counts": counts}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--ip", default="192.168.0.100")
    parser.add_argument("--seconds", type=int, default=20)
    args = parser.parse_args()
    probe(args.ip, args.seconds)
