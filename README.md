# battlegroundsmod

Mod de Minecraft **1.21.1** para **NeoForge 21.1.264** (`net.neoforged.moddev` 2.0.148, `mod_id=battlegroundsmod`, versão `0.1.0`).

## Equipe

- **Stark**: criador, direção criativa, testes e armas.
- **Claude**: Tech Lead / Arquiteto (arquitetura, issues e code review).
- **Gemini**: Engenheiro Executor (implementação via issues e PRs).

## Como colaborar

Seguimos o protocolo definido em [`AGENTS.md`](AGENTS.md):

1. Abrir ou receber uma **Issue** no GitHub.
2. Criar uma branch dedicada no padrão `feat/issue-N` (sem commitar direto na `main`).
3. Implementar, testar localmente e abrir um **Pull Request** com `Closes #N` na descrição.
4. Aguardar o code review do Tech Lead e o merge na `main`.

## Compilando com Gradle

### Requisitos

- **JDK 21** (toolchain configurada no Gradle).

### Build

```bash
./gradlew build        # Linux / macOS
gradlew.bat build      # Windows
```

O `.jar` do mod é gerado em `build/libs/` (`battlegroundsmod-0.1.0.jar`).

### Tarefas de desenvolvimento

- `./gradlew runClient` — inicia o cliente do Minecraft com o mod carregado.
- `./gradlew runServer` — inicia o servidor dedicado (`--nogui`).
- `./gradlew runGameTestServer` — executa o servidor de gametests.
- `./gradlew runData` — executa o datagen (saída em `src/generated/resources`).

*(No Windows, use `gradlew.bat` no lugar de `./gradlew`.)*

### Download da build mais recente (`latest`)

O jar gerado automaticamente a partir da `main` pode ser baixado em:
[`battlegroundsmod-0.1.0.jar`](https://github.com/starkinhon/battlegroundsmod/releases/download/latest/battlegroundsmod-0.1.0.jar)

### CI

O workflow [`.github/workflows/build.yml`](.github/workflows/build.yml) compila e valida cada Pull Request automaticamente.

## Licença

All Rights Reserved. Pode jogar e gravar vídeos à vontade, mas não reupar o jar
nem copiar código/assets sem permissão. Detalhes em [`LICENSE`](LICENSE) e
aviso de mod feito por fãs em [`NOTICE.md`](NOTICE.md).
