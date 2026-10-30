package com.starkinhon.battlegroundsmod.power.powers.bills;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.HakaiBolaSpec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The non fatal Hakai (bills.hakai_bola.json): V on a target that still has too much life to be erased. Bills raises
// his hand, a big sphere of hakai forms over it and goes after the target, slowly, through walls and anything else,
// for as long as the chase lasts. If it gets them they are trapped inside, held still and taking hits, until it blows
// up. The clients draw it from the position sent every tick (client/fx/HakaiRenderer); a hakaizinho can erase it.
public final class HakaiBola {
    public static final String SKILL = HakaiBolaSpec.SKILL;
    private static final double VIEW_DISTANCE = 160;
    private static int nextId;

    private HakaiBola() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level, LivingEntity target) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        HakaiBolaSpec spec = HakaiBolaSpec.get(awakened);
        HakaiBolaSpec.Bola bola = spec.bola();
        HakaiBolaSpec.Prisao prisao = spec.prisao();
        int raise = Math.max(1, spec.tempo().atrasoTicks());
        int chase = Math.max(1, bola.perseguirTicks());
        int trapTicks = Math.max(1, prisao.duracaoTicks());
        int every = Math.max(1, prisao.intervaloTicks());
        double radius = Math.max(0.5, bola.raio());
        int id = nextId++;

        data.busyTicks = raise;
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, raise, 3, false, false, false));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        Vec3[] pos = {overHand(caster, bola, radius)};
        Vec3[] velocity = {Vec3.ZERO};
        Function<String, Vec3> where = onde -> "alvo".equals(onde) ? Combat.center(target) : "bola".equals(onde) ? pos[0] : caster.position().add(0, 1, 0);
        List<ServerPlayer> screen = new ArrayList<>();
        if (caster instanceof ServerPlayer player) {
            screen.add(player);
        }
        if (target instanceof ServerPlayer player) {
            screen.add(player);
        }
        SkillSounds.play(level, spec.sons(), "uso", 0, raise, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", caster, raise);

        int interrupts = data.interrupts;
        int[] caught = {-1};
        Destructible.Attack[] attack = {null};
        Tasks.add(age -> {
            if (age < raise) {
                // The sphere grows over his raised hand; taken out before it is done, it fades.
                if (!caster.isAlive() || data.interrupts != interrupts) {
                    send(level, id, 2, pos[0], -1, 0, awakened);
                    return true;
                }
                pos[0] = overHand(caster, bola, radius);
                double k = (age + 1) / (double) raise;
                send(level, id, 0, pos[0], -1, (float) (radius * (0.15 + 0.85 * k * k * (3 - 2 * k))), awakened);
                return false;
            }
            if (attack[0] == null) {
                attack[0] = Destructible.track(level, caster, pos[0], radius, () -> send(level, id, 2, attack[0].pos(), -1, 0, awakened));
                SkillSounds.play(level, spec.sons(), "lancar", 0, chase, where, screen);
                Fx.effects(level, SKILL, awakened, "lancar", pos[0], Combat.center(target).subtract(pos[0]).normalize());
            }
            if (attack[0].gone()) {
                return true;
            }
            int t = age - raise;
            if (caught[0] < 0) {
                if (!target.isAlive() || target.isRemoved() || t >= chase) {
                    // The chase is over: it fades where it is.
                    Fx.effects(level, SKILL, awakened, "sumir", pos[0], Vec3.ZERO);
                    send(level, id, 2, pos[0], -1, 0, awakened);
                    attack[0].end();
                    return true;
                }
                // Slow and steady, straight through anything, turning a little each tick towards them.
                Vec3 to = Combat.center(target).subtract(pos[0]);
                Vec3 want = to.lengthSqr() < 1.0E-6 ? Vec3.ZERO : to.normalize().scale(bola.velocidade());
                velocity[0] = velocity[0].lerp(want, Math.max(0.01, Math.min(1, bola.curva())));
                if (velocity[0].length() > bola.velocidade()) {
                    velocity[0] = velocity[0].normalize().scale(bola.velocidade());
                }
                pos[0] = pos[0].add(velocity[0]);
                attack[0].move(pos[0]);
                send(level, id, 0, pos[0], -1, (float) radius, awakened);
                if (Combat.center(target).distanceTo(pos[0]) <= radius * bola.pegar()) {
                    caught[0] = t;
                    trap(caster, target, pos[0], trapTicks);
                    SkillSounds.play(level, spec.sons(), "prender", 0, trapTicks, where, screen);
                    Fx.effects(level, SKILL, awakened, "prender", target, trapTicks);
                }
                return false;
            }
            // Trapped: the ball stays put with them inside, hit after hit, until it blows up.
            int k = t - caught[0];
            send(level, id, 1, pos[0], target.getId(), (float) radius, awakened);
            if (k > 0 && k % every == 0 && k < trapTicks && target.isAlive()) {
                Combat.hurt(caster, target, prisao.dano());
                Fx.effects(level, SKILL, awakened, "dano", Combat.center(target), Vec3.ZERO);
            }
            if (k >= trapTicks || !target.isAlive()) {
                explode(caster, level, spec, pos[0]);
                SkillSounds.play(level, spec.sons(), "explosao", 0, 0, onde -> pos[0], screen);
                Fx.effects(level, SKILL, awakened, "explosao", pos[0], Vec3.ZERO);
                send(level, id, 2, pos[0], -1, 0, awakened);
                attack[0].end();
                return true;
            }
            return false;
        });
        return true;
    }

    // Held in the middle of the sphere, no moves, for the whole trap.
    private static void trap(LivingEntity caster, LivingEntity target, Vec3 center, int ticks) {
        Vec3 feet = center.subtract(0, target.getBbHeight() / 2, 0);
        Freeze.hold(target, ticks, age -> feet);
        Stun.apply(target, ticks);
        PowerLogic.seal(target, ticks);
    }

    private static void explode(LivingEntity caster, ServerLevel level, HakaiBolaSpec spec, Vec3 at) {
        HakaiBolaSpec.Explosao explosao = spec.explosao();
        double radius = Math.max(1, explosao.raio());
        for (LivingEntity hit : Combat.inRadius(level, at, radius, caster)) {
            double k = Math.min(1, Combat.center(hit).distanceTo(at) / radius);
            Combat.hurt(caster, hit, (float) (explosao.dano() * (1 - k * 0.5)));
            Combat.pushFrom(hit, at, explosao.empurrao() * (1 - k * 0.5), explosao.altura());
        }
    }

    private static Vec3 overHand(LivingEntity caster, HakaiBolaSpec.Bola bola, double radius) {
        return caster.position().add(0, caster.getBbHeight() + 0.6 + bola.acima() + radius, 0).add(Combat.flatLook(caster).scale(0.3));
    }

    private static void send(ServerLevel level, int id, int mode, Vec3 pos, int targetId, float radius, boolean awakened) {
        ModPayloads.HakaiBall payload = new ModPayloads.HakaiBall(id, mode, pos, targetId, radius, awakened);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
