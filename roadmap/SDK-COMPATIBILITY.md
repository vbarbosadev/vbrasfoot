# Compatibilidade do SDK — 0.8.0 experimental

CareerIoEvents/ModContext.careerIoEvents exige0.8.0, events.career.read e
requiredCapabilities=events.career.io/--probe-career-hooks. Default preserva
implementacoes antigas de ModContext; DTOs/kinds antigos inalterados.

## Quatro identidades diferentes

1. **API major**: atualmente 1. Manifesto aceita 1 ou intervalo inteiro `[min,max)`
   contendo 1; nao e range SemVer. Metodos anteriores sao preservados; adicoes
   podem ter default que recusa loader antigo, em vez de inventar capacidade.
2. **Loader/SDK SemVer**: 0.8.0 nesta entrega. `minLoaderVersion` e minimo explicito
   com major.minor.patch, nao inferido do Java nem da versao do seu mod.
3. **Perfil do jogo**: somente `brasfoot22-23` conhecido, fingerprint completo do
   EXE validado pelo loader. Mesmo nome comercial com bytes diferentes e recusado.
   Validador SDK confere manifesto/classe, nao verifica EXE, save ou licenca.
4. **Versao do seu mod**: campo textual nao vazio; dependencias sao IDs, nao ranges
   de versao. Nao anunciar resolucao SemVer entre mods que nao foi implementada.

Sdk.VERSION e constante de compilacao; pode ser inlined pelo javac. Nao use essa
constante como detector confiavel da versao do loader em execucao. Declare minimos
e capacidades no manifesto e trate erros/defaults documentados.

## Minimos por recurso

| Recurso | Minimo | Permissao / condicao |
| --- | --- | --- |
| Consultas legadas por snapshot | Contrato antigo API 1 | game.read; evitar copia completa |
| Paginas e registerScopedAction | 0.2.0 | game.read/query.paged ou ui.register |
| scopedClubs/scopedPlayers e erros tipados | 0.3.0 | game.read + query.scoped |
| CLI --validate-mod sem jogo | 0.3.1 | Valida so o JAR, nao executa mod |
| CareerObservations | 0.4.0 | events.career.read + hooks opt-in; capacidade condicional events.career.observed |
| Selecao proximo inicio | 0.4.1 | UI/CLI do loader; sem hot reload |
| Historico limitado de falhas | 0.4.2 | Memoria da sessao; captura nao exaustiva |
| Diagnostico local revisavel | 0.4.3 | Previa/opt-in/confirmacao; sem upload |
| ZIP SDK-only e validador independente | 0.4.4 | JDK para compilar; ferramenta nao precisa de loader/jogo |
| JSON/--describe-sdk e garantias de revogacao do contexto | 0.4.6 | Ferramenta target-free / contexto session-owned |
| scopedCalendar/scopedClubBalance | 0.5.0 | game.read + query.calendar.current/query.club.balance; campos delimitados |
| registerPanel/UiTheme/ModPanel | 0.6.0 | ui.register + ui.panel.embedded; overlay da janela da carreira, nao injecao arbitraria |
| UiTheme.Role/style e preservacao de estilos explicitos | 0.7.0 | Componentes do mod somente, EDT; estilo inspirado, sem assets do jogo |

Minimos descrevem disponibilidade dos recursos, nao uma matriz de gameplay
aprovada em todas as versoes historicas. O template de leitura exige 0.3.1 pelos
seus scripts/recursos; o observador exige 0.4.0. Compile com API atual e teste no
loader/perfil que anunciar; nao remova requisitos para forcar inicializacao.

## Manifesto e capacidades

[Contrato completo](SDK-CONTRACT.md) documenta UTF-8 e campos. Campos desconhecidos,
formatos/API/perfil/capacidades incompativeis recusam. Defaults atuais: manifestVersion=1,
minLoaderVersion=0.1.0, dependencias/opcionais/conflitos/permissoes/capacidades vazias.
apiVersion e gameProfile sao obrigatorios; id segue letras/numeros minusculos com
separadores ponto/hifen. `mainClass` precisa existir no proprio JAR, ser publica,
concreta, implementar Mod e ter construtor publico sem argumentos.

Capacidade nao concede permissao. game.read continua exigida para query.scoped;
events.career.read para observacoes. events.career.observed e condicional a
--probe-career-hooks no loader, nao habilitada pelo SDK. Modo seguro ignora mods.
Dependencias obrigatorias/opcionais presentes ordenam inicializacao; nao fornecem
importacao de classes privadas entre JARs. Conflitos/ciclos/IDs duplicados recusam
o conjunto. Nenhuma permissao e sandbox ou acesso irrestrito a escrita do jogo.

## Java, empacotamento e distribuicao

- Toolchain e CI medidos: **Windows/JDK 17 e 21**, bytecode --release 17. Runtime
  do jogo e hooks seguem sua matriz especifica, nao qualquer Java futuro.
- API e validador sao compilados de fontes explicitas em pastas novas, sem reciclar
  classes de builds anteriores; nao incluem loader completo, ASM ou codigo do jogo.
- API fica no classpath de compilacao, **nao dentro do JAR do mod**. Loader fornece
  os tipos publicos em runtime. Nao sombrear/reempacotar API ou validador.
- SDK inclui codigo original MIT e avisos legais dos recursos Javadoc gerados.
  Nao inclui JRE/JDK nem licenca/conteudo proprietario do jogo.
- Hashes por arquivo e SHA-256 do ZIP detectam alteracao/corrupcao, nao autenticam
  origem nem provam seguranca. Execute ferramentas/scripts apenas de SDK confiavel.
- Build/teste e reproduzivel como procedimento; ZIP/Javadocs podem variar entre
  JDKs, timestamps e builds. Nao se anuncia reproducibilidade bit a bit.
- Instalador nao faz download automatico. Nunca distribuir EXE, saves, classes ou
  bibliotecas extraidas do jogo com seu mod/SDK.

Semantica scoped/threads: [consultas](SCOPED-QUERIES.md),
[observacoes](CAREER-OBSERVATIONS.md). Session/generation de observacoes e scope
de consulta nao sao identidade persistente. Persistencia por carreira P3, eventos
completos, gameplay P6 e gates de release ainda pendentes; API experimental nao e
promessa de plataforma 100% completa nem release estavel.
