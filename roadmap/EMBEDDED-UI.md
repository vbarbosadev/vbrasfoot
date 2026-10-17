# SDK0.6.0 — painel dentro da janela do jogo (experimental)

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
