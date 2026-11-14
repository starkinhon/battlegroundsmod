# Shibuya's own sounds (ambience port, Stark 2026-11-14), built here from noise and tones the same way the approved
# preview builds them live (forja/jujutsu-shibuya-ambiente amb.js sfx and metro.js msfx): no recording.
#   shibuya_cidade        10 s loop: the city's low hum (air conditioning, far traffic) and the traffic band
#   shibuya_neon           2 s loop: a near neon's buzz, 120 Hz and its harmonics
#   shibuya_estalo       0.8 s: the crackle of a sign with a bad contact
#   shibuya_vapor          4 s loop: the hiss of a manhole's steam
#   shibuya_carro        3.5 s: a car passing on a near street
#   shibuya_sirene       2.8 s loop: the police car's siren, up and down (the game bends its pitch for the Doppler)
#   shibuya_sirene_longe 8.5 s: a far siren rising and fading
#   metro_ronco            5 s loop: the train's rumble and the roar of its wheels in the tunnel
#   metro_motor            2 s loop: the VVVF motor's two saw tones (the game steps its pitch like the preview's scale)
#   metro_freio            2 s loop: the brake's squeal at the end of the braking
#   metro_trilho        0.25 s: the wheels' clack at a rail joint (two, one per axle)
#   metro_porta          1.4 s: the doors' air hiss
#   metro_aviso            2 s: the train-approaching chime (three rising notes)
#   metro_chegada        1.2 s: the doors-open chime
#   metro_pinpon         1.6 s: the doors-closing "pin-pon" three times
#   metro_melodia        3.8 s: the platform's departure melody (12 marimba notes, made up)
# Rerunning writes the same files (fixed seed). Run from the repo root; needs numpy and ffmpeg (libvorbis).
import math
import subprocess
import wave

import numpy as np

SR = 44100
OUT = "src/main/resources/assets/battlegroundsmod/sounds/"
rng = np.random.default_rng(1415)


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


def brown(n):
    w = rng.uniform(-1, 1, n)
    out = np.zeros(n)
    last = 0.0
    for i in range(n):
        last = (last + 0.02 * w[i]) / 1.02
        out[i] = last * 3.5
    return out


def white(n):
    return rng.uniform(-1, 1, n)


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


def tone(out, f0, dur, g, when, kind="sine", f1=None):
    # the preview's tone(): a 10 ms attack and an exponential fall to nothing at dur
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f0 if f1 is None else f0 * (f1 / f0) ** (t / dur)
    ph = 2 * math.pi * np.cumsum(np.full(n, 1.0) * f) / SR
    if kind == "sine":
        w = np.sin(ph)
    elif kind == "tri":
        w = 2 / math.pi * np.arcsin(np.sin(ph))
    else:
        w = 2 * ((ph / (2 * math.pi)) % 1) - 1
    e = np.minimum(1, t / 0.01) * np.exp(np.log(1e-4) * t / dur)
    s = int(when * SR)
    out[s:s + n] += w[:len(out) - s] * e[:len(out) - s] * g


def cidade():
    n = SR * 11
    t = np.arange(n) / SR
    hum = biquad(brown(n), "lp", 260) * 0.10
    traf = biquad(brown(n), "band", 700, 0.6) * 0.07 * (0.75 + 0.25 * np.sin(t * 2 * math.pi / 11 * 2))
    write("shibuya_cidade", norm(loop_fade(hum + traf), 0.6))


def neon():
    n = SR * 2
    t = np.arange(n) / SR
    saw = 2 * ((t * 120) % 1) - 1
    x = biquad(np.tile(saw, 2), "band", 360, 2)[n:]
    write("shibuya_neon", norm(x * (0.9 + 0.1 * np.sin(t * 2 * math.pi * 3)), 0.5))


def estalo():
    n = int(SR * 0.8)
    out = np.zeros(n)
    for _ in range(6):
        d = 0.02 + rng.random() * 0.03
        k = int(d * SR)
        s = int(rng.random() * 0.6 * SR)
        b = biquad(white(k), "hp", 3000) * np.exp(-np.arange(k) / (k / 3))
        out[s:s + k] += b[:n - s]
    write("shibuya_estalo", norm(out, 0.8))


def vapor():
    n = SR * 5
    t = np.arange(n) / SR
    x = biquad(white(n), "hp", 2500) * (0.8 + 0.2 * np.sin(t * 2 * math.pi / 5 * 3))
    write("shibuya_vapor", norm(loop_fade(x), 0.45))


def carro():
    n = int(SR * 3.5)
    t = np.arange(n) / SR
    f = 500 + 500 * rng.random()
    x = biquad(brown(n), "band", f, 0.8)
    e = np.minimum(1, t / 1.4) * np.exp(-np.maximum(0, t - 1.4) / 0.7)
    write("shibuya_carro", norm(x * e, 0.7))


def sirene():
    # two up-and-down sweeps (700 to 1000 Hz, the preview's 4.5 rad/s), a triangle wave, closing on itself
    per = 2 * math.pi / 4.5
    n = int(round(SR * per * 2))
    t = np.arange(n) / SR
    f = 700 + 300 * (0.5 + 0.5 * np.sin(t * 4.5))
    ph = 2 * math.pi * np.cumsum(f) / SR
    w = 2 / math.pi * np.arcsin(np.sin(ph))
    write("shibuya_sirene", norm(w, 0.5))


def sirene_longe():
    n = int(SR * 8.5)
    t = np.arange(n) / SR
    tt = t % 1.4
    f = np.where(tt < 0.7, 620 + 360 * tt / 0.7, 980 - 360 * (tt - 0.7) / 0.7)
    ph = 2 * math.pi * np.cumsum(f) / SR
    w = biquad(2 / math.pi * np.arcsin(np.sin(ph)), "lp", 1200)
    e = np.minimum(1, t / 2) * np.clip((8.4 - t) / 6.4, 0, 1)
    write("shibuya_sirene_longe", norm(w * e, 0.5))


def ronco():
    n = SR * 6
    x = biquad(brown(n), "lp", 150) * 0.6 + biquad(brown(n), "band", 500, 0.7) * 0.4
    write("metro_ronco", norm(loop_fade(x), 0.8))


def motor():
    n = SR * 2
    t = np.arange(n) / SR
    s1 = 2 * ((t * 523) % 1) - 1
    s2 = 2 * ((t * 784.5) % 1) - 1
    x = biquad(np.tile(s1 + s2, 2), "band", 900, 3)[n:]
    write("metro_motor", norm(x, 0.5))


def freio():
    n = SR * 2
    t = np.arange(n) / SR
    f = 2900 + 180 * np.sin(t * 2 * math.pi * 23)
    write("metro_freio", norm(np.sin(2 * math.pi * np.cumsum(f) / SR), 0.4))


def trilho():
    n = int(SR * 0.25)
    out = np.zeros(n)
    for when, g in ((0, 0.12), (0.09, 0.1)):
        k = int(0.08 * SR)
        b = biquad(brown(k), "band", 1300, 2) * g * np.exp(np.log(1e-4) * np.arange(k) / (0.07 * SR))
        s = int(when * SR)
        out[s:s + k] += b[:n - s]
    write("metro_trilho", norm(out, 0.8))


def porta():
    n = int(SR * 1.4)
    t = np.arange(n) / SR
    x = biquad(white(n), "hp", 2200) * np.minimum(1, t / 0.05) * np.exp(np.log(1e-4) * t / 1.3)
    write("metro_porta", norm(x, 0.6))


def aviso():
    out = np.zeros(int(SR * 2))
    tone(out, 784, 0.6, 0.06, 0)
    tone(out, 988, 0.6, 0.06, 0.35)
    tone(out, 1175, 0.9, 0.06, 0.7)
    write("metro_aviso", norm(out, 0.6))


def chegada():
    out = np.zeros(int(SR * 1.2))
    tone(out, 1320, 0.4, 0.05, 0.0)
    tone(out, 990, 0.6, 0.05, 0.3)
    write("metro_chegada", norm(out, 0.6))


def pinpon():
    out = np.zeros(int(SR * 1.6))
    for i in range(3):
        tone(out, 1400, 0.18, 0.045, i * 0.5)
        tone(out, 1050, 0.25, 0.045, i * 0.5 + 0.2)
    write("metro_pinpon", norm(out, 0.6))


def melodia():
    notes = [659, 784, 880, 988, 880, 784, 659, 587, 659, 784, 659, 523]
    out = np.zeros(int(SR * 3.8))
    for i, f in enumerate(notes):
        tone(out, f, 0.5, 0.05, i * 0.27)
        tone(out, f * 2, 0.2, 0.012, i * 0.27)
    write("metro_melodia", norm(out, 0.6))


if __name__ == "__main__":
    for fn in (cidade, neon, estalo, vapor, carro, sirene, sirene_longe, ronco, motor, freio, trilho, porta, aviso, chegada, pinpon, melodia):
        fn()
