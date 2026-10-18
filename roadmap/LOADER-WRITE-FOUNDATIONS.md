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

Ainda NAO ha dispatcher que combine journal+gate+comando original,protocolo publico
de reconciliacao,carregamento do ledger em startup ou commit conjunto com save.
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
