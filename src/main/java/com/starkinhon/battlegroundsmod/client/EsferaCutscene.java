// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;
import com.starkinhon.battlegroundsmod.client.fx.SkillEffects;
import com.starkinhon.battlegroundsmod.skill.EsferaCena;
import com.starkinhon.battlegroundsmod.skill.EsferaSpec;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// Bills' Esfera da Destruição Planetária (Shift + C), seen by Bills and his target: the cutscene remade with Stark on
// 2026-11-26 in the web preview (forja/bills-esfera-cutscene, approved after 17 rounds). Every shot, pose and the ball
// come from skill/EsferaCena (the preview's math); this class lays them out in the world from where Bills stood and the
// way he faced when it started, runs the camera (position, aim, lens and dutch tilt) and the white fades, and
// client/fx/SpaceScene draws outer space, the two bodies and the sun. On the server both are held high up in the sky
// meanwhile (power/powers/bills/Esfera), so nothing down here can see, hit or chase them.
public final class EsferaCutscene {
    private static final double BAR_HEIGHT = 0.11;

    private static int casterId = -1;
    private static int targetId = -1;
    private static boolean awakened;
    private static EsferaSpec spec;
    private static EsferaCena cena;
    private static int age;
    private static Vec3 origin = Vec3.ZERO;
    private static Vec3 forward = new Vec3(0, 0, 1);
    private static float yaw;
    // The frame worked out last (the camera, its lens and tilt, the reference points), reused by everything drawn in it.
    private static Frame frame;

    private EsferaCutscene() {
    }

    // One moment of the cutscene, in Bills' frame (x right, y up, z ahead, from his feet): see EsferaCena.
    public record Frame(double t, EsferaCena.Corpo bills, EsferaCena.Corpo alvo, double[] olhos, double[] peito, double[] alvoAt,
            EsferaCena.Bola bola, double[] esf, EsferaCena.Shot shot) {
    }

    public static void start(int caster, int target, boolean isAwakened) {
        Minecraft minecraft = Minecraft.getInstance();
        EsferaSpec next = EsferaSpec.get(isAwakened);
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(caster);
        if (!next.cutscene().ativa() || entity == null) {
            return;
        }
        if (casterId < 0) {
            CutsceneCamera.begin("esfera");
        }
        casterId = caster;
        targetId = target;
        awakened = isAwakened;
        spec = next;
        cena = new EsferaCena(next);
        age = 0;
        frame = null;
        origin = entity.position();
        forward = Vec3.directionFromRotation(0, entity.getYRot());
        yaw = entity.getYRot();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        minecraft.options.hideGui = true;
        com.starkinhon.battlegroundsmod.client.fx.SpaceScene.warm();
    }

    public static boolean active() {
        return casterId >= 0;
    }

    // The space scene is up.
    public static boolean inSpace() {
        return casterId >= 0 && age >= cena.espaco && age < cena.fim;
    }

    public static int casterId() {
        return casterId;
    }

    public static int targetId() {
        return targetId;
    }

    public static EsferaSpec spec() {
        return spec;
    }

    public static EsferaCena cena() {
        return cena;
    }

    public static Vec3 origin() {
        return origin;
    }

    public static Vec3 forward() {
        return forward;
    }

    // The way he faced when it started: the whole scene is laid out from it.
    public static float yaw() {
        return yaw;
    }

    public static Vec3 right() {
        return new Vec3(-forward.z, 0, forward.x);
    }

    // Ticks since the cutscene started, with the frame's fraction.
    public static double time(float partialTick) {
        return age + partialTick;
    }

    // A point in Bills' frame (x right, y up, z ahead, from his feet where he stood) in the world.
    public static Vec3 world(double[] p) {
        Vec3 r = right();
        return origin.add(r.x * p[0] + forward.x * p[2], p[1], r.z * p[0] + forward.z * p[2]);
    }

    // Everything at this moment (worked out once per frame).
    public static Frame frame(float partialTick) {
        double t = time(partialTick);
        Frame last = frame;
        if (last != null && last.t() == t) {
            return last;
        }
        EsferaCena.Corpo bills = cena.bills(t);
        EsferaCena.Corpo alvo = cena.alvo(t);
        EsferaCena.Raiz r = bills.raiz();
        double[] olhos = {r.x(), r.y() + 1.62, r.z()};
        double[] peito = {r.x(), r.y() + 1.1, r.z()};
        double[] alvoAt = cena.alvoPos(t);
        EsferaCena.Bola bola = cena.esfera(t);
        double[] esf = cena.bolaPos(t, bola);
        EsferaCena.Shot shot = cena.camera(t, olhos, peito, alvoAt, esf != null ? esf : peito);
        Frame next = new Frame(t, bills, alvo, olhos, peito, alvoAt, bola, esf, shot);
        frame = next;
        return next;
    }

    public static void tick() {
        if (casterId < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getEntity(casterId) == null) {
            stop(minecraft);
            return;
        }
        age++;
        // The moments only the two screens in space see.
        if (age == (int) cena.espaco) {
            SkillEffects.play(EsferaSpec.SKILL, awakened, "chegada", world(new double[] {0, 0, 0}), forward, -1, 0);
        } else if (age == (int) cena.implode) {
            // (its effects wait for the burst into the giant sun, "inicio" in the json)
            double[] at = cena.bolaPos(cena.gigante, cena.esfera(cena.gigante));
            if (at != null) {
                SkillEffects.play(EsferaSpec.SKILL, awakened, "implodir", world(at), forward, -1, 0);
            }
        }
        if (age >= cena.fim) {
            // The screen went white at the end; it fades back into the world.
            ScreenFx.flash(0xFFFFFF, 1.0f, 10);
            stop(minecraft);
            return;
        }
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void stop(Minecraft minecraft) {
        casterId = -1;
        targetId = -1;
        frame = null;
        CutsceneCamera.end("esfera");
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (casterId < 0) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    // ---------- the camera ----------

    public static FaceCutscene.Shot shot(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (casterId < 0 || minecraft.level == null || minecraft.player == null) {
            return null;
        }
        EsferaCena.Shot s = frame(partialTick).shot();
        Vec3 pos = world(s.pos());
        Vec3 dir = world(s.olhar()).subtract(pos);
        float camYaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        return new FaceCutscene.Shot(pos, camYaw, pitch);
    }

    // Each shot's own lens (vertical field of view, as on the preview: no zoom inside a shot).
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (casterId < 0 || !event.usedConfiguredFov()) {
            return;
        }
        event.setFOV(frame((float) event.getPartialTick()).shot().fov());
    }

    // The dutch tilt (positive rolls the picture clockwise, like the preview's camera).
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (casterId < 0) {
            return;
        }
        double roll = frame((float) event.getPartialTick()).shot().rolar();
        if (roll != 0) {
            event.setRoll(event.getRoll() - (float) roll);
        }
    }

    // Cinema bars, the white fades (into the streaks and out in space, the sun bursting, the end) and his line.
    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        if (casterId < 0) {
            return;
        }
        double t = age + delta.getGameTimeDeltaPartialTick(false);
        float white = (float) cena.flash(t);
        if (white > 0.004f) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(white * 255) << 24 | 0xFFFFFF);
        }
        int bar = spec.cutscene().barrasPretas() ? (int) Math.round(graphics.guiHeight() * BAR_HEIGHT) : 0;
        if (bar > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), bar, 0xFF000000);
            graphics.fill(0, graphics.guiHeight() - bar, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        }
        EsferaSpec.Legenda legenda = spec.legenda();
        if (legenda == null || legenda.texto() == null || legenda.texto().isEmpty() || t < legenda.inicioTick() || t > legenda.fimTick()) {
            return;
        }
        float alpha = (float) Mth.clamp(Math.min(t - legenda.inicioTick(), legenda.fimTick() - t) / 3, 0, 1);
        if (alpha <= 0.02f) {
            return;
        }
        int color = parse(legenda.cor(), 0xFFFFFF) | Math.max(4, (int) (alpha * 255)) << 24;
        float scale = (float) Math.max(0.5, legenda.tamanho());
        Component text = Component.literal(legenda.texto());
        var font = Minecraft.getInstance().font;
        float y = graphics.guiHeight() - Math.max(bar, 8) - 10 - font.lineHeight * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2f, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, true);
        graphics.pose().popPose();
    }

    private static int parse(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
