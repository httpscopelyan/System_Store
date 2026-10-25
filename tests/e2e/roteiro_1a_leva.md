# Roteiro E2E — 1ª leva de correções (visão do operador de caixa)

> Autor: John (operador-usuário) · Time de Validação PDV
> Alvo: validar NA TELA as 5 correções da 1ª leva (150 testes unitários já verdes).
> Operação **100% teclado** (RNF03) — o leitor de código de barras age como teclado
> (digita o código + Enter). O mouse só é citado como alternativa de emergência.
>
> **Este roteiro NÃO roda o app.** Rodar `mvn javafx:run` e instalar dependências é
> ação de ambiente — passa pelo Coordenador → dev. Ver seção "Automação" no fim.

---

## 0. Convenções

- `[F2]`, `[Enter]`, `[ESC]`, `[1]` = tecla pressionada.
- "bipar" = digitar o código no campo e dar `[Enter]` (simula o leitor USB).
- Valores em pt-BR: vírgula decimal (`20,00`). Os campos aceitam vírgula ou ponto.
- Navegação global por accelerator do shell: **F1 Preço · F2 Venda · F3 Estoque ·
  F4 Caixa · F8 Relatórios**. Na tela de Venda, F5/F6/F7/F9/F10 são atalhos da
  própria venda (não navegam).

---

## 1. Pré-condições de ambiente (o dev prepara ANTES de rodar)

1. Container Docker `db_scopel` de pé (porta 5432 publicada), `.env` com a senha.
2. Banco migrado (Flyway V1..V28 aplicadas).
3. **Um usuário operador existente.** Não há seed de usuário nas migrations (senha
   é BCrypt — regra inviolável 3, nada de INSERT cru de usuário). O dev cria pelo
   próprio fluxo do app. Para o roteiro assumo:
   - login: `operador` · senha: `1234` (parametrizável no script).
4. **Produtos semeados** (sem hash, pode ser SQL — ver seed abaixo). Precisamos de:
   - 1 produto de estoque **inteiro** (qtd 32, unidade UN) → item 2.
   - 1 produto de **peso** (qtd 1,25, unidade KG) → item 2.
   - produtos com preço conhecido para as 3 vendas do item 1.
5. App rodando com `mvn javafx:run` (JDK 26). Janela em **1366×768** para checar o
   item 3 (sem estouro / com scroll).

### Seed de produtos (`tests/e2e/seed_roteiro_1a_leva.sql`)

> Colunas reais da tabela `product` (V1 + V8): `code VARCHAR(15)`, `description`,
> `price NUMERIC(10,2)`, `quantity NUMERIC(10,3)`, `unit VARCHAR(2)`, `active`,
> `cost_price NUMERIC(10,2)`, `min_stock NUMERIC(10,3)`, `category`.
> Rodar com o container de pé: `docker exec -i db_scopel psql -U <user> -d <db> < seed_roteiro_1a_leva.sql`
> (o dev executa — é ação de ambiente).

```sql
-- Produtos de teste do roteiro da 1ª leva. Códigos fictícios estáveis.
INSERT INTO product (code, description, price, quantity, unit, active, cost_price, min_stock, category) VALUES
  ('7891000100103', 'LEITE INTEGRAL 1L',      5.49,  32.000, 'UN', TRUE,  4.00, 5.000, 'Laticinios'),
  ('7894900011517', 'REFRIGERANTE COLA 2L',   8.99,  20.000, 'UN', TRUE,  6.00, 4.000, 'Bebidas'),
  ('7896005800010', 'CAFE TORRADO 500G',     15.90,  10.000, 'UN', TRUE, 11.00, 3.000, 'Mercearia'),
  ('2000000000015', 'BANANA PRATA',           6.90,   1.250, 'KG', TRUE,  4.50, 0.500, 'Hortifruti')
ON CONFLICT (code) DO UPDATE SET
  price = EXCLUDED.price, quantity = EXCLUDED.quantity, unit = EXCLUDED.unit,
  active = EXCLUDED.active, cost_price = EXCLUDED.cost_price, min_stock = EXCLUDED.min_stock;
```

- **LEITE (32 UN)** → usado no item 2 (estoque inteiro deve exibir "32").
- **BANANA (1,25 KG)** → usado no item 2 (peso deve exibir "1.25").
- LEITE / REFRI / CAFE → as 3 vendas do item 1.

---

## FLUXO A — Login e abertura de caixa (base para tudo)

| # | Ação (teclado) | Resultado esperado |
|---|---|---|
| A1 | App abre na tela de **Login**. Digitar `operador` no campo Login, `[Tab]`, digitar `1234`, `[Enter]`. | Entra no sistema; barra de status inferior mostra "Usuário: operador" e "Caixa fechado". |
| A2 | `[F4]` para abrir a tela **Caixa**. | Abre a tela Caixa; status grande = **FECHADO**. |
| A3 | No campo "valor de abertura/troco" digitar `100,00` e acionar **Abrir caixa**. | Mensagem "Caixa aberto!"; status = **ABERTO**; "Saldo inicial" no resumo = **R$ 100,00**; barra de status inferior passa a indicar caixa aberto. |

> Resultado esperado consolidado do Fluxo A: operador logado + caixa aberto com
> R$ 100,00. Sem isso as vendas são bloqueadas (RN14: "Abra o caixa antes de vender").

---

## FLUXO B — Correção 1: DÉBITO não é mais contado como CRÉDITO (PRIORIDADE)

Esta é a verificação que o QA não cobre unitariamente — é o foco da leva.
Três vendas (débito, crédito, dinheiro) + suprimento + sangria, depois conferir o
resumo do Caixa.

### B1 — Venda paga em CARTÃO DÉBITO
| # | Ação | Resultado esperado |
|---|---|---|
| B1.1 | `[F2]` → tela **Venda**. | Campo "Código de barras" com foco; venda nova vazia; TOTAL R$ 0,00. |
| B1.2 | Bipar `7891000100103` (`[Enter]`). | LEITE entra na tabela (Qtd 1, Preço 5,49); TOTAL **R$ 5,49**; faixa azul fina do último item mostra **só o nome** "LEITE INTEGRAL 1L". |
| B1.3 | `[F10]` (Finalizar). | Vai para tela **Pagamento** (seleção de forma); TOTAL A PAGAR **R$ 5,49**. |
| B1.4 | `[2]` (Cartão). | Vai para **Pagamento em Cartão** (DÉBITO/CRÉDITO). |
| B1.5 | `[1]` (Débito). | Vai para tela de **espera da maquininha**: "Passe o cartão... ENTER para confirmar"; tipo = **DEBITO**. |
| B1.6 | `[Enter]` (confirma — mock aprova; não há maquininha real). | Vai para **Venda finalizada**: forma CARTÃO, total R$ 5,49, sem troco. |

### B2 — Venda paga em CARTÃO CRÉDITO
| # | Ação | Resultado esperado |
|---|---|---|
| B2.1 | `[F2]` → nova Venda. | Venda nova vazia; faixa do último item volta para "—". |
| B2.2 | Bipar `7894900011517` (`[Enter]`). | REFRIGERANTE COLA 2L na tabela; TOTAL **R$ 8,99**; faixa mostra só "REFRIGERANTE COLA 2L". |
| B2.3 | `[F10]` → `[2]` (Cartão) → `[2]` (Crédito) → `[Enter]`. | Tipo exibido = **CREDITO**; mock aprova; Venda finalizada: CARTÃO, R$ 8,99. |

### B3 — Venda paga em DINHEIRO (com troco)
| # | Ação | Resultado esperado |
|---|---|---|
| B3.1 | `[F2]` → nova Venda. | Venda nova vazia. |
| B3.2 | Bipar `7896005800010` (`[Enter]`). | CAFE TORRADO 500G; TOTAL **R$ 15,90**; faixa mostra só "CAFE TORRADO 500G". |
| B3.3 | `[F10]` → `[1]` (Dinheiro). | Tela **Pagamento em Dinheiro**; TOTAL R$ 15,90; TROCO R$ 0,00. |
| B3.4 | Digitar `20,00` no "Valor recebido", `[Enter]`. | TROCO exibido = **R$ 4,10**; confirma; Venda finalizada: DINHEIRO, troco R$ 4,10. |

### B4 — Suprimento e Sangria
| # | Ação | Resultado esperado |
|---|---|---|
| B4.1 | `[F4]` → tela **Caixa**. | Caixa ABERTO. |
| B4.2 | No campo de movimento digitar `50,00` e acionar **Suprimento**. | "Suprimento registrado!"; aparece uma linha de entrada na movimentação. |
| B4.3 | No campo de movimento digitar `30,00` e acionar **Sangria**. | "Sangria registrada!"; aparece uma linha de saída. |

### B5 — Conferência do resumo do Caixa (o teste central)
Com as 3 vendas + suprimento + sangria lançados, conferir na tela Caixa (F4):

| Campo / coluna | Valor esperado | Por quê |
|---|---|---|
| Resumo **Crédito** (`resEntCredito`) | **R$ 8,99** | só a venda em crédito |
| Resumo **Débito** (`resEntDebito`) | **R$ 5,49** (≠ zero!) | a venda em débito entra no débito, NÃO no crédito |
| Resumo **Entradas em Dinheiro** (`resEntDinheiro`) | **R$ 65,90** | dinheiro da venda (15,90) + suprimento (50,00) |
| Resumo **Saídas em Dinheiro** (`resSaiDinheiro`) | **R$ 30,00** | sangria |
| **Saldo final / em gaveta** (`resSaldoFinal`) | **R$ 135,90** | 100,00 + 65,90 − 30,00 — **cartão fica de fora da gaveta** |
| Linha da venda 1 na **Movimentação** | Débito 5,49 · Crédito 0,00 · Dinheiro 0,00 | débito na coluna certa |
| Linha da venda 2 na **Movimentação** | Crédito 8,99 · Débito 0,00 | crédito na coluna certa |

> **FALHA a reportar** se: Débito aparecer R$ 0,00 e Crédito R$ 14,48 (bug antigo:
> débito somado como crédito), ou se o saldo em gaveta incluir o valor dos cartões.

### B6 — Fechamento (confere a contagem)
| # | Ação | Resultado esperado |
|---|---|---|
| B6.1 | No campo "valor contado" digitar `135,90`, acionar **Fechar caixa**. | "Caixa FECHADO! Esperado: R$ 135,90 | Contado: R$ 135,90 | Diferença: R$ 0,00". (Se a impressora não existir, some um aviso de impressão, mas o fechamento NÃO cai — RNF06.) |

---

## FLUXO C — Correção 2: Quantidade sem zeros à direita (Estoque e Alterar produto)

| # | Ação | Resultado esperado |
|---|---|---|
| C1 | `[F3]` → tela **Estoque**. | Lista de produtos; foco no campo Código. |
| C2 | Olhar a coluna **"Qtd. Atual"** da linha do LEITE INTEGRAL 1L. | Exibe **`32`** — e NÃO `32.000`. |
| C3 | Olhar a coluna **"Qtd. Atual"** da linha da BANANA PRATA. | Exibe **`1.25`** — e NÃO `1.250` nem `1.2500`. |
| C4 | Conferir a coluna **"Qtd. Mín."** do CAFE (min_stock 3). | Exibe **`3`** (sem `.000`). Da BANANA (0,5) exibe **`0.5`**. |
| C5 | Selecionar LEITE (setas ↑/↓), acionar **Alterar** (✎). | Abre "Produtos — Alterar"; campo/label **"Estoque atual"** mostra **`32`** (sem zeros à direita). |

> Observação de operador: essa correção é só de exibição; o valor no banco continua
> `NUMERIC(10,3)`. Não deve mudar nenhum cálculo.

---

## FLUXO D — Correção 3: Tela de pagamento enxuta, com scroll, sem roubar foco

| # | Ação | Resultado esperado |
|---|---|---|
| D1 | Com caixa aberto, `[F2]`, bipar `7896005800010`, `[F10]`. | Abre a tela **Pagamento**. |
| D2 | Ler as opções visíveis. | Aparecem **somente**: `1 — DINHEIRO`, `2 — CARTÃO`, `3 — PIX`. |
| D3 | Procurar blocos "pagamento dividido" e "a prazo / crediário". | **Não existem** na tela (foram removidos). |
| D4 | Janela em 1366×768: verificar se a tela cabe. | Nada estoura para fora da janela; se o conteúdo passar da altura, há **scroll vertical** (ScrollPane). |
| D5 | Sem clicar com o mouse, pressionar `[1]`. | Vai direto para Pagamento em Dinheiro (o foco inicial está na área de opções; o teclado 1/2/3 funciona sem clique). |
| D6 | `[ESC]`. | Volta para a tela de **Venda** com os itens preservados. |
| D7 | `[F10]` de novo, testar `[2]` e `[3]` e `[ESC]` em cada. | `[2]` leva ao Cartão, `[3]` leva ao PIX, `[ESC]` sempre volta um passo. Foco nunca "preso" num campo que engula os dígitos. |

> **FALHA a reportar** se: aparecerem opções de dividido/crediário, se a tela cortar
> conteúdo em 1366×768 sem scroll, ou se 1/2/3 só funcionarem após clicar na tela.

---

## FLUXO E — Correção 4: Títulos padronizados (faixa fina azul)

Passar por todas as telas e conferir o topo:

| # | Tela (como chegar) | Resultado esperado |
|---|---|---|
| E1 | Venda (`[F2]`) | Topo = **faixa fina azul-marinho** (`.page-header`, ~32px), título branco pequeno "Venda — Mercado Scopel". Sem banner grande em gradiente. |
| E2 | Pagamento (`[F10]`) | Mesma faixa fina azul "Pagamento — Mercado Scopel". |
| E3 | Pagamento em Dinheiro / Cartão / PIX | Mesma faixa fina azul em todas. |
| E4 | Alterar produto (Estoque → selecionar → Alterar) | Faixa fina azul "Produtos — Alterar". |
| E5 | **Estoque** (`[F3]`) | **ATENÇÃO (possível pendência):** a tela de Estoque ainda usa a barra de título estilo "janela" (`.win-titlebar`, gradiente azul claro), e não a faixa `.page-header`. Se o combinado do item 4 é "TODAS as telas com a faixa fina", **reportar como inconsistência** para o Coordenador decidir se a Estoque entra no padrão. |

> Parecer de usabilidade: a faixa fina libera espaço vertical útil — bom para
> 1366×768. Só falta a Estoque alinhar ao padrão.

---

## FLUXO F — Correção 5: Barra do último item mostra só o nome

| # | Ação | Resultado esperado |
|---|---|---|
| F1 | `[F2]` nova venda. | Faixa do último item mostra o placeholder **"—"**. |
| F2 | Bipar `7891000100103` (`[Enter]`). | Faixa azul fina exibe **"LEITE INTEGRAL 1L"** — grande, branco, **só o nome** (sem preço, sem código, sem quantidade). |
| F3 | Bipar `7894900011517` (`[Enter]`). | Faixa passa a exibir **"REFRIGERANTE COLA 2L"** (acompanha o último bipado). |
| F4 | Pesquisa por quantidade: digitar `3*7896005800010`, `[Enter]`. | Entra 3× CAFE; faixa mostra **"CAFE TORRADO 500G"** (só o nome; a quantidade 3 aparece na tabela, não na faixa). |
| F5 | Cancelar um item (campo "Código p/ cancelar", digitar `7894900011517`, acionar Cancelar item). | A faixa reflete o último item que **restou** (sem "fantasma" do item removido). |
| F6 | Durante F2–F5, observar o campo de código. | O foco **nunca** sai do campo de código (RNF03): a faixa é só visual, `focusTraversable=false`. |

---

## Checklist de aceite da 1ª leva (resumo para o Coordenador)

- [ ] **C1** Débito aparece no Débito (R$ 5,49), crédito no Crédito (R$ 8,99); gaveta = 135,90 (cartão fora).
- [ ] **C2** Estoque mostra `32` e `1.25` (sem zeros à direita), inclusive em "Alterar produto".
- [ ] **C3** Pagamento só 1/2/3, sem dividido/crediário, com scroll em 1366×768, teclado sem roubo de foco.
- [ ] **C4** Faixa fina azul em todas as telas — **exceto Estoque (verificar/pendência)**.
- [ ] **C5** Barra do último item mostra só o nome, sem fantasma, sem roubar foco.

---

## Automação (pyautogui) — onde entra e o que depende de autorização

Eu (John) **não escrevo o script** e **não rodo o app**. Descrevo o que o script
precisa exercitar; o **Senior Backend** implementa em Python, e só roda **após o
dev autorizar** abrir o app e instalar dependências.

### O que o script deve exercitar (replicando este roteiro)
1. Abrir o app já rodando (janela em 1366×768) e logar (`operador`/`1234`,
   parametrizáveis).
2. `[F4]`, abrir caixa com `100,00`.
3. 3 vendas: débito (LEITE), crédito (REFRI), dinheiro (CAFE, recebido 20,00).
4. Suprimento 50,00 e sangria 30,00.
5. `[F4]` e **capturar a tela** do resumo do Caixa.
6. `[F3]` e **capturar a tela** da lista de Estoque.
7. `[F2]`+`[F10]` e **capturar a tela** de Pagamento.

### O que o script deve VERIFICAR (via OCR na captura — Tesseract/pytesseract)
- No resumo do Caixa: ler os campos e afirmar **Débito = 5,49**, **Crédito = 8,99**,
  **Entradas em dinheiro = 65,90**, **Saída = 30,00**, **Saldo final = 135,90**.
  Falha dura se Débito = 0,00 ou Crédito = 14,48.
- No Estoque: a célula de qtd do LEITE contém o texto **"32"** e **não** "32.000";
  a da BANANA contém **"1.25"** e não "1.250".
- No Pagamento: OCR acha "DINHEIRO", "CARTÃO", "PIX" e **não** acha "dividido" nem
  "crediário"/"prazo".
- (Opcional) comparar screenshots do topo das telas para confirmar a faixa fina.

### Dependências de ambiente (quem instala: o dev, após autorizar)
- `pip install pyautogui pytesseract pillow`
- **Tesseract OCR** instalado no Windows (binário) + idioma `por` se quiser PT.
- App rodando (`mvn javafx:run`, JDK 26) e banco `db_scopel` de pé, com o seed
  acima aplicado e o usuário operador criado.
- Resolução/escala de tela fixa (1366×768, 100%) — pyautogui depende de coordenadas.

### Riscos de automação que já antecipo como operador
- pyautogui clica/digita por **coordenada**; qualquer mudança de layout ou DPI
  quebra o script. Preferir navegação por **teclas de função** (F2/F3/F4/F10/1/2/3)
  em vez de cliques sempre que possível.
- Telas de cartão dependem de **ENTER manual** (mock aprova); o script precisa
  esperar a tela de espera aparecer antes do `[Enter]`.
- OCR de dinheiro pode confundir `,`/`.` — validar os dois formatos.

---

## SOLICITAÇÃO FORMAL AO COORDENADOR

Coordenador, segue minha solicitação como operador-usuário:

1. **Peça autorização ao dev** para (a) aplicar o seed `seed_roteiro_1a_leva.sql`,
   (b) garantir o usuário operador, (c) **instalar** `pyautogui`, `pytesseract`,
   `pillow` + **Tesseract OCR**, e (d) **rodar `mvn javafx:run`** para a validação
   na tela. Nada disso eu faço sozinho — é ação de ambiente.
2. **Solicite ao Senior Backend** o algoritmo Python (pyautogui + pytesseract) que
   executa os FLUXOS A–F deste roteiro e aplica as verificações da seção Automação,
   salvando as 3 capturas (Caixa, Estoque, Pagamento) e um log pass/fail por item.
3. Quando o script existir e o dev autorizar rodar, **me chame** para acompanhar a
   execução e dar o parecer de usabilidade real (o que travou, confundiu, demorou).

Enquanto isso não acontece, este roteiro serve para **validação manual** pelo dev,
passo a passo.
