package com.starkinhon.battlegroundsmod.client.cutscene;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.client.FaceCutscene;
import com.starkinhon.battlegroundsmod.skill.SkillSpec;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// Plays a cutscene made in the Editor de Cutscenes (see CutsceneData) in place of a skill's hand-made one: the camera
// follows the keys (position, aim, roll and zoom, along a smooth path), only inside the marked stretches, and the two
// characters can be moved, turned and posed on top of what the skill does. Everything here is seen by this player only;
// the offsets of the characters are drawn, they don't move anyone for real. The editor runs the same maths, so what is
// seen there is what plays here.
public final class CutscenePlayer {
    private static final double BAR_HEIGHT = 0.11;
    private static final double DEFAULT_FOV = 70;
    private static final double DEFAULT_AIM_HEIGHT = 1.5;
    private static final int SETTLE_TICKS = 4;

    private static CutsceneData data;
    private static String skill = "";
    private static int casterId = -1;
    private static int targetId = -1;
    // Only the camera: the skill's own cutscene still runs and draws its bars and lines (the Esfera's trip to space).
    private static boolean cameraOnly;
    private static int age;
    private static Vec3 origin = Vec3.ZERO;
    private static Vec3 forward = new Vec3(0, 0, 1);
    private static Vec3 casterStart = Vec3.ZERO;
    private static Vec3 targetStart;
    private static Vec3 casterLast = Vec3.ZERO;
    private static Vec3 targetLast;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static boolean previousHideGui;
    // Worked out with the camera each frame, for the roll and zoom events that come after it.
    private static double frameWeight;
    private static double frameRoll;
    private static double frameFov;
    private static final Set<Integer> PUSHED = new HashSet<>();

    private CutscenePlayer() {
    }

    // Starts the editor's cutscene of this skill if there is one; false leaves the skill to its own cutscene.
    public static boolean start(String skillId, int caster, int target, boolean onlyCamera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        CutsceneData next;
        try {
            next = CutsceneData.load(skillId);
        } catch (Exception e) {
            CutsceneData.warn(skillId, e);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("§c[Souls] A cutscene de " + skillId + " tem um erro: "
                        + e.getMessage()), false);
            }
            return false;
        }
        Entity casterEntity = minecraft.level.getEntity(caster);
        if (next == null || !next.on() || casterEntity == null) {
            return false;
        }
        Entity targetEntity = target >= 0 && target != caster ? minecraft.level.getEntity(target) : null;
        if (data == null) {
            previousCameraType = minecraft.options.getCameraType();
            previousHideGui = minecraft.options.hideGui;
        }
        data = next;
        skill = skillId;
        casterId = caster;
        targetId = targetEntity != null ? target : -1;
        cameraOnly = onlyCamera;
        age = 0;
        casterStart = casterLast = casterEntity.position();
        targetStart = targetLast = targetEntity != null ? targetEntity.position() : null;
        Vec3 flat = targetStart == null ? Vec3.ZERO : new Vec3(targetStart.x - casterStart.x, 0, targetStart.z - casterStart.z);
        forward = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, casterEntity.getYRot()) : flat.normalize();
        origin = next.fromTarget() && targetStart != null ? targetStart : casterStart;
        PUSHED.clear();
        apply(minecraft, 0);
        return true;
    }

    // The camera to go back to at the end, when another cutscene already changed it before this one started.
    public static void restoreTo(CameraType cameraType) {
        previousCameraType = cameraType;
    }

    public static boolean active() {
        return data != null;
    }

    public static String skill() {
        return skill;
    }

    public static void tick() {
        if (data == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ++age >= data.end() || minecraft.level.getEntity(casterId) == null) {
            stop(minecraft);
            return;
        }
        // A cutscene measured from the target settles its origin on where the target really stands once the skill has
        // pinned it: the first position seen can still be mid-jump or catching up on this client.
        if (age <= SETTLE_TICKS && data.fromTarget() && targetId >= 0) {
            Entity target = minecraft.level.getEntity(targetId);
            if (target != null) {
                targetStart = targetLast = target.position();
                origin = targetStart;
            }
        }
        apply(minecraft, age);
    }

    // Third person (so the player's own body is drawn) and no HUD while the camera is the cutscene's.
    private static void apply(Minecraft minecraft, double t) {
        boolean filming = weight(t) > 0 || weight(t + 1) > 0;
        minecraft.options.setCameraType(filming ? CameraType.THIRD_PERSON_BACK : previousCameraType);
        if (!cameraOnly) {
            minecraft.options.hideGui = filming && data.hideHud() || previousHideGui;
        }
    }

    public static void stop() {
        if (data != null) {
            stop(Minecraft.getInstance());
        }
    }

    private static void stop(Minecraft minecraft) {
        data = null;
        casterId = -1;
        targetId = -1;
        frameWeight = 0;
        minecraft.options.setCameraType(previousCameraType);
        if (!cameraOnly) {
            minecraft.options.hideGui = previousHideGui;
        }
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (data == null || !data.lock()) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    // ---- Space of the scene ----

    private static Vec3 left() {
        return new Vec3(forward.z, 0, -forward.x);
    }

    private static Vec3 toWorld(double x, double y, double z) {
        return origin.add(left().scale(x)).add(0, y, 0).add(forward.scale(z));
    }

    private static Vec3 toWorldDir(double x, double y, double z) {
        return left().scale(x).add(0, y, 0).add(forward.scale(z));
    }

    private static Vec3 toLocal(Vec3 world) {
        Vec3 d = world.subtract(origin);
        return new Vec3(d.dot(left()), d.y, d.dot(forward));
    }

    // Where a character stands now, in the scene's space, with the editor's offset on top.
    private static Vec3 actorLocal(String name, float partialTick) {
        boolean caster = !"alvo".equals(name) || targetId < 0;
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(caster ? casterId : targetId);
        if (entity != null) {
            if (caster) {
                casterLast = entity.getPosition(partialTick);
            } else {
                targetLast = entity.getPosition(partialTick);
            }
        }
        Vec3 local = toLocal(caster ? casterLast : targetLast);
        ActorState state = actorState(caster ? "usuario" : "alvo", age + partialTick);
        return state == null ? local : local.add(state.x, state.y, state.z);
    }

    private static Vec3 actorStart(String name) {
        boolean caster = !"alvo".equals(name) || targetId < 0;
        return toLocal(caster ? casterStart : targetStart);
    }

    // ---- Camera ----

    // Where the camera goes this frame, or null when the player has their own view (outside the stretches).
    public static FaceCutscene.Shot shot(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer viewer = minecraft.player;
        frameWeight = 0;
        if (data == null || viewer == null || data.cameraKeys().isEmpty()) {
            return null;
        }
        double t = age + partialTick;
        double w = weight(t);
        if (w <= 0) {
            return null;
        }
        double[] c = camera(t, partialTick);
        Vec3 pos = toWorld(c[0], c[1], c[2]);
        double yaw = Math.toRadians(c[3]);
        double pitch = Math.toRadians(c[4]);
        Vec3 dir = toWorldDir(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        float mcYaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        float mcPitch = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
        if (w < 1) {
            Vec3 own = viewer.getEyePosition(partialTick);
            float ownYaw = viewer.getViewYRot(partialTick);
            float ownPitch = viewer.getViewXRot(partialTick);
            pos = own.lerp(pos, w);
            mcYaw = ownYaw + (float) (Mth.wrapDegrees(mcYaw - ownYaw) * w);
            mcPitch = (float) Mth.lerp(w, ownPitch, mcPitch);
        }
        frameWeight = w;
        frameRoll = c[5] * w;
        frameFov = c[6];
        return new FaceCutscene.Shot(pos, mcYaw, mcPitch);
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (data != null && frameWeight > 0 && frameRoll != 0) {
            // Positive rolls the picture clockwise, like the editor.
            event.setRoll(event.getRoll() - (float) frameRoll);
        }
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (data != null && frameWeight > 0 && event.usedConfiguredFov()) {
            event.setFOV(Mth.lerp(frameWeight, event.getFOV(), frameFov));
        }
    }

    // How much the camera is the cutscene's at time t: 1 inside a stretch, gliding in and out at its ends.
    private static double weight(double t) {
        double best = 0;
        for (CutsceneData.Trecho trecho : data.segments()) {
            double w = 0;
            if (t >= trecho.inicio() && t <= trecho.fim()) {
                w = trecho.entradaTicks() > 0 ? smooth((t - trecho.inicio()) / trecho.entradaTicks()) : 1;
            } else if (t > trecho.fim() && trecho.saidaTicks() > 0 && t < trecho.fim() + trecho.saidaTicks()) {
                w = 1 - smooth((t - trecho.fim()) / trecho.saidaTicks());
            }
            best = Math.max(best, w);
        }
        return best;
    }

    // [x, y, z, yaw, pitch, roll, fov] at time t.
    private static double[] camera(double t, float partialTick) {
        List<CutsceneData.CamKey> keys = data.cameraKeys();
        return curve(keys, t, CutsceneData.CamKey::t, CutsceneData.CamKey::suave, k -> cameraValues(k, partialTick),
                new boolean[] {false, false, false, true, false, true, false});
    }

    private static double[] cameraValues(CutsceneData.CamKey key, float partialTick) {
        double[] p = key.pos();
        double x = p[0];
        double y = p[1];
        double z = p[2];
        if (isActor(key.seguir())) {
            Vec3 moved = actorLocal(key.seguir(), partialTick).subtract(actorStart(key.seguir()));
            x += moved.x;
            y += moved.y;
            z += moved.z;
        }
        double[] r = key.rot() == null ? new double[3] : key.rot();
        double yaw = r.length > 0 ? r[0] : 0;
        double pitch = r.length > 1 ? r[1] : 0;
        double roll = r.length > 2 ? r[2] : 0;
        if (isActor(key.mirar())) {
            Vec3 at = actorLocal(key.mirar(), partialTick).add(0, key.mirarAltura() == null ? DEFAULT_AIM_HEIGHT : key.mirarAltura(), 0);
            double dx = at.x - x;
            double dy = at.y - y;
            double dz = at.z - z;
            if (dx * dx + dy * dy + dz * dz > 1.0E-6) {
                yaw = Math.toDegrees(Math.atan2(dx, dz));
                pitch = Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            }
        }
        double fov = key.fov() > 0 ? key.fov() : DEFAULT_FOV;
        return new double[] {x, y, z, yaw, pitch, roll, fov};
    }

    private static boolean isActor(String name) {
        return "usuario".equals(name) || "alvo".equals(name);
    }

    // ---- Characters ----

    private record ActorState(double x, double y, double z, double giro, SkillSpec.Pose pose, boolean replace) {
    }

    public record PoseOverride(SkillSpec.Pose pose, boolean replace) {
    }

    private static String actorName(int entityId) {
        if (data == null) {
            return null;
        }
        return entityId == casterId ? "usuario" : entityId == targetId && targetId >= 0 ? "alvo" : null;
    }

    private static ActorState actorState(String name, double t) {
        CutsceneData.Actor actor = data == null || name == null ? null : data.actor(name);
        List<CutsceneData.ActorKey> keys = actor == null || actor.chaves() == null ? List.of() : actor.chaves();
        if (keys.isEmpty() || t < keys.get(0).t() || t > keys.get(keys.size() - 1).t()) {
            return null;
        }
        double[] v = curve(keys, t, CutsceneData.ActorKey::t, CutsceneData.ActorKey::suave, k -> {
            double[] p = k.pos() == null || k.pos().length < 3 ? new double[3] : k.pos();
            return new double[] {p[0], p[1], p[2], k.giro()};
        }, new boolean[] {false, false, false, true});
        // Poses blend straight from key to key (eased), like the skill animations.
        int i = segment(keys, t, CutsceneData.ActorKey::t);
        CutsceneData.ActorKey a = keys.get(i);
        CutsceneData.ActorKey b = keys.get(Math.min(i + 1, keys.size() - 1));
        double span = b.t() - a.t();
        double u = span <= 0 ? 0 : ease(a.suave(), (t - a.t()) / span);
        SkillSpec.Pose pose = pose(a).lerp(pose(b), u);
        return new ActorState(v[0], v[1], v[2], v[3], pose, "substituir".equals(actor.modo()));
    }

    private static SkillSpec.Pose pose(CutsceneData.ActorKey key) {
        return key.pose() == null ? SkillSpec.Pose.ZERO : key.pose();
    }

    // The editor's pose for this body right now, for SkillAnimations; null when it has none.
    public static PoseOverride pose(LivingEntity entity) {
        String name = actorName(entity.getId());
        if (name == null) {
            return null;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        ActorState state = actorState(name, age + partialTick);
        return state == null ? null : new PoseOverride(state.pose(), state.replace());
    }

    // Moves and turns the body where the editor put it (drawn only).
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (data == null || event.isCanceled()) {
            return;
        }
        String name = actorName(event.getEntity().getId());
        ActorState state = name == null ? null : actorState(name, age + event.getPartialTick());
        if (state == null || state.x == 0 && state.y == 0 && state.z == 0 && state.giro == 0) {
            return;
        }
        Vec3 offset = toWorldDir(state.x, state.y, state.z);
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(offset.x, offset.y, offset.z);
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees((float) state.giro));
        PUSHED.add(event.getEntity().getId());
    }

    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    // ---- Bars and lines ----

    public static void renderGui(GuiGraphics graphics, DeltaTracker delta) {
        if (data == null || cameraOnly) {
            return;
        }
        double t = age + delta.getGameTimeDeltaPartialTick(false);
        int bar = data.bars() ? (int) Math.round(graphics.guiHeight() * BAR_HEIGHT * weight(t)) : 0;
        if (bar > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), bar, 0xFF000000);
            graphics.fill(0, graphics.guiHeight() - bar, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        }
        if (data.legendas() == null) {
            return;
        }
        var font = Minecraft.getInstance().font;
        for (CutsceneData.Legenda legenda : data.legendas()) {
            if (legenda == null || legenda.texto() == null || legenda.texto().isEmpty() || t < legenda.inicio() || t > legenda.fim()) {
                continue;
            }
            float alpha = (float) Mth.clamp(Math.min(t - legenda.inicio(), legenda.fim() - t) / 2, 0, 1);
            if (alpha <= 0.02f) {
                continue;
            }
            int color = parse(legenda.cor(), 0xFFFFFF) | Math.max(4, (int) (alpha * 255)) << 24;
            float scale = (float) Math.max(0.5, legenda.tamanho() <= 0 ? 1 : legenda.tamanho());
            Component text = Component.literal(legenda.texto());
            float y = graphics.guiHeight() - Math.max(bar, 8) - 10 - font.lineHeight * scale;
            graphics.pose().pushPose();
            graphics.pose().translate(graphics.guiWidth() / 2f, y, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, text, -font.width(text) / 2, 0, color, true);
            graphics.pose().popPose();
        }
    }

    private static int parse(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ---- Keyframe maths (the editor has the same) ----

    // Index of the key at or before t (0 when t is before the first).
    private static <K> int segment(List<K> keys, double t, java.util.function.ToDoubleFunction<K> time) {
        int i = 0;
        while (i + 1 < keys.size() && time.applyAsDouble(keys.get(i + 1)) <= t) {
            i++;
        }
        return i;
    }

    // Values at time t along a Catmull-Rom curve through the keys, the key's "suave" easing how it gets to the next one.
    // angle[c]: channel c is in degrees and takes the short way round.
    private static <K> double[] curve(List<K> keys, double t, java.util.function.ToDoubleFunction<K> time, Function<K, String> easing,
            Function<K, double[]> values, boolean[] angle) {
        int n = keys.size();
        if (n == 1 || t <= time.applyAsDouble(keys.get(0))) {
            return values.apply(keys.get(0));
        }
        if (t >= time.applyAsDouble(keys.get(n - 1))) {
            return values.apply(keys.get(n - 1));
        }
        int i = segment(keys, t, time);
        K a = keys.get(i);
        K b = keys.get(i + 1);
        double span = time.applyAsDouble(b) - time.applyAsDouble(a);
        double u = span <= 0 ? 1 : ease(easing.apply(a), (t - time.applyAsDouble(a)) / span);
        double[] p1 = values.apply(a);
        double[] p2 = values.apply(b);
        double[] p0 = i > 0 ? values.apply(keys.get(i - 1)) : p1;
        double[] p3 = i + 2 < n ? values.apply(keys.get(i + 2)) : p2;
        // A cut holds and jumps: no curve leaking into the keys around it.
        if ("corte".equals(easing.apply(a))) {
            return p1;
        }
        if (i > 0 && "corte".equals(easing.apply(keys.get(i - 1)))) {
            p0 = p1;
        }
        if ("corte".equals(easing.apply(b))) {
            p3 = p2;
        }
        double[] out = new double[p1.length];
        for (int c = 0; c < out.length; c++) {
            double v1 = p1[c];
            double v0 = p0[c];
            double v2 = p2[c];
            double v3 = p3[c];
            if (angle[c]) {
                v0 = v1 + Mth.wrapDegrees(v0 - v1);
                v2 = v1 + Mth.wrapDegrees(v2 - v1);
                v3 = v2 + Mth.wrapDegrees(v3 - v2);
            }
            out[c] = catmull(v0, v1, v2, v3, u);
        }
        return out;
    }

    private static double catmull(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u;
        double u3 = u2 * u;
        return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }

    static double ease(String type, double x) {
        x = Mth.clamp(x, 0, 1);
        if (type == null) {
            return smooth(x);
        }
        return switch (type) {
            case "linear" -> x;
            case "entrada" -> x * x * x;
            case "saida" -> 1 - Math.pow(1 - x, 3);
            case "corte" -> x >= 1 ? 1 : 0;
            default -> smooth(x);
        };
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
