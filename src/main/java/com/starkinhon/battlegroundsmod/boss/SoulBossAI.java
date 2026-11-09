package com.starkinhon.battlegroundsmod.boss;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;

import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Destructible;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Melee;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.power.BossOrder;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.bills.Hakaizinho;
import com.starkinhon.battlegroundsmod.power.powers.DomainSpace;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Susanoo;
import com.starkinhon.battlegroundsmod.skill.HakaiSpec;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

// The boss's combat brain. Instead of rolling a random move every few ticks, it reads the fight every tick:
// - each decision scores every move it may use in this phase (distance, line of sight, cooldowns, whether the target
//   is stunned, busy, pressing it, or just burned their ult), plus whole combos, and picks one (aleatoriedade adds
//   variety so it is not predictable);
// - before a move it stops and telegraphs it for avisoTicks, so a good player can react;
// - it reacts to the target after a human-like delay: back dodge out of skills and projectiles, Susanoo counter when it
//   is being comboed or sees a heavy hit coming, a dodge and a punish window when the target uses their ult;
// - between moves it positions itself like a ninja: it switches between walking calmly and moving insanely fast,
//   blinks behind the target (shadow step) and rushes in with dashes and punches when it feels aggressive, keeps
//   range and strafes otherwise, double jumps to targets standing up high, and reappears behind a target it can't reach;
// - a god (estilo "deus", Bills) has none of the ninja moves: he walks calmly at the target, never dodges, dashes or
//   jumps, and now and then blinks behind it; attacks flying at him are shot down with the hakaizinho.
// Every number comes from the boss file (BossSpec). /soul boss debug shows what it is thinking.
// A boss may also have its own tactics (BossTactics, the Gojo boss) that reweigh every move for the situation, say how
// its held techniques are used, and run steps of their own; the getters below are what they read.
// Under /gamerule soulbotscaoticos (BossRules.chaos) the strategy is switched off and it fights like a lunatic: see
// "chaos" below. The locks stay the same (cooldowns, stuns, busy, the human reaction before a punch): wilder, not unfair.
public class SoulBossAI extends Goal {
    // Toggled by /soul boss debug: each boss shows its state and best options on the action bar of players nearby.
    public static boolean debug = false;

    private static final double DEBUG_RANGE = 40;
    private static final int STEP_TIMEOUT = 50;
    private static final int REACTION_EXPIRY = 10;
    private static final int REPATH_TICKS = 8;
    private static final int SHADOW_CHECK_TICKS = 10;
    // A target moving more than this in one tick teleported: the boss has to notice it again.
    private static final double TELEPORT_JUMP = 2.5;
    // Extra ticks to notice someone who got into reach behind it.
    private static final int BEHIND_EXTRA_TICKS = 4;
    // Degrees per tick it turns to punch, and how squarely it must face them (cosine) to throw it.
    private static final float PUNCH_TURN = 40;
    private static final double PUNCH_FACING = 0.5;
    // Chaos (gamerule soulbotscaoticos): decisions and pauses this many times shorter, warnings and the gap between
    // techniques shortened, calm stretches almost gone, reactions more likely, and a random burst of movement (dodge,
    // air jump, dash, blink) checked every CHAOS_BURST_TICKS with CHAOS_BURST_CHANCE.
    private static final int CHAOS_DECISION_DIV = 3;
    private static final int CHAOS_PAUSE_DIV = 4;
    private static final double CHAOS_TELL = 0.6;
    private static final int CHAOS_GAP_DIV = 3;
    private static final int CHAOS_CALM_DIV = 4;
    private static final double CHAOS_REACTION = 1.4;
    private static final double CHAOS_CLOSE = 0.8;
    private static final int CHAOS_BURST_TICKS = 6;
    private static final double CHAOS_BURST_CHANCE = 0.3;
    private static final ResourceLocation FAST = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "boss_fast");

    private enum State { IDLE, TELL, ACTION }

    private enum Reaction { DODGE_SIDE, DODGE_BACK, COUNTER, ESCAPE }

    private enum Mood { CLOSE, RANGE }

    private record Option(String name, double score, boolean combo) {
    }

    private final SoulBoss boss;
    private final RandomSource random;

    private LivingEntity target;
    private long now;
    private State state = State.IDLE;
    private String move;
    private int tellLeft;
    private int tellTotal;
    private int actionAge;
    private final Deque<String> plan = new ArrayDeque<>();
    private String comboName;
    private int stepAge;
    private int decisionWait;
    private Mood mood = Mood.CLOSE;
    private int moodAge;
    private int strafeSide = 1;
    private int strafeAge;
    private int repath;
    private int dashCooldown;
    private int shadowCooldown;
    private boolean fast;
    private int paceLeft;
    private int punchCooldown;
    // Human reaction on the punch: when the target walks, dashes or teleports into reach, the boss only punches from
    // this game tick on (fase.reacaoTicks, more from behind). -1 while the target is out of reach.
    private long punchReadyAt = -1;
    private Vec3 lastTargetPos;
    private Vec3 lastBossPos;
    // Ticks until it may use another special technique (see Percepcao.intervaloTecnicasTicks).
    private int techniqueGap;
    private int stuck;
    private int punishLeft;
    private boolean jumpPending;
    private boolean jumpBack;
    private final Deque<Long> pressure = new ArrayDeque<>();
    private long seenCast = -1;
    private boolean targetWasSwinging;
    private Reaction reaction;
    private long reactAt;
    private final Deque<String> recent = new ArrayDeque<>();
    private String thinking = "";
    // The attack the hakaizinho is being shot at (the spark follows it until it leaves the finger).
    private Destructible.Attack antiTarget;
    private BossTactics tactics;
    private boolean tacticsAsked;
    // /gamerule soulbotscaoticos, read every tick.
    private boolean chaos;

    public SoulBossAI(SoulBoss boss) {
        this.boss = boss;
        this.random = boss.getRandom();
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = boss.getTarget();
        return t != null && t.isAlive() && !boss.staged();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        reset();
    }

    @Override
    public void stop() {
        reset();
        setPace(false, 0);
        halt();
    }

    // Drops whatever it was doing (new phase, new target).
    public void reset() {
        paceLeft = 0;
        state = State.IDLE;
        plan.clear();
        comboName = null;
        reaction = null;
        pressure.clear();
        decisionWait = 0;
        punchReadyAt = -1;
        lastTargetPos = null;
        lastBossPos = null;
    }

    // ---------------------------------------------------------------- the loop

    @Override
    public void tick() {
        LivingEntity t = boss.getTarget();
        if (t == null) {
            return;
        }
        if (t != target) {
            target = t;
            reset();
            seenCast = t.hasData(ModAttachments.POWER) ? t.getData(ModAttachments.POWER).lastCastTime : -1;
        }
        now = boss.level().getGameTime();
        chaos = BossRules.chaos(boss.level());
        BossSpec spec = boss.spec();
        BossSpec.Fase fase = boss.fase();
        PowerData data = boss.powerData();
        double distance = boss.distanceTo(target);
        countDown();
        trackReach(spec, fase, distance);
        perceive(spec, fase, distance);
        if (debug && now % 5 == 0) {
            showDebug(fase, distance);
        }

        boolean busy = data.busyTicks > 0 || Freeze.isFrozen(boss);
        if (state == State.ACTION) {
            actionAge++;
            BossSpec.Skill skill = spec.skills().get(move);
            boolean guarding = skill != null && "contra".equals(skill.tipo()) && Susanoo.isReady(boss);
            if (busy || guarding || actionAge < 2) {
                keepAiming(skill);
                if (skill != null && "barragem".equals(skill.tipo()) && actionAge % 4 == 0) {
                    boss.swing(InteractionHand.MAIN_HAND);
                }
                if (guarding) {
                    // Holding the counter: stand ground facing them, it is bait.
                    halt();
                }
                return;
            }
            state = State.IDLE;
            decisionWait = Math.max(decisionWait, chaos ? fase.pausaTicks() / CHAOS_PAUSE_DIV : fase.pausaTicks());
        }
        if (busy) {
            keepAiming(null);
            return;
        }
        if (spec.god() && shootDown(spec, fase)) {
            return;
        }
        if (finishing()) {
            // A finishing move goes on whatever the target does.
            reaction = null;
        }
        if (state != State.TELL && tactics() != null && tactics().act(this)) {
            return;
        }
        if (reaction != null && now >= reactAt) {
            Reaction r = reaction;
            reaction = null;
            if (now - reactAt <= REACTION_EXPIRY && react(r, spec, fase, distance)) {
                return;
            }
        }
        if (state == State.TELL) {
            tell(spec, distance);
            return;
        }
        if (!plan.isEmpty() && runStep(spec, fase, distance)) {
            return;
        }
        if (--decisionWait <= 0) {
            int interval = spec.percepcao().intervaloDecisaoTicks();
            decisionWait = Math.max(1, chaos ? interval / CHAOS_DECISION_DIV : interval);
            decide(spec, fase, distance);
            if (state != State.IDLE || !plan.isEmpty()) {
                return;
            }
        }
        position(spec, fase, distance);
    }

    private void countDown() {
        if (dashCooldown > 0) {
            dashCooldown--;
        }
        if (punchCooldown > 0) {
            punchCooldown--;
        }
        if (shadowCooldown > 0) {
            shadowCooldown--;
        }
        if (techniqueGap > 0) {
            techniqueGap--;
        }
        if (--paceLeft <= 0) {
            BossSpec.Ritmo ritmo = boss.spec().ritmo();
            boolean nowFast = !fast;
            int min = nowFast ? ritmo.rapidoMinTicks() : ritmo.calmoMinTicks();
            int max = nowFast ? ritmo.rapidoMaxTicks() : ritmo.calmoMaxTicks();
            if (chaos && !nowFast) {
                // Chaos: barely a breath of calm before running again.
                min /= CHAOS_CALM_DIV;
                max /= CHAOS_CALM_DIV;
            }
            setPace(nowFast, min + random.nextInt(Math.max(1, max - min + 1)));
        }
        if (punishLeft > 0) {
            punishLeft--;
        }
        if (jumpPending && !boss.onGround()) {
            jumpPending = false;
            Movement.tryPerform(boss, Movement.Kind.PULO_DUPLO, jumpBack ? -1 : 1, 0);
            jumpBack = false;
        }
    }

    // Calm or insanely fast: a speed modifier on top of the boss's base speed.
    private void setPace(boolean fastNow, int ticks) {
        fast = fastNow;
        paceLeft = ticks;
        AttributeInstance speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        BossSpec.Ritmo ritmo = boss.spec().ritmo();
        double phase = boss.fase().multVelocidade() > 0 ? boss.fase().multVelocidade() : 1;
        double mult = (fastNow ? ritmo.velocidadeRapido() : ritmo.velocidadeCalmo()) * phase;
        speed.addOrUpdateTransientModifier(new AttributeModifier(FAST, mult - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    // ---------------------------------------------------------------- reading the target

    // When the target gets into punching reach by its own move (walking in, a Dash Attack, a teleport behind it), the
    // boss needs a human moment to notice before its first punch, and longer when it shows up behind it. Getting there
    // by its own move (walking up, its dash, its shadow step) it knows, so it punches almost at once.
    private void trackReach(BossSpec spec, BossSpec.Fase fase, double distance) {
        Vec3 targetPos = target.position();
        Vec3 bossPos = boss.position();
        double targetMoved = lastTargetPos == null ? 0 : targetPos.distanceTo(lastTargetPos);
        double bossMoved = lastBossPos == null ? 0 : bossPos.distanceTo(lastBossPos);
        lastTargetPos = targetPos;
        lastBossPos = bossPos;
        if (distance > spec.distancias().soco() + 0.5) {
            punchReadyAt = -1;
            return;
        }
        boolean blinked = targetMoved > TELEPORT_JUMP;
        if (punchReadyAt >= 0 && !blinked) {
            return;
        }
        int delay = targetMoved >= bossMoved ? Math.max(2, fase.reacaoTicks()) : 2;
        if (!inFront(target)) {
            delay += BEHIND_EXTRA_TICKS;
        }
        punchReadyAt = now + delay;
    }

    // The target is within 90 degrees of where the boss looks.
    private boolean inFront(LivingEntity entity) {
        Vec3 to = entity.position().subtract(boss.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        return flat.lengthSqr() < 1.0E-4 || flat.normalize().dot(Combat.flatLook(boss)) > 0;
    }

    private void perceive(BossSpec spec, BossSpec.Fase fase, double distance) {
        BossSpec.Reacoes reacoes = spec.reacoes();
        while (!pressure.isEmpty() && now - pressure.peekFirst() > reacoes.janelaPressaoTicks()) {
            pressure.pollFirst();
        }
        // Moves the target just used (players and other bosses record their last cast).
        if (target.hasData(ModAttachments.POWER)) {
            PowerData their = target.getData(ModAttachments.POWER);
            if (their.lastCastTime > seenCast) {
                seenCast = their.lastCastTime;
                onTargetCast(their.lastCastSlot, distance, reacoes, fase);
            }
        }
        // Swings up close count as pressure, so a player spamming punches gets countered.
        boolean swinging = target.swinging;
        if (swinging && !targetWasSwinging && distance <= spec.distancias().soco() + 1.5 && facesBoss(target)) {
            pressure.addLast(now);
        }
        targetWasSwinging = swinging;
        // Being beaten up: counter if it can, otherwise get out (back dodge, air jump, step behind them...).
        if (pressure.size() >= reacoes.golpesParaFugir() && reaction == null && state != State.ACTION) {
            schedule(Reaction.COUNTER, reacoes.chanceContraAtaque(), fase);
            if (reaction == null) {
                schedule(Reaction.ESCAPE, reacoes.chanceFugirAoApanhar(), fase);
            }
            pressure.clear();
        }
        if (now % 2 == 0 && projectileIncoming()) {
            schedule(Reaction.DODGE_SIDE, reacoes.chanceEsquivarProjetil(), fase);
        }
    }

    private void onTargetCast(int slot, double distance, BossSpec.Reacoes reacoes, BossSpec.Fase fase) {
        if (PowerData.isUlt(slot)) {
            // They spent their ult: get out of it, then make them pay.
            punishLeft = reacoes.punirTicks();
            schedule(Reaction.DODGE_BACK, reacoes.chanceEsquivarUlt(), fase);
        } else if ((slot == PowerData.SLOT_STRONG || slot == PowerData.SLOT_BARRAGE) && distance <= 6) {
            // A heavy hit is winding up right next to it: counter it.
            schedule(Reaction.COUNTER, reacoes.chanceContraAtaque(), fase);
        } else if (distance <= 30) {
            schedule(Reaction.DODGE_SIDE, reacoes.chanceEsquivarSkill(), fase);
        }
    }

    public void onHurt(LivingEntity attacker) {
        if (attacker == target) {
            pressure.addLast(boss.level().getGameTime());
        }
    }

    // Reactions wait reacaoTicks like a human would, and only some happen (chance x the phase's chanceReacao).
    private void schedule(Reaction r, double chance, BossSpec.Fase fase) {
        if (random.nextDouble() >= chance * fase.chanceReacao() * (chaos ? CHAOS_REACTION : 1)) {
            return;
        }
        if (reaction == null || r == Reaction.COUNTER) {
            reaction = r;
            reactAt = now + Math.max(0, fase.reacaoTicks());
        }
    }

    private boolean react(Reaction r, BossSpec spec, BossSpec.Fase fase, double distance) {
        if (r == Reaction.COUNTER) {
            String counter = firstOfType(spec, fase, "contra");
            if (counter != null && off(spec, counter) && distance <= spec.skills().get(counter).distanciaMax()) {
                plan.clear();
                state = State.IDLE;
                return begin(spec, counter);
            }
            r = Reaction.ESCAPE;
        }
        if (spec.god()) {
            // A god doesn't dodge or run: at most he reappears behind whoever is bothering him.
            boolean step = r == Reaction.ESCAPE || random.nextDouble() < spec.passoSombra().chanceNaEsquiva();
            if (step && shadowStep(spec, Math.max(distance, spec.passoSombra().distanciaMin()))) {
                plan.clear();
                state = State.IDLE;
                return true;
            }
            return false;
        }
        if (r == Reaction.ESCAPE) {
            return escape(spec, distance);
        }
        if (state == State.TELL && r != Reaction.COUNTER) {
            state = State.IDLE;
        }
        if (random.nextDouble() < spec.passoSombra().chanceNaEsquiva() && shadowStep(spec, distance)) {
            plan.clear();
            return true;
        }
        face(Combat.center(target), 360);
        float side = r == Reaction.DODGE_SIDE ? (random.nextBoolean() ? 1 : -1) : 0;
        if (Movement.tryPerform(boss, Movement.Kind.ESQUIVA, -1, side)) {
            plan.clear();
            thinking = "esquiva";
            return true;
        }
        return false;
    }

    // Getting out of a beating, picked at random so it can't be read: back dodge into a backwards air jump, a side
    // dodge, or a shadow step behind the attacker.
    private boolean escape(BossSpec spec, double distance) {
        if (state == State.TELL) {
            state = State.IDLE;
        }
        plan.clear();
        int pick = random.nextInt(3);
        if (pick == 2 && shadowStep(spec, Math.max(distance, spec.passoSombra().distanciaMin()))) {
            return true;
        }
        face(Combat.center(target), 360);
        float side = pick == 1 ? (random.nextBoolean() ? 1 : -1) : 0;
        if (Movement.tryPerform(boss, Movement.Kind.ESQUIVA, -1, side)) {
            if (pick == 0) {
                jumpPending = true;
                jumpBack = true;
            }
            thinking = "fugindo";
            return true;
        }
        return dash(spec);
    }

    // Something thrown at him (a meteor, a giant fireball, a slash, another sphere): the hakaizinho goes straight at it
    // and pulverizes it. Returns true when he started the shot.
    private boolean shootDown(BossSpec spec, BossSpec.Fase fase) {
        String anti = firstOfType(spec, fase, "anti");
        if (anti == null || !off(spec, anti) || now % 2 != 0) {
            return false;
        }
        BossSpec.Skill skill = spec.skills().get(anti);
        Destructible.Attack attack = Destructible.nearestThreat(level(), boss, skill.distanciaMax());
        if (attack == null) {
            return false;
        }
        plan.clear();
        comboName = null;
        state = State.IDLE;
        antiTarget = attack;
        Hakaizinho.aimAt(boss, attack::pos);
        move = anti;
        halt();
        if (!execute(spec)) {
            Hakaizinho.aimAt(boss, null);
            antiTarget = null;
            return false;
        }
        thinking = "hakaizinho no ataque";
        return true;
    }

    private boolean erasable() {
        return HakaiSpec.get(boss.powerData().awakened).canErase(target);
    }

    private double lifeOf(LivingEntity entity) {
        return entity.getHealth() / Math.max(1, entity.getMaxHealth());
    }

    private boolean facesBoss(LivingEntity entity) {
        Vec3 to = boss.position().subtract(entity.position());
        to = new Vec3(to.x, 0, to.z);
        return to.lengthSqr() < 1.0E-4 || Combat.flatLook(entity).dot(to.normalize()) > 0.5;
    }

    // Arrows, fireballs and the like flying at it.
    private boolean projectileIncoming() {
        Vec3 center = Combat.center(boss);
        for (Projectile projectile : boss.level().getEntitiesOfClass(Projectile.class, boss.getBoundingBox().inflate(10))) {
            if (projectile.getOwner() == boss || projectile.onGround()) {
                continue;
            }
            Vec3 velocity = projectile.getDeltaMovement();
            Vec3 to = center.subtract(projectile.position());
            if (velocity.lengthSqr() > 0.04 && velocity.normalize().dot(to.normalize()) > 0.9) {
                return true;
            }
        }
        return false;
    }

    private boolean targetStunned() {
        MobEffectInstance slow = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return Freeze.isFrozen(target) || (slow != null && slow.getAmplifier() >= 3);
    }

    private boolean targetBusy() {
        return target.hasData(ModAttachments.POWER) && target.getData(ModAttachments.POWER).busyTicks > 0;
    }

    // ---------------------------------------------------------------- choosing

    private void decide(BossSpec spec, BossSpec.Fase fase, double distance) {
        List<Option> options = options(spec, fase, distance);
        if (!chaos) {
            // Waiting is an option too: less aggressive phases breathe more between moves. (Chaos never waits.)
            options.add(new Option("esperar", (1 - fase.agressividade()) * 0.6, false));
        }
        options.sort(Comparator.comparingDouble(Option::score).reversed());
        thinking = summary(options);
        // Chaos: always a roll, never "the best one".
        Option pick = pick(options, chaos ? 1 : spec.percepcao().aleatoriedade());
        if (pick == null || pick.name().equals("esperar")) {
            return;
        }
        if (pick.combo()) {
            BossSpec.Combo combo = spec.combos().get(pick.name());
            plan.clear();
            plan.addAll(combo.passos());
            comboName = pick.name();
            stepAge = 0;
            runStep(spec, fase, distance);
        } else {
            begin(spec, pick.name());
        }
    }

    private List<Option> options(BossSpec spec, BossSpec.Fase fase, double distance) {
        List<Option> options = new ArrayList<>();
        for (String name : fase.skills()) {
            double score = score(spec, fase, name, distance);
            if (score > 0 && tactics() != null) {
                score = tactics().weigh(this, name, false, score);
            }
            score = crazy(score);
            if (score > 0) {
                options.add(new Option(name, score, false));
            }
        }
        if (fase.combos() != null) {
            for (String name : fase.combos()) {
                BossSpec.Combo combo = spec.combos().get(name);
                if (combo == null || combo.passos().isEmpty()) {
                    continue;
                }
                // A combo is as good as its opener, a bit better since it is a plan.
                double score = openerScore(spec, fase, combo.passos().get(0), distance) * combo.peso() * 1.2;
                if (punishLeft > 0) {
                    score *= spec.reacoes().multPunir();
                }
                if (combo.chained() && !allOff(spec, combo)) {
                    score = 0;
                }
                if (score > 0 && tactics() != null) {
                    score = tactics().weigh(this, name, true, score);
                }
                score = crazy(score);
                if (score > 0 && !recent.contains(name)) {
                    options.add(new Option(name, score, true));
                }
            }
        }
        return options;
    }

    // Chaos: what the reading of the fight said only decides whether a move is ALLOWED (0 stays 0: out of range, on
    // cooldown, an ult outside its moment, the other sword's forms). How much it is wanted is mostly luck, flattened so
    // a weak option is still picked often.
    private double crazy(double score) {
        if (!chaos || score <= 0) {
            return score;
        }
        return Math.pow(score, 0.25) * (0.3 + 1.4 * random.nextDouble());
    }

    private double openerScore(BossSpec spec, BossSpec.Fase fase, String step, double distance) {
        return switch (step) {
            case "soco" -> distance <= spec.distancias().soco() + 1 ? 1 : 0.5 * fase.agressividade();
            case "investida", "aproximar" -> distance > spec.investida().distanciaMin() && dashCooldown == 0 ? 0.9 : 0.3;
            case "sombra" -> shadowCooldown == 0 && distance >= spec.passoSombra().distanciaMin()
                    && distance <= spec.passoSombra().distanciaMax() ? (fast ? 1.2 : 0.6) : 0.2;
            case "esquiva", "pulo" -> 0.2;
            default -> {
                BossSpec.Skill skill = spec.skills().get(step);
                if (skill == null) {
                    yield 0;
                }
                // A scripted step opens its combo whenever it is off cooldown (the tactics decide when it is time).
                yield "roteiro".equals(skill.tipo()) ? (off(spec, step) ? skill.peso() : 0) : score(spec, fase, step, distance);
            }
        };
    }

    // Every technique of the combo off cooldown.
    private boolean allOff(BossSpec spec, BossSpec.Combo combo) {
        for (String step : combo.passos()) {
            if (spec.skills().containsKey(step) && !off(spec, step)) {
                return false;
            }
        }
        return true;
    }

    // How good a move is right now. 0 = can't or shouldn't.
    private double score(BossSpec spec, BossSpec.Fase fase, String name, double distance) {
        BossSpec.Skill skill = spec.skills().get(name);
        if (skill == null || !ready(spec, name)) {
            return 0;
        }
        String tipo = skill.tipo();
        if ("roteiro".equals(tipo)) {
            // Only as a step of its combo.
            return 0;
        }
        boolean melee = "golpe".equals(tipo) || "barragem".equals(tipo);
        if ("hakai".equals(tipo) && erasable()) {
            // Low enough to be erased: nothing is worth more, from as far as the Hakai reaches.
            double reach = HakaiSpec.get(boss.powerData().awakened).alvo().alcance();
            return distance <= reach && boss.hasLineOfSight(target) ? skill.peso() * 10 : 0;
        }
        boolean sees = boss.hasLineOfSight(target) || tactics() != null && tactics().throughWalls(this, name);
        if (skill.needsStun()) {
            // Only on a stunned target, and then nothing is better while the stun lasts.
            if (!Stamina.stunnable(target) || distance > skill.distanciaMax() || !sees) {
                return 0;
            }
            return skill.peso() * 8;
        }
        if (!"contra".equals(tipo) && (distance < skill.distanciaMin() || distance > skill.distanciaMax() || !sees)) {
            return 0;
        }
        boolean stunned = targetStunned();
        boolean guarded = Susanoo.isReady(target);
        double score = skill.peso();
        switch (tipo) {
            case "alcance" -> {
                score *= stunned ? 1.8 : 1;
                score *= targetBusy() ? 1.3 : 1;
                score *= punishLeft > 0 ? 1.5 : 1;
                score *= guarded ? 2 : 1;
                score *= 1.5 - fase.agressividade();
            }
            case "controle" -> {
                if (stunned) {
                    return 0;
                }
                score *= punishLeft > 0 ? spec.reacoes().multPunir() : 1;
                score *= targetBusy() ? 1.5 : 1;
                score *= pressure.isEmpty() ? 1 : 1.5;
            }
            case "contra" -> {
                // Only worth it while being pressed up close.
                if (distance > skill.distanciaMax() || pressure.size() < Math.max(1, spec.reacoes().golpesParaContra() - 1)) {
                    return 0;
                }
                score *= 1.5;
            }
            case "hakai" -> {
                // The hakai ball: kept for when it is far enough to matter and they still have plenty of life (lower
                // than that he waits to erase them instead).
                if (lifeOf(target) < 0.35) {
                    return 0;
                }
                score *= punishLeft > 0 ? 1.5 : 1;
                score *= targetBusy() ? 1.3 : 1;
            }
            case "livre" -> {
                // No rules: whenever it is ready and in range, a random weight that changes every decision.
                return skill.peso() * (0.5 + random.nextDouble());
            }
            case "anti" -> {
                // Shot at attacks on its own (shootDown); as a move only to finish someone low who is far away.
                if (lifeOf(target) >= 0.2 || distance < 8) {
                    return 0;
                }
                score *= 2;
            }
            case "izanami" -> {
                // The escape from death: only from half life down, and best while being pressed.
                if (boss.getHealth() / boss.getMaxHealth() > 0.5) {
                    return 0;
                }
                score *= pressure.isEmpty() ? 1 : 2;
            }
            case "ult" -> {
                score *= stunned ? 3 : distance < 8 ? 0.3 : 1;
                score *= boss.getHealth() / boss.getMaxHealth() < 0.5 ? 1.5 : 1;
            }
            default -> {
                if (guarded && melee) {
                    // Never punch into a counter stance.
                    return 0;
                }
                score *= stunned ? 2.2 : 1;
                score *= 0.5 + fase.agressividade();
            }
        }
        if (recent.contains(name)) {
            score *= 0.35;
        }
        return score;
    }

    // Best move most of the time; otherwise a weighted roll among the rest.
    private Option pick(List<Option> sorted, double randomness) {
        if (sorted.isEmpty()) {
            return null;
        }
        if (random.nextDouble() >= randomness) {
            return sorted.get(0);
        }
        double total = sorted.stream().mapToDouble(Option::score).sum();
        double roll = random.nextDouble() * total;
        for (Option option : sorted) {
            roll -= option.score();
            if (roll <= 0) {
                return option;
            }
        }
        return sorted.get(0);
    }

    // Off cooldown and, for a special technique, past the gap since the last one.
    private boolean ready(BossSpec spec, String name) {
        BossSpec.Skill skill = spec.skills().get(name);
        return off(spec, name) && (techniqueGap <= 0 || basic(skill) || tactics() != null && tactics().basic(name));
    }

    // Only the skill's own cooldown: the defenses (counter, hakaizinho on an attack) don't wait for the gap.
    private boolean off(BossSpec spec, String name) {
        BossSpec.Skill skill = spec.skills().get(name);
        return skill != null && boss.powerData().cooldowns[skill.slot()] <= 0;
    }

    // Strong and barrage are base attacks everyone has, not techniques.
    private static boolean basic(BossSpec.Skill skill) {
        return skill.slot() == PowerData.SLOT_STRONG || skill.slot() == PowerData.SLOT_BARRAGE;
    }

    private static String firstOfType(BossSpec spec, BossSpec.Fase fase, String tipo) {
        for (String name : fase.skills()) {
            BossSpec.Skill skill = spec.skills().get(name);
            if (skill != null && tipo.equals(skill.tipo())) {
                return name;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- using a move

    // Starts the warning for a move; the move itself fires when the warning ends.
    private boolean begin(BossSpec spec, String name) {
        BossSpec.Skill skill = spec.skills().get(name);
        if (skill == null || !off(spec, name)) {
            return false;
        }
        move = name;
        tellTotal = Math.max(0, (int) Math.round(skill.avisoTicks() * boss.fase().multAviso() * (chaos ? CHAOS_TELL : 1)));
        tellLeft = tellTotal;
        halt();
        if (tellTotal == 0) {
            return execute(spec);
        }
        state = State.TELL;
        BossFx.tellStart(level(), boss, skill.avisoSom());
        return true;
    }

    private void tell(BossSpec spec, double distance) {
        BossSpec.Skill skill = spec.skills().get(move);
        face(aimPoint(skill), skill.giroGrausPorTick() * 2);
        BossFx.tell(level(), boss, skill.tipo(), BossFx.color(skill.avisoCor()), 1 - tellLeft / (double) Math.max(1, tellTotal));
        if (--tellLeft <= 0) {
            execute(spec);
        }
    }

    private boolean execute(BossSpec spec) {
        BossSpec.Skill skill = spec.skills().get(move);
        face(aimPoint(skill), 360);
        if (tactics() != null && !chaos) {
            // (Chaos: no plan for the charge, each technique uses its own random.)
            BossOrder.give(boss, tactics().order(this, move));
        }
        boolean cast = PowerLogic.cast(boss, skill.slot());
        // Whatever the move didn't take is dropped, so it can't reach a later one.
        BossOrder.clear(boss);
        if (!cast) {
            state = State.IDLE;
            return false;
        }
        int cooldown = skill.recargaTicks() + (skill.recargaMaxTicks() > skill.recargaTicks()
                ? random.nextInt(skill.recargaMaxTicks() - skill.recargaTicks() + 1) : 0);
        BossSpec.Fase fase = boss.fase();
        // Shift variants keep the player's cooldowns: the phases don't shorten them.
        boss.powerData().cooldowns[skill.slot()] = skill.slot() >= PowerData.SLOT_SHIFT ? cooldown
                : Math.max(20, (int) Math.round(cooldown * fase.multRecarga()) - fase.menosRecargaTicks());
        if ("golpe".equals(skill.tipo()) || "barragem".equals(skill.tipo())) {
            boss.swing(InteractionHand.MAIN_HAND);
        }
        if (!basic(skill) && !(tactics() != null && tactics().basic(move))) {
            techniqueGap = chaos ? spec.percepcao().techniqueGap() / CHAOS_GAP_DIV : spec.percepcao().techniqueGap();
        }
        remember(move);
        state = State.ACTION;
        actionAge = 0;
        return true;
    }

    private void remember(String name) {
        recent.addLast(name);
        while (recent.size() > 2) {
            recent.pollFirst();
        }
    }

    // While a move plays it keeps turning towards the target, at that move's speed.
    private void keepAiming(BossSpec.Skill skill) {
        if (Freeze.isFrozen(boss)) {
            return;
        }
        boss.getNavigation().stop();
        face(aimPoint(skill), skill == null ? 10 : skill.giroGrausPorTick());
    }

    // The ult aims at the feet (it lands on the ground); everything else at the body.
    private Vec3 aimPoint(BossSpec.Skill skill) {
        if (skill != null && "anti".equals(skill.tipo()) && antiTarget != null && !antiTarget.gone()) {
            return antiTarget.pos();
        }
        Vec3 own = skill != null && move != null && tactics() != null ? tactics().aim(this, move) : null;
        if (own != null) {
            return own;
        }
        return skill != null && "ult".equals(skill.tipo()) ? target.position() : Combat.center(target);
    }

    // Turns body, head and pitch together, at most maxTurn degrees this tick. Skills aim with these angles.
    private void face(Vec3 point, float maxTurn) {
        Vec3 delta = point.subtract(boss.getEyePosition());
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) -(Mth.atan2(delta.y, delta.horizontalDistance()) * Mth.RAD_TO_DEG);
        float newYaw = Mth.approachDegrees(boss.getYRot(), yaw, maxTurn);
        boss.setYRot(newYaw);
        boss.setYHeadRot(newYaw);
        boss.setYBodyRot(newYaw);
        boss.setXRot(Mth.approachDegrees(boss.getXRot(), pitch, maxTurn));
    }

    // ---------------------------------------------------------------- combos

    // Runs the current combo step. Returns true when it used this tick.
    private boolean runStep(BossSpec spec, BossSpec.Fase fase, double distance) {
        String step = plan.peekFirst();
        if (step == null) {
            return false;
        }
        ++stepAge;
        Boolean own = tactics() != null ? tactics().step(this, step, stepAge) : null;
        if (own != null) {
            // A step of the tactics: they keep their own time.
            if (own) {
                nextStep();
            }
            return true;
        }
        if (stepAge > STEP_TIMEOUT) {
            dropCombo();
            return false;
        }
        double reach = spec.distancias().soco();
        switch (step) {
            case "soco" -> {
                if (distance > reach) {
                    approach(spec, distance, true);
                    return true;
                }
                if (punch()) {
                    nextStep();
                }
                return true;
            }
            case "aproximar" -> {
                if (distance <= reach) {
                    nextStep();
                    return false;
                }
                approach(spec, distance, true);
                return true;
            }
            case "investida" -> {
                if (distance <= reach + 1 || dash(spec)) {
                    nextStep();
                } else {
                    approach(spec, distance, false);
                }
                return true;
            }
            case "sombra" -> {
                if (distance <= reach || shadowStep(spec, distance)) {
                    nextStep();
                } else if (stepAge > 20 && distance > reach + 1) {
                    if (dash(spec)) {
                        nextStep();
                    } else {
                        approach(spec, distance, true);
                    }
                } else {
                    approach(spec, distance, true);
                }
                return true;
            }
            case "esquiva" -> {
                face(Combat.center(target), 360);
                if (Movement.backstep(boss) || stepAge > 10) {
                    nextStep();
                }
                return true;
            }
            case "pulo" -> {
                if (jump()) {
                    nextStep();
                }
                return true;
            }
            default -> {
                BossSpec.Skill skill = spec.skills().get(step);
                BossSpec.Combo combo = comboName != null ? spec.combos().get(comboName) : null;
                boolean chained = combo != null && combo.chained();
                if (skill == null || !(chained ? off(spec, step) : ready(spec, step))) {
                    // Not available (or locked by a stun): skip the step, keep the combo going.
                    nextStep();
                    return false;
                }
                if (skill.needsStun()) {
                    // A step that needs a stunned target: used the moment they are open, skipped if they never are.
                    if (!Stamina.stunnable(target)) {
                        if (stepAge > 10) {
                            nextStep();
                        }
                        return false;
                    }
                    // (No sight needed: right after a slam the target may be down in its hole.)
                    if (distance > skill.distanciaMax()) {
                        nextStep();
                        return false;
                    }
                    nextStep();
                    begin(spec, step);
                    return true;
                }
                if ("roteiro".equals(skill.tipo())) {
                    // Scripted: no range or sight to wait for.
                    nextStep();
                    begin(spec, step);
                    return true;
                }
                if (distance > skill.distanciaMax() || !boss.hasLineOfSight(target) && !(tactics() != null && tactics().throughWalls(this, step))) {
                    approach(spec, distance, distance > spec.investida().distanciaMin());
                    return true;
                }
                if (distance < skill.distanciaMin()) {
                    Movement.backstep(boss);
                    return true;
                }
                nextStep();
                begin(spec, step);
                return true;
            }
        }
    }

    private void nextStep() {
        plan.pollFirst();
        stepAge = 0;
        if (plan.isEmpty() && comboName != null) {
            remember(comboName);
            comboName = null;
        }
    }

    public void dropCombo() {
        plan.clear();
        comboName = null;
        stepAge = 0;
    }

    // ---------------------------------------------------------------- moving around

    private void position(BossSpec spec, BossSpec.Fase fase, double distance) {
        if (spec.god()) {
            walkCalmly(spec, distance);
            return;
        }
        if (--moodAge <= 0) {
            moodAge = chaos ? Math.max(8, spec.percepcao().trocarHumorTicks() / CHAOS_DECISION_DIV)
                    : Math.max(20, spec.percepcao().trocarHumorTicks());
            double aggression = chaos ? Math.max(CHAOS_CLOSE, fase.agressividade()) : fase.agressividade();
            mood = random.nextDouble() < aggression ? Mood.CLOSE : Mood.RANGE;
        }
        boolean close = mood == Mood.CLOSE || punishLeft > 0 || targetStunned();
        if (Susanoo.isReady(target) && !chaos) {
            // Their counter is up: back off and wait it out.
            close = false;
        }
        boolean sees = boss.hasLineOfSight(target);
        if (unreachable(spec, sees, distance)) {
            return;
        }
        double heightGap = target.getY() - boss.getY();
        if (heightGap >= spec.puloDuplo().alturaMin() && horizontal(distance) <= spec.puloDuplo().distanciaMax() && jump()) {
            return;
        }
        if (chaos && sees && boss.tickCount % CHAOS_BURST_TICKS == 0 && random.nextDouble() < CHAOS_BURST_CHANCE
                && burst(spec, distance)) {
            return;
        }
        if (close && sees && boss.tickCount % SHADOW_CHECK_TICKS == 0) {
            BossSpec.PassoSombra sombra = spec.passoSombra();
            double chance = fast ? sombra.chance() : sombra.chanceCalmo();
            if (random.nextDouble() < (chaos ? chance * 2 : chance) && shadowStep(spec, distance)) {
                return;
            }
        }
        if (close) {
            if (distance > spec.investida().distanciaMin() && sees && dash(spec)) {
                return;
            }
            if (distance > spec.distancias().soco()) {
                approach(spec, distance, true);
            } else {
                halt();
                face(Combat.center(target), 30);
                punch();
            }
            return;
        }
        double ideal = spec.distancias().ideal();
        if (!sees || distance > ideal + 4) {
            approach(spec, distance, false);
            return;
        }
        if (++strafeAge > spec.movimento().trocarLadoTicks() || boss.horizontalCollision) {
            strafeAge = 0;
            strafeSide = -strafeSide;
        }
        boss.getNavigation().stop();
        float forward = distance < ideal - 2 ? -0.6f : distance > ideal + 2 ? 0.4f : 0;
        double floor = voidFloor();
        if (!Double.isNaN(floor)) {
            // On an expansion's floor a strafe would read the air under it as a cliff and only walk forward: it steps to
            // a point beside it instead.
            Vec3 to = target.position().subtract(boss.position());
            Vec3 ahead = new Vec3(to.x, 0, to.z);
            ahead = ahead.lengthSqr() > 1.0E-4 ? ahead.normalize() : Combat.flatLook(boss);
            Vec3 side = new Vec3(-ahead.z, 0, ahead.x).scale(strafeSide * 0.6 * 2);
            Vec3 spot = boss.position().add(ahead.scale(forward * 2)).add(side);
            if (!DomainSpace.keepsInside(boss, spot)) {
                // At the edge of the expansion (past it Gojo's shatters, Sukuna's barrier throws him back): the other way round.
                strafeSide = -strafeSide;
                spot = boss.position().add(ahead.scale(forward * 2)).subtract(side);
            }
            boss.getMoveControl().setWantedPosition(spot.x, floor, spot.z, spec.movimento().velocidadeAndar());
        } else {
            boss.getMoveControl().strafe(forward, strafeSide * 0.6f);
        }
        boss.lookAt(target, 30, 30);
        boss.setYHeadRot(boss.getYRot());
        if (distance <= spec.distancias().soco()) {
            punch();
        }
    }

    // A god between moves: no strafing, no dashes. He walks straight at the target, punches when it is in reach, and
    // now and then simply appears behind it.
    private void walkCalmly(BossSpec spec, double distance) {
        boolean sees = boss.hasLineOfSight(target);
        if (unreachable(spec, sees, distance)) {
            return;
        }
        double reach = spec.distancias().soco();
        if (sees && boss.tickCount % SHADOW_CHECK_TICKS == 0 && distance > reach + 1
                && random.nextDouble() < spec.passoSombra().chanceCalmo() && shadowStep(spec, distance)) {
            return;
        }
        if (distance > reach) {
            approach(spec, distance, false);
            return;
        }
        halt();
        face(Combat.center(target), 30);
        punch();
    }

    // Chaos: a move out of nowhere between attacks, so the next second can't be read. Each one has its own cooldown
    // (Movement, dash, shadow step), so a burst that isn't ready simply doesn't happen.
    private boolean burst(BossSpec spec, double distance) {
        switch (random.nextInt(5)) {
            case 0 -> {
                face(Combat.center(target), 360);
                if (Movement.tryPerform(boss, Movement.Kind.DESVIO, 0, random.nextBoolean() ? 1 : -1)) {
                    thinking = "caos: esquiva do lado";
                    return true;
                }
            }
            case 1 -> {
                face(Combat.center(target), 360);
                if (Movement.tryPerform(boss, Movement.Kind.ESQUIVA, -1, 0)) {
                    jumpPending = true;
                    jumpBack = random.nextBoolean();
                    thinking = "caos: pra tras e pro alto";
                    return true;
                }
            }
            case 2 -> {
                if (jump()) {
                    thinking = "caos: pulo duplo";
                    return true;
                }
            }
            case 3 -> {
                if (distance > spec.distancias().soco() + 1 && dash(spec)) {
                    thinking = "caos: investida";
                    return true;
                }
            }
            default -> {
                if (shadowStep(spec, Math.max(distance, spec.passoSombra().distanciaMin()))) {
                    thinking = "caos: passo sombra";
                    return true;
                }
            }
        }
        return false;
    }

    private void approach(BossSpec spec, double distance, boolean run) {
        run &= !spec.god();
        boss.getLookControl().setLookAt(target, 30, 30);
        double floor = voidFloor();
        if (!Double.isNaN(floor)) {
            // Up in an expansion (Gojo's or Sukuna's) there are no blocks, so no path: it walks straight at them over the floor.
            BossSpec.Movimento movimento = spec.movimento();
            boss.getNavigation().stop();
            boss.getMoveControl().setWantedPosition(target.getX(), floor, target.getZ(),
                    run ? movimento.velocidadeCorrer() : movimento.velocidadeAndar());
            return;
        }
        if (--repath <= 0 || boss.getNavigation().isDone()) {
            repath = REPATH_TICKS;
            BossSpec.Movimento movimento = spec.movimento();
            boss.getNavigation().moveTo(target, run ? movimento.velocidadeCorrer() : movimento.velocidadeAndar());
        }
    }

    private boolean punch() {
        if (punchCooldown > 0 || boss.powerData().punchDelay > 0 || punchReadyAt < 0 || now < punchReadyAt) {
            return false;
        }
        // Turns towards them like a fighter would, not in one tick; punches once roughly facing them.
        face(Combat.center(target), PUNCH_TURN);
        Vec3 to = Combat.center(target).subtract(boss.getEyePosition());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.lengthSqr() > 1.0E-4 && flat.normalize().dot(Combat.flatLook(boss)) < PUNCH_FACING) {
            return false;
        }
        if (!Melee.normalHit(boss, target)) {
            return false;
        }
        punchCooldown = Math.max(1, boss.spec().distancias().intervaloSocoTicks());
        return true;
    }

    // A forward dash to close the gap fast.
    private boolean dash(BossSpec spec) {
        BossSpec.Investida investida = spec.investida();
        if (spec.god() || dashCooldown > 0 || !boss.onGround() || !Movement.free(boss)) {
            return false;
        }
        face(Combat.center(target), 360);
        boss.getNavigation().stop();
        Vec3 dir = Combat.flatLook(boss);
        boss.setDeltaMovement(dir.x * investida.forca(), investida.altura(), dir.z * investida.forca());
        boss.hasImpulse = true;
        boss.hurtMarked = true;
        dashCooldown = fast ? spec.ritmo().recargaInvestidaRapido() : investida.recargaTicks();
        if (chaos) {
            dashCooldown /= 2;
        }
        BossFx.dash(level(), boss, BossFx.color(boss.spec().intro().cor()));
        thinking = "investida";
        return true;
    }

    // Shadow step: a blink to just behind the target, leaving an afterimage trail, then straight into punches.
    private boolean shadowStep(BossSpec spec, double distance) {
        BossSpec.PassoSombra sombra = spec.passoSombra();
        if (shadowCooldown > 0 || distance < sombra.distanciaMin() || distance > sombra.distanciaMax() || Freeze.isFrozen(boss)) {
            return false;
        }
        Vec3 from = boss.position();
        Vec3 back = Combat.flatLook(target).reverse();
        for (double turn : new double[] {0, 35, -35}) {
            Vec3 spot = target.position().add(back.yRot((float) Math.toRadians(turn)).scale(sombra.atras()));
            if (!boss.level().noCollision(boss, boss.getBoundingBox().move(spot.subtract(from)))
                    || boss.level().noCollision(boss, boss.getBoundingBox().move(spot.subtract(from)).move(0, -1.5, 0))
                            && !DomainSpace.onFloor(boss, spot)
                    || !DomainSpace.keepsInside(boss, spot)) {
                // Blocked, nothing to stand on (an expansion's floor counts), or past its barrier.
                continue;
            }
            boss.teleportTo(spot.x, spot.y, spot.z);
            boss.getNavigation().stop();
            face(Combat.center(target), 360);
            Intangible.hold(boss, 3);
            BossFx.shadowStep(level(), from, spot, BossFx.color(spec.intro().cor()));
            shadowCooldown = chaos ? sombra.recargaTicks() / 2 : sombra.recargaTicks();
            punchCooldown = 0;
            lastBossPos = boss.position();
            punchReadyAt = now + 2;
            mood = Mood.CLOSE;
            thinking = "passo sombra";
            return true;
        }
        return false;
    }

    // Jump now, air jump as soon as it leaves the ground (countDown does it).
    private boolean jump() {
        if (boss.spec().god() || !boss.onGround() || !Movement.free(boss) || boss.getData(ModAttachments.MOVEMENT).airJumpUsed) {
            return false;
        }
        face(Combat.center(target), 360);
        boss.getJumpControl().jump();
        jumpPending = true;
        thinking = "pulo duplo";
        return true;
    }

    // Can't see or reach the target for a while: reappear behind them.
    private boolean unreachable(BossSpec spec, boolean sees, double distance) {
        BossSpec.Teleporte teleporte = spec.teleporte();
        // (In an expansion it walks without a path, so a finished path means nothing there.)
        boolean stalled = !sees || (distance > spec.distancias().ideal() + 4 && boss.getNavigation().isDone()
                && Double.isNaN(voidFloor()));
        stuck = stalled ? stuck + 1 : 0;
        if (!teleporte.ativo() || stuck < teleporte.presoTicks()) {
            return false;
        }
        stuck = 0;
        Vec3 behind = target.position().subtract(Combat.flatLook(target).scale(teleporte.distancia()));
        Vec3 from = boss.position();
        Vec3 front = target.position().add(Combat.flatLook(target).scale(teleporte.distancia()));
        for (Vec3 spot : new Vec3[] {behind, front}) {
            if (boss.level().noCollision(boss, boss.getBoundingBox().move(spot.subtract(from))) && DomainSpace.keepsInside(boss, spot)) {
                boss.teleportTo(spot.x, spot.y, spot.z);
                boss.getNavigation().stop();
                face(Combat.center(target), 360);
                BossFx.teleport(level(), from, spot, BossFx.color(boss.spec().intro().cor()));
                thinking = "teleporte";
                return true;
            }
        }
        return false;
    }

    // The height of the floor while it is up in the space of an expansion, NaN anywhere else (DomainSpace.floorY).
    private double voidFloor() {
        return DomainSpace.floorY(boss);
    }

    // Stands where it is. On an expansion's floor a strafe of nothing reads the air under it as a cliff and walks
    // forward, so it is told to stay at its own spot there.
    public void halt() {
        boss.getNavigation().stop();
        if (!Double.isNaN(voidFloor())) {
            boss.getMoveControl().setWantedPosition(boss.getX(), boss.getY(), boss.getZ(), 0);
            boss.setXxa(0);
            boss.setZza(0);
        } else {
            boss.getMoveControl().strafe(0, 0);
        }
    }

    private double horizontal(double distance) {
        return Math.sqrt(Math.max(0, distance * distance - Math.pow(target.getY() - boss.getY(), 2)));
    }

    private ServerLevel level() {
        return Combat.level(boss);
    }

    // ---------------------------------------------------------------- what the tactics read

    private BossTactics tactics() {
        if (!tacticsAsked) {
            tacticsAsked = true;
            tactics = boss.tactics();
        }
        return tactics;
    }

    // A finishing combo (BossSpec.Combo.ult) is running.
    private boolean finishing() {
        BossSpec.Combo combo = comboName != null ? boss.spec().combos().get(comboName) : null;
        return combo != null && combo.finisher() && !plan.isEmpty();
    }

    public SoulBoss boss() {
        return boss;
    }

    public LivingEntity target() {
        return target;
    }

    public long now() {
        return now;
    }

    // The combo running now, or null.
    public String combo() {
        return plan.isEmpty() ? null : comboName;
    }

    // Hits and swings it took up close lately.
    public int pressure() {
        return pressure.size();
    }

    // The target just spent their ult: time to make them pay.
    public boolean punishing() {
        return punishLeft > 0;
    }

    public boolean stunned() {
        return targetStunned();
    }

    public boolean busyTarget() {
        return targetBusy();
    }

    public boolean usingMove() {
        return state != State.IDLE;
    }

    // /gamerule soulbotscaoticos: no strategy, pure chaos. Tactics skip their waiting and keeping distance under it.
    public boolean chaos() {
        return chaos;
    }

    public boolean isOff(String name) {
        return off(boss.spec(), name);
    }

    // The move is one this phase may use (under /gamerule soulbossfight false: every move of the boss).
    public boolean has(String name) {
        return boss.fase().skills().contains(name);
    }

    // The tactics start a move now, dropping what it was about to do (an answer that can't wait for the next decision:
    // the Susanoo against an attack flying at it). Only between moves, and only with the move off cooldown.
    public boolean use(String name) {
        if (state != State.IDLE || !has(name) || !off(boss.spec(), name)) {
            return false;
        }
        plan.clear();
        comboName = null;
        return begin(boss.spec(), name);
    }

    public void lookAt(Vec3 point, float maxTurn) {
        face(point, maxTurn);
    }

    public boolean stepBehind() {
        return shadowStep(boss.spec(), boss.distanceTo(target));
    }

    public void say(String text) {
        thinking = text;
    }

    // ---------------------------------------------------------------- debug

    private static String summary(List<Option> sorted) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            Option option = sorted.get(i);
            text.append(i == 0 ? "" : "  ").append(option.combo() ? "[" + option.name() + "]" : option.name())
                    .append(' ').append(String.format("%.2f", option.score()));
        }
        return text.toString();
    }

    private void showDebug(BossSpec.Fase fase, double distance) {
        String what = switch (state) {
            case TELL -> "aviso " + move;
            case ACTION -> "usando " + move;
            default -> comboName != null ? "combo " + comboName + " > " + plan.peekFirst() : mood == Mood.CLOSE ? "perto" : "distancia";
        };
        Component line = Component.literal(String.format("§cFase %d §7| %s §7| §f%s §7| dist %.1f | pressao %d%s §7| %s",
                boss.phase() + 1, fast ? "§eRAPIDO" : "§acalmo", what, distance, pressure.size(), punishLeft > 0 ? " §6PUNIR" : "",
                tactics() != null ? thinking + " §b" + tactics().mood() : thinking));
        for (ServerPlayer player : level().players()) {
            if (player.distanceTo(boss) <= DEBUG_RANGE) {
                player.displayClientMessage(line, true);
            }
        }
    }
}
