package com.starkinhon.battlegroundsmod.power.powers.gojo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.DominioSpec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// V (ult): Expansão de Domínio: Vazio Infinito (gojo.dominio.json, see DominioSpec). Gojo makes the hand sign, then the
// domain bursts out of him: everyone within dominio.raio blocks (except him) is paralyzed by infinite information until
// it ends, including anyone who walks in later. The look (shell, void, eye, cracks) is all on the clients
// (client/fx/DomainScene); the cutscene of the sign is the editor's file gojo.dominio (data/.../souls_cutscenes).
// If Gojo falls, the domain cracks open early.
public final class InfiniteVoid {
    public static final String SKILL = DominioSpec.SKILL;
    // Players this far away get the domain, so they see the black sphere from outside.
    private static final double VIEW_DISTANCE = 160;
    // Those caught are held in short stretches, so they go free the moment it ends (or Gojo falls).
    private static final int HOLD_STEP = 10;

    private InfiniteVoid() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean awakened = data.awakened;
        DominioSpec spec = DominioSpec.get(awakened);
        Vec3 center = caster.position();
        double radius = Math.max(2, spec.dominio().raio());
        int full = spec.fullTick();
        int close = spec.closeTick();

        data.busyTicks = full;
        if (spec.dominio().pararUsuario()) {
            Freeze.hold(caster, full);
        }
        send(level, center, new ModPayloads.GojoDomain(caster.getId(), center, awakened, false));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));

        List<ServerPlayer> screen = new ArrayList<>();
        if (caster instanceof ServerPlayer player) {
            screen.add(player);
        }
        Function<String, Vec3> where = onde -> caster.isAlive() ? caster.position().add(0, 1, 0) : center.add(0, 1, 0);
        SkillSounds.play(level, spec.sons(), "uso", 0, spec.expandTick(), where, screen);
        SkillSounds.play(level, spec.sons(), "expandir", spec.expandTick(), spec.dominio().expandirTicks(), where, screen);

        Tasks.add(age -> {
            boolean gone = !caster.isAlive() || caster.isRemoved() || caster.level() != level;
            if (age < full) {
                if (gone) {
                    send(level, center, new ModPayloads.GojoDomain(caster.getId(), center, awakened, true));
                    return true;
                }
                return false;
            }
            if (age == full) {
                SkillSounds.play(level, spec.sons(), "acerto", 0, close - full, where, screen);
            }
            if (gone || age >= close) {
                if (gone) {
                    send(level, center, new ModPayloads.GojoDomain(caster.getId(), center, awakened, true));
                }
                SkillSounds.play(level, spec.sons(), "fechar", 0, spec.closeTicks(), onde -> center.add(0, 1, 0), screen);
                return true;
            }
            DominioSpec.Acerto acerto = spec.acerto();
            int step = age - full;
            for (LivingEntity victim : Combat.inRadius(level, center, radius, caster)) {
                if (acerto.paralisar() && step % HOLD_STEP == 0) {
                    // A tick of overlap so they never slip free in between.
                    Freeze.hold(victim, Math.min(HOLD_STEP + 1, close - age));
                }
                if (acerto.dano() > 0 && acerto.aCadaTicks() > 0 && step % acerto.aCadaTicks() == 0) {
                    Combat.hurt(caster, victim, (float) acerto.dano());
                }
            }
            return false;
        });
        return true;
    }

    private static void send(ServerLevel level, Vec3 center, ModPayloads.GojoDomain payload) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
