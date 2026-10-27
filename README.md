# Catálogo de mods vbrasfoot

Esta é a branch **mods**, base das PRs de novos mods. Loader/SDK são desenvolvidos
na [`main`](https://github.com/vbarbosadev/vbrasfoot/tree/main).

| Pasta | Mod |
|---|---|
| `mods/patrocinio` | Patrocínio, logo opcional e mensagem salva; experimental, ID reservado |
| `mods/janela-emprestimos` | Exemplo da API de empréstimos e janela piloto LOAN-only |
| `mods/scout` | Scout de jogadores |
| `mods/comparador` | Comparação de jogadores |
| `mods/painel-carreira` | Painel de consultas de carreira |
| `mods/observador` | Observação de eventos experimentais |
| `mods/inspetor` | Inspetor de leitura |

## Instalar só os escolhidos

Obtenha ZIPs individuais nas Releases **quando publicados**; não há download de release
garantido por esta árvore. Alternativa: sparse checkout + build de cada mod escolhido:

```powershell
git clone --filter=blob:none --sparse --branch mods https://github.com/vbarbosadev/vbrasfoot.git vbrasfoot-mods
cd vbrasfoot-mods
git sparse-checkout set mods/patrocinio mods/scout mods/comparador mods/painel-carreira mods/observador sdk tools
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build-mod.ps1 -Mod scout
```

Repita o build para os outros nomes escolhidos. Só esses cinco diretórios de mods
serão selecionados; sdk/tools são necessários para compilar. Arquivos da raiz e
metadados Git também são trazidos. O botão Download ZIP não oferece seleção de pastas.

Copie o JAR em `dist/scout/` para `vbrasfoot/mods`, com jogo fechado, e abra
`vbrasfoot-mods.cmd` do loader de autores 0.16.0+. Não use o perfil combinado para
carregar mods arbitrários. Instale também dependências declaradas no manifesto.
Preserve `sav` e `vbrasfoot/data`. Teste primeiro numa cópia descartável.

## Criar e publicar

**Com IA:** [prompt-base, skill e instalador Windows](ai/README.md). A instalação
da skill é opcional: o agente também pode ler o SKILL.md diretamente.

Leia [CONTRIBUTING.md](CONTRIBUTING.md) para SDK, branch `mod/nome`, testes e PR
com base **mods**. Cada pasta mantém seu README de funcionamento; os comandos
de build individuais antigos podem refletir o checkout do SDK: nesta branch use
`tools/build-mod.ps1`, que encontra fontes/testes e não embute a API no JAR.

`catalog.json` é o índice. Fontes SDK públicas MIT em `sdk/src` permitem build
offline com JDK17+. Não incluímos EXE, bibliotecas do jogo, JRE, saves ou JARs gerados.
Empréstimos nativos e ajuste de tela são integrações do loader, não mods separados.

Workflow `catalog`: valida PRs, compila/testa os mods. Em execução manual, informe
um mod para gerar seu ZIP individual em Artifacts. Publicação nas Releases é uma
ação posterior do mantenedor, não automática no merge. Artefatos não são assinados.
Mods Java não têm sandbox; instale apenas código confiável. SDK Beta não é estável.
