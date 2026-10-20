# Contexto interno revisado e reconciliacao limitada sem efeitos

Incremento posterior: PERSISTENT-FINANCE-REQUEST.md define payload financeiro
interno reproduzivel sem nonce, sem libera-lo a mods ou resolver APPLIED/UNKNOWN.
FINANCE-CONTRACT-INVENTORY.md acrescenta revisao de contratos completos e
correspondencia com journals no mesmo binding, inclusive owners nao selecionados.

Entrega de codigo experimental, sem API publica de escrita, permissao nova para
mods ou instalacao pessoal atualizada. Nao cria mods de patrocinio/negociacao.
Nao registrar snapshot offline ou retorno de init como prontidao global.

## 1. Preparacao explicita no gate da sessao

Main conecta CareerAssociationCoordinator a CareerOperationalContext usando o
MESMO gate privado de ProbeSession/CareerIoInterlock, nunca um gate paralelo.
O painel opt-in oferece **Preparar contexto INTERNO**, com confirmacao explicita.
Criar/inspecionar metadata nao instala contexto automaticamente.

Antes da instalacao:
- Review privada pertence ao coordenador e e consumida uma unica vez;
- associacao foi escolhida explicitamente e corresponde ao arquivo decodificado;
- revisao/hash/tamanho observados coincidem com ancora de metadata;
- stream/roots/session/generation/evidenceRevision continuam atuais;
- ramo de inicializacao foi observado na carga aceita, lifecycle esta healthy,
  IO phase ACTIVE e nao ha carga/save em voo ou recusa no interlock da sessao;
- inventario inclui owners configurados e existentes no binding; orphans, schema/
  corrupcao/erros, anchors divergentes, APPLIED e UNKNOWN/PREPARED bloqueiam;
- arquivo, metadata e HEADtokens sao conferidos novamente antes da instalacao;
- na EDT, verifica mais uma vez roots/provenance e ownership do interlock;
- gate instala somente se a preparacao opaca continuar valida na mesma epoch.

Resultado **BOUND_NO_PUBLIC_WRITES** significa apenas contexto interno vinculado
aos fatos revisados. Nao cria owners, concede permissoes, despacha handlers ou
habilita escrita no ModContext. Operacao futura ainda precisa de regras especificas,
validacao do subconjunto do grafo, preflight renovado e contrato publico revisado.
Nao e garantia de consistencia global, pausa de todas as threads ou ausencia de
mutacoes nao instrumentadas. Nenhuma API de comando foi adicionada ao SDK.

## 2. Invalidacao e cancelamento nao podem conceder autoridade tardia

Preparacao nao segura uma fence enquanto faz hashing/reads fora da EDT. Usa epoch
otimista do gate. LOAD/RESET/SAVE/atribuicao de root/close e outra preparacao mudam
epoch ou invalidam contexto; installPrepared antigo e recusado. Context preparation
ocupada falha fechada e invalida autoridade antiga sem fingir cancelar um handler
em execucao. Historico APPLIED/UNKNOWN na memoria do gate tambem bloqueia outra
preparacao; um inventario persistente vazio nao apaga esse conhecimento.

Token de preparacao e privado, da instancia, one-shot. Em timeout/interrupcao/erro,
cancelPreparation e chamado mesmo se FutureTask ja estava executando na EDT.
Se instalacao ainda nao ocorreu, o callback tardio nao pode instalar. Se ocorreu
antes do cancel, somente o contexto dessa preparacao e revogado. Cancel antigo
nao apaga contexto mais novo de outra preparacao. Nada espera pela EDT sob lock
do gate, nem executa file I/O ou callback de usuario dentro dele.

Metadata/quarentena/reancoragem confirmadas no coordenador tambem invalidam o
contexto anterior e exigem gate quiescente. Nao restaurar contexto em finally.
Status e snapshot em memoria; arquivo/diarios podem mudar apos conferencias.
Nao e transacao de filesystem+EDT nem autorizacao duradoura para escrever depois
sem novo preflight. Contexto depois de efeito/save ainda requer reconciliacao.

## 3. Inspecao da ancora realmente comprometida do diario

inspectRecordedRecovery le administrativamente current HEAD/record do journal
selecionado, valida owner/schema/checksums e mostra recordedSave separado de
observedSave. Nao procura ultimo orphan, nao deduz identidade ou reancora dados.
Assim uma revisao de metadata nao esconde a ancora antiga apenas atras de um
mismatch generico: o painel pode explicar se o historico e incerto/aplicado/sem efeito.

Esse read administrativo e interno em root dedicado de journals. read(save),
inspectRecovery(recordedSave, observedSave) e write ordinarios continuam exigindo
ancora exata; nao ha novo bypass de revisao exposto ao ModStore publico.
Quarentena monotona pode usar a ancora REAL antiga, sem a trocar, preservando
UNKNOWN. Isso ainda nao resolve se um efeito ocorreu ou foi incluido no save.

## 4. Primeira reconciliacao permitida: somente NO_EFFECT_CONFIRMED

Action **Reancorar diario SOMENTE com ausencia de efeitos confirmada** requer:
- associacao atual explicitamente aceita e Review revalidada;
- owner/HEADtoken da linha efetivamente revisada, mesmo se mod nao selecionado;
- journal comprometido nao vazio, na ancora anterior conhecida;
- TODOS os receipts gravados como NO_EFFECT_CONFIRMED pelo handler confiavel.

CareerCommandJournal cria prova interna opaca e nao construivel externamente ao
journal por Java ordinario. Store aceita essa prova para trocar ancora com CAS,
lock, previous e registro existente, mantendo schema/payload/keys/operationUUIDs/
digests/ordem/resultados. Ancora anterior tambem e conferida sob lock. Igualdade
de ancora faz no-op validado. Falha apos commit exige reler, nao retry automatico.

Nao reemite lease de key antiga, nem mesmo quando NO_EFFECT. APPLIED, PREPARED,
UNKNOWN, empty/uncommitted ou schema desconhecido NAO sao migrados por esse caminho.
Nao compara apenas saldo e nao converte uma afirmacao generica do usuario em
prova de sucesso/ausencia de efeito. Os estados de origem precisam ser confiaveis;
checksums nao autenticam arquivos contra adulteracao/codigo Java malicioso.

Reancorar um diario nao migra contratos de mods ou outros journals. Se outro owner
tem efeito historico/incerto ou ancora antiga, contexto continua bloqueado. Nao
ha commit multi-namespace; o protocolo permanece conservador em estado parcial.
Funcoes nao habilitam replay nem exatamente-uma-vez entre save e sidecars.

## 5. Evidencia e limites

Self-tests de contexto usam roots/markers init PROPRIOS: binding explicito, init
ausente, IO invalidation, roots trocadas, journal uncertainty/APPLIED/disabled owner,
no-effect migration, review velha, gate errado, EDT queued/running timeout e
cancelamento apos instalacao/sem apagar contexto mais novo. Positivo sintetico
NAO e qualificacao global original ou aceite de pagamento/transferencia.

Fixture original de I/O continua usando JFrame sintetico e pulando init. Ela
agora confirma recusa de PREPARE_INTERNAL_CONTEXT nesse estado, sem falsificar
marker de init para obter positivo. O usuario ja forneceu anteriormente uma carga
real positiva e confirmou abertura do painel, mas isso nao aprova escritas ou
todos os estados do jogo. Nenhuma nova sessao humana e inventada por estes testes.

Fixture financeira privada valida uma NO_EFFECT real do adaptador (plano consumido/
obsoleto recusado sem mutacao), save/reload originais e reancoragem SOMENTE desse
journal sem efeito. Journal misto com APPLIED permanece recusado. Contextos e
UUIDs dessa fixture sao autoridades ARTIFICIAIS, nao ativacao do loader instalado.

## 6. Gates ainda pendentes

- Contexto operacional com regras por operacao e revalidacao em cada dispatch;
  contexto preparado nao e grant generico de gameplay.
- Reconciliar APPLIED/UNKNOWN por evidencia persistente por operacao, inclusao no
  save, crash/restore/save-as e durabilidade; preservar incerteza ate haver prova.
- Catalogo/duplicidade de associacoes para o mesmo path, quotas globais,
  compactacao/migracoes e cooperacao entre escritores/instancias. Inventario atual
  e de um root/binding, nao snapshot atomico global.
- Identidade/digest persistente de pedidos financeiros (planos atuais tem nonce),
  categorias contabeis/UI e aceite original de ponta a ponta.
- Calendario/partidas/temporadas, ofertas/contrapropostas/commit de mercado,
  permissioes/API publica de escrita e autoria externa.

Nenhuma dessas lacunas deve virar flag de sucesso, rollback por setter bruto,
generic game.write ou botao de apagar historico para tentar novamente.
