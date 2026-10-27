# Validacao da regra relativa e taxa — 21/10/2026

Teste pelo painel real do loader em fixture descartavel
`relative-fee-final-6e8afd5ddefe443ebda9ad2abac6219a` sob Temp/opencode/brasfoot-loan-fixture.
Autorizacao real de Main/ProbeSession, associacao criada pela UI. Nao usada
autoridade artificial do harness. Inputs de politica foram ARTIFICIAIS somente
nessa fixture: Marios24, colegas8, participacoes0/8 na temporada/clube, indicador
de disponibilidade=false. Nao foi avancado calendario nem iniciada partida.

- Consultar/registrar: APPROVED, valor1479168, taxa147916 (10% arredondado abaixo).
- Confirmacao do painel mostrou taxa antes da transferencia.
- Resultado EXECUTED/APPLIED; elenco humano33->34 com contador34/35 imediato.
- AEK saldo7355232->7503148; Athletico11587932->11440016.
- Tracker1/historico1, origemAEK/destinoAthletico, vencimento1674563978893.
- Salvar pelo menu original, sair, abrir nova JVM e carregar: jogador, saldos,
  tracker/historico e prazo preservados. Clock1642941578893 em todas as etapas.
- Tentar mesma chave apos reopen bloqueou antes da confirmacao; nenhuma nova
  transferencia/cobranca. Chave loan_f413577881a64198b74375b88defa06e.

Primeiro teste isolado detectou previsao de ledger incorreta para credito em clube
IA: saldo correto, mas demonstrativo nao muda como em clube humano. Tentativa
ficou UNKNOWN; fixture antiga preservada, nao reexecutada. Previsao foi corrigida
para a rotina nativa fingerprintada (credito IA altera apenas saldo). Reteste acima
foi em fixture NOVA. Refresh tambem roda apos efeito parcial, sem implicar rollback.

Cobertura limitada: paid branch pelo painel com inputs artificiais, nao temporada
simulada; free branch/politica/codec misto/recovery/arredondamento/limites e falhas
apos emprestimo/debito/credito em testes sinteticos separados. Nao e teste de
queda de energia ou garantia de atomicidade entre jogo e metadados. APPLIED continua
sem prova generica de inclusao no save; a reabertura concreta acima foi observada.
Uso continuado apos efeito/revisao antiga continua conservadoramente bloqueado.
