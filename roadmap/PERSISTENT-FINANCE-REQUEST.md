# Pedido financeiro interno reproduzivel

Incremento experimental apos OPERATIONAL-CONTEXT.md. Nao instala loader, nao cria
mods e nao concede API/permissoes de escrita. Categorias continuam **raw 6/7**,
nao patrocinio/salario. Nenhuma mudanca no EXE, schemas serializados ou saves pessoais.

## Payload v1

FinanceRequest e um valor interno imutavel com codec binario limitado a4096bytes:
- formato `vbrasfoot.finance-request/1` e SHA256 do perfil original suportado;
- UUIDs explicitos de carreira/binding e SHA256/tamanho da revisao do save;
- owner e chave de operacao persistente;
- locator do clube: posicao na lista original P, ID gD e nome exato;
- direcao raw, quantia int positiva, saldo anterior e TODOS os14campos do ledger.

Campos seguem ordem fixa; inteiros usam DataOutputStream big-endian, UUIDs dois
longs, strings writeUTF (UTF modificado Java com tamanho). Ledger ordenado por nome
de campo. Decode exige formato/perfil conhecidos, bounds, validacao de aritmetica,
sem trailing bytes e reencoding identico. SHA256 desses bytes e payloadDigest.
Map iteration order, nonce, session token e geracao nao entram no digest.
Mesma solicitacao reconstruida em outra JVM tem o mesmo intent. Mudancas de
owner/key/escopo/locator/precondicao/direcao/quantia mudam digest.

Codec nao grava sozinho em disco nem concede autoridade ao payload decodificado.
Fixture persiste bytes em arquivo PROPRIO explicito e reabre para comprovar
reprodutibilidade; nao e um request-store de producao, fila automatica ou commit
atomico com journal. Journal ainda guarda digest/intent/receipt, nao payload inteiro.
Persistencia de solicitacoes de mods e retencao/quota/migracao desse armazenamento
continuam pendentes. O codec nao autentica dados contra adulteracao.

## Admissao e efeito

LegacyFinanceAdapter.request captura locator e precondicao completa na EDT,
exigindo clube humano exato nas listas P/aN. O par ID/nome precisa ser unico em P;
ID isolado NAO e unico no save original fornecido. Repeticao da referencia,
ambiguidades do par ou lista invalida sao recusadas. Indice/nome NAO sao identidade global:
locator e estritamente vinculado a revisao do save. Nao inferir validade em save-as,
copia, restore para outros bytes ou revisao nova apenas pelo mesmo nome/ID/saldo.

prepare(request) revalida locator composto unico e snapshot completo ANTES de criar plano.
Plano guarda roots/clube/ledger por referencias fracas e continua one-shot. Antes
do efeito repete roots, ledger, clube humano, locator composto unico e snapshot completo.
Nao recalcula precondicao antiga para tornar pedido obsoleto valido. Nao migra
pedido para revisao nova. Somente metodos originais v/w e full-ledger postcheck
existentes executam efeito; nao ha setter bruto/rollback.

Dispatcher agora tambem consulta acceptsContext(context,owner) do handler antes
de reservar/persistir. Plano persistente exige career/binding/save/owner exatos,
key/digest/direcao correspondentes. Gate/journal/roots/EDT fences continuam
obrigatorios. O pedido nao contorna falta de contexto atual ou de owner permitido.

prepare(club,direction,amount) anterior continua para fixtures legadas, com nonce
temporario; nao anunciar esses planos como contratos persistentes. Caminho novo
prepare(FinanceRequest) usa somente digest canonico, sem nonce. Nenhum desses
caminhos foi conectado a ModContext ou ganhou owner publico em Main.

## Reexecucao e limites de evidencia

Persistir pedido nao prova que seu efeito ocorreu ou foi incluido no save. Quando
o journal ja conhece a chave, nao recebe outra lease. Payload diferente sob mesma
chave continua conflito. Reabrir mesmo input/mesmos valores em roots novos nao
autoriza repetir APPLIED: dispatcher retorna RECORDED_ONLY e invalida contexto.
UNKNOWN/PREPARED permanecem incertos, sem retry automatico. Nenhuma classificacao
de inclusao no save foi deduzida por comparar saldo ou ate o ledger completo.

Testes novos usam payloads proprios, digest em JVM filha, campos individualmente,
codec invalido/truncado/trailing/profile, contexto/owner e journal reaberto.
Fixture original financeira armazena/reconstroi pedido de credito raw6, executa
efeito original e writer/reload em copia privada; comprova recusa de precondicao
obsoleta/revisao diferente e nao replay apos restaurar input na fixture. As
autoridades da fixture continuam ARTIFICIAIS; nao ha aceite de gameplay em Main,
init global, UI financeira ou reabertura standalone aprovada por esses testes.

## Proximos gates

- Request-store de producao revisado, quota, lifecycle e preflight de operacao;
- Reconciliacao por operacao de APPLIED/UNKNOWN/inclusao no save/interrupcoes;
- Categorias contabeis/UI e aceite original standalone;
- Calendario/mercado e contratos/permissoes publicos especificos.

Este incremento resolve reprodutibilidade do contrato interno, nao a conclusao
da camada de escrita nem a compatibilidade de todo o gameplay.
