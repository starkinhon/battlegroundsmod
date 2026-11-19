# The Templos Antigos' own sounds, built here from noise and tones the same way the approved preview builds them live
# (forja/jujutsu-templo-ambiente/amb.js, sfx): no recording.
#   templo_vento    7 s loop: the low wind in the cedars (low-passed noise), the game raises it with the gusts
#   templo_sino     14 s: the big bell, detuned partials that beat and "wave", the log's dry knock, a long tail
#   templo_asas     1.6 s: the doves taking off, many wing claps in the first second and a half
#   templo_tobi     1.7 s: the black kite's "pii-hyorororo", a whistle that rises, then falls trembling
#   templo_uguisu   1.8 s: the bush warbler's "hoo... hokekyo"
#   templo_coruja   1.3 s: an owl's two low hoots
# Rerunning writes the same files (fixed seed). Run from the repo root; needs numpy and ffmpeg (libvorbis).
import math
import subprocess
import wave

import numpy as np

SR = 44100
OUT = "src/main/resources/assets/battlegroundsmod/sounds/"
rng = np.random.default_rng(2119)


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


def vento():
    n = SR * 8
    t = np.arange(n) / SR
    x = biquad(biquad(rng.uniform(-1, 1, n), "lp", 420), "lp", 420)
    sw = 0.65 + 0.35 * np.sin(t * 2 * math.pi / 8 * 2 + 0.4) ** 2
    return norm(loop_fade(x * sw), 0.75)


def sino():
    n = SR * 14
    x = np.zeros(n)
    for f, g, d in [(64, 0.12, 13.99), (65.3, 0.1, 13.99), (128.6, 0.05, 9), (171, 0.04, 8), (214, 0.025, 6), (287, 0.015, 4), (356, 0.01, 3)]:
        x += tone(n, 0, f, d, g)
    L = int(0.08 * SR)
    x[:L] += biquad(rng.uniform(-1, 1, L), "band", 300, 2) * np.exp(-np.arange(L) / SR / 0.025) * 0.4
    return norm(x, 0.85)


def asas():
    n = int(SR * 1.6)
    x = np.zeros(n)
    for _ in range(40):
        L = int(0.04 * SR)
        i0 = int(rng.uniform() * 1.4 * SR)
        x[i0:i0 + L] += biquad(rng.uniform(-1, 1, L), "band", 700 + rng.uniform() * 600, 1.5) * np.exp(-np.arange(L) / SR / 0.012)
    return norm(x, 0.8)


def tobi():
    n = int(SR * 1.7)
    x = tone(n, 0, 2600, 0.5, 0.05, "sine", 2900)
    # the "hyorororo": the falling part trembles
    m = tone(n, int(0.5 * SR), 2900, 1.15, 0.04, "sine", 1900)
    t = np.arange(n) / SR
    x += m * (0.75 + 0.25 * np.sin(2 * math.pi * 11 * t))
    return norm(x, 0.7)


def uguisu():
    n = int(SR * 1.8)
    x = tone(n, 0, 1150, 0.9, 0.025, "sine", 1250)
    x += tone(n, int(1.05 * SR), 2300, 0.12, 0.03, "sine", 2100)
    x += tone(n, int(1.2 * SR), 1900, 0.12, 0.03, "sine", 2400)
    x += tone(n, int(1.35 * SR), 2500, 0.3, 0.03, "sine", 1800)
    return norm(x, 0.7)


def coruja():
    n = int(SR * 1.3)
    x = tone(n, 0, 380, 0.35, 0.03, "sine", 360) + tone(n, int(0.6 * SR), 370, 0.6, 0.03, "sine", 340)
    return norm(biquad(x, "lp", 900), 0.7)


write("templo_vento", vento())
write("templo_sino", sino())
write("templo_asas", asas())
write("templo_tobi", tobi())
write("templo_uguisu", uguisu())
write("templo_coruja", coruja())
