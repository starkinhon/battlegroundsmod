package com.starkinhon.battlegroundsmod.animal.dbmob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Spec;
import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// The Dragon Ball creatures' blows (DbMobSpec says when; the preview, tools/mobs/dbmobs, says how they look). The tick
// numbers are the preview's frames (timings.txt), so the hit lands when the baked animation shows it. Blows that hit
// many times (the Punho do Lobo, the machine gun, the ki pistol, the bites) add their damage up and land it in a few
// bigger packets, so the tier Shield never eats each little hit (#291/#307). Every hold has its own length (combate
// limpo: nobody is in two prisons) and ends with the blow, so a blow cut short never leaves one on.
public final class DbMobMoves {
    private DbMobMoves() {
    }

    static final NarutoMob.Kit KIT = new NarutoMob.Kit() {
        public Spec spec(String id) {
            return DbMobSpec.get(id);
        }

        public float[][] travel(String id, String mode) {
            return DbTravel.of(id, mode);
        }

        public boolean canUse(NarutoMob m, String mode, LivingEntity t) {
            return DbMobMoves.canUse((DbMob) m, mode, t);
        }

        public Vec3 fleeFrom(NarutoMob m) {
            return DbMobMoves.fleeFrom((DbMob) m);
        }

        public void start(NarutoMob m, String mode, LivingEntity t) {
            DbMobMoves.start((DbMob) m, mode, t);
        }

        public void tick(NarutoMob m, String mode, int tick, LivingEntity t) {
            DbMobMoves.tick((DbMob) m, mode, tick, t);
        }

        public void impact(NarutoMob m, String mode, LivingEntity t) {
            DbMobMoves.impact((DbMob) m, mode, t);
        }

        public void end(NarutoMob m, String mode, LivingEntity t) {
            DbMobMoves.end((DbMob) m, mode, t);
        }

        public void teleport(NarutoMob m, String mode, LivingEntity t) {
            DbMobMoves.teleport((DbMob) m, mode, t);
        }

        public boolean answer(NarutoMob m, LivingEntity attacker, DamageSource source, float amount) {
            return DbMobMoves.answer((DbMob) m, attacker, source, amount);
        }

        public void hurtBy(NarutoMob m, LivingEntity attacker, float amount) {
            DbMobMoves.hurtBy((DbMob) m, attacker, amount);
        }

        public void died(NarutoMob m) {
            DbMobMoves.died((DbMob) m);
        }

        public void idle(NarutoMob m) {
            DbMobMoves.idle((DbMob) m);
        }

        public void drop(NarutoMob m, ServerLevel level, boolean byPlayer) {
            DbMobMoves.drop((DbMob) m, level, byPlayer);
        }

        public SoundEvent ambient(NarutoMob m) {
            return DbMobMoves.ambient((DbMob) m);
        }

        public SoundEvent hurtSound(NarutoMob m) {
            return DbMobMoves.hurtSound((DbMob) m);
        }

        public SoundEvent deathSound(NarutoMob m) {
            return DbMobMoves.deathSound((DbMob) m);
        }

        public float pitch(NarutoMob m) {
            return DbMobMoves.pitch((DbMob) m);
        }
    };

    // ---------- state kept through a blow ----------

    // what a blow has hit so far (charges hit each one once) and the damage it still owes them (packets)
    private static final Map<Integer, Set<Integer>> HIT_ONCE = new HashMap<>();
    private static final Map<Integer, Map<Integer, Float>> OWED = new HashMap<>();
    // a stream effect's id per mob (beams, balls, pillars, drains)
    private static final Map<Integer, Integer> STREAM = new HashMap<>();
    // where a blow aimed when it locked (a laser, the rocket, the Dodonpa): the point it fires at
    private static final Map<Integer, Vec3> AIM = new HashMap<>();
    // hits taken by a mob holding someone (three quick ones free them)
    private static final Map<Integer, Integer> STRUGGLE = new HashMap<>();
    // Yakon's swallowing: how much strong ki it took
    private static final Map<Integer, Float> SWALLOWED = new HashMap<>();
    // Babidi's last teleport (game time)
    private static final Map<Integer, Long> BLINKED = new HashMap<>();

    // ground effects that last: the acid puddles, the pirate's bombs, the burning breath, the bleeding
    private abstract static class Zone {
        int age;

        // true when done
        abstract boolean tick(ServerLevel level);
    }

    private static final List<Zone> ZONES = new ArrayList<>();
    private static final Map<Integer, Integer> BLEEDING = new HashMap<>();

    public static void register() {
        // Shift on the flying dragon dives (DbMob.travel) instead of dropping the rider out of the sky
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityMountEvent e) -> {
            if (e.isDismounting() && !e.getLevel().isClientSide && e.getEntityBeingMounted() instanceof DbMob d && d.isDragon() && d.isAlive()
                    && !d.onGround() && !d.isInWater() && e.getEntityMounting() instanceof Player p && p.isAlive() && !p.isRemoved()
                    && p.isShiftKeyDown()) {
                e.setCanceled(true);
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (!(e.getLevel() instanceof ServerLevel level) || ZONES.isEmpty()) {
                return;
            }
            Iterator<Zone> it = ZONES.iterator();
            List<Zone> mine = new ArrayList<>();
            while (it.hasNext()) {
                Zone z = it.next();
                if (z instanceof LevelZone lz && lz.level != level) {
                    continue;
                }
                mine.add(z);
            }
            for (Zone z : mine) {
                z.age++;
                if (z.tick(level)) {
                    ZONES.remove(z);
                }
            }
        });
    }

    private abstract static class LevelZone extends Zone {
        final ServerLevel level;

        LevelZone(ServerLevel level) {
            this.level = level;
        }
    }

    public static void resetStatics() {
        HIT_ONCE.clear();
        OWED.clear();
        STREAM.clear();
        AIM.clear();
        STRUGGLE.clear();
        SWALLOWED.clear();
        BLINKED.clear();
        ZONES.clear();
        BLEEDING.clear();
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

    // a point on the body: forward, right, up (blocks)
    private static Vec3 body(NarutoMob m, double fwd, double side, double up) {
        return m.position().add(m.forward().scale(fwd)).add(right(m).scale(side)).add(0, up, 0);
    }

    private static boolean alive(LivingEntity t) {
        return t != null && t.isAlive() && !(t instanceof Player p && p.isSpectator());
    }

    private static void sound(NarutoMob m, SoundEvent s, float vol, float pitch) {
        SubCommon.sound(lvl(m), m.position(), s, vol, pitch);
    }

    private static void sound(NarutoMob m, Vec3 at, SoundEvent s, float vol, float pitch) {
        SubCommon.sound(lvl(m), at, s, vol, pitch);
    }

    private static boolean ally(DbMob m, LivingEntity t) {
        // the creatures never hurt their own kind, nor Babidi's side its own side
        return t instanceof DbMob d && (d.getType() == m.getType() || m.master() >= 0 && (d.getId() == m.master() || d.master() == m.master())
                || d.master() == m.getId() || m.master() == d.getId());
    }

    private static void hit(DbMob m, LivingEntity t, float dmg, float size, int color) {
        if (ally(m, t)) {
            return;
        }
        Combat.hurt(m, t, dmg);
        DbCommon.hit(lvl(m), Combat.center(t), size, color);
    }

    private static void magic(DbMob m, LivingEntity t, float dmg) {
        if (!ally(m, t)) {
            Combat.hurtMagic(m, t, dmg);
        }
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

    private static List<LivingEntity> cone(DbMob m, Vec3 from, Vec3 dir, double length, double minDot) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 flat = SubCommon.flat(dir).normalize();
        for (LivingEntity t : Combat.inRadius(lvl(m), from, length, m)) {
            if (ally(m, t)) {
                continue;
            }
            Vec3 to = SubCommon.flat(t.position().subtract(from));
            if (to.lengthSqr() < 0.5 || to.normalize().dot(flat) > minDot) {
                out.add(t);
            }
        }
        return out;
    }

    private static boolean once(DbMob m, LivingEntity t) {
        return HIT_ONCE.computeIfAbsent(m.getId(), k -> new HashSet<>()).add(t.getId());
    }

    // damage owed to t by this blow, landed later as one packet (pay)
    private static void owe(DbMob m, LivingEntity t, float dmg) {
        OWED.computeIfAbsent(m.getId(), k -> new HashMap<>()).merge(t.getId(), dmg, Float::sum);
    }

    private static void pay(DbMob m, boolean asMagic) {
        Map<Integer, Float> owed = OWED.remove(m.getId());
        if (owed == null) {
            return;
        }
        for (Map.Entry<Integer, Float> e : owed.entrySet()) {
            if (lvl(m).getEntity(e.getKey()) instanceof LivingEntity t && alive(t)) {
                if (asMagic) {
                    magic(m, t, e.getValue());
                } else {
                    hit(m, t, e.getValue(), 0.6f, 10);
                }
            }
        }
    }

    private static boolean hold(DbMob m, LivingEntity t, int ticks) {
        if (Freeze.isFrozen(t) || t instanceof SoulBoss b && b.isBoss() && ticks > 40) {
            return false;
        }
        m.release = Freeze.hold(t, ticks);
        m.heldId = t.getId();
        STRUGGLE.remove(m.getId());
        return true;
    }

    private static boolean hold(DbMob m, LivingEntity t, int ticks, java.util.function.IntFunction<Vec3> anchor) {
        if (Freeze.isFrozen(t)) {
            return false;
        }
        m.release = Freeze.hold(t, ticks, anchor);
        m.heldId = t.getId();
        STRUGGLE.remove(m.getId());
        return true;
    }

    private static void letGo(DbMob m) {
        if (m.release != null) {
            m.release.run();
            m.release = null;
        }
        m.heldId = -1;
    }

    private static boolean holding(DbMob m, LivingEntity t) {
        return t != null && m.heldId == t.getId() && m.release != null;
    }

    private static void dizzy(DbMob m, LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks + 40, 0, false, false), m);
        SubNet.send(lvl(m), SubNet.DIZZY, t.getId(), t.position(), Vec3.ZERO, ticks, 0, 0);
        if (t instanceof Mob mob && !(t instanceof SoulBoss)) {
            mob.setTarget(null);
        }
    }

    private static Vec3 ground(NarutoMob m, Vec3 at) {
        return SubCommon.ground(lvl(m), at);
    }

    private static Vec3 clip(DbMob m, Vec3 from, Vec3 to) {
        HitResult hit = lvl(m).clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, m));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    private static void wave(DbMob m, Vec3 at, double r, float dmg, double push, double up, boolean groundOnly) {
        for (LivingEntity t : Combat.inRadius(lvl(m), at, r, m)) {
            if (ally(m, t) || groundOnly && !t.onGround() && t.getY() - at.y > 1.2) {
                continue;
            }
            double k = 1 - 0.5 * Math.min(1, t.position().distanceTo(at) / r);
            hit(m, t, (float) (dmg * k), 0.7f, 10);
            Combat.pushFrom(t, at, push * k, up * k);
        }
    }

    private static int stream(DbMob m) {
        return STREAM.computeIfAbsent(m.getId(), k -> SubNet.newId());
    }

    private static void endStream(DbMob m, int kind) {
        Integer id = STREAM.remove(m.getId());
        if (id != null) {
            SubNet.send(lvl(m), kind, id, m.position(), Vec3.ZERO, 0, 0, 1);
        }
    }

    // a small hole of soft blocks, only with the destruction gamerule
    private static void dent(DbMob m, Vec3 at, double radius) {
        if (BlockRules.canBreak(lvl(m))) {
            DbCommon.bigCrater(lvl(m), at, radius, radius * 0.6, 3);
        }
    }

    // weak blocks (plants, leaves, logs, glass) in the way of a charge, only with the destruction gamerule
    private static void smash(DbMob m, Vec3 at, double r, boolean logs) {
        ServerLevel level = lvl(m);
        if (!BlockRules.canBreak(level)) {
            return;
        }
        BlockPos c = BlockPos.containing(at);
        int ri = (int) Math.ceil(r);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-ri, 0, -ri), c.offset(ri, (int) Math.ceil(m.getBbHeight()), ri))) {
            if (p.distToCenterSqr(at) > r * r * 1.5) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.getDestroySpeed(level, p) < 0) {
                continue;
            }
            boolean weak = s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.REPLACEABLE) || s.is(Blocks.GLASS)
                    || s.is(BlockTags.SAPLINGS) || logs && s.is(BlockTags.LOGS) || s.getDestroySpeed(level, p) <= 0.3f;
            if (weak) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
    }

    private static void face(DbMob m, Vec3 at) {
        double dx = at.x - m.getX(), dz = at.z - m.getZ();
        if (dx * dx + dz * dz > 1.0E-4) {
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            m.setYRot(yaw);
            m.yBodyRot = yaw;
            m.yHeadRot = yaw;
        }
    }

    private static void bleed(DbMob m, LivingEntity t, int ticks) {
        BLEEDING.put(t.getId(), ticks);
        ServerLevel level = lvl(m);
        ZONES.add(new LevelZone(level) {
            boolean tick(ServerLevel l) {
                Integer left = BLEEDING.get(t.getId());
                if (left == null || left <= 0 || !alive(t)) {
                    BLEEDING.remove(t.getId());
                    return true;
                }
                BLEEDING.put(t.getId(), left - 1);
                if (left % 20 == 0) {
                    Combat.hurtMagic(m.isAlive() ? m : t, t, 8);
                    SubNet.send(l, SubNet.BLOOD_SPLASH, 0, Combat.center(t), new Vec3(0, -1, 0), 0.4f, 0, 0);
                }
                return false;
            }
        });
    }

    // ---------- the AI's gates ----------

    static boolean canUse(DbMob m, String mode, LivingEntity t) {
        if (m.isDragon() && (m.tamed() || m.isVehicle())) {
            // a tamed dragon only breathes when its rider says
            return false;
        }
        switch (m.id() + "/" + mode) {
            case "dino_chifrudo/clava" -> {
                // only who stands beside or behind it
                Vec3 to = SubCommon.flat(t.position().subtract(m.position()));
                return to.lengthSqr() < 0.01 || to.normalize().dot(m.forward()) < 0.2;
            }
            case "dino_chifrudo/chifrada" -> {
                Vec3 to = SubCommon.flat(t.position().subtract(m.position()));
                return to.lengthSqr() > 0.01 && to.normalize().dot(m.forward()) > 0.6;
            }
            case "peixe_gigante/salto", "peixe_gigante/abocanhar" -> {
                return m.isInWater();
            }
            case "babidi/controle" -> {
                return m.hasLineOfSight(t);
            }
            case "pterodactilo/mergulho" -> {
                // dives from above
                return m.getY() > t.getY() + 1.5;
            }
            default -> {
                return true;
            }
        }
    }

    @Nullable
    static Vec3 fleeFrom(DbMob m) {
        LivingEntity t = m.getTarget();
        if (t == null || m.attacking()) {
            return null;
        }
        float hp = m.getHealth() / m.getMaxHealth();
        switch (m.id()) {
            case "raptor", "cell_jr" -> {
                // the last one left, nearly dead, runs (the Cell Jr. laughing)
                if (hp < 0.25f && m.level().getEntitiesOfClass(DbMob.class, m.getBoundingBox().inflate(14), o -> o != m && o.getType() == m.getType()).isEmpty()) {
                    return t.position();
                }
            }
            case "babidi" -> {
                // keeps behind the ones he controls
                if (m.gap(t) < 4) {
                    return t.position();
                }
            }
            default -> {
            }
        }
        return null;
    }

    // ---------- start ----------

    static void start(DbMob m, String mode, @Nullable LivingEntity t) {
        HIT_ONCE.remove(m.getId());
        OWED.remove(m.getId());
        AIM.remove(m.getId());
        switch (m.id() + "/" + mode) {
            case "saibaman/agarrao" -> sound(m, SoundEvents.WITCH_CELEBRATE, 1.2f, 1.8f);
            case "saibaman/acido" -> sound(m, SoundEvents.LLAMA_SPIT, 1f, 0.6f);
            case "bandido_deserto/lobo" -> sound(m, SoundEvents.WOLF_GROWL, 1.2f, 0.9f);
            case "bandido_deserto/areia" -> sound(m, SoundEvents.SAND_BREAK, 1.4f, 0.8f);
            case "soldado_red_ribbon/metralhadora" -> sound(m, SoundEvents.CROSSBOW_LOADING_END.value(), 1.2f, 1.3f);
            case "soldado_red_ribbon/bazuca" -> sound(m, SoundEvents.CROSSBOW_LOADING_END.value(), 1.2f, 0.6f);
            case "pirata/sabre" -> sound(m, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, 1.2f);
            case "pirata/bomba" -> sound(m, SoundEvents.FLINTANDSTEEL_USE, 1.4f, 0.9f);
            case "yeti/rugido" -> sound(m, SoundEvents.POLAR_BEAR_WARNING, 2f, 0.5f);
            case "yeti/pisao" -> sound(m, SoundEvents.POLAR_BEAR_WARNING, 1.6f, 0.7f);
            case "raptor/bote" -> sound(m, SoundEvents.PARROT_IMITATE_RAVAGER, 1.4f, 1.3f);
            case "raptor/garras" -> sound(m, SoundEvents.PARROT_IMITATE_VINDICATOR, 1.4f, 0.9f);
            case "dino_chifrudo/chifrada" -> sound(m, SoundEvents.RAVAGER_STEP, 1.6f, 0.8f);
            case "dino_chifrudo/clava" -> sound(m, SoundEvents.RAVAGER_STEP, 1.4f, 1.1f);
            case "tiranossauro/investida", "tiranossauro/pisao" -> sound(m, SoundEvents.RAVAGER_ROAR, 3f, 0.5f);
            case "pterodactilo/mergulho" -> sound(m, SoundEvents.PHANTOM_SWOOP, 2f, 0.8f);
            case "pterodactilo/ventania" -> sound(m, SoundEvents.PHANTOM_FLAP, 2f, 0.6f);
            case "dragao/fogo" -> sound(m, SoundEvents.ENDER_DRAGON_GROWL, 1.6f, 1.2f);
            case "soldado_freeza/pistola" -> sound(m, SoundEvents.BEACON_ACTIVATE, 0.9f, 2f);
            case "soldado_freeza/rajada", "soldado_freeza/bomba" -> sound(m, SoundEvents.BEACON_POWER_SELECT, 1f, 1.6f);
            case "cell_jr/kame" -> sound(m, SoundEvents.BEACON_AMBIENT, 1.6f, 1.6f);
            case "cell_jr/sumir" -> sound(m, SoundEvents.WITCH_CELEBRATE, 1f, 2f);
            case "yakon/escuro" -> sound(m, SoundEvents.WARDEN_ROAR, 1.6f, 1.2f);
            case "yakon/devorar" -> sound(m, SoundEvents.WARDEN_SNIFF, 2f, 0.6f);
            case "tigre_sabre/bote" -> sound(m, SoundEvents.POLAR_BEAR_WARNING, 1.3f, 1.1f);
            case "tigre_sabre/patada" -> sound(m, SoundEvents.POLAR_BEAR_WARNING, 1.6f, 0.9f);
            case "peixe_gigante/salto" -> sound(m, SoundEvents.DOLPHIN_SWIM, 1.6f, 0.5f);
            case "peixe_gigante/abocanhar" -> sound(m, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.6f, 0.6f);
            case "androide_gero/laser" -> sound(m, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.8f);
            case "androide_gero/canhao" -> sound(m, SoundEvents.PISTON_EXTEND, 1.4f, 0.6f);
            case "tao_pai_pai/dodonpa" -> sound(m, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 1.8f);
            case "tao_pai_pai/pilar" -> {
                sound(m, SoundEvents.STONE_BREAK, 2f, 0.6f);
                SubNet.send(lvl(m), SubNet.CRACKS, 0, ground(m, ahead(m, 1)), Vec3.ZERO, 2.2f, 7, 1);
            }
            case "spopovich/abraco" -> sound(m, SoundEvents.RAVAGER_AMBIENT, 1.4f, 1.4f);
            case "spopovich/investida" -> sound(m, SoundEvents.RAVAGER_ROAR, 1.6f, 1.4f);
            case "yamu/sugar" -> sound(m, SoundEvents.BEACON_AMBIENT, 1.2f, 0.7f);
            case "yamu/rajada" -> sound(m, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.8f);
            case "babidi/paparapa" -> {
                sound(m, SoundEvents.EVOKER_PREPARE_ATTACK, 1.6f, 1.4f);
                if (t != null) {
                    // the circle under the target now: 1.6 s to get out of it
                    Vec3 at = ground(m, t.position());
                    AIM.put(m.getId(), at);
                    DmNet.send(lvl(m), DmNet.CIRCLE, m.getId(), at, Vec3.ZERO, 2.4f, 38, 0);
                }
            }
            case "babidi/controle" -> sound(m, SoundEvents.EVOKER_PREPARE_SUMMON, 1.6f, 1.2f);
            default -> {
            }
        }
    }

    // ---------- every tick of a blow ----------

    static void tick(DbMob m, String mode, int tick, @Nullable LivingEntity t) {
        ServerLevel level = lvl(m);
        switch (m.id() + "/" + mode) {
            // ---------- Saibaman: clinging on the back, blinking faster and faster ----------
            case "saibaman/agarrao" -> {
                if (!holding(m, t) || tick < 20) {
                    return;
                }
                Vec3 back = t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(0.45)).add(0, 0.15, 0);
                m.setPos(back.x, back.y, back.z);
                m.setDeltaMovement(Vec3.ZERO);
                face(m, t.position().add(t.getLookAngle()));
                int left = 56 - tick;
                int every = left > 20 ? 8 : left > 10 ? 4 : 2;
                if (left > 0 && tick % every == 0) {
                    DbCommon.hit(level, Combat.center(m), 0.5f, Db.GREEN);
                    sound(m, SoundEvents.CREEPER_PRIMED, 0.8f, 1.6f + (1 - left / 36f));
                }
                if (tick == 56) {
                    explode(m, t, 1f);
                }
            }
            // ---------- Bandido: the six claws ----------
            case "bandido_deserto/lobo" -> {
                if (tick >= 16 && tick <= 46 && (tick - 16) % 6 == 0) {
                    boolean leftSide = (tick / 6) % 2 == 0;
                    Vec3 at = body(m, 1.3, leftSide ? -0.3 : 0.3, 1.2);
                    SubNet.send(level, SubNet.SLASH, 0, at, right(m).scale(leftSide ? 1 : -1).add(0, -0.6, 0).normalize(), 0.7f, 0.12f, SubNet.STYLE_CRIMSON);
                    sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f + 0.05f * tick / 6);
                    if (front(m, t, 1.8, 0.2)) {
                        owe(m, t, 16);
                        DbCommon.hit(level, Combat.center(t), 0.35f, 10);
                        Stun.apply(t, 4);
                    }
                    if (tick == 28) {
                        pay(m, false);
                    }
                }
                if (tick == 47) {
                    // the two palms on the chest
                    sound(m, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 0.8f);
                    SubNet.send(level, NmNet.SHOCK, 0, body(m, 1.2, 0, 1.1), Vec3.ZERO, 1.6f, 0, 0);
                    if (front(m, t, 2.0, 0.2)) {
                        owe(m, t, 30);
                        pay(m, false);
                        Combat.push(t, m.forward(), 1.3, 0.35);
                    } else {
                        pay(m, false);
                    }
                }
            }
            // ---------- Red Ribbon: 14 shots, 2 ticks apart, landing in packets of 4 ----------
            case "soldado_red_ribbon/metralhadora" -> {
                if (tick < 10 || tick > 36 || tick % 2 != 0) {
                    return;
                }
                Vec3 from = body(m, 0.9, -0.25, 1.35);
                Vec3 aim = alive(t) ? Combat.center(t) : ahead(m, 10).add(0, 1, 0);
                RandomSource r = m.getRandom();
                Vec3 dir = aim.subtract(from).normalize().add(r.nextGaussian() * 0.035, r.nextGaussian() * 0.025, r.nextGaussian() * 0.035).normalize();
                Vec3 end = clip(m, from, from.add(dir.scale(24)));
                for (LivingEntity o : Combat.alongLine(level, from, end, 0.45, m)) {
                    if (ally(m, o)) {
                        continue;
                    }
                    end = Combat.center(o);
                    owe(m, o, 11);
                    Stun.apply(o, 3);
                    break;
                }
                SubNet.send(level, SubNet.STREAK, 0, from, end.subtract(from), 0.06f, Db.GOLD_W, 2);
                DbCommon.hit(level, end, 0.18f, Db.GOLD);
                sound(m, from, SoundEvents.IRON_GOLEM_STEP, 0.7f, 1.9f);
                int shot = (tick - 10) / 2 + 1;
                if (shot % 4 == 0 || shot == 14) {
                    pay(m, true);
                }
            }
            case "soldado_red_ribbon/bazuca" -> {
                if (tick == 26 && alive(t)) {
                    // the red dot on the chest for half a second: the aim locks now
                    Vec3 from = body(m, 0.7, -0.3, 1.5);
                    AIM.put(m.getId(), Combat.center(t));
                    SubNet.send(level, SubNet.BEAM, 0, from, Combat.center(t).subtract(from), 0.025f, SubNet.COLOR_RED, 0);
                }
            }
            // ---------- Yeti: the ice breaking ----------
            // ---------- Pirate: the bomb leaves the hand at frame 14, lands, and fizzes on the ground until 62 ----------
            case "pirata/bomba" -> {
                if (tick != 14) {
                    return;
                }
                Vec3 hand = body(m, 0.5, 0.3, 1.6);
                Vec3 at = alive(t) ? ground(m, t.position()) : ground(m, ahead(m, 5));
                if (at.distanceTo(m.position()) > 9) {
                    at = ground(m, m.position().add(at.subtract(m.position()).normalize().scale(9)));
                }
                AIM.put(m.getId(), at);
                DmNet.send(level, DmNet.BOMB, m.getId(), hand, at.subtract(hand), 48, 0, 14);
            }
            case "yeti/rugido" -> {
                if (tick == 52) {
                    for (LivingEntity o : cone(m, m.position(), m.forward(), 8, 0.55)) {
                        if (Freeze.isFrozen(o) && HIT_ONCE.getOrDefault(m.getId(), Set.of()).contains(o.getId())) {
                            SubNet.send(level, SubNet.FREEZE, o.getId(), o.position(), Vec3.ZERO, 0, 0, 1);
                            magic(m, o, 60);
                        }
                    }
                    letGo(m);
                }
            }
            // ---------- Raptor and tiger: the bites while pinned ----------
            case "raptor/bote" -> {
                if (holding(m, t) && (tick == 34 || tick == 44 || tick == 52)) {
                    sound(m, t.position(), SoundEvents.FOX_BITE, 1.4f, 0.7f);
                    SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(t), m.forward(), 0.5f, 0, 0);
                    owe(m, t, 22);
                    if (tick != 34) {
                        pay(m, false);
                    }
                }
            }
            case "tigre_sabre/bote" -> {
                if (!holding(m, t)) {
                    return;
                }
                if (t instanceof Player p && p.jumping && tick > 34 && m.getRandom().nextInt(4) == 0) {
                    // pressing jump shakes it off sooner
                    letGo(m);
                    Combat.push(t, m.forward().scale(-1), 0.5, 0.3);
                    return;
                }
                if (tick == 36 || tick == 46 || tick == 56) {
                    sound(m, t.position(), SoundEvents.FOX_BITE, 1.6f, 0.5f);
                    SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(t), m.forward(), 0.6f, 0, 0);
                    owe(m, t, 30);
                    if (tick != 36) {
                        pay(m, false);
                    }
                }
            }
            case "tigre_sabre/patada" -> {
                if (tick == 30) {
                    SubNet.send(level, SubNet.SLASH, 0, body(m, 1.6, 0.2, 1.4), right(m).scale(-1).add(0, -0.7, 0).normalize(), 1.2f, 0.2f, SubNet.STYLE_CRIMSON);
                    sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.7f);
                    if (front(m, t, 2.8, 0.2)) {
                        owe(m, t, 45);
                    }
                    pay(m, false);
                }
                if (tick == 41) {
                    // the roar: everything around backs off
                    sound(m, SoundEvents.RAVAGER_ROAR, 1.8f, 1.3f);
                    SubNet.send(level, NmNet.SHOCK, 0, m.position().add(0, 0.1, 0), Vec3.ZERO, 6, 0, 1);
                    for (LivingEntity o : Combat.inRadius(level, m.position(), 6, m)) {
                        if (!ally(m, o)) {
                            Combat.pushFrom(o, m.position(), 0.7, 0.2);
                        }
                    }
                }
            }
            // ---------- Chifrudo, T-rex: what they run over ----------
            case "dino_chifrudo/chifrada" -> {
                if (tick >= 24 && tick <= 52) {
                    smash(m, ahead(m, 1.6).add(0, 0.4, 0), 1.1, false);
                    for (LivingEntity o : Combat.inRadius(level, ahead(m, 1.4).add(0, 0.8, 0), 1.6, m)) {
                        if (!ally(m, o) && once(m, o)) {
                            hit(m, o, 90, 0.9f, 10);
                            // thrown over it: lands behind
                            Combat.push(o, m.forward().scale(-1), 0.9, 1.0);
                        }
                    }
                }
            }
            case "tiranossauro/investida" -> {
                if (tick >= 30 && tick <= 66) {
                    smash(m, ahead(m, 2.6).add(0, 0.5, 0), 1.8, true);
                    if (tick % 4 == 0) {
                        sound(m, SoundEvents.RAVAGER_STEP, 2f, 0.5f);
                        SubNet.send(level, SubNet.DUST, 0, m.position(), Vec3.ZERO, 1.4f, 0, 0);
                    }
                    for (LivingEntity o : Combat.inRadius(level, ahead(m, 2.4).add(0, 1, 0), 2.2, m)) {
                        if (!ally(m, o) && once(m, o)) {
                            hit(m, o, 150, 1.1f, 10);
                            Combat.push(o, m.forward(), 1.8, 0.6);
                        }
                    }
                }
            }
            // ---------- Pterodactyl: carrying someone up ----------
            case "pterodactilo/mergulho" -> {
                if (holding(m, t) && tick > 26 && tick < 66) {
                    m.setDeltaMovement(m.getDeltaMovement().multiply(0.5, 0, 0.5).add(0, 0.16, 0));
                    m.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, 0.16, 0));
                }
                if (tick == 66 && holding(m, t)) {
                    letGo(m);
                    sound(m, SoundEvents.PHANTOM_AMBIENT, 1.4f, 1.2f);
                }
            }
            // ---------- Dragon: the jet of fire ----------
            case "dragao/fogo" -> {
                if (tick == 17) {
                    Vec3 mouth = dragonMouth(m);
                    Vec3 dir = fireDir(m, t);
                    DmNet.send(level, DmNet.BREATH, m.getId(), mouth, dir.scale(7), 54, 0, 0);
                }
                if (tick >= 28 && tick <= 70 && tick % 7 == 0) {
                    Vec3 mouth = dragonMouth(m);
                    Vec3 dir = fireDir(m, t);
                    for (LivingEntity o : cone(m, mouth, dir, 7.5, 0.82)) {
                        if (o == m.getControllingPassenger()) {
                            continue;
                        }
                        magic(m, o, 30);
                        o.igniteForSeconds(4);
                    }
                    if (BlockRules.canBreak(level) && tick % 14 == 0) {
                        Vec3 spot = ground(m, mouth.add(dir.scale(4 + m.getRandom().nextDouble() * 3)));
                        BlockPos p = BlockPos.containing(spot);
                        if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                            level.setBlock(p, Blocks.FIRE.defaultBlockState(), 3);
                        }
                    }
                    sound(m, mouth, SoundEvents.BLAZE_SHOOT, 1.4f, 0.6f);
                }
            }
            // ---------- Freeza's soldier: the pistol ----------
            case "soldado_freeza/pistola" -> {
                if (tick == 14 || tick == 19 || tick == 24 || tick == 29 || tick == 34 || tick == 43) {
                    boolean big = tick == 43;
                    Vec3 from = body(m, 0.9, 0.35, 1.35);
                    Vec3 aim = alive(t) ? Combat.center(t) : ahead(m, 10).add(0, 1, 0);
                    Vec3 end = clip(m, from, from.add(aim.subtract(from).normalize().scale(18)));
                    LivingEntity got = null;
                    for (LivingEntity o : Combat.alongLine(level, from, end, big ? 0.6 : 0.45, m)) {
                        if (!ally(m, o)) {
                            got = o;
                            end = Combat.center(o);
                            break;
                        }
                    }
                    int flight = Math.max(2, (int) (from.distanceTo(end) / 2.2));
                    SubNet.send(level, SubNet.SHOT, SubNet.newId(), from, end.subtract(from), flight, Db.PINK, 0);
                    sound(m, from, SoundEvents.BEACON_DEACTIVATE, 0.8f, big ? 1.2f : 2f);
                    if (got != null) {
                        owe(m, got, big ? 40 : 12);
                    }
                    if (tick == 24 || tick == 34 || big) {
                        LivingEntity paid = got;
                        DbCommon.later(flight, () -> pay(m, true));
                        if (big && paid != null) {
                            DbCommon.later(flight, () -> DbCommon.boom(level, Combat.center(paid), 1.2f, Db.PINK, 1000 + m.getRandom().nextInt(100)));
                        }
                    }
                }
            }
            case "soldado_freeza/rajada" -> {
                if (tick == 18 || tick == 22 || tick == 26) {
                    Vec3 from = body(m, 0.9, -0.35, 1.4);
                    Vec3 aim = alive(t) ? Combat.center(t) : ahead(m, 10).add(0, 1, 0);
                    Vec3 end = clip(m, from, from.add(aim.subtract(from).normalize().scale(16)));
                    for (LivingEntity o : Combat.alongLine(level, from, end, 0.5, m)) {
                        if (!ally(m, o)) {
                            end = Combat.center(o);
                            owe(m, o, 20);
                            break;
                        }
                    }
                    int flight = Math.max(2, (int) (from.distanceTo(end) / 2));
                    SubNet.send(level, SubNet.SHOT, SubNet.newId(), from, end.subtract(from), flight, Db.PINK, 0);
                    sound(m, from, SoundEvents.BEACON_DEACTIVATE, 0.9f, 1.7f);
                    if (tick == 26) {
                        DbCommon.later(flight, () -> pay(m, true));
                    }
                }
            }
            case "soldado_freeza/bomba" -> {
                if (tick >= 20 && tick < 44) {
                    float grow = (tick - 20) / 24f;
                    SubNet.send(level, SubNet.KI_BALL, stream(m), body(m, 0.2, 0, 2.6), Vec3.ZERO, 0.2f + 0.45f * grow, Db.PURPLE, 0);
                }
            }
            // ---------- Cell Jr.: the Kamehameha ----------
            case "cell_jr/kame" -> {
                if (tick >= 12 && tick < 32) {
                    float grow = (tick - 12) / 20f;
                    SubNet.send(level, SubNet.KI_BALL, stream(m), body(m, -0.1, 0.45, 0.75), Vec3.ZERO, 0.08f + 0.18f * grow, Db.KAME, 0);
                }
                if (tick == 31 && alive(t)) {
                    endStream(m, SubNet.KI_BALL);
                    AIM.put(m.getId(), Combat.center(t));
                }
                if (tick >= 32 && tick <= 50) {
                    Vec3 from = body(m, 0.6, 0, 0.8);
                    Vec3 aim = AIM.getOrDefault(m.getId(), ahead(m, 10).add(0, 0.8, 0));
                    Vec3 dir = aim.subtract(from).normalize();
                    Vec3 end = DbCommon.beamEnd(m, level, from, dir, 14, 0.6, false);
                    SubNet.send(level, SubNet.KI_BEAM, stream(m), from, end.subtract(from), 0.45f, Db.KAME, tick == 50 ? 1 : 0);
                    if (tick == 38 || tick == 44 || tick == 50) {
                        for (LivingEntity o : Combat.alongLine(level, from, end, 0.7, m)) {
                            magic(m, o, 30);
                            Combat.push(o, dir, 0.4, 0.1);
                        }
                    }
                    if (tick == 50) {
                        STREAM.remove(m.getId());
                        DbCommon.boom(level, end, 1.4f, Db.KAME, 1000 + m.getRandom().nextInt(100));
                    }
                }
            }
            // ---------- Yakon: eating the light ----------
            case "yakon/devorar" -> {
                if (tick < 50) {
                    Vec3 mouth = body(m, 0.8, 0, 2.2);
                    if (tick == 2) {
                        DmNet.send(level, DmNet.SUCK, m.getId(), mouth, Vec3.ZERO, 8, 48, 0);
                    }
                    if (tick % 5 == 0) {
                        for (LivingEntity o : Combat.inRadius(level, m.position(), 8, m)) {
                            if (ally(m, o)) {
                                continue;
                            }
                            Vec3 pull = mouth.subtract(o.position()).normalize().scale(0.12);
                            o.setDeltaMovement(o.getDeltaMovement().add(pull.x, 0, pull.z));
                            o.hurtMarked = true;
                            Stamina.drain(o, 0.04);
                        }
                    }
                }
            }
            // ---------- Peixe: the water pulling to the mouth ----------
            case "peixe_gigante/abocanhar" -> {
                if (tick < 18) {
                    Vec3 mouth = body(m, 2.2, 0, 0.6);
                    if (tick % 4 == 0) {
                        SubNet.send(level, NmNet.SPLASH, 0, mouth, Vec3.ZERO, 0.8f, 0, 0);
                    }
                    for (LivingEntity o : Combat.inRadius(level, mouth, 5, m)) {
                        if (ally(m, o)) {
                            continue;
                        }
                        Vec3 pull = mouth.subtract(o.position()).multiply(1, 0, 1).normalize().scale(0.09);
                        o.setDeltaMovement(o.getDeltaMovement().add(pull));
                        o.hurtMarked = true;
                    }
                }
                if (tick == 34 && holding(m, t)) {
                    // spat out far
                    letGo(m);
                    Combat.push(t, m.forward(), 1.5, 0.6);
                    SubNet.send(level, NmNet.SPLASH, 0, Combat.center(t), Vec3.ZERO, 1.2f, 0, 0);
                    sound(m, SoundEvents.LLAMA_SPIT, 1.6f, 0.5f);
                }
            }
            // ---------- Androide: the eyes warm up, the arm cannon ----------
            case "androide_gero/laser" -> {
                if (tick == 24 && alive(t)) {
                    AIM.put(m.getId(), t.position().add(0, 0.9, 0));
                    DbCommon.hit(level, body(m, 0.3, 0, 1.75), 0.3f, Db.RED);
                    sound(m, SoundEvents.BEACON_POWER_SELECT, 1f, 2f);
                }
            }
            case "androide_gero/canhao" -> {
                if (tick >= 22 && tick < 34) {
                    SubNet.send(level, SubNet.KI_BALL, stream(m), body(m, 1.1, 0.35, 1.3), Vec3.ZERO, 0.12f + 0.2f * (tick - 22) / 12f, Db.GOLD, 0);
                }
                if (tick == 33) {
                    endStream(m, SubNet.KI_BALL);
                    if (alive(t)) {
                        AIM.put(m.getId(), Combat.center(t));
                    }
                }
                if (tick >= 34 && tick <= 46) {
                    Vec3 from = body(m, 1.1, 0.35, 1.3);
                    Vec3 aim = AIM.getOrDefault(m.getId(), ahead(m, 10).add(0, 1, 0));
                    Vec3 dir = aim.subtract(from).normalize();
                    Vec3 end = DbCommon.beamEnd(m, level, from, dir, 16, 0.6, false);
                    SubNet.send(level, SubNet.KI_BEAM, stream(m), from, end.subtract(from), 0.55f, Db.GOLD, tick == 46 ? 1 : 0);
                    if (tick == 38 || tick == 42 || tick == 46) {
                        for (LivingEntity o : Combat.alongLine(level, from, end, 0.75, m)) {
                            magic(m, o, 38);
                        }
                    }
                    if (tick == 46) {
                        STREAM.remove(m.getId());
                        DbCommon.boom(level, end, 1.8f, Db.GOLD, 1000 + m.getRandom().nextInt(100));
                        DbCommon.hurtRadius(m, level, end, 2, 20, 0.6, 0.3);
                    }
                }
            }
            // ---------- Tao Pai Pai: riding the pillar ----------
            case "tao_pai_pai/pilar" -> {
                if (tick >= 6 && tick < 58) {
                    // the pillar under him: rises out of the ground, then flies with him on it
                    Vec3 foot = tick < 20 ? ground(m, ahead(m, 1)) : m.position().add(0, -0.4, 0);
                    float len = tick < 20 ? 3.2f * (tick - 6) / 14f : 3.2f;
                    Vec3 axis = tick < 20 ? new Vec3(0, len, 0) : m.forward().scale(len);
                    DmNet.send(level, DmNet.PILLAR, stream(m), foot, axis, len, 0, 0);
                }
                if (tick == 58) {
                    endStream(m, DmNet.PILLAR);
                }
            }
            // ---------- Spopovich: the crush ----------
            case "spopovich/abraco" -> {
                if (holding(m, t) && (tick == 30 || tick == 45 || tick == 60)) {
                    sound(m, t.position(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.6f);
                    hit(m, t, 45, 0.7f, 10);
                    DbCommon.screen(level, t.position(), 4);
                }
                if (tick == 66 && holding(m, t)) {
                    letGo(m);
                    Combat.push(t, m.forward(), 0.6, -0.4);
                    hit(m, t, 20, 0.8f, 10);
                    SubNet.send(level, SubNet.CRACKS, 0, ground(m, t.position()), Vec3.ZERO, 1.6f, 6, 1);
                }
            }
            case "spopovich/investida" -> {
                if (tick >= 26 && tick <= 50) {
                    if (tick % 3 == 0) {
                        SubNet.send(level, SubNet.DASH_TRAIL, m.getId(), Combat.center(m), Vec3.ZERO, 1, 0, 0);
                    }
                    for (LivingEntity o : Combat.inRadius(level, ahead(m, 1.1).add(0, 1, 0), 1.6, m)) {
                        if (!ally(m, o) && once(m, o)) {
                            hit(m, o, 120, 0.9f, 10);
                            Combat.push(o, m.forward(), 1.5, 0.5);
                        }
                    }
                }
            }
            // ---------- Yamu: the needle and the flask ----------
            case "yamu/sugar" -> {
                if (!holding(m, t) || tick < 12) {
                    return;
                }
                Vec3 flask = body(m, 0.5, 0.4, 1.1);
                DmNet.send(level, DmNet.DRAIN, m.getId(), Combat.center(t), flask.subtract(Combat.center(t)), (tick - 12) / 50f, 0, tick >= 62 ? 1 : 0);
                if (tick % 5 == 0) {
                    Stamina.drain(t, 0.05);
                    m.heal(1.5f);
                }
                if (tick == 32 || tick == 56) {
                    magic(m, t, 30);
                }
                if (tick == 62) {
                    letGo(m);
                }
            }
            case "yamu/rajada" -> {
                if (tick >= 18 && tick < 28) {
                    SubNet.send(level, SubNet.KI_BALL, stream(m), body(m, 0.9, -0.35, 1.3), Vec3.ZERO, 0.12f + 0.16f * (tick - 18) / 10f, Db.RED, 0);
                }
            }
            // ---------- Babidi ----------
            case "babidi/paparapa" -> {
                if (tick == 53) {
                    Vec3 at = AIM.get(m.getId());
                    if (at != null) {
                        pay(m, true);
                    }
                }
            }
            default -> {
            }
        }
    }

    // ---------- the hit ----------

    static void impact(DbMob m, String mode, @Nullable LivingEntity t) {
        ServerLevel level = lvl(m);
        switch (m.id() + "/" + mode) {
            // ---------- Saibaman ----------
            case "saibaman/agarrao" -> {
                // only clings if it landed on its back (the teleport frame put it there)
            }
            case "saibaman/acido" -> {
                Vec3 mouth = body(m, 0.5, 0, 1.2);
                Vec3 at = alive(t) ? ground(m, t.position()) : ground(m, ahead(m, 4));
                int flight = 7;
                DmNet.send(level, DmNet.ACID, m.getId(), mouth, at.subtract(mouth), 1.7f, 46, flight);
                sound(m, SoundEvents.LLAMA_SPIT, 1.4f, 0.6f);
                ZONES.add(new LevelZone(level) {
                    boolean tick(ServerLevel l) {
                        if (age > flight + 46) {
                            return true;
                        }
                        if (age > flight && (age - flight) % 10 == 0) {
                            for (LivingEntity o : Combat.inRadius(l, at, 1.8, m)) {
                                if (o.getY() - at.y < 1.2 && !ally(m, o)) {
                                    Combat.hurtMagic(m.isAlive() ? m : o, o, 10);
                                    o.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0), m);
                                }
                            }
                            SubCommon.sound(l, at, SoundEvents.LAVA_POP, 0.6f, 1.6f);
                        }
                        return false;
                    }
                });
            }
            // ---------- Bandido ----------
            case "bandido_deserto/lobo" -> {
            }
            case "bandido_deserto/areia" -> {
                Vec3 hand = body(m, 0.8, 0.3, 1.2);
                DmNet.send(level, DmNet.SAND, m.getId(), hand, m.forward().scale(4.5), 0.55f, 0, 0);
                sound(m, SoundEvents.SAND_BREAK, 1.6f, 1.2f);
                for (LivingEntity o : cone(m, m.position(), m.forward(), 4.8, 0.6)) {
                    magic(m, o, 10);
                    o.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false), m);
                    dizzy(m, o, 60);
                }
            }
            // ---------- Red Ribbon ----------
            case "soldado_red_ribbon/bazuca" -> {
                Vec3 from = body(m, 0.7, -0.3, 1.5);
                Vec3 aim = AIM.getOrDefault(m.getId(), alive(t) ? Combat.center(t) : ahead(m, 12).add(0, 1, 0));
                Vec3 dir = aim.subtract(from).normalize();
                Vec3 end = clip(m, from, from.add(dir.scale(28)));
                for (LivingEntity o : Combat.alongLine(level, from, end, 0.6, m)) {
                    if (!ally(m, o)) {
                        end = Combat.center(o);
                        break;
                    }
                }
                int flight = Math.max(3, (int) (from.distanceTo(end) / 1.4));
                DmNet.send(level, DmNet.ROCKET, SubNet.newId(), from, end.subtract(from), flight, 0, 0);
                sound(m, from, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.8f, 0.6f);
                Vec3 boom = end;
                DbCommon.later(flight, () -> {
                    DbCommon.boom(level, boom, 3f, Db.RED_W, 1000 + level.getRandom().nextInt(100));
                    DbCommon.screen(level, boom, 8);
                    for (LivingEntity o : Combat.inRadius(level, boom, 3.4, m)) {
                        if (!ally(m, o)) {
                            double k = 1 - 0.5 * Math.min(1, Combat.center(o).distanceTo(boom) / 3.4);
                            Combat.hurtMagic(m.isAlive() ? m : o, o, (float) (120 * k));
                            Combat.pushFrom(o, boom, 1.2 * k, 0.5);
                        }
                    }
                    dent(m, boom, 2.2);
                });
            }
            // ---------- Pirata ----------
            case "pirata/sabre" -> {
                SubNet.send(level, SubNet.ARC, 0, body(m, 1.1, 0, 1.2), m.forward(), 2.2f, Db.WHITE, 0);
                sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.9f);
                for (LivingEntity o : cone(m, m.position(), m.forward(), 3.4, -0.15)) {
                    hit(m, o, 70, 0.7f, 10);
                    Combat.push(o, o.position().subtract(m.position()), 0.5, 0.2);
                }
            }
            case "pirata/bomba" -> {
                // the bomb thrown at frame 14 went off now
                Vec3 at = AIM.getOrDefault(m.getId(), ahead(m, 5));
                SubNet.send(level, SubNet.FIRE_BOOM, 0, at.add(0, 0.4, 0), new Vec3(at.y, 0, 0), 2.4f, 0, m.getRandom().nextInt(50));
                sound(m, at, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 1.1f);
                wave(m, at.add(0, 0.5, 0), 3, 95, 1.0, 0.45, false);
                dent(m, at, 1.6);
            }
            // ---------- Yeti ----------
            case "yeti/rugido" -> {
                sound(m, SoundEvents.RAVAGER_ROAR, 1.8f, 1.3f);
                Vec3 dir = m.forward();
                for (int i = 1; i <= 4; i++) {
                    SubNet.send(level, SubNet.FROST, 0, ground(m, m.position().add(dir.scale(i * 1.8))), Vec3.ZERO, 1.2f + i * 0.45f, 60, 0);
                }
                DbCommon.screen(level, m.position(), 4);
                for (LivingEntity o : cone(m, m.position(), dir, 7.5, 0.55)) {
                    magic(m, o, 30);
                    o.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true), m);
                    if (once(m, o) && !Freeze.isFrozen(o) && m.heldId < 0) {
                        hold(m, o, 30);
                        SubNet.send(level, SubNet.FREEZE, o.getId(), o.position(), new Vec3(8, 0, 0), 30, 0, 0);
                    }
                }
            }
            case "yeti/pisao" -> {
                Vec3 at = ground(m, ahead(m, 1.4));
                sound(m, SoundEvents.GLASS_BREAK, 2f, 0.6f);
                SubNet.send(level, SubNet.CRACKS, 0, at, Vec3.ZERO, 3f, 9, 1);
                SubNet.send(level, SubNet.FROST, 0, at, Vec3.ZERO, 3.6f, 80, 0);
                for (int ring = 0; ring < 3; ring++) {
                    int n = 6 + ring * 4;
                    double r = 1.2 + ring * 1.1;
                    for (int i = 0; i < n; i++) {
                        double a = i * Math.PI * 2 / n + ring * 0.4;
                        Vec3 p = ground(m, at.add(Math.cos(a) * r, 0, Math.sin(a) * r));
                        Vec3 lean = new Vec3(Math.cos(a) * 0.4, 1, Math.sin(a) * 0.4).normalize();
                        int delay = ring * 3;
                        DbCommon.later(delay, () -> SubNet.send(level, SubNet.ICE_SPIKE, 0, p, lean, 1.0f + ring * 0.25f + level.getRandom().nextFloat() * 0.5f, 50, 0));
                    }
                }
                wave(m, at, 4, 90, 0.4, 0.85, true);
            }
            // ---------- Raptor ----------
            case "raptor/bote" -> {
                sound(m, SoundEvents.FOX_BITE, 1.4f, 0.6f);
                if (front(m, t, 2.4, 0.3)) {
                    hit(m, t, 25, 0.6f, 10);
                    hold(m, t, 30);
                    Stun.apply(t, 28);
                }
            }
            case "raptor/garras" -> {
                Vec3 at = body(m, 1.2, 0, 1);
                SubNet.send(level, SubNet.SLASH, 0, at, right(m).add(0, 0.8, 0).normalize(), 0.8f, 0.14f, SubNet.STYLE_CRIMSON);
                SubNet.send(level, SubNet.SLASH, 0, at, right(m).add(0, -0.8, 0).normalize(), 0.8f, 0.14f, SubNet.STYLE_CRIMSON);
                sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.4f);
                if (front(m, t, 2.4, 0.2)) {
                    hit(m, t, 60, 0.6f, 10);
                    SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(t), m.forward(), 0.6f, 0, 0);
                    bleed(m, t, 80);
                }
            }
            // ---------- Chifrudo ----------
            case "dino_chifrudo/chifrada" -> SubNet.send(level, SubNet.DUST, 0, m.position(), Vec3.ZERO, 1.6f, 0, 0);
            case "dino_chifrudo/clava" -> {
                // the tail sweeps round on the side the target stands
                Vec3 tail = body(m, -2.2, 0, 0.9);
                sound(m, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 0.6f);
                SubNet.send(level, SubNet.ARC, 0, tail, m.forward().scale(-1), 2.6f, Db.WHITE, 0);
                for (LivingEntity o : Combat.inRadius(level, tail, 3.6, m)) {
                    if (!ally(m, o)) {
                        hit(m, o, 80, 0.9f, 10);
                        Combat.pushFrom(o, m.position(), 1.3, 0.35);
                    }
                }
                smash(m, tail, 2.2, false);
            }
            // ---------- T-rex ----------
            case "tiranossauro/investida" -> {
            }
            case "tiranossauro/pisao" -> {
                Vec3 at = ground(m, ahead(m, 1.8));
                sound(m, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.5f);
                SubNet.send(level, SubNet.CRACKS, 0, at, Vec3.ZERO, 4.5f, 12, 1);
                SubNet.send(level, NmNet.SHOCK, 0, at.add(0, 0.1, 0), Vec3.ZERO, 6, 0, 0);
                DbCommon.screen(level, at, 4);
                wave(m, at, 6, 110, 0.6, 0.7, true);
            }
            // ---------- Pterodactyl ----------
            case "pterodactilo/mergulho" -> {
                if (front(m, t, 2.6, -0.2) && !(t instanceof SoulBoss b && b.isBoss())) {
                    hit(m, t, 40, 0.6f, 10);
                    hold(m, t, 42, age -> m.position().add(0, -1.6, 0));
                }
            }
            case "pterodactilo/ventania" -> {
                Vec3 at = body(m, 1.2, 0, 0.3);
                SubNet.send(level, NmNet.WIND, 0, at, m.forward().scale(8), 0.7f, 0, 0);
                DmNet.send(level, DmNet.SAND, m.getId() + 1, ground(m, ahead(m, 2)), m.forward().scale(7), 0.8f, 1, 0);
                for (LivingEntity o : cone(m, m.position(), m.forward(), 8.5, 0.4)) {
                    boolean flying = !o.onGround();
                    Combat.push(o, m.forward(), flying ? 2.2 : 1.1, flying ? 0.5 : 0.2);
                    if (o instanceof Player) {
                        NmNet.screen(o, 2, 40);
                    }
                }
            }
            // ---------- Freeza's soldier: the purple bomb ----------
            case "soldado_freeza/bomba" -> {
                endStream(m, SubNet.KI_BALL);
                Vec3 from = body(m, 0.4, 0, 2.6);
                Vec3 at = alive(t) ? t.position().add(0, 0.5, 0) : ahead(m, 8);
                int flight = Math.max(3, (int) (from.distanceTo(at) / 1.8));
                SubNet.send(level, SubNet.SHOT, SubNet.newId(), from, at.subtract(from), flight, Db.PURPLE, 0);
                DbCommon.later(flight, () -> {
                    DbCommon.boom(level, at, 2.6f, Db.PURPLE, 1000 + level.getRandom().nextInt(100));
                    for (LivingEntity o : Combat.inRadius(level, at, 3.2, m)) {
                        if (!ally(m, o)) {
                            double k = 1 - 0.5 * Math.min(1, Combat.center(o).distanceTo(at) / 3.2);
                            Combat.hurtMagic(m.isAlive() ? m : o, o, (float) (110 * k));
                            Combat.pushFrom(o, at, 0.9 * k, 0.4);
                        }
                    }
                    dent(m, at, 2);
                });
            }
            // ---------- Cell Jr.: the spin kick from behind ----------
            case "cell_jr/sumir" -> {
                if (!front(m, t, 2.6, -0.2)) {
                    return;
                }
                // looking back at the right time blocks it
                Vec3 look = t.getLookAngle().multiply(1, 0, 1).normalize();
                Vec3 toCell = SubCommon.flat(m.position().subtract(t.position())).normalize();
                if (look.dot(toCell) > 0.5) {
                    sound(m, SoundEvents.SHIELD_BLOCK, 1.4f, 1.2f);
                    DbCommon.hit(level, Combat.center(t), 0.5f, Db.WHITE);
                    Stun.apply(m, 25);
                    return;
                }
                hit(m, t, 80, 0.8f, 10);
                Combat.push(t, m.forward(), 1.2, 0.35);
                sound(m, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4f, 1.4f);
            }
            // ---------- Yakon ----------
            case "yakon/escuro" -> {
                // the pounce's two rips (the darkness went up at the start)
                SubNet.send(level, SubNet.SLASH, 0, body(m, 1.4, 0, 1.6), right(m).add(0, -0.8, 0).normalize(), 1.4f, 0.22f, SubNet.STYLE_CRIMSON);
                SubNet.send(level, SubNet.SLASH, 0, body(m, 1.4, 0, 1.4), right(m).scale(-1).add(0, -0.8, 0).normalize(), 1.4f, 0.22f, SubNet.STYLE_CRIMSON);
                sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.5f);
                if (front(m, t, 2.8, 0.1)) {
                    hit(m, t, 150, 1f, 10);
                    Combat.push(t, m.forward(), 0.7, 0.3);
                }
            }
            case "yakon/devorar" -> {
                // swallowed the light: swells, glows and heals (or bursts, fed too much strong ki)
                float took = SWALLOWED.getOrDefault(m.getId(), 0f);
                SWALLOWED.remove(m.getId());
                if (took >= 60) {
                    sound(m, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 1.4f);
                    DbCommon.boom(level, body(m, 0.5, 0, 2), 2.4f, Db.GOLD, 1000 + m.getRandom().nextInt(100));
                    m.hurt(m.damageSources().magic(), m.getMaxHealth() * 0.35f);
                    Stun.apply(m, 50, true);
                    return;
                }
                m.heal(40);
                DbCommon.hit(level, body(m, 0.5, 0, 2), 1.2f, Db.GOLD);
                sound(m, SoundEvents.WARDEN_HEARTBEAT, 2f, 1.4f);
            }
            // ---------- Tiger ----------
            case "tigre_sabre/bote" -> {
                SubNet.send(level, SubNet.SLASH, 0, body(m, 1.6, 0, 1), right(m).add(0, -0.7, 0).normalize(), 1.2f, 0.2f, SubNet.STYLE_CRIMSON);
                sound(m, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.8f);
                if (front(m, t, 2.4, 0.3)) {
                    Defense.breakGuard(t, m);
                    hit(m, t, 30, 0.8f, 10);
                    hold(m, t, 32);
                    Stun.apply(t, 30);
                }
            }
            // ---------- Peixe ----------
            case "peixe_gigante/salto" -> {
                Vec3 at = ground(m, m.position());
                SubNet.send(level, NmNet.SPLASH, 0, at.add(0, 0.5, 0), Vec3.ZERO, 2.6f, 0, 0);
                SubNet.send(level, NmNet.SHOCK, 0, at.add(0, 0.1, 0), Vec3.ZERO, 3.4f, 0, 0);
                sound(m, SoundEvents.GENERIC_SPLASH, 2f, 0.5f);
                wave(m, at, 3.4, 90, 1.0, 0.5, false);
            }
            case "peixe_gigante/abocanhar" -> {
                sound(m, SoundEvents.FOX_BITE, 1.8f, 0.4f);
                if (front(m, t, 2.4, 0.2)) {
                    hit(m, t, 70, 0.9f, 10);
                    hold(m, t, 16, age -> m.position().add(m.forward().scale(2.2)));
                }
            }
            // ---------- Androide ----------
            case "androide_gero/laser" -> {
                Vec3 aim = AIM.getOrDefault(m.getId(), ahead(m, 10).add(0, 0.9, 0));
                for (int eye = -1; eye <= 1; eye += 2) {
                    Vec3 from = body(m, 0.3, 0.12 * eye, 1.72);
                    Vec3 dir = aim.subtract(from).normalize();
                    Vec3 end = DbCommon.beamEnd(m, level, from, dir, 16, 0.4, true);
                    SubNet.send(level, SubNet.BEAM, 0, from, end.subtract(from), 0.06f, SubNet.COLOR_RED, 0);
                    SubNet.send(level, SubNet.SCAR, 0, ground(m, end), dir, 0.3f, 80, 0);
                    for (LivingEntity o : Combat.alongLine(level, from, end, 0.55, m)) {
                        if (once(m, o)) {
                            magic(m, o, 90);
                            o.igniteForSeconds(2);
                        }
                    }
                }
                sound(m, SoundEvents.BEACON_DEACTIVATE, 1.4f, 2f);
            }
            // ---------- Tao Pai Pai ----------
            case "tao_pai_pai/dodonpa" -> {
                Vec3 from = body(m, 0.8, -0.3, 1.55);
                Vec3 aim = alive(t) ? Combat.center(t) : ahead(m, 14).add(0, 1, 0);
                Vec3 dir = aim.subtract(from).normalize();
                Vec3 end = DbCommon.beamEnd(m, level, from, dir, 20, 0.35, false);
                SubNet.send(level, SubNet.BEAM, 0, from, end.subtract(from), 0.05f, SubNet.COLOR_GOLD, 0);
                DbCommon.hit(level, end, 0.5f, Db.GOLD);
                sound(m, from, SoundEvents.TRIDENT_THUNDER.value(), 0.8f, 2f);
                for (LivingEntity o : Combat.alongLine(level, from, end, 0.4, m)) {
                    // pierces: magic damage goes through armor
                    magic(m, o, 110);
                    break;
                }
            }
            case "tao_pai_pai/pilar" -> {
                endStream(m, DmNet.PILLAR);
                sound(m, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 1.1f);
                if (front(m, t, 2.6, 0.1)) {
                    hit(m, t, 130, 1f, 10);
                    Combat.push(t, m.forward(), 1.3, 0.4);
                }
                SubNet.send(level, SubNet.DUST, 0, ahead(m, 2), Vec3.ZERO, 1.2f, 0, 0);
            }
            // ---------- Spopovich ----------
            case "spopovich/abraco" -> {
                if (front(m, t, 2.0, 0.2)) {
                    hold(m, t, 52, age -> m.position().add(m.forward().scale(0.9)).add(0, 0.3, 0));
                    sound(m, t.position(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1.2f, 0.6f);
                }
            }
            case "spopovich/investida" -> {
            }
            // ---------- Yamu ----------
            case "yamu/sugar" -> {
                if (front(m, t, 1.8, 0.2)) {
                    hold(m, t, 52);
                    hit(m, t, 10, 0.4f, Db.RED);
                }
            }
            case "yamu/rajada" -> {
                endStream(m, SubNet.KI_BALL);
                Vec3 from = body(m, 0.9, -0.35, 1.3);
                Vec3 aim = alive(t) ? Combat.center(t) : ahead(m, 10).add(0, 1, 0);
                Vec3 end = clip(m, from, from.add(aim.subtract(from).normalize().scale(14)));
                for (LivingEntity o : Combat.alongLine(level, from, end, 0.55, m)) {
                    if (!ally(m, o)) {
                        end = Combat.center(o);
                        break;
                    }
                }
                int flight = Math.max(3, (int) (from.distanceTo(end) / 1.8));
                SubNet.send(level, SubNet.SHOT, SubNet.newId(), from, end.subtract(from), flight, Db.RED, 0);
                Vec3 boom = end;
                DbCommon.later(flight, () -> {
                    DbCommon.boom(level, boom, 1.6f, Db.RED, 1000 + level.getRandom().nextInt(100));
                    for (LivingEntity o : Combat.inRadius(level, boom, 1.8, m)) {
                        if (!ally(m, o)) {
                            Combat.hurtMagic(m.isAlive() ? m : o, o, 100);
                        }
                    }
                });
            }
            // ---------- Babidi ----------
            case "babidi/paparapa" -> {
                Vec3 at = AIM.get(m.getId());
                if (at == null) {
                    return;
                }
                DmNet.send(level, DmNet.CIRCLE, m.getId(), at, Vec3.ZERO, 2.4f, 0, 1);
                DbCommon.boom(level, at.add(0, 1, 0), 1.8f, Db.PINK, 1000 + m.getRandom().nextInt(100));
                for (LivingEntity o : Combat.inRadius(level, at.add(0, 0.8, 0), 2.6, m)) {
                    if (!ally(m, o) && Math.abs(o.getY() - at.y) < 2.5) {
                        // the inside bursts: two packets
                        magic(m, o, 70);
                        owe(m, o, 70);
                    }
                }
            }
            case "babidi/controle" -> control(m, t);
            default -> {
            }
        }
    }

    // ---------- teleports (the hidden frame) ----------

    static void teleport(DbMob m, String mode, @Nullable LivingEntity t) {
        ServerLevel level = lvl(m);
        switch (m.id() + "/" + mode) {
            case "saibaman/agarrao" -> {
                // the leap ends on the target's back: clings
                if (front(m, t, 4.5, -1) && !(t instanceof SoulBoss b && b.isBoss()) && hold(m, t, 40)) {
                    m.setNoGravity(true);
                    m.noPhysics = true;
                    sound(m, SoundEvents.SLIME_ATTACK, 1.4f, 1.4f);
                }
            }
            case "cell_jr/sumir" -> {
                SubNet.send(level, SubNet.VANISH, 0, m.position().add(0, 0.7, 0), Vec3.ZERO, 0, 0, 0);
                if (!alive(t)) {
                    return;
                }
                Vec3 look = t.getLookAngle().multiply(1, 0, 1);
                look = look.lengthSqr() < 1.0E-3 ? t.position().subtract(m.position()).multiply(1, 0, 1).normalize() : look.normalize();
                Vec3 behind = ground(m, t.position().subtract(look.scale(1.4)));
                m.teleportTo(behind.x, behind.y, behind.z);
                face(m, t.position());
                SubNet.send(level, SubNet.VANISH, 0, behind.add(0, 0.7, 0), Vec3.ZERO, 0, 0, 0);
                sound(m, behind, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.8f);
            }
            default -> {
            }
        }
    }

    // ---------- the end of a blow ----------

    static void end(DbMob m, String mode, @Nullable LivingEntity t) {
        HIT_ONCE.remove(m.getId());
        pay(m, true);
        AIM.remove(m.getId());
        switch (m.id() + "/" + mode) {
            case "saibaman/agarrao" -> {
                m.setNoGravity(false);
                m.noPhysics = false;
                letGo(m);
            }
            case "soldado_freeza/bomba", "cell_jr/kame", "androide_gero/canhao", "yamu/rajada" -> {
                endStream(m, SubNet.KI_BALL);
                endStream(m, SubNet.KI_BEAM);
                letGo(m);
            }
            case "tao_pai_pai/pilar" -> {
                endStream(m, DmNet.PILLAR);
                letGo(m);
            }
            case "yamu/sugar" -> {
                DmNet.send(lvl(m), DmNet.DRAIN, m.getId(), m.position(), Vec3.ZERO, 0, 0, 1);
                letGo(m);
            }
            case "spopovich/investida" -> {
                // missed everyone: trips and stays dazed
                if (!HIT_ONCE.containsKey(m.getId()) && m.getRandom().nextInt(2) == 0) {
                    Stun.apply(m, 40, true);
                    sound(m, SoundEvents.RAVAGER_STUNNED, 1.4f, 1.2f);
                }
            }
            default -> letGo(m);
        }
    }

    // The Saibaman blowing up on its prey's back (weaker when it was hit loose early). It dies in it.
    private static void explode(DbMob m, @Nullable LivingEntity t, float k) {
        ServerLevel level = lvl(m);
        Vec3 at = Combat.center(m);
        letGo(m);
        DbCommon.boom(level, at, 2.4f * k, Db.GREEN, 1000 + m.getRandom().nextInt(100));
        DbCommon.screen(level, at, 8);
        for (LivingEntity o : Combat.inRadius(level, at, 2.8, m)) {
            if (!ally(m, o)) {
                Combat.hurtMagic(m, o, (o == t ? 110 : 50) * k);
                Combat.pushFrom(o, at, 0.8, 0.4);
            }
        }
        m.setNoGravity(false);
        m.noPhysics = false;
        m.hurt(m.damageSources().generic(), m.getMaxHealth() * 4);
    }

    // ---------- Babidi's control ----------

    private static void control(DbMob m, @Nullable LivingEntity t) {
        ServerLevel level = lvl(m);
        if (!alive(t) || !m.hasLineOfSight(t)) {
            SubCommon.sound(level, m.position(), SoundEvents.EVOKER_CAST_SPELL, 1f, 0.6f);
            return;
        }
        DmNet.send(level, DmNet.DRAIN, m.getId(), body(m, 0.5, 0, 1.2), t.getEyePosition().subtract(body(m, 0.5, 0, 1.2)), 1, 1, 0);
        SubCommon.sound(level, t.position(), SoundEvents.EVOKER_CAST_SPELL, 1.6f, 0.8f);
        if (t instanceof Player p) {
            // 4 s with the M lit: dizzy, weak, the world turning
            DmNet.send(level, DmNet.MAJIN, p.getId(), p.position(), Vec3.ZERO, 80, 0, 0);
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false), m);
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1, false, false), m);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, false), m);
            dizzy(m, p, 80);
            return;
        }
        if (t instanceof SoulBoss b && b.isBoss()) {
            return;
        }
        if (t instanceof DbMob d) {
            d.setMaster(m.getId(), 1200);
            d.setTarget(null);
        }
        if (t instanceof Mob mob) {
            // turns on whoever Babidi fights
            DmNet.send(level, DmNet.MAJIN, mob.getId(), mob.position(), Vec3.ZERO, 1200, 0, 0);
            LivingEntity enemy = m.getLastHurtByMob() != null ? m.getLastHurtByMob() : null;
            if (enemy == null) {
                Player near = level.getNearestPlayer(m, 16);
                enemy = near != null && !near.isCreative() ? near : null;
            }
            mob.setTarget(enemy);
        }
    }

    static void unmark(DbMob m) {
        m.setMaster(-1, 0);
        DmNet.send(lvl(m), DmNet.MAJIN, m.getId(), m.position(), Vec3.ZERO, 0, 0, 0);
    }

    // ---------- the ridden dragon's fire ----------

    static void rideFire(DbMob m, Player rider, @Nullable LivingEntity aim) {
        m.blow(0, aim);
        m.setYRot(rider.getYRot());
        m.yBodyRot = rider.getYRot();
        m.yHeadRot = rider.getYRot();
        // where the rider looks (the blow's target stays whatever is aimed at, if anything)
        AIM.put(m.getId(), rider.getEyePosition().add(rider.getLookAngle().scale(8)));
    }

    private static Vec3 dragonMouth(DbMob m) {
        return body(m, 3.6, 0, 3.2);
    }

    private static Vec3 fireDir(DbMob m, @Nullable LivingEntity t) {
        Vec3 mouth = dragonMouth(m);
        Vec3 aim = AIM.get(m.getId());
        if (aim == null && alive(t)) {
            aim = Combat.center(t);
        }
        if (aim == null) {
            aim = mouth.add(m.forward().scale(6)).add(0, -2.4, 0);
        }
        return aim.subtract(mouth).normalize();
    }

    // ---------- answers to being hit ----------

    static boolean answer(DbMob m, LivingEntity attacker, DamageSource source, float amount) {
        return false;
    }

    static void hurtBy(DbMob m, LivingEntity attacker, float amount) {
        String mode = m.attacking() ? attackMode(m) : null;
        if (mode == null) {
            return;
        }
        switch (m.id() + "/" + mode) {
            case "saibaman/agarrao" -> {
                // a strong blow while it clings sets it off early, weaker
                if (m.heldId >= 0 && amount >= 4 && m.blowTick() < 56) {
                    explode(m, m.blowTarget, 0.55f);
                }
            }
            case "raptor/bote", "tigre_sabre/bote", "pterodactilo/mergulho", "yamu/sugar" -> {
                // hitting it while it holds you makes it let go
                if (m.heldId == attacker.getId() || m.heldId >= 0 && amount >= 3) {
                    letGo(m);
                    m.cutBlow();
                }
            }
            case "spopovich/abraco" -> {
                if (m.heldId == attacker.getId()) {
                    int n = STRUGGLE.merge(m.getId(), 1, Integer::sum);
                    if (n >= 3) {
                        letGo(m);
                        m.cutBlow();
                        Stun.apply(m, 20, true);
                    }
                }
            }
            case "tao_pai_pai/pilar" -> {
                // the pillar broken under him: he falls
                if (m.blowTick() >= 20 && m.blowTick() < 58) {
                    endStream(m, DmNet.PILLAR);
                    SubNet.send(lvl(m), SubNet.DUST, 0, m.position(), Vec3.ZERO, 1.4f, 0, 0);
                    m.cutBlow();
                    Stun.apply(m, 30, true);
                }
            }
            case "yakon/devorar" -> {
                if (m.blowTick() < 50) {
                    SWALLOWED.merge(m.getId(), amount, Float::sum);
                }
            }
            default -> {
            }
        }
    }

    @Nullable
    private static String attackMode(DbMob m) {
        var a = m.spec().attack(m.attackKind);
        return a == null ? null : a.mode();
    }

    static void died(DbMob m) {
        letGo(m);
        Integer id = STREAM.remove(m.getId());
        if (id != null) {
            SubNet.send(lvl(m), SubNet.KI_BEAM, id, m.position(), Vec3.ZERO, 0, 0, 1);
            SubNet.send(lvl(m), SubNet.KI_BALL, id, m.position(), Vec3.ZERO, 0, 0, 1);
            SubNet.send(lvl(m), DmNet.PILLAR, id, m.position(), Vec3.ZERO, 0, 0, 1);
        }
        if (m.master() >= 0) {
            DmNet.send(lvl(m), DmNet.MAJIN, m.getId(), m.position(), Vec3.ZERO, 0, 0, 0);
        }
        if ("babidi".equals(m.id())) {
            // his spell breaks: everyone he took is free
            for (DbMob d : m.level().getEntitiesOfClass(DbMob.class, m.getBoundingBox().inflate(48), d -> d.master() == m.getId())) {
                unmark(d);
            }
        }
        HIT_ONCE.remove(m.getId());
        OWED.remove(m.getId());
        AIM.remove(m.getId());
        STRUGGLE.remove(m.getId());
    }

    // ---------- out of a blow ----------

    static void idle(DbMob m) {
        if (m.sleep > 0) {
            m.sleep--;
        }
        ServerLevel level = lvl(m);
        LivingEntity t = m.getTarget();
        switch (m.id()) {
            case "androide_gero" -> {
                // nearly broken and someone close: blinks red and blows itself up in 3 s
                if (m.selfDestruct < 0 && m.getHealth() < m.getMaxHealth() * 0.2f && alive(t) && m.distanceTo(t) < 6) {
                    m.selfDestruct = 60;
                    sound(m, SoundEvents.CREEPER_PRIMED, 1.4f, 0.6f);
                }
                if (m.selfDestruct > 0) {
                    m.selfDestruct--;
                    m.getNavigation().stop();
                    if (m.selfDestruct % (m.selfDestruct > 30 ? 10 : 4) == 0) {
                        DbCommon.hit(level, Combat.center(m), 0.7f, Db.RED);
                        sound(m, SoundEvents.NOTE_BLOCK_BIT.value(), 1.2f, 1.8f);
                    }
                    if (m.selfDestruct == 0) {
                        Vec3 at = Combat.center(m);
                        DbCommon.boom(level, at, 4.5f, Db.ORANGE, 1000 + m.getRandom().nextInt(100));
                        DbCommon.screen(level, at, 8);
                        for (LivingEntity o : Combat.inRadius(level, at, 5, m)) {
                            if (!ally(m, o)) {
                                double k = 1 - 0.5 * Math.min(1, Combat.center(o).distanceTo(at) / 5);
                                Combat.hurtMagic(m, o, (float) (200 * k));
                                Combat.pushFrom(o, at, 1.4 * k, 0.6);
                            }
                        }
                        dent(m, at, 3);
                        m.hurt(m.damageSources().generic(), m.getMaxHealth() * 4);
                    }
                }
            }
            case "babidi" -> {
                // someone too close: teleports away (behind the ones he controls)
                if (alive(t) && m.distanceTo(t) < 3.5 && !m.attacking()) {
                    long now = level.getGameTime();
                    if (now - BLINKED.getOrDefault(m.getId(), -1000L) > 100) {
                        BLINKED.put(m.getId(), now);
                        Vec3 away = SubCommon.flat(m.position().subtract(t.position()));
                        away = away.lengthSqr() < 1.0E-3 ? m.forward().scale(-1) : away.normalize();
                        Vec3 to = ground(m, m.position().add(away.scale(8)));
                        SubNet.send(level, SubNet.VANISH, 0, m.position().add(0, 0.8, 0), Vec3.ZERO, 0, 0, 0);
                        m.teleportTo(to.x, to.y, to.z);
                        SubNet.send(level, SubNet.VANISH, 0, to.add(0, 0.8, 0), Vec3.ZERO, 0, 0, 0);
                        sound(m, to, SoundEvents.ENDERMAN_TELEPORT, 1f, 1.6f);
                    }
                }
            }
            case "yakon" -> {
                // the darkness: put up when it starts hunting
                if (m.attacking() && "escuro".equals(attackMode(m)) && m.blowTick() == 8) {
                    DmNet.send(level, DmNet.DARK, m.getId(), m.position(), Vec3.ZERO, 12, 80, 0);
                    for (Player p : level.getEntitiesOfClass(Player.class, m.getBoundingBox().inflate(12))) {
                        if (!p.isCreative()) {
                            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0, false, false), m);
                        }
                    }
                }
            }
            case "yamu" -> {
                // keeps near Spopovich
                if (t == null && m.tickCount % 40 == 0) {
                    DbMob spo = nearest(m, "spopovich", 20);
                    if (spo != null && m.distanceTo(spo) > 5) {
                        m.getNavigation().moveTo(spo, 1.0);
                    }
                }
            }
            case "spopovich" -> {
                if (m.tickCount % 20 == 0 && nearest(m, "yamu", 24) == null && m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue() < 0.34
                        && m.tickCount > 200) {
                    // Yamu fell: furious, faster
                    m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.36);
                }
            }
            default -> {
            }
        }
    }

    @Nullable
    private static DbMob nearest(DbMob m, String id, double r) {
        DbMob best = null;
        double bd = r * r;
        for (DbMob d : m.level().getEntitiesOfClass(DbMob.class, m.getBoundingBox().inflate(r), d -> d != m && id.equals(d.id()))) {
            double dd = d.distanceToSqr(m);
            if (dd < bd) {
                bd = dd;
                best = d;
            }
        }
        return best;
    }

    // ---------- drops (the preview's "Solta", with the mod's items and the closest vanilla ones) ----------

    static void drop(DbMob m, ServerLevel level, boolean byPlayer) {
        RandomSource r = level.getRandom();
        switch (m.id()) {
            case "saibaman" -> {
                some(m, Items.WHEAT_SEEDS, 1, 2, 0.7, r);
                some(m, Items.SLIME_BALL, 0, 1, 0.3, r);
                rare(m, com.starkinhon.battlegroundsmod.ModItems.SENZU.get(), 0.05, byPlayer, r);
            }
            case "bandido_deserto" -> {
                some(m, Items.LEATHER, 1, 2, 0.7, r);
                some(m, Items.IRON_NUGGET, 1, 3, 0.5, r);
                rare(m, Items.GOLD_NUGGET, 0.2, byPlayer, r);
            }
            case "soldado_red_ribbon" -> {
                some(m, Items.GUNPOWDER, 1, 2, 0.8, r);
                some(m, Items.BREAD, 1, 1, 0.4, r);
                rare(m, com.starkinhon.battlegroundsmod.ModItems.DRAGON_RADAR.get(), 0.01, byPlayer, r);
            }
            case "pirata" -> {
                some(m, Items.GUNPOWDER, 0, 2, 0.6, r);
                some(m, Items.COD, 1, 2, 0.6, r);
                rare(m, Items.GOLD_NUGGET, 0.25, byPlayer, r);
                rare(m, Items.MAP, 0.03, byPlayer, r);
            }
            case "yeti" -> {
                some(m, Items.WHITE_WOOL, 1, 3, 1, r);
                some(m, Items.PACKED_ICE, 1, 2, 0.6, r);
                rare(m, Items.BONE, 0.3, byPlayer, r);
            }
            case "raptor" -> {
                some(m, Items.BEEF, 1, 2, 1, r);
                some(m, Items.LEATHER, 0, 1, 0.6, r);
                rare(m, Items.FLINT, 0.08, byPlayer, r);
            }
            case "dino_chifrudo" -> {
                some(m, Items.BEEF, 2, 4, 1, r);
                some(m, Items.LEATHER, 1, 3, 1, r);
                rare(m, Items.BONE, 0.2, byPlayer, r);
            }
            case "tiranossauro" -> {
                some(m, Items.BEEF, 4, 8, 1, r);
                some(m, Items.LEATHER, 2, 5, 1, r);
                rare(m, Items.BONE_BLOCK, 0.15, byPlayer, r);
            }
            case "pterodactilo" -> {
                some(m, Items.PHANTOM_MEMBRANE, 0, 1, 0.5, r);
                some(m, Items.CHICKEN, 1, 1, 0.7, r);
                rare(m, Items.FEATHER, 0.3, byPlayer, r);
            }
            case "dragao" -> {
                some(m, Items.BEEF, 3, 6, 1, r);
                rare(m, Items.DRAGON_BREATH, 0.12, byPlayer, r);
                rare(m, Items.TURTLE_SCUTE, 0.35, byPlayer, r);
            }
            case "soldado_freeza" -> {
                some(m, Items.IRON_NUGGET, 1, 3, 0.7, r);
                rare(m, com.starkinhon.battlegroundsmod.ModItems.SCOUTER.get(), 0.06, byPlayer, r);
            }
            case "cell_jr" -> {
                some(m, Items.SLIME_BALL, 0, 2, 0.5, r);
                rare(m, com.starkinhon.battlegroundsmod.ModItems.SENZU.get(), 0.04, byPlayer, r);
            }
            case "yakon" -> {
                some(m, Items.LEATHER, 2, 4, 1, r);
                some(m, Items.GLOWSTONE_DUST, 2, 5, 1, r);
                rare(m, com.starkinhon.battlegroundsmod.ModItems.SENZU.get(), 0.25, byPlayer, r);
            }
            case "tigre_sabre" -> {
                some(m, Items.BEEF, 1, 3, 1, r);
                some(m, Items.LEATHER, 1, 2, 1, r);
                rare(m, Items.BONE, 0.3, byPlayer, r);
            }
            case "peixe_gigante" -> {
                some(m, Items.COD, 3, 6, 1, r);
                some(m, Items.SALMON, 1, 3, 0.7, r);
                rare(m, Items.FISHING_ROD, 0.05, byPlayer, r);
            }
            case "androide_gero" -> {
                some(m, Items.IRON_INGOT, 1, 2, 0.8, r);
                some(m, Items.REDSTONE, 1, 3, 0.7, r);
                rare(m, Items.COPPER_INGOT, 0.3, byPlayer, r);
            }
            case "tao_pai_pai" -> {
                some(m, Items.PINK_WOOL, 1, 2, 1, r);
                rare(m, com.starkinhon.battlegroundsmod.ModItems.SENZU.get(), 0.2, byPlayer, r);
            }
            case "spopovich", "yamu" -> {
                some(m, Items.RED_WOOL, 1, 2, 0.8, r);
                some(m, Items.GLASS_BOTTLE, 0, 1, 0.5, r);
                rare(m, com.starkinhon.battlegroundsmod.ModItems.SENZU.get(), 0.08, byPlayer, r);
            }
            case "babidi" -> {
                some(m, Items.BOOK, 1, 1, 1, r);
                some(m, Items.ORANGE_WOOL, 1, 2, 1, r);
                rare(m, com.starkinhon.battlegroundsmod.ModItems.SENZU.get(), 0.3, byPlayer, r);
            }
            default -> {
            }
        }
    }

    private static void some(DbMob m, Item item, int min, int max, double chance, RandomSource r) {
        if (r.nextDouble() >= chance) {
            return;
        }
        int n = min + r.nextInt(max - min + 1);
        if (n > 0) {
            m.spawnAtLocation(new ItemStack(item, n));
        }
    }

    private static void rare(DbMob m, Item item, double chance, boolean byPlayer, RandomSource r) {
        if (byPlayer && r.nextDouble() < chance) {
            ItemEntity e = m.spawnAtLocation(new ItemStack(item));
            if (e != null) {
                e.setGlowingTag(true);
            }
        }
    }

    // ---------- voices ----------

    @Nullable
    static SoundEvent ambient(DbMob m) {
        return switch (m.id()) {
            case "saibaman", "cell_jr" -> SoundEvents.WITCH_CELEBRATE;
            case "raptor" -> SoundEvents.PARROT_IMITATE_VINDICATOR;
            case "dino_chifrudo" -> SoundEvents.COW_AMBIENT;
            case "tiranossauro", "dragao" -> SoundEvents.RAVAGER_AMBIENT;
            case "pterodactilo" -> SoundEvents.PHANTOM_AMBIENT;
            case "yeti" -> SoundEvents.POLAR_BEAR_AMBIENT;
            case "tigre_sabre" -> SoundEvents.CAT_STRAY_AMBIENT;
            case "peixe_gigante" -> null;
            case "androide_gero" -> SoundEvents.BEACON_AMBIENT;
            case "yakon" -> SoundEvents.WARDEN_AMBIENT;
            case "babidi" -> SoundEvents.EVOKER_AMBIENT;
            case "spopovich" -> SoundEvents.RAVAGER_AMBIENT;
            default -> SoundEvents.PILLAGER_AMBIENT;
        };
    }

    @Nullable
    static SoundEvent hurtSound(DbMob m) {
        return switch (m.id()) {
            case "saibaman", "cell_jr" -> SoundEvents.WITCH_HURT;
            case "raptor", "pterodactilo" -> SoundEvents.PHANTOM_HURT;
            case "dino_chifrudo", "tiranossauro", "dragao", "spopovich" -> SoundEvents.RAVAGER_HURT;
            case "yeti" -> SoundEvents.POLAR_BEAR_HURT;
            case "tigre_sabre" -> SoundEvents.CAT_HURT;
            case "peixe_gigante" -> SoundEvents.COD_HURT;
            case "androide_gero" -> SoundEvents.IRON_GOLEM_HURT;
            case "yakon" -> SoundEvents.WARDEN_HURT;
            case "babidi" -> SoundEvents.EVOKER_HURT;
            default -> SoundEvents.PILLAGER_HURT;
        };
    }

    @Nullable
    static SoundEvent deathSound(DbMob m) {
        return switch (m.id()) {
            case "saibaman", "cell_jr" -> SoundEvents.WITCH_DEATH;
            case "raptor", "pterodactilo" -> SoundEvents.PHANTOM_DEATH;
            case "dino_chifrudo", "tiranossauro", "dragao", "spopovich" -> SoundEvents.RAVAGER_DEATH;
            case "yeti" -> SoundEvents.POLAR_BEAR_DEATH;
            case "tigre_sabre" -> SoundEvents.CAT_DEATH;
            case "peixe_gigante" -> SoundEvents.COD_DEATH;
            case "androide_gero" -> SoundEvents.IRON_GOLEM_DEATH;
            case "yakon" -> SoundEvents.WARDEN_DEATH;
            case "babidi" -> SoundEvents.EVOKER_DEATH;
            default -> SoundEvents.PILLAGER_DEATH;
        };
    }

    static float pitch(DbMob m) {
        return switch (m.id()) {
            case "saibaman", "cell_jr", "babidi" -> 1.5f;
            case "raptor", "pterodactilo" -> 1.2f;
            case "tiranossauro", "dragao", "yakon" -> 0.6f;
            case "spopovich", "dino_chifrudo" -> 0.8f;
            default -> 1f;
        };
    }
}
