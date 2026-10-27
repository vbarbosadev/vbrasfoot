# Central de Mensagens e recebimento adiado — loader 0.14.0 / mod 0.6.0

## Escopo entregue

- Imagem opcional, botão Sem logo e codec v2 com texto de até 2000 caracteres.
  Leitura do codec v1 continua suportada, sem alterar contratos antigos até gravação explícita.
- Publicação durável de oferta sem crédito e sem naming rights. Logo opcional continua visual.
- Projeção das ofertas na Central de Mensagens ORIGINAL: assunto à esquerda, texto simples
  e botão Receber à direita. Marcadores são substituídos uma única vez; HTML é literal.
- Recebimento usa quote/activate existentes, os mesmos journals/witnesses/ledger e o setter
  original. Dinheiro e estádio só mudam depois da confirmação de recebimento.
- Cancelamento pendente não efetua nenhuma chamada financeira. Um cancelamento invalida
  inclusive uma cotação já emitida. Após claim financeiro, cancelamento pendente é recusado.
- CLAIMED/UNKNOWN/NO_EFFECT não viram ofertas disponíveis de novo. Tombstones antigos têm
  precedência sobre metadados de mensagem; não há migração de contratos pagos para a receber.
- Permanecem empréstimos, ajuste de DPI/janela, vínculo compartilhado e proteções anteriores.

## Evidência estática e limites

REA MCP/CLI não estavam disponíveis nesta sessão. A inspeção abaixo foi feita com `javap`
e leitura local de entradas ZIP, não constitui Evidence REA.

Artefato suportado: `game.jar` extraído do EXE SHA-256
`2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.

`a.bn(JDialog, ArrayList, best.ah)` cria nova ArrayList privada (`oh`), copia/reverte as
mensagens recebidas e usa `b.u` como modelo. `a.br` faz cast explícito do modelo para b.u.
`components.as` possui construtor vazio sem anexar mensagem ao coach; outros construtores
podem anexar ao save. Por isso só o vazio é usado como identidade transitória de linha.

Fingerprints exigidos em runtime:

- a/bn.class: b318e93c33d1b96142a47a1786dadeac24ad5966b55a8587e6a47955c313341f
- a/br.class: 5ffd746dceb9cf83993c0c2ae56d9bd6a63fe80b353a180abed17a5d6813a65e
- b/u.class: 8b7ad05ec334327878ab17322bb6dd926b430791421bb21ff3df090a044a11a5
- components/as.class: ae14042f7dd3053cec2bc7f7f0741ffe96f4cee1a327679da279f7373e215597

O modelo b.u é preservado. O callback nativo é delegado apenas para linhas originais;
o detalhe vd é envolvido com GroupLayout.replace/CardLayout para preservar seu layout.
Ao fechar, renderer, callback, detalhe e lista privada são restaurados. Nenhuma oferta
é inserida na coleção serializada do coach/jogo. O contador original NÃO inclui ofertas.

## Persistência e segurança

`sponsor-offers` é um root dedicado, isolado por carreira/binding, com termos imutáveis
e cancelamento retido. Ele não é prova financeira. `SponsorActivationStore` permanece a
autoridade anti-replay e tem prioridade na projeção dos estados.

Antes do efeito, a oferta capturada na cotação é comparada à oferta atual, além das
verificações anteriores de scope, geração, inventários, ledger e contexto operacional.
A listagem lê um snapshot de ledger para todos os itens, sem re-hashear o save por linha.
Atualização/recebimento rodam em workers; o Swing fica no EDT. Busy e uncertain não
reabilitam pagamento ao alternar linhas. Exceções não disparam repetição automática.

Levar somente o save, apagar metadados, restaurar sidecars antigos ou fechar sem salvar
continua sem recuperação universal. Nenhuma promessa de exatamente-uma-vez entre esses casos.

## Validação

- SponsorOffersCoordinatorSelfTest: 17 checks (pending, cancel, outros bindings,
  memória financeira aplicada/incerta sobrepõe pending, nenhuma escrita no save).
- SponsorMessagePanelBindingSelfTest: 13 checks de seleção nativa/custom, dupla ação,
  refresh sem duplicação, bloqueios e restauração de callbacks/layout.
- SponsorDeferredOriginalRuntimeTest: 24 checks na cópia marcada: scanner real do loader
  descobre a central original, worker lista ofertas, seleção nativa continua funcionando,
  pending sobrevive a save/reload original sem crédito, clique no painel chama quote/activate
  do coordenador, crédito exato, naming adiado, botão pago desabilitado, save/reload pago,
  cancelamento invalida quote anterior e ledger reaberto rejeita replay.
  Marcadores de decoder e confirmação humana são ARTIFICIAIS; não é aceite manual completo.
- Mod: codec/modelo 28, layout 14, identificação de clube 9, notice legado 5,
  logo opcional/texto/codec v1 4.
- Regressões executadas: ativação 101, encerramento 43, continuação sponsor 5,
  identidade 23, coordenador 70, lifecycle 97, perfil combinado 10, portabilidade 33,
  UI empréstimo 28, continuação empréstimo 16. Outros testes de perfis/janela/loans
  também passaram na compilação intermediária. Build completo não foi repetido.

Build: `C:\Users\vinicius\AppData\Local\Temp\opencode\sponsor-messages-b41f31e4903e4043b5fd9048028c3a06`.
Fixture: `C:\Users\vinicius\AppData\Local\Temp\opencode\sponsor-messages-runtime-950f4766aa344a4ca84d4a609f0e3449`.
Screenshot original: `home-release/native-message-preview.png` dentro da fixture.

Entrega: ZIP combinado em Documents, extração junto do bf22-23.exe e launcher
vbrasfoot-completo.cmd. Não instalar automaticamente nem alterar saves/metadata do usuário.
