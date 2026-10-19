# REVISION — melhorias e correções longas por rodada

Registro das entregas mais longas/estruturais de cada rodada. Concertos curtos e
pontuais ficam em `FIXES.md`.

---

## 2026-10-17 — Refatoração de navegação: shell único + Navigator (Fase 1)

Refatoração **estrutural só de UI** (nada de service/model/migration). Trocou a
navegação por `stage.setScene(...)` — que recriava a janela e causava salto — por
um **shell único persistente** com roteamento central. Suíte **36 testes, 0
falhas**: nenhuma regressão, como esperado por ser só UI (ADR 0012).

### O que mudou
- **Shell único persistente:** `main_layout.fxml` + `MainController` — `BorderPane`
  com ToolBar de ícones, menu lateral e barra de status **fixos**; só o centro
  troca de página. A janela nasce uma vez (**1365×720**) e não redimensiona mais.
- **Roteador central:** `config/Navigator` (`@Component`, `bind(BorderPane)` +
  `navigate(Page)` com `setCenter`, carregando o FXML pela controller factory do
  Spring) e enum `config/Page` (INICIO, VENDA, CAIXA, PRODUTOS, PAGAMENTO). Os
  controllers de página passaram a chamar `navigator.navigate(...)` em vez de
  montar `Scene`/`Stage`.
- **Login fora do shell:** transição login→sistema via `scene.setRoot(...)`, não
  `stage.setScene`.
- **Rotas corrigidas:** abrir caixa → **Venda** (antes ia para Produtos); removido
  o botão "Ir para Venda" da tela de Produtos.
- **Páginas padronizadas:** inicio/sale/caixa/pagamento/create_product sem
  `prefWidth/prefHeight`; cabeçalho padronizado `.page-header`; `inicio.fxml` virou
  só a página inicial (as barras migraram para o shell). Foco do campo de pagamento
  via `Platform.runLater`.

### Ressalvas / pendências
- **Validação visual pendente:** todo o shell e as páginas são JavaFX e **não têm
  teste automatizado** — validar com `mvn javafx:run` (navegar entre as 5 páginas,
  conferir janela fixa e rotas, em especial abrir caixa → Venda).
- **Pendência menor:** o `CaixaController` ainda usa campo local `caixaAtual` em vez
  de ler da `UserSession` — a unificar numa rodada futura.
- **Não** marca nenhum RF como ✅: foi entrega de UI; o `MAPA-REQUISITOS.md` não
  muda status por isso.

### Documentos tocados
`CLAUDE.md` (seção 3, JavaFX), `ESPECIFICACAO-PROJETO.md` (seções 4.3, 10, 13 e
ADR 0012 na 14), este `REVISION.md`.

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
