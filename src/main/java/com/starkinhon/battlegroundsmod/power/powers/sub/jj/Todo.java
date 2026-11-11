package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.Arrays;
import java.util.List;

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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Todo (SubAlma of Jujutsu, tier 6), kit remade with Stark (2026-11-11, preview forja/todo-kit v4): Boogie Woogie.
// Passive: whoever he swaps is lost for a moment: the next hit on them is a critical.
// Z Boogie Woogie: tap = a clap and he swaps with whoever he aims at (25 blocks). Hold = marks whoever he aimed at (A);
//   let go aiming at someone else (B) and A and B swap. Let go at nobody and he swaps with A.
// X Block + Clap: he rips a block out of the ground and throws it over the target; a clap swaps him with the block, so he
//   is in the air over them, and he comes down with a hammer punch.
// C Fake Clap: he claps without the technique. Mobs fall for it and turn round for a second; everyone near is left open.
// V Turbilhao de Palmas: a stomp kicks 11 blocks up round the target, they hang in the air; 12 claps in 2 s, each one swaps
//   the target with a block in the air and he is there hitting (right, left, kick); the last puts the target on the
//   ground in front of him and ends in a Black Flash.
// B Vibraslap: the metal hand of Shinjuku. Hold it: for up to 6 s he swaps with no clap, no sound, no warning, every few
//   ticks, each swap with a blow from a new side; now and then he swaps the two enemies with each other instead.
// Shift+V Black Flash Garantido (full ult bar): he plants his foot, cursed energy bursts from his shoulders, and he dashes
//   up to 50 blocks steered by his aim; nothing stops him. Touching someone, he stops and the punch is always a Black Flash.
public final class Todo {
    private static final int VIBRA_TICKS = 120;
    private static final int VIBRA_STEP = 3;

    private Todo() {
    }

    public static Power create() {
        return new Power("todo", 0x6B4A2E, 1.1f, List.of(
                new Ability(60, 0, Todo::boogie),
                new Ability(320, 0, Todo::blockClap),
                new Ability(200, 0, Todo::fakeClap),
                new Ability(900, 0, Todo::turbilhao),
                new Ability(700, 0, Todo::vibraslap)),
                Arrays.asList(null, null, null, new Ability(900, 0, Todo::guaranteed), null));
    }

    // Swaps two bodies: each goes where the other stood, keeping where it looks. The one swapped is left open.
    static void swap(ServerLevel level, LivingEntity a, LivingEntity b) {
        Vec3 pa = a.position();
        Vec3 pb = b.position();
        NrCommon.teleport(a, pb, a.getYRot());
        NrCommon.teleport(b, pa, b.getYRot());
        SubNet.send(level, SubNet.SWAP, 0, pa, Vec3.ZERO, 0, 0, 0);
        SubNet.send(level, SubNet.SWAP, 0, pb, Vec3.ZERO, 0, 0, 0);
        lost(a);
        lost(b);
    }

    // The passive: a swapped enemy is lost for a moment and the next hit on them is a critical.
    private static void lost(LivingEntity e) {
        if (!"todo".equals(JjCommon.power(e))) {
            SubCommon.setState(e, JjCommon.OPEN, 40);
        }
    }

    private static void clap(LivingEntity caster, ServerLevel level, boolean real) {
        SubCommon.pose(caster, "jj.clap");
        SubNet.send(level, SubNet.CLAP, caster.getId(), DbCommon.hands(caster), Vec3.ZERO, real ? 1 : 0, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 1.8f);
        SubCommon.sound(level, caster.position(), SoundEvents.SLIME_SQUISH, 1.2f, 1.6f);
    }

    // ---------- Z: Boogie Woogie ----------

    private static boolean boogie(LivingEntity caster, ServerLevel level) {
        LivingEntity a = JjCommon.target(caster, 25);
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
                }
                return false;
            }
            SubNet.send(level, SubNet.GROUND_MARK, a.getId(), a.position(), Vec3.ZERO, 0, 0, 1);
            if (!a.isAlive()) {
                return true;
            }
            clap(caster, level, true);
            SubCommon.busy(caster, 4);
            LivingEntity b = age >= tap ? JjCommon.target(caster, 25) : null;
            if (b != null && b != a) {
                swap(level, a, b);
                DbCommon.screen(level, Combat.center(a), 38);
            } else {
                swap(level, caster, a);
                DbCommon.screen(level, Combat.center(caster), 38);
                // A mob that loses sight of him for a moment.
                if (a instanceof Mob mob && mob.getTarget() == caster) {
                    Stun.apply(a, 6);
                }
            }
            return true;
        });
        return true;
    }

    // ---------- X: Block + Clap ----------

    private static boolean blockClap(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 20);
        if (t == null) {
            SubCommon.warn(caster, "todo.no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int rip = 8;
        int swapAt = 30;
        int slam = 42;
        SubCommon.pose(caster, "jj.block_rip");
        SubCommon.busy(caster, slam + 6);
        Freeze.holdLookFree(caster, rip + 4);
        FallingBlockEntity[] block = {null};
        Vec3[] at = {null};
        Tasks.add(age -> {
            if (guard.broken() || !t.isAlive()) {
                if (block[0] != null) {
                    block[0].discard();
                }
                Freeze.free(caster);
                return true;
            }
            if (age == rip) {
                BlockState state = ripState(level, caster);
                BlockPos air = BlockPos.containing(caster.getEyePosition().add(SubCommon.flat(caster.getLookAngle()).scale(0.8)).add(0, 0.6, 0));
                if (!level.getBlockState(air).isAir()) {
                    air = BlockPos.containing(caster.position().add(0, 2.4, 0));
                }
                if (level.getBlockState(air).isAir()) {
                    FallingBlockEntity b = FallingBlockEntity.fall(level, air, state);
                    b.dropItem = false;
                    b.time = 1;
                    Vec3 over = t.position().add(0, 4.5, 0);
                    Vec3 d = over.subtract(b.position());
                    double flight = 16;
                    b.setDeltaMovement(d.x / flight, d.y / flight + 0.04 * flight * 0.5, d.z / flight);
                    b.hurtMarked = true;
                    block[0] = b;
                }
                SubCommon.pose(caster, "jj.block_throw");
                SubCommon.sound(level, caster.position(), SoundEvents.STONE_BREAK, 1.4f, 0.6f);
                SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
                return false;
            }
            if (age == swapAt) {
                clap(caster, level, true);
                Vec3 to = block[0] != null && block[0].isAlive() ? block[0].position() : t.position().add(0, 4.5, 0);
                if (block[0] != null) {
                    Vec3 old = caster.position();
                    block[0].setPos(old.x, old.y + 0.5, old.z);
                    block[0].setDeltaMovement(Vec3.ZERO);
                    SubNet.send(level, SubNet.SWAP, 0, old, Vec3.ZERO, 0, 0, 0);
                    Tasks.later(10, block[0]::discard);
                }
                SubNet.send(level, SubNet.SWAP, 0, to, Vec3.ZERO, 0, 0, 0);
                NrCommon.teleport(caster, to, NrCommon.yawTo(to, t.position()));
                at[0] = to;
                JjCommon.carry(caster, slam - swapAt, at);
                DbCommon.screen(level, to, 38);
                return false;
            }
            if (age > swapAt && age <= slam) {
                if (age == slam - 5) {
                    SubCommon.pose(caster, "jj.hammer_punch");
                }
                float k = SubCommon.smooth((age - swapAt) / (float) (slam - swapAt));
                Vec3 goal = t.position().add(0, t.getBbHeight() + 0.2, 0);
                at[0] = at[0].lerp(goal, 0.15 + 0.6 * k);
                if (age < slam) {
                    return false;
                }
                Freeze.free(caster);
                Vec3 c = Combat.center(t);
                Defense.breakGuard(t, caster);
                Combat.hurt(caster, t, 17);
                Stun.apply(t, 25);
                t.setDeltaMovement(0, -1.2, 0);
                t.hurtMarked = true;
                DbCommon.screen(level, c, 39);
                Tasks.later(2, () -> DbCommon.screen(level, c, 40));
                Vec3 g = SubCommon.ground(level, t.position());
                SubNet.send(level, SubNet.CRACKS, 0, g, Vec3.ZERO, 2.4f, 8, 1);
                JjCommon.crater(level, g, 2, 1.1, 10);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.8f, 0.5f);
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.9f);
                return true;
            }
            return false;
        });
        return true;
    }

    // The block he rips up: the ground under his feet (taken out when blocks can break), or stone.
    private static BlockState ripState(ServerLevel level, LivingEntity caster) {
        BlockPos under = BlockPos.containing(caster.position().add(SubCommon.flat(caster.getLookAngle()).scale(1.2))).below();
        BlockState state = level.getBlockState(under);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, under) < 0 || !state.getFluidState().isEmpty()) {
            return Blocks.STONE.defaultBlockState();
        }
        if (BlockRules.canBreak(level)) {
            level.destroyBlock(under, false);
        }
        return state;
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
            }
        }
        if (fooled == 0 && Combat.inRadius(level, caster.position(), 10, caster).isEmpty()) {
            SubCommon.warn(caster, "todo.nobody");
            return false;
        }
        return true;
    }

    // ---------- V: Turbilhao de Palmas ----------

    private static boolean turbilhao(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 20);
        if (t == null) {
            SubCommon.warn(caster, "todo.no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        int stomp = 4;
        int rise = 12;
        int clapGap = 3;
        int claps = 12;
        int stones = claps - 1;
        int bfAt = rise + 2 + clapGap * (claps - 1);
        int fit = 12;
        int end = bfAt + fit;
        Vec3 home = caster.position();
        Vec3 center = t.position();
        FallingBlockEntity[] rocks = new FallingBlockEntity[stones];
        Vec3[] spots = new Vec3[stones];
        for (int i = 0; i < stones; i++) {
            double a = i * 2.39996 + 0.4;
            double r = 2.2 + 1.9 * SubCommon.RANDOM.nextDouble();
            spots[i] = center.add(Math.cos(a) * r, 2.2 + 4.6 * SubCommon.RANDOM.nextDouble(), Math.sin(a) * r);
        }
        Vec3[] tAt = {center};
        Vec3[] cAt = {home};
        SubCommon.pose(caster, "jj.slam_fist");
        SubCommon.busy(caster, end + 4);
        Freeze.holdLookFree(caster, rise + 2);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, end, 2, false, false));
        Tasks.add(age -> {
            boolean gone = guard.broken() || !t.isAlive();
            if (gone || age > end) {
                for (FallingBlockEntity r : rocks) {
                    if (r != null && r.isAlive()) {
                        r.discard();
                    }
                }
                Freeze.free(caster);
                Freeze.free(t);
                return true;
            }
            if (age == stomp) {
                SubCommon.sound(level, caster.position(), SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.3f);
                SubNet.send(level, SubNet.CRACKS, 0, SubCommon.ground(level, center), Vec3.ZERO, 3f, 8, 1);
                DbCommon.screen(level, center, 39);
                for (int i = 0; i < stones; i++) {
                    Vec3 from = center.add(spots[i].subtract(center).multiply(0.35, 0, 0.35));
                    BlockPos air = BlockPos.containing(SubCommon.ground(level, from).add(0, 0.6, 0));
                    if (!level.getBlockState(air).isAir()) {
                        continue;
                    }
                    FallingBlockEntity b = FallingBlockEntity.fall(level, air, groundState(level, air.below()));
                    b.dropItem = false;
                    b.time = 1;
                    b.setNoGravity(true);
                    Vec3 d = spots[i].subtract(b.position());
                    double flight = rise - stomp - i * 0.3;
                    b.setDeltaMovement(d.scale(1 / flight));
                    b.hurtMarked = true;
                    rocks[i] = b;
                }
                return false;
            }
            if (age > stomp && age < rise) {
                return false;
            }
            if (age == rise) {
                for (int i = 0; i < stones; i++) {
                    if (rocks[i] != null && rocks[i].isAlive()) {
                        rocks[i].setPos(spots[i]);
                        rocks[i].setDeltaMovement(Vec3.ZERO);
                        rocks[i].hurtMarked = true;
                    }
                }
                Freeze.hold(t, end - rise, a -> tAt[0]);
                Freeze.hold(caster, end - rise, a -> cAt[0]);
                return false;
            }
            int k = age - rise - 2;
            if (k >= 0 && k % clapGap == 0 && k / clapGap < claps) {
                int n = k / clapGap;
                clap(caster, level, true);
                if (n < stones) {
                    Vec3 from = tAt[0];
                    Vec3 to = rocks[n] != null && rocks[n].isAlive() ? rocks[n].position() : spots[n];
                    if (rocks[n] != null && rocks[n].isAlive()) {
                        rocks[n].setPos(from.add(0, 1, 0));
                        rocks[n].setNoGravity(false);
                        FallingBlockEntity r = rocks[n];
                        Tasks.later(8, r::discard);
                    }
                    SubNet.send(level, SubNet.SWAP, 0, from, Vec3.ZERO, 0, 0, 0);
                    SubNet.send(level, SubNet.SWAP, 0, to, Vec3.ZERO, 0, 0, 0);
                    tAt[0] = to.add(0, -1, 0);
                    NrCommon.teleport(t, tAt[0], t.getYRot());
                    double ang = n * 2.1;
                    cAt[0] = tAt[0].add(Math.cos(ang) * 1.2, 0, Math.sin(ang) * 1.2);
                    NrCommon.teleport(caster, cAt[0], NrCommon.yawTo(cAt[0], tAt[0]));
                    SubCommon.pose(caster, n % 3 == 2 ? "jj.manji_kick" : "jj.div_punch");
                    Combat.hurt(caster, t, 2.5f);
                    Stun.apply(t, 8);
                    DbCommon.hit(level, Combat.center(t), 0.7f, com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.WHITE);
                    SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1.1f + 0.05f * n);
                    return false;
                }
                // The last clap: the target lands on the ground right in front of him, and the Black Flash.
                Vec3 fwd = SubCommon.flat(home.subtract(center)).normalize().scale(-1);
                if (fwd.lengthSqr() < 0.01) {
                    fwd = SubCommon.flat(caster.getLookAngle()).normalize();
                }
                SubNet.send(level, SubNet.SWAP, 0, tAt[0], Vec3.ZERO, 0, 0, 0);
                cAt[0] = SubCommon.ground(level, home);
                tAt[0] = SubCommon.ground(level, home.add(fwd.scale(1.3)));
                NrCommon.teleport(caster, cAt[0], NrCommon.yawTo(cAt[0], tAt[0]));
                NrCommon.teleport(t, tAt[0], t.getYRot());
                Vec3 contact = JjCommon.contact(caster, t);
                SubCommon.pose(caster, "jj.bf_punch");
                Itadori.sendFlash(level, new ModPayloads.BlackFlash(1, caster.getId(), contact, fwd, 1f, fit));
                SubNet.send(level, SubNet.BF_SPARKS, SubNet.newId(), contact, fwd, 40, 0, 0);
                DbCommon.screen(level, contact, 26);
                Defense.breakGuard(t, caster);
                Combat.hurt(caster, t, 10);
                SubCommon.sound(level, contact, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
                SubCommon.sound(level, contact, SoundEvents.PLAYER_ATTACK_CRIT, 1.6f, 0.6f);
                return false;
            }
            if (age == end) {
                Vec3 fwd = SubCommon.flat(tAt[0].subtract(cAt[0])).normalize();
                Vec3 contact = JjCommon.contact(caster, t);
                Freeze.free(t);
                Freeze.free(caster);
                Itadori.sendFlash(level, new ModPayloads.BlackFlash(2, caster.getId(), contact, fwd, 1f, 0));
                Combat.hurtMagic(caster, t, 14);
                Stun.apply(t, 50);
                t.setDeltaMovement(fwd.scale(2.4).add(0, 0.55, 0));
                t.hurtMarked = true;
                JjCommon.crater(level, SubCommon.ground(level, contact), 3.5, 2, 26);
                SubCommon.sound(level, contact, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
                return true;
            }
            return false;
        });
        return true;
    }

    // The ground under a spot as a block he can kick up (taken out when blocks can break), or stone.
    private static BlockState groundState(ServerLevel level, BlockPos under) {
        BlockState state = level.getBlockState(under);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, under) < 0 || !state.getFluidState().isEmpty()) {
            return Blocks.STONE.defaultBlockState();
        }
        if (BlockRules.canBreak(level)) {
            level.destroyBlock(under, false);
        }
        return state;
    }

    // ---------- B: Vibraslap ----------

    private static boolean vibraslap(LivingEntity caster, ServerLevel level) {
        if (Combat.inRadius(level, caster.position(), 20, caster).isEmpty()) {
            SubCommon.warn(caster, "todo.no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "jj.clap");
        SubCommon.sound(level, caster.position(), SoundEvents.CHAIN_HIT, 1.2f, 1.8f);
        SubCommon.sound(level, caster.position(), SoundEvents.ANVIL_LAND, 0.25f, 2f);
        int[] n = {0};
        Tasks.add(age -> {
            if (guard.broken() || age > VIBRA_TICKS || (age > 4 && !SubCommon.held(caster, age, 70))) {
                return true;
            }
            if (age < 4 || age % VIBRA_STEP != 0) {
                return false;
            }
            List<LivingEntity> near = Combat.inRadius(level, caster.position(), 20, caster);
            if (near.isEmpty()) {
                return true;
            }
            LivingEntity aimed = JjCommon.target(caster, 20);
            LivingEntity t = aimed != null ? aimed : near.get(0);
            int k = n[0]++;
            // Now and then the two enemies swap with each other instead.
            if (k % 5 == 2 && near.size() > 1) {
                LivingEntity o = near.get(0) == t ? near.get(1) : near.get(0);
                Vec3 pa = t.position();
                Vec3 pb = o.position();
                SubNet.send(level, SubNet.AFTERIMAGE, t.getId(), pa, pb.subtract(pa), t.getBbHeight(), 0, 0);
                SubNet.send(level, SubNet.AFTERIMAGE, o.getId(), pb, pa.subtract(pb), o.getBbHeight(), 0, 0);
                NrCommon.teleport(t, pb, t.getYRot());
                NrCommon.teleport(o, pa, o.getYRot());
                lost(t);
                lost(o);
            }
            // No clap, no sound: he is just there, on a new side, already hitting.
            double ang = k * 2.39 + SubCommon.RANDOM.nextDouble() * 1.2;
            Vec3 to = t.position().add(Math.cos(ang) * 1.1, 0, Math.sin(ang) * 1.1);
            if (SubCommon.solid(level, to.add(0, 0.5, 0))) {
                to = t.position().subtract(SubCommon.flat(t.getLookAngle()).normalize().scale(1.1));
            }
            Vec3 from = caster.position();
            SubNet.send(level, SubNet.AFTERIMAGE, caster.getId(), from, to.subtract(from), caster.getBbHeight(), 0, 0);
            NrCommon.teleport(caster, to, NrCommon.yawTo(to, t.position()));
            int move = SubCommon.RANDOM.nextInt(3);
            SubCommon.pose(caster, move == 2 ? "jj.manji_kick" : "jj.div_punch");
            Combat.hurt(caster, t, 3);
            Stun.apply(t, 6);
            t.setDeltaMovement(Vec3.ZERO);
            t.hurtMarked = true;
            DbCommon.hit(level, Combat.center(t), 0.6f, com.starkinhon.battlegroundsmod.power.powers.sub.db.Db.WHITE);
            SubCommon.sound(level, t.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.2f + 0.3f * SubCommon.RANDOM.nextFloat());
            return false;
        });
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
                Itadori.sendFlash(level, new ModPayloads.BlackFlash(1, caster.getId(), contact[0], dir[0], 1f, fit));
                SubNet.send(level, SubNet.BF_SPARKS, SubNet.newId(), contact[0], dir[0], 46, 0, 0);
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
                SubNet.send(level, SubNet.BF_SPARKS, SubNet.newId(), contact[0], dir[0], 14, 0, 0);
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
                Itadori.sendFlash(level, new ModPayloads.BlackFlash(2, caster.getId(), contact[0], dir[0], 1f, 0));
                SubNet.send(level, SubNet.BF_SPARKS, SubNet.newId(), contact[0], dir[0], 30, 0, 0);
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
