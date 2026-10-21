# Checkpoint de execucao continua — 21/10/2026

Nao substitui roadmap/STATUS.md (mantido inalterado por instrucao do usuario).
Checkpoint para retomar sem reexecutar evidencias ou depender de credenciais no
contexto. Nao instalar builds/mods nem tocar saves/EXE pessoais.

## Entregas deste lote

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
  aprovados.CI de main39587322612 em andamento;nao presumir seu resultado.
- Proximo incremento em feat/review-financial-multi-root-recovery (baseado no
  main277a3f6 apos integracao exata da PR71):revisao pura das3roots de metadados,
  nao transacao nem as2roots Kryo.32checks proprios/46coordinator/43facts/
  89durable/23gaps/160I/O originais e SDK isolado aprovados. Ver
  MULTI-ROOT-RECOVERY-REVIEW.md.Nao liberar reancoragem ou inferir inclusao.

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
Nao encerrar processos pessoais/criar carreira/jogar partidas/rodar init como getter.

Nao ha troca automatica de conexao configurada. Trocar credencial/conta/custo exige
selecionar conexao autorizada;atingir limite pode impedir qualquer acao posterior.
Nao prometer continuidade independente do host. Retomar deste checkpoint.
