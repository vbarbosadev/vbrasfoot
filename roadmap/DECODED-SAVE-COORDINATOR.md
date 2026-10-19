# Origem decodificada, coordenador e revisao explicita

Codigo experimental opt-in, ainda sem API publica de escrita ou instalacao nova.
Complementa EXPLICIT-SAVE-ASSOCIATIONS.md e JOURNAL-RECOVERY-AND-SAVE-FENCES.md.
Nao cria mods, carreiras, partidas, pagamentos ou negociacoes automaticamente.

## O que foi conectado

1. O transformer fingerprintado de `c.a` observa o String realmente fornecido ao
   construtor original de FileInputStream e envolve somente o InputStream passado
   ao construtor original de Kryo Input. Exige exatamente um site de cada tipo no
   perfil conhecido; sem fallback para classe original se uma sonda pedida falhar.
2. LoadInputTrace calcula SHA256 e conta somente os bytes lidos pelo decoder.
   Nao reabre, drena, busca EOF por conta propria, reposiciona ou tenta ler de novo.
   Nao aloca o save inteiro. Skip/mark/reset, falha de leitura/close, outro thread,
   leitura nao linear ou >256 MiB recusam evidencia, sem truncar a leitura original.
   Falha do hashing diagnostico nao substitui resultado/exception do stream original.
3. Depois de Input.close e da atribuicao original das duas roots, a sonda captura
   referencias fracas e o ticket da geracao. Somente carga aceita pelo guard pode
   publicar evidencia. Fallback propaga a origem/bytes/roots do backup realmente
   decodificado, mesmo quando o metodo externo retorna false.
4. DecodedSaveProvenance guarda no maximo uma evidencia atual, com referencias
   fracas. Load/reset/atribuicao de root/save/detach invalidam. Um contador CAS sob
   lock impede completion antigo de substituir invalidacao/evidencia mais recente;
   sessao/geracao/roots tambem precisam coincidir ao verificar.
5. LoadedSaveVerifier, fora da EDT, compara observacao estavel do arquivo com
   caminho real + tamanho + hash do stream, e verifica roots/ticket na EDT com
   timeout limitado. Uma copia de mesmos bytes em outro path nao seleciona a origem.
   O resultado e um snapshot de fatos, NAO uma lease ou autoridade duradoura.

Hash do stream cobre bytes alimentados no buffer do Kryo, incluindo read-ahead.
Nao prova que cada byte foi semanticamente interpretado, que nao existe sufixo
nao interpretado ou que todo o grafo esta consistente. Close observado nao e fsync.
Arquivo/roots podem mudar depois da verificacao: nao existe transacao atomicamente
ligando filesystem, EDT e sidecars. Nao ha garantia contra codigo Java malicioso
que falsifique chamadas aos hooks ou contra corridas hostis de filesystem.

## Coordenador de metadados — sem contexto de comandos

CareerAssociationCoordinator recebe diretorio de saves e registro EXPLICITO de
ate64 owners selecionados, sem varrer o disco para inferir identidade. Na revisao:

- exige evidencia atual do decoder dentro do diretorio escolhido;
- observa/verifica o arquivo fora da EDT;
- sem handle escolhido: ASSOCIATION_REQUIRED, nunca UUID novo silencioso;
- com handle: inspeciona metadata e cada diario configurado pela ancora conhecida;
- compara novamente metadata/evidencia ao final; nao aceita resultado antigo;
- mostra ASSOCIATION_CONFLICT, REVISION_REVIEW_REQUIRED ou READ_ONLY_LINKED.

READ_ONLY_LINKED NAO significa pronto para escrita. FileKey indisponivel e exibido
como tal, nao fabricado a partir de hash; mudanca de timestamps continua conflito.
Owners antigos/desabilitados/desconhecidos nao estao nesse registro: nao e auditoria
global de todos os diarios existentes. IOException/corrupcao/mismatch nao e EMPTY.
Incremento BINDING-JOURNAL-INVENTORY.md fecha a lacuna de owners nao selecionados
existentes no binding escolhido: une configurados+namespaces fisicos e revalida
nomes/HEADtokens antes de confirmar. Ainda nao e inventario de outras carreiras.

Uma Review e privada, vinculada a instancia e consumida uma unica vez. confirm
exige acao explicita, revalida stream/arquivo/geracao e token de metadata antes de
chamar create/fork/rename/assert-rename/revision/restore dos servicos existentes.
Quarentena usa owner e token de diario que foram realmente revisados. Nao resolve
UNKNOWN/APPLIED, nao reancora diarios e nao instala CareerCommandGate.Context.
Mesmo recarga dos mesmos bytes com mesmas roots sinteticas revoga Review antiga.
Falhas/incertezas nao permitem retry automatico ou reutilizar confirmacao.

## Interface opt-in no codigo atual

Ao recompilar e iniciar com `--probe-career-hooks`, o menu vbrasfoot inclui
**Carreira e diarios (experimental, sem escritas no jogo)**. Modo normal/seguro
nao recebe o painel nem habilita a sonda. Nada foi instalado por esta entrega.

- Abrir/Atualizar apenas revisa; nenhum metadata/journal e criado automaticamente.
- Campos vazios nao selecionam carreira por nome ou hash. Para inspecionar uma
  associacao anterior, informar ambos UUIDs explicitamente.
- Nenhuma acao e preselecionada. Modal mostra acao/arquivo/hash/geracao/owner e
  tem Cancelar como opcao padrao. Cancelar nao chama confirm.
- Criar associacao cria SOMENTE metadata externa, nao uma carreira no Brasfoot.
- Renomeacao afirmada sem identidade nativa e opcao separada da comprovada.
- Quarentena nao oferece botao de "tentar novamente" ou apagar comprovantes.
- File I/O/observacao/hash/diarios ficam em SwingWorker, nunca na EDT. Editar UUIDs
  revoga a revisao anterior. Save/recarga entre revisar e confirmar causam recusa.
- UUID confirmado ou TENTADO em falha incerta fica nos campos e no log local,
  inclusive se a janela for fechada durante o commit explicitamente confirmado.
  Em erro depois do commit, inspecionar esse handle antes de outra tentativa.

Raiz propria do loader: `home/data/.career-state/associations` e `journals`.
Nao e `ModStore` publico nem mudanca do escopo global `data/<modID>` existente.
Nao ha catalogo/discovery global, unicidade global de path ou quota global de IDs;
usuario ainda deve guardar os UUIDs. Registros nao sao excluidos/compactados.
O painel fica indisponivel se registro explicito exceder64owners, sem apresentar
uma lista parcial como completa ou impedir que o jogo inicie por esse motivo.

## Evidencia e limites de aceite

- Self-tests usam streams/roots/markers e arquivos PROPRIOS, inclusive transicao
  de geracao, fallback, revisao antiga, corrida de confirmacoes, falhas e budgets.
- Fixture de I/O original confirma hash/tamanho do stream completo do save privado,
  roots/geracao, fallback verdadeiro, schemas inalterados, coordenador e recusa de
  Review depois do writer. UUIDs criados nessa fixture sao decisoes ARTIFICIAIS
  do teste, nao confirmacoes humanas ou identidade automaticamente descoberta.
- A fixture usa JFrame sintetico e NAO executa o ramo completo de inicializacao.
  initializationPathObserved continua false, public IO load continua UNKNOWN e
  nenhum contexto de comando e instalado. Nao extrapolar para prontidao global.
- REA binary_session nesta investigacao nao anunciou ferramenta JVM elegivel e
  providers nativos nao estavam configurados/disponiveis. Reutilizado javap do JDK
  e bytecode privado anterior, vinculado ao SHA do perfil e fingerprint de `c.a`.
  Nenhum engine/configuracao foi instalado/alterado, nenhum bytecode/save publicado.
- ProbeSession grava `decoded-save-provenance.properties` com status/motivo,
  geracao/hash/tamanho/backup/initialization e writeAuthority=false, sem paths/roots.

## O que ainda nao esta implementado/liberado

Nao chamar este incremento de implementacao de "tudo": faltam qualificacao do
contexto real/global e aceite humano, interceptacao/preflight de writer em producao
com gate, reconciliacao por operacao entre revisoes, save-as/restores completos,
catalogo/quotas/compactacao/migracoes globais, semantica e UI financeira, calendario/
partidas/temporadas, ofertas/interceptacao/contrapropostas/commit de transferencias,
API publica de escrita e autoria externa. Nao habilitar essas frentes por flags
de prontidao artificiais, polling ou stubs que apenas retornam sucesso.

Incremento posterior: ORIGINAL-IO-INTERLOCK.md conecta admissao antes dos corpos
originais de carga/reset/save ao gate privado da ProbeSession. Ainda sem concessao
de contexto a mods, qualificacao real ou reconciliacao de efeitos entre revisoes.
