# Manifesto versionado de qualificacoes financeiras amostradas

Contrato historico de manifest0/1 e schemas1/2/3. A extensao schema4 em
PERSISTED-LEDGER-REFERENCE-FACTS.md preserva esses fatos sem requalificacao e
acrescenta envelopes separados de referencia somente para novas observacoes.

FinanceQualificationManifest separa fatos NOVOS de ausencia historica. Nao e
manifesto de exclusividade/sandbox, prova de bytes individuais ou inclusao causal.

## Versoes e pontos de observacao

- Manifest0:legacy,3fatos obrigatoriamente AUSENTES. Nao pode receber fatos novos.
- Manifest1:membershipBefore e membershipAfter nas fronteiras reais das2roots;
  serializerAfterReference APOS retorno da chamada Kryo com argumento de ledger.
  Cada fato e Optional<enum>:ausente nao e positivo/negativo, nem prova inferida.
- Versao/status/marker de presenca desconhecido recusa leitura. Nao existe
  extensao silenciosa por string,classe carregada ou nome de arquivo.

Plan.sampleFact devolve sample+membership no MESMO acesso na EDT. Nao faz varredura
extra de clubes para produzir o manifesto. Root/target/ledger indisponiveis antes
da checagem deixam fato AUSENTE;membership negativo mantem status concreto e sample
negativo. Snapshot financeiro pode divergir com membership unico e isso permanece
SEPARADO:pertencer a lista nao significa valores esperados ou ownership global.

HookProbe calcula perfil do serializer ORIGINAL do argumento real uma vez APOS
retorno. Tracker atribui o fato somente a operacao cujo Field/receiver/ledger foi
avaliado no mesmo callback relevante. Referencia repetida,erro/atribuicao falha ou
janela invalida nao conservam perfil positivo como se fosse unico. Outra operacao
ou outro clube nao empresta qualificacao. Perfil negativo continua registrado
como negativo quando o site e unico/atribuivel.

FinanceSerializationTracker guarda somente enums/Optionals imutaveis no manifesto,
nao clube/ledger/Field/serializer/runtime/valores/caminhos. Bounded32 e janelas
one-shot/lifecycle/revision/weak-target existentes preservados. Nenhum file I/O,
wait de EDT/mod callback ou gate alternativo novo. Os metodos antigos de fixtures
tem defaults AUSENTES, nao inventam fatos para aumentar cobertura.

## Persistencia3 e compatibilidade

FinanceExecutionWitnessStore le schemas/formats1,2e3:
-1:execucoes sem observacoes,sem migracao em leitura;
-2:observacoes antigas com manifest0sintetizado AUSENTE,sem mudar HEAD ou payload;
-3:ao menos uma observacao tem manifest1;cada operacao carrega sua propria versao.

Novo append usa3quando existe manifest1,conservando witnesses,UUIDs,intents,estados,
contratos/recibos e observacoes anteriores. Container3pode misturar manifest0/1;
fatos antigos ficam IGUAIS,nao sao reavaliados no runtime atual. Arm/complete
posteriores conservam3;container2sem novos manifests continua2. Schema3sem nenhum
manifest novo e recusado,nao e aceito como caminho de downgrade. Codec2recusa
escrever fatos novos silenciosamente. Readers antigos que so conhecem1/2recusam3
pelo guard de schema anterior;nao oferecer fallback/descarte de fatos.

Validacao cruza fatos com amostras/referencia:root MATCHED nao pode coexistir com
membership negativo registrado;perfil positivo exige referencia unica/atribuida
e nao pode acompanhar UNSUPPORTED_SERIALIZER. Perfil negativo nao acompanha
EXPECTED_LEDGER_ARGUMENT_OBSERVED. Schema3nao altera schema original Kryo/save/EXE.

Reusa a MESMA coleta interna explicita e CAS/record/HEAD atomic do incremento
anterior. Nenhum worker automatico de Main ou nova permissao publica. Falha antes
do HEAD conserva schema2anterior;depois de HEAD deixa3visivel e retorno incerto.
Orfaos nao sao promovidos/descartados. Excecoes controladas nao sao power-loss.
Retry do efeito/lease/reanchor de historico nunca e concedido por esse manifesto.

## Revisao e exportacao

Report local exporta versao e3status/NOT_RECORDED sem valores/nomes/paths novos.
FinanceSerializationReview remove gap de fato AUSENTE somente quando O MESMO
manifest1o registra;fato negativo recebe gap explicito CLUB_MEMBERSHIP_NOT_QUALIFIED
ou SAMPLED_SERIALIZER_PROFILE_UNSUPPORTED. Um before negativo e after ausente
mantem TANTO negativa quanto incompletude. Historico0continua com gaps originais.

Mesmo3fatos positivos deixam MUTATION_EXCLUSIVITY_UNPROVEN,
REFERENCE_TABLE_AND_ALIASES_UNPROVEN e MULTI_ROOT_RECOVERY_UNPROVEN. Alem dos
outros gaps de argumentos/root/checkpoints/receipt presentes. blocksInclusion=true,
operationInclusionProven=false/writeAuthority=false sempre. Nao inclui manifest
retroativo de cobertura dos24sites do Kryo nem de todos os mecanismos Java.

## Evidencia

33checks de manifesto com fatos PROPRIOS:roundtrip de ausencia/todos enums,
versao/presenca/status invalidos,legacy com fatos recusado,mistura0/1,codec2sem
downgrade,contradicoes e revisao positiva ainda com3obrigacoes causais bloqueadas.
63checks de persistencia:read2sem migracao,append3misto/reopen/imutabilidade,
previous2preservado,arm/complete sem downgrade,interrupcoes RECORD_WRITTEN/
HEAD_STAGED/HEAD_COMMITTED durante2->3sem promover orfaos/repetir efeito,além
dos casos anteriores.28root capture:uma chamada antes+uma depois sem duplicar
sampler;29numeric/reference:perfil positivo/negativo,duplicata/falha sem perfil
orfao.23gap-review/69witness/42writer-report/18membership/14profile/31mutation/
29dispatcher preservados (42writer inclui novo check do report,executado apos
compilacao inicial da fixture original).

Compilacao isolada:
Temp/opencode/qualification-manifest-reviewed-ec40413436304065af095f50a768e8f9.
239financeiros ORIGINAIS em runtime-test.log da mesma fixture:os3UUIDs persistem
manifest1membership antes/depois e perfil do serializer REAL;reopen3preserva fatos
sem runtime objects,contratos/recibos intactos e revisao continua bloqueada.
Writer/argumentos/save-reload/gate historico preservados. Fonte e OUTRA copia
privada,EXE/input hash-conferidos;autoridades sao ARTIFICIAIS,nao Main/UI/standalone.

## Proximos elos

Perfil e amostrado APOS retorno,nao garante metadata durante a chamada.Membership
abrange somente P(),nao aliases fora da lista ou exclusividade de grafo.Mutacoes
Field.set/Unsafe externos,custom/native/gerados nao sao globalmente cobertos.
Permanece necessario qualificar tabela de referencias/aliases e a fronteira de
execucao,coordenar recovery multi-root/revisoes/restore/copia/save-as e validar
UI/standalone antes de comandos publicos.Calendario/negociacao/SDK seguem pendentes.
Nenhuma instalacao/save pessoal/mod novo/release/tag ou novo teste humano.
