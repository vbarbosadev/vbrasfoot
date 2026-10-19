# Estado atual — contexto de retomada para agentes/LLMs

Atualizado em **19/10/2026**. Baseline: `main` em
`1b8288e80adc1b227245a38f95ab9983f15caa5d`, PRs **#1–#49 integrados**.
Antes de editar, confira `git status`, commits novos e este documento: esta e uma
fotografia da sessao, nao uma garantia de que o ambiente permaneceu igual.
Incremento posterior a esse baseline: preparacao de validacao humana delimitada
de carga e inspetor offline dos reports, conforme secao7.1. Sessao humana ainda
NAO executada/aprovada; nao confundir a copia preparada com carga real concluida.

## 1. Objetivo e pedido do usuario

Construir um loader/SDK confiavel para o **Brasfoot22-23 fornecido**, permitindo
que autores e IAs criem mods variados sem conhecer classes ofuscadas.

O escopo em andamento prepara suporte a patrocinios e negociacoes de transferencias
com varias rodadas. O usuario pediu implementar as capacidades restantes do loader,
mas esclareceu: **NAO criar os mods agora**. Nao interpretar pedidos de continuar
ou implementar tudo como autorizacao para criar carreiras, jogar partidas, alterar
saves pessoais ou ignorar gates de compatibilidade.

**Resumo honesto:** consultas publicas somente leitura estao disponiveis; ha uma
base interna extensa de persistencia/comandos, revisao de metadados e protecao de
I/O. Ainda **nao existe suporte publico completo a escritas, calendario ou
negociacoes**. Nao declarar "100%", pronto para producao ou todos os gates aprovados.

## 2. Restricoes que devem ser preservadas

- Exclusivo do perfil original conhecido. Preservar EXE, recursos, schemas
  serializados e saves existentes. Sem bypass de licenca ou redistribuicao proprietaria.
- Nao encerrar processos de jogo do usuario, criar carreiras, jogar partidas ou
  sobrescrever saves pessoais automaticamente. Testes usam fixtures novas e marcadas.
- Nao reverter/descartar trabalho desconhecido. Investigar mudancas antes de editar.
- Preservar a aparencia aprovada do comparador **1.1.2**; refinamento visual foi adiado.
- Nao instalar builds, publicar releases/tags ou modificar mods instalados por
  inferencia. As entregas recentes alteraram codigo/Git, nao a instalacao do jogo.
- Historico de trabalho: branches especificas, PRs detalhados, checks e integracao.
  `main` protegida foi preservada; merges anteriores usaram squash, head SHA completo
  e bypass administrativo de review, sem alterar protecao. Verificar autorizacao e
  estado atual antes de repetir operacoes remotas; nao ha necessidade de faze-las
  apenas para consultar este contexto.
- Mods Java nao sao sandboxados. Permissoes do SDK nao impedem um JAR malicioso de
  usar arquivos/reflexao por conta propria. Nao prometer sandbox ou exactly-once.

## 3. Repositorio e ambiente conhecido

- Checkout Windows/PowerShell:
  `C:\Users\vinicius\Documents\dev\realfoot-descompacted\vbrasfoot`.
- Jogo usado como origem read-only dos wrappers:
  `C:\Users\vinicius\Documents\dev\realfoot-descompacted\Brasfoot22-23`.
- Instalacao pessoal separada:
  `C:\Users\vinicius\Documents\Brasfoot22-23`.
- Temporarios aprovados:
  `C:\Users\vinicius\AppData\Local\Temp\opencode`.
- Build sem Maven/downloads: JDK 17/21; hooks usam ASM interno do JDK com export
  explicito. Modo normal nao transforma classes; sonda e opt-in.
- REA nao anunciou ferramenta JVM elegivel na ultima inspecao; providers nativos
  indisponiveis/configurados insuficientemente. Foram reutilizados javap/JDK,
  scanners Java e evidencia privada anterior. Nao instalar engines/configuracoes
  apenas para repetir analise que as ferramentas existentes conseguem realizar.

## 4. Versoes instaladas e identidade do alvo

Valores confirmados nas sessoes anteriores; revalidar se for tocar na instalacao.

| Artefato | Estado / SHA256 |
|---|---|
| EXE original `bf22-23.exe` | `2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c` |
| Loader instalado **0.9.3 experimental** | `3662113df6f2277c7c3ab05d3e9bb0143664a707dcf798dbf3109f8b235004f7` |
| Comparador **1.1.2** | `18438b12e5f7940f94a3fd20b7f1bd5d90fac9387f4e9df3d5a0dc9a59af6c1d` |
| Scout somente leitura **1.0.0** | `e97fce5e32ad37791b99d64292c8ea1b5169185b990cbd19b745a3a160f13b99` |

API major continua **1**. Nao houve release/tag publica dessas fundacoes.
Observador experimental nao esta instalado. Backup explicito anterior ao loader0.9.3:
`vbrasfoot/backups/managed-0.9.2-before-0.9.3-8d19c5d47f76419a900d95b070f54af5`.
Nao confundir backups `session-*` com backup gerenciado do instalador.

## 5. O que esta implementado

### SDK publico e leitura

- Pacote SDK-only, validador, Javadocs, templates/exemplos e testes de autoria sem
  jogo ou classes internas. Isolamento/revogacao de recursos e falhas de mods.
- Consultas com scopes/referencias obsoletas recusadas, DTOs de data/saldo e detalhes
  de jogadores. Sete habilidades individuais brutas; `Optional.empty` quando desabilitadas.
- `CareerIoEvents` publico limitado a carga existente qualificada e sequencia de
  save observada; nao significa durabilidade, identidade persistente ou unload.
- Sem permissao/API publica de escrita na carreira, sem `game.write` generico.

### Fundacoes internas de persistencia e comandos

- `CareerSidecarStore`: envelopes versionados/checksums, records imutaveis,
  HEAD/current/previous, CAS, locks cooperativos, limites, recovery explicito.
  Nao e transacao save+sidecars; nao migra ancora ordinaria automaticamente.
- `CareerSaveAssociations` + `SaveFileObserver`: observacao read-only limitada,
  UUIDs somente por decisao explicita, fork vazio com lineage precisa, rename
  comprovado ou afirmado separadamente, revisao e restore do previous conhecido.
  Hash identifica bytes, **nao identidade logica**. `CareerScope`/`ClubRef` tambem nao.
- `CareerCommandGate`: owner/context/permit de sessao, roots fracas, recheck na EDT,
  supressao de repeticao, exclusao de handlers e persistencia terminal em andamento.
- `CareerCommandJournal`: intent duravel antes do efeito; PREPARED e apresentado
  conservadoramente como UNKNOWN. Reabrir/duplicar nao reemite lease. `inspectRecovery`
  distingue EMPTY/NO_EFFECT_ONLY/HISTORICAL_EFFECTS/UNCERTAIN; `quarantinePending`
  persiste PREPARED->UNKNOWN, sem resolver ou liberar replay.
- `CareerCommandDispatcher`: journal->gate->EDT->handler->completion; timeout de
  fila versus handler em execucao, resultados incertos, exclusao ate terminar I/O
  terminal. Resultado tardio nao apaga incerteza; historico vira RECORDED_ONLY.
- `LegacyFinanceAdapter`/`FinanceDelta`: somente credito bruto6/debito bruto7,
  fingerprints exatos, valores previstos e verificacao de saldo+14campos do ledger,
  overflow e plano one-shot. Sem setters brutos de rollback. Nomes brutos NAO
  significam categorias de negocio validadas como patrocinio ou salario.

### Integracoes recentes — PRs #45–#49

- **#45:** associacoes explicitas e observador de arquivos.
- **#46:** classificacao/quarentena do diario e fences de save.
- **#47:** origem do stream realmente alimentado ao decoder Kryo ligada a roots
  fracas/sessao/geracao, inclusive fallback. Verificador cruza path/hash/tamanho e
  roots na EDT com timeout. Coordenador e painel opt-in de revisao de metadados.
- **#48:** `CareerIoInterlock` integrado ANTES das rotinas originais c/ae/setter/reset
  via transformer fingerprintado. Recusa conflitos antes de mutacao; fallback/reset
  do mesmo thread usam a fence externa; saves nao aninham. Cleanup preserva retorno/
  Throwable; detach nao libera writer em voo nem permite trocar owner no meio.
- **#49:** revisao une owners configurados e namespaces fisicos no binding escolhido,
  incluindo mods desabilitados sem carrega-los. Valida nomes/tipos/conteudos/HEADs,
  limita uniao64/arquivos128/bytes16MiB/budget cooperativo5s, revalida nomes/tokens
  antes de confirmar. Diretorio existente sem HEAD e pendencia, nao historico vazio.

## 6. O que esta conectado — e o que NAO esta

No codigo atual, `Main` registra o painel **Carreira e diarios** somente com
`--probe-career-hooks`. `ProbeSession` instala o interlock e um gate privado antes
do runtime instrumentado. A versao instalada continua anterior a essas entregas.

- Abrir/atualizar painel so le. Confirmacoes one-shot alteram metadata/quarentena
  externa, nao o grafo do jogo. Cancelar e padrao; I/O em SwingWorker, fora da EDT.
- Metadata do coordenador: `home/data/.career-state/associations` e `journals`.
  Nao transforma o `ModStore` global `data/<modID>` em persistencia por carreira.
- Gate da sessao **nao recebe contextos/owners para comandos de mods**. Adaptador
  financeiro/dispatcher permanecem internos, usados em fixtures explicitamente autorizadas.
- Um futuro servico de comandos deve usar o mesmo gate/interlock da sessao; criar
  um segundo gate nao participa da exclusao. Nao existe autoridade publica pronta.
- READ_ONLY_LINKED, STREAM_BOUND, igualdade de bytes ou initPath observado isolado
  nao sao permissao para escrever, prova de consistencia global ou de EDT drenada.
- Inventario cobre somente o binding/root explicitamente escolhido. Nao descobre
  identidades, outras carreiras, journals removidos ou armazenamento externo.

## 7. Evidencia mais recente e seus limites

- PR #49: <https://github.com/vbarbosadev/vbrasfoot/pull/49>.
- CI da `main` **39255237995**, Java17/21 aprovado:
  <https://github.com/vbarbosadev/vbrasfoot/actions/runs/39255237995>.
- Build completo/regressoes locais aprovados:18checks de inventario,
  43de coordenador,151de I/O/schema originais e26financeiros originais; suites
  anteriores continuam rodando no build. SDK-only/cinco projetos/negativos aprovados.
- Logs privados locais: `build/binding-inventory-original.log`,
  `build/binding-inventory-finance.log`, `build/binding-inventory-sdk-test.log`.
- Fixtures preservadas sob Temp/opencode:
  - I/O: `vbrasfoot-hook-io-0215f95ab0d44665a9554fb70763d215`.
  - Financas: `vbrasfoot-finance-39c7741242e94dea88d48f4880f29f38`.
  - SDK: `vbrasfoot-sdk-author-a874b114ba684da7ae6b617f249439b1`.

**Nao extrapolar:** fixture I/O usa JFrame sintetico e pula init UI completo;
`initializationPathObserved=false`, public IO load UNKNOWN e nenhum contexto de
comando de producao instalado. UUIDs/reservas/journals extras das fixtures sao
autoridades/dados ARTIFICIAIS, nao confirmacoes humanas nem efeitos automaticamente
reconciliados. Financas passaram writer/reload originais em copia, nao reabertura
standalone com aceite humano. Fault injection usa excecoes, nao power-loss real.
Schemas inalterados e hashes de origem foram conferidos; nao publicar saves,
game.jar, javap, TSVs ou bytecode privado dos diretorios build/Temp.

### 7.1. Preparacao da carga real — incremento posterior ao baseline

- `prepare-load-validation.ps1`: copia completa verificada com baseline, home novo
  sem mods e launcher opt-in `probe-load.cmd`, SEM iniciar jogo automaticamente.
- `--inspect-load-validation PASTA-SESSAO`: leitor offline limitado de reports
  encerrados, sem jogo/home/log/cache novo ou autoridade de escrita. Valida
  correlacao de sessionIDs entre lifecycle/provenance/interlock e rejeita dados
  incompletos/misturados/invalidos, perdas, recusas e save no roteiro de carga.
- `QUALIFIED_LOAD_OBSERVED` e so coerencia de markers; nao prontidao global, prova
  de ausencia de todos os efeitos, aceite humano ou autorizacao de comandos.
- Novo roteiro: `roadmap/LOAD-ONLY-VALIDATION.md`. Positivos em self-tests sao
  reports/markers SINTETICOS, inclusive quando produzidos por ProbeSession real.
- A inspecao estatica privada confirmou que init original pode chamar autosave/
  progressao via ap conforme estado/configuracao. Nao executar esse ramo sozinho
  como se fosse getter ou alterar save/configuracao para obter positivo.
- Portanto o proximo gate continua sendo a sessao humana com carreira EXISTENTE
  na copia, sem avancar/jogar/salvar/confirmar metadata; registrar qualquer tentativa
  original de autosave ou recusa de SAVE aninhado em LOAD, sem contorna-la.

## 8. Bloqueios e proxima sequencia recomendada

1. **Carga real delimitada:** preparar/reutilizar copia isolada e validar uma carga
   EXISTENTE pelo ramo completo da UI, incluindo provenance/painel e invalidacao
   por recarga/save. Nao criar outra carreira ou jogar automaticamente para isso.
   Distinguir observacao de init de prova de consistencia global.
2. **Contexto de comando qualificado:** definir contrato operacional limitado,
   vincular associacao aceita + evidence revision + roots/geracao, journals e gate
   da sessao. Revalidar imediatamente antes do efeito; negar ambiguidades. Nao
   simplesmente chamar installContext porque READ_ONLY_LINKED retornou.
3. **Reconciliacao por operacao:** persistir evidencia reproduzivel suficiente para
   distinguir efeito em memoria, inclusao no save, interrupcao, save-as e restore.
   APPLIED e historico, nao prova de inclusao; comparar apenas saldo nao basta.
   UNKNOWN nao deve virar sucesso/no-effect por confirmacao humana generica.
4. **Primeira operacao financeira especifica:** validar categoria de negocio,
   identidade/digest persistente do pedido (plano atual tem nonce de sessao), UI,
   erros e reabertura original antes de API publica/permissionamento.
5. **Calendario:** mapear fronteiras reais e ordenar/deduplicar/recuperar eventos;
   separar dia/partida/temporada. Nao transformar polling ou todo retorno de az em
   DayAdvanced. Nao jogar partidas automaticamente para obter evidencia.
6. **Mercado:** mapear geracao/interesse, identidade/validade de ofertas e interceptar
   antes do commit; implementar recusa/contraproposta/cancelamento/expiracao e
   consistencia final de dinheiro/elencos/transferencia. Ainda nao implementados.
7. **Contrato de autoria:** DTOs/permissoes/erros/docs/testes SDK-only e autor externo,
   apos gates reais. Nao publicar capacidades ficticias ou stubs que dizem sucesso.

Tambem faltam catalogo/recuperacao global de associacoes, quotas globais, compactacao,
migracoes, politica completa de copy/restore/save-as, UI de recuperacao mais ampla
e validacao de durabilidade. Nao ha unload confiavel: caminhos examinados saem por
System.exit/EXIT_ON_CLOSE; nao inventar CareerUnloaded.

## 9. Armadilhas ja identificadas

- Java/Windows pode devolver `fileKey=null`. Rename afirmado e comprovado sao
  diferentes; hardlinks com ambos paths presentes usam Files.isSameFile.
- `best.S.aY(2)` instala roster mas retorna **null**. PANEL_RETURN significa apenas
  que a chamada retornou, nao painel retornado/renderizado ou EDT drenada.
- Qualificador de criacao0.9.3 reconhece shape estrito online, nao readiness.
  Positivo original de PATH_RETURNED segue pendente; reports anteriores tem sequencias
  independentes e nao permitem reconstruir ordem global retroativamente.
- NO_OPTION1 e distinto de cancel/close. Nao inferir resultado de dialogo de "feito".
- `best.ah.e(long)` so seta saldo. v/w atualizam ledger condicionalmente; C.ez soma
  dL+dM como int antes de long. Nao substituir operacao financeira por setter.
- Erro preexistente de importacao de times: StreamCorruptedException,
  header E8CC1702, caminho best.U.a/w -> best.p.dd -> a.gq.mi. Arquivo
  `teams/forge_can.ban` SHA256
  `b7c0769f76e70c1cfd66b0bb34faa384558f447d4c70ab4e4779d09fa730adff`.
  Nao apagar/converter esse arquivo nem culpar o loader sem evidencia nova.

## 10. Arquivos e comandos de retomada

Leia primeiro este STATUS, depois somente os contratos relevantes:
- `roadmap/DECODED-SAVE-COORDINATOR.md`: provenance/coordenador/painel.
- `roadmap/ORIGINAL-IO-INTERLOCK.md`: admissao/cleanup originais e gate da sessao.
- `roadmap/BINDING-JOURNAL-INVENTORY.md`: inventario do binding e owners desabilitados.
- `roadmap/LOAD-ONLY-VALIDATION.md`: preparacao/manual e inspeccao offline da carga.
- `roadmap/JOURNAL-RECOVERY-AND-SAVE-FENCES.md`: incerteza/quarentena/fences.
- `roadmap/EXPLICIT-SAVE-ASSOCIATIONS.md`, `CAREER-PERSISTENCE-DESIGN.md` e
  `LOADER-WRITE-FOUNDATIONS.md`: identidade/armazenamento/comandos e limites.
- `roadmap/MOD-VARIETY-PLAN.md`: escopo geral. `VALIDACAO.md`: evidencia historica
  detalhada; secoes antigas podem ter sido superadas por entregas posteriores.
- Classes correspondentes em `src/br/vbrasfoot`, transformer em
  `hook-src/br/vbrasfoot/hooks/CareerTransformer.java`, self-tests em
  `test/br/vbrasfoot` e `test/br/vbrasfoot/hooks`.

```powershell
git status --short --branch
git log -5 --oneline
.\build.ps1
.\test-career-hooks.ps1 -GameDir '<origem conhecida>' -Save '<save existente>'
.\test-finance-commands.ps1 -GameDir '<origem conhecida>' -Save '<save existente>'
.\release-sdk.ps1 -Output '.\dist\sdk-verificacao-nova'
.\test-sdk-package.ps1 -Archive '.\dist\sdk-verificacao-nova.zip'
git diff --check
```

Wrappers originais recusam jogo em execucao, criam fixtures marcadas novas e
verificam hashes de origem; nao os contorne. Nao restaurar/apagar fixtures privadas
automaticamente. Use timeout suficiente; um build interrompido nao conta como
aprovado. Nao instalar o JAR gerado sobre o loader pessoal sem tarefa explicita.

**Disciplina de continuidade:** priorizar um fluxo real seguro de ponta a ponta,
nao apenas mais infraestrutura isolada. Separar implementado/testado em fixture,
integrado no codigo, instalado e aceito humanamente. Atualizar este STATUS quando
uma entrega mudar esses fatos, sem transformar intencoes em capacidades prontas.
