package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Tasks;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// Boneco Amaldiçoado (Jujutsu, Principal Yaga's cursed corpses): right click and a big cursed doll stands up beside the
// player, fights every monster around for 60 s and falls apart. Used up. Placeholder body: an iron golem.
public class CursedDollItem extends Item {
    private static final int LIFE = 1200;

    public CursedDollItem(Properties properties) {
        super(properties.stacksTo(4));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            IronGolem doll = EntityType.IRON_GOLEM.create(server);
            if (doll == null) {
                return InteractionResultHolder.fail(stack);
            }
            var look = player.getLookAngle();
            doll.moveTo(player.getX() + look.x * 2, player.getY(), player.getZ() + look.z * 2, player.getYRot(), 0);
            doll.setPlayerCreated(true);
            doll.setCustomName(Component.translatable("entity.battlegroundsmod.boneco_amaldicoado"));
            server.addFreshEntity(doll);
            Fx.burst(server, ParticleTypes.SQUID_INK, doll.position().add(0, 1, 0), 40, 0.6, 0.05);
            Fx.sound(server, doll.position(), SoundEvents.IRON_GOLEM_REPAIR, 1f, 0.6f);
            Tasks.later(LIFE, () -> {
                if (doll.isAlive()) {
                    Fx.burst((ServerLevel) doll.level(), ParticleTypes.SQUID_INK, doll.position().add(0, 1, 0), 40, 0.6, 0.05);
                    doll.discard();
                }
            });
            player.getCooldowns().addCooldown(this, 200);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.boneco_amaldicoado.desc").withStyle(s -> s.withColor(0xA88AD8)));
    }
}
