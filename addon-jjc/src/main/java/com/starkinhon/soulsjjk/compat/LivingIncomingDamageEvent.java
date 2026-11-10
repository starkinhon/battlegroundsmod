package com.starkinhon.soulsjjk.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

// NeoForge's LivingIncomingDamageEvent (cancel + change the amount), backed by Forge's LivingHurtEvent.
public final class LivingIncomingDamageEvent {
    private final LivingHurtEvent event;

    public LivingIncomingDamageEvent(LivingHurtEvent event) {
        this.event = event;
    }

    public LivingEntity getEntity() {
        return event.getEntity();
    }

    public DamageSource getSource() {
        return event.getSource();
    }

    public float getAmount() {
        return event.getAmount();
    }

    public void setAmount(float amount) {
        event.setAmount(amount);
    }

    public boolean isCanceled() {
        return event.isCanceled();
    }

    public void setCanceled(boolean canceled) {
        event.setCanceled(canceled);
    }
}
