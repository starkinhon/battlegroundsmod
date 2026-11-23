// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
// How it fights comes from its souls_bosses file and SoulBossAI. It uses every move of every phase from the start (the
// /gamerule soulbossfight with entrance, titles, bar, phase changes and final ult went out on 2026-11-22, Stark).
public abstract class SoulBoss extends Monster {
    // The phase, mirrored to clients (the aura shows the sharingan's eye trail from a given phase on).
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(SoulBoss.class, EntityDataSerializers.INT);
    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private boolean configured;
    // The allbossestier0 rule as last applied, so flipping it changes bosses already in the world.
    private boolean tier0Applied;
    // The Livro dos Desafios' Modo Difícil (challenge/ChallengeArenas): fights at this tier when it is above its own (-1 = its own)
    private int arenaTier = -1;
    // The Dungeon (challenge/Dungeon): fights at EXACTLY this tier, weaker or stronger than its own (arenaTier only ever raises it),
    // and never drops its soul: the run puts the soul in the player itself.
    private int forcedTier = -1;
    // Modo Difícil raised it above its own tier (arenaTier stronger than the file's): set by applyStats.
    private boolean raisedByHard;
    private boolean dungeonFight;
    // A fight of the Livro dos Desafios (challenge/ChallengeArenas, not the Dungeon): life against the player's curve.
    private boolean challengeFight;
    // How much more life a common challenger has than a player of the same tier: a small edge, the fight stays winnable.
    private static final double CHALLENGE_EDGE = 1.15;

    public void challengeFight() {
        challengeFight = true;
        if (configured && level() instanceof ServerLevel server) {
            applyStats(server, spec());
            setHealth(getMaxHealth());
        }
    }

    public void dungeonTier(int tier) {
        forcedTier = tier;
        dungeonFight = true;
        if (configured && level() instanceof ServerLevel server) {
            applyStats(server, spec());
            setHealth(getMaxHealth());
        }
    }

    public boolean dungeonFight() {
        return dungeonFight;
    }

    // A SubAlma raised by the Modo Dificil to a stronger tier than its own hits at the player's SubAlma scale
    // (Combat.subScale): the bosses' multSubAlmas assumed a SubAlma stays at its low tier, so raised to the divine souls'
    // tier it hit harder than them (Stark 2026-11-22: Goku's Kaioken above Sukuna's Clivar).
    public boolean raisedByHard() {
        return raisedByHard;
    }

    public void arenaTier(int tier) {
        arenaTier = tier;
        if (configured && level() instanceof ServerLevel server) {
            applyStats(server, spec());
            setHealth(getMaxHealth());
        }
    }
    private int phase;
    // Entrance or phase change in progress: the AI waits.
    private boolean lethalBypass;
    SoulBossAI ai;
    // The Dragon Ball souls' flight and air fight (AirCombat), made on the first tick of one that flies.
    AirCombat air;
    // Its soul being stolen by the player who killed it (only after death).
    private SoulCeremony ceremony;
    // A soul with no ceremony (not killed by a player), dropped on the body as it ends its fall, and the last ground
    // that body stood on (SoulCeremony.safeSpot).
    private ItemStack pendingSoul;
    private net.minecraft.world.phys.Vec3 pendingGround;
    // Raised by Makima as a puppet of its soul (power/powers/makima/Almas): no boss fight, no soul to drop.
    private LivingEntity puppetOwner;

    protected SoulBoss(EntityType<? extends SoulBoss> type, Level level) {
        super(type, level);
        this.lookControl = new AimLookControl(this);
        this.xpReward = isBoss() ? 200 : 10;
        if (isBoss()) {
            setPersistenceRequired();
        }
    }

    // A real boss: the allbossestier0 rule, staying
    // in the world, and fighting bosses of other souls on sight. The SubAlma and common-enemy mobs (SoulMob) are not:
    // they fight with the same AI and skills, but as plain monsters.
    // A universe's common enemy (ModEntities.FIGHTERS): no Escudo, and players hit it harder
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

    // The message of a boss whose fight holds the players on the ground (boss/BossFlightLock), or null: they may fly.
    public String flightLock() {
        return null;
    }

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

    // It fights as a plain mob: one phase with every move of every phase.
    public BossSpec.Fase fase() {
        return mobFase(spec());
    }

    public void puppetOf(LivingEntity owner) {
        this.puppetOwner = owner;
        setPersistenceRequired();
    }

    public LivingEntity puppetOwner() {
        return puppetOwner;
    }

    public int phase() {
        return level().isClientSide() ? entityData.get(DATA_PHASE) : phase;
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

    // Flying (the sky passers, the air fight) they are drawn from up to 192 blocks, not the 64 of a player-sized mob
    // (Stark 2026-11-19: the ones going by in the sky were barely seen). No gravity is synced by vanilla.
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        if (!isNoGravity()) {
            return super.shouldRenderAtSqrDistance(distance);
        }
        double d = 192 * getViewScale();
        return distance < d * d;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 0);
    }

    // Its air fight (null until it first flies, or for souls that don't fly).
    public AirCombat air() {
        return air;
    }

    // While AirCombat flies it, no goal may walk or jump it (the stroll would steer it on the ground's way).
    void airControl(boolean owned) {
        if (owned) {
            goalSelector.disableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE);
            goalSelector.disableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP);
        } else {
            goalSelector.enableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE);
            goalSelector.enableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP);
        }
    }

    // The playable introduction (intro/IntroScript) drives this boss by hand: introHold turns its AI off (it only does
    // what the script casts), introMoves limits the AI to these moves (empty = punches and dashes only) and introFloor is
    // the life fraction it can't drop under (the script moves the bar step by step).
    public boolean introHold;
    public java.util.Set<String> introMoves;
    public float introFloor = -1;
    // Who it faces, standing still, while the script keeps it so (Gojo on the platform looking at Sukuna through the talk
    // and the duel: Stark 2026-11-19, he walked off up the stairs and cast from there). null = free.
    public Entity introFace;
    // Not even its strong blow and barrage (they are its real skills): only what the page's fight has, punches, dashes and
    // the air combat's own moves (Stark 2026-11-19: Vegeta in Act 2 as in the preview, to teach).
    public boolean introPlain;

    // A moment the AI holds still for (none of its own; the Kokushibo waits to draw his sword).
    public boolean staged() {
        return false;
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
        // No wandering while the introduction holds it (it walked off on its own, behind the player and up the stairs),
        // and no looking around while it faces someone for the script.
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return !introHold && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !introHold && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16f) {
            @Override
            public boolean canUse() {
                return introFace == null && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return introFace == null && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return introFace == null && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return introFace == null && super.canContinueToUse();
            }
        });
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
        if (com.starkinhon.battlegroundsmod.challenge.Universes.universeOf(level().dimension()) == null
                && !(level().dimension() == net.minecraft.world.level.Level.OVERWORLD && level() instanceof net.minecraft.server.level.ServerLevel server
                        && com.starkinhon.battlegroundsmod.power.Progression.on(server.getServer()))) {
            return false;
        }
        return com.starkinhon.battlegroundsmod.skill.ChallengeSpec.get().universeOf(bossId()) != null;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (introFace != null) {
            if (!introFace.isAlive() || introFace.level() != level()) {
                introFace = null;
                return;
            }
            getNavigation().stop();
            float yaw = (float) (Mth.atan2(introFace.getZ() - getZ(), introFace.getX() - getX()) * Mth.RAD_TO_DEG) - 90f;
            setYRot(yaw);
            setYBodyRot(yaw);
            setYHeadRot(yaw);
            getLookControl().setLookAt(introFace, 30f, 30f);
        }
    }

    @Override
    public void tick() {
        // Every task the boss's moves start is its own (the Izanami only cuts off the moves of those caught in it).
        Tasks.as(this, this::tickAsOwner);
    }

    private void tickAsOwner() {
        if (!level().isClientSide) {
            // Before it moves this tick: in the air it is moved by its own speed (AirCombat), not by Minecraft's AI.
            AirCombat.tick(this);
        }
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (com.starkinhon.battlegroundsmod.power.powers.gojo.InfiniteVoid.blocks(this) || com.starkinhon.battlegroundsmod.power.powers.sukuna.ShrineDomain.blocks(this)) {
            // Caught in Gojo's domain: no moves, nothing until it shatters.
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
        bossEvent.setVisible(false);
        data.awakened = fase().despertado();
        // It has everything from the start, the sharingan's eye trail too (clients read the last phase).
        entityData.set(DATA_PHASE, spec.fases().size() - 1);
        LivingEntity target = getTarget();
        if (target != null && !stillHunting(target)) {
            super.setTarget(null);
        }
        if (getTarget() == null && isBoss() && tickCount % 20 == 0) {
            sense(level);
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    private void configure(ServerLevel level, BossSpec spec) {
        configured = true;
        applyStats(level, spec);
        setHealth(getMaxHealth());
        phase = 0;
        bossEvent.setColor(barColor(spec.fase(0).corBarra()));
    }

    // Attributes and tier, from the file or the allbossestier0 rule. Health keeps its fraction when the max changes.
    private void applyStats(ServerLevel level, BossSpec spec) {
        tier0Applied = allTier0(level);
        float healthFraction = getHealth() / Math.max(1, getMaxHealth());
        BossSpec.Atributos atributos = atributos();
        int ownTier = isBoss() ? BossRules.tier(level, spec)
                : spec.tier() != null ? com.starkinhon.battlegroundsmod.power.Tiers.clamp(spec.tier()) : com.starkinhon.battlegroundsmod.power.Tiers.HUMANO;
        int tier = forcedTier >= 0 ? forcedTier : arenaTier >= 0 ? Math.min(ownTier, arenaTier) : ownTier;
        raisedByHard = forcedTier < 0 && arenaTier >= 0 && tier < ownTier;
        // raised to a higher tier: its life grows as much as a tier's life does (the same share of a fighter of that tier)
        double lifeScale = com.starkinhon.battlegroundsmod.power.Tiers.health(tier) / com.starkinhon.battlegroundsmod.power.Tiers.health(ownTier);
        // The bosses' files were written for the old tier curve (Tier 0 = 400 ... Tier 10 = 20): their life follows the curve
        // in padrao.tiers.json as much as a player's of their tier did (Stark's rebalance 2026-11-17 raised the players' only,
        // and the bosses fell 3 to 4 times faster). The shared Tier 0 of /gamerule allbossestier0 is already on the new curve.
        // Not in the Dungeon: there the fight's tier is the same for both sides and its own floor below (half the tier's life)
        // is the balance Stark played with; the rescale doubled every boss there (Kakashi at Tier 10: 32 -> 65 vs the
        // player's 20) and the first etapa became unwinnable (Stark, 2026-11-19).
        if (!tier0Applied && !dungeonFight) {
            lifeScale *= com.starkinhon.battlegroundsmod.power.Tiers.health(ownTier) / com.starkinhon.battlegroundsmod.power.Tiers.legacyHealth(ownTier);
        }
        // Livro dos Desafios: a common challenger (file life = its tier's old share, everyone but the chefes) has the life a
        // PLAYER of the fight's tier has, times CHALLENGE_EDGE. The rescale above followed the tier curve, but a player gets
        // only 20 + (curve - Tier 10's 65), so Kuririn at Tier 8 had 135 against the player's 90 (Stark, 2026-11-19). Damage
        // already matches at the same tier (both sides scale by the tier; multDano 1). The chefes keep the life above:
        // being 2,5x a fighter of their tier is what makes them chefes.
        if (challengeFight && !tier0Applied && !dungeonFight
                && atributos.vida() <= com.starkinhon.battlegroundsmod.power.Tiers.legacyHealth(ownTier) + 0.5) {
            lifeScale = com.starkinhon.battlegroundsmod.power.Tiers.playerHealth(tier)
                    / com.starkinhon.battlegroundsmod.power.Tiers.legacyHealth(ownTier) * CHALLENGE_EDGE;
        }
        double maxLife = atributos.vida() * lifeScale;
        if (dungeonFight) {
            maxLife = Math.max(maxLife, Math.round(com.starkinhon.battlegroundsmod.power.Tiers.health(tier) * 0.50));
        }
        setBase(Attributes.MAX_HEALTH, maxLife);
        // Armor 0 counts too (the Escudo replaced the bosses' armor on 2026-11-08), so an old save loses its 20.
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(Math.max(0, atributos.armadura()));
        }
        setBase(Attributes.MOVEMENT_SPEED, atributos.velocidade());
        setBase(Attributes.KNOCKBACK_RESISTANCE, atributos.resistenciaEmpurrao());
        setBase(Attributes.FOLLOW_RANGE, atributos.alcanceVisao());
        setHealth(Math.max(1, healthFraction * getMaxHealth()));
        powerData().tier = tier;
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && value > 0) {
            instance.setBaseValue(value);
        }
    }

    private static BossEvent.BossBarColor barColor(String name) {
        return name == null ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.byName(name);
    }

    // Free for its phase scene: no move of anyone's holds it (Freeze), keeps it in a cutscene (Intangible) or a trap.
    protected boolean sceneFree() {
        return !Freeze.isFrozen(this) && !com.starkinhon.battlegroundsmod.combat.Intangible.is(this)
                && !com.starkinhon.battlegroundsmod.combat.Capture.trapped(this);
    }

    // The life (as a fraction) it can't drop under right now, -1 for none: the introduction's step (introFloor), or a
    // boss's own line (Kokushibo waits at his awakening's line while held). Damage that bypasses everything (the Hakai)
    // still goes through.
    protected float phaseFloor() {
        if (introFloor >= 0) {
            return introFloor;
        }
        return -1;
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

    // The vanilla goal only takes a player it sees: a boss spawned in a hole or behind a wall stood there for minutes
    // (Shigaraki in the Soco Serio's trench, 2026-11-12). A player close by for 3 s is felt even unseen; the AI's
    // teleport gets to them.
    private int unseenTicks;

    private void sense(ServerLevel level) {
        if (neutral()) {
            unseenTicks = 0;
            return;
        }
        Player near = level.getNearestPlayer(getX(), getY(), getZ(), 24, player -> player instanceof Player p && stillHunting(p) && !p.isInvisible());
        if (near == null) {
            unseenTicks = 0;
            return;
        }
        unseenTicks += 20;
        if (unseenTicks >= 60) {
            unseenTicks = 0;
            setTarget(near);
        }
    }

    private boolean stillHunting(LivingEntity target) {
        if (!target.isAlive() || target.isRemoved() || target.level() != level()) {
            return false;
        }
        return !(target instanceof Player player) || !(player.isCreative() || player.isSpectator()
                || com.starkinhon.battlegroundsmod.item.SoulCandyItem.outOfBody(player));
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
        if (puppetOwner != null) {
            // A soul Makima raised: it is already hers.
            return;
        }
        if (dungeonFight) {
            // The Dungeon gives the soul straight to the player (challenge/Dungeon.bossBeaten): no item, no ceremony.
            return;
        }
        var soul = ModItems.SOULS.get(spec().poder());
        if (soul != null && dropsSoul(level, source)) {
            ServerPlayer killer = SoulCeremony.killer(source);
            if (killer != null) {
                // Killed by a player: the soul leaves the body and becomes the item (SoulCeremony), dropped there.
                ceremony = new SoulCeremony(killer, source, new ItemStack(soul.get()), spec().poder(), spec().tier(), position());
            } else {
                // Not here, where the blow landed: where the body ends up, as it vanishes (tickDeath).
                pendingSoul = new ItemStack(soul.get());
                pendingGround = position();
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
        if (com.starkinhon.battlegroundsmod.challenge.ChallengeArenas.isArena(level.dimension())) {
            return true;
        }
        return random.nextFloat() < WORLD_SOUL_CHANCE;
    }

    // While its soul ceremony plays the body stays lying where it fell (vanilla would take it away after 20 ticks).
    @Override
    protected void tickDeath() {
        if (pendingSoul != null && level() instanceof ServerLevel level) {
            if (onGround()) {
                pendingGround = position();
            }
            // Vanilla takes the body away on this tick (deathTime 20): the soul is left where it lies.
            if (deathTime >= 19) {
                SoulCeremony.dropPlain(level, this, pendingSoul, pendingGround);
                pendingSoul = null;
            }
        }
        if (ceremony == null || level().isClientSide()) {
            super.tickDeath();
            return;
        }
        deathTime = Math.min(deathTime + 1, 20);
        if (!ceremony.tick((ServerLevel) level(), this)) {
            ceremony = null;
        }
    }

    // A dead body thrown out of the world keeps its soul: back on the last safe ground instead of gone with it.
    @Override
    protected void onBelowWorld() {
        if (isDeadOrDying() && level() instanceof ServerLevel level) {
            if (ceremony != null) {
                net.minecraft.world.phys.Vec3 back = ceremony.rescueSpot(level);
                setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                resetFallDistance();
                teleportTo(back.x, back.y, back.z);
                return;
            }
            if (pendingSoul != null) {
                SoulCeremony.dropPlain(level, this, pendingSoul, pendingGround);
                pendingSoul = null;
            }
        }
        super.onBelowWorld();
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        // Set before the player is added: a bar starts visible, and a common mob that was just born flashed one at the
        // top of the screen until its first tick hid it (Stark 2026-11-10).
        bossEvent.setVisible(false);
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

    // A boss whose bar says more than its name (Freeza Poder Total's clock); null puts the name back.
    protected void setBarName(net.minecraft.network.chat.Component name) {
        bossEvent.setName(name == null ? getDisplayName() : name);
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
        tag.putInt("ArenaTier", arenaTier);
        tag.putInt("ForcedTier", forcedTier);
        tag.putBoolean("DungeonFight", dungeonFight);
        tag.putBoolean("ChallengeFight", challengeFight);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        configured = tag.getBoolean("BossConfigured");
        phase = tag.getInt("BossPhase");
        arenaTier = tag.contains("ArenaTier") ? tag.getInt("ArenaTier") : -1;
        forcedTier = tag.contains("ForcedTier") ? tag.getInt("ForcedTier") : -1;
        dungeonFight = tag.getBoolean("DungeonFight");
        challengeFight = tag.getBoolean("ChallengeFight");
        if (configured && !level().isClientSide()) {
            // The bar's color is set when a phase starts: after a reload it was back to red until the next phase.
            try {
                BossSpec spec = spec();
                bossEvent.setColor(barColor(spec.fase(Math.min(phase, spec.fases().size() - 1)).corBarra()));
            } catch (RuntimeException e) {
                // no file for it (yet): keeps the default bar
            }
        }
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
