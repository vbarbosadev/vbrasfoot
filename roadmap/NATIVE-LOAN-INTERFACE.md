# Emprestimo na tela original de clubes

ATUALIZACAO: fluxo simplificado e politica historica do clube em
[FLAMENGO-CLUB-HISTORY-VALIDATION.md](FLAMENGO-CLUB-HISTORY-VALIDATION.md).
O seletor de associacao e a confirmacao de revisao descritos abaixo sao do fluxo
anterior: agora a associacao unica do caminho verificado e resolvida automaticamente,
sem UUID/selecao; ambiguidades e historicos inseguros recusam. Validacao da revisao
continua obrigatoria no coordenador, mas nao abre confirmacao tecnica separada.

## Integracao de interface

Integracao interna opt-in do loader, somente no perfil fingerprintado22-23 e
com `--probe-career-hooks`. Nao altera o EXE nem publica escrita no SDK.

Na tela original `a.iA`, o botao nativo de emprestimo usa uma unica callback do
mod, substituindo (nao acumulando) a callback nativa. Campo `yK` fornece a selecao,
`ul` o destino do contexto nativo e o bridge emite handles efemeros por identidade.
Nenhum lookup por nome, ID digitado ou lista de candidatos concede autoridade.

Ao clicar, a consulta da regra/taxa ocorre sem efeito. Se recusado, exibe motivo.
Se permitido, resolve a associacao explicita do save carregado, gera a chave
internamente e solicita confirmacao com jogador, origem, destino, taxa e prazo.
Selecao alterada/janela fechada invalida a execucao antes do primeiro efeito.
Cliques concorrentes sao recusados; fechar uma janela nao presume rollback.

## Identidade e revisoes

A primeira selecao da associacao existente e explicita, sem copiar UUIDs. Quando
nao ha associacao para aquele caminho, a criacao de metadados exige confirmacao.
Selecao lembrada em `data/native-loan-association.properties` para o caminho
EXATO do arquivo carregado; bytes iguais ou nomes iguais nao associam outra copia.
Formato desconhecido/corrompido e preservado e recusado, nao sobrescrito.

Save alterado exige reconhecer a nova base; isso nao comprova inclusao de efeitos
ou taxas antigas. A verificacao de carga original nao e fabricada a partir de um
evento de save: depois de salvar, feche/reabra e carregue o arquivo antes do novo
pedido. Nunca avancar calendario ou iniciar partida automaticamente para isso.

## Limites da continuacao

Continuacao de pedidos NOVOS deve conservar contratos, chaves e resultados antigos.
Historico continua sem prova generica de inclusao no save. Emprestimo ativo pode
ser verificado por jogador exclusivo no destino, tracker unico, origem e prazo;
isso e consistencia observada, NAO prova causal da taxa. Tracker ausente/ambiguo,
UNKNOWN, operacao parcial e outros conflitos nao podem ser ignorados.

Nova solicitacao do mesmo jogador historico nao pode contornar a protecao trocando
a chave. Novo emprestimo apos retorno/expiracao exige qualificacao adicional do
ciclo de retorno; nao inferir ausencia de efeito pelo desaparecimento do tracker.

## Evidencias delimitadas

- Teste sintetico de callback: somente um handler; nativo nao roda junto do mod;
  busy desabilita; callback atrasada fica inerte; encerramento restaura o original.
- Preferencia: caminho exato apenas; outro arquivo nao herda associacao; formato
  desconhecido nao e sobrescrito.
- Inspecao offline da fixture saved `relative-fee-final-6e8afd5ddefe443ebda9ad2abac6219a`:
  emprestimo ativo encontrado apos save real; tracker ausente/duplicado e alias de
  elenco recusados em grafo descartavel. Arquivo-semente permaneceu intacto.
  Esse teste nao inicializa prontidao nem comprova inclusao financeira.

## Fluxo real observado — 22/10/2026

Fixture `native-context-bff21a17795d4d9f9d20eb3a27b97fc1`, home proprio,
Main/ProbeSession reais. UI controlada por componentes Swing da propria JVM.
Sem autoridades artificiais no fluxo de cotacao/executar.

1. Carregar semente LOAN_FIXTURE pelo menu original.
2. Abrir Times, selecionar AEK Atenas na arvore nativa e Marios na tabela.
3. Botao Emprestimo(mod) mostrou confirmacao de primeira associacao sem UUIDs,
   depois cotacao com taxa147916. Confirmar executou e refletiu34/35 imediatamente.
   Inputs de politica pagos foram artificiais na fixture: Marios24/colegas8,
   participacao0/8, disponibilidade=false. Nenhuma alteracao assim no jogo do usuario.
4. A mesma tela removeu Marios da lista e atualizou o saldo. Selecionado Thomas
   Strakosha, novo clique mostrou historico1ativo, taxa0; confirmar executou.
   Elenco35,trackers2,historico2; nenhuma segunda cobranca do primeiro emprestimo.
5. Salvar pelo menu original, sair e abrir NOVA JVM:2jogadores e saldos preservados.
   Novo clique com elenco35 recusou por destino cheio sem gravar pedido.
6. Encerrou pelo menu; save de2emprestimos arquivado como two-loans-before-vacancy.s22.
   Criada UMA vaga artificial no save descartavel removendo Mycael nao emprestado,
   sem tocar trackers/historicos ou metadados do mod. Nao simula venda/retorno.
7. Abrir NOVA JVM/carga original desse novo baseline:2trackers/34jogadores.
   Selecionar AEK e clicar Emprestimo pediu reconhecer revisao nova, reutilizou
   associacao lembrada, mostrou2historicos ativos consistentes e taxa0 para
   Alberto Brignoli. Confirmacao executou:35jogadores,3trackers/3historicos.
8. Salvar/sair pela UI original. Clock1642941578893 em todas as etapas.
   Saldos depois da unica taxa:AEK7503148 (antes7355232),Athletico11440016
   (antes11587932). Prazo dos3trackers1674563978893.

Ledger novo retém bytes e revisoes originais, fontes antigas e tombstones de
jogadores/chaves sem reancoragem. Durante claim/efeito/commit terminal, load/reset/
save originais ficam excluidos. UNKNOWN/EXECUTING e historicos conflitantes/alheios
continuam bloqueando. Painel antigo vira somente leitura apos ledger de continuacao.

Limites: testes de politica/fees/cancelamento de callback/replay/falhas de escrita
sao complementados por testes sinteticos; nao ha prova de queda de energia ou
atomicidade distribuida entre save e metadados. Nao e garantia generica de inclusao
de taxa em qualquer save. Repeticao do mesmo jogador historico fica bloqueada,
mesmo apos retorno, ate qualificacao do ciclo. Limite32pedidos no ledger, sem eviccao.
