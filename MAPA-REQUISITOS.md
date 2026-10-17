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
> Última atualização: 2026-10-17.

## Legenda de status
- ✅ **Feito** — implementado e (quando dito) testado.
- 🟡 **Parcial** — base existe, falta completar.
- ⬜ **Futuro** — ainda não começado.

## Fases
- **F1 — Fase atual:** teste do `SaleService`, bug do caixa aberto, **design das
  telas** (inicial → venda → pagamento → login → gestão), **tela de pagamento +
  troco**.
- **F2 — Impressão:** cupom + abertura de gaveta (escpos-coffee).
- **F3 — Fiscal:** NFC-e / CF-e SAT.
- **F4 — Pagamento integrado:** Mercado Pago Point via API + polling.
- **F5 — Gestão:** cadastros e relatórios completos.

---

## Requisitos Funcionais (RF)

### Módulo Produtos
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF01 | Cadastrar produto | ✅ | — | `create_product.fxml` + `ProductService` |
| RF02 | Editar produto | ⬜ | F5 | |
| RF03 | Inativar produto (nunca excluir) | 🟡 | F5 | entidade tem `active` |
| RF04 | Buscar produto por código de barras | ✅ | — | usado ao bipar |
| RF05 | Buscar produto por descrição (parcial) | ⬜ | F5 | |
| RF06 | Gerar código interno EAN-13 (produto sem código) | ⬜ | F5 | |
| RF07 | Consulta de preço (tela rápida) | ⬜ | F5 | depende de estado "venda inativa" |
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
| RF16 | Adicionar item por código digitado | 🟡 | F1 | mesmo campo do leitor |
| RF17 | Alterar quantidade de um item | ⬜ | F1 | F4 = quantidade |
| RF18 | Remover item da venda (com autorização) | ⬜ | F1 | F6; RN autorização gerente |
| RF19 | Aplicar desconto no item | ⬜ | F1 | F5 |
| RF20 | Aplicar desconto no total | ⬜ | F1 | RN08 (>10% exige gerente) |
| RF21 | Cancelar venda em andamento (com autorização) | ⬜ | F1 | F7; RN17 (mantém registro CANCELADA) |
| RF22 | Total/subtotal/qtd em tempo real | 🟡 | F1 | base feita na TableView |
| RF23 | Interpretar etiqueta de balança (código "2…") | ⬜ | F5 | RN06 |
| RF24 | Informar CPF do consumidor antes de finalizar | ⬜ | F1/F3 | RN20 (entra no XML) |

### Módulo Pagamento
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF25 | Selecionar forma de pagamento | 🟡 | F1 | só DINHEIRO (service testado); CARTAO/PIX ficam em F4 |
| RF26 | Receber em dinheiro e calcular troco | ✅ | F1 | `SaleService.pay`/`calculateChange` testados (RN09); UI `PaymentController` a validar |
| RF27 | Receber em cartão via maquininha | ⬜ | F4 | Mercado Pago Point |
| RF28 | Receber em PIX | ⬜ | F4 | |
| RF29 | Dividir pagamento em várias formas | ⬜ | F4 | RN18 |
| RF30 | Tratar pagamento negado sem perder a venda | ⬜ | F4 | |
| RF31 | Cancelar cobrança pendente na maquininha | ⬜ | F4 | |

### Módulo Caixa
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF32 | Abrir caixa com fundo de troco | ✅ | — | RN13 |
| RF33 | Registrar sangria | ✅ | — | RN14/RN15 |
| RF34 | Registrar suprimento | ✅ | — | RN14 |
| RF35 | Fechar caixa com conferência e diferença | ✅ | — | RN16 |
| RF36 | Emitir relatório de fechamento | ⬜ | F5 | |
| RF37 | Abrir a gaveta nas situações autorizadas | ⬜ | F2 | RN11 |

### Módulo Usuários
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF38 | Autenticar usuário | ✅ | — | BCrypt, `UserService.login` |
| RF39 | Cadastrar e editar usuários | ⬜ | F5 | nunca por SQL cru (fura o hash) |
| RF40 | Definir perfil (operador/gerente) | 🟡 | F1 | enum `Role`; falta UI de gestão |
| RF41 | Autorização de gerente em operações sensíveis | ⬜ | F1 | RN08/RNF09 |

### Módulo Impressão
| RF | Descrição | Status | Fase | Notas |
|---|---|---|---|---|
| RF42 | Imprimir comprovante da venda | ⬜ | F2 | RNF15: "NÃO É DOCUMENTO FISCAL" |
| RF43 | Reimprimir o último comprovante | ⬜ | F2 | RNF06 |
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
| RN02 | Código de barras como texto (zeros à esquerda) | ✅ (inviolável) |
| RN03 | Total item = preço × qtd, arredondado no fim | ✅ |
| RN04 | Total venda = itens − descontos, nunca negativo | 🟡 (falta desconto) |
| RN05 | KG aceita fracionário (3 casas); UN só inteiro | 🟡 |
| RN06 | Código "2…" = etiqueta de balança | ⬜ |
| RN07 | Bipar item já presente incrementa quantidade | ✅ |
| RN08 | Desconto > 10% exige gerente | ⬜ |
| RN09 | Venda só gravada após confirmação do pagamento | ✅ (`SaleService.pay` grava `Payment` + baixa estoque + FINALIZADA, em `@Transactional`; testado 2026-10-17) |
| RN10 | Gravar venda + baixar estoque na mesma transação | ✅ |
| RN11 | Gaveta só abre: venda em dinheiro, sangria, suprimento | ⬜ (F2) |
| RN12 | Estoque negativo permitido, gera alerta | 🟡 |
| RN13 | Um operador = um caixa aberto por vez | ✅ |
| RN14 | Não inicia venda com caixa fechado | ✅ |
| RN15 | Sangria não deixa saldo em dinheiro negativo | ✅ |
| RN16 | Fechamento registra diferença, não impede fechar | ✅ |
| RN17 | Venda cancelada mantém registro (status, usuário, motivo) | ⬜ |
| RN18 | Pagamento múltiplo; soma ≥ total; só dinheiro gera troco | ⬜ (F4) |
| RN19 | Preço congelado no item | ✅ (inviolável) |
| RN20 | CPF do consumidor antes de finalizar (entra no XML) | ⬜ |
| RN21 | Máquina de estados: ação fora do estado é recusada pelo service | 🟡 (reforçado 2026-10-17 com estado `EM_PAGAMENTO`: `goToPayment` só de ABERTA+itens, `pay` só de EM_PAGAMENTO, `addItem` só em ABERTA — testado; demais estados a completar) |

---

## Requisitos Não Funcionais (RNF)
| RNF | Descrição | Como verificar | Status |
|---|---|---|---|
| RNF01 | Busca por código < 300 ms com 20.000 produtos | teste de carga / índice no banco | ⬜ |
| RNF02 | Finalizar venda (gravar + baixar estoque) < 1 s | teste de tempo | ⬜ |
| RNF03 | Operação de venda sem mouse | revisão de UX + foco/atalhos | 🟡 |
| RNF04 | Total legível a 1 m | fonte grande, alto contraste (design) | 🟡 |
| RNF05 | Falha de energia não deixa estoque/caixa inconsistente | `@Transactional` + testes | 🟡 |
| RNF06 | Falha de impressão não impede a venda já gravada; reimprime | ordem: gravar → imprimir | ⬜ (F2) |
| RNF07 | Opera sem internet (exceto fiscal e pagamento integrado) | arquitetura offline-first | ✅ |
| RNF08 | Senhas em hash BCrypt, nunca em texto puro | revisão de segurança | ✅ (inviolável) |
| RNF09 | Operações sensíveis exigem gerente | revisão de segurança | ⬜ |
| RNF10 | Toda abertura de gaveta registra operador/data/hora/motivo | auditoria | ⬜ (F2) |
| RNF11 | Nenhuma venda é apagada; cancelar = status | revisão de modelo | 🟡 |
| RNF12 | Windows 10+, 4 GB RAM, 1366×768 | teste em ambiente-alvo | 🟡 |
| RNF13 | Regra de negócio isolada no service, sem JavaFX | revisão de arquitetura | ✅ |
| RNF14 | Backup diário automático do banco | rotina/script | ⬜ |
| RNF15 | Comprovante não fiscal diz "NÃO É DOCUMENTO FISCAL" | revisão legal | ⬜ (F2) |
| RNF16 | XMLs das NFC-e guardados ≥ 5 anos | armazenamento fiscal | ⬜ (F3) |

---

## Atalhos de teclado (planejamento) e estado exigido
| Tecla | Ação | Estado exigido |
|---|---|---|
| F2 | Nova venda | sem venda ativa |
| F3 | Buscar produto por nome | — |
| F4 | Quantidade (multiplica próximo item) | venda ativa |
| F5 | Desconto (com autorização) | venda ativa |
| F6 | Cancelar item | venda ativa com itens |
| F7 | Cancelar venda | venda ativa |
| F10 | Finalizar / ir ao pagamento | venda ativa |
| — | Sangria / Suprimento | caixa aberto |
| — | Consulta de preço | venda **inativa** |
| — | Relatórios | venda **inativa** |
| — | Abrir caixa | login feito |
| — | Fechar caixa | venda inativa |

> Observação: estes atalhos vêm do planejamento (sistema de referência Yzidro). A
> atribuição final das teclas no Mercado Scopel é decisão do **Arquiteto** +
> **Frontend**, mantendo a regra de estado acima.

---

## Fora de escopo (descartado das referências de design)
O mercadinho é mais simples que o ERP de referência. **Não** implementar, salvo
pedido explícito: cadastro completo de cliente, vendedor/comissão, crediário,
venda consignada, "a prazo"/devolução, orçamento, compra, produção, frota,
contabilidade. Cliente no PDV é apenas **CPF no cupom** (RF24/RN20).
