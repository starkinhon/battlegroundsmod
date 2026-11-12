package com.starkinhon.soulsdev.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;

// Mouse de mentira pros roteiros: põe o cursor num ponto da tela (pixels de GUI) e clica na tela aberta.
// "alvo" acha as áreas clicáveis do Livro das Almas (PowerSelectScreen.hits, por reflexão) pelo que elas são.
final class TelaMouse {
    private static double gx = -1, gy = -1;

    private TelaMouse() {
    }

    static void mover(double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        gx = x;
        gy = y;
        double px = x * scale, py = y * scale;
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), px, py);
        try {
            for (String nome : new String[]{"xpos", "ypos"}) {
                Field f = MouseHandler.class.getDeclaredField(nome);
                f.setAccessible(true);
                f.setDouble(mc.mouseHandler, nome.equals("xpos") ? px : py);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        if (mc.screen != null) {
            mc.screen.mouseMoved(x, y);
        }
    }

    static String clicar(boolean apertar, boolean soltar) {
        Screen tela = Minecraft.getInstance().screen;
        if (tela == null) {
            return "nenhuma tela aberta";
        }
        if (apertar) {
            tela.mouseClicked(gx, gy, 0);
        }
        if (soltar) {
            tela.mouseReleased(gx, gy, 0);
        }
        return (apertar && soltar ? "clique" : apertar ? "segurando" : "soltou") + " em " + gx + " " + gy;
    }

    static String alvo(String tipo, String valor) {
        Screen tela = Minecraft.getInstance().screen;
        if (tela == null) {
            return "ERRO nenhuma tela aberta";
        }
        try {
            Field f = tela.getClass().getDeclaredField("hits");
            f.setAccessible(true);
            for (Object h : (List<?>) f.get(tela)) {
                if (casa(h, tipo, valor)) {
                    int x = (int) get(h, "x"), y = (int) get(h, "y"), w = (int) get(h, "w"), hh = (int) get(h, "h");
                    mover(x + w / 2.0, y + hh / 2.0);
                    return "mouse em " + tipo + " " + valor + " (" + (x + w / 2) + ", " + (y + hh / 2) + ")";
                }
            }
            return "ERRO alvo não está na tela: " + tipo + " " + valor;
        } catch (ReflectiveOperationException e) {
            return "ERRO esta tela não é o Livro das Almas (" + e + ")";
        }
    }

    private static Object get(Object rec, String nome) throws ReflectiveOperationException {
        Method m = rec.getClass().getDeclaredMethod(nome);
        m.setAccessible(true);
        return m.invoke(rec);
    }

    private static boolean casa(Object h, String tipo, String valor) throws ReflectiveOperationException {
        return switch (tipo) {
            case "alma" -> {
                Object p = get(h, "soul");
                yield p != null && !(boolean) get(h, "seal") && p.toString().contains("id=" + valor + ",");
            }
            case "aba" -> (int) get(h, "tab") == Integer.parseInt(valor.isEmpty() ? "0" : valor);
            case "traje" -> (int) get(h, "tab") == -2;
            case "constelacao" -> valor.equals(get(h, "cons"));
            case "voltar" -> (boolean) get(h, "back");
            case "selo" -> (boolean) get(h, "seal");
            case "despertar" -> (int) get(h, "awaken") >= 0;
            default -> false;
        };
    }
}
