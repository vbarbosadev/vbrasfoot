# Validacao — 16/10/2026

## Emissao numerica efetiva do serializer — 20/10/2026

FinanceFieldTransformer fingerprinta as seis variantes int/long CachedField
Unsafe/ASM/Object do Kryo4.0.2 do EXE fornecido. Somente probe opt-in Java17/21,
body-only em write, inventario exato de uma chamada Output fixa e uma varint,
sem read/copy/schema/fallback. Callback apos retorno recebe o argumento EXATO,
sem reler saldo/ledger; preserva flag/Output/retorno/falha originais.

HookProbe exige root em andamento, mesmo classloader e mesmo SaveOutputTrace via
Output.getOutputStream. Plano compara Field/receiver por classe e IDENTIDADE com
weak club/ledger originais; compara argumento a expected por UUID fresco do
journal. Tracker bounded nao retém argumentos/Field/Output/alvos fortes: guarda
status/count/nomes de campo/rotas. Missing/partial/mismatch/duplicata/falha nao viram
completude. Captura/exportacao nao mudam gate/interlock, receipts/witnesses,
contratos/revisoes ou writeAuthority=false/operationInclusionProven=false.

Evidencia final isolada em
`Temp/opencode/finance-numeric-original-2cb87a76bd674d4b846e657a5b320046`:
-12checks sinteticos numeric emission com classes geradas PROPRIAS;
-24checks root capture e40writer/report, incluindo MATCHED-only sem emissao;
-104checks financeiros ORIGINAIS em `final-runtime-test.log`:6recusas de
 fingerprint adulterado e24casos das6variantes com bytes fixos/varint exatos e
 falha de overflow preservada;15argumentos esperados na ultima operacao, e
 VALUE_MISMATCH nas duas anteriores. Rota REAL observada: UnsafeIntField e
 UnsafeLongField. Save/reload originais e bloqueio de historico mantidos.
Fixture final copiada de OUTRA copia privada, input/hash/EXE conferidos, sem
escrever em instalacao/save pessoal ou executar progresso/criacao de carreira.
O wrapper completo anterior passou74checks originais com primeira versao do
incremento; nao confundir com104da fixture final ampliada.69witness/29dispatcher
tambem passaram em compilacao isolada deste incremento.

Limites: argumento aceito em Output pode estar somente em buffer. Nao captura
ranges/bytes individuais nem codificacao da aresta clube.nx/ledger. Observar a
leitura Unsafe NAO cobre suas escritas/reflexao/decoder/copy/Java arbitrario nem
prova causalidade da operacao. Captura e em memoria, sem commit crash-safe por
UUID. APPLIED/UNKNOWN sem prova continuam bloqueados; nenhuma lease/reanchor/
permissao publica nova. Persistencia/recovery/restore/save-as/UI/standalone/
calendario/negociacao seguem pendentes. Sem mod novo, instalacao, release/tag ou
novo teste humano. `roadmap/STATUS.md` inalterado.
Contrato/limites: `roadmap/FINANCIAL-NUMERIC-EMISSION.md`.

## Alvos financeiros por operacao nas fronteiras do serializer — 20/10/2026

No caminho executeWitnessedFinance, dispatcher entrega ExecutionPermit fresco ao
plano somente apos APPLIED observado com roots iguais e receipt APPLIED no gate.
Plano valida request/permit exatos e registra target FRACO com UUID/intent e
checkpoint. Tracker nao retem grafo/runtime; target coletado e unavailable,
sem rediscovery por saldo/nome/ID. Registro e diagnostico ANTES de gravar evidencia
terminal/receipt: nao comprova que essas gravacoes tiveram sucesso ou que nao
ocorreu timeout posterior. Nao e autoridade nem testemunho de inclusao duravel.

FinanceSerializationTracker limita32operacoes e marca capacidade/duplicatas
explicitamente. HookProbe abre janela antes da primeira root real do Kryo e
fecha depois da segunda. Na EDT, amostra root/roots correntes/clube/indice/
membership/nome/humano/ledger por IDENTIDADE e snapshot financeiro completo.
Mudancas sao ROOT_MISMATCH/LEDGER_MISMATCH/STATE_MISMATCH; off-EDT e WRONG_THREAD,
getter falho e READ_FAILED. Operacao/UUID/intent preservados, nunca inventados.
Checkpoints distinguem interferencia direta, cobertura ausente ou nenhuma escrita
direta observada. Load/reset/root/detach/registro novo invalidam janelas antigas.

WrittenSaveProvenance guarda captura imutavel com os bytes/roots existentes
somente se sequencia do save e completa. ProbeSession possui/fecha subscription,
exporta UUIDs/status/count/capacity/continuity sem keys/payloads/valores/paths.
operationInclusionProven=false e writeAuthority=false sempre mantidos.

Evidencia final:
-24checks de captura e39de writer/report em saida isolada atual
 `Temp/opencode/operation-capture-reviewed-416c919eaae647f8b1b0e420d901fe80`:
 status/identidade/UUID/intent, uma janela nao pode ser reutilizada, roots trocadas,
 alteracao/invalidacao, EDT/read failure, interferencia/checkpoint ausente,
 capacidade/duplicata e exportacao sem key/valores/paths.
-69checks de witness e29de dispatcher preservados na mesma compilacao.
-71checks financeiros ORIGINAIS em fixture PRIVADA com fontes finais do adapter
 e pipeline, compiladas isoladamente em
 `Temp/opencode/operation-serializer-final-fd22c69be2c24d7fb826dbc6b532dfba/fixture/runtime-test.log`.
 Tres operacoes testemunhadas chegaram a captura com UUIDs exatos do journal;
 ultimas amostras matched, anteriores mismatch/interferencia apos outros efeitos;
 legado/rejeitado nao registrados. Writer/reload originais preservados. Refinamento
 posterior somente consome janela com root errado (teste sintetico final).
 Hashes de EXE/save de origem conferidos antes/depois, autoridades ARTIFICIAIS.
-SDK-only ZIP/hashes/allowlist/cinco projetos/negativos passou em
 `build/operation-capture-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-6a760e8fe59946809a362358f35a3e0a`.

Limite critico: amostrar clube/ledger antes/depois da root NAO prova quais campos
Kryo emitiu, serializer escolhido, campos transient/custom ou ausencia de Unsafe/
reflexao/decoder. Nao existe INCLUDED/lease/reanchor novo, captura e EM MEMORIA e
report de shutdown nao e protocolo crash-safe. APPLIED/UNKNOWN sem prova continuam
bloqueados. Faltam emissao de campos e coverage causal pertinente/persistencia da
inclusao/revisoes/restore/save-as, UI/standalone/calendario/mercado/API publica.
Sem mod, instalacao/EXE/save pessoal alterado, processo pessoal encerrado, tag/
release ou nova sessao humana. Contrato: `roadmap/OPERATION-SERIALIZER-CAPTURE.md`.

## Reconciliacao provada de UNKNOWN sem efeito — 20/10/2026

Prova opaca emitida somente de witness comprometido NO_EFFECT para UUID/intent/
escopo/revisao exatos permite acrescentar NoEffectResolution ao journal. Raw
receipt PREPARED/UNKNOWN e conservado; effectiveReceipts e visao derivada explicita.
Auditoria guarda estado original, UUID/payload e HEADtoken/context/generation da
prova. APPLIED/ARMED/UNKNOWN/MEMORY_APPLIED nao servem como prova de ausencia.
Nunca nova lease para key antiga; lease original resolvida nao pode executar/
completar. Inspecao conserva historico, nao inventa rollback/inclusao no save.

Schema/format2 do journal do LOADER le1e2; inspecao v1 nao grava/migra. Escrita
subsequente usa2com receipts/auditoria conservados. Loader antigo deve recusar
schema novo; nao ha downgrade implicito. Schema do jogo original inalterado.
Coordenador/painel oferecem confirmacao explicita por owner, com os tres inventarios
e origem revalidados. Outros efeitos/incertezas/conflitos/ancoras continuam
bloqueados; memoria UNKNOWN do gate real nao e apagada por auditoria no disco.

Evidencia final isolada:
 `Temp/opencode/proven-no-effect-final-d3e03c499e2a4b41a6e2eeadb12823c7`.
-63checks novos: raw/audit/effective, UUID/payload/escopo/revisao/token, prova
 antiga/invalida/duplicada/batch, lease/replay, contradicao APPLIED, schema1read-only,
 reabertura/reanchor, faults nos tres estagios, auditorias corrompidas recusadas
 e EDT recusada.
-111checks de contexto incluindo confirmacao auditada e painel (positivos
 sinteticos, nao clearance da memoria incerta de uma execucao real).
-Regressoes anteriores isoladas:28journal,29dispatcher,27reanchor sem efeito.
-65checks financeiros ORIGINAIS em `build/proven-no-effect-original-finance.log`:
 plano persistente obsoleto recusado sem mutacao, receipt terminal interrompido,
 witness NO_EFFECT comprometido, reconciliacao com raw UNKNOWN preservado e
 lease antiga nunca reemitida. Autoridades da fixture sao ARTIFICIAIS.
-159checks originais I/O/schema em `build/proven-no-effect-original-io.log`.
 Wrappers rodaram51checks iniciais de reconciliacao;12extras passaram depois na
 saida isolada final63total. Nao confundir suite inicial com final.
-SDK-only ZIP/hashes/allowlist/cinco projetos/negativos passou em
 `build/proven-no-effect-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-9ce7dd2cd8224781bea9a3b194efb55a`.

Ainda pendem inclusao causal APPLIED/UNKNOWN no save, protocolo entre revisoes,
UI/standalone, calendario/mercado/API publica. Sem mod novo, instalacao/EXE/save
pessoal alterado, processo pessoal encerrado, release/tag ou nova sessao humana.
Contrato e limites: `roadmap/PROVEN-NO-EFFECT-RECONCILIATION.md`.

## Observacao de mutacoes financeiras e intervalos do serializer — 20/10/2026

FinanceMutationTransformer e opt-in no runtime instrumentado, body-only e
fingerprintado para best.ah/best.C. Brackets sem argumentos cercam42PUTFIELD
escolhidos:10nb/4nx em ah e28sites numericos em C. Nao muda receptor/valor/schema,
retorno/Throwable ou arquivo original; callback nao faz I/O/wait/handler de mod.
Falha no PUTFIELD nao completa bracket: outstanding permanece e recusa estabilidade.

Inventario independente do EXE conhecido e quatro JARs internos confirmou
1317classes/406713instrucoes/0erros e0sites externos para os16campos private
escolhidos. dK tem0PUTFIELD, nao prova imutabilidade. Evidencia PRIVADA:
 `Temp/opencode/finance-putfield-static-f99d90e0a7ee49cf86aef301766dd61c/FINDINGS.md`.
Fonte scanner/TSVs/identidades/javap preservados la. Analise nao executou jogo.

Tracker bounded/global compara identidade/revision monotona, contadores ativos
por thread/health e ambas classes definidas no MESMO runtime. Mudanca de root/
load/reset/detach invalida checkpoint; equal-value write tambem muda revision.
Global e conservador: write em outro clube tambem invalida. Active0 nao prova
quiescencia de toda operacao composta ou bloqueia concorrencia do gameplay.

Plano do adapter captura checkpoint APPLIED e HookProbe captura antes da primeira
root e depois da segunda root do writer. WrittenSaveProvenance conserva esses
checkpoints em memoria sem alterar criterio de byte provenance. Avaliacao separa
executionToSerializer e serializerWindow em UNAVAILABLE/OBSERVED_INTERFERENCE/
NO_OBSERVED_DIRECT_WRITE. Report exporta contadores/revisions sem paths/objetos;
operationInclusionProven=false e writeAuthority=false permanecem explicitos.

Evidencia sintetica final em
 `Temp/opencode/financial-mutation-final-fc53b31894af4fad912f09eaa125732e`:
-22checks de tracker/checkpoints/avaliacao e8de transformer: int/long/reference,
 schema preservado, write nao financeiro intocado, NPE original sem completion,
 cobertura/runtime/identidade, equal-value/nested/foreign/unmatched, invalidacao,
 checkpoints ausentes e interferencia no serializer.
-36checks de writer/stream/provenance/report anteriores passaram.
-SDK-only ZIP/hashes/allowlist/cinco projetos/negativos passou em
 `build/financial-mutation-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-d2cb7d2a4f1040c682cbcec3a95356dc`.

Primeira tentativa do wrapper parou na compilacao: teste usou CareerBridge.call
com argumento embora esse helper so aceite getter sem argumentos. Corrigido para
invocacao original por reflexao na fixture; tentativa falha nao conta como aceite.

LIMITE CRITICO:42sites diretos nao cobrem reflexao/Unsafe/Kryo/decoder/native/
codigo gerado/injetado/inicializacao VM. Ausencia de evento observado NAO e prova
de ausencia universal de mutacao ou inclusao de uma operacao no save. Checkpoints
NAO sao persistidos no witness e nao resolvem crash/reinicio ou UNKNOWN antigo.
Ainda faltam cobertura causal pertinente/captura especifica de club/ledger no
serializer, prova duravel de inclusao/revisoes/restore/save-as e aceite UI/
standalone/calendario/mercado/API publica. Sem grant/retry/migracao novo, mod,
instalacao/save/EXE pessoal alterado, release/tag ou nova sessao humana.
Detalhes: `roadmap/FINANCIAL-MUTATION-OBSERVATION.md`.

## Inventario de execucao no contexto operacional — 20/10/2026

Coordenador de Main inclui agora root dedicado finance-execution-witnesses na
uniao de owners configurados + diarios + contratos + registros de execucao.
Inclui mods desativados/nao selecionados e execution-only owners. Limite64 e
aplicado a uniao, sem omissao/resultado parcial. Construcao/read de ausencia nao
criam pastas; sem handle nao ha inventario ou inferencia de identidade.

Inspecao administrativa valida registros comprometidos e mostra ancora REAL
separada do save observado. Read/arm/complete ordinarios continuam exigindo ancora
exata; nenhuma migracao. Review conserva os TRES inventarios e revalida conjunto
de namespaces/HEADtokens no fim, antes de qualquer confirmacao e antes de instalar
contexto na EDT no MESMO gate/interlock. HEAD/namespace novos invalidam review.

Painel mostra owner/contagem/ancora antiga/key/UUID/status/falhas, sem botao de
executar/apagar/migrar. ARMED/UNKNOWN/MEMORY_APPLIED_SAVE_UNPROVEN/conflitos ou
ancora divergente bloqueiam contexto. NO_EFFECT de execucao nao apaga journal
UNKNOWN: guard anterior continua bloqueando. Compatibilidade de recibo legado
NO_EFFECT sem witness exige UUID/intent exatos, sem inventar registro ou lease.
Journal-only reanchor nao move witness antigo e contexto permanece bloqueado.

Evidencia em compilacao isolada atual:
 `Temp/opencode/execution-inventory-final-0339fbfdbe574f33af659f47b2cb0ceb`.
-107checks de contexto (39novos): todos estados, owner desativado execution-only,
 contradicao mesmo com journal NO_EFFECT, execucao NO_EFFECT com journal UNKNOWN,
 alteracao HEAD/namespace apos review, orphan/HEAD corrupto/schema/payload invalidos,
 ancora antiga apos journal-only reanchor, guard ordinario e limite da uniao.
 Positivos usam roots/markers init PROPRIOS, nao qualificacao original/global.
-43checks de coordenador e69de witness/recovery preservados.
-SDK-only ZIP/hashes/allowlist/cinco projetos/negativos passou em
 `build/execution-inventory-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-895ca313b20a4dfb8bf0b2f9a2943266`.

Fixture inicial criava instancia diferente de witness-store ao completar a lease;
guard corretamente recusou foreign/previous-process lease. Fixture foi corrigida
para manter mesma instancia, sem relaxar implementacao/contar falha como positivo.

Nao e snapshot atomico multi-root nem grant duradouro contra qualquer escritor
externo; novo preflight por operacao ainda necessario. Checksums/path checks nao
autenticam historico contra Java/FS hostis. Main so revisa/bloqueia: nao arma
witnesses nem cria handlers/owners publicos. Captura causal no serializer/prova
persistente de inclusao no save e protocolo de revisoes/restore/save-as seguem
pendentes. Sem novo mod/instalacao/EXE/save pessoal alterado, processo pessoal
encerrado, release/tag ou nova sessao humana. Detalhes:
 `roadmap/EXECUTION-WITNESS-INVENTORY.md`.

## Evidencia duravel de execucao financeira — 20/10/2026

Novo caminho interno executeWitnessedFinance liga contrato persistido, journal
PREPARED fresco, ExecutionPermit opaco e ARMED duravel antes de enfileirar efeito
na EDT. Depois grava MEMORY_APPLIED/NO_EFFECT_CONFIRMED/OUTCOME_UNKNOWN antes do
receipt final, mantendo retention do MESMO gate. ExecutionPermit so e emitido com
lease da instancia corrente e token/UUID/intent/estado PREPARED bruto revalidados;
snapshot/receipt historico ou lease reaberta nao bastam. Root de evidencias e
DEDICADO, com CAS/lock/checksums/previous/quotas, limite32 e nenhum eviction/reanchor.

Falha de ARMED nao enfileira efeito. Falha de evidencia terminal deixa journal
incerto e gate invalido. Se MEMORY_APPLIED foi comprometido mas receipt final
falhou, FinanceExecutionRecovery distingue MEMORY_APPLIED_SAVE_UNPROVEN do caso
sem resultado confiavel, conservando receipt UNKNOWN e key sem nova lease.
Timeout em execucao permanece UNKNOWN mesmo apos retorno tardio APPLIED. Roots
trocadas/falha no handler nao geram testemunho positivo de execucao. Nenhum desses
registros significa INCLUDED no save, migracao de revisao ou retry autorizado.

Evidencia final sintetica:
-69checks de evidencia duravel em
 `Temp/opencode/execution-witness-final-b6c5c299bb61483793e25ede5c7c79f4`:
 fases antes/depois de commit ARMED/terminal, journal final interrompido nos tres
 estagios, ordem causal payload->PREPARED->ARMED->efeito->evidencia, UUIDs exatos,
 journal UNKNOWN com observacao independente, timeout/retorno tardio/save fence,
 lease antiga/prova de outro career, schema/payload/EDT e conflitos de recovery.
 Nao e power-loss nem aceite de gameplay; efeitos/roots sao PROPRIOS.
-29checks do dispatcher existente passaram na mesma compilacao isolada.
-54checks financeiros ORIGINAIS com writer instrumentado passaram em
 `build/execution-witness-original-finance.log`: credito/debito possuem UUIDs
 exatos e MEMORY_APPLIED duravel, evidencias reabertas iguais apos writer/reload,
 ancora de output divergente recusada e restore do input na fixture com gate
 novo nao reemite lease. Autoridades da fixture sao ARTIFICIAIS. Wrapper rodou
 build/suite com64checks iniciais de witness;5checks adicionais de escopo/schema/
 payload/EDT/conflito passaram depois em compilacao isolada (69total). Nao
 confundir64com suite final. Nenhum hash de origem alterado pelo wrapper.
-SDK-only ZIP/hashes/allowlist/cinco projetos/negativos passou em
 `build/execution-witness-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-9dde6548b60e4011b87ddae3ffe3518b`.

Main ainda nao cria witnesses/owners/handlers de escrita para mods. Integracao de
inventario/review dessas evidencias, captura causal na serializacao e cobertura
das mutacoes financeiras pertinentes continuam pendentes antes de reconciliar
APPLIED/UNKNOWN quanto ao SAVE. Historicos sem prova nao recebem evidencia
retroativa. Nao editar schema original ou comparar apenas saldo/ledger para
fingir esse elo. Sem transacao multi-root, exactly-once ou seguranca contra Java/
FS hostis. Categorias/UI/standalone, calendario/mercado e API publica sem aceite.

Procedimento manual financeiro futuro foi delimitado em
 `roadmap/FINANCE-EXECUTION-WITNESS.md`; NAO e instrucao para executar pagamento
agora. Nenhum passo financeiro humano solicitado antes dos gates de UI/inclusao.
Sem novo mod, instalacao/EXE/save pessoal alterado, processo pessoal encerrado,
release/tag ou nova sessao humana. Autorizacao de teste restrita a copias isoladas.

## Stream real do save e avaliacao por operacao — 20/10/2026

CareerTransformer fingerprintado agora confere e instrumenta exatamente dois
FileOutputStream(String), um Output(OutputStream) e dois writes de roots do writer
original. Captura destino real, referencias fracas dos objetos serializados e
hash/size dos bytes entregues com sucesso ao stream subjacente. Nao troca bytes,
objetos/schema/destino, adiciona flush/close ou retenta falhas originais.

SaveOutputTrace recusa evidencia em falhas de write/flush/close, acesso de outro
thread, uso apos close/finalize, stream vazio/nao fechado ou excesso de tamanho.
WrittenSaveProvenance exige ticket ativo inalterado, exatamente duas roots com
retorno e sequencia metadata/primary/underlying-close/backup/return completa.
Load/reset/root assignment/novo save/detach invalidam evidencia corrente;
completion antigo nao a restaura. WrittenSaveVerifier cruza path REAL exato,
hash/size do arquivo observado e roots/ticket atuais na EDT com timeout. Hashing
fica fora da EDT; backup/copia byte-identicos nao selecionam outro path.

Report written-save-provenance.properties captura snapshot ANTES do detach, sem
paths/objetos/nomes de clubes; operationInclusionProven=false e writeAuthority=false.
Nao e testemunho duravel de comando, commit atomico save+sidecars ou garantia de
power-loss. Reload invalida binding corrente, nao restaura authority antiga.

FinanceSaveAssessment separa por key/UUID real do receipt a correlacao dos bytes
escritos/recarregados e a inclusao do efeito. Mesmo output/decoder identicos,
APPLIED/UNKNOWN permanecem UNPROVEN. NO_EFFECT de outro payload e CONFLICT; pedido
sem receipt nao ganha UUID. Nenhum estado INCLUDED existe neste avaliador e
nenhum journal/contrato/metadata e migrado, completado ou liberado para replay.

Evidencia:
-36checks de stream/provenance/verificador/report e18de avaliacao por operacao passaram
 em compilacao isolada atual em
 `Temp/opencode/save-assessment-final-e8cc4929971842a9bdaeda8f91b9d8dd`.
 Positivos de hooks/roots nesses testes sao SINTETICOS.
 Os4checks finais de report conferem captura antes de detach, ausencia de paths
 exportados e flags de inclusao/autoridade sempre falsas.
-159checks I/O/schema ORIGINAIS em `build/written-save-original-validation.log`,
 em fixture privada: hash/size/path/roots do output primario reais, backup nao
 selecionado implicitamente, reload dos mesmos bytes sem autoridade antiga e
 falhas originais engolidas de metadata/primary/backup sem evidencia falsa.
 Fixture continua pulando init completo: NAO e qualificacao global de gameplay.
-50checks financeiros ORIGINAIS com `test-finance-commands.ps1 -ProbeWriter`
 em `build/written-save-probed-finance.log`, fixture PRIVADA
 `Temp/opencode/vbrasfoot-finance-4f7e9dbd9b0a4a5bbe736a2904aec91a`:
 stream efetivo apos efeitos financeiros ligado ao arquivo, decoder dos mesmos
 bytes e avaliacao com UUIDs APPLIED ainda UNPROVEN/bloqueados. Autoridades dessa
 fixture sao ARTIFICIAIS; nao e aceite do contexto de Main/standalone/UI.
-47checks financeiros ORIGINAIS sem instrumentacao tambem passaram em
 `build/written-save-normal-finance.log`. Wrappers financeiros rodaram32checks
 iniciais de stream+18de assessment;4checks adicionais de report passaram depois
 em saida isolada (36total), sem alterar o runtime financeiro original testado.
-SDK-only ZIP/hashes/allowlist/cinco projetos/negativos passou em
 `build/written-save-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-6ce72bf97fd74a3cad8063f36559a889`.

Javap read-only do EXE conhecido esta em
 `Temp/opencode/vbrasfoot-save-writer-20261020.javap.txt`; offsets48/140/149/160/169/174
 confirmam destinos .info/.s22, Output ctor, duas roots e close. REA nao anunciou
ferramenta JVM na busca direcionada; sem instalacao/configuracao para forcar isso.
Erros iniciais de compilacao do report e de fixture sem root-assignment/novo load
foram corrigidos; nao contam como positivos nem motivaram relaxar verificacao.

O save original nao guarda IDs dos comandos. Cobertura causal das mutacoes
financeiras e testemunho persistente por operacao ainda faltam para resolver
APPLIED/UNKNOWN/inclusao no save. Categorias/UI/standalone, calendario/mercado e
API publica tambem nao estao aprovados. Sem novo mod, instalacao pessoal/save/EXE
alterado, release/tag ou nova sessao humana. Detalhes e limites:
 `roadmap/WRITTEN-SAVE-EVIDENCE.md`.

### Rotulos e refresh financeiro — evidencia ESTATICA adicional

Scan read-only de1032classes nao-org do EXE original estabeleceu:
 credito6->dO/eH->rotulo `Patrocinio/Sócio torcedor:` e
 debito7->dQ/eI->rotulo `Estádio:` na construcao de linhas a.cW.pp.
Nao chamar7de salario. Rotulo visual nao valida todos os produtores de caixa,
contratos recorrentes ou efeitos de mod. Fontes/offsets/fingerprints em
 `Temp/opencode/finance-ui-static-cb1e3beb21294da5a2e20d7547ae8dc7/FINDINGS.md`.

a.eg.qx atualiza somente saldo principal; a.cW.po atualiza resumos, nao linhas.
Rechamar pp duplica linhas; pq sozinho nao rele ledger. Original qT cria novo
dialogo/modal e re chama qx ao retornar. a.eg.re nao e refresh puro (chama
best.S.w); nao usar como callback inocuo. Nenhuma UI foi aberta/adapter de refresh
aprovado por essa analise.6classes org nao foram disassembladas; reflexao/runtime
nao observados. Nao contar busca inicial .NET sem candidatos como ausencia real.
Mapa e limites: `roadmap/FINANCE-UI-EVIDENCE.md`. Sem mudanca de categoria no SDK,
execucao de jogo, nova sessao humana ou aceite financeiro/visual standalone.

## Inventario de contratos no contexto operacional — 19/10/2026

Coordenador de Main agora seleciona root dedicado finance-requests alem de
associations/journals e revisa uniao de owners configurados + namespaces de
journals + contratos existentes no binding explicitamente escolhido. Inclui
mods desativados/nao selecionados e request-only owners. Uniao acima de64recusa
resultado parcial. Nenhuma pasta de contratos e criada por construir/revisar.

Inspecao administrativa mostra ancora REAL do envelope dos contratos separada
da revisao observada, preservando guard estrito no read/persist ordinario. Para
cada key compara intent completo (operacao+digest): pedido sem receipt, receipt
historico/incerto sem contrato, conflito, pareado APPLIED ou pareado UNKNOWN
bloqueiam contexto. Pareado NO_EFFECT nao acrescenta incerteza. Recusas legadas
NO_EFFECT sem contrato sao identificadas LEGACY_NO_EFFECT_WITHOUT_REQUEST e
preservam somente compatibilidade da preparacao limitada, sem fabricar payload,
receber lease antiga ou contornar armazenamento no novo dispatcher.

Review conserva ambos inventarios e revalida HEADtokens/owners antes de confirmar
qualquer acao e novamente antes de instalar contexto. Ancora divergente, contrato
incompleto/corrompido ou link bloqueante recusam PREPARE_INTERNAL_CONTEXT no MESMO
gate da sessao. Journal-only NO_EFFECT reanchor NAO migra contrato: ancora antiga
fica visivel e contexto continua bloqueado. Sem snapshot atomico ou autorizacao
duradoura; operacoes futuras ainda precisam de preflight renovado.

Evidencia sintetica em saida isolada
 `Temp/opencode/contract-inventory-final-374a8eac7b1945d7bfe5d0d818d9811d`:
-68checks de contexto:28novos cobrem pareamentos, request-only/mod desativado,
 conflito sob NO_EFFECT, orphan/corrupt HEAD, limite da uniao, alteracao de HEAD
 ou namespace apos review, legado sem contrato e ancora antiga apos reanchor do
 journal. Positivos usam roots/markers init PROPRIOS, nao qualificacao original.
-43checks do coordenador e46de armazenamento preservados.
-SDK-only ZIP/hashes/allowlist/cinco projetos/negativos passou em
 `build/contract-inventory-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-ef3c8391d9564adea4c93f88cf2e2b24`.

Primeira tentativa de teste de HEAD corrompido supunha row de erro; inventario
recusou tudo como CORRUPT ANTES de row parcial. Teste foi corrigido para exigir
essa recusa mais conservadora, sem enfraquecer o codigo e sem contar a tentativa
falha como positiva.

Painel exibe contagem/ancora/links/falhas, sem novo botao de apagar/migrar/pagar.
Main ainda NAO persiste contratos nem cria owners/handlers de escrita para mods.
Faltam lifecycle/protocolo entre revisoes/restore/save-as, reconciliacao
APPLIED/UNKNOWN/inclusao no save por operacao, quotas/catalogo globais/retencao/
compactacao, categorias/UI/standalone e API/calendario/mercado. Sem novo mod,
instalacao/save pessoal alterado, release/tag ou nova sessao humana. Contrato:
 `roadmap/FINANCE-CONTRACT-INVENTORY.md`.

## Contrato financeiro gravado antes do diario/efeito — 19/10/2026

FinanceRequestStore implementa agregado interno append-only de ate32pedidos
completos v1 por career/binding/owner, em root dedicado e ancora exata do save.
Reusa CAS/lock/HEAD checksummed/previous e quotas do sidecar. Mesmo payload/key
confirma read-only; outro payload sob mesma key conflita, sem eviction. Leitura
revalida counts/lengths/keys/schema/ancoras/escopos; namespace sem HEAD comprometido
e reconciliacao obrigatoria, nunca historico vazio. Nenhuma migracao automatica.

executeStoredFinance confere contexto/owner/handler/request/journal/store, revisa
journal antes e depois de persistir contrato e so entao entra no dispatcher
journal-before-effect existente. Plano persistente pelo execute comum recebe
REQUEST_REQUIRED sem intent/efeito. Nova key sob PREPARED/UNKNOWN, token velho,
payload historico conflitante e journal orphan sao recusados antes de armazenar
novo contrato. Mesmo com gate novo, historico registrado nao recebe outra lease.

Evidencia:
-46checks finais do request-store em compilacao isolada propria:
 `Temp/opencode/finance-store-final-10fd4cd7e5714ef28954395d66cadc8d`.
 Cobrem persistencia/reabertura/CAS, chave imutavel, quotas, schema/corrupcao,
 duplicate/anchor/owner/EDT, ordem payload->PREPARED->efeito->receipt, falhas de
 payload/journal em RECORD_WRITTEN/HEAD_STAGED/HEAD_COMMITTED e gate novo sem
 replay, novas keys bloqueadas por incerteza e conflito antes da persistencia.
-47checks originais na mesma compilacao isolada, fixture PRIVADA em
 `Temp/opencode/finance-store-final-10fd4cd7e5714ef28954395d66cadc8d/fixture/runtime-test.log`:
 credito/debito agora exigem contratos armazenados, bypass recusado, payloads
 exatos preservados em ordem, writer/reload originais e restore do input na
 fixture nao reexecutam APPLIED. Hashes do EXE/save de origem conferidos antes/
 depois. Autoridades da fixture sao ARTIFICIAIS, nao aceitacao de Main/gameplay.
-Wrapper completo `test-finance-commands.ps1` tambem passou em
 `build/finance-request-store-validation.log`: build/suite,52checks de request,
 32iniciais de store e47originais. Os guards/testes adicionais posteriores foram
 validados em compilacao isolada com46checks; nao confundir32com suite final.
-SDK-only ZIP/hashes/allowlist/cinco projetos/negativos aprovados em
 `build/finance-request-store-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-5bc229d4422645edaace030610a6643e`.

Uma tentativa de comando local da fixture parou antes de copiar/iniciar o jogo
por usar variavel PowerShell HOME read-only; reexecucao com loaderHome passou.
Nao contar erro de harness como positivo. Fault probes continuam excecoes
controladas, nao power-loss nem garantia de durability multi-file.

Main ainda NAO cria request-store nem owners/handlers publicos. Quotas globais,
retencao/compactacao, inventario de contratos no contexto e protocolo de revisoes
continuam pendentes. Store recusa outra revisao mesmo apos metadata/NO_EFFECT
reancorados; nao contornar com migracao silenciosa. APPLIED/UNKNOWN/inclusao no
save, categorias/UI/standalone e API publica permanecem sem aceite. Sem transacao
save+journal+contrato, exatamente-uma-vez, rollback ou retry automatico.
Nenhum mod criado, instalacao pessoal/save/EXE alterado ou processo pessoal
encerrado. Nenhuma nova sessao humana/release/tag. Contrato:
 `roadmap/FINANCE-REQUEST-STORE.md`.

## Pedido financeiro interno reproduzivel — 19/10/2026

FinanceRequest v1 define codec canonico limitado a4096bytes: perfil/fingerprint,
career/binding/save hash+tamanho, owner/key, locator composto do clube, raw6/7,
quantia e precondicao de saldo+14campos. Digest SHA256 nao usa nonce/token/geracao.
Decode rejeita versao/perfil desconhecidos, valores invalidos, truncamento,
trailing/noncanonical bytes. Nao e store de producao, autoridade ou witness de
efeito. Fixture grava payload em arquivo proprio; journal continua guardando
intent/digest/receipt, nao contrato inteiro.

Adapter prepara plano fraco one-shot a partir do request imutavel, valida locator
e snapshot integral na EDT antes de plano/efeito e nao atualiza precondicao velha.
Dispatcher consulta escopo do handler (owner/career/binding/revisao) antes de
reservar/persistir. Nenhum owner publico, ModContext.write ou instalacao nova.

Evidencia contra fontes finais compiladas em saida isolada:
-52checks de payload, outra JVM, todos os campos individualmente, bytes invalidos,
 contexto e journal reaberto, sem nova lease para APPLIED nem overwrite de key.
-44checks financeiros originais em copia PRIVADA:
 `Temp/opencode/persistent-finance-final-7e1b95b879d94d6ba8616aafee9c059c/fixture/runtime-test.log`.
 Credito e debito usam pedidos canonicos; negados owner/career/binding/save/locator
 errados e precondicao velha, sem journal/efeito indevido. Repeticao do clube foi
 inserida/removida SOMENTE na lista da fixture para testar recusa de ambiguidade,
 antes de qualquer save/efeito. Writer/reload originais preservam saldo e ledger.
 Restore do input na fixture e journal reaberto com gate NOVO retornam
 RECORDED_ONLY para pedido aplicado, sem pagamento duplicado. Autoridades desses
 testes sao ARTIFICIAIS, nao qualificacao operacional do loader instalado.
-SDK-only ZIP/hashes/allowlist/cinco projetos/negativos aprovado em
 `build/persistent-finance-sdk-test.log`, fixture
 `Temp/opencode/vbrasfoot-sdk-author-8b30fb1740a0467f9e3340c5ddd60243`.

Tentativa inicial exigindo ID gD globalmente unico foi RECUSADA pelo original
antes do efeito: IDs se repetem no save fornecido. Logs iniciais preservados em
 `build/persistent-finance-validation.log` e
 `Temp/opencode/persistent-finance-final-e3c594fe9cd541b29abc89ec222604c9/fixture/runtime-test.log`.
Nao contar essas tentativas como positivas. Locator final e revisao EXATA do save
+indice+ID+nome, exigindo par ID/nome unico e referencia nao repetida em P, alem
de clube humano exato em P/aN. Nao e ID global, nao migra entre revisoes/copies.

Fontes/save originais tiveram hashes conferidos antes/depois da execucao final.
Nenhum processo pessoal encerrado, save pessoal/EXE/mod instalado sobrescrito,
release/tag ou nova sessao humana realizada. APPLIED/UNKNOWN/inclusao no save,
request-store de producao, categorias/UI e reabertura standalone permanecem sem
aceite; os testes nao deduzem efeito persistido apenas de saldo ou ledger.
Contrato e limites: `roadmap/PERSISTENT-FINANCE-REQUEST.md`.

## Contexto interno revisado e reconciliacao NO_EFFECT — 19/10/2026

Main conecta o coordenador ao MESMO gate privado de ProbeSession/interlock.
Preparar contexto e acao explicita: requer associacao/arquivo/stream/roots/geracao
atuais, init observado/lifecycle healthy/ACTIVE sem I/O em voo e todos os journals
revisados sem efeitos historicos/incertos, erros ou anchors divergentes. Gate
tambem recusa memoria APPLIED/UNKNOWN, inclusive se inventario persistente vazio.
Resultado BOUND_NO_PUBLIC_WRITES nao concede owners/permissoes/handlers aos mods,
nao e consistencia global ou preflight permanente para operacoes futuras.

Preparacao usa token privado one-shot/epoch otimista, sem bloquear I/O durante
hash/reads. Final EDT recheck valida ownership do interlock e source atual. Cancel
de timeout/interrupcao revoga instalacao que ja ocorreu ou impede instalacao
tardia, sem apagar contexto novo de outra preparacao. Mudanca de root/inicio de
I/O/metadata confirmada invalida o contexto antigo, sem restauracao automatica.

Inspecao administrativa do journal mostra ancora realmente comprometida, mantendo
read/write ordinarios com guard de revisao. Quarentena na ancora antiga continua
monotona, sem migrar UNKNOWN. Novo caminho de reconciliacao so reancora historico
nao vazio integralmente NO_EFFECT_CONFIRMED, via prova interna opaca/CAS/lock e
previous: preserva keys/UUIDs/digests/resultados e nunca reemite lease. APPLIED,
PREPARED e UNKNOWN recusados; ausencia de efeito nao e inferida de saldo ou aceite
generico do usuario. Contratos de mods/outros journals nao sao migrados juntos.

Evidencia:
-27checks de reconciliacao limitada e40de contexto passaram contra fontes atuais
 compiladas em saida ISOLADA, sem sobrescrever build concorrente, em
 `Temp/opencode/operational-context-final-497ddcfd3fe849709a6003192330e4ce`.
 Contextos/init positivos nesses testes sao SINTETICOS, nao qualificacao original.
-152checks I/O/schema originais em `build/operational-context-original-io.log`,
 fixture PRIVADA `Temp/opencode/vbrasfoot-hook-io-61ed2ca5c1664504ba92d2cdbff9577e`.
 Fixture pula init e agora confirma recusa de preparacao operacional nesse estado.
-29checks financeiros originais em `build/operational-context-finance-reviewed.log`,
 fixture privada `Temp/opencode/vbrasfoot-finance-3e40aa460769428abf585f4f926147a8`:
 journal de recusa real do adaptador sem mutacao reancorado apos writer/reload
 originais; journal misto com APPLIED recusado. Autoridades da fixture sao ARTIFICIAIS.
-SDK-only ZIP/hashes/allowlist/cinco projetos/negativos aprovado em
 `build/operational-context-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-850bce7d4dac498cb7b816103890e121`;
 archive local dist/sdk-0.9.3-operational-context.zip, sem release/tag/upload.

Execucao conjunta de wrappers excedeu timeout depois do I/O aprovado; financeiro
foi reexecutado separadamente e passou. Nenhum processo do usuario encerrado.
Guard refinado de memoria/quiescencia/recusas foi testado depois em saida isolada (40checks),
além do build completo com36checks anterior. Nao contar timeout como sucesso.
Nenhuma nova sessao humana foi fabricada, nenhum save pessoal/mod/instalacao
alterado. Contexto publico, reconciliacao APPLIED/UNKNOWN, pedido financeiro
persistente/categorias/UI, calendario/mercado e API de escrita continuam pendentes.
Contrato: `roadmap/OPERATIONAL-CONTEXT.md`.

## Preparacao de carga humana e inspetor offline — 19/10/2026

`prepare-load-validation.ps1` reutiliza copia completa verificada/profile/hash/
recusa de processo aberto do preparador existente, acrescenta home de sonda novo
sem mods, exports ASM/sun.java2d, launchers e roteiro de carga EXISTENTE sem criar
carreira/avancar/jogar/salvar/confirmar metadata. Nao inicia jogo automaticamente.
baseline.json/load-validation.json indicam preparacao, nunca aceite da carga.

`--inspect-load-validation PASTA-SESSAO` e target-free: antes de InstallPaths/
Profile.verify/diagnostics/runtime, le somente sete reports encerrados conhecidos,
ate128KiB cada, sem redirects. Parse estrito de contadores/booleans/enums/duplicatas,
profile e lifecycle sessionIDs correlacionados entre provenance/interlock/lifecycle.
Rele arquivos antes de aceitar. Nao escolhe latest nem imprime texto/paths privados
arbitrarios. ProbeSession acrescenta sessionID aos reports de provenance/interlock;
reports antigos sem correlacao sao INCOMPLETE, sem qualificacao retroativa.

Resultados: QUALIFIED_LOAD_OBSERVED (so markers coerentes), NOT_QUALIFIED (save,
perdas, recusas, init ausente, trabalho em voo etc.) ou INCOMPLETE (arquivos ausentes/
inseguros/malformados/incompativeis/misturados/alterados). CLI nao-positiva retorna
erro, sem criar home/log/cache/backup ou executar game/mod. Positivo NAO e contexto
vivo, prova contra adulteracao, consistencia global, ausencia de todos os efeitos,
durabilidade ou aceite humano. Nenhuma escrita e autorizada por esse resultado.

31checks target-free aprovados em fixture propria
`Temp/opencode/load-validation-inspector-3016163618733426665`: marcadores positivos
SINTETICOS, old-frame path, save/falha de save, interlock em voo/recusa, criacao,
perdas, mistura de sessoes/geracao, formato/counter/boolean/duplicatas/profile,
limites/termination, EDT/parser/CLI real sem jogo/home e junction Windows. Inclui
roundtrip dos writers reais ProbeSession/SessionDiagnostics com markers sinteticos;
NAO e evidencia de ramo init original. Build completo local inicial passou com28
checks; testes ampliados31foram recompilados/executados depois, sem mudar API publica.
Sintaxe PowerShell do novo preparador aprovada. Nenhum processo do usuario encerrado.

Inspecao estatica privada em `build/full-load-init-20261019.javap.txt` reutilizou
cache original conhecido com javap existente: c.a chama ap no init completo; ap
tem caminhos de autosave/progressao conforme estado/configuracao. Por isso nao foi
executado automaticamente como se fosse getter sem efeitos, nem foi alterado o
save para obter positivo. Ramo init real/menu/painel e aceite humano permanecem
pendentes. Nao ha criacao/partida/escrita no jogo pessoal ou instalacao nova.
Contrato e roteiro em `roadmap/LOAD-ONLY-VALIDATION.md`; STATUS.md e contexto de
retomada, nao declaracao de que a sessao humana foi realizada.

## Inventario read-only dos journals do binding escolhido — 19/10/2026

`CareerSidecarStore.inventoryBinding` le somente careerUUID/bindingUUID fornecidos
no root selecionado. Une namespaces existentes com owners configurados no
coordenador, sem inferir identidade, varrer outras carreiras ou carregar mods.
Nomes/tipos/ancestors/reparse/conteudo conhecido/quotas/HEAD checksums validados;
nao cria locks/pastas, apaga desconhecidos ou promove orphan. Namespace existente
sem HEAD e pendencia RECONCILIATION_REQUIRED, nunca diario limpo/ausente.

Limites:64namespaces,128arquivos/16MiB por namespace e budget cooperativo5s (nao
interrompe syscall bloqueada). A UNIAO com owners configurados tambem tem limite64.
Exceder limite, entrada desconhecida, HEAD corrupto ou erro I/O recusa revisao,
sem resultado parcial apresentado como completo. Ordem deterministica na UI;
owner configurado e existente nao selecionado sao distinguidos.

Review compara inventario/HEADtokens antes/depois da leitura dos journals e
novamente em confirm. Mudancas exigem review nova. Quarentena explicita de um
owner nao selecionado usa somente receipt/ancora/token revisados; nunca executa
mod ou handler. Sem resolver UNKNOWN/APPLIED, migrar diario ou instalar contexto.
Nao e snapshot atomico, catalogo global, garantia contra alteracao hostil ou lock
multi-namespace. Metadata pode ficar divergente de journal apos revisao de save;
guard continua recusando isso em vez de reassociar silenciosamente.

Evidencia aprovada em `build/binding-inventory-original.log`:
-18checks novos de inventario em fixture propria
 `Temp/opencode/binding-journal-inventory-9889667303312064670`;
-43checks de coordenador, incluindo disabled/orphan e union overflow, fixture
 `Temp/opencode/career-association-coordinator-14489925187261979430`;
-151checks I/O/schema originais em COPIA privada
 `Temp/opencode/vbrasfoot-hook-io-0215f95ab0d44665a9554fb70763d215`: journal
 SINTETICO nao selecionado descoberto/quarentinado numa associacao ARTIFICIAL da
 fixture ligada ao decoder original. Nenhum handler financeiro ou mod executado
 para produzir/quarantinar esse journal; init UI original continua nao qualificado.
-26checks financeiros originais aprovados em `build/binding-inventory-finance.log`,
 fixture privada `Temp/opencode/vbrasfoot-finance-39c7741242e94dea88d48f4880f29f38`.
-SDK-only hashes/allowlist/cinco projetos/negativos aprovado em
 `build/binding-inventory-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-a874b114ba684da7ae6b617f249439b1`;
 archive local dist/sdk-0.9.3-binding-inventory.zip, sem release/tag/upload.

Primeiro build local ultrapassou timeout do harness apos testes principais; nao
contado como build completo aprovado. Wrapper original foi reexecutado com limite
maior e build completo/regressoes passaram conforme logs acima. Nenhum processo
do usuario foi encerrado. Wrappers conferiram EXE/save originais por hash;
loader instalado0.9.3 e mods/saves intactos. Contrato e limites:
`roadmap/BINDING-JOURNAL-INVENTORY.md`. Qualificacao real, reconciliacao por efeito,
calendario, mercado e API publica de escrita continuam pendentes.

## Admissao nas entradas originais de I/O — 18/10/2026

`CareerIoInterlock` conecta o gate cooperativo a carga/reset/save originais no
modo sonda. CareerTransformer chama beforeOriginalIo ANTES do corpo de c/ae/setter
e depois de super, antes da mutacao, no construtor de reset. Admissao recusada
propaga antes do try de cleanup; nao fecha frame do pai. Retornos/Throwable liberam
owner capturado em afterOriginalIo. Nao engolir BUSY e seguir com writer original.
Schemas/fingerprint originais mantidos; nenhuma escrita no EXE.

LOAD/RESET externos obtem fence, invalidam contexto anterior e permitem somente
fallback/reset aninhado no mesmo thread, sob a mesma janela. SAVE nao aninha e
threads concorrentes sao recusados. queued/running/timed-out ou persistencia de
completion ainda ativa impedem admissao. Nao ha espera EDT, queue/retry de save
ou restauracao de contexto depois do retorno. Stack limitada64; foreign/LIFO/
double exits recusados. Detach fecha mas nao libera I/O em voo; contador global
impede hot swap do owner no meio da rotina. Caller sem owner tambem e contado.

ProbeSession instala gate/interlock privados antes do runtime e encerra ambos
na disposal. Report local original-io-interlock.properties contem contagens/depth
e writeAuthority=false, sem filenames/objetos. Esse gate NAO concede contextos a
mods e NAO e exposto no ModContext. Nao atribuir prontidao ou reconciliacao a ele.

Evidencia aprovada:
-39checks novos em `build/original-io-interlock-final.log`, fixture propria
 `Temp/opencode/original-io-interlock-13929383224137494441`: exclusao gate/bridge,
 bytecode sintetico transformado, fallback/Throwable/earlyreturn/constructor,
 competencia de threads, depthbound, LIFO, post-handler persistence, timeout,
 attach/detach sem janela desprotegida e recusas antes de efeito.
-149checks I/O/schema originais, fixture PRIVADA
 `Temp/opencode/vbrasfoot-hook-io-4a82374b4a7b482285b0cc0b56041853`: uma reserva
 ARTIFICIAL da fixture bloqueia writer antes de output, decoder/setter antes de
 root/lifecycle/provenance mutation. Sem reserva, writer/reload/fallback originais
 continuam funcionando, entradas balanceadas e contexto antigo nao e restaurado.
 JFrame sintetico ainda pula init UI; nenhum gate de gameplay aprovado.
-26checks financeiros originais aprovados em
 `build/original-io-interlock-finance.log`, fixture privada
 `Temp/opencode/vbrasfoot-finance-caa43a707cfa4512b7b17955d9b22b23`.
-SDK-only ZIP/hash/allowlist/cinco projetos/negativos aprovados em
 `build/original-io-interlock-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-fabcb931147744c1b59e4416299da117`.
 Archive local dist/sdk-0.9.3-original-io-interlock.zip, sem release/tag/upload.

Wrappers conferiram origem EXE/save por hash. Loader instalado continua0.9.3;
nenhum mod/carreira/partida criado e nenhum save de usuario sobrescrito. Protecao
e somente cooperativa de um gate no runtime instrumentado, nao lock cross-process
ou sandbox. UI original pode apresentar/logar recusa propagada, sem promessa de
dialogo customizado. Qualificacao real, reconciliacao, calendario, mercado e API
publica ainda pendentes; contrato em `roadmap/ORIGINAL-IO-INTERLOCK.md`.

## Stream decodificado, coordenador e painel de revisao — 18/10/2026

`CareerTransformer` conserva fingerprint exato de c.a e schemas, acrescentando
observacao do path realmente passado a FileInputStream e wrapper somente no
InputStream original fornecido a Kryo. Exige1site de cada construtor. Hash/count
cobrem bytes alimentados no buffer, nao leitura extra nem prova de interpretacao
semantica de cada byte. Leitura/close originais preservados; skip/mark/reset,
cross-thread, falhas ou >256MiB recusam evidencia sem truncar o stream original.

`DecodedSaveProvenance` retém uma unica evidencia fraca, ligada a sessao/geracao/
roots de carga aceita. Fallback propaga origem/bytes do backup. Invalidacoes de
load/reset/root/save/detach e revisao esperada impedem completion antigo de
substituir estado mais novo. `LoadedSaveVerifier` compara path real+hash+tamanho
do arquivo observado e roots na EDT com timeout; nao e autoridade de escrita.

`CareerAssociationCoordinator` revalida evidencia/metadata ao revisar e confirmar.
Review privada, por instancia e one-shot; nenhuma identidade inferida sem handle
explicito ou confirmacao nova. Registro limitado aos owners configurados, NAO
inventario global. Quarentena usa owner/token revisados; revision mismatch ou
corrupcao nao se tornam diario vazio. Nao instala CareerCommandGate.Context,
nao migra journal entre saves e nao resolve APPLIED/UNKNOWN como sucesso/fracasso.

Main registra painel de metadata SOMENTE em modo sonda. Abrir/Atualizar nao
escrevem; dialogo exige acao explicita com Cancelar padrao. UUIDs editados revogam
review, todo I/O fica em SwingWorker e commit incerto conserva Handle tentado.
Metadados em data/.career-state, separados de ModStore publico. Source em codigo
novo, nao instalado. Novo report local decoded-save-provenance.properties inclui
status/motivo/hash/tamanho/geracao/backup/initialization, writeAuthority=false,
sem paths/objetos exportados. Novo painel ainda sem aceite visual humano.

Evidencia local aprovada em `build/decoded-coordinator-final-io.log`:
-36checks de provenance em fixture propria
 `Temp/opencode/decoder-provenance-4182109270792433907`;
-36checks de coordenador/UI report em fixture propria
 `Temp/opencode/career-association-coordinator-13708892728670936148`;
-142checks I/O/schema originais em copia privada
 `Temp/opencode/vbrasfoot-hook-io-83bbdfc528cd442097ee36aca14d230d`:
 stream primario completo, path/hash/tamanho, roots/geracao, fallback real,
 metadata explicitamente ARTIFICIAL da fixture, journals sem criacao automatica
 e recusa de review depois do writer. JFrame sintetico continua pulando init UI;
 initializationPathObserved=false e public IO load UNKNOWN, sem contexto de comandos.
-26checks financeiros originais continuam aprovados, evidencia em
 `build/decoded-coordinator-final-finance.log`, fixture privada
 `Temp/opencode/vbrasfoot-finance-b064aa37f6674150913687520ad596b8`.
-SDK-only ZIP/hashes/allowlist/cinco projetos/negativos aprovados em
 `build/decoded-coordinator-sdk-test.log`, fixture propria
 `Temp/opencode/vbrasfoot-sdk-author-6ba72963e8c54dd5af83a2c0022ea129`.
 Archive local dist/sdk-0.9.3-decoded-coordinator.zip, sem release/tag/upload.

REA binary_session nao anunciou ferramenta JVM elegivel nesta sessao; providers
nativos nao disponiveis/configurados. Reutilizados javap/JDK e bytecode privado
anterior do perfil SHA conhecido. Sem instalar engines/configuracoes nem publicar
bytecode/saves. Wrappers conferiram hashes de origem EXE/save; jogo instalado,
mods e saves nao foram atualizados.

Nao e conclusao de "tudo": faltam qualificacao/global graph real e aceite humano,
writer preflight/gate de producao, reconciliacao por operacao entre revisoes,
discovery/quotas/compactacao/migracoes globais, categorias/UI financeira, calendario,
partidas/temporadas, mercado/ofertas/interceptacao/contrapropostas/commit e API
publica de escrita. Nao inventar sucesso para essas lacunas. Contrato completo:
`roadmap/DECODED-SAVE-COORDINATOR.md`.

## Recuperacao conservadora do diario e exclusao durante save — 18/10/2026

- `CareerCommandJournal.inspectRecovery` compara ancora registrada conhecida e
  revisao observada sem migrar HEAD. Classifica EMPTY, NO_EFFECT_ONLY,
  HISTORICAL_EFFECTS e UNCERTAIN. Mesmo hash igual nao comprova efeito no grafo.
- `quarantinePending` persiste apenas PREPARED -> OUTCOME_UNKNOWN com CAS e lock,
  preservando keys/UUIDs/digests/resultados terminais. Nao reemite lease, nao
  resolve incerteza como sucesso/ausencia de efeito e nao desbloqueia intents.
  Complete tardio nao pode substituir UNKNOWN por APPLIED/NO_EFFECT, mesmo com
  token relido. Corridas recovery/complete tem um unico vencedor CAS.
- `CareerCommandGate.beginSave/endSave` fornece exclusao cooperativa ANTES do
  writer original; recusa queued/running/timed-out ainda em execucao. Durante a
  janela, nega reserva e novo contexto. Fechar janela em retorno/falha nao
  restaura roots/contexto anterior nem muda ancora de associacao/journal. Dispatcher
  retem exclusao ate retorno da persistencia terminal, nao so ate retorno do handler.
- 54 checks novos aprovados em `build/recovery-finance-final.log`: estados de
  recovery, mismatch sem migracao, quarentena idempotente, leases tardias, duas
  corridas CAS, tres etapas de fault injection, EDT, autoridade e ciclo da fence,
  dispatcher sem handler/intent durante save ou apos falha, e completion I/O
  pausado impedindo save/troca de contexto. Fixture propria:
  `Temp/opencode/command-recovery-8202244687060114063`.
- 26 checks do fluxo financeiro original aprovados com writer/reload dentro da
  fence fornecida EXPLICITAMENTE pela fixture. Saldo e 14 campos de ledger
  preservados; old context recusado; historico financeiro nao reancorado ao output.
  Copia privada: `Temp/opencode/vbrasfoot-finance-686ca51a8f284e8db5fc04759d081a7b`.
  Origem EXE/save preservada por hashes do wrapper. Nao e interceptacao standalone.
- 132 checks I/O/schema originais aprovados em `build/recovery-hooks-final.log`;
  fixture privada `Temp/opencode/vbrasfoot-hook-io-560df06a6ef8468da3f2f539273c7a06`.
- SDK-only fora do checkout aprovado em `build/recovery-sdk-final-test.log`, fixture
  `Temp/opencode/vbrasfoot-sdk-author-38258a31086c45fa99ba032ffdcf89dd`;
  archive local `dist/sdk-0.9.3-recovery-final.zip`. Sem publicacao/release.
- Loader instalado continua 0.9.3 com SHA256
  `3662113df6f2277c7c3ab05d3e9bb0143664a707dcf798dbf3109f8b235004f7`.

Limites: servicos internos ainda sem Main/ModContext/startup/hook de writer
conectado. Fence nao e lock de outra instancia do jogo. Quarentena pode cercar
conservadoramente uma lease ainda viva, sem cancelar/encerrar seu handler.
Sem inventario global de journals, prova dos bytes realmente decodificados,
reconciliacao de efeitos APPLIED/UNKNOWN, migracao entre revisoes, UI ou
atomicidade save+sidecars. Fault injection usa excecoes controladas, nao power-loss.
Ainda faltam calendario/mercado e API publica. Nenhum mod criado ou instalado.
Contrato e pendencias: `roadmap/JOURNAL-RECOVERY-AND-SAVE-FENCES.md`.

## Associacoes explicitas de saves — observacao nao e carreira carregada

SaveFileObserver le arquivo .s22/.sbck selecionado fora da EDT,nomes/pasta
normalizados/ancestors/reparse/NOFOLLOW/tiporegular,256MiBmax e doubleSHA256+
size/fileKey/lastModified/creationTime antes/depois. verify refaz leitura antes de
confirmar. Nao deserializa,escreve no save ou afirma que bytes sao grafo carregado.
Arquivo pode mudar aposobservacao;nao e lock/commit conjunto com writeroriginal.

CareerSaveAssociations persiste metadados com UUIDs novos SOMENTE mediante acao
explicita do caller interno confiavel:CREATE_NEW,FORK_COPY_WITH_EMPTY_MOD_DATA,
RENAME_SAME_FILE,ASSERT_RENAME_WITHOUT_FILE_IDENTITY,ACCEPT_CHANGED_REVISION e
RESTORE_RECORDED_PREVIOUS. Copy nao herda contratos/journal;lineageinclui parent
handle/token/revision/savehashsize. Rename exige oldpathausente e evidencia da
forca declarada;revision/restore no mesmopath,restore apenas previous comprometido
validado. Save-as nome+bytes novos nao e adotado por heuristica. ConflitosCAS,
staleObservation,unknownschema/corrupcao falhamfechado,orfaos preservados.
Falha de create apos commit conserva Handle TENTADO na CreationFailure para
inspecao explicita;nao gerar outra identidade automaticamente apos erro incerto.

WindowsJava neste host retorna fileKeynull. Nao inventar identidade com hash/nome:
EXACT_OBSERVATION nao e gerado nesse caso;strongrename recusado. Ramo afirmado
pelo usuario e separado/gravado como decisao maisfraca,nao sameobject comprovado.
Files.isSameFile quando amboscaminhosexistem detecta hardlink mesmo com keynull.
Tambem conservado creationTime em Observation e manifest;igualdade de hash/mtime
nao esconde alteracao de creationTime entre observacao e confirmacao.

Store adiciona inspectAssociation/changeAssociationRevision APENAS para namespace
br.vbrasfoot.association em rootdedicado. Transicao verifica token+ancora anterior
soblock,registroexistente,payloadversionado+previous;nao habilita migrar dados/journal
ordinarios. read/write de namespace de mod continuam exigindo SaveRevisionigual.
Sem ModContext/Main/UI/contextocomandos conectado,sem capacidades/metodospublicos,
sem criacao de mods ou instalacao;versao instalada0.9.3permanece igual.

58checks sinteticos aprovados em association-original-integrated.log,fixture
Temp/opencode/save-association-2930481404577992252:roundtripmetadata,confirmacao
obrigatoria,copia/emptyfork/lineage,renameassertion,revisao/restoreprevious e
unknownrestore,metadadosold/staleToken/race2writers/1winner,sourcechangehashpass,
creationTimechange,nomes/pathtraversal/EDT,hardlink,junctionWin,corruptschema/payload,
guarddeprivilegio do store e3faultstagescomHandlepersistido. Strong nativo rename
fileKey indisponivel =>SKIP explicito desse ramo;nao conta como aceite/prova.
Dados sao bytes proprios com extensao de save,nao carreiras decodificadas.

Regressao integrada original24checks aprovada em fixture PRIVADA
Temp/opencode/vbrasfoot-finance-66edaf6ab3534a65a531dfcc4104ce05:save input original
observado/associado por decisao ARTIFICIAL da fixture,UUIDs usados no contexto de
teste financeiro;originalcredit/debit e writer/reload preservam saldo/14camposledger.
finance-output de nome/hashnovos NAO reassocia metadata nem diario automaticamente;
policy save-as e reconciliacao exigidas. Contexto nao e prova de qualificacao real
do loader/UI/nem aceite de autoria. Origem EXE/save conferidos porhash intactos.
132checks I/O/schema originais tambem aprovados em fixturec6aa8de8bc054f0aa0ac60b5a54b6d65
sob Temp/opencode. SDK-only5projetos/hashes/allowlist/negative/JSON fora checkout
fixture8d2ca07eeb554ddaa67e444f3d7793a5 aprovado;archive local
dist/sdk-0.9.3-association-reviewed.zip. Sem release/tag/upload ou redistribuicao
de saves/bytecode/jogo. Nao pedir outra carreira para validar este core interno.

Pendentes:arquivo exatamente decodificado->roots/generation,coordenador/UI,
discovery/index/duplicidadeglobalpaths/quotasglobais,migracoes/compactacao,
reconciliacao de dados/journal e resultadosincertos,save-as,eventos e mercado,
APIspublicas/aceite humano. Nao ativar comandos com EXACT_OBSERVATION isolado.

## Dispatcher com journal e adaptador financeiro original limitado

CareerCommandDispatcher agora conecta intentpersistido->gate->EDT->handler->
resultadopersistido. Exige owner/bindingUUIDs coerentes e handler que aceite
operation/payloadDigest;callbacks genericos default nao autorizados. Root preflight
na EDT/timeouts e nova checagem antes do efeito;I/O fora da EDT,sem callback sob
lockdo gate. Diferencia EXECUTED/NO_EFFECT/RECORDED_ONLY/DENIED/UNCERTAIN.
Journal previamente APPLIED nunca e apresentado como efeito no grafo recem-carregado;
nao reexecuta. Timeoutqueued cancela sem efeito,timeoutRUNNING permanece incerto,
nao termina EDT/threadoriginal. Executing-set impede instalar contexto enquanto
handler antigo nao retornar,mesmo apos invalidacao. Resultado tardio nao desfaz
uncertainty;falha no journal apos efeito suspende contexto. Interrupcao do caller
e restaurada apos tentativa de finalizar journal,sem abandono silencioso de fila.

LegacyFinanceAdapter fingerprintado best.ah/C,perfilconhecido,somente clubehumano
exato e ledgerjZtrue. Planoweak/one-shot,expectedsnapshot14camposnumericos+saldo,
roots/ledgeridentidade antes/depois. Usa v(int,int)categoria6 ou w(int,int)categoria7
ORIGINAIS;nao escreve fields por reflexao e nao usa setter e(long) para rollback.
Fields privados apenas lidos. CodigosRAW6/7 NAO sao categorias de patrocinio/salario
semanticamente validadas;nenhuma API/capability/mod habilitado. Plano/digest nonce
temporario,nao request persistente recriavel por mod.

FinanceDelta valida amountintpositivo,saldolong,acumuladorint,totaislong e IADD
dL+dM antes de conversao long;debito nao cria divida neste recorte. Sobrescrita
por plano stale recusada antes da rotina original;falhaaposinvoke e incerta,nao
promessa de transacao/reversao. Threadlegada pode ignorar EDT;postcheck detecta
divergencia observavel,nao prova ausencia de corrida ou grafo inteiro consistente.

Build final em finance-pipeline-reviewed.log:29dispatcherchecks+15aritmetica,
mais suites existentes. Testes proprios incluem owner/binding/opdigest mismatches,
duplicata/restart/recordedonly,staleRoots,journalCAS/faultbeforeaftercommit,
EDTrefusal/interrupcao,queued/runningtimeout,latecallback e recusa de contexto
enquanto handleroriginal simulado continua executando. Wrapper financeiro teve
erro inicial $HOME read-only do PowerShell antes de iniciar runtime;corrigido
para loaderHome e repetido em fixturesnovas,sem modificar arquivos de usuario.

NOVO teste original test-finance-commands.ps1 aprovado:20checks em fixture
Temp/opencode/vbrasfoot-finance-fd003f07f3e049f594dc05905e71bef8. EXE/savecopiados
porhash,marker/cwdfresh,frameSINTETICO,loadoriginal. Credito123,debit23,credito5
viajournal/gate/adapteroriginais produziram exatamente saldo+105 e ledger previsto.
Oferta de op errada para handler negada sem journal,duplicata nao repetida,plano
stale nao aplicado,negativo recusado,refsantigas recusadas apos reload. Writer
original gravou finance-output.s22/sbck NOVOS com bytesiguais;loadoriginal reabriu,
saldo e TODOS os14camposnumericos de ledger iguais aos esperados,contagemclubes
preservada. Saveinput e EXEorigem conferidos inalterados. Nao inicia main/joga/
cria carreira,nem testa EXEstandalone/aceiteUI/semantica patrocinio/roundtrip de
todo o grafo. ContextoUUID/binding sao ARTIFICIAIS da fixture,nao associacao real.

132checks I/O/schema originais anteriores tambem aprovados em fixture privada
vbrasfoot-hook-io-0e352f6ffcb442fc933f4c35d07c923b. SDK-only5projetos/hashes/
allowlist/negative/JSON aprovado fora checkout fixture40b7fb76407d4fb8abb774301f03f6ea,
archive dist/sdk-0.9.3-journal-dispatch.zip local. Nada publicado/tagueado ou
instalado;loader do usuario continua0.9.3SHA3662113df6f2277c7c3ab05d3e9bb0143664a707dcf798dbf3109f8b235004f7.
Sem alterar saves/mods instalados ou gerar mods de patrocinio/negociacao.

Continuam pendentes associacao aceita de carreira/save/revisao/copy/rename/restore,
reconciliacao apos crash/save,journalcompactacao/eventoscalendario,categorias
financeiras/UI/interceptacao de mercado,APIs publicas/autoriaexterna. Nao declarar
o pedido inteiro concluido com esta entrega limitada.

## Bases internas de persistencia e comandos — sem novos mods ou API publica

Pedido atual e ampliar loader para permitir patrocinio/negociacao futura,NAO
gerar esses mods. Adicionados CareerSidecarStore,CareerCommandGate e
CareerCommandJournal internos. Nenhum ligado a ModContext,Main ou callbacks do
jogo; API/capabilities publicas e versao instalada0.9.3 inalteradas. Validator
testa explicitamente que career.sidecars/gameplay.write continuam unsupported.

Sidecar:envelopes versionados/owner/hash/schema/revision,payload destacado,
HEADcurrent/previous,CAS token,lockfile entre processos,forcefiles/atomicmove
sem fallback,quotas1MiBpayload/128files/16MiBnamespace,previous/recovery explicito
sem eleger orfaos. Key de UUIDs/binding fornecida explicitamente por caller futuro,
nao gera identidade de carreira do hash/path. SaveRevision mismatch nao migra
associacao. Nenhum save e aberto pelo store. Corruptos/orfaos preservados.

Gate:owner/op/contexto confiavel externo,roots fracas,permit uma vez/EDT,
256receipts sem eviction,64owners,revoke/invalidate/shutdown;efeito incerto
suspende contexto e nao e rollback. Journal:intent persistido PREPARED antes da
Lease,read mostra incerteza,lease so na instancia original,sem reemissao apos
restart;CAS/terminal nao sobrescrito,32intents sem eviction. Pending/unknown
bloqueiam novos intents. Nao ha dispatcher nem reconciliador com save existente.

Build final write-foundations-final.log aprovado:60checks store,32gate,28journal
e suites anteriores. Fixture sidecar-core-8685777742727693978 e journal
command-journal-17907877287666125179 sob Temp/opencode. Teste store inclui outro
processo Java PROPRIO com lock,race inicial entre writers,corruptHEAD/recordmissing,
ownership mismatch,previous explicito,CAS/quotas,EDTrefused,erros injetados em
3etapas antes/depois do commit,unknownfilepreserved e junction Windows recusada
sem alterar alvo. Symlink nao criado por permissao nesta maquina:SKIP explicito,
nao contado como aceito. Falhas injetadas NAO sao crash/powerloss reais nem prova
de fsync de diretorio/transacao atomica save+sidecar. Checagens de paths nao sao
sandbox contra races hostis/hardlinks/mods Java maliciosos. Detalhes em
roadmap/LOADER-WRITE-FOUNDATIONS.md; gates restantes explicitamente abertos.

132checks I/O/schema originais aprovados em fixture privada
Temp/opencode/vbrasfoot-hook-io-5e03f2c2b6624eabb289b5fdee05860d,nao executa
credito/debito/transferencia nem valida reabertura de nova escrita financeira.
SDK-only5projetos/hashes/allowlist/negative/JSON aprovado fora checkout fixture
33ba379fadc449b4abc70984442f4ba6;archive local dist/sdk-0.9.3-write-foundations.zip.
Sem tag/release/publicacao,sem instalacao/alteracao dos arquivos do jogo/mods.

Mapa novo read-only map-gameplay-boundaries.ps1:30checks classificador,
1038classes/47refs no EXE SHA conhecido,TSVprivado build/gameplay-boundaries-core-final.tsv.
Javap confirma kb->saldo long,e(long) setter puro,v/w(int,int) alteram saldo e
condicionalmente C.h/i ledger;f(long) subtrai e pode atualizar C.b. Categoria e
overflow de acumuladores int ainda nao resolvidos,nenhum comando publico liberado.
az tem7callers compartilhados,ah.a/b(F,bool)10sites candidatos em best.l,nao
prova commit/interceptacao de transferencia. Artefatos privados nao redistribuidos.

## 0.9.3 — reconhecimento online estrito, nao prontidao

Reutilizada rodada real0.9.2 0fad05d7-aaa6-487d-8da4-67c144ea5ccc:preparacao
completa/entry1 e callback da mesma instancia/attempt2,bound1/unbound0,NO_OPTION1,
AB/oq retornaram,roots/generation4/thread37 estaveis.9events entry/6dialog/
78progress,V1/ap13/at12/ax12,painel2callreturn,VRETURN;sem drop/rejected/outside.
GuardUNKNOWN/healthytrue,io0/0,sem save/pending/stale,dispatcher3/3. Erro de equipe
E8CC1702 e hashforge_can.ban preexistente preservados. Registrado tambem na PR41.

Implementada maquina de estados separada em CreationPathQualification e integrada
aos hooks ja existentes via CreationEntryTracker. NAO adiciona sites de bytecode/transformacoes
ou altera schema legado. Ordem online inclui binding fraca/primeira tentativa EDT,
oq/pl/choice1/AB/az/V/stackLIFO38entradas,maxdepth38,painel2request/return,retornos
V/AB/pl/oq. Receiver so requerido nas entradas; roots/epoch/geracao verificadas em
todos os sinais. Outro shape completo e UNSUPPORTED_SHAPE,nao falha inventada do
jogo. Formato creation-qualification.properties statusRUNNING/REJECTED/PATH_RETURNED,
reason de primeira falha,limites2048sinais/64depth/64ativos/128concluidos,drop e
capacityRejected explicitos. Snapshot detached inclui incompletos sem esperar EDT.

PATH_RETURNED e historico de um percurso reconhecido,nao CareerReady/ACTIVE,
identidade persistente ou grafo/quiescencia/durabilidade assegurados. Sem mudanca
em CareerLifecycle,eventos/capabilities/metodos SDK. Interferencia observada de
save/load/reset/root/scheduler/run e overlap,reentrada/progressao fora da thread
rejeitam conservadoramente ativos;nao provam cobertura de todos os schedulers/
reflexao/JNI. Binding pode continuar diagnosticamente bound,mas qualificador e
single-use inclusive apos tentativa de validacao sem pl. A UI nao e bloqueada.

281checks da maquina estrita e289integracao aprovados em
qualification-final-build.log:remove/duplica cada sinal,No vsYes/cancel/
close,failed em fronteiras,aa/AC/pn,outropainel,receiver,roots/geracao em cada sinal,
bounds,eventringeviction,captureincompleta/reset,reuso e65threads proprias para
limite64ativos. Bridge real na EDT com objetos proprios reconhece o shape e deixa
guardinteiro igual antes/depois; hooks reais de save/async/root recusam qualificacao.
Isso e teste SINTETICO,nao execucao de nova carreira original.

132checks I/O/schema/detalhes originais aprovados com fonte final0.9.3 em
Temp/opencode/vbrasfoot-hook-io-31b1b75fb71646d6a55bc4b4d50a9cc3,log privado
qualification-reviewed-original.log. Nao cria carreira nem exercita os markers
de qualificacao na UI original; garante apenas os recortes de regressao existentes.

Relatorios anteriores tem namespaces/sequencias locais distintas; NAO foram
reclassificados retroativamente como PATH_RETURNED. Corroboracao online original
nova permanece pendente; nenhuma nova criacao/partida/save solicitada ao usuario
para esta entrega e nenhuma feita automaticamente pelo agente.

Portablefixture352fd733fa5a495892312dc14604608c e SDK-only5projetos/hashes/allowlist/
negative/JSON fora checkout fixture2dd60da3902f4c0d835590488122b4d9 aprovados sob
Temp/opencode;UiSmokeTest comparador sintetico aprovado. Pacotes locais
dist/portable-0.9.3-qualification-reviewed e dist/sdk-0.9.3-qualification-reviewed.zip,
sem tag/release/upload ou distribuicao de jogo/save/bytecode proprietario.

## 0.9.2 — vinculo fraco entre preparacao e callback de criacao

Nova investigacao reutilizou a rodada4da73e9a-ff85-4272-963e-afea042d9551:
dialogo real retorno1/NO_OPTION,resultKnowntrue,6events oq/pl/AB com retornos,
generation4/thread37/rootsMatchedtrue,dropped/rejected/outside0;progress78 com
V1/ap13/at12/ax12 entradas/retornos,aaausente,painel2callrequest39/callreturn40,
VRETURN78,tasks0. GuardUNKNOWN,healthytrue,io0/0,sem save/pending/stale;
dispatcher3/3. Stderr E8CC1702 preexistente; hashforge_can.ban conferido igual
b7c0769f76e70c1cfd66b0bb34faa384558f447d4c70ab4e4779d09fa730adff. Nenhum
arquivo do jogo/mod/save alterado ao inspecionar reports. Evidencia tambem PR40.

Javap privado local identificou hU.oq->p.dg->aY(1),assignment best.S.gL da
instancia bV e callback cc->bV.oq. Mapper read-only ampliado:45checks sinteticos,
1038classes/86referencias no EXE de SHA conhecido; p.dg tambem tem caller forms.p,
portanto dg sozinho NAO e origem exclusiva. Corrigida interpretacao anterior:
aY(2) retorna null,nao objeto de elenco. Marker PANEL_RETURN significa retorno
da chamada; UI ainda pode ter revalidate/repaint pendentes e ap tem trabalho
apos o site. Nem construction,RETURN ou painel nao-nulo certificam prontidao.

Implementado CreationEntryTransformer em a.hU/best.S fingerprintados,metodos/
access/descriptors/sites exatos; soma marker receiver na entrada bV.oq sem
ler locals no retorno. Compoe sondas existentes e preserva fieldwrite/stack/
null/Throwable/campos/metodos/schema/hierarquia. Probe opt-in17/21,memoria apenas.
Tracker liga instancia fraca/roots/epoch/generation atraves de callbacks;
64bindings/ring512/8frames por thread,counters explicitos. Falha,etapa ausente,
reentrada/overflow,roots trocadas,painel diferente/reset nao fabricam binding.
Mesmo painel reatribuido fora da preparacao revoga ligacao anterior. Sem getters/
Swing/I/O/callback ou promocao do guard. Report creation-entry.properties separado;
entry/attempt nao sao IDs de outros relatorios nem identidade de carreira.

27tracker+15bytecode proprio aprovados em entry-reviewed-portable-build.log:
estagios normais/missing/failure,identidade do painel,geracao/roots,cap/ring,
reentrada/overflow/reset/cross-thread,guard inalterado;schema/fingerprint/sites,
nullretorno/caller effects e mesmoThrowable propagado.132checks originais de
I/O/schema aprovados, incluindo a.hU/best.S definidos/verificados e comparados
com originais em fixture privada8a44e478713547a88fa7b28416126d23 sob Temp/opencode.
NAO chama hU.oq/dg/aY para criar carreira; positivo novo de binding UI pendente.

Portable0.9.2 fixture1619d160f76346ef92e88516aa04660d e SDK-only5projetos/
hashes/allowlist/negative/JSON fora checkout fixture6b8dab2ee9124341b71f442863ff2c27
aprovados sob Temp/opencode. UiSmokeTest comparador sintetico aprovado.
Pacotes locais dist/portable-0.9.2-entry-reviewed e dist/sdk-0.9.2-entry-reviewed.zip,
sem tag/release/upload ou distribuicao de TSV/javap/bytecode/save proprietario.
APImajor1/metodos/capabilities publicos inalterados. Readiness/unload/P3/eventos
de partida/calendario/gameplay e autoria externa continuam gates separados.

## 0.9.1 — diagnostico bruto do dialogo de torneio

Implementado marcador fingerprintado oq/pl/showConfirmDialog em a.bV compondo
com AB existente. Nenhum campo/metodo/schema novo, nenhum int/Throwable original
substituido. Ring512,8frames,roots fracas,epoch/geracao/thread/parent e counters
explicitos. Sem callback/Swing/I/O no tracker, sem eventos SDK/readiness/sidecars.
ResultKnown separa retorno de dialogo dos placeholders. Frames nao sao IDs da
trace async; nao inferir ligacao entre namespaces de IDs.

Build/suites:15tracker e37bytecode sinteticos aprovados em dialog-build.log,
incluindo0/1/2/-1/intMin,efeitos originais,preservacao da excecao de dialogo/AB,
sem resultado/retorno inventado em falha,shape/fingerprint/sitealterado,limites,
reset/cross-thread/stale-generation/mismatch e guard inalterado. Fixture propria
usa marker de AB explicito; teste separado confirma bridge AB real encaminha
ao tracker sem alterar guard. Pequenos erros de catch de report/arvore da fixture
detectados e corrigidos antes de empacotar, nenhuma versao falha instalada.

130checks I/O/schema/detalhes ORIGINAL aprovados em fixture privada
Temp/opencode/vbrasfoot-hook-io-db78636cb6f74a389a03d53939ba5a77. Definicao e
verificacao de a.bV original transformado/schema aprovadas; NAO chama oq/pl nem
dialogos originais. Positivo UI novo permanece pendente; nao declarar nova carreira
pronta nem pedir variantes repetidas sem examinar o primeiro report.

Pacotes finais0.9.1 locais aprovados: dist/portable-0.9.1-dialog-reviewed e
dist/sdk-0.9.1-dialog-reviewed.zip. Portable fixture509f89fc5c8446dca71c09c9de07ba80
e SDK-only5projetos/allowlist/hashes/JSON/negative fora do checkout fixture
197754dd05534481bb2f58fae3ebaf98 sob Temp/opencode. UiSmokeTest sintetico do
comparador aprovado. Sem release/tag/upload ou alteracao dos mods instalados.

## 18/10/2026 — SDK0.9.0, primeira entrega do pedido de variedade

Contrato aditivo scopedPlayerDetails/capability query.player.details, APImajor1,
Player anterior intocado. Sete atributos brutos e Optional.empty quando a carreira
desabilita habilidades individuais. Sem salarios/energia/contratos inferidos.
Mapeamento estatico por rotulos da UI a.eg e getters diretos best.F com javap
do JDK, alvo SHA original conhecido; REA JVM indisponivel no contexto anterior.
Bytecode/strings/saves privados nao publicados. Nenhum metodo de gameplay chamado
pelas consultas. Listas imutaveis, EDT, escopo/handle e configuracao verificados;
isso nao garante atomicidade do grafo contra threads legadas.

Build final aprovado (player-details-final-build.log):10checks de snapshots,
17checks do scout SDK-only incluindo painel sintetico/headless, lifecycle/revocation
e suites existentes. 130checks originais I/O/schema/detalhes aprovados em
Temp/opencode/vbrasfoot-hook-io-455c7bd22fb0498baeaa61e0dde48f29: compara sete
campos aos getters originais, paginacao, bounds e handle stale apos reload.
Teste original trabalha em fixture privada, nao em saves instalados. Nao conta
como execucao de partida/nova carreira ou aceite visual humano do scout.

SDK0.9.0 local/hashes/allowlist/5projetos compilados fora do checkout aprovados em
Temp/opencode/vbrasfoot-sdk-author-cc1f50dc9dfa42beae76ea120446934b.
Portable0.9.0/regressao instalador aprovados em
Temp/opencode/vbrasfoot-install-897f5a9e2ad647fc89b82e715af1c268.
UiSmokeTest sintetico do comparador aprovado. Nenhuma mudanca visual no comparador.
Pacotes dist/sdk-0.9.0-scout-final.zip e dist/portable-0.9.0-scout-final sao
locais/experimentais, sem release/tag/publicacao. Plano completo e gates restantes
em roadmap/MOD-VARIETY-PLAN.md; esta entrega NAO implementa persistencia por
carreira, novos eventos de calendario/partida ou escrita de gameplay.

Reempacotamento final incluindo LICENSE no scout/JAR/allowlist aprovado:
dist/sdk-0.9.0-reviewed.zip e dist/portable-0.9.0-reviewed;
fixtures SDK7cc76478908f49f58b189077e6e0d571 e portable17bb3d905f90414eac63636da1e0efb6
sob Temp/opencode. Nenhum pacote anterior sobrescrito.

### Evidencia real0.8.2 incorporada

Mapa subsequente somente leitura de chamadores:37checks sinteticos,
1038classes/81referencias, build/creation-origin-callers.tsv privado. Confirma
calls bV.oq->pl->AB e aa->cR.pl/AC,alem de cU->pn. Javap de bV.pl mostra
que0pede painel10 e nao-zero chama AB; ainda NAO capturamos valor do dialogo,
entao nao inferir opcao humana. Nenhum jogo/hook/sidecar executado por esse mapa.
Invariantes de persistencia propostos separadamente, nao anunciar P3 implementado.

Sessao bcfd9a1f-89e4-4ac1-9e55-670522992ba1:78markers/drop0/outside0/rejected0/
unbound0,taskcount0,origin1/generation4/thread37/rootsMatchedtrue. V1,ap13,at12,
ax12 entradas/retornos; aa nao observado. ap PANEL_REQUEST2 seq39, PANEL_RETURN
seq40, V RETURNseq78, nenhumFAILED. Isso prova chamadas/retornos, nao prontidao
da UI/grafo/carreira. UNKNOWN/healthytrue,io0/0,sem saves/pending/stale nesta
sessao,dispatcher3/3sem perdas. Stderr contem E8CC1702 de equipe preexistente.
Receiver so conhecido na entrada; retornos nao inventam matched. Usuario apenas
disse feito; nenhuma variante/opcao amistoso inferida. Sem pedir repeticao antes
de marker causal novo concreto. Registro anterior tambem na PR37.

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

## P4/0.8.1 — sonda causal de tarefas candidatas

Pedido "faca" autorizou implementar sonda do mapa anterior. AsyncCreationTracker
associa contexto AB/AC/pn a task construida e seu run por identidade fraca,
origem temporaria/label/parent e roots/geracao. Contexto causal pode atravessar
threads e descendentes; task pode iniciar antes de oR retornar. Sem getters/
Swing/I/O/mod callbacks no tracker e sem ativar guard ou novos eventos SDK.
Limites64tasks vivos/128reports/8frames; overflow/desconhecido/duplicata/stale
recusados. Report creation-async.properties privado com flags primitivas, nao
grafo/objetos/nomes de save. Sessao reset invalida epoch, sem persistencia.

AsyncCreationTransformer guardSHA cR/cS/hk/ho e1method/return/sitecounts por
metodo. cR combina candidato existente e oR em memoria apos verificar original.
Nao altera delay/timer/cancel/thread/save/schema; falhas repropagadas. Tarefas
fora de candidato ativo nao ganham origem por suposicao (unbound). Candidatos
podem ser usados em temporada: origem AB/AC/pn nao prova nova carreira exclusiva.
Dois nulls de raizes ou return de Timer nao viram pronta/unload/sidecar/P3.

19checks tracker proprios: run em thread independente antes de schedule-return,
originLabel e parent de descendente apos return do candidato, duplicata, roots/
generationchange, schedule/run failure, desconhecido/reset,64live/128reports,
no-objects DTO e lifecycle UNKNOWN intacto.7checks bytecode sintetico: fingerprint,
normal/exception scheduler/run, shape/sites divergentes.44guard checks agora
incluem reportasync vazio no close sem tasks inventadas. Build/suites aprovados,
UI smoke separado comparador/content/bounds/cleanup aprovado. Primeira tentativa
de ler ZIP PE por .NET foi recusada; reutilizados hashes exatos do scanner Java
do mapa anterior, sem reinterpretar falha como hash ausente/alterar EXE.

75checks original I/O/schema aprovados em
Temp/opencode/vbrasfoot-hook-io-98775e21553348faa4ad03b609386c12:
schemas originais vs transformados dos4novos alvos (cR ja testado antes),
load/save/fallback/closefailure/I/O existentes preservados. Teste DEFINE/VERIFY
essas classes, nao executa oR/run/criacao originais, nao UI/gameplay delas.
REA provider JVM indisponivel anteriormente registrado; nao engine/setup novo.

Portable0.8.1 build/teste fixture aprovados:
Temp/opencode/vbrasfoot-install-a291d32a37c14caca6e9662a88c908f4.
SDK-only0.8.1 zip/4projetos API-only/JSON/hashes/allowlist/negative aprovados fora
checkout: Temp/opencode/vbrasfoot-sdk-author-615cee32770b4092ada059ba98bd24f9.
Pacotes dist/portable-0.8.1-async-final e dist/sdk-0.8.1-async-final.zip locais,
sem tag/upload/release. Metodos/capabilities do SDK nao ampliados nesta patch.

Proximo gate humano apos instalacao/preflight: criar1carreira com nome inedito na
copia Documents em --probe-career-hooks; informar ramo/opcoes e acesso ao elenco,
fechar normalmente sem jogar/forcar save. Original pode autosalvar; nao prometer
nenhum I/O. Primeiro observar origin/taskchain, depois decidir variante amistoso/
cancelamento/temporada. Nao promover lifecycle nem repetir carreiras sem report.

## Criacao real0.8.1 — ramo AB sem tarefas capturadas

Usuario confirmou feito apos criar carreira na copia. Sessao privada
7124ea6e-777a-42cd-b080-11be81238907: AB start/return1,4steps az/V,
roots/listas/gen estaveis,4153clubes/1humano. az indice1->4/data2->5Jan2022,
V4->22/data5->23Jan. AC/pn nao observados. Async count/rejected/unbound0:
nao ha evidencia positiva das tasks selecionadas; isso nao prova ausencia global
de tarefas/continuacoes. GuardUNKNOWN generation4/healthytrue, io counts0/0,
sem pendentes/stale,unboundSave1. Metadata/primary/stream/backup/return observados
com streamFailurefalse, mas bound/completesequencefalse pela criacao nao qualificada.
Dispatcher4/4 sem perdas. Stderr NAO vazio: erro preexistente E8CC1702 na leitura
de equipe best.U.w; forge_can.ban SHAb7c0769f76e70c1cfd66b0bb34faa384558f447d4c70ab4e4779d09fa730adff
conferido igual ao historico e preservado. Loader1694541b... conforme instalado.
Nenhuma opcao de amistoso inferida do feito; nenhum save/mod alterado pelo agente.

## P4/0.8.2 — trace sincronica por contexto, nao pronta

CreationProgressTransformer observa best.f V/aa/ap/at/ax somente em memoria
com probe17/21, SHA classe conhecido e method/access/descriptor/RETURN/panelsites
exatos. Sem campo/metodo/assinatura/hierarquia novo; root serializada exige
schema/I/O original. Preserva int do panel request e JPanel de retorno na stack,
legacy handlers e Throwable. Capture causal apenas sob AB/AC/pn/task associada,
ring512sequencia/threadID/origin/parent/label/stage/panel/geracao; eviccoes/outside
explicitos. Task e trace+counters capturados atomicamente; nao consulta Swing/
getters/I/O ou altera lifecycle. Nao interpretar painel2/5/6 como pronta.

Primeiro teste original detectou VerifyError: aa stackmap dropa local0 para TOP
antes de RETURN. Corrigido antes de empacotar/instalar: so ENTER le this; outros
markers/catch nao dependem de locals descartados, frame catch vazio. Report
receiverKnown=false/matched=false nesses pontos e rootsMatched separado. Nao
fabricar receiver a partir da raiz estatica. Pequeno erro de aridade de fixture
tambem detectado/corrigido antes da suite final. Logs privados sync-progression-
original.log/sync-final-build.log ignorados; nenhuma versao quebrada instalada.

27tracker checks e11bytecode proprio aprovados: contexto/unbound/stale/caps/reset/
receiver desconhecido/captura detached,normal/exception/panel constructor failure,
nenhum PANEL_RETURN inventado em falha, schema/sitio alterado/fingerprint.
Build/suites e UiSmokeTest separados aprovados (sync-final-build/ui.log).
76I/O/schema original checks aprovados em
Temp/opencode/vbrasfoot-hook-io-2d4a99a326344fd7aac93a22b355d7b6,
incluindo best.f original vs transformado e save/reload/contagens/fallback/
metadados/close existentes preservados. Verificacao de classes nao prova execucao
das novas rotas V/aa/ap/at/ax na UI; essa corroboracao ainda pendente.
Nenhum arquivo proprietario/TSV/bytecode/save publicado; Profile/EXE preservados.
APImajor1/metodos/capabilities inalterados, sem sidecars/identidade/unload/pronta/
durabilidade/atomicidade/gameplay por suposicao.

Portable0.8.2 build/regressao fixture aprovados:
Temp/opencode/vbrasfoot-install-64aa01142b30404ea714d268dc946054.
SDK0.8.2 ZIP/API-only4projetos/hashes/allowlist/negative/JSON aprovados fora
checkout: Temp/opencode/vbrasfoot-sdk-author-123d92385efa4421a7bfb48bb87a4ef2.
dist/portable-0.8.2-sync-final e dist/sdk-0.8.2-sync-final.zip sao pacotes locais,
sem release/tag/upload/publicacao. UI nova trace pendente antes de qualquer aceite.
