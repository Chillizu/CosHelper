#!/usr/bin/env python3
"""Virtual chat peer for CosHelper Hotspot mode.

Uses only Python stdlib plus ctypes to call the system libopus.so.0.
Speaks the CosHelper transport frame:
    10-byte header, little-endian:
        frameType : int16 (always 2)
        sequence  : int16
        timestamp : int32
        dataLen   : int16
    followed by dataLen bytes of Opus payload
"""

import argparse
import ctypes
import math
import socket
import struct
import sys
import time

FRAME_TYPE = 2
FRAME_SIZE = 320          # 20 ms at 16 kHz mono
SAMPLE_RATE = 16000
CHANNELS = 1
OPUS_APPLICATION_AUDIO = 2049


def _load_opus():
    """Try common libopus names."""
    for name in ["libopus.so.0", "libopus.so", "opus.dll", "libopus.dylib"]:
        try:
            return ctypes.CDLL(name)
        except OSError:
            continue
    raise RuntimeError("libopus not found; install the opus system package")


_lib = _load_opus()


class OpusEncoder:
    def __init__(self):
        err = ctypes.c_int32()
        _lib.opus_encoder_create.argtypes = [
            ctypes.c_int32, ctypes.c_int32, ctypes.c_int32, ctypes.POINTER(ctypes.c_int32)
        ]
        _lib.opus_encoder_create.restype = ctypes.c_void_p
        self._enc = _lib.opus_encoder_create(
            SAMPLE_RATE, CHANNELS, OPUS_APPLICATION_AUDIO, ctypes.byref(err)
        )
        if err.value != 0:
            raise RuntimeError(f"opus_encoder_create failed: {err.value}")
        _lib.opus_encode.argtypes = [
            ctypes.c_void_p, ctypes.POINTER(ctypes.c_int16),
            ctypes.c_int, ctypes.c_char_p, ctypes.c_int32
        ]
        _lib.opus_encode.restype = ctypes.c_int32
        try:
            _lib.opus_encoder_destroy.argtypes = [ctypes.c_void_p]
            _lib.opus_encoder_destroy.restype = None
        except AttributeError:
            pass

    def encode(self, samples: bytes) -> bytes:
        if len(samples) // 2 != FRAME_SIZE:
            raise ValueError(f"expected {FRAME_SIZE} samples, got {len(samples) // 2}")
        max_bytes = 4000
        out = ctypes.create_string_buffer(max_bytes)
        pcm_array = (ctypes.c_int16 * FRAME_SIZE).from_buffer_copy(samples)
        ret = _lib.opus_encode(self._enc, pcm_array, FRAME_SIZE, out, max_bytes)
        if ret < 0:
            raise RuntimeError(f"opus_encode failed: {ret}")
        return bytes(out[:ret])

    def __del__(self):
        enc = getattr(self, "_enc", None)
        if enc and _lib and hasattr(_lib, "opus_encoder_destroy"):
            try:
                _lib.opus_encoder_destroy(enc)
            except Exception:
                pass


class OpusDecoder:
    def __init__(self):
        err = ctypes.c_int32()
        _lib.opus_decoder_create.argtypes = [
            ctypes.c_int32, ctypes.c_int32, ctypes.POINTER(ctypes.c_int32)
        ]
        _lib.opus_decoder_create.restype = ctypes.c_void_p
        self._dec = _lib.opus_decoder_create(
            SAMPLE_RATE, CHANNELS, ctypes.byref(err)
        )
        if err.value != 0:
            raise RuntimeError(f"opus_decoder_create failed: {err.value}")
        _lib.opus_decode.argtypes = [
            ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int32,
            ctypes.POINTER(ctypes.c_int16), ctypes.c_int, ctypes.c_int32
        ]
        _lib.opus_decode.restype = ctypes.c_int32
        try:
            _lib.opus_decoder_destroy.argtypes = [ctypes.c_void_p]
            _lib.opus_decoder_destroy.restype = None
        except AttributeError:
            pass

    def decode(self, payload: bytes) -> bytes:
        pcm = (ctypes.c_int16 * FRAME_SIZE)()
        ret = _lib.opus_decode(self._dec, payload, len(payload), pcm, FRAME_SIZE, 0)
        if ret < 0:
            raise RuntimeError(f"opus_decode failed: {ret}")
        return bytes(pcm)

    def __del__(self):
        dec = getattr(self, "_dec", None)
        if dec and _lib and hasattr(_lib, "opus_decoder_destroy"):
            try:
                _lib.opus_decoder_destroy(dec)
            except Exception:
                pass


def build_frame(seq: int, payload: bytes) -> bytes:
    """Build a CosHelper transport frame."""
    timestamp = int(time.time() * 1000) % 0x100000000
    if timestamp >= 0x80000000:
        timestamp -= 0x100000000
    data_len = len(payload)
    if data_len > 0x7FFF:
        raise ValueError("payload too large for signed short")
    header = struct.pack(
        "<hhih",
        FRAME_TYPE,
        seq if seq < 0x8000 else seq - 0x10000,
        timestamp if timestamp < 0x80000000 else timestamp - 0x100000000,
        data_len
    )
    return header + payload


def parse_frame(data: bytes) -> tuple[int, int, int, bytes]:
    """Parse a transport frame. Returns (frame_type, seq, timestamp, payload)."""
    if len(data) < 10:
        raise ValueError("frame too short")
    frame_type, seq, timestamp, data_len = struct.unpack("<hhih", data[:10])
    if len(data) < 10 + data_len:
        raise ValueError("incomplete frame")
    payload = data[10:10 + data_len]
    return frame_type, seq, timestamp, payload


def read_exact(sock: socket.socket, n: int) -> bytes:
    """Read exactly n bytes from socket."""
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionResetError("socket closed")
        buf += chunk
    return buf


def generate_sine_samples() -> bytes:
    """Generate a 1 kHz sine wave for one 16 kHz frame."""
    pcm = (ctypes.c_int16 * FRAME_SIZE)()
    for i in range(FRAME_SIZE):
        sample = int(32767 * math.sin(2.0 * math.pi * 1000.0 * i / SAMPLE_RATE))
        pcm[i] = sample
    return bytes(pcm)


def run_server(port: int, mode: str) -> None:
    """Run as TCP server on 0.0.0.0:port."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(("0.0.0.0", port))
        s.listen(1)
        print(f"[server] listening on 0.0.0.0:{port}")
        conn, addr = s.accept()
        print(f"[server] connection from {addr}")
        with conn:
            received = 0
            decoder = OpusDecoder() if mode == "echo" else None
            try:
                while True:
                    header = read_exact(conn, 10)
                    frame_type, seq, timestamp, data_len = struct.unpack("<hhih", header)
                    payload = read_exact(conn, data_len)
                    received += 1
                    if received <= 5 or received % 10 == 0:
                        print(f"[server] received frame {received} seq={seq} len={len(payload)}")
                    if mode == "echo":
                        # Echo the exact frame back (header + payload)
                        conn.sendall(header + payload)
                    elif mode == "canned":
                        # Server side canned: just count and reply with a canned frame
                        # using the same sequence number as the received frame.
                        canned = get_canned_payload()
                        conn.sendall(build_frame(seq, canned))
            except ConnectionResetError:
                print(f"[server] connection closed, total frames received: {received}")


def get_canned_payload() -> bytes:
    """Lazy-initialized encoded Opus payload."""
    if not hasattr(get_canned_payload, "payload"):
        encoder = OpusEncoder()
        get_canned_payload.payload = encoder.encode(generate_sine_samples())
        get_canned_payload.encoder = encoder  # keep alive
    return get_canned_payload.payload


def run_client(host: str, port: int, mode: str) -> None:
    """Connect to a server and send/receive frames."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        print(f"[client] connecting to {host}:{port}")
        s.connect((host, port))
        print(f"[client] connected to {host}:{port}")

        seq = 0
        sent = 0
        received = 0

        def sender():
            nonlocal seq, sent
            if mode == "canned":
                payload = get_canned_payload()
            else:
                payload = get_canned_payload()
            while True:
                frame = build_frame(seq, payload)
                seq = (seq + 1) % 0x10000
                s.sendall(frame)
                sent += 1
                if sent <= 5 or sent % 50 == 0:
                    print(f"[client] sent {sent} frames, received {received}")
                time.sleep(0.02)

        def receiver():
            nonlocal received
            while True:
                header = read_exact(s, 10)
                _, _, _, data_len = struct.unpack("<hhih", header)
                payload = read_exact(s, data_len)
                received += 1
                if received <= 5 or received % 50 == 0:
                    print(f"[client] received {received} frames")
                if mode == "echo":
                    # Send the received payload back (with a new sequence number)
                    s.sendall(build_frame(seq, payload))

        import threading
        threading.Thread(target=receiver, daemon=True).start()
        sender()


def main():
    parser = argparse.ArgumentParser(description="CosHelper virtual chat peer")
    parser.add_argument("--mode", choices=["server", "client", "echo", "canned"], default="echo",
                        help="server or client mode; echo/canned are client sub-behaviours")
    parser.add_argument("--host", default="127.0.0.1", help="server host for client mode")
    parser.add_argument("--port", type=int, default=19999, help="TCP port")
    args = parser.parse_args()

    if args.mode == "server":
        run_server(args.port, "echo")
    elif args.mode == "echo":
        run_client(args.host, args.port, "echo")
    elif args.mode == "canned":
        run_client(args.host, args.port, "canned")
    elif args.mode == "client":
        # Default client behaviour: echo-style, just connect and send a canned frame
        run_client(args.host, args.port, "canned")
    else:
        parser.print_help()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[peer] stopped")
        sys.exit(0)
