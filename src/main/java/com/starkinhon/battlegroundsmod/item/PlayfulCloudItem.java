package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;

// Nuvem Brincalhona (Jujutsu, Toji's three-section staff, a special grade cursed tool): pure strength, no technique.
// Not a sword: fights with hand-to-hand "golpes" moves at 2x normal reach, and each hit throws the target far back.
public class PlayfulCloudItem extends Item {
    public static final double REACH_MULT = 2.0;
    private static final ResourceLocation REACH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            BattlegroundsMod.MODID, "playful_cloud_reach");

    public PlayfulCloudItem(Properties properties) {
        super(properties.stacksTo(1).attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                        BASE_ATTACK_DAMAGE_ID, 9.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(
                        BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(
                        REACH_MODIFIER_ID, 3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build()));
    }

    public static boolean held(LivingEntity entity) {
        return entity != null && entity.getMainHandItem().getItem() instanceof PlayfulCloudItem;
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
