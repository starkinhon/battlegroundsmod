# The Outono de Kyoto's own sounds, built here from noise and tones the same way the approved preview builds them live
# (forja/jujutsu-kyoto-ambiente/amb.js, sfx): no recording.
#   kyoto_folhas     6 s loop: leaves rustling in the wind (band-passed noise), the game raises it with the gusts
#   kyoto_sino       9 s: a far temple bell, detuned partials beating against each other, a long tail
#   kyoto_corvo      1.3 s: a crow's hoarse "kaa" three times (a falling sawtooth through a formant)
#   kyoto_suzumushi  0.75 s: one "riiin" of the bell cricket (a high tone trembling at 38 Hz), the game repeats it
#   kyoto_bambu      0.15 s: two bamboo stems knocking in a gust
#   kyoto_shishi     0.9 s: the shishi-odoshi's hollow "kon" on the stone, with the garden's echo
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
    x = biquad(rng.uniform(-1, 1, n), "band", 900, 0.6)
    # leaves don't hiss evenly: small swells and quick flutters
    sw = 0.7 + 0.3 * np.sin(t * 2 * math.pi / 7 * 2) ** 2
    fl = 0.8 + 0.2 * biquad(rng.uniform(-1, 1, n), "lp", 9) * 6
    return norm(loop_fade(x * sw * np.clip(fl, 0.3, 1.4)), 0.7)


def sino():
    n = SR * 9
    x = np.zeros(n)
    for f, g, d in [(73, 0.05, 9), (74.2, 0.04, 9), (146, 0.025, 6), (201, 0.02, 5), (242, 0.012, 4), (311, 0.008, 3)]:
        x += tone(n, 0, f, min(d, 8.99), g)
    # the strike: a short dull knock of the log on the bronze
    L = int(0.05 * SR)
    x[:L] += biquad(rng.uniform(-1, 1, L), "lp", 400) * np.exp(-np.arange(L) / SR / 0.012) * 0.03
    return norm(x, 0.8)


def corvo():
    n = int(SR * 1.3)
    x = np.zeros(n)
    for i in range(3):
        x += tone(n, int(i * 0.42 * SR), 560, 0.28, 0.05, "saw", 420)
    return norm(biquad(x, "band", 1300, 3), 0.8)


def suzumushi():
    n = int(SR * 0.75)
    t = np.arange(n) / SR
    o = np.sin(2 * math.pi * 4400 * t)
    am = 1 + 0.5 * np.sin(2 * math.pi * 38 * t)
    e = np.interp(t, [0, 0.05, 0.55, 0.7, 0.75], [0, 1, 1, 0, 0])
    return norm(o * am * e, 0.6)


def bambu():
    n = int(SR * 0.15)
    x = np.zeros(n)
    for i0, f in [(0, 620), (int(0.045 * SR), 840)]:
        L = int(0.06 * SR)
        b = biquad(rng.uniform(-1, 1, L), "band", f, 8) * np.exp(-np.arange(L) / SR / 0.018)
        x[i0:i0 + L] += b * (1 if i0 == 0 else 0.6)
    return norm(x, 0.8)


def shishi():
    n = int(SR * 0.9)
    x = np.zeros(n)
    for dt, g in [(0, 1), (0.22, 0.25), (0.45, 0.08)]:
        i0 = int(dt * SR)
        x += tone(n, i0, 430, 0.18, g, "sine", 390)
        L = int(0.05 * SR)
        x[i0:i0 + L] += biquad(rng.uniform(-1, 1, L), "band", 1100, 6) * g * 0.8 * 4
    return norm(x, 0.85)


write("kyoto_folhas", folhas())
write("kyoto_sino", sino())
write("kyoto_corvo", corvo())
write("kyoto_suzumushi", suzumushi())
write("kyoto_bambu", bambu())
write("kyoto_shishi", shishi())
