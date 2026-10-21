# Perfil amostrado do serializer original do ledger

Continua o inventario offline: o Kryo empacotado contem UnsafeRegionField que
pode escrever blocos de memoria. Presenca estatica nao demonstra seu uso. Javap
do FieldSerializer original mostra useMemRegions=false no construtor e getter
getUseMemRegions. Nao presumimos que a configuracao permaneceu igual no runtime.

LedgerSerializerProfile.inspect roda somente na EDT e amostra o serializer EXATO
efetivamente passado ao Kryo, depois da chamada original retornar no hook da
referencia. Nao modifica configuracao, troca serializer ou relê valores financeiros.
Exige runtime/objeto best.C exatos, FieldSerializer por identidade de Class,
getType igual ao ledger, getUseMemRegions=false e getSerializeTransient=false.

Exige14cached fields completos e distintos, declarados na MESMA best.C:
dK/dN/dR/dS long e os outros10int. Campos static/transient/synthetic, desconhecidos,
duplicados, ausentes ou tipo alterado recusam. Cada cached route deve ser a classe
original int/long Unsafe/ASM/Object daquele runtime; rotas custom/genericas/region
nao qualificam. Nao basta o nome FieldSerializer ou14argumentos numericos iguais.

HookProbe so atribui SerializerKind.FIELD_SERIALIZER ao ledger que passa por essa
amostragem. Falhas/configuracoes especiais/metadata indisponivel sao OTHER e a
captura de referencia fica UNSUPPORTED_SERIALIZER quando a identidade coincide.
Isso e uma RESTRICAO da qualificacao diagnostica, nao do comportamento do Kryo:
nenhuma excecao do serializer e suprimida, nenhum write bloqueado/substituido.
Nao altera admissibilidade de comando/gate/interlock ou schema de save/metadados.

## Evidencia

14checks target-free com metadata GERADA propria validam EDT/tipo/runtime,
regioes/transients,14campos, cached null/custom/duplicata/falta. Fixtures nao
atestam autenticidade, que continua no loader/fingerprints originais; a verificacao
nao e um sandbox que torna um ClassLoader arbitrario confiavel.

232checks financeiros ORIGINAIS em
Temp/opencode/ledger-profile-original-04835af8fe874bee99a916b53d162339/runtime-test.log:
12checks adicionais sobre serializers DESCARTAVEIS da classe real best.C, sem
alterar o serializer selecionado pelo writer: default suportado, memoria/transient
recusados, tipo/runtime/null/field null/duplicado/removido recusados e restauração
somente da fixture. Captura real dos3UUIDs continua com ledger exato via writeObject,
15argumentos finais esperados, persistencia/reopen/save-reload/historico bloqueado
preservados. Copia de outra copia privada, EXE/input hash-conferidos.

## Limites e proximo objetivo

Perfil e amostrado APOS retorno; NAO prova metadata durante toda a chamada, nem
exclusao universal de UnsafeRegionField/UnsafeInput em outros lugares. Nao inspeciona
offset/accessIndex/Unsafe alias ou referencias internas. Java hostil pode adulterar
metadata entre leituras; estabilidade dos14campos nao e isolamento/causalidade.
Nao afirmar que essa amostra prova que todas as escritas ou bytes sao suportados.

Rotulo persistente antigo EXPECTED_LEDGER_ARGUMENT_OBSERVED nao contem versao do
perfil: **NAO reinterpretar historicos antigos** como se tivessem passado pelo
perfil novo. Nesta versao, a verificacao e somente live/report e negativa para
casos nao suportados. Evolucao persistente exigira formato/manifest versionado,
sem promover snapshots anteriores. operationInclusionProven=false/writeAuthority=false.

Proximo objetivo viavel: identidade/aliases de ledger no conjunto de clubes e
limites de membership, mantendo criterios conservadores e sem chamar init/progress.
Depois: manifesto persistente versionado de coverage e decisao de inclusao apenas
quando houver prova suficiente. Sem nova sessao humana/mod/public writes/instalacao.
