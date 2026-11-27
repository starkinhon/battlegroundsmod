// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Todo (SubAlma of Jujutsu, tier 6), kit remade with Stark (2026-11-27, preview forja/todo-kit/shinjuku-todo.js v9:
// "do lixo pro bglh foda"). Creative swaps and heavy punches: his X tears the ground into stones (TodoStone) that stay on
// the ground, and every skill of his can use any of them, big or small.
// Passive: whoever he swaps with the Z is lost for a moment: the next hit on them is a critical.
// Z Boogie Woogie: tap = a clap and he swaps with whoever he aims at (25 blocks), a person or one of his stones. Hold =
//   marks what he aimed at (A); let go aiming at something else (B) and A and B swap (a stone with an enemy too). Let go
//   at nothing and he swaps with A. A stone in front, the enemy behind: swap and the punch meant for the stone lands on
//   them; they fly back and break the stone on the way.
// X Quebra-Chao: he jumps and punches the ground: a crater, and the earth flies out in stones of every size (4 big, 7
//   medium, 16 pebbles) that fall with real physics (bounce, roll, settle) round the crater, never behind him. The one
//   in front lands between him and the enemy. Whoever is close to the punch is hit and thrown up.
// C Fake Clap: he claps without the technique (stays as it was).
// V Black Flash (needs one of his stones within 2 blocks): 1st V throws it about 15 blocks up. 2nd V aiming at the target
//   before it falls (its flight, about 2 s): the clap swaps the target with the stone; the target drops right onto him,
//   he jumps and punches up. On the contact everything slows and the screen loses its color for 0.6 s, then the Black
//   Flash bursts: black lightning, red waves, the ground under him sinks and the target goes up high. No 2nd V in time:
//   the stone smashes and the ult ends without a Black Flash.
// B Bloco Bala (needs one of his stones within 2 blocks): he kicks it at the enemy (up to 30 blocks), straight like a
//   bullet; close to them he claps and swaps with it in the air, the stone drops where he was and he keeps flying with
//   the kick's speed, arriving with a punch.
// Shift+V Black Flash Garantido (full ult bar): the 50-block dash steered by the aim (stays as it was).
public final class Todo {
    // A stone "glued" to him for the V and the B: within this many blocks of its edge (mobs look further, they don't
    // walk to their stones).
    private static final double REACH = 2;
    private static final double MOB_REACH = 7;
    // The V's throw: about 15 blocks up.
    private static final double THROW_HEIGHT = 15;
    // The X's stones: 4 big, 7 medium, 16 pebbles; the 4th one is the big stone that lands in front.
    private static final int FRONT = 3;
    // Each Todo's V while its stone is up.
    private static final Map<UUID, Flash> FLASH = new HashMap<>();

    private Todo() {
    }

    public static Power create() {
        return new Power("todo", 0x6B4A2E, 1.1f, List.of(
                new Ability(60, 0, Todo::boogie),
                new Ability(300, 0, Todo::quebraChao),
                new Ability(200, 0, Todo::fakeClap),
                new Ability(900, 0, Todo::blackFlash),
                new Ability(360, 0, Todo::blocoBala)),
                Arrays.asList(null, null, null, new Ability(900, 0, Todo::guaranteed), null));
    }

    // ---------- the swap ----------

    // Swaps two bodies (people or stones): each goes where the other stood, keeping where it looks. lost: the people
    // swapped are left open (the Z's passive).
    static void swap(ServerLevel level, Entity a, Entity b, boolean lost) {
        Vec3 pa = a.position();
        Vec3 pb = b.position();
        put(a, pb);
        put(b, pa);
        SubNet.send(level, SubNet.SWAP, 0, pa, Vec3.ZERO, 0, 0, 0);
        SubNet.send(level, SubNet.SWAP, 0, pb, Vec3.ZERO, 0, 0, 0);
        if (lost) {
            lost(a);
            lost(b);
        }
    }

    // A body put somewhere by a swap: a person keeps where it looks, a stone falls from there if it has to.
    private static void put(Entity e, Vec3 pos) {
        if (e instanceof TodoStone stone) {
            stone.placeAt(pos);
        } else if (e instanceof LivingEntity living) {
            NrCommon.teleport(living, pos, living.getYRot());
        }
    }

    // The passive: a swapped enemy is lost for a moment and the next hit on them is a critical.
    private static void lost(Entity e) {
        if (e instanceof LivingEntity living && !"todo".equals(JjCommon.power(living))) {
            SubCommon.setState(living, JjCommon.OPEN, 40);
        }
    }

    private static void clap(LivingEntity caster, ServerLevel level, boolean real) {
        SubCommon.pose(caster, "jj.clap");
        SubNet.send(level, SubNet.CLAP, caster.getId(), DbCommon.hands(caster), Vec3.ZERO, real ? 1 : 0, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 1.8f);
        SubCommon.sound(level, caster.position(), SoundEvents.SLIME_SQUISH, 1.2f, 1.6f);
    }

    // ---------- his stones ----------

    static List<TodoStone> stones(ServerLevel level, LivingEntity caster, double range) {
        return level.getEntitiesOfClass(TodoStone.class, caster.getBoundingBox().inflate(range), s -> s.isAlive() && s.ownedBy(caster));
    }

    // His closest stone glued to him (within REACH of its edge), or null.
    private static TodoStone nearStone(ServerLevel level, LivingEntity caster) {
        double reach = caster instanceof Player ? REACH : MOB_REACH;
        TodoStone best = null;
        double bestD = Double.MAX_VALUE;
        for (TodoStone s : stones(level, caster, reach + 2)) {
            double d = SubCommon.flat(s.position().subtract(caster.position())).length() - s.size() * 0.45 - caster.getBbWidth() * 0.5;
            if (d <= reach && d < bestD && Math.abs(s.getY() - caster.getY()) < 3) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    // What the Z aims at: one of his stones under the crosshair (closer than the person aimed, nothing solid between),
    // else the person aimed, else the closest in front.
    private static Entity swapAim(ServerLevel level, LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double free = wall.getType() == HitResult.Type.MISS ? range : wall.getLocation().distanceTo(eye) + 0.6;
        TodoStone stone = null;
        double stoneD = free;
        for (TodoStone s : stones(level, caster, range)) {
            double d = TodoStone.hitDistance(s, eye, end);
            if (d < stoneD) {
                stoneD = d;
                stone = s;
            }
        }
        LivingEntity aimed = Combat.withAllies(() -> SubCommon.aimed(caster, range));
        if (stone != null && (aimed == null || stoneD < aimed.distanceTo(caster))) {
            return stone;
        }
        if (aimed != null) {
            return aimed;
        }
        return Combat.withAllies(() -> JjCommon.target(caster, range));
    }

    // ---------- Z: Boogie Woogie ----------

    private static boolean boogie(LivingEntity caster, ServerLevel level) {
        // a mate of the group can be swapped too (Combat.withAllies), and one of his stones
        Entity a = swapAim(level, caster, 25);
        if (a == null) {
            SubCommon.warn(caster, "todo.no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int tap = 5;
        int max = 60;
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.GROUND_MARK, a.getId(), a.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            boolean holding = SubCommon.held(caster, age, 0) && age < max && a.isAlive();
            if (holding) {
                if (age >= tap) {
                    SubNet.send(level, SubNet.GROUND_MARK, a.getId(), a.position(), Vec3.ZERO, 1, 0, 0);
                    if ((age - tap) % 4 == 0) {
                        // Says what letting go does now (test with friends, 2026-11-25: nobody knew the hold existed).
                        Entity b = swapAim(level, caster, 25);
                        if (b != null && b != a) {
                            SubCommon.warn(caster, "todo.hold_swap", a.getDisplayName(), b.getDisplayName());
                        } else {
                            SubCommon.warn(caster, "todo.hold_mark", a.getDisplayName());
                        }
                    }
                }
                return false;
            }
            SubNet.send(level, SubNet.GROUND_MARK, a.getId(), a.position(), Vec3.ZERO, 0, 0, 1);
            if (!a.isAlive()) {
                return true;
            }
            clap(caster, level, true);
            SubCommon.busy(caster, 4);
            Entity b = age >= tap ? swapAim(level, caster, 25) : null;
            if (b != null && b != a) {
                swap(level, a, b, true);
                DbCommon.screen(level, Combat.center(a), 38);
            } else {
                swap(level, caster, a, true);
                DbCommon.screen(level, Combat.center(caster), 38);
                // A mob that loses sight of him for a moment.
                if (a instanceof Mob mob && mob.getTarget() == caster) {
                    Stun.apply(mob, 6);
                }
            }
            return true;
        });
        return true;
    }

    // ---------- X: Quebra-Chao ----------

    private static boolean quebraChao(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 fwd = SubCommon.flat(caster.getLookAngle());
        fwd = fwd.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : fwd.normalize();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 start = caster.position();
        Vec3 hop = start.add(fwd.scale(0.9));
        Vec3 c0 = SubCommon.ground(level, start.add(fwd.scale(2)));
        int jump = 8;
        Vec3[] at = {start};
        Vec3 face = fwd;
        SubCommon.pose(caster, "jj.slam_leap");
        SubCommon.busy(caster, jump + 8);
        JjCommon.carry(caster, jump, at);
        Tasks.add(age -> {
            if (guard.broken()) {
                Freeze.free(caster);
                return true;
            }
            if (age < jump) {
                float k = (age + 1) / (float) jump;
                at[0] = start.lerp(hop, SubCommon.smooth(k)).add(0, 1.3 * Math.sin(Math.PI * k), 0);
                if (age == jump - 3) {
                    SubCommon.pose(caster, "jj.slam_fist");
                }
                return false;
            }
            Freeze.free(caster);
            punchGround(caster, level, c0, face, right);
            return true;
        });
        return true;
    }

    // The fist in the ground: crater, hit round it, and the stones fly.
    private static void punchGround(LivingEntity caster, ServerLevel level, Vec3 c0, Vec3 fwd, Vec3 right) {
        BlockState earth = earth(level, c0);
        DbCommon.screen(level, c0, 39);
        Tasks.later(2, () -> DbCommon.screen(level, c0, 40));
        SubNet.send(level, SubNet.CRACKS, 0, c0, Vec3.ZERO, 3.2f, 9, 1);
        DbCommon.hit(level, c0.add(0, 0.3, 0), 1.3f, com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.WHITE);
        SubCommon.sound(level, c0, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.8f);
        SubCommon.sound(level, c0, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.8f, 0.5f);
        SubCommon.sound(level, c0, SoundEvents.STONE_BREAK, 1.6f, 0.6f);
        JjCommon.crater(level, c0, 2.3, 1.3, 0);
        for (LivingEntity e : Combat.inRadius(level, c0.add(0, 0.5, 0), 4, caster)) {
            double d = e.position().distanceTo(c0);
            float k = (float) (1 - 0.5 * Math.min(1, d / 4));
            Combat.hurt(caster, e, 9 * k);
            Stun.apply(e, 25);
            Combat.pushFrom(e, c0.subtract(fwd.scale(0.5)), 0.7 * k, 0.55 * k + 0.2);
        }
        // A new X: the stones of the last one crumble (one set on the ground at a time).
        for (TodoStone old : stones(level, caster, 96)) {
            old.crumble();
        }
        float[] sizes = new float[27];
        for (int i = 0; i < 27; i++) {
            sizes[i] = i < 4 ? new float[] {1.45f, 1.25f, 1.6f, 1.3f}[i] : i < 11 ? 0.7f + 0.3f * SubCommon.RANDOM.nextFloat()
                    : 0.28f + 0.24f * SubCommon.RANDOM.nextFloat();
        }
        List<Vec3> lands = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            lands.add(landing(level, c0, fwd, right, sizes, lands, i));
        }
        for (int i = 0; i < sizes.length; i++) {
            Vec3 from = c0.add((SubCommon.RANDOM.nextDouble() - 0.5) * 1.6, 0.15, (SubCommon.RANDOM.nextDouble() - 0.5) * 1.6);
            TodoStone s = TodoStone.create(level, caster, from, sizes[i], stoneState(earth, sizes[i]));
            double t = (0.75 + 0.35 * SubCommon.RANDOM.nextDouble()) * 20;
            Vec3 d = lands.get(i).subtract(from);
            double vy = (d.y + 0.6 + TodoStone.GRAVITY * t * (t + 1) * 0.5) / t;
            level.addFreshEntity(s);
            s.launch(new Vec3(d.x / t, vy, d.z / t), false);
        }
    }

    // Where stone i lands: round the crater, never behind him nor on him, not on the way of the big one in front, and
    // never on top of another stone.
    private static Vec3 landing(ServerLevel level, Vec3 c0, Vec3 fwd, Vec3 right, float[] sizes, List<Vec3> lands, int i) {
        Vec3 me = c0.subtract(fwd.scale(2));
        if (i == FRONT) {
            return SubCommon.ground(level, me.add(fwd.scale(5.6)).add(right.scale(0.25)));
        }
        Vec3 best = null;
        for (int k = 0; k < 60; k++) {
            double a = -Math.PI / 2 + (SubCommon.RANDOM.nextDouble() - 0.5) * Math.PI * 1.5;
            boolean big = sizes[i] > 0.6f;
            double r = (big ? 3.2 : 2.6) + SubCommon.RANDOM.nextDouble() * (big ? 2.6 : 4.4);
            double side = Math.cos(a) * r * 1.15;
            double ahead = 2 - Math.sin(a) * r;
            if (ahead < 0.6 && Math.abs(side) < 1.6) {
                continue;
            }
            if (Math.abs(side) < 1.2 && ahead > 4 && ahead < 7.5) {
                continue;
            }
            Vec3 p = me.add(right.scale(side)).add(fwd.scale(ahead));
            best = p;
            boolean apart = true;
            for (int j = 0; j < lands.size(); j++) {
                if (SubCommon.flat(lands.get(j).subtract(p)).length() <= (sizes[i] + sizes[j]) * 0.62) {
                    apart = false;
                    break;
                }
            }
            if (apart) {
                break;
            }
        }
        if (best == null) {
            best = me.add(fwd.scale(4)).add(right.scale(i % 2 == 0 ? 3 : -3));
        }
        return SubCommon.ground(level, best);
    }

    // The earth torn out: the block under the punch, as loose earth (grass comes out as dirt), or dirt.
    private static BlockState earth(ServerLevel level, Vec3 c0) {
        BlockPos under = BlockPos.containing(c0).below();
        BlockState state = level.getBlockState(under);
        if (state.isAir() || BlockRules.keeps(state) || state.getDestroySpeed(level, under) < 0 || !state.getFluidState().isEmpty()
                || !state.isCollisionShapeFullBlock(level, under)) {
            return Blocks.DIRT.defaultBlockState();
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.FARMLAND)) {
            return Blocks.DIRT.defaultBlockState();
        }
        return state.getBlock().defaultBlockState();
    }

    // The pebbles come out a little rougher than the big chunks.
    private static BlockState stoneState(BlockState earth, float size) {
        if (size < 0.6f && SubCommon.RANDOM.nextFloat() < 0.35f) {
            if (earth.is(Blocks.DIRT)) {
                return Blocks.COARSE_DIRT.defaultBlockState();
            }
            if (earth.is(Blocks.STONE) || earth.is(Blocks.DEEPSLATE)) {
                return Blocks.COBBLESTONE.defaultBlockState();
            }
        }
        return earth;
    }

    // ---------- V: Black Flash ----------

    private static final class Flash {
        final TodoStone stone;
        final SubCommon.Guard guard;
        boolean swapped;

        Flash(TodoStone stone, SubCommon.Guard guard) {
            this.stone = stone;
            this.guard = guard;
        }
    }

    private static boolean blackFlash(LivingEntity caster, ServerLevel level) {
        if (FLASH.containsKey(caster.getUUID())) {
            return false;
        }
        TodoStone stone = nearStone(level, caster);
        if (stone == null) {
            SubCommon.warn(caster, "todo.sem_pedra");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int grab = 4;
        int toss = 6;
        SubCommon.pose(caster, "jj.block_rip");
        SubCommon.busy(caster, toss + 4);
        Freeze.holdLookFree(caster, toss + 2);
        Flash flash = new Flash(stone, guard);
        FLASH.put(caster.getUUID(), flash);
        boolean mob = !(caster instanceof Player);
        Tasks.add(age -> {
            if (FLASH.get(caster.getUUID()) != flash) {
                return true;
            }
            if (guard.broken() || !stone.isAlive()) {
                FLASH.remove(caster.getUUID());
                Freeze.free(caster);
                return true;
            }
            if (age == grab) {
                SubCommon.pose(caster, "jj.block_throw");
            }
            if (age == toss) {
                // Straight up over his head: the stone comes down where he stands.
                double vy = Math.sqrt(2 * TodoStone.GRAVITY * THROW_HEIGHT);
                double flight = 2 * vy / TodoStone.GRAVITY;
                Vec3 side = SubCommon.flat(caster.position().subtract(stone.position())).scale(1 / flight);
                stone.launch(new Vec3(side.x, vy, side.z), false);
                DbCommon.screen(level, stone.position(), 38);
                SubCommon.sound(level, stone.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
                SubCommon.sound(level, stone.position(), SoundEvents.STONE_BREAK, 1.2f, 0.7f);
                return false;
            }
            if (age < toss + 2 || flash.swapped) {
                return flash.swapped;
            }
            if (stone.resting() || stone.onGround()) {
                // Too late: it smashes and the ult ends with no Black Flash.
                FLASH.remove(caster.getUUID());
                stone.crumble();
                SubCommon.warn(caster, "todo.bf_perdeu");
                return true;
            }
            double vy = stone.getDeltaMovement().y;
            if (mob && vy < 0.1 && JjCommon.target(caster, 30) != null) {
                press(caster, level, flash);
                return true;
            }
            if (caster instanceof Player && age % 3 == 0) {
                double left = (vy + Math.sqrt(Math.max(0, vy * vy + 2 * TodoStone.GRAVITY * Math.max(0, stone.getY() - caster.getY())))) / TodoStone.GRAVITY;
                SubCommon.warn(caster, "todo.bf_espera", String.format(java.util.Locale.ROOT, "%.1f", left / 20));
            }
            return false;
        });
        return true;
    }

    // V again (players) while the stone is up: the swap. True when the press was taken.
    public static boolean flashPress(ServerPlayer player) {
        Flash flash = FLASH.get(player.getUUID());
        if (flash == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!flash.swapped && flash.stone.isAlive() && !flash.stone.resting()) {
            press(player, level, flash);
        }
        return true;
    }

    // The 2nd V: the target and the stone swap, the target drops onto him and he goes up to meet it.
    private static void press(LivingEntity caster, ServerLevel level, Flash flash) {
        LivingEntity t = JjCommon.target(caster, 30);
        if (t == null) {
            SubCommon.warn(caster, "todo.no_target");
            return;
        }
        flash.swapped = true;
        TodoStone stone = flash.stone;
        SubCommon.Guard guard = flash.guard;
        clap(caster, level, true);
        Vec3 a = t.position();
        Vec3 b = stone.position();
        Vec3[] tVel = {stone.getDeltaMovement()};
        SubNet.send(level, SubNet.AFTERIMAGE, t.getId(), a, b.subtract(a), t.getBbHeight(), 0, 0);
        SubNet.send(level, SubNet.SWAP, 0, a, Vec3.ZERO, 0, 0, 0);
        SubNet.send(level, SubNet.SWAP, 0, b, Vec3.ZERO, 0, 0, 0);
        stone.placeAt(a);
        Vec3[] tAt = {b};
        NrCommon.teleport(t, b, t.getYRot());
        int maxFall = 70;
        int slow = 12;
        Vec3[] cAt = {caster.position()};
        Vec3[] cVel = {Vec3.ZERO};
        int[] phase = {0};
        int[] mark = {0};
        Vec3[] hitP = {null};
        Freeze.hold(t, maxFall + slow + 4, age -> tAt[0]);
        Freeze.holdLookFree(caster, maxFall + slow + 4, age -> cAt[0]);
        SubCommon.busy(caster, maxFall + slow + 6);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, maxFall + slow + 10, 3, false, false));
        Tasks.add(age -> {
            boolean gone = guard.broken() || !t.isAlive();
            if (gone || age > maxFall + slow + 2) {
                FLASH.remove(caster.getUUID());
                Freeze.free(t);
                Freeze.free(caster);
                t.fallDistance = 0;
                return true;
            }
            t.fallDistance = 0;
            caster.fallDistance = 0;
            if (phase[0] == 0) {
                // The target drops, keeping the stone's flight.
                tVel[0] = tVel[0].add(0, -TodoStone.GRAVITY, 0);
                tAt[0] = tAt[0].add(tVel[0]);
                double h = tAt[0].y - cAt[0].y;
                if (age == 4) {
                    SubCommon.pose(caster, "jj.dash_charge");
                }
                if (h < 4.2 && tVel[0].y < 0) {
                    // He jumps to meet it.
                    Vec3 to = SubCommon.flat(tAt[0].subtract(cAt[0]));
                    cVel[0] = new Vec3(to.x * 0.08, 0.55, to.z * 0.08);
                    SubCommon.pose(caster, "jj.uppercut");
                    SubCommon.sound(level, cAt[0], SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.7f);
                    phase[0] = 1;
                    mark[0] = age;
                }
                if (h < 0.5) {
                    phase[0] = 1;
                    mark[0] = age;
                }
                return false;
            }
            if (phase[0] == 1) {
                cVel[0] = cVel[0].add(0, -TodoStone.GRAVITY, 0);
                cAt[0] = cAt[0].add(cVel[0]);
                tVel[0] = tVel[0].add(0, -TodoStone.GRAVITY, 0);
                tAt[0] = tAt[0].add(tVel[0]);
                Vec3 fist = cAt[0].add(0, caster.getBbHeight() + 0.8, 0);
                boolean touch = fist.distanceTo(tAt[0].add(0, 0.8, 0)) < 1.6 || tAt[0].y - cAt[0].y < caster.getBbHeight() + 0.4
                        || age - mark[0] > 10;
                if (!touch) {
                    return false;
                }
                // The contact: everything slows and the screen loses its color.
                hitP[0] = tAt[0].add(0, 0.6, 0);
                tAt[0] = new Vec3(tAt[0].x, Math.max(tAt[0].y, cAt[0].y + caster.getBbHeight() + 0.3), tAt[0].z);
                hitP[0] = tAt[0].add(0, 0.6, 0);
                phase[0] = 2;
                mark[0] = age;
                SubNet.send(level, Jj2Net.TODO_GRAY, caster.getId(), hitP[0], new Vec3(0, 1, 0), slow, 0, 0);
                Defense.breakGuard(t, caster);
                Combat.hurt(caster, t, 10);
                SubCommon.sound(level, hitP[0], SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.5f);
                SubCommon.sound(level, hitP[0], SoundEvents.WARDEN_HEARTBEAT, 1.6f, 0.7f);
                return false;
            }
            int k = age - mark[0];
            // Slow motion: both drift up a hair.
            cAt[0] = cAt[0].add(0, 0.0024, 0);
            tAt[0] = tAt[0].add(0, 0.0024, 0);
            if (k < slow) {
                return false;
            }
            Vec3 up = new Vec3(0, 1, 0);
            Itadori.sendFlash(level, new ModPayloads.BlackFlash(21, caster.getId(), hitP[0], up, 1f, 6));
            Itadori.sendFlash(level, new ModPayloads.BlackFlash(22, caster.getId(), hitP[0], up, 1f, 0));
            DbCommon.screen(level, hitP[0], 26);
            Vec3 g = SubCommon.ground(level, cAt[0]);
            SubNet.send(level, SubNet.CRACKS, 0, g, Vec3.ZERO, 3f, 10, 1);
            JjCommon.crater(level, g, 2.6, 1.0, 16);
            FLASH.remove(caster.getUUID());
            Freeze.free(t);
            Freeze.free(caster);
            Combat.hurtMagic(caster, t, 18);
            Stun.apply(t, 40);
            t.setDeltaMovement(0, 1.5, 0);
            t.hurtMarked = true;
            caster.setDeltaMovement(0, 0.1, 0);
            caster.hurtMarked = true;
            SubCommon.sound(level, hitP[0], SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 1.5f);
            SubCommon.sound(level, hitP[0], SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.6f);
            return true;
        });
    }

    // ---------- B: Bloco Bala ----------

    private static boolean blocoBala(LivingEntity caster, ServerLevel level) {
        TodoStone stone = nearStone(level, caster);
        if (stone == null) {
            SubCommon.warn(caster, "todo.sem_pedra");
            return false;
        }
        LivingEntity t = JjCommon.target(caster, 30);
        if (t == null) {
            SubCommon.warn(caster, "todo.no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int kick = 3;
        int max = 50;
        double speed = 2.0;
        Vec3 kickedFrom = stone.position();
        Vec3[] cAt = {caster.position()};
        Vec3[] cVel = {Vec3.ZERO};
        int[] swapAt = {-1};
        SubCommon.pose(caster, "jj.manji_kick");
        SubCommon.busy(caster, 14);
        Freeze.holdLookFree(caster, kick + 2);
        Tasks.add(age -> {
            if (guard.broken() || !t.isAlive() || age > max) {
                Freeze.free(caster);
                return true;
            }
            if (age < kick) {
                return false;
            }
            if (age == kick) {
                if (!stone.isAlive()) {
                    return true;
                }
                Vec3 dir = Combat.center(t).subtract(stone.center()).normalize();
                stone.launch(dir.scale(speed).add(0, 0.11, 0), true);
                DbCommon.screen(level, stone.position(), 38);
                SubCommon.sound(level, stone.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 0.6f);
                SubCommon.sound(level, stone.position(), SoundEvents.STONE_BREAK, 1.2f, 0.8f);
                return false;
            }
            if (swapAt[0] < 0) {
                if (!stone.isAlive()) {
                    return true;
                }
                double d = stone.center().distanceTo(Combat.center(t));
                boolean stopped = stone.resting() || stone.horizontalCollision || stone.position().distanceTo(kickedFrom) > 32;
                if (d > 6 && !stopped) {
                    return false;
                }
                // The clap in the air: he takes the stone's place and keeps its speed.
                clap(caster, level, true);
                Vec3 a = caster.position();
                Vec3 b = stone.position();
                cVel[0] = stopped ? Combat.center(t).subtract(b).normalize().scale(speed) : stone.getDeltaMovement();
                SubNet.send(level, SubNet.AFTERIMAGE, caster.getId(), a, b.subtract(a), caster.getBbHeight(), 0, 0);
                SubNet.send(level, SubNet.SWAP, 0, a, Vec3.ZERO, 0, 0, 0);
                SubNet.send(level, SubNet.SWAP, 0, b, Vec3.ZERO, 0, 0, 0);
                stone.placeAt(a.add(0, 0.6, 0));
                cAt[0] = b;
                NrCommon.teleport(caster, b, NrCommon.yawTo(b, t.position()));
                Freeze.holdLookFree(caster, 24, x -> cAt[0]);
                SubCommon.busy(caster, 26);
                swapAt[0] = age;
                return false;
            }
            // Flying on with the kick, a little drawn to the target; the punch on arrival.
            Vec3 to = Combat.center(t).subtract(cAt[0].add(0, caster.getBbHeight() * 0.5, 0));
            Vec3 v = cVel[0].add(0, -0.0075, 0);
            v = v.lerp(to.normalize().scale(v.length()), 0.25);
            cVel[0] = v;
            Vec3 next = cAt[0].add(v);
            Vec3 floor = SubCommon.ground(level, next);
            cAt[0] = next.y < floor.y ? new Vec3(next.x, floor.y, next.z) : next;
            if (age - swapAt[0] == 2) {
                SubCommon.pose(caster, "jj.div_punch");
            }
            double reach = 1.3 + t.getBbWidth() * 0.5;
            if (to.length() > reach + 0.6 && age - swapAt[0] < 22) {
                return false;
            }
            Freeze.free(caster);
            if (to.length() <= reach + 1.2) {
                Vec3 dir = SubCommon.flat(to).normalize();
                Vec3 c = Combat.center(t);
                Defense.breakGuard(t, caster);
                Combat.hurt(caster, t, 13);
                Stun.apply(t, 25);
                t.setDeltaMovement(dir.scale(1.0).add(0, 0.3, 0));
                t.hurtMarked = true;
                DbCommon.hit(level, c, 1.1f, com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.WHITE);
                DbCommon.screen(level, c, 39);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.8f, 0.6f);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.3f, 0.8f);
            }
            caster.setDeltaMovement(SubCommon.flat(cVel[0]).scale(0.1).add(0, 0.1, 0));
            caster.hurtMarked = true;
            caster.fallDistance = 0;
            return true;
        });
        return true;
    }

    public static void clear() {
        FLASH.clear();
    }

    // ---------- C: Fake Clap ----------

    private static boolean fakeClap(LivingEntity caster, ServerLevel level) {
        clap(caster, level, false);
        SubCommon.busy(caster, 6);
        int fooled = 0;
        for (LivingEntity e : Combat.inRadius(level, caster.position(), 10, caster)) {
            SubCommon.setState(e, JjCommon.OPEN, 60);
            if (e instanceof Mob) {
                int hold = JjCommon.control(caster, e, 20);
                Freeze.hold(e, hold);
                Freeze.face(e, e.getYRot() + 180, 0);
                fooled++;
            } else {
                // A player flinches at the clap too (test with friends, 2026-11-25: on players it did nothing you could
                // see): the shortest real stun (Stark's floor, 25 ticks).
                Stun.apply(e, 25);
                fooled++;
            }
        }
        if (fooled == 0 && Combat.inRadius(level, caster.position(), 10, caster).isEmpty()) {
            SubCommon.warn(caster, "todo.nobody");
            return false;
        }
        return true;
    }

    // ---------- Shift+V: Black Flash Garantido ----------

    private static boolean guaranteed(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int windup = 8;
        double speed = 2.2;
        double maxDist = 50;
        int fit = 14;
        double[] went = {0};
        Vec3[] dir = {SubCommon.flat(caster.getLookAngle()).normalize()};
        LivingEntity[] hit = {null};
        Vec3[] contact = {null};
        int[] hitAt = {-1};
        SubCommon.pose(caster, "jj.dash_charge");
        SubCommon.busy(caster, windup + 40);
        Freeze.holdLookFree(caster, windup);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, windup + 30, 3, false, false));
        SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.9f, 1.4f);
        Tasks.add(age -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.TODO_FLAME, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                return true;
            }
            if (age < windup) {
                SubNet.send(level, SubNet.TODO_FLAME, caster.getId(), caster.position(), Vec3.ZERO, Math.min(1, age / 5f), 0, 0);
                return false;
            }
            if (hit[0] == null) {
                if (age == windup) {
                    SubCommon.pose(caster, "jj.dash_run");
                    SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
                    SubNet.send(level, SubNet.CRACKS, 0, SubCommon.ground(level, caster.position()), Vec3.ZERO, 1.6f, 6, 1);
                }
                // Steered by the aim, a little up or down too.
                Vec3 look = caster.getLookAngle();
                Vec3 want = new Vec3(look.x, Math.max(-0.35, Math.min(0.35, look.y)), look.z).normalize();
                dir[0] = dir[0].lerp(want, 0.35).normalize();
                caster.setDeltaMovement(dir[0].scale(speed));
                caster.hurtMarked = true;
                caster.fallDistance = 0;
                went[0] += speed;
                SubNet.send(level, SubNet.TODO_FLAME, caster.getId(), caster.position(), dir[0], 1, 1, 0);
                SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), caster.position(), dir[0], 1, 0, 0);
                for (LivingEntity e : Combat.inRadius(level, Combat.center(caster).add(dir[0].scale(0.8)), 1.9, caster)) {
                    hit[0] = e;
                    break;
                }
                if (hit[0] == null) {
                    if (went[0] >= maxDist || caster.horizontalCollision) {
                        caster.setDeltaMovement(dir[0].scale(0.2));
                        caster.hurtMarked = true;
                        SubNet.send(level, SubNet.TODO_FLAME, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                        SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                        SubCommon.warn(caster, "todo.dash_miss");
                        return true;
                    }
                    return false;
                }
                // He touched someone: he stops dead and the punch is a Black Flash.
                LivingEntity t = hit[0];
                hitAt[0] = age;
                caster.setDeltaMovement(Vec3.ZERO);
                caster.hurtMarked = true;
                SubNet.send(level, SubNet.DASH_TRAIL, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                Vec3 flat = SubCommon.flat(t.position().subtract(caster.position()));
                dir[0] = flat.lengthSqr() < 1.0E-4 ? SubCommon.flat(dir[0]).normalize() : flat.normalize();
                contact[0] = JjCommon.contact(caster, t);
                Vec3 holdC = caster.position();
                Vec3 holdT = t.position();
                SubCommon.pose(caster, "jj.bf_punch");
                Freeze.hold(caster, fit, a -> holdC);
                Freeze.hold(t, fit + 2, a -> holdT);
                Itadori.sendFlash(level, new ModPayloads.BlackFlash(21, caster.getId(), contact[0], dir[0], 1f, fit));
                DbCommon.screen(level, contact[0], 26);
                Defense.breakGuard(t, caster);
                Combat.hurt(caster, t, 12);
                SubCommon.sound(level, contact[0], SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9f, 1.5f);
                SubCommon.sound(level, contact[0], SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.6f);
                return false;
            }
            LivingEntity t = hit[0];
            int a = age - hitAt[0];
            if (a < fit) {
                SubNet.send(level, SubNet.TODO_FLAME, caster.getId(), caster.position(), dir[0], 1 - a / (float) fit, 0, 0);
            }
            if (a == 5 || a == 10) {
                Combat.hurtMagic(caster, t, 3);
                JjCommon.crater(level, SubCommon.ground(level, contact[0]), 1.4 + a * 0.1, 0.9, 8);
                SubCommon.sound(level, contact[0], SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 1.4f);
            }
            if (a >= fit || !t.isAlive()) {
                Freeze.free(t);
                Freeze.free(caster);
                SubNet.send(level, SubNet.TODO_FLAME, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                if (!t.isAlive()) {
                    return true;
                }
                Itadori.sendFlash(level, new ModPayloads.BlackFlash(22, caster.getId(), contact[0], dir[0], 1f, 0));
                Combat.hurtMagic(caster, t, 14);
                Stun.apply(t, 50);
                t.setDeltaMovement(dir[0].scale(2.6).add(0, 0.6, 0));
                t.hurtMarked = true;
                JjCommon.crater(level, SubCommon.ground(level, contact[0]), 4.2, 2.4, 34);
                SubCommon.sound(level, contact[0], SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.55f);
                return true;
            }
            return false;
        });
        return true;
    }
}
