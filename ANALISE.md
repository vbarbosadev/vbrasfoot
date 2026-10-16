# Perfil Brasfoot22-23

## Observacoes estaticas

Arquivo: `../Brasfoot22-23/bf22-23.exe`, 7.320.384 bytes, PE32 x86.

SHA-256: `2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.

REA `open_binary` confirmou identidade/formato. `inspect_pe_resources` inventariou 16 recursos PE, Evidence ID `ev_be6f2b7e5edacef1c6fc9b6690e87d930d572b9b495fb197e2a6a49532ca93bb`. `inspect_artifact` teve cobertura somente da raiz PE: nao encontrou os membros do ZIP anexado e nao demonstra ausencia de Java. Evidencia pai: `ev_f58bce68213ed6e2cb2c7c5d9476d5e3ff5ac1dbb5713012d28c908c4158375f`.

Inspecao complementar com `jar`, `javap` e leitura do ZIP em memoria: 2.193 entradas, 1.038 classes, prefixo PE de 125.440 bytes. Manifesto Eclipse Jar-in-Jar com `Rsrc-Main-Class: best.h2`. Nao foi necessario executar o programa para obter esses fatos.

| Funcao | Classe/acesso observado |
|---|---|
| Entrada | `best.h2.main(String[])` |
| Janela da carreira | `best.n.db()`; `best.S` registra seu JFrame por `best.n.a(JFrame)` |
| Estado de carreira | `c.a.SR : best.f` |
| Estado auxiliar | `c.a.afQ : best.ay` |
| Clubes | `best.f.P()` |
| Clubes humanos | `best.f.aN()` |
| Clube | `best.ah`: `gD()`, `getNome()`, `kc()` |
| Jogador | `best.F`: `gD()`, `getNome()`, `getIdade()`, `getPosicao()` |
| Forca/valor | `best.F.fi()` / `fk()`; bytecode mostra leitura simples de campos int |
| Gravacao/leitura | `c.a`, Kryo `writeClassAndObject` / `readClassAndObject` |

O bytecode de `c.a` configura `setRegistrationRequired(false)`, grava `SR` e depois `afQ`. A leitura realiza a mesma sequencia e aceita `.s22` ou `.sbck`. O campo SR pode ser nao nulo antes de uma carreira estar carregada; o bridge exige tambem listas de clubes/humanos nao vazias.

Correcao apos teste real: `best.n.dc()` retorna outro campo (`cD`), nao o JFrame da carreira (`cC`). A primeira implementacao usava esse acesso incorreto, e o teste sintetico reproduzia a mesma suposicao. O menu agora usa `db()`. O teste registra janelas distintas via `a(JFrame)` e `b(JFrame)` e verifica que somente a primeira recebe o menu.

Integracao visual: a tela do elenco e `a.eg`. Seu campo `ve` e a `JToolBar` que contem Agenda, Tabelas, Times e o botao Opcoes (`HX`). O loader procura esse painel na arvore visivel da janela da carreira e insere um JButton com popup antes de `HX`, copiando o estilo desse botao. Nao cria uma JMenuBar externa nem altera o layout do painel. Aguarda a tela correta e repete a instalacao quando o jogo recria o painel. A identificacao foi obtida pelo bytecode; o teste usa uma fixture sintetica com os mesmos nomes/tipos, e nao instancia uma carreira real.

## Referencias ao RealBrasfoot26

O manifesto do RealBF26 aponta para `launcher.BfLauncher`. O kit existente fixa essa entrada em `launcher/.../LegacyGameRunner.java`. `mod-loader/.../LegacyTransferBridge.java` depende de `components.TransferWindowRules`, presente no RealBF26 e ausente do inventario 22-23. Por isso o novo bridge e especifico e somente de leitura.

Conceitos reaproveitados do kit local: launcher separado, classloader legado, manifestos/dependencias, interface Swing e dados externos. Nenhum JAR/classe do RealBF26 e adicionado ao runtime do 22-23.

## Limites

Ghidra nao estava configurado no servidor REA; nao houve decompilacao nativa profunda. O inventario PE nao estabelece comportamento Java. A assinatura dos getters nao comprova toda a semantica do jogo. Os resultados dos testes offline ficam em `verification/`; teste interativo de salvar/reabrir no executavel original deve ser registrado separadamente.
