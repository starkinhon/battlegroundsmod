package com.starkinhon.battlegroundsmod.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Powers;
import com.starkinhon.battlegroundsmod.power.Tiers;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

// The power's constellation (U): every skill as a star of a constellation that grows from the power's core, over a
// night sky tinted with the power's color. Hovering a star shows its name, clicking it (or the arrow keys) shows its
// card on the right: icon, name, key, tags, cooldown and what it does. Only for looking for now; the stars already know
// which skill they grow from (SkillTree), so an unlock system can lock them later.
public class ConstellationScreen extends Screen {
    private static final int ULT_COLOR = 0xF2C94C;
    private static final int TEXT = 0xFFE6E0EE;
    private static final int MUTED = 0xFF9A93A6;

    private record Star(float x, float y, float size, float phase, float speed, float depth, boolean cross) {
    }

    private final Power power;
    private final PowerData data;
    private final List<SkillTree.Node> nodes;
    private final List<Star> stars = new ArrayList<>();
    private final long openedAt = Util.getMillis();
    private long selectedAt = openedAt;
    private int selected;
    private int hovered = -1;

    // Layout, worked out each frame from the screen size.
    private int areaLeft;
    private int areaTop;
    private int areaRight;
    private int areaBottom;
    private int panelLeft;
    private int panelRight;
    private int nodeSize;

    public ConstellationScreen(Power power, PowerData data) {
        super(Component.translatable("screen.battlegroundsmod.skills"));
        this.power = power;
        this.data = data;
        this.nodes = SkillTree.build(power);
        Random random = new Random(power.id().hashCode());
        for (int i = 0; i < 190; i++) {
            float size = random.nextFloat() < 0.82f ? 1 : random.nextFloat() < 0.7f ? 1.5f : 2;
            stars.add(new Star(random.nextFloat(), random.nextFloat(), size, random.nextFloat() * Mth.TWO_PI,
                    0.6f + random.nextFloat() * 2.2f, 0.3f + random.nextFloat(), random.nextFloat() < 0.06f));
        }
    }

    // Opens the screen for the player's power. Does nothing without a power.
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        PowerData data = minecraft.player.getData(ModAttachments.POWER);
        Power power = Powers.get(data.power);
        if (power == null) {
            minecraft.player.displayClientMessage(Component.translatable("message.battlegroundsmod.no_power"), true);
            return;
        }
        SkillIcons.reload();
        minecraft.setScreen(new ConstellationScreen(power, data));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.4f, 0.45f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float time() {
        return (Util.getMillis() - openedAt) / 1000f;
    }

    private void layout() {
        int panelWidth = Mth.clamp(Math.round(width * 0.30f), 150, 220);
        panelRight = width - 12;
        panelLeft = panelRight - panelWidth;
        areaLeft = 14;
        areaRight = panelLeft - 14;
        areaTop = 50;
        areaBottom = height - 44;
        nodeSize = areaBottom - areaTop < 230 ? 22 : 28;
    }

    private float nodeX(SkillTree.Node node) {
        return pointX(node.x());
    }

    private float nodeY(SkillTree.Node node) {
        return pointY(node.y());
    }

    private float pointX(float x) {
        float pad = nodeSize * 0.8f;
        return areaLeft + pad + x * (areaRight - areaLeft - pad * 2);
    }

    private float pointY(float y) {
        float pad = nodeSize * 0.8f;
        return areaTop + pad + y * (areaBottom - areaTop - pad * 2);
    }

    private int depth(SkillTree.Node node) {
        return node.parent() == SkillTree.CORE ? 1 : 2;
    }

    private SkillTree.Node parentOf(SkillTree.Node node) {
        for (SkillTree.Node other : nodes) {
            if (other.slot() == node.parent()) {
                return other;
            }
        }
        return null;
    }

    private boolean unlocked(SkillTree.Node node) {
        return SkillTree.unlocked(data, power, node.slot());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Drawn in render: the sky replaces the blurred world.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout();
        float t = time();
        hovered = nodeAt(mouseX, mouseY);
        sky(graphics, mouseX, mouseY, t);
        links(graphics, t);
        core(graphics, t);
        for (int i = 0; i < nodes.size(); i++) {
            node(graphics, i, t);
        }
        if (hovered >= 0) {
            SkillTree.Node node = nodes.get(hovered);
            Component name = power.abilityName(node.slot());
            float x = nodeX(node);
            float y = nodeY(node) + nodeSize / 2f + 6;
            int w = font.width(name);
            graphics.fill(Math.round(x - w / 2f - 3), Math.round(y - 2), Math.round(x + w / 2f + 3), Math.round(y + 10), 0xC0050308);
            GuiDraw.centeredText(graphics, font, name, x, y, 1, 0xFFFFFFFF, true);
        }
        header(graphics, t);
        panel(graphics, t);
        footer(graphics);
    }

    // ---- Sky -------------------------------------------------------------------------------------------------------

    private void sky(GuiGraphics graphics, int mouseX, int mouseY, float t) {
        int color = power.color();
        boolean tsukuyomi = power.id().equals("uchiha_ghost");
        if (tsukuyomi) {
            graphics.fillGradient(0, 0, width, height, TsukuyomiSky.top(), TsukuyomiSky.bottom());
        } else {
            graphics.fillGradient(0, 0, width, height, 0xFF000000 | GuiDraw.mix(0x040308, color, 0.06f),
                    0xFF000000 | GuiDraw.mix(0x0A0610, color, 0.24f));
        }
        float fade = Mth.clamp(t / 0.35f, 0, 1);
        float cx = (areaLeft + areaRight) / 2f;

        if (tsukuyomi) {
            stars(graphics, mouseX, mouseY, t, fade * 0.45f, 0xFF4030);
            float coreY = pointY(SkillTree.CORE_Y);
            float radius = Math.min((areaRight - areaLeft) * 0.42f, (coreY - areaTop) * 0.5f);
            TsukuyomiSky.draw(graphics, width, height, cx, coreY - radius * 1.05f, radius, pointX(SkillTree.CORE_X), coreY, t, fade);
            return;
        }

        // Nebula: big soft clouds of the power's color drifting slowly.
        GuiDraw.glow(graphics, cx + Mth.sin(t * 0.07f) * 30, height * 0.45f, width * 0.42f, color, 0.16f * fade);
        GuiDraw.glow(graphics, width * 0.18f + Mth.cos(t * 0.05f) * 25, height * 0.25f, width * 0.28f, GuiDraw.mix(color, 0x2040FF, 0.35f), 0.10f * fade);
        GuiDraw.glow(graphics, width * 0.78f, height * 0.7f + Mth.sin(t * 0.06f) * 20, width * 0.32f, GuiDraw.mix(color, 0xFF3070, 0.3f), 0.09f * fade);
        GuiDraw.glow(graphics, pointX(SkillTree.CORE_X), pointY(SkillTree.CORE_Y), width * 0.22f, color, 0.22f * fade);

        stars(graphics, mouseX, mouseY, t, fade, color);
        drawPlanet(graphics, cx, color, fade);
    }

    // Stars, with a little parallax from the mouse.
    private void stars(GuiGraphics graphics, int mouseX, int mouseY, float t, float fade, int color) {
        float px = (mouseX - width / 2f) / width;
        float py = (mouseY - height / 2f) / height;
        for (Star star : stars) {
            float twinkle = 0.35f + 0.65f * (0.5f + 0.5f * Mth.sin(t * star.speed() + star.phase()));
            float x = star.x() * width - px * 10 * star.depth();
            float y = star.y() * height - py * 6 * star.depth();
            int alpha = Math.round(255 * twinkle * fade * (0.45f + 0.55f * star.depth() / 1.3f));
            int tint = GuiDraw.mix(0xFFFFFF, color, 0.25f * star.depth());
            if (star.cross()) {
                GuiDraw.star(graphics, x, y, 4 + 2 * twinkle, 0.6f, 0, GuiDraw.argb(alpha, tint));
            }
            float s = star.size() / 2;
            GuiDraw.rect(graphics, x - s, y - s, x + s, y + s, GuiDraw.argb(alpha, tint));
        }
        graphics.flush();
    }

    // A planet's curve under the core, its rim lit by the power.
    private void drawPlanet(GuiGraphics graphics, float cx, int color, float fade) {
        float radius = width * 0.9f;
        float crest = pointY(SkillTree.CORE_Y) + nodeSize * 0.9f;
        float centerY = crest + radius;
        int rim = GuiDraw.mix(color, 0xFFFFFF, 0.25f);
        for (int x = 0; x < width; x += 2) {
            float dx = x + 1 - cx;
            if (Math.abs(dx) >= radius) {
                continue;
            }
            int top = Math.round(centerY - Mth.sqrt(radius * radius - dx * dx));
            if (top >= height) {
                continue;
            }
            float edge = 1 - Math.abs(dx) / radius;
            graphics.fillGradient(x, top - 10, x + 2, top, GuiDraw.argb(0, color), GuiDraw.argb(Math.round(70 * edge * fade), color));
            graphics.fillGradient(x, top, x + 2, height, 0xFF000000 | GuiDraw.mix(0x07040B, color, 0.10f), 0xFF030205);
            graphics.fill(x, top, x + 2, top + 1, GuiDraw.argb(Math.round(220 * edge * fade), rim));
        }
    }

    // ---- Constellation ---------------------------------------------------------------------------------------------

    private float linkProgress(SkillTree.Node node, float t) {
        return Mth.clamp((t - 0.15f - (depth(node) - 1) * 0.3f) / 0.35f, 0, 1);
    }

    private float popProgress(SkillTree.Node node, float t) {
        return Mth.clamp((t - 0.35f - (depth(node) - 1) * 0.3f) / 0.28f, 0, 1);
    }

    private void links(GuiGraphics graphics, float t) {
        int color = power.color();
        float coreX = pointX(SkillTree.CORE_X);
        float coreY = pointY(SkillTree.CORE_Y);
        // Faint roots under the core.
        float roots = Mth.clamp(t / 0.4f, 0, 1);
        int rootColor = GuiDraw.argb(Math.round(70 * roots), color);
        GuiDraw.line(graphics, coreX, coreY, coreX - nodeSize * 2.2f, coreY + nodeSize * 1.6f, 1, 1.5f, rootColor);
        GuiDraw.line(graphics, coreX, coreY, coreX + nodeSize * 2.2f, coreY + nodeSize * 1.6f, 1, 1.5f, rootColor);
        GuiDraw.line(graphics, coreX, coreY, coreX, coreY + nodeSize * 1.8f, 1, 1.5f, rootColor);

        for (int i = 0; i < nodes.size(); i++) {
            SkillTree.Node node = nodes.get(i);
            SkillTree.Node parent = parentOf(node);
            float x1 = parent == null ? coreX : nodeX(parent);
            float y1 = parent == null ? coreY : nodeY(parent);
            float x2 = nodeX(node);
            float y2 = nodeY(node);
            float progress = linkProgress(node, t);
            if (progress <= 0) {
                continue;
            }
            boolean open = unlocked(node);
            boolean lit = i == selected || i == hovered;
            int lineColor = !open ? 0x60606070
                    : GuiDraw.argb(lit ? 255 : 190, GuiDraw.mix(node.ult() ? GuiDraw.mix(color, ULT_COLOR, 0.5f) : color, 0xFFFFFF, lit ? 0.45f : 0.15f));
            float ex = Mth.lerp(progress, x1, x2);
            float ey = Mth.lerp(progress, y1, y2);
            GuiDraw.line(graphics, x1, y1, ex, ey, lit ? 1.4f : 1.1f, lit ? 3.5f : 2.5f, lineColor);
            if (progress >= 1 && open) {
                // A joint star on the way, and a spark running from the root out to the skill.
                float jx = Mth.lerp(0.55f, x1, x2);
                float jy = Mth.lerp(0.55f, y1, y2);
                GuiDraw.diamond(graphics, jx, jy, 2, GuiDraw.argb(230, GuiDraw.mix(color, 0xFFFFFF, 0.5f)));
                float phase = (t * 0.42f + i * 0.37f) % 1f;
                float sx = Mth.lerp(phase, x1, x2);
                float sy = Mth.lerp(phase, y1, y2);
                float spark = Mth.sin(phase * Mth.PI);
                GuiDraw.star(graphics, sx, sy, 3 + 2 * spark, 0.7f, t * 2, GuiDraw.argb(Math.round(230 * spark), 0xFFFFFF));
            }
        }
        graphics.flush();
    }

    private void core(GuiGraphics graphics, float t) {
        int color = power.color();
        float x = pointX(SkillTree.CORE_X);
        float y = pointY(SkillTree.CORE_Y);
        float appear = Mth.clamp(t / 0.3f, 0, 1);
        float beat = 0.5f + 0.5f * Mth.sin(t * 2.4f);
        GuiDraw.glow(graphics, x, y, nodeSize * (1.6f + 0.2f * beat), color, 0.7f * appear);
        GuiDraw.glow(graphics, x, y, nodeSize * 0.6f, 0xFFFFFF, 0.5f * appear);
        GuiDraw.star(graphics, x, y, nodeSize * (0.75f + 0.1f * beat) * appear, 1.4f, t * 0.4f, GuiDraw.argb(255, GuiDraw.mix(color, 0xFFFFFF, 0.6f)));
        GuiDraw.star(graphics, x, y, nodeSize * 0.45f * appear, 1.0f, t * 0.4f + Mth.PI / 4, GuiDraw.argb(220, color));
        GuiDraw.diamond(graphics, x, y, 2.5f * appear, 0xFFFFFFFF);
        graphics.flush();
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    private void node(GuiGraphics graphics, int index, float t) {
        SkillTree.Node node = nodes.get(index);
        float pop = popProgress(node, t);
        if (pop <= 0) {
            return;
        }
        boolean open = unlocked(node);
        boolean isSelected = index == selected;
        boolean isHovered = index == hovered;
        int color = power.color();
        float x = nodeX(node);
        float y = nodeY(node);
        float scale = easeOutBack(pop) * (isHovered ? 1.08f : 1f);
        float beat = 0.5f + 0.5f * Mth.sin(t * 3f);

        if (open) {
            GuiDraw.glow(graphics, x, y, nodeSize * (isSelected ? 1.5f + 0.15f * beat : 1.1f), node.ult() ? ULT_COLOR : color,
                    (isSelected ? 0.55f : isHovered ? 0.4f : 0.18f) * pop);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        int half = nodeSize / 2;
        int icon = nodeSize - 4;
        graphics.fill(-half, -half, half, half, 0xF0100A14);
        SkillIcons.draw(graphics, font, power, node.slot(), -half + 2, -half + 2, icon, open ? 1f : 0.25f);
        int frame;
        if (!open) {
            frame = 0xFF4A4452;
        } else if (isSelected) {
            frame = 0xFFFFFFFF;
        } else if (node.ult()) {
            frame = 0xFF000000 | ULT_COLOR;
        } else {
            frame = 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, isHovered ? 0.5f : 0.2f);
        }
        GuiDraw.ornateFrame(graphics, -half - 1, -half - 1, half + 1, half + 1, GuiDraw.alpha(frame, 0.85f), frame);
        GuiDraw.frame(graphics, -half + 1, -half + 1, half - 1, half - 1, GuiDraw.alpha(frame, 0.3f));
        if (node.ult()) {
            crown(graphics, 0, -half - 4, open ? 0xFF000000 | ULT_COLOR : 0xFF5A5060);
        }
        if (!open) {
            lock(graphics, 0, 0);
        }

        // Key badge on the bottom right corner (a Shift variant shows a small arrow before the key).
        String key = PowerHud.shortKey(Keybinds.forSlot(power, node.slot()).getTranslatedKeyMessage().getString());
        String badge = node.shift() ? "⇧" + key : key;
        int bw = font.width(badge) + 5;
        int bx = half - bw + 4;
        int by = half - 6;
        graphics.fill(bx, by, bx + bw, by + 11, 0xF0050308);
        GuiDraw.frame(graphics, bx, by, bx + bw, by + 11, frame);
        graphics.drawString(font, badge, bx + 3, by + 2, open ? 0xFFFFFFFF : 0xFF8A8494, false);
        graphics.pose().popPose();
    }

    private static void crown(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x - 4, y + 1, x + 5, y + 3, color);
        graphics.fill(x - 4, y - 2, x - 3, y + 1, color);
        graphics.fill(x, y - 3, x + 1, y + 1, color);
        graphics.fill(x + 4, y - 2, x + 5, y + 1, color);
    }

    private static void lock(GuiGraphics graphics, int x, int y) {
        GuiDraw.frame(graphics, x - 3, y - 6, x + 4, y, 0xFFB8B0C4);
        graphics.fill(x - 4, y - 1, x + 5, y + 6, 0xFFB8B0C4);
        graphics.fill(x, y + 1, x + 1, y + 4, 0xFF2A2430);
    }

    private int nodeAt(double mouseX, double mouseY) {
        float t = time();
        for (int i = 0; i < nodes.size(); i++) {
            SkillTree.Node node = nodes.get(i);
            if (popProgress(node, t) < 0.5f) {
                continue;
            }
            float half = nodeSize / 2f + 2;
            if (Math.abs(mouseX - nodeX(node)) <= half && Math.abs(mouseY - nodeY(node)) <= half) {
                return i;
            }
        }
        return -1;
    }

    // ---- Text around -----------------------------------------------------------------------------------------------

    private void header(GuiGraphics graphics, float t) {
        int color = power.color();
        float cx = (areaLeft + areaRight) / 2f;
        Component name = power.name();
        // As big as fits without running into the corner labels.
        float room = 2 * (cx - 100);
        float scale = 2.5f;
        while (scale > 1 && font.width(name) * scale > room) {
            scale -= 0.5f;
        }
        float fade = Mth.clamp(t / 0.4f, 0, 1);
        int alpha = Math.max(8, Math.round(255 * fade));
        GuiDraw.glow(graphics, cx, 22, font.width(name) * scale * 0.7f, color, 0.25f * fade);
        GuiDraw.centeredText(graphics, font, name, cx + 1, 9, scale, GuiDraw.argb(alpha, GuiDraw.mix(color, 0x000000, 0.5f)), false);
        GuiDraw.centeredText(graphics, font, name, cx, 8, scale, GuiDraw.argb(alpha, GuiDraw.mix(color, 0xFFFFFF, 0.75f)), true);
        Component subtitle = Component.literal("✦ ").append(power.description()).append(" ✦");
        GuiDraw.centeredText(graphics, font, subtitle, cx, 10 + 8 * scale + 3, 1, GuiDraw.argb(alpha, GuiDraw.mix(color, 0xFFFFFF, 0.3f)), true);

        // Top left: how to use the screen.
        String key = PowerHud.shortKey(Keybinds.SKILLS_MENU.getTranslatedKeyMessage().getString());
        int kw = font.width(key) + 5;
        graphics.fill(8, 7, 8 + kw, 18, 0xF0050308);
        GuiDraw.frame(graphics, 8, 7, 8 + kw, 18, 0xFF000000 | color);
        graphics.drawString(font, key, 11, 9, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.battlegroundsmod.skills.title"), 12 + kw, 9, TEXT, true);

        // Top right: how many skills are open.
        int open = 0;
        for (SkillTree.Node node : nodes) {
            if (unlocked(node)) {
                open++;
            }
        }
        Component count = Component.literal("✦ " + open + " / " + nodes.size());
        int box = 16;
        int bx = panelRight - box;
        graphics.fill(bx, 6, panelRight, 6 + box, 0xF0050308);
        GuiDraw.ornateFrame(graphics, bx, 6, panelRight, 6 + box, 0xFF000000 | GuiDraw.mix(color, 0x000000, 0.2f), 0xFF000000 | color);
        GuiDraw.star(graphics, bx + box / 2f, 6 + box / 2f, 6, 1.2f, t * 0.8f, 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.5f));
        graphics.flush();
        GuiDraw.rightText(graphics, font, count, bx - 6, 10, 1, TEXT, true);
    }

    private void panel(GuiGraphics graphics, float t) {
        if (nodes.isEmpty()) {
            return;
        }
        SkillTree.Node node = nodes.get(Mth.clamp(selected, 0, nodes.size() - 1));
        int slot = node.slot();
        boolean open = unlocked(node);
        int color = power.color();
        int top = 44;
        int bottom = height - 42;
        int left = panelLeft;
        int right = panelRight;
        int width = right - left;
        float in = Mth.clamp((Util.getMillis() - selectedAt) / 220f, 0, 1);
        float appear = Mth.clamp((t - 0.2f) / 0.35f, 0, 1);

        graphics.fillGradient(left, top, right, bottom, 0xF20C0814, 0xF2060410);
        GuiDraw.ornateFrame(graphics, left, top, right, bottom, 0xFF000000 | GuiDraw.mix(color, 0x000000, 0.35f),
                0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.3f));
        graphics.fill(left + 1, top + 1, right - 1, top + 2, GuiDraw.argb(160, color));
        graphics.enableScissor(left + 2, top + 2, right - 2, bottom - 2);

        int cx = (left + right) / 2;
        int y = top + 14 + Math.round((1 - in) * 6);
        int textAlpha = Math.max(8, Math.round(255 * in * appear));

        // Big icon.
        int size = width >= 190 ? 52 : 44;
        int half = size / 2;
        GuiDraw.glow(graphics, cx, y + half, size * 1.1f, node.ult() ? ULT_COLOR : color, (open ? 0.4f : 0.1f) * in * appear);
        // A dotted halo around the icon.
        for (int i = 0; i < 24; i++) {
            float a = i / 24f * Mth.TWO_PI + t * 0.15f;
            float r = half + 9;
            GuiDraw.rect(graphics, cx + Mth.cos(a) * r - 0.5f, y + half + Mth.sin(a) * r - 0.5f,
                    cx + Mth.cos(a) * r + 0.5f, y + half + Mth.sin(a) * r + 0.5f, GuiDraw.argb(Math.round(110 * appear), color));
        }
        graphics.flush();
        graphics.fill(cx - half, y, cx + half, y + size, 0xF0100A14);
        SkillIcons.draw(graphics, font, power, slot, cx - half + 3, y + 3, size - 6, open ? 1f : 0.25f);
        int frame = open ? 0xFF000000 | (node.ult() ? ULT_COLOR : GuiDraw.mix(color, 0xFFFFFF, 0.3f)) : 0xFF4A4452;
        GuiDraw.ornateFrame(graphics, cx - half - 1, y - 1, cx + half + 1, y + size + 1, GuiDraw.alpha(frame, 0.85f), frame);
        if (node.ult()) {
            crown(graphics, cx, y - 6, frame);
        }
        if (!open) {
            lock(graphics, cx, y + half);
        }
        y += size + 10;

        // Name.
        Component name = Component.literal(power.abilityName(slot).getString().toUpperCase());
        float nameScale = font.width(name) * 1.5f <= width - 16 ? 1.5f : 1f;
        List<FormattedCharSequence> nameLines = nameScale < 1.5f ? font.split(name, width - 16) : List.of(name.getVisualOrderText());
        for (FormattedCharSequence line : nameLines) {
            graphics.pose().pushPose();
            graphics.pose().translate(cx - font.width(line) * nameScale / 2f, y, 0);
            graphics.pose().scale(nameScale, nameScale, 1);
            graphics.drawString(font, line, 0, 0, GuiDraw.argb(textAlpha, 0xFFFFFF), true);
            graphics.pose().popPose();
            y += Math.round(10 * nameScale);
        }
        y += 3;

        // Status pill.
        Component status = Component.translatable(open ? "screen.battlegroundsmod.skills.unlocked" : "screen.battlegroundsmod.skills.locked");
        int sw = font.width(status) + 8;
        graphics.fill(cx - sw / 2, y, cx + sw / 2, y + 11, open ? GuiDraw.argb(230, GuiDraw.mix(color, 0x000000, 0.35f)) : 0xE0302A36);
        GuiDraw.frame(graphics, cx - sw / 2, y, cx + sw / 2, y + 11, open ? 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.3f) : 0xFF5A5262);
        GuiDraw.centeredText(graphics, font, status, cx, y + 2, 1, GuiDraw.argb(textAlpha, 0xFFFFFF), false);
        y += 16;

        // Key.
        String key = PowerHud.shortKey(Keybinds.forSlot(power, slot).getTranslatedKeyMessage().getString());
        Component keyLine = Component.translatable("screen.battlegroundsmod.skills.key");
        String keyText = node.shift() ? "Shift + " + key : key;
        int kw = font.width(keyText) + 6;
        int total = font.width(keyLine) + 5 + kw;
        int kx = cx - total / 2;
        graphics.drawString(font, keyLine, kx, y + 2, GuiDraw.argb(textAlpha, MUTED), true);
        int bx = kx + font.width(keyLine) + 5;
        graphics.fill(bx, y, bx + kw, y + 11, 0xF0050308);
        GuiDraw.frame(graphics, bx, y, bx + kw, y + 11, 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.3f));
        graphics.drawString(font, keyText, bx + 3, y + 2, GuiDraw.argb(textAlpha, 0xFFFFFF), false);
        y += 16;

        // Tags.
        List<Component> tags = new ArrayList<>();
        List<Integer> tagColors = new ArrayList<>();
        if (node.ult()) {
            tags.add(Component.translatable("screen.battlegroundsmod.skills.tag.ult"));
            tagColors.add(ULT_COLOR);
        }
        if (node.shift()) {
            tags.add(Component.translatable("screen.battlegroundsmod.skills.tag.shift"));
            tagColors.add(GuiDraw.mix(color, 0xFFFFFF, 0.2f));
        }
        Ability ability = PowerLogic.ability(power, slot);
        if (ability != null && ability.counter()) {
            tags.add(Component.translatable("screen.battlegroundsmod.skills.tag.counter"));
            tagColors.add(0x6FB8FF);
        }
        String tagsKey = SkillIcons.key(power, slot, "tags");
        if (I18n.exists(tagsKey)) {
            for (String tag : I18n.get(tagsKey).split(",")) {
                if (!tag.isBlank()) {
                    tags.add(Component.literal(tag.trim()));
                    tagColors.add(GuiDraw.mix(color, 0x888888, 0.5f));
                }
            }
        }
        y = pills(graphics, tags, tagColors, left + 8, right - 8, y, textAlpha);

        // Cooldown.
        Component cooldown;
        if (ability == null) {
            cooldown = Component.empty();
        } else if (ability.cooldown() >= PowerData.SPENT / 2) {
            cooldown = Component.translatable("screen.battlegroundsmod.skills.once");
        } else {
            float seconds = ability.cooldown() / 20f;
            String value = seconds == Math.floor(seconds) ? String.valueOf((int) seconds) : String.format("%.1f", seconds);
            cooldown = Component.translatable("screen.battlegroundsmod.skills.cooldown", value);
        }
        GuiDraw.centeredText(graphics, font, cooldown, cx, y, 1, GuiDraw.argb(textAlpha, TEXT), true);
        y += 11;
        if (node.ult()) {
            GuiDraw.centeredText(graphics, font, Component.translatable("screen.battlegroundsmod.skills.needs_ult"), cx, y, 1,
                    GuiDraw.argb(textAlpha, ULT_COLOR), true);
            y += 11;
        }
        y += 4;

        // Ornament line.
        int line = GuiDraw.argb(Math.round(170 * appear), color);
        graphics.fill(left + 10, y, cx - 5, y + 1, line);
        graphics.fill(cx + 6, y, right - 10, y + 1, line);
        GuiDraw.diamond(graphics, cx + 0.5f, y + 0.5f, 3, GuiDraw.argb(255, GuiDraw.mix(color, 0xFFFFFF, 0.4f)));
        graphics.flush();
        y += 8;

        // Description.
        String descKey = SkillIcons.key(power, slot, "desc");
        Component description = I18n.exists(descKey) ? Component.translatable(descKey)
                : Component.translatable("screen.battlegroundsmod.skills.no_desc");
        for (FormattedCharSequence lineText : font.split(description, width - 18)) {
            graphics.drawString(font, lineText, left + 9, y, GuiDraw.argb(textAlpha, 0xD8D2E0), true);
            y += 10;
        }
        if (!open) {
            y += 4;
            SkillTree.Node parent = parentOf(node);
            Component needs = parent == null ? Component.translatable("screen.battlegroundsmod.skills.core")
                    : power.abilityName(parent.slot());
            graphics.drawString(font, Component.translatable("screen.battlegroundsmod.skills.requires", needs), left + 9, y,
                    GuiDraw.argb(textAlpha, 0xFF7A7A), true);
        }
        graphics.disableScissor();
    }

    // Tag pills, wrapped over as many rows as they need. Returns the y under them.
    private int pills(GuiGraphics graphics, List<Component> tags, List<Integer> colors, int left, int right, int y, int textAlpha) {
        if (tags.isEmpty()) {
            return y;
        }
        // Rows first, so each row can be centered.
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> row = new ArrayList<>();
        int rowWidth = 0;
        for (int i = 0; i < tags.size(); i++) {
            int w = font.width(tags.get(i)) + 8;
            if (!row.isEmpty() && rowWidth + 3 + w > right - left) {
                rows.add(row);
                row = new ArrayList<>();
                rowWidth = 0;
            }
            rowWidth += (row.isEmpty() ? 0 : 3) + w;
            row.add(i);
        }
        rows.add(row);
        int cx = (left + right) / 2;
        for (List<Integer> r : rows) {
            int total = -3;
            for (int i : r) {
                total += font.width(tags.get(i)) + 8 + 3;
            }
            int x = cx - total / 2;
            for (int i : r) {
                int w = font.width(tags.get(i)) + 8;
                int c = colors.get(i);
                graphics.fill(x, y, x + w, y + 11, GuiDraw.argb(200, GuiDraw.mix(c, 0x000000, 0.55f)));
                GuiDraw.frame(graphics, x, y, x + w, y + 11, 0xFF000000 | c);
                graphics.drawString(font, tags.get(i), x + 4, y + 2, GuiDraw.argb(textAlpha, GuiDraw.mix(c, 0xFFFFFF, 0.6f)), false);
                x += w + 3;
            }
            y += 14;
        }
        return y + 2;
    }

    private void footer(GuiGraphics graphics) {
        int color = power.color();
        int top = height - 32;
        int bottom = height - 8;
        int left = 12;
        int right = width - 12;
        graphics.fillGradient(left, top, right, bottom, 0xF00C0814, 0xF0060410);
        GuiDraw.ornateFrame(graphics, left, top, right, bottom, 0xFF000000 | GuiDraw.mix(color, 0x000000, 0.35f),
                0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.3f));
        graphics.fill(left + 1, top + 1, right - 1, top + 2, GuiDraw.argb(120, color));

        // Left: the soul's tier.
        graphics.drawString(font, Component.translatable("screen.battlegroundsmod.skills.tier"), left + 8, top + 4, MUTED, false);
        graphics.drawString(font, Tiers.name(data.tier), left + 8, top + 14, 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.4f), true);
        int divider = left + 8 + Math.max(font.width(Component.translatable("screen.battlegroundsmod.skills.tier")),
                font.width(Tiers.name(data.tier))) + 10;
        graphics.fill(divider, top + 4, divider + 1, bottom - 4, GuiDraw.argb(120, color));

        // Right: how to use the screen.
        Component click = Component.translatable("screen.battlegroundsmod.skills.click");
        Component browse = Component.translatable("screen.battlegroundsmod.skills.browse");
        int hints = right - 8 - Math.max(font.width(click), font.width(browse));
        graphics.drawString(font, click, hints, top + 4, MUTED, false);
        graphics.drawString(font, browse, hints, top + 14, MUTED, false);
        graphics.fill(hints - 10, top + 4, hints - 9, bottom - 4, GuiDraw.argb(120, color));

        int locked = 0;
        for (SkillTree.Node node : nodes) {
            if (!unlocked(node)) {
                locked++;
            }
        }
        Component center = locked == 0
                ? Component.literal("✦ ").append(Component.translatable("screen.battlegroundsmod.skills.all_unlocked")).append(" ✦")
                : Component.translatable("screen.battlegroundsmod.skills.some_locked", locked);
        GuiDraw.centeredText(graphics, font, center, (divider + hints - 9) / 2f, top + 9, 1, 0xFF000000 | GuiDraw.mix(color, 0xFFFFFF, 0.45f), true);
    }

    // ---- Input -----------------------------------------------------------------------------------------------------

    private void select(int index) {
        if (nodes.isEmpty()) {
            return;
        }
        index = Math.floorMod(index, nodes.size());
        if (index != selected) {
            selected = index;
            selectedAt = Util.getMillis();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f + 0.08f * index, 0.7f));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layout();
        int index = nodeAt(mouseX, mouseY);
        if (index >= 0 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            select(index);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Keybinds.SKILLS_MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_W) {
            select(selected - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_D
                || keyCode == GLFW.GLFW_KEY_S || keyCode == GLFW.GLFW_KEY_TAB) {
            select(selected + 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
