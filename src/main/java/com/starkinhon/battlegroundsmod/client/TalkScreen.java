package com.starkinhon.battlegroundsmod.client;

import static com.starkinhon.battlegroundsmod.client.ui.Px.GH;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GOLD;
import static com.starkinhon.battlegroundsmod.client.ui.Px.GW;
import static com.starkinhon.battlegroundsmod.client.ui.Px.MUTED;
import static com.starkinhon.battlegroundsmod.client.ui.Px.R;
import static com.starkinhon.battlegroundsmod.client.ui.Px.T;
import static com.starkinhon.battlegroundsmod.client.ui.Px.TEXT;
import static com.starkinhon.battlegroundsmod.client.ui.Px.WHITE;
import static com.starkinhon.battlegroundsmod.client.ui.Px.clamp;
import static com.starkinhon.battlegroundsmod.client.ui.Px.ink;
import static com.starkinhon.battlegroundsmod.client.ui.Px.lerp;
import static com.starkinhon.battlegroundsmod.client.ui.Px.mix;
import static com.starkinhon.battlegroundsmod.client.ui.Px.rgb;
import static com.starkinhon.battlegroundsmod.client.ui.Px.tw;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;
import com.starkinhon.battlegroundsmod.client.ui.IdlePoses;
import com.starkinhon.battlegroundsmod.client.ui.Journal;
import com.starkinhon.battlegroundsmod.client.ui.Px;
import com.starkinhon.battlegroundsmod.client.ui.SkinDraw;
import com.starkinhon.battlegroundsmod.client.ui.UiIcons;
import com.starkinhon.battlegroundsmod.network.ModPayloads;

import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// The talk scene with an NPC (the approved preview's drawConversa, Stark 2026-11-13): right click on an NPC (npc/Talks)
// stops the player, the camera goes to their shoulder and the two look at each other; the NPC gestures while its line
// types under them, centered, with its name over a gold ornate line. The answers stand on the right (keys 1-3); with a
// mission ready the card shows its steps and rewards when the mouse is on "Aceitar" (always, for a delivery: the items
// it asks for and how many the player has, Buu's sweets to pick from). Accepting runs the NPC on the server and its
// answer types in the same place, with NOVA MISSÃO / ENTREGUE on top and the items flying to the hotbar.
public class TalkScreen extends Screen {
    private final ModPayloads.OpenTalk talk;
    private final Journal.Mission m;
    private final String name;
    private final String opening;
    private final List<String> sweets = new ArrayList<>();
    // fala (the NPC's line with the answers), mais, espera (the server's answer coming), aceitou, dito (only Fechar), tchau
    private String state;
    private String reply = "";
    private float t, age, cardA, warnAt = -9, closeAt = -1;
    private int pick = -1;
    private long last = System.nanoTime();

    public TalkScreen(ModPayloads.OpenTalk talk) {
        super(Component.translatable("screen.battlegroundsmod.conversa"));
        this.talk = talk;
        this.m = talk.mission().isEmpty() ? null : Journal.mission(talk.mission());
        Component who = parse(talk.name());
        this.name = m != null && !m.quem().isEmpty() ? m.quem() : who.getString();
        this.opening = m != null ? m.fala() : parse(talk.line()).getString();
        this.state = m != null ? "fala" : "dito";
        if (m != null && m.escolha()) {
            for (Journal.Reward r : m.entrega()) {
                sweets.add(r.id());
            }
        }
    }

    private static Component parse(String json) {
        Minecraft mc = Minecraft.getInstance();
        if (json == null || json.isEmpty() || mc.level == null) {
            return Component.empty();
        }
        try {
            Component c = Component.Serializer.fromJson(json, mc.level.registryAccess());
            return c == null ? Component.empty() : c;
        } catch (RuntimeException e) {
            return Component.literal(json);
        }
    }

    // ---------------------------------------------------------------- from the server

    public static void open(ModPayloads.OpenTalk payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.level.getEntity(payload.npc()) == null) {
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof TalkScreen)) {
            return;
        }
        camStart(payload.npc());
        mc.setScreen(new TalkScreen(payload));
    }

    public static void reply(List<String> lines, boolean accepted) {
        if (!(Minecraft.getInstance().screen instanceof TalkScreen ts)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            String s = parse(l).getString();
            if (!s.isEmpty()) {
                sb.append(sb.length() > 0 ? " " : "").append(s);
            }
        }
        ts.reply = sb.toString();
        ts.set(accepted ? "aceitou" : "dito");
        if (accepted) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f));
        }
    }

    private void set(String s) {
        state = s;
        t = 0;
    }

    // ---------------------------------------------------------------- what is said

    private String shown() {
        return switch (state) {
            case "mais" -> m != null && !m.mais().isEmpty() ? m.mais() : opening;
            case "espera" -> reply.isEmpty() ? "..." : reply;
            case "aceitou" -> reply.isEmpty() ? opening : reply;
            case "dito" -> m != null && !reply.isEmpty() ? reply : opening;
            case "tchau" -> Component.translatable("screen.battlegroundsmod.conversa.tchau").getString();
            default -> opening;
        };
    }

    private boolean asking() {
        return state.equals("fala") || state.equals("mais");
    }

    private static int have(String id) {
        ItemStack want = Journal.stack(id, 1);
        LocalPlayer p = Minecraft.getInstance().player;
        if (want.isEmpty() || p == null) {
            return 0;
        }
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(want.getItem())) {
                n += s.getCount();
            }
        }
        return n;
    }

    private void accept() {
        if (m == null) {
            return;
        }
        if (!m.entrega().isEmpty() && !m.escolha()) {
            for (Journal.Reward r : m.entrega()) {
                if (have(r.id()) < r.n()) {
                    warnAt = Px.time;
                    return;
                }
            }
        }
        String chosen = "";
        if (m.escolha()) {
            if (pick < 0 || pick >= sweets.size() || have(sweets.get(pick)) < 1) {
                warnAt = Px.time;
                return;
            }
            chosen = BuiltInRegistries.ITEM.getKey(Journal.stack(sweets.get(pick), 1).getItem()).toString();
        }
        reply = "";
        set("espera");
        PacketDistributor.sendToServer(new ModPayloads.TalkChoice(talk.npc(), 0, chosen));
    }

    private void bye() {
        set("tchau");
        closeAt = 2.2f;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        t += dt;
        age += dt;
        if (closeAt > 0 && t >= closeAt) {
            onClose();
            return;
        }
        facePlayer();
        Px.begin(graphics, mouseX, mouseY);
        Px.hits.clear();
        try {
            draw(dt);
            Px.drawTip();
        } finally {
            Px.end();
        }
    }

    // The player keeps looking at the NPC (their back to the camera on the shoulder).
    private void facePlayer() {
        Minecraft mc = Minecraft.getInstance();
        Entity npc = mc.level == null ? null : mc.level.getEntity(talk.npc());
        LocalPlayer p = mc.player;
        if (npc == null || p == null) {
            return;
        }
        Vec3 to = npc.getEyePosition().subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
        p.setYRot(p.getYRot() + Mth.wrapDegrees(yaw - p.getYRot()) * 0.2f);
        p.setXRot(p.getXRot() + (pitch - p.getXRot()) * 0.2f);
        p.setYHeadRot(p.getYRot());
        p.yBodyRot = p.getYRot();
    }

    private void draw(float dt) {
        String shown = shown();
        int n = (int) Math.floor(t * 40);
        boolean done = n >= shown.length();
        talkingNow = !done;
        int uc = uniColor();

        // the dark band at the bottom for the text to read well (no box), and a light one on top
        for (int i = (int) Math.ceil(GH * 0.62f); i < GH; i += 2) {
            float k = Math.max(0, (i - GH * 0.62f) / (GH * 0.38f));
            R(0, i, GW, 2, rgb(6, 5, 12), 0.78f * (float) Math.pow(k, 1.3));
        }
        for (int i = 0; i < 24; i += 2) {
            R(0, i, GW, 2, rgb(6, 5, 12), 0.35f * (1 - i / 24f));
        }

        // ---- the name, the ornate line and the line (centered)
        float nameY = GH - 58, lw = Math.min(GW * 0.62f, 330), lx = GW / 2f - lw / 2;
        T(name, GW / 2f, nameY, mix(GOLD, WHITE, 0.12f), 10, true, 0, true, 1);
        for (int x = 0; x < lw; x++) {
            float e = Math.min(x, lw - x) / (lw * 0.18f);
            R(lx + x, nameY + 13, 1, 1, GOLD, 0.85f * clamp(e, 0, 1));
        }
        for (float ex : new float[] {lx - 3, lx + lw}) {
            R(ex + 1, nameY + 12, 1, 3, GOLD, 0.7f);
            R(ex, nameY + 13, 3, 1, GOLD, 0.7f);
        }
        R(GW / 2f - 2, nameY + 12, 4, 3, GOLD, 1);
        R(GW / 2f - 1, nameY + 11, 2, 5, GOLD, 1);
        List<String> lines = Px.wrap(shown, lw + 30, 9);
        int left = n;
        float yy = nameY + 20;
        for (String l : lines) {
            String part = l.substring(0, Math.max(0, Math.min(l.length(), left)));
            left -= l.length() + 1;
            int fullW = tw(l, 9);
            T(part, GW / 2f - fullW / 2f + 1, yy + 1, 0, 9, false, -1, false, 0.6f);
            T(part, GW / 2f - fullW / 2f, yy, WHITE, 9, false, -1, false, 1);
            yy += 12;
            if (left < 0) {
                break;
            }
        }
        if (done && asking() && Math.floor(Px.time * 2) % 2 == 1) {
            // ▼
            float cx = GW / 2f, ty = yy + 2;
            R(cx - 3, ty, 7, 1, GOLD, 1);
            R(cx - 2, ty + 1, 5, 1, GOLD, 1);
            R(cx - 1, ty + 2, 3, 1, GOLD, 1);
            R(cx, ty + 3, 1, 1, GOLD, 1);
        }

        // ---- the answers on the right (each on a dark panel with an icon; keys 1-3)
        List<Object[]> opts = options();
        float ow = Math.min(168, GW * 0.36f), ox = GW - ow - 12, oh = 17;
        float oy = Math.round(GH * 0.5f) - opts.size() * (oh + 3) / 2f;
        float optsTop = oy;
        boolean hovAcc = false;
        if (done) {
            for (int i = 0; i < opts.size(); i++) {
                Object[] o = opts.get(i);
                String label = (String) o[0];
                boolean quest = (Boolean) o[1];
                Runnable f = (Runnable) o[2];
                boolean over = Px.over(ox, oy, ow, oh);
                if (over && quest) {
                    hovAcc = true;
                }
                float sh = over ? 3 : 0, a = clamp((t - shown.length() / 40f) * 6 - i * 0.6f, 0, 1);
                if (a <= 0) {
                    oy += oh + 3;
                    continue;
                }
                R(ox - sh, oy, ow, oh, over ? rgb(40, 32, 18) : rgb(10, 9, 16), (over ? 0.86f : 0.66f) * a);
                R(ox - sh, oy, ow, 1, over ? GOLD : WHITE, (over ? 0.9f : 0.12f) * a);
                R(ox - sh, oy + oh - 1, ow, 1, over ? GOLD : 0, (over ? 0.9f : 0.4f) * a);
                R(ox - sh, oy, 2, oh, over || quest ? GOLD : WHITE, (over ? 1 : quest ? 0.6f : 0.18f) * a);
                bubbleIcon(ox - sh + 6, oy + 4, quest ? GOLD : rgb(235, 232, 240), quest, a);
                T(label, ox - sh + 21, oy + 5, over ? rgb(255, 240, 190) : WHITE, 8, false, -1, true, a);
                T(String.valueOf(i + 1), ox - sh + ow - 9, oy + 5, rgb(150, 145, 165), 6, false, -1, false, a);
                Px.hit(ox, oy, ow, oh, f);
                oy += oh + 3;
            }
        }
        if (Px.time - warnAt < 1.6f && m != null) {
            String w = Component.translatable(m.escolha() ? "screen.battlegroundsmod.conversa.escolha" : "screen.battlegroundsmod.conversa.faltam").getString();
            T(w, ox + ow / 2, oy + 2, rgb(255, 120, 110), 7, false, 0, true, 1);
        }

        // ---- the mission card: with the mouse on "Aceitar"; a delivery keeps it (the items are clicked there)
        boolean showCard = m != null && done && asking() && (hovAcc || !m.entrega().isEmpty());
        cardA = lerp(cardA, showCard ? 1 : 0, 1 - (float) Math.pow(0.001, dt));
        if (m != null && cardA > 0.02f) {
            card(ox, optsTop, ow, uc);
        }

        // ---- accepted: NOVA MISSÃO / ENTREGUE on top and the items flying to the hotbar
        if (state.equals("aceitou") && m != null) {
            boolean delivered = !m.entrega().isEmpty();
            float a = clamp(t * 3, 0, 1) * clamp(3.2f - t, 0, 1);
            if (a > 0) {
                T(Component.translatable(delivered ? "screen.battlegroundsmod.conversa.entregue" : "screen.battlegroundsmod.conversa.nova").getString(),
                        GW / 2f, 30, GOLD, 7, true, 0, true, a);
                T(m.nome(), GW / 2f, 40, WHITE, 12, true, 0, true, a);
                float w2 = tw(m.nome(), 12, true) + 40;
                for (int x = 0; x < w2; x++) {
                    float e = Math.min(x, w2 - x) / (w2 * 0.2f);
                    R(GW / 2f - w2 / 2 + x, 56, 1, 1, GOLD, 0.8f * clamp(e, 0, 1) * a);
                }
            }
            if (delivered && m.escolha() && pick >= 0 && pick < sweets.size() && t > 0.2f) {
                // the sweet picked goes up to the NPC (what it gave comes in MISSÃO CONCLUÍDA)
                float p = clamp(Px.easeOut((t - 0.2f) / 0.6f), 0, 1);
                if (p < 1) {
                    UiIcons.slot(lerp(GW - 120, GW * 0.42f, p), lerp(GH * 0.5f, GH * 0.38f, p) - (float) Math.sin(p * Math.PI) * 16, sweets.get(pick),
                            Journal.stack(sweets.get(pick), 1), 0, -1, false);
                }
            } else if (delivered && t > 0.2f) {
                float p = clamp(Px.easeOut((t - 0.2f) / 0.5f), 0, 1);
                List<Journal.Reward> rec = m.rec();
                for (int i = 0; i < rec.size(); i++) {
                    if (p >= 1) {
                        break;
                    }
                    Journal.Reward r = rec.get(i);
                    float x0 = GW / 2f - rec.size() * 10 + i * 20, y0 = 64, x1 = GW / 2f - 80 + i * 18, y1 = GH - 12;
                    UiIcons.slot(lerp(x0, x1, p), lerp(y0, y1, p) - (float) Math.sin(p * Math.PI) * 20, r.id(), r.stack(), 0, -1, false);
                }
            }
        }
    }

    private int uniColor() {
        if (m == null) {
            return GOLD;
        }
        return switch (m.uni()) {
            case "naruto" -> 0xF28C28;
            case "jujutsu" -> 0x7A5CFF;
            case "bleach" -> 0xE8E8F0;
            case "demonslayer" -> 0xC7A2FF;
            default -> 0xFF9A2E;
        };
    }

    // [label, is the mission answer, what it does]
    private List<Object[]> options() {
        List<Object[]> out = new ArrayList<>();
        if (asking() && m != null) {
            out.add(new Object[] {Component.translatable("screen.battlegroundsmod.conversa." + talk.accept()).getString(), true, (Runnable) this::accept});
            out.add(new Object[] {Component.translatable("screen.battlegroundsmod.conversa.mais").getString(), false, (Runnable) () -> set("mais")});
            out.add(new Object[] {Component.translatable("screen.battlegroundsmod.conversa.agora_nao").getString(), false, (Runnable) this::bye});
        } else if (!state.equals("espera") && !state.equals("tchau")) {
            out.add(new Object[] {Component.translatable("screen.battlegroundsmod.conversa.fechar").getString(), false, (Runnable) this::onClose});
        }
        return out;
    }

    private void card(float cx, float optsTop, float cw, int uc) {
        float a = cardA;
        boolean delivery = !m.entrega().isEmpty();
        List<String> steps = new ArrayList<>();
        if (!delivery) {
            List<Journal.Step> st = Journal.steps(m);
            for (int i = 0; i < Math.min(3, st.size()); i++) {
                List<String> w = Px.wrap(st.get(i).text(), cw - 20, 7);
                for (int j = 0; j < w.size(); j++) {
                    steps.add((j > 0 ? "  " : "· ") + w.get(j));
                }
            }
        }
        float ch = delivery ? 72 : 27 + steps.size() * 9 + 4 + 20;
        float cy = optsTop - ch - 8 + Math.round((1 - a) * 6);
        R(cx, cy, cw, ch, rgb(10, 9, 16), 0.8f * a);
        R(cx, cy, cw, 1, uc, 0.9f * a);
        float yy = cy + 5;
        T(m.lugar().toUpperCase(), cx + 7, yy, mix(ink(uc), WHITE, 0.2f), 6, false, -1, true, a);
        T(m.tipo(), cx + cw - 7, yy, m.tipo().startsWith("DIÁRIA") ? rgb(140, 205, 255) : GOLD, 6, false, 1, true, a);
        yy += 9;
        T(m.nome(), cx + 7, yy, WHITE, 9, true, -1, true, a);
        yy += 13;
        if (delivery) {
            T(Component.translatable(m.escolha() ? "screen.battlegroundsmod.conversa.escolha_doce" : "screen.battlegroundsmod.conversa.entrega").getString(),
                    cx + 7, yy, GOLD, 6, true, -1, true, a);
            yy += 8;
            float sx = cx + 7;
            for (int i = 0; i < m.entrega().size(); i++) {
                Journal.Reward r = m.entrega().get(i);
                int got = have(r.id());
                boolean ok = got >= r.n();
                ItemStack stack = Journal.stack(r.id(), Math.max(1, r.n()));
                slot(sx, yy, r.id(), stack, m.escolha() ? 0 : r.n(), m.escolha() && pick == i ? GOLD : -1, !ok, a);
                List<String> tip = List.of(stack.getHoverName().getString(), Component.translatable(ok ? "screen.battlegroundsmod.conversa.voce_tem"
                        : "screen.battlegroundsmod.conversa.falta", ok ? got : r.n() - got).getString());
                List<Integer> colors = List.of(WHITE, ok ? rgb(120, 230, 120) : rgb(255, 120, 110));
                final int idx = i;
                Px.hit(sx, yy, 18, 18, m.escolha() && ok ? () -> pick = idx : null, tip, colors);
                sx += 21;
            }
            yy += 24;
        } else {
            for (String l : steps) {
                T(l, cx + 7, yy, TEXT, 7, false, -1, true, a);
                yy += 9;
            }
            yy += 4;
        }
        T(Component.translatable("screen.battlegroundsmod.conversa.recompensa").getString(), cx + 7, yy + 5, GOLD, 6, true, -1, true, a);
        float sx = cx + cw - 7 - m.rec().size() * 20 + 2;
        for (Journal.Reward r : m.rec()) {
            ItemStack stack = r.stack();
            slot(sx, yy, r.id(), stack, r.n(), -1, false, a);
            String nm = !r.name().isEmpty() ? r.name() : r.id().equals(UiIcons.PODER)
                    ? Component.translatable("hud.battlegroundsmod.missao.poder").getString() : stack.getHoverName().getString();
            List<String> tip = r.desc().isEmpty() ? List.of(nm) : List.of(nm, r.desc());
            List<Integer> colors = r.desc().isEmpty() ? List.of(rgb(120, 200, 255)) : List.of(rgb(120, 200, 255), MUTED);
            Px.hit(sx, yy, 18, 18, null, tip, colors);
            sx += 20;
        }
    }

    private static void slot(float x, float y, String id, ItemStack stack, int n, int glow, boolean dim, float a) {
        if (a < 0.99f) {
            Px.g.flush();
            RenderSystem.setShaderColor(1, 1, 1, a);
        }
        UiIcons.slot(x, y, id, stack, n, glow, dim);
        if (a < 0.99f) {
            Px.g.flush();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    // The pixel icon of an answer: a speech bubble, or the mission's "!".
    private static void bubbleIcon(float x, float y, int col, boolean quest, float a) {
        if (quest) {
            int dark = rgb(42, 26, 5);
            R(x + 3, y, 4, 1, col, a);
            R(x + 2, y + 1, 6, 1, col, a);
            R(x + 1, y + 2, 8, 6, col, a);
            R(x + 2, y + 8, 6, 1, col, a);
            R(x + 3, y + 9, 4, 1, col, a);
            R(x + 4, y + 2, 2, 4, dark, a);
            R(x + 4, y + 7, 2, 1, dark, a);
            return;
        }
        int dark = rgb(34, 34, 34);
        R(x + 1, y + 1, 8, 6, col, a);
        R(x, y + 2, 10, 4, col, a);
        R(x + 2, y + 7, 2, 2, col, a);
        R(x + 2, y + 3, 1, 1, dark, a);
        R(x + 4, y + 3, 1, 1, dark, a);
        R(x + 6, y + 3, 1, 1, dark, a);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            float gx = Px.toGrid(mouseX), gy = Px.toGrid(mouseY);
            if (Px.click(gx, gy)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.6f));
                return true;
            }
            // a click on the text finishes typing it
            if (Math.floor(t * 40) < shown().length()) {
                t = shown().length() / 40f + 0.01f;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_3) {
            List<Object[]> opts = options();
            int i = keyCode - GLFW.GLFW_KEY_1;
            if (Math.floor(t * 40) >= shown().length() && i < opts.size()) {
                ((Runnable) opts.get(i)[2]).run();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.6f));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            if (Math.floor(t * 40) < shown().length()) {
                t = shown().length() / 40f + 0.01f;
            } else if (!asking() && !state.equals("espera")) {
                onClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new ModPayloads.TalkChoice(talk.npc(), 2, ""));
        super.onClose();
    }

    @Override
    public void removed() {
        camStop();
        talkingNow = false;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- the camera on the player's shoulder

    private static final double BACK = 1.75, SIDE = 0.62, UP = 0.12, IN = 0.45, OUT = 0.35;
    private static int camNpc = -1;
    private static long camIn, camOut = -1;
    private static boolean talkingNow;

    public static boolean active() {
        return camNpc >= 0;
    }

    private static void camStart(int npc) {
        Minecraft mc = Minecraft.getInstance();
        if (camNpc < 0) {
            CutsceneCamera.begin("talk");
            camIn = Util.getMillis();
        }
        camNpc = npc;
        camOut = -1;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        mc.options.hideGui = true;
    }

    // A cut back to the player's own view: a glide out passed through their head (the face filled the screen).
    private static void camStop() {
        camFinish();
    }

    private static void camFinish() {
        if (camNpc >= 0) {
            camNpc = -1;
            camOut = -1;
            CutsceneCamera.end("talk");
        }
    }

    // Behind the player's right shoulder, looking past them at the NPC (a bit left of the middle), pushing in slowly.
    public static FaceCutscene.Shot shot(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (camNpc < 0) {
            return null;
        }
        Entity npc = mc.level == null ? null : mc.level.getEntity(camNpc);
        LocalPlayer p = mc.player;
        if (npc == null || p == null) {
            camFinish();
            return null;
        }
        long now = Util.getMillis();
        double tin = (now - camIn) / 1000.0;
        double blend = smooth(tin / IN);
        if (camOut >= 0) {
            double to = (now - camOut) / 1000.0;
            if (to >= OUT) {
                camFinish();
                return null;
            }
            blend = Math.min(blend, 1 - smooth(to / OUT));
        }
        Vec3 eyes = p.getEyePosition(partialTick);
        Vec3 face = npc.getPosition(partialTick).add(0, npc.getBbHeight() * 0.62, 0);
        Vec3 flat = new Vec3(face.x - eyes.x, 0, face.z - eyes.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, p.getViewYRot(partialTick)) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        double push = 0.12 * smooth(tin / 6);
        Vec3 want = eyes.subtract(flat.scale(BACK - push)).add(right.scale(SIDE)).add(0, UP, 0);
        // not inside a wall (the Kame House, Capsule Corp): stop short of it
        var hit = mc.level.clip(new ClipContext(eyes, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        Vec3 position = want;
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 d = want.subtract(eyes);
            double len = d.length();
            position = eyes.add(d.scale(Math.max(0.2, (hit.getLocation().distanceTo(eyes) - 0.25)) / Math.max(1.0E-4, len)));
        }
        Vec3 aim = face.add(right.scale(0.35));
        Vec3 look = aim.subtract(position);
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
        if (blend < 1) {
            // from beside the head, not from inside it (the eyes): the glide never crosses the player's face
            Vec3 own = eyes.subtract(flat.scale(0.45)).add(right.scale(SIDE)).add(0, UP, 0);
            float ownYaw = p.getViewYRot(partialTick), ownPitch = p.getViewXRot(partialTick);
            position = own.lerp(position, blend);
            yaw = ownYaw + (float) (Mth.wrapDegrees(yaw - ownYaw) * blend);
            pitch = (float) Mth.lerp(blend, ownPitch, pitch);
        }
        return new FaceCutscene.Shot(position, yaw, pitch);
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    // The NPC's arms in the scene (client/NpcRenderer): the preview's gestures while its line types, its still pose
    // after; null when it is not the one talking. {right xRot, right zRot, left xRot, left zRot}
    public static float[] gesture(int entityId, float ageInTicks) {
        if (camNpc < 0 || camOut >= 0 || entityId != camNpc) {
            return null;
        }
        float u = (ageInTicks / 20f / 2.4f) % 1f;
        SkinDraw.Pose pose = IdlePoses.talking(u, talkingNow);
        return new float[] {pose.rarm[0], -pose.rarm[1], pose.larm[0], -pose.larm[1]};
    }
}
