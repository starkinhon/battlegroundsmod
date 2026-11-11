package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.client.CutsceneCamera;
import com.starkinhon.battlegroundsmod.client.FaceCutscene;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.universe.shenlong.Shen;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// Shenlong's cutscene in the game (universe/shenlong/ShenlongServer starts it): the approved preview
// (forja/shenlong) frame by frame. Its shaders are the preview's (souls_shen_*, generated from glsl.js, and souls_dball
// for the balls); the head, jaw and legs are its baked meshes (ShenlongMesh); the body, spikes and whiskers are rebuilt
// every frame with the same math (universe/shenlong/Shen); the timeline, the camera shots, the lines and the letterbox
// are the preview's. Everything is laid out in the preview's scene space (the summoner at the origin looking +Z) and
// moved to the world by SceneMat. Whoever is near sees the dragon; the summoner also gets the cinema camera, the bars
// and the wish menu (client/ShenlongWishScreen).
public final class ShenlongScene {
    private static int summoner = -1;
    private static Vec3 origin = Vec3.ZERO;
    private static float yaw;
    private static long start, picked;
    private static String wish = "";
    private static double lastT = -1;
    private static boolean camOpen;

    private ShenlongScene() {
    }

    // ---------------- state from the server ----------------

    public static void state(ModPayloads.ShenlongState s) {
        if (s.summoner() < 0) {
            stop();
            return;
        }
        boolean fresh = summoner < 0 || start != s.start();
        summoner = s.summoner();
        origin = new Vec3(s.x(), s.y(), s.z());
        yaw = s.yaw();
        start = s.start();
        wish = s.wish();
        picked = s.picked();
        if (fresh) {
            lastT = -1;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mine() && !camOpen) {
            CutsceneCamera.begin("shenlong");
            camOpen = true;
        }
        if (!wish.isEmpty() && mc.screen instanceof com.starkinhon.battlegroundsmod.client.ShenlongWishScreen) {
            mc.setScreen(null);
        }
    }

    private static void stop() {
        summoner = -1;
        if (camOpen) {
            camOpen = false;
            CutsceneCamera.end("shenlong");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.starkinhon.battlegroundsmod.client.ShenlongWishScreen) {
            mc.setScreen(null);
        }
    }

    public static boolean active() {
        return summoner >= 0;
    }

    // The local player called him: the cinema camera, the bars, the menu.
    public static boolean mine() {
        Minecraft mc = Minecraft.getInstance();
        return summoner >= 0 && mc.player != null && mc.player.getId() == summoner;
    }

    public static boolean cutscene() {
        return mine();
    }

    public static String wish() {
        return wish;
    }

    // The scene's time in seconds: held at the menu until the wish comes.
    public static double time(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }
        double now = mc.level.getGameTime() + partial;
        if (picked > 0 && !wish.isEmpty()) {
            return Math.min(Shen.T_END, Shen.T_PICKED + (now - picked) / 20.0);
        }
        return Math.min(Shen.T_MENU, Math.max(0, (now - start) / 20.0));
    }

    // Client tick: the end, the menu, the sounds, the camera kept in third person.
    public static void tick() {
        if (summoner < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.position().distanceTo(origin) > 400) {
            stop();
            return;
        }
        double t = time(0);
        if (t >= Shen.T_END - 0.01 && !wish.isEmpty()) {
            stop();
            return;
        }
        if (mine()) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (wish.isEmpty() && t >= Shen.T_MENU - 0.01 && !(mc.screen instanceof com.starkinhon.battlegroundsmod.client.ShenlongWishScreen)) {
                mc.setScreen(new com.starkinhon.battlegroundsmod.client.ShenlongWishScreen());
            }
        }
        sounds(lastT, t);
        lastT = t;
    }

    // The scene's sounds, played as the time crosses each moment (the preview is silent: these follow its beats).
    private static void sounds(double a, double b) {
        if (a < 0 || b <= a) {
            return;
        }
        // the heartbeat of the balls, faster and faster
        for (double x = Math.ceil(a * 20) / 20; x < b && x < 5; x += 0.05) {
            double rate = 0.9 + 3.2 * Shen.sm(0, 5, x);
            if (Math.floor(x * rate) != Math.floor((x - 0.05) * rate) && x > 0.05) {
                play(SoundEvents.WARDEN_HEARTBEAT, 1.2f, (float) (0.8 + 0.25 * x / 5));
            }
        }
        for (double[] bolt : Shen.BOLTS) {
            if (a < bolt[0] && b >= bolt[0]) {
                play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.4f, 0.8f + 0.1f * (float) (bolt[1] % 3));
            }
        }
        cross(a, b, 2.0, SoundEvents.WITHER_AMBIENT, 0.4f, 0.5f);
        cross(a, b, 4.95, SoundEvents.BEACON_ACTIVATE, 2f, 0.7f);
        cross(a, b, 4.95, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.5f);
        cross(a, b, 5.6, SoundEvents.ENDER_DRAGON_FLAP, 1.5f, 0.6f);
        cross(a, b, 9.0, SoundEvents.ENDER_DRAGON_FLAP, 1.5f, 0.5f);
        cross(a, b, 13.0, SoundEvents.ENDER_DRAGON_GROWL, 2f, 0.55f);
        cross(a, b, 25.6, SoundEvents.BEACON_DEACTIVATE, 1.6f, 0.6f);
        cross(a, b, 27.0, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 1.2f);
        cross(a, b, 29.2, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.6f, 0.6f);
        cross(a, b, 29.3, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, 1.4f, 0.8f);
    }

    private static void cross(double a, double b, double at, SoundEvent sound, float volume, float pitch) {
        if (a < at && b >= at) {
            play(sound, volume, pitch);
        }
    }

    private static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 p = world(Shen.BALLS);
        mc.level.playLocalSound(p.x, p.y, p.z, sound, SoundSource.AMBIENT, volume, pitch, false);
    }

    // ---------------- scene space ----------------

    private static double[] left() {
        double r = Math.toRadians(yaw);
        return new double[] {Math.cos(r), 0, Math.sin(r)};
    }

    private static double[] fwd() {
        double r = Math.toRadians(yaw);
        return new double[] {-Math.sin(r), 0, Math.cos(r)};
    }

    static Vec3 world(double[] p) {
        double[] l = left(), f = fwd();
        return new Vec3(origin.x + p[0] * l[0] + p[2] * f[0], origin.y + p[1], origin.z + p[0] * l[2] + p[2] * f[2]);
    }

    private static double[] local(Vec3 w) {
        double[] l = left(), f = fwd();
        double dx = w.x - origin.x, dy = w.y - origin.y, dz = w.z - origin.z;
        return new double[] {dx * l[0] + dz * l[2], dy, dx * f[0] + dz * f[2]};
    }

    private static Matrix4f sceneMat(Vec3 cam) {
        double[] l = left(), f = fwd();
        Matrix4f basis = new Matrix4f((float) l[0], 0, (float) l[2], 0, 0, 1, 0, 0, (float) f[0], 0, (float) f[2], 0, 0, 0, 0, 1);
        return new Matrix4f().translation((float) (origin.x - cam.x), (float) (origin.y - cam.y), (float) (origin.z - cam.z)).mul(basis);
    }

    private static Matrix4f sceneRot() {
        double[] l = left(), f = fwd();
        return new Matrix4f((float) l[0], 0, (float) l[2], 0, 0, 1, 0, 0, (float) f[0], 0, (float) f[2], 0, 0, 0, 0, 1);
    }

    // ---------------- the head: where it is, which way it looks (the preview's updateHead) ----------------

    private record Head(double[] pos, double[] x, double[] up, double[] f) {
        Matrix4f matrix() {
            return new Matrix4f((float) x[0], (float) x[1], (float) x[2], 0, (float) up[0], (float) up[1], (float) up[2], 0,
                    (float) f[0], (float) f[1], (float) f[2], 0, (float) pos[0], (float) pos[1], (float) pos[2], 1);
        }
    }

    private static Head head(double headArc, double time, double look, double settle) {
        Shen.Frame fr = Shen.frame(headArc);
        double[] neck = fr.p();
        double[] F = fr.t();
        double[] toEye = Shen.norm(Shen.sub(Shen.EYE, neck));
        F = Shen.norm(Shen.lerp(F, toEye, look * 0.85));
        double[] up = Shen.sub(new double[] {0, 1, 0}, Shen.mul(F, F[1]));
        up = Shen.norm(Shen.lerp(Shen.norm(fr.n()), Shen.norm(up), Math.max(look, Shen.sm(Shen.HELIX_END, Shen.L, headArc))));
        double[] xs = Shen.cross(up, F);
        double[] bob = {Math.sin(time * 0.8) * 0.25 * settle, Math.sin(time * 1.1 + 1) * 0.2 * settle};
        double[] pos = Shen.add(Shen.add(neck, Shen.mul(F, 1.2 * Shen.HEAD_S)), Shen.add(Shen.mul(xs, bob[0]), Shen.mul(up, bob[1])));
        return new Head(pos, xs, up, F);
    }

    // ---------------- the camera (the preview's cinema shots) ----------------

    private static double[] cinema(double t, double[] headPos) {
        double[] p, l;
        if (t < 4.6) {
            p = Shen.lerp(new double[] {2.6, 1.7, -1.6}, new double[] {3.0, 1.3, -2.6}, Shen.sm(0, 4.6, t));
            l = Shen.lerp(new double[] {0, 0.3, 5}, new double[] {0, 12, 12}, Shen.sm(2.2, 4.6, t));
        } else if (t < 12.4) {
            p = Shen.lerp(new double[] {3.5, 1.2, -9}, new double[] {-8, 1.0, -16}, Shen.sm(4.6, 12.4, t));
            l = Shen.lerp(new double[] {0, 6, 6}, Shen.lerp(new double[] {0, 6, 6}, headPos, 0.8), Shen.sm(5.6, 7.6, t));
        } else if (t < 14.8) {
            p = Shen.lerp(new double[] {46, 8, -30}, new double[] {42, 13, -27}, Shen.sm(12.4, 14.8, t));
            l = new double[] {0, 26, 9};
        } else if (t < 22.3) {
            p = Shen.lerp(new double[] {0.8, 1.3, -1.2}, new double[] {0.25, 1.62, -0.25}, Shen.sm(14.8, 17, t));
            l = Shen.lerp(headPos, Shen.add(headPos, new double[] {0, -1.5, 0}), Shen.sm(15, 22, t));
        } else if (t < 25.6) {
            p = new double[] {0, 1.62, 0};
            l = headPos;
        } else if (t < 27.2) {
            p = Shen.lerp(new double[] {46, 10, -36}, new double[] {40, 16, -30}, Shen.sm(25.6, 27.2, t));
            l = new double[] {0, 24, 8};
        } else if (t < 29.3) {
            p = Shen.lerp(new double[] {2.2, 1.0, -4.5}, new double[] {1.2, 1.3, -3.4}, Shen.sm(27.2, 29.3, t));
            double[] c = {0, 0, 0};
            for (int i = 0; i < 7; i++) {
                c = Shen.add(c, Shen.ballAt(i, Math.min(t, 29.2 + 0.9)));
            }
            l = Shen.mul(c, 1 / 7.0);
        } else {
            double y = 0;
            for (int i = 0; i < 7; i++) {
                y += Shen.ballAt(i, Math.min(t, 31.6))[1] / 7;
            }
            p = Shen.lerp(new double[] {0, 14, -40}, new double[] {0, 22, -52}, Shen.sm(29.3, 33, t));
            l = Shen.lerp(new double[] {0, y * 0.85, 8}, new double[] {0, 30, 60}, Shen.sm(32, 34, t));
        }
        return new double[] {p[0], p[1], p[2], l[0], l[1], l[2]};
    }

    private static double[] lastCam;

    // The camera of the summoner (CameraMixin), or null.
    public static FaceCutscene.Shot shot(float partial) {
        if (!mine()) {
            return null;
        }
        double t = time(partial);
        double[] headPos = Shen.dragonVisible(t)
                ? head(Shen.headArc(t), t, Shen.look(t), Shen.settle(t)).pos()
                : new double[] {0, 30, 15};
        double[] c = cinema(t, headPos);
        lastCam = c;
        Vec3 p = world(new double[] {c[0], c[1], c[2]}), at = world(new double[] {c[3], c[4], c[5]});
        Vec3 d = at.subtract(p).normalize();
        float yRot = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float xRot = (float) -Math.toDegrees(Math.asin(Mth.clamp(d.y, -1, 1)));
        return new FaceCutscene.Shot(p, yRot, xRot);
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (mine()) {
            // the preview's 60-degree lens
            event.setFOV(60);
        }
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!mine()) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    // The summoner's own body hides when the camera is inside it (the preview hides the silhouette the same way).
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!mine() || event.getEntity().getId() != summoner || lastCam == null) {
            return;
        }
        if (Math.hypot(lastCam[0], lastCam[2]) <= 1.6 && lastCam[1] <= 4) {
            event.setCanceled(true);
        }
    }

    // ---------------- the GUI: bars and the lines ----------------

    public static void renderGui(GuiGraphics g, DeltaTracker delta) {
        if (summoner < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double t = time(delta.getGameTimeDeltaPartialTick(false));
        int w = g.guiWidth(), h = g.guiHeight();
        if (mine()) {
            int bar = (int) Math.round(Shen.letterbox(t) * 0.09 * h);
            if (bar > 0) {
                g.fill(0, 0, w, bar, 0xFF000000);
                g.fill(0, h - bar, w, h, 0xFF000000);
            }
        }
        Component line = speech(t);
        if (line == null) {
            return;
        }
        // the preview's lettering: big, pale green with a dark green outline and a green glow
        Font font = mc.font;
        float scale = Math.max(1.5f, Math.min(3f, w / 260f));
        int maxW = (int) ((w * 0.88) / scale);
        List<FormattedCharSequence> rows = font.split(line, maxW);
        float y0 = h * 0.89f - rows.size() * (font.lineHeight + 2) * scale;
        g.pose().pushPose();
        g.pose().translate(w / 2f, y0, 0);
        g.pose().scale(scale, scale, 1);
        int y = 0;
        for (FormattedCharSequence row : rows) {
            int x = -font.width(row) / 2;
            for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}}) {
                g.drawString(font, row, x + o[0], y + o[1], 0xFF06210F, false);
            }
            g.drawString(font, row, x, y, 0xFFD8FFD9, false);
            y += font.lineHeight + 2;
        }
        g.pose().popPose();
    }

    private static Component speech(double t) {
        if (t >= Shen.LINES[0][0] && t < Shen.LINES[0][1]) {
            return Component.translatable("shenlong.battlegroundsmod.fala.1");
        }
        if (t >= Shen.LINES[1][0] && t < Shen.LINES[1][1]) {
            return Component.translatable("shenlong.battlegroundsmod.fala.2");
        }
        if (t >= 22.3 && t < 25.0 && !wish.isEmpty()) {
            return Component.translatable("shenlong.battlegroundsmod.desejo." + wish + ".resposta");
        }
        if (t >= 25.0 && t < 26.6) {
            return Component.translatable("shenlong.battlegroundsmod.adeus");
        }
        return null;
    }

    // ---------------- fog: the storm closes in (the preview's uFog) ----------------

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (summoner < 0) {
            return;
        }
        double s = Shen.storm(time((float) event.getPartialTick()));
        if (s <= 0) {
            return;
        }
        event.setRed((float) Mth.lerp(s, event.getRed(), 0.10));
        event.setGreen((float) Mth.lerp(s, event.getGreen(), 0.16));
        event.setBlue((float) Mth.lerp(s, event.getBlue(), 0.12));
    }

    // ---------------- drawing ----------------

    private static boolean broken;

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (summoner < 0 || broken || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || FxShaders.shenBody == null) {
            return;
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            draw(event);
        } catch (RuntimeException e) {
            broken = true;
            com.starkinhon.battlegroundsmod.BattlegroundsMod.LOGGER.error("Drawing Shenlong failed; the cutscene stops drawing", e);
        } finally {
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            VertexBuffer.unbind();
        }
    }

    // the per-frame uniforms of the creature (the preview's setTime)
    private static final class U {
        double storm, flash, ballPow, emerge, dissolve, eye;
        double[] bolt = {0, 1, 0};
    }

    private static void draw(RenderLevelStageEvent event) {
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = time(partial);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f sceneMat = sceneMat(cam);
        double[] camL = local(cam);
        Matrix4f view = RenderSystem.getModelViewMatrix(), proj = RenderSystem.getProjectionMatrix();

        U u = new U();
        u.storm = Shen.storm(t);
        for (double[] b : Shen.BOLTS) {
            double life = Shen.boltLife(t, b[0]);
            if (life > u.flash) {
                u.flash = life;
                List<double[]> segs = boltSegs(b);
                u.bolt = Shen.norm(new double[] {segs.get(0)[0], segs.get(0)[1], segs.get(0)[2]});
            }
        }
        u.ballPow = Shen.ballPow(t);
        u.emerge = Shen.emerge(t);
        u.dissolve = Shen.dissolve(t);
        u.eye = Shen.eye(t);

        // 1. the world goes dark under the storm (the preview's ground: x mix(1, (0.32, 0.38, 0.42), storm))
        if (u.storm > 0.001) {
            screen(1 + (0.32 - 1) * u.storm, 1 + (0.38 - 1) * u.storm, 1 + (0.42 - 1) * u.storm, true);
        }
        if (u.flash > 0.001) {
            screen(0.7 * u.flash * 0.22, 0.78 * u.flash * 0.22, 1.0 * u.flash * 0.22, false);
        }
        // 2. the storm sky over the game's sky
        if (u.storm > 0.001 && FxShaders.shenSky != null) {
            sky(t, u, view, proj);
        }
        // 3. the balls' glow and the lightning on the ground
        if (FxShaders.shenGround != null && (u.ballPow > 0.001 || u.flash > 0.001)) {
            ground(sceneMat, camL, u);
        }
        // 4. the dragon, then the balls
        if (Shen.dragonVisible(t)) {
            dragon(t, sceneMat, camL, u, view, proj);
        }
        balls(t, cam, camL, sceneMat, u);
        // 5. light: the eyes, the halos, the pillar, the lightning, the trails
        additive();
        if (Shen.dragonVisible(t)) {
            eyes(t, sceneMat, camL, u);
        }
        halos(t, sceneMat, camL);
        pillar(t, sceneMat, camL);
        bolts(t, sceneMat, camL);
        trails(t, sceneMat, camL);
    }

    private static void screen(double r, double g, double b, boolean multiply) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        if (multiply) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
        } else {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float fr = (float) r, fg = (float) g, fb = (float) b;
        buf.addVertex(-1, -1, 0).setColor(fr, fg, fb, 1f);
        buf.addVertex(1, -1, 0).setColor(fr, fg, fb, 1f);
        buf.addVertex(1, 1, 0).setColor(fr, fg, fb, 1f);
        buf.addVertex(-1, 1, 0).setColor(fr, fg, fb, 1f);
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.restoreProjectionMatrix();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void sky(double t, U u, Matrix4f view, Matrix4f proj) {
        ShaderInstance sh = FxShaders.shenSky;
        sh.safeGetUniform("SceneRot").set(sceneRot());
        sh.safeGetUniform("CamL").set(0f, 0f, 0f);
        sh.safeGetUniform("uStorm").set((float) u.storm);
        sh.safeGetUniform("uFlash").set((float) u.flash);
        sh.safeGetUniform("uTime").set((float) t);
        sh.safeGetUniform("uBolt").set((float) u.bolt[0], (float) u.bolt[1], (float) u.bolt[2]);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> sh);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        int la = 24, lo = 48;
        float r = 400;
        for (int i = 0; i < la; i++) {
            for (int j = 0; j < lo; j++) {
                for (int[] c : new int[][] {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}}) {
                    double th = Math.PI * c[0] / la, ph = 2 * Math.PI * c[1] / lo;
                    b.addVertex((float) (Math.sin(th) * Math.cos(ph) * r), (float) (Math.cos(th) * r), (float) (Math.sin(th) * Math.sin(ph) * r));
                }
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private static void ground(Matrix4f sceneMat, double[] camL, U u) {
        ShaderInstance sh = FxShaders.shenGround;
        sh.safeGetUniform("SceneMat").set(sceneMat);
        sh.safeGetUniform("CamL").set((float) camL[0], (float) camL[1], (float) camL[2]);
        sh.safeGetUniform("uBall").set((float) Shen.BALLS[0], (float) Shen.BALLS[1], (float) Shen.BALLS[2]);
        sh.safeGetUniform("uBallPow").set((float) u.ballPow);
        sh.safeGetUniform("uStorm").set((float) u.storm);
        sh.safeGetUniform("uFlash").set((float) (u.flash * 0.9));
        sh.safeGetUniform("uFog").set(0.1f, 0.16f, 0.12f);
        sh.safeGetUniform("uFogK").set((float) (0.0025 + 0.004 * u.storm));
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(() -> sh);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        int n = 40;
        double size = 60, y = 0.04;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double x0 = Shen.BALLS[0] - size + 2 * size * i / n, x1 = Shen.BALLS[0] - size + 2 * size * (i + 1) / n;
                double z0 = Shen.BALLS[2] - size + 2 * size * j / n, z1 = Shen.BALLS[2] - size + 2 * size * (j + 1) / n;
                b.addVertex((float) x0, (float) y, (float) z0);
                b.addVertex((float) x0, (float) y, (float) z1);
                b.addVertex((float) x1, (float) y, (float) z1);
                b.addVertex((float) x1, (float) y, (float) z0);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private static void creatureUniforms(ShaderInstance sh, double t, Matrix4f sceneMat, double[] camL, U u) {
        sh.safeGetUniform("SceneMat").set(sceneMat);
        sh.safeGetUniform("CamL").set((float) camL[0], (float) camL[1], (float) camL[2]);
        Vector3f sun = new Vector3f(-0.35f, 0.8f, -0.45f).normalize();
        sh.safeGetUniform("uSun").set(sun.x, sun.y, sun.z);
        double s = u.storm;
        sh.safeGetUniform("uSunCol").set((float) (0.95 - 0.7 * s), (float) (0.95 - 0.68 * s), (float) (0.9 - 0.6 * s));
        sh.safeGetUniform("uAmb").set((float) (0.42 - 0.25 * s), (float) (0.46 - 0.25 * s), (float) (0.5 - 0.22 * s));
        sh.safeGetUniform("uFog").set((float) (0.66 - 0.56 * s), (float) (0.78 - 0.62 * s), (float) (0.92 - 0.8 * s));
        sh.safeGetUniform("uFogK").set((float) (0.0025 + 0.004 * s));
        sh.safeGetUniform("uBall").set((float) Shen.BALLS[0], (float) Shen.BALLS[1], (float) Shen.BALLS[2]);
        sh.safeGetUniform("uBallPow").set((float) u.ballPow);
        sh.safeGetUniform("uFlash").set((float) (u.flash * 0.9));
        sh.safeGetUniform("uEmerge").set((float) u.emerge);
        sh.safeGetUniform("uDissolve").set((float) u.dissolve);
        sh.safeGetUniform("uArcMax").set((float) Shen.BODY_L);
        sh.safeGetUniform("uTime").set((float) t);
        sh.safeGetUniform("uEye").set((float) u.eye);
    }

    private static void dragon(double t, Matrix4f sceneMat, double[] camL, U u, Matrix4f view, Matrix4f proj) {
        ShaderInstance sh = FxShaders.shenBody;
        creatureUniforms(sh, t, sceneMat, camL, u);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        double headArc = Shen.headArc(t), settle = Shen.settle(t);
        // the parts rebuilt every frame: body, spikes, whiskers (scene space, arc in the vertex)
        sh.safeGetUniform("ModelMat").set(new Matrix4f());
        sh.safeGetUniform("ArcFix").set(-1000f);
        sh.safeGetUniform("Mirror").set(1f);
        RenderSystem.setShader(() -> sh);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        body(b, headArc, t, settle);
        spikes(b, headArc, t, settle);
        Head hd = head(headArc, t, Shen.look(t), settle);
        Matrix4f headScaled = hd.matrix().scale((float) Shen.HEAD_S);
        whiskers(b, headScaled, headArc, t);
        MeshData mesh = b.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        // the sculpted parts, from their buffers
        part(sh, ShenlongMesh.HEAD, headScaled, headArc + 2, 1, view, proj);
        Matrix4f jaw = new Matrix4f(headScaled).translate(0, -0.15f, -0.35f).rotateX((float) Shen.jaw(t)).translate(0, 0.15f, 0.35f);
        part(sh, ShenlongMesh.JAW, jaw, headArc + 2, 1, view, proj);
        for (int i = 0; i < 4; i++) {
            double d = Shen.LEG_AT[i][0], side = Shen.LEG_AT[i][1];
            double a = headArc - d;
            if (a <= 1) {
                continue;
            }
            Shen.Frame f = Shen.frame(a);
            double r = Shen.radius(d);
            double[] sw = Shen.sway(a, d, t, settle);
            double[] c = Shen.add(f.p(), Shen.add(Shen.mul(f.b(), sw[0]), Shen.mul(f.n(), sw[1])));
            double[] sideDir = Shen.mul(f.b(), side);
            double[] sh0 = Shen.add(Shen.add(c, Shen.mul(sideDir, r * 0.82)), Shen.mul(f.n(), -r * 0.25));
            double[] X = sideDir, Y = f.n(), Z = Shen.cross(X, Y);
            boolean mirror = Shen.dot(Z, f.t()) < 0;
            Matrix4f m = new Matrix4f((float) X[0], (float) X[1], (float) X[2], 0, (float) Y[0], (float) Y[1], (float) Y[2], 0,
                    (float) Z[0], (float) Z[1], (float) Z[2], 0, (float) sh0[0], (float) sh0[1], (float) sh0[2], 1);
            if (mirror) {
                m.scale(1, 1, -1);
            }
            part(sh, ShenlongMesh.LEG0 + i, m, a, mirror ? -1 : 1, view, proj);
        }
    }

    private static void part(ShaderInstance sh, int i, Matrix4f model, double arc, float mirror, Matrix4f view, Matrix4f proj) {
        VertexBuffer vb = ShenlongMesh.part(i);
        if (vb == null) {
            return;
        }
        sh.safeGetUniform("ModelMat").set(model);
        sh.safeGetUniform("ArcFix").set((float) arc);
        sh.safeGetUniform("Mirror").set(mirror);
        vb.bind();
        vb.drawWithShader(view, proj, sh);
        VertexBuffer.unbind();
    }

    // one vertex of the creature: aP, region, arc, radius (see souls_shen_body.vsh)
    private static void v(BufferBuilder b, double[] q, double[] n, double apx, double apy, double apz, int reg, double r, double arc) {
        b.addVertex((float) q[0], (float) q[1], (float) q[2]).setColor(0f, reg / 15f, 0f, 1f).setUv((float) apx, (float) apy)
                .setUv1((int) Math.round(Mth.clamp(arc * 64, -32000, 32000)), 0)
                .setUv2((int) Math.round(Mth.clamp(apz * 1000, -32000, 32000)), (int) Math.round(Mth.clamp(r * 1000, 0, 32000)))
                .setNormal((float) n[0], (float) n[1], (float) n[2]);
    }

    private static void body(BufferBuilder b, double headArc, double time, double settle) {
        int rings = Shen.RINGS, sides = Shen.SIDES;
        double step = Shen.BODY_L / (rings - 1);
        double[][] prevQ = null, prevN = null;
        double[] prevAp = null;
        double prevR = 0, prevA = 0, prevD = 0;
        for (int i = 0; i < rings; i++) {
            double d = i * step, a = headArc - d;
            if (a < -0.5) {
                break;
            }
            Shen.Frame f = Shen.frame(Math.max(0, a));
            double r = Shen.radius(d);
            double[] sw = Shen.sway(a, d, time, settle);
            double[] c = Shen.add(f.p(), Shen.add(Shen.mul(f.b(), sw[0]), Shen.mul(f.n(), sw[1])));
            double[][] q = new double[sides + 1][], nn = new double[sides + 1][];
            for (int s = 0; s <= sides; s++) {
                double th = (double) s / sides * Math.PI * 2, ct = Math.cos(th), st = Math.sin(th);
                double rr = r * (1 - 0.14 * Math.max(0, -ct)) * (1 + 0.05 * Math.max(0, ct));
                double[] dir = Shen.add(Shen.mul(f.n(), ct), Shen.mul(f.b(), st));
                q[s] = Shen.add(c, Shen.mul(dir, rr));
                nn[s] = dir;
            }
            if (prevQ != null) {
                for (int s = 0; s < sides; s++) {
                    // the preview's (a, b, a+1) (a+1, b, b+1): a = the ring before, b = this ring
                    bodyV(b, prevQ[s], prevN[s], prevD, s, prevR, prevA);
                    bodyV(b, q[s], nn[s], d, s, r, a);
                    bodyV(b, q[s + 1], nn[s + 1], d, s + 1, r, a);
                    bodyV(b, prevQ[s + 1], prevN[s + 1], prevD, s + 1, prevR, prevA);
                }
            }
            prevQ = q;
            prevN = nn;
            prevR = r;
            prevA = a;
            prevD = d;
        }
    }

    private static void bodyV(BufferBuilder b, double[] q, double[] n, double d, int s, double r, double a) {
        double th = (double) s / Shen.SIDES * Math.PI * 2;
        v(b, q, n, Shen.BODY_L - d, th * r, (double) s / Shen.SIDES, 0, r, a);
    }

    private static void spikes(BufferBuilder b, double headArc, double time, double settle) {
        for (Shen.Spike sp : Shen.SPIKES) {
            double a = headArc - sp.d();
            if (a < 0.5) {
                continue;
            }
            Shen.Frame f = Shen.frame(a);
            double r = Shen.radius(sp.d());
            double[] sw = Shen.sway(a, sp.d(), time, settle);
            double[] c = Shen.add(f.p(), Shen.add(Shen.mul(f.b(), sw[0]), Shen.mul(f.n(), sw[1])));
            double[] base = Shen.add(c, Shen.mul(f.n(), r * 0.9));
            double[] front = Shen.add(base, Shen.mul(f.t(), sp.w() * 0.5)), back = Shen.sub(base, Shen.mul(f.t(), sp.w() * 0.5));
            double[] tip = Shen.add(Shen.add(base, Shen.mul(f.n(), sp.h())), Shen.mul(f.t(), -sp.w() * sp.lean()));
            double th = 0.06 + r * 0.04;
            double[][] p = {Shen.add(front, Shen.mul(f.b(), th)), Shen.add(back, Shen.mul(f.b(), th)), tip,
                    Shen.sub(front, Shen.mul(f.b(), th)), Shen.sub(back, Shen.mul(f.b(), th)), Shen.add(tip, new double[] {0, 0.0001, 0})};
            double[] fr = {0, 0, 1, 0, 0, 1};
            double[] nA = Shen.add(Shen.mul(f.b(), 0.8), Shen.mul(f.n(), 0.2)), nB = Shen.add(Shen.mul(f.b(), -0.8), Shen.mul(f.n(), 0.2));
            // the preview's 8 triangles, each as a quad with its last corner doubled
            int[][] tris = {{0, 2, 1}, {3, 4, 5}, {0, 1, 4}, {0, 4, 3}, {1, 2, 5}, {1, 5, 4}, {2, 0, 3}, {2, 3, 5}};
            for (int[] tri : tris) {
                for (int k : new int[] {tri[0], tri[1], tri[2], tri[2]}) {
                    v(b, p[k], k < 3 ? nA : nB, 0, 0, fr[k], 6, r, a);
                }
            }
        }
    }

    private static final double[][] WH = {{1, 12.5, 0, 1.0}, {-1, 11.2, 1.7, 0.83}};
    private static final int WSEG = 60, WS = 8;

    private static void whiskers(BufferBuilder b, Matrix4f headScaled, double headArc, double time) {
        for (double[] w : WH) {
            double side = w[0], len = w[1], ph = w[2], fq = w[3];
            double[][] pts = new double[WSEG + 1][];
            for (int i = 0; i <= WSEG; i++) {
                double t = (double) i / WSEG, l = len * t;
                Vector3f lv = new Vector3f(
                        (float) (side * (0.75 + 2.2 * Math.sin(Math.min(1, t * 2.2) * Math.PI / 2) + Math.sin(t * 5 - time * 2.1 * fq + ph) * 1.6 * t)),
                        (float) (0.72 - 2.6 * t * t + Math.sin(t * 6.5 - time * 1.7 * fq + ph * 0.7) * 1.4 * t),
                        (float) (4.4 + 1.0 * Math.sin(t * Math.PI) - l * 0.62));
                headScaled.transformPosition(lv);
                pts[i] = new double[] {lv.x, lv.y, lv.z};
            }
            double[][][] ringQ = new double[WSEG + 1][WS + 1][], ringN = new double[WSEG + 1][WS + 1][];
            for (int i = 0; i <= WSEG; i++) {
                double t = (double) i / WSEG;
                double[] T = Shen.norm(Shen.sub(pts[Math.min(WSEG, i + 1)], pts[Math.max(0, i - 1)]));
                double[] N = Shen.norm(Shen.cross(T, new double[] {0, 1, 0}));
                if (!Double.isFinite(N[0])) {
                    N = new double[] {1, 0, 0};
                }
                double[] B = Shen.cross(T, N);
                double r = (0.17 * (1 - t) + 0.02) * Shen.HEAD_S;
                for (int s = 0; s <= WS; s++) {
                    double th = (double) s / WS * Math.PI * 2;
                    double[] dir = Shen.add(Shen.mul(N, Math.cos(th)), Shen.mul(B, Math.sin(th)));
                    ringQ[i][s] = Shen.add(pts[i], Shen.mul(dir, r));
                    ringN[i][s] = dir;
                }
            }
            for (int i = 0; i < WSEG; i++) {
                for (int s = 0; s < WS; s++) {
                    int[][] c = {{i, s}, {i + 1, s}, {i + 1, s + 1}, {i, s + 1}};
                    for (int[] k : c) {
                        v(b, ringQ[k[0]][k[1]], ringN[k[0]][k[1]], 0, 0, (double) k[0] / WSEG, 6, 1, headArc + 2);
                    }
                }
            }
        }
    }

    // ---------------- the balls (souls_dball, the inventory's ball) ----------------

    private static void balls(double t, Vec3 cam, double[] camL, Matrix4f sceneMat, U u) {
        ShaderInstance sh = FxShaders.dball;
        if (sh == null) {
            return;
        }
        double pulse = Shen.pulse(t), stone = Shen.stone(t);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        sh.safeGetUniform("Pulse").set((float) pulse);
        sh.safeGetUniform("Stone").set((float) stone);
        RenderSystem.setShader(() -> sh);
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        for (int i = 0; i < 7; i++) {
            if (t > 29.2 && t - 29.2 > 2.6) {
                continue;
            }
            double[] p = Shen.ballAt(i, t);
            double fly = t > 29.2 ? 1 - Shen.sm(1.8, 2.6, t - 29.2) : 0;
            double rad = 0.36 * (1 + fly * 1.6);
            double spin = t * 0.6 + i;
            Matrix4f rot = new Matrix4f().rotateXYZ(0.3f, (float) spin, 0f);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            int la = 16, lo = 24;
            for (int a = 0; a < la; a++) {
                for (int c = 0; c < lo; c++) {
                    for (int[] k : new int[][] {{a, c}, {a + 1, c}, {a + 1, c + 1}, {a, c + 1}}) {
                        double th = Math.PI * k[0] / la, ph = 2 * Math.PI * k[1] / lo;
                        float nx = (float) (Math.sin(th) * Math.sin(ph)), ny = (float) Math.cos(th), nz = (float) (Math.sin(th) * Math.cos(ph));
                        Vector3f n = rot.transformDirection(new Vector3f(nx, ny, nz));
                        Vector3f wp = sceneMat.transformPosition(new Vector3f((float) (p[0] + n.x * rad), (float) (p[1] + n.y * rad), (float) (p[2] + n.z * rad)));
                        Vector3f wn = sceneMat.transformDirection(new Vector3f(n));
                        b.addVertex(wp.x, wp.y, wp.z).setColor((nx + 1) * 0.5f, (ny + 1) * 0.5f, (nz + 1) * 0.5f, (i + 1) / 7f).setUv(0, 0)
                                .setUv1(0, 0).setUv2(240, 240).setNormal(wn.x, wn.y, wn.z);
                    }
                }
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.disableCull();
    }

    // ---------------- light (additive) ----------------

    private static void additive() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
    }

    private static void glowUniforms(ShaderInstance sh, Matrix4f sceneMat, double[] camL, float r, float g, float b, double pow, double mode, double t,
            boolean backFaces) {
        sh.safeGetUniform("SceneMat").set(sceneMat);
        sh.safeGetUniform("ModelMat").set(new Matrix4f());
        sh.safeGetUniform("CamL").set((float) camL[0], (float) camL[1], (float) camL[2]);
        sh.safeGetUniform("uCol").set(r, g, b);
        sh.safeGetUniform("uPow").set((float) pow);
        sh.safeGetUniform("uMode").set((float) mode);
        sh.safeGetUniform("uTime").set((float) t);
        if (backFaces) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.enableCull();
        }
        RenderSystem.setShader(() -> sh);
    }

    // a sphere in scene space (only the faces toward the camera: the preview's FrontSide)
    private static void sphere(BufferBuilder b, Matrix4f m, double r, int la, int lo) {
        for (int a = 0; a < la; a++) {
            for (int c = 0; c < lo; c++) {
                for (int[] k : new int[][] {{a, c}, {a + 1, c}, {a + 1, c + 1}, {a, c + 1}}) {
                    double th = Math.PI * k[0] / la, ph = 2 * Math.PI * k[1] / lo;
                    float nx = (float) (Math.sin(th) * Math.sin(ph)), ny = (float) Math.cos(th), nz = (float) (Math.sin(th) * Math.cos(ph));
                    Vector3f p = m.transformPosition(new Vector3f((float) (nx * r), (float) (ny * r), (float) (nz * r)));
                    Vector3f n = m.transformDirection(new Vector3f(nx, ny, nz)).normalize();
                    b.addVertex(p.x, p.y, p.z).setColor(1f, 1f, 1f, 1f).setUv((float) k[1] / lo, 1 - (float) k[0] / la).setUv1(0, 0).setUv2(0, 0)
                            .setNormal(n.x, n.y, n.z);
                }
            }
        }
    }

    private static void eyes(double t, Matrix4f sceneMat, double[] camL, U u) {
        ShaderInstance sh = FxShaders.shenGlow;
        if (sh == null) {
            return;
        }
        Head hd = head(Shen.headArc(t), t, Shen.look(t), Shen.settle(t));
        Matrix4f headScaled = hd.matrix().scale((float) Shen.HEAD_S);
        glowUniforms(sh, sceneMat, camL, 1f, 0.2f, 0.05f, u.eye * 0.7, 0, t, false);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        for (int s : new int[] {1, -1}) {
            Matrix4f m = new Matrix4f(headScaled).translate(0.86f * s, 1.24f, 1.78f).scale(1.25f, 0.6f, 1f);
            sphere(b, m, 0.42, 16, 24);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void halos(double t, Matrix4f sceneMat, double[] camL) {
        ShaderInstance sh = FxShaders.shenGlow;
        if (sh == null) {
            return;
        }
        double pulse = Shen.pulse(t);
        for (int i = 0; i < 7; i++) {
            if (t > 29.2 && t - 29.2 > 2.6) {
                continue;
            }
            double fly = t > 29.2 ? 1 - Shen.sm(1.8, 2.6, t - 29.2) : 0;
            double[] p = Shen.ballAt(i, t);
            glowUniforms(sh, sceneMat, camL, 1f, 0.62f, 0.16f, 0.15 + pulse * 0.7 + fly * 1.6, 0, t, false);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            Matrix4f m = new Matrix4f().translation((float) p[0], (float) p[1], (float) p[2]).scale((float) (1 + fly * 1.6));
            sphere(b, m, 0.7, 12, 18);
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
    }

    private static void pillar(double t, Matrix4f sceneMat, double[] camL) {
        ShaderInstance sh = FxShaders.shenGlow;
        double pil = Shen.pillar(t);
        if (sh == null || pil <= 0.01) {
            return;
        }
        double grow = Math.max(0.01, Shen.pillarGrow(t));
        for (int k = 0; k < 2; k++) {
            float rs = k == 0 ? 1 : 0.45f;
            glowUniforms(sh, sceneMat, camL, k == 0 ? 1f : 1f, k == 0 ? 0.72f : 0.95f, k == 0 ? 0.25f : 0.75f, pil * (k == 1 ? 1.4 : 0.9), 1, t, false);
            Matrix4f m = new Matrix4f().translation((float) Shen.BALLS[0], (float) Shen.BALLS[1], (float) Shen.BALLS[2])
                    .scale((float) (1 + 0.4 * (1 - pil)), (float) grow, (float) (1 + 0.4 * (1 - pil)));
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            // the preview's CylinderGeometry(2.6, 1.3, 140, 48, 30, open), standing on the balls, uv.y 1 at the top
            int rad = 48, hs = 30;
            double rt = 2.6 * rs, rb = 1.3 * rs, hgt = 140, slope = (rb - rt) / hgt;
            for (int y = 0; y < hs; y++) {
                for (int x = 0; x < rad; x++) {
                    for (int[] c : new int[][] {{x, y}, {x, y + 1}, {x + 1, y + 1}, {x + 1, y}}) {
                        double v = (double) c[1] / hs, th = (double) c[0] / rad * Math.PI * 2;
                        double r = rt + (rb - rt) * v;
                        float px = (float) (r * Math.sin(th)), pz = (float) (r * Math.cos(th)), py = (float) (hgt * (1 - v));
                        Vector3f p = m.transformPosition(new Vector3f(px, py, pz));
                        Vector3f n = new Vector3f((float) Math.sin(th), (float) slope, (float) Math.cos(th)).normalize();
                        b.addVertex(p.x, p.y, p.z).setColor(1f, 1f, 1f, 1f).setUv((float) c[0] / rad, (float) (1 - v)).setUv1(0, 0).setUv2(0, 0)
                                .setNormal(n.x, n.y, n.z);
                    }
                }
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
    }

    private static final List<List<double[]>> BOLT_SEGS = new ArrayList<>();

    private static List<double[]> boltSegs(double[] bolt) {
        if (BOLT_SEGS.isEmpty()) {
            for (double[] b : Shen.BOLTS) {
                BOLT_SEGS.add(Shen.boltPath((int) b[1], new double[] {b[2], b[3], b[4]}));
            }
        }
        for (int i = 0; i < Shen.BOLTS.length; i++) {
            if (Shen.BOLTS[i] == bolt) {
                return BOLT_SEGS.get(i);
            }
        }
        return BOLT_SEGS.get(0);
    }

    private static void ribbonQuad(BufferBuilder b, double[] a, double[] c, double w, double[] camL, float alpha) {
        double[] mid = Shen.lerp(a, c, 0.5), dir = Shen.norm(Shen.sub(c, a)), toC = Shen.norm(Shen.sub(camL, mid));
        double[] side = Shen.mul(Shen.norm(Shen.cross(dir, toC)), w);
        if (!Double.isFinite(side[0])) {
            return;
        }
        double[] q0 = Shen.sub(a, side), q1 = Shen.add(a, side), q2 = Shen.sub(c, side), q3 = Shen.add(c, side);
        b.addVertex((float) q0[0], (float) q0[1], (float) q0[2]).setUv(-1, alpha);
        b.addVertex((float) q1[0], (float) q1[1], (float) q1[2]).setUv(1, alpha);
        b.addVertex((float) q3[0], (float) q3[1], (float) q3[2]).setUv(1, alpha);
        b.addVertex((float) q2[0], (float) q2[1], (float) q2[2]).setUv(-1, alpha);
    }

    private static void bolts(double t, Matrix4f sceneMat, double[] camL) {
        ShaderInstance sh = FxShaders.shenBolt;
        if (sh == null) {
            return;
        }
        for (double[] bolt : Shen.BOLTS) {
            float life = (float) Shen.boltLife(t, bolt[0]);
            if (life <= 0) {
                continue;
            }
            sh.safeGetUniform("SceneMat").set(sceneMat);
            sh.safeGetUniform("CamL").set((float) camL[0], (float) camL[1], (float) camL[2]);
            sh.safeGetUniform("uCol").set(0.55f, 0.7f, 1.0f);
            RenderSystem.disableCull();
            RenderSystem.setShader(() -> sh);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (double[] s : boltSegs(bolt)) {
                ribbonQuad(b, new double[] {s[0], s[1], s[2]}, new double[] {s[3], s[4], s[5]}, 0.55 * s[6], camL, life);
            }
            MeshData mesh = b.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }
    }

    private static void trails(double t, Matrix4f sceneMat, double[] camL) {
        ShaderInstance sh = FxShaders.shenBolt;
        if (sh == null || !(t > 29.2 && t - 29.2 < 2.6)) {
            return;
        }
        sh.safeGetUniform("SceneMat").set(sceneMat);
        sh.safeGetUniform("CamL").set((float) camL[0], (float) camL[1], (float) camL[2]);
        sh.safeGetUniform("uCol").set(1f, 0.65f, 0.2f);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> sh);
        int n = 40;
        double fade = 1 - Shen.sm(1.8, 2.6, t - 29.2);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < 7; i++) {
            double[][] pts = new double[n][];
            for (int k = 0; k < n; k++) {
                pts[k] = Shen.ballAt(i, t - k * 0.02);
            }
            double[][] l = new double[n][], r = new double[n][];
            float[] al = new float[n];
            for (int k = 0; k < n; k++) {
                double[] a = pts[k], c = pts[Math.min(n - 1, k + 1)];
                double[] dir = Shen.norm(Shen.sub(a, c)), toC = Shen.norm(Shen.sub(camL, a));
                double dc = Shen.len(Shen.sub(camL, a));
                double[] side = Shen.cross(dir, toC);
                side = !(Shen.len(side) >= 1e-4) ? new double[] {0, 0, 0} : Shen.mul(Shen.norm(side), (0.3 + dc * 0.02) * (1 - (double) k / n));
                l[k] = Shen.sub(a, side);
                r[k] = Shen.add(a, side);
                al[k] = (float) ((1 - (double) k / n) * fade);
            }
            for (int k = 0; k < n - 1; k++) {
                b.addVertex((float) l[k][0], (float) l[k][1], (float) l[k][2]).setUv(-1, al[k]);
                b.addVertex((float) r[k][0], (float) r[k][1], (float) r[k][2]).setUv(1, al[k]);
                b.addVertex((float) r[k + 1][0], (float) r[k + 1][1], (float) r[k + 1][2]).setUv(1, al[k + 1]);
                b.addVertex((float) l[k + 1][0], (float) l[k + 1][1], (float) l[k + 1][2]).setUv(-1, al[k + 1]);
            }
        }
        MeshData mesh = b.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    static Entity summonerEntity() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || summoner < 0 ? null : mc.level.getEntity(summoner);
    }
}
