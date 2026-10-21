# Inventario offline de superficies de mutacao

MutationSurfaceInventory analisa UM arquivo ZIP/JAR explicitamente selecionado,
sem carregar classes ou executar o alvo. Bibliotecas aninhadas exigem analise
separada. SHA256 antes/depois vincula o resultado ao arquivo exato, nao ao nome.
Limites:20000classes/50000entradas/2MiB por classe/256MiB de classes/8milhoes de
instrucoes/8192sites. Entrada duplicada, parse invalido ou quota excedida recusa
o resultado inteiro. Nao aceita inventario parcial como ausencia de mecanismos.

Classifica sites com membro/metodo/descriptor/indice de instrucao:42campos diretos
selecionados, setters reflexivos/Unsafe/FieldAccess, dispatch reflexivo, method
handles/VarHandles, codigo dinamico, metodos native e candidatos imediatos de
metodos read/write de subclasses Serializer/FieldSerializer. Candidatos incluem
serializers embutidos; NAO sao automaticamente customizados, perigosos ou ativos.
SetAccessible/getters NAO sao rotulados como writes. Invokedynamic registra seu
bootstrap, sem resolver todos os argumentos/targets. Hierarquia profunda nao
resolvida, recursos/configuracao/gerados/JDK/external mods nao incluidos.

## Resultado do conjunto original fornecido

Analise da copia privada original (game.jar extraido e4bibliotecas) em
Temp/opencode/surface-inventory-a2333b4910b54c2e8c2e8ecd0136d57a/*.inventory.txt.
Total1317classes/406713instrucoes, sem parse errors ou truncamento.

| Arquivo | Classes | Instrucoes | Observacoes estaticas |
| --- | ---: | ---: | --- |
| game.jar |1038|337581|42PUTFIELD financeiros,0Field.set/Unsafe diretos,2dispatch reflexivos no JarRsrcLoader,1Class.forName e1metodo native components.NumberFormat.toFixed|
| kryo-4.0.2.jar |214|43057|19setters reflexivos,41Unsafe,23FieldAccess,19dispatch reflexivos,4dinamicos,268candidatos de metodos serializer|
| reflectasm-1.10.1-shaded.jar |26|23673|1FieldAccess,1dispatch reflexivo,6dinamicos|
| objenesis-2.1.jar |37|1982|21dispatch reflexivos,5dinamicos|
| minlog-1.3.0.jar |2|420|nenhum site das categorias procuradas|

Hashes: game.jar eeaac13c62d494b8de34cf00ddf89fa78a6202273e0c7a48ca780332611372ed;
Kryo f32aceb5b2eb39be6f339da722c3bb043df163c38d51522acbb77ee4d6c4d362;
reflectasm 01eb0d064149d9b8594da1c21c3dd1928f0869923648d734af97e5e36e9833ba;
objenesis c74330cc6b806c804fd37e74487b4fe5d7c2750c5e15fbc6efa13bdee1bdef80;
minlog f7b399d3a5478a4f3e0d98bd1c9f47766119c66414bc33aa0f6cde0066f24cc2.
O hash de game.jar e do container extraido, nao substitui fingerprint do EXE.

## Descoberta acionavel e sequencia

UnsafeCacheFields$UnsafeRegionField tem putLong/putByte em readSlow e copyMemory
em copy. UnsafeInput/UnsafeMemoryInput tem copyMemory. Esses sites NAO estavam
nos24setters do incremento anterior. O inventario nao determina destino/offset,
configuracao ou selecao runtime: nao afirmar que alteraram campos financeiros.
Proximo objetivo: qualificar caminho/configuracao real e rejeitar ou observar
uso pertinente de regioes sem mascarar as demais lacunas. Native NumberFormat
e dispatch no bootstrap tambem exigem analise de alcance antes de exclusao causal;
nao inferir benignidade/risco pelo nome.

17testes com bytecode GERADO (nunca carregado): categorias/contagem/indices,
determinismo, imutabilidade, getters/setAccessible ignorados, parse/quota invalida,
EDT recusada. universalMutationCoverage=false sempre. Nenhuma admission/lease,
gate, schema persistente, receipt ou prova de inclusao alterada. Sem novas flags
de producao; CLI offline usa o export ASM ja empregado pelos testes de hooks.

Este inventario e evidência estatica de superficie, nao observacao runtime,
analise de aliases/alcance ou sandbox contra Java arbitrario.0sites em categoria
nao significa ausencia de comportamento. Mantem APPLIED/UNKNOWN bloqueados.
