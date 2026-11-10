package com.starkinhon.soulsjjk.compat;

import java.lang.reflect.Field;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

// 1.21 vertex writing on 1.20.1. In 1.21 a vertex's attributes may be given in any order and the vertex ends by
// itself; in 1.20.1 they must follow the format's order and end with endVertex(). Each vertex is collected here and
// written in the target format's order on endVertex() (the port turns every addVertex chain into VX.v(...)...endVertex()).
public final class VX {
    private VX() {
    }

    private static final ThreadLocal<W> POOL = ThreadLocal.withInitial(W::new);
    private static Field formatField;

    public static VertexConsumer v(VertexConsumer target, float x, float y, float z) {
        return POOL.get().start(target, x, y, z);
    }

    public static VertexConsumer v(VertexConsumer target, double x, double y, double z) {
        return POOL.get().start(target, (float) x, (float) y, (float) z);
    }

    public static VertexConsumer v(VertexConsumer target, Vector3f p) {
        return POOL.get().start(target, p.x, p.y, p.z);
    }

    public static VertexConsumer v(VertexConsumer target, Matrix4f m, float x, float y, float z) {
        Vector4f p = m.transform(new Vector4f(x, y, z, 1));
        return POOL.get().start(target, p.x, p.y, p.z);
    }

    public static VertexConsumer v(VertexConsumer target, PoseStack.Pose pose, float x, float y, float z) {
        return v(target, pose.pose(), x, y, z);
    }

    public static VertexConsumer v(VertexConsumer target, PoseStack.Pose pose, Vector3f p) {
        return v(target, pose.pose(), p.x, p.y, p.z);
    }

    // 1.21 Tesselator.getInstance().begin(mode, format).
    public static BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        if (builder.building()) {
            builder.end();
        }
        builder.begin(mode, format);
        return builder;
    }

    // 1.21 BufferBuilder.build(): null when nothing was drawn.
    public static BufferBuilder.RenderedBuffer build(BufferBuilder builder) {
        BufferBuilder.RenderedBuffer rendered = builder.end();
        if (rendered.isEmpty()) {
            rendered.release();
            return null;
        }
        return rendered;
    }

    public static Vector3f normal(PoseStack.Pose pose, float x, float y, float z) {
        return pose.normal().transform(new Vector3f(x, y, z)).normalize();
    }

    static VertexFormat formatOf(VertexConsumer target) {
        if (!(target instanceof BufferBuilder builder)) {
            return null;
        }
        try {
            if (formatField == null) {
                for (Field f : BufferBuilder.class.getDeclaredFields()) {
                    if (f.getType() == VertexFormat.class) {
                        f.setAccessible(true);
                        formatField = f;
                        break;
                    }
                }
            }
            return formatField == null ? null : (VertexFormat) formatField.get(builder);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    static final class W implements VertexConsumer {
        VertexConsumer target;
        float x, y, z;
        int r = 255, g = 255, b = 255, a = 255;
        boolean hasColor, hasUv, hasOverlay, hasLight, hasNormal;
        float u, v;
        int ou, ov, lu, lv;
        float nx, ny, nz;

        W start(VertexConsumer target, float x, float y, float z) {
            this.target = target;
            this.x = x;
            this.y = y;
            this.z = z;
            hasColor = hasUv = hasOverlay = hasLight = hasNormal = false;
            r = g = b = a = 255;
            return this;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = (float) x;
            this.y = (float) y;
            this.z = (float) z;
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            hasColor = true;
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            return color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.u = u;
            this.v = v;
            hasUv = true;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            ou = u;
            ov = v;
            hasOverlay = true;
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            lu = u;
            lv = v;
            hasLight = true;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            nx = x;
            ny = y;
            nz = z;
            hasNormal = true;
            return this;
        }

        @Override
        public VertexConsumer normal(Matrix3f m, float x, float y, float z) {
            Vector3f n = m.transform(new Vector3f(x, y, z));
            return normal(n.x, n.y, n.z);
        }

        @Override
        public void endVertex() {
            VertexConsumer t = target;
            VertexFormat format = formatOf(t);
            if (format == null) {
                t.vertex(x, y, z);
                if (hasColor) {
                    t.color(r, g, b, a);
                }
                if (hasUv) {
                    t.uv(u, v);
                }
                if (hasOverlay) {
                    t.overlayCoords(ou, ov);
                }
                if (hasLight) {
                    t.uv2(lu, lv);
                }
                if (hasNormal) {
                    t.normal(nx, ny, nz);
                }
            } else {
                for (VertexFormatElement e : format.getElements()) {
                    switch (e.getUsage()) {
                        case POSITION -> t.vertex(x, y, z);
                        case COLOR -> t.color(r, g, b, a);
                        case UV -> {
                            if (e.getIndex() == 0) {
                                t.uv(u, v);
                            } else if (e.getIndex() == 1) {
                                t.overlayCoords(ou, ov);
                            } else {
                                t.uv2(lu, lv);
                            }
                        }
                        case NORMAL -> t.normal(nx, ny, nz);
                        default -> {
                        }
                    }
                }
            }
            t.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            target.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            target.unsetDefaultColor();
        }
    }
}
