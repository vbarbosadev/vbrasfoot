# Inspetor de clubes e jogadores

Mod de consulta para o perfil Brasfoot22-23 suportado. Não altera jogadores,
dinheiro, data ou saves. Consulte o manifesto para permissões/ID.

No catálogo (branch `mods`), compile com JDK17+:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build-mod.ps1 -Mod inspetor
```

Copie somente `dist/inspetor/inspetor.jar` para `vbrasfoot/mods` com o jogo fechado.
Use o loader de autores 0.16.0+ por `vbrasfoot-mods.cmd`; abra a ação do inspetor
no menu vbrasfoot depois de carregar uma carreira. A disponibilidade das consultas
depende da carreira atual; não use IDs/index como identidade persistente.

Para remover, feche o jogo e retire esse JAR. Preserve saves e dados de outros mods.
Código do projeto sob MIT, sem jogo/bibliotecas proprietárias incluídos.
Compilação automatizada não equivale a aceite manual em todas as resoluções/JVMs.
