# Revisao de elenco, jogadores e emprestimos

Status em 21/10/2026: inspecao estatica complementada por validacao runtime em
fixtures descartaveis isoladas; nenhuma escrita publica do SDK foi adicionada.
Nenhum save pessoal foi aberto ou alterado, nenhuma partida foi iniciada e nenhum
calendario foi avancado durante os testes.

## Alvo e evidencias

- Executavel local: `Brasfoot22-23/bf22-23.exe`, SHA-256
  `2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.
  Hash verificado novamente apos a inspecao.
- JAR privado com classes extraidas para inspeccao: `%LOCALAPPDATA%/Temp/opencode/player-roster-static/game-classes.jar`, SHA-256
  `51d1122bafc958804f4881f74ad5c03c9f827202ad7d275c470b11f435b0a704`
  (1038 classes, 2193321 bytes). Nenhuma classe foi executada durante a
  extracao ou disassemblagem.
- REA: identidade/inspecao do artefato `ev_6fe5bae956075b9ed40fb76d4e1fa2bfda899d9356ed84cd0200f47ed2451c2b`;
  vista de inventario direcionada `ev_5028e7f1bb57fc8c130d91f995f98ce8254519fa440dac476d787ddd165eb57b`.
  Os corpos abaixo foram conferidos por `javap` no JDK local, nao sao observacao
  runtime. Resultados temporarios em
  `%LOCALAPPDATA%/Temp/opencode/player-roster-static/`.

## Rota original de pedido de emprestimo

1. Em `a.iA`, o botao `Emprestimo` encaminha para `sz()`. O handler chama
   `best.l.a(player, clubeDestino)` com o jogador atualmente selecionado (`yK`).
   A pagina de jogadores usa uma lista local da UI; o handler nao passa essa
   lista para a regra de negocio.
2. `best.l.a(best.F, best.ah)` nao recebe lista de candidatos nem testa
   pertencimento a uma lista. Em vez disso verifica o indicador de
   disponibilidade do jogador, clube de destino diferente do atual, limite do
   elenco profissional, limite de emprestimos e a regra de elegibilidade entre
   clubes (`d`). O codigo conta no maximo quatro emprestimos.
3. No resultado positivo, chama `best.F.q(clubeDestino)`. A rota cria um
   `components.t` associado ao jogador e ao clube anterior, registra-o na lista
   global de emprestimos com vencimento de 366 dias e chama
   `best.F.a(clubeDestino, 0, false, true, false)`. Esta ultima rotina altera a
   associacao/elencos e acrescenta um registro de historico. Portanto nao e um
   filtro visual nem uma consulta sem efeitos; o bytecode indica uma transicao
   de emprestimo. A validacao abaixo confirmou save/reopen pela UI, persistencia
   do tracker/historico e a rotina de retorno em replay isolado. A agenda normal
   que dispara o retorno e outros estados continuam fora da cobertura.
4. A UI traduz retorno `1` como “Emprestimo realizado”. Ha uma discrepancia que
   precisa ser resolvida: a regra compara o elenco profissional com 35, enquanto
   a UI traduz o retorno `6` como “Limite de 30 jogadores alcancado”. O teste
   runtime abaixo reproduziu a mensagem com um destino que exibia 39/35. Nao
   normalizar esse limite no SDK sem entender a diferenca.

## Estado, serializacao e efeitos financeiros (estatico)

- A raiz primaria salva e `SR` (`best.f`). Seus campos de instancia `av` (lista
  de `components.t`, acessada por `bt()`) e `au` (historico, acessado por `bo()`)
  nao sao `transient`. Na rota positiva, o construtor de `components.t` insere o
  rastreador em `av`; a rotina de transferencia acrescenta `best.ap` a `au`.
- As listas de clube `best.ah.nd` (`kc()`, elenco profissional usado pela regra)
  e `ne` (`ky()`, outra lista de elenco) tambem nao sao `transient`. A rotina
  chamada pelo emprestimo remove o jogador de `kc()` do clube anterior e o
  adiciona a `kc()` do destino; esse caminho nao altera `ky()`.
- `components.t` tem os campos nao-transient `U` (jogador), `Pm` (prazo), `Pn`
  (clube anterior) e `Po` (marcador). O construtor associa jogador/clube
  anterior, insere o registro em `av` e define `Pm` para o tempo corrente mais
  366 dias.
- Em `best.F`, `bW` (indice numerico do clube) nao e transient, mas `eo` (referencia
  direta ao clube atual) e transient. `n(clube)` atualiza ambos; `fg()` tenta
  reconstruir `eo` por `SR.x(bW)` quando a referencia esta nula. Isso e uma rota
   estatica de reancoragem lazy; a reabertura positiva abaixo valida um caminho
   pelo jogo, sem provar todos os estados de recuperacao.
- O historico `best.ap` guarda ponteiro direto para jogador em `U`, que e
  transient; a mesma criacao grava data, indices dos clubes e valor em campos
  inteiros nao-transient. O valor passado nesta rota e zero. O FieldSerializer
  observado exclui campos static/transient, conforme `roadmap/SAVE-ANALYSIS.md`;
   portanto o grafo indica que rastreador, elencos e dados numericos do historico
   sao candidatos a persistir. O diff offline abaixo encontrou o tracker e um
   registro de historico apos save/reopen, mas nao qualificou todos os campos nem
   todos os objetos do grafo.
- Na chamada `best.F.a(destino, 0, false, true, false)`, o ramo que atualiza
  valores dos clubes e ignorado pelas flags; o valor do historico tambem e zero.
  A rotina tambem grava `best.F.eJ` como a data corrente mais 365 dias (sem
  qualificar aqui a semantica desse campo), enquanto o rastreador vence em 366
  dias. No retorno automatico, `best.F.r(clubeAnterior)` usa valor zero, flags
  diferentes e o caminho de 180 dias para `eJ`. Nao ha mutacao financeira direta
  observada nesses dois caminhos; impacto posterior de salarios/folha durante a
  simulacao nao foi qualificado.
- `best.f.au()` percorre rastreadores vencidos (`tM`). `tN()` retorna o jogador
  ao clube anterior automaticamente se ele nao for humano, ou se o clube humano
  tiver menos de 35 jogadores (`kw()` consulta `nd`). Se o clube humano nao tiver
  vaga, o retorno nao ocorre por esse ramo e o rastreador nao e removido nessa
  passagem; ha um fluxo separado de notificacao/marcador. Estaticamente,
  `best.f.ax()` chama `au()` quando `av` nao esta vazio; a frequencia de `ax()` e
  os efeitos em runtime continuam sem validacao.
- Assim, o limite 35 aparece tanto na aceitacao do emprestimo quanto na condicao
  de retorno a clube humano; o runtime reproduziu a mensagem de UI “30” no erro
  6. Nao converter nenhum desses valores em contrato publico sem resolver a
  discrepancia.

### Conclusao sobre jogador fora da lista

E **possivel em nivel de regra de negocio**, condicionalmente: a funcao original
nao exige pertinencia a lista; aceita uma referencia `best.F` e um clube e
aplica suas proprias verificacoes. O caminho normal da UI, porem, usa o jogador
selecionado em `a.iA`; a varredura estatica de referencias nao encontrou outro
chamador direto de `best.l.a(F, ah)`. Assim, a inspecao nao demonstra um fluxo de
UI normal para selecionar alguem fora da pagina nem prova que uma referencia
fabricada/recuperada seja valida. Isso nao autoriza expor o metodo obfuscado ou
um bypass de lista como API publica.

## Rota distinta de academia de juniores

O levantamento anterior tambem precisava de correcao: existe uma chamada com
flag `true` para `a.cz`, em `a.ds.pL()`, a partir do painel “Academia de
Juniores”. O dialogo pode chegar a `best.F.c(...)`, que manipula as listas `ky`
e o historico. Esse e um fluxo separado da regra `best.l.a(F, ah)` usada pelo
botao Emprestimo; nao o tratar como prova de pedido de emprestimo de jogador
profissional fora da lista. A semantica completa do fluxo de juniores ainda
precisa de qualificacao.

## Validacao runtime e replay controlado em fixtures descartaveis (21/10/2026)

- Fixture em `%LOCALAPPDATA%/Temp/opencode/brasfoot-loan-fixture/`. A semente
  `LOAN_FIXTURE.s22` foi validada offline: 4.153 clubes, 96.203 jogadores e
  round-trip Kryo byte-identico. SHA-256 da semente:
  `232c5f2fe07dd28fb538d103716668822c8314417ba0f7051f0f459a7439289e`.
  O EXE das copias manteve o SHA-256 esperado
  `2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.
- Positivo: na copia `cases/loan-positive`, Diego Esser saiu do Internacional
  para o Athletico; a UI informou “Emprestimo realizado” e o elenco mudou de
  33/35 para 34/35. Sem avancar a data (23/01/2022), salvamos pela UI, fechamos
  normalmente e reabrimos a carreira pela UI. Diego continuava associado ao
  Athletico e o elenco permanecia 34/35. `.s22` e `.sbck` ficaram byte-identicos,
  ambos com SHA-256
  `E192B872A8E448777179F9C546EF1D35BA05E50994AD1A3E408A576BED2B963C`.
- Tentativa separada na copia `cases/loan-refusal`: Ben White (Arsenal) tambem foi
  aceito pelo Athletico. A carreira nao foi salva; o `.s22` em disco permaneceu
  com o hash-semente. Portanto, este caso nao fornece recusa por reputacao.
- Recusa reproduzida na terceira copia, `cases/loan-refusal-low-division`: foi
  criada uma carreira descartavel com Amazonas FC (3a divisao), nome de tecnico
  `Teste`, e o torneio amistoso foi recusado. A primeira tela da carreira mostrava
  05/01/2022 e 39/35 jogadores; nenhuma partida ou data foi avancada. Ao pedir
  Santiago Beltran (River Plate, indicador E ativo), a UI exibiu exatamente
  “Limite de 30 jogadores alcancado”. A evidencia demonstra o caminho de recusa
  por limite de elenco, mas nao isola nem comprova recusa por divisao/reputacao.
  A carreira nao foi salva e foi fechada normalmente; o `.s22` continuou com o
  hash-semente.
- Capturas runtime e copias descartaveis permanecem no diretorio temporario acima.
  Os resultados validam um sucesso e sua persistencia pelo proprio jogo, alem de
  uma recusa por limite; a rotina de retorno abaixo foi exercitada separadamente
  em memoria. Nenhum teste iniciou partida ou avancou a data.
- SaveCheck offline executado na semente e no `.s22` positivo, com homes/cache
  isolados em `savecheck-diff/`: ambos decodificaram 4.153 clubes/96.203 jogadores,
  preservaram a entrada e produziram round-trip Kryo byte-identico.
- Diff read-only dos snapshots: a unica mudanca de associacao foi Diego Esser
  (id 3972; idade/posicao/forca/valor inalterados), de Internacional para
  Athletico Paranaense. O elenco destino foi 33 -> 34 e o de origem 32 -> 31;
  nao foram encontradas outras mudancas de associacao.
- No grafo decodificado, o tracker passou de 0 para 1 e aponta para Diego, clube
  anterior Internacional, prazo bruto `1674563978893` e `expired=false`. O
  historico passou de 0 para 1 registro `best.ap`; o ponteiro transient `U` esta
  nulo depois da leitura, e os demais inteiros observados (`d=23`, `ei=3972`,
  `ej=1`, `on=1`, `oo=130`, `op=280`, `oq=0`, `y=2022`) permanecem sem
  interpretacao semantica neste relatorio.
- Saldo `kb()` nao mudou em nenhum clube: Athletico Paranaense `11587932` e
  Internacional `15000000` antes/depois. Isso cobre somente o saldo imediato
  persistido, nao efeitos financeiros futuros da simulacao.
- Qualificacao controlada do limite em `limit-qualification/`: para cada caso foi
  lida uma nova copia privada da semente; jogadores de preenchimento foram
  transferidos apenas no grafo em memoria para formar elencos de 30, 31, 32, 33,
  34 e 35. A chamada original `best.l.a` retornou sucesso (1), moveu Diego Esser
  e criou tracker/historico para 30-34; com 35 retornou 6 sem mover jogador nem
  criar registros. Em 34, o destino foi a 35; em 35, a recusa ocorreu antes de
  qualquer efeito. Isso confirma o limite funcional **35**, nao 30. A tela que
  mostrou “Limite de 30” em 39 jogadores e a traducao do retorno 6 pela UI; a UI
  nao foi reexecutada nesses tamanhos exatos. Os testes chamaram o metodo de
  negocio original em um harness isolado, nao publicam nem autorizam esse metodo
  como API.
- Qualificacao de elegibilidade tambem em memoria: jogador disponivel id 7090,
  do Sevilla (reputacao 4), contra Ferroviaria (elenco 23, reputacao 1, divisao
  3, outro pais). A regra pura `best.l.d` recusou; `best.l.a` retornou 4 com
  elenco abaixo de 35, sem alterar jogador, elenco, tracker ou historico. Com o
  mesmo destino, controles isolados elevando reputacao para 4 ou divisao para 1
  fizeram a regra aceitar. Isso isola a condicao de reputacao/divisao para esse
  caso, nao todos os perfis ou mensagens da UI.
- Rotina de retorno exercitada sem adiantar o relogio: uma copia do save positivo
  foi lida; somente em memoria, o vencimento do tracker foi posto um milissegundo
  antes do clock da carreira e `best.f.au()` foi chamado. `tM()` mudou de falso
  para verdadeiro; Diego voltou do Athletico ao Internacional, os elencos foram
  34 -> 33 e 31 -> 32, tracker 1 -> 0 e historico 1 -> 2. Os saldos imediatos
  ficaram iguais e `best.f.bc()` nao mudou. O `.s22` de entrada permaneceu
  intacto. Isso valida a transicao de retorno para clube anterior nao-humano,
  nao a frequencia/agenda que chama `au()` no jogo normal.

## Decisao e lacunas

- Nao adicionar mutadores ou permissao/capacidade de escrita nesta etapa. O SDK
  nao oferece identidade persistente de jogador. O incremento SDK 0.9.4 posterior
  adiciona `PlayerRef` somente temporario, emitido junto de consulta scoped do
  elenco e invalidado por troca/reordenacao/substituicao; isso nao e identidade
  persistente nem autoridade de mutacao.
- A investigacao deste ponto esta encerrada no escopo testado: sucesso/persistencia,
  limite funcional 35, um caso de elegibilidade por reputacao/divisao e a rotina
  de retorno para clube nao-humano. Isso nao e aceite para escrita publica. Antes
  de propor uma API ainda faltam identidade/ownership duraveis, contrato/invariantes
  para ambos os elencos, efeito de salarios/folha em simulacao, demais recusas,
  notificacoes e refresh/EDT, disparo normal do vencimento e retorno ao clube
  humano no limite 35. Nao avancar o calendario para obter essas respostas.
- Qualquer validacao futura de escrita permanece restrita a fixture descartavel;
  saves pessoais e o EXE original continuam intocados.
- Loader instalado 0.9.3, API major 1, comparador aprovado e
  `roadmap/STATUS.md` permanecem inalterados. O codigo-fonte de desenvolvimento
  esta em SDK 0.9.4 por causa do handle somente leitura; nenhum mod foi criado.

## Decisoes para o fluxo de mod (21/10/2026)

- Decisao do solicitante: a avaliacao deve ser uma regra automatica nova do mod,
  nao uma escolha manual nem uma capacidade atribuida ao jogo original.
- Decisao do solicitante: usar os 366 dias do tracker original, sem prazo
  configuravel nesta versao.
- Essas escolhas nao qualificam um algoritmo especifico de avaliacao nem mudam
  o comportamento original: `best.l.a` ainda transfere imediatamente e nao cria
  um pedido pendente. O fluxo pendente/avaliacao precisa ser implementado acima
  da regra original, com criterio explicitamente documentado.
- A API 0.9.4 implementa apenas referencias temporarias seguras para jogadores;
  nao inclui escrita de carreira, mod de emprestimos ou permissao de mutacao.
  Seguem bloqueadores de escrita os gates `writeAuthority=false` e
  `operationInclusionProven=false`, a frequencia normal do retorno e o retorno
  ao clube humano cheio, alem de refresh/notificacoes e efeitos de folha.

## Incremento: retorno humano controlado e regra pura do mod

- Fixture nova: `%LOCALAPPDATA%/Temp/opencode/brasfoot-loan-fixture/human-return-2345e4339cca48bf99ea44f4c0b30637`.
  Harness privado `limit-qualification/LoanHumanReturnProbe.java`, com chamadas
  do grafo na EDT. Leitura de copia do positivo; sem writer, UI, partida ou
  alteracao do relogio da carreira.
- Somente em memoria, o antigo clube Internacional foi marcado humano e incluido
  em `aN`; jogadores de preenchimento foram movidos para formar 35 no elenco.
  Isso e um controle artificial de um ramo, nao uma carreira humana criada pela UI.
- Vencimento igual ao clock: `tM=false`. Vencimento `clock-1`: `tM=true`.
  Portanto o comparador e estrito: nao basta chegar ao timestamp exato do prazo.
- Duas chamadas de `components.t.tN()` com origem humana35 recusaram sem mover
  Diego, mudar os dois elencos/historico ou remover o tracker. Liberando uma vaga,
  a chamada aceitou: origem34->35, destino34->33, historico+1, saldos imediatos e
  clock inalterados. O helper NAO remove o tracker: remocao pertence a `best.f.au`.
- SHA256 do save de origem e da copia apos o teste:
  `e192b872a8e448777179f9c546ef1d35ba05e50994ad1a3e408a576bed2b963c`.
  EXE da fixture antes/depois manteve o hash do perfil.
- Limites: nao executou `au` para origem humana, suas notificacoes/coach,
  remocao do tracker, save/reopen ou agenda normal. O ramo do helper humano foi
  qualificado; o fluxo externo humano permanece pendente.
- Inspecao estatica de `best.f.ax`: primeiro pode chamar `n(int)` e chama `az()`;
  so depois, havendo trackers, chama `au()`. A mesma rotina tambem chama `q(1)` e
  outras operacoes de progressao/UI. NAO executar `ax` como getter nem como teste
  isolado de retorno: violaria a guarda de nao avancar calendario. Essa inspecao
  nao comprova frequencia normal nem execucao runtime da agenda.
- Incidente de harness: o nome PowerShell `$home` conflitou com `$HOME` reservado;
  primeira tentativa gerou copia de entrada/cache sob o perfil do usuario.
  Arquivos gerados foram identificados por hash/conteudo e timestamps e movidos,
  sem apagar outros dados, para `misplaced-test-artifacts-1c511f8df6c9496eb2b850d4c49de13a`
  dentro da fixture. Teste repetido com `$fixtureHome` no diretorio correto, aprovado.
- `LoanClubAssessment` implementa somente o criterio novo `destination-median/1`:
  destino com35+ recusa; elenco vazio retorna dados insuficientes; caso contrario,
  recomenda aceite se forca do candidato <= mediana do elenco destino. Mediana
  par preserva meio ponto e limites int usando long. Esta e uma recomendacao do
  mod, nao a regra original, permissao de clube ou autorizacao de transferencia.
  Disponibilidade/elegibilidade originais ainda precisarao de revalidacao no efeito.
- `LoanClubAssessmentSelfTest`:14 checks aprovados isoladamente, incluindo
  34/35/39, mediana impar/par, vazio, ordem, entradas nulas e extremos int.
  Sem fila persistente, UI de pedidos, adapter de escrita ou mod instalavel novo.

## Incremento posterior: processador humano e pedidos de sessao

### Rotina externa, nao a agenda de progressao

Harness privado atualizado em `limit-qualification/LoanHumanReturnProbe.java`, modo
`processor`. Agora recusa caminhos fora da fixture temporaria antes de gerar arquivos.
Executado em duas pastas novas:

- `human-processor-d5eb15c426b04776b966f6c6232654e6`: teste em memoria de `best.f.au`.
- `human-processor-codec-cab22eca786249f58e9c019d3e45ec95`: mesmo teste mais gravacao
  e releitura OFFLINE das duas roots pelo codec Kryo original.

O antigo clube foi convertido artificialmente em humano como antes. Para observar
as mensagens, recebeu explicitamente o mesmo coach do destino humano da fixture;
nao e uma configuracao normal de duas carreiras nem aceite de ownership do coach.
As chamadas do processador ocorreram na EDT; codec em thread principal.

Resultados observados:

1. Origem35: `au` nao moveu o jogador, nao alterou historico e reteve o tracker.
   Criou uma mensagem raw28/82 e marcou `tQ=true`.
2. Segunda passagem ainda em35: nenhuma segunda mensagem, sem mudanca de elenco.
3. Liberada uma vaga somente em memoria: origem34->35, destino34->33,
   historico+1, tracker removido e uma mensagem raw29/83.
4. Passagem posterior: nao duplicou historico nem mensagem.
5. Saldos imediatos e clock preservados em todas as passagens.
6. O codec offline preservou, apos releitura: clock/historico, ausencia de tracker,
   tamanhos dos elencos e uma unica ocorrencia do jogador conhecido (id3972 e nome
   Diego Esser) no Internacional, com `fg()` reancorado ao mesmo clube.

Saida `returned-codec.s22` da segunda fixture: SHA256
`5dc558858e31199c024a9a90954cdce2e4ede3a4011d2e353460973e430aa045`.
Input e copia privada continuam SHA256
`e192b872a8e448777179f9c546ef1d35ba05e50994ad1a3e408a576bed2b963c`;
EXE da fixture manteve o hash do perfil. Nenhuma partida ou data avancada.

Isto amplia a cobertura anterior do helper para o processador externo humano,
incluindo contagem/codigos raw de notificacoes e remocao de tracker. NAO valida
texto/renderizacao/refresh de UI, coach exclusivo, writer original, reabertura
standalone, durabilidade ou inclusao causal de comando no save. A agenda `ax`
continua apenas inspecionada estaticamente e nao foi executada.

### Fila interna, sem persistencia nem efeito

`LoanRequestQueue` implementa estados PENDING/APPROVED/REFUSED/INSUFFICIENT_DATA/
CANCELLED/INVALIDATED para ate32 propostas por instancia. Inputs escalares sao
copiados e exigem o mesmo query scope; mesmos key/payload retornam o estado ja
registrado, payload diferente conflita e nenhuma key e reciclada. Avaliacao e
passo explicito usando `LoanClubAssessment`. Cancelamento/invalidation/close
nao executam chamadas de jogo; close impede novas operacoes e invalida pendentes
ou aprovados. Snapshots sao imutaveis e nao contem objetos do jogo.

`LoanRequestQueueSelfTest`:21checks aprovados isoladamente; regra pura14checks
aprovados novamente. Ambos incluidos em build.ps1. A fila NAO autentica handles,
nao prova clubes distintos por tokens distintos, nem que os inputs permanecem
atuais; tudo isso exige revalidacao futura pelo adapter. Nao esta conectada a
Main, ModContext, eventos, gravacao em disco ou UI. Aprovacao e recomendacao,
NAO emprestimo executado. Pedidos nao sobrevivem a shutdown/reload e nunca devem
persistir `PlayerRef`/`ClubRef` como identidade duravel.

Proximos gates: protocolo de pedido duravel e identidades na revisao exata,
invariantes/revalidacao de ambos os elencos, adapter+gate+journal sem replay,
recovery/save/reopen original e UI antes de liberar escrita publica.

Incremento posterior: [LOAN-WRITE-PIPELINE.md](LOAN-WRITE-PIPELINE.md) registra
contrato duravel, store, dispatcher e adapter internos testados em fixtures;
nao os confundir com permissao publica ou mod instalavel concluido.
