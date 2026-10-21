# Trilha live de referencias do ledger

Escopo entregue pela PR70. A extensao posterior em
PERSISTED-LEDGER-REFERENCE-FACTS.md permite persistir resumos novos em schema4;
nao altera a ausencia de referencias nos schemas1/2/3 descritos abaixo.

## Escopo

Captura opt-in, body-only, no mesmo writer Kryo/Output/stream observado pelo
ProbeSession. Nao substitui resolver, serializer, decoder ou bytes de saves.
API major1 e loader instalado0.9.3 permanecem inalterados. Nenhum mod gerado.

`KryoReferenceTableTransformer` exige Java17/21 e SHA256 de Kryo.class
`98e2fa40c12678b7f66faee59ed7c634d9af3e310ae067ed2da67a313115bac5`.
Inventario exato:4sites writeVarInt em writeReferenceOrNull,5Serializer.write,
1entrada/1retorno normal de reset. Divergencia recusa transformacao.

## Semantica observada e desenho

- Sites0/1:marcadores null0/untracked1, sem identidade de ledger qualificada.
- Site2:referencia existente, valor original id+2 observado apos writeVarInt.
- Site3:objeto novo, marcador1 e ID alocado observados apos writeVarInt.
- Corpo:callback somente depois do retorno normal do Serializer.write original.
- Reset:entrada invalida a epoca anterior;retorno normal completa reset. Falha
  ou reset durante field-call nao vira resultado positivo.
- ObjectField envolve sua chamada original com token opaco por operacao/window.
  Nao le novamente nx para reconstruir argumento. Target fraco seleciona o ledger
  por identidade;checks de runtime/Output/stream restringem a captura live.
- Aceita resolver exatamente MapReferenceResolver com referencias ativas e corpo
  exatamente FieldSerializer. Resolver customizado nao e qualificado por produzir
  os mesmos bytes. Sem marcadores, corpo retornado e UNTRACKED_CONTENT_RETURNED.
- Cada UUID/window possui ate128eventos escalares. IDs sao locais a uma epoca,
  nao sao identidade persistente de clube. Report nao exporta IDs crus/valores/paths.
- `usesOutsideFieldCall` conta eventos (marcador e/ou corpo), nao numero de aliases
  ou objetos. Zero eventos adicionais nao prova ownership exclusivo do grafo.

Resolucao: MISSING, NEW_CONTENT_RETURNED, PRIOR_CONTENT_REFERENCE,
PRIOR_REFERENCE_UNPROVEN, UNTRACKED_CONTENT_RETURNED, AMBIGUOUS, UNAVAILABLE,
CAPACITY_EXCEEDED. Referencia anterior somente liga corpo observado na mesma
epoca;ID preexistente nao observado continua UNPROVEN. Duas field-calls nao se
fundem em uma emissao unica. Tokens estrangeiros/repetidos e chamadas incompletas
falham conservadoramente.

## Validacao privada

- 278checks financeiros originais:fixture isolada com EXE SHA256
  `2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.
  Tres UUIDs reais:1marcador novo/1corpo/1field-call/0eventos adicionais/2resets.
- Oito grafos proprios:new,alias-before,alias-after,root-reset,root-no-reset,
  references-disabled,unobserved-prior,custom-resolver. Bytes identicos ao writer
  original nao instrumentado em todos. Decoder original confirma identidade dos
  aliases fora de P(),compartilhamento entre roots sem reset e novas incarnacoes
  com reset/referencias desativadas. unobserved-prior intencionalmente nao alega
  decodificacao standalone de stream iniciado com tabela previamente populada.
- Suite isolada:18scalar trace,37numeric/reference,28root capture,43writer/report,
  33manifest,63durable observations,23gap review,69execution witness,29dispatcher.
- Evidencia financeira final:Temp/opencode/ledger-reference-table-60dde270372b477e8b88653f0fdf543e/decoded-runtime-test.log.
- Suite final:Temp/opencode/reference-table-final-reviewed-61bb77e608ea4c9a8ad2ccef22b8ac14.
- Wrappers finais aprovados:159I/O originais em
  build/reference-table-original-io-final.log,fixture
  Temp/opencode/vbrasfoot-hook-io-4928e618478941638adf5736a71e124c;
  SDK isolado/hashes/allowlist/authoring negativos em
  build/reference-table-sdk-test-final.log,fixture
  Temp/opencode/vbrasfoot-sdk-author-b1330b14b6a04ccfa60294f63e21c954.
- Rota estatica:Temp/opencode/kryo-reference-route-45d1d03a7a5c4ab5a1ade4c5f4436178/route.javap.txt.
- Os primeiros logs de compilacao/wrapper falharam por checkpoint indefinido em
  fixture propria;corrigido explicitamente como null (sem continuidade de mutacao).
  Esses logs iniciais nao sao aceite;usar somente evidencias finais acima.

## Limites e proximo objetivo

Capture.referenceTraces e separado de Operation/manifest. Schemas1/2/3 e
manifest0/1 NAO persistem essa trilha;reopen nao inventa facts de referencia.
REFERENCE_TABLE_AND_ALIASES_UNPROVEN permanece, assim como exclusividade de mutacao,
perfil durante execucao e recuperacao multi-root. `operationInclusionProven=false`
e `writeAuthority=false`. APPLIED/UNKNOWN sem prova seguem bloqueados,sem nova lease.

Proximo objetivo:contrato versionado de NOVOS resumos persistidos,validacao de
contradicoes/ausencia/overflow e histories mistas,sem migracao em leitura ou
promocao retroativa. Isso nao resolvera por si so inclusao causal ou ownership.
Testes controlados nao sao power-loss nem aceite UI/standalone EXE.
