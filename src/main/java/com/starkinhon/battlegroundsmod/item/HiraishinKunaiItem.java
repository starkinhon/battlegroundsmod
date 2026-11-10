package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// Kunai do Hiraishin (Naruto, the Fourth Hokage's): Shift + right click leaves the seal where the player stands; right
// click flashes them back to it from up to 300 blocks, in the same world. Plant it before a fight, escape or come back
// behind the enemy. 8 s recharge. Also a quick blade.
public class HiraishinKunaiItem extends SwordItem {
    private static final String TAG = "souls_hiraishin";
    private static final double RANGE = 300;
    private static final DustParticleOptions FLASH = new DustParticleOptions(new Vector3f(1f, 0.85f, 0.2f), 1.6f);

    public HiraishinKunaiItem(Properties properties) {
        super(Tiers.IRON, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 0, -1.6f)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            CompoundTag data = server.getPersistentData();
            ServerLevel here = server.serverLevel();
            if (player.isShiftKeyDown()) {
                CompoundTag mark = new CompoundTag();
                mark.putDouble("x", player.getX());
                mark.putDouble("y", player.getY());
                mark.putDouble("z", player.getZ());
                mark.putString("dim", here.dimension().location().toString());
                data.put(TAG, mark);
                server.displayClientMessage(Component.translatable("message.battlegroundsmod.hiraishin_marked"), true);
                ring(here, player.position());
                Fx.sound(here, player.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 1.6f);
                return InteractionResultHolder.success(stack);
            }
            if (!data.contains(TAG)) {
                server.displayClientMessage(Component.translatable("message.battlegroundsmod.hiraishin_none"), true);
                return InteractionResultHolder.fail(stack);
            }
            CompoundTag mark = data.getCompound(TAG);
            Vec3 to = new Vec3(mark.getDouble("x"), mark.getDouble("y"), mark.getDouble("z"));
            if (!mark.getString("dim").equals(here.dimension().location().toString()) || to.distanceTo(player.position()) > RANGE) {
                server.displayClientMessage(Component.translatable("message.battlegroundsmod.hiraishin_far"), true);
                return InteractionResultHolder.fail(stack);
            }
            ring(here, player.position());
            server.teleportTo(to.x, to.y, to.z);
            server.fallDistance = 0;
            ring(here, to);
            Fx.sound(here, to, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.8f);
            player.getCooldowns().addCooldown(this, 160);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void ring(ServerLevel level, Vec3 at) {
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2;
            level.sendParticles(FLASH, at.x + Math.cos(a) * 0.9, at.y + 0.1, at.z + Math.sin(a) * 0.9, 1, 0, 0.6, 0, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.kunai_hiraishin.desc").withStyle(s -> s.withColor(0xFFD84A)));
    }
}
