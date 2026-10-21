# Argumento de referencia clube -> ledger no Kryo original

Incremento apos FINANCIAL-NUMERIC-EMISSION.md. Captura o objeto/serializer
efetivamente recebidos pelo Kryo, NAO interpreta IDs da tabela de referencias ou
bytes individuais. Nao concede INCLUDED, reconciliacao APPLIED/UNKNOWN ou lease.

## Rota observada

javap do ObjectField do Kryo4.0.2 fornecido identificou write(Output,Object) com:
-dois sites Kryo.writeObject(Output,Object,Serializer), dinamico e fixo obrigatorio;
-um site writeObjectOrNull(Output,Object,Serializer), fixo nullable;
-return antecipado exclusivo do caminho dinamico nulo, apos writeClass(null);
-return final apos caminho nao-nulo/fixo completar.

UnsafeObjectField e AsmObjectField herdam write de ObjectField; seus getField
selecionam a leitura. Nao precisamos reler o campo por fora nem presumir que o
getter financeiro kL representa o argumento serializado. Na fixture original,
o caminho observado para clube.nx foi **writeObject**, com o serializer exato
FieldSerializer do mesmo runtime e argumento IDENTICO ao weak ledger original.
Isso e observacao em fixture, nao garantia para outros ambientes.

FinanceReferenceTransformer exige Java17/21, SHA256 original ObjectField
817f50e574b98a9d1d97427202a31243038e58a7dab47d0c29a24c150f8f7e20 e inventario
exato2/1/2sites/returns. Somente probe opt-in, body-only, sem fallback. Preserva
read/copy/schema/EXE/recursos e a selecao do serializer original. Nenhuma classe
original e redistribuida. Scratch locals preservam os argumentos EXATOS Output,
objeto e serializer da stack; o callback so ocorre APOS a chamada retornar.
Excecao original nao executa o callback do site. O return dinamico nulo reporta
null/nenhum serializer apos o writeClass(null) original retornar. O caminho fixo
obrigatorio nulo continua lancando a mesma falha, sem fabricar evento positivo.

## Identidade, limites e lifecycle

HookProbe exige root em andamento na janela do writer, receiver best.ah,
classloaders de receiver/cachedField/Output iguais e MESMO SaveOutputTrace via
Output.getOutputStream. Falha ou Output aninhado/diferente => referencia unavailable.
Field e obtido por getField publico da classe base CachedField, sem ler o valor
financeiro de novo. Classifica serializer por IDENTIDADE de Class: FieldSerializer
exato, nenhum ou outro; nao exporta nomes arbitrarios de serializer customizado.

Plan.referenced exige campo nx declarado na MESMA classe best.ah, tipo EXATO
best.C e receiver IDENTICO ao weak clube da operacao. Compara o argumento ao
weak ledger, nao por valores/nome/ID/classe apenas. Outra instancia e null sao
divergencias; outro receiver e irrelevante. Reusa UUID/permit/target/gate/interlock
existentes, sem owner publico novo e sem reconstruir alvo apos reload.

Tracker guarda somente Edge(status,count,rotas), nao Object/Field/serializer/
Output/nomes/valores/paths. Ate32operacoes,3rotas fixas e contador saturado2:
- MISSING: nenhuma referencia relevante observada;
- EXPECTED_LEDGER_ARGUMENT_OBSERVED: um argumento do ledger exato, serializer
  FieldSerializer exato e rota suportada retornou;
- REFERENCE_MISMATCH: argumento null ou ledger diferente;
- UNSUPPORTED_SERIALIZER: argumento correto, serializer nao suportado;
- AMBIGUOUS: mais de um evento relevante, mesmo que identidade igual;
- UNAVAILABLE: falta de target/EDT, erro de callback/atribuicao ou rota invalida.

Callbacks internos fora do lock; commit revalida revision/closed/one-shot. Finish
congela Edge imutavel com a captura; load/reset/root/detach/invalidation/close
nao recuperam janela antiga. Report exporta apenas status/count/rotas, mantendo
operationInclusionProven=false e writeAuthority=false. Captura numerica e amostras
de root continuam SEPARADAS: argumento de ledger pode coincidir para operacoes
anteriores cujos valores numericos divergiram. Identidade nao comprova valores.

## Evidencia

25checks sinteticos combinados de argumentos numericos/referencia com classes
GERADAS proprias: identidade, outro receiver, null/troca, serializer custom,
duplicatas/count bounded, erro/rota invalida/off-EDT/invalidacao e report imutavel.
24checks root capture,41writer/report,69witness e29dispatcher preservados.

Fixture privada final:122checks ORIGINAIS em
Temp/opencode/finance-reference-original-82f0f118516a4e438c07669442cbece3/final-runtime-test.log.
Compara runtime probe e runtime SEM probe sobre dados proprios para os caminhos
dinamico/fixo-nullable/fixo-obrigatorio, null/nao-null e overflow DIRETO no cached
field nx, sem primitive anterior consumir primeiro a falha de buffer. Bytes/falhas
originais preservados; fingerprint alterado recusado. Roots reais capturaram
uma referencia writeObject por UUID, mesmo ledger esperado; ultima operacao tem
15argumentos numericos esperados e as duas anteriores VALUE_MISMATCH. Save/reload
original e bloqueio do historico preservados. Fixture copiada de outra copia
privada, EXE/input hash-conferidos; nenhuma escrita em instalacao/save pessoal.

## Proximos elos ainda nao provados

Kryo.writeObject pode serializar conteudo ou emitir referencia a objeto anterior;
este incremento NAO observa/resolve a tabela de referencias, ranges ou bytes do
objeto. Nao prova ownership exclusivo do ledger nem ausencia de aliases no grafo.
Retorno pode deixar bytes apenas em buffer. Nenhuma protecao contra mudancas de
CachedField/Output/serializer por Java arbitrario; custom serializers nao aceitos.
Nao amplia cobertura de escritas Unsafe/reflexao/decoder/copy. Nao elimina a lacuna
causal entre execucao e save. Captura permanece EM MEMORIA, report de shutdown nao
e commit crash-safe. Ainda faltam protocolo duravel por UUID ligado ao writer,
falhas antes/depois do efeito/serializer/commit e recovery de revisoes/restore/
copia/save-as. Nenhuma resolucao automatica de APPLIED/UNKNOWN antigo sem prova.
UI/standalone/calendario/mercado/API publica seguem pendentes. Sem novo teste humano.
