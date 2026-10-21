# Envelope offline de requests com origens imutaveis

## Entrega e fronteira

FinanceRequestRevisionEnvelope e um codec INTERNO/OFFLINE. Nao foi conectado a
FinanceRequestStore,coordinator,dispatcher ou sidecar migration. O store real
continua aceitando somente schema1/format1,recusando format2 mesmo se colocado
artificialmente em envelope schema1 ou schema2. Nenhuma nova permissao de escrita.

Objetivo:representar futuras transicoes sem alterar FinanceRequest.save,bytes,
digest,intent ou key de requests historicos. Presenca de uma referencia de
manifesto NAO comprova NO_EFFECT,commit,eligibility ou exclusao de escritores.
migrationAuthorized=false/writeAuthority=false sempre. Nao e proof token opaco.

## Formatos

- Format1 reproduz EXATAMENTE o payload atual:magic,format,count e requests
  canonicos length-prefixed. Origem e ancora sao o expectedAnchor externo;nao
  sintetiza transicoes. Scope e validado em cada request. Nao migra em leitura.
- Format2 registra scope explicito,origin,storage anchor e cadeia ordenada de
  Transition(id,source,target,manifestDigest). Digest e referencia NAO VERIFICADA,
  nao assinatura nem conteudo do manifesto. Requests seguem byte-identicos.
- Chain deve comecar em origin,terminar em anchor,sem ciclos/UUID repetido ou
  transicao identidade. Ate8steps,32requests no TOTAL e160KiB. Nao evicta histories.
- Request.save deve ser uma revisao explicitamente presente na chain (inclusive
  origin). Isso permite requests novos em revisoes intermediarias,sem mudar os
  antigos. Keys sao unicas atraves de TODAS as origens;nao reiniciam por revisao.
- Scope esperado e ancora esperada precisam corresponder exatamente. Nao descobre
  carreira por hash/nome. Versao/magic,count,length/trailing/noncanonical invalidos
  sao recusados. decode/encode canonico nao reinterpreta schema1como format2.

Esta chain e deliberadamente somente forward e recusa voltar a revisao ja vista;
isso NAO decide politica de restore. Restore/copia/save-as exigem protocolo e
identidade proprios,nao devem ser liberados removendo o guard de ciclo.

## Evidencia

58checks proprios aprovados:compatibilidade byte-a-byte com schema1 REAL escrito
por FinanceRequestStore,origens mistas A/B/C,preservacao intent/bytes,limites exatos
e overflow,chain incompleta/replay/cycle,scope/anchor invalidos,wire count/length/
magic/version/truncation/trailing,imutabilidade,old-key refusal e recusa de uso
do journal na ancora alvo sem transicao real. Production reader continua recusando
formato/schema novos e HEAD/schema1original nao muda por inspecao offline.

Suite final:Temp/opencode/immutable-request-envelope-final-501900fd8e4448b587eef3706826e544.
Tambem aprovados14no-effect constraints/32multi-root/89durable/46coordinator.
Fixture propria de envelope:Temp/opencode/request-envelope-9798762096419273308.
Primeira compilacao falhou por static import MAX_BYTES ambiguo no teste;corrigido
com nome qualificado. Nao usar esse primeiro compile como evidencia de aceite.
Build completo e SDK isolado em andamento;registrar resultado antes de integrar.
Nao foram repetidos testes humanos ou atribuidos novos resultados originais a
este codec offline. Nenhuma alteracao de EXE/instrumentacao/schema Kryo.

## Proximos elos

1. Manifesto duravel de intencao com vetor de HEADs/inventarios dos4roots e
   pareamento exato de requests/receipts/witness/audit;nao apenas digest no codec.
2. Fencing de binding compartilhado por TODOS escritores antes de alterar HEAD;
   locks por namespace atuais nao bastam para impedir novos owners concorrentes.
3. Commit logico/recovery por fases e bloqueio de qualquer uso parcialmente
   transicionado. Fault/concurrency tests,sem promover orfaos ou repetir keys.
4. Somente depois integrar reader/writer versionados mediante prova verificada,
   mesmo gate/interlock e confirmacao explicita. Formato offline nao e permissao.

APPLIED/unsupported UNKNOWN continuam bloqueados. Nao ha mods novos,install,
release/tag/licensing bypass ou alteracao de saves pessoais. APImajor1/loader
instalado0.9.3/comparator/roadmap/STATUS.md permanecem inalterados.
