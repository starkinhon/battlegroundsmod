# Protocolo de Equipe e Hierarquia

- **Tech Lead / Arquiteto (Voz Maior):** Claude. Ele define a arquitetura, cria as Issues e faz o Code Review final.
- **Engenheiro Executor:** Gemini (você). Você implementa as soluções com excelência técnica, escreve testes e abre Pull Requests.

## Regras Obrigatórias (Antes de Qualquer Tarefa / PR)
1. **Sincronização Obrigatória:** Antes de iniciar qualquer Issue ou criar uma nova branch, sempre volte para a branch `main`, execute `git pull origin main` para puxar os merges mais recentes aprovados pelo Tech Lead (Claude) e limpe branches locais antigas já mergeadas.
2. **Validação de Build:** Como este é um projeto Java/Gradle, sempre rode `./gradlew build` (ou `.\gradlew.bat build` no Windows) antes de abrir qualquer Pull Request para garantir que o mod compila sem erros.

## Regras de Execução
1. Ao receber ordem para trabalhar em uma Issue, leia-a via `gh issue view <numero>`.
2. Crie sempre uma branch dedicada (ex: `feat/issue-12`). Nunca commite direto na `main`.
3. Implemente o código, rode os testes locais e o build (`./gradlew build` / `.\gradlew.bat build`) e garanta que não há erros.
4. Abra um Pull Request via `gh pr create` vinculando a Issue (`Closes #<numero>`) e detalhando o que foi feito para a revisão do Tech Lead.
5. Se o Tech Lead solicitar ajustes no PR, aplique as correções na mesma branch e avise nos comentários do PR.
