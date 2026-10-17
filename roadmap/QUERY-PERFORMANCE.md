# Consultas scoped — medicao e orcamento local (0.4.5)

Incremento P2/P7, **nao gate universal de performance/gameplay**. Medicao usa EXE
conhecido e save copiados para fixture nova, decode original com frame sintetico.
Nao roda inicializacao completa da UI, partida ou save. Sem hooks/permissoes novas.

## Reproduzir

```powershell
.\benchmark-queries.ps1 -Samples 100
# Ou caminhos explicitos, sempre com o jogo fechado:
.\benchmark-queries.ps1 -GameDir 'C:\copia-local-conhecida\Brasfoot22-23' -Save 'C:\copia-local-conhecida\Brasfoot22-23\sav\carreira.s22' -Samples 100
```

Runner exige perfil conhecido, recusa jogo/loader aberto e copia EXE/save sem
overwrite em `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-query-benchmark-UUID`.
Java filho proprio tem timeout 180 s; se exceder, so esse processo e encerrado,
fixture preservada e nenhuma conclusao de gate. Origem e fixture hashadas.
Nao aponta o jogo para sav original nem invoca metodo de gravacao.

Relatorio privado em `loader/query-benchmark.properties` e log do benchmark;
nao versionar/redistribuir EXE/save/raw logs. Relatorio tem hashes/contagens,
sem nomes de clubes/jogadores; ainda revisar antes de compartilhar.

## Metodo

- Cada cenario: 20 aquecimentos e 100 amostras nas execucoes registradas.
- Percentis nearest-rank, nanos preservados no report; p50/p95/max publicados em ms.
- `edt.ns`: tempo do corpo de consulta dentro da EDT; `wall.ns`: chamada externa,
  agendamento/espera da EDT e retorno. Nao medir apenas trabalho fora da EDT.
- `edt.allocated.bytes`: contador ThreadMXBean se suportado/habilitavel; indisponibilidade
  declarada. Alocacao por chamada nao e memoria retida, RSS ou prova de leak.
- Heap amostrado/GC sao globais da JVM entre chamadas, sem System.gc. Peak amostrado
  pode perder picos reais; inclui grafo legado/JIT/framework, nao atribuir ao mod.
- `runtime.prepare` inclui verificacao/preparo de cache novo; `original.decode`
  e load original com frame sintetico. **Nao sao** tempo total de abrir/jogar na UI.
- Cenarios sequenciais fixos, um produtor e JVM nova a cada execucao. Ordem/JIT/GC
  podem influenciar resultados; nao se anuncia benchmark microisolado perfeito.
- Paginas de clubes contam todos os matches e ainda varrem a lista inteira. Filtro
  e nome do primeiro clube, sem serializa-lo. Elenco de maior tamanho observado por
  paginas e consultado apos renovar handle, sem manter refs velhas/expiradas.

## Evidencia local

Windows/Oracle Java **21.0.12**, `-Xmx1536m`, 12 processadores logicos visiveis;
save de **8.395 clubes / 193.831 jogadores**. Mesmo input hash conhecido em todas
as fixtures, sem mutacao. Baseline 0.4.4, duas execucoes com cache 0.4.5:

| Cenario | Baseline EDT p95 | Cache run A | Cache run B | Alocacao EDT p95 baseline → A |
| --- | ---: | ---: | ---: | ---: |
| Clubes, 100 retornados | 5,938 ms | 2,366 ms | 3,089 ms | 1.918.688 → 546.048 bytes |
| Clubes filtrados, 1 retornado | 4,506 ms | 1,669 ms | 2,024 ms | 1.816.000 → 471.872 bytes |
| Clubes, 500 retornados | 5,048 ms | 2,008 ms | 2,402 ms | 2.333.632 → 829.696 bytes |

Maximos nao melhoram necessariamente: pagina 100 baseline 9,912 ms, cache A
12,323 ms e B 4,023 ms. Nao esconder outliers nem concluir latencia garantida.
Alocacao de pagina 100 caiu aproximadamente **72%**, na cobertura local medida.

Para jogadores, baseline usou primeiro elenco (25), e runs novos selecionam maior
elenco observado (**52**). **Nao comparar percentuais** entre esses cenarios. Runs
A/B para 52: p95 limite100 = 0,154/0,225 ms; limite500 = 0,062/0,040 ms. Retorno
52 em ambos, nao 100/500 jogadores. Diferencas de ordem/JIT impedem concluir que
pagina500 e intrinsicamente mais rapida. Nenhuma carga sintetica inflada no grafo.

Baseline preparo/decode: 772,780/760,622 ms; cache A: 785,120/767,152 ms. Esses
tempos distintos nao sustentam ganho de startup, apenas contexto da fixture.

## Otimizacao

`CareerBridge.call` guarda `Method` publico sem parametros por classe via ClassValue
e mapa concorrente. Evita getMethod repetido/copia de metadata em cada jogador/clube.
Sem nomes/campos/schema alterados, sem objetos de carreira guardados no cache.
ClassValue nao e mapa global forte de Class/classloader. Ainda depende da vida das
classes e das referencias do runtime; teste nao e prova exaustiva de descarregamento.
Erros de lookup/invocacao mantem semantica; testes verificam classes diferentes,
mesmo nome em classloaders distintos e concorrencia. API major 1 permanece.

## Orcamento de referencia, nao suporte universal

Para este ambiente/save, propoe-se acompanhar **EDT p95 <= 8 ms** para clubes
ate 500 retornados e **<= 1 ms** para jogadores **ate 52 observados**, com p95
de alocacao EDT <= 1 MiB nos cenarios de clubes. Metas acima da faixa medida com
margem; nao deadline, cancelamento, assert de CI ou promessa para qualquer hardware.
Maximo/outliers/wall/GC sempre devem ser revisados, mesmo se p95 estiver dentro.

Antes de fechar P7: medir UI real com pintura/interacao, varios mods, mais carreiras,
Java 17 e 21/hardware diferente, startup completo e memoria retida/RSS; testar
troca de carreira com profiler apropriado. Nenhum gate integral e fechado por
estes dados, e a varredura linear de clubes continua limitacao explicita.
