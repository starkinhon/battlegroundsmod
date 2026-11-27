// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.DbFx.bb;
import static com.starkinhon.battlegroundsmod.client.fx.DbFx.rel;
import static com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.hash;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.power.powers.sub.jj.Jj2Net;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// The remade Jujutsu SubAlmas of the port's second batch (Todo, Nanami, Itadori; power/powers/sub/jj/Jj2Net, SubNet kinds
// 600-619), drawn as their approved previews draw them (forja/todo-kit/shinjuku-todo.js, forja/nanami-kit/
// nanami-shinjuku.js, forja/itadori-kit/shinjuku.js). Drawn inside DbFx's passes; age in ticks.
final class Jj2Fx {
    private static final float[] BF_RED = FxDraw.rgb(0xFF2A3A);
    private static final float[] BF_BLACK = {0.02f, 0, 0.03f};

    private static final class Fx {
        final int kind;
        final int id;
        final Vec3 pos;
        final Vec3 dir;
        final float a;
        final float b;
        final float c;
        final double born;

        Fx(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c, double now) {
            this.kind = kind;
            this.id = id;
            this.pos = pos;
            this.dir = dir;
            this.a = a;
            this.b = b;
            this.c = c;
            this.born = now;
        }
    }

    private static final List<Fx> SHOTS = new ArrayList<>();

    private Jj2Fx() {
    }

    static boolean handles(int kind) {
        return Jj2Net.has(kind);
    }

    static void receive(int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        SHOTS.add(new Fx(kind, id, pos, dir, a, b, c, ScreenFx.now()));
    }

    static boolean active() {
        return !SHOTS.isEmpty();
    }

    static void clear() {
        SHOTS.clear();
    }

    private static double life(Fx f) {
        return switch (f.kind) {
            case Jj2Net.TODO_GRAY -> f.a + 4;
            default -> 40;
        };
    }

    static void draw(double now, float partial) {
        if (DbFx.pass) {
            SHOTS.removeIf(f -> now - f.born > life(f));
        }
        for (Fx f : SHOTS) {
            float age = (float) (now - f.born) + partial;
            if (age < 0) {
                continue;
            }
            switch (f.kind) {
                case Jj2Net.TODO_GRAY -> todoGray(f, age);
                default -> {
                }
            }
        }
    }

    // ---------- Todo ----------

    // The V's contact (preview: phase 'contato'): the screen goes grey in 0.12 s and stays for the slow motion (a ticks),
    // then the color comes back in 0.12 s; small black and red sparks crackle on the fist, more as it squeezes.
    private static void todoGray(Fx f, float age) {
        float in = Mth.clamp(age / 2.4f, 0, 1);
        float out = 1 - Mth.clamp((age - f.a) / 2.4f, 0, 1);
        float g = Math.min(in, out);
        Minecraft mc = Minecraft.getInstance();
        if (g > 0 && mc.cameraEntity != null && mc.cameraEntity.position().distanceTo(f.pos) < 48) {
            // Mode 14 (Saitama's silence) as a plain grey: all grey, no dark edges, no lines.
            ScreenFx.sustainGrade(14, g, 1f, 0f, 0f, f.pos);
        }
        if (age > f.a) {
            return;
        }
        Vector3f p = rel(f.pos);
        float sq = Mth.clamp(age / f.a, 0, 1);
        int frame = (int) (age * 2);
        int n = 1 + (int) (sq * 3);
        for (int i = 0; i < n; i++) {
            int s = frame * 7 + i * 13 + f.id;
            if (hash(s, 7, 61) < 0.3f) {
                continue;
            }
            float ang = hash(s, 1, 61) * Mth.TWO_PI;
            float up = hash(s, 2, 61);
            float r = (0.25f + 0.6f * hash(s, 3, 61)) * (0.6f + 0.8f * sq);
            Vector3f q = new Vector3f(p).add(Mth.cos(ang) * r * 0.7f, (up - 0.3f) * r, Mth.sin(ang) * r * 0.7f);
            float size = 0.05f + 0.05f * hash(s, 4, 61);
            bb(q, size * 1.6f, ang, FxDraw.FLARE, 4 / 32f, BF_RED, 0.9f, true);
            bb(q, size, ang, FxDraw.SOLID, 0, BF_BLACK, 1, false);
        }
    }
}
