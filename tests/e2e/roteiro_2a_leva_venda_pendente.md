# Roteiro E2E — 2ª leva: venda pendente preservada e restaurada (visão do operador)

> Autor: John (operador-usuário) · Time de Validação PDV
> Alvo: validar NA TELA a correção da 2ª leva — a venda em andamento **não se
> perde** ao sair da tela de Venda e voltar (162 testes unitários já verdes).
> Operação **100% teclado** (RNF03) — o leitor de código de barras age como
> teclado (digita o código + Enter). O mouse só é citado como alternativa.
>
> **Este roteiro NÃO roda o app e NÃO pede automação.** O dev decidiu PULAR o
> pyautogui nesta leva. Rodar `mvn javafx:run` é ação de ambiente feita pelo
> **próprio dev**, que executa os passos abaixo manualmente, à mão.

---

## 0. Convenções

- `[F2]`, `[Enter]`, `[ESC]`, `[1]` = tecla pressionada.
- "bipar" = digitar o código no campo e dar `[Enter]` (simula o leitor USB).
- Valores pt-BR: vírgula decimal (`20,00`). Os campos aceitam vírgula ou ponto.
- Navegação global por accelerator do shell: **F1 Preço · F2 Venda · F3 Estoque ·
  F4 Caixa · F8 Relatórios**. Também vale pelo **menu lateral** (setas + Enter) —
  é o caminho que o dev usou quando descobriu o bug (ir ao Estoque e voltar).
- Na tela de Venda: **F5** desconto · **F6** cancelar item · **F7** cancelar
  venda · **F9** cliente · **F10** finalizar (ir ao pagamento).

### O que mudou (resumo do operador)

ANTES: eu começava uma venda, bipava uns itens, saía pro Estoque pra ajustar um
produto, voltava pra Venda — e a venda **sumia** (abria uma vazia; a de antes
virava "venda fantasma" solta no banco). AGORA: ao voltar, a **mesma** venda
reaparece inteira (itens, total, barra do último item) e eu continuo de onde
parei. Venda finalizada/cancelada NÃO volta — aí sim abre uma nova vazia.

---

## 1. Pré-condições de ambiente (o dev prepara ANTES de rodar)

1. Container Docker `db_scopel` de pé (porta 5432), `.env` com a senha.
2. Banco migrado (Flyway até a última versão aplicada).
3. **Um usuário operador** e **um usuário gerente/supervisor** (para o F7 do
   Fluxo 3). Sem seed de usuário nas migrations (senha BCrypt — regra inviolável
   3, nada de INSERT cru). O dev cria pelo fluxo do app. Assumo:
   - operador: `operador` · senha `1234`
   - gerente: `gerente` · senha `1234` (ou o supervisor que o dev já usa)
4. **Produtos semeados** — reaproveito o **mesmo seed do `roteiro_1a_leva.md`**
   (`tests/e2e/seed_roteiro_1a_leva.sql`). Os códigos usados aqui:
   - `7891000100103` — LEITE INTEGRAL 1L (R$ 5,49)
   - `7894900011517` — REFRIGERANTE COLA 2L (R$ 8,99)
   - `7896005800010` — CAFE TORRADO 500G (R$ 15,90)
   - `2000000000015` — BANANA PRATA (R$ 6,90/KG) — usado no Estoque (ajuste)
5. App rodando com `mvn javafx:run` (JDK 26). Janela em **1366×768**.

> Se o seed da 1ª leva já estiver aplicado, não precisa reaplicar — os produtos
> acima bastam. Nenhum produto novo é exigido nesta leva.

---

## FLUXO A — Base: login e abertura de caixa (igual à 1ª leva)

| # | Ação (teclado) | Resultado esperado |
|---|---|---|
| A1 | Login: digitar `operador`, `[Tab]`, `1234`, `[Enter]`. | Entra no sistema; barra inferior "Usuário: operador". |
| A2 | `[F4]` → tela **Caixa**. | Status grande = **FECHADO**. |
| A3 | Campo de abertura: `100,00`, acionar **Abrir caixa**. | "Caixa aberto!"; status **ABERTO**; saldo inicial R$ 100,00. |

> Sem isso, a Venda bloqueia (RN14). O Fluxo 5 testa justamente o caso SEM caixa.

---

## FLUXO 1 — Caminho principal: a venda volta intacta  ⟵ RESSALVA DO QA (restauração visual)

> **Esta é a correção central e cobre a RESSALVA que o QA não conseguiu testar
> em unitário: a RESTAURAÇÃO VISUAL na tela** (tabela, TOTAL, barra do último
> item, foco). O QA validou a decisão de restaurar no service (`resumeOrOpenSale`);
> aqui eu confiro que a TELA de fato reaparece preenchida.

| # | Ação | Resultado esperado |
|---|---|---|
| 1.1 | `[F2]` → tela **Venda**. | Venda nova vazia; TOTAL **R$ 0,00**; barra do último item = **"—"**; foco no campo Código. |
| 1.2 | Bipar `7891000100103` (`[Enter]`). | LEITE na tabela (Qtd 1 · 5,49); TOTAL **R$ 5,49**; barra do último item = **"LEITE INTEGRAL 1L"**. |
| 1.3 | Bipar `7894900011517` (`[Enter]`). | REFRI entra; TOTAL **R$ 14,48**; barra = **"REFRIGERANTE COLA 2L"**. |
| 1.4 | Bipar `7896005800010` (`[Enter]`). | CAFE entra; TOTAL **R$ 30,38**; barra = **"CAFE TORRADO 500G"**; 3 linhas na tabela. **Anote este estado.** |
| 1.5 | Sair da Venda indo ao **Estoque**: `[F3]` (ou menu lateral → Estoque). | Abre a tela Estoque. A venda NÃO foi finalizada nem cancelada — só saí da tela. |
| 1.6 | (Opcional, como o dev fazia) ajustar/olhar um produto no Estoque, ex. BANANA `2000000000015`. | Faço o que preciso no Estoque normalmente. |
| 1.7 | **Voltar para a Venda**: `[F2]` (ou menu lateral → Venda). | **PONTO-CHAVE.** A venda volta **intacta**: as **mesmas 3 linhas** (LEITE, REFRI, CAFE), TOTAL **R$ 30,38**, barra do último item mostra **"CAFE TORRADO 500G"** (NÃO "—"), foco de volta no campo Código. **Não** abriu venda vazia. |
| 1.8 | Continuar bipando: `7891000100103` (`[Enter]`). | Soma na **MESMA** venda (RN07): LEITE vai a Qtd 2; TOTAL **R$ 35,87**. Não criou venda paralela. |
| 1.9 | Finalizar: `[F10]` → `[1]` (Dinheiro) → `20,00`... | *(ajuste o recebido ao total)* — o caminho de pagamento funciona normalmente sobre a venda restaurada; venda vai a FINALIZADA. |

> **FALHA a reportar** se no passo 1.7: a tela abrir **vazia** (TOTAL R$ 0,00),
> a barra do último item voltar para "—" com itens na tabela, o TOTAL divergir,
> ou os itens sumirem/duplicarem. Esse era exatamente o bug antigo.

---

## FLUXO 2 — Depois de finalizar, volta abrindo venda NOVA

| # | Ação | Resultado esperado |
|---|---|---|
| 2.1 | (Partindo de uma venda finalizada — ex.: fim do Fluxo 1.) Estar na tela "Venda finalizada" ou em outra tela. | Venda anterior está **FINALIZADA**. |
| 2.2 | `[F2]` → tela **Venda**. | Abre uma venda **NOVA e vazia**: TOTAL **R$ 0,00**, tabela vazia, barra = **"—"**, foco no Código. |
| 2.3 | Bipar `7896005800010` (`[Enter]`). | CAFE entra numa venda nova (TOTAL R$ 15,90). Nada da venda finalizada anterior reaparece. |

> **FALHA a reportar** se a venda já finalizada "voltar" na tela (itens antigos
> reaparecendo). Finalizada nunca se restaura.

---

## FLUXO 3 — Cancelar venda e sair: não restaura a cancelada

| # | Ação | Resultado esperado |
|---|---|---|
| 3.1 | `[F2]` → nova Venda; bipar `7891000100103` e `7894900011517`. | 2 itens; TOTAL **R$ 14,48**. |
| 3.2 | (Opcional) preencher o motivo no campo "motivo do cancelamento". | Motivo digitado. |
| 3.3 | `[F7]` (Cancelar venda). | Abre o diálogo de **autorização de supervisor** (cancelar venda exige gerente — RN08/RF21). |
| 3.4 | Autenticar como `gerente` / `1234` no diálogo, confirmar. | "Venda cancelada"; a tela **já abre uma venda nova vazia** (TOTAL R$ 0,00, barra "—"). A venda anterior ficou **CANCELADA**. |
| 3.5 | Sair para o **Estoque** (`[F3]`) e **voltar** (`[F2]`). | A venda **cancelada NÃO volta**. A tela mostra a venda vazia (nova), TOTAL R$ 0,00, barra "—", foco no Código. |
| 3.6 | Bipar `7896005800010` (`[Enter]`). | CAFE entra numa venda limpa; nada da venda cancelada reaparece. |

> Observação de operador: se eu cancelar o diálogo de supervisor (ESC), a venda
> **não** é cancelada e continua ABERTA com os itens — aí ela deve voltar intacta
> (vira o caso do Fluxo 1).
> **FALHA a reportar** se os itens da venda cancelada reaparecerem ao voltar.

---

## FLUXO 4 — EM_PAGAMENTO: sair da Venda me devolve ao PAGAMENTO  ⟵ RESSALVA DO QA

> **Esta é a 2ª RESSALVA que o QA não conseguiu testar em unitário: o
> ROTEAMENTO de volta ao Pagamento quando a venda está EM_PAGAMENTO.** É o risco
> mais sutil da leva — valide com atenção.

| # | Ação | Resultado esperado |
|---|---|---|
| 4.1 | `[F2]` → nova Venda; bipar `7896005800010` (`[Enter]`). | CAFE na venda; TOTAL **R$ 15,90**. |
| 4.2 | `[F10]` (Finalizar). | Vai para a tela **Pagamento** (seleção 1-Dinheiro / 2-Cartão / 3-PIX); TOTAL A PAGAR R$ 15,90. A venda agora está **EM_PAGAMENTO**. |
| 4.3 | **Sem concluir o pagamento**, tentar voltar à Venda pelo menu lateral → **Venda** (ou `[F2]`). | **PONTO-CHAVE.** O sistema **não** abre venda nova nem me deixa "escapar": ele me **roteia de volta à tela de Pagamento** (seleção 1/2/3), com a mesma venda e o mesmo TOTAL R$ 15,90. A venda EM_PAGAMENTO é preservada. |
| 4.4 | (Confirmação extra) `[ESC]` na tela de Pagamento. | Tenta voltar à Venda, mas de novo **cai no Pagamento** (a venda segue EM_PAGAMENTO até ser concluída). Não perde a venda, não duplica. |
| 4.5 | Concluir de verdade: `[1]` (Dinheiro) → digitar o recebido (ex. `20,00`), `[Enter]`. | Troco **R$ 4,10**; confirma; venda vai a **FINALIZADA**. Só então saio do "laço" do pagamento. |
| 4.6 | Após finalizar, `[F2]`. | Abre venda **nova e vazia** (como no Fluxo 2). |

> **FALHA a reportar** se ao voltar (4.3/4.4): abrir uma **venda nova vazia**, ou
> a venda EM_PAGAMENTO sumir/virar órfã, ou a tela ficar "presa" sem conseguir
> concluir nem cancelar.
> **Parecer de usabilidade a registrar:** é intuitivo ficar "preso" no
> pagamento? Para um caixa, provavelmente sim (evita largar venda pela metade),
> mas confira se há um caminho CLARO para **desistir** do pagamento e voltar a
> mexer na venda (ex.: cancelar o pagamento). Se não houver saída óbvia, anotar
> como ponto de usabilidade para o Coordenador.

---

## FLUXO 5 — Sem caixa aberto (RN14): venda bloqueada

> Precisa do caixa **fechado**. Se já abriu no Fluxo A, feche-o antes (Caixa →
> Fechar) ou reinicie o app sem abrir caixa.

| # | Ação | Resultado esperado |
|---|---|---|
| 5.1 | Com **nenhum caixa aberto**, `[F2]` → tela **Venda**. | A tela avisa **"Abra o caixa antes de vender"** no status; o campo **Código fica desabilitado** (não aceita bipe). |
| 5.2 | Tentar digitar/bipar no campo Código. | Nada entra — campo desabilitado. Nenhuma venda é criada (não gera venda órfã sem caixa). |
| 5.3 | `[F4]`, abrir caixa com `100,00`, voltar `[F2]`. | Agora o campo Código habilita, status limpo, foco no Código; dá pra vender. |

> **FALHA a reportar** se a tela deixar bipar sem caixa, ou se não exibir o aviso.

---

## FLUXO 6 — (Opcional) Prova da venda órfã: só 1 ABERTA no banco

Depois de várias idas-e-voltas sem finalizar (repetir Fluxo 1 passos 1.1–1.7
algumas vezes, saindo pro Estoque e voltando), conferir que **não** acumulou
várias vendas ABERTA soltas. Duas formas:

- **No banco** (o dev executa — é ação de ambiente):
  ```sql
  SELECT status, COUNT(*) FROM sale GROUP BY status ORDER BY status;
  ```
  Esperado: **no máximo 1** linha com `status = ABERTA` (a venda em andamento do
  operador/caixa atual). Antes da correção, cada ida-e-volta deixava uma ABERTA
  órfã a mais.
- **Pelo app:** se houver relatório/histórico que liste vendas por status, não
  deve haver um monte de ABERTA sem itens empilhadas.

> **FALHA a reportar** se houver 2+ vendas ABERTA do mesmo operador/caixa sem
> motivo — é o sintoma da órfã que a correção eliminou.

---

## Checklist de aceite da 2ª leva (resumo para o Coordenador)

- [ ] **F1** Ao sair pro Estoque e voltar, a venda volta **intacta** (itens, TOTAL,
      barra do último item com o nome, foco no Código) e continua somando na mesma
      venda. *(cobre RESSALVA: restauração VISUAL)*
- [ ] **F2** Após finalizar, voltar à Venda abre uma venda **NOVA e vazia**.
- [ ] **F3** Venda **cancelada** (F7 c/ gerente) não volta; abre nova vazia.
- [ ] **F4** Venda **EM_PAGAMENTO**: sair da Venda me **roteia de volta ao
      Pagamento** (não abre nova, não perde a venda). *(cobre RESSALVA: roteamento
      EM_PAGAMENTO)*
- [ ] **F5** Sem caixa aberto: aviso "Abra o caixa antes de vender" + Código
      desabilitado (RN14).
- [ ] **F6** (Opcional) No máx. 1 venda ABERTA no banco após várias idas-e-voltas
      (sem órfãs).

---

## Automação (pyautogui)

**Nesta leva NÃO há automação.** O dev decidiu pular o pyautogui por ora, então
NÃO estou solicitando script ao Senior Backend nem ao Coordenador. Este roteiro é
para **validação manual** feita pelo próprio dev com `mvn javafx:run`, passo a
passo. Se mais tarde o dev quiser automatizar, o modelo da seção "Automação" do
`roteiro_1a_leva.md` serve de base (os Fluxos 1 e 4 seriam os alvos de verificação
por OCR: tabela/TOTAL preenchidos ao voltar, e tela de Pagamento reaparecendo).
