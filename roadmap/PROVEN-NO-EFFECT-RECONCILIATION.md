# Reconciliacao auditada de UNKNOWN com prova duravel de ausencia de efeito

Este incremento resolve UM caso delimitado: receipt PREPARED/UNKNOWN no journal,
mas registro de execucao comprometido NO_EFFECT_CONFIRMED para o MESMO UUID,
intent completo, owner/career/binding e revisao. Nao resolve APPLIED/inclusao no
save, ARMED, timeout UNKNOWN ou historicos antigos sem prova. Sem novo mod,
instalacao, alteracao de schema do jogo ou API publica de escrita.

## Prova e auditoria

FinanceExecutionWitnessStore.proveNoEffect so constroi prova opaca apos ler registro
comprometido, exigir HEADtoken revisado e UUID/intent exatos. MEMORY_APPLIED,
ARMED, OUTCOME_UNKNOWN, ausencia, payload/UUID errados e revisao divergente sao
recusados. Prova revalida HEADtoken/conteudo antes da decisao e antes do commit.
Nao aceita confirmacao humana generica, saldo ou ledger como prova.

CareerCommandJournal.reconcileNoEffects recebe lista bounded de provas, valida
escopo/revisao e cada receipt. APPLIED contraditorio e recusado. Acrescenta uma
NoEffectResolution ao journal por operacao incerta; NAO sobrescreve receipt bruto.
Audit guarda UUID/intent, estado original PREPARED ou OUTCOME_UNKNOWN, HEADtoken
da evidencia e context token/generation da execucao. A chave antiga NUNCA recebe
nova lease. Lease original nao pode emitir ExecutionPermit nem completar efeito
depois de resolvida. Quarentena posterior nao reescreve receipt resolvido.

Snapshot.receipts preserva visao historica bruta (PREPARED apresentado UNKNOWN
como antes); noEffectResolutions preserva a auditoria. effectiveReceipts e visao
derivada explicita que usa NO_EFFECT somente quando existe audit correspondente.
Recovery/guards de novos intents/FinanceContractReview usam essa visao. Historico
e audit continuam legiveis depois de reabrir; nao apagar UNKNOWN para fingir que
jamais existiu. Nova operacao com OUTRO pedido/key pode passar guard do journal,
mas ainda depende de todos os outros gates/contexto/owners/preflight.

Formato/schema do journal do LOADER passa a2; le1e2 e valida pares exatos, keys/
UUIDs unicos, auditorias e EOF. Inspecao de v1 nao grava nem migra automaticamente.
Gravacao subsequente usa2, conserva receipts e acrescenta auditoria quando preciso.
Loader antigo nao entende schema2 e deve recusar, nao tentar downgrade. Nenhuma
classe/schema do save original muda. Nao ha migracao destrutiva de arquivo antigo.

## Acao explicita no coordenador

Painel oferece Reconciliar UNKNOWN SOMENTE com prova duravel NO_EFFECT. Confirmacao
one-shot revisa os tres inventarios e origem atual no mesmo gate operacional.
Seleciona owner explicito e apenas operacoes ainda incertas com testemunho exato
NO_EFFECT; se nao ha nenhuma, recusa. Todas as provas selecionadas sao verificadas
antes de um unico commit CAS do journal, sem batch parcial nesse namespace.
Painel mostra receipt original e auditoria/HEAD de prova. Nao executa pagamentos.

Contexto ainda e BOUND_NO_PUBLIC_WRITES, nao grant de gameplay. Quaisquer outros
UNKNOWN/APPLIED/conflitos/ancoras antigas/orphans continuam bloqueando. Memoria
UNKNOWN do gate de uma execucao real NAO e apagada por essa acao; gate continua
conservador ate existir protocolo proprio, sem restaurar autoridade antiga. Teste
positivo de contexto usa autoridades/markers PROPRIOS e nao simula esse clearance.

## Interrupcoes e revisoes

Records/HEAD usam CAS/lock/checksums/previous existentes. Falha antes do commit
preserva antigo UNKNOWN; depois de HEAD comprometido pode haver audit mesmo sem
retorno ao caller. Reler explicitamente antes de decidir, nunca retry automatico.
Reaplicar mesma prova atual a receipt ja resolvido faz no-op; token velho conflita.
Novos writes no witness invalidam provas antigas mesmo com mesmo NO_EFFECT.

Reanchor explicito de diario integralmente sem efeitos pode conservar auditoria
e receipts brutos ao mudar ancora. NAO migra contrato ou witness de revisao antiga;
contexto continua bloqueado pelos outros stores. Nao usar reanchor para reaplicar
uma prova antiga a outra revisao. Sem transacao atomica entre roots/save, nem
garantia de power-loss, exatamente-uma-vez ou autenticacao contra Java/FS hostis.

## Validacao e pendencias

Testes cobrem provas falsas/ausentes/estados errados, contradicao APPLIED, escopo/
UUID/payload/revisao/token, lease velha/replay, raw UNKNOWN imutavel, reabertura,
schema1 read-only, reanchor conservando audit, falhas nos tres estagios de commit,
batch/duplicate/stale proof e I/O na EDT recusado.

Fixture financeira original captura plano persistente, altera estado na COPIA
por operacoes originais ja existentes e executa plano obsoleto: adapter recusa
ANTES de mutar. Falha controlada no receipt terminal deixa journal UNKNOWN com
NO_EFFECT testemunhado. Recovery auditado preserva saldo/ledger/UUID e nao reemite
lease. Autoridades ARTIFICIAIS, nao UI/standalone/gameplay publico.

Falta finalizar prova causal de INCLUSAO de efeitos aplicados no save e protocolo
de revisoes/restore/save-as. Instrumentacao de42writes diretos nao cobre reflexao/
Unsafe/Kryo/decoder e nao pode sozinha gerar INCLUDED. UI/standalone/calendario/
ofertas/negociacao e contratos/permissoes SDK publicos seguem pendentes.
