// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.ItadoriDomain;
import com.starkinhon.battlegroundsmod.skill.DominioSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Itadori's Expansão de Domínio (power/powers/sub/jj/ItadoriDomain), as the preview Stark approved
// (forja/itadori-kit/shinjuku.js, SCENES.dominio, 2026-11-27):
// - out in the world: the black dome closing round him with a pale blue rim (it stands, dimmer, while it waits on those
//   running), then the smooth black ball while it is open.
// - inside (its space above the world): all black, and the station showing from where he stood outwards: the platform
//   of grey tiles with the yellow lines at its edges, the pillars, the steel trusses and roof, the lamps, the timetables,
//   the benches, the tracks lower on both sides, and the suburban trains coming out of the dark and going back into it.
//   The far off goes into the black (the preview's fog, 14 to 44 blocks).
// - the end (it closes, or Gojo or Sukuna break it in the Duelo de Expansões): it breaks like glass, Gojo's way
//   (DomainGlass): cracks run out of one point, then it shatters into pieces that fly, fall and bounce (Stark 2026-11-28).
// Everything is in the domain's frame: his feet at 0, facing +z, y up (localToRel turns it into the world).
public final class ItadoriDomainScene {
    private static final double RD = ItadoriDomain.RADIUS;
    private static final float FOG_NEAR = 14, FOG_FAR = 44;
    // The station: its length, the trains (cars, their length and gap), their speeds.
    private static final float L = 86;
    private static final int NC = 6;
    private static final float CW = 3.4f, CH = 4.4f, CL = 22f, CY = -0.35f, SP = CL + 0.6f;
    // How far the trains show (the shader cuts them off past it), and where they wait, just past it.
    private static final double TRAIN_REVEAL = RD + 12, PARK = TRAIN_REVEAL + 2;

    private static final class Dom {
        final int caster;
        final Vec3 center;
        final Vec3 arena;
        float yaw;
        final double start;
        double openAt = -1;
        double closeAt = -1;
        double goneAt = -1;
        boolean part;
        double last = -1;
        // each train: z, speed, wait. They wait out of sight, past where the station shows (PARK), never parked at the edge
        // (Stark 2026-11-28).
        final double[][] trains = {{PARK, -24, 2}, {-PARK - NC * SP, 30, 6}};
        long seed;

        Dom(int caster, Vec3 center, float yaw, double start) {
            this.caster = caster;
            this.center = center;
            this.yaw = yaw;
            this.start = start;
            this.arena = DominioSpec.arena(Minecraft.getInstance().level, center);
            this.seed = caster * 31L + (long) start;
        }
    }

    private record Shatter(Vec3 center, double radius, double start) {
    }

    // How long the cracks run before it shatters, when it closes and when a stronger domain breaks it; how long the
    // pieces last after.
    private static final double CLOSE_CRACK = 12, BREAK_CRACK = 4, GLASS_LIFE = 52;

    // The dome breaking like glass, on its own (the domain itself may be gone while the pieces still fall).
    private static final class Glass {
        final int caster;
        final Vec3 center;
        final Vec3 arena;
        final double radius;
        final double start;
        final double crack;
        DomainGlass pattern;
        int played;

        Glass(int caster, Vec3 center, Vec3 arena, double radius, double start, double crack) {
            this.caster = caster;
            this.center = center;
            this.arena = arena;
            this.radius = radius;
            this.start = start;
            this.crack = crack;
        }
    }

    private static final List<Dom> DOMS = new ArrayList<>();
    private static final List<Shatter> SHATTERS = new ArrayList<>();
    private static final List<Glass> GLASS = new ArrayList<>();
    private static VertexBuffer stationGpu;
    private static VertexBuffer trainGpu;

    private ItadoriDomainScene() {
    }

    // ---------- from the server ----------

    public static void event(int caster, Vec3 center, Vec3 dir, float a, float b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = now();
        int mode = Math.round((float) dir.y);
        Dom d = find(caster);
        switch (mode) {
            case ItadoriDomain.M_SIGN -> {
                if (d != null) {
                    DOMS.remove(d);
                }
                DOMS.add(new Dom(caster, center, (float) dir.x, now - b));
            }
            case ItadoriDomain.M_OPEN -> {
                if (d == null) {
                    d = new Dom(caster, center, (float) dir.x, now - b);
                    DOMS.add(d);
                }
                d.openAt = now;
                d.part = dir.z > 0.5;
                d.yaw = (float) dir.x;
            }
            case ItadoriDomain.M_CLOSE -> {
                if (d != null && d.closeAt < 0) {
                    d.closeAt = now;
                }
                if (d == null || d.closeAt == now) {
                    double r = d == null ? RD : d.openAt >= 0 ? RD : Math.max(1, ItadoriDomain.waveRadius(now - d.start));
                    breakGlass(caster, d == null ? center : d.center, d == null ? DominioSpec.arena(mc.level, center) : d.arena, r, now, CLOSE_CRACK);
                }
            }
            case ItadoriDomain.M_GONE -> {
                if (d != null) {
                    d.goneAt = now;
                }
            }
            case ItadoriDomain.M_BREAK -> {
                double r = d == null || d.openAt >= 0 ? RD : Math.max(1, ItadoriDomain.waveRadius(now - d.start));
                if (d != null) {
                    DOMS.remove(d);
                }
                breakGlass(caster, d == null ? center : d.center, d == null ? DominioSpec.arena(mc.level, center) : d.arena, r, now, BREAK_CRACK);
            }
            case ItadoriDomain.M_FAIL -> SHATTERS.add(new Shatter(center.add(0, 1.2, 0), 3, now));
            default -> {
            }
        }
    }

    private static Dom find(int caster) {
        for (Dom d : DOMS) {
            if (d.caster == caster) {
                return d;
            }
        }
        return null;
    }

    public static boolean active() {
        return !DOMS.isEmpty() || !SHATTERS.isEmpty() || !GLASS.isEmpty();
    }

    public static void clear() {
        DOMS.clear();
        SHATTERS.clear();
        GLASS.clear();
    }

    private static void breakGlass(int caster, Vec3 center, Vec3 arena, double radius, double now, double crack) {
        GLASS.removeIf(g -> g.caster == caster);
        GLASS.add(new Glass(caster, center, arena, radius, now, crack));
    }

    private static double now() {
        return ScreenFx.now() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    // ---------- every tick: the floor under the local player ----------

    public static void tick() {
        if (DOMS.isEmpty() && SHATTERS.isEmpty() && GLASS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double now = ScreenFx.now();
        SHATTERS.removeIf(s -> now - s.start > 40);
        GLASS.removeIf(g -> now - g.start > g.crack + GLASS_LIFE || mc.level == null);
        Iterator<Dom> it = DOMS.iterator();
        while (it.hasNext()) {
            Dom d = it.next();
            if ((d.goneAt >= 0 && now > d.goneAt + 4) || now - d.start > 20 * 60 * 2 || mc.level == null) {
                it.remove();
                continue;
            }
            if (d.openAt >= 0 && d.goneAt < 0 && mc.player != null) {
                floor(d, mc.player);
            }
        }
    }

    // The station has no blocks: its floor (the platform, the tracks lower) holds the local player up, so they walk and
    // jump as on the ground and step up onto the platform; the barrier stops them softly.
    private static void floor(Dom d, LocalPlayer player) {
        Vec3 pos = player.position();
        if (!nearArena(d, pos)) {
            return;
        }
        double y = ItadoriDomain.floorAt(d.arena, d.yaw, pos.x, pos.z);
        if (pos.y <= y + 0.001 && pos.y > y - 2.5) {
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
            }
            player.setPos(pos.x, y, pos.z);
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Math.max(0, v.y), v.z);
            player.setOnGround(true);
            player.resetFallDistance();
        }
        pos = player.position();
        double dx = pos.x - d.arena.x, dz = pos.z - d.arena.z, r = Math.sqrt(dx * dx + dz * dz), max = RD - 1.5;
        if (r > max) {
            player.setPos(d.arena.x + dx / r * max, pos.y, d.arena.z + dz / r * max);
            Vec3 v = player.getDeltaMovement();
            double out = (v.x * dx + v.z * dz) / r;
            if (out > 0) {
                player.setDeltaMovement(v.x - dx / r * out, v.y, v.z - dz / r * out);
            }
        }
    }

    private static boolean nearArena(Dom d, Vec3 p) {
        double dx = p.x - d.arena.x, dz = p.z - d.arena.z;
        return p.y > d.arena.y - 40 && p.y < d.arena.y + 80 && dx * dx + dz * dz < (RD + 24) * (RD + 24);
    }

    // The domain this camera is inside of (open, its space round the camera).
    private static Dom insideNow(Vec3 camera) {
        for (Dom d : DOMS) {
            if (d.openAt >= 0 && d.goneAt < 0 && nearArena(d, camera)) {
                return d;
            }
        }
        return null;
    }

    public static boolean inside() {
        if (DOMS.isEmpty()) {
            return false;
        }
        return insideNow(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) != null;
    }

    private static Matrix4f localToRel(Vec3 origin, float yaw, Vec3 camera) {
        Vec3 o = origin.subtract(camera);
        return new Matrix4f().translation((float) o.x, (float) o.y, (float) o.z).rotateY(-yaw * Mth.DEG_TO_RAD);
    }

    // ---------- inside: the black, the station, the people ----------

    // Called at the end of the level before the effects (FxWorld): the station in place of the world.
    public static void backdrop(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || FxShaders.itaStation == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Dom d = insideNow(camera);
        if (d == null) {
            return;
        }
        double now = now();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        stepTrains(d, now);
        double reveal = reveal(d, now);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        modelView.mul(localToRel(d.arena, d.yaw, camera));
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            // all round it black
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Vec3 camL = toLocal(d, camera);
            float e = 300;
            float cx = (float) camL.x, cy = (float) camL.y, cz = (float) camL.z;
            float[][] corners = {{-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}, {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}};
            int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
            for (int[] f : faces) {
                for (int i : f) {
                    b.addVertex(cx + corners[i][0] * e, cy + corners[i][1] * e, cz + corners[i][2] * e).setColor(0, 0, 0, 255);
                }
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            if (reveal > 0.01) {
                upload();
                ShaderInstance sh = FxShaders.itaStation;
                sh.safeGetUniform("FogNear").set(FOG_NEAR);
                sh.safeGetUniform("FogFar").set(FOG_FAR);
                Matrix4f view = RenderSystem.getModelViewMatrix(), proj = RenderSystem.getProjectionMatrix();
                if (stationGpu != null) {
                    sh.safeGetUniform("Offset").set(0f, 0f, 0f);
                    sh.safeGetUniform("Reveal").set((float) reveal);
                    stationGpu.bind();
                    stationGpu.drawWithShader(view, proj, sh);
                }
                if (trainGpu != null && reveal >= RD) {
                    sh.safeGetUniform("Reveal").set((float) TRAIN_REVEAL);
                    for (int i = 0; i < 2; i++) {
                        int sd = i == 0 ? -1 : 1;
                        float bob = (float) (0.012 * Math.sin(now / 20 * 23 + sd));
                        sh.safeGetUniform("Offset").set(sd * 7f, bob, (float) d.trains[i][0]);
                        trainGpu.bind();
                        trainGpu.drawWithShader(view, proj, sh);
                    }
                }
                VertexBuffer.unbind();
            }
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
        people(event, d, camera, partial);
    }

    // How far the station shows: from where he stood out to the raio as it opens (1.5 s), back in as it closes (1 s).
    private static double reveal(Dom d, double now) {
        double r = easeOut3((now - d.openAt) / ItadoriDomain.REVEAL) * (RD + 1);
        if (d.closeAt >= 0) {
            r *= 1 - sm(CLOSE_CRACK, ItadoriDomain.CLOSE_TICKS - 2, now - d.closeAt);
        }
        return r;
    }

    // The trains: by, into the dark, and back after a while.
    private static void stepTrains(Dom d, double now) {
        double dt = d.last < 0 ? 0 : Math.max(0, Math.min(10, now - d.last)) / 20;
        d.last = now;
        if (reveal(d, now) < RD) {
            return;
        }
        for (double[] tr : d.trains) {
            if (tr[2] > 0) {
                tr[2] -= dt;
                continue;
            }
            tr[0] += tr[1] * dt;
            if ((tr[1] < 0 && tr[0] < -PARK - NC * SP) || (tr[1] > 0 && tr[0] > PARK)) {
                tr[0] = tr[1] < 0 ? PARK : -PARK - NC * SP;
                d.seed = d.seed * 6364136223846793005L + 1442695040888963407L;
                tr[2] = 3 + ((d.seed >>> 33) % 1000) / 1000.0 * 4;
            }
        }
    }

    private static Vec3 toLocal(Dom d, Vec3 world) {
        Vec3 p = world.subtract(d.arena);
        double a = d.yaw * Mth.DEG_TO_RAD;
        double cs = Math.cos(a), sn = Math.sin(a);
        return new Vec3(p.x * cs + p.z * sn, p.y, -p.x * sn + p.z * cs);
    }

    // The people inside, drawn again over the black (Gojo's and Sukuna's way), in the station's dim light.
    private static void people(RenderLevelStageEvent event, Dom d, Vec3 camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Entity viewer = event.getCamera().getEntity();
        boolean detached = event.getCamera().isDetached();
        // no world fog on them: the black all round made the far fog (and so the name tags and damage numbers drawn with
        // it) go black inside (Stark 2026-11-28)
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        net.minecraft.client.renderer.FogRenderer.setupNoFog();
        try {
            dispatcher.setRenderShadow(false);
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity) || entity.isInvisible() || (entity == viewer && !detached) || !nearArena(d, entity.position())) {
                    continue;
                }
                Vec3 rel = entity.getPosition(partial).subtract(camera);
                float yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
                dispatcher.render(entity, rel.x, rel.y, rel.z, yaw, partial, new PoseStack(), buffers, 0xF000F0);
            }
            RenderSystem.setShaderColor(0.82f, 0.85f, 0.9f, 1);
            buffers.endBatch();
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            dispatcher.setRenderShadow(true);
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // ---------- out in the world: the dome, and the break ----------

    // FxWorld calls this at the end of the level, matrices set to the camera.
    public static void render(Vec3 camera, float partial) {
        if (DOMS.isEmpty() && SHATTERS.isEmpty()) {
            return;
        }
        double now = now();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        for (Dom d : DOMS) {
            if (d.goneAt >= 0 || (d.openAt >= 0 && nearArena(d, camera))) {
                continue;
            }
            double age = now - d.start;
            double r = ItadoriDomain.waveRadius(age);
            if (r < 0.2) {
                continue;
            }
            // Closing: the dome is whole and dark from outside. While it waits on those running it is dimmer, so they
            // see their way out.
            double a = d.openAt >= 0 ? 0.94 : age < ItadoriDomain.SIGN + ItadoriDomain.WAVE ? 0.9 : 0.55;
            if (d.closeAt >= 0 && now - d.closeAt >= CLOSE_CRACK) {
                // shattered: the glass pieces (glass) take its place
                a = 0;
            }
            if (a <= 0.01) {
                continue;
            }
            modelView.pushMatrix();
            Vec3 o = d.center.subtract(camera);
            modelView.translate((float) o.x, (float) o.y, (float) o.z);
            RenderSystem.applyModelViewMatrix();
            try {
                dome(d.center.subtract(camera).scale(-1), (float) r, (float) a);
            } finally {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        }
        for (Shatter s : SHATTERS) {
            double k = (now - s.start) / 34;
            if (k < 0 || k >= 1) {
                continue;
            }
            modelView.pushMatrix();
            Vec3 o = s.center.subtract(camera);
            modelView.translate((float) o.x, (float) o.y, (float) o.z);
            RenderSystem.applyModelViewMatrix();
            try {
                shatter(camera.subtract(s.center), s.radius, k, s.start);
            } finally {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        }
    }

    // ---------- the end: it breaks like glass (DomainGlass, Gojo's) ----------

    // FxWorld calls this in the effects' main pass (FxDraw), so the cracks and the edges of the pieces glow.
    public static void glass(Vec3 camera) {
        if (GLASS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = now();
        for (Glass g : GLASS) {
            double t = now - g.start;
            if (t < 0) {
                continue;
            }
            // From up in its space (the station) it breaks round the arena; out in the world round where he stood.
            double ax = camera.x - g.arena.x, az = camera.z - g.arena.z;
            boolean up = camera.y > g.arena.y - 40 && ax * ax + az * az < (RD + 24) * (RD + 24);
            Vec3 hub = up ? g.arena : g.center;
            float r = (float) (up ? RD : g.radius);
            boolean inside = camera.distanceTo(hub) < r;
            if (g.pattern == null) {
                // It breaks first where this viewer sees it: in front of them from inside, on their side from outside.
                Vector3f impact;
                if (inside) {
                    Vector3f look = mc.gameRenderer.getMainCamera().getLookVector();
                    impact = new Vector3f(look.x, Math.max(look.y, 0) + 0.35f, look.z);
                } else {
                    impact = camera.subtract(hub).toVector3f();
                    impact.y = Math.max(impact.y, r * 0.45f);
                }
                if (impact.lengthSquared() < 1.0E-6f) {
                    impact.set(0, 1, 0);
                }
                g.pattern = new DomainGlass(g.caster * 31 + 4243, impact);
            }
            sounds(g, hub, t, camera.distanceTo(hub) < r + 60);
            Vector3f c = hub.subtract(camera).toVector3f();
            if (t < g.crack) {
                g.pattern.cracks(c, r, (float) t, (float) g.crack, inside);
                continue;
            }
            DomainGlass.Ground ground = (x, z) -> up ? (float) g.arena.y
                    : mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            g.pattern.shards(c, hub, r, (float) (t - g.crack), (float) GLASS_LIFE, inside, false, ground);
        }
    }

    // A crack of glass at each jolt of the cracks (the four of DomainGlass), then it shatters: the sound, the screen.
    private static void sounds(Glass g, Vec3 hub, double t, boolean near) {
        Minecraft mc = Minecraft.getInstance();
        while (g.played < 4 && t >= g.crack * g.played / 4.0) {
            if (near) {
                mc.level.playLocalSound(hub.x, hub.y, hub.z, net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS,
                        0.7f + 0.15f * g.played, 1.5f - 0.15f * g.played, false);
            }
            g.played++;
        }
        if (g.played == 4 && t >= g.crack) {
            g.played++;
            if (near) {
                Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
                mc.level.playLocalSound(eye.x, eye.y, eye.z, net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 2f, 0.5f, false);
                mc.level.playLocalSound(eye.x, eye.y, eye.z, net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.6f, 0.8f, false);
                boolean in = eye.distanceTo(hub) < g.radius;
                ScreenFx.fovKick(in ? 12 : 5, 16);
                ScreenFx.warp(hub, (float) g.radius * 1.4f, 0.4f, 30, 0, new Vec3(0, 1, 0), 0xFFFFFF, 0.05f, 16, -1, null, 0);
            }
        }
    }

    private static void premult() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    // The dome (the preview's DOME_F2): black, a pale blue rim where it turns away from the eye.
    private static void dome(Vec3 eye, float r, float alpha) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        premult();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int rings = 24, segs = 48;
        for (int i = 0; i < rings; i++) {
            double t0 = Math.PI * i / rings - Math.PI / 2, t1 = Math.PI * (i + 1) / rings - Math.PI / 2;
            for (int j = 0; j < segs; j++) {
                double p0 = Math.PI * 2 * j / segs, p1 = Math.PI * 2 * (j + 1) / segs;
                domeVert(b, eye, r, t0, p0, alpha);
                domeVert(b, eye, r, t1, p0, alpha);
                domeVert(b, eye, r, t1, p1, alpha);
                domeVert(b, eye, r, t0, p1, alpha);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static void domeVert(BufferBuilder b, Vec3 eye, float r, double lat, double lon, float alpha) {
        double nx = Math.cos(lat) * Math.cos(lon), ny = Math.sin(lat), nz = Math.cos(lat) * Math.sin(lon);
        double vx = eye.x - nx * r, vy = eye.y - ny * r, vz = eye.z - nz * r;
        double vl = Math.max(1.0E-4, Math.sqrt(vx * vx + vy * vy + vz * vz));
        double f = Math.abs((nx * vx + ny * vy + nz * vz) / vl);
        double rim = Math.pow(1 - f, 2.5);
        double a = (0.94 + 0.06 * rim) * alpha;
        float cr = (float) (0.35 * rim * 0.8 * a), cg = (float) (0.6 * rim * 0.8 * a), cb = (float) (1.0 * rim * 0.8 * a);
        b.addVertex((float) (nx * r), (float) (ny * r), (float) (nz * r)).setColor(cr, cg, cb, (float) a);
    }

    // The break: the dome in pieces, flying off, turning and falling, fading, a blue light at their edges.
    private static void shatter(Vec3 eye, double radius, double k, double seed) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        premult();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int rings = 10, segs = 20;
        float fade = (float) (1 - sm(0.35, 1, k));
        for (int i = 0; i < rings; i++) {
            double t0 = Math.PI * i / rings - Math.PI / 2, t1 = Math.PI * (i + 1) / rings - Math.PI / 2;
            for (int j = 0; j < segs; j++) {
                double p0 = Math.PI * 2 * j / segs, p1 = Math.PI * 2 * (j + 1) / segs;
                double h = hash(seed + i * 37 + j * 13);
                double tm = (t0 + t1) / 2, pm = (p0 + p1) / 2;
                Vec3 n = new Vec3(Math.cos(tm) * Math.cos(pm), Math.sin(tm), Math.cos(tm) * Math.sin(pm));
                // each piece goes out from the dome, falls, and shrinks a little
                double out = radius * (0.08 + 0.25 * h) * easeOut3(k * 1.4);
                Vec3 c = n.scale(radius + out).add(0, -radius * 0.35 * k * k, 0);
                double shrink = 1 - 0.5 * k;
                double spin = (h - 0.5) * 4 * k;
                float a = fade * (float) (0.75 + 0.25 * h);
                Vec3[] q = {sp(radius, t0, p0), sp(radius, t1, p0), sp(radius, t1, p1), sp(radius, t0, p1)};
                Vec3 mid = n.scale(radius);
                for (Vec3 v : q) {
                    Vec3 rel = v.subtract(mid).scale(shrink);
                    rel = rotate(rel, n, spin);
                    Vec3 p = c.add(rel);
                    double edge = Math.min(1, rel.length() / Math.max(0.01, radius * 0.16 * shrink));
                    float glow = (float) (0.55 * edge * (1 - k));
                    b.addVertex((float) p.x, (float) p.y, (float) p.z).setColor(0.35f * glow * a, 0.6f * glow * a, 1f * glow * a, a * 0.92f);
                }
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static Vec3 rotate(Vec3 v, Vec3 axis, double ang) {
        double c = Math.cos(ang), s = Math.sin(ang);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1 - c)));
    }

    private static Vec3 sp(double r, double lat, double lon) {
        return new Vec3(r * Math.cos(lat) * Math.cos(lon), r * Math.sin(lat), r * Math.cos(lat) * Math.sin(lon));
    }

    // ---------- the station, built once into the GPU ----------

    private static void upload() {
        if ((stationGpu != null && trainGpu != null) || !RenderSystem.isOnRenderThread()) {
            return;
        }
        if (stationGpu == null) {
            stationGpu = bake(station());
        }
        if (trainGpu == null) {
            trainGpu = bake(train());
        }
    }

    private static VertexBuffer bake(Geo g) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        g.emit(b);
        MeshData mesh = b.build();
        if (mesh == null) {
            return null;
        }
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(mesh);
        VertexBuffer.unbind();
        return vb;
    }

    // Boxes (and flat lit quads) in the domain's frame, their light baked in: the preview's dim hemisphere light and a
    // weak key light, the lamps and the signs unlit.
    private static final class Geo {
        final List<float[]> boxes = new ArrayList<>();
        final List<float[]> quads = new ArrayList<>();

        void box(float w, float h, float d, int color, float x, float y, float z) {
            boxes.add(new float[] {w, h, d, color, x, y, z, 0, 0});
        }

        void boxRz(float w, float h, float d, int color, float x, float y, float z, float rz) {
            boxes.add(new float[] {w, h, d, color, x, y, z, rz, 0});
        }

        void glow(float w, float h, float d, int color, float x, float y, float z) {
            boxes.add(new float[] {w, h, d, color, x, y, z, 0, 1});
        }

        // A flat quad facing +y (the floor tiles) at height y.
        void tile(float x0, float z0, float x1, float z1, float y, int color) {
            quads.add(new float[] {x0, z0, x1, z1, y, color});
        }

        void emit(BufferBuilder b) {
            for (float[] q : quads) {
                int c = (int) q[5];
                float s = shade(0, 1, 0);
                float r = ((c >> 16) & 255) / 255f * s, g = ((c >> 8) & 255) / 255f * s, bl = (c & 255) / 255f * s;
                b.addVertex(q[0], q[4], q[1]).setColor(r, g, bl, 1);
                b.addVertex(q[0], q[4], q[3]).setColor(r, g, bl, 1);
                b.addVertex(q[2], q[4], q[3]).setColor(r, g, bl, 1);
                b.addVertex(q[2], q[4], q[1]).setColor(r, g, bl, 1);
            }
            for (float[] bx : boxes) {
                emitBox(b, bx);
            }
        }

        private static float shade(float nx, float ny, float nz) {
            // the light from above (the preview's hemisphere, sky #dfe6ee) and a weak key from the front-left
            float lx = 0.37f, ly = 0.86f, lz = 0.35f;
            return 0.32f + 0.28f * (ny * 0.5f + 0.5f) + 0.2f * Math.max(0, nx * lx + ny * ly + nz * lz);
        }

        private static void emitBox(BufferBuilder b, float[] bx) {
            float hw = bx[0] / 2, hh = bx[1] / 2, hd = bx[2] / 2;
            int c = (int) bx[3];
            float cx = bx[4], cy = bx[5], cz = bx[6], rz = bx[7];
            boolean lit = bx[8] < 0.5f;
            float cr = ((c >> 16) & 255) / 255f, cg = ((c >> 8) & 255) / 255f, cb = (c & 255) / 255f;
            float cs = Mth.cos(rz), sn = Mth.sin(rz);
            // the 6 faces: normal, then 4 corners (counter-clockwise from outside)
            float[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            float[][][] f = {
                    {{hw, -hh, -hd}, {hw, hh, -hd}, {hw, hh, hd}, {hw, -hh, hd}},
                    {{-hw, -hh, hd}, {-hw, hh, hd}, {-hw, hh, -hd}, {-hw, -hh, -hd}},
                    {{-hw, hh, -hd}, {-hw, hh, hd}, {hw, hh, hd}, {hw, hh, -hd}},
                    {{-hw, -hh, hd}, {-hw, -hh, -hd}, {hw, -hh, -hd}, {hw, -hh, hd}},
                    {{hw, -hh, hd}, {hw, hh, hd}, {-hw, hh, hd}, {-hw, -hh, hd}},
                    {{-hw, -hh, -hd}, {-hw, hh, -hd}, {hw, hh, -hd}, {hw, -hh, -hd}}};
            for (int i = 0; i < 6; i++) {
                float nx = n[i][0] * cs - n[i][1] * sn, ny = n[i][0] * sn + n[i][1] * cs, nz = n[i][2];
                float s = lit ? shade(nx, ny, nz) : 1;
                for (float[] p : f[i]) {
                    float x = p[0] * cs - p[1] * sn, y = p[0] * sn + p[1] * cs;
                    b.addVertex(cx + x, cy + y, cz + p[2]).setColor(cr * s, cg * s, cb * s, 1);
                }
            }
        }
    }

    private static Geo station() {
        Geo g = new Geo();
        int edge = 0xB0AEA6, yel = 0xE8C020, steel = 0x5A5E62, roof = 0x3A3C40, pill = 0x8C8D8A, lamp = 0xDFE8F4, grav = 0x3B3833, rail = 0x8A8580,
                slp = 0x4A3A2C, bench = 0x6A4A32, board = 0x0C0D0E;
        // the platform (its top at 0): grey tiles, one block each, a little lighter or darker each (the preview's tiles)
        for (int i = -5; i < 5; i++) {
            for (int j = (int) (-L / 2); j < (int) (L / 2); j++) {
                int v = 118 + Math.floorMod(i * 7 + j * 13, 5) * 5;
                int col = (v << 16) | (v << 8) | (v - 6);
                g.tile(i, j, i + 1, j + 1, 0.001f, col);
            }
        }
        for (float z = -L / 2 + 4; z < L / 2; z += 8) {
            // the platform's body under the tiles, the yellow lines, the gravel, the rails, the sleepers
            g.box(10, 1.2f, 8, edge, 0, -0.6f, z);
            g.box(0.5f, 0.02f, 8, yel, -4.4f, 0.011f, z);
            g.box(0.5f, 0.02f, 8, yel, 4.4f, 0.011f, z);
            for (int sd = -1; sd <= 1; sd += 2) {
                g.box(8, 0.1f, 8, grav, sd * 8, -1.25f, z);
                g.box(0.1f, 0.14f, 8, rail, sd * 7 - 0.8f, -1.05f, z);
                g.box(0.1f, 0.14f, 8, rail, sd * 7 + 0.8f, -1.05f, z);
                for (int k = 0; k < 10; k++) {
                    g.box(2.3f, 0.08f, 0.25f, slp, sd * 7, -1.16f, z - 3.6f + k * 0.8f);
                }
            }
        }
        int n = 0;
        for (float z = -L / 2 + 4; z <= L / 2 - 4; z += 8, n++) {
            // pillars, the steel truss, the roof, the lamps
            g.box(0.45f, 7, 0.45f, pill, -3, 3.5f, z);
            g.box(0.45f, 7, 0.45f, pill, 3, 3.5f, z);
            g.box(7.4f, 0.3f, 0.3f, steel, 0, 6.7f, z);
            g.boxRz(0.14f, 2.2f, 0.14f, steel, -2.2f, 5.9f, z, -0.8f);
            g.boxRz(0.14f, 2.2f, 0.14f, steel, 2.2f, 5.9f, z, 0.8f);
            g.box(11, 0.35f, 8, roof, 0, 7.3f, z);
            for (int x = -3; x <= 3; x += 3) {
                g.box(0.22f, 0.25f, 8, steel, x, 6.95f, z);
            }
            g.glow(0.28f, 0.08f, 5.5f, lamp, -1.4f, 6.75f, z + 4);
            g.glow(0.28f, 0.08f, 5.5f, lamp, 1.4f, 6.75f, z + 4);
            if (n % 2 == 0) {
                // a timetable: black, amber and green rows lit on both faces
                g.glow(3.4f, 0.85f, 0.16f, board, 0, 5.2f, z + 4);
                g.box(0.05f, 1.3f, 0.05f, steel, -1.2f, 6.2f, z + 4);
                g.box(0.05f, 1.3f, 0.05f, steel, 1.2f, 6.2f, z + 4);
                float[][] rows = {{14, 14, 60, 10, 0}, {84, 14, 40, 10, 0}, {140, 14, 90, 10, 0}, {14, 38, 110, 9, 1}, {140, 38, 70, 9, 1}};
                for (float[] r : rows) {
                    float w = r[2] / 256 * 3.4f, h = r[3] / 64 * 0.85f;
                    float x = (r[0] + r[2] / 2) / 256 * 3.4f - 1.7f, y = 5.2f + 0.425f - (r[1] + r[3] / 2) / 64 * 0.85f;
                    int col = r[4] < 0.5f ? 0xFFB347 : 0x7DFFB0;
                    g.glow(w, h, 0.17f, col, x, y, z + 4);
                }
            } else {
                for (int sd = -1; sd <= 1; sd += 2) {
                    g.box(0.6f, 0.1f, 2.2f, bench, sd * 1.9f, 0.48f, z + 4);
                    g.box(0.1f, 0.5f, 2.2f, bench, sd * 2.15f, 0.78f, z + 4);
                    g.box(0.5f, 0.45f, 0.1f, steel, sd * 1.9f, 0.22f, z + 3.1f);
                    g.box(0.5f, 0.45f, 0.1f, steel, sd * 1.9f, 0.22f, z + 4.9f);
                }
            }
        }
        return g;
    }

    // A Japanese suburban train of NC cars along +z from 0 (the preview's makeCar): stainless steel with green stripes,
    // four doors a side, lit windows, a black cab with headlights and a sign, pantographs, roof units, bogies, the bellows.
    private static Geo train() {
        Geo g = new Geo();
        int inox = 0x9EA4A8, roofC = 0x6F7478, dark = 0x1C1D20, green = 0x2F9E4A, lime = 0x9BD23C, door = 0x8A9094, glass = 0x6F8592, glassD = 0x0B0F14,
                frame = 0x2A2D31, head = 0xFFF6D8, tail = 0xFF2A20, sign = 0xFFA020, wheel = 0x3A3A3A;
        for (int k = 0; k < NC; k++) {
            float oz = k * SP;
            boolean front = k == 0, back = k == NC - 1;
            g.box(CW, CH - 0.5f, CL, inox, 0, CY + 0.5f + (CH - 0.5f) / 2, oz);
            g.box(CW - 0.3f, 0.3f, CL - 0.2f, roofC, 0, CY + CH + 0.13f, oz);
            g.box(CW - 0.9f, 0.2f, CL - 0.6f, roofC, 0, CY + CH + 0.35f, oz);
            g.box(1.6f, 0.45f, 3.2f, roofC, 0, CY + CH + 0.6f, oz - 5);
            g.box(1.6f, 0.45f, 3.2f, roofC, 0, CY + CH + 0.6f, oz + 5);
            g.box(CW - 0.6f, 0.6f, CL - 3, dark, 0, CY + 0.2f, oz);
            for (int e2 = -1; e2 <= 1; e2 += 2) {
                float bz = oz + e2 * 7.2f;
                g.box(2.6f, 0.5f, 3, dark, 0, CY - 0.05f, bz);
                for (int s = -1; s <= 1; s += 2) {
                    for (int o = -1; o <= 1; o += 2) {
                        g.box(0.14f, 0.84f, 0.84f, wheel, s * 0.8f, CY - 0.25f, bz + o * 0.9f);
                    }
                }
            }
            for (int sd = -1; sd <= 1; sd += 2) {
                float x = sd * (CW / 2 + 0.01f);
                g.box(0.03f, 0.22f, CL, green, x, CY + 1.35f, oz);
                g.box(0.03f, 0.08f, CL, lime, x, CY + 1.55f, oz);
                g.box(0.03f, 0.16f, CL, green, x, CY + CH - 0.25f, oz);
                float[] doors = {-7.6f, -2.55f, 2.55f, 7.6f};
                for (float dz : doors) {
                    g.box(0.05f, 2.3f, 1.5f, door, x, CY + 0.5f + 1.15f, oz + dz);
                    g.box(0.06f, 2.3f, 0.04f, frame, x, CY + 0.5f + 1.15f, oz + dz);
                    g.glow(0.14f, 0.95f, 0.5f, glass, x, CY + 2.25f, oz + dz - 0.38f);
                    g.glow(0.14f, 0.95f, 0.5f, glass, x, CY + 2.25f, oz + dz + 0.38f);
                }
                for (int w = 0; w < doors.length + 1; w++) {
                    float z0 = w == 0 ? -CL / 2 + 0.6f : doors[w - 1] + 0.95f, z1 = w == doors.length ? CL / 2 - 0.6f : doors[w] - 0.95f;
                    int cnt = Math.max(1, Math.round((z1 - z0) / 1.5f));
                    for (int q = 0; q < cnt; q++) {
                        float zc = z0 + (q + 0.5f) * (z1 - z0) / cnt, ww = (z1 - z0) / cnt - 0.18f;
                        g.glow(0.16f, 0.98f, ww, glass, x + sd * 0.02f, CY + 2.35f, oz + zc);
                    }
                }
            }
            if (front || back) {
                int e2 = front ? -1 : 1;
                float z = oz + e2 * (CL / 2 + 0.01f);
                g.glow(CW - 0.1f, 2.0f, 0.06f, glassD, 0, CY + 2.6f, z);
                g.box(CW, 0.5f, 0.08f, green, 0, CY + 1.25f, z);
                g.glow(1.6f, 0.28f, 0.08f, sign, 0, CY + 3.85f, z + e2 * 0.01f);
                for (int sd = -1; sd <= 1; sd += 2) {
                    g.glow(0.42f, 0.22f, 0.08f, front ? head : tail, sd * 1.15f, CY + 1.0f, z + e2 * 0.02f);
                }
            } else {
                g.box(2.2f, 3.6f, 0.6f, dark, 0, CY + 2.3f, oz - (CL / 2 + 0.25f));
                g.box(2.2f, 3.6f, 0.6f, dark, 0, CY + 2.3f, oz + (CL / 2 + 0.25f));
            }
            if (k == 1 || k == 4) {
                // the pantograph: a diamond of thin bars, its shoe on top (across the car)
                float py = CY + CH + 0.45f;
                g.boxRz(0.07f, 1.3f, 0.07f, dark, -0.45f, py + 0.55f, oz, 0.7f);
                g.boxRz(0.07f, 1.3f, 0.07f, dark, 0.45f, py + 0.55f, oz, -0.7f);
                g.boxRz(0.07f, 1.3f, 0.07f, dark, -0.45f, py + 1.45f, oz, -0.7f);
                g.boxRz(0.07f, 1.3f, 0.07f, dark, 0.45f, py + 1.45f, oz, 0.7f);
                g.box(1.8f, 0.07f, 0.3f, dark, 0, py + 1.95f, oz);
            }
        }
        return g;
    }

    // ---------- helpers ----------

    private static double easeOut3(double x) {
        double t = Math.max(0, Math.min(1, x));
        return 1 - Math.pow(1 - t, 3);
    }

    private static double sm(double a, double b, double x) {
        double u = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return u * u * (3 - 2 * u);
    }

    private static double hash(double n) {
        double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return s - Math.floor(s);
    }
}
