# Patrocínios 0.6.0 — Central de Mensagens (loader 0.14.0)

### Atualização loader 0.15.0 — mensagem nativa salva

Novas publicações agora criam uma mensagem nativa em memória no coach. Após salvar
pelo jogo, a central original a mostra mesmo sem mods: assunto com prefixo nativo
`Agradecimento — Patrocinio`, texto personalizado e estado informativo. Nenhuma alteração
do EXE é necessária. Receber continua exclusivo do mod e atualiza texto/dinheiro na mesma
etapa protegida. É necessário salvar depois de receber.

O mod reutiliza a linha nativa, sem duplicar; ofertas externas sem mensagem no save carregado
não são reinseridas nem podem ser recebidas. Fechar sem salvar descarta a mensagem nova,
mas NÃO apaga os contratos/ledgers externos nem libera pagamento repetido. Ofertas antigas
não são migradas silenciosamente. Esta seção substitui a descrição de projeção sem save abaixo.

## Fluxo atual

- Logo opcional; `Sem logo` limpa a imagem escolhida.
- Texto editável antes de salvar, até 2000 caracteres, com `{patrocinador}`, `{clube}` e `{valor}`.
- **Ativar e enviar mensagem** grava uma oferta pendente, sem dinheiro ou mudança de estádio.
- Feche o painel e abra a Central de Mensagens original, pelo balão ao lado do saldo.
  Selecione `Patrocinio: NOME` e clique **Receber patrocínio** à direita. Revise e confirme.
  Só então o crédito único e o naming rights opcionais são aplicados. Salve pelo jogo.
- A oferta é projetada na cópia privada da lista usada pela tela original, sem inserir novos
  tipos no save nem apagar mensagens nativas. O contador original do balão não inclui a projeção.
- As mensagens e cancelamentos persistem em `vbrasfoot/data/.career-state/sponsor-offers`.
  O ledger financeiro antigo continua sendo a autoridade para pagamentos: não há replay.
- Encerrar antes de receber cancela a oferta. Depois de receber, recarregue os dados do painel
  e use o encerramento original para restaurar o estádio sem tirar dinheiro.
- Configurações v1 são lidas; novas gravações usam v2 para texto e imagem opcional.
  Contratos antigos já pagos não são transformados em ofertas a receber.

## Referência do fluxo legado 0.5.x (crédito imediato)

As seções abaixo descrevem a implementação anterior. No painel 0.6.0, o recebimento
foi transferido para a Central de Mensagens conforme o fluxo acima; o crédito/encerramento
protegidos do loader continuam preservados.

Esta versão conecta o crédito único e o nome opcional do estádio aos métodos originais do jogo. Configurar ou importar um patrocinador **não paga nada**: a ativação exige uma cotação e uma confirmação separada de clube, crédito, saldo antes/depois e estádio.

## Encerrar patrocínio

Selecione um contrato com **CRÉDITO REGISTRADO**, clique **Encerrar patrocínio**
e confira o nome atual e o nome original registrados na confirmação.
O encerramento restaura o nome anterior à ativação, retira a logo do topo e
mantém o dinheiro recebido e toda a contabilidade. Não há desconto ou novo crédito.
Se o estádio foi renomeado depois, a confirmação avisa antes de sobrescrever.
Capacidade e preços não mudam. Depois, **salve pelo jogo** para persistir o nome.

O contrato fica **ENCERRADO**, com ativação e tentativa de encerramento preservadas.
Não pode ser ativado ou encerrado novamente. Encerramento incerto também não é
repetido automaticamente. Se fechar sem salvar ou restaurar um save anterior,
o histórico pode dizer encerrado sem o nome restaurado nesse save: preserve os
metadados para revisão, não apague o histórico. Ativações antigas sem nome original
registrado não recebem um nome inventado. No loader 0.13.1, encerrar com sucesso
permite ativar um NOVO contrato de UUID distinto, sem repetir o crédito antigo.
Contrato ativo ou tentativa incerta continua bloqueando o próximo patrocinador.
Isso não reconcilia empréstimos/históricos financeiros pendentes nem comprova que
créditos antigos estejam incluídos no save atual após restauração de backup.

### Pasta portátil — loader 0.12.1+

Os novos vínculos usam caminhos relativos à instalação atual. Para levar o jogo
a outra pasta, unidade ou PC Windows, copie **a pasta inteira**, incluindo `sav`
e `vbrasfoot/data`, e abra `vbrasfoot-nitido.cmd` na pasta de destino (Java 17+).
IDs, configurações e históricos acompanham a instalação; não há dependência do
nome do usuário, letra da unidade ou localização antiga.

Instalações antigas com caminhos absolutos pedem uma confirmação de pasta
movida/copiada no vínculo. Essa migração preserva todos os registros originais,
incluindo aliases e tentativas de pagamento. Não escolhe carreira por hash/nome
nem cria uma associação vazia para contornar histórico. Raízes misturadas ou
metadados incompletos continuam exigindo revisão. Copiar apenas o save não
transfere os históricos e não recebe garantia de recuperação ou de replay seguro.

**Validação atual:** métodos originais, preservação da capacidade e persistência após salvar/recarregar foram testados numa cópia descartável. Esse teste usou autoridade artificial; a aceitação completa pela interface e pelo coordenador operacional ainda está pendente. Não confundir esse resultado com garantia universal de pagamento exatamente uma vez ou inclusão em qualquer save.

## O que já funciona

- Ação **Patrocínios (protótipo)** no menu do vbrasfoot, com painel integrado.
- Identificação automática do único clube humano/controlado da carreira; cadastro é bloqueado se não houver exatamente um. O ID nativo `-1` (indisponível) é aceito e combinado com o nome exato do clube para filtrar a configuração.
- Consulta paginada em grupos de 500 clubes, limitada a 10.000 e validada contra mudanças/inconsistências durante a leitura.
- Cadastro de patrocinadores por carreira e clube humano exato; valor-base para ativação única confirmada.
- Importação local PNG/JPEG, normalização para PNG e armazenamento da imagem junto aos dados do mod.
- Lista, prévia das logos dentro do painel, persistência após reiniciar e arquivamento de configurações.
- Seleção explícita de uma configuração para exibir sua logo no topo da tela do clube. A faixa é uma **prévia visual**: não significa contrato ativo nem pagamento.
- Importação explícita de configurações antigas do armazenamento global para uma carreira; os dados de origem são preservados e nada é ativado.

## Limites importantes desta versão

Os itens novos aparecem como **CONFIGURADO — não pago**. O botão **Ativar e creditar valor base** exige uma carreira vinculada e um contrato selecionado ainda não tentado. Recorrência e bônus por partida continuam desabilitados. O nome opcional do estádio muda sem reconstruir o estádio ou alterar sua capacidade. A logo usa uma área fixa de 128×40 após a data, com proporção preservada, e continua sendo apenas visual.

Uma tentativa durável é registrada **antes** de alterar o jogo. Histórico aplicado/incerto bloqueia outro patrocínio no mesmo binding e também pode bloquear empréstimos/escritas genéricas até reconciliação. A versão é conservadora: não oferece renovação automática nem restaura o nome anterior ao arquivar. Uma tentativa retida não pode ser apagada pelo painel para permitir novo pagamento.

**Após aplicar, salve pelo comando original do jogo.** Se fechar sem salvar ou restaurar um backup, o histórico não repete o crédito para compensar. `CRÉDITO REGISTRADO` significa tentativa histórica aplicada em memória, não prova de que o save atualmente carregado contém o efeito. Cópias sem sidecars, sidecars antigos/restaurados e histórico removido não têm recuperação universal; preserve o save e os metadados e não tente novamente após resultado incerto.

As configurações usam `ModContext.careerData()` sob `career UUID / binding UUID / mod ID`. A ativação usa o serviço restrito `ModContext.sponsors()`, com permissão `sponsor.activate`, capability `game.sponsor.activate` e `--probe-career-hooks`. Use **Vincular esta carreira...** no próprio painel; associações ambíguas exigem revisão em **Carreira e diários**. Uma nova revisão exige confirmação, preservando o histórico. Os arquivos globais antigos não são usados automaticamente; importação nunca ativa pagamentos.

O loader altera saldo/contabilidade e nome do estádio em memória somente após ativação confirmada. O próprio jogo grava o save. Configurações e tentativas ficam em sidecars sob `vbrasfoot/data/.career-state`; preserve essa pasta junto dos saves. Calendário e elencos não são alterados pelo patrocínio.

## Telas e campos

### Novo patrocinador

- **Clube fixo da carreira:** preenchido automaticamente; não existe seletor de outros clubes. **Atualizar meu clube** refaz a identificação. Os campos ficam bloqueados até haver exatamente um clube controlado e a leitura inicial terminar.
- **Nome do patrocinador:** obrigatório, até 100 caracteres.
- **Valor base:** inteiro positivo até 2.147.483.647 em unidades do jogo; salvar a configuração não credita esse valor.
- **Alterar nome do estádio ao ativar:** opcional, até 120 caracteres.
- **Arquivo de logo:** obrigatório para salvar; escolha PNG/JPEG local. O mod incorpora uma cópia e mostra uma prévia.
- **Salvar configuração:** pede confirmação e grava somente os dados próprios do mod.

### Configurações e logos

A tabela lista patrocinador, valor-base e estado. **Arquivar selecionada** só está disponível antes de qualquer tentativa. **Recarregar dados** relê a carreira selecionada. **Exibir selecionada no topo** persiste uma escolha visual. **Ativar e creditar valor base** prepara uma cotação sem efeitos e pede confirmação explícita; a cotação é descartada se o save, seleção, saldo ou estádio mudar. Após sucesso, a logo selecionada é destacada e é necessário salvar pelo jogo.

## Compilar e validar

Gere um SDK local (isso não publica nada) e use o JAR API versionado:

```powershell
.\release-sdk.ps1 -Output .\dist\sdk-sponsor-test
.\examples\sponsor-manager\build.ps1 -Sdk .\dist\sdk-sponsor-test\api\vbrasfoot-api-0.12.0.jar
java -jar .\Brasfoot22-23\vbrasfoot\app\vbrasfoot.jar --validate-mod .\examples\sponsor-manager\dist\sponsor-manager.jar
```

O build roda testes de codec/modelo/logo e compila com Java 17, sem importar classes ofuscadas do jogo. O validador verifica o manifesto e a classe principal, mas não é sandbox nem valida a integração visual real.

## Teste manual em uma cópia do jogo

1. Faça uma cópia da pasta do Brasfoot e dos saves; mantenha o jogo fechado.
2. Compile e valide o JAR conforme acima.
3. Instale somente na cópia de teste: `.\examples\sponsor-manager\install.ps1 -GameDir 'C:\caminho\da\copia\Brasfoot22-23'`.
4. Abra a cópia com `vbrasfoot-nitido.cmd` (modo probe) e carregue um save existente.
5. Abra **Patrocínios** e use **Vincular esta carreira...**; confirme a associação exata. Resolva ambiguidades em **Carreira e diários**, sem criar IDs para contornar histórico.
6. Cadastre valor-base, logo e nome opcional do estádio; salve a configuração. Confirme que isso ainda não mudou saldo/estádio.
7. Selecione a configuração, clique **Ativar e creditar valor base** e confira a cotação antes de confirmar. Verifique crédito, nome, capacidade preservada e logo após a data.
8. Salve pelo jogo, feche/reabra a cópia e confira saldo/estádio e bloqueio de repetição. Não apague metadados nem repita a operação caso haja incerteza.

Para atualizar uma instalação, feche primeiro o jogo e use `install.ps1 -Update`; o script guarda uma cópia do JAR anterior e não altera os dados do mod. Para remover, feche o jogo e remova manualmente apenas `vbrasfoot/mods/br.vbrasfoot.sponsor-manager.jar`; preservar/limpar os dados é escolha explícita do usuário.

## Validação ainda pendente

Falta aceitar o caminho completo da interface/coordenador com carga original operacional. O teste descartável de métodos originais não substitui essa aceitação. Recuperação geral de cópia/restore sem histórico permanece fora do escopo, assim como mensalidades e bônus por vitória.
