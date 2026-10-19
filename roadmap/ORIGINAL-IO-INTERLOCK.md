# Exclusao nas entradas originais de carregar, resetar e gravar

Incremento do loader experimental. NAO implementa todo o calendario/mercado nem
libera API de escrita. Sem criacao de mods, instalacao ou alteracao de saves do usuario.

## Integracao real, diferente de chamar beginSave apenas na fixture

O CareerTransformer fingerprintado de c.a insere admissao sincronica antes do
corpo original de c(String,boolean), ae(String), a(best.f) e do construtor de reset
(neste ultimo, imediatamente depois de Object.<init>, antes da primeira mutacao).
Nenhum campo/metodo/schema e acrescentado ao jogo; original EXE permanece intacto.

beforeOriginalIo e deliberadamente diferente dos markers best-effort: uma recusa
se propaga ANTES de executar a rotina original. Nao engolir BUSY e prosseguir com
writer/decoder enquanto comando esta ativo. Admissao recusada fica fora do handler
de cleanup desse metodo; nao pode fechar a fence do chamador externo por engano.
Markers diagnosticos e corpo original ficam dentro da regiao protegida. Retornos
normais/antecipados e Throwable executam afterOriginalIo e mantem resultado original.

CareerIoInterlock usa o MESMO CareerCommandGate fornecido pelo caller confiavel:
- primeira entrada obtem fence exclusiva e invalida contexto/roots anteriores;
- queued/running/timed-out ainda executando ou persistencia terminal em andamento
  impedem LOAD/RESET/SAVE, sem espera na EDT;
- durante LOAD/RESET, o mesmo thread pode executar fallback LOAD e resets internos
  sob a fence externa; nenhuma saida de filho libera a fence do pai;
- nenhuma entrada SAVE pode ser aninhada em outra operacao, nem LOAD/RESET em SAVE;
- threads concorrentes sao recusados, stack limitada a64entradas sem eviction;
- Entry opaca exige instancia/thread/LIFO exatos; foreign/double/out-of-order exit
  nao libera a exclusao; outer exit fecha a fence sem restaurar contexto antigo;
- encerramento marca owner fechado/invalida gate, mas NAO libera writer que ainda
  nao retornou. A saida real desse writer pode liberar sua fence depois do close.

O bridge captura owner/entry no inicio de CADA chamada, inclusive chamada sem
interlock conectado. Contador global de chamadas impede instalar outro owner no
meio de uma rotina ja em andamento. Detach com rotina ativa fecha owner mas o
mantem referenciado ate ultima saida; novos calls sao recusados, nao executados
sem protecao ou transferidos para outro gate. Registro nao chama mods/usuarios.

## Sessao instalada pelo codigo atual

ProbeSession conecta interlock e gate privados antes da preparacao do runtime
opt-in e os fecha na disposal. Modo normal/seguro nao transforma essas rotinas.
Esse gate ainda NAO concede contexto/owners a mods e nao e exposto por ModContext.
Portanto, em operacao normal da sonda nao existem comandos de mods para bloquear:
esta entrega conecta a exclusao, nao cria uma autoridade de escrita silenciosa.

Fixture original conecta um gate proprio com reserva ARTIFICIAL para comprovar
recusa de entradas antes de qualquer mudanca. Nao e qualificacao da carreira real.
Um futuro servico de comandos deve usar esse mesmo gate da sessao e passar pelos
gates independentes de associacao/prontidao/reconciliacao. Um segundo gate criado
por conta propria nao participa dessa exclusao: isto nao e sandbox de codigo Java.

Report local original-io-interlock.properties registra admitted/refused/completed,
depth antes do fechamento e writeAuthority=false, sem filenames ou objetos de jogo.
Depth >0 no shutdown e trabalho ainda em voo, nao "cleanup concluido" inventado.

## Evidencia delimitada

CareerIoInterlockSelfTest testa gate/bridge e bytecode sintetico transformado:
refusals antes do corpo, root reset/constructor, nested fallback, LIFO, outra
thread, depth bound, pending persistence, timeout, early return, original failure,
legacy swallowed failure e attach/detach sem janela de troca desprotegida.

CareerHookRuntimeTest conecta o interlock ao runtime fingerprintado original:
- reserva artificial bloqueia ae antes de produzir arquivos de saida;
- bloqueia c e setter antes de substituir roots ou alterar lifecycle/provenance;
- apos cancelar reserva, writer original funciona e backup/reload continuam;
- retorno do writer nao restaura contexto artificial anterior;
- fallback recursivo original drena entradas sob uma fence sem leaks;
- verifica schema original e hashes de origem intactos.

Esses testes usam COPIAS privadas, JFrame sintetico e nenhuma partida/carreira
nova. Ramo completo de inicializacao de UI continua nao exercitado nessa fixture.
Nao e aceite humano, interceptacao validada em todas as telas do standalone ou
teste real de falta de energia/disco/permissoes. Rejection de conflito se propaga;
UI original pode apresentar/logar esse erro, sem promessa de dialogo customizado
ou adiamento automatico. Nao faz queue/retry silencioso de saves recusados.

## Ainda pendente

Qualificacao real/global de contexto, registro completo de journals, reconciliacao
por operacao de APPLIED/UNKNOWN, revision/save-as/restore coordination, durabilidade
interarquivos, categorias/UI financeiras, calendario/partidas/temporadas, geracao/
ofertas/interceptacao/contrapropostas/commit de mercado, API publica e aceite humano.
Somente detectar um retorno de load/save nao fecha esses gates. Nao mudar resultado
incerto para sucesso/ausencia de efeito apenas para remover bloqueio.
