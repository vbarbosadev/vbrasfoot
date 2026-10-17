# Validacao — 16/10/2026

## Executado

- `build.ps1`: compilacao Java 17, JAR do launcher, JAR publico da API, JAR do inspetor; **24 testes unitarios aprovados**.
- `run.ps1 --validate-only`: perfil e hash corretos; entrada `best.h2`, bibliotecas e assinaturas do bridge resolvidas; mod do inspetor reconhecido sem inicializacao de seu codigo.
- `test.ps1 -IncludeUi`: integracao Swing com o JAR real do inspetor e uma janela sintetica. Menu anterior preservado, duas acoes instaladas sem duplicacao, consulta executada e tabela nao editavel com o jogador esperado.
- `run.ps1 --check-save sav\Flamengo.s22`: leitura de ambas as raizes com o Kryo embarcado, regravacao de uma copia e releitura. **8.395 clubes / 193.831 jogadores**; snapshots publicos identicos antes/depois. Nenhuma interface de jogo iniciada.
- Fixture com JAR quebrado: `--safe-mode --validate-only` ignora o arquivo e termina com sucesso; validacao normal rejeita com exit code 1.

Relatorio detalhado do save: `verification/save-14473687435780074918/report.properties`.

## Integridade dos originais

Hashes ao final, iguais aos observados antes da implementacao:

```text
bf22-23.exe
2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c

sav/Flamengo.s22 e sav/Flamengo.sbck
f9918072e724521a6ce5efaad8774db78fa2b2bd704e7acf0007f58fdcfcfcc9
```

## O que NAO foi comprovado

O round-trip **nao foi identico em bytes** (`byteIdentical=false`). A causa dessa diferenca nao foi estabelecida. A igualdade dos snapshots verifica somente os campos expostos pela API, nao todos os campos do grafo, competicoes e historicos. Portanto nao se afirma equivalencia completa da carreira a partir desse teste.

Ainda falta executar uma sessao real numa copia completa do jogo: carregar a carreira, consultar o painel, avancar, salvar pela rotina normal e reabrir pelo executavel original. O teste Swing usa uma janela sintetica, nao a janela real do jogo. Nao foi simulada uma partida nem feita gravacao pela interface original.

A arquitetura evita transformar classes ou substituir serializadores para reduzir o risco, mas o release permanece experimental ate essa validacao interativa.

## Correcao do menu apos relato do usuario

O primeiro teste Swing nao identificava a janela correta: registrava uma janela sintetica via `best.n.b(JFrame)` e o loader consultava `dc()`. O bytecode de `best.S` mostra que a janela real da carreira e registrada via `best.n.a(JFrame)`, acessivel por `db()`. O loader foi corrigido para `db()` e passou a registrar a instalacao no log. O teste de regressao usa duas janelas distintas, exige que `dc()` fique intacta e so permite a instalacao depois do registro em `db()`. A confirmacao visual na sessao real ainda deve ser feita apos reiniciar o launcher atualizado.

## Menu interno em vez de barra externa

O usuario confirmou que o menu apareceu apos a correcao de `db()`, mas numa barra externa. A integracao foi substituida por um botao na JToolBar original de `a.eg` (`ve`), antes de Opcoes (`HX`). `test.ps1 -IncludeUi` passou novamente: 24 testes unitarios, validacao do perfil e regressao Swing com botao interno, sem criar menu externo, preservando os controles originais e evitando duplicacao. A fixture representa somente o painel/barra com os mesmos membros; o posicionamento visual e a disponibilidade de espaco na tela real ainda precisam da confirmacao do usuario.

## Incremento P0 — diagnostico e investigacao (16/10/2026)

Implementado diagnostico por sessao antes da verificacao do perfil, descoberta de
mods e extracao do runtime. Inclui identidade esperada/verificada, JVM/argumentos,
etapas, stdout/stderr limitados e log rotativo separado. O hook de fechamento nao
consulta Swing nem executa callbacks de mods. Falhas anteriores a esse ponto e
erros gravados somente pelo jogo em `erros.log` permanecem fora dessa consolidacao.

Executado nesta rodada:

- `test.ps1 -IncludeUi`: **46 testes aprovados**, validacao do perfil e integracao
  Swing sintetica aprovadas. Inclui captura limitada, erros anteriores a extracao,
  restauracao de streams e rejeicao de home interno antes de criar diretorios.
- JVMs filhas encerradas por retorno, `System.exit` na thread principal e
  `System.exit` na EDT: diagnostico fechado, sem timeout. Isso testa o hook do
  loader, nao todas as rotinas de encerramento do jogo real.
- `prepare-validation.ps1 -Preview`: inventariados 34.342 arquivos, 258.044.198
  bytes. Fixture reduzida de copia aprovada com executavel correto, diretorio
  vazio, caminhos com espacos/acentos e rejeicao de destino existente. Essa fixture
  **nao e um jogo completo nem foi iniciada**.
- Preparacao da copia real excedeu os limites de 240 e 600 segundos; destinos
  parciais preservados no diretorio temporario do OpenCode. Houve tambem uma
  tentativa inicial interrompida por conflito com a variavel PowerShell HOME,
  corrigido no script. Nenhuma dessas tentativas aprova a copia real ou o P0.
- Verificacao offline do save atual:
  `verification/save-6138189412788400229/report.properties`.
  8.395 clubes / 193.831 jogadores; round-trip, repeticao das mesmas raizes e
  segunda geracao identicos em bytes. Snapshots publicos iguais nas duas geracoes.
- Reproducao com o input preservado do teste antigo:
  `verification/save-4251315688372723433/report.properties`.
  Diferenca original reproduzida, primeiro offset 8.911.369, tamanho mantido
  em 74.283.590 bytes, mesmo hash de round-trip do teste anterior. Repeticao das
  mesmas raizes e segunda geracao estaveis em bytes; snapshots publicos iguais.
  **Causa semantica da diferenca ainda nao determinada.**

Hashes conferidos antes/depois desta rodada, inalterados:

```text
bf22-23.exe
2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c

sav/Flamengo.s22 e sav/Flamengo.sbck
c645b7eaf7d5a04a60349fa98d8575092c9643515059854d55cf63ad230c19ef
```

O save atual ja diferia do hash historico antes desta implementacao. Nao houve
restauracao, gravacao de saves originais nem lancamento do jogo real nesta rodada.

P0 permanece aberto. Roteiro interativo em `roadmap/P0-VALIDACAO.md`; P1 a P6 nao
foram implementadas nem aprovadas por este incremento.

## Incremento experimental P1/P2 — 16/10/2026

- SDK/loader 0.2.0, API major 1. Exemplo compilado somente contra o JAR publico.
- `test.ps1 -IncludeUi -Save '..\Brasfoot22-23\sav\Flamengo.s22'`:
  **64 testes aprovados**, perfil e Swing sintetico aprovados; teste adicional de
  registro/remocao de acao passou. Verificador offline aprovado, inclusive todas
  as paginas de clubes e paginas de jogadores do primeiro clube humano iguais
  ao snapshot. Sem gameplay ou reabertura no EXE original.
- Relatorio: `verification/save-10258633321303238868/report.properties`.
  8.395 clubes / 193.831 jogadores; round-trip identico em bytes. EXE/save originais
  com os mesmos hashes do incremento anterior.
- A primeira consulta paginada por ID revelou ambiguidade: 8.395 clubes geraram
  apenas **um valor distinto** no getter legado historicamente chamado de ID.
  Corrigido para recusar selecao ambigua; `playersAt(club.index(), ...)` usa posicao
  na lista atual. Nao e identidade duravel e nao protege troca de carreira entre
  chamadas. Semantica completa de IDs permanece desconhecida.
- Corrigida uma corrida preexistente entre o hook do diagnostico e o hook de
  LogManager: `termination.txt` registra o motivo mesmo se handlers ja fecharam.
- Pacote criado por `release.ps1`, sem arquivos do jogo/runtime Java, contendo
  SDK, licenca e hashes. Instalador valida hashes das fontes do pacote antes de
  escrever; esses hashes nao sao assinatura de autenticidade.
- `test-portable.ps1`: instalacao/preview, validacao instalada sem referencia ao
  checkout, reinstalacao, modo seguro com JAR quebrado, arquivo gerenciado alterado,
  lock ativo, mover diretorio, preview/desinstalacao preservando dados e hash do EXE
  aprovados. Fixture privada com EXE correto, nao com todos os recursos/saves.
  Ambiente: Windows, PowerShell 5, Java 21.0.12, caminhos com espacos/acento.
  O CMD seguro tambem foi executado em `--validate-only` com sucesso.
  Pacote revisado: `dist/portable-0.2.0-reviewed/`.
  Evidencias em `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-e4e321db8de641108d42b9f9f36eb6ff`.
  Teste adicional: junction de home para diretorio protegido recusada tanto pelo
  instalador como pelo loader, antes de qualquer escrita no destino protegido.

Limites: substituicao atomica por arquivo, nao por release; atualizacao interrompida
exige revisao manual. Sem matriz completa de junctions/links, somente leitura, falta
de espaco, falhas de escrita e JVMs 17+. O inspetor foi atualizado nesta finalizacao
para filtro/paginacao de 100 clubes e ate 500 jogadores do clube selecionado;
nao chama mais snapshot completo no runtime atual.
O teste Swing do inspetor atualizado foi reexecutado com sucesso; sua compilacao
usa apenas o JAR publico. Os 64 testes unitarios foram reexecutados na revisao.
Nao ha ampliacao por suposicao de calendario/financas/historicos, servicos entre
mods, cancelamento de tarefas ou eventos de carreira. P0/P1/P2 nao aprovados como
fases completas. Guias: `roadmap/PORTABLE.md` e `roadmap/SDK-CONTRACT.md`.

## Fechamento de bloqueios P0/P1 — 17/10/2026

- `prepare-validation.ps1` concluiu a copia completa: **34.342 arquivos**,
  **258.044.198 bytes**, com hash da origem antes/depois e do destino. A copia usa
  helper proprio C# com quatro tarefas no maximo; nao executa o jogo.
- Copia privada: `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-p0-complete-20261017`.
  `baseline.json` registra o inventario. Pacote instalado e atualizado somente
  nessa copia; inspetor 0.2.0 reconhecido no `--validate-only` instalado.
- Conferencia adicional apos instalar/atualizar: todos os **34.342 arquivos** do
  baseline permanecem identicos na origem e na copia; nenhuma divergencia.
  Evidencia privada: `pre-session-integrity.json` no diretorio da copia.
- `test.ps1 -IncludeUi`: **66 testes** aprovados e Swing sintetico aprovado,
  incluindo bloqueio do Main antes de escrever logs em instalacao incompleta.
- `test-portable.ps1` com `dist/portable-gates-20261017`: matriz anterior aprovada,
  mais falha real de substituicao por arquivo bloqueado, publicacao parcial,
  preservacao de journal, launcher bloqueado e rollback explicito dos arquivos
  gerenciados. Hash anterior da licenca da fixture restaurado e reinstalacao aprovada.
- Recuperacao com backup corrompido recusada antes de alterar o alvo;
  `-Complete` recusou geracao parcialmente publicada. Preview preservou journal.
  Saves e dados do usuario nao sao alvos de nenhuma recuperacao.
- Log privado do teste: `build/portable-gates-20261017-rerun.log`.
  Fixture: `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-eb0b2efddfe84291916d873e7a7bcb87`.

Recuperacao por journal nao promete transacao atomica de toda a release. Caminho
de conclusao depois de publicacao completa e limpeza interrompida implementado,
mas nao testado em cada ponto de interrupcao. Matriz total de falta de espaco,
somente leitura e JVMs suportadas continua pendente.

O usuario escolheu sessao real guiada. Ambiente instalado pronto, mas **nenhuma
partida/interface real ou reabertura no original foi aprovada nesta rodada**.
P0 segue aberto; P3/P4/P6 nao foram implementados sem os gates/evidencias exigidos.

### Sessao guiada — referencia visual no EXE original

Usuario informou abertura por `original.cmd` da copia isolada e forneceu a captura
privada `logs/imgs/teste01.png` (nao versionada). Observado na imagem:

- Carreira Flamengo carregada, data **23/01/2022**.
- Financas exibidas: **18 milhoes 356 mil**; elenco **27/35**.
- Proximo jogo: Sampaio Correa - RJ, Carioca, primeira rodada, em casa.
- Confianca da diretoria 95%, torcida 85%; controles originais visiveis.
- Captura de 1920 x 1080; escala do Windows nao informada.

Isso estabelece somente a referencia visual fornecida pelo usuario para o original.
Ainda nao comprova menu do loader, consulta, avancar/salvar/reabrir, outras escalas,
nem comparacao completa de calendario, competicoes e historicos. Proximo passo:
fechar sem salvar e carregar a mesma carreira pelo launcher **instalado** da copia.

### Sessao guiada — menu interno e inspetor

Usuario confirmou o botao e forneceu `logs/imgs/teste02-inspetor.png` (privada,
nao versionada). A captura mostra:

- Botao vbrasfoot na barra interna, imediatamente antes de Opcoes; nenhuma barra
  externa adicional observada no recorte.
- Data 23/01/2022; inspetor aberto com filtro `flamengo`, seis clubes encontrados,
  Flamengo identificado como clube humano e jogadores apresentados na tabela.
- Exemplos visiveis: Agustin Rossi, idade 30, goleiro, valor 26.364.000; Dyogo
  Alves, idade 22; nomes/idades correspondentes aos trechos da referencia visual.

O recorte nao mostra financas/elenco total nem todos os controles da barra.
Troca para Agenda/retorno sem duplicacao, outras resolucoes/escalas, ausencia de
erros em logs e salvar/reabrir ainda precisam de confirmacao. `logs/imgs/as.png`
mostra outro estado (financas/elenco diferentes) sem associacao declarada a esta
sessao; nao foi usada como referencia nem como prova de divergencia do loader.

Usuario confirmou na sessao guiada: retorno de Agenda ao elenco sem duplicacao
do botao, financas 18 milhoes 356 mil e elenco 27/35 iguais a referencia original,
sem erro relatado nessas acoes. Confirmacao do usuario, nao captura independente
de todos esses passos nem verificacao completa de campos/logs. Proximo gate:
concluir partida na copia, salvar pelo launcher e reabrir no EXE original da copia.

### Sessao guiada — salvar pelo launcher e reabrir no original

Usuario confirmou sucesso do roteiro de concluir partida, salvar, fechar e reabrir
Flamengo pelo EXE original da copia, incluindo os dados solicitados. Nao forneceu
capturas pos-partida/reabertura nem valores finais de data/saldo/placar: resultado
interativo registrado como **confirmacao do usuario**, nao medicao independente.

Conferencias independentes apos a confirmacao:

- EXE original da origem e da copia: mesmo SHA-256 esperado, intactos.
- Saves Flamengo.s22/.sbck da origem mantem SHA-256 `c645b7eaf7d5a04a60349fa98d8575092c9643515059854d55cf63ad230c19ef`.
- Save da copia mudou para SHA-256 `43085fa25b9d96fa8fed420f9c1e1468cbc32053d1a53fecb08639a2e51d937b`.
- Sessao instalada `session-1dee82b3-4e11-4761-aedc-f28e009d408f`: perfil, backup,
  inspetor ativo, entrada real e instalacao de menu registrados; stderr vazio;
  encerramento `jvm-shutdown`. Dois registros de instalacao do menu podem decorrer
  de recriacao do painel; nao comprovam sozinhos duplicacao. Usuario confirmou
  previamente ausencia de botao duplicado apos troca de tela.
- Verificador offline do save produzido aprovado: 8.395 clubes / 193.831 jogadores,
  round-trip identico em bytes. Relatorio privado na copia em
  `vbrasfoot/verification/save-7108773662264221777/report.properties`.

Ainda falta repetir em modo seguro/sem mods, resolucoes/escalas diferentes,
comparacao ampla de calendario/competicoes/historicos e investigacao semantica
da divergencia antiga. P0 nao aprovado integralmente por este teste unico.

### Sessao guiada — modo seguro/sem mods

Usuario confirmou sucesso da repeticao pelo launcher seguro: carregar, concluir
outra partida, salvar, fechar e reabrir no EXE original da copia, sem erro relatado.
Nao forneceu valores finais numericos ou capturas apos reabertura; evidencia
interativa registrada como confirmacao do usuario.

Conferencias independentes:

- Sessao `session-40d51d91-e5e6-4515-a6af-0b2432777423`: `safeMode=true`, perfil e
  backup verificados; nenhum carregamento de mod/menu registrado; stderr vazio
  e encerramento `jvm-shutdown`.
- Save da copia passou a SHA-256 `52837913efd1f59661f0d45a177033fcb497d3c8e64753b6908fe49a9872eeed`.
- EXE e saves da origem continuam com os hashes anteriores, intactos.

Os dois cenarios reais foram confirmados na mesma copia, sequencialmente (o modo
seguro partiu do save produzido no cenario normal, nao de um baseline restaurado).
Nao houve restauracao automatica. Ainda pendentes outras resolucoes/escalas,
comparacao ampla de historicos/competicoes/calendario e causa da diferenca antiga.

### Sessao guiada — resolucao menor

Usuario informou que o jogo nao permite reduzir a janela. O teste foi feito
alterando a resolucao de exibicao e reabrindo o launcher instalado da copia:
**1280 x 720**, com confirmacao de que vbrasfoot, Opcoes e demais botoes continuam
acessiveis. Confirmacao do usuario; captura dessa resolucao nao fornecida e escala
do Windows nao informada. A referencia anterior foi captura de 1920 x 1080.
Cobertura de resolucao menor registrada; multiplas escalas continuam pendentes.

## Investigacao da diferenca antiga e testes de erro — 17/10/2026

Executado `analyze-save.ps1` nas copias antigas preservadas. Inputs com hashes
conferidos antes/depois, intactos. Sete autotestes do comparador passaram.

- Primeiro offset divergente 8.911.369 localizado em `best.F.dm` (String de nome
  vazia). Trecho original `01 81`, round-trip `B1 37`: nova String vazia versus
  referencia ja existente. Fixture com duas Strings vazias distintas reproduziu
  identidades fundidas na leitura, valores preservados e bytes diferentes ao gravar.
- Output da sonda instrumentada foi **identico por SHA-256** ao round-trip anterior
  nao instrumentado: `fc23e9d74ee92d12e4da9565f9c8c2b70e28249f731a1898bd7bec09a3990b47`.
- Comparacao offline dos grafos decodificados: 31.742.740 comparacoes, 20.578.147
  campos serializados, 5.228.120 objetos mutaveis unicos somados por raiz;
  nenhuma divergencia, tipo nao suportado encontrado ou budget excedido.
- Sonda inicial limitada a 30 milhoes de comparacoes foi explicitamente incompleta;
  o resultado acima e da reexecucao com budget ampliado, sem truncamento observado.

Cobertura/exclusoes e comando reproduzivel em `roadmap/SAVE-ANALYSIS.md`.
Campos Kryo serializados, listas/arrays e aliases mutaveis comparados; String
por valor, Calendar/TimeZone por propriedades documentadas. Nao contempla campos
transient/static nem atribui cada diferenca binaria restante a um mecanismo unico;
nao e garantia universal de compatibilidade. Nenhuma instrumentacao dessas sondas
foi inserida no launcher ou SDK.

Relatorios privados em `build/save-analysis-d1b7a42db9754a2faaf5de094321024a/`.

`test-portable.ps1 -Package dist/portable-gates-errors-20261017` aprovado:

- Permissao de escrita negada por ACL NTFS real em app da fixture; ACL original
  restaurada em finally; instalacao recusada e recuperacao explicita aprovada.
- Falha real por arquivo bloqueado, journal preservado, launcher recusado,
  rollback explicito e reinstalacao aprovados.
- Backup gerenciado corrompido e journal com alvo `sav/Flamengo.s22` recusados
  antes de alterar os alvos; nao existem saves reais nessa fixture.
- Conclusao de limpeza apos publicacao completa e staging antes do journal:
  aprovados em **estados sinteticos**, nao queda de energia real.
- Espaco: reserva maior que disco disponivel recusada antes de criar staging;
  nao esgotou o disco nem simulou ENOSPC durante escrita.
- Demais cenarios de portable (preview, mover, modo seguro, hashes, locks,
  desinstalacao preservando dados e junction) continuam aprovados.

Fixture privada: `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-b87e10c9a3d0465c8ae2ffac8f65400e`.
Log: `build/portable-gates-errors-20261017.log`. Build: 66 testes aprovados.

P0/P1/P7 permanecem com cobertura incompleta de escalas, JVMs e falhas em todos os
pontos. Os resultados acima fecham a investigacao delimitada da diferenca antiga
e ampliam recuperacao; nao aprovam automaticamente todas as fases do roadmap.

## Consolidacao publicada e CI remoto — 17/10/2026

Incremento publicado em `feat/release-gates-hardening`, PR #2. Execucao remota
GitHub Actions `38996623272`: Windows/Temurin 17 e 21 aprovados para build,
66 testes sinteticos e parsing PowerShell. Sem jogo/saves ou upload privado.
Isso confirma execucao sintetica nos dois JDKs, nao gameplay nos dois runtimes.
Warnings de actions v4 obsoletas motivaram troca para v5 com SHA de commit
resolvido no repositorio oficial; execucao dessa revisao deve ser conferida aparte.

### P4 — inventario estatico inicial

`inspect-career-hooks.ps1` aprovado: EXE conhecido, cinco membros candidatos com
descriptor/Code/classe fingerprintados, artefato sintetico desconhecido recusado
antes de publicar relatorio. Scanner percorreu 1.038 classes do ZIP principal e
encontrou 11 referencias diretas selecionadas (quatro chamadas, sete putstatic).
Callers e armadilhas em `roadmap/CAREER-HOOKS.md`; relatorios privados em
`build/hook-analysis-e89d846f3cb54f2eb6fee66677841436/`.

Isso e somente evidencia estatica do artefato fornecido, nao observacao de execucao.
Nenhuma transformacao aplicada, nenhum evento de carreira habilitado, nenhum
schema/nome/campo do jogo alterado. Descarregamento e tecnica/runtime precisam
de testes controlados antes de habilitar P3/P4; resultado nao fecha esses gates.

Inspecao adicional: `best.h2` instala `components.ar`; seu handler de excecoes
nao tratadas grava HTML em erros.log e imprime o mesmo Throwable no stderr.
Portanto o caminho observado do handler chega a captura da sessao; nao se afirma
consolidacao de todo o historico/mensagens exclusivas do arquivo legado nem captura
de falhas da JVM/parsing anteriores ao diagnostico. Bytecode privado em
`build/analysis/entrypoint-bytecode.txt` e `legacy-handler-bytecode.txt`.

Revisao `e9ff212` com Actions v5 pinadas e compilacao do inventario de hooks:
CI remoto **38997162116** (push) e **38997165762** (PR) aprovados, Windows/Temurin
17 e 21. Os dois checks foram tornados obrigatorios na main (app GitHub Actions
15368, strict/up-to-date); protecoes anteriores e excecao de review por PR mantidas.
Nenhum PR integrado automaticamente. Revisoes seguintes tambem exigem checks.

## Proxima entrega P4 — sonda opt-in (17/10/2026)

Branch `feat/career-hook-probe`, baseada no merge do PR #2. Sonda desativada por
padrao; nenhum evento publico/capacidade de mods, unload ou persistencia P3.

Tecnica documentada em `roadmap/CAREER-PROBE.md`: transforma somente corpos de
`c.a` em memoria, apos hash exato e contagem de sites; nao altera EXE/cache/schema.
ASM interno do JDK 17/21 explicitamente exportado somente no opt-in. Nao afirma
que esse mecanismo seja contrato de producao ou portavel a outros JDKs.

Executado localmente com Java 21:

- Build: **69 testes gerais**, **12 de tracking/observador**, **12 de bytecode
  sintetico transformado** aprovados, sem jogo nas suites sinteticas.
- `run.ps1 --probe-career-hooks --validate-only`: classe transformada verificada
  sem iniciar UI/carreira/mods. Tentativa inicial encontrou VerifyError de frame
  no handler legado; corrigida, reexecutada e passou. Nunca foi aplicada por fallback.
- `test-career-hooks.ps1`: **21 checks** aprovados chamando metodos originais de
  I/O na EDT em fixture nova. Frame sintetico evita inicializacao da UI completa.
  Load/save/reload, backup, falha primaria capturada pelo jogo, falha de backup,
  fallback `.s22` invalido/`.sbck` valido deduplicado e argumento null verificados.
- Schema de c.a original/transformada igual por nome, campos, assinaturas,
  modificadores e superclass. Contagens de clubes/jogadores preservadas apos save/reload;
  igualdade completa do grafo/gameplay **nao** declarada por esse teste.
- Excecoes esperadas de falha primaria/backup/fallback aparecem no log; nao foram
  mascaradas. Falha do backup nao virou falha de save primario nem sucesso de backup.
- EXE/input da fixture e EXE/save da origem com hashes antes/depois iguais.

Fixture aprovada reexecutada com transcript:
`%LOCALAPPDATA%\Temp\opencode\vbrasfoot-hook-io-720847240c304ae182ed8b45132f1dd4`.
Todos os outputs/falhas sao nomes novos nessa fixture, sem restauracao ou mutacao
de save da origem. Transcript privado `loader/runtime-observations.txt` registra
sete observacoes na AWT-EventQueue-0, incluindo fallback completo com retorno
externo false, falha primaria sem completed e falha de backup com primario completed.
Frame sintetico nao e teste
de UI/gameplay, troca/unload, EXE original ou durabilidade fisica do arquivo.
Sonda ainda requer sessao real guiada antes de habilitar eventos/sidecars.

Pacote `dist/portable-career-probe-20261017` gerado e instalado **somente** na
copia completa isolada existente. Validacao instalada opt-in aprovada sem iniciar
jogo; EXE/saves da origem e save atual da copia permanecem nos hashes anteriores.
Regressao portable aprovada com a sonda desligada: logs privados em
`build/portable-career-probe-20261017.log`, fixture
`%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-2cbd948ecd7d40f0a1c9ba14f6d43ddc`.
`test.ps1 -IncludeUi` tambem reexecutado: suites sinteticas e inspetor aprovados.

### Sonda — corroboracao pela UI real

Usuario confirmou carregar Flamengo, salvar normalmente, fechar e reabrir pelo
EXE original da copia com sucesso. Sessao privada
`session-cb6c1f69-ade3-4a83-b275-9b1f070fd870` conferida independentemente:

- `careerHookProbe=true`; uma observacao load completa, sem fallback/backup,
  `AWT-EventQueue-0`, sequence 1.
- Uma observacao save completa, backup=true, **Thread-1**, sequence 2.
  Portanto gravacao real nao esta necessariamente na EDT; API/eventos futuros
  nao podem prometer EDT para todos os hooks nem gravar sidecars com essa suposicao.
- Stderr vazio e encerramento `jvm-shutdown`.
- EXE da origem/copia intactos; saves da origem mantem hash
  `c645b7eaf7d5a04a60349fa98d8575092c9643515059854d55cf63ad230c19ef`.
- Save da copia manteve hash `52837913efd1f59661f0d45a177033fcb497d3c8e64753b6908fe49a9872eeed`
  depois dessa gravacao sem avancar. Save bem-sucedido nao exige mudanca de hash;
  a observacao foi baseada no fechamento original de Output, nao timestamp/hash.

Reabertura no EXE original: confirmacao do usuario, sem captura final fornecida.
Esse teste corrobora os pontos de load/save na UI para esta sessao e ambiente;
nao fecha carreira nova, unload/troca, fallback/falhas na UI completa ou todos os
gates de P4. Nenhum evento publico ou persistencia por carreira liberado.

### Entrega limitada da sonda e mapeamento de lifecycle

Executado na mesma branch do PR #3, sem incorporar um CareerUnloaded por suposicao:

- Scanner ampliado: 1.038 classes, 139 referencias selecionadas de I/O, construcao,
  registro de janela, dispose e System.exit. Inclui dispose de Graphics/dialogos;
  nao sao 139 eventos de unload. `best.S` usa EXIT_ON_CLOSE; `a.eg.rq` chama exit(0).
  Construtor c.a e chamado por tres callers diretos e substitui raizes.
- `roots-reset` injetado somente ao concluir construtor original; SHA/site guard
  mantidos. Nao inicializa API de eventos nem afirma carreira nova pronta/unload.
- `ProbeDispatcher` remove logging da thread do hook no launcher; fila 128,
  offer sem espera, FIFO por entrada, drops/erros/descarte contabilizados.
  Close 500ms; worker daemon sem espera pela EDT. Estatisticas em arquivo privado
  separado de LogManager, nao garantia de delivery duravel ou fsync.
- Build/testes: **69 gerais**, **13 tracking**, **11 dispatcher**, **13 bytecode
  sintetico** aprovados localmente com Java 21. Inclui fila cheia, callback lento,
  close idempotente, FIFO, callback falho, offer apos close e System.exit na EDT
  em JVM filha propria com estatisticas preservadas.
- `test-career-hooks.ps1`: **26 checks** aprovados nos metodos originais na EDT
  com frame sintetico; inclui troca das referencias de raizes e snapshot vazio
  depois de construtor, entrega assíncrona pelo dispatcher (callback fora da thread
  do jogo) e ausencia de drops/falhas na fixture. Fixture:
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-hook-io-689ddf25f30745c6ab9e36d9721d5ec6`.
- `test.ps1 -IncludeUi`: suites sinteticas e inspetor aprovados sem sonda.

Source threads permanecem no record; callback de log agora roda no worker.
Ordem FIFO nao significa ordem global de sequence entre hooks concorrentes.
Callback ja executando que ignore interrupcao pode terminar depois do close;
workerAlive explicita o limite. Nao usar essa fila best-effort para gravar sidecars.
Corroboracao completa da UI/lifecycle/unload e bus publico confiavel continuam
pendentes, portanto P3/P4 nao sao anunciados concluidos.

Pacote `dist/portable-career-dispatch-20261017` gerado; matriz portable aprovada
(`build/portable-career-dispatch.log`). Instalado/validado opt-in somente na copia
completa; `probe-delivery.properties` da validacao registrou workerAlive=false,
sem aceites/drops/falhas (nenhuma operacao de jogo executada nessa validacao).
EXE/saves da origem e save corrente da copia mantiveram seus hashes anteriores.

## Proxima entrega P2 — consultas com escopo 0.3.0

Branch `feat/career-scoped-queries`, baseada na branch do PR #3 (ainda aberto ao
iniciar). Identidade somente da sessao/encarnacao observada; nao identifica save
permanentemente nem exige hooks/dispatcher. API major 1 preservada; SDK/loader
e inspetor 0.3.0. Capacidade query.scoped e loader minimo explicitamente declarados.

Testes locais Java 21:

- Suites anteriores: **69 gerais, 13 tracking, 11 dispatcher, 13 bytecode** aprovados.
- **16 testes de escopo**: raizes/listas/bridge trocados, handles expirados/forjados,
  clube movido, capacidade limitada, erro tipado atravessando EDT e default antigo
  recusando consultas scoped sem fabricar garantias.
  Erros de shrink/modificacao concorrente da lista traduzidos para STATE_CHANGED,
  sem afirmar protecao contra toda mutacao in-place.
- `test-career-hooks.ps1`: **30 checks** nos metodos originais, frame sintetico,
  incluida recusa de handle anterior apos reload e roots-reset, valores scoped
  iguais a consulta atual legada. Fixture privada:
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-hook-io-094cf9be5ded4fa39376adcc69830052`.
- `test.ps1 -IncludeUi -Save ...`: aprovado. Inspetor 0.3.0 compilado apenas contra
  SDK publico; UI sintetica mostra valores e esvazia tabela/pede Atualizar ao
  receber erro STALE_SCOPE. Isso testa tratamento da UI, nao troca real de carreira.
- Offline do save atual aprovado, 8.395 clubes / 193.831 jogadores, bytes iguais;
  primeiro clube scoped igual e handle do grafo anterior recusado. Relatorio privado
  `verification/save-16314565127216860410/report.properties`.

Registro fraco/LRU nao segura grafos antigos; ate 4.096 handles. Escopo conferido
nas leituras da EDT, nao por polling de tela ou diagnostico descartavel. Checks de
identidade/tamanho nao sao lock nem snapshot transacional: mutacoes in-place,
A->B->A entre observacoes e carga parcial podem escapar (SCOPED-QUERIES.md).
Nenhum evento novo, persistencia por carreira, schema/save alterado ou escrita de
gameplay foi introduzido. Gates de P0/P3/P4/P6 continuam explicitos.

Pacote `dist/portable-0.3.0-scoped-20261017` gerado e regressao portable aprovada
(`build/portable-0.3.0-scoped.log`); fixture privada
`%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-4998f0a1da974132a368bfaa511902fc`.
Pacote e inspetor atualizados somente na copia completa isolada e validacao normal
aprovada sem hooks. EXE/saves da origem e save atual da copia permaneceram nos
hashes anteriores. UI real 0.3.0 ainda requer confirmacao; nao foi iniciada neste passo.
Revisao final do pacote (tratamento tipado de listas alteradas) gerada em
`dist/portable-0.3.0-scoped-reviewed`, instalada na mesma copia e validada sem hooks.

### Confirmacao do inspetor 0.3.0 na UI real

Usuario confirmou carregar Flamengo e consultar jogadores com o inspetor 0.3.0,
sem erro relatado. Sessao `session-b0a6492f-1316-403a-8fc2-431cf3d9b4db`
conferida: mod 0.3.0 ativo, menu integrado, `careerHookProbe=false`, stderr vazio
na leitura. EXE/saves da origem e save corrente da copia mantem hashes anteriores.
Nao houve pedido de avancar/salvar; esta confirmacao nao testa troca de carreira,
invalidation na UI real ou todos os gates de P2/P4.

## Template independente e validador individual — 0.3.1

PR #4 foi aceito na branch da sonda, nao main. Incremento scoped aprovado foi
reaplicado sem alteracoes na nova branch `feat/mod-template-readonly` baseada na
main apos #3. Este passo leva scopes + template ao proximo PR para main.

- Template em `templates/read-only-mod`, com fontes, manifesto minimo, testes,
  build apenas contra SDK, instalacao com preview/lock sem overwrite e licenca MIT.
- `--validate-mod JAR`: independente do jogo/home; le manifesto e liga/verifica
  mainClass publica/concreta/construtor sem inicializar mod. Nao prova seguranca,
  comportamento ou conjunto de dependencias. `--validate-only` continua validando
  conjunto instalado/perfil separado.
- Teste em JVM filha propria usa mod com inicializador/constructor que falhariam
  se executados; validacao passou sem jogo e sem criar home/cache/logs.
- Loader/SDK 0.3.1 para distinguir o novo comando da versao 0.3.0; API major 1
  preservada. Template exige loader minimo 0.3.1, somente game.read/ui.register.
- Pacote inclui JAR publico, Javadocs gerados e template por allowlist de fontes;
  build/dist/lib de desenvolvimento nunca entram na distribuicao do template.

Executado Windows/Java 21:

- **75 gerais, 13 tracking, 11 dispatcher, 16 scope, 13 bytecode sintetico**
  aprovados, mais **7 testes independentes do template**. Template compila/testa
  com somente API e suas proprias classes, sem jogo/loader no classpath.
- `test.ps1 -IncludeUi`: suites e inspetor sintetico aprovados.
- `release.ps1 -Output dist/portable-0.3.1-template`: Javadocs/pacote gerados.
- `test-mod-template.ps1`: projeto copiado para fora do checkout, compilado,
  testado, empacotado, validado individualmente, instalado/validado na fixture.
  Preview nao escreve; lock ativo e JAR existente recusados; EXE intacto.
  Fixture privada: `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-author-template-941e605dfb06426d91429ec651ade32d`.
- Regressao portable aprovada; log `build/portable-0.3.1-template.log`, fixture
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-641a605ee47c4111bdcb729fe9f1a524`.

Nao e teste independente feito por terceiro humano nem UI real do mod novo;
template nao oferece eventos/persistencia por carreira/gameplay. P5 permanece
parcial, apesar de fluxo automatizado independente aprovado.

Revisao final `dist/portable-0.3.1-author-final`: relatorio individual e do conjunto
inclui dependencias/opcionais/conflitos/capacidades/permissoes; manifestos retêm
capacidades requeridas. Testes gerais agora 75, template 7, demais suites mantidas.
Projeto independente final em `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-author-template-1e9ede77253d47eaa9b070fdc24d2bc8`;
log `build/template-final.log`. Javadocs tambem sao validados no CI Java 17/21.

Pacote anterior da mesma entrega (`portable-0.3.1-template-reviewed`) instalado
somente na copia completa existente e JAR do novo mod copiado pelo instalador do
template; conjunto com **inspetor + mod de consulta** passou em --validate-only.
Nao inicializou a interface/mods nesse comando. Report do template usa textarea
com scroll/tamanho limitado para nao abrir dezenas de linhas fora da tela.
UI real do novo mod e usabilidade por autor terceiro ainda precisam de confirmacao.
Loader final 0.3.1 tambem instalado na copia; conjunto com os dois JARs validado
com relatorio de capacidades/dependencias. EXE/saves da origem e save da copia
novamente conferidos, sem mudancas pelos comandos de build/instalacao/validacao.
