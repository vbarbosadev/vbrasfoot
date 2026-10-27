# Preparar a branch local mods

Execute `New-ModsBranch.ps1` num repositório sem branch local `mods` existente.
O script usa um índice Git temporário e allowlist de fontes/API; NÃO troca a branch
atual, NÃO mexe no índice do usuário e NÃO commita o trabalho pendente do loader.
Cria um commit de catálogo com histórico ligado ao HEAD atual, e a ref local `mods`.

Depois revise: `git ls-tree -r --name-only mods` e `git show mods:README.md`.
O script não faz push. Só após revisão e autorização: `git push origin mods`.
Para atualizar um catálogo já usado, faça uma PR na própria branch; não recrie a ref.
`-UpdateExisting` é destinado somente à preparação local inicial: gera um commit
descendente do anterior, sem reescrever histórico, e compara a ref antes de atualizar.
Não o use para substituir contribuições remotas por um snapshot antigo de exemplos.
