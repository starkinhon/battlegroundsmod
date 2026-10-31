package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.client.fx.ScreenFx;
import com.starkinhon.battlegroundsmod.skill.DestructionPlanetSpec;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

// Bills' trip to outer space for the Destruction Touch on a planet (Shift + V), seen by Bills only. A close shot of his
// hand snapping, a white flash and he is in space (client/fx/SpaceScene + PlanetScene draw it over the world): the
// square Earth of the C in front of him, one half of it glowing pink, a white point and a starburst on it, a quiet
// moment, then the white shockwave and that half blowing up into a lilac cloud, seen wide and at last from behind his
// back. A last flash and the player is back in their own view. Timing from bills.destruction_touch_planeta.json; the
// space is laid out from where he stood and faced when it started.
public final class PlanetCutscene {
    private static final double BAR_HEIGHT = 0.11;

    private static int casterId = -1;
    private static DestructionPlanetSpec spec;
    private static int age;
    private static Vec3 origin = Vec3.ZERO;
    private static Vec3 forward = new Vec3(0, 0, 1);
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean previousHideGui;
    private static boolean flashedIn;

    private PlanetCutscene() {
    }

    public static void start(int caster, boolean awakened) {
        Minecraft minecraft = Minecraft.getInstance();
        DestructionPlanetSpec next = DestructionPlanetSpec.get(awakened);
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
        return casterId >= 0 && age >= spec.spaceTick() && age < spec.returnTick();
    }

    public static int casterId() {
        return casterId;
    }

    public static DestructionPlanetSpec spec() {
        return spec;
    }

    // Ticks since the press, with the frame's fraction.
    public static double time(float partialTick) {
        return age + partialTick;
    }

    public static Vec3 forward() {
        return forward;
    }

    public static Vec3 right() {
        return new Vec3(-forward.z, 0, forward.x);
    }

    public static Vec3 planetCenter() {
        var e = spec.espaco();
        return origin.add(forward.scale(e.distancia())).add(0, -e.abaixo(), 0);
    }

    // Out of the planet's middle towards the half that blows up: the side looking back at Bills, to his right, where
    // the wide shots look from.
    public static Vec3 blastDir() {
        return forward.scale(-0.6).add(right().scale(0.8)).add(0, 0.25, 0).normalize();
    }

    // Where the white point lights up: on the planet's skin, in the middle of that half.
    public static Vec3 blastPoint() {
        return planetCenter().add(blastDir().scale(spec.espaco().tamanho() * 0.55));
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
        if (age == spec.flashTick() + Math.max(1, spec.tempo().claraoTicks()) / 3) {
            ScreenFx.flash(0xE0ECFF, 0.55f, 6);
        }
        if (age == spec.blastTick()) {
            ScreenFx.flash(0xFFFFFF, 0.8f, 8);
        }
        if (age >= spec.returnTick()) {
            ScreenFx.flash(0xF2E0FF, 0.9f, 8);
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
        Vec3 right = right();
        Vec3 planet = planetCenter();
        double distance = spec.espaco().distancia();
        Vec3 pos;
        Vec3 look;
        if (t < spec.spaceTick()) {
            // His hand by his face, snapping, close and from the front.
            double k = smooth(t / spec.spaceTick());
            pos = eyes.add(forward.scale(Mth.lerp(k, 1.6, 1.25))).add(right.scale(0.9)).add(0, -0.15, 0);
            look = eyes.add(right.scale(0.35)).add(0, -0.1, 0);
        } else if (t < spec.flashTick()) {
            // Wide, off to his right: the planet with its glowing half, drifting slowly.
            double k = (t - spec.spaceTick()) / Math.max(1, spec.flashTick() - spec.spaceTick());
            pos = feet.add(forward.scale(distance * (0.3 + 0.05 * k))).add(right.scale(distance * 0.42)).add(0, 18 - 4 * k, 0);
            look = planet.lerp(blastPoint(), 0.25);
        } else if (t < spec.blastTick()) {
            // Closer, on the white point and the starburst, then holding still.
            double k = smooth((t - spec.flashTick()) / Math.max(1, spec.pauseTick() - spec.flashTick()));
            Vec3 near = blastPoint().add(blastDir().scale(spec.espaco().tamanho() * 1.1)).add(forward.scale(-20));
            pos = feet.add(forward.scale(distance * 0.35)).add(right.scale(distance * 0.42)).add(0, 14, 0).lerp(near, 0.35 + 0.25 * k);
            look = planet.lerp(blastPoint(), 0.45);
        } else if (t < spec.backTick()) {
            // Wide again for the blast, pulling back as the cloud grows.
            double k = (t - spec.blastTick()) / Math.max(1, spec.backTick() - spec.blastTick());
            pos = feet.add(forward.scale(distance * (0.25 - 0.1 * k))).add(right.scale(distance * (0.55 + 0.1 * k))).add(0, 24 + 10 * k, 0);
            look = planet.lerp(blastPoint(), 0.5);
        } else {
            // From behind his back, the blast in front of him.
            double k = smooth((t - spec.backTick()) / Math.max(1, spec.returnTick() - spec.backTick()));
            pos = eyes.subtract(forward.scale(Mth.lerp(k, 2.2, 3))).add(right.scale(0.9)).add(0, 0.35, 0);
            look = eyes.add(planet.subtract(eyes).normalize().scale(20));
        }
        Vec3 dir = look.subtract(pos);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        return new FaceCutscene.Shot(pos, yaw, pitch);
    }

    // Cinema bars.
    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        if (casterId < 0 || !spec.cutscene().barrasPretas()) {
            return;
        }
        int bar = (int) Math.round(graphics.guiHeight() * BAR_HEIGHT);
        graphics.fill(0, 0, graphics.guiWidth(), bar, 0xFF000000);
        graphics.fill(0, graphics.guiHeight() - bar, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
