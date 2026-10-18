# Proxima entrega P4 — sonda opt-in de carreira

Estado: **diagnostico experimental**, nao API de eventos ou aceite completo de P4.
Nao habilita sidecars/P3, descarregamento ou gameplay. Use somente copia isolada.

## Integracao

`LegacyRuntime` transforma `c.a` e, no incremento de candidatos, `a.bV`/`a.cR`
durante `defineClass` quando a opcao
`--probe-career-hooks` e solicitada. EXE, cache extraido e schemas permanecem
originais; nao modifica nomes, campos, descriptors ou hierarquia. Fingerprint
SHA-256 completo da classe e contagem exata de sites devem coincidir; falha recusa
a sonda antes de definir a classe, sem fallback silencioso para hooks por tentativa.

### Candidatos de conclusao da criacao — diagnostico, nao evento

Tres corpos originais selecionados: a.bV.AB()V e a.cR.AC()/pn()V. Fingerprints
de classe inteira, nome/acesso/descriptor e um RETURN exato por metodo validados
antes de defineClass. Transformacao so em memoria, somente na sonda17/21.
Entrada/retorno/falha emitidos como creation-candidate-start/return/failed;
`file` nesse record interno identifica o metodo candidato, nao nome de save.
`completed=false` sempre: retorno do caminho nao comprova carreira nova pronta.
AC pode retornar apos selecionar painel10 antes de terminar o amistoso; os paths
de a.cR tambem podem servir inicio de temporada. Nao interpreta escolha/dialogo.

Sinais passam pela fila diagnostica128 existente, sem I/O/callback de mods no
metodo original. Falha do observador isolada; excecao original repropagada e
handlers legados tem precedencia. Nenhuma raiz/flag/lifecycle guard alterado,
nenhum READ_COMPLETED ou capacidade publica emitida. UNKNOWN permanece UNKNOWN.

Corroboracao pendente: criar uma carreira sem amistoso e outra com a opcao, se
oferecida, usando nomes novos na copia. Registrar escolha, momento de chegada ao
elenco e se candidato ainda rodava/retornou. Cancelar etapas nao deve virar pronta.
Nao jogar partidas/sobrescrever saves apenas para testar. Instalacao/validate-only
sem jogo verifica classes transformadas, nao executa os caminhos originais.

Confirmacao posterior do usuario e log registraram start/return de a.bV.AB e
escrita de teste04, mas guard continuou UNKNOWN. Incremento de conteudo gera
creation-candidates.properties com tres labels fixos, counters e ultima tentativa
pareada: identidade fraca de raizes/listas, geracao e contagens antes/depois.
Somente captura getters P/aN na EDT; outras threads nao fornecem conteudo.
Sem nomes de arquivo/clubes/jogadores/objetos persistidos, sem consulta Swing no
shutdown; o report final copia apenas metadados. Stack por thread limitada8,
overflow descarta pareamento em vez de atribuir retorno interno a inicio externo.
Conteudo consultavel nao prova inicializacao completa; nao promove fase ou evento.

Etapas internas: antes/depois de chamadas az()I/V()V nos corpos selecionados,
capturadas apenas com candidato pareado ativo. Sites exatos: AB/AC um az e um V;
pn nenhum az e um V. Mesmo SHA de classe, sitecounts recusam divergencia. Marcadores
preservam receiver/resultado int, nao substituem chamadas. Report inclui indice/
campos calendar clonados e disponibilidade, no max8etapas por label3. Excecao
conserva somente etapas executadas, nao gera after de chamada que lancou.
Sem transformar classe serializada best.f adicionalmente/polling ou eventos.

Tecnica: ASM interno do JDK, explicitamente exportado **somente para a sonda**.
Nao e dependencia publica do SDK, nem contrato estavel entre versoes Java. Sonda
restrita a Java 17/21; modos normais nao precisam desse export. Sua substituicao
por dependencia externa com licenca/versionamento revisados pode ser necessaria
antes de liberar hooks de producao. A toolchain usa `-source/-target 17` apenas
para essa classe; restante continua `--release 17`.

Callbacks internos sao observacoes diagnosticas, com falhas isoladas; nao executar
trabalho de mods/sidecars neles. Nenhum objeto legado vai para o record de observacao.
Nomes de arquivo e threads entram no log privado: revisar antes de compartilhar.

### Entrega limitada de diagnostico

O launcher enfileira observacoes numa fila de **128 itens**, por `offer` sem
esperar espaco; logging acontece numa thread daemon dedicada, nunca na thread do
metodo do jogo. Fila cheia recusa o item novo e incrementa `dropped`. Ordem e FIFO
de entrada na fila, nao promessa de ordenacao global por `sequence` entre threads
concorrentes. Isso nao e um bus publico multiassinante nem entrega duravel.

Fechamento desassina primeiro e espera no maximo **500 ms**, sem chamar Swing,
objetos legados ou mods. Pendentes descartados sao contados; callback ja executando
nao pode ser cancelado com garantia se ignorar interrupcao. Worker e daemon e
`workerAlive` registra esse limite. `probe-delivery.properties` na sessao registra
accepted/dropped/delivered/failed/discarded, independentemente de LogManager.
`delivered` significa callback retornou, nao fsync nem garantia de que LogManager
ainda aceitava linhas durante System.exit. Perdas nao podem ser usadas como base
para persistencia de carreira. Nenhuma perda e anunciada como evento confiavel.

## Semantica observada

- Load: marker depois do Input.close original, conclusao apenas se metodo termina
  com true e raizes decodificadas. Argumento null nao vira sucesso de leitura.
- Fallback: tracking por profundidade na thread; backup interno bem-sucedido gera
  uma observacao externa com `fallback=true`, mesmo se retorno externo for false.
- Save: sucesso primario depois do Output.close; copia `.sbck` registrada a parte.
  Excecao engolida pelo jogo nao e tratada como sucesso primario.
- Throwable nao capturado pelo jogo e repropagado depois de limpar tracking.
  Hook nao cancela, nao restaura e nao corrige falhas do jogo.
- Sucesso aqui nao e garantia de durabilidade/fsync ou de `.info` escrito.
- Nenhum CareerUnloaded, MatchFinished ou evento publico foi habilitado.
- `roots-reset`: conclusao do construtor original `c.a`, que substitui SR/afQ
  por raizes novas. Nao significa carreira nova pronta, unload ou troca confirmada;
  a inicializacao completa pode acontecer depois. A sonda observa apenas seu retorno.

## Validar sem iniciar UI

```powershell
.\run.ps1 --probe-career-hooks --validate-only
```

Verifica schema da classe transformada na JVM, sem inicializar carreira/mods.
Nao comprova execucao dos hooks.

## Teste automatizado de I/O original

```powershell
.\test-career-hooks.ps1
```

Cria fixture nova privada no Temp/OpenCode; copia EXE/save local para `input.s22`.
Executa rotinas originais na EDT com frame sintetico registrado para evitar
inicializacao completa da interface. Grava somente nomes novos na fixture.
Falhas induzidas usam diretorios nos caminhos novos, nao corrompem saves do usuario.
Resultados incluem shape/schema, leitura, save/backup, releitura, falha primaria,
falha de backup, fallback recursivo, argumento null e recriacao das raizes. EXE/input/origem sao hashados.

Limitacao importante: frame sintetico faz a rotina pular a inicializacao completa
de UI. Nao valida jogo novo, troca/descarregamento de carreira, ordem dos eventos
com UI real, reabertura do EXE ou comportamento em gameplay. Sonda e observador
tambem tem testes sinteticos sem classes proprietarias (fixtures sao codigo proprio).

O teste de I/O original tambem usa dispatcher: metodos executam na EDT da fixture
e os records sao entregues na thread vbrasfoot-probe-dispatch, sem drops/falhas nos
casos exercitados. Isso corrobora o fluxo de entrega nesses metodos, nao transforma
a fila diagnostica em protocolo transacional/duravel.

## Sessao guiada em copia completa

Depois de gerar e instalar um pacote novo somente na copia completa:

```powershell
& '<copia>\Brasfoot22-23\vbrasfoot.cmd' --probe-career-hooks
```

Carregar carreira, consultar, salvar, fechar e reabrir no EXE original da copia.
Conferir `career-hook-probe=Observation[...]` nos logs, uma conclusao por operacao,
threads/ordem e save resultante. Nao usar a pasta original para sondas de falha.
O modo seguro explicitamente recusa a opcao; `--check-save` tambem, mantendo o
verificador offline sem transformacoes.

Sessao guiada ja corroborada para carregar/salvar Flamengo e reabrir no EXE
original da copia (confirmacao do usuario). Log mostrou load em AWT-EventQueue-0
e save em **Thread-1**, ambos completos e save com backup; stderr vazio.
Nao assumir EDT para todos os hooks. Save sem avancar manteve hash, reforcando
que mudanca de timestamp/hash nao e criterio de evento. Detalhes em VALIDACAO.md.

## Pendencias antes da API

### Correlacao de tarefas candidatas0.8.1

Quatro classes: a.cR.oR/a.cS.run e a.hk.oR/a.ho.run. SHA originais inteiros e
exatamente1method/RETURN/taskconstructor/Timer.schedule nos schedulers e
1run/RETURN/Timer.cancel/V-ou-ax nos tasks. cR combina transformacoes existentes
de candidatos com oR, validando SHA original primeiro. Nenhuma mudanca de
campo/nome/hierarquia/schema/cache, thread original, delay, cancel ou excecao.
Constructor de task e observado antes de schedule; run pode iniciar antes do
retorno do scheduler, e report nao presume a ordem inversa. Excecao original
repropagada apos marcador de falha, sem reexecutar/cancelar tarefas.

Tracker usa identidade fraca da tarefa e das raizes, sessao local/geracao e
origin ID/label do candidato AB/AC/pn da thread originaria. Em run associado,
contexto causal e levado a nova thread; tarefas descendentes herdam origin e
parent ID. ThreadLocal sozinho nao e heranca entre threads. ID e apenas sonda,
nao ID persistente de carreira/tarefa do SDK. Comparacao de roots/generation na
entrada/saida recusa stale; fallback de desconhecido e unbound, nao associacao
por nome/tela. Paths AB/AC/pn nao sao origem exclusiva de nova carreira:
rotas de temporada ainda podem usar o mesmo candidato, sem declarar readiness.

Limites:64tasks vivos com weak identity,128reports com eviccao de terminados ou
coletados,8frames por ThreadLocal. Overflow recusa/descarta pareamento em vez de
guardar grafo ilimitado. Uma tarefa cancelada por outro caminho pode permanecer
pendente ate coleta/reset; nao inventar return e nao alterar Timer original.
Reset de sessao invalida epoch; roots/geracao trocados invalidam correlacao no
proximo marker. Nenhum getter/Swing/I/O/callback mod no novo tracker.

creation-async.properties copia metadados primitivos ao fechar: task id/origin/
originLabel/parent/label/generation e scheduleReturned/scheduleFailed/started/
returned/failed/stale, counters rejected/unbound. Returned nao e pronta nem
assert de que Timer foi cancelado corretamente. Report best-effort, nao journal;
run que terminar depois da captura pode nao aparecer como concluido no arquivo.
Callbacks originais nao sao interrompidos nem esperados no shutdown.

19checks tracker sintetico +7bytecode propria fixture aprovados, schema/I/O75
checks originais aprovados (definicao/verificacao das quatro classes, nao executar
oR/run originais/novo jogo). Futuro gate humano: criar nova carreira com nome
inedito na copia e observar qual candidato/task rodou; nao jogar/forcar save.
Salvar automaticamente pelo original continua possivel; nao prometer zero I/O
ao criar. Nao pedir variantes amistoso/temporada antes de ver o primeiro report.

### Caminho sincronico0.8.2

Rodada0.8.1 real7124ea6e-777a-42cd-b080-11be81238907 observou AB/az/V e
nenhuma tarefa capturada (count/rejected/unbound0). Nao afirmar que todo caminho
e sincronico; evidencia negativa limitada aos alvos desta sonda. Guard UNKNOWN,
save de criacao unbound, erro preexistente de equipe E8CC1702/forge_can.ban
inalterado. Por isso0.8.2 detalha corpos V/aa/ap/at/ax de best.f na sonda.

Classe original SHA dbd5bd8633e3a1c2f211e8bd6c322db8f7401c5ccf7cc622cc4050f0624df71b;
metodos/access/descriptor/1RETURN exatos,1site painel em ap/at/ax e0em V/aa.
Transformacao so em memoria com --probe-career-hooks17/21, sem adicionar/remover
campo/metodo/assinatura/hierarquia, sem mudar schema ou bytes de save/cache/EXE.
Grafo best.f serializado exige teste shape e I/O original alem da fixture propria.

Marca ENTER/RETURN/FAILED e PANEL_REQUEST/PANEL_RETURN; request preserva int
original, return preserva JPanel na pilha. Painel apenas solicitado/construido,
nao pronta, renderizado ou sem trabalho pendente. Capture so sob contexto causal
AB/AC/pn ou task associada; fora incrementa progress.outside, nao cria origem.
Ring512 eventos com seq/threadID/origin/parent/label/metodo/stage/panel/generation;
progress.dropped informa eviccoes e impede interpretar ausencia como nao-execucao.
Nenhum getter/I/O/callback/Swing no tracker; excecao original repropagada e
handlers legados preservados. Trace e tasks capturados atomicamente no lock ao
gerar report; tarefas que continuam depois da captura nao sao esperadas.

Mapas de pilha legados descartam this para TOP perto de RETURN em aa. Entrada
observa receiver real; marcadores posteriores/catch NAO leem locals descartados.
receiverKnown=false/matched=false nesses pontos, rootsMatched compara apenas
raizes/epoch/geracao atuais. Nao reinterpretar rootsMatched como identidade
conhecida do receiver ou estado atomico do grafo. PANEL_RETURN usa panel=-1;
o codigo foi observado no request, nao inferido retroativamente no retorno.
Record de FAILED nao substitui Throwable original nem conta como RETURN.

Sem promover nova carreira/UNKNOWN ou produzir eventos SDK novos. Antes de novo
gate real: schema de best.f e I/O original precisam passar com a transformacao.

0.7.4 instrumenta Output.close da biblioteca local por SHA/sitecounts exatos,
sem alterar catch/return/schema/cache em disco. Receivers correlacionados a c.a.ae
evitam atribuir falha de outro Output ao save. Report inclui close subjacente e
IOException engolida; sequencia completa exige close subjacente observado.
Teste de biblioteca original com streams proprios induz IOException e flush
failure preservando a politica original. Nao e teste de disco cheio em save real.
Fingerprint/schema/controle normal/falha/recuperacao aprovados; UI0.7.4 pendente.

Incremento de etapas instrumenta fechamento do FileOutputStream de .info e
retorno de best.f.ap no ramo completo da leitura. Guard registra sequencias
completas/parciais/falhas de save e recusa tickets de saves sobrepostos.
Sites exatos/fingerprint/schema preservados; nenhum evento publico promovido.
Teste original induz falha .info em diretorio novo da fixture, confirmando que
.s22 e backup podem concluir apesar dessa falha. Frame sintetico registra zero
passagens pelo ramo completo de UI; isso nao valida o novo marcador em UI real.

Incremento atual: [guard interno de lifecycle](CAREER-LIFECYCLE.md), independente
da fila diagnostica, com invalidacao conservadora e relatorio privado. Permanece
opt-in; nao substitui a corroboracao de UI nem implementa eventos publicos.

Corroborar UI real e fallback/falhas em todos os caminhos, mapear unload/troca,
fixar momento de commit e geracao de carreira, implementar filas/callbacks limitados
e testar compatibilidade do save produzido. Somente depois anunciar capacidades
publicas de carregar/salvar e prosseguir com P3. Contrato de SDK ainda sem eventos.
