# Contrato de eventos de I/O de carreira — SDK0.8.0 experimental

ModContext.careerIoEvents(): permission events.career.read e capability requerida
events.career.io. Exige --probe-career-hooks/Java17 ou21 no perfil22-23 conhecido.
API nova separada: assinantes de CareerObservations nao recebem novos enum kinds.

## Semantica delimitada

- CURRENT_STATE: replay do estado atual, nao evento novo de load/save.
- INVALIDATED: tentativa/reset/troca de raizes, nao CareerUnloaded.
- EXISTING_LOAD_COMPLETED: decode/retorno exclusivo aceitos na geracao atual e
  ramo de inicializacao best.f.ap observado. Fallback recursivo conta uma vez.
  Frame existente que pula esse ramo nao se qualifica. Nao inclui criacao nem
  promessa de que trabalhos futuros da fila EDT terminaram.
- SAVE_SEQUENCE_COMPLETED: apenas depois de EXISTING_LOAD_COMPLETED nessa geracao,
  .info fechado, wrapper e stream do .s22 fechados sem falha observada, backup
  copiado, retorno normal e ticket exclusivo aceito. Falha parcial, IOException
  engolida, save sobreposto/atrasado/desassociado nao produz esse evento.

State imutavel: session/generation/revision/phase, existingLoads/completedSaves,
healthy. Contadores agregados da sessao; generation/session nao sao IDs persistentes
nem CareerScope. ACTIVE significa leitura qualificada observada, nao carreira nova
pronta ou lock do grafo. DTOs nao expõem caminhos/objetos/streams/Throwable.

## Entrega e encerramento

Fila64 por assinatura,32 workers compartilhados com observacoes antigas e4
assinaturas totais por mod entre ambos servicos. Worker separado do jogo/EDT,
FIFO/revisoes crescentes, sem retry. Overflow/callback falho encerra so assinatura,
status via CareerObservations.Subscription; fechar/reassinar explicitamente para
replay. Worker que ignora interrupcao continua consumindo seu slot ate sair.
Updates sao historicos: compare session/generation/state antes de nova consulta;
isso nao torna leitura ou gameplay atomicos. Close pode ter callback ja selecionado.
Loader revoga contexto/assinaturas na falha e shutdown; CLOSED e estado de descarte,
sem evento final garantido ou unload inventado.
Cada hub tem grace500ms total; descarte da sessao fecha ambos sequencialmente
(ate1000ms juntos), alem do dispatcher diagnostico500ms. Nenhuma consulta EDT
no shutdown e nenhuma garantia de encerrar callback que ignora interrupcao.

Sem fsync/durabilidade, transacao entre arquivos, identidade persistente,
sidecars/P3, gameplay, MatchFinished ou lifecycle completo. Nao tratar esse stream
como journal. Hooks continuam best-effort sob falha catastrofica de JVM.

## Uso SDK-only

```java
var events = context.careerIoEvents();
var subscription = events.subscribe(update -> {
    if (update.kind() == br.vbrasfoot.api.CareerIoEvents.Kind.SAVE_SEQUENCE_COMPLETED) {
        context.logger().info("Save observado: " + update.state().completedSaves());
    }
});
// Fechar subscription no shutdown; sem aguardar a EDT no callback.
```

Manifesto: minLoaderVersion=0.8.0, permissions=events.career.read,
requiredCapabilities=events.career.io. Modo normal recusa capability antes de
inicializar mod; service e contexto retidos nao funcionam depois de dispose.
