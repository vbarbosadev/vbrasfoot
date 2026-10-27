# Reconhecimento de devolucao nativa — 23/10/2026

## Reproducao relatada

Usuario tinha4emprestados,recebeu recusa correta pelo cap3,devolveu Carlão e Ruan
Carvalho usando opcao original do jogo e NAO salvou antes do novo pedido. O mod
ainda exigia tracker ativo para todos osAPPLIED,causando falso bloqueio historico.
Arquivo pessoal em disco continuava com4trackers e ambos no Flamengo,como esperado
para devolucao somente em memoria. Copia de analise return-audit.s22:
SHA659a54d60eb11be8432a66c1187609bde561e82c043c6138dfb67505a6ca82d1,
clock1643554402790. Carlão43534,RuanCarvalho23758.

## Correcao limitada

APPLIED significa que a concessao aconteceu;nao que deve permanecer ativa para
sempre. A checagem agora admite retorno nativo consistente,sem alterar o contrato:

1. Historico nativo bo/best.ap tem movimento original daquele jogador,da origem
   para destino,valor0,na data do pedido. Valores0 sao o movimento nativo do
   emprestimo,inclusive quando o mod cobrou taxa separada;nao implica reembolso.
2. Depois aparece exatamente movimento inverso,valor0,data entre concessao e data
   atual. Sem movimentos intermediarios/adicionais,duplicatas ou ordem ambigua.
3. Jogador unico,mesmo id/nome,no elenco profissional da origem,com referencia de
   clube correta e nenhum tracker para ele. Retorno com tracker restante recusa.
4. Tipo de registro de jogador separado de treinador para nao colidir ids.

Antes de salvar,best.ap guarda U/transient ligado ao jogador,com id ainda nao
materializado. Depois da preparacao original mb e serializacao,usa ei/gD e tipoej.
Adapter trata ambos,sem escrever campos para fazer verificacao passar.
Leitura de bytecode do artefato reconhecido confirma components.t.tN chama
best.F.r(origin),que usa a transferencia original com flag de retorno e valor0;
best.F.a(...) anexa o movimento inverso em bo. Caller remove tracker apos retorno.
best.ap.mb materializa id/tipo para serializacao. Essas observacoes nao provam
que toda transferencia reversa de valor0 e causalmente aquele callback;prova usada
e consistencia do roundtrip nativo + estado unico da origem sem tracker.

Nao considera ausencia de tracker sozinha como retorno,nem jogador sumido ou
venda para outro clube. Falta de historia nativa correta continua bloqueando.
UNKNOWN/EXECUTING/contrato divergente permanecem bloqueados. Baseline anterior
Macarrao/MatheusReis permanece especifico e nao foi ampliado para estes retornos.
Cap3 conta trackers atuais,nao numero de contratosAPPLIED do passado. Tombstones
continuam proibindo repetir jogador/origem;novos jogadores distintos sao permitidos
quando demais requisitos passam. Nao repete transferencias nem devolve taxas.

## Validacao

-16checks puros de evidencia:mesmo dia/ordem,retorno posterior,falta de concessao,
  venda,origemerrada,duplicatas,outromovimento,datasinvalidas/futuras e semdados.
-19checks lote incluem origem sem evidencia recusada,retorno comprovado aceito,
  tracker restante e jogador ainda no destino com historia de retorno recusados.
-Probe em copia do Flamengo acima com4contratosAPPLIED e baseline preexistente:
  checks antes passaram. Chamou2vezes tN original e emulou remocao dos trackers
  pelo caller APENAS na copia em memoria. Depois2trackers,4contratos preservados,
  checks passaram inclusive aposSaveCheck privado escrever/reler outro arquivo.
  Para roundtrip privado,chamou mb original nos movimentos de referencia transiente,
  como preparacao do save;primeiro ensaio sem isso nao materializou ids e recusou.
  Nao foi ignorado esse erro nem alterado decoder para aprovar historico ausente.
  Nenhum save pessoal,contrato ou ledger pessoal modificado. Calendar nao avancou.
  Probe de lifecycle/adapter nao e teste do clique naUI pessoal nem do limite
  financeiro causal;um novo emprestimo nao foi executado pelo agente nessa sessao.

Regressao completa e probe final passaram. Pacote
portable-0.9.4-native-loan-return-20261023 instalado na copia autorizada existente
Documents/Brasfoot22-23,com jogo fechado. Backup anterior:
Documents/Brasfoot22-23-loan-return-backup-20261023-092625.
85arquivosprotegidos/save/EXE/historicos intactos,JARconfere,validate-only instalado
passou sem iniciar jogo. Save pessoal nao foi regravado para simular devolucoes:
continua com estado que o usuario salvou. Se fechou sem salvar as devolucoes,
precisa refaze-las pelo jogo;nao foram aplicadas pelo agente ao save pessoal.
