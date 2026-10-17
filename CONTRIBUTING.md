# Contribuindo com o vbrasfoot

Obrigado por contribuir! Leia `README.md`, `roadmap/ROADMAP.md` e `VALIDACAO.md`
antes de propor mudancas. O projeto e experimental e tem como alvo exclusivamente
o Brasfoot22-23 identificado pelo perfil SHA-256 documentado.

## Antes de comecar

- Abra uma issue para discutir mudancas grandes, novas capacidades ou API publica.
- Consulte issues e PRs existentes para evitar trabalho duplicado.
- Use sua propria copia local do jogo para testes. O repositorio nao fornece o jogo.
- Nao publique saves, executaveis, classes, recursos proprietarios, credenciais ou
  logs com dados privados. Nao contribua mecanismos de bypass de registro/licenca.
- Mods Java sao codigo confiavel, nao uma sandbox.

## Ambiente e fluxo de trabalho

1. Instale um JDK 17+ com `java`, `javac` e `jar` no PATH e use PowerShell no Windows.
2. Faca um fork e clone-o. Para testes locais do jogo, mantenha o layout descrito
   no README; nao copie artefatos proprietarios para arquivos versionados.
3. Crie uma branch a partir de `main` atualizada, por exemplo:

   ```powershell
   git switch main
   git pull --ff-only
   git switch -c fix/descricao-curta
   ```

4. Faca alteracoes pequenas, com testes e documentacao proporcionais ao escopo.
5. Execute:

   ```powershell
   .\build.ps1
   git diff --check
   ```

6. Quando o ambiente permitir, execute `test.ps1 -IncludeUi`. Testes com saves e
   jogo real devem seguir `roadmap/P0-VALIDACAO.md`, sempre em copia isolada.
7. Use commits descritivos, preferencialmente `fix(escopo): descricao`,
   `feat(escopo): descricao`, `docs: descricao` ou `test: descricao`.
8. Envie sua branch e abra um PR para `main`, preenchendo o modelo. Informe
   explicitamente quais testes nao puderam ser executados.

## Regras de compatibilidade

- Nao modificar `bf22-23.exe` no disco nem alterar schemas de serializacao.
- Nao restaurar, apagar ou sobrescrever saves automaticamente.
- Nao assumir que classes/regras do RealBrasfoot26 existem no Brasfoot22-23.
- Mudancas em hooks exigem identidade do artefato, evidencias estaticas e teste
  controlado; nomes ofuscados nao devem vazar para a API publica.
- Round-trip offline e testes Swing sinteticos nao aprovam gameplay ou UI real.
- Separe observacoes, inferencias e pendencias; nao anuncie capacidades sem provas.

## Revisao e merge

A `main` exige PR, conversas resolvidas e historico linear. Para colaboradores,
exige uma aprovacao de outro revisor; novos commits invalidam aprovacoes antigas
e a ultima alteracao precisa de aprovacao por outra pessoa. Administradores tem
excecao de revisao somente para merge via PR (limitacao do GitHub para repositorio
pessoal); push direto, force-push e exclusao continuam bloqueados.
Prefira squash ou rebase ao integrar. Nao use force-push na `main`.

O workflow `Build and unit tests` compila loader/SDK/exemplo e executa testes
sinteticos no Windows com Temurin 17/21, sem jogo/saves. Checks obrigatorios na
protecao da branch so devem ser adicionados apos a primeira execucao aprovada.
Os comandos locais, resultados e gates reais devem constar no PR; CI nao aprova
gameplay, interface real ou compatibilidade de saves. Nao declare testes executados
apenas por existir um checklist preenchido.

## Licenciamento

O codigo e a documentacao originais do projeto sao disponibilizados sob a
licenca MIT; veja `LICENSE`. Ao contribuir, disponibilize suas contribuicoes
sob essa mesma licenca. Nao remova avisos existentes nem inclua codigo de
terceiros sem identificar sua origem e termos compativeis com a licenca MIT.
Dependencias de terceiros mantem suas proprias licencas.
A licenca do loader nao concede direitos sobre o jogo proprietario, suas
classes, recursos, executaveis ou saves, nem autoriza sua redistribuicao.
