# Associacoes explicitas de save — servico interno, nao ativacao de carreira

Implementacao: SaveFileObserver + CareerSaveAssociations,sem ModContext/Main/UI
conectados. NAO gera mods de patrocinio/negociacao,nao escolhe uma carreira por
hash/nome e nao instala comandos no jogo. Loader instalado/API continuam0.9.3.

## Observar nao e carregar ou identificar carreira

SaveFileObserver abre somente um nome seguro .s22/.sbck em diretorio escolhido,
fora da EDT,READ/NOFOLLOW. Confere ancestors canonicos/links/reparse,tipo regular,
limite256MiB,metadados antes/depois e duas leituras SHA256 no mesmo channel.
Observation tem caminho canonico,nome,size/hash,fileKey quando disponivel e
lastModified/creationTime. verify refaz observacao antes de confirmar metadados externos.
Nao escreve save,nao deserializa e NAO prova que bytes sao uma carreira valida
ou correspondem ao grafo carregado. Nao e lock/transacao com writer original:
o arquivo pode mudar apos observacao,ou numa corrida hostil nao observada.

No Windows,JDK BasicFileAttributes.fileKey pode ser null. Entao EXACT_OBSERVATION
nao e produzido por suposicao. FILE_IDENTITY_UNAVAILABLE ou
NAME_CHANGED_IDENTITY_UNAVAILABLE preservam a lacuna. Hash identifica bytes de
revisao,nao identidade logica de carreira;timestamp/path tambem nao bastam.

## Confirmacoes (somente caller interno confiavel; UI humana futura ainda falta)

- CREATE_NEW_CAREER: apos verificar Observation,gera UUIDcareer+binding novos,
  grava metadados e retorna Handle+token. Esta e decisao explicita de associacao,
  nao classificacao automatica do jogo como pronto. Nenhuma chamada por default.
- FORK_COPY_WITH_EMPTY_MOD_DATA: requer outro path com bytes correspondentes ao
  source registrado,gera IDs novos e registra parenthandle+token+revision+hashsize
  EXATOS da origem. Nao clona contratos/journal/estatisticas. Recusa mesmo fileKey
  conhecido ou isSameFile/hardlink quando ambos caminhos existem. Se origem sumiu,
  permite apenas escolha explicita de nova linhagem,nao atesta copia fisica.
- RENAME_SAME_FILE: apenas mesmo diretorio,mesmos bytes,mesma identidade nativa
  nao vazia,nome diferente e pathantigo AUSENTE. Preserva UUIDs,grava nova revisao.
- ASSERT_RENAME_WITHOUT_FILE_IDENTITY: confirmacao humana separada e mais fraca
  para fileKey indisponivel. Exige mesmosbytes/diretorio,pathantigoausente e nome
  diferente;grava a natureza afirmada da decisao. NAO afirma prova de sameobject.
  Se caminho antigo existe,recusa mesmo que o usuario pudesse estar copiando.
- ACCEPT_CHANGED_REVISION: mesmo path selecionado,alguma diferenca de bytes/key/
  timestamp e consentimento explicito. Atualiza so metadados de associacao.
  Nao e prova de SaveCompleted ou de que arquivo novo e mesma carreira logica.
- RESTORE_RECORDED_PREVIOUS: mesmo path e bytes correspondentes exatamente a
  previous comprometido e validado. Grava revisao nova monotona,sem eleger orphan/
  latest e sem restaurar payloads/journal de mods por inferencia.

Samepath/hash com arquivo substituido nao e MATCH automatico. Copia/diretorio
movido nao sao rename automaticos. Repetir confirmacao sem metadatachange nao
fabrica identidade nativa nem cria revisoes vazias.

## Persistencia e privilegio limitado

Root DEDICADO de metadados,key UUIDcareer/UUIDbinding/br.vbrasfoot.association,
payloadbinario schema1 com owner envelope/CAS/previous/atomicHEAD do store.
inspectAssociation e changeAssociationRevision so aceitam esse namespace interno.
Moddata/journal ordinarios continuam exigindo SaveRevision igual,e nao podem usar
essa operacao para contornar guard de revisao. Trocar ancora exige HEADtoken e
previousSaveRevision conferidos sob lock de writer,registro existente e payload
novo validado. Nao ha alteracao generica para namespaces de mods.

Falha de create/fork devolve CreationFailure com Handle TENTADO para inspecao
explicita antes de retry. Se commit ocorreu,esse handle permite encontrar a
associacao;se nao,inspect resulta NOT_FOUND. Nao eleger registro por data nem
gerar outra identidade silenciosamente apos resultado incerto. Orfaos preservados.

Nao e transacao entre save+associacao+payloads de todos os mods. Alterar associacao
nao migra journal/dados para nova revisao e nao liga context de comandos. Logs de
APPLIED ou dados de contrato podem continuar incompatíveis:reconciliar separadamente
antes de autorizar novas escritas. Nao existe ainda indexglobal/discovery,listagem,
unicidade global de path,quota global de UUIDs,migracao,compactacao ou UI de conflitos.
Um caller pode confirmar associacoes distintas para mesmo path;nao assumir que
lookup por nome/hash produziria uma unica resposta. Nenhuma resolve isso por heuristica.

## Verificacao e limites

Fixtures sao bytes PROPRIOS com extensao de save,nao saves/gameplay decodificados.
Testes exercitam createpersistido/confirmacaorequerida,copyfork sem clonar dados,
rename com evidencia declarada,revisao nova/restoreprevious/unknownrestore/stale
observation/CASconcorrente,hardlinks,junctionWindows,names/pathtraversal,EDT,
corruptschema/payload e interrupcao em etapas do store sem tocar arquivosfonte.
Strongrename com fileKey nativo tem cobertura condicional:quando indisponivel,
o teste reporta SKIP desse ramo e testa explicitassertion em vez de inventar aceite.
Faultinjection e excecao controlada,nao crash/powerloss real. Checagem de paths nao
e sandbox contra hardlinks/ancestorraces hostis ou codigo Java malicioso.

Regressao integrada adicional usa uma COPIA privada de save original:observacao
read-only+create explicito da fixture fornecem UUIDs ao contexto artificial do teste
financeiro;credito/debito e writer/reload originais preservam saldo/ledger. Novo
finance-output com nome/bytes novos e recusado como revisao do pathantigo,sem
retarget automatico;diario antigo tambem recusa nova ancora. Nao e confirmacao
humana/UI ou prova de identidade automaticamente inferida de carreira original.

Proximo gate:coordenador e UI de confirmacao,mais evidencia vinculando bytes
exatamente decodificados ao contexto de roots/geracao original. Sem isso,mesmo
EXACT_OBSERVATION nao pode ser apresentado como CurrentCareer ou liberar pagamentos.
