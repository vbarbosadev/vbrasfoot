# Painel independente de carreira

Exemplo MVP **para conferencia humana**, somente leitura, SDK/loader **0.5.0+**,
Java 17+. Mod `br.vbrasfoot.career-dashboard` 1.0.0, API major 1; permissoes
`game.read,ui.register`; capacidades obrigatorias `query.scoped`,
`query.calendar.current`, `query.club.balance`. Cabecalho da UI explicita o alcance.
Sem dependencias externas, rede, persistencia, GlobalData, eventos, polling,
snapshot global, reflection, objetos legados ou escrita de save/gameplay.
Mods Java sao codigo confiavel, nao sandbox.

## Build standalone e testes

Nesta pasta, use o SDK publico atualizado (o JAR antigo nao compila os novos DTOs):

```powershell
.\build.ps1 -Sdk '..\..\dist\vbrasfoot-api.jar'
# Ou copie esta pasta fora do checkout:
.\build.ps1 -Sdk 'C:\SDK\api\vbrasfoot-api-0.8.1.jar'
```

Default: `lib/vbrasfoot-api.jar`. Sem download/install ou build principal. Compila
com `javac --release 17` num `build/` exclusivo, executa testes fakes headless e gera
`dist/career-dashboard.jar`. API/testes nao entram no JAR; manifesto e MIT entram.
Teste headless nao precisa janela nem jogo. Validacao opcional, sem inicializar:

```powershell
java -jar 'C:\SDK\tools\vbrasfoot-validator-0.8.1.jar' .\dist\career-dashboard.jar
```

Com loader0.5.0+ instalado numa copia de teste e jogo fechado:

```powershell
.\install.ps1 -GameDir 'C:\copia-teste\Brasfoot22-23' -Preview
.\install.ps1 -GameDir 'C:\copia-teste\Brasfoot22-23'
```

Preview nao escreve. Installer recusa links/junctions, update pendente e overwrite;
valida sem inicializar, usa runtime.lock, staging e hash. Nao encerra processos,
nao altera configuracao/saves, nao faz hot reload. Build nao instala.

## Workflow manual

1. Carregue a carreira; abra **vbrasfoot → Painel de carreira (data e saldo)**.
2. **Atualizar clubes** descarta selecao, handles e leitura anterior e consulta
   primeira pagina com filtro atual. Nao le data/saldo automaticamente.
3. Use filtro de nome + **Filtrar**/Enter e **Clubes < / >**; cada chamada pede
   no maximo 100 clubes. Nenhuma varredura automatica. Selecione o clube da pagina.
4. **Consultar data e saldo** relê manualmente as duas consultas e mostra campos
   escalares. Trocar clube, filtrar ou paginar limpa a leitura anterior.
5. Reaperte **Consultar data e saldo** para reler o mesmo clube; **Atualizar clubes**
   para renovar handles. Fechar/reabrir descarta tudo; nao consulta ate seu comando.

Saldo e o **long bruto em unidades monetarias do jogo**, inclusive negativo e
extremos long; nao R$, reais, centavos ou conversao. Sem ratios, ranking, formulas
derivadas, recomendacao ou historia financeira. Nome apenas identifica visualmente
o clube observado; nenhum ID/handle e identidade persistente ou cruzada entre saves.
Data mostra `displayedDate` exatamente como recebido, campos gregorianos AD
ano/mes/dia, `sourceTimeZoneId`, `displayTimeZoneId` e indice observado do dia.
Nao chama esse texto de ISO, nao converte fusos, nao infere agenda/calendario completo.

Pagina, data e saldo devem ter o mesmo escopo atual; o saldo tambem deve retornar
o ClubRef escolhido. Divergencias viram `GameQueryException(STATE_CHANGED)` e
limpam tudo. Mesmo escopo **nao garante snapshot atomico** nem revisao de mutacoes;
chamadas sequenciais podem observar instantes diferentes. Handles temporarios
ficam apenas em memoria. Todos os erros, incluindo `STALE_SCOPE`,
`EXPIRED_REFERENCE`, `CLUB_MOVED`, `NO_CAREER`, `STATE_CHANGED`, `UNSUPPORTED_DATA`
e defaults `UnsupportedOperationException`, limpam handles/leitura e pedem refresh,
sem retry, fallback ou inventar dados.

Acao scoped, consultas e janela propria na Swing EDT. Sem janela na initialize.
Shutdown revoga registro, marca stopped antes de callbacks tardios e agenda limpeza
e dispose sem esperar a EDT. Consultas limitadas ainda podem pausar brevemente a UI.

## Cobertura e limites

`DashboardModelTest` executa **43 casos** e cobre sucesso com mesmo escopo, datas/fusos/displayDate,
negativos e extremos long, filtros e offsets 0/100/200, limite 100, vazio/sem carreira,
refresh, divergencia entre pagina/data/saldo/clube, todos os motivos de erro e
defaults antigos unsupported. Fake falha em snapshot, consultas sem escopo,
GlobalData/readData/writeData. Testa registro scoped, shutdown idempotente,
callback tardio inerte, limpeza mesmo se close falha, e shutdown sem esperar EDT.

**Teste visual / integracao com UI real e carreira real: pendente.** Headless nao
prova comportamento visual, latencia, contrato real do jogo ou seguranca do loader.
Para smoke automation, componentes: `career-dashboard.refresh`, `.filter`,
`.clubs`, `.read`, `.output`, `.status` (prefixo completo em cada nome).
Confirmar manualmente filtro/paginacao, leitura, stale/refresh e fechar/reabrir;
nao automatiza decisoes de gameplay. Encerramento abrupto pode nao chamar shutdown.

Contrato: `GameAccess`, `GameQueryException`, `ModContext` no SDK publico;
guias `SCOPED-QUERIES.md`/`SDK-CONTRACT.md` do SDK. Nao necessita GlobalData.
