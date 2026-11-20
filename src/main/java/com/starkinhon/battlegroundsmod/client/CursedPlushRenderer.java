// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.item.CursedPlush;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// Yaga's cursed plush: the Boneco Amaldiçoado's own item model (models/item/boneco_amaldicoado_3d), drawn at toy size and
// animated as a whole body. Waddles when it walks; the Investida crouches and leans into the ram, the Barragem twists
// left and right fast with little jabs, the Empurrão winds back and throws its weight forward.
public class CursedPlushRenderer extends EntityRenderer<CursedPlush> {
    // The model is 14.9 px tall (0.93 block) with its feet at y 0.69 px: 1.35x makes a 1.25 block toy.
    private static final float SCALE = 1.35f;
    private static final float FEET = 0.5f - 0.694f / 16f;
    private final ItemRenderer items;
    private ItemStack stack;

    public CursedPlushRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.45f;
    }

    @Override
    public void render(CursedPlush plush, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (stack == null) {
            stack = new ItemStack(ModItems.CURSED_DOLL.get());
        }
        pose.pushPose();
        float body = Mth.rotLerp(partial, plush.yBodyRotO, plush.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180f - body));

        // Pivot at the feet: lean (X, + is forward), twist (Y), roll (Z), squash and slide.
        float lean = 0, twist = 0, roll = 0, squash = 1, slide = 0;
        float walkPos = plush.walkAnimation.position(partial);
        float walkSpeed = Math.min(1f, plush.walkAnimation.speed(partial));
        roll += Mth.sin(walkPos * 0.7f) * 10f * walkSpeed;
        float bob = Math.abs(Mth.sin(walkPos * 0.7f)) * 0.06f * walkSpeed;
        float t = plush.stateAge() + partial;
        switch (plush.state()) {
            case CursedPlush.DASH -> {
                if (t < CursedPlush.DASH_GO) {
                    float k = t / CursedPlush.DASH_GO;
                    lean = -8f * k;
                    squash = 1f - 0.18f * k;
                } else {
                    lean = 32f;
                    squash = 1.05f;
                }
            }
            case CursedPlush.BARRAGE -> {
                float side = Mth.sin(t * Mth.PI);
                twist = side * 18f;
                roll += side * 5f;
                lean = 12f;
                slide = Math.abs(Mth.cos(t * Mth.PI)) * 0.12f;
            }
            case CursedPlush.SHOVE -> {
                if (t < CursedPlush.SHOVE_HIT) {
                    float k = t / CursedPlush.SHOVE_HIT;
                    lean = -18f * k;
                    twist = -25f * k;
                    squash = 1f - 0.1f * k;
                } else {
                    float k = Math.min(1f, (t - CursedPlush.SHOVE_HIT) / 2f);
                    float back = Math.max(0f, (t - CursedPlush.SHOVE_HIT - 3f) / (CursedPlush.SHOVE_LEN - CursedPlush.SHOVE_HIT - 3f));
                    lean = Mth.lerp(back, 28f * k, 0f);
                    twist = Mth.lerp(k, -25f, 15f) * (1f - back);
                    slide = 0.25f * k * (1f - back);
                }
            }
            default -> {
            }
        }
        pose.translate(0, bob, slide);
        pose.mulPose(Axis.YP.rotationDegrees(twist));
        pose.mulPose(Axis.XP.rotationDegrees(lean));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        float wide = 1f + (1f - squash) * 0.5f;
        pose.scale(SCALE * wide, SCALE * squash, SCALE * wide);
        pose.translate(0, FEET, 0);
        int overlay = LivingEntityRenderer.getOverlayCoords(plush, 0f);
        items.renderStatic(stack, ItemDisplayContext.NONE, light, overlay, pose, buffers, plush.level(), plush.getId());
        pose.popPose();
        super.render(plush, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CursedPlush plush) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
