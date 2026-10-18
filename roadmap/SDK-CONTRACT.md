# SDK 0.9.3 — API major 1 (experimental)

Contrato aditivo0.9.0: query.player.details / scopedPlayerDetails; veja
PLAYER-DETAILS.md para Optional de habilidades, limites e recuperacao de erros.

Novo contrato delimitado: [CareerIoEvents](CAREER-IO-EVENTS.md), opt-in0.8.0.
Eventos de I/O qualificados, nao lifecycle completo/durabilidade ou sidecars.

[Primeiro mod sem checkout](SDK-QUICKSTART.md) · [Compatibilidade](SDK-COMPATIBILITY.md).
[Criacao assistida por IA e contrato JSON](AI-MOD-AUTHORING.md).
[Calendario atual/saldo somente leitura](CALENDAR-BALANCE.md), loader0.5.0+.
[Paineis dentro da janela do jogo](EMBEDDED-UI.md), loader0.6.0+.

Contrato anterior preservado por compatibilidade. Novas consultas 0.3.0 em
`SCOPED-QUERIES.md` adicionam handles com escopo; APIs por index abaixo continuam
sem garantia entre carreiras. Observacoes opt-in 0.4.0 em `CAREER-OBSERVATIONS.md`;
eventos completos/persistencia por carreira ainda indisponiveis.

Compile somente contra `vbrasfoot-api.jar`, com Java 17+. O build do inspetor usa
somente esse JAR no classpath, sem classes do jogo/loader. API major 1 mantem os
metodos anteriores; paginas/registro scoped exigem 0.2.0, handles 0.3.0 e observacoes
0.4.0. Declare minLoaderVersion conforme recursos usados. A identidade
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
`ui.panel.embedded`, `query.paged`, `query.scoped`, `query.calendar.current`, `query.club.balance` e,
somente com hooks opt-in, `events.career.observed`.
Permissoes sao as quatro primeiras mais `events.career.read`; capacidades de
consulta nao concedem acesso sem `game.read`. Observacoes nao sao eventos completos,
escrita de gameplay ou sandbox.

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
Reconsulte ao trocar de carreira. APIs legadas por ID/index nao possuem token de
geracao; handles das APIs scoped 0.3.0 tem contrato separado. Paginas consecutivas
nao sao snapshot atomico e podem observar momentos diferentes.

`snapshot()` permanece por compatibilidade e copia toda a carreira na EDT; evite
seu uso em novas interfaces. O inspetor 0.2.0 usa filtro/paginas de 100 clubes e
mostra ate 500 jogadores do clube selecionado, com limite informado na interface.
Consultas paginadas tambem executam leitura na EDT,
para nao ler objetos legados concorrentemente; a varredura de clubes ainda e linear.
Ha medicao local de queries com decode original/frame sintetico e orcamento de
referencia em [QUERY-PERFORMANCE.md](QUERY-PERFORMANCE.md), nao aprovado para toda
UI/hardware. Varredura de clubes continua linear; alocacao nao equivale a heap retido.

## Lifecycle e threads

O loader registra `DISCOVERED -> VALIDATED -> INITIALIZED -> ACTIVE`. Inicializacao
e encerramento rodam na thread principal; acoes de UI na EDT. Falha de inicializacao
marca `FAILED` e aborta o carregamento, sem iniciar dependentes. Encerramento normal
ocorre em ordem inversa; falhas de shutdown sao registradas e os demais continuam.
Selecao proximo inicio e por JAR em `MOD-MANAGEMENT.md`; nao muda estado da sessao.
`DISABLED` nao e emitido como callback do SDK: JAR excluido nao e inicializado.
`System.exit` nao garante `shutdown()` de mods. Hot reload nao e suportado.

Use `registerScopedAction` e feche a assinatura para remover a acao (remocao
agendada na EDT, close idempotente). Acoes antigas sao removidas quando o loader
fecha a UI normalmente. Nao bloqueie a EDT nem espere por ela em shutdown hooks.
Threads, timers e janelas criados pelo mod sao responsabilidade do autor.

### Recursos do contexto (garantias consolidadas no loader 0.4.6)

Declare minLoaderVersion=0.4.6 se depender destas garantias de revogacao automatica;
loaders anteriores mantem assinaturas mas nao necessariamente esse comportamento.

Todas as acoes registradas pelo contexto, inclusive `registerAction`, pertencem ao
mod: falha parcial de initialize ou inicio do encerramento revoga dispatch e
assinaturas; remocao visual e agendada, sem esperar pela EDT. Close de uma assinatura
e idempotente e nao remove uma nova acao que reutilizou o mesmo ID. IDs locais
iguais em mods diferentes sao independentes. Falha de shutdown nao impede limpeza
do proprietario nem encerramento dos demais em ordem inversa.

Durante shutdown normal, consultas/dados globais permanecem disponiveis para
limpeza final, mas novas acoes/assinaturas sao recusadas. Apos seu retorno/falha,
ou falha de initialize, servicos do contexto e facades previamente obtidas recusam
operacoes com IllegalStateException. Logger permanece utilizavel para diagnostico.
FAILED de inicializacao nao vira STOPPED apenas porque cleanup terminou. Loader
fechado recusa load; nao e hot reload. Inicializar/fechar loader segue sequencial.

Revogacao nao cancela callback/consulta/I/O ja iniciado e nao espera por codigo de
mod em andamento. Threads, janelas, timers e referencias mantidas pelo proprio mod
continuam responsabilidade do autor. Nao equivale a sandbox, prova de ausencia de
leak ou suporte confiavel a shutdown de mods via System.exit.

Dados continuam globais em `data/<mod-id>/` para preservar compatibilidade. Nao
assuma vinculo com save; dados por carreira dependem de P3/P4. Nao ha registro de
servicos entre mods; mantenha suas classes privadas isoladas.

## Ainda pendente de P2

Agenda completa, competicoes, historicos/operacoes financeiras e partidas exigem
evidencias proprias; data atual/saldo delimitados estao em CALENDAR-BALANCE.md.
Identidade/geracao de carreira,
eventos completos e cancelamento de tarefas sao futuros; observacoes limitadas e
politica de callback experimental documentadas em CAREER-OBSERVATIONS.md.
