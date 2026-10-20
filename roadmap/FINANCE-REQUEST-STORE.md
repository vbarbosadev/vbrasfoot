# Contratos financeiros gravados antes do diario e do efeito

Incremento posterior: FINANCE-CONTRACT-INVENTORY.md conecta inventario/read-only
dos contratos ao coordenador de Main e a preparacao explicita do contexto interno.
Nao cria handlers/owners/API publica nem migra contratos entre revisoes.

Incremento interno experimental de PERSISTENT-FINANCE-REQUEST.md. Nao instala,
nao cria mods e nao concede owners/permissoes/API de escrita. Nao resolve efeitos
APPLIED/UNKNOWN ou inclusao no save. Categorias continuam raw6/7.

## Armazenamento selecionado explicitamente

FinanceRequestStore recebe CareerSidecarStore em root DEDICADO a contratos, nunca
o root de journals/associacoes/ModStore. Escopo fixo career/binding/owner e revisao
do save. Payload agrega ate32pedidos completos v1 de ate4096bytes cada, com ordem
de insercao e key unica. Envelope/HEAD checksummed e CAS/lock/previous/quotas do
sidecar existente protegem a gravacao. Decode revalida TODOS os contratos, schema,
versao, escopo/ancora, counts/lengths, keys duplicadas e trailing bytes.

persist exige token revisto. Mesma key/payload retorna confirmacao read-only do
registro comprometido; mesma key/outro payload e conflito. Nao substitui pedidos,
nao remove keys, nao reordena, nao compacta/evicta automaticamente. Quota32 e de
pedidos por namespace; limites128files/16MiB do sidecar tambem permanecem. Faltam
quotas globais e politica de retencao/compactacao/migracao de producao.

Read nao cria diretorios. Namespace existente sem HEAD nao e conjunto vazio:
retorna RECONCILIATION_REQUIRED, preservando orphans para revisao. HEAD/schema/
checksums corrompidos ou revisao divergente recusam leitura/persistencia.
Nao escolher registro orphan mais recente, restaurar previous silenciosamente,
migrar pedidos ou interpretar o mesmo saldo/nome como identidade logica.

## Pipeline especifico interno

executeStoredFinance exige owner/context/store/journal/handler/payload compativeis
ANTES de tocar disco. Handler de plano persistente informa persistentRequest.
execute comum recusa um plano persistente com REQUEST_REQUIRED; fixture legada
sem contrato continua suportada e nao deve ser promovida a API publica.

Ordem:
1. Revisar journal na ancora/token fornecidos. Namespace uncommitted, historico
   conflitante ou nova key sob incerteza sao recusados antes de guardar payload.
2. Persistir ou confirmar contrato imutavel por CAS em root de requests.
3. Conferir journal novamente: mudanca durante commit bloqueia dispatch.
4. Pipeline existente: roots/gate/dispatch retention -> journal PREPARED -> EDT
   effect -> receipt terminal -> liberar retention. Fences existentes permanecem.

StoredResult separa token/snapshot dos contratos de Result/receipt do diario.
Token de contrato NAO e lease/autorizacao de efeito. Reabrir contrato e ter gate
novo nao torna um receipt historico executavel; journal retorna RECORDED_ONLY.
Nova key continua bloqueada se diario tiver PREPARED/UNKNOWN, antes de consumir
capacidade de contratos. Um request-store ausente e journal antigo existente
pode receber SOMENTE payload que corresponda ao intent existente; isso NAO prova
efeito/inclusao no save nem concede lease historica.

## Estados parciais/interrupcoes

- Falha no payload antes de HEAD: orphan preservado, nenhum journal/handler
  iniciado por essa chamada; reabertura desse namespace exige revisao.
- Falha apos commit do payload: payload pode estar comprometido sem retorno ao
  caller, mas nenhum handler e enfileirado. Reler explicitamente antes de decidir.
- Payload comprometido + falha de journal: contrato fica legivel; se PREPARED foi
  comprometido, read o reporta UNKNOWN. Se restou so orphan sem HEAD, novo caminho
  nao o trata como diario vazio, inclusive com gate novo.
- Mudanca de token do journal apos payload: pode sobrar contrato sem intent, mas
  efeito nao e enfileirado. Nao apagar contrato ou inventar execucao/rollback.
- Falha apos efeito/receipt usa conservadorismo existente; contrato inteiro
  preservado nao e witness de inclusao em arquivo do jogo.

Nao ha transacao entre os dois roots e o save; nem exatamente-uma-vez ou retry
automatico. Fault probes sao excecoes controladas, NAO prova de power-loss/fsync
de diretorios. Path checks nao fazem sandbox contra Java/FS hostis/hardlinks/races.

## Escopo instalado e revisoes

Componente e pipeline estao implementados e cobertos por fixtures; incremento
FINANCE-CONTRACT-INVENTORY.md conecta Main ao root dedicado para inventario/read
no coordenador. Main ainda NAO persiste pedidos ou cria owners/handlers de escrita
para mods. Nao chamar a camada de escrita de pronta por causa desses componentes.

Store fica ancorado na revisao original. Nao pode receber pedidos de outra
revisao, mesmo apos metadata ou diario NO_EFFECT terem sido reancorados. Essa
recusa deliberada permanece ate haver protocolo explicito para manter contratos
historicos, receipts e revisoes/restore/save-as coerentes. Nao mudar bytes do
contrato antigo para contornar o bloqueio.

## Evidencia e proximos gates

Self-tests: roundtrip/reabertura/CAS, conflito imutavel, quotas/sem eviction,
anchor/owner/schema/corrupcao/duplicates, EDT, ordem payload/journal/efeito/receipt,
interrupcoes de payload e journal nos tres estagios, reabertura com gate novo,
bloqueio de novo pedido por incerteza e conflito historico antes da persistencia.

Fixture original privada usa executeStoredFinance em credito/debito raw, preserva
payloads completos e nao permite bypass; writer/reload original e restore do input
da fixture nao habilitam pagamento duplicado. Autoridades sao ARTIFICIAIS, nao
qualificacao de Main, UI financeira ou reabertura standalone original.

Ainda faltam: lifecycle/migracoes entre revisoes, retencao/compactacao e
reconciliacao APPLIED/UNKNOWN por evidencia especifica,
categorias/UI/standalone, calendario/mercado e contratos/permissoes publicos.
