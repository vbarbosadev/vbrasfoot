# Pipeline interno de emprestimos: contratos e efeito controlado

Atualizacao posterior: [LOAN-MANAGER.md](LOAN-MANAGER.md) descreve schema2 de
decisoes, inventario integrado ao coordenador, painel de pedidos sem transferencia
e teste de writer/load originais com frame sintetico. As secoes abaixo registram
o incremento inicial; seus gates nao equivalem a qualificacao de escrita publica.

Codigo experimental interno. Nao adiciona permissao/capacidade de escrita publica,
nao conecta Main/ModContext e nao instala mod. Loader instalado e saves pessoais
inalterados. A fila de sessao nao foi convertida em workflow duravel completo.

## Contrato imutavel

`LoanRequest` guarda UUIDs career/binding, revisao exata SHA256/tamanho, owner/key,
locators compostos dos dois clubes (index/id/nome), indice do jogador no elenco
de origem, snapshots ordenados completos dos dois elencos e clock da carreira.
Nao persiste PlayerRef/ClubRef, objetos/classloaders do jogo ou autoridade de gate.
Esses locators so valem para a revisao selecionada; nao sao identidade global.

Codec `vbrasfoot.loan-request/1` inclui hash do perfil e identificador da regra
`destination-median/1`, limita cada elenco a500 e payload a128KiB; decode exige
reencoding identico, sem trailing bytes, versao/profile/policy exatos. Digest
SHA256 desses bytes forma o intent da operacao interna `LOAN_ACCEPT`.
As quatro operacoes anteriores do journal continuam codificadas por nome.

`LoanRequestStore` usa root DEDICADO (nao financeiro/journal/dados globais), mesmo
CareerSidecarStore/CAS/quotas/fences de disco existentes. Ate32contratos por owner,
envelope max1MiB; nunca descarta key para permitir outro efeito. Mesma key/payload
so confirma leitura; key divergente conflita. Leitura de namespace com orphan e
sem HEAD exige revisao, nao equivale a store vazio. Nenhuma migracao de revisao
ou conciliacao APPLIED/UNKNOWN foi adicionada.

O payload pode ser persistido sem efeito. Isso nao grava decisao/cancelamento da
fila de sessao nem autoriza sua execucao apos reload.

## Despacho

`executeStoredLoan` exige regra aprovada, contexto/owner/revisao/store/journal e
handler correspondentes. Persiste payload antes do journal e do efeito; confere
o token do journal novamente depois da persistencia do payload. Nao ha transacao
entre arquivos: payload isolado ou PREPARED incerto nao autoriza retry automatico.
O dispatcher generico recusa handler persistente de emprestimo se o contrato nao
foi passado pelo caminho de persistencia. Financas mantem o caminho anterior.

Mesmo gate compartilhado conceitual/journal/EDT/timeouts/revogacao existentes;
nenhum gate novo e ligado a Main. APPLIED significa resultado observado em
memoria, nao inclusao causal no save. Nova JVM/novas roots com journal historico
retornam RECORDED_ONLY sem invocar metodo original. UNKNOWN continua bloqueado.

## Adapter

`LegacyLoanAdapter` verifica fingerprints exatos de cinco classes originais.
`CareerBridge.loanSelection` resolve somente handles emitidos pela consulta, na
EDT; nao expoe objetos/metodos internos a mods e nao recebe jogador arbitrario
como contrato publico. Destino deve ser clube humano exato em P/aN; clubes
distintos, locators compostos unicos, jogador exato no indice de origem.

O adapter rejeita alias do jogador nos elencos profissionais/juniores observados,
locator de jogador ambiguo na origem e tracker ja existente para ele. Limites:
16384clubes/500por elenco/1milhao de memberships/500ms no scan de memberships,
10000trackers. Essa varredura nao e ownership exclusivo de TODO o grafo ou sandbox.
Getters e varreduras de locator adicionais nao sao um timeout total da EDT.

Plan guarda roots/clubes/jogador por referencias fracas e e one-shot. Antes da
chamada repete identidade, snapshots ordenados e clock, sem atualizar payload
antigo para torna-lo valido. `best.l.a` e chamado uma unica vez e conserva suas
regras de disponibilidade, elegibilidade, lotacao e numero de emprestimos.
Nao usa setter bruto de roster, bypass de lista da UI, rollback ou prazo proprio.

Pos-check observa membros/ordem dos dois elencos, append de tracker/historico,
anchors do jogador/tracker, prazo exatamente clock+366*86400000, clock e saldos
imediatos. Falha/throw apos invocar e conservadoramente UNKNOWN. Codigos nativos
de recusa2..6 so sao NO_EFFECT quando o subconjunto observado permanece igual;
os ramos estaticos retornam antes do mutador nativo. Nao cobre salarios futuros.

## Testes medidos

-35checks proprios de codec/store/dispatcher, payloads/efeitos artificiais:
  roundtrip, digest, payload invalido, conflito/limites/revisao/namespace, refusal
  de policy e contexto, persistencia antes do efeito, bypass recusado, duplicate,
  restart, faults RECORD_WRITTEN/HEAD_STAGED/HEAD_COMMITTED e UNKNOWN sem replay.
-Regressoes isoladas:29dispatcher/32gate/28journal/46finance-request-store,
  mais14policy/21fila de sessao, aprovadas ao adicionar a infraestrutura.
-Fixture original privada: `limit-qualification/LoanAdapterProbe.java` em
  `%LOCALAPPDATA%/Temp/opencode/brasfoot-loan-fixture`.
  Contextos/owners dos testes sao ARTIFICIAIS, nao qualificacao de Main.
-Caso inicial sem controle: Diego forca41 foi recusado pela policy de mediana,
  antes de invocar regra original. Nenhuma excecao foi aberta na policy para ele.
-Controles posteriores mudam SOMENTE em memoria sua forca para20 antes de
  capturar o contrato. Nao representam a fixture original sem alteracoes:
  - `adapter-positive-5f8a97a985344845bb2530f3a1b7fb89`: EXECUTED/native1, codec
    offline preservou emprestimo/prazo, seed restaurada com mesmo controle de
    forca nao repetiu journal historico; RECORDED_ONLY.
  - `adapter-stale-2abdf8cdc84c4f39b9a0e48c24df09c5`: elenco reordenado depois
    de preparar; NO_EFFECT/native-1 (metodo original nao invocado).
  - `adapter-unavailable-87ae5c60f53e465bafd24527eb023c08`: disponibilidade
    desativada depois de preparar; NO_EFFECT/native5 (recusa original).
  - `adapter-alias-1b5bfc1e5f764091afdf89b500094621`: alias artificial na lista
    junior do destino depois de preparar; NO_EFFECT/native-1.
-Cada teste confirmou input/copia com SHA256
  `232c5f2fe07dd28fb538d103716668822c8314417ba0f7051f0f459a7439289e` e EXE intacto.
  Sem partidas ou progresso de calendario. Codec offline nao e writer/UI original.

## Gates ainda abertos (nao contornar)

1. Inventario/recovery de root de emprestimos integrado ao coordenador de Main,
   com conflitos de revisao/copia/restore tratados junto dos outros journals.
2. Decisoes/cancelamentos de pedidos duraveis, revalidacao live e ligacao da fila
   ao dispatch sem converter referencias temporarias em identidade persistente.
3. Qualificacao do save/writer original e reabertura/UI/refresh por operacao,
   sem inferir inclusao por igualdade de valores ou por codec offline.
4. Somente depois: contrato publico tipado, permissoes/capacidades e facade
   revogavel, painel de pedidos e mod instalavel. Sem expor nomes obfuscados.

Nao habilitar permissao apenas por existir um adapter. `writeAuthority=false` e
`operationInclusionProven=false` permanecem para a integracao publica.
