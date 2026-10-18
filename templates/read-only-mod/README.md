# Meu primeiro mod — template de leitura/interface

Requer Windows/PowerShell, JDK 17+ para compilar, SDK/loader 0.3.1+ e copia local
do Brasfoot22-23 suportado para jogar. Nao precisa do checkout do loader, Maven,
classes do jogo ou downloads durante o build. Mod Java e codigo confiavel, nao sandbox.

## Criar

1. Copie esta pasta inteira para um diretorio novo do seu projeto.
2. Obtenha `api/vbrasfoot-api-0.7.2.jar` no SDK-only (ou `sdk/vbrasfoot-api-0.7.2.jar`
   no pacote portable do loader).
3. Compile/teste usando somente esse JAR:

   ```powershell
   .\build.ps1 -Sdk 'C:\Downloads\vbrasfoot-sdk\api\vbrasfoot-api-0.7.2.jar'
   ```

   Alternativa: coloque a API em `lib/vbrasfoot-api.jar` e execute `build.ps1`.
   O output e `dist/meu-mod.jar`; classes de teste nao entram nele.

4. Personalize `id`, `name`, `version` no manifesto. Ao mudar o pacote/classe,
   atualize `mainClass`, o runner de teste em build.ps1 e o namespace das fontes.
   Ao mudar o ID, ajuste tambem o nome do JAR de destino em install.ps1.
5. Declare somente capacidades/permissoes utilizadas. Este exemplo usa
   `game.read` e `ui.register`; nao escreve dados nem gameplay.

## Validar e instalar

```powershell
java -jar 'C:\Jogos\Brasfoot22-23\vbrasfoot\app\vbrasfoot.jar' --validate-mod .\dist\meu-mod.jar
.\install.ps1 -GameDir 'C:\Jogos\Brasfoot22-23' -Preview
.\install.ps1 -GameDir 'C:\Jogos\Brasfoot22-23'
```

O validador nao inicializa o mod nem o jogo. Verifica manifesto/compatibilidade e
classe/construtor, nao prova seguranca, dependencias instaladas ou comportamento.
Depois valide o conjunto de mods pelo launcher `--validate-only`.

Sem loader/jogo, o SDK-only tambem oferece:

```powershell
java -jar 'C:\Downloads\vbrasfoot-sdk\tools\vbrasfoot-validator-0.7.2.jar' .\dist\meu-mod.jar
```

Instalacao/UI ainda precisam do loader portable e copia do jogo. Nao embuta API
ou validador no seu JAR. Consulte o guia SDK-QUICKSTART.md distribuido nos docs.

O instalador exige loader instalado, recusa JAR existente/links, valida o JAR e
usa lock para nao instalar com sessao do loader aberta. Nao atualiza/apaga mods
automaticamente. Feche o jogo normalmente antes de instalar; arquivos gerados
ficam privados. Para atualizar/remover, revise e opere somente seu proprio JAR
com o jogo fechado. Nunca apague saves ou toda a pasta mods.

Abra `vbrasfoot.cmd`, carregue sua carreira e escolha **vbrasfoot → Exemplo:
consultar clubes**. O exemplo mostra ate 50 clubes e ate 20 jogadores de um clube
da pagina; nao representa todos os clubes humanos quando estao fora da pagina.

## Contrato e limites

Acao roda na EDT; consultas sao limitadas e com handles de escopo. Handle obsoleto
produz mensagem para consultar novamente, nao retry infinito. `shutdown()` fecha
o registro; System.exit pode nao chamar shutdown de mods. Sem threads persistentes.

Escopo e identidade da consulta na sessao, **nao** identidade permanente de carreira.
Nao persistir ClubRef/CareerScope em arquivos. Paginas nao sao snapshot atomico.
Nao ha eventos publicos de partida/temporada, dados por carreira, escrita de gameplay
ou hot reload neste template. Esses recursos dependem dos gates ainda abertos.

Guias: `docs/SCOPED-QUERIES.md` e `docs/SDK-CONTRACT.md` no SDK-only; `sdk/` no portable.
