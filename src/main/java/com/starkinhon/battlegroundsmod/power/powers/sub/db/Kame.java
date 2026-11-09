package com.starkinhon.battlegroundsmod.power.powers.sub.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Mestre Kame (SubAlma of Dragon Ball, tier 7), the approved forja/kame-kit page in the Realista+Anime style.
// Z Suiken (Drunken Fist): he staggers in, hiccups and every stumble is a blow (two crooked punches, a spin, a low sweep
//   and both palms, irregular on purpose), and for 4 s 60% of the melee hits that come slip past him and he slaps back
//   a crooked palm (DbCommon.onIncomingDamage). Z again: the palm right away.
// X Max Power: he swells (1.4x bigger), the shirt bursts, the blue aura rises: punches +40%. X again (or by itself after
//   3 s): the Kamehameha Max from the hip, and he is skinny again.
// C Bankoku Bikkuri Sho: two blue lightning bolts with white cores (and branches) from the hands hold the target shaking
//   for 2.5 s, then a burst knocks it down.
// V Mafuba: a gale of green speed lines and a green tornado lift the target spinning (the screen twists round it,
//   flashing white between the cuts), the wind runs into the jar with the target shrinking inside, the lid falls and the
//   魔封波 seal sticks. Sealed 6 s: with 20% of life or less it dies sealed; with more it breaks the jar and comes out
//   with 15% less life. Kame is left on his knees with no stamina.
public final class Kame {
    public static final String MAFUBA_SEALED = "db_mafuba_sealed";
    private static final String MAX_FIRING = "db_kame_max_firing";
    private static final int SUIKEN_TICKS = 80;
    private static final int MAX_TICKS = 200;
    private static final int SEAL_TICKS = 120;
    private static final int MAX_AUTO = 92;
    private static final ResourceLocation BIG = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "db_max_power");
    private static final ResourceLocation SHRINK = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "db_mafuba_shrink");

    private Kame() {
    }

    public static Power create() {
        return new Power("kame", 0xFF9A3A, 0.95f, List.of(
                new Ability(200, 0, Kame::suiken),
                new Ability(300, 0, Kame::maxPower),
                new Ability(240, 0, Kame::shock),
                new Ability(1400, 0, Kame::mafuba)));
    }

    // ---------- Z: Suiken ----------

    private static boolean suiken(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, DbCommon.SUIKEN)) {
            // Z again: the crooked palm now, on whoever is in front.
            LivingEntity enemy = SubCommon.aimed(caster, 3.5);
            if (enemy == null) {
                for (LivingEntity t : Combat.inCone(caster, 3.5, 0.3)) {
                    enemy = t;
                    break;
                }
            }
            if (enemy == null) {
                SubCommon.warn(caster, "kame.palm_no_target");
            } else {
                palm(caster, level, enemy);
            }
            DbCommon.cooldown(caster, 0, 8);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.setState(caster, DbCommon.SUIKEN, SUIKEN_TICKS);
        SubCommon.pose(caster, "db.bebado");
        SubCommon.sound(level, caster.position(), SoundEvents.GENERIC_DRINK, 1f, 0.8f);
        LivingEntity foe = SubCommon.aimed(caster, 7);
        if (foe != null) {
            drunkCombo(caster, level, foe, guard);
        }
        Tasks.add(age -> {
            if (guard.broken() || !SubCommon.hasState(caster, DbCommon.SUIKEN)) {
                SubCommon.clearState(caster, DbCommon.SUIKEN);
                DbCommon.cooldown(caster, 0, 200);
                return true;
            }
            // Walks crooked and changes direction on his own: hard to aim at.
            if (age % 12 == 0) {
                Vec3 side = SubCommon.flat(caster.getLookAngle()).cross(new Vec3(0, 1, 0));
                double s = SubCommon.RANDOM.nextBoolean() ? 0.32 : -0.32;
                caster.setDeltaMovement(caster.getDeltaMovement().add(side.scale(s)));
                caster.hurtMarked = true;
                SubCommon.pose(caster, "db.bebado");
            }
            return false;
        });
        DbCommon.cooldown(caster, 0, 6);
        return false;
    }

    // Each blow with its own time, side, height and push (the irregular rhythm is the trick): two crooked punches, the
    // "almost fell" spin, the low sweep, and both palms with the manga frame.
    private static final int[][] DRUNK = {{19, 0, 25}, {22, 1, 20}, {38, 2, 45}, {41, 3, 30}, {55, 4, 100}};
    private static final String[] DRUNK_POSE = {"soco.forte_golpe", "soco.barragem_final", "db.palma_torta", "db.gesto_baixo", "db.empurra"};

    private static void drunkCombo(LivingEntity caster, ServerLevel level, LivingEntity foe, SubCommon.Guard guard) {
        int[] next = {0};
        SubCommon.busy(caster, 60);
        Tasks.add(age -> {
            if (guard.broken() || !foe.isAlive() || next[0] >= DRUNK.length) {
                return true;
            }
            // Staggering in: closes the gap swaying side to side.
            Vec3 to = SubCommon.flat(foe.position().subtract(caster.position()));
            double gap = to.length();
            if (gap > 1.3 && gap < 9) {
                Vec3 side = to.normalize().cross(new Vec3(0, 1, 0));
                Vec3 step = to.normalize().scale(Math.min(0.35, gap - 1.2)).add(side.scale(0.12 * Mth.sin(age * 0.36f)));
                caster.setDeltaMovement(step.x, caster.getDeltaMovement().y, step.z);
                caster.hurtMarked = true;
            }
            if (age == 6) {
                SubCommon.sound(level, caster.position(), SoundEvents.VILLAGER_AMBIENT, 0.8f, 1.6f);
            }
            int[] h = DRUNK[next[0]];
            if (age < h[0]) {
                return false;
            }
            next[0]++;
            SubCommon.pose(caster, DRUNK_POSE[h[1]]);
            if (foe.distanceTo(caster) > 3.2) {
                return false;
            }
            boolean last = h[1] == 4;
            Vec3 at = Combat.center(foe);
            Combat.hurt(caster, foe, last ? 6 : 2.5f);
            Stun.apply(foe, last ? 16 : 10);
            Combat.push(foe, foe.position().subtract(caster.position()), h[2] / 100.0 * (last ? 1.4 : 0.6), last ? 0.5 : 0.1);
            SubNet.send(level, SubNet.KI_BOOM, 0, at, new Vec3(DbCommon.groundY(level, at), 0, 0), last ? 0.55f : 0.3f, Db.WHITE, 1000 + age);
            DbCommon.hit(level, at, last ? 0.9f : 0.5f, Db.WHITE);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(foe, new ModPayloads.Hitstop(foe.getId(), last ? 3 : 1));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.Hitstop(caster.getId(), last ? 3 : 1));
            if (last) {
                DbCommon.screen(level, at, 50);
            }
            SubCommon.sound(level, foe.position(), last ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_WEAK, 1.2f, last ? 0.8f : 1.2f);
            return last;
        });
    }

    // The crooked palm (Z again, or every dodge of the Suiken).
    public static void palm(LivingEntity kame, ServerLevel level, LivingEntity enemy) {
        SubCommon.pose(kame, "db.palma_torta");
        Combat.hurt(kame, enemy, 4);
        Stun.apply(enemy, 10);
        Combat.push(enemy, enemy.position().subtract(kame.position()), 0.6, 0.15);
        DbCommon.hit(level, Combat.center(enemy), 0.5f, Db.WHITE);
        DbCommon.screen(level, Combat.center(enemy), 4);
        SubCommon.sound(level, enemy.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 1.3f);
    }

    // ---------- X: Max Power ----------

    private static boolean maxPower(LivingEntity caster, ServerLevel level) {
        if (SubCommon.hasState(caster, DbCommon.MAX_POWER)) {
            if (!SubCommon.hasState(caster, MAX_FIRING)) {
                kamehamehaMax(caster, level);
            }
            DbCommon.cooldown(caster, 1, 80);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean[] fired = {false};
        SubCommon.setState(caster, DbCommon.MAX_POWER, MAX_TICKS);
        SubCommon.pose(caster, "db.flex");
        SubCommon.busy(caster, 14);
        SubNet.send(level, SubNet.KI_AURA, caster.getId(), caster.position(), new Vec3(0.6, 0, 0), MAX_AUTO + 50, Db.KAME_W, 1.35f);
        SubNet.send(level, SubNet.FLEX, 0, caster.position(), Vec3.ZERO, 0, 0, 0);
        DbCommon.screen(level, Combat.center(caster), 8);
        SubCommon.sound(level, caster.position(), SoundEvents.RAVAGER_ROAR, 1.2f, 1.1f);
        SubCommon.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.5f, 0.6f);
        Tasks.add(age -> {
            boolean on = SubCommon.hasState(caster, DbCommon.MAX_POWER) && !guard.broken();
            // He swells over a second and goes back the same way.
            setSize(caster, on ? 0.4 * SubCommon.smooth(age / 20f) : 0);
            if (!on) {
                SubCommon.clearState(caster, DbCommon.MAX_POWER);
                DbCommon.cooldown(caster, 1, 300);
                return true;
            }
            // Not pressed again: it goes by itself after 3 s (mobs and bosses as soon as someone is in their aim).
            boolean ready = caster instanceof Player ? age >= MAX_AUTO : age >= 60 && age % 10 == 0 && SubCommon.aimed(caster, 30) != null;
            if (ready && !fired[0] && !SubCommon.hasState(caster, MAX_FIRING)) {
                fired[0] = true;
                kamehamehaMax(caster, level);
            }
            return false;
        });
        DbCommon.cooldown(caster, 1, 10);
        return false;
    }

    private static void setSize(LivingEntity caster, double extra) {
        AttributeInstance scale = caster.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        scale.removeModifier(BIG);
        if (extra > 0.001) {
            scale.addTransientModifier(new AttributeModifier(BIG, extra, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    // X again: the original Kamehameha, the strongest move of the SubAlma; the Max Power ends with it.
    private static void kamehamehaMax(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int cup = 26;
        int beamTicks = 44;
        int ball = SubNet.newId();
        int beam = SubNet.newId();
        SubCommon.setState(caster, MAX_FIRING, cup + beamTicks + 6);
        SubCommon.pose(caster, "db.concha");
        SubCommon.busy(caster, cup + beamTicks + 4);
        Map<Integer, Integer> hitAt = new HashMap<>();
        Tasks.add(age -> {
            Vec3 hm = DbCommon.hands(caster);
            if (guard.broken()) {
                SubNet.send(level, SubNet.KI_BALL, ball, hm, Vec3.ZERO, 0, Db.KAME, 1);
                SubNet.send(level, SubNet.KI_BEAM, beam, hm, Vec3.ZERO, 0, Db.KAME, 1);
                SubCommon.clearState(caster, DbCommon.MAX_POWER);
                return true;
            }
            SubCommon.busy(caster, 3);
            if (age < cup) {
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 4, false, false));
                SubNet.send(level, SubNet.KI_BALL, ball, hm, Vec3.ZERO, 0.1f + 0.32f * SubCommon.smooth(age / 22f) + 0.03f * Mth.sin(age * 2f), Db.KAME + 48, 0);
                if (age % 10 == 0) {
                    SubCommon.sound(level, hm, SoundEvents.BEACON_AMBIENT, 1.2f, 0.7f + age / 40f);
                }
                return false;
            }
            int t = age - cup;
            if (t == 0) {
                SubNet.send(level, SubNet.KI_BALL, ball, hm, Vec3.ZERO, 0, Db.KAME, 1);
                SubCommon.pose(caster, "db.empurra");
                DbCommon.screen(level, hm, 45);
                SubCommon.sound(level, hm, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 1.0f);
                SubCommon.sound(level, hm, SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.6f);
            }
            boolean end = t >= beamTicks;
            Vec3 dir = caster.getLookAngle();
            Vec3 reach = DbCommon.beamEnd(caster, level, hm, dir, 40, 1.1, false);
            Vec3 to = hm.add(reach.subtract(hm).scale(Math.min(1, t / 5.0)));
            SubNet.send(level, SubNet.KI_BEAM, beam, hm, to.subtract(hm), 1.27f + 0.11f * Mth.sin(t * 0.4f), Db.KAME, end ? 1 : 0);
            DbCommon.hurtAlong(caster, level, hm, to, 1.1, 3, age, 4, hitAt, dir.scale(0.4));
            if (t % 8 == 0) {
                SubCommon.sound(level, to, SoundEvents.FIREWORK_ROCKET_BLAST, 1.4f, 0.5f);
            }
            if (end) {
                DbCommon.boom(level, to, 3.3f, Db.KAME, 5);
                double ground = DbCommon.groundY(level, to);
                if (to.y - ground < 3) {
                    JjCommon.crater(level, new Vec3(to.x, ground, to.z), 3.2, 1.6, 24);
                }
                DbCommon.hurtRadius(caster, level, to, 3.3, 14, 1.6, 0.7);
                // Spent: skinny again.
                SubCommon.clearState(caster, DbCommon.MAX_POWER);
                return true;
            }
            return false;
        });
    }

    // ---------- C: Bankoku Bikkuri Sho ----------

    private static boolean shock(LivingEntity caster, ServerLevel level) {
        LivingEntity target = SubCommon.aimed(caster, 12);
        if (target == null) {
            SubCommon.warn(caster, "kame.shock_no_target", 12);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int aim = 6;
        int hold = 50;
        SubCommon.pose(caster, "db.empurra");
        SubCommon.busy(caster, aim + hold + 4);
        Tasks.add(age -> {
            if (age < aim) {
                return guard.broken();
            }
            int t = age - aim;
            Vec3 body = Combat.center(target);
            if (t == 0) {
                // The two bolts run to the body (drawn from Kame's hands to the target, a new path every tick).
                SubNet.send(level, SubNet.SCREEN, caster.getId(), body, new Vec3(hold, 0, 0), 54, target.getId(), 0);
                Stun.apply(target, hold + 6);
                Vec3 at = target.position();
                Freeze.hold(target, hold, a -> at.add((SubCommon.RANDOM.nextDouble() - 0.5) * 0.08, 0, (SubCommon.RANDOM.nextDouble() - 0.5) * 0.08));
                SubCommon.sound(level, body, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
            }
            if (guard.broken() || !target.isAlive()) {
                SubNet.send(level, SubNet.SCREEN, caster.getId(), body, new Vec3(0, 0, 0), 54, target.getId(), 0);
                Freeze.free(target);
                return true;
            }
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 4, false, false));
            if (t % 5 == 0) {
                Combat.hurtMagic(caster, target, 1);
            }
            if (t % 3 == 0) {
                SubCommon.sound(level, body, SoundEvents.BEE_STING, 0.6f, 0.6f + SubCommon.RANDOM.nextFloat() * 0.8f);
            }
            if (t < hold) {
                return false;
            }
            // The burst that knocks it down.
            Freeze.free(target);
            Combat.hurtMagic(caster, target, 8);
            Stun.apply(target, 20);
            Combat.push(target, target.position().subtract(caster.position()), 1.1, 0.45);
            SubNet.send(level, SubNet.KI_BOOM, 0, body, new Vec3(DbCommon.groundY(level, body), 0, 0), 0.6f, Db.KAME_W, 1000 + SubCommon.RANDOM.nextInt(100));
            DbCommon.hit(level, body, 0.9f, Db.KAME_W);
            DbCommon.screen(level, body, 46);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.Hitstop(target.getId(), 3));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.Hitstop(caster.getId(), 3));
            SubCommon.sound(level, body, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.8f);
            return true;
        });
        return true;
    }

    // ---------- V: Mafuba ----------

    private static void shrink(LivingEntity target, double k) {
        AttributeInstance scale = target.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        scale.removeModifier(SHRINK);
        if (k > 0.001) {
            scale.addTransientModifier(new AttributeModifier(SHRINK, -Math.min(0.97, k), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, double t) {
        return a.lerp(b, t).lerp(b.lerp(c, t), t);
    }

    private static boolean mafuba(LivingEntity caster, ServerLevel level) {
        LivingEntity target = SubCommon.aimed(caster, 14);
        if (target == null) {
            SubCommon.warn(caster, "kame.mafuba_no_target", 14);
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        boolean boss = target instanceof SoulBoss b && b.isBoss();
        int sealTicks = boss ? SEAL_TICKS * 2 / 3 : SEAL_TICKS;
        // Stance, gale (the tornado grows and lifts it), the swirl in the air, into the jar, the lid.
        int stance = 18;
        int lift = 70;
        int pot = 102;
        int shut = 132;
        int kneel = 60;
        Vec3 look = SubCommon.flat(caster.getLookAngle());
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        Vec3 jar = SubCommon.ground(level, caster.position().add(look.scale(2.4)).add(right.scale(1.6)));
        Vec3 mouth = jar.add(0, 0.8, 0);
        Vec3 start = target.position();
        Vec3 air = start.add(0, 2.6, 0);
        Vec3 mid = air.lerp(mouth, 0.5).add(right.scale(0.5)).add(0, 2.4, 0);
        double floor = SubCommon.ground(level, start).y;
        int jarId = SubNet.newId();
        int gustId = SubNet.newId();
        int streamId = SubNet.newId();
        int swirlId = SubNet.newId();
        int funnelId = SubNet.newId();
        SubCommon.pose(caster, "db.mafuba");
        SubCommon.busy(caster, shut);
        Freeze.holdLookFree(caster, shut);
        SubNet.send(level, SubNet.JAR, jarId, jar, Vec3.ZERO, 0, 0, 0);
        SubCommon.sound(level, jar, SoundEvents.DECORATED_POT_PLACE, 1.2f, 0.8f);
        Stun.apply(target, shut + 4);
        PowerLogic.seal(target, shut + sealTicks);
        Freeze.hold(target, shut, a -> {
            if (a < stance + 15) {
                return start;
            }
            if (a < lift) {
                return start.lerp(air, SubCommon.smooth((a - stance - 15) / (float) (lift - stance - 15)));
            }
            if (a < pot) {
                return air.add(Math.sin(a * 0.15) * 0.2, Math.sin(a * 0.25) * 0.15, 0);
            }
            float k = SubCommon.smooth(Math.min(1, (a - pot) / (float) (shut - pot - 2)));
            return bezier(air, mid, mouth.add(0, 0.25, 0), k).subtract(0, 0.9 * (1 - 0.97 * k), 0);
        });
        boolean[] done = {false};
        Tasks.add(age -> {
            if (done[0]) {
                return true;
            }
            if (guard.broken() && age < shut) {
                // Kame was cut off before the lid shut: the target drops free.
                SubNet.send(level, SubNet.GALE, gustId, DbCommon.hands(caster), Combat.center(target), 0, 0, 1);
                SubNet.send(level, SubNet.GALE, streamId, mouth, Combat.center(target), 0, 1, 1);
                SubNet.send(level, SubNet.SPIRAL, swirlId, Combat.center(target), Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.TORNADO, funnelId, start, Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, SubNet.JAR, jarId, jar, Vec3.ZERO, 1, 0, 1);
                shrink(target, 0);
                Freeze.free(target);
                return true;
            }
            if (!target.isAlive()) {
                SubNet.send(level, SubNet.JAR, jarId, jar, Vec3.ZERO, 1, 0, 1);
                shrink(target, 0);
                return true;
            }
            Vec3 hm = DbCommon.hands(caster);
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (age == stance) {
                SubCommon.sound(level, hm, SoundEvents.ELYTRA_FLYING, 1.2f, 1.2f);
            }
            // The white flash between the cuts, as in the reference.
            if (age == lift || age == pot) {
                DbCommon.screen(level, c, 55);
            }
            float k = Mth.clamp((age - stance) / (float) (lift - stance), 0, 1);
            if (age >= stance && age <= pot) {
                float aw = SubCommon.smooth((age - stance) / 6f);
                SubNet.send(level, SubNet.GALE, gustId, hm, c, aw, 0, age >= pot ? 1 : 0);
                if (age % 6 == 0) {
                    SubCommon.sound(level, c, SoundEvents.ELYTRA_FLYING, 0.8f, 1.4f);
                }
            }
            // The tornado round the target: it grows, stays while it spins in the air, then runs over to the jar.
            if (age >= stance + 8 && age <= shut) {
                float grow = SubCommon.smooth((k - 0.15f) / 0.6f);
                float e = age < pot ? 0 : SubCommon.smooth((age - pot) / (float) (shut - pot));
                Vec3 foot = new Vec3(start.x, floor, start.z).lerp(new Vec3(jar.x, jar.y, jar.z), e * 0.6);
                SubNet.send(level, SubNet.TORNADO, funnelId, foot, Vec3.ZERO, 5.5f * grow * (1 - e), SubCommon.smooth((k - 0.15f) / 0.3f) * (1 - e), age >= shut ? 1 : 0);
            }
            // The screen twisting round it.
            if (age >= stance && age <= shut) {
                float amt;
                float rad;
                if (age < lift) {
                    amt = 0.9f * k;
                    rad = 2.6f;
                } else if (age < pot) {
                    amt = 1.15f;
                    rad = 3.4f;
                } else {
                    float q = (age - pot) / (float) (shut - pot);
                    amt = 1 - 0.7f * q;
                    rad = 2.2f * (1 - 0.6f * q);
                }
                SubNet.send(level, SubNet.SPIRAL, swirlId, c, Vec3.ZERO, rad, amt, age >= shut ? 1 : 0);
            }
            // Into the jar: a river of wind from the mouth to the target, shrinking it as it goes.
            if (age >= pot && age <= shut) {
                float q = Mth.clamp((age - pot) / (float) (shut - pot - 2), 0, 1);
                shrink(target, 0.97 * SubCommon.smooth((q - 0.2f) / 0.75f));
                SubNet.send(level, SubNet.GALE, streamId, mouth, c, 1 - SubCommon.smooth((q - 0.85f) / 0.15f), 1 + q, age >= shut ? 1 : 0);
            }
            if (age < shut) {
                return false;
            }
            // The lid falls. 20% of life or less: it dies sealed. More: it will break the jar.
            done[0] = true;
            shrink(target, 0);
            boolean dies = target.getHealth() <= target.getMaxHealth() * 0.2f;
            SubNet.send(level, SubNet.JAR, jarId, jar, Vec3.ZERO, sealTicks, dies ? 0 : 1, 1);
            SubCommon.sound(level, jar, SoundEvents.DECORATED_POT_INSERT, 1.5f, 0.7f);
            Tasks.later(8, () -> {
                DbCommon.screen(level, mouth, 53);
                SubCommon.sound(level, jar, SoundEvents.ENCHANTMENT_TABLE_USE, 1.5f, 0.8f);
                SubCommon.sound(level, jar, SoundEvents.DECORATED_POT_HIT, 1.5f, 0.6f);
            });
            seal(caster, level, target, jar, sealTicks, dies);
            // The cost: on his knees with no stamina.
            SubCommon.pose(caster, "db.ajoelhado");
            Freeze.hold(caster, kneel);
            Stamina.exhaust(caster);
            return true;
        });
        return true;
    }

    private static void seal(LivingEntity caster, ServerLevel level, LivingEntity target, Vec3 jar, int sealTicks, boolean dies) {
        SubCommon.setState(target, MAFUBA_SEALED, sealTicks);
        Vec3 inside = jar.add(0, -0.2, 0);
        Freeze.hold(target, sealTicks, a -> inside);
        target.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, sealTicks, 0, false, false));
        Stun.apply(target, sealTicks);
        Tasks.later(sealTicks, () -> {
            SubCommon.clearState(target, MAFUBA_SEALED);
            target.removeEffect(MobEffects.INVISIBILITY);
            Freeze.free(target);
            if (!target.isAlive()) {
                return;
            }
            if (dies) {
                target.hurt(level.damageSources().indirectMagic(caster, caster), Float.MAX_VALUE);
                SubCommon.sound(level, jar, SoundEvents.BEACON_DEACTIVATE, 1.5f, 0.6f);
                return;
            }
            // It breaks the jar and comes out with 15% less life.
            target.teleportTo(jar.x, jar.y + 0.1, jar.z);
            target.hurt(level.damageSources().indirectMagic(caster, caster), target.getMaxHealth() * 0.15f);
            Combat.push(target, target.position().subtract(caster.position()), 0.9, 0.5);
            Stun.apply(target, 14);
            DbCommon.screen(level, jar, 20);
            SubCommon.sound(level, jar, SoundEvents.DECORATED_POT_SHATTER, 2f, 0.7f);
            SubCommon.sound(level, jar, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.3f);
        });
        if (target instanceof Player) {
            SubCommon.warn(target, "kame.sealed");
        }
    }
}
