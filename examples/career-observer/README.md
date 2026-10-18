# Observador experimental — SDK 0.8.0

No SDK-only atual, copie esta pasta para projeto novo e execute:

```powershell
.\build.ps1 -Sdk 'C:\Downloads\vbrasfoot-sdk\api\vbrasfoot-api-0.9.1.jar'
java -jar 'C:\Downloads\vbrasfoot-sdk\tools\vbrasfoot-validator-0.9.1.jar' .\dist\career-observer.jar
```

O build independente usa somente a API e inclui teste headless de fake proprio;
nenhum evento real e inferido do teste. LICENSE acompanha fonte/JAR. Nao embutir
API ou validador dentro do mod. JDK/loader/jogo nao sao incluidos no SDK-only.

Compilado somente contra API publica pelo build do projeto. JAR em
`dist/career-observer.jar`, nao instalado em mods por padrao: requer launcher com
`--probe-career-hooks`, capacidades `events.career.observed,events.career.io` e permissao
`events.career.read`. Sem flag, o conjunto instalado e recusado antes de iniciar.

Para testar, copie somente esse JAR para `vbrasfoot/mods/career-observer.jar` na
**copia isolada** com loader 0.8.0, jogo fechado; nunca sobrescreva JAR existente.
Abra `vbrasfoot.cmd --probe-career-hooks`, carregue/consulte/feche normalmente.
Logs privados mostram `career-observer kind=...`; nao escreve dados ou gameplay.
Segunda assinatura mostra `career-io kind=...`: EXISTING_LOAD_COMPLETED exige
ramo de inicializacao aceito; SAVE_SEQUENCE_COMPLETED exige esse load e todas
etapas observadas do save. Duas assinaturas consomem dois dos quatro slots do mod.
Sem caminhos, dados de carreira persistidos ou garantia de fsync/lifecycle completo.
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
