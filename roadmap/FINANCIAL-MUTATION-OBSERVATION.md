# Observacao de escritas financeiras entre execucao e serializer

Incremento opt-in apos EXECUTION-WITNESS-INVENTORY.md. Nao instala loader, nao
habilita comandos publicos, nao cria mods e nao afirma inclusao de APPLIED/UNKNOWN
no save. Checkpoints novos sao diagnosticos EM MEMORIA, nao evidencias duraveis
de inclusao. Stores/journal/contratos/witnesses nao sao migrados por este codigo.

## Inventario estatico estabelecido

Scan independente do EXE conhecido e quatro JARs internos cobriu1317classes,
406713instrucoes e0erros de parse. Resolveu cada PUTFIELD por owner/name/descriptor,
corroborado por javap. Todos16campos escolhidos sao private. Encontrou42sites:
- best.ah.nb:J:10; best.ah.nx:Lbest/C;:4.
- best.C:28; dL..dV:2cada; dW/dX:3cada; dK:J:0.
- Nenhum site externo as classes definidoras nesse inventario empacotado.

Evidencia PRIVADA em
 `Temp/opencode/finance-putfield-static-f99d90e0a7ee49cf86aef301766dd61c`:
FINDINGS.md (methods/offsets), putfield-sites.tsv, field-counts.tsv,
field-definitions.tsv, class-inventory.tsv, scan-identity.txt,
defining-classes-javap.txt e Scan.java (scanner offline reproduzivel).
Nenhuma classe do jogo foi executada para esse scan, sem instalacao/configuracao.

Esses sao sites DIRETOS, nao todas as mutacoes possiveis. Reflexao, Unsafe,
Kryo/reflectasm/decoder, codigo gerado/nativo/injetado e inicializacao VM podem
contornar os PUTFIELDs. Ausencia de site dK nao prova que o campo nunca muda.

## Instrumentacao somente em memoria, fingerprintada

FinanceMutationTransformer e carregado somente com --probe-career-hooks e nos
wrappers privados -ProbeWriter. Exige Java17/21 e hashes exatos:
- best/ah:9e12714ef123afe00636edd25a64334451ba272a9d702b5ce63c656cd4411e6f
- best/C:e208455170e565d83ab0733a79ba2c10e4addfb9a07b583d6b06d46a460eded4

Confere contagens10+4e28antes de definir classes. Brackets argument-free cercam
os sites escolhidos: callback antes -> PUTFIELD ORIGINAL -> callback depois.
Nao muda schema/fields, receptor/valor, retorno/Throwable, class loader, ordem
dos writes originais ou arquivo no disco. Callbacks nao executam file I/O, EDT
wait ou codigo de mod. Modo normal nao transforma essas classes.

Se PUTFIELD falha (por exemplo receptor null), o callback posterior nao ocorre:
contador fica ativo e qualquer estabilidade e recusada. Nao adicionar finally
para fingir que um write possivelmente incompleto terminou. Callbacks sao
diagnosticos best-effort; falha interna tenta marcar tracker unhealthy, sem
substituir erro original. Nao usar isso como sandbox ou garantia de runtime hostil.

## Tracker global e cobertura limitada

FinanceMutationTracker registra ambas classes definidas no MESMO runtime por
referencia fraca do loader. Uma classe apenas ou runtime diferente nao fornece
cobertura; mistura/reuso ambiguo deixa health conservadoramente falso.

Revision monotona muda antes/depois de cada write escolhido e em invalidacao
de load/root/reset/detach. Contadores started/completed e active, mais profundidade
por thread limitada, impedem declarar quiescencia com write em voo, callback
unmatched, overflow, thread diferente ou falha observada. Health falso e sticky.
Tracker GLOBAL e conservador: write em outro clube tambem invalida checkpoint.
Nao armazena grafos, clubes, nomes ou listas ilimitadas; retencao de threads
ativas e limitada64 e entradas sao removidas ao drenar normalmente.

stableSince compara identidade do tracker, revision exata, cobertura de ambas
classes/health e contadores drenados. Uma escrita com MESMO valor ainda invalida
checkpoint: igualdade de saldo/ledger nao esconde evento observado.

Active=0 nao e quiescencia de todo o gameplay ou de uma operacao composta: outro
metodo pode estar entre dois PUTFIELDs. Sincronizar tracker nao bloqueia mutacoes
originais nem torna serializacao atomica. Escritas nao instrumentadas nao sao
detectadas; checkpoint positivo significa APENAS ausencia de write observado.

## Ligacao diagnostica ao efeito e a serializacao

LegacyFinanceAdapter.Plan registra executionMutationEnd apos APPLIED verificado.
Esse checkpoint fraco de cobertura e em memoria: nao altera schema do witness
persistente nem e grant de escrita. Falha/REJECTED nao cria checkpoint positivo.

No writer, HookProbe captura financeBefore antes da primeira root real entregue
ao Kryo e financeAfter depois do retorno da segunda root. WrittenSaveProvenance
conserva esses valores com binding existente do stream/path/hash/roots, sem
alterar os criterios antigos de byte provenance. Quando faltam checkpoints ou
cobertura, noObservedFinanceWriteDuringSerialization=false. Binding de bytes
pode continuar valido sem qualquer conclusao financeira.

FinanceMutationAssessment separa executionToSerializer e serializerWindow em
UNAVAILABLE, OBSERVED_INTERFERENCE ou NO_OBSERVED_DIRECT_WRITE. Nao existe INCLUDED,
token de autoridade ou API de reconciliacao nesse avaliador. Checkpoints faltantes,
runtime diferente ou tracker unhealthy retornam UNAVAILABLE. Mudanca/callback ativo
recusa estabilidade mesmo com estado financeiro final numericamente igual.

Report written-save-provenance acrescenta revisions before/after e o fato
finance.noObservedDirectWriteDuringSerialization, sem paths/objetos de jogo.
Coverage explicita exclusoes. operationInclusionProven=false e writeAuthority=false
permanecem obrigatorios, independentemente do booleano diagnostico.

## Testes e limites de aceite

Fixtures sinteticas cobrem int/long/reference preservados, schema unchanged,
write nao financeiro intocado, NPE original sem completion falsa, cobertura/runtime,
equal-value write, nested/foreign/unmatched, identidade do tracker, invalidacao,
checkpoints presentes/ausentes e interferencia no intervalo do serializer.

Fixture financeira original com -ProbeWriter inclui write intencional do MESMO
saldo pelo setter original e(J) na COPIA para provar deteccao de interferencia
sem depender de comparacao numerica; nao e rollback ou alteracao de save pessoal.
Testa markers de ambas classes e checkpoints nas duas roots do writer privado.
Autoridades dessa fixture sao ARTIFICIAIS, nao contexto publico de Main ou aceite
visual/standalone. Original input/EXE sao conferidos antes/depois pelo wrapper.

## Proximos gates reais

Nao confundir a cobertura dos42sites diretos com cobertura universal de mutacoes.
Falta qualificar mecanismos nao diretos pertinentes e a causalidade do serializer,
ligar operacao/club/ledger ao grafo realmente serializado e persistir/recuperar a
prova de inclusao em revisoes/restore/save-as. Checkpoints em memoria nao resolvem
reinicio/crash nem reescrevem UNKNOWN antigo. UI/standalone/calendario/mercado e
permissoes publicas continuam pendentes, sem exatamente-uma-vez ou commit atomico.
