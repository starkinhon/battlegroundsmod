# The Floresta Negra's own sounds, built from noise and tones the way the approved preview builds them live
# (forja/jujutsu-floresta-ambiente/amb.js, sfx): no recording.
#   floresta_drone        8 s loop: the forest's low breath (brown noise under 90 Hz)
#   floresta_rangido1-3   a far trunk creaking (a narrow noise band sliding), the game picks one now and then
#   floresta_gota1-3      a drop falling on the black water
#   floresta_sopro        the Árvore Matriz exhaling its miasma
# Rerunning writes the same files (fixed seed). Run from the repo root; needs numpy and ffmpeg (libvorbis).
import math
import subprocess
import wave

import numpy as np

SR = 44100
OUT = "src/main/resources/assets/battlegroundsmod/sounds/"
rng = np.random.default_rng(2026)


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


def sbiquad(x, kind, f, q):
    # a biquad whose frequency moves (f: one value per sample), the preview's exponentialRampToValueAtTime
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i in range(len(x)):
        w = 2 * math.pi * f[i] / SR
        a = math.sin(w) / (2 * q)
        c = math.cos(w)
        if kind == "lp":
            b0, b1, b2 = (1 - c) / 2, 1 - c, (1 - c) / 2
        else:
            b0, b1, b2 = a, 0, -a
        a0 = 1 + a
        v = (b0 * x[i] + b1 * x1 + b2 * x2 - (-2 * c) * y1 - (1 - a) * y2) / a0
        x2, x1, y2, y1 = x1, x[i], y1, v
        y[i] = v
    return y


def brown(n):
    w = rng.uniform(-1, 1, n)
    out = np.zeros(n)
    last = 0.0
    for i in range(n):
        last = (last + 0.02 * w[i]) / 1.02
        out[i] = last * 3.5
    return out


def burst(n, i0, dur, kind, f0, gain, q, brown_noise=False, f1=None):
    # the preview's burst(): noise through a filter (sliding from f0 to f1), up in min(dur * 0.3, 0.6), down to silence
    L = min(int(dur * SR), n - i0)
    t = np.arange(L) / SR
    src = brown(L) if brown_noise else rng.uniform(-1, 1, L)
    f = f0 * (f1 / f0) ** (t / dur) if f1 else np.full(L, f0)
    y = sbiquad(src, kind, f, q if q else 0.707)
    att = min(dur * 0.3, 0.6)
    e = np.where(t < att, gain * t / att, gain * (0.0001 / gain) ** ((t - att) / (dur - att)))
    out = np.zeros(n)
    out[i0:i0 + L] = y * e
    return out


def drone():
    # the forest's low breath: brown noise under 90 Hz, a slow swell in it
    n = SR * 8
    x = biquad(brown(n), "lp", 90)
    t = np.arange(n) / SR
    x *= 0.8 + 0.2 * np.sin(t * 2 * math.pi / 8 * 2) ** 2
    write("floresta_drone", norm(loop_fade(x, 1.5), 0.5))


def rangido(k):
    # a far trunk creaking: a narrow band of noise sliding up or down
    n = int(SR * 1.8)
    f0 = 180 + rng.uniform() * 260
    dur = 0.6 + rng.uniform() * 0.9
    x = burst(n, 0, dur, "band", f0, 0.05, 18, False, f0 * (0.7 + rng.uniform() * 0.6))
    write("floresta_rangido%d" % k, norm(x[:int(SR * (dur + 0.1))], 0.7))


def gota(k):
    # a drop on the black water: a short rising blip
    n = int(SR * 0.14)
    f0 = 900 + rng.uniform() * 600
    x = tone(n, 0, f0, 0.09, 0.06, "sine", 1800 + rng.uniform() * 600)
    write("floresta_gota%d" % k, norm(x, 0.6))


def sopro():
    # the Árvore Matriz exhaling: a deep brown roar falling, a hiss under it
    n = int(SR * 5)
    x = burst(n, 0, 4.5, "lp", 140, 0.4, 0, True, 60) + burst(n, int(0.3 * SR), 3.5, "band", 420, 0.05, 1.2, False, 200)
    write("floresta_sopro", norm(x, 0.9))


drone()
for k in (1, 2, 3):
    rangido(k)
for k in (1, 2, 3):
    gota(k)
sopro()
