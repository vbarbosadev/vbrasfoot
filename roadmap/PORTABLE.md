# Instalacao portable experimental — 0.8.0

P0 ainda nao aprovado: primeiro use uma copia completa isolada do jogo. Os testes
automatizados de instalacao nao substituem carregar/jogar/salvar/reabrir no original.

## Gerar e instalar

Desenvolvedor (JDK 17+):

```powershell
.\release.ps1 -Output .\dist\portable-0.8.0
```

O destino deve ser novo. O pacote inclui loader, scripts, licenca MIT, hashes e
JAR publico do SDK, sem classes/recursos do jogo, saves ou Java redistribuido.
`sdk/javadoc/index.html` documenta a API; `sdk/template-read-only/` e um projeto
independente para copiar, compilar/testar e empacotar um mod (leitura/interface).
Teste reproduzivel: `test-mod-template.ps1 -Package <pacote>`, sem gameplay.
Nao inclui mods automaticamente. Copie somente JARs confiaveis para
`<jogo>\vbrasfoot\mods\` depois da instalacao.

Usuario (runtime Java 17+ no PATH; validado nesta rodada com Java 21.0.12):

```powershell
.\install.ps1 -GameDir 'C:\Jogos\Brasfoot22-23' -Preview
.\install.ps1 -GameDir 'C:\Jogos\Brasfoot22-23'
```

O instalador exige unidade local, perfil conhecido, reserva livre minima de 256 MiB
mais margem proporcional aos arquivos gerenciados para staging/geracao anterior
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
Antes de publicar arquivos, o instalador prepara fontes e a geracao anterior dos
arquivos gerenciados em `.update-pending/`, com journal e hashes. Falhas preservam
esse diretorio; launcher, nova instalacao e desinstalacao recusam o estado pendente.
Nao ha rollback automatico nem restauracao de saves.

Use o script do pacote para recuperar inclusive uma primeira instalacao incompleta:

```powershell
.\recover-update.ps1 -GameDir 'C:\Jogos\Brasfoot22-23'
.\recover-update.ps1 -GameDir 'C:\Jogos\Brasfoot22-23' -Apply
```

O preview valida allowlist, backups/hashes e o estado atual antes de propor retornar
os arquivos gerenciados a geracao anterior. Arquivo alterado fora da atualizacao
ou backup corrompido bloqueia a recuperacao; preserve tudo e revise manualmente.
Nao apague o journal para forcar a execucao. Saves/mods/dados nao sao restaurados.

Se todos os arquivos novos **e o manifesto novo** ja foram publicados, mas apenas
a limpeza foi interrompida, `-Complete` (preview) e `-Complete -Apply` mantem a
geracao nova depois de conferir hashes, removendo somente temporarios gerenciados.
Esse caminho de conclusao passou numa fixture sintetica do estado publicado com
limpeza interrompida. Falha real de substituicao/rollback, staging sem journal,
backup corrompido e journal fora da allowlist foram testados. Isso nao equivale a
simular queda de energia em cada ponto do protocolo ou a transacao de toda a versao.

```powershell
& 'C:\Jogos\Brasfoot22-23\vbrasfoot\app\uninstall.ps1'
& 'C:\Jogos\Brasfoot22-23\vbrasfoot\app\uninstall.ps1' -Apply
```

Sem `-Apply`, somente mostra o que seria removido. Com confirmacao remove somente
arquivos gerenciados com hash igual ao manifesto e o manifesto; diretorios e dados
do usuario permanecem. Nao faz remocao recursiva nem restaura saves.

## Testar o pacote

```powershell
.\test-portable.ps1 -Package .\dist\portable-0.8.0
```

Usa fixture privada apenas com EXE correto; nao representa jogo completo nem testa
gameplay. Testa instalacao/preview, reinstalacao, caminho com espacos/acento,
mudanca de local, JAR quebrado no modo seguro, arquivo alterado, lock e remocao
preservando dados. Para validar gameplay siga `P0-VALIDACAO.md`.
O teste provoca uma falha real de substituicao com arquivo bloqueado, confirma
publicacao parcial, bloqueio do launcher e rollback explicito com hash anterior.
Tambem nega escrita por ACL NTFS real na fixture e restaura a ACL em finally,
verifica recusa de backup/journal corrompidos e testa cleanup em estados sinteticos.
O preflight de espaco e exercitado com `-ReserveBytes` maior que o espaco disponivel;
nao enche o disco, nao monta volumes e nao comprova comportamento sob ENOSPC real.
`-ReserveBytes` permite aumentar a reserva, nunca reduzir o minimo de 256 MiB.
O teste tambem cria uma junction em fixture separada e exige recusa por instalador
e loader, verificando ausencia de escrita no diretorio protegido de destino.
