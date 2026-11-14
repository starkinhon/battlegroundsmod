package com.starkinhon.battlegroundsmod.animal.jjmob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import javax.annotation.Nullable;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Spec;
import com.starkinhon.battlegroundsmod.animal.naruto.NmNet;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// The blows of the Jujutsu curses (Stark 2026-11-14, preview tools/mobs/jjmobs approved: "porta"), each biome's in its
// own class (JjShibuya, JjFloresta, JjTemplos, JjAldeia, JjDagon, JjHanami, JjJogo, JjKyoto, JjColonia); here what
// they share: the helpers, the exorcism, what each one is made of, the lasting zones (magma, the bleeding) and the
// kit the Naruto creatures' body calls.
// Every blow is written by the preview's frames: frame f of a mode is f / 20 s of the preview's "t % N", so what
// lands, lands on the frame the baked animation shows it. Damages are on the mod's Tier 0 scale like the Naruto
// creatures' (Combat.hurt shrinks them by the creature's tier). Combate limpo: a hold never takes someone already
// held, every hold has its own length and ends with its blow (or the body's death), and the ones that hold someone
// let go after a few hits from them.
final class JjMobMoves {
    private JjMobMoves() {
    }

    // One biome's blows. f = the frame of the mode (tick - 1).
    interface Moves {
        default boolean canUse(JjMob m, String mode, LivingEntity t) {
            return true;
        }

        default void start(JjMob m, String mode, @Nullable LivingEntity t) {
        }

        void tick(JjMob m, String mode, int f, @Nullable LivingEntity t);

        default void end(JjMob m, String mode, @Nullable LivingEntity t) {
        }

        // the one it holds (or clings to) hit it: true lets go
        default boolean struck(JjMob m, String mode, LivingEntity attacker, float amount) {
            return false;
        }

        default void idle(JjMob m) {
        }

        @Nullable
        default Vec3 fleeFrom(JjMob m) {
            return null;
        }
    }

    private static final Moves NONE = (m, mode, f, t) -> {
    };

    static Moves moves(JjMob m) {
        return switch (m.kind()) {
            case "mosca", "cabecao", "transfigurado", "metro" -> JjShibuya.MOVES;
            case "rastejante", "verme", "olhos", "utero" -> JjFloresta.MOVES;
            case "boneco", "kitsune", "sino" -> JjTemplos.MOVES;
            case "espantalho", "poco", "kuchisake" -> JjAldeia.MOVES;
            case "peixe", "caranguejo", "embriao", "afogado" -> JjDagon.MOVES;
            case "broto", "javali", "guardiao" -> JjHanami.MOVES;
            case "inseto", "vulcaozinho", "golem" -> JjJogo.MOVES;
            case "lanterna", "mechamaru", "oni" -> JjKyoto.MOVES;
            case "larva", "abate", "solta" -> JjColonia.MOVES;
            case "kogane" -> JjKogane.MOVES;
            default -> NONE;
        };
    }

    // ---------- closing in ----------

    // A blow from up close lands ON the body (Stark 2026-11-14: "they don't even touch me, too artificial"): from its
    // start until it lands, a close blow that walks no path of its own steps the body in to touching distance and keeps
    // it turned to the target, so the bite, the claw and the punch land in contact on the frame the animation shows.
    private static final double MELEE = 4.0;
    // close blows that reach by themselves (a tongue, the train's doors, roots, the bell's trap, a school of fish)
    private static final Set<String> REACHES = Set.of("lingua", "portas", "enraizar", "prender", "cardume");
    private static final double TOUCH = 0.15;

    static void closeIn(JjMob m, String mode, int tick, LivingEntity t) {
        if (!alive(t) || m.isKogane() || m.caught >= 0 || m.heldId >= 0 || REACHES.contains(mode) || JjTravel.of(m.id(), mode) != null) {
            return;
        }
        Spec s = m.spec();
        int kind = s.kindOf(mode);
        NarutoSpec.Attack a = s.attack(kind);
        if (a == null || a.max() > MELEE || tick >= a.impact()) {
            return;
        }
        Vec3 to = t.position().subtract(m.position()).multiply(1, 0, 1);
        double len = to.length();
        if (len < 1.0E-3) {
            return;
        }
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        m.faceBlow(yaw);
        double gap = m.gap(t);
        if (gap <= TOUCH) {
            return;
        }
        int left = Math.max(1, a.impact() - tick);
        double step = Math.min(0.6, (gap - TOUCH) / left);
        m.move(net.minecraft.world.entity.MoverType.SELF, to.scale(step / len));
    }

    // ---------- the kit ----------

    static final NarutoMob.Kit KIT = new NarutoMob.Kit() {
        public Spec spec(String id) {
            return JjMobSpec.get(id);
        }

        public float[][] travel(String id, String mode) {
            return JjTravel.of(id, mode);
        }

        public boolean canUse(NarutoMob n, String mode, LivingEntity t) {
            JjMob m = (JjMob) n;
            if (m.dying >= 0 || m.isKogane()) {
                return false;
            }
            if (m.summoned() && !m.kind().equals("peixe") && !m.kind().equals("mosca")) {
                // the Kitsune's paper fakes only run around
                return false;
            }
            if (m.summoned() && !mode.equals("mordida")) {
                return false;
            }
            return moves(m).canUse(m, mode, t);
        }

        public Vec3 fleeFrom(NarutoMob n) {
            return moves((JjMob) n).fleeFrom((JjMob) n);
        }

        public void start(NarutoMob n, String mode, LivingEntity t) {
            JjMob m = (JjMob) n;
            m.mode = mode;
            m.struck = 0;
            m.caught = -1;
            m.mark = t != null ? t.position() : m.position();
            HIT_ONCE.remove(m.getId());
            moves(m).start(m, mode, t);
        }

        public void tick(NarutoMob n, String mode, int tick, LivingEntity t) {
            JjMob m = (JjMob) n;
            closeIn(m, mode, tick, t);
            moves(m).tick(m, mode, tick - 1, t);
        }

        public void impact(NarutoMob n, String mode, LivingEntity t) {
            // everything lands by its frame in tick
        }

        public void end(NarutoMob n, String mode, LivingEntity t) {
            JjMob m = (JjMob) n;
            moves(m).end(m, mode, t);
            finish(m);
        }

        public void teleport(NarutoMob n, String mode, LivingEntity t) {
        }

        public boolean answer(NarutoMob n, LivingEntity attacker, DamageSource source, float amount) {
            return false;
        }

        public void hurtBy(NarutoMob n, LivingEntity attacker, float amount) {
            JjMob m = (JjMob) n;
            // whoever hits it while it holds or clings to someone (the one held cannot strike back: a friend frees them; the
            // one a fly or a larva clings to can)
            if (m.attacking() && m.mode != null && (m.heldId >= 0 || m.caught >= 0)) {
                m.struck++;
                if (moves(m).struck(m, m.mode, attacker, amount)) {
                    letGo(m);
                    m.caught = -1;
                }
            }
        }

        public void died(NarutoMob n) {
            JjMob m = (JjMob) n;
            finish(m);
        }

        public void idle(NarutoMob n) {
            JjMob m = (JjMob) n;
            if (!m.attacking() && (m.release != null || m.mode != null)) {
                // a blow cut short (a stun in its wind-up): nothing it held stays held
                finish(m);
            }
            moves(m).idle(m);
        }

        public void drop(NarutoMob n, ServerLevel level, boolean byPlayer) {
            JjDrops.drop((JjMob) n, level, byPlayer);
        }

        public SoundEvent ambient(NarutoMob n) {
            return JjDrops.ambient((JjMob) n);
        }

        public SoundEvent hurtSound(NarutoMob n) {
            return JjDrops.hurtSound((JjMob) n);
        }

        public SoundEvent deathSound(NarutoMob n) {
            return JjDrops.deathSound((JjMob) n);
        }

        public float pitch(NarutoMob n) {
            return JjDrops.pitch((JjMob) n);
        }
    };

    // the end of a blow (or of the body): what it held lets go, its tube of flesh goes back
    private static void finish(JjMob m) {
        letGo(m);
        m.caught = -1;
        if (TUBES.remove(m.getId())) {
            SubNet.send(lvl(m), JmNet.TUBE, m.getId(), m.position(), Vec3.ZERO, 0, 0, 1);
        }
        if (m.mode != null && m.isNoGravity() && m.spec().move != com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec.Move.FLYER) {
            m.setNoGravity(false);
        }
        m.mode = null;
    }

    // ---------- helpers ----------

    static ServerLevel lvl(NarutoMob m) {
        return (ServerLevel) m.level();
    }

    static Vec3 ahead(NarutoMob m, double d) {
        return m.position().add(m.forward().scale(d));
    }

    static Vec3 right(NarutoMob m) {
        Vec3 f = m.forward();
        return new Vec3(-f.z, 0, f.x);
    }

    // a point of the body: fwd blocks ahead, side to its right, up over its feet (all scaled by nothing: blocks)
    static Vec3 body(NarutoMob m, double fwd, double side, double up) {
        return m.position().add(m.forward().scale(fwd)).add(right(m).scale(side)).add(0, up, 0);
    }

    static boolean alive(LivingEntity t) {
        return t != null && t.isAlive() && !t.isRemoved() && !(t instanceof Player p && p.isSpectator());
    }

    static void sound(NarutoMob m, SoundEvent s, float vol, float pitch) {
        SubCommon.sound(lvl(m), m.position(), s, vol, pitch);
    }

    static void sound(NarutoMob m, Vec3 at, SoundEvent s, float vol, float pitch) {
        SubCommon.sound(lvl(m), at, s, vol, pitch);
    }

    static void hit(NarutoMob m, LivingEntity t, float dmg, float size, int color) {
        Combat.hurt(m, t, dmg * power(m));
        impact(m, Combat.center(t), size, splash(m));
    }

    // the curse's tier in its blows (Combat.hurt takes them down to a plain creature's, Tier 10)
    static float power(NarutoMob m) {
        return JjMobSpec.power(m.id());
    }

    // a blow's impact (JmNet.IMPACT: no glowing streaks, only what the blow knocks out), along the body's facing
    static void impact(NarutoMob m, Vec3 at, float size, int what) {
        JmNet.send(lvl(m), JmNet.IMPACT, m.getId(), at, m.forward(), size, what, m.getRandom().nextInt(64));
    }

    // A heavy blow breaks the ground and what stands where it lands (Stark 2026-11-14: they must wreck the place like
    // the other fighters): the blocks in radius r of at, down to one under it, that a hand could break (dirt, stone,
    // wood, leaves, glass; never what has a block entity or is unbreakable), some thrown out. Only with
    // soulsBlockDestruction and mobGriefing.
    static void smash(NarutoMob m, Vec3 at, double r) {
        ServerLevel level = lvl(m);
        if (!com.starkinhon.battlegroundsmod.combat.BlockRules.canBreak(level)) {
            return;
        }
        net.minecraft.core.BlockPos mid = net.minecraft.core.BlockPos.containing(at);
        int n = (int) Math.ceil(r);
        int thrown = 0;
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -1; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    if (dx * dx + dy * dy * 1.5 + dz * dz > r * r) {
                        continue;
                    }
                    net.minecraft.core.BlockPos p = mid.offset(dx, dy, dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    var state = level.getBlockState(p);
                    float hard = state.getDestroySpeed(level, p);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || hard < 0 || hard > 3) {
                        continue;
                    }
                    if (thrown < 6 && dy >= 0 && m.getRandom().nextInt(5) == 0) {
                        thrown++;
                        var fly = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, p, state);
                        fly.dropItem = false;
                        Vec3 out = new Vec3(dx, 0, dz);
                        out = out.lengthSqr() < 1.0E-4 ? m.forward() : out.normalize();
                        fly.setDeltaMovement(out.scale(0.3 + 0.4 * m.getRandom().nextDouble()).add(0, 0.5 + 0.4 * m.getRandom().nextDouble(), 0));
                        fly.hurtMarked = true;
                    } else {
                        com.starkinhon.battlegroundsmod.combat.BlockBreakFx.erase(level, p, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    // a cut: blood sprayed across it
    static void rip(NarutoMob m, Vec3 at, float size) {
        impact(m, at, size, JmNet.RIP);
    }

    private static int splash(NarutoMob m) {
        return switch (((JjMob) m).kind()) {
            case "peixe", "embriao", "afogado" -> JmNet.WATER;
            default -> JmNet.FLESH;
        };
    }

    // cursed energy (through armor)
    static void magic(NarutoMob m, LivingEntity t, float dmg) {
        Combat.hurtMagic(m, t, dmg * power(m));
    }

    // in front of the body (cone) and within reach blocks of its edge
    static boolean front(NarutoMob m, LivingEntity t, double reach, double minDot) {
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

    // who a blow can hit around a point: never another curse or creature of the mod's biomes
    static List<LivingEntity> around(NarutoMob m, Vec3 at, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Combat.inRadius(lvl(m), at, r, m)) {
            if (!(e instanceof NarutoMob) && alive(e)) {
                out.add(e);
            }
        }
        return out;
    }

    static List<LivingEntity> line(NarutoMob m, Vec3 from, Vec3 to, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Combat.alongLine(lvl(m), from, to, r, m)) {
            if (!(e instanceof NarutoMob) && alive(e)) {
                out.add(e);
            }
        }
        return out;
    }

    private static final Map<Integer, Set<Integer>> HIT_ONCE = new HashMap<>();

    // true the first time this blow reaches t
    static boolean once(NarutoMob m, LivingEntity t) {
        return HIT_ONCE.computeIfAbsent(m.getId(), k -> new HashSet<>()).add(t.getId());
    }

    // a hold: refused on someone already held, and on a boss for long ones
    static boolean hold(JjMob m, LivingEntity t, int ticks) {
        if (!holdable(t, ticks)) {
            return false;
        }
        m.release = Freeze.hold(t, ticks);
        m.heldId = t.getId();
        m.struck = 0;
        return true;
    }

    static boolean hold(JjMob m, LivingEntity t, int ticks, IntFunction<Vec3> anchor) {
        if (!holdable(t, ticks)) {
            return false;
        }
        m.release = Freeze.hold(t, ticks, anchor);
        m.heldId = t.getId();
        m.struck = 0;
        return true;
    }

    // held but free to look around (turning away is how one breaks the Olhos' stare)
    static boolean holdLook(JjMob m, LivingEntity t, int ticks) {
        if (!holdable(t, ticks)) {
            return false;
        }
        m.release = Freeze.holdLookFree(t, ticks);
        m.heldId = t.getId();
        return true;
    }

    private static boolean holdable(LivingEntity t, int ticks) {
        return alive(t) && !Freeze.isFrozen(t) && !(t instanceof SoulBoss b && b.isBoss() && ticks > 40)
                && !(t instanceof Player p && p.isCreative());
    }

    static void letGo(NarutoMob m) {
        if (m.release != null) {
            m.release.run();
            m.release = null;
        }
        m.heldId = -1;
    }

    static boolean holding(NarutoMob m, LivingEntity t) {
        return t != null && m.heldId == t.getId() && m.release != null;
    }

    static void slow(NarutoMob m, LivingEntity t, int ticks, int level) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, level, false, true), m);
    }

    static void dizzy(NarutoMob m, LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks + 40, 0, false, false), m);
        SubNet.send(lvl(m), SubNet.DIZZY, t.getId(), t.position(), Vec3.ZERO, ticks, 0, 0);
        if (t instanceof Mob mob && !(t instanceof SoulBoss)) {
            mob.setTarget(null);
        }
    }

    static void screen(LivingEntity t, int mode, int ticks) {
        NmNet.screen(t, mode, ticks);
    }

    // the one it holds or clings to now
    @Nullable
    static LivingEntity victim(JjMob m) {
        int id = m.heldId >= 0 ? m.heldId : m.caught;
        return id >= 0 && m.level().getEntity(id) instanceof LivingEntity v ? v : null;
    }

    static Vec3 ground(NarutoMob m, Vec3 at) {
        return SubCommon.ground(lvl(m), at);
    }

    // where a line from from to to first meets a wall
    static Vec3 clip(NarutoMob m, Vec3 from, Vec3 to) {
        HitResult hit = lvl(m).clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, m));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    // a blow that spreads from a point: weaker at its edge
    static void wave(NarutoMob m, Vec3 at, double r, float dmg, double push, double up) {
        for (LivingEntity t : around(m, at, r)) {
            if (!once(m, t)) {
                continue;
            }
            double k = 1 - 0.5 * Math.min(1, t.position().distanceTo(at) / r);
            hit(m, t, (float) (dmg * k), 0.7f, 10);
            Combat.pushFrom(t, at, push * k, up * k);
        }
    }

    // the face of the blow turned to t (a blow that does not walk a path)
    static void face(NarutoMob m, LivingEntity t) {
        if (!alive(t)) {
            return;
        }
        double dx = t.getX() - m.getX(), dz = t.getZ() - m.getZ();
        if (dx * dx + dz * dz > 1.0E-4) {
            m.faceBlow((float) (Math.atan2(dz, dx) * (180 / Math.PI)) - 90f);
        }
    }

    // ---------- tubes of flesh (the tongue, the cord, the hair, the silk) ----------

    private static final Set<Integer> TUBES = new HashSet<>();

    static void tube(NarutoMob m, Vec3 root, Vec3 tip, float thick, int look) {
        TUBES.add(m.getId());
        SubNet.send(lvl(m), JmNet.TUBE, m.getId(), root, tip.subtract(root), thick, look, 0);
    }

    static void endTube(NarutoMob m) {
        if (TUBES.remove(m.getId())) {
            SubNet.send(lvl(m), JmNet.TUBE, m.getId(), m.position(), Vec3.ZERO, 0, 0, 1);
        }
    }

    // ---------- what lasts after a blow: magma pools, the bleeding ----------

    abstract static class Zone {
        final ServerLevel level;
        int age;

        Zone(ServerLevel level) {
            this.level = level;
        }

        // true when done
        abstract boolean tick();
    }

    private static final List<Zone> ZONES = new ArrayList<>();
    private static final Map<Integer, Integer> BLEEDING = new HashMap<>();

    static void zone(Zone z) {
        ZONES.add(z);
    }

    // blood running from a cut: a little every second
    static void bleed(NarutoMob m, LivingEntity t, int ticks) {
        boolean already = BLEEDING.containsKey(t.getId());
        BLEEDING.merge(t.getId(), ticks, Math::max);
        if (already) {
            return;
        }
        zone(new Zone(lvl(m)) {
            boolean tick() {
                Integer left = BLEEDING.get(t.getId());
                if (left == null || left <= 0 || !alive(t)) {
                    BLEEDING.remove(t.getId());
                    return true;
                }
                BLEEDING.put(t.getId(), left - 1);
                if (left % 20 == 0) {
                    Combat.hurtMagic(m.isAlive() ? m : t, t, 8 * power(m));
                    SubNet.send(level, SubNet.BLOOD_SPLASH, 0, Combat.center(t), new Vec3(0, -1, 0), 0.4f, 0, 0);
                }
                return false;
            }
        });
    }

    // a pool of magma burning whoever stands in it (the Vulcãozinho's bombs, the Inseto's burst)
    static void magma(NarutoMob m, Vec3 at, float r, int ticks) {
        Vec3 g = ground(m, at);
        SubNet.send(lvl(m), JmNet.MAGMA, 0, g, Vec3.ZERO, r, ticks, 0);
        zone(new Zone(lvl(m)) {
            boolean tick() {
                if (age > ticks) {
                    return true;
                }
                if (age % 10 == 0) {
                    for (LivingEntity t : Combat.inRadius(level, g, r, m)) {
                        if (t instanceof NarutoMob || !alive(t) || t.getY() > g.y + 0.8) {
                            continue;
                        }
                        t.igniteForSeconds(2);
                        Combat.hurtMagic(m.isAlive() ? m : t, t, 10 * power(m));
                    }
                }
                return false;
            }
        });
    }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (!(e.getLevel() instanceof ServerLevel level) || ZONES.isEmpty()) {
                return;
            }
            List<Zone> mine = new ArrayList<>();
            for (Zone z : ZONES) {
                if (z.level == level) {
                    mine.add(z);
                }
            }
            for (Zone z : mine) {
                z.age++;
                if (z.tick()) {
                    ZONES.remove(z);
                }
            }
        });
        JjKogane.register();
    }

    public static void resetStatics() {
        HIT_ONCE.clear();
        TUBES.clear();
        ZONES.clear();
        BLEEDING.clear();
        JjDagon.resetStatics();
        JjKyoto.resetStatics();
        JjKogane.resetStatics();
    }

    // ---------- summoned ones (the Útero's flies, the school of fish, the Kitsune's fakes) ----------

    @Nullable
    static JjMob summon(JjMob maker, String kind, Vec3 at, int ticks, @Nullable LivingEntity target) {
        var type = ModEntities.JJ_MOBS.get("jj_" + kind);
        if (type == null) {
            return null;
        }
        JjMob s = type.get().create(lvl(maker));
        if (s == null) {
            return null;
        }
        s.moveTo(at.x, at.y, at.z, maker.getYRot(), 0);
        s.finalizeSpawn(lvl(maker), lvl(maker).getCurrentDifficultyAt(s.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        s.makeSummoned(ticks, maker.getId());
        if (target != null) {
            s.setTarget(target);
        }
        lvl(maker).addFreshEntity(s);
        maker.spawned.add(s.getId());
        return s;
    }

    // how many it made are still around
    static int summonedAlive(JjMob maker) {
        int n = 0;
        Iterator<Integer> it = maker.spawned.iterator();
        while (it.hasNext()) {
            if (maker.level().getEntity(it.next()) instanceof JjMob s && s.isAlive()) {
                n++;
            } else {
                it.remove();
            }
        }
        return n;
    }

    // a summoned one's time is up: the fake turns to paper, the fish dives back into the sand, the fly flies off
    static void unsummon(JjMob m) {
        if (!m.isAlive()) {
            return;
        }
        if (m.kind().equals("kitsune")) {
            paperBurst(m);
            return;
        }
        SubNet.send(lvl(m), SubNet.DUST, 0, m.position(), Vec3.ZERO, 0.6f, 0, 0);
        m.discard();
    }

    // one hit and the Kitsune's fake is paper
    static void paperBurst(JjMob m) {
        SubNet.send(lvl(m), JmNet.PAPER, 0, m.position().add(0, 0.4, 0), Vec3.ZERO, 14, 0, 0);
        sound(m, SoundEvents.BOOK_PAGE_TURN, 1.2f, 1.4f);
        m.discard();
    }

    // ---------- the exorcism ----------

    static void dyingStart(JjMob m, DamageSource source) {
        // a dead body falls (the flies, fish, insects and lanterns stop flying; the baked 'morte' lays it on the ground)
        m.setNoGravity(false);
        m.caught = -1;
        if (!m.stolen.isEmpty()) {
            ItemEntity item = new ItemEntity(lvl(m), m.getX(), m.getY() + 0.4, m.getZ(), m.stolen.copy());
            item.setDefaultPickUpDelay();
            item.setGlowingTag(true);
            lvl(m).addFreshEntity(item);
            m.stolen = net.minecraft.world.item.ItemStack.EMPTY;
        }
        for (int id : m.spawned) {
            if (m.level().getEntity(id) instanceof JjMob s && s.isAlive() && s.summoned() && s.kind().equals("kitsune")) {
                paperBurst(s);
            }
        }
        if (m.kind().equals("abate")) {
            JjKogane.abateBeaten(m, source);
        }
    }

    static void dyingTick(JjMob m, int dying) {
        ServerLevel level = lvl(m);
        if (m.kind().equals("boneco")) {
            // a cursed corpse: its stuffing bursts out as it falls, the core rolls out
            if (dying == 30) {
                SubNet.send(level, JmNet.STUFFING, m.getId(), m.position().add(0, 0.5, 0), Vec3.ZERO, 1, 0, 0);
                sound(m, SoundEvents.WOOL_BREAK, 1.4f, 0.7f);
            }
            return;
        }
        if (m.kind().equals("abate")) {
            // a person: faints, and the Kogane takes them out of the game
            if (dying == 70) {
                SubNet.send(level, SubNet.POOF, 0, m.position().add(0, 0.5, 0), Vec3.ZERO, 1.2f, 0, 0);
            }
            return;
        }
        if (dying == JjMob.ASH_START) {
            SubNet.send(level, JmNet.EXORCIZE, m.getId(), m.position(), Vec3.ZERO, JjMob.ASH_TICKS, 0, 0);
            sound(m, SoundEvents.SOUL_ESCAPE.value(), 1.4f, 0.6f);
            sound(m, SoundEvents.SAND_BREAK, 1.2f, 0.5f);
        }
    }

    // ---------- what each one is made of (the preview's "Fraqueza") ----------

    static float material(JjMob m, DamageSource source) {
        boolean fire = source.is(DamageTypeTags.IS_FIRE);
        boolean boom = source.is(DamageTypeTags.IS_EXPLOSION);
        boolean arrow = source.is(DamageTypeTags.IS_PROJECTILE);
        JjMob.Tool tool = JjMob.tool(source);
        float k = 1;
        switch (m.kind()) {
            case "espantalho", "broto" -> k = fire ? 3 : 1;
            case "larva", "javali", "guardiao", "embriao", "afogado", "peixe" -> k = fire ? 2 : 1;
            case "lanterna" -> k = tool == JjMob.Tool.SWORD ? 2 : fire ? 0.5f : 1;
            case "sino" -> {
                if (tool == JjMob.Tool.SWORD) {
                    k = 0.5f;
                }
                // the bell has no eye on its back
                if (source.getEntity() != null && behind(m, source.getEntity().position())) {
                    k *= 2;
                }
            }
            case "caranguejo" -> {
                if (arrow || tool == JjMob.Tool.SWORD && source.getEntity() != null && !behind(m, source.getEntity().position())) {
                    k = 0.5f;
                }
            }
            case "vulcaozinho" -> k = tool == JjMob.Tool.SWORD ? 0.5f : tool == JjMob.Tool.PICKAXE ? 2 : fire ? 0 : 1;
            case "golem" -> k = tool == JjMob.Tool.SWORD ? 0.5f : tool == JjMob.Tool.PICKAXE || boom ? 2 : fire ? 0 : 1;
            case "inseto" -> k = fire ? 0 : 1;
            case "mechamaru" -> k = arrow || tool == JjMob.Tool.FIST ? 0.5f : tool == JjMob.Tool.AXE || boom ? 2 : 1;
            case "oni" -> k = arrow ? 0.5f : 1;
            default -> {
            }
        }
        return k;
    }

    // a point behind the body's back
    static boolean behind(NarutoMob m, Vec3 p) {
        Vec3 to = p.subtract(m.position()).multiply(1, 0, 1);
        return to.lengthSqr() > 0.01 && to.normalize().dot(m.forward()) < -0.3;
    }
}
