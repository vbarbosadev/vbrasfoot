# SDK 0.2.0 — API major 1 (experimental)

Compile somente contra `vbrasfoot-api.jar`, com Java 17+. O build do inspetor usa
somente esse JAR no classpath, sem classes do jogo/loader. API major 1 mantem os
metodos anteriores; recursos novos exigem `minLoaderVersion=0.2.0`. A identidade
publica esta em `Sdk`. O loader aceita somente o SHA-256 do perfil documentado.

## Manifesto UTF-8

```properties
manifestVersion=1
id=br.exemplo.consulta
name=Consulta
version=1.0.0
apiVersion=[1,2)
minLoaderVersion=0.2.0
gameProfile=brasfoot22-23
mainClass=br.exemplo.Consulta
dependencies=
optionalDependencies=
conflicts=
permissions=game.read,ui.register
requiredCapabilities=query.paged
```

Manifestos antigos continuam aceitos (`apiVersion=1`, campos novos opcionais).
`apiVersion` aceita um major exato suportado ou intervalo inteiro `[min,max)`;
nao e um parser de ranges SemVer. `minLoaderVersion` usa `major.minor.patch`.
Campos desconhecidos, versoes incompativeis e capacidades ausentes sao recusados.
IDs obrigatorios ausentes, duplicatas, ciclos e conflitos impedem a inicializacao.
Dependencias opcionais presentes tambem ordenam inicializacao e podem formar ciclos.
Dependencias sao IDs, nao ranges de versao de mods; nao compartilham classes privadas.

Capacidades disponiveis: `game.read`, `ui.register`, `data.read`, `data.write`,
`query.paged`. Permissoes sao as quatro primeiras; `query.paged` nao concede acesso
sem `game.read`. Nao ha eventos reais, escrita de gameplay nem sandbox.

## Consultas

```java
var page = context.game().clubs("Flamengo", 0, 50);
for (var club : page.items()) {
    var players = context.game().playersAt(club.index(), 0, 100);
}
```

`offset >= 0`, `1 <= limit <= 500`; listas retornadas sao imutaveis. Clubes sao
filtrados por nome, ignorando maiusculas via Locale.ROOT (nao remove acentos).
Uma pagina de clubes nao copia jogadores; uma de jogadores copia somente o trecho
solicitado de um clube. Ausencia de clube retorna pagina vazia. `loaded=false`
indica ausencia de carreira identificada pelo bridge.

IDs sao somente os inteiros observados na carreira corrente e podem se repetir.
`players(id, ...)` recusa IDs ambiguos; prefira `playersAt(club.index(), ...)`.
O index e uma posicao na lista atual, nao uma identidade permanente.
IDs nao sao identidade global nem contrato de estabilidade entre carreiras,
copias ou novas sessoes.
No save offline testado, 8.395 clubes produziram apenas um valor distinto no
acesso legado historicamente exposto como `id`. Nao use esse campo como chave
unica; sua semantica completa ainda nao esta estabelecida. O campo e mantido
somente por compatibilidade com a API anterior.
Reconsulte ao trocar de carreira. Nao existe token de geracao nesta versao: paginas
consecutivas nao sao snapshot atomico e podem observar momentos diferentes.

`snapshot()` permanece por compatibilidade e copia toda a carreira na EDT; evite
seu uso em novas interfaces. O inspetor 0.2.0 usa filtro/paginas de 100 clubes e
mostra ate 500 jogadores do clube selecionado, com limite informado na interface.
Consultas paginadas tambem executam leitura na EDT,
para nao ler objetos legados concorrentemente; a varredura de clubes ainda e linear.
Nao ha orcamento de desempenho aprovado sem medicao em sessao real.

## Lifecycle e threads

O loader registra `DISCOVERED -> VALIDATED -> INITIALIZED -> ACTIVE`. Inicializacao
e encerramento rodam na thread principal; acoes de UI na EDT. Falha de inicializacao
marca `FAILED` e aborta o carregamento, sem iniciar dependentes. Encerramento normal
ocorre em ordem inversa; falhas de shutdown sao registradas e os demais continuam.
`DISABLED` esta reservado para a configuracao futura de mods; nao e emitido hoje.
`System.exit` nao garante `shutdown()` de mods. Hot reload nao e suportado.

Use `registerScopedAction` e feche a assinatura para remover a acao (remocao
agendada na EDT, close idempotente). Acoes antigas sao removidas quando o loader
fecha a UI normalmente. Nao bloqueie a EDT nem espere por ela em shutdown hooks.
Threads, timers e janelas criados pelo mod sao responsabilidade do autor.

Dados continuam globais em `data/<mod-id>/` para preservar compatibilidade. Nao
assuma vinculo com save; dados por carreira dependem de P3/P4. Nao ha registro de
servicos entre mods; mantenha suas classes privadas isoladas.

## Ainda pendente de P2

Leitura de calendario, competicoes, financas, historicos e partidas exige evidencias
especificas do 22-23. Nao foi adicionada por suposicao. Identidade/geracao de carreira,
eventos, cancelamento de tarefas e politicas completas de callbacks sao futuros.
