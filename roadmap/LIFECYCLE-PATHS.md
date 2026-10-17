# P4 — rotas de criacao/navegacao, sem novo evento publico

Investigacao posterior ao merge #13. Alvo exclusivo: EXE SHA-256
`2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.
Nenhum EXE/schema/save alterado; nenhum metodo de criacao/gameplay executado neste
incremento. Nomes ofuscados ficam nesta investigacao interna, nao no SDK.

## Observacoes estaticas

`inspect-career-hooks.ps1` agora gera `lifecycle-paths.tsv` privado com seletores
owner/member/descriptor exatos, caller/offset e hashes de classe/Code. Mantem
inventario anterior; overload diferente nao e match e nenhuma classe e inicializada.
O scanner percorre as 1.038 classes do ZIP principal. Nao cobre reflexao/JNI,
callers em bibliotecas embarcadas ou toda semantica do grafo de UI.
Execucao final: 139 referencias no inventario anterior, 166 no recorte ampliado.
`best.f.V` tem dez sites diretos neste recorte, inclusive chamada em best.f.aa;
nao e exclusivo da rota a.bV.AB. Contagem de sites nao conta eventos de runtime.

| Rota | Observacao nos bytes | O que NAO prova |
|---|---|---|
| `forms.q.actionPerformed` | Constroi c.a, chama best.p.dd e cria best.S(true) | Construtor de raizes nao conclui carreira pronta |
| `forms.s.actionPerformed` | Cria best.S(false), seleciona best.S.aY(7) | Abrir painel nao confirma leitura nem descarrega carreira anterior |
| `a.gq.mi` | Constroi c.a, inicializa recursos e cria janela ou seleciona painel0 | Nao e ponto seguro de unload; classe tambem participa de UI de registro |
| `best.h.mh` | Constroi c.a, abre forms.h.wT e executa outras inicializacoes | Retorno do construtor nao e retorno do fluxo inteiro; nao substituir entrada best.h2 |
| `a.hU.oq` | Aplica configuracao inicial e, apos condicao de ligas, seleciona painel1 | Escolha de ligas ainda nao e carreira pronta |
| `a.bV.pl/AB` | pl oferece torneio amistoso; caminho sem torneio chama AB, que chama best.f.az/V | Escolha do torneio altera rota; um hook somente em AB pode ter cobertura incompleta |
| `best.f.V` | Ramifica pelo campo bk para ap ou aa | Nome/retorno nao demonstram commit de criacao ou exclusividade de lifecycle |
| `best.S.aY` | Substitui componentes/painel; valor2 cria a.eg | Reconstruir tela de elenco nao e carregar/trocar carreira |

Esse recorte identifica configuracao/selecoes intermediarias; nao oferece um unico
endpoint de criar/trocar/descarregar corroborado. Sem reproduzir caminhos reais,
novo hook em aY geraria risco de duplicacao por navegacao. Nao foi adicionado.

## REA e cobertura do ambiente

REA abriu o EXE como PE x86 com hash esperado. `inspect_artifact` summary,
Evidence `ev_f2d71889c5ed71bec8470c15efd1057d9adf01cc80ba634aaee32d919c58edcc`,
parent `ev_f58bce68213ed6e2cb2c7c5d9476d5e3ff5ac1dbb5713012d28c908c4158375f`,
inventariou somente raiz PE (um node, sem filhos). Isso **nao** prova ausencia do
ZIP JVM embarcado: essa rota do provider nao o expandiu. As observacoes JVM acima
vem de ZipFile/parser local/javap, nao de pseudocode nativo ou execucao REA.
Provider Ghidra nao configurado; captura native UI anunciada exige macOS, e nao
substitui a UI Swing/Windows desta investigacao. Nenhuma dependencia foi instalada.
Sessao REA fechada ao concluir. Dados privados nao publicados.

## Roteiro de corroboracao que ainda bloqueia P3/P6

Na copia completa **nova**, preservando a instalacao/saves atuais:

1. Abrir pelo launcher e flags documentadas em CAREER-PROBE.md; observar apenas
   acoes oferecidas pelo jogo. Nao invocar entradas alternativas/reflexao para
   contornar registro/licenca ou criar um caminho inexistente na UI.
2. Criar carreira com um tecnico pelas telas originais, primeiro sem amistoso,
   depois outra carreira com amistoso se oferecido. Registrar momentos em que
   consultas tornam-se validas, logs/threads/ordem, nomes de arquivo apenas privados.
3. Salvar cada carreira pela UI, fechar normalmente, reabrir no original e comparar.
   Ter dois saves reais permite testar associacao futura sem usar copia renomeada
   como se comprovasse duas identidades persistentes.
4. Procurar voltar/carregar outra carreira nas telas/menu originais. Se inexistente
   no fluxo observado, registrar essa limitacao; nao generalizar ausencia em todo jogo.
5. Se ha troca, testar A->B->A, refs scoped antigas recusadas e ausencia de callback
   duplicado; fechar dialogo sem escolher save nao deve anunciar evento.
6. Fechamento de JVM e invalidez de contexto sao encerramento de sessao, nao
   CareerUnloaded de carreira. Save primario fechado nao e fsync/commit duravel.

Sem essas observacoes, identidade/sidecar automatico P3 e escrita P6 permanecem
bloqueados. Testes I/O existentes cobrem load/fallback/escrita primaria e guard,
nao substituem este roteiro. Gates humanos/publicacao tambem nao sao aprovados
por um script ou pela manutencao do roadmap.

## Reproduzir sem jogo iniciado

```powershell
.\inspect-career-hooks.ps1
```

Resultados completos em build/hook-analysis-UUID, ignorados; EXE conhecido hashado
antes/depois. CI compila scanner e testa selecao com fixture que falharia se fosse
inicializada, sem EXE/save. Nao redistribuir os textos javap/classes/saves privados.
