package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.mojang.blaze3d.platform.InputConstants;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

// Makima's C held (forja/makima-fantoche): a wheel of the mobs she keeps, each one shown as itself. The mouse marks one,
// a click picks it (as many as there is room for), letting go of C sends the ones picked (or the one under the mouse).
public final class MakimaWheel extends Screen {
    private static final int MAX_OUT = 3;
    private final List<String> names = new ArrayList<>();
    private final List<String> types = new ArrayList<>();
    private final Map<Integer, LivingEntity> bodies = new HashMap<>();
    private final TreeSet<Integer> picked = new TreeSet<>();
    private final int room;
    private int hover = -1;
    private int age;
    private boolean sent;

    MakimaWheel(int out, List<String> entries) {
        super(Component.translatable("gui.battlegroundsmod.makima.fantoches"));
        this.room = Math.max(0, MAX_OUT - out);
        for (String e : entries) {
            int bar = e.lastIndexOf('|');
            names.add(bar < 0 ? e : e.substring(0, bar));
            types.add(bar < 0 ? "" : e.substring(bar + 1));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
        // Letting go of C sends them (the key is read from the window: a screen eats the key's own release).
        var key = Keybinds.ABILITIES.get(2).getKey();
        boolean down = key.getType() == InputConstants.Type.MOUSE
                ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), key.getValue()) == 1
                : InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
        if (age > 2 && !down) {
            send(true);
        }
    }

    private void send(boolean withHover) {
        if (sent) {
            return;
        }
        sent = true;
        if (withHover && picked.isEmpty() && hover >= 0) {
            picked.add(hover);
        }
        // Highest first: the server takes each one out of the list as it rises, so the lower ones keep their place.
        for (int i : picked.descendingSet()) {
            PacketDistributor.sendToServer(new ModPayloads.MkPick(i));
        }
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hover >= 0) {
            if (!picked.remove(hover) && picked.size() < room) {
                picked.add(hover);
            }
            if (picked.size() >= room) {
                send(false);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            sent = true;
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        // No blur, no dark: the world goes on behind the wheel.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        int n = names.size();
        float cx = width / 2f, cy = height / 2f;
        float k = Math.min(1, (age + partial) / 3f);
        float pop = 0.85f + 0.15f * (1 - (1 - k) * (1 - k));
        float r = Math.min(height * 0.36f, 130) * pop;
        // The disk: lighter in the middle, darker ring where the bodies stand, a soft edge.
        for (int y = (int) -r; y <= r; y++) {
            float hw = (float) Math.sqrt(Math.max(0, r * r - y * y));
            for (int band = 0; band < 3; band++) {
                float from = band == 0 ? 0 : band == 1 ? 0.35f : 0.70f, to = band == 0 ? 0.35f : band == 1 ? 0.70f : 1f;
                int alpha = band == 0 ? 0x5A : band == 1 ? 0x99 : 0x40;
                float in = r * from, out = r * to;
                if (Math.abs(y) > out) {
                    continue;
                }
                float wo = (float) Math.sqrt(Math.max(0, out * out - y * y));
                float wi = Math.abs(y) < in ? (float) Math.sqrt(in * in - y * y) : 0;
                int a = (int) (alpha * k) << 24;
                graphics.fill((int) (cx - wo), (int) (cy + y), (int) (cx - wi), (int) (cy + y + 1), a);
                graphics.fill((int) (cx + wi), (int) (cy + y), (int) (cx + wo), (int) (cy + y + 1), a);
            }
        }
        // Which one the mouse points at.
        double dx = mouseX - cx, dy = mouseY - cy;
        hover = -1;
        if (n > 0 && dx * dx + dy * dy > (r * 0.18) * (r * 0.18)) {
            double an = Math.atan2(dy, dx) + Math.PI / 2;
            an = (an % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
            hover = (int) Math.round(an / (Math.PI * 2) * n) % n;
        }
        float ring = r * 0.68f;
        int box = (int) Math.max(34, Math.min(62, r * 2 * Math.PI / Math.max(n, 3) * 0.62));
        for (int i = 0; i < n; i++) {
            double an = -Math.PI / 2 + i * 2 * Math.PI / n;
            int x = (int) (cx + Math.cos(an) * ring), y = (int) (cy + Math.sin(an) * ring);
            boolean hov = i == hover, pick = picked.contains(i);
            int x0 = x - box / 2, y0 = y - box / 2 - 4, x1 = x + box / 2, y1 = y + box / 2 + 8;
            if (pick) {
                graphics.fill(x0, y0, x1, y1, ((int) (0x8C * k) << 24) | 0xA3122A);
            }
            if (hov || pick) {
                int c = (int) (255 * k) << 24 | (hov ? 0xF4DC6C : 0xA3122A);
                graphics.fill(x0, y0, x1, y0 + 2, c);
                graphics.fill(x0, y1 - 2, x1, y1, c);
                graphics.fill(x0, y0, x0 + 2, y1, c);
                graphics.fill(x1 - 2, y0, x1, y1, c);
            }
            LivingEntity body = body(i);
            if (body != null) {
                float h = Math.max(body.getBbHeight(), body.getBbWidth() * 0.8f);
                int scale = (int) Math.max(4, (box - 14) / Math.max(0.6f, h));
                graphics.enableScissor(x0 + 2, y0 + 2, x1 - 2, y1 - 12);
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x0 + 2, y0 + 2, x1 - 2, y1 - 12, scale, 0.0625f,
                        mouseX, mouseY, body);
                graphics.disableScissor();
            }
            String name = font.plainSubstrByWidth(names.get(i), box + 22);
            graphics.drawString(font, name, x - font.width(name) / 2, y1 - 10, ((int) (255 * k) << 24) | 0xFFFFFF, true);
        }
        Component mid = n == 0 ? Component.translatable("gui.battlegroundsmod.makima.fantoches_vazio") : title;
        graphics.drawString(font, mid, (int) (cx - font.width(mid) / 2f), (int) (cy - 4), ((int) (255 * k) << 24) | 0xFFD6DC, true);
        if (room > 1) {
            Component tip = Component.translatable("gui.battlegroundsmod.makima.fantoches_dica", picked.size(), room);
            graphics.drawString(font, tip, (int) (cx - font.width(tip) / 2f), (int) (cy + 7), ((int) (200 * k) << 24) | 0xC9A5AC, true);
        }
    }

    private LivingEntity body(int i) {
        if (bodies.containsKey(i)) {
            return bodies.get(i);
        }
        LivingEntity made = null;
        Minecraft mc = Minecraft.getInstance();
        EntityType<?> type = EntityType.byString(types.get(i)).orElse(null);
        if (type != null && mc.level != null) {
            try {
                Entity e = type.create(mc.level);
                if (e instanceof LivingEntity le) {
                    made = le;
                }
            } catch (RuntimeException ignored) {
                // A body that can't be made here shows only its name.
            }
        }
        bodies.put(i, made);
        return made;
    }
}
