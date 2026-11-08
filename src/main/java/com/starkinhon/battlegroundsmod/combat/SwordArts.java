package com.starkinhon.battlegroundsmod.combat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.skill.ComboSpec;
import com.starkinhon.battlegroundsmod.skill.DashAtaqueSpec;
import com.starkinhon.battlegroundsmod.skill.StaminaSpec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The base attacks with a sword in hand (any sword: for now only Aizen's Kyoka Suigetsu exists, but vanilla swords and
// any later katana get the same moves). Anime swordsmanship, not any one character's style:
// - Dash Attack: runs in low with the blade trailing behind (waki-gamae), and on reaching someone cuts flat right to
//   left as he goes THROUGH them, ending behind with his back to them; they stand frozen a moment and only then the
//   cut shows on them ("it was already cut"). The combo goes on from there, as with the punches.
// - Ataque Forte: raises the sword by the right shoulder with both hands (hasso) and charges while held, the blade
//   lighting up; let go, a big diagonal cut down across the body (kesagiri) that breaks guards and throws. Fully
//   charged, a glint runs along the blade and the cut flies on as a crescent of light.
// - Barragem: one-handed cuts from every side, four a round (down across, back up, flat, rising), for as long as it is
//   held, ending in a two-handed rising cut over the head that throws them up.
// The poses are keyframe animations (assets/battlegroundsmod/animations/espada.*.json, which also turn the sword
// about the wrist) and the light is drawn by client/fx/SwordFx: the blade's own trail, cuts on whoever is hit, the wave.
// The hitboxes follow the blade: a slice in front, as wide as the swing, not a punch's narrow cone.
public final class SwordArts {
    public static final String DASH_RUN = "espada.dash";
    // Kokushibo's quick draw (tools/espada/guardar.js): his sword rests in the sheath out of combat mode, and the first
    // click of the combo pulls it out cutting.
    public static final String QUICK_DRAW = "espada.sacar_kokushibo";
    // Its cut lands this much later than the combo's first: the hand first pulls the blade out along the sheath.
    static final int QUICK_DRAW_DELAY = 2;
    private static final String DASH_CUT = "espada.dash_corte";
    private static final String STRONG_CHARGE = "espada.forte_carga";
    private static final String STRONG_CUT = "espada.forte_corte";
    private static final String BARRAGE = "espada.barragem";
    private static final String BARRAGE_END = "espada.barragem_final";

    // Ataque Forte: wind-up into hasso, then up to this much longer charging, for up to this much more damage.
    private static final int STRONG_WINDUP = 6;
    private static final int STRONG_CHARGE_TICKS = 30;
    private static final float STRONG_CHARGE_BONUS = 1.5f;
    // The cut lands this many ticks after the key is let go (the blade crossing in front, see espada.forte_corte).
    private static final int STRONG_CUT_AT = 3;
    private static final double STRONG_REACH = 4.4;
    // The fully charged cut's wave: blocks it flies, how fast.
    private static final double WAVE_RANGE = 14;
    private static final double WAVE_SPEED = 1.7;
    // Barragem: one round of cuts (espada.barragem) and how many cuts in it; it lasts at least MIN rounds, and while
    // held up to MAX.
    private static final int ROUND = 12;
    private static final int CUTS_PER_ROUND = 4;
    private static final int BARRAGE_MIN_ROUNDS = 2;
    private static final int BARRAGE_MAX_ROUNDS = 5;
    private static final double BARRAGE_REACH = 3.8;
    // The finisher's rising cut crosses in front this many ticks in, and the move is over at END.
    private static final int FINISH_CUT_AT = 5;
    private static final int FINISH_END = 10;
    // Dash Attack: the cut passes them at PASS, and shows on them at REVEAL.
    private static final int DASH_PASS = 2;
    private static final int DASH_REVEAL = 9;
    // How far past them he ends up.
    private static final double DASH_THROUGH = 1.5;

    private static final double VIEW_DISTANCE = 128;

    // What the client draws (client/fx/SwordFx), ModPayloads.SwordFx's kind (sent as kind | rgb << 4).
    // A cut across pos along dir: a = length, b = width.
    public static final int FX_CUT = 0;
    // A crescent flying from pos along dir: a = how far it goes, b = its tilt about dir (degrees).
    public static final int FX_WAVE = 1;
    // The entity's sword moves are drawn in this color (sent when a move starts).
    public static final int FX_COLOR = 2;
    // A glint runs along the entity's blade (the Forte is fully charged).
    public static final int FX_GLINT = 3;
    // Kokushibo's 1st Form, Yoru no Miya (client/fx/MoonSlashRenderer): pos = his eyes, dir = his look, a = the moons' seed.
    public static final int FX_MOON = 4;
    // Yoru no Miya's blow landing on the entity (with the cut): impact frame and shake there. pos = its middle.
    public static final int FX_MOON_HIT = 5;
    // Kokushibo's 2nd, 3rd, 5th or 6th Form, or an awakened one (client/fx/MoonFormsRenderer): pos = his feet, dir = his
    // look, a = the seed, b = the form (+ 100 at the full moon).
    public static final int FX_MOON_FORM = 6;
    // One target of the 16th Form (sent just before it): entityId = the target, a = the form's seed, b = its index.
    public static final int FX_MOON_TARGET = 7;
    // Kokushibo's sword stretching (a = 1) or going back (a = 0) on the H key (client/outfit/SwordStretch).
    public static final int FX_MOON_STRETCH = 8;

    private SwordArts() {
    }

    public static boolean held(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof SwordItem;
    }

    // ---------- Dash Attack ----------

    // From DashAttack when the slide reaches someone.
    static void dashCut(LivingEntity attacker, LivingEntity target, DashAtaqueSpec spec) {
        ServerLevel level = Combat.level(attacker);
        PowerData data = attacker.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        int color = color(attacker);
        data.busyTicks = DASH_REVEAL + 2;
        CombatMode.enter(attacker);
        // The combo goes on from the next hit, as after the punching Dash Attack.
        ComboSpec combo = ComboSpec.get();
        int step = Math.floorMod(spec.golpeDoCombo(), combo.golpes().size());
        data.comboStep = step + 1 >= combo.golpes().size() ? 0 : step + 1;
        data.comboResetTicks = combo.resetTicks() + DASH_REVEAL;
        data.punchDelay = DASH_REVEAL;
        Stamina.gain(attacker, StaminaSpec.get().ganhoGolpePct());
        anim(attacker, DASH_CUT);
        Fx.sound(level, attacker.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.5f);
        DashAtaqueSpec.Golpe hit = spec.golpe();
        float damage = (float) (combo.golpe(step).dano() * hit.multiplicadorDano()) * Melee.multiplier(attacker);
        Tasks.add(age -> {
            if (!attacker.isAlive() || data.interrupts != interrupts) {
                return true;
            }
            if (age == DASH_PASS) {
                if (!Stun.canAttack(attacker) || Freeze.isFrozen(attacker)) {
                    return true;
                }
                passThrough(attacker, target, level, color);
                if (target.isAlive() && Combat.isTarget(target, attacker)) {
                    // They stand there, not knowing yet.
                    Freeze.hold(target, DASH_REVEAL - DASH_PASS);
                }
                return false;
            }
            if (age < DASH_REVEAL) {
                return false;
            }
            if (target.isAlive() && Combat.isTarget(target, attacker) && target.distanceTo(attacker) < 6) {
                Vec3 side = right(Combat.flatLook(attacker));
                Vec3 line = side.scale(-1).add(0, 0.18, 0).normalize();
                Vec3 away = target.position().subtract(attacker.position());
                away = new Vec3(away.x, 0, away.z);
                away = away.lengthSqr() < 1.0E-4 ? Combat.flatLook(attacker) : away.normalize();
                land(attacker, target, damage, hit.stunTicks(), hit.hitstopTicks() + 2, hit.tremor() + 0.2f, away,
                        hit.empurrao() + 0.3, hit.subida() + 0.15, "final");
                cut(level, target, line, 2.6f, 1.2f, color);
                Fx.sound(level, Combat.center(target), SoundEvents.PLAYER_ATTACK_CRIT, 1.3f, 0.7f);
                Fx.sound(level, Combat.center(target), SoundEvents.TRIDENT_THROW.value(), 1.0f, 0.6f);
            }
            return true;
        });
    }

    // Puts him on the far side of the target, still facing the way he ran (his back to them). If there's no room,
    // he cuts from where he is.
    private static void passThrough(LivingEntity attacker, LivingEntity target, ServerLevel level, int color) {
        Vec3 from = attacker.position();
        Vec3 to = target.position().subtract(from);
        Vec3 flat = new Vec3(to.x, 0, to.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Combat.flatLook(attacker) : flat.normalize();
        Vec3 spot = target.position().add(flat.scale(target.getBbWidth() * 0.5 + DASH_THROUGH));
        boolean room = attacker.level().noCollision(attacker, attacker.getBoundingBox().move(spot.subtract(from)))
                && level.clip(new ClipContext(from.add(0, 1, 0), spot.add(0, 1, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker))
                        .getType() == HitResult.Type.MISS;
        Fx.sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.9f);
        if (!room) {
            return;
        }
        float yRot = (float) (Mth.atan2(-flat.x, flat.z) * Mth.RAD_TO_DEG);
        if (attacker instanceof ServerPlayer player) {
            player.connection.teleport(spot.x, spot.y, spot.z, yRot, player.getXRot());
        } else {
            attacker.moveTo(spot.x, spot.y, spot.z, yRot, attacker.getXRot());
            attacker.setYHeadRot(yRot);
        }
        attacker.setDeltaMovement(flat.scale(0.15));
        attacker.hurtMarked = true;
        // A streak of light where he went through. No sprite puffs or rings: the blade's own light does the rest.
        int light = Melee.lighter(color);
        Fx.line(level, ModParticles.streak(light, 1.6f, 6), from.add(0, 1, 0), spot.add(0, 1, 0), 0.5);
    }

    // ---------- Ataque Forte ----------

    static boolean strong(LivingEntity player, ServerLevel level) {
        PowerData data = player.getData(ModAttachments.POWER);
        float mult = Melee.multiplier(player);
        int color = color(player);
        int interrupts = data.interrupts;
        data.busyTicks = STRONG_WINDUP + 2;
        anim(player, STRONG_CHARGE);
        Fx.sound(level, player.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 0.8f);
        float[] released = {-1};
        int[] cutAt = {-1};
        Tasks.add(age -> {
            // Super armor: only a real stun (Stun.INTERRUPT_TICKS+) cuts it off, plain punches don't.
            if (!player.isAlive() || data.interrupts != interrupts || Stun.real(player)) {
                if (released[0] < 0) {
                    anim(player, "");
                }
                return true;
            }
            if (released[0] < 0) {
                boolean charging = age < STRONG_WINDUP || data.attackHeld && age < STRONG_WINDUP + STRONG_CHARGE_TICKS;
                float charge = Mth.clamp((age - STRONG_WINDUP) / (float) STRONG_CHARGE_TICKS, 0, 1);
                if (charging) {
                    data.busyTicks = 2;
                    if (data.attackHeld) {
                        Charge.report(player, charge);
                    }
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
                    if (age > STRONG_WINDUP && (age - STRONG_WINDUP) % 8 == 0) {
                        Fx.sound(level, Combat.handPos(player), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 0.8f + charge * 0.9f);
                    }
                    if (age == STRONG_WINDUP + STRONG_CHARGE_TICKS - 1) {
                        Fx.sound(level, Combat.handPos(player), SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 1.8f);
                        send(level, FX_GLINT, player, player.position(), Vec3.ZERO, 0, 0, color);
                    }
                    return false;
                }
                // Let go (or fully charged): the cut.
                released[0] = charge;
                cutAt[0] = age;
                data.busyTicks = STRONG_CUT_AT + 8;
                anim(player, STRONG_CUT);
                Fx.sound(level, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.75f);
                Vec3 look = Combat.flatLook(player);
                player.setDeltaMovement(player.getDeltaMovement().add(look.scale(0.35)));
                player.hurtMarked = true;
                return false;
            }
            if (age - cutAt[0] < STRONG_CUT_AT) {
                return false;
            }
            float charge = released[0];
            float power = 1 + STRONG_CHARGE_BONUS * charge;
            Vec3 look = player.getLookAngle();
            Vec3 flat = Combat.flatLook(player);
            Vec3 side = right(flat);
            // Down across, right shoulder to left hip (seen from him).
            Vec3 line = side.scale(-0.75).add(0, -1, 0).normalize();
            Fx.sound(level, player.getEyePosition(), SoundEvents.PLAYER_ATTACK_STRONG, 1.4f, 0.65f - charge * 0.15f);
            Fx.sound(level, player.getEyePosition(), SoundEvents.TRIDENT_THROW.value(), 1.2f, 0.75f);
            for (LivingEntity target : slashed(player, STRONG_REACH + 0.8 * charge, -80, 80)) {
                Defense.breakGuard(target, player);
                if (land(player, target, 10f * mult * power, 16, 4 + Math.round(3 * charge), 0.5f + charge * 0.5f, look,
                        2.4 * (1 + 0.8 * charge), 0.55 + 0.3 * charge, "final")) {
                    cut(level, target, line, 2.4f + 1.2f * charge, 1.1f + 0.8f * charge, color);
                }
            }
            if (charge >= 0.999f) {
                wave(player, level, color, mult);
            }
            return true;
        });
        return true;
    }

    // The fully charged Forte's cut flies on: a crescent of light that cuts everyone it passes, until a wall.
    private static void wave(LivingEntity player, ServerLevel level, int color, float mult) {
        Vec3 dir = player.getLookAngle();
        Vec3 start = player.getEyePosition().add(0, -0.3, 0).add(dir.scale(1.2));
        Vec3 far = start.add(dir.scale(WAVE_RANGE));
        var hitWall = level.clip(new ClipContext(start, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double range = hitWall.getType() == HitResult.Type.MISS ? WAVE_RANGE : hitWall.getLocation().distanceTo(start);
        send(level, FX_WAVE, player, start, dir, (float) range, -38, color);
        Fx.sound(level, start, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.8f);
        Set<Integer> done = new HashSet<>();
        int ticks = (int) Math.ceil(range / WAVE_SPEED);
        Vec3 side = right(Combat.flatLook(player));
        Vec3 line = side.scale(-0.75).add(0, -1, 0).normalize();
        Tasks.add(age -> {
            double from = Math.min(range, age * WAVE_SPEED);
            double to = Math.min(range, (age + 1) * WAVE_SPEED);
            Vec3 a = start.add(dir.scale(from));
            Vec3 b = start.add(dir.scale(to));
            for (LivingEntity target : Combat.alongLine(level, a, b, 1.6, player)) {
                if (done.add(target.getId())) {
                    Defense.breakGuard(target, player);
                    if (Combat.hurt(player, target, 9f * mult)) {
                        Stun.apply(target, 14);
                        Melee.shake(target, 0.6f, 8);
                        cut(level, target, line, 3f, 1.6f, color);
                    }
                    Combat.push(target, dir, 1.6, 0.4);
                }
            }
            if (age >= ticks) {
                if (range < WAVE_RANGE) {
                    PunchFx.hit(level, PunchFx.FINAL, b, dir, color, 1, b.y - 1);
                    Fx.sound(level, b, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
                }
                return true;
            }
            return false;
        });
    }

    // ---------- Barragem ----------

    static boolean barrage(LivingEntity player, ServerLevel level) {
        PowerData data = player.getData(ModAttachments.POWER);
        float mult = Melee.multiplier(player);
        int color = color(player);
        int interrupts = data.interrupts;
        float hitDamage = 1.5f * mult;
        float finisherDamage = 6f * mult;
        data.busyTicks = ROUND * BARRAGE_MIN_ROUNDS + 4;
        int[] finishAt = {-1};
        Tasks.add(age -> {
            // Super armor: only a real stun (Stun.INTERRUPT_TICKS+) cuts it off, plain punches don't.
            if (!player.isAlive() || data.interrupts != interrupts || Stun.real(player)) {
                anim(player, "");
                return true;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            if (finishAt[0] < 0) {
                int round = age / ROUND;
                if (age % ROUND == 0) {
                    boolean more = round < BARRAGE_MIN_ROUNDS || data.attackHeld && round < BARRAGE_MAX_ROUNDS;
                    if (!more) {
                        finishAt[0] = age;
                        data.busyTicks = FINISH_END + 4;
                        anim(player, BARRAGE_END);
                        Fx.sound(level, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.9f);
                        return false;
                    }
                    anim(player, BARRAGE);
                }
                data.busyTicks = 3;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 1, false, false));
                int inRound = age % ROUND;
                int cutLen = ROUND / CUTS_PER_ROUND;
                // Each cut: the swish as it starts, the hit as the blade crosses in front.
                if (inRound % cutLen == 0) {
                    Fx.sound(level, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.25f + level.random.nextFloat() * 0.5f);
                }
                if (inRound % cutLen == cutLen - 1) {
                    int which = inRound / cutLen;
                    Vec3 line = barrageLine(Combat.flatLook(player), which);
                    for (LivingEntity target : slashed(player, BARRAGE_REACH, -80, 80)) {
                        if (Combat.hurt(player, target, hitDamage)) {
                            Melee.hitFx(player, target, "golpe");
                            Stamina.gain(player, StaminaSpec.get().ganhoAcertoBarragemPct());
                            Melee.shake(target, 0.15f, 4);
                            cut(level, target, jitter(level, line), 1.8f, 0.7f, color);
                        }
                        // Keeps them in front for the whole barrage.
                        Combat.push(target, look, 0.2, 0.05);
                    }
                    Fx.sound(level, eye, SoundEvents.PLAYER_ATTACK_WEAK, 0.6f, 1.4f + level.random.nextFloat() * 0.4f);
                }
                return false;
            }
            int since = age - finishAt[0];
            if (since == FINISH_CUT_AT) {
                Fx.sound(level, eye, SoundEvents.PLAYER_ATTACK_STRONG, 1.4f, 0.8f);
                Fx.sound(level, eye, SoundEvents.TRIDENT_THROW.value(), 1.1f, 0.9f);
                for (LivingEntity target : slashed(player, BARRAGE_REACH + 0.4, -70, 70)) {
                    if (land(player, target, finisherDamage, 14, 4, 0.6f, look, 1.2, 1.15, "final")) {
                        cut(level, target, new Vec3(0, 1, 0), 3.2f, 1.4f, color);
                    }
                }
            }
            if (since >= FINISH_END) {
                data.busyTicks = 2;
                return true;
            }
            return false;
        });
        return true;
    }

    // The line each barrage cut leaves (see espada.barragem): down across, back up the other way, flat, rising.
    private static Vec3 barrageLine(Vec3 flat, int which) {
        Vec3 side = right(flat);
        return switch (which) {
            case 0 -> side.scale(-0.8).add(0, -1, 0).normalize();
            case 1 -> side.scale(0.9).add(0, 0.8, 0).normalize();
            case 2 -> side.scale(-1).add(0, -0.08, 0).normalize();
            default -> side.scale(0.8).add(0, 1, 0).normalize();
        };
    }

    private static Vec3 jitter(ServerLevel level, Vec3 line) {
        return line.add((level.random.nextDouble() - 0.5) * 0.3, (level.random.nextDouble() - 0.5) * 0.3, (level.random.nextDouble() - 0.5) * 0.3)
                .normalize();
    }

    // ---------- Combo (left click) ----------

    // The five cuts of the click combo (espada.combo1..5): flat right to left, rising back across, kesagiri, a thrust,
    // and the two-handed overhead cut that ends it. Same timings and values as the punches (padrao.combo.json); the
    // blade reaches a bit further and cuts everyone in its arc.
    private static final String COMBO = "espada.combo";
    private static final double[][] COMBO_ARC = {{-80, 80}, {-65, 65}, {-60, 60}, {-18, 18}, {-35, 35}};
    private static final double[] COMBO_EXTRA_REACH = {0.4, 0.4, 0.4, 1.0, 0.6};

    // Returns the extra ticks before its cut lands (the quick draw's).
    static int comboSwing(LivingEntity attacker, int step) {
        color(attacker);
        int delay = 0;
        if (quickDraw(attacker)) {
            // Out of the sheath and cutting in one move (iaido); it ends up high on the right, where the third cut starts.
            attacker.getData(ModAttachments.POWER).comboStep = 2;
            anim(attacker, QUICK_DRAW);
            delay = QUICK_DRAW_DELAY;
        } else {
            anim(attacker, COMBO + (Math.floorMod(step, COMBO_ARC.length) + 1));
        }
        ServerLevel level = Combat.level(attacker);
        boolean last = step == COMBO_ARC.length - 1;
        Fx.sound(level, attacker.getEyePosition(), SoundEvents.PLAYER_ATTACK_SWEEP, last ? 1.0f : 0.6f,
                (last ? 0.85f : 1.25f) + level.random.nextFloat() * 0.25f);
        return delay;
    }

    // Lands combo cut step. Returns true when it connected with anyone (blocked or not).
    static boolean comboCut(LivingEntity attacker, LivingEntity target, int step, ComboSpec.Golpe golpe, ComboSpec spec) {
        if (!attacker.isAlive() || (!Stun.canAttack(attacker) || Freeze.isFrozen(attacker)) && !Melee.struckFirst(attacker)) {
            return false;
        }
        ServerLevel level = Combat.level(attacker);
        int i = Math.floorMod(step, COMBO_ARC.length);
        double reach = spec.alcance() + COMBO_EXTRA_REACH[i];
        List<LivingEntity> hit = slashed(attacker, reach, COMBO_ARC[i][0], COMBO_ARC[i][1]);
        // The one he chased or clicked on is cut even a bit off the arc.
        if (target != null && !hit.contains(target) && Combat.isTarget(target, attacker) && attacker.distanceTo(target) <= reach + 1.2) {
            hit.add(target);
        }
        Vec3 eye = attacker.getEyePosition();
        if (hit.isEmpty()) {
            Fx.sound(level, eye, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.5f, 1.3f);
            return false;
        }
        boolean last = "final".equals(golpe.momento());
        float damage = (float) golpe.dano() * Melee.multiplier(attacker);
        int color = Melee.lighter(Melee.fistColor(attacker));
        Vec3 look = attacker.getLookAngle();
        Vec3 line = comboLine(attacker, i);
        boolean any = false;
        for (LivingEntity e : hit) {
            if (land(attacker, e, damage, golpe.stunTicks(), golpe.hitstopTicks(), golpe.tremor(), look, golpe.empurrao(), golpe.subida(),
                    golpe.momento())) {
                any = true;
                Melee.markHit(attacker, e);
                cut(level, e, jitter(level, line), last ? 2.8f : i == 3 ? 1.2f : 2.0f, last ? 1.2f : 0.8f, color);
            }
        }
        if (any) {
            Fx.sound(level, eye, last ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP, last ? 1.3f : 0.9f,
                    last ? 0.8f : 1.5f + level.random.nextFloat() * 0.3f);
            Fx.sound(level, eye, SoundEvents.PLAYER_ATTACK_CRIT, 0.6f, 1.5f);
            if (last) {
                Fx.sound(level, eye, SoundEvents.TRIDENT_THROW.value(), 1.0f, 0.8f);
            }
        }
        return any;
    }

    // The line each combo cut leaves on whoever it hits (see tools/espada/anims.js).
    private static Vec3 comboLine(LivingEntity attacker, int step) {
        Vec3 flat = Combat.flatLook(attacker);
        Vec3 side = right(flat);
        return switch (step) {
            case 0 -> side.scale(-1).add(0, -0.1, 0).normalize();
            case 1 -> side.scale(0.9).add(0, 0.8, 0).normalize();
            case 2 -> side.scale(-0.8).add(0, -1, 0).normalize();
            case 3 -> flat;
            default -> new Vec3(0, -1, 0).add(side.scale(0.1)).normalize();
        };
    }

    // ---------- shared ----------

    // Everyone the swing reaches: in front within reach (plus their own width), between the two angles left/right of
    // where he faces (degrees, + = right), at a height the blade can reach (it follows where he looks up or down).
    // Whoever he aims right at is always in.
    static List<LivingEntity> slashed(LivingEntity attacker, double reach, double fromDeg, double toDeg) {
        Vec3 eye = attacker.getEyePosition();
        Vec3 look = attacker.getLookAngle();
        Vec3 flat = Combat.flatLook(attacker);
        Vec3 side = right(flat);
        List<LivingEntity> out = new ArrayList<>();
        LivingEntity aimed = Combat.raycastTarget(attacker, reach + 0.5);
        if (aimed != null) {
            out.add(aimed);
        }
        for (LivingEntity e : Combat.inRadius(Combat.level(attacker), eye, reach + 3, attacker)) {
            if (e == aimed) {
                continue;
            }
            Vec3 to = Combat.center(e).subtract(eye);
            Vec3 h = new Vec3(to.x, 0, to.z);
            double dist = h.length() - e.getBbWidth() * 0.5;
            if (dist > reach) {
                continue;
            }
            double angle = Math.toDegrees(Math.atan2(h.dot(side), h.dot(flat)));
            if (h.length() > 0.6 && (angle < fromDeg || angle > toDeg)) {
                continue;
            }
            // The band the blade sweeps, tilted with the look.
            double mid = eye.y - 0.4 + look.y * Math.max(0, h.length());
            if (e.getBoundingBox().maxY < mid - 1.6 || e.getBoundingBox().minY > mid + 1.4) {
                continue;
            }
            if (attacker.hasLineOfSight(e)) {
                out.add(e);
            }
        }
        return out;
    }

    // A sword hit: damage, then (unless guarded) stun, hitstop, shake and the push after the hitstop.
    private static boolean land(LivingEntity attacker, LivingEntity hit, float damage, int stun, int stop, float tremor, Vec3 pushDir,
            double push, double up, String momento) {
        if (!Combat.hurt(attacker, hit, damage)) {
            return false;
        }
        Stamina.gain(attacker, StaminaSpec.get().ganhoAcertoPct());
        if (Defense.blockedThisTick(hit)) {
            return true;
        }
        Stun.apply(hit, stun);
        Melee.hitFx(attacker, hit, momento);
        if (stop > 0) {
            Freeze.hold(hit, stop);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new ModPayloads.Hitstop(attacker.getId(), stop));
        }
        Melee.shake(attacker, tremor, stop + 4);
        Melee.shake(hit, tremor * 1.3f, stop + 4);
        Tasks.later(stop, () -> {
            if (hit.isAlive()) {
                Combat.push(hit, pushDir, push, up);
            }
        });
        return true;
    }

    // The line of light a cut leaves across the target.
    private static void cut(ServerLevel level, LivingEntity target, Vec3 line, float length, float width, int color) {
        send(level, FX_CUT, target, Combat.center(target), line, length, width, color);
    }

    // The color his sword moves are drawn in (his power's), told to everyone around when a move starts.
    static int color(LivingEntity entity) {
        int color = Melee.lighter(Melee.fistColor(entity));
        send(Combat.level(entity), FX_COLOR, entity, entity.position(), Vec3.ZERO, 0, 0, color);
        return color;
    }

    // True when this swing takes Kokushibo's sword out of its sheath: the watchers are told right away, before the move's
    // animation, so the sword leaves the sheath on its first frame.
    private static boolean quickDraw(LivingEntity attacker) {
        if (!(attacker instanceof ServerPlayer player)
                || !(player.getMainHandItem().getItem() instanceof com.starkinhon.battlegroundsmod.item.KokushiboSwordItem)) {
            return false;
        }
        boolean sheathed = player.getData(ModAttachments.POWER).sheathSent == 1;
        com.starkinhon.battlegroundsmod.power.PowerLogic.syncSheath(player);
        return sheathed;
    }

    private static void anim(LivingEntity entity, String move) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new ModPayloads.PlaySkillAnimation(entity.getId(), move, false));
    }

    private static void send(ServerLevel level, int kind, LivingEntity entity, Vec3 pos, Vec3 dir, float a, float b, int color) {
        ModPayloads.SwordFx payload = new ModPayloads.SwordFx(kind | (color & 0xFFFFFF) << 4, entity.getId(), pos, dir, a, b);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // His right, flat.
    private static Vec3 right(Vec3 flat) {
        return new Vec3(-flat.z, 0, flat.x);
    }
}
