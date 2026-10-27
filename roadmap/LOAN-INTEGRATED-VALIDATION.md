# Emprestimos: validacao integrada de 21/10/2026

## Resultado observado

Fixture descartavel `integrated-ui-284c9d11d40742429487898e6d130bf1`, sob
`%LOCALAPPDATA%\Temp\opencode\brasfoot-loan-fixture`. Nada instalado no jogo pessoal.

O harness iniciou `Main.run --probe-career-hooks` com home `fixture\vbrasfoot`.
Somente comandos de componentes Swing foram usados; nenhum comando de emprestimo
ou autoridade artificial do harness foi usado neste fluxo.

1. Carregar LOAN_FIXTURE pela UI original; Athletico com 33 jogadores.
2. Criar associacao pelo painel Carreira e diarios, com confirmacao explicita.
   Career `3e0ddb5d-a42d-46af-bba3-b414bb9c2f38`, binding
   `1c1ea57a-6f50-4feb-9945-fe8735d731fb`.
3. No painel Emprestimos, consultar AEK Atenas e Athletico Paranaense, selecionar
   Marios Balamotis (forca24 natural, sem edicao), registrar e avaliar.
   Chave `loan_67e42bab613a4d2d884a8dca605c3a1e`: APPROVED.
4. Transferir e confirmar: EXECUTED/NONE, estado duravel APPLIED.
   Contador nativo 34/35 imediato; tracker1/historico1; origem AEK Atenas;
   destino Athletico; vencimento1674563978893.
5. Salvar pelo menu Opcoes/Salvar jogo e fechar por Sair do jogo (JVM encerrou).
6. Abrir uma nova JVM, carregar o mesmo save e revisar pelo painel com os UUIDs.
   Jogador/34jogadores/tracker1/historico1/vencimento preservados.
   Revisao alterada=true, APPLIED_SAVE_UNPROVEN: historico, nao replay.
7. Tentar confirmar a transferencia da mesma chave: recusada por associacao/revisao
   divergente. Estado do jogo continuou com um unico tracker/historico.

Clock em todas as etapas:1642941578893 (23/01/2022). Nenhuma partida ou avanco.
SHA256 do save final: `3f123745d1b097e6c2590f1c8a5b65cd676afe069592d8283e985faffcaced79`.
SHA256 da semente: `232c5f2fe07dd28fb538d103716668822c8314417ba0f7051f0f459a7439289e`.
SHA256 do EXE: `2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.

## Limites da prova

Uma transferencia natural em uma carreira descartavel; nao e matriz de todos os
elencos, plataformas ou falhas de energia. Falhas poscommit e reinicio do store
sao testes sinteticos separados, nao crashes reais do jogo.

Apos esse teste, o painel foi reforcado para recusar historico/revisao obsoleta
antes de oferecer confirmacao e exibir APPROVED antigo como STALE_REVIEW_REQUIRED;
EXECUTING e exibido como OUTCOME_UNKNOWN. A decisao original permanece intacta.
Reteste em nova JVM com essas fontes finais: reabrir save, preencher UUIDs/chave,
clicar Transferir resultou em "Transferencia bloqueada" sem abrir confirmacao.
Clock/tracker/historico/elenco/prazo continuaram identicos. Smoke Swing final passou.

O bloqueio conservador permanece para novas transferencias apos efeito/save.
Nao foi implementada recuperacao que permita uso continuado em revisoes novas.
Nao existe API publica de escrita. APPLIED nao e uma prova generica de inclusao
no save; aqui a persistencia foi verificada pela reabertura concreta do jogo.
