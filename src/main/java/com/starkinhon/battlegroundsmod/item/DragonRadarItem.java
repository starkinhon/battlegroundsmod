// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Radar do Dragão (remake, Stark 2026-11-20): held in either hand, its green screen shows the Esferas around
// (client/DragonRadarHud, fed once a second by universe/DragonBallWorld.radarTick). Right click is the button on top:
// it changes the zoom (60, 400 or 2500 blocks). It no longer changes how often enemies drop a ball.
public class DragonRadarItem extends Item {
    public static final int[] ZOOMS = {60, 400, 2500};
    // Client side only, set by the RadarSignals payload and by the button: what the screen shows.
    public static int[] clientSignals = new int[0];
    public static long clientSignalsAt;
    public static int clientZoom;
    public static long clientPressedAt;

    public DragonRadarItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            clientZoom = (clientZoom + 1) % ZOOMS.length;
            clientPressedAt = System.currentTimeMillis();
            player.playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 0.5f, 1.6f + clientZoom * 0.2f);
            player.displayClientMessage(Component.translatable("hud.battlegroundsmod.radar.zoom", clientZoom + 1, ZOOMS[clientZoom]), true);
        }
        // consume: no arm swing, the thumb just presses the button
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.radar_dragao.desc").withStyle(s -> s.withColor(0x8EE07A)));
    }
}
