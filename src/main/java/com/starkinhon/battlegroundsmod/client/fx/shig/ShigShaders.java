package com.starkinhon.battlegroundsmod.client.fx.shig;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

// Shigaraki's shaders (assets/battlegroundsmod/shaders/core/souls_shig_*), the preview's GLSL. Each on its own: a problem
// in one only hides that effect, never the skill (the hits and the Decadência on the server stay the same).
public final class ShigShaders {
    static ShaderInstance tent, decay, hand, shell, drain, scream, mark, end, fiber, wound;

    private ShigShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        one(event, "souls_shig_tent", DefaultVertexFormat.POSITION, s -> tent = s);
        one(event, "souls_shig_decay", DefaultVertexFormat.NEW_ENTITY, s -> decay = s);
        one(event, "souls_shig_hand", DefaultVertexFormat.NEW_ENTITY, s -> hand = s);
        one(event, "souls_shig_shell", DefaultVertexFormat.NEW_ENTITY, s -> shell = s);
        one(event, "souls_shig_drain", DefaultVertexFormat.NEW_ENTITY, s -> drain = s);
        one(event, "souls_shig_scream", DefaultVertexFormat.NEW_ENTITY, s -> scream = s);
        one(event, "souls_shig_mark", DefaultVertexFormat.NEW_ENTITY, s -> mark = s);
        one(event, "souls_shig_end", DefaultVertexFormat.NEW_ENTITY, s -> end = s);
        one(event, "souls_shig_fiber", DefaultVertexFormat.NEW_ENTITY, s -> fiber = s);
        one(event, "souls_shig_wound", DefaultVertexFormat.NEW_ENTITY, s -> wound = s);
    }

    private static void one(RegisterShadersEvent event, String name, VertexFormat format, Consumer<ShaderInstance> sink) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, name), format),
                    sink);
        } catch (Exception e) {
            BattlegroundsMod.LOGGER.error("Could not load Shigaraki's shader {}", name, e);
        }
    }
}
