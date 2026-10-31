# Implementation Plan — PDV (store_system/beta)

Author: planning session (Opus), 2026-10-31.
Executor: Sonnet, later session. Read this file **completely** before touching code,
together with `CLAUDE.md`, `MAPA-REQUISITOS.md` and `.claude/COORDENADOR.md`.

Language policy of this repo is unchanged: **code and identifiers in English,
user-facing strings and docs in Portuguese**. This plan is written in English on
purpose; the UI texts quoted inside it are the literal Portuguese strings to ship.

---

## 0. How to use this plan

* Phases are ordered by dependency. Do not start a phase before the previous one
  compiles and `mvn test` is green.
* Every phase has: **Goal → Files → Design → DB → Rules → Tests → Acceptance**.
* One commit per phase (or per sub-item inside a phase), message in Portuguese,
  conventional prefix (`feat(...)`, `fix(...)`, `refactor(...)`, `chore(...)`).
* Build/test environment (confirmed): `./mvnw` with `JAVA_HOME` pointing at **JDK 26**
  and the Docker container `db_scopel` up with `-p 5432:5432`. App runs with
  `mvn javafx:run` (never the IntelliJ green button).
* Flyway: **never edit or rename an applied migration**. Before creating one, list
  `src/main/resources/db/migration/` and use the next free `V` number. At planning
  time the highest is **V28**, so the numbers below (V29, V30, V31) are expected but
  must be re-checked.
* Requirement IDs allocated by this plan: **RF54–RF72, RN22–RN30, RNF17–RNF18**
  (highest in use today: RF53, RN21, RNF16). Register them in `MAPA-REQUISITOS.md`
  in Phase 8.

---

## 1. Global code rules (apply to every phase, no exceptions)

### 1.1 No comments in code

* **Zero comments** in `.java`, `.fxml`, `.css` and in **new** `.sql` migrations:
  no `//`, no `/* */`, no Javadoc, no `<!-- -->`, no `--` in new SQL.
* Intent is expressed by **names**: rename a variable or extract a private method
  with a descriptive name instead of writing a comment.
  `validateQuantity` + `rejectFractionForUnitProduct` beats a comment explaining the rule.
* When you touch a file that already has comments, **delete the comments of that
  file** as part of the change (the current codebase is full of them; they go away
  file by file, as files are touched — no separate mass-deletion commit).
* **Exception (hard rule):** already-applied migrations (`V1`–`V28`) are immutable —
  do not strip their comments. Only new migrations are comment-free.
* Requirement traceability (`RF`/`RN`) must **not** live in comments anymore. It
  lives in `MAPA-REQUISITOS.md` and in **test method names**
  (e.g. `lowStockListsProductWhenDifferenceIsExactlyTheMargin_RN22`).

### 1.2 Object orientation and file division

* Layering stays `controller → service → repository → model`. Business rules and
  money arithmetic live **only in services**. Controllers wire UI and delegate.
* **Size limits (RNF18):** any `.java` file ≤ **400 lines**; controllers ≤ **200 lines**;
  one public type per file. When a file would cross the limit, split it by
  responsibility, never by "part 1 / part 2".
* New sub-packages to create and use:
  * `controller/support/` — UI collaborators (focus policy, table presenters,
    popups, input parsers, bindings). Plain classes, no `@Component` unless they
    need injection; constructed by the controller.
  * `service/alert/` — low-stock alert model, rule and text formatting.
  * `notification/` — outbound notification ports and implementations (e-mail),
    same port/adapter style already used by `hardware/CardPaymentGateway` +
    `MockCardPaymentGateway`.
* A controller that grows past the limit is split into: the controller (actions and
  wiring) + a `...Presenter` (columns, formatting, cell factories) + focused
  collaborators. `SaleController` (611 lines today) and `EstoqueController` are
  explicitly covered by Phases 4 and 5.
* FXML: extract repeated blocks into fragments included with `<fx:include>` when a
  single FXML passes ~150 lines or when the same block appears on 3+ screens
  (e.g. the Back bar of Phase 1).

### 1.3 Money and quantity (unchanged, restated)

`BigDecimal` only, `NUMERIC(10,2)` money / `NUMERIC(10,3)` quantity, `HALF_UP`,
barcode is `String`, `@Transactional` for multi-table operations, price frozen on
the sale item (RN19).

### 1.4 Authorship

No `Co-Authored-By`, no "Generated with ..." footer, no AI mention in commit
messages, PR descriptions, code or docs. The dev is the only author. See Phase 0.

### 1.5 Cost terminology (RF72)

Anything whose **origin is a cost** is called `custo` in the UI and `cost` in the
code. Never "Vr. Custo", "Preço de custo", "Valor pago", "Custo unit.", `preco`,
`valor` or `paid` for a cost. Qualifiers always start with the word: **Custo**,
**Custo unitário**, **Custo médio**, **Custo total**. Enforced by Phase 2.5 and
mandatory for every new label, field, column header, message and identifier created
in later phases.

---

## Phase 0 — Repository and authorship cleanup (do first, needs the dev's OK)

**Goal:** GitHub shows the dev as the only author/contributor and no AI co-author
anywhere in history.

**Findings (verified):**

* Two commits carry `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`:
  * `f68b3f8` "checkpoint: time de agentes, testes, correções e fluxo de pagamento"
  * `cb06c79` "feat(inicio): tela inicial (menu) do PDV exibida apos o login"
* History has **two author identities, both the dev**:
  `Scopel_Yan <160073076+ScopelYann@users.noreply.github.com>` and
  `Yan Victor Scopel <devscopelyan@gmail.com>`. GitHub counts them as two
  contributors.
* Remote: `https://github.com/httpscopelyan/System_Store.git`.

**Steps:**

1. Future commits: never add co-author/footer lines (rule 1.4). Optional guard —
   add `.githooks/commit-msg` that fails when the message matches
   `Co-Authored-By|Claude|Generated with`, and document
   `git config core.hooksPath .githooks` in `HELP.md`.
2. History rewrite — **ask the dev before running it** (it rewrites published
   history and requires a force-push; anyone with a clone must re-clone or reset):
   * Preferred: `git filter-repo --message-callback` stripping any line that starts
     with `Co-Authored-By:`, plus `--mailmap` collapsing both identities into the
     one the dev wants to keep.
   * Fallback without `git-filter-repo`: `git rebase -i --root` / `git filter-branch
     --msg-filter`, same effect, slower.
   * Make a backup branch/tag first (`git tag backup/pre-rewrite`), then
     `git push --force-with-lease origin main` and delete the backup tag locally only
     when the dev confirms GitHub looks right.
3. Verify: `git log -i --grep="co-authored" | wc -l` → `0`;
   `git log --format='%an <%ae>' | sort -u` → one identity;
   GitHub → Insights → Contributors shows one person.
4. Repo settings (manual, by the dev): Settings → Collaborators → remove anyone who
   is not the dev; no GitHub App needs write access.
5. Housekeeping while here: untracked `nome.txt` at the repo root — delete it or add
   it to `.gitignore`; and the still-pending `git rm --cached .env` from the security
   backlog (`REVISION.md`) — commit it in this phase.

**Acceptance:** no AI reference in `git log`, a single contributor on GitHub, `.env`
untracked, working tree clean.

---

## Phase 1 — Foundations: global Back navigation + support packages

**Goal (RF69, RN30):** every page reached by redirect has a "Voltar" that returns to
where the user actually came from.

**Files:**

* `config/Navigator.java` (rewrite the 1-level history), new
  `config/NavigationHistory.java`.
* `controller/MainController.java` + `resources/fxml/main_layout.fxml` (shell Back
  button), new fragment `resources/fxml/back_bar.fxml` + `controller/support/BackBar`
  if a per-page bar is preferred over per-page buttons.
* Per-page Voltar buttons (see list below).
* `resources/css/styles.css`: style class `back-button`.

**Design:**

* `NavigationHistory` holds a bounded `Deque<Page>` (max 10): `push(Page)`,
  `Optional<Page> back()`, `boolean canGoBack()`, `clear()`. It never pushes the
  same page twice in a row and never pushes payment pages
  (`PAGAMENTO*`, `VENDA_FINALIZADA`) — RN30 forbids leaving the payment flow
  backwards; the payment screens keep their own explicit buttons.
* `Navigator` delegates to it: `navigate(Page)` pushes the current page; new
  `back()` navigates to the popped page, falling back to `Page.INICIO`;
  `canGoBack()` exposed for button state. Keep `getPreviousPage()` until
  `PriceCheckController` is migrated, then remove it.
* Shell: one `◀ Voltar` Button as the **first** item of the top `ToolBar` in
  `main_layout.fxml`, wired to `MainController.onBack()`, disabled when
  `!navigator.canGoBack()` (refresh its state at the end of every `navigate`, via a
  listener/callback the Navigator exposes — do not poll).
* Per-page button `Voltar (Esc)` with `styleClass="win-button"` on: `create_product`,
  `edit_product`, `stock_entry`, `stock_adjust`, `batch_form`, `label_form`,
  `price_adjust`, `price_table_form`, `customer_form`, `supplier_form`,
  `purchase_form`, `quote_form`, `receivable_list`, `payable_list`, `report_view`,
  `historico_vendas`, `config_form`, and the new `shopping_list` (Phase 3).
  **Not** on `sale`, `caixa`, `inicio` (primary pages) and **not** on the payment
  screens.
* Esc → back on those same pages, via `controller/support/EscapeToBack` (installs a
  scene event filter when the node enters a Scene and removes it on exit — copy the
  lifecycle pattern already used in `SaleController.bindSaleShortcutsToScene`).
  `sale.fxml` keeps its own Esc semantics (focus) and must not get this helper.
* `PriceCheckController.onBack()` is rewritten to use `navigator.back()`.

**Tests:** `NavigationHistoryTests` (plain JUnit, no Spring): push/pop order, bound
of 10, no duplicate consecutive pages, payment pages never pushed, `canGoBack` on an
empty history, fallback to `INICIO`.

**Acceptance:** Estoque → Novo produto → Voltar returns to Estoque; Estoque →
Entrada → Voltar returns to Estoque; Back is disabled right after login; Back never
re-enters a payment screen.

---

## Phase 2 — Product page: suggested price, minimum stock, text codes

Covers: "cost + margin fills the sale price like a placeholder", "minimum stock
defined at creation", "code may be text".

### 2.1 Pricing suggestion in the service layer (RF65)

* New `service/PricingSuggestionService`:
  * `BigDecimal suggestFor(BigDecimal cost, BigDecimal marginPercent)` →
    `cost × (1 + margin/100)`, scale 2 `HALF_UP`, returns `null` when cost is null or
    ≤ 0 or margin is null.
  * `BigDecimal suggestFor(Product product)` → uses `product.costPrice` and the
    default margin from settings.
  * `BigDecimal marginPercentOf(Product product)` → `(price − cost) / cost × 100`,
    scale 1 `HALF_UP`.
  * `BigDecimal profitOf(Product product)` → `price − cost`, scale 2.
  * Default margin read from `AppSettingService` key **`pricing.default_margin_percent`**
    (default `30`), replacing the two hardcoded `1.30` / `×100` blocks now living
    inside `EstoqueController` and the `×(1+margin)` block inside
    `CreateProductsController`.
* `EstoqueController` and `CreateProductsController` stop computing money and call
  this service (controllers must not do arithmetic on money).

### 2.2 Suggested-price behaviour in the form (RN27)

* New `controller/support/SuggestedPriceBinding`:
  * Observes `costField` and `marginField`; writes the suggestion into `priceField`
    **only while the user has not typed in `priceField`**.
  * "User typed" is detected with a `KEY_TYPED`/`KEY_PRESSED` handler on `priceField`
    (not `textProperty`, which also fires for programmatic writes) setting
    `userTypedPrice = true`; clearing the field (empty text) resets the flag back to
    `false` so the suggestion comes back.
  * While the value is a suggestion, `priceField` carries CSS class
    **`suggested-value`** (new rule in `styles.css`: grey italic text, e.g.
    `-fx-text-fill: #8a9099; -fx-font-style: italic;`) so it reads like a placeholder;
    the class is removed as soon as the user types. JavaFX `promptText` cannot be
    submitted, so the suggestion is **real text visually marked as suggested** — this
    is the intended behaviour: untouched → the suggestion is saved; typed → the typed
    value wins and is never overwritten.
* Same binding reused in `EditProductController` (`edit_product.fxml`).

### 2.3 Missing fields on creation (RF66)

* `create_product.fxml`: add **Estoque mínimo** (`minStockField`, prompt `Ex: 10`)
  and **Categoria** (`categoryField`) next to Quantidade/Unidade; add the `Voltar`
  button from Phase 1. `activeBox` must start **selected** (today a new product is
  saved inactive by default, which is a bug worth fixing here).
* `CreateProductsController`: read both fields, delegate to a new
  `ProductService.create(...)` that validates (description required, unit required,
  price/cost/minStock non-negative, `minStock` scale 3, code required and unique)
  and sets defaults (`minStock = 0`, `category = null`). Keep the controller free of
  validation logic — it only maps fields and shows messages.

### 2.4 Alphanumeric product codes (RF70)

* `Product.code` becomes `length = 30`; new migration
  **`V29__widen_product_code.sql`**: `ALTER TABLE product ALTER COLUMN code TYPE VARCHAR(30);`
  plus `CREATE UNIQUE INDEX ux_product_code_lower ON product (lower(code));`
* `ProductRepository`: add `Optional<Product> findByCodeIgnoreCase(String code)`;
  `ProductService.findCode` and `save/create` use it, so `PAO FRANCES` and
  `pao frances` are the same product and duplicates are refused case-insensitively.
* Normalization rule: `trim()` only (never `upper()`/strip zeros) — leading zeros of
  barcodes must survive. Accepted characters: letters, digits, space, `-`, `.`;
  reject anything else with a clear message.
* Both numeric scanning and typed text codes keep working: the scanner sends digits,
  the operator may type `pao frances`.

**Tests:** `PricingSuggestionServiceTests` (suggestion with/without cost, margin
0/negative, rounding `HALF_UP`, margin from settings, profit/margin of a product);
extend `ProductSearchServiceTests` or add `ProductCodeRulesTests` (case-insensitive
duplicate refused, leading zeros preserved, invalid characters refused, text code
accepted, `minStock` defaults and validation).

**Acceptance:** typing cost 0,80 and margin 80 shows a grey italic `1,44` in Preço de
venda and saves `1,44`; typing `1,50` over it keeps `1,50` even when the cost changes;
a product can be created with code `pao frances`, min stock 10 and category `Padaria`.

---

## Phase 2.5 — Cost terminology standardization (RF72)

**Goal:** one single word for cost across the whole PDV — UI, code, docs — so the
cashier and the dev never have to guess whether "Custo", "Custo unit.", "Preço de
custo" or "valor pago" mean the same thing. Pure renaming/wording: **no behaviour
change, no schema change**, `mvn test` must stay green without touching assertions
(except assertions on the renamed message strings).

**Standard:**

| Concept | UI text (pt-BR) | Java identifier | DB column |
|---|---|---|---|
| Product cost | `Custo` | `costPrice` | `cost_price` |
| Cost of one unit in an entry/purchase | `Custo unitário` | `unitCost` | `unit_cost` |
| Cost of the whole line/entry | `Custo total` | `totalCost` | `total_cost` |
| Weighted average cost (Phase 7) | `Custo médio` | `averageCost` | *(not persisted)* |
| Derived numbers (not costs) | `Margem (%)`, `Lucro (R$)`, `Preço de venda`, `Preço sugerido` | `marginPercent`, `profit`, `price`, `suggestedPrice` | `price` |

Rules:

* UI label, prompt, column header, tooltip and message for a cost **always start with
  the word `Custo`**; no abbreviations (`Custo unit.` → `Custo unitário`), no
  synonyms (`Vr. Custo`, `Preço de custo`, `Valor pago` → `Custo`).
* Java identifiers for a cost **always contain `cost`** (`costField`, `colCost`,
  `unitCostField`, `costValue`); a variable holding a parsed `BigDecimal` is never
  named `...Text` (fix `costText` in `CreateProductsController`, which holds a
  `BigDecimal`, not text).
* Margin, profit, price and suggestion are **not** costs — do not rename them to
  `custo`; they only *derive* from it.
* Already-applied migrations stay untouched (`cost_price`, `unit_cost`, `total_cost`
  are already consistent); **new** columns must follow the table above.

**Inventory to fix (verified at planning time):**

* `resources/fxml/purchase_form.fxml:33` — `promptText="Custo unit."` → `Custo unitário`.
* `resources/fxml/purchase_form.fxml:42` — column `Custo` → `Custo unitário` (the
  column shows `PurchaseItem.unitCost`).
* `resources/fxml/stock_entry.fxml:30` — `Custo unitário (opcional)` already correct;
  keep it as the reference wording.
* `resources/fxml/estoque.fxml:38` — column `Custo` stays (it is the product cost);
  `colSugest` header `Sugest.` → `Preço sugerido` (it is a price, derived from the
  cost) and the `fx:id` becomes `colSuggestedPrice`.
* `controller/PurchaseController.java:85` — `"Qtd ou custo inválidos"` →
  `"Quantidade ou custo inválidos"`, matching `StockEntryController:55`.
* `controller/CreateProductsController.java` — `costText` → `costValue` (and the
  existing `costValue` → `normalizedCost`), `applyMargem()` → `applySuggestedPrice()`
  (Portuguese method name in a code-in-English codebase).
* `controller/EstoqueController.java` — `lucroPercent()` → `marginPercent()`,
  `sugestao()` → `suggestedPrice()`, `colLucro` → `colMarginPercent` (these move to
  `PricingSuggestionService` / `ProductTablePresenter` in Phases 2 and 5 — do the
  rename **there**, not twice).
* `price_check`, `label_form`, `report_view`: check every cost text while passing and
  align it; `LabelService` / `ReportService` / `ReceiptService` must print `Custo`,
  `Custo unitário`, `Custo médio` only.
* Docs: `CLAUDE.md`, `HELP.md`, `ESPECIFICACAO-PROJETO.md`, `MAPA-REQUISITOS.md`,
  `DESIGN_LAYOUT.md` — same vocabulary.

**Glossary (deliverable of this phase):** a short `Glossário` section in
`ESPECIFICACAO-PROJETO.md` with the table above, so later work has one place to check.

**Order note:** run this phase **after Phase 2** (which already renames the pricing
helpers into `PricingSuggestionService`) and **before Phases 3–7**, so every new
screen is born with the standard wording. Phases 5 and 7 must follow the table when
they create `Lucro (R$)`, `Custo médio` and the cost-mode setting.

**Tests / verification:** `./mvnw test` green (adjust only assertions that quote a
renamed message); then these greps must come back empty:

```
grep -rniE "Vr\. Custo|Pre(ç|c)o de custo|Custo unit\.|valor pago" src/main docs *.md
grep -rn "costText\|applyMargem\|lucroPercent\|sugestao(" src/main/java
```

**Acceptance:** every cost on screen reads `Custo` / `Custo unitário` / `Custo médio`
/ `Custo total`; no Portuguese method or field names left in the touched classes; the
glossary exists and matches the code.

---

## Phase 3 — Settings + "Lista d/ Compras" page + e-mail alerts

Covers addition 1 in full.

### 3.1 Settings (RF56)

* New keys (constants in `AppSettingService`), seeded by
  **`V30__seed_alert_and_pricing_settings.sql`** (no comments in the file):

| key | default | meaning |
|---|---|---|
| `stock.min_alert_margin` | `5` | how close to the minimum triggers the warning |
| `pricing.default_margin_percent` | `30` | default markup (Phase 2) |
| `alerts.email_enabled` | `false` | send low-stock e-mail |
| `alerts.email_to` | *(empty)* | recipients, comma-separated |
| `alerts.email_interval_minutes` | `1440` | how often the check runs |
| `alerts.smtp_host` | *(empty)* | SMTP host |
| `alerts.smtp_port` | `587` | SMTP port |
| `alerts.smtp_user` | *(empty)* | SMTP user |
| `alerts.smtp_from` | *(empty)* | From address |
| `store.name` | `Mercado Scopel` | used in the subject/body (reuse the existing key if `storeNameField` already maps to one — check `ConfigFormController` before adding) |

* **The SMTP password never goes to the database or to Git.** It is read from the
  `.env` file as `SMTP_PASSWORD` and injected with `@Value("${SMTP_PASSWORD:}")`.
  Document it in `HELP.md`. If it is empty, e-mail sending is disabled (the page
  still works).
* UI: new card **"Avisos de estoque"** in the settings screen. To respect the size
  limit, build it as a fragment `resources/fxml/config_alerts.fxml` with its own
  `ConfigAlertsController` (`<fx:include>` inside `config_form.fxml`), containing:
  margin field, "Enviar avisos por e-mail" checkbox, recipients field (comma
  separated), interval field, SMTP host/port/user/from, a `Testar envio` button and a
  message label. Validation: each recipient must match a simple e-mail regex;
  interval ≥ 15.

### 3.2 Low-stock rule and service (RN22)

* New `service/alert/`:
  * `record StockAlert(String code, String description, Product.Unidade unit,
    BigDecimal quantity, BigDecimal minStock, BigDecimal suggestedQuantity,
    Severity severity)` with `enum Severity { CRITICO, ATENCAO }`.
  * `StockAlertService` (`@Service`): `List<StockAlert> lowStockItems()`, ordered by
    severity then description.
  * `StockAlertFormatter`: builds the plain-text block used **both** by the page
    preview and by the e-mail, so they can never diverge.
* Rule (RN22), literal from the dev's example (current 15, minimum 10, margin 5 →
  **must warn**):
  * eligible: `active = true` **and** `min_stock > 0` (a product without a defined
    minimum is never listed — otherwise the whole catalogue would show up);
  * listed when `quantity <= min_stock + margin`;
  * `CRITICO` when `quantity <= min_stock`, otherwise `ATENCAO`;
  * `suggestedQuantity = (min_stock + margin) − quantity`, scale 3, floor 0;
  * KG products use the same numeric margin in their own unit (margin 5 = 5 kg);
  * margin comes from `stock.min_alert_margin` and is read per call (changing it in
    Settings takes effect without restart).
* `ProductRepository`: `@Query` returning eligible products ordered, with the margin
  as a parameter — do the filtering in SQL, not in Java (RNF17).
* Text format (Portuguese, as requested):

```
Mercado Scopel — Lista de itens que precisam de ser repostos:

energetico: estoque atual 15: estoque minimo 10 (repor 0)
pao frances: estoque atual 4: estoque minimo 10 (repor 11)

2 item(ns) nesta situacao.
```

### 3.3 Notification port (RF55, RN23)

* New `notification/`:
  * `interface AlertNotifier { void sendLowStock(String subject, String body, List<String> recipients); }`
  * `LoggingAlertNotifier` (`@Primary` when mail is not configured) — writes to the
    log, used in dev/tests; mirrors `MockCardPaymentGateway`.
  * `SmtpAlertNotifier` — `JavaMailSender`; add dependency
    `spring-boot-starter-mail` to `pom.xml`; built only when
    `alerts.email_enabled = true` **and** host/from/`SMTP_PASSWORD` are present.
  * `AlertNotifierSelector` (or a `@Configuration` factory) decides which one is
    used at call time based on current settings — settings change at runtime, so the
    decision cannot be frozen at startup by a profile.
* `StockAlertScheduler` (`@Component`): own `ScheduledExecutorService` with a daemon
  thread (copy the lifecycle of `UpdateService`: first run 60 s after boot, then
  `alerts.email_interval_minutes`, `@PreDestroy` shutdown). It:
  * skips when the list is empty or e-mail is disabled;
  * avoids spam by storing `alerts.last_sent_digest` (hash of the body) and
    `alerts.last_sent_at` in `app_setting`, skipping an identical body inside the
    interval;
  * **never propagates an exception** — any failure is logged and the app keeps
    running (same posture as printing, RNF06). RN23.

### 3.4 "Lista d/ Compras" page (RF54)

* `Page.LISTA_COMPRAS("/fxml/shopping_list.fxml")`; new
  `resources/fxml/shopping_list.fxml` + `controller/ShoppingListController`
  (≤ 200 lines; table wiring goes to `controller/support/ShoppingListPresenter`).
* Layout following the Estoque style (`win-root`, `win-titlebar`, `win-grid`,
  `win-footer`): title "Lista d/ Compras", table columns **Código, Nome, Estoque
  atual, Estoque mínimo, Diferença, Repor, Situação**; `CRITICO` rows get CSS class
  `alert-critical` (red), `ATENCAO` gets `alert-warning` (amber).
* Footer buttons: `🔄 Atualizar`, `✉ Enviar por e-mail agora` (disabled when no
  recipients are configured, with the reason in the message label), `➕ Entrada`
  (navigates to `ENTRADA_ESTOQUE` pre-filling the selected product's code through
  `UserSession`), `Voltar`.
* Shell: add a toolbar button `🧺 Lista d/ Compras` in `main_layout.fxml` +
  `MainController.onListaCompras()`. Its text shows the count
  (`🧺 Lista d/ Compras (3)`), refreshed on navigation and after the scheduler runs;
  count computed off the FX thread and applied with `Platform.runLater`.
* Also show a discreet hint in the sale status bar when a sold product crosses the
  threshold? **Out of scope** — do not add (keeps the sale screen quiet).

**Tests:** `StockAlertServiceTests` — boundary `quantity == minStock + margin` listed;
`minStock + margin + 0.001` not listed; `quantity == minStock` is `CRITICO`;
`minStock = 0` excluded; inactive excluded; KG with decimals; margin change in
settings changes the result; `suggestedQuantity` never negative.
`StockAlertFormatterTests` — exact expected text for 0, 1 and many items.
`AlertNotifierTests` — a fake `AlertNotifier` records subject/body/recipients; a
notifier that throws does not break `StockAlertScheduler.runOnce()`.

**Acceptance:** with margin 5, a product at 15/min 10 appears as ATENCAO; at 10/min
10 appears as CRITICO; "Enviar agora" with a configured address produces exactly the
previewed text; with a wrong SMTP the page shows a failure message and the app keeps
working.

---

## Phase 4 — Sale page (`sale.fxml` / `SaleController`)

Covers additions 2, 4-search, 5, 6, 8 and fix 10. **Do 4.0 first** — the focus bug
blocks manual validation of everything else.

### 4.0 Fix: fields unreachable during a sale (bug 10)

* Reproduce first: login → open the cash register → sale screen → try to click
  `Desconto`, `Cliente ID`, `Código p/ cancelar`, `Motivo`. Log
  `scene.getFocusOwner()` on every focus change to see who takes the focus back.
* Known suspects in `SaleController` (all to be checked, in this order):
  1. `reclaimFocusUnlessTypingInSaleField()` — the whitelist `isSaleInputField`
     covers only 4 known `TextField`s by identity; anything else (buttons, new
     quantity field, popup list, table cells) loses the focus immediately.
  2. The controller is a **singleton bean** while `Navigator` reloads the FXML on
     every navigation, so `initialize()` runs again and re-registers listeners and
     scene event filters; `uninstallSaleShortcuts` only runs for the old scene, so
     filters from previous loads can survive and keep consuming keys.
  3. With `vendaAtual == null` every `onXxx` returns silently, which looks exactly
     like "nothing is clickable" — but gives no feedback.
* Fix design:
  * New `controller/support/SaleFocusGuard` with a **pure, testable policy**:
    `boolean shouldReclaim(Node focusOwner)` → `false` when the focus owner is
    `null`, a `TextInputControl`, a `Button`/`CheckBox`/`ComboBox`/`ListView` that
    belongs to the sale page, or any node inside an open popup/dialog; `true`
    otherwise. The guard owns the listener install/remove and a
    `suspend()/resume()` pair used while a modal (manager authorization, quick
    product register) is open.
  * Make idempotent re-initialization explicit: a single
    `detachFromPreviousScene()` step at the start of `initialize()` that removes any
    previously installed filter/guard before installing new ones.
  * When there is no active sale, every action writes a clear message
    ("Abra o caixa antes de vender" / "Nenhuma venda em andamento") instead of
    returning silently.
* **Tests:** `SaleFocusGuardTests` (pure JUnit over the policy, no JavaFX toolkit —
  keep the policy taking a small interface or use `Node` stubs if the toolkit is
  needed; if a toolkit is unavoidable, cover the policy with a plain enum-based
  decision function instead). Manual script in `tests/e2e` describing the click
  checks.

### 4.1 Quantity field beside the scan field (RF57, RN24)

* `sale.fxml`: replace the single code `VBox` with one `HBox` so both fields share
  the same line, exactly as asked
  (`campo de bipe --------------- | campo d/ quantidade ----`):
  * `codeField` with `HBox.hgrow="ALWAYS"`, label `Código / Nome do produto`;
  * `quantityField` `prefWidth≈180`, label `Qtd` (becomes `Peso (KG)` for KG
    products), `styleClass="code-field"` so both look like one divided field.
* Flow: Enter on `codeField` **resolves** the product but does **not** add it yet:
  * exact code (case-insensitive) → the product becomes the *pending* product, its
    description and price appear in the existing `lastItemLabel` band, focus moves to
    `quantityField` pre-filled with `1` (UN) or empty (KG), text selected;
  * Enter on `quantityField` → `saleService.addItem(sale, code, quantity)`, table and
    total refresh, fields clear, focus returns to `codeField`;
  * Esc on `quantityField` → cancels the pending product, focus back to `codeField`;
  * the legacy `5*código` syntax keeps working and skips the quantity step
    (`parseQuantityAndCode` moves to `controller/support/ScanInputParser`).
* Pending state lives in `controller/support/PendingScan` (product + resolved price),
  not in loose controller fields.
* Safety (RN24): `quantityField` gets a `TextFormatter` accepting only digits and one
  comma/dot; a quantity above `sale.max_quantity_per_item` (new setting, default
  `999`) is refused with "Quantidade acima do limite — confirme" — this protects
  against a barcode being scanned while the focus is on the quantity field.
* Trade-off to state to the dev when reporting the phase: this is **two Enters per
  item** instead of one. Keep it as the default (explicitly requested), but read the
  behaviour from a setting `sale.ask_quantity_after_scan` (default `true`) so it can
  be turned off per store without a code change.

### 4.2 KG products (RF60)

* When the pending product has `unit = KG`: label becomes `Peso (KG)`, prompt
  `Ex: 0,750`, the formatter accepts up to 3 decimals, and a live line shows
  `0,750 kg × R$ 39,90 = R$ 29,93` before confirming (display only — the authoritative
  total comes from the service after `addItem`).
* No service change needed: `SaleService.validateQuantity` already rejects fractions
  for UN and accepts 3 decimals for KG (RN05). Only the UI adapts.
* Deferred (write it in the phase report, do not implement): weight-embedded scale
  barcodes (EAN-13 with prefix `2`, weight inside the code) — a future
  `ScaleBarcodeParser` in the same support package.

### 4.3 Product suggestion list on the scan field (RF58, RF59, RN25)

* `ProductService.searchForSale(String term, int limit)` — resolution order:
  1. exact code (case-insensitive) → single result;
  2. term is all digits → active products whose **code starts with** the term;
  3. otherwise → active products whose **description starts with** the term, then
     (appended, de-duplicated) those that **contain** it;
  limit 20, ordered.
* `ProductRepository`: `findTop20ByCodeStartingWithAndActiveTrueOrderByCode`,
  `findTop20ByDescriptionStartingWithIgnoreCaseAndActiveTrueOrderByDescription`,
  `findTop20ByDescriptionContainingIgnoreCaseAndActiveTrueOrderByDescription`.
* `controller/support/ProductSuggestionPopup`:
  * a `Popup` anchored under `codeField` holding a `ListView<Product>` with cells
    rendered as `001 — Pão francês — R$ 0,90 — UN — 32 em estoque`;
  * opens as the operator types (debounce ~200 ms, minimum 2 characters) and on Enter
    when there is no exact code match;
  * keyboard: `↓`/`↑` move, `Enter`/`Tab` pick the highlighted row, `Esc` closes and
    keeps the typed text, mouse click picks; while it is open the focus guard is
    suspended;
  * picking a row = resolving the pending product → the quantity step of 4.1 (RN25:
    choosing from the list never adds an item without quantity confirmation).
* Reuse the same popup later in `price_check.fxml` and in the Estoque filter if it is
  trivial; otherwise leave a note.

**Tests:** `ProductSearchForSaleTests` — exact code wins over prefix; digit term uses
code prefix (`00` → `001`, `002`); text term uses name prefix (`re` → `Refrigerante`,
`Requeijão`) before contains; inactive excluded; limit respected; empty/blank term
returns empty. `ScanInputParserTests` — plain code, `5*code`, `0,750*code`, two `*`,
empty quantity, empty code. Service-level KG/UN coverage already exists in
`SaleServiceTests`; add a case for quantity above the limit if the rule lands in the
service.

**Acceptance:** scan `001` + Enter → focus in Qtd showing `1`; type `5` + Enter → 5
units on the sale; type `re` → list with Refrigerante/Requeijão, arrow down + Enter →
product pending with the quantity focused; a KG product shows `Peso (KG)` and accepts
`0,750`; all the Venda+ fields are clickable throughout.

---

## Phase 5 — Estoque page: bulk editing, selection, "Aceitar Sugestão", profit

Covers additions 3, 4 and the profit question.

### Decision to record (ADR): profit stays inside Estoque

The Estoque grid already has Custo, Lucro (%), Sugest. and Vr. Venda. A separate
"Lucros" page would duplicate the editing path for the same fields and double the
authorization surface. So: **no new page** — Estoque gains an extra `Lucro (R$)`
column, a manager-only visibility rule and inline editing; the grid *is* the profit
screen (code, nome, custo, lucro R$, lucro %, valor de venda). Write this decision as
**ADR 0015** in `ESPECIFICACAO-PROJETO.md` with this rationale.

### 5.1 Controller split (mandatory, before features)

* `EstoqueController` keeps actions/wiring only.
* New `controller/support/ProductTablePresenter` — columns, cell factories,
  formatting (`plain`, `formatQuantity`), alignment, severity styling.
* New `controller/support/ProductSelection` — maps the table selection to
  `List<Long> ids` + which column is selected; answers `isSingleColumn()`,
  `selectedColumn()`, `rowCount()`.
* New `controller/support/BulkCellEditor` — commit of one edited cell applied to the
  whole selection.

### 5.2 Cell selection, Shift/Ctrl+click, row handle (RF61, RF62)

* `table.getSelectionModel().setCellSelectionEnabled(true)` and
  `setSelectionMode(SelectionMode.MULTIPLE)` — Shift+click (range) and Ctrl+click
  (toggle) then work natively, as the dev expects from Windows.
* Whole-product selection: add a **leading handle column** (`colHandle`, width 24, no
  header text, CSS class `row-handle`, a `⋮`/`▸` glyph) at the very start of the row,
  before Código. Clicking it selects every cell of that row ("select the whole
  product to edit several fields"). Implement in `ProductSelection.selectWholeRow(int
  rowIndex)`.
* Selecting a single cell of a column is enough to start editing that column
  (`table.setEditable(true)`, editable cells via `TextFieldTableCell`-style editors
  with a `BigDecimal`/`String` converter that accepts comma decimals).

### 5.3 Bulk edit (RN26)

* Editable columns: **Nome, Custo, Lucro (%), Vr. Venda, Qtd. Mín.**
* **Qtd. Atual is never editable here.** Stock quantity only changes through the
  audited path (`StockService.registerEntry` / `adjust`, with reason). The grid's
  `⚖ Ajuste` button pre-fills the selected product instead. State this in the phase
  report.
* Commit semantics: editing one cell while N rows are selected applies the committed
  value to the same column of all N rows, after a confirmation dialog
  `Aplicar R$ 4,50 em 7 produtos?` (no dialog for a single row).
* Editing `Lucro (%)` recomputes `Vr. Venda` from each row's own cost
  (`PricingSuggestionService`), per row — not a single shared price.
* New `service/ProductBulkEditService` (`@Service`, `@Transactional`):
  `applyPrice(List<Long>, BigDecimal)`, `applyCost(...)`, `applyMinStock(...)`,
  `applyMarginPercent(...)`, `applyDescription(...)`; each validates non-negative,
  scales (`2` money / `3` quantity, `HALF_UP`), ignores unknown ids and returns the
  number of changed products. Price changes never touch past sales (RN19) and never
  create stock movements.
* **Authorization:** `applyPrice`, `applyCost` and `applyMarginPercent` require
  `User.Role.GERENTE`; the service throws
  `ManagerAuthorizationRequiredException` and the controller reuses the
  `ManagerAuthorizationController` fallback pattern already in `SaleController`
  (`applyWithManagerFallback`) — extract that pattern into
  `controller/support/ManagerAuthorizedAction` so both screens share it.

### 5.4 "Aceitar Sugestão" (RF63)

* New footer button `✔ Aceitar Sugestão`, **enabled only when the current selection
  is in the `Vr. Venda` column** (otherwise disabled with a tooltip "Selecione células
  da coluna Vr. Venda").
* Action: for every selected row, `price = PricingSuggestionService.suggestFor(product)`
  (skipping rows with cost ≤ 0, reported in the message label:
  `Sugestão aplicada em 5 de 7 (2 sem custo)`), through
  `ProductBulkEditService.applyPrice`.

### 5.5 Manager-only profit (RF64)

* Columns `Custo`, `Lucro (R$)` (new), `Lucro (%)`, `Sugest.` are visible/managed
  **only** when the logged user is `GERENTE`; an operator sees Código, Nome, Vr.
  Venda, Qtd. Mín., Qtd. Atual. Implemented in `ProductTablePresenter` from
  `UserSession`, plus a `Margens` toggle for the manager to hide them on a shared
  screen.
* Extra filters in the top bar: `Só abaixo do mínimo` and `Sem custo` checkboxes
  (useful for pricing work and feeds the Phase 3 list).
* Estoque footer also gets `🧺 Lista d/ Compras` and `Voltar`.

**Tests:** `ProductBulkEditServiceTests` — applies to many ids, rejects negative,
rounds `HALF_UP`, requires GERENTE for price/cost/margin, allows minStock for an
operator, ignores unknown ids, returns the changed count, margin applied per-row cost.
`PricingSuggestionServiceTests` extended for "accept suggestion" (cost 0 → skipped).

**Acceptance:** Shift+click over the Vr. Venda cells of 4 Monsters, type one price,
confirm → the 4 change; clicking the row handle selects the whole product; an
operator cannot see Custo/Lucro; "Aceitar Sugestão" restores the computed price for
the selected rows; Qtd. Atual cannot be typed over.

---

## Phase 6 — Cash register: mandatory reason and error rectification

Covers addition 7.

### 6.1 Mandatory reason for sangria/suprimento (RF67, RN28)

* `caixa.fxml`: next to `movementAmountField`, add a `ComboBox` `movementReasonBox`
  with preset reasons (`Troco inicial`, `Depósito bancário`, `Pagamento a fornecedor`,
  `Retirada do proprietário`, `Outro`) plus a `TextField` `movementReasonField`
  enabled when `Outro` is chosen (or always, pre-filled from the preset).
* `CashRegisterService.supplement(...)` / `withdrawal(...)`: the `reason` parameter
  already exists but is optional — now **reject blank or shorter than 3 characters**
  with a clear message. The UI blocks the button when the reason is empty; the
  service refuses anyway (RN21 posture: UI never replaces service validation).
* `ReceiptService.buildCashCloseReceipt` / the movement receipt must print the reason
  of each movement; `CaixaController` shows it in the `Histórico` column of `movTable`.

### 6.2 Error rectification (RF68, RN29)

* Definition to implement (chosen because it is auditable and legal): a rectification
  **never edits or deletes** the original record; it creates a new movement that
  references the corrected one, carries a mandatory reason and requires manager
  authorization.
* Cash: `CashMovement.Type` gains `RETIFICACAO` (11 chars — fits the existing
  `VARCHAR(11)`), plus fields `correctedMovement` (`@ManyToOne` self-reference) and
  `authorizedBy` (`@ManyToOne User`).
* Stock: `StockMovement.Type` gains `RETIFICACAO`; the column is `VARCHAR(10)` today
  and must be widened.
* Migration **`V31__add_movement_rectification.sql`**:
  * `ALTER TABLE cash_movement ADD COLUMN corrected_movement_id BIGINT REFERENCES cash_movement(id);`
  * `ALTER TABLE cash_movement ADD COLUMN authorized_by_id BIGINT REFERENCES users(id);`
  * `ALTER TABLE stock_movement ALTER COLUMN type TYPE VARCHAR(12);`
  * `ALTER TABLE stock_movement ADD COLUMN corrected_movement_id BIGINT REFERENCES stock_movement(id);`
  * `ALTER TABLE stock_movement ADD COLUMN authorized_by_id BIGINT REFERENCES users(id);`
* Services:
  * `CashRegisterService.rectify(Long movementId, BigDecimal correctAmount, String reason, User authorizedBy)`
    — `@Transactional`, only while the register is OPEN, reason mandatory,
    `authorizedBy` must be `GERENTE`, writes the compensating movement so that
    `currentBalance` reflects the corrected value, and leaves the original untouched.
  * `StockService.rectify(String code, BigDecimal correctQuantity, String reason, User authorizedBy)`
    — same posture, `Type.RETIFICACAO`, keeps `resultingStock` consistent.
* UI: `🧮 Retificar` button in the cash movement bar, enabled with a movement
  selected in `movTable`; a small modal (reuse the `manager_auth.fxml` pattern) asking
  for the correct value and the reason, then manager authorization.
  In `stock_adjust.fxml`, add a `Tipo` choice `Ajuste | Retificação de erro`; the
  reason field already exists and becomes mandatory for both.
* Legal note to keep in mind while implementing: this is a correction trail, never a
  way to hide a sale or a value. Nothing is deleted.

**Tests:** extend `CashRegisterServiceTests` — blank reason refused for
sangria/suprimento/rectification; rectification by an operator refused; balance after
rectification; original movement unchanged. `StockServiceTests` — rectification
records `RETIFICACAO` with reason and authorizer, stock lands on the corrected value,
history keeps both records. `CashCloseReceiptTests` — the receipt shows reasons.

**Acceptance:** sangria without a reason is impossible from the UI and from the
service; a wrong R$ 100 sangria can be rectified to R$ 10 with a manager and a reason,
and both records remain visible in the movement list.

---

## Phase 7 — Stock entry cost (answer to the dev's question) (RF71)

**Answer, verified in the code:** yes, it is the same field. `stock_entry.fxml`'s
`Custo unitário` goes to `StockService.registerEntry(..., unitCost, ...)`, which does
`product.setCostPrice(unitCost)` — the very same `cost_price` set by `Custo` in the
product form and read by Estoque's Lucro/Sugest. columns. It is a **full overwrite
with the last entry cost**, not an average, and it silently changes every margin
shown on the Estoque grid.

**Work:**

* New setting `stock.cost_mode` with values `ULTIMO` (default, current behaviour) and
  `MEDIA_PONDERADA`, exposed in the settings screen.
* `StockService.registerEntry`: when `MEDIA_PONDERADA`,
  `newCost = (currentStock × currentCost + entryQuantity × entryCost) / (currentStock + entryQuantity)`,
  scale 2 `HALF_UP`; when current stock ≤ 0 fall back to the entry cost. Keep the
  arithmetic in a small `service/CostAveraging` helper so the service stays readable.
* `stock_entry.fxml`: show the current cost of the scanned product and what the cost
  will become after saving ("Custo atual R$ 4,50 → novo custo R$ 4,72").

**Tests:** `StockServiceTests` — `ULTIMO` overwrites; `MEDIA_PONDERADA` with
stock 10 @ 4,00 + entry 10 @ 5,00 → 4,50; entry over zero/negative stock; null
`unitCost` keeps the cost untouched.

**Acceptance:** switching the mode in Settings changes the resulting cost of a new
entry, with no migration of past data.

---

## Phase 8 — Documentation, requirements and closing sweep

* `MAPA-REQUISITOS.md`: add RF54–RF72, RN22–RN30, RNF17–RNF18 with status and phase.
* `ESPECIFICACAO-PROJETO.md`: ADR 0015 (profit inside Estoque), ADR 0016 (no comments
  in code + file size limits), ADR 0017 (alert notification port + SMTP secret in
  `.env`), ADR 0018 (rectification as a compensating movement, nothing deleted).
* `CLAUDE.md`: section 2 says "Spring Boot 3" but `pom.xml` is on **4.1.1** — fix it;
  add the new rules (no comments, file size, Back navigation, alert settings) and the
  new pages to section 6.
* `HELP.md`: how to configure the alert e-mail (including `SMTP_PASSWORD` in `.env`),
  the new sale flow (code → quantity), the suggestion list, bulk editing in Estoque.
* `REVISION.md` / `FIXES.md`: move whatever is left over from each phase.
* Final sweep: `./mvnw test` green with JDK 26 + `db_scopel`; `mvn javafx:run` manual
  pass over the acceptance list of every phase; `git log` free of AI references;
  no file over 400 lines (`find src -name "*.java" -exec wc -l {} +` sorted) and no
  comments in touched files (`grep -rn "//\|/\*" src/main/java` reviewed).

---

## 9. Do not do

* No "venda sem nota", second register, or any way to hide revenue — refuse it
  (Lei 8.137/90).
* Never delete or edit an audit record (movements, sales); corrections are new
  records (Phase 6).
* Never put the SMTP password (or any secret) in `app_setting`, in the code or in Git.
* Never edit an applied Flyway migration; always a new one.
* Never bulk-edit stock quantity outside `StockService`.
* No business logic, money arithmetic or authorization decisions in controllers.
* No AI co-author, footer or mention in commits, PRs, code or docs.
* Do not implement what is explicitly deferred here (weight-embedded scale barcodes,
  low-stock hint inside the sale screen, a separate "Lucros" page).

## 10. Phase order summary

| Phase | Theme | Depends on |
|---|---|---|
| 0 | Git/authorship cleanup | — |
| 1 | Navigation history + global Back | — |
| 2 | Product page: suggestion, min stock, text codes | 1 |
| 2.5 | Cost terminology standardization (UI, code, docs) | 2 |
| 3 | Settings + Lista d/ Compras + e-mail alerts | 2 |
| 4 | Sale page: quantity, KG, search list, focus fix | 2 |
| 5 | Estoque: bulk edit, selection, suggestion, profit | 2 |
| 6 | Cash register: reasons + rectification | 1 |
| 7 | Stock entry cost mode | 2 |
| 8 | Docs, requirements, closing sweep | all |
