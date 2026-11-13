package com.starkinhon.battlegroundsmod.npc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// A friendly character of a universe (Stark 2026-11-10, "like DragonMineZ: NPCs teach you along the way"): it walks
// around its spot, looks at players, can't be hurt, and right click talks to it (Npcs.talk). Which NPC it is comes from
// its entity id: battlegroundsmod:npc_gohan -> gohan. Placed by Npcs.ensure near each universe's arrival point.
public class UniverseNpc extends PathfinderMob {
    public UniverseNpc(EntityType<? extends UniverseNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    public String npcId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath().replaceFirst("^npc_", "");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8f, 0.6f));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            getNavigation().stop();
            lookAt(player, 360, 360);
            Talks.open(server, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // In the talk scene (npc/Talks): stands still and looks at the player until the scene closes (or 30 s).
    private Player talker;
    private int talkUntil;

    void hold(Player player) {
        talker = player;
        talkUntil = tickCount + 600;
    }

    void release() {
        talker = null;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (talker == null) {
            return;
        }
        if (!talker.isAlive() || tickCount > talkUntil || distanceToSqr(talker) > 144) {
            talker = null;
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(talker, 30, 30);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
