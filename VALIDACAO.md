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
