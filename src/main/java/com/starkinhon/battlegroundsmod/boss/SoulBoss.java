package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
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
    // The phase, mirrored to clients (the aura shows the sharingan's eye trail from a given phase on).
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(SoulBoss.class, EntityDataSerializers.INT);
    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private boolean configured;
    // The allbossestier0 rule as last applied, so flipping it changes bosses already in the world.
    private boolean tier0Applied;
    private int phase;
    // Entrance or phase change in progress: the AI waits.
    private int stagedTicks;
    private int stagedAge;
    private boolean ultUsed;
    private boolean ultPending;
    private boolean lethalBypass;
    private SoulBossAI ai;
    // Its soul being stolen by the player who killed it (only after death).
    private SoulCeremony ceremony;

    protected SoulBoss(EntityType<? extends SoulBoss> type, Level level) {
        super(type, level);
        this.lookControl = new AimLookControl(this);
        this.xpReward = isBoss() ? 200 : 10;
        if (isBoss()) {
            setPersistenceRequired();
        }
    }

    // A real boss: the bar, entrance, phases and final ult under /gamerule soulbossfight, the allbossestier0 rule, staying
    // in the world, and fighting bosses of other souls on sight. The SubAlma and Fighter mobs (SoulMob) are not: they
    // fight with the same AI and skills, but as plain monsters.
    // A universe's common enemy or a street fighter (ModEntities.FIGHTERS): no Escudo, and players hit it harder
    // (PowerEvents), so a soulless human can beat the Saibamen of a tier above.
    public boolean common() {
        for (var type : com.starkinhon.battlegroundsmod.ModEntities.FIGHTERS) {
            if (type.get() == getType()) {
                return true;
            }
        }
        return false;
    }

    public boolean isBoss() {
        return true;
    }

    // The file in souls_bosses/ this boss reads.
    public abstract String bossId();

    public BossSpec spec() {
        return BossSpec.get(bossId());
    }

    // Its attributes from the file, or the shared Tier 0 ones under /gamerule allbossestier0.
    public BossSpec.Atributos atributos() {
        return isBoss() ? BossRules.atributos(level(), spec()) : spec().atributos();
    }

    private boolean allTier0(Level level) {
        return isBoss() && BossRules.allTier0(level);
    }

    // Under /gamerule soulbossfight false it fights as a plain mob: one phase with every move of every phase.
    public BossSpec.Fase fase() {
        return bossfight() ? spec().fase(phase) : mobFase(spec());
    }

    public int phase() {
        return level().isClientSide() ? entityData.get(DATA_PHASE) : phase;
    }

    // The boss fight (titles, bar, entrance, phases, final ult) is on: /gamerule soulbossfight.
    public boolean bossfight() {
        return isBoss() && BossRules.bossfight(level());
    }

    private static final java.util.Map<BossSpec, BossSpec.Fase> MOB_FASES = new java.util.IdentityHashMap<>();

    // The first phase's pace, with the moves and combos of all phases (so it uses everything from the start).
    private static BossSpec.Fase mobFase(BossSpec spec) {
        synchronized (MOB_FASES) {
            return MOB_FASES.computeIfAbsent(spec, s -> {
                java.util.LinkedHashSet<String> skills = new java.util.LinkedHashSet<>();
                java.util.LinkedHashSet<String> combos = new java.util.LinkedHashSet<>();
                for (BossSpec.Fase f : s.fases()) {
                    skills.addAll(f.skills());
                    if (f.combos() != null) {
                        combos.addAll(f.combos());
                    }
                }
                BossSpec.Fase f = s.fase(0);
                return new BossSpec.Fase(f.comecaEm(), java.util.List.copyOf(skills), java.util.List.copyOf(combos), f.agressividade(),
                        f.multRecarga(), f.menosRecargaTicks(), f.multVelocidade(), f.multAviso(), f.reacaoTicks(), f.chanceReacao(),
                        f.pausaTicks(), f.despertado(), f.corBarra(), 0, null, null);
            });
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 0);
    }

    // Entrance, phase change, final ult, or waiting to start it: the AI holds still.
    public boolean staged() {
        return stagedTicks > 0 || ultPending;
    }

    // Its own way of reading the fight on top of SoulBossAI (see BossTactics), or null. Asked for only once it fights.
    public BossTactics tactics() {
        return null;
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
        // Bosses of different souls fight each other on sight (Bills against the Uchiha Ghost), before players.
        // The mobs leave each other alone (unless one hits the other).
        if (isBoss()) {
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, SoulBoss.class, 10, true, false,
                    other -> other.getType() != getType() && other instanceof SoulBoss boss && boss.isBoss()));
        }
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, player -> !neutral()));
    }

    // The characters of a challenge book met out in their world (Kuririn, Piccolo...) are neutral (Stark 2026-11-10):
    // they only fight a player who hits them first (HurtByTargetGoal). In their arena or outside the universes (a boss
    // fight in the overworld) they come at you as always.
    private boolean neutral() {
        if (com.starkinhon.battlegroundsmod.challenge.Universes.universeOf(level().dimension()) == null) {
            return false;
        }
        return com.starkinhon.battlegroundsmod.skill.ChallengeSpec.get().universeOf(bossId()) != null;
    }

    @Override
    public void tick() {
        // Every task the boss's moves start is its own (the Izanami only cuts off the moves of those caught in it).
        Tasks.as(this, this::tickAsOwner);
    }

    private void tickAsOwner() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.blocks(this) || com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.blocks(this)) {
            // Caught in Gojo's domain: no phases, no ult, no moves, nothing until it shatters (only the bar keeps up).
            bossEvent.setProgress(getHealth() / getMaxHealth());
            return;
        }
        BossSpec spec = spec();
        PowerData data = powerData();
        if (!spec.poder().equals(data.power)) {
            data.power = spec.poder();
        }
        if (!configured) {
            configure(level, spec);
        } else if (allTier0(level) != tier0Applied) {
            applyStats(level, spec);
        }
        // The tier's Armadura, like the players' (their own file's armor stays under it).
        com.starkinhon.battlegroundsmod.power.Tiers.applyDefense(this);
        PowerLogic.tick(this);
        Movement.tick(this);
        BossFalas.tick(this);
        if ("itadori".equals(bossId())) {
            // Low on life, the Itadori may give the body to Sukuna (and is gone).
            SukunaEntity.takeOver(this, level);
            if (isRemoved()) {
                return;
            }
        }
        boolean bossfight = bossfight();
        bossEvent.setVisible(bossfight);
        int want = spec.faseFor(getHealth() / getMaxHealth());
        // A new phase (and its scene) waits until nobody's move has the boss (held, in a cutscene, in a trap): the Kokushibo
        // used to awaken while a Gojo pulled him, and his scene broke (Stark, 2026-11-08). Its life stops at the phase's
        // line meanwhile (phaseFloor).
        if (bossfight && want > phase && isAlive() && sceneFree()) {
            phase = want;
            startPhase(level, spec.fase(phase));
        }
        data.awakened = fase().despertado();
        // As a plain mob it has everything from the start, the sharingan's eye trail too (clients read the last phase).
        entityData.set(DATA_PHASE, bossfight ? phase : spec.fases().size() - 1);
        BossSpec.UltFinal ult = spec.ultFinal();
        ultPending = bossfight && ult != null && ult.ativa() && !ultUsed && isAlive() && getHealth() / getMaxHealth() <= ult.vida();
        // It finishes whatever move it is in first, so nothing else is flying when the meteor comes.
        if (ultPending && stagedTicks == 0 && data.busyTicks <= 0 && !Freeze.isFrozen(this) && !PowerLogic.sealed(this) && Stun.canAttack(this)) {
            ultPending = false;
            ultUsed = true;
            if (ai != null) {
                ai.reset();
            }
            stage(FinalUlt.start(level, this, ult), false);
        }
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
        LivingEntity target = getTarget();
        if (target != null && !stillHunting(target)) {
            super.setTarget(null);
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    private void configure(ServerLevel level, BossSpec spec) {
        configured = true;
        applyStats(level, spec);
        setHealth(getMaxHealth());
        phase = 0;
        bossEvent.setColor(barColor(spec.fase(0).corBarra()));
        if (!bossfight()) {
            // A plain mob: no entrance.
            return;
        }
        BossSpec.Intro intro = spec.intro();
        stage(intro.duracaoTicks(), true);
        BossFx.title(level, this, intro.titulo(), intro.subtitulo(), BossFx.color(intro.cor()));
    }

    // Attributes and tier, from the file or the allbossestier0 rule. Health keeps its fraction when the max changes.
    private void applyStats(ServerLevel level, BossSpec spec) {
        tier0Applied = allTier0(level);
        float healthFraction = getHealth() / Math.max(1, getMaxHealth());
        BossSpec.Atributos atributos = atributos();
        setBase(Attributes.MAX_HEALTH, atributos.vida());
        // Armor 0 counts too (the Escudo replaced the bosses' armor on 2026-11-08), so an old save loses its 20.
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(Math.max(0, atributos.armadura()));
        }
        setBase(Attributes.MOVEMENT_SPEED, atributos.velocidade());
        setBase(Attributes.KNOCKBACK_RESISTANCE, atributos.resistenciaEmpurrao());
        setBase(Attributes.FOLLOW_RANGE, atributos.alcanceVisao());
        setHealth(Math.max(1, healthFraction * getMaxHealth()));
        powerData().tier = isBoss() ? BossRules.tier(level, spec)
                : spec.tier() != null ? com.starkinhon.battlegroundsmod.power.Tiers.clamp(spec.tier()) : com.starkinhon.battlegroundsmod.power.Tiers.HUMANO;
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && value > 0) {
            instance.setBaseValue(value);
        }
    }

    private void startPhase(ServerLevel level, BossSpec.Fase fase) {
        // A new phase shakes off slows and stuns, and any hold or seal left on it.
        removeAllEffects();
        Freeze.freeAll(this);
        powerData().sealedUntil = 0;
        // A boss with a scene of its own for this phase (Kokushibo stretching his sword) plays it and shows the title itself.
        int scene = phaseScene(level, phase);
        stage(scene > 0 ? scene : fase.transicaoTicks(), true);
        if (scene <= 0) {
            BossFx.title(level, this, fase.titulo(), fase.subtitulo(), BossFx.color(spec().intro().cor()));
        }
        bossEvent.setColor(barColor(fase.corBarra()));
        if (ai != null) {
            ai.reset();
        }
    }

    // The scene a boss plays when this phase starts, instead of the plain still moment with a title: how long it holds the
    // boss (0 = none, the file's transicaoTicks and title).
    protected int phaseScene(ServerLevel level, int phase) {
        return 0;
    }

    private void stage(int ticks, boolean untouchable) {
        if (ticks <= 0) {
            return;
        }
        stagedTicks = ticks;
        stagedAge = 0;
        if (untouchable) {
            Intangible.hold(this, ticks);
        }
    }

    private static BossEvent.BossBarColor barColor(String name) {
        return name == null ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.byName(name);
    }

    // A hit that would kill it before its final ult leaves it at 1 life instead, so the ult always happens
    // (/kill and the void still work). Only in a boss fight (/gamerule soulbossfight).
    @Override
    public void setHealth(float health) {
        // Only looked up when it matters: LivingEntity's constructor already calls this.
        BossSpec.UltFinal ult = health <= 0 && configured && !level().isClientSide ? spec().ultFinal() : null;
        if (health <= 0 && configured && !ultUsed && !lethalBypass && ult != null && ult.ativa() && bossfight()) {
            health = 1;
        }
        super.setHealth(health);
    }

    // Free for its phase scene: no move of anyone's holds it (Freeze), keeps it in a cutscene (Intangible) or a trap.
    protected boolean sceneFree() {
        return !Freeze.isFrozen(this) && !com.starkinhon.battlegroundsmod.combat.Intangible.is(this)
                && !com.starkinhon.battlegroundsmod.combat.Capture.trapped(this);
    }

    // The life (as a fraction) it can't drop under right now, -1 for none: the next phase's line, while that phase has to
    // wait for the boss to be free (sceneFree). Damage that bypasses everything (the Hakai) still goes through.
    protected float phaseFloor() {
        if (!bossfight() || sceneFree()) {
            return -1;
        }
        BossSpec spec = spec();
        if (phase + 1 >= spec.fases().size()) {
            return -1;
        }
        return (float) spec.fase(phase + 1).comecaEm();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        lethalBypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        float floor = lethalBypass || level().isClientSide() ? -1 : phaseFloor();
        if (floor > 0) {
            amount = Math.max(0, Math.min(amount, getHealth() - floor * getMaxHealth()));
        }
        boolean hurt = super.hurt(source, amount);
        lethalBypass = false;
        if (hurt && ai != null && source.getEntity() instanceof LivingEntity attacker) {
            ai.onHurt(attacker);
        }
        return hurt;
    }

    // Relentless: once it has a target it never lets go because of distance or losing sight (the vanilla target goals
    // try to clear it). It only switches to someone else, or drops a target that died, left, changed dimension or
    // went creative/spectator. Unreachable targets are handled by the AI's teleport.
    @Override
    public void setTarget(LivingEntity target) {
        LivingEntity current = getTarget();
        if (target == null && current != null && stillHunting(current)) {
            return;
        }
        super.setTarget(target);
    }

    private boolean stillHunting(LivingEntity target) {
        if (!target.isAlive() || target.isRemoved() || target.level() != level()) {
            return false;
        }
        return !(target instanceof Player player) || !(player.isCreative() || player.isSpectator());
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // Gone in peaceful, like any monster.
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        var soul = ModItems.SOULS.get(spec().poder());
        if (soul != null && dropsSoul(level, source)) {
            ServerPlayer killer = SoulCeremony.killer(source);
            if (killer != null) {
                // Killed by a player: the soul leaves the body and becomes the item (SoulCeremony), dropped there.
                ceremony = new SoulCeremony(killer, source, new ItemStack(soul.get()), spec().poder(), spec().tier());
            } else {
                spawnAtLocation(new ItemStack(soul.get()));
            }
        }
    }

    // Taking a character's soul is the heart of the mod (Stark 2026-11-10: "a mecânica principal"). Beaten in its
    // challenge arena by a player, it always gives its soul; met out in the world, now and then (WORLD_SOUL_CHANCE).
    // Fighters and common enemies have no soul item, so they never get here. dropAlma in the json: always.
    private static final float WORLD_SOUL_CHANCE = 0.15f;

    private boolean dropsSoul(ServerLevel level, DamageSource source) {
        if (spec().dropAlma()) {
            return true;
        }
        if (SoulCeremony.killer(source) == null) {
            return false;
        }
        if (level.dimension() == com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.ARENAS) {
            return true;
        }
        return random.nextFloat() < WORLD_SOUL_CHANCE;
    }

    // While its soul ceremony plays the body stays lying where it fell (vanilla would take it away after 20 ticks).
    @Override
    protected void tickDeath() {
        if (ceremony == null || level().isClientSide()) {
            super.tickDeath();
            return;
        }
        deathTime = Math.min(deathTime + 1, 20);
        if (!ceremony.tick((ServerLevel) level(), this)) {
            ceremony = null;
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        // Set before the player is added: a bar starts visible, and a common mob that was just born flashed one at the
        // top of the screen until its first tick hid it (Stark 2026-11-10).
        bossEvent.setVisible(bossfight());
        bossEvent.addPlayer(player);
        BossSpec.Barra barra = spec().barra();
        if (barra != null && barra.textura() != null) {
            PacketDistributor.sendToPlayer(player, new ModPayloads.BossBarStyle(bossEvent.getId(), barra.textura(), barra.frames(),
                    barra.largura(), barra.altura(), barra.glowColor()));
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
        tag.putBoolean("BossUltUsed", ultUsed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        configured = tag.getBoolean("BossConfigured");
        phase = tag.getInt("BossPhase");
        ultUsed = tag.getBoolean("BossUltUsed");
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
