# The Terras do Jogo's own sounds, built here from noise the same way the approved preview builds them live
# (forja/jujutsu-jogo-ambiente/amb.js, sfx): no recording.
#   jogo_ronco      6 s loop: the volcano's rumble (brown noise low-passed at 55 Hz, a soft octave above for small speakers)
#   jogo_chiado     6 s loop: a fumarole's hiss (high-passed noise at 2600 Hz)
#   jogo_borbulha   6 s loop: lava bubbling (brown noise band-passed at 180 Hz)
#   jogo_plop       0.2 s: a lava bubble popping
#   jogo_baque      0.4 s: a volcanic bomb landing
#   jogo_explosao   4 s: the strombolian blast: the deep boom, the crack and rocks falling after it
#   jogo_trovao     3.2 s: the volcanic lightning's thunder: the crack and the long roll
# Rerunning writes the same files (fixed seed). Run from the repo root; needs numpy and ffmpeg (libvorbis).
import math
import subprocess
import wave

import numpy as np

SR = 44100
OUT = "src/main/resources/assets/battlegroundsmod/sounds/"
rng = np.random.default_rng(1419)


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


def brown(n):
    w = rng.uniform(-1, 1, n)
    b = np.zeros(n)
    last = 0.0
    for i in range(n):
        last = (last + 0.02 * w[i]) / 1.02
        b[i] = last * 3.5
    return b


def burst(n, i0, dur, kind, f, gain, q=0.707, br=False):
    # the preview's burst(): noise through a filter, 10 ms attack, exponential decay to silence at dur
    L = min(int(dur * SR), n - i0)
    if L <= 0:
        return np.zeros(n)
    x = brown(L) if br else rng.uniform(-1, 1, L)
    x = biquad(x, kind, f, q)
    t = np.arange(L) / SR
    att = min(0.01, dur * 0.1)
    e = np.where(t < att, gain * t / att, gain * (0.0001 / gain) ** ((t - att) / (dur - att)))
    out = np.zeros(n)
    out[i0:i0 + L] = x * e
    return out


def ronco():
    n = SR * 7
    b = brown(n)
    x = biquad(biquad(b, "lp", 55), "lp", 55) + 0.3 * biquad(b, "band", 110, 1.2)
    return norm(loop_fade(x), 0.9)


def chiado():
    n = SR * 7
    t = np.arange(n) / SR
    x = biquad(biquad(rng.uniform(-1, 1, n), "hp", 2600), "hp", 2600)
    sw = 0.8 + 0.2 * np.sin(t * 2 * math.pi / 7 * 3) ** 2
    return norm(loop_fade(x * sw), 0.55)


def borbulha():
    n = SR * 7
    x = biquad(brown(n), "band", 180, 1.5)
    # lumps: bubbles bursting through the crust now and then
    for _ in range(40):
        i0 = int(rng.uniform() * (n - SR))
        x += burst(n, i0, 0.12, "band", 120 + rng.uniform() * 200, 0.4, 1.5, True)
    return norm(loop_fade(x), 0.7)


def plop():
    n = int(SR * 0.2)
    x = tone(n, 0, 260, 0.15, 0.25, "sine", 40)
    x += burst(n, 0, 0.08, "band", 700, 0.08, 1.2)
    return norm(x, 0.7)


def baque():
    n = int(SR * 0.4)
    x = burst(n, 0, 0.35, "lp", 140, 0.5, 0.707, True) + burst(n, 0, 0.12, "band", 900, 0.12, 1.5)
    return norm(x, 0.85)


def explosao():
    n = SR * 4
    x = burst(n, 0, 3.2, "lp", 70, 1.1, 0.707, True) + burst(n, int(0.02 * SR), 1.0, "lp", 300, 0.5)
    for _ in range(10):
        x += burst(n, int((0.4 + rng.uniform() * 3) * SR), 0.2, "band", 400 + rng.uniform() * 600, 0.06, 2)
    return norm(x, 0.95)


def trovao():
    n = int(SR * 3.2)
    x = burst(n, 0, 0.25, "hp", 900, 0.25) + burst(n, int(0.05 * SR), 2.8, "lp", 120, 0.7, 0.707, True)
    x += burst(n, int(0.4 * SR), 1.6, "lp", 220, 0.3, 0.707, True)
    return norm(x, 0.95)


write("jogo_ronco", ronco())
write("jogo_chiado", chiado())
write("jogo_borbulha", borbulha())
write("jogo_plop", plop())
write("jogo_baque", baque())
write("jogo_explosao", explosao())
write("jogo_trovao", trovao())
