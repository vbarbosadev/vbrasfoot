# Saida real do writer e avaliacao por operacao

Incremento experimental apos FINANCE-CONTRACT-INVENTORY.md. Nao instala loader,
nao cria mods, nao modifica schema/EXE e nao habilita escrita publica. Este texto
NAO declara concluida reconciliacao APPLIED/UNKNOWN, calendario ou negociacao.

## O que faltava no caminho do save

Markers existentes observavam metadata/Output.close/stream close/backup/return.
Eles nao identificavam os bytes efetivamente enviados ao arquivo primario nem
os dois objetos realmente passados a Kryo.writeClassAndObject. Hash de um arquivo
encontrado depois nao era, sozinho, prova de que o writer instrumentado o produziu.

Javap read-only do perfil fornecido confirmou c.a.ae(String): FileOutputStream
da metadata .info e depois do primario .s22; um Output(OutputStream); dois
writeClassAndObject(Output,Object), com SR e afQ; Output.close e copia para sbck.
Offsets originais relevantes:48,140,149,160,169,174. Arquivo local privado:
 `Temp/opencode/vbrasfoot-save-writer-20261020.javap.txt`.
Identidade do alvo continua SHA256 de Profile, e transformer continua exigindo
fingerprint exato de c.a; nao se aceita forma aproximada de bytecode.

## Instrumentacao e stream

CareerTransformer agora tambem exige exatamente dois destinos de FileOutputStream,
um construtor de Output com stream e dois writes de roots no c.a original.
Antes de cada root write conserva referencia FRACA do objeto real; marca retorno
do write somente se o metodo original retornou. Nao substitui objeto, serializer,
destino, bytes ou retorno/Throwable original.

SaveOutputTrace envolve somente stream entregue ao Output primario. Hash/size
sao de bytes cuja chamada ao delegate retornou sem falha. Nao abre/rele arquivo,
faz seek, retenta, adiciona flush ou fecha antes/depois do original. Delegates que
falham parcialmente nao ganham hash de sucesso: falha fica sticky e original
Throwable e preservado. Cross-thread, pos-close/finalize, tamanho acima do limite,
stream vazio/nunca fechado ou falha de write/flush/close recusam evidencia.

HookProbe exige dois destinos observados (metadata e primario .s22), exatamente
duas roots serializadas com retorno, mesmo ticket ativo e sequencia original
completa metadata/primary/underlying-close/backup/return. Evidencia rejeitada nunca
recupera uma anterior. Load/reset/root assignment/detach/novo save invalidam a
observacao corrente; completion atrasado de tentativa superseded nao a republica.

WrittenSaveProvenance guarda somente ultima evidencia em memoria, com roots
fracas, session/generation/revision, destino e hash/size. Nao e registro duravel
de inclusao de efeitos. WrittenSaveVerifier cruza path REAL exato e hash/size do
arquivo explicitamente observado, roots atuais na EDT e ticket/evidence atuais.
File reads ficam fora da EDT, lookup tem timeout; resultado nao e autoridade.
Backup/copia byte-identicos nao podem selecionar outro destino implicitamente.

Report local written-save-provenance.properties e capturado antes de detach,
sem paths/objetos/nomes de clubes. writeAuthority=false e
operationInclusionProven=false sao sempre explicitos. Se houve reload depois do
save, report corrente pode estar INVALIDATED: nao inventar testemunho historico.

## Avaliacao por operacao

FinanceSaveAssessment compara os contratos/journal na MESMA revisao observada do
decoder e registra por key o UUID real do receipt quando existe, status do
contrato e estado do receipt. Tambem separa:
- ausencia de evidencia do writer;
- destino diferente (copia/save-as nao selecionado automaticamente);
- bytes diferentes (arquivo alterado/restore sem correspondencia);
- bytes escritos observados e depois efetivamente lidos pelo decoder no mesmo path.

Inclusao e NO_EFFECT_CONFIRMED, CONFLICT ou UNPROVEN. Nao existe estado INCLUDED
neste avaliador. Operacao APPLIED/UNKNOWN/PREPARED continua UNPROVEN mesmo se
output e reload concordam byte a byte e saldo/ledger sobreviveram. NO_EFFECT de
outro digest e CONFLICT, nunca comprova o pedido atual. Pedido sem receipt nao
recebe UUID inventado. Ancoras antigas continuam bloqueando contexto.

Avaliacao e somente leitura: nao completa/quarentena/migra journal, apaga pedidos,
reancora metadata ou libera lease. Verified DTO guardado antes de reload e um fato
diagnostico naquele instante, nao uma autoridade corrente depois de invalidacao.

## Limites que impedem resolver APPLIED/UNKNOWN com estes dados

O save original nao carrega UUIDs dos comandos do loader. Relacionar arquivo ao
writer/decoder fecha um elo de proveniencia, mas nao prova que um efeito especifico
permaneceu no grafo durante a serializacao, sobretudo frente a mutacoes financeiras
originais nao instrumentadas ou compensacoes. Nem saldo, nem ledger completo,
nem confirmacao generica do usuario substituem um testemunho causal por operacao.

Ainda e necessario um protocolo especifico com cobertura das mutacoes pertinentes,
captura causal no serializer e persistencia/recuperacao do testemunho, inclusive
interrupcoes, restore e save-as. Nao adicionar UUID ao schema original para fingir
compatibilidade. Nao declarar commit atomico save+sidecars ou exactly-once.

## Outros gates do pedido amplo

Evidencia existente so estabelece raw6->dO e raw7->dQ, nao nomes de negocio;
best.ah.v/w nao fazem refresh da UI financeira. Calendario az/V e predicados de
mercado sao candidatos compartilhados, nao eventos exclusivos nem commit de oferta.
Faltam analise/qualificacao desses caminhos antes de calendario, interceptacao,
contraproposta, commit e permissioes publicas. Aceite financeiro/UI/standalone
continua exigindo validacao isolada, nao iniciar uma carreira/partida ou alterar
save pessoal automaticamente para obter esse aceite.

REA nao anunciou ferramenta de JVM na busca direcionada deste incremento;
foi usado javap/JDK existente, sem instalar engine ou mudar configuracao.
Fontes/evidencia anterior e limites estao em LOADER-WRITE-FOUNDATIONS.md,
CREATION-VALIDATION.md, P0-VALIDACAO.md e artefatos privados build/finance-*-bytecode.txt
e build/gameplay-boundaries-core-final.tsv. Essas observacoes nao sao aceite novo.
