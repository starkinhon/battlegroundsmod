package com.starkinhon.battlegroundsmod.power.powers.sukuna;

import java.util.Map;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModEffects;
import com.starkinhon.battlegroundsmod.boss.BossFalas;
import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.Sukuna;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Z: Desmantelar (解), Sukuna's thrown Dismantle, as in the 3D preview Stark approved (forja/sukuna-desmantelar).
//   - Tap: the crescent flies from the hand (3 blocks a tick), white-hot, then black with a white outline, goes through
//     whoever is in its plane and breaks into pieces a little past them. Tapping is a combo (Stark, 2026-11-08): the
//     cooldown doesn't start at once, each tap opens WINDOW ticks for the next. 1st: standing. 2nd: lying. 3rd: a burst of
//     six cuts (standing, lying and slanted) that throws the target far, the damage split between them. The cooldown
//     starts when the combo ends (the 3rd tap, or the window running out).
//   - Hold (before the 3rd tap): the chant (chapter 238). He stands still in the charging stance and says a word every
//     WORD ticks, each one over his head with a star in his hand; letting go throws the cut at the stage reached:
//     1 龍鱗 Dragon Scales 1.75x; 2 反発 Recoil 2.5x, throws far; 3 番いの流星 Paired Falling Stars, two crescents in an X, 3.5x.
//     A hit during the chant breaks it.
//   - With the Modelo bar full (ModelBar), after the third word: the gesture, both hands joined, still and open to hits
//     for GESTURE ticks (a hit breaks it and halves the bar); then the fourth stage is ready: letting go standing throws
//     the World Cutting Slash, crouched the waffle (WorldSlash). It empties the bar.
public final class Desmantelar {
    // Ticks: the cut leaves the hand; holding this long means the chant; between combo taps; a word; the gesture; the
    // world cut leaves the hand after the release; the longest a stage is held before it goes by itself.
    private static final int SPAWN = 3, DECIDE = 5, WINDOW = 20, WORD = 10, GESTURE = 20, WORLD_RELEASE = 6, MAX_HOLD = 240;
    private static final double SPEED = 3, RANGE = 36;
    private static final float DAMAGE = 30;
    private static final double[] SCALE = {1, 1.75, 2.5, 3.5}, THROW = {1, 1, 1.6, 2.2};
    private static final int COMBO_COOLDOWN = 50, WORLD_COOLDOWN = 400, BROKEN_COOLDOWN = 30;
    private static final int[] CHANT_COOLDOWN = {40, 60, 80, 100};
    // The preview's two kinds: unit (blocks per crescent unit) and the height of its middle, for a 1.8-block-tall body.
    private static final double PE_UNIT = 1.75, PE_Y = 1.55, DE_UNIT = 2.0, DE_Y = 1.0;
    // The 3rd tap's burst (the preview's CB_CUTS): lying?, offset to his left, roll about the flight, size, height offset,
    // ticks after the first.
    private static final double[][] BURST = {
            {0, -1.3, 0, 1.05, 0, 0}, {1, 0, 0, 1.1, 0.85, 1}, {0, 1.3, 0.78, 1.0, 0, 1},
            {0, -0.4, -0.8, 1.1, 0, 2}, {1, 0, 0.15, 0.95, -0.3, 2}, {0, 0.5, 0, 1.2, 0, 3}};
    private static final String[] WORDS = {"canto1", "canto2", "canto3"};

    private static final class State {
        int step;
        long windowUntil = -1;
        boolean busy;
        int token;
    }

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

    private Desmantelar() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        State st = STATES.computeIfAbsent(caster, k -> new State());
        if (st.busy) {
            return false;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        long now = level.getGameTime();
        int step = now <= st.windowUntil ? st.step : 0;
        st.busy = true;
        int token = ++st.token;
        int interrupts = data.interrupts;
        data.busyTicks = Math.max(data.busyTicks, DECIDE + 1);
        anim(caster, "soco.combo" + (step + 1));
        int[] chant = {-1, 0};
        boolean[] gesture = {false, false};
        Tasks.add(age -> {
            if (!caster.isAlive() || caster.isRemoved() || data.interrupts != interrupts || Freeze.isFrozen(caster)
                    || !Sukuna.ID.equals(caster.getData(ModAttachments.POWER).power)) {
                broken(caster, st, chant[0] >= 0);
                return true;
            }
            boolean held = holding(caster);
            if (chant[0] < 0) {
                if (step < 2 && held && age >= DECIDE) {
                    chant[0] = age;
                    chant[1] = caster.hurtTime;
                    anim(caster, "sukuna.canto");
                    return false;
                }
                if (age >= SPAWN && (!held || step == 2)) {
                    tap(caster, step);
                    st.busy = false;
                    data.busyTicks = Math.max(data.busyTicks, 2);
                    if (step < 2) {
                        st.step = step + 1;
                        st.windowUntil = level.getGameTime() + WINDOW;
                        int mine = token;
                        Tasks.later(WINDOW + 1, () -> {
                            if (st.token == mine && level.getGameTime() > st.windowUntil) {
                                cooldown(caster, COMBO_COOLDOWN);
                            }
                        });
                    } else {
                        st.windowUntil = -1;
                        cooldown(caster, COMBO_COOLDOWN);
                    }
                    return true;
                }
                return false;
            }
            // the chant
            int ct = age - chant[0];
            caster.addEffect(new MobEffectInstance(ModEffects.ENRAIZADO, 3, 0, false, false, false));
            data.busyTicks = Math.max(data.busyTicks, 3);
            if (caster.hurtTime > chant[1]) {
                // hit: the chant breaks (with the bar full, it drops to half)
                broken(caster, st, true);
                return true;
            }
            chant[1] = caster.hurtTime;
            for (int k = 0; k < 3; k++) {
                if (ct == k * WORD + 1) {
                    word(caster, k);
                }
            }
            int stage = Math.min(3, ct / WORD);
            if (ct == 3 * WORD && ModelBar.full(caster)) {
                gesture[0] = true;
                anim(caster, "sukuna.canto_gesto");
                Fx.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.5f);
            }
            if (gesture[0] && ct == 3 * WORD + GESTURE) {
                gesture[1] = true;
                SukunaCuts.star(caster, hand(caster, 0.15, 1.3, 0.45), 2.6f);
                Fx.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.4f, 0.6f);
                Fx.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.5f);
            }
            double total = gesture[0] ? 3 * WORD + GESTURE : 3 * WORD;
            Charge.report(caster, Math.min(1, ct / total));
            if (held && ct < MAX_HOLD) {
                return false;
            }
            if (gesture[1]) {
                world(caster, st, interrupts);
            } else {
                release(caster, st, stage);
            }
            return true;
        });
        return true;
    }

    // Holding the skill key (players only: a bot taps).
    private static boolean holding(LivingEntity caster) {
        return caster instanceof ServerPlayer && caster.getData(ModAttachments.POWER).abilityHeld;
    }

    private static void word(LivingEntity caster, int k) {
        ServerLevel level = Combat.level(caster);
        BossFalas.line(caster, "fala.battlegroundsmod.sukuna." + WORDS[k], BossFalas.colors(Sukuna.ID));
        SukunaCuts.star(caster, hand(caster, -0.25, 1.55 + 0.08 * k, 0.45), (float) (0.8 + 0.35 * k));
        Fx.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.6f + 0.15f * k);
        Fx.sound(level, caster.position(), SoundEvents.TRIDENT_RETURN, 0.8f, 0.6f + 0.1f * k);
    }

    // A combo tap's cut(s).
    private static void tap(LivingEntity caster, int step) {
        LivingEntity target = target(caster);
        if (step == 0) {
            cut(caster, target, false, 0, 0, 1, 0, DAMAGE, 0.25, 0.05, 14, 1f);
        } else if (step == 1) {
            cut(caster, target, true, 0, 0, 1, 0, DAMAGE, 0.25, 0.05, 14, 1f);
        } else {
            PowerData data = caster.getData(ModAttachments.POWER);
            int interrupts = data.interrupts;
            float each = DAMAGE * 1.8f / BURST.length;
            for (double[] b : BURST) {
                Runnable go = () -> {
                    if (caster.isAlive() && data.interrupts == interrupts) {
                        LivingEntity aim = target != null && target.isAlive() ? target : null;
                        cut(caster, aim, b[0] > 0.5, b[1], b[2], b[3], b[4], each, 0.95, 0.42, 12, 0.35f);
                    }
                };
                if (b[5] <= 0) {
                    go.run();
                } else {
                    Tasks.later((int) b[5], go);
                }
            }
        }
    }

    // Let go of the chant at this stage (0: before the first word ended, a plain cut).
    private static void release(LivingEntity caster, State st, int stage) {
        PowerData data = caster.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        boolean lying = caster.isCrouching();
        anim(caster, "soco.forte_golpe");
        data.busyTicks = Math.max(data.busyTicks, SPAWN + 2);
        st.windowUntil = -1;
        Tasks.later(SPAWN, () -> {
            st.busy = false;
            if (!caster.isAlive() || data.interrupts != interrupts) {
                cooldown(caster, BROKEN_COOLDOWN);
                return;
            }
            LivingEntity target = target(caster);
            double sc = SCALE[stage];
            double knock = 0.95 * THROW[stage], up = 0.35 + 0.1 * THROW[stage];
            int stun = stage >= 2 ? 26 : 14;
            if (stage == 3) {
                // Paired Falling Stars: two crescents crossed in an X
                cut(caster, target, false, 0, 0.42, sc, 0, DAMAGE * (float) sc / 2, knock, up, stun, 1f);
                cut(caster, target, false, 0, -0.42, sc, 0, DAMAGE * (float) sc / 2, knock, up, stun, 1f);
            } else {
                cut(caster, target, lying, 0, 0, sc, 0, DAMAGE * (float) sc, knock, up, stun, 1f);
            }
            cooldown(caster, CHANT_COOLDOWN[stage]);
        });
    }

    // The fourth stage: the World Cutting Slash (standing) or the waffle (crouched).
    private static void world(LivingEntity caster, State st, int interrupts) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean crouched = caster.isCrouching();
        anim(caster, crouched ? "sukuna.waffle" : "sukuna.corte_mundo");
        data.busyTicks = Math.max(data.busyTicks, WORLD_RELEASE + 4);
        st.windowUntil = -1;
        int[] hurt = {caster.hurtTime};
        Tasks.add(age -> {
            caster.addEffect(new MobEffectInstance(ModEffects.ENRAIZADO, 3, 0, false, false, false));
            if (!caster.isAlive() || data.interrupts != interrupts || caster.hurtTime > hurt[0]) {
                broken(caster, st, true);
                return true;
            }
            hurt[0] = caster.hurtTime;
            if (age < WORLD_RELEASE) {
                return false;
            }
            st.busy = false;
            Vec3 dir = aim(caster);
            if (crouched) {
                WorldSlash.waffle(caster, hand(caster, -0.3, 1.0, 0.8), dir);
            } else {
                // standing: level, a little up or down with the aim (never into the ground under him)
                double pitch = Math.max(-0.26, Math.min(0.5, Math.asin(Math.max(-1, Math.min(1, dir.y)))));
                Vec3 flat = Combat.flatLook(caster);
                Vec3 way = flat.scale(Math.cos(pitch)).add(0, Math.sin(pitch), 0);
                WorldSlash.standing(caster, hand(caster, -0.3, 1.4, 1.2), way);
            }
            ModelBar.set(caster, 0);
            cooldown(caster, WORLD_COOLDOWN);
            return true;
        });
    }

    // Cut off (hit, stunned, frozen): no cut; with the bar full and the chant going, the bar drops to half.
    private static void broken(LivingEntity caster, State st, boolean chanting) {
        st.busy = false;
        st.windowUntil = -1;
        caster.removeEffect(ModEffects.ENRAIZADO);
        if (chanting) {
            if (ModelBar.full(caster)) {
                ModelBar.set(caster, ModelBar.get(caster) / 2);
            }
            anim(caster, "");
            cooldown(caster, BROKEN_COOLDOWN);
        }
    }

    // One crescent: lying or standing, offset to his left (blocks), rolled about its flight, scaled; its damage, how it
    // throws, its stun, and how much it gives the Modelo bar.
    private static void cut(LivingEntity caster, LivingEntity target, boolean lying, double left, double roll, double sc, double yo, float damage,
            double knock, double knockUp, int stun, float weight) {
        double body = caster.getBbHeight() / 1.8;
        Vec3 fwd = Combat.flatLook(caster);
        Vec3 leftV = new Vec3(fwd.z, 0, -fwd.x);
        double y = (lying ? DE_Y : PE_Y * (1 + (sc - 1) * 0.7)) * body + yo * body;
        Vec3 origin = caster.position().add(fwd.scale(1.1 * body)).add(leftV.scale(left * body)).add(0, y, 0);
        Vec3 dir;
        double reach;
        if (target != null) {
            Vec3 to = Combat.center(target).subtract(origin);
            dir = to.lengthSqr() < 1.0E-4 ? caster.getLookAngle() : to.normalize();
            reach = Math.min(RANGE + 4, to.length() + 1.8);
        } else {
            dir = caster.getLookAngle();
            reach = RANGE;
        }
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 across = lying ? dir.cross(up) : up.subtract(dir.scale(dir.y));
        if (across.lengthSqr() < 1.0E-4) {
            across = leftV;
        }
        across = across.normalize();
        if (roll != 0) {
            Vec3 perp = dir.cross(across).normalize();
            across = across.scale(Math.cos(roll)).add(perp.scale(Math.sin(roll)));
        }
        int flags = !lying && roll == 0 && caster.onGround() && Math.abs(dir.y) < 0.35 ? SukunaCuts.GROOVE : 0;
        SukunaCuts.fire(caster, new SukunaCuts.Throw(origin, dir, across, (lying ? DE_UNIT : PE_UNIT) * sc * body, SPEED, reach, damage, knock, knockUp,
                stun, flags, weight, 2.2 * sc));
    }

    // Whoever the cut goes for: in the crosshair, else the one he is fighting if in front of him.
    private static LivingEntity target(LivingEntity caster) {
        LivingEntity target = Combat.raycastTarget(caster, RANGE);
        if (target != null) {
            return target;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        LivingEntity other = null;
        if (data.foughtId >= 0 && caster.level().getEntity(data.foughtId) instanceof LivingEntity fought) {
            other = fought;
        } else if (caster instanceof Mob mob) {
            other = mob.getTarget();
        }
        if (other == null || !Combat.isTarget(other, caster) || other.distanceTo(caster) > RANGE) {
            return null;
        }
        Vec3 to = Combat.center(other).subtract(caster.getEyePosition()).normalize();
        return to.dot(caster.getLookAngle()) > 0.75 ? other : null;
    }

    private static Vec3 aim(LivingEntity caster) {
        LivingEntity target = target(caster);
        return target != null ? Combat.center(target).subtract(caster.getEyePosition()).normalize() : caster.getLookAngle();
    }

    // A point in his own frame: x to his left, y up, z ahead (blocks, for a 1.8-block-tall body).
    private static Vec3 hand(LivingEntity caster, double x, double y, double z) {
        double body = caster.getBbHeight() / 1.8;
        Vec3 fwd = Combat.flatLook(caster);
        Vec3 left = new Vec3(fwd.z, 0, -fwd.x);
        return caster.position().add(left.scale(x * body)).add(0, y * body, 0).add(fwd.scale(z * body));
    }

    private static void cooldown(LivingEntity caster, int ticks) {
        PowerData data = caster.getData(ModAttachments.POWER);
        if (data.noCooldown) {
            return;
        }
        data.cooldowns[0] = Math.max(data.cooldowns[0], ticks);
        if (caster instanceof ServerPlayer player) {
            PowerLogic.sync(player);
        }
    }

    private static void anim(LivingEntity entity, String move) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.PlaySkillAnimation(entity.getId(), move, false));
    }
}
