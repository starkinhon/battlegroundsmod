package com.starkinhon.battlegroundsmod.client.fx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.starkinhon.battlegroundsmod.power.powers.sub.Rukia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Rukia's ice over a body, the visualizer's "corpo fotonico de gelo" (iceBody): every box of the model gets a shell
// that freezes and thaws pixel by pixel of the skin, its front glowing white, crystals poking out of it, drops falling
// while it thaws, pixels flying off when it shatters. The shell is the entity's own model drawn again through the
// souls_ice shader with each box grown a little (see Grow). The Bankai keeps a shell on Rukia herself as armor:
// frozen whole, thawed down to the neck, frozen again at the end and thawed slowly (timeline in Rukia.BANKAI_*).
public final class IceShell {
    static final int SHATTER = 1;
    static final int THAW = 2;
    private static final float THICK = 0.05f;
    private static final float[] PALE = {0.84f, 0.95f, 1f};
    private static final float[] ICE_BLUE = FxDraw.rgb(0xBFE8FF);
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final float[] MIST = {0.9f, 0.96f, 1f};

    private static final class Body {
        final int id;
        final boolean armor;
        final int mode;
        final float grow;
        final float height;
        final Vec3 offset;
        final double born;
        double endAt = -1;
        int end;
        float progAtEnd;
        boolean flashed;

        Body(int id, boolean armor, int mode, float grow, float height, Vec3 offset, double born) {
            this.id = id;
            this.armor = armor;
            this.mode = mode;
            this.grow = grow;
            this.height = height;
            this.offset = offset;
            this.born = born;
        }
    }

    // What a body's ice looks like at one moment.
    private record Look(float prog, float shatter, float drip, float spikes) {
    }

    private static final Map<Integer, Body> BODIES = new HashMap<>();
    private static MultiBufferSource.BufferSource buffers;

    private IceShell() {
    }

    // ---------- from the server (through SubFx) ----------

    // id freezes from origin (mode 0 from the feet up, 1 out from the point), all over in `grow` ticks; height = how
    // far the ice reaches in blocks (0 = the body's own height).
    static void freeze(int id, Vec3 origin, int mode, float grow, float height) {
        double now = ScreenFx.now();
        Entity e = FxAnchor.entity(id);
        Vec3 offset = e == null ? Vec3.ZERO : origin.subtract(e.position());
        BODIES.put(id, new Body(id, false, mode, Math.max(1, grow), height, offset, now));
    }

    static void armor(int id) {
        BODIES.put(id, new Body(id, true, 0, 1, 0, Vec3.ZERO, ScreenFx.now()));
    }

    static void end(int id, int style) {
        Body b = BODIES.get(id);
        if (b == null || b.endAt >= 0) {
            return;
        }
        double now = ScreenFx.now();
        b.progAtEnd = look(b, (float) (now - b.born)).prog();
        b.endAt = now;
        b.end = style;
    }

    static boolean active() {
        return !BODIES.isEmpty();
    }

    static void clear() {
        BODIES.clear();
    }

    // ---------- the program over time ----------

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static Look look(Body b, float age) {
        if (b.endAt >= 0) {
            float e = Math.max(0, age - (float) (b.endAt - b.born));
            if (b.end == SHATTER) {
                return new Look(b.progAtEnd, smooth(e / 10), 0, 1);
            }
            float thaw = b.armor ? 30 : 40;
            return new Look(b.progAtEnd * (1 - smooth(e / thaw)), 0, 1, 1);
        }
        if (!b.armor) {
            return new Look(smooth(age / b.grow), 0, 0, 1);
        }
        float x = Rukia.BANKAI_X;
        float m = Rukia.BANKAI_M;
        float arm = Rukia.BANKAI_ARMOR;
        if (age < x) {
            return new Look(smooth((age - 10) / (x - 14)), 0, 0, 0.5f);
        }
        if (age < m) {
            return new Look(1 - (1 - arm) * smooth((age - x - 2) / 26), 0, age < x + 34 ? 1 : 0, 0.6f);
        }
        if (age < m + 16) {
            return new Look(arm + (1 - arm) * smooth((age - m) / 14), 0, 0, 0.6f);
        }
        return new Look(1 - smooth((age - m - 18) / 50), 0, 1, 0.5f);
    }

    private static boolean done(Body b, double now) {
        float age = (float) (now - b.born);
        if (b.endAt >= 0) {
            return now - b.endAt > (b.end == SHATTER ? 12 : b.armor ? 32 : 42);
        }
        return b.armor && age > Rukia.BANKAI_M + 70;
    }

    private static Vec3 origin(Body b, LivingEntity e, Vec3 feet) {
        return b.mode == 0 ? feet : feet.add(b.offset);
    }

    private static float reach(Body b, LivingEntity e) {
        return b.height > 0 ? b.height * e.getBbHeight() / 1.8f : e.getBbHeight();
    }

    // ---------- the shell itself (after the entities) ----------

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || BODIES.isEmpty() || FxShaders.ice == null
                || FxShaders.flat()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            BODIES.clear();
            return;
        }
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = ScreenFx.now();
        ShaderInstance shader = FxShaders.ice;
        if (buffers == null) {
            buffers = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            for (Body b : BODIES.values()) {
                if (!(FxAnchor.entity(b.id) instanceof LivingEntity e) || e.isInvisible() || FxAnchor.firstPersonSelf(b.id)) {
                    continue;
                }
                Look l = look(b, (float) (now - b.born) + partial);
                if (l.prog() <= 0.002f || l.shatter() >= 0.999f) {
                    continue;
                }
                Vec3 feet = e.getPosition(partial);
                if (feet.distanceToSqr(camera) > 128 * 128) {
                    continue;
                }
                Vec3 o = origin(b, e, feet).subtract(camera);
                shader.safeGetUniform("IceOrigin").set((float) o.x, (float) o.y, (float) o.z);
                shader.safeGetUniform("IceMode").set((float) b.mode);
                shader.safeGetUniform("IceHeight").set(reach(b, e));
                shader.safeGetUniform("Progress").set(l.prog());
                shader.safeGetUniform("Shatter").set(l.shatter());
                shader.safeGetUniform("Seed").set((b.id % 17) * 0.37f);
                draw(dispatcher, e, feet.subtract(camera), partial);
                buffers.endBatch();
            }
        } finally {
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void draw(EntityRenderDispatcher dispatcher, LivingEntity entity, Vec3 rel, float partial) {
        MultiBufferSource ice = type -> {
            ResourceLocation texture = FxRenderTypes.textureOf(type);
            if (texture == null) {
                return new Nothing();
            }
            String path = texture.getPath();
            // Only the body and what it wears: no shadow, fire, enchant glint, held items or name tag.
            if (!(path.startsWith("textures/") || path.startsWith("skins/")) || path.contains("shadow") || path.contains("glint")
                    || path.contains("atlas")) {
                return new Nothing();
            }
            return new Grow(buffers.getBuffer(FxRenderTypes.iceShell(texture)));
        };
        int hurtTime = entity.hurtTime;
        entity.hurtTime = 0;
        try {
            dispatcher.render(entity, rel.x, rel.y, rel.z, Mth.lerp(partial, entity.yRotO, entity.getYRot()), partial, new PoseStack(), ice,
                    0xF000F0);
        } catch (RuntimeException e) {
            // A renderer that can't be drawn this way just gets no shell (the glints and crystals still show).
        } finally {
            entity.hurtTime = hurtTime;
        }
    }

    // Grows every face of the model's boxes by THICK: pushed out along its normal and widened in its own plane, so the
    // faces of each box meet again as a slightly bigger box (a shell over each part, not a blob over the body).
    private static final class Grow implements VertexConsumer {
        private final VertexConsumer out;
        private final float[][] v = new float[4][];
        private final int[][] iv = new int[4][];
        private int n;

        Grow(VertexConsumer out) {
            this.out = out;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float vv, int overlay, int light, float nx, float ny, float nz) {
            v[n] = new float[] {x, y, z, u, vv, nx, ny, nz};
            iv[n] = new int[] {color, overlay, light};
            if (++n < 4) {
                return;
            }
            n = 0;
            float cx = (v[0][0] + v[1][0] + v[2][0] + v[3][0]) / 4;
            float cy = (v[0][1] + v[1][1] + v[2][1] + v[3][1]) / 4;
            float cz = (v[0][2] + v[1][2] + v[2][2] + v[3][2]) / 4;
            Vector3f a = new Vector3f(v[1][0] - v[0][0], v[1][1] - v[0][1], v[1][2] - v[0][2]);
            Vector3f b = new Vector3f(v[3][0] - v[0][0], v[3][1] - v[0][1], v[3][2] - v[0][2]);
            if (a.lengthSquared() > 1.0E-10f) {
                a.normalize();
            }
            if (b.lengthSquared() > 1.0E-10f) {
                b.normalize();
            }
            for (int i = 0; i < 4; i++) {
                float[] p = v[i];
                Vector3f d = new Vector3f(p[0] - cx, p[1] - cy, p[2] - cz);
                float sa = Math.signum(d.dot(a)) * THICK;
                float sb = Math.signum(d.dot(b)) * THICK;
                float gx = p[0] + a.x * sa + b.x * sb + p[5] * THICK;
                float gy = p[1] + a.y * sa + b.y * sb + p[6] * THICK;
                float gz = p[2] + a.z * sa + b.z * sb + p[7] * THICK;
                out.addVertex(gx, gy, gz, iv[i][0], p[3], p[4], iv[i][1], iv[i][2], p[5], p[6], p[7]);
            }
        }

        // Anything drawn vertex by vertex (not a model box) goes through as it is.
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return out.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return out.setColor(r, g, b, a);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return out.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return out.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return out.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return out.setNormal(x, y, z);
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

    // ---------- what goes with it (drawn with the other effects, from SubFx) ----------

    static void prune(double now) {
        Iterator<Body> it = BODIES.values().iterator();
        while (it.hasNext()) {
            Body b = it.next();
            if (done(b, now) || FxAnchor.entity(b.id) == null && now - b.born > 2) {
                it.remove();
            }
        }
    }

    // glow: the light (front glints, drops' glow, flying pixels, the Bankai's ribbons and mist column);
    // else: what has a body (crystals poking out, the crown, mist, and the flat box shell when the shader is off).
    static void draw(Vec3 camera, double now, float partial, boolean glow) {
        Minecraft mc = Minecraft.getInstance();
        for (Body b : BODIES.values()) {
            if (!(FxAnchor.entity(b.id) instanceof LivingEntity e)) {
                continue;
            }
            float age = (float) (now - b.born) + partial;
            Look l = look(b, age);
            boolean self = FxAnchor.firstPersonSelf(b.id);
            Vec3 feet = e.getPosition(partial);
            if (b.armor) {
                armorExtras(b, e, feet, camera, age, partial, glow, self);
                if (glow && !b.flashed && age >= Rukia.BANKAI_X && b.endAt < 0) {
                    b.flashed = true;
                    if (mc.player != null && mc.player.position().distanceTo(feet) < 24) {
                        ScreenFx.flash(0xFFFFFF, 0.7f, 12);
                        ScreenFx.vignette(0x0A1A3A, 0.5f, Rukia.BANKAI_M - Rukia.BANKAI_X);
                    }
                }
            }
            if (self) {
                continue;
            }
            if (!glow && (FxShaders.flat() || FxShaders.ice == null)) {
                AABB box = e.getBoundingBox().move(feet.subtract(e.position())).inflate(0.07);
                SubFx.iceBox(box, camera, l.shatter() > 0 ? -1 : l.prog(), 1 - l.shatter(), b.id);
            }
            surface(b, e, feet, camera, age, l, glow);
            if (glow && l.shatter() > 0) {
                Vector3f c = rel(feet.add(0, e.getBbHeight() * 0.5, 0), camera);
                for (int i = 0; i < 30; i++) {
                    Vector3f dir = new Vector3f(hash(i, 91) - 0.5f, hash(i, 92) * 0.8f, hash(i, 93) - 0.5f).normalize();
                    Vector3f q = new Vector3f(c).add(new Vector3f(dir).mul(0.3f + 2.8f * l.shatter())).add(0, -2 * l.shatter() * l.shatter(), 0);
                    FxDraw.billboard(q, 0.25f + 0.25f * hash(i, 94), i + l.shatter() * 6, SubFx.SHARD, hash(i, 95) * 0.49f, ICE_BLUE,
                            1 - l.shatter(), true);
                }
                if (l.shatter() < 0.4f) {
                    FxDraw.billboard(c, 2.5f, 0, FxDraw.FLARE, 10 / 32f, WHITE, 0.8f * (1 - l.shatter() / 0.4f), true);
                }
            }
        }
    }

    // Points on the body's surface (a box the shape of a person: wide at the arms, thin front to back), turned with it.
    private static void surface(Body b, LivingEntity e, Vec3 feet, Vec3 camera, float age, Look l, boolean glow) {
        float yaw = Mth.lerp(0.5f, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        float w = e.getBbWidth();
        float hw = w * 0.75f + THICK;
        float hd = w * 0.22f + THICK;
        float h = e.getBbHeight();
        float reach = reach(b, e);
        Vec3 o = origin(b, e, feet);
        float front = l.prog() * 1.12f - 0.05f;
        for (int i = 0; i < 36; i++) {
            int side = (int) (hash(i + b.id * 7, 1) * 5);
            float u = hash(i + b.id * 7, 2) * 2 - 1;
            float y = hash(i + b.id * 7, 3) * h;
            float r = hash(i + b.id * 7, 4);
            Vec3 at;
            Vec3 nrm;
            switch (side) {
                case 0 -> {
                    at = feet.add(fwd.scale(hd)).add(right.scale(u * hw)).add(0, y, 0);
                    nrm = fwd;
                }
                case 1 -> {
                    at = feet.add(fwd.scale(-hd)).add(right.scale(u * hw)).add(0, y, 0);
                    nrm = fwd.scale(-1);
                }
                case 2 -> {
                    at = feet.add(right.scale(hw)).add(fwd.scale(u * hd)).add(0, y * 0.8, 0);
                    nrm = right;
                }
                case 3 -> {
                    at = feet.add(right.scale(-hw)).add(fwd.scale(u * hd)).add(0, y * 0.8, 0);
                    nrm = right.scale(-1);
                }
                default -> {
                    at = feet.add(right.scale(u * w * 0.3)).add(fwd.scale((r - 0.5) * hd)).add(0, h + THICK, 0);
                    nrm = new Vec3(0, 1, 0);
                }
            }
            float g = (float) (b.mode == 0 ? (at.y - o.y) / reach : at.distanceTo(o) / reach);
            if (glow) {
                float near = 1 - Math.abs(g - front) / 0.06f;
                if (near > 0 && l.shatter() <= 0 && l.drip() <= 0) {
                    Vector3f p = rel(at.add(nrm.scale(0.03)), camera);
                    FxDraw.billboard(p, 0.09f + 0.08f * r, 0, FxDraw.ORB, 0.9f, WHITE, near * 0.9f, true);
                    if (r > 0.8f) {
                        FxDraw.billboard(rel(at.add(nrm.scale(0.05)), camera), 0.35f, age * 0.05f + i, FxDraw.FLARE, 4 / 32f, PALE, near * 0.6f, true);
                    }
                }
                // Thawing: drops and bits of ice falling from where the ice just went.
                if (l.drip() > 0 && g > front && g < front + 0.25f) {
                    float ph = fract(age * (0.04f + 0.03f * r) + r * 7);
                    float a = l.drip() * (1 - (g - front) / 0.25f) * (1 - ph);
                    Vec3 q = at.add(nrm.scale(0.04 + ph * 0.1)).add(0, -ph * ph * 0.9, 0);
                    FxDraw.billboard(rel(q, camera), 0.14f, 0, FxDraw.ORB, 0.6f, ICE_BLUE, a * 0.5f, true);
                }
            } else {
                if (l.drip() > 0 && g > front && g < front + 0.25f) {
                    float ph = fract(age * (0.04f + 0.03f * r) + r * 7);
                    float a = l.drip() * (1 - (g - front) / 0.25f) * (1 - ph);
                    Vec3 q = at.add(nrm.scale(0.04 + ph * 0.1)).add(0, -ph * ph * 0.9, 0);
                    FxDraw.billboard(rel(q, camera), 0.05f, 0, FxDraw.SOLID, 0, r > 0.5f ? PALE : ICE_BLUE, a, false);
                }
                // Crystals growing out of the frozen parts.
                if (l.spikes() > 0 && r > 0.55f && g < front - 0.02f) {
                    float k = smooth((front - g) / 0.12f) * (1 - l.shatter());
                    Vec3 dir = nrm.add(0, 0.5, 0).normalize();
                    SubFx.iceSpike(rel(at, camera), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z),
                            (0.12f + 0.3f * (r - 0.55f) / 0.45f) * k * l.spikes(), 0.045f, i, 1);
                }
            }
        }
    }

    // The Bankai: the white mist rising round her, the crown of ice and the two long ribbons while it lasts.
    private static void armorExtras(Body b, LivingEntity e, Vec3 feet, Vec3 camera, float age, float partial, boolean glow, boolean self) {
        float x = Rukia.BANKAI_X;
        float m = Rukia.BANKAI_M;
        float out = b.endAt < 0 ? 1 : 1 - smooth((float) (ScreenFx.now() - b.endAt + partial) / 20);
        float col = smooth(age / 26) * (1 - smooth((age - x - 14) / 26)) * out;
        if (col > 0) {
            for (int i = 0; i < 26; i++) {
                float y = fract(age * 0.01f + hash(i, 141)) * 6;
                float a = hash(i, 142) * Mth.TWO_PI + age * 0.02f;
                float r = 0.8f + hash(i, 143) * 0.8f;
                Vector3f p = rel(feet.add(Mth.cos(a) * r, y, Mth.sin(a) * r), camera);
                if (self && p.lengthSquared() < 2.5f) {
                    continue;
                }
                if (glow) {
                    FxDraw.billboard(p, 1.1f, i, FxDraw.SOFT, 0.1f, MIST, 0.12f * col * (1 - y / 6), true);
                } else {
                    FxDraw.billboard(p, 1.6f, i, FxDraw.SMOKE, 0.25f, MIST, 0.5f * col * (1 - y / 6), false);
                }
            }
            if (glow && !self) {
                FxDraw.ribbon(rel(feet, camera), rel(feet.add(0, 7, 0), camera), 3, FxDraw.BEAM, 0.3f, WHITE, 0.35f * col, true);
            }
        }
        boolean formed = age >= x && age < m + 16 && b.endAt < 0;
        if (!formed || self) {
            return;
        }
        float in = smooth((age - x) / 6) * (1 - smooth((age - m) / 16));
        float yaw = Mth.lerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        float h = e.getBbHeight();
        if (glow) {
            // Two long ribbons of ice floating out behind her.
            Vec3 center = feet.add(0, h * 0.5, 0);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 last = center.add(right.scale(s * 0.3)).add(0, 0.4 * h / 1.8, 0).add(fwd.scale(-0.2));
                for (int k = 1; k <= 12; k++) {
                    float u = k / 12f;
                    Vec3 p = center.add(right.scale(s * (0.4 + u * 1.2))).add(0, 0.6 + Mth.sin(u * 3 + age * 0.05f) * 0.8 + u * 0.8, 0)
                            .add(fwd.scale(-0.3 - u * 0.5));
                    FxDraw.ribbon(rel(last, camera), rel(p, camera), 0.06f, FxDraw.BEAM, 0.9f, PALE, 0.9f * in, true);
                    last = p;
                }
            }
            return;
        }
        // The crown: five little spikes of ice on her head, turned with it.
        float head = Mth.lerp(partial, e.yHeadRotO, e.yHeadRot) * Mth.DEG_TO_RAD;
        Vec3 hr = new Vec3(Mth.cos(head), 0, Mth.sin(head));
        Vec3 hf = new Vec3(-Mth.sin(head), 0, Mth.cos(head));
        Vec3 top = feet.add(0, e.getEyeHeight() + 0.27 * h / 1.8, 0).add(hf.scale(-0.06));
        for (int k = 0; k < 5; k++) {
            float lean = (k - 2) * 0.2f;
            Vec3 base = top.add(hr.scale((k - 2) * 0.09));
            Vec3 dir = new Vec3(0, 1, 0).add(hr.scale(-Mth.sin(lean))).normalize();
            SubFx.iceSpike(rel(base, camera), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z), (k == 2 ? 0.3f : 0.2f) * in, 0.04f,
                    k, 1);
        }
    }

    private static Vector3f rel(Vec3 world, Vec3 camera) {
        return new Vector3f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z));
    }

    private static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }
}
