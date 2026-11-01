package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.client.cutscene.CutscenePlayer;
import com.starkinhon.battlegroundsmod.skill.DominioSpec;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Gojo's Expansão de Domínio: Vazio Infinito (DominioSpec, gojo.dominio.json), seen by everyone near it. In order:
// - the sign: a white glow hugging Gojo's body and a star of light at his hand; then the shockwave, a ring of bent air
//   and light running out of him that stops every attack in flight in the air (AttackClock asks heldAt);
// - the white: a sphere of soft cloudy light swallowing everything within the radius, the ground too (its lower half is
//   underground, it shows wherever the ground is gone); whoever it reaches sees only white;
// - the tunnel (only inside): black ink eats the white, then streaks of violet, magenta, red and blue light rush out of
//   a far dark point past the camera, faster and faster, the screen stretching with the speed;
// - the void (only inside): no walls and no floor, endless depth: stars and a blue haze on every side, far behind Gojo a
//   black hole turning in rings of blue light with black ink and sparks pulled into it, white ink drifting by, and
//   a ring of water under everyone every 2 seconds. From outside, all this time, a smooth black ball;
// - the end: cracks of light run all over the sphere, then it shatters like glowing glass, black and white shards
//   flying away, and the world is back.
// Those caught (everyone but Gojo) also get, on their own screen only, the information pouring in (DomainScreen): lines
// of light and letters rushing into their mind, flashes of nature and senses, a slow zoom on the black hole, a heart
// slowing down and a high whine; their bodies show grey, light threads and glyphs flying into their heads.
// The whole skill is seen in game, from everyone's own view, with no cutscene (Gojo sees his white dome growing out
// towards his target); in the tunnel the two of them stand still face to face, 20 blocks apart. A kill inside turns the screen to red and white ink (fx_impact mode 5).
// Everything is a function of the time since it started (no state but the start), smooth at any frame rate.
public final class DomainScene {
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] BLACK = {0.01f, 0.01f, 0.015f};
    private static final float[] MILK = FxDraw.rgb(0xE9EDF6);
    private static final float[] PURPLE = FxDraw.rgb(0xA040FF);
    private static final float[] MAGENTA = FxDraw.rgb(0xFF3CDC);
    private static final float[] RED = FxDraw.rgb(0xFF2058);
    private static final float[] BLUE = FxDraw.rgb(0x4C5CFF);
    private static final float[] PINK = FxDraw.rgb(0xFFD6FF);
    private static final float[] SILVER = FxDraw.rgb(0xCFE8FF);
    private static final int VIEW = 192;
    // The cracks run for this long before it shatters.
    private static final int CRACK_TICKS = 14;
    // The shards: the sphere cut on a grid of LAT x LON cells, two triangles each.
    private static final int LAT = 9;
    private static final int LON = 18;

    enum Stage {
        NONE, WHITE, TUNNEL, VOID
    }

    public record Outline(LivingEntity entity, float[] rgb, float strength, double thickness) {
    }

    private record Ripple(Vec3 pos, double born, float size) {
    }

    static final class Domain {
        final int caster;
        final Vec3 center;
        final DominioSpec spec;
        final double start;
        // Where Gojo faced when he started: the black hole is far behind him, the tunnel runs towards it.
        Vec3 forward;
        final Set<String> fired = new HashSet<>();
        double closeStart = -1;
        double nextBeat;
        double nextWhine;
        double nextStutter;

        Domain(int caster, Vec3 center, DominioSpec spec, double start, Vec3 forward) {
            this.caster = caster;
            this.center = center;
            this.spec = spec;
            this.start = start;
            this.forward = forward;
        }

        double radius() {
            return spec.radius();
        }

        // When the cracking starts, in ticks since the start (early if Gojo fell).
        double closeAt() {
            return closeStart >= 0 ? Math.min(spec.closeTick(), closeStart - start) : spec.closeTick();
        }

        double shatterAt() {
            return closeAt() + Math.min(CRACK_TICKS, spec.closeTicks() / 2.0);
        }

        double goneAt() {
            return closeAt() + spec.closeTicks();
        }

        boolean once(String key) {
            return fired.add(key);
        }

        boolean contains(Vec3 pos) {
            return pos.distanceToSqr(center) < radius() * radius();
        }

        // What a camera at pos sees at time t.
        Stage stage(Vec3 pos, double t) {
            if (t < spec.whiteTick() || t >= shatterAt()) {
                return Stage.NONE;
            }
            double dist = pos.distanceTo(center);
            if (t < spec.tunnelTick() || closeAt() < spec.tunnelTick()) {
                return dist < spec.whiteRadius(t) ? Stage.WHITE : Stage.NONE;
            }
            if (dist >= radius()) {
                return Stage.NONE;
            }
            return t < spec.voidTick() ? Stage.TUNNEL : Stage.VOID;
        }

        // The black hole's direction: behind Gojo, a little above the horizon.
        Vector3f holeDir() {
            float lift = (float) Math.toRadians(spec.visual().buracoAltura());
            return new Vector3f((float) -forward.x * Mth.cos(lift), Mth.sin(lift), (float) -forward.z * Mth.cos(lift)).normalize();
        }

        // The tunnel runs level, towards the black hole.
        Vector3f tunnelAxis() {
            return new Vector3f((float) -forward.x, 0, (float) -forward.z).normalize();
        }
    }

    private static final List<Domain> DOMAINS = new ArrayList<>();
    private static final List<Ripple> RIPPLES = new ArrayList<>();
    // When each body inside the void last left a ripple.
    private static final Map<Integer, Long> STILL = new HashMap<>();
    private static final int RIPPLE_EVERY = 40;
    // How long the white takes to tear open into the tunnel, in ticks.
    private static final int BURST_TICKS = 16;
    private static float lastPartial;
    // The shards' corners on a unit sphere, jittered (made once).
    private static Vector3f[][] grid;

    private DomainScene() {
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Events from the server.

    public static void event(int casterId, Vec3 center, boolean awakened, int mode, int other) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double now = ScreenFx.now();
        if (mode == 1) {
            for (Domain d : DOMAINS) {
                if (d.caster == casterId && d.closeStart < 0) {
                    d.closeStart = now;
                }
            }
            if (CutscenePlayer.active() && DominioSpec.SKILL.equals(CutscenePlayer.skill())) {
                CutscenePlayer.stop();
            }
            return;
        }
        if (mode == 3) {
            tunnel(casterId, other);
            return;
        }
        if (mode == 2) {
            kill(other, center);
            return;
        }
        DOMAINS.removeIf(d -> d.caster == casterId);
        Entity caster = minecraft.level.getEntity(casterId);
        Vec3 forward = Vec3.directionFromRotation(0, caster != null ? caster.getYRot() : 0);
        Domain d = new Domain(casterId, center, DominioSpec.get(awakened), now, forward);
        d.nextBeat = d.spec.tunnelTick() + 10;
        d.nextWhine = d.spec.voidTick() + 20;
        d.nextStutter = d.spec.tunnelTick() + 50;
        DOMAINS.add(d);
    }

    // The tunnel starts: Gojo and his target stand face to face, 20 blocks apart (the server put them there), and the
    // black hole is behind him as seen from the target. No cutscene: everyone keeps their own view.
    private static void tunnel(int casterId, int targetId) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity caster = minecraft.level.getEntity(casterId);
        Entity target = targetId >= 0 ? minecraft.level.getEntity(targetId) : null;
        for (Domain d : DOMAINS) {
            if (d.caster == casterId && caster != null && target != null) {
                Vec3 to = target.position().subtract(caster.position());
                if (to.horizontalDistanceSqr() > 1.0E-4) {
                    d.forward = new Vec3(to.x, 0, to.z).normalize();
                }
            }
        }
    }

    // The local player is inside an open domain that is not their own: no other skill's cutscene plays there.
    public static boolean blocksCutscenes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (DOMAINS.isEmpty() || minecraft.player == null) {
            return false;
        }
        for (Domain d : DOMAINS) {
            double t = time(d, 0);
            if (t >= d.spec.whiteTick() && t < d.goneAt() && d.contains(minecraft.player.position())) {
                return true;
            }
        }
        return false;
    }

    // The killing blow inside the domain: a white blink, then the frame turns to sumi-e, red and white ink.
    private static void kill(int victimId, Vec3 center) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity victim = minecraft.level.getEntity(victimId);
        Vec3 at = victim != null ? victim.position().add(0, victim.getBbHeight() * 0.6, 0) : center.add(0, 1, 0);
        ScreenFx.flash(0xFFFFFF, 0.95f, 3);
        ScreenFx.impactFrame(new int[] {5}, 34, 0xC8102E, at, 2);
        ScreenFx.fovKick(-10, 20);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 0.5f, 1f));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_CRIT, 0.6f, 1f));
    }

    public static void clear() {
        DOMAINS.clear();
        RIPPLES.clear();
        STILL.clear();
    }

    public static boolean active() {
        return !DOMAINS.isEmpty();
    }

    private static double time(Domain d, float partial) {
        return ScreenFx.now() + partial - d.start;
    }

    private static Vec3 cameraPos() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    // The domain whose inside the camera sees right now (tunnel or void), or null.
    private static Domain around(Vec3 camera, float partial) {
        for (Domain d : DOMAINS) {
            Stage s = d.stage(camera, time(d, partial));
            if (s == Stage.TUNNEL || s == Stage.VOID) {
                return d;
            }
        }
        return null;
    }

    // The world is replaced by the tunnel or the void (FxWorld draws the backdrop first).
    public static boolean inVoid() {
        if (DOMAINS.isEmpty()) {
            return false;
        }
        Vec3 camera = cameraPos();
        return around(camera, lastPartial) != null || flooded(camera, lastPartial) != null;
    }

    // The domain whose white has flooded the camera's view (it is all white but the people), or null.
    private static Domain flooded(Vec3 camera, float partial) {
        for (Domain d : DOMAINS) {
            double t = time(d, partial);
            if (d.stage(camera, t) == Stage.WHITE && fill(d, t) > 0) {
                return d;
            }
        }
        return null;
    }

    // How white the world is for someone the white has reached: after the white has poured over the ground and fallen
    // from the sky for a while, what is left of the world fades out behind it, until only the people show.
    private static float fill(Domain d, double t) {
        double from = d.spec.whiteTick() + d.spec.fases().branco() * 0.45;
        double to = d.spec.whiteTick() + d.spec.fases().branco() * 0.82;
        float k = (float) Mth.clamp((t - from) / Math.max(1, to - from), 0, 1);
        return k * k * (3 - 2 * k);
    }

    // ---- the shockwave holding attacks in the air ----------------------------------------------------------------------

    // Some shockwave is holding attacks right now.
    static boolean holding() {
        for (Domain d : DOMAINS) {
            double t = time(d, lastPartial);
            if (t >= d.spec.ondaTick() && t < d.spec.whiteTick() + 3 && t < d.closeAt()) {
                return true;
            }
        }
        return false;
    }

    // pos is inside a shockwave that holds attacks (the same rule as the server's).
    static boolean heldAt(Vec3 pos) {
        for (Domain d : DOMAINS) {
            double t = time(d, lastPartial);
            if (t >= d.spec.ondaTick() && t < d.spec.whiteTick() + 3 && t < d.closeAt()
                    && pos.distanceTo(d.center) <= d.spec.waveRadius(Math.min(t, d.spec.whiteTick())) + 1) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Once a tick: the moments that hit the screen, the sustained speed and zoom, the victim's sounds, the ripples.

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }
        DOMAINS.removeIf(d -> time(d, 0) > d.goneAt() + 1 || minecraft.player.position().distanceToSqr(d.center) > VIEW * VIEW * 4);
        RIPPLES.removeIf(r -> ScreenFx.now() - r.born() > RIPPLE_EVERY);
        if (DOMAINS.isEmpty()) {
            STILL.clear();
            return;
        }
        Vec3 camera = cameraPos();
        for (Domain d : DOMAINS) {
            double t = time(d, 0);
            DominioSpec spec = d.spec;
            boolean caster = minecraft.player.getId() == d.caster;
            double dist = camera.distanceTo(d.center);
            Stage stage = d.stage(camera, t);
            boolean victim = !caster && d.contains(minecraft.player.getEyePosition()) && t >= spec.tunnelTick() && t < d.shatterAt();

            if (t >= spec.ondaTick() && d.once("onda") && dist < 110) {
                ScreenFx.impactFrame(new int[] {4, 3, 4}, 2, 0x9FE8FF, d.center.add(0, 1.4, 0), 0);
                ScreenFx.warp(d.center.add(0, 1.2, 0), (float) spec.dominio().raioChoque(), 0.55f, 26, 0, new Vec3(0, 1, 0), 0xCFF4FF, 0.08f, 20, -1,
                        null, 0);
                ScreenFx.fovKick(caster ? -4 : 9, 14);
            }
            if (t >= spec.whiteTick() && d.once("white") && dist >= d.radius() && dist < 160 && d.closeAt() > spec.whiteTick()) {
                ScreenFx.flash(0xFFFFFF, 0.55f, 16);
            }
            if (stage == Stage.TUNNEL) {
                double u = Mth.clamp((t - spec.tunnelTick()) / Math.max(1, spec.fases().tunel()), 0, 1);
                Vector3f a = d.tunnelAxis();
                ScreenFx.sustainBlur(camera.add(a.x * 120, 0, a.z * 120), (float) (0.25 + 1.7 * u * u));
                ScreenFx.sustainFov((float) (4 + 30 * u * u));
                if (d.once("tunnelIn")) {
                    ScreenFx.fovKick(-14, 10);
                    // The screen shakes as the white tears open.
                    com.starkinhon.battlegroundsmod.client.ScreenShake.start(2.2f, 22);
                }
            }
            if (stage == Stage.VOID && d.once("apex")) {
                ScreenFx.flash(0xFFFFFF, 1, 16);
                ScreenFx.impactFrame(new int[] {2, 4}, 2, 0xB070FF, camera.add(d.holeDir().x * 50, d.holeDir().y * 50, d.holeDir().z * 50), 0);
                ScreenFx.fovKick(-28, 20);
            }
            if (victim && t >= spec.voidTick()) {
                // Frozen, the view sinks slowly into the black hole.
                double u = Mth.clamp((t - spec.voidTick()) / Math.max(1, spec.fases().vazio()), 0, 1);
                ScreenFx.sustainFov((float) (-38 * (1 - Math.pow(1 - u, 2))));
            }
            if (victim) {
                victimSounds(d, t, minecraft);
            }
            if (t >= d.closeAt() && d.once("crack") && dist < d.radius() + 30) {
                ScreenFx.impactFrame(new int[] {3}, 2, 0xFFFFFF, d.center.add(0, 2, 0), 0);
            }
            if (t >= d.shatterAt() && d.once("shatter") && dist < d.radius() + 60) {
                ScreenFx.flash(0xFFFFFF, dist < d.radius() ? 0.9f : 0.5f, 12);
                ScreenFx.fovKick(dist < d.radius() ? 12 : 5, 16);
                ScreenFx.warp(d.center, (float) d.radius() * 1.4f, 0.4f, 30, 0, new Vec3(0, 1, 0), 0xFFFFFF, 0.05f, 16, -1, null, 0);
            }
            if (t >= spec.voidTick() && t < d.shatterAt()) {
                steps(d, minecraft);
            }
        }
    }

    // On the screen of those caught: a heart slowing down, a high whine now and then, and a frame that stutters.
    private static void victimSounds(Domain d, double t, Minecraft minecraft) {
        double total = d.spec.closeTick() - d.spec.tunnelTick();
        double u = Mth.clamp((t - d.spec.tunnelTick()) / Math.max(1, total), 0, 1);
        var sounds = minecraft.getSoundManager();
        if (t >= d.nextBeat) {
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, (float) (1.0 - 0.35 * u), (float) (0.9 - 0.3 * u)));
            d.nextBeat = t + 14 + 34 * u;
        }
        if (t >= d.nextWhine && t >= d.spec.voidTick()) {
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.BEACON_AMBIENT, 2.0f, (float) (0.35 + 0.4 * u)));
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, (float) (0.15 + 0.3 * u)));
            d.nextWhine = t + 50;
        }
        if (t >= d.nextStutter) {
            if (!FxConfig.on(FxConfig.REDUCED_FLASH)) {
                ScreenFx.impactFrame(new int[] {2}, 1, 0x7FD8FF, d.center.add(0, 1, 0), 0);
            }
            d.nextStutter = t + 40 + 40 * FxWorld.hash((int) t, 3, 1);
        }
    }

    // One ring of water every 2 seconds under each body in the void, wherever it is.
    private static void steps(Domain d, Minecraft minecraft) {
        long now = ScreenFx.now();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || !d.contains(entity.position())) {
                continue;
            }
            Long last = STILL.get(entity.getId());
            if (last == null || now - last >= RIPPLE_EVERY) {
                ripple(entity.position(), 1);
                STILL.put(entity.getId(), now);
            }
        }
    }

    private static void ripple(Vec3 pos, float size) {
        RIPPLES.add(new Ripple(pos, ScreenFx.now(), size));
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Outlines drawn by Aura's mask pass: none any more (no glow on Gojo, none on those caught).

    static List<Outline> outlines(float partial) {
        return List.of();
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Inside: the tunnel or the void covers the world before anything else is drawn, and the people inside are drawn on
    // it again (those caught in grey), with the glyphs flying into their heads.

    static void backdrop(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        Domain d = around(camera, partial);
        if (minecraft.level == null) {
            return;
        }
        if (d == null) {
            Domain white = flooded(camera, partial);
            if (white != null) {
                whiteBackdrop(event, white, time(white, partial), partial);
            }
            return;
        }
        double t = time(d, partial);
        if (d.stage(camera, t) == Stage.TUNNEL) {
            sky(0x0B0016, 0x26073D);
        } else {
            sky(0x010309, 0x0B1A3A);
        }
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Entity viewer = event.getCamera().getEntity();
        boolean detached = event.getCamera().isDetached();
        List<LivingEntity> caught = new ArrayList<>();
        try {
            dispatcher.setRenderShadow(false);
            for (int pass = 0; pass < 2; pass++) {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (!(entity instanceof LivingEntity living) || entity.isInvisible() || (entity == viewer && !detached)
                            || !d.contains(entity.getPosition(partial).add(0, entity.getBbHeight() / 2, 0))) {
                        continue;
                    }
                    boolean isVictim = entity.getId() != d.caster;
                    if (isVictim != (pass == 1)) {
                        continue;
                    }
                    if (isVictim) {
                        caught.add(living);
                    }
                    Vec3 rel = entity.getPosition(partial).subtract(camera);
                    float yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
                    // Fully lit: there is no sky or sun here, the void glows on them.
                    dispatcher.render(entity, rel.x, rel.y, rel.z, yaw, partial, new PoseStack(), buffers, 0xF000F0);
                }
                if (pass == 1) {
                    // Those caught: drained of color, cold and grey.
                    RenderSystem.setShaderColor(0.58f, 0.6f, 0.66f, 1);
                }
                buffers.endBatch();
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
            glyphs(event.getCamera(), caught, camera, t, partial, buffers);
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            dispatcher.setRenderShadow(true);
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // The white drawn over the world (as much as fill says), and the people the white has reached drawn again on top
    // of it, still hidden by whatever stands in front of them (the depth of the world is kept).
    private static void whiteBackdrop(RenderLevelStageEvent event, Domain d, double t, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        sky(0xFFFFFF, 0xF4F6FB, fill(d, t));
        double reach = d.spec.whiteRadius(t);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Entity viewer = event.getCamera().getEntity();
        boolean detached = event.getCamera().isDetached();
        try {
            dispatcher.setRenderShadow(false);
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity) || entity.isInvisible() || (entity == viewer && !detached)
                        || entity.getPosition(partial).distanceTo(d.center) > reach) {
                    continue;
                }
                Vec3 rel = entity.getPosition(partial).subtract(camera);
                float yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
                dispatcher.render(entity, rel.x, rel.y, rel.z, yaw, partial, new PoseStack(), buffers, 0xF000F0);
            }
            buffers.endBatch();
        } finally {
            dispatcher.setRenderShadow(true);
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static final String GLYPHS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#%&$@?!+=<>/*";

    // Letters and numbers in Minecraft's font rushing into the heads of those caught: infinite information.
    private static void glyphs(Camera cam, List<LivingEntity> caught, Vec3 camera, double t, float partial, MultiBufferSource.BufferSource buffers) {
        if (caught.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        for (LivingEntity entity : caught) {
            Vec3 head = entity.getPosition(partial).add(0, entity.getEyeHeight(), 0).subtract(camera);
            int seed = 7001 + entity.getId() * 31;
            for (int k = 0; k < 26; k++) {
                float life = 7 + 6 * FxWorld.hash(seed, k, 1);
                double cycle = (t + FxWorld.hash(seed, k, 2) * life) / life;
                int round = (int) Math.floor(cycle);
                float u = (float) (cycle - round);
                Vector3f dir = FxWorld.sphere(seed + round * 17, k);
                float out = 2.8f * (1 - u) * (1 - u) + 0.15f;
                Vec3 at = head.add(dir.x * out, dir.y * out * 0.8, dir.z * out);
                char c = GLYPHS.charAt((int) (FxWorld.hash(seed + round, k, 3) * GLYPHS.length()) % GLYPHS.length());
                int alpha = (int) (255 * Math.min(1, u * 4) * (1 - u * 0.5f));
                if (alpha < 12) {
                    continue;
                }
                int color = alpha << 24 | (k % 3 == 0 ? 0x9FE8FF : 0xFFFFFF);
                PoseStack pose = new PoseStack();
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(cam.rotation());
                float s = 0.022f * (0.6f + 0.6f * (1 - u));
                pose.scale(s, -s, s);
                String text = String.valueOf(c);
                font.drawInBatch(text, -font.width(text) / 2f, -4, color, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }
        }
        buffers.endBatch();
    }

    // ------------------------------------------------------------------------------------------------------------------
    // With the other world effects (FxDraw is begun).

    static void render(Vec3 camera, double now, float partial) {
        lastPartial = partial;
        for (Domain d : DOMAINS) {
            double t = time(d, partial);
            if (t < 0) {
                continue;
            }
            Stage stage = d.stage(camera, t);
            sign(d, camera, t, partial);
            if (stage == Stage.TUNNEL) {
                tunnel(d, t, (float) now);
                people(d, camera, (float) now, partial, 1);
            } else if (stage == Stage.VOID) {
                drawVoid(d, camera, t, (float) now, partial);
            } else {
                shell(d, camera, t, (float) now);
            }
            if (t >= d.closeAt() && t < d.shatterAt()) {
                cracks(d, camera, t);
            }
            if (t >= d.shatterAt()) {
                shards(d, camera, t);
            }
        }
    }

    // After the glow, in the alpha layer: the black things that the light must not wash out (the black hole, the ink
    // pulled into it, the tunnel's dark heart).
    static void overlay(Vec3 camera, double now, float partial) {
        Domain d = around(camera, partial);
        if (d == null) {
            return;
        }
        double t = time(d, partial);
        if (d.stage(camera, t) == Stage.TUNNEL) {
            Vector3f a = d.tunnelAxis();
            float in = (float) Mth.clamp((t - d.spec.tunnelTick()) / 10.0, 0, 1);
            FxDraw.billboard(new Vector3f(a).mul(390), 60, 0, FxDraw.DISC, 0.9f, BLACK, 0.85f * in, false);
            return;
        }
        float in = (float) Mth.clamp((t - d.spec.voidTick()) / 8.0, 0, 1);
        Vector3f dir = d.holeDir();
        float size = (float) d.spec.visual().buracoTamanho();
        Vector3f eye = new Vector3f(dir).mul(95);
        // The hole: pure black with a hair of soft edge.
        FxDraw.billboard(eye, size * 0.27f, 0, FxDraw.DISC, 0.12f, BLACK, in, false);
        // Blots of black ink orbiting and falling into it.
        Vector3f x = new Vector3f(dir).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f y = new Vector3f(x).cross(dir).normalize();
        for (int k = 0; k < 14; k++) {
            float life = 90 + 80 * FxWorld.hash(811, k, 1);
            double cycle = (t + FxWorld.hash(811, k, 2) * life) / life;
            int round = (int) Math.floor(cycle);
            float u = (float) (cycle - round);
            float ang = FxWorld.hash(811 + round, k, 3) * Mth.TWO_PI + u * 2.6f;
            float rr = size * (0.5f - 0.36f * u * u);
            Vector3f p = new Vector3f(eye).add(new Vector3f(x).mul(Mth.cos(ang) * rr)).add(new Vector3f(y).mul(Mth.sin(ang) * rr * 0.8f));
            float s = size * (0.07f + 0.12f * FxWorld.hash(811 + round, k, 4)) * (1 - 0.6f * u);
            float fade = Math.min(1, u * 6) * Math.min(1, (1 - u) * 5);
            FxDraw.billboard(p, s, ang + u, FxDraw.SPLAT, FxWorld.hash(811 + round, k, 5), BLACK, 0.95f * fade * in, false);
        }
    }

    // ---- the sign and the shockwave ----------------------------------------------------------------------------------

    private static void sign(Domain d, Vec3 camera, double t, float partial) {
        DominioSpec spec = d.spec;
        double onda = spec.ondaTick();
        if (t > spec.whiteTick() + 6 || t >= d.closeAt()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Entity caster = minecraft.level.getEntity(d.caster);
        if (caster != null && t > onda - 10 && t < onda + 5) {
            // The star of light at his hand (in front of his chin), growing to the moment the wave leaves.
            float yaw = caster.getViewYRot(partial) * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vector3f hand = caster.getEyePosition(partial).add(fwd.scale(0.42)).add(0, -0.22, 0).subtract(camera).toVector3f();
            float k = (float) (t < onda ? Math.pow((t - (onda - 10)) / 10.0, 2) : 1 - (t - onda) / 5.0);
            k = Mth.clamp(k, 0, 1);
            FxDraw.billboard(hand, 0.6f + 3.2f * k, (float) t * 0.03f, FxDraw.FLARE, 4 / 32f, WHITE, k, true);
            FxDraw.billboard(hand, 0.3f + 1.2f * k, 0, FxDraw.SOFT, 0.9f, WHITE, k, true);
            Vector3f l = FxDraw.left().mul(1.2f + 5 * k);
            Vector3f u = FxDraw.up().mul(0.8f + 3 * k);
            FxDraw.ribbon(new Vector3f(hand).sub(l), new Vector3f(hand).add(l), 0.05f + 0.1f * k, FxDraw.BEAM, 0.9f, SILVER, k, true);
            FxDraw.ribbon(new Vector3f(hand).sub(u), new Vector3f(hand).add(u), 0.04f + 0.08f * k, FxDraw.BEAM, 0.9f, WHITE, k * 0.8f, true);
        }
        if (t < onda) {
            return;
        }
        // The wave: a shell of light running out to raioChoque, then a faint sphere where time stands still.
        double wr = spec.waveRadius(t);
        float run = (float) Mth.clamp((t - onda) / DominioSpec.WAVE_TICKS, 0, 1);
        Vector3f c = d.center.add(0, 1.2, 0).subtract(camera).toVector3f();
        double dist = camera.distanceTo(d.center.add(0, 1.2, 0));
        float apparent = (float) (dist > wr * 1.01 ? wr * dist / Math.sqrt(Math.max(1.0E-3, dist * dist - wr * wr)) : wr);
        float front = 1 - run;
        float hold = (float) (t < spec.whiteTick() ? 0.22 + 0.06 * Math.sin(t * 0.6) : Math.max(0, 0.22 * (1 - (t - spec.whiteTick()) / 6)));
        FxDraw.billboard(c, (float) (apparent * 2.02), 0, FxDraw.RING, Mth.clamp(0.5f / apparent, 0.01f, 0.25f), WHITE, front * 0.9f + hold,
                true);
        FxDraw.billboard(c, (float) (apparent * 2.2), 0, FxDraw.RING, 0.2f, SILVER, front * 0.4f + hold * 0.5f, true);
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f g = d.center.subtract(camera).toVector3f().add(0, 0.05f, 0);
        FxDraw.disk(g, up, (float) wr * 2.1f, FxDraw.RING, Mth.clamp(1.4f / (float) Math.max(1, wr), 0.02f, 0.3f), WHITE, front, true);
        FxDraw.disk(g, up, (float) wr * 2.5f, FxDraw.RING, 0.35f, SILVER, front * 0.5f, true);
        if (front > 0) {
            for (int k = 0; k < 60; k++) {
                Vector3f dir = FxWorld.sphere(901, k);
                Vector3f from = new Vector3f(dir).mul((float) wr * 0.85f).add(c);
                Vector3f to = new Vector3f(dir).mul((float) wr + 1.5f + 3 * front).add(c);
                FxDraw.ribbon(from, to, 0.1f, FxDraw.STREAK, 0.9f, k % 3 == 0 ? SILVER : WHITE, front * 0.8f, true);
            }
        }
    }

    // ---- outside: the white dome, then the black ball ---------------------------------------------------------------

    private static void shell(Domain d, Vec3 camera, double t, float now) {
        DominioSpec spec = d.spec;
        if (t < spec.whiteTick() || t >= d.shatterAt()) {
            return;
        }
        Vector3f c = d.center.subtract(camera).toVector3f();
        boolean whiteOnly = d.closeAt() < spec.tunnelTick();
        // White to black over the first ticks of the tunnel.
        float black = whiteOnly ? 0 : (float) Mth.clamp((t - spec.tunnelTick()) / 8.0, 0, 1);
        float r = (float) (black > 0 ? d.radius() : spec.whiteRadius(t));
        if (r < 0.05f) {
            return;
        }
        double dist = camera.distanceTo(d.center);
        boolean outside = dist > r;
        if (black < 1) {
            if (outside) {
                whiteDome(c, r, t, now, 1 - black, true, (float) dist);
            } else {
                pour(d, c, t, now, 1 - black);
            }
        }
        if (black > 0) {
            sphere(c, (float) d.radius(), BLACK, black, 22, 40);
            if (outside) {
                // Smooth black: only a soft hint of light on top, no ring around it.
                Vector3f toCam = new Vector3f(c).negate().normalize();
                Vector3f shine = new Vector3f(toCam).add(0, 0.9f, 0).normalize().mul(r * 0.98f).add(c);
                FxDraw.billboard(shine, r * 0.9f, 0, FxDraw.SOFT, 0.0f, SILVER, 0.05f * black, true);
            }
        }
    }

    // A sphere of soft white light like a cloud: the white shell, cloud puffs bulging out of its skin, a halo, and the
    // front running along the ground.
    private static void whiteDome(Vector3f c, float r, double t, float now, float k, boolean outside, float dist) {
        // From inside (Gojo watching it grow) it is a veil, so what it is about to swallow still shows through it.
        float skin = outside ? 1 : 0.55f;
        sphere(c, r, WHITE, 0.94f * k * skin, 18, 32);
        for (int n = 0; n < 150; n++) {
            Vector3f dir = FxWorld.sphere(921, n);
            float bulge = 1 + 0.03f * Mth.sin(now * 0.2f + n);
            Vector3f p = new Vector3f(dir).mul(r * bulge).add(c);
            float size = r * (0.22f + 0.24f * FxWorld.hash(921, n, 1));
            float[] col = n % 5 == 0 ? FxDraw.rgb(0xF2EEFF) : WHITE;
            FxDraw.billboard(p, size, FxWorld.hash(921, n, 2) * 6 + now * 0.01f, FxDraw.SMOKE, 0.15f + 0.1f * Mth.sin(now * 0.1f + n), col,
                    0.95f * k * skin, false);
        }
        if (outside) {
            FxDraw.billboard(c, r * 2.9f, 0, FxDraw.SOFT, 0.2f, WHITE, 0.35f * k, true);
        }
        float grow = (float) Mth.clamp(1 - r / 40.0, 0, 1);
        Vector3f up = new Vector3f(0, 1, 0);
        FxDraw.disk(new Vector3f(c).add(0, 0.06f, 0), up, r * 2.2f, FxDraw.RING, 0.25f, WHITE, (0.4f + 0.6f * grow) * k, true);
    }

    // A sphere of quads, seen from both sides.
    private static void sphere(Vector3f c, float r, float[] rgb, float opacity, int rings, int segments) {
        for (int i = 0; i < rings; i++) {
            float t0 = Mth.PI * i / rings - Mth.HALF_PI;
            float t1 = Mth.PI * (i + 1) / rings - Mth.HALF_PI;
            for (int j = 0; j < segments; j++) {
                float p0 = Mth.TWO_PI * j / segments;
                float p1 = Mth.TWO_PI * (j + 1) / segments;
                FxDraw.quad(point(c, r, t0, p0), point(c, r, t0, p1), point(c, r, t1, p1), point(c, r, t1, p0), FxDraw.SOLID, 0, rgb, opacity, false);
            }
        }
    }

    private static Vector3f point(Vector3f c, float r, float lat, float lon) {
        return new Vector3f(Mth.cos(lat) * Mth.cos(lon) * r, Mth.sin(lat) * r, Mth.cos(lat) * Mth.sin(lon) * r).add(c);
    }

    // ---- the tunnel ----------------------------------------------------------------------------------------------------

    // Everything is placed around the camera along the tunnel's axis: the lines come out of a point 400 blocks away and
    // rush past, faster and faster (the distance run grows with the cube of the time).
    private static void tunnel(Domain d, double t, float now) {
        double tau = t - d.spec.tunnelTick();
        double total = Math.max(1, d.spec.fases().tunel());
        float u = (float) Mth.clamp(tau / total, 0, 1);
        double run = 3 * tau + 26 * tau * tau * tau / (3 * total * total);
        float speed = 3 + 26 * u * u;
        float in = (float) Mth.clamp(tau / 6, 0, 1);
        Vector3f a = d.tunnelAxis();
        Vector3f x = new Vector3f(a).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f y = new Vector3f(x).cross(a).normalize();

        Vector3f far = new Vector3f(a).mul(390);
        FxDraw.billboard(far, 420, 0, FxDraw.SOFT, 0.0f, PURPLE, 0.3f * in, true);
        FxDraw.billboard(far, 180, now * 0.01f, FxDraw.SOFT, 0.2f, MAGENTA, 0.25f * in, true);

        double range = 600;
        for (int k = 0; k < 540; k++) {
            float ang = FxWorld.hash(931, k, 1) * Mth.TWO_PI;
            float rr = 1.6f + 46 * (float) Math.pow(FxWorld.hash(931, k, 2), 1.6);
            double z = 400 - ((run * (0.75 + 0.5 * FxWorld.hash(931, k, 3)) + FxWorld.hash(931, k, 4) * range) % range);
            float len = 6 + speed * (1.4f + 1.6f * FxWorld.hash(931, k, 5));
            Vector3f off = new Vector3f(x).mul(Mth.cos(ang) * rr).add(new Vector3f(y).mul(Mth.sin(ang) * rr));
            Vector3f from = new Vector3f(a).mul((float) z).add(off);
            Vector3f to = new Vector3f(a).mul((float) z + len).add(off);
            float fade = in * smooth(400, 230, (float) z) * smooth(-200, -40, (float) z);
            float width = (0.05f + 0.2f * FxWorld.hash(931, k, 6)) * (0.6f + rr / 30f);
            float pick = FxWorld.hash(931, k, 7);
            float[] col = pick < 0.38f ? MAGENTA : pick < 0.6f ? PINK : pick < 0.78f ? RED : pick < 0.92f ? PURPLE : BLUE;
            FxDraw.ribbon(from, to, width, FxDraw.STREAK, 0.7f, col, fade * 0.9f, true);
        }
        // Clouds of dark violet giving it volume, and white flecks, all streaming the same way, slower.
        for (int k = 0; k < 60; k++) {
            float ang = FxWorld.hash(932, k, 1) * Mth.TWO_PI;
            float rr = 8 + 62 * FxWorld.hash(932, k, 2);
            double z = 400 - ((run * 0.5 + FxWorld.hash(932, k, 4) * range) % range);
            Vector3f p = new Vector3f(a).mul((float) z).add(new Vector3f(x).mul(Mth.cos(ang) * rr)).add(new Vector3f(y).mul(Mth.sin(ang) * rr));
            float s = 18 + 26 * FxWorld.hash(932, k, 3);
            FxDraw.billboard(p, s, FxWorld.hash(932, k, 5) * 6, FxDraw.SMOKE, 0.3f, k % 3 == 0 ? FxDraw.rgb(0x1A0630) : FxDraw.rgb(0x3A0F5A),
                    0.4f * in * smooth(400, 260, (float) z), false);
        }
        for (int k = 0; k < 26; k++) {
            float ang = FxWorld.hash(933, k, 1) * Mth.TWO_PI;
            float rr = 5 + 25 * FxWorld.hash(933, k, 2);
            double z = 400 - ((run * 0.8 + FxWorld.hash(933, k, 4) * range) % range);
            Vector3f p = new Vector3f(a).mul((float) z).add(new Vector3f(x).mul(Mth.cos(ang) * rr)).add(new Vector3f(y).mul(Mth.sin(ang) * rr));
            FxDraw.billboard(p, 1.5f + 4 * FxWorld.hash(933, k, 3), FxWorld.hash(933, k, 5) * 6, FxDraw.SPLAT, FxWorld.hash(933, k, 6), WHITE,
                    0.85f * in * smooth(400, 230, (float) z), false);
        }
    }

    // ---- the void ------------------------------------------------------------------------------------------------------

    private static void drawVoid(Domain d, Vec3 camera, double t, float now, float partial) {
        DominioSpec.Visual v = d.spec.visual();
        float[] ringColor = FxDraw.rgb(FxDraw.parse(v.corBuraco(), 0x8FE6FF));
        float[] haze = FxDraw.rgb(FxDraw.parse(v.corNevoa(), 0x2F5CFF));
        float in = (float) Mth.clamp((t - d.spec.voidTick()) / 8.0, 0, 1);
        Vector3f dir = d.holeDir();
        float size = (float) v.buracoTamanho();
        Vector3f eye = new Vector3f(dir).mul(95);
        Vector3f x = new Vector3f(dir).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f y = new Vector3f(x).cross(dir).normalize();

        sky(v, dir, size, now, in, haze);
        // The galaxy band through the hole, like the reference: long soft light across the sky, laid on the sky sphere
        // (so it is never cut by the far plane) in short pieces that fade out towards both ends.
        int pieces = 28;
        float span = 1.5f;
        for (int k = 0; k < 3; k++) {
            float w = 8 + k * 10;
            for (int i = 0; i < pieces; i++) {
                float a0 = -span + 2 * span * i / pieces;
                float a1 = -span + 2 * span * (i + 1) / pieces;
                float mid = (a0 + a1) / 2 / span;
                float fade = (1 - mid * mid) * (1 - mid * mid);
                Vector3f p0 = new Vector3f(dir).mul(Mth.cos(a0)).add(new Vector3f(x).mul(Mth.sin(a0))).normalize().mul(92);
                Vector3f p1 = new Vector3f(dir).mul(Mth.cos(a1)).add(new Vector3f(x).mul(Mth.sin(a1))).normalize().mul(92);
                FxDraw.ribbon(p0, p1, w * (0.5f + 0.5f * fade), FxDraw.BEAM, 0.3f, k == 0 ? SILVER : haze, (0.16f - k * 0.04f) * fade * in, true);
            }
        }
        // The black hole: its glow, the turning rings (shape 10) and sparks spiralling in.
        FxDraw.billboard(eye, size * 2.4f, 0, FxDraw.SOFT, 0.05f, haze, 0.35f * in, true);
        FxDraw.billboard(eye, size * 1.3f, 0, FxDraw.SOFT, 0.1f, ringColor, 0.2f * in, true);
        FxDraw.billboard(eye, size, 0, FxDraw.BLACK_HOLE, 0.27f, ringColor, in, true);
        FxDraw.billboard(eye, size * 0.62f, now * 0.01f, FxDraw.BLACK_HOLE, 0.44f, WHITE, 0.4f * in, true);
        for (int k = 0; k < 90; k++) {
            float life = 30 + 40 * FxWorld.hash(941, k, 1);
            double cycle = (t + FxWorld.hash(941, k, 2) * life) / life;
            int round = (int) Math.floor(cycle);
            float u = (float) (cycle - round);
            float lane = FxWorld.hash(941 + round, k, 3) * Mth.TWO_PI;
            Vector3f p0 = spiral(eye, x, y, size, lane, u);
            Vector3f p1 = spiral(eye, x, y, size, lane, Math.max(0, u - 0.05f));
            FxDraw.ribbon(p1, p0, 0.25f + 0.4f * FxWorld.hash(941, k, 4), FxDraw.STREAK, 0.9f, k % 4 == 0 ? ringColor : WHITE,
                    in * Mth.sin(u * Mth.PI), true);
        }
        // Dust hanging in the void around them (fixed in the world, so it slides past as they look around).
        Vector3f c = d.center.subtract(camera).toVector3f();
        for (int k = 0; k < 170; k++) {
            Vector3f p = FxWorld.sphere(951, k).mul(4 + 34 * FxWorld.hash(951, k, 1)).add(c).add(0, 2, 0);
            p.add(Mth.sin(now * 0.01f + k) * 0.6f, Mth.sin(now * 0.013f + k * 2) * 0.4f, Mth.cos(now * 0.011f + k) * 0.6f);
            float tw = 0.4f + 0.6f * Math.max(0, Mth.sin(now * (0.05f + 0.08f * FxWorld.hash(951, k, 2)) + k));
            FxDraw.billboard(p, 0.06f + 0.12f * FxWorld.hash(951, k, 3), 0, FxDraw.SOFT, 0.9f, k % 3 == 0 ? ringColor : WHITE, tw * 0.8f * in, true);
        }
        whiteInk(d, c, t, now, in);
        ripples(camera, now, partial, in);
        people(d, camera, now, partial, in);
    }

    // Stars on every side, a few big ones, and a blue haze (none in front of the hole).
    private static void sky(DominioSpec.Visual v, Vector3f hole, float size, float now, float in, float[] haze) {
        float clear = Mth.cos((float) Math.atan(size * 0.75f / 95));
        for (int k = 0; k < 16; k++) {
            Vector3f p = FxWorld.sphere(961, k);
            if (p.dot(hole) > 0.93f) {
                p.add(new Vector3f(hole).mul(-1.2f)).normalize();
            }
            p.mul(92);
            FxDraw.billboard(p, 50 + 50 * FxWorld.hash(961, k, 1), FxWorld.hash(961, k, 2) * 6 + now * 0.0005f, FxDraw.SMOKE,
                    0.3f + 0.3f * FxWorld.hash(961, k, 3), k % 3 == 0 ? SILVER : haze, 0.09f * in, true);
        }
        int count = Math.max(0, v.estrelas());
        for (int k = 0; k < count; k++) {
            Vector3f p = FxWorld.sphere(962, k);
            if (p.dot(hole) > clear) {
                continue;
            }
            p.mul(90);
            float twinkle = 0.45f + 0.55f * Mth.sin(now * (0.06f + 0.12f * FxWorld.hash(962, k, 1)) + k);
            float s = 0.1f + 0.35f * FxWorld.hash(962, k, 2) * FxWorld.hash(962, k, 3);
            boolean big = k % 67 == 0;
            FxDraw.billboard(p, big ? s * 8 : s, 0, big ? FxDraw.FLARE : FxDraw.SOFT, big ? 4 / 32f : 0.9f, k % 4 == 0 ? SILVER : WHITE,
                    twinkle * in, true);
        }
    }

    // A point on a spiral falling into the hole: u 1 out on the rings, 0 at the hole's edge.
    private static Vector3f spiral(Vector3f eye, Vector3f x, Vector3f y, float size, float lane, float u) {
        float rr = size * (0.14f + 0.5f * u);
        float ang = lane + (1 - u) * 3.2f;
        return new Vector3f(eye).add(new Vector3f(x).mul(Mth.cos(ang) * rr)).add(new Vector3f(y).mul(Mth.sin(ang) * rr * 0.85f));
    }

    // Abstract splashes of white ink drifting through the void, fading in and out.
    private static void whiteInk(Domain d, Vector3f c, double t, float now, float in) {
        for (int k = 0; k < 28; k++) {
            float life = 160 + 120 * FxWorld.hash(971, k, 1);
            double cycle = (t + FxWorld.hash(971, k, 2) * life) / life;
            int round = (int) Math.floor(cycle);
            float u = (float) (cycle - round);
            int seed = 971 + round * 13;
            Vector3f base = FxWorld.sphere(seed, k);
            base.y = base.y * 0.5f + 0.2f;
            base.normalize().mul(16 + 64 * FxWorld.hash(seed, k, 3));
            Vector3f drift = FxWorld.sphere(seed + 7, k).mul(14 * u);
            Vector3f p = new Vector3f(c).add(base).add(drift).add(0, 4, 0);
            float fade = smooth(0, 0.15f, u) * smooth(1, 0.7f, u);
            float s = 4 + 12 * FxWorld.hash(seed, k, 4);
            FxDraw.billboard(p, s, FxWorld.hash(seed, k, 5) * 6 + u * 0.6f, FxDraw.SPLAT, FxWorld.hash(seed, k, 6), WHITE, 0.9f * fade * in, false);
        }
    }

    // Rings of water spreading from each step, several behind each other, the way a drop ripples a still pool.
    private static void ripples(Vec3 camera, float now, float partial, float in) {
        Vector3f up = new Vector3f(0, 1, 0);
        for (Ripple r : RIPPLES) {
            float age = (float) (ScreenFx.now() + partial - r.born());
            Vector3f at = r.pos().subtract(camera).toVector3f().add(0, 0.03f, 0);
            float u0 = Math.min(1, age / RIPPLE_EVERY);
            float rad = (0.4f + 3.6f * r.size()) * FxWorld.easeOut(u0);
            if (rad >= 0.05f) {
                float fade = (1 - u0) * (1 - u0);
                FxDraw.disk(at, up, rad * 2, FxDraw.RING, Mth.clamp(0.06f / Math.max(0.3f, rad), 0.008f, 0.12f), WHITE, 0.7f * fade * in, true);
            }
            float u = Math.min(1, age / 20);
            FxDraw.disk(at, up, 1.6f * r.size(), FxDraw.SOFT, 0.1f, SILVER, 0.25f * (1 - u) * in, true);
        }
    }

    // Gojo's eyes glowing blue; on everyone caught, thin threads of light pouring into their head.
    private static void people(Domain d, Vec3 camera, float now, float partial, float life) {
        Minecraft minecraft = Minecraft.getInstance();
        float[] glow = FxDraw.rgb(FxDraw.parse(d.spec.visual().corBuraco(), 0x8FE6FF));
        Entity viewer = minecraft.gameRenderer.getMainCamera().getEntity();
        boolean detached = minecraft.gameRenderer.getMainCamera().isDetached();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity.isInvisible()) {
                continue;
            }
            Vec3 pos = entity.getPosition(partial);
            if (!d.contains(pos.add(0, entity.getBbHeight() / 2, 0))) {
                continue;
            }
            if (entity.getId() == d.caster) {
                if (entity == viewer && !detached) {
                    continue;
                }
                float yaw = Mth.rotLerp(partial, living.yHeadRotO, living.yHeadRot) * Mth.DEG_TO_RAD;
                Vector3f fwd = new Vector3f(-Mth.sin(yaw), 0, Mth.cos(yaw));
                Vector3f right = new Vector3f(-fwd.z, 0, fwd.x);
                Vector3f eyes = pos.add(0, entity.getEyeHeight() + 0.03, 0).subtract(camera).toVector3f().add(new Vector3f(fwd).mul(0.27f));
                for (int s = -1; s <= 1; s += 2) {
                    Vector3f e = new Vector3f(eyes).add(new Vector3f(right).mul(0.12f * s));
                    FxDraw.billboard(e, 0.34f, now * 0.02f, FxDraw.FLARE, 6 / 32f, glow, 0.9f * life, true);
                    FxDraw.billboard(e, 0.1f, 0, FxDraw.SOFT, 0.9f, WHITE, life, true);
                }
                continue;
            }
            Vector3f head = pos.add(0, entity.getEyeHeight(), 0).subtract(camera).toVector3f();
            int seed = 615 + entity.getId();
            for (int k = 0; k < 16; k++) {
                float span = 8 + 6 * FxWorld.hash(seed, k, 1);
                double cycle = (now + FxWorld.hash(seed, k, 2) * span) / span;
                float u = (float) (cycle - Math.floor(cycle));
                Vector3f dir = FxWorld.sphere(seed, k + (int) Math.floor(cycle) * 13);
                float out = 4.5f * (1 - u) + 0.2f;
                Vector3f from = new Vector3f(dir).mul(out + 1.6f).add(head);
                Vector3f to = new Vector3f(dir).mul(out).add(head);
                FxDraw.ribbon(from, to, 0.045f, FxDraw.STREAK, 0.9f, k % 2 == 0 ? WHITE : glow, u * (1 - u) * 3.2f * life, true);
            }
            FxDraw.billboard(head, 1.0f, 0, FxDraw.SOFT, 0.3f, glow, (0.25f + 0.2f * Mth.sin(now * 0.9f + entity.getId())) * life, true);
        }
    }

    // ---- the end: cracks, then glowing glass shards --------------------------------------------------------------------

    private static Vector3f[][] grid() {
        if (grid == null) {
            grid = new Vector3f[LAT + 1][LON];
            for (int i = 0; i <= LAT; i++) {
                for (int j = 0; j < LON; j++) {
                    boolean pole = i == 0 || i == LAT;
                    float lat = Mth.PI * i / LAT - Mth.HALF_PI + (pole ? 0 : (FxWorld.hash(981, i * 64 + j, 1) - 0.5f) * 0.18f);
                    float lon = Mth.TWO_PI * j / LON + (pole ? 0 : (FxWorld.hash(981, i * 64 + j, 2) - 0.5f) * 0.22f);
                    grid[i][j] = new Vector3f(Mth.cos(lat) * Mth.cos(lon), Mth.sin(lat), Mth.cos(lat) * Mth.sin(lon));
                }
            }
        }
        return grid;
    }

    // Cracks of light running over the sphere along where it will break: from outside only on the side facing the camera.
    private static void cracks(Domain d, Vec3 camera, double t) {
        Vector3f[][] g = grid();
        float k = (float) Mth.clamp((t - d.closeAt()) / (d.shatterAt() - d.closeAt()), 0, 1);
        float r = (float) d.radius() * 1.004f;
        Vector3f c = d.center.subtract(camera).toVector3f();
        boolean inside = d.contains(camera);
        int edge = 0;
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                Vector3f a = g[i][j];
                Vector3f[] ends = {g[i][(j + 1) % LON], g[i + 1][j], g[i + 1][(j + 1) % LON]};
                for (Vector3f b : ends) {
                    edge++;
                    float shown = Mth.clamp(k * 1.5f - FxWorld.hash(983, edge, 1) * 0.5f, 0, 1);
                    if (shown <= 0 || a.distanceSquared(b) < 1.0E-6f) {
                        continue;
                    }
                    Vector3f mid = new Vector3f(a).add(b).normalize();
                    Vector3f pa = new Vector3f(a).mul(r).add(c);
                    if (!inside && new Vector3f(pa).negate().dot(mid) < 0) {
                        continue;
                    }
                    Vector3f pb = new Vector3f(a).lerp(b, shown).normalize().mul(r).add(c);
                    float w = 0.12f + r * 0.004f;
                    FxDraw.ribbon(pa, pb, w * 4, FxDraw.BEAM, 0.2f, SILVER, 0.35f, true);
                    FxDraw.ribbon(pa, pb, w, FxDraw.BEAM, 0.9f, WHITE, 1, true);
                }
            }
        }
    }

    // The sphere breaks along the cracks into triangles of glass, black and milky white with glowing edges, that fly out
    // turning and fade, with white dust thrown out among them.
    private static void shards(Domain d, Vec3 camera, double t) {
        Vector3f[][] g = grid();
        double tau = t - d.shatterAt();
        float u = (float) Mth.clamp(tau / Math.max(1, d.goneAt() - d.shatterAt()), 0, 1);
        if (u >= 1) {
            return;
        }
        float r = (float) d.radius();
        Vector3f c = d.center.subtract(camera).toVector3f();
        boolean white = d.closeAt() < d.spec.tunnelTick();
        float fade = (float) Math.pow(1 - u, 1.5);
        int n = 0;
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                Vector3f a = g[i][j], b = g[i][(j + 1) % LON], cc = g[i + 1][(j + 1) % LON], e = g[i + 1][j];
                shard(c, r, a, b, cc, n++, (float) tau, u, fade, white);
                shard(c, r, a, cc, e, n++, (float) tau, u, fade, white);
            }
        }
        for (int k = 0; k < 50; k++) {
            Vector3f dir = FxWorld.sphere(991, k);
            Vector3f p = new Vector3f(dir).mul(r + (float) tau * (0.6f + 0.8f * FxWorld.hash(991, k, 1))).add(c);
            FxDraw.billboard(p, 4 + 8 * FxWorld.hash(991, k, 2) + 6 * u, FxWorld.hash(991, k, 3) * 6, FxDraw.SMOKE, 0.2f + 0.6f * u, MILK, 0.55f * fade,
                    false);
        }
    }

    private static void shard(Vector3f c, float r, Vector3f a, Vector3f b, Vector3f e, int n, float tau, float u, float fade, boolean white) {
        Vector3f mid = new Vector3f(a).add(b).add(e).div(3);
        if (mid.lengthSquared() < 1.0E-4f || a.distanceSquared(b) < 1.0E-6f || a.distanceSquared(e) < 1.0E-6f || b.distanceSquared(e) < 1.0E-6f) {
            return;
        }
        Vector3f out = new Vector3f(mid).normalize();
        float speed = 0.35f + 0.7f * FxWorld.hash(993, n, 1);
        Vector3f axis = FxWorld.sphere(994, n);
        float spin = tau * (0.04f + 0.1f * FxWorld.hash(993, n, 2));
        float shrink = 1 - 0.35f * u;
        Vector3f home = new Vector3f(out).mul(r + speed * tau).add(c).add(0, -0.012f * tau * tau, 0);
        Vector3f[] corners = new Vector3f[3];
        Vector3f[] src = {a, b, e};
        for (int k = 0; k < 3; k++) {
            corners[k] = new Vector3f(src[k]).mul(r).sub(new Vector3f(mid).mul(r)).rotateAxis(spin, axis.x, axis.y, axis.z).mul(shrink).add(home);
        }
        float[] face = white || FxWorld.hash(993, n, 3) < 0.3f ? MILK : BLACK;
        FxDraw.quad(corners[0], corners[1], corners[2], corners[2], FxDraw.SOLID, 0, face, 0.95f * fade, false);
        for (int k = 0; k < 3; k++) {
            FxDraw.ribbon(corners[k], corners[(k + 1) % 3], 0.18f, FxDraw.BEAM, 0.8f, WHITE, 0.9f * fade, true);
        }
    }

    // ------------------------------------------------------------------------------------------------------------------

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    // A screen-wide gradient.
    private static void sky(int top, int bottom) {
        sky(top, bottom, 1);
    }

    private static void sky(int top, int bottom, float alpha) {
        if (alpha <= 0) {
            return;
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 1, 0, 1, -1, 1), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        if (alpha < 1) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] low = FxDraw.rgb(bottom);
        float[] high = FxDraw.rgb(top);
        builder.addVertex(0, 0, 0).setColor(low[0], low[1], low[2], alpha);
        builder.addVertex(1, 0, 0).setColor(low[0], low[1], low[2], alpha);
        builder.addVertex(1, 1, 0).setColor(high[0], high[1], high[2], alpha);
        builder.addVertex(0, 1, 0).setColor(high[0], high[1], high[2], alpha);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.restoreProjectionMatrix();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    // ---- the screen --------------------------------------------------------------------------------------------------

    // ---- inside the white: it pours over everything like water ------------------------------------------------------

    // Seen from inside the white: from the sky, white falls like water in heavy cloudy curtains and floods the ground,
    // running out from Gojo towards the edge; the front is a rolling wall of foam. (fill then washes the rest out.)
    private static void pour(Domain d, Vector3f c, double t, float now, float k) {
        DominioSpec spec = d.spec;
        float r = (float) spec.whiteRadius(t);
        float big = (float) d.radius();
        Vector3f up = new Vector3f(0, 1, 0);
        // The flooded ground: a sheet of white out to the front, and foam patches drifting on it.
        FxDraw.disk(new Vector3f(c).add(0, 0.06f, 0), up, r * 2.05f, FxDraw.SOFT, 0.75f, WHITE, 0.92f * k, false);
        for (int n = 0; n < 140; n++) {
            float dist = big * (float) Math.sqrt(FxWorld.hash(941, n, 1));
            if (dist > r) {
                continue;
            }
            float ang = FxWorld.hash(941, n, 2) * Mth.TWO_PI + now * 0.002f;
            Vector3f p = new Vector3f(c).add(Mth.cos(ang) * dist, 0.1f + 0.05f * (n % 4), Mth.sin(ang) * dist);
            float size = 3 + 6 * FxWorld.hash(941, n, 3);
            FxDraw.disk(p, up, size, FxDraw.SMOKE, 0.2f, n % 4 == 0 ? MILK : WHITE, 0.9f * k, false);
        }
        // The front: a wall of foam rolling over the ground, with spray thrown up.
        int ring = 110;
        for (int n = 0; n < ring; n++) {
            float ang = n / (float) ring * Mth.TWO_PI + FxWorld.hash(942, n, 1) * 0.05f;
            float wob = 1 + 0.05f * Mth.sin(ang * 7 + now * 0.15f) + 0.03f * Mth.sin(ang * 13 - now * 0.11f);
            float rr = r * wob;
            float h = 0.6f + 2.2f * FxWorld.hash(942, n, 2) + 0.6f * Mth.sin(now * 0.3f + n);
            Vector3f p = new Vector3f(c).add(Mth.cos(ang) * rr, h, Mth.sin(ang) * rr);
            FxDraw.billboard(p, 2.4f + 3.2f * FxWorld.hash(942, n, 3), FxWorld.hash(942, n, 4) * 6 + now * 0.02f, FxDraw.SMOKE, 0.2f, WHITE,
                    0.95f * k, false);
        }
        // From the sky: puffs of white pour down where the white has reached, swelling as they fall, like water poured
        // from above, and settle on the ground as more foam.
        int falls = 190;
        for (int n = 0; n < falls; n++) {
            float dist = big * (float) Math.sqrt(FxWorld.hash(943, n, 1));
            double reached = reachedAt(spec, dist) + 14 * FxWorld.hash(943, n, 2);
            double age = t - reached;
            if (age < 0) {
                continue;
            }
            float drop = (float) Mth.clamp(age / (22 + 18 * FxWorld.hash(943, n, 3)), 0, 1);
            float ang = FxWorld.hash(943, n, 4) * Mth.TWO_PI;
            float top = 18 + 22 * FxWorld.hash(943, n, 5);
            float y = top * (1 - drop * drop) + 1.2f * drop;
            Vector3f p = new Vector3f(c).add(Mth.cos(ang) * dist, y, Mth.sin(ang) * dist);
            float size = (4 + 7 * FxWorld.hash(943, n, 6)) * (0.6f + 0.8f * drop);
            FxDraw.billboard(p, size, FxWorld.hash(943, n, 7) * 6 + now * 0.01f, FxDraw.SMOKE, 0.18f, n % 5 == 0 ? MILK : WHITE, 0.95f * k,
                    false);
            if (drop < 1) {
                // The stream behind it, like a waterfall.
                Vector3f tail = new Vector3f(p).add(0, Math.min(top - y, 10), 0);
                FxDraw.ribbon(p, tail, size * 0.45f, FxDraw.STREAK, 0.5f, WHITE, 0.7f * k * (1 - drop * 0.6f), false);
            }
        }
        // Overhead, the white closing over the sky above the flood.
        float roof = (float) Mth.clamp((t - spec.whiteTick()) / Math.max(1, spec.fases().branco() * 0.6), 0, 1);
        for (int n = 0; n < 60; n++) {
            float dist = r * (float) Math.sqrt(FxWorld.hash(944, n, 1));
            float ang = FxWorld.hash(944, n, 2) * Mth.TWO_PI + now * 0.003f;
            Vector3f p = new Vector3f(c).add(Mth.cos(ang) * dist, 24 + 16 * FxWorld.hash(944, n, 3), Mth.sin(ang) * dist);
            FxDraw.billboard(p, 12 + 16 * FxWorld.hash(944, n, 4), FxWorld.hash(944, n, 5) * 6, FxDraw.SMOKE, 0.25f, WHITE, 0.9f * k * roof, false);
        }
    }

    // When the white's front reaches dist from the center, in ticks since the press.
    private static double reachedAt(DominioSpec spec, double dist) {
        double lo = spec.whiteTick();
        double hi = spec.whiteTick() + Math.max(4, spec.fases().branco() * 0.85);
        if (spec.whiteRadius(hi) <= dist) {
            return hi;
        }
        for (int i = 0; i < 14; i++) {
            double mid = (lo + hi) / 2;
            if (spec.whiteRadius(mid) < dist) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    // Under the HUD: the white swallowing the view, the ink tearing it open into the tunnel, and for those caught the
    // information pouring in (DomainScreen).
    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (DOMAINS.isEmpty() || minecraft.player == null) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Vec3 camera = cameraPos();
        for (Domain d : DOMAINS) {
            double t = time(d, partial);
            Stage stage = d.stage(camera, t);
            DominioSpec spec = d.spec;
            boolean gojo = minecraft.player.getId() == d.caster;
            if (stage == Stage.TUNNEL && t < spec.tunnelTick() + BURST_TICKS) {
                // The white (the world is all white by now) tears open into the tunnel.
                DomainScreen.whiteBurst(graphics, (float) ((t - spec.tunnelTick()) / BURST_TICKS), t);
            }
            if (t < spec.tunnelTick() && t < d.closeAt() && camera.distanceTo(d.center) < d.radius() + 40) {
                caption(graphics, minecraft, d, t);
            }
            boolean victim = !gojo && d.contains(minecraft.player.getEyePosition(partial))
                    && (stage == Stage.TUNNEL || stage == Stage.VOID);
            if (victim) {
                float strength = (float) Mth.clamp(spec.visual().telaVitima(), 0, 2) * (FxConfig.on(FxConfig.REDUCED_FLASH) ? 0.4f : 1);
                double total = spec.closeTick() - spec.tunnelTick();
                float u = (float) Mth.clamp((t - spec.tunnelTick()) / Math.max(1, total), 0, 1);
                DomainScreen.information(graphics, t, u, strength, stage == Stage.VOID, (int) d.start);
            }
        }
    }

    // Gojo's words, in game: "Expansão de Domínio:" during the sign, "Vazio Infinito." as the white comes out.
    private static void caption(GuiGraphics graphics, Minecraft minecraft, Domain d, double t) {
        DominioSpec spec = d.spec;
        double first = Math.max(4, spec.ondaTick() - 46);
        double second = spec.whiteTick() + 2;
        String text;
        double from;
        double to;
        int color;
        float size;
        if (t >= first && t < spec.ondaTick()) {
            text = "Expansão de Domínio:";
            from = first;
            to = spec.ondaTick();
            color = 0xFFFFFF;
            size = 1.6f;
        } else if (t >= second && t < second + 60) {
            text = "Vazio Infinito.";
            from = second;
            to = second + 60;
            color = 0xBFEFFF;
            size = 2.1f;
        } else {
            return;
        }
        float a = (float) Math.min(Mth.clamp((t - from) / 6, 0, 1), Mth.clamp((to - t) / 8, 0, 1));
        int alpha = (int) (a * 255);
        if (alpha < 8) {
            return;
        }
        Font font = minecraft.font;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(graphics.guiWidth() / 2f, graphics.guiHeight() * 0.74f, 0);
        pose.scale(size, size, 1);
        graphics.drawString(font, text, -font.width(text) / 2, 0, (alpha << 24) | color, true);
        pose.popPose();
    }
}
