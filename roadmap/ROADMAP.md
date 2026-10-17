# Roadmap — vbrasfoot como plataforma de mods do Brasfoot22-23

Data: 16/10/2026  
Estado: planejamento; os itens abaixo nao estao implementados, exceto quando identificados como existentes.  
Documento do projeto: `vbrasfoot/roadmap/ROADMAP.md`.

## 1. Objetivo e definicao de pronto

Entregar um modloader instalavel **dentro da pasta original do Brasfoot22-23**, independente do checkout de desenvolvimento, com ferramentas e contratos suficientes para terceiros criarem mods sem conhecer classes ofuscadas.

O usuario deve conseguir:

1. Instalar o vbrasfoot na pasta que ja contem `bf22-23.exe`.
2. Abrir o jogo por um launcher vbrasfoot nessa mesma pasta.
3. Continuar uma carreira `.s22` existente, sem migracao ou necessidade de criar novo save.
4. Usar o menu interno para consultar mods, estado de carregamento e diagnosticos.
5. Instalar um mod compativel copiando seu JAR, sem recompilar o jogo ou o loader.
6. Voltar ao executavel original sem precisar desfazer patches no jogo.
7. Desenvolver mods usando um SDK documentado, exemplos e testes reproduziveis.

**Dois marcos de prontidao:**

- **MVP para criar mods de leitura/interface/eventos:** fases P0 a P5, mais os gates correspondentes de P7.
- **Plataforma pronta para mods que alteram gameplay:** inclui P6 e todos os gates de P7. Nao anunciar suporte a escrita antes disso.

## 2. Regras que nao podem ser violadas

- O alvo e o Brasfoot22-23 fornecido, nao o RealBrasfoot26.
- Nao substituir nem modificar `bf22-23.exe` no disco.
- Nao redistribuir classes, recursos, executaveis ou saves do jogo no pacote do SDK/loader. Extrair o runtime somente da copia local fornecida pelo usuario.
- Preservar as classes e o formato de serializacao usados pelos saves existentes.
- Nao usar classes ou regras exclusivas do RealBrasfoot26 como se existissem no 22-23.
- Nao contornar verificacoes de registro/licenca do jogo.
- Nao apagar, restaurar ou sobrescrever saves automaticamente.
- Tratar mods Java como codigo confiavel: permissoes da API nao sao uma sandbox.
- Nunca encerrar uma sessao do usuario para atualizar o JAR. Solicitar fechamento e respeitar progresso nao salvo.
- Toda conclusao sobre compatibilidade deve dizer quais testes realmente foram executados.

## 3. Base existente e lacunas confirmadas

### Ja existe

- Launcher separado, compilacao Java 17+ e API em JAR.
- Perfil por SHA-256 completo do executavel.
- Entrada original `best.h2` e bibliotecas embarcadas originais.
- Manifestos `vbrasfoot.mod.properties`, dependencias por ID, rejeicao de ciclos/duplicatas e permissoes basicas.
- Consulta de clubes/jogadores por snapshots imutaveis.
- Mod exemplo do inspetor e dados globais separados por mod.
- Backups antes da inicializacao e modo seguro.
- Verificacao offline de uma copia do save e testes unitarios/Swing.
- Opcao Java `--add-exports=java.desktop/sun.java2d=ALL-UNNAMED` no script de execucao.

### Ainda nao significa pronto

- O launcher agora aceita exclusivamente `gameRoot/vbrasfoot` como home interno; o pacote experimental ainda depende dos gates reais de P0/P1.
- Os scripts de desenvolvimento dependem de diretorios irmaos; os launchers do pacote portable usam a propria localizacao.
- O menu interno foi implementado em `a.eg.ve`, junto a `HX`/Opcoes, mas a versao mais recente ainda precisa de confirmacao visual na sessao real.
- O teste antigo encontrou 8.395 clubes e 193.831 jogadores com snapshots iguais,
  mas bytes diferentes. A primeira diferenca foi localizada na normalizacao de
  referencias de String vazia pelo Kryo; comparacao dos campos serializados dos
  dois grafos nao encontrou divergencias na cobertura implementada. As demais
  diferencas nao foram atribuidas byte a byte (ver `SAVE-ANALYSIS.md`).
- O round-trip offline nao equivale a jogar, salvar e reabrir pelo executavel original.
- Nao ha eventos reais de partida/temporada, dados vinculados a carreira, API de escrita nem politica completa de conflitos.
- Dependencias ordenam inicializacao; nao fornecem importacao de classes privadas entre mods.
- Logs iniciais e erros nao tratados do jogo ainda nao estao consolidados em um diagnostico unico por sessao.

## 4. Layout de instalacao proposto

```text
Brasfoot22-23/
  bf22-23.exe                    # original, intocado
  options.bcf                   # configuracao original
  sav/                          # saves originais, sem conversao
  teams/ ...                    # recursos originais
  vbrasfoot.cmd                 # entrada principal para o usuario
  vbrasfoot-safe.cmd            # recuperacao sem mods
  vbrasfoot/
    app/                        # loader e metadados da versao instalada
    runtime/                    # Java empacotado, se incluido na distribuicao
    mods/                       # JARs de mods instalados pelo usuario
    config/                     # mods habilitados e configuracoes
    data/
      global/<mod-id>/
      careers/<career-id>/<mod-id>/
    cache/<game-sha256>/         # derivado da copia local; regeneravel
    logs/<session-id>/
    backups/<session-id>/
    verification/
    install-manifest.json       # arquivos gerenciados, versoes e hashes
```

O projeto-fonte continua em `realfoot-descompacted/vbrasfoot/`. A pasta instalada em `Brasfoot22-23/vbrasfoot/` e uma distribuicao, nao uma copia do repositorio inteiro. Os nomes exatos dos arquivos de configuracao podem ser fechados em P1; a separacao entre recursos gerenciados e dados do usuario e obrigatoria.

Duplo clique em `bf22-23.exe` continua abrindo o jogo original, sem mods. O usuario abre `vbrasfoot.cmd` para carregar os mods; nao prometer carregamento pelo EXE original sem um mecanismo adicional explicitamente aprovado.

## P0 — Estabilizar e comprovar a versao atual

Prioridade: bloqueante. Dependencias: nenhuma.

- [x] Confirmar o botao vbrasfoot na barra interna da tela do elenco, sem barra externa adicional. (Captura real teste02-inspetor.png; outras resolucoes/escala ainda pendentes.)
- [ ] Testar resolucoes menores/maiores, escala do Windows, troca de telas e recriacao do painel. (1920x1080 em captura; 1280x720 e troca de tela confirmados pelo usuario; escala nao informada e matriz completa ainda pendente.)
- [ ] Garantir que nenhum botao original seja ocultado, deslocado para fora da area util ou sobrescrito. Definir alternativa integrada se faltar espaco.
- [ ] Registrar no diagnostico Java utilizado, argumentos, perfil, etapas da inicializacao e instalacao do menu.
- [ ] Capturar stdout/stderr desde o inicio, inclusive falhas anteriores ao FileHandler e excecoes tratadas pelo handler legado, sem duplicar logs indefinidamente.
- [ ] Verificar encerramento por fechar janela e por `System.exit`; evitar hooks que esperem pela EDT enquanto ela encerra a JVM.
- [x] Executar uma sessao em **copia completa e isolada** do jogo: carregar carreira existente, consultar, avancar, salvar, fechar e reabrir no EXE original. (Sucesso confirmado pelo usuario; hashes/logs e verificacao offline conferidos. Capturas finais/dados numericos nao fornecidos.)
- [x] Repetir com mods desligados e com o inspetor ativo. (Usuario confirmou ambos; logs dos modos conferidos; testes sequenciais na mesma copia.)
- [ ] Comparar dados relevantes antes/depois: clubes, jogadores, financas, calendario, competicoes e historicos, com cobertura e limites registrados.
- [x] Investigar a diferenca binaria do round-trip sem assumir que seja benigna ou que represente corrupcao. (Primeiro offset explicado e grafo serializado comparado; conclusoes/cobertura delimitadas em SAVE-ANALYSIS.md.)

**Aceite:** sessao real concluida e documentada, arquivos originais preservados e nenhuma falha bloqueante conhecida. Testes sinteticos nao substituem esse gate.

## P1 — Instalar na pasta original sem dependencia do projeto

Prioridade: bloqueante para distribuicao. Dependencias: P0.

- [x] Separar explicitamente `gameRoot`, `installRoot` e caminhos de dados.
- [ ] Substituir a proibicao geral de `home` interno por validacao especifica: aceitar `gameRoot/vbrasfoot`, rejeitar sobreposicao com `sav`, `teams`, executavel e demais recursos originais; verificar caminhos canonicos, junctions e links antes de escrita.
- [x] Criar pacote de release que contenha somente arquivos do vbrasfoot e licencas de dependencias redistribuiveis.
- [x] Criar instalador portable, por exemplo `install.ps1 -GameDir <pasta>`, com modo de previsualizacao.
- [x] Instalar launchers que resolvam os caminhos pela propria localizacao e iniciem com o diretorio de trabalho real do jogo.
- [x] Centralizar os argumentos JVM necessarios para que modo normal, seguro e diagnostico usem a mesma compatibilidade.
- [ ] Definir runtime Java suportado. Usuario final precisa apenas do runtime; JDK fica para desenvolvedores. Se empacotar Java, revisar licenca, fornecedor, versao e atualizacoes de seguranca.
- [ ] Verificar espaco livre, permissao de escrita, versao do jogo e existencia de sessao aberta antes de instalar/atualizar.
- [ ] Registrar arquivos gerenciados em manifesto. Atualizar apenas esses arquivos; preservar mods, configuracoes, backups e dados do usuario.
- [x] Implementar desinstalacao com previsualizacao, preservando dados por padrao e sem remocao recursiva ampla da pasta do jogo.
- [x] Manter modo seguro acessivel mesmo quando um mod/JAR esta quebrado.
- [ ] Testar instalacao, atualizacao, reinstalacao e mudanca de local com caminhos contendo espacos e acentos, sem privilegios administrativos.

**Aceite:** instalar numa copia do Brasfoot e executar mesmo depois de mover ou tornar indisponivel o projeto-fonte. Hash do EXE e recursos originais permanece igual. Saves existentes continuam abrindo.

## P2 — Firmar o contrato publico para autores de mods

Prioridade: alta. Dependencias: P0; pode ser projetada enquanto P1 e implementada.

- [x] Versionar API/SDK e definir compatibilidade entre loader, perfil do jogo e mods.
- [ ] Evoluir o manifesto com versao/range da API, versao minima do loader, dependencias obrigatorias/opcionais, conflitos e capacidades requeridas.
- [x] Preservar ou migrar explicitamente o formato atual; rejeitar campos/versoes incompatíveis com mensagens acionaveis.
- [ ] Definir estados do ciclo de vida: descoberto, validado, inicializado, ativo, falhou, desativado e encerrado.
- [ ] Definir threads permitidas, descarte de assinaturas, cancelamento e remocao de componentes da interface.
- [x] Nao expor nomes ofuscados, referencias mutaveis do jogo ou classloaders internos na API publica.
- [x] Documentar estabilidade dos IDs de clubes/jogadores e como identifica-los em snapshots de carreiras diferentes.
- [x] Adicionar consultas paginadas/filtradas; evitar copiar os quase 194 mil jogadores a cada acao na EDT. (API/bridge e inspetor usam paginas; falta medicao do orcamento real.)
- [ ] Ampliar leitura conforme evidencias: carreira, clube humano, calendario, competicoes, partidas, financas e historicos.
- [ ] Introduzir registro de servicos publicos entre mods, se necessario, em vez de compartilhar suas classes privadas indiscriminadamente.

**Aceite:** mod compilado apenas contra o SDK consegue consultar e apresentar dados, sem dependencia das classes do jogo nem do codigo-fonte do loader.

## P3 — Vincular persistencia a carreira sem alterar o save

Prioridade: alta. Dependencias: contrato de ciclo de vida de P2 e hooks de carregar/salvar de P4.

- [ ] Separar dados globais, por carreira e por sessao.
- [ ] Criar registro externo de identidade de carreira. Nao usar somente nome do arquivo nem hash do conteudo como identidade permanente: nomes mudam e cada gravacao altera o hash.
- [ ] Definir como tratar renomeacao, Save As, copia, restauracao de `.sbck` e duas carreiras com dados parecidos. Quando ambiguo, solicitar associacao explicita, sem vincular silenciosamente.
- [ ] Versionar schemas dos dados de cada mod e fornecer migracoes com backup e validacao.
- [ ] Gravar sidecars por substituicao atomica de arquivo, depois de confirmar sucesso da gravacao original, com geracao/hash associados ao save observado.
- [ ] Tratar falha entre gravacao do jogo e do sidecar: detectar desencontro, preservar geracao anterior e apresentar recuperacao. Nao afirmar transacao atomica entre arquivos independentes.
- [ ] Definir comportamento ao abrir sem um mod: seu estado e preservado, mas nao carregado/executado.
- [ ] Definir politica para recursos que dependem da presenca permanente de um mod; nao anuncia-los como reversiveis se o original nao puder representa-los.
- [ ] Expandir backups para incluir sidecars correspondentes, metadados e hashes; restauracao somente por operacao explicita e revisada.

**Aceite:** duas carreiras nao compartilham dados por engano; save antigo abre sem arquivos de mod; renomear/restaurar/corromper um sidecar tem comportamento testado e nao danifica o `.s22`.

## P4 — Expor eventos reais e confiaveis

Prioridade: alta. Dependencias: P0 e P2.

- [ ] Mapear com REA e inspecao de bytecode as rotinas de carregar, salvar, simular partida, avancar calendario, concluir transferencia e mudar temporada.
- [ ] Corroborar os pontos escolhidos com execucao controlada numa copia do jogo. Registrar identidade do artefato e evidencias de cada hook.
- [ ] Definir a tecnica de integracao de forma explicita. Preferir hooks em corpos de metodos, somente em memoria, sem alterar nomes, campos, hierarquia ou schemas serializados.
- [ ] Validar assinaturas/fingerprints antes de aplicar qualquer hook. Se o perfil divergir, recusar a capacidade e informar o motivo; nao executar hook por tentativa.
- [ ] Implementar primeiro `CareerLoaded`, `CareerUnloaded` e `CareerSaved`.
- [ ] Implementar depois `MatchFinished`, `DayAdvanced`, `TransferCompleted` e `SeasonFinished`, apenas onde houver evidencias e testes suficientes.
- [ ] Documentar semantica de cada evento: inicio/conclusao, thread, IDs, ordem, excecoes e dados disponiveis.
- [ ] Garantir entrega sem duplicacao por mudanca de tela, repintura ou timer de UI. Polling de tela nao comprova conclusao de partida.
- [ ] Isolar excecoes de callbacks, impedir reentrada indevida e nunca permitir que falha de um assinante interrompa a simulacao do jogo.
- [ ] Fazer assinaturas encerraveis e definir comportamento para callbacks lentos, filas limitadas e eventos apos descarregamento de carreira.

**Aceite:** um mod exemplo reage uma vez a cada evento real testado, com ordem documentada; callbacks que falham nao bloqueiam carregar, jogar ou salvar.

## P5 — Entregar o SDK e a experiencia de criar mods

Prioridade: alta para abrir a plataforma. Dependencias: P1 a P4 para o MVP completo.

- [ ] Publicar SDK versionado, Javadocs, guia de compatibilidade e especificacao do manifesto.
- [x] Criar template com compilacao simples, manifesto, testes, empacotamento e instalacao na pasta escolhida do jogo. (Starter leitura/interface; nao inclui eventos ou carreira.)
- [ ] Entregar exemplos: consulta/interface; historico por carreira; reacao a uma partida finalizada. Exemplos devem declarar somente as permissoes utilizadas.
- [x] Criar comando de validacao que nao inicialize codigo de mods, com relatorio de dependencias, capacidades e erros. (--validate-mod individual + --validate-only conjunto; nao e auditoria/sandbox.)
- [x] Adicionar painel interno de mods: ID, versao, permissoes, estado, falhas recentes e local dos logs. (0.4.2: 128 resumos da sessao nas fronteiras do loader, sem alegar captura de todo erro do jogo/mod.)
- [x] Implementar habilitar/desabilitar para a proxima inicializacao. (0.4.1: config atomica, grafo validado, CLI e painel; sem hot reload. UI real e usabilidade ainda pendentes.)
- [x] Fornecer exportacao de diagnostico revisavel pelo usuario, sem enviar automaticamente saves ou dados privados. (0.4.3: resumo TXT com previa editavel, identidade/detalhes opt-in, save local explicito; sem logs brutos/upload. UI real ainda pendente.)
- [ ] Fazer teste de usabilidade: criar e instalar um mod novo seguindo apenas o guia, sem editar o loader.

**Aceite do MVP:** um desenvolvedor cria um JAR a partir do template, instala na copia do Brasfoot com vbrasfoot ja instalado e usa consultas/eventos/dados por carreira sem conhecer `best.*` ou `a.*`.

## P6 — Habilitar mods de gameplay com operacoes controladas

Prioridade: posterior ao MVP. Dependencias: P0 a P5 aprovadas.

- [ ] Definir capacidades pequenas e separadas; evitar uma permissao irrestrita `game.write` como unica politica.
- [ ] Mapear rotinas originais e suas precondicoes antes de oferecer comandos de alteracao.
- [ ] Validar carreira ativa, geracao do snapshot, IDs, permissao, invariantes e fase do calendario antes de executar.
- [ ] Executar comandos na thread e no momento adequados do jogo; proibir mutacao durante gravacao ou iteracao sensivel.
- [ ] Retornar erros tipados e resultados claros; impedir repeticao acidental e referencias de uma carreira anterior.
- [ ] Definir prioridade/exclusividade por recurso e diagnostico quando dois mods tentarem alterar a mesma regra.
- [ ] Validar e preparar operacoes antes de aplicar. Oferecer rollback somente quando houver implementacao e teste reais; nao prometer transacao sobre rotinas legadas arbitrarias.
- [ ] Criar primeiro um exemplo minimo e reversivel. Trocas, contrapropostas e leiloes ficam em projetos posteriores, cada um com analise propria do 22-23.
- [ ] Para cada capacidade, provar que o save resultante reabre no jogo original, ou documentar/rejeitar a capacidade se ela exigir formato incompatível.

**Aceite:** comandos autorizados alteram apenas o estado esperado; entradas invalidas nao produzem efeitos parciais conhecidos; conflitos sao explicitos; saves testados reabrem no EXE original.

## P7 — Gates de release e operacao

Prioridade: transversal. Cada fase adiciona seus testes; esta fase consolida a liberacao.

### Matriz minima

- [ ] Save existente abre no original e no launcher, antes e depois de salvar pelo launcher.
- [ ] Modo normal, modo seguro, sem mods, mod invalido e dependencia ausente/ciclica.
- [ ] Dois mods simultaneos e falha em inicializacao/callback/encerramento.
- [ ] Troca de carreira sem vazamento de dados ou objetos da carreira anterior.
- [ ] Interface em resolucoes/escalas distintas, sem sobreposicao de controles.
- [ ] Instalacao portable, atualizacao bloqueada por sessao aberta e desinstalacao preservando dados.
- [ ] Caminhos com espacos/acentos, diretorio somente leitura, falta de espaco e escrita interrompida.
- [ ] Dado de mod corrompido, restauracao de backup e save/sidecar de geracoes diferentes.
- [ ] Versao desconhecida do executavel e hook incompativel recusados antes de mutacoes.
- [ ] Bibliotecas/classes de mods isoladas do runtime original conforme contrato; permissoes testadas sem alegar sandbox.
- [ ] Tempo de inicializacao, leitura do elenco e memoria medidos numa carreira grande; estabelecer orcamentos a partir da medicao, nao de suposicoes.

### Artefatos para publicar

- [ ] Pacote portable e hashes dos arquivos.
- [ ] Guia de instalacao, uso, modo seguro, atualizacao e recuperacao.
- [ ] SDK, template, exemplos e notas de compatibilidade.
- [ ] Changelog e lista de limitacoes conhecidas.
- [ ] Relatorio de testes com ambiente, passos, cobertura e resultados reais.

**Gate final:** nao ha pendencia bloqueante dos marcos anunciados; instalar/copiar mods e continuar saves existentes foi demonstrado na pasta original; o pacote funciona sem o projeto de desenvolvimento.

## 5. Sequencia recomendada de entregas

| Entrega | Conteudo | Condicao para seguir |
|---|---|---|
| A — Base confiavel | P0 | UI real e salvar/reabrir aprovados |
| B — Instalacao portable | P1 | Executa dentro de Brasfoot22-23 sem checkout |
| C — Contrato e carreira | P2 + hooks de carregar/salvar de P4 + P3 | API estavel e persistencia externa consistente |
| D — Eventos e SDK | Restante de P4 + P5 + gates MVP de P7 | Terceiro cria e instala mod novo pelo guia |
| E — Gameplay | P6 + gates completos de P7 | Mutacoes e compatibilidade comprovadas por capacidade |

Nao ha estimativa de prazo fechada: principalmente hooks e identidade de carreira dependem de evidencias e testes ainda nao obtidos.

## 6. Proximas tarefas de implementacao

1. [x] Salvar este documento em `vbrasfoot/roadmap/ROADMAP.md`.
2. Fechar a verificacao visual do menu interno e a sessao de salvar/reabrir em copia isolada.
3. Implementar o layout instalado e remover com seguranca o bloqueio de `home` interno, sem simplesmente permitir qualquer caminho dentro do jogo.
4. Gerar e instalar o pacote em `Brasfoot22-23/vbrasfoot/`, mantendo o EXE e os saves intocados durante a instalacao.
5. Publicar os resultados dessa primeira instalacao antes de iniciar a API de escrita.

### Incremento de implementacao — 16/10/2026

- Diagnostico por sessao, captura limitada e fechamento sem acesso a EDT
  implementados; testes automatizados de retorno/System.exit aprovados.
- Verificador offline ampliado com repeticao das mesmas raizes, segunda geracao,
  tamanhos e primeiro byte divergente. Diferenca antiga reproduzida, causa aberta.
- Preparacao de copia isolada e roteiro em `P0-VALIDACAO.md` adicionados.
  Fixture reduzida aprovada; copia completa real ainda pendente apos timeout.
- Evidencias e limitacoes atualizadas em `../VALIDACAO.md` (46 testes).

Nenhum gate de fase foi marcado como aprovado: ainda falta a sessao real de P0.

### Incremento P1/P2 — 16/10/2026

Implementacao experimental (nao equivale a aceite das fases):

- P1: InstallPaths separa gameRoot/installRoot e valida caminhos antes de escrita;
  release.ps1 gera pacote sem jogo/Java; instalacao com preview, manifesto/hashes,
  verificacao de processo/lock/espaco, launchers independentes e desinstalacao
  por allowlist preservando dados. Guia: `PORTABLE.md`.
- P2: SDK 0.2.0/API major 1, manifesto evoluido compativel com formato antigo,
  dependencias opcionais/conflitos/capacidades/loader minimo, estados de lifecycle,
  acoes com registro encerravel e consultas paginadas no bridge. O exemplo compila
  somente contra o JAR publico. Contrato e limites: `SDK-CONTRACT.md`.
- Pendente P1: sessao real instalada sem checkout, matriz completa de erros de
  escrita/interrupcao e testes de todas as JVMs anunciadas. Recuperacao explicita
  de uma falha real de substituicao ja foi implementada/testada (ver incremento abaixo).
- Pendente P2: ampliar leitura com evidencias do jogo, geracao de carreira,
  cancelamento de tarefas e politicas completas de lifecycle. Servicos entre mods
  nao foram introduzidos porque nao ha necessidade comprovada nesta entrega.

P0 continua bloqueante. Nao anunciar P1/P2 completos ou suporte a gameplay.

### Fechamento de bloqueios — 17/10/2026

- Copia completa isolada concluida: 34.342 arquivos, hashes verificados antes/depois
  da copia com concorrencia limitada; origem somente lida. Baseline privada preservada.
- Pacote instalado na copia completa e `--validate-only` aprovado com inspetor 0.2.0.
  Sessao real guiada escolhida pelo usuario; ainda nao executada/aprovada.
- P1: staging/journal de arquivos gerenciados, bloqueio de instalacao incompleta e
  recuperacao explicita com preview/allowlist/hashes adicionados. Falha real por
  arquivo bloqueado, publicacao parcial e rollback da geracao anterior testados.
- P0/P1/P2 ainda nao aprovados integralmente; P3/P4/P6 nao foram antecipados sem hooks
  e evidencias. Proximo passo e a sessao guiada e registro de seus resultados reais.

### Investigacao e matriz de recuperacao — 17/10/2026

- `analyze-save.ps1`: causa da primeira diferenca antiga reproduzida por String
  vazia; 20.578.147 campos serializados comparados sem divergencias na cobertura
  declarada. Sondas locais nao sao usadas no launcher/SDK. Detalhes em SAVE-ANALYSIS.md.
- Testes de P1 ampliados: ACL NTFS negando escrita, backup corrompido, journal fora
  da allowlist, staging interrompido e conclusao de limpeza em fixture sintetica.
- Espaco insuficiente: preflight recusou reserva maior que a disponivel antes de
  staging; falta de espaco real durante escrita/quedas de energia nao simuladas.
- Persistem limites de release: escalas, matriz de JVMs, cobertura semantica ampla
  da sessao real e erros legados nao consolidados. Nao declarar roadmap completo.

### Consolidacao e CI — 17/10/2026

- Workflow publico compila loader/SDK/inspetor e executa testes sinteticos no
  Windows com Temurin 17/21, sem jogo/saves e sem upload de artefatos privados.
- Execucao local com Java 21: 66 testes e Swing aprovados. CI remoto 38996623272
  aprovado para Windows/Temurin 17 e 21 (sintetico); revisoes seguintes do workflow
  devem ter seus checks conferidos antes de liberar o PR.
- Este incremento consolida recuperacao, copia completa e evidencias guiadas;
  nao substitui gates ainda abertos nem implementa eventos por polling de tela.

### P4 — inicio do mapeamento estatico (17/10/2026)

- `inspect-career-hooks.ps1` le somente o EXE conhecido, gera fingerprints de
  classe/Code/instrucoes e descriptors, recusando perfil sintetico desconhecido.
- Candidatos de carregar/salvar e suas armadilhas documentados em CAREER-HOOKS.md:
  salvar captura falhas e retorna void; carregar tem fallback recursivo cujo
  resultado e descartado pela chamada externa. Retorno simples nao comprova evento.
- Nenhum hook aplicado nem evento habilitado. Callers/descarregamento e execucao
  controlada ainda pendentes; esse mapeamento nao e aceite de P4 ou liberacao de P3.
- CI da revisao e9ff212 aprovado com Actions v5 pinadas e Java 17/21; checks agora
  obrigatorios na main, mantendo excecao somente de revisao para administrador.

### P4 — sonda opt-in (17/10/2026)

- `--probe-career-hooks` transforma somente corpos de c.a em memoria, SHA da
  classe e sites exatos validados; normal/seguro/verificador preservam caminho sem hooks.
- Tracking interno distingue retorno, conclusao primaria, backup e fallback
  recursivo. Eventos publicos/capacidades de mods continuam indisponiveis.
- Testes sinteticos de observador/transformador e I/O real na EDT com frame
  sintetico; UI completa/gameplay/unload/troca ainda pendentes. CAREER-PROBE.md.
- Nao marcar CareerLoaded/Unloaded/Saved implementados para mods antes dos gates.
- Sessao real da sonda: usuario confirmou load/save/reabertura original; logs
  mostram uma conclusao de cada operacao, load na EDT e save em Thread-1, stderr
  vazio. Unload/troca e politica de entrega ainda pendentes; nao habilitar P3.

### P4 — entrega limitada e sinais de lifecycle (17/10/2026)

- Dispatcher interno com fila 128, rejeicao nao bloqueante contabilizada, worker
  daemon isolado, callback falho isolado e close com prazo 500ms, sem EDT no shutdown.
  Estatisticas privadas em probe-delivery.properties; diagnostico nao duravel.
- Mapeamento ampliado: 139 referencias selecionadas em 1.038 classes, incluindo
  System.exit/EXIT_ON_CLOSE e recriacao de raizes. Nao ha unico unload corroborado.
- `roots-reset` observado depois de concluir construtor original; nao e
  CareerLoaded/Unloaded. Teste privado confirma raizes substituidas e snapshot vazio.
- Cobertura sintetica/real-I/O documentada; UI de criar/trocar/fechar ainda precisa
  corroborar lifecycle publico. API/P3 permanecem bloqueadas, sem sidecars neste passo.

### P2 — referencias de consulta com escopo (0.3.0)

- Session/generation identificam encarnacao observada de raizes/listas no bridge,
  nao carreira persistente ou revisao de todas as mutacoes. Funciona sem sonda.
- Handles opacos com referencia fraca e registro LRU 4.096 recusam grafo anterior,
  token expirado/forjado e clube movido/substituido; erros tipados pedem nova consulta.
- APIs anteriores preservadas; metodos scoped adicionados a API major 1 com
  loader minimo 0.3.0/capacidade query.scoped. Inspetor migrado para scoped reads.
- Reload/reset em metodos originais na fixture corroboraram recusa das referencias
  anteriores. Leitura normal/offline e Swing testados; detalhes SCOPED-QUERIES.md.
- Limites concorrentes/in-place declarados. Nao fecha identidade P3, lifecycle P4
  ou comandos P6. PR empilhado sobre a sonda enquanto PR #3 estiver aberto.

### P5 — starter de leitura/interface (0.3.1)

- Inspetor 0.3.0 confirmado pelo usuario na UI; logs e hashes conferidos. Sem
  inferir teste real de troca/invalidation a partir disso.
- PR #4 integrado na branch da sonda; scoped reaplicado na nova branch baseada
  em main para incluir as mudancas aprovadas no proximo PR principal.
- Template independente com build/testes/JAR/manifesto minimo/instalacao com
  preview/lock/recusa de overwrite, compilado somente contra SDK publico.
- SDK 0.3.1 empacotado com Javadocs e template de fontes por allowlist. Validador
  individual --validate-mod sem jogo e sem inicializacao de codigo, testado.
- Teste automatizado fora do checkout aprovado, mas usabilidade por terceiro,
  eventos/carreira, painel, habilitar/desabilitar e exportacao diagnostica ainda
  pendentes. Starter nao equivale ao MVP completo nem ao aceite integral de P5.

### P4 — guard sincronico experimental de lifecycle

- Estado interno separado da fila diagnostica; tickets de sessao/geracao recusam
  saves de grafo anterior. Tentativas concorrentes/overflow falham conservadoramente.
- Instrumenta atribuicoes de raizes em load/construtor/setter, preservando schema,
  EXE e fingerprint. Reset/falha nao significa CareerUnloaded ou nova carreira pronta.
- 22 testes internos, 15 de bytecode e 41 checks de I/O original aprovados. Frame
  sintetico nao comprova lifecycle de criar/trocar/descarregar na UI completa.
- Somente opt-in; relatorio privado career-lifecycle.properties, sem callbacks
  publicos, identidade persistente ou sidecars. Detalhes CAREER-LIFECYCLE.md.

### P4 — observacoes publicas experimentais (0.4.0)

- Usuario confirmou teste do guard; relatorio real com load=1, saves=0, ACTIVE
  antes de close, CLOSED depois, healthy=true e nenhuma tentativa pendente/perda.
- SDK disponibiliza CareerObservations somente com hooks opt-in/permissao;
  READ_COMPLETED/PRIMARY_WRITE_COMPLETED/INVALIDATED, sem inventar unload.
- Replay e revisoes ordenados, fila 64/assinatura, workers isolados e limitados,
  erros/overflow encerram somente assinatura; estado atual e recovery explicitos.
- Mod observador compilado apenas contra API; 33 checks sinteticos e 44 de I/O
  original com esse JAR. Nao prova UI completa, save duravel ou identidade P3.
- P3, eventos completos de lifecycle/gameplay e restante do roadmap permanecem
  pendentes. CAREER-OBSERVATIONS.md define contrato e cobertura, nao release estavel.

### P5 — selecao proximo inicio e painel (0.4.1)

- Nova branch baseada em main apos #5/#6/#7. API major 1 preservada; sem novos hooks.
- Configuracao por nome exato do JAR, sem editar/remover saves ou mods. Modo seguro
  ignora config/mods; JAR invalido desabilitado pode ser ignorado antes de carregar.
- Dependencias/conflitos/ciclos/duplicatas validados antes de gravacao atomica.
  CLI recusa sessao ativa; snapshot config obsoleto/corrompido falha conservadoramente.
- Painel no menu interno separa estado agora de proximo inicio; I/O fora da EDT.
- Testes sinteticos, Swing e processos novos em fixture; UI real e falhas recentes
  consolidadas continuam pendentes. P3/P4 completos/P6 nao fechados por este passo.

### P5 — historico limitado de falhas da sessao (0.4.2)

- #8 integrado na main antes da nova branch; usuario confirmou fluxo de painel,
  disable/reinicio/enable. Config final habilitada e sessoes inspecionadas; sem
  extrapolar para usabilidade por autor terceiro ou gates de gameplay.
- Historico 128 resumos imutaveis, sem reter excecoes/classes de mods; captura
  initialize/construtor, UI, callback, overflow e shutdown nas fronteiras do loader.
- Painel inclui contagem/ultima falha por JAR e aba global; Atualizar recarrega.
  Overflow detectado por status/painel/close, sem duplicacao por assinatura.
- 23 testes novos e UI sintetica; sem provocar falhas nos mods/saves do usuario.
  UI real 0.4.2, exportacao diagnostica e demais gates permanecem pendentes.

### P5 — exportacao revisavel de diagnostico (0.4.3)

- #9 integrado na main antes da nova branch. Resumo por allowlist, sem leitura ou
  anexacao de logs/saves/binarios; identidades/mensagens omitidas por padrao.
- Previa editavel, opt-ins independentes e confirmacao antes de salvar exatamente
  o texto revisado. Destino fixo diagnostics/diagnostic-UUID.txt, CREATE_NEW, limite 256 KiB,
  links/aliases recusados. Nenhum envio automatico ou sobrescrita de arquivos.
- 41 testes sinteticos e smoke Swing de cancelamento/opt-in/edicao/save exato;
  teste de junction fica no Temp privado, nao dentro da instalacao de desenvolvimento.
- UI humana/exportacao real pendentes; nao fecha P3, lifecycle/gameplay ou MVP
  integral. Contrato/privacidade/limites em DIAGNOSTIC-EXPORT.md.
