# vbrasfoot — SDK e loader Beta 0.16.0

Crie mods Java para a versão suportada do **Brasfoot22-23**. API major 1, JDK 17/21.
Projeto experimental: não é editor irrestrito do jogo e mods Java não têm sandbox.
Não inclui jogo, saves, bibliotecas proprietárias ou Java.

## Escolha seu caminho

| Quero… | Começar por |
|---|---|
| Baixar apenas alguns mods | [Catálogo na branch mods](https://github.com/vbarbosadev/vbrasfoot/tree/mods) |
| Criar e publicar um mod por PR | [Guia para autores](docs/CRIAR-E-PUBLICAR-MODS.md) |
| Pedir a um agente de IA para criar um mod | [Prompt, skill e instalador](ai/README.md) |
| Conhecer capacidades e limites atuais | [Contrato Beta 0.16](roadmap/SDK-BETA-016.md) |
| Desenvolver o SDK/loader | [CONTRIBUTING.md](CONTRIBUTING.md) e [estrutura](docs/ESTRUTURA.md) |

## main e mods

- **main:** fontes do loader/SDK, testes, ferramentas, templates e exemplos usados
  na validação do SDK. Não contém artefatos compilados ou arquivos pessoais do jogo.
- **mods:** catálogo com uma pasta por mod, build independente e PRs com base em `mods`.
  Para contribuir, crie `mod/<nome>` a partir dessa branch. [Fluxo completo](docs/CRIAR-E-PUBLICAR-MODS.md).

Não use o ZIP combinado de patrocínio para testar JARs arbitrários: ele seleciona só
o patrocinador. O loader para autores usa **`vbrasfoot-mods.cmd`** e plugins confiáveis
em `vbrasfoot/mods`. Downloads individuais só existem quando efetivamente publicados
nas Releases; o catálogo documenta build e sparse checkout como alternativas.

## Criar com o SDK independente

Obtenha uma distribuição SDK-only confiável: API, validador, Javadocs e templates.
Siga o [primeiro mod](roadmap/SDK-QUICKSTART.md). Não precisa do checkout ou jogo
para compilar/testar os exemplos SDK-only. Não embuta a API no JAR do mod.

Recursos públicos: consultas de clubes/jogadores, atributos, saldo/data, menus/painéis,
logos delimitadas, dados por carreira e empréstimos com cotação e confirmação.
A janela de transferências é **piloto somente para empréstimos pelo loader**;
não governa compras, agentes livres ou IA e não altera o relógio da carreira.
Patrocínio ainda tem serviço restrito ao ID canônico. Confira o contrato antes de criar.

## Desenvolver neste repositório

No Windows, com JDK 17 ou 21 e PowerShell:

```powershell
.\build.ps1
.\release-sdk.ps1 -Output .\dist\sdk-0.16.0
.\test-sdk-package.ps1 -Archive .\dist\sdk-0.16.0.zip
```

Use destino novo para cada release. Geração local não publica uma Release no GitHub.
O build contém testes sintéticos e não equivale a aceite manual no jogo.
Para jogar, use sua cópia licenciada do perfil suportado; [instalação portable](roadmap/PORTABLE.md).
Não coloque EXE/recursos em arquivos versionados para compilar o SDK.

## Segurança, saves e compatibilidade

- Execute primeiro numa cópia descartável e feche outras instâncias.
- Preserve `sav` e `vbrasfoot/data`; não apague journals para contornar bloqueios.
- Dados externos de mods não são automaticamente uma transação do save.
- Não há recuperação universal de restauração de backups ou perda de sidecars.
- Modificações com resultados incertos nunca devem ser repetidas automaticamente.
- Hash do EXE suportado: `2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.
- Não há promessa de mods ao abrir diretamente o EXE. Mensagens nativas salvas podem
  continuar visíveis sem loader, mas suas ações adicionais dependem do mod.

## Documentação e licença

[Índice](docs/README.md) · [Compatibilidade](roadmap/SDK-COMPATIBILITY.md) ·
[Validação](VALIDACAO.md) · [Gates de release](roadmap/RELEASE-GATES.md)

Código original do projeto sob [MIT](LICENSE). Essa licença não inclui jogo,
recursos proprietários ou material de terceiros. Não publique saves, credenciais,
logs privados ou imagens sem autorização. Ainda faltam aceitação externa e gates
operacionais para chamar este SDK de estável.
