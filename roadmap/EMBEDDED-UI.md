# SDK0.7.1 — painel dentro da janela do jogo (experimental)

Declare minLoaderVersion=0.6.0, permissions=ui.register e
requiredCapabilities=ui.panel.embedded. Para consultar dados, game.read e capacidades
de query apropriadas tambem. APImajor1 preservada por defaultUnsupported antigo.

```java
registration = context.registerPanel("meu-painel", "Meu painel", theme -> new ModPanel() {
    private final javax.swing.JPanel view = construirComponentesProprios();
    public javax.swing.JComponent component() { return view; }
    public void close() { /* descartar modelos/listeners/timers sem bloquear */ }
});
```

Factory/component/close rodam na EDT; so construir controles do mod, nunca retornar
componente do jogo, de outra janela ou com parent. Factory nao recebe janela/game
objects/classloader. Contexto pode fornecer DTOs publicos se autorizado.

## Integracao delimitada

Acao no menu vbrasfoot abre overlay opaco usando glassPane da janela da carreira,
somente quando tela de elenco a.eg e reconhecida pelo perfil. Nao substitui nem
reparenta contentPane/componentes originais, nao muda bounds e nao grava preferences.
Voltar ao jogo/Escape fecha, restaura glassPane anterior e descarta ModPanel.
Um painel por vez: abrir outro descarta anterior; cleanup de owner antigo nao
remove outro. Registro encerrado/desligamento remove painel por owner unico;
recriacao da tela, troca de glassPane ou janela fechada causam descarte na proxima
verificacao de menu800ms. Componentes do mod nao sao hot reload persistente.

Camada original visivel impede abrir painel, sem chamar factory. Mod e codigo
confiavel: isto nao isola codigo hostil/sandbox. Falha factory/action aparece nas
fronteiras de falhas do loader; falha close e logada e nao impede restauracao.
Nao bloqueie EDT no close; remocao de registration pode ser agendada sem espera.

Overlay intercepta area visual/mouse mas nao e modal nem pausa simulacao/timers:
atalhos globais/componentes do original e alteracoes de estado ainda podem existir.
Nao prometer exclusao mutua de gameplay, transacao ou congelamento do grafo. Testar
atalhos/resolucoes/multimods antes de anunciar comportamento amplo. Se botao do
mod ficar inacessivel, Escape/fechamento normal sao saidas (atalhos nativos Windows
continuam disponiveis); nao remover controles originais para ganhar espaco.

## Tema sem recursos proprietarios

UiTheme copia Font/Color de Opcoes e painel local, nao compartilha componentes,
Icon/Border/modelo/eventos originais. apply usa paleta em componentes mod-owned
na EDT; cabecalho/botao de voltar seguem mesma paleta. Nao e recriacao pixel-a-pixel
de todo widget original ou garantia de todos renderers Swing/escala/acessibilidade.
Mods podem configurar layout/renderers proprios respeitando dados da paleta.
SDK/ZIP nao inclui nenhuma imagem/fonte/recurso binario do jogo. Original local
e necessario so para jogar, nao compilar o exemplo. Tema e local a cada abertura.

### Composicao visual por papeis (0.7.0)

Atualizacao0.7.1: o usuario pediu composicao semelhante ao painel nativo mostrado
na captura, nao a paleta preto/verde inicial. O host agora centraliza um card em
glass pane transparente, limitado a1200x740 e a area disponivel menos32px.
O jogo permanece visivel ao redor; sem janela externa ou mudanca de bounds.
Corpo azul-petroleo, secoes azul escuro, campos/tabelas claros, botoes claros e
Fechar no rodape direito. Escape e cleanup seguem o contrato anterior.
Em1280x720 o card se ajusta a altura disponivel; nao promete margens amplas em
telas pequenas, bloqueio modal completo, pausa ou fidelidade pixel-perfect.
Comparador1.1.1 exige loader0.7.1. Paleta anterior abaixo e registro da entrega0.7.0.

UiTheme.style(component, Role) distingue BODY/HEADER/TITLE/SECTION, botoes primario/
secundario, INPUT/TABLE/STATUS. Composicao original sem assets: preto/carvao no
cabecalho e rodape, branco no corpo, verde nas secoes/acoes e amarelo na selecao
de tabela/status. Fonte deriva da fonte local, com hierarquia de tamanhos/peso.
Paleta inspirada no jogo, nao amostragem pixel-perfect de todos controles originais.

apply nao sobrescreve cores/fontes explicitas nao-UIResource e reaplica papeis
marcados, preservando renderers/cabecalhos de tabela/selection colors. Use style em
componentes proprios antes de montar painel; aplicar ao mesmo painel nao apaga
estilos de titulo/secao/botao. Nao chama updateComponentTreeUI ou altera UIManager
global, preferencias do jogo, bordas originais ou dados. Mod pode continuar usando
estilos proprios; quem depende dessa preservacao deve exigir loader0.7.0.

Comparador1.1.0 organiza filtro/clube/navegacao em linhas separadas por lado para
evitar FlowLayout esconder filtros em telas menores. Resultado readonly separado
e rodape de status. Preview/smoke sintetico inclui1920x1080 e1280x720; UI real,
escala150% e demais layouts precisam confirmacao antes de anunciar suporte amplo.

Referencia autorizada para consulta foi realbrasfoot-mods/PlayerSwapPanel.java:
apenas principios visuais de header/corpo/secoes/rodape, sem copiar codigo, recursos,
APIs/regras de transferencia ou nomes ofuscados de outro perfil. Licenca explicita
nao encontrada nessa arvore; nenhuma incorporacao de fonte dela neste projeto.

## Exemplo e evidencias

Comparador oferece duas acoes: janela propria anterior e Comparador dentro do jogo.
Mesmo modelo/paginas100/DTOs e nenhum salario/ranking inventado. Painel integrado
nao cria JDialog. Factory nova a cada abertura; voltar limpa selecoes.

Smoke Swing com janela/carreira fake + JAR real verifica comparar, voltar, original
contentPane/bounds/glassPane preservados, dois owners, close idempotente, camada
original visivel recusada e screen change descartado. Checks puros palette/EDT e
SDK-only/build API-only tambem. Nao prova UI original/inputs/hardware/causa do corte.
Conferencia humana pendente depois da instalacao revisada na copia.

Sem injecao generica em quaisquer telas, menus originais extras ou uso de classes
ofuscadas pelo mod. P3/lifecycle/eventos/partidas/gameplay continuam gates separados.
