# P0 — roteiro de validacao real

Este roteiro nao declara compatibilidade. Execute somente na copia isolada.

## Preparacao

1. Feche o jogo normalmente, preservando seu progresso. Nao encerre processos a forca.
2. Execute `build.ps1` e `prepare-validation.ps1 -Preview`.
3. Execute `prepare-validation.ps1`. O destino novo e informado no console;
   o jogo original e somente lido. Links/junctions na origem sao recusados.
4. Guarde `baseline.json`: identidade de todos os arquivos copiados. Nenhum
   backup e restaurado automaticamente. Uma copia interrompida deve ser revisada,
   nao reutilizada como se estivesse completa.

## Sessao segura e sessao com inspetor

Para testes independentes, prepare **dois destinos novos**, um por cenario:

- `safe.cmd`: jogo via loader, sem mods/menu.
- `normal.cmd`: jogo via loader com o inspetor.

Em cada destino:

1. Abra primeiro `original.cmd`, carregue a carreira existente, registre data,
   clube humano, elenco, financas, calendario, competicoes e historicos; feche
   sem salvar. Nunca abra duas instancias simultaneamente.
2. Abra o launcher do cenario, carregue a mesma carreira e compare esses dados.
3. No cenario normal, confirme o botao na barra interna ao lado de Opcoes.
   Consulte o inspetor, troque de tela e volte. Registre screenshot, resolucao,
   escala do Windows e controles originais visiveis. Repita em tamanhos/escalas
   distintos; o log de instalacao sozinho nao comprova posicionamento correto.
4. Avance o calendario/partida, registre as alteracoes esperadas, salve pela
   interface original e feche normalmente.
5. Abra `original.cmd`, carregue o save gravado na copia e compare os dados
   registrados, incluindo financas, calendario, competicoes e historicos.
6. Registre passos, resultados, erros e limites da comparacao. Preserve os logs
   em `loader/logs/session-*/` e os saves da copia como evidencia privada.

## Diagnostico

Cada execucao do launcher tem `session.properties`, `stdout.log`, `stderr.log`
e `loader-*.log`. Capturas sao limitadas a 8 MiB por stream, com aviso de
truncamento; logs do loader rotacionam em tres arquivos de aproximadamente
1 MiB. Sessoes antigas nao sao apagadas automaticamente.

O hook de diagnostico apenas fecha streams/handlers: nao espera pela EDT e nao
executa callbacks de mods. `System.exit` nao garante `shutdown()` dos mods.
Falhas antes de entrar no launcher (Java ausente/JVM incapaz de iniciar), parsing
de argumentos ou validacao do caminho home permanecem no console. Erros legados
gravados somente em `Brasfoot22-23/erros.log` devem ser recolhidos da copia;
nao sao automaticamente incorporados ao log do loader.

Revise caminhos e argumentos antes de compartilhar diagnosticos. Nao envie
saves ou outros dados privados automaticamente.

## Round-trip offline

`normal.cmd --check-save sav\Flamengo.s22` verifica somente copias privadas.
O relatorio inclui tamanho, primeiro byte diferente, repeticao da serializacao
das mesmas raizes e segunda geracao. Esses resultados ajudam a investigar a
diferenca binaria, mas nao demonstram equivalencia de todos os campos do grafo
nem substituem a reabertura no EXE original.

## Gate

P0 permanece pendente enquanto a sessao real, UI em escalas distintas,
encerramento do jogo e investigacao da diferenca binaria nao forem documentados.
Nao instalar na pasta original nem anunciar escrita de gameplay antes dos gates
correspondentes do roadmap.
