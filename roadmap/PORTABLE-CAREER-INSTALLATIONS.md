# Instalações portáteis — loader 0.12.1

## Contrato

O local atual de `sav` é fornecido pela instalação selecionada, não por um caminho
do desenvolvedor. Novos registros de associação usam formato 2 com diretório `.`;
novos grupos de identidade usam formato 2 com o nome relativo do save. Referências
relativas recusam caminhos externos, travessia e nomes inválidos.

Registros antigos em formato 1 permanecem intactos. Uma confirmação de cópia/movimento
da instalação completa cria apenas `.identity-registry/portable-root.properties`.
O marcador identifica a raiz legada para resolução dentro da instalação atual;
não acessa o local antigo e não precisa ser reescrito a cada movimento. Grupos
legados continuam verificando o checksum e a chave originais antes da resolução.
Aliases, UUIDs, tokens e históricos congelados não são regravados.

O vínculo de patrocínios e a resolução nativa de empréstimos oferecem a confirmação
para uma raiz legada única. Raízes misturadas, histórico corrompido/incompleto e
identidades ambíguas continuam bloqueados; não há seleção por igualdade de hashes.
Instalações antigas ainda no caminho original inicializam o marcador ao vincular,
preparando a próxima transferência. A nova identidade física de arquivo pode
exigir aceitação da revisão; isso não reconcilia nem repete efeitos financeiros.

Para outro PC Windows com Java 17+ e o perfil suportado do jogo, leve a pasta
**inteira**, incluindo `sav` e `vbrasfoot/data`. Copiar somente o `.s22` não leva
configurações/históricos e não recebe garantia de recuperação. Não há promessa de
funcionamento em qualquer sistema operacional nem recuperação de histórico excluído.

## Validação em 2026-10-23

- 33 verificações de portabilidade: caminhos com espaços/acentos, várias cópias,
  local antigo ausente, registros/grupos novos relativos, aliases congelados,
  revisão do primário, marcador corrompido e crédito aplicado não repetível.
- Regressões: associações 58; registro/consolidação 25; coordenador 70;
  integração nativa 28; continuação de empréstimos 16; histórico agrupado 11;
  gate 38; pipeline sponsor 101; lifecycle/permissões 95; JSON SDK 40.
- Migração ensaiada numa cópia temporária dos metadados reais antes da instalação.
- Não foi executado um teste físico em outro PC, nem ativação pelo fluxo completo
  de UI/coordenador nesta correção.

## Instalação autorizada

Atualizado somente:
`C:\Users\vinicius\Documents\Brasfoot22-23 - Copia`.
Loader 0.12.1; mod sponsor-manager mantido em 0.4.0.
As quatro associações antigas do Flamengo resolvem para o `sav` da cópia,
com o mesmo primário e os três membros históricos. Nenhum pagamento foi executado.
Todos os 88 arquivos preexistentes de saves/dados mantiveram seus hashes;
somente o marcador portátil foi adicionado aos metadados.
Validação do conjunto instalado com probe passou, sem iniciar o jogo.

Backup dos arquivos gerenciados e de toda `.career-state` anterior à migração:
`vbrasfoot/backups/before-portable-0.12.1-3c50e6fb1da844a2af0c0d8db2eb72c4`.
Build, testes e baseline:
`C:\Users\vinicius\AppData\Local\Temp\opencode\portable-sponsor-5a5cfa2fa16247aeaca950ff4f56f616`.

Para repetir uma migração offline explicitamente autorizada, com jogo fechado:
`br.vbrasfoot.CareerPortableInstallationTool --confirm-complete-installation-copy <pasta-atual-do-jogo> <diretorio-sav-legado>`.
A ferramenta adquire o lock do loader e não grava saves, gera IDs, limpa históricos
ou autoriza replay. Não deve ser usada para unir instalações independentes.
