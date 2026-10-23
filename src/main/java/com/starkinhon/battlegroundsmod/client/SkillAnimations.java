package com.starkinhon.battlegroundsmod.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.skill.AgarraoSpec;
import com.starkinhon.battlegroundsmod.skill.AnimationSpec;
import com.starkinhon.battlegroundsmod.skill.DefesaSpec;
import com.starkinhon.battlegroundsmod.skill.PoseTimeline;
import com.starkinhon.battlegroundsmod.skill.SkillSpec;
import com.starkinhon.battlegroundsmod.skill.SkillSpecs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// Body poses of skills on the client. The server says who started which skill; the pose over time
// comes from the skill's spec, so it matches the Forja de Skills page.
public final class SkillAnimations {
    // from: the pose the body was in when this started, blended out over BLEND_TICKS so back-to-back moves (a punch
    // combo) flow into each other. holdAt/holdTicks: a hitstop, the animation stands still for holdTicks from holdAt.
    private record Playing(PoseTimeline spec, int startTick, SkillSpec.Pose from, double holdAt, int holdTicks) {
        double time(double raw) {
            return holdTicks <= 0 || raw <= holdAt ? raw : Math.max(holdAt, raw - holdTicks);
        }

        double end() {
            return spec.animationEnd() + Math.max(0, holdTicks);
        }
    }

    private static final double BLEND_TICKS = 1.5;
    // Entities holding their guard up (Defense); they stand in the guard pose when no move is playing.
    private static final Set<Integer> GUARDING = new HashSet<>();

    private static final Map<Integer, Playing> PLAYING = new HashMap<>();
    private static final Set<HumanoidModel<?>> SHIFTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final float DEG = (float) (Math.PI / 180);

    private SkillAnimations() {
    }

    // "skill#part" plays one part of a move with several steps, timed by the server (see AgarraoSpec.phase); the
    // number after a second # is how long the part runs, when only the server knows (a dash until it reaches someone).
    // An empty skill stops whatever is playing (the move was cut off).
    public static void play(int entityId, String skill, boolean awakened) {
        if (skill.isEmpty()) {
            PLAYING.remove(entityId);
            return;
        }
        String[] parts = skill.split("#");
        if (parts.length > 1 && AgarraoSpec.SKILL.equals(parts[0])) {
            int ticks = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            play(entityId, AgarraoSpec.get(awakened).phase(parts[1], ticks));
            return;
        }
        play(entityId, SkillSpecs.get(skill, AnimationSpec.class, awakened));
    }

    public static void play(int entityId, PoseTimeline spec) {
        Entity entity = entity(entityId);
        if (entity != null) {
            PLAYING.put(entityId, new Playing(spec, entity.tickCount, current(entity), 0, 0));
        }
    }

    public static void guard(int entityId, boolean on) {
        if (on) {
            GUARDING.add(entityId);
        } else {
            GUARDING.remove(entityId);
        }
    }

    // Freezes whatever this entity is playing for a few ticks, starting now (hitstop).
    public static void hold(int entityId, int ticks) {
        Entity entity = entity(entityId);
        Playing playing = PLAYING.get(entityId);
        if (entity != null && playing != null && ticks > 0) {
            double now = playing.time(entity.tickCount - playing.startTick());
            PLAYING.put(entityId, new Playing(playing.spec(), playing.startTick(), playing.from(), now, ticks));
        }
    }

    private static Entity entity(int entityId) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(entityId);
    }

    private static SkillSpec.Pose current(Entity entity) {
        Playing playing = PLAYING.get(entity.getId());
        if (playing == null) {
            return SkillSpec.Pose.ZERO;
        }
        double t = playing.time(entity.tickCount - playing.startTick());
        return t > playing.spec().animationEnd() ? SkillSpec.Pose.ZERO : pose(playing, t, entity.tickCount - playing.startTick());
    }

    private static SkillSpec.Pose pose(Playing playing, double t, double raw) {
        SkillSpec.Pose pose = playing.spec().poseAt(t);
        return raw < BLEND_TICKS ? playing.from().lerp(pose, raw / BLEND_TICKS) : pose;
    }

    // Called after the vanilla pose is set up. Angles are added on top, so the head still follows the aim.
    public static void apply(LivingEntity entity, HumanoidModel<?> model, float ageInTicks) {
        Playing playing = PLAYING.get(entity.getId());
        double raw = playing == null ? -1 : ageInTicks - playing.startTick();
        if (playing != null && raw > playing.end()) {
            PLAYING.remove(entity.getId());
        }
        // First person hands are posed with ageInTicks 0, which lands here as t < 0 and leaves the animation running.
        boolean animating = raw >= 0 && raw <= playing.end();
        boolean guard = ageInTicks != 0 && GUARDING.contains(entity.getId());
        if (!animating && !guard) {
            unshift(model);
            return;
        }
        SkillSpec.Pose pose = animating ? pose(playing, playing.time(raw), raw) : DefesaSpec.get().pose();
        float lean = (float) pose.tronco() * DEG;
        // Lean around the hips like the page does: the neck and shoulders swing with the body.
        float cos = (float) Math.cos(lean);
        float sin = (float) Math.sin(lean);
        model.body.xRot += lean;
        model.body.y += 12 - 12 * cos;
        model.body.z = -12 * sin;
        model.head.xRot += (float) pose.cabeca() * DEG;
        model.head.y += 12 - 12 * cos;
        model.head.z = -12 * sin;
        arm(model.rightArm, pose.bracoDir(), lean, cos, sin, 1);
        arm(model.leftArm, pose.bracoEsq(), lean, cos, sin, -1);
        twist(model, (float) pose.giro() * DEG, sin);
        leg(model.rightLeg, pose.pernaDir(), 1);
        leg(model.leftLeg, pose.pernaEsq(), -1);
        model.hat.copyFrom(model.head);
        SHIFTED.add(model);
    }

    // Turns the chest and carries the shoulders around with it, the way vanilla's attack swing does.
    private static void twist(HumanoidModel<?> model, float giro, float leanSin) {
        if (giro == 0) {
            return;
        }
        float yaw = model.body.yRot - giro;
        model.body.yRot = yaw;
        float s = (float) Math.sin(yaw);
        float c = (float) Math.cos(yaw);
        model.rightArm.x = -c * 5;
        model.rightArm.z = s * 5 - 10 * leanSin;
        model.leftArm.x = c * 5;
        model.leftArm.z = -s * 5 - 10 * leanSin;
        model.rightArm.yRot -= giro;
        model.leftArm.yRot -= giro;
    }

    private static void leg(ModelPart leg, SkillSpec.Braco perna, int outward) {
        if (perna != null) {
            leg.xRot -= (float) perna.frente() * DEG;
            leg.zRot += outward * (float) perna.abrir() * DEG;
        }
    }

    // frente raises the arm forward; abrir opens it to the side (negative closes it towards the chest).
    private static void arm(ModelPart arm, SkillSpec.Braco braco, float lean, float cos, float sin, int outward) {
        arm.xRot += lean - (float) braco.frente() * DEG;
        arm.zRot += outward * (float) braco.abrir() * DEG;
        arm.y += 10 - 10 * cos;
        arm.z = -10 * sin;
    }

    // Vanilla never resets these z offsets, and every player shares one model, so undo them for the next player drawn.
    private static void unshift(HumanoidModel<?> model) {
        if (!SHIFTED.remove(model)) {
            return;
        }
        model.body.z = 0;
        model.head.z = 0;
        model.hat.copyFrom(model.head);
        // The attack swing sets the arm z itself.
        if (model.attackTime <= 0) {
            model.rightArm.z = 0;
            model.leftArm.z = 0;
        }
    }
}
