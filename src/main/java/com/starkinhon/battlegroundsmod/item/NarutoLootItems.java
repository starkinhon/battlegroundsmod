package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.universe.NarutoActivities;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

// The items of the Naruto structures (Stark 2026-11-12, world/NarutoSites, universe/NarutoActivities): trophies and
// scrolls that only come from their activities, a few things to use.
public final class NarutoLootItems {
    private NarutoLootItems() {
    }

    private static void desc(String id, List<Component> tooltip, int color) {
        tooltip.add(Component.translatable("item.battlegroundsmod." + id + ".desc").withStyle(s -> s.withColor(color)));
    }

    // Something to keep (the bell of the bell test, the Heaven and Earth scrolls): only its line in the tooltip.
    public static class Lore extends Item {
        private final String id;
        private final int color;

        public Lore(Properties properties, String id, int color) {
            super(properties);
            this.id = id;
            this.color = color;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            desc(id, tooltip, color);
        }
    }

    // A thing used with right click: consumed or put on a cooldown by use().
    public abstract static class Usable extends Lore {
        private final int cooldown;
        private final boolean consumed;

        protected Usable(Properties properties, String id, int color, int cooldown, boolean consumed) {
            super(properties, id, color);
            this.cooldown = cooldown;
            this.consumed = consumed;
        }

        // true when it worked (the cooldown starts / one is spent)
        protected abstract boolean use(ServerPlayer player, ItemStack stack, InteractionHand hand);

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(player instanceof ServerPlayer server)) {
                return InteractionResultHolder.success(stack);
            }
            if (!use(server, stack, hand)) {
                return InteractionResultHolder.fail(stack);
            }
            if (cooldown > 0) {
                player.getCooldowns().addCooldown(this, cooldown);
            }
            if (consumed && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResultHolder.consume(stack);
        }
    }

    // Pele de Cobra Gigante: the snake changes its skin and comes out new; so does whoever wears it for a moment (the
    // bad effects go, a short regeneration).
    public static class SnakeSkin extends Usable {
        public SnakeSkin(Properties properties) {
            super(properties, "pele_cobra", 0xD8CFA0, 0, true);
        }

        @Override
        protected boolean use(ServerPlayer player, ItemStack stack, InteractionHand hand) {
            player.getActiveEffects().stream().filter(e -> !e.getEffect().value().isBeneficial()).map(MobEffectInstance::getEffect).toList()
                    .forEach(player::removeEffect);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY(1), player.getZ(), 60, 0.4, 0.8, 0.4, 0.02);
            level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1f, 0.7f);
            return true;
        }
    }

    // Célula do Hashirama: strong regeneration for 60 s.
    public static class HashiramaCell extends Usable {
        public HashiramaCell(Properties properties) {
            super(properties, "celula_hashirama", 0x7FD67A, 0, true);
        }

        @Override
        protected boolean use(ServerPlayer player, ItemStack stack, InteractionHand hand) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1200, 2));
            player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 1200, 1));
            player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY(1), player.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 1f, 0.6f);
            return true;
        }
    }

    // Bolsa de Veneno da Salamandra: spread on the weapon in the other hand, the next 8 hits poison.
    public static class PoisonPouch extends Usable {
        public static final String TAG = "souls_veneno";

        public PoisonPouch(Properties properties) {
            super(properties, "bolsa_veneno", 0x9BE05A, 0, true);
        }

        @Override
        protected boolean use(ServerPlayer player, ItemStack stack, InteractionHand hand) {
            ItemStack weapon = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (weapon.isEmpty()) {
                SubCommon.warn(player, "veneno_sem_arma");
                return false;
            }
            CustomData.update(DataComponents.CUSTOM_DATA, weapon, tag -> tag.putInt(TAG, 8));
            player.serverLevel().sendParticles(ParticleTypes.ITEM_SLIME, player.getX(), player.getY(0.7), player.getZ(), 20, 0.3, 0.2, 0.3, 0.05);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 1f, 0.6f);
            player.displayClientMessage(Component.translatable("message.battlegroundsmod.veneno_na_arma"), true);
            return true;
        }
    }

    // Asas de Papel (Konan's): 30 s gliding on paper wings (NarutoActivities makes the glide), 90 s to fold again.
    public static class PaperWings extends Usable {
        public PaperWings(Properties properties) {
            super(properties, "asas_de_papel", 0xEDE6D2, 1800, false);
        }

        @Override
        protected boolean use(ServerPlayer player, ItemStack stack, InteractionHand hand) {
            NarutoActivities.wings(player, 600);
            return true;
        }
    }

    // Pergaminho de Marionete: an allied puppet for 40 s (2 min to call it again).
    public static class PuppetScroll extends Usable {
        public PuppetScroll(Properties properties) {
            super(properties, "pergaminho_marionete", 0xB48A5A, 2400, false);
        }

        @Override
        protected boolean use(ServerPlayer player, ItemStack stack, InteractionHand hand) {
            return NarutoActivities.summonAlly(player, "marionete", 800);
        }
    }

    // Pergaminho de Invocação do Sapo: the giant warrior toad by your side for 40 s, a long wait to call it again.
    public static class ToadScroll extends Usable {
        public ToadScroll(Properties properties) {
            super(properties, "pergaminho_sapo", 0xE6A14A, 12000, false);
        }

        @Override
        protected boolean use(ServerPlayer player, ItemStack stack, InteractionHand hand) {
            return NarutoActivities.summonAlly(player, "sapo_gigante", 800);
        }
    }

    // Sharingan: 45 s seeing everything: enemies around glow, night vision, a little speed, and a third of the blows
    // that would land are seen in time and dodged (NarutoActivities). 5 min to open it again.
    public static class Sharingan extends Usable {
        public Sharingan(Properties properties) {
            super(properties, "sharingan", 0xE0352B, 6000, false);
        }

        @Override
        protected boolean use(ServerPlayer player, ItemStack stack, InteractionHand hand) {
            NarutoActivities.sharingan(player, 900);
            return true;
        }
    }

    // Kubikiribōchō, the Executioner's Blade: huge and slow, and it remakes itself with the blood it drinks (each hit
    // heals the wielder and mends the blade).
    public static class Kubikiribocho extends SwordItem {
        public Kubikiribocho(Properties properties) {
            super(Tiers.NETHERITE, properties.attributes(SwordItem.createAttributes(Tiers.NETHERITE, 7, -3.1f)));
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            attacker.heal(1.5f);
            if (stack.isDamaged()) {
                stack.setDamageValue(Math.max(0, stack.getDamageValue() - 6));
            }
            if (attacker.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
            }
            return super.hurtEnemy(stack, target, attacker);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            desc("kubikiribocho", tooltip, 0xB9C8D6);
        }
    }
}
