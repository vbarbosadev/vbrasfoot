# Gerenciador de pedidos — estado real da entrega

ATUALIZACAO: o fluxo recomendado e o botao nativo simplificado, com historico por
clube e resolucao automatica de associacao exata. Evidencias e limites atuais em
[FLAMENGO-CLUB-HISTORY-VALIDATION.md](FLAMENGO-CLUB-HISTORY-VALIDATION.md).
As politicas e paineis legados abaixo permanecem para auditoria de contratos antigos.

## Regra relativa e taxa (atualizacao)

Regra origin-median-or-low-use/2: participacao parcialmente conhecida pode
comprovar liberacao. Candidato conhecido e maximo conhecido>=5 com candidato<=25%
desse maximo ja bastam: registros ausentes so poderiam aumentar o maximo real,
nao invalidar essa conclusao. Desconhecidos nunca viram zero. Fora desse caso,
dados parciais continuam INSUFFICIENT_DATA, e o diagnostico exibe a cobertura.
Contratos gravados com /1 conservam sua versao, decisoes e bytes canonicos.
Transferir inicia desativado, so habilita apos revisao APPROVED da chave atual
sem bloqueios conhecidos; edicao de identidade/chave/selecao ou operacao nova
invalida o estado. Revisar pedidos pode habilitar um aprovado existente; execucao
sempre revalida no coordenador e resultado/erro desabilitam o botao novamente.

Novos contratos usam codec v2; v1 permanece canonico e sem taxa retroativa.
Origem libera gratis forca<=mediana; acima, uso<=25% do maior numero de
participacoes do elenco libera por10% do valor (inteiro arredondado para baixo).
Exige maior amostra>=5jogos; registros desconhecidos/incompletos nao viram zero.
Leitura de best.k por temporada atual e clube, sem usar partidas de outros clubes.
Destino continua avaliado pela mediana e pelos limites/elegibilidade nativos.
Disponibilidade nativa nao limita v2: efeito usa a rotina nativa de emprestimo
apos validacao completa, sem reescrever flag e sem expor bypass publico no SDK.
Taxa/saldos/ledgers/participacao entram no digest e sao revalidados antes do efeito.
Emprestimo+debito+credito ficam sob uma lease/journal; falhas parciais sao UNKNOWN,
nao rollback, e nunca recebem repeticao automatica.
Consulte [LOAN-RELATIVE-FEE-VALIDATION.md](LOAN-RELATIVE-FEE-VALIDATION.md).

Atualizacao de runtime: [LOAN-FULL-UI-VALIDATION.md](LOAN-FULL-UI-VALIDATION.md)
confirma transferencia/save/reabertura na UI original em fixture. A falha de
contador/sorter foi corrigida e retestada em fixture nova (ver secao final).
Integracao pelo painel real: [LOAN-INTEGRATED-VALIDATION.md](LOAN-INTEGRATED-VALIDATION.md).

O desenvolvimento0.9.4 agora inclui um painel INTERNO do loader para pedidos e
decisoes persistentes. Nao e um mod externo instalavel nem uma API de escrita
publica concluida. A transferencia tem confirmacao separada e autoridade da sessao real.
Loader instalado/EXE/recursos/saves pessoais nao foram atualizados.

## Painel

Correcao de diagnostico: Verificar elegibilidade nativa consulta disponibilidade,
limites e elegibilidade de destino, sem chamar a rotina de transferencia.
Registrar uma chave nova faz essa consulta primeiro e nao grava em caso de recusa.
Transferencia continua revalidando o estado e usando a unica chamada nativa.
O resultado mostra codigo/motivo nativo ou recusa anterior a chamada; esses detalhes
tambem ficam no log da sessao. Contratos antigos nao ganharam motivo retroativo.
Registrar novamente uma chave existente apenas exibe o estado; Novo pedido gera
uma chave na tela e limpa jogador, sem alterar contratos ou remover bloqueios.

Somente no modo opt-in `--probe-career-hooks`, fora do modo seguro, o menu do loader
registra **Emprestimos (experimental)**. Requer
carreira existente carregada e associacao explicita em **Carreira e diarios**.
Nao inicia carreira, amistoso, partida, avancamento de calendario ou save.
Nao identifica a carreira automaticamente por nome ou hash.

1. Selecionar os UUIDs career/binding da associacao revisada.
2. Filtrar e consultar clubes (ate100por filtro; refinar se necessario).
3. Selecionar origem e destino humano; consultar elenco e selecionar jogador.
4. Usar uma chave de pedido distinta para uma nova solicitacao. A chave e preservada
   apos erro para permitir inspecao, nao repeticao cega.
5. **Registrar e avaliar (sem transferir)** persiste primeiro PENDING e depois a
   decisao automatica da regra `destination-median/1`. Uma falha entre os commits
   pode deixar PENDING, que e exibido apos revisao.
6. **Avaliar pela regra** calcula apenas a recomendacao em snapshots imutaveis.
   **Cancelar pedido** so funciona antes de uma tentativa de execucao.
7. **Revisar pedidos** le estados e correlaciona contrato/journal, inclusive apos
    reabertura. Mudanca de revisao nao migra dados nem autoriza execucao.
8. **Transferir pedido aprovado...** revalida revisao, inventario e elencos,
   pede confirmacao e usa o gate da sessao original. Aprovacao sozinha nao transfere.
9. Salvar e sair pelos menus originais. Guardar ambos UUIDs e a chave do pedido.

Consulta de elencos e resolucao de handles rodam na EDT; verificacao de arquivos e
gravacao de metadados ficam fora dela. Selecao e botoes ficam bloqueados enquanto
uma operacao explicita esta em curso. Fechar a janela nao interpreta um commit
ja iniciado como cancelado. Abertura/selecoes incompletas nao criam metadados.

Os dados ficam em `.career-state/loan-requests` sob o home do loader, separados de
journals e contratos financeiros. A assinatura de owner e
`br.vbrasfoot.loader.loans`; conflito com ID de mod selecionado desativa o painel,
em vez de reutilizar o namespace silenciosamente. Nenhuma limpeza automatica.

## Persistencia e recuperacao

O contrato imutavel continua no codec `vbrasfoot.loan-request/1`.
O envelope de store e schema2: contratos + estado ordenado de cada chave.
Leitura de schema1 preserva os bytes e interpreta ausencia historica de decisao
como PENDING; apenas uma mutacao explicita grava schema2. Payload/digest/owner/
key/revisao antigos nao sao reescritos para uma revisao nova.

Estados: PENDING, APPROVED, REFUSED, INSUFFICIENT_DATA, CANCELLED, EXECUTING,
APPLIED, NO_EFFECT, UNKNOWN. APPROVED e apenas recomendacao, NAO transferencia.
Decisao armazenada contraditoria com a policy dos inputs imutaveis e CORRUPT.

O caminho interno de dispatch persiste EXECUTING antes de qualquer tentativa e
so emite uma lease privada na instancia que realizou esse commit. Reabrir um
EXECUTING, mesmo sem journal, e OUTCOME_UNKNOWN para recuperacao e nunca emite
outra lease. Falha apos efeito ou durante completion nao e rollback. A lease
de completion tambem e one-shot. Resultados historicos APPLIED/NO_EFFECT retornam
RECORDED_ONLY, inclusive em duplicata na sessao; nao significam efeito novo.

O coordenador inventaria essa root junto de journal/contratos financeiros/
evidencias, inclui owners nao selecionados, confere HEADs de novo antes de
confirmar e bloqueia contexto quando ha pendencia, efeito incerto, revisao antiga,
payload/receipt divergente ou APPLIED sem inclusao provada. Decisao fechada sem
tentativa (cancelamento/recusa/dados insuficientes) nao e confundida com execucao.
Nao ha reconciliacao automatica, reancoragem de contratos ou replay.
Na revisao alterada, PENDING/APPROVED aparecem como STALE_REVIEW_REQUIRED,
com a decisao gravada preservada para auditoria. EXECUTING aparece como
OUTCOME_UNKNOWN. Nenhum desses estados oferece confirmacao de transferencia.

**Limitacao importante:** apos APPLIED/UNKNOWN ou mudanca de revisao, novos
emprestimos nesse binding tambem ficam bloqueados. Nao criar outra associacao,
apagar metadados ou trocar chave para contornar isso. A recuperacao segura evita
replay, mas ainda nao libera uso continuado em revisoes posteriores.

## Verificacao desta rodada

-23checks de decisoes duraveis: reopen, cancelamento, lease one-shot, crash/fault
  apos commit de EXECUTING, schema1 sem migracao em leitura e upgrade explicito.
-43checks payload/store/dispatch;54coordinator;111contexto;29dispatcher aprovados
  isoladamente com as fontes desta rodada.
-Smoke Swing com janela PROPRIA, sem classes do jogo: abrir painel, transferencia
   com selecao incompleta sem adapter/arquivos e descarte de janelas.
  Screenshot: `%LOCALAPPDATA%/Temp/opencode/loan-ui-smoke-10389200899061316223/pending-manager.png`.
-Writer/carregador originais na fixture
  `brasfoot-loan-fixture/original-loan-writer-6287b0e201114d0da237075ca90e4cbc`:
  .s22/.sbck identicos e .info gerado; reload nativo preserva tracker/historico,
  jogador e clubes, prazo366 e clock. SHA256 do output:
  `b4aa3f1d61f47d888f8805e6ba09ff8cfd94d7ded803d2fc33b59f4c1c5a0d69`.
  Input e EXE preservados. O controle de forca20 da fixture permanece artificial.
  Frame sintetico: nao e aceite da UI completa, readiness ou inclusao causal.

## O que impede chamar o mod de completo

O fluxo integrado solicitar/avaliar/transferir/salvar/fechar/reabrir passou em
copia descartavel, sem avancar calendario. Ainda faltam politica de recuperacao
para uso continuado por operacao/revisao sem inferir causalidade
por igualdade de estado e contrato publico tipado/permissionado/revogavel para
mods externos. O SDK continua anunciando gameplay.write como indisponivel.
Nao criar permissao publica declarativa a partir desta integracao interna.
A agenda de retorno foi inspecionada,
mas nao avancada automaticamente para teste.
