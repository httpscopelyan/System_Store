# Roteiro E2E — 3ª leva: Voltar/editar do pagamento + Cadastro rápido (visão do operador)

> Autor: John (operador-usuário) · Time de Validação PDV
> Alvo: validar NA TELA as 2 mudanças da 3ª leva (172 testes unitários já verdes).
> Operação **100% teclado** (RNF03) — o leitor de código de barras age como
> teclado (digita o código + Enter). O mouse só é citado como alternativa.
>
> **Este roteiro NÃO roda o app e NÃO pede automação.** O dev decidiu PULAR o
> pyautogui nesta leva. Rodar `mvn javafx:run` é ação de ambiente feita pelo
> **próprio dev**, que executa os passos abaixo manualmente, à mão.

---

## 0. Convenções

- `[F2]`, `[Enter]`, `[ESC]`, `[1]`, `[Tab]` = tecla pressionada.
- "bipar" = digitar o código no campo Código e dar `[Enter]` (simula o leitor USB).
- Valores pt-BR: vírgula decimal (`2,50`). Os campos aceitam vírgula ou ponto.
- Navegação global por accelerator do shell: **F1 Preço · F2 Venda · F3 Estoque ·
  F4 Caixa · F8 Relatórios**. Na tela de Venda: **F5** desconto · **F6** cancelar
  item · **F7** cancelar venda · **F9** cliente · **F10** finalizar (ir ao pagamento).
- Peso/quantidade no campo Código: formato `quantidade*código` (ex.: `1,250*789...`).

### O que mudou (resumo do operador)

**Mudança 1 — Voltar do pagamento agora LIBERA a venda pra editar.** Antes, quando
eu apertava F10 e ia pro pagamento, se eu desistisse e voltasse, a venda ficava
"presa" EM_PAGAMENTO — eu voltava pro pagamento de novo e não conseguia mexer nos
itens. AGORA, na tela de seleção de forma (1/2/3), se eu aperto `[ESC]` (ou
"Voltar à venda"), a venda **volta ABERTA e editável**: dá pra bipar mais item,
tirar item e finalizar de novo. (Isto resolve direto o ponto de usabilidade que
anotei no Fluxo 4 da 2ª leva.)

**Mudança 2 — Cadastro rápido quando o código não existe.** Antes, bipar um código
sem cadastro só dava "Produto não encontrado" e eu tinha que largar a venda, ir no
Estoque cadastrar e voltar. AGORA, bipar um código inexistente abre um **modal de
cadastro rápido** com o código já preenchido e travado; eu digito nome + preço +
unidade (UN/KG, default UN), salvo, e **a venda em andamento continua intacta**.
O produto NÃO entra sozinho: aparece "Produto cadastrado — bipe novamente para
vender" e eu **rebipo** pra lançar o item (preço = o que acabei de cadastrar).

---

## 1. Pré-condições de ambiente (o dev prepara ANTES de rodar)

1. Container Docker `db_scopel` de pé (porta 5432), `.env` com a senha.
2. Banco migrado (Flyway até a última versão aplicada).
3. **Um usuário operador.** Sem seed de usuário nas migrations (senha BCrypt —
   regra inviolável 3, nada de INSERT cru). O dev cria pelo fluxo do app. Assumo:
   - operador: `operador` · senha `1234`
4. **Produtos semeados** — reaproveito o **mesmo seed do `roteiro_1a_leva.md`**
   (`tests/e2e/seed_roteiro_1a_leva.sql`). Os códigos EXISTENTES usados aqui:
   - `7891000100103` — LEITE INTEGRAL 1L (R$ 5,49, UN)
   - `7894900011517` — REFRIGERANTE COLA 2L (R$ 8,99, UN)
   - `7896005800010` — CAFE TORRADO 500G (R$ 15,90, UN)
5. **Códigos que NÃO podem existir no banco** (são cadastrados na hora pelo próprio
   roteiro, via modal). Se por acaso já existirem, troque por outros inéditos:
   - `7890000000001` — vira "TESTE UN" (Fluxo 3)
   - `7890000000002` — vira "BANANA KG" (Fluxo 4)
   - `7890000000003` — usado só pra cancelar o modal (Fluxo 5)
   - `7890000000004` — usado nas bordas (Fluxo 6)
   > Confirme com o dev que esses 4 códigos **não estão** no `product`. Como o
   > cadastro rápido grava no banco de verdade, **reexecutar o roteiro** fará o
   > código já existir (vira venda normal, não abre modal). Para repetir do zero,
   > o dev apaga esses produtos: `DELETE FROM product WHERE code LIKE '789000000000%';`
6. App rodando com `mvn javafx:run` (JDK 26). Janela em **1366×768**.

---

## FLUXO A — Base: login e abertura de caixa

| # | Ação (teclado) | Resultado esperado |
|---|---|---|
| A1 | Login: digitar `operador`, `[Tab]`, `1234`, `[Enter]`. | Entra no sistema; barra inferior "Usuário: operador". |
| A2 | `[F4]` → tela **Caixa**. | Status grande = **FECHADO**. |
| A3 | Campo de abertura: `100,00`, acionar **Abrir caixa**. | "Caixa aberto!"; status **ABERTO**; saldo inicial R$ 100,00. |

> Sem isso a Venda bloqueia (RN14). Mantenha este caixa aberto para todos os fluxos.

---

## FLUXO 1 — Voltar do pagamento e CONTINUAR editando  ⟵ RESSALVA DO QA (`PaymentController.onVoltar`, UI)

> **Esta é a RESSALVA que o QA não cobre em unitário: o roteamento de VOLTA ao
> apertar ESC/"Voltar à venda" na tela de seleção de forma.** O QA validou no
> service a transição EM_PAGAMENTO → ABERTA (`SaleService.reopenForEditing`); aqui
> eu confiro que o `[ESC]` na tela de Pagamento realmente reabre a venda editável
> na TELA de Venda (itens preservados, dá pra somar mais e finalizar).

| # | Ação | Resultado esperado |
|---|---|---|
| 1.1 | `[F2]` → tela **Venda**. | Venda nova vazia; TOTAL **R$ 0,00**; barra do último item = **"—"**; foco no campo Código. |
| 1.2 | Bipar `7891000100103` (`[Enter]`). | LEITE na tabela (Qtd 1 · 5,49); TOTAL **R$ 5,49**; barra = **"LEITE INTEGRAL 1L"**. |
| 1.3 | Bipar `7894900011517` (`[Enter]`). | REFRI entra; 2 linhas; TOTAL **R$ 14,48**; barra = **"REFRIGERANTE COLA 2L"**. **Anote: 2 itens, R$ 14,48.** |
| 1.4 | `[F10]` (Finalizar). | Vai para **Pagamento** (seleção 1-Dinheiro / 2-Cartão / 3-PIX); TOTAL A PAGAR **R$ 14,48**. A venda está **EM_PAGAMENTO**. No rodapé aparece o atalho "ESC — Voltar à venda". |
| 1.5 | **`[ESC]`** (ou acionar "Voltar à venda"). | **PONTO-CHAVE.** Volta para a tela **Venda** com os **mesmos 2 itens** (LEITE + REFRI), TOTAL **R$ 14,48**, foco no campo Código. A venda agora está de novo **ABERTA e editável** — NÃO ficou presa voltando pro pagamento. |
| 1.6 | Bipar `7896005800010` (`[Enter]`). | CAFE entra como **3º item** na MESMA venda; TOTAL **R$ 30,38**. Prova que voltou editável (adicionar item só vale em ABERTA — RN21). |
| 1.7 | `[F10]` → `[1]` (Dinheiro) → digitar `50,00`, `[Enter]`. | Tela Dinheiro: TOTAL R$ 30,38; TROCO exibido **R$ 19,62**; confirma; **Venda finalizada**: DINHEIRO, total R$ 30,38, troco R$ 19,62. Venda vai a FINALIZADA. |

> **FALHA a reportar** se no passo 1.5: a tela voltar pro **Pagamento** (preso), ou
> abrir **venda nova vazia**, ou os 2 itens sumirem. E, no 1.6, se o bipe for
> recusado com erro de estado (sinal de que a venda não voltou pra ABERTA).
>
> **Parecer de usabilidade (anotar):** agora existe um caminho CLARO para desistir
> do pagamento e mexer na venda — era exatamente a dúvida que deixei no Fluxo 4 da
> 2ª leva. Confirme que o atalho "ESC — Voltar à venda" está visível e óbvio na
> tela de seleção de forma.

---

## FLUXO 2 — Cadastro rápido UN: bipar inexistente, cadastrar, rebipar  ⟵ RESSALVA DO QA (`QuickProductController`, UI/modal)

> **Esta é a RESSALVA que o QA não cobre em unitário: o modal de cadastro rápido
> (abre/trava código/foca nome/salva/volta à venda).** Valide com atenção.

| # | Ação | Resultado esperado |
|---|---|---|
| 2.1 | `[F2]` → nova Venda; bipar `7891000100103` (`[Enter]`). | LEITE na venda; TOTAL **R$ 5,49**. (Serve pra provar que a venda sobrevive ao modal.) |
| 2.2 | Bipar `7890000000001` (`[Enter]`) — **código inexistente**. | Abre o **modal "Cadastro rápido de produto"**. O campo **Código** já vem preenchido com `7890000000001` e **travado** (não editável). O foco cai no campo **Nome**. Unidade default = **UN**. |
| 2.3 | Digitar no Nome: `TESTE UN`. `[Tab]` até Preço (ou clicar). Digitar Preço: `2,50`. Deixar Unidade = **UN**. | Campos preenchidos; sem mensagem de erro. |
| 2.4 | `[Enter]` (ou botão **Salvar (Enter)**). | Modal fecha; volta à tela de **Venda** com o LEITE ainda lá (TOTAL **R$ 5,49** intacto). Status mostra **"Produto cadastrado — bipe novamente para vender"**; campo Código limpo e com foco. **O produto NÃO foi adicionado sozinho.** |
| 2.5 | **Rebipar** `7890000000001` (`[Enter]`). | Agora entra como item: **TESTE UN**, Qtd 1, preço **2,50**; TOTAL passa a **R$ 7,99** (5,49 + 2,50). Preço congelado no item = o que cadastrei (RN19). |
| 2.6 | `[F10]` → `[1]` (Dinheiro) → `10,00`, `[Enter]`. | TOTAL R$ 7,99; TROCO **R$ 2,01**; Venda finalizada: DINHEIRO. |

> **FALHA a reportar** se: o código vier editável/vazio no modal; o foco não cair
> no Nome; o produto entrar na venda sozinho sem rebipar; a venda (LEITE) sumir ao
> fechar o modal; ou o rebipe não achar o produto recém-criado.
>
> **Observação de operador (não é falha):** o produto nasce com **estoque 0**, e o
> sistema **não barra** a venda por falta de estoque — ao finalizar, o estoque do
> TESTE UN fica **negativo (−1)**. Faz sentido num cadastro de emergência no
> balcão, mas anoto para o Coordenador: o dev precisa ajustar o estoque depois
> (Estoque → Alterar), senão o inventário fica torto.

---

## FLUXO 3 — Cadastro rápido KG: unidade KG importa de verdade (rebipe por peso)

> Prova que a **unidade escolhida no modal** vale: cadastro como KG e rebipo no
> formato peso `quantidade*código`. Se o produto fosse UN, o mesmo rebipe seria
> **barrado** (fração proibida em UN — RN05).

| # | Ação | Resultado esperado |
|---|---|---|
| 3.1 | `[F2]` → nova Venda. | Venda nova vazia; TOTAL R$ 0,00. |
| 3.2 | Bipar `7890000000002` (`[Enter]`) — **código inexistente**. | Abre o modal com `7890000000002` travado no Código; foco no Nome; Unidade default UN. |
| 3.3 | Nome: `BANANA KG`. Preço: `5,99`. **Trocar a Unidade para `KG`** (abrir o combo com teclado — seta ↓ — e selecionar KG). | Unidade = **KG** selecionada; sem erro. |
| 3.4 | `[Enter]` / **Salvar**. | Modal fecha; status "Produto cadastrado — bipe novamente para vender"; venda segue vazia e intacta; foco no Código. |
| 3.5 | **Rebipar por peso:** digitar `1,250*7890000000002`, `[Enter]`. | Entra o item **BANANA KG** com Qtd/peso **1,250 kg**, preço 5,99/kg; total da linha **R$ 7,49** (1,250 × 5,99 = 7,4875 → 7,49 HALF_UP); TOTAL da venda **R$ 7,49**. **Isto só é aceito porque a unidade é KG.** |
| 3.6 | (Contraprova opcional) Em outra venda, bipar por peso um produto **UN** existente: `1,250*7891000100103`. | **Recusado** com a mensagem **"Produto vendido por unidade não aceita fração"** — confirma que a unidade KG do passo 3.5 é o que liberou o peso fracionado. |
| 3.7 | Voltar à venda da BANANA, `[F10]` → `[1]` → `10,00`, `[Enter]`. | TOTAL R$ 7,49; TROCO **R$ 2,51**; Venda finalizada. |

> **FALHA a reportar** se: o rebipe `1,250*código` for recusado mesmo com o produto
> cadastrado como KG; ou se um produto UN aceitar o peso fracionado no passo 3.6.

---

## FLUXO 4 — Cancelar o modal (ESC): mantém "não encontrado" e venda intacta

| # | Ação | Resultado esperado |
|---|---|---|
| 4.1 | `[F2]` → nova Venda; bipar `7896005800010` (`[Enter]`). | CAFE na venda; TOTAL **R$ 15,90**. |
| 4.2 | Bipar `7890000000003` (`[Enter]`) — **código inexistente**. | Abre o modal de cadastro rápido com `7890000000003` travado. |
| 4.3 | **`[ESC]`** (ou botão **Cancelar (Esc)**). | Modal fecha **sem cadastrar nada**. Volta à Venda; status mostra **"Produto não encontrado"**; a venda segue **intacta** (CAFE ainda lá, TOTAL **R$ 15,90**); campo Código limpo e com foco. |
| 4.4 | (Prova) `[F3]` → Estoque, procurar `7890000000003`. | **Não existe** — o cancelamento não gravou produto. |
| 4.5 | Voltar `[F2]`, bipar `7896005800010` de novo. | Soma na mesma venda (RN07): CAFE Qtd 2; TOTAL **R$ 31,80**. A venda continuou normal depois do cancelamento. |

> **FALHA a reportar** se: ao cancelar o modal a venda sumir/zerar; se um produto
> `7890000000003` acabar gravado no banco; ou se o status não voltar pra "Produto
> não encontrado".

---

## FLUXO 5 — Bordas de validação do modal (mensagens em PT, não salva)

> Usa `7890000000004` (inexistente) para abrir o modal e testar as recusas. Como o
> modal só fecha ao salvar OK ou cancelar, dá pra testar vários erros em sequência
> na mesma abertura.

| # | Ação (dentro do modal) | Resultado esperado |
|---|---|---|
| 5.1 | `[F2]` → nova Venda; bipar `7890000000004` → abre o modal. | Modal aberto; Código `7890000000004` travado; foco no Nome. |
| 5.2 | **Nome vazio**: deixar Nome em branco, Preço `2,00`, Salvar. | Mensagem **"Informe o nome do produto"** (em PT, vermelha). **Não salva**, modal continua aberto. |
| 5.3 | **Preço vazio**: Nome `ITEM X`, apagar o Preço, Salvar. | Mensagem **"Preço inválido"**. Não salva. |
| 5.4 | **Preço com letras**: Preço `abc`, Salvar. | Mensagem **"Preço inválido"**. Não salva. |
| 5.5 | **Preço negativo**: Preço `-1,00`, Salvar. | Mensagem **"Preço inválido"** (negativo é recusado). Não salva. |
| 5.6 | Corrigir: Nome `ITEM X`, Preço `3,00`, Unidade UN, Salvar. | Agora **salva**; volta à venda com "Produto cadastrado — bipe novamente para vender". (Fecha o teste de bordas.) |

### Borda "código já cadastrado" (defensiva — difícil reproduzir no balcão)

| # | Observação | Resultado esperado |
|---|---|---|
| 5.7 | O modal só abre para código **inexistente**; e o código vem **travado**, então o operador não consegue, no uso normal, digitar um código que já existe. A mensagem **"Este código já está cadastrado"** é uma **proteção defensiva** no `onConfirm` (dispara se o produto passar a existir entre abrir o modal e salvar — ex.: outra estação cadastrou o mesmo código no intervalo). | **Não é reproduzível manualmente por um único operador** de forma confiável. Deixo marcado para o Coordenador/QA: a cobertura dessa mensagem é por revisão de código / teste unitário, não por este roteiro de balcão. Se o dev quiser forçar: abrir o modal para `789...X`, e, antes de salvar, inserir `789...X` no banco por fora; ao salvar deve aparecer **"Este código já está cadastrado"** e não duplicar. |

> **FALHA a reportar** se: alguma mensagem sair em inglês ou genérica; se o modal
> **salvar** com nome vazio / preço inválido / preço negativo; ou se, no caso
> defensivo 5.7, gravar código duplicado em vez de mostrar a mensagem.

---

## Checklist de aceite da 3ª leva (resumo para o Coordenador)

- [ ] **F1** `[ESC]`/"Voltar à venda" na seleção de forma reabre a venda **ABERTA**
      e editável (itens preservados; dá pra bipar mais e finalizar). *(cobre
      RESSALVA `PaymentController.onVoltar`, UI)*
- [ ] **F2** Código inexistente abre o **modal** com código travado + foco no Nome;
      salvar NÃO adiciona sozinho; **rebipar** lança o item ao preço cadastrado;
      venda em andamento preservada. *(cobre RESSALVA `QuickProductController`, UI/modal)*
- [ ] **F3** Unidade **KG** escolhida no modal permite rebipe por peso
      `1,250*código`; um produto UN recusaria o mesmo peso (RN05).
- [ ] **F4** `[ESC]`/Cancelar no modal mantém "Produto não encontrado" e a venda
      intacta; nada é gravado.
- [ ] **F5** Bordas: nome vazio / preço vazio / preço com letras / preço negativo →
      mensagem em PT, **não salva**; "código já cadastrado" é defensivo (revisão de
      código, não reproduzível no balcão).
- [ ] (Obs. de operador) Produto do cadastro rápido nasce com estoque 0 e a venda
      não é barrada por estoque — estoque fica negativo; dev precisa ajustar depois.

---

## Automação (pyautogui)

**Nesta leva NÃO há automação.** O dev decidiu pular o pyautogui, então **não
estou solicitando** script ao Senior Backend nem ao Coordenador. Este roteiro é
para **validação manual** feita pelo próprio dev com `mvn javafx:run`, passo a
passo.
