# The Aldeia Amaldiçoada's own sounds, built here from noise and tones the same way the approved preview builds them live
# (forja/jujutsu-aldeia-ambiente/amb.js, sfx): no recording.
#   aldeia_arroz      6 s loop: the rice rustling (band-passed noise at 600 Hz), the game raises it and its pitch with the gusts
#   aldeia_sapo1-3    the paddy frogs at night: short croaks in a row, each frog its own pitch (the game picks one)
#   aldeia_higurashi  1.6 s: the evening cicada's "ka-na-ka-na", notes falling and fading
#   aldeia_bolha      0.1 s: a gas bubble popping on the water
# Rerunning writes the same files (fixed seed). Run from the repo root; needs numpy and ffmpeg (libvorbis).
import math
import subprocess
import wave

import numpy as np

SR = 44100
OUT = "src/main/resources/assets/battlegroundsmod/sounds/"
rng = np.random.default_rng(1219)


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


def arroz():
    n = SR * 7
    t = np.arange(n) / SR
    x = biquad(biquad(rng.uniform(-1, 1, n), "band", 600, 0.6), "band", 600, 0.6)
    sw = 0.7 + 0.3 * np.sin(t * 2 * math.pi / 7 * 2 + 0.7) ** 2
    return norm(loop_fade(x * sw), 0.7)


def sapo(seed):
    r = np.random.default_rng(seed)
    f = 280 + r.uniform() * 260
    k = 2 + int(r.uniform() * 4)
    n = int(SR * (0.15 + k * 0.13))
    x = np.zeros(n)
    for i in range(k):
        x += tone(n, int(i * 0.13 * SR), f, 0.09, 0.05, "square", f * 0.8)
    return norm(biquad(x, "lp", 2500), 0.6)


def higurashi():
    n = int(SR * 1.6)
    x = np.zeros(n)
    for i in range(9):
        x += tone(n, int(i * 0.16 * SR), 4200 - i * 60, 0.12, 0.022 * (1 - i / 10), "saw", 3900 - i * 60)
    return norm(biquad(x, "lp", 6000), 0.55)


def bolha():
    n = int(SR * 0.1)
    return norm(tone(n, 0, 900, 0.06, 0.03, "sine", 300), 0.6)


write("aldeia_arroz", arroz())
write("aldeia_sapo1", sapo(5))
write("aldeia_sapo2", sapo(17))
write("aldeia_sapo3", sapo(29))
write("aldeia_higurashi", higurashi())
write("aldeia_bolha", bolha())
