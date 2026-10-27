# Encerramento de patrocínio — loader 0.13.0 / mod 0.5.0

O usuário confirmou a ativação pelo painel, crédito em caixa e nome do estádio
alterado em jogo. Isso é aceite humano dessa ativação, não prova universal de
inclusão financeira em todos os saves/restores.

## Entregue

`SponsorAccess.quoteEnd/end` prepara cotação e confirmação separada: clube,
saldo preservado, nome atual e nome original armazenado na ativação. Aviso explícito
se o nome atual difere do nome patrocinado (possível alteração manual).
Não inventa nomes para registros antigos sem auditoria.

`SponsorActivationStore` schema 3 preserva leitura de schemas 1/2 e adiciona uma
tentativa de encerramento com UUID, revisão do save, nome atual, snapshot financeiro
completo e estado CLAIMED/ENDED/NO_EFFECT/UNKNOWN. Ativação financeira original
permanece MEMORY_APPLIED e continua bloqueando replay, mesmo após encerrar.

Fluxo: seleção/associação/carga operacional verificadas -> contexto restrito
SPONSOR_END -> exclusão composta de I/O -> claim durável -> journal dedicado
PREPARED -> handler EDT -> journal terminal -> estado terminal do encerramento.
Timeout em voo mantém a exclusão do dispatcher; resultado tardio não promove UNKNOWN.
Nenhum handler financeiro é chamado: somente o setter original do nome do estádio.
Saldo/contabilidade, mesmo objeto do estádio e seleção são rechecados antes/depois.

A preparação especial do gate não limpa recibos: tolera somente APPLIED de
empréstimos e da exata ativação financeira selecionada; incerteza bloqueia.
É uma operação de nome não financeira, não uma reconciliação de empréstimos/saves.
Contextos genéricos e novos pagamentos permanecem conservadoramente bloqueados.

O painel oferece **Encerrar patrocínio**, desabilita após tentativa, arquiva a
projeção visual e limpa a logo selecionada ao terminar. Releitura do estado ENDED
oculta a logo mesmo se a escrita da configuração/feature falhar após o terminal.
Métodos da fachada são revogados no shutdown e conservam a mesma restrição de owner.

## Validação executada

- SponsorEndPipelineSelfTest: 36 checks (aplicado, recusado, exceção após efeito,
  falha de commit terminal, timeout/retorno tardio, exclusão de save, replay negado).
- SponsorOriginalRuntimeTest: 23 checks na cópia descartável marcada
  `sponsor-end-original-cdd79338740446a3a7484ac239eeba4b`: ativação original,
  encerramento original, saldo/contabilidade/capacidade/preços preservados,
  original save/reload com nome anterior restaurado e dinheiro preservado,
  crédito e encerramento não repetíveis após reabrir. **Autoridade artificial**;
  esse teste não aceita o caminho completo da UI/coordenador de encerramento.
- Gate 41; lifecycle/permissões 97; JSON SDK 40; store 47;
  pipeline ativação 101; portabilidade 33; coordenador 70;
  mod modelo/logo 28, layout 13, consulta clube 9.
- Fontes core/hooks/mod/testes compilados com Java 17; build completo não foi
  repetido nesta fase. Validação do conjunto instalado passou sem iniciar o jogo.

## Instalação

Atualizado somente `C:\Users\vinicius\Documents\Brasfoot22-23 - Copia`.
113 arquivos preexistentes de saves/dados conservaram hashes, nenhum pagamento
ou encerramento executado pela instalação. Backup loader/metadados:
`vbrasfoot/backups/before-sponsor-ending-b8c2f08423664d299f531da28c15ad84`.
Backup do mod:
`vbrasfoot/backups/mod-update-sponsor-manager-20261023-201538-a1868cb1af6e4cb8a9130eac8ae8b2d5/sponsor-manager.previous.jar`.
Build/artefatos: `C:\Users\vinicius\AppData\Local\Temp\opencode\sponsor-ending-545643d781db4b2f8634e2b1985ed1dc`.

## Teste humano pendente

Abrir pelo launcher nítido, carregar e vincular a carreira, selecionar STAKE,
encerrar e confirmar STAKE ARENA -> Maracanã, dinheiro mantido e logo removida.
Salvar no jogo, reabrir e verificar persistência e contrato ENCERRADO.
Não apagar histórico nem repetir após incerteza. Fechar sem salvar/restore pode
deixar histórico encerrado sem nome restaurado no save antigo; não há compensação
automática nem garantia universal para cópias sem sidecars/histórico restaurado.
