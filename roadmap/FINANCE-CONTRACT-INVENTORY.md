# Inventario de contratos e correspondencia com diarios

Incremento interno experimental de FINANCE-REQUEST-STORE.md. Main ja conecta o
coordenador a roots dedicados `associations`, `journals` e `finance-requests` dentro
de `home/data/.career-state`. Essa conexao faz inventario/leitura; NAO cria owners,
handlers, pagamentos, contratos de mods ou API de escrita publica.

## Selecao e limites

Review exige handle de associacao explicitamente selecionado; continua sem buscar
carreira por nome/hash, escolher UUID ou inventariar globalmente outros roots.
Sem handle, nao ha inventario inventado nem gravacao de pastas de contratos.

Para UM career/binding, revisa uniao de:
- owners configurados no loader;
- namespaces existentes de journals, inclusive mods desativados/nao selecionados;
- namespaces existentes de finance-requests, mesmo sem journal correspondente.

Uniao maior que64owners recusa tudo, sem aceitar resultado parcial. Inventarios
reutilizam budgets/limites/path checks do sidecar. HEAD ilegivel/corrompido ou
entrada insegura pode impedir o inventario inteiro; nunca omitir aquele owner.
Pastas sem HEAD sao reconhecidas como incompletas e exigem reconciliacao, nao
historico vazio. Payload comprometido e decodificado contra a ancora REAL do
envelope, separada da revisao do arquivo observado, mesmo se metadata foi atualizada.
read/persist ordinarios continuam exigindo revisao exata. Nenhuma migracao nova.

## Comparacao por key e intent completo

Para cada owner revisado, FinanceContractReview compara key, operacao e digest de
contratos completos com receipts do journal. Nao compara saldo para inferir efeito.

| Estado | Significado e preparacao do contexto |
|---|---|
| REQUEST_WITHOUT_RECEIPT | Contrato sem intent/receipt conhecido: bloqueia. Nao deduzir que e seguro repetir ou apagar. |
| RECEIPT_WITHOUT_REQUEST | Efeito historico/incerto sem contrato conhecido: bloqueia, sem fabricar payload. |
| PAYLOAD_CONFLICT | Mesma key com operacao/digest diferentes: bloqueia mesmo se receipt diz NO_EFFECT. |
| MATCHED_HISTORICAL_EFFECT | Correspondencia com APPLIED: continua historico, bloqueia. |
| MATCHED_UNCERTAIN | Correspondencia com PREPARED/UNKNOWN: continua incerto, bloqueia. |
| MATCHED_NO_EFFECT | Correspondencia exata com NO_EFFECT_CONFIRMED: nao acrescenta incerteza de efeito. |
| LEGACY_NO_EFFECT_WITHOUT_REQUEST | Recusa legada comprovadamente sem efeito, sem contrato: preserva compatibilidade da preparacao interna limitada, sem inventar contrato ou replay. |

Prepared bruto aparece como UNKNOWN nas snapshots, conforme journal existente.
Se journal nao pode ser lido, nao ha comparacao parcial que contorne o erro: a
preparacao ja e bloqueada pela revisao do journal. Se contrato nao pode ser lido,
row registra falha ou inventario inteiro e recusado. Todo contrato em ancora
divergente bloqueia independentemente de correspondencia de key/digest/NO_EFFECT.

LEGACY_NO_EFFECT_WITHOUT_REQUEST nao habilita novo comando antigo nem isenta
prepare(FinanceRequest) de armazenamento. Uma futura operacao ainda precisa de
payload novo/revisado e preflight de operacao; contexto continua BOUND_NO_PUBLIC_WRITES.
Somente ausencia de efeitos ja afirmada pelo handler confiavel e preservada; nao
usar confirmacao humana generica para criar NO_EFFECT ou corrigir historico.

## Revalidacao de contexto e UI

Review conserva snapshots imutaveis e ambos inventarios (journals e contratos).
No final da leitura, confere novamente HEADtokens/conjunto de owners, associacao
e prova de carga. Confirmar qualquer acao exige inventarios ainda iguais aos
mostrados. Acao continua one-shot, e contexto anterior e invalidado por begin.

PREPARE_INTERNAL_CONTEXT exige, alem dos gates anteriores, nenhum contrato com
erro, ancora divergente ou link bloqueante. Antes de instalar na EDT confere de
novo ambos inventarios/associacao e load evidence. Usa MESMO gate/interlock da
ProbeSession, mantendo cancelamento/epoch/timeout existentes.

Painel experimental mostra por owner quantos contratos existem, ancora real
quando diverge, key/status da correspondencia e falhas. Nao adiciona botao para
apagar/migrar/autorizar pagamento ou resolver resultado por confirmacao generica.
Quarentena continua limitada ao journal na ancora verdadeira; reancoragem
NO_EFFECT do journal NAO migra os contratos antigos. Esse estado parcial fica
visivel e continua bloqueando contexto ate existir protocolo explicito.

Nao ha snapshot atomico entre roots nem fence contra toda alteracao externa.
Mudancas depois dos checks ainda sao possiveis; cada operacao futura necessita
preflight renovado. Inventario revisado NAO e grant permanente de escrita.
Checksums/path checks nao autenticam historico contra Java/FS hostis/hardlinks.

## Evidencia e limites

Testes de contexto usam roots/markers de inicializacao PROPRIOS; positivos nao
qualificam consistencia global original, UI financeira ou gameplay no Main.
Cobrem estados pareados, payload sem journal/owner desativado, conflito sob
NO_EFFECT, HEAD corrupto/orphan, limite da uniao, alteracao de namespace/HEAD apos
review e ancora antiga visivel apos reancorar somente journal. Recusas antigas
sem contrato continuam explicitamente legadas, sem nova lease ou contrato inventado.

Faltam protocolo de revisoes/restore/save-as que preserve pedidos historicos,
reconciliacao APPLIED/UNKNOWN por evidencia de operacao/inclusao no save, catalogo
global/quotas/retencao/compactacao, categorias/UI/standalone original e finalmente
permissoes/API especificas/calendario/mercado. Nada aqui resolve esses gates.
