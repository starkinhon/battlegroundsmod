# The Colônia do Jogo do Abate's own sounds (Stark 2026-11-14: "pode montar som, é até melhor"), built here from noise
# the same way the approved preview builds them live (forja/jujutsu-colonia-ambiente/amb.js, sfx): no recording.
#   colonia_vento        10 s loop: deep brown-noise wind + a whistle sweeping through the ruins' cracks
#   colonia_fogo          8 s loop: the low roar of a burning building + its crackles
#   colonia_desabamento   3 s: a far collapse, a deep rumble and rubble falling
#   colonia_estrondo      4 s: the far cursed-energy blast, a boom with a falling sub tone
# Rerunning writes the same files (fixed seed). Run from the repo root; needs numpy and ffmpeg (libvorbis).
import math
import subprocess
import wave

import numpy as np

SR = 44100
OUT = "src/main/resources/assets/battlegroundsmod/sounds/"
rng = np.random.default_rng(1414)


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


def sweep_band(x, freqs, q):
    # a band-pass whose center follows freqs (one per sample), coefficients updated every 64 samples
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for s in range(0, len(x), 64):
        w = 2 * math.pi * freqs[s] / SR
        a = math.sin(w) / (2 * q)
        c = math.cos(w)
        a0 = 1 + a
        b0, b2, a1, a2 = a / a0, -a / a0, -2 * c / a0, (1 - a) / a0
        for i in range(s, min(s + 64, len(x))):
            v = b0 * x[i] + b2 * x2 - a1 * y1 - a2 * y2
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


def loop_fade(x, sec=1.0):
    # make the end flow into the start: crossfade the last sec over the first
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


def env(n, attack, decay):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(attack, 1e-4)) * np.exp(-t / decay)


def vento():
    n = SR * 11
    t = np.arange(n) / SR
    low = biquad(brown(n), "lp", 500)
    swell = 0.6 + 0.4 * np.sin(t * 2 * math.pi / 11 * 2 + 0.7) ** 2
    freqs = 380 + 300 * (0.5 + 0.5 * np.sin(t * 2 * math.pi / 11 * 3)) + 70 * np.sin(t * 4.4)
    howl = sweep_band(rng.uniform(-1, 1, n), freqs, 9)
    x = norm(low, 0.7) * swell + norm(howl, 0.22) * swell ** 2
    return norm(loop_fade(x), 0.8)


def fogo():
    n = SR * 9
    roar = norm(biquad(brown(n), "lp", 180), 0.6)
    flick = 0.75 + 0.25 * biquad(rng.uniform(-1, 1, n), "lp", 3)
    x = roar * norm(flick, 1)
    # crackles: short bright noise bursts, denser in clumps
    t = 0.0
    while t < 9 - 0.05:
        L = int(SR * (0.004 + rng.uniform() * 0.018))
        i = int(t * SR)
        burst = biquad(rng.uniform(-1, 1, L), "hp", 1800 + rng.uniform() * 2500) * env(L, 0.0008, L / SR / 3)
        x[i:i + L] += burst * (0.25 + rng.uniform() * 0.55)
        t += 0.02 + rng.exponential(0.09)
    return norm(loop_fade(x), 0.8)


def desabamento():
    n = SR * 3
    x = norm(biquad(brown(n), "lp", 110), 0.8) * env(n, 0.05, 0.9)
    for _ in range(14):
        L = int(SR * (0.04 + rng.uniform() * 0.1))
        i = int(SR * (0.25 + rng.uniform() * 1.9))
        if i + L >= n:
            continue
        x[i:i + L] += biquad(rng.uniform(-1, 1, L), "band", 500 + rng.uniform() * 900, 2) * env(L, 0.002, L / SR / 3) * 0.9
    return norm(x, 0.85)


def estrondo():
    n = SR * 4
    t = np.arange(n) / SR
    boom = norm(biquad(brown(n), "lp", 75), 1) * env(n, 0.01, 1.1)
    crack = norm(biquad(rng.uniform(-1, 1, n), "lp", 260), 1) * env(n, 0.005, 0.35)
    f = 48 * (26 / 48) ** np.minimum(1, t / 2.5)
    sub = np.sin(2 * math.pi * np.cumsum(f) / SR) * env(n, 0.005, 0.9)
    return norm(boom * 0.9 + crack * 0.35 + sub * 0.5, 0.95)


if __name__ == "__main__":
    write("colonia_vento", vento())
    write("colonia_fogo", fogo())
    write("colonia_desabamento", desabamento())
    write("colonia_estrondo", estrondo())
