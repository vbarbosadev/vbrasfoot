# Referencia atual escolhida pelo usuario e escala Java — 23/10/2026

## Decisao explicita

Usuario pediu resolver bloqueio de Macarrao/Matheus Reis e escolheu **Manter save
atual**: continuar sem transferir jogadores/saldos, preservar registros antigos e
impedir repeticao. Nao escolheu recuperar um save contendo aqueles emprestimos.

LoanBaselineStore grava em root separado `loan-baselines`, somente no principal,
decisao USER_SELECTED_CURRENT_SAVE_NOT_PROOF_OF_NO_EFFECT, SHA/tamanho selecionados,
digests dos contratos e tokens das fontes historicas revisadas. Nao altera os
APPLIED, contratos, diarios, aliases ou suas ancoras. Nao declara que efeitos nunca
ocorreram. Nao muda saldo, elenco, prazo, calendario nem save.

Somente os contratos exatos selecionados, com taxa0, podem sair da lista de ativos
esperados. Seus tombstones permanecem na lista completa: nao podem ser solicitados
novamente por chave nova. Outros APPLIED e UNKNOWN/EXECUTING continuam bloqueando
normalmente. Fontes antigas ausentes/alteradas, decisao corrompida, contrato divergente
e quota excedida recusam. O token da decisao e retido no ledger de novos pedidos;
remove-lo/alterar apos isso bloqueia a continuacao. Nada e reconciliado implicitamente.

Cada nova cotacao/revisao/execucao valida ainda os aposentados: jogador com id/nome
exatos, uma unica ocorrencia no elenco profissional da origem registrada, sem
tracker nativo daquele jogador. Caso seja restaurado um save com o emprestimo ou
haja outra incompatibilidade, recusa em vez de ignorar o estado. Checagem repetida
antes do efeito real. Fluxo generico/legado somente leitura com baseline presente.
Este tratamento conservador nao implementa retornos/transferencias futuras desses
dois jogadores: estados distintos exigem nova revisao, nao aprovacao silenciosa.

## Escala

Probe Java de display (sem jogo): tela fisica1920x1200, bounds Java1536x960,
transform1.25x1.25. Mesma JVM com `-Dsun.java2d.uiScale=1`: bounds1920x1200,
transform1x1. Ha ampliacao fracionaria125% do conteudo antigo; isso pode borrar
imagens raster e texto. Nao e prova de baixa resolucao dos arquivos de imagem.

Launcher aceita `--pixel-perfect`, removido antes do parser Main, e aplica somente
a propriedade de escala Java. Novo atalho opcional `vbrasfoot-nitido.cmd` inclui
emprestimos e escala100%. Launcher normal preserva comportamento anterior. Nenhum
registro, resolucao, driver ou configuracao global de DPI do Windows e modificado.
O conteudo pode ficar menor; nao inventa detalhe nos assets antigos. Se persistir
baixa qualidade: conferir resolucao Recomendada do monitor e compatibilidade DPI
de java.exe/javaw.exe (evitar ampliacao Sistema/Sistema aprimorado), sem alterar
automaticamente esses ajustes. Em multiplos monitores, comparar no mesmo monitor.

## Evidencia de partida

Save selecionado: Flamengo.s22 SHA256
52837913efd1f59661f0d45a177033fcb497d3c8e64753b6908fe49a9872eeed,
clock1643295202790,trackers0. Macarrao136609 no Fluminense do Itaum5802;
MatheusReis2498 no Fluminense81. Ambos contratos antigos APPLIED para Flamengo,
taxa0,com recibos APPLIED correspondentes. Dados de partida checados em copia
descartavel pelo decoder existente,sem regravar save.

Backup antes de qualquer gravacao de baseline/atualizacao:
Documents/Brasfoot22-23-current-baseline-backup-20261023-073953.

Testes incluem codec/escopo/fontes/decisao explicita, preservacao de todos os
tombstones na agregacao de4membros,retirada apenas dos2selecionados e fonte adicional
do token de baseline. Teste sintetico nao comprova execucao no runtime original.

Na copia descartavel, o adapter real confirmou ambos jogadores unicos na origem
e sem trackers antes de permitir gravacao da referencia de teste. Somente depois
da escolha do usuario,backup e fechamento do jogo, mesma verificacao contra o save
copiado e os metadados originais permitiu registrar a referencia no principal real:
token9e46b892a9bbf1a6bb241a3f25e19e836a7de7c0185a90d1163d93f389b6bcc4.
69arquivosanteriores protegidos devem permanecer identicos ao backup.

Tentativas de abrir o runtime visual na nova fixture nao mostraram janela do jogo,
tanto na escala automatica quanto100%. Sessoes de teste proprias foram encerradas,
sem tocar sessoes pessoais ou executar pedido. Nao se anuncia teste novo de
transferencia/save/reabertura aprovado nessa fixture. Metadados e guards reais
foram testados; validacao da nova solicitacao completa na UI continua distinta.

Teste PowerShell automatizado cobre argumento de escala apenas no launcher opt-in,
retirada da flag antes do parser Main, preservacao do probe e validate-only,
recuperacao preview de geracoes antigas7arquivos e novas8arquivos com atalho,
e rejeicao de indices duplicados/fora de faixa. Uninstaller reconhece novo atalho.

Pacote `dist/portable-0.9.4-current-baseline-pixel-20261023` gerado com regressao
completa e testes PowerShell aprovados. Instalado na copia autorizada existente
Documents/Brasfoot22-23,com jogo fechado.69arquivosprotegidos preexistentes
(save,EXE,dados/historicos) identicos ao backup;apenas nova referencia em root
separado. JAR instalado confere. Launcher instalado com --pixel-perfect
--probe-career-hooks --validate-only passou sem iniciar jogo. Nova leitura em JVM
separada validou baselinepersistido,2contratos exatos,fontes e guards de origem/
ausencia de tracker. Nenhum novo emprestimo real foi executado no save pessoal.
