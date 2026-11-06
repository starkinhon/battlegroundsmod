package com.starkinhon.battlegroundsmod.power.powers.gojo;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

// Red (Vermelho), Gojo's X. For now only its look, the one Stark approved in the Skill Visualizer (aba "Gojo · Vermelho",
// "na ponta do dedo"): the Red lights up at his fingertip, its tails form one by one and spin around it, then it fades.
// It does nothing to anyone yet (the move itself comes later). The orb is drawn on the clients (client/fx/VermelhoRenderer),
// which put it on his hand themselves; this only says it is alive every tick.
public final class Vermelho {
    public static final int COOLDOWN = 40;
    // How long it shows: the tails take 50 ticks to form, then it spins until the end and fades.
    public static final int LIFE = 120;
    private static final double VIEW_DISTANCE = 160;
    public static final int ALIVE = 0;
    public static final int OVER = 1;
    private static int nextId;

    private Vermelho() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = caster.getData(ModAttachments.POWER);
        String power = data.power;
        int id = nextId++;
        caster.swing(InteractionHand.MAIN_HAND, true);
        send(level, caster, id, ALIVE);
        Tasks.add(age -> {
            boolean gone = !caster.isAlive() || caster.isRemoved() || !power.equals(caster.getData(ModAttachments.POWER).power);
            if (gone || age >= LIFE) {
                send(level, caster, id, OVER);
                return true;
            }
            send(level, caster, id, ALIVE);
            return false;
        });
        return true;
    }

    private static void send(ServerLevel level, LivingEntity caster, int id, int mode) {
        ModPayloads.Vermelho payload = new ModPayloads.Vermelho(id, caster.getId(), mode);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(caster) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
