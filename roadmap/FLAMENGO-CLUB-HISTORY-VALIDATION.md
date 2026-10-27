# Save Flamengo: historico por clube e fluxo simples — 22/10/2026

ATUALIZACAO: a ambiguidade administrativa das quatro associacoes foi consolidada
em uma principal com tres membros historicos obrigatorios. Veja
[UNIQUE-SAVE-ASSOCIATION.md](UNIQUE-SAVE-ASSOCIATION.md). As constatacoes abaixo
sobre efeitos APPLIED sem prova no save continuam validas; nao foram apagadas.

## Origem e preservacao

Arquivo escolhido pelo usuario: `Documents/ Brasfoot22-23/sav/Flamengo.s22`.
Tamanho75857190bytes; SHA256
`52837913efd1f59661f0d45a177033fcb497d3c8e64753b6908fe49a9872eeed`.
Analise e testes somente na copia descartavel
`Temp/opencode/brasfoot-loan-fixture/flamengo-history-5a1587de3542401e88d9cf083c271666`.
EXE reconhecido pelo fingerprint habitual; nenhum calendario ou partida iniciado.

## O que o save realmente contem

Inspecao do grafo original por Kryo, em JVM propria, com source intacto:

- Temporada interna1; clock1643295202790.
-263242objetos distintos de jogadores nos elencos profissionais/juniores inspecionados.
-12883registros de participacao, TODOS da temporada1;250359jogadores sem registro.
-Nenhum jogador inspecionado com registros em multiplas temporadas ou clubes.
-Ganso: Fluminense, chave de clube81,1registro,temporada1,1jogo.
-Matheus Reis: Fluminense,nenhum registro. Flamengo profissional27jogadores.
-Regravacao em arquivo privado e nova leitura preservaram contagens por temporada.

Conclusao delimitada: o registro tem clube/temporada/participacao; o save permite
usar historico POR CLUBE quando presente, mas NAO comprova preservacao ao longo
de futuras temporadas. Nao havia dados de temporadas anteriores nem oportunidade
de validar mudanca real de clube historico. Nao avancamos o jogo para fabrica-los.
Tambem nao equivale a carreira real anterior ao inicio do save.

## Regra implementada para NOVOS contratos

`origin-club-career-weighted/1`, mantida dentro do codec de contrato v2 por tag
explicita. Politicas antigas /1 e /2 preservam bytes/avaliacao, sem migracao.

1. Forca<=mediana atual da origem: libera gratis, sem exigir jogos.
2. Acima: captura TODAS as temporadas registradas pelo jogador no clube atual,
   dentro dos limites; outros clubes e temporadas futuras sao excluidos. Repeticao
   do mesmo clube/ano e ambiguo, nao somado nem tratado como zero.
3. Compara jogos do candidato aos maximos conhecidos da mesma temporada/clube nos
   historicos dos colegas ATUAIS. Nao reconstrui elenco antigo completo; maximo e
   limite inferior observado, desconhecido nao e zero. Ha limite de128temporadas/
   16000celulas, sem truncar silenciosamente para aprovar.
4. Temporada atual suficiente: candidato conhecido e maximo>=5. Historico anterior
   suficiente: registros do candidato naquele clube e soma de maximos>=5.
   Com ambos, pondera70%razao atual/30%razao historica anterior (jogos/maximos
   acumulados). Com apenas um periodo suficiente, usa somente esse periodo.
   Na primeira temporada registrada no clube nao usa dados do clube anterior.
5. Razao ponderada<=25% permite taxa10% do valor arredondada para baixo. Comparacao
   racional exata, inclusive extremos numericos, sem floats/arredondar decisao.
6. Sem evidencia suficiente, recusa. Destino, limites, saldo e execucao continuam
   revalidados. Dados historicos/financas entram no digest antes da confirmacao.

## UI e identidade

Click nativo faz validacao e mostra recusa curta ou uma confirmacao de jogador,
clubes, taxa e366dias. Sem formularios UUID/chaves, selecao de associacao ou
confirmacao tecnica de revisao. Diagnosticos completos ficam no log.

Associacao unica do caminho EXATO verificado e reutilizada automaticamente;
revisao atual passa pela mesma verificacao de historico ativo do coordenador.
Nenhuma inclusao de taxa antiga e presumida e nenhum efeito antigo e repetido.
Ambiguidade, caminho desconhecido com historico ja existente, formato invalido,
UNKNOWN ou historico inconsistente retornam recusa, nao criam vinculo vazio.
Em home sem qualquer associacao, cria metadados automaticamente por autorizacao
expressa do usuario para o fluxo automatico. Cancelar confirmacao nao cobra/transfere.

## Teste real na copia Flamengo

-Ganso na tela de Fluminense: apenas "Recusado: historico insuficiente para avaliar."
  Nenhuma associacao criada nessa recusa, nenhum contrato/efeito/cobranca.
-Matheus Reis: diretamente uma confirmacao com origem/destino,taxa0,prazo366.
  Cancelar deixou27jogadores,tracker0,historico2,saldo18937321 inalterados.
-Nova solicitacao/confirmacao: Matheus transferido;28jogadores,tracker1,historico3,
  saldo inalterado. Salvar/sair/carregar em nova JVM preservou o resultado.
-Na mesma copia, inputs ARTIFICIAIS apenas para exercitar taxa: AEK com Marios24,
  colegas8,participacao0/8,disponibilidadefalse. Valor3905928,taxa390592.
  Depois de reabrir o save, clicar abriu DIRETAMENTE confirmacao, sem UUID ou revisao.
  Confirmar:29jogadores,tracker2,historico4;Flamengo18937321->18546729,
  AEK25704384->26094976. Prazo1674917602790. Clock inalterado em todas as etapas.
  Salvar/sair/reabrir novamente preservou ambos jogadores e saldos. Outra cotacao
  abriu diretamente confirmacao curta; cancelar nao criou terceiro emprestimo.

Branch ponderada entre temporadas foi testada em fixtures SINTETICAS separadas:
primeira temporada em clube novo, excluir clubes anteriores, atual70/historico30,
amostra curta com fallback, duplicados/ausentes, extremos e codec legados.
Nao apresenta esses testes como observacao de uma segunda temporada real.

## Bloqueio encontrado na instalacao existente

Inspecao SOMENTE LEITURA dos metadados do usuario encontrou QUATRO associacoes
para o mesmo caminho Flamengo.s22. Uma tem Ganso/ Rodrigo Castillo NO_EFFECT;
outra Macarrao APPLIED; outra esta vazia; outra Matheus Reis APPLIED. Sao namespaces
distintos. Nenhuma foi escolhida, fundida, apagada ou reancorada automaticamente.
O save original analisado nao tem trackers de emprestimo; nao inferimos quais
efeitos historicos foram salvos, descartados ou restaurados.

Por isso a versao simplificada recusa a identificacao desse caminho AMBIGUO na
instalacao existente, mesmo que uma preferencia antiga selecione uma associacao.
Usar a vazia ignoraria historicos das demais. A copia limpa usada nos testes tem
uma unica associacao e nao reproduz essa ambiguidade. Resolver a ambiguidade e a
inclusao/ausencia desses efeitos requer reconciliacao separada; nao foi simulada
como resolvida por esta entrega. A UI mostra recusa curta sem expor UUIDs.
