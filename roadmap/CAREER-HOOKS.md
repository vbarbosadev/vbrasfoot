# P4 — candidatos estaticos de hooks de carreira

Este documento registra o **mapeamento estatico**. A entrega posterior de sonda
 opt-in e seus testes/limites estao em `CAREER-PROBE.md`; observacoes publicas
 limitadas posteriores estao em `CAREER-OBSERVATIONS.md`, nao eventos completos.
Este inventario nao e corroboracao de runtime nem aceite de P4. A API continua bloqueada
pelas evidencias/gates correspondentes; polling de tela nao substitui esses hooks.

## Identidade

- EXE: `2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.
- Membro: `c/a.class`, major 51, SHA-256
  `4e77bf19948d700acebf8a09bbc0c39fe0cb45901f4dd5eb304fcfddb4fbd19d`.
- Fonte: bytes da copia local fornecida, `javap -c -p -s` e parser proprio do
  classfile. Ferramenta verifica o EXE antes/depois e nao inicializa classes do jogo.
- Fingerprints cobrem descriptor, acesso, instrucoes e Code completo (inclui
  handlers). Code/instrucoes/classes nao sao redistribuidos; relatorios ficam em build.

## Candidatos e armadilhas

### `c.a.ae(String): void` — grava carreira

Observado: escreve `.info` separadamente; cria Kryo, desabilita registro obrigatorio,
grava SR (`best.f`) e afQ (`best.ay`) em `.s22`, fecha Output. Somente depois do
fechamento bem-sucedido marca um flag local de sucesso. Se sucesso, tenta copiar
o `.s22` para `.sbck`. Excecoes sao capturadas e impressas, sem propagacao obrigatoria.

Consequencias para um hook futuro:

- Retorno normal/ausencia de excecao para o caller **nao prova sucesso**.
- `.info` bem-sucedido nao prova gravacao da carreira, e vice-versa.
- O ponto candidato de sucesso do save primario e apos fechar Output, nao depois
  de clicar no botao ou detectar mudanca de timestamp.
- Definir se CareerSaved significa save primario confirmado ou tambem `.sbck`
  concluido; falha do backup legado nao pode virar sucesso silencioso desse backup.
- Nome do arquivo vem do argumento; identidade permanente nao deve depender dele.

Fingerprint de Code completo:
`60cc614297d0c502a1e3b5fc91deb0d6e019f0e038ca2366aa11a1f8286fd801`.

### `c.a.c(String, boolean): boolean` — carrega carreira/backup

Observado: argumento boolean seleciona `.s22`/`.sbck`; le as duas raizes e atribui
SR e afQ separadamente. Depois inicializa recursos/estado/UI quando necessario.
Em falha do `.s22`, chama recursivamente a mesma rotina para `.sbck`, **descarta
o resultado dessa chamada** e retorna false na chamada externa.

Consequencias:

- Retorno false da chamada externa **nao prova que nenhuma carreira foi carregada**:
  a chamada interna do backup pode ter funcionado.
- Atribuicao de SR pode acontecer antes da leitura de afQ falhar; nao anunciar
  CareerLoaded no primeiro putstatic nem apenas quando SR se torna nao nulo.
- O argumento null evita a leitura; retorno true nesse caminho nao prova que
  um arquivo foi lido. Pode representar inicializacao de carreira ja alocada.
- Instrumentacao deve acompanhar profundidade/resultado real da tentativa e
  origem `.s22`/`.sbck`, deduplicando fallback e respeitando falhas da inicializacao.
- Ponto exato de commit/ordem dos eventos depende de execucao controlada, ainda ausente.

Fingerprint de Code completo:
`e5ba1b91ccb5e69ec11fcf86a63e0183570584e2674147e060e395597ad75512`.

### Outros membros inspecionados

- `ad(String): void`: escreve somente `.info`; **nao e CareerSaved**.
- `vK(): void`: mostra mensagem de arquivo invalido; **nao e unload**.
- `a(best.f): void`: somente atribui SR; nao estabelece carreira carregada/salva.
- Ponto de descarregar/trocar carreira ainda nao mapeado/corroborado.

## Callers e atribuicoes diretas (inventario completo do ZIP principal)

Scanner proprio percorreu as **1.038 classes** do EXE, decodificando instrucoes
JVM (incluindo switches/wide), sem executar classes. Encontrou 11 referencias
diretas selecionadas: quatro chamadas e sete putstatic em SR/afQ.

- Save: `a.hz.sh()` chama `ae` com `SR.br()`; depois fecha seu dialogo.
- Outro caller de save: `forms.m.actionPerformed(...)`, com nome construido a
  partir de contador/preferences. Nao foi assumido como save normal do usuario.
- Load: `a.dR.actionPerformed(...)` chama `c(nome,false)`, descarta o retorno e
  fecha o dialogo. Hook de fechamento desse dialogo nao provaria load bem-sucedido.
- Fallback: chamada recursiva dentro da propria `c.a.c(...)`.
- SR/afQ recebem null diretamente somente no inicializador estatico nesse recorte;
  construtor de `c.a` cria raizes e load atribui as raizes decodificadas. O setter
  `a(best.f)` tambem atribui SR, sem callers diretos encontrados no inventario.

**Limites:** selecao por invocacoes/putstatic diretos para esses membros, nao
reflexao/JNI, chamadas indiretas em bibliotecas embarcadas nem toda a arvore de UI.
Ausencia de um putstatic null de unload nao significa ausencia de descarregamento:
fechar/trocar a UI pode deixar as raizes alocadas. Nao usar SR nao nulo como lifecycle.
Fingerprints de cada caller e Code ficam no TSV privado, ligados ao mesmo EXE.

## Reproduzir

### Extensao de fechamento/recriacao

Inventario ampliado percorreu as mesmas 1.038 classes e retornou **139 referencias**
selecionadas: construtores de c.a, setter/field da janela, `dispose()` e System.exit,
alem do I/O anterior. `dispose()` tambem inclui Graphics/dialogos: resultados sao
candidatos, nao 139 pontos de descarregamento.

Corroborado estaticamente por javap:

- `best.S` cria JFrame, configura **EXIT_ON_CLOSE (3)** e registra a janela por
  `best.n.a(JFrame)`. Somente um caller direto desse setter foi encontrado no ZIP
  principal; cC recebe null somente no inicializador estatico no recorte inspecionado.
- `a.eg.rq()` chama `System.exit(0)`. Nao ha retorno garantido nem evento
  WindowClosed garantido nesse caminho; callback de unload na EDT no hook de
  shutdown pode travar o encerramento e nao sera adicionado por suposicao.
- `c.a.<init>()` e chamado em `a.gq.mi()`, `best.h.mh()` e
  `forms.q.actionPerformed(...)`, substituindo raizes antes de mais inicializacao.
  O retorno desse construtor nao comprova carreira totalmente inicializada.
- Nenhum endpoint unico de CareerUnloaded/troca foi corroborado. O caminho da
  janela nao foi transformado; observacoes `roots-reset` permanecem diagnosticas.

Sonda de roots-reset testada nos metodos originais com frame sintetico; detalhes
em CAREER-PROBE.md. Necessario observar a UI real de criar/trocar/fechar antes de
definir lifecycle publico. Inventario nao cobre reflexao/JNI ou toda biblioteca.

```powershell
.\inspect-career-hooks.ps1
```

Requer JDK 17+ e EXE local conhecido. Gera fingerprints e bytecode privado em
`build/hook-analysis-*/`; tambem testa recusa de um EXE sintetico desconhecido.
Nenhuma rotina do jogo e executada; nao escreve em sav/options/recursos do jogo.

## Proxima entrega, antes de expor eventos

Investigacao posterior de criacao/navegacao e roteiro de corroboracao em
[LIFECYCLE-PATHS.md](LIFECYCLE-PATHS.md). Nenhum novo hook/evento habilitado.

1. Completar cadeia dos callers e descarregamento, distinguindo carreira nova,
   troca e fallback (callers diretos identificados, sem corroboracao de runtime).
2. Escolher tecnica em memoria e definir fingerprints fail-closed por capacidade.
3. Corroborar pontos na copia isolada: carregar valido/invalido/backup, salvar com
   sucesso/falha e mudar carreira, com identidade/threads/ordem registrados.
4. Testar sem alterar nomes/campos/hierarquia/schema de classes serializadas.
5. Implementar bus limitado com callbacks isolados e assinaturas encerraveis.
6. Somente entao habilitar capacidades de eventos e persistencia por carreira.

API publica deve usar nomes semanticos; os nomes ofuscados acima ficam somente
na investigacao/bridge interno. Nenhum evento foi anunciado como disponivel.
