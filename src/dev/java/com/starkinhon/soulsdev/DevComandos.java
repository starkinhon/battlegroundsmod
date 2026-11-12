package com.starkinhon.soulsdev;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.Powers;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// /dev: comandos de teste do lado do servidor (precisa de cheats / op). Ajuda: /dev ajuda.
public final class DevComandos {
    private static final List<String> TECLAS = List.of("z", "x", "c", "v", "b");

    // Comandos agendados por /dev repetir e /dev depois.
    private static final List<Agendado> AGENDA = new ArrayList<>();

    private record Agendado(CommandSourceStack source, String comando, int cada, int[] falta, int[] vezes) {
    }

    private DevComandos() {
    }

    private static final String[] AJUDA = {
            "/dev travar <alvos>  - parado no lugar, sem IA, sem atacar (ainda anima e toma dano)",
            "/dev estatua <alvos>  - congelado total (nem o tick roda)",
            "/dev passivo <alvos>  - anda normal mas nunca ataca",
            "/dev imortal <alvos>  - toma dano e volta com vida cheia, nunca morre",
            "/dev soltar [alvos]  - tira tudo (sem alvos: todos do mundo)",
            "/dev invocar <entidade> [distancia] [solto]  - nasce na sua frente, olhando pra você, já travado",
            "/dev skill <z|x|c|v|b> [shift] [quem]  - solta a skill (boss/mob: pelo poder dele; jogador: igual à tecla)",
            "/dev mirar <alvo>  - vira você pro peito do alvo (a mira vai pro cliente também)",
            "/dev acertar <z|x|c|v|b> [shift] <alvo>  - mira no alvo e solta a skill na hora",
            "/dev atordoar <alvos> [ticks]  - stun do mod (padrão 60)   /dev selar <alvos> [ticks]  - sem skills",
            "  (efeito comum do jogo ou do mod: /effect give <alvo> <efeito> <segundos> <nível>)",
            "/dev fx <efeito> [a] [b] [c] [distancia] [altura] [ticks]  - efeito das almas PARADO na sua frente",
            "  ex.: /dev fx genki 3 0 0 10 3 400 (Genkidama). a/b/c: ver SubNet.java. ticks 0 = uma vez. /dev fx parar",
            "/dev repetir <ticks> <comando>  - roda o comando a cada N ticks (/dev repetir parar)",
            "/dev depois <ticks> <comando>  - roda o comando uma vez daqui N ticks",
            "/dev preparar  - dia fixo, sem chuva, sem mobs nascendo",
            "/dev info <alvos>  - vida, posição, alvo, travas (também vai pro latest.log)",
            "Cliente: /devc ajuda (roteiro, teclas, câmera, print, gravar)"};

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext context = event.getBuildContext();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dev").requires(s -> s.hasPermission(2));
        root.executes(c -> ajuda(c.getSource()));
        root.then(Commands.literal("ajuda").executes(c -> ajuda(c.getSource())));
        root.then(modo("travar", DevTravas.PRENDER));
        root.then(modo("prender", DevTravas.PRENDER));
        root.then(modo("estatua", DevTravas.ESTATUA));
        root.then(modo("passivo", DevTravas.PASSIVO));
        root.then(modo("imortal", DevTravas.IMORTAL));
        root.then(Commands.literal("soltar")
                .executes(c -> soltarTudo(c.getSource()))
                .then(Commands.argument("alvos", EntityArgument.entities())
                        .executes(c -> soltar(c.getSource(), EntityArgument.getEntities(c, "alvos")))));
        root.then(Commands.literal("invocar")
                .then(Commands.argument("entidade", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                        .suggests(net.minecraft.commands.synchronization.SuggestionProviders.SUMMONABLE_ENTITIES)
                        .executes(c -> invocar(c, 5, true))
                        .then(Commands.argument("distancia", DoubleArgumentType.doubleArg(0, 64))
                                .executes(c -> invocar(c, DoubleArgumentType.getDouble(c, "distancia"), true))
                                .then(Commands.literal("solto")
                                        .executes(c -> invocar(c, DoubleArgumentType.getDouble(c, "distancia"), false))))));
        root.then(Commands.literal("skill")
                .then(Commands.argument("tecla", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(TECLAS, b))
                        .executes(c -> skill(c, false, null))
                        .then(Commands.literal("shift")
                                .executes(c -> skill(c, true, null))
                                .then(Commands.argument("quem", EntityArgument.entities())
                                        .executes(c -> skill(c, true, EntityArgument.getEntities(c, "quem")))))
                        .then(Commands.argument("quem", EntityArgument.entities())
                                .executes(c -> skill(c, false, EntityArgument.getEntities(c, "quem"))))));
        root.then(Commands.literal("mirar")
                .then(Commands.argument("alvo", EntityArgument.entity())
                        .executes(c -> mirar(c.getSource(), EntityArgument.getEntity(c, "alvo")) ? 1 : 0)));
        root.then(Commands.literal("acertar")
                .then(Commands.argument("tecla", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(TECLAS, b))
                        .then(Commands.literal("shift")
                                .then(Commands.argument("alvo", EntityArgument.entity())
                                        .executes(c -> acertar(c, true))))
                        .then(Commands.argument("alvo", EntityArgument.entity())
                                .executes(c -> acertar(c, false)))));
        root.then(Commands.literal("atordoar")
                .then(Commands.argument("alvos", EntityArgument.entities())
                        .executes(c -> atordoar(c.getSource(), EntityArgument.getEntities(c, "alvos"), 60))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(c -> atordoar(c.getSource(), EntityArgument.getEntities(c, "alvos"),
                                        IntegerArgumentType.getInteger(c, "ticks"))))));
        root.then(Commands.literal("selar")
                .then(Commands.argument("alvos", EntityArgument.entities())
                        .executes(c -> selar(c.getSource(), EntityArgument.getEntities(c, "alvos"), 100))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(c -> selar(c.getSource(), EntityArgument.getEntities(c, "alvos"),
                                        IntegerArgumentType.getInteger(c, "ticks"))))));
        root.then(fx());
        root.then(Commands.literal("repetir")
                .then(Commands.literal("parar").executes(c -> parar(c.getSource())))
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .then(Commands.argument("comando", StringArgumentType.greedyString())
                                .executes(c -> agendar(c, IntegerArgumentType.getInteger(c, "ticks"), -1)))));
        root.then(Commands.literal("depois")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                        .then(Commands.argument("comando", StringArgumentType.greedyString())
                                .executes(c -> agendar(c, IntegerArgumentType.getInteger(c, "ticks"), 1)))));
        root.then(Commands.literal("preparar").executes(c -> preparar(c.getSource())));
        root.then(Commands.literal("info")
                .then(Commands.argument("alvos", EntityArgument.entities())
                        .executes(c -> info(c.getSource(), EntityArgument.getEntities(c, "alvos")))));
        dispatcher.register(root);
    }

    private static int ajuda(CommandSourceStack source) {
        for (String linha : AJUDA) {
            source.sendSuccess(() -> Component.literal(linha), false);
        }
        return AJUDA.length;
    }

    private static void dizer(CommandSourceStack source, String texto) {
        SoulsDev.log(texto);
        source.sendSuccess(() -> Component.literal("[dev] " + texto), false);
    }

    private static void falhar(CommandSourceStack source, String texto) {
        SoulsDev.log("FALHOU: " + texto);
        source.sendFailure(Component.literal("[dev] " + texto));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> modo(String nome, String tag) {
        return Commands.literal(nome)
                .then(Commands.argument("alvos", EntityArgument.entities())
                        .executes(c -> aplicar(c.getSource(), EntityArgument.getEntities(c, "alvos"), tag, nome)));
    }

    private static int aplicar(CommandSourceStack source, Collection<? extends Entity> alvos, String tag, String nome) {
        int n = 0;
        for (Entity e : alvos) {
            if (e instanceof Player) {
                falhar(source, "jogador não pode ser travado: " + e.getName().getString());
                continue;
            }
            if (e instanceof LivingEntity living) {
                DevTravas.aplicar(living, tag);
                n++;
            }
        }
        int total = n;
        dizer(source, nome + ": " + total + " entidade(s)");
        return total;
    }

    private static int soltar(CommandSourceStack source, Collection<? extends Entity> alvos) {
        int n = 0;
        for (Entity e : alvos) {
            if (DevTravas.soltar(e)) {
                n++;
            }
        }
        int total = n;
        dizer(source, "soltar: " + total + " entidade(s)");
        return total;
    }

    private static int soltarTudo(CommandSourceStack source) {
        int n = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (DevTravas.temAlguma(e) && DevTravas.soltar(e)) {
                    n++;
                }
            }
        }
        int total = n;
        dizer(source, "soltar tudo: " + total + " entidade(s)");
        return total;
    }

    private static int invocar(CommandContext<CommandSourceStack> c, double distancia, boolean travar) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        EntityType<?> type = ResourceArgument.getSummonableEntityType(c, "entidade").value();
        ServerLevel level = source.getLevel();
        Vec3 origem = source.getPosition();
        float yaw = source.getRotation().y;
        Vec3 frente = Vec3.directionFromRotation(0, yaw);
        Vec3 pos = origem.add(frente.scale(distancia));
        Entity e = type.create(level);
        if (e == null) {
            falhar(source, "não deu pra criar " + type.getDescriptionId());
            return 0;
        }
        float olhando = yaw + 180;
        e.moveTo(pos.x, pos.y, pos.z, olhando, 0);
        if (e instanceof Mob mob) {
            mob.setYHeadRot(olhando);
            mob.setYBodyRot(olhando);
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.COMMAND, null);
        }
        if (!level.tryAddFreshEntityWithPassengers(e)) {
            falhar(source, "entidade repetida (UUID)");
            return 0;
        }
        if (travar && e instanceof LivingEntity living) {
            DevTravas.aplicar(living, DevTravas.PRENDER);
        }
        dizer(source, "invocar: " + e.getName().getString() + " id=" + e.getId() + (travar ? " (travado)" : " (solto)")
                + String.format(" em %.1f %.1f %.1f", pos.x, pos.y, pos.z));
        return e.getId();
    }

    private static int skill(CommandContext<CommandSourceStack> c, boolean shift, Collection<? extends Entity> quem) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        String tecla = StringArgumentType.getString(c, "tecla").toLowerCase();
        int key = TECLAS.indexOf(tecla);
        if (key < 0) {
            falhar(source, "tecla inválida: " + tecla + " (use z, x, c, v ou b)");
            return 0;
        }
        Collection<? extends Entity> alvos = quem != null ? quem : List.of(source.getEntityOrException());
        int n = 0;
        for (Entity e : alvos) {
            if (!(e instanceof LivingEntity living)) {
                continue;
            }
            PowerData data = living.getData(ModAttachments.POWER);
            Power power = Powers.get(data.power);
            if (power == null) {
                falhar(source, e.getName().getString() + " não tem poder");
                continue;
            }
            int slot = power.keySlot(key, shift && !power.shiftSlots().isEmpty());
            if (slot < 0 || slot >= PowerData.SLOT_COUNT) {
                falhar(source, power.id() + " não tem " + (shift ? "Shift+" : "") + tecla.toUpperCase());
                continue;
            }
            String nome = power.abilityName(slot).getString();
            if (living instanceof ServerPlayer player) {
                Tasks.as(player, () -> PowerLogic.tryUse(player, slot));
                dizer(source, "skill " + (shift ? "Shift+" : "") + tecla.toUpperCase() + " (" + nome + ") pedida por " + player.getName().getString());
                n++;
            } else {
                boolean[] ok = {false};
                // Travado não solta nada (sem IA); a skill pedida aqui roda mesmo assim, mas o dano dele continua
                // bloqueado se estiver travado/passivo. Pra ver o boss acertar, solte antes.
                Tasks.as(living, () -> ok[0] = PowerLogic.cast(living, slot));
                dizer(source, "skill " + (shift ? "Shift+" : "") + tecla.toUpperCase() + " (" + nome + ") de " + e.getName().getString()
                        + (ok[0] ? ": soltou" : ": não soltou (recarga, ocupado, sem alvo?)"));
                if (ok[0]) {
                    n++;
                }
            }
        }
        return n;
    }

    // Vira quem mandou o comando pro peito do alvo, no servidor e no cliente (teleporte só de rotação).
    private static boolean mirar(CommandSourceStack source, Entity alvo) throws CommandSyntaxException {
        Entity eu = source.getEntityOrException();
        Vec3 peito = alvo.position().add(0, alvo.getBbHeight() * 0.6, 0);
        eu.lookAt(EntityAnchorArgument.Anchor.EYES, peito);
        if (eu instanceof ServerPlayer player) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
        dizer(source, String.format("mirar %s: yaw=%.1f pitch=%.1f dist=%.1f", alvo.getName().getString(), eu.getYRot(), eu.getXRot(),
                eu.distanceTo(alvo)));
        return true;
    }

    private static int acertar(CommandContext<CommandSourceStack> c, boolean shift) throws CommandSyntaxException {
        mirar(c.getSource(), EntityArgument.getEntity(c, "alvo"));
        return skill(c, shift, null);
    }

    private static int atordoar(CommandSourceStack source, Collection<? extends Entity> alvos, int ticks) {
        int n = 0;
        for (Entity e : alvos) {
            if (e instanceof LivingEntity living) {
                Stun.apply(living, ticks, true);
                n++;
            }
        }
        dizer(source, "atordoar " + ticks + " ticks: " + n + " entidade(s)");
        return n;
    }

    private static int selar(CommandSourceStack source, Collection<? extends Entity> alvos, int ticks) {
        int n = 0;
        for (Entity e : alvos) {
            if (e instanceof LivingEntity living) {
                PowerLogic.seal(living, ticks);
                n++;
            }
        }
        dizer(source, "selar " + ticks + " ticks: " + n + " entidade(s)");
        return n;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> fx() {
        float[] padrao = {2, 0, 0, 6, 1.6f, 200};
        String[] nomes = {"a", "b", "c", "distancia", "altura", "ticks"};
        com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> ultimo = null;
        // Monta de trás pra frente: tipo [a [b [c [distancia [altura [ticks]]]]]].
        for (int i = nomes.length; i >= 0; i--) {
            final int n = i;
            com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> arg = i == 0
                    ? Commands.argument("efeito", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(DevFx.nomes(), b))
                    : i == nomes.length ? Commands.argument(nomes[i - 1], IntegerArgumentType.integer(0))
                    : Commands.argument(nomes[i - 1], com.mojang.brigadier.arguments.FloatArgumentType.floatArg());
            arg.executes(c -> fx(c, n, padrao, nomes));
            if (ultimo != null) {
                arg.then(ultimo);
            }
            ultimo = arg;
        }
        return Commands.literal("fx")
                .then(Commands.literal("parar").executes(c -> {
                    dizer(c.getSource(), "fx parar: " + DevFx.parar() + " efeito(s)");
                    return 1;
                }))
                .then(ultimo);
    }

    private static int fx(CommandContext<CommandSourceStack> c, int dados, float[] padrao, String[] nomes) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        String nome = StringArgumentType.getString(c, "efeito");
        int kind = DevFx.tipo(nome);
        if (kind < 0) {
            falhar(source, "efeito desconhecido: " + nome);
            return 0;
        }
        float[] v = padrao.clone();
        for (int i = 0; i < dados; i++) {
            v[i] = i == nomes.length - 1 ? IntegerArgumentType.getInteger(c, nomes[i])
                    : com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, nomes[i]);
        }
        Entity eu = source.getEntityOrException();
        Vec3 frente = Vec3.directionFromRotation(0, eu.getYRot());
        Vec3 pos = eu.position().add(frente.scale(v[3])).add(0, v[4], 0);
        int id = DevFx.tocar(source.getLevel(), kind, pos, frente, v[0], v[1], v[2], (int) v[5]);
        dizer(source, String.format("fx %s (%d) id=%d a=%s b=%s c=%s em %.1f %.1f %.1f por %d ticks", nome, kind, id, v[0], v[1], v[2],
                pos.x, pos.y, pos.z, (int) v[5]));
        return id;
    }

    private static int agendar(CommandContext<CommandSourceStack> c, int ticks, int vezes) {
        String comando = StringArgumentType.getString(c, "comando");
        if (comando.startsWith("/")) {
            comando = comando.substring(1);
        }
        CommandSourceStack source = c.getSource();
        AGENDA.add(new Agendado(source, comando, ticks, new int[] {ticks}, new int[] {vezes}));
        dizer(source, (vezes == 1 ? "depois " + ticks + " ticks: " : "a cada " + ticks + " ticks: ") + comando);
        return 1;
    }

    private static int parar(CommandSourceStack source) {
        int n = AGENDA.size();
        AGENDA.clear();
        dizer(source, "repetir parar: " + n + " comando(s) cancelado(s)");
        return n;
    }

    private static int preparar(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        CommandSourceStack quieto = source.withSuppressedOutput().withPermission(4);
        for (String cmd : List.of("time set 6000", "weather clear 1000000", "gamerule doDaylightCycle false",
                "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamerule doPatrolSpawning false",
                "gamerule doTraderSpawning false", "gamerule doInsomnia false")) {
            server.getCommands().performPrefixedCommand(quieto, cmd);
        }
        dizer(source, "preparar: meio-dia fixo, sem chuva, sem mobs nascendo");
        return 1;
    }

    private static int info(CommandSourceStack source, Collection<? extends Entity> alvos) {
        for (Entity e : alvos) {
            StringBuilder s = new StringBuilder();
            s.append(e.getName().getString()).append(" id=").append(e.getId())
                    .append(String.format(" pos=%.2f %.2f %.2f", e.getX(), e.getY(), e.getZ()));
            if (e instanceof LivingEntity living) {
                s.append(String.format(" vida=%.1f/%.1f", living.getHealth(), living.getMaxHealth()));
                PowerData data = living.getData(ModAttachments.POWER);
                if (!data.power.isEmpty()) {
                    s.append(" poder=").append(data.power).append(" ocupado=").append(data.busyTicks);
                }
            }
            if (e instanceof Mob mob) {
                LivingEntity alvo = mob.getTarget();
                s.append(" alvo=").append(alvo == null ? "-" : alvo.getName().getString()).append(" semIA=").append(mob.isNoAi());
            }
            s.append(" travas=").append(DevTravas.descrever(e));
            dizer(source, s.toString());
        }
        return alvos.size();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (AGENDA.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        // Cópia: um comando agendado pode agendar outro (ou parar todos).
        List<Agendado> agora = new ArrayList<>();
        for (Agendado a : AGENDA) {
            if (--a.falta()[0] <= 0) {
                agora.add(a);
                a.falta()[0] = a.cada();
            }
        }
        for (Agendado a : agora) {
            if (a.vezes()[0] > 0) {
                a.vezes()[0]--;
            }
            server.getCommands().performPrefixedCommand(a.source(), a.comando());
        }
        for (Iterator<Agendado> it = AGENDA.iterator(); it.hasNext();) {
            if (it.next().vezes()[0] == 0) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void parou(ServerStoppingEvent event) {
        AGENDA.clear();
    }
}
