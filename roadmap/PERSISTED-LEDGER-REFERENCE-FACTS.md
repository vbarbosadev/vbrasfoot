# Fatos persistidos da tabela de referencias

Incremento apos a trilha live da PR70. Nao altera schema original Kryo,save,EXE,
API major1,loader instalado0.9.3 ou comparator. Nenhum mod/release/tag/instalacao.

## Contrato e compatibilidade

FinanceSerializationEvidence.Operation tem Optional<FinanceReferenceTableEvidence>.
Ausente significa NAO REGISTRADO;presente version1carrega Summary escalar imutavel:
resolucao,newMarkers,existingMarkers,contentReturns,usesOutsideFieldCall,resets,
fieldCalls,supportedResolver. MISSING capturado difere de ausencia historica.
resets conta entradas de epoca;em UNAVAILABLE pode incluir reset incompleto,
nao deve ser reinterpretado como contagem de conclusoes normais.
Nao persiste IDs runtime,objetos,paths,campos financeiros ou identidade de clubes.

Witness store le schemas1/2/3/4.1continua execution-only;2sintetiza manifest0;
3preserva manifests0/1;nenhum deles possui fatos de tabela.4adiciona por operacao
marker Optional estrito e envelope versionado. Container4pode misturar fatos
ausentes/version1;deve conter ao menos um envelope novo. Nenhuma leitura migra
HEAD ou inventa fatos de runtime para historico antigo. Arm/complete conserva o
schema minimo suficiente sem descartar envelopes. Codec2/3recusa downgrade com
fatos novos. Readers antigos recusam4,sem fallback que perca observacoes.

Coleta explicita existente usa UUID exato do capture,contrato,receipt e witness
do MESMO namespace. Capture com traces nao vazias exige inventario de UUIDs
completo,unico e limitado;nao junta por posicao ou saldo. Novo resumo e persistido
somente com a observacao live que o gerou. Confirmacao repetida compara tambem
fatos imutaveis;nao substitui dados de observacao antiga por novos.

## Validacao e diagnostico

Versao/status/presenca desconhecidos,contagens negativas,overflow/budget128,
outside maior que eventos de marcador/corpo e resolucoes positivas sem contagens
necessarias sao recusados. Encoding positivo exige field-call unica e edge
observada;NEW/PRIOR requerem resolver suportado. UNAVAILABLE/CAPACITY preservam
falha e contagens parciais,sem promover uma amostra truncada a positiva.

Resumos agregados NAO permitem reconstruir eventos/epocas ou provar que bytes
individuais correspondem a mutacao. Validacao estrutural nao e revalidacao causal.
Os IDs descartados nao viram chave duravel;qualificacao continua sendo uma
observacao do writer live fingerprinted,no instante/escopo originalmente capturado.

Revisao pura acrescenta REFERENCE_TABLE_NOT_RECORDED para ausencia historica,
REFERENCE_TABLE_ENCODING_NOT_QUALIFIED para status sem new/prior observado e
ADDITIONAL_LEDGER_USES_OBSERVED quando outside>0. O contador e de eventos,nao de
aliases. Mesmo NEW positivo e outside0deixam REFERENCE_TABLE_AND_ALIASES_UNPROVEN,
MUTATION_EXCLUSIVITY_UNPROVEN e MULTI_ROOT_RECOVERY_UNPROVEN. Todos os reports
permanecem blocking,operationInclusionProven=false/writeAuthority=false.

CAS/record/HEAD,quota sem eviction,previous record,OUTCOME_UNKNOWN apos commit e
verificacoes de arquivo/histories existentes sao reutilizados. Excecoes controladas
nao simulam crash/power-loss. APPLIED/unsupported UNKNOWN continuam bloqueados;
old key nunca recebe lease nova. Nenhuma coleta automatica Main/permissao publica.

## Validacao

43checks proprios cobrem facts/codec2/3/4/absence/contradicoes/gaps.89checks duraveis
incluem interrupcoes2->4e3->4,sem promover orfaos ou repetir efeito.
Suite final tambem passou18scalar trace/33manifest/23gaps/37numeric-reference/
28root/43writer-report/69witness/29dispatcher em
Temp/opencode/reference-facts-final-3e009abb37104dd7bbcd014480e64799.

280checks financeiros originais aprovados:os3UUIDs persistem envelope1/schema4,
reopen mantem resumo exato e gaps causais. Bytes/aliases/roundtrip mantidos;
EXE privado hash2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c.
Evidencia:Temp/opencode/reference-facts-original-75eb8ae2cc494c288da312e01254ef84/runtime-test.log.
Esta fixture compilou40checks de facts;os3checks adicionais de alias review e
future wire version/status foram depois aprovados na suite final43acima.
Wrappers finais aprovados:159I/O originais em build/reference-facts-original-io.log,
fixture Temp/opencode/vbrasfoot-hook-io-b7a0b84dcb504d16aaf2ba2bf14d0a80;
SDK ZIP isolado/hashes/allowlist/SDK-only authoring/corrupcao recusada em
build/reference-facts-sdk-test.log,fixture
Temp/opencode/vbrasfoot-sdk-author-7eea45f7c47f4b36846a704c641f1c82.
CI17/21 e SHA exato seguem obrigatorios.Nao e aceite Main/UI/EXE standalone.

## Proximo elo

Nao e necessario repetir aceite humano de load/painel. Proximo objetivo viavel:
inspecao read-only de revisoes e coordenacao multi-root/recovery,ou outra lacuna
independente sem fingir que Java arbitrario tem exclusividade de mutacao.
UI/standalone,calendario e negociacao continuam pendentes.
