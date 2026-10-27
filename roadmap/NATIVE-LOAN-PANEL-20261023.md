# Painel de emprestimos e investigacao do bloqueio — 23/10/2026

## Interface

`NativeLoanDialog` substitui JOptionPane com textarea gigante por painel modal
compacto (466x365), sem barra de janela, centralizado sobre a tela selecionada.
Moldura azul, fundo verde, titulo amarelo, jogador/textos brancos e botoes claros
seguem o modelo visual fornecido. E um componente proprio, nao reutiliza nem
executa o formulario nativo de compra. Nao tem campo de proposta: taxa automatica.

Confirmacao: Cancelar/Confirmar. Respostas: Fechar. Escape e fechamento cancelam;
nenhum botao padrao autoriza com Enter acidentalmente. Texto longo pode rolar
verticalmente e o tamanho e limitado pela tela. Mensagens permanecem texto puro,
sem interpretar nomes de jogador como HTML. Nenhuma regra de concessao foi relaxada.

## Bloqueio investigado

Os logs da instalacao, sessoes `99be854d-31c5-4cee-9013-5694c3298d41` e
`1a26f394-78ec-4aaa-9a1c-92768d11a96c`, registram HistoricalLoanFailure.

Nova copia descartavel:
`Temp/opencode/brasfoot-loan-fixture/unconfirmed-639dd277db8140729bf756c23b9746af`.
Hash do save copiado e original antes/depois da copia:
`52837913efd1f59661f0d45a177033fcb497d3c8e64753b6908fe49a9872eeed`.
Leitura pelo decoder existente em JVM isolada, sem salvar, transferir ou avancar:

- clock1643295202790; trackers nativos de emprestimo:0;
- Macarrao, id136609: contrato APPLIED, origem Fluminense do Itaum, destino Flamengo,
  taxa0; no save, localizado uma vez no elenco profissional do Fluminense do Itaum,
  chave5802;
- Matheus Reis, id2498: contrato APPLIED, origem Fluminense, destino Flamengo,
  taxa0; no save, localizado uma vez no elenco profissional do Fluminense, chave81.

Isso comprova incompatibilidade entre o estado APPLIED dos metadados e o save
atual. NAO comprova o evento que causou a divergencia (nao salvo, descartado,
restauracao ou outro). Nao comprova retorno regular nem ausencia de efeito em
outro save. Portanto nao apagar/reancorar historicos, nao repetir transferencias
e nao transformar APPLIED em NO_EFFECT automaticamente. Consolidacao de identidade
permanece valida; recuperacao desses efeitos permanece pendente.

Nova mensagem identifica TODOS os jogadores historicos nao confirmados, em vez de
parar no primeiro e retornar aviso generico. A verificacao de jogador/tracker foi
mantida. Sem novos pedidos/cobrancas executados durante investigacao.

## Validacao e instalacao

Teste de painel cobre aparencia compacta, botoes, cancelamento, ausencia de
autorizacao ao construir/renderizar e nomes no diagnostico. Preview renderizado
em PNG, separado do jogo. Smoke de modal real usa somente widgets de teste.
Nao se apresenta esse teste como exercicio do runtime nativo do jogo.

Duas JVMs do jogo/loader estavam abertas na verificacao inicial. Nenhuma foi
encerrada e nenhum pacote deve ser instalado sobre uma sessao em andamento.

Na verificacao posterior as sessoes ja estavam fechadas. Pacote final
`dist/portable-0.9.4-native-loan-panel-final-20261023` gerado com regressao completa
aprovada e instalado em `Documents/Brasfoot22-23`. Backup antes da atualizacao:
`Documents/Brasfoot22-23-loan-panel-backup-20261023-071247`.
Todos os69arquivosprotegidos (save,EXE e dados historicos) permaneceram identicos.
JAR instalado coincide com o pacote; `--validate-only` instalado passou, sem iniciar
o jogo. Smoke modal proprio:12checks incluindo Confirmar,Cancelar e dispose.
Nao foi executado novo emprestimo na instalacao do usuario.
