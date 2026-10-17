# Exportacao revisavel — 0.4.3 experimental

Relatorio **TXT UTF-8 local**, nao ZIP nem upload. Nenhum envio, clipboard ou
abertura automatica de rede; compartilhar continua sendo uma acao manual do usuario.

## Passo a passo

1. Abra a copia pelo launcher, carregue carreira e entre em **vbrasfoot →
   Gerenciar mods → Preparar diagnostico**.
2. Revise o texto na janela **Revisar diagnostico**. Nada foi gravado ainda.
3. Por padrao, nomes/IDs/versoes/permissoes e mensagens de falha ficam **excluidos**.
   Mods aparecem como mod-1, mod-2 etc. Ha apenas versao do loader/API, perfil
   esperado, versao numerica do Java, familia/arquitetura do SO, modo, estados e
   contadores agregados de falhas.
4. Se necessario, marque explicitamente identidade dos mods e/ou detalhes de
   falhas/validacao. Clique **Gerar previa com opcoes** para aplicar. Mensagens
   podem conter caminhos, nomes de carreira ou segredos; nao ha anonimizacao
   garantida quando detalhes sao incluidos. Gerar novamente pede confirmacao
   antes de descartar suas edicoes/redacoes.
5. Edite/remova qualquer dado que nao deseja compartilhar. **Salvar texto revisado**
   grava exatamente o texto exibido, nao regenera o relatorio. Checkboxes so se
   aplicam ao Gerar previa, nunca implicitamente no salvar.
6. Confirme a gravacao local. Destino exclusivo:
   `vbrasfoot/diagnostics/diagnostic-<UUID>.txt`. Cada save explicito cria um arquivo
   novo; nada e sobrescrito. A interface informa o caminho quando conclui.
7. Revise tambem o arquivo salvo antes de compartilhar manualmente. Nao precisa
   salvar a carreira nem provocar falhas para testar essa funcao.

Fechar/cancelar **antes** da confirmacao de salvar nao cria arquivo ou pasta.
Depois de confirmar, fechar a janela nao cancela com garantia a gravacao ja iniciada.
Preparacao/leitura do inventario e salvamento ocorrem fora da EDT; previa e janela
pertencem ao loader e sao descartadas com a UI.

## Conteudo e privacidade

- Allowlist de dados estruturados, nao varredura/compactacao da pasta do jogo.
  O gerenciador le sua selecao/manifestos para o inventario; nenhum conteudo de
  JAR/config e anexado ao relatorio.
- Nao le/anexa stdout/stderr/loader.log, session.properties, argumentos de linha
  de comando/JVM, variaveis de ambiente, java.home, usuario/PID/session UUID,
  saves/backups, EXE, classes, recursos ou dados persistentes de mods.
- Versao Java e numerica e SO/arquitetura sao enums; nao copia propriedades brutas
  que possam conter caminhos/strings privadas. Hash no relatorio e o **esperado**
  do perfil fixo, nao hash de save nem nova verificacao do executavel.
- Identidade e detalhes sao opt-ins independentes. Detalhes incluem tipo/mensagem,
  horario/etapa de falha e erro de validacao limitado, inclusive mods removidos do
  inventario; podem revelar identidades mesmo com checkbox de nomes desligado.
- Texto de origem opcional tem controles/bidi/quebras escapados e limites de campo.
  Isso evita falsificar linhas do resumo, **nao** remove todos os segredos.
- Previa editada pode conter qualquer texto que o usuario incluir. Revisao humana
  e indispensavel; relatorio minimo tambem nao e promessa de anonimato absoluto.
- Snapshot nao e transacional nem live: representa inventario/estados/falhas lidos
  no preparo, nao carreira consistente. Para novo snapshot, prepare novamente.

## Escrita e limites

- Ate 512 mods, 128 falhas e **256 KiB UTF-8** por relatorio. Tamanho/Unicode invalidos
  sao recusados antes de criar pasta/arquivo; conteudo nao e truncado silenciosamente
  apos revisao. Detalhes excepcionalmente grandes podem exigir menos opt-ins.
- Destino fixo dentro do installRoot validado; sem seletor de caminho arbitrario
  que permita sobrescrever save. CREATE_NEW, NOFOLLOW_LINKS e verificacao de
  ancestors/junctions/aliases. Arquivo existente recusa, inclusive colisao de UUID.
- Force no arquivo apos escrita. Nao oferece transacao atomica/durabilidade de
  diretorio em queda de energia: encerramento forcado pode deixar relatorio parcial.
  Falha capturada tenta remover somente o arquivo novo criado pela operacao.
- Nenhuma restauracao, exclusao de saves, reset de config ou remocao de logs. Pasta
  diagnostics e preservada pelo instalador/desinstalador; revise antes de removê-la.
- Filesystem malicioso/edicao concorrente de paths nao e sandbox. Java de mods e
  confiavel; nao ha garantia de isolamento contra um mod deliberadamente hostil.

## Evidencia e pendencias

41 checks sinteticos: allowlist/opt-ins independentes, sentinelas privadas ausentes,
snapshots imutaveis, controle de Unicode/bytes, texto revisado exato, CREATE_NEW,
destino nao-diretorio e junction NTFS recusados, arquivos originais preservados.
Smoke Swing cobre preparo/cancelamento sem escrita, opt-in, edicao, confirmacao e
save exato em fixture propria, alem do descarte das janelas. Nao e teste humano
na UI real nem autorizacao para exportar dados privados automaticamente.

Exportacao de sessoes anteriores/logs brutos nao faz parte desta entrega. Se a
inicializacao falhar antes do painel, consulte logs locais e revise-os manualmente.
P3/lifecycle completo/gameplay e gates restantes de P5/P7 continuam pendentes.
