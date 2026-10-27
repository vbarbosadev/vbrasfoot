# Organização do repositório

## Branch main — SDK/loader

| Pasta | Conteúdo |
|---|---|
| `src/br/vbrasfoot/api` | Contratos públicos; autores não dependem dos internos |
| `src/br/vbrasfoot` | Loader e adapters internos |
| `hook-src` | Instrumentação do perfil suportado |
| `test` | Testes e fixtures sintéticas em código |
| `templates` | Ponto de partida para novos mods |
| `examples` | Exemplos integrados ao build/teste do SDK |
| `packaging` | Scripts e instruções de distribuição |
| `sdk-tools` | Validação do pacote público |
| `tools/catalog` | Preparação local do catálogo independente |
| `docs` | Guias de navegação/contribuição |
| `docs/archive` | Documentos antigos preservados, não guias atuais |
| `ai` | Prompt-base, skill e instalador seguro para autores |
| `roadmap` | Contratos detalhados, pesquisas e evidências históricas |

Os scripts da raiz são entradas existentes de build/teste/release. Foram preservados
para não quebrar CI, links e os comandos já distribuídos. Não mova fontes ou guias
históricos sem atualizar os consumidores e executar os testes correspondentes.

## Branch mods — catálogo

`mods/<nome>/` contém código, manifesto, README, testes e scripts do mod.
`sdk/src/` contém somente API pública MIT para compilação offline; não contém jogo.
`tools/` contém build genérico e empacotamento individual. `.github/` valida PRs.
`catalog.json` lista mods e limitações. Veja o README próprio dessa branch.

## Não publicar

`build`, `dist`, `cache`, `logs`, `backups`, `verification`, saves, dados por carreira,
EXE, classes extraídas, imagens/sons do jogo, senhas e históricos pessoais.
O `.gitignore` é uma proteção auxiliar, não uma auditoria de arquivos já rastreados.
Sempre revise `git diff --cached --stat` e os arquivos staged; nunca use `git add .`
sem revisar o conteúdo. Arquivos locais foram preservados, não apagados.
