# REVISION — melhorias e correções longas por rodada

Registro das entregas mais longas/estruturais de cada rodada. Concertos curtos e
pontuais ficam em `FIXES.md`.

---

## 2026-10-22 — Time de Validação PDV: #G3 (consulta de preço) + barra do último item

Rodada do time de validação (`/validacao-pdv G3` + adição pedida pelo dev), esteira
completa com portões verdes. **Onde paramos / contexto para a próxima rodada:**

### Entregue (pronto em código + QA verde; PENDENTE validação manual na tela)
- **#G3 — Consulta de preço rápida (tela dedicada, tecla F1):** nova `Page.CONSULTA_PRECO`
  + `price_check.fxml` + `PriceCheckController` (**SÓ LEITURA** — reusa
  `ProductService.findCode`/`searchByName`/`listAll`, nenhum método novo; QA e validador
  confirmaram que nenhum caminho de escrita é alcançável). PREÇO grande, descrição,
  unidade, aviso "SÓ CONSULTA — NÃO É VENDA", erro "Produto não encontrado" em PT, Esc/
  Voltar via `Navigator` (histórico de 1 nível, fallback `INICIO`). Cobre **RF07**
  (consulta rápida) + **RF05** (por nome) e reusa **RF04** (por código).
  - **RN21:** F1 em `MainController.onConsultaPreco` + guard `hasSaleInProgress()` —
    bloqueia (dica ~4s na barra de status) quando a venda tem itens OU está EM_PAGAMENTO;
    libera sem venda / venda aberta vazia. Para o guard enxergar o estado real, o
    `SaleController` passou a sincronizar `userSession.setVendaAtual(...)` em TODAS as
    transições (wrapper único `setVendaAtual`).
- **Barra "ÚLTIMO ITEM" (tela de venda):** visor `lastItemDisplay` (entre código e tabela,
  `focusTraversable=false`) mostra **nome + preço unitário** (congelado, RN19) do item
  recém-bipado; placeholder "—" vazio; reflete item restante ao cancelar item e limpa ao
  cancelar venda. CSS `last-item-*` **secundário** ao `total-value` (TOTAL segue dominante,
  RNF04). É melhoria de UX (não RF novo).
- **QA:** `mvn test` **139 verdes, 0 falhas** (JDK 26 + Docker). +5 testes
  `ProductSearchServiceTests` (bordas de leitura do #G3). A refatoração `setVendaAtual`
  NÃO causou regressão.
- **Roteiro E2E do John:** `tests/e2e/roteiros/G3_consulta_preco_e_barra.md` (Parte A
  consulta+RN21, Parte B barra).

### Correção de teste (não é regressão)
`SaleCardPixHistoryTests` (teste **untracked** da rodada de pagamento 2026-10-20, nunca
executado) tinha typo de assert: exigia troco com escala 0, sendo que o correto é 2
(`R$ 0,00`, NUMERIC(10,2)). QA corrigiu o **assert do teste**; o código de produção
estava certo.

### Decisões do dev (registradas no AUDITORIA)
#G3: tecla **F1**, **tela dedicada** (não popup). Barra: **nome + preço** unitário.

### Pendências para a próxima rodada (retomar daqui)
- **Validação manual na tela** (`mvn javafx:run`, JDK 26) fecha os `✅*` de **G1/G2/G3/G5
  de uma vez**: F1 abre/bloqueia conforme estado (RN21), Esc volta, erro PT, foco no
  código; barra atualiza sem roubar foco e secundária ao TOTAL.
- **Automação pyautogui do G3** NÃO escrita ainda (e as do G1/G2 escritas, não rodadas):
  todas dependem de instalar **Tesseract OCR** + `pip install pyautogui pytesseract
  pillow` no ambiente do dev. Vale instalar 1× e rodar G1+G2+G3 juntos.
- **UX opcional (não bloqueia):** busca por nome com vários resultados mostra só o 1º +
  aviso para refinar (sem lista de escolha); o prompt do campo de venda ainda não sugere
  `qtd*código` (vindo do #G2).
- **Próximo alvo recomendado:** **#G4** (fechamento de caixa por forma de pagamento) ou
  **#G7** (CPF na nota).

### Documentos tocados
`AUDITORIA-PDV.md` (RF07/RF05 ✅*, RF04 reusado, barra como UX/RNF04, decisões do dev,
histórico), `MAPA-REQUISITOS.md` (RF05/RF07 ✅*, tabela de atalhos com F1, data), este
`REVISION.md`. Código: `PriceCheckController`+`price_check.fxml` (novos), `Page`,
`Navigator`, `MainController`+`main_layout.fxml`, `SaleController`+`sale.fxml`,
`styles.css`. Teste: `ProductSearchServiceTests` (novo), fix em `SaleCardPixHistoryTests`.
E2E: roteiro do John.

---

## 2026-10-22 — Time de Validação PDV: #G2 (quantidade / multiplicador e peso)

Rodada do time de validação (`/validacao-pdv G2`), esteira completa com os portões
verdes. **Onde paramos / contexto para a próxima rodada:**

### Entregue (pronto em código + testado; PENDENTE validação manual na tela)
- **Mecanismo `qtd*código` no próprio campo do leitor** (sem campo novo, sem F4):
  `6*7891234` → 6 un; `1,250*7899` → 1,250 kg; só o código (sem `*`) → 1 un (intacto).
  Vírgula E ponto já funcionam (`new BigDecimal(texto.replace(',', '.'))`).
- **Backend:** `exception/InvalidQuantityException` (mensagens PT); `SaleService.validateQuantity`
  (RN05 — UN inteiro/>0, KG >0 e ≤3 casas com `setScale(3, HALF_UP)`), chamado em
  `addItem` **antes** da soma (RN07 não deixa fração virar parcela); RN19 preservado.
  `SaleController.parseQuantityAndCode`/`parseQuantity` fazem o parse (separa só no 1º
  `*`, código cru com zero à esquerda, malformados com erro PT no statusLabel sem
  derrubar a venda).
- **QA:** `SaleServiceTests` (@SpringBootTest) **27 verdes** com JDK 26 + Docker, 10
  métodos novos de borda (RN05/RN07/RN19), sem regressão.
- **Roteiro E2E do John:** `tests/e2e/roteiros/G2_quantidade_peso.md` (7 cenários).
- **Automação (escrita, NÃO rodada):** `tests/e2e/g2_quantidade_peso.py` + `seed_g2.sql`
  (só os 2 produtos; idempotente) + README. Reset entre fluxos por F2 (venda nova).

### Decisões do dev (registradas no AUDITORIA)
Mecanismo `qtd*código` sem F4; **RF17 estreitado para só multiplicador** (não edita
linha já lançada — correção = F6 + rebipar); **KG por peso digitado** (a loja NÃO tem
balança etiquetadora → **RN06 N/A para esta loja**, lacuna #G6 só no cenário genérico/
aluguel); vírgula E ponto aceitos.

### Pendências para a próxima rodada (retomar daqui)
- **Validação manual** (`mvn javafx:run`, JDK 26): cenários 3 (bipe simples = 1 un),
  6 (todos os malformados com mensagem PT, venda não cai) e 7 (foco perpétuo no
  leitor / RNF03). Limpa o ✅* do #G2 e do RN05/RF17.
- **Rodar a automação pyautogui** (ação de ambiente do dev): instalar **Tesseract OCR**
  (engine de sistema, UB-Mannheim + idioma `por` no PATH) + `pip install pyautogui
  pytesseract pillow`; seeds (`seed_g2.sql` + caixa1 aberto); app aberto; `python
  tests/e2e/g2_quantidade_peso.py`. Mesma pendência do script do G1.
- **UX sugerida pelo John (não bloqueia):** prompt do campo poderia sugerir
  `qtd*código` (hoje diz só "Bipe ou digite o código"). F4 NÃO deve ser ligado (G2
  substitui) — já reconciliado no MAPA.
- **Próximo alvo recomendado:** #G3 (consulta de preço rápida, venda inativa) ou
  #G7 (CPF na nota).

### Documentos tocados
`AUDITORIA-PDV.md` (veredito #G2, RF17 narrowing, RN06 N/A, F4, decisões do dev,
histórico), `MAPA-REQUISITOS.md` (RF17 🟡, RN05 ✅*, RN06 N/A, tabela de atalhos),
este `REVISION.md`. Código: `SaleService`, `SaleController`, `InvalidQuantityException`,
`SaleServiceTests`. E2E: roteiro + script + seed + README.

---

## 2026-10-21 — Time de Validação PDV: inventário + G1 (senha de supervisor) + G5 (atalhos)

Primeira rodada do **novo time de validação** (comando `/validacao-pdv`, playbook
`.claude/COORDENADOR-VALIDACAO.md`, agentes `gerenciador-regras`, `senior-backend`,
`qa-validacao`, `operador-john`, `validador-pdv`). Esteira com portões rodada ponta
a ponta. **Onde paramos / contexto para a próxima rodada:**

### Entregue (pronto em código, PENDENTE validação manual na tela)
- **Inventário completo** de 56 funcionalidades vs PDVs de mercado em
  `AUDITORIA-PDV.md` (fonte do que fazer depois; lacunas priorizadas #G1–#G8, #H1–#H4).
- **G1 — Autorização por senha de supervisor** (RF41, RF18, RF21, RF19/RF20+RN08,
  RNF09): `UserService.authenticateManager(managerLogin, rawPassword)` (reusa
  `login`/BCrypt, exige GERENTE, não toca `UserSession`); diálogo só-teclado
  `manager_auth.fxml` + `ManagerAuthorizationController`; `SaleController`
  `applyWithManagerFallback` liga cancelar item/venda e desconto acima do teto.
  **10 testes JUnit verdes** (`ManagerAuthorizationTests`), 27 no conjunto, sem regressão.
- **G5 — Atalhos da venda** (RNF03, RN21): event filter sensível ao contexto no
  `SaleController` (`bindSaleShortcutsToScene`/`installSaleShortcuts`/`handleSaleKey`)
  liga F5 desconto, F6 cancelar item, F7 cancelar venda, F9 cliente, F10 finalizar,
  só enquanto a venda está ativa; não mexe no menu/`MainController`; preserva o foco
  perpétuo no leitor. Compila no JDK 26.
- **Automação E2E (escrita, NÃO rodada):** `tests/e2e/roteiros/G1_senha_supervisor.md`
  + `tests/e2e/g1_senha_supervisor.py` + `tests/e2e/README.md` (pyautogui, 3 fluxos).

### Pendências para a próxima rodada (retomar daqui)
- **Validação manual (limpa o veredito ✅* dos dois):** `mvn javafx:run` com **JDK 26** —
  diálogo do G1 (foco no login, Enter/Esc, erro inline, foco volta ao leitor) e os
  atalhos do G5 (inclusive que bipar código+Enter continua funcionando).
- **Rodar o script pyautogui** (ação de ambiente do dev): calibrar `REGIAO_*` (OCR) e
  `TABS_*` na tela real; caixa do `caixa1` aberto; seeds operador/gerente/produto.
- **Adiados para F5 pelo dev:** **G1-A** teto de desconto por operador (hoje AppSetting
  global 10%) e **G1-B** trilha de auditoria dedicada (hoje `Sale.authorizedBy` é campo
  único sobrescrito; base do RF48).
- **Próxima funcionalidade recomendada:** **#G2** — quantidade/multiplicador e peso no
  bipe (RF17/RN05). `SaleService.addItem` já aceita `BigDecimal quantity`; falta só UI +
  validação. Depois #G7 (CPF na nota) e #G3 (consulta de preço rápida).

### Documentos tocados
`AUDITORIA-PDV.md` (inventário + G1/G5 + histórico + decisões F5),
`MAPA-REQUISITOS.md` (RF18/RF19/RF20/RF21/RF41 → 🟡, RN08/RNF03/RNF09 anotados),
este `REVISION.md`. Agentes/comando novos em `.claude/`.

---

## 2026-10-20 — REVISÕES2: fluxo de pagamento guiado, prévia, fechamento impresso e histórico

Rodada com 5 entregas (ver ESPECIFICACAO seção 10 e ADR 0013). Resumo do que foi
feito: fluxo de pagamento guiado (Dinheiro/Cartão/PIX) com porta
`CardPaymentGateway` + `MockCardPaymentGateway`; prévia do cupom na tela de venda
(RNF15); tela "Venda finalizada"; impressão do fechamento de caixa (RNF06);
histórico de vendas; correção do bug dos itens da venda anterior persistirem na
nova venda.

### Pendências para a próxima rodada
- **Rodar `mvn test` completo** com Docker (`db_scopel`) + JDK 26: os 8 testes novos
  `SaleCardPixHistoryTests` (@SpringBootTest) compilam mas ainda não foram
  executados. Unitários puros (`MockCardPaymentGatewayTests`, cupom de fechamento)
  já verdes. **Nenhum RF foi marcado ✅ dependendo desses testes** sem o QA
  confirmar verde — RF27/RF28/RF31 ficaram 🟡.
- **Enriquecer o cupom de fechamento** com quebra por **forma de pagamento** e
  **sangrias**: hoje o cupom traz só saldo inicial/esperado/contado/diferença. Esses
  valores são calculados na tela e **não** existem como getters em `CashRegister`;
  exige o Backend expor um **resumo via service** (base para RF45 também).
- **Validação visual manual** das telas novas (`mvn javafx:run`): foco e teclas
  1/2/3/ESC no fluxo de pagamento; no PIX o **ENTER não dispara** o botão Confirmar
  — avaliar `defaultButton`.
- **Integração real Mercado Pago** (Orders API `POST /v1/orders` + polling, Access
  Token + device id em modo PDV): plugar um adaptador real de `CardPaymentGateway`
  no lugar do mock `@Primary`, sem tocar nos services (F4, ADR 0013).

### Segurança (ALTO, pré-existente) — senha do banco exposta
- `spring.datasource.password` está em **texto puro** no `application.properties` e
  o `.env` foi **rastreado no Git** (commit **98d4de8**), com a senha do Postgres no
  histórico.
- Pendências do dev: `git rm --cached .env`; trocar `spring.datasource.password`
  para `${DB_PASSWORD}`; **rotacionar** a senha do Postgres (já vazou no histórico).
- O `.gitignore` **já foi atualizado** para ignorar `.env` (não resolve o que já
  está no histórico — por isso a rotação é obrigatória).

### Documentos tocados
`MAPA-REQUISITOS.md` (RF25/27/28/31/36/42/43, RNF06/RNF15, Fases),
`CLAUDE.md` (seções 6 e 7, reforço da regra 8), `ESPECIFICACAO-PROJETO.md`
(seções 5.7, 10, 12, 13 e ADR 0013 na 14), este `REVISION.md`.

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
