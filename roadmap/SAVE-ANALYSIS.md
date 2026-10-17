# Investigacao offline da diferenca binaria antiga

## Identidade e escopo

Alvo: Brasfoot22-23, EXE SHA-256
`2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c`.
Input preservado SHA-256
`f9918072e724521a6ce5efaad8774db78fa2b2bd704e7acf0007f58fdcfcfcc9`;
round-trip SHA-256
`fc23e9d74ee92d12e4da9565f9c8c2b70e28249f731a1898bd7bec09a3990b47`.
Ambos tem 74.283.590 bytes. Essa investigacao usa as copias privadas do teste
antigo, nao regrava o save do usuario nem invoca carregar/salvar pela UI.

## Observacoes

- 825.628 posicoes de bytes diferem; primeiro offset (base zero): **8.911.369**.
- Sonda de campos do Kryo localizou esse offset em `best.F.dm`, campo String de
  nome do jogador dentro de `best.f.ag`. Valor observado: **String vazia**.
- No input, o trecho comeca com `01 81` (objeto novo/String vazia); na regravacao,
  `B1 37` (referencia Kryo ja existente). A sonda e instrumentada somente offline;
  seu output tem exatamente o hash da regravacao sem instrumentacao anterior.
- Fixture minima com dois objetos String vazios distintos: depois de ler com
  Kryo 4.0.2, ambos apontam para a mesma String; valores iguais, bytes diferentes
  ao regravar. Isso reproduz o mecanismo sem usar classes do jogo na fixture.

**Conclusao delimitada:** a primeira divergencia e explicada pela normalizacao de
identidade de Strings vazias na desserializacao e pela reemissao de referencias.
As demais diferencas nao foram atribuidas byte a byte. Mudancas nos indices/fluxo
de referencias subsequentes sao uma explicacao consistente, nao prova individual
de cada byte. Nao se afirma que toda diferenca binaria em qualquer save seja benigna.

## Comparacao do grafo decodificado

Foi usado o Kryo original, com `setRegistrationRequired(false)`, para ler as duas
raizes de cada arquivo. O comparador percorre os campos **realmente incluidos**
pelo FieldSerializer original, e nao apenas o snapshot publico de clubes/jogadores.

Resultado na copia antiga:

- 31.742.740 comparacoes de valores/referencias;
- 20.578.147 campos serializados percorridos;
- 5.228.120 objetos mutaveis unicos, somados por raiz;
- 316.351 ocorrencias de Strings;
- zero divergencias, zero estouros do orcamento, zero tipos nao suportados encontrados.

Listas/arrays tem ordem e tamanho comparados; primitivos e numeros sao comparados
por valor (floats/doubles por bits brutos), aliases mutaveis sao verificados em
ambas as direcoes. String e comparada por valor; identidade da String e contada
separadamente, pois a propria leitura ja pode normaliza-la. A topologia e comparada
por raiz: o jogo/Kryo resetam referencias entre as gravacoes das duas raizes.

Calendar/TimeZone sao comparados por tempo, leniencia, semana, ID/regras de fuso e
cutover gregoriano quando aplicavel, nao por seus campos privados de implementacao.
Campos static/transient nao sao objeto desse contrato. Tipos/serializadores nao
suportados, diferencas ou limites excedidos fazem a ferramenta retornar falha.

O comparador passou sete fixtures: valores iguais com normalizacao de String,
mutacao de primitivo, divisao de alias mutavel, troca da ordem de lista, tamanho
de array, payload NaN diferente e grafo ciclico igual. Esses testes corroboram o
instrumento, mas nao estabelecem todas as regras de gameplay.

**Nao equivale a:** equivalencia dos bytes originais, prova de todos os campos
transient/static, semantica documentada de cada campo ofuscado, ou garantia geral
de compatibilidade. Reabertura real continua sendo gate separado.

## Reproduzir

JDK 17+, copia local do jogo conforme README e inputs privados existentes:

```powershell
.\analyze-save.ps1 `
  -Original .\verification\save-14473687435780074918\input.s22 `
  -RoundTrip .\verification\save-14473687435780074918\roundtrip.s22
```

O script valida/extrai o runtime local, compila somente sondas proprias e gera
outputs novos em `build/save-analysis-*/`, incluindo o save privado da sonda.
Compara hashes dos inputs antes/depois e exige que a sonda reproduza o hash do
round-trip informado. Usa JARs proprietarios apenas no classpath local, nao os
redistribui. `-Focus` escolhe o offset da sonda; padrao e o primeiro offset antigo.

Nao se deve inserir essas sondas/instrumentacao no launcher de producao. Relatorios
podem conter nomes ofuscados e caminhos locais; revisar antes de compartilhar.
Saves/resultados binarios ficam ignorados pelo Git.
