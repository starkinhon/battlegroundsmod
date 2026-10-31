package com.starkinhon.battlegroundsmod.power.powers.bills;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.HakaiSpec;
import com.starkinhon.battlegroundsmod.skill.HakaizinhoSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Shift + V: the hakaizinho (bills.hakaizinho.json). Bills flicks a tiny pink spark of hakai from his fingertip and it
// flies straight where he aims, through everything: blocks on its path are gone (no drops, no debris), whoever it
// touches is hit hard and erased like under the Hakai if that leaves them low enough, and attacks in flight it meets
// are pulverized (combat/Destructible: Madara's meteor, the giant fireball, the Susanoo slash, a falling sphere...).
// The spark is drawn on the clients (client/fx/HakaiRenderer) on the same straight path.
public final class Hakaizinho {
    public static final String SKILL = HakaizinhoSpec.SKILL;

    // A boss aiming at something else than its target (an attack flying at it), read when the spark leaves the finger.
    private static final Map<LivingEntity, Supplier<Vec3>> AIM = new WeakHashMap<>();

    private Hakaizinho() {
    }

    // The next spark of this caster goes at point (followed until it is shot); null goes back to its target.
    public static void aimAt(LivingEntity caster, Supplier<Vec3> point) {
        if (point == null) {
            AIM.remove(caster);
        } else {
            AIM.put(caster, point);
        }
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        HakaizinhoSpec spec = HakaizinhoSpec.get(awakened);
        int windUp = Math.max(0, spec.tempo().atrasoTicks());
        data.busyTicks = windUp + 4;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        Function<String, Vec3> where = onde -> fingertip(caster, aim(caster));
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();
        SkillSounds.play(level, spec.sons(), "uso", 0, windUp, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", caster, windUp);
        int interrupts = data.interrupts;
        Tasks.later(windUp, () -> {
            // Cut off before the flick (grabbed, sealed): nothing comes out.
            if (!caster.isAlive() || data.interrupts != interrupts) {
                return;
            }
            Vec3 dir = aim(caster);
            AIM.remove(caster);
            Vec3 start = fingertip(caster, dir);
            SkillSounds.play(level, spec.sons(), "lancar", 0, 0, where, screen);
            Fx.effects(level, SKILL, awakened, "lancar", start, dir);
            fly(caster, level, spec, start, dir, awakened, screen);
        });
        return true;
    }

    private static void fly(LivingEntity caster, ServerLevel level, HakaizinhoSpec spec, Vec3 start, Vec3 dir, boolean awakened,
            List<ServerPlayer> screen) {
        HakaizinhoSpec.Voo voo = spec.voo();
        HakaizinhoSpec.Destruir destruir = spec.destruir();
        double speed = Math.max(0.2, voo.velocidade());
        double reach = Math.max(speed, voo.alcance());
        int ticks = (int) Math.ceil(reach / speed);
        Vec3 velocity = dir.scale(speed);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(start) < (reach + 96) * (reach + 96)) {
                PacketDistributor.sendToPlayer(player, new ModPayloads.HakaiShot(start, velocity, ticks, awakened));
            }
        }
        boolean grief = destruir.raioBlocos() > 0 && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        HakaiSpec hakai = HakaiSpec.get(awakened);
        Set<LivingEntity> hit = new HashSet<>();
        Tasks.repeat(ticks, age -> {
            Vec3 from = start.add(dir.scale(age * speed));
            Vec3 to = start.add(dir.scale(Math.min(reach, (age + 1) * speed)));
            // Attacks in its way: gone, in a burst of hakai where they were.
            for (Destructible.Attack attack : Destructible.eraseAlong(level, from, to, destruir.raioAtaques(), caster)) {
                Vec3 at = attack.pos();
                Fx.effects(level, SKILL, awakened, "pulverizar", at, dir);
                SkillSounds.play(level, spec.sons(), "pulverizar", 0, 0, onde -> at, screen);
            }
            for (LivingEntity target : Combat.alongLine(level, from, to, voo.raio(), caster)) {
                if (!hit.add(target)) {
                    continue;
                }
                if (!hakai.canErase(target)) {
                    Combat.hurt(caster, target, destruir.dano());
                }
                Fx.effects(level, SKILL, awakened, "acerto", Combat.center(target), dir);
                if (target.isAlive() && hakai.canErase(target)) {
                    // Pulverized: the same sound as an attack it erases.
                    Hakai.disintegrate(caster, target, awakened);
                    SkillSounds.play(level, spec.sons(), "pulverizar", 0, 0, onde -> Combat.center(target), screen);
                } else {
                    SkillSounds.play(level, spec.sons(), "acerto", 0, 0, onde -> Combat.center(target), screen);
                }
            }
            if (grief) {
                dig(level, from, to, destruir.raioBlocos());
            }
        });
    }

    // A round tunnel along the segment: every block that can be broken is simply gone. No drops, no neighbor updates.
    private static void dig(ServerLevel level, Vec3 from, Vec3 to, double radius) {
        Set<BlockPos> done = new HashSet<>();
        Vec3 along = to.subtract(from);
        double length = along.length();
        int r = (int) Math.ceil(radius);
        for (double s = 0; s <= length; s += 0.5) {
            Vec3 point = length < 1.0E-6 ? from : from.add(along.scale(s / length));
            BlockPos center = BlockPos.containing(point);
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos block = center.offset(dx, dy, dz);
                        if (!done.add(block) || Vec3.atCenterOf(block).distanceTo(point) > radius + 0.3 || !level.isLoaded(block)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(block);
                        if (state.isAir() || state.getDestroySpeed(level, block) < 0) {
                            continue;
                        }
                        level.setBlock(block, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    // Where he aims: his look, or at the one he is fighting for a caster without a crosshair (a boss).
    private static Vec3 aim(LivingEntity caster) {
        Supplier<Vec3> at = AIM.get(caster);
        if (at != null && at.get() != null) {
            return at.get().subtract(caster.getEyePosition()).normalize();
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        if (!(caster instanceof ServerPlayer) && data.foughtId >= 0 && caster.level().getEntity(data.foughtId) instanceof LivingEntity fought) {
            return Combat.center(fought).subtract(caster.getEyePosition()).normalize();
        }
        return Vec3.directionFromRotation(caster.getXRot(), caster.getYRot());
    }

    // The tip of his right index finger, raised in front of him.
    private static Vec3 fingertip(LivingEntity caster, Vec3 dir) {
        Vec3 flat = Combat.flatLook(caster);
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        return caster.getEyePosition().add(right.scale(0.35)).add(dir.scale(0.8)).add(0, -0.1, 0);
    }
}
