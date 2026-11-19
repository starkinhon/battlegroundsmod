# The Floresta de Hanami's own sounds, built here from noise and tones the same way the approved preview builds them live
# (forja/jujutsu-hanami-ambiente/amb.js, sfx): no recording.
#   hanami_folhas     6 s loop: the canopy rustling (high-passed noise at 1800 Hz), the game raises it with the gusts
#   hanami_passaro1-3 short whistled phrases that rise or fall, each bird its own way (the game picks one)
#   hanami_grilo      6 s loop: the crickets at night, three quick 4600 Hz chirps every 0.35 to 0.75 s
#   hanami_coracao    0.8 s: the Hanami's Heart, "tum" (55 to 40 Hz) and a weaker "tum" (50 to 38 Hz) 0.35 s later,
#                     with a soft octave on top so it is heard on small speakers too
# Rerunning writes the same files (fixed seed). Run from the repo root; needs numpy and ffmpeg (libvorbis).
import math
import subprocess
import wave

import numpy as np

SR = 44100
OUT = "src/main/resources/assets/battlegroundsmod/sounds/"
rng = np.random.default_rng(1119)


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


def folhas():
    n = SR * 7
    t = np.arange(n) / SR
    x = biquad(biquad(rng.uniform(-1, 1, n), "hp", 1800), "hp", 1800)
    sw = 0.7 + 0.3 * np.sin(t * 2 * math.pi / 7 * 3 + 1.1) ** 2
    return norm(loop_fade(x * sw), 0.6)


def passaro(seed):
    r = np.random.default_rng(seed)
    f = 2200 + r.uniform() * 1800
    k = 2 + int(r.uniform() * 5)
    up = r.uniform() < 0.5
    n = int(SR * (0.15 + k * 0.11))
    x = np.zeros(n)
    for i in range(k):
        x += tone(n, int(i * 0.11 * SR), f * (1 + i * 0.04 * (1 if up else -1)), 0.08, 0.03, "sine", f * (1.25 if up else 0.8))
    return norm(x, 0.6)


def grilo():
    n = SR * 6
    x = np.zeros(n)
    t0 = 0.05
    while t0 < 5.8:
        for i in range(3):
            x += tone(n, int((t0 + i * 0.05) * SR), 4600, 0.035, 0.012)
        t0 += 0.35 + rng.uniform() * 0.4
    return norm(x, 0.5)


def coracao():
    n = int(SR * 0.8)
    x = tone(n, 0, 55, 0.25, 1, "sine", 40) + tone(n, int(0.35 * SR), 50, 0.22, 0.7, "sine", 38)
    x += 0.25 * (tone(n, 0, 110, 0.2, 1, "sine", 80) + tone(n, int(0.35 * SR), 100, 0.18, 0.7, "sine", 76))
    return norm(x, 0.9)


write("hanami_folhas", folhas())
write("hanami_passaro1", passaro(11))
write("hanami_passaro2", passaro(23))
write("hanami_passaro3", passaro(37))
write("hanami_grilo", grilo())
write("hanami_coracao", coracao())
