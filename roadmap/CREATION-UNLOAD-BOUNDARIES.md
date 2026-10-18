# Criacao e descarregamento — mapa estatico18/10/2026

Estado: mapeamento parcial reproducivel, **nao novos eventos publicos**. Loader
permanece0.8.0. EXE conhecido22-23, SHA
2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c.

## Ferramenta somente leitura

```powershell
.\map-career-boundaries.ps1 -GameDir '..\Brasfoot22-23'
```

Compila ferramentas target-free, testa classificador e inspeciona entradas .class
do ZIP principal sem carregar/inicializar classes. Saida TSV nova privada em
build, nunca sobrescreve existente; recusa perfil desconhecido/links/junctions.
SHA do EXE conferido antes/depois. Cada site retém caller/target/descriptor/offset,
opcode anterior e hashes da classe/Code. Roles sao labels do analista, nao prova
automatica de pronta/unload ou ligacao causal assincrona.

Recorte final:1038classes/73referencias classificadas, sendo24progressao compartilhada,
10caminho agendado,13trocas de painel,7writes de raizes,3callers de reset,
2callers de leitura,2de save,1entrada criacao,5saidas de processo,3fechamentos
de janela e3politicas de close cujo argumento requer inspecao adicional.
Graphics.dispose e JDialog.dispose generico excluidos: nao candidatos de unload.
21checks de classificador sintetico e11scanner existentes aprovados, CI17/21.

## Criacao: fronteira assincrona ainda sem commit confiavel

- a.bV.AB chama az/V, mas V escolhe ap ou aa conforme flag bk. Ambos caminhos
  compartilham rotinas de progressao com outras telas/partidas/temporadas.
- a.cR.AC pode apenas escolher painel10 e retornar. pn chama V, mas nao e
  o unico caminho de continuacao desse painel.
- a.cR.oR cria Timer e agenda a.cS com atraso0. Constructor de a.cR chama oR.
  a.cS.run cancela Timer e chama V. Portanto retorno do construtor/painel nao
  prova que essa continuacao ja executou, nem que foi exclusiva de criacao.
- best.f.ap pode selecionar painel2, autosave ou at; at pode selecionar painel5
  ou chamar ax. best.S.aY(5) monta a.hk; a.hk.oR agenda a.ho, cujo run chama ax.
  ax pode voltar a ap, trocar para painel6 ou apenas retornar conforme flags.
  Isso acrescenta uma segunda fronteira assincrona, nao um simples RETURN final.
- best.S.aY(2) monta painel de elenco a.eg, mas o site e reutilizado. Menu,
  contagens de clubes, calendario, retorno de V/ap e raiz nao-nula continuam
  insuficientes para declarar nova carreira pronta.

Observacao estatica, nao execucao de Timer/partidas pelo agente. Nao foi usado
invokeLater para alterar thread original nem instrumentado best.f (schema/save).
Nenhum token causal entre esses Timers ja foi demonstrado pelo runtime.

## Descarregamento: limites das rotas examinadas

- a.eg.rq encerra JVM via System.exit(0); best.S configura JFrame close operation3
  (EXIT_ON_CLOSE). Sao saidas de sessao, nao unload entregavel dentro da sessao.
- Inventario encontrou5 sites System.exit no ZIP, incluindo saidas fora da carreira.
  Nao os executar nem usar callback de shutdown como unload comprovado.
- Sete PUTSTATIC de SR/afQ: dois null em c.a.<clinit>, dois novos objetos no
  construtor, dois decode no load e um setter SR. Os nulls sao inicializacao
  da classe, nao descarregamento de carreira ativa. So estes sites diretos no
  recorte; nao prova ausencia de reflexao/JNI/writes de bibliotecas/in-place.
- Callers do construtor: best.h.mh, forms.q.actionPerformed e a.gq.mi. Os caminhos
  inspecionados configuram/criam raizes/telas novas; nao ha commit de unload
  validado neles. Reset continua INVALIDATED/UNKNOWN, nao CareerUnloaded.
- best.n.a(JFrame) apenas guarda o frame. Dispose de frame/dialogo nao prova
  que grafo inteiro perdeu referencias ou que save pendente terminou.

Nao encontrou-se rota de troca pela UI nas sessoes humanas anteriores; o mapa
nao afirma ausencia global dessa funcao. Busca limitada a invocacoes/writes
diretos do ZIP principal, sem bibliotecas aninhadas/reflexao/JNI/overrides dinamicos.

## Proxima implementacao diagnosticavel

1. Observar fronteiras a.cR.oR/a.cS.run e a.hk.oR/a.ho.run por fingerprints e
   sites exatos, com IDs causais limitados da tarefa/raizes/geracao. Nao concluir
   apenas porque qualquer Timer terminou ou a.eg apareceu.
2. Correlacionar exclusivamente operacao de nova carreira iniciada, separando
   load existente, nova temporada, cancelamento e falha. Expirar/invalidar origem
   se roots/geracao mudarem; callbacks cruzando threads nao herdam ThreadLocal.
3. Corroborar ramo sem/com amistoso quando oferecido e momento humano de acesso
   ao elenco. Nenhum novo teste de criacao solicitado antes de sonda preparada.
4. Se so houver saida da JVM no perfil observado, manter disposal de sessao
   separado e nao inventar CareerUnloaded para habilitar P3. Identidade/associacao
   e recuperacao de sidecars ainda sao gates independentes.

Nenhuma criacao/jogada/save/alteracao de recursos realizada pelo agente nesta
investigacao. Evidencia privada em build/lifecycle-map-49bcef9e049a4f9e8c9cb6b7c37bc6f2;
TSV/javap nao distribuidos. REA sem provider JVM configurado ja documentado;
reusado javap/scanner local, sem setup/download/reconfiguracao de engines.
