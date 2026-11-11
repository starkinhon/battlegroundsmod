package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// Shared bits of the Jujutsu SubAlmas (Itadori, Choso, Nanami, Nobara, Todo, Inumaki, Miwa): the timed states, the buffs
// on the base fighting (Red Scale, Overtime), how long someone has been fighting (Nanami's Overtime), the craters with
// flying blocks, and the defenses checked on every hit (the Manji Kick's guard, Supernova's orbs, the sleeper woken with a
// critical, the fake clap's opening, the blood Nobara's Resonance follows).
public final class JjCommon {
    // Itadori's Manji Kick guard is up.
    public static final String MANJI = "jj_manji";
    // Itadori in the zone after a Black Flash: the window doubles.
    public static final String ZONE = "jj_zone";
    // Choso's Red Scale.
    public static final String SCALE = "jj_scale";
    // Bound by Choso's blood rope.
    public static final String BOUND = "jj_bound";
    // Marked with Nanami's 7:3 lines.
    public static final String RATIO = "jj_ratio";
    // Nanami's Overtime.
    public static final String OVERTIME = "jj_overtime";
    // Hurt by Nobara a moment ago: her Resonance can follow the blood.
    public static final String NOBARA_BLOOD = "jj_nobara_blood";
    // Asleep from Inumaki's "Sleep": the next hit is a critical and wakes.
    public static final String ASLEEP = "jj_asleep";
    // Turned round by Todo's fake clap: the next hit is a critical.
    public static final String OPEN = "jj_open";
    // Inside Miwa's Simple Domain.
    public static final String SIMPLE_DOMAIN = "jj_simple_domain";

    // How long someone has been fighting: when it started, and the last hit given or taken (15 s of quiet ends it).
    private static final Map<UUID, long[]> FIGHT = new HashMap<>();
    private static final int FIGHT_GAP = 300;

    private JjCommon() {
    }

    public static boolean isBoss(LivingEntity e) {
        return e instanceof SoulBoss b && b.isBoss();
    }

    // How much a control lasts on someone: full on someone of the same tier or below, less on stronger ones and bosses.
    public static int control(LivingEntity caster, LivingEntity target, int ticks) {
        double k = 1;
        int gap = Tiers.of(caster) - Tiers.of(target);
        if (gap > 0) {
            k *= Math.max(0.35, 1 - 0.25 * gap);
        }
        if (isBoss(target)) {
            k *= 0.5;
        }
        return Math.max(4, (int) Math.round(ticks * k));
    }

    // A move that missed: its cooldown comes down to ticks (never up).
    public static void shorten(LivingEntity caster, int slot, int ticks) {
        PowerData data = caster.getData(ModAttachments.POWER);
        data.cooldowns[slot] = Math.min(data.cooldowns[slot], ticks);
        if (caster instanceof net.minecraft.server.level.ServerPlayer player) {
            com.starkinhon.battlegroundsmod.power.PowerLogic.sync(player);
        }
    }

    // Someone carried along a path: kept at at[0] each tick (the move's task moves it), for ticks.
    public static void carry(LivingEntity e, int ticks, Vec3[] at) {
        Freeze.hold(e, ticks, age -> at[0]);
    }

    // The point in front of someone's chest where a punch lands on a target.
    public static Vec3 contact(LivingEntity caster, LivingEntity target) {
        Vec3 c = Combat.center(target);
        Vec3 to = c.subtract(caster.getEyePosition());
        return c.subtract(to.normalize().scale(target.getBbWidth() * 0.5));
    }

    public static String power(LivingEntity e) {
        PowerData data = e.getData(ModAttachments.POWER);
        return data.power;
    }

    // ---------- the base fighting under a buff ----------

    public static float meleeMultiplier(LivingEntity attacker) {
        float k = 1;
        if (SubCommon.hasState(attacker, SCALE)) {
            k *= 1.5f;
        }
        if (SubCommon.hasState(attacker, OVERTIME)) {
            k *= 1.6f;
        }
        return k;
    }

    // Every physical hit (not magic): Overtime makes them all heavier.
    public static float damageMultiplier(LivingEntity attacker) {
        return SubCommon.hasState(attacker, OVERTIME) ? 1.4f : 1;
    }

    // ---------- the fight clock (Overtime) ----------

    private static void fought(LivingEntity e, long now) {
        long[] f = FIGHT.get(e.getUUID());
        if (f == null || now - f[1] > FIGHT_GAP) {
            FIGHT.put(e.getUUID(), new long[] {now, now});
        } else {
            f[1] = now;
        }
    }

    // Ticks someone has been in this fight (0 out of one).
    public static long fighting(LivingEntity e) {
        long[] f = FIGHT.get(e.getUUID());
        long now = e.level().getGameTime();
        if (f == null || now - f[1] > FIGHT_GAP) {
            return 0;
        }
        return now - f[0];
    }

    // ---------- breaking the ground ----------

    // A bowl broken where a blow came down; some of the blocks fly up and out (no drops). Gamerule soulsBlockDestruction.
    public static void crater(ServerLevel level, Vec3 center, double r, double deep, int flyMax) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Mth.floor(center.y) - 1;
        int[] flown = {0};
        for (int x = Mth.floor(center.x - r); x <= Mth.floor(center.x + r); x++) {
            for (int z = Mth.floor(center.z - r); z <= Mth.floor(center.z + r); z++) {
                double dx = (x + 0.5 - center.x) / r;
                double dz = (z + 0.5 - center.z) / r;
                for (int y = top + 1; y >= top - Mth.ceil(deep); y--) {
                    double dy = y > top ? 0 : (top + 0.5 - (y + 0.5)) / Math.max(0.5, deep);
                    if (dx * dx + dz * dz + dy * dy > 0.75 + 0.35 * SubCommon.RANDOM.nextDouble()) {
                        continue;
                    }
                    pos.set(x, y, z);
                    breakOne(level, pos, center, flown, flyMax, 0.45);
                }
            }
        }
    }

    // Breaks the blocks in a ball round a point (a body charging through a wall).
    public static void carve(ServerLevel level, Vec3 at, double r, Vec3 from) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] flown = {0};
        for (int x = Mth.floor(at.x - r); x <= Mth.floor(at.x + r); x++) {
            for (int y = Mth.floor(at.y - r); y <= Mth.floor(at.y + r); y++) {
                for (int z = Mth.floor(at.z - r); z <= Mth.floor(at.z + r); z++) {
                    if (new Vec3(x + 0.5, y + 0.5, z + 0.5).distanceTo(at) > r) {
                        continue;
                    }
                    pos.set(x, y, z);
                    breakOne(level, pos, from, flown, 4, 0.25);
                }
            }
        }
    }

    // Breaks the one block at pos (a small ki shot chipping the ground): it flies off now and then, or just goes.
    public static void breakAt(ServerLevel level, BlockPos pos, Vec3 from, boolean canFly) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        breakOne(level, pos.mutable(), from, new int[] {0}, canFly ? 1 : 0, 0.35);
    }

    private static void breakOne(ServerLevel level, BlockPos.MutableBlockPos pos, Vec3 from, int[] flown, int flyMax, double up) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
            return;
        }
        if (flown[0] < flyMax && SubCommon.RANDOM.nextInt(3) == 0) {
            flown[0]++;
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos.immutable(), state);
            block.dropItem = false;
            Vec3 out = new Vec3(pos.getX() + 0.5 - from.x, 0, pos.getZ() + 0.5 - from.z);
            out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize();
            block.setDeltaMovement(out.scale(0.2 + 0.35 * SubCommon.RANDOM.nextDouble()).add(0, up + 0.45 * SubCommon.RANDOM.nextDouble(), 0));
            block.hurtMarked = true;
        } else {
            com.starkinhon.battlegroundsmod.combat.BlockBreakFx.erase(level, pos, state, 3);
        }
    }

    // ---------- every hit ----------

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        if (!(target.level() instanceof ServerLevel level) || attacker == target || event.getAmount() <= 0) {
            return;
        }
        long now = level.getGameTime();
        if (attacker instanceof LivingEntity enemy) {
            fought(target, now);
            fought(enemy, now);
        }
        // Itadori's Manji Kick: a blow from the front is caught and answered with the kick.
        if (attacker instanceof LivingEntity enemy && SubCommon.hasState(target, MANJI)) {
            Vec3 to = SubCommon.flat(enemy.position().subtract(target.position()));
            boolean front = to.dot(SubCommon.flat(target.getLookAngle())) > 0.1;
            if (front && (attacker == direct || direct instanceof Projectile) && enemy.distanceTo(target) < 8) {
                event.setCanceled(true);
                SubCommon.clearState(target, MANJI);
                Itadori.counter(target, level, enemy);
                return;
            }
        }
        // Choso's Supernova: an orb takes the hit (a punch or a shot) and bursts.
        if (Choso.orbTakes(target, level, direct != null ? direct : attacker)) {
            event.setCanceled(true);
            return;
        }
        // Asleep: the hit is a critical and wakes.
        if (SubCommon.hasState(target, ASLEEP)) {
            SubCommon.clearState(target, ASLEEP);
            Freeze.free(target);
            event.setAmount(event.getAmount() * 1.8f);
            SubNet.send(level, SubNet.SLEEP, target.getId(), target.position(), Vec3.ZERO, 0, 0, 0);
            DbCommon.hit(level, Combat.center(target), 1, com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.WHITE);
            SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.8f);
        }
        // Turned round by the fake clap: the hit is a critical.
        if (attacker != null && SubCommon.hasState(target, OPEN)) {
            SubCommon.clearState(target, OPEN);
            event.setAmount(event.getAmount() * 1.6f);
            SubCommon.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.7f);
        }
        // Nanami's first hit in Overtime cracks the ground.
        if (attacker instanceof LivingEntity enemy && SubCommon.hasState(enemy, Nanami.OVERTIME_FIRST)) {
            Nanami.firstHit(enemy, level, target);
        }
        // Nobara drew blood: her Resonance can follow it for 10 s.
        if (attacker instanceof LivingEntity enemy && "nobara".equals(power(enemy))) {
            SubCommon.setState(target, NOBARA_BLOOD, 200);
        }
        // Itadori's Ritmo: every blow he lands fills it.
        if (attacker instanceof LivingEntity enemy && "itadori".equals(power(enemy))) {
            Itadori.landed(enemy);
        }
    }

    // Is someone inside Miwa's Simple Domain (the Infinite Void spares them)?
    public static boolean inSimpleDomain(LivingEntity e) {
        return SubCommon.hasState(e, SIMPLE_DOMAIN);
    }

    // A mob's target, or the one in the aim, or the closest in front.
    public static LivingEntity target(LivingEntity caster, double range) {
        LivingEntity t = SubCommon.aimed(caster, range);
        if (t != null) {
            return t;
        }
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive() && mob.distanceTo(mob.getTarget()) <= range) {
            return mob.getTarget();
        }
        LivingEntity best = null;
        double bestD = range;
        for (LivingEntity e : Combat.inCone(caster, range, 0.8)) {
            double d = e.distanceTo(caster);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    public static void clear() {
        FIGHT.clear();
        Itadori.clear();
        Nanami.clear();
        Miwa.clear();
        Choso.clear();
        Nobara.clear();
        Inumaki.clear();
    }
}
