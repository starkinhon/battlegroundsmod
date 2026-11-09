package com.starkinhon.battlegroundsmod.client.fx;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.SoulCeremony;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// The soul ceremony on the clients (boss/SoulCeremony has the rules and the clock). The body lies where it fell, with no
// red death tint. The soul leaves it slowly: a glowing copy of the boss in the soul's color, standing, rising. Then it
// breaks into motes that fly into the shape of the soul item's icon, each mote taking the color of its pixel: the icon
// is read from textures/item/soul_<id>.png when the ceremony starts, so new icons work with no code change (they only
// need a transparent background). Formed, it shines once, shrinks to the item's size and the real item falls from that
// spot; the body fades away. The item keeps a soft glow (and, for a Divine Soul, a beam of light) until it is picked up.
public final class SoulCeremonyFx {
    // The icon's width in the air while formed, and when it turns into the item.
    private static final float ICON_SIZE = 1.15f;
    private static final float ITEM_SIZE = 0.5f;
    private static final int MAX_MOTES = 900;
    private static final float[] WHITE = {1, 1, 1};

    private static final class Ceremony {
        final LivingEntity entity;
        final long start;
        final SoulCeremony.Timing timing;
        final float[] rgb;
        final boolean mine;
        final Icon icon;
        final Vec3 form;

        Ceremony(LivingEntity entity, long start, SoulCeremony.Timing timing, float[] rgb, boolean mine, Icon icon) {
            this.entity = entity;
            this.start = start;
            this.timing = timing;
            this.rgb = rgb;
            this.mine = mine;
            this.icon = icon;
            this.form = entity.position().add(0, SoulCeremony.FORM_HEIGHT, 0);
        }
    }

    // The soul's item on the ground after its ceremony.
    private static final class Glow {
        final int itemId;
        final long since;
        final float[] rgb;
        final boolean divine;
        boolean seen;

        Glow(int itemId, long since, float[] rgb, boolean divine) {
            this.itemId = itemId;
            this.since = since;
            this.rgb = rgb;
            this.divine = divine;
        }
    }

    // The icon's opaque pixels: position in the icon (-0.5..0.5, y up) and color.
    private record Icon(float[] x, float[] y, float[][] color, float spacing) {
    }

    private static final Map<Integer, Ceremony> CEREMONIES = new HashMap<>();
    private static final Map<Integer, String> POWERS = new HashMap<>();
    private static final List<Glow> GLOWS = new ArrayList<>();
    private static final Map<String, Icon> ICONS = new HashMap<>();
    private static boolean drawing;

    private SoulCeremonyFx() {
    }

    public static void start(int entityId, int killerId, String power, int color, boolean full) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(entityId) instanceof LivingEntity living)) {
            return;
        }
        boolean mine = minecraft.player != null && minecraft.player.getId() == killerId;
        // The server decides a tick after the death.
        CEREMONIES.put(entityId, new Ceremony(living, ScreenFx.now() - 1, SoulCeremony.Timing.of(full), FxDraw.rgb(color), mine, icon(power)));
        POWERS.put(entityId, power);
    }

    public static void drop(int entityId, int itemId) {
        Ceremony c = CEREMONIES.get(entityId);
        String power = POWERS.get(entityId);
        float[] rgb = c != null ? c.rgb : WHITE;
        GLOWS.add(new Glow(itemId, ScreenFx.now(), rgb, power != null && divine(power)));
    }

    private static boolean divine(String power) {
        for (Power p : Powers.divine()) {
            if (p.id().equals(power)) {
                return true;
            }
        }
        return false;
    }

    static boolean active() {
        return !CEREMONIES.isEmpty() || !GLOWS.isEmpty();
    }

    // The killer's HUD hides from the death until the soul is on the ground.
    public static boolean hidesHud() {
        long now = ScreenFx.now();
        for (Ceremony c : CEREMONIES.values()) {
            if (c.mine && now - c.start < c.timing.drop() + 4) {
                return true;
            }
        }
        return false;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            CEREMONIES.clear();
            POWERS.clear();
            GLOWS.clear();
            return;
        }
        long now = ScreenFx.now();
        Iterator<Map.Entry<Integer, Ceremony>> it = CEREMONIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Ceremony> e = it.next();
            Ceremony c = e.getValue();
            if (now - c.start > c.timing.end() + 4 || c.entity.level() != minecraft.level) {
                it.remove();
                POWERS.remove(e.getKey());
            }
        }
        GLOWS.removeIf(g -> {
            Entity item = minecraft.level.getEntity(g.itemId);
            if (item != null && item.isAlive()) {
                g.seen = true;
                return false;
            }
            // Picked up (or gone), or never arrived here.
            return g.seen || now - g.since > 100;
        });
    }

    // ---- The body and the soul's ghost, drawn in place of the vanilla body ----

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (drawing) {
            return;
        }
        LivingEntity entity = event.getEntity();
        Ceremony c = CEREMONIES.get(entity.getId());
        if (c == null || c.entity != entity) {
            return;
        }
        event.setCanceled(true);
        float partial = event.getPartialTick();
        double age = ScreenFx.now() + partial - c.start;
        SoulCeremony.Timing t = c.timing;
        LivingEntityRenderer renderer = event.getRenderer();
        MultiBufferSource source = event.getMultiBufferSource();
        PoseStack pose = event.getPoseStack();
        ResourceLocation skin;
        try {
            skin = renderer.getTextureLocation(entity);
        } catch (RuntimeException e) {
            return;
        }
        drawing = true;
        try {
            // The body: no red tint, a little darker once the soul has left, fading away at the end.
            float fade = (float) Mth.clamp((age - t.fade()) / Math.max(1, t.end() - t.fade()), 0, 1);
            float dim = 1 - 0.3f * (float) Mth.clamp((age - t.out()) / 20.0, 0, 1);
            MultiBufferSource body = type -> {
                if (fade <= 0) {
                    return new Tint(source.getBuffer(type), dim, dim, dim, 1, true);
                }
                ResourceLocation texture = texture(type, skin);
                return texture == null ? new Nothing() : new Tint(source.getBuffer(RenderType.entityTranslucent(texture)), dim, dim, dim, 1 - fade, true);
            };
            if (fade < 1) {
                renderer.render(entity, entity.getYRot(), partial, pose, body, event.getPackedLight());
            }

            // The ghost: the same model standing, glowing in the soul's color, rising out of the body.
            float ghost = ghostAlpha(t, age);
            if (ghost > 0.003f) {
                float rise = ghostRise(t, age);
                float[] rgb = c.rgb;
                MultiBufferSource glow = type -> {
                    ResourceLocation texture = texture(type, skin);
                    return texture == null ? new Nothing() : new Tint(source.getBuffer(RenderType.eyes(texture)), rgb[0] * ghost, rgb[1] * ghost, rgb[2] * ghost, 1, false);
                };
                int deathTime = entity.deathTime;
                int hurtTime = entity.hurtTime;
                entity.deathTime = 0;
                entity.hurtTime = 0;
                pose.pushPose();
                pose.translate(0, rise, 0);
                try {
                    renderer.render(entity, entity.getYRot(), partial, pose, glow, 0xF000F0);
                } finally {
                    pose.popPose();
                    entity.deathTime = deathTime;
                    entity.hurtTime = hurtTime;
                }
            }
        } catch (RuntimeException e) {
            // A renderer that can't be drawn this way: the ceremony goes on without its body.
        } finally {
            drawing = false;
        }
    }

    // Only the body and what it wears: no shadow, enchant glint, name tag or item atlas.
    private static ResourceLocation texture(RenderType type, ResourceLocation skin) {
        ResourceLocation texture = FxRenderTypes.textureOf(type);
        if (texture == null) {
            texture = skin;
        }
        String path = texture.getPath();
        if (!(path.startsWith("textures/") || path.startsWith("skins/")) || path.contains("shadow") || path.contains("glint")
                || path.contains("atlas")) {
            return null;
        }
        return texture;
    }

    // Fades in as it leaves the body, gone while the motes take the icon's shape.
    private static float ghostAlpha(SoulCeremony.Timing t, double age) {
        if (age < t.out()) {
            return 0;
        }
        float in = (float) Mth.clamp((age - t.out()) / 12.0, 0, 1);
        float out = 1 - (float) Mth.clamp((age - t.morph()) / Math.max(1, (t.shine() - t.morph()) * 0.6), 0, 1);
        return 0.85f * in * out * (0.85f + 0.15f * Mth.sin((float) age * 0.35f));
    }

    // Slowly up out of the body, easing to a stop.
    private static float ghostRise(SoulCeremony.Timing t, double age) {
        float k = (float) Mth.clamp((age - t.out()) / Math.max(1, t.morph() - t.out()), 0, 1);
        return 0.15f + 0.75f * (1 - (1 - k) * (1 - k));
    }

    // ---- The light: glow around the ghost, motes forming the icon, the shine, the item's glow and beam ----

    static void render(Vec3 camera, double now, float partial) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f right = new Vector3f(cam.getLeftVector()).negate();
        Vector3f up = new Vector3f(cam.getUpVector());
        for (Ceremony c : CEREMONIES.values()) {
            if (c.form.distanceToSqr(camera) > 128 * 128) {
                continue;
            }
            draw(c, camera, now - c.start, right, up, partial);
        }
        Minecraft minecraft = Minecraft.getInstance();
        for (Glow g : GLOWS) {
            Entity item = minecraft.level == null ? null : minecraft.level.getEntity(g.itemId);
            if (item == null || !item.isAlive()) {
                continue;
            }
            itemGlow(g, item.getPosition(partial).subtract(camera).toVector3f(), (float) (now - g.since));
        }
    }

    private static void draw(Ceremony c, Vec3 camera, double age, Vector3f right, Vector3f up, float partial) {
        SoulCeremony.Timing t = c.timing;
        float[] rgb = c.rgb;
        LivingEntity entity = c.entity;
        Vector3f feet = entity.getPosition(partial).subtract(camera).toVector3f();
        Vector3f form = c.form.subtract(camera).toVector3f();
        float height = entity.getBbHeight();
        int seed = entity.getId() * 131 + (int) c.start;

        // Around the ghost: a soft glow and wisps drifting up off it.
        float ghost = ghostAlpha(t, age);
        Vector3f ghostCenter = new Vector3f(feet).add(0, ghostRise(t, age) + height * 0.5f, 0);
        if (ghost > 0.01f) {
            FxDraw.billboard(ghostCenter, height * 1.9f, 0, FxDraw.SOFT, 0.15f, rgb, ghost * 0.32f, true);
        }
        if (age > t.out() && age < t.shine()) {
            for (int k = 0; k < 28; k++) {
                float life = 16 + 14 * FxWorld.hash(seed, k, 1);
                float s = (float) ((age - t.out() + life * FxWorld.hash(seed, k, 2)) % life);
                float a = FxWorld.hash(seed, k, 3) * Mth.TWO_PI;
                float r = 0.35f * Mth.sqrt(FxWorld.hash(seed, k, 4));
                Vector3f at = new Vector3f(feet).add(Mth.cos(a) * r, 0.2f + s * 0.07f + FxWorld.hash(seed, k, 5) * height * 0.8f, Mth.sin(a) * r);
                float k01 = s / life;
                float fadeOut = 1 - (float) Mth.clamp((age - t.morph()) / 10.0, 0, 1);
                FxDraw.billboard(at, 0.06f + 0.05f * FxWorld.hash(seed, k, 6), 0, FxDraw.SOFT, 0.85f, FxDraw.mix(WHITE, rgb, 0.6f),
                        (1 - k01) * Math.min(1, s / 2) * 0.8f * fadeOut, true);
            }
        }

        // The motes: from the ghost into the icon, held, shrunk to the item, gone as the item falls.
        Icon icon = c.icon;
        if (age >= t.morph() && age < t.drop() + 3) {
            float span = Math.max(1, t.shine() - t.morph());
            float shrink = (float) Mth.clamp((age - t.shine() - 2) / Math.max(1, t.drop() - t.shine() - 2), 0, 1);
            float size = Mth.lerp(shrink * shrink, ICON_SIZE, ITEM_SIZE);
            float gone = 1 - (float) Mth.clamp((age - t.drop()) / 3.0, 0, 1);
            float pulse = age > t.shine() ? 1 + 0.6f * (float) Math.max(0, 1 - (age - t.shine()) / 6.0) : 1;
            for (int i = 0; i < icon.x().length; i++) {
                float delay = FxWorld.hash(seed, i, 11) * span * 0.4f;
                float k = (float) Mth.clamp((age - t.morph() - delay) / (span * 0.6f), 0, 1);
                float e = k * k * (3 - 2 * k);
                Vector3f target = new Vector3f(form).add(new Vector3f(right).mul(icon.x()[i] * size)).add(new Vector3f(up).mul(icon.y()[i] * size));
                // Born somewhere in the ghost.
                Vector3f born = new Vector3f(ghostCenter).add((FxWorld.hash(seed, i, 12) - 0.5f) * 0.7f, (FxWorld.hash(seed, i, 13) - 0.5f) * height * 0.9f,
                        (FxWorld.hash(seed, i, 14) - 0.5f) * 0.7f);
                Vector3f at = new Vector3f(born).lerp(target, e);
                // A curl on the way.
                float swirl = Mth.sin(e * Mth.PI) * 0.45f;
                float a = FxWorld.hash(seed, i, 15) * Mth.TWO_PI + e * 4;
                at.add(Mth.cos(a) * swirl, Mth.sin(e * Mth.PI) * 0.25f, Mth.sin(a) * swirl);
                float[] color = FxDraw.mix(FxDraw.mix(WHITE, rgb, 0.7f), icon.color()[i], e);
                float opacity = Math.min(1, k * 4) * gone;
                if (k <= 0 || opacity <= 0.01f) {
                    continue;
                }
                float mote = icon.spacing() * size * (e < 1 ? 1.9f - 0.5f * e : 1.4f) * pulse;
                FxDraw.billboard(at, mote, 0, FxDraw.SOFT, 0.85f, color, opacity, true);
            }
            // Its light while it takes shape.
            float form01 = (float) Mth.clamp((age - t.morph()) / span, 0, 1);
            FxDraw.billboard(form, size * 2.4f, 0, FxDraw.SOFT, 0.15f, rgb, 0.3f * form01 * gone, true);
        }

        // Formed: one shine, a flare and a ring going out.
        if (age >= t.shine() && age < t.shine() + 14) {
            float s = (float) (age - t.shine());
            float k = s / 14;
            FxDraw.billboard(form, 2.8f * (1 - k * 0.5f), (float) age * 0.02f, FxDraw.FLARE, 4 / 32f, FxDraw.mix(WHITE, rgb, 0.35f), (1 - k) * 0.85f, true);
            FxDraw.billboard(form, 0.5f + 3.2f * FxWorld.easeOut(k), 0, FxDraw.RING, 0.08f, rgb, (1 - k) * 0.6f, true);
            FxDraw.billboard(form, 1.2f, 0, FxDraw.SOFT, 0.7f, WHITE, (1 - k) * 0.5f, true);
        }
    }

    private static void itemGlow(Glow g, Vector3f at, float age) {
        float in = Math.min(1, age / 10);
        float pulse = 0.8f + 0.2f * Mth.sin(age * 0.15f);
        Vector3f center = new Vector3f(at).add(0, 0.3f, 0);
        FxDraw.billboard(center, 1.3f, 0, FxDraw.SOFT, 0.2f, g.rgb, 0.32f * in * pulse, true);
        FxDraw.billboard(center, 0.45f, 0, FxDraw.SOFT, 0.7f, FxDraw.mix(WHITE, g.rgb, 0.5f), 0.25f * in * pulse, true);
        if (g.divine) {
            Vector3f top = new Vector3f(at).add(0, 9, 0);
            FxDraw.ribbon(new Vector3f(at).add(0, 0.1f, 0), top, 0.4f, FxDraw.BEAM, 0, g.rgb, 0.14f * in, true);
            FxDraw.ribbon(new Vector3f(at).add(0, 0.1f, 0), top, 0.12f, FxDraw.BEAM, 0, WHITE, 0.1f * in, true);
        }
        // Motes rising slowly around it.
        int seed = g.itemId * 17;
        for (int k = 0; k < 8; k++) {
            float life = 30 + 20 * FxWorld.hash(seed, k, 1);
            float s = (age + life * FxWorld.hash(seed, k, 2)) % life;
            float a = FxWorld.hash(seed, k, 3) * Mth.TWO_PI + s * 0.05f;
            float r = 0.25f + 0.2f * FxWorld.hash(seed, k, 4);
            Vector3f p = new Vector3f(at).add(Mth.cos(a) * r, 0.1f + s * 0.04f, Mth.sin(a) * r);
            float k01 = s / life;
            FxDraw.billboard(p, 0.05f, 0, FxDraw.SOFT, 0.85f, g.rgb, (1 - k01) * Math.min(1, s / 3) * 0.7f * in, true);
        }
    }

    // ---- The icon ----

    // The soul item's icon read pixel by pixel (every few pixels, so a 128 px icon gives a few hundred motes). Read once
    // per soul; a missing icon falls back to a plain crystal shape.
    private static Icon icon(String power) {
        return ICONS.computeIfAbsent(power, SoulCeremonyFx::readIcon);
    }

    private static Icon readIcon(String power) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/item/soul_" + power + ".png");
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(location); NativeImage image = NativeImage.read(in)) {
            int w = image.getWidth();
            int h = image.getHeight();
            int step = Math.max(1, Math.max(w, h) / 32);
            List<float[]> points = new ArrayList<>();
            for (int py = 0; py < h; py += step) {
                for (int px = 0; px < w; px += step) {
                    int abgr = image.getPixelRGBA(px, py);
                    if ((abgr >>> 24) < 100) {
                        continue;
                    }
                    float r = (abgr & 0xFF) / 255f;
                    float g = (abgr >> 8 & 0xFF) / 255f;
                    float b = (abgr >> 16 & 0xFF) / 255f;
                    points.add(new float[] { (px + step * 0.5f) / w - 0.5f, 0.5f - (py + step * 0.5f) / h, r, g, b });
                }
            }
            if (!points.isEmpty()) {
                return toIcon(points, step / (float) Math.max(w, h));
            }
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.warn("Soul ceremony: could not read {}", location);
        }
        return crystal();
    }

    // A white diamond.
    private static Icon crystal() {
        List<float[]> points = new ArrayList<>();
        int n = 24;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float x = (i + 0.5f) / n - 0.5f;
                float y = (j + 0.5f) / n - 0.5f;
                if (Math.abs(x) / 0.32f + Math.abs(y) / 0.48f <= 1) {
                    points.add(new float[] { x, y, 0.9f, 0.95f, 1f });
                }
            }
        }
        return toIcon(points, 1f / n);
    }

    private static Icon toIcon(List<float[]> points, float spacing) {
        // Too many: keep an even spread.
        int every = Math.max(1, (points.size() + MAX_MOTES - 1) / MAX_MOTES);
        int n = (points.size() + every - 1) / every;
        float[] x = new float[n];
        float[] y = new float[n];
        float[][] color = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] p = points.get(i * every);
            x[i] = p[0];
            y[i] = p[1];
            color[i] = new float[] { p[2], p[3], p[4] };
        }
        return new Icon(x, y, color, spacing * (float) Math.sqrt(every));
    }

    // Passes everything through with the color scaled, and optionally without the overlay (vanilla's red death tint).
    private record Tint(VertexConsumer out, float r, float g, float b, float a, boolean noOverlay) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            out.setColor((int) (red * r), (int) (green * g), (int) (blue * b), (int) (alpha * a));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (noOverlay) {
                out.setUv1(0, 10);
            } else {
                out.setUv1(u, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            out.setNormal(x, y, z);
            return this;
        }
    }

    // A new one each time it is asked for: an enchanted item in the hand joins its glint buffer with its own, and the
    // game refuses joining one buffer to itself ("Duplicate delegates").
    private static final class Nothing implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
