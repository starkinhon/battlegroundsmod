# Catálogo de Subalmas — Dano, Recarga e Controle

Levantamento técnico de todas as **21 subalmas** registradas em `Powers.java` (`registerSub`) e das **técnicas ensinadas** (`Powers.INATA`), sem alteração de código.

## Fórmula de Dano Real (mesmo tier, sem guarda)

Valores de `padrao.tiers.json`, `Tiers.java` e `Combat.java`:

- **`Combat.hurt` / `Combat.hurtMagic` (subalmas):** `base × 5,5 (multSubAlmas) × Tiers.health(tier) / 850 × (1 − resistenciaPorTier)`
- **`Combat.hurtBase` / `hurtBasic`:** sem o `× 5,5` (`hurtBarrage` ainda `× 0,28`).
- **Multiplicadores por tier (mesmo tier, sem Escudo, sem armadura vanilla, sem Força):**

| Tier | `Tiers.health` | Vida do jogador (`playerHealth`) | Resistência | Fator líquido (`hurt`/`hurtMagic`) | % da vida por 1 de dano base |
| --- | --- | --- | --- | --- | --- |
| **T6** | 255 | **210** | −12% (`×0,88`) | **× 1,452** | 0,691% |
| **T7** | 185 | **140** | −9% (`×0,91`) | **× 1,089** | 0,778% |
| **T8** | 135 | **90** | −6% (`×0,94`) | **× 0,821** | 0,912% |
| **T10 (Inata)** | 65 | **20** | 0% (`×1,00`) | **× 0,0765** *(sem `subScale`)* | 0,382% |

> **Nota sobre guarda (`Defense.java`):** hoje `Defense.onIncomingDamage` reduz apenas o dano (bloqueio) ou anula e atordoa o atacante (parry); controle (`Stun`, `Freeze`, `PowerLogic.seal`) passa direto. **Nenhuma subalma chama `Defense.blockGrab`.** Na coluna **Obs.** indicamos quando a skill *deveria* ser bloqueável pela regra das divinas (o que vem até o alvo = bloqueável; área, olhar, domínio e teleporte nas costas = não).

---

## Dragon Ball

### Goku — Tier 6 (Vida: 210) · `sub/db/Goku.java`

*Multiplicador melee: `1,1×` (`Goku.java:69`). Modos: Kaioken (C) +50% dano/velocidade melee e `1,5×` no Kamehameha; Kaioken x3/x10/x20 (B, ensinado por Sr. Kaioh) até `+90%` melee (`DbCommon.java:229-241`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (seg.) | Kamehameha | 8,0 | ~4–16 × (1,1–3,98) + 9–20 explosão *(×1,5 no Kaioken)* | ~24–85 (11–40%) · Kaioken: ~36–128 (17–61%) | Empurrão | Dano reduz na guarda | Raio 40 bl, bate a cada 4 ticks até 64 ticks + explosão final (`hurtMagic`). `Goku.java:70,141-155`. |
| X | Teleporte (Transmissão Instantânea) | 7,0 | 14 | ≈ 20,3 (10%) | 2,0 s stun (`40` ticks) | Não (aparece nas costas) | Instantâneo (30 bl), soco 2 ticks depois (`hurt`). Regra divinas: não bloqueável. `Goku.java:71,165-206`. |
| C | Kaioken (ativar) | 20,0 *(após fim)* | 0 | 0 | — | — | Buff 10 s (`200` ticks): +50% melee, velocidade +50%, drena 2 HP/s (para em 4 HP). Recarga manual ao acabar. `Goku.java:72,219-264`. |
| C (2º uso) | Rush do Kaioken | 20,0 *(encerra buff)* | 4 × 4 + 8 | ≈ 34,8 (17%) | 2,0 s stun (`40` ticks) | Dano reduz na guarda *(deveria ser bloqueável)* | 4 golpes em volta + chute final (`hurt`). Encerra o Kaioken. Regra divinas: dash/investida = bloqueável. `Goku.java:268-336`. |
| B | Kaioken x3 / x10 / x20 | 30,0 *(após fim)* | 0 | 0 | Auto-stun 1,5 s ao fim do x20 | — | Ensinada (Sr. Kaioh). Sobe nível (+60/75/90% melee, drena 2/3/4 HP/s, 8 s por pressão). x10/x20 dão Lentidão/Fraqueza no fim. `Goku.java:74,502-584`. |
| V (seg.) | Genki Dama | 60,0 | 15–100 *(cheia 60 s: `max(dmg, maxHp×4)`)* | ~21,8–145,2 (10–69%) · **Cheia: ≈ 1220 (581% — ONE-SHOT)** | Empurrão | Não (área 7–52 bl) | **ALERTA DUPLA ESCALA** na carga cheia (60 s): `maxHp×4` passa por `hurtMagic`. Teleguiada até 80 bl. `Goku.java:73,463-485`. |
| Shift+V (seg.) | Mini Genki Dama | 25,0 | 10–32 | ~14,5–46,5 (7–22%) | 1,7 s stun (`30` ticks no impacto + `30` após 4 ticks) | Não (área + coluna de luz) | Ensinada (Sr. Kaioh). Carga até 2,2 s, teleguiada 60 bl (`hurtMagic`). `Goku.java:75,589-735`. |

---

### Vegeta — Tier 6 (Vida: 210) · `sub/db/Vegeta.java`

*Multiplicador melee: `1,1×` (`Vegeta.java:43`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (seg.) | Renzoku Energy Dan | 7,0 | ~8–32 × 0,9 *(até 41 tiros, 22% erram)* | ~10–42 (5–20%) | Cegueira na fumaça | Dano reduz na guarda | Segura 1–4,1 s, 1 bola a cada 2 ticks (`hurtMagic`). `Vegeta.java:44,52-175`. |
| X | Big Bang Attack | 10,0 | 24 *(cai até 50% na borda)* | ≈ 34,8 (17%) | Empurrão | Dano reduz na guarda *(área 8,3 bl)* | Carga 1,6 s, projétil teleguiado 48 bl (`hurtMagic`). `Vegeta.java:45,224-305`. |
| C | Explosão Final | 80,0 | 170 + 10 × 9 = 260 | **≈ 377,5 (180% — ONE-SHOT)** *(borda: ≈ 303 / 144%)* | Empurrão final (auto-trava 20 s pedra) | Não (domo raio 20 bl) | **ALERTA:** mata qualquer T6 em 1 uso (`hurtMagic`). Deixa Vegeta com 1 HP e petrificado/intangível por 20 s. `Vegeta.java:46,332-414`. |
| V | Final Flash | 50,0 | ~15 × 2,2 + 14 = 47 | ~68,2 (32%) | Empurrão | Dano reduz na guarda | Carga 4,5 s (imóvel), raio 3 s (40 bl, atravessa tudo, bate a cada 4 ticks) + explosão 10,3 bl (`hurtMagic`). `Vegeta.java:47,423-511`. |

---

### Freeza — Tier 6 (Vida: 210) · `sub/db/Freeza.java`

*Multiplicador melee: `1,0×` (`Freeza.java:51`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (seg.) | Death Beam | 3,5 | 1–8 × 5,5 *(30% dos tiros 2–7 erram)* | ~8,0–63,9 (4–30%) *(média segurando: ~48 / 23%)* | Empurrão no último | Dano reduz na guarda | Instantâneo (40 bl, atravessa tudo), rajada de 8 a cada 3 ticks (`hurtMagic`). `Freeza.java:52,60-115`. |
| X (+X) | Telecinese | 11,0 *(após fim)* | 13 | ≈ 18,9 (9%) | Até 3,0 s levitação/selo + 0,5 s queda + 1,2 s stun = **até 4,7 s** | Não (telecinese à distância) | Alcance 22,5 bl (`Freeze` + `seal`). X de novo arremessa no chão (`hurt`). Recarga manual ao fim. `Freeza.java:53,126-208`. |
| C (+C) | Death Saucer | 13,0 *(após fim)* | 15 (ida) + 15 (volta) *(ou 6 se detonar com C)* | ≈ 21,8 (10%) · 2 hits: ≈ 43,6 (21%) | 0,7 s stun por corte (`14` ticks) | Dano reduz na guarda | Disco teleguiado 45 bl (`hurtMagic`). Na volta corta o próprio Freeza com `e.hurt` **direto** (`14 × tier × 5,5`, sem `Combat`, +1,3 s stun). `Freeza.java:54,213-363`. |
| V | Supernova | 55,0 | 40 + até ~15 × 2,5 = ~77,5 | ~58,1 (28%) inicial · até ~112,5 (54%) se ficar no domo | 1,0 s stun (`20` ticks) | Não (domo raio 36 bl) | Carga 5,5 s (imóvel), bola teleguiada 70 bl, domo dura 9 s (`hurtMagic`). `Freeza.java:55,374-496`. |

---

### Piccolo — Tier 7 (Vida: 140) · `sub/db/Piccolo.java`

*Multiplicador melee: `1,0×` (`Piccolo.java:54`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z | Masenko | 7,0 | ~6 × 1,6 + 10 = 19,6 | ~21,4 (15%) | Empurrão | Dano reduz na guarda | Carga 1,05 s, raio 1,3 s (24 bl, a cada 4 ticks) + explosão 5,4 bl (`hurtMagic`). `Piccolo.java:55,78-140`. |
| X | Braço Esticado | 8,0 | 9 | ≈ 9,8 (7%) | 0,5 s puxão + 3,0 s stun (`60` ticks, cheio em boss) = **3,5 s** | Dano reduz na guarda *(deveria ser bloqueável)* | Alcance 17 bl, puxa e dá joelhada (`hurt`). Abre combo pro C. Regra divinas: agarrão que vem até você = bloqueável. `Piccolo.java:56,147-239`. |
| C (+C) | Hellzone Grenade | 14,0 *(após fim)* | 16 + 0,8×20 = 32 *(+3 por toque na jaula)* | ≈ 34,9 (25%) | **2,85 s** stun (`57` ticks, cheio em boss) | Não (cerco 360°) | **Precisa de stun (`Stamina.stunnable`, #148).** 20 esferas cercam e detonam juntas (`hurtMagic`). Recarga manual. `Piccolo.java:57,277-464`. |
| V (seg.) | Makankosappo | 30,0 | 8–24 (raio) + 6–12 (explosão) | ~15,3–39,2 (11–28%) | Hitstun 0,6 s (`12` ticks) | Dano reduz na guarda | Tira roupa (1,4 s) + carga (0,6–3,1 s). Raio perfurante 68 bl (`hurtMagic`). `Piccolo.java:58,471-576`. |

---

### Mestre Kame — Tier 7 (Vida: 140) · `sub/db/Kame.java`

*Multiplicador melee: `0,95×` (`Kame.java:64`). Max Power (X) dá `+40%` melee (`DbCommon.java:233`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (+Z) | Suiken (Punho Bêbado) | 10,0 *(após 4 s)* | Combo: 4×2,5 + 6 = 16 · Palma (Z/esquiva): 4 | Combo: ≈ 17,4 (12%) · Palma: ≈ 4,4 (3%) | Hitstun 0,5–0,8 s | Dano reduz na guarda | Por 4 s, **60% dos golpes melee passam reto** e contra-atacam com palma (`DbCommon.java:253`). Recarga manual. `Kame.java:65,73-178`. |
| X (+X) | Max Power / Kamehameha Max | 15,0 *(após fim)* | ~11 × 3 + 14 = 47 | ~51,2 (37%) | Empurrão | Dano reduz na guarda | +40% melee por até 4,6 s; X de novo (ou auto após 4,6 s) dispara raio 2,2 s + explosão (`hurtMagic`). Recarga manual. `Kame.java:66,182-290`. |
| C | Bankoku Bikkuri Sho | 12,0 | 10 × 1 + 8 = 18 | ≈ 19,6 (14%) | 2,5 s choque (`Freeze`+`Stun`) + 2,5 s stun (`50` ticks) = **5,0 s** | Dano reduz na guarda *(deveria ser bloqueável)* | Alcance 12 bl (`hurtMagic`). Abre combo pro Mafuba. Regra divinas: projétil/raio frontal = bloqueável. `Kame.java:67,295-349`. |
| V | Mafuba | 70,0 | **Execução ≤20% HP (`Float.MAX_VALUE`)** ou **15% `maxHp` direto** | **21,0 (15%)** *(ou 100% se ≤20% HP)* | 6,6 s captura + 6,0 s selado (4 s boss) + 0,7 s stun = **13,3 s** | Não (tornado) | **Precisa de stun (`Stamina.stunnable`, #148). ALERTA DANO DIRETO:** `target.hurt` fora do `Combat` (ignora Escudo/guarda/tier). Zera estamina e ajoelha Kame 3 s. `Kame.java:68,369-544`. |

---

### Kuririn — Tier 8 (Vida: 90) · `sub/db/Kuririn.java`

*Multiplicador melee: `1,0×` (`Kuririn.java:57`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (seg.) | Kienzan | 9,0 | 18 *(segurando: 2 discos = 36)* | ≈ 14,8 (16%) · 2 discos: ≈ 29,6 (33%) | Hitstun 0,6 s (`12` ticks) | Dano reduz na guarda | Carga 1,1 s, 34 bl, perfura alvos e corta blocos (`hurtMagic`). `Kuririn.java:58,70-153`. |
| X | Taiyoken | 15,0 | 0 | 0 | **6,0 s** stun + cegueira (`120` ticks) | Não (flash de olhar) | Cone 10 bl só em quem olha pra Kuririn. Golpe pela frente acorda o alvo (`DbCommon.java:268`). `Kuririn.java:59,200-252`. |
| C | Kakusan Koha | 11,0 | Até 6–14 × 3,5 *(6 primeiros miram alvos)* | ~17,2 (19%) *(6 raios no mesmo alvo)* | Hitstun 0,4 s (`8` ticks) | Não (chuva de cima) | 14 raios em área de 7 bl (`hurtMagic`). `Kuririn.java:60,256-352`. |
| V (seg.) | Kienzan Teleguiado | 25,0 | 16 *(por alvo, 1× cada)* | ≈ 13,1 (15%) | Hitstun 0,6 s (`12` ticks) | Dano reduz na guarda | Carga 1,4 s, guiado pela mira por 4,5 s (`hurtMagic`). `Kuririn.java:61,357-431`. |

---

## Naruto

### Naruto — Tier 6 (Vida: 210) · `sub/nr/Naruto.java`

*Multiplicador melee: `1,05×` (`Naruto.java:59`). Modo Sábio (V): `+2` blocos alcance e `1,3×` melee (`NrCommon.java:115-120`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z | Rasengan *(Oodama c/ clone)* | 8,0 | 5×1,8 + 9 = 18 *(Oodama: 5×2,6 + 13 = 26)* | ≈ 26,1 (12%) · Oodama: ≈ 37,8 (18%) | 0,5 s `Freeze` + 2,6 s stun (`52` ticks) = **3,1 s** | Dano reduz na guarda *(deveria ser bloqueável)* | Carga 2 s + corrida 18 bl (`hurtMagic`). Regra divinas: dash/investida = bloqueável. `Naruto.java:60,103-206`. |
| X (+X) | Kage Bunshin / Troca | 15,0 *(após troca/fim)* | Clone: 3/soco · Troca: 7 | Clone: ≈ 4,4 (2%)/soco · Troca: ≈ 10,2 (5%) | 1,0 s stun na troca (`20` ticks) | Dano reduz na guarda | Invoca 3 clones (1 HP, duram 20 s, `NarutoClone.java:129`). X de novo troca de lugar e soca (`hurt`). `Naruto.java:61,222-314`. |
| C | Uzumaki Naruto Rendan | 13,0 | 4×2,5 + 11 = 21 | ≈ 30,5 (15%) | 3,8 s no ar (`Freeze`) + 2,6 s stun (`52` ticks) = **6,4 s** | Não (cercado por 4 clones) | Alcance 10 bl (`hurt`). Alvo preso durante toda a sequência. `Naruto.java:62,318-420`. |
| B | Henge Armadilha | 18,0 | 5×1,8 + 9 = 18 *(Rasengan do clone)* | ≈ 26,1 (12%) | 0,8 s + 0,6 s `Freeze` + 2,6 s stun = **~3,5 s** | Não (emboscada nas costas) | Pedra armada 45 s (máx. 2, raio 3 bl). Clone sai atrás e dá Rasengan (`hurtMagic`). `Naruto.java:64,608-767`. |
| Shift+B | Fuuma Shuriken (Henge) | 18,0 | 6 (corte) + 18 (Rasengan) = 24 | ≈ 34,8 (17%) *(se desviar: só Rasengan ≈ 26,1 / 12%)* | **~3,5 s** | Dano reduz na guarda *(se desviar: costas)* | Alcance 32 bl. Se o alvo desvia, vira clone atrás com Rasengan (`hurt` + `hurtMagic`). `Naruto.java:65,660-720`. |
| V (+V) | Modo Sábio / Rasenshuriken | 45,0 *(30 s sem lançar; 10 s se interrompido)* | ~20×1,7 + 8 = 42 | ~61,0 (29%) | ~2,9 s puxão + hitstun repetido | Não (esfera raio 9 bl) | Medita 4,4 s (qualquer hit quebra). Modo dura 30 s. V de novo lança Rasenshuriken 48 bl (`hurtMagic`) e encerra o modo. `Naruto.java:63,438-600`. |

---

### Sasuke — Tier 6 (Vida: 210) · `sub/nr/Sasuke.java`

*Multiplicador melee: `1,05×` (`Sasuke.java:56`). Espada Kusanagi equipa bônus extra via `NarutoGear.kusanagiBoost` (`NrCommon.java:125`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z | Katon: Goukakyuu | 7,0 | 26 *(até 50% na borda)* | ≈ 37,8 (18%) | 1,5 s stun centro (`30` ticks, cheio em boss) / 1,25 s borda | Dano reduz na guarda | Bola de fogo 44 bl, raio 4 bl, incendeia 4 s (`hurtMagic`). `Sasuke.java:57,78-193`. |
| X (seg.) | Chidori | 7,5 | 15 *(crítico em paralisado/marcado: 24)* | ≈ 21,8 (10%) · Crítico: ≈ 34,8 (17%) | 1,5 s stun (`30` ticks) | Dano reduz na guarda *(deveria ser bloqueável)* | Dash 8–22 bl, **intangível** durante a corrida (`UNTOUCHABLE`). Regra divinas: dash = bloqueável. `Sasuke.java:58,197-300`. |
| C | Chidori Senbon | 10,0 | 3 × agulhas (1–9, prático 2–5) | ~8,7–21,8 (4–10%) *(máx. 9: 39,2 / 19%)* | **3,5 s paralisia** (`70` ticks, cheio em boss) com ≥2 agulhas ou marcado *(1 agulha: 1,5 s)* | Dano reduz na guarda *(deveria ser bloqueável)* | Leque de 9 agulhas (14 bl, `hurtMagic`). Paralisia = `Freeze`+`seal`+`Stun` real. `Sasuke.java:59,422-481`. |
| B | Sharingan (Leitura) | 12,0 *(2 s se ninguém bater)* | 9 *(no 2º contra-ataque)* | ≈ 13,1 (6%) | 1,5 s stun (`30` ticks, cheio em boss) | Não (aparece nas costas) | 6 s de leitura: **desvia automaticamente dos 2 primeiros golpes < 45 dano**, marca o atacante e contra-ataca no 2º (`hurt`). `Sasuke.java:61,314-418`. |
| V | Kirin | 55,0 | 60 (alvo) · 34 (área 8 bl) | ≈ 87,1 (41%) · área: ≈ 49,4 (24%) | **3,0 s stun** alvo (`60` ticks, cheio em boss) · 2,0 s área | Não (cai do céu) | Só a céu aberto (64 bl), segue o alvo após 0,8 s de selo (`hurtMagic`). `Sasuke.java:60,489-570`. |

---

### Kakashi — Tier 7 (Vida: 140) · `sub/nr/Kakashi.java`

*Multiplicador melee: `1,0×` (`Kakashi.java:63`). Cópia do Sharingan (C) dá `1,2×` na skill copiada (`NrCommon.java:125`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (toque) | Raikiri | 10,0 | 17 | ≈ 18,5 (13%) | 1,6 s stun (`32` ticks) | Dano reduz na guarda *(deveria ser bloqueável)* | Dash 16 bl, intangível na corrida (`hurtMagic`). Regra divinas: dash = bloqueável. `Kakashi.java:64,115-173`. |
| Z (seg.) | Raiju Tsuiga (Cão) | 10,0 | 13 | ≈ 14,2 (10%) | **2,0 s paralisia** (`40` ticks) | Dano reduz na guarda *(deveria ser bloqueável)* | Cão teleguiado no chão 24 bl (`hurtMagic`). Regra divinas: projétil que pega = bloqueável. `Kakashi.java:64,175-251`. |
| X | Kamui | 20,0 | 12 | ≈ 13,1 (9%) | 1,55 s puxão + 4,0 s banido (2 s boss) + 1,5 s stun = **~7,0 s** | Não (olhar Mangekyo) | Alcance 34 bl (`hurtMagic`). Alvo fica intangível/invisível/selado na outra dimensão. `Kakashi.java:65,255-351`. |
| Shift+X | Kamui (em si mesmo) | 12,0 | 0 | 0 | — | — | Fica intangível e invulnerável por 1,1 s (`22` ticks). `Kakashi.java:69,355-369`. |
| C | Sharingan: Cópia | 15,0 | Depende da skill copiada (`×1,2`) | Variável (`×1,2`) | Da skill copiada | Da skill copiada | Copia última skill usada a ≤16 bl nos últimos 10 s (exceto ult de divina). `Kakashi.java:66,373-396`. |
| B | Ninken (Armadilha) | 25,0 | 6 + 6×2 = 18 *(no mesmo tier)* | ≈ 19,6 (14%) | **3,25 s** (`65` ticks `Freeze`+`seal`+`Stun`) | Não (vem do chão) | Selo armado 40 s (16 bl). Duração reduzida por `JjCommon.control` contra tiers maiores/boss. `Kakashi.java:68,400-491`. |
| V | Susanoo Perfeito (Kamui Shuriken) | 70,0 | ~8×3 + 24 = 48 | ~52,3 (37%) | ~1,9 s vórtice + 3,0 s banido (1,5 s boss) + 1,5 s stun = **~6,4 s** | Não (área raio 9 bl) | Kakashi fica blindado/intangível no Susanoo. Vórtice puxa e bane quem chega ao centro (`hurtMagic`). `Kakashi.java:67,495-663`. |
| Shift+V | Sennen Goroshi | 90,0 | 5 | ≈ 5,4 (4%) | 2,3 s travado antes + 3,5 s stun / 3,0 s selo = **~5,8 s** | Não (aparece nas costas) | Secreta (20 bl), joga alvo pro alto sem dano de queda (`hurtMagic`). `Kakashi.java:69,676-751`. |

---

### Gaara — Tier 7 (Vida: 140) · `sub/nr/Gaara.java`

*Multiplicador melee: `0,95×` (`Gaara.java:102`). Passiva Escudo de Areia: bloqueia 1 golpe frontal a cada 6 s (3 s com Cabaça; cancela se <30 dano, reduz 40% se ≥30, `NrCommon.java:236-256`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (toque) | Mão de Areia (Tapa) | 7,0 | 12 | ≈ 13,1 (9%) | 1,25 s stun (`25` ticks) | Dano reduz na guarda *(deveria ser bloqueável)* | Alcance 18–20 bl (`hurtMagic`). Regra divinas: mão vindo até você = bloqueável. `Gaara.java:103,140-311`. |
| Z (seg.) | Mão de Areia → Caixão | 7,0 *(+X em 1 s)* | *(entra no X: 24)* | ≈ 26,1 (19%) | **3,7–5,45 s** (ver X) | Dano reduz na guarda *(deveria ser bloqueável)* | Fecha a mão no alvo e inicia o Caixão de Areia (X). `Gaara.java:222-278`. |
| X (+X) | Sabaku Kyu / Sabaku Soso | 15,0 *(após fim)* | 24 *(se esmagar com X)* | ≈ 26,1 (19%) | 3,7 s prisão (`Freeze`+`seal`+`Stun`) + 1,75 s stun ao esmagar = **até 5,45 s** | Não (areia sobe do chão) | Alcance 24 bl (infinito em alvo na Areia Movediça B). Desativa Escudo passivo enquanto prende (`hurtMagic`). `Gaara.java:104,315-455`. |
| C (+C) | Plataforma de Areia | 8,0 *(após pousar)* | 0 | 0 | — | — | Voo livre consumindo estamina (`7,5%×(0,4+v/14)/s`). `Gaara.java:105,465-610`. |
| B | Areia Movediça (Armadilha) | 20,0 | 3 | ≈ 3,3 (2%) | **4,0 s** Lentidão VI + puxão (`80` ticks) | Não (chão) | Até 2 armadas (40 s, alcance 20 bl). Comba com X de qualquer distância (`hurtMagic`). `Gaara.java:107,614-728`. |
| V | Sabaku Taiso | 80,0 | 45 | ≈ 49,0 (35%) | ~3,45 s preso na areia + **3,0 s stun** (`60` ticks) = **~6,45 s** | Não (área raio 10 bl) | Alcance 30 bl; Gaara fica intangível durante a carga (`hurtMagic`). `Gaara.java:106,739-850`. |

---

### Shikamaru — Tier 8 (Vida: 90) · `sub/nr/Shikamaru.java`

*Multiplicador melee: `0,9×` (`Shikamaru.java:118`). Sombras drenam estamina por alvo: 10%/s mesmo tier (~10 s), 20%/s mais forte (~5 s), 5%/s mais fraco (~20 s); Sinalizador (B) dobra alcance e corta dreno pela metade.*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (+Z seg.) | Kagemane / Kubishibari | 10,0 *(após soltar; 2 s se errar)* | 1,6 a cada 0,5 s (até ~11× no enforcamento) | ~1,3/hit · até ~14,4 (16%) | **Até ~10 s** (20 s c/ B) preso + selado *(~6,7 s enforcando)* | Não (sombra no chão) | Alcance 24 bl (48 c/ B). Alvo espelha passos; 4+ bombas nele ativam Fosso (+3 s stun/selo). `Shikamaru.java:119,162-333`. |
| X (+X) | Kage Nui | 10,0 *(após soltar)* | 7 | ≈ 5,7 (6%) | **Até ~10 s** (1 alvo) preso + selado *(divide estamina por nº de alvos, até 4)* | Não (chão) | Alcance 20 bl (40 c/ B), até 4 alvos (`hurtMagic`). `Shikamaru.java:120,343-452`. |
| C (toque/seg.) | Papel Bomba | **0,4** *(bolsa: 10, recarrega 1/3 s)* | 8 por papel *(até 10 em leque)* | ≈ 6,6 (7%) por papel · **10 em cadeia: ≈ 65,7 (73%)** | 1,0 s stun (`20` ticks) · +3,0 s Fosso se ≥4 no Z | Não (armadilha/área) | **ALERTA RECARGA/BURST:** 0,4 s de cd, cadeia 4,6 bl (`hurtMagic`). 10 papéis sobrepostos tiram até 73% da vida T8. `Shikamaru.java:121,494-690`. |
| Shift+C | Detonar Papéis | 1,0 | *(detona todos os C armados)* | *(ver C)* | *(ver C)* | Não (área) | Detona todos os papéis armados em sequência rápida. `Shikamaru.java:124,632-645`. |
| B | Sinalizador | 40,0 | 0 | 0 | — | — | Buff 8 s: dobra alcance de Z/X e reduz dreno de estamina pela metade. `Shikamaru.java:123,814-841`. |
| V | Xeque-mate | 55,0 | 18 *(por alvo explodido; pode acumular se alvos estiverem juntos)* | ≈ 14,8 (16%) *(1 alvo)* | 0,6 s+ preso + 1,5 s stun (`30` ticks) | Não (mar de sombras raio 16 bl) | Ajoelha 1 s com Resistência III, prende todos em 16 bl e explode em cadeia (`hurtMagic`). `Shikamaru.java:122,710-810`. |

---

### Ino — Tier 8 (Vida: 90) · `sub/nr/Ino.java`

*Multiplicador melee: `0,9×` (`Ino.java:99`). Passiva Leitura Mental: alerta visual quando inimigo a ≤16 bl inicia skill (`Ino.java:199`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (+Z) | Shintenshin (Troca de Mente) | 13,0 *(após fim; 10 s se errar/apanhar)* | Saída (Z): 9 · Soco pilotado: 6 | Saída: ≈ 7,4 (8%) · Soco: ≈ 4,9 (5%) | **7,0 s** controle total (10 s fraco / 5 s forte / 4 s boss) + 1,0 s stun saída | Dano reduz na guarda *(deveria ser bloqueável)* | Espírito anda 20 bl (teleguiado se marcado por B). Corpo de Ino fica vulnerável. `Ino.java:100,220-487`. |
| X | Shinranshin | 13,0 | 0 | 0 | **7,0 s** controles invertidos (3 s boss) | Não (cone mental 12 bl) | Inverte andar e câmera de jogadores; mobs atacam o alvo mais próximo. `Ino.java:101,515-568`. |
| C | Elo Mental | 15,0 | 50% do dano sofrido por outro ligado | Variável | — | Não (elo mental) | Liga 2–3 inimigos (12 bl, 6 s). **ALERTA:** dano refletido chama `other.hurt` direto (`NrCommon.java:284`), fora do `Combat`. `Ino.java:102,572-650`. |
| B | Campo de Flores | 12,0 | 0 | 0 | — | — | Até 2 campos (30 s, raio 2,5 bl). Marca alvo por 8 s para Shintenshin teleguiado. `Ino.java:104,654-732`. |
| V | Teatro Mental | 55,0 | 36 *(+socos de 4 entre fantoches)* | ≈ 29,6 (33%) *(+≈ 3,3/soco)* | **10,0 s** fantoche/selo (5 s boss) + 0,9 s puxão + 2,0 s stun = **12,9 s** | Não (domo mental raio 20 bl) | Ino fica ajoelhada; golpe >8 dano nela cancela sem explosão (`hurtMagic`). `Ino.java:103,757-915`. |

---

## Jujutsu

### Itadori — Tier 6 (Vida: 210) · `sub/jj/Itadori.java`

*Multiplicador melee: `1,15×` (`Itadori.java:80`). Passiva Ritmo: cada golpe amplia a janela do Black Flash (`Itadori.java:90-102`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (toque/seg.) | Punho Divergente | 7,0 *(2 s se errar)* | 3 + 3 + 5 + 12 = 23 *(2º impacto: 6 na guarda)* | ≈ 33,4 (16%) | 0,4 s `Freeze` + 1,25 s stun (`25` ticks) | Quebra guarda no 2º impacto (`Defense.breakGuard`) | Segurar Z armazena o 2º impacto no alvo (até 3 s, alcance 32 bl). `Itadori.java:81,106-225`. |
| X (+X) | Black Flash | 15,0 *(1,5 s em cadeia na Zona; 6–8 s se errar)* | 10 + 3 + 3 + 9 = 25 *(errou timing: 6)* | ≈ 36,3 (17%) · **4× na Zona: ≈ 145,2 (69%)** | 0,9 s `Freeze` + 1,5 s stun (`30` ticks) = **2,4 s** | Quebra guarda (`Defense.breakGuard`) | **ALERTA CADEIA:** na Zona (5 s), cd cai pra **1,5 s** até 4 consecutivos (`hurt`+`hurtMagic`). `Itadori.java:82,229-360`. |
| C | Chute Manji (Counter) | 8,0 *(2,5 s sem ativar)* | 12 | ≈ 17,4 (8%) | 1,25 s stun (`25` ticks) | Quebra guarda (`Defense.breakGuard`) | Postura 1,1 s: anula golpe frontal (<8 bl) e contra-ataca; reflete projéteis de volta (`hurt`). `Itadori.java:83,390-468`. |
| B | Golpe na Alma | 25,0 *(8 s se alvo não estiver conjurando)* | 6 | ≈ 8,7 (4%) | **3,0 s selo** (`60` ticks) + 1,25 s stun (2,5 s boss) | Dano reduz na guarda | Se o alvo estiver usando/carregando skill, cancela e sela por 3 s (`hurt`). `Itadori.java:85,690-736`. |
| V (+V) | Corte de Tesoura | 25,0 | 24 *(por entidade marcada, até 3 alvos)* | ≈ 34,8 (17%) | 1,5 s stun (`30` ticks; 2,6 s boss) | Quebra guarda (`Defense.breakGuard`) | Marca até 3 alvos/pilares (4,2 bl) e fecha tesoura (`hurtMagic`). `Itadori.java:84,489-637`. |

---

### Choso — Tier 6 (Vida: 210) · `sub/jj/Choso.java`

*Multiplicador melee: `1,0×` (`Choso.java:69`). Passiva Poças de Sangue: golpes deixam poças por 10 s (máx. 8) que explodem com X e B.*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (seg.) | Sangue Perfurante | 10,0 | 7–20 | ~10,2–29,0 (5–14%) | 0,5–1,3 s stun (`10–26` ticks) | Dano reduz na guarda | Carga 0,4–2,0 s, feixe 25 bl varrido com a mira (`hurtMagic`), deixa poça. `Choso.java:70,135-203`. |
| X (+X) | Supernova | 18,0 *(15 s se todas orbes quebrarem)* | 3,6 × orbes (até 5 = 18) *(+poças: 2,4×orbes = até 12/poça)* | ≈ 26,1 (12%) *(+≈ 17,4 / 8% por poça)* | 1,25 s stun (`25` ticks c/ 5 orbes) | Não (área 4,5 bl + poças) | 5 orbes (8 s) **anulam 1 golpe/projétil cada** (`JjCommon.java:271`). X de novo explode orbes + poças em 14 bl (`hurtMagic`). `Choso.java:71,207-292`. |
| C (+C) | Crimson Binding | 13,0 *(após fim; 4 s se errar)* | 10 *(no puxão)* | ≈ 14,5 (7%) | **2,5 s** preso+selado (`50` ticks) + 1,25 s stun no puxão = **3,75 s** | Dano reduz na guarda *(deveria ser bloqueável)* | Corda 12 bl. C de novo puxa e bate no chão (`hurt`). Regra divinas: corda que vem até você = bloqueável. `Choso.java:72,296-410`. |
| B | Meteorito de Sangue | 15,0 | 16 *(+10 por poça em 3 bl)* | ≈ 23,2 (11%) *(+≈ 14,5 / 7% por poça)* | 1,25 s stun (`25` ticks) | Não (área 3 bl em arco) | Alcance 22 bl, persegue levemente no ar (`hurtMagic`). `Choso.java:74,559-620`. |
| V | Perfurante Máximo | 35,0 | 34 (leque) + 10 (espinhos) = 44 | ≈ 63,9 (30%) | 1,5 s stun (`30` ticks; 2,6 s boss) + 1,25 s nos espinhos | Quebra guarda (`Defense.breakGuard`) | Carga 2 s (Resistência III), varre leque de 40 bl + espinhos no chão (`hurtMagic`). `Choso.java:73,414-555`. |

---

### Todo — Tier 6 (Vida: 210) · `sub/jj/Todo.java`

*Multiplicador melee: `1,1×` (`Todo.java:56`). Passiva: alvo trocado fica `OPEN` por 2 s (próximo golpe `×1,6`, `JjCommon.java:285`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (toque/seg.) | Boogie Woogie | 3,0 | 0 | 0 | 0,3 s stun em mob + `OPEN` (`×1,6` no próx. hit) | Não (teleporte) | Alcance 25 bl. Toque: troca com alvo; segurar: marca A e troca A com B. `Todo.java:57,93-135`. |
| X | Bloco + Palma | 16,0 | 17 *(×1,6 se `OPEN` = 27,2)* | ≈ 24,7 (12%) · c/ `OPEN`: ≈ 39,5 (19%) | 1,25 s stun (`25` ticks) | Quebra guarda (`Defense.breakGuard`) | Alcance 20 bl, arremessa bloco, troca no ar e desce com martelada (`hurt`). `Todo.java:58,139-230`. |
| C | Palma Falsa | 10,0 | 0 | 0 | 1,0 s `Freeze` + vira costas (mobs) + `OPEN` 3 s (todos em 10 bl) | Não (área 10 bl) | Próximo golpe no alvo causa `×1,6`. `Todo.java:59,247-265`. |
| B (seg.) | Vibraslap | 35,0 | Até ~39 × 3 = 117 *(1 golpe a cada 3 ticks por até 6 s)* | **Até ~169,9 (81%)** *(média 3 s: ~85 / 40%)* | Hitstun contínuo (0,3 s a cada 0,15 s) = **até 6,0 s travado** | Dano reduz na guarda | **ALERTA:** teleporta em volta batendo a cada 3 ticks sem aviso (20 bl, `hurt`). Se segurar os 6 s, quase mata um T6. `Todo.java:61,429-485`. |
| V | Turbilhão de Palmas | 45,0 | 11×2,5 + 10 + 14 = 51,5 | ≈ 74,8 (36%) | 2,3 s trocas/BF (`Freeze`) + 2,5 s stun (`50` ticks) = **4,8 s** | Quebra guarda no Black Flash | Alcance 20 bl, Resistência III durante o golpe (`hurt`+`hurtMagic`). `Todo.java:60,269-413`. |
| Shift+V | Black Flash Garantido | 45,0 | 12 + 3 + 3 + 14 = 32 *(×1,6 se `OPEN`)* | ≈ 46,5 (22%) *(c/ `OPEN`: ≈ 57)* | 0,7 s `Freeze` + 2,5 s stun (`50` ticks) = **3,2 s** | Quebra guarda *(deveria ser bloqueável no dash)* | Dash guiado até 50 bl com Resistência IV (`hurt`+`hurtMagic`). `Todo.java:62,489-599`. |

---

### Nanami — Tier 7 (Vida: 140) · `sub/jj/Nanami.java`

*Multiplicador melee: `1,05×` (`Nanami.java:60`). Passiva Expediente: após 60 s em combate, `+20%` em todo dano (`JjCommon.java:130`). Modo Overtime (V): `+40%` em todo dano (`×1,6` melee) por 12 s (`JjCommon.java:120,128`). Com ambos: `×1,68`!*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real *(normal / Overtime)* | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (+Z) | 7:3 (Razão) | 8,0 *(7 s se expirar)* | 9×2 = 18 *(Overtime: 10×2 = 20)* | ≈ 19,6 (14%) · Overtime: ≈ 30,5 (22%) | **2,0 s stun** (`40` ticks, cheio em boss ≤T7) | Quebra guarda (`Defense.breakGuard`) | Marca por 3 s (14 bl); próx. golpe dobra (`JjCommon.java:267`). Z de novo dá dash e acerta (`hurt`). `Nanami.java:61,90-178`. |
| X | Colapso | 15,0 | 15 *(cai até 50% na borda)* | ≈ 16,3 (12%) · Overtime: ≈ 22,9 (16%) | 1,1 s stun centro (`22` ticks) | Não (área 5,5 bl) | Golpeia parede/chão (6 bl), desaba blocos em 5,5 bl (`hurtMagic`). `Nanami.java:62,182-242`. |
| C | Lâmina Enfaixada | 10,0 *(1,5 s se errar)* | 3 × (5×2) = 30 | ≈ 32,7 (23%) · Overtime: ≈ 45,8 (33%) *(+Expediente: ≈ 54,9 / 39%)* | **2,0 s stun** (`40` ticks no 3º corte) | Quebra guarda no 1º corte | 3 cortes críticos rápidos (4,5 bl, `hurt`). `Nanami.java:63,246-289`. |
| B | Counter do Escritório | 12,0 *(5 s sem ativar)* | 14 | ≈ 15,3 (11%) · Overtime: ≈ 21,4 (15%) | **2,0 s stun** (`40` ticks) | Quebra guarda | Postura 1 s: anula qualquer golpe (<7 bl) e contra-ataca no ponto 7:3 (`hurt`). `Nanami.java:65,293-333`. |
| V | Overtime | 45,0 | 20 *(área 8 bl, já com `OVERTIME` ativo!)* | ≈ 30,5 (22%) *(+Expediente: ≈ 36,6 / 26%)* | 1,7 s stun centro (`34` ticks) | Não (área 8 bl) | Ativa buff 12 s (`×1,4` dano geral, `×1,6` melee) antes do impacto (`hurtMagic`). `Nanami.java:64,337-399`. |

---

### Inumaki — Tier 7 (Vida: 140) · `sub/jj/Inumaki.java`

*Multiplicador melee: `0,95×` (`Inumaki.java:57`). Recurso Garganta: limite 100, recupera 3/s; acima de 100 tosse sangue (−2 HP e **silenciado 4 s**).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (toque/seg.) | "Pare" | 7,5 *(2 s se errar)* | 0 | 0 | **1,5 s** (toque) até **~4,5 s** (segurado) `Freeze`+`Stun`+`seal` | Não (voz) | Alcance 15 bl (25 bl cone c/ Megafone). Garganta: 20 (30 c/ B) + 5,5 a cada 0,25 s segurando. `Inumaki.java:58,143-207`. |
| X | "Esmague-se" | 12,0 *(2 s se errar)* | 15 *(22 se congelado; ×1,8 se dormindo = 27–39,6)* | ≈ 16,3 (12%) · Congelado: ≈ 24,0 (17%) · **Dormindo+Cong.: ≈ 43,1 (31%)** | 1,3 s `Freeze`+`Stun` (`26` ticks) | Não (esmagamento de cima) | Alcance 15 bl (`hurtMagic`). Garganta: 45 (65 c/ B). Derruba voadores. `Inumaki.java:59,211-269`. |
| C | "Durma" | 13,0 *(2 s se errar)* | 0 | 0 | **3,0 s** `Freeze`+`Stun`+`seal` (`60` ticks) | Não (voz) | Alcance 8 bl (25 c/ B). Próximo golpe acorda com **crítico `×1,8`** (`JjCommon.java:276`). Garganta: 20 (30 c/ B). `Inumaki.java:60,273-308`. |
| B | Megafone | 16,0 | 0 | 0 | — | — | Buff 6 s: próxima palavra (Z/X/C) vira cone de 25 bl, custo de garganta `×1,5`. `Inumaki.java:62,312-325`. |
| V | "Exploda / Voe para longe" | 45,0 | 30 | ≈ 32,7 (23%) | Empurrão extremo (`2,6`) | Não (cone 30 bl) | Garganta: 95 se vazia, senão estoura (silêncio 4 s + −2 HP). Destrói chão (`hurtMagic`). `Inumaki.java:61,329-397`. |

---

### Nobara — Tier 8 (Vida: 90) · `sub/jj/Nobara.java`

*Multiplicador melee: `0,95×` (`Nobara.java:84`). Passiva Pregos: até 3 cravados por alvo (10 s) + sangue de Nobara (10 s em qualquer golpe).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (+Z) | Pregos / Grampo | 8,0 *(após Grampo; 3 s se errar)* | 3×4 (pregos) + 3×6 (Grampo) = até 30 | Pregos: ≈ 3,3–9,9 (4–11%) · Grampo (3): ≈ 14,8 (16%) · Total: ≈ 24,6 (27%) | 1,25 s stun no Grampo (`25` ticks) | Dano reduz na guarda | 3 pregos (25 bl, `hurt`); Z de novo detona todos os pregos cravados (`hurtMagic`). `Nobara.java:85,172-348`. |
| X | Ressonância | 15,0 | 16 + 6×(pregos−1) = 16–28 | ≈ 13,1–23,0 (15–26%) | **2,0 s stun** (`40` ticks) | Não (dano interno até 60 bl) | Atinge todos com prego ou sangue até 60 bl (`hurtMagic`). Custa 2 HP de Nobara. `Nobara.java:86,352-402`. |
| C | Martelada | 7,0 | 9 | ≈ 7,4 (8%) | 1,5 s stun (`30` ticks) | Dano reduz na guarda | Curto alcance (3,2 bl), crava 1 prego e marca sangue (`hurt`). `Nobara.java:87,406-441`. |
| B | Campo de Pregos | 18,0 | 3 por prego *(7 armados)* | ≈ 2,5 (3%) por prego | Hitstun 0,25 s | Não (chão) | 7 armadilhas no chão (20 s), cravam no pé ao pisar (`hurt`). `Nobara.java:89,445-504`. |
| V | Ressonância Compartilhada | 50,0 | Reflexo: `min(30, dano×2)` por hit · Final: 24 + 8×pregos (24–48) | Final: ≈ 19,7–39,4 (22–44%) · **Reflexo: até ≈ 24,6 (27%) POR GOLPE** | **3,0 s stun** no final (`60` ticks) | Não (elo 60 bl) | **ALERTA SEMI-DUPLA ESCALA:** por 8 s, cada hit que Nobara leva reflete `min(30, amount×2)` via `Combat.hurtMagic` (`Nobara.java:619,630`), re-escalando dano já escalado! Custa 3 HP. `Nobara.java:88,508-641`. |

---

### Miwa — Tier 8 (Vida: 90) · `sub/jj/Miwa.java`

*Multiplicador melee: `0,95×` (`Miwa.java:69`). Passiva Bainha Calma: +1 marca/s sem atacar (máx. 3, `+25%` cada → até `×1,75` no próximo corte).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real *(0 / 3 marcas)* | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (seg.) | Batto *(Perfeito em 1,0 s)* | 5,0 *(1,5 s se errar)* | 6–12 *(Perfeito: 24)* × (1–1,75) | Normal: ~4,9–17,2 (5–19%) · **Perfeito: ≈ 19,7 (22%) / 3 marcas: ≈ 34,5 (38%)** | 0,75 s *(Perfeito: 1,5 s stun)* | Dano reduz na guarda | Soltar no clique (1 s) dobra o dano (`hurt`). Com 3 marcas tem altíssimo dano/recarga! `Miwa.java:70,153-228`. |
| X (+X) | Domínio Simples | 15,0 *(após sair)* | 7 por corte automático | ≈ 5,7 (6%) por corte | Hitstun 0,75 s + empurrão | Não (automático no círculo 2,2 bl) | Dura até 15 s (para se andar >0,8 bl). **Imune a Expansão de Domínio** (`JjCommon.inSimpleDomain`), corta projéteis e gera marcas. `Miwa.java:71,232-314`. |
| C | Corte em Linha | 8,0 *(2,5 s se errar)* | 10 × (1–1,75) | ≈ 8,2 (9%) · 3 marcas: ≈ 14,4 (16%) | 1,25 s stun (`25` ticks) | Dano reduz na guarda *(deveria ser bloqueável)* | Dash 8 bl instantâneo (`hurt`). `Miwa.java:72,318-383`. |
| B | Counter Estilo Nova Sombra | 11,0 | 12 × (1–1,75) | ≈ 9,9 (11%) · 3 marcas: ≈ 17,2 (19%) | **2,0 s stun** (`40` ticks) | Não (teleporte nas costas) | Janela 0,8 s: anula golpe (<8 bl), teleporta atrás e corta (`hurt`). Se ninguém bater, fica lenta 0,6 s. `Miwa.java:74,387-453`. |
| V (+V) | Batto: Lua Partida | 50,0 *(30 s se interrompida)* | 22 × (1–1,75) | ≈ 18,1 (20%) · **3 marcas: ≈ 31,6 (35%)** | **3,0 s stun** (`60` ticks) | Não (círculo raio 8 bl) | Marca todos que entram em 8 bl por até 5 s (imune a Domínio enquanto carrega), corta tudo junto (`hurt`). `Miwa.java:73,457-547`. |

---

## Bleach

### Byakuya — Tier 7 (Vida: 140) · `sub/Byakuya.java`

*Multiplicador melee: `1,0×` (`Byakuya.java:50`). Bankai (V) dura 20 s, amplifica Z/X, habilita Senkei (V) e Hakuteiken (Shift+V), e depois trava V por 20 s (`REST_TICKS = 400`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (seg.) | Senbonzakura | 4,5 | Até 30 × 1,0 = 30 *(Bankai: 40 × 1,5 = 60)* | **~32,7 (23%)** · **Bankai: ~65,4 (47%)** | Empurrão leve | Dano reduz na guarda | **ALERTA DPS/RECARGA:** rio de pétalas 22 bl, bate a cada 2 ticks por até 3 s (4 s Bankai, `hurt`). `Byakuya.java:51,94-150`. |
| X (seg.) | Gokei | 10,0 | 8–22 *(Bankai: 10,8–29,7)* | ~8,7–24,0 (6–17%) · Bankai: ~11,8–32,4 (8–23%) | Até 4,0 s `Freeze` + 1,3 s stun (`26` ticks) = **até 5,3 s** | Não (esfera 360°) | Alcance 26 bl, acerto garantido na mira (`hurt`). `Byakuya.java:52,154-203`. |
| C | Senka | 8,0 | 2 × 6 = 12 | ≈ 13,1 (9%) | 0,2 s + **2,6 s stun** (`52` ticks) = **2,8 s** | Não (teleporte nas costas) | Alcance 18 bl, Shunpo atrás com 2 cortes (`hurt`). `Byakuya.java:53,207-256`. |
| B (toque) | Hado 4: Byakurai | 6,0 | 9 | ≈ 9,8 (7%) | Empurrão | Dano reduz na guarda | Raio fino 30 bl (`hurtMagic`). `Byakuya.java:55,281-297`. |
| B (seg.) | Bakudo 81: Danku | 6,0 | 0 | 0 | — | — | Parede de luz por 4,4 s (`88` ticks) que bloqueia ataques frontais. `Byakuya.java:55,299-312`. |
| V | Bankai: Senbonzakura Kageyoshi | 20,0 *(descanso após 20 s de Bankai)* | Tempestade: 1,5 a cada 0,5 s (até ~36×) | ≈ 1,6/hit (1%) · tempestade contínua em 1,8–5 bl | — | Não (aura) | `Ability` tem 2 s (`40` ticks) para permitir V de novo (Senkei). Dá Resistência III por 1,6 s na ativação (`hurt`). `Byakuya.java:54,316-370`. |
| V (em Bankai) | Senkei | **3,0** *(trava `SENKEI`, `Ability` 2 s)* | 30 | ≈ 32,7 (23%) | 1,85 s `Freeze` + 1,5 s stun (`30` ticks) = **3,35 s** | Não (cercado por espadas) | **ALERTA RECARGA:** pode usar várias vezes dentro dos 20 s de Bankai (`hurt`), 24 bl! `Byakuya.java:54,373-407`. |
| Shift+V (em Bankai) | Shukei: Hakuteiken | 2,0 *(encerra Bankai → 20 s descanso)* | 42 | ≈ 45,8 (33%) | 1,2 s `Freeze` + 1,5 s stun (`30` ticks) = **2,7 s** | Dano reduz na guarda *(deveria ser bloqueável)* | Alcance 24 bl, Resistência IV durante voo (`hurt`). Encerra Bankai após 1,5 s. `Byakuya.java:56,411-477`. |

---

### Rukia — Tier 7 (Vida: 140) · `sub/Rukia.java`

*Multiplicador melee: `1,0×` (`Rukia.java:56`). Recargas já receberam leve aumento em 20/11.*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z | Some no Mai, Tsukishiro | 10,0 | 8 + 6 (estilhaço) = 14 | ≈ 15,3 (11%) | 2,45 s `Freeze` (`49` ticks) | Não (coluna do chão, raio 3 bl) | Alcance 24 bl (`hurtMagic`). `Rukia.java:57,88-116`. |
| X | Tsugi no Mai, Hakuren | 9,5 | 11 + 5 (estilhaço) = 16 | ≈ 17,4 (12%) | ~1,6–2,25 s `Freeze` | Não (avalanche 18 bl) | Cone frontal crescente (`hurtMagic`). `Rukia.java:58,120-163`. |
| C | San no Mai, Shirafune | 6,5 | 12 + 4 = 16 *(armadilha chão: 6 + 4 = 10)* | ≈ 17,4 (12%) · armadilha: ≈ 10,9 (8%) | ~2,5 s `Freeze` *(armadilha: 2,0 s)* | Dano reduz na guarda *(deveria ser bloqueável)* | Lança 17 bl (`hurtMagic`). No chão deixa estaca 8 s. `Rukia.java:59,169-229`. |
| B (toque) | Bakudo 61: Rikujokoro | 9,5 | 4 | ≈ 4,4 (3%) | **3,5 s** `Freeze` + ocupado (`70` ticks) | Não (barras surgem no corpo) | Alcance 26 bl (`hurtMagic`). `Rukia.java:61,254-274`. |
| B (seg.) | Hado 73: Soren Sokatsui | 9,5 | 16 (alvo) · 8 (área 3 bl) | ≈ 17,4 (12%) · área: ≈ 8,7 (6%) | Empurrão | Dano reduz na guarda | Alcance 30 bl (`hurtMagic`). `Rukia.java:61,276-300`. |
| V | Bankai: Hakka no Togame | 26,0 | 4 + 15 (estilhaço) = 19 | ≈ 20,7 (15%) | 2,15 s `Freeze` (`43` ticks) *(auto-freeze 1,3 s no fim)* | Não (onda raio 10 bl + aura 2 bl) | Dura 10 s com Resistência II (`hurtMagic`). Recarga bem curta para ult (26 s). `Rukia.java:60,304-372`. |

---

## Técnicas Ensinadas (`Powers.INATA`) · `power/Powers.java:89-102`

Kit do jogador sem alma (**Tier 10**, vida = **20**). Como `Powers.isSub("inata")` é `false`, **não aplica `multSubAlmas (5,5)`** (`Combat.java:271`). Fator líquido T10: `65/850 = 0,0765`. *Nuvem Brincalhona (Maki) ainda não está implementada no kit (`Powers.java:98-100`).*

| Tecla | Skill | Recarga (s) | Dano base no código | Dano real (T10, vida 20) | Controle | Bloqueável | Obs. |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Z (seg.) | Kamehameha (Mestre Kame) | 11,0 (`220` ticks) | ~4–16×(1,1–2,3) + 9–20 | ~1,3–4,5 (6–22%) | Empurrão | Dano reduz na guarda | Reusa `Goku::kamehameha` sem Kaioken e sem `subScale`. `Powers.java:93-94`. |
| X | Rasengan (Jiraiya) | 11,0 (`220` ticks) | 5×1,8 + 9 = 18 | ≈ 1,4 (7%) | 0,5 s `Freeze` + 2,6 s stun (`52` ticks) = **3,1 s** | Dano reduz na guarda *(deveria ser bloqueável)* | Reusa `Naruto::rasengan` sem clones e sem `subScale`. `Powers.java:96-97`. |

---

## Alertas

### 1. Dupla escala (ou semi-dupla escala)
- **Goku V — Genki Dama cheia (`Goku.java:481`):** `dmg = Math.max(dmg, e.getMaxHealth() * 4)` e depois `Combat.hurtMagic(...)` multiplica de novo por `5,5 × tier`. Num T6, gera **≈ 1.220 de dano real (581% da vida)**; mesmo contra divinas ignora a proporção de tier.
- **Nobara V — Ressonância Compartilhada (`Nobara.java:619,630`):** pega `amount` (dano *já* escalado do atacante), dobra com teto `Math.min(30, amount * 2)` e passa para `Combat.hurtMagic(nobara, t, back)`, aplicando `× 5,5 × tier` de novo. Qualquer golpe ≥15 em Nobara reflete **≈ 24,6 real (27% da vida T8) por golpe** para todos os ligados.

### 2. Dano direto fora do `Combat` (foge de Escudo, tier e/ou guarda)
- **Kame V — Mafuba (`Kame.java:528,534`):** chama `target.hurt(indirectMagic, Float.MAX_VALUE)` (execução ≤20%) e `target.hurt(indirectMagic, target.getMaxHealth() * 0.15f)` diretamente, burlando `Combat.hurt*` e Escudo de tier.
- **Freeza C — Death Saucer auto-dano (`Freeza.java:340`):** chama `e.hurt(magic(), 14 * damageMultiplier * subScale)` direto (sem `Combat`/`DamageDebug`).
- **Ino C — Elo Mental (`NrCommon.java:284`):** chama `other.hurt(indirectMagic, half)` direto para repassar 50% do dano recebido (intencional para não re-escalar, mas foge do `Combat`).

### 3. Execuções e one-shots práticos
- **Kame V — Mafuba (`Kame.java:496,528`):** mata instantaneamente qualquer alvo com **≤ 20% de vida**.
- **Goku V — Genki Dama cheia (`Goku.java:479-483`):** mata em 1 golpe após 60 s de carga.
- **Vegeta C — Explosão Final (`Vegeta.java:336-384`):** **377,5 de dano real (180% da vida T6)** no centro e **303 (144%)** até na borda de 20 blocos — one-shot garantido no mesmo tier.

### 4. Curas e regeneração
- **Nenhuma subalma possui cura ou regeneração** ativa ou passiva. *(Apenas custos de auto-dano: Goku Kaioken, Vegeta Explosão Final, Inumaki garganta e Nobara X/V.)*

### 5. Controles maiores que 3 segundos
| Subalma | Skill | Controle total | Obs. |
| --- | --- | --- | --- |
| Kame | V Mafuba | **13,3 s** | 6,6 s captura + 6,0 s selo + 0,7 s stun *(precisa de stun)* |
| Ino | V Teatro Mental | **12,9 s** | 10 s fantoche/selo + 0,9 s puxão + 2 s stun |
| Shikamaru | Z Kagemane / X Kage Nui | **~10,0 s** *(20 s c/ B)* | Limitado por estamina (mesmo tier: 10%/s) |
| Ino | Z Shintenshin | **8,0 s** | 7 s controle total (mesmo tier) + 1 s stun na saída |
| Kakashi | X Kamui | **~7,0 s** | 1,55 s puxão + 4 s banido + 1,5 s stun |
| Ino | X Shinranshin | **7,0 s** | Controles invertidos (3 s em boss) |
| Gaara | V Sabaku Taiso | **~6,45 s** | ~3,45 s preso na areia + 3 s stun |
| Kakashi | V Susanoo (Kamui) | **~6,4 s** | 1,9 s vórtice + 3 s banido + 1,5 s stun |
| Naruto | C Rendan | **6,4 s** | 3,8 s sequência no ar + 2,6 s stun no chão |
| Kuririn | X Taiyoken | **6,0 s** | Stun + cegueira (quebra se levar golpe pela frente) |
| Todo | B Vibraslap | **até 6,0 s** | Hitstun contínuo a cada 0,15 s |
| Kakashi | Shift+V Sennen Goroshi | **~5,8 s** | 2,3 s travado + 3,5 s stun no ar |
| Gaara | X Sabaku Kyu + Soso | **5,45 s** | 3,7 s prisão + 1,75 s stun |
| Byakuya | X Gokei (segurado) | **5,3 s** | Até 4 s congelado + 1,3 s stun |
| Kame | C Bankoku Bikkuri Sho | **5,0 s** | 2,5 s choque + 2,5 s stun |
| Todo | V Turbilhão de Palmas | **4,8 s** | 2,3 s trocas/BF + 2,5 s stun |
| Freeza | X Telecinese | **4,7 s** | Até 3 s levitação + 0,5 s queda + 1,2 s stun |
| Inumaki | Z "Pare" (segurado) | **~4,5 s** | 1,5 s + até 3 s drenando garganta |
| Gaara | B Areia Movediça | **4,0 s** | Lentidão VI + puxão ao centro |
| Choso | C Crimson Binding | **3,75 s** | 2,5 s preso + 1,25 s stun no puxão |
| Piccolo | X Braço Esticado | **3,5 s** | 0,5 s puxão + 3 s stun (cheio em boss) |
| Sasuke | C Chidori Senbon | **3,5 s** | Paralisia cheia em boss (≥2 agulhas ou marcado) |
| Rukia | B Rikujokoro | **3,5 s** | `Freeze` + ocupado |
| Naruto | B / Shift+B (Henge) | **~3,5 s** | Emboscada + Rasengan |
| Byakuya | V Senkei (Bankai) | **3,35 s** | 1,85 s `Freeze` + 1,5 s stun |
| Kakashi | B Ninken | **3,25 s** | `Freeze` + `seal` + `Stun` |
| Todo | Shift+V BF Garantido | **3,2 s** | 0,7 s `Freeze` + 2,5 s stun |
| Naruto | Z Rasengan | **3,1 s** | 0,5 s `Freeze` + 2,6 s stun |

### 6. Skills com recarga < 2 s (ou muito baixa) e dano alto
- **Shikamaru C — Papel Bomba (`0,4 s`):** limitado pela bolsa (10 papéis, 1 a cada 3 s), mas soltar 10 em leque e detonar gera **≈ 65,7 dano real (73% da vida T8)**.
- **Itadori X — Black Flash na Zona (`1,5 s`):** após o 1º acerto, recarga cai de 15 s para **1,5 s** (até 4× seguidos = **145,2 / 69% da vida T6**).
- **Byakuya V — Senkei na Bankai (`2,0 s` / trava `3,0 s`):** dá **32,7 (23% da vida T7)** a cada 3 s durante os 20 s da Bankai (**10,9 dano/s** de recarga).
- **Freeza Z — Death Beam (`3,5 s`):** rajada de 8 dá **~48–64 real (23–30% da vida T6)** → **~13,7–18,3 dano/s** de recarga!
- **Byakuya Z — Senbonzakura (`4,5 s`):** até **32,7 (23% T7)** normal / **65,4 (47% T7)** em Bankai → **7,3 / 14,5 dano/s** de recarga!
- **Miwa Z — Batto Perfeito (`5,0 s`):** **19,7 (22% T8)** sem marcas / **34,5 (38% T8)** com 3 marcas → **3,9–6,9 dano/s** de recarga!

---

## Comparação com as Divinas (recargas **Novas** propostas em 20/11)

> **Como ler "Dano/s de recarga":** mostramos `dano real ÷ recarga` em pontos absolutos **e** em **% da vida do mesmo tier por segundo**, pois uma divina T0 tem 1.755 de vida e uma subalma T8 tem 90. Para respeitar a regra *"1 skill divina vale 5 de subalma"*, nenhuma subalma deveria superar uma divina em % de vida por skill, controle ou % vida/s de recarga.

### Divinas (referência — recargas novas)

| Alma (Tier / Vida) | Maior dano real (% vida) | Maior controle (s) | Melhor dano/s de recarga *(absoluto · % vida/s)* |
| --- | --- | --- | --- |
| **Bills** (T0 / 1755) | V Hakai (execução) · B Toque ≈ 326 (19%) | 9,6 s *(X Bop)* | Z Energia: ~59 / 10 s = **5,9/s (0,34%/s)** |
| **Saitama** (T1 / 1255) | V Modo Sério (Soco Sério ~502 / 40%) | 2,5 s *(C/B; prop. 1,5 s)* | Z Socos: ~40 / 8 s = **~5,0/s (0,40%/s)** *(Modo Sério pela barra)* |
| **Joy Boy / Luffy** (T2 / 895) | C Bazooka G5 ≈ 89 (10%) *(~179 gigante / 20%)* | 1,8 s *(C/Z G5)* | Z Pistol G5: ~85 / 18 s = **4,7/s (0,53%/s)** *(G1: 14 / 4 s = 3,5/s)* |
| **Shigaraki** (T3 / 635) | Shift+V Fim do Mundo ≈ 202 (32%) | 2,8 s *(C Mãos)* | Z Toque: ~88,5 / 10 s = **8,9/s (1,40%/s)** |
| **Gojo** (T4 / 445) | C Roxo ≈ 132 (30%) *(cheio)* | 10,0 s *(Z Azul; prop. 6 s)* · 5 s *(Shift+V)* | X Vermelho: ~41,6 / 12 s = **3,5/s (0,78%/s)** *(C Roxo: 2,9/s)* |
| **Sukuna** (T4 / 445) | Z seg. Corte do Mundo ≈ 165 (37%) *(ignora defesa)* | 5,0 s *(B Cravada)* | Z Desmantelar (combo ~39): ~18,4 / 12 s = **1,5/s (0,34%/s)** *(Z seg.: 2,8/s)* |
| **Makima** (T4 / 445) | B Ritual ~40% vida *(Z Bang/Ordem: execução ≤15–20%)* | 4,9 s *(X Corrente)* | Z Bang: ~21,3 / 5 s = **4,3/s (0,96%/s)** |
| **Madara** (T5 / 310) | V Meteoro ≈ 64 (21%) *(Shift+V duplo ≈ 99 / 32%)* | 7,5 s *(X Mangekyo: 2,5+5 s)* | B Agarrão: ~28,4 / 35 s = **0,8/s (0,26%/s)** *(Shift+C: 0,45/s)* |
| **Kokushibo** (T5 / 310) | Shift+V 16ª forma ~30–40% vida *(YoruNoMiya ≈ 17 / 5%)* | 0,6 s/lua | Z 1ª forma: ~17 / 10 s = **1,7/s (0,55%/s)** |

### Subalmas (estado atual no código)

| Subalma (Tier / Vida) | Maior dano real (% vida) | Maior controle (s) | Melhor dano/s de recarga *(absoluto · % vida/s)* |
| --- | --- | --- | --- |
| **Goku** (T6 / 210) | V Genki cheia ≈ 1220 (**581%**) · Z Kaioken ~128 (**61%**) | 2,0 s *(X / C)* | Z Kamehameha: ~85 / 8 s = **10,6/s (5,06%/s)** *(Kaioken: 16,0/s · 7,6%/s)* |
| **Vegeta** (T6 / 210) | C Explosão Final ≈ 377,5 (**180%**) | 0 s *(cegueira Z)* | Z Renzoku: ~42 / 7 s = **6,0/s (2,86%/s)** *(C: 4,7/s)* |
| **Freeza** (T6 / 210) | V Supernova ~112,5 (**54%**) | 4,7 s *(X Telecinese)* | Z Death Beam: ~48–64 / 3,5 s = **13,7–18,3/s (6,5–8,7%/s)** |
| **Piccolo** (T7 / 140) | V Makankosappo ~39,2 (**28%**) · C Hellzone ≈ 34,9 (**25%**) | 3,5 s *(X Braço)* | Z Masenko: ~21,4 / 7 s = **3,1/s (2,18%/s)** |
| **Kame** (T7 / 140) | X Kamehameha Max ~51,2 (**37%**) *(V executa ≤20%)* | **13,3 s** *(V Mafuba)* | X Max Power: ~51,2 / 15 s = **3,4/s (2,44%/s)** |
| **Kuririn** (T8 / 90) | Z Kienzan (2×) ≈ 29,6 (**33%**) | **6,0 s** *(X Taiyoken)* | Z Kienzan (2×): 29,6 / 9 s = **3,3/s (3,65%/s)** |
| **Naruto** (T6 / 210) | V Rasenshuriken ~61,0 (**29%**) | **6,4 s** *(C Rendan)* | Z Oodama: 37,8 / 8 s = **4,7/s (2,25%/s)** |
| **Sasuke** (T6 / 210) | V Kirin ≈ 87,1 (**41%**) | 3,5 s *(C Senbon)* | Z Katon: 37,8 / 7 s = **5,4/s (2,57%/s)** |
| **Kakashi** (T7 / 140) | V Susanoo ~52,3 (**37%**) | **7,0 s** *(X Kamui)* | Z Raikiri: 18,5 / 10 s = **1,9/s (1,32%/s)** |
| **Gaara** (T7 / 140) | V Sabaku Taiso ≈ 49,0 (**35%**) | **6,45 s** *(V Taiso)* | Z Mão: 13,1 / 7 s = **1,9/s (1,33%/s)** *(X: 1,7/s)* |
| **Shikamaru** (T8 / 90) | C 10 Papéis ≈ 65,7 (**73%**) | **10,0 s** *(20 s c/ B)* | C Papel (1 a cada 3 s): 6,6 / 3 s = **2,2/s (2,43%/s)** |
| **Ino** (T8 / 90) | V Teatro Mental ≈ 29,6 (**33%**) | **12,9 s** *(V Teatro)* | Z Shintenshin: 7,4 / 13 s = **0,6/s (0,63%/s)** |
| **Itadori** (T6 / 210) | X Black Flash (4×) ≈ 145,2 (**69%**) | 3,0 s selo *(B)* · 2,4 s *(X)* | Z Divergente: 33,4 / 7 s = **4,8/s (2,27%/s)** *(X cadeia: 24,2/s · 11,5%/s)* |
| **Choso** (T6 / 210) | V Perfurante Máximo ≈ 63,9 (**30%**) | 3,75 s *(C Binding)* | Z Perfurante: 29,0 / 10 s = **2,9/s (1,38%/s)** |
| **Todo** (T6 / 210) | B Vibraslap ~169,9 (**81%**) · V ≈ 74,8 (**36%**) | **6,0 s** *(B)* · 4,8 s *(V)* | B Vibraslap: ~169,9 / 35 s = **4,9/s (2,31%/s)** |
| **Nanami** (T7 / 140) | C Lâmina (Overtime+Exp.) ≈ 54,9 (**39%**) | 2,0 s *(Z / C / B)* | C Lâmina: 32,7 / 10 s = **3,3/s (2,33%/s)** *(Overtime: 4,6/s · 3,3%/s)* |
| **Inumaki** (T7 / 140) | X (dormindo+cong.) ≈ 43,1 (**31%**) · V ≈ 32,7 (**23%**) | 4,5 s *(Z seg.)* | X Esmague-se: 24,0 / 12 s = **2,0/s (1,43%/s)** |
| **Nobara** (T8 / 90) | V Final (3 pregos) ≈ 39,4 (**44%**) *(+27%/hit reflexo)* | 3,0 s *(V)* | Z Pregos+Grampo: 24,6 / 8 s = **3,1/s (3,42%/s)** |
| **Miwa** (T8 / 90) | Z Batto Perfeito (3 marcas) ≈ 34,5 (**38%**) | 3,0 s *(V)* | Z Batto Perfeito: 19,7–34,5 / 5 s = **3,9–6,9/s (4,4–7,7%/s)** |
| **Byakuya** (T7 / 140) | Z Senbonzakura (Bankai) ~65,4 (**47%**) · Shift+V ≈ 45,8 (**33%**) | 5,3 s *(X Gokei)* | Z Senbonzakura: **7,3/s (5,2%/s)** · Bankai: **14,5/s (10,4%/s)** *(Senkei: 10,9/s)* |
| **Rukia** (T7 / 140) | V Bankai ≈ 20,7 (**15%**) | 3,5 s *(B Rikujokoro)* | C Shirafune: 17,4 / 6,5 s = **2,7/s (1,91%/s)** |

---

### Subalmas / skills que HOJE passam alguma divina

1. **Maior dano em % da vida do mesmo tier (divinas sem execução ficam em 19–40%):**
   - **Goku V (Genki Dama cheia):** **581%** *(bug de dupla escala)* e **Z (Kamehameha Kaioken):** **61%** — passam TODAS as divinas.
   - **Vegeta C (Explosão Final):** **180%** — passa TODAS as divinas.
   - **Todo B (Vibraslap):** **81%** — passa TODAS as divinas.
   - **Shikamaru C (10 Papéis em cadeia):** **73%** — passa TODAS as divinas.
   - **Itadori X (4× Black Flash na Zona):** **69%** — passa TODAS as divinas.
   - **Freeza V (Supernova):** **54%** — passa TODAS as divinas.
   - **Byakuya Z (Senbonzakura na Bankai):** **47%** — passa TODAS as divinas.
   - **Nobara V (Final 3 pregos):** **44%** e **Sasuke V (Kirin):** **41%** — passam o teto de 40% das divinas (Saitama/Makima).
   - Quase todas as outras subalmas (Kame 37%, Kakashi 37%, Gaara 35%, Nanami 39%, Miwa 38%, Kuririn 33%, Ino 33%, Choso 30%, Inumaki 31%) já passam várias divinas (Luffy 20%, Bills sem Hakai 19%, Madara 21–32%, Gojo 30%).

2. **Maior controle (s) (divinas ficam entre 0,6 s e 9,6 s; maioria entre 1,5 s e 5 s):**
   - **Kame V (Mafuba — 13,3 s)**, **Ino V (Teatro Mental — 12,9 s)** e **Shikamaru Z/X (10 s / 20 s c/ B)** passam **TODAS as divinas** (inclusive Bills Bop 9,6 s e Gojo Azul 10 s / prop. 6 s).
   - **Ino Z (8,0 s)** passa Madara (7,5 s), Sukuna (5 s), Makima (4,9 s), Shigaraki (2,8 s), Saitama (2,5 s), Luffy (1,8 s) e Kokushibo (0,6 s).
   - **Kakashi X (7,0 s)**, **Ino X (7,0 s)**, **Gaara V (6,45 s)**, **Naruto C (6,4 s)**, **Kuririn X (6,0 s)**, **Todo B (6,0 s)**, **Kakashi Shift+V (5,8 s)**, **Gaara X (5,45 s)**, **Byakuya X (5,3 s)** e **Kame C (5,0 s)** passam Sukuna (5 s), Makima (4,9 s), Shigaraki (2,8 s), Saitama (2,5 s), Luffy (1,8 s) e Kokushibo (0,6 s).

3. **Dano/s de recarga (com as recargas Novas das divinas, a melhor divina é Shigaraki Z: `8,9/s` absoluto e `1,40%/s` da vida; maioria das divinas está em `0,3–0,9%/s`):**
   - **Em valor absoluto (`dano real ÷ recarga`):** **Itadori X na Zona (`24,2/s`)**, **Freeza Z (`13,7–18,3/s`)**, **Goku Z (`10,6/s` / `16,0/s` Kaioken)** e **Byakuya Z/Senkei na Bankai (`14,5/s` / `10,9/s`)** superam até o maior valor absoluto de todas as divinas (Shigaraki Z `8,9/s`, Bills Z `5,9/s`) mesmo sendo Tier 6–7!
   - **Em % da vida por segundo de recarga (`% vida / s`):** **20 das 21 subalmas** (todas exceto Ino `0,63%/s`) superam a maioria das divinas (`0,26–0,96%/s`), e **17 subalmas** superam inclusive a melhor divina (Shigaraki Z `1,40%/s`) — com destaque extremo para **Itadori X (11,5%/s)**, **Byakuya Z Bankai (10,4%/s)**, **Freeza Z (8,7%/s)**, **Miwa Z Perfeito (7,7%/s)** e **Goku Z Kaioken (7,6%/s)**.

> **Conclusão:** com as recargas das divinas subindo ~2,5–3×, as subalmas ficaram com **recargas 2× a 4× menores** e **% de dano por skill igual ou maior** (por causa do `multSubAlmas = 5,5` somado a recargas de 3,5–10 s). O rebalanceamento das subalmas vai precisar atuar nas três frentes: subir recargas, ajustar `multSubAlmas`/danos-base (e corrigir os bugs de dupla escala/dano direto) e limitar controles acima de 3 s.
