# Revisao de recovery entre roots financeiras (somente leitura)

## Pergunta e limite

Aceitar a revisao da associacao ou reancorar apenas o diario nao coordena as
ancoras de contratos e evidencias. A revisao precisa explicar quais roots
continuam antigas/indisponiveis e quais histories ainda bloqueiam recuperacao.
Estas sao as tres roots de METADADOS,nao as duas roots Kryo serializadas pelo jogo.

FinanceMultiRootRecoveryReview consome somente snapshots ja revisados pelo
coordinator. Nao faz I/O,scan global,write,migracao,retry,lease ou nova leitura
do grafo do jogo. Nao e transacao,plano executavel ou prova de snapshot atomico.

## Fatos por owner

- Uniao ordenada de owners dos3inventarios fornecidos,incluindo desabilitados.
  Duplicatas e inventario/union acima de64sao recusados,nao truncados.
- Vetor imutavel de tokens fornecidos e ancoras com HEAD committed. EMPTY nao
  empresta a revisao observada como se fosse uma ancora duravel. Root ausente
  no inventario nao e a mesma coisa que namespace explicitamente inspecionado
  como vazio;falha/corrupcao/row ausente produz ROOT_REVIEW_UNAVAILABLE.
- ASSOCIATION_REVISION_DIFFERS e diferencas por root sao separados de
  ROOT_ANCHORS_DIFFER:todos podem estar na MESMA ancora antiga,ou so o diario
  pode ter sido reancorado. REVIEW_REVISION_DIFFERS identifica snapshots de
  observacoes diferentes fornecidos a mesma revisao.
- HISTORICAL_OR_UNCERTAIN_RECEIPTS,CONTRACT_LINKS_UNRESOLVED e
  EXECUTION_NOT_NO_EFFECT reutilizam a semantica dos comparadores existentes.
  NO_EFFECT legado sem witness conserva a excecao existente;nao e novo lease.
- SERIALIZATION_OBSERVATIONS_PRESENT exige considerar observacoes historicas;
  hash de output igual nao autoriza mover witness/observacoes para outro save.
- COORDINATED_COMMIT_UNPROVEN permanece SEMPRE. Mesmo empty/no-effect/current
  nao permite automaticReanchorAllowed,operationInclusionProven ou writeAuthority.

## Integracao

O report textual de Carreira e diarios mostra owners,numero de roots com HEAD e
gaps ordenados. Nao cria botoes,altera handlers/actions/guards ou prepara contexto.
Layout/cores/comparator aprovado e loader instalado0.9.3 permanecem inalterados.
Nenhuma permissao publica/API major/release/tag/schema foi alterada.

32checks proprios passaram:ancoras alinhadas/divergentes/antigas,EMPTY real vs
root indisponivel,review de outra revisao,no-effect/legacy/applied/unknown/orphan,
writer observado,owner omitido/desabilitado,duplicata/capacity/token invalido e
imutabilidade.46checks coordinator passaram,incluindo texto real e ausencia de
criacao de roots ao renderizar.43facts/89durable/23gaps preservados.
Evidencia:Temp/opencode/multi-root-final-a8ce82a098ad470e895712b9ce363843.
Wrapper original I/O e SDK isolado em andamento;nao presumir resultado ate concluir.

## Dependencias restantes

Uma transacao multi-root real ainda precisa de protocolo duravel de intencao,
vetor de HEADs validado,exclusao de escritores/TOCTOU,commit e recovery por fases,
sem promover orfaos nem apagar UNKNOWN. Nunca migrar APPLIED por saldo/hash.
Operacoes sem efeito precisam preservar requests,receipts,witnesses,audit e
old-key refusal em todas as fases. Esta revisao nao implementa essa transacao.

Segue necessario resolver ownership/mutation exclusivity,inclusao causal,
coordenacao das duas roots DO JOGO,UI/standalone,calendario e negociacao.
Nao repetir aceite humano load/painel ja confirmado;nenhum save pessoal tocado.
