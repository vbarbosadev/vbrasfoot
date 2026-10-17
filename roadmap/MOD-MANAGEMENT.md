# Gerenciar mods — 0.4.2 experimental

Menu **vbrasfoot → Gerenciar mods (proxima inicializacao)** mostra JAR, ID, versao,
permissoes, estado atual, selecao futura, validacao do manifesto e pasta dos logs.
Tabela read-only com scroll; janela limitada ao desktop. Scan/escrita fora da EDT.
Abrir/atualizar nao grava. Habilitar/desabilitar exige selecionar e confirmar.

**Sem hot reload**: desabilitar ACTIVE nao muda esta sessao. Feche normalmente e
reinicie. Nao instala, baixa, apaga ou edita JAR/saves; nao desabilita dependentes
silenciosamente. Aba **Falhas da sessao** e colunas de resumo mostram erros
capturados pelo loader; use Atualizar para recarregar. P5 permanece parcial;
painel nao e sandbox. Exportacao diagnostica ainda pendente.

## Falhas recentes (0.4.2)

- Ate **128 registros globais por sessao**, descartando os mais antigos e contando
  evicoes. Resumos imutaveis com horario, sequencia, arquivo, ID, etapa, tipo e mensagem.
- Captura nas fronteiras do loader: inicializacao/construtor, acao de UI, callback
  de observacoes, overflow de assinatura e shutdown. Erro de acao/callback nao marca
  todo o mod como FAILED; estado original da sessao permanece fiel ao contrato.
- Overflow e detectado ao consultar status, atualizar painel ou descartar assinatura,
  uma vez por assinatura. Horario e o da deteccao, nao necessariamente da sobrecarga.
- Nao retém Throwable/Class, callbacks, classloaders ou objetos do jogo; textos
  limitados (mensagem 512 chars, arquivo/ID/tipo 160, mais marcador de corte).
- Somente memoria da sessao: reiniciar limpa o historico. Stack traces completos,
  quando registrados pelo caminho original, continuam nos logs privados. Falhas de
  inicializacao podem impedir abrir o painel; use logs e CLI/modo seguro.
- Nao intercepta todas as threads/timers criados pelo mod, erros engolidos por ele
  ou falhas internas do jogo. Lista vazia nao comprova ausencia de erros.
- Nao altera saves, nao envia nem exporta automaticamente. Mensagens podem conter
  caminhos, dados ou segredos produzidos pelo mod: revisar antes de compartilhar.
  Codigo Java e confiavel, nao sandbox; nao ha garantia contra callback malicioso
  ou JVM sem recursos. Nao provoque erros em carreira real para validar este painel.

## Linha de comando (jogo fechado)

```powershell
& 'C:\Jogos\Brasfoot22-23\vbrasfoot.cmd' --list-mods
& 'C:\Jogos\Brasfoot22-23\vbrasfoot.cmd' --disable-mod 'meu-mod.jar'
& 'C:\Jogos\Brasfoot22-23\vbrasfoot.cmd' --validate-only
& 'C:\Jogos\Brasfoot22-23\vbrasfoot.cmd' --enable-mod 'meu-mod.jar'
```

Use nome exato listado, inclusive maiusculas, nao ID/caminho. Selecao por arquivo
permite ignorar manifesto quebrado antes de carregar. Renomear nao transfere
desabilitacao; substituir conteudo no mesmo nome preserva selecao. Nomes ausentes
sao preservados. Apenas extensao `.jar` minuscula participa, como anteriormente.

Desabilite dependentes antes da dependencia; habilite dependencias antes dos
consumidores. Cada candidato valida IDs duplicados, conflitos, ciclos e obrigatorias
ausentes; erro recusa a operacao inteira, sem cascata. Se varios erros independentes
impedirem qualquer candidato valido, revise os JARs com jogo fechado; nao ha reparo
em lote. Validacao de manifesto nao inicializa codigo nem garante seguranca/classes.
Habilitar observador nao liga hooks: events.career.observed ainda exige a flag.

## Preservacao

Arquivo local `vbrasfoot/mods-selection.properties`, formato 1, UTF-8:

```properties
format=1
disabled.meu-mod.jar=true
```

Ausente: todos habilitados por compatibilidade. Existente vazio, corrompido,
desconhecido ou maior que 64 KiB recusa modo normal, sem habilitar tudo silenciosamente.
Seguro/check-save ignoram selecao/mods. Para reparar, feche o jogo, preserve copia
e revise explicitamente o arquivo; nenhum reset/restauro/exclusao automaticos.
Instalador/desinstalador preservam configuracao.

Staging no mesmo diretorio, force do arquivo e ATOMIC_MOVE exigido; sem fallback
nao atomico. Nao garante durabilidade de diretorio/quedas de energia. Snapshot
obsoleto e recusado se bytes mudarem; atualize/reabra painel. CLI usa runtime.lock;
writes dos paineis serializadas na JVM. Edicao manual concorrente ou filesystem
malicioso nao e transacao/sandbox. Links/junctions/aliases sao recusados.
Limites: 512 JARs/nomes desabilitados, nomes ate 160 chars sem caminhos/ADS.
Logs/erros podem conter caminhos privados; revisar antes de compartilhar.

## Testes e limites

Suite sintetica: dependencia/conflito/duplicata, invalido, Unicode/aliases,
limites/corrupcao, stale config, selecao e painel read-only. Smoke Swing: acao,
carga assincrona, estado separado da selecao, abrir nao grava, cleanup de janela.
Fixture do pacote testa processos novos/CLI/lock/seguro sem iniciar jogo/mods.
Nao equivale a UI real nem disable/reabrir/enable feito por humano.

Reproduzir: `build.ps1`, `test.ps1 -IncludeUi`, release para destino novo e
`test-mod-management.ps1 -Package <pacote>`. Ultimo copia EXE local conhecido para
fixture privada; sem saves, gameplay ou artefatos proprietarios redistribuidos.
