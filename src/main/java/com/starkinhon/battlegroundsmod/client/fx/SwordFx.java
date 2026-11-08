package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.client.SkillAnimations;
import com.starkinhon.battlegroundsmod.client.outfit.AizenSwordRenderer;
import com.starkinhon.battlegroundsmod.combat.SwordArts;
import com.starkinhon.battlegroundsmod.item.AizenSwordItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// The sword moves' looks (combat/SwordArts), drawn with shader shapes only:
// - The blade's trail: while a sword move plays, where the blade really is (from the posed arm and the held item, the
//   same transforms the game draws it with) is kept every frame for a few ticks, and the strip it swept is drawn as
//   light: the power's color fading out behind, a white edge along the path of the tip. Only fast swings show (slow
//   moves leave nothing), so the trail is exactly the cut the animation makes, whatever the animation is.
// - The blade lights up while the Ataque Forte charges, and a glint runs along it when the charge is full.
// - Cuts: a thin line of light across whoever got cut, opening from the middle, then thinning out (anime "it was
//   already cut"). The barrage's hits put one at a new angle each time.
// - The wave: the fully charged Forte throws its cut forward as a crescent of light.
// Everything glows (it all goes to the bloom).
public final class SwordFx {
    // Network kinds: see combat/SwordArts.
    public static final int CUT = SwordArts.FX_CUT;
    public static final int WAVE = SwordArts.FX_WAVE;
    public static final int COLOR = SwordArts.FX_COLOR;
    public static final int GLINT = SwordArts.FX_GLINT;
    public static final int MOON = SwordArts.FX_MOON;
    public static final int MOON_HIT = SwordArts.FX_MOON_HIT;
    public static final int MOON_FORM = SwordArts.FX_MOON_FORM;
    public static final int MOON_TARGET = SwordArts.FX_MOON_TARGET;
    public static final int MOON_STRETCH = SwordArts.FX_MOON_STRETCH;

    // How long the trail lasts behind the blade, in ticks.
    private static final float TRAIL_TICKS = 3.2f;
    private static final float CUT_TICKS = 11;
    private static final float WAVE_SPEED = 1.7f;
    private static final float[] WHITE = {1, 1, 1};

    private record Sample(double time, Vec3 base, Vec3 tip) {
    }

    private static final class Trail {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        int entityId;
    }

    private record Cut(Vec3 pos, Vec3 dir, float length, float width, int color, double born) {
    }

    private record Wave(Vec3 pos, Vec3 dir, float range, float tilt, int color, double born) {
    }

    private record Glint(int entityId, double born) {
    }

    private record Origin(Vector3f offset, Vec3 lerpPos) {
    }

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static final Map<Integer, Integer> COLORS = new HashMap<>();
    private static final Map<Integer, Origin> ORIGINS = new HashMap<>();
    private static final List<Cut> CUTS = new ArrayList<>();
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final List<Glint> GLINTS = new ArrayList<>();

    private SwordFx() {
    }

    public static void receive(int packed, int entityId, Vec3 pos, Vec3 dir, float a, float b) {
        int kind = packed & 0xF;
        int color = packed >>> 4 & 0xFFFFFF;
        double now = now();
        switch (kind) {
            case CUT -> CUTS.add(new Cut(pos, unit(dir), a, b, color, now));
            case WAVE -> WAVES.add(new Wave(pos, unit(dir), a, b, color, now));
            case COLOR -> COLORS.put(entityId, color);
            case GLINT -> GLINTS.add(new Glint(entityId, now));
            case MOON -> {
                MoonSlashRenderer.receive(pos, dir, (int) a);
                com.starkinhon.battlegroundsmod.client.FormKanji.cast(entityId, 1);
            }
            case MOON_HIT -> MoonSlashRenderer.hit(pos);
            case MOON_FORM -> {
                MoonFormsRenderer.receive((int) b, pos, dir, (int) a);
                com.starkinhon.battlegroundsmod.client.FormKanji.cast(entityId, (int) b % 100);
            }
            case MOON_TARGET -> MoonFormsRenderer.target((int) a, (int) b, entityId);
            case MOON_STRETCH -> com.starkinhon.battlegroundsmod.client.outfit.SwordStretch.set(entityId, a > 0.5f);
            default -> {
            }
        }
    }

    public static boolean active() {
        return !TRAILS.isEmpty() || !CUTS.isEmpty() || !WAVES.isEmpty() || !GLINTS.isEmpty();
    }

    public static void clear() {
        TRAILS.clear();
        CUTS.clear();
        WAVES.clear();
        GLINTS.clear();
        ORIGINS.clear();
        MoonSlashRenderer.clear();
        MoonFormsRenderer.clear();
    }

    private static Vec3 unit(Vec3 v) {
        return v.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : v.normalize();
    }

    private static double now() {
        return ScreenFx.now() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    // ---------- where the blade is ----------

    // Before an entity is drawn: where its drawing starts (relative to whichever camera draws it, shadow passes too),
    // so a blade drawn later in that pass can be put back in the world.
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!SkillAnimations.swordMove(entity, entity.tickCount + event.getPartialTick())) {
            ORIGINS.remove(entity.getId());
            return;
        }
        Vector3f offset = event.getPoseStack().last().pose().getTranslation(new Vector3f());
        ORIGINS.put(entity.getId(), new Origin(offset, entity.getPosition(event.getPartialTick())));
    }

    // From ItemInHandLayer, at the wrist (pose is where the held item is drawn from, the wrist turn already in).
    public static void atWrist(LivingEntity entity, ItemStack stack, PoseStack pose) {
        Origin origin = ORIGINS.get(entity.getId());
        if (origin == null || stack.isEmpty()) {
            return;
        }
        Vec3[] blade = blade(entity, stack, pose);
        if (blade != null) {
            Vec3 shift = origin.lerpPos().subtract(origin.offset().x, origin.offset().y, origin.offset().z);
            record(entity, blade[0].add(shift), blade[1].add(shift));
        }
    }

    // From first person (FirstPersonAnimation): pose already maps to the world, relative to the entity's feet.
    public static void atWristFirstPerson(LivingEntity entity, ItemStack stack, PoseStack pose, Vec3 feet) {
        Vec3[] blade = blade(entity, stack, pose);
        if (blade != null) {
            record(entity, blade[0].add(feet), blade[1].add(feet));
        }
    }

    // The blade's base and tip through pose, the way the item is drawn (its third person display transform, then the
    // sword's own model: Aizen's 3D one, or a sword sprite's diagonal).
    private static Vec3[] blade(LivingEntity entity, ItemStack stack, PoseStack pose) {
        pose.pushPose();
        try {
            BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, entity.getId());
            model.applyTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, pose, false);
            pose.translate(-0.5f, -0.5f, -0.5f);
            Vector3f base;
            Vector3f tip;
            if (stack.getItem() instanceof AizenSwordItem) {
                AizenSwordRenderer.toSword(pose, true);
                base = new Vector3f(0, 1.2f, 0);
                tip = new Vector3f(0, 21, 0);
            } else if (stack.getItem() instanceof com.starkinhon.battlegroundsmod.item.KokushiboSwordItem
                    || stack.getItem() instanceof com.starkinhon.battlegroundsmod.item.KokushiboAwakenedSwordItem) {
                com.starkinhon.battlegroundsmod.client.outfit.KokushiboSwordRenderer.toSword(pose, true);
                base = new Vector3f(0, com.starkinhon.battlegroundsmod.client.outfit.KokushiboSwordRenderer.BLADE_BASE, 0);
                tip = new Vector3f(0, stack.getItem() instanceof com.starkinhon.battlegroundsmod.item.KokushiboAwakenedSwordItem
                        ? com.starkinhon.battlegroundsmod.client.outfit.KokushiboSwordRenderer.LONG_TIP
                        : com.starkinhon.battlegroundsmod.client.outfit.KokushiboSwordRenderer.TIP, 0);
            } else {
                // A sword sprite: the blade on the diagonal, from past the guard to the tip.
                base = new Vector3f(0.42f, 0.42f, 0.5f);
                tip = new Vector3f(0.95f, 0.95f, 0.5f);
            }
            Matrix4f m = pose.last().pose();
            m.transformPosition(base);
            m.transformPosition(tip);
            return new Vec3[] {new Vec3(base), new Vec3(tip)};
        } finally {
            pose.popPose();
        }
    }

    private static void record(LivingEntity entity, Vec3 base, Vec3 tip) {
        double t = now();
        Trail trail = TRAILS.computeIfAbsent(entity.getId(), id -> new Trail());
        trail.entityId = entity.getId();
        Sample last = trail.samples.peekLast();
        // Once per frame (a shader pack's shadow pass draws the entity twice).
        if (last != null && Math.abs(last.time() - t) < 1.0E-4) {
            return;
        }
        // Going back in time, or the blade jumping (he went through someone): start the trail over.
        if (last != null && (t < last.time() || last.base().distanceToSqr(base) > 4)) {
            trail.samples.clear();
        }
        trail.samples.addLast(new Sample(t, base, tip));
    }

    // ---------- drawing ----------

    static void render(Vec3 camera, double now, float partial) {
        drawTrails(camera, now);
        CUTS.removeIf(c -> now - c.born() > CUT_TICKS);
        for (Cut c : CUTS) {
            drawCut(c, camera, (float) (now - c.born()));
        }
        WAVES.removeIf(w -> now - w.born() > w.range() / WAVE_SPEED + 8);
        for (Wave w : WAVES) {
            drawWave(w, camera, (float) (now - w.born()));
        }
        GLINTS.removeIf(g -> now - g.born() > 8);
        for (Glint g : GLINTS) {
            drawGlint(g, camera, (float) (now - g.born()));
        }
    }

    private static int colorOf(int entityId) {
        return COLORS.getOrDefault(entityId, 0x9FD8FF);
    }

    private static void drawTrails(Vec3 camera, double now) {
        Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            Trail trail = it.next().getValue();
            while (!trail.samples.isEmpty() && now - trail.samples.peekFirst().time() > TRAIL_TICKS) {
                trail.samples.removeFirst();
            }
            if (trail.samples.isEmpty()) {
                it.remove();
                ORIGINS.remove(trail.entityId);
                continue;
            }
            int color = colorOf(trail.entityId);
            drawTrail(trail, camera, now, color);
            drawCharge(trail, camera, now, color);
        }
    }

    // The swept strip, smoothed (Catmull-Rom through the frames' samples) and faded by age and by speed.
    private static void drawTrail(Trail trail, Vec3 camera, double now, int color) {
        List<Sample> s = new ArrayList<>(trail.samples);
        if (s.size() < 2) {
            return;
        }
        int sub = 4;
        List<Vector3f> bases = new ArrayList<>();
        List<Vector3f> tips = new ArrayList<>();
        List<Float> ages = new ArrayList<>();
        List<Float> speeds = new ArrayList<>();
        for (int i = 0; i < s.size() - 1; i++) {
            Sample a = s.get(Math.max(0, i - 1));
            Sample b = s.get(i);
            Sample c = s.get(i + 1);
            Sample d = s.get(Math.min(s.size() - 1, i + 2));
            double dt = Math.max(1.0E-3, c.time() - b.time());
            // How fast the tip moved, blocks per tick: a swing is well over half a block per tick.
            float speed = (float) (c.tip().distanceTo(b.tip()) / dt);
            for (int k = 0; k < sub; k++) {
                float u = k / (float) sub;
                bases.add(catmull(a.base(), b.base(), c.base(), d.base(), u, camera));
                tips.add(catmull(a.tip(), b.tip(), c.tip(), d.tip(), u, camera));
                ages.add((float) (now - Mth.lerp(u, b.time(), c.time())));
                speeds.add(speed);
            }
        }
        Sample end = s.get(s.size() - 1);
        bases.add(rel(end.base(), camera));
        tips.add(rel(end.tip(), camera));
        ages.add((float) (now - end.time()));
        speeds.add(speeds.get(speeds.size() - 1));

        float[] rgb = FxDraw.rgb(color);
        float[] light = FxDraw.mix(rgb, WHITE, 0.55f);
        int n = bases.size();
        float[] vis = new float[n];
        for (int i = 0; i < n; i++) {
            float age = 1 - Mth.clamp(ages.get(i) / TRAIL_TICKS, 0, 1);
            float fast = Mth.clamp((speeds.get(i) - 0.35f) / 1.1f, 0, 1);
            vis[i] = age * age * fast;
        }
        for (int i = 0; i < n - 1; i++) {
            if (vis[i] <= 0.01f && vis[i + 1] <= 0.01f) {
                continue;
            }
            Vector3f b0 = bases.get(i), t0 = tips.get(i), b1 = bases.get(i + 1), t1 = tips.get(i + 1);
            // Three bands on the swept surface itself (no seams between frames), each fading in toward the tip: the
            // power's color over the whole blade, a lighter one over the outer half, a white edge where the tip went.
            FxDraw.quadFade(b0, b1, t1, t0, FxDraw.SOLID, 0, rgb, 0, 0, 0.42f * vis[i + 1], 0.42f * vis[i], true);
            FxDraw.quadFade(new Vector3f(b0).lerp(t0, 0.5f), new Vector3f(b1).lerp(t1, 0.5f), t1, t0, FxDraw.SOLID, 0, light,
                    0, 0, 0.5f * vis[i + 1], 0.5f * vis[i], true);
            FxDraw.quadFade(new Vector3f(b0).lerp(t0, 0.86f), new Vector3f(b1).lerp(t1, 0.86f), t1, t0, FxDraw.SOLID, 0, WHITE,
                    0, 0, 0.95f * vis[i + 1], 0.95f * vis[i], true);
        }
    }

    // While the Forte charges, the blade lights up more and more.
    private static void drawCharge(Trail trail, Vec3 camera, double now, int color) {
        Entity entity = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(trail.entityId);
        if (entity == null || !"espada.forte_carga".equals(SkillAnimations.swordMoveName(entity))) {
            return;
        }
        Sample last = trail.samples.peekLast();
        double age = entity.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false) - SkillAnimations.swordMoveStart(entity);
        float charge = Mth.clamp((float) (age - 6) / 30f, 0, 1);
        if (charge <= 0) {
            return;
        }
        float[] rgb = FxDraw.rgb(color);
        Vector3f base = rel(last.base(), camera);
        Vector3f tip = rel(last.tip(), camera);
        float pulse = 0.85f + 0.15f * Mth.sin((float) now * (0.6f + charge));
        FxDraw.ribbon(base, tip, 0.5f + 0.5f * charge, FxDraw.BEAM, 0.1f, rgb, 0.45f * charge * pulse, true);
        FxDraw.ribbon(base, tip, 0.12f + 0.08f * charge, FxDraw.BEAM, 0.7f, FxDraw.mix(rgb, WHITE, 0.6f), 0.8f * charge, true);
        FxDraw.billboard(tip, 0.6f + 0.9f * charge, (float) now * 0.05f, FxDraw.FLARE, 6 / 32f, FxDraw.mix(rgb, WHITE, 0.5f), 0.6f * charge * pulse, true);
    }

    // The full-charge glint running from the guard to the tip.
    private static void drawGlint(Glint g, Vec3 camera, float age) {
        Trail trail = TRAILS.get(g.entityId());
        if (trail == null || trail.samples.isEmpty()) {
            return;
        }
        Sample last = trail.samples.peekLast();
        float u = Mth.clamp(age / 5f, 0, 1);
        Vector3f at = rel(last.base().lerp(last.tip(), u), camera);
        float fade = 1 - Mth.clamp((age - 5) / 3f, 0, 1);
        FxDraw.billboard(at, 1.6f, age * 0.2f, FxDraw.FLARE, 4 / 32f, WHITE, 0.95f * fade, true);
        FxDraw.billboard(at, 0.7f, 0, FxDraw.SOFT, 0.9f, WHITE, fade, true);
    }

    // A cut opens from its middle along the line in a tick and a half, holds, then thins and fades.
    private static void drawCut(Cut c, Vec3 camera, float age) {
        if (age < 0) {
            return;
        }
        float open = 1 - (float) Math.pow(1 - Mth.clamp(age / 1.5f, 0, 1), 3);
        float fade = 1 - Mth.clamp((age - 3) / (CUT_TICKS - 3), 0, 1);
        float thin = 0.35f + 0.65f * fade;
        float[] rgb = FxDraw.rgb(c.color());
        Vector3f center = rel(c.pos(), camera);
        Vector3f half = c.dir().toVector3f().mul(c.length() * 0.5f * open);
        Vector3f a = new Vector3f(center).sub(half);
        Vector3f b = new Vector3f(center).add(half);
        // Overshoot a little past each end, thinner.
        Vector3f a2 = new Vector3f(center).sub(new Vector3f(half).mul(1.25f));
        Vector3f b2 = new Vector3f(center).add(new Vector3f(half).mul(1.25f));
        FxDraw.ribbon(a2, b2, 0.45f * c.width() * thin, FxDraw.BEAM, 0.05f, rgb, 0.6f * fade, true);
        FxDraw.ribbon(a, b, 0.2f * c.width() * thin, FxDraw.BEAM, 0.6f, FxDraw.mix(rgb, WHITE, 0.5f), 0.9f * fade, true);
        FxDraw.ribbon(a, b, 0.07f * c.width() * thin, FxDraw.BEAM, 1, WHITE, fade, true);
        if (age < 3) {
            float flash = 1 - age / 3;
            FxDraw.billboard(center, 0.9f * c.width() * flash + 0.3f, age * 0.3f, FxDraw.FLARE, 6 / 32f, WHITE, 0.75f * flash, true);
        }
    }

    // A crescent of light flying forward, bowed toward where it goes, thick in the middle and sharp at the ends.
    private static void drawWave(Wave w, Vec3 camera, float age) {
        float travel = Math.min(age * WAVE_SPEED, w.range());
        float fade = age * WAVE_SPEED < w.range() ? Mth.clamp(age / 1.5f, 0, 1) : 1 - Mth.clamp((age - w.range() / WAVE_SPEED) / 8, 0, 1);
        if (fade <= 0) {
            return;
        }
        Vector3f dir = w.dir().toVector3f();
        Vector3f side = new Vector3f(dir).cross(0, 1, 0);
        if (side.lengthSquared() < 1.0E-4f) {
            side.set(1, 0, 0);
        }
        side.normalize().rotateAxis(w.tilt() * Mth.DEG_TO_RAD, dir.x, dir.y, dir.z);
        Vector3f center = rel(w.pos(), camera).add(new Vector3f(dir).mul(travel));
        float radius = 2.0f + 0.04f * travel;
        int steps = 24;
        Vector3f[] pts = new Vector3f[steps + 1];
        float[] widths = new float[steps + 1];
        float[] us = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float th = Mth.lerp(i / (float) steps, -1.25f, 1.25f);
            pts[i] = new Vector3f(center).add(new Vector3f(side).mul(Mth.sin(th) * radius)).add(new Vector3f(dir).mul((Mth.cos(th) - 1) * radius * 0.5f));
            widths[i] = 0.9f * (float) Math.pow(Math.max(0, Mth.cos(th)), 1.4f);
            us[i] = i / (float) steps;
        }
        float[] rgb = FxDraw.rgb(w.color());
        float[] wide = new float[steps + 1];
        float[] thin = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            wide[i] = widths[i] * 1.6f;
            thin[i] = widths[i] * 0.3f;
        }
        FxDraw.strip(pts, wide, us, FxDraw.BEAM, 0.05f, rgb, 0.55f * fade, true);
        FxDraw.strip(pts, widths, us, FxDraw.BEAM, 0.5f, FxDraw.mix(rgb, WHITE, 0.5f), 0.85f * fade, true);
        FxDraw.strip(pts, thin, us, FxDraw.BEAM, 1, WHITE, fade, true);
        // Speed lines trailing off its ends.
        for (int e = -1; e <= 1; e += 2) {
            Vector3f tipPt = pts[e < 0 ? 2 : steps - 2];
            FxDraw.ribbon(tipPt, new Vector3f(tipPt).sub(new Vector3f(dir).mul(2.4f)), 0.08f, FxDraw.STREAK, 0.6f, rgb, 0.6f * fade, true);
        }
    }

    private static Vector3f catmull(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u, Vec3 camera) {
        return new Vector3f(
                Mth.catmullrom(u, (float) (a.x - camera.x), (float) (b.x - camera.x), (float) (c.x - camera.x), (float) (d.x - camera.x)),
                Mth.catmullrom(u, (float) (a.y - camera.y), (float) (b.y - camera.y), (float) (c.y - camera.y), (float) (d.y - camera.y)),
                Mth.catmullrom(u, (float) (a.z - camera.z), (float) (b.z - camera.z), (float) (c.z - camera.z), (float) (d.z - camera.z)));
    }

    private static Vector3f rel(Vec3 world, Vec3 camera) {
        return new Vector3f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z));
    }
}
