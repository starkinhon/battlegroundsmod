package com.starkinhon.battlegroundsmod.power.powers.sub.nr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.starkinhon.battlegroundsmod.universe.NarutoGear;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// Shared bits of the Naruto SubAlmas (Naruto, Sasuke, Kakashi, Gaara, Shikamaru, Ino): the target a move goes for, the
// Sage Mode's longer punches, the paralysis, Kakashi's record of the moves used near him, and the defenses checked on
// every hit (Sasuke's reading, Gaara's sand, the meditation broken by a hit, the damage passed along Ino's link).
public final class NrCommon {
    public static final String SAGE = "nr_sage";
    public static final String MEDITATE = "nr_meditate";
    public static final String MEDITATE_BROKEN = "nr_meditate_broken";
    public static final String READ = "nr_sharingan_read";
    // Read by Sasuke's Sharingan: his Chidori goes straight at them (critical), one Senbon needle paralyzes.
    public static final String MARKED = "nr_sharingan_marked";
    // Nothing touches them (Sasuke dashing with the Chidori, sliding out of a blow read by the Sharingan).
    public static final String UNTOUCHABLE = "nr_untouchable";
    public static final String PARALYZED = "nr_paralyzed";
    public static final String COPY = "nr_copy";
    // Gaara's sand guards again 6 s after it blocked.
    private static final int SAND_GUARD_TICKS = 120;
    private static final Map<Integer, Long> SAND_GUARD = new HashMap<>();
    // Kakashi's Sharingan: the last moves anyone used, where and when.
    private static final List<Cast> CASTS = new ArrayList<>();
    // Ino's links: who is tied to whom.
    private static final List<Link> LINKS = new ArrayList<>();
    private static boolean sharing;

    public record Cast(LivingEntity caster, Ability ability, String power, int slot, Vec3 at, long time) {
    }

    public static final class Link {
        final LivingEntity owner;
        final List<LivingEntity> members;
        final long until;
        final int id;

        Link(LivingEntity owner, List<LivingEntity> members, long until) {
            this.owner = owner;
            this.members = members;
            this.until = until;
            this.id = SubNet.newId();
        }
    }

    private NrCommon() {
    }

    // The one a move goes for: the one in the aim, else the closest in front (a wide cone), up to range.
    public static LivingEntity target(LivingEntity caster, double range) {
        LivingEntity t = SubCommon.aimed(caster, range);
        if (t != null) {
            return t;
        }
        if (caster instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()
                && mob.distanceTo(mob.getTarget()) <= range) {
            return mob.getTarget();
        }
        LivingEntity best = null;
        double bestD = range;
        for (LivingEntity e : Combat.inCone(caster, range, 0.75)) {
            double d = e.distanceTo(caster);
            if (d < bestD && Combat.lockable(e)) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    // Moves someone to pos looking along yaw (players by teleport, so the client follows).
    public static void teleport(LivingEntity e, Vec3 pos, float yaw) {
        if (e instanceof net.minecraft.server.level.ServerPlayer player) {
            player.teleportTo(player.serverLevel(), pos.x, pos.y, pos.z, yaw, player.getXRot());
        } else {
            e.moveTo(pos.x, pos.y, pos.z, yaw, e.getXRot());
            e.setYHeadRot(yaw);
            e.setYBodyRot(yaw);
        }
    }

    public static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z)));
    }

    // ---------- the base fighting in Sage Mode (Kawazu Kumite) ----------

    public static double extraReach(LivingEntity attacker) {
        return SubCommon.hasState(attacker, SAGE) ? 2 : 0;
    }

    public static float meleeMultiplier(LivingEntity attacker) {
        return SubCommon.hasState(attacker, SAGE) ? 1.3f : 1;
    }

    // Every hit of the mod: the copied move hits 20% harder.
    public static float damageMultiplier(LivingEntity attacker) {
        return (SubCommon.hasState(attacker, COPY) ? 1.2f : 1) * NarutoGear.kusanagiBoost(attacker);
    }

    // A punch landed in Sage Mode: the wave in the air shows it reached farther than the arm.
    public static void onPunchLanded(LivingEntity attacker, LivingEntity hit) {
        if (!SubCommon.hasState(attacker, SAGE) || !(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 fist = DbCommon.hand(attacker);
        Vec3 c = Combat.center(hit);
        SubNet.send(level, SubNet.KAWAZU, 0, fist, c.subtract(fist), 0, 0, 0);
        DbCommon.hit(level, c, 0.8f, com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.ORANGE);
    }

    // ---------- paralysis (Chidori Senbon, the hound's bite) ----------

    public static void paralyze(ServerLevel level, LivingEntity t, int ticks) {
        SubCommon.setState(t, PARALYZED, ticks);
        Freeze.hold(t, ticks);
        PowerLogic.seal(t, ticks);
        // A real stun the whole length, boss or not (Stark, 2026-11-09: the Senbon has to stun a lot): it opens the
        // stun-only moves.
        Stun.apply(t, ticks, true);
        SubNet.send(level, SubNet.PARALYZE, t.getId(), t.position(), Vec3.ZERO, ticks, 0, 0);
        SubCommon.sound(level, t.position(), SoundEvents.BEEHIVE_WORK, 1f, 2f);
    }

    // ---------- Kakashi's Sharingan: what was used near him ----------

    public static void recordCast(LivingEntity caster, String power, int slot) {
        if (SubCommon.hasState(caster, COPY) && "kakashi".equals(power)) {
            return;
        }
        var p = Powers.get(power);
        if (p == null) {
            return;
        }
        long now = caster.level().getGameTime();
        CASTS.removeIf(c -> now - c.time > 200 || !c.caster.isAlive());
        CASTS.add(new Cast(caster, PowerLogic.ability(p, slot), power, slot, caster.position(), now));
        while (CASTS.size() > 40) {
            CASTS.remove(0);
        }
    }

    // The last move someone else used within range in the last 10 s that the Sharingan can copy (a Divine Soul's ult
    // can't), or null.
    public static Cast lastCast(LivingEntity kakashi, double range) {
        long now = kakashi.level().getGameTime();
        for (int i = CASTS.size() - 1; i >= 0; i--) {
            Cast c = CASTS.get(i);
            if (c.caster == kakashi || c.caster.level() != kakashi.level() || now - c.time > 200 || c.ability == null) {
                continue;
            }
            if ("kakashi".equals(c.power) || (PowerData.isUlt(c.slot) && !Powers.isSub(c.power))) {
                continue;
            }
            if (c.at.distanceTo(kakashi.position()) <= range) {
                return c;
            }
        }
        return null;
    }

    // ---------- Ino's link ----------

    public static Link link(LivingEntity owner, List<LivingEntity> members, int ticks) {
        LINKS.removeIf(l -> l.owner == owner);
        Link l = new Link(owner, new ArrayList<>(members), owner.level().getGameTime() + ticks);
        LINKS.add(l);
        return l;
    }

    public static boolean linked(Link l) {
        return LINKS.contains(l);
    }

    public static void unlink(Link l) {
        LINKS.remove(l);
    }

    // ---------- every hit ----------

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        if (!(target.level() instanceof ServerLevel level) || attacker == target) {
            return;
        }
        PowerData data = target.getData(ModAttachments.POWER);
        // Sasuke going through a blow (the Chidori's dash, the slide out of a blow he read).
        if (attacker != null && SubCommon.hasState(target, UNTOUCHABLE)) {
            event.setCanceled(true);
            return;
        }
        // Sasuke reading with the Sharingan: any blow from someone (punch, shot or skill) is read and dodged.
        if (attacker instanceof LivingEntity enemy && SubCommon.hasState(target, READ)
                && Sasuke.dodge(target, level, enemy, event.getAmount())) {
            event.setCanceled(true);
            return;
        }
        // Naruto meditating: any hit breaks it.
        if (SubCommon.hasState(target, MEDITATE) && event.getAmount() > 0) {
            SubCommon.clearState(target, MEDITATE);
            SubCommon.setState(target, MEDITATE_BROKEN, 4);
        }
        // Gaara's sand: the first shot or hit from the front every 6 s sticks in a wall of sand. A blow that is too strong
        // (an ult) breaks through.
        // The Cabaça de Areia (Stark 2026-11-12, Itens de Naruto): Gaara carrying it guards every 3 s from any side; anyone
        // else carrying it gets one blow stopped every 20 s.
        boolean gaara = "gaara".equals(data.power);
        boolean gourd = NarutoGear.hasGourd(target);
        if ((gaara || gourd) && (attacker != null || direct != null)) {
            Long last = SAND_GUARD.get(target.getId());
            long now = level.getGameTime();
            Entity from = direct != null ? direct : attacker;
            Vec3 toFrom = SubCommon.flat(from.position().subtract(target.position()));
            boolean front = gourd || direct instanceof Projectile || toFrom.dot(SubCommon.flat(target.getLookAngle())) > -0.2;
            int every = !gaara ? 400 : gourd ? 60 : SAND_GUARD_TICKS;
            if (front && (last == null || now - last >= every) && !(gaara && Gaara.coffining(target))) {
                SAND_GUARD.put(target.getId(), now);
                SubNet.send(level, SubNet.SAND_WALL, target.getId(), target.position(), toFrom, 0, 0, 0);
                SubCommon.sound(level, target.position(), SoundEvents.SAND_BREAK, 1.6f, 0.7f);
                SubCommon.sound(level, target.position(), SoundEvents.SAND_PLACE, 1.4f, 0.6f);
                if (event.getAmount() < 30) {
                    event.setCanceled(true);
                    return;
                }
                event.setAmount(event.getAmount() * 0.6f);
            }
        }
        // Ino's link: half of what one feels runs along the thread to the others.
        if (!sharing && !LINKS.isEmpty() && event.getAmount() > 0) {
            long now = level.getGameTime();
            Iterator<Link> it = LINKS.iterator();
            List<Runnable> later = new ArrayList<>();
            while (it.hasNext()) {
                Link l = it.next();
                if (now > l.until || !l.owner.isAlive()) {
                    it.remove();
                    continue;
                }
                if (!l.members.contains(target)) {
                    continue;
                }
                float half = event.getAmount() * 0.5f;
                for (LivingEntity other : l.members) {
                    if (other == target || !other.isAlive()) {
                        continue;
                    }
                    later.add(() -> {
                        SubNet.send(level, SubNet.INO_PULSE, 0, target.getEyePosition().add(0, 0.3, 0),
                                other.getEyePosition().add(0, 0.3, 0).subtract(target.getEyePosition().add(0, 0.3, 0)), 0, 0, 0);
                        Tasks.later(8, () -> {
                            if (other.isAlive()) {
                                sharing = true;
                                try {
                                    other.invulnerableTime = 0;
                                    other.hurt(l.owner.damageSources().indirectMagic(l.owner, l.owner), half);
                                } finally {
                                    sharing = false;
                                }
                            }
                        });
                    });
                }
            }
            later.forEach(Runnable::run);
        }
    }

    public static void clear() {
        CASTS.clear();
        LINKS.clear();
        SAND_GUARD.clear();
    }
}
