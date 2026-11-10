package com.starkinhon.soulsjjk.power.powers.gojo;

import com.starkinhon.soulsjjk.compat.Att;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.function.Function;

import com.starkinhon.soulsjjk.ModAttachments;
import com.starkinhon.soulsjjk.combat.BlockRules;
import com.starkinhon.soulsjjk.combat.Combat;
import com.starkinhon.soulsjjk.combat.Fx;
import com.starkinhon.soulsjjk.combat.SkillSounds;
import com.starkinhon.soulsjjk.combat.Tasks;
import com.starkinhon.soulsjjk.network.ModPayloads;
import com.starkinhon.soulsjjk.power.BossOrder;
import com.starkinhon.soulsjjk.power.PowerData;
import com.starkinhon.soulsjjk.skill.AzulSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.starkinhon.soulsjjk.compat.PacketDistributor;

// Blue (Azul), Gojo's Z (gojo.azul.json), the same as the Skill Visualizer's "Z · Azul" scenes.
// The orb shows in his hand at once. Let go within segurado.decidirTicks it is the tap: the small orb flies to where he
// aims, locks there and pulls whoever is near slowly for clique.duracaoTicks. Held longer it charges between his hands,
// growing up to orbe.escalaMax times; let go (or held too long) it follows his aim for segurado.duracaoTicks, pulls much
// harder (whoever reaches it is held spinning inside), tears blocks out of the ground and crushes them in the middle,
// then implodes. The orb is drawn on the clients (client/fx/AzulRenderer) from what is sent here every tick.
// While the held one is out, Z again (PowerLogic sends it here, see press): a tap parks it where it is, or sends a parked
// one back to following; held while it follows, it is shot towards the aim, chases whoever is there and explodes; held
// while parked, it circles him (orbita) pulling everything, on its own until it ends. The mouse wheel moves it away or closer
// (scroll, sent by the client while it is told the orb is under control).
public final class Azul {
    public static final String SKILL = AzulSpec.SKILL;
    // The held one's body animation (both hands on the orb while it charges).
    public static final String HOLD_ANIMATION = "gojo.azul_maximo";
    private static final double VIEW_DISTANCE = 160;
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static int nextId;

    public static final int ALIVE = 0;
    public static final int IMPLODED = 1;
    public static final int GONE = 2;

    private enum Phase { DECIDE, FLY, LOCKED, CHARGE, FOLLOW, PARKED, ORBIT, SHOT }

    // The held one under a player's control: Z presses and wheel turns land here, the task reads them.
    private static final class Control {
        boolean pressed;
        int holdStart = -1;
        double reach;
        int scroll;
        // The game time the Blue's task last ran: a control its task no longer runs (the task dropped from outside)
        // must not keep swallowing Z for good.
        long seen;
    }

    private static final Map<UUID, Control> CONTROLS = new HashMap<>();

    // Every held Blue out right now, as the Unlimited Purple (Roxo.irrestrito) sees it: a Red fired at full charge into
    // his own Blue parked at full charge. Updated by the Blue's task every tick; consumed, the Blue goes out at once.
    public static final class Live {
        final LivingEntity owner;
        final ServerLevel level;
        Vec3 pos = Vec3.ZERO;
        double radius;
        double charge;
        boolean parked;
        long seen;
        boolean claimed;
        boolean consumed;

        Live(LivingEntity owner, ServerLevel level) {
            this.owner = owner;
            this.level = level;
            this.seen = level.getGameTime();
        }

        public Vec3 pos() {
            return pos;
        }

        public double radius() {
            return radius;
        }

        // Still out (its task ran this tick or the one before) and not taken yet.
        public boolean alive() {
            return !consumed && level.getGameTime() - seen <= 2;
        }

        public void consume() {
            consumed = true;
        }
    }

    private static final List<Live> LIVE = new ArrayList<>();

    // Every Blue of owner's still out, and any other within radius of center, fades now without imploding (its task
    // sees it on its next tick, lets go of what it tore up and frees the Z). The Duelo de Expansões starting: nothing
    // keeps pulling through its scenes (Stark, 2026-11-09: a Blue kept pulling him while the duel opened).
    public static void dispel(LivingEntity owner, Vec3 center, double radius) {
        for (Live live : LIVE) {
            if (live.owner == owner || live.level == owner.level() && live.pos.distanceTo(center) <= radius + live.radius) {
                live.consumed = true;
            }
        }
    }

    // owner's parked Blue charged to at least minCharge that the line from `from` along dir (length long, width wide)
    // runs into: the nearest one, claimed so no other Red takes it too. null if there is none.
    public static Live claimParked(LivingEntity owner, Vec3 from, Vec3 dir, double length, double width, double minCharge) {
        LIVE.removeIf(l -> l.consumed || l.level.getGameTime() - l.seen > 40);
        Live best = null;
        double bestT = Double.MAX_VALUE;
        for (Live l : LIVE) {
            if (l.owner != owner || l.level != owner.level() || l.claimed || !l.parked || l.charge < minCharge || !l.alive()) {
                continue;
            }
            Vec3 rel = l.pos.subtract(from);
            double t = rel.dot(dir);
            if (t < -l.radius || t > length + l.radius) {
                continue;
            }
            if (rel.subtract(dir.scale(t)).length() > l.radius + width) {
                continue;
            }
            if (t < bestT) {
                bestT = t;
                best = l;
            }
        }
        if (best != null) {
            best.claimed = true;
        }
        return best;
    }

    // owner's Blue parked right now with at least minCharge (not taken by a Red yet), or null. The Gojo boss waits for
    // it before firing the Red of the Unlimited Purple.
    public static Live parkedBlue(LivingEntity owner, double minCharge) {
        for (Live l : LIVE) {
            if (l.owner == owner && l.level == owner.level() && l.parked && !l.claimed && l.charge >= minCharge && l.alive()) {
                return l;
            }
        }
        return null;
    }

    // One of owner's Blues is still out (flying, parked, following, circling or shot). The Gojo boss doesn't throw a
    // second one on top of it.
    public static boolean out(LivingEntity owner) {
        for (Live l : LIVE) {
            if (l.owner == owner && l.level == owner.level() && l.alive()) {
                return true;
            }
        }
        return false;
    }

    // How far a boss's tap reaches: past this the orb would lock in the empty air between him and the target
    // (tapTarget stops it at clique.distancia), so a boss throws a charged one instead.
    public static double tapReach(boolean awakened) {
        return AzulSpec.get(awakened).clique().distancia() * 2;
    }

    private Azul() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        return use(caster, level, false);
    }

    // Z pressed while his held Blue is out: it is a command to it, not a new Blue. Returns false when there is none.
    public static boolean press(ServerPlayer player) {
        Control control = CONTROLS.get(player.getUUID());
        if (control == null) {
            return false;
        }
        if (player.level().getGameTime() - control.seen > 40) {
            // Its task has not run for 2 s (a hold in the air is shorter): the Blue is gone without ending the control,
            // and Z would stay swallowed for good, whatever power he has now (Stark 2026-11-09).
            CONTROLS.remove(player.getUUID(), control);
            return false;
        }
        control.pressed = true;
        return true;
    }

    // The mouse wheel while his Blue follows the aim: up (+) sends it away, down (-) brings it closer.
    public static void scroll(ServerPlayer player, int steps) {
        Control control = CONTROLS.get(player.getUUID());
        if (control != null && steps != 0) {
            control.scroll += Mth.clamp(steps, -5, 5);
        }
    }

    // Shift + Z: the very same Blue, drawn with the mix look (the glass ball) instead of the realism one.
    public static boolean useMix(LivingEntity caster, ServerLevel level) {
        return use(caster, level, true);
    }

    private static boolean use(LivingEntity caster, ServerLevel level, boolean shift) {
        PowerData data = Att.get(caster, ModAttachments.POWER);
        boolean awakened = data.awakened;
        AzulSpec spec = AzulSpec.get(awakened);
        AzulSpec.Orbe orbe = spec.orbe();
        AzulSpec.Clique clique = spec.clique();
        AzulSpec.Segurado segurado = spec.segurado();
        AzulSpec.Visual visual = spec.visual();
        int style = visual == null ? (shift ? 3 : 2) : shift ? (visual.estiloShift() != null ? visual.estiloShift() : 3) : visual.estilo();
        int id = nextId++;
        String power = data.power;
        int interrupts = data.interrupts;
        int decide = Math.max(1, segurado.decidirTicks());
        int chargeTicks = Math.max(1, segurado.carregarTicks());
        int maxHold = Math.max(chargeTicks, segurado.maxSegurarTicks());
        double baseRadius = Math.max(0.05, orbe.raio());
        double scaleMax = Math.max(1, orbe.escalaMax());
        // Bosses don't hold keys: half the time a tap, else a charge of their own length.
        // Bosses (the Gojo boss) use every form: a third of the time a tap, else a charge; see BossPlan for what the
        // charged one does after.
        // A boss with tactics (the Gojo boss) says how this time: tap or charge, how much, and the plan (BossOrder).
        BossOrder.Order order = BossOrder.take(caster);
        // Without an order (chaos, gamerule soulbotscaoticos) a target out of the tap's reach still gets a charged one:
        // a tap would only lock halfway, in the empty air (Stark, 2026-11-09: "o Azul aleatório").
        boolean farTarget = caster instanceof Mob far && far.getTarget() != null
                && far.getTarget().distanceTo(caster) > clique.distancia() * 2;
        boolean bossHolds = order != null ? order.hold() : farTarget || RANDOM.nextDouble() < 0.67;
        int bossCharge = order != null ? Math.max(4, (int) Math.round(chargeTicks * Mth.clamp(order.charge(), 0, 1)))
                : Math.max(4, chargeTicks / 3 + RANDOM.nextInt(Math.max(1, chargeTicks)));
        BossPlan orderedPlan = order != null ? BossPlan.named(order.plan()) : null;
        List<ServerPlayer> screen = caster instanceof ServerPlayer player ? List.of(player) : List.<ServerPlayer>of();

        // What changes from tick to tick.
        Phase[] phase = {Phase.DECIDE};
        Vec3[] orb = {holdAt(caster, 0, baseRadius)};
        double[] radius = {baseRadius};
        double[] charge = {0};
        int[] phaseStart = {0};
        Vec3[] flyFrom = {orb[0]};
        Vec3[] flyTo = {orb[0]};
        int[] flyTicks = {1};
        boolean[] following = {true};
        int[] lifeStart = {0};
        Vec3[] velocity = {Vec3.ZERO};
        double[] orbitAngle = {0};
        LivingEntity[] chased = {null};
        Vec3[] shotDir = {Vec3.ZERO};
        double[] shotSpeed = {0};
        double[] shotFlown = {0};
        Control control = new Control();
        Live live = new Live(caster, level);
        LIVE.add(live);
        AzulSpec.Controle controle = spec.controle();
        AzulSpec.Orbita orbita = spec.orbita();
        AzulSpec.Tiro tiro = spec.tiro();
        List<FallingBlockEntity> torn = new ArrayList<>();
        int[] tornCount = {0};
        int[] lastCrushFx = {-10};
        int[] lastEatFx = {-10};
        // A boss's charged Blue: what it does once out (BossPlan) and when.
        BossPlan[] bossPlan = {null};
        int[] bossAt = {0};
        int[] eaten = {0};

        Function<String, Vec3> where = onde -> "usuario".equals(onde) ? caster.position().add(0, 1, 0) : orb[0];
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
        SkillSounds.play(level, spec.sons(), "uso", 0, 0, where, screen);
        Fx.effects(level, SKILL, awakened, "uso", orb[0], caster.getLookAngle());
        data.busyTicks = decide + 2;

        Tasks.add(age -> {
            control.seen = level.getGameTime();
            PowerData now = Att.get(caster, ModAttachments.POWER);
            boolean casterGone = !caster.isAlive() || caster.isRemoved() || !power.equals(now.power);
            boolean cut = now.interrupts != interrupts;
            if (live.consumed) {
                // Swallowed by the Unlimited Purple: the Red hit it, both implode (drawn by RoxoRenderer).
                send(level, id, GONE, orb[0], radius[0], (float) charge[0], style);
                release(torn);
                endControl(caster, control);
                LIVE.remove(live);
                return true;
            }
            if (casterGone || cut && (phase[0] == Phase.DECIDE || phase[0] == Phase.CHARGE)) {
                // Cut short: it fades without imploding.
                send(level, id, GONE, orb[0], radius[0], (float) charge[0], style);
                release(torn);
                endControl(caster, control);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                return true;
            }
            if (cut) {
                // Stunned while it is out: it stays where it is.
                following[0] = false;
            }
            boolean held = caster instanceof Player ? com.starkinhon.soulsjjk.combat.Stamina.holding(caster) : bossHolds && age < decide + bossCharge;
            int t = age - phaseStart[0];
            switch (phase[0]) {
                case DECIDE -> {
                    orb[0] = holdAt(caster, 0, baseRadius);
                    now.busyTicks = Math.max(now.busyTicks, 2);
                    if (age + 1 >= decide) {
                        phaseStart[0] = age + 1;
                        if (held) {
                            phase[0] = Phase.CHARGE;
                            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster,
                                    new ModPayloads.PlaySkillAnimation(caster.getId(), HOLD_ANIMATION, awakened));
                            SkillSounds.play(level, spec.sons(), "carregar", 0, chargeTicks, where, screen);
                            Fx.effects(level, SKILL, awakened, "carregar", orb[0], caster.getLookAngle());
                        } else {
                            phase[0] = Phase.FLY;
                            flyFrom[0] = orb[0];
                            flyTo[0] = tapTarget(caster, clique.distancia(), baseRadius);
                            flyTicks[0] = Math.max(1, (int) Math.ceil(flyFrom[0].distanceTo(flyTo[0]) / Math.max(0.1, clique.velocidade())));
                            caster.swing(InteractionHand.MAIN_HAND, true);
                            SkillSounds.play(level, spec.sons(), "lancar", 0, 0, where, screen);
                            Fx.effects(level, SKILL, awakened, "lancar", orb[0], flyTo[0].subtract(flyFrom[0]).normalize());
                        }
                    }
                }
                case FLY -> {
                    double k = Math.min(1, (t + 1) / (double) flyTicks[0]);
                    orb[0] = flyFrom[0].lerp(flyTo[0], k);
                    if (k >= 1) {
                        phase[0] = Phase.LOCKED;
                        phaseStart[0] = age + 1;
                        int left = Math.max(1, clique.duracaoTicks() - flyTicks[0]);
                        SkillSounds.play(level, spec.sons(), "travar", 0, left, where, screen);
                        Fx.effects(level, SKILL, awakened, "travar", orb[0], Vec3.ZERO);
                    }
                }
                case LOCKED -> {
                    pullTap(level, caster, orb[0], baseRadius, clique, age);
                    if (flyTicks[0] + t + 1 >= clique.duracaoTicks()) {
                        send(level, id, GONE, orb[0], radius[0], 0, style);
                        SkillSounds.play(level, spec.sons(), "fim", 0, 0, where, screen);
                        Fx.effects(level, SKILL, awakened, "fim", orb[0], Vec3.ZERO);
                        return true;
                    }
                }
                case CHARGE -> {
                    charge[0] = Math.min(1, (t + 1) / (double) chargeTicks);
                    if (held) {
                        com.starkinhon.soulsjjk.combat.Charge.report(caster, charge[0]);
                    }
                    radius[0] = baseRadius * (1 + (scaleMax - 1) * smooth(charge[0]));
                    orb[0] = holdAt(caster, smooth(charge[0]), radius[0]);
                    now.busyTicks = Math.max(now.busyTicks, 2);
                    // Players let it go only when they let go of Z, however long that is; bosses after their charge.
                    if (!held || !(caster instanceof Player) && t + 1 >= maxHold) {
                        phase[0] = Phase.FOLLOW;
                        phaseStart[0] = age + 1;
                        lifeStart[0] = age + 1;
                        control.reach = segurado.miraMax() != null ? Mth.lerp(charge[0], segurado.mira(), segurado.miraMax()) : segurado.mira();
                        if (caster instanceof ServerPlayer player) {
                            CONTROLS.put(player.getUUID(), control);
                        } else {
                            bossPlan[0] = orderedPlan != null ? orderedPlan : BossPlan.pick();
                            bossAt[0] = age + 1 + bossPlan[0].delay();
                        }
                        caster.swing(InteractionHand.MAIN_HAND, true);
                        // The push of the hand that sends it out.
                        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
                        SkillSounds.play(level, spec.sons(), "soltar", 0, Math.max(1, segurado.duracaoTicks()), where, screen);
                        Fx.effects(level, SKILL, awakened, "soltar", orb[0], caster.getLookAngle());
                    }
                }
                case FOLLOW, PARKED, ORBIT, SHOT -> {
                    double c = charge[0];
                    if (cut && phase[0] == Phase.FOLLOW) {
                        // Stunned while it follows: it stays where it is.
                        phase[0] = Phase.PARKED;
                    }
                    // Z again: a tap parks it or sends it back to following; held, it is shot (following) or circles
                    // him (parked), on its own until it ends.
                    if (bossPlan[0] != null && age >= bossAt[0] && !cut
                            && !parkTooSoon(caster, bossPlan[0], phase[0], orb[0], radius[0], age - lifeStart[0])) {
                        // A boss "presses Z" by itself: the plan picked when it let the Blue go.
                        BossPlan doing = bossPlan[0];
                        bossPlan[0] = doing.next();
                        bossAt[0] = age + (bossPlan[0] != null ? bossPlan[0].delay() : 0);
                        if (doing == BossPlan.PARK_THEN_UNPARK || doing == BossPlan.PARK_THEN_ORBIT || doing == BossPlan.IRRESTRITO) {
                            doing = BossPlan.PARK;
                        }
                        if (doing == BossPlan.SHOOT && phase[0] == Phase.FOLLOW) {
                            caster.swing(InteractionHand.MAIN_HAND, true);
                            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
                            phase[0] = Phase.SHOT;
                            phaseStart[0] = age;
                            chased[0] = caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive() ? mob.getTarget() : null;
                            Vec3 aim = chased[0] != null ? Combat.center(chased[0]) : followTarget(caster, control.reach);
                            Vec3 to = aim.subtract(orb[0]);
                            shotDir[0] = to.lengthSqr() > 1.0E-4 ? to.normalize() : caster.getLookAngle();
                            shotSpeed[0] = tiro.velocidade();
                            shotFlown[0] = 0;
                            SkillSounds.play(level, spec.sons(), "atirar", 0, 0, where, screen);
                            Fx.effects(level, SKILL, awakened, "atirar", orb[0], shotDir[0]);
                        } else if (doing == BossPlan.PARK && phase[0] == Phase.FOLLOW) {
                            phase[0] = Phase.PARKED;
                            velocity[0] = Vec3.ZERO;
                            SkillSounds.play(level, spec.sons(), "travarControle", 0, 0, where, screen);
                            Fx.effects(level, SKILL, awakened, "travarControle", orb[0], Vec3.ZERO);
                        } else if (doing == BossPlan.UNPARK && phase[0] == Phase.PARKED) {
                            phase[0] = Phase.FOLLOW;
                            SkillSounds.play(level, spec.sons(), "seguirDeNovo", 0, 0, where, screen);
                            Fx.effects(level, SKILL, awakened, "seguirDeNovo", orb[0], Vec3.ZERO);
                        } else if (doing == BossPlan.ORBIT && phase[0] == Phase.PARKED) {
                            caster.swing(InteractionHand.MAIN_HAND, true);
                            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
                            phase[0] = Phase.ORBIT;
                            Vec3 from = orb[0].subtract(caster.position());
                            orbitAngle[0] = Math.atan2(from.z, from.x);
                            SkillSounds.play(level, spec.sons(), "orbitar", 0, 0, where, screen);
                            Fx.effects(level, SKILL, awakened, "orbitar", caster.position().add(0, 0.05, 0), Vec3.ZERO);
                        }
                    }
                    if (control.pressed && phase[0] == Phase.ORBIT) {
                        // Circling, it goes on by itself until it ends: Z does nothing to it any more.
                        control.pressed = false;
                    }
                    if (control.pressed) {
                        control.pressed = false;
                        // A press while a hold is already being timed (the key repeating) doesn't restart it.
                        if (control.holdStart < 0) {
                            control.holdStart = age;
                        }
                    }
                    if (control.holdStart >= 0 && phase[0] != Phase.SHOT) {
                        boolean down = now.abilityHeld;
                        if (!down) {
                            // Letting go while it circles changes nothing: it keeps circling until it ends.
                            if (phase[0] != Phase.ORBIT && age - control.holdStart < decide) {
                                boolean park = phase[0] == Phase.FOLLOW;
                                phase[0] = park ? Phase.PARKED : Phase.FOLLOW;
                                velocity[0] = Vec3.ZERO;
                                String momento = park ? "travarControle" : "seguirDeNovo";
                                SkillSounds.play(level, spec.sons(), momento, 0, 0, where, screen);
                                Fx.effects(level, SKILL, awakened, momento, orb[0], Vec3.ZERO);
                            }
                            control.holdStart = -1;
                        } else if (age - control.holdStart >= decide && phase[0] != Phase.ORBIT) {
                            caster.swing(InteractionHand.MAIN_HAND, true);
                            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL, awakened));
                            if (phase[0] == Phase.FOLLOW) {
                                phase[0] = Phase.SHOT;
                                phaseStart[0] = age;
                                chased[0] = Combat.raycastTarget(caster, tiro.alcance());
                                Vec3 aim = chased[0] != null ? Combat.center(chased[0]) : Combat.raycastPoint(caster, tiro.alcance());
                                Vec3 to = aim.subtract(orb[0]);
                                shotDir[0] = to.lengthSqr() > 1.0E-4 ? to.normalize() : caster.getLookAngle();
                                shotSpeed[0] = tiro.velocidade();
                                shotFlown[0] = 0;
                                control.holdStart = -1;
                                endControl(caster, control);
                                SkillSounds.play(level, spec.sons(), "atirar", 0, 0, where, screen);
                                Fx.effects(level, SKILL, awakened, "atirar", orb[0], shotDir[0]);
                            } else {
                                phase[0] = Phase.ORBIT;
                                Vec3 from = orb[0].subtract(caster.position());
                                orbitAngle[0] = Math.atan2(from.z, from.x);
                                SkillSounds.play(level, spec.sons(), "orbitar", 0, 0, where, screen);
                                Fx.effects(level, SKILL, awakened, "orbitar", caster.position().add(0, 0.05, 0), Vec3.ZERO);
                            }
                        }
                    }
                    // The wheel: further away or closer while it follows the aim.
                    if (control.scroll != 0) {
                        int steps = control.scroll;
                        control.scroll = 0;
                        if (phase[0] == Phase.FOLLOW) {
                            double before = control.reach;
                            control.reach = Mth.clamp(control.reach + steps * controle.passoScroll(), controle.alcanceMin(), controle.alcanceMax());
                            if (control.reach != before) {
                                String momento = steps > 0 ? "afastar" : "aproximar";
                                SkillSounds.play(level, spec.sons(), momento, 0, 0, where, screen);
                                Fx.effects(level, SKILL, awakened, momento, orb[0], caster.getLookAngle().scale(steps > 0 ? 1 : -1));
                            }
                        }
                    }
                    if (phase[0] == Phase.SHOT) {
                        // Shot: it speeds up towards the aim, turning to chase whoever it was aimed at, and explodes.
                        if (chased[0] != null && chased[0].isAlive()) {
                            Vec3 to = Combat.center(chased[0]).subtract(orb[0]);
                            if (to.lengthSqr() > 1.0E-4) {
                                shotDir[0] = shotDir[0].lerp(to.normalize(), Mth.clamp(tiro.perseguir(), 0, 1)).normalize();
                            }
                        }
                        shotSpeed[0] = Math.min(tiro.velocidadeMax(), shotSpeed[0] + tiro.aceleracao());
                        Vec3 next = orb[0].add(shotDir[0].scale(shotSpeed[0]));
                        var hit = level.clip(new net.minecraft.world.level.ClipContext(orb[0], next, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
                        boolean wall = hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
                        orb[0] = wall ? hit.getLocation() : next;
                        shotFlown[0] += shotSpeed[0];
                        velocity[0] = shotDir[0].scale(shotSpeed[0]);
                        boolean reached = !Combat.inRadius(level, orb[0], radius[0] + 0.8, caster).isEmpty();
                        pullHeld(level, caster, orb[0], radius[0], baseRadius * scaleMax, c, segurado, age);
                        steerBlocks(orb[0], radius[0], torn);
                        if (wall || reached || shotFlown[0] >= tiro.alcance() || age - phaseStart[0] >= Math.max(1, tiro.maxTicks())) {
                            explode(caster, level, orb[0], radius[0], c, tiro, torn);
                            send(level, id, IMPLODED, orb[0], radius[0], (float) c, style);
                            SkillSounds.play(level, spec.sons(), "explodir", 0, 0, where, screen);
                            Fx.effects(level, SKILL, awakened, "explodir", orb[0], Vec3.ZERO);
                            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), "", false));
                            return true;
                        }
                        break;
                    }
                    // Following the aim (or circling him): pulled towards where it should be, keeping part of its speed,
                    // so it lags a little behind and moves softly. Parked, it stays.
                    Vec3 goal = null;
                    double pullK = controle.seguir();
                    if (phase[0] == Phase.FOLLOW) {
                        goal = followTarget(caster, control.reach);
                    } else if (phase[0] == Phase.ORBIT) {
                        orbitAngle[0] += orbita.velocidade();
                        goal = caster.position().add(Math.cos(orbitAngle[0]) * orbita.raio(), orbita.altura(), Math.sin(orbitAngle[0]) * orbita.raio());
                        pullK *= 2;
                    }
                    if (goal != null) {
                        Vec3 v = velocity[0].scale(Mth.clamp(controle.amortecer(), 0, 0.99)).add(goal.subtract(orb[0]).scale(pullK));
                        double max = controle.velocidadeMax() * (phase[0] == Phase.ORBIT ? 1.6 : 1);
                        if (v.length() > max) {
                            v = v.normalize().scale(max);
                        }
                        velocity[0] = v;
                        orb[0] = orb[0].add(v);
                    } else {
                        velocity[0] = Vec3.ZERO;
                    }
                    if (caster instanceof ServerPlayer player && phase[0] != Phase.SHOT) {
                        // Keeps the client's wheel on the orb (the hotbar doesn't scroll) while it is under control.
                        PacketDistributor.sendToPlayer(player, new ModPayloads.AzulControl(true));
                    }
                    pullHeld(level, caster, orb[0], radius[0], baseRadius * scaleMax, c, segurado, age);
                    tearBlocks(level, orb[0], radius[0], c, segurado, torn, tornCount);
                    if (eatGround(level, orb[0], radius[0], c, spec.singularidade(), eaten) && age - lastEatFx[0] >= 4) {
                        lastEatFx[0] = age;
                        SkillSounds.play(level, spec.sons(), "singularidade", 0, 0, where, screen);
                        Fx.effects(level, SKILL, awakened, "singularidade", orb[0], Vec3.ZERO);
                    }
                    int crushed = steerBlocks(orb[0], radius[0], torn);
                    if (crushed > 0 && age - lastCrushFx[0] >= 3) {
                        lastCrushFx[0] = age;
                        SkillSounds.play(level, spec.sons(), "esmagar", 0, 0, where, screen);
                        Fx.effects(level, SKILL, awakened, "esmagar", orb[0], Vec3.ZERO);
                    }
                    if (age - lifeStart[0] + 1 >= Math.max(1, segurado.duracaoTicks())) {
                        // It implodes: everyone in it is hit once more, the blocks still flying fall.
                        float last = (float) (segurado.danoFim() * (0.4 + 0.6 * c));
                        for (LivingEntity hit : Combat.inRadius(level, orb[0], radius[0] * 1.6 + 1, caster)) {
                            Combat.hurtMagic(caster, hit, last);
                        }
                        release(torn);
                        endControl(caster, control);
                        send(level, id, IMPLODED, orb[0], radius[0], (float) c, style);
                        SkillSounds.play(level, spec.sons(), "fimForte", 0, 0, where, screen);
                        Fx.effects(level, SKILL, awakened, "fimForte", orb[0], Vec3.ZERO);
                        return true;
                    }
                }
            }
            live.pos = orb[0];
            live.radius = radius[0];
            live.charge = charge[0];
            live.parked = phase[0] == Phase.PARKED;
            live.seen = level.getGameTime();
            send(level, id, ALIVE, orb[0], radius[0], (float) charge[0], style);
            return false;
        });
        return true;
    }

    // What a boss's charged Blue does once out, like a player pressing Z: keeps chasing the target until it implodes (and
    // eats the ground under them when charged enough), is shot at them, parks there (sometimes chasing again after), or
    // parks and then circles the boss. Each step has its own wait (delay) after the one before; next() is the step after.
    // A boss parks its Blue on the target, not in the empty air on the way there (Stark, 2026-11-09: the Gojo boss left
    // it parked halfway, "o Azul aleatório"): while it follows it keeps going until it reaches them, for at most
    // PARK_WAIT ticks (a target it can't reach still gets it parked).
    private static final int PARK_WAIT = 70;

    private static boolean parkTooSoon(LivingEntity caster, BossPlan plan, Phase phase, Vec3 orb, double radius, int following) {
        boolean parks = plan == BossPlan.PARK || plan == BossPlan.PARK_THEN_UNPARK || plan == BossPlan.PARK_THEN_ORBIT
                || plan == BossPlan.IRRESTRITO;
        if (!parks || phase != Phase.FOLLOW || following >= PARK_WAIT || !(caster instanceof Mob mob)
                || mob.getTarget() == null || !mob.getTarget().isAlive()) {
            return false;
        }
        return orb.distanceTo(Combat.center(mob.getTarget())) > Math.max(3, radius * 1.5);
    }

    private enum BossPlan {
        // IRRESTRITO: chases the target a little, then parks on it and stays there, waiting for the Red (the Unlimited
        // Purple, ordered by the Gojo boss's tactics).
        CHASE, SHOOT, PARK, UNPARK, ORBIT, PARK_THEN_UNPARK, PARK_THEN_ORBIT, IRRESTRITO;

        // A plan by name (BossOrder.plan), or null.
        static BossPlan named(String name) {
            if (name == null) {
                return null;
            }
            for (BossPlan plan : values()) {
                if (plan.name().equalsIgnoreCase(name)) {
                    return plan;
                }
            }
            return null;
        }

        static BossPlan pick() {
            double r = RANDOM.nextDouble();
            return r < 0.2 ? CHASE : r < 0.5 ? SHOOT : r < 0.65 ? PARK : r < 0.8 ? PARK_THEN_UNPARK : PARK_THEN_ORBIT;
        }

        // Ticks to wait before doing this step.
        int delay() {
            return switch (this) {
                case CHASE -> Integer.MAX_VALUE / 2;
                case SHOOT -> 20 + RANDOM.nextInt(40);
                case PARK, PARK_THEN_UNPARK, PARK_THEN_ORBIT -> 15 + RANDOM.nextInt(30);
                case UNPARK -> 30 + RANDOM.nextInt(40);
                case ORBIT -> 8 + RANDOM.nextInt(20);
                case IRRESTRITO -> 16 + RANDOM.nextInt(10);
            };
        }

        // The step itself (the two-step plans start by parking) and the one after it.
        BossPlan next() {
            return switch (this) {
                case PARK_THEN_UNPARK -> UNPARK;
                case PARK_THEN_ORBIT -> ORBIT;
                default -> null;
            };
        }
    }

    // The Blue is no longer his to steer: Z and the wheel go back to normal.
    private static void endControl(LivingEntity caster, Control control) {
        if (caster instanceof ServerPlayer player) {
            CONTROLS.remove(player.getUUID(), control);
            PacketDistributor.sendToPlayer(player, new ModPayloads.AzulControl(false));
        }
    }

    // The shot Blue blows up: everyone near is hit and thrown out, blocks break (gamerule), the torn blocks fly out.
    private static void explode(LivingEntity caster, ServerLevel level, Vec3 at, double r, double c, AzulSpec.Tiro tiro, List<FallingBlockEntity> torn) {
        double reach = r * Math.max(0.5, tiro.raioExplosao()) + 1;
        float damage = (float) (tiro.dano() * (0.4 + 0.6 * c));
        for (LivingEntity hit : Combat.inRadius(level, at, reach, caster)) {
            Combat.hurtMagic(caster, hit, damage);
            Combat.pushFrom(hit, at, tiro.empurrao() * (0.5 + 0.5 * c), 0.5);
        }
        if (tiro.forcaExplosao() > 0 && BlockRules.canBreak(level)) {
            level.explode(caster, at.x, at.y, at.z, (float) (tiro.forcaExplosao() * (0.4 + 0.6 * c)), false,
                    net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        }
        for (FallingBlockEntity block : torn) {
            if (!block.isRemoved()) {
                Vec3 out = block.position().subtract(at);
                out = out.lengthSqr() > 1.0E-4 ? out.normalize() : new Vec3(0, 1, 0);
                block.setNoGravity(false);
                block.noPhysics = false;
                block.setDeltaMovement(out.scale(0.9).add(0, 0.4, 0));
            }
        }
        torn.clear();
    }

    // Between his hands, in front of the chest: higher and further out the bigger it is (the Visualizer's holdAt).
    private static Vec3 holdAt(LivingEntity caster, double c, double radius) {
        Vec3 forward = Combat.flatLook(caster);
        return caster.position().add(0, 1.45 + 0.4 * c, 0).add(forward.scale(0.9 + radius * 1.05));
    }

    // Where the tap's orb locks: where he aims, stopped short of a wall. Bosses put it on their target.
    private static Vec3 tapTarget(LivingEntity caster, double distance, double radius) {
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            Vec3 at = Combat.center(mob.getTarget());
            Vec3 eye = caster.getEyePosition();
            Vec3 to = at.subtract(eye);
            return to.length() > distance * 2 ? eye.add(to.normalize().scale(distance)) : at;
        }
        Vec3 eye = caster.getEyePosition();
        Vec3 hit = Combat.raycastPoint(caster, distance);
        double d = hit.distanceTo(eye);
        return d < distance - 0.01 ? eye.add(caster.getLookAngle().scale(Math.max(1, d - radius - 0.3))) : hit;
    }

    // Where the held one goes: segurado.mira blocks ahead of his eyes (the mouse steers it). Bosses aim at their target.
    private static Vec3 followTarget(LivingEntity caster, double reach) {
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            return Combat.center(mob.getTarget());
        }
        return caster.getEyePosition().add(caster.getLookAngle().scale(reach));
    }

    // The tap: whoever is near is pulled hard into it and stuck there.
    private static void pullTap(ServerLevel level, LivingEntity caster, Vec3 orb, double r, AzulSpec.Clique clique, int age) {
        double reach = Math.max(1, clique.raioPuxar());
        int every = Math.max(1, clique.danoACadaTicks());
        for (LivingEntity e : Combat.inRadius(level, orb, reach, caster)) {
            if (skip(e)) {
                continue;
            }
            Vec3 to = orb.subtract(Combat.center(e));
            double d = to.length();
            if (d > reach) {
                continue;
            }
            Vec3 dm = e.getDeltaMovement();
            if (d > r + 0.7) {
                com.starkinhon.soulsjjk.boss.BossFalas.azulPull(caster, e, d);
                // Pulled straight at it (off the ground too), fast enough that walking away doesn't save you.
                double speed = Math.min(clique.maxPuxar(), clique.puxar() + (clique.maxPuxar() - clique.puxar()) * (1 - d / reach));
                Vec3 want = to.normalize().scale(Math.min(speed, d * 0.5));
                e.setDeltaMovement(dm.lerp(want, 0.6));
                e.fallDistance = 0;
            } else {
                // Stuck to it: held in place at the orb.
                e.setDeltaMovement(to.scale(0.4));
                e.fallDistance = 0;
                if (age % every == 0 && clique.dano() > 0) {
                    Combat.hurtMagic(caster, e, clique.dano());
                }
            }
            e.hurtMarked = true;
        }
    }

    // The held one: pulled hard from far away (dropped items too), and held spinning inside it once there.
    private static void pullHeld(ServerLevel level, LivingEntity caster, Vec3 orb, double r, double rMax, double c, AzulSpec.Segurado s, int age) {
        double reach = Math.max(1, Mth.lerp(c, s.raioPuxar() * 0.6, s.raioPuxar()));
        double maxSpeed = Mth.lerp(c, s.maxPuxar() * 0.5, s.maxPuxar());
        int every = Math.max(1, s.danoACadaTicks());
        float damage = (float) (s.dano() * (0.4 + 0.6 * c));
        AABB box = new AABB(orb, orb).inflate(reach);
        for (Entity e : level.getEntities(caster, box, x -> x.isAlive() && !x.isSpectator() && !(x instanceof FallingBlockEntity)
                && (x instanceof ItemEntity || x instanceof LivingEntity living && Combat.isTarget(living, caster)))) {
            if (e instanceof LivingEntity living && skip(living)) {
                continue;
            }
            Vec3 to = orb.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d > reach) {
                continue;
            }
            Vec3 want;
            double blend;
            if (d > Math.max(r, rMax * 0.5 * c)) {
                if (e instanceof LivingEntity living) {
                    com.starkinhon.soulsjjk.boss.BossFalas.azulPull(caster, living, d);
                }
                double speed = Math.max(s.puxar(), maxSpeed * (0.4 + 0.6 * (1 - d / reach)));
                want = to.normalize().scale(Math.min(speed, d * 0.6));
                blend = 0.55;
            } else {
                // Inside: held in the middle, turning around it.
                Vec3 around = new Vec3(-to.z, 0, to.x).normalize().scale(0.15 * r);
                want = to.scale(0.4).add(around);
                blend = 0.5;
            }
            e.setDeltaMovement(e.getDeltaMovement().lerp(want, blend));
            e.fallDistance = 0;
            e.hurtMarked = true;
            if (e instanceof LivingEntity living && d < r * 1.3 + 0.5 && age % every == 0 && damage > 0) {
                Combat.hurtMagic(caster, living, damage);
            }
        }
    }

    private static boolean skip(LivingEntity e) {
        return e instanceof Player player && (player.isSpectator() || player.isCreative() && player.getAbilities().flying);
    }

    // Blocks torn off the top of the ground near it (only charged at least halfway, and with the gamerule on).
    private static void tearBlocks(ServerLevel level, Vec3 orb, double r, double c, AzulSpec.Segurado s, List<FallingBlockEntity> torn, int[] count) {
        if (c < 0.5 || s.blocosPorTick() <= 0 || !BlockRules.canBreak(level)) {
            return;
        }
        double reach = s.raioBlocos() * Mth.clamp((c - 0.5) * 2 * 0.5 + 0.5, 0, 1);
        if (reach <= 0.5) {
            return;
        }
        for (int i = 0; i < s.blocosPorTick() && count[0] < s.maxBlocos(); i++) {
            double a = RANDOM.nextDouble() * Mth.TWO_PI;
            double rr = Math.sqrt(RANDOM.nextDouble()) * reach;
            int x = Mth.floor(orb.x + Math.cos(a) * rr);
            int z = Mth.floor(orb.z + Math.sin(a) * rr);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
            if (!level.isLoaded(pos)) {
                continue;
            }
            double d = Vec3.atCenterOf(pos).distanceTo(orb);
            if (d > reach || RANDOM.nextDouble() > 1.2 - d / reach) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
                continue;
            }
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
            block.dropItem = false;
            block.setNoGravity(true);
            block.noPhysics = true;
            block.setDeltaMovement(0, 0.25, 0);
            torn.add(block);
            count[0]++;
        }
    }

    // The singularity: touching the ground and charged enough, it eats the blocks around its middle away (destroyed, no
    // drops), the nearer the faster, so it never sinks in and sits inside the ground. Returns whether it ate any.
    private static boolean eatGround(ServerLevel level, Vec3 orb, double r, double c, AzulSpec.Singularidade s, int[] eaten) {
        if (s == null || c + 1.0E-6 < s.cargaMin() || s.raio() <= 0 || !BlockRules.canBreak(level)) {
            return false;
        }
        // Touching: a solid block within its surface plus contato.
        double touch = r + Math.max(0, s.contato());
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        boolean touching = false;
        int span = Mth.ceil(touch);
        for (int dx = -span; dx <= span && !touching; dx++) {
            for (int dy = -span; dy <= span && !touching; dy++) {
                for (int dz = -span; dz <= span && !touching; dz++) {
                    probe.set(Mth.floor(orb.x) + dx, Mth.floor(orb.y) + dy, Mth.floor(orb.z) + dz);
                    if (Vec3.atCenterOf(probe).distanceToSqr(orb) <= touch * touch && level.isLoaded(probe)
                            && !level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()) {
                        touching = true;
                    }
                }
            }
        }
        if (!touching) {
            return false;
        }
        double reach = s.raio();
        // Stronger the more it was charged above cargaMin.
        double power = 0.6 + 0.4 * Mth.clamp((c - s.cargaMin()) / Math.max(1.0E-3, 1 - s.cargaMin()), 0, 1);
        int done = 0;
        for (int i = 0; i < s.tentativasPorTick() && done < s.maxBlocosPorTick(); i++) {
            // A random spot in the ball, more of them near the middle.
            double u = RANDOM.nextDouble();
            double d = reach * u * u;
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian());
            if (dir.lengthSqr() < 1.0E-6) {
                continue;
            }
            Vec3 at = orb.add(dir.normalize().scale(d));
            BlockPos pos = BlockPos.containing(at);
            double dist = Vec3.atCenterOf(pos).distanceTo(orb);
            if (dist > reach || RANDOM.nextDouble() > power * Math.pow(Math.max(0, 1 - dist / reach), Math.max(0.1, s.curva()))) {
                continue;
            }
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
                continue;
            }
            eaten[0]++;
            if (s.particulasACada() > 0 && eaten[0] % s.particulasACada() == 0) {
                // The break's crumbs and sound, on some of them only.
                level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
            }
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            done++;
        }
        return done > 0;
    }

    // The torn blocks spiral in and are crushed in the middle. Returns how many were crushed this tick.
    private static int steerBlocks(Vec3 orb, double r, List<FallingBlockEntity> torn) {
        int crushed = 0;
        for (FallingBlockEntity block : torn) {
            if (block.isRemoved()) {
                continue;
            }
            Vec3 to = orb.subtract(block.position().add(0, 0.5, 0));
            double d = to.length();
            if (d < Math.max(0.6, r * 0.55)) {
                block.discard();
                crushed++;
                continue;
            }
            Vec3 in = to.normalize();
            Vec3 around = to.cross(new Vec3(0, 1, 0));
            around = around.lengthSqr() > 1.0E-6 ? around.normalize() : Vec3.ZERO;
            Vec3 v = block.getDeltaMovement().add(in.scale(0.06 + 0.04 * RANDOM.nextDouble())).add(around.scale(0.05)).scale(0.9);
            if (v.length() > 0.9) {
                v = v.normalize().scale(0.9);
            }
            block.setDeltaMovement(v);
            block.time = 1;
        }
        torn.removeIf(Entity::isRemoved);
        return crushed;
    }

    // When it is over, the blocks still flying fall back down.
    private static void release(List<FallingBlockEntity> torn) {
        for (FallingBlockEntity block : torn) {
            if (!block.isRemoved()) {
                block.setNoGravity(false);
                block.noPhysics = false;
                block.setDeltaMovement(block.getDeltaMovement().scale(0.5));
            }
        }
        torn.clear();
    }

    private static void send(ServerLevel level, int id, int mode, Vec3 pos, double radius, float charge, int style) {
        ModPayloads.Azul payload = new ModPayloads.Azul(id, mode, pos, (float) radius, charge, style);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
