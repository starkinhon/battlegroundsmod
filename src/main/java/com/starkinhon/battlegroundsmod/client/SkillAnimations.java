package com.starkinhon.battlegroundsmod.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

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
    private record Playing(SkillSpec spec, int startTick) {
    }

    private static final Map<Integer, Playing> PLAYING = new HashMap<>();
    private static final Set<HumanoidModel<?>> SHIFTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final float DEG = (float) (Math.PI / 180);

    private SkillAnimations() {
    }

    public static void play(int entityId, String skill, boolean awakened) {
        var level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(entityId);
        if (entity != null) {
            PLAYING.put(entityId, new Playing(SkillSpecs.get(skill, SkillSpec.class, awakened), entity.tickCount));
        }
    }

    // Called after the vanilla pose is set up. Angles are added on top, so the head still follows the aim.
    public static void apply(LivingEntity entity, HumanoidModel<?> model, float ageInTicks) {
        Playing playing = PLAYING.get(entity.getId());
        double t = playing == null ? -1 : ageInTicks - playing.startTick();
        if (playing != null && t > playing.spec().animationEnd()) {
            PLAYING.remove(entity.getId());
        }
        // First person hands are posed with ageInTicks 0, which lands here as t < 0 and leaves the animation running.
        if (t < 0 || t > playing.spec().animationEnd()) {
            unshift(model);
            return;
        }
        SkillSpec.Pose pose = playing.spec().poseAt(t);
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
        model.hat.copyFrom(model.head);
        SHIFTED.add(model);
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
