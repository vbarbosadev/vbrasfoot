# Evidencia duravel de execucao financeira

Incremento interno experimental apos WRITTEN-SAVE-EVIDENCE.md. NAO resolve por si
so inclusao no save. Nenhum owner/handler publico foi criado em Main, nenhum mod
foi gerado/instalado e nenhum schema/EXE/save pessoal foi alterado.

## O elo novo

Antes, o journal podia ficar UNKNOWN se o handler aplicou efeito mas a gravacao
do receipt terminal falhou. Nao existia registro independente que distinguisse
essa falha de uma invocacao com resultado desconhecido. Agora o caminho interno
executeWitnessedFinance oferece a sequencia:

1. Revisar/persistir contrato completo conforme pipeline existente.
2. Reservar gate e reter dispatch; comprometer PREPARED no journal.
3. Com lease ORIGINAL da instancia corrente, journal revalida token/UUID/intent e
   estado PREPARED bruto e emite ExecutionPermit opaco. Receipts historicos,
   snapshots UNKNOWN, leases de outra instancia e estados terminais nao emitem.
4. FinanceExecutionWitnessStore compromete ARMED em root DEDICADO, ligado ao
   MESMO operationUUID/intent/career/binding/owner/save/context token/generation.
   Somente depois disso enfileira handler na EDT.
5. Observa outcome do handler e roots antes/depois e compromete evidencia
   terminal de execucao FORA da EDT, ainda sob retention do mesmo gate.
6. Somente depois tenta receipt terminal no journal e libera retention existente.

Nao ha callback de arquivo ou espera EDT sob lock do gate. Handler de plano
persistente original e explicitamente elegivel; handlers novos sao recusados por
padrao (supportsExecutionWitness=false). Caminhos internos anteriores continuam
disponiveis para fixtures historicas e NAO sao anunciados como causalmente cobertos.

## Estados e armazenamento

Store agregado de ate32evidencias por career/binding/owner na revisao EXATA do
save, usando CAS/lock/checksums/previous/quotas ja existentes do sidecar. Decode
valida identidade/keys unicas/operacoes financeiras/geracao/formato/schema/EOF.
Nao ha eviction/reanchor/replay ou promocao de orphan. Namespace sem HEAD e
RECONCILIATION_REQUIRED, nao historico vazio. Lease de arm e privada/da instancia;
reabrir/duplicar nunca a reemite. Root deve ser separado de journals/contratos.

- ARMED: prova apenas que a invocacao foi preparada; nao prova se chegou a rodar.
- MEMORY_APPLIED: handler confiavel retornou APPLIED e roots nao mudaram naquela
  observacao. Nao e INCLUDED, consistencia global ou efeito ainda presente depois.
- NO_EFFECT_CONFIRMED: handler ou gate puderam provar ausencia de invocacao/efeito.
- OUTCOME_UNKNOWN: timeout em execucao, falha ou resultado/roots nao confiaveis.

Estados terminais sao imutaveis, incluindo UNKNOWN. Cancelamento/timeout nao
interrompe handler em voo; exclusao de save continua ate seu retorno. Retorno
tardio nao converte OUTCOME_UNKNOWN em MEMORY_APPLIED. Revogacao/contexto antigo
nao e escondida pelo registro independente; gate/journal ainda podem ficar UNKNOWN.

## Interrupcoes e recuperacao

- Falha antes/depois de commit ARMED: handler nao e enfileirado por essa chamada;
  journal continua incerto. Store pode estar orphan ou ARMED; nada reexecuta.
- Falha depois do efeito ao registrar evidencia: journal fica UNKNOWN e gate
  invalidado. So estado realmente comprometido e legivel; nao deduzir outro.
- MEMORY_APPLIED comprometido + receipt final falhou: preserva UUID/intent exatos
  e evidencia independente de execucao. FinanceExecutionRecovery pode reportar
  MEMORY_APPLIED_SAVE_UNPROVEN mesmo quando journal conservou UNKNOWN. Nao
  reescreve o receipt nem concede uma nova lease.
- Ausencia de evidencia: NO_EXECUTION_WITNESS. Nao criar memoria retroativa para
  operacoes antigas. ARMED e runtime UNKNOWN continuam incertos.
- Payload/UUID divergente, witness sem receipt ou contradicao terminal e CONFLICT.
  NO_EFFECT para outro payload nunca reconcilia o pedido atual.

FinanceExecutionRecovery e read-only por key/operationUUID/intent completo. Caller
precisa primeiro ler os stores na MESMA ancora/escopo verificados; snapshots nao
sao credenciais. Store recusa leitura com ancora diferente apos save/restore.
Nao existe migracao automatica por saldo/ledger/path/hash ou confirmacao humana.

## Ainda nao e testemunho de save

Ligacao causal ate retorno do handler nao prova que aquele efeito foi mantido
durante serializacao, inclusive se o writer foi ligado a bytes reais. Ainda falta
cobertura das mutacoes financeiras originais pertinentes, conservacao da identidade
do clube/ledger e captura causal no serializer, mais persistencia/recuperacao do
testemunho de inclusao para revisoes/restore/save-as. Saldo/ledger iguais nao
substituem essa cobertura. Nao modificar schema do save para incluir UUID.

Main ainda nao seleciona root de witnesses nem integra esses registros ao painel/
inventario de contexto. Sem autoridade de escrita publica, isso nao e bypass para
mods. Integracao precisa de inventario bounded/review/revalidacao conservadores
equivalentes aos contratos/journals, antes de qualquer grant publico.

Falhas injetadas sao excecoes controladas; nao prova de power-loss/fsync de todos
os diretorios, transacao multi-root ou exactly-once. Path/checksums nao autenticam
historico contra Java malicioso, hardlinks ou races hostis. Nenhuma limpeza de
historico deve ser usada para fingir uma operacao nova.

## Validacao humana: ainda nao executar pagamento

A validacao manual financeira sera preparada SOMENTE quando UI/binding correto e
testemunho de inclusao estiverem implementados/revisados. Nao pedir ao usuario
para tentar pagar, editar saldo, apagar UNKNOWN ou salvar instalacao pessoal.
O procedimento futuro deve fornecer:
1. Copia completa isolada hash-verificada, nova e explicitamente identificada,
   baseline de save existente e backup imutavel. Nenhuma criacao de carreira.
2. Uma operacao explicitamente escolhida com owner/key/UUID e valores esperados,
   captura de evidencias antes/depois e nenhum replay automatico.
3. Conferencia de saldo e categoria original na UI; fechamento/reabertura do
   dialogo financeiro conforme caminho seguro, sem chamar pp como refresh.
4. Save pelo fluxo original, fechamento normal e reabertura pelo EXE original da
   COPIA, nunca da instalacao pessoal. Nao avancar dia/jogar partida para fabricar
   aceite. Original init pode ter autosave/progressao; declarar essa possibilidade.
5. Inspecao offline e correlacao exata de evidencias; incerteza e divergencia
   suspendem testes. Confirmacao humana valida UI, NAO resolve UNKNOWN causalmente.

Ainda nao ha ferramenta pronta/aceite para esses passos financeiros manuais.
Testes atuais usam somente fixtures marcadas de autoridade ARTIFICIAL.
