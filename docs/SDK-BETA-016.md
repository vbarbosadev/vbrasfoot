# SDK Beta 0.16.0 — contrato atual para autores

Status: experimental, API major 1, Java 17+. Perfil exato Brasfoot22-23; não é editor
genérico, sandbox Java, suporte Android nem execução de mods pelo EXE original.
Este documento atualiza as descrições históricas 0.11/0.12 nos demais guias.

## Distribuições

- SDK-only: API, validador sem jogo, Javadocs, templates e exemplos. Compilar fora do checkout.
- Loader para autores: `vbrasfoot-mods.cmd` seleciona explicitamente `--developer-mods`,
  ignorando os perfis exclusivos antigos sem apagá-los. Lê `vbrasfoot/mods` pela seleção
  normal e oferece gerenciador de mods e revisão de carreiras. Não há plugin embutido obrigatório.
- ZIP combinado do jogador não é o pacote para criar mods: seleciona só o patrocinador.

Instale apenas JARs confiáveis; permissões são limites da API, NÃO uma sandbox.
Não distribua o jogo, bibliotecas extraídas, saves, JDK/JRE ou classes ofuscadas.

## Empréstimos públicos

Declare `permissions=game.read,ui.register,loan.request` e as capabilities
`game.loan.request,query.scoped,query.player.references`.
Inicie com hooks (launcher para autores já os habilita).

```java
var loans = context.loans(); // retenha este serviço enquanto usar suas cotações
var career = loans.bindCareer(); // worker, pode pedir confirmação da identidade
var quote = loans.quote(career, playerRef, originRef, destinationRef);
// Mostrar jogador, clubes, taxa e prazo. Somente após confirmação humana explícita:
var result = loans.accept(quote);
```

Referências vêm de `GameAccess` scoped, não de números/objetos fabricados. O destino
precisa ser o clube humano exato; não é transferência arbitrária nem edição de atributos.
Reutiliza limites, elegibilidade, mediana/taxa quando aplicável, histórico, journals,
anti-replay e exclusão original de I/O existentes. Todos os autores compartilham esse
histórico central para não duplicar o mesmo jogador com outro mod. Sem proprietário
especial por ID, mas a permissão/capability são obrigatórias. Tokens não persistem,
uma tentativa consome a cotação inclusive em erro; encerramento do mod revoga o serviço
e o guard é conferido no efeito. Salve pelo jogo; não existe replay automático ou
garantia universal de inclusão em saves restaurados/copias sem histórico.

## Janela de transferências — PILOTO SOMENTE EMPRÉSTIMOS

Declare `transfer.windows`, `game.read` e, para configurar, `transfer.window.configure`.
`context.transferWindows()` fornece snapshot e alteração compare-and-swap por carreira:

```java
var windows = context.transferWindows();
var old = windows.current(career);
var period = new TransferWindows.Period(LocalDate.of(2027,1,1), LocalDate.of(2027,1,31));
// Exibir o período/escopo e confirmar antes de modificar:
windows.configure(career, old.revision(), new TransferWindows.Rules(true, List.of(period)));
```

Datas inclusivas, até 32 períodos não sobrepostos, ano explícito. Desabilitada permite
o comportamento anterior; habilitada sem períodos bloqueia todos os empréstimos.
Não altera a data atual. Configuração persistida em sidecars por carreira, independente
de salvar pelo jogo. Outro mod com revisão antiga não sobrescreve silenciosamente.
É conferida na cotação, execução e antes do efeito, nos caminhos públicos e internos
de empréstimos do loader. Se a data/regras mudarem, a operação pode ser recusada.

**Não controla compras, jogadores livres, pré-contratos, IA ou o EXE sem loader.**
Não é uma janela global de transferências concluída. Essa ampliação depende de mapear,
interceptar e testar cada caminho original separadamente; também falta aceitação manual
por autor externo. Retroceder/avançar artificialmente o relógio não está autorizado pela API.

## Exemplo e release

`examples/loan-window-sdk` compila somente contra a API e demonstra os dois serviços
com confirmação explícita. A UI do exemplo é simples, não faz parte das regras do jogo.
Teste numa cópia descartável e preserve `sav` e `vbrasfoot/data`.

Distribuição gerada/verificada localmente não é publicação oficial ou release estável.
Continuam pendentes aceitação humana externa, matriz operacional completa Java 17/21,
política de suporte/publicação e validação real de todas as falhas/restaurações suportadas.
