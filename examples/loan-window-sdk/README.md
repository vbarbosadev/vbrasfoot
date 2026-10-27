# Exemplo SDK-only: empréstimo e janela piloto

Compile com `build.ps1 -Sdk CAMINHO/api/vbrasfoot-api-0.16.0.jar`.
Copie `dist/loan-window-example.jar` para `vbrasfoot/mods` da distribuição de autores,
com jogo fechado, e abra `vbrasfoot-mods.cmd`. Não embuta a API no mod.

Os dois menus demonstram confirmação explícita de pedido e configuração de um período.
Não usa classes ofuscadas/reflection, não muda atributos, não edita o relógio e não aceita
pedidos automaticamente. Janela fecha apenas empréstimos executados pelo loader.
O exemplo não governa compras, jogadores livres ou contratações da IA.

Teste primeiro em cópia descartável. Faça backup e preserve `sav` e `vbrasfoot/data`.
Políticas persistem em sidecars independentemente de salvar pelo jogo. Para desativar via
API, configure `new TransferWindows.Rules(false, List.of())` usando a revisão atual.
Uma política habilitada sem períodos bloqueia todos os empréstimos pelo loader.
Não há rollback universal de saves/sidecars antigos, sandbox Java ou aceitação pública estável.
