# Scout somente leitura — SDK 0.9.3 experimental (requer loader0.9.0+)

Consulta manual paginada (50 clubes/jogadores), filtros por nome de clube e
ordenacao numerica da pagina de elenco. Sete habilidades individuais aparecem
somente quando habilitadas na carreira. Vazio nao significa zero. Sem pontuacao
inventada, salarios/contratos, varredura automatica de todo o mundo ou escrita.

```powershell
.\build.ps1 -Sdk 'C:\SDK\api\vbrasfoot-api-0.9.3.jar'
java -jar 'C:\SDK\tools\vbrasfoot-validator-0.9.3.jar' --json .\dist\player-scout.jar
```

Requer game.read/ui.register, query.scoped/query.player.details/ui.panel.embedded.
Nao requer sonda, nao persiste handles nem altera saves. Erros limpam selecao e
dados; refaca a busca de clubes. IDs/codigos nao sao identidades persistentes.
Testes do modelo sao sinteticos; aceitacao visual no jogo continua separada.
Codigo deste exemplo sob a licenca MIT do repositorio.
