# Novo patrocinador após encerrar — loader 0.13.1

## Correção

O bloqueio reportado não era no cadastro: a ativação passava pelo veto global de
efeitos de patrocínio, que deliberadamente incluía contratos encerrados.
Além desse veto, a revisão de empréstimos rejeitava os journals/requests/witnesses
financeiros do patrocinador antigo e as âncoras desses stores pertenciam ao save
da ativação anterior.

- `permitsNextSponsor()` aceita somente histórico sem efeito ou ativação aplicada
  com encerramento ENDED. CLAIMED/UNKNOWN/encerramento não concluído bloqueiam.
- Replay do mesmo UUID continua negado independentemente do encerramento.
- A revisão específica para novos patrocinadores conserva todos os históricos e
  retira da visão loan-only somente payloads exatos de sponsors concluídos. Recibos
  pendentes, witnesses incertos, payloads alheios, falhas de leitura e empréstimos no
  namespace do patrocinador continuam bloqueando. Baselines antigos não são reescritos.
- Stores financeiros e de encerramento de novos contratos ficam sob
  `sponsor-operations/<sponsor UUID>`, ainda isolados por carreira/binding/owner,
  evitando reancorar ou apagar journals anteriores ao mudar de revisão de save.
- O gate pode preparar contexto para novo sponsor contabilizando intents exatos
  dos créditos/encerramentos históricos e empréstimos qualificados. Nenhum recibo
  é removido e nenhuma incerteza é limpa.
- Caminhos genéricos/empréstimos permanecem conservadores. A correção não afirma
  inclusão de crédito antigo no save atual após restore nem executa compensação.

## Testes

- SponsorEndPipelineSelfTest: 43 (inclui novo UUID após END e veto em incerteza).
- SponsorContinuationReviewSelfTest: 5 contra revisão real do coordenador com
  proveniência sintética: ativo bloqueia; encerrado com finance legado passa;
  payload antigo intacto; caminho genérico permanece bloqueado; PREPARED alheio
  adicionado ao journal continua impedindo novo sponsor.
- CareerCommandGateSelfTest: 42, incluindo crédito + encerramento contabilizados
  sem apagar recibos e veto a UNKNOWN.
- Pipeline ativação 101; coordenador 70; histórico agrupado 11; continuação 16;
  lifecycle/permissões 97.
- SponsorOriginalRuntimeTest: 28 checks com métodos e save/reload originais numa
  cópia descartável: primeiro crédito -> encerramento -> segundo crédito distinto
  -> save/reload -> saldo exato e novo nome, primeiro histórico intacto.
  Autoridade artificial: não substitui aceite humano do fluxo completo de UI.
- ZIP extraído novamente e todos os arquivos comparados por SHA-256; mod validado
  pelo loader extraído. Sem build completo repetido nesta revisão.

## Entrega sem comandos

`C:\Users\vinicius\Documents\VBrasfoot-Patrocinios-0.5.0-Loader-0.13.1.zip`

Contém loader 0.13.1, mod 0.5.0, launchers e instruções; não contém jogo, Java,
saves ou dados pessoais. Jogos abertos foram detectados, nenhum processo encerrado
e nenhuma instalação/save alterado automaticamente. Extrair com jogo fechado,
preservando `sav` e `vbrasfoot/data`; não recriar REDBULL nem repetir DORITOS.

Build/testes: `C:\Users\vinicius\AppData\Local\Temp\opencode\sponsor-sequence-d372e41e2d2043d5b4c0c65cb59e1aad`.
Fixture original: `C:\Users\vinicius\AppData\Local\Temp\opencode\sponsor-sequence-runtime-3fd6ab2ce25f4464bf981b3e021ac3b1`.
