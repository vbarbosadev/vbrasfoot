# Checkpoint de execucao continua — 21/10/2026

Nao substitui roadmap/STATUS.md (mantido inalterado por instrucao do usuario).
Checkpoint para retomar sem reexecutar evidencias ou depender de credenciais no
contexto. Nao instalar builds/mods nem tocar saves/EXE pessoais.

## Entregas deste lote

-23/10/2026 bug de devolucao nativa corrigido. Usuario confirmou usar Devolver
  emprestimo de Carlao43534/RuanCarvalho23758 e nao salvar antes do novo pedido.
  DiscoSHA659a54d60eb11be8432a66c1187609bde561e82c043c6138dfb67505a6ca82d1,
 4trackers,ambosFlamengo. Antes APPLIED implicava ativoeterno;agora LoanReturnEvidence
  exige bo original ida0na data do pedido e volta0na ordemcorreta/datasvalidas,
  sem intermediarios/duplicatas/novomovimento. Depois lote exige jogadorunico
  id/nome na origem semtracker. Trata Utransient no estadoNAOSALVO e ei/gD+ejtipo1
  depois do save. Treinadores nao colidem ids. Nao aceita soausencia/retornoeditado.
  Contratos/ledgersAPPLIED intactos,tombstones impedemrepetir,no refund,taxasantigas
  nao inferidascomosalvas. BaselineMacarraoMatheus naoalterado. Limite3mantido.
 16testesevidencia/19lote e regressaocompleta passaram. Probecopia real4APPLIED:
  tNoriginaldos2retornos+remocaotrackerspelo callerEMULADO apenasmemoriafixture,
  ficou2ativos e checksaprovaram;SaveCheckroundtripprivado comprep.mboriginal
  materializandoidtransiente tambemaprovou. Tentativasemmbnaopassou e foicorrigido
  harness,nao relaxado produto. Nenhum novo emprestimo pessoal nem save pessoal
  escrito. Pacote native-loan-return-20261023 instalado comjogofechado;backup
  Documents/Brasfoot22-23-loan-return-backup-20261023-092625;85arquivosprotegidos
  intactos,JARconfere,validate-onlyinstaladoaprovado. Usuario precisara refazer
  devolucoes sefechousemsalvar;nao anunciardevolucaojaaplicadanosavepessoal.
  Evidencias NATIVE-LOAN-RETURN-RECOGNITION.md. UIcompleta pessoal nao testadaneste
  lote,adapter/runtimeemcopia e codecforam;roundtripnaoe cliquemenusalvarreal.

-23/10/2026 pedido usuario:cap3emprestados e listado segue rotinaoriginalsemfee.
  LoanDestinationLimit.MAX3 usado em nativeGuards para ambos caminhos,preflight,
  prepare e ultimo guard antes de efeito.4contratos pessoais existentes preservados;
  com>=3novos bloqueados,sem devolverautomaticamente. Nova politica native-listed-
  no-fee/1 no codec v2;sem history/medianafeesmod,listados usam best.l.a uma unica
  vez,revalidando fz e limites. Nao listados continuam politica personalizada.
  Politicas antigas nao migradas/bytespreservados. Refresco tambem no nativo.
  15checkspolicycodec passaram;probe real em copia:Munir,JonathanFernandes,Joannis
  Steig3transferencias nativas fee0,trackers1/2/3;quarto listado recusado semmutator.
  Saldo/clock/copia saveintactos. GateARTIFICIALharness,nao qualificacao da sessao
  pessoal/save/reopen. Primeira buscaampla parou em clube ambiguo semrelaxarguards;
  novaJVMlimpa buscou clubesunicos e passou. Regressaocompleta e release aprovados.
  Pacote portable-0.9.4-loan-cap3-native-listed-20261023 instalado comjogofechado.
  Backup Documents/Brasfoot22-23-loan-cap3-backup-20261023-085852;85arquivosprotegidos
  intactos,JARconfere,validate-only instalado passou. Nenhuma transferencia pessoal.
  Evidencia LOAN-THREE-LIMIT-NATIVE-LISTED.md.

-23/10/2026 otimizacao solicitada da lentidao do fluxo: aliases compartilham
  observacao dentro da mesma chamada com verificacao independente final;teste
  conta1observacao adicional na agregacao em vez de9(3aliases). Todos osinventarios
  e fontes continuam obrigatorios. Sem cache entre pedidos e semremover guards.
  LoanHistoryBatch valida ativos/aposentados num unico percurso de elencos/trackers,
  antes de cotacao e novamente na execucao/dispatch.15checks passaram,399leituras
  emvezde798para2alvos;recusa idduplicado,trackerdivergente,junior,etc. Testegrupo11
  passou incluindo observacaocompartilhada. Probe do saveANTIGO emcopia e metadados
  do backup confirmou checks reais:86ms separado/61mslote(amostraunica,aquecimento,
  NAO fluxo completo). Dados pessoaisATUAIS ja tinhamnovosemprestimos/save e foram
  preservados;nao restaurados para backup. SaveatualSHAa3babfa03ef7e4ad47f234f12b2e90c16a6f8a7f87f09afd5e0b27943bb557b7.
  Botao Avaliando.../Executando... e logs native-loan timing por elegibilidade,
  associacao,cotacao,execucao semtempo humano da confirmacao.
  Regressaocompleta/release/validate-onlyinstalado passaram. Pacote
  portable-0.9.4-loan-flow-perf-20261023 instalado comjogofechado,copiaautorizada.
  Backup Documents/Brasfoot22-23-loan-perf-backup-20261023-084010;76arquivosprotegidos
  intactos,JARconfere,nenhuma partidadata ou efeito novo executado peloagente.
  Nao anunciar ganhoend-to-endmedido;resta I/Ode seguranca evarredura naEDT.
  Evidencias LOAN-FLOW-PERFORMANCE-20261023.md.

-23/10/2026 usuario pediu resolver bloqueio historico e nitidez. Perguntado sobre
  destino dos efeitos antigos,escolheu explicitamente MANTER SAVE ATUAL,sem repetir
  transferencia/cobranca. Fechou jogo voluntariamente;nenhuma sessao pessoal morta.
  Nova LoanBaselineStore em loan-baselines guarda decisao de referencia atual,
  SHA/tamanho,2digests e tokens das fontes. APPLIED/contratos/diarios/aliases intactos;
  nao declara inexistencia de efeito anterior. Contratos0fee selecionados saem
  SOMENTE dos checks de ativo no destino;mantidos em all/tombstones. A cada quote,
  revisao e dispatch verificam jogador unico na origem sem tracker;estado diverso
  bloqueia. OutrosAPPLIED/UNKNOWN/EXECUTING continuam regras anteriores. Token da
  referencia fica retido nas fontes do novo ledger;sem generic/legacy bypass.
  Referencia REAL do principal b8b2e5e1 gravada depois de backup e validacao em
  copia dos2players na origem,tracker0 e fontes/recibos exatos. Token9e46b892a9bbf1a6bb241a3f25e19e836a7de7c0185a90d1163d93f389b6bcc4.
  Backup Documents/Brasfoot22-23-current-baseline-backup-20261023-073953.
 69arquivosprotegidos preexistentes intactos;saveSHA52837913efd1f59661f0d45a177033fcb497d3c8e64753b6908fe49a9872eeed.
  DPI medido por Java:1920x1200fisico/1536x960logico/125%. Com uiScale1:
  1920x1200logico/100%. Novo atalho opcional gerenciado vbrasfoot-nitido.cmd inclui
  probe e --pixel-perfect;launcher remove flag antes do Main. Escala normal mantida
  no atalho antigo;nenhuma configuracao Windows/driver/resolucao alterada.
  Regressoes passaram,12testesbaseline,10agregacao e testePS argumentos/recuperacao
  geracoes7/8arquivos,rejeicaoindicesinvalidos. Pacote current-baseline-pixel-20261023
  instalado;JARconfere,launcherinstalado--pixel-perfect--validate-only passou.
  Limite: nova fixture abriu sem janela visivel tanto default quanto100%,sem erro
  explicito;nenhum pedido completo transfer/save/reopen foi qualificado nela.
  Somente probes proprios foram encerrados. Nao anunciar emprestimo UI real aprovado.
  Nova leitura baselinepersistido+guards reais passou. Evidencias CURRENT-LOAN-
  BASELINE-AND-DPI.md;estado selecionado e decisao do usuario,nao prova de NO_EFFECT.

-23/10/2026 painel de emprestimo compacto no estilo solicitado (verde/azul,titulo
  amarelo,textosbrancos,Confirmar/Cancelar ou Fechar) implementado em NativeLoanDialog
  e integrado a todas as respostas/confirmacoes do botao. Escape/dispose cancelam;
  sem default Enter para executar. Sem alterar regras/taxa/guards.
  Logs reais confirmaram HistoricalLoanFailure. Nova copia descartavel unconfirmed-
  639dd277db8140729bf756c23b9746af decodificada sem save/transferencia/progressao:
  Flamengo SHA52837913efd1f59661f0d45a177033fcb497d3c8e64753b6908fe49a9872eeed,
  clock1643295202790,trackers0. Macarrao136609 esta no Fluminense do Itaum5802;
  MatheusReis2498 esta no Fluminense81. Ambos APPLIED para Flamengo,taxa0 nos
  metadados. Divergencia CONFIRMADA,causa/descarte/restore nao comprovados. Nao
  apagar/reancorar/converter/repetir. Nova mensagem mostra nomes dos nao confirmados.
  Regressao completa passou;9checksheadless painel e12checksmodal real proprio
  (semruntimejogo) passaram. Pacote final dist/portable-0.9.4-native-loan-panel-final-
  20261023 instalado na copia autorizada Documents/Brasfoot22-23 APOS fechamento
  das sessoes pessoais (nenhuma encerrada pelo agente). Backup antes da atualizacao
  Documents/Brasfoot22-23-loan-panel-backup-20261023-071247.69arquivosprotegidos
  intactos,JAR confere,validate-only instalado passou. Erro historico NAO resolvido
  por bypass;emprestimos podem continuar bloqueados. Evidencia NATIVE-LOAN-PANEL-
  20261023.md. Nenhum pedido novo executado no save pessoal.

-22/10/2026 CORRECAO ASSOCIACAO UNICA implementada e instalada na copia autorizada
  Documents/Brasfoot22-23. Registro duravel com lock entre threads/processos e
  criacao idempotente. Flamengo consolidado offline:1principal b8b2e5e1 e3aliases
  cc3d71bd/e1c2889d/e35cccf9 preservados. Todos os67arquivosanteriores protegidos
  (save/EXE/historicos) identicos ao backup feito ANTES da consolidacao:
  Documents/Brasfoot22-23-association-backup-20261022-135141.
  Aliases congelam associacao e inventarios dos5roots historicos padrao. Membro
  removido/novo/alterado,HEAD/namespace alterado,intent sobrevivente e checksum
  invalido bloqueiam sem inventario parcial. Principal pode evoluir normalmente.
  Coordenador nativo agrega todos os membros/ledgers/diarios/contratos/financas/
  witnesses,retém tokens de aliases e revalida antes do claim. Fluxos antigos e
  acesso direto a alias bloqueados para nao contornar historicos.
  Regressao completa/release passaram:25checksregistro incluindo24threads e4JVMs;
  7checksagregacao de4ledgers e2APPLIED obrigatorios;codec/fontesalias testados.
  Pacote dist/portable-0.9.4-unique-association-20261022 instalado e validate-only
  instalado aprovado. Nenhuma partida/data avancada,nenhum jogo do usuario aberto.
  LIMITE: identidade duplicada RESOLVIDA,mas Macarrao/MatheusReisAPPLIED nao foram
  comprovados no save original semtrackers. Continuam bloqueando novos efeitos;
  nao anunciar recuperacao/liberacao irrestrita. Renomear grupo consolidado exige
  migracao propria ainda nao implementada (recusa conservadora). Mais detalhes em
  UNIQUE-SAVE-ASSOCIATION.md;nao apagar/converter historicos antigos por suposicao.

-22/10 saveFlamengo escolhido pelo usuario inspecionado em copia descartavel.
  OriginalSHA52837913efd1f59661f0d45a177033fcb497d3c8e64753b6908fe49a9872eeed intacto.
  Temporada1,12883registros todos temporada1,nenhum multitemporada/multiclube nos
  elencos inspecionados. Nao comprova preservacao futura; nao avancamos calendario.
  Nova politica origin-club-career-weighted/1 captura temporadas registradas no
  clube atual, exclui outros clubes, pondera70atual/30historico quando suficientes,
  fallback para periodo valido,ausente!=zero. Codec politicasantigas preservado.
  UI sem selecao UUID/associacao/revisao, mostra decisao curta/taxa e so confirmacao
  de transferencia. Fonteambigua recusa. Testes29historia/28UIautomatica e regressao
  completa passaram;nativeflowFlamengoGanso recusado/Matheusgratis/cancelamento/
  save/reopen/paidMarios390592 com inputsARTIFICIAISsomentefixture aprovados.
  IMPORTANTISSIMO: instalacao REAL de testes tem4associacoes para EXATO caminho
  Flamengo.s22: Ganso+RodrigoNO_EFFECT;MacarraoAPPLIED;vazia;MatheusReisAPPLIED.
  Original sem trackers;nao inferir inclusao/descarte/restore dos efeitos antigos.
  Agora sera RECUSADO por identidade ambigua,nenhum vinculo ignorado/apagado.
  Reconcilacao das4fontes NAO realizada e continua pendente. Nao anunciar Flamengo
  existente pronto para emprestar;somentecopylimpa aprovou. Evidencia completa em
  FLAMENGO-CLUB-HISTORY-VALIDATION.md.
  Pacote dist/portable-0.9.4-club-history-reviewed-20261022 instalado na copia
  autorizada Documents/ Brasfoot22-23;73arquivosprotegidosintactos;backupcompleto
  Brasfoot22-23-club-history-backup-20261022-110408. validate-only instalado aprovado.

-22/10 integracao nativa e continuacao limitada entregues na copia de testes.
  NativeLoanUi substitui uma unica callback do botao MJ em a.iA fingerprintado,
  captura selecao/clubes por identidade, associa path exato com escolha humana
  inicial, mostra taxa e pede confirmacao. Sem UUID/chave manual. Refresh de
  elenco/saldo apos sucesso ou efeito parcial sem replay.
  LoanContinuationStore usa ledger unico com ancora sentinela, request bytes e
  revisoes originais, referencias HEAD historicas e tombstones permanentes.
  Gate narrow loan-only prepara com APPLIED exatos sem limpar receipts; generico
  continua bloqueado. I/O excluido atraves commit terminal. UNKNOWN/conflitos/
  sources ausentes/alterados/financeiros alheios bloqueiam. Painel antigo readonly
  apos ledger. Nova revisao exige recarga original e reconhecimento explicito.
  Fluxo real:3emprestimos (1taxa/2gratis),2consecutivos + terceiro apos save/reopen;
  roster/saldos/trackers preservados, clock inalterado. Inputs da paid branch e
  vaga do terceiro foram ARTIFICIAIS em fixture descartavel somente; ver
  NATIVE-LOAN-INTERFACE.md. Nenhuma partida/avanco.
  Regressao completa release passou:19store/15gate/15coordinator de continuacao,
  11UIcontextual e demais suites; smoke Swing aprovado. Pacote
  dist/portable-0.9.4-native-loans-20261022 instalado em Documents/ Brasfoot22-23
  autorizado pelo usuario como COPIA DE TESTES. Backup inclui sav/app/data completos:
  Brasfoot22-23-native-loans-backup-20261022-010623.72arquivos protegidos intactos;
  validate-only instalado aprovado. Limites32pedidos/emprestimos historicos ativos,
  mesmo jogador/origem jamais replay; retorno/expiracao e fee inclusion generica
  ainda nao qualificados. Nao chamar recuperacao irrestrita ou escrita publica.

-22/10: resolvidas participacao parcial e habilitacao indevida do botao Transferir.
  Policy /2 aprova pouca participacao quando candidato conhecido<=25% do maximo
  conhecido>=5, sem exigir todos os colegas e sem desconhecido=zero; diagnostico
  informa cobertura. Policy /1 preservada no codec/digest e avaliacao de historicos.
  Botao nasce desativado, habilita apenas apos revisao de chave APPROVED sem
  bloqueios conhecidos e invalida em edicao/selecao/operacao/erro/execucao.
  Build/regressao passou:38checks release/taxa/codec/recovery,57coordinator e
  smoke Swing sem classes do jogo. Instalado pacote partial-use-ui-20261021 na
  copia autorizada Documents/ Brasfoot22-23;24arquivos protegidos intactos.
  Backup Brasfoot22-23-partial-use-backup-20261022-000702;validate-only instalado
  aprovado. Nenhum fluxo real de transferencia foi executado nesta rodada.
  Recuperacao para novos emprestimos apos APPLIED/save continua bloqueada.

- Regra relativa e taxa implementadas em LoanReleasePolicy/LoanTerms e codec v2;
  v1 permanece intacto. Origem ate mediana gratis; acima com <=25% do maximo de
  participacoes do elenco (amostra>=5), taxa10% arredondada abaixo. Ausencia de
  estatistica !=zero. Destino ainda avalia mediana, limites e elegibilidade.
  Indicador disponibilidade nao limita v2, sem alterar flag ou expor bypass SDK.
  Teste painel real paid branch com inputs artificiais em fixture descartavel:
  EXECUTED/APPLIED, taxa147916, saldos e jogador preservados apos salvar/reabrir,
  duplicata bloqueada sem cobranca nova; clock inalterado. Ver LOAN-RELATIVE-FEE-VALIDATION.md.
  Regressao completa build passou;33checks novos politica/taxa/codec/falhas parciais
  e smoke Swing. Pacote portable-0.9.4-relative-fee-20261021 instalado na copia
  autorizada Documents/ Brasfoot22-23;24arquivos protegidos intactos, backup
  Brasfoot22-23-relative-fee-backup-20261021-204719, validate-only instalado aprovado.
  Recuperacao para uso continuado apos APPLIED/revisao antiga AINDA bloqueada.

- Correcao de diagnostico NO_EFFECT: codigo nativo e motivo da pre-validacao no
  resultado/log; consulta nativa somente leitura antes de registrar chave nova.
  Novo pedido gera chave somente na tela; registrar chave existente nao reabre
  tentativa encerrada. Nenhum schema/contrato antigo foi migrado ou apagado.
  Build/regressoes passaram,43checks de decisoes/diagnosticos e smoke Swing.
  Pacote dist/portable-0.9.4-loan-diagnostics-20261021 instalado na COPIA DE TESTES
  explicitamente autorizada C:\Users\vinicius\Documents\Brasfoot22-23.
  Backup irmao Brasfoot22-23-diagnostics-backup-20261021-164954;24arquivos de
  save/EXE/config/mods/metadados conferidos intactos. validate-only instalado com
  --probe-career-hooks aprovado; jogo nao iniciado nessa verificacao.
  Pedido antigo de Ganso permanece NO_EFFECT; nao inferir o motivo passado a
  partir da elegibilidade atual. Uso continuado apos APPLIED segue bloqueado.

- Emprestimo integrado pelo painel real: solicitar/avaliar/confirmar transferencia/
  salvar/sair/reabrir em nova JVM aprovados em copia descartavel. Marios natural24,
  AEK->Athletico,33->34,tracker1/historico1,clock1642941578893 inalterado.
  Duplicata da chave apos reopen recusada, sem novo efeito. Evidencia em
  LOAN-INTEGRATED-VALIDATION.md. Transferencia usa gate real de ProbeSession.
  Painel reforcado para exibir aprovacao de revisao antiga como STALE_REVIEW_REQUIRED
  e interrupcao como OUTCOME_UNKNOWN, sem oferecer confirmacao de execucao.
  Recuperacao continua conservadora: APPLIED/UNKNOWN/revisao antiga bloqueiam
  novos emprestimos no binding; uso continuado NAO concluido. Nao ha API publica
  de escrita. Nada instalado no jogo pessoal. Guia em packaging/LOAN-INSTALL.md.

- PR65:inventario offline dos5arquivos originais,1317classes/406713instructions,
  42writes diretos reencontrados. Descobriu UnsafeRegionField fora dos24setters.
 17checks proprios/CI17/21 aprovados;main fe623095a267167e6ae15be0b1433dd45376e738.
- PR66:perfil live de ledger no hook real:regions/transients false,14campos/
  rotas numeric exatos.14checks proprios/232financeiros/159I/O/SDK/CI17/21.
 main04cd2d93124b2a81ffcbc80b38e576d2a3a440d2.
- PR67:recusa alias entre clubes antes de preparar comando e em samples;
  limite16384/250ms/lista completa/identity.18checks proprios/236financeiros/
 159I/O/SDK/CI17/21. maincd71d56c1565ba0983a3546a162d9395209c5275.
- PR68:revisao pura de lacunas de observacoes persistidas,23checks proprios/
 238financeiros originais/159I/O/SDK isolado aprovados. Fontes em
 feat/explain-persisted-financial-evidence-gaps. Confirmar mergeCommit e CI de main
 na PR antes de iniciar alteracao posterior;nao presumir merge pela branch local.
- PR69 integrado em main377a39028bf3ebf428bf9939a9b39d25781c6095;
  CI de main39582749286 aprovado. Implementado em feat/version-financial-qualification-manifests:
 manifest0legacy/1sampled e witness schema1/2/3,mistura sem requalificacao retroativa.
33manifest/63persistencia/28root/29numeric-reference e239financeiros originais;
  integracao confirmada;nenhum fato antigo requalificado.
- Incremento de referencias em feat/trace-original-kryo-ledger-references:
  278financeiros originais/8grafos/bytes identicos/decoder alias aprovados;
  18scalar/37numeric-reference/28root/43writer/33manifest/63persistencia/
  23gaps/69witness/29dispatcher. Ver KRYO-LEDGER-REFERENCE-TRACE.md.
  Captura somente memoria/report;schemas1/2/3 nao armazenam esses facts.
  PR70 integrada pelo head exato a945c3133db6c9763a94907aabb0b2880711e33f,
  main78e499eafbe7e10f6eb323c462a3a0db6ecc0179;CI17/21 da PR39585653372 e
  push39585647938 aprovados.159I/O originais/SDK isolado finais aprovados.
  CI de main39586313483 aprovado em Java17/21.
- Incremento seguinte iniciado em feat/persist-kryo-ledger-reference-facts
  (rebaseado sobre main78e499e apos integracao exata da PR70):
  schema4/envelope1 separado,ausencia em1/2/3 preservada,43fact checks/
  89durable/280financeiros originais aprovados. Ver
  PERSISTED-LEDGER-REFERENCE-FACTS.md para paths e limites.159I/O originais e SDK
  isolado aprovados.PR71 integrada pelo head175927ffdb0844b61683d04f38f258d9644c0a1d,
  main277a3f6279c8af54609297a0c642edbeabd96512;CI17/21 PR39586553331/push39586548261
  aprovados.CI de main39587322612 aprovado em Java17/21.
- Incremento entregue na PR72 em feat/review-financial-multi-root-recovery (baseado no
  main277a3f6 apos integracao exata da PR71):revisao pura das3roots de metadados,
  nao transacao nem as2roots Kryo.32checks proprios/46coordinator/43facts/
  89durable/23gaps/160I/O originais e SDK isolado aprovados. Ver
  MULTI-ROOT-RECOVERY-REVIEW.md.Nao liberar reancoragem ou inferir inclusao.
- PR72 integrada pelo head67ac2dfa817a364907702ccb32c1e4ae556a41d9,
  main1068ff69ea26f00ded5083eb416ef51aac289969;CI17/21 PR39587678354/push39587669328
  aprovados.CI de main39588232964 aprovado em Java17/21.
- Proximo incremento em feat/qualify-no-effect-revision-recovery:14checks
  metadata-only confirmam que journal-only reanchor deixa roots antigas,alterar
  request.save quebra intent/receipt e copiar payload para ancora nova e CORRUPT.
  Ver NO-EFFECT-REVISION-RECOVERY-PROTOCOL.md antes de tentar migracao real.
  Nao ha transacao ou novo caminho de write;build completo e SDK isolado aprovados
  (ver documento para logs/fixture).Aguardar PR/CI/integracao deste incremento.
- PR73 integrada pelo head7f0ff5010f9638f242ac72f109aca52a39ed650d,
  main40e1ba29ab695a35e7bfdda04c7a0df1694d6ca9;CI17/21 push39588927676/
  PR39588968915 e main39589542987 aprovados.
- Incremento em feat/version-immutable-request-revision-envelopes:codec OFFLINE
  format1byte-identico/format2origens+chain NAO VERIFICADA,sem conectar store real.
  58checks envelope/14constraints/32multi-root/89durable/46coordinator aprovados;
  build completo/SDK em andamento.Ver IMMUTABLE-REQUEST-REVISION-ENVELOPE.md.

## Evidencias reutilizaveis

- Temp/opencode/surface-inventory-a2333b4910b54c2e8c2e8ecd0136d57a/*.inventory.txt;
- Temp/opencode/ledger-profile-original-04835af8fe874bee99a916b53d162339/runtime-test.log;
- Temp/opencode/ledger-membership-original-f283031b9c7f4f8a891a043ec3fc760c/bounded-runtime-test.log;
- Temp/opencode/ledger-membership-reviewed-e53af669d23145a994ff147648c24c53;
- Temp/opencode/serialization-gap-review-51afffcc066147f2bda1ddd6d602eca5/runtime-test.log;
- build/serialization-gap-original-io.log:159I/O/schema ORIGINAIS aprovados,
 fixture Temp/opencode/vbrasfoot-hook-io-260faa5d83e64cca9a22aadf4418d77b;
- build/serialization-gap-sdk-test.log:SDK isolado/hashes/allowlist/authoring
 negativos aprovados,fixture Temp/opencode/vbrasfoot-sdk-author-25afb8dec3714e31a8cd894236499174.

## Manifesto integrado (PR69)

Foi implementado o manifest versionado de qualificacoes NOVAS nas observacoes:
1. Definir facts separados para membership BEFORE/AFTER e perfil AFTER do
   serializer. Registrar UNAVAILABLE/ausencia e instante/escopo, nao so positivo.
2. Obter membership no MESMO sampler/EDT sem nova varredura duplicada;obter perfil
   do argumento REAL do hook, sem reconstruir a partir de hash ou nome.
3. Evoluir formato persistente explicitamente (ler1/2/novo,manter antigo sem
   migracao em leitura). Observacoes antigas preservam facts AUSENTES.
4. Testar mistura de histories,corrupt version/route/status,capacity,revogacao,
   falha antes/depois de HEAD e reopen. Nunca copiar fatos live para UUID antigo.
5. Facts integrados no report de gaps sem remover exclusividade/tabela de refs/
   multi-root unproven. Fixture original privada+PR+CI17/21+main,seguir proximo.

Evidencia adicional:Temp/opencode/qualification-manifest-reviewed-ec40413436304065af095f50a768e8f9/runtime-test.log.
Wrappers adicionais aprovados:159I/O/schema ORIGINAIS em
build/qualification-manifest-original-io.log,fixture
Temp/opencode/vbrasfoot-hook-io-33b52076d5f74964bc76cc3e0381b34a;
SDK ZIP isolado/hashes/allowlist/authoring negativos em
build/qualification-manifest-sdk-test.log,fixture
Temp/opencode/vbrasfoot-sdk-author-bde9550bf4244312992163cf8abb7184.
Nao sao power-loss ou aceite Main/UI/standalone.

## Referencias implementadas (aguardar integracao/CI)

Qualificar a semantica da tabela de referencias do Kryo para o ledger:
1. Inspecionar a rota original writeObject/referenceOrNull para distinguir
   conteudo serializado de referencia preexistente,sem presumir pelos15argumentos.
2. Correlacionar a aresta nx observada ao mesmo objeto/registro da tabela e ao
   stream exato;IDs runtime nao viram persistent club identity entre encarnacoes.
3. Testar alias fora de P(),referencia preexistente,reset/autoReset,dedup e falha
   antes/depois da emissao,em dados proprios e copia privada;sem alterar bytes.
4. Se nao houver prova suficiente,registrar status ambiguo/indisponivel,manter
   gap e seguir tarefa independente de recuperacao/read-only.Apenas fatos NOVOS
   podem entrar em formato versionado,nunca reanotar schema1/2ou manifest0.
5. PR/CI/main e checkpoint,seguindo proximo objetivo viavel sem novo continue.

Proximo incremento apos integrar a trilha:versionar persistencia de novos resumos
de referencia por UUID/revisao,ausencia explicita em histories antigas,validacao
de contradicoes,interrupcoes/reopen e mistura. Nao remover gap de ownership ou
inclusao causal apenas por persistir observacoes live.

## Objetivos seguintes e dependencias

- Qualificar tabela de referencias do Kryo/aliases fora de P(),sem transformar
  UNIQUE_IN_CLUB_LIST em ownership global. MemRegion e UnsafeInput ainda nao
  universalmente excluidos;perfil AFTER nao comprova estado durante a chamada.
- Estabelecer fronteira de execucao justificavel para mecanismos restantes de
  mutacao (Field.set/Unsafe externos comprovadamente escapam). JVM arbitrary Java
  nao tem sandbox atual:nao emitir selo de exclusividade inventado.
- Somente apos requisitos suficientes,decisao conservadora de inclusao por UUID,
  recuperacao/revisoes/restore/copia/save-as/coordenacao multi-root.
- UI refresh/EDT/lifetime e roteiro especifico de reabertura EXE original privado;
  quando necessitar humano,preparar procedimento causal e seguir tarefa independente.
- Calendario dia/partida/temporada:order/dedup/recovery/revogacao sem progress auto.
- Negociacao:offer identity/lifetime/precommit/counteroffers/cancel/expiry/commit.
- Storage global quotas/catalogo/retention/migrations;SDK permissoes/documentacao/
  autoria externa. Nao gerar mods solicitados para depois.

## Guardas e politica de continuidade

Cada objetivo:branch,testes/evidencia,PR,CI17/21,integracao SHA exato,main CI;
seguir objetivo viavel sem pedir outro continue. Se bloqueado,registrar motivo e
seguir tarefa independente. Parar apenas para autorizacao necessaria,ambiguidade
de dados,limite de servico ou ausencia de proximo passo seguro. Nao declarar todo
o plano concluido ao finalizar um PR.

operationInclusionProven=false/writeAuthority=false,APPLIED/UNKNOWN sem prova
bloqueados;old keys nunca recebem lease nova. Installed loader0.9.3/APImajor1 e
comparator visual inalterados;sem release/tag/licensing bypass. Cópias privadas
de evidencia contêm autoridades ARTIFICIAIS,nao aceite de Main/UI/standalone.
Nao encerrar processos pessoais nem criar carreiras/jogar partidas em artefatos
originais; testes runtime aprovados usam somente copias descartaveis isoladas.

Nao ha troca automatica de conexao configurada. Trocar credencial/conta/custo exige
selecionar conexao autorizada;atingir limite pode impedir qualquer acao posterior.
Nao prometer continuidade independente do host. Retomar deste checkpoint.

## Investigacao de elenco (21/10/2026)

- Ver `roadmap/PLAYER-ROSTER-MUTATION-REVIEW.md`: regra original
  `best.l.a(best.F,best.ah)` nao recebe lista de candidatos, mas seu sucesso
  altera elenco, associacao e historico e cria rastreio de emprestimo. Uma
  referencia fora da lista nao foi validada em runtime; nao publicar escrita.
- Mapa estatico adicional: `best.f.av` (rastreador), `best.f.au` (historico),
  listas de elenco `best.ah.nd/ne` e campos de `components.t` sao nao-transient;
  `best.F.eo` e `best.ap.U` sao transient. O clube atual do jogador e reancorado
  por `bW`/`SR.x` sob demanda. O pedido grava valor zero e nao percorre o ramo
  direto de alteracao de caixa; salarios futuros nao foram avaliados. O retorno
  automatico usa limite 35 para clube humano; UI do pedido ainda traduz erro 6
  como limite 30. A semente/positivo, o limite 30-35 e a rotina de retorno foram
  qualificados com diffs e replay isolado em memoria; veja resultados abaixo.
- Corrigida a busca anterior incompleta: `a.ds.pL()` passa `true` a `a.cz` no
  fluxo da Academia de Juniores, separado do emprestimo profissional.
- Validacao runtime autorizada somente em
  `%LOCALAPPDATA%/Temp/opencode/brasfoot-loan-fixture/`, em tres copias do EXE
  com SHA-256 esperado `2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.
  A semente `LOAN_FIXTURE.s22` tem 4.153 clubes/96.203 jogadores, round-trip Kryo
  byte-identico e SHA-256
  `232c5f2fe07dd28fb538d103716668822c8314417ba0f7051f0f459a7439289e`.
- `loan-positive`: pela UI, Diego Esser foi emprestado do Internacional ao
  Athletico; elenco 33/35 -> 34/35, data mantida em 23/01/2022. Salvamento pela
  UI, encerramento normal e reabertura confirmaram Diego no Athletico e 34/35.
  `.s22`/`.sbck` identicos, SHA-256
  `E192B872A8E448777179F9C546EF1D35BA05E50994AD1A3E408A576BED2B963C`.
- `loan-refusal`: Ben White (Arsenal) tambem foi aceito pelo Athletico. Nao houve
  salvamento e o arquivo em disco permaneceu no hash-semente.
- `loan-refusal-low-division`: carreira descartavel com Amazonas FC (3a divisao),
  tecnico `Teste`; torneio amistoso recusado. Primeira tela: 05/01/2022, elenco
  39/35 e proximo jogo apenas listado. Nenhuma partida foi aberta ou data avancada.
  Pedido por Santiago Beltran (River Plate, indicador E ativo) recebeu da UI
  “Limite de 30 jogadores alcancado”. Isso confirma recusa por limite de elenco,
  nao por divisao/reputacao. A carreira nao foi salva; copia fechada normalmente
  e `.s22` preservado no hash-semente.
- SaveCheck offline da semente e do positivo, com homes isolados em
  `savecheck-diff/`: ambos 4.153 clubes/96.203 jogadores, entrada intacta e
  round-trip Kryo byte-identico. Diff dos snapshots: unica mudanca de associacao
  Diego Esser (id 3972), Internacional -> Athletico Paranaense; destino 33 -> 34,
  origem 32 -> 31, sem outras mudancas de membership.
- Diff read-only do grafo: tracker 0 -> 1 para Diego, clube anterior Internacional,
  prazo bruto `1674563978893`, `expired=false`; historico `best.ap` 0 -> 1. O
  ponteiro transient `U` do historico fica nulo apos leitura; inteiros novos
  `d=23,ei=3972,ej=1,on=1,oo=130,op=280,oq=0,y=2022` nao foram semanticamente
  decodificados. Saldo `kb()` inalterado em todos os clubes, incluindo Athletico
  Paranaense `11587932` e Internacional `15000000`; nao cobre efeitos futuros.
- `limit-qualification`: codigo original executado em harness privado, sem abrir
  a UI; cada rodada recarregou a semente e ajustou apenas listas em memoria.
  Elencos 30, 31, 32, 33 e 34 retornaram 1 e moveram Diego; em 35 retornou 6
  sem efeitos. Em 34 -> 35 houve aceite; em 35 houve recusa. Portanto o limite
  funcional e 35; “Limite de 30” e a mensagem associada pela UI ao retorno 6,
  observada anteriormente com elenco 39. Nao reexecutamos a UI em 34/35.
- Elegibilidade: id 7090 disponivel, clube de origem Sevilla (reputacao 4), foi
  testado contra Ferroviaria (23 jogadores, reputacao 1, divisao 3, outro pais).
  A regra de elegibilidade retornou falso e o pedido de negocio retornou 4,
  sem mudar elenco/tracker/historico. Controles apenas em memoria, no mesmo clube,
  retornaram verdadeiro elevando reputacao a 4 ou divisao a 1. E evidencia para
  essa combinacao, nao qualificacao universal da regra/UI.
- Retorno/expiracao: sobre copia do save positivo, o vencimento foi alterado
  somente em memoria para `clock-1`; `best.f.au()` executou a rotina original sem
  avancar `best.f.bc()`. Diego voltou do Athletico ao Internacional, elencos
  34 -> 33 e 31 -> 32, tracker 1 -> 0, historico 1 -> 2; saldos imediatos iguais.
  A entrada `.s22` ficou intacta. Isso qualifica a transicao quando o clube
  anterior nao e humano, nao a agenda/frequencia normal nem o retorno para clube
  humano cheio (que permanece sustentado por analise estatica).
- Capturas runtime estao nas respectivas pastas de caso sob o diretorio temporario.
  Nenhum save pessoal/EXE original foi aberto ou alterado; nenhum mod ou escrita
  publica do SDK foi criado. A investigacao desta etapa fecha sucesso/persistencia,
  limite funcional, um motivo de elegibilidade e a rotina de retorno controlado.
  Nao fecha salarios/folha futuros, todas as recusas, refresh/EDT/notificacoes,
  disparo normal por calendario, nem retorno ao clube humano com elenco 35.
  Portanto nao propor escrita publica ainda.

## Retomada do mod de emprestimos

- Escolhas confirmadas pelo usuario: avaliacao automatica por regra nova do mod,
  prazo original366dias. Ver detalhes/limites em PLAYER-ROSTER-MUTATION-REVIEW.md.
- Fontes de desenvolvimento SDK0.9.4 adicionam `PlayerRef` somente temporario,
  `scopedPlayersWithReferences` e capacidade read-only `query.player.references`;
  game.read obrigatorio e facade revogada no encerramento. APImajor1 mantida;
  loader instalado0.9.3 e STATUS.md inalterados. Nao e ownership duravel nem write.
- Execucao completa anterior de test.ps1 passou; repeticao interrompida por
  reinicio do host saiu4 sem assertion reportada. Depois passaram diretamente
  22QueryScopes/76ModContextLifecycle/37SdkValidatorJson e async/calendar/player.
  Nova execucao completa iniciada na retomada; nao assumir conclusao sem resultado.
- Harness privado humano repetido na fixture `human-return-2345e4339cca48bf99ea44f4c0b30637`:
  tM exige prazo<clock; tN recusa duas vezes origem humana35 e aceita apos34->35;
  historia+1/saldos/clock preservados. Origem humana artificial em memoria;
  nao executou au/notificacoes/save/reopen/agenda. Hashes input/EXE preservados.
- Inspecao de ax confirma que NAO e getter/processador isolado de retorno: chama
  az antes de au e outras rotinas de progressao. Nao executar com as guardas atuais.
- Regra pura `LoanClubAssessment` destination-median/1 adicionada,14checks isolados
  aprovados e test incluido em build.ps1:35+ recusa; vazio insuficiente; demais
  candidato<=mediana destino recomenda aceite. Nao substitui elegibilidade nativa,
  nao cria fila/adapter/mod instalavel nem altera manifesto de write.
- Incidente de `$home` PowerShell reservado: gerados input/cache sob perfil do
  usuario foram isolados em `misplaced-test-artifacts-1c511f8df6c9496eb2b850d4c49de13a`
  na fixture, com identificacao por hash/nomes/timestamps, sem exclusao ampla.
- Proximo: qualificar au humano/notificacoes sem avancar clock, serializacao e
  precondicoes/ownership dos elencos; projetar contrato de pedido e recovery antes
  de liberar escrita. operationInclusionProven=false/writeAuthority=false continuam.

### Retomada apos cancelamento do host

- Test.ps1 da rodada anterior cancelado explicitamente por restart do servidor;
  nao registrar como falha de assertion nem como suite completa aprovada.
- `human-processor-codec-cab22eca786249f58e9c019d3e45ec95`: au original na EDT,
  origem humana artificial35 bloqueia/avisa raw28/82 uma vez; com vaga34 retorna
  a35, remove tracker e avisa29/83; passagem posterior sem duplicata. Codec
  OFFLINE preserva clock/historico/elencos/tracker ausente e jogador unico no
  clube anterior. Input/EXE intactos; nao e writer/UI/agenda/ownership qualificados.
- `LoanRequestQueue` interna de sessao adicionada: pendente/avaliacao explicita,
  estados terminais, idempotencia por key/payload, cap32 sem eviction, cancel/
  invalidation/close e snapshots imutaveis.21checks proprios +14policy aprovados.
  Nao e fila duravel nem execucao; nao conecta Main/ModContext ou muda permissoes.
- Nova suite completa test.ps1 iniciada em background apos fontes/testes finais;
  aguardar resultado real, sem assumir sucesso. Detalhes das fixtures/limites em
  PLAYER-ROSTER-MUTATION-REVIEW.md. Escrita publica permanece desativada.

### Contrato duravel e adapter de emprestimos

- Ver LOAN-WRITE-PIPELINE.md: LoanRequest codec1 (locators/revisao/elencos/clock,
  sem persistir handles), LoanRequestStore root dedicado e executeStoredLoan.
  LOAN_ACCEPT interno; sem novas permissoes/capabilities ou Main/ModContext.
- LegacyLoanAdapter fingerprint5classes, handles de consulta via bridge interno,
  destino humano, revalidacao/aliases kc+ky, snapshot de ambos os elencos, clock,
  native once e pos-checktracker/historico/saldos/prazo366. Nao e ownership global.
-35checks codec/store/despacho aprovados;29dispatcher novamente aprovado com
  fontes finais.14policy/21fila/32gate/28journal/46finance-request-store tambem
  aprovados isoladamente durante esta rodada.
- Fixture adapter positivo/stale/unavailable/alias aprovadas. Diego original41
  recusado pela policy; controle explicito SOMENTE em memoria para forca20
  possibilitou testar efeito nativo. Nunca afrouxar policy para obter sucesso.
  Positivo/native1/codec/seed restaurada sem replay; stale e alias preflight-1;
  unavailable native5. Hash seed/exe intactos; contextos de gate ARTIFICIAIS.
- Suite completa anterior sh_15f8e9b0e001OW687cvPsLE5CL terminou aprovada incluindo
  validate-only sem iniciar jogo (antes deste novo adapter/store/dispatcher).
  Nova suite completa em background para incremento atual; resultado pendente.
- Nao considerar objetivo mod completo: integracao do inventario/recovery de
  emprestimos no coordenador, workflow de decisoes duraveis, writer/UI/reopen e
  API publica permissionada/painel/mod permanecem sem qualificacao/implementacao.

- Resultado posterior: test.ps1 sh_15f9989fe001D8dmJbpmsoPCOP concluiu aprovado,
  incluindo build, regressoes e validate-only sem iniciar jogo. Essa execucao
  registrou31checks do LoanRequestStoreSelfTest; os4checks finais de policy/contexto
  e a guarda adicional do dispatcher foram compilados e executados separadamente:
  35loan-store/29dispatcher aprovados. Nao atribuir35checks ao log da suite31.
  Symlink/fileKey indisponiveis permaneceram SKIP, nao foram requalificados.

### Integracao de decisoes/recovery/painel (sem promocao de escrita)

- Ver LOAN-MANAGER.md. LoanRequestStore schema2 persiste estados/cancelamentos,
  claim EXECUTING antes de efeito, lease/completion one-shot; schema1 lido sem
  rewrite/sem inventar decisoes antigas. Pending->avaliacao automatica no submit
  do coordenador; execucao interrompida nao recebe lease apos reopen.
- Coordenador Main inventaria loan-requests junto das roots anteriores, inclui
  owners ausentes/desabilitados e revalida HEADs/associacao/decoder antes de
  mutacao. LoanContractReview correlaciona estados/receipts, preserva UNKNOWN e
  APPLIED_SAVE_UNPROVEN. Nenhuma reancoragem ou recuperacao causal inventada.
- Painel loader-owned em modo probe: filtra clubes, consulta jogador por handle,
  registra/avalia/cancela/revisa metadados. Botao Transferir desativado sem callback.
  Nao e mod externo instalavel nem API publica de escrita. Owner reservado
  br.vbrasfoot.loader.loans; conflito selecionado desativa painel.
-17decisions/43store/54coordinator/111context/29dispatcher aprovados isoladamente.
  Smoke Swing proprio aprovado; screenshot na fixture loan-ui-smoke-10389200899061316223.
- Writer original e loader original na fixture original-loan-writer-6287b0e201114d0da237075ca90e4cbc
  preservaram loan/tracker/anchors/deadline/clock, output+backup identicos e info.
  Hash outputb4aa3f1d61f47d888f8805e6ba09ff8cfd94d7ded803d2fc33b59f4c1c5a0d69.
  Input/exeintactos; forca20 artificial, frame sintetico e autoridadeARTIFICIAL;
  nao e Main/UI completa nem prova causal de inclusao.
- Nova test.ps1 com fontes atuais em background; aguardar notificacao real.
  Public write/API/instalacao continuam desativados ate qualificacao restante.

### Teste solicitado na UI original: aceite parcial, bug de refresh

- Ver LOAN-FULL-UI-VALIDATION.md. Fixture full-ui-ec31f10533e74d54bce762c31c548539:
  entrypoint original/carga pelo menu, carreira real23/01/2022,33jogadores.
  Harness de componentes Swing somente na propria JVM; gate/owner ARTIFICIAIS.
- Marios Balamotis5065/AEKAtenas/forca24 disponivel passou policy e regra nativa
  sem alteracao artificial. Adapter/store/journal retornaram EXECUTED/native1,
  tracker/historico1,elenco34,prazo366,clock1642941578893inalterado.
- BUG: tabela incorporou linha, mas contador ficou33/35 e sorter avisou row-count
  mismatch. Selecionar jogador mostrou prazo23/01/2023; isso nao corrige refresh.
- Salvar pela UI gerou s22/sbck identicos hash3f123745d1b097e6c2590f1c8a5b65cd676afe069592d8283e985faffcaced79.
  Saida normal/menu; novaJVM+carga/menu preservou jogador/anchors/tracker/historico/
  clock e contador agora34/35. Nova saida normal, nenhum harness da fixture vivo.
- Preparacao falhou antes por info ausente/exportSunGraphics; corrigidos sem
  mudar seed. Documentado, nao contar tentativas falhas como load qualificado.
- Seed/exeintactos; sem partidas/data avancada/saves pessoais. Nao houve nova
  alteracao no codigo de producao nesta rodada, apenas harness privado e docs.
- Proximo: corrigir/qualificar refresh seguro de contador/model/sorter, testar
  fluxo do coordenador e API publica antes de ativar transferencia no painel.
  Teste nao transforma artificial gate em autoridade publica nem prova causal.

### Correcao do refresh solicitado

- LoanRosterRefresh + chamada pelo LegacyLoanAdapter: captura selecao antes de
  mutacao, evento do model antes de acessar novos indices view, restaura identidade
  ou fallback, contador pKoriginal delimitado. Somente EDT/tela dos clubes exatos/
  modelo e classloader originais. Fingerprints a.eg/b.Q adicionados. Status visual
  separado de APPLIED/UNKNOWN, sem replay/rollback automatico.
-7checks sinteticos aprovados, incluidos no build; sem init/save/calendar.
-UI original em fixture NOVA refresh-ui-e43f7b0b5d95431897327373aa973671:
  Marios5065/AEK->Athletico sem alterar atributos,EXECUTED/native1/refreshUPDATED.
  Contador34/35 imediato, selecao anterior preservada, novo jogador selecionavel/
  card/prazo corretos. Sem warning do sorter no log. Clock/data intactos.
-SaveUI/sbck mesmos bytes do caso anterior (3f123745d1b097e6c2590f1c8a5b65cd676afe069592d8283e985faffcaced79),
  EXEintacto, saida normal/menu. Detalhes/capturas em LOAN-FULL-UI-VALIDATION.md.
-Regressao completa test.ps1 iniciada em background; aguardar resultado. Botao
  publico segue desativado: refresh corrigido nao equivale a integracao do gate
  artificial com Main/API permissionada/recovery causal concluida.
