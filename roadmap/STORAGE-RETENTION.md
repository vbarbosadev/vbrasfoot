# P1/P7 — inventario de armazenamento (somente leitura)

Estado: implementado inventario; **nao** implementada politica completa de limpeza/retencao.
Nao ha exclusao, rotacao, restauracao ou remocao automatica. Mods, saves, dados e backups
permanecem intactos. Decisoes de retencao e eventual limpeza exigem trabalho separado.

## Uso

```powershell
java -cp dist/vbrasfoot.jar br.vbrasfoot.Main --storage-status --game-dir C:\Jogo --home C:\Jogo\vbrasfoot
```

O home precisa existir como diretorio real acessivel. Dentro do gameRoot, somente
`gameRoot/vbrasfoot` e permitido; home externo e permitido, mas nunca o gameRoot ou
seu ancestral. O comando retorna antes de perfil, runtime, mods, diagnosticos, locks,
logs, cache ou backup. Nao precisa dos binarios do jogo para inventariar um home.
Nao combinar com outros modos, safe-mode, sonda ou argumentos do jogo.
O wrapper `run.ps1` nao foi alterado neste gap; use a CLI Java diretamente para
garantir o caminho somente leitura, independentemente de preparacoes feitas por wrappers.

Categorias fixas: `logs`, `backups`, `cache`, `verification`, `diagnostics`, `data`, `mods`.
Somente essas subarvores sao percorridas: nao se lista o home para descobrir categorias,
nao se percorrem categorias desconhecidas nem `gameRoot/sav` ou recursos originais.
Nao se leem conteudos, manifestos JAR ou nomes privados para exibicao. Saida agregada
por categoria: arquivos regulares, diretorios (incluindo raiz da categoria), bytes
logicos e estado. Bytes nao representam espaco fisico alocado, deduplicacao, compressao
ou quotas; hard links podem ser contados mais de uma vez. Nao existe total do disco/home.

`AUSENTE` significa categoria inexistente; nao e criada. `COMPLETO` cobre a categoria
conhecida na observacao. `INCOMPLETO` e `RECUSADO` sempre qualificam valores como parciais.
Relatorio parcial e erro de CLI (exit 1), nao sucesso silencioso. Erros de home tambem
recusam a operacao, com orientacao para caminho/permissoes, sem expor nomes privados.

## Limites e seguranca

- Limite global de **100.000 entradas** existentes, incluindo raizes de categorias;
  ausentes nao consomem entradas. Limite cooperativo de **10 segundos** de varredura.
  Memoria da fila de diretorios limitada pelo numero de entradas; sem recursao Java.
- O tempo e verificado entre operacoes. Chamadas de filesystem bloqueadas e validacao
  inicial dos caminhos nao sao interrompidas pelo prazo: nao e um timeout rigido de I/O.
- Contadores/bytes usam `long` com soma exata; overflow/valor negativo recusa total.
- Metadados `NOFOLLOW_LINKS`, recusa de symbolic links/`isOther` e validacao de caminhos
  reais por `InstallPaths.rejectRedirects`, inclusive ancestrais, antes de abrir diretorios.
  Junctions Windows resolvidas como alias de caminho sao recusadas antes de traversal.
  Nao se usa `InstallPaths.resolve`, pois ele tambem varreria categorias desconhecidas.
- Java NIO padrao nao expoe um bit DOS reparse universal. Usa-se o contrato existente
  de `isOther`, symlink e comparacao `toRealPath`; reparse points atipicos dependem do
  provider e nao ha garantia para todos os tipos proprietarios. Nao afirmamos teste de
  todos os reparse tags. Aliases de caminho tambem podem ser recusados conservadoramente.
- Inventario nao e snapshot atomico; escritores podem mudar tamanho/entradas enquanto
  o comando roda. Feche o jogo/escritores e repita para uma observacao mais estavel.
  Revalidacao antes do traversal reduz risco, mas NIO sem handles no-follow atomicos
  nao elimina TOCTOU de troca maliciosa de diretorios. Nao execute em arvore alterada
  concorrentemente por adversario. Nao sao adquiridos locks que criariam arquivos.

## Validacao sintetica

`test/br/vbrasfoot/StorageInventorySelfTest.java`: tamanhos/contagens, categorias
ausentes, exclusao de desconhecidos e saves originais, CLI sem escrita (snapshot
antes/depois), exclusividade de modos, budget parcial, overflow, tipo incorreto,
home ausente/invalido, symlinks de raiz/aninhados/quebrados e junction Windows.
Cada capacidade indisponivel imprime `SKIP` explicitamente; nao equivale a sucesso.
Fixtures ficam preservadas em diretorio temporario, nunca no alvo instalado.

Compilar `src` com `javac --release 17 -encoding UTF-8` em diretorio isolado;
compilar o self-test com esse classpath e executa-lo com `java -Djava.awt.headless=true`.
Argumento opcional do teste define o diretorio pai temporario da fixture.
Suite integrada em `build.ps1` e CI Windows Java 17/21; fixtures no Temp/opencode
privado em Windows, fora do home de desenvolvimento (que recusa junctions).
Este incremento nao valida contra instalacao real nem implementa limpeza/retencao completa.
