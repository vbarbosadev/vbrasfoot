# Consultas scoped e handles temporarios de jogador

API major 1 continua compativel com mods antigos. Novos recursos exigem
`minLoaderVersion=0.3.0`, `requiredCapabilities=query.scoped` e permissao `game.read`.
Manifestos 0.2.0 permanecem aceitos; nao houve migracao de saves/dados externos.

Desde o SDK 0.9.4, `scopedPlayersWithReferences` tambem exige
`minLoaderVersion=0.9.4`, `requiredCapabilities=query.player.references` e `game.read`.
Esse recurso apenas fornece referencias opacas e temporarias; nao concede mutacao.

```java
var page = context.game().scopedClubs("Flamengo", 0, 100);
for (var item : page.items()) {
    var players = context.game().scopedPlayersWithReferences(item.reference(), 0, 100);
    for (var player : players.items()) {
        // player.reference() e valido somente nesta encarnacao do grafo/lista.
    }
}
```

## O que o escopo significa

`CareerScope(session, generation)` identifica uma **encarnacao observada do grafo
na sessao de consulta**. Session e aleatoria por instancia do bridge; generation
aumenta quando uma consulta observa referencias diferentes de SR, afQ ou listas
de clubes/humanos. A deteccao ocorre na consulta, nao na fila diagnostica: funciona
tambem sem hooks e nao sofre com drops do dispatcher.

**Nao e:** identidade permanente de carreira, numero de versao do save, revisao de
todas as mutacoes, evento CareerLoaded/Unloaded, ou transacao. Carreira A reaberta
produz grafo novo e invalida referencias antigas mesmo sendo o mesmo arquivo.
Renomear/copiar/Save As e associacao persistente permanecem P3/P4.

`ClubRef` tem scope e token opaco; nao se usa o ID ambiguo nem so o index legado.
O bridge guarda somente referencia fraca ao clube e posicao observada. Ao consultar
players verifica scope, validade do token e identidade do objeto naquela posicao.
Reordenacao/substituicao recusa o handle em vez de selecionar outro clube.

`PlayerRef` e emitido apenas junto de `scopedPlayersWithReferences` para um `ClubRef`;
ele aponta para o objeto exato e posicao observada dentro daquele elenco. IDs de
jogador nao sao usados como identidade, inclusive se repetidos. Mudanca de clube,
reordenacao ou substituicao invalida o handle com `PLAYER_MOVED`; limite LRU pode
expira-lo com `EXPIRED_REFERENCE`. Nao persista tokens em configuracoes/sidecars,
nao os reuse em outra carreira/sessao e nao os trate como garantia para alem da
consulta/revalidacao feita pelo loader. O handle identifica uma linha do elenco de
origem; nao prova membership global unico, ownership exclusivo nem prontidao para
qualquer futura mutacao.

Registro conjunto de clubes e jogadores limitado a **4.096 handles**, com descarte LRU. Leituras de outros mods
podem provocar expiracao; isso e explicitamente tratado, nao sandbox. Referencias
fracas nao mantem grafos de carreiras anteriores vivos. Raizes/listas trocadas
invalidam todo o registro da encarnacao anterior na proxima consulta.

## Erros e threads

`GameQueryException.reason()` identifica:

- `STALE_SCOPE`: outra encarnacao/bridge; consulte clubes novamente.
- `EXPIRED_REFERENCE`: token expirado/inexistente; consulte novamente.
- `CLUB_MOVED`: posicao/objeto mudou; consulte novamente.
- `PLAYER_MOVED`: clube, posicao ou objeto do jogador mudou; consulte novamente.
- `NO_CAREER`: bridge nao identifica carreira carregada nesse estado.
- `STATE_CHANGED`: referencias/listas mudaram durante leitura; consulte novamente.
- `UNSUPPORTED_DATA`: novos dados0.5.0 nao representaveis; nao converter calendario
  por suposicao nem fazer refresh infinito. Veja CALENDAR-BALANCE.md.

Offset/limit invalidos ainda retornam IllegalArgumentException. Nao retry
automatico infinito; o inspetor esvazia a tabela e pede Atualizar quando obsoleto.
Erros tipados atravessam a fronteira EDT sem virar um erro generico de reflexao.

Leituras acontecem na EDT. Referencias de raizes/listas sao conferidas antes/depois;
players confere novamente identidade/posicao do clube, e a API de PlayerRef tambem
confere o objeto e a posicao exatos dentro do elenco. Isso **nao
e lock** sobre o legado: mutacoes in-place concorrentes, troca A->B->A entre
observacoes, alteracoes internas sem mudar identidade/tamanho e falhas parciais de
load podem nao ser detectadas. Campos numericos nao formam snapshot transacional.
O escopo nao certifica conclusao de carregar ou descarregar. Hooks/lifecycle e
politica de escrita precisam resolver esses limites antes de P3/P6.

APIs antigas `snapshot`, `players(id)` e `playersAt(index)` permanecem para
compatibilidade, **sem garantias de escopo**. O inspetor 0.3.0 usa scopedClubs e
scopedPlayers. Implementacoes antigas de GameAccess recusam os metodos scoped
por default (UnsupportedOperationException), sem inventar seguranca a partir de
snapshots. O JAR publico nao expoe raizes/listas/classloader/reflexao do jogo.

## Testes

`QueryScopesSelfTest`: escopo estavel, troca de raizes/listas/bridge, handles
expirados/forjados, clube/jogador reordenados, limite conjunto e propagacao de erro tipado pela EDT.
`test-career-hooks.ps1`: rotinas originais na fixture privada corroboram que reload
e recriacao de raizes recusam handles anteriores e leitura nova funciona.
O teste usa frame sintetico; nao fecha lifecycle completo na UI real.
