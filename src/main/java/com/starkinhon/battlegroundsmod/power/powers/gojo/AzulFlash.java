package com.starkinhon.battlegroundsmod.power.powers.gojo;

import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.BlackFlashSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Shift + Z of Gojo (gojo.azul_black_flash.json, see BlackFlashSpec): the Blue pulls the enemy to him and he meets it
// with a Black Flash. No orb: he holds his hand out and whoever he aims at (up to 50 blocks) is dragged in, faster and
// faster. Close to him time slows down (the target comes in slowly, his fist cocks back crackling black lightning),
// then the punch. The punch "fits": the impact frames, and the target stays stuck on the fist while the black lightning
// bursts out in waves and the crater opens right there, at the punch point (gamerule soulsBlockDestruction). Only at the
// end a last burst throws it far. The look is all on the client (client/fx/BlackFlashRenderer, sent as
// ModPayloads.BlackFlash: 0 slow motion, 1 the punch, 2 the throw) plus the skill file's vfx moments. The target has
// no control of its body the whole way (client/LimpBody, told each phase with ModPayloads.Limp): limbs dragged behind
// in the pull, floating in the slow motion, folded on the fist (a jolt each wave), tumbling in the throw, lying stunned.
public final class AzulFlash {
    public static final String SKILL = BlackFlashSpec.SKILL;
    // The pulling pose (the left arm out, the right fist cocked back), held until the punch.
    public static final String HOLD_ANIMATION = "gojo.azul_black_flash_segurando";
    public static final int COOLDOWN = 260;
    public static final int SLOW = 0;
    public static final int PUNCH = 1;
    public static final int THROW = 2;
    private static final double VIEW_DISTANCE = 160;
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();

    private enum Phase { PULL, SLOW, FIT, DONE }

    private AzulFlash() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        BlackFlashSpec spec = BlackFlashSpec.get(awakened);
        BlackFlashSpec.Puxar puxar = spec.puxar();
        BlackFlashSpec.Golpe golpe = spec.golpe();
        LivingEntity target = caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive() ? mob.getTarget()
                : Combat.raycastTarget(caster, puxar.alcanceAlvo());
        if (target == null || target.distanceTo(caster) > puxar.alcanceAlvo() + 2) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.azul_flash_no_target"), true);
            }
            return false;
        }
        String power = data.power;
        int interrupts = data.interrupts;
        int chargeTicks = Math.max(1, spec.segurado().carregarTicks());
        int slowTicks = Math.max(1, puxar.lentoTicks());
        int fit = Math.max(1, golpe.encaixeTicks());
        // Bosses don't hold keys: a charge of their own (BossOrder when the tactics chose one).
        BossOrder.Order order = BossOrder.take(caster);
        double bossCharge = order != null ? (order.hold() ? Mth.clamp(order.charge(), 0, 1) : 0) : RANDOM.nextInt(3) == 0 ? 0 : 0.35 + 0.65 * RANDOM.nextDouble();
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();
        Vec3 contact = front(caster, target, puxar);
        Vec3 dir = aim(caster, target);
        Vec3[] stopAt = {null};
        Function<String, Vec3> where = onde -> switch (onde == null ? "" : onde) {
            case "alvo" -> Combat.center(target);
            case "golpe" -> contact;
            case "impacto" -> stopAt[0] != null ? stopAt[0] : Combat.center(target);
            default -> caster.position().add(0, 1, 0);
        };

        // From now on the target is ours: held still (no walking, turning or skills), carried along the pull.
        Vec3 start = target.position();
        Vec3 end = contact.subtract(0, target.getBbHeight() / 2, 0);
        Vec3 line = end.subtract(start);
        double distance = line.length();
        // Where the slow motion takes over (or right away, when it is already that close).
        double slowFrom = Math.min(puxar.lentoDistancia(), distance);
        Vec3 slowStart = distance < 1.0E-3 ? end : end.subtract(line.scale(slowFrom / distance));
        Vec3[] hold = {start};
        Freeze.hold(target, 20 * 30, age -> hold[0]);
        limp(target, ModPayloads.Limp.PULL, 0, 0);
        double speed = Math.max(0.3, puxar.velocidade());
        // Starts slow and ends at velocidade (ease in, t^2): the average is half of it.
        int pullTicks = Mth.clamp((int) Math.ceil((distance - slowFrom) * 2 / speed), Math.max(1, puxar.minTicks()), Math.max(puxar.minTicks(), puxar.maxTicks()));
        // He stays where he stands from here to the throw (the pull ends in front of him, the punch has to land).
        Freeze.holdLookFree(caster, pullTicks + slowTicks + fit + 6);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), HOLD_ANIMATION, awakened));
        SkillSounds.play(level, spec.sons(), "uso", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", caster.position().add(0, 1.3, 0), caster.getLookAngle());
        SkillSounds.play(level, spec.sons(), "puxar", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "puxar", Combat.center(target), dir.scale(-1));
        data.busyTicks = pullTicks + slowTicks + fit + 8;

        Phase[] phase = {Phase.PULL};
        int[] phaseStart = {0};
        boolean[] released = {false};
        double[] charge = {0};
        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            boolean gone = !target.isAlive() || target.isRemoved() || target.level() != caster.level();
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || now.interrupts != interrupts || gone) {
                // Cut short: everyone is let go where they are.
                limp(target, ModPayloads.Limp.OFF, 0, 0);
                Freeze.free(target);
                Freeze.free(caster);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                return true;
            }
            now.busyTicks = Math.max(now.busyTicks, 2);
            // The charge: every tick the key is held from the start, until it is let go once.
            if (!released[0] && phase[0] != Phase.FIT) {
                boolean held = caster instanceof ServerPlayer ? Stamina.holding(caster) : charge[0] < bossCharge;
                if (held) {
                    charge[0] = Math.min(1, charge[0] + 1.0 / chargeTicks);
                    Charge.report(caster, charge[0]);
                } else {
                    released[0] = true;
                }
            }
            int t = age - phaseStart[0];
            switch (phase[0]) {
                case PULL -> {
                    double f = (t + 1) / (double) pullTicks;
                    hold[0] = start.lerp(slowStart, f * f);
                    face(target, caster);
                    if (t + 1 >= pullTicks) {
                        phase[0] = Phase.SLOW;
                        phaseStart[0] = age + 1;
                        limp(target, ModPayloads.Limp.SLOW, slowTicks, 0);
                        SkillSounds.play(level, spec.sons(), "lento", 0, slowTicks, where, screen);
                        Fx.effects(level, SKILL, awakened, "lento", contact, dir);
                        send(level, new ModPayloads.BlackFlash(SLOW, caster.getId(), contact, dir, (float) charge[0], slowTicks));
                    }
                }
                case SLOW -> {
                    // Coming in slowly, slower and slower, right onto the fist.
                    double f = (t + 1) / (double) slowTicks;
                    double k = 1 - (1 - f) * (1 - f);
                    hold[0] = slowStart.lerp(end, k);
                    face(target, caster);
                    if (t + 1 >= slowTicks) {
                        phase[0] = Phase.FIT;
                        phaseStart[0] = age + 1;
                        punch(level, caster, target, spec, awakened, charge[0], contact, dir, end, hold, stopAt, where, screen);
                    }
                }
                case FIT, DONE -> {
                    return true;
                }
            }
            return false;
        });
        return true;
    }

    // The Black Flash. It fits: the target stays on the fist through the waves of black lightning, the crater opens at
    // the punch point, and only after encaixeTicks the last burst throws it far.
    private static void punch(ServerLevel level, LivingEntity caster, LivingEntity target, BlackFlashSpec spec, boolean awakened, double c,
            Vec3 contact, Vec3 dir, Vec3 end, Vec3[] hold, Vec3[] stopAt, Function<String, Vec3> where, List<ServerPlayer> screen) {
        BlackFlashSpec.Golpe golpe = spec.golpe();
        int fit = Math.max(1, golpe.encaixeTicks());
        caster.swing(InteractionHand.MAIN_HAND, true);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        SkillSounds.play(level, spec.sons(), "blackFlash", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "blackFlash", contact, dir);
        send(level, new ModPayloads.BlackFlash(PUNCH, caster.getId(), contact, dir, (float) c, fit));
        Combat.hurt(caster, target, Mth.lerp((float) c, golpe.dano(), golpe.danoMax()));
        limp(target, ModPayloads.Limp.FIT, fit, 0);
        // Both of them feel it: he stays in the punch, the target stuck on the fist.
        Freeze.hold(target, fit + 2, age -> hold[0]);
        caster.getData(ModAttachments.POWER).busyTicks = Math.max(caster.getData(ModAttachments.POWER).busyTicks, fit + 8);
        boolean breaks = BlockRules.canBreak(level);
        List<Integer> waves = golpe.ondas() == null ? List.of() : golpe.ondas();
        double crater = Mth.lerp(c, golpe.crateraRaio(), golpe.crateraRaioMax());
        Vec3 ground = groundUnder(level, contact).add(new Vec3(dir.x, 0, dir.z).normalize().scale(1.2));
        Vec3 feet = caster.position();
        int[] debris = {0};
        Vec3[] last = {null};
        Tasks.add(age -> {
            if (!target.isAlive() || target.isRemoved()) {
                limp(target, ModPayloads.Limp.OFF, 0, 0);
                Freeze.free(target);
                Freeze.free(caster);
                return true;
            }
            if (age < fit) {
                // Shaking on the fist, pushed in a little more with every wave.
                double jolt = age > 6 ? 0.04 : 0;
                hold[0] = end.add((RANDOM.nextDouble() - 0.5) * jolt, (RANDOM.nextDouble() - 0.5) * jolt, (RANDOM.nextDouble() - 0.5) * jolt);
                target.setDeltaMovement(Vec3.ZERO);
                int wave = waves.indexOf(age);
                if (wave >= 0) {
                    limp(target, ModPayloads.Limp.JOLT, 0, (float) (0.6 + 0.4 * (wave + 1) / waves.size()));
                    SkillSounds.play(level, spec.sons(), "onda", 0, 0, where, screen);
                    Fx.effects(level, SKILL, awakened, "onda", contact, dir);
                    if (breaks) {
                        // The crater grows with each wave, and the punch point itself is blown open.
                        double grow = (wave + 1) / (double) waves.size();
                        crater(level, ground, crater * (0.45 + 0.55 * grow), feet, dir, debris);
                        carve(level, contact, 1.2 + 0.5 * c, feet, dir, debris);
                    }
                }
                return false;
            }
            if (age == fit) {
                // The last burst: only now it flies.
                Freeze.free(target);
                Freeze.free(caster);
                SkillSounds.play(level, spec.sons(), "arremesso", 0, 0, where, screen);
                Fx.effects(level, SKILL, awakened, "arremesso", contact, dir);
                send(level, new ModPayloads.BlackFlash(THROW, caster.getId(), contact, dir, (float) c, 0));
                if (breaks) {
                    crater(level, ground, crater, feet, dir, debris);
                }
                Combat.hurt(caster, target, Mth.lerp((float) c, golpe.danoFinal(), golpe.danoFinalMax()));
                Stun.apply(target, golpe.atordoadoTicks());
                limp(target, ModPayloads.Limp.THROW, golpe.atordoadoTicks(), (float) c);
                double throwSpeed = Mth.lerp(c, golpe.empurrao(), golpe.empurraoMax());
                target.setDeltaMovement(dir.scale(throwSpeed).add(0, golpe.subir() * throwSpeed * 0.3 + 0.2, 0));
                target.hurtMarked = true;
                last[0] = target.position();
                return false;
            }
            // Flying: where it stops (a wall, the ground) gets a last blow, without breaking anything there.
            int flying = age - fit;
            double moved = target.position().distanceTo(last[0]);
            last[0] = target.position();
            if (flying > 2 && (moved < 0.35 || flying >= 30)) {
                stopAt[0] = Combat.center(target);
                SkillSounds.play(level, spec.sons(), "impacto", 0, 0, where, screen);
                Fx.effects(level, SKILL, awakened, "impacto", stopAt[0], dir);
                return true;
            }
            return false;
        });
    }

    // The target's body, for whoever sees it (and the target itself, in third person).
    private static void limp(LivingEntity target, int phase, int ticks, float strength) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ModPayloads.Limp(target.getId(), phase, ticks, strength));
    }

    private static void send(ServerLevel level, ModPayloads.BlackFlash payload) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(payload.pos()) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // The top of the ground under the point (up to 6 blocks down), or the point itself in the air.
    private static Vec3 groundUnder(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
        for (int i = 0; i < 6; i++) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return new Vec3(at.x, pos.getY() + 1, at.z);
            }
            pos.move(0, -1, 0);
        }
        return at;
    }

    // A bowl dug into the ground around 'center' (wide and shallow), never under his own feet.
    private static void crater(ServerLevel level, Vec3 center, double r, Vec3 feet, Vec3 dir, int[] debris) {
        double deep = r * 0.55;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Mth.floor(center.y) - 1;
        for (int x = Mth.floor(center.x - r); x <= Mth.floor(center.x + r); x++) {
            for (int z = Mth.floor(center.z - r); z <= Mth.floor(center.z + r); z++) {
                double dx = (x + 0.5 - center.x) / r, dz = (z + 0.5 - center.z) / r;
                for (int y = top + 1; y >= top - Mth.ceil(deep); y--) {
                    double dy = (top + 0.5 - (y + 0.5)) / Math.max(0.5, deep);
                    if (y > top) {
                        // (grass, flowers and the like standing on the ground go too)
                        dy = 0;
                    }
                    if (dx * dx + dz * dz + dy * dy > 0.75 + 0.35 * hash(x, y, z)) {
                        continue;
                    }
                    pos.set(x, y, z);
                    remove(level, pos, center.add(0, 1, 0), feet, dir, debris);
                }
            }
        }
    }

    // Breaks the blocks within r of the point (a ragged ball): walls hit by the punch.
    private static void carve(ServerLevel level, Vec3 at, double r, Vec3 feet, Vec3 dir, int[] debris) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(at.x - r); x <= Mth.floor(at.x + r); x++) {
            for (int y = Mth.floor(at.y - r); y <= Mth.floor(at.y + r); y++) {
                for (int z = Mth.floor(at.z - r); z <= Mth.floor(at.z + r); z++) {
                    if (new Vec3(x + 0.5, y + 0.5, z + 0.5).distanceTo(at) > r * (0.85 + 0.3 * hash(x, y, z))) {
                        continue;
                    }
                    pos.set(x, y, z);
                    remove(level, pos, at, feet, dir, debris);
                }
            }
        }
    }

    // One block out: some fly off forward and to the sides as debris, the rest just go.
    private static void remove(ServerLevel level, BlockPos.MutableBlockPos pos, Vec3 from, Vec3 feet, Vec3 dir, int[] debris) {
        double fx = pos.getX() + 0.5 - feet.x, fz = pos.getZ() + 0.5 - feet.z;
        // Never the ground he stands on (nor the column over it).
        if (fx * fx + fz * fz < 1.6 * 1.6 && pos.getY() < feet.y + 2.2) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
            return;
        }
        if (debris[0] < 36 && RANDOM.nextInt(4) == 0) {
            debris[0]++;
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos.immutable(), state);
            block.dropItem = false;
            Vec3 out = new Vec3(pos.getX() + 0.5 - from.x, 0, pos.getZ() + 0.5 - from.z);
            out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize();
            block.setDeltaMovement(out.scale(0.25 + 0.35 * RANDOM.nextDouble()).add(new Vec3(dir.x, 0, dir.z).normalize().scale(0.2 + 0.2 * RANDOM.nextDouble()))
                    .add(0, 0.35 + 0.45 * RANDOM.nextDouble(), 0));
            block.hurtMarked = true;
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // Where the target ends up: its middle, right on his fist in front of his chest.
    private static Vec3 front(LivingEntity caster, LivingEntity target, BlackFlashSpec.Puxar puxar) {
        Vec3 forward = Combat.flatLook(caster);
        double gap = puxar.distanciaFrente() + (caster.getBbWidth() + target.getBbWidth()) * 0.5;
        double chest = caster.getBbHeight() * 0.62;
        return caster.position().add(forward.scale(gap)).add(0, Math.max(chest, target.getBbHeight() * 0.5), 0);
    }

    // The way the punch throws: where he looks (bosses: at the target), never down into the ground under him.
    private static Vec3 aim(LivingEntity caster, LivingEntity target) {
        Vec3 dir = caster instanceof Mob ? Combat.center(target).subtract(caster.getEyePosition()) : caster.getLookAngle();
        if (dir.lengthSqr() < 1.0E-4) {
            dir = Combat.flatLook(caster);
        }
        dir = dir.normalize();
        return dir.y < -0.15 ? new Vec3(dir.x, -0.15, dir.z).normalize() : dir;
    }

    private static void face(LivingEntity target, LivingEntity caster) {
        Vec3 to = caster.getEyePosition().subtract(target.getEyePosition());
        float yaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        Freeze.face(target, yaw, pitch);
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
