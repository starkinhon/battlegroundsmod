package com.starkinhon.battlegroundsmod.client.fx;

import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.burst;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.clamp;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.crystal;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.crystals;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.ease3;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.flash;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.frost;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.hsh;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.impact;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.lerp;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.mist;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.rgb;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.rnd;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.shake;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.shock;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.smooth;
import static com.starkinhon.battlegroundsmod.client.fx.BleachFx.speedLines;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.starkinhon.battlegroundsmod.client.fx.BleachFx.Body;
import com.starkinhon.battlegroundsmod.client.fx.BleachFx.Pc;
import com.starkinhon.battlegroundsmod.client.fx.BleachFx.Run;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Rukia's skills: forja/bleach/rukia.js, module by module, with the page's numbers. Each update() is the page's
// update(dt) with t in seconds; what the page does to its victims (freeze, statue, shatter) comes from the server here
// (BL_ICE), at the same moments.
final class Rk {
    // styles of BL_ICE (who froze them: the shards and the screen differ)
    static final int TSUKI = 0, HAKUREN = 1, SHIRA = 2, TRAP = 3, FLOWER = 4;

    private Rk() {
    }

    // ---------- Z: Tsukishiro ----------

    static final class Tsukishiro extends Run {
        final float R;
        final Pc frost, light, pillar, halo, flash, mist, puff;
        final List<Pc> crys;
        final float[][] cp = new float[16][];
        boolean go, brk;

        Tsukishiro(Vec3 at, float radius) {
            super(at, null);
            R = radius;
            frost = add(frost(5.5f));
            light = add(new Pc(BleachMesh.LIGHT, BleachMesh.Mesh.TUBE, BleachMesh.BOTH, 18, BleachFx.seed()));
            pillar = add(new Pc(BleachMesh.PILLAR, BleachMesh.Mesh.TUBE, BleachMesh.BOTH, 15, BleachFx.seed()));
            halo = add(new Pc(BleachMesh.HALO, BleachMesh.Mesh.HALO, BleachMesh.BACK, 19, BleachFx.seed()).color(0x9fd8ff));
            flash = add(flash(0xcfeeff));
            mist = add(mist(6));
            puff = add(mist(7, 0xf6faff, 0x9db4cf));
            crys = new ArrayList<>();
            for (Pc c : crystals(16, 0.5f)) {
                crys.add(add(c));
            }
            for (int i = 0; i < 16; i++) {
                float ang = i / 16f * Mth.TWO_PI + (hsh(i, 2, 9) - 0.5f) * 0.3f, rad = 2.85f + 0.75f * hsh(i, 3, 9), big = hsh(i, 4, 9);
                cp[i] = new float[] {ang, rad, 0.6f + 1.9f * big * big, 0.1f + 0.22f * big, 0.25f + 0.5f * hsh(i, 5, 9), 0.02f + 0.14f * hsh(i, 6, 9)};
            }
            frost.show(true).at(0, 0.03f, 0);
            frost.p.a(0, 0, R / 5.5f, 0).b(5.5f, 0, 0, 0);
        }

        @Override
        void update(float dt) {
            t += dt;
            final float DRAW = 0.4f;
            if (t < DRAW) {
                float k = smooth(t / DRAW);
                frost.p.a(0.1f * k, k, R / 5.5f, 0);
                for (int j = 0; j < 2; j++) {
                    float a = rnd() * 6.283f, r = (float) Math.sqrt(rnd()) * R;
                    snow(Mth.cos(a) * r, 0.1f, Mth.sin(a) * r, 0, 0.6f + rnd() * 0.8f, 0, 1.2f + rnd(), 0.06f + 0.06f * rnd());
                }
                return;
            }
            float s = t - DRAW;
            if (!go) {
                go = true;
                Vec3 c = world(0, 2, 0);
                impact(c, 0xbfe6ff, 1, 0, 1);
                shock(c, 1.8f, 0xd7f1ff);
                shake(c, 0.6f);
                light.show(true).at(0, 0, 0);
                pillar.show(true).at(0, 0, 0);
                halo.show(true).at(0, 4.5f, 0).scale(4.2f, 7, 4.2f);
                mist.show(true).at(0, 0.1f, 0);
                flash.show(true).at(0, 1.4f, 0);
                for (int j = 0; j < 140; j++) {
                    float a = rnd() * 6.283f, r = (float) Math.sqrt(rnd()) * R;
                    snow(Mth.cos(a) * r, rnd() * 3, Mth.sin(a) * r, -Mth.sin(a) * 1.5f, 3 + rnd() * 9, Mth.cos(a) * 1.5f, 0.8f + rnd() * 1.4f,
                            0.06f + 0.1f * rnd() * rnd());
                }
            }
            float g = ease3(s / 0.22f);
            light.p.a(R * (0.9f + 0.1f * g), 42 * ease3(s / 0.12f), (float) Math.exp(-s * 2.2f) * 1.2f + 0.08f * (s < 1.3f ? 1 : 0), 0);
            if (s > 1.6f) {
                light.show(false);
            }
            halo.p.a((float) Math.exp(-s * 2.5f) * 0.9f + 0.15f * (s < 1.2f ? 1 : 0), 0, 0, 0);
            float fl = s / 0.18f;
            flash.show(fl < 1).scale(0.8f + 1.4f * (float) Math.sqrt(Math.min(fl, 1)));
            flash.p.alpha = 0.7f * (1 - Math.min(fl, 1));
            float shat = clamp((s - 1.2f) / 0.25f, 0, 1);
            pillar.p.a(R * 0.9f, 10, g, shat).b(0, (float) Math.exp(-s * 1.6f) + 0.25f, 0, 0);
            frost.p.a(Math.min(0.66f, 0.1f + 0.56f * ease3(s / 0.3f)) + 0.05f * smooth((s - 0.3f) / 1.2f), 1, R / 5.5f, smooth((s - 2.8f) / 3.0f));
            frost.p.alpha = 1 - smooth((s - 4.5f) / 1.5f);
            if (s > 6) {
                frost.show(false);
            }
            float me = R * (1.1f + 1.0f * ease3(s / 2.2f));
            mist.scale(me, 1.4f, me);
            mist.p.a(0.7f, 0.05f, 0, 0);
            mist.p.life = clamp(s / 3.2f, 0, 1);
            if (s > 3.2f) {
                mist.show(false);
            }
            for (int i = 0; i < 16; i++) {
                Pc pc = crys.get(i);
                float[] c = cp[i];
                float gg = ease3((s - c[5]) / 0.12f);
                pc.show(gg > 0 && s < 1.45f).at(Mth.cos(c[0]) * c[1], 0, Mth.sin(c[0]) * c[1]);
                pc.p.a(c[2], c[3], gg, shat).b(0, Mth.cos(c[0]) * c[4], Mth.sin(c[0]) * c[4], 0);
            }
            if (s >= 1.45f && !brk) {
                brk = true;
                pillar.show(false);
                Vec3 c = world(0, 2.5f, 0);
                shake(c, 0.45f);
                impact(c, 0xd8f2ff, 0, 1, 0);
                burst(world(0, 0.2f, 0), 70, R * 1.6f, 9, 6.5f, 3, 0.55f);
                for (int i = 0; i < 16; i += 2) {
                    burst(world(crys.get(i).pos), 2, 0.4f, 1, 4, 3, 0.25f);
                }
                puff.show(true).at(0, 1.2f, 0).scale(R * 1.05f, 2.2f, R * 1.05f);
                shock(c, 1.2f, 0xe0f4ff);
            }
            if (brk) {
                float ps = s - 1.45f;
                puff.p.life = clamp(0.3f + ps / 1.6f, 0, 1);
                puff.p.a(0.3f, 0.2f, 0, 0);
                float pr = R * (1.05f + 0.5f * ease3(ps / 1.5f));
                puff.scale(pr, 2.2f + 0.6f * ps, pr);
                if (ps > 1.8f) {
                    puff.show(false);
                }
            }
            if (s > 6.1f) {
                done = true;
            }
        }
    }

    // ---------- X: Hakuren ----------

    static final class Hakuren extends Run {
        static final float LEN = 18, SPEED = 0.55f, STEP = 0.13f;
        final List<List<Pc>> stabs = new ArrayList<>();
        final List<Pc> stabFrost = new ArrayList<>();
        final Pc bliz, path, mist, casing;
        final Pc[] puffs = new Pc[2];
        final List<Pc> ridge;
        final float[][] rp = new float[44][];
        final float[] tg = new float[44];
        final Vector3f[] stab = new Vector3f[4];
        boolean go, brk, cased;
        float hitT = -1;
        float s;

        Hakuren(Vec3 feet, Vec3 dir) {
            super(feet, dir);
            for (int i = 0; i < 4; i++) {
                List<Pc> l = new ArrayList<>();
                for (Pc c : crystals(3, 40 + i * 11)) {
                    l.add(add(c));
                }
                stabs.add(l);
                stabFrost.add(add(frost(1.4f)));
            }
            bliz = add(new Pc(BleachMesh.BLIZ, BleachMesh.Mesh.TUBE_HI, BleachMesh.BOTH, 13, BleachFx.seed()));
            path = add(frost(11));
            ridge = new ArrayList<>();
            for (Pc c : crystals(44, 200)) {
                ridge.add(add(c));
            }
            mist = add(mist(6));
            puffs[0] = add(mist(7));
            puffs[1] = add(mist(7));
            casing = add(new Pc(BleachMesh.PILLAR, BleachMesh.Mesh.TUBE, BleachMesh.BOTH, 15, BleachFx.seed()));
            for (int i = 0; i < 44; i++) {
                float z = 1.8f + 15.5f * (i + hsh(i, 1, 4)) / 44, spread = 0.5f + 2.4f * (z / 17), x = (hsh(i, 2, 4) * 2 - 1) * spread;
                float big = hsh(i, 3, 4) * (1 - 0.6f * Math.abs(x) / Math.max(spread, 0.1f));
                rp[i] = new float[] {x, -z, 0.5f + 2.0f * big + 0.6f * z / 17, 0.1f + 0.24f * big, x * 0.12f + (hsh(i, 5, 4) - 0.5f) * 0.25f,
                        -(0.25f + 0.35f * hsh(i, 6, 4)), 0.03f * hsh(i, 7, 4)};
            }
            float[] as = {-0.75f, -0.25f, 0.25f, 0.75f};
            for (int i = 0; i < 4; i++) {
                stab[i] = new Vector3f(Mth.sin(as[i]) * 1.7f, 0, -Mth.cos(as[i]) * 1.7f);
            }
        }

        // The server froze someone in the avalanche: the first one gets the casing statue.
        void hit(Vec3 at) {
            if (!cased && go) {
                cased = true;
                hitT = s;
                casing.show(true).at(local(at));
            }
        }

        @Override
        void update(float dt) {
            t += dt;
            for (int i = 0; i < 4; i++) {
                float si = t - i * STEP;
                if (si < 0) {
                    continue;
                }
                Pc fp = stabFrost.get(i);
                fp.show(si < 3.6f).at(stab[i].x, 0.03f, stab[i].z);
                fp.p.a(0.25f + 0.35f * ease3(si / 0.2f), 0, 0, smooth((si - 2.6f) / 1)).b(1.4f, 0, 0, 0);
                List<Pc> l = stabs.get(i);
                for (int j = 0; j < l.size(); j++) {
                    Pc pc = l.get(j);
                    float a = j * 2.1f + i, gg = ease3((si - 0.02f * j) / 0.1f);
                    pc.show(gg > 0 && si < 2.3f).at(stab[i].x, 0, stab[i].z);
                    pc.p.a(0.35f + 0.35f * hsh(i, j, 1), 0.07f + 0.05f * hsh(j, i, 2), gg, clamp((t - 2.1f) / 0.2f, 0, 1)).b(0, Mth.cos(a) * 0.5f, Mth.sin(a) * 0.5f, 0);
                }
                if (si < dt * 1.5f) {
                    shake(world(stab[i]), 0.12f);
                    for (int j = 0; j < 10; j++) {
                        snow(stab[i].x, 0.1f, stab[i].z, (rnd() - 0.5f) * 2, 1.5f + rnd() * 2.5f, (rnd() - 0.5f) * 2, 0.6f + rnd() * 0.6f, 0.05f + 0.05f * rnd());
                    }
                }
            }
            final float T0 = 4 * STEP + 0.12f;
            s = t - T0;
            if (s < 0) {
                return;
            }
            if (!go) {
                go = true;
                bliz.show(true);
                path.show(true);
                mist.show(true);
                speedLines(world(0, 1, -3));
                shake(origin, 0.35f);
                bliz.at(0, 1.0f, -0.6f).up(new Vector3f(0, -0.02f, -1).normalize());
            }
            float reach = ease3(s / SPEED), frontZ = -0.6f - LEN * reach;
            bliz.p.a(LEN, 0.55f, 3.8f, Math.max(reach, 0.02f)).b(smooth((s - SPEED - 0.1f) / 0.9f) * 1.25f, 0.5f, 0, 0);
            bliz.p.alpha = 1 - smooth((s - SPEED - 0.6f) / 0.5f);
            if (s > SPEED + 1.1f) {
                bliz.show(false);
            }
            path.at(0, 0.03f, -0.6f - 11 + 0.6f).scale(11, 1, 11);
            path.q.rotationY(Mth.PI);
            path.p.a(0.3f, 0, 0, smooth((s - 2.6f) / 2.6f)).b(11, 0.75f, 0.001f + reach * 0.98f, 0);
            if (s > 5.4f) {
                path.show(false);
            }
            for (int i = 0; i < 44; i++) {
                Pc pc = ridge.get(i);
                float[] c = rp[i];
                boolean passed = frontZ <= c[1];
                if (!passed) {
                    tg[i] = 0;
                } else if (tg[i] == 0) {
                    tg[i] = s;
                }
                float gg = passed ? ease3((s - tg[i] - c[6]) / 0.14f) : 0;
                pc.show(gg > 0 && !brk).at(c[0], 0, c[1]);
                pc.p.a(c[2], c[3], gg, clamp((s - 2.0f) / 0.25f, 0, 1)).b(0, c[4], c[5], 0);
            }
            if (reach < 1) {
                for (int j = 0; j < 6; j++) {
                    snow((rnd() - 0.5f) * 6 * reach, 0.2f + rnd() * 2.5f, frontZ + rnd(), (rnd() - 0.5f) * 3, rnd() * 2, -6 - rnd() * 6, 0.5f + rnd() * 0.5f,
                            0.05f + 0.08f * rnd() * rnd());
                }
            }
            mist.at(0, 0.2f, (frontZ - 0.6f) / 2).scale(1.2f + 2.4f * reach, 1.6f, Math.max(0.5f, Math.abs(frontZ) / 2 + 0.6f));
            mist.p.a(0.6f, 0, 0, 0);
            mist.p.life = clamp((s - 0.1f) / 3.2f, 0, 1);
            if (s > 3.3f) {
                mist.show(false);
            }
            if (cased) {
                float cs = s - hitT;
                casing.p.a(0.78f, 2.5f, ease3(cs / 0.16f), clamp((s - 2.0f) / 0.25f, 0, 1)).b(0, 0.3f, 0, 0);
            }
            if (s >= 2.25f && !brk) {
                brk = true;
                casing.show(false);
                shake(world(0, 1, -6), 0.5f);
                impact(world(0, 1, -6), 0xd8f2ff, 0, 1);
                for (int i = 0; i < 44; i += 3) {
                    burst(world(ridge.get(i).pos), 2, 0.6f, rp[i][2], 4, 3, 0.12f + rp[i][3]);
                }
                for (int k = 0; k < 2; k++) {
                    puffs[k].show(true).at(0, 0.3f, -5 - k * 7).scale(2.4f, 1.5f, 4);
                    puffs[k].p.life = 0.35f;
                }
            }
            if (brk) {
                for (Pc p : puffs) {
                    p.p.life = Math.min(1, p.p.life + dt / 1.6f);
                    p.p.a(0.4f, 0.6f, 0, 0);
                    if (p.p.life >= 1) {
                        p.show(false);
                    }
                }
            }
            if (s > 5.5f) {
                done = true;
            }
        }
    }

    // ---------- C: Shirafune (b: 0 it pierced someone at a, 1 it hit the ground at a: the stake, 2 nothing) ----------

    static final class Shirafune extends Run {
        static final float MAX = 17;
        final int caster;
        final int mode;
        final float stop;
        final Vector3f dir;
        final Pc sheath, lance, stake, stakeFrost, flash, mist;
        final List<Pc> spikes = new ArrayList<>();
        boolean go, hit, brk;
        float hitAt, stakeT = -1;
        Vector3f hitP;

        Shirafune(int caster, Vec3 base, Vec3 dir, float stop, int mode) {
            super(base, null);
            this.caster = caster;
            this.mode = mode;
            this.stop = stop;
            this.dir = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
            sheath = add(crystal(77));
            lance = add(crystal(91));
            stake = add(new Pc(BleachMesh.STAKE, BleachMesh.Mesh.CRY, BleachMesh.BOTH, 14, 5));
            for (Pc c : crystals(4, 300)) {
                spikes.add(add(c));
            }
            stakeFrost = add(frost(2.2f));
            flash = add(flash(0xcfeeff));
            mist = add(mist(7));
        }

        @Override
        void update(float dt) {
            t += dt;
            float s0 = t;
            // the blade turns to crystal in her hand (0.35 s)
            Entity e = FxAnchor.entity(caster);
            if (s0 < 0.55f && e instanceof LivingEntity le) {
                Vec3 look = le.getViewVector(1);
                Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
                Vec3 hand = le.position().add(0, le.getBbHeight() * 0.62, 0).add(right.scale(0.38)).add(look.scale(0.3));
                Vector3f bd = new Vector3f((float) look.x, (float) look.y, (float) look.z);
                sheath.show(true).at(local(hand.add(look.scale(0.1)))).up(bd);
                sheath.p.a(1.45f, 0.075f, ease3(s0 / 0.3f), 0);
                if (s0 < 0.35f) {
                    for (int j = 0; j < 2; j++) {
                        float k = rnd() * 1.4f;
                        BleachFx.emit(hand.add(look.scale(k)), new Vec3(rnd() - 0.5f, rnd(), rnd() - 0.5f), 0.5f, 0.05f);
                    }
                }
            } else {
                sheath.show(false);
            }
            if (s0 < 0.35f) {
                return;
            }
            float s = s0 - 0.35f;
            if (!go) {
                go = true;
                lance.show(true).at(0, 0, 0).up(dir);
                speedLines(origin);
                shake(origin, 0.2f);
            }
            float len = MAX * ease3(s / 0.13f);
            if (mode == 1) {
                len = Math.min(len, stop + 0.35f);
            } else if (mode == 2) {
                len = Math.min(len, stop);
            }
            if (mode == 0 && len >= stop && !hit) {
                hit = true;
                hitAt = s;
                hitP = new Vector3f(dir).mul(stop);
                Vec3 hp = world(hitP);
                impact(hp, 0xcdeeff, 1, 0, 1, 0);
                shock(hp, 1.3f, 0xe3f6ff);
                shake(hp, 0.6f);
                flash.show(true).at(hitP);
                for (int i = 0; i < spikes.size(); i++) {
                    float a = i * 1.7f;
                    // the page's spikes burst out of the wound backwards along the lance, in a cone
                    Vector3f back = new Vector3f(dir);
                    Vector3f side = new Vector3f(dir).cross(0, 1, 0);
                    if (side.lengthSquared() < 1.0E-4f) {
                        side.set(1, 0, 0);
                    }
                    side.normalize();
                    Vector3f up = new Vector3f(side).cross(dir).normalize();
                    Vector3f d = back.add(side.mul(Mth.cos(a) * 0.6f)).add(up.mul(Mth.sin(a) * 0.6f)).normalize();
                    spikes.get(i).show(true).at(new Vector3f(hitP).add(new Vector3f(dir).mul(0.15f))).up(d);
                }
                burst(world(new Vector3f(hitP).add(new Vector3f(dir).mul(0.5f)).add(0, -0.4f, 0)), 14, 0.4f, 0.6f, 3, 3, 0.12f,
                        new Vec3(dir.x, dir.y + 0.2f, dir.z), 6);
            }
            if (hit) {
                len = Math.min(len, stop + 1.2f);
            }
            float hold2 = mode == 0 ? 0.65f : 0.45f;
            float brkK = clamp((s - 0.13f - hold2) / 0.18f, 0, 1);
            lance.p.a(Math.max(len, 0.01f), 0.24f, 1, brkK);
            if (hit) {
                float hs = s - hitAt;
                for (int i = 0; i < spikes.size(); i++) {
                    spikes.get(i).p.a(0.6f + 0.4f * hsh(i, 1, 2), 0.09f, ease3(hs / 0.15f), clamp((s - 2.3f) / 0.2f, 0, 1));
                }
                float fl = hs / 0.25f;
                flash.show(fl < 1).scale(0.8f + 1.4f * (float) Math.sqrt(Math.min(1, fl)));
                flash.p.alpha = 1 - Math.min(1, fl);
                if (s > 2.5f) {
                    for (Pc p : spikes) {
                        p.show(false);
                    }
                }
            }
            if (brkK >= 1 && !brk) {
                brk = true;
                lance.show(false);
                for (int k = 0; k < 18; k++) {
                    burst(world(new Vector3f(dir).mul(len * (k + rnd()) / 18)), 2, 0.3f, 0.3f, 2.5f, 1.5f, 0.14f + 0.1f * rnd());
                }
                mist.show(true).at(new Vector3f(dir).mul(len * 0.5f)).scale(0.5f, 0.5f, len * 0.5f);
                mist.q.rotationTo(0, 0, 1, dir.x, dir.y, dir.z);
                mist.p.life = 0.4f;
                if (mode == 1) {
                    Vector3f sp = new Vector3f(dir).mul(stop);
                    stake.show(true).at(sp).up(new Vector3f(dir).negate().add(0, 0.3f, 0).normalize());
                    stakeFrost.show(true).at(sp.x, sp.y + 0.03f, sp.z);
                    stakeT = s;
                }
            }
            if (brk) {
                mist.p.life = Math.min(1, mist.p.life + dt / 1.2f);
                mist.p.a(0, 2, 0, 0);
                if (mist.p.life >= 1) {
                    mist.show(false);
                }
            }
            if (stakeT >= 0) {
                float ss = s - stakeT;
                stake.p.a(2.4f, 0.32f, ease3(ss / 0.1f + 0.6f), ss > 8 ? clamp((ss - 8) / 0.25f, 0, 1) : 0);
                stakeFrost.p.a(0.35f + 0.15f * ease3(ss / 0.4f), 0, 0, smooth((ss - 7.6f) / 0.6f));
                if (ss > 8.3f) {
                    stake.show(false);
                    stakeFrost.show(false);
                    done = true;
                }
            } else if (s > 3.2f) {
                done = true;
            }
        }
    }

    // ---------- B click: Rikujokoro (six bars of light round the target, who follows) ----------

    static final class Riku extends Run {
        final int target;
        final List<Pc> bars = new ArrayList<>();
        final float[][] ba = new float[6][];
        final Pc flash;
        boolean slam;
        float ft = -1;

        Riku(int target, Vec3 at) {
            super(at, null);
            this.target = target;
            for (int i = 0; i < 6; i++) {
                bars.add(add(new Pc(BleachMesh.KIDO, BleachMesh.Mesh.GRID_LO, BleachMesh.BOTH, 17, 3 + i * 5.3f).color(0xffe08a)));
                ba[i] = new float[] {i / 6f * Mth.TWO_PI + (hsh(i, 1, 3) - 0.5f) * 0.28f + 0.3f, (hsh(i, 2, 3) - 0.5f) * 0.35f, 1.0f + (hsh(i, 3, 3) - 0.5f) * 0.18f,
                        1.7f + 0.5f * hsh(i, 4, 3), 0.3f + 0.08f * hsh(i, 5, 3), i * 0.035f};
            }
            flash = add(flash(0x9cc8ff));
        }

        @Override
        void update(float dt) {
            t += dt;
            Entity e = FxAnchor.entity(target);
            if (e != null) {
                origin = e.position();
            }
            for (int i = 0; i < 6; i++) {
                Pc pc = bars.get(i);
                float[] b = ba[i];
                float s = t - 0.18f - b[5], k = clamp(s / 0.14f, 0, 1), r = lerp(4.8f, 0.12f, k * k);
                pc.show(s > -0.15f && t < 3.9f);
                Vector3f dirIn = new Vector3f(-Mth.cos(b[0]), 0, -Mth.sin(b[0]));
                pc.at(Mth.cos(b[0]) * (r + b[3]), b[2], Mth.sin(b[0]) * (r + b[3]));
                // Matrix4.lookAt(0, -dirIn, up): the bar's +z points at the center, then turned round its own z
                pc.q.identity().lookAlong(new Vector3f(dirIn).negate(), new Vector3f(0, 1, 0)).conjugate();
                pc.q.rotateZ((i % 2 == 1 ? 0.7f : -0.7f) + b[1]);
                pc.p.a(b[3], b[4], 0, 0);
                pc.p.alpha = (s < 0 ? (s + 0.15f) / 0.15f : 1) * (t > 3.4f ? (0.5f + 0.5f * Mth.sin(t * 60)) * (1 - (t - 3.4f) / 0.5f) : 1);
            }
            if (t >= 0.18f + 0.14f + 0.175f && !slam) {
                slam = true;
                Vec3 c = world(0, 1, 0);
                impact(c, 0xffe9a8, 1, 0);
                shock(c, 0.9f, 0xfff1c2);
                shake(c, 0.35f);
                flash.show(true).at(0, 1, 0);
                flash.p.color = rgb(0xfff0b8);
                ft = t;
            }
            if (ft >= 0) {
                float fl = (t - ft) / 0.25f;
                flash.show(fl < 1).scale(0.6f + 1.2f * (float) Math.sqrt(Math.min(1, fl)));
                flash.p.alpha = 1 - Math.min(1, fl);
            }
            if (t > 3.95f) {
                done = true;
            }
        }
    }

    // ---------- B held: Soren Sokatsui (the chant with blue fire in her palms, then the twin blue fire) ----------

    static final class Soren extends Run {
        static final float CH = 0.75f;
        final int caster;
        final Pc[] fires = new Pc[2], palms = new Pc[2];
        final Pc burst, smoke;
        Vec3 tgt;
        float fireT = -1, ht = -1;
        boolean shot, hitDone;
        float ringSeed = rnd() * 20;

        Soren(int caster, Vec3 at) {
            super(at, null);
            this.caster = caster;
            for (int i = 0; i < 2; i++) {
                fires[i] = add(new Pc(BleachMesh.FIRE, BleachMesh.Mesh.TUBE_HI, BleachMesh.BOTH, 16, 10 + i * 9));
                palms[i] = add(new Pc(BleachMesh.BURST, BleachMesh.Mesh.SPH, BleachMesh.BOTH, 16, BleachFx.seed()).color(0x3f8dff).color2(0x0b1f7a));
            }
            burst = add(new Pc(BleachMesh.BURST, BleachMesh.Mesh.SPH, BleachMesh.BOTH, 15, BleachFx.seed()).color(0x3f8dff).color2(0x0b1f7a));
            smoke = add(mist(6, 0x8d9ab3, 0x4a5570));
        }

        void fire(Vec3 target) {
            tgt = target;
            if (t < CH) {
                t = CH;
            }
        }

        void cancel() {
            if (tgt == null) {
                done = true;
            }
        }

        Vec3[] hands() {
            Entity e = FxAnchor.entity(caster);
            if (!(e instanceof LivingEntity le)) {
                return new Vec3[] {origin, origin};
            }
            Vec3 look = le.getViewVector(1);
            Vec3 f = new Vec3(look.x, 0, look.z).normalize();
            Vec3 right = new Vec3(-f.z, 0, f.x);
            Vec3 c = le.position().add(0, le.getBbHeight() * 0.72, 0).add(look.scale(0.62));
            return new Vec3[] {c.add(right.scale(0.18)), c.add(right.scale(-0.18))};
        }

        @Override
        void update(float dt) {
            t += dt;
            Vec3[] hands = hands();
            origin = hands[0];
            for (int i = 0; i < 2; i++) {
                Pc pc = palms[i];
                pc.show(t < CH + 0.1f).at(local(hands[i]));
                float r = 0.12f + 0.2f * smooth(t / CH);
                pc.scale(r * (1 + 0.08f * Mth.sin(t * 30 + i)));
                pc.p.life = 0.25f + 0.1f * Mth.sin(t * 9 + i);
                pc.p.a(0, 0, 0, 0);
            }
            if (t < CH || tgt == null) {
                if (rnd() < 0.5f) {
                    BleachFx.emit(hands[0], new Vec3(rnd() - 0.5f, 1 + rnd(), rnd() - 0.5f), 0.4f, 0.04f);
                }
                if (t > 6) {
                    done = true;
                }
                if (tgt == null) {
                    return;
                }
            }
            if (fireT < 0) {
                fireT = t;
            }
            float s = t - fireT;
            Vec3 mid = hands[0].add(hands[1]).scale(0.5);
            float dist = (float) mid.distanceTo(tgt);
            float reach = ease3(s / 0.2f), len = dist * reach;
            Vector3f to = local(tgt).sub(local(mid)).normalize();
            for (int i = 0; i < 2; i++) {
                Pc pc = fires[i];
                pc.show(s < 1.25f).at(local(mid)).up(to);
                pc.p.a(Math.max(len, 0.05f), 0.42f, 7, 0).b(i, smooth((s - 0.65f) / 0.55f) * 1.2f, 0, 0);
            }
            if (!shot) {
                shot = true;
                speedLines(mid);
                shake(mid, 0.25f);
            }
            if (reach >= 0.999f && !hitDone) {
                hitDone = true;
                ht = s;
                impact(tgt, 0x9cc8ff, 1, 0, 1);
                shock(tgt, 1.9f, 0xbcd8ff);
                shake(tgt, 0.75f);
                burst.show(true);
                smoke.show(true);
            }
            if (hitDone) {
                float hs = s - ht;
                Vector3f tl = local(tgt);
                burst.at(tl).scale(0.6f + 2.6f * ease3(hs / 0.5f));
                burst.p.life = clamp(hs / 1.4f, 0, 1);
                if (hs > 1.4f) {
                    burst.show(false);
                }
                float sr = 1.6f + 2 * ease3(hs / 1.5f);
                double gy = BleachFx.groundAt(tgt.x, tgt.y, tgt.z);
                smoke.at(local(new Vec3(tgt.x, gy + 0.2 + hs * 0.5, tgt.z))).scale(sr, 1.4f + 1.6f * ease3(hs / 1.5f), sr);
                smoke.p.a(0.3f, 0.4f, 0, 0);
                smoke.p.life = clamp(0.3f + (hs - 0.3f) / 2.4f, 0.3f, 1);
                smoke.show(hs > 0.3f && hs < 2.8f);
                // the dust ring (the approved kits' ring, KiMesh)
                BleachFx.dustRing(new Vec3(tgt.x, gy, tgt.z), 5 * ease3(hs / 1.2f), 6, clamp(hs / 1.6f, 0, 1), rgb(0x4f8dff), ringSeed);
                if (hs > 2.9f) {
                    done = true;
                }
            }
        }
    }

    // ---------- V: Bankai, Hakka no Togame (follows her) ----------

    static final class Bankai extends Run {
        final int caster;
        final Pc frost, mistR, aura, flash, halo, blade;
        final List<Pc> field;
        final float[][] fp = new float[34][];
        final List<Pc> train = new ArrayList<>(), crown = new ArrayList<>();
        int phase; // 0 on, 1 end
        float et;
        boolean go, trainBrk;
        float ft = -1;

        Bankai(int caster, Vec3 feet) {
            super(feet, null);
            this.caster = caster;
            frost = add(frost(10.5f));
            mistR = add(mist(6));
            aura = add(mist(7, 0xffffff, 0xb8cde4));
            flash = add(flash(0xe8f6ff));
            halo = add(new Pc(BleachMesh.HALO, BleachMesh.Mesh.HALO, BleachMesh.BACK, 19, BleachFx.seed()).color(0xcfeaff));
            field = new ArrayList<>();
            for (Pc c : crystals(34, 500)) {
                field.add(add(c));
            }
            for (int i = 0; i < 34; i++) {
                float a = hsh(i, 1, 6) * 6.283f, r = 2.2f + 7.3f * (float) Math.sqrt(hsh(i, 2, 6)), big = hsh(i, 3, 6) * (0.4f + 0.6f * r / 9.5f);
                fp[i] = new float[] {a, r, 0.4f + 1.7f * big, 0.08f + 0.2f * big, 0.2f + 0.5f * hsh(i, 4, 6)};
            }
            for (int i = 0; i < 7; i++) {
                Pc pc = add(new Pc(BleachMesh.PETAL, BleachMesh.Mesh.GRID_LO, BleachMesh.BOTH, 14, 31 + i * 4.1f));
                train.add(pc);
            }
            for (int i = 0; i < 6; i++) {
                Pc pc = add(new Pc(BleachMesh.PETAL, BleachMesh.Mesh.GRID_LO, BleachMesh.BOTH, 14, 61 + i * 3.3f));
                crown.add(pc);
            }
            blade = add(crystal(13));
            for (Pc p : field) {
                p.world = true;
            }
            for (Pc p : List.of(frost, mistR, aura, flash, halo, blade)) {
                p.world = true;
            }
            Body b = BleachFx.body(caster);
            b.ctrl = true;
            b.mode = 0;
            b.h = 2.05f;
            b.sh = -1;
            b.thaw = false;
            b.pending = 1;
        }

        void end() {
            if (phase == 0) {
                phase = 1;
                et = 0;
            }
        }

        @Override
        void update(float dt) {
            t += dt;
            Entity e = FxAnchor.entity(caster);
            if (!(e instanceof LivingEntity le)) {
                done = true;
                release();
                return;
            }
            origin = le.position();
            float yaw = Mth.lerp(0.5f, le.yBodyRotO, le.yBodyRot) * Mth.DEG_TO_RAD;
            face(new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)));
            Body body = BleachFx.body(caster);
            body.ctrl = true;
            float climb = clamp(t / 0.8f, 0, 1);
            // the blade turns to ice (her sword: in her hand, pointed up and forward)
            Vec3 look = le.getViewVector(1);
            Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
            Vec3 hand = le.position().add(0, le.getBbHeight() * 0.62, 0).add(right.scale(0.38)).add(look.scale(0.3));
            Vec3 bdw = look.add(0, 0.6, 0).normalize();
            blade.show(t > 0.5f).at(BleachFx.toV(hand.subtract(origin).add(bdw.scale(0.05)))).up(BleachFx.toV(bdw));
            blade.p.a(1.55f, 0.08f, ease3((t - 0.5f) / 0.3f), 0);
            if (phase == 0) {
                body.prog = climb - 0.52f * smooth((t - 0.85f) / 0.45f);
                body.tint = smooth(climb);
                aura.show(true).at(0, 0.05f, 0).scale(1.9f, 0.7f, 1.9f);
                aura.p.a(0.5f, 0.1f, 0, 0);
                aura.p.life = 0.5f + 0.08f * Mth.sin(t * 2);
                halo.show(true).at(0, 1.1f, 0).scale(1.6f, 2.2f, 1.6f);
                halo.p.a(0.25f + 0.2f * climb, 0, 0, 0);
                if (rnd() < 0.7f) {
                    BleachFx.emit(origin.add((rnd() - 0.5f) * 2, rnd() * 2.2f, (rnd() - 0.5f) * 2), new Vec3((rnd() - 0.5f) * 0.5f, 0.4f + rnd() * 0.6f,
                            (rnd() - 0.5f) * 0.5f), 1.2f, 0.04f + 0.05f * rnd());
                }
            }
            float s = t - 0.8f;
            if (s >= 0 && !go) {
                go = true;
                Vec3 c = origin.add(0, 1, 0);
                impact(c, 0xe6f6ff, 1, 0, 1, 0);
                shock(c, 2.4f, 0xffffff);
                shake(c, 0.7f);
                frost.show(true).at(0, 0.03f, 0);
                mistR.show(true);
                flash.show(true).at(0, 1, 0);
                ft = 0;
                for (int j = 0; j < 160; j++) {
                    float a = rnd() * 6.283f;
                    BleachFx.emit(origin.add(Mth.cos(a) * 0.6f, 0.3f + rnd() * 1.6f, Mth.sin(a) * 0.6f),
                            new Vec3(Mth.cos(a) * (4 + rnd() * 8), rnd() * 2, Mth.sin(a) * (4 + rnd() * 8)), 0.8f + rnd(), 0.05f + 0.08f * rnd() * rnd());
                }
            }
            if (go) {
                float front = 0.6f + 9.4f * ease3(s / 0.55f);
                frost.p.a(front / 10.5f, 0, 0, phase == 1 ? smooth((et - 0.3f) / 2) : 0).b(10.5f, 0, 0, 0);
                mistR.at(0, 0.15f, 0).scale(front * 1.05f, 1.3f, front * 1.05f);
                mistR.p.a(0.75f, 0, 0, 0);
                mistR.p.life = clamp(0.25f + s / 6, 0, phase == 1 ? 1 : 0.6f) + (phase == 1 ? et / 2 : 0);
                float fl = s / 0.3f;
                flash.show(fl < 1).scale(1 + 4 * (float) Math.sqrt(Math.min(1, fl)));
                flash.p.alpha = 1 - Math.min(1, fl);
                for (int i = 0; i < 34; i++) {
                    Pc pc = field.get(i);
                    float[] c = fp[i];
                    float gg = ease3((s - (c[1] - 0.6f) / 9.4f * 0.45f) / 0.12f);
                    pc.show(gg > 0 && !(phase == 1 && et > 0.6f)).at(Mth.cos(c[0]) * c[1], 0, Mth.sin(c[0]) * c[1]);
                    pc.p.a(c[2], c[3], gg, phase == 1 ? clamp((et - 0.3f) / 0.25f, 0, 1) : 0).b(0, Mth.cos(c[0]) * c[4], Mth.sin(c[0]) * c[4], 0);
                }
                float hs = le.getBbHeight() / 2f;
                for (int i = 0; i < 7; i++) {
                    Pc pc = train.get(i);
                    float a = (i - 3) * 0.24f, g2 = ease3((s - 0.1f - i * 0.03f) / 0.35f);
                    pc.show(g2 > 0 && !(phase == 1 && et > 0.5f)).at(Mth.sin(a) * 0.2f, 0.95f * hs, 0.12f + Math.abs(a) * 0.05f);
                    pc.q.rotationYXZ(a * 1.3f, Mth.PI * 0.62f + (hsh(i, 1, 8) - 0.5f) * 0.1f, 0);
                    pc.p.a((1.6f + 0.5f * hsh(i, 2, 8)) * g2, 0.32f + 0.08f * hsh(i, 3, 8), -0.7f, phase == 1 ? clamp((et - 0.2f) / 0.3f, 0, 1) : 0).b(-0.18f, 0, 0, 0);
                }
                for (int i = 0; i < 6; i++) {
                    Pc pc = crown.get(i);
                    float a = i / 6f * 6.283f, g2 = ease3((s - 0.2f) / 0.3f);
                    pc.show(g2 > 0 && !(phase == 1 && et > 0.5f)).at(0.2f, 1.95f * hs, 0.05f);
                    // rotation order ZXY: z = a, x = -0.9
                    pc.q.set(new Quaternionf().rotationZ(a).mul(new Quaternionf().rotationX(-0.9f)));
                    pc.p.a(0.32f * g2, 0.11f, -0.6f, 0).b(0.1f, 0, 0, 0);
                }
            }
            if (phase == 1) {
                et += dt;
                float melt = clamp(et / 1.6f, 0, 1);
                body.prog = 0.48f * (1 - melt);
                body.tint = 1 - smooth(melt);
                aura.show(false);
                halo.p.a(Math.max(0, 0.45f * (1 - et)), 0, 0, 0);
                if (rnd() < 0.8f) {
                    BleachFx.emit(origin.add((rnd() - 0.5f) * 0.7f, 2 - melt * 1.8f + rnd() * 0.3f, (rnd() - 0.5f) * 0.5f), new Vec3(0, -3 - rnd() * 2, 0), 0.4f,
                            0.035f);
                }
                if (et > 0.25f && !trainBrk) {
                    trainBrk = true;
                    burst(world(0, 0.5f, 0.6f), 24, 1, 1, 2.5f, 3, 0.18f);
                    for (int i = 0; i < 34; i += 3) {
                        burst(origin.add(BleachFx.toVec(field.get(i).pos)), 2, 0.3f, fp[i][2], 3, 3, 0.15f);
                    }
                }
                if (et > 2.6f) {
                    done = true;
                    release();
                }
            }
            if (t > 120) {
                done = true;
                release();
            }
        }

        void release() {
            Body b = BleachFx.body(caster);
            b.ctrl = true;
            b.prog = 0;
            b.tint = 0;
            b.pending = -1;
        }
    }

    // ---------- the Bankai's ice flower on someone it touched: crystal, then the flower, then it all shatters ----------

    static final class Flower extends Run {
        final int target;
        final List<Pc> petals = new ArrayList<>();
        boolean popped;

        Flower(int target, Vec3 at) {
            super(at, null);
            this.target = target;
            float seed0 = 100 + rnd() * 80;
            for (int i = 0; i < 11; i++) {
                petals.add(add(new Pc(BleachMesh.PETAL, BleachMesh.Mesh.GRID_LO, BleachMesh.BOTH, 15, seed0 + i * 2.9f)));
            }
            BleachFx.freeze(target, at, 0, 2.1f, 4);
            BleachFx.shake(at, 0.3f);
        }

        @Override
        void update(float dt) {
            t += dt;
            Entity e = FxAnchor.entity(target);
            if (e != null && !popped) {
                origin = e.position();
            }
            float vs = t;
            float shat = clamp((vs - 1.9f) / 0.25f, 0, 1), open = smooth((vs - 0.45f) / 0.6f), s0 = vs - 0.3f;
            for (int i = 0; i < 11; i++) {
                Pc pc = petals.get(i);
                boolean inner = i < 4;
                float a = (inner ? i / 4f : (i - 4) / 7f) * 6.283f + (inner ? 0.4f : 0) + (hsh(i, 9, 2) - 0.5f) * 0.3f;
                float len = inner ? 1.5f + 0.3f * hsh(i, 1, 1) : 2.3f + 0.5f * hsh(i, 2, 1), w = inner ? 0.42f : 0.62f + 0.12f * hsh(i, 3, 1);
                float g = ease3(s0 / 0.25f), lean = lerp(0.12f, inner ? 0.35f : 0.75f, open);
                pc.show(s0 > 0 && shat < 1).at(Mth.cos(a) * 0.25f, 0.02f, Mth.sin(a) * 0.25f);
                // rotation order YXZ: y = -a + pi/2, x = -(pi/2 - lean)
                pc.q.rotationYXZ(-a + Mth.HALF_PI, -(Mth.HALF_PI - lean), 0);
                pc.p.a(len * g, w, -0.9f, shat).b(0.12f + 0.2f * open, 0, 0, 0);
                pc.p.alpha = 1 - smooth((shat - 0.6f) / 0.4f);
            }
            if (vs >= 2.15f && !popped) {
                popped = true;
                Vec3 c = origin.add(0, 1.4, 0);
                impact(c, 0xe6f6ff, 1, 0);
                shock(c, 1.2f, 0xffffff);
                shake(c, 0.45f);
            }
            if (vs > 2.5f) {
                done = true;
            }
        }
    }
}
