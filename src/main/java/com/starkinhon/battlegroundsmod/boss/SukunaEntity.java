package com.starkinhon.battlegroundsmod.boss;

import com.starkinhon.battlegroundsmod.ModEntities;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Fx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

// Ryomen Sukuna, the King of Curses boss (Stark, 2026-11-09): tier 4, 300 life, the same level as the Gojo boss. In
// Yuji's body (no Heian form: Stark, "o Sukuna Heian é completamente bizarro" next to Gojo). First half playing, the
// second half serious with the whole kit (SukunaTactics; tuning in souls_bosses/sukuna.json).
// The Itadori mob can turn into him (takeOver): low on life, once, by chance, Sukuna takes the body.
public class SukunaEntity extends SoulBoss {
    // The Itadori's life (fraction) where Sukuna may take over, and the chance it happens (rolled once per Itadori).
    private static final float TAKE_OVER_LIFE = 0.3f;
    private static final double TAKE_OVER_CHANCE = 0.3;

    public SukunaEntity(EntityType<? extends SukunaEntity> type, Level level) {
        super(type, level);
    }

    private SukunaTactics tactics;

    @Override
    public BossTactics tactics() {
        if (tactics == null) {
            tactics = new SukunaTactics(this);
        }
        return tactics;
    }

    @Override
    public String bossId() {
        return "sukuna";
    }

    // Every tick of an Itadori mob: low on life, once, a chance Sukuna takes over the body. The Itadori is gone and the
    // Sukuna boss stands in its place, looking the same way, after the same target.
    public static void takeOver(SoulBoss itadori, ServerLevel level) {
        if (!itadori.isAlive() || itadori.getPersistentData().getBoolean("SukunaRolled")
                || itadori.getHealth() / itadori.getMaxHealth() > TAKE_OVER_LIFE) {
            return;
        }
        itadori.getPersistentData().putBoolean("SukunaRolled", true);
        if (itadori.getRandom().nextDouble() >= TAKE_OVER_CHANCE) {
            return;
        }
        SukunaEntity sukuna = ModEntities.SUKUNA.get().create(level);
        if (sukuna == null) {
            return;
        }
        LivingEntity target = itadori.getTarget();
        sukuna.moveTo(itadori.getX(), itadori.getY(), itadori.getZ(), itadori.getYRot(), itadori.getXRot());
        sukuna.setYHeadRot(itadori.getYHeadRot());
        sukuna.setYBodyRot(itadori.yBodyRot);
        sukuna.finalizeSpawn(level, level.getCurrentDifficultyAt(sukuna.blockPosition()), MobSpawnType.CONVERSION, null);
        if (itadori.isPersistenceRequired()) {
            sukuna.setPersistenceRequired();
        }
        // The marks come up through the skin: a heartbeat and a burst of the mod's dark red smoke where he stands.
        Fx.sound(level, Combat.center(itadori), SoundEvents.WARDEN_HEARTBEAT, 1.6f, 0.6f);
        BossFx.shockwave(level, itadori, 0x7A0E14, 4);
        itadori.discard();
        level.addFreshEntity(sukuna);
        if (target != null && target.isAlive()) {
            sukuna.setTarget(target);
        }
    }
}
