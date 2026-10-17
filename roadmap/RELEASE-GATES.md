# Marcos de release — reconciliacao 17/10/2026

Complementa [ROADMAP.md](ROADMAP.md), sem substituir os aceites P0-P7 nem criar
gates de gameplay para leitura/interface. Escopo exclusivo: Brasfoot22-23 conhecido.
Evidencias detalhadas: `../VALIDACAO.md` e guias/contratos desta pasta.

## Evidencia nao e aceite

- **Implementado:** fonte/artefato entrega o recurso delimitado.
- **Automatizado:** teste/CI demonstra somente ambiente e cobertura registrados.
- **UI real:** confirmacao humana ou captura/log de sessao, com limites explicitos.
- **Aceito:** criterios do marco anunciado satisfeitos e revisados; merge, hash,
  fixture, round-trip ou benchmark isolado nao aprovam uma fase inteira.

Nenhum marco esta registrado como release aceita nesta reconciliacao. Nao calcular
percentual global, nem tratar pendencias historicas resolvidas como ausencias atuais.

## Tres marcos e criterios

| Marco | Criterios para anunciar | Situacao / bloqueios reais |
|---|---|---|
| Experimental de leitura/interface | Portable/SDK independente, consultas/menu delimitados, P0/P1/P7 aplicaveis ao caminho anunciado, sessao real identificada e limitacoes publicas; origem/publicacao e operacao revisadas | Implementacoes/fixtures/CI e sessoes na copia completa existem; escalas, JVM/runtime anunciado, sessao sem checkout disponivel e cobertura operacional restante precisam ser delimitadas/fechadas. Pacote gerado nao e publicacao revisada. Nao prometer carreira persistente/eventos completos/gameplay |
| MVP P0-P5 + P7 correspondente | Aceites dessas fases; hooks/lifecycle reais P4, identidade/sidecars P3, SDK/exemplos e terceiro criando/instalando mod pelo guia sem classes ofuscadas; release documentada | P4 completo e P3 bloqueiam exemplos de carreira/partida; terceiro humano e publicacao pendentes. Painel/exportacao confirmados nao fecham MVP |
| Gameplay P6 + P7 | MVP aceito; comandos pequenos/autorizados, invariantes/threads/conflitos/falhas por capacidade e save reaberto no original; gates P7 pertinentes completos | Nao implementado. Bloqueio P4 -> P3 -> P6; nenhuma API de escrita de gameplay liberada |

Eventos experimentais opt-in continuam separados: READ_COMPLETED e fechamento do
Output primario nao sao CareerLoaded/Unloaded/Saved completos ou fsync/commit
duravel. Teste de I/O original com frame sintetico nao e UI completa. O observador
SDK nao foi instalado na sessao real de consulta 0.4.0; nao inferir callback real.

## Autenticidade e supply-chain

**SHA-256 != autenticidade.** Hash/allowlist detectam divergencia frente ao indice;
pacote e indice adulterados juntos podem concordar. Perfil por hash identifica
bytes suportados, nao certifica procedencia/licenca do jogo. Validadores nao
auditam codigo; mods Java e scripts executam com confianca do usuario, sem sandbox.

Antes da publicacao revisada, identificar origem/canal oficial, versao/commit,
responsavel e procedimento verificavel para obter/conferir o pacote confiavel.
Registrar revisao das fontes, scripts, dependencias/licencas e Actions pinadas;
decidir/documentar mecanismo de autenticacao de release e resposta a pacote
comprometido. Assinatura/atestado, se escolhido, precisa de verificacao documentada;
nao alegar mecanismo ja implementado. Nao baixar/instalar codigo automaticamente
nem redistribuir artefatos proprietarios. Java empacotado continua opcional e exige
fornecedor/licenca/atualizacao de seguranca revisados antes de ser anunciado.

## Retencao de disco e limpeza revisada

Hoje stdout/stderr sao limitados por sessao e logs do loader rotacionam; historico
de falhas tem 128 resumos. Isso nao limita o total acumulado entre sessoes.
Backups, verification, diagnostics, caches, fixtures e geracoes de atualizacao
podem crescer. Reserva do instalador nao e politica de retencao operacional.

Pendencia de operacao: inventario de tamanhos/idades e politica documentada por
categoria, com limites definidos a partir de uso medido, sem numeros arbitrarios.
Limpeza deve mostrar previa dos caminhos/bytes/efeitos, exigir revisao/confirmacao,
validar caminhos/links e remover apenas alvos gerenciados reconhecidos; preservar
evidencias de recuperacao e estados de atualizacao pendente ate revisao explicita.
Ha inventario somente leitura via `--storage-status`, agregado por categoria e
limitado em entradas/tempo, com parcial explicito: [STORAGE-RETENTION.md](STORAGE-RETENTION.md).
Nao ha ferramenta geral de limpeza/retencao nem autorizacao para executar limpeza.

**Nunca apagar saves `.s22`/`.sbck`, dados de mods ou restaurar backups
automaticamente.** Backups de saves tambem nao devem ser descartados por uma
limpeza silenciosa. Cache regeneravel e temporarios gerenciados podem ter fluxo
explicito separado; recuperacao de `.update-pending/` existente nao substitui uma
politica de retencao de sessoes/backups. Desinstalacao atual preserva dados.

## Politica de manutencao do SDK

Base atual: API major 1, SDK/loader 0.5.0 experimental em desenvolvimento;
validador JSON/guia IA sem ampliar capacidades de gameplay. Contexto foi integrado
por #13; recovery0.4.5 instalado/confirmado na copia Documents. Sem release/tag.
Manter minLoaderVersion/capacidades por recurso e perfil exato, recusando
incompatibilidades com mensagem acionavel. Adicoes preservam assinaturas anteriores;
default pode recusar capacidade em loader antigo. Nao prometer compatibilidade
SemVer entre mods, Java futuro ou perfis nao validados.

Para publicar: explicitar responsavel/canal de suporte, versoes de loader/SDK/Java
mantidas, processo de deprecacao/migracao e tratamento de correcoes de seguranca.
Nao existe janela temporal de suporte garantida nesta evidencia. Cada release
deve registrar changelog, matriz medida e limitacoes; repetir testes SDK-only fora
do checkout e CI Java 17/21, mais testes reais quando mudar bridge/hooks/UI/runtime.
Nao converter sucesso sintetico em promessa de gameplay nesses runtimes. Quebra
de contrato exige versao/guia de migracao explicitos; mudanca comportamental de
cleanup deve constar nas notas mesmo sem alterar assinaturas.

## UI/menu e extensoes opcionais

Menu de mods, estado, selecao para proximo inicio e diagnostico pertencem ao
loader; preservar controles originais e testar resolucoes/escalas aplicaveis.
Nao ha hot reload nem promessa de carregar mods ao abrir `bf22-23.exe` diretamente.
Transferencias ampliadas, contrapropostas, leiloes e outras extensoes originais
sao opcionais posteriores, analisadas no 22-23 por capacidade P6; nao requisitos
extras do experimental/MVP nem importacao de regras do RealBrasfoot26.

## Proxima ordem sem antecipar aceite

1. Consolidar P0/P1/P7 e publicacao experimental delimitada, com politica operacional.
2. Corroborar rotas reais P4 e entrega confiavel; nao sintetizar unload por tela/raiz.
3. Implementar/testar P3 em duas carreiras, copias/renomes/Save As/restauracao/falhas.
4. Completar P4/P5/MVP com terceiro humano e publicacao revisada.
5. P6/P7 por capacidade, somente apos MVP aceito.

PR integrado nao e publicacao do SDK. Autor terceiro e release/tag/upload revisados
continuam pendentes e separados dos builds locais ja aprovados.
