package com.starkinhon.battlegroundsmod.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// Where an effect that follows an entity is this frame: a point on its body (seguir) and where it looks.
final class FxAnchor {
    private FxAnchor() {
    }

    static Entity entity(int id) {
        var level = Minecraft.getInstance().level;
        return id < 0 || level == null ? null : level.getEntity(id);
    }

    // null when nothing is followed (or the entity is gone): the effect stays where it started.
    static Vec3 point(int id, String seguir, float partial) {
        Entity entity = seguir == null ? null : entity(id);
        if (entity == null) {
            return null;
        }
        Vec3 feet = entity.getPosition(partial);
        return switch (seguir) {
            case "cabeca" -> entity.getEyePosition(partial);
            case "boca" -> entity.getEyePosition(partial).add(entity.getViewVector(partial).scale(0.32)).add(0, -0.1, 0);
            case "pes" -> feet;
            default -> feet.add(0, entity.getBbHeight() / 2, 0);
        };
    }

    static Vec3 look(int id, String seguir, float partial) {
        Entity entity = seguir == null ? null : entity(id);
        return entity == null ? null : entity.getViewVector(partial);
    }

    // True when this is the camera's own body seen from inside (first person).
    static boolean firstPersonSelf(int id) {
        Minecraft mc = Minecraft.getInstance();
        return id >= 0 && mc.getCameraEntity() != null && mc.getCameraEntity().getId() == id && mc.options.getCameraType().isFirstPerson();
    }
}
