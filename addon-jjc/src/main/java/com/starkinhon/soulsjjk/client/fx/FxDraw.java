package com.starkinhon.soulsjjk.client.fx;

import com.starkinhon.soulsjjk.compat.VX;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;

// Writes souls_vfx quads. Positions are relative to the camera. With a shader pack on, the same calls draw with
// the flat fallback (a soft dot), which ignores the shape.
final class FxDraw {
    static final int SOFT = 0;
    static final int RING = 1;
    static final int STREAK = 2;
    static final int BEAM = 3;
    static final int FIRE = 4;
    static final int SMOKE = 5;
    static final int FLARE = 6;
    static final int SOLID = 7;
    static final int DISC = 8;
    static final int SUN = 9;
    // Infinite Void's black hole (DomainScene): photon ring and swirling rings around the hole; param = the hole's size.
    static final int BLACK_HOLE = 10;
    // An ink splash with tendrils and droplets; param = which one.
    static final int SPLAT = 11;
    // A soft glow (like SOFT) that is never faded near surfaces: for something sitting right on a body.
    static final int ORB = 16;
    // Aizen's cartoon reiatsu (souls_vfx.fsh shape 17).
    static final int AURA = 17;
    // Gojo's Blue (souls_vfx.fsh shape 18); param = AzulRenderer.param(style, charge).
    static final int AZUL = 18;
    // Gojo's Red (souls_vfx.fsh shapes 19 and 20): one tail (a strip, u along it) and the orb.
    static final int VRABO = 19;
    static final int VORBE = 20;
    // Gojo's Purple (souls_vfx.fsh shapes 21 to 24): the two spheres mixing into it (color.g = the mix, color.b >= 0.95 =
    // no contour), the twisted space round it in flight, the light leaking out of it, one living bolt (a strip).
    static final int ROXO = 21;
    static final int ROXO_VORTEX = 22;
    static final int ROXO_RAYS = 23;
    static final int ROXO_RAIO = 24;

    private static VertexConsumer additive;
    private static VertexConsumer alpha;
    private static final Vector3f LEFT = new Vector3f();
    private static final Vector3f UP = new Vector3f();
    private static final Vector3f LOOK = new Vector3f();

    private FxDraw() {
    }

    static void begin(MultiBufferSource buffers, Camera camera, boolean flat) {
        additive = buffers.getBuffer(flat ? FxRenderTypes.FLAT_ADD : FxRenderTypes.VFX_ADD);
        alpha = buffers.getBuffer(flat ? FxRenderTypes.FLAT_ALPHA : FxRenderTypes.VFX_ALPHA);
        LEFT.set(camera.getLeftVector());
        UP.set(camera.getUpVector());
        LOOK.set(camera.getLookVector());
    }

    // A quad with uv (0,0) at a, (1,0) at b, (1,1) at c and (0,1) at d.
    static void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int shape, float param, float[] rgb, float opacity, boolean glow) {
        quad(glow ? additive : alpha, a, b, c, d, shape, param, rgb, opacity);
    }

    static void quad(VertexConsumer out, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int shape, float param, float[] rgb, float opacity) {
        if (opacity <= 0.002f) {
            return;
        }
        int p = Math.round(Math.max(0, Math.min(1, param)) * 255);
        float o = Math.min(1, opacity);
        vertex(out, a, 0, 0, shape, p, rgb, o);
        vertex(out, b, 1, 0, shape, p, rgb, o);
        vertex(out, c, 1, 1, shape, p, rgb, o);
        vertex(out, d, 0, 1, shape, p, rgb, o);
    }

    // Same, with its own opacity at each corner (a curtain that fades where it turns edge-on to the camera).
    static void quadFade(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int shape, float param, float[] rgb, float oa, float ob, float oc,
            float od, boolean glow) {
        if (Math.max(Math.max(oa, ob), Math.max(oc, od)) <= 0.002f) {
            return;
        }
        VertexConsumer out = glow ? additive : alpha;
        int p = Math.round(Math.max(0, Math.min(1, param)) * 255);
        vertex(out, a, 0, 0, shape, p, rgb, Math.min(1, oa));
        vertex(out, b, 1, 0, shape, p, rgb, Math.min(1, ob));
        vertex(out, c, 1, 1, shape, p, rgb, Math.min(1, oc));
        vertex(out, d, 0, 1, shape, p, rgb, Math.min(1, od));
    }

    // Same, with its own color at each corner (smooth shading across a mesh).
    static void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, int shape, float param, float[] ca, float[] cb, float[] cc, float[] cd,
            float opacity, boolean glow) {
        if (opacity <= 0.002f) {
            return;
        }
        VertexConsumer out = glow ? additive : alpha;
        int p = Math.round(Math.max(0, Math.min(1, param)) * 255);
        float o = Math.min(1, opacity);
        vertex(out, a, 0, 0, shape, p, ca, o);
        vertex(out, b, 1, 0, shape, p, cb, o);
        vertex(out, c, 1, 1, shape, p, cc, o);
        vertex(out, d, 0, 1, shape, p, cd, o);
    }

    private static void vertex(VertexConsumer out, Vector3f pos, float u, float v, int shape, int param, float[] rgb, float opacity) {
        VX.v(out, pos.x, pos.y, pos.z).uv(u, v).color(rgb[0], rgb[1], rgb[2], opacity).uv2(shape, param).endVertex();
    }

    // Square facing the camera, turned by spin radians.
    static void billboard(Vector3f center, float size, float spin, int shape, float param, float[] rgb, float opacity, boolean glow) {
        float half = size / 2;
        float cos = (float) Math.cos(spin) * half, sin = (float) Math.sin(spin) * half;
        Vector3f x = new Vector3f(LEFT).mul(cos).add(new Vector3f(UP).mul(sin));
        Vector3f y = new Vector3f(UP).mul(cos).sub(new Vector3f(LEFT).mul(sin));
        quad(new Vector3f(center).sub(x).sub(y), new Vector3f(center).add(x).sub(y), new Vector3f(center).add(x).add(y),
                new Vector3f(center).sub(x).add(y), shape, param, rgb, opacity, glow);
    }

    // A strip from one point to another, turned to face the camera: u runs along it, v across.
    static void ribbon(Vector3f from, Vector3f to, float width, int shape, float param, float[] rgb, float opacity, boolean glow) {
        Vector3f along = new Vector3f(to).sub(from);
        if (along.lengthSquared() < 1.0E-6f) {
            return;
        }
        Vector3f mid = new Vector3f(from).add(to).mul(0.5f);
        Vector3f side = new Vector3f(along).cross(mid);
        if (side.lengthSquared() < 1.0E-8f) {
            side.set(LEFT);
        }
        side.normalize(width / 2);
        quad(new Vector3f(from).sub(side), new Vector3f(to).sub(side), new Vector3f(to).add(side), new Vector3f(from).add(side),
                shape, param, rgb, opacity, glow);
    }

    // One continuous strip through several points, turned to face the camera, with u running along it (us[i] at
    // pts[i]) and v across (the Visualizer's strip).
    static void strip(Vector3f[] pts, float[] widths, float[] us, int shape, float param, float[] rgb, float opacity, boolean glow) {
        if (opacity <= 0.002f || pts.length < 2) {
            return;
        }
        VertexConsumer out = glow ? additive : alpha;
        int p = Math.round(Math.max(0, Math.min(1, param)) * 255);
        float o = Math.min(1, opacity);
        Vector3f[] sides = new Vector3f[pts.length];
        for (int i = 0; i < pts.length; i++) {
            Vector3f a = pts[Math.max(0, i - 1)];
            Vector3f b = pts[Math.min(pts.length - 1, i + 1)];
            // Positions are relative to the camera, so the point itself is the view direction.
            Vector3f side = new Vector3f(b).sub(a).cross(pts[i]);
            sides[i] = side.lengthSquared() < 1.0E-12f ? new Vector3f() : side.normalize(widths[i] / 2);
        }
        for (int i = 0; i < pts.length - 1; i++) {
            vertex(out, new Vector3f(pts[i]).sub(sides[i]), us[i], 0, shape, p, rgb, o);
            vertex(out, new Vector3f(pts[i + 1]).sub(sides[i + 1]), us[i + 1], 0, shape, p, rgb, o);
            vertex(out, new Vector3f(pts[i + 1]).add(sides[i + 1]), us[i + 1], 1, shape, p, rgb, o);
            vertex(out, new Vector3f(pts[i]).add(sides[i]), us[i], 1, shape, p, rgb, o);
        }
    }

    // Square lying across normal (a ring on the ground has normal straight up).
    static void disk(Vector3f center, Vector3f normal, float size, int shape, float param, float[] rgb, float opacity, boolean glow) {
        disk(glow ? additive : alpha, center, normal, size, 0, shape, param, rgb, opacity);
    }

    // Same, turned by spin radians around its normal, into any buffer (textured decals use their own).
    static void disk(VertexConsumer out, Vector3f center, Vector3f normal, float size, float spin, int shape, float param, float[] rgb, float opacity) {
        Vector3f n = new Vector3f(normal).normalize();
        Vector3f x0 = new Vector3f(n).cross(Math.abs(n.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f y0 = new Vector3f(n).cross(x0);
        float cos = (float) Math.cos(spin) * size / 2, sin = (float) Math.sin(spin) * size / 2;
        Vector3f x = new Vector3f(x0).mul(cos).add(new Vector3f(y0).mul(sin));
        Vector3f y = new Vector3f(y0).mul(cos).sub(new Vector3f(x0).mul(sin));
        quad(out, new Vector3f(center).sub(x).sub(y), new Vector3f(center).add(x).sub(y), new Vector3f(center).add(x).add(y),
                new Vector3f(center).sub(x).add(y), shape, param, rgb, opacity);
    }

    // Camera-facing square into any buffer.
    static void billboard(VertexConsumer out, Vector3f center, float size, float spin, float[] rgb, float opacity) {
        disk(out, center, new Vector3f(LOOK).negate(), size, spin, SOLID, 0, rgb, opacity);
    }

    // The camera's left and up (for effects laid flat on the screen).
    static Vector3f left() {
        return new Vector3f(LEFT);
    }

    static Vector3f up() {
        return new Vector3f(UP);
    }

    static float[] rgb(int color) {
        return new float[] {((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f};
    }

    static float[] mix(float[] a, float[] b, float t) {
        return new float[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    static int parse(String hex, int fallback) {
        if (hex == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
