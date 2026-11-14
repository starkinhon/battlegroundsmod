package com.starkinhon.battlegroundsmod.client.fx;

import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// The living air of the Jujutsu biomes (the ambience doc "Ambientes vivos dos biomas de Jujutsu", Stark 2026-11-12:
// "pétalas de sakura... poluição de luz... corvos... pode usar a criatividade, pra deixar bem vivo"), in TemploFx's
// way: no textures, every mote its own size, tone, spin and path, easing in when the player walks in (BiomeAura) and out
// when he leaves.
// - Floresta Negra: dead leaves, dark spores, purple wisps of cursed energy rising; a short dark green fog.
// - Aldeia Amaldiçoada: gnats in clouds by day, fireflies blinking by night, a white humid haze; crows overhead.
// - Mar de Dagon: sea spray thrown up and falling back.
// - Floresta de Hanami: petals of the giant flowers' five colors, golden pollen, fireflies at night.
// - Terras do Jogo: embers rising, dark ash falling like snow; a red fog that burns redder at night; vultures.
// - Outono de Kyoto: maple leaves (red, crimson, orange, gold) and ginkgo leaves falling and tumbling in the wind.
// - Colônia do Jogo do Abate: not here any more, it has its own full ambience in ColoniaAirFx.
// - Shibuya: not here any more either, its own ambience is ShibuyaAirFx (neon, steam, smog, metro).
// - Shibuya: leaflets flying between the buildings; by day a yellowish smog, by night the light pollution (the fog
//   glows orange-violet from below, never black); crows circling the towers.
public final class JujutsuAirFx {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double AREA = 30;

    enum Kind {
        LEAF, PAPER, ASH, EMBER, MOTE, GLOW_MOTE, FIREFLY, GNAT, SPRAY, WISP
    }

    // A kind of mote: how many, their tones (r, g, b) and size range.
    private record Air(Kind kind, int count, float[][] tones, double size0, double size1) {
    }

    // tint: the fog's color mixed in by day (day) and by night (night); far: the fog's far distance scale (1 = vanilla);
    // birds: crows (or vultures when big) circling overhead.
    private record Mood(String id, ResourceKey<Biome> biome, List<Air> airs, float[] tint, float day, float night, float far,
            int birds, boolean big) {
    }

    private static final class State {
        final Mood mood;
        final double[][][] motes;
        final double[][] birds;
        float strength, lastStrength;

        State(Mood mood) {
            this.mood = mood;
            this.motes = new double[mood.airs().size()][][];
            for (int i = 0; i < motes.length; i++) {
                motes[i] = new double[mood.airs().get(i).count()][];
            }
            this.birds = new double[mood.birds()][];
        }
    }

    private static float[] c(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return new float[] {(v >> 16 & 255) / 255f, (v >> 8 & 255) / 255f, (v & 255) / 255f};
    }

    private static float[][] cs(String... hex) {
        float[][] out = new float[hex.length][];
        for (int i = 0; i < hex.length; i++) {
            out[i] = c(hex[i]);
        }
        return out;
    }

    private static final State[] STATES = {
        new State(new Mood("floresta_negra", ModWorldgen.FLORESTA_NEGRA, List.of(
                new Air(Kind.LEAF, 150, cs("#4a3a2a", "#3a2e22", "#5a4630", "#2e2a1c"), 0.07, 0.12),
                new Air(Kind.MOTE, 220, cs("#2a3a2a", "#1e2a1e", "#3a4430"), 0.025, 0.05),
                new Air(Kind.WISP, 40, cs("#9b4dff", "#7a3cc8", "#b46bff"), 0.08, 0.16)),
                c("#1e2a1e"), 0.35f, 0.5f, 0.55f, 0, false)),
        new State(new Mood("aldeia_amaldicoada", ModWorldgen.ALDEIA_AMALDICOADA, List.of(
                new Air(Kind.GNAT, 140, cs("#1a1a14", "#2a2a20"), 0.02, 0.03),
                new Air(Kind.FIREFLY, 90, cs("#d8ff6a", "#f0ff9a", "#b8ff4a"), 0.05, 0.08)),
                c("#d8dcd8"), 0.25f, 0.1f, 0.7f, 6, false)),
        new State(new Mood("mar_de_dagon", ModWorldgen.MAR_DE_DAGON, List.of(
                new Air(Kind.SPRAY, 70, cs("#e8f4f8", "#ffffff", "#c8e8f0"), 0.03, 0.05)),
                null, 0, 0, 1, 0, false)),
        new State(new Mood("floresta_de_hanami", ModWorldgen.FLORESTA_DE_HANAMI, List.of(
                new Air(Kind.LEAF, 220, cs("#f6f2ec", "#f28ab4", "#f4cc40", "#e2483e", "#fff0f6"), 0.06, 0.11),
                new Air(Kind.GLOW_MOTE, 160, cs("#ffd040", "#fff4b0", "#ffe070"), 0.025, 0.05),
                new Air(Kind.FIREFLY, 60, cs("#c8ff8a", "#e8ffb0"), 0.05, 0.08)),
                c("#cfe2c6"), 0.15f, 0.1f, 0.8f, 0, false)),
        new State(new Mood("terras_do_jogo", ModWorldgen.TERRAS_DO_JOGO, List.of(
                new Air(Kind.EMBER, 220, cs("#ff8a2a", "#ffb040", "#ff5a14", "#ffd070"), 0.03, 0.06),
                new Air(Kind.ASH, 260, cs("#5a5452", "#7a7270", "#3e3a38"), 0.04, 0.08)),
                c("#8a3a1a"), 0.3f, 0.55f, 0.6f, 5, true)),
        new State(new Mood("outono_de_kyoto", ModWorldgen.OUTONO_DE_KYOTO, List.of(
                new Air(Kind.LEAF, 340, cs("#c8301e", "#9a1e18", "#e0682a", "#e8a830", "#f0c838", "#d8401e"), 0.07, 0.12)),
                c("#e8d0b4"), 0.12f, 0.0f, 0.85f, 0, false)),
    };

    private JujutsuAirFx() {
    }

    // ---------------------------------------------------------------- tick

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        for (State s : STATES) {
            s.lastStrength = s.strength;
        }
        if (mc.level == null || mc.player == null) {
            for (State s : STATES) {
                s.strength = 0;
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        Vec3 p = mc.player.position();
        double t = (level.getGameTime() % 240000L) / 20.0;
        double gust = 0.5 + 0.5 * Math.max(0, Math.sin(t * 0.45)) * (0.6 + 0.4 * Math.sin(t * 1.7));
        double windDir = 0.6 + 0.4 * Math.sin(t * 0.05);
        double wx = Math.cos(windDir) * (0.5 + 1.6 * gust), wz = Math.sin(windDir) * (0.5 + 1.6 * gust);
        for (State s : STATES) {
            Mood mood = s.mood;
            boolean inside = BiomeAura.in(mood.id(), b -> b.is(mood.biome()));
            s.strength = Mth.clamp(s.strength + (inside ? 0.015f : -0.02f), 0, 1);
            if (s.strength <= 0) {
                continue;
            }
            for (int a = 0; a < s.motes.length; a++) {
                Air air = mood.airs().get(a);
                for (int i = 0; i < s.motes[a].length; i++) {
                    double[] m = s.motes[a][i];
                    if (m == null) {
                        m = s.motes[a][i] = new double[20];
                        spawn(level, air, m, p, true);
                    }
                    move(level, air, m, p, wx, wz, gust);
                }
            }
            for (int i = 0; i < s.birds.length; i++) {
                if (s.birds[i] == null) {
                    s.birds[i] = bird(p, mood.big());
                }
                double[] b = s.birds[i];
                b[7] = b[0];
                b[8] = b[1];
                b[9] = b[2];
                b[3] += 0.05 * b[4];
                b[5] += 0.05 * (mood.big() ? 3.0 : 9.0 + 4 * Math.sin(b[3] * 0.7));
                b[10] += 0.05 * (b[11] - b[10]) * 0.2;
                // the flock's middle follows the player loosely, so the birds stay in sight
                b[12] += (p.x - b[12]) * 0.002;
                b[13] += (p.z - b[13]) * 0.002;
                b[0] = b[12] + Math.cos(b[3]) * b[6];
                b[2] = b[13] + Math.sin(b[3]) * b[6];
                b[1] = Math.max(p.y, groundAt(level, b[12], b[13])) + b[10] + Math.sin(b[3] * 2.3) * 2;
                if (RANDOM.nextInt(400) == 0) {
                    b[11] = (mood.big() ? 40 : 18) + RANDOM.nextDouble() * 22;
                }
            }
        }
    }

    private static double groundAt(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
    }

    // m: x, y, z, last x, y, z, angle, spin, axis x, y, z, size, tone, phase, last angle, age, vx, vy, vz, anchor
    private static void spawn(ClientLevel level, Air air, double[] m, Vec3 p, boolean anywhere) {
        double reach = air.kind() == Kind.GNAT || air.kind() == Kind.FIREFLY || air.kind() == Kind.SPRAY ? 16 : AREA;
        m[0] = p.x + (RANDOM.nextDouble() - 0.5) * reach * 2;
        m[2] = p.z + (RANDOM.nextDouble() - 0.5) * reach * 2;
        double ground = groundAt(level, m[0], m[2]);
        switch (air.kind()) {
            case LEAF, PAPER, ASH -> m[1] = Math.max(ground + 0.5, p.y - 4) + (anywhere ? RANDOM.nextDouble() * 20 : 12 + RANDOM.nextDouble() * 10);
            case EMBER, WISP -> m[1] = ground + (anywhere ? RANDOM.nextDouble() * 14 : RANDOM.nextDouble() * 1.5);
            case MOTE, GLOW_MOTE -> m[1] = Math.max(ground, p.y - 6) + RANDOM.nextDouble() * 14;
            case FIREFLY -> m[1] = ground + 0.4 + RANDOM.nextDouble() * 2.6;
            case GNAT -> m[1] = ground + 0.6 + RANDOM.nextDouble() * 2;
            case SPRAY -> m[1] = ground + 0.2;
        }
        m[3] = m[0];
        m[4] = m[1];
        m[5] = m[2];
        m[6] = RANDOM.nextDouble() * 6.283;
        m[14] = m[6];
        m[7] = (RANDOM.nextBoolean() ? 1 : -1) * (1.5 + RANDOM.nextDouble() * 3.5);
        double ax = RANDOM.nextDouble() - 0.5, ay = RANDOM.nextDouble() - 0.5, az = RANDOM.nextDouble() - 0.5;
        double l = Math.max(1e-3, Math.sqrt(ax * ax + ay * ay + az * az));
        m[8] = ax / l;
        m[9] = ay / l;
        m[10] = az / l;
        m[11] = air.size0() + RANDOM.nextDouble() * (air.size1() - air.size0());
        // The sea spray only rises off open water: on the sand and the grass the white specks read as a rendering bug
        // (Stark 2026-11-14), so there the mote is left without size until it is spawned again.
        if (air.kind() == Kind.SPRAY && !level.getFluidState(BlockPos.containing(m[0], ground - 1, m[2])).is(net.minecraft.tags.FluidTags.WATER)) {
            m[11] = 0;
        }
        m[12] = RANDOM.nextInt(air.tones().length);
        m[13] = RANDOM.nextDouble() * 6.283;
        m[15] = anywhere ? RANDOM.nextDouble() * 4 : 0;
        m[16] = (RANDOM.nextDouble() - 0.5) * 2.4;
        m[17] = air.kind() == Kind.SPRAY ? 2 + RANDOM.nextDouble() * 3 : 0;
        m[18] = (RANDOM.nextDouble() - 0.5) * 2.4;
        m[19] = m[1];
    }

    private static void move(ClientLevel level, Air air, double[] m, Vec3 p, double wx, double wz, double gust) {
        double dt = 0.05;
        m[3] = m[0];
        m[4] = m[1];
        m[5] = m[2];
        m[14] = m[6];
        m[15] += dt;
        m[13] += dt * (1.1 + m[11] * 4);
        boolean dead = false;
        switch (air.kind()) {
            case LEAF, ASH -> {
                double slow = air.kind() == Kind.ASH ? 0.6 : 1;
                m[6] += dt * m[7] * (0.7 + gust) * slow;
                double edge = Math.abs(Math.sin(m[6]));
                m[1] -= dt * (air.kind() == Kind.ASH ? 0.3 + 0.15 * edge : 0.35 + 0.5 * edge);
                m[0] += dt * (wx * slow + Math.cos(m[13]) * 0.6);
                m[2] += dt * (wz * slow + Math.sin(m[13] * 0.8) * 0.6);
            }
            case PAPER -> {
                m[6] += dt * m[7] * (0.4 + gust);
                m[1] += dt * (-0.18 + gust * 0.5 * Math.sin(m[13] * 0.6));
                m[0] += dt * (wx * 2.2 + Math.cos(m[13]) * 0.8);
                m[2] += dt * (wz * 2.2 + Math.sin(m[13] * 0.7) * 0.8);
            }
            case EMBER -> {
                m[1] += dt * (0.7 + m[11] * 8 + 0.4 * Math.sin(m[13] * 1.3));
                m[0] += dt * (wx * 0.4 + Math.cos(m[13] * 2.1) * 0.5);
                m[2] += dt * (wz * 0.4 + Math.sin(m[13] * 1.7) * 0.5);
                dead = m[15] > 5 + m[11] * 40;
            }
            case WISP -> {
                m[1] += dt * 0.35;
                m[0] += dt * Math.cos(m[13] * 0.9) * 0.4;
                m[2] += dt * Math.sin(m[13] * 0.7) * 0.4;
                dead = m[15] > 7;
            }
            case MOTE, GLOW_MOTE -> {
                m[1] += dt * (Math.sin(m[13] * 0.5) * 0.12 - 0.03);
                m[0] += dt * (wx * 0.25 + Math.cos(m[13] * 0.7) * 0.25);
                m[2] += dt * (wz * 0.25 + Math.sin(m[13] * 0.6) * 0.25);
            }
            case FIREFLY -> {
                m[0] += dt * Math.cos(m[13] * 0.8 + m[6]) * 0.6;
                m[2] += dt * Math.sin(m[13] * 0.6 + m[6]) * 0.6;
                m[1] += dt * Math.sin(m[13] * 1.1) * 0.25;
            }
            case GNAT -> {
                // a cloud: each gnat darts around its anchor
                double r = 0.6 + 0.4 * Math.sin(m[13] * 3.1);
                m[0] = m[3] + (Math.cos(m[13] * 7 + m[6]) * r - (m[0] - m[3]) * 0) * 0.08 + (m[16] * 0 + Math.cos(m[13] * 11) * 0.03);
                m[2] = m[5] + Math.sin(m[13] * 6 + m[6]) * r * 0.08 + Math.sin(m[13] * 13) * 0.03;
                m[1] = m[19] + Math.sin(m[13] * 5 + m[7]) * 0.4;
            }
            case SPRAY -> {
                m[17] -= dt * 9.8;
                m[0] += dt * (m[16] + wx * 0.6);
                m[1] += dt * m[17];
                m[2] += dt * (m[18] + wz * 0.6);
                dead = m[17] < 0 && m[1] < m[19];
            }
        }
        boolean far = Math.abs(m[0] - p.x) > AREA || Math.abs(m[2] - p.z) > AREA || m[1] - p.y > 26 || p.y - m[1] > 20;
        boolean landed = (air.kind() == Kind.LEAF || air.kind() == Kind.ASH || air.kind() == Kind.PAPER)
                && m[1] < groundAt(level, m[0], m[2]) + 0.05;
        if (far || landed || dead || (air.kind() == Kind.SPRAY && !nearWater(level, m))) {
            spawn(level, air, m, p, false);
        }
    }

    // Spray only flies where there is water under it (the surf and the sea).
    private static boolean nearWater(ClientLevel level, double[] m) {
        if (m[15] > 0.1) {
            return true;
        }
        BlockPos at = BlockPos.containing(m[0], groundAt(level, m[0], m[2]) - 1, m[2]);
        return !level.getFluidState(at).isEmpty();
    }

    private static double[] bird(Vec3 p, boolean big) {
        double[] b = new double[14];
        b[12] = p.x + (RANDOM.nextDouble() - 0.5) * 40;
        b[13] = p.z + (RANDOM.nextDouble() - 0.5) * 40;
        b[3] = RANDOM.nextDouble() * 6.283;
        b[4] = (RANDOM.nextBoolean() ? 1 : -1) * (big ? 0.08 : 0.25) * (0.7 + RANDOM.nextDouble() * 0.6);
        b[5] = RANDOM.nextDouble() * 6.283;
        b[6] = (big ? 30 : 10) + RANDOM.nextDouble() * (big ? 30 : 18);
        b[10] = b[11] = (big ? 40 : 18) + RANDOM.nextDouble() * 22;
        b[0] = b[7] = b[12] + Math.cos(b[3]) * b[6];
        b[2] = b[9] = b[13] + Math.sin(b[3]) * b[6];
        b[1] = b[8] = p.y + b[10];
        return b;
    }

    // ---------------------------------------------------------------- render

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float daylight = mc.level.getSkyDarken(partial);
        float night = Mth.clamp((0.8f - daylight) / 0.6f, 0, 1);
        float light = 0.25f + 0.75f * daylight;
        Camera cam = event.getCamera();
        Vec3 camera = cam.getPosition();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        PoseStack.Pose last = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean any = false;
        for (State s : STATES) {
            any |= Mth.lerp(partial, s.lastStrength, s.strength) > 0.001f;
        }
        if (!any) {
            return;
        }
        VertexConsumer out = buffers.getBuffer(FxRenderTypes.AREA);
        for (State s : STATES) {
            float fade = Mth.lerp(partial, s.lastStrength, s.strength);
            if (fade <= 0.001f) {
                continue;
            }
            for (int a = 0; a < s.motes.length; a++) {
                Air air = s.mood.airs().get(a);
                if (glows(air.kind())) {
                    continue;
                }
                float when = air.kind() == Kind.GNAT ? 1 - night : 1;
                for (double[] m : s.motes[a]) {
                    if (m != null) {
                        mote(out, last, air, m, partial, camera, left, up, fade * when, light, false);
                    }
                }
            }
            for (double[] b : s.birds) {
                if (b != null) {
                    bird(out, last, b, partial, camera, fade, s.mood.big());
                }
            }
        }
        buffers.endBatch(FxRenderTypes.AREA);
        VertexConsumer glow = buffers.getBuffer(FxRenderTypes.GLOW);
        for (State s : STATES) {
            float fade = Mth.lerp(partial, s.lastStrength, s.strength);
            if (fade <= 0.001f) {
                continue;
            }
            for (int a = 0; a < s.motes.length; a++) {
                Air air = s.mood.airs().get(a);
                if (!glows(air.kind())) {
                    continue;
                }
                float when = air.kind() == Kind.FIREFLY ? night : air.kind() == Kind.GLOW_MOTE ? 0.6f + 0.4f * night : 1;
                if (when <= 0.01f) {
                    continue;
                }
                for (double[] m : s.motes[a]) {
                    if (m != null) {
                        mote(glow, last, air, m, partial, camera, left, up, fade * when, 1, true);
                    }
                }
            }
        }
        buffers.endBatch(FxRenderTypes.GLOW);
    }

    private static boolean glows(Kind k) {
        return k == Kind.EMBER || k == Kind.WISP || k == Kind.FIREFLY || k == Kind.GLOW_MOTE;
    }

    private static void mote(VertexConsumer out, PoseStack.Pose last, Air air, double[] m, float partial, Vec3 camera,
            Vector3f left, Vector3f up, float fade, float light, boolean glow) {
        double x = Mth.lerp(partial, m[3], m[0]) - camera.x, y = Mth.lerp(partial, m[4], m[1]) - camera.y,
                z = Mth.lerp(partial, m[5], m[2]) - camera.z;
        double d = Math.sqrt(x * x + y * y + z * z);
        float a = (float) (Mth.clamp((AREA - d) / 10, 0, 1) * Mth.clamp((d - 0.4) / 1.2, 0, 1)) * fade;
        switch (air.kind()) {
            case EMBER -> a *= (float) (Mth.clamp(1 - m[15] / (5 + m[11] * 40), 0, 1) * (0.6 + 0.4 * Math.sin(m[13] * 9)));
            case WISP -> a *= (float) (Math.sin(Math.PI * Mth.clamp(m[15] / 7, 0, 1)) * 0.7);
            case FIREFLY -> a *= (float) Math.max(0, Math.sin(m[13] * 1.7 + m[6])) * 1.2f;
            case SPRAY -> a *= 0.8f;
            default -> {
            }
        }
        a = Math.min(a, 1);
        if (a <= 0.01f) {
            return;
        }
        float[] tone = air.tones()[(int) m[12]];
        double size = m[11];
        Kind k = air.kind();
        if (k == Kind.LEAF || k == Kind.PAPER || k == Kind.ASH) {
            // tumbling flat: the half-axes turned about the mote's own axis by its spin
            double ang = Mth.lerp(partial, m[14], m[6]);
            double ax = m[8], ay = m[9], az = m[10];
            double px = Math.abs(ay) < 0.9 ? 0 : 1, py = Math.abs(ay) < 0.9 ? 1 : 0;
            double e1x = -az * py, e1y = az * px, e1z = ax * py - ay * px;
            double l1 = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
            e1x /= l1;
            e1y /= l1;
            e1z /= l1;
            double e2x = ay * e1z - az * e1y, e2y = az * e1x - ax * e1z, e2z = ax * e1y - ay * e1x;
            double c = Math.cos(ang), s = Math.sin(ang);
            double long_ = k == Kind.PAPER ? 1.0 : 1.3, wide = k == Kind.PAPER ? 0.75 : 0.8;
            double ux = (e1x * c + e2x * s) * size * long_, uy = (e1y * c + e2y * s) * size * long_, uz = (e1z * c + e2z * s) * size * long_;
            double vx = ax * size * wide, vy = ay * size * wide, vz = az * size * wide;
            double facing = Math.abs((e2x * c - e1x * s) * 0.3 + (e2y * c - e1y * s) * 0.9 + (e2z * c - e1z * s) * 0.3);
            float kk = (float) (0.78 + 0.22 * facing) * light;
            float tip = k == Kind.PAPER ? a : a * 0.55f;
            quad(out, last, x, y, z, ux, uy, uz, vx, vy, vz, tone[0] * kk, tone[1] * kk, tone[2] * kk, a, tip);
            return;
        }
        // a small diamond facing the camera
        double lx = left.x() * size, ly = left.y() * size, lz = left.z() * size;
        double ux = up.x() * size, uy = up.y() * size, uz = up.z() * size;
        float kk = glow ? 1 : light;
        quad(out, last, x, y, z, lx, ly, lz, ux, uy, uz, tone[0] * kk, tone[1] * kk, tone[2] * kk, a, glow ? a * 0.3f : a * 0.7f);
    }

    // A bird seen from below: a body and two wings flapping (a vulture glides with its wings out).
    private static void bird(VertexConsumer out, PoseStack.Pose last, double[] b, float partial, Vec3 camera, float fade, boolean big) {
        double x = Mth.lerp(partial, b[7], b[0]) - camera.x, y = Mth.lerp(partial, b[8], b[1]) - camera.y,
                z = Mth.lerp(partial, b[9], b[2]) - camera.z;
        double d = Math.sqrt(x * x + y * y + z * z);
        float a = (float) Mth.clamp((140 - d) / 30, 0, 1) * fade * 0.95f;
        if (a <= 0.01f) {
            return;
        }
        double heading = b[3] + (b[4] > 0 ? Math.PI / 2 : -Math.PI / 2);
        double fx = Math.cos(heading), fz = Math.sin(heading);
        double rx = -fz, rz = fx;
        double s = big ? 1.6 : 0.55;
        double flap = big ? 0.12 * Math.sin(b[5] * 0.4) : Math.sin(b[5]) * 0.9;
        double wingY = Math.sin(flap) * s, wingOut = Math.cos(flap) * s;
        float r = big ? 0.16f : 0.08f, g = big ? 0.13f : 0.08f, bl = big ? 0.12f : 0.1f;
        // body
        quad(out, last, x, y, z, fx * s * 0.45, 0, fz * s * 0.45, rx * s * 0.12, 0, rz * s * 0.12, r, g, bl, a, a);
        // wings: from the body out to each side, the tips lifted or dropped by the flap
        for (int side = -1; side <= 1; side += 2) {
            double tx = x + rx * wingOut * side, ty = y + wingY, tz = z + rz * wingOut * side;
            double mx = (x + tx) / 2, my = (y + ty) / 2, mz = (z + tz) / 2;
            double hx = (tx - x) / 2, hy = (ty - y) / 2, hz = (tz - z) / 2;
            quad(out, last, mx, my, mz, hx, hy, hz, fx * s * 0.2, 0, fz * s * 0.2, r, g, bl, a, a * 0.8f);
        }
    }

    private static void quad(VertexConsumer out, PoseStack.Pose last, double x, double y, double z, double ux, double uy, double uz,
            double vx, double vy, double vz, float r, float g, float b, float a, float tip) {
        out.addVertex(last, (float) (x + ux), (float) (y + uy), (float) (z + uz)).setColor(r, g, b, tip);
        out.addVertex(last, (float) (x + vx), (float) (y + vy), (float) (z + vz)).setColor(r, g, b, a);
        out.addVertex(last, (float) (x - ux), (float) (y - uy), (float) (z - uz)).setColor(r, g, b, tip);
        out.addVertex(last, (float) (x - vx), (float) (y - vy), (float) (z - vz)).setColor(r, g, b, a);
        out.addVertex(last, (float) (x + ux), (float) (y + uy), (float) (z + uz)).setColor(r, g, b, tip);
        out.addVertex(last, (float) (x - vx), (float) (y - vy), (float) (z - vz)).setColor(r, g, b, a);
        out.addVertex(last, (float) (x - ux), (float) (y - uy), (float) (z - uz)).setColor(r, g, b, tip);
        out.addVertex(last, (float) (x + vx), (float) (y + vy), (float) (z + vz)).setColor(r, g, b, a);
    }

    // ---------------------------------------------------------------- sky and fog

    // The fog's color: each biome's tint mixed in by day and by night (Shibuya's light pollution: the night glows).
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float daylight = mc.level.getSkyDarken(partial);
        float night = Mth.clamp((0.8f - daylight) / 0.6f, 0, 1);
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        for (State s : STATES) {
            float fade = Mth.lerp(partial, s.lastStrength, s.strength);
            if (fade <= 0.001f || s.mood.tint() == null) {
                continue;
            }
            float k = fade * Mth.lerp(night, s.mood.day(), s.mood.night());
            float[] t = s.mood.tint();
            // by night the tint is a glow of its own, not lit by the (dark) sky
            float lit = Mth.lerp(night, 1, 0.55f);
            r = Mth.lerp(k, r, t[0] * lit);
            g = Mth.lerp(k, g, t[1] * lit);
            b = Mth.lerp(k, b, t[2] * lit);
        }
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float scale = 1;
        for (State s : STATES) {
            float fade = Mth.lerp(partial, s.lastStrength, s.strength);
            if (fade > 0.001f) {
                scale = Math.min(scale, Mth.lerp(fade, 1, s.mood.far()));
            }
        }
        if (scale >= 0.999f) {
            return;
        }
        event.setFarPlaneDistance(event.getFarPlaneDistance() * scale);
        event.setNearPlaneDistance(event.getNearPlaneDistance() * scale);
        event.setCanceled(true);
    }
}
