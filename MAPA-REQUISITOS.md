# MAPA DE REQUISITOS — Sistema de Caixa (PDV) Mercado Scopel

> Fonte única e reconciliada dos requisitos. Existem dois documentos de origem com
> numerações diferentes; este mapa resolve a divergência e é a referência que o
> **Coordenador** e o **Arquiteto** usam para escolher e validar tarefas.
>
> - **Numeração canônica** = a do PDF `Planejamento_Sistema_de_caixa` (RF01–RF53,
>   RN01–RN21, RNF01–RNF16). É a meta final.
> - `ESPECIFICACAO-PROJETO.md` descreve o **estado atual** e usa uma numeração
>   própria (RF01–RF20); quando relevante, a coluna "Notas" aponta o equivalente.
>
> Última atualização: 2026-10-22.

## Legenda de status
- ✅ **Feito** — implementado e (quando dito) testado.
- ✅* **Feito em código, falta validação manual na tela** — lógica implementada e
  testada (JUnit), UI ainda não vista rodando (`mvn javafx:run`).
- 🟡 **Parcial** — base existe, falta completar.
- ⬜ **Futuro** — ainda não começado.

## Fases
- **F1 — Fase atual:** teste do `SaleService`, bug do caixa aberto, **design das
  telas** (inicial → venda → pagamento → login → gestão), **tela de pagamento +
  troco**.
- **F2 — Impressão:** cupom + abertura de gaveta (escpos-coffee).
- **F3 — Fiscal:** NFC-e / CF-e SAT.
- **F4 — Pagamento integrado:** Mercado Pago Point via API + polling.
  Parte **antecipada em 2026-10-20** (REVISÕES2, a pedido do dev): fluxo de
  pagamento guiado (Dinheiro/Cartão/PIX) + porta `CardPaymentGateway` com
  **mock** (`MockCardPaymentGateway`); a integração real (Orders API + polling)
  continua na F4 (ADR 0013).
- **F5 — Gestão:** cadastros e relatórios completos.

---

## Requisitos Funcionais (RF)

### Módulo Produtos
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF01 | Cadastrar produto | ✅ | — | `create_product.fxml` + `ProductService`. **Subnota (3ª leva, 2026-10-22)** — caminho "produto não encontrado no caixa → cadastro rápido": ao bipar código inexistente na venda, `SaleController` abre o modal `QuickProductController` (`cadastro_rapido.fxml`) que persiste via `ProductService.save` (código cru String, nome, preço, unidade UN/KG default UN, estoque 0, active=true); o operador rebipa para vender (RN19). É variação de UX do RF01, NÃO um RF novo. Não confundir com RF06 (gerar EAN-13 para produto SEM código). Em F1 sem gerente; fila de revisão = #G-revprod (F5). Testado (`ReopenAndQuickProductTests`) |
| RF02 | Editar produto | ⬜ | F5 | |
| RF03 | Inativar produto (nunca excluir) | 🟡 | F5 | entidade tem `active` |
| RF04 | Buscar produto por código de barras | ✅ | — | usado ao bipar; **reusado pela Consulta de preço (#G3, F1, 2026-10-22)** — `PriceCheckController` chama `ProductService.findCode` (código cru, zeros à esquerda) |
| RF05 | Buscar produto por descrição (parcial) | ✅* | F1 | **G3 (2026-10-22):** `ProductService.searchByName` (read-only: `findByDescriptionContainingIgnoreCase`) exposto na Consulta de preço (`price_check.fxml`, campo "buscar por nome") além do filtro do Estoque; mostra 1º resultado e avisa p/ refinar se houver vários. +5 testes `ProductSearchServiceTests` verdes. Pendente validação manual na tela |
| RF06 | Gerar código interno EAN-13 (produto sem código) | ⬜ | F5 | |
| RF07 | Consulta de preço (tela rápida) | ✅* | F1 | **G3 (2026-10-22):** tela DEDICADA `price_check.fxml` + `PriceCheckController` (SÓ LEITURA — reusa `findCode`/`searchByName`/`listAll`; NÃO abre venda, não grava, não baixa estoque; QA confirmou por código). Tecla **F1** em `MainController.onConsultaPreco` com guard RN21 `hasSaleInProgress()` (bloqueia com itens OU EM_PAGAMENTO, dica ~4s; libera sem venda ou venda aberta vazia). Aviso "SÓ CONSULTA — NÃO É VENDA", PREÇO grande (64px), descrição, unidade; erro "Produto não encontrado" em PT; Esc/Voltar via `Navigator` (histórico 1 nível → Início). QA: 139 testes verdes no conjunto. Pendente validação manual na tela (`mvn javafx:run` JDK 26) + automação pyautogui (falta Tesseract OCR) |
| RF08 | Alterar preço com histórico | ⬜ | F5 | não quebra RN19 (preço congelado) |

### Módulo Estoque
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF09 | Registrar entrada de mercadoria | ⬜ | F5 | |
| RF10 | Ajustar estoque manual com motivo | ⬜ | F5 | |
| RF11 | Baixar estoque automático na venda | ✅ | — | RN10, `@Transactional` |
| RF12 | Listar produtos abaixo do mínimo (farol) | ⬜ | F5 | farol verde/amarelo/vermelho |
| RF13 | Histórico de movimentação de um produto | ⬜ | F5 | |

### Módulo Venda
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF14 | Iniciar nova venda | ✅ | — | `SaleService.openSale`; testado (`SaleServiceTests`) |
| RF15 | Adicionar item por código de barras | ✅ | — | RN07 (agrupa); testado (`SaleServiceTests`) |
| RF16 | Adicionar item por código digitado | 🟡 | F1 | mesmo campo do leitor; aceita `qtd*código` (G2) |
| RF17 | Alterar quantidade de um item | 🟡 | F1 | **G2 (2026-10-22): multiplicador `qtd*código` no leitor** — `SaleController.parseQuantityAndCode` + `SaleService.validateQuantity` (RN05); 27 testes verdes; ✅\* em código. **Estreitado pelo dev para SÓ multiplicador** — não edita quantidade de linha já lançada (divergência menor vs. mercado, aceitável F1; edição in-loco → F5). **F4 dedicado substituído por `qtd*código`** (não ligar F4 = sem UI morta). Pendente validação manual na tela |
| RF18 | Remover item da venda (com autorização) | 🟡 | F1 | G1: `cancelItem` + diálogo de senha de gerente (F6); validação manual na tela pendente |
| RF19 | Aplicar desconto no item | 🟡 | F1 | G1: `applyItemDiscount` + diálogo de gerente acima do teto; validação manual pendente |
| RF20 | Aplicar desconto no total | 🟡 | F1 | G1: `applySaleDiscount` + diálogo de gerente (RN08 >10%); validação manual pendente |
| RF21 | Cancelar venda em andamento (com autorização) | 🟡 | F1 | G1: `cancelSale` + diálogo de gerente (F7); RN17 mantém registro; validação manual pendente |
| RF22 | Total/subtotal/qtd em tempo real | 🟡 | F1 | base feita na TableView; **barra "ÚLTIMO ITEM" (só nome) na tela de venda (2026-10-22)** reforça a legibilidade (RNF04) — preço segue na tabela, no TOTAL e na Consulta de preço (F1); UX, não RF novo |
| RF23 | Interpretar etiqueta de balança (código "2…") | ⬜ / N/A | F5 | RN06. **N/A para a loja do dev** (sem balança etiquetadora; KG por peso digitado, G2). Lacuna #G6 só para o cenário genérico/aluguel |
| RF24 | Informar CPF do consumidor antes de finalizar | ⬜ | F1/F3 | RN20 (entra no XML) |

### Módulo Pagamento
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF25 | Selecionar forma de pagamento | ✅ | F1 | fluxo guiado 1-Dinheiro/2-Cartão/3-PIX na UI (`pagamento.fxml`); service aceita DINHEIRO/CARTAO/PIX |
| RF26 | Receber em dinheiro e calcular troco | ✅ | F1 | `SaleService.pay`/`calculateChange` testados (RN09); UI `pagamento_dinheiro.fxml` a validar |
| RF27 | Receber em cartão via maquininha | 🟡 | F4 | fluxo + gateway mock prontos (`SaleService.payCard`, `CardPaymentGateway`/`MockCardPaymentGateway`, enum `CardType`); falta integração real Mercado Pago |
| RF28 | Receber em PIX | 🟡 | F4 | copia-e-cola + confirmação manual (`SaleService.payPix`, `pagamento_pix.fxml`); sem conciliação automática |
| RF29 | Dividir pagamento em várias formas | ⬜ | F4 | RN18 |
| RF30 | Tratar pagamento negado sem perder a venda | ⬜ | F4 | mock pode negar; tratamento na UI a completar |
| RF31 | Cancelar cobrança pendente na maquininha | 🟡 | F4 | `CardPaymentGateway.cancel` (mock); falta fio na UI + integração real |

### Módulo Caixa
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF32 | Abrir caixa com fundo de troco | ✅ | — | RN13 |
| RF33 | Registrar sangria | ✅ | — | RN14/RN15 |
| RF34 | Registrar suprimento | ✅ | — | RN14 |
| RF35 | Fechar caixa com conferência e diferença | ✅ | — | RN16 |
| RF36 | Emitir relatório de fechamento | 🟡 | F5 | cupom de fechamento impresso ao fechar (`ReceiptService.buildCashCloseReceipt`/`printCashCloseReceipt`; `CaixaController.onClose`/`onPrintFechamento`) com saldo inicial/esperado/contado/diferença; falta quebra por forma de pagamento e sangrias (exige resumo no service) |
| RF37 | Abrir a gaveta nas situações autorizadas | ⬜ | F2 | RN11 |

### Módulo Usuários
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF38 | Autenticar usuário | ✅ | — | BCrypt, `UserService.login` |
| RF39 | Cadastrar e editar usuários | ⬜ | F5 | nunca por SQL cru (fura o hash) |
| RF40 | Definir perfil (operador/gerente) | 🟡 | F1 | enum `Role`; falta UI de gestão |
| RF41 | Autorização de gerente em operações sensíveis | 🟡 | F1 | G1: `UserService.authenticateManager` + diálogo `ManagerAuthorizationController` (login+senha do gerente sem deslogar o operador); 10 testes JUnit verdes; validação manual na tela pendente |

### Módulo Impressão
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF42 | Imprimir comprovante da venda | 🟡 | F2 | prévia só-leitura na tela de venda (`ReceiptService.buildSaleReceipt`) com aviso RNF15; impressão térmica em F2 |
| RF43 | Reimprimir o último comprovante | 🟡 | F2 | reimpressão do fechamento (`CaixaController.onPrintFechamento`); reimpressão do cupom de venda em F2 (RNF06) |
| RF44 | Imprimir etiqueta de código de barras | ⬜ | F5 | |

### Módulo Relatórios
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF45 | Vendas do dia por forma de pagamento | ⬜ | F5 | |
| RF46 | Vendas por operador | ⬜ | F5 | |
| RF47 | Produtos mais vendidos no período | ⬜ | F5 | |
| RF48 | Relatório de cancelamentos e descontos | ⬜ | F5 | |

### Módulo Fiscal (fase 3)
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF49 | Emitir NFC-e ao finalizar a venda | ⬜ | F3 | |
| RF50 | Contingência offline e transmitir depois | ⬜ | F3 | RNF07 |
| RF51 | Cancelar NFC-e dentro do prazo legal | ⬜ | F3 | |
| RF52 | Armazenar e consultar XMLs emitidos | ⬜ | F3 | RNF16 (guardar ≥5 anos) |
| RF53 | Imprimir DANFE NFC-e com QR Code | ⬜ | F3 | |

### Interface / navegação (vindo do ESPECIFICACAO)
| ID | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| UI01 | Tela inicial (menu) por papel | 🟡 | F1 | `inicio.fxml` em andamento (TASK.md) |

---

## Regras de Negócio (RN)
| RN | Descrição | Status |
|---|---|---|
| RN01 | Dinheiro = BigDecimal 2 casas, HALF_UP | ✅ (inviolável; reforçado em `SaleService` — `addItem`/`calculateTotal` com `setScale(2, HALF_UP)`, 2026-10-17) |
| RN02 | Código de barras como texto (zeros à esquerda) | ✅ (inviolável; `parseQuantityAndCode` passa o código cru, preserva zeros à esquerda — G2; Consulta de preço G3 idem) |
| RN03 | Total item = preço × qtd, arredondado no fim | ✅ |
| RN04 | Total venda = itens − descontos, nunca negativo | 🟡 (falta desconto) |
| RN05 | KG aceita fracionário (3 casas); UN só inteiro | ✅* (G2 2026-10-22: `SaleService.validateQuantity` — UN inteiro/>0 recusa fração; KG >0 e ≤3 casas, normaliza `setScale(3, HALF_UP)`; chamado por `addItem` ANTES da soma RN07; 27 testes verdes. Pendente validação manual na tela) |
| RN06 | Código "2…" = etiqueta de balança | ⬜ / **N/A para a loja do dev** (sem balança etiquetadora — KG entra por peso digitado, G2/RN05). Permanece lacuna #G6 só para o cenário genérico/aluguel |
| RN07 | Bipar item já presente incrementa quantidade | ✅ (G2 preservou: `validateQuantity` roda ANTES da soma, fração barrada não entra como parcela) |
| RN08 | Desconto > 10% exige gerente | 🟡 (G1: service recusa sem gerente e abre diálogo de senha; teto global 10% via AppSetting — teto por operador é F5/G1-A; validação manual pendente) |
| RN09 | Venda só gravada após confirmação do pagamento | ✅ (`SaleService.pay` grava `Payment` + baixa estoque + FINALIZADA, em `@Transactional`; testado 2026-10-17. **Interpretação (ADR 0014, 2026-10-22):** "gravada" = FINALIZADA; ABERTA/EM_PAGAMENTO são rascunho operacional NÃO-FISCAL persistido só para durabilidade do carrinho — não entram em números fiscais; `resumeOrOpenSale` reaproveita esse rascunho sem ferir a RN09) |
| RN10 | Gravar venda + baixar estoque na mesma transação | ✅ |
| RN11 | Gaveta só abre: venda em dinheiro, sangria, suprimento | ⬜ (F2) |
| RN12 | Estoque negativo permitido, gera alerta | 🟡 |
| RN13 | Um operador = um caixa aberto por vez | ✅ |
| RN14 | Não inicia venda com caixa fechado | ✅ |
| RN15 | Sangria não deixa saldo em dinheiro negativo | ✅ |
| RN16 | Fechamento registra diferença, não impede fechar | ✅ |
| RN17 | Venda cancelada mantém registro (status, usuário, motivo) | ⬜ |
| RN18 | Pagamento múltiplo; soma ≥ total; só dinheiro gera troco | ⬜ (F4) |
| RN19 | Preço congelado no item | ✅ (inviolável; preservado no G2 — `validateQuantity` só normaliza a quantidade, `unitPrice` segue copiado do produto no 1º lançamento; a barra "último item" G3 mostra `unitPrice` congelado, nada recalculado na UI) |
| RN20 | CPF do consumidor antes de finalizar (entra no XML) | ⬜ |
| RN21 | Máquina de estados: ação fora do estado é recusada pelo service | 🟡 (reforçado 2026-10-17 com estado `EM_PAGAMENTO`: `goToPayment` só de ABERTA+itens, `pay` só de EM_PAGAMENTO, `addItem` só em ABERTA — testado. **G3 (2026-10-22):** Consulta de preço (F1) respeita RN21 na UI — `MainController.hasSaleInProgress()` bloqueia a consulta com venda em itens/EM_PAGAMENTO; `SaleController` sincroniza `userSession.setVendaAtual(...)` em todas as transições para o guard ler o estado real. **2ª leva "venda pendente" (2026-10-22):** `SaleService.resumeOrOpenSale`/`canResume`/`sameEntity` reaproveita a venda ABERTA/EM_PAGAMENTO do mesmo operador e caixa (demais casos → `openSale` nova) e `SaleController.initialize` restaura tabela/TOTAL/barra do último item e roteia EM_PAGAMENTO ao pagamento, preservando o guard RN14 — **implementada e aprovada (PRONTA F1), validação manual na tela pendente** (`mvn javafx:run` JDK 26); QA 162 testes verdes (`ResumeOrOpenSaleTests`, 11 bordas). Demais estados a completar) **3ª leva (2026-10-22):** nova transição EM_PAGAMENTO → ABERTA via `SaleService.reopenForEditing` (reabrir venda para edição); guards recusam ABERTA/FINALIZADA/CANCELADA; sem Payment órfão. `PaymentController.onVoltar` chama a reabertura. Testada em `ReopenAndQuickProductTests` (172 verdes). Validação manual na tela pendente. |

---

## Requisitos Não Funcionais (RNF)
| RNF | Descrição | Como verificar | Status |
|---|---|---|---|
| RNF01 | Busca por código < 300 ms com 20.000 produtos | teste de carga / índice no banco | ⬜ |
| RNF02 | Finalizar venda (gravar + baixar estoque) < 1 s | teste de tempo | ⬜ |
| RNF03 | Operação de venda sem mouse | revisão de UX + foco/atalhos | 🟡 (G5 ligou F5/F6/F7/F9/F10 na venda via event filter sensível ao contexto, preservando o foco no leitor; G2 pôs quantidade/peso por `qtd*código` no próprio leitor — dígitos/Enter não consumidos; G3 pôs Consulta de preço em F1 com Esc/Enter só-teclado e foco inicial no código; validação manual pendente) |
| RNF04 | Total legível a 1 m | fonte grande, alto contraste (design) | 🟡 (`total-value` 56px na venda; **barra "ÚLTIMO ITEM" 2026-10-22** com preço 30px/nome 26px — proeminente, mas secundária ao TOTAL; Consulta de preço com PREÇO 64px) |
| RNF05 | Falha de energia não deixa estoque/caixa inconsistente | `@Transactional` + testes | 🟡 |
| RNF06 | Falha de impressão não impede a venda já gravada; reimprime | ordem: gravar → imprimir | 🟡 aplicado no fechamento: `CaixaController.onClose` imprime após fechar e engole falha de impressão (fechamento não é derrubado); reimpressão via `onPrintFechamento`. Cupom de venda em F2 |
| RNF07 | Opera sem internet (exceto fiscal e pagamento integrado) | arquitetura offline-first | ✅ |
| RNF08 | Senhas em hash BCrypt, nunca em texto puro | revisão de segurança | ✅ (inviolável) |
| RNF09 | Operações sensíveis exigem gerente | revisão de segurança | 🟡 (G1: autorização por senha de gerente no ato; trilha de auditoria dedicada é F5/G1-B; validação manual pendente) |
| RNF10 | Toda abertura de gaveta registra operador/data/hora/motivo | auditoria | ⬜ (F2) |
| RNF11 | Nenhuma venda é apagada; cancelar = status | revisão de modelo | 🟡 |
| RNF12 | Windows 10+, 4 GB RAM, 1366×768 | teste em ambiente-alvo | 🟡 |
| RNF13 | Regra de negócio isolada no service, sem JavaFX | revisão de arquitetura | ✅ (G2: `validateQuantity` no service; controller só faz parse do formato do campo. G3: `PriceCheckController` só lê via `ProductService`, sem regra) |
| RNF14 | Backup diário automático do banco | rotina/script | ⬜ |
| RNF15 | Comprovante não fiscal diz "NÃO É DOCUMENTO FISCAL" | revisão legal | 🟡 prévia do cupom na tela de venda exibe o aviso "NÃO É DOCUMENTO FISCAL"; Consulta de preço avisa "SÓ CONSULTA — NÃO É VENDA" (G3); reforçar na impressão térmica (F2) |
| RNF16 | XMLs das NFC-e guardados ≥ 5 anos | armazenamento fiscal | ⬜ (F3) |

---

## Atalhos de teclado (planejamento) e estado exigido
| Tecla | Ação | Estado exigido |
|---|---|---|
| F1 | Consulta de preço (tela dedicada, #G3 — ligado 2026-10-22) | venda **inativa** (sem itens / não EM_PAGAMENTO) |
| F2 | Nova venda | sem venda ativa |
| F3 | Buscar produto por nome | — |
| ~~F4~~ | ~~Quantidade (multiplica próximo item)~~ | **Substituído (G2, 2026-10-22):** quantidade/peso por `qtd*código` digitado no próprio campo do leitor (ex.: `6*7891234`, `1,250*7899`). **F4 não é mais necessário nem será ligado** (evita UI morta). |
| F5 | Desconto (com autorização) | venda ativa |
| F6 | Cancelar item | venda ativa com itens |
| F7 | Cancelar venda | venda ativa |
| F9 | Cliente (setCustomer, extra ERP) | venda ativa |
| F10 | Finalizar / ir ao pagamento | venda ativa |
| — | Sangria / Suprimento | caixa aberto |
| — | Relatórios | venda **inativa** |
| — | Abrir caixa | login feito |
| — | Fechar caixa | venda inativa |

> Observação: estes atalhos vêm do planejamento (sistema de referência Yzidro). A
> atribuição final das teclas no Mercado Scopel é decisão do **Arquiteto** +
> **Frontend**, mantendo a regra de estado acima. F5/F6/F7/F9/F10 já ligados na venda
> (G5, event filter sensível ao contexto). F4 (quantidade) foi resolvido pelo
> mecanismo `qtd*código` no leitor (G2) e não será ligado. **F1 = Consulta de preço**
> ligado no shell (G3, `MainController.onConsultaPreco`) com guard RN21 de venda inativa.

---

## Fora de escopo (descartado das referências de design)
O mercadinho é mais simples que o ERP de referência. **Não** implementar, salvo
pedido explícito: cadastro completo de cliente, vendedor/comissão, crediário,
venda consignada, "a prazo"/devolução, orçamento, compra, produção, frota,
contabilidade. Cliente no PDV é apenas **CPF no cupom** (RF24/RN20).
