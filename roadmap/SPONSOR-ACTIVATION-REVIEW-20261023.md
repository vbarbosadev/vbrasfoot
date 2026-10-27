# Revisão do patrocínio — 2026-10-23

Desenvolvimento: loader 0.12.0 / sponsor-manager 0.4.0.
Instalação pessoal em `C:\Users\vinicius\Documents\Brasfoot22-23` permanece
em 0.11.0 / 0.3.0; nenhum pagamento pessoal foi executado nesta revisão.

## Correções desta revisão

- Exclusão composta de save/load/reset desde antes do claim até a conclusão
  do histórico do patrocinador, além da exclusão do dispatcher em timeout em voo.
- Cotação conserva a seleção exata da carreira; execução revalida o escopo e
  o handler confere a seleção novamente na EDT antes de qualquer efeito.
- Interface marca a tentativa como exigindo revisão antes de enviar a ativação,
  evitando reabilitar o botão com estado antigo após uma exceção.
- Metadados do SDK descrevem owner/permissão/capability restritos, exigência de
  probe e ausência de garantia universal de inclusão no save.
- Documentação atualizada e build do mod aceita `-Output` novo, sem sobrescrever
  o JAR antigo em `examples/sponsor-manager/dist`.

## Resultados confirmados

| Teste | Verificações |
|---|---:|
| CareerCommandGateSelfTest | 38 |
| SponsorActivationPipelineSelfTest | 101 |
| SponsorActivationStoreSelfTest | 47 |
| ModContextLifecycleSelfTest | 95 |
| SdkValidatorJsonSelfTest | 40 |
| SponsorStoreTest (inclui JPEG/proporção) | 28 |
| SponsorPanelLayoutTest | 12 |
| SponsorClubLookupTest | 9 |
| SponsorOriginalRuntimeTest | 14 |

O teste original foi repetido numa cópia fresca marcada:
`C:\Users\vinicius\AppData\Local\Temp\opencode\sponsor-reviewed-runtime-5611b06d89c9441ea7e83df3e5e7f1d4`.
Usou métodos originais e save/reload originais, mas **autoridade artificial**.
Não comprova o caminho completo de carga operacional/UI/coordenador.

SDK 0.12.0 foi gerado e `test-sdk-package.ps1` passou fora do checkout,
incluindo templates/exemplos, allowlist, hashes e corrupção recusada.
O JAR final do mod foi validado pelo validador isolado, com
`observationModeRequired=true`; isso não inicializa o jogo/mod nem valida o conjunto instalado.

Artefatos de revisão (não instalados):
`C:\Users\vinicius\AppData\Local\Temp\opencode\sponsor-review-b3e7868a193d4cc7a34e1587f230fcfa`
(`classes`, `tests`, `sdk-final.zip`, `package-final/sponsor-manager.jar`).

O build completo inicial excedeu o timeout durante a suíte; a execução seguinte
em background concluiu com sucesso, incluindo os testes de empréstimos, hooks,
recuperação e exemplos SDK-only. Casos de symlink/fileKey indisponíveis foram
explicitamente ignorados, não aprovados. Como alguns fontes foram atualizados
durante essa execução, os testes alterados acima também foram compilados e
executados separadamente contra os fontes atualizados; os JARs de `dist` desse
build não devem substituir os artefatos finais revisados sem novo empacotamento.

Hash do `sav/Flamengo.s22` pessoal conferido novamente, sem alteração:
`D298949F04F11DFE952B006FDB50B6B6C69B8E2A41251A038989744E0967C88C`.

## Próximo gate antes de instalar

### Instalação posterior autorizada na cópia

Por pedido explícito do usuário, loader 0.12.0 e mod 0.4.0 foram instalados em
`C:\Users\vinicius\Documents\Brasfoot22-23 - Copia` para testar a integração.
A instalação pessoal sem o sufixo ` - Copia` não foi atualizada.
O loader foi recompilado dos fontes atuais com hooks; o mod veio de
`package-final/sponsor-manager.jar` acima. Validação instalada com probe e export ASM
passou (perfil, entrada, bibliotecas, bridge e seis manifestos), sem iniciar o jogo.
Hashes dos oito arquivos gerenciados e do mod foram verificados, sem transação pendente.
Os 88 arquivos preexistentes de `sav` e `vbrasfoot/data` conservaram seus hashes.

Backups na cópia:
- `vbrasfoot/backups/before-sponsor-0.4.0-3cbf7ee847b34f7eb3ba70c63f05fadc`.
- `vbrasfoot/backups/mod-update-sponsor-manager-20261023-194336-d604451767104ca8a76f060c7897b936/sponsor-manager.previous.jar`.

Staging e baseline de integridade:
`C:\Users\vinicius\AppData\Local\Temp\opencode\sponsor-copy-install-08d60ff0dd664fba832505326d8f76d4`.
Nenhuma ativação foi executada durante a instalação. A aceitação completa pela
interface/coordenador continua pendente; abra `vbrasfoot-nitido.cmd` dessa cópia.

Validar numa cópia descartável o fluxo completo de carga original operacional,
vínculo no painel, cotação, confirmação, crédito/estádio/logo, save/reload e
recusa de repetição, sem instalar autoridade artificial nem relaxar o verificador.
Só depois empacotar/instalar o loader e o mod na pasta autorizada.

Histórico aplicado/incerto continua bloqueando nova ativação e pode bloquear
empréstimos/escritas genéricas até reconciliação. Fechar sem salvar ou restaurar
backup não autoriza compensação/replay. Cópias sem sidecars, histórico removido
ou sidecars antigos/restaurados não têm recuperação universal nesta fase.
