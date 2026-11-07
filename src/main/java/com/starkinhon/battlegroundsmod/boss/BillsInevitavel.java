package com.starkinhon.battlegroundsmod.boss;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.bills.Hakai;
import com.starkinhon.battlegroundsmod.skill.HakaiSpec;
import com.starkinhon.battlegroundsmod.skill.InevitavelSpec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The Bills boss's Inevitável (Stark's idea): Bills is tier 0, a god. Whoever he targets at tier 4 or weaker
// (InevitavelSpec.tierMinimo) doesn't get a fight. He appears in front of them and they freeze where they stand, can't
// move, turn, attack or use anything. Looking them in the eyes he says "Você realmente acha que pode vir me enfrentar
// com esse nível de poder? Patético." while he raises his arm at them, very slowly. Then the Hakai: the camera on his
// eyes, "Hakai.", the open hand, and they are erased whatever their life (Hakai.strike). As the last sparks go he says
// "Saiba, o deus da destruição é inevitável."
// Players in creative or spectator are never picked. He is untouchable for the whole scene: it is inevitable.
public final class BillsInevitavel {
    // Bills in a scene: until when (game time).
    private static final Map<LivingEntity, Long> RUNNING = new WeakHashMap<>();
    // Bills' next try (game time), so a scene that could not start (no room around them) doesn't retry every tick.
    private static final Map<LivingEntity, Long> NEXT = new WeakHashMap<>();
    // Whoever was erased this way: Bills says the line of the scene, not his usual "Que tédio." over it.
    private static final Set<LivingEntity> VICTIMS = java.util.Collections.newSetFromMap(new WeakHashMap<>());

    private BillsInevitavel() {
    }

    // While the scene plays: his other lines wait.
    public static boolean running(LivingEntity bills) {
        Long until = RUNNING.get(bills);
        return until != null && bills.level().getGameTime() < until;
    }

    // This death was the Inevitável: true once.
    static boolean victim(LivingEntity target) {
        return VICTIMS.remove(target);
    }

    // Not even worth a fight: a god (tier 0) looking at someone tier tierMinimo or weaker.
    static boolean beneath(SoulBoss bills, LivingEntity target, InevitavelSpec spec) {
        if (Tiers.of(bills) != Tiers.DEUS || Tiers.of(target) < spec.tierMinimo()) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return target.isAlive() && !target.isRemoved() && !Intangible.untouchable(target);
    }

    // Starts the scene on Bills' target if it is beneath him: true when it started.
    static boolean tryStart(SoulBoss bills, LivingEntity target) {
        if (target == null || !bills.isBoss() || !(bills.level() instanceof ServerLevel level) || running(bills)) {
            return false;
        }
        long now = level.getGameTime();
        if (now < NEXT.getOrDefault(bills, Long.MIN_VALUE)) {
            return false;
        }
        PowerData data = bills.powerData();
        boolean awakened = data.awakened;
        InevitavelSpec spec = InevitavelSpec.get(awakened);
        if (!beneath(bills, target, spec) || bills.distanceTo(target) > spec.alcance() || target.level() != level) {
            return false;
        }
        NEXT.put(bills, now + Math.max(20, spec.recargaTicks()));
        Vec3 spot = spot(level, bills, target, spec.distancia());
        if (spot == null) {
            return false;
        }
        HakaiSpec hakai = HakaiSpec.get(awakened);
        int strikeAt = Math.max(1, spec.hakaiTick());
        int total = strikeAt + hakai.cutsceneEnd() + 2;
        RUNNING.put(bills, now + total);

        // He is simply there, in front of them.
        Fx.effects(level, Hakai.SKILL, awakened, "sumir", bills.position().add(0, 1, 0), Vec3.ZERO);
        Fx.sound(level, bills.position(), SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.6f);
        Hakai.move(bills, spot, bills.getYRot(), bills.getXRot());
        Hakai.face(bills, target);
        Hakai.face(target, bills);
        Fx.sound(level, spot, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.5f);

        // Both stand still for the whole thing; they can't do anything, not even a counter, and nothing touches either.
        data.busyTicks = total;
        Freeze.hold(bills, total);
        Freeze.hold(target, total);
        Stun.apply(target, total, true);
        PowerLogic.seal(target, total);
        Intangible.hold(bills, total);
        Intangible.hold(target, strikeAt + 1);
        bills.getNavigation().stop();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(bills,
                new ModPayloads.PlaySkillAnimation(bills.getId(), InevitavelSpec.SKILL, awakened));

        // A breath of silence, then he speaks, raising his arm.
        Tasks.later(Math.max(0, spec.falaTick()), () -> {
            if (bills.isAlive() && target.isAlive()) {
                BossFalas.sayNow(bills, InevitavelSpec.FALA);
            }
        });
        Tasks.later(strikeAt, () -> {
            if (!bills.isAlive() || !target.isAlive() || target.isRemoved() || target.level() != level) {
                // Gone before the Hakai (logged out, killed by something else): the scene ends here.
                end(bills, target);
                return;
            }
            Hakai.face(bills, target);
            Hakai.face(target, bills);
            VICTIMS.add(target);
            int kill = Hakai.strike(bills, level, target, false, false);
            Tasks.later(Math.max(1, kill - Math.max(0, spec.fimAntesTicks())), () -> {
                if (bills.isAlive()) {
                    BossFalas.sayNow(bills, InevitavelSpec.FALA_FIM);
                }
            });
        });
        return true;
    }

    private static void end(SoulBoss bills, LivingEntity target) {
        RUNNING.remove(bills);
        Freeze.free(bills);
        Freeze.free(target);
        Intangible.free(bills);
        bills.powerData().busyTicks = 0;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(bills, new ModPayloads.PlaySkillAnimation(bills.getId(), "", false));
    }

    // Where he appears: distance blocks in front of them on the side he comes from, else around them, with room for his
    // body, a block up or down at most, and in their sight (never on the other side of a wall). Null when nowhere fits.
    private static Vec3 spot(ServerLevel level, SoulBoss bills, LivingEntity target, double distance) {
        Vec3 d = bills.position().subtract(target.position());
        Vec3 from = new Vec3(d.x, 0, d.z);
        from = from.lengthSqr() < 1.0e-4 ? Combat.flatLook(target) : from.normalize();
        Vec3 eyes = target.getEyePosition();
        double reach = Math.max(1.2, distance);
        for (double turn : new double[] {0, 40, -40, 80, -80, 125, -125, 180}) {
            Vec3 dir = from.yRot((float) Math.toRadians(turn));
            Vec3 at = target.position().add(dir.scale(reach));
            for (double dy : new double[] {0, 1, -1}) {
                Vec3 p = at.add(0, dy, 0);
                if (!level.noCollision(bills, bills.getBoundingBox().move(p.subtract(bills.position())))) {
                    continue;
                }
                Vec3 head = p.add(0, bills.getEyeHeight(), 0);
                if (level.clip(new ClipContext(eyes, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target)).getType()
                        == HitResult.Type.MISS) {
                    return p;
                }
            }
        }
        return null;
    }

    public static void clear() {
        RUNNING.clear();
        NEXT.clear();
        VICTIMS.clear();
    }
}
