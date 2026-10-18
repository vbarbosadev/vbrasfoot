# Persistencia por carreira — proposta, NAO capacidade disponivel

Progresso interno: CareerSidecarStore/CareerCommandJournal implementam envelopes,
CAS,previous explicito,intents e testes sinteticos. Entrega posterior adiciona
SaveFileObserver/CareerSaveAssociations para decisoes explicitas sobre bytes de
save e metadados externos;veja EXPLICIT-SAVE-ASSOCIATIONS.md. Nao estao ligados
a ModContext/contexto de comandos e nao habilitam P3. Painel de metadados opt-in
foi acrescentado posteriormente conforme abaixo. Veja LOADER-WRITE-FOUNDATIONS.md.
Inspecao/quarentena de journals e exclusao cooperativa antes do writer foram
adicionadas internamente; contratos/limites em JOURNAL-RECOVERY-AND-SAVE-FENCES.md.
Nao significam reconciliacao de efeitos concluida ou startup integrado.
Incremento seguinte conecta stream real do decoder a roots/geracao e um coordenador
com painel de confirmacao opt-in: DECODED-SAVE-COORDINATOR.md. Revisao de owners
explicitamente selecionados nao e inventario global nem contexto pronto para escrita.

## Invariantes antes de implementar P3

- Nao modificar o formato do save, EXE ou recursos. Sidecar externo por mod.
- CareerScope e ClubRef NAO sao IDs persistentes. Nome/path, ID de clube,
  nome de tecnico e hash do save isoladamente NAO identificam uma carreira.
- Hash identifica bytes de uma revisao observada, nao origem logica. Renomear
  pode preservar bytes; duas copias tambem. Nunca unir historicos por inferencia.
- Novo UUID so apos associacao qualificada ou vinculacao humana explicita.
  Criacao UNKNOWN nao pode receber identidade automatica silenciosa.
- Falha/ambiguidade resulta em conflito acionavel e leitura segura, sem escolher
  automaticamente o sidecar mais recente nem copiar sobre dados de outro mod.

## Modelo de associacao proposto

Registro externo versionado: career UUID + binding UUID + revisao/hash/tamanho
dos bytes observados + raiz/path relativo validado + mod ID + schema do mod.
Nao guardar objetos legados/handles do SDK. Rename confirmado pode mover binding;
copia deve exigir escolher compartilhar ou criar derivacao. Restore deve apontar
revisao conhecida ou abrir conflito, nunca apagar dados mais novos silenciosamente.
Regras de copia/rename/restore precisam de aceite antes de um contrato publico.

Save e sidecar nao formam transacao atomica: observar o retorno de ae nao fornece
fsync, consistencia de grafo ou commit multi-arquivo. Escrever envelope temporario
proprio, validar e manter versao anterior recuperavel; apos crash reconciliar com
bytes do save, sem aplicar automaticamente payload a revisao divergente. Windows
locks/rename e limites de arquivo precisam de testes reais; nao prometer durabilidade.
Nao gravar sidecars dentro de hook, EDT ou callback de dispatcher de melhor esforco.

## Matriz minima antes de publicar

Nova carreira pronta/UNKNOWN,load primario/backup,save completo/falhas parciais,
copia/rename/restore de mesmo nome,conflito de dois processos,arquivo mudando
durante hashing,crash em cada etapa,disco cheio/permissao negada,corrupcao,
schema desconhecido/migracao falha,symlink/junction/path traversal,quota de mod,
shutdown/callback revogado e roundtrip/reabertura no EXE original.

Esta e uma proposta de trabalho, nao implementacao/teste aprovado/decisao de
identidade ja aceita. Armazenamento global existente continua global; nao o
rebatizar como persistencia de carreira. Nenhuma capability nova habilitada aqui.
