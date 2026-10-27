# Emprestimos experimentais — instalacao e limites

Requer Windows, Java17+ no PATH e a versao bf22-23.exe reconhecida pelo instalador.
Pacote nao inclui jogo nem Java. Feche o jogo/loader; faca backup da pasta inteira.
**Use primeiro uma copia descartavel do jogo, nunca seu unico save.**

Na pasta extraida do pacote, PowerShell:

```powershell
.\install.ps1 -GameDir 'C:\copia-do-jogo' -Preview
.\install.ps1 -GameDir 'C:\copia-do-jogo'
& 'C:\copia-do-jogo\vbrasfoot.cmd' --probe-career-hooks
```

O instalador recusa EXE desconhecido, arquivos gerenciados alterados, links e
atualizacao incompleta. Nao contorne essas recusas. So instala loader/launchers;
nao substitui EXE, recursos ou saves. Iniciar o loader cria backups e metadados.
Sem a flag experimental o painel de emprestimos nao fica disponivel.

## Uso

Devolucao nativa agora pode encerrar a exigencia de tracker ativo do contrato
historico:exige movimento nativo de ida e volta, jogador unico na origem e nenhum
tracker restante. Funciona antes de salvar e depois de reabrir. Nao apaga contrato,
nao repete jogador/origem e nao reembolsa taxas. Sem evidencia consistente recusa.

ATUALIZACAO: maximo3emprestados no destino nos dois caminhos. Com3oumais,nenhum
novo;contratos existentes nao sao desfeitos. Jogador ja disponivel no jogo usa a
rotina original sem taxa/medianas/historico do mod;limites e elegibilidade nativa
continuam obrigatorios. Jogador nao listado permanece na regra personalizada.

Para comparar nitidez em telas com escala fracionaria (125%,150%), abra
`vbrasfoot-nitido.cmd`: ativa emprestimos e escala Java100%, sem mudar o Windows.
Elementos podem ficar menores. O atalho normal preserva a escala automatica.
Usuarios que explicitamente escolheram manter o save atual podem ter referencia
auditada em loan-baselines: historicos antigos permanecem, sem replay, e apenas
os contratos exatos selecionados deixam de exigir emprestimo ativo no destino.
Ainda exigem jogador unico na origem e ausencia de tracker; outros efeitos
incertos, taxas e estados divergentes nao sao ignorados.

Uma unica associacao principal por caminho de save/carreira. Criacao repetida nao
gera duplicatas, inclusive entre processos. Grupos consolidados preservam todos
os historicos anteriores como fontes obrigatorias e aliases somente leitura.
Consolidacao de identidade nao recupera efeitos antigos nem prova taxas salvas.

### Fluxo recomendado: tela original do clube

1. Abra vbrasfoot-emprestimos.cmd (ou vbrasfoot.cmd --probe-career-hooks) e carregue
   seu save pela UI original. Abra Times, selecione um clube e jogador.
2. Clique Emprestimo(mod) na propria tela. Origem/destino/jogador sao os selecionados
   no jogo; nenhum UUID/chave precisa ser digitado. Avaliacao recusada exibe motivo.
3. A associacao unica do caminho carregado e resolvida automaticamente; nao pede
   UUID, chave ou selecao de carreira. Home sem associacoes cria metadados automaticamente.
   Caminho desconhecido com historico, associacoes ambiguas e inconsistencias
   recusam sem criar uma associacao nova para contornar o bloqueio.
4. Confira jogador, clubes, regra e taxa. Somente confirmar executa. Cancelar nao cobra.
5. Confira resultado, saldo e elenco. Pode solicitar outro jogador distinto na mesma
   sessao, respeitando limites, historico e estado atual. Nao ha save automatico.
6. Depois de salvar, feche/reabra e carregue o save antes de novo pedido. O mod
   valida a nova revisao, verifica historicos ativos e usa a nova base sem replay.
   Essa validacao nao comprova inclusao de taxas antigas no save.

### Painel avancado de pedidos (legado)

1. Carregue uma carreira existente pela UI original, sem avancar a data.
2. Menu vbrasfoot / Carreira e diarios: revise e crie/seleciona explicitamente a
   associacao. Guarde os UUIDs de carreira e binding; nao crie outra para burlar erros.
3. Menu vbrasfoot / Emprestimos: informe ambos UUIDs, filtre e consulte clubes,
   selecione origem e destino humano, consulte o elenco e selecione jogador.
4. Use Consultar regra e taxa para consultar a liberacao relativa ao clube de
   origem, a taxa e as restricoes ATUAIS de destino, sem transferir.
   Registrar e avaliar tambem faz essa verificacao antes de gravar
   uma chave nova; recusa nativa impede o registro. A consulta nao garante execucao
   futura e nao explica retrospectivamente pedidos antigos. Guarde a chave gravada.
5. Somente APPROVED na revisao atual permite Transferir pedido aprovado e a
   confirmacao separada. O jogo ainda revalida o elenco antes do efeito.
   O botao fica desativado sem pedido aprovado revisado. Trocar UUIDs, chave ou
   selecao invalida a habilitacao; use Revisar pedidos para rever pedido existente.
6. Confira o resultado e salve pelo menu original; nao ha save automatico.
7. Apos reabrir, informe os mesmos UUIDs/chave e use Revisar pedidos.
8. Uma chave encerrada nunca e reaberta por Registrar e avaliar. Para uma NOVA
   solicitacao, use Novo pedido, consulte o elenco e escolha um jogador elegivel.
   Essa acao nao altera o historico e nao remove bloqueios de APPLIED/UNKNOWN.

## Restricoes que fazem parte desta entrega

- Experimental interno do loader; SDK externo continua somente leitura.
- Regra NOVA do mod: origem libera gratis quem tem forca <= mediana do elenco.
  Acima da mediana, avalia todas as temporadas registradas pelo jogador no clube
  atual, sem incluir clubes anteriores. Com dados suficientes, pondera70% da
  participacao atual e30% da historica anterior no clube. Se so um periodo tiver
  amostra suficiente (maximo conhecido/soma de maximos>=5), usa esse periodo.
  Ate25% permite taxa10% do valor, arredondada para baixo. Primeira temporada
  registrada no clube usa apenas seus dados atuais; nao herda jogos de outro clube.
  Usa maximos conhecidos por temporada dos historicos dos colegas atuais, nao
  reconstrucao completa de elencos antigos. Ausentes nao significam zero jogos.
  Sem evidencia suficiente, a interface mostra somente recusa curta.
  Contratos antigos conservam sua politica sem reavaliacao silenciosa.
  Destino tambem exige candidato <= sua mediana; destino35+ recusa; vazio exige dados.
  O indicador nativo de disponibilidade NAO restringe novos contratos desta regra.
  Limite nativo de quatro emprestimos e elegibilidade do destino continuam ativos.
- Taxa mostrada antes da confirmacao, debitada do destino e creditada na origem.
  Saldo insuficiente recusa. Em clube IA o credito nativo altera saldo sem lancar
  no demonstrativo humano. Falha parcial e UNKNOWN: nao ha rollback ou replay.
- Contratos v1 antigos preservam bytes e regra nativa, sem taxa retroativa;
  apenas pedidos NOVOS usam o contrato v2 com regra, participacao e financas.
- Prazo NATIVO366dias, sem duracao configuravel; apenas destino humano.
- APPLIED significa efeito observado em memoria, nao garantia generica de save.
- EXECUTING interrompido/UNKNOWN nunca e repetido; falha nao significa rollback.
- NO_EFFECT encerra a tentativa sem transferencia. Novas tentativas exibem o
  retorno nativo (ou recusa da pre-validacao) e o registram no log da sessao.
  Motivos detalhados antigos nao foram gravados; nao ha reconstrucao retroativa.
- Revisao obsoleta invalida aprovacao acionavel; historicos ficam preservados.
- Continuacao de novos pedidos DISTINTOS esta disponivel apenas pelo botao da tela
  original, com historicos ativos consistentes. Depois de salvar exige reabrir/carga
  original e validacao automatica da nova revisao. Painel legado fica somente leitura apos
  o primeiro registro no ledger de continuacao.
- UNKNOWN/EXECUTING, fontes desaparecidas/alteradas, conflitos, efeitos financeiros
  alheios e emprestimo antigo sem tracker exato continuam bloqueando. Nunca repetir
  para corrigir taxa/transferencia supostamente ausente do save.
- Limite32registros novos no ledger, sem apagar antigos. Mesmo jogador/origem
  historicos nao podem ser solicitados novamente trocando chave. Ciclo de retorno/
  expiracao ainda nao qualificado para liberar novo emprestimo desse jogador.
- Nao apagar `.career-state`, trocar chave ou criar associacao para contornar
  bloqueios. Preserve a pasta e os logs para investigacao; nao tente novamente.
- Nenhuma partida, avancamento de calendario ou retorno automatico foi executado
  na validacao integrada. Nao ha promessa de compatibilidade com todos os saves.

O fluxo real solicitar/avaliar/transferir/salvar/fechar/reabrir passou em copia
descartavel; repeticao da chave foi recusada. Detalhes: LOAN-INTEGRATED-VALIDATION.md.
Desinstalacao: execute `C:\copia-do-jogo\vbrasfoot\app\uninstall.ps1`
primeiro em preview (padrao); so use `-Apply` apos revisar. Preserve backups/dados.
