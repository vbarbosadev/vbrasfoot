# Prompt-base — criar um mod vbrasfoot

Copie o bloco abaixo para seu agente. Preencha os caminhos e, se quiser, a ideia.
Se a skill estiver instalada, peça explicitamente para usar `vbrasfoot-mod-author`.

```text
Use a skill vbrasfoot-mod-author, se disponível.
Quero criar um mod para vbrasfoot (Brasfoot22-23, SDK Beta, API major 1).

SDK extraído e confiável: [CAMINHO DO SDK]
Projeto de destino: [PASTA NOVA OU PASTA DO MOD]
Ideia: [DESCREVA OU ESCREVA "SUGIRA 3 IDEIAS ALEATÓRIAS VIÁVEIS"]
Nome/ID: [OPCIONAL; ESCOLHA UM ID ÚNICO SE EU NÃO INFORMAR]
Nível de risco desejado: somente leitura e interface, salvo autorização explícita.

Antes de editar, leia ai/skills/vbrasfoot-mod-author/SKILL.md neste repositório
ou a cópia instalada. Leia o README, sdk-package.json, Javadocs e contrato atual
do SDK. Inspecione --describe-sdk do validador confiável e as assinaturas reais.

Se eu pedir algo aleatório, sugira 3 ideias que funcionem com as capacidades
verificadas, indicando dados necessários, permissões e limitações. Espere minha
escolha antes de implementar. Não prometa capacidades ausentes.

Use somente br.vbrasfoot.api. Não altere loader, executável ou saves diretamente;
não use reflexão, classes ofuscadas ou bibliotecas do jogo para contornar a API.
Não embuta a API no JAR final. Não trate IDs/índices/handles como identidade durável.
Swing na EDT; trabalho pesado e I/O fora dela. Dados externos não acompanham
automaticamente o botão Salvar do jogo.

Crie fonte, manifesto, README, licença identificada, build Java17 e testes SDK-only.
Teste fakes de ausência/troca de carreira, referências expiradas e shutdown.
Valide o JAR sem inicializar o jogo. Separe teste automatizado de teste real.
Se usar empréstimos, exiba a cotação exata e exija confirmação humana antes de
accept; nunca repita automaticamente uma tentativa incerta.

Para publicação, prepare mods/<slug>/ e catalog.json na branch base mods,
usando uma branch mod/<slug>. Não mude a branch atual com alterações pendentes,
não faça commit, push, PR ou instalação sem minha autorização.

Entregue o que foi criado, os testes executados, limitações, caminhos do JAR e
instruções para instalar somente esse mod. Não declare o SDK estável nem diga
que uma janela só de empréstimos controla compras, agentes livres ou IA.
```

O prompt não instala ferramentas nem concede permissão para executar downloads.
Prefira o SDK atual informado pelo pacote, não uma versão adivinhada pelo agente.
