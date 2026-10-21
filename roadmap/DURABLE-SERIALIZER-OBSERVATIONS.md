# Observacoes persistentes de serializacao por UUID

Incremento apos FINANCIAL-LEDGER-REFERENCE.md. Persiste observacoes historicas de
writer/argumentos/execucao. **Nao e prova de inclusao causal**, reconciliacao de
APPLIED/UNKNOWN, autorizacao de comando ou garantia contra perda de energia.

## Um historico, sem inventario paralelo

FinanceExecutionWitnessStore passa a ler schema/format1e2. Historico sem observacoes
continua escrito como1; abrir/inspecionar schema1 NAO migra. A primeira observacao
explicita escreve2, preservando todos os witnesses e os records/HEAD anteriores.
Novos arm/complete conservam observacoes/schema2, nunca fazem downgrade. Loaders
antigos que so conhecem1 recusam2, conforme decoder antigo; nao recebem fallback.
Nenhuma alteracao no schema original do save/Kryo/EXE ou nos contratos/diarios.

Observacoes ficam no MESMO root finance-execution-witnesses, career/binding/owner
e ancora de ORIGEM existentes. Inventario/HEAD/review de execucao ja incluem owners
desativados; append muda o token revisado. Nao ha novo namespace fora desse controle.
Painel mostra contagem de observacoes com aviso sem prova de inclusao. Recovery
continua MEMORY_APPLIED_SAVE_UNPROVEN e bloqueia; contagem nao habilita acao nova.

## Captura opaca e coleta explicita

WrittenSaveProvenance.Binding recebe UUID de observacao novo por conclusao
qualificada do writer, mesmo quando duas gravacoes emitiram bytes iguais. Nao
deduzir ID por hash/caminho ou reutilizar observacao de outro writer.
WrittenSaveVerifier.capture verifica stream/arquivo exato, origem selecionada,
ticket/roots atuais na EDT e gera Captured com construtor privado. Captured e
TRANSITORIO: segura verifier/observacao de arquivo/binding (weak roots), nao e
serializado. Nao pode ser reconstruido de report, arquivo ou apos restart.

FinanceExecutionWitnessStore.observeSerialization e caminho INTERNO EXPLICITO e
off-EDT, com stores exatos de contrato/diario e token CAS da execucao. Main NAO
recebe novo owner/handler/worker automatico de producao. Fluxo qualificado interno
deve coletar apos writer e antes de perder sua captura; fixtures fazem esse fluxo.
Crash antes da coleta deixa a evidencia de execucao anterior e historico bloqueado,
nao fabrica observacao e nao tenta repetir o efeito.

Coleta exige:
- source SaveRevision igual a ancora atual do witness/contrato/diario;
- Captured ainda valido e janela com operacoes; rejeita captura ausente/invalida;
- por UUID selecionado, intent COMPLETO igual no witness, recibo BRUTO e contrato
  canonico daquela career/binding/owner/revisao. Nao adotar outra operacao por
  igualdade de key/valores; UUIDs de outros owners nao sao incorporados;
- no-effect em witness/recibo contradiz a captura e e recusado. Historias ARMED ou
  UNKNOWN podem receber observacao, sem mudar sua incerteza;
- ao menos uma operacao exata para o namespace; coverage missing/partial/mismatch/
  ambiguous/unavailable e capacidade parcial sao PRESERVADOS, nao promovidos.

Persiste por observacao: nonce/session/generation/revision do writer, SHA256/tamanho
EXATOS do stream, tokens anteriores de execucao/contrato/diario, capacidade,
checkpoint-status durante serializer e, por UUID/intent, estados de execucao e
recibo observados, amostras antes/depois, continuidade direta, status/count/rotas
numericas e da referencia. Nao duplica valores financeiros, nomes, caminho do save,
Field/Output/serializer/roots/plan/runtime no historico. Codec2 fixa o mapeamento de
15campos e whitelist6/3rotas; formato novo exige nova versao, nao reinterpretacao.

Mesmo nonce vivo + mesmo conteudo/contrato/diario retorna confirmacao SOMENTE de
leitura; nunca lease, segundo append ou retry. Nonce ja registrado nao pode mudar.
Limite8observacoes por owner/ancora, ate32operacoes por observacao; quotas existentes
do Sidecar tambem se aplicam. Sem eviccao, limpeza de orfaos ou compactacao implicita.

## Commit, interrupcao e recuperacao

Revalida Captured antes da selecao, antes do commit e depois; verifica tokens/
snapshots de contrato e diario antes/depois e token do witness por CAS. Reutiliza
record force + HEAD staged + atomic move do CareerSidecarStore, sem arquivo
temporario canonico ou fallback nao-atomico. Nao e transacao atomica MULTI-root.

- Interrupcao antes do HEAD: ultimo HEAD continua visivel. Records/staging orfaos
  preservados, nunca escolhidos por data ou promovidos automaticamente.
- Interrupcao depois do HEAD: OUTCOME_UNKNOWN da gravacao; reabrir e comparar UUIDs/
  tokens, sem repetir efeito ou descartar historico.
- Arquivo/roots/tokens alterados apos commit: coleta retorna OUTCOME_UNKNOWN, mas
  preserva observacao HISTORICA dos bytes do stream, nao autoridade do arquivo atual.
- Leitura schema2 valida contagens, UUIDs unicos, rotas/enums/contadores/estados,
  vinculo a witnesses e ausencia de trailing data. Corrupcao/versao desconhecida
  nao vira historico limpo nem dispara rollback automatico.
- read de output/save-as/restore com outra revisao continua recusado. inspect de
  ancora antiga mostra mudanca; nao reancora, migra ou associa por hash.

Interrupcoes testadas sao excecoes controladas, NAO power-loss/fsync de diretorio
ou crash real. Locks/checksums/path checks cooperativos nao sao sandbox contra
Java hostil/hardlinks/alteracoes externas. Ainda ha janelas de corrida apos checagens;
tokens e capturas preservam o que foi observado, nao isolamento universal.

## Evidencia e limites restantes

50checks proprios de schema1sem migracao/schema2/restart/idempotencia/CAS, tokens,
UUIDs, missing negativo, inventario existente, no-effect contraditorio, schema/
format/truncamento/trailing/duplicatas/status invalidos, quotas sem eviccao,
arm/complete posterior sem downgrade, source/output distintos, off-EDT, output
alterado antes/depois do commit, root stale e3fases de interrupcao sem retry de
efeito/orphan promotion.69witness/41writer/24root/25numeric-reference/29dispatcher
preservados na compilacao isolada final
Temp/opencode/durable-serializer-reviewed-38e236ad89c742eb81f78b4e74c82545.

127checks ORIGINAIS em
Temp/opencode/durable-serializer-original-0563bae890224a8f8dbae9a4c5bccd16/final-runtime-test.log:
3UUIDs e argumentos reais persistidos com stream original exato, reabertura2
sem runtime objects, idempotencia e contratos/recibos imutaveis; save/reload e
historico bloqueado preservados. Fixture copiada de outra copia privada,
EXE/input hash-conferidos. Autoridades sao ARTIFICIAIS, nao aceite Main/UI/standalone.

operationInclusionProven=false/writeAuthority=false sempre. Ainda faltam cobertura
causal de mutacoes nao-diretas pertinentes, semantica da tabela de referencias/
aliases/custom/transients, coordenacao/recovery multi-root e protocolo revisao/
restore/copia/save-as. Coletor nao e writer automatico de Main, report shutdown nao
substitui commit, nem observacao persistida resolve UNKNOWN no gate. UI/standalone,
calendario/mercado/SDK publico seguem pendentes. Sem novo teste humano ou mod novo.
