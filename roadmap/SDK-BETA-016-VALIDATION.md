# Entrega Beta 0.16.0 — 27/10/2026

## Implementado e delimitado

1. SDK-only atualizado: LoanAccess, TransferWindows, contrato Beta, Javadocs,
   quickstart e exemplo independente, validador JSON com capabilities condicionais.
2. Serviço público de empréstimos: facade por mod, permissão/capability, DTOs e
   tokens efêmeros, autoridade central do coordenador, guard revogado no shutdown.
   Ledger de empréstimos compartilhado preserva baseline/anti-replay entre autores.
3. Janela PILOTO LOAN-only: até 32 períodos inclusivos, configuração por carreira,
   autor/revisão CAS, persistência em sidecars, verificação quote/execute/effect.
   Caminho nativo e legado do loader verificam a política. Nunca edita calendário.

Loader de autores tem --developer-mods e marcador próprio, seleção normal de plugins,
gerenciador e vínculo de carreira. Exemplo de terceiro incluído no ZIP; nenhum jogo,
save, histórico pessoal, runtime Java ou lib proprietária distribuído.

## Validações executadas

- SdkGameplayBetaSelfTest: 23 checks de API/fachada, permissões, revogação, datas
  inclusivas, persistência/reinício, revisão obsoleta, perfil e plugin terceiro.
- PublicLoanOriginalRuntimeTest: 14 checks com decoder/adapters/coordenador/fachada
  reais em cópia descartável: fechado impede quote; política alterada impede accept;
  aberto aceita jogador naturalmente listado sem taxa, saldo/clock intactos, um tracker,
  replay recusado, save/reload original preserva jogador único. Marcadores de decoder
  e confirmação são artificiais: não é aceite manual do fluxo completo por terceiro.
- SDK gerado/verificado; test-sdk-package executado fora do checkout: templates,
  exemplos (incluindo loan-window), compilação só API, validador sem inicialização,
  arquivos extras/checksum/traversal/namespace proibido recusados.
  Quickstart atualizado depois e SDK regenerado; verificador de hashes/allowlist passou
  novamente. Nenhuma classe da API mudou entre essas duas gerações.
- Loader de autores extraído e comparado por hash; --validate-only selecionou apenas
  br.exemplo.loan-window e verificou o perfil suportado, sem iniciar o jogo.
- Regressões: JSON40, lifecycle102, perfis combinado10/sponsor8/loan9, listado20,
  UI loan30, continuação16+15, grouped11, coordinator70, identidade23,
  ativação sponsor101, ending43, fence nativo e dialogs11. Uma execução agregada
  excedeu timeout após loan UI; testes restantes foram executados separadamente
  e passaram. Não foi executado build.ps1 completo/matriz CI17+21 nesta revisão.

Build: `%LOCALAPPDATA%/Temp/opencode/sdk-beta-557d1b9f297a48508848f74819d2cfe5`.
Fixture runtime: `%LOCALAPPDATA%/Temp/opencode/sdk-public-loan-runtime-f95a32bd77f94a8892e0a0614646a980`.

## Artefatos locais, sem publicação

- Documents/VBrasfoot-SDK-Beta-0.16.0.zip (+ sha256 e pasta extraída).
- Documents/VBrasfoot-Loader-Autores-Beta-0.16.0.zip.

Nenhum EXE, instalação ou save pessoal alterado. Sem commit, tag ou publicação.

## NÃO concluído / não anunciar como pronto

- Janela global de compras, jogadores livres, pré-contratos e IA: ainda exige
  investigação e interceptação de cada caminho original. Expor datas em regra
  não significa controlar a economia inteira. EXE sem loader não aplica janela.
- Alteração/retrocesso do relógio da carreira: deliberadamente não implementado.
- Aceitação humana externa, matriz operacional completa, suporte e publicação
  revisada: gates separados, ainda pendentes. Não chamar de SDK estável/final.
- Proteções financeiras conservadoras antigas permanecem: patrocínio aplicado/incerto
  pode impedir empréstimos. Não apagar journals para contornar.
