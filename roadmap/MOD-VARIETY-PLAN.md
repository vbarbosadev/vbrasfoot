# Entregas para variedade de mods

Pedido do usuario: implementar todas as frentes propostas. Nao significa
autorizar inferencia de gates ou modificar saves de usuario para obter aceite.

| Frente | Entrega/estado | Evidencia necessaria para liberar o restante |
|---|---|---|
| Mais leituras | 0.9.0: sete habilidades e scout somente leitura | Campos adicionais: mapear rotulos, getters sem efeito e testar individualmente |
| Criacao pronta | Trace0.8.2 corroborou78markers AB/V/ap/at/ax e painel2; UNKNOWN | Distinguir AB de temporada/cancelamento/falha; painel nao prova prontidao |
| Persistencia por carreira | Nao liberada; armazenamento global nao substitui sidecar | Identidade/associacao/copia/rename/restore, protocolo de recuperacao e testes de conflito |
| Eventos de calendario | Consulta atual existe; eventos nao liberados | Fronteiras reais e falhas/reentrada; sem inventar evento por polling |
| Eventos de partida/temporada | Nao liberados | Mapear execucao original e confirmar variantes sem jogar automaticamente |
| Comandos de gameplay | Nao liberados | Um comando por vez, regras/rollback/conflitos, copia de save, reabertura original e aceite humano |
| Autoria com IA | Contrato/validador/SDK-only e exemplos | Autor externo seguindo apenas docs; nao declarar aceite por teste automatizado |

Sequencia: concluir primeira entrega de leitura com CI/PR/pacotes; depois
investigar classificacao das origens AB/AC/pn (sem pedir outra carreira antes
de marker concreto), desenhar identidade/recuperacao antes de P3 e mapear
eventos. Gameplay apenas depois dos respectivos gates; nao chamar API bruta
de escrita refletiva nem habilitar capacidade generica game.write.

0.9.0 nao fecha este plano. Nao ha publicacao/tag estavel, promessa de prazo
ou execucao em segundo plano apos a sessao.
