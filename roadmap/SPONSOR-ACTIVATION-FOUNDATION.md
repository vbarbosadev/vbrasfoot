# Patrocínio: fundação interna para tentativa única

## Atualização em desenvolvimento — loader 0.12.0 / mod 0.4.0

O texto abaixo registra a fundação anterior (0.11.0/0.3.0), não o estado atual de exposição.
Agora `SponsorService` conecta a cotação/confirmação ao coordenador operacional,
somente para `br.vbrasfoot.sponsor-manager` com `sponsor.activate` e
`game.sponsor.activate`. Configurar/importar continua sem efeitos.
O schema 2 registra os nomes anterior/proposto do estádio e lê schema 1.
Uma fence composta exclui I/O original desde antes do claim até o commit terminal;
em timeout, a exclusão do dispatcher também permanece enquanto o handler está em voo.
Seleção de carreira revogada ou trocada invalida a cotação, inclusive na EDT antes do efeito.

`SponsorOriginalRuntimeTest` passou 14 verificações numa cópia marcada descartável:
crédito original de 2.000.000, setter original do nome, mesmo estádio/capacidade/preços,
persistência de saldo/contabilidade/nome após save/reload original e replay recusado.
**Autoridade artificial nesse teste:** ele não aceita a interface nem o coordenador
de carga operacional. A validação completa desses caminhos ainda está pendente.
Nenhum efeito foi aplicado à carreira pessoal; instalação continua em 0.11.0/0.3.0.

O histórico aplicado/incerto bloqueia novas ativações e pode bloquear empréstimos
e escritas genéricas até reconciliação. Não há compensação ao fechar sem salvar
nem garantia universal para cópias sem sidecars ou histórico antigo/restaurado.
`MEMORY_APPLIED` nunca significa inclusão comprovada no save atualmente carregado.
Veja o [README atual do mod](../examples/sponsor-manager/README.md).

## Entregue, ainda sem pagamento público

`SponsorActivationStore` mantém tentativas em um root dedicado
`sponsor-activations`, sob carreira/binding explicitamente fornecidos pelo loader.
Não descobre carreira por clube, nome, caminho ou hash. O sentinel do envelope é
constante apenas para ler o histórico quando a revisão do save muda; **não é uma
revisão real nem prova de inclusão do pagamento**. Cada payload conserva a revisão
original, clube exato, saldo/ledger anteriores, valor, owner, UUID do patrocinador,
UUID da tentativa e digest dos termos revisados.

- Chave financeira determinística pelo UUID do patrocinador; mudar a revisão ou
  os termos não cria uma segunda tentativa para esse UUID no mesmo binding.
- Estados: `CLAIMED`, `MEMORY_APPLIED`, `NO_EFFECT`, `UNKNOWN`.
- Nenhum estado significa contrato ativo salvo. APPLIED da camada financeira é
  registrado somente como `MEMORY_APPLIED`.
- Claim aplicado/incerto bloqueia outras ativações. `NO_EFFECT` libera somente
  um patrocinador distinto, nunca replay do mesmo contrato.
- Lease de conclusão pertence à instância que comprometeu o claim e é consumida
  mesmo se o commit terminal falhar. Reabrir, copiar metadados ou conhecer o UUID
  não recria a lease.
- Quota limitada a 32 tentativas, sem eviction, limpeza, migração ou restauração
  automática. Store incompleto/corrompido não vira histórico vazio.

`SponsorActivationPipeline` é **interno, não conectado a Main/ModContext**. Precisa
de contexto e handler independentemente qualificados e de confirmação explícita
do caller. Sua ordem é claim do patrocinador -> payload financeiro -> journal
PREPARED -> witness ARMED -> handler na EDT -> terminais do witness/journal ->
observação terminal do patrocinador. Reutiliza o dispatcher financeiro existente;
não chama o adaptador original por fora dele nem concede owner/permissão pública.

Se houver exceção, journal/witness incerto ou falha ao registrar o terminal, o
resultado é incerto e nunca é reenfileirado. Timeout em voo mantém a exclusão
original de I/O até o handler retornar; um retorno tardio não promove UNKNOWN.
Não há transação atômica entre os roots nem com o save.

O coordenador existente consulta o novo root antes de preparar contexto genérico
e no caminho de empréstimos, incluindo membros históricos de grupos. Assim, uma
tentativa de patrocínio aplicada/incerta não pode ser ignorada por outra rota de
escrita. Root ausente é uma consulta read-only e não cria arquivos.

## Cobertura automatizada

Fixtures próprias sem runtime original verificam:
- isolamento por carreira/binding e payload original imutável entre revisões;
- bloqueio de replay, mudança de termos, lease estrangeira e estado terminal;
- falhas injetadas antes/depois do HEAD e na conclusão, sem promoção de orphans;
- cópia **com histórico intacto** preservando tombstones, sem recriar leases;
- ordem de commits antes do efeito, contagem de invocações e timeout tardio;
- interoperação com os bloqueios do coordenador existente.

Isso não prova power-loss/fsync, execução no jogo, persistência financeira ou
exactly-once. Cópia de `.s22` sem sidecars, restauração de sidecars antigos,
exclusão de histórico, fork vazio e associação nova permanecem lacunas explícitas.
O root não autentica o histórico contra Java malicioso. Os métodos de escrita
continuam internos e não devem ser expostos com base nesses testes.

## Dados por carreira adicionados depois desta fundação

SDK/loader 0.11.0 acrescenta `CareerDataAccess`: sidecars separados por mod e
associação explicitamente selecionada. A seleção exige sessão probe, save existente
observado e confirmação no painel Carreira e diários. Forks começam vazios; saves
alterados exigem aceitar a revisão antes de novo escopo. O sponsor-manager 0.3.0
guarda configurações e seleção visual nessa camada e oferece importação legada
global explícita, sem ativação. A logo também carrega o escopo efêmero da consulta
da carreira atual para não aparecer em outro clube/carga com os mesmos nome/ID.

## Falta para liberar a versão ativação + crédito único + logo

1. Qualificar inclusão do efeito no save e recuperar o vínculo por revisão,
   restore, backup input, save-as/fork e cópia sem presumir que MEMORY_APPLIED é
   INCLUDED. Os gaps de `FinanceSerializationReview` continuam bloqueantes.
2. Inventariar o histórico de ativação na revisão apresentada ao usuário e guardar
   seus tokens no objeto de autorização; fechar o protocolo de exclusão até o
   commit terminal de todos os roots. O pipeline interno isolado não basta.
3. Somente então conectar owner/handler estreitos ao coordenador e SDK, estados
   de contrato, confirmação exata de valor/clube e logo ativa por carreira.
4. Validar o efeito na categoria original e o save/reopen numa cópia completa
   identificada e autorizada. Não usar a instalação pessoal para qualificar
   escrita ainda bloqueada.

Nenhuma chamada financeira original ou mudança em saves pessoais foi feita. O mod
0.3.0 foi instalado na pasta pedida para testar somente dados por carreira e a
prévia visual; pagamento segue desabilitado. Loader 0.11.0 e mod anterior foram
preservados em `vbrasfoot/backups`.
