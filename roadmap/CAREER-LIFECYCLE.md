# Guard interno de carreira — P4 parcial

Ativo **somente** com `--probe-career-hooks`. Nao e API publica, identidade
persistente, sidecar, lock do jogo ou aceite integral de lifecycle.

## Contrato interno

- `EMPTY`: nenhum caminho observado nesta sessao; nao prova ausencia de carreira.
- `UNKNOWN`: tentativa de leitura/reset/setter ou troca de raiz observada. Nao
  significa unload nem carreira nova pronta; erros podem deixar raizes parciais.
- `ACTIVE`: leitura exclusiva concluiu com marcador de decode e geracao atual,
  incluindo fallback recursivo. Nao prova que toda inicializacao da UI terminou.
- `CLOSED`: guard descartado na sessao. Nao entrega CareerUnloaded nem consulta EDT.

Geracao invalida antes de uma tentativa e depois de cada atribuicao instrumentada
de SR/afQ. O transformador exige cinco sites originais: dois no construtor, dois
na leitura e um no setter. Mantem fingerprint completo e schema original.
Construtor/setter tem tratamento de excecao apos inicializacao de Object, para
limpar tracking e repropagar falhas sem alterar o fluxo original.

Ticket de save captura sessao/geracao/estado na entrada. Conclusao primaria de uma
geracao anterior e recusada; sem leitura aceita nao ha save associado. Save aqui
e Output.close observado, **nao** fsync, commit atomico, .info ou snapshot consistente.
Nao cancela saves reais concorrentes nem protege o grafo legado contra mutacao.

Tentativas sobrepostas nao publicam ACTIVE: precisam de leitura posterior exclusiva
para recuperar estado conhecido. Registro limita 64 tentativas; overflow deixa
guard nao saudavel ate fim da sessao. Fallback na mesma thread e uma tentativa
externa, nao varias carreiras. Geracao nao e a CareerScope do SDK e nao deve ser
persistida. Nenhum objeto legado e retido no guard.

Atualizacoes sincronas sao curtas, sem I/O, callbacks de mods ou chamadas EDT.
Logging continua no dispatcher limitado/descartavel, **separado** do estado.
Falhas de callbacks/perda de diagnostico nao apagam transicoes do guard nos testes.
Hooks seguem best-effort; falha catastrofica de JVM/alocacao nao oferece garantia.

No fechamento, `career-lifecycle.properties` privado registra estado anterior,
geracao, arquivos, contadores, tentativas abertas e saude. Snapshot/descarte sao
atomicos no lock interno; arquivo posterior nao e duravel. Encerramento forcado
pode impedir o relatorio. Nomes de saves sao privados: revisar antes de compartilhar.

## Evidencia e limites

### Etapas de I/O e exclusividade de save (incremento interno)

SaveAttempt captura ticket na entrada, ate64 tentativas ativas por guard. Saves
sobrepostos (inclusive recursivos) tornam ambas conclusoes ambiguas: nao incrementam
primaryWrites nem sequencia completa. O jogo continua executando normalmente; isso
nao e lock/serializacao do save original. Fim/falha libera a tentativa; duplicatas,
outro guard e completions depois de close nao substituem o ultimo resultado.
Overflow deixa healthy=false e invalida geracao. Load/reset durante save continua
invalidando o ticket; nao cancela mutacao original nem torna grafo consistente.

SaveOutcome privado distingue metadataClosed (.info apos FileOutputStream.close),
primaryClosed (.s22 apos Output.close), backupCopied (.sbck apos Files.copy), returned,
bound e completeSequence. Completo exige todos marcadores, retorno sem abort e
associacao aceita ao ticket exclusivo ativo. Falha engolida de .info ou backup nao
vira sequencia completa mesmo quando a observacao PRIMARY_WRITE_COMPLETED ocorre.
Counters complete/partial/failed sao agregados da sessao, nao identidade da carreira
ou journal; ultimo resultado e historico e nao deve ser associado ao loadedFile
atual por suposicao. primaryClosed com abort/unbound/overlap conta como parcial.

read.initializedPaths conta apenas leitura aceita com passagem pelo caminho
best.f.ap() no ramo de inicializacao de c.a.c. Frame ja registrado pula esse ramo.
Marcador apos retorno de ap e antes do retorno externo, nao comprovacao de toda
UI/event queue pronta ou criacao. Fallback propaga o marcador do decode aceito
sem contar o retorno externo false como nova leitura. UNKNOWN de criacao inalterado.

Sites adicionais exigem SHA original e exatamente um FileOutputStream.close no
save e um best.f.ap na leitura. Nenhum campo/metodo/descriptor original alterado.
Relatorio career-lifecycle.properties inclui estagios/counters/inFlightSaves sem
I/O ou Swing nos metodos instrumentados. Relatorio nao e duravel em crash/kill.
Sem CareerLoaded/CareerSaved/CareerUnloaded publico, fsync, commit atomico de tres
arquivos, sidecars/P3, IDs persistentes ou garantia de snapshot.

Limite adicional confirmado estaticamente: Kryo4.0.2 Output.close chama flush e
engole IOException do close do stream subjacente. Portanto primaryClosed e ate
completeSequence representam os marcadores observados, NAO ausencia de todo erro
de fechamento do arquivo. Antes de CareerSaved exigir evidencia desse caminho,
sem modificar o comportamento original; nao inferir sucesso duravel desses flags.

UI real0.7.3 corroborada na sessao008c347b-75ed-4b54-85f4-61c4e13f56a5:
uma leitura aceita com initializedPaths1, save com todos marcadores/counters1,
sem partial/failed/stale/unbound/pendentes e dispatcher sem perdas. Usuario
confirmou funcionamento do roteiro; .s22/.sbck atuais tem hashes iguais. Nao
generaliza para criacao/unload nem remove o limite do close subjacente de Kryo.

### Incremento0.7.4 — fechamento subjacente observado

Sonda transforma tambem com.esotericsoftware.kryo.io.Output em memoria, somente
Java17/21/opt-in. SHA da classe73ba15ba16dbcff3a2ce8456de5b70126077c1b62b4128359bcbb2d2dfc4e04a,
um close(), uma chamada flush, um OutputStream.close e um handler IOException
com ASTORE exatos. Divergencia recusa antes de defineClass; modo normal intocado.
Caller c.a.ae duplica receiver antes de Output.close e registra sua identidade
na tentativa corrente da thread. Marcadores apos close normal e no handler
original so afetam esse receiver, nao outros Outputs/threads/operacoes.
Receiver retido somente durante a tentativa e liberado no retorno/falha; report
e estado publico contem flags/counters, nunca stream/Throwable/objeto do jogo.

streamClosed exige retorno da chamada subjacente. streamCloseFailed marca a
IOException que a biblioteca engole; flag e persistente naquela tentativa e
nao e apagada por marcador posterior de sucesso. O jogo continua engolindo a
excecao, copiando backup ou retornando exatamente como antes; sem fechar duas
vezes, retry, rethrow, flush/fsync extra, reparo ou alteracao de bytes gravados.
Falha conhecida impede bound/PRIMARY_WRITE_COMPLETED e completeSequence.
Sequencia completa0.7.4 tambem exige streamClosed: unknown/null-stream nao vira
completa. Kind experimental legado ainda significa wrapper-return quando close
subjacente nao foi observado, nao durabilidade; sem expandir sua API.
Report adiciona save.last.streamClosed/streamCloseFailed e counter
save.streamCloseFailures. Versoes anteriores nao tinham esses campos e seu
completeSequence nao deve ser reinterpretado como fechamento subjacente provado.

43 guard checks,9 bytecode sintetico e69 checks original I/O/biblioteca aprovados.
Falha de close/flush foi induzida com Output original e streams de codigo proprio,
ticket/metadata/backup sinteticos; nao falha de FileOutputStream do save completo
por disco cheio. Caminho normal de c.a.ae confirma close subjacente real em fixture.
Esse escopo nao prova fsync, consistencia do grafo, atomicidade .info/.s22/.sbck,
CareerUnloaded, identidade persistente, todas rotas de save ou readiness de criacao.

- 39 testes sinteticos: geracao, save atrasado em thread propria, fallback,
  falhas/parcialidade, tentativas sobrepostas, overflow, fechamento, relatorio e
  independencia de callback falho/fila saturada.
- 15 checks de bytecode sintetico, incluindo excecao do construtor repropagada.
- 61 checks nos metodos originais em fixture privada: schema, load/save/reload,
  fallback, falhas, reset e setter; frame sintetico, nao gameplay/UI completa.

Pendente: UI real de criar/trocar/descarregar, atribuicoes fora dos metodos
instrumentados e mutacoes in-place, compromisso do save, protocolo publico de
assinaturas/ordem/entrega e testes de mod assinante. Sem habilitar P3/P6.

Reproducao: `build.ps1` (sintetico) e `test-career-hooks.ps1` (EXE/save locais
conhecidos, somente copias e nomes novos). Nenhum artefato do jogo redistribuido.
