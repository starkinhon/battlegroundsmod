package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.CutsceneCamera;
import com.starkinhon.battlegroundsmod.client.FaceCutscene;
import com.starkinhon.battlegroundsmod.client.outfit.MeshModel;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel;
import com.starkinhon.battlegroundsmod.power.powers.duel.DuelRules;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The Duelo de Expansões on the client (power/powers/duel/DomainDuel on the server), ported from the preview Stark
// approved (forja/duelo-expansoes v2). Gojo's tunnel itself is DomainScene, its clock held still while the duel plays
// (the streaks keep flowing); this draws what the duel adds, with the mod's real pieces:
// - the Santuário (the same model as Sukuna's domain, SukunaDomainScene) sprouting out of the tunnel's floor behind
//   Sukuna, and sinking back if Gojo wins;
// - the sky split in two (souls_duel_sky): the Santuário's red on Sukuna's side, Gojo's cyan on his, the burning line
//   between them pushed by every move; in the tie, the red cracks the Santuário opens in the void's barrier;
// - the camera on the two (the shrine rising over Gojo's shoulder, the title wide, then their faces in turn);
// - the minigame's HUD (DuelRules: Sequência, Cabo de guerra or Refinamento) and the local fighter's keys, measured here
//   and sent to the server (ModPayloads.ExpansionDuelInput).
public final class DuelScene {
    private static final String[] MATERIALS = {"tile", "black", "dark", "wood", "gold", "post", "red", "void", "gum", "tooth",
            "bone", "horn", "hornB", "bark", "heap", "lamp"};
    private static final Map<String, MeshModel> SHRINE = new LinkedHashMap<>();
    private static boolean shrineBroken;
    // Where the Santuário stands behind Sukuna, its size, and how deep it starts under the floor.
    private static final double SHRINE_Z = -8.6, SHRINE_SCALE = 0.424, SHRINE_DEPTH = 19;
    private static final double SKY_R = 100;
    // How long it lingers after the server says it is over (the shrine sinking, the last cracks).
    private static final int LINGER = 70;
    private static final int GOJO_COLOR = 0x7FF3FF, SUKUNA_COLOR = 0xFF8A8A;

    private static final class Duel {
        final int gojo;
        final int sukuna;
        final double created = ScreenFx.now();
        double begin = -1;
        int seed;
        int game;
        char[] seq;
        final DuelRules.Round[] rounds = new DuelRules.Round[5];
        final int[] score = new int[2];
        // Per side, what each key or round scored (-1: nothing yet).
        final int[][] marks = {filled(10), filled(10)};
        // Per side, where the Refinamento's pointer was when they pressed (-1: no press).
        final double[][] pressedAt = {{-1, -1, -1, -1, -1}, {-1, -1, -1, -1, -1}};
        final int[] beats = new int[2];
        int result = -1;
        double resultAt = -1;
        double tieAt = -1;
        double crackStart = -1;
        int crackLen = 1;
        int reopens;
        double reopenAt = -1;
        double endAt = -1;
        double push;
        double pushTarget;
        double hitAt = -1;
        // Sukuna won: when the red starts down the tunnel (-1: not), and whether its front reached the camera yet.
        double redAt = -1;
        boolean redHit;
        int hitSide;
        // The local fighter's own presses.
        final boolean[] sent = new boolean[10];
        int lastBeat;
        int myBeats;
        boolean camera;
        final Set<String> fired = new HashSet<>();

        Duel(int gojo, int sukuna) {
            this.gojo = gojo;
            this.sukuna = sukuna;
        }

        boolean once(String key) {
            return fired.add(key);
        }
    }

    private static Duel duel;

    private DuelScene() {
    }

    private static int[] filled(int n) {
        int[] a = new int[n];
        java.util.Arrays.fill(a, -1);
        return a;
    }

    // ---------- from the server ----------

    public static void event(int gojo, int sukuna, int mode, int a, int b, int c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = ScreenFx.now();
        if (duel == null || duel.gojo != gojo || duel.sukuna != sukuna) {
            if (mode == DomainDuel.M_END) {
                return;
            }
            endCamera();
            duel = new Duel(gojo, sukuna);
        }
        Duel d = duel;
        switch (mode) {
            case DomainDuel.M_FIGHTERS -> {
                // Load the Santuário's model now, in the white, not on the first frame of the duel.
                shrineReady();
            }
            case DomainDuel.M_BEGIN -> {
                d.begin = now;
                d.seed = a;
                d.game = b;
                d.seq = DuelRules.sequence(a);
                for (int i = 0; i < 5; i++) {
                    d.rounds[i] = DuelRules.round(a, i);
                }
                com.starkinhon.battlegroundsmod.client.ScreenShake.start(2.5f, 34);
                ui(SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 0.6f);
            }
            case DomainDuel.M_SYNC -> {
                // The server's duel clock: a client that fell behind (a hitch, the first duel of the session) catches
                // up, or its presses arrive after the server's window and don't count (in-game test 2026-11-09: 8 x 0
                // with every key right, the client 22 ticks behind).
                if (d.begin >= 0 && Math.abs((now - d.begin) - a) > 1) {
                    d.begin = now - a;
                }
            }
            case DomainDuel.M_POINT -> {
                int side = Mth.clamp(a, 0, 1);
                d.score[side] += c;
                if (d.game == DuelRules.CABO) {
                    d.beats[side]++;
                } else if (b >= 0 && b < d.marks[side].length) {
                    d.marks[side][b] = c;
                }
                double push = DuelRules.push(d.game, c) * 0.11;
                d.pushTarget = Mth.clamp(d.pushTarget + (side == DomainDuel.GOJO ? push : -push), -1, 1);
                if (c > 0) {
                    d.hitAt = now;
                    d.hitSide = side;
                    boolean cabo = d.game == DuelRules.CABO;
                    if (side == DomainDuel.GOJO) {
                        ui(SoundEvents.AMETHYST_BLOCK_CHIME, cabo ? 0.35f : 0.9f, 1.2f + 0.1f * c);
                    } else {
                        ui(SoundEvents.PLAYER_ATTACK_SWEEP, cabo ? 0.3f : 0.8f, 1.1f + 0.1f * c);
                    }
                    if (!cabo) {
                        com.starkinhon.battlegroundsmod.client.ScreenShake.start(1.2f + c * 0.6f, 6);
                    }
                }
            }
            case DomainDuel.M_RESULT -> {
                if (a == DomainDuel.SUKUNA && d.result != DomainDuel.SUKUNA) {
                    // The red runs down Gojo's tunnel (DomainScene.redden), from right after the title.
                    d.redAt = now + DuelRules.TAKEOVER_FROM;
                    DomainScene.redden(d.gojo, d.redAt);
                }
                d.result = a;
                d.score[0] = b;
                d.score[1] = c;
                d.resultAt = now;
                d.pushTarget = a == DomainDuel.GOJO ? 1.5 : a == DomainDuel.SUKUNA ? -1.5 : 0;
                ScreenFx.flash(a == DomainDuel.GOJO ? 0xCFF8FF : a == DomainDuel.SUKUNA ? 0xFF3040 : 0xFFFFFF, 0.7f, 8);
                com.starkinhon.battlegroundsmod.client.ScreenShake.start(4f, 24);
                ui(a == DomainDuel.GOJO ? SoundEvents.BEACON_POWER_SELECT : a == DomainDuel.SUKUNA ? SoundEvents.WARDEN_ROAR : SoundEvents.GLASS_BREAK, 0.9f, 0.7f);
            }
            case DomainDuel.M_TIE -> {
                if (d.tieAt < 0) {
                    d.tieAt = now;
                }
                d.crackStart = now;
                d.crackLen = Math.max(1, a);
                d.reopens = b;
                if (c == 1) {
                    d.reopenAt = now;
                    ScreenFx.flash(0xCFF8FF, 0.6f, 10);
                }
            }
            case DomainDuel.M_END -> {
                if (d.endAt < 0) {
                    d.endAt = now;
                    if (d.tieAt >= 0) {
                        ScreenFx.flash(0xFF2030, 0.5f, 8);
                    }
                }
            }
            default -> {
            }
        }
    }

    // Sukuna, in the domain of the Gojo he fights: none of the information, the grey or the sinking view on him.
    public static boolean spares(int gojo, int entity) {
        Duel d = duel;
        return d != null && d.gojo == gojo && d.sukuna == entity && (d.endAt < 0 || d.result != DomainDuel.GOJO);
    }

    public static void clear() {
        endCamera();
        duel = null;
    }

    private static void ui(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ---------- time ----------

    private static double partial() {
        DeltaTracker timer = Minecraft.getInstance().getTimer();
        return timer.getGameTimeDeltaPartialTick(false);
    }

    // Ticks since the duel began (the Santuário starts to sprout), or -1.
    private static double dt(Duel d, double partial) {
        return d.begin < 0 ? -1 : ScreenFx.now() + partial - d.begin;
    }

    private static int side(Duel d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return -1;
        }
        int id = mc.player.getId();
        return id == d.gojo ? DomainDuel.GOJO : id == d.sukuna ? DomainDuel.SUKUNA : -1;
    }

    // The minigame is on: from the duel's start to the end of the result.
    private static boolean playing(Duel d, double t) {
        return d.begin >= 0 && t >= 0 && t < DuelRules.settleAt(d.game, d.result, DomainDuel.SUKUNA) && d.tieAt < 0 && (d.endAt < 0 || d.result >= 0);
    }

    // The camera is the duel's: for the two, and for anyone watching from inside the tunnel.
    public static boolean cutscene() {
        Duel d = duel;
        if (d == null || d.begin < 0) {
            return false;
        }
        double t = dt(d, 0);
        if (!playing(d, t) || (d.endAt >= 0 && ScreenFx.now() - d.endAt > 2)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        if (side(d) >= 0) {
            return true;
        }
        Entity gojo = mc.level.getEntity(d.gojo);
        return gojo != null && mc.player.distanceTo(gojo) < 64;
    }

    public static void tick() {
        Duel d = duel;
        Minecraft mc = Minecraft.getInstance();
        if (d == null) {
            return;
        }
        if (mc.level == null || (d.endAt >= 0 && ScreenFx.now() - d.endAt > LINGER)
                || (d.begin < 0 && ScreenFx.now() - d.created > 600)) {
            clear();
            return;
        }
        d.push += (d.pushTarget - d.push) * 0.18;
        if (d.redAt >= 0 && !d.redHit && ScreenFx.now() >= d.redAt + DomainScene.TAKEOVER_RUN * 0.8) {
            // The red's front runs past the camera: the screen floods red, the whole tunnel is his.
            d.redHit = true;
            ScreenFx.flash(0xFF1A2A, 0.75f, 12);
            com.starkinhon.battlegroundsmod.client.ScreenShake.start(5f, 20);
            ui(SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 0.55f);
        }
        boolean cam = cutscene();
        if (cam && !d.camera) {
            d.camera = true;
            CutsceneCamera.begin("duel");
        }
        if (cam) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (d.camera) {
            endCamera();
        }
    }

    // The fighter's keys are the minigame's: no inventory, no dropping, no swapping hands while it plays. Called at the
    // START of the client tick (ClientTickEvent.Pre): Minecraft reads the clicks in its own tick (handleKeybinds), so
    // draining them after it (Post) was too late and E still opened the inventory (in-game test 2026-11-09).
    public static void drainKeys() {
        Duel d = duel;
        Minecraft mc = Minecraft.getInstance();
        if (d == null || mc.level == null || side(d) < 0 || !playing(d, dt(d, 0))) {
            return;
        }
        while (mc.options.keyInventory.consumeClick()) {
        }
        while (mc.options.keyDrop.consumeClick()) {
        }
        while (mc.options.keySwapOffhand.consumeClick()) {
        }
        while (mc.options.keyJump.consumeClick()) {
        }
    }

    private static void endCamera() {
        Duel d = duel;
        if (d != null && d.camera) {
            d.camera = false;
            CutsceneCamera.end("duel");
        }
    }

    // ---------- the keys ----------

    public static void onKey(InputEvent.Key event) {
        Duel d = duel;
        Minecraft mc = Minecraft.getInstance();
        if (d == null || d.begin < 0 || mc.screen != null || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (side(d) < 0) {
            return;
        }
        double t = dt(d, partial());
        if (!playing(d, t) || t > DuelRules.gameEnd(d.game)) {
            return;
        }
        char k = switch (event.getKey()) {
            case GLFW.GLFW_KEY_W -> 'W';
            case GLFW.GLFW_KEY_A -> 'A';
            case GLFW.GLFW_KEY_S -> 'S';
            case GLFW.GLFW_KEY_D -> 'D';
            case GLFW.GLFW_KEY_Q -> 'Q';
            case GLFW.GLFW_KEY_E -> 'E';
            case GLFW.GLFW_KEY_SPACE -> ' ';
            default -> 0;
        };
        if (k == 0) {
            return;
        }
        switch (d.game) {
            case DuelRules.SEQ -> {
                // Only the first press of each key's window counts.
                int i = DuelRules.slotAt(t);
                if (k == ' ' || i < 0 || d.sent[i]) {
                    return;
                }
                d.sent[i] = true;
                boolean ok = k == d.seq[i];
                PacketDistributor.sendToServer(new ModPayloads.ExpansionDuelInput(i, ok ? 1 : 0));
                if (!ok) {
                    ui(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.6f);
                }
            }
            case DuelRules.CABO -> {
                if ((k != 'A' && k != 'D') || k == d.lastBeat || t < DuelRules.K0 || t >= DuelRules.K0 + DuelRules.CABO_LEN) {
                    return;
                }
                d.lastBeat = k;
                PacketDistributor.sendToServer(new ModPayloads.ExpansionDuelInput(d.myBeats++, 1));
            }
            default -> {
                int i = DuelRules.roundAt(t);
                if (k != ' ' || i < 0 || d.sent[i]) {
                    return;
                }
                d.sent[i] = true;
                int pts = DuelRules.points(d.rounds[i], i, t);
                d.pressedAt[side(d)][i] = DuelRules.pointer(d.rounds[i], i, t);
                PacketDistributor.sendToServer(new ModPayloads.ExpansionDuelInput(i, pts));
                if (pts == 0) {
                    ui(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.6f);
                }
            }
        }
    }

    // ---------- the camera ----------

    private record Frame(Vec3 g, Vec3 s, Vec3 eyeG, Vec3 eyeS, Vec3 f, Vec3 r, Vec3 mid) {
    }

    private static Frame frame(Duel d, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity gojo = mc.level.getEntity(d.gojo);
        Entity sukuna = mc.level.getEntity(d.sukuna);
        if (gojo == null || sukuna == null) {
            return null;
        }
        Vec3 g = gojo.getPosition(partial);
        Vec3 s = sukuna.getPosition(partial);
        Vec3 to = new Vec3(s.x - g.x, 0, s.z - g.z);
        Vec3 f = to.lengthSqr() > 1.0E-4 ? to.normalize() : Vec3.directionFromRotation(0, gojo.getYRot());
        Vec3 r = new Vec3(-f.z, 0, f.x);
        return new Frame(g, s, g.add(0, gojo.getEyeHeight(), 0), s.add(0, sukuna.getEyeHeight(), 0), f, r, g.add(s).scale(0.5));
    }

    public static FaceCutscene.Shot shot(float partial) {
        Duel d = duel;
        if (d == null) {
            return null;
        }
        Frame w = frame(d, partial);
        if (w == null) {
            return null;
        }
        double t = dt(d, partial);
        Vec3 p;
        Vec3 l;
        Vec3 up = new Vec3(0, 1, 0);
        int end = DuelRules.gameEnd(d.game);
        if (t < DuelRules.RISE_END + 2) {
            // over Gojo's shoulder: the Santuário rising out of his tunnel behind Sukuna
            double u = sm(0, DuelRules.RISE_END, t);
            p = w.eyeG().subtract(w.f().scale(3.6 - 0.6 * u)).add(w.r().scale(1.3)).add(up.scale(0.5));
            l = w.s().add(w.f().scale(-SHRINE_Z)).add(up.scale(3 + 6 * u));
        } else if (t < DuelRules.K0) {
            // wide, from the side: the two, the shrine, the split sky
            double u = sm(DuelRules.RISE_END, DuelRules.K0, t);
            p = w.mid().add(w.r().scale(15 - 2 * u)).add(up.scale(2.6)).subtract(w.f().scale(2));
            l = w.mid().add(w.f().scale(3)).add(up.scale(3));
        } else if (t < end) {
            int face = faceAt(d, t);
            if (face < 0) {
                double u = sm(end - 30, end, t);
                p = w.mid().add(w.r().scale(13 + 2 * u)).add(up.scale(2.4 + u)).subtract(w.f().scale(4));
                l = w.mid().add(w.f().scale(3)).add(up.scale(2.5));
            } else if (face == DomainDuel.GOJO) {
                double drift = 0.15 * Math.sin(t * 0.05);
                p = w.eyeG().add(w.f().scale(2.4)).add(w.r().scale(0.55 + drift)).add(up.scale(0.12));
                l = w.eyeG().add(up.scale(0.02));
            } else {
                double drift = 0.15 * Math.sin(t * 0.05);
                p = w.eyeS().subtract(w.f().scale(2.4)).subtract(w.r().scale(0.9 + drift)).add(up.scale(0.25));
                l = w.eyeS().add(up.scale(0.02));
            }
        } else {
            // the result, high and wide, on the winner's side
            double u = sm(end, DuelRules.resultEnd(d.game), t);
            Vec3 lean = d.result == DomainDuel.GOJO ? w.f().scale(-3) : d.result == DomainDuel.SUKUNA ? w.f().scale(5) : Vec3.ZERO;
            p = w.mid().add(w.r().scale(17 + 3 * u)).add(up.scale(5 + 2 * u)).add(lean);
            l = w.mid().add(w.f().scale(3)).add(up.scale(4)).add(lean);
            if (d.result == DomainDuel.SUKUNA) {
                // The red taking the tunnel over: over Sukuna's shoulder, down the tunnel past Gojo to its far end, where
                // the red comes from (the tunnel runs away behind Gojo, DomainScene.tunnelAxis).
                int re = DuelRules.resultEnd(d.game);
                double k = sm(re - 8, re + 2, t);
                double push = 0.6 * sm(re, DuelRules.settleAt(d.game, d.result, DomainDuel.SUKUNA), t);
                Vec3 p2 = w.eyeS().add(w.f().scale(3.2 - push)).add(w.r().scale(1.1)).add(up.scale(0.55));
                Vec3 l2 = w.eyeG().subtract(w.f().scale(12)).add(up.scale(0.3));
                p = p.lerp(p2, k);
                l = l.lerp(l2, k);
            }
        }
        Vec3 dir = l.subtract(p);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        return new FaceCutscene.Shot(p, yaw, pitch);
    }

    // Whose face the camera is on during the game (the preview's cuts), or -1 for wide.
    private static int faceAt(Duel d, double t) {
        if (d.game == DuelRules.SEQ) {
            int i = 9;
            for (int k = 0; k < 10; k++) {
                if (t < DuelRules.slotEnd(k) + DuelRules.GAP) {
                    i = k;
                    break;
                }
            }
            if (i >= 8) {
                return -1;
            }
            return (i / 2) % 2 == 0 ? DomainDuel.GOJO : DomainDuel.SUKUNA;
        }
        if (d.game == DuelRules.REF) {
            int i = 4;
            for (int k = 0; k < 5; k++) {
                if (t < DuelRules.roundEnd(k) + DuelRules.RGAP) {
                    i = k;
                    break;
                }
            }
            if (i >= 4) {
                return -1;
            }
            return i % 2 == 0 ? DomainDuel.GOJO : DomainDuel.SUKUNA;
        }
        double c = t - DuelRules.K0;
        if (c > DuelRules.CABO_LEN - 25) {
            return -1;
        }
        return ((int) (c / 25)) % 2 == 0 ? DomainDuel.GOJO : DomainDuel.SUKUNA;
    }

    // ---------- the world: the sky and the shrine ----------

    public static void render(Vec3 camera, float partial) {
        Duel d = duel;
        if (d == null || d.begin < 0) {
            return;
        }
        if (d.result == DomainDuel.SUKUNA && d.endAt >= 0) {
            // The Santuário took the tunnel over: its own cutscene (SukunaDomainScene) has the screen from the six eyes.
            return;
        }
        Frame w = frame(d, partial);
        if (w == null || camera.distanceTo(w.mid()) > 90) {
            return;
        }
        double now = ScreenFx.now() + partial;
        double t = now - d.begin;
        try {
            sky(d, w, t, now);
            shrine(d, w, t, now, camera);
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void sky(Duel d, Frame w, double t, double now) {
        ShaderInstance sh = FxShaders.duelSky;
        if (sh == null) {
            return;
        }
        float mode;
        float alpha;
        float crack = 0;
        if (d.tieAt >= 0) {
            mode = 1;
            crack = (float) Mth.clamp((now - d.crackStart) / d.crackLen, 0, 1);
            alpha = (float) sm(d.tieAt, d.tieAt + 10, now);
            if (d.endAt >= 0) {
                crack = 1;
                alpha *= (float) (1 - sm(d.endAt + 4, d.endAt + 16, now));
            }
        } else {
            mode = 0;
            alpha = (float) sm(4, 22, t);
            int resultEnd = DuelRules.resultEnd(d.game) + (d.result == DomainDuel.SUKUNA ? DuelRules.TAKEOVER : 0);
            if (d.result == DomainDuel.GOJO) {
                alpha *= (float) (1 - sm(resultEnd, resultEnd + 26, t));
            } else if (d.result == DomainDuel.SUKUNA) {
                alpha *= (float) (1 - sm(resultEnd + 14, resultEnd + 26, t));
            } else if (d.result == DomainDuel.TIE) {
                alpha *= (float) (1 - sm(resultEnd - 6, resultEnd, t));
            }
            if (d.endAt >= 0 && d.result < 0) {
                alpha *= (float) (1 - sm(d.endAt, d.endAt + 10, now));
            }
        }
        if (alpha <= 0.002f) {
            return;
        }
        double hit = d.hitAt < 0 ? 0 : Math.exp(-(now - d.hitAt) / 5.0);
        double flash = d.resultAt < 0 ? 0 : 0.6 * Math.exp(-(now - d.resultAt) / 4.0);
        if (d.reopenAt >= 0) {
            flash = Math.max(flash, 0.5 * Math.exp(-(now - d.reopenAt) / 4.0));
        }
        sh.safeGetUniform("T").set((float) (now / 20.0));
        sh.safeGetUniform("Push").set((float) d.push);
        sh.safeGetUniform("Axis").set((float) w.f().x, 0f, (float) w.f().z);
        sh.safeGetUniform("Mode").set(mode);
        sh.safeGetUniform("Crack").set(crack);
        sh.safeGetUniform("Alpha").set(alpha);
        sh.safeGetUniform("Flash").set((float) flash);
        sh.safeGetUniform("Seed").set((float) (d.seed % 997) * 0.37f);
        sh.safeGetUniform("Hit").set((float) hit);
        sh.safeGetUniform("HitSide").set((float) d.hitSide);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        int rings = 32, segs = 64;
        for (int i = 0; i < rings; i++) {
            double t0 = Math.PI * i / rings - Math.PI / 2, t1 = Math.PI * (i + 1) / rings - Math.PI / 2;
            for (int j = 0; j < segs; j++) {
                double p0 = Math.PI * 2 * j / segs, p1 = Math.PI * 2 * (j + 1) / segs;
                vert(b, t0, p0);
                vert(b, t1, p0);
                vert(b, t1, p1);
                vert(b, t0, p1);
            }
        }
        draw(sh, b);
    }

    private static void vert(BufferBuilder b, double lat, double lon) {
        double c = Math.cos(lat);
        b.addVertex((float) (SKY_R * c * Math.cos(lon)), (float) (SKY_R * Math.sin(lat)), (float) (SKY_R * c * Math.sin(lon)));
    }

    private static boolean shrineReady() {
        if (shrineBroken || FxShaders.sdShrine == null) {
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
                BattlegroundsMod.LOGGER.error("Malevolent Shrine model failed to load for the domain duel", e);
                return false;
            }
        }
        return true;
    }

    // The Santuário behind Sukuna, facing Gojo: out of the tunnel's floor as the duel begins, back under it if Gojo wins.
    private static void shrine(Duel d, Frame w, double t, double now, Vec3 camera) {
        if (d.tieAt >= 0 || !shrineReady()) {
            return;
        }
        double rise = ease(sm(0, DuelRules.RISE_END, t));
        double tilt = 0;
        double lit = 1;
        if (d.result == DomainDuel.GOJO && d.resultAt >= 0) {
            // it falls: shaken, tipping, swallowed by the floor, its light going out
            double k = sm(d.resultAt + 6, d.resultAt + 46, now);
            rise *= 1 - k * k;
            tilt = 0.22 * k;
            lit = 1 - 0.7 * k;
        }
        if (d.result == DomainDuel.SUKUNA && d.endAt >= 0 && now - d.endAt > 24) {
            // the tunnel shattered: Sukuna's own domain takes it from here (SukunaDomainScene)
            return;
        }
        if (d.endAt >= 0 && d.result < 0) {
            return;
        }
        if (rise <= 0.001) {
            return;
        }
        double quake = rise < 1 ? (1 - rise) : 0;
        double yaw = Math.atan2(w.g().z - w.s().z, w.g().x - w.s().x) * Mth.RAD_TO_DEG - 90;
        double a = yaw * Mth.DEG_TO_RAD;
        Vec3 origin = w.s();
        Vec3 o = origin.subtract(camera);
        // The camera in the shrine's own frame (as SukunaDomainScene.toLocal), mirrored like the model below.
        Vec3 pc = camera.subtract(origin);
        double cs = Math.cos(a), sn = Math.sin(a);
        Vec3 camL = new Vec3(pc.x * cs + pc.z * sn, pc.y, -pc.x * sn + pc.z * cs);
        // Drawn upside down through the shrine shader's mirror (which keeps only what is under the floor) and flipped back
        // by the view: so the floor cuts it as it rises, and it is lit as it always is.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(new Matrix4f().translation((float) o.x, (float) o.y, (float) o.z).rotateY((float) -a).scale(1, -1, 1));
        RenderSystem.applyModelViewMatrix();
        try {
            float shake = (float) (quake * 0.18 * Math.sin(now * 2.7));
            Matrix4f model = new Matrix4f().scale(1, -1, 1)
                    .translate(shake, (float) (-(1 - rise) * SHRINE_DEPTH), (float) SHRINE_Z)
                    .rotateX((float) -tilt).rotateZ((float) (tilt * 0.4))
                    .scale((float) SHRINE_SCALE);
            ShaderInstance sh = FxShaders.sdShrine;
            sh.safeGetUniform("ModelMat").set(model);
            sh.safeGetUniform("T").set((float) (now / 20.0));
            sh.safeGetUniform("Lit").set((float) lit);
            sh.safeGetUniform("Rim").set(0.35f);
            sh.safeGetUniform("Amb").set((float) (0.55 + 0.25 * Math.exp(-Math.max(0, t - DuelRules.RISE_END) / 10.0)));
            sh.safeGetUniform("Mirror").set(1f);
            sh.safeGetUniform("CamL").set((float) camL.x, (float) -camL.y, (float) camL.z);
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
            // back to the shader's usual state for Sukuna's own domain
            sh.safeGetUniform("Mirror").set(0f);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
        if (t < DuelRules.RISE_END && d.once("rise")) {
            ui(SoundEvents.WARDEN_EMERGE, 0.9f, 0.5f);
        }
    }

    // ---------- the HUD ----------

    public static void renderGui(GuiGraphics g, DeltaTracker delta) {
        Duel d = duel;
        Minecraft mc = Minecraft.getInstance();
        if (d == null || d.begin < 0 || mc.player == null || mc.level == null) {
            return;
        }
        Entity gojo = mc.level.getEntity(d.gojo);
        if (side(d) < 0 && (gojo == null || mc.player.distanceTo(gojo) > 64)) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        double now = ScreenFx.now() + partial;
        double t = now - d.begin;
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        if (d.tieAt >= 0) {
            tieHud(g, font, d, now, cx);
            return;
        }
        if (!playing(d, t)) {
            return;
        }
        // the title
        if (t >= DuelRules.TITLE && t < DuelRules.K0 + 4) {
            int alpha = (int) (255 * sm(DuelRules.TITLE, DuelRules.TITLE + 5, t) * (1 - sm(DuelRules.K0 - 2, DuelRules.K0 + 4, t)));
            if (alpha > 8) {
                centered(g, font, "DUELO DE EXPANSÕES", cx, (int) (g.guiHeight() * 0.36), 2.4f, (alpha << 24) | 0xFFFFFF);
                centered(g, font, DuelRules.NAMES[d.game].toUpperCase(), cx, (int) (g.guiHeight() * 0.36) + 26, 1.3f, (alpha << 24) | 0xE6E9EF);
            }
        }
        int end = DuelRules.gameEnd(d.game);
        // The game's HUD gives way to the result (they would sit on top of each other on a small screen).
        if (t >= DuelRules.MG && t < end + 2 && d.result < 0) {
            minigame(g, font, d, t, cx, g.guiHeight() - 92);
        }
        if (d.result >= 0 && d.resultAt >= 0) {
            int alpha = (int) (255 * sm(d.resultAt, d.resultAt + 4, now) * (1 - sm(d.resultAt + DuelRules.RESULT - 4, d.resultAt + DuelRules.RESULT + 2, now)));
            if (alpha > 8) {
                String title = d.result == DomainDuel.GOJO ? "GOJO VENCE" : d.result == DomainDuel.SUKUNA ? "SUKUNA VENCE" : "EMPATE";
                int col = d.result == DomainDuel.GOJO ? GOJO_COLOR : d.result == DomainDuel.SUKUNA ? SUKUNA_COLOR : 0xFFFFFF;
                centered(g, font, title, cx, (int) (g.guiHeight() * 0.34), 3f, (alpha << 24) | col);
                centered(g, font, d.score[0] + "  x  " + d.score[1], cx, (int) (g.guiHeight() * 0.34) + 32, 1.6f, (alpha << 24) | 0xFFFFFF);
                String sub = d.result == DomainDuel.GOJO ? "O Santuário cai" : d.result == DomainDuel.SUKUNA ? "O túnel é dominado" : "Os dois vão pro Vazio";
                centered(g, font, sub, cx, (int) (g.guiHeight() * 0.34) + 50, 1f, (alpha << 24) | 0xE6E9EF);
            }
        }
    }

    private static void minigame(GuiGraphics g, Font font, Duel d, double t, int cx, int y) {
        int you = side(d);
        String[] names = {"Gojo" + (you == 0 ? " (você)" : ""), "Sukuna" + (you == 1 ? " (você)" : "")};
        // the score
        String gs = String.valueOf(d.score[0]);
        String ss = String.valueOf(d.score[1]);
        float sc = 1.8f;
        int w = (int) ((font.width(gs + "  x  " + ss)) * sc);
        int x0 = cx - w / 2;
        scaled(g, font, gs, x0, y - 22, sc, 0xFF000000 | GOJO_COLOR);
        scaled(g, font, "  x  ", x0 + (int) (font.width(gs) * sc), y - 22, sc, 0xFFFFFFFF);
        scaled(g, font, ss, x0 + (int) (font.width(gs + "  x  ") * sc), y - 22, sc, 0xFF000000 | SUKUNA_COLOR);
        String hint;
        if (d.game == DuelRules.SEQ) {
            int box = 18, gap = 3, n = 10;
            int left = cx - (n * box + (n - 1) * gap) / 2;
            int cur = DuelRules.slotAt(t);
            for (int i = 0; i < n; i++) {
                int bx = left + i * (box + gap);
                boolean now = i == cur;
                boolean past = t >= DuelRules.slotEnd(i);
                int grow = now ? 2 : 0;
                g.fill(bx - grow, y - grow, bx + box + grow, y + box + grow, now ? 0x55FFFFFF : 0x8C0A0C10);
                frame(g, bx - grow, y - grow, bx + box + grow, y + box + grow, now ? 0xFFFFFFFF : 0x2EFFFFFF);
                String k = t >= DuelRules.slotStart(i) ? String.valueOf(d.seq[i]) : "·";
                int col = past ? 0x73FFFFFF : 0xFFFFFFFF;
                g.drawString(font, k, bx + box / 2 - font.width(k) / 2, y + 5, col, true);
                for (int s = 0; s < 2; s++) {
                    int dy = y + box + 6 + s * 8;
                    int m = d.marks[s][i];
                    int c = m > 0 ? (s == 0 ? 0xFF22D3EE : 0xFFEF4444) : (past || m == 0) ? 0x59FFFFFF : 0x1FFFFFFF;
                    g.fill(bx, dy, bx + box, dy + 4, c);
                }
            }
            for (int s = 0; s < 2; s++) {
                String nm = names[s];
                g.drawString(font, nm, left - 6 - font.width(nm), y + box + 4 + s * 8, 0xFF000000 | (s == 0 ? GOJO_COLOR : SUKUNA_COLOR), true);
            }
            if (cur >= 0) {
                double u = 1 - (t - DuelRules.slotStart(cur)) / (DuelRules.slotEnd(cur) - DuelRules.slotStart(cur));
                int bw = n * box + (n - 1) * gap;
                g.fill(left, y + box + 24, left + bw, y + box + 26, 0x26FFFFFF);
                g.fill(left, y + box + 24, left + (int) (bw * u), y + box + 26, 0xFFFFFFFF);
            }
            hint = you >= 0 ? "W A S D Q E" : "";
        } else if (d.game == DuelRules.CABO) {
            int bw = 230, bh = 12;
            int left = cx - bw / 2;
            double f = Mth.clamp(50 + (d.score[0] - d.score[1]) * 2.2, 4, 96) / 100.0;
            int split = left + (int) (bw * f);
            g.fill(left - 2, y - 2, left + bw + 2, y + bh + 2, 0x40FFFFFF);
            g.fillGradient(left, y, split, y + bh, 0xFF0E7490, 0xFF22D3EE);
            g.fillGradient(split, y, left + bw, y + bh, 0xFFEF4444, 0xFF7F1D1D);
            g.fill(split - 2, y - 3, split + 2, y + bh + 3, 0xFFFFFFFF);
            double secs = Math.max(0.5, (Mth.clamp(t, DuelRules.K0, DuelRules.K0 + DuelRules.CABO_LEN) - DuelRules.K0) / 20.0);
            for (int s = 0; s < 2; s++) {
                String line = names[s] + "   " + String.format(java.util.Locale.ROOT, "%.1f", d.beats[s] / secs) + " batidas/s";
                g.drawString(font, line, cx - font.width(line) / 2, y + bh + 6 + s * 10, 0xFF000000 | (s == 0 ? GOJO_COLOR : SUKUNA_COLOR), true);
            }
            double left2 = Math.max(0, (DuelRules.K0 + DuelRules.CABO_LEN - t) / 20.0);
            hint = t < DuelRules.K0 ? "" : left2 > 0 ? String.format(java.util.Locale.ROOT, "%.1f s", left2) + (you >= 0 ? "   ·   A D A D" : "") : "";
        } else {
            int bw = 230, bh = 16;
            int left = cx - bw / 2;
            int i = DuelRules.roundAt(t);
            g.fill(left - 2, y - 2, left + bw + 2, y + bh + 2, 0x33FFFFFF);
            g.fill(left, y, left + bw, y + bh, 0x990A0C10);
            if (i >= 0) {
                DuelRules.Round r = d.rounds[i];
                int z0 = left + (int) (bw * (r.center() - r.half())), z1 = left + (int) (bw * (r.center() + r.half()));
                int c0 = left + (int) (bw * (r.center() - r.half() * 0.3)), c1 = left + (int) (bw * (r.center() + r.half() * 0.3));
                g.fill(z0, y, z1, y + bh, 0x38FFFFFF);
                g.fill(c0, y, c1, y + bh, 0x99FFFFFF);
                for (int s = 0; s < 2; s++) {
                    double at = d.pressedAt[s][i];
                    if (at >= 0) {
                        int mx = left + (int) (bw * at);
                        g.fill(mx - 1, y - 4, mx + 2, y + bh + 4, s == 0 ? 0xFF22D3EE : 0xFFEF4444);
                    }
                }
                int px = left + (int) (bw * DuelRules.pointer(r, i, t));
                g.fill(px - 2, y - 3, px + 2, y + bh + 3, 0xFFFFFFFF);
            }
            int box = 30, gap = 6;
            int dl = cx - (5 * box + 4 * gap) / 2;
            for (int k = 0; k < 5; k++) {
                for (int s = 0; s < 2; s++) {
                    int m = d.marks[s][k];
                    boolean done = t >= DuelRules.roundEnd(k);
                    int c = m == 2 ? (s == 0 ? 0xFF22D3EE : 0xFFEF4444) : m == 1 ? (s == 0 ? 0xAA22D3EE : 0xAAEF4444) : (done || m == 0) ? 0x59FFFFFF : 0x1FFFFFFF;
                    int dy = y + bh + 8 + s * 9;
                    g.fill(dl + k * (box + gap), dy, dl + k * (box + gap) + box, dy + (m == 2 ? 6 : 4), c);
                }
            }
            for (int s = 0; s < 2; s++) {
                String nm = names[s];
                g.drawString(font, nm, dl - 6 - font.width(nm), y + bh + 6 + s * 9, 0xFF000000 | (s == 0 ? GOJO_COLOR : SUKUNA_COLOR), true);
            }
            hint = i >= 0 ? "rodada " + (i + 1) + " de 5" + (you >= 0 ? "   ·   Espaço" : "") : "";
        }
        if (t < DuelRules.K0) {
            int count = (int) Math.ceil((DuelRules.K0 - t) / 6.7);
            String how = d.game == DuelRules.SEQ ? "aperte a tecla que aparecer: W A S D Q E" : d.game == DuelRules.CABO ? "A e D alternando, o mais rápido que der" : "aperte Espaço com o ponteiro na zona";
            hint = (you >= 0 ? how : "assistindo") + "   ·   " + count;
        }
        // The game's name over the score, once the title (which already says it, in the middle) is gone.
        if (t >= DuelRules.K0 + 4) {
            g.drawString(font, DuelRules.NAMES[d.game].toUpperCase(), cx - font.width(DuelRules.NAMES[d.game].toUpperCase()) / 2, y - 36, 0xCCFFFFFF, true);
        }
        if (!hint.isEmpty()) {
            g.drawString(font, hint, cx - font.width(hint) / 2, y + 52, 0xFFFFFFFF, true);
        }
    }

    // The tie: the barrier's cracks as a bar, and for Gojo how to reopen it.
    private static void tieHud(GuiGraphics g, Font font, Duel d, double now, int cx) {
        if (d.endAt >= 0) {
            return;
        }
        double crack = Mth.clamp((now - d.crackStart) / d.crackLen, 0, 1);
        int bw = 180, y = 22;
        int left = cx - bw / 2;
        String label = "Barreira do Vazio" + (d.reopens > 0 ? "   (reaberta " + d.reopens + "x)" : "");
        g.drawString(font, label, cx - font.width(label) / 2, y - 11, 0xFFE6E9EF, true);
        g.fill(left - 1, y - 1, left + bw + 1, y + 7, 0x66FFFFFF);
        g.fill(left, y, left + (int) (bw * (1 - crack)), y + 6, 0xFF22D3EE);
        g.fill(left + (int) (bw * (1 - crack)), y, left + bw, y + 6, 0xFFB91C1C);
        if (side(d) == DomainDuel.GOJO) {
            String hint = "V reabre o Vazio (estamina pesada)";
            g.drawString(font, hint, cx - font.width(hint) / 2, y + 11, 0xFF7FF3FF, true);
        }
    }

    private static void centered(GuiGraphics g, Font font, String text, int cx, int y, float scale, int color) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, y, 0);
        pose.scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, color, true);
        pose.popPose();
    }

    private static void scaled(GuiGraphics g, Font font, String text, int x, int y, float scale, int color) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, true);
        pose.popPose();
    }

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    // ---------- helpers ----------

    private static void draw(ShaderInstance shader, BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(mesh);
    }

    private static double sm(double a, double b, double x) {
        double u = Mth.clamp((x - a) / (b - a), 0, 1);
        return u * u * (3 - 2 * u);
    }

    private static double ease(double u) {
        // out-back: the shrine slams up and settles
        double c1 = 1.4, c3 = c1 + 1;
        return u <= 0 ? 0 : u >= 1 ? 1 : 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);
    }
}
