# Observacoes experimentais para mods — SDK 0.4.0 / API major 1

`ModContext.careerObservations()` exige **events.career.read**. Manifesto pode
requerer **events.career.observed**; conjunto que a requer e recusado sem
`--probe-career-hooks`, antes de inicializar mods. `--validate-mod` reconhece a
capacidade condicional, mas nao habilita hooks. Modo seguro ignora mods/hooks.

Este contrato experimental nao equivale aos eventos completos de P4. Nao fornece
CareerUnloaded, MatchFinished, identidade persistente, cancelamento do jogo ou
persistencia por carreira. Nunca usar nomes/geracoes como identidade de sidecar.

## Eventos observados

| Kind | Significado |
| --- | --- |
| CURRENT_STATE | Replay inicial do estado interno na inscricao; nao e load novo |
| INVALIDATED | Tentativa de load/reset/setter ou atribuicao de raiz; UNKNOWN nao e unload |
| READ_COMPLETED | Decode/retorno aceitos na geracao atual e tentativa exclusiva; fallback externo gera um so evento |
| PRIMARY_WRITE_COMPLETED | Output.close original e conclusao aceitos para ticket ativo atual; nao garante backup/.info/fsync |

Incremento interno recusa primary write quando houve sobreposicao de saves.
Diagnostico separado observa .info/backup/retorno, sem ampliar a semantica deste
Kind ou adicionar um evento CareerSaved. Resultado completo observado nao e
durabilidade. Veja CAREER-LIFECYCLE.md; API/manifesto permanecem os mesmos.

0.7.4 observa tambem o close subjacente e o handler IOException do Output original.
Falha conhecida suprime PRIMARY_WRITE_COMPLETED; nao muda o retorno/catch/backup
do jogo nem adiciona fsync. completeSequence do diagnostico privado exige close
subjacente observado; este Kind continua experimental, nao CareerSaved duravel.

`State` tem session, generation, revision, phase, reads, primaryWrites e healthy.
Nao contem nomes/caminhos, referencias legadas ou referencias mutaveis. Sessao e
geracao pertencem ao guard, **nao** sao a CareerScope do SDK. EMPTY significa nao
observado, ACTIVE significa leitura aceita, nao toda UI pronta. Tentativas
sobrepostas/falhas deixam UNKNOWN; overflow do tracking interno deixa healthy=false.

`state()` independe de perdas dos assinantes. Fechamento interno publica apenas
estado CLOSED, nao entrega unload ou promete callback final. Encerramento forcado
pode impedir cleanup/reports. O report privado ainda inclui arquivos; revisar
antes de compartilhar, apesar de a API nao expor caminhos.

## Ordem, threads e falhas

- Estado/revisao atualizados no lock interno; enfileiramento sem executar codigo
  de assinantes no produtor. Revisions estritamente crescentes em cada sessao.
- Inscricao enfileira replay e registra assinante no mesmo lock do hub: sem gap
  de transicao entre replay e assinatura. Replay e prefixo, nao repeticao de load.
- Um worker daemon **por assinatura**, fila **64**, ate **32 workers** por sessao
  e **4 assinaturas ativas** por mod. Codigo retirado que ignora interrupcao ainda
  conta para o limite de workers ate terminar; nao gera threads ilimitadas.
- Ordem FIFO por assinante, sem retry. Fila cheia: OVERFLOW, limpa pendentes,
  desassina e interrompe worker. Excecao de callback: CALLBACK_FAILED. Outras
  assinaturas e simulacao continuam. Codigo Java nao e sandbox: callback que
  ignora interrupcao nao pode ser morto com garantia.
- `status()` preserva causa terminal; recovery explicito fecha/reassina e recebe
  CURRENT_STATE atualizado. Nao usar esta fila como journal de persistencia.
- `close()` nao espera callbacks. Um callback ja selecionado pode iniciar/terminar
  depois de close; queued descartados. Loader fecha assinaturas antes do shutdown
  do mod e apos falha de initialize; servico retido nao aceita novas inscricoes.
- Cleanup do hub tem **500 ms totais**, nao por assinatura; dispatcher diagnostico
  separado tem seus proprios 500 ms. Sem consultas EDT no shutdown da sonda.

Updates sao historicos: uma leitura anterior pode chegar depois de invalidacao no
jogo. Compare session/generation/phase com `state()` e consulte novamente se preciso;
comparacao nao torna consultas atomicas. Nunca usar evento para mutar grafo antigo.
Callbacks nao rodam na EDT: use SwingUtilities.invokeLater para UI, nao aguarde
EDT durante encerramento. Nao ha escrita de gameplay na API.

## Exemplo e cobertura

[Observador](../examples/career-observer/README.md) compila apenas contra JAR publico.
JAR separado em dist e sdk/examples, **nao instalado automaticamente** ou habilitado
em modo normal. Declara somente events.career.read, registra dados sem caminhos.

33 checks sinteticos de replay/ordem, stale completion, perda, callback falho,
worker lento, limites, close, permissao e ownership. Teste original de I/O usa
esse JAR SDK-only e verifica tres reads (incluindo fallback uma vez) e tres writes
primarias; frame sintetico, nao partida/UI completa. Suites/gates em VALIDACAO.md.

Pendente: criar/trocar/descarregar pela UI, todas as rotas originais, save commit
duravel e P3. Nenhum aceite integral do roadmap e inferido destes testes.
