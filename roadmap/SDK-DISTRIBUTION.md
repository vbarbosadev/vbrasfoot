# Distribuicao do SDK-only — 0.7.4 experimental

Incremento de P5 para autores: API, validador target-free, contratos/guias, Javadocs,
template e observador SDK-only com testes. Sem loader completo, jogo, saves, logs,
bibliotecas originais, credenciais, JRE ou downloads durante build/test.

## Gerar e verificar (mantenedor)

```powershell
.\release-sdk.ps1 -Output .\dist\sdk-0.7.4-reviewed
.\test-sdk-package.ps1 -Archive .\dist\sdk-0.7.4-reviewed.zip
```

Requer checkout/JDK 17 ou 21 apenas para **gerar**, nao para consumidor compilar um
mod com o ZIP extraido. Nao precisa de build.ps1 ou arquivos do jogo: API e somente
tres classes do validador sao compiladas em diretorios novos, de fontes explicitas,
com sourcepath vazio. Fonte/API antigas em build nao sao recicladas.

Saidas: pasta nova, `.zip` e `.zip.sha256`. Destino existente e recusado, sem limpar
pastas antigas automaticamente. Fontes/template/exemplo/guias entram por allowlist,
nao por copia recursiva de suas pastas de desenvolvimento. Build/dist/lib privados
nao entram. Arquivos gerados pelo Javadoc tem verificacao de layout e avisos legais
preservados. ZIP usa `/` canonico e inclui .gitignore, inclusive no PowerShell 5.

## O que os checks estabelecem

- `sdk-package.json`: formato 1, tipo sdk-only, versao/major/perfil/bytecode e flag
  experimental. `checksums.json` verifica cada arquivo, rejeita extras/faltantes,
  traversal/aliases/links e layout fora da allowlist.
- JAR API so pode ter classes `br.vbrasfoot.api`, manifesto e LICENSE. Validador
  inclui API e somente SdkValidator/ModManifest/ModValidator, sem Main, Profile,
  LegacyRuntime ou ASM. Nao e o loader disfarçado nem um runtime extraido do jogo.
- SHA-256 externo do ZIP e verificado antes de extrair. Teste recusa nomes ZIP
  inseguros/duplicados/links e tamanhos declarados acima dos limites. Execute o
  runner **somente em SDK gerado/revisado e confiavel**: depois ele executa scripts,
  javac/java do pacote. Hashes nao sao assinatura nem autenticam um download hostil.
- Teste extrai fora do checkout, copia os projetos para diretorios novos, personaliza
  ID do template, compila/testa usando somente API e valida os JARs produzidos.
  Fonte do loader/game nao entra no classpath. SDK original permanece hashavel/intacto.
- Fixture cujo initializer/constructor/callback falharia e validada sem executar
  codigo. JAR invalido/argumento desconhecido recusados; nao cria home/cache do loader.
- Negative fixtures de checksum adulterado, arquivo extra e caminho inseguro sao
  dados sinteticos proprios, nunca saves reais do usuario.
- CI Windows/Java 17 e 21 executa geracao/verificacao com permissions contents:read,
  sem upload/publicacao, credenciais novas ou arquivos proprietarios.

Procedimento reproduzivel nao significa ZIP identico bit a bit entre horarios/JDKs.
Checks de manifesto/classes nao comprovam seguranca de mod, conjunto instalado,
UI, save ou gameplay. JAR de mod nao deve embutir API/testes/classes do jogo.

## Publicacao — gate separado

Esta entrega **gera e testa artefatos locais/CI; nao cria release/tag nem publica
SDK automaticamente**. Antes de distribuir publicamente, revisar origem, licencas,
arquivos/hashes e notas de limitacoes; publicar apenas ZIP/sidecar revisados, como
experimental/pre-release, por acao explicitamente autorizada. Nao usar wildcard de
dist: ha fixtures/pacotes privados e prototipos de testes nessa pasta.

Primeiro prototipo usou CreateFromDirectory do .NET Framework e gerou nomes ZIP
com `\`; foi recusado pelo teste. Writer foi corrigido para entradas `/` explicitas.
Prototipo privado rejeitado nao e artefato de publicacao; somente saida revisada
que passou no runner deve ser considerada.

[Guia de autor](SDK-QUICKSTART.md), [compatibilidade](SDK-COMPATIBILITY.md) e
[manifesto](SDK-CONTRACT.md). Usabilidade por terceiro humano e publicacao revisada
ainda pendentes; nenhum aceite integral P5/P7, P3 ou gameplay inferido do teste.
