# Recovery de revisao sem efeito — restricoes e protocolo proposto

## Experimento proprio (sem jogo)

FinanceNoEffectRevisionRecoverySelfTest cria somente arquivo binario PROPRIO e
quatro roots privadas de metadata. Request/receipt/witness artificiais usam a
MESMA chave/UUID/intent;ambos resultados duraveis sao NO_EFFECT_CONFIRMED,sem
invocar handler ou getter do jogo. Nao e carreira original nem prova de durabilidade
em crash/power-loss.14checks aprovados em
Temp/opencode/no-effect-revision-constraints-10a65ae11c4243bbafaf048d6fc30d6a;
metadata Temp/opencode/no-effect-revision-14988502853373416773.

Observacoes executadas:
1. Aceitar revisao da associacao preserva todos os3historicos financeiros antigos.
2. reanchorNoEffects do diario funciona no escopo estreito existente,preserva
   recibos e nao gera outra lease para chave antiga. MAS contratos e evidencias
   continuam ancorados na revisao antiga,recusando leitura ordinaria na nova.
3. FinanceRequest.encode inclui save hash/size. Alterar request.save muda bytes,
   digest e intent;receipt original nao corresponde mais. Journal.prepare recusa
   a mesma key com esse novo payload;comparador bloqueia contexto.
4. Copiar payload antigo para um envelope com nova ancora em OUTRA root propria
   produz CORRUPT no decoder schema1. Write na root original recusa mudar ancora.
5. Falhas preservam contratos/witnesses originais,recibos reancorados e arquivo
   alvo. Nao e seguro tentar corrigir isso reescrevendo requests ou apagando keys.

Isso qualifica o BLOQUEIO do protocolo,nao implementa recuperacao coordenada.
Nao ha permissao ou handler novo nem relaxamento do decoder por causa do teste.

## Invariantes obrigatorias da futura implementacao

- Request original continua canonico e byte-identico,incluindo revisao original;
  digests/intent/key/UUID/receipt/witness/audit nunca sao regenerados para aparentar
  a revisao nova. Formato versionado deve distinguir ancora de STORAGE de origem
  IMUTAVEL de cada request. Schema1nao pode ser reinterpretado silenciosamente.
- Todos os owners do binding,incluindo mods ausentes,entram no inventario. Root
  ausente,namespace orfao/corrupto,quota ou scan incompleto bloqueia tudo.
- Eligibility exige pareamento exato request/receipt/witness e provas duraveis
  de ausencia de efeito. UNKNOWN so entra se resolucao append-only comprovada;
  APPLIED/MEMORY_APPLIED e UNKNOWN sem prova NAO entram. Excecoes legacy devem
  ser especificadas/testadas separadamente,sem inventar requests ou witnesses.
- Uma intencao duravel deve registrar source/target,career/binding,vetor de HEADs
  e inventarios dos4roots (associacao/journal/contracts/execution),versao e digest
  do conjunto original. Nao usar igualdade de saldo ou output para eligibility.
- Antes de alterar HEAD,necessario excluir escritores nos4roots e revalidar
  vetores,arquivo observado e escopo. Locks atuais sao POR NAMESPACE em roots
  separadas;nao oferecem hoje exclusao coordenada para novos owners no binding.
- Stage de records novos sem promover orfaos;marcador/manifest duravel identifica
  inequivocamente fases. Definir commit logico e impedir uso de estado parcialmente
  movido por TODAS as rotas de leitura/contexto/dispatch relevantes.
- Recovery usa intencao exata e valida records/HEADs;nao escolhe newest orphan,
  nao faz rollback destrutivo ou descarta histories desconhecidas. Se um writer
  externo alterou qualquer vetor,resultado bloqueado/UNKNOWN,nao auto overwrite.
- Fault tests por fase e concorrencia real sao necessarios,mas nao substituem
  crash/power-loss. Mesmo sucesso nao comprova inclusao de operacoes APPLIED.

## Sequencia segura de implementacao (ainda nao entregue)

1. Definir envelope versionado para preservar requests antigos sem recalcular
   intent e com prova explicita da transicao. Testar codecs e histories mistas,
   leitura sem migracao e impossibilidade de nova lease para qualquer old key.
2. Definir fencing/lock de binding compartilhado por todos os escritores que
   participam do protocolo;adicionar revalidacao de inventario antes de HEAD.
3. Persistir intencao/stages/recovery com CAS e sem caminho publico de migracao.
4. So depois conectar confirmacao interna explicita,mesmo gate/interlock e
   verificacao do loaded save;testar arquivo/copia/restore/save-as separadamente.

Nao implementar simplesmente tres writes sucessivos com compensacao.Nao alterar
request.save para passar no decoder.Nenhuma acao automatica de reanchor habilitada.
operationInclusionProven=false/writeAuthority=false permanecem,assim como schemas
do jogo,loader instalado0.9.3,APImajor1,comparator e roadmap/STATUS.md inalterados.
