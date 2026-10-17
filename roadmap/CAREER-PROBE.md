# Proxima entrega P4 — sonda opt-in de carreira

Estado: **diagnostico experimental**, nao API de eventos ou aceite completo de P4.
Nao habilita sidecars/P3, descarregamento ou gameplay. Use somente copia isolada.

## Integracao

`LegacyRuntime` transforma somente `c.a` durante `defineClass` quando a opcao
`--probe-career-hooks` e solicitada. EXE, cache extraido e schemas permanecem
originais; nao modifica nomes, campos, descriptors ou hierarquia. Fingerprint
SHA-256 completo da classe e contagem exata de sites devem coincidir; falha recusa
a sonda antes de definir a classe, sem fallback silencioso para hooks por tentativa.

Tecnica: ASM interno do JDK, explicitamente exportado **somente para a sonda**.
Nao e dependencia publica do SDK, nem contrato estavel entre versoes Java. Sonda
restrita a Java 17/21; modos normais nao precisam desse export. Sua substituicao
por dependencia externa com licenca/versionamento revisados pode ser necessaria
antes de liberar hooks de producao. A toolchain usa `-source/-target 17` apenas
para essa classe; restante continua `--release 17`.

Callbacks internos sao observacoes diagnosticas, com falhas isoladas; nao executar
trabalho de mods/sidecars neles. Nenhum objeto legado vai para o record de observacao.
Nomes de arquivo e threads entram no log privado: revisar antes de compartilhar.

## Semantica observada

- Load: marker depois do Input.close original, conclusao apenas se metodo termina
  com true e raizes decodificadas. Argumento null nao vira sucesso de leitura.
- Fallback: tracking por profundidade na thread; backup interno bem-sucedido gera
  uma observacao externa com `fallback=true`, mesmo se retorno externo for false.
- Save: sucesso primario depois do Output.close; copia `.sbck` registrada a parte.
  Excecao engolida pelo jogo nao e tratada como sucesso primario.
- Throwable nao capturado pelo jogo e repropagado depois de limpar tracking.
  Hook nao cancela, nao restaura e nao corrige falhas do jogo.
- Sucesso aqui nao e garantia de durabilidade/fsync ou de `.info` escrito.
- Nenhum CareerUnloaded, MatchFinished ou evento publico foi habilitado.

## Validar sem iniciar UI

```powershell
.\run.ps1 --probe-career-hooks --validate-only
```

Verifica schema da classe transformada na JVM, sem inicializar carreira/mods.
Nao comprova execucao dos hooks.

## Teste automatizado de I/O original

```powershell
.\test-career-hooks.ps1
```

Cria fixture nova privada no Temp/OpenCode; copia EXE/save local para `input.s22`.
Executa rotinas originais na EDT com frame sintetico registrado para evitar
inicializacao completa da interface. Grava somente nomes novos na fixture.
Falhas induzidas usam diretorios nos caminhos novos, nao corrompem saves do usuario.
Resultados incluem shape/schema, leitura, save/backup, releitura, falha primaria,
falha de backup, fallback recursivo e argumento null. EXE/input/origem sao hashados.

Limitacao importante: frame sintetico faz a rotina pular a inicializacao completa
de UI. Nao valida jogo novo, troca/descarregamento de carreira, ordem dos eventos
com UI real, reabertura do EXE ou comportamento em gameplay. Sonda e observador
tambem tem testes sinteticos sem classes proprietarias (fixtures sao codigo proprio).

## Sessao guiada em copia completa

Depois de gerar e instalar um pacote novo somente na copia completa:

```powershell
& '<copia>\Brasfoot22-23\vbrasfoot.cmd' --probe-career-hooks
```

Carregar carreira, consultar, salvar, fechar e reabrir no EXE original da copia.
Conferir `career-hook-probe=Observation[...]` nos logs, uma conclusao por operacao,
threads/ordem e save resultante. Nao usar a pasta original para sondas de falha.
O modo seguro explicitamente recusa a opcao; `--check-save` tambem, mantendo o
verificador offline sem transformacoes.

Sessao guiada ja corroborada para carregar/salvar Flamengo e reabrir no EXE
original da copia (confirmacao do usuario). Log mostrou load em AWT-EventQueue-0
e save em **Thread-1**, ambos completos e save com backup; stderr vazio.
Nao assumir EDT para todos os hooks. Save sem avancar manteve hash, reforcando
que mudanca de timestamp/hash nao e criterio de evento. Detalhes em VALIDACAO.md.

## Pendencias antes da API

Corroborar UI real e fallback/falhas em todos os caminhos, mapear unload/troca,
fixar momento de commit e geracao de carreira, implementar filas/callbacks limitados
e testar compatibilidade do save produzido. Somente depois anunciar capacidades
publicas de carregar/salvar e prosseguir com P3. Contrato de SDK ainda sem eventos.
