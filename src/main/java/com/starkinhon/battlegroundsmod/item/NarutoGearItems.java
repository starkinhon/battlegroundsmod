package com.starkinhon.battlegroundsmod.item;

import java.util.List;

import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.universe.NarutoGear;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// The ninja gear Stark approved on 2026-11-12 (thread Itens de Naruto): Shuriken, Kunai, Papel-Bomba, Cabaça de Areia,
// Leque Gigante, Manto da Akatsuki and Pergaminho de Selamento. Their passives live in universe/NarutoGear.
public final class NarutoGearItems {
    private NarutoGearItems() {
    }

    private static void desc(String id, List<Component> tooltip, int color) {
        tooltip.add(Component.translatable("item.battlegroundsmod." + id + ".desc").withStyle(s -> s.withColor(color)));
    }

    // Throws one (NinjaThrown) from the hand, a short wait between throws.
    static InteractionResultHolder<ItemStack> throwOne(Item item, Level level, Player player, InteractionHand hand, float speed, int cooldown) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            NinjaThrown t = new NinjaThrown(level, player, stack);
            t.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, speed, 0.6f);
            level.addFreshEntity(t);
            level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.6f, 1.6f + level.random.nextFloat() * 0.3f);
        }
        player.getCooldowns().addCooldown(item, cooldown);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // Shuriken: right click throws one, fast and light (8 on the mod's scale), and it falls where it hit to be picked up.
    public static class Shuriken extends Item {
        public Shuriken(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            return throwOne(this, level, player, hand, 2.4f, 4);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            desc("shuriken", tooltip, 0xB9C2CE);
        }
    }

    // Kunai: a quick knife in the hand, and right click throws it (14 on the mod's scale).
    // Plain Item (not SwordItem): a sword tier adds durability, which forces the stack to 1.
    public static class Kunai extends Item {
        public Kunai(Properties properties) {
            super(properties.attributes(SwordItem.createAttributes(Tiers.IRON, 0, -1.8f)));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            return throwOne(this, level, player, hand, 2.1f, 10);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            desc("kunai", tooltip, 0xB9C2CE);
        }
    }

    // Papel-Bomba: thrown, it sticks to the block or the body it hits and blows 2 s later. It breaks blocks only with the
    // mod's destruction on (BlockRules).
    public static class PaperBomb extends Item {
        public PaperBomb(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            return throwOne(this, level, player, hand, 1.4f, 20);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            desc("papel_bomba", tooltip, 0xE0533F);
        }
    }

    // Cabaça de Areia: carried anywhere in the inventory it rides on the back (client GourdLayer) and its sand stops one
    // blow every 20 s; Gaara's soul with it gets bigger sand (NarutoGear, Gaara, NrCommon).
    public static class Gourd extends Item {
        public Gourd(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            desc("cabaca_gaara", tooltip, 0xD9B878);
            tooltip.add(Component.translatable("item.battlegroundsmod.cabaca_gaara.gaara").withStyle(s -> s.withColor(0xC9A76B)));
        }
    }

    // Leque Gigante (Temari's): right click, a blast of wind in front: whoever is there is thrown far away, the user is
    // pushed back. 6 s to swing it again.
    public static class Fan extends SwordItem {
        public Fan(Properties properties) {
            super(Tiers.IRON, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 1, -2.8f)));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                Vec3 look = player.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                Vec3 eye = player.getEyePosition();
                int hit = 0;
                for (LivingEntity t : Combat.inCone(player, 10, 0.55)) {
                    double d = t.position().distanceTo(player.position());
                    double k = 1 - d / 12;
                    Combat.hurtBasic(player, t, 10f);
                    Combat.push(t, flat, 2.4 * k + 0.8, 0.55 + 0.3 * k);
                    hit++;
                }
                // the wind itself: gusts along the cone
                for (int i = 1; i <= 9; i++) {
                    Vec3 p = eye.add(look.scale(i * 1.1)).add(0, -0.4, 0);
                    server.sendParticles(i % 3 == 0 ? ParticleTypes.GUST : ParticleTypes.SMALL_GUST, p.x, p.y, p.z, 1 + i / 3, 0.25 * i * 0.3, 0.3, 0.25 * i * 0.3, 0);
                }
                server.playSound(null, player.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
                server.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.8f, 0.9f);
                Combat.launchSelf(sp, flat.scale(-0.9).add(0, 0.25, 0));
                player.getCooldowns().addCooldown(this, 120);
                if (hit == 0) {
                    player.fallDistance = 0;
                }
            }
            player.swing(hand, true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            desc("leque_temari", tooltip, 0xB28BD0);
        }
    }

    // Manto da Akatsuki: worn, the common Naruto creatures only notice you from up close (NarutoGear).
    public static class Cloak extends ArmorItem {
        public Cloak(Properties properties) {
            super(NarutoGear.AKATSUKI, ArmorItem.Type.CHESTPLATE, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            desc("manto_akatsuki", tooltip, 0xD8333A);
        }
    }

    // Pergaminho de Selamento: right click a weak, hurt creature to seal it inside; right click again to let it out as an
    // ally for 30 s (then it goes). Nothing seals a boss, a player or a creature that is still strong.
    public static class SealScroll extends Item {
        public SealScroll(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
            if (!(player instanceof ServerPlayer sp)) {
                return InteractionResult.SUCCESS;
            }
            // the stack in the hand (in creative the passed one is a copy)
            return NarutoGear.seal(sp, player.getItemInHand(hand), target) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(player instanceof ServerPlayer sp)) {
                return InteractionResultHolder.success(stack);
            }
            if (!NarutoGear.sealed(stack)) {
                SubCommon.warn(sp, "selo_vazio");
                return InteractionResultHolder.fail(stack);
            }
            return NarutoGear.release(sp, stack) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return NarutoGear.sealed(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            desc("pergaminho_selamento", tooltip, 0xE0B070);
            Component inside = NarutoGear.sealedName(stack);
            if (inside != null) {
                tooltip.add(Component.translatable("item.battlegroundsmod.pergaminho_selamento.dentro", inside).withStyle(s -> s.withColor(0xFF7A5A)));
            }
        }
    }

    // Used by NarutoGear.
    static boolean isGourd(ItemStack s) {
        return s.is(ModItems.SAND_GOURD.get());
    }
}
