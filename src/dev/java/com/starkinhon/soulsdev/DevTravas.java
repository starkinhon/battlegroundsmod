package com.starkinhon.soulsdev;

import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// As travas de teste. Cada modo é uma tag na entidade (sobrevive a salvar/abrir o mundo e funciona em seletor:
// @e[tag=soulsdev.prender]):
//  - prender: parado no lugar, sem IA, sem alvo, não causa dano em ninguém. Continua "vivo" (animação, piscar de
//    dano, efeitos das skills nele), só não sai do lugar nem com knockback. É o "travar" padrão.
//  - estatua: congelado de verdade, nem o tick roda (nada anima, nada dele avança). Ainda toma dano e morre.
//  - passivo: anda e anima normal, mas nunca pega alvo nem causa dano.
//  - imortal: toma dano normal (barra desce, efeitos acontecem) mas 1 s depois do último golpe volta com vida cheia, e
//    nunca morre. Combina com os outros.
public final class DevTravas {
    public static final String PRENDER = "soulsdev.prender";
    public static final String ESTATUA = "soulsdev.estatua";
    public static final String PASSIVO = "soulsdev.passivo";
    public static final String IMORTAL = "soulsdev.imortal";
    public static final Set<String> MODOS = Set.of(PRENDER, ESTATUA, PASSIVO, IMORTAL);

    private static final String DADOS = "soulsdev";
    private static final int CURA_DEPOIS = 20;

    private DevTravas() {
    }

    public static String tag(String modo) {
        return "soulsdev." + modo;
    }

    public static boolean preso(Entity entity) {
        Set<String> tags = entity.getTags();
        return tags.contains(PRENDER) || tags.contains(ESTATUA);
    }

    public static boolean inofensivo(Entity entity) {
        return entity != null && (preso(entity) || entity.getTags().contains(PASSIVO));
    }

    public static boolean temAlguma(Entity entity) {
        for (String tag : entity.getTags()) {
            if (MODOS.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private static CompoundTag dados(Entity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(DADOS)) {
            root.put(DADOS, new CompoundTag());
        }
        return root.getCompound(DADOS);
    }

    // Liga um modo. prender/estatua/passivo se substituem (só um por vez); imortal soma com qualquer um.
    public static void aplicar(LivingEntity entity, String modo) {
        if (!IMORTAL.equals(modo)) {
            boolean imortal = entity.getTags().contains(IMORTAL);
            soltar(entity);
            if (imortal) {
                entity.addTag(IMORTAL);
            }
        }
        entity.addTag(modo);
        CompoundTag d = dados(entity);
        if (PRENDER.equals(modo) || ESTATUA.equals(modo)) {
            d.putDouble("x", entity.getX());
            d.putDouble("y", entity.getY());
            d.putDouble("z", entity.getZ());
            entity.setDeltaMovement(Vec3.ZERO);
            if (entity instanceof Mob mob) {
                if (!d.contains("noAi")) {
                    d.putBoolean("noAi", mob.isNoAi());
                }
                mob.setNoAi(true);
                mob.getNavigation().stop();
            }
        }
        if (!IMORTAL.equals(modo) && entity instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    // Tira todos os modos e devolve a IA como estava. Devolve se havia algo pra soltar.
    public static boolean soltar(Entity entity) {
        boolean tinha = temAlguma(entity);
        for (String modo : MODOS) {
            entity.removeTag(modo);
        }
        CompoundTag root = entity.getPersistentData();
        if (root.contains(DADOS)) {
            CompoundTag d = root.getCompound(DADOS);
            if (d.contains("noAi") && entity instanceof Mob mob) {
                mob.setNoAi(d.getBoolean("noAi"));
            }
            root.remove(DADOS);
        }
        entity.setDeltaMovement(Vec3.ZERO);
        return tinha;
    }

    public static String descrever(Entity entity) {
        StringBuilder modos = new StringBuilder();
        for (String tag : entity.getTags()) {
            if (MODOS.contains(tag)) {
                modos.append(modos.isEmpty() ? "" : ",").append(tag.substring("soulsdev.".length()));
            }
        }
        return modos.isEmpty() ? "solto" : modos.toString();
    }

    private static void fixar(Entity entity) {
        CompoundTag d = dados(entity);
        if (!d.contains("x")) {
            d.putDouble("x", entity.getX());
            d.putDouble("y", entity.getY());
            d.putDouble("z", entity.getZ());
        }
        double x = d.getDouble("x");
        double y = d.getDouble("y");
        double z = d.getDouble("z");
        if (entity.getX() != x || entity.getY() != y || entity.getZ() != z) {
            entity.setPos(x, y, z);
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void antesDoTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || entity.getTags().isEmpty() || !temAlguma(entity)) {
            return;
        }
        Set<String> tags = entity.getTags();
        if (tags.contains(IMORTAL) && entity instanceof LivingEntity living && living.isAlive()
                && living.getHealth() < living.getMaxHealth()) {
            long hit = dados(entity).getLong("hit");
            if (entity.level().getGameTime() - hit >= CURA_DEPOIS) {
                living.setHealth(living.getMaxHealth());
            }
        }
        if (preso(entity)) {
            fixar(entity);
            if (entity instanceof Mob mob) {
                // Uma skill (Freeze etc.) pode ter devolvido a IA no meio: trava de novo.
                mob.setNoAi(true);
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            if (tags.contains(ESTATUA) && entity.isAlive()) {
                // Morto ainda precisa do tick pra sumir.
                event.setCanceled(true);
            }
        } else if (tags.contains(PASSIVO) && entity instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void depoisDoTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide() && !entity.getTags().isEmpty() && preso(entity)) {
            fixar(entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void alvo(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() != null && inofensivo(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // Quem está travado/passivo não machuca ninguém, nem com skill que já estava no ar.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dano(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        Entity dono = source.getEntity();
        Entity direto = source.getDirectEntity();
        if ((dono != null && dono != event.getEntity() && inofensivo(dono))
                || (direto != null && direto != event.getEntity() && inofensivo(direto))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void tomouDano(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide() && entity.getTags().contains(IMORTAL)) {
            dados(entity).putLong("hit", entity.level().getGameTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void morte(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide() && entity.getTags().contains(IMORTAL)) {
            event.setCanceled(true);
            entity.setHealth(entity.getMaxHealth());
            SoulsDev.log("imortal segurou a morte de " + entity.getName().getString());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void knockback(LivingKnockBackEvent event) {
        if (preso(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
