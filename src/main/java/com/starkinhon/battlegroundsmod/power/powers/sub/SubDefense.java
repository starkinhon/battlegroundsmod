package com.starkinhon.battlegroundsmod.power.powers.sub;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;

import com.starkinhon.battlegroundsmod.combat.Combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// The SubAlmas' shields, checked on every hit (PowerEvents): a wall of light stops what comes through its front.
public final class SubDefense {
    // A wall of light at center facing `facing`: whoever stands behind it (within reach) is covered from the front.
    // onBlock runs on each blocked hit and may take it down.
    public static final class Shield {
        final LivingEntity owner;
        final ServerLevel level;
        final Vec3 center;
        final Vec3 facing;
        final double size;
        final long until;
        final BiConsumer<Shield, Entity> onBlock;
        boolean down;

        Shield(LivingEntity owner, ServerLevel level, Vec3 center, Vec3 facing, double size, long until, BiConsumer<Shield, Entity> onBlock) {
            this.owner = owner;
            this.level = level;
            this.center = center;
            this.facing = facing;
            this.size = size;
            this.until = until;
            this.onBlock = onBlock;
        }

        public void takeDown() {
            down = true;
            SubNet.send(level, SubNet.SHIELD, owner.getId(), center, facing, 0, 0, 0);
        }
    }

    private static final List<Shield> SHIELDS = new ArrayList<>();

    private SubDefense() {
    }

    public static Shield shield(LivingEntity owner, ServerLevel level, Vec3 center, Vec3 facing, double size, int ticks, int style,
            BiConsumer<Shield, Entity> onBlock) {
        SHIELDS.removeIf(s -> s.owner == owner);
        Shield s = new Shield(owner, level, center, facing, size, level.getGameTime() + ticks, onBlock);
        SHIELDS.add(s);
        SubNet.send(level, SubNet.SHIELD, owner.getId(), center, facing, ticks, style, (float) size);
        return s;
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Entity source = event.getSource().getDirectEntity();
        Entity attacker = event.getSource().getEntity();
        Vec3 from = source != null ? source.position().add(0, source.getBbHeight() * 0.5, 0)
                : attacker != null ? attacker.position().add(0, attacker.getBbHeight() * 0.5, 0) : event.getSource().getSourcePosition();
        if (from != null && !SHIELDS.isEmpty()) {
            Iterator<Shield> it = SHIELDS.iterator();
            while (it.hasNext()) {
                Shield s = it.next();
                if (s.down || s.level != target.level() || s.level.getGameTime() > s.until || !s.owner.isAlive()) {
                    it.remove();
                    continue;
                }
                if (attacker == s.owner) {
                    continue;
                }
                Vec3 rel = Combat.center(target).subtract(s.center);
                double behind = -rel.dot(s.facing);
                // The one hit stands behind the wall (close to it) and the hit comes from in front of it.
                if (behind < -0.6 || behind > 5 || rel.subtract(s.facing.scale(-behind)).length() > s.size * 0.9 + 1) {
                    continue;
                }
                if (from.subtract(s.center).dot(s.facing) <= 0) {
                    continue;
                }
                event.setCanceled(true);
                SubNet.send(s.level, SubNet.SHIELD_HIT, s.owner.getId(), s.center, s.facing, 0, 0, 0);
                SubCommon.sound(s.level, s.center, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.7f);
                SubCommon.sound(s.level, s.center, SoundEvents.SHIELD_BLOCK, 1f, 1.2f);
                if (s.onBlock != null) {
                    s.onBlock.accept(s, attacker);
                }
                if (s.down) {
                    it.remove();
                }
                return;
            }
        }
        // Dragon Ball: the Suiken's dodges and the Taiyoken's stun broken by a hit from the front.
        com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon.onIncomingDamage(event);
        // Naruto: Sasuke's reading, Gaara's sand, the meditation broken, Ino's link.
        if (!event.isCanceled()) {
            com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon.onIncomingDamage(event);
        }
        // Jujutsu: the Manji Kick's guard, Supernova's orbs, the sleeper's critical, the fake clap, Nobara's blood.
        if (!event.isCanceled()) {
            com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon.onIncomingDamage(event);
        }
    }

    public static void clear() {
        SHIELDS.clear();
    }
}
