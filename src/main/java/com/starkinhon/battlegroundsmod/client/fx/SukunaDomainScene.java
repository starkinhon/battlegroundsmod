package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.CutsceneCamera;
import com.starkinhon.battlegroundsmod.client.FaceCutscene;
import com.starkinhon.battlegroundsmod.client.outfit.MeshModel;
import com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain;
import com.starkinhon.battlegroundsmod.skill.DominioSpec;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// Sukuna's Expansão de Domínio: Santuário Malevolente (power/powers/sukuna/ShrineDomain), drawn as the 3D preview Stark
// approved (forja/sukuna-dominio v35, 2026-11-09 05:36): this class runs the preview's timeline (its ticks, T_*) from the
// cut Stark made in the preview's editor (src) and draws it with the preview's own shaders (shaders/core/souls_sd_*).
// Everything is worked out in the domain's own frame, as the preview's: Sukuna's feet at 0 facing +z, y up (localToRel
// turns it into the world). Before the screen goes black that frame stands where he opened it; after it, in the space of
// its own above the world (DominioSpec.arena), where everyone was taken.
// - the press (everyone near): the shockwave, a dark red ring and a low wall of bent air running out to the raio.
// - those in it (Sukuna, everyone caught, creative players with him) see the cutscene, with the camera of each shot:
//   the hand sign up close while the floor turns to water block by block behind him; the one drop and its ring; the
//   dark, the black, the red sea rising from the bottom of the frame; drowning (the surface from below, the air
//   bubbles, each its own); the black aura and the six eyes rushing in; the red sky, straight down the dark Santuário to
//   his face, the close, the very fast pull back, the light coming on (the shrine lit, mirrored in the water with the cyan
//   light); then the camera flies back to each one's own view (second 13) and everyone plays.
// - inside, from then on: the wind of red mist and pale streaks, embers and ash, the Desmantelar lines flashing through
//   the air everywhere (one through each body at each little cut it takes), cuts opening in the water, Clivar's grid
//   measuring each body and snapping into slits, blood pooling under them, everything shaking with the cuts.
// - from outside: the smooth black ball, as Gojo's.
// The fast version (V twice) shows the black aura and the six eyes on each one's own screen, then the domain cutting.
public final class SukunaDomainScene {
    // ---------- the preview's timeline (ticks) ----------
    private static final double T_WATER = 26, T_WATER_END = 26 + 34 * 0.87, T_DROP = 90, T_HIT = 108, T_DARK = 132, T_BLACK = 170,
            T_AURA = 162, T_SIX = 181, T_RED = 196, T_FACE = 220, T_WIDE = 240, T_BACK = 246, T_LIT = 256, T_RET = 302, T_PLAY = 324;
    private static final double FOV = 25;
    private static final double GOTA_X = 4.5, GOTA_Z = 2.0;
    private static final double SHRINE_Z = -8.6, SHRINE_SCALE = 0.424;
    private static final double NEVOA = 1.6;
    private static final double MIST_R = 46;
    private static final int LPOOL = 72;
    private static final double LCUT0 = T_PLAY - 6, LCUT_DT = 0.12;
    private static final double SCAR0 = T_PLAY - 2, SCAR_DT = 0.5;
    private static final int SPOOL = 72;
    private static final double CLV0 = T_PLAY - 2, CLV_DT = 7, CMEAS = 3;
    private static final int EMB_N = 900;
    private static final int CLOSE_FADE = ShrineDomain.CLOSE_TICKS;

    private static final String[] MATERIALS = {"tile", "black", "dark", "wood", "gold", "post", "red", "void", "gum", "tooth",
            "bone", "horn", "hornB", "bark", "heap", "lamp"};
    private static final Map<String, MeshModel> SHRINE = new LinkedHashMap<>();
    private static boolean shrineBroken;

    private static final class Dom {
        final int caster;
        final Vec3 center;
        final Vec3 arena;
        final double start;
        float yaw;
        boolean decided;
        boolean quick;
        boolean part;
        int target = -1;
        double closeAt = -1;
        double goneAt = -1;
        boolean camera;
        final java.util.Set<Integer> played = new java.util.HashSet<>();
        // the blocks that turn to water: x, y (the top of the ground), z
        int[] cells;
        // each Desmantelar line keeps where it was born (round the viewer then)
        final Map<Integer, Vec3> lineAt = new HashMap<>();

        Dom(int caster, Vec3 center, double start, float yaw) {
            this.caster = caster;
            this.center = center;
            this.start = start;
            this.yaw = yaw;
            this.arena = DominioSpec.arena(Minecraft.getInstance().level, center);
        }
    }

    private static final List<Dom> DOMS = new ArrayList<>();
    private static double frameNow = -1;

    private SukunaDomainScene() {
    }

    // ---------- from the server ----------

    public static void event(int casterId, Vec3 center, int mode, int a, int b, boolean part) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = now();
        Dom d = find(casterId);
        switch (mode) {
            case ShrineDomain.M_WAVE -> {
                if (d != null) {
                    DOMS.remove(d);
                    endCamera(d);
                }
                DOMS.add(new Dom(casterId, center, now, a / 10f));
                // load the shrine's meshes now, while the wave plays: loading them when it first shows froze the game
                // for 1.5 s on the first domain of the session (test 2026-11-09)
                shrineReady();
                // The floor inside mirrors this place: take it all round now, while the wave is on screen.
                if (mc.player != null && mc.player.position().distanceTo(center) <= ShrineDomain.RADIUS + 16) {
                    DomainPanorama.request();
                }
            }
            case ShrineDomain.M_CUTSCENE, ShrineDomain.M_QUICK -> {
                if (d == null) {
                    d = new Dom(casterId, center, now - ShrineDomain.WAVE, a / 10f);
                    DOMS.add(d);
                }
                d.decided = true;
                d.quick = mode == ShrineDomain.M_QUICK;
                d.part = part;
                d.yaw = a / 10f;
                d.target = b;
                if (!d.quick && part) {
                    d.camera = true;
                    CutsceneCamera.begin("sukuna_domain");
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
            }
            case ShrineDomain.M_CLOSE -> {
                if (d != null && d.closeAt < 0) {
                    d.closeAt = now;
                    endCamera(d);
                }
            }
            case ShrineDomain.M_GONE -> {
                if (d != null) {
                    d.goneAt = now;
                    endCamera(d);
                }
            }
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

    private static void endCamera(Dom d) {
        if (d.camera) {
            d.camera = false;
            CutsceneCamera.end("sukuna_domain");
        }
    }

    public static void clear() {
        for (Dom d : DOMS) {
            endCamera(d);
        }
        DOMS.clear();
        DomainPanorama.clear();
        restoreHeld();
    }

    public static boolean active() {
        return !DOMS.isEmpty();
    }

    private static double now() {
        return ScreenFx.now() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static double age(Dom d, double now) {
        return now - d.start;
    }

    private static double cut(Dom d, double now) {
        double age = age(d, now);
        if (!d.decided) {
            return age < ShrineDomain.WAVE ? -1 : -0.5;
        }
        return ShrineDomain.cut(d.quick, age);
    }

    // The preview's tick for a tick of Stark's cut: the sign (0-60), the drop (60-90, trimmed), the drowning and the rest.
    private static double src(double c) {
        if (c < 60) {
            return c;
        }
        if (c < 90) {
            return 90 + (0.236 + (c - 60) / 30 * 0.716) * 42;
        }
        return c + 42;
    }

    // The cutscene is this player's right now.
    private static boolean scene(Dom d, double c) {
        return d.camera && d.decided && !d.quick && d.part && c >= 0 && c < ShrineDomain.CUT_PLAY && d.closeAt < 0;
    }

    private static Dom sceneDom(double now) {
        for (Dom d : DOMS) {
            if (scene(d, cut(d, now))) {
                return d;
            }
        }
        return null;
    }

    public static boolean cutscene() {
        return sceneDom(now()) != null;
    }

    // The HUD goes away in the cutscene and in the fast version's dive into the dark too (it stayed over the eyes).
    public static boolean hidesHud() {
        if (cutscene()) {
            return true;
        }
        double now = now();
        for (Dom d : DOMS) {
            double c = cut(d, now);
            if (d.quick && d.part && d.closeAt < 0 && c >= ShrineDomain.QUICK_FROM && c < ShrineDomain.CUT_PLAY) {
                return true;
            }
        }
        return false;
    }

    // ---------- every tick ----------

    public static void tick() {
        // anything still emptied (a draw that never finished: another mod cancelled it) gets its items back
        restoreHeld();
        if (DOMS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double now = ScreenFx.now();
        Iterator<Dom> it = DOMS.iterator();
        while (it.hasNext()) {
            Dom d = it.next();
            double age = age(d, now);
            if ((d.goneAt >= 0 && now > d.goneAt + 12) || age > ShrineDomain.WAVE + ShrineDomain.CUT_LEN + ShrineDomain.FREE_TICKS + 200 || mc.level == null) {
                endCamera(d);
                it.remove();
                continue;
            }
            double c = cut(d, now);
            if (d.camera && !scene(d, c) && (c >= ShrineDomain.CUT_PLAY || d.closeAt >= 0)) {
                endCamera(d);
            }
            if (scene(d, c)) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            sounds(d, c, mc);
            if (c >= ShrineDomain.CUT_INSIDE - 2 && d.goneAt < 0 && mc.player != null) {
                floor(d, mc.player);
            }
        }
    }

    // The arena has no blocks: its floor holds the local player up here too (as Gojo's, DomainScene.floor), standing on
    // it, so they walk and jump like on the ground, and the barrier stops them softly. Without it the player kept falling
    // and the server kept pulling them back up (ShrineDomain.floor): the stutter that made walking impossible.
    private static void floor(Dom d, LocalPlayer player) {
        Vec3 pos = player.position();
        if (!nearArena(d, pos)) {
            return;
        }
        if (pos.y > d.arena.y + 0.001) {
            clampRadius(d, player);
            return;
        }
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
        }
        player.setPos(pos.x, d.arena.y, pos.z);
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x, Math.max(0, v.y), v.z);
        player.setOnGround(true);
        player.resetFallDistance();
        clampRadius(d, player);
    }

    private static void clampRadius(Dom d, LocalPlayer player) {
        Vec3 pos = player.position();
        if (!nearArena(d, pos)) {
            return;
        }
        double dx = pos.x - d.arena.x, dz = pos.z - d.arena.z, r = Math.sqrt(dx * dx + dz * dz), max = ShrineDomain.RADIUS - 1.5;
        if (r > max) {
            player.setPos(d.arena.x + dx / r * max, pos.y, d.arena.z + dz / r * max);
            Vec3 v = player.getDeltaMovement();
            double out = (v.x * dx + v.z * dz) / r;
            if (out > 0) {
                player.setDeltaMovement(v.x - dx / r * out, v.y, v.z - dz / r * out);
            }
        }
    }

    private static void sounds(Dom d, double c, Minecraft mc) {
        if (!d.decided || d.closeAt >= 0 || mc.player == null) {
            return;
        }
        boolean in = d.part || mc.player.position().distanceTo(d.arena) < ShrineDomain.RADIUS + 8;
        if (!in) {
            return;
        }
        if (!d.quick) {
            once(d, mc, 68, c, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 0.8f, 1.6f);
            once(d, mc, 69, c, SoundEvents.GENERIC_SPLASH, 0.6f, 0.35f);
            once(d, mc, 92, c, SoundEvents.AMBIENT_UNDERWATER_ENTER, 1.0f, 1.0f);
            once(d, mc, 154, c, SoundEvents.WARDEN_EMERGE, 0.6f, 0.9f);
            once(d, mc, 214, c, SoundEvents.BEACON_ACTIVATE, 0.45f, 1.0f);
        }
        once(d, mc, 139, c, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5f, 0.8f);
        once(d, mc, 150, c, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 0.7f);
        if (c >= ShrineDomain.CUT_PLAY - 4) {
            // the wide beams passing close and the red cuts: a cut's sound in the air
            double s = src(c);
            int kl = (int) Math.floor((s - LCUT0) / LCUT_DT);
            for (int k = Math.max(0, kl - 8); k <= kl; k++) {
                double r = hk(k, 301);
                if ((r >= 0.52 && r < 0.82) && hk(k, 330) < 0.18 && d.played.add(100000 + k)) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_SWEEP, (float) (0.5 + hk(k, 331) * 0.6), 0.25f));
                }
            }
        }
    }

    private static void once(Dom d, Minecraft mc, int at, double c, SoundEvent sound, float pitch, float volume) {
        if (c >= at && c < at + 6 && d.played.add(at)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }

    // ---------- the frame ----------

    private static Vec3 origin(Dom d, double c) {
        // From the moment it is decided everyone is in the domain's own space (Stark 07:27: no more blocks in the way),
        // the opening scenes included; only the wave happens where it opened.
        return c >= ShrineDomain.CUT_INSIDE || d.decided ? d.arena : d.center;
    }

    private static Matrix4f localToRel(Dom d, double c, Vec3 camera) {
        Vec3 o = origin(d, c).subtract(camera);
        return new Matrix4f().translation((float) o.x, (float) o.y, (float) o.z).rotateY(-d.yaw * Mth.DEG_TO_RAD);
    }

    private static Vec3 toLocal(Dom d, double c, Vec3 world) {
        Vec3 p = world.subtract(origin(d, c));
        double a = d.yaw * Mth.DEG_TO_RAD;
        double cs = Math.cos(a), sn = Math.sin(a);
        // inverse of rotateY(-yaw)
        return new Vec3(p.x * cs + p.z * sn, p.y, -p.x * sn + p.z * cs);
    }

    private static Vec3 toWorld(Dom d, double c, Vec3 local) {
        double a = d.yaw * Mth.DEG_TO_RAD;
        double cs = Math.cos(a), sn = Math.sin(a);
        return origin(d, c).add(local.x * cs - local.z * sn, local.y, local.x * sn + local.z * cs);
    }

    // ---------- the cameras (the preview's "Cena") ----------

    private record Shot(double s0, double s1, Vec3 p0, Vec3 p1, Vec3 l0, Vec3 l1, String ease, double f0, double f1, double[] lookAt,
            Vec3 pc, Vec3 lc) {
    }

    private static Vec3 v(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    private static final Shot[] SHOTS = {
            new Shot(T_DARK, T_BLACK - 6, v(2.19, 0.745, 2.197), v(2.5, -3.6, 2.0), v(4.5, -0.6, 2.0), v(2.9, 1.6, 0.9), "snap", 0, 0, new double[] {0.1, 0.7}, null, null),
            new Shot(T_BLACK - 6, T_RED, v(2.5, -3.6, 2.0), v(2.59, -28.1, -27.7), v(2.9, 1.6, 0.9), v(2.6, -31, -31.2), "in", 0, 0, new double[] {0, 0.3}, null, null),
            new Shot(T_RED, T_FACE, v(0, 8.8, 1.85), v(0, 1.66, 1.85), v(0, 9.1, -8), v(0, 1.6, -8), "out", 0, 0, null, null, null),
            new Shot(T_FACE, T_WIDE, v(0, 1.66, 1.85), v(0, 1.66, 1.85), v(0, 1.6, -8), v(0, 1.6, -8), "io", 0, 0, null, null, null),
            new Shot(T_WIDE, T_BACK, v(0, 1.66, 1.85), v(0, 2.6, 56), v(0, 1.6, -8), v(0, 2.2, -8), "out", 0, 0, null, null, null),
            new Shot(T_BACK, T_LIT, v(0, 2.6, 56), v(0, 2.62, 55.6), v(0, 2.2, -8), v(0, 2.2, -8), "io", 0, 0, null, null, null),
            new Shot(T_LIT, T_RET, v(0, 2.62, 55.6), v(0, 2.9, 50), v(0, 2.2, -8), v(0, 2.6, -8), "io", 0, 0, null, null, null),
            // the return: from the lit wide shot, curving round him, to each one's own view (filled in at use)
            new Shot(T_RET, T_PLAY, v(0, 2.9, 50), null, v(0, 2.6, -8), null, "io", 25, 70, null, v(16, 4.5, 6), v(0, 1.6, 0))};

    private static double ease(String e, double u) {
        return switch (e) {
            case "out" -> 1 - Math.pow(1 - u, 3);
            case "snap" -> 1 - Math.pow(1 - u, 5);
            case "in" -> u * u * u;
            case "lin" -> u;
            default -> u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
        };
    }

    private static Vec3 bez(Vec3 a, Vec3 c, Vec3 b, double k) {
        return a.scale((1 - k) * (1 - k)).add(c.scale(2 * k * (1 - k))).add(b.scale(k * k));
    }

    // [position, look, fov] in the domain's frame at the preview's tick s (own: the viewer's eye and look, for the return)
    private static Vec3[] sceneCam(double s, Vec3 ownP, Vec3 ownL) {
        Shot sh = SHOTS[0];
        for (Shot x : SHOTS) {
            if (s >= x.s0()) {
                sh = x;
            }
        }
        double r = cl((s - sh.s0()) / Math.max(1e-6, sh.s1() - sh.s0()), 0, 1);
        double u = ease(sh.ease(), r);
        double ul = sh.lookAt() != null ? sm(sh.lookAt()[0], sh.lookAt()[1], r) : u;
        Vec3 p1 = sh.p1() != null ? sh.p1() : ownP;
        Vec3 l1 = sh.l1() != null ? sh.l1() : ownL;
        Vec3 p = sh.pc() != null ? bez(sh.p0(), sh.pc(), p1, u) : sh.p0().lerp(p1, u);
        Vec3 l = sh.lc() != null ? bez(sh.l0(), sh.lc(), l1, ul) : sh.l0().lerp(l1, ul);
        double f0 = sh.f0() > 0 ? sh.f0() : FOV, f1 = sh.f1() > 0 ? sh.f1() : f0;
        return new Vec3[] {p, l, new Vec3(f0 + (f1 - f0) * u, u, 0)};
    }

    // The cut's camera: fixed in the sign and the drop (Stark's editor), the scenes' own after.
    private static Vec3[] cutCam(Dom d, double c, Vec3 ownP, Vec3 ownL) {
        if (c < 60) {
            return new Vec3[] {v(0, 1.5, 2.4), v(0, 1.416, 0), new Vec3(FOV / 0.763, 0, 0)};
        }
        if (c < 90) {
            return new Vec3[] {v(2.19, 0.745, 2.197), v(4.5, 0.145, 2), new Vec3(FOV, 0, 0)};
        }
        return sceneCam(src(c), ownP, ownL);
    }

    public static FaceCutscene.Shot shot(float partial) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer viewer = mc.player;
        double now = now();
        Dom d = sceneDom(now);
        if (d == null || viewer == null) {
            return null;
        }
        double c = cut(d, now);
        double s = src(c);
        Vec3 eye = viewer.getEyePosition(partial);
        Vec3 look = Vec3.directionFromRotation(viewer.getViewXRot(partial), viewer.getViewYRot(partial));
        // where the camera lands: the third person spot behind the head (4 blocks back, like vanilla), never in the eyes
        Vec3 ownP = toLocal(d, c, eye.subtract(look.scale(4)));
        Vec3 ownL = toLocal(d, c, eye.add(look.scale(8)));
        Vec3[] cam = cutCam(d, c, ownP, ownL);
        Vec3 p = cam[0], l = cam[1];
        if (s >= T_DARK && s < T_RED && p.y < -0.02) {
            // drowning: the eye dragged down, swaying
            double sw = Math.sin(s * 0.42) + 0.35 * Math.sin(s * 1.7), cw = Math.cos(s * 0.37) + 0.3 * Math.sin(s * 2.3);
            Vec3 dir = l.subtract(p);
            double len = dir.length();
            l = p.add(dir.add(v(0.02 * Math.sin(s * 0.9) * len, 0.03 * cw * len, 0.07 * sw * len * 0.2)));
        }
        if (s > T_PLAY - 4) {
            double[] q = quakeOffsets(s);
            p = p.add(q[0], q[1], q[2]);
            l = l.add(q[3], q[4], 0);
        }
        Vec3 wp = toWorld(d, c, p), wl = toWorld(d, c, l);
        if (c < ShrineDomain.CUT_INSIDE && mc.level != null) {
            // out in the world the ground is not the preview's flat grid: walls and hills must not swallow the camera
            // (Stark 07:02, "os blocos atrapalham"), so it stops in front of the first block between Sukuna and it
            Vec3 from = toWorld(d, c, v(0, 1.5, 0));
            net.minecraft.world.phys.BlockHitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, wp,
                    net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, viewer));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                Vec3 back = wp.subtract(from);
                double len = back.length();
                if (len > 1e-3) {
                    wp = hit.getLocation().subtract(back.scale(0.25 / len));
                }
            }
        }
        Vec3 dir = wl.subtract(wp);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        return new FaceCutscene.Shot(wp, yaw, pitch);
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (DOMS.isEmpty()) {
            return;
        }
        double now = now();
        Dom d = sceneDom(now);
        if (d != null) {
            double c = cut(d, now);
            double s = src(c);
            Vec3[] cam = cutCam(d, c, Vec3.ZERO, Vec3.ZERO);
            double fov = cam[2].x;
            if (s >= T_RET) {
                // lands on the player's own zoom
                fov = Mth.lerp(cam[2].y, FOV, event.getFOV());
            }
            event.setFOV(fov + (s > T_PLAY - 4 ? quake(s) * 1.75 : 0));
            return;
        }
        Dom in = insideNow(now);
        if (in != null) {
            double s = src(cut(in, now));
            event.setFOV(event.getFOV() + quake(s) * 1.75 * PLAY_SHAKE);
        }
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (DOMS.isEmpty()) {
            return;
        }
        double now = now();
        Dom d = sceneDom(now);
        if (d != null) {
            double s = src(cut(d, now));
            Vec3 p = cutCam(d, cut(d, now), Vec3.ZERO, Vec3.ZERO)[0];
            if (s >= T_DARK && s < T_RED && p.y < -0.02) {
                double sw = Math.sin(s * 0.42) + 0.35 * Math.sin(s * 1.7);
                event.setRoll(event.getRoll() + (float) (0.07 * sw * Mth.RAD_TO_DEG));
            }
        }
    }

    // While the domain plays, everyone inside shakes with the cuts (on top of their own camera): yaw and pitch.
    public static float[] shake() {
        if (DOMS.isEmpty()) {
            return null;
        }
        double now = now();
        if (sceneDom(now) != null) {
            return null;
        }
        Dom d = insideNow(now);
        if (d == null) {
            return null;
        }
        double s = src(cut(d, now));
        if (s <= T_PLAY - 4) {
            return null;
        }
        double[] q = quakeOffsets(s);
        // the preview's offsets (blocks, the camera ~3 blocks from what it looks at) as angles
        // while playing it is only a faint tremor (Stark 07:30: the strong one makes the fight unplayable)
        float yaw = (float) Math.toDegrees(q[0] / 3.0 + q[3] / 8.0) * PLAY_SHAKE;
        float pitch = (float) Math.toDegrees(q[1] / 3.0 + q[4] / 8.0) * PLAY_SHAKE;
        return new float[] {yaw, pitch};
    }

    private static final float PLAY_SHAKE = 0.2f;

    // The preview's shake (05:34 Stark: half of the first version): camera xyz and look xy.
    private static double[] quakeOffsets(double t) {
        double k = sm(T_PLAY - 4, T_PLAY + 6, t), Q = quake(t), A = k * (0.017 + 0.08 * Q);
        double sx = Math.sin(t * 7.3) + 0.6 * Math.sin(t * 23.7 + 1.3) + 0.4 * Math.sin(t * 41.1);
        double sy = Math.sin(t * 9.1 + 1) + 0.6 * Math.sin(t * 27.9) + 0.4 * Math.sin(t * 47.3 + 2);
        double sz = Math.sin(t * 13.7 + 4);
        return new double[] {A * sx, A * sy * 0.8, A * sz * 0.4, A * 1.6 * Math.sin(t * 19.3 + 0.5), A * 1.3 * Math.sin(t * 16.1 + 2.2)};
    }

    // How hard the domain shakes at tick t: a steady tremor + a kick from every cut just born + each Clivar snap.
    private static double quakeImp(double t) {
        if (t < LCUT0) {
            return 0;
        }
        int kl = (int) Math.floor((t - LCUT0) / LCUT_DT);
        double q = 0;
        for (int k = Math.max(0, kl - 30); k <= kl; k++) {
            double age = t - (LCUT0 + k * LCUT_DT);
            if (age < 0) {
                continue;
            }
            double r = hk(k, 301);
            double w = r < 0.52 ? 0.12 * (0.3 + hk(k, 307)) : r < 0.64 ? 0.9 : r < 0.82 ? 0.45 : r < 0.93 ? 0.3 : 0.05;
            q += w * Math.exp(-age * 1.4);
        }
        double ca = (t - CLV0 - CMEAS) % CLV_DT;
        if (t > CLV0 + CMEAS) {
            q += 0.8 * Math.exp(-ca * 1.1);
        }
        return Math.min(1.6, q * 0.55);
    }

    private static double quake(double t) {
        if (t < T_PLAY - 6) {
            return 0;
        }
        return sm(T_PLAY - 6, T_PLAY + 4, t) * (0.35 + quakeImp(t));
    }

    // The domain this player is inside of, playing (after the camera came back, or the fast version).
    private static Dom insideNow(double now) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Dom d : DOMS) {
            double c = cut(d, now);
            if (d.decided && d.closeAt < 0 && c >= ShrineDomain.CUT_INSIDE && nearArena(d, cam)) {
                return d;
            }
        }
        return null;
    }

    private static boolean nearArena(Dom d, Vec3 p) {
        double dx = p.x - d.arena.x, dz = p.z - d.arena.z;
        return p.y > d.arena.y - 40 && p.y < d.arena.y + 80 && dx * dx + dz * dz < (ShrineDomain.RADIUS + 24) * (ShrineDomain.RADIUS + 24);
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (DOMS.isEmpty() || sceneDom(now()) == null) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    // ---------- the backdrop: the drowning world, or the inside (sky, the mirror, the people) ----------

    // Called at the end of the level before the effects (FxWorld): replaces the world with the domain where it should.
    public static void backdrop(RenderLevelStageEvent event) {
        if (DOMS.isEmpty() || !shadersReady()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = now();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        for (Dom d : DOMS) {
            double c = cut(d, now);
            if (c < 0 || d.goneAt >= 0) {
                continue;
            }
            currentYaw = d.yaw;
            double s = src(c);
            Vec3 camL = toLocal(d, c, camera);
            boolean sceneHere = scene(d, c);
            if (sceneHere && s >= T_DARK && s < T_RED && camL.y < -0.02) {
                withLocal(event, d, c, camera, () -> drown(d, s, camL));
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                return;
            }
            if (c >= ShrineDomain.CUT_INSIDE && nearArena(d, camera)) {
                withLocal(event, d, c, camera, () -> inside(d, c, s, camL));
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                people(event, d, c, s, camera, partial, sceneHere);
                return;
            }
        }
    }

    private static void withLocal(RenderLevelStageEvent event, Dom d, double c, Vec3 camera, Runnable draw) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        modelView.mul(localToRel(d, c, camera));
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            draw.run();
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // The red sea the eye sinks into: the murk, the surface from below, the bubbles.
    private static void drown(Dom d, double s, Vec3 camL) {
        float t = (float) (s / 20);
        float fade = (float) ((1 - 0.2 * sm(T_BLACK - 14, T_BLACK + 2, s)) * (1 - sm(T_RED - 5, T_RED - 1, s)));
        float depth = (float) Math.min(0, camL.y);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        ShaderInstance back = FxShaders.sdUwBack;
        back.safeGetUniform("T").set(t);
        back.safeGetUniform("Fade").set(fade);
        back.safeGetUniform("Depth").set(depth);
        back.safeGetUniform("CamL").set((float) camL.x, (float) camL.y, (float) camL.z);
        sphere(back, camL, 40, 16, 32);
        premult();
        ShaderInstance surf = FxShaders.sdUwSurf;
        surf.safeGetUniform("T").set(t);
        surf.safeGetUniform("Fade").set(fade);
        surf.safeGetUniform("Depth").set(depth);
        double qU = (s - T_HIT) / 16;
        surf.safeGetUniform("Ring").set((float) (s < T_HIT || qU >= 1 ? -9 : 1.8 * (1 - (1 - qU) * (1 - qU))));
        surf.safeGetUniform("Hit").set((float) GOTA_X, (float) GOTA_Z);
        surf.safeGetUniform("CamL").set((float) camL.x, (float) camL.y, (float) camL.z);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        float e = 200;
        b.addVertex((float) camL.x - e, 0, (float) camL.z - e);
        b.addVertex((float) camL.x + e, 0, (float) camL.z - e);
        b.addVertex((float) camL.x + e, 0, (float) camL.z + e);
        b.addVertex((float) camL.x - e, 0, (float) camL.z + e);
        draw(surf, b);
        bubbles(s, camL, t, fade, depth);
    }

    private static final class Bubble {
        double R, f, lat, up, dep, low, vy, zx, zf, tx, tf, zp, seed;
        boolean burst;
    }

    private static Bubble[] uwb;

    // The preview's 520 bubbles: the same seeded numbers, so each is the one it was there.
    private static Bubble[] bubbleSet() {
        if (uwb != null) {
            return uwb;
        }
        long[] sd = {91};
        java.util.function.DoubleSupplier r = () -> {
            sd[0] = (sd[0] * 16807) % 2147483647L;
            return (sd[0] - 1) / 2147483646.0;
        };
        Bubble[] out = new Bubble[520];
        for (int i = 0; i < out.length; i++) {
            boolean burst = i < 230;
            double kind = r.getAsDouble();
            double R = kind < 0.6 ? 0.012 + r.getAsDouble() * 0.03 : kind < 0.92 ? 0.04 + r.getAsDouble() * 0.06 : 0.1 + r.getAsDouble() * 0.16;
            Bubble b = new Bubble();
            b.R = R;
            b.burst = burst;
            b.f = burst ? 0.07 + r.getAsDouble() * 0.2 : 0.12 + r.getAsDouble() * 0.75;
            b.lat = (r.getAsDouble() - 0.5) * (burst ? 1.4 : 3.6);
            b.up = (r.getAsDouble() - 0.5) * (burst ? 0.9 : 2.2);
            b.dep = (burst ? 0.35 + r.getAsDouble() * 1.4 : 0.6 + r.getAsDouble() * 3.4) + R * 5;
            b.low = 0.1 + r.getAsDouble() * (burst ? 0.5 : 1.6);
            b.vy = (0.55 + R * 9 + r.getAsDouble() * 0.35) * 1.4;
            b.zx = (0.02 + r.getAsDouble() * 0.09 + R * 0.4) * 2.6;
            b.zf = 3 + r.getAsDouble() * 7;
            b.tx = 0.15 + r.getAsDouble() * 0.35;
            b.tf = 1.5 + r.getAsDouble() * 3;
            b.zp = r.getAsDouble() * 6.3;
            b.seed = r.getAsDouble() * 100;
            out[i] = b;
        }
        uwb = out;
        return out;
    }

    private static void bubbles(double s, Vec3 camL, float t, float fade, float depth) {
        double a = T_DARK, bb = T_BLACK;
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Vec3[] basis = camBasis(camL, null);
        int n = 0;
        for (Bubble k : bubbleSet()) {
            double t0 = a + k.f * (bb - a) * 1.3;
            double dt = (s - t0) / 20;
            if (dt < 0) {
                continue;
            }
            Vec3[] pl = sceneCam(k.burst ? Math.max(t0, a + 6) : t0, Vec3.ZERO, Vec3.ZERO);
            Vec3 p0 = pl[0], l0 = pl[1];
            Vec3 fwd = l0.subtract(p0).normalize();
            Vec3 right = fwd.cross(v(0, 1, 0));
            right = right.lengthSqr() < 1e-4 ? v(1, 0, 0) : right.normalize();
            Vec3 uv = right.cross(fwd);
            Vec3 p = p0.add(fwd.scale(k.dep)).add(right.scale(k.lat * (0.4 + k.dep * 0.5))).add(uv.scale(k.up * (0.4 + k.dep * 0.4)));
            double py = Math.min(p.y, -0.25) - k.low;
            double x = p.x + k.zx * Math.sin(k.zf * dt + k.zp) + k.tx * Math.sin(k.tf * dt + k.seed);
            double z = p.z + k.zx * Math.cos(k.zf * 0.8 * dt + k.zp) + k.tx * Math.cos(k.tf * 1.3 * dt + k.seed * 1.7);
            double y = py + k.vy * dt + 0.25 * dt * dt;
            if (y > -0.01) {
                continue;
            }
            double pop = 1 - sm(-0.12, -0.01, y), grow = sm(0, 0.1, dt);
            double size = k.R * grow * Math.max(0.05, pop) * 1.45;
            double fl = Math.min(0.45, k.R * 2.2);
            float dfade = (float) Math.exp(Math.max(depth, -3.5) * 0.25);
            quad(buf, new Vector3f((float) x, (float) y, (float) z), basis[0], basis[1], (float) size, (float) (k.seed / 100), (float) fl, dfade, 1f);
            n++;
        }
        ShaderInstance sh = FxShaders.sdBubble;
        sh.safeGetUniform("T").set(t);
        sh.safeGetUniform("Fade").set(fade);
        if (n == 0) {
            buf.build();
            return;
        }
        premult();
        draw(sh, buf);
    }

    // right and up of the viewer, in the domain's frame
    private static Vec3[] camBasis(Vec3 camL, Dom d) {
        Minecraft mc = Minecraft.getInstance();
        org.joml.Quaternionf q = mc.gameRenderer.getMainCamera().rotation();
        Vector3f r = q.transform(new Vector3f(-1, 0, 0));
        Vector3f u = q.transform(new Vector3f(0, 1, 0));
        float yaw = currentYaw;
        double a = yaw * Mth.DEG_TO_RAD, cs = Math.cos(a), sn = Math.sin(a);
        Vec3 rl = new Vec3(r.x * cs + r.z * sn, r.y, -r.x * sn + r.z * cs);
        Vec3 ul = new Vec3(u.x * cs + u.z * sn, u.y, -u.x * sn + u.z * cs);
        return new Vec3[] {rl, ul};
    }

    private static float currentYaw;

    private static void quad(BufferBuilder b, Vector3f c, Vec3 r, Vec3 u, float half, float cr, float cg, float cb, float ca) {
        float rx = (float) r.x * half, ry = (float) r.y * half, rz = (float) r.z * half;
        float ux = (float) u.x * half, uy = (float) u.y * half, uz = (float) u.z * half;
        b.addVertex(c.x - rx - ux, c.y - ry - uy, c.z - rz - uz).setUv(-1, -1).setColor(cr, cg, cb, ca);
        b.addVertex(c.x + rx - ux, c.y + ry - uy, c.z + rz - uz).setUv(1, -1).setColor(cr, cg, cb, ca);
        b.addVertex(c.x + rx + ux, c.y + ry + uy, c.z + rz + uz).setUv(1, 1).setColor(cr, cg, cb, ca);
        b.addVertex(c.x - rx + ux, c.y - ry + uy, c.z - rz + uz).setUv(-1, 1).setColor(cr, cg, cb, ca);
    }

    // The inside, under everything: the red sky, the shrine and its cyan light upside down in the water, the water.
    private static void inside(Dom d, double c, double s, Vec3 camL) {
        float t = (float) (s / 20);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        ShaderInstance sky = FxShaders.sdSky;
        sky.safeGetUniform("T").set(t);
        sky.safeGetUniform("Red").set(s >= T_RED ? 1f : 0f);
        sky.safeGetUniform("CamL").set((float) camL.x, (float) camL.y, (float) camL.z);
        sphere(sky, camL, 50, 24, 48);
        if (s >= T_RED && DomainPanorama.ready() && FxShaders.sdPano != null) {
            // the outside, upside down in the water (where the domain opened, taken when it started)
            ShaderInstance pano = FxShaders.sdPano;
            pano.safeGetUniform("T").set(t);
            pano.safeGetUniform("Yaw").set(d.yaw * Mth.DEG_TO_RAD);
            pano.safeGetUniform("A").set((float) (sm(T_RED + 4, T_RED + 16, s) * (1 - 0.5 * sm(T_PLAY, T_PLAY + 20, s))));
            pano.safeGetUniform("Quake").set(0f);
            pano.safeGetUniform("Texel").set(DomainPanorama.texel());
            pano.safeGetUniform("CamL").set((float) camL.x, (float) camL.y, (float) camL.z);
            RenderSystem.setShaderTexture(0, DomainPanorama.texture());
            sphere(pano, camL, 50, 24, 48);
        }
        // the mirror: what stands on the water, upside down under it
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        if (s >= T_RED) {
            shrine(d, s, camL, true);
            column(s, true);
        }
        // the water over it
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        premult();
        ShaderInstance fl = FxShaders.sdFloor;
        fl.safeGetUniform("T").set(t);
        fl.safeGetUniform("Arena").set(1f);
        fl.safeGetUniform("Quake").set(0f);
        fl.safeGetUniform("Light").set(1f);
        fl.safeGetUniform("CamL").set((float) camL.x, (float) camL.y, (float) camL.z);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        float e = 600;
        b.addVertex((float) camL.x - e, 0, (float) camL.z - e);
        b.addVertex((float) camL.x - e, 0, (float) camL.z + e);
        b.addVertex((float) camL.x + e, 0, (float) camL.z + e);
        b.addVertex((float) camL.x + e, 0, (float) camL.z - e);
        draw(fl, b);
    }

    // The scene's light on the people inside (the preview's hemisphere + key light), as a tint.
    private static float[] tint(double s) {
        double lit = sm(T_LIT, T_LIT + 3, s);
        double amb = s < T_FACE ? 0.06 + 0.64 * sm(T_FACE - 8, T_FACE, s) : s < T_WIDE ? 0.7 : s < T_BACK ? 0.7 - 0.45 * sm(T_WIDE, T_BACK, s) : 0.25 + 0.37 * lit;
        if (s > T_PLAY - 6) {
            amb *= 1 + 0.45 * quakeImp(s);
        }
        double key = 0.9 * lit;
        return new float[] {(float) Math.min(1, amb * 0.92 + key * 0.5), (float) Math.min(1, amb * 0.66 + key * 0.24), (float) Math.min(1, amb * 0.66 + key * 0.14)};
    }

    // The people inside, drawn again over the backdrop (Gojo's way), in the domain's light. In the cutscene's own
    // shots inside only Sukuna shows (the preview hides the target until the camera is back).
    private static void people(RenderLevelStageEvent event, Dom d, double c, double s, Vec3 camera, float partial, boolean sceneHere) {
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
        float[] tint = tint(s);
        try {
            dispatcher.setRenderShadow(false);
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity) || entity.isInvisible() || (entity == viewer && !detached) || !nearArena(d, entity.position())) {
                    continue;
                }
                boolean victim = victim(d, entity);
                if (victim && sceneHere && s >= T_RED && s < T_RET + 10) {
                    continue;
                }
                Vec3 rel = entity.getPosition(partial).subtract(camera);
                if (victim && s >= T_PLAY - 6) {
                    // Clivar jolts the body at each cut (homing: it can't get away)
                    double jolt = clivarJolt(s);
                    double kl = Math.floor((s - CLV0) / CLV_DT);
                    rel = rel.add((hk(kl + entity.getId() * 0.37, 200) - 0.5) * 0.12 * jolt, 0, (hk(kl, 202 + entity.getId()) - 0.5) * 0.12 * jolt);
                }
                float yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
                dispatcher.render(entity, rel.x, rel.y, rel.z, yaw, partial, new PoseStack(), buffers, 0xF000F0);
            }
            RenderSystem.setShaderColor(tint[0], tint[1], tint[2], 1);
            buffers.endBatch();
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            dispatcher.setRenderShadow(true);
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static boolean victim(Dom d, Entity e) {
        return e.getId() != d.caster && e instanceof LivingEntity && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    // ---------- the effects (FxWorld calls this at the end of the level, matrices set to the camera) ----------

    public static void render(Vec3 camera, float partial) {
        if (DOMS.isEmpty() || !shadersReady()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double now = now();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        for (Dom d : DOMS) {
            double age = age(d, now);
            double c = cut(d, now);
            double s = c >= 0 ? src(c) : -1;
            currentYaw = d.yaw;
            if (d.goneAt >= 0) {
                continue;
            }
            // the press: the shockwave
            if (age < ShrineDomain.WAVE + 8) {
                modelView.pushMatrix();
                Vec3 o = d.center.subtract(camera);
                modelView.translate((float) o.x, (float) o.y, (float) o.z);
                RenderSystem.applyModelViewMatrix();
                wave(age);
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
            if (c < 0) {
                continue;
            }
            boolean inArena = c >= ShrineDomain.CUT_INSIDE && nearArena(d, camera);
            boolean sceneHere = scene(d, c);
            Vec3 camL = toLocal(d, c, camera);
            if (sceneHere && s >= T_DARK && s < T_RED && camL.y < -0.02) {
                continue;
            }
            modelView.pushMatrix();
            modelView.mul(localToRel(d, c, camera));
            RenderSystem.applyModelViewMatrix();
            try {
                if (inArena) {
                    domain(d, c, s, camL, partial, camera);
                } else if (c < ShrineDomain.CUT_INSIDE || !nearArena(d, camera)) {
                    if (s < T_BLACK) {
                        world(d, c, s, camL, camera, mc);
                    }
                    if (!d.part || d.quick) {
                        dome(d, c, s, camera);
                    }
                }
            } finally {
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableBlend();
                RenderSystem.enableCull();
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        }
    }

    // The ring and the low wall of bent air (matrices at the centre, not turned).
    private static void wave(double age) {
        double r = ShrineDomain.waveRadius(age);
        float a = (float) (1 - sm(ShrineDomain.WAVE - 2, ShrineDomain.WAVE + 8, age));
        if (a <= 0.01 || r < 0.2) {
            return;
        }
        ShaderInstance sh = FxShaders.sdWave;
        sh.safeGetUniform("R").set((float) r);
        sh.safeGetUniform("A").set(a);
        sh.safeGetUniform("T").set((float) (age / 20));
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        premult();
        float e = (float) r + 2;
        sh.safeGetUniform("Wall").set(0f);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(-e, 0.06f, -e).setUv(-e, -e);
        b.addVertex(-e, 0.06f, e).setUv(-e, e);
        b.addVertex(e, 0.06f, e).setUv(e, e);
        b.addVertex(e, 0.06f, -e).setUv(e, -e);
        draw(sh, b);
        sh.safeGetUniform("Wall").set(1f);
        b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        int n = 96;
        float h = 3.2f;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float x0 = (float) (Math.cos(a0) * r), z0 = (float) (Math.sin(a0) * r), x1 = (float) (Math.cos(a1) * r), z1 = (float) (Math.sin(a1) * r);
            b.addVertex(x0, 0, z0).setUv(x0, z0);
            b.addVertex(x1, 0, z1).setUv(x1, z1);
            b.addVertex(x1, h, z1).setUv(x1, z1);
            b.addVertex(x0, h, z0).setUv(x0, z0);
        }
        draw(sh, b);
    }

    // Out in the world, before the black: the blocks turning to water, the drop, its ring; the light dying is a screen pass.
    private static void world(Dom d, double c, double s, Vec3 camL, Vec3 camera, Minecraft mc) {
        double wU = cl((s - T_WATER) / (T_WATER_END - T_WATER), 0, 1);
        double water = s < T_WATER ? -1 : (ShrineDomain.RADIUS + 2) * ease("io", wU);
        double light = s < T_HIT ? 1 : 1 - 0.55 * sm(T_HIT, 170, s);
        double qU = (s - T_HIT) / 16;
        double ring = s < T_HIT || qU >= 1 ? -9 : 1.8 * (1 - (1 - qU) * (1 - qU));
        double ringA = s < T_HIT || qU >= 1 ? 0 : 0.35 * (1 - sm(0.3, 1, qU));
        ground(d, c, camera, d.part ? light : 1);
        if (water > 0) {
            floorCells(d, c, s, camera, water, ring, ringA, d.part ? light : 1);
        }
        if (s >= T_DROP && s < T_HIT && d.part) {
            double u = cl((s - T_DROP) / (T_HIT - T_DROP), 0, 1);
            double y = Math.max(0.03, 2.2 * (1 - u * u));
            Vec3[] basis = camBasis(camL, d);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float half = 0.04f;
            Vector3f at = new Vector3f((float) GOTA_X, (float) (y + groundAt(d, GOTA_X, GOTA_Z)), (float) GOTA_Z);
            float st = (float) (1.2 + 0.6 * u);
            Vec3 r = basis[0].scale(half), up = basis[1].scale(half * st);
            b.addVertex(at.x - (float) (r.x + up.x), at.y - (float) (r.y + up.y), at.z - (float) (r.z + up.z)).setUv(-1, -1);
            b.addVertex(at.x + (float) (r.x - up.x), at.y + (float) (r.y - up.y), at.z + (float) (r.z - up.z)).setUv(1, -1);
            b.addVertex(at.x + (float) (r.x + up.x), at.y + (float) (r.y + up.y), at.z + (float) (r.z + up.z)).setUv(1, 1);
            b.addVertex(at.x - (float) (r.x - up.x), at.y - (float) (r.y - up.y), at.z - (float) (r.z - up.z)).setUv(-1, 1);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            draw(FxShaders.sdDrop, b);
        }
    }

    // How high the water is at a local spot (the ground there, relative to his feet).
    private static double groundAt(Dom d, double x, double z) {
        Vec3 w = toWorld(d, 0, v(x, 0, z));
        int[] cells = d.cells;
        if (cells == null) {
            return 0;
        }
        int bx = Mth.floor(w.x), bz = Mth.floor(w.z);
        for (int i = 0; i < cells.length; i += 3) {
            if (cells[i] == bx && cells[i + 2] == bz) {
                return cells[i + 1] - d.arena.y;
            }
        }
        return 0;
    }

    // The ground's top block in each column within the raio, near his feet's height: where the water lies.
    private static int[] cells(Dom d) {
        if (d.cells != null) {
            return d.cells;
        }
        // The opening plays on the domain's own flat stage (no real blocks up there): one cell per block within the raio,
        // all at the stage's height, so nothing of the world can poke through, hide the camera or tear the water.
        List<int[]> out = new ArrayList<>();
        Vec3 st = d.arena;
        int cx = Mth.floor(st.x), cz = Mth.floor(st.z), cy = Mth.floor(st.y + 0.01);
        int R = (int) ShrineDomain.RADIUS + 3;
        for (int x = cx - R; x <= cx + R; x++) {
            for (int z = cz - R; z <= cz + R; z++) {
                double dx = x + 0.5 - st.x, dz = z + 0.5 - st.z;
                if (dx * dx + dz * dz <= R * R) {
                    out.add(new int[] {x, cy, z});
                }
            }
        }
        int[] flat = new int[out.size() * 3];
        for (int i = 0; i < out.size(); i++) {
            flat[i * 3] = out.get(i)[0];
            flat[i * 3 + 1] = out.get(i)[1];
            flat[i * 3 + 2] = out.get(i)[2];
        }
        d.cells = flat;
        return flat;
    }

    // The stage's ground for the opening: plain grass blocks (the preview's field), as far as the eye needs, dimming with
    // the light. Only their tops: it is flat.
    private static final int GROUND_R = 96;

    private static void ground(Dom d, double c, Vec3 camera, double light) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.renderer.texture.TextureAtlasSprite top = mc.getModelManager()
                .getAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                .getSprite(ResourceLocation.withDefaultNamespace("block/grass_block_top"));
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(new Matrix4f(localToRel(d, c, camera)).invert());
        int ox = Mth.floor(d.arena.x), oz = Mth.floor(d.arena.z);
        double gy = Mth.floor(d.arena.y + 0.01);
        Vec3 o = new Vec3(ox, gy, oz).subtract(camera);
        modelView.translate((float) o.x, (float) o.y, (float) o.z);
        RenderSystem.applyModelViewMatrix();
        float u0 = top.getU0(), u1 = top.getU1(), v0 = top.getV0(), v1 = top.getV1();
        float k = (float) light;
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int x = -GROUND_R; x <= GROUND_R; x++) {
            for (int z = -GROUND_R; z <= GROUND_R; z++) {
                int r2 = x * x + z * z;
                if (r2 > GROUND_R * GROUND_R) {
                    continue;
                }
                // the plains' grass tint, a touch of per-block variation like the vanilla noise, darker far away
                float var = (float) (0.94 + 0.08 * hash(x * 13.7 + z * 71.3));
                float far = (float) (1 - 0.25 * Math.sqrt(r2) / GROUND_R);
                float cr = 0.49f * var * far * k, cg = 0.74f * var * far * k, cb = 0.35f * var * far * k;
                b.addVertex(x, 0, z).setUv(u0, v0).setColor(cr, cg, cb, 1f);
                b.addVertex(x, 0, z + 1).setUv(u0, v1).setColor(cr, cg, cb, 1f);
                b.addVertex(x + 1, 0, z + 1).setUv(u1, v1).setColor(cr, cg, cb, 1f);
                b.addVertex(x + 1, 0, z).setUv(u1, v0).setColor(cr, cg, cb, 1f);
            }
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        BufferUploader.drawWithShader(b.buildOrThrow());
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void floorCells(Dom d, double c, double s, Vec3 camera, double water, double ring, double ringA, double light) {
        int[] cells = cells(d);
        if (cells.length == 0) {
            return;
        }
        // its own frame: block aligned, from the corner of the block under him (the world's blocks turn, not his)
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        Matrix4f back = new Matrix4f(localToRel(d, c, camera)).invert();
        modelView.mul(back);
        int ox = Mth.floor(d.arena.x), oz = Mth.floor(d.arena.z);
        Vec3 o = new Vec3(ox, 0, oz).subtract(camera);
        modelView.translate((float) o.x, (float) o.y, (float) o.z);
        RenderSystem.applyModelViewMatrix();
        Vec3 hitW = toWorld(d, 0, v(GOTA_X, 0, GOTA_Z));
        Vec3 camF = camera.subtract(ox, 0, oz);
        ShaderInstance sh = FxShaders.sdFloor;
        sh.safeGetUniform("T").set((float) (s / 20));
        sh.safeGetUniform("Arena").set(0f);
        sh.safeGetUniform("Water").set((float) water);
        sh.safeGetUniform("Ring").set((float) ring);
        sh.safeGetUniform("RingA").set((float) ringA);
        sh.safeGetUniform("Light").set((float) light);
        sh.safeGetUniform("Quake").set(0f);
        sh.safeGetUniform("Hit").set((float) (hitW.x - ox), (float) (hitW.z - oz));
        sh.safeGetUniform("Mid").set((float) (d.arena.x - ox), (float) (d.arena.z - oz));
        sh.safeGetUniform("CamL").set((float) camF.x, (float) camF.y, (float) camF.z);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int i = 0; i < cells.length; i += 3) {
            float x = cells[i] - ox, y = cells[i + 1] + 0.015f, z = cells[i + 2] - oz;
            b.addVertex(x, y, z);
            b.addVertex(x, y, z + 1);
            b.addVertex(x + 1, y, z + 1);
            b.addVertex(x + 1, y, z);
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        draw(sh, b);
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    // From outside: the smooth black ball, as Gojo's.
    private static void dome(Dom d, double c, double s, Vec3 camera) {
        double a = c >= ShrineDomain.CUT_INSIDE ? 1 : sm(T_DARK, T_BLACK, s);
        if (d.quick) {
            a = sm(ShrineDomain.QUICK_FROM, ShrineDomain.QUICK_FROM + 6, c) + (c >= ShrineDomain.CUT_PLAY ? 1 : 0);
        }
        if (d.closeAt >= 0) {
            a *= 1 - cl((now() - d.closeAt) / CLOSE_FADE, 0, 1);
        }
        a = cl(a, 0, 1);
        if (a <= 0.01) {
            return;
        }
        // the ball is centred where it opened, not on him (it does not turn)
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(new Matrix4f(localToRel(d, 0, camera)).invert());
        Vec3 o = d.center.subtract(camera);
        modelView.translate((float) o.x, (float) o.y, (float) o.z);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(a > 0.99);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int rings = 24, segs = 48;
        float R = (float) ShrineDomain.RADIUS;
        for (int i = 0; i < rings; i++) {
            double t0 = Math.PI * i / rings - Math.PI / 2, t1 = Math.PI * (i + 1) / rings - Math.PI / 2;
            for (int j = 0; j < segs; j++) {
                double p0 = Math.PI * 2 * j / segs, p1 = Math.PI * 2 * (j + 1) / segs;
                float[][] q = {sp(R, t0, p0), sp(R, t0, p1), sp(R, t1, p1), sp(R, t1, p0)};
                for (float[] v : q) {
                    b.addVertex(v[0], v[1], v[2]).setColor(0.003f, 0.002f, 0.004f, (float) a);
                }
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static float[] sp(float r, double lat, double lon) {
        return new float[] {(float) (r * Math.cos(lat) * Math.cos(lon)), (float) (r * Math.sin(lat)), (float) (r * Math.cos(lat) * Math.sin(lon))};
    }

    // Inside, over the people: the shrine, the mist and wind, embers, the Desmantelar lines, cuts in the water, Clivar.
    private static void domain(Dom d, double c, double s, Vec3 camL, float partial, Vec3 camera) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        if (s >= T_RED) {
            shrine(d, s, camL, false);
        }
        // the water's cuts lie on it
        scars(s);
        // the blood under each one Clivar cuts
        List<LivingEntity> victims = victims(d);
        blood(d, c, s, victims, partial, camera);
        double red = s < T_RED ? 0 : sm(T_RED - 2, T_RED + 8, s) * (s < T_FACE ? 0.35 : s < T_WIDE ? 0.55 : 1 - sm(T_WIDE, T_WIDE + 14, s));
        double low = sm(T_WIDE - 6, T_WIDE + 20, s);
        double wind = sm(T_LIT, T_LIT + 30, s);
        if (red + low + wind > 0.001) {
            ShaderInstance m = FxShaders.sdMist;
            m.safeGetUniform("T").set((float) (s / 20));
            m.safeGetUniform("Red").set((float) (red * NEVOA));
            // while playing the air has to stay clear enough to fight in (test 2026-11-09: floor and sky read as one red)
            double play = sm(T_PLAY, T_PLAY + 20, s);
            m.safeGetUniform("Low").set((float) (low * (1 - 0.7 * play)));
            m.safeGetUniform("Wind").set((float) (wind * (1 - 0.75 * play)));
            m.safeGetUniform("R").set((float) MIST_R);
            m.safeGetUniform("CamL").set((float) camL.x, (float) camL.y, (float) camL.z);
            RenderSystem.depthMask(false);
            RenderSystem.enableCull();
            GL11.glCullFace(GL11.GL_FRONT);
            premult();
            sphere(m, Vec3.ZERO, (float) MIST_R, 24, 48);
            GL11.glCullFace(GL11.GL_BACK);
            RenderSystem.disableCull();
        }
        if (s >= T_LIT) {
            embers(s, camL);
        }
        if (s >= LCUT0) {
            lines(d, c, s, camL, victims, partial, camera);
            clivar(d, c, s, victims, partial, camera);
        }
    }

    private static List<LivingEntity> victims(Dom d) {
        List<LivingEntity> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity le && le.isAlive() && victim(d, e) && nearArena(d, e.position())) {
                out.add(le);
            }
        }
        return out;
    }

    // ---- the shrine ----

    private static boolean shrineReady() {
        if (shrineBroken) {
            return false;
        }
        if (SHRINE.isEmpty()) {
            try {
                for (String m : MATERIALS) {
                    SHRINE.put(m, MeshModel.load("malevolent_shrine_" + m.toLowerCase()));
                }
            } catch (RuntimeException e) {
                shrineBroken = true;
                SHRINE.clear();
                BattlegroundsMod.LOGGER.error("Malevolent Shrine model failed to load for the domain", e);
                return false;
            }
        }
        return true;
    }

    private static void shrine(Dom d, double s, Vec3 camL, boolean mirror) {
        if (!shrineReady()) {
            return;
        }
        // The shrine stands still: the tremor is only a screen effect (Stark 06:38).
        double Q = 0;
        double t = s;
        Matrix4f model = new Matrix4f();
        if (mirror) {
            model.scale(1, -1, 1);
        }
        model.translate((float) (Q * 0.03 * Math.sin(t * 37.1)), (float) (Q * 0.015 * Math.abs(Math.sin(t * 29.3))), (float) (SHRINE_Z + Q * 0.02 * Math.sin(t * 31.7)))
                .rotateZ((float) (Q * 0.004 * Math.sin(t * 23.3))).scale((float) SHRINE_SCALE);
        double lit = sm(T_LIT, T_LIT + 3, s);
        double rim = s < T_FACE ? 0.12 * sm(T_RED, T_RED + 6, s) : s < T_LIT ? 0.08 : 0.35;
        double amb = s < T_FACE ? 0.06 + 0.64 * sm(T_FACE - 8, T_FACE, s) : s < T_WIDE ? 0.7 : s < T_BACK ? 0.7 - 0.45 * sm(T_WIDE, T_BACK, s) : 0.25 + 0.37 * lit;
        if (s > T_PLAY - 6) {
            amb *= 1 + 0.45 * quakeImp(s);
        }
        ShaderInstance sh = FxShaders.sdShrine;
        sh.safeGetUniform("ModelMat").set(model);
        sh.safeGetUniform("T").set((float) (s / 20));
        sh.safeGetUniform("Lit").set((float) lit);
        sh.safeGetUniform("Rim").set((float) rim);
        sh.safeGetUniform("Amb").set((float) amb);
        sh.safeGetUniform("Mirror").set(mirror ? 1f : 0f);
        sh.safeGetUniform("CamL").set((float) camL.x, (float) camL.y, (float) camL.z);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        PoseStack.Pose identity = new PoseStack().last();
        for (Map.Entry<String, MeshModel> e : SHRINE.entrySet()) {
            String m = e.getKey();
            float glow = m.equals("void") ? -1 : (m.equals("red") || m.equals("post") || m.equals("lamp")) ? 1 : (m.equals("tile") || m.equals("gold")) ? 0.4f : 0.15f;
            sh.safeGetUniform("Glow").set(glow);
            RenderSystem.setShaderTexture(0, ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/models/malevolent_shrine/" + m.toLowerCase() + ".png"));
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            e.getValue().emit(b, identity, 0xF000F0, OverlayTexture.NO_OVERLAY);
            draw(sh, b);
        }
    }

    // The cyan light that lives only in the water (05:03 Stark: the two side lights out).
    private static void column(double s, boolean mirror) {
        double a = sm(T_LIT, T_LIT + 6, s) * 0.9;
        if (a <= 0.001) {
            return;
        }
        float x = -1.9f, z = -17, w = 0.63f, h = 6.9f;
        ShaderInstance sh = FxShaders.sdCol;
        sh.safeGetUniform("T").set((float) (s / 20));
        sh.safeGetUniform("A").set((float) a);
        sh.safeGetUniform("Seed").set(8.2f);
        sh.safeGetUniform("W").set(w);
        sh.safeGetUniform("H").set(h);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        float sy = mirror ? -1 : 1;
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(x - w / 2, 0, z).setUv(0, 0);
        b.addVertex(x + w / 2, 0, z).setUv(1, 0);
        b.addVertex(x + w / 2, sy * h, z).setUv(1, 1);
        b.addVertex(x - w / 2, sy * h, z).setUv(0, 1);
        draw(sh, b);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ---- embers and ash ----

    private static void embers(double s, Vec3 camL) {
        double uT = s / 20;
        float A = (float) sm(T_LIT, T_LIT + 30, s);
        Vec3[] basis = camBasis(camL, null);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < EMB_N; i++) {
            double px = hash(i * 1.31) * 6.283, py = 2 + Math.sqrt(hash(i * 2.17)) * 36, pz = hash(i * 3.71) * 8;
            double sd = hash(i * 5.3), sz = 0.6 + hash(i * 6.7) * 2.4, rise = 0.3 + hash(i * 8.1) * 1.4;
            boolean ash = hash(i * 9.9) < 0.18;
            double life = 7 + sd * 9;
            double age = ((uT + sd * 40) % life + life) % life;
            double ang = px + uT * (0.9 / (1 + py * 0.12)) * (0.6 + rise * 0.5) + Math.sin(uT * 0.7 + sd * 20) * 0.15;
            double rad = py + Math.sin(uT * 0.9 + sd * 31) * 0.8;
            Vector3f p = new Vector3f((float) (Math.cos(ang) * rad), (float) (pz * 0.25 + age * rise * 0.7 + Math.sin(uT * 2.3 + sd * 50) * 0.25), (float) (Math.sin(ang) * rad));
            float half = (float) (sz * 0.021 * (1 + (ash ? 1.5 : 0)));
            quad(b, p, basis[0], basis[1], half, (float) sd, ash ? 1 : 0, (float) (age / life), 1);
        }
        ShaderInstance sh = FxShaders.sdEmber;
        sh.safeGetUniform("T").set((float) uT);
        sh.safeGetUniform("A").set(A);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        premult();
        draw(sh, b);
    }

    // ---- the Desmantelar lines ----

    private record Line(int kind, Vec3 c, Vec3 ax, double len, double wid, double life, double tap, double bend, double seed, double grow, Vec3 drift) {
    }

    // Line k's own shape, as the preview's lineParams; anchor: where it is born (round the viewer).
    private static Line lineParams(int k, Vec3 anchor) {
        double r = hk(k, 301);
        int kind = r < 0.52 ? 0 : r < 0.64 ? 1 : r < 0.82 ? 2 : r < 0.93 ? 3 : 4;
        boolean far = hk(k, 316) < 0.3;
        Vec3 c = far ? v((hk(k, 302) - 0.5) * 60, 0.3 + Math.pow(hk(k, 303), 1.4) * 14, 6 + (hk(k, 304) - 0.5) * 56)
                : anchor.add((hk(k, 302) - 0.5) * 22, 0.2 + Math.pow(hk(k, 303), 1.3) * 7, (hk(k, 304) - 0.5) * 16);
        double th = hk(k, 305) * 6.283, ph = (hk(k, 306) - 0.5) * Math.PI * 0.95;
        Vec3 ax = v(Math.cos(th) * Math.cos(ph), Math.sin(ph), Math.sin(th) * Math.cos(ph));
        double len = kind == 4 ? 1.2 + hk(k, 307) * 2.6 : kind == 1 ? 14 + hk(k, 307) * 26 : 8 + Math.pow(hk(k, 307), 0.7) * 38;
        double wid = kind == 0 ? 0.05 + Math.pow(hk(k, 308), 2) * 0.35 : kind == 1 ? 0.8 + hk(k, 308) * 1.8 : kind == 2 ? 0.12 + hk(k, 308) * 0.4
                : kind == 3 ? 0.06 + hk(k, 308) * 0.18 : 0.5 + hk(k, 308) * 0.8;
        double life = kind == 4 ? 7 + hk(k, 309) * 5 : kind == 1 ? 2.5 + hk(k, 309) * 2 : 3 + hk(k, 309) * 4;
        return new Line(kind, c, ax, len, wid, life, 0.35 + hk(k, 310) * 0.9, kind == 4 ? 0 : (hk(k, 311) - 0.5) * 0.04, hk(k, 312) * 100,
                0.6 + hk(k, 313) * 0.9, v((hk(k, 314) - 0.5) * 0.06, 0.03, (hk(k, 315) - 0.5) * 0.06));
    }

    private static void lines(Dom d, double c, double s, Vec3 camL, List<LivingEntity> victims, float partial, Vec3 camera) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        premult();
        int kl = (int) Math.floor((s - LCUT0) / LCUT_DT);
        // where the viewer looks, 7 blocks ahead on the water: most lines cross the space each one sees
        Minecraft mc = Minecraft.getInstance();
        Vector3f fw = mc.gameRenderer.getMainCamera().getLookVector();
        double a = d.yaw * Mth.DEG_TO_RAD, cs = Math.cos(a), sn = Math.sin(a);
        Vec3 fl = new Vec3(fw.x * cs + fw.z * sn, 0, -fw.x * sn + fw.z * cs);
        fl = fl.lengthSqr() < 1e-4 ? v(0, 0, 1) : fl.normalize();
        Vec3 anchorNow = new Vec3(camL.x + fl.x * 11, 0, camL.z + fl.z * 11);
        d.lineAt.keySet().removeIf(k -> k < kl - 140);
        for (int k = Math.max(0, kl - LPOOL + 1); k <= kl; k++) {
            double age = s - (LCUT0 + k * LCUT_DT);
            if (age < 0) {
                continue;
            }
            if (hk(k, 333) < (s >= T_PLAY ? 0.7 : 0.5)) {
                // half the preview's count: its camera stood far, in the game they cross right in front of you
                continue;
            }
            Vec3 anchor = d.lineAt.computeIfAbsent(k, kk -> anchorNow);
            Line L = lineParams(k, anchor);
            if (age > L.life()) {
                continue;
            }
            drawLine(L, age, camL);
        }
        // one through each body at each little cut it takes (the server's CUT_EVERY, cutPhase)
        int p = (int) Math.floor(c - ShrineDomain.CUT_PLAY);
        for (LivingEntity v : victims) {
            Vec3 body = toLocal(d, c, v.getPosition(partial).add(0, v.getBbHeight() * 0.55, 0));
            for (int back = 0; back < 8; back++) {
                int q = p - back;
                if (q < 0 || Math.floorMod(q + ShrineDomain.cutPhase(v.getId()), ShrineDomain.CUT_EVERY) != 0) {
                    continue;
                }
                double age = c - ShrineDomain.CUT_PLAY - q;
                int k = 50000 + v.getId() * 977 + q;
                double th = hk(k, 305) * 6.283, ph = (hk(k, 306) - 0.5) * 1.6;
                Vec3 ax = v(Math.cos(th) * Math.cos(ph), Math.sin(ph), Math.sin(th) * Math.cos(ph));
                int kind = hk(k, 301) < 0.6 ? 0 : 2;
                Line L = new Line(kind, body, ax, 3.5 + hk(k, 307) * 4, kind == 0 ? 0.08 + hk(k, 308) * 0.12 : 0.18 + hk(k, 308) * 0.2, 5 + hk(k, 309) * 2,
                        0.5 + hk(k, 310) * 0.6, (hk(k, 311) - 0.5) * 0.03, hk(k, 312) * 100, 0.6, Vec3.ZERO);
                if (age <= L.life()) {
                    drawLine(L, age, camL);
                }
            }
        }
    }

    private static void drawLine(Line L, double age, Vec3 camL) {
        Vec3 C = L.c().add(L.drift().scale(age));
        Vec3 side = L.ax().cross(camL.subtract(C));
        if (side.lengthSqr() < 1e-8) {
            return;
        }
        side = side.normalize();
        // a cut passing right by the camera fills the screen with white: those fade out (only seen at a distance)
        Vec3 rel = camL.subtract(C);
        double along = cl(rel.dot(L.ax()), -L.len() * 0.5, L.len() * 0.5);
        double near = C.add(L.ax().scale(along)).distanceTo(camL);
        double vis = sm(2.5, 6.0, near);
        if (vis <= 0.001) {
            return;
        }
        ShaderInstance sh = FxShaders.sdLine;
        sh.safeGetUniform("Kind").set((float) L.kind());
        sh.safeGetUniform("Tap").set((float) L.tap());
        sh.safeGetUniform("Seed").set((float) L.seed());
        sh.safeGetUniform("Grow").set((float) ease("out", cl(age / L.grow(), 0, 1)));
        sh.safeGetUniform("Life").set((float) sm(L.grow() * 0.6, L.life(), age));
        sh.safeGetUniform("A").set((float) ((1 - sm(L.life() * 0.75, L.life(), age)) * vis));
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        int n = 24;
        for (int i = 0; i < n; i++) {
            double x0 = -0.5 + (double) i / n, x1 = -0.5 + (double) (i + 1) / n;
            Vec3[] q = {lineVert(L, C, side, camL, x0, -0.5), lineVert(L, C, side, camL, x1, -0.5), lineVert(L, C, side, camL, x1, 0.5), lineVert(L, C, side, camL, x0, 0.5)};
            float[] us = {(float) (x0 + 0.5), (float) (x1 + 0.5), (float) (x1 + 0.5), (float) (x0 + 0.5)};
            float[] vs = {0, 0, 1, 1};
            for (int j = 0; j < 4; j++) {
                b.addVertex((float) q[j].x, (float) q[j].y, (float) q[j].z).setUv(us[j], vs[j]);
            }
        }
        draw(sh, b);
    }

    private static Vec3 lineVert(Line L, Vec3 C, Vec3 side, Vec3 camL, double x, double y) {
        Vec3 b = C.add(L.ax().scale(x * L.len()));
        double wd = Math.max(L.wid(), b.distanceTo(camL) * 0.004);
        return b.add(side.scale(y * wd + L.bend() * L.len() * (0.25 - x * x)));
    }

    // ---- the cuts in the water ----

    private static void scars(double s) {
        if (s < SCAR0) {
            return;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        premult();
        int sl = (int) Math.floor((s - SCAR0) / SCAR_DT);
        ShaderInstance sh = FxShaders.sdScar;
        for (int k = Math.max(0, sl - SPOOL + 1); k <= sl; k++) {
            double age = s - (SCAR0 + k * SCAR_DT), life = 22 + hk(k, 21) * 14;
            if (age < 0 || age > life) {
                continue;
            }
            double a = hk(k, 22) * 6.283, rr = 2.5 + Math.sqrt(hk(k, 23)) * 34, len = 3 + Math.pow(hk(k, 24), 1.3) * 14, wd = 0.25 + hk(k, 25) * 0.45;
            double grow = ease("out", cl(age / 3, 0, 1));
            sh.safeGetUniform("Seed").set((float) (hk(k, 27) * 100));
            sh.safeGetUniform("Asp").set((float) (len / wd * 0.12));
            sh.safeGetUniform("Open").set((float) (sm(0, 3, age) * (1 - sm(life * 0.4, life, age))));
            sh.safeGetUniform("A").set((float) (1 - sm(life * 0.6, life, age)));
            Vec3 c = v(Math.cos(a) * rr, 0.02 + (k % SPOOL) * 0.0004, Math.sin(a) * rr);
            double rot = hk(k, 26) * 6.283;
            Vec3 ux = v(Math.cos(rot), 0, -Math.sin(rot)).scale(len * grow / 2), uz = v(Math.sin(rot), 0, Math.cos(rot)).scale(wd / 2);
            plane(sh, c, ux, uz);
        }
    }

    // a quad c +- ux +- uy with UV 0..1 (u along ux, v along uy)
    private static void plane(ShaderInstance sh, Vec3 c, Vec3 ux, Vec3 uy) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Vec3 p0 = c.subtract(ux).subtract(uy), p1 = c.add(ux).subtract(uy), p2 = c.add(ux).add(uy), p3 = c.subtract(ux).add(uy);
        b.addVertex((float) p0.x, (float) p0.y, (float) p0.z).setUv(0, 0);
        b.addVertex((float) p1.x, (float) p1.y, (float) p1.z).setUv(1, 0);
        b.addVertex((float) p2.x, (float) p2.y, (float) p2.z).setUv(1, 1);
        b.addVertex((float) p3.x, (float) p3.y, (float) p3.z).setUv(0, 1);
        draw(sh, b);
    }

    // ---- Clivar ----

    private static double clivarJolt(double s) {
        double kl = Math.floor((s - CLV0) / CLV_DT);
        if (kl < 0) {
            return 0;
        }
        double ca = s - (CLV0 + kl * CLV_DT) - CMEAS;
        if (ca < 0) {
            ca += CLV_DT;
        }
        return ca >= 0 && ca < 3 ? 1 - ca / 3 : 0;
    }

    private static void clivar(Dom d, double c, double s, List<LivingEntity> victims, float partial, Vec3 camera) {
        int kl = (int) Math.floor((s - CLV0) / CLV_DT);
        if (kl < 0 || victims.isEmpty()) {
            return;
        }
        for (LivingEntity v : victims) {
            Vec3 feet = toLocal(d, c, v.getPosition(partial));
            float bodyYaw = Mth.rotLerp(partial, v.yBodyRotO, v.yBodyRot) - d.yaw;
            double w = v.getBbWidth(), h = v.getBbHeight();
            Vec3 tc = feet.add(0, h * 0.54, 0);
            int vid = v.getId();
            for (int slot = 0; slot < 3; slot++) {
                int k = kl - slot;
                if (k < 0) {
                    continue;
                }
                int kk = k * 31 + vid * 7919;
                double age = s - (CLV0 + k * CLV_DT);
                if (age < 0 || age > CMEAS + 16) {
                    continue;
                }
                // the grid measures the body
                if (age < CMEAS + 3) {
                    grid(tc, bodyYaw, w * 1.63, h * 1.08, w * 0.93, cl(age / CMEAS, 0, 1), 1 - sm(CMEAS, CMEAS + 3, age), kk);
                }
                double ca = age - CMEAS;
                if (ca < 0) {
                    continue;
                }
                // the snap: 2-4 slits through the body, depth off so they show on it
                int ns = 2 + (int) Math.floor(hk(kk, 45) * 3);
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                premult();
                ShaderInstance sc = FxShaders.sdScar;
                for (int j = 0; j < ns && ca < 12; j++) {
                    double L = (1.7 + hk(kk, 50 + j) * 0.9) * h / 1.8;
                    Vec3 at = tc.add((hk(kk, 60 + j) - 0.5) * 0.3, (hk(kk, 70 + j) - 0.5) * 1.2 * h / 1.8, 0);
                    double ry = (hk(kk, 80 + j) - 0.5) * 1.2 + Math.PI, rz = (hk(kk, 90 + j) - 0.5) * Math.PI * 1.1;
                    double grow = ease("snap", cl(ca / 1.2, 0, 1));
                    Matrix4f m = new Matrix4f().rotateY((float) (ry - bodyYaw * Mth.DEG_TO_RAD)).rotateZ((float) rz);
                    Vector3f ux = m.transformDirection(new Vector3f(1, 0, 0)).mul((float) (L * grow / 2));
                    Vector3f uy = m.transformDirection(new Vector3f(0, 1, 0)).mul(0.16f);
                    sc.safeGetUniform("Seed").set((float) (hk(kk, 100 + j) * 100));
                    sc.safeGetUniform("Asp").set((float) (L * 1.5));
                    sc.safeGetUniform("Open").set((float) (sm(0, 1.5, ca) * (1 - sm(5, 11, ca))));
                    sc.safeGetUniform("A").set((float) (1 - sm(7, 12, ca)));
                    plane(sc, at, new Vec3(ux.x, ux.y, ux.z), new Vec3(uy.x, uy.y, uy.z));
                }
                // the white zigzag bits torn off the grid fly out
                ShaderInstance zg = FxShaders.sdZig;
                for (int j = 0; j < 12 && ca <= 11; j++) {
                    double h0 = hk(kk, 110 + j), h1 = hk(kk, 130 + j), h2 = hk(kk, 150 + j), h3 = hk(kk, 170 + j);
                    Vec3 dir = v(Math.cos(h0 * 6.283), (h1 - 0.3) * 1.4, Math.sin(h0 * 6.283)).normalize();
                    double sp = 0.06 + h2 * 0.12;
                    Vec3 at = tc.add((h3 - 0.5) * 0.7, (h1 - 0.5) * 1.6, (h2 - 0.5) * 0.4).add(dir.scale(sp * ca * (1 - ca / 30))).add(0, -0.004 * ca * ca, 0);
                    Matrix4f m = new Matrix4f().rotateXYZ((float) (h1 * 6 + ca * 0.4 * h3), (float) (h2 * 6 + ca * 0.3), (float) (h0 * 6 + ca * 0.5 * (h1 - 0.5)));
                    Vector3f ux = m.transformDirection(new Vector3f(1, 0, 0)).mul((float) ((0.16 + h3 * 0.18) / 2));
                    Vector3f uy = m.transformDirection(new Vector3f(0, 1, 0)).mul((float) ((0.05 + h2 * 0.05) / 2));
                    zg.safeGetUniform("Seed").set((float) h1);
                    zg.safeGetUniform("A").set((float) (1 - sm(5, 11, ca)));
                    plane(zg, at, new Vec3(ux.x, ux.y, ux.z), new Vec3(uy.x, uy.y, uy.z));
                }
                RenderSystem.enableDepthTest();
            }
        }
    }

    private static void grid(Vec3 c, float yawDeg, double sx, double sy, double sz, double measure, double fade, int k) {
        ShaderInstance sh = FxShaders.sdGrid;
        Matrix4f box = new Matrix4f().translation((float) c.x, (float) c.y, (float) c.z).rotateY(-yawDeg * Mth.DEG_TO_RAD);
        Matrix4f inv = new Matrix4f(box).invert();
        sh.safeGetUniform("BoxInv").set(inv);
        sh.safeGetUniform("Off").set((float) (hk(k, 41) * 3), (float) (hk(k, 42) * 3), (float) (hk(k, 43) * 3));
        sh.safeGetUniform("NGrid").set((float) (7 + Math.floor(hk(k, 44) * 5)));
        sh.safeGetUniform("Measure").set((float) measure);
        sh.safeGetUniform("Fade").set((float) fade);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        premult();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        float hx = (float) sx / 2, hy = (float) sy / 2, hz = (float) sz / 2;
        float[][][] faces = {
                {{hx, -hy, -hz}, {hx, hy, -hz}, {hx, hy, hz}, {hx, -hy, hz}, {1, 0, 0}},
                {{-hx, -hy, hz}, {-hx, hy, hz}, {-hx, hy, -hz}, {-hx, -hy, -hz}, {-1, 0, 0}},
                {{-hx, hy, -hz}, {-hx, hy, hz}, {hx, hy, hz}, {hx, hy, -hz}, {0, 1, 0}},
                {{-hx, -hy, hz}, {-hx, -hy, -hz}, {hx, -hy, -hz}, {hx, -hy, hz}, {0, -1, 0}},
                {{-hx, -hy, hz}, {hx, -hy, hz}, {hx, hy, hz}, {-hx, hy, hz}, {0, 0, 1}},
                {{hx, -hy, -hz}, {-hx, -hy, -hz}, {-hx, hy, -hz}, {hx, hy, -hz}, {0, 0, -1}}};
        for (float[][] f : faces) {
            for (int i = 0; i < 4; i++) {
                Vector3f p = box.transformPosition(new Vector3f(f[i][0], f[i][1], f[i][2]));
                b.addVertex(p.x, p.y, p.z).setColor(1f, 1f, 1f, 1f).setNormal(f[4][0], f[4][1], f[4][2]);
            }
        }
        draw(sh, b);
        RenderSystem.disableCull();
    }

    // The blood pool under each one, growing with every Clivar.
    private static void blood(Dom d, double c, double s, List<LivingEntity> victims, float partial, Vec3 camera) {
        if (s < CLV0 + CMEAS || victims.isEmpty()) {
            return;
        }
        int hits = Math.max(0, (int) Math.floor((s - CLV0 - CMEAS) / CLV_DT) + 1);
        double R = Math.min(0.95, 0.18 + hits * 0.05 + 0.05 * sm(0, 4, ((s - CLV0 - CMEAS) % CLV_DT)));
        ShaderInstance sh = FxShaders.sdBlood;
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        premult();
        for (LivingEntity v : victims) {
            Vec3 f = toLocal(d, c, v.getPosition(partial));
            sh.safeGetUniform("R").set((float) R);
            sh.safeGetUniform("A").set(1f);
            sh.safeGetUniform("Seed").set((float) (v.getId() % 97) * 0.37f);
            plane(sh, v(f.x, 0.015, f.z), v(1.6, 0, 0), v(0, 0, 1.6));
        }
    }

    // ---------- the screen (after everything): the light dying, the black, the red rising, the aura and the eyes ----------

    public static void screen() {
        if (DOMS.isEmpty() || !shadersReady()) {
            return;
        }
        double now = now();
        Minecraft mc = Minecraft.getInstance();
        for (Dom d : DOMS) {
            double c = cut(d, now);
            double close = d.closeAt >= 0 ? cl((now - d.closeAt) / CLOSE_FADE, 0, 1) : 0;
            if (d.goneAt >= 0) {
                close = 1 - cl((now - d.goneAt) / 10, 0, 1);
            }
            boolean in = d.part || nearArena(d, mc.gameRenderer.getMainCamera().getPosition());
            if (c >= 0 && d.closeAt < 0 && in) {
                double s = src(c);
                boolean sceneHere = scene(d, c);
                boolean under = s >= T_DARK && s < T_RED && toLocal(d, c, mc.gameRenderer.getMainCamera().getPosition()).y < -0.02;
                if (sceneHere && s < T_BLACK && !under) {
                    // the light dying is the world's; under the water the sea has its own (it went near black, test 07:4x)
                    double light = s < T_HIT ? 1 : 1 - 0.55 * sm(T_HIT, 170, s);
                    if (s >= T_DARK) {
                        light *= (1 - 0.6 * sm(T_DARK, T_BLACK - 10, s)) * (1 - sm(T_BLACK - 10, T_BLACK - 2, s));
                    }
                    if (light < 0.999) {
                        flat((float) light, (float) light, (float) light, 1, true);
                    }
                }
                if (sceneHere) {
                    double a = T_HIT + 10, b = T_HIT + 18;
                    double k = 0, rise = -1;
                    if (s >= a && s < T_DARK) {
                        k = sm(a, b, s);
                    } else if (s >= T_DARK && s < T_DARK + 16) {
                        k = 1;
                        rise = 1.5 * sm(T_DARK + 1, T_DARK + 16, s);
                    }
                    if (k > 0.001) {
                        ShaderInstance ov = FxShaders.sdOv;
                        ov.safeGetUniform("Black").set((float) k);
                        ov.safeGetUniform("Rise").set((float) rise);
                        ov.safeGetUniform("T").set((float) (s / 20));
                        fullScreen(ov);
                    }
                }
                if ((sceneHere || d.quick && d.part) && s >= T_AURA && s < T_RED) {
                    ShaderInstance ab = FxShaders.sdAbyss;
                    ab.safeGetUniform("K").set((float) (s - T_AURA));
                    ab.safeGetUniform("Asp").set((float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight()));
                    ab.safeGetUniform("Six").set((float) (s >= T_SIX ? sm(T_SIX, T_SIX + 2, s) : 0));
                    ab.safeGetUniform("Red").set(1f);
                    fullScreen(ab);
                }
            }
            if (close > 0.001 && in) {
                flat(0, 0, 0, (float) close, false);
            }
        }
    }

    private static void fullScreen(ShaderInstance sh) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        premult();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.addVertex(-1, -1, 0);
        b.addVertex(1, -1, 0);
        b.addVertex(1, 1, 0);
        b.addVertex(-1, 1, 0);
        draw(sh, b);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.restoreProjectionMatrix();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void flat(float r, float g, float bl, float a, boolean multiply) {
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
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(-1, -1, 0).setColor(r, g, bl, a);
        b.addVertex(1, -1, 0).setColor(r, g, bl, a);
        b.addVertex(1, 1, 0).setColor(r, g, bl, a);
        b.addVertex(-1, 1, 0).setColor(r, g, bl, a);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.restoreProjectionMatrix();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    // ---------- what the people hold ----------

    // In the cutscene nobody holds anything: an item in hand was drawn over his face in the hand sign close-up (test
    // 2026-11-09). Emptied only for the draw, on this client, and put back right after.
    private static final Map<LivingEntity, net.minecraft.world.item.ItemStack[]> HELD = new HashMap<>();

    public static void onRenderLivingPre(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        if (DOMS.isEmpty() || !cutscene()) {
            return;
        }
        LivingEntity e = event.getEntity();
        net.minecraft.world.item.ItemStack main = e.getMainHandItem(), off = e.getOffhandItem();
        if (main.isEmpty() && off.isEmpty() || HELD.containsKey(e)) {
            return;
        }
        HELD.put(e, new net.minecraft.world.item.ItemStack[] {main, off});
        e.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        e.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
    }

    private static void restoreHeld() {
        for (Map.Entry<LivingEntity, net.minecraft.world.item.ItemStack[]> en : HELD.entrySet()) {
            en.getKey().setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, en.getValue()[0]);
            en.getKey().setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, en.getValue()[1]);
        }
        HELD.clear();
    }

    public static void onRenderLivingPost(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?> event) {
        if (HELD.isEmpty()) {
            return;
        }
        LivingEntity e = event.getEntity();
        net.minecraft.world.item.ItemStack[] items = HELD.remove(e);
        if (items != null) {
            e.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, items[0]);
            e.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, items[1]);
        }
    }

    // ---------- helpers ----------

    private static boolean shadersReady() {
        return FxShaders.sdSky != null && FxShaders.sdFloor != null && FxShaders.sdShrine != null && FxShaders.sdCol != null && FxShaders.sdMist != null
                && FxShaders.sdEmber != null && FxShaders.sdLine != null && FxShaders.sdScar != null && FxShaders.sdGrid != null && FxShaders.sdZig != null
                && FxShaders.sdBlood != null && FxShaders.sdAbyss != null && FxShaders.sdOv != null && FxShaders.sdUwBack != null && FxShaders.sdUwSurf != null
                && FxShaders.sdBubble != null && FxShaders.sdDrop != null && FxShaders.sdWave != null;
    }

    private static void premult() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    private static void draw(ShaderInstance shader, BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(mesh);
    }

    private static void sphere(ShaderInstance sh, Vec3 c, float r, int rings, int segs) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int i = 0; i < rings; i++) {
            double t0 = Math.PI * i / rings - Math.PI / 2, t1 = Math.PI * (i + 1) / rings - Math.PI / 2;
            for (int j = 0; j < segs; j++) {
                double p0 = Math.PI * 2 * j / segs, p1 = Math.PI * 2 * (j + 1) / segs;
                float[][] q = {sp(r, t0, p0), sp(r, t1, p0), sp(r, t1, p1), sp(r, t0, p1)};
                for (float[] v : q) {
                    b.addVertex((float) (c.x + v[0]), (float) (c.y + v[1]), (float) (c.z + v[2]));
                }
            }
        }
        draw(sh, b);
    }

    private static double cl(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }

    private static double sm(double a, double b, double x) {
        double u = cl((x - a) / (b - a), 0, 1);
        return u * u * (3 - 2 * u);
    }

    private static double hash(double n) {
        double s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return s - Math.floor(s);
    }

    private static double hk(double k, double j) {
        return hash(k * 7.13 + j * 31.7 + 0.5);
    }
}
