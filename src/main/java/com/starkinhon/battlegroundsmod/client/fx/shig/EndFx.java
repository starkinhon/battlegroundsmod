package com.starkinhon.battlegroundsmod.client.fx.shig;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.starkinhon.battlegroundsmod.client.ScreenShake;
import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;
import com.starkinhon.battlegroundsmod.power.powers.shigaraki.Decay;
import com.starkinhon.battlegroundsmod.power.powers.shigaraki.DecayNoise;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// Shift+V, Fim do Mundo (preview v6): "Desapareça." while he kneels and raises the hand; the five fingers touch the
// ground (impact frame), and the Decadência runs out at 24 blocks a second to 150, climbing whatever stands, with a wall
// of dust riding its front (souls_shig_end, raymarched in a ring, three anime tones). Around the camera every block
// the front takes dries, cracks and crumbles like the other seeds (DecayBlocks), at the same times as the server.
public final class EndFx {
    private static final int NEAR = 72;

    private static final class E {
        final int caster;
        final double start;
        double touchAt = -1;
        Vec3 c;
        float speed = 24, reach = 150;
        int gy;
        boolean breaks;
        // the columns around the camera already handed to DecayBlocks (by ring, like the server)
        final java.util.Set<Long> done = new java.util.HashSet<>();

        E(int caster, double start) {
            this.caster = caster;
            this.start = start;
        }
    }

    private static final List<E> LIST = new ArrayList<>();

    private EndFx() {
    }

    static boolean active() {
        return !LIST.isEmpty();
    }

    static void clear() {
        LIST.clear();
    }

    static void receive(int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        double now = ShigFx.clock();
        int mode = Math.round(a);
        var mc = Minecraft.getInstance();
        if (mode == 0) {
            LIST.removeIf(e -> e.caster == id);
            LIST.add(new E(id, now));
            if (mc.player != null && mc.player.position().distanceTo(pos) < 48) {
                mc.gui.setOverlayMessage(Component.translatable("message.battlegroundsmod.shigaraki.desapareca").withStyle(ChatFormatting.DARK_RED,
                        ChatFormatting.ITALIC), false);
            }
            return;
        }
        E e = null;
        for (E q : LIST) {
            if (q.caster == id) {
                e = q;
            }
        }
        if (mode == 2) {
            if (e != null) {
                LIST.remove(e);
            }
            return;
        }
        if (e == null) {
            e = new E(id, now - 1.5);
            LIST.add(e);
        }
        e.touchAt = now;
        e.c = pos;
        e.speed = b;
        e.reach = c;
        e.gy = Mth.floor(pos.y + 0.01);
        e.breaks = dir.y > 0.5;
        if (mc.player != null) {
            double d = mc.player.position().distanceTo(pos);
            if (d < 40) {
                ScreenFx.impactFrame(new int[] {4, 3, 4, 3}, 1, 0xFFFFFF, pos, 0);
            }
            if (d < c) {
                ScreenShake.start((float) (1.3 * (1 - d / c * 0.7)), 60);
            }
        }
    }

    static void tick() {
        double now = ShigFx.clock();
        LIST.removeIf(e -> now - e.start > 11.5 || (e.touchAt >= 0 && now - e.touchAt > 10));
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for (E e : LIST) {
            if (e.touchAt < 0) {
                continue;
            }
            float t = (float) (now - e.touchAt);
            float upTo = Math.min(e.reach + 4, (t + 1) * e.speed * 1.15f);
            columns(level, e, cam, upTo);
        }
    }

    // The columns near the camera the front reaches within the next second, with the server's times (FimDoMundo).
    private static void columns(ClientLevel level, E e, Vec3 cam, float upTo) {
        int cx = Mth.floor(cam.x), cz = Mth.floor(cam.z);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        var caster = level.getEntity(e.caster);
        int added = 0;
        for (int dx = -NEAR; dx <= NEAR && added < 900; dx++) {
            for (int dz = -NEAR; dz <= NEAR && added < 900; dz++) {
                int x = cx + dx, z = cz + dz;
                float px = x + 0.5f, pz = z + 0.5f;
                float dh = (float) Math.hypot(px - e.c.x, pz - e.c.z);
                if (dh > upTo || dh > e.reach + DecayNoise.vn(px * 0.33f, e.gy * 0.33f, pz * 0.33f) * 4) {
                    continue;
                }
                long key = BlockPos.asLong(x, 0, z);
                if (!e.done.add(key)) {
                    continue;
                }
                added++;
                boolean safe = caster != null && (px - caster.getX()) * (px - caster.getX()) + (pz - caster.getZ()) * (pz - caster.getZ()) < 2.2 * 2.2;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int y = e.gy - 2; y <= top; y++) {
                    if (safe && y < e.gy) {
                        continue;
                    }
                    at.set(x, y, z);
                    BlockState st = level.getBlockState(at);
                    if (!Decay.decays(level, at, st)) {
                        continue;
                    }
                    float when = DecayNoise.wave(px, y + 0.5f, pz, dh, e.speed, y + 0.5f - e.gy, 12);
                    DecayBlocks.add(level, at.immutable(), st, e.touchAt + when, e.breaks);
                }
            }
        }
    }

    static void render(Vec3 camera) {
        ShaderInstance sh = ShigShaders.end;
        if (LIST.isEmpty() || sh == null) {
            return;
        }
        double now = ShigFx.clock();
        for (E e : LIST) {
            if (e.touchAt < 0) {
                continue;
            }
            float t = (float) (now - e.touchAt);
            float R = Math.max(t, 0) * e.speed;
            if (t > 8.5f) {
                continue;
            }
            float W = Math.min(4 + R * 0.12f, 14), H = Math.min(8 + R * 0.2f, 32);
            float A = ShigDraw.smooth(t / 0.25f) * (1 - ShigDraw.smooth((R - 125) / 30));
            if (A < 0.005f) {
                continue;
            }
            float cx = (float) (e.c.x - camera.x), cz = (float) (e.c.z - camera.z), gy = (float) (e.gy - camera.y);
            sh.safeGetUniform("C").set(cx, cz);
            sh.safeGetUniform("R").set(R);
            sh.safeGetUniform("W").set(W);
            sh.safeGetUniform("H").set(H);
            sh.safeGetUniform("T").set((float) (now % 1000));
            sh.safeGetUniform("A").set(A);
            sh.safeGetUniform("Gy").set(gy);
            for (int part = 0; part < 2; part++) {
                sh.safeGetUniform("Part").set((float) part);
                BufferBuilder b = ShigDraw.begin();
                cylinder(b, cx, gy - 1, cz, R + 3, 42, 160);
                ShigDraw.draw(b, sh, ShigDraw.ALPHA, true, part == 1);
            }
        }
    }

    // An open cylinder, faces outwards.
    private static void cylinder(BufferBuilder b, float cx, float y0, float cz, float r, float h, int n) {
        for (int j = 0; j < n; j++) {
            double a0 = j / (double) n * Math.PI * 2, a1 = (j + 1) / (double) n * Math.PI * 2;
            float x0 = cx + (float) Math.cos(a0) * r, z0 = cz + (float) Math.sin(a0) * r, x1 = cx + (float) Math.cos(a1) * r, z1 = cz + (float) Math.sin(a1) * r;
            float[][] q = {{x0, y0, z0}, {x0, y0 + h, z0}, {x1, y0 + h, z1}, {x0, y0, z0}, {x1, y0 + h, z1}, {x1, y0, z1}};
            for (float[] p : q) {
                ShigDraw.v(b, p[0], p[1], p[2], 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0);
            }
        }
    }
}
