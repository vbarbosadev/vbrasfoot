# Comparador independente de jogadores

Mod somente leitura, SDK/loader **0.6.0+**, Java 17+. Manifesto
`br.vbrasfoot.player-comparator`, permissoes `game.read,ui.register`, capacidade
`query.scoped,ui.panel.embedded`. Sem dependencias externas, classes do jogo, acesso raw/reflection,
arquivos de dados, persistencia, rede, threads de background ou escrita de gameplay.
Mods Java sao codigo confiavel, nao sandbox.

## Build e testes standalone

Nesta pasta, sem executar o build principal do loader:

```powershell
.\build.ps1 -Sdk '..\..\dist\vbrasfoot-api.jar'
```

Fora do checkout, copie esta pasta e use o JAR publico do SDK-only:

```powershell
.\build.ps1 -Sdk 'C:\SDK\api\vbrasfoot-api-0.6.0.jar'
```

O default e `lib/vbrasfoot-api.jar`. Nao baixa nem instala dependencias. Compila
recursivamente os pacotes e classes internas, testa em headless e gera
`dist/player-comparator.jar` com apenas classes do mod e manifesto; API e testes
ficam fora do JAR. Cada build usa diretorio exclusivo em `build/`.

Validacao opcional, sem abrir o jogo ou inicializar o mod:

```powershell
java -jar 'C:\SDK\tools\vbrasfoot-validator-0.6.0.jar' .\dist\player-comparator.jar
```

Build nao instala o mod. Com jogo fechado e loader0.6.0+ instalado, revise o JAR:

```powershell
.\install.ps1 -GameDir 'C:\copia-teste\Brasfoot22-23' -Preview
.\install.ps1 -GameDir 'C:\copia-teste\Brasfoot22-23'
```

Nao sobrescreve JAR existente, valida sem inicializar, usa runtime.lock e staging;
preserva configuracao/saves. Sem hot reload. Carregue uma carreira e abra
**vbrasfoot → Comparar dois jogadores**.

Nova acao **Comparador dentro do jogo** usa ModContext.registerPanel: mesmo modelo
em painel mod-owned, sem JDialog, overlay dentro da janela atual com paleta/fontes
copiadas dos controles locais. Voltar ao jogo/Escape descarta e restaura camada
anterior. Tela original nao e reparentada/substituida; nao pausa simulacao e nao
injeta widgets em qualquer tela. Veja EMBEDDED-UI.md no SDK; UI real pendente.

## Workflow da interface

1. Clique **Atualizar tudo** para consultar clubes e limpar selecoes antigas.
2. Em A e B, filtre pelo nome do clube e navegue com **Clubes < / >**.
3. Escolha um clube; navegue com **Jogadores < / >** se necessario.
4. Selecione uma linha e clique **Escolher A/B**. Pode usar o mesmo clube nos dois
   lados ou clubes diferentes. Ordenar a tabela nao altera o jogador escolhido.
5. A tabela inferior mostra lado a lado os campos publicos, sem pontuacao composta,
   deltas, recomendacao ou vencedor. Filtrar/paginar preserva escolhas no mesmo
   escopo; **Atualizar tudo** relê e exige escolher novamente.

Cada chamada pede no maximo **100** clubes/jogadores, sem percorrer automaticamente
a carreira, sem snapshot global de 194 mil jogadores. Apenas paginas atuais e dois
DTOs escolhidos ficam em memoria. ClubRef/CareerScope sao handles temporarios de
consulta, nao IDs permanentes; nenhum ID e usado para localizar ou persistir dados.
Nenhuma referencia ao objeto legado do jogo e recebida/retida.

`STALE_SCOPE`, `EXPIRED_REFERENCE`, `CLUB_MOVED`, `STATE_CHANGED` e `NO_CAREER`
invalidam paginas e escolhas e pedem **Atualizar tudo**. Nao ha retry automatico ou
fallback para API sem escopo. Mudancas de escopo entre paginas tambem invalidam
ambos os lados. O usuario pode fechar e reabrir a janela para descartar tudo.
Acao, consultas e UI rodam na EDT; shutdown revoga o registro e descarta a janela
na EDT de forma agendada, sem esperar pela EDT nem callbacks tardios reabrindo-a.
Nao ha janela criada na inicializacao.

## Contrato e pitfalls

SDK 0.4.6 `GameAccess.Player` publica `id`, `name`, `age`, `position`, `strength`
e `value`. A comparacao mostra nome, idade em anos, posicao como **codigo SDK**,
forca e `value` crus, alem do nome do clube observado. ID e omitido: nao e uma
identidade persistente. Nao assume nomes de posicao a partir de codigo desconhecido.
API nao documenta unidade de `value`: nao rotulamos como R$, salario ou valor de
mercado. **Salario, potencial, forma, estatisticas e atributos privados nao estao
disponiveis**; nao sao inventados ou calculados.

**Dados comparados nao sao ranking oficial nem evidencia de desempenho/statistica.**
Forca nao prova desempenho futuro. Nao inferimos regras de gameplay. Paginas nao
sao snapshot atomico: jogadores selecionados em momentos diferentes podem refletir
instantes diferentes, e o escopo nao e uma revisao de todas as mutacoes de gameplay.
Nao ha observacao automatica de mudancas; selecoes podem ficar desatualizadas ate a
proxima consulta. Atualize manualmente para nova leitura. Consultas limitadas na
EDT podem pausar brevemente a UI; teste manual com carreira real continua necessario.
Encerramento abrupto/System.exit pode nao chamar shutdown.

## Cobertura dos testes

`ComparisonModelTest` usa GameAccess/ModContext falsos, independentes da janela:
paginacao 0/100/200, filtro e resultado vazio, mesmo/dois clubes, IDs duplicados,
selecao mantida entre paginas, campos/unidades, todos os motivos de erro,
escopo divergente de clubes/jogadores, nenhuma carreira e offsets invalidos.
Fake falha se snapshot, consultas sem escopo, lookup por ID/index ou persistencia
forem usados. Testa registro scoped, shutdown idempotente e callback tardio inerte.
Nao prova UI visual, integracao com carreira real, latencia ou seguranca do loader.

Integracao adicional `test.ps1 -IncludeUi` do loader usa o JAR real com carreira
fake: janela/tabelas/selecoes, stale/refresh e shutdown na Swing EDT. Nao substitui
confirmacao humana no Brasfoot. Pacote SDK-only testa este projeto fora do checkout.

Referencias: `GameAccess`, `ModContext`, `GameQueryException` no JAR publico; guias
`SCOPED-QUERIES.md` e `SDK-CONTRACT.md` distribuidos no SDK. Para mudancas futuras,
verifique o contrato publico antes de adicionar campos/unidades.
