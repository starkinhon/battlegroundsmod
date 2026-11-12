package com.starkinhon.battlegroundsmod.universe;

import java.util.EnumMap;
import java.util.List;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// The passives of the ninja gear Stark approved on 2026-11-12 (thread Itens de Naruto; the items are item/NarutoGearItems):
// - Manto da Akatsuki: worn, the common Naruto creatures only notice you from up close (unless you hit them);
// - Cabaça de Areia: carried, its sand stops a blow (NrCommon's sand guard); Gaara's soul with it has bigger sand
//   (gourdBoost, read by Gaara);
// - Pergaminho de Selamento: seals a weak, hurt creature and lets it out later as an ally for 30 s;
// - Kusanagi in the inventory: Sasuke's soul hits 25% harder (NrCommon.damageMultiplier);
// - Sharingan: an Uchiha's soul (Sasuke, Madara) keeps it open twice as long and dodges half the blows (NarutoActivities).
public final class NarutoGear {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, BattlegroundsMod.MODID);

    // No defense of its own (life and defense come from the tier): the cloak is worn for what it hides.
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> AKATSUKI = ARMOR_MATERIALS.register("manto_akatsuki",
            () -> new ArmorMaterial(Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                for (ArmorItem.Type t : ArmorItem.Type.values()) {
                    m.put(t, 0);
                }
            }), 12, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.BLACK_WOOL),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "manto_akatsuki"))), 0f, 0f));

    private static final String SEALED = "souls_selado";
    private static final String SEALED_NAME = "souls_selado_nome";
    private static final int ALLY_TICKS = 600;

    private NarutoGear() {
    }

    public static void register(IEventBus modBus) {
        ARMOR_MATERIALS.register(modBus);
    }

    public static void registerGame() {
        NeoForge.EVENT_BUS.addListener(NarutoGear::onTarget);
    }

    // ---------------------------------------------------------------- souls

    private static String power(LivingEntity e) {
        return e instanceof Player p ? p.getData(ModAttachments.POWER).power : "";
    }

    public static boolean uchiha(LivingEntity e) {
        String p = power(e);
        return "sasuke".equals(p) || "uchiha_ghost".equals(p);
    }

    private static boolean carries(LivingEntity e, Item item) {
        return e instanceof Player p && p.getInventory().contains(new ItemStack(item));
    }

    public static boolean hasGourd(LivingEntity e) {
        return carries(e, ModItems.SAND_GOURD.get());
    }

    // Gaara's soul carrying the gourd: his sand reaches 30% farther and covers 30% more.
    public static double gourdBoost(LivingEntity caster) {
        return "gaara".equals(power(caster)) && hasGourd(caster) ? 1.3 : 1;
    }

    // Sasuke's soul with the Kusanagi anywhere in the inventory.
    public static float kusanagiBoost(LivingEntity attacker) {
        return "sasuke".equals(power(attacker)) && carries(attacker, ModItems.KUSANAGI.get()) ? 1.25f : 1f;
    }

    // ---------------------------------------------------------------- Manto da Akatsuki, and the sealed allies

    private static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null || mob.level().isClientSide) {
            return;
        }
        // an ally let out of the scroll never turns on its owner
        if (NarutoActivities.isAllyOf(mob, target)) {
            event.setCanceled(true);
            return;
        }
        if (mob instanceof NarutoMob && target instanceof Player p && p.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.AKATSUKI_CLOAK.get())
                && mob.getLastHurtByMob() != p) {
            double follow = mob.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE) ? mob.getAttributeValue(Attributes.FOLLOW_RANGE) : 16;
            if (mob.distanceTo(p) > Math.max(4, follow * 0.35)) {
                event.setCanceled(true);
            }
        }
    }

    // ---------------------------------------------------------------- Pergaminho de Selamento

    public static boolean sealed(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d != null && d.contains(SEALED);
    }

    public static Component sealedName(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null || !d.contains(SEALED)) {
            return null;
        }
        return Component.translatable(d.copyTag().getString(SEALED_NAME));
    }

    // Seals the creature (it has to be a common one, already beaten down to 40% of its life). False when it can't.
    public static boolean seal(ServerPlayer player, ItemStack stack, LivingEntity target) {
        if (sealed(stack)) {
            SubCommon.warn(player, "selo_cheio");
            return false;
        }
        if (!(target instanceof Mob) || target instanceof Player || target instanceof SoulBoss || target.getType().is(Tags.EntityTypes.BOSSES)
                || target.getTags().contains("souls_naruto_aliado") || target.getMaxHealth() > 80) {
            SubCommon.warn(player, "selo_nao_cabe");
            return false;
        }
        if (target.getHealth() > target.getMaxHealth() * 0.4f) {
            SubCommon.warn(player, "selo_forte");
            return false;
        }
        CompoundTag tag = new CompoundTag();
        if (!target.save(tag)) {
            SubCommon.warn(player, "selo_nao_cabe");
            return false;
        }
        tag.remove("UUID");
        tag.remove("Passengers");
        String name = target.getType().getDescriptionId();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.put(SEALED, tag);
            t.putString(SEALED_NAME, name);
        });
        ServerLevel level = player.serverLevel();
        Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
        level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 50, 0.5, 0.6, 0.5, 0.04);
        level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y, c.z, 80, 0.6, 0.8, 0.6, 0.6);
        level.playSound(null, target.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.6f, 0.6f);
        level.playSound(null, target.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, 1.4f);
        target.discard();
        player.displayClientMessage(Component.translatable("message.battlegroundsmod.selo_selou", Component.translatable(name)), true);
        return true;
    }

    // Lets the sealed creature out in front of the player, healed, fighting for them for 30 s.
    public static boolean release(ServerPlayer player, ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) {
            return false;
        }
        CompoundTag tag = d.copyTag().getCompound(SEALED);
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(2));
        Entity e = EntityType.loadEntityRecursive(tag, level, x -> {
            x.moveTo(at.x, player.getY(), at.z, player.getYRot(), 0);
            return x;
        });
        if (e == null) {
            SubCommon.warn(player, "naruto.invocacao_falhou");
            return false;
        }
        if (e instanceof LivingEntity l) {
            l.setHealth(l.getMaxHealth());
        }
        level.addFreshEntity(e);
        NarutoActivities.adoptAlly(player, e, ALLY_TICKS);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.remove(SEALED);
            t.remove(SEALED_NAME);
        });
        if (stack.has(DataComponents.CUSTOM_DATA) && stack.get(DataComponents.CUSTOM_DATA).isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        }
        player.getCooldowns().addCooldown(stack.getItem(), 100);
        level.playSound(null, e.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.2f, 1.1f);
        return true;
    }
}
