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

- 22 testes sinteticos: geracao, save atrasado em thread propria, fallback,
  falhas/parcialidade, tentativas sobrepostas, overflow, fechamento, relatorio e
  independencia de callback falho/fila saturada.
- 15 checks de bytecode sintetico, incluindo excecao do construtor repropagada.
- 41 checks nos metodos originais em fixture privada: schema, load/save/reload,
  fallback, falhas, reset e setter; frame sintetico, nao gameplay/UI completa.

Pendente: UI real de criar/trocar/descarregar, atribuicoes fora dos metodos
instrumentados e mutacoes in-place, compromisso do save, protocolo publico de
assinaturas/ordem/entrega e testes de mod assinante. Sem habilitar P3/P6.

Reproducao: `build.ps1` (sintetico) e `test-career-hooks.ps1` (EXE/save locais
conhecidos, somente copias e nomes novos). Nenhum artefato do jogo redistribuido.
