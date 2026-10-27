# Sidecars de mod vinculados à carreira — 0.11.0

`ModContext.careerData()` oferece armazenamento externo separado por
`career UUID / binding UUID / mod ID / chave`. Requer `career.data.scoped`,
`data.read`, `data.write`, Java 17 e loader 0.11.0+. O root dedicado não grava
bytes no `.s22`, não concede autoridade sobre saldo/ledger e não comprova qualquer
efeito financeiro.

## Seleção explicitamente confirmada

1. Inicie com `--probe-career-hooks` e carregue o save existente pelo fluxo
   original inicializado.
2. No menu **Carreira e diários**, revise o UUID/binding já associado e selecione
   **Vincular dados de mods a esta associação** após confirmação.
3. Abra o mod. `careerData().current()` devolve um `Scope` apenas para essa seleção
   de sessão. Sem associação exata, inicialização observada ou seleção, retorna
   vazio/recusa. Nova sessão exige selecionar novamente.
4. Passe o mesmo Scope a `read`/`write`; ele é validado contra a associação e a
   revisão atual antes de cada operação. Salvar, trocar carreira, aceitar revisão
   ou limpar seleção invalida o Scope antigo. Consulte novamente depois da ação
   explícita de revisão.

Arquivos são chaves simples, não caminhos. Cada valor tem limite de
`CareerDataAccess.MAX_VALUE_BYTES` e sidecars usam quotas/locks/CAS/checksum do
armazenamento interno. O limite de namespaces por binding também se aplica.
Namespaces parciais, corrompidos ou com conflitos são recusados; não se apaga,
reancora, migra ou recupera automaticamente. O API não fornece lista global de
carreiras nem busca por nome/hash.

## Fork, cópia e dados antigos

Um fork explicitamente criado recebe UUIDs novos e portanto começa com sidecars
vazios. Não existe cópia automática de dados. Restauração conhecida mantém os
sidecars externos sem rollback implícito; mudança de revisão exige revisão e novo
Scope. Cópia sem associação/fork próprio não pode ler dados do original.

Os dados globais legados `ModContext.readData/writeData` permanecem independentes
e compatíveis. Importação para uma carreira deve ser uma ação explícita do mod,
que preserve os dados de origem, confirme o destino e não transforme uma
configuração legada em pagamento/ativação. Um mod Java continua não sendo sandbox.

## Limites

Sidecars não autenticam o mod host, não são transacionais com o save e não provam
`fsync`/power-loss. O escopo identifica a associação escolhida e a revisão
observada, não uma transação atômica do jogo. Não use esses dados como prova de
que um crédito foi salvo nem como razão para repetir um efeito incerto.
