# vbrasfoot 0.2.0 (experimental)

Codigo e documentacao originais sob a [licenca MIT](LICENSE).
O jogo Brasfoot, seus recursos e dependencias de terceiros nao sao abrangidos
por essa concessao. Veja [como contribuir](CONTRIBUTING.md).

Modloader experimental **exclusivo do Brasfoot22-23 fornecido neste diretorio**. Usa o Java embarcado em `../Brasfoot22-23/bf22-23.exe`, sem substituir o executavel, sem importar classes do RealBrasfoot26 e sem migrar saves.

## Usar

Instalacao portable dentro da pasta do jogo: [guia](roadmap/PORTABLE.md).
Contrato publico do SDK/API major 1: [especificacao](roadmap/SDK-CONTRACT.md).
P0 e os gates reais de P1/P2 ainda nao estao aprovados.

Requer JDK 17+ com `java`, `javac` e `jar` no PATH. Nao exige Maven nem downloads.

```powershell
cd vbrasfoot
.\build.ps1
.\run.ps1 --validate-only
.\run.ps1
```

Carregue sua carreira existente pelo menu normal do jogo. Na tela do elenco, o botao **vbrasfoot** aparece na **barra interna do Brasfoot, junto a Opcoes** (a mesma barra de Agenda, Tabelas e Times), nao numa barra Swing separada no topo da janela. Clique nele e em **Consultar clubes e jogadores** para abrir o inspetor. Clique em **Atualizar** depois de carregar ou trocar de carreira. Consultas nao alteram jogadores, financas ou arquivos de save.

```powershell
.\run.ps1 --list-mods
.\run.ps1 --safe-mode
.\run.ps1 --check-save 'sav\Flamengo.s22'
```

**Importante:** `run.ps1` inicia o processo com o diretorio de trabalho real do jogo. O caminho relativo de `--check-save` e resolvido nesse diretorio; prefira caminho absoluto ou `sav\Flamengo.s22` ao usar `run.ps1`.

`--validate-only` verifica a identidade, bibliotecas, assinaturas do bridge e manifestos sem inicializar classes do jogo ou dos mods. `--safe-mode` ignora completamente os mods, inclusive JARs com manifestos invalidos, e nao instala menus. `--check-save` ignora mods e realiza uma verificacao offline em copias privadas, sem iniciar a interface do jogo.

Antes de iniciar o jogo normalmente (inclusive em modo seguro), o launcher cria uma copia verificada dos arquivos diretos de `sav/` e de `options.bcf` em `vbrasfoot/backups/session-*/`. Isso nao e um snapshot atomico: **feche qualquer outra instancia do Brasfoot antes de iniciar**. Backups nunca sao restaurados automaticamente e podem ocupar bastante espaco.

## Compatibilidade de saves

- Perfil aceito: SHA-256 `2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.
- Entrada original: `best.h2`.
- Bibliotecas originais: Kryo 4.0.2, minlog 1.3.0, objenesis 2.1 e reflectasm 1.10.1.
- Classes originais nao sao transformadas nem recebem campos/metodos adicionais.
- Save original: duas raizes Kryo, `best.f` e `best.ay`, nessa ordem; `.sbck` e o backup legado.
- O loader nao intercepta o carregamento nem a gravacao do jogo. O jogo continua gravando normalmente quando voce manda salvar.
- Dados dos mods ficam em `vbrasfoot/data/<id>/`, fora de `sav/`. Nesta versao sao dados globais do mod, **nao associados automaticamente a uma carreira**.

O verificador copia o save para `verification/save-*/input.s22`, decodifica ambas as raizes, regrava somente `roundtrip.s22`, decodifica novamente e compara os snapshots de clubes/jogadores. O relatorio inclui os hashes do original antes/depois e se o round-trip foi identico em bytes. Ele **nao substitui** o teste de jogar, salvar e reabrir uma copia no executavel original.

O cache e reconstruido a partir do executavel validado a cada execucao. Nao sao aceitas versoes diferentes por tentativa de compatibilidade.

## Criar mods

Compile contra `dist/vbrasfoot-api.jar`, implemente `br.vbrasfoot.api.Mod` e coloque `vbrasfoot.mod.properties` na raiz do JAR:

```properties
id=br.exemplo.meumod
name=Meu mod
version=1.0.0
apiVersion=1
gameProfile=brasfoot22-23
mainClass=br.exemplo.MeuMod
dependencies=
permissions=game.read,ui.register
```

Copie para `vbrasfoot/mods/`. O exemplo completo esta em `examples/inspector/` e e instalado pelo build. Dependencias sao IDs separados por virgula; ordenam inicializacao, mas nao compartilham classes privadas entre mods. Dependencias ausentes, ciclos e IDs duplicados impedem a inicializacao.

Permissoes da API: `game.read`, `ui.register`, `data.read`, `data.write`. Nao existe permissao para escrita na carreira nesta versao. Leitura retorna records imutaveis, sem objetos legados. Acoes de interface rodam na thread Swing; nao faca trabalho demorado nelas.

**Mods Java sao codigo confiavel, nao uma sandbox.** Permissoes restringem os servicos da API, mas um JAR arbitrario ainda pode acessar arquivos/reflexao por conta propria. Nao instale mods de origem desconhecida. JARs antigos de `realbrasfoot-mods` nao sao compativeis automaticamente.

## Limites e validacao pendente

Nao foram portadas trocas, contrapropostas ou selecao de leiloes: dependem de regras adicionadas ao RealBrasfoot26 que nao existem no original. O primeiro release oferece infraestrutura e consulta somente de leitura.

Antes de usar numa carreira importante, teste numa copia completa do diretorio do jogo: abrir save existente, consultar, avancar, salvar, fechar e reabrir pelo `bf22-23.exe` original. Nao basta o launcher abrir ou o verificador offline passar para afirmar compatibilidade total em gameplay.

Veja `ANALISE.md` para o mapeamento e a origem das evidencias.

## Validacao isolada e diagnostico

`prepare-validation.ps1 -Preview` mostra a origem, destino e volume de uma copia
isolada. Sem `-Preview`, cria um destino novo, verifica hashes e prepara launchers
independentes do checkout. Nao abre o jogo nem restaura saves. A copia completa
tem milhares de arquivos e pode demorar; somente a presenca de `baseline.json`
apos o sucesso da preparacao identifica uma copia concluida. Destinos parciais
nao sao apagados nem reutilizados automaticamente.

Siga `roadmap/P0-VALIDACAO.md` para o teste real. Cada execucao do launcher registra
metadados da JVM, etapas e stdout/stderr em `logs/session-*/`. Capturas por stream
tem limite de 8 MiB e o log do loader tem rotacao; sessoes antigas sao preservadas.
Revise caminhos e argumentos antes de compartilhar. Falhas anteriores ao launcher
e erros gravados exclusivamente no arquivo legado `erros.log` nao sao consolidados.

Investigacao reproduzivel da divergencia binaria antiga (somente copias privadas):
[resultado e limites](roadmap/SAVE-ANALYSIS.md), ferramenta `analyze-save.ps1`.

## Testes

GitHub Actions executa `build.ps1` e validacao de sintaxe PowerShell no Windows
com Temurin 17/21, sem arquivos do jogo. CI sintetico nao aprova os gates reais.

`build.ps1` executa os testes unitarios sem interface. Para incluir a integracao Swing (janela sintetica + JAR real do inspetor) e o round-trip de uma copia do save:

```powershell
.\test.ps1 -IncludeUi -Save '..\Brasfoot22-23\sav\Flamengo.s22'
```

O teste Swing nao inicia a partida nem o launcher original e nao demonstra gameplay. O verificador offline inicializa somente os recursos de idioma exigidos pelos construtores durante a desserializacao.
