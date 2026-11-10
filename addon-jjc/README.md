# Souls x JujutsuCraft (addon)

Addon Forge 1.20.1 para o **JujutsuCraft ver51**: o Gojo e o Sukuna do mod Souls viram duas técnicas novas do JujutsuCraft.

- No livro de técnicas do JujutsuCraft (2ª página) aparecem **Gojo Souls** e **Sukuna Souls**.
- As skills entram no seletor de técnicas do JujutsuCraft (mesma tecla de trocar, Shift volta) e saem com a tecla de usar técnica, gastando energia amaldiçoada.
- O grau do JujutsuCraft escala o dano das skills (Grau Especial = dano cheio do Souls).

| Seletor | Gojo Souls | Custo | Sukuna Souls | Custo |
|---|---|---|---|---|
| 3 | Azul (segurar carrega) | 80 | Desmantelar (combo, cântico segurando) | 40 |
| 4 | Vermelho | 120 | Desmantelar: Cortes no Oponente | 150 |
| 5 | Roxo | 400 | Clivar | 120 |
| 6 | Azul + Black Flash | 150 | Fuga | 500 |
| 7 | Vermelho à Queima-roupa | 150 | Cravada | 200 |
| 8 | Expansão de 0,2 s | 600 | | |
| 20 (domínio) | Vazio Infinito | 1250 | Santuário Malevolente | 1250 |

Os golpes 1-3 do JujutsuCraft e "cancelar domínio" continuam no seletor. O Duelo de Expansões funciona igual ao do Souls.

## Código

- O código dos kits é o do Souls (`src/main/java/com/starkinhon/soulsjjk`), trazido de NeoForge 1.21.1 através de `compat/`.
- `jjc/` liga ao JujutsuCraft (livro, seletor, disparo, energia, grau); `mixin/jjc/` entra nos procedures do JujutsuCraft.
- `jjcapi/` são só assinaturas das classes do JujutsuCraft para compilar (ficam fora do jar).
- Build: GitHub Actions (`.github/workflows/addon-jjc.yml`), o jar sai na release `jjc-addon`.
