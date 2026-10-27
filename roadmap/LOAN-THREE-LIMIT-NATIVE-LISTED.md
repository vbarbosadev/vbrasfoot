# Limite3 e jogador listado no jogo — 23/10/2026

## Regras solicitadas

Limite de3jogadores emprestados no destino, tanto caminho personalizado quanto
original. Contagem pelos trackers nativos e clube atual do jogador (mesmo criterio
do jogo). Limite>=3 recusa no preflight,prepare e imediatamente antes do efeito.
O limite nativo original4 continua dentro do binario,mas nao e usado como permissao
para aceitar um quarto pelo mod. Contratos ja existentes (incluindo4no Flamengo)
nao sao devolvidos,cancelados ou alterados;nenhum novo enquanto houver3oumais.

Quando `fz` indica jogador ja disponivel nativamente: elegibilidade original e
limites permanecem;sem mediana de origem/destino,historico de participacao ou
calculo de taxa do mod. Nova tag de contrato `native-listed-no-fee/1`,taxa0,
participacao desconhecida sem avaliacao. Snapshots financeiros apenas protegem
o estado antes/depois;nao sao usados para definir taxa nem custo.
Execucao usa exatamente uma chamada a `best.l.a(player,destination)` (rotina
original),nao `player.q` negociado pelo mod. Disponibilidade precisa continuar
verdadeira ao revalidar;se mudar,recusa sem converter pedido em outra politica.

Para jogador nao listado,a regra personalizada anterior continua. As confirmacoes
e ledgers seguros permanecem nos dois caminhos;nao se acumula callback de compra/
emprestimo nem se oferece escrita publica. Politicas/bytes de contratos antigos
nao sao regravados para a nova tag;guards atuais de limite3 valem para qualquer
nova execucao. Refresco de elenco executado tambem no caminho original.

## Testes

15checks sinteticos:contagens0..5,bloqueio>=3,forca alta/origem sem participacao
nao exige avaliacao custom quando listado,taxa0,codec/digest,elenco35 preservado,
politica antiga nao migrada e motivo curto do limite3.

Probe na copia descartavel `unconfirmed-639dd277db8140729bf756c23b9746af`:
leitura do seedFlamengo52837913...,carga de roots sem iniciar partida/UI.
Elegibilidade/captura/prepare de Munir nativo,taxa0,sem history. Em JVM separada,
dispatcher com gate ARTIFICIAL APENAS do harness e arquivos de teste novos fez3
chamadas reais a rotina original:Munir,Jonathan Fernandes,Joannis Steig (nome
com codificacao original). Trackers1,2,3;quarto listado recusou code3 sem chamada
nativa extra. Saldo Flamengo e clock inalterados;nenhum arquivo save regravado.
Uma primeira tentativa da busca ampla da fixture parou em clube de identidade
ambigua apos2efeitos em memoria;nao repetiu nessa JVM. Nova JVM limpa restringiu
a busca a clubes unicos e passou. Nenhum guard do produto foi relaxado para teste.
Isso comprova rotina/adapter/cap na copia,nao a sessao pessoal ou um novo save/reopen.

Regressao completa e release passaram. Pacote
portable-0.9.4-loan-cap3-native-listed-20261023 instalado na copia autorizada existente
Documents/Brasfoot22-23,com jogo fechado. Backup antes da atualizacao:
Documents/Brasfoot22-23-loan-cap3-backup-20261023-085852.
85arquivosprotegidos(save,EXE,dados/historicos)intactos;JARconfere;
validate-only instalado passou sem iniciar jogo. Nenhum emprestimo pessoal existente
foi alterado nem nova transferencia executada na instalacao pessoal pelo agente.
