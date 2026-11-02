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
// - the white: a line of light running out along the ground with sparkles, then round bubbles of white popping up
//   everywhere and merging until all is white but the people; then it tears open into the tunnel;
// - the tunnel (only inside): black ink eats the white, then streaks of violet, magenta, red and blue light rush out of
//   a far dark point past the camera, faster and faster, the screen stretching with the speed;
// - the void (only inside, a space of its own above the world): no walls, a still mirror of water for a floor, endless depth: stars and a blue haze on every side, far behind Gojo a
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
    // Stark's ink splashes (textures/fx/gojo_ink/1..12.png, white on clear), tinted black or white, used for every
    // splash of ink inside the domain.
    private static final net.minecraft.resources.ResourceLocation[] INK = new net.minecraft.resources.ResourceLocation[12];

    static {
        for (int i = 0; i < INK.length; i++) {
            INK[i] = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.starkinhon.battlegroundsmod.BattlegroundsMod.MODID,
                    "textures/fx/gojo_ink/" + (i + 1) + ".png");
        }
    }
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
        // The floor of the inside, a space of its own straight above center (DominioSpec.arena): from the tunnel to the
        // shatter everyone in the domain is up there.
        final Vec3 inner;
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
            this.inner = DominioSpec.arena(Minecraft.getInstance().level, center);
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

        // The domain's middle nearest pos: up inside, or out in the world.
        Vec3 hub(Vec3 pos) {
            return pos.distanceToSqr(inner) < pos.distanceToSqr(center) ? inner : center;
        }

        boolean contains(Vec3 pos) {
            return pos.distanceToSqr(hub(pos)) < radius() * radius();
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
            if (pos.distanceTo(hub(pos)) >= radius()) {
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
    // The tunnel opens on a long run of black and white impact frames (IMPACT_TICKS each) with a hard shake, before the
    // white tears: manga (black ink on white) and black paper with white ink, back and forth.
    private static final int IMPACT_TICKS = 2;
    private static final int[] TUNNEL_IMPACT = {3, 4, 3, 4, 3, 4, 3};
    private static final int TUNNEL_FRAMES = IMPACT_TICKS * TUNNEL_IMPACT.length;
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

    // How white the world is for someone the white has reached: once the bubbles have covered most of it, what is left
    // of the world fades out behind them, until only the people show on white.
    private static float fill(Domain d, double t) {
        double from = d.spec.whiteTick() + d.spec.fases().branco() * DominioSpec.WHITE_SPREAD * 0.7;
        double to = d.spec.whiteTick() + d.spec.fases().branco() * (DominioSpec.WHITE_SPREAD + 0.1);
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
        DOMAINS.removeIf(d -> time(d, 0) > d.goneAt() + 1 || minecraft.player.position().distanceToSqr(d.hub(minecraft.player.position())) > VIEW * VIEW * 4);
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
            double dist = camera.distanceTo(d.hub(camera));
            Stage stage = d.stage(camera, t);
            boolean victim = !caster && d.contains(minecraft.player.getEyePosition()) && t >= spec.tunnelTick() && t < d.shatterAt();

            if (t >= spec.ondaTick() && d.once("onda") && dist < 110) {
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
                    ScreenFx.fovKick(-18, 14);
                    // The screen shakes as the white tears open.
                    com.starkinhon.battlegroundsmod.client.ScreenShake.start(3.5f, 34);
                    ScreenFx.impactFrame(TUNNEL_IMPACT, IMPACT_TICKS, 0xFFFFFF, d.hub(camera).add(0, 1.4, 0), 0);
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
                ScreenFx.impactFrame(new int[] {3}, 2, 0xFFFFFF, d.hub(camera).add(0, 2, 0), 0);
            }
            if (t >= d.shatterAt() && d.once("shatter") && dist < d.radius() + 60) {
                ScreenFx.flash(0xFFFFFF, dist < d.radius() ? 0.9f : 0.5f, 12);
                ScreenFx.fovKick(dist < d.radius() ? 12 : 5, 16);
                ScreenFx.warp(d.center, (float) d.radius() * 1.4f, 0.4f, 30, 0, new Vec3(0, 1, 0), 0xFFFFFF, 0.05f, 16, -1, null, 0);
            }
            if (t >= spec.voidTick() && t < d.shatterAt()) {
                steps(d, minecraft);
            }
            if (t >= spec.tunnelTick() - 2 && t < d.shatterAt() + 4) {
                floor(d, minecraft.player);
            }
        }
    }

    // The inside's own floor holds the local player up (there are no blocks up there): never below it, standing on it,
    // so they walk and jump on it like on the ground. The server keeps the same floor (InfiniteVoid.floor).
    private static void floor(Domain d, net.minecraft.client.player.LocalPlayer player) {
        Vec3 pos = player.position();
        if (d.hub(pos) != d.inner || d.inner == d.center || pos.distanceTo(d.inner) > d.radius() + 8 || pos.y > d.inner.y + 0.001) {
            return;
        }
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
        }
        player.setPos(pos.x, d.inner.y, pos.z);
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x, Math.max(0, v.y), v.z);
        player.setOnGround(true);
        player.resetFallDistance();
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
                ScreenFx.impactFrame(new int[] {2}, 1, 0x7FD8FF, d.hub(minecraft.player.position()).add(0, 1, 0), 0);
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

    // The white drawn over the world (as much as fill says). The people come back on top of it later, after the
    // bubbles and their glow (peopleOverWhite); the depth of the world is kept for them.
    private static void whiteBackdrop(RenderLevelStageEvent event, Domain d, double t, float partial) {
        sky(0xFFFFFF, 0xF4F6FB, fill(d, t));
    }

    // After all the effects and the glow: the players and mobs the white has reached drawn again, so they show over the
    // bubbles of light (still hidden by walls in front of them: the world's depth is kept). The camera's own body only
    // in third person.
    public static void peopleOverWhite(RenderLevelStageEvent event) {
        if (DOMAINS.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Domain d : DOMAINS) {
            double t = time(d, partial);
            boolean whiteShows = t >= d.spec.whiteTick() && t < d.shatterAt() && (t < d.spec.tunnelTick() + 8 || d.closeAt() < d.spec.tunnelTick());
            Vec3 camera = event.getCamera().getPosition();
            Stage stage = d.stage(camera, t);
            if (whiteShows && stage != Stage.TUNNEL && stage != Stage.VOID) {
                drawPeople(event, d, d.spec.whiteRadius(t) + 1.5, partial);
                return;
            }
        }
    }

    private static void drawPeople(RenderLevelStageEvent event, Domain d, double reach, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
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
            ink(p, s, ang + u, FxWorld.hash(811 + round, k, 5), BLACK, 0.95f * fade * in);
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

    // ---- the white bubbles, then (from outside) the black ball ---------------------------------------------------------------

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
            bubbles(d, c, t, now, 1 - black);
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
            float[] col = pick < 0.3f ? RED : pick < 0.5f ? PURPLE : pick < 0.66f ? BLUE : pick < 0.86f ? PINK : MAGENTA;
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
        // The torn white still flying past in puffs, for the first second or two.
        float torn = (float) Mth.clamp(1 - tau / 40, 0, 1);
        for (int k = 0; k < 70 && torn > 0; k++) {
            float ang = FxWorld.hash(934, k, 1) * Mth.TWO_PI;
            float rr = 3 + 30 * FxWorld.hash(934, k, 2);
            double z = 120 - ((run * 1.2 + FxWorld.hash(934, k, 4) * 200) % 200);
            Vector3f p = new Vector3f(a).mul((float) z).add(new Vector3f(x).mul(Mth.cos(ang) * rr)).add(new Vector3f(y).mul(Mth.sin(ang) * rr));
            FxDraw.billboard(p, 2 + 5 * FxWorld.hash(934, k, 3), 0, FxDraw.DISC, 0.35f, WHITE, torn * smooth(120, 80, (float) z), false);
        }
        for (int k = 0; k < 26; k++) {
            float ang = FxWorld.hash(933, k, 1) * Mth.TWO_PI;
            float rr = 5 + 25 * FxWorld.hash(933, k, 2);
            double z = 400 - ((run * 0.8 + FxWorld.hash(933, k, 4) * range) % range);
            Vector3f p = new Vector3f(a).mul((float) z).add(new Vector3f(x).mul(Mth.cos(ang) * rr)).add(new Vector3f(y).mul(Mth.sin(ang) * rr));
            ink(p, 1.5f + 4 * FxWorld.hash(933, k, 3), FxWorld.hash(933, k, 5) * 6, FxWorld.hash(933, k, 6), WHITE,
                    0.85f * in * smooth(400, 230, (float) z));
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
        Vector3f c = d.hub(camera).subtract(camera).toVector3f();
        waterFloor(d, c, eye, size, now, in, haze, ringColor);
        for (int k = 0; k < 170; k++) {
            Vector3f p = FxWorld.sphere(951, k).mul(4 + 34 * FxWorld.hash(951, k, 1)).add(c).add(0, 2, 0);
            p.add(Mth.sin(now * 0.01f + k) * 0.6f, Mth.sin(now * 0.013f + k * 2) * 0.4f, Mth.cos(now * 0.011f + k) * 0.6f);
            float tw = 0.4f + 0.6f * Math.max(0, Mth.sin(now * (0.05f + 0.08f * FxWorld.hash(951, k, 2)) + k));
            FxDraw.billboard(p, 0.06f + 0.12f * FxWorld.hash(951, k, 3), 0, FxDraw.SOFT, 0.9f, k % 3 == 0 ? ringColor : WHITE, tw * 0.8f * in, true);
        }
        winds(c, now, in, ringColor);
        whiteInk(d, c, t, now, in);
        ripples(camera, now, partial, in);
        people(d, camera, now, partial, in);
    }

    // Wind among the ink, like the references: long thin threads of light streaking round the people, quick dashes
    // that blink in and out, and wide soft sheets of air sweeping round in slow arcs. Everything turns the same way
    // round the middle, appears, flies and fades. c is the middle (camera-relative).
    private static void winds(Vector3f c, float now, float in, float[] ringColor) {
        if (in <= 0) {
            return;
        }
        // Threads: curved streaks flying fast round the middle.
        for (int k = 0; k < 70; k++) {
            float life = 18 + 22 * FxWorld.hash(971, k, 1);
            double cycle = (now + FxWorld.hash(971, k, 2) * life) / life;
            int round = (int) Math.floor(cycle);
            float u = (float) (cycle - round);
            float r = 5 + 32 * FxWorld.hash(971 + round, k, 3);
            float h = -1 + 15 * FxWorld.hash(971 + round, k, 4);
            float len = (3 + 9 * FxWorld.hash(971 + round, k, 5)) / r;
            float a = FxWorld.hash(971 + round, k, 6) * Mth.TWO_PI + u * 40 / r;
            float fade = Mth.sin(u * Mth.PI) * in;
            float[] color = k % 4 == 0 ? ringColor : k % 3 == 0 ? SILVER : WHITE;
            float w = 0.03f + 0.07f * FxWorld.hash(971 + round, k, 7);
            Vector3f prev = null;
            for (int i = 0; i <= 3; i++) {
                float ang = a - len * i / 3f;
                Vector3f p = new Vector3f(Mth.cos(ang) * r, h + 0.4f * Mth.sin(ang * 3 + k), Mth.sin(ang) * r).add(c);
                if (prev != null) {
                    float tail = 1 - (i - 1) / 3f;
                    FxDraw.ribbon(p, prev, w * (0.4f + 0.6f * tail), FxDraw.STREAK, 0.9f, color, 0.9f * fade * tail, true);
                }
                prev = p;
            }
        }
        // Dashes: short sparks of light blinking on and off where the wind passes.
        for (int k = 0; k < 60; k++) {
            float life = 6 + 8 * FxWorld.hash(973, k, 1);
            double cycle = (now + FxWorld.hash(973, k, 2) * life) / life;
            int round = (int) Math.floor(cycle);
            float u = (float) (cycle - round);
            float r = 3 + 36 * FxWorld.hash(973 + round, k, 3);
            float ang = FxWorld.hash(973 + round, k, 4) * Mth.TWO_PI + u * 6 / r;
            float h = -1 + 16 * FxWorld.hash(973 + round, k, 5);
            Vector3f p = new Vector3f(Mth.cos(ang) * r, h, Mth.sin(ang) * r).add(c);
            Vector3f along = new Vector3f(-Mth.sin(ang), 0, Mth.cos(ang)).mul(0.5f + 0.9f * FxWorld.hash(973 + round, k, 6));
            float blink = Mth.sin(u * Mth.PI);
            blink *= blink;
            FxDraw.ribbon(new Vector3f(p).sub(along), new Vector3f(p).add(along), 0.09f, FxDraw.STREAK, 0.9f, k % 3 == 0 ? ringColor : WHITE,
                    blink * in, true);
            FxDraw.billboard(p, 0.5f, 0, FxDraw.SOFT, 0.5f, WHITE, 0.35f * blink * in, true);
        }
        // Sheets of air: wide soft ribbons sweeping round in long arcs, slowly, fading at both ends.
        for (int k = 0; k < 9; k++) {
            float life = 70 + 50 * FxWorld.hash(977, k, 1);
            double cycle = (now + FxWorld.hash(977, k, 2) * life) / life;
            int round = (int) Math.floor(cycle);
            float u = (float) (cycle - round);
            float r = 9 + 26 * FxWorld.hash(977 + round, k, 3);
            float h = 0 + 12 * FxWorld.hash(977 + round, k, 4);
            float tilt = (FxWorld.hash(977 + round, k, 5) - 0.5f) * 0.5f;
            float span = 1.2f + 1.4f * FxWorld.hash(977 + round, k, 6);
            float start = FxWorld.hash(977 + round, k, 7) * Mth.TWO_PI + u * 1.6f;
            float w = 1.2f + 2.2f * FxWorld.hash(977 + round, k, 8);
            float life01 = Mth.sin(u * Mth.PI) * in;
            int pieces = 16;
            Vector3f prev = null;
            for (int i = 0; i <= pieces; i++) {
                float f = i / (float) pieces;
                float ang = start - span * f;
                Vector3f p = new Vector3f(Mth.cos(ang) * r, h + Mth.sin(ang) * r * tilt, Mth.sin(ang) * r).add(c);
                if (prev != null) {
                    float edge = Mth.sin(f * Mth.PI);
                    FxDraw.ribbon(p, prev, w * (0.5f + 0.5f * edge), FxDraw.BEAM, 0.25f, k % 2 == 0 ? SILVER : ringColor, 0.09f * edge * life01, true);
                    FxDraw.ribbon(p, prev, w * 0.15f, FxDraw.BEAM, 0.6f, WHITE, 0.12f * edge * life01, true);
                }
                prev = p;
            }
        }
    }

    // The inside's own floor: a still, dark mirror of water under everyone, glowing faintly blue and fading out into the
    // depth at its edges, with the black hole and its rings mirrored in it. c is the floor's middle (camera-relative).
    private static void waterFloor(Domain d, Vector3f c, Vector3f hole, float size, float now, float in, float[] haze, float[] ringColor) {
        Vector3f up = new Vector3f(0, 1, 0);
        float r = (float) d.radius();
        Vector3f g = new Vector3f(c).add(0, 0.02f, 0);
        FxDraw.disk(g, up, r * 2.6f, FxDraw.SOFT, 0.05f, haze, 0.16f * in, true);
        FxDraw.disk(g, up, r * 1.2f, FxDraw.SOFT, 0.1f, SILVER, 0.05f * in, true);
        // Slow wide rings of calm water running out from the middle.
        for (int k = 0; k < 4; k++) {
            float u = ((now * 0.004f) + k / 4f) % 1f;
            FxDraw.disk(g, up, r * 2.4f * u, FxDraw.RING, 0.04f, SILVER, 0.08f * in * (1 - u) * Math.min(1, u * 6), true);
        }
        // The hole mirrored in the water: as far below the floor as it is above it.
        Vector3f mirror = new Vector3f(hole.x, 2 * g.y - hole.y, hole.z);
        FxDraw.billboard(mirror, size * 1.3f, 0, FxDraw.SOFT, 0.1f, ringColor, 0.08f * in, true);
        FxDraw.billboard(mirror, size, -now * 0.01f, FxDraw.BLACK_HOLE, 0.27f, ringColor, 0.22f * in, true);
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
            ink(p, s, FxWorld.hash(seed, k, 5) * 6 + u * 0.6f, FxWorld.hash(seed, k, 6), WHITE, 0.9f * fade * in);
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
        Vector3f c = d.hub(camera).subtract(camera).toVector3f();
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

    // One of Stark's ink splashes, picked by pick (0 to 1), facing the camera. The pictures (512 px, smoothed) have a
    // soft halo around the splash, so the quad is drawn larger than the splash. White ink also gets a glow in the bloom.
    private static void ink(Vector3f at, float size, float spin, float pick, float[] rgb, float opacity) {
        if (opacity <= 0.003f) {
            return;
        }
        int i = Math.min(INK.length - 1, Math.max(0, (int) (pick * INK.length)));
        FxDraw.billboard(FxWorld.decal(INK[i]), at, size * 1.25f, spin, rgb, Math.min(1, opacity));
        if (rgb[0] > 0.5f) {
            FxDraw.billboard(at, size * 1.1f, 0, FxDraw.SOFT, 0.2f, SILVER, 0.35f * Math.min(1, opacity), true);
        }
    }

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

    // ---- the white: bubbles of white light popping up everywhere ---------------------------------------------------

    // The white, as in Stark's reference: a bright line of light runs out along the ground with sparkles rising from
    // it, and behind it round bubbles of white pop up everywhere (on the ground, on things, in the sky), swell and merge,
    // until nothing is left but white (fill then washes out what still shows between them, the people stay).
    private static void bubbles(Domain d, Vector3f c, double t, float now, float k) {
        DominioSpec spec = d.spec;
        float r = (float) spec.whiteRadius(t);
        float big = (float) d.radius();
        float spread = (float) Mth.clamp((t - spec.whiteTick()) / Math.max(1, spec.fases().branco() * DominioSpec.WHITE_SPREAD), 0, 1);
        Vector3f up = new Vector3f(0, 1, 0);
        if (spread < 1) {
            // The line of light on the ground, running out ahead of the bubbles.
            float line = 1 - spread * spread;
            FxDraw.disk(new Vector3f(c).add(0, 0.12f, 0), up, (r + 1.5f) * 2, FxDraw.RING, Mth.clamp(1.2f / Math.max(1, r), 0.01f, 0.2f), WHITE,
                    line, true);
            FxDraw.disk(new Vector3f(c).add(0, 0.1f, 0), up, (r + 1.5f) * 2.08f, FxDraw.RING, 0.12f, SILVER, 0.6f * line, true);
        }
        // Sparkles rising where the line has passed.
        for (int n = 0; n < 120; n++) {
            float dist = big * (float) Math.sqrt(FxWorld.hash(951, n, 1));
            double age = t - reachedAt(spec, dist) - 4 * FxWorld.hash(951, n, 2);
            float life = 26 + 20 * FxWorld.hash(951, n, 3);
            if (age < 0 || age > life) {
                continue;
            }
            float u = (float) (age / life);
            float ang = FxWorld.hash(951, n, 4) * Mth.TWO_PI;
            Vector3f p = new Vector3f(c).add(Mth.cos(ang) * dist, 0.4f + u * (3 + 6 * FxWorld.hash(951, n, 5)), Mth.sin(ang) * dist);
            FxDraw.billboard(p, 0.35f + 0.5f * FxWorld.hash(951, n, 6), 0, FxDraw.SOFT, 0.8f, WHITE, k * (1 - u) * Mth.clamp(u * 6, 0, 1), true);
        }
        // The bubbles: soft-edged round discs of white that pop up and swell. Ground ones sit low and spread over the
        // floor as patches; the rest hang everywhere up to the sky.
        int count = 420;
        for (int n = 0; n < count; n++) {
            float dist = big * (float) Math.sqrt(FxWorld.hash(952, n, 1));
            double age = t - reachedAt(spec, dist) - 18 * FxWorld.hash(952, n, 2);
            if (age < 0) {
                continue;
            }
            float grow = (float) Mth.clamp(age / (10 + 10 * FxWorld.hash(952, n, 3)), 0, 1);
            grow = 1 - (1 - grow) * (1 - grow) * (1 - grow);
            float ang = FxWorld.hash(952, n, 4) * Mth.TWO_PI;
            boolean ground = n % 5 < 2;
            float size;
            Vector3f p;
            if (ground) {
                size = (2.5f + 5 * FxWorld.hash(952, n, 5)) * (0.6f + dist / big);
                p = new Vector3f(c).add(Mth.cos(ang) * dist, 0.15f + 0.02f * (n % 7), Mth.sin(ang) * dist);
                FxDraw.disk(p, up, size * 2 * grow, FxDraw.DISC, 0.35f, WHITE, k, false);
                // A bubble sitting on the patch.
                p.add(0, size * 0.35f * grow, 0);
                FxDraw.billboard(p, size * 0.9f * grow, 0, FxDraw.DISC, 0.3f, WHITE, k, false);
            } else {
                float height = 1 + 34 * (float) Math.pow(FxWorld.hash(952, n, 6), 1.3);
                size = (3 + 8 * FxWorld.hash(952, n, 5)) * (0.7f + height / 30);
                float wobble = 0.15f * Mth.sin(now * 0.08f + n);
                p = new Vector3f(c).add(Mth.cos(ang) * dist, height + wobble, Mth.sin(ang) * dist);
                FxDraw.billboard(p, size * grow, 0, FxDraw.DISC, 0.3f, n % 6 == 0 ? MILK : WHITE, k, false);
            }
            // Glow: a soft white halo on each bubble, so the white shines (bloom).
            FxDraw.billboard(p, size * 1.5f * grow, 0, FxDraw.SOFT, 0.35f, WHITE, 0.45f * k, true);
            if (grow < 1) {
                // A bright pop where each one is born.
                FxDraw.billboard(p, size * 0.6f * (1 - grow), 0, FxDraw.SOFT, 0.9f, WHITE, k * (1 - grow), true);
            }
        }
    }

    // When the white's front reaches dist from the center, in ticks since the press.
    private static double reachedAt(DominioSpec spec, double dist) {
        double lo = spec.whiteTick();
        double hi = spec.whiteTick() + Math.max(4, spec.fases().branco() * DominioSpec.WHITE_SPREAD);
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
            double burst = t - spec.tunnelTick() - TUNNEL_FRAMES;
            if (stage == Stage.TUNNEL && burst >= 0 && burst < BURST_TICKS) {
                // After the impact frames the white (the world is all white by now) tears open into the tunnel.
                DomainScreen.whiteBurst(graphics, (float) (burst / BURST_TICKS), t);
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
        // The shockwave goes off the instant the skill is used; the words come after it.
        double first = spec.ondaTick() + 10;
        double second = spec.whiteTick() + 2;
        String text;
        double from;
        double to;
        int color;
        float size;
        if (t >= first && t < spec.whiteTick() - 4) {
            text = "Expansão de Domínio:";
            from = first;
            to = spec.whiteTick() - 4;
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
        // Above the combat HUD (attack name, "Ultimate") so they never overlap.
        pose.translate(graphics.guiWidth() / 2f, graphics.guiHeight() * 0.6f, 0);
        pose.scale(size, size, 1);
        graphics.drawString(font, text, -font.width(text) / 2, 0, (alpha << 24) | color, true);
        pose.popPose();
    }
}
