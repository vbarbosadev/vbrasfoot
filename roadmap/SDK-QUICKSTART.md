# Primeiro mod com o SDK independente — 0.9.0 experimental

Guia para o **ZIP SDK-only**, sem checkout, EXE, saves, Maven, rede ou classes do
jogo para compilar/testar. Para jogar, voce precisa separadamente do loader portable
compativel e da copia local conhecida do Brasfoot22-23, com licenca propria.

## 1. Preparar

- Windows/PowerShell e JDK **17 ou 21**, com java/javac/jar no PATH.
- Extraia o ZIP SDK-only num diretorio novo. Confirme origem e SHA-256 por canal
  confiavel; sidecar/checksums detectam corrupcao, **nao autenticam um download**.
- Rode `verify.ps1` na raiz extraida. Ele verifica allowlist, hashes e namespaces
  sem inicializar classes. Nao execute scripts/ferramentas de SDK nao confiavel.

Estrutura do SDK-only:

```text
README.md / LICENSE / sdk-package.json / checksums.json / verify.ps1
api/vbrasfoot-api-0.9.0.jar
tools/vbrasfoot-validator-0.9.0.jar
docs/                 # contratos, compatibilidade e este guia
javadoc/index.html
template-read-only/   # copie para seu projeto
examples/career-observer/
```

O pacote portable do loader tem layout diferente: API/validador/guias em `sdk/`.
Os comandos abaixo usam o SDK-only. API e codigo original MIT; jogo/JRE nao sao
redistribuidos. Avisos legais dos recursos gerados pelo Javadoc sao preservados.

## 2. Criar projeto novo

No PowerShell, ajuste os dois caminhos para pastas suas:

```powershell
$SdkRoot = 'C:\Downloads\vbrasfoot-sdk-0.9.0'
$Project = 'C:\Mods\meu-primeiro-mod'
if (Test-Path -LiteralPath $Project) { throw 'Escolha projeto novo; nao sobrescrever.' }
Copy-Item -LiteralPath (Join-Path $SdkRoot 'template-read-only') -Destination $Project -Recurse
```

Crie previamente a pasta pai do projeto, se necessario. Edite SOMENTE sua copia:

- `vbrasfoot.mod.properties`: troque `id`, `name` e `version` por valores proprios.
  ID exemplo: `br.meunome.consulta`, usando letras minusculas/numeros e pontos/hifens.
- Se mudar o ID, ajuste nome de destino no `install.ps1` da copia, evitando colisao.
- O template funciona sem mudar pacote/classe Java. Se renomear, ajuste arquivos,
  package, mainClass e runner do teste em build.ps1 de forma consistente.
- Declare somente permissoes/capacidades utilizadas; nao existe game.write generico.

Veja a [especificacao do manifesto](SDK-CONTRACT.md) e
[compatibilidade/minimos](SDK-COMPATIBILITY.md). Nao copie nomes ofuscados, reflection,
JAR do loader, API ou bibliotecas/classes do jogo para dentro do seu mod.

## 3. Compilar, testar e validar sem jogo

```powershell
& (Join-Path $Project 'build.ps1') -Sdk (Join-Path $SdkRoot 'api\vbrasfoot-api-0.9.0.jar')
java -jar (Join-Path $SdkRoot 'tools\vbrasfoot-validator-0.9.0.jar') (Join-Path $Project 'dist\meu-mod.jar')
```

Build usa apenas a API publica e Java, com --release 17. Teste usa fake SDK proprio,
sem original do jogo. JAR final contem somente classes do mod, manifesto e licenca;
testes/API nao entram. Nao necessita do fonte do loader ou de instalar o jogo.

Validador liga/verifica classe publica/concreta/construtor, **sem** inicializar
classe, construir mod ou chamar callbacks. Nao e sandbox/auditoria, nao resolve
dependencias instaladas nem comprova comportamento ou compatibilidade de save.
Codigo Java de mod continua sendo confiavel. Nunca valide material nao confiavel
como se esse comando fosse isolamento contra ataques ao JVM/class parser.

## 4. Instalar e usar (somente copia isolada)

Com loader portable instalado e jogo fechado:

```powershell
$GameDir = 'C:\Jogos\Brasfoot22-23-copia'
& (Join-Path $Project 'install.ps1') -GameDir $GameDir -Preview
& (Join-Path $Project 'install.ps1') -GameDir $GameDir
& (Join-Path $GameDir 'vbrasfoot.cmd') --validate-only
& (Join-Path $GameDir 'vbrasfoot.cmd')
```

Instalador do template recusa JAR existente/links/sessao ativa; nao atualiza ou
apaga arquivos automaticamente. Carregue carreira e abra **vbrasfoot → Exemplo:
consultar clubes**. Nao precisa salvar. Renomeie tambem rotulo da acao no Java se
quiser personalizar o menu. Executavel original nao e modificado; abrir so o EXE
original nao carrega seu mod. Preserve saves e licenca do jogo.

Gerencie proximo inicio pelo painel; sem hot reload. Dados de ModContext ainda sao
globais por mod, **nao por carreira**. Nao persista ClubRef/CareerScope como IDs de
carreira. Paginas nao sao transacao ou snapshot consistente de todo o jogo.

## 5. Segundo exemplo: observacoes experimentais

Copie `examples/career-observer` para outro projeto novo e execute seu build.ps1
com o mesmo `-Sdk`. Ha teste proprio usando apenas API. Valide seu JAR com a mesma
ferramenta. O exemplo declara somente `events.career.read`, requer capacidade
`events.career.observed` e loader 0.4.0+. Para executar, flag `--probe-career-hooks`
e obrigatoria; nao copie esse JAR para mods se quer continuar iniciando sem hooks.
O SDK-only nao liga hooks ou instala o exemplo automaticamente.

[Contrato das observacoes](CAREER-OBSERVATIONS.md): nao e evento completo de unload,
save duravel, partida, temporada ou identidade permanente. Sem persistencia P3
ou escrita de gameplay. Nao use fila descartavel como journal de dados por carreira.

## 6. Relatar resultado / erros comuns

| Erro | Acao |
| --- | --- |
| javac/java/jar nao encontrado | Usar JDK 17/21 no PATH; nenhum download automatico |
| API/minLoaderVersion/capacidade recusada | Conferir contrato; nao apagar campos para burlar incompatibilidade |
| mainClass ausente ou classe invalida | Corrigir package/classe/manifesto na sua copia e recompilar |
| Dependencia/conflito de mods | Validar conjunto pelo launcher; ferramenta SDK valida so um JAR |
| Handle obsoleto/expirado | Consultar novamente; nao retry infinito nem reutilizar grafo anterior |
| JAR existente/sessao aberta | Fechar normalmente e revisar somente seu JAR; sem remover saves ou toda pasta mods |

Diagnostico local pode ser revisado no painel e exportado explicitamente; nao envie
JAR do jogo, saves, licenca ou logs privados sem revisao. [Guia](DIAGNOSTIC-EXPORT.md).

Teste automatizado do projeto extrai o SDK fora do checkout, personaliza ID,
compila/testa os dois projetos contra API e valida sem jogo. **Nao substitui** um
terceiro humano seguindo este guia nem o aceite integral de P5/P7. Publicacao
estavel e gates de lifecycle/persistencia/gameplay continuam separados.
