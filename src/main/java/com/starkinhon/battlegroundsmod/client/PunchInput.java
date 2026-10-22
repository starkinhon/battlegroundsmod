package com.starkinhon.battlegroundsmod.client;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.skill.ComboSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// In combat mode, left click with an empty hand throws the next punch of the combo, also into the air (vanilla only
// attacks when the crosshair is on someone). Out of combat mode left click is vanilla. The server picks the target. Clicking a block still mines it, unless
// someone is standing in front, closer than the block.
public final class PunchInput {
    private PunchInput() {
    }

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!event.isAttack() || event.getHand() != InteractionHand.MAIN_HAND || player == null || player.isSpectator()
                || !player.getMainHandItem().isEmpty() || !player.getData(ModAttachments.POWER).hasPower()
                || !player.getData(ModAttachments.POWER).combat) {
            return;
        }
        HitResult hit = minecraft.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK
                && !someoneAhead(player, hit.getLocation().distanceTo(player.getEyePosition()))) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(new ModPayloads.Punch(false));
    }

    private static boolean someoneAhead(LocalPlayer player, double blockDistance) {
        ComboSpec spec = ComboSpec.get();
        double range = Math.min(blockDistance, Math.max(spec.alcance(), spec.passo().distanciaMax()));
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(range + 1))) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
                continue;
            }
            Vec3 to = entity.position().add(0, entity.getBbHeight() / 2, 0).subtract(eye);
            if (to.length() <= range + entity.getBbWidth() && to.normalize().dot(look) >= spec.passo().cone()) {
                return true;
            }
        }
        return false;
    }
}
