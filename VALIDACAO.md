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

## P4 parcial — guard interno de lifecycle

Branch `feat/career-lifecycle-guard`, baseada no starter do PR #5 ainda aberto.
Nao modifica SDK publico/versao; somente sonda opt-in recebe estado interno.
Contrato/limites em `roadmap/CAREER-LIFECYCLE.md`.

Windows/Java 21: 75 gerais, 13 tracking, 11 dispatcher, **22 lifecycle**, 16 scope,
**15 bytecode** e 7 template aprovados; `test.ps1 -IncludeUi` aprovado.
`test-career-hooks.ps1`: **41 checks originais** aprovados na fixture privada
`%LOCALAPPDATA%\Temp\opencode\vbrasfoot-hook-io-daacb145eb21408b8cd653825b5a3c08`.
EXE/save de entrada da fixture e origem conferidos pelo runner, sem mudanca.
Stack traces esperados das falhas induzidas sao privados em runtime-test.log.

Guard acompanha decode/fallback/reload, primaria fechada vs falha, reset e setter
originais sem alterar schema. Testes sinteticos corroboram recusa de save atrasado,
concorrencia, overflow e independencia de callback falho/fila saturada. Report
testado com fechamento explicito, sem consultas Swing ou unload sintetizado.
Shutdown real/System.exit com este guard, UI completa de criar/trocar/descarregar
e novo mod na UI **ainda nao corroborados**; nenhum evento publico/P3 habilitado.

Pacote `dist/portable-0.3.1-lifecycle-guard` gerado e instalado somente na copia
completa existente; --probe-career-hooks --validate-only aprovado com os dois mods,
sem inicializar jogo/mods ou exercitar o guard. API publica permanece 0.3.1;
este incremento interno da sonda e identificado pela branch/commit, nao release.

## Confirmacao real do guard e SDK de observacoes 0.4.0

Usuario informou teste feito. Sessao privada conferida:
`session-44216057-f6b0-420a-8e8a-45f4decf9168` na copia completa.
career-lifecycle.properties: geracao 3, loads=1, saves=0, Flamengo, ACTIVE antes de
close/CLOSED depois, healthy=true, inFlight=0, staleCompletions=0/unboundSaves=0.
Dispatcher: accepted=delivered=1, dropped/failed/discarded=0, workerAlive=false.
Originais e save corrente da copia mantem hashes anteriores. Este teste nao e
criar/trocar/descarregar carreira nem save do novo guard.

Branch `feat/career-observations-sdk` empilhada sobre #6 (ainda aberto), SDK/loader
0.4.0, API major 1 preservada. Observacoes experimentais somente opt-in, assinantes
separados da fila diagnostica e ownership por mod. Sem caminhos/objetos de jogo na
API. Nenhum sidecar ou evento CareerUnloaded implementado por suposicao.

`test-career-hooks.ps1`: **44 checks originais** aprovados, agora com mod observador
real compilado apenas contra SDK, recebendo tres leituras (fallback uma vez) e duas
escritas primarias, sem evento de primaria falhada. Fixture privada:
`%LOCALAPPDATA%\Temp\opencode\vbrasfoot-hook-io-1c14ab0dd09c40608e165311346a9136`.
Origem/EXE/input intactos no runner; frame sintetico, nao UI/gameplay completos.
Nova suite publica cobre replay, ordem, stale save, erro/overflow, limites, workers
que ignoram interrupcao, ownership/init falhado/permissao e modo normal recusado.
Contrato/limites e exemplo em CAREER-OBSERVATIONS.md e examples/career-observer.

Revisao 0.4.0: **33 checks de observacoes**, demais suites mantidas; stderr da sessao
guiada do guard vazio. `test.ps1 -IncludeUi` e release com Javadocs aprovados.
Template independente 0.4.0 aprovado fora do checkout na fixture
`%LOCALAPPDATA%\Temp\opencode\vbrasfoot-author-template-d09fb84d0dd64ead8849795f1e5d5799`.
Pacote inclui guia/observador em sdk/examples, nao instalado por padrao em mods.

Pacote final `dist/portable-0.4.0-observations-reviewed`: regressao portable aprovada
em `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-fcc6dbbb854b4d51abac75fe205581fa`.
Na fixture independente do template, observador copiado explicitamente e conjunto
testado em processos novos: normal --validate-only recusou capacidade sem flag
(exit=1), probe --validate-only e safe --validate-only passaram (exit=0). Logs
privados `build/observations-{normal-refused,probe-validation,safe-validation}.log`.
Esses comandos nao inicializam jogo/mods. Loader 0.4.0 instalado somente na copia
completa, preservando mods existentes; observador nao instalado ali automaticamente.

### Confirmacao guiada de abertura/consulta com 0.4.0

Usuario confirmou abrir/consultar sem erro; nao testou outra carreira porque nao
possui segundo save e nao encontrou botao para troca na tela usada. Isso nao prova
ausencia de caminho de troca em todo o jogo nem fornece endpoint CareerUnloaded.
Sessao privada `session-87421f49-5020-4596-8d2b-b00aaf60ae89`: hooks opt-in, mods
consulta/inspetor ACTIVE, menu integrado, uma leitura completa de Flamengo na EDT,
stderr vazio na inspecao. Mod observador nao instalado nessa copia: nao chamar
esta confirmacao de teste real de callback do exemplo SDK 0.4.0.
Processo Java PID 2436 ainda observado ativo; sem termination/report final nessa
leitura. Nao afirmar fechamento, save, criacao ou troca. Hashes EXE/saves da origem
e save corrente da copia novamente identicos aos anteriores.

## P5 parcial — gerenciador e selecao proximo inicio 0.4.1

Branch `feat/mod-management-next-start`, baseada na main apos #5/#6/#7. SDK/loader
0.4.1, API major 1 sem novos metodos. Contrato em roadmap/MOD-MANAGEMENT.md.
Config versionada por nome exato de JAR, staging/force/ATOMIC_MOVE exigido; falha
de grafo/corrupcao/snapshot obsoleto nao altera selecao. Nenhum JAR/save e editado
ou removido. CLI usa runtime.lock, modo seguro/verificador ignoram config/mods.
Painel separa estado atual por **arquivo carregado** de selecao futura, inclusive
IDs duplicados em JAR desabilitado; leitura/escrita em SwingWorker, sem hot reload.

Windows/Oracle Java 21.0.12:

- **75 gerais + 35 selecao**, 13 tracking, 11 dispatcher, 22 lifecycle, 33
  observacoes, 16 scope, 15 bytecode e 7 template aprovados.
- `test.ps1 -IncludeUi` aprovado: painel real do loader na janela sintetica,
  acao integrada, tabela correta, abrir nao grava, cleanup sem janela vazada.
- `release.ps1 -Output dist/portable-0.4.1-management-reviewed`: pacote/Javadocs.
- `test-mod-management.ps1`: processos novos validam JAR invalido ativo recusado,
  disable/list/validate com JAR invalido desabilitado, enable invalido recusado
  preservando bytes da config, sessao ativa bloqueando CLI, config corrompida
  recusada e modo seguro aprovado. Nenhum jogo/mod inicializado; EXE/JAR intactos.
  Fixture privada `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-mod-management-b17b55e0690d4ed481bf53a0225fc3df`.
- Template independente aprovado com pacote final em fixture
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-author-template-d4319be4e9b44193a23ca665cd9fb87e`.
- Regressao portable aprovada; fixture
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-67d864e8ec7f463a85bf72450233916e`.
  Runner via powershell.exe exigiu GameDir explicito: default PSScriptRoot no
  parametro falhou nesse modo; log privado build/portable-management-reviewed-explicit.log.

Limites: testes nao substituem disable/reabrir/enable na UI real; historico de
falhas recentes/exportacao diagnostica ainda pendentes. Sem identidade P3,
persistencia por carreira, novos eventos de gameplay ou aceite integral de P5/P7.

## Confirmacao guiada 0.4.1 e historico de falhas 0.4.2

Usuario confirmou todo o roteiro de painel, desabilitar exemplo, reiniciar,
manter inspetor, reabilitar e reiniciar. Conferencia independente na copia:

- Sessao `session-fed53b9c-57a9-41b1-a341-1d03670e9c1b`: inspetor ACTIVE,
  sem registro ACTIVE do exemplo; sessoes `session-569f025d-ec96-4922-b192-323534b61adb`
  e `session-cbbea928-b379-4d32-a6c4-c5dbc8a1fd35`: ambos ACTIVE novamente.
- Stderr dessas sessoes vazio; config final format=1 sem entries disabled.
  Isso corrobora a confirmacao, nao cria evidencia de lifecycle/troca de carreira.
- #8 integrado na main como 036d4eb, CI main Java 17/21 aprovado antes de prosseguir.

Nova branch `feat/mod-failure-history`, loader/SDK 0.4.2, API major 1 sem novos
metodos publicos. Historico de ate 128 resumos da sessao, com evicoes contabilizadas;
sem reter Throwable/Class/classloader/objetos legados. Falhas de initialize/construtor,
acao UI, callback, overflow e shutdown atribuidas a arquivo/ID. Painel tem resumo
por JAR e aba global read-only; recarrega no Atualizar. Overflow registrado quando
status e observado, nao necessariamente no instante exato da perda. Nao e journal
duravel, nao captura toda thread arbitraria e nao altera estado/saves para diagnostico.

Executado Windows/Oracle Java 21.0.12:

- **23 testes novos**: limite/evicao, snapshots imutaveis, mensagens limitadas,
  getMessage falho, 400 registros concorrentes, ownership/etapas, construtor refletido,
  erro de acao/callback/overflow e shutdown, deduplicacao e sessao nova vazia.
- Demais suites: 75 gerais, 35 selecao, 13 tracking, 11 dispatcher, 22 lifecycle,
  33 observacoes, 16 scope, 15 bytecode e 7 template aprovados.
- `test.ps1 -IncludeUi`: resumo/aba de falhas com registros sinteticos, readonly,
  menu integrado/sem escrita ao abrir/cleanup aprovados. Nenhum erro provocado
  nos mods/saves do usuario, nem interface real do jogo nesta etapa.
- `release.ps1 -Output dist/portable-0.4.2-failure-history`: pacote/Javadocs gerados.
- `test-mod-management.ps1`: fixture privada
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-mod-management-64cda4798f8640208458c683cd544523`,
  CLI/lock/config/safe sem inicializar jogo/mods aprovado.
- `test-mod-template.ps1`: fixture privada
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-author-template-60826139a2064bd0ba89852c26f7fa9c`,
  build SDK-only fora do checkout/instalacao/validacao aprovados.
- Portable aprovado em execucao sequencial: fixture
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-ef48505225cd4defa477c248462d2b30`,
  log build/portable-0.4.2-failure-history-sequential.log. Primeira tentativa paralela
  com testes CLI foi recusada pela deteccao global de sessao: runners de instalacao
  nao sao independentes e devem ser executados sequencialmente.

EXE/saves da origem e save corrente da copia novamente hashados, intactos.
UI real 0.4.2, exportacao diagnostica revisavel e demais gates permanecem pendentes.

## P5 — diagnostico local revisavel 0.4.3

#9 integrado na main como c9563e1 antes da branch `feat/reviewable-diagnostic-export`;
CI main Java 17/21 aprovado. Nao ha confirmacao humana nova de UI 0.4.2 inferida
do merge ou de testes automatizados.

Exportacao TXT por allowlist, previa editavel e confirmacao explicita. Padrao
omite nomes/IDs/versoes/permissoes e mensagens; opt-ins independentes podem incluir
dados privados. Sem coleta de logs brutos, dados de carreira, arquivos de sessao,
argumentos, env, saves/backups/binarios/dados de mods. Nenhum envio automatico.
Salva exatamente revisao em diagnostics/diagnostic-UUID.txt com CREATE_NEW, limite
256 KiB e paths redirecionados recusados. Nao e escrita transacional/duravel em
queda de energia, relatorio pode ficar parcial se processo for encerrado a forca.
Contrato/privacidade em roadmap/DIAGNOSTIC-EXPORT.md.

Windows/Oracle Java 21.0.12:

- **41 checks novos**: default sem sentinelas privadas, opt-ins independentes,
  snapshots imutaveis, enums/numeros em vez de propriedades brutas, controles/bidi
  escapados, bytes UTF-8 revisados exatos, CREATE_NEW sem overwrite, limites de
  chars/bytes/Unicode, limites de inventario/falhas e junction NTFS recusada sem
  tocar destino protegido sintetico. Preparar/cancelar nao cria pasta ou arquivo.
- Demais suites: 75 gerais, 35 selecao, 23 falhas, 13 tracking, 11 dispatcher,
  22 lifecycle, 33 observacoes, 16 scope, 15 bytecode e 7 template aprovados.
- `test.ps1 -IncludeUi` aprovado: prepara/cancela previa sem escrita, defaults
  privados, opt-in de mensagens, edicao, confirmacao local e salvamento exato em
  fixture propria, cleanup de janelas. Nenhum save ou erro em mod do usuario.
  Fixture Swing `build/manager-ui-2906028408055779450`; fixture de exportacao pura
  `build/diagnostic-export-12028262039393776455` (privadas/ignoradas pelo Git).
- Teste inicial colocou junction em build e foi corretamente recusado no scan
  recursivo do installRoot. Somente a junction criada por esse teste foi removida,
  sem /S e sem apagar o destino/sentinela; fixture de junction agora fica no Temp
  privado opencode, fora da instalacao de desenvolvimento. Rerun completo aprovado.
- `release.ps1 -Output dist/portable-0.4.3-diagnostic-export`: pacote e Javadocs.
- CLI/lock/config/safe aprovado na fixture privada
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-mod-management-8be0ce51fa134f6ba2aa1e7630f4f1a7`.
- Template SDK-only fora do checkout/instalacao/validacao aprovado na fixture
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-author-template-ca3f389814114c689b05a942abb0fea5`.
- Portable sequencial aprovado com GameDir explicito; fixture
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-1523ae27835845d8a43917a81307c649`,
  log build/portable-0.4.3-diagnostic-export.log.

EXE/saves da origem e save corrente da copia novamente hashados, intactos.
UI humana/exportacao na copia real ainda pendentes; nao declara MVP completo,
anonimato garantido, captura exaustiva ou progresso em identidade P3/gameplay P6.
Revisao final `dist/portable-0.4.3-export-reviewed` gerada apos novo smoke Swing e
todas as suites aprovadas; previa com area de status legivel/monoespacada e sem
reescrita implicita do texto revisado.

## Confirmacao guiada 0.4.3 e SDK-only 0.4.4

Usuario informou teste de exportacao feito e autorizou integrar #10. PR integrada
na main como 0ac7fca; CI main Java 17/21 aprovado antes da nova branch
`feat/standalone-sdk-package`. Na copia foi observado somente por nome/tamanho/data
o arquivo local `diagnostic-bc2b05d4-a5f5-429a-93c6-48b123c12be4.txt` (986 bytes,
17/10/2026 10:27:26). **Conteudo privado nao foi lido/publicado**; metadados e
confirmacao nao provam individualmente todos os opt-ins/redacoes/anonimato.

SDK/loader 0.4.4, API major 1. `release-sdk.ps1` compila API e tres classes do
validador target-free em pastas novas, fonte explicita/sourcepath vazio; nao inclui
Main/LegacyRuntime/Profile/ASM. Validador individual compartilhado com launcher,
sem necessidade de EXE/loader/home para autores. Perfil publico e loader conferidos
no teste; alteracao nao migra saves ou permissões.

Executado Windows/Oracle Java 21.0.12:

- **78 gerais** aprovados, incluindo JVM filha com validador SDK e classe cujo
  initializer/construtor/callback falharia se executado; nao criou home/cache/logs.
  Demais suites: 35 selecao, 23 falhas, 41 exportacao, 13 tracking, 11 dispatcher,
  22 lifecycle, 33 observacoes, 16 scope, 15 bytecode, 7 template e teste do observador
  SDK-only aprovados. `test.ps1 -IncludeUi` aprovado, sem novo gameplay.
- `release-sdk.ps1 -Output dist/sdk-0.4.4-final`: API/validador/Javadocs/guias,
  template/observador/fontes/testes/MIT, ZIP canonico e checksums. ZIP **260.568 bytes**,
  SHA-256 `356754ff9693b4dc0cafc0712f19296a6fa134597a4b2309524a41cdeb979d19`.
  Nao criou tag/release, upload, dependencia baixada, jogo ou runtime redistribuido.
- `test-sdk-package.ps1`: ZIP extraido fora do checkout, projetos novos com ID
  personalizado no template, build/test de ambos somente contra API e validador
  target-free. API/testes nao embutidos no JAR dos mods. Fixture de nao inicializacao
  passou, invalido/argumentos desconhecidos recusados. Destino existente, hash
  adulterado, arquivo extra sintetico, traversal no indice e namespace proibido com
  checksum consistente recusados. Fixture privada
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-sdk-author-c3d881d7945a43c086b55b0704b72fc8`.
- Primeiro prototipo ZIP gerado pelo .NET Framework tinha entradas com backslash
  e foi corretamente recusado; writer corrigido para nomes `/`. Prototipo privado
  `dist/sdk-0.4.4-author.zip` nao e artefato aprovado; sem delete/overwrite automatico.
- `release.ps1 -Output dist/portable-0.4.4-sdk-reviewed`: pacote/Javadocs/validador e
  novos guias; links relativos entre guia de observacoes e exemplo corrigidos no portable.
- CLI/lock/config/safe aprovado em fixture privada
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-mod-management-cd7f2f3104044f6ea30098878bb6fca0`.
- Template fora do checkout/instalacao/validate-only aprovado em
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-author-template-0f5392e34e224ad2a1aef5ac8f6292f9`.
- Portable sequencial aprovado com GameDir explicito; fixture
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-7ee3a8d9966c41cbad25ee181c4b4abf`,
  log build/portable-0.4.4-sdk-reviewed.log.

CI ampliado para gerar/extrair/verificar SDK-only em Java 17/21, contents:read e sem
upload/publicacao. Checksums nao autenticam origem; scripts/ferramentas do pacote
so devem ser executados quando confiaveis. Build reproduzivel como procedimento,
nao promessa de ZIP identico entre horarios/JDKs. Javadocs preservam avisos legais.

EXE/saves da origem e save corrente da copia hashados e intactos. Publicacao revisada
do SDK e usabilidade por autor terceiro humano ainda pendentes; API experimental
nao e aceite integral P5/P7 nem avanco de identidade P3/lifecycle completo/P6.

## P2/P7 parcial — recursos e validade do contexto do mod

Incremento em `feat/mod-context-lifecycle`, baseado em main da83cbd (#12 integrado).
Sem nova release/tag, sem atualizar a instalacao isolada; API major 1 e assinaturas
publicas preservadas. Comportamento de encerramento documentado no SDK-CONTRACT.md.

- Acoes legacy/scoped tem proprietario e sao revogadas na falha parcial de initialize
  ou inicio de shutdown, sem esperar pela EDT; limpeza visual permanece agendada.
- Handle encerrado nao remove registro novo com mesmo ID, nem quando label e
  callback sao identicos (comparacao por identidade, nao igualdade do record).
- Contexto e facades obtidas antes do encerramento recusam servicos apos falha de
  initialize ou retorno/falha de shutdown. Dados/consultas globais ficam disponiveis
  durante shutdown normal; registros novos nao. Logger continua utilizavel.
- FAILED de initialize preservado ao terminar cleanup; close idempotente e loader
  fechado recusa load. Dois mods com mesmo ID local de acao nao se confundem;
  shutdown reverso continua apos falha do segundo.

`test.ps1 -IncludeUi` aprovado: **53 checks** novos de lifecycle nas fronteiras
reais do loader com mods sinteticos, incluindo EDT estacionada para comprovar que
revogacao nao espera pelo event dispatch. Suites existentes (78 gerais, 35 selecao,
23 falhas, 41 exportacao, estatisticas/cache, tracking/dispatcher/lifecycle,
observacoes/scopes/bytecode/template/observador) e UI sintetica aprovadas.
Log local ignorado `build/context-lifecycle-validation.log`.

Nao houve execucao de jogo, leitura/gravacao de save, instalacao ou publicacao.
Callbacks/operacoes ja iniciados podem terminar; cleanup nao e cancelamento de
thread, sandbox, prova de ausencia de leak ou garantia de shutdown via System.exit.
Nenhum gate integral P2/P7 ou lifecycle de carreira/P3/P6 fechado por estes testes.

## P2/P7 parcial — benchmark read-only e cache de getters 0.4.5

#11 integrado na main como c2b524c antes da nova branch `feat/scoped-query-performance`;
CI main Java 17/21 aprovado. SDK/loader 0.4.5, API major 1/formatos de save inalterados.

Runner `benchmark-queries.ps1` usa EXE/save conhecidos em copia privada nova, sem
hooks/simulacao/gravar carreira. Decode original usa frame sintetico, nao UI completa.

Fixture de baseline 0.4.4:
`%LOCALAPPDATA%\Temp\opencode\vbrasfoot-query-benchmark-b96ef05ec6744b498b3abb367c975987`.
Runs cache A/B 0.4.5:
`vbrasfoot-query-benchmark-4f35a5ba80634b9e8baeafa405b34bd0` e
`vbrasfoot-query-benchmark-2b3f215156c44ebd8b2ba084b9156d12` no mesmo Temp/opencode.
Cada cenario tem 20 aquecimentos e 100 amostras; Java21.0.12, Xmx1536m, 12 CPUs
logicas, **8.395 clubes / 193.831 jogadores**. Input/exe da fixture e origem intactos.
Reports privados query-benchmark.properties/log nao redistribuidos; nomes de clube,
jogador e filtro nao serializados no report.

- Pagina100 clubes p95 EDT baseline **5,938 ms**, A **2,366**, B **3,089**;
  alocacao EDT p95 **1.918.688 -> 546.048 bytes** (~72% menor na cobertura local).
- Filtrada com 1 resultado: p95 **4,506 -> 1,669/2,024 ms**, alocacao
  **1.816.000 -> 471.872 bytes**. Pagina500 com 500 resultados: p95
  **5,048 -> 2,008/2,402 ms**, alocacao **2.333.632 -> 829.696 bytes**.
- Outlier pagina100 baseline 9,912 ms vs A12,323/B4,023; nao concluir maximo garantido.
- Baseline jogadores retornava25, runs novos escolhem maior elenco observado52;
  nao comparar percentual entre esses grupos nem tratar limit500 como500 retornos.
- Tempos de preparo/decode nao sao startup completo, heap amostrado nao e RSS ou
  memoria retida; contador de alocacao e so EDT. Nao houve forced GC.
- Orcamentos locais propostos (nao enforce/CI universal): p95 EDT <=8ms clubes,
  <=1ms para elenco52 observado e alocacao EDT <=1MiB clubes. Metodo/limites e
  tabela completa em QUERY-PERFORMANCE.md; gate P7 global continua aberto.

Cache por ClassValue e mapa concorrente de getters publicos evita getMethod a cada
campo, sem cache de objetos da carreira/mapa global forte de classloaders. Teste
cobre classes distintas, mesmo nome em dois loaders, falha/missing e concorrencia;
nao e prova exaustiva de ausencia de leak. Cinco checks de percentis/estatisticas
sem mutar amostras aprovados. Demais suites 78 gerais, 35 selecao, 23 falhas, 41
exportacao, 13 tracking, 11 dispatcher, 22 lifecycle, 33 observacoes, 16 scope,
15 bytecode, 7 template e fake SDK-only do observador aprovados.

- `test.ps1 -IncludeUi` aprovado (UI sintetica, sem nova confirmacao humana).
- `test-career-hooks.ps1`: **44 checks originais** com cache/schema/ref stale/I/O
  e observador SDK-only aprovados na fixture privada
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-hook-io-099c0a51e93a412a8390dafdfbe20dc4`.
- SDK-only 0.4.5 gerado/testado fora do checkout; fixture
  `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-sdk-author-026f681ba34a413ab365637b817cbe4b`.
  Pacote dist/sdk-0.4.5-query-reviewed.zip; sem tag/release/upload.
- Portable dist/portable-0.4.5-query-reviewed gerado; template e CLI instalados em
  fixtures privadas `vbrasfoot-author-template-c724490371f9494c83355cd721c20df8` e
  `vbrasfoot-mod-management-9c6afadbdab44441b3830f43fb2c06ea` (Temp/opencode).
- Regressao portable sequencial aprovada em `vbrasfoot-install-f4c6878670e4492cb33b57b23cfc791b`;
  log build/portable-0.4.5-query-reviewed.log, GameDir explicito.

Hashes EXE/saves da origem e save corrente da copia novamente intactos. Leitura
scoped otimizada nao fecha identidade P3, unload/eventos completos, gameplay ou
memoria/latencia universal. Matriz Java17/UI real/multimods/mais carreiras segue pendente.

## P4 parcial — rotas de criacao/navegacao apos #13

#13 integrado ad9f6a8; reconciliacao de gates #14 integrada e87fad6 antes do novo
incremento. REA abriu EXE PE/x86 com SHA esperado; inventario provider-neutral
somente raiz, sem expandir ZIP JVM. Evidence/view IDs e limitacoes registrados
em LIFECYCLE-PATHS.md. Sessao fechada; nenhuma configuracao/dependencia instalada.

Scanner/javap local estendido com seletores exatos owner/member/descriptor:
1.038 classes, 139 referencias anteriores e 166 no recorte ampliado. Dez sites
diretos de best.f.V, incluindo best.f.aa; nao exclusivo de concluir criacao.
Ligadas rotas forms.q/forms.s, a.hU.oq, a.bV.pl/AB e best.S.aY. Selecao de painel,
liga ou tecnico nao prova carreira pronta/unload. UI de registro nao contornada.

`inspect-career-hooks.ps1` aprovado; sete checks sinteticos de selecao/overload,
fingerprints, classfile invalido e ausencia de inicializacao. Perfil desconhecido
recusado; EXE original hashado antes/depois. Relatorios privados ignorados em
`build/hook-analysis-67b80a6d1aee4791b0802955c1363c4a`, log local
`build/lifecycle-paths-review-final.log`. Bytecode/classes nao redistribuidos.

Nao houve execucao de criacao/gameplay nem novo hook. Roteiro com duas carreiras
reais e variantes de amistoso em LIFECYCLE-PATHS.md permanece pendente; unload,
save duravel, identidade/sidecars P3 e gameplay P6 continuam bloqueados.

## P7 parcial — isolamento com dois JARs reais

Base main 60c8b0d apos #14/#15. Suite ModJarIsolationSelfTest compila duas fontes
proprias contra dist/vbrasfoot-api.jar, com main/helper de mesmo nome binario e
comportamento diferente, empacota JARs/manifestos reais e usa discover/validator/load.
**31 checks** aprovados no Windows/Java21: no-init em validacao, dependencias por ID
ordenando filenames inversos, classes/instancias/counters separados, helper exclusivo
do provider inacessivel ao dependente, duas acoes ativas, shutdown reverso/cleanup.
Fixture build/jar-isolation-UUID preservada, propriedades process-local removidas
no finally. Build completo aprovado; sem jogo, rede ou saves.
Nao e sandbox, isolamento contra codigo hostil/internals, prova de GC/leak ou UI
real. P7 global e P4/P3/P6 permanecem abertos.

## P1/P7 parcial — inventario somente leitura do home

Base main ee2bcd5 apos #16 integrado. --storage-status retorna antes de perfil,
runtime, mods, diagnostico e lock, sem criar arquivo. Sete categorias fixas,
metadados/bytes logicos agregados sem conteudo/nomes privados; desconhecidos/sav
original nao varridos. Parcial/recusa retorna erro; limites cooperativos 100mil
entradas/10s, overflow protegido, no-follow/revalidacao de caminhos.

`test.ps1 -IncludeUi` aprovado Windows/Java21: **34 checks** novos com junction
Windows, CLI sem escrita inclusive erro, budget/overflow/tipos/home/ausencias;
symlinks indisponiveis explicitamente SKIP, nao testados neste ambiente. Demais
suites incluindo 31 JARs/53 context lifecycle e UI sintetica aprovadas.
Fixture privada `%LOCALAPPDATA%\Temp\opencode\storage-inventory-12736365844648426654`.
Log local build/storage-inventory-validation.log. Primeira fixture sob build foi
recusada pela validacao normal devido a sua junction; link criado pelo proprio
teste removido sem recursao/destino intacto e fixture movida para Temp privado.

Nao e total do home/disco, snapshot atomico, quota/blocos alocados ou garantia
contra TOCTOU hostil/todos reparse tags. Tempo nao interrompe I/O bloqueado.
Nao implementa limpeza/retencao completa, nem autoriza apagar dados/backups/saves.
Sem jogo, save, instalacao/tag/release/upload; matriz CI 17/21 conferida no PR.

## Criacao humana de duas carreiras e investigacao de corte/header

Usuario confirmou criacao/consulta/salvar/reabrir no EXE original em copia nova
Documents/Brasfoot22-23, sem partidas, escala100%. Variante amistoso nao confirmada;
troca dentro da sessao nao encontrada. Sessoes/observacoes/guard e limites em
roadmap/CREATION-VALIDATION.md: UNKNOWN/unboundSaves na criacao impedem P3 apesar
de escrita primaria completa; reabertura A no loader tem load completo/ACTIVE.

Erro E8CC1702 localizado por leitura de quatro bytes de arquivos .ban (8.399),
um header divergente, hash igual nas tres copias, sem deserializar/modificar.
Javap original liga teams/.ban/ObjectInputStream ao stacktrace. Nao e .s22 nem
incompatibilidade introduzida pelo loader; importacao desse arquivo e recusada
pelo legado. Nao apagar/converter recursos ou alegar erro benigno/equipe importada.

Corte de janela repetido ao concluir criacao, antes de abrir mod; Alt+Espaco
recuperou. Codigo original alterna estado e pack da janela sem moldura; hipotese,
nao causa comprovada. Incremento recovery explicito no menu usa EDT/area util do
monitor e diagnostica bounds/estado; sem ajustes automaticos/hook/save/preferences.
Seis checks sinteticos de geometria/thread e smoke Swing exercitam acao; UI real
nova e reproducao no original continuam pendentes. Bytecode privado no Temp e
log local build/creation-window-validation.log, nenhum artefato privado publicado.

## Instalacao da mitigacao e seletores de escrita de campo

Pacote privado portable-0.4.5-window-8a16775 gerado, instalado na copia Documents
com jogo fechado; JAR e hash/commit registrados em CREATION-VALIDATION.md. Todos
arquivos sav, EXE, mods e selecao hashados antes/depois sem mudanca. Validacao
com sonda sem iniciar jogo aprovada. Instalacao nao e confirmacao UI da nova acao.

Scanner ampliado para seletores PUTFIELD/PUTSTATIC exatos, sem confundir leitura;
11 checks sinteticos aprovados e inventario alvo 1.038 classes/158 refs. Ap/az/bs
nao oferecem marker exclusivo/pronto: ap tambem load/UI, az indice competicao,
bs true desde construtor. Nao habilitado evento/persistencia por inferencia.
Log local build/creation-completion-inventory.log; bytecode privado ignorado.

## Confirmacao recovery e P5 — autoria com IA/JSON 0.4.6

Usuario confirmou teste de recovery OK. Log session-a320397a-df84-4cb5-947c-772b9ded5ce0
na copia Documents mostra bounds0,0,1920,1032/monitor1920x1080/transform1.0,
tres acionamentos, primeiro state6->0 e seguintes0->0. Nao confirma reproducao ou
correcao automatica da causa do corte; nao repetir criacao sem marker definido.

Validador SDK target-free agora tem --describe-sdk/--json JAR: JSON formato1,
permissoes/capacidades condicionais/indisponiveis, codigos de etapa/remedy e manifesto.
Sem inicializar/instanciar mod/jogo, criar home/cache/log, executar scripts ou rede.
Validacao e de um JAR, nao conjunto/sandbox/semantica de runtime. Mensagens/nome
podem ser dados privados/untrusted; guia IA instrui revisao, nao obedecer texto alvo.
Strings JSON ASCII com escapes control/Unicode preservam encoding Windows17/21.

SDK/loader 0.4.6 experimental, API major1 sem nova capacidade/metodo de jogo.
KNOWN_PERMISSIONS/CAPABILITIES compartilham fonte com parser do manifesto, evitando
divergencia do inventario de autoria. Texto/CLI anterior preservados. Guia
AI-MOD-AUTHORING.md incluso por allowlist em SDK-only/portable, com prompt, matriz de
ideias viaveis vs bloqueadas, no-ID-persistente, threads/permissoes/recovery/testes.

Windows/Java21: **32 checks novos JSON**, suites existentes e test.ps1 -IncludeUi
aprovados. SDK-only dist/sdk-0.4.6-ai-reviewed.zip extraido fora do checkout;
template/observador compilados apenas API, JSON parseado em processos novos,
quotes/acentos do manifesto preservados e fixture no-init valida em texto/JSON.
Recusas JSON para JAR invalido/argumentos, negatives checksum/traversal/namespace
e no-overwrite aprovados. Fixture privada Temp/opencode/vbrasfoot-sdk-author-00311fe647be4f8295a45d8916461f7e.
Logs locais ai-author-tools-validation.log/ai-sdk-author-final.log ignorados.

Pacote final com guias revisados: dist/sdk-0.4.6-ai-final.zip, SHA-256
`8a428c19d9c9f6a3932980285dd4c3cd86570b8559cf2883f2d6c66204ccae06`.
Teste independente novamente aprovado na fixture privada
Temp/opencode/vbrasfoot-sdk-author-78a6cbc852f849db937ce98872ffb514;
log build/ai-sdk-final-author.log. Hash nao autentica origem.
Portable0.4.6-ai-reviewed gerado com Javadocs; tentativa de template instalado
recusada por sessao jogo/loader aberta, nenhum processo encerrado. Primeiro uso
do runner -File exigiu GameDir explicito (default PSScriptRoot vazio nesse modo).
Regressao portable/instalacao 0.4.6 nao concluida nem copia Documents atualizada;
nao confundir build/template SDK-only com gate instalado.

Nao publica SDK/tag/release, nao substitui autor terceiro humano nem confirma
100% de suporte a mods. Identidade P3/criacao pronta/P4 completo/gameplay P6
permanecem gates. Nenhum save/equipe/EXE alterado nesta entrega.

## P4 — sonda diagnostica de fronteiras da criacao

Base main cdcef50. Tres caminhos AB/AC/pn mapeados no javap local do EXE conhecido
da copia Documents, sem executar as rotinas; bytecode privado Temp/opencode/
vbrasfoot-creation-path-989037c5a98b4aad99a994faa9e126fd. Full-ClassSHA e access/
descriptor/um RETURN validados fail-closed. Metodo original/schemas preservados.
Sonda emit creation-candidate-* em fila diagnostica existente sem publicar eventos
de mods, marcar guard ACTIVE ou completed=true. UNKNOWN permanece UNKNOWN; retorno
de AC antes do torneio nao vira carreira pronta. Excecao original repropagada,
falha sink isolada. Normal/seguro nao aplicam novos hooks; validate probe liga
classes sem instanciar/inicializar esses paineis. Sem bypass registro/licenca.

Build aprovado: 11 novos checks bytecode e 16 tracking (13+3 conservacao guard),
demais suites existentes. Primeira compilacao do teste importava ASM interno com
--release17; substituido por constantes reflection Modifier no teste, sem ampliar
exports fora da sonda. 44 checks de I/O original aprovados na fixture privada
Temp/opencode/vbrasfoot-hook-io-e09f1ccf1a434723b0455f406e990714; regressao final
com dois checks shape a.bV/a.cR passou **46 checks** em
Temp/opencode/vbrasfoot-hook-io-b1a7f77677ce4a2d95d91a7e8306ba21. Logs locais
creation-probe-build.log/creation-probe-original-io.log ignorados.
Nenhum save de usuario/EXE alterado. UI original dos candidatos ainda pendente;
nao definir CareerLoaded/identidade/sidecars P3/gameplay P6 por estes resultados.

## P5 — comparador SDK-only e preparacao do teste real

#21 integrado4fe1050/CI17+21 antes desta branch. Mod original MIT em
examples/player-comparator, main/model/testes/build independentes, apenas API jar.
Permissoes game.read/ui.register, query.scoped, minLoader0.4.6; consultas100, dois
DTOs escolhidos em memoria, stale limpa ambos os lados. Sem snapshot global,
lookup persistente por ID, filesystem/network ou gameplay. Value/codigo posicao/
forca crus, sem unidades/ranking oficial inventados.

94 checks headless modelo/lifecycle aprovados. Review alterou disposal para
invokeLater (nao espera EDT) e incluiu LICENSE no JAR; callback tardio nao reabre
janela. Smoke Swing carrega o JAR real via ModLoader contra GameAccess fake:
janela/selecoes/tabelas readonly, comparacao6 campos, STALE_SCOPE limpa selecoes,
refresh e shutdown descarta janela. UI sintetica, nao carreira real/latencia.

SDK-only dist/sdk-0.4.6-comparator-reviewed.zip gerado/extrado fora do checkout:
template/observador/comparador compilados/testados apenas API e JSON validados;
allowlist/namespace/hash/negatives aprovados. Fixture privada
Temp/opencode/vbrasfoot-sdk-author-0c0f3a4c07a64d84b090a664e40e3fef.
Logs build/comparator-validation.log/comparator-sdk-author.log ignorados.
Numeracao0.4.6/API1 permanece para incremento interno identificado por commit,
sem release/tag/publicacao. Novo mod nao e instalado por padrao no pacote; a copia
de teste do usuario pode receber explicitamente o comparador nesta tarefa.

Portable final dist/portable-0.4.6-dual-final gerado e regressao aprovada em fixture
Temp/opencode/vbrasfoot-install-f8a6e20012ad4f79a68077c3fa1d161d, sem gameplay/save.
SDK-only final dist/sdk-0.4.6-dual-final.zip com installer do comparador por allowlist,
teste fora do checkout novamente aprovado em
Temp/opencode/vbrasfoot-sdk-author-b589ede13b1c46fa8b915dd434edf179.
Novo installer exige loader existente, recusa links/JAR existente/sessao aberta,
valida mod sem init, usa runtime.lock/staging e nao escreve configuracao/saves.
Sem instalar por padrao nos mods do pacote e sem release/tag/upload.

## P4 — confirmacao humana e conteudo do candidato

Usuario confirmou comparador e criacao. Sessoes privadas07f45482 e3199941c na copia
Documents corroboram comparador ACTIVE/load teste02 e AB start/return na EDT +
save teste04, respectivamente. Guard UNKNOWN/unboundSaves1 permaneceu na criacao;
nenhuma prova de cancelamento/amistoso/troca ou reabertura original de teste04.

Novo CreationContentTracker: labels3/stack8, weak refs, contagens antes/depois,
geracao/identidade raiz/lista e outcome da ultima tentativa pareada. Nao promove
guard/eventos, nao registra nomes/dados de jogadores; getters somente na EDT e
metadados copiados no shutdown sem entrar na EDT. Sem grafos/Throwable/classes
retidos no Report. Overflow abandona pareamento e sessoes antigas nao alimentam
nova sessao. 27 checks sinteticos aprovados; regressao46 I/O original aprovada
em Temp/opencode/vbrasfoot-hook-io-261f504c1bce47c6a5dc6a9e564f260b.
Logs build/creation-content-validation.log/creation-content-original-io.log privados.
Conteudo do AB original ainda nao observado nesta versao; nao inferir retroativo.

## SDK0.5.0 — calendario atual e saldo scoped

Base apos #23 integrado2cfae93. APImajor1, defaults novos recusam implementacoes
antigas; scopedCalendar/scopedClubBalance exigem game.read e capacidades/minimo
documentados em CALENDAR-BALANCE.md. Calendar clone antes de get/getTime evita
normalizacao do objeto vivo; GregorianAD/era valida, month1..12, origem/exibicao
timezones explicitados. Saldo long inteiro inclusive negativos, unidades nao ISO.
Nenhuma escrita/agenda/evento/identidade/selecionar humano primeiro silencioso.

Mapeamento static liga bb/J/T ao JLabel da data e kb ao saldo/custo/debito/credito;
relatorio/bytecode privado Temp/opencode/sdk-read-static-20261017, EXE conhecido.
15 checks snapshots puros +61 context lifecycle (novos metodos revogados),
demais suites aprovadas. **52 checks I/O original** aprovados na fixture privada
Temp/opencode/vbrasfoot-hook-io-6f2998e1de634f84b8f79f670c8e0538: scope/data/indice
e clone/saldo iguais ao original, stale balance apos reload, noCalendar apos reset.
Log build/calendar-balance-original-io.log. Frame sintetico, nao confirmacao UI.
Campos/in-place/multithread/locale/timezones e humanos selecionados amplos pendentes.
Novas leituras nao desbloqueiam evento de dia nem P3/P4completo/P6. Nenhum save
de usuario/EXE/arquivo de equipe alterado, sem publicacao/tag/release.

## P5 — painel e kit para autor terceiro, sem aceite artificial

#24 integrado7df12fb/CI17+21 antes desta branch. examples/career-dashboard e fonte
original MIT/API-only, modelo/fakes/build/installer independente, minLoader0.5.0,
permissoesgame.read/ui.register/capacidades scoped/calendar/balance. Leitura manual,
saldoLong bruto/fusos, escopos e ClubRef conferidos, stale limpa handles; shutdown
agendado EDT sem esperar. Sem jogo/rede/save/reflexao/polling/persistencia.

43 testes headless iniciais aprovados (novos motivos futuros podem ampliar numero);
test.ps1 -IncludeUi com JAR real e GameAccessfake confirma data29/02/2024/saldo
negativo, stale limpa output e shutdown fecha janela. Nao conferencia UI real.
SDK0.5.0-mvp-preparation extraido fora do checkout compila/testa/valida quatro
projetos API-only, JSON + negatives/allowlist/hashes/no-init aprovados em fixture
Temp/opencode/vbrasfoot-sdk-author-027367932d7f422db070516321691cfb.
Logs locais dashboard-validation.log/mvp-sdk-test.log ignorados.

THIRD-PARTY-ACCEPTANCE prepara kit/missao/relatorio para humano independente, nao
executa esse gate nem publica release. O MVP P0-P5+P7 depende de identidade/sidecars/
eventos reais nao entregues por esta documentacao. Sem afirmar100% por builds.

## Confirmacao real data/saldo e etapas internas da criacao

Usuario confirmou data/saldo coincidentes e nova criacao OK. Sessao c163429c
reporta4153clubes/1humano antes/depois, AB1start/1return/0fail/rejected e roots/
lists/generation estaveis; guardUNKNOWN, healthy, inFlight0/stale0/unbound0 ao
fechar. Nenhuma prova AC/pn/amistoso/cancelamento ou ready commit por contagens.

Sonda agora marca callsites az/V antes/depois dentro de candidatos apenas;
ClassSHA/methodaccess/descriptor/sites exatos validados antes define. Preserva
receiver/returnint/Throwable, nao transforma best.f extra, no polling/flag/guard
ou evento publico. Cada ultima tentativa tem max8etapas/3labels; scalars copied
calendar e indice/contagens capturados somenteEDT, indisponibilidade declarada.
33 testes content,12 bytecode candidate e52 I/O original aprovados (fixtureframe
sintetico, nao execucao AB/AC/pn original). Log local creation-steps-original.log.
Confirmacao das etapas originais e caminhos alternativos pendente; P3 segue gate.

## Etapas reais e prototipo UI integrado0.6.0

Report privado session-7398aa1c-1ff1-4144-95c0-887fbb74b07f mostra AB etapas4:
beforeaz indice1/data02jan2022, afteraz4/05jan, beforeV4/05jan, afterV22/23jan,
geracao4 e4153clubes/1humano estaveis. Calendario disponivel; AC/pn nao observados.
Usuario confirma tudo bateu. Nao chamar esses avancos internos DayAdvanced/ready
commit; cancels/variantes ainda nao corroborados e guard conservador.

SDK0.6.0/APImajor1 introduz UI own ModPanel/UiTheme/registerPanel com capacidade
ui.panel.embedded+ui.register, factory/component/closeEDT. Overlay dentro da
careerWindow existente, nao substitui/reparenta contentPane/bounds/glass original;
Voltar/Escape restaura camada, um owner ativo, registros ownerUUID separados,
screen change fecha, glass visivel recusa. Nao pausa simulacao/atalhos globais,
genericinjection/codigo hostil ou todos tipos de renderer/escala.

Comparador refatorado em JPanel/model readonly reutilizado por janela anterior e
overlay (integrado nao cria JDialog). Tema apenas Font/Color local copiados,
sem recursos proprietarios compartilhados/distribuidos. Tres checks palette/EDT
e smoke Swing com JAR real testam comparacao6campos/Voltar/identidade content/
bounds/glass, substituicao entre owners/close idempotente/camada existente e
screen recreation. Log local embedded-ui-validation.log, UI original pendente.
Saves/EXE/equipe nao alterados; nenhum lifecycle/identidade ou evento promovido.

SDK-only sdk-0.6.0-embedded-reviewed gerado/extraido/testado fora do checkout,
quatro projetos compilados API-only e validados, no-init/JSON/namespace/allowlist/
hash/negatives aprovados em fixture privada
Temp/opencode/vbrasfoot-sdk-author-d4b3d3e0e2b24230b67c0cf1375d79f3.
Logs embedded-sdk-test.log/embedded-ui-validation.log ignorados. Sem publicacao.

## UI0.7.0 — revisao visual, nao so paleta global

Usuario confirmou UI0.6 integrada/voltar funcionando e pediu visual mais proximo.
Consulta read-only da referencia realbrasfoot-mods revelou composicao de header/
body/secoes/footer; nenhuma licenca explicita localizada. Nao copiar codigo,
assets ou logica de transferencia; nova implementacao original com Swing padrao.

UiTheme.Role/style distingue9papeis. apply preserva cores/fontes nao-UIResource
explicitas, nao apaga roles e configura JTable header/rowHeight/selection, sem
alterar UIManager/global/gamecomponents. Comparador1.1.0/loader0.7.0 reorganiza
dois lados/busca/clube/navegacao, resultado e rodape. Modelo/consultas inalterados.

Cinco checks puros roles/thread/style preservation; smoke integrado compara,
preserva content/bounds/glass/voltar e verifica controles principais dentro da
area1280x720. Capturas PNG originais fake-only em build/embedded-style-preview-UUID
e embedded-style-small-UUID, nao screenshots do jogo/save. A causa estetica e
avaliacao humana, nao prometer pixel-perfect ou matriz completa a partir do smoke.
Primeira compilacao detectou chave final extra, corrigida antes dos testes.
Logs locais style-roles-validation.log ignorados; jogo do usuario observado aberto,
nenhum processo encerrado e instalacao adiada enquanto houver sessao ativa.

Regressao final: test.ps1 -IncludeUi aprovado, previews sinteticos gerados e
inspecionados, controles de voltar/atualizar/escolher dentro da area1280x720.
Tabela resultado mantem espaco minimo e navegacao/rotulos compactados para essa
altura. Source/consulta nao mudou para atingir visual.
Portable dist/portable-0.7.0-style-final gerado/regressao aprovada em fixture
Temp/opencode/vbrasfoot-install-87be4ff9cc254348808391eb7bf6240b.
SDK dist/sdk-0.7.0-style-final.zip extraido fora do checkout com quatro projetos
API-only aprovados em Temp/opencode/vbrasfoot-sdk-author-7bde158169ab436c8964453851edc318.
Sem publicacao/tag/upload; jogo posteriormente observado fechado, instalacao
requer novo preflight e backup do comparador anterior conhecido.

## UI0.7.1 — card central azul-petroleo

Pedido posterior com capturas: painel menor que o jogo e visual semelhante ao
painel nativo. Host transparente centraliza card limitado1200x740/area-32px,
com Fechar no rodape. Tema azul-petroleo/campos claros/botoes claros; sem assets,
mudancas de consultas, save ou bounds do jogo. Comparador1.1.1/minLoader0.7.1.
test.ps1 -IncludeUi aprovado; smoke verifica transparencia, centralizacao e
dimensoes menores, comparar/fechar e controles em1280x720. Previews sinteticos
build/embedded-style-preview-331213923190034028.png e
build/embedded-style-small-10032760155564305791.png inspecionados.
Fundo cinza desses previews e apenas preenchimento fake, nao captura do jogo.
Aprovacao visual humana e escalas adicionais permanecem pendentes.

Portable0.7.1 regressao aprovada em fixture
Temp/opencode/vbrasfoot-install-b6afe51b227141c9942c61da2f0a262e;
SDK-only0.7.1 extraido/testado fora checkout em
Temp/opencode/vbrasfoot-sdk-author-7b53b296b3ce4840881a79ff021ba2ed.
Sem publicacao/tag/upload e sem inferir aprovacao visual pelos testes.

## UI0.7.2 — aproximacao da composicao nativa

Usuario confirmou direcao0.7.1 e pediu maior semelhanca com captura de busca
nativa. Comparador1.1.2/conteudo900x590, area escura de escolha, X no canto,
sem faixas extras/split/Fechar no rodape. Botoes com borda/relevo do LAF local,
texto verde nas acoes, cabecalhos normais e setas com tooltips. Sem assets,
filtros sem respaldo no SDK, alteracoes de consultas ou schemas de saves.
test.ps1 -IncludeUi aprovado com card<=940x660 e viewports de listas>=300x50
em1280x720. Primeiras previas expuseram lista comprimida: corrigido layout e
adicionado check de viewport, nao apenas bounds dos botoes. Previews finais
build/embedded-style-preview-7337705821029412817.png e
build/embedded-style-small-14695976260294565821.png fake-only; menor inspecionado.
SDK ZIP0.7.2 validado fora checkout em
Temp/opencode/vbrasfoot-sdk-author-09469d9af68a4229b33caf13e646a404.
Portable0.7.2 gerado, mas regressao interrompida pelo guard de processo ativo;
nao relatar essa regressao como aprovada. Nenhum processo encerrado, instalacao
adiada; confirmacao visual humana dentro do jogo permanece pendente.

Depois do ajuste de mensagens ao novo rotulo Atualizar, smoke e SDK-only
reexecutados/aprovados. Pacotes revisados locais:
dist/portable-0.7.2-native-reviewed e dist/sdk-0.7.2-native-reviewed.zip.
SDK fora checkout: Temp/opencode/vbrasfoot-sdk-author-db757c6ef5774698b3e81982762febe0.
Sem tag/upload/publicacao; guard de jogo aberto continua obrigatorio.

## P4/0.7.3 — etapas de save e ramo de inicializacao de leitura

Usuario confirmou visual0.7.2 melhor e adiou refinamentos, autorizando trabalho
nos eventos confiaveis de carreira. Incremento interno, nao P4/P3 completos.

REA binary_session consultado: sem alvo aberto, provedores Ghidra/IDA nao
configurados, Hopper indisponivel em Windows e JEB sem provider. Nenhuma instalacao
ou reconfiguracao feita. Inspecao suportada via javap local do EXE de SHA conhecido
2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c;
build/career-boundaries-javap.txt privado/ignorado, bytecode estatico, nao runtime.
c.a.ae escreve .info primeiro e engole IOException; depois .s22 e copia .sbck.
c.a.c pula inicializacao com frame existente e chama best.f.ap no ramo sem frame.
Nenhum trecho de bytecode/artefato proprietario incluido no pacote publico.

CareerTransformer mantem SHA inteiro c.a e schema; novos sites exatos:
FileOutputStream.close no save (apos fechar .info) e best.f.ap na leitura.
Guard SaveAttempt limitado64, ticket na entrada, recusa ambas conclusoes de saves
sobrepostos/atrasados/foreign/duplicados/apos close. Sem cancelar/serializar saves
originais. SaveOutcome distingue metadata/primary/backup/returned/bound/completo,
sem I/O/mod callback no hook. Closure de JVM nao sintetiza completions.
Saves com primaryClosed e falha .info/backup continuam PRIMARY_WRITE_COMPLETED
experimentais, mas nao sequencia completa. Marcador de ramo de inicializacao so
conta com leitura aceita; nao promove criacao/UNKNOWN nem prova fila/UI pronta.
Report privado career-lifecycle.properties inclui counters/ultimo resultado/pendentes.

test.ps1 -IncludeUi aprovado:39 guard checks e demais suites/modelos/overlay.
test-career-hooks.ps1 aprovado:61 checks original I/O, incluindo .info como
diretorio novo e falha primaria/backup/null/fallback/schema/releitura. Sequencia
completa1, parciais2 (backup e .info), falhas2 (primario e null); frame sintetico
observa zero ramos completos de UI. Origem EXE/save hashados e preservados.
Fixture: Temp/opencode/vbrasfoot-hook-io-b452824e73f642968da9d62ad0abb665.

Portable0.7.3 gerado/regressao aprovada:
Temp/opencode/vbrasfoot-install-582fc2659e4f4e369d2b230e2751ad84.
SDK-only0.7.3 zip fora checkout aprovado, quatro projetos API-only/hashes/allowlist/
negative/JSON/no-init: Temp/opencode/vbrasfoot-sdk-author-f446c566eaf545bea9fb45c859886b3c.
Comando agregado ultrapassou240s durante SDK test; nao inferir sucesso dessa
execucao. SDK teste reexecutado separado e aprovado, log career-boundaries-sdk-final-test.log.
Sem tag/upload/publicacao. Nao fsync/commit atomico/snapshot/identidade persistente,
CareerLoaded/CareerSaved/CareerUnloaded publico ou desbloqueio de sidecars/P3.

Proximo gate real: abrir copia Documents com --probe-career-hooks, carregar uma
carreira existente, salvar normalmente e fechar. Conferir read.initializedPaths e
save.last.* no relatorio; reabrir no EXE original. Sem criar carreira/jogar para
validar este incremento. Falhas induzidas somente em fixture privada, nao no jogo
do usuario. UNKNOWN da criacao e unload nao mapeado permanecem bloqueios separados.

Inspecao estatica adicional de Output.close na biblioteca local Kryo4.0.2
(SHAf32aceb5b2eb39be6f339da722c3bb043df163c38d51522acbb77ee4d6c4d362)
mostra flush seguido de close do stream com IOException engolida. Saida privada
build/career-output-close-javap.txt. Nenhuma falha dessa classe foi induzida em
runtime nesta entrega. completeSequence segue sendo apenas sequencia de marcadores,
nao prova de ausencia de erro subjacente. Este limite impede anunciar CareerSaved
confiavel sem novo mapeamento/corroboracao; nao adicionar close/fsync ao jogo para
mascarar o problema e nao instrumentar biblioteca sem fingerprint/schema/testes.

### Corroboracao UI real0.7.3 — caminho de leitura e etapas de save

Usuario respondeu "funcionou" ao roteiro de carregar carreira existente/salvar/
fechar/reabrir. Sessao privada008c347b-75ed-4b54-85f4-61c4e13f56a5 conferida:
loads1/read.initializedPaths1, phase.beforeClose ACTIVE/afterClose CLOSED,
generation3, saves1/completeSequences1, partial0/failed0, healthytrue,
staleCompletions0/unboundSaves0 e ambas contagens inFlight0 no fechamento.
Ultimo save: metadataClosed/primaryClosed/backupCopied/returned/bound/completo
todos true. Load na AWT-EventQueue-0, save na Thread-1. Dispatcher accepted2/
delivered2, dropped/failed/discarded0, workerAlivefalse; stderr vazio.

Hashes atuais do .s22 e .sbck iguais:
acd4238eb72f45debb00816f80d372a4bc2a4c179c33fb558385938a7d7742c1.
.info existe252bytes; nao desserializado. EXE permanece SHA original conhecido.
Igualdade atual nao prova snapshot atomico nem ausencia de alteracoes externas
desde a sessao. Nomes/caminhos privados nao copiados para este registro publico.

Novo marcador de passagem do ramo completo de inicializacao agora corroborado
na UI real para essa leitura existente, sem ampliar para criacao, todas telas,
fila EDT pronta, unload/troca ou durabilidade. Confirmacao humana do roteiro e
registrada, mas log do loader nao observa reabertura pelo EXE original separadamente.
Limite de IOException engolida por Kryo Output.close permanece; nao liberar
CareerSaved/CareerLoaded/CareerUnloaded completos, identidade ou P3 por esta evidencia.

## P4/0.7.4 — IOException do close subjacente detectada

OutputCloseTransformer novo, apenas memoria/opt-in17/21, sem distribuir/copiar
bytecode da biblioteca. SHA Output.class local extraido do Kryo conhecido:
73ba15ba16dbcff3a2ce8456de5b70126077c1b62b4128359bcbb2d2dfc4e04a.
Reusa inspecao javap0.7.3, sem repetir setup/provider REA indisponivel. Um close,
flush, chamada OutputStream.close, handler IOException e ASTORE exatos exigidos.
Marcador normal apos invocacao e falha apos ASTORE do catch original. Frames,
schema/catch/return permanecem originais; nenhuma politica de erro alterada.
LegacyRuntime verifica Output transformado antes de validate-only, CodeSource
da biblioteca e correto e cache/JAR original nao e transformado em disco.

CareerTransformer registra receiver antes do wrapper close, preservando stack.
HookProbe correlaciona por identidade/ThreadLocal; outros Outputs fora da tentativa
nao atribuem sucesso/falha. Receiver so vive durante tentativa; flags/counter no
guard/report, nao streams/Throwable/objetos publicados. Falha sticky impede bound/
PRIMARY_WRITE_COMPLETED e completo; completeSequence0.7.4 exige streamClosed.
Wrapper-return sem close conhecido conserva semantica experimental anterior,
mas nao sequencia completa. Sem fechar duas vezes/retry/rethrow/fsync/reparo do jogo.

43 guard checks +9 novos bytecode checks sinteticos + test.ps1 -IncludeUi aprovados.
Primeira compilacao de teste tentou importar ASM com --release17: erro detectado;
negative agora e classe fixture propria sem handler, preservando toolchain normal.
Fingerprint recusado/handler ausente/normal/IOException engolida/matching Output/
fora de save/null/sticky/cleanup/report exercitados com codigo proprio.

69 checks I/O/biblioteca originais aprovados:
Temp/opencode/vbrasfoot-hook-io-e4029e52c1f64da98aa2fe4134c7ca53.
Pipeline c.a.ae real observa streamClosedtrue e schema preservado. Falha subjacente
foi induzida com stream proprio injetado no Output ORIGINAL, ticket e marcadores
metadata/backup sinteticos: metodo engole IOException/retorna, guard recusa sucesso
e conta falha. Flush failure ainda propaga KryoException e nao chama close extra.
Recuperacao exclusiva funciona; falha de outro Output nao contamina o atual.
Resultado em output-close-observations.properties privado. Nao e falha de disco
cheio/IO real de FileOutputStream no save completo; nao anunciar tal cobertura.

Portable0.7.4 gerado/regressao aprovada:
Temp/opencode/vbrasfoot-install-f68d45f10a4e49eeb2426d908bb0058e.
SDK-only zip fora checkout aprovado (quatroprojetos/hash/allowlist/negative/JSON):
Temp/opencode/vbrasfoot-sdk-author-2b566056cb5f4038a67a398b66ae6ec4.
Pacotes dist/portable-0.7.4-close-final e dist/sdk-0.7.4-close-final.zip locais,
sem tag/publicacao/upload. API major1/metodos/capacidades inalterados.
UI real0.7.4 pendente, assim como demais rotas de save/criacao/unload/troca,
fsync/commit atomico/graph snapshot/identidade persistente/P3. Fechamento subjacente
agora observado nesses caminhos, nao liberar eventos completos automaticamente.

### Corroboracao real0.7.4

Usuario confirmou "pronto" ao roteiro load/save/fechar. Sessao privada
13b58cd0-a16a-42df-ac43-0928fb39aea4:1load/initializedPaths1,1save/completo1,
streamClosedtrue/streamCloseFailedfalse/streamCloseFailures0, metadata/primary/
backup/returned/boundtrue. Partial/failed/stale/unbound/pendentes0, healthytrue.
Load EDT/save Thread-1, dispatcher2/2 sem perdas/workerAlivefalse, stderr vazio.
Save/backup atuais SHAacd4238eb72f45debb00816f80d372a4bc2a4c179c33fb558385938a7d7742c1
iguais; .info252bytes nao desserializado, EXE e loader0.7.4 hashes intactos.
Hash igual ao anterior sem avancar nao significa ausencia de operacao: fechamento
observado. Nenhuma falha fisica de disco/fsync/unload provada nessa sessao.

## SDK0.8.0 — contrato publico qualificado de I/O, nao lifecycle completo

Contrato definido em CAREER-IO-EVENTS.md antes da implementacao. Java SDK in-process,
nao HTTP/servico remoto: skills/instrucoes Postman consultadas, nenhuma workspace,
collection/spec artificial criada e nenhum recurso privado enviado a servico.
CareerIoEvents DTOs imutaveis/default ModContext.careerIoEvents/capability
events.career.io. EXISTING_LOAD_COMPLETED exige decode exclusivo/retorno/geracao
e ramo best.f.ap observado; SAVE_SEQUENCE_COMPLETED exige load qualificado atual
e todas etapas metadata/primary/stream/backup/retorno/bound. Fallback uma vez;
frame existente que pula ramo nao qualifica. Nao criacao/unload/ID persistente.

Hub typed usa o dispatcher existente com estado/revisao independente; antigas
CareerObservations Kind/DTO inalteradas.32workers compartilhados por budget
atomico,4assinaturas totais por mod entre streams; retired callback ignora interrupt
retém slot ate sair. Cleanup/falha/init/overflow/permissions/capability/provider
e revogacao por ownership reutilizados.36 novos checks sinteticos: replay/FIFO/
paths ausentes, qualificacao, partial/closefailure/stale/overlap/fallback/reset,
callback failure/replay de recovery/worker resourcecap/per-mod/revogacao/preflight.
Build e suites headless/bytecode/modelos aprovados; UI smoke separado aprovado.
test.ps1 -IncludeUi agregado ultrapassou180/300s: nao relatar execucoes truncadas
como aprovadas; build.ps1 e UiSmokeTest reexecutados separadamente com exit0,
logs qualified-build-final.log/qualified-ui-final.log. Nenhum processo de usuario
encerrado; timeouts do harness nao sao evidencias de lifecycle.

Original I/O fixture72checks aprovada:
Temp/opencode/vbrasfoot-hook-io-e5a37bf75c0c4e9eb6e5504c3b0b399f.
Observador0.8.0 JAR SDK-only assina os dois streams. O frame sintetico do I/O
original entrega reads/writes antigos, mas NAO eventos qualificados de load/save;
checks verificam ausencia de falsos positivos e counters0 do stream novo.
Evento positivo do novo stream validado sinteticamente, nao afirmar UI real dele
ou mod terceiro humano. Report privado adiciona io.existingLoads/completedSaves/phase.

Portable0.8.0 gerado/regressao aprovada:
Temp/opencode/vbrasfoot-install-f53fa26629d643e3aa0cb5e8ec6b0bcb.
SDK zip/API-only quatroprojetos fora checkout aprovado:
Temp/opencode/vbrasfoot-sdk-author-a7355f5dbbd3480b9c8578e49cf18c94.
Novo guia incluido na allowlist, Javadocs e JSON validator condicional/minLoader
testados. Pacotes dist/portable-0.8.0-io-final e dist/sdk-0.8.0-io-final.zip locais,
sem tag/upload/publicacao. Novo observador nao instalado automaticamente; exige
flag e duas capabilities, enquanto comparador permanece independente dessa flag.
Sem fsync/graph consistency/atomicidade/sidecars/P3/lifecycle completo/gameplay.

Pacotes revisados com guias finais tambem aprovados:
dist/portable-0.8.0-io-reviewed (fixture7a5041591cc34fcaa6d819b0069bceaa) e
dist/sdk-0.8.0-io-reviewed.zip (fixture9d1cab12a48442f4b1a6a3f047ed4214).

## Consumidor real0.8.0 e mapa criacao/unload

Usuario instalou observador conforme guia e confirmou feito. Sessao privada
43e0dddf-96ec-4cae-9fec-bd18786dcc09: mod observer ACTIVE; CURRENT_STATErev0,
INVALIDATEDrev1/2/3, EXISTING_LOAD_COMPLETEDrev4 uma vez e
SAVE_SEQUENCE_COMPLETEDrev5 uma vez, geracao3. Stream antigo manteve reads/writes
originais. Guard io counts1/1, streamClosedtrue/failedfalse, healthytrue,
partial/falha/stale/unbound/pendentes0, dispatcher2/2 sem perdas, stderr vazio.
Entrega positiva ao consumidor SDK-only na UI real agora corroborada nesse
caminho normal; nao todas rotas/falhas UI/criacao/unload/fsync/atomicidade/P3.

Depois de pedido execute, observer ja estava ausente em Documents/mods; nao
apagado/movido pelo agente nem sobrescrita de mods-selection. Jogo fechado.
Branch feat/creation-unload-boundary-map: ferramenta somente leitura e CI tests,
sem mudar loader instalado0.8.0/SDK/EXE/saves, sem nova versao/release.
Novo MapCareerBoundaries usa scanner existente, roles exatas com descriptor,
recusa registros malformed/Graphics/JDialog genericos como fronteira.21tests
proprios e11scanner aprovados; wrapper recusa output existente/perfil/links.
Mapa final privado1038classes/73referencias em
build/lifecycle-map-49bcef9e049a4f9e8c9cb6b7c37bc6f2/boundaries-final.tsv.
EXE hash conhecido preservado. Bytecode javap privado da mesma identidade.

Novas evidencias estaticas: cR.oR agenda cS que chama V; hk.oR agenda ho que
chama ax. Construtores/painel RETURN podem anteceder continuacao agendada.
ap/at/ax fazem caminhos reutilizados/novos paineis; nao prontidao exclusiva.
eg.rq System.exit(0), JFrame principal EXIT_ON_CLOSE,5sites exit no ZIP.
7root writes:2null clinit e5constructor/load/setter, sem null unload direto
fora clinit nesse recorte. Metodo best.n.a(JFrame) setter de referencia, nao
commit. Estatistica/ausencia no recorte nao prova ausencia de rota dinamica.
REA JVM provider ausente previamente registrado, usado fallback suportado local
sem reconfigurar/instalar ferramentas ou executar paths do jogo. Guia detalhado
CREATION-UNLOAD-BOUNDARIES.md distingue fatos/inferencia/pedencias/sonda futura.
Sem instrumentacao adicional/liberar carreira nova/unload/sidecars ou chamar
usuario para repetir criacao apenas por um novo candidato ainda nao observado.

Wrapper testes negativos locais: output existente recusado com hash intacto;
EXE falso de3bytes em fixture propria recusado antes de gerar TSV. Parser
PowerShell aprovado, git diff --check limpo. Links/junctions guard implementado,
nao exercitado nesta rodada (nao relatar teste de link como aprovado).
