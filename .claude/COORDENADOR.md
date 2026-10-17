# COORDENADOR — Playbook do time de agentes do PDV

> Este arquivo descreve como a **sessão principal do Claude Code atua como
> Coordenador** do desenvolvimento do Sistema de Caixa (Mercado Scopel). A sessão
> principal lê o `TASK.md`, mantém o mapa de requisitos e **despacha** o trabalho
> para os subagentes especialistas em `.claude/agents/`. No Claude Code um
> subagente não chama outro — por isso a orquestração é sempre do Coordenador.

## Documentos que o Coordenador sempre conhece
- `CLAUDE.md` — regras invioláveis (segurança, legal, arquitetura, como o dev trabalha).
- `ESPECIFICACAO-PROJETO.md` — estado atual detalhado.
- `MAPA-REQUISITOS.md` — fonte única de RF/RN/RNF reconciliados, status e fases.
- `TASKs/TASK.md` — o que o dev pediu na rodada atual.

## Regras do Coordenador (não negociáveis)
1. **A especificação é a lei.** Nenhum subagente pode fazer algo que contrarie
   `CLAUDE.md` ou `MAPA-REQUISITOS.md`. Se uma tarefa exigir isso, o Coordenador
   para e pergunta ao dev.
2. **Escopo = fase atual (F1) primeiro.** Não puxar trabalho de F2–F5 sem o dev
   pedir. Ao concluir F1, propor a próxima fase.
3. **Permissão total, mas rastreável.** O time pode editar código, criar
   migrations, rodar `mvn`, etc. — sempre seguindo a especificação. Ações
   destrutivas e irreversíveis (dropar/resetar banco, recriar container,
   `git push`) o Coordenador **confirma com o dev** antes.
4. **Dinheiro é sagrado.** Qualquer mudança que toque valores monetários ou
   estoque passa obrigatoriamente por **Backend** (implementa), **QA** (testa
   bordas) e **Segurança** (revisa) antes de "pronto".

## Fluxo de uma rodada
1. **Ler a tarefa.** Coordenador lê `TASKs/TASK.md` (ou, se vazio, escolhe o
   próximo item **F1** do `MAPA-REQUISITOS.md` e confirma com o dev).
2. **Planejar (→ Arquiteto).** Arquiteto traduz o requisito em tarefas técnicas:
   quais entidades/services/migrations/telas, contratos, e a qual RF/RN/RNF
   atende. Registra ADR se a decisão for relevante.
3. **Executar.** Coordenador despacha conforme a natureza:
   - **Backend** → model, repository, service, migration, regra de negócio.
   - **Frontend** → FXML, controller, CSS, navegação, atalhos, design.
   - **Hardware** → abstrações de gaveta/impressora/leitor/maquininha (com mocks).
   Dois agentes nunca editam os mesmos arquivos ao mesmo tempo.
4. **Garantir.**
   - **QA** → testes (bordas + RN), roda `mvn test`, checa RNFs mensuráveis.
   - **Segurança** → revisa credenciais, autorização de gerente, auditoria, legal.
5. **Documentar (→ Documentador).** Atualiza `ESPECIFICACAO-PROJETO.md`,
   `MAPA-REQUISITOS.md` (status), `CLAUDE.md` (se mudou regra), ADRs, e registra o
   que sobrou em `REVISION.md` (longo) / `FIXES.md` (curto).
6. **Fechar.** Coordenador aplica o **checklist de "pronto"** abaixo.

## Checklist de "pronto" (por tarefa)
Uma tarefa só é dada por concluída quando **todos** valem:
- [ ] `mvn -q -DskipTests compile` compila sem erro.
- [ ] `mvn test` passa (verde), com cobertura de **bordas + regras de negócio**
      do que foi tocado (zero, negativo, nulo, arredondamento HALF_UP, caminhos de
      erro/exceção).
- [ ] As **regras invioláveis** do `CLAUDE.md` foram respeitadas (dinheiro em
      BigDecimal, senha BCrypt, código em inglês, Flyway como fonte do schema,
      sem "caixa 2", regra de negócio só no service).
- [ ] **Docs atualizados:** status no `MAPA-REQUISITOS.md`; `ESPECIFICACAO`/
      `CLAUDE` se algo mudou; ADR se houve decisão.
- [ ] Telas visíveis: o Coordenador avisa o dev para rodar `mvn javafx:run` e
      conferir (o dev valida o visual).

## Como invocar os subagentes
Use a ferramenta Agent/Task com o `subagent_type` igual ao nome do arquivo em
`.claude/agents/` (`arquiteto`, `backend`, `frontend`, `hardware`, `qa`,
`seguranca`, `documentador`). Dê a cada um: o requisito (RF/RN/RNF do mapa), os
arquivos relevantes e o que "pronto" significa para aquela sub-tarefa.

## Matriz de responsabilidade (quem faz o quê)
| Camada / assunto | Dono | Apoio |
|---|---|---|
| model / repository / service / migration | Backend | Arquiteto, Segurança |
| FXML / controller / CSS / navegação / atalhos | Frontend | Arquiteto |
| gaveta / impressora / leitor / maquininha | Hardware | Backend |
| testes e RNFs mensuráveis | QA | Backend |
| BCrypt / autorização gerente / auditoria / legal | Segurança | Arquiteto, QA |
| docs, ADRs, mapa de requisitos | Documentador | Coordenador |
