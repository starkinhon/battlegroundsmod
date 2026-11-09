package com.starkinhon.battlegroundsmod.domain;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.starkinhon.battlegroundsmod.ModEntities;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Sukuna's Malevolent Shrine (Fukuma Mizushi) as a placed model: the blocky shrine Stark approved in the web preview
// (forja/santuario/santuario-malevolente-model.html), baked to models/mesh/malevolent_shrine_<material>.bin and drawn by
// client/fx/MalevolentShrineRenderer. Only the look for now: no collision, no hitbox, no domain logic. The Sukuna power
// will spawn it when his Domain Expansion is made; until then /soul santuario places one in front of the player and
// /soul santuario remover clears them.
public class MalevolentShrine extends Entity {
    // The model: base about 15 x 15 blocks, mound out to +-12.5, horns up to about 24 blocks high.
    private static final double HALF = 13.5, HEIGHT = 25.0;

    public MalevolentShrine(EntityType<? extends MalevolentShrine> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // The entity itself is a small box at the shrine's centre; the model is huge, so culling uses the whole shrine.
    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(getX() - HALF, getY() - 1, getZ() - HALF, getX() + HALF, getY() + HEIGHT, getZ() + HALF);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    // /soul santuario [remover] (operators only).
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("santuario").requires(source -> source.hasPermission(2))
                .executes(context -> place(context.getSource()))
                .then(Commands.literal("remover").executes(context -> remove(context.getSource())));
    }

    // In front of the player, its mouth facing them, standing on their feet's height.
    private static int place(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        float yaw = source.getRotation().y;
        Vec3 look = Vec3.directionFromRotation(0, yaw);
        MalevolentShrine shrine = new MalevolentShrine(ModEntities.MALEVOLENT_SHRINE.get(), level);
        Vec3 pos = at.add(look.scale(22));
        shrine.moveTo(pos.x, at.y, pos.z, yaw + 180f, 0f);
        level.addFreshEntity(shrine);
        source.sendSuccess(() -> Component.literal("Santuário Malevolente colocado."), true);
        return 1;
    }

    private static int remove(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        var found = level.getEntitiesOfClass(MalevolentShrine.class, new AABB(at, at).inflate(96));
        found.forEach(Entity::discard);
        int n = found.size();
        source.sendSuccess(() -> Component.literal(n + " santuário(s) removido(s)."), true);
        return n;
    }
}
