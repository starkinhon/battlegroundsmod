package com.starkinhon.battlegroundsmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.client.fx.FxConfig;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// The hotbar in the same crystal look as the inventory (CrystalInventory, Stark 2026-11-19): a dark chamfered plate in
// the soul's color, crystal clusters growing from behind both ends, dark (or light) glass slots, the selected slot lit,
// and the same glowing outline around each item. Follows the inventory's style buttons: "Minecraft" keeps vanilla's
// hotbar. Also left to vanilla in spectator mode and when the attack indicator is set to show on the hotbar. Anything
// that fails turns it off and vanilla's hotbar comes back.
public final class CrystalHotbar {
    // Room around the 182x22 hotbar for the crystals.
    private static final int PAD_X = 44;
    private static final int PAD_Y = 40;
    private static final int W = 182 + PAD_X * 2;
    private static final int H = PAD_Y + 22 + 4;

    private static boolean broken;
    private static String builtFor;
    private static int[] ramp;
    private static float hue;
    private static ResourceLocation tex;

    private CrystalHotbar() {
    }

    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (broken || !event.getName().equals(VanillaGuiLayers.HOTBAR) || FxConfig.inventoryStyle() == 2) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR
                || mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            return;
        }
        try {
            String soul = CrystalInventory.soulOf(player);
            if (soul.isEmpty()) {
                // No soul (Humano): Minecraft's own hotbar.
                return;
            }
            if (!soul.equals(builtFor)) {
                build(soul);
            }
            draw(event.getGuiGraphics(), player, event.getPartialTick().getGameTimeDeltaPartialTick(true));
            event.setCanceled(true);
        } catch (Exception e) {
            broken = true;
            builtFor = null;
            BattlegroundsMod.LOGGER.error("Crystal hotbar failed, going back to the vanilla one", e);
        }
    }

    private static void draw(GuiGraphics g, Player player, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int[] R = ramp;
        int hx = g.guiWidth() / 2 - 91;
        int hy = g.guiHeight() - 22;

        RenderSystem.enableBlend();
        g.blit(tex, hx - PAD_X, hy - PAD_Y, 0, 0, W, H, W, H);

        boolean light = FxConfig.inventoryStyle() == 1;
        int glass = light ? CrystalInventory.hsl(hue, 0.1f, 0.6f) : CrystalInventory.hsl(hue, 0.12f, 0.17f);
        int selected = player.getInventory().selected;
        for (int i = 0; i < 9; i++) {
            int x = hx + 3 + i * 20;
            int y = hy + 3;
            CrystalInventory.slotFrame(g, x, y, 16, R, glass, light);
            if (i == selected) {
                // The selected slot: a bright rim and a soft glow inside.
                g.fill(x - 3, y - 3, x + 19, y - 2, R[6]);
                g.fill(x - 3, y + 18, x + 19, y + 19, R[6]);
                g.fill(x - 3, y - 2, x - 2, y + 18, R[6]);
                g.fill(x + 18, y - 2, x + 19, y + 18, R[6]);
                g.fill(x, y, x + 16, y + 16, CrystalInventory.argb(R[4], 0.28f));
            }
        }
        ItemStack offhand = player.getOffhandItem();
        int offX = 0;
        if (!offhand.isEmpty()) {
            offX = player.getMainArm().getOpposite() == HumanoidArm.LEFT ? hx - 26 : hx + 192;
            CrystalInventory.slotFrame(g, offX, hy + 3, 16, R, glass, light);
        }

        // The items (with vanilla's pop when one is picked up). No outline: the Escuro/Claro/Minecraft styles cover
        // readability now (Stark 2026-11-19).
        for (int i = 0; i < 10; i++) {
            ItemStack stack = i < 9 ? player.getInventory().items.get(i) : offhand;
            if (stack.isEmpty()) {
                continue;
            }
            int x = i < 9 ? hx + 3 + i * 20 : offX;
            int y = hy + 3;
            item(g, mc, player, stack, x, y, i + 1, partialTick);
        }
    }

    private static void item(GuiGraphics g, Minecraft mc, Player player, ItemStack stack, int x, int y, int seed, float partialTick) {
        float pop = stack.getPopTime() - partialTick;
        PoseStack pose = g.pose();
        if (pop > 0) {
            float f = 1 + pop / 5;
            pose.pushPose();
            pose.translate(x + 8, y + 12, 0);
            pose.scale(1 / f, (f + 1) / 2, 1);
            pose.translate(-(x + 8), -(y + 12), 0);
        }
        g.renderItem(player, stack, x, y, seed);
        if (pop > 0) {
            pose.popPose();
        }
        g.renderItemDecorations(mc.font, stack, x, y);
    }

    // The plate and the crystal clusters at both ends, built once per soul.
    private static void build(String soul) {
        hue = CrystalInventory.hueFor(soul);
        ramp = CrystalInventory.rampFor(soul);
        int[] R = ramp;
        CrystalInventory.cw = W;
        CrystalInventory.ch = H;
        int[] buf = new int[W * H];
        int hx = PAD_X;
        int hy = PAD_Y;
        double mirror = W / 2.0 - 0.5;
        // x, y, length, half width, angle, apex
        double[][] ends = { { hx + 10, hy + 14, 40, 9, -52, 0 }, { hx + 14, hy + 10, 30, 7, -20, 0.4 }, { hx + 10, hy + 16, 30, 7, -92, 0.3 },
                { hx + 12, hy + 20, 28, 8, -130, 0 }, { hx + 24, hy + 20, 18, 6, -160, 0 } };
        // The first look (Stark 2026-11-19: "esse jeito aqui tava tão lindo"): the lower crystals run off the screen.
        CrystalInventory.fitToPicture = false;
        try {
            for (double[] e : ends) {
                CrystalInventory.crystal(buf, R, e[0], e[1], e[2], e[3], e[4], 0.32, e[5]);
                CrystalInventory.crystal(buf, R, 2 * mirror - e[0], e[1], e[2], e[3], -e[4], 0.32, -e[5]);
            }
        } finally {
            CrystalInventory.fitToPicture = true;
        }
        plate(buf, R, hx - 3, hy - 2, 188, 25);

        NativeImage image = new NativeImage(W, H, true);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int c = buf[y * W + x];
                if (c != 0) {
                    image.setPixelRGBA(x, y, CrystalInventory.abgr(c));
                }
            }
        }
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "dyn/crystal_hotbar");
        // register() closes the texture it replaces.
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(image));
        tex = loc;
        builtFor = soul;
    }

    // Chamfered plate: dark edge, a light line on the top/left inside it, faint diagonal grain.
    private static void plate(int[] buf, int[] R, int x, int y, int w, int h) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int cut = Math.min(Math.min(i, j), Math.min(w - 1 - i, h - 1 - j));
                int corner = Math.min(i, w - 1 - i) + Math.min(j, h - 1 - j);
                if (corner < 3) {
                    continue;
                }
                int c = R[2];
                if (cut == 0 || corner == 3) {
                    c = R[0];
                } else if (cut == 1) {
                    c = (j == 1 || i == 1) && !(j == h - 2 || i == w - 2) ? R[4] : R[1];
                }
                int px = x + i;
                int py = y + j;
                if (px >= 0 && py >= 0 && px < W && py < H) {
                    buf[py * W + px] = c;
                }
            }
        }
    }
}
