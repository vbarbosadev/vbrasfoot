# Mensagem nativa no save, EXE intacto — loader 0.15.0

## Resultado

Publicação cria somente `components.as` original, na lista `best.al.lQ()` do coach
do clube humano exato. Sem dinheiro ou naming rights na publicação. Usa apenas campos
existentes: RU=9 (prefixo informativo Agradecimento), RV=-1 (sem template), RW=assunto,
RX=texto HTML-escapado + status, data=data do jogo, Sb=marcador/UUID. RZ=false,
Sc=-1 e oq=-1 impossibilitam o caminho de multa. Nenhuma classe/campo novo no Kryo.

O save original grava essa coleção. Sem mod, o renderer e a central nativos exibem
o texto/status sem ação de recebimento ou multa. Com mod, o binding reutiliza a linha
por marcador em vez de duplicar/apagar a mensagem nativa ao fechar a tela.

Mensagem recebida é atualizada dentro do handler da ativação, no mesmo EDT e exclusão
de I/O original do crédito e do estádio. Se a atualização falhar após efeito, o resultado
é conservador/incerto; nunca autoriza repetir. Atualizações informativas isoladas usam
validação de scope/proveniência e gate sincronizado, recusando I/O/commands em andamento.

## Sem salvar / dados legados

O inbox filtra sidecars pela presença da mensagem no coach carregado e o quote/execute
revalidam essa presença. Recarregar sem salvar não reconstrói mensagens a partir do disco.
Cancelamento não reinsere mensagens ausentes. Configurações/sidecars de contratos e
ledger financeiro continuam duráveis e conservadores: não são rollback transacional
do save. Ofertas órfãs podem ser canceladas antes de nova oferta, desde que sem tentativa
financeira. Não existe migração automática dos avisos sidecar-only antigos para o save,
nem reset de pagamentos aplicados/incertos. Não reivindicar rollback universal ou replay
seguro quando restauram save/sidecars antigos.

## Validação

- SponsorDeferredOriginalRuntimeTest: 31 verificações, incluindo criação no coach,
  descarte sem salvar, não ressurreição via sidecar, rejeição de recebimento ausente,
  save/reload pending, atualização paga, crédito exato, deduplicação e callbacks originais.
  Marcadores do decoder e confirmação são artificiais; saves e métodos originais reais,
  somente cópia descartável. Scanner real da central também foi exercitado.
- OriginalSponsorMessageReadTest: duas JVMs separadas, classpath SOMENTE game.jar e
  dependências originais + um harness de reflexão. SponsorService ausente do classpath.
  Leu pending.s22 e input.s22, mostrou texto/status na a.bn ORIGINAL, verificou saldos
  antes/depois e botões Aa/zZ ocultos. Capturas standalone-pending/paid.png.
  Não é aceite manual de lançar o PE diretamente; verifica decoder/renderer originais
  sem loader, plugins ou classes modificadas. EXE pessoal nunca foi executado/escrito.
- Regressões: lifecycle/fachada 102, offer 17, binding 13, ativação 101, ending 43,
  continuação sponsor 5, identidade 23, coordinator 70, gate 42, listado 20, loan UI 30,
  continuação loans 16+15, grouped 11, combinado 10, portabilidade 33.

Build: `%LOCALAPPDATA%/Temp/opencode/native-save-messages-917348726c0b42b496e9fadb9dbbe6c6`.
Fixture final: `%LOCALAPPDATA%/Temp/opencode/native-save-final-4d1bc8beb39144b18aa3b375086ea8b8`.
Não se repetiu o build completo. Nenhuma instalação, EXE, save ou metadata pessoal foi alterado.

Análise das classes originais com javap/local ZIP; REA não estava disponível na sessão,
sem Evidence REA. Perfil EXE SHA-256 permanece
`2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.
