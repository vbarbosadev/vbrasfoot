# Associacao unica por save/carreira — 22/10/2026

## Garantia e preservacao

O caminho exato normalizado do save possui uma unica associacao principal ativa.
Repetir CREATE_NEW ou fork para um destino ja conhecido reutiliza o handle existente,
sem escrever outro HEAD. Nao depende do clube humano: trocar de clube nao cria carreira.
Copias independentes e renomeacoes continuam explicitas; nao se identifica carreira
apenas por hash/nome. Renomear grupo consolidado exige migracao propria e e recusado
por enquanto, em vez de abandonar aliases silenciosamente.

`associations/.identity-registry/*.group` conserva principal e membros originais,
checksum e fingerprints dos HEADs historicos nao principais. Lock FileChannel entre
threads/processos cobre criacao, consolidacao e alteracoes de identidade. Escrita
forcada e instalacao atomica; intent sobrevivente ou arquivo pendente bloqueiam
novas operacoes para inspecao. Windows nao oferece fsync de diretorio via FileChannel;
isto nao e demonstracao de durabilidade sob queda de energia.

Os aliases ficam somente leitura. Membro removido/adicionado, metadados modificados,
namespace historico novo/alterado/removido, registro corrompido ou operacao interrompida
bloqueiam sem aceitar inventario parcial. Dados anteriores nao sao apagados/reancorados.

## Execucao nativa

O coordenador exige principal e agrega TODOS os membros: diarios, contratos financeiros,
witnesses de execucao, contratos de emprestimo e ledgers de continuacao. Referencias
de aliases usam `alias/<career>/<binding>/...`; ledger principal retém essas fontes
sem mudar bytes/politicas/ancoras dos contratos antigos. Overlap de chaves/jogadores,
UNKNOWN/EXECUTING, fontes divergentes e recibos alheios recusam. Inventario revalidado
antes da execucao e antes da reserva duravel. Fluxos genericos/legados ficam somente
leitura para grupos consolidados, evitando selecao de alias/vinculo vazio como bypass.

APPLIED continua exigindo jogador e tracker ativos exatos no save carregado. A
consolidacao administrativa NAO declara taxa salva nem resolve inclusao/descarte de
efeitos. Sem prova, novos pedidos continuam recusados com motivo curto.

## Flamengo na copia autorizada

Backup atualizado: `Documents/Brasfoot22-23-association-backup-20261022-135141`.
Consolidacao executada offline, sem jogo aberto, na pasta existente autorizada.
Resultado: **1 principal + 3 membros historicos preservados**.

- principal b8b2e5e1: Ganso e Rodrigo Castillo NO_EFFECT;
- cc3d71bd: Macarrao APPLIED;
- e1c2889d: vazio;
- e35cccf9: Matheus Reis APPLIED.

Principal escolhido deterministicamente, nao por estar vazio ou por maior permissao.
Os APPLIED dos outros membros continuam obrigatorios. Save Flamengo original tem
SHA256 `52837913efd1f59661f0d45a177033fcb497d3c8e64753b6908fe49a9872eeed` e a
inspecao anterior nao encontrou trackers. Logo identidade resolvida nao significa
emprestimos liberados: esses efeitos continuam sem recuperacao comprovada.

## Testes

Fixtures sinteticas: 24 criacoes concorrentes e quatro JVMs no mesmo caminho produzem
uma identidade; reinicio/idempotencia; consolidacao de quatro registros sem alterar
seus bytes; alias modificado/removido/adicionado; checksum corrompido; intent pendente;
fork repetido; evolucao apenas do principal. Teste de coordenador confirma quatro
ledgers revisados, dois APPLIED ainda ativos para verificacao no runtime e bloqueio
por acesso direto a alias ou alteracao de seu ledger. Regressoes do codec e das
politicas antigas permanecem obrigatorias. Symlinks e queda de energia nao foram
qualificados neste ambiente.

Ferramenta interna offline: `br.vbrasfoot.CareerAssociationConsolidationTool
--consolidate-identity-only <metadata-root> <save-directory> <save-name>`.
Exige backup e jogo fechado; nao constitui API publica nem recupera efeitos.
