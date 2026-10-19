# Bases internas de escrita — nao sao comandos de gameplay disponiveis

Escopo: preparar o loader para futuros mods, SEM gerar mods de patrocinio ou
negociacao. API publica/capabilities e loader instalado permanecem0.9.3 nesta
entrega interna; nao ha conexao automatica entre estas classes e jogo/mods.

## 1. CareerSidecarStore

Key externa explicita careerUUID/bindingUUID/modID. Nao descobre identidade,
nao usa CareerScope, path do save ou hash como ID logico. SaveRevision e SHA256+
tamanho dos bytes que um futuro coordenador devera observar/verificar fora da
EDT. O store NAO abre saves, cria UUIDs de carreira, confirma associacao ou
migra binding quando bytes mudam; mismatch exige reconciliacao explicita.

Envelope binario proprio versionado,owner/schema/revision/saveRevision,payload
destacado e checksums SHA256 de payload/record/HEAD. Sem Java deserialization.
Records imutaveis com nomes aleatorios; HEAD pequeno aponta current/previous.
CAS exige token do HEAD anterior (EMPTY na primeira escrita). Bloqueio de arquivo
exclui writers cooperativos inclusive outro processo. Payloadmax1MiB,128arquivos
e16MiB por namespace; orfaos/historico contam. Sem limpeza automatica.

Escreve/force record novo,escreve/force HEAD temporario,confere CAS novamente e
exige move atomico de HEAD sem fallback. Antes do commit,orfaos nao sao promovidos;
apos commit,erro e OUTCOME_UNKNOWN e exige reread. Read nao cria pastas/locks.
Corrupcao de current nao usa previous silenciosamente. readPrevious/recoverPrevious
exigem HEAD valido/token/saveRevision e acao explicita; recover grava nova revisao
monotonica, preserva corruptos. HEAD corrompido nunca varre records para eleger ultimo.

Nao promete fsync do diretorio,recuperacao de falta de energia,transacao multi-save/
sidecar ou exatamente-uma-vez com o jogo. Falhas injetadas em etapas sao excecoes
controladas,NAO simulacao completa de crash/powerloss. Sem migracao de schema,
GC/compactacao,historico ilimitado,quotas globais de todos os UUIDs ou UI de recovery.
Checa links/reparse/canonical ancestors e NOFOLLOW; nao sandbox contra troca hostil
de ancestors/hardlinks durante chamadas por outro processo. Mods Java nao sandboxados
podem ignorar este store; nao anunciar protecao contra codigo malicioso.

## 2. CareerCommandGate

Gate somente em memoria/sessao,sem handler financeiro/mercado conectado. Autoridade
de owner/op especifica criada por caller interno confiavel; nao sao novas permissoes
em ModManifest. Context exige UUIDs/saveRevision/roots/generation explicitamente
fornecidos por futuro coordenador; instalar context nao prova sua qualificacao.

Reserva exclusiva global,owner+key idempotente+payloadDigest,permit opaco da instancia,
recheck de roots/context na EDT antes de iniciar. Start.invocationGranted e true
uma unica vez; statusRUNNING repetido nao e nova autorizacao. APPLIED e afirmacao
do handler confiavel,nao descoberta automatica de efeitos. Throwable apos iniciar
deve terminar OUTCOME_UNKNOWN,nunca presumir rollback. NO_EFFECT_CONFIRMED exige
prova do handler. Cancel so recusa antes de iniciar. Invalidate/revoke/close durante
RUNNING tornam efeito incerto; outcomeunknown suspende contexto ate verificacao
explicita.256receipts/64owners,sem eviction para permitir repetir efeito antigo.

Gate nao e ledger persistente; sozinho nao evita repeticao apos restart. Nao
executa callback sob lock e nao toca objeto legado. Nenhuma operacao publica nova.

## 3. CareerCommandJournal

Store com root dedicado e mesmo binding/revisao externa explicita; journal separado
de payload de contratos do mod. Intent inclui owner namespace,key,operation,digest,
operationUUID. PREPARED e gravado antes de retornar Lease opaca somente nessa
instancia. Snapshot mostra PREPARED como OUTCOME_UNKNOWN: antes de handler nao
ha como provar se outro processo/execucao chegou ao efeito. Reabrir/duplicar key
nunca devolve Lease novamente. Mudanca de payload/op na mesma key e conflito.

Completion explicita APPLIED/NO_EFFECT_CONFIRMED/OUTCOME_UNKNOWN,lease/tokenCAS;
terminal nao sobrescrito por resultado diferente. Qualquer PREPARED/UNKNOWN impede
novos intents. Cap32intents/sem eviction; head movement/saveRevision nao migram
automaticamente. Falha antes de retornar Lease nao autoriza handler; falha apos
commit pode deixar intent incerto e bloqueado,mesmo se nenhum efeito ocorreu.
Conservador intencional: nao sacrificar seguranca para reexecutar automaticamente.

Entrega posterior adicionou dispatcher e adaptador original LIMITADOS conforme
secao5abaixo. Ainda NAO ha protocolo publico de reconciliacao,carregamento do
ledger vinculado automaticamente em startup ou commit conjunto com save.
Entrega posterior adiciona inspectRecovery/quarantinePending e janela de exclusao
beginSave/endSave, ainda internas e sem startup/hook conectado. Veja
JOURNAL-RECOVERY-AND-SAVE-FENCES.md: classificacao/quarentena nao resolvem efeitos
incertos nem reancoram journals; fechar janela nao reativa contexto anterior.
Mesmo APPLIED pode significar efeito em memoria nao salvo. Restore/copia do save
exigem decisao explicita; nenhum teste atual estabelece exactly-once interarquivos.

## 4. Mapa estatico de financas/calendario/mercado

`map-gameplay-boundaries.ps1` le o EXE conhecido sem carregar classes ou executar
operacoes.1038classes/47referencias classificadas,30checks target-free. TSV privado
em build,nao distribuir. JVM REA indisponivel conforme contexto anterior; usado
javap/scanner Java existente,sem baixar/configurar engines.

- best.ah.kb le nb(long);e(long) so substitui nb. Nenhum caller direto de e foi
  encontrado no recorte principal selecionado; isso nao prova ausencia global.
- v(int,int) soma saldo e chama C.h se nx existe e jZ=true;w(int,int) subtrai e
  chama C.i nas mesmas condicoes.8sites de v e7de w. Nao assumir int ilimitado:
  C.h/i atualizam alguns acumuladores int,com risco de overflow alem do saldo long.
- f(long) subtrai e pode atualizar C.b;nao usar f com negativo como credito.
- C.h/i escolhem campos por codigo de categoria. Significado financeiro dos codigos
  ainda nao comprovado por rotulos/fluxo/UI; NAO expor categoria inventada patrocinio.
- az tem7callers compartilhados de criacao/torneio/progressao. Retorno nao e evento
  DayAdvanced exclusivo. Precisa ligar antes/depois do calendario e invalidacoes.
- ah.a/b(F,boolean) tem10sites em best.l relacionados a selecao/predicados de mercado;
  nao sao commit de transferencia ou garantia de poder interceptar todas as compras.

## Gates restantes para o escopo solicitado

1. Verificar qualificacao online original e definir contrato limitado de contexto.
2. Coordenador identidade/binding com politicas explicitas de copy/rename/restore,
   referencias verificadas,UI de conflitos e mudanca de revisao de save.
3. Recovery/compactacao/migracoes/quotas globais e testes de interrupcao reais.
4. Integrar gate+journal com efeitos originais especificos e reconcilacao de erro.
5. Calendario/eventos: fronteiras e ordem/duplicacao/perdas documentadas.
6. Financas: categorias/overflow/contabilidade/UI e roundtrip em save privado.
7. Mercado: geracao/interesse,identidade de ofertas,interceptacao antes do commit,
   exclusao mutua,aceite/reproposta/cancelamento e roundtrip original.
8. API publica/documentacao/permissionamento,testes humanos e autoria externa.

Nenhuma etapa dessa lista e declarada concluida por estas fundacoes isoladas.
OPERATIONAL-CONTEXT.md implementa preparacao interna revisada no gate da sessao,
com cancelamento de instalacao EDT tardia, e reconciliacao limitada de journals
inteiramente NO_EFFECT_CONFIRMED. Nao habilita API publica nem resolve APPLIED/UNKNOWN.

Atualizacao posterior: ORIGINAL-IO-INTERLOCK.md conecta admissao antes do writer,
load e reset originais ao gate privado da sessao opt-in, com cleanup/nested fallback
testados. Nao liga comandos a mods nem resolve qualificacao/reconciliacao restantes.

## 5. Dispatcher e adaptador financeiro limitados — entrega posterior

CareerCommandDispatcher liga journal+gate+EDT+handler interno. Inicio fora da EDT,
preflight de roots na EDT com timeout,validacao de owner/UUIDs de binding e de
operation/digest aceitos pelo handler (handler generico nao aceita nada por default).
Reserva antes de persistir intent; somente Lease retornada permite enfileirar efeito.
Root/contexto/permissao checados de novo imediatamente antes do handler. Handler
roda fora de locks dos registries,na EDT;disk I/O do journal fora da EDT.

Resultados EXECUTED/NO_EFFECT/RECORDED_ONLY/DENIED/UNCERTAIN. Reabrir journal com
APPLIED nao prova aplicar ao grafo recem-decodificado:retorna RECORDED_ONLY,nao
executa handler e invalida contexto ate reconciliacao. Duplicata no gate tambem
nao reemite permit. Erros de journal nunca enfileiram handler apos prepare falho;
erro depois do efeito e UNCERTAIN,invalida contexto e nao presume rollback.
Interrupcao de espera nao abandona silenciosamente callback agendado;preserva
flag do caller apos gravar resultado. I/O interrompido ainda pode falhar incerto.

Timeout antes de entrar callback cancela queued work e registra NO_EFFECT.
Timeout de handler em andamento nao encerra a thread nem desfaz mutacoes:registra
UNCERTAIN e impede instalar novo contexto enquanto handler original nao retornar.
Resultado tardio nao apaga incerteza. Isto NAO e cancelamento cooperativo do jogo,
exactly-once com save ou promessa de que callback legado sempre retorna.

LegacyFinanceAdapter aceita apenas LegacyRuntime do perfil conhecido e SHAexatos
de best.ah/C,sem modificar classes/EXE/schema. Usa getters e snapshot dos14campos
numericos do ledger;require clube da lista humana atual/jZtrue,mesmas roots/ledger,
estado esperado inalterado e plano one-shot. FinanceDelta valida amountintpositivo,
saldo long,acumulador int da categoria,totais long e a soma IADD original dL+dM
antes da conversao. Debito nao pode criar divida neste recorte.

Somente CREDIT_RAW_6(v) e DEBIT_RAW_7(w). Nomes sao codigos do bytecode,NAO
patrocinio/salario/ou categoria de negocio validada. Adapter verifica depois saldo
e TODOS os campos do ledger;qualquer diferenca ou excecao apos chamar original
e incerta,sem compensacao por setter bruto. Nenhum outro campo e escrito via
reflexao;fields privados sao apenas lidos na verificacao. Plano/digest e temporario
por instancia (nonce),nao contrato de request persistente/recriavel por mods.

test-finance-commands.ps1 cria fixture marcada/fresh fora do jogo instalado,copia
EXE/save por hash,executa credito/debito via dispatcher e writer/reload originais
em arquivos finance-output novos. Nao inicia main do jogo,nao cria carreira/partida,
nao grava no save de origem nem comprova reabertura pelo EXE standalone/aceite UI.
Contexto/UUIDs sao autoridades artificiais EXPLICITAS da fixture,NAO geracao de
identidade real ou prova de qualificacao do lifecycle. Public API continua read-only.
