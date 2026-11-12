package com.starkinhon.soulsdev.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.starkinhon.soulsdev.SoulsDev;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// Teclas simuladas: aperta a tecla de verdade pro jogo (KeyMapping), então passa pelo mesmo caminho do teclado do Stark
// (Keybinds do mod, Shift+tecla, segurar pra carregar). Nomes: letras/números (z, x, 1), shift, ctrl, alt, espaco,
// ataque, usar, frente, tras, esquerda, direita, pulo, ou o nome cru do Minecraft (key.keyboard.f5, key.mouse.left).
public final class Teclas {
    private static final Map<InputConstants.Key, Long> SOLTAR_EM = new HashMap<>();
    private static long agora;

    private Teclas() {
    }

    public static InputConstants.Key tecla(String nome) {
        Options o = Minecraft.getInstance().options;
        String n = nome.toLowerCase();
        return switch (n) {
            case "shift" -> o.keyShift.getKey();
            case "ctrl" -> o.keySprint.getKey();
            case "alt" -> InputConstants.getKey("key.keyboard.left.alt");
            case "espaco", "pulo" -> o.keyJump.getKey();
            case "ataque" -> o.keyAttack.getKey();
            case "usar" -> o.keyUse.getKey();
            case "frente" -> o.keyUp.getKey();
            case "tras" -> o.keyDown.getKey();
            case "esquerda" -> o.keyLeft.getKey();
            case "direita" -> o.keyRight.getKey();
            default -> porNome(n);
        };
    }

    private static InputConstants.Key porNome(String n) {
        if (n.length() == 1 && n.charAt(0) >= 'a' && n.charAt(0) <= 'z') {
            return InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_A + (n.charAt(0) - 'a'));
        }
        if (n.length() == 1 && Character.isDigit(n.charAt(0))) {
            return InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_0 + (n.charAt(0) - '0'));
        }
        if (n.matches("f([1-9]|1[0-2])")) {
            return InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F1 + Integer.parseInt(n.substring(1)) - 1);
        }
        try {
            return InputConstants.getKey(n.startsWith("key.") ? n : "key.keyboard." + n);
        } catch (RuntimeException e) {
            return InputConstants.UNKNOWN;
        }
    }

    // Aperta por N ticks (1 tick = 1/20 s). Apertar de novo uma tecla já segurada só estende o tempo.
    public static void apertar(String nome, int ticks) {
        InputConstants.Key key = tecla(nome);
        if (key == InputConstants.UNKNOWN) {
            throw new IllegalArgumentException("tecla desconhecida: " + nome);
        }
        boolean nova = !SOLTAR_EM.containsKey(key);
        SOLTAR_EM.put(key, agora + Math.max(1, ticks) + 1);
        KeyMapping.set(key, true);
        if (nova) {
            KeyMapping.click(key);
        }
        SoulsDev.log("tecla " + nome + " (" + key.getName() + ") por " + ticks + " ticks");
    }

    public static void soltarTodas() {
        for (InputConstants.Key key : SOLTAR_EM.keySet()) {
            KeyMapping.set(key, false);
        }
        SOLTAR_EM.clear();
    }

    @SubscribeEvent
    public static void antes(ClientTickEvent.Pre event) {
        agora++;
        // A janela sem foco solta as teclas: segura de novo.
        for (InputConstants.Key key : SOLTAR_EM.keySet()) {
            KeyMapping.set(key, true);
        }
    }

    @SubscribeEvent
    public static void depois(ClientTickEvent.Post event) {
        for (Iterator<Map.Entry<InputConstants.Key, Long>> it = SOLTAR_EM.entrySet().iterator(); it.hasNext();) {
            Map.Entry<InputConstants.Key, Long> e = it.next();
            if (agora >= e.getValue()) {
                KeyMapping.set(e.getKey(), false);
                it.remove();
            }
        }
    }
}
