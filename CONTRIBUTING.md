# Criar, testar e publicar mods

## Criar

1. Use JDK 17 ou 21 no Windows. Obtenha o SDK Beta por canal confiável e execute `verify.ps1`.
2. Copie `template-read-only` para seu projeto, escolha ID único e altere manifesto/classe.
3. Consulte [SDK-BETA-016](https://github.com/vbarbosadev/vbrasfoot/blob/main/roadmap/SDK-BETA-016.md) e Javadocs do pacote SDK. Use somente API pública.
4. Compile contra `vbrasfoot-api-0.16.0.jar` e valide com o JAR validator do SDK.
5. Teste numa cópia do jogo com `vbrasfoot-mods.cmd`; nunca distribua jogo ou saves.

## Publicar no catálogo

Faça fork se não tiver acesso de escrita. Num clone limpo:

```powershell
git clone --branch mods https://github.com/SEU-USUARIO/vbrasfoot.git
cd vbrasfoot
git remote add upstream https://github.com/vbarbosadev/vbrasfoot.git
git fetch upstream mods
git switch -c mod/meu-mod upstream/mods
```

Adicione `mods/meu-mod/` com `src`, `test` opcional, `vbrasfoot.mod.properties`,
`README.md` e licença do código/recursos próprios. Atualize `catalog.json`.
No README descreva instalação, permissões, versão mínima, dependências, limitações,
teste realizado, licença das logos e como remover sem perder dados.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build-mod.ps1 -Mod meu-mod
git add mods/meu-mod catalog.json
git diff --cached --stat
git commit -m "feat(mod): adiciona meu-mod"
git push -u origin mod/meu-mod
```

No GitHub abra PR: **base `vbarbosadev/vbrasfoot:mods`**, compare sua branch
`mod/meu-mod`. Também pode usar `gh pr create --repo vbarbosadev/vbrasfoot --base mods`.
Correções seguem `mod/nome-descricao` e o mesmo processo. Não faça merge em `main`.
O mantenedor revisa código, licenças, permissões e testes antes de aprovar.

Proteção recomendada no GitHub: exigir PR, uma aprovação e check `catalog` para
`mods`; bloquear force-push/deleção. Arquivos locais não configuram essa proteção.

## Baixar só os mods escolhidos

**Para jogar:** use ZIP individual publicado pelo mantenedor nas Releases. Não há
release presumida: enquanto não publicada, compile apenas os mods desejados.
A automação da branch permite gerar um ZIP de UM mod por execução; o artefato de
Actions não é uma Release assinada e exige acesso conforme as regras do GitHub.

**Para código de 5 entre 10 mods**, use sparse checkout com clone parcial:

```powershell
git clone --filter=blob:none --sparse --branch mods https://github.com/vbarbosadev/vbrasfoot.git vbrasfoot-mods
cd vbrasfoot-mods
git sparse-checkout set mods/patrocinio mods/scout mods/comparador mods/painel-carreira mods/observador sdk tools
```

Isso traz as cinco pastas de mods, API e ferramentas necessárias para compilar;
arquivos da raiz podem continuar aparecendo por funcionamento do modo cone.
Requer suporte a clone parcial no servidor; não promete zero histórico/metadados.
Troque a lista por nomes existentes em `catalog.json`. Para acrescentar um:
`git sparse-checkout add mods/janela-emprestimos`.

Compile cada escolhido com `tools/build-mod.ps1 -Mod NOME`, copie só os JARs em
`dist/NOME/` para `vbrasfoot/mods` com o jogo fechado e use o loader de autores.
Se um mod declarar dependência, instale-a também. Não baixe o ZIP da branch inteira
esperando uma seleção parcial; o botão Download ZIP baixa a árvore inteira.

**Limites:** patrocínio ainda tem ID reservado; empréstimos/tela internos do loader
não são JARs independentes. Janela exemplo controla empréstimos, não compras/IA.
