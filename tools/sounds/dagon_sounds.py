# The Mar de Dagon's own sounds, built here from noise the same way the approved preview builds them live
# (forja/jujutsu-dagon-ambiente/amb.js, sfx): no recording.
#   dagon_vento    6 s loop: the sea wind (band-passed noise at 500 Hz)
#   dagon_abismo   6 s loop: the abyss's deep rumble (brown noise low-passed at 70 Hz, a soft octave above so small
#                  speakers carry it), the game raises it near the Nest
#   dagon_onda     3.6 s: a wave breaking: the low crash (900 to 300 Hz) and the high hiss of the foam (2500 to 5000 Hz)
# Rerunning writes the same files (fixed seed). Run from the repo root; needs numpy and ffmpeg (libvorbis).
import math
import subprocess
import wave

import numpy as np

SR = 44100
OUT = "src/main/resources/assets/battlegroundsmod/sounds/"
rng = np.random.default_rng(1319)


def biquad(x, kind, f, q=0.707):
    w = 2 * math.pi * f / SR
    a = math.sin(w) / (2 * q)
    c = math.cos(w)
    if kind == "lp":
        b0, b1, b2 = (1 - c) / 2, 1 - c, (1 - c) / 2
    elif kind == "hp":
        b0, b1, b2 = (1 + c) / 2, -(1 + c), (1 + c) / 2
    else:  # band
        b0, b1, b2 = a, 0, -a
    a0, a1, a2 = 1 + a, -2 * c, 1 - a
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i in range(len(x)):
        v = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, x[i], y1, v
        y[i] = v
    return y


def loop_fade(x, sec=1.0):
    k = int(sec * SR)
    head, body, tail = x[:k], x[k:-k], x[-k:]
    t = np.linspace(0, 1, k)
    return np.concatenate([tail * (1 - t) + head * t, body])


def norm(x, peak):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def write(name, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    tmp = "/tmp/" + name + ".wav"
    with wave.open(tmp, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "4", OUT + name + ".ogg"], check=True)


def tone(n, i0, freq, dur, gain, kind="sine", f2=None):
    # the preview's tone(): exponential attack to gain in min(0.02, dur * 0.15), exponential decay to silence at dur
    out = np.zeros(n)
    L = min(int(dur * SR), n - i0)
    t = np.arange(L) / SR
    f = freq * (f2 / freq) ** (t / dur) if f2 else np.full(L, freq)
    ph = 2 * math.pi * np.cumsum(f) / SR
    w = np.sin(ph) if kind == "sine" else 2 * ((ph / (2 * math.pi)) % 1) - 1
    att = min(0.02, dur * 0.15)
    e = np.where(t < att, 0.0001 * (gain / 0.0001) ** np.minimum(t / att, 1), gain * (0.0001 / gain) ** np.maximum((t - att) / (dur - att), 0))
    out[i0:i0 + L] = w * e
    return out


def sweep_filter(x, kind, f0, f1, q=0.707, chunk=2048):
    # a filter whose cutoff glides exponentially from f0 to f1 (done in short blocks)
    out = np.zeros_like(x)
    n = len(x)
    for i in range(0, n, chunk):
        f = f0 * (f1 / f0) ** (i / n)
        seg = x[max(0, i - 512):i + chunk]
        y = biquad(seg, kind, f, q)
        out[i:i + chunk] = y[len(seg) - len(x[i:i + chunk]):]
    return out


def env(n, att, gain):
    t = np.arange(n) / SR
    dur = n / SR
    return np.where(t < att, 0.0001 * (gain / 0.0001) ** (t / att), gain * (0.0001 / gain) ** ((t - att) / (dur - att)))


def vento():
    n = SR * 7
    t = np.arange(n) / SR
    x = biquad(biquad(rng.uniform(-1, 1, n), "band", 500, 0.6), "band", 500, 0.6)
    sw = 0.75 + 0.25 * np.sin(t * 2 * math.pi / 7 * 2 + 0.3) ** 2
    return norm(loop_fade(x * sw), 0.6)


def abismo():
    n = SR * 7
    w = rng.uniform(-1, 1, n)
    b = np.zeros(n)
    last = 0.0
    for i in range(n):
        last = (last + 0.02 * w[i]) / 1.02
        b[i] = last * 3.5
    x = biquad(biquad(b, "lp", 70), "lp", 70) + 0.25 * biquad(b, "band", 140, 1.2)
    return norm(loop_fade(x), 0.9)


def onda():
    n = int(SR * 3.6)
    lo = sweep_filter(rng.uniform(-1, 1, int(SR * 2.8)), "lp", 900, 300) * env(int(SR * 2.8), 0.25, 0.4)
    hi = sweep_filter(rng.uniform(-1, 1, n), "hp", 2500, 5000) * env(n, 0.6, 0.09)
    x = hi.copy()
    x[:len(lo)] += lo
    return norm(x, 0.85)


write("dagon_vento", vento())
write("dagon_abismo", abismo())
write("dagon_onda", onda())
