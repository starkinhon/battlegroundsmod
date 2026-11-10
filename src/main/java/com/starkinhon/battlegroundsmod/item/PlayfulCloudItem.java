package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

// Nuvem Brincalhona (Jujutsu, Toji's three-section staff, a special grade cursed tool): pure strength, no technique.
// Each hit throws the target far back and a bit up.
public class PlayfulCloudItem extends SwordItem {
    public PlayfulCloudItem(Properties properties) {
        super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 5, -3.0f)));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        Vec3 push = target.position().subtract(attacker.position()).multiply(1, 0, 1);
        if (push.lengthSqr() > 1.0E-4) {
            push = push.normalize().scale(1.8);
            target.push(push.x, 0.45, push.z);
            target.hurtMarked = true;
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.battlegroundsmod.nuvem_brincalhona.desc").withStyle(s -> s.withColor(0xD8D0B0)));
    }
}
