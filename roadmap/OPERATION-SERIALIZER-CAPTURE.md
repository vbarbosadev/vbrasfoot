# Captura de alvos por operacao nas fronteiras do serializer original

Incremento experimental apos FINANCIAL-MUTATION-OBSERVATION.md. Correlaciona UUIDs
novos com amostras de roots/clube/ledger, mas NAO declara inclusao no save nem
finaliza APPLIED/UNKNOWN. Nenhum mod/instalacao/schema/EXE/save pessoal alterado.

## Registro somente no caminho testemunhado

executeWitnessedFinance preserva o ExecutionPermit opaco da preparacao fresca do
journal. Depois de handler APPLIED, roots iguais antes/depois e receipt APPLIED do
gate, solicita captura diagnostica ao plano, ainda na EDT. Plano original exige
request/permit com career/binding/owner/save/intent exatos, uso one-shot e checkpoint
de execucao. Legado, rejeitado, retorno incerto ou sem prova de journal nao registra
alvo por esse caminho. Falha de diagnostico nao modifica efeito ou receipt original.

Esse registro nao substitui MEMORY_APPLIED duravel: ocorre ANTES do witness/receipt
terminal serem gravados. Uma falha/timeout posterior pode deixar historico incerto
mesmo com registro em memoria. Nao transformar callback em prova de commit ou
autoridade corrente. Main continua sem owner/handler de gameplay para mods.

FinanceSerializationTracker tem ate32operacoes, UUID/intent/checkpoint escalares e
referencia FRACA ao plano, que ja guarda clube/ledger/roots fracos. Nao retem o grafo
nem runtime por callback forte. Se plano e coletado, amostra e TARGET_UNAVAILABLE;
nao procurar outro clube por nome/ID/saldo. Capacidade esgotada ou registro duplicado
e explicitamente marcado, sem apresentar lista parcial como cobertura completa.

## Fronteiras reais, sem novo acesso a arquivo na EDT

HookProbe captura janela antes da primeira root realmente entregue a
Kryo.writeClassAndObject e a encerra depois do retorno da segunda root. Usa os
mesmos sites fingerprintados ja existentes; nao substitui serializer/dados/bytes.
Tracker amostra alvos fora do proprio lock e apenas na EDT. Off-EDT retorna
WRONG_THREAD sem acessar o alvo. Getters internos sao do adapter original confiavel,
nao callbacks de mods. Nenhum file I/O/wait/loop ilimitado e introduzido.

Plan.sample exige:
- root primaria serializada e roots correntes EXATAMENTE iguais as weak roots da
  execucao observada;
- mesmo clube original no indice capturado e exatamente uma ocorrencia em P/aN,
  nome/humano coerentes; limites100000clubes/64humanos, sem aceitar lista parcial;
- MESMA referencia de ledger (nao apenas valores iguais);
- saldo e14campos numericos completos iguais ao expected daquela operacao.

O writer original pode reatribuir IDs numericos. Amostra usa referencias de objetos
da MESMA encarnacao, nao fabrica locator persistente novo ou heranca entre revisoes.
Mudanca de root/indice/membership/nome, ledger ou estado e explicitamente recusada.
Operacao anterior alterada por outra posterior aparece STATE_MISMATCH mesmo que a
ultima operacao tenha amostras coincidentes. Isso NAO declara que a anterior foi
revertida/nao incluida: apenas que seu expected isolado nao e o estado amostrado.

Status: MATCHED_EXPECTED_STATE, TARGET_UNAVAILABLE, ROOT_MISMATCH, LEDGER_MISMATCH,
STATE_MISMATCH, WRONG_THREAD ou READ_FAILED. Identidade/contador de writes diretos
separam UNAVAILABLE/OBSERVED_INTERFERENCE/NO_OBSERVED_DIRECT_WRITE entre execucao e
inicio da janela. Sem coverage de ambas classes/tracker saudavel nao ha positivo.

## Lifecycle, report e conservadorismo

Load/reset/root assignment/detach limpam identidades antigas e invalidam janelas.
Registro novo durante janela invalida captura. Finish e one-shot, verifica owner/
revision/root e nunca reutiliza janela com outra identidade. Close e revogacao,
nao restauracao. Nenhuma carga reencontra operacoes antigas por igualdade de saldo.

WrittenSaveProvenance conserva Capture imutavel junto de destino/hash/size/roots e
checkpoints ja existentes SOMENTE se a sequencia original inteira foi observada.
Save parcial/aborted nao ganha binding positivo. Snapshot de captura nao muda o
criterio de bytes, nao reancora journals/contratos/witnesses e nao concede lease.

ProbeSession possui/fecha subscription do tracker e exporta somente UUIDs/status/
continuity/count/capacity no report local escrito antes do detach. Nao exporta key,
payload, saldo, nomes, paths de saves ou objetos. Coverage e explicitamente de
amostragem de alvos, NAO campos emitidos. operationInclusionProven=false e
writeAuthority=false continuam obrigatorios mesmo se tudo estiver MATCHED.

## Limite critico: fronteira de root nao e emissao de campo

Incremento posterior: [argumentos numericos do Kryo](FINANCIAL-NUMERIC-EMISSION.md)
observa argumentos de15campos apos Output.writeInt/writeLong retornar. O limite
abaixo continua valido para as AMOSTRAS de root; a nova observacao ainda nao
comprova a aresta clube/ledger, cobertura causal completa ou inclusao duravel.

MATCHED antes/depois nao prova quais campos o Kryo escreveu, qual serializer foi
selecionado, se um campo e transient/custom ou se uma escrita nao instrumentada
ocorreu no meio. Cobertura42PUTFIELD nao abrange Unsafe/reflexao/decoder. Nao ha
INCLUDED neste componente nem commit duravel de testemunho de inclusao.

Captura e diagnostica/em memoria; report de shutdown nao substitui protocolo
duravel/crash-safe por operacao. Ainda falta capturar os objetos/campos na rota
efetivamente usada pelo serializer, qualificar mecanismos de mutacao pertinentes,
persistir a prova ligada aos bytes e recuperar sob interrupcao/restore/save-as.
Historicos antigos sem prova e execucoes incertas continuam bloqueados.

## Evidencia

Testes sinteticos cobrem status, UUID/intent, changes de ledger/root/estado,
registro/invalidacao entre fronteiras, janela foreign/one-shot, EDT, target failure,
cobertura mutacao ausente/interferente, capacidade/duplicata e exportacao sem key/
valores/paths. Planos mantidos vivos na fixture por reachabilityFence nao mudam a
politica FRACA do tracker em producao.

Fixture original financeira: tres planos APPLIED testemunhados sao associados aos
UUIDs do journal durante a serializacao de roots reais. Os dois primeiros acusam
STATE_MISMATCH/interferencia porque operacoes posteriores mudaram seus expected;
o ultimo e amostrado MATCHED antes/depois. Planos legados/rejeitados nao aparecem.
Writer/reload e esquema original continuam testados; autoridades sao ARTIFICIAIS,
nao aceite do contexto de Main, UI/standalone ou exatamente-uma-vez.
