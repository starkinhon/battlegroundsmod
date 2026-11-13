package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import java.util.Map;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.CustomDeath;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Passive, Toque da Decadência acumulado (Stark, 2026-11-13): every fist of Shigaraki's that lands (combo, Dash Attack,
// Forte, Barragem) leaves the Decadência on the body (Stark asked for 20 hits a level; 30 after the retest, where 20 made
// Madara lose every fight 8.6 x 3.3 DPS). Each HITS_PER_LEVEL hits it goes up a level (up to MAX_LEVEL): the
// body greys from the touch (the Toque's own crumbling), takes more damage from everything and crumbles a share of its
// health every second, more each level, until at the last level it is falling to dust and dies of it. Without a new
// touch for FADE ticks it all goes away. The share is of the victim's own health, so it bites a boss the same way.
public final class Esfarelar {
    static final int HITS_PER_LEVEL = 30, MAX_LEVEL = 5, FADE = 400;
    // the health crumbled each second at each level, and the extra damage taken from everything
    private static final float[] PER_SECOND = {0, 0.005f, 0.01f, 0.02f, 0.035f, 0.06f};
    private static final float VULNERABLE_PER_LEVEL = 0.08f;

    private static final class Mark {
        LivingEntity by;
        int hits;
        int level;
        long last;
        boolean ticking;
    }

    private static final Map<LivingEntity, Mark> MARKS = new WeakHashMap<>();

    private Esfarelar() {
    }

    static boolean isShigaraki(LivingEntity e) {
        return e.hasData(ModAttachments.POWER) && "shigaraki".equals(e.getData(ModAttachments.POWER).power);
    }

    // Combat.hurtBasic: a fist landed.
    public static void onFist(LivingEntity attacker, LivingEntity target) {
        if (attacker == target || !target.isAlive() || !isShigaraki(attacker) || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        Mark m = MARKS.computeIfAbsent(target, t -> new Mark());
        long now = level.getGameTime();
        if (m.by != attacker || now - m.last > FADE) {
            m.hits = 0;
            m.level = 0;
        }
        m.by = attacker;
        m.last = now;
        m.hits++;
        int level0 = Math.min(MAX_LEVEL, m.hits / HITS_PER_LEVEL);
        if (level0 > m.level) {
            m.level = level0;
            levelUp(level, attacker, target, m);
        }
        if (m.level > 0 && !m.ticking) {
            m.ticking = true;
            tick(level, target, m);
        }
    }

    // The extra damage a body under the Decadência takes (PowerEvents.onIncomingDamage), 1 with none.
    public static float vulnerability(LivingEntity target) {
        Mark m = MARKS.get(target);
        if (m == null || m.level <= 0 || target.level().getGameTime() - m.last > FADE) {
            return 1;
        }
        return 1 + VULNERABLE_PER_LEVEL * m.level;
    }

    // The level on a body now (0 with none), for the HUD and the bosses.
    public static int level(LivingEntity target) {
        Mark m = MARKS.get(target);
        return m == null || target.level().getGameTime() - m.last > FADE ? 0 : m.level;
    }

    private static void levelUp(ServerLevel level, LivingEntity attacker, LivingEntity target, Mark m) {
        Vec3 touch = target.position().add(0, target.getBbHeight() * 0.6, 0);
        // the Toque's own crumbling on the body from the touch (fast, so no real stun), a little damage with it
        Decay.body(attacker, target, touch, 3f, 2f * m.level, 0);
        SubCommon.sound(level, touch, SoundEvents.SAND_BREAK, 1.2f, 0.6f - 0.06f * m.level);
        SubCommon.sound(level, touch, SoundEvents.GRAVEL_BREAK, 0.9f, 0.5f);
        SubCommon.warn(target, "shigaraki.decay_level", m.level, MAX_LEVEL);
        SubCommon.warn(attacker, "shigaraki.decay_level_on", m.level, MAX_LEVEL);
    }

    private static void tick(ServerLevel level, LivingEntity target, Mark m) {
        Tasks.addSystem(age -> {
            long now = level.getGameTime();
            if (!target.isAlive() || target.isRemoved() || m.by == null || m.by.isRemoved() || now - m.last > FADE) {
                m.ticking = false;
                if (target.isAlive() && now - m.last > FADE && m.level > 0) {
                    m.level = 0;
                    m.hits = 0;
                    SubCommon.warn(target, "shigaraki.decay_gone");
                }
                return true;
            }
            if (age % 20 != 19) {
                return false;
            }
            // a share of the body's own health, through every defense (it is the body falling apart, not a blow)
            float share = PER_SECOND[m.level] * target.getMaxHealth();
            float dmg = share / Math.max(0.01f, Tiers.damageMultiplier(m.by));
            boolean wasAlive = !target.isDeadOrDying();
            Vec3 v = target.getDeltaMovement();
            Combat.hurtPiercing(m.by, target, dmg);
            target.setDeltaMovement(v);
            if (m.level >= 3) {
                SubCommon.sound(level, target.position(), SoundEvents.SAND_BREAK, 0.5f + 0.1f * m.level, 0.7f);
            }
            if (wasAlive && target.isDeadOrDying()) {
                // gone to dust: the clients crumble the body (never the normal death)
                CustomDeath.mark(target);
                Vec3 touch = target.position().add(0, target.getBbHeight() * 0.6, 0);
                int ticks = 30;
                SubNet.send(level, Shigaraki.BODY, target.getId(), touch, new Vec3(2.5, ticks, 0), 0, 0, 0);
                SubNet.send(level, Shigaraki.BODY, target.getId(), touch, new Vec3(2.5, ticks, 0), 1, 0, 0);
                m.ticking = false;
                return true;
            }
            return false;
        });
    }

    // The world closed (ServerReset).
    public static void resetStatics() {
        MARKS.clear();
    }
}
