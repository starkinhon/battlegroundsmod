package com.starkinhon.battlegroundsmod.animal.naruto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.animal.MobGrief;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// The 48 blows of the Naruto creatures, as the approved preview shows and describes them (tools/mobs/naruto/*.js, the
// "info" of each mob). The hit lands on the frame the preview's target reacts; every wind-up the preview shows (the
// crouch, the glint, the gills opening) is the time to get out, so a step back or aside during it dodges the blow
// (reach is checked again on the hit). Damages are on the mod's Tier 0 scale like the Dragon Ball creatures'
// (Combat.hurt shrinks them by the creature's tier): 20 = one heart for a human. Holds never take someone already held
// (combate limpo: nobody is in two prisons), and every hold has its own length, so a blow cut short never leaves one on.
final class NarutoMoves {
    private NarutoMoves() {
    }

    // ---------- helpers ----------

    private static ServerLevel lvl(NarutoMob m) {
        return (ServerLevel) m.level();
    }

    private static Vec3 ahead(NarutoMob m, double d) {
        return m.position().add(m.forward().scale(d));
    }

    private static Vec3 right(NarutoMob m) {
        Vec3 f = m.forward();
        return new Vec3(-f.z, 0, f.x);
    }

    private static boolean alive(LivingEntity t) {
        return t != null && t.isAlive() && !(t instanceof Player p && (p.isSpectator()));
    }

    private static void sound(NarutoMob m, SoundEvent s, float vol, float pitch) {
        SubCommon.sound(lvl(m), m.position(), s, vol, pitch);
    }

    private static void sound(NarutoMob m, Vec3 at, SoundEvent s, float vol, float pitch) {
        SubCommon.sound(lvl(m), at, s, vol, pitch);
    }

    private static void hit(NarutoMob m, LivingEntity t, float dmg, float size, int color) {
        Combat.hurt(m, t, dmg);
        DbCommon.hit(lvl(m), Combat.center(t), size, color);
    }

    // in front of the body (cone) and within reach blocks of its edge
    private static boolean front(NarutoMob m, LivingEntity t, double reach, double minDot) {
        if (!alive(t)) {
            return false;
        }
        Vec3 to = t.position().subtract(m.position()).multiply(1, 0, 1);
        double gap = m.gap(t);
        if (gap > reach || Math.abs(t.getY() - m.getY()) > reach + 1.5) {
            return false;
        }
        return to.lengthSqr() < 0.04 || to.normalize().dot(m.forward()) > minDot || gap < 0.5;
    }

    private static boolean hold(NarutoMob m, LivingEntity t, int ticks) {
        if (Freeze.isFrozen(t)) {
            return false;
        }
        m.release = Freeze.hold(t, ticks);
        m.heldId = t.getId();
        return true;
    }

    private static boolean hold(NarutoMob m, LivingEntity t, int ticks, java.util.function.IntFunction<Vec3> anchor) {
        if (Freeze.isFrozen(t)) {
            return false;
        }
        m.release = Freeze.hold(t, ticks, anchor);
        m.heldId = t.getId();
        return true;
    }

    private static void letGo(NarutoMob m) {
        if (m.release != null) {
            m.release.run();
            m.release = null;
        }
        m.heldId = -1;
    }

    private static boolean holding(NarutoMob m, LivingEntity t) {
        return t != null && m.heldId == t.getId() && m.release != null;
    }

    private static void poison(LivingEntity t, NarutoMob m, int ticks, int level) {
        t.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, level), m);
    }

    private static void slow(LivingEntity t, NarutoMob m, int ticks, int level) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, level, false, true), m);
    }

    private static void dizzy(NarutoMob m, LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks + 40, 0, false, false), m);
        SubNet.send(lvl(m), SubNet.DIZZY, t.getId(), t.position(), Vec3.ZERO, ticks, 0, 0);
        if (t instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    // a ground point under at
    private static Vec3 ground(NarutoMob m, Vec3 at) {
        return SubCommon.ground(lvl(m), at);
    }

    private static void wave(NarutoMob m, Vec3 at, double r, float dmg, double push, double up, @Nullable Set<LivingEntity> skip) {
        for (LivingEntity t : Combat.inRadius(lvl(m), at, r, m)) {
            if (t instanceof NarutoMob || skip != null && !skip.add(t)) {
                continue;
            }
            double k = 1 - 0.5 * Math.min(1, t.position().distanceTo(at) / r);
            hit(m, t, (float) (dmg * k), 0.7f, 10);
            Combat.pushFrom(t, at, push * k, up * k);
        }
    }

    // ---------- the AI's gates ----------

    static boolean canUse(NarutoMob m, String mode, LivingEntity t) {
        switch (m.id()) {
            case "tubarao_agua" -> {
                return m.isInWater();
            }
            case "salamandra_venenosa" -> {
                if (mode.equals("rabada")) {
                    // the tail sweeps whoever is behind or beside it
                    Vec3 to = t.position().subtract(m.position()).multiply(1, 0, 1).normalize();
                    return to.dot(m.forward()) < 0.3;
                }
                return true;
            }
            case "corvo_uchiha", "clone_madeira", "zetsu_branco" -> {
                return m.hasLineOfSight(t);
            }
            case "garca_papel" -> {
                // dives from above
                return !mode.equals("mergulho") || m.getY() > t.getY() + 0.8;
            }
            case "marionete" -> {
                // nothing without its marionetista
                return m.partner() != null;
            }
            default -> {
                return true;
            }
        }
    }

    @Nullable
    static Vec3 fleeFrom(NarutoMob m) {
        switch (m.id()) {
            case "tigre" -> {
                LivingEntity t = m.getTarget();
                if (t != null && m.getHealth() < m.getMaxHealth() * 0.22f) {
                    return t.position();
                }
            }
            case "inseto_gigante" -> {
                // the frogs hunt it: one near, and the beetle runs
                List<NarutoMob> frogs = m.level().getEntitiesOfClass(NarutoMob.class, m.getBoundingBox().inflate(7),
                        f -> f.getType() == ModEntities.naruto("sapo_ninja"));
                if (!frogs.isEmpty()) {
                    return frogs.get(0).position();
                }
            }
            case "ninken" -> {
                // the tiger's roar scares the dogs off
                if (m.sleep > 0) {
                    return m.blowSpot;
                }
            }
            default -> {
            }
        }
        return null;
    }

    // ---------- the blows ----------

    static void start(NarutoMob m, String mode, @Nullable LivingEntity t) {
        ServerLevel level = lvl(m);
        switch (m.id() + "/" + mode) {
            case "ninja_renegado/papelbomba" -> sound(m, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1f, 1.4f);
            case "ninken/pinca" -> sound(m, SoundEvents.WOLF_GROWL, 1.2f, 1.1f);
            case "ninken/gatsuga" -> {
                sound(m, SoundEvents.WOLF_GROWL, 1.2f, 0.9f);
                SubNet.send(level, SubNet.DUST, 0, m.position(), Vec3.ZERO, 0.8f, 0, 0);
            }
            case "cobra_gigante/bote" -> sound(m, SoundEvents.CAT_HISS, 2f, 0.5f);
            case "cobra_gigante/engolir" -> sound(m, SoundEvents.CAT_HISS, 2f, 0.4f);
            case "tigre/salto" -> sound(m, SoundEvents.POLAR_BEAR_WARNING, 1.3f, 1.1f);
            case "tigre/rugido" -> {
            }
            case "ninja_som/onda" -> sound(m, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.8f);
            case "ninja_som/rajada" -> sound(m, SoundEvents.BREEZE_CHARGE, 1.2f, 0.8f);
            case "centopeia/picada", "centopeia/enrolar" -> sound(m, SoundEvents.SPIDER_AMBIENT, 1.2f, 1.4f);
            case "ninja_leque/kamaitachi" -> sound(m, SoundEvents.ARMOR_EQUIP_ELYTRA.value(), 1.3f, 1.2f);
            case "ninja_leque/fechado" -> sound(m, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.2f, 0.8f);
            case "escorpiao_areia/emboscada" -> {
                // the sand trembles under the target before it comes out
                SubNet.send(level, SubNet.DUST, 0, t != null ? t.position() : m.position(), Vec3.ZERO, 1.1f, 0, 0);
                sound(m, t != null ? t.position() : m.position(), SoundEvents.SAND_BREAK, 1.4f, 0.6f);
            }
            case "escorpiao_areia/ferroada" -> sound(m, SoundEvents.SPIDER_AMBIENT, 1.2f, 0.7f);
            case "marionete/laminas" -> {
                sound(m, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 1.4f, 1.3f);
                puppeteerGesture(m, "laminas");
            }
            case "marionete/jaula" -> {
                sound(m, SoundEvents.CHEST_OPEN, 1.4f, 0.6f);
                puppeteerGesture(m, "jaula");
            }
            case "marionetista/agulhas" -> {
                sound(m, SoundEvents.CROSSBOW_LOADING_START.value(), 1.2f, 1.5f);
                NarutoMob puppet = m.partner();
                if (puppet != null && !puppet.attacking()) {
                    // while it aims, the puppet hangs limp
                    puppet.blow(puppet.spec().kindOf("agulhas"), null);
                }
            }
            case "espadachim_nevoa/silencioso" -> {
                // the seal, then the mist swallows him
                SubNet.send(level, NmNet.MIST, m.getId(), m.position(), Vec3.ZERO, 12, 200, 0);
            }
            case "espadachim_nevoa/clones" -> sound(m, SoundEvents.PLAYER_SPLASH, 1f, 0.8f);
            case "tubarao_agua/salto" -> sound(m, SoundEvents.DOLPHIN_SWIM, 1.4f, 0.6f);
            case "tubarao_agua/torpedo" -> sound(m, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.4f, 0.8f);
            case "caranguejo_gigante/pinca" -> sound(m, SoundEvents.ARMADILLO_SCUTE_DROP, 1.2f, 0.6f);
            case "caranguejo_gigante/bolhas" -> sound(m, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.2f, 1.2f);
            case "cacador_nevoa/prisao" -> sound(m, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1f, 1.2f);
            case "cacador_nevoa/senbon" -> sound(m, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1f, 1.6f);
            case "ninja_chuva/senbon" -> {
                // the umbrella goes up and opens over the target: its shadow spins on the ground 0.7 s before the rain
                m.blowSpot = t != null ? t.position() : ahead(m, 4);
                sound(m, SoundEvents.ARMOR_EQUIP_ELYTRA.value(), 1.2f, 1.5f);
            }
            case "ninja_chuva/corrente" -> sound(m, SoundEvents.CHAIN_PLACE, 1.4f, 0.8f);
            case "garca_papel/mergulho" -> sound(m, SoundEvents.BOOK_PAGE_TURN, 1.4f, 0.7f);
            case "garca_papel/corte" -> sound(m, SoundEvents.BOOK_PAGE_TURN, 1.4f, 1.3f);
            case "salamandra_venenosa/gas" -> sound(m, SoundEvents.SNIFFER_SNIFFING, 2f, 0.5f);
            case "salamandra_venenosa/rabada" -> sound(m, SoundEvents.SNIFFER_SNIFFING, 1.5f, 0.8f);
            case "zetsu_branco/agarrao" -> sound(m, SoundEvents.MUD_BREAK, 1.4f, 0.6f);
            case "zetsu_branco/esporos" -> sound(m, SoundEvents.SPORE_BLOSSOM_PLACE, 1.4f, 0.8f);
            case "corvo_uchiha/genjutsu" -> {
                sound(m, SoundEvents.PARROT_IMITATE_WITCH, 1.2f, 0.5f);
                SubNet.send(level, NmNet.TOMOE, m.getId(), m.getEyePosition(), t != null ? t.getEyePosition().subtract(m.getEyePosition()) : Vec3.ZERO, 70, 0, 0);
            }
            case "corvo_uchiha/revoada" -> sound(m, SoundEvents.PHANTOM_FLAP, 1.4f, 1.2f);
            case "clone_madeira/estacas" -> sound(m, SoundEvents.WOOD_HIT, 1.4f, 0.6f);
            case "clone_madeira/prisao" -> sound(m, SoundEvents.WOOD_PLACE, 1.4f, 0.5f);
            case "sapo_ninja/bala" -> sound(m, SoundEvents.FROG_AMBIENT, 1.4f, 0.8f);
            case "sapo_ninja/lingua" -> sound(m, SoundEvents.FROG_TONGUE, 1.4f, 1f);
            case "sapo_gigante/salto" -> {
                sound(m, SoundEvents.FROG_LONG_JUMP, 3f, 0.4f);
                // the shadow on the ground shows where it lands
                m.blowSpot = ground(m, ahead(m, 6));
                SubNet.send(level, SubNet.GROUND_MARK, m.getId(), m.blowSpot, Vec3.ZERO, 1, 0, 0);
            }
            case "sapo_gigante/canhao" -> sound(m, SoundEvents.FROG_AMBIENT, 3f, 0.4f);
            case "inseto_gigante/chifrada" -> sound(m, SoundEvents.ARMADILLO_SCUTE_DROP, 1.2f, 0.8f);
            case "inseto_gigante/voo" -> sound(m, SoundEvents.BEE_LOOP_AGGRESSIVE, 1.4f, 0.6f);
            default -> {
            }
        }
    }

    // the marionetista's hands work the same blow its puppet throws
    private static void puppeteerGesture(NarutoMob puppet, String mode) {
        NarutoMob pm = puppet.partner();
        if (pm != null && !pm.attacking()) {
            pm.blow(pm.spec().kindOf(mode), puppet.getTarget());
        }
    }

    static void tick(NarutoMob m, String mode, int tick, @Nullable LivingEntity t) {
        ServerLevel level = lvl(m);
        int f = tick - 1;
        switch (m.id() + "/" + mode) {
            case "ninja_renegado/substituicao" -> {
                if (f == 7) {
                    // the log takes the blow in a puff of smoke
                    SubNet.send(level, SubNet.POOF, 0, m.position().add(0, 0.9, 0), Vec3.ZERO, 1.1f, 0, 0);
                    sound(m, SoundEvents.WOOL_BREAK, 1.2f, 0.7f);
                }
            }
            case "ninja_renegado/papelbomba" -> {
                if (f == 12) {
                    // the kunai stuck by the target's feet, its tag starting to burn
                    m.blowSpot = ground(m, alive(t) ? t.position() : ahead(m, 3.3));
                    Vec3 hand = m.position().add(0, 1.3, 0);
                    SubNet.send(level, SubNet.KUNAI, 0, hand, m.blowSpot.subtract(hand), 4, 0, 0);
                }
                if (f >= 16 && f < 47) {
                    SubNet.send(level, SubNet.TAG, m.getId(), m.blowSpot, Vec3.ZERO, 1, (f - 16) / 31f, 0);
                    if (f % 8 == 0) {
                        sound(m, m.blowSpot, SoundEvents.FIRE_AMBIENT, 1f, 1.6f);
                    }
                }
            }
            case "ninken/gatsuga" -> {
                if (f == 14) {
                    SubNet.send(level, NmNet.DRILL, m.getId(), Combat.center(m), m.forward(), 1, 0, 0);
                    sound(m, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4f, 1.2f);
                }
                if (f >= 14 && f <= 28) {
                    SubNet.send(level, NmNet.DRILL, m.getId(), Combat.center(m), m.forward(), 1, 0, 0);
                    if (f % 2 == 0) {
                        // the drill ploughs a trench through the earth (never the stone)
                        MobGrief.bore(m, Combat.center(m).add(m.forward().scale(0.6)), 0.95, MobGrief.EARTH);
                    }
                    // goes through whoever is on the line
                    for (LivingEntity o : Combat.inRadius(level, Combat.center(m), 1.3, m)) {
                        if (o instanceof NarutoMob || !hitOnce(m, o)) {
                            continue;
                        }
                        hit(m, o, 70, 0.6f, 10);
                        Combat.push(o, right(m).scale(m.getRandom().nextBoolean() ? 1 : -1).add(m.forward()), 0.8, 0.35);
                    }
                }
                if (f == 29) {
                    SubNet.send(level, NmNet.DRILL, m.getId(), Combat.center(m), m.forward(), 0, 0, 1);
                }
            }
            case "cobra_gigante/engolir" -> {
                if (holding(m, t) && f > 15 && f < 92 && f % 20 == 0) {
                    Combat.hurt(m, t, 30);
                }
                if (holding(m, t) && f == 91) {
                    spit(m, t, 1.4);
                }
            }
            case "ninja_som/onda" -> {
                if (f == 19) {
                    Vec3 hand = m.position().add(0, 1.4, 0).add(right(m).scale(-0.3));
                    Vec3 dir = alive(t) ? Combat.center(t).subtract(hand).normalize() : m.forward();
                    SubNet.send(level, NmNet.SOUND, m.getId(), hand, dir.scale(8), 0, 0, 0);
                }
            }
            case "centopeia/enrolar" -> {
                // wrapped around the leg: the target is slowed until it hits the centipede
                if (holding(m, t) && f % 10 == 0) {
                    slow(t, m, 15, 3);
                }
            }
            case "marionete/jaula" -> {
                if (holding(m, t) && f == 69) {
                    spit(m, t, 0.9);
                }
            }
            case "espadachim_nevoa/silencioso" -> {
                if (f == 49 && alive(t)) {
                    // the only warning: the blade's glint, 0.5 s before
                    SubNet.send(level, SubNet.SPARKS, 0, m.position().add(0, 1.6, 0).add(m.forward().scale(0.6)), Vec3.ZERO, 0.4f, 6, 1);
                    sound(m, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.8f);
                }
            }
            case "espadachim_nevoa/clones" -> {
                if (f == 12) {
                    waterClones(m, t);
                }
            }
            case "cacador_nevoa/prisao" -> {
                if (holding(m, t)) {
                    if (f % 10 == 0) {
                        // no air in the bubble
                        Combat.hurt(m, t, 18);
                        t.setAirSupply(Math.max(0, t.getAirSupply() - 40));
                    }
                    SubNet.send(level, NmNet.BUBBLE, t.getId(), Combat.center(t), Vec3.ZERO, 1.1f, 0, 0);
                    if (f >= 73) {
                        freeBubble(m, t);
                    }
                }
            }
            case "ninja_chuva/senbon" -> {
                if (f == 14) {
                    SubNet.send(level, NmNet.UMBRELLA, m.getId(), m.blowSpot, Vec3.ZERO, 5, 50, 0);
                }
                if (f >= 35 && f < 65 && f % 4 == 0) {
                    for (LivingEntity o : Combat.inRadius(level, m.blowSpot.add(0, 1, 0), 5, m)) {
                        if (!(o instanceof NarutoMob)) {
                            hit(m, o, 22, 0.35f, 10);
                        }
                    }
                    sound(m, m.blowSpot, SoundEvents.ARROW_HIT, 0.8f, 1.6f);
                }
            }
            case "ninja_chuva/corrente" -> {
                if (f > 26 && f < 54) {
                    Vec3 hand = m.position().add(0, 1.2, 0).add(right(m).scale(-0.3));
                    Vec3 to = m.heldId >= 0 && m.level().getEntity(m.heldId) instanceof LivingEntity in ? Combat.center(in)
                            : ahead(m, Math.max(1.2, 6.5 * (1 - (f - 26) / 28.0))).add(0, 1, 0);
                    SubNet.send(level, NmNet.CHAIN, m.getId(), hand, to.subtract(hand), 0, 0, 0);
                }
            }
            case "salamandra_venenosa/gas" -> {
                if (f >= 30 && f < 90 && f % 10 == 0) {
                    Vec3 c = m.position().add(m.forward().scale(2.5));
                    for (LivingEntity o : Combat.inRadius(level, c, 7, m)) {
                        // a low gas: whoever climbed onto something high escapes it
                        if (o instanceof NarutoMob || o.getY() > m.getY() + 1.8) {
                            continue;
                        }
                        poison(o, m, 60, 1);
                        Combat.hurt(m, o, 12);
                    }
                }
            }
            case "zetsu_branco/agarrao" -> {
                if (f == 4) {
                    // the ground bubbles in front of the target
                    Vec3 spot = alive(t) ? t.position().add(m.forward().scale(-1.1)) : ahead(m, 2);
                    SubNet.send(level, SubNet.DUST, 0, spot, Vec3.ZERO, 0.9f, 0, 0);
                    sound(m, spot, SoundEvents.MUD_STEP, 1.4f, 0.5f);
                }
                if (holding(m, t) && f > 25 && f < 58 && f % 5 == 0) {
                    // the stamina runs out of the target into him
                    Stamina.drain(t, 4);
                }
            }
            case "corvo_uchiha/revoada" -> {
                if (f >= 22 && f <= 36 && f % 3 == 0 && alive(t) && t.distanceTo(m) < 4) {
                    hit(m, t, 10, 0.3f, 1);
                }
            }
            case "sapo_ninja/lingua" -> {
                if (holding(m, t) && f == 33) {
                    // the slap once it is pulled in
                    hit(m, t, 40, 0.6f, 10);
                    Combat.push(t, m.forward(), 0.5, 0.25);
                    sound(m, t.position(), SoundEvents.SLIME_ATTACK, 1.3f, 0.8f);
                    letGo(m);
                }
            }
            case "inseto_gigante/voo" -> {
                if (f == 8) {
                    sound(m, SoundEvents.BEE_LOOP_AGGRESSIVE, 1.6f, 0.5f);
                }
            }
            default -> {
            }
        }
    }

    private static final java.util.Map<Integer, Set<Integer>> HIT_ONCE = new java.util.HashMap<>();

    // a blow that runs through (the Gatsuga, a dive) hits each one once
    private static boolean hitOnce(NarutoMob m, LivingEntity t) {
        Set<Integer> set = HIT_ONCE.computeIfAbsent(m.getId(), k -> new HashSet<>());
        return set.add(t.getId());
    }

    static void impact(NarutoMob m, String mode, @Nullable LivingEntity t) {
        ServerLevel level = lvl(m);
        switch (m.id() + "/" + mode) {
            // ---------- Ninja Renegado ----------
            case "ninja_renegado/substituicao" -> {
                SubNet.send(level, SubNet.SLASH, 0, ahead(m, 1.2).add(0, 1.2, 0), right(m).add(0, -0.4, 0).normalize(), 1.1f, 0.18f, SubNet.STYLE_SUKUNA);
                sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.3f);
                MobGrief.mow(m, m.position(), m.forward(), 2.4, 1.2, 2);
                if (front(m, t, 2.2, 0.2)) {
                    hit(m, t, 80, 0.6f, 10);
                    Combat.push(t, m.forward(), 0.35, 0.15);
                }
            }
            case "ninja_renegado/papelbomba" -> {
                SubNet.send(level, SubNet.TAG, m.getId(), m.blowSpot, Vec3.ZERO, 1, 1, 1);
                SubNet.send(level, SubNet.FIRE_BOOM, 0, m.blowSpot.add(0, 0.4, 0), new Vec3(m.blowSpot.y, 0, 0), 2.2f, 0, m.getRandom().nextInt(50));
                sound(m, m.blowSpot, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 1.1f);
                wave(m, m.blowSpot.add(0, 0.5, 0), 2.8, 120, 0.9, 0.45, null);
                MobGrief.crater(m, m.blowSpot, 1.5, MobGrief.ROCK);
                MobGrief.scorch(m, m.blowSpot, 2.3, 1);
            }
            // ---------- Ninken ----------
            case "ninken/pinca" -> {
                if (!front(m, t, 2.2, 0.2)) {
                    return;
                }
                // two bite the arms (1 s without attacking), the third bites: here the pack's grip on the arms is the
                // first one in, then every dog bites
                if (!Freeze.isFrozen(t)) {
                    Stun.apply(t, 20);
                }
                hit(m, t, 50, 0.5f, 10);
                sound(m, t.position(), SoundEvents.WOLF_GROWL, 1.2f, 1.4f);
            }
            case "ninken/gatsuga" -> {
            }
            // ---------- Cobra Gigante ----------
            case "cobra_gigante/bote" -> {
                sound(m, SoundEvents.EVOKER_FANGS_ATTACK, 1.6f, 0.6f);
                // the huge body lunging flattens the plants in its way, the head slams into the ground
                MobGrief.mow(m, m.position(), m.forward(), 10, 1.4, 2.5);
                MobGrief.heave(m, ahead(m, Math.min(10, alive(t) ? m.gap(t) + 1 : 8)), 1.6, 3, MobGrief.EARTH);
                if (front(m, t, 11, 0.75)) {
                    hit(m, t, 180, 0.9f, 10);
                    Combat.push(t, m.forward(), 1.1, 0.4);
                    poison(t, m, 60, 0);
                }
            }
            case "cobra_gigante/engolir" -> {
                if (!front(m, t, 9.5, 0.7) || Freeze.isFrozen(t)) {
                    return;
                }
                // inside: held at the head, dark, 4 s; hitting it from inside makes it spit sooner
                Vec3 mouth = ahead(m, Math.min(9, m.gap(t) + 1.5));
                hold(m, t, 80, age -> mouth);
                t.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, false, false));
                NmNet.screen(t, 0, 76);
                sound(m, mouth, SoundEvents.GENERIC_EAT, 2f, 0.4f);
            }
            // ---------- Tigre ----------
            case "tigre/salto" -> {
                SubNet.send(level, SubNet.SLASH, 0, ahead(m, 1.6).add(0, 1, 0), right(m).add(0, -0.7, 0).normalize(), 1.3f, 0.2f, SubNet.STYLE_CRIMSON);
                sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.8f);
                MobGrief.mow(m, m.position(), m.forward(), 2.6, 1.2, 2);
                if (front(m, t, 2.4, 0.3)) {
                    Defense.breakGuard(t, m);
                    hit(m, t, 120, 0.8f, 10);
                    Combat.push(t, m.forward(), 0.6, 0.3);
                }
            }
            case "tigre/rugido" -> {
                sound(m, SoundEvents.RAVAGER_ROAR, 2f, 1.2f);
                SubNet.send(level, NmNet.SHOCK, 0, m.position().add(0, 0.1, 0), Vec3.ZERO, 6, 0, 1);
                MobGrief.shatter(m, m.position().add(0, 0.5, 0), 3.2);
                for (LivingEntity o : Combat.inRadius(level, m.position(), 9, m)) {
                    if (o instanceof NarutoMob n) {
                        // the dogs and the weak run from it
                        if (n.spec().health <= 30) {
                            n.blowSpot = m.position();
                            n.sleep = 60;
                            n.setTarget(null);
                        }
                        continue;
                    }
                    Combat.pushFrom(o, m.position(), 0.7, 0.2);
                }
            }
            // ---------- Ninja do Som ----------
            case "ninja_som/onda" -> {
                Vec3 hand = m.position().add(0, 1.4, 0);
                Vec3 dir = alive(t) ? Combat.center(t).subtract(hand).normalize() : m.forward();
                // the sound shatters the glass and the leaves along it
                MobGrief.shatterLine(m, hand, hand.add(dir.scale(8)), 1.1);
                for (LivingEntity o : Combat.alongLine(level, hand, hand.add(dir.scale(8)), 1.4, m)) {
                    if (o instanceof NarutoMob) {
                        continue;
                    }
                    hit(m, o, 40, 0.4f, 6);
                    dizzy(m, o, 40);
                }
            }
            case "ninja_som/rajada" -> {
                Vec3 hand = m.position().add(0, 1.0, 0);
                SubNet.send(level, SubNet.GALE, m.getId(), hand, hand.add(m.forward().scale(7)), 0, 0, 1);
                sound(m, SoundEvents.BREEZE_SHOOT, 1.6f, 0.7f);
                MobGrief.mow(m, m.position(), m.forward(), 7, 1.6, 2.5);
                for (LivingEntity o : Combat.alongLine(level, hand, hand.add(m.forward().scale(7)), 1.5, m)) {
                    if (o instanceof NarutoMob) {
                        continue;
                    }
                    hit(m, o, 80, 0.6f, 6);
                    // five blocks back, spinning
                    Combat.push(o, m.forward(), 1.5, 0.45);
                }
            }
            // ---------- Centopeia ----------
            case "centopeia/picada" -> {
                sound(m, SoundEvents.SPIDER_HURT, 1.2f, 1.4f);
                if (front(m, t, 2.4, 0.3)) {
                    hit(m, t, 60, 0.5f, 10);
                    poison(t, m, 80, 0);
                }
            }
            case "centopeia/enrolar" -> {
                if (front(m, t, 1.6, -0.2) && hold(m, t, 40)) {
                    slow(t, m, 40, 3);
                    hit(m, t, 20, 0.4f, 10);
                    sound(m, t.position(), SoundEvents.SPIDER_STEP, 1.4f, 0.8f);
                }
            }
            // ---------- Ninja do Leque ----------
            case "ninja_leque/kamaitachi" -> {
                // a fan of wind blades over 7 blocks (twice as far in the sandstorm)
                double range = m.level().isThundering() || m.level().isRaining() ? 14 : 7;
                Vec3 from = m.position().add(0, 1.1, 0);
                SubNet.send(level, NmNet.WIND, m.getId(), from, m.forward().scale(range), 0.55f, 0, 0);
                sound(m, SoundEvents.BREEZE_SHOOT, 1.6f, 1.3f);
                // the wind blades cut the grass, the flowers and the leaves in the fan
                MobGrief.mow(m, m.position(), m.forward(), range, 2.6, 3);
                for (LivingEntity o : Combat.inRadius(level, m.position(), range, m)) {
                    Vec3 to = o.position().subtract(m.position()).multiply(1, 0, 1);
                    if (o instanceof NarutoMob || to.lengthSqr() < 0.01 || to.normalize().dot(m.forward()) < 0.82) {
                        continue;
                    }
                    hit(m, o, 100, 0.6f, 6);
                    Combat.push(o, m.forward(), 1.5, 0.3);
                }
            }
            case "ninja_leque/fechado" -> {
                sound(m, SoundEvents.PLAYER_ATTACK_STRONG, 1.3f, 0.7f);
                SubNet.send(level, SubNet.SLASH, 0, ahead(m, 1.2).add(0, 0.6, 0), new Vec3(0, 1, 0).add(m.forward().scale(0.3)).normalize(), 1.2f, 0.2f, SubNet.STYLE_SUKUNA);
                MobGrief.mow(m, m.position(), m.forward(), 2.2, 0.9, 2);
                if (front(m, t, 2.3, 0.3)) {
                    hit(m, t, 80, 0.6f, 10);
                    // three blocks up
                    Combat.push(t, m.forward(), 0.3, 0.85);
                }
            }
            // ---------- Escorpião de Areia ----------
            case "escorpiao_areia/emboscada" -> {
                SubNet.send(level, SubNet.DUST, 0, m.position(), Vec3.ZERO, 1.4f, 0, 0);
                sound(m, SoundEvents.SAND_BREAK, 1.6f, 0.5f);
                MobGrief.heave(m, m.position(), 1.6, 3, MobGrief.EARTH);
                if (front(m, t, 1.8, -1) && hold(m, t, 30)) {
                    slow(t, m, 30, 4);
                    hit(m, t, 60, 0.5f, 10);
                }
            }
            case "escorpiao_areia/ferroada" -> {
                sound(m, SoundEvents.TRIDENT_HIT, 1.2f, 1.3f);
                if (front(m, t, 2.0, 0.2)) {
                    hit(m, t, 60, 0.5f, 10);
                    poison(t, m, 100, 0);
                }
            }
            // ---------- Marionete / Marionetista ----------
            case "marionete/laminas" -> {
                SubNet.send(level, SubNet.SLASH, 0, ahead(m, 1.4).add(0, 1.3, 0), right(m).add(0, 0.8, 0).normalize(), 1.4f, 0.16f, SubNet.STYLE_SUKUNA);
                SubNet.send(level, SubNet.SLASH, 0, ahead(m, 1.4).add(0, 1.3, 0), right(m).add(0, -0.8, 0).normalize(), 1.4f, 0.16f, SubNet.STYLE_SUKUNA);
                sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 1.4f);
                MobGrief.mow(m, m.position(), m.forward(), 2.8, 1.3, 2.5);
                if (front(m, t, 2.6, 0.3)) {
                    hit(m, t, 100, 0.6f, 10);
                    poison(t, m, 80, 0);
                }
            }
            case "marionete/jaula" -> {
                if (!front(m, t, 2.8, 0.3) || Freeze.isFrozen(t)) {
                    return;
                }
                // pulled into the chest: 2 s in the dark, then spat out
                Vec3 chest = ahead(m, 0.3).add(0, 0.2, 0);
                hold(m, t, 50, age -> chest);
                t.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 50, 0, false, false));
                NmNet.screen(t, 0, 42);
                hit(m, t, 80, 0.6f, 10);
                sound(m, SoundEvents.CHEST_CLOSE, 1.6f, 0.5f);
            }
            case "marionetista/agulhas" -> {
                Vec3 sleeve = m.position().add(0, 1.3, 0).add(m.forward().scale(0.4));
                for (int i = 0; i < 9; i++) {
                    Vec3 d = m.forward().add(m.getRandom().nextGaussian() * 0.12, m.getRandom().nextGaussian() * 0.06 - 0.05, m.getRandom().nextGaussian() * 0.12);
                    SubNet.send(level, SubNet.SENBON, 0, sleeve, d.normalize().scale(4.5), 0, 0, 0);
                }
                sound(m, SoundEvents.ARROW_SHOOT, 1.2f, 1.7f);
                MobGrief.shatterLine(m, sleeve, sleeve.add(m.forward().scale(4.5)), 0.7);
                if (front(m, t, 3.4, 0.5)) {
                    hit(m, t, 40, 0.4f, 10);
                    Combat.push(t, m.forward(), 0.4, 0.1);
                }
            }
            // ---------- Espadachim da Névoa ----------
            case "espadachim_nevoa/silencioso" -> {
                SubNet.send(level, SubNet.SLASH, 0, ahead(m, 1.5).add(0, 1.1, 0), right(m).add(0, -0.3, 0).normalize(), 1.7f, 0.22f, SubNet.STYLE_SUKUNA);
                sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.8f);
                MobGrief.mow(m, m.position(), m.forward(), 3.0, 1.8, 2.5);
                if (front(m, t, 2.6, 0.2)) {
                    hit(m, t, 140, 0.8f, 10);
                    Combat.push(t, m.forward(), 0.5, 0.2);
                }
            }
            case "espadachim_nevoa/clones" -> {
                // the original comes in from the side while the clones are being hit
                SubNet.send(level, SubNet.SLASH, 0, ahead(m, 1.5).add(0, 1.1, 0), right(m).add(0, 0.3, 0).normalize(), 1.7f, 0.22f, SubNet.STYLE_SUKUNA);
                sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.9f);
                MobGrief.mow(m, m.position(), m.forward(), 3.0, 1.8, 2.5);
                if (front(m, t, 2.8, 0.2)) {
                    hit(m, t, 120, 0.8f, 10);
                    Combat.push(t, m.forward(), 0.4, 0.2);
                }
            }
            // ---------- Tubarão de Água ----------
            case "tubarao_agua/salto" -> {
                sound(m, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.6f, 0.7f);
                SubNet.send(level, NmNet.SPLASH, 0, m.position(), Vec3.ZERO, 1.6f, 0, 0);
                MobGrief.soak(m, m.position(), 2.0);
                if (front(m, t, 2.6, 0.2)) {
                    hit(m, t, 120, 0.7f, 10);
                    // dragged one block toward the water
                    Combat.push(t, m.forward(), 0.45, 0.1);
                }
            }
            case "tubarao_agua/torpedo" -> {
                Vec3 from = m.position().add(0, 0.5, 0);
                SubNet.send(level, NmNet.WATER_BALL, 0, from, m.forward().scale(7), 0.6f, 6, 1);
                sound(m, SoundEvents.TRIDENT_RIPTIDE_2.value(), 1.4f, 1f);
                MobGrief.shatterLine(m, from, from.add(m.forward().scale(7)), 0.9);
                MobGrief.soak(m, from.add(m.forward().scale(7)), 1.8);
                for (LivingEntity o : Combat.alongLine(level, from, from.add(m.forward().scale(7)), 1.3, m)) {
                    if (o instanceof NarutoMob) {
                        continue;
                    }
                    hit(m, o, 100, 0.6f, 4);
                    Combat.push(o, m.forward(), 1.15, 0.3);
                    slow(o, m, 40, 1);
                }
            }
            // ---------- Caranguejo Gigante ----------
            case "caranguejo_gigante/pinca" -> {
                sound(m, SoundEvents.ARMADILLO_HURT, 1.2f, 0.6f);
                if (front(m, t, 1.8, 0.3)) {
                    hit(m, t, 50, 0.5f, 10);
                    hold(m, t, 20);
                }
            }
            case "caranguejo_gigante/bolhas" -> {
                Vec3 mouth = ahead(m, 0.6).add(0, 0.5, 0);
                Vec3 to = alive(t) ? t.getEyePosition() : ahead(m, 3).add(0, 1.4, 0);
                SubNet.send(level, NmNet.WATER_BALL, 1, mouth, to.subtract(mouth), 0.35f, 4, 2);
                sound(m, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.4f, 0.8f);
                if (front(m, t, 3.6, 0.5)) {
                    hit(m, t, 20, 0.3f, 4);
                    NmNet.screen(t, 2, 30);
                }
            }
            // ---------- Caçador da Névoa ----------
            case "cacador_nevoa/prisao" -> {
                if (!front(m, t, 1.6, 0.2) || !hold(m, t, 62)) {
                    return;
                }
                sound(m, t.position(), SoundEvents.PLAYER_SPLASH, 1.4f, 0.7f);
                SubNet.send(level, NmNet.BUBBLE, t.getId(), Combat.center(t), Vec3.ZERO, 1.1f, 0, 0);
                NmNet.screen(t, 3, 60);
            }
            case "cacador_nevoa/senbon" -> {
                Vec3 hand = m.position().add(0, 0.9, 0);
                Vec3 neck = alive(t) ? t.getEyePosition().add(0, -0.25, 0) : ahead(m, 4).add(0, 1.5, 0);
                for (int i = 0; i < 3; i++) {
                    Vec3 aim = neck.add((i - 1) * 0.12, 0, 0).subtract(hand);
                    SubNet.send(level, SubNet.SENBON, 0, hand, aim, 0, 0, 0);
                }
                sound(m, SoundEvents.ARROW_SHOOT, 1f, 1.9f);
                if (alive(t) && m.gap(t) < 7 && m.hasLineOfSight(t)) {
                    hit(m, t, 60, 0.4f, 10);
                    // in the neck: a second of paralysis
                    NrCommon.paralyze(level, t, 20);
                }
            }
            // ---------- Ninja da Chuva ----------
            case "ninja_chuva/senbon" -> {
                sound(m, m.blowSpot, SoundEvents.ARROW_SHOOT, 1.4f, 1.5f);
            }
            case "ninja_chuva/corrente" -> {
                Vec3 hand = m.position().add(0, 1.2, 0).add(right(m).scale(-0.3));
                Vec3 to = alive(t) ? Combat.center(t) : ahead(m, 6).add(0, 1, 0);
                if (to.distanceTo(hand) > 7) {
                    to = hand.add(to.subtract(hand).normalize().scale(7));
                }
                SubNet.send(level, NmNet.CHAIN, m.getId(), hand, to.subtract(hand), 0, 0, 0);
                sound(m, SoundEvents.CHAIN_HIT, 1.4f, 0.9f);
                if (alive(t) && m.gap(t) < 6.5 && m.hasLineOfSight(t) && !Freeze.isFrozen(t)) {
                    hit(m, t, 60, 0.5f, 10);
                    // pulled to the front of him
                    Vec3 pull = m.position().subtract(t.position());
                    double d = Math.max(0, pull.length() - 1.4);
                    Combat.push(t, pull, Math.min(2.2, 0.3 + d * 0.32), 0.3);
                    m.heldId = t.getId();
                }
            }
            // ---------- Garça de Papel ----------
            case "garca_papel/mergulho" -> {
                // a small paper bomb: it burns up in the blast
                Vec3 at = m.position().add(0, 0.3, 0);
                SubNet.send(level, SubNet.FIRE_BOOM, 0, at, new Vec3(ground(m, at).y, 0, 0), 1.3f, 0, m.getRandom().nextInt(50));
                sound(m, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.4f);
                wave(m, at, 2.4, 80, 0.6, 0.35, null);
                MobGrief.crater(m, at, 1.2, MobGrief.ROCK);
                MobGrief.scorch(m, at, 1.8, 1);
                m.discard();
            }
            case "garca_papel/corte" -> {
                sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.8f);
                if (alive(t) && t.distanceTo(m) < 2.2) {
                    hit(m, t, 40, 0.4f, 10);
                }
            }
            // ---------- Salamandra Venenosa ----------
            case "salamandra_venenosa/gas" -> {
                Vec3 c = m.position().add(m.forward().scale(2.5));
                SubNet.send(level, NmNet.GAS, m.getId(), c, m.forward(), 7, 60, 0);
                sound(m, SoundEvents.FIRE_EXTINGUISH, 2f, 0.4f);
                // the poison kills the grass and the plants under the cloud
                MobGrief.blight(m, c, 4.5);
            }
            case "salamandra_venenosa/rabada" -> {
                sound(m, SoundEvents.PLAYER_ATTACK_STRONG, 1.6f, 0.5f);
                MobGrief.mow(m, m.position(), m.forward().scale(-1), 3.6, 2.4, 2);
                MobGrief.heave(m, m.position().subtract(m.forward().scale(2.4)), 1.6, 2, MobGrief.EARTH);
                for (LivingEntity o : Combat.inRadius(level, m.position(), 3.8, m)) {
                    Vec3 to = o.position().subtract(m.position()).multiply(1, 0, 1).normalize();
                    if (o instanceof NarutoMob || to.dot(m.forward()) > 0.35) {
                        continue;
                    }
                    hit(m, o, 120, 0.7f, 10);
                    Combat.pushFrom(o, m.position(), 1.0, 0.35);
                }
            }
            // ---------- Zetsu Branco ----------
            case "zetsu_branco/agarrao" -> {
                SubNet.send(level, SubNet.DUST, 0, m.position(), Vec3.ZERO, 1.2f, 0, 0);
                sound(m, SoundEvents.ROOTED_DIRT_BREAK, 1.4f, 0.6f);
                MobGrief.heave(m, m.position(), 1.4, 3, MobGrief.EARTH);
                if (front(m, t, 1.6, -0.2) && hold(m, t, 32)) {
                    hit(m, t, 40, 0.5f, 10);
                    dizzy(m, t, 30);
                }
            }
            case "zetsu_branco/esporos" -> {
                sound(m, SoundEvents.SPORE_BLOSSOM_BREAK, 1.6f, 0.7f);
                if (front(m, t, 4, 0.3)) {
                    stickSpores(m, t, 12);
                }
            }
            // ---------- Corvo Uchiha ----------
            case "corvo_uchiha/genjutsu" -> {
                // looking away during the warning saves you
                if (!alive(t)) {
                    return;
                }
                Vec3 toCrow = m.getEyePosition().subtract(t.getEyePosition()).normalize();
                if (t.getLookAngle().dot(toCrow) < 0.55 || t.distanceTo(m) > 9) {
                    return;
                }
                sound(m, t.position(), SoundEvents.AMBIENT_CAVE.value(), 1f, 1.4f);
                dizzy(m, t, 40);
                NmNet.screen(t, 1, 56);
                SubNet.send(level, NmNet.FEATHERS, t.getId(), t.position(), Vec3.ZERO, 56, 0, 0);
                hit(m, t, 10, 0.3f, 1);
            }
            case "corvo_uchiha/revoada" -> {
            }
            // ---------- Clone de Madeira ----------
            case "clone_madeira/estacas" -> {
                Vec3 from = ground(m, ahead(m, 0.8));
                Vec3 to = alive(t) ? ground(m, t.position()) : ground(m, ahead(m, 5));
                Vec3 line = to.subtract(from).multiply(1, 0, 1);
                if (line.length() > 6) {
                    line = line.normalize().scale(6);
                }
                SubNet.send(level, NmNet.STAKES, m.getId(), from, line, 7, 0, 0);
                sound(m, SoundEvents.WOOD_BREAK, 1.6f, 0.5f);
                // the stakes tear the ground up along the line
                MobGrief.heaveLine(m, from, from.add(line), 1.1, MobGrief.EARTH);
                for (LivingEntity o : Combat.alongLine(level, from.add(0, 0.5, 0), from.add(line).add(0, 0.5, 0), 1.0, m)) {
                    if (o instanceof NarutoMob) {
                        continue;
                    }
                    hit(m, o, 80, 0.6f, 10);
                    // the last stake throws one and a half blocks up
                    Combat.push(o, line, 0.2, 0.62);
                }
            }
            case "clone_madeira/prisao" -> {
                if (!alive(t) || m.gap(t) > 5.5 || !m.hasLineOfSight(t)) {
                    return;
                }
                Vec3 spot = ground(m, t.position());
                SubNet.send(level, NmNet.CAGE, m.getId(), spot, Vec3.ZERO, 40, 0, 0);
                sound(m, spot, SoundEvents.WOOD_PLACE, 2f, 0.4f);
                MobGrief.heave(m, spot, 2.2, 4, MobGrief.EARTH);
                if (hold(m, t, 40)) {
                    hit(m, t, 40, 0.5f, 10);
                    m.blowSpot = spot;
                }
            }
            // ---------- Sapo Ninja ----------
            case "sapo_ninja/bala" -> {
                Vec3 mouth = ahead(m, 0.4).add(0, 0.5, 0);
                Vec3 to = alive(t) ? Combat.center(t) : ahead(m, 5).add(0, 0.8, 0);
                SubNet.send(level, NmNet.WATER_BALL, 0, mouth, to.subtract(mouth), 0.3f, Math.max(3, (int) (to.distanceTo(mouth) * 1.4)), 0);
                sound(m, SoundEvents.FROG_TONGUE, 1.2f, 0.6f);
                Vec3 splash = to;
                com.starkinhon.battlegroundsmod.combat.Tasks.later(Math.max(3, (int) (to.distanceTo(mouth) * 1.4)), () -> {
                    MobGrief.shatter(m, splash, 0.6);
                    MobGrief.soak(m, splash, 1.0);
                });
                if (alive(t) && m.gap(t) < 6.5 && m.hasLineOfSight(t)) {
                    hit(m, t, 40, 0.4f, 4);
                    Combat.push(t, t.position().subtract(m.position()), 0.55, 0.2);
                }
            }
            case "sapo_ninja/lingua" -> {
                if (!alive(t) || m.gap(t) > 4.2 || !m.hasLineOfSight(t) || Freeze.isFrozen(t)) {
                    return;
                }
                // the tongue sticks and pulls it in front of the frog
                Vec3 spot = ahead(m, 1.0);
                Vec3 start = t.position();
                hold(m, t, 22, age -> start.lerp(spot, Math.min(1, age / 8.0)));
                sound(m, t.position(), SoundEvents.SLIME_SQUISH, 1.3f, 1.1f);
            }
            // ---------- Sapo Gigante ----------
            case "sapo_gigante/salto" -> {
                Vec3 at = ground(m, m.position());
                SubNet.send(level, SubNet.GROUND_MARK, m.getId(), m.blowSpot, Vec3.ZERO, 0, 0, 1);
                SubNet.send(level, NmNet.SHOCK, 0, at.add(0, 0.1, 0), Vec3.ZERO, 5, 0, 0);
                SubNet.send(level, SubNet.CRACKS, 0, at, Vec3.ZERO, 3.4f, 11, 1);
                sound(m, at, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.5f);
                sound(m, at, SoundEvents.ANVIL_LAND, 1.4f, 0.4f);
                wave(m, at.add(0, 0.5, 0), 5, 200, 1.4, 0.6, null);
                // the landing sinks the ground under it and throws the earth around
                MobGrief.crater(m, at, 2.0, MobGrief.ROCK);
                MobGrief.heave(m, at, 4.5, 7, MobGrief.EARTH);
            }
            case "sapo_gigante/canhao" -> {
                Vec3 mouth = ahead(m, 1.8).add(0, 2.2, 0);
                Vec3 to = alive(t) ? Combat.center(t) : ahead(m, 8).add(0, 1, 0);
                if (to.distanceTo(mouth) > 10) {
                    to = mouth.add(to.subtract(mouth).normalize().scale(10));
                }
                int flight = 10;
                m.blowSpot = to;
                SubNet.send(level, NmNet.WATER_BALL, 0, mouth, to.subtract(mouth), 1.5f, flight, 0);
                sound(m, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2f, 0.5f);
                Vec3 burst = to;
                com.starkinhon.battlegroundsmod.combat.Tasks.later(flight, () -> {
                    if (!m.isAlive()) {
                        return;
                    }
                    SubNet.send(level, NmNet.SPLASH, 0, burst, Vec3.ZERO, 3f, 0, 0);
                    SubCommon.sound(level, burst, SoundEvents.GENERIC_SPLASH, 2f, 0.6f);
                    // the water hits like a wall: a dent of earth, mud all around
                    MobGrief.shatter(m, burst, 1.2);
                    MobGrief.crater(m, burst, 1.3, MobGrief.EARTH);
                    MobGrief.soak(m, burst, 3.0);
                    for (LivingEntity o : Combat.inRadius(level, burst, 3.2, m)) {
                        if (o instanceof NarutoMob) {
                            continue;
                        }
                        hit(m, o, 180, 0.8f, 4);
                        Combat.pushFrom(o, burst.subtract(0, 0.6, 0), 1.6, 0.45);
                        o.clearFire();
                    }
                });
            }
            // ---------- Inseto Gigante ----------
            case "inseto_gigante/chifrada" -> {
                sound(m, SoundEvents.ARMADILLO_SCUTE_DROP, 1.4f, 0.5f);
                if (front(m, t, 1.8, 0.2)) {
                    hit(m, t, 60, 0.5f, 10);
                    Combat.push(t, m.forward(), 0.4, 0.62);
                }
            }
            case "inseto_gigante/voo" -> {
                sound(m, SoundEvents.ARMADILLO_SCUTE_DROP, 1.4f, 0.7f);
                MobGrief.mow(m, m.position(), m.forward(), 2.2, 1.0, 2);
                if (front(m, t, 2.0, 0)) {
                    hit(m, t, 60, 0.5f, 10);
                    Combat.push(t, m.forward(), 0.5, 0.3);
                }
            }
            default -> {
            }
        }
    }

    // ---------- teleports (the blows that hide the body) ----------

    static void teleport(NarutoMob m, String mode, @Nullable LivingEntity t) {
        ServerLevel level = lvl(m);
        switch (m.id() + "/" + mode) {
            case "ninja_renegado/substituicao", "espadachim_nevoa/silencioso" -> {
                if (!alive(t)) {
                    return;
                }
                // reappears behind the target, facing its back
                Vec3 back = t.getLookAngle().multiply(1, 0, 1);
                back = back.lengthSqr() < 1.0E-3 ? t.position().subtract(m.position()).multiply(1, 0, 1) : back;
                Vec3 spot = t.position().subtract(back.normalize().scale(1.6));
                place(m, spot, t.position());
                if (mode.equals("silencioso")) {
                    SubNet.send(level, NmNet.MIST, m.getId(), m.position(), Vec3.ZERO, 6, 60, 0);
                } else {
                    SubNet.send(level, SubNet.POOF, 0, m.position().add(0, 0.9, 0), Vec3.ZERO, 0.8f, 0, 0);
                }
            }
            case "zetsu_branco/agarrao" -> {
                if (!alive(t)) {
                    return;
                }
                // comes up out of the ground right in front of the target
                Vec3 to = m.position().subtract(t.position()).multiply(1, 0, 1);
                to = to.lengthSqr() < 1.0E-3 ? m.forward().scale(-1) : to.normalize();
                place(m, t.position().add(to.scale(1.1)), t.position());
            }
            case "corvo_uchiha/revoada" -> {
                if (!alive(t)) {
                    return;
                }
                // the flock goes through the target and comes together again 6 blocks past where it took off
                Vec3 dir = t.position().subtract(m.position()).multiply(1, 0, 1);
                dir = dir.lengthSqr() < 1.0E-3 ? m.forward() : dir.normalize();
                Vec3 end = t.position().add(dir.scale(3));
                Vec3 from = m.position().add(0, 0.4, 0);
                SubNet.send(level, NmNet.FLOCK, m.getId(), from, end.add(0, 0.4, 0).subtract(from), 38, 0, 0);
                place(m, end, end.add(dir.scale(-1)));
            }
            case "garca_papel/mergulho" -> {
            }
            default -> {
            }
        }
    }

    // puts the body at spot (on the ground there, when there is one), facing look
    private static void place(NarutoMob m, Vec3 spot, Vec3 look) {
        ServerLevel level = lvl(m);
        BlockPos column = BlockPos.containing(spot);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        double y = Math.abs(top - spot.y) < 3 ? top : spot.y;
        Vec3 at = new Vec3(spot.x, y, spot.z);
        if (!level.noCollision(m, m.getBoundingBox().move(at.subtract(m.position())))) {
            return;
        }
        double dx = look.x - at.x, dz = look.z - at.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        m.teleportTo(at.x, at.y, at.z);
        m.setYRot(yaw);
        m.yBodyRot = yaw;
        m.yHeadRot = yaw;
    }

    static void end(NarutoMob m, String mode, @Nullable LivingEntity t) {
        HIT_ONCE.remove(m.getId());
        switch (m.id() + "/" + mode) {
            case "cobra_gigante/engolir", "marionete/jaula" -> {
                if (holding(m, t)) {
                    spit(m, t, 1.0);
                }
            }
            case "cacador_nevoa/prisao" -> {
                if (holding(m, t)) {
                    freeBubble(m, t);
                }
            }
            case "clone_madeira/prisao" -> {
                SubNet.send(lvl(m), NmNet.CAGE, m.getId(), m.blowSpot, Vec3.ZERO, 0, 0, 1);
                letGo(m);
            }
            case "ninja_chuva/corrente" -> {
                SubNet.send(lvl(m), NmNet.CHAIN, m.getId(), m.position(), Vec3.ZERO, 0, 0, 1);
                m.heldId = -1;
            }
            case "ninken/gatsuga" -> SubNet.send(lvl(m), NmNet.DRILL, m.getId(), Combat.center(m), m.forward(), 0, 0, 1);
            default -> letGo(m);
        }
    }

    // out of the snake's mouth / the puppet's chest, thrown ahead
    private static void spit(NarutoMob m, LivingEntity t, double strength) {
        letGo(m);
        t.removeEffect(MobEffects.INVISIBILITY);
        NmNet.screen(t, 0, 0);
        Combat.push(t, m.forward(), strength, 0.4);
        sound(m, t.position(), SoundEvents.LLAMA_SPIT, 1.6f, 0.5f);
        SubNet.send(lvl(m), SubNet.DUST, 0, t.position(), Vec3.ZERO, 0.9f, 0, 0);
    }

    private static void freeBubble(NarutoMob m, LivingEntity t) {
        letGo(m);
        NmNet.screen(t, 3, 0);
        SubNet.send(lvl(m), NmNet.BUBBLE, t.getId(), Combat.center(t), Vec3.ZERO, 1.1f, 0, 1);
        SubNet.send(lvl(m), NmNet.SPLASH, 0, Combat.center(t), Vec3.ZERO, 1.2f, 0, 0);
        sound(m, t.position(), SoundEvents.PLAYER_SPLASH, 1.4f, 1f);
    }

    // ---------- answers to being hit ----------

    // The Substituição: a strong blow (2+ hearts after the creature's own resistances aside) hits a log instead.
    static boolean answer(NarutoMob m, LivingEntity attacker, DamageSource source, float amount) {
        if (!m.id().equals("ninja_renegado") || m.attacking() || amount < 3 || !m.ready(0) || Stun.real(m)) {
            return false;
        }
        if (!m.blow(0, attacker)) {
            return false;
        }
        m.setTarget(attacker);
        return true;
    }

    static void hurtBy(NarutoMob m, LivingEntity attacker, float amount) {
        LivingEntity held = m.heldId >= 0 && m.level().getEntity(m.heldId) instanceof LivingEntity l ? l : null;
        switch (m.id()) {
            // hitting it from inside makes it spit sooner
            case "cobra_gigante" -> {
                if (held != null && held == attacker) {
                    spit(m, held, 1.4);
                }
            }
            // a blow on the hunter (by the prisoner or anyone else) bursts the bubble
            case "cacador_nevoa" -> {
                if (held != null && amount >= 1) {
                    freeBubble(m, held);
                }
            }
            // hitting the centipede, the zetsu or the crab lets go
            case "centopeia", "zetsu_branco", "caranguejo_gigante", "escorpiao_areia" -> {
                if (held != null) {
                    letGo(m);
                    held.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                }
            }
            default -> {
            }
        }
        // a strong blow on the marionetista's threads (on the marionetista) lets its puppet's prisoner out
        if (m.id().equals("marionetista")) {
            NarutoMob puppet = m.partner();
            if (puppet != null && puppet.heldId >= 0 && puppet.level().getEntity(puppet.heldId) instanceof LivingEntity in) {
                spit(puppet, in, 0.8);
            }
        }
    }

    // ---------- the spores ----------

    private static final String SPORES = "nr_spores";

    private static void stickSpores(NarutoMob m, LivingEntity t, int count) {
        SubCommon.setState(t, SPORES, 120);
        t.getPersistentData().putInt(SPORES, count);
        t.getPersistentData().putInt(SPORES + "_by", m.getId());
        slow(t, m, 120, 1);
        SubNet.send(lvl(m), NmNet.SPORES, t.getId(), t.position(), Vec3.ZERO, count, 0, 0);
    }

    // each spore stuck sucks chakra (stamina) until it bursts; water or a dodge washes them off
    static void sporeTick(LivingEntity t) {
        if (!SubCommon.hasState(t, SPORES) || !(t.level() instanceof ServerLevel level)) {
            if (t.getPersistentData().contains(SPORES) && t.level() instanceof ServerLevel level) {
                t.getPersistentData().remove(SPORES);
                SubNet.send(level, NmNet.SPORES, t.getId(), t.position(), Vec3.ZERO, 0, 0, 0);
            }
            return;
        }
        int left = t.getPersistentData().getInt(SPORES);
        if (t.isInWater()) {
            left = 0;
        } else if (t.tickCount % 10 == 0) {
            Stamina.drain(t, 1.5);
            left--;
        }
        if (left <= 0) {
            SubCommon.clearState(t, SPORES);
            t.getPersistentData().remove(SPORES);
            t.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            SubNet.send(level, NmNet.SPORES, t.getId(), t.position(), Vec3.ZERO, 0, 0, 0);
            return;
        }
        t.getPersistentData().putInt(SPORES, left);
        if (t.tickCount % 10 == 0) {
            SubNet.send(level, NmNet.SPORES, t.getId(), t.position(), Vec3.ZERO, left, 0, 0);
        }
    }

    // ---------- the water clones ----------

    private static void waterClones(NarutoMob m, @Nullable LivingEntity t) {
        ServerLevel level = lvl(m);
        for (int i = 0; i < 2; i++) {
            Vec3 side = right(m).scale(i == 0 ? 1.6 : -1.6).add(m.forward().scale(1.2));
            Vec3 at = ground(m, m.position().add(side));
            NarutoMob clone = (NarutoMob) m.getType().create(level);
            if (clone == null) {
                continue;
            }
            clone.moveTo(at.x, at.y, at.z, m.getYRot(), 0);
            clone.setVariant(m.variant());
            clone.makeClone(140);
            if (t != null) {
                clone.setTarget(t);
            }
            level.addFreshEntity(clone);
            m.spawned.add(clone.getId());
            SubNet.send(level, NmNet.SPLASH, 0, at, Vec3.ZERO, 1.2f, 0, 0);
        }
        sound(m, SoundEvents.PLAYER_SPLASH, 1.4f, 0.8f);
    }

    static void burstClone(NarutoMob m) {
        SubNet.send(lvl(m), NmNet.SPLASH, 0, Combat.center(m), Vec3.ZERO, 1.4f, 0, 0);
        sound(m, SoundEvents.PLAYER_SPLASH, 1.2f, 1.2f);
        m.discard();
    }

    // ---------- the marionetista ----------

    static void spawnPuppet(NarutoMob pm, ServerLevel level) {
        NarutoMob puppet = (NarutoMob) ModEntities.naruto("marionete").create(level);
        if (puppet == null) {
            return;
        }
        Vec3 at = pm.position().add(pm.forward().scale(3.2));
        at = ground(pm, at);
        puppet.moveTo(at.x, at.y, at.z, pm.getYRot(), 0);
        puppet.setVariant(pm.variant());
        level.addFreshEntity(puppet);
        puppet.setPersistenceRequired();
        pm.setLink(puppet.getId());
        puppet.setLink(pm.getId());
    }

    // ---------- every tick ----------

    static void idle(NarutoMob m) {
        ServerLevel level = lvl(m);
        if (m.sleep > 0) {
            m.sleep--;
        }
        switch (m.id()) {
            case "marionetista" -> {
                NarutoMob puppet = m.partner();
                if (puppet == null) {
                    m.setLink(-1);
                    return;
                }
                if (m.getTarget() == null && puppet.getTarget() != null) {
                    m.setTarget(puppet.getTarget());
                } else if (puppet.getTarget() == null && m.getTarget() != null) {
                    puppet.setTarget(m.getTarget());
                }
                // the blue threads give away where it hides (drawn between the two bodies by the client)
                if (m.tickCount % 10 == 0) {
                    SubNet.send(level, NmNet.THREADS, m.getId(), m.position(), Vec3.ZERO, 1, puppet.getId(), 0);
                }
            }
            case "marionete" -> {
                // without its marionetista, a heap of wood
                if (m.tickCount > 20 && m.partner() == null) {
                    collapse(m);
                }
            }
            case "ninja_chuva" -> {
                // faster in the rain
                var speed = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                if (speed != null) {
                    double want = m.spec().speed * (level.isRainingAt(m.blockPosition()) ? 1.3 : 1);
                    if (Math.abs(speed.getBaseValue() - want) > 1.0E-3) {
                        speed.setBaseValue(want);
                    }
                }
            }
            case "tubarao_agua" -> {
                // out of the water it gives up on its target
                if (!m.isInWater() && m.getTarget() != null && !m.attacking()) {
                    m.setTarget(null);
                }
            }
            case "espadachim_nevoa" -> {
                // when he sees you he raises the mist
                if (!m.isClone() && m.getTarget() != null && m.tickCount % 40 == 0) {
                    SubNet.send(level, NmNet.MIST, m.getId(), m.position(), Vec3.ZERO, 12, 60, 0);
                }
            }
            case "ninja_som" -> {
                LivingEntity t = m.getTarget();
                if (t != null && m.heldId >= 0) {
                    m.heldId = -1;
                }
            }
            default -> {
            }
        }
    }

    // the puppet falls in a heap when its threads are cut
    private static void collapse(NarutoMob m) {
        SubNet.send(lvl(m), SubNet.DUST, 0, m.position(), Vec3.ZERO, 1.2f, 0, 0);
        sound(m, SoundEvents.WOOD_BREAK, 1.4f, 0.6f);
        m.hurt(m.damageSources().genericKill(), Float.MAX_VALUE);
    }

    static void died(NarutoMob m) {
        letGo(m);
        if (m.id().equals("marionetista")) {
            NarutoMob puppet = m.partner();
            if (puppet != null) {
                collapse(puppet);
            }
        }
        if (m.id().equals("espadachim_nevoa")) {
            for (int id : m.spawned) {
                if (m.level().getEntity(id) instanceof NarutoMob c && c.isClone()) {
                    burstClone(c);
                }
            }
        }
    }

    // ---------- voices ----------

    @Nullable
    static SoundEvent ambient(NarutoMob m) {
        return switch (m.id()) {
            case "ninken" -> SoundEvents.WOLF_PANT;
            case "tigre" -> SoundEvents.POLAR_BEAR_AMBIENT;
            case "cobra_gigante", "salamandra_venenosa" -> SoundEvents.CAT_HISS;
            case "centopeia", "escorpiao_areia", "caranguejo_gigante", "inseto_gigante" -> SoundEvents.SPIDER_AMBIENT;
            case "tubarao_agua" -> SoundEvents.DOLPHIN_AMBIENT_WATER;
            case "garca_papel" -> SoundEvents.BOOK_PAGE_TURN;
            case "corvo_uchiha" -> SoundEvents.PARROT_AMBIENT;
            case "sapo_ninja", "sapo_gigante" -> SoundEvents.FROG_AMBIENT;
            case "marionete" -> null;
            default -> null;
        };
    }

    static SoundEvent hurtSound(NarutoMob m) {
        return switch (m.id()) {
            case "ninken" -> SoundEvents.WOLF_HURT;
            case "tigre" -> SoundEvents.POLAR_BEAR_HURT;
            case "cobra_gigante", "salamandra_venenosa" -> SoundEvents.SNIFFER_HURT;
            case "centopeia", "escorpiao_areia", "inseto_gigante" -> SoundEvents.SPIDER_HURT;
            case "caranguejo_gigante" -> SoundEvents.ARMADILLO_HURT;
            case "tubarao_agua" -> SoundEvents.DOLPHIN_HURT;
            case "garca_papel" -> SoundEvents.BOOK_PUT;
            case "corvo_uchiha" -> SoundEvents.PARROT_HURT;
            case "sapo_ninja", "sapo_gigante" -> SoundEvents.FROG_HURT;
            case "marionete", "clone_madeira" -> SoundEvents.WOOD_HIT;
            default -> SoundEvents.PLAYER_HURT;
        };
    }

    static SoundEvent deathSound(NarutoMob m) {
        return switch (m.id()) {
            case "ninken" -> SoundEvents.WOLF_DEATH;
            case "tigre" -> SoundEvents.POLAR_BEAR_DEATH;
            case "cobra_gigante", "salamandra_venenosa" -> SoundEvents.SNIFFER_DEATH;
            case "centopeia", "escorpiao_areia", "inseto_gigante" -> SoundEvents.SPIDER_DEATH;
            case "caranguejo_gigante" -> SoundEvents.ARMADILLO_DEATH;
            case "tubarao_agua" -> SoundEvents.DOLPHIN_DEATH;
            case "garca_papel" -> SoundEvents.BOOK_PAGE_TURN;
            case "corvo_uchiha" -> SoundEvents.PARROT_DEATH;
            case "sapo_ninja", "sapo_gigante" -> SoundEvents.FROG_DEATH;
            case "marionete", "clone_madeira" -> SoundEvents.WOOD_BREAK;
            case "zetsu_branco" -> SoundEvents.ROOTED_DIRT_BREAK;
            default -> SoundEvents.PLAYER_DEATH;
        };
    }

    static float pitch(NarutoMob m) {
        return switch (m.id()) {
            case "cobra_gigante", "salamandra_venenosa", "sapo_gigante" -> 0.5f;
            case "tigre" -> 0.9f;
            case "corvo_uchiha", "sapo_ninja", "garca_papel" -> 1.2f;
            case "caranguejo_gigante", "inseto_gigante" -> 0.7f;
            case "ninken" -> 1.1f;
            default -> 1f;
        };
    }

    // every spawned thing of the moves that needs its own tick (the spores) runs from here
    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.EntityTickEvent.Post e) -> {
            if (e.getEntity() instanceof LivingEntity l && !l.level().isClientSide && l.tickCount % 2 == 0) {
                sporeTick(l);
            }
        });
    }
}
