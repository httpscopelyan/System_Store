# REVISION — melhorias e correções longas por rodada

Registro das entregas mais longas/estruturais de cada rodada. Concertos curtos e
pontuais ficam em `FIXES.md`.

---

## 2026-10-17 — Pagamento em dinheiro (Fase 1, RF25/RF26, Opção A)

Feature fechada ponta a ponta no backend: a venda deixou de finalizar "solta" e
passou a exigir um pagamento gravado (RN09). Em F1 só se processa **DINHEIRO**;
CARTAO/PIX e pagamento múltiplo (RN18) ficam na F4.

### Modelo e schema
- Enum `PaymentMethod {DINHEIRO, CARTAO, PIX}`.
- Entidade `Payment` (`sale_id`, `method`, `amountReceived`, `change`,
  `createdAt`) + `PaymentRepository`. Opção A: **um pagamento por venda** em F1,
  evolui para 1:N no RN18/F4 (ver ADR 0011).
- Migration **V7**: `ALTER sale.status TYPE VARCHAR(20)` (para caber o novo estado)
  + `CREATE TABLE payment`.
- Novo estado `EM_PAGAMENTO` em `Sale.Status`, entre ABERTA e FINALIZADA (RN21).

### Service (`SaleService`)
- `goToPayment` — ABERTA + com itens → `EM_PAGAMENTO`.
- `calculateChange` — troco com `HALF_UP`.
- `pay` — `@Transactional`: valida `EM_PAGAMENTO`, aceita **só DINHEIRO**, exige
  recebido ≥ total; grava `Payment`, baixa estoque e marca `FINALIZADA` (RN09).
- Guarda de estado no `addItem` — só permite adicionar item em venda `ABERTA`
  (reforço da RN21).

### UI (JavaFX — sem teste automatizado)
- `pagamento.fxml` + `PaymentController` (visual azul-marinho, total e troco em
  fonte grande; recalcula troco a cada digitação).
- `SaleController.onFinalize` agora chama `goToPayment` e navega para a tela de
  pagamento.
- `UserSession` passou a guardar também a `vendaAtual`.

### Testes e verificação
- 9 testes novos em `SaleServiceTests`: `goToPayment`; `pay` feliz, exato e
  insuficiente; forma de pagamento inválida; estado inválido; `calculateChange`;
  `addItem` fora de ABERTA.
- Suíte total **36 testes verde** (`mvn test`, JDK 26).
- **Segurança aprovou** a feature.

### Ressalvas
- `PaymentController` / `pagamento.fxml` são UI e **não têm teste automatizado** —
  validar manualmente com `mvn javafx:run` (finalizar venda, digitar recebido,
  conferir troco e status FINALIZADA).
- **Dívida técnica (Segurança):** `SaleService.finalizeSale` ainda finaliza a venda
  e baixa estoque **sem gravar `Payment` e sem guarda de estado**. Hoje não está
  ligado a nenhuma tela (o fluxo de UI passa por `goToPayment` → `pay`), só é
  exercido por testes. Deve ser deprecado/protegido no futuro para não restar um
  caminho de finalização sem pagamento registrado.

### Documentos tocados
`MAPA-REQUISITOS.md` (RF25/RF26/RN09/RN21), `ESPECIFICACAO-PROJETO.md`
(seções 5, 8, 9.4, 10, 12, 13, 14 — ADR 0011 + nota na 0010), este `REVISION.md`.
