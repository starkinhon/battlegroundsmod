package com.starkinhon.battlegroundsmod.boss;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Melee;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.uchiha.Susanoo;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
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
// - between moves it positions itself: rushes in with dashes and punches when it feels aggressive, keeps range and
//   strafes otherwise, double jumps to targets standing up high, and reappears behind a target it can't reach.
// Every number comes from the boss file (BossSpec). /soul boss debug shows what it is thinking.
public class SoulBossAI extends Goal {
    // Toggled by /soul boss debug: each boss shows its state and best options on the action bar of players nearby.
    public static boolean debug = false;

    private static final double DEBUG_RANGE = 40;
    private static final int STEP_TIMEOUT = 50;
    private static final int REACTION_EXPIRY = 10;
    private static final int REPATH_TICKS = 8;

    private enum State { IDLE, TELL, ACTION }

    private enum Reaction { DODGE_SIDE, DODGE_BACK, COUNTER }

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
    private int punchCooldown;
    private int stuck;
    private int punishLeft;
    private boolean jumpPending;
    private final Deque<Long> pressure = new ArrayDeque<>();
    private long seenCast = -1;
    private boolean targetWasSwinging;
    private Reaction reaction;
    private long reactAt;
    private final Deque<String> recent = new ArrayDeque<>();
    private String thinking = "";

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
        boss.getNavigation().stop();
        boss.getMoveControl().strafe(0, 0);
    }

    // Drops whatever it was doing (new phase, new target).
    public void reset() {
        state = State.IDLE;
        plan.clear();
        comboName = null;
        reaction = null;
        pressure.clear();
        decisionWait = 0;
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
        BossSpec spec = boss.spec();
        BossSpec.Fase fase = boss.fase();
        PowerData data = boss.powerData();
        double distance = boss.distanceTo(target);
        countDown();
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
                    boss.getNavigation().stop();
                    boss.getMoveControl().strafe(0, 0);
                }
                return;
            }
            state = State.IDLE;
            decisionWait = Math.max(decisionWait, fase.pausaTicks());
        }
        if (busy) {
            keepAiming(null);
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
            decisionWait = Math.max(1, spec.percepcao().intervaloDecisaoTicks());
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
        if (punishLeft > 0) {
            punishLeft--;
        }
        if (jumpPending && !boss.onGround()) {
            jumpPending = false;
            Movement.doubleJump(boss);
        }
    }

    // ---------------------------------------------------------------- reading the target

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
        if (pressure.size() >= reacoes.golpesParaContra() && reaction == null && state != State.ACTION) {
            schedule(Reaction.COUNTER, reacoes.chanceContraAtaque(), fase);
            pressure.clear();
        }
        if (now % 2 == 0 && projectileIncoming()) {
            schedule(Reaction.DODGE_SIDE, reacoes.chanceEsquivarProjetil(), fase);
        }
    }

    private void onTargetCast(int slot, double distance, BossSpec.Reacoes reacoes, BossSpec.Fase fase) {
        if (slot == PowerData.SLOT_ULT) {
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
        if (random.nextDouble() >= chance * fase.chanceReacao()) {
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
            if (counter != null && ready(spec, counter) && distance <= spec.skills().get(counter).distanciaMax()) {
                plan.clear();
                state = State.IDLE;
                return begin(spec, counter);
            }
            r = Reaction.DODGE_BACK;
        }
        if (state == State.TELL && r != Reaction.COUNTER) {
            state = State.IDLE;
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
        // Waiting is an option too: less aggressive phases breathe more between moves.
        options.add(new Option("esperar", (1 - fase.agressividade()) * 0.6, false));
        options.sort(Comparator.comparingDouble(Option::score).reversed());
        thinking = summary(options);
        Option pick = pick(options, spec.percepcao().aleatoriedade());
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
                if (score > 0 && !recent.contains(name)) {
                    options.add(new Option(name, score, true));
                }
            }
        }
        return options;
    }

    private double openerScore(BossSpec spec, BossSpec.Fase fase, String step, double distance) {
        return switch (step) {
            case "soco" -> distance <= spec.distancias().soco() + 1 ? 1 : 0.5 * fase.agressividade();
            case "investida", "aproximar" -> distance > spec.investida().distanciaMin() && dashCooldown == 0 ? 0.9 : 0.3;
            case "esquiva", "pulo" -> 0.2;
            default -> spec.skills().containsKey(step) ? score(spec, fase, step, distance) : 0;
        };
    }

    // How good a move is right now. 0 = can't or shouldn't.
    private double score(BossSpec spec, BossSpec.Fase fase, String name, double distance) {
        BossSpec.Skill skill = spec.skills().get(name);
        if (skill == null || !ready(spec, name)) {
            return 0;
        }
        String tipo = skill.tipo();
        boolean melee = "golpe".equals(tipo) || "barragem".equals(tipo);
        if (!"contra".equals(tipo) && (distance < skill.distanciaMin() || distance > skill.distanciaMax() || !boss.hasLineOfSight(target))) {
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

    private boolean ready(BossSpec spec, String name) {
        BossSpec.Skill skill = spec.skills().get(name);
        return skill != null && boss.powerData().cooldowns[skill.slot()] <= 0;
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
        if (skill == null || !ready(spec, name)) {
            return false;
        }
        move = name;
        tellTotal = Math.max(0, (int) Math.round(skill.avisoTicks() * boss.fase().multAviso()));
        tellLeft = tellTotal;
        boss.getNavigation().stop();
        boss.getMoveControl().strafe(0, 0);
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
        if (!PowerLogic.cast(boss, skill.slot())) {
            state = State.IDLE;
            return false;
        }
        boss.powerData().cooldowns[skill.slot()] = (int) Math.round(skill.recargaTicks() * boss.fase().multRecarga());
        if ("golpe".equals(skill.tipo()) || "barragem".equals(skill.tipo())) {
            boss.swing(InteractionHand.MAIN_HAND);
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
        if (++stepAge > STEP_TIMEOUT) {
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
                if (skill == null || !ready(spec, step)) {
                    // Not available (or locked by a stun): skip the step, keep the combo going.
                    nextStep();
                    return false;
                }
                if (distance > skill.distanciaMax() || !boss.hasLineOfSight(target)) {
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

    private void dropCombo() {
        plan.clear();
        comboName = null;
        stepAge = 0;
    }

    // ---------------------------------------------------------------- moving around

    private void position(BossSpec spec, BossSpec.Fase fase, double distance) {
        if (--moodAge <= 0) {
            moodAge = Math.max(20, spec.percepcao().trocarHumorTicks());
            mood = random.nextDouble() < fase.agressividade() ? Mood.CLOSE : Mood.RANGE;
        }
        boolean close = mood == Mood.CLOSE || punishLeft > 0 || targetStunned();
        if (Susanoo.isReady(target)) {
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
        if (close) {
            if (distance > spec.investida().distanciaMin() && sees && dash(spec)) {
                return;
            }
            if (distance > spec.distancias().soco()) {
                approach(spec, distance, true);
            } else {
                boss.getNavigation().stop();
                boss.getMoveControl().strafe(0, 0);
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
        boss.getMoveControl().strafe(forward, strafeSide * 0.6f);
        boss.lookAt(target, 30, 30);
        boss.setYHeadRot(boss.getYRot());
        if (distance <= spec.distancias().soco()) {
            punch();
        }
    }

    private void approach(BossSpec spec, double distance, boolean run) {
        boss.getLookControl().setLookAt(target, 30, 30);
        if (--repath <= 0 || boss.getNavigation().isDone()) {
            repath = REPATH_TICKS;
            BossSpec.Movimento movimento = spec.movimento();
            boss.getNavigation().moveTo(target, run ? movimento.velocidadeCorrer() : movimento.velocidadeAndar());
        }
    }

    private boolean punch() {
        if (punchCooldown > 0 || boss.powerData().punchDelay > 0) {
            return false;
        }
        face(Combat.center(target), 360);
        boss.swing(InteractionHand.MAIN_HAND);
        Melee.normalHit(boss, target);
        punchCooldown = Math.max(1, boss.spec().distancias().intervaloSocoTicks());
        return true;
    }

    // A forward dash to close the gap fast.
    private boolean dash(BossSpec spec) {
        BossSpec.Investida investida = spec.investida();
        if (dashCooldown > 0 || !boss.onGround() || !Movement.free(boss)) {
            return false;
        }
        face(Combat.center(target), 360);
        boss.getNavigation().stop();
        Vec3 dir = Combat.flatLook(boss);
        boss.setDeltaMovement(dir.x * investida.forca(), investida.altura(), dir.z * investida.forca());
        boss.hasImpulse = true;
        boss.hurtMarked = true;
        dashCooldown = investida.recargaTicks();
        BossFx.dash(level(), boss, BossFx.color(boss.spec().intro().cor()));
        thinking = "investida";
        return true;
    }

    // Jump now, air jump as soon as it leaves the ground (countDown does it).
    private boolean jump() {
        if (!boss.onGround() || !Movement.free(boss) || boss.getData(ModAttachments.MOVEMENT).airJumpUsed) {
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
        boolean stalled = !sees || (distance > spec.distancias().ideal() + 4 && boss.getNavigation().isDone());
        stuck = stalled ? stuck + 1 : 0;
        if (!teleporte.ativo() || stuck < teleporte.presoTicks()) {
            return false;
        }
        stuck = 0;
        Vec3 behind = target.position().subtract(Combat.flatLook(target).scale(teleporte.distancia()));
        Vec3 from = boss.position();
        Vec3 front = target.position().add(Combat.flatLook(target).scale(teleporte.distancia()));
        for (Vec3 spot : new Vec3[] {behind, front}) {
            if (boss.level().noCollision(boss, boss.getBoundingBox().move(spot.subtract(from)))) {
                boss.teleportTo(spot.x, spot.y, spot.z);
                boss.getNavigation().stop();
                face(Combat.center(target), 360);
                BossFx.teleport(level(), from, spot);
                thinking = "teleporte";
                return true;
            }
        }
        return false;
    }

    private double horizontal(double distance) {
        return Math.sqrt(Math.max(0, distance * distance - Math.pow(target.getY() - boss.getY(), 2)));
    }

    private ServerLevel level() {
        return Combat.level(boss);
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
        Component line = Component.literal(String.format("§cFase %d §7| §f%s §7| dist %.1f | pressao %d%s §7| %s",
                boss.phase() + 1, what, distance, pressure.size(), punishLeft > 0 ? " §6PUNIR" : "", thinking));
        for (ServerPlayer player : level().players()) {
            if (player.distanceTo(boss) <= DEBUG_RANGE) {
                player.displayClientMessage(line, true);
            }
        }
    }
}
