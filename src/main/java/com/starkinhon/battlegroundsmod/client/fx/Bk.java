package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.clamp;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.flash;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.impact;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.lerp;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.rnd;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.shake;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.shock;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.smooth;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.speedLines;

import java.util.List;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.fx.BleachFx.Pc;
import com.starkinhon.battlegroundsmod.client.fx.BleachFx.Run;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Byakuya's skills: forja/bleach/byakuya.js, module by module, with the page's numbers (see Rk for how a Run maps the
// page's world onto the game's).
final class Bk {
    private Bk() {
    }

    static Pc swarm(float seed) {
        return new Pc(BleachMesh.SWARM, BleachMesh.Mesh.SWARM, BleachMesh.BOTH, 16, seed);
    }

    static Pc slash(int color) {
        return new Pc(BleachMesh.SLASH, BleachMesh.Mesh.GRID_LO, BleachMesh.BOTH, 21, BleachFx.seed()).color(color);
    }

    // The page's placeSlash: center c, the stroke along x, the plane's normal y.
    static void place(Pc pc, Vector3f c, Vector3f along, Vector3f normal, float size) {
        Vector3f x = new Vector3f(along).normalize(), y = new Vector3f(normal).normalize(), z = new Vector3f(x).cross(y).normalize();
        pc.q.setFromNormalized(new Matrix3f(x, y, z));
        pc.at(c).scale(size, 1, size);
    }

    static LivingEntity living(int id) {
        return FxAnchor.entity(id) instanceof LivingEntity le ? le : null;
    }

    // Where his sword hand is (right) or his pointing finger (left), out in front of him.
    static Vec3 hand(LivingEntity le, boolean left) {
        Vec3 look = le.getViewVector(1);
        Vec3 f = new Vec3(look.x, 0, look.z);
        f = f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 right = new Vec3(-f.z, 0, f.x);
        return le.position().add(0, le.getBbHeight() * 0.62, 0).add(right.scale(left ? -0.38 : 0.38)).add(look.scale(0.45));
    }

    // ---------- the pink sparks where petals cut (the page's makeCuts): each its own angle and size ----------

    static final class Cut extends Run {
        final Pc pc = add(slash(0xff8fc0));
        final Vector3f al = new Vector3f(rnd() - 0.5f, rnd() - 0.5f, rnd() - 0.5f).normalize();
        final Vector3f nr = new Vector3f(rnd() - 0.5f, rnd() - 0.5f, rnd() - 0.5f).normalize();
        final float sz = 0.35f + 0.35f * rnd();

        Cut(Vec3 at) {
            super(at.add((rnd() - 0.5) * 0.5, (rnd() - 0.5) * 0.9, (rnd() - 0.5) * 0.5), null);
        }

        @Override
        void update(float dt) {
            t += dt;
            boolean on = t < 0.22f;
            pc.show(on);
            if (!on) {
                done = true;
                return;
            }
            place(pc, new Vector3f(), al, nr, sz);
            pc.p.a(0, 0.05f, clamp(t / 0.06f, 0, 1) * 1.05f, clamp((t - 0.08f) / 0.14f, 0, 1) * 1.05f);
        }
    }

    // ---------- Z: Senbonzakura (the river follows his aim while held) ----------

    static final class Senbon extends Run {
        final int caster;
        final Pc sw = add(swarm(3));
        float rel = -1, len;

        Senbon(int caster, Vec3 at) {
            super(at, null);
            this.caster = caster;
            sw.show(true);
        }

        void release() {
            if (rel < 0) {
                rel = 0;
            }
        }

        @Override
        void update(float dt) {
            t += dt;
            LivingEntity le = living(caster);
            if (le == null) {
                done = true;
                return;
            }
            origin = hand(le, false);
            Vec3 a = le.getViewVector(1);
            len = Math.min(22, len + dt * 45);
            sw.at(0, 0, 0).up(new Vector3f((float) a.x, (float) a.y, (float) a.z));
            float back = rel >= 0 ? smooth(rel / 0.45f) : 0;
            if (rel >= 0) {
                rel += dt;
            }
            sw.p.a(len, 1.1f, 24, Math.min(1, t / 0.25f)).b(Mth.sin(t * 1.3f) * 0.5f, 0.15f, back, 0);
            if (rel >= 0.5f || t > 8) {
                done = true;
            }
        }
    }

    // ---------- X: Gokei (the sphere closes round the target while held; let go, it crushes) ----------

    static final class Gokei extends Run {
        final Pc sw = add(swarm(7)), burst = add(swarm(9)), fl = add(flash(0xffc2dc));
        int key;
        float rel = -1, R = 3.2f;
        boolean crushed;

        Gokei(Vec3 center) {
            super(center, null);
            sw.show(true);
        }

        void release() {
            if (rel < 0) {
                rel = 0;
            }
        }

        @Override
        void update(float dt) {
            t += dt;
            if (rel < 0) {
                R = lerp(3.2f, 1.25f, smooth(t / 3));
                sw.p.a(R, 0.35f, 1.6f + 2.2f * smooth(t / 3), Math.min(1, t / 0.4f)).b(0, 0, 0, 1);
                if (t > 8) {
                    done = true;
                }
                return;
            }
            rel += dt;
            float cr = clamp(rel / 0.16f, 0, 1);
            sw.p.b(0, 0, cr, 1).a(R, 0.2f, 6, 1);
            if (cr >= 1 && !crushed) {
                crushed = true;
                sw.show(false);
                burst.show(true);
                fl.show(true);
                impact(origin, 0xffd1e6, 1, 0, 1);
                shock(origin, 1.6f, 0xffd6ea);
                shake(origin, 0.7f);
            }
            if (crushed) {
                float bs = rel - 0.16f;
                burst.p.a(6, 0, 0, 1).b(0, 0, 0, 3);
                burst.p.life = clamp(bs / 1.2f, 0, 1);
                if (bs > 1.2f) {
                    burst.show(false);
                    done = true;
                }
                float f = bs / 0.25f;
                fl.show(f < 1).scale(0.8f + 2 * (float) Math.sqrt(Math.min(1, f)));
                fl.p.alpha = 1 - Math.min(1, f);
            }
        }
    }

    // ---------- C: Senka (shunpo behind the target: an afterimage stays, two cuts on its back) ----------

    static final class Senka extends Run {
        final int caster, target;
        final Vec3 from;
        final Pc[] cuts = {add(slash(0xffb3d4)), add(slash(0xffb3d4))};
        final Pc petals = add(swarm(21));
        final boolean[] cut = new boolean[2];
        boolean tp;

        Senka(int caster, Vec3 from, int target) {
            super(from, null);
            this.caster = caster;
            this.target = target;
            this.from = from;
            Entity e = FxAnchor.entity(target);
            if (e != null) {
                origin = e.position();
                face(e.position().subtract(from));
            }
        }

        @Override
        void update(float dt) {
            t += dt;
            Entity e = FxAnchor.entity(target);
            if (e != null) {
                origin = e.position();
            }
            if (t >= 0.06f && !tp) {
                tp = true;
                speedLines(from);
                petals.show(true).at(local(from.add(0, 1, 0)));
                petals.p.life = 0;
                BleachFx.ghost(caster, from, 0.5f);
            }
            if (tp) {
                BleachFx.ghost(caster, from, t > 0.5f ? 0 : 0.5f * (1 - clamp((t - 0.06f) / 0.4f, 0, 1)));
                petals.at(local(from.add(0, 1, 0)));
                petals.p.a(1.6f, 0, 0, 0.12f).b(0, 0, 0, 3);
                petals.p.life = clamp((t - 0.06f) / 0.7f, 0, 1);
                if (t > 0.8f) {
                    petals.show(false);
                }
            }
            float[] at = {0.1f, 0.22f};
            for (int i = 0; i < 2; i++) {
                float s = t - at[i];
                Pc c = cuts[i];
                c.show(s > 0 && s < 0.35f);
                if (s <= 0) {
                    continue;
                }
                if (!cut[i]) {
                    cut[i] = true;
                    Vec3 w = world(0, 1.2f, 0);
                    impact(w, 0xffd1e6, 1, 0);
                    shake(w, 0.35f);
                    shock(w, 0.8f, 0xffe0ef);
                }
                place(c, new Vector3f(0, 1.15f, -0.25f), i == 0 ? new Vector3f(1, 0.65f, 0) : new Vector3f(1, -0.65f, 0), new Vector3f(0, 0, 1), 1.5f);
                c.p.a(0, 0.1f, clamp(s / 0.05f, 0, 1) * 1.05f, clamp((s - 0.1f) / 0.25f, 0, 1) * 1.05f);
            }
            if (t > 0.9f) {
                done = true;
            }
        }
    }

    // ---------- B tap: Byakurai (a thin white bolt from his finger) ----------

    static final class Byakurai extends Run {
        final int caster;
        final Vec3 tgt;
        final Pc bolt = add(new Pc(BleachMesh.BOLT, BleachMesh.Mesh.TUBE_HI, BleachMesh.BOTH, 20, 4).color(0xbfe0ff));
        final Pc fl = add(flash(0xd8ecff));
        boolean hit;

        Byakurai(int caster, Vec3 tgt) {
            super(tgt, null);
            this.caster = caster;
            this.tgt = tgt;
        }

        @Override
        void update(float dt) {
            t += dt;
            LivingEntity le = living(caster);
            Vec3 finger = le == null ? tgt : hand(le, true);
            Vector3f d = BleachFx.toV(tgt.subtract(finger));
            float s = t - 0.15f;
            if (s < 0) {
                return;
            }
            bolt.show(s < 0.35f).at(local(finger)).up(new Vector3f(d).normalize());
            bolt.p.a(d.length(), 0.05f, clamp(s / 0.04f, 0, 1) * 1.02f, 0);
            bolt.p.alpha = 1 - smooth((s - 0.15f) / 0.2f);
            if (s >= 0.04f && !hit) {
                hit = true;
                impact(tgt, 0xd6ebff, 1, 0);
                shock(tgt, 0.9f, 0xe6f2ff);
                shake(tgt, 0.3f);
                fl.show(true);
            }
            if (hit) {
                float f = (s - 0.04f) / 0.2f;
                fl.show(f < 1).at(0, 0, 0).scale(0.4f + 0.9f * (float) Math.sqrt(Math.min(1, f)));
                fl.p.alpha = 1 - Math.min(1, f);
            }
            if (s > 0.4f) {
                done = true;
            }
        }
    }

    // ---------- B held: Danku (a wall of light rises in front of him and holds; hits ripple on it) ----------

    static final class Danku extends Run {
        static final float LIFE = 4.4f;
        final int caster;
        final Pc wall = add(new Pc(BleachMesh.WALL, BleachMesh.Mesh.GRID, BleachMesh.BOTH, 19, BleachFx.seed()).color(0xfff2c4));
        final Pc fl = add(flash(0xd8ecff));
        float hitT = -1, hu, hv;

        Danku(int caster, Vec3 feet, Vec3 forward) {
            super(feet, forward);
            this.caster = caster;
            wall.show(true);
        }

        void hit(Vec3 at) {
            Vector3f l = local(at);
            hu = clamp(l.x / 2.6f, -1, 1);
            hv = clamp(-(l.y - 1.6f) / 1.6f, -1, 1);
            hitT = t;
            fl.show(true).at(l.x, l.y, -3.2f + 0.2f);
            shock(at, 1, 0xfff4d0);
            shake(at, 0.35f);
        }

        @Override
        void update(float dt) {
            t += dt;
            wall.show(t < LIFE).at(0, 1.6f, -3.2f).scale(2.6f, 1, 1.6f);
            wall.q.rotationX(Mth.HALF_PI);
            wall.p.a(2.6f, 1.6f, clamp(t / 0.2f, 0, 1) * 1.05f, 0);
            wall.p.alpha = 1 - smooth((t - (LIFE - 0.4f)) / 0.4f);
            if (hitT >= 0) {
                float hs = t - hitT;
                wall.p.b(hu, hv, clamp(hs / 0.7f, 0, 1), 0);
                float f = hs / 0.25f;
                fl.show(f < 1).scale(0.3f + 0.6f * (float) Math.sqrt(Math.min(1, f)));
                fl.p.alpha = 0.6f * (1 - Math.min(1, f));
            } else {
                wall.p.b(0, 0, 1, 0);
            }
            if (t > LIFE) {
                done = true;
            }
        }
    }

    // ---------- V: Bankai Senbonzakura Kageyoshi; V again: Senkei; Shift+V: Shukei Hakuteiken (follows him) ----------

    static final class Kageyoshi extends Run {
        final int caster;
        final Pc blades = add(new Pc(BleachMesh.BLADES, BleachMesh.Mesh.BLADES, BleachMesh.BOTH, 12, 2));
        final Pc ripple = add(new Pc(BleachMesh.RIPPLE, BleachMesh.Mesh.GRID, BleachMesh.FRONT, 4, BleachFx.seed()));
        final Pc storm = add(swarm(11));
        final Pc senkei = add(new Pc(BleachMesh.BLADES, BleachMesh.Mesh.BLADES, BleachMesh.BOTH, 12, 6));
        final Pc sburst = add(swarm(13));
        final Pc fl = add(flash(0xffd6ea));
        final Pc[] wings = new Pc[2];
        final Pc halo = add(new Pc(BleachMesh.SLASH, BleachMesh.Mesh.GRID_LO, BleachMesh.BOTH, 20, BleachFx.seed()).color(0xfff6dc));
        final Pc light = add(new Pc(BleachMesh.BOLT, BleachMesh.Mesh.TUBE_HI, BleachMesh.BOTH, 20, 8).color(0xfff3d6));
        final Pc big = add(slash(0xffffff)), big2 = add(slash(0xffd9ea));
        float sk = -1, hk = -1, end = -1, sht, ht;
        boolean r, up, sc, sh, d, hc;
        Vec3 sat = Vec3.ZERO;
        int senkeiTarget = -1, hakuTarget = -1;
        Vec3 hakuFrom = Vec3.ZERO;

        Kageyoshi(int caster, Vec3 feet) {
            super(feet, null);
            this.caster = caster;
            blades.p.solid = true;
            senkei.p.solid = true;
            for (int i = 0; i < 2; i++) {
                int sd = i == 0 ? -1 : 1;
                wings[i] = add(new Pc(BleachMesh.WING, BleachMesh.Mesh.GRID, BleachMesh.BOTH, 18, 3 + i));
                wings[i].p.b(sd, i * 1.3f, 0, 0);
            }
            for (Pc p : List.of(blades, ripple, storm, senkei, sburst, fl, big, big2)) {
                p.world = true;
            }
        }

        void senkei(int target, Vec3 at) {
            if (sk < 0) {
                sk = 0;
                sat = at;
                senkeiTarget = target;
                sc = false;
                sh = false;
            }
        }

        void hakuteiken(int target) {
            if (hk < 0) {
                hk = 0;
                hakuTarget = target;
                d = false;
                hc = false;
                LivingEntity le = living(caster);
                hakuFrom = le == null ? origin : le.position();
            }
        }

        void stop() {
            if (end < 0) {
                end = 0;
            }
        }

        @Override
        void update(float dt) {
            t += dt;
            LivingEntity le = living(caster);
            if (le == null) {
                done = true;
                return;
            }
            origin = le.position();
            float yaw = Mth.lerp(0.5f, le.yBodyRotO, le.yBodyRot) * Mth.DEG_TO_RAD;
            face(new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)));
            float hs = le.getBbHeight() / 2f;
            float out = 1;
            if (end >= 0) {
                end += dt;
                out = 1 - smooth(end / 2);
                if (end > 2) {
                    done = true;
                    return;
                }
            }
            if (t > 0.18f && !r) {
                r = true;
                ripple.show(true);
                ripple.p.b[0] = 9;
                shake(origin, 0.2f);
            }
            if (r) {
                float rs = t - 0.18f;
                Vector3f rp = rot.transform(new Vector3f(0, 0.03f, -0.6f));
                ripple.at(rp).scale(9, 1, 9);
                ripple.p.a(1 + rs * 7, clamp(rs / 1.6f, 0, 1), 0, 0);
                if (rs > 1.6f) {
                    ripple.show(false);
                }
            }
            float rise = clamp((t - 0.45f) / 1.05f, 0, 1), dis = clamp((t - 2.1f) / 0.8f, 0, 1);
            blades.show(t > 0.45f && dis < 1).at(0, 0, 0);
            blades.p.a(rise * 1.35f, dis, 0, 0).b(0, 0, 0, 0);
            if (t > 0.45f && !up) {
                up = true;
                Vec3 c = origin.add(0, 1, 0);
                impact(c, 0xffd6ea, 1, 0, 1);
                shock(c, 2, 0xffe0ef);
                shake(c, 0.6f);
            }
            float st = clamp((t - 2.1f) / 0.8f, 0, 1);
            storm.show(t > 2.1f && hk < 0.9f).at(0, 0.1f, 0);
            storm.p.a(4.2f, 3.2f, 1.4f, st * (hk >= 0 ? 1 - smooth(hk / 0.6f) : 1) * out).b(0, 0, 0, 2);
            // Senkei
            if (sk >= 0) {
                sk += dt;
                Entity te = FxAnchor.entity(senkeiTarget);
                if (te != null && sk < 1.85f) {
                    sat = te.position();
                }
                Vector3f sl = BleachFx.toV(sat.subtract(origin));
                senkei.show(sk < 2.4f).at(sl);
                senkei.p.a(clamp(sk / 0.9f, 0, 1) * 1.35f, 0, clamp((sk - 1.3f) / 0.55f, 0, 1), 0).b(0, 0, 0, 1);
                if (sk > 1.3f && !sc) {
                    sc = true;
                    speedLines(sat);
                }
                if (sk > 1.85f && !sh) {
                    sh = true;
                    Vec3 c = sat.add(0, 1.2, 0);
                    impact(c, 0xffd6ea, 1, 0, 1, 0, 1);
                    shock(c, 2.6f, 0xffe6f2);
                    shake(c, 1.0f);
                    sburst.show(true);
                    fl.show(true).at(new Vector3f(sl).add(0, 1.1f, 0));
                    sht = sk;
                }
                if (sh) {
                    float bs = sk - sht;
                    sburst.at(new Vector3f(sl).add(0, 1.1f, 0));
                    sburst.p.a(8, 0, 0, 1).b(0, 0, 0, 3);
                    sburst.p.life = clamp(bs / 1.4f, 0, 1);
                    if (bs > 1.4f) {
                        sburst.show(false);
                    }
                    float f = bs / 0.3f;
                    fl.show(f < 1).scale(1 + 3 * (float) Math.sqrt(Math.min(1, f)));
                    fl.p.alpha = 1 - Math.min(1, f);
                }
                if (sk > 2.6f) {
                    sk = -1;
                    senkei.show(false);
                    sburst.show(false);
                }
            }
            // Shukei: Hakuteiken
            if (hk >= 0) {
                hk += dt;
                float op = smooth(hk / 0.6f);
                for (int i = 0; i < 2; i++) {
                    int sd = i == 0 ? -1 : 1;
                    wings[i].show(hk < 2.6f).at(sd * 0.18f, 1.35f * hs, 0.18f);
                    wings[i].p.a(3.3f, 1.5f, op, 1);
                    wings[i].p.alpha = 1 - smooth((hk - 2.2f) / 0.4f);
                }
                halo.show(hk < 2.6f).at(0, 2.25f * hs, 0.25f).scale(0.5f);
                halo.q.rotationX(Mth.HALF_PI);
                halo.p.a(0, 0.06f, op * 3, 0);
                Vec3 hand = hand(le, false);
                Vec3 look = le.getViewVector(1);
                light.show(hk > 0.4f && hk < 2.4f).at(local(hand)).up(localDir(look));
                light.p.a(1.8f, 0.07f, 1.02f, 0);
                Entity te = FxAnchor.entity(hakuTarget);
                if (hk >= 1.15f && !hc) {
                    hc = true;
                    ht = hk;
                    Vec3 tp = te == null ? origin : te.position();
                    Vec3 c = tp.add(0, 1.2, 0);
                    impact(c, 0xffffff, 1, 0, 1, 0);
                    shock(c, 2.8f, 0xffffff);
                    shake(c, 1.1f);
                    bigAt = tp;
                    bigFace = tp.subtract(hakuFrom);
                }
                if (hk > 0.9f && !d) {
                    d = true;
                    speedLines(origin);
                }
                if (hc) {
                    float s = hk - ht;
                    for (int i = 0; i < 2; i++) {
                        Pc b = i == 0 ? big : big2;
                        b.show(s < 0.9f);
                        // the page's frame: Byakuya at the origin looking down -z at the target
                        Run f = frame;
                        f.origin = bigAt;
                        f.face(bigFace);
                        Vector3f c = f.rot.transform(new Vector3f(0, 1.4f + i * 0.1f, -0.2f));
                        Vector3f al = f.rot.transform(new Vector3f(1, -0.35f + i * 0.1f, 0));
                        Vector3f nr = f.rot.transform(new Vector3f(0, 0, 1));
                        place(b, BleachFx.toV(bigAt.subtract(origin)).add(c), al, nr, 3.6f - i * 0.6f);
                        b.p.a(0, 0.09f - i * 0.03f, clamp(s / 0.08f, 0, 1) * 1.05f, clamp((s - 0.2f) / 0.6f, 0, 1) * 1.05f);
                    }
                }
                if (hk > 2.8f) {
                    hk = -1;
                    for (Pc p : wings) {
                        p.show(false);
                    }
                    halo.show(false);
                    light.show(false);
                    big.show(false);
                    big2.show(false);
                    stop();
                }
            }
            if (t > 40) {
                done = true;
            }
        }

        Vec3 bigAt = Vec3.ZERO, bigFace = new Vec3(0, 0, 1);
        final Run frame = new Run(Vec3.ZERO, null) {
            @Override
            void update(float dt) {
            }
        };
    }
}
