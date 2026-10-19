# Validacao delimitada de carga existente — preparacao e leitura de relatorios

Objetivo: obter evidencia do ramo real de inicializacao de uma carga existente,
sem confundir fixtures com JFrame sintetico com uma sessao humana pelo menu.
Nao e autorizacao de comandos, aceite de gameplay ou protocolo completo de P0.

## Por que a carga nao foi automatizada

A inspecao privada de c.a/best.f mostra que o ramo completo chama G, cria best.S,
chama cb/ap e outros componentes originais. ap pode chamar autosave ou caminhos
de progressao dependendo do save/configuracao. Executa-lo automaticamente como
se fosse getter read-only violaria a restricao de nao jogar/avancar/gravar por conta
propria. O interlock atual tambem recusa SAVE aninhado em LOAD; se ocorrer, registrar
a recusa como limite real, nao engolir ou alterar o save para fabricar positivo.

Nao mudar a carreira/configuracao, falsificar init markers, suprimir rotinas originais
ou contornar verificacoes de licenca para obter aceite. Esta etapa precisa de
interacao humana delimitada no jogo original usando uma copia completa isolada.

## Preparar sem iniciar

```powershell
.\build.ps1
.\prepare-load-validation.ps1 -GameDir '<origem conhecida>' -Preview
.\prepare-load-validation.ps1 -GameDir '<origem conhecida>'
```

O script reutiliza prepare-validation.ps1/VerifiedGameCopy: destino novo, perfil
exato, recusa de instancias do jogo abertas, links/junctions e copia com hashes.
Gera baseline.json apenas apos copia verificada. Nenhum jogo e iniciado, save
restaurado ou instalacao pessoal modificada. Precisa de espaco e permissao para
copiar os recursos/saves privados; nao publicar essa pasta.

Acrescenta home `load-probe` NOVO, sem mods, com copia do loader, e:
- `probe-load.cmd`: exports de ASM/sun.java2d necessarios e --probe-career-hooks;
- `inspect-load.cmd`: pede um caminho de sessao EXPLICITO, nao escolhe latest;
- `INSTRUCOES-CARGA.txt`: roteiro humano, sem partida/save/confirmacao de metadata;
- `load-validation.json`: identidade do loader preparado, sem automatic launch
  ou write authority. Sua existencia indica preparacao, NAO execucao aprovada.

Launchers normal/safe/original do preparador anterior continuam sendo alternativas
separadas, nao passos automaticos deste roteiro. So execute o launcher adequado.

## Sessao humana

1. Fechar outras instancias normalmente e abrir probe-load.cmd da COPIA.
2. Carregar um save EXISTENTE pelo menu normal, sem criar carreira.
3. Nao avancar calendario/partida, simular, salvar ou confirmar metadados externos.
4. No elenco, abrir vbrasfoot > Carreira e diarios; apenas Verificar. UUIDs podem
   continuar vazios. Registrar origem/geracao, visual/erros e sequencia observada.
5. Dialogo ou comportamento inesperado: registrar e cancelar quando possivel,
   sem aceitar por tentativa. Autosave/progressao original pode impedir qualificar
   este cenario; nao fingir que o loader impediu todo efeito apenas por nao clicar.
6. Fechar normalmente SEM salvar. Guardar a pasta de sessao informada no console.

Nao e necessario pedir outra carreira para validar infraestrutura. Se a carreira
existente nao permite esse caminho delimitado, documentar o bloqueio antes de
ampliar o experimento. Aceite humano continua distinto de markers automaticamente lidos.

## Inspecao offline, sem jogo

```powershell
java -jar .\dist\vbrasfoot.jar --inspect-load-validation '<load-probe/logs/session-ID encerrada>'
```

Tambem pode usar inspect-load.cmd da copia. Nao executa classes do jogo/mods,
nao exige instalacao do jogo, nao cria home/cache/logs/backup ou altera arquivos.
Retorno0 significa apenas QUALIFIED_LOAD_OBSERVED; outros estados retornam erro
na CLI para nao serem considerados positivos por automacao.

Le somente sete arquivos conhecidos, ate128KiB cada, em caminho sem redirects:
session.properties, career-lifecycle.properties, decoded-save-provenance.properties,
original-io-interlock.properties, creation-entry.properties, probe-delivery.properties
e termination.txt. Rele e compara bytes antes de aceitar. Nao pesquisa outras
sessoes, arquivos de save ou logs brutos, e nao imprime conteudo arbitrario/paths
privados dos reports. Leitura fora da EDT. Campos duplicados, invalidos, formatos
desconhecidos, profile divergente ou mistura de lifecycle sessionIDs sao recusados.

Reports de provenance/interlock agora incluem o ID da mesma sessao do lifecycle.
Sessoes antigas sem esse campo sao INCOMPLETE; nao inferir vinculo retroativamente.

### Resultados

- **QUALIFIED_LOAD_OBSERVED:** markers coerentes de carga existente com ramo init,
  stream/geracao atual, lifecycle ACTIVE/healthy, exclusao drenada e diagnosticos
  sem perdas; sem save tentado ou tentativa de criacao observada neste roteiro.
- **NOT_QUALIFIED:** arquivos validos mas markers faltam, foram invalidados, houve
  save/recusa/perda/interferencia ou trabalho em voo. Nao autoriza retry/escrita.
- **INCOMPLETE:** relatorios ausentes/incompativeis/inseguros/malformados/misturados
  ou alterados durante leitura. Nao inventar contadores default nem eleger sessao.

O positivo nao comprova consistencia global, EDT drenada em todos os momentos,
ausencia de todos os efeitos nao instrumentados, durabilidade, grafo comparado ao
EXE standalone ou aceite visual. A propria mensagem declara write authority ausente.
Arquivos podem mudar apos a leitura; relatorios nao sao assinados ou uma prova
contra codigo Java malicioso que falsifique hooks/arquivos. Nunca instalar contexto
de comandos a partir desse resultado offline.

## Testes e gates seguintes

Self-tests usam SOMENTE reports/markers sinteticos, incluindo roundtrip dos writers
reais de ProbeSession/SessionDiagnostics e CLI sem jogo/home. Positivo nesses testes
nao e evidencia de init original ou aceite humano. CI continua target-free.

Depois de uma sessao humana delimitada, revisar evidencia/limites antes de conectar
contexto operacional. Reconciliacao por operacao/revisao/save-as/restore, semantica
financeira, calendario/mercado e API publica de escrita continuam pendentes.
