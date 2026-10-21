# Identidade de ledger na lista atual de clubes

FinanceLedgerMembership verifica uma fronteira delimitada: referencias dos clubes
presentes em P() no momento da leitura. UNIQUE_IN_CLUB_LIST nao significa ownership
exclusivo do grafo serializado ou ausencia de aliases em outros lugares.

Somente EDT, target/type exatos, ledger esperado nao-nulo, lista completa de ate
16384clubes e250ms de orcamento cooperativo. Snapshot transitorio de referencias
e identity set detectam duplicatas/foreign/null; nao usam nome/ID/saldo/equals.
Target deve aparecer exatamente uma vez e seu getter devolver o MESMO ledger;
qualquer outro clube com esse ledger recusa como SHARED_LEDGER. Ledger desconhecido
em outro clube nao e tratado como exclusividade positiva. Revalida tamanho/ordem/
identidades apos os getters. Nao serializa/retém lista/alvos/valores/caminhos.

Status: UNIQUE_IN_CLUB_LIST, SHARED_LEDGER, TARGET_NOT_UNIQUE, LEDGER_REPLACED,
INVALID_CLUB_LIST, READ_FAILED, BUDGET_EXCEEDED, WRONG_THREAD. Ao exceder limite,
jamais aceita lista parcial. A checagem de tempo nao interrompe getter bloqueado:
o source de producao e somente o getter original fingerprintado kL via reflection,
nao callback de mod. Em caso de falha nenhuma mutacao financeira e tentada.

LegacyFinanceAdapter restringe requireHumanClub (request/prepare/preflight/retorno
de apply) e sample no serializer. Aliases falham antes de preparar plano e em
samples retornam LEDGER_MISMATCH. Mudanca durante execucao/erro posterior continua
seguindo o pipeline conservador UNKNOWN, nao rollback nem nova lease. Nao cria
owner/handler/gate paralelo nem muda schema de save ou metadados persistentes.

## Evidencia

18checks com objetos PROPRIOS: distinto/igual/trocado ledger, target ausente/
duplicado, duplicata de outro clube, null/foreign/lista vazia/ausente, quota,
off-EDT, getter falho, crescimento/reordenacao e ausencia de retencao sticky.

236financeiros ORIGINAIS em
Temp/opencode/ledger-membership-original-f283031b9c7f4f8a891a043ec3fc760c/bounded-runtime-test.log.
4novos checks: lista real passa; alias controlado de nx em outro clube detectado;
prepare recusado sem lancamento; finally restaura ledger exato/snapshots de ambos
antes de rodar o pipeline normal. Hash/input da origem privada conferidos. As
escritas reflexivas do teste sao explicitas somente na COPIA privada e nao viram
cobertura de reflexao global. Whole-graph ownership permanece nao demonstrado.

As2primeiras tentativas recusaram a lista real de8395clubes pelo limite inicial4096,
antes de criar contratos/diarios ou executar efeitos. Limite revisado16384mantem
budget fixo explicito e permite analisar a carreira fornecida. Mesmo fixture foi
reutilizado apenas enquanto o protocolo fresh (sem finance-output/history) se
mantinha verdadeiro, sem apagar historico para repetir. Resultado final positivo
e separado dos logs de tentativa inicial/diagnostica.

## Limites

Checagem cooperativa de lista nao captura mutacao externa/Java hostil depois da
leitura, getters adulterados ou aliases fora de P(). Nao prova exclusividade de
referencias na tabela Kryo, ownership de toda a root, ledger no arquivo ou inclusao
causal de operacao. Tempo por verificacao e agregado de multiplas operacoes ainda
precisa aceite de responsividade UI antes de public writes. Campos de auditoria
antigos nao recebem retroativamente essa verificacao. Sem migracao/reanchor/retry.

Proximo objetivo: manifest versionado de qualificacoes NOVAS em observacoes
persistentes, distinguindo factualidade de amostra e exclusoes; depois verificar
referencias internas/aliases fora da lista e coordenar recuperacao. Nenhum historico
incerto recebe prova inventada. APPLIED/UNKNOWN sem prova continuam bloqueados.
