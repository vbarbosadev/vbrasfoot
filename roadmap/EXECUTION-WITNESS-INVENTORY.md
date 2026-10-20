# Revisao dos registros de execucao no contexto operacional

Incremento de FINANCE-EXECUTION-WITNESS.md. Main seleciona root dedicado
`home/data/.career-state/finance-execution-witnesses` para LEITURA/INVENTARIO no
coordenador. Nao cria owners/handlers de escrita, nao persiste pagamentos de mods,
nao instala loader e nao declara inclusao de MEMORY_APPLIED no save.

## Uniao completa do binding explicitamente escolhido

O coordenador revisa owners configurados + namespaces existentes em journals +
finance-requests + finance-execution-witnesses. Mod desativado/nao selecionado
com registro de execucao tambem entra na uniao, mesmo sem contrato/diario locais.
Limite64 na uniao; nao omitir owner para aceitar resultado parcial.

Sem handle explicito nao ha inventario nem inferencia de identidade por hash/
nome. Construcao/leitura de roots ausentes nao cria pastas. Inventarios mantem
budgets/limites/checksums/path checks do sidecar. HEAD corrupto pode recusar toda
revisao, nao e historico vazio. Namespace sem HEAD e erro de reconciliacao.

FinanceExecutionWitnessStore.inspectRecordedRecovery mostra ancora realmente
comprometida separada da revisao observada do decoder. Read/arm/complete ordinarios
continuam presos a revisao exata; inspecao administrativa nao migra nada.

## Cruzamento e bloqueios

FinanceExecutionRecovery cruza diario e evidencia por operationUUID E intent
completo (key/operacao/digest). O painel mostra owner/configurado, contagem,
ancora antiga quando diverge, key/UUID/status e erros. Nao expoe objetos do jogo
ou adiciona botao de executar/apagar/migrar/resolver efeitos.

- ARMED_UNCERTAIN e EXECUTION_UNCERTAIN continuam bloqueados.
- MEMORY_APPLIED_SAVE_UNPROVEN e execucao observada, NAO inclusao no save:
  continua bloqueado, inclusive se receipt terminal se perdeu e diario e UNKNOWN.
- Registro sem diario ou UUID/intent/estado contraditorio e CONFLICT e bloqueia.
- NO_EFFECT_CONFIRMED so nao acrescenta bloqueio quando TODAS as revisoes e
  historicos ja passaram pelos guards anteriores. Nao limpa UNKNOWN do diario:
  o diario incerto bloqueia independentemente do resultado de execucao.
- NO_EXECUTION_WITNESS preserva apenas compatibilidade de recibo legado
  NO_EFFECT_CONFIRMED, com UUID/intent exatos. Nao fabricar evidencia para APPLIED/
  UNKNOWN antigos nem conceder nova lease. Os guards de contratos permanecem.
- Qualquer erro de leitura/schema ou ancora divergente bloqueia contexto,
  independentemente de saldo, hash do save ou status NO_EFFECT.

Reancorar somente um diario NO_EFFECT nao move o registro de execucao. Sua ancora
antiga fica visivel e continua bloqueando contexto. Quarentena continua monotona,
limitada ao diario; nao finaliza testemunho nem autoriza pagamento.

## Revalidacao

Review guarda os TRES inventarios e snapshots imutaveis. Confere conjunto de owners
e HEADtokens ao final da leitura. Confirmacao one-shot de qualquer acao exige os
tres inventarios ainda iguais aos mostrados; PREPARE_INTERNAL_CONTEXT confere
novamente antes de instalacao EDT no MESMO gate/interlock da ProbeSession.
Alteracao de HEAD ou novo namespace de execucao invalida review antiga. Preparacao
invalida autoridade anterior e nao a restaura ao falhar, conforme protocolo existente.

Nao e snapshot atomico entre roots nem fence contra todo escritor externo. Novo
preflight por operacao continua obrigatorio; igualdade de tokens nao protege
contra adulteracao hostil de arquivos. Path/checksums nao fazem sandbox Java.

## Evidencia e escopo restante

Testes usam markers init/roots PROPRIOS; positivo nao e qualificacao global
original. Cobrem todos os estados de execucao, owner desativado execution-only,
contradicao MEMORY_APPLIED/NO_EFFECT, alteracao de HEAD/namespace apos review,
orphan/HEAD corrupto, ancora antiga apos reanchor apenas do journal, guard ordinario
de revisao e limite da uniao. Registros sem contrato/diario nao ficam invisiveis.

Conexao e de revisao/bloqueio somente. Ainda falta cobertura causal das mutacoes
financeiras e captura no serializer, persistencia/recuperacao da prova de inclusao
por operacao e protocolo de revisoes/restore/save-as. UI/standalone/calendario/
mercado/SDK publico seguem sem aceite. Nenhuma sessao financeira humana deve ser
pedida como se essa prova ja existisse.
