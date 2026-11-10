"""Synthesised UI sounds (docs/08 §6): quiet, soft, marimba-like — no arcade bells, no buzzers.

Usage: python tools/make_sfx.py <work_dir> <res_raw_dir>    (needs ffmpeg on PATH for the OGG encoding)
Generated from scratch, so there is no third-party licence (see res/raw/LICENSES.md).
"""
import math
import random
import struct
import subprocess
import sys
import wave
from pathlib import Path

RATE = 44100


def marimba(freq: float, dur: float, amp: float = 1.0) -> list[float]:
    """Wooden-bar tone: fundamental + fast-decaying 3.9x partial, 4 ms attack."""
    out = []
    for i in range(int(dur * RATE)):
        t = i / RATE
        attack = min(1.0, t / 0.004)
        body = math.exp(-t / 0.28) * math.sin(2 * math.pi * freq * t)
        bright = 0.22 * math.exp(-t / 0.05) * math.sin(2 * math.pi * freq * 3.9 * t)
        out.append(amp * attack * (body + bright))
    return out


def mix(base: list[float], add: list[float], at: float) -> list[float]:
    start = int(at * RATE)
    need = start + len(add)
    if len(base) < need:
        base = base + [0.0] * (need - len(base))
    for i, v in enumerate(add):
        base[start + i] += v
    return base


def tap() -> list[float]:
    # very short soft "tuk": low sine with a quick pitch drop
    out = []
    for i in range(int(0.055 * RATE)):
        t = i / RATE
        f = 420 - 1800 * t
        out.append(math.exp(-t / 0.012) * math.sin(2 * math.pi * f * t) * min(1, t / 0.002))
    return out


def flip() -> list[float]:
    # paper rustle: smoothed noise with a soft swell
    rnd = random.Random(7)
    out, low = [], 0.0
    n = int(0.16 * RATE)
    for i in range(n):
        t = i / n
        low += 0.18 * (rnd.uniform(-1, 1) - low)
        out.append(low * math.sin(math.pi * t) ** 1.5 * 1.6)
    return out


def match() -> list[float]:
    return mix(marimba(784, 0.5), marimba(1047, 0.6, 0.85), 0.11)


def soft() -> list[float]:
    # neutral "bloop": gentle downward sine, no harshness
    out, phase = [], 0.0
    n = int(0.2 * RATE)
    for i in range(n):
        t = i / RATE
        f = 330 - 380 * t
        phase += 2 * math.pi * f / RATE
        out.append(math.sin(phase) * math.sin(math.pi * i / n) * 0.8)
    return out


def snap() -> list[float]:
    out = []
    for i in range(int(0.05 * RATE)):
        t = i / RATE
        out.append(math.exp(-t / 0.006) * (math.sin(2 * math.pi * 1300 * t) + 0.5 * math.sin(2 * math.pi * 650 * t)))
    return out


def star() -> list[float]:
    # played with a rising SoundPool rate for each star
    return marimba(880, 0.45)


def win() -> list[float]:
    # short calm melody (C E G C), under 2 s, no fanfare
    out: list[float] = []
    for k, (f, at) in enumerate([(523, 0.0), (659, 0.16), (784, 0.32), (1047, 0.52)]):
        out = mix(out, marimba(f, 0.9 if k == 3 else 0.5, 0.9), at)
    return out


def pop() -> list[float]:
    # soap bubble (docs/12 M15a): tiny upward "plop" with a breath of noise
    rnd = random.Random(11)
    out, phase = [], 0.0
    n = int(0.09 * RATE)
    for i in range(n):
        t = i / RATE
        f = 520 + 2600 * t
        phase += 2 * math.pi * f / RATE
        env = math.exp(-t / 0.018) * min(1, t / 0.001)
        out.append(env * (math.sin(phase) + 0.25 * rnd.uniform(-1, 1)))
    return out


# Xylophone bars (docs/12 M15b, ADR-024): C D E F G A C' D' – no B, so random taps never clash.
NOTES = {"c": 523.25, "d": 587.33, "e": 659.25, "f": 698.46, "g": 783.99, "a": 880.0, "c2": 1046.5, "d2": 1174.66}


def note(freq: float):
    return lambda: marimba(freq, 1.1)


SOUNDS = {"sfx_tap": tap, "sfx_flip": flip, "sfx_match": match, "sfx_soft": soft, "sfx_snap": snap, "sfx_star": star, "sfx_win": win, "sfx_pop": pop}
SOUNDS.update({f"note_{name}": note(freq) for name, freq in NOTES.items()})


def write_wav(path: Path, samples: list[float], peak: float = 0.5) -> None:
    top = max(abs(s) for s in samples) or 1.0
    fade = int(0.01 * RATE)
    data = bytearray()
    for i, s in enumerate(samples):
        gain = min(1.0, (len(samples) - i) / fade)  # 10 ms fade-out, no clicks
        data += struct.pack("<h", int(max(-1, min(1, s / top * peak * gain)) * 32767))
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(bytes(data))


def main() -> None:
    work, raw = Path(sys.argv[1]), Path(sys.argv[2])
    work.mkdir(parents=True, exist_ok=True)
    raw.mkdir(parents=True, exist_ok=True)
    for name, make in SOUNDS.items():
        wav = work / f"{name}.wav"
        write_wav(wav, make(), peak=0.35 if name in ("sfx_tap", "sfx_flip", "sfx_snap") else 0.5)
        subprocess.run(
            ["ffmpeg", "-nostdin", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4", str(raw / f"{name}.ogg")],
            check=True,
        )
        print(name)


if __name__ == "__main__":
    main()
