# Argumentos numericos emitidos pelo Kryo original

Incremento experimental apos OPERATION-SERIALIZER-CAPTURE.md. Nao e INCLUDED,
reconciliacao de APPLIED/UNKNOWN, lease ou permissao publica de escrita.

## Rota identificada e fronteira observada

Inspecao estatica com javap do Kryo4.0.2 extraido da fixture privada original
identificou seis classes CachedField para int/long: UnsafeCacheFields$Unsafe*,
AsmCacheFields$Asm* e ObjectField$Object*. Cada write(Output,Object) tem uma chamada
Output.writeInt/writeLong varint e outra fixa. A rota Unsafe le via Unsafe.getInt/
getLong; a ASM usa FieldAccess; a Object usa Field.getInt/getLong. Isso nao observa
execucao. A fixture original final observou efetivamente **UnsafeIntField e
UnsafeLongField** para os15campos financeiros. Nao deduzir a rota de outro Java/
ambiente apenas pelo nome/visibilidade do campo ou disponibilidade de Unsafe.

FinanceFieldTransformer exige Java17/21, SHA256 exato de cada uma das seis classes
do artefato fornecido e inventario exato dos dois sites. LegacyRuntime transforma
somente em probe opt-in; recusa falha sem fallback. Sem alterar campos, metodos,
construtores, read/copy, nomes, serialVersionUID, serializer selecionado ou schema.
Nenhuma classe proprietaria ou da biblioteca e adicionada ao repositorio/SDK.

Em cada site, scratch locals preservam Output/argumento/flag EXATOS. A chamada
original ocorre com os mesmos argumentos. SOMENTE apos retorno, callback recebe
cachedField/receiver/Output/argumento numerico, sem reler o valor financeiro. O
retorno int do varint permanece na stack; callback nao troca resultado original.
Falha de Output nao executa esse callback. Nao capturamos bytes/ranges individuais:
retorno pode significar buffer em memoria, ainda nao arquivo duravel.

## Vinculo e recusa

HookProbe aceita somente durante uma root em andamento da janela do writer atual,
com receiver best.ah/best.C e classloaders de receiver/cachedField/Output iguais.
Output.getOutputStream deve ser o MESMO SaveOutputTrace daquele writer. Output
aninhado/stream diferente ou falha de introspeccao marca cobertura unavailable.
Campo vem do getField publico da classe base CachedField, nao de getters de saldo.
Captura nao le arquivo, nao espera EDT nem chama callbacks de mods.

Plan.emitted compara identidade EXATA de clube/ledger e classe declaradora/tipo/
nome com nb e os14campos mapeados. Argumento e comparado ao expected daquela
operacao. Outros objetos nao sao reencontrados por nome/ID/valor. Registro continua
dependente do ExecutionPermit fresco, weak target e mesmo gate/interlock do PR60.

Tracker guarda somente status, nomes limitados de campo/rota e contadores, NAO
valores numericos/objetos/Field/Output. Ate32operacoes x15campos x6rotas, sem nova
retencao forte de grafo/runtime. Sampler interno roda fora do lock do tracker;
commit verifica revision/closed/one-shot novamente. Off-EDT/target perdido/falha
recusa. Invalidation/close interrompe a janela; finish congela o resultado.

Estados independentes das amostras de root:
- MISSING: nenhum argumento relevante observado, inclusive se roots MATCHED;
- PARTIAL: menos de15campos distintos, sem inferir os ausentes;
- EXPECTED_NUMERIC_ARGUMENTS_OBSERVED:15argumentos distintos esperados observados;
- VALUE_MISMATCH: ao menos um argumento diverge do expected;
- AMBIGUOUS: campo repetido, mesmo quando valor igual; nao sobrescrever;
- UNAVAILABLE: falha de alvo/callback/stream/thread/rota.

WrittenSaveProvenance conserva isso no Capture existente, apenas quando toda a
sequencia original e qualificada. Report exporta status/count/rotas, nao valores,
keys/payloads/nomes/paths. operationInclusionProven=false/writeAuthority=false.
Nenhum contrato/journal/witness e reancorado ou reescrito por esta captura.

## Evidencia e limites

12checks sinteticos com classes GERADAS proprias: roots MATCHED sem argumentos
continua MISSING, parcial,15unicos, receiver irrelevante, rotas imutaveis,
valor divergente, duplicata, off-EDT, falha, rota desconhecida e invalidacao.
24checks de root capture/40writer/report preservados; report de root-only nao
inventa emissao numerica.

Fixture original final:104checks, incluindo24casos de bytes/overflow das seis
variantes (fixo/varint, int/long negativo),6recusas de fingerprint modificado,
15argumentos da ultima operacao e VALUE_MISMATCH nas duas anteriores. As rotas
reais foram UnsafeIntField/UnsafeLongField. Save/reload original preserva saldo/
ledger e mantem historico bloqueado. Fixture privada em
Temp/opencode/finance-numeric-original-2cb87a76bd674d4b846e657a5b320046/
final-runtime-test.log. Fontes finais compiladas isoladamente; copia da fixture
privada anterior, nenhum acesso de escrita ao jogo/save pessoal.

**Ainda falta** capturar a codificacao/identidade da aresta clube.nx->ledger,
verificar referencias/custom serializers/transients e os mecanismos pertinentes
de mutacao nao-direta. Observar Unsafe LEITURA no serializer nao instrumenta
Unsafe ESCRITA, decoder, copy, reflexao ou Java arbitrario.15argumentos coincidentes
nao provam causalidade de uma operacao nem isolamento entre leitura e gravacao.
Metadados CachedField e Output nao tem protecao contra mutacao Java hostil.
Captura continua em memoria; shutdown report nao e witness duravel/crash-safe.
Persistencia por UUID ligada ao hash do writer, interrupcoes, restore/copia/save-as,
UI/standalone e permissao publica permanecem pendentes. Sem novo teste humano.
