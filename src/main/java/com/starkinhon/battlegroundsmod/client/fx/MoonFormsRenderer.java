package com.starkinhon.battlegroundsmod.client.fx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.power.powers.kokushibo.MoonForms;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Kokushibo's Moon Breathing forms 2, 3, 5 and 6, drawn exactly as the 3D pages Stark approved
// (forja/kokushibo-formas/moon3d.html): every frame the pages' script (power/powers/kokushibo/MoonForms, the same on the
// server, so the hits and the carving match what is seen) says where each cut, moon and sparkle is, and this lays them
// out in the world, the cuts as strips and the moons and sparkles as billboards, through the pages' own shader
// (souls_moon3d). Back to front, see-through, no bloom: the same on a bright sky, at night or in a cave. The awakened
// forms (7th, 8th, 9th, 10th, 14th, 16th: the stretched sword) are drawn the same way in their own colours.
public final class MoonFormsRenderer {
    private static final int MAX = 6;
    private static final double IMPACT_VIEW = 48;

    private record Cast(MoonForms.Form form, MoonForms.Basis basis, double born) {
    }

    private static final List<Cast> CASTS = new ArrayList<>();
    // The 16th Form's targets, told just before the form itself: seed -> entity ids, in order.
    private static final java.util.Map<Integer, List<Integer>> TARGETS = new java.util.HashMap<>();

    private MoonFormsRenderer() {
    }

    // One of the 16th Form's targets (index: its place in the order the server picked them).
    public static void target(int seed, int index, int entityId) {
        List<Integer> ids = TARGETS.computeIfAbsent(seed, k -> new ArrayList<>());
        while (ids.size() <= index) {
            ids.add(-1);
        }
        ids.set(index, entityId);
        while (TARGETS.size() > 8) {
            TARGETS.remove(TARGETS.keySet().iterator().next());
        }
    }

    // form: + 100 at the full moon (more moons left hanging, bigger and for longer).
    public static void receive(int code, Vec3 feet, Vec3 look, int seed) {
        boolean full = code >= 100;
        int form = code % 100;
        MoonForms.Basis basis = MoonForms.basis(form, feet, look);
        MoonForms.Form shape;
        if (form == 16) {
            // homing: the cuts follow the very entities the server is cutting
            List<Integer> ids = TARGETS.remove(seed);
            List<Integer> list = ids == null ? List.of() : ids;
            net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
            double[][] start = new double[list.size()][];
            net.minecraft.world.entity.Entity[] ents = new net.minecraft.world.entity.Entity[list.size()];
            for (int i = 0; i < list.size(); i++) {
                ents[i] = level == null ? null : level.getEntity(list.get(i));
                start[i] = ents[i] != null ? basis.local(ents[i].position()) : new double[] {0, 0, 20};
            }
            shape = MoonForms.create16(seed, full, start, i -> ents[i] != null && ents[i].isAlive() ? basis.local(ents[i].position()) : null);
        } else {
            shape = MoonForms.create(form, seed, full);
        }
        CASTS.add(new Cast(shape, basis, ScreenFx.now()));
        while (CASTS.size() > MAX) {
            CASTS.remove(0);
        }
        // The pages open with a flash and a shake (stronger for the bigger forms).
        Minecraft mc = Minecraft.getInstance();
        boolean awakened = MoonForms.awakened(form);
        if (mc.player != null && mc.player.position().distanceTo(feet) < IMPACT_VIEW * (awakened ? 2 : 1)) {
            float shake = awakened ? 1.1f : form == 6 ? 1.0f : form == 3 ? 0.9f : 0.7f;
            ScreenFx.flash(awakened ? 0xD8C0FF : 0xFFE2A0, 0.18f, 5);
            ScreenFx.radialBlur(feet.add(0, 1.5, 0), 0.3f, 8);
            com.starkinhon.battlegroundsmod.client.ScreenShake.start(shake, 12);
        }
    }

    public static boolean active() {
        return !CASTS.isEmpty();
    }

    public static void clear() {
        CASTS.clear();
    }

    public static void render(MultiBufferSource source, Vec3 camera, double now, float partial) {
        CASTS.removeIf(c -> (now - c.born()) / 20.0 > c.form().end);
        if (CASTS.isEmpty() || FxShaders.moon3d == null) {
            return;
        }
        Camera view = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = new Vector3f(view.getLeftVector()), up = new Vector3f(view.getUpVector());
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z()), upv = new Vec3(up.x(), up.y(), up.z());
        VertexConsumer out = source.getBuffer(Types.BODY);
        for (Cast c : CASTS) {
            double t = Math.max(0, (now - c.born()) / 20.0);
            MoonForms.Frame fr = c.form().frame(t);
            MoonForms.Basis b = c.basis();
            // the awakened colours are the same kinds of piece, three further up (see souls_moon3d)
            int pal = c.form().pal ? 3 * 4096 : 0;
            for (MoonForms.Ribbon rb : fr.ribbons()) {
                ribbon(out, b, rb, camera, pal);
            }
            for (MoonForms.Moon m : fr.moons()) {
                Vec3 p = b.world(m.p()).subtract(camera);
                double h = m.r() * 1.3;
                double rot = m.rot() % (Math.PI * 2);
                if (rot < 0) {
                    rot += Math.PI * 2;
                }
                int d = pal + 4096 + (m.idx() & 4095), e = (int) Math.round(rot * 1000);
                float cr = (float) m.thin(), cg = (float) m.a(), cb = (float) m.diss(), ca = (float) ((m.cut() - 0.9) / 0.2);
                billboard(out, p, right, upv, h, 1.3f, cr, cg, cb, ca, d, e);
            }
            for (MoonForms.Spark s : fr.sparks()) {
                Vec3 p = b.world(s.p()).subtract(camera);
                billboard(out, p, right, upv, s.z() * 4, 4f, (float) (s.a() / 1.5), 0, 0, 0, pal + 8192, 0);
            }
        }
    }

    private static void ribbon(VertexConsumer out, MoonForms.Basis b, MoonForms.Ribbon rb, Vec3 camera, int pal) {
        int n = rb.p.length - 1;
        double w = rb.hw * 2.2;
        int d = pal + ((int) Math.round(rb.seed * 100) & 4095), e = (int) Math.min(32000, Math.round(rb.len * 100));
        float cr = (float) (rb.head / 1.2), cg = (float) rb.tail, cb = (float) rb.bandA, ca = rb.shape / 3f;
        Vec3 prevP = b.world(rb.p[0]).subtract(camera), prevA = b.dir(rb.a[0]).scale(w);
        for (int i = 0; i < n; i++) {
            Vec3 p = b.world(rb.p[i + 1]).subtract(camera), a = b.dir(rb.a[i + 1]).scale(w);
            float s0 = i / (float) n, s1 = (i + 1) / (float) n;
            vertex(out, prevP.subtract(prevA), s0, -2.2f, cr, cg, cb, ca, d, e);
            vertex(out, p.subtract(a), s1, -2.2f, cr, cg, cb, ca, d, e);
            vertex(out, p.add(a), s1, 2.2f, cr, cg, cb, ca, d, e);
            vertex(out, prevP.add(prevA), s0, 2.2f, cr, cg, cb, ca, d, e);
            prevP = p;
            prevA = a;
        }
    }

    private static void billboard(VertexConsumer out, Vec3 p, Vec3 right, Vec3 up, double h, float uvh, float cr, float cg, float cb, float ca, int d,
            int e) {
        Vec3 r = right.scale(h), u = up.scale(h);
        vertex(out, p.subtract(r).subtract(u), -uvh, -uvh, cr, cg, cb, ca, d, e);
        vertex(out, p.add(r).subtract(u), uvh, -uvh, cr, cg, cb, ca, d, e);
        vertex(out, p.add(r).add(u), uvh, uvh, cr, cg, cb, ca, d, e);
        vertex(out, p.subtract(r).add(u), -uvh, uvh, cr, cg, cb, ca, d, e);
    }

    private static void vertex(VertexConsumer out, Vec3 p, float u, float v, float r, float g, float b, float a, int d, int e) {
        out.addVertex((float) p.x, (float) p.y, (float) p.z).setUv(u, v)
                .setColor(Mth.clamp(r, 0f, 1f), Mth.clamp(g, 0f, 1f), Mth.clamp(b, 0f, 1f), Mth.clamp(a, 0f, 1f)).setUv2(d, e);
    }

    // souls_moon3d, premultiplied see-through, sorted back to front (sortOnUpload), behind blocks (depth test, no write).
    static final class Types extends RenderType {
        private static final ShaderStateShard SHADER = new ShaderStateShard(() -> FxShaders.moon3d);
        private static final TransparencyStateShard PREMULTIPLIED = new TransparencyStateShard("battlegroundsmod_moon3d_premultiplied", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });
        static final RenderType BODY = create("battlegroundsmod_moon3d", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 1 << 18, false, true,
                CompositeState.builder()
                        .setShaderState(SHADER)
                        .setTransparencyState(PREMULTIPLIED)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
