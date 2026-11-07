package com.starkinhon.battlegroundsmod.power.powers.sub.jj;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Todo (SubAlma of Jujutsu, tier 6), the Visualizador's Todo tab: Boogie Woogie.
// Z Boogie Woogie: a clap, and he swaps places with whoever he is looking at (up to 25 blocks).
// X Block + Clap: he rips a block out of the ground and throws it over the target; a clap swaps him with the block, so he
//   is in the air over them, and he comes down with a hammer punch.
// C Fake Clap: he claps without the technique. Mobs fall for it and turn round for a second; everyone near is left open:
//   the next hit on them is a critical.
public final class Todo {
    private Todo() {
    }

    public static Power create() {
        return new Power("todo", 0x6B4A2E, 1.1f, List.of(
                new Ability(60, 0, Todo::boogie),
                new Ability(320, 0, Todo::blockClap),
                new Ability(200, 0, Todo::fakeClap),
                new Ability(1000, 0, Todo::ultMissing)));
    }

    // Swaps two bodies: each goes where the other stood, keeping where it looks.
    static void swap(ServerLevel level, LivingEntity a, LivingEntity b) {
        Vec3 pa = a.position();
        Vec3 pb = b.position();
        NrCommon.teleport(a, pb, a.getYRot());
        NrCommon.teleport(b, pa, b.getYRot());
        SubNet.send(level, SubNet.SWAP, 0, pa, Vec3.ZERO, 0, 0, 0);
        SubNet.send(level, SubNet.SWAP, 0, pb, Vec3.ZERO, 0, 0, 0);
    }

    private static void clap(LivingEntity caster, ServerLevel level, boolean real) {
        SubCommon.pose(caster, "jj.clap");
        SubNet.send(level, SubNet.CLAP, caster.getId(), DbCommon.hands(caster), Vec3.ZERO, real ? 1 : 0, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6f, 1.8f);
        SubCommon.sound(level, caster.position(), SoundEvents.SLIME_SQUISH, 1.2f, 1.6f);
    }

    // ---------- Z: Boogie Woogie ----------

    private static boolean boogie(LivingEntity caster, ServerLevel level) {
        LivingEntity t = JjCommon.target(caster, 25);
        if (t == null) {
            SubCommon.warn(caster, "todo.no_target");
            return false;
        }
        clap(caster, level, true);
        SubCommon.busy(caster, 4);
        swap(level, caster, t);
        DbCommon.screen(level, Combat.center(caster), 38);
        // A mob that loses sight of him for a moment.
        if (t instanceof Mob mob && mob.getTarget() == caster) {
            Stun.apply(t, 6);
        }
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

    private static boolean ultMissing(LivingEntity caster, ServerLevel level) {
        SubCommon.warn(caster, "db.skill_coming");
        return false;
    }
}
