// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.bills;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.AshKill;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.EsferaSpec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Shift + C: Esfera da Destruição Planetária (bills.esfera.json, see EsferaSpec; the normal C is DestructionSphere).
// Bills puts his hands behind his back and vanishes in black streaks, taking the target with him to outer space: a
// cutscene only the two of them see (client/EsferaCutscene, client/fx/SpaceScene), where the target lies adrift below
// him, he charges the sphere between his hands and throws it, and it hits the target and carries it down. Everyone else sees
// both of them gone (frozen, untouchable and hidden where they stood). Back in the world Bills lands where he was,
// facing the spot queda.distancia blocks ahead of him, and the target falls from queda.altura blocks up straight down
// onto it with the sun on top of it: it blows up in flames, opening a crater.
// It needs a stunned target (Stamina.stunnable: stunned by a hit, or exhausted), e.g. right after the Destruction Bop.
public final class Esfera {
    public static final String SKILL = EsferaSpec.SKILL;
    private static final double VIEW_DISTANCE = 256;
    // How long the crater takes to open from the middle out (the budget per pulse may stretch it a little).
    private static final int CRATER_TICKS = 20;
    // Stark 2026-11-26: while the cutscene played they stayed on the ground, only hidden: mobs kept chasing them, their
    // names still showed and the fight went on around their bodies. Now both are carried this high straight up over
    // where they stood (a place of their own, far from everything: no mob reaches or sees them, no name or health
    // bar is drawn that far), held there, untouchable, until they come back. Same column, so the same chunks stay loaded.
    private static final double AWAY_UP = 256;
    // Who is away up there right now (UUID -> spot on the ground), players and mobs: logging out mid-cutscene puts a
    // player back there first, so nobody is saved up in the sky and falls when they come back; and the systems that
    // keep people inside an area (the challenge arenas) leave them alone while they are away (see away(Entity)).
    private static final java.util.Map<java.util.UUID, Vec3> AWAY = new java.util.HashMap<>();

    // Up in the sky for the Esfera's cutscene right now (the arenas don't pull them back into the ring meanwhile).
    public static boolean away(net.minecraft.world.entity.Entity entity) {
        return entity != null && !AWAY.isEmpty() && AWAY.containsKey(entity.getUUID());
    }

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Vec3 back = AWAY.remove(player.getUUID());
                // (only if still up there: someone who died meanwhile already respawned somewhere else)
                if (back != null && player.isAlive() && player.getY() > back.y + AWAY_UP / 2) {
                    player.teleportTo(back.x, back.y, back.z);
                    player.fallDistance = 0;
                }
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> AWAY.clear());
    }

    private Esfera() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        EsferaSpec spec = EsferaSpec.get(awakened);
        LivingEntity target = Combat.raycastTarget(caster, spec.alvo().alcance());
        if (target == null && data.foughtId >= 0 && level.getEntity(data.foughtId) instanceof LivingEntity fought && Combat.isTarget(fought, caster)
                && fought.distanceTo(caster) <= spec.alvo().alcance()) {
            target = fought;
        }
        if (target == null && caster instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null
                && Combat.isTarget(mob.getTarget(), caster) && mob.getTarget().distanceTo(caster) <= spec.alvo().alcance()) {
            target = mob.getTarget();
        }
        if (target == null) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_target"), true);
            }
            return false;
        }
        if (!com.starkinhon.battlegroundsmod.combat.Capture.canGrab(caster, target)) {
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_target"), true);
            }
            return false;
        }
        if (!Stamina.stunnable(target)) {
            // Only a stunned (or exhausted) target can be taken to space: told on screen, nothing spent.
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.battlegroundsmod.needs_stun")
                        .withStyle(s -> s.withColor(0xFF5555)), true);
            }
            return false;
        }
        LivingEntity victim = target;
        // Out of the hakai ball (or any trap) first: one grab at a time (it blows up on them before the trip).
        com.starkinhon.battlegroundsmod.combat.Capture.take(victim);
        int tp = spec.tpTick();
        int blast = spec.blastTick();
        int end = spec.endTick();
        com.starkinhon.battlegroundsmod.combat.Capture.grabbed(victim, caster, end);
        // Laid out from where he stands and faces now, the same way on the clients.
        Vec3 forward = Vec3.directionFromRotation(0, caster.getYRot());
        Vec3 home = caster.position();

        // Away in space: both stand still where they were, untouchable, and the others don't see them.
        data.busyTicks = end + 2;
        // (Let go a tick before the end, so the landing below can move and turn them.)
        // Up and away from the moment they vanish (the black streaks) until they are back.
        // Not inside an expansion (Gojo's, Sukuna's, a Duelo de Expansões), theirs or the target's: its space is a place of
        // its own already, and going up would break it (Gojo's shatters when he leaves it) or leave them in the air when
        // it ends. There they stay where they are, only hidden and untouchable, as before.
        boolean stayDown = inExpansion(caster) || inExpansion(target);
        double up = stayDown ? 0 : AWAY_UP;
        Vec3 casterAway = home.add(0, up, 0);
        Freeze.hold(caster, end - 1, age -> age < tp ? home : casterAway);
        // Where the target will come down from, laid out now: queda.distancia blocks ahead of him, queda.altura over
        // the ground there. It is moved up there while it is still away in space (hidden from everyone, drawn adrift by
        // the two screens in the cutscene), so when the cutscene ends it is already in the sky and nobody sees it
        // standing on the ground for a moment first.
        EsferaSpec.Queda queda = spec.queda();
        Vec3 ahead = home.add(forward.scale(queda.distancia()));
        double floorY;
        Vec3 high;
        // Stark 2026-11-20: in the Livro dos Desafios arena the target came down on top of the barrier ceiling (the ground
        // was looked for from 40 over Bills, which hit the ceiling, and it fell from 150 over that) and was stuck up there.
        // In an arena: the spot is pulled inside the ring, the ground is looked for under the ceiling, and the fall starts
        // just under it. Out in the world it falls from queda.altura as always.
        com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.Bounds box =
                com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.bounds(level, home);
        if (box != null) {
            double cx = box.center().getX() + 0.5, cz = box.center().getZ() + 0.5;
            double dx = ahead.x - cx, dz = ahead.z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            double keep = Math.max(1, box.radius() - 4.0);
            if (d > keep) {
                ahead = new Vec3(cx + dx * keep / d, ahead.y, cz + dz * keep / d);
            }
            double under = box.ceilingY() - 1.0;
            floorY = ground(level, new Vec3(ahead.x, under, ahead.z), under - (box.center().getY() - 40));
            floorY = Math.min(floorY, box.ceilingY() - 4.0);
            high = new Vec3(ahead.x, Math.max(floorY + 1, Math.min(floorY + queda.altura(), box.ceilingY() - 3.0)), ahead.z);
        } else {
            floorY = ground(level, new Vec3(ahead.x, home.y + 40, ahead.z), 120);
            high = new Vec3(ahead.x, floorY + queda.altura(), ahead.z);
        }
        final Vec3 spot = ahead;
        final double groundY = floorY;
        final Vec3 top = high;
        double drop = Math.max(1, top.y - groundY);
        Vec3 stood = victim.position();
        Vec3 victimAway = stood.add(0, up, 0);
        Freeze.hold(victim, end - 1, age -> age < tp ? stood : victimAway);
        if (spec.cutscene().intangivel()) {
            Intangible.hold(caster, end);
            Intangible.holdExcept(victim, end, caster);
        }
        List<ServerPlayer> screen = new ArrayList<>();
        if (caster instanceof ServerPlayer player) {
            screen.add(player);
        }
        if (victim instanceof ServerPlayer player) {
            screen.add(player);
        }
        if (spec.cutscene().ativa()) {
            for (ServerPlayer player : screen) {
                PacketDistributor.sendToPlayer(player, new ModPayloads.EsferaCutscene(caster.getId(), victim.getId(), awakened));
            }
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL + "#bills", awakened));
        // Their spots on the ground (while away they are held high up, where nobody would hear a thing).
        Function<String, Vec3> where = onde -> "alvo".equals(onde) ? stood.add(0, 1, 0) : home.add(0, 1, 0);
        SkillSounds.play(level, spec.sons(), "uso", 0, tp, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", caster, tp);

        Tasks.later(tp, () -> {
            if (!caster.isAlive()) {
                return;
            }
            // Gone in black streaks, the target with him.
            Fx.effects(level, SKILL, awakened, "tp", home, forward);
            Fx.effects(level, SKILL, awakened, "tp", stood, forward);
            SkillSounds.play(level, spec.sons(), "tp", 0, 0, where, screen);
            hideFromOthers(level, home, caster, victim, end - tp);
            if (!stayDown) {
                away(level, caster, home, end - tp);
                away(level, victim, stood, end - tp);
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim, new ModPayloads.PlaySkillAnimation(victim.getId(), SKILL + "#alvo", awakened));
            SkillSounds.play(level, spec.sons(), "espaco", spec.spaceTick() - tp, end - spec.spaceTick(), where, screen);
        });
        SkillSounds.play(level, spec.sons(), "carga", spec.chargeTick(), spec.throwTick() - spec.chargeTick(), where, screen);
        SkillSounds.play(level, spec.sons(), "lancar", spec.throwTick(), spec.flyTick() - spec.throwTick(), where, screen);
        // The ball flies small for a while and implodes into the giant sun at blastTick (no contact in space: the sun
        // only swallows the target back in the world, on the way down).
        SkillSounds.play(level, spec.sons(), "implodir", blast, end - blast, where, screen);

        Tasks.later(blast, () -> {
            // The sun hits it in space (no explosion up there; danoEspaco 0 by default).
            if (victim.isAlive() && spec.danoEspaco() > 0) {
                AshKill.hurt(caster, victim, spec.danoEspaco());
            }
        });

        Tasks.later(end, () -> {
            back(caster);
            back(victim);
            if (caster.isAlive()) {
                // Back on the ground in black streaks, facing where the target will come down.
                // (at his spot on the ground: he is still held up in the sky until the teleport below)
                Fx.effects(level, SKILL, awakened, "voltar", home, forward);
                SkillSounds.play(level, spec.sons(), "voltar", 0, 0, onde -> home.add(0, 1, 0), screen);
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
            if (!victim.isAlive() || victim.isRemoved()) {
                if (caster instanceof ServerPlayer player && caster.isAlive()) {
                    player.teleportTo(level, home.x, home.y, home.z, player.getYRot(), player.getXRot());
                } else if (caster.isAlive()) {
                    caster.teleportTo(home.x, home.y, home.z);
                }
                hideFromOthers(level, home, caster, victim, 0);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim, new ModPayloads.PlaySkillAnimation(victim.getId(), "", false));
                return;
            }
            // Still lying adrift and stunned while the sun carries it down.
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim, new ModPayloads.PlaySkillAnimation(victim.getId(), SKILL + "#queda", awakened));
            Stun.apply(victim, (int) Math.ceil(drop / Math.max(0.2, queda.velocidade())) + 20);
            // Back on the spot he left (he was held up in the sky, see AWAY_UP); in an expansion, where it has him now.
            Vec3 from = stayDown && caster.isAlive() ? caster.position() : home;
            float yaw = (float) (Mth.atan2(spot.z - from.z, spot.x - from.x) * Mth.RAD_TO_DEG) - 90;
            if (caster instanceof ServerPlayer player) {
                player.teleportTo(level, from.x, from.y, from.z, yaw, -10);
            } else if (caster.isAlive()) {
                caster.teleportTo(from.x, from.y, from.z);
                caster.setYRot(yaw);
                caster.setYHeadRot(yaw);
                caster.setYBodyRot(yaw);
            }
            caster.fallDistance = 0;
            if (victim instanceof ServerPlayer player) {
                player.teleportTo(level, top.x, top.y, top.z, yaw + 180, 60);
            } else {
                victim.teleportTo(top.x, top.y, top.z);
            }
            // Seen again only once both are back down (not for a frame up in the sky).
            hideFromOthers(level, home, caster, victim, 0);
            fall(caster, level, spec, victim, groundY, drop, awakened, screen);
        });
        return true;
    }

    // The target coming down from the sky, straight, into a crater.
    private static void fall(LivingEntity caster, ServerLevel level, EsferaSpec spec, LivingEntity victim, double groundY, double drop,
            boolean awakened, List<ServerPlayer> screen) {
        EsferaSpec.Queda queda = spec.queda();
        double speed = Math.max(0.2, queda.velocidade());
        int max = (int) Math.ceil(drop / speed) + 60;
        Fx.effects(level, SKILL, awakened, "queda", victim, max);
        // The sun comes down with it, on top of it.
        Vec3 head = victim.position().add(0, victim.getBbHeight(), 0);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim, new ModPayloads.EsferaFall(victim.getId(), head, head, awakened));
        SkillSounds.play(level, spec.sons(), "queda", 0, (int) Math.ceil(drop / speed), onde -> victim.position(), screen);
        // Still "away" while it comes down from the sky: a challenge arena would otherwise pull it straight back into the
        // ring (it starts far over the ceiling) and the sun would land on the spawn instead of where it falls.
        AWAY.put(victim.getUUID(), new Vec3(victim.getX(), groundY, victim.getZ()));
        Tasks.add(age -> {
            if (!victim.isAlive() || victim.isRemoved()) {
                AWAY.remove(victim.getUUID());
                return true;
            }
            victim.fallDistance = 0;
            Vec3 pos = victim.position();
            var below = level.clip(new ClipContext(pos, pos.subtract(0, speed + 0.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
            boolean landed = victim.onGround() || below.getType() != HitResult.Type.MISS || age >= max;
            if (!landed) {
                victim.setDeltaMovement(0, -speed, 0);
                victim.hurtMarked = true;
                return false;
            }
            Vec3 at = below.getType() != HitResult.Type.MISS ? below.getLocation() : pos;
            victim.setDeltaMovement(0, 0, 0);
            victim.hurtMarked = true;
            AWAY.remove(victim.getUUID());
            land(caster, level, spec, victim, at, awakened, screen);
            return true;
        });
    }

    private static void land(LivingEntity caster, ServerLevel level, EsferaSpec spec, LivingEntity victim, Vec3 at, boolean awakened,
            List<ServerPlayer> screen) {
        EsferaSpec.Queda queda = spec.queda();
        AshKill.hurt(caster, victim, queda.dano());
        double radius = Math.max(1, queda.raio());
        for (LivingEntity hit : Combat.inRadius(level, at, radius, caster)) {
            if (hit == victim) {
                continue;
            }
            double k = Math.min(1, Combat.center(hit).distanceTo(at) / radius);
            AshKill.hurt(caster, hit, (float) (queda.danoArea() * (1 - k * 0.6)));
            Combat.pushFrom(hit, at, queda.empurrao() * (1 - k * 0.5), 0.5 + 0.4 * (1 - k));
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(victim, new ModPayloads.PlaySkillAnimation(victim.getId(), "", false));
        Fx.effects(level, SKILL, awakened, "impacto", at, new Vec3(0, 1, 0));
        SkillSounds.play(level, spec.sons(), "impacto", 0, 0, onde -> at, screen);
        if (queda.quebrarBlocos() && BlockRules.canBreak(level)) {
            // Stark 2026-11-25: the game froze hard on this blast, only on this skill. The crater (radius 40, 20 deep, about
            // 67 thousand blocks) went through Agarrao.crater: level.destroyBlock on every block in 3 ticks, each one with
            // neighbour updates and its own break event (particles and sound on every client). Now it is a Ruin.bowl like
            // the end-game bosses': opened from the middle out over about a second, flags 2|16 and the shared budget per
            // pulse, so it costs a little each pulse instead of everything at once. Same size and depth, with its rim lifted
            // and the middle scorched by the sun; the client throws the blocks flying (RuinNet.debris) instead of the
            // thousands of break events.
            double cr = Math.max(1, queda.raioCratera());
            Ruin.bowl(level, at.x, at.z, cr, queda.profundidadeCratera(), CRATER_TICKS, 0,
                    Ruin.opts().rim(0.22).scorch(0.45).seed((int) (at.x * 7 + at.z * 3)));
            RuinNet.debris(level, at, cr * 0.7, 14 + cr * 0.5, 0.6, (int) Math.min(220, 20 + cr * 5), Math.min(2.2, 0.9 + cr * 0.03));
            // The crater opens under it: it ends up at the bottom.
            Tasks.later(2, () -> victim.setDeltaMovement(0, -1.5, 0));
        }
    }

    // Away in space: mobs after them let go right away and again every few ticks (a new one could pick them up), and a
    // player's spot on the ground is kept in case they log out up there.
    private static void away(ServerLevel level, LivingEntity who, Vec3 ground, int ticks) {
        AWAY.put(who.getUUID(), ground);
        Tasks.add(age -> {
            if (age % 5 == 0) {
                net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(ground, ground).inflate(96, 96 + AWAY_UP, 96);
                for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, m -> m.getTarget() == who)) {
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                }
            }
            if (age >= ticks || who.isRemoved()) {
                if (who.isRemoved()) {
                    AWAY.remove(who.getUUID());
                }
                return true;
            }
            return false;
        });
    }

    private static void back(LivingEntity who) {
        AWAY.remove(who.getUUID());
        who.fallDistance = 0;
    }

    private static boolean inExpansion(LivingEntity e) {
        return com.starkinhon.battlegroundsmod.power.powers.DomainSpace.inside(e)
                || com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.open(e)
                || com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.open(e)
                || com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.around(e)
                || com.starkinhon.battlegroundsmod.power.powers.duel.DomainDuel.fighting(e);
    }

    // While they are in space nobody else sees them (0 ticks: back again). Neither of them is hidden from the two
    // screens in the cutscene, which draw them in space.
    // Measured from the spot he left (he may already be held up high).
    private static void hideFromOthers(ServerLevel level, Vec3 home, LivingEntity caster, LivingEntity victim, int ticks) {
        for (ServerPlayer player : level.players()) {
            if (player == caster || player == victim || player.distanceToSqr(home) > VIEW_DISTANCE * VIEW_DISTANCE) {
                continue;
            }
            PacketDistributor.sendToPlayer(player, new ModPayloads.HideEntity(caster.getId(), ticks));
            PacketDistributor.sendToPlayer(player, new ModPayloads.HideEntity(victim.getId(), ticks));
        }
    }

    // The top of the ground under a point, looking down up to reach blocks (the point less 40 when there is none).
    private static double ground(ServerLevel level, Vec3 from, double reach) {
        var hit = level.clip(new ClipContext(from, from.subtract(0, reach, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? from.y - 40 : hit.getLocation().y;
    }
}
