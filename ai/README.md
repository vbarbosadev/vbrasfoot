# Criar mods com agentes de IA

- [Prompt-base para copiar e preencher](prompts/criar-mod.md)
- [Skill vbrasfoot-mod-author](skills/vbrasfoot-mod-author/SKILL.md)

## Instalação fácil no Windows

Com o repositório já baixado e revisado, dê dois cliques em **`install-skill.cmd`**
nesta pasta. Instala somente a skill em:

`%USERPROFILE%\.agents\skills\vbrasfoot-mod-author\SKILL.md`

Nenhum download, jogo, chave, configuração do agente ou instalação de Java é feito.
Se já existir uma instalação, o comando padrão recusa sobrescrever.

Para substituir, revisando antes (guarda backup):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ai/install-skill.ps1 -Replace
```

Para instalar só no projeto de mod, execute com caminho explícito:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ai/install-skill.ps1 -ProjectRoot 'C:\Mods\meu-mod'
```

O destino será `C:\Mods\meu-mod\.agents\skills\vbrasfoot-mod-author`.
O projeto deve existir; o instalador não cria um projeto nem muda de diretório Git.

## Usar

Reabra a sessão do agente, se necessário, e peça:

> Use a skill vbrasfoot-mod-author. SDK em C:\SDK\VBrasfoot-SDK-Beta-0.16.0.
> Sugira três mods aleatórios somente leitura que eu possa criar com esta API.

A descoberta de `.agents/skills` depende do agente/versão. Se ela não aparecer,
peça ao agente para **ler diretamente o caminho do SKILL.md** e forneça o prompt-base.
Isso funciona como instrução explícita, sem depender de instalação automática.
Não é garantida compatibilidade de instalação com todos os clientes.

Sem clonar: no GitHub, abra o SKILL.md, use Raw/Download e salve manualmente no destino
acima. Não precisa executar código baixado. A skill não substitui o SDK: obtenha
separadamente API/validador/documentação por canal confiável.

Desinstalar: remova apenas a pasta instalada `vbrasfoot-mod-author`; mantenha seus
projetos, mods, saves e eventuais backups. A instalação não é executada pelo agente
que preparou estes arquivos, nem muda configuração global por conta própria.
