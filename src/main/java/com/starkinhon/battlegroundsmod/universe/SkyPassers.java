package com.starkinhon.battlegroundsmod.universe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.boss.AirCombat;
import com.starkinhon.battlegroundsmod.boss.BossFalas;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.boss.SoulMob;
import com.starkinhon.battlegroundsmod.challenge.ChallengeArenas;
import com.starkinhon.battlegroundsmod.command.SoulCommand;
import com.starkinhon.battlegroundsmod.power.Progression;
import com.starkinhon.battlegroundsmod.power.Tiers;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.Db;
import com.starkinhon.battlegroundsmod.power.powers.sub.db.DbCommon;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Dragon Ball characters flying across the sky (forja db-voo-mobs, approved by Stark 2026-11-12), real mobs passing far
// over the player: Goku on the boost, Goku and Vegeta training (they part, rush in, the fists meet, both vanish and
// come back further on; after 6 clashes they leave together), Piccolo with his arms crossed, Kuririn skimming low and
// Freeza floating by standing up. They are born far off, cross the sky in front of the player and are gone far off.
// In the Dragon Ball biomes by day under the open sky one comes every 3 to 5 minutes; elsewhere about one in 20.
// Flying up to them (24 blocks) they brake and look (Stark 2026-11-12): Goku and Kuririn wave, Piccolo crosses his
// arms and goes, Freeza threatens you, and Vegeta tells a weaker one (a worse tier) to go and train, while one of his
// tier or stronger is challenged to prove who is stronger: the air fight (boss/AirCombat).
// Hitting one of them starts the fight too. /soul passagem <tipo> brings one now (tests and videos).
public final class SkyPassers {
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final double DT = 0.05;
    private static final int CHECK = 20;
    private static final double NEAR = 24;
    private static final double GONE = 260;
    private static final String[] KINDS = {"solo", "duelo", "cruzeiro", "rasante", "freeza"};

    private static final List<Event> EVENTS = new ArrayList<>();
    private static final Map<UUID, Double> NEXT = new HashMap<>();

    private static final class Event {
        final String kind;
        final ServerLevel level;
        final List<SoulBoss> ents = new ArrayList<>();
        final Vec3 dir;
        final Vec3 perp;
        final double speed;
        final double low;
        double t;
        // reacting to a player flying up (seconds since, or -1)
        double react = -1;
        ServerPlayer seen;
        boolean leaving;
        // the duel
        Vec3 center;
        Vec3 axis = new Vec3(1, 0, 0);
        int phase;
        double pt;
        int clashes;

        Event(String kind, ServerLevel level, Vec3 dir, Vec3 perp, double speed, double low) {
            this.kind = kind;
            this.level = level;
            this.dir = dir;
            this.perp = perp;
            this.speed = speed;
            this.low = low;
        }
    }

    private SkyPassers() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SkyPassers::onServerTick);
        SoulCommand.addSubcommand(SkyPassers::command);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("passagem").requires(source -> source.hasPermission(2))
                .then(Commands.argument("tipo", StringArgumentType.word()).suggests((context, builder) -> {
                    for (String k : KINDS) {
                        builder.suggest(k);
                    }
                    return builder.buildFuture();
                }).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String kind = StringArgumentType.getString(context, "tipo");
                    if (!List.of(KINDS).contains(kind)) {
                        context.getSource().sendFailure(Component.literal("Tipos: solo, duelo, cruzeiro, rasante, freeza"));
                        return 0;
                    }
                    spawn(player, kind);
                    return 1;
                }));
    }

    // ------------------------------------------------------------------ spawning

    private static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % CHECK == 0 && Progression.on(server)) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                maybeSpawn(player);
            }
        }
        Iterator<Event> it = EVENTS.iterator();
        while (it.hasNext()) {
            Event e = it.next();
            if (step(e)) {
                for (SoulBoss b : e.ents) {
                    AirCombat air = AirCombat.of(b);
                    if (air != null && air.passing()) {
                        b.discard();
                    }
                }
                it.remove();
            }
        }
    }

    private static void maybeSpawn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (player.isSpectator() || !player.isAlive() || level.getDifficulty() == Difficulty.PEACEFUL
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || level.dimension().equals(ChallengeArenas.ARENAS)) {
            return;
        }
        String universe = UniverseBiomes.universeAt(level, player.blockPosition());
        boolean db = DragonBallWorld.UNIVERSE.equals(universe);
        if (!db && level.dimension() != Level.OVERWORLD) {
            return;
        }
        double next = NEXT.computeIfAbsent(player.getUUID(), k -> 1200.0 + RANDOM.nextInt(2400));
        // 3-5 minutes in the Dragon Ball biomes, about 20 elsewhere
        next -= CHECK * (db ? 1 : 0.2);
        NEXT.put(player.getUUID(), next);
        if (next > 0) {
            return;
        }
        if (!level.isDay() || !level.canSeeSky(player.blockPosition().above()) || busyNear(player)) {
            return;
        }
        NEXT.put(player.getUUID(), 3600.0 + RANDOM.nextInt(2400));
        spawn(player, KINDS[RANDOM.nextInt(KINDS.length)]);
    }

    private static boolean busyNear(ServerPlayer player) {
        for (Event e : EVENTS) {
            if (e.level == player.level() && !e.ents.isEmpty() && e.ents.get(0).distanceToSqr(player) < 400 * 400) {
                return true;
            }
        }
        return false;
    }

    private static EntityType<SoulMob> type(String name) {
        Supplier<EntityType<SoulMob>> s = switch (name) {
            case "vegeta" -> ModEntities.VEGETA;
            case "piccolo" -> ModEntities.PICCOLO;
            case "kuririn" -> ModEntities.KURIRIN;
            case "freeza" -> ModEntities.FREEZA;
            default -> ModEntities.GOKU;
        };
        return s.get();
    }

    // A path crossing the sky in front of where the player looks: `lateral` blocks ahead, `h` up.
    private static void spawn(ServerPlayer player, String kind) {
        ServerLevel level = player.serverLevel();
        Vec3 look = flat(player.getLookAngle());
        double ang = (RANDOM.nextBoolean() ? 1 : -1) * Math.PI / 2 + (RANDOM.nextDouble() - 0.5) * 0.8;
        Vec3 dir = new Vec3(look.x * Math.cos(ang) - look.z * Math.sin(ang), 0, look.x * Math.sin(ang) + look.z * Math.cos(ang)).normalize();
        double h, lateral, speed;
        switch (kind) {
            case "solo" -> {
                h = 48 + RANDOM.nextDouble() * 10;
                lateral = 40 + RANDOM.nextDouble() * 30;
                speed = 58;
            }
            case "cruzeiro" -> {
                h = 34 + RANDOM.nextDouble() * 8;
                lateral = 26 + RANDOM.nextDouble() * 14;
                speed = 26;
            }
            case "rasante" -> {
                h = 4.5;
                lateral = 14 + RANDOM.nextDouble() * 6;
                speed = 44;
            }
            case "freeza" -> {
                h = 11 + RANDOM.nextDouble() * 4;
                lateral = 12 + RANDOM.nextDouble() * 5;
                speed = 9;
            }
            default -> {
                h = 70 + RANDOM.nextDouble() * 10;
                lateral = 50 + RANDOM.nextDouble() * 30;
                speed = 55;
            }
        }
        Vec3 mid = player.position().add(look.scale(lateral));
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(mid.x), Mth.floor(mid.z));
        mid = new Vec3(mid.x, Math.max(player.getY(), ground) + h, mid.z);
        // born far off, but where mobs still move (inside the simulation distance)
        double back = "freeza".equals(kind) ? 55 : "duelo".equals(kind) ? 80 : 230;
        Vec3 start = mid.subtract(dir.scale(back));
        while (back > 30 && !level.isPositionEntityTicking(BlockPos.containing(start))) {
            back -= 16;
            start = mid.subtract(dir.scale(back));
        }
        Event e = new Event(kind, level, dir, look, speed, ground + h);
        if ("duelo".equals(kind)) {
            e.center = start;
            add(e, "goku", start.add(e.perp.scale(-12)), Vec3.ZERO, 0);
            add(e, "vegeta", start.add(e.perp.scale(12)), Vec3.ZERO, 0);
        } else {
            String name = switch (kind) {
                case "cruzeiro" -> "piccolo";
                case "rasante" -> "kuririn";
                case "freeza" -> "freeza";
                default -> "goku";
            };
            int flags = switch (kind) {
                case "cruzeiro" -> com.starkinhon.battlegroundsmod.universe.Flight.CROSSED;
                case "freeza" -> Flight.CROSSED | Flight.UPRIGHT;
                default -> 0;
            };
            add(e, name, start, dir.scale(speed), flags);
        }
        if (!e.ents.isEmpty()) {
            EVENTS.add(e);
        }
    }

    private static void add(Event e, String name, Vec3 at, Vec3 velocity, int flags) {
        SoulMob mob = type(name).create(e.level);
        if (mob == null) {
            return;
        }
        mob.moveTo(at.x, at.y, at.z, (float) Math.toDegrees(Math.atan2(-e.dir.x, e.dir.z)), 0);
        mob.finalizeSpawn(e.level, e.level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
        AirCombat air = AirCombat.of(mob);
        if (air == null) {
            return;
        }
        air.pass(velocity, !"cruzeiro".equals(e.kind) && !"freeza".equals(e.kind) && !"duelo".equals(e.kind), flags);
        mob.setNoGravity(true);
        e.level.addFreshEntity(mob);
        e.ents.add(mob);
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static Vec3 yaw(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    // ------------------------------------------------------------------ flying them

    // One tick of an event. True when it is over (all far off, gone or turned to a fight).
    private static boolean step(Event e) {
        e.t += DT;
        // hit by a player: the fight (the others go on)
        e.ents.removeIf(b -> {
            AirCombat air = AirCombat.of(b);
            if (b.isRemoved() || !b.isAlive() || air == null || !air.passing()) {
                return true;
            }
            if (b.getTarget() != null) {
                if (b.getLastHurtByMob() == b.getTarget()) {
                    air.fight(b.getTarget());
                    return true;
                }
                b.setTarget(null);
            }
            return false;
        });
        if (e.ents.isEmpty()) {
            return true;
        }
        boolean far = true;
        for (SoulBoss b : e.ents) {
            if (!e.level.isPositionEntityTicking(b.blockPosition())) {
                return true;
            }
            ServerPlayer p = (ServerPlayer) e.level.getNearestPlayer(b, GONE);
            if (p != null) {
                far = false;
            }
        }
        if (e.t > 2 && far || e.t > 120) {
            return true;
        }
        if (e.react < 0 && !e.leaving) {
            ServerPlayer near = flyerNear(e);
            if (near != null) {
                e.react = 0;
                e.seen = near;
                for (SoulBoss b : e.ents) {
                    AirCombat.of(b).boost(false);
                }
            }
        }
        if (e.react >= 0) {
            react(e);
        } else if ("duelo".equals(e.kind) && !e.leaving) {
            duel(e);
        } else {
            cruise(e);
        }
        return false;
    }

    private static ServerPlayer flyerNear(Event e) {
        for (SoulBoss b : e.ents) {
            for (ServerPlayer p : e.level.players()) {
                if (Flight.flying(p) && p.distanceToSqr(b) < NEAR * NEAR) {
                    return p;
                }
            }
        }
        return null;
    }

    private static void cruise(Event e) {
        for (SoulBoss b : e.ents) {
            AirCombat air = AirCombat.of(b);
            double curve = switch (e.kind) {
                case "solo" -> Math.sin(e.t * 0.9) * 0.35;
                case "rasante" -> Math.sin(e.t * 1.6) * 0.25;
                default -> 0;
            };
            Vec3 d = yaw(e.dir, curve);
            if ("solo".equals(e.kind)) {
                d = new Vec3(d.x, Math.sin(e.t * 0.7) * 0.06, d.z);
            }
            if ("rasante".equals(e.kind)) {
                double ground = e.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getBlockX(), b.getBlockZ());
                d = new Vec3(d.x, Mth.clamp((ground + 4.5 - b.getY()) * 0.1, -1, 1), d.z);
            }
            if (e.leaving) {
                int i = e.ents.indexOf(b);
                d = d.add(e.perp.scale((i % 2 == 0 ? 1 : -1) * Math.sin(e.t * 3) * 0.2));
                if (!"rasante".equals(e.kind)) {
                    d = d.add(0, 0.15, 0);
                }
            }
            air.steerTo(d, e.leaving ? ("duelo".equals(e.kind) ? 60 : 50) : e.speed, 2);
        }
    }

    // A player flew up to them: they brake, turn to look at them and react, then go on the boost.
    private static void react(Event e) {
        double before = e.react;
        e.react += DT;
        ServerPlayer p = e.seen;
        if (p == null || !p.isAlive() || p.level() != e.level) {
            e.react = 4;
        }
        for (int i = 0; i < e.ents.size(); i++) {
            SoulBoss b = e.ents.get(i);
            AirCombat air = AirCombat.of(b);
            if (p != null) {
                Vec3 to = flat(p.position().subtract(b.position()));
                Vec3 want = p.position().subtract(to.scale(9 + i * 3)).add(i * 4 - 2, 2, 0);
                Vec3 wish = want.subtract(b.position()).scale(0.25);
                if (wish.length() > 1) {
                    wish = wish.normalize();
                }
                air.steerTo(wish, 18, 3);
                b.getLookControl().setLookAt(p, 30, 30);
            }
        }
        if (before < 0.8 && e.react >= 0.8 && p != null) {
            List<SoulBoss> fighters = new ArrayList<>();
            for (SoulBoss b : e.ents) {
                AirCombat air = AirCombat.of(b);
                switch (b.bossId()) {
                    case "goku", "kuririn" -> air.flags(Flight.WAVE);
                    case "piccolo" -> air.flags(Flight.CROSSED);
                    case "freeza" -> {
                        air.flags(Flight.CROSSED | Flight.UPRIGHT);
                        BossFalas.line(b, "fala.battlegroundsmod.ceu.freeza_ameaca", BossFalas.colors("freeza"));
                    }
                    case "vegeta" -> {
                        if (Tiers.of(p) > Tiers.of(b)) {
                            air.flags(Flight.CROSSED);
                            BossFalas.line(b, "fala.battlegroundsmod.ceu.vegeta_treinar", BossFalas.colors("vegeta"));
                        } else {
                            BossFalas.line(b, "fala.battlegroundsmod.ceu.vegeta_desafio", BossFalas.colors("vegeta"));
                            SubCommon.pose(b, "db.apontar");
                            fighters.add(b);
                        }
                    }
                    default -> {
                    }
                }
            }
            for (SoulBoss b : fighters) {
                // a moment for the line, then he comes
                com.starkinhon.battlegroundsmod.combat.Tasks.later(24, () -> {
                    AirCombat air = AirCombat.of(b);
                    if (b.isAlive() && air != null && p.isAlive()) {
                        air.fight(p);
                    }
                });
            }
        }
        if (before < 2.2 && e.react >= 2.2) {
            for (SoulBoss b : e.ents) {
                AirCombat air = AirCombat.of(b);
                if ("goku".equals(b.bossId()) || "kuririn".equals(b.bossId())) {
                    air.flags(0);
                }
            }
        }
        if (e.react > 4) {
            e.react = -1;
            e.leaving = true;
            for (SoulBoss b : e.ents) {
                AirCombat air = AirCombat.of(b);
                if (!air.passing()) {
                    continue;
                }
                air.boost(true);
                // off on the boost and climbing a little, never straight through the player
                air.velocity(e.dir.scale(40).add(0, 10, 0));
            }
        }
    }

    // ------------------------------------------------------------------ the duel

    private static final int SEPARA = 0, INVESTE = 1, SOME = 2, FIM = 3;

    private static void duel(Event e) {
        if (e.ents.size() < 2) {
            e.leaving = true;
            return;
        }
        SoulBoss a = e.ents.get(0), b = e.ents.get(1);
        AirCombat aa = AirCombat.of(a), ab = AirCombat.of(b);
        e.pt += DT;
        e.center = e.center.add(e.dir.scale(10 * DT));
        switch (e.phase) {
            case SEPARA -> {
                for (int i = 0; i < 2; i++) {
                    SoulBoss m = e.ents.get(i);
                    Vec3 want = e.center.add(e.axis.scale(i == 1 ? 11 : -11));
                    Vec3 wish = want.subtract(m.position()).scale(0.3);
                    if (wish.length() > 1) {
                        wish = wish.normalize();
                    }
                    AirCombat.of(m).boost(false);
                    AirCombat.of(m).steerTo(wish, 30, 4);
                    m.getLookControl().setLookAt(e.center.x, e.center.y, e.center.z);
                }
                if (e.pt > 0.55) {
                    e.phase = INVESTE;
                    e.pt = 0;
                    aa.boost(true);
                    ab.boost(true);
                }
            }
            case INVESTE -> {
                aa.steerTo(e.center.subtract(a.position()).normalize(), 55, 8);
                ab.steerTo(e.center.subtract(b.position()).normalize(), 55, 8);
                double closing = aa.velocity().subtract(ab.velocity()).length() * DT;
                if (a.distanceTo(b) < 1.8 + closing || e.pt > 1) {
                    clash(e, a, b);
                }
            }
            case SOME -> {
                if (e.pt < DT * 1.5) {
                    for (SoulBoss m : e.ents) {
                        SubNet.send(e.level, SubNet.VANISH, 0, m.position().add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                        m.setInvisible(true);
                    }
                }
                if (e.pt > 0.32) {
                    e.center = e.center.add(e.dir.scale(22)).add(0, (RANDOM.nextDouble() - 0.5) * 8, 0);
                    for (int i = 0; i < 2; i++) {
                        SoulBoss m = e.ents.get(i);
                        Vec3 to = e.center.add(e.axis.scale(i == 1 ? 7 : -7));
                        m.teleportTo(to.x, to.y, to.z);
                        m.setInvisible(false);
                        AirCombat.of(m).velocity(Vec3.ZERO);
                        AirCombat.of(m).boost(true);
                        SubNet.send(e.level, SubNet.VANISH, 0, to.add(0, 1, 0), Vec3.ZERO, 0, 0, 0);
                    }
                    e.phase = INVESTE;
                    e.pt = 0;
                }
            }
            case FIM -> {
                if (e.pt > 0.6) {
                    e.leaving = true;
                    for (int i = 0; i < 2; i++) {
                        AirCombat air = AirCombat.of(e.ents.get(i));
                        air.boost(true);
                        air.velocity(e.dir.scale(30));
                    }
                } else {
                    aa.steerTo(Vec3.ZERO, 0, 3);
                    ab.steerTo(Vec3.ZERO, 0, 3);
                }
            }
            default -> {
            }
        }
    }

    private static void clash(Event e, SoulBoss a, SoulBoss b) {
        Vec3 mid = a.position().add(b.position()).scale(0.5).add(0, 0.9, 0);
        e.clashes++;
        SubCommon.pose(a, RANDOM.nextBoolean() ? "soco.combo4" : "soco.combo1");
        SubCommon.pose(b, "soco.combo2");
        DbCommon.hit(e.level, mid, 1.4f, Db.WHITE);
        DbCommon.hit(e.level, mid, 0.8f, Db.GOLD);
        SubCommon.sound(e.level, mid, SoundEvents.GENERIC_EXPLODE.value(), 4f, 1.4f);
        SubCommon.sound(e.level, mid, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 4f, 0.6f);
        Vec3 away = a.position().subtract(b.position());
        away = away.lengthSqr() < 1.0E-4 ? e.perp : away.normalize();
        AirCombat.of(a).velocity(away.scale(18));
        AirCombat.of(b).velocity(away.scale(-18));
        AirCombat.of(a).boost(false);
        AirCombat.of(b).boost(false);
        e.phase = e.clashes >= 6 ? FIM : e.clashes % 2 == 1 ? SOME : SEPARA;
        e.pt = 0;
        e.axis = new Vec3(RANDOM.nextDouble() - 0.5, (RANDOM.nextDouble() - 0.5) * 0.8, RANDOM.nextDouble() - 0.5).normalize();
    }
}
