package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;
import com.starkinhon.battlegroundsmod.skill.EsferaSpec;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

// Bills' trip to outer space (Esfera da Destruição), seen by Bills only. First the camera looks up at him; a white flash
// and he is in space (client/fx/SpaceScene draws it over the world): a wide shot over the square Earth while the sphere
// grows over his hand, a close shot of his face while he says his line, then from behind as he throws it at the planet.
// A last flash and the player is back in their own view, with the sphere falling from the sky. Timing from
// bills.esfera.json; the space is laid out from where he stood and faced when it started.
public final class EsferaCutscene {
    private static final double BAR_HEIGHT = 0.11;

    private static int casterId = -1;
    private static EsferaSpec spec;
    private static int age;
    private static Vec3 origin = Vec3.ZERO;
    private static Vec3 forward = new Vec3(0, 0, 1);
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean previousHideGui;
    private static boolean flashedIn;

    private EsferaCutscene() {
    }

    public static void start(int caster, boolean awakened) {
        Minecraft minecraft = Minecraft.getInstance();
        EsferaSpec next = EsferaSpec.get(awakened);
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(caster);
        if (!next.cutscene().ativa() || entity == null) {
            return;
        }
        if (casterId < 0) {
            previousCameraType = minecraft.options.getCameraType();
            previousHideGui = minecraft.options.hideGui;
        }
        casterId = caster;
        spec = next;
        age = 0;
        flashedIn = false;
        origin = entity.position();
        forward = Vec3.directionFromRotation(0, entity.getYRot());
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        minecraft.options.hideGui = true;
    }

    public static boolean active() {
        return casterId >= 0;
    }

    // The space scene is up (between the two flashes).
    public static boolean inSpace() {
        return casterId >= 0 && age >= spec.spaceTick() && age < spec.fallTick();
    }

    public static int casterId() {
        return casterId;
    }

    public static EsferaSpec spec() {
        return spec;
    }

    public static Vec3 origin() {
        return origin;
    }

    public static Vec3 forward() {
        return forward;
    }

    // Ticks since he got to space, with the frame's fraction.
    public static double spaceAge(float partialTick) {
        return age + partialTick - spec.spaceTick();
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
        if (!flashedIn && age >= spec.spaceTick() - 2) {
            flashedIn = true;
            ScreenFx.flash(0xFFFFFF, 1, 7);
        }
        if (age >= spec.fallTick()) {
            ScreenFx.flash(0xFFF2D0, 0.9f, 8);
            stop(minecraft);
            return;
        }
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void stop(Minecraft minecraft) {
        casterId = -1;
        minecraft.options.setCameraType(previousCameraType);
        minecraft.options.hideGui = previousHideGui;
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

    // Where the sphere is in space: growing over his raised hand while he charges, then flying down to the planet.
    // Also tells its radius (out[0]).
    public static Vec3 spherePos(Entity caster, float partialTick, double[] radius) {
        double s = spaceAge(partialTick);
        int charge = Math.max(1, spec.tempo().cargaTicks());
        int throwTicks = Math.max(1, spec.tempo().lancarTicks());
        double full = spec.esfera().raioEspaco();
        double k = smooth(s / charge);
        double r = Mth.lerp(k, 0.25, full);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 overHand = caster.getEyePosition(partialTick).add(right.scale(0.35)).add(forward.scale(0.3)).add(0, 1.1 + full, 0);
        Vec3 held = overHand.subtract(0, full - r, 0);
        radius[0] = r;
        if (s < charge) {
            return held;
        }
        double f = Math.min(1, (s - charge) / throwTicks);
        return overHand.lerp(planetTop(), Math.pow(f, 1.6));
    }

    public static Vec3 planetCenter() {
        EsferaSpec.Espaco e = spec.espaco();
        return origin.add(forward.scale(e.distancia())).add(0, -e.abaixo(), 0);
    }

    public static Vec3 planetTop() {
        return planetCenter().add(0, spec.espaco().tamanho() / 2, 0);
    }

    public static FaceCutscene.Shot shot(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer viewer = minecraft.player;
        Entity caster = casterId < 0 || minecraft.level == null ? null : minecraft.level.getEntity(casterId);
        if (caster == null || viewer == null) {
            return null;
        }
        double t = age + partialTick;
        Vec3 eyes = caster.getEyePosition(partialTick);
        Vec3 feet = caster.getPosition(partialTick);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        int charge = Math.max(1, spec.tempo().cargaTicks());
        Vec3 pos;
        Vec3 look;
        if (t < spec.spaceTick()) {
            // From low in front, looking up at him as he looks up.
            double k = smooth(t / spec.spaceTick());
            pos = eyes.add(forward.scale(Mth.lerp(k, 2.6, 1.7))).add(right.scale(0.5)).add(0, -0.9, 0);
            look = eyes.add(0, 0.4 + 0.6 * k, 0);
        } else {
            double s = t - spec.spaceTick();
            double[] r = {1};
            Vec3 ball = spherePos(caster, partialTick, r);
            if (s < charge * 0.5) {
                // Wide, slowly going around him: Bills, the growing sphere and the planet below.
                double a = -0.7 + 0.6 * s / (charge * 0.5);
                Vec3 around = forward.yRot((float) a);
                pos = feet.add(around.scale(11)).add(0, 4, 0);
                look = feet.add(0, 2.2, 0).lerp(ball, 0.25);
            } else if (s < charge) {
                // His face, close, while he says it.
                double k = smooth((s - charge * 0.5) / (charge * 0.5));
                pos = eyes.add(forward.scale(Mth.lerp(k, 1.9, 1.35))).add(right.scale(0.45)).add(0, -0.2, 0);
                look = eyes.add(0, 0.05, 0);
            } else {
                // From behind and above, following the sphere down to the planet.
                pos = feet.subtract(forward.scale(7)).add(right.scale(2.5)).add(0, 6.5, 0);
                look = ball;
            }
        }
        Vec3 dir = look.subtract(pos);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        return new FaceCutscene.Shot(pos, yaw, pitch);
    }

    // Cinema bars and his line as a subtitle.
    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        if (casterId < 0) {
            return;
        }
        double t = age + delta.getGameTimeDeltaPartialTick(false);
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

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
