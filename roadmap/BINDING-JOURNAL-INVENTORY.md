# Inventario dos diarios de um binding explicitamente escolhido

Incremento de revisao/recuperacao, nao descoberta de carreira ou permissao de escrita.
Incremento OPERATIONAL-CONTEXT.md permite explicar a ancora real antiga e migrar
explicitamente apenas historico NO_EFFECT_CONFIRMED; mismatch nao e apagado ou
interpretado como diario vazio e continua bloqueando contexto ate resolucao valida.
Nao cria mods, efeitos financeiros, carreiras ou saves. Nenhuma instalacao automatica.

## Lacuna corrigida

O coordenador antes revisava somente owners dos manifests selecionados. Um diario
de mod desabilitado/ausente podia existir no MESMO binding sem aparecer na revisao.
Agora a revisao une owners configurados com namespaces fisicos existentes, sempre
dentro do root de journals e careerUUID/bindingUUID EXPLICITAMENTE fornecidos.

Nao varre outras carreiras, nao deduz UUID por path/hash/nome e nao seleciona
"ultimo diario". Owners encontrados nao sao carregados, habilitados ou executados.
O painel distingue [configurado] de [existente, nao selecionado]. A quarentena pode
atuar sobre um owner descoberto SOMENTE depois de revisao e confirmacao explicitas.

## Store: inventario administrativo read-only e limitado

CareerSidecarStore.inventoryBinding le exatamente root/careerUUID/bindingUUID:
- root/binding ausente retorna lista vazia SEM criar diretorios, locks ou arquivos;
- binding existente deve ser diretorio real; entradas devem ter nome Key valido e
  ser namespaces-diretorios reais, sem links/reparse/aliases;
- cada namespace valida somente nomes conhecidos de HEAD/.lock/record/HEAD.tmp,
  tipo regular, count<=128 e bytes logicos<=16MiB; arquivos desconhecidos preservados
  causam recusa, nao ignorados ou apagados;
- HEAD e lido/verificado pelo parser limitado existente; token aponta committed
  current, nunca o arquivo de data mais recente; corrupcao interrompe revisao;
- namespace existente sem HEAD fica listado com token EMPTY como AUSENCIA DE
  COMMIT, nao como diario seguramente vazio; orphan nunca e promovido;
- ate64namespaces e budget cooperativo5s, sem resultado parcial quando excedido.
  Esse budget nao interrompe syscall de filesystem bloqueada e nao promete tempo
  maximo de resposta de disco/rede. Limites por namespace nao sao quotas globais.

Nao le payloads nessa listagem, alem do HEAD limitado. Decode do journal continua
separado e exigindo ancora registrada + schema/checksums originais.

## Coordenador e revalidacao

Na review, soma owners configurados + existentes em ordem deterministica. Limite
de64vale tambem para a UNIAO: nao apresentar apenas os primeiros como completos.
Para cada existente sem HEAD, produz RECONCILIATION_REQUIRED e nenhuma Recovery;
ate um diretorio vazio preexistente nao e confundido com namespace ausente.
Configured namespace ausente pode ser EMPTY sem criar diario fisico.

Cada journal comprometido e lido pela ancora registrada, preservando UNCERTAIN,
HISTORICAL_EFFECTS e erros de schema/corrupcao/mismatch. Token obtido precisa
coincidir com inventario. Depois da leitura, inventaria novamente e compara nomes
e HEADtokens; se mudaram, a review inteira e recusada. Em confirm, antes de alterar
metadata ou quarantinar, repete essa comparacao com inventario contido na Review
privada. Um mod desabilitado que apareceu desde a review exige confirmar novamente.

Listagem nao e snapshot atomico: escritores podem mudar depois da conferencia e
nao ha lock multi-namespace ou transacao de todos os journals. Oscilacao entre
duas observacoes, adulteracao hostil e filesystem races nao estao eliminadas.
Nenhum resultado dessa revisao autoriza novos comandos ou instala contexto.

Namespace existente sem HEAD nao oferece "recuperar ultimo orphan" nem quarentena
como se houvesse receipt valido. Corrupcao de HEAD/entrada desconhecida/permissoes
ou limite abortam revisao em vez de retornar inventario parcial/limpo. Mismatch
de SaveRevision continua erro acionavel; nenhuma migracao silenciosa.

## Evidencia

BindingJournalInventorySelfTest usa apenas dados proprios: ausencia sem side effects,
owner nao selecionado, snapshot imutavel, HEAD alterado, orphan/empty namespace,
outro binding nao visitado, arquivos intactos, EDT, invalid names/types/contents,
HEAD corrupto, quotas de namespaces/arquivos/bytes e junction Windows.
CareerAssociationCoordinatorSelfTest cobre descoberta/quarentena de disabled owner,
mudanca de inventario desde review, UI diferenciada, orphan recusado e overflow da
uniao. Nenhum mod e carregado ou handler financeiro executado por esses testes.

Fixture original adiciona um journal SINTETICO a uma associacao ARTIFICIAL criada
para os bytes realmente decodificados de uma COPIA de save original. Coordenador
descobre owner nao configurado e mantem receipts durante quarentena explicita.
Isso nao e comprovacao de efeito original aplicado/salvo nem de init UI completo.

## Pendentes

Inventario e apenas deste binding/root; nao e discovery/catalogo de todas as
carreiras, diario removido manualmente, mod com armazenamento externo ou binding
desconhecido. Sem quotas globais/migracao/compactacao, reconciliacao por operacao,
commit save+sidecars, contexto qualificado real, calendario/mercado/API publica ou
aceite humano. UNKNOWN continua bloqueando novos intents, sem replay automatico.
