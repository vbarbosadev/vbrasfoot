# Observador experimental — SDK 0.4.0

Compilado somente contra API publica pelo build do projeto. JAR em
`dist/career-observer.jar`, nao instalado em mods por padrao: requer launcher com
`--probe-career-hooks`, capacidade `events.career.observed` e permissao
`events.career.read`. Sem flag, o conjunto instalado e recusado antes de iniciar.

Para testar, copie somente esse JAR para `vbrasfoot/mods/career-observer.jar` na
**copia isolada** com loader 0.4.0, jogo fechado; nunca sobrescreva JAR existente.
Abra `vbrasfoot.cmd --probe-career-hooks`, carregue/consulte/feche normalmente.
Logs privados mostram `career-observer kind=...`; nao escreve dados ou gameplay.
Para voltar a iniciar sem hooks, remova explicitamente somente esse JAR, com jogo
fechado. Modo seguro ignora mods/hook; nao ha hot reload.

Callbacks sao observacoes historicas, numa thread daemon exclusiva. Podem estar
obsoletos quando entregues: compare sessao/geracao com `state()` e faca consulta
nova quando necessario; essa comparacao nao e transacao com as consultas do SDK.
Nao manipule Swing diretamente (use invokeLater); nao aguarde EDT durante shutdown.
Fechar assinatura nao mata codigo Java ja selecionado/em execucao.

Este mod apenas registra observacoes. PRIMARY_WRITE_COMPLETED nao garante backup,
.info, fsync ou sidecar consistente. INVALIDATED nao e CareerUnloaded.
Se fila estourar/callback falhar, assinatura encerra; `status()` e `state()` permitem
diagnosticar/reassinar explicitamente. Nao existe retry automatico ou entrega duravel.
Consulte `roadmap/CAREER-OBSERVATIONS.md` (ou guia equivalente no SDK distribuido).
