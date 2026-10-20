# Rotulos financeiros e limites do refresh original — evidencia estatica

Analise read-only do EXE conhecido, SHA256 de Profile, usando jar/javap existentes.
Sem executar o jogo, alterar EXE/save, instalar ferramentas ou aprovar UI real.

## Rotulos agora estabelecidos para este perfil

| Operacao bruta | Acumulador/getter | Rotulo EXATO na UI original |
|---|---|---|
| credito categoria6 | best.C.dO / eH()I | `Patrocinio/Sócio torcedor:` |
| debito categoria7 | best.C.dQ / eI()I | `Estádio:` |

best.C.h(II)V offsets55–68 associa6 a dO; eH offsets0–4 o retorna.
a.cW.pp()V offsets155–180 cria linha, associa rotulo, le eH e adiciona a DW.
best.C.i(II)V offsets37–50 associa7 a dQ; eI offsets0–4 o retorna.
a.cW.pp()V offsets341–366 associa a linha `Estádio:` ao valor eI.

Isso comprova NOMENCLATURA visual, nao todos os produtores/fluxos de caixa dessas
categorias. Nao chamar categoria7 de salario nem declarar contratos/recorrencias
de patrocinio prontos. Adapter continua raw6/7 ate validar regras por operacao,
reconciliacao e aceite visual/persistente. Nenhum mod foi criado.

## Atualizacao de UI: nao usar um callback amplo como getter inocuo

- `private a.eg.qx()V`: refresh minimo do saldo principal. Le campo zu, chama
  best.ah.kb() em8, formata via best.C.c(J) em11 e JLabel.setText em14.
- `private a.cW.po()V`: refresh dos RESUMOS do dialogo financeiro, incluindo
  salarios/saldo/emprestimos/juros; caminho do saldo kb66/formatter69/setText78.
  Nao atualiza linhas de categorias.
- `private a.cW.pp()V`: constroi linhas e as ACRESCENTA a DW. Rechamar sem limpar
  duplica linhas. `pq()` troca modelo de tabela a partir de DW, sem reler ledger.
  Nao sao refreshes completos independentes seguros.
- `private a.eg.qT()V`: caminho original para reconstruir dialogo, criando NOVO
  a.cW em54–60; mostra modal em119 e chama qx em139 quando retorna. Nao abrir
  modal automaticamente como efeito colateral de um pagamento do loader.
- `public a.eg.re()V` NAO e refresh puro: chama best.S.w(best.ah) em25 antes de
  qx29 (possivel mutacao). qJ atualiza outras coisas e ordena jogadores; qt e
  outro caminho de detalhes de jogador, nao refresh minimo de saldo.

Nao foi integrado callback por reflexao apenas por esses nomes. Faltam binding
do painel/clube correto, lifetime/revocacao, EDT e aceite runtime/visual. Metodos
privados ainda precisam de fingerprint e recusa de estado errado num adapter
especifico. Nao alterar visual aprovado do comparador.

## Evidencia e cobertura

Pasta PRIVADA preservada:
 `Temp/opencode/finance-ui-static-cb1e3beb21294da5a2e20d7547ae8dc7`.
FINDINGS.md registra fontes/offsets/cobertura; a.cW-utf8.txt corrige decoding de
acentos de dumps anteriores; finance-host-model.txt cobre a.eg; best.C.txt cobre
aritmetica; class-fingerprints.txt lista identidades extraidas somente nessa pasta.

- best/C.class: e208455170e565d83ab0733a79ba2c10e4addfb9a07b583d6b06d46a460eded4
- a/cW.class: 791d26be610d85d66660a5d207098f89d18076f36be534cf0195c3c6684f7a5c
- a/eg.class: ca708c478ddd4ef77336e55b561862ea98cd2b7b89b0ed59c20b5c206c9b04fd

Scan javap cobriu1032classes nao-org;6classes org do loader original nao foram
disassembladas. Encontrou uma chamada externa de eH e uma de eI, ambas em pp,
e um constructor site de a.cW, em qT. Reflexao/dinamica fora dessa cobertura nao
foi observada. Busca .NET inicial sem candidatos nao foi usada como prova negativa.

Nenhuma UI foi aberta/refresh executado e nenhuma sessao humana nova foi feita.
Conclusoes estaticas nao equivalem a pixels, threading/lifetime aprovados,
aceitacao de pagamento no original standalone ou consistencia global do grafo.
