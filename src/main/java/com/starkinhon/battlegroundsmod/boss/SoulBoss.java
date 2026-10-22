package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

// A boss that carries a soul: it has the power's PowerData, so it casts the same skills as a player with that soul.
// How it fights comes from its souls_bosses file and SoulBossAI. Phases follow its life; the entrance and each phase
// change are short untouchable moments with a title, made to look good on video.
public abstract class SoulBoss extends Monster {
    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private boolean configured;
    private int phase;
    // Entrance or phase change in progress: the AI waits.
    private int stagedTicks;
    private int stagedAge;
    private SoulBossAI ai;

    protected SoulBoss(EntityType<? extends SoulBoss> type, Level level) {
        super(type, level);
        this.lookControl = new AimLookControl(this);
        this.xpReward = 200;
        setPersistenceRequired();
    }

    // The file in souls_bosses/ this boss reads.
    public abstract String bossId();

    public BossSpec spec() {
        return BossSpec.get(bossId());
    }

    public BossSpec.Fase fase() {
        return spec().fase(phase);
    }

    public int phase() {
        return phase;
    }

    public boolean staged() {
        return stagedTicks > 0;
    }

    public PowerData powerData() {
        return getData(ModAttachments.POWER);
    }

    // Base values; the real ones come from the boss file when it first ticks.
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        ai = new SoulBossAI(this);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, ai);
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        BossSpec spec = spec();
        PowerData data = powerData();
        if (!spec.poder().equals(data.power)) {
            data.power = spec.poder();
        }
        if (!configured) {
            configure(level, spec);
        }
        PowerLogic.tick(this);
        Movement.tick(this);
        int want = spec.faseFor(getHealth() / getMaxHealth());
        if (want > phase && isAlive()) {
            phase = want;
            startPhase(level, spec.fase(phase));
        }
        data.awakened = fase().despertado();
        if (stagedTicks > 0) {
            stagedTicks--;
            getNavigation().stop();
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            BossFx.aura(level, this, BossFx.color(spec.intro().cor()), stagedAge++);
            if (stagedTicks == 0) {
                BossFx.shockwave(level, this, BossFx.color(spec.intro().cor()), 6);
                for (LivingEntity near : Combat.inRadius(level, Combat.center(this), 6, this)) {
                    Combat.pushFrom(near, position(), 1.6, 0.5);
                }
            }
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    private void configure(ServerLevel level, BossSpec spec) {
        configured = true;
        BossSpec.Atributos atributos = spec.atributos();
        setBase(Attributes.MAX_HEALTH, atributos.vida());
        setBase(Attributes.ARMOR, atributos.armadura());
        setBase(Attributes.MOVEMENT_SPEED, atributos.velocidade());
        setBase(Attributes.KNOCKBACK_RESISTANCE, atributos.resistenciaEmpurrao());
        setBase(Attributes.FOLLOW_RANGE, atributos.alcanceVisao());
        setHealth(getMaxHealth());
        phase = 0;
        BossSpec.Intro intro = spec.intro();
        stage(intro.duracaoTicks());
        BossFx.title(level, this, intro.titulo(), intro.subtitulo(), BossFx.color(intro.cor()));
        bossEvent.setColor(barColor(fase().corBarra()));
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && value > 0) {
            instance.setBaseValue(value);
        }
    }

    private void startPhase(ServerLevel level, BossSpec.Fase fase) {
        // A new phase shakes off slows and stuns.
        removeAllEffects();
        stage(fase.transicaoTicks());
        BossFx.title(level, this, fase.titulo(), fase.subtitulo(), BossFx.color(spec().intro().cor()));
        bossEvent.setColor(barColor(fase.corBarra()));
        if (ai != null) {
            ai.reset();
        }
    }

    private void stage(int ticks) {
        if (ticks <= 0) {
            return;
        }
        stagedTicks = ticks;
        stagedAge = 0;
        Intangible.hold(this, ticks);
    }

    private static BossEvent.BossBarColor barColor(String name) {
        return name == null ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.byName(name);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && ai != null && source.getEntity() instanceof LivingEntity attacker) {
            ai.onHurt(attacker);
        }
        return hurt;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        var soul = ModItems.SOULS.get(spec().poder());
        if (spec().dropAlma() && soul != null) {
            spawnAtLocation(new ItemStack(soul.get()));
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
        BossSpec.Barra barra = spec().barra();
        if (barra != null && barra.textura() != null) {
            PacketDistributor.sendToPlayer(player, new ModPayloads.BossBarStyle(bossEvent.getId(), barra.textura(), barra.frames(),
                    barra.largura(), barra.altura()));
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("BossConfigured", configured);
        tag.putInt("BossPhase", phase);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        configured = tag.getBoolean("BossConfigured");
        phase = tag.getInt("BossPhase");
        if (hasCustomName()) {
            bossEvent.setName(getDisplayName());
        }
    }

    // Vanilla resets the pitch to level every tick when nothing is being looked at; the AI aims skills with it,
    // so it is kept.
    private static final class AimLookControl extends LookControl {
        AimLookControl(SoulBoss boss) {
            super(boss);
        }

        @Override
        protected boolean resetXRotOnTick() {
            return false;
        }
    }
}
