package com.starkinhon.battlegroundsmod.animal.jjmob;

import java.util.UUID;

import javax.annotation.Nullable;

import com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

// One of the Jujutsu biomes' curses (Stark 2026-11-14, preview tools/mobs/jjmobs approved: "porta"). The body, AI,
// blow paths and baked animation are the Naruto creatures' (NarutoMob); the blows are JjMobMoves', the numbers
// JjMobSpec's. What only these have:
// - the exorcism: a curse never leaves a body. It takes the blow, falls (the preview's 'morte', baked) and turns to black
//   ash from the head down, texel by texel (client/fx/AshDeath, style 4); vanilla's red tint and tipping over never show.
//   The Boneco (a cursed corpse, not a curse) bursts its stuffing instead, and the Jogador do Abate (a person) faints;
// - what each one is made of (the preview's "Vida" and "Fraqueza"): straw burns three times over, a bell or a rock
//   halves a sword, a pickaxe breaks the rock twice as fast, the bell's back has no eye...
// - the Jogador do Abate's points (the plate over its head) and the Kogane that follows each player in the Colônia.
public class JjMob extends NarutoMob {
    // the Jogador do Abate's points (what the plate shows, what the one who beats it takes)
    private static final EntityDataAccessor<Integer> POINTS = SynchedEntityData.defineId(JjMob.class, EntityDataSerializers.INT);
    // what the Kogane's paper bubble says now, and until when (tickCount); "" = no bubble
    private static final EntityDataAccessor<String> SAY = SynchedEntityData.defineId(JjMob.class, EntityDataSerializers.STRING);
    // a fake of the Kitsune's Ilusão de Papel (one hit and it is paper), or a fish of the Embrião's school (goes back
    // into the sand after a while): drawn like the others, never drops anything
    private static final EntityDataAccessor<Boolean> SUMMONED = SynchedEntityData.defineId(JjMob.class, EntityDataSerializers.BOOLEAN);

    // the ash starts at this tick of the death and the body is gone at the end of it (the preview's exorcize: the fall,
    // then 1.7 s of crumbling)
    public static final int ASH_START = 19;
    public static final int ASH_TICKS = 34;
    // ticks since it died (both sides: the client plays the 'morte' from it), -1 = alive
    public int dying = -1;
    // the Kogane's player
    @Nullable
    UUID owner;
    // ticks a summoned one has left
    int summonLeft = -1;
    // the Kitsune that made this fake / the Útero that gave birth to this fly (its id), -1 = none
    int maker = -1;
    // the Boneco's Despertador: how long the one it watches has stood still near it
    int stillTicks;
    Vec3 stillAt = Vec3.ZERO;
    // the Útero's and the Embrião's age (the five minutes until they hatch)
    int age;
    // the blow under way (null between blows) and what it locked when it started: the spot it aims at, the ones it
    // caught, how many hits the one it holds gave it
    @Nullable
    String mode;
    Vec3 mark = Vec3.ZERO;
    Vec3[] marks = new Vec3[0];
    int caught = -1;
    int struck;
    // what the Verme swallowed (a player's item in hand): it runs with it and gives it back when it dies
    net.minecraft.world.item.ItemStack stolen = net.minecraft.world.item.ItemStack.EMPTY;

    public JjMob(EntityType<? extends JjMob> type, Level level) {
        super(type, level);
    }

    @Override
    public Kit kit() {
        return JjMobMoves.KIT;
    }

    // the preview's id, without the "jj_"
    public String kind() {
        return id().substring(3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(POINTS, 0);
        builder.define(SAY, "");
        builder.define(SUMMONED, false);
    }

    public int points() {
        return entityData.get(POINTS);
    }

    public String say() {
        return entityData.get(SAY);
    }

    void setSay(String text) {
        entityData.set(SAY, text);
    }

    public boolean summoned() {
        return entityData.get(SUMMONED);
    }

    void makeSummoned(int ticks, int makerId) {
        entityData.set(SUMMONED, true);
        summonLeft = ticks;
        maker = makerId;
    }

    public boolean isKogane() {
        return "jj_kogane".equals(id());
    }

    // a person or a cursed corpse: no ash
    public boolean ashless() {
        return "jj_abate".equals(id()) || "jj_boneco".equals(id());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, type, data);
        if ("jj_abate".equals(id())) {
            // 1 to 19 points, as the preview draws them
            int[] pts = {1, 5, 7, 12, 19};
            entityData.set(POINTS, pts[random.nextInt(pts.length)]);
        }
        return out;
    }

    // ---------- what it is made of ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }
        if (isKogane()) {
            // a blow goes through her: she puffs away and comes back (JjKogane)
            if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                JjKogane.struck(this);
                return false;
            }
            return super.hurt(source, amount);
        }
        if (summoned() && "jj_kitsune".equals(id()) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // a fake of the Ilusão de Papel: one hit and it is paper
            JjMobMoves.paperBurst(this);
            return true;
        }
        if (dying >= 0) {
            return false;
        }
        amount *= JjMobMoves.material(this, source);
        return super.hurt(source, amount);
    }

    // What a hit's maker held: sword, axe, pickaxe, shovel, an empty hand, or nothing to say (a projectile, a technique).
    enum Tool {
        SWORD, AXE, PICKAXE, SHOVEL, FIST, OTHER
    }

    static Tool tool(DamageSource source) {
        if (source.getDirectEntity() != source.getEntity() || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return Tool.OTHER;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return Tool.OTHER;
        }
        ItemStack hand = attacker.getMainHandItem();
        if (hand.isEmpty()) {
            return Tool.FIST;
        }
        if (hand.getItem() instanceof SwordItem) {
            return Tool.SWORD;
        }
        if (hand.getItem() instanceof AxeItem) {
            return Tool.AXE;
        }
        if (hand.getItem() instanceof PickaxeItem) {
            return Tool.PICKAXE;
        }
        if (hand.getItem() instanceof ShovelItem) {
            return Tool.SHOVEL;
        }
        return Tool.OTHER;
    }

    // ---------- the exorcism ----------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && dying < 0) {
            dying = 0;
            JjMobMoves.dyingStart(this, source);
        }
    }

    @Override
    protected void tickDeath() {
        // vanilla's own (deathTime: tipping over red, then gone at 20) never runs, nor the red of the last blow
        dying++;
        hurtTime = 0;
        if (level().isClientSide) {
            return;
        }
        JjMobMoves.dyingTick(this, dying);
        if (dying >= deathLength() && !isRemoved()) {
            remove(RemovalReason.KILLED);
        }
    }

    // how long the body stays after death: the fall and the ash (or the Boneco's burst, the Abate's faint)
    public int deathLength() {
        if ("jj_abate".equals(id())) {
            return 88;
        }
        if ("jj_boneco".equals(id())) {
            return 70;
        }
        return ASH_START + ASH_TICKS + 3;
    }

    // The renderer plays the baked 'morte' while it dies (null: vanilla's death).
    @Override
    @Nullable
    public String deathMode() {
        return dying >= 0 || isDeadOrDying() ? "morte" : null;
    }

    @Override
    public float deathFrame(float partial) {
        return Math.max(0, dying) + (isRemoved() ? 0 : partial);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_HEALTH_ID.equals(key) && level().isClientSide && getHealth() <= 0 && dying < 0) {
            dying = 0;
        }
    }

    // ---------- AI hooks ----------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        age++;
        if (summonLeft > 0 && --summonLeft == 0) {
            JjMobMoves.unsummon(this);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !isKogane() && !summoned() && super.removeWhenFarAway(distance);
    }

    @Override
    protected boolean shouldDropLoot() {
        return !summoned() && !isKogane() && super.shouldDropLoot();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof JjMob j && (j.maker == getId() || maker == j.getId() || maker >= 0 && maker == j.maker)) {
            return true;
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean isPushable() {
        return !isKogane() && super.isPushable();
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isKogane() && super.canBeCollidedWith();
    }

    // ---------- the save ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("JjPoints", points());
        tag.putInt("JjAge", age);
        if (owner != null) {
            tag.putUUID("JjOwner", owner);
        }
        tag.putBoolean("JjSummoned", summoned());
        if (!stolen.isEmpty()) {
            tag.put("JjStolen", stolen.save(registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(POINTS, tag.getInt("JjPoints"));
        age = tag.getInt("JjAge");
        owner = tag.hasUUID("JjOwner") ? tag.getUUID("JjOwner") : null;
        stolen = tag.contains("JjStolen") ? net.minecraft.world.item.ItemStack.parseOptional(registryAccess(), tag.getCompound("JjStolen"))
                : net.minecraft.world.item.ItemStack.EMPTY;
        if ((tag.getBoolean("JjSummoned") || isKogane()) && stolen.isEmpty()) {
            // a fake, a summoned fish or a Kogane never outlives the moment it was made for (the Kogane comes back by itself)
            discard();
        }
    }

    @Nullable
    Player ownerPlayer() {
        return owner == null ? null : level().getPlayerByUUID(owner);
    }

    // the ones made of fire and stone do not burn
    @Override
    public boolean fireImmune() {
        String k = kind();
        return k.equals("inseto") || k.equals("vulcaozinho") || k.equals("golem") || super.fireImmune();
    }

    // the curses' own events (the zones that last, the Kogane's points kept through death)
    public static void registerJj() {
        JjMobMoves.register();
    }

    public static void resetJj() {
        JjMobMoves.resetStatics();
    }

    // a player in the Colônia: their Kogane comes if it is not there
    public static void kogane(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
        JjKogane.ensure(level, player);
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder attributes(NarutoSpec.Spec s) {
        return NarutoMob.attributes(s);
    }

    static ServerLevel server(JjMob m) {
        return (ServerLevel) m.level();
    }
}
