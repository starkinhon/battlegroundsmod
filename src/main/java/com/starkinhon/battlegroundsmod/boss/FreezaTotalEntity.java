package com.starkinhon.battlegroundsmod.boss;

import java.util.ArrayDeque;
import java.util.Deque;

import com.starkinhon.battlegroundsmod.power.powers.freezaboss.FreezaTotal;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Freeza at 100% (Stark, 2026-11-13): the second end-game boss, the one above the Freeza soul. His power never becomes
// a soul (dropAlma false). Phase 1 is his 50% (Kienzan Duplo, the island, the Rajada Mortal, the tail blitz, the Onda
// Imperial for whoever stays glued to him); at half his life he goes to 100%: the muscles swell (synced here, the
// renderer swells the body and changes the skin), Namek starts to fall apart and a 5:00 clock runs on the bar while his
// power falls (FreezaTotal.Total).
public class FreezaTotalEntity extends SoulBoss {
    private static final EntityDataAccessor<Float> DATA_BULK = SynchedEntityData.defineId(FreezaTotalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_WHIP = SynchedEntityData.defineId(FreezaTotalEntity.class, EntityDataSerializers.INT);

    private FreezaTotalTactics tactics;
    private long armorUntil;
    private final Deque<Long> hits = new ArrayDeque<>();
    private int clockShown = -1;

    public FreezaTotalEntity(EntityType<? extends FreezaTotalEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BULK, 0f);
        builder.define(DATA_WHIP, -1000);
    }

    // 0 at 50%, 1 at 100% (the muscles).
    public float bulk() {
        return entityData.get(DATA_BULK);
    }

    public void setBulk(float k) {
        entityData.set(DATA_BULK, Math.max(0, Math.min(1, k)));
    }

    // The tail whip starts now (the renderer sweeps the tail for 0.32 s).
    public void whip() {
        entityData.set(DATA_WHIP, (int) level().getGameTime());
    }

    public int whipAt() {
        return entityData.get(DATA_WHIP);
    }

    // Half the damage while he winds a move up.
    public void armor(int ticks) {
        armorUntil = Math.max(armorUntil, level().getGameTime() + ticks);
    }

    // How many blows he took in the last 1.2 s (the Onda Imperial answers whoever is glued to him hitting).
    public int recentHits() {
        long now = level().getGameTime();
        while (!hits.isEmpty() && now - hits.peekFirst() > 24) {
            hits.pollFirst();
        }
        return hits.size();
    }

    // "Namek explodes in m:ss" on the bar.
    public void clock(int seconds) {
        if (seconds == clockShown) {
            return;
        }
        clockShown = seconds;
        setBarName(seconds <= 0 ? Component.translatable("boss.battlegroundsmod.freeza_total.esgotado")
                : Component.translatable("boss.battlegroundsmod.freeza_total.relogio", seconds / 60, String.format("%02d", seconds % 60)));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide()) {
            if (level().getGameTime() < armorUntil) {
                amount *= 0.5f;
            }
            // at 0:00 his body can't take it any more
            if (FreezaTotal.spent(this)) {
                amount *= 1.25f;
            }
            if (source.getEntity() != null && source.getEntity() != this) {
                hits.addLast(level().getGameTime());
                if (hits.size() > 16) {
                    hits.pollFirst();
                }
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide()) {
            FreezaTotal.end(this);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            FreezaTotal.end(this);
        }
        super.remove(reason);
    }

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new FreezaTotalTactics(this);
        }
        return tactics;
    }

    @Override
    public String bossId() {
        return "freeza_total";
    }
}
