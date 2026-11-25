// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.power.powers.hashirama.Hashirama;
import com.starkinhon.battlegroundsmod.power.powers.hashirama.HsBuddha;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// Hashirama Senju, the God of Shinobi (Stark, 2026-11-13): the first end-game boss, the one "above" the divine souls in
// the Vale do Fim. His power never becomes a soul (dropAlma false). Phase 1 is the Mokuton (forest, stakes, prison,
// lance, flowers, dome, the dragon); at 60% life he takes Sage Mode (the red marks, synced here so the skin changes) and
// summons the Buddha of a Thousand Hands, standing on its head while its arms slap the ground (HsBuddha).
public class HashiramaEntity extends SoulBoss {
    private static final EntityDataAccessor<Boolean> DATA_SAGE = SynchedEntityData.defineId(HashiramaEntity.class, EntityDataSerializers.BOOLEAN);
    // Substituição de Madeira: until when (game time) his body is wood (the renderer swaps the skin).
    private static final EntityDataAccessor<Integer> DATA_WOOD = SynchedEntityData.defineId(HashiramaEntity.class, EntityDataSerializers.INT);

    private HashiramaTactics tactics;
    private final java.util.ArrayDeque<Long> hits = new java.util.ArrayDeque<>();
    // The stun he takes when he touches the ground after the Buddha (HsBuddha.end): spent from up there, it is the opening
    // for whoever survived the hands. Waits for the landing so it is not lost in the 40-block fall.
    private int landStun;

    public HashiramaEntity(EntityType<? extends HashiramaEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SAGE, false);
        builder.define(DATA_WOOD, 0);
    }

    public boolean sage() {
        return entityData.get(DATA_SAGE);
    }

    public void setSage(boolean on) {
        entityData.set(DATA_SAGE, on);
    }

    public void woodFor(int ticks) {
        entityData.set(DATA_WOOD, (int) level().getGameTime() + ticks);
    }

    public boolean wood() {
        return (int) level().getGameTime() < entityData.get(DATA_WOOD);
    }

    // The Buddha fell apart or dissolved: he comes down (gravity back on in HsBuddha) and summons again later.
    public void buddhaEnded() {
        if (tactics != null) {
            tactics.buddhaEnded();
        }
    }

    public void stunOnLanding(int ticks) {
        landStun = Math.max(landStun, ticks);
    }

    // How many blows he took in the last 1.2 s (the Cúpula answers whoever is glued to him hitting, like Freeza's Onda).
    public int recentHits() {
        long now = level().getGameTime();
        while (!hits.isEmpty() && now - hits.peekFirst() > 24) {
            hits.pollFirst();
        }
        return hits.size();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide()) {
            if (source.getEntity() != null && source.getEntity() != this) {
                hits.addLast(level().getGameTime());
                if (hits.size() > 16) {
                    hits.pollFirst();
                }
            }
            // Wood armor (Stark 2026-11-22, "apanha feito patinho"): half the damage while he casts a jutsu, like Freeza.
            if (powerData().busyTicks > 0 && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) {
                amount *= 0.5f;
            }
            // The wooden dome takes the blow (and the first one arms its counter).
            if (!source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL) && Hashirama.domeHit(this, source)) {
                return false;
            }
            if (source.is(DamageTypes.FALL) && HsBuddha.active(this)) {
                return false;
            }
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && source.getEntity() != null && source.getEntity() != this) {
            HsBuddha.hitOnTop(this);
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            Vec3 p = HsBuddha.perch(this);
            if (p != null) {
                setPos(p.x, p.y, p.z);
                setDeltaMovement(Vec3.ZERO);
                fallDistance = 0;
                getNavigation().stop();
            } else if (landStun > 0 && (onGround() || isInWater())) {
                com.starkinhon.battlegroundsmod.combat.Stun.apply(this, landStun, true);
                landStun = 0;
            }
        }
    }

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new HashiramaTactics(this);
        }
        return tactics;
    }

    // The rare loot, only for the world fight (EndBossLoot).
    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        EndBossLoot.drop(this, level, source, 1500,
                new net.minecraft.world.item.ItemStack(com.starkinhon.battlegroundsmod.ModItems.HASHIRAMA_CELL.get(), 3),
                new net.minecraft.world.item.ItemStack(com.starkinhon.battlegroundsmod.ModItems.SOLDIER_PILL.get(), 4),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, 2));
    }

    // No flying in his fight (Stark 2026-11-20): the Mokuton fight is on the ground.
    @Override
    public String flightLock() {
        return "message.battlegroundsmod.flight_lock.hashirama";
    }

    @Override
    public String bossId() {
        return "hashirama";
    }
}
