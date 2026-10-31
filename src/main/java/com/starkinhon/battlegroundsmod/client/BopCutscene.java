package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.skill.DestructionBopSpec;

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

// Bills' Destruction Bop, seen by the two fighters only: a low side shot of the freeze and the flick, pulling back and
// tilting up as the target flies, up beside them in the air while Bills comes back behind them, a close shot of his
// hand slowly reaching their shoulder, then a cut to a wide side shot of the diagonal fall and a backed-off view of
// the hole. Measured from where the target stood, with z from Bills towards it.
public final class BopCutscene {
    private static final double BAR_HEIGHT = 0.11;

    private static int casterId = -1;
    private static int targetId = -1;
    private static DestructionBopSpec spec;
    private static int end;
    private static int age;
    private static Vec3 origin = Vec3.ZERO;
    private static Vec3 forward = new Vec3(0, 0, 1);
    private static double top;
    private static Vec3 landed;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean previousHideGui;

    private BopCutscene() {
    }

    public static void start(int caster, int target, boolean awakened, int endTick) {
        Minecraft minecraft = Minecraft.getInstance();
        DestructionBopSpec next = DestructionBopSpec.get(awakened);
        Entity casterEntity = minecraft.level == null ? null : minecraft.level.getEntity(caster);
        Entity targetEntity = minecraft.level == null ? null : minecraft.level.getEntity(target);
        if (!next.cutscene().ativa() || casterEntity == null || targetEntity == null) {
            return;
        }
        if (casterId < 0) {
            previousCameraType = minecraft.options.getCameraType();
            previousHideGui = minecraft.options.hideGui;
        }
        casterId = caster;
        targetId = target;
        spec = next;
        end = endTick;
        age = 0;
        origin = targetEntity.position();
        top = origin.y;
        landed = null;
        Vec3 to = origin.subtract(casterEntity.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        forward = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, casterEntity.getYRot()) : flat.normalize();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        minecraft.options.hideGui = true;
    }

    public static boolean active() {
        return casterId >= 0;
    }

    public static void tick() {
        if (casterId < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ++age >= end + Math.max(1, spec.cutscene().voltarTicks())
                || minecraft.level.getEntity(casterId) == null || minecraft.level.getEntity(targetId) == null) {
            stop(minecraft);
            return;
        }
        Entity target = minecraft.level.getEntity(targetId);
        top = Math.max(top, target.getY());
        if (landed == null && age >= spec.groundTick()) {
            landed = target.position();
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

    // x to the left (seen from Bills), y up from the target's feet, z from Bills towards the target.
    private static Vec3 world(double x, double y, double z) {
        Vec3 left = new Vec3(forward.z, 0, -forward.x);
        return origin.add(left.scale(x)).add(0, y, 0).add(forward.scale(z));
    }

    public static FaceCutscene.Shot shot(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer viewer = minecraft.player;
        Entity target = minecraft.level == null ? null : minecraft.level.getEntity(targetId);
        if (casterId < 0 || target == null || viewer == null) {
            return null;
        }
        double t = age + partialTick;
        Vec3 live = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0);
        double up = top - origin.y;
        int flick = spec.flickTick();
        int vanish = spec.vanishTick();
        int appear = spec.appearTick();
        int close = spec.closeTick();
        int slap = spec.slapTick();
        int ground = spec.groundTick();

        // The freeze and the flick, low from the side.
        Vec3 freezePos = world(3.2, 0.7, -1.4);
        Vec3 freezeLook = world(0, 1.3, -0.5);
        // Pulled back, looking up after them.
        Vec3 risePos = world(7.5, 1.2, -3.5);
        // Up in the air beside them, Bills behind them in the shot.
        Vec3 airPos = world(5, up + 1.2, -2.2);

        Vec3 pos;
        Vec3 look;
        if (t < flick) {
            pos = freezePos;
            look = freezeLook;
        } else if (t < vanish) {
            double k = smooth((t - flick) / 4);
            pos = freezePos.lerp(risePos, k);
            look = freezeLook.lerp(live, k);
        } else if (t < close) {
            double k = smooth((t - vanish) / Math.max(1, appear - vanish + 1));
            pos = risePos.lerp(airPos, k);
            look = live;
        } else if (t < slap) {
            // Close on the hand and the shoulder, from the side, slowly pushing in.
            double k = (t - close) / Math.max(1, slap - close);
            double z = spec.tapa().atras() * 0.5;
            Vec3 shoulder = world(0, up + 1.45, z);
            pos = world(2.1 - 0.7 * k, up + 1.6 - 0.1 * k, z - 0.5);
            look = shoulder;
        } else {
            // Cut wide, from the side of the diagonal, following them down; then backed off over the hole.
            Vec3 throwEnd = world(0, 0, -spec.tapa().frente());
            Vec3 middle = world(0, up * 0.5, -spec.tapa().frente() * 0.5);
            Vec3 fallPos = middle.add(new Vec3(forward.z, 0, -forward.x).scale(11 + up * 0.4)).add(0, 1.5, 0);
            Vec3 hole = landed != null ? landed : throwEnd;
            Vec3 left = new Vec3(forward.z, 0, -forward.x);
            Vec3 holePos = hole.add(left.scale(7)).add(forward.scale(4)).add(0, 4.5, 0);
            double k = smooth((t - ground) / 10);
            pos = fallPos.lerp(holePos, k);
            look = live.lerp(hole.add(0, 0.3, 0), smooth((t - ground) / 6));
        }
        Vec3 dir = look.subtract(pos);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        double blend = blend(t);
        if (blend < 1) {
            Vec3 own = viewer.getEyePosition(partialTick);
            float ownYaw = viewer.getViewYRot(partialTick);
            float ownPitch = viewer.getViewXRot(partialTick);
            pos = own.lerp(pos, blend);
            yaw = ownYaw + (float) (Mth.wrapDegrees(yaw - ownYaw) * blend);
            pitch = (float) Mth.lerp(blend, ownPitch, pitch);
        }
        return new FaceCutscene.Shot(pos, yaw, pitch);
    }

    // Cuts straight in, eases back out over voltarTicks.
    private static double blend(double t) {
        return 1 - smooth((t - end) / Math.max(1, spec.cutscene().voltarTicks()));
    }

    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        if (casterId < 0 || !spec.cutscene().barrasPretas()) {
            return;
        }
        double t = age + delta.getGameTimeDeltaPartialTick(false);
        int height = (int) Math.round(graphics.guiHeight() * BAR_HEIGHT * blend(t));
        if (height > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), height, 0xFF000000);
            graphics.fill(0, graphics.guiHeight() - height, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        }
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
