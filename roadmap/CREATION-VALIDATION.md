# Criacao real de duas carreiras e investigacao de UI — 17/10/2026

## Confirmacao humana e corroboracao local

Copia escolhida pelo usuario: `C:\Users\vinicius\Documents\Brasfoot22-23`;
loader instalado 0.4.5 com sonda, anterior aos incrementos integrados #13-#17.
Usuario confirmou criar duas carreiras, consultar mod, salvar e reabrir pelo EXE
original, sem jogar partidas. Escala Windows 100%; resolucao 1920x1080 (area util
reportada 1920x1032). Nao houve confirmacao explicita da variante amistoso: nao
atribuir o teste B a essa variante. Nao encontrou opcao de carregar outro save
durante carreira; isso nao prova ausencia global de rota de troca/descarregamento.

Logs novos separados dos historicos copiados pelo usuario:

- A: `session-6461e57f-d61f-4360-889d-255965961da2`: roots-reset uma vez, duas
  observacoes save primario/backup completos em Thread-1/Thread-2, unboundSaves=2.
- B: `session-8b02e52b-e2d3-46e2-9b6e-37d341314288`: roots-reset uma vez, uma
  observacao save primario/backup completo em Thread-1, unboundSaves=1.
- Reabertura A no loader: `session-b6f33ac6-1718-4f25-8bff-0a684d8a7cf0`: load
  completo na EDT, loads=1, ACTIVE antes de close/CLOSED depois, healthy=true,
  sem saves/unbound/stale. Reabertura no EXE original foi confirmacao humana,
  nao observacao do loader (EXE original nao executa nossa sonda).

Na criacao A/B, guard permaneceu UNKNOWN antes de close, loads=0/saves=0 e
unboundSaves>0 apesar de escrita primaria observada. **Bloqueio confirmado:**
criacao nao entra pelo endpoint de load e ainda nao ha marker confiavel de pronta.
Nao promover roots-reset/snapshot/menu a CareerLoaded, nem fazer sidecar automatico
para essas gravacoes. CLOSED de sessao nao e CareerUnloaded de carreira.

Saves e backups novos existem; nenhuma gravacao do agente realizada. Hashes dos
saves registrados somente em evidencia privada; nao redistribuir saves/raw logs.

## StreamCorruptedException: arquivo de equipe, nao .s22

Em A/B, erro repetido em best.U.a -> best.U.w -> best.p.dd -> a.gq.mi.
Inspecao JVM original por javap: best.U.w lista `user.dir/teams`; filtro best.V
aceita `.ban`. best.U.a abre FileInputStream/ObjectInputStream, espera e.t e
captura IOException imprimindo-a e retornando sem importar aquele arquivo.

Leitura somente dos quatro bytes iniciais de 8.399 `.ban` na copia encontrou
um header diferente de AC ED 00 05: `forge_can.ban`, E8 CC 17 02, igual ao erro.
SHA-256 igual na origem, copia Temp anterior e copia Documents:
`b7c0769f76e70c1cfd66b0bb34faa384558f447d4c70ab4e4779d09fa730adff`.
Logo incompatibilidade e preexistente, nao causada por alteracao do loader.
Header correto nos demais nao comprova validade de todos objetos/equipes.

Nao deserializamos esses arquivos para investigar, nao apagamos/convertemos nem
suprimimos erro. Ausencia de equipe no jogo novo e possivel efeito desse retorno;
nao anunciar importacao de todas equipes ou equivalencia semantica completa.
Nao atribuir o erro de equipe ao corte da janela sem teste causal.

## Corte da janela

Captura fornecida mostra janela sem moldura, cortada na esquerda/topo. Usuario
reproduziu ao finalizar criacao/entrar na tela do time, antes de depender do mod;
recuperou com Alt+Espaco, duas vezes. Mod continuou utilizavel.

best.S original cria JFrame, calcula tamanho, alterna NORMAL/MAXIMIZED_BOTH,
configura undecorated e chama pack antes de mostrar. E uma hipotese de interacao
com Windows/layout, **nao causa comprovada**: nao existe captura before/after dos
bounds/estado e nao foi repetida criacao no EXE original para comparar.

Incremento oferece somente acao explicita `Restaurar janela na area do monitor`
no menu vbrasfoot. Na EDT, NORMAL + bounds da area util do monitor corrente,
excluindo insets/taskbar; validate/repaint. Sem timer de reposicionamento, hooks,
modificar dimensoes globais legadas, salvar preferencias ou qualquer save.
Log registra bounds/estado antes/depois e geometria/escala ao instalar menu.

Se botao estiver inacessivel, Alt+Espaco/Win+Seta continuam alternativas Windows.
Esta e mitigacao acionada pelo usuario, nao correcao automatica da causa raiz nem
garantia para toda escala/multimonitor/tela. Teste Swing sintetico confirma acao,
seis checks de geometria/thread incluem taskbar/origem negativa/segundo monitor.
UI real dessa nova acao ainda requer confirmacao humana.

## Identidade e limites da investigacao

REA abriu o EXE da copia Documents como PE/x86 com SHA esperado
`2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c` e sessao
fechada. Analise JVM veio de javap local, nao runtime/pseudocode REA. Bytecode
privado em `%LOCALAPPDATA%\Temp\opencode\vbrasfoot-ui-investigation-0203546da6a242d29b41b2243d2f8bd7`.
Sem instalar engines, executar criacao/gameplay pelo agente ou editar recursos.

Proximo gate: definir/corroborar marker de conclusao da criacao antes de P3;
diagnosticar bounds ao repetir corte, comparar caminho original e confirmar nova
acao de recovery. Nao exigir troca na mesma sessao se a UI nao oferece a rota;
documentar escopo de sessao/reabertura, sem inventar evento unload.

## Atualizacao da copia e candidatos de conclusao

Pacote privado `dist/portable-0.4.5-window-8a16775` gerado da main 8a16775 e
instalado somente na copia Documents com jogo fechado. Incremento interno mantém
numero 0.4.5; identidade pelo commit e SHA-256 do JAR instalado:
`de89190ba33bb2341538a3e875e1947838a782222b8599ab8059e7dafd72e241`.
EXE, todos os arquivos sav, JARs de mods e selecao antes/depois iguais. Validacao
`--probe-career-hooks --validate-only` aprovada, sem iniciar jogo/mods. Evidencia
privada em Temp/opencode/vbrasfoot-window-update-e4dccced85ba437ebb68c796103328e2;
nova acao de recovery ainda nao confirmada pelo usuario.

Confirmacao posterior: usuario informou teste todo OK. Sessao privada
`session-a320397a-df84-4cb5-947c-772b9ded5ce0` corrobora tres acionamentos de recovery,
bounds 0,0,1920,1032 mantidos; primeiro estado6 virou NORMAL0, seguintes idempotentes
na geometria. Escala transform1.0 e monitor1920x1080. Nao houve novo corte capturado
nesse teste; nao atribuir eliminacao da causa ao sucesso da acao manual.

Inventario ampliado `creation-completion.tsv`: seletores de escrita de campo
`field:owner.member:descriptor` alem das chamadas exatas. PUTSTATIC/PUTFIELD
distintos, GETFIELD nao conta como commit; scanner nao inicializa classes.
11 checks sinteticos de overload/field type/read-vs-write/no-init aprovados.
Recorte alvo: 1.038 classes, 158 referencias (inclui 139 selecoes anteriores).

- `best.f.ap` tem sete sites diretos, inclusive load c.a.c e acoes de a.eg.
  Metodo inclui navegacao, autosave e ramificacoes. Retorno nao e exclusivo de
  criacao: hook indiscriminado duplicaria lifecycle em navegacao/jogo.
- `best.f.az` percorre competicoes buscando entrada valida e atualiza indice;
  retorno nao garante carreira nova inicializada. Sete sites diretos no recorte.
- `best.f.bs` recebe true no construtor da raiz e no load, false em best.h.mh;
  apenas tres sites diretos de escrita no ZIP principal. Logo true nao prova
  conclusao da criacao (ja pode existir antes da configuracao de ligas/tecnico).

Nao foi introduzido hook/evento em candidatos rejeitados nem promovido o guard
UNKNOWN por consulta/tela. Proximo trabalho deve separar a operacao completa
de criacao dos caminhos reutilizados, com fingerprint/execucao controlada; nao
pedir ao usuario repetir carreiras apenas para compensar ausencia desse marker.
Recorte nao cobre reflexao/JNI/bibliotecas embarcadas. Bytecode/TSV privados em
build/hook-analysis-579ea4bfbbaa4388bcfbfbb1ff7825c6, sem redistribuicao.
