package com.starkinhon.battlegroundsmod.power.powers.gojo;

import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
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
// with a Black Flash. A small Blue forms on his left palm (the arm held out, the right one cocked back), grabs the target
// he aims at and drags it in to the front of him, faster and faster. Let go of the key before it arrives and the punch
// goes as soon as it gets there; still held, the target floats in front of him while the punch charges, and it goes
// when the key is let go. Always a Black Flash: the world stops for a moment, the black and red lightning bursts out
// of the fist (client/fx/BlackFlashRenderer) and the target is thrown far, breaking what it flies through (with the
// gamerule soulsBlockDestruction).
// The Blue on the palm is the same orb as the Z's (client/fx/AzulRenderer, the realism look), sent from here every tick.
public final class AzulFlash {
    public static final String SKILL = BlackFlashSpec.SKILL;
    // The pulling pose (the left arm out with the Blue, the right fist cocked back), held until the punch.
    public static final String HOLD_ANIMATION = "gojo.azul_black_flash_segurando";
    public static final int COOLDOWN = 260;
    private static final double VIEW_DISTANCE = 160;
    private static final RandomSource RANDOM = RandomSource.create();
    // The palm Blue's ids, far from the Z's own (Azul counts from 0).
    private static int nextId = 1 << 24;

    private enum Phase { FORM, PULL, CHARGE, HIT }

    private AzulFlash() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        BlackFlashSpec spec = BlackFlashSpec.get(awakened);
        BlackFlashSpec.Puxar puxar = spec.puxar();
        BlackFlashSpec.Segurado segurado = spec.segurado();
        LivingEntity target = caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive() ? mob.getTarget()
                : Combat.raycastTarget(caster, puxar.alcanceAlvo());
        if (target == null || target.distanceTo(caster) > puxar.alcanceAlvo() + 2) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.azul_flash_no_target"), true);
            }
            return false;
        }
        int id = nextId++;
        String power = data.power;
        int interrupts = data.interrupts;
        int form = Math.max(1, puxar.formarTicks());
        int chargeTicks = Math.max(1, segurado.carregarTicks());
        int maxHold = Math.max(1, segurado.maxSegurarTicks());
        // Bosses don't hold keys: a charge of their own (BossOrder when the tactics chose one).
        BossOrder.Order order = BossOrder.take(caster);
        int bossCharge = Math.min(maxHold, order != null ? (order.hold() ? (int) Math.ceil(chargeTicks * Mth.clamp(order.charge(), 0, 1)) : 0)
                : RANDOM.nextInt(3) == 0 ? 0 : chargeTicks / 3 + RANDOM.nextInt(chargeTicks + 1));
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();
        Vec3[] contact = {front(caster, target, puxar)};
        Vec3[] stopAt = {null};
        Function<String, Vec3> where = onde -> switch (onde == null ? "" : onde) {
            case "usuario" -> caster.position().add(0, 1, 0);
            case "alvo" -> Combat.center(target);
            case "golpe" -> contact[0];
            case "impacto" -> stopAt[0] != null ? stopAt[0] : Combat.center(target);
            default -> palm(caster);
        };

        // From now on the target is ours: held still (no walking, turning or skills), carried along the pull.
        Vec3 start = target.position();
        Vec3[] hold = {start};
        Freeze.hold(target, 20 * 30, age -> hold[0]);
        double distance = start.distanceTo(contact[0]);
        double speed = Math.max(0.3, puxar.velocidade());
        // Starts slow and ends at velocidade (ease in, t^2): the average is half of it.
        int pullTicks = Mth.clamp((int) Math.ceil(distance * 2 / speed), Math.max(1, puxar.minTicks()), Math.max(puxar.minTicks(), puxar.maxTicks()));

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), HOLD_ANIMATION, awakened));
        SkillSounds.play(level, spec.sons(), "uso", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", palm(caster), caster.getLookAngle());
        data.busyTicks = form + pullTicks + 4;
        sendOrb(level, id, 0, palm(caster), puxar.orbeRaio() * 0.4, 0);

        Phase[] phase = {Phase.FORM};
        int[] phaseStart = {0};
        // Let go during the pull: the punch goes on arrival, with no charge.
        boolean[] released = {false};
        double[] charge = {0};
        Tasks.add(age -> {
            PowerData now = caster.getData(ModAttachments.POWER);
            boolean gone = !target.isAlive() || target.isRemoved() || target.level() != caster.level();
            if (!caster.isAlive() || caster.isRemoved() || !power.equals(now.power) || now.interrupts != interrupts || gone) {
                // Cut short: the Blue fades on the palm and the target is let go where it is.
                Freeze.free(target);
                sendOrb(level, id, 2, palm(caster), puxar.orbeRaio(), (float) charge[0]);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                return true;
            }
            now.busyTicks = Math.max(now.busyTicks, 2);
            boolean held = caster instanceof ServerPlayer ? Stamina.holding(caster) : age < form + pullTicks + bossCharge;
            if (!held) {
                released[0] = true;
            }
            int t = age - phaseStart[0];
            contact[0] = front(caster, target, puxar);
            switch (phase[0]) {
                case FORM -> {
                    double grow = smooth((t + 1) / (double) form);
                    sendOrb(level, id, 0, palm(caster), puxar.orbeRaio() * (0.4 + 0.6 * grow), 0);
                    if (t + 1 >= form) {
                        phase[0] = Phase.PULL;
                        phaseStart[0] = age + 1;
                        SkillSounds.play(level, spec.sons(), "puxar", 0, 0, where, screen);
                        Fx.effects(level, SKILL, awakened, "puxar", Combat.center(target), contact[0].subtract(start).normalize());
                    }
                }
                case PULL -> {
                    double f = (t + 1) / (double) pullTicks;
                    double k = f * f;
                    // Toward where in front of him it ends, wherever he is now.
                    Vec3 to = contact[0].subtract(0, target.getBbHeight() / 2, 0);
                    hold[0] = start.lerp(to, k);
                    face(target, caster);
                    sendOrb(level, id, 0, palm(caster), puxar.orbeRaio(), 0);
                    if (t + 1 >= pullTicks) {
                        SkillSounds.play(level, spec.sons(), "chegou", 0, 0, where, screen);
                        Fx.effects(level, SKILL, awakened, "chegou", Combat.center(target), caster.getLookAngle());
                        phaseStart[0] = age + 1;
                        if (released[0]) {
                            phase[0] = Phase.HIT;
                            hit(level, caster, target, spec, awakened, 0, contact, stopAt, hold, id, where, screen);
                        } else {
                            phase[0] = Phase.CHARGE;
                            SkillSounds.play(level, spec.sons(), "carregar", 0, maxHold, where, screen);
                            Fx.effects(level, SKILL, awakened, "carregar", caster.position().add(0, 1, 0), caster.getLookAngle());
                        }
                    }
                }
                case CHARGE -> {
                    // Floating in front of him, wherever he turns, while the punch charges.
                    hold[0] = contact[0].subtract(0, target.getBbHeight() / 2, 0);
                    face(target, caster);
                    charge[0] = Math.min(1, (t + 1) / (double) chargeTicks);
                    sendOrb(level, id, 0, palm(caster), Mth.lerp(charge[0], puxar.orbeRaio(), puxar.orbeRaioMax()), (float) charge[0]);
                    if (released[0] || t + 1 >= maxHold) {
                        phase[0] = Phase.HIT;
                        phaseStart[0] = age + 1;
                        hit(level, caster, target, spec, awakened, charge[0], contact, stopAt, hold, id, where, screen);
                    }
                }
                case HIT -> {
                    return true;
                }
            }
            return phase[0] == Phase.HIT;
        });
        return true;
    }

    // The Black Flash: the world stops a moment on the contact, then the target flies.
    private static void hit(ServerLevel level, LivingEntity caster, LivingEntity target, BlackFlashSpec spec, boolean awakened, double c,
            Vec3[] contact, Vec3[] stopAt, Vec3[] hold, int id, Function<String, Vec3> where, List<ServerPlayer> screen) {
        BlackFlashSpec.Golpe golpe = spec.golpe();
        Vec3 dir = aim(caster, target);
        Vec3 point = Combat.center(target).add(dir.scale(-target.getBbWidth() * 0.5));
        contact[0] = point;
        int stop = Math.max(1, Math.round(Mth.lerp((float) c, golpe.paradaTicks(), golpe.paradaTicksMax())));
        float damage = Mth.lerp((float) c, golpe.dano(), golpe.danoMax());
        double throwSpeed = Mth.lerp(c, golpe.empurrao(), golpe.empurraoMax());
        sendOrb(level, id, 1, palm(caster), spec.puxar().orbeRaio(), (float) c);
        caster.swing(InteractionHand.MAIN_HAND, true);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        SkillSounds.play(level, spec.sons(), "blackFlash", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "blackFlash", point, dir);
        ModPayloads.BlackFlash flash = new ModPayloads.BlackFlash(point, dir, (float) c, stop);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(point) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, flash);
            }
        }
        Combat.hurt(caster, target, damage);
        // The impact frame: both of them stopped in place, the target still held where the fist met it.
        Freeze.hold(caster, stop);
        caster.getData(ModAttachments.POWER).busyTicks = Math.max(caster.getData(ModAttachments.POWER).busyTicks, stop + 6);
        boolean breaks = BlockRules.canBreak(level);
        Vec3[] last = {null};
        int[] debris = {0};
        Tasks.add(age -> {
            if (!target.isAlive() || target.isRemoved()) {
                Freeze.free(target);
                return true;
            }
            if (age < stop) {
                target.setDeltaMovement(Vec3.ZERO);
                return false;
            }
            if (age == stop) {
                Freeze.free(target);
                Stun.apply(target, golpe.atordoadoTicks());
                target.setDeltaMovement(dir.scale(throwSpeed).add(0, golpe.subir() * throwSpeed * 0.3 + 0.2, 0));
                target.hurtMarked = true;
                last[0] = target.position();
                return false;
            }
            int flying = age - stop;
            Vec3 pos = Combat.center(target);
            double moved = last[0] == null ? 0 : target.position().distanceTo(last[0]);
            last[0] = target.position();
            if (breaks && flying <= golpe.quebraTicks()) {
                carve(level, pos, golpe.quebraRaio() * (0.7 + 0.3 * c), debris);
            }
            // It stopped (a wall, the ground) or flew its time: the last blow lands there.
            if (flying > 2 && (moved < 0.35 || flying >= golpe.quebraTicks())) {
                stopAt[0] = pos;
                SkillSounds.play(level, spec.sons(), "impacto", 0, 0, where, screen);
                Fx.effects(level, SKILL, awakened, "impacto", pos, dir);
                if (breaks) {
                    carve(level, pos, golpe.quebraRaio() * 1.5, debris);
                }
                return true;
            }
            return false;
        });
    }

    // Breaks the blocks within r of the point (a ragged ball), a few flying off as debris.
    private static void carve(ServerLevel level, Vec3 at, double r, int[] debris) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = Mth.floor(at.x - r), maxX = Mth.floor(at.x + r);
        int minY = Mth.floor(at.y - r), maxY = Mth.floor(at.y + r);
        int minZ = Mth.floor(at.z - r), maxZ = Mth.floor(at.z + r);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Vec3 mid = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    double d = mid.distanceTo(at);
                    if (d > r * (0.85 + 0.3 * hash(x, y, z))) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
                        continue;
                    }
                    if (debris[0] < 20 && RANDOM.nextInt(10) == 0) {
                        debris[0]++;
                        FallingBlockEntity block = FallingBlockEntity.fall(level, pos.immutable(), state);
                        block.dropItem = false;
                        Vec3 out = mid.subtract(at);
                        out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : out.normalize();
                        block.setDeltaMovement(out.scale(0.5 + 0.5 * RANDOM.nextDouble()).add(0, 0.35, 0));
                        block.hurtMarked = true;
                    } else {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    // Where the target ends the pull: its middle, right in front of his chest.
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

    // His left palm, the arm held out in front of him (the clients smooth the orb between the ticks).
    static Vec3 palm(LivingEntity caster) {
        Vec3 forward = Combat.flatLook(caster);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double scale = caster.getBbHeight() / 1.8;
        return caster.position().add(forward.scale(0.78 * scale)).add(right.scale(-0.34 * scale)).add(0, 1.36 * scale, 0);
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    // The Blue on the palm, drawn by the Z's renderer (mode 0 alive, 1 bursts into the punch, 2 fades); realism look.
    private static void sendOrb(ServerLevel level, int id, int mode, Vec3 pos, double radius, float charge) {
        ModPayloads.Azul payload = new ModPayloads.Azul(id, mode, pos, (float) radius, charge, 2);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
