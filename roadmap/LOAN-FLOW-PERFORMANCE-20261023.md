# Fluxo de novos emprestimos: eliminar trabalho repetido — 23/10/2026

## Alteracoes

- Revisao dos aliases recebe observacao/verificacao da revisao principal, somente
  dentro da mesma chamada. Metadados/HEADs de cada membro continuam inspecionados
  e revalidados integralmente. Ao finalizar o inventario historico, save e origem
  carregada sao novamente verificados. Nenhum cache global ou entre pedidos.
- No grupo Flamengo com3aliases, teste instrumentado conta1observacao adicional
  do save na agregacao em vez das9anteriores (3poralias). Cada observacao continua
  calculando o hash duas vezes. Verificacoes da revisao principal e os pontos de
  confirmacao/execucao continuam independentes; nao se afirma1leitura no fluxo total.
- Resolucao automatica nao repete o inventario global imediatamente depois de obter
  um grupo existente validado;usa sua principal. Caminho ainda desconhecido continua
  exigindo inventario para impedir criar historico vazio como bypass.
- LoanHistoryBatch confere todos os ativos e aposentados em um unico percurso por
  elencos profissionais/juniores e trackers. Mantem cotas e exige club/id/nome,
  unicidade,referencia de clube,prazo/origem de tracker e ausencia para aposentados.
  IDs duplicados para alvos recusam,mesmo com nomes distintos. Nao amostra jogadores.
  Nao reutiliza indice de grafo depois que retorna da EDT. Os checks continuam
  antes da cotacao e novamente antes da execucao/preparacao e no dispatch real.
- Botao mostra Avaliando.../Executando... enquanto ocupado. Logs `native-loan timing`
  registram elegibilidade,associacao,cotacao e execucao em ms. Tempo do usuario na
  confirmacao nao entra na medida da execucao. Nao ha efeito em paralelo na UI.

## Evidencia e limites

15checks sinteticos de varredura:399leituras de id para399jogadores ao validar2alvos,
contra798leituras no caminho separado. Testes de tracker ausente/duplicado/origem/
prazo divergentes,retorno do tracker antigo,juniores,id/nome duplicados e jogador
ausente preservam recusa. Inventario agrupado instrumentado confirma1observacao
adicional do save,retendo4fontes/tombstones e comportamento da referencia atual.

Probe na copia original do Flamengo (save52837913...,sem jogos/calendario/savewrite)
e metadados do backup pre-baseline:validacoes antigas dos2aposentados86ms;
validacao em lote61ms. Uma amostra,ordem antiga->nova,com aquecimento/cache de JVM;
nao demonstra25ms de ganho permanente nem mede fluxo de transferencia completo.
Historicos pessoais atuais ja possuem novos dados e NAO foram restaurados para o
backup. Save atual detectado com SHAa3babfa03ef7e4ad47f234f12b2e90c16a6f8a7f87f09afd5e0b27943bb557b7.

Ainda ha I/O de seguranca e varredura limitada na EDT,portanto nao se promete UI
sempre responsiva ou latencia maxima. Logs por etapa permitem localizar gargalos
restantes no uso real. Nenhuma protecao de UNKNOWN/EXECUTING/replay/fontes foi removida.

Regressao completa e release passaram. Pacote
`dist/portable-0.9.4-loan-flow-perf-20261023` instalado na copia autorizada existente,
com jogo fechado. Backup atualizado preserva o save MAIS NOVO do usuario:
`Documents/Brasfoot22-23-loan-perf-backup-20261023-084010`.
76arquivosprotegidos intactos,sem restaurar versao anterior;JAR instalado confere.
Launcher instalado probe/pixel-perfect/validate-only passou,sem iniciar o jogo.
Nenhum novo emprestimo foi executado pelo agente na instalacao pessoal. Ganho total
do fluxo ainda depende dos novos logs no uso real;nao foi medido end-to-end aqui.
