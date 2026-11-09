package com.starkinhon.battlegroundsmod.boss;

import java.util.ArrayDeque;
import java.util.Deque;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Susanoo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// What every boss with tactics reads of the fight (GojoTactics, MadaraTactics, BillsTactics), once a tick: how far the
// target is, whether he sees them, whether they are in the air, running away, holding the Susanoo counter, how many are
// fighting him and how many stand next to the target, whether the world can be broken (gamerule soulsBlockDestruction),
// WHO the target is (Madara, Bills, Aizen, Gojo, a brawler, another power, a plain mob or player), and which of the
// target's own moves are ready (their cooldowns: a Bills whose hakaizinho is recharging can't shoot a meteor down, a
// Madara whose Susanoo is recharging can be punched). Also the blink away every one of them may use.
abstract class TacticsBase implements BossTactics {
    protected enum Kind { MADARA, BILLS, AIZEN, GOJO, LUTADOR, PODER, COMUM }

    protected final SoulBoss boss;
    protected final RandomSource random;

    protected int blinkCooldown;
    private final Deque<Double> distances = new ArrayDeque<>();

    // The read of this tick.
    protected long readAt = -1;
    protected double distance;
    protected double myLife;
    protected double theirLife;
    protected int enemies;
    protected int crowd;
    protected boolean air;
    protected boolean fleeing;
    protected boolean guarded;
    protected boolean breaks;
    protected boolean sees;
    protected boolean strongFoe;
    protected Kind kind = Kind.COMUM;

    protected TacticsBase(SoulBoss boss) {
        this.boss = boss;
        this.random = boss.getRandom();
    }

    // ---------------------------------------------------------------- reading the fight

    protected void read(SoulBossAI ai) {
        LivingEntity target = ai.target();
        if (target == null || readAt == ai.now()) {
            return;
        }
        readAt = ai.now();
        ServerLevel level = Combat.level(boss);
        distance = boss.distanceTo(target);
        myLife = life(boss);
        theirLife = life(target);
        sees = boss.hasLineOfSight(target);
        guarded = Susanoo.isReady(target);
        breaks = BlockRules.canBreak(level);
        kind = kindOf(target);
        strongFoe = target instanceof SoulBoss || kind == Kind.BILLS || kind == Kind.GOJO
                || target.hasData(ModAttachments.POWER) && target.getData(ModAttachments.POWER).hasPower()
                        && target.getData(ModAttachments.POWER).tier <= 5;
        // In the air: nothing under their feet for 2.5 blocks. Up in Gojo's domain there are no blocks at all, only its
        // floor: there it is 2.5 blocks above the floor (before, everyone in there read as in the air and the Gojo boss
        // kept throwing the Blue to bring them down).
        Vec3 feet = target.position();
        double floor = InfiniteVoid.floorY(target);
        air = !Double.isNaN(floor) ? feet.y - floor > 2.5
                : !target.onGround() && level.clip(new ClipContext(feet, feet.add(0, -2.5, 0), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.ANY, target)).getType() == HitResult.Type.MISS;
        // Who is fighting him, and who stands next to the target (a line or a ring catches them too).
        enemies = 0;
        crowd = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(24), x -> x != boss && x.isAlive())) {
            if (!foe(e)) {
                continue;
            }
            enemies++;
            if (e != target && e.distanceTo(target) <= 5) {
                crowd++;
            }
        }
        // Running away: farther every half second for a second and a half.
        if (ai.now() % 10 == 0) {
            distances.addLast(distance);
            while (distances.size() > 4) {
                distances.pollFirst();
            }
        }
        fleeing = distance > 10 && distances.size() == 4 && distances.peekLast() - distances.peekFirst() > 4;
    }

    protected boolean foe(LivingEntity e) {
        if (e instanceof Player player) {
            return !player.isSpectator() && !player.isCreative();
        }
        if (e instanceof SoulBoss other) {
            return other.getType() != boss.getType();
        }
        return e instanceof Mob mob && mob.getTarget() == boss;
    }

    protected static Kind kindOf(LivingEntity target) {
        String power = target instanceof SoulBoss other ? other.spec().poder()
                : target.hasData(ModAttachments.POWER) ? target.getData(ModAttachments.POWER).power : "";
        return switch (power == null ? "" : power) {
            case "uchiha_ghost" -> Kind.MADARA;
            case "destruction_god" -> Kind.BILLS;
            case "manipulator" -> Kind.AIZEN;
            case "strongest" -> Kind.GOJO;
            case "rubber_boy", "one_punch" -> Kind.LUTADOR;
            case "" -> Kind.COMUM;
            default -> Kind.PODER;
        };
    }

    protected static double life(LivingEntity e) {
        return e.getHealth() / Math.max(1, e.getMaxHealth());
    }

    // One of the target's own moves is ready to use (off cooldown). False for someone without that power.
    protected static boolean theirReady(LivingEntity target, int slot) {
        if (target == null || !target.hasData(ModAttachments.POWER)) {
            return false;
        }
        PowerData data = target.getData(ModAttachments.POWER);
        return data.hasPower() && slot >= 0 && slot < data.cooldowns.length && data.cooldowns[slot] <= 0;
    }

    // A weighted pick among the styles (each weight already set for the situation), rarely the same one twice in a row.
    protected int pick(double[] weights, int current) {
        if (current >= 0 && current < weights.length) {
            weights[current] *= 0.4;
        }
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    // A random charge between min and max, moved by the style.
    protected double range(double min, double max, double bump) {
        double c = min + (max - min) * random.nextDouble() + bump;
        return Math.max(0.05, Math.min(1, c));
    }

    // A little noise, so even the same situation doesn't always end the same way.
    protected double jitter() {
        return 0.85 + 0.3 * random.nextDouble();
    }

    protected void coolDown() {
        if (blinkCooldown > 0) {
            blinkCooldown--;
        }
    }

    // ---------------------------------------------------------------- moving

    // A blink straight away from the target (or to a side when that is blocked), landing on the ground.
    protected boolean blinkAway(SoulBossAI ai, double far) {
        LivingEntity target = ai.target();
        if (target == null) {
            return false;
        }
        Vec3 from = boss.position();
        Vec3 away = from.subtract(target.position());
        away = new Vec3(away.x, 0, away.z);
        away = away.lengthSqr() < 1.0E-4 ? Combat.flatLook(boss).reverse() : away.normalize();
        for (double turn : new double[] {0, 40, -40, 80, -80}) {
            Vec3 dir = away.yRot((float) Math.toRadians(turn));
            for (double up : new double[] {0, 1, -1, 2, -2, 3}) {
                Vec3 spot = from.add(dir.scale(far)).add(0, up, 0);
                AABB box = boss.getBoundingBox().move(spot.subtract(from));
                if (!boss.level().noCollision(boss, box) || boss.level().noCollision(boss, box.move(0, -1.0, 0))
                        && !InfiniteVoid.onFloor(boss, spot) || !InfiniteVoid.keepsInside(boss, spot)) {
                    continue;
                }
                boss.teleportTo(spot.x, spot.y, spot.z);
                boss.getNavigation().stop();
                ai.lookAt(Combat.center(target), 360);
                Intangible.hold(boss, 3);
                BossFx.shadowStep(Combat.level(boss), from, spot, BossFx.color(boss.spec().intro().cor()));
                blinkCooldown = 120;
                return true;
            }
        }
        return false;
    }
}
