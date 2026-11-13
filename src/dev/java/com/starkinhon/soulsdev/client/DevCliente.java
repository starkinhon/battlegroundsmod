package com.starkinhon.soulsdev.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.starkinhon.soulsdev.SoulsDev;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// /devc: comandos de teste do lado do cliente (não precisam de cheats). Ajuda: /devc ajuda.
public final class DevCliente {
    private static final String[] AJUDA = {
            "/devc roteiro <nome|caminho>  - roda soulsdev/roteiros/<nome>.txt (/devc roteiro parar)",
            "/devc tecla <tecla> [ticks]  - aperta de verdade: z x c v b, shift, alt, espaco, ataque, usar, frente...",
            "/devc camera lado|frente|tras <dist> [altura]  /  cima <dist>  - segue e olha pro jogador",
            "/devc camera duelo <dist> [altura]  - de lado, centrada entre você e o mob/boss mais perto",
            "/devc camera ponto <x> <y> <z>  /  fixa <x> <y> <z> <yaw> <pitch>  /  congelar  /  jogador",
            "/devc vista primeira|terceira|frente  - câmera normal do Minecraft (F5)",
            "/devc olhar <yaw> <pitch>  - gira o jogador",
            "/devc mirar  /  mirar seguir  /  mirar parar  - mira no mob/boss mais perto (seguir = todo tick)",
            "/devc forja <skill> [momento] [distancia]  - efeitos da Forja da skill, parados na sua frente",
            "/devc hud on|off   /devc chat on|off   /devc fov <30-110>   /devc janela <largura> <altura>",
            "/devc print <nome>  - PNG em _claude_shots (subpastas valem: luffy/z1)",
            "/devc gravar <nome> [cada N quadros]  /  gravar parar  - um PNG por quadro",
            "/devc marca <texto>  - escreve no latest.log (pra achar o momento depois)",
            "/devc fechar  - fecha a tela aberta   /devc sair  - fecha o jogo",
            "/devc mouse <x> <y>  - põe o mouse nesse ponto da tela (pixels de GUI)",
            "/devc alvo <alma <id>|aba <n>|constelacao <id>|voltar|selo|despertar|traje>  - mouse em cima disso no Livro das Almas",
            "/devc clique  /  segurar  /  soltar  - botão esquerdo na tela aberta"};

    private DevCliente() {
    }

    @SubscribeEvent
    public static void registrar(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("devc");
        root.executes(c -> ajuda(c.getSource()));
        root.then(Commands.literal("ajuda").executes(c -> ajuda(c.getSource())));
        root.then(Commands.literal("roteiro")
                .then(Commands.literal("parar").executes(c -> ok(c, Roteiro.parar())))
                .then(Commands.argument("nome", StringArgumentType.greedyString()).executes(c -> {
                    try {
                        return ok(c, "roteiro " + Roteiro.iniciar(StringArgumentType.getString(c, "nome"), 0));
                    } catch (Exception e) {
                        return erro(c, e.getMessage());
                    }
                })));
        root.then(Commands.literal("tecla")
                .then(Commands.argument("tecla", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("z", "x", "c", "v", "b", "shift", "alt", "espaco",
                                "ataque", "usar", "frente", "tras", "esquerda", "direita", "g", "r", "f", "k", "h"), b))
                        .executes(c -> tecla(c, 2))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(c -> tecla(c, IntegerArgumentType.getInteger(c, "ticks"))))));
        LiteralArgumentBuilder<CommandSourceStack> camera = Commands.literal("camera");
        for (String nome : List.of("lado", "frente", "tras", "duelo")) {
            camera.then(Commands.literal(nome)
                    .then(Commands.argument("dist", DoubleArgumentType.doubleArg(0.5, 200))
                            .executes(c -> camera(c, nome, DoubleArgumentType.getDouble(c, "dist"), 0))
                            .then(Commands.argument("altura", DoubleArgumentType.doubleArg(-100, 200))
                                    .executes(c -> camera(c, nome, DoubleArgumentType.getDouble(c, "dist"),
                                            DoubleArgumentType.getDouble(c, "altura"))))));
        }
        camera.then(Commands.literal("cima")
                .then(Commands.argument("dist", DoubleArgumentType.doubleArg(0.5, 300))
                        .executes(c -> camera(c, "cima", DoubleArgumentType.getDouble(c, "dist"), 0))));
        camera.then(Commands.literal("ponto")
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg()).executes(c -> {
                                    CameraDev.ponto(xyz(c));
                                    return ok(c, "câmera no ponto");
                                })))));
        camera.then(Commands.literal("fixa")
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                                .then(Commands.argument("pitch", FloatArgumentType.floatArg(-90, 90)).executes(c -> {
                                                    CameraDev.fixa(xyz(c), FloatArgumentType.getFloat(c, "yaw"), FloatArgumentType.getFloat(c, "pitch"));
                                                    return ok(c, "câmera fixa");
                                                })))))));
        camera.then(Commands.literal("congelar").executes(c -> {
            CameraDev.congelar();
            return ok(c, "câmera congelada");
        }));
        camera.then(Commands.literal("jogador").executes(c -> {
            CameraDev.desligar();
            return ok(c, "câmera normal");
        }));
        root.then(camera);
        root.then(Commands.literal("vista")
                .then(Commands.literal("primeira").executes(c -> vista(c, CameraType.FIRST_PERSON)))
                .then(Commands.literal("terceira").executes(c -> vista(c, CameraType.THIRD_PERSON_BACK)))
                .then(Commands.literal("frente").executes(c -> vista(c, CameraType.THIRD_PERSON_FRONT))));
        root.then(Commands.literal("olhar")
                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(-90, 90)).executes(c -> {
                            Player p = Minecraft.getInstance().player;
                            float yaw = FloatArgumentType.getFloat(c, "yaw");
                            float pitch = FloatArgumentType.getFloat(c, "pitch");
                            p.setYRot(yaw);
                            p.setXRot(pitch);
                            p.yRotO = yaw;
                            p.xRotO = pitch;
                            p.setYHeadRot(yaw);
                            return ok(c, "olhar " + yaw + " " + pitch);
                        }))));
        root.then(Commands.literal("mirar")
                .executes(c -> {
                    String alvo = Mira.mirar();
                    return alvo == null ? erro(c, "nenhum mob perto") : ok(c, "mirando " + alvo);
                })
                .then(Commands.literal("seguir").executes(c -> {
                    Mira.seguir(true);
                    return ok(c, "mira seguindo o mais perto");
                }))
                .then(Commands.literal("parar").executes(c -> {
                    Mira.seguir(false);
                    return ok(c, "mira parada");
                })));
        root.then(Commands.literal("forja")
                .then(Commands.argument("skill", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ForjaFx.skills(), b))
                        .executes(c -> forja(c, null, 4))
                        .then(Commands.argument("momento", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ForjaFx.momentos(StringArgumentType.getString(c, "skill")), b))
                                .executes(c -> forja(c, StringArgumentType.getString(c, "momento"), 4))
                                .then(Commands.argument("distancia", DoubleArgumentType.doubleArg(0, 64))
                                        .executes(c -> forja(c, StringArgumentType.getString(c, "momento"),
                                                DoubleArgumentType.getDouble(c, "distancia")))))));
        root.then(Commands.literal("hud")
                .then(Commands.literal("on").executes(c -> hud(c, true)))
                .then(Commands.literal("off").executes(c -> hud(c, false))));
        // chat escondido nas gravações (2ª rodada do #318: o chat cobria o personagem em 854x480)
        root.then(Commands.literal("chat")
                .then(Commands.literal("on").executes(c -> chat(c, true)))
                .then(Commands.literal("off").executes(c -> chat(c, false))));
        root.then(Commands.literal("fov")
                .then(Commands.argument("graus", IntegerArgumentType.integer(30, 110)).executes(c -> {
                    int graus = IntegerArgumentType.getInteger(c, "graus");
                    Minecraft.getInstance().options.fov().set(graus);
                    return ok(c, "fov " + graus);
                })));
        root.then(Commands.literal("janela")
                .then(Commands.argument("largura", IntegerArgumentType.integer(320, 7680))
                        .then(Commands.argument("altura", IntegerArgumentType.integer(240, 4320)).executes(c -> {
                            int w = IntegerArgumentType.getInteger(c, "largura");
                            int h = IntegerArgumentType.getInteger(c, "altura");
                            GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(), w, h);
                            return ok(c, "janela " + w + "x" + h);
                        }))));
        root.then(Commands.literal("print")
                .then(Commands.argument("nome", StringArgumentType.greedyString())
                        .executes(c -> ok(c, "print " + Gravador.print(StringArgumentType.getString(c, "nome"))))));
        // nome com "/" (subpasta) precisa de texto livre: o último pedaço, se for número, é o "cada N quadros".
        root.then(Commands.literal("gravar")
                .then(Commands.literal("parar").executes(c -> ok(c, Gravador.parar())))
                .then(Commands.argument("nome", StringArgumentType.greedyString()).executes(c -> {
                    String[] partes = StringArgumentType.getString(c, "nome").trim().split("\\s+");
                    int cada = 1;
                    String nome = partes[0];
                    if (partes.length > 1) {
                        try {
                            cada = Integer.parseInt(partes[partes.length - 1]);
                        } catch (NumberFormatException e) {
                            return erro(c, "gravar <nome> [cada N quadros]");
                        }
                    }
                    return ok(c, "gravando em " + Gravador.gravar(nome, Math.max(1, Math.min(60, cada))));
                })));
        root.then(Commands.literal("marca")
                .then(Commands.argument("texto", StringArgumentType.greedyString())
                        .executes(c -> ok(c, "MARCA " + StringArgumentType.getString(c, "texto")))));
        root.then(Commands.literal("mouse")
                .then(Commands.argument("x", DoubleArgumentType.doubleArg(0, 10000))
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg(0, 10000))
                                .executes(c -> {
                                    TelaMouse.mover(DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"));
                                    return ok(c, "mouse em " + DoubleArgumentType.getDouble(c, "x") + " " + DoubleArgumentType.getDouble(c, "y"));
                                }))));
        root.then(Commands.literal("alvo")
                .then(Commands.argument("tipo", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("alma", "aba", "constelacao", "voltar", "selo", "despertar", "traje"), b))
                        .executes(c -> alvo(c, StringArgumentType.getString(c, "tipo"), ""))
                        .then(Commands.argument("valor", StringArgumentType.word())
                                .executes(c -> alvo(c, StringArgumentType.getString(c, "tipo"), StringArgumentType.getString(c, "valor"))))));
        root.then(Commands.literal("clique").executes(c -> ok(c, TelaMouse.clicar(true, true))));
        root.then(Commands.literal("segurar").executes(c -> ok(c, TelaMouse.clicar(true, false))));
        root.then(Commands.literal("soltar").executes(c -> ok(c, TelaMouse.clicar(false, true))));
        root.then(Commands.literal("fechar").executes(c -> {
            Minecraft.getInstance().setScreen(null);
            return ok(c, "tela fechada");
        }));
        root.then(Commands.literal("sair").executes(c -> {
            Gravador.parar();
            SoulsDev.log("devc sair: fechando o jogo");
            Minecraft.getInstance().stop();
            return 1;
        }));
        d.register(root);
    }

    private static int ajuda(CommandSourceStack source) {
        for (String linha : AJUDA) {
            source.sendSuccess(() -> Component.literal(linha), false);
        }
        return AJUDA.length;
    }

    private static int alvo(CommandContext<CommandSourceStack> c, String tipo, String valor) {
        String r = TelaMouse.alvo(tipo, valor);
        return r.startsWith("ERRO") ? erro(c, r) : ok(c, r);
    }

    private static int ok(CommandContext<CommandSourceStack> c, String texto) {
        SoulsDev.log(texto);
        c.getSource().sendSuccess(() -> Component.literal("[devc] " + texto), false);
        return 1;
    }

    private static int erro(CommandContext<CommandSourceStack> c, String texto) {
        SoulsDev.LOGGER.warn("[soulsdev] ERRO {}", texto);
        c.getSource().sendFailure(Component.literal("[devc] " + texto));
        return 0;
    }

    private static Vec3 xyz(CommandContext<CommandSourceStack> c) {
        return new Vec3(DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"), DoubleArgumentType.getDouble(c, "z"));
    }

    private static int tecla(CommandContext<CommandSourceStack> c, int ticks) {
        String nome = StringArgumentType.getString(c, "tecla");
        try {
            Teclas.apertar(nome, ticks);
        } catch (IllegalArgumentException e) {
            return erro(c, e.getMessage());
        }
        return ok(c, "tecla " + nome + " " + ticks + " ticks");
    }

    private static int camera(CommandContext<CommandSourceStack> c, String nome, double dist, double altura) {
        CameraDev.relativa(nome, dist, altura);
        return ok(c, "câmera " + nome + " " + dist + (altura != 0 ? " altura " + altura : ""));
    }

    private static int forja(CommandContext<CommandSourceStack> c, String momento, double dist) {
        String skill = StringArgumentType.getString(c, "skill");
        try {
            return ok(c, ForjaFx.tocar(skill, momento, dist));
        } catch (RuntimeException e) {
            return erro(c, "forja " + skill + ": " + e.getMessage());
        }
    }

    private static int vista(CommandContext<CommandSourceStack> c, CameraType tipo) {
        CameraDev.desligar();
        Minecraft.getInstance().options.setCameraType(tipo);
        return ok(c, "vista " + tipo);
    }

    private static int hud(CommandContext<CommandSourceStack> c, boolean on) {
        Minecraft.getInstance().options.hideGui = !on;
        return ok(c, "hud " + (on ? "on" : "off"));
    }

    private static boolean semChat;

    // Esconde só a camada do chat (sem mexer na visibilidade do chat, que no servidor barra os comandos do roteiro).
    @SubscribeEvent
    public static void camada(RenderGuiLayerEvent.Pre event) {
        if (semChat && event.getName().equals(VanillaGuiLayers.CHAT) && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
        }
    }

    private static int chat(CommandContext<CommandSourceStack> c, boolean on) {
        int r = ok(c, "chat " + (on ? "on" : "off"));
        semChat = !on;
        if (!on) {
            Minecraft.getInstance().gui.getChat().clearMessages(false);
        }
        return r;
    }
}
