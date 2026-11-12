# Souls Dev: ferramentas de teste

Mod à parte, **só de teste** (`src/dev`, modid `soulsdev`). Entra no `runClient` e no jar separado
`build/devlibs/soulsdev-*.jar` (artifact do CI `soulsdev-jar-so-testes`). **Nunca** vai no jar de lançamento
(`build/libs`), então não precisa tirar nada antes de lançar. `-PnoDevMods` deixa ele de fora.

## Abrir o jogo sozinho e rodar um roteiro

```
gradlew runClient -Pmundo=SoulsDev -Proteiro=exemplo-boss-travado
```

- `-Pmundo=<nome>`: abre esse mundo direto; se não existir, cria (Criativo, cheats, semente 1, plano).
- `-PmundoTipo=plano|normal|<preset>` (ex. `battlegroundsmod:souls`): tipo do mundo novo.
- `-Proteiro=<nome|caminho>`: roda ao entrar no mundo. Procura `run/soulsdev/roteiros/<nome>.txt`, `tools/roteiros/<nome>.txt` ou caminho completo.
- `-Pshots=<pasta>`: onde salvar prints e quadros. Padrão: `S:/Souls/Claude/_claude_shots` se existir, senão `run/soulsdev_shots`.
- Com mundo/roteiro, o jogo não pausa ao perder o foco.

Roteiro: um comando por linha (com ou sem `/`), `# comentário`, `espera <ticks>`, `sair`. Tudo vai pro
`run/logs/latest.log` com `[soulsdev]` e o tick do cliente.

## /dev (servidor, precisa de cheats)

| Comando | O que faz |
|---|---|
| `/dev travar <alvos>` | parado no lugar, sem IA, sem alvo, não machuca ninguém; ainda anima e toma dano, sem knockback |
| `/dev estatua <alvos>` | congelado total (nem o tick roda) |
| `/dev passivo <alvos>` | anda normal, nunca ataca |
| `/dev imortal <alvos>` | toma dano, 1 s depois volta com vida cheia, nunca morre (soma com os outros) |
| `/dev soltar [alvos]` | tira tudo (sem alvos: o mundo todo) |
| `/dev invocar <entidade> [dist] [solto]` | nasce na sua frente olhando pra você, já travado |
| `/dev skill <z\|x\|c\|v\|b> [shift] [quem]` | solta a skill (jogador = igual à tecla; boss/mob = pelo poder dele) |
| `/dev mirar <alvo>` | vira você pro peito do alvo |
| `/dev acertar <tecla> [shift] <alvo>` | mira e solta a skill na hora |
| `/dev atordoar <alvos> [ticks]` | stun do mod (padrão 60) |
| `/dev selar <alvos> [ticks]` | sem skills (padrão 100) |
| `/dev fx <efeito> [a] [b] [c] [dist] [altura] [ticks]` | efeito das almas (SubNet: genki, ki_ball, getsuga, death_ball...) PARADO na frente. a/b/c: ver `SubNet.java`. ticks 0 = uma vez. `/dev fx parar` |
| `/dev repetir <ticks> <comando>` / `parar` | roda o comando a cada N ticks |
| `/dev depois <ticks> <comando>` | roda uma vez daqui N ticks |
| `/dev preparar` | meio-dia fixo, sem chuva, sem mobs nascendo |
| `/dev info <alvos>` | vida, posição, alvo, travas (no log também) |

Os modos são tags: `@e[tag=soulsdev.prender]`, `soulsdev.estatua`, `soulsdev.passivo`, `soulsdev.imortal`.
Efeito comum (do jogo ou do mod): `/effect give <alvo> <efeito> <segundos> <nível>`.

## /devc (cliente, sem cheats)

| Comando | O que faz |
|---|---|
| `/devc roteiro <nome>` / `parar` | roda um roteiro |
| `/devc tecla <tecla> [ticks]` | aperta de verdade (z x c v b, shift, alt, espaco, ataque, usar, frente, tras, letras, f1-f12); segurar = mais ticks |
| `/devc camera lado\|frente\|tras <dist> [altura]` | segue e olha pro jogador, girada pelo rumo dele na hora |
| `/devc camera cima <dist>` | de cima |
| `/devc camera duelo <dist> [altura]` | de lado, centrada entre você e o mob/boss mais perto (cabem os dois) |
| `/devc camera ponto <x> <y> <z>` | parada, olhando pro jogador |
| `/devc camera fixa <x> <y> <z> <yaw> <pitch>` / `congelar` / `jogador` | fixa / fica onde está / volta ao normal |
| `/devc vista primeira\|terceira\|frente` | câmera normal (F5) |
| `/devc olhar <yaw> <pitch>` | gira o jogador |
| `/devc mirar` / `mirar seguir` / `mirar parar` | mira no mob/boss mais perto (seguir = todo tick, pra skill segurada) |
| `/devc forja <skill> [momento] [dist]` | toca a lista vfx da Forja da skill parada na frente |
| `/devc hud on\|off`, `fov <n>`, `janela <w> <h>` | tela |
| `/devc print <nome>` | PNG (subpasta vale: `luffy/z1`) |
| `/devc gravar <nome> [cada N quadros de tela, não ticks]` / `parar` | um PNG por quadro: `f00000_t000123.png` (quadro e tick) |
| `/devc marca <texto>` | marca no log |
| `/devc fechar` / `sair` | fecha a tela aberta / fecha o jogo |

Montar vídeo dos quadros (na nuvem):
`ffmpeg -framerate 30 -pattern_type glob -i 'f*.png' -pix_fmt yuv420p video.mp4`
