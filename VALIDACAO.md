# Validacao — 16/10/2026

## Executado

- `build.ps1`: compilacao Java 17, JAR do launcher, JAR publico da API, JAR do inspetor; **24 testes unitarios aprovados**.
- `run.ps1 --validate-only`: perfil e hash corretos; entrada `best.h2`, bibliotecas e assinaturas do bridge resolvidas; mod do inspetor reconhecido sem inicializacao de seu codigo.
- `test.ps1 -IncludeUi`: integracao Swing com o JAR real do inspetor e uma janela sintetica. Menu anterior preservado, duas acoes instaladas sem duplicacao, consulta executada e tabela nao editavel com o jogador esperado.
- `run.ps1 --check-save sav\Flamengo.s22`: leitura de ambas as raizes com o Kryo embarcado, regravacao de uma copia e releitura. **8.395 clubes / 193.831 jogadores**; snapshots publicos identicos antes/depois. Nenhuma interface de jogo iniciada.
- Fixture com JAR quebrado: `--safe-mode --validate-only` ignora o arquivo e termina com sucesso; validacao normal rejeita com exit code 1.

Relatorio detalhado do save: `verification/save-14473687435780074918/report.properties`.

## Integridade dos originais

Hashes ao final, iguais aos observados antes da implementacao:

```text
bf22-23.exe
2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c

sav/Flamengo.s22 e sav/Flamengo.sbck
f9918072e724521a6ce5efaad8774db78fa2b2bd704e7acf0007f58fdcfcfcc9
```

## O que NAO foi comprovado

O round-trip **nao foi identico em bytes** (`byteIdentical=false`). A causa dessa diferenca nao foi estabelecida. A igualdade dos snapshots verifica somente os campos expostos pela API, nao todos os campos do grafo, competicoes e historicos. Portanto nao se afirma equivalencia completa da carreira a partir desse teste.

Ainda falta executar uma sessao real numa copia completa do jogo: carregar a carreira, consultar o painel, avancar, salvar pela rotina normal e reabrir pelo executavel original. O teste Swing usa uma janela sintetica, nao a janela real do jogo. Nao foi simulada uma partida nem feita gravacao pela interface original.

A arquitetura evita transformar classes ou substituir serializadores para reduzir o risco, mas o release permanece experimental ate essa validacao interativa.

## Correcao do menu apos relato do usuario

O primeiro teste Swing nao identificava a janela correta: registrava uma janela sintetica via `best.n.b(JFrame)` e o loader consultava `dc()`. O bytecode de `best.S` mostra que a janela real da carreira e registrada via `best.n.a(JFrame)`, acessivel por `db()`. O loader foi corrigido para `db()` e passou a registrar a instalacao no log. O teste de regressao usa duas janelas distintas, exige que `dc()` fique intacta e so permite a instalacao depois do registro em `db()`. A confirmacao visual na sessao real ainda deve ser feita apos reiniciar o launcher atualizado.

## Menu interno em vez de barra externa

O usuario confirmou que o menu apareceu apos a correcao de `db()`, mas numa barra externa. A integracao foi substituida por um botao na JToolBar original de `a.eg` (`ve`), antes de Opcoes (`HX`). `test.ps1 -IncludeUi` passou novamente: 24 testes unitarios, validacao do perfil e regressao Swing com botao interno, sem criar menu externo, preservando os controles originais e evitando duplicacao. A fixture representa somente o painel/barra com os mesmos membros; o posicionamento visual e a disponibilidade de espaco na tela real ainda precisam da confirmacao do usuario.

## Incremento P0 — diagnostico e investigacao (16/10/2026)

Implementado diagnostico por sessao antes da verificacao do perfil, descoberta de
mods e extracao do runtime. Inclui identidade esperada/verificada, JVM/argumentos,
etapas, stdout/stderr limitados e log rotativo separado. O hook de fechamento nao
consulta Swing nem executa callbacks de mods. Falhas anteriores a esse ponto e
erros gravados somente pelo jogo em `erros.log` permanecem fora dessa consolidacao.

Executado nesta rodada:

- `test.ps1 -IncludeUi`: **46 testes aprovados**, validacao do perfil e integracao
  Swing sintetica aprovadas. Inclui captura limitada, erros anteriores a extracao,
  restauracao de streams e rejeicao de home interno antes de criar diretorios.
- JVMs filhas encerradas por retorno, `System.exit` na thread principal e
  `System.exit` na EDT: diagnostico fechado, sem timeout. Isso testa o hook do
  loader, nao todas as rotinas de encerramento do jogo real.
- `prepare-validation.ps1 -Preview`: inventariados 34.342 arquivos, 258.044.198
  bytes. Fixture reduzida de copia aprovada com executavel correto, diretorio
  vazio, caminhos com espacos/acentos e rejeicao de destino existente. Essa fixture
  **nao e um jogo completo nem foi iniciada**.
- Preparacao da copia real excedeu os limites de 240 e 600 segundos; destinos
  parciais preservados no diretorio temporario do OpenCode. Houve tambem uma
  tentativa inicial interrompida por conflito com a variavel PowerShell HOME,
  corrigido no script. Nenhuma dessas tentativas aprova a copia real ou o P0.
- Verificacao offline do save atual:
  `verification/save-6138189412788400229/report.properties`.
  8.395 clubes / 193.831 jogadores; round-trip, repeticao das mesmas raizes e
  segunda geracao identicos em bytes. Snapshots publicos iguais nas duas geracoes.
- Reproducao com o input preservado do teste antigo:
  `verification/save-4251315688372723433/report.properties`.
  Diferenca original reproduzida, primeiro offset 8.911.369, tamanho mantido
  em 74.283.590 bytes, mesmo hash de round-trip do teste anterior. Repeticao das
  mesmas raizes e segunda geracao estaveis em bytes; snapshots publicos iguais.
  **Causa semantica da diferenca ainda nao determinada.**

Hashes conferidos antes/depois desta rodada, inalterados:

```text
bf22-23.exe
2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c

sav/Flamengo.s22 e sav/Flamengo.sbck
c645b7eaf7d5a04a60349fa98d8575092c9643515059854d55cf63ad230c19ef
```

O save atual ja diferia do hash historico antes desta implementacao. Nao houve
restauracao, gravacao de saves originais nem lancamento do jogo real nesta rodada.

P0 permanece aberto. Roteiro interativo em `roadmap/P0-VALIDACAO.md`; P1 a P6 nao
foram implementadas nem aprovadas por este incremento.

## Incremento experimental P1/P2 — 16/10/2026

- SDK/loader 0.2.0, API major 1. Exemplo compilado somente contra o JAR publico.
- `test.ps1 -IncludeUi -Save '..\Brasfoot22-23\sav\Flamengo.s22'`:
  **64 testes aprovados**, perfil e Swing sintetico aprovados; teste adicional de
  registro/remocao de acao passou. Verificador offline aprovado, inclusive todas
  as paginas de clubes e paginas de jogadores do primeiro clube humano iguais
  ao snapshot. Sem gameplay ou reabertura no EXE original.
- Relatorio: `verification/save-10258633321303238868/report.properties`.
  8.395 clubes / 193.831 jogadores; round-trip identico em bytes. EXE/save originais
  com os mesmos hashes do incremento anterior.
- A primeira consulta paginada por ID revelou ambiguidade: 8.395 clubes geraram
  apenas **um valor distinto** no getter legado historicamente chamado de ID.
  Corrigido para recusar selecao ambigua; `playersAt(club.index(), ...)` usa posicao
  na lista atual. Nao e identidade duravel e nao protege troca de carreira entre
  chamadas. Semantica completa de IDs permanece desconhecida.
- Corrigida uma corrida preexistente entre o hook do diagnostico e o hook de
  LogManager: `termination.txt` registra o motivo mesmo se handlers ja fecharam.
- Pacote criado por `release.ps1`, sem arquivos do jogo/runtime Java, contendo
  SDK, licenca e hashes. Instalador valida hashes das fontes do pacote antes de
  escrever; esses hashes nao sao assinatura de autenticidade.
- `test-portable.ps1`: instalacao/preview, validacao instalada sem referencia ao
  checkout, reinstalacao, modo seguro com JAR quebrado, arquivo gerenciado alterado,
  lock ativo, mover diretorio, preview/desinstalacao preservando dados e hash do EXE
  aprovados. Fixture privada com EXE correto, nao com todos os recursos/saves.
  Ambiente: Windows, PowerShell 5, Java 21.0.12, caminhos com espacos/acento.
  O CMD seguro tambem foi executado em `--validate-only` com sucesso.
  Pacote revisado: `dist/portable-0.2.0-reviewed/`.
  Evidencias em `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-install-e4e321db8de641108d42b9f9f36eb6ff`.
  Teste adicional: junction de home para diretorio protegido recusada tanto pelo
  instalador como pelo loader, antes de qualquer escrita no destino protegido.

Limites: substituicao atomica por arquivo, nao por release; atualizacao interrompida
exige revisao manual. Sem matriz completa de junctions/links, somente leitura, falta
de espaco, falhas de escrita e JVMs 17+. O inspetor foi atualizado nesta finalizacao
para filtro/paginacao de 100 clubes e ate 500 jogadores do clube selecionado;
nao chama mais snapshot completo no runtime atual.
O teste Swing do inspetor atualizado foi reexecutado com sucesso; sua compilacao
usa apenas o JAR publico. Os 64 testes unitarios foram reexecutados na revisao.
Nao ha ampliacao por suposicao de calendario/financas/historicos, servicos entre
mods, cancelamento de tarefas ou eventos de carreira. P0/P1/P2 nao aprovados como
fases completas. Guias: `roadmap/PORTABLE.md` e `roadmap/SDK-CONTRACT.md`.
