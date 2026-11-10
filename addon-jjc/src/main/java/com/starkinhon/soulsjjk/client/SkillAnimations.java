package com.starkinhon.soulsjjk.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.starkinhon.soulsjjk.client.cutscene.CutscenePlayer;
import com.starkinhon.soulsjjk.skill.AgarraoSpec;
import com.starkinhon.soulsjjk.skill.DestructionBopSpec;
import com.starkinhon.soulsjjk.skill.EsferaSpec;
import com.starkinhon.soulsjjk.skill.AnimationSpec;
import com.starkinhon.soulsjjk.skill.DefesaSpec;
import com.starkinhon.soulsjjk.skill.PoseTimeline;
import com.starkinhon.soulsjjk.skill.SkillSpec;
import com.starkinhon.soulsjjk.skill.SkillSpecs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.joml.Quaternionf;
import org.joml.Vector3f;

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
    // Skills with a hand-made keyframe animation (KeyframeAnimation) play it instead of the Forja pose: from startTick
    // for endTicks (the last pose held), then blended out over KEYFRAME_OUT ticks. releasedAt: the tick a held skill was
    // let go (its animation waits at its "soltar" mark until then), -1 before.
    private record Keyframed(KeyframeAnimation animation, int startTick, int endTicks, String skill, int releasedAt) {
    }

    private static final Map<Integer, Keyframed> KEYFRAMED = new HashMap<>();
    // The sword moves (combat/SwordArts) are the keyframe animations named "espada.<move>".
    public static final String SWORD_PREFIX = "espada.";
    // The bare-fist base moves (combo, Forte, Barragem, Dash Attack) are the keyframe animations named "soco.<move>"
    // (made with tools/poses).
    public static final String FIST_PREFIX = "soco.";
    // Sukuna's techniques, baked frame by frame from their 3D previews (forja/sukuna-*): they run to their own end and
    // keep the body square with the aim (the arrow of the Fuga flies where he looks).
    public static final String SUKUNA_PREFIX = "sukuna.";
    // How the right hand is turned about the wrist right now (the sword moves), radians x, y, z: entity id -> angles.
    private static final Map<Integer, float[]> WRIST = new HashMap<>();
    private static final double KEYFRAME_OUT = 6;
    // Entities whose body is kept square with where they look while their move plays (a teleport that turns them
    // around would otherwise leave the body turned sideways, since vanilla only drags it along once the head is far off).
    private static final Set<Integer> SQUARE = new HashSet<>();
    private static final Set<HumanoidModel<?>> SHIFTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final float DEG = (float) (Math.PI / 180);

    private SkillAnimations() {
    }

    // "skill#part" plays one part of a move with several steps, timed by the server (see AgarraoSpec.phase); the
    // number after a second # is how long the part runs, when only the server knows (a dash until it reaches someone).
    // An empty skill stops whatever is playing (the move was cut off).
    public static void play(int entityId, String skill, boolean awakened) {
        if (com.starkinhon.soulsjjk.network.ModPayloads.PlaySkillAnimation.RELEASE.equals(skill)) {
            // Let go: an animation waiting at its "soltar" mark plays the rest; anything else stops.
            Keyframed held = KEYFRAMED.get(entityId);
            Entity who = entity(entityId);
            if (held != null && who != null && held.animation().release() >= 0 && held.releasedAt() < 0) {
                KEYFRAMED.put(entityId, new Keyframed(held.animation(), held.startTick(), held.endTicks(), held.skill(), who.tickCount));
                return;
            }
            skill = "";
        }
        SQUARE.remove(entityId);
        KEYFRAMED.remove(entityId);
        WRIST.remove(entityId);
        if (skill.isEmpty()) {
            PLAYING.remove(entityId);
            return;
        }
        var keyframes = skill.contains("#") || !net.minecraft.resources.ResourceLocation.isValidPath(skill)
                ? java.util.Optional.<KeyframeAnimation>empty() : KeyframeAnimation.of(skill);
        Entity who = entity(entityId);
        if (keyframes.isPresent() && who != null) {
            KEYFRAMED.put(entityId, new Keyframed(keyframes.get(), who.tickCount, keyframeEnd(skill, awakened), skill, -1));
            if (skill.startsWith(SWORD_PREFIX) || skill.startsWith(FIST_PREFIX) || skill.startsWith(SUKUNA_PREFIX) || keyframes.get().followLook()) {
                // The cuts and punches are made facing ahead: the body turns with the aim, or they would go off to the side.
                SQUARE.add(entityId);
                return;
            }
            if (!SkillSpecs.exists(skill)) {
                // Only the keyframes (gojo.dominio_relampago): nothing more to read.
                return;
            }
        }
        String[] parts = skill.split("#");
        if (parts.length > 1 && AgarraoSpec.SKILL.equals(parts[0])) {
            int ticks = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            play(entityId, AgarraoSpec.get(awakened).phase(parts[1], ticks));
            return;
        }
        if (parts.length > 1 && DestructionBopSpec.SKILL.equals(parts[0])) {
                        SQUARE.add(entityId);
            return;
        }
        play(entityId, SkillSpecs.get(skill, AnimationSpec.class, awakened));
    }

    public static void play(int entityId, PoseTimeline spec) {
        Entity entity = entity(entityId);
        if (entity != null) {
            // Always from Minecraft's standing pose, never from whatever was showing, so poses can't stack up.
            PLAYING.put(entityId, new Playing(spec, entity.tickCount, SkillSpec.Pose.ZERO, 0, 0));
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

    // How long a hand-made animation runs, the last pose held to the end. Gojo's domain: until the tunnel is over.
    private static int keyframeEnd(String skill, boolean awakened) {
        if (skill.startsWith(SWORD_PREFIX) || skill.startsWith(FIST_PREFIX) || skill.startsWith(SUKUNA_PREFIX)) {
            return KeyframeAnimation.of(skill).map(a -> (int) Math.ceil(a.length() * 20)).orElse(0);
        }
        if (com.starkinhon.soulsjjk.skill.DominioSpec.SKILL.equals(skill)) {
            return com.starkinhon.soulsjjk.skill.DominioSpec.get(awakened).voidTick();
        }
        if (!SkillSpecs.exists(skill)) {
            // Only a keyframe animation (no spec file): it runs to its own end.
            return KeyframeAnimation.of(skill).map(a -> (int) Math.ceil(a.length() * 20)).orElse(0);
        }
        int spec = (int) Math.ceil(SkillSpecs.get(skill, AnimationSpec.class, awakened).animationEnd());
        // The skill poses made with tools/poses run to their own end too (it can go a little past the spec's).
        return KeyframeAnimation.of(skill).filter(KeyframeAnimation::followLook).map(a -> Math.max(spec, (int) Math.ceil(a.length() * 20)))
                .orElse(spec);
    }

    // Seconds into a keyframe animation at raw ticks since it started: it waits at its "soltar" mark until let go.
    private static double seconds(Keyframed keyframed, double raw) {
        float release = keyframed.animation().release();
        if (release < 0) {
            return Math.max(0, raw) / 20;
        }
        if (keyframed.releasedAt() < 0) {
            return Math.min(Math.max(0, raw) / 20, release);
        }
        return release + Math.max(0, raw - (keyframed.releasedAt() - keyframed.startTick())) / 20;
    }

    // Ticks after the start when a keyframe animation reaches its last pose (blended out after that).
    private static double end(Keyframed keyframed) {
        if (keyframed.releasedAt() < 0) {
            return keyframed.endTicks();
        }
        KeyframeAnimation a = keyframed.animation();
        return keyframed.releasedAt() - keyframed.startTick() + Math.max(0, a.length() - a.release()) * 20;
    }

    private static Entity entity(int entityId) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(entityId);
    }

    private static SkillSpec.Pose pose(Playing playing, double t, double raw) {
        SkillSpec.Pose pose = playing.spec().poseAt(t);
        return raw < BLEND_TICKS ? playing.from().lerp(pose, raw / BLEND_TICKS) : pose;
    }

    // True while the entity has a keyframe animation on screen (FirstPersonAnimation draws the arms from it).
    public static boolean keyframed(LivingEntity entity, float ageInTicks) {
        Keyframed keyframed = KEYFRAMED.get(entity.getId());
        if (keyframed == null) {
            return false;
        }
        double raw = ageInTicks - keyframed.startTick();
        return raw >= -1 && raw <= end(keyframed) + KEYFRAME_OUT;
    }

    // True while this entity plays a sword move (its blade leaves a trail, client/fx/BladeTrail).
    public static boolean swordMove(Entity entity, float ageInTicks) {
        Keyframed keyframed = KEYFRAMED.get(entity.getId());
        if (keyframed == null || !keyframed.skill().startsWith(SWORD_PREFIX)) {
            return false;
        }
        double raw = ageInTicks - keyframed.startTick();
        return raw >= -1 && raw <= keyframed.endTicks() + KEYFRAME_OUT;
    }

    // The sword move playing (null when none), and how far into it, in ticks.
    public static String swordMoveName(Entity entity) {
        Keyframed keyframed = KEYFRAMED.get(entity.getId());
        return keyframed == null || !keyframed.skill().startsWith(SWORD_PREFIX) ? null : keyframed.skill();
    }

    public static int swordMoveStart(Entity entity) {
        Keyframed keyframed = KEYFRAMED.get(entity.getId());
        return keyframed == null ? 0 : keyframed.startTick();
    }

    // How the main hand item is turned about the wrist right now (null: as vanilla holds it). Set while the body is posed.
    public static float[] wrist(Entity entity) {
        return WRIST.get(entity.getId());
    }

    // Called after the vanilla pose is set up. Angles are added on top, so the head still follows the aim.
    public static void apply(LivingEntity entity, HumanoidModel<?> model, float ageInTicks) {
        Keyframed keyframed = KEYFRAMED.get(entity.getId());
        if (keyframed != null && ageInTicks != 0) {
            double raw = ageInTicks - keyframed.startTick();
            double end = end(keyframed);
            if (raw > end + KEYFRAME_OUT || raw < -1) {
                KEYFRAMED.remove(entity.getId());
                WRIST.remove(entity.getId());
                SQUARE.remove(entity.getId());
            } else {
                // Where vanilla points the head (the aim), for animations that keep following it.
                float lookX = model.head.xRot;
                float lookY = model.head.yRot;
                standStill(model);
                if (SQUARE.contains(entity.getId())) {
                    entity.yBodyRot = entity.yBodyRotO = entity.getYHeadRot();
                }
                float weight = (float) (raw <= end ? 1 : 1 - (raw - end) / KEYFRAME_OUT);
                float seconds = (float) seconds(keyframed, raw);
                keyframed.animation().apply(model, seconds, weight);
                // As the move blends out, the character's own stance blends in (not Minecraft's plain standing pose).
                if (weight < 1 && StancePoses.wants(entity, model, ageInTicks)) {
                    StancePoses.pose(entity, model, ageInTicks, 1 - weight);
                }
                if (keyframed.animation().followLook()) {
                    model.head.xRot += lookX;
                    model.head.yRot += lookY;
                }
                float[] wrist = keyframed.animation().wrist(seconds, weight);
                if (wrist == null) {
                    WRIST.remove(entity.getId());
                } else {
                    WRIST.put(entity.getId(), wrist);
                }
                model.hat.copyFrom(model.head);
                SHIFTED.add(model);
                return;
            }
        }
        Playing playing = PLAYING.get(entity.getId());
        double raw = playing == null ? -1 : ageInTicks - playing.startTick();
        if (playing != null && raw > playing.end()) {
            PLAYING.remove(entity.getId());
        }
        // First person hands are posed with ageInTicks 0, which lands here as t < 0 and leaves the animation running.
        boolean animating = raw >= 0 && raw <= playing.end();
        boolean guard = ageInTicks != 0 && GUARDING.contains(entity.getId());
        // The Editor de Cutscenes can pose the body too, on top of the move or in its place.
        CutscenePlayer.PoseOverride cut = ageInTicks != 0 ? CutscenePlayer.pose(entity) : null;
        if (!animating && !KEYFRAMED.containsKey(entity.getId())) {
            SQUARE.remove(entity.getId());
        } else if (SQUARE.contains(entity.getId())) {
            entity.yBodyRot = entity.yBodyRotO = entity.getYHeadRot();
        }
        if (!animating && !guard && cut == null) {
            if (StancePoses.wants(entity, model, ageInTicks)) {
                stance(entity, model, ageInTicks);
                return;
            }
            unshift(model);
            return;
        }
        if (animating || cut != null) {
            standStill(model);
        }
        SkillSpec.Pose pose = animating ? pose(playing, playing.time(raw), raw) : guard ? DefesaSpec.get().pose() : SkillSpec.Pose.ZERO;
        if (cut != null) {
            pose = cut.replace() ? cut.pose() : pose.plus(cut.pose());
        }
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
        // Skill poses (all made in the Forja) open the arm before raising it, like the page; combo punches that twist
        // the chest (giro) and the guard keep the older order they were tuned with.
        boolean forja = (animating || cut != null) && pose.giro() == 0;
        arm(model.rightArm, pose.bracoDir(), lean, cos, sin, 1, forja);
        arm(model.leftArm, pose.bracoEsq(), lean, cos, sin, -1, forja);
        twist(model, (float) pose.giro() * DEG, sin);
        turn(model, (float) pose.corpo() * DEG);
        leg(model.rightLeg, pose.pernaDir(), 1);
        leg(model.leftLeg, pose.pernaEsq(), -1);
        turn((float) pose.pernas() * DEG, model.rightLeg, model.leftLeg);
        model.hat.copyFrom(model.head);
        SHIFTED.add(model);
    }

    // Nothing playing: the character's standing / walking / running pose (StancePoses). The head keeps following the
    // aim, and an arm holding something keeps Minecraft's way of holding it (only moved with the shoulder).
    private static void stance(LivingEntity entity, HumanoidModel<?> model, float ageInTicks) {
        float lookX = model.head.xRot;
        float lookY = model.head.yRot;
        float[] right = model.rightArmPose != HumanoidModel.ArmPose.EMPTY ? new float[] {model.rightArm.xRot, model.rightArm.yRot, model.rightArm.zRot} : null;
        float[] left = model.leftArmPose != HumanoidModel.ArmPose.EMPTY ? new float[] {model.leftArm.xRot, model.leftArm.yRot, model.leftArm.zRot} : null;
        standStill(model);
        StancePoses.pose(entity, model, ageInTicks, 1);
        model.head.xRot += lookX;
        model.head.yRot += lookY;
        if (right != null) {
            model.rightArm.setRotation(right[0], right[1], right[2]);
        }
        if (left != null) {
            model.leftArm.setRotation(left[0], left[1], left[2]);
        }
        model.hat.copyFrom(model.head);
        SHIFTED.add(model);
    }

    // While a move plays, the vanilla pose underneath (walking, punching, sneaking, looking around) is cleared, so the
    // move's pose always starts from Minecraft's default stance with everything at zero.
    private static void standStill(HumanoidModel<?> model) {
        for (ModelPart part : new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
            part.setRotation(0, 0, 0);
            part.xScale = part.yScale = part.zScale = 1;
        }
        model.head.setPos(0, 0, 0);
        model.body.setPos(0, 0, 0);
        model.rightArm.setPos(-5, 2, 0);
        model.leftArm.setPos(5, 2, 0);
        model.rightLeg.setPos(-1.9f, 12, 0);
        model.leftLeg.setPos(1.9f, 12, 0);
        model.crouching = false;
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

    // Turns the posed upper body (chest and arms, as set above) around the vertical, like the Forja's corpo; the head
    // stays as it is so it keeps looking ahead.
    private static void turn(HumanoidModel<?> model, float corpo) {
        if (corpo == 0) {
            return;
        }
        model.body.yRot -= corpo;
        float s = (float) Math.sin(corpo);
        float c = (float) Math.cos(corpo);
        // The neck goes around with the chest (it is off-center when leaning); the head itself does not turn.
        float hx = model.head.x;
        model.head.x = hx * c - model.head.z * s;
        model.head.z = hx * s + model.head.z * c;
        turn(corpo, model.rightArm, model.leftArm);
    }

    // Carries limbs, already posed, around the vertical through the middle of the body.
    private static void turn(float angle, ModelPart... parts) {
        if (angle == 0) {
            return;
        }
        float s = (float) Math.sin(angle);
        float c = (float) Math.cos(angle);
        for (ModelPart part : parts) {
            float x = part.x;
            float z = part.z;
            part.x = x * c - z * s;
            part.z = x * s + z * c;
            Quaternionf q = new Quaternionf().rotationY(-angle).rotateZYX(part.zRot, part.yRot, part.xRot);
            Vector3f euler = q.getEulerAnglesZYX(new Vector3f());
            part.xRot = euler.x;
            part.yRot = euler.y;
            part.zRot = euler.z;
        }
    }

    private static void leg(ModelPart leg, SkillSpec.Braco perna, int outward) {
        if (perna != null) {
            leg.xRot -= (float) perna.frente() * DEG;
            leg.zRot += outward * (float) perna.abrir() * DEG;
        }
    }

    // frente raises the arm forward; abrir opens it to the side (negative closes it towards the chest).
    // forja: opened first, then raised (the page's X-then-Z), turned into Minecraft's Z-Y-X angles.
    private static void arm(ModelPart arm, SkillSpec.Braco braco, float lean, float cos, float sin, int outward, boolean forja) {
        arm.xRot += lean - (float) braco.frente() * DEG;
        arm.zRot += outward * (float) braco.abrir() * DEG;
        if (forja) {
            Vector3f euler = new Quaternionf().rotationY(arm.yRot).rotateX(arm.xRot).rotateZ(arm.zRot).getEulerAnglesZYX(new Vector3f());
            arm.xRot = euler.x;
            arm.yRot = euler.y;
            arm.zRot = euler.z;
        }
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
        // nor the x of the head, body and legs (a pose that turns the hips moves them)
        model.head.x = 0;
        model.body.x = 0;
        model.rightLeg.x = -1.9f;
        model.leftLeg.x = 1.9f;
        // (a hand-made animation may have scaled parts; the model is shared by everyone drawn with it)
        for (ModelPart part : new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
            part.xScale = part.yScale = part.zScale = 1;
        }
        model.hat.copyFrom(model.head);
        // The attack swing sets the arm z itself.
        if (model.attackTime <= 0) {
            model.rightArm.z = 0;
            model.leftArm.z = 0;
        }
    }
}
