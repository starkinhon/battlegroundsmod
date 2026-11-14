package com.starkinhon.battlegroundsmod.animal.jjmob;

import static com.starkinhon.battlegroundsmod.animal.jjmob.JjMobMoves.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.world.ModWorldgen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// The Kogane of the Jogo do Abate (Stark 2026-11-14, preview tools/mobs/jjmobs/kogane.js): in the Colônia every player
// has one at their shoulder. It never fights and never dies: a blow goes through it, it puffs away ('some') and comes
// back. It says hello when it comes ('oi'), the rules the first time ('anuncio'), and the points when its player
// beats a Jogador do Abate ('pontos': the plate over the beaten one's head goes to the player; the total is kept on
// the player). Its paper bubble's text is a lang key (jj.kogane.*), so it speaks the game's language.
final class JjKogane {
    private JjKogane() {
    }

    // each player's Kogane (entity id), the ones on their way out (the player left the Colônia), until when each bubble shows
    private static final Map<UUID, Integer> OF = new HashMap<>();
    private static final Set<Integer> LEAVING = new HashSet<>();
    private static final Map<Integer, Integer> SAY_UNTIL = new HashMap<>();
    static final String POINTS = "jj_abate_points";
    private static final String MET = "jj_kogane_met";

    static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone e) -> {
            // the points survive death
            CompoundTag old = e.getOriginal().getPersistentData();
            if (old.contains(POINTS)) {
                e.getEntity().getPersistentData().putInt(POINTS, old.getInt(POINTS));
            }
            if (old.getBoolean(MET)) {
                e.getEntity().getPersistentData().putBoolean(MET, true);
            }
        });
    }

    static void resetStatics() {
        OF.clear();
        LEAVING.clear();
        SAY_UNTIL.clear();
    }

    private static void say(JjMob k, String text, int ticks) {
        k.setSay(text);
        SAY_UNTIL.put(k.getId(), k.tickCount + ticks);
    }

    private static void play(JjMob k, String mode) {
        if (k.attacking()) {
            k.cutBlow();
            return;
        }
        k.blow(k.spec().kindOf(mode), null);
    }

    // Called every few seconds for a player in the Colônia: their Kogane comes if it is not there.
    static void ensure(ServerLevel level, ServerPlayer player) {
        Integer id = OF.get(player.getUUID());
        if (id != null && level.getEntity(id) instanceof JjMob k && k.isAlive() && !LEAVING.contains(id)) {
            return;
        }
        var type = ModEntities.JJ_MOBS.get("jj_kogane");
        if (type == null) {
            return;
        }
        JjMob k = type.get().create(level);
        if (k == null) {
            return;
        }
        Vec3 at = spot(player);
        k.moveTo(at.x, at.y, at.z, player.getYRot() + 180, 0);
        k.finalizeSpawn(level, level.getCurrentDifficultyAt(k.blockPosition()), MobSpawnType.EVENT, null);
        k.owner = player.getUUID();
        level.addFreshEntity(k);
        OF.put(player.getUUID(), k.getId());
        SubNet.send(level, SubNet.POOF, 0, at, Vec3.ZERO, 0.6f, 0, 0);
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(MET)) {
            data.putBoolean(MET, true);
            say(k, "jj.kogane.anuncio", 100);
            play(k, "anuncio");
        } else {
            say(k, "jj.kogane.oi", 80);
            play(k, "oi");
        }
    }

    // by its player's shoulder
    private static Vec3 spot(Player p) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-3 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        return p.position().add(side.scale(0.9)).add(look.scale(0.4)).add(0, p.getBbHeight() + 0.2, 0);
    }

    // a blow goes through it: it puffs away and comes back at its player's shoulder
    static void struck(JjMob k) {
        if (!k.attacking()) {
            play(k, "some");
        }
    }

    // A Jogador do Abate beaten by a player: its points go to the player, and the player's Kogane shows them.
    static void abateBeaten(JjMob abate, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int pts = Math.max(1, abate.points());
        CompoundTag data = player.getPersistentData();
        int total = data.getInt(POINTS) + pts;
        data.putInt(POINTS, total);
        Integer id = OF.get(player.getUUID());
        if (id != null && player.serverLevel().getEntity(id) instanceof JjMob k && k.isAlive()) {
            say(k, "jj.kogane.pontos|" + pts + "|" + total, 84);
            play(k, "pontos");
            sound(k, SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.6f);
        }
    }

    static final Moves MOVES = new Moves() {
        public boolean canUse(JjMob m, String mode, LivingEntity t) {
            return false;
        }

        public void tick(JjMob k, String mode, int f, LivingEntity t) {
            if (mode.equals("some") && f == 6) {
                SubNet.send(lvl(k), SubNet.POOF, 0, k.position().add(0, 0.3, 0), Vec3.ZERO, 0.5f, 0, 0);
                sound(k, SoundEvents.CHICKEN_EGG, 1f, 1.6f);
            }
            if (mode.equals("oi") && f == 10) {
                sound(k, SoundEvents.CHICKEN_AMBIENT, 1f, 1.8f);
            }
        }

        public void end(JjMob k, String mode, LivingEntity t) {
            if (!mode.equals("some")) {
                return;
            }
            if (LEAVING.remove(k.getId())) {
                k.discard();
                return;
            }
            Player p = k.ownerPlayer();
            if (p != null) {
                Vec3 at = spot(p);
                k.teleportTo(at.x, at.y, at.z);
                SubNet.send(lvl(k), SubNet.POOF, 0, at, Vec3.ZERO, 0.5f, 0, 0);
            }
        }

        public void idle(JjMob k) {
            Integer until = SAY_UNTIL.get(k.getId());
            if (until != null && k.tickCount > until) {
                SAY_UNTIL.remove(k.getId());
                k.setSay("");
            }
            Player p = k.ownerPlayer();
            if (p == null || !p.isAlive() || p.level() != k.level() || p.isSpectator()) {
                k.discard();
                return;
            }
            if (k.tickCount % 40 == 0 && !LEAVING.contains(k.getId())
                    && !lvl(k).getBiome(p.blockPosition()).is(ModWorldgen.COLONIA_DO_JOGO_DO_ABATE)) {
                // out of the Colônia: it says goodbye
                OF.remove(p.getUUID(), k.getId());
                if (k.attacking()) {
                    SubNet.send(lvl(k), SubNet.POOF, 0, k.position().add(0, 0.3, 0), Vec3.ZERO, 0.5f, 0, 0);
                    k.discard();
                    return;
                }
                LEAVING.add(k.getId());
                say(k, "jj.kogane.tchau", 60);
                play(k, "some");
                return;
            }
            Vec3 want = spot(p);
            double d = k.position().distanceTo(want);
            if (d > 14 && !k.attacking()) {
                k.teleportTo(want.x, want.y, want.z);
            } else if (d > 0.4) {
                k.getMoveControl().setWantedPosition(want.x, want.y, want.z, Math.min(2.2, 0.9 + d * 0.25));
            }
            k.getLookControl().setLookAt(p, 30f, 30f);
        }
    };
}
