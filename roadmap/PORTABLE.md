# Instalacao portable experimental — 0.2.0

P0 ainda nao aprovado: primeiro use uma copia completa isolada do jogo. Os testes
automatizados de instalacao nao substituem carregar/jogar/salvar/reabrir no original.

## Gerar e instalar

Desenvolvedor (JDK 17+):

```powershell
.\release.ps1 -Output .\dist\portable-0.2.0
```

O destino deve ser novo. O pacote inclui loader, scripts, licenca MIT, hashes e
JAR publico do SDK, sem classes/recursos do jogo, saves ou Java redistribuido.
Nao inclui mods automaticamente. Copie somente JARs confiaveis para
`<jogo>\vbrasfoot\mods\` depois da instalacao.

Usuario (runtime Java 17+ no PATH; validado nesta rodada com Java 21.0.12):

```powershell
.\install.ps1 -GameDir 'C:\Jogos\Brasfoot22-23' -Preview
.\install.ps1 -GameDir 'C:\Jogos\Brasfoot22-23'
```

O instalador exige unidade local, perfil conhecido, espaco livre minimo de 256 MiB
e permissao de escrita (falha sem elevar privilegios). Nao baixa nem instala Java.
JDK e exigido somente para desenvolvimento; nao se afirma teste em todas as JVMs
17+. Resolucao e validacao dos caminhos recusam links/junctions identificados.
Essas verificacoes nao sao uma sandbox contra um processo malicioso trocando
caminhos concorrentemente; feche outras sessoes e nao instale em arvore nao confiavel.

Abra `vbrasfoot.cmd` na pasta do jogo. `vbrasfoot-safe.cmd` ignora mods, inclusive
JARs quebrados. Os dois usam o mesmo `app/launch.ps1`, com cwd real do jogo e
argumentos JVM centralizados. `bf22-23.exe` continua original e sem mods.

Somente `gameRoot/vbrasfoot` e aceito como home interno; homes externos de
desenvolvimento continuam aceitos desde que nao contenham a pasta do jogo.
Configuracoes/dados/cache/logs/backups sao separados de `app/`, os arquivos
gerenciados ficam em `install-manifest.json`.

## Atualizar ou remover

Execute o instalador do novo pacote no mesmo GameDir. Ele altera somente a lista
fixa de arquivos gerenciados e recusa arquivos existentes nao gerenciados ou com
hash alterado. Feche o jogo primeiro: o instalador nao encerra processos e bloqueia
locks ativos. A verificacao de processos e conservadora (pode bloquear por outra
instalacao do jogo/loader aberta).

As substituicoes sao atomicas **por arquivo**, nao uma transacao de toda a versao.
Falhas/interrupcoes podem deixar instalacao parcial. Nao ha rollback automatico:
preserve o diretorio e revise arquivos/manifesto antes de recuperar; nao apague
dados ou saves para forcar reinstalacao. A recuperacao de atualizacao interrompida
ainda e gate pendente de release.

```powershell
& 'C:\Jogos\Brasfoot22-23\vbrasfoot\app\uninstall.ps1'
& 'C:\Jogos\Brasfoot22-23\vbrasfoot\app\uninstall.ps1' -Apply
```

Sem `-Apply`, somente mostra o que seria removido. Com confirmacao remove somente
arquivos gerenciados com hash igual ao manifesto e o manifesto; diretorios e dados
do usuario permanecem. Nao faz remocao recursiva nem restaura saves.

## Testar o pacote

```powershell
.\test-portable.ps1 -Package .\dist\portable-0.2.0
```

Usa fixture privada apenas com EXE correto; nao representa jogo completo nem testa
gameplay. Testa instalacao/preview, reinstalacao, caminho com espacos/acento,
mudanca de local, JAR quebrado no modo seguro, arquivo alterado, lock e remocao
preservando dados. Para validar gameplay siga `P0-VALIDACAO.md`.
O teste tambem cria uma junction em fixture separada e exige recusa por instalador
e loader, verificando ausencia de escrita no diretorio protegido de destino.
