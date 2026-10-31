package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;
import com.starkinhon.battlegroundsmod.client.fx.SkillEffects;
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

// Bills' Esfera da Destruição Planetária (Shift + C), seen by Bills and his target. In front of him as he puts his
// hands behind his back and vanishes in black streaks; then outer space (client/fx/SpaceScene draws it over the world):
// seen from the front as he appears there, then from over his shoulder looking down at the target lying adrift below
// him, slowly falling towards the Earth; close from the front while he charges the sphere between his hands; from the
// side as he throws it; over his shoulder again as it implodes into a giant sun and flies down to the target, and the
// explosion of flames. A flash and the players are back in their own view. Timing from bills.esfera.json; space is
// laid out from where he stood and faced when it started (the server lays out the landing the same way).
public final class EsferaCutscene {
    private static final double BAR_HEIGHT = 0.11;

    private static int casterId = -1;
    private static int targetId = -1;
    private static boolean awakened;
    private static EsferaSpec spec;
    private static int age;
    private static Vec3 origin = Vec3.ZERO;
    private static Vec3 forward = new Vec3(0, 0, 1);
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean previousHideGui;

    private EsferaCutscene() {
    }

    public static void start(int caster, int target, boolean isAwakened) {
        Minecraft minecraft = Minecraft.getInstance();
        EsferaSpec next = EsferaSpec.get(isAwakened);
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(caster);
        if (!next.cutscene().ativa() || entity == null) {
            return;
        }
        if (casterId < 0) {
            previousCameraType = minecraft.options.getCameraType();
            previousHideGui = minecraft.options.hideGui;
        }
        casterId = caster;
        targetId = target;
        awakened = isAwakened;
        spec = next;
        age = 0;
        origin = entity.position();
        forward = Vec3.directionFromRotation(0, entity.getYRot());
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        minecraft.options.hideGui = true;
    }

    public static boolean active() {
        return casterId >= 0;
    }

    // The space scene is up.
    public static boolean inSpace() {
        return casterId >= 0 && age >= spec.spaceTick() && age < spec.endTick();
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

    public static Vec3 origin() {
        return origin;
    }

    public static Vec3 forward() {
        return forward;
    }

    public static Vec3 right() {
        return new Vec3(-forward.z, 0, forward.x);
    }

    // Ticks since the cutscene started, with the frame's fraction.
    public static double time(float partialTick) {
        return age + partialTick;
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
        Entity caster = minecraft.level.getEntity(casterId);
        // The moments only the two screens in space see.
        if (age == spec.spaceTick()) {
            ScreenFx.flash(0xFFFFFF, 0.8f, 6);
            SkillEffects.play(EsferaSpec.SKILL, awakened, "chegada", caster.position(), forward, -1, 0);
        } else if (age == spec.flyTick() + implodeTicks()) {
            double[] r = {1};
            SkillEffects.play(EsferaSpec.SKILL, awakened, "implodir", spherePos(caster, 0, r), forward, -1, 0);
        } else if (age == spec.blastTick()) {
            SkillEffects.play(EsferaSpec.SKILL, awakened, "explosaoEspaco", targetPos(0), forward, -1, spec.endTick() - spec.blastTick());
        }
        if (age >= spec.endTick()) {
            ScreenFx.flash(0xFFFFFF, 0.9f, 8);
            stop(minecraft);
            return;
        }
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void stop(Minecraft minecraft) {
        casterId = -1;
        targetId = -1;
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

    // ---------- where things are in space ----------

    public static Vec3 planetCenter() {
        EsferaSpec.Espaco e = spec.espaco();
        return origin.add(forward.scale(e.distancia())).subtract(right().scale(e.lado())).add(0, -e.abaixo(), 0);
    }

    public static Vec3 planetTop() {
        return planetCenter().add(0, spec.espaco().tamanho() / 2, 0);
    }

    // The target adrift: below and ahead of him, sinking slowly towards the Earth.
    public static Vec3 targetPos(float partialTick) {
        EsferaSpec.Deriva d = spec.deriva();
        Vec3 start = origin.add(forward.scale(d.frente())).add(0, -d.abaixo(), 0);
        Vec3 down = planetTop().subtract(start);
        down = down.lengthSqr() < 1.0E-6 ? new Vec3(0, -1, 0) : down.normalize();
        double s = Math.max(0, spaceAge(partialTick));
        // A slow sway on top of the sinking, like floating.
        Vec3 sway = right().scale(Math.sin(s * 0.03) * 0.4).add(0, Math.sin(s * 0.045) * 0.25, 0);
        return start.add(down.scale(s * d.velocidade())).add(sway);
    }

    // How the target is turned lying there (degrees around the vertical).
    public static float targetSpin(float partialTick) {
        return (float) (Math.max(0, spaceAge(partialTick)) * spec.deriva().giro()) + 25;
    }

    public static Vec3 chest(Entity caster, float partialTick) {
        return caster.getPosition(partialTick).add(0, caster.getBbHeight() * 0.62, 0);
    }

    public static int implodeTicks() {
        return Math.min(8, Math.max(2, spec.tempo().vooTicks() / 5));
    }

    // Where the sphere is and how big (radius[0]); radius 0 when there is none.
    public static Vec3 spherePos(Entity caster, float partialTick, double[] radius) {
        double t = time(partialTick);
        EsferaSpec.Esfera e = spec.esfera();
        Vec3 hands = chest(caster, partialTick).add(forward.scale(0.75));
        double charge = Math.max(0.05, e.raioCarga());
        if (t < spec.chargeTick()) {
            radius[0] = 0;
            return hands;
        }
        if (t < spec.throwTick()) {
            double k = (t - spec.chargeTick()) / Math.max(1, spec.throwTick() - spec.chargeTick());
            radius[0] = Mth.lerp(smooth(Math.min(1, k * 1.4)), 0.04, charge);
            return hands;
        }
        Vec3 aim = targetPos(partialTick).add(0, 0.4, 0);
        Vec3 out = hands.add(aim.subtract(hands).normalize().scale(4));
        if (t < spec.flyTick()) {
            // Thrown: out of his hands, still small.
            double k = (t - spec.throwTick()) / Math.max(1, spec.flyTick() - spec.throwTick());
            radius[0] = charge;
            return hands.lerp(out, 1 - (1 - k) * (1 - k));
        }
        if (t < spec.blastTick()) {
            double s = t - spec.flyTick();
            int implode = implodeTicks();
            double big = Math.max(charge, e.raioGigante());
            if (s < implode) {
                // In to a point, then all at once a giant sun.
                radius[0] = charge * (1 - 0.7 * smooth(s / implode));
                return out;
            }
            double grow = (s - implode) / 3;
            radius[0] = grow < 1 ? Mth.lerp(grow, charge * 0.3, big * 1.12) : big * (1 + 0.12 * Math.max(0, 1 - (grow - 1) / 3));
            double k = (s - implode) / Math.max(1, spec.blastTick() - spec.flyTick() - implode);
            return out.lerp(aim, k * k * (1.6 - 0.6 * k));
        }
        radius[0] = 0;
        return aim;
    }

    // ---------- the camera ----------

    public static FaceCutscene.Shot shot(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer viewer = minecraft.player;
        Entity caster = casterId < 0 || minecraft.level == null ? null : minecraft.level.getEntity(casterId);
        if (caster == null || viewer == null) {
            return null;
        }
        double t = time(partialTick);
        Vec3 eyes = caster.getEyePosition(partialTick);
        Vec3 chest = chest(caster, partialTick);
        Vec3 right = right();
        Vec3 target = targetPos(partialTick);
        Vec3 pos;
        Vec3 look;
        if (t < spec.spaceTick()) {
            // From the front, a little low, as he puts his hands behind his back.
            double k = smooth(t / spec.spaceTick());
            pos = eyes.add(forward.scale(Mth.lerp(k, 3.4, 2.8))).add(right.scale(0.35)).add(0, -0.35, 0);
            look = eyes.add(0, -0.3, 0);
        } else if (t < spec.aimTick()) {
            // In space, from the front: he has just appeared.
            double k = smooth((t - spec.spaceTick()) / (spec.aimTick() - spec.spaceTick()));
            pos = eyes.add(forward.scale(Mth.lerp(k, 4.2, 3.0))).add(right.scale(-0.4)).add(0, Mth.lerp(k, -0.6, -0.2), 0);
            look = eyes.add(0, -0.35, 0);
        } else if (t < spec.chargeTick()) {
            // Over his shoulder, high, looking down past him at the target adrift and the Earth far below.
            double k = smooth((t - spec.aimTick()) / (spec.chargeTick() - spec.aimTick()));
            pos = eyes.subtract(forward.scale(Mth.lerp(k, 2.6, 1.9))).add(right.scale(0.95)).add(0, Mth.lerp(k, 1.9, 1.5), 0);
            look = target.add(0, 0.3, 0);
        } else if (t < spec.throwTick()) {
            // Close from the front, a little low: the sphere forming between his hands.
            double k = smooth((t - spec.chargeTick()) / (spec.throwTick() - spec.chargeTick()));
            pos = chest.add(forward.scale(Mth.lerp(k, 3.0, 2.3))).add(right.scale(0.55)).add(0, -0.45, 0);
            look = chest.add(forward.scale(0.5)).add(0, 0.15, 0);
        } else if (t < spec.flyTick()) {
            // From the side as he throws it.
            pos = chest.add(right.scale(4.6)).add(forward.scale(1.2)).add(0, 0.5, 0);
            look = chest.add(forward.scale(1.8));
        } else if (t < spec.blastTick()) {
            // Over his shoulder again, the sun flying down to the target.
            double[] r = {1};
            Vec3 ball = spherePos(caster, partialTick, r);
            double back = 3 + Math.max(0, r[0]) * 0.25;
            pos = eyes.subtract(forward.scale(back)).add(right.scale(1.4)).add(0, 2.2, 0);
            look = ball.lerp(target, 0.45);
        } else {
            // Pulling back from the explosion.
            double k = smooth((t - spec.blastTick()) / Math.max(1, spec.endTick() - spec.blastTick()));
            pos = eyes.subtract(forward.scale(Mth.lerp(k, 4, 8))).add(right.scale(2)).add(0, Mth.lerp(k, 3, 5), 0);
            look = target;
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
