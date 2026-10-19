# Recuperacao conservadora do diario e exclusao durante saves

Implementacao inicial interna, sem ModContext ou hooks do writer no jogo instalado.
Incremento ORIGINAL-IO-INTERLOCK.md conecta exclusao as entradas originais no codigo
opt-in, ainda sem instalar nova versao no jogo ou autorizar contextos de escrita.
Incremento DECODED-SAVE-COORDINATOR.md liga leitura/quarentena de diarios a painel
de metadata opt-in no Main; nao a contexto de comandos ou startup automatico.
Nao cria mods de patrocinio/negociacao. Complementa EXPLICIT-SAVE-ASSOCIATIONS.md;
nao substitui qualificacao de roots/geracao ou reconciliacao de efeitos persistidos.

## Inspecao de recuperacao

CareerCommandJournal.inspectRecovery exige a revisao REGISTRADA conhecida e a
revisao OBSERVADA separadamente. Le o diario somente pela ancora registrada,
sem ignorar o guard, pesquisar manifests/orfaos ou retarget de HEAD. Devolve
snapshot destacado, ambas revisoes, indicador de mudanca e uma classificacao:

- EMPTY: sem receipts; NAO e prova de carreira nova nem autorizacao para comandos.
- NO_EFFECT_ONLY: todos os receipts registram ausencia de efeito confirmada pelo
  handler original confiavel. Nao descobre efeitos por comparacao de saldo.
- HISTORICAL_EFFECTS: algum APPLIED, sem incerteza. APPLIED e historico de execucao,
  nao prova de que o save observado incluiu esse efeito ou de que o grafo atual o tem.
- UNCERTAIN: algum PREPARED/OUTCOME_UNKNOWN. Tem prioridade sobre APPLIED.

Bytes iguais tambem nao comprovam efeito no grafo carregado. Classificacao nao
possui allowWrite, nao emite lease, nao modifica associacao e nao instala contexto.
Ancora registrada desconhecida continua erro; nao existe lookup automatico por hash.
Cada diario e avaliado isoladamente: nao ha inventario completo de todos os mods,
nem conclusao global de prontidao baseada apenas numa lista parcial.
Entrega BINDING-JOURNAL-INVENTORY.md acrescenta inventario limitado dos namespaces
existentes no binding explicito, incluindo nao selecionados, com recusa de orphan
sem HEAD. Nao conclui prontidao nem oferece discovery de outros bindings.

## Quarentena duravel monotona

quarantinePending exige ancora registrada + token HEAD atual. Converte somente
PREPARED em OUTCOME_UNKNOWN, preservando operationUUID, intent, key, digest,
ordem e todos os resultados terminais. Usa store/CAS/lock existentes fora da EDT.
Sem pendentes, nao cria geracao ou pastas. Repetir apos sucesso e no-op.

Serve para tornar duravel a incerteza de uma interrupcao sem esperar que algum
caller interprete PREPARED corretamente. Nao exige fingir que o processo anterior
morreu: se chamado enquanto uma lease ainda esta viva, pode conservadoramente
cerca-la. Complete APPLIED/NO_EFFECT dessa lease nao pode sobrescrever UNKNOWN,
mesmo relendo token novo; completar UNKNOWN identico e no-op. Uma corrida entre
complete e quarentena tem um unico vencedor CAS. Nao ha rollback de efeito em voo.

Depois de falha I/O, reler explicitamente o HEAD. Em qualquer etapa os receipts
conservam supressao de replay; HEAD nao comprometido nao promove orphan. Falha
pos-commit pode ja ter convertido para UNKNOWN. Um retry com token antigo falha;
com token relido so normaliza o que ainda for PREPARED. Nunca reemite lease.

Quarentena NAO resolve UNKNOWN como sucesso/fracasso, nao apaga comprovantes,
nao desbloqueia intents novos e nao altera a revisao do save. Nao existe botao
"confirmar e tentar de novo". Evidencia independente por operacao e politica de
restauracao/save continuam necessarias para reconciliar efeitos de fato.

## Janela de exclusao antes do writer original

CareerCommandGate.beginSave deve ser chamado por um futuro coordenador confiavel
ANTES de entrar no writer original. Recusa reserva pendente, handler em execucao,
handler timed-out que ainda nao retornou, gate fechado ou outra janela aberta.
Nao espera na EDT nem invoca writer. Em sucesso, invalida roots/contexto anterior
e retorna SaveFence opaca, exclusiva daquela instancia.

Durante a janela, reserve retorna SAVE_IN_PROGRESS antes de conceder permit,
inclusive para keys historicas; installContext tambem e recusado. Invalidate nao
remove a exclusao. endSave exige a mesma fence e nao pode ser repetido/reutilizado
ou executado por fence de outro gate. Fechar gate permanece definitivo.

O caller fecha a janela em finally, tanto em retorno quanto excecao do writer.
Isso nao declara SaveCompleted nem restaura o contexto antigo. Mesmo que a revisao
pareca igual, nova qualificacao independente e exigida antes de installContext.
Journal/associacao nao sao reancorados. Nao manter uma fence aberta apos writer
travado como se ele tivesse terminado; callbacks tardios continuam limitacao do jogo.

Esta e exclusao de comandos COOPERATIVOS de um gate, nao lock de todos os saves
ou de outras instancias do jogo. Um evento posterior de save nao pode simular que
essa exclusao foi obtida antes. Os hooks atuais nao interceptam automaticamente
o inicio do writer e nao estao ligados a beginSave: integracao em producao pendente.
O dispatcher retem a reserva ate retornar do I/O de completion do diario, com
release em finally. beginSave e installContext tambem recusam esse intervalo
apos o handler terminar. Nao ha espera/drain automatico: caller deve adiar o save
quando ocupado. Se completion falhar, a reserva e liberada mas o contexto e
invalidado e o resultado permanece incerto; isso nao fornece atomicidade.

## Evidencia e pendencias

CareerRecoverySelfTest usa somente roots/bytes/diarios proprios: classificacoes,
revision mismatch sem migracao,quarentena persistida/idempotente,leases tardias,
CAS recovery/recovery e recovery/complete,falhas nas tres etapas de store,EDT,
fences durante queued/running/timed-out,autoridade,nesting,fechamento,invalidation
e dispatcher sem efeito/intents durante janela ou apos falha do writer.
Tambem pausa o I/O terminal depois do handler e confirma que save/troca de
contexto so sao permitidos depois do finally do dispatcher liberar a reserva.

FinanceOriginalRuntimeTest usa copia privada marcada de save original. A fixture
chama beginSave/endSave ao redor do writer original e confirma balance+14 campos
do ledger apos reload; contexto antigo continua recusado. Inspecao classifica o
diario como HISTORICAL_EFFECTS com revisao diferente,sem migrar para output. Isso
nao e interceptacao do jogo standalone,qualificacao de carreira ou aceite humano.

Ainda faltam: startup de producao/inventario de journals,conflitos/UI,ancora dos
bytes realmente decodificados,observacao qualificada de revisoes do writer,
reconciliacao por operacao de APPLIED/UNKNOWN,save-as/restores,compactacao/migracoes,
quotas globais,calendario/mercado/API publica e validacao humana. Nao ha transacao
save+sidecars nem garantia exactly-once/power-loss. Fault injection usa excecoes,
nao crash real. Loader instalado0.9.3 e mods existentes permanecem intactos.
