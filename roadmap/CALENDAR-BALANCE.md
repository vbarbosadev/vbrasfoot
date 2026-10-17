# SDK 0.5.0 — calendario atual e saldo de clube, somente leitura

API major1 preservada; novos defaults recusam em implementacoes antigas. Declare
minLoaderVersion=0.5.0, permissoes=game.read e capacidades query.calendar.current,
query.club.balance (mais query.scoped para obter ClubRef). Sem hooks obrigatorios.

```java
var page = context.game().scopedClubs("nome do clube", 0, 100);
var date = context.game().scopedCalendar();
var balance = context.game().scopedClubBalance(page.items().get(0).reference());
// Confirme escopos iguais, inclusive page.scope(), antes de combinar respostas.
// Paginas vazias devem ser tratadas antes de acessar items().get(0).
```

## Dados e limites

ScopedCalendar contem scope, dayIndex (indice observado na lista do calendario,
nao numero de revisao/ID/evento) e CalendarDate. Campos Gregorian AD: year, month
1..12 e day, com sourceTimeZoneId, displayTimeZoneId e displayedDate. Gregorian AD
somente; calendario ausente/era ou data nao representavel recusa UNSUPPORTED_DATA.

O bridge clona Calendar **antes** de get/getTime: normalizacao lenient ocorre na
copia, nao no grafo original. Texto segue receita da UI dd/MM/yyyy, locale/zone
default da JVM. Campos usam timezone da origem; texto pode mostrar dia diferente
se default/origem diferirem. IDs de timezone sao IDs Java, nao promessa de ZoneId
IANA ou timestamp ISO. Sem expor Calendar mutavel, Date ou objetos do jogo.

ScopedClubBalance contem scope, ClubRef escolhido e balance long completo, inclusive
negativos. Unidades monetarias do jogo, **nao** moeda ISO/reais/centavos/conversao.
Nao confundir saldo com value do jogador. Sem historico de transacoes, saldo
consolidado de todos os clubes ou API de debito/credito. Snapshot e imutavel.

Leituras na EDT, roots/scope/handle conferidos antes/depois; calendario confere
objeto e indice, saldo revalida handle. Mesmo scope nao e snapshot atomico: campos
in-place concorrentes/duas consultas sequenciais podem divergir sem trocar roots.
Refresh manual, nunca polling para inferir DayAdvanced ou conclusao de carreira.
NO_CAREER/STALE_SCOPE/EXPIRED_REFERENCE/CLUB_MOVED/STATE_CHANGED conservam contratos
anteriores. UNSUPPORTED_DATA nao se resolve convertendo calendario por suposicao.

## Evidencia do Brasfoot22-23, interna ao bridge

EXE conhecido SHA2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c.
Javap/scanner local de 1.038 classes, fontes/bytecode privados em
Temp/opencode/sdk-read-static-20261017, nunca distribuido no SDK.

- best.f.bb()Calendar retorna as.get(af).a(); J()I retorna af. T()String usa mesma
  entrada e best.a.f() com SimpleDateFormat/dd/MM/yyyy. a.eg.qv offset434 chama T
  e escreve JLabel Eg. Calendar original vem de Calendar.getInstance, nao assumir
  tipo universal sem validacao.
- best.ah.kb()J e getter puro nb:J. a.eg.qx offset8 escreve rotulo saldo com
  best.C.c(J). a.cq.oI compara saldo/custo de expansao e mostra insuficiencia;
  v/w somam/subtraem valores em nb, sem prova de ISO/centavos.
- aN retorna humanos, nao humano atualmente selecionado. B() static retorna X
  usado no construtor de a.eg. Esta entrega **nao** escolhe humano aN.get(0) nem
  publica novo handle de clube selecionado sem validar identidade.

## Testes

15 checks puros: copia defensiva/normalizacao no clone, leap/month, timezone
divergente, unsupported/missing/nonlenient invalido, long extremos/negativos,
invariante scope e defaults antigos. Facades game.read e contexto revogado
tambem cobrem novos metodos. Teste I/O original ampliado:52 checks, getters
data/indice/saldo iguais ao original, scopes/clone, stale saldo apos reload e
NO_CAREER apos reset. Fixture com frame sintetico, nao confirmacao UI humana.

Exemplo de conferencia: examples/career-dashboard, separado do loader e API-only.
Comparar data e saldo com UI original (saldo pode estar abreviado/formatado).
Comparacao humana, modos/regioes/timezones distintos e concorrencia abrangente
continuam pendentes. Nao fecha P2, P3/P4/P6/MVP nem adiciona agenda/competicoes/
partidas/historicos sem evidencia propria.

Confirmacao humana posterior: usuario informou data e saldo coincidindo com UI
original na copia de teste. Sessao adaaac74-115a-4c42-9de7-76667ace441d corrobora
painel ACTIVE/load teste02; igualdade dos valores e confirmacao humana, nao log
de conteudos. Nao extrapolar para todos clubes/timezones/modos ou escrita.
