package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.SwordArts;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Kokushibo, the Upper Moon One boss (Stark, 2026-11-08): the fourth boss, tier 4, 1000 life, a demon with the same body as
// the player with the soul (OniBody: stronger at night, regenerates, a sword cuts the regeneration). He fights with his own
// sword in hand and the normal Moon Breathing (1st, 2nd, 3rd, 5th and 6th Forms) until he is down to 600; then the fight
// stops for his scene (KokushiboDespertar): the kimono tears, the sword stretches into the awakened one and from then on he
// only uses the Awakened Moon (7th, 8th, 9th, 10th, 14th and the homing 16th). How he picks them is KokushiboTactics;
// tuning in souls_bosses/kokushibo.json.
public class KokushiboEntity extends SoulBoss {
    // How he looks, mirrored to clients: 0 his kimono and normal sword, 1 the kimono torn, 2 torn and the sword stretched.
    public static final int LOOK_NORMAL = 0, LOOK_TORN = 1, LOOK_AWAKENED = 2;
    private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(KokushiboEntity.class, EntityDataSerializers.INT);

    private KokushiboTactics tactics;
    // Set while the awakening scene runs, so the look follows the scene and not the life.
    private boolean inScene;

    public KokushiboEntity(EntityType<? extends KokushiboEntity> type, Level level) {
        super(type, level);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.KOKUSHIBO_SWORD.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LOOK, LOOK_NORMAL);
    }

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new KokushiboTactics(this);
        }
        return tactics;
    }

    @Override
    public String bossId() {
        return "kokushibo";
    }

    public int look() {
        return entityData.get(DATA_LOOK);
    }

    // The sword is stretched: the Awakened Moon is his kit now (also on the clients, for the sword in his hand).
    public boolean awakened() {
        return look() == LOOK_AWAKENED;
    }

    // In a boss fight he awakens with his phase (and his scene); as a plain mob, when he is down to the same 60%.
    private boolean wantsAwakened() {
        if (bossfight()) {
            return phase() >= 1;
        }
        BossSpec.Fase second = spec().fase(1);
        return getHealth() / getMaxHealth() <= second.comecaEm();
    }

    @Override
    public void tick() {
        if (getMainHandItem().isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.KOKUSHIBO_SWORD.get()));
        }
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (!inScene) {
            boolean want = wantsAwakened();
            if (want != awakened()) {
                setLook(want ? LOOK_AWAKENED : LOOK_NORMAL);
                stretch(level, want);
            }
        }
        // The keys of his soul follow the sword, as the player's do with H (MoonDemon.swap).
        powerData().moonSword = awakened();
    }

    @Override
    protected int phaseScene(ServerLevel level, int phase) {
        if (phase != 1 || awakened()) {
            return 0;
        }
        inScene = true;
        // A new sword, a new kit: what the normal forms left recharging doesn't hold the awakened ones back (but the 16th
        // still waits a while).
        java.util.Arrays.fill(powerData().cooldowns, 0);
        powerData().cooldowns[com.starkinhon.battlegroundsmod.power.PowerData.SLOT_SHIFT_ULT] = 400;
        return KokushiboDespertar.start(level, this);
    }

    // A big blow he shrugs off (the manga: "not even an infant would die from that").
    private float bigHit;
    private long bigHitAt;

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        float before = getHealth();
        boolean hurt = super.hurt(source, amount);
        if (hurt && isAlive() && !level().isClientSide()) {
            long now = level().getGameTime();
            if (now - bigHitAt > 40) {
                bigHit = 0;
                bigHitAt = now;
            }
            bigHit += Math.max(0, before - getHealth());
            if (bigHit >= getMaxHealth() * 0.08f) {
                bigHit = 0;
                BossFalas.say(this, "fala.battlegroundsmod.kokushibo.bebe", 1200);
            }
        }
        return hurt;
    }

    void setLook(int look) {
        entityData.set(DATA_LOOK, look);
    }

    void sceneOver() {
        inScene = false;
    }

    // The sword stretches (or comes back) in his hand for everyone watching (client/outfit/SwordStretch).
    void stretch(ServerLevel level, boolean on) {
        PacketDistributor.sendToPlayersTrackingEntity(this, new ModPayloads.SwordFx(SwordArts.FX_MOON_STRETCH, getId(), position(), Vec3.ZERO,
                on ? 1 : 0, 0));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("KokushiboLook", look());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int look = tag.getInt("KokushiboLook");
        // a scene cut off by a save ends as if it had played
        setLook(look == LOOK_TORN ? LOOK_AWAKENED : look);
    }
}
