# Detalhes de jogadores — 0.9.0 experimental

Contrato aditivo: `GameAccess.scopedPlayerDetails(ClubRef, offset, limit)` e
capacidade `query.player.details` (minLoaderVersion=0.9.0, permissao game.read).
Nao substitui `Player`/`ScopedPlayerPage`; implementacoes antigas recusam pelo
metodo default sem fabricar dados. Nao depende de --probe-career-hooks.

1. Obtenha ClubRef via scopedClubs.
2. Consulte paginas de 1..500, offset >= 0; acima do total retorna vazio.
3. Use PlayerDetails.player para dados anteriores; skills e Optional.
4. Se skills estiver vazio, a carreira desativou habilidades individuais.
5. Em STALE_SCOPE/EXPIRED_REFERENCE/CLUB_MOVED/STATE_CHANGED refaca a consulta
   de clubes; nao recupere por ID numerico nem use fallback sem escopo.

Sete valores inteiros brutos: goalkeeping, tackling, passing, playmaking,
finishing, speed, technique. Sem normalizacao, promessa de escala, score de
scout, conversao de moedas ou semantica de potencial. Lista imutavel e DTOs
destacados; escopo e referencia sao temporarios, nao identidade persistente.
Leitura na EDT com verificacao de raizes/listas/referencia/configuracao antes
e depois, nao um snapshot atomico contra threads legadas que ignoram a EDT.
Salario, energia, caracteristicas e contrato ainda NAO expostos neste contrato.

## Evidencia e limites

Alvo e somente o EXE conhecido SHA256
2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c.
Javap privado da tela a.eg relaciona rotulos Goleiro/Desarme/Passe/Armacao/
Finalizacao/Velocidade/Tecnica aos getters best.F gK/gN/gM/gO/gP/gJ/gL,
respectivamente. best.F confirma getters diretos de int; a tela condiciona
exibicao a best.f.isHabilidadeIndividual. Sem reutilizar strings proprietarias
como instrucoes, sem redistribuir bytecode/decompilacao. REA JVM permanece
indisponivel conforme investigacao anterior; analise usa javap do JDK local.

Teste sintetico usa valores distintos, nao depende de valores iguais na base.
Teste original compara cada campo da primeira pagina de elenco aos getters
originais e recusa referencia apos reload. Isso nao e aceite humano visual
do scout ou prova de consistencia do grafo inteiro.

Exemplo SDK-only: examples/player-scout. Consulta manual com paginas de50,
ordenacao apenas da pagina atual, sem varredura mundial/sidecars/autosave.
