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

// Makima's C held (forja/makima-fantoche): her control panel. The outer ring holds the mobs she keeps, each one shown as
// itself: a click picks one (as many as there is room for), letting go of C sends the ones picked (or the one under the
// mouse). The inner ring holds the ones out, her mobs and the souls of her V (gold): a click calls one back into her
// shadow, the right button opens its orders (attack her target, follow her, hold its spot, fight freely, call back, let
// go). The middle: a click calls them all back, the right button sends them all at her target.
public final class MakimaWheel extends Screen {
    private static final int MAX_OUT = 3;
    private static final int MAX_STOCK = 9;
    // Orders (power/powers/makima/Puppets: A_*), and the state each one leaves (Puppets: FREE, ATTACK, FOLLOW, STAY).
    private static final int A_BACK = 0;
    private static final int A_ATTACK = 1;
    private static final int A_FOLLOW = 2;
    private static final int A_STAY = 3;
    private static final int A_FREE = 4;
    private static final int A_LET_GO = 5;
    private static final int A_ALL_BACK = 6;
    private static final int A_ALL_ATTACK = 7;
    private static final int[] MENU = {A_ATTACK, A_FOLLOW, A_STAY, A_FREE, A_BACK, A_LET_GO};
    private static final int RED = 0xA3122A;
    private static final int GOLD = 0xE2B84E;
    private static final int HOVER = 0xF4DC6C;

    private static final class Out {
        final int id;
        final int kind;
        int order;
        final String type;
        final String name;

        Out(int id, int kind, int order, String type, String name) {
            this.id = id;
            this.kind = kind;
            this.order = order;
            this.type = type;
            this.name = name;
        }
    }

    private final List<String> names = new ArrayList<>();
    private final List<String> types = new ArrayList<>();
    private final List<Out> outs = new ArrayList<>();
    private final Map<Integer, LivingEntity> bodies = new HashMap<>();
    private final Map<String, LivingEntity> outBodies = new HashMap<>();
    private final TreeSet<Integer> picked = new TreeSet<>();
    // What the mouse is on: a kept one (hover), one out (hoverOut), the middle (hoverMid), a line of the open menu.
    private int hover = -1;
    private int hoverOut = -1;
    private boolean hoverMid;
    private int hoverRow = -1;
    // The orders menu of one out (its id, -1 closed) and where it opens.
    private int menuId = -1;
    private int menuX;
    private int menuY;
    private int age;
    private boolean sent;

    MakimaWheel(int out, List<String> entries) {
        super(Component.translatable("gui.battlegroundsmod.makima.fantoches"));
        for (String e : entries) {
            if (e.startsWith("@")) {
                String[] p = e.substring(1).split("\\|", 5);
                if (p.length == 5) {
                    try {
                        outs.add(new Out(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3], p[4]));
                    } catch (NumberFormatException ignored) {
                        // A line that isn't one of hers.
                    }
                }
                continue;
            }
            int bar = e.lastIndexOf('|');
            names.add(bar < 0 ? e : e.substring(0, bar));
            types.add(bar < 0 ? "" : e.substring(bar + 1));
        }
    }

    // Room for more mobs out (souls don't count).
    private int room() {
        int mobs = 0;
        for (Out o : outs) {
            if (o.kind == 0) {
                mobs++;
            }
        }
        return Math.max(0, MAX_OUT - mobs);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
        // Letting go of C sends them (the key is read from the window: a screen eats the key's own release). With an
        // orders menu open it waits for the choice.
        var key = Keybinds.ABILITIES.get(2).getKey();
        boolean down = key.getType() == InputConstants.Type.MOUSE
                ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), key.getValue()) == 1
                : InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
        if (age > 2 && !down && menuId < 0) {
            send(true);
        }
    }

    private void send(boolean withHover) {
        if (sent) {
            return;
        }
        sent = true;
        if (withHover && picked.isEmpty() && hover >= 0 && room() > 0) {
            picked.add(hover);
        }
        // Highest first: the server takes each one out of the list as it rises, so the lower ones keep their place.
        for (int i : picked.descendingSet()) {
            PacketDistributor.sendToServer(new ModPayloads.MkPick(i));
        }
        onClose();
    }

    private void order(int action, int id) {
        PacketDistributor.sendToServer(new ModPayloads.MkOrder(action, id));
    }

    // Called back into her shadow: it leaves the inner ring and (a mob) joins the kept ones at the end, as on the server.
    private void callBack(Out o) {
        outs.remove(o);
        if (o.kind == 0 && names.size() < MAX_STOCK) {
            names.add(o.name);
            types.add(o.type);
        }
    }

    private Out out(int id) {
        for (Out o : outs) {
            if (o.id == id) {
                return o;
            }
        }
        return null;
    }

    private int[] menuRows(Out o) {
        // A soul is hers for good: it can't be let go.
        return o.kind == 0 ? MENU : java.util.Arrays.copyOf(MENU, MENU.length - 1);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menuId >= 0) {
            Out o = out(menuId);
            if (o != null && hoverRow >= 0) {
                int action = menuRows(o)[hoverRow];
                order(action, o.id);
                if (action == A_BACK || action == A_LET_GO) {
                    if (action == A_BACK) {
                        callBack(o);
                    } else {
                        outs.remove(o);
                    }
                } else {
                    o.order = action == A_ATTACK ? 1 : action == A_FOLLOW ? 2 : action == A_STAY ? 3 : 0;
                }
            }
            // A click anywhere closes the menu.
            menuId = -1;
            hoverRow = -1;
            return true;
        }
        boolean right = button == 1;
        if (hoverMid && !outs.isEmpty()) {
            if (right) {
                order(A_ALL_ATTACK, -1);
                for (Out o : outs) {
                    o.order = 1;
                }
            } else {
                order(A_ALL_BACK, -1);
                for (Out o : new ArrayList<>(outs)) {
                    callBack(o);
                }
            }
            return true;
        }
        if (hoverOut >= 0 && hoverOut < outs.size()) {
            Out o = outs.get(hoverOut);
            if (right) {
                menuId = o.id;
                menuX = (int) mx + 6;
                menuY = (int) my - 4;
            } else {
                order(A_BACK, o.id);
                callBack(o);
            }
            return true;
        }
        if (hover >= 0 && !right) {
            int room = room();
            if (!picked.remove(hover) && picked.size() < room) {
                picked.add(hover);
            }
            if (room > 0 && picked.size() >= room) {
                send(false);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (menuId >= 0) {
                menuId = -1;
                return true;
            }
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
        int no = outs.size();
        float cx = width / 2f, cy = height / 2f;
        float k = Math.min(1, (age + partial) / 3f);
        float pop = 0.85f + 0.15f * (1 - (1 - k) * (1 - k));
        float r = Math.min(height * (no > 0 ? 0.40f : 0.36f), no > 0 ? 145 : 130) * pop;
        float mid = r * 0.17f, split = r * 0.55f;
        // The disk: lighter in the middle, darker rings where the bodies stand, a soft edge.
        float[][] bands = no > 0
                ? new float[][] {{0, 0.17f, 0x70}, {0.17f, 0.55f, 0x9E}, {0.55f, 0.57f, 0x30}, {0.57f, 1f, 0x80}}
                : new float[][] {{0, 0.35f, 0x5A}, {0.35f, 0.70f, 0x99}, {0.70f, 1f, 0x40}};
        for (int y = (int) -r; y <= r; y++) {
            for (float[] band : bands) {
                float in = r * band[0], out = r * band[1];
                if (Math.abs(y) > out) {
                    continue;
                }
                float wo = (float) Math.sqrt(Math.max(0, out * out - y * y));
                float wi = Math.abs(y) < in ? (float) Math.sqrt(in * in - y * y) : 0;
                int a = (int) (band[2] * k) << 24;
                graphics.fill((int) (cx - wo), (int) (cy + y), (int) (cx - wi), (int) (cy + y + 1), a);
                graphics.fill((int) (cx + wi), (int) (cy + y), (int) (cx + wo), (int) (cy + y + 1), a);
            }
        }
        // What the mouse points at (nothing new while an orders menu is open).
        double dx = mouseX - cx, dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double an = Math.atan2(dy, dx) + Math.PI / 2;
        an = (an % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        if (menuId < 0) {
            hover = -1;
            hoverOut = -1;
            hoverMid = false;
            if (no > 0 && dist <= mid) {
                hoverMid = true;
            } else if (no > 0 && dist < split) {
                hoverOut = (int) Math.round(an / (Math.PI * 2) * no) % no;
            } else if (n > 0 && dist > (no > 0 ? split : r * 0.18)) {
                hover = (int) Math.round(an / (Math.PI * 2) * n) % n;
            }
        }
        // The kept ones.
        float ring = r * (no > 0 ? 0.78f : 0.68f);
        int box = (int) Math.max(30, Math.min(62, ring * 2 * Math.PI / Math.max(n, 3) * 0.62));
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / n;
            int x = (int) (cx + Math.cos(a) * ring), y = (int) (cy + Math.sin(a) * ring);
            boolean hov = i == hover, pick = picked.contains(i);
            int x0 = x - box / 2, y0 = y - box / 2 - 4, x1 = x + box / 2, y1 = y + box / 2 + 8;
            if (pick) {
                graphics.fill(x0, y0, x1, y1, ((int) (0x8C * k) << 24) | RED);
            }
            if (hov || pick) {
                frame(graphics, x0, y0, x1, y1, 2, (int) (255 * k) << 24 | (hov ? HOVER : RED));
            }
            LivingEntity body = body(i);
            if (body != null) {
                drawBody(graphics, body, x0 + 2, y0 + 2, x1 - 2, y1 - 12, box - 14, mouseX, mouseY);
            }
            String name = font.plainSubstrByWidth(names.get(i), box + 22);
            graphics.drawString(font, name, x - font.width(name) / 2, y1 - 10, ((int) (255 * k) << 24) | 0xFFFFFF, true);
        }
        // The ones out: their own bodies, a thin chain-red (mob) or gold (soul) frame, what they were told under them.
        float inner = r * 0.36f;
        int ob = (int) Math.max(24, Math.min(40, inner * 2 * Math.PI / Math.max(no, 3) * 0.6));
        for (int i = 0; i < no; i++) {
            Out o = outs.get(i);
            double a = -Math.PI / 2 + i * 2 * Math.PI / no;
            int x = (int) (cx + Math.cos(a) * inner), y = (int) (cy + Math.sin(a) * inner);
            boolean hov = i == hoverOut || o.id == menuId;
            int x0 = x - ob / 2, y0 = y - ob / 2 - 3, x1 = x + ob / 2, y1 = y + ob / 2 + 3;
            int tone = o.kind == 1 ? GOLD : RED;
            graphics.fill(x0, y0, x1, y1, ((int) (0x55 * k) << 24) | (o.kind == 1 ? 0x3A2A08 : 0x2A0508));
            frame(graphics, x0, y0, x1, y1, hov ? 2 : 1, (int) (255 * k) << 24 | (hov ? HOVER : tone));
            LivingEntity body = outBody(o);
            if (body != null) {
                drawBody(graphics, body, x0 + 1, y0 + 1, x1 - 1, y1 - 1, ob - 6, mouseX, mouseY);
            }
            Component st = Component.translatable("gui.battlegroundsmod.makima.estado." + Math.max(0, Math.min(3, o.order)));
            small(graphics, st, x, y1 + 1, ((int) (230 * k) << 24) | (o.order == 1 ? 0xFF8A8A : tone == GOLD ? 0xF2E2C6 : 0xD9B8BE));
        }
        // The middle.
        if (no > 0) {
            int c = ((int) (255 * k) << 24) | (hoverMid ? HOVER : 0xFFD6DC);
            Component l1 = Component.translatable("gui.battlegroundsmod.makima.recolher");
            Component l2 = Component.translatable("gui.battlegroundsmod.makima.todos");
            graphics.drawString(font, l1, (int) (cx - font.width(l1) / 2f), (int) (cy - 9), c, true);
            graphics.drawString(font, l2, (int) (cx - font.width(l2) / 2f), (int) (cy + 1), c, true);
        } else {
            Component m = n == 0 ? Component.translatable("gui.battlegroundsmod.makima.fantoches_vazio") : title;
            graphics.drawString(font, m, (int) (cx - font.width(m) / 2f), (int) (cy - 4), ((int) (255 * k) << 24) | 0xFFD6DC, true);
            if (room() > 1) {
                Component tip = Component.translatable("gui.battlegroundsmod.makima.fantoches_dica", picked.size(), room());
                graphics.drawString(font, tip, (int) (cx - font.width(tip) / 2f), (int) (cy + 7), ((int) (200 * k) << 24) | 0xC9A5AC, true);
            }
        }
        // Under the disk: who the mouse is on and what a click does.
        if (no > 0) {
            Component what;
            Component tip;
            if (hoverOut >= 0 && hoverOut < no) {
                Out o = outs.get(hoverOut);
                what = Component.literal(o.name);
                tip = Component.translatable("gui.battlegroundsmod.makima.dica_fora");
            } else if (hoverMid) {
                what = Component.translatable("gui.battlegroundsmod.makima.todos_fora", no);
                tip = Component.translatable("gui.battlegroundsmod.makima.dica_meio");
            } else if (hover >= 0) {
                what = Component.literal(names.get(hover));
                tip = room() > 0 ? Component.translatable("gui.battlegroundsmod.makima.fantoches_dica", picked.size(), room())
                        : Component.translatable("gui.battlegroundsmod.makima.sem_espaco");
            } else {
                what = n == 0 ? Component.translatable("gui.battlegroundsmod.makima.fantoches_vazio") : title;
                tip = Component.translatable("gui.battlegroundsmod.makima.dica_geral");
            }
            int by = (int) (cy + r + 6);
            graphics.drawString(font, what, (int) (cx - font.width(what) / 2f), by, ((int) (255 * k) << 24) | 0xFFFFFF, true);
            graphics.drawString(font, tip, (int) (cx - font.width(tip) / 2f), by + 11, ((int) (210 * k) << 24) | 0xC9A5AC, true);
        }
        // The orders menu of one out.
        if (menuId >= 0) {
            Out o = out(menuId);
            if (o == null) {
                menuId = -1;
            } else {
                int[] rows = menuRows(o);
                int w = 0;
                for (int action : rows) {
                    w = Math.max(w, font.width(Component.translatable("gui.battlegroundsmod.makima.ordem." + action)));
                }
                w += 12;
                int h = rows.length * 12 + 4;
                int x0 = Math.min(menuX, width - w - 2), y0 = Math.max(2, Math.min(menuY, height - h - 2));
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 400);
                graphics.fill(x0, y0, x0 + w, y0 + h, 0xE6140608);
                frame(graphics, x0, y0, x0 + w, y0 + h, 1, 0xFF000000 | (o.kind == 1 ? GOLD : RED));
                hoverRow = -1;
                for (int i = 0; i < rows.length; i++) {
                    int ry = y0 + 2 + i * 12;
                    boolean on = mouseX >= x0 && mouseX < x0 + w && mouseY >= ry && mouseY < ry + 12;
                    if (on) {
                        hoverRow = i;
                        graphics.fill(x0 + 1, ry, x0 + w - 1, ry + 12, 0x80000000 | RED);
                    }
                    int col = rows[i] == A_LET_GO ? 0xFF8A8A : on ? HOVER : 0xFFE8DCDE;
                    graphics.drawString(font, Component.translatable("gui.battlegroundsmod.makima.ordem." + rows[i]), x0 + 6, ry + 2,
                            0xFF000000 | col, true);
                }
                graphics.pose().popPose();
            }
        }
    }

    private static void frame(GuiGraphics graphics, int x0, int y0, int x1, int y1, int t, int c) {
        graphics.fill(x0, y0, x1, y0 + t, c);
        graphics.fill(x0, y1 - t, x1, y1, c);
        graphics.fill(x0, y0, x0 + t, y1, c);
        graphics.fill(x1 - t, y0, x1, y1, c);
    }

    private void small(GuiGraphics graphics, Component text, int x, int y, int argb) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.75f, 0.75f, 1);
        graphics.drawString(font, text, -font.width(text) / 2, 0, argb, true);
        graphics.pose().popPose();
    }

    private static void drawBody(GuiGraphics graphics, LivingEntity body, int x0, int y0, int x1, int y1, int size, int mouseX, int mouseY) {
        float h = Math.max(body.getBbHeight(), body.getBbWidth() * 0.8f);
        int scale = (int) Math.max(4, size / Math.max(0.6f, h));
        graphics.enableScissor(x0, y0, x1, y1);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x0, y0, x1, y1, scale, 0.0625f, mouseX, mouseY, body);
        graphics.disableScissor();
    }

    // One out: its own body in the world when it is near enough to be loaded, else one made from its kind.
    private LivingEntity outBody(Out o) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(o.id) instanceof LivingEntity live && live.isAlive()) {
            return live;
        }
        if (outBodies.containsKey(o.type)) {
            return outBodies.get(o.type);
        }
        LivingEntity made = make(o.type);
        outBodies.put(o.type, made);
        return made;
    }

    private LivingEntity body(int i) {
        if (bodies.containsKey(i)) {
            return bodies.get(i);
        }
        LivingEntity made = make(types.get(i));
        bodies.put(i, made);
        return made;
    }

    private static LivingEntity make(String typeId) {
        Minecraft mc = Minecraft.getInstance();
        EntityType<?> type = EntityType.byString(typeId).orElse(null);
        if (type != null && mc.level != null) {
            try {
                Entity e = type.create(mc.level);
                if (e instanceof LivingEntity le) {
                    return le;
                }
            } catch (RuntimeException ignored) {
                // A body that can't be made here shows only its name.
            }
        }
        return null;
    }
}
