# Teste de emprestimo na interface original — aceite parcial

Atualizacao: o bug de refresh descrito abaixo foi corrigido e retestado conforme
a secao final. Este documento preserva a reproducao original, nao a reclassifica
retroativamente como sucesso.

## Escopo e seguranca

Fixture nova em `%LOCALAPPDATA%/Temp/opencode/brasfoot-loan-fixture/full-ui-ec31f10533e74d54bce762c31c548539`.
Somente copia de EXE/recursos/semente descartavel, sem abrir saves pessoais ou
instalar loader/mod no jogo do usuario. Harness privado
`limit-qualification/FullUiLoanProbe.java`; comandos/capturas em `ui-actions.log`.

O harness chamou o entrypoint original pelo runtime do perfil e operou componentes
Swing REAIS da propria JVM na EDT. Nao usou frame sintetico, acesso a janelas de
outros processos, clicks globais, novo jogo, partida ou avancamento de calendario.
Interface carregou a semente atraves de **Jogo salvo -> Carregar**.

Nao e um teste do botao de transferencia do gerenciador (continua desativado) nem
aceite de autoridade publica. A transferencia foi explicitamente comandada pelo
harness via CareerBridge scoped handles + LegacyLoanAdapter + store/journal/gate;
owner/contexto da fixture sao ARTIFICIAIS e nao equivalem ao coordenador de Main.

## Preparacao e erros encontrados no proprio teste

- Primeira abertura apenas mostrou menu; foi reiniciada a JVM propria para adicionar
  suporte a clicks em labels (o menu original usa JLabel com listener, nao JButton).
- A primeira tentativa de carga omitiu o `.info` da fixture e o parametro de launch
  `--add-exports=java.desktop/sun.java2d=ALL-UNNAMED`, ja usado em run.ps1. Houve
  FileNotFoundException de metadata e IllegalAccessError, respectivamente.
  Corrigida a preparacao copiando o `.info` da semente e usando o parametro original.
  Nenhum byte do save mudou nessas tentativas; nao contam como carga aprovada.
- Duas JVMs proprias de preparacao foram encerradas apos verificar PID/commandline
  exatos. As sessoes de carga/efeito/save/reopen aprovadas foram encerradas pelo
  item original **Opcoes -> Sair do jogo**, sem matar processos pessoais.

## Candidato sem controle artificial de atributos

Carreira carregada pela UI: Athletico Paranaense, 33jogadores, 23/01/2022,
clock1642941578893, zero trackers e zero registros de historico de transferencia.

Diego Esser, usado em testes antigos, foi descartado como positivo desta rodada:
forca41 acima da mediana do destino, conforme regra do mod. Nao mudamos seus
atributos nem afrouxamos a policy. Consulta somente leitura encontrou candidato
natural disponivel e elegivel:

- Marios Balamotis, id observado5065, AEK Atenas,21anos, posicao0, forca24.
- Disponibilidade nativa=true; policyAPPROVE; predicado puro de elegibilidade=true.
- Request capturado dos handles de elenco consultado, nao de referencia fabricada.

## Resultado do efeito e falha visual

`loan 5065 AEK Atenas` retornou EXECUTED/native1/stateAPPLIED no harness.
Elenco real do Athletico33->34; tracker0->1; historico0->1. Tracker aponta
jogador atual Athletico/origemAEK, due1674563978893 = clock+366dias.
Clock nao mudou. Nenhuma mutacao artificial de forca/disponibilidade foi usada.

A UI original incorporou a linha do novo jogador; seleciona-lo mostrou nome,
atributos e **Emprestado ate:23 de jan.de2023**. Entretanto:

**FALHA: contador permaneceu33/35 enquanto a tabela ja tinha34linhas.**
O log Java tambem registrou `row index is bigger than sorter's row count`, coerente
com um sorter nao notificado da mudanca do model. Nao assumir que repaint ou
selecionar linha e refresh suficiente. Nao chamar metodo obfuscado de UI sem
qualificar ciclo/efeitos. Capturas:

- `before-reopen-ui-1.png`: elenco inicial33/35.
- `before-reopen-ui-2.png`: linha adicionada, contador antigo.
- `before-reopen-ui-3.png`: Marios selecionado/prazo visivel, contador33/35.

## Save e reabertura em outra JVM

1. Pela UI original: **Opcoes -> Salvar jogo**. Foram gerados `.s22`, `.sbck` e
   `.info` SOMENTE na fixture. Primary/backup byte-identicos.
2. Pela UI original: **Opcoes -> Sair do jogo**, termino normal da JVM.
3. Nova JVM, entrypoint original, **Jogo salvo -> Carregar** no arquivo produzido.
4. Tabela mostrou Marios, agora ordenado junto dos goleiros. Contador34/35 correto;
   nome/prazo mantidos. Clock1642941578893, tracker1/historico1, anchors e due iguais.
5. Nova saida normal pelo menu. Confirmado que nao resta FullUiLoanProbe dessa fixture.

Capturas finais: `after-reopen-roster.png`, `after-reopen-selected-player.png`.
Observacao e reabertura reais demonstram persistencia deste emprestimo especifico;
nao constituem protocolo geral de inclusao causal por UUID/recovery de crash.

## Integridade

- Semente reutilizavel antes/depois:
  `232c5f2fe07dd28fb538d103716668822c8314417ba0f7051f0f459a7439289e`.
- EXE da semente e da fixture antes/depois:
  `2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.
- Primary e backup produzidos pela UI:
  `3f123745d1b097e6c2590f1c8a5b65cd676afe069592d8283e985faffcaced79`.
- Nenhuma alteracao de EXE/recursos ou saves pessoais; sem partidas/data avancada.

## Decisao

APROVADO no caso: adapter sobre carreira carregada pela UI, candidato natural,
efeito observado, gravacao original e reabertura em outra JVM com loan preservado.

REPROVADO para liberacao visual imediata: contador/sorter nao atualizados apos
efeito. Antes de habilitar transferencia no painel, implementar e qualificar
refresh seguro da UI e o fluxo real do coordenador/API permissionada. A agenda
normal e o retorno apos decorrer366dias nao foram executados por esta validacao.

## Correcao e reteste do refresh

`LoanRosterRefresh` foi conectado ao pos-check do `LegacyLoanAdapter`. Antes do
efeito captura a tela de elenco original ativa, apenas se pertence a um dos dois
clubes, e a identidade da linha selecionada enquanto o sorter ainda e valido.
Depois do efeito, na MESMA EDT, limpa selecao, emite `fireTableDataChanged` no
modelo original, restaura selecao por identidade (fallback seguro para removido/
vazio) e chama a rotina delimitada original do contador `a.eg.pK`.

Inspecao por javap confirmou que pK apenas le elenco/tamanho e seta o JLabel Ef;
nao chama calendario, save, init ou construtor de carreira. A classe a.eg e o
modelo b.Q passaram a ter fingerprints exatos exigidos pelo adapter. Somente
painel/model originais do mesmo classloader e janela db sao considerados; ausencia
de tela aplicavel e NOT_VISIBLE, nao erro de transferencia. Status de refresh
UPDATED/FAILED e separado do resultado do efeito: falha visual nunca autoriza
repetir um emprestimo nem converte uma transferencia observada em rollback.

Fixture NOVA: `brasfoot-loan-fixture/refresh-ui-e43f7b0b5d95431897327373aa973671`.
Semente sem alteracoes, carga pela UI, Marios5065 do AEK, mesmos atributos/policy,
contexto ARTIFICIAL de harness como no teste anterior. Resultado:

- EXECUTED/native1/APPLIED/refreshUPDATED.
- Contador exibiu34/35 IMEDIATAMENTE, sem reabrir ou trocar tela.
- Selecao anterior Mycael preservada; nova linha33 selecionavel e card mostrou
  Marios/emprestado ate23/01/2023. Mapeamentos do sorter atualizados.
- Log da JVM sem o aviso anterior `row index is bigger than sorter's row count`.
- Clock1642941578893/data23/01/2022, tracker1/historico1/elenco34.
- Capturas `ui-1.png` (selecao preservada) e `ui-2.png` (novo jogador selecionado).
- Save pela UI primary/backup iguais, hash
  `3f123745d1b097e6c2590f1c8a5b65cd676afe069592d8283e985faffcaced79`, igual
  ao caso anterior sem refresh. EXE intacto. Saida normal pelo menu.

`LoanRosterRefreshSelfTest`:7checks aprovados, modelo com lista viva para adicao/
remocao/vazio, selecao por identidade, sort keys ativas, ausencia de tela e EDT.
Incluido no build. Suite completa iniciada em background com as fontes finais.

O bloqueio especifico de contador/sorter deste caso esta resolvido. Isso nao
ativa o botao do gerenciador nem qualifica automaticamente Main/permissoes/recovery
causal ou a agenda de retorno. Nao houve instalacao pessoal.
