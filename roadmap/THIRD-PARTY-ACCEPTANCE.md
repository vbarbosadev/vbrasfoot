# Gate humano: criar um mod sem editar o loader

Este protocolo prepara o gate P5; nao declara MVP completo. Autor terceiro nao
e o agente que implementou SDK/exemplos, nem substituido por teste automatizado.
IA pode ajudar: o objetivo e comprovar que a documentacao publica basta e que
um humano revisa o codigo/instalacao. Nao enviar saves/EXE/dados privados a IA.

## Kit e ambiente

- SDK-only revisado: API0.5.0, validador/Javadocs, template, guias IA/compatibilidade
  e exemplos. Obter de canal confiavel; checksum nao autentica o autor.
- JDK17 ou21, Windows, pasta nova para projeto fora do checkout/SDK.
- Loader0.5.0 na copia local do Brasfoot22-23 conhecido para testar instalacao;
  jogo/licenca fornecidos pelo usuario, nunca redistribuidos no kit.
- Sem acesso ao fonte do loader/classes ofuscadas; exemplos sao fontes proprias.

## Missao minima independente

Usando apenas SDK-QUICKSTART, AI-MOD-AUTHORING, CALENDAR-BALANCE/Javadocs:

1. Criar um projeto a partir do template, com ID proprio/nome/versao proprios.
2. Implementar uma mudanca de interface real, nao apenas renomear JAR: filtro de
   elenco ou painel proprio de data/saldo. Declarar permissoes/capacidades minimas.
3. Compilar/testar contra API somente, validar --json JAR. Registrar comandos,
   Java/version e dificuldades. Codigo de mod e confiavel, nao sandbox; revisar.
4. Instalar em copia com jogo fechado sem sobrescrever mods, sem editar loader.
5. Abrir/carregar save existente, usar interface, fechar/reabrir painel, testar
   erro/referencia stale em fake ou fluxo oferecido pelo jogo, fechar normalmente.
6. Remover/desabilitar somente o mod proprio explicitamente e reabrir sem ele;
   save original deve continuar funcionando sem depender desse mod readonly.

Nao forcar troca que a UI nao oferece. Nao avancar partidas/apagar/restaurar saves
para simular gates. Fakes cobrem falhas tecnicas, nao alegar UI original testada.

## Relatorio a devolver

```text
Autor terceiro / data:
SDK/loader/commit ou hash / Java / Windows / resolucao e escala:
Ideia e diferenca implementada:
Guias usados / houve necessidade de consultar/editar loader?:
Build/tests/JSON (exit codes):
Instalacao/reinicio/uso/fechar-remover-reabrir:
Data/saldo comparados com UI, se usados:
Problemas e passos para reproduzir:
Tempo gasto e trechos do guia que faltaram:
Conclusao: passou / parcial / bloqueado, e limites:
```

Snapshots/logs/mensagens revisados pelo autor; nada enviado automaticamente.
Relatorio nao deve conter caminhos pessoais, saves, binarias ou segredos por padrao.
Manter evidencias privadas localmente; publicar somente resumo aprovado.

## Aceite delimitado e gates separados

Esse teste pode fechar usabilidade do SDK readonly somente se o autor completar
missao sem ajuda baseada nos internals. Nao fecha identidade/sidecars P3, eventos
reais P4, gameplay P6, performance ampla ou publicacao. O MVP originalmente definido
P0-P5+P7 ainda exige carreira persistente/eventos/documentacao compatibilidade.
Release experimental de leitura pode ter escopo menor, declarado explicitamente.

Antes de release/tag/assets: revisar arquivo exato/allowlist/licencas/origem,
changelog e relatorio matriz aplicavel, obter revisao de publicacao e nunca upload
wildcard de dist privado. Nao anunciar 100% por checks CI ou este protocolo.
