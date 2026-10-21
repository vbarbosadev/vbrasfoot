# Escritas financeiras nao-diretas nas rotas originais do Kryo

Incremento apos DURABLE-SERIALIZER-OBSERVATIONS.md. Fecha uma PARTE delimitada da
lacuna decoder/copy, sem afirmar cobertura universal de mutacao Java ou inclusao.

## Inventario estatico do artefato fornecido

javap do Kryo4.0.2 da fixture privada original identificou24sites de setters:

| Classes originais | Sites por classe | Total |
| --- | --- | --- |
| UnsafeCacheFields$UnsafeIntField/UnsafeLongField |2read+1copy|6|
| AsmCacheFields$AsmIntField/AsmLongField |2read+1copy|6|
| ObjectField$ObjectIntField/ObjectLongField |2read+1copy|6|
| ObjectField |1setField+1copy via FieldAccess.set|2|
| UnsafeCacheFields$UnsafeObjectField |1setField+1copy|2|
| AsmCacheFields$AsmObjectField |1setField+1copy|2|

Numeric read tem ramos fixo/varint. ObjectField.read despacha setField virtual;
ObjectField.copy pode usar FieldAccess.set ou despachar setField virtual. O segundo
caminho usa o site ja instrumentado no setter, sem duplicar artificialmente esse
site. Override Unsafe/ASM de setField/copy tambem e coberto. O inventario NAO inclui
todos os setters de todos os tipos do Kryo, read/copy custom ou chamadas externas.

KryoFinanceMutationTransformer exige SHA256 das9classes originais, Java17/21 e
contagem EXATA de sites por metodo/descriptor. Os6fingerprints numericos e do
ObjectField sao verificados pelos transformers de emissao existentes ANTES da
composicao; as2classes adicionais passam pelo transformer novo. Sem fallback.
LegacyRuntime registra a classe APENAS depois de defineClass bem-sucedido. Probe
permanece opt-in, body-only, sem novo agent/JVM flags, campo/metodo/schema ou
substituicao de decoder. Sem classes originais redistribuidas.

## Callback no setter efetivo

O callback e inserido imediatamente ANTES da chamada original Unsafe.putInt/
putLong/putObject, Field.setInt/setLong/set ou FieldAccess.setInt/setLong/set, depois
de todos os operandos (inclusive Input.read/copy de filho) serem calculados.
Operandos originais ficam na stack. Target e arg2 de read/copy e arg1 de setField,
conforme bytecode fingerprintado, nao objeto reencontrado por nome/ID/valor.

financeKryoMutationStarting verifica receiver best.ah/best.C, mesmo classloader do
cached field, Field real obtido da base CachedField, declaradora/tipo exatos e
somente nb/nx ou14campos financeiros mapeados. Outros campos/objetos sao ignorados.
Falha de introspeccao/atribuicao relevante invalida saude do tracker. Nao reler valor
financeiro nem acessar arquivo; nenhum callback de mod ou espera EDT introduzido.

Quando relevante, beginKryo avanca a MESMA revisao global dos42PUTFIELD selecionados
e incrementa contadores antes de escrever. Retorno booleano e scratch local somente
durante o site. Apos a chamada ORIGINAL retornar, endKryo drena a entrada no MESMO
thread. Escrita de valor/referencia IGUAL tambem invalida checkpoint. Sem dispatch
alternativo, rollback ou supressao de excecao original.

Se Input.read/copy filho falhar ANTES do site, nao inventa escrita no target pai;
eventuais escritas de filhos ja observadas continuam contadas. Se o setter falhar
APOS begin, nao chama end e nao fabrica quiescencia: active permanece pendente e
stableSince recusa. Invalidation enquanto active torna health false de forma sticky.
Nao fechar artificialmente uma escrita incerta para liberar comandos. E uma
restricao conservadora da sonda, nao bloqueio/supressao do comportamento original.

## Cobertura e compatibilidade de observacoes

Snapshot inclui contadores Kryo iniciados/completos e set imutavel bounded de ate9
nomes de classes carregadas no runtime exato. Nao retém Class/ClassLoader forte,
Field, objeto alvo ou valores. Registro tardio de classe muda a revisao, portanto
checkpoint anterior nao pode assumir a cobertura nova. Outro runtime nao empresta
inventario. Contadores gerais e entradas active existentes continuam compartilhados.

Report acrescenta contagens Kryo antes/depois/classes e coverage EXPLICITA. Os nomes
legados NO_OBSERVED_DIRECT_WRITE/directWriteContinuity permanecem por compatibilidade:
uma janela estavel pode agora ser invalidada tambem por um dos setters selecionados,
mas NAO pode ser interpretada como ausencia de toda mutacao nao-direta. Os manifests/
contadores novos sao diagnosticos em memoria/report, nao uma nova prova persistida.
Schema2 anterior NAO ganha retroativamente cobertura Kryo nem e migrado/reinterpretado.
Observacoes persistidas continuam operationInclusionProven=false/writeAuthority=false.

## Evidencia final

31checks de tracker incluem classes9/lifecycle/imutabilidade/runtime estrangeiro,
inicio antes da mutacao, escrita igual, drain e falha sem drain.50durable-observation,
69witness,41writer,24root,25numeric-reference e29dispatcher preservados em
Temp/opencode/kryo-mutation-reviewed-b3278930d2cb4a51a9776cbf50ffa1ef.

220checks financeiros ORIGINAIS em
Temp/opencode/kryo-mutation-original-5c0ecc05bfdb4308951ed39f5f9991c2/reviewed-runtime-test.log:
-9fingerprints alterados recusados e9classes originais definidas pela rota verificada;
-todos6numeric read/copy fixo/varint preservam valores, input truncado/falhas e
 filtragem de campos PROPRIOS nao financeiros;
-set/read/copy ObjectField (ramos com/sem FieldAccess), UnsafeObjectField e
 AsmObjectField preservam identidade de set/deep-copy/valor e nao contam campos
 PROPRIOS fora do mapeamento;
-decoder real do save executa setters selecionados sem active pendente;
-read/copy Unsafe/reflexao de nb/dK/dL e setField nx original com MESMO valor/
 referencia invalidam checkpoint, mantendo snapshot completo e ledger IDENTICOS;
-input truncado relevante falha antes do site sem escrever/inventar mutacao;
-teste NEGATIVO explicito: Field.setLong externo ao Kryo, mesmo valor, NAO muda
 checkpoint. Isso DEMONSTRA a lacuna residual, nao a mascara;
-Field privado deliberadamente inacessivel em setter selecionado conserva excecao,
 valores e active pendente. Executado SO ao final da fixture, sem contaminar a
 sequencia de aceite de writer/observacoes persistidas;
-writer/argumentos/captura duravel/restart/save-reload/gate historico preservados.

Fixture e copia de outra copia privada; EXE/input hash-conferidos. Nenhuma escrita
em instalacao/save pessoal, carreira/progresso automatico, processo pessoal parado,
mod novo/release/tag ou teste humano. Autoridades da fixture sao ARTIFICIAIS.

## Lacunas mantidas

Nao instrumenta Field.set/Unsafe GLOBALMENTE, VarHandles, setters de outros tipos/
classes/custom decoders, JNI/native, gerados/injetados arbitrarios ou inicializacao
VM. Metadados/accessIndex/offset/Field/Output podem ser alterados por Java hostil;
esses hooks e locks nao sao sandbox. Nove classes carregadas e24sites instrumentados
nao provam ausencia de qualquer outra escrita, nem ownership/aliases/referencias
internas/transients do grafo. Sem restaurar health para encobrir falha.

Ainda falta qualificar mecanismos pertinentes restantes ou declarar um contrato
de execucao que os exclua de forma justificavel, sem inventar prova para historicos
antigos. Persistencia continua de OBSERVACOES, nao de inclusao causal. Reconciliacao
APPLIED/UNKNOWN/revisoes/restore/copia/save-as, coordenacao multi-root, UI/standalone,
calendario/negociacao e public writes permanecem pendentes e bloqueados.
