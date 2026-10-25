# Testes E2E (pyautogui) — G1 e G2

> Ferramenta de teste de ponta-a-ponta que **abre o app de verdade e clica/digita
> na tela**. Nao e codigo de producao e **nao** faz parte de `src/`.
> Quem **prepara o ambiente e roda e o DEV** — abrir o app desktop, subir o Docker
> e instalar dependencias sao acoes de ambiente que passam pelo Coordenador -> dev.

## O que esta aqui

| Arquivo | Para que serve |
|---|---|
| `roteiros/G1_senha_supervisor.md` | Roteiro do John (operador) do G1 — a verdade do comportamento esperado. |
| `g1_senha_supervisor.py` | Script pyautogui que automatiza **3 fluxos** do G1. |
| `roteiros/G2_quantidade_peso.md` | Roteiro do John do G2 (quantidade/multiplicador e peso). |
| `g2_quantidade_peso.py` | Script pyautogui que automatiza **4 fluxos** do G2. |
| `seed_g2.sql` | Seed de dev (so PRODUTOS) para o G2 — rodar a mao no psql. |
| `README.md` | Este arquivo: pre-condicoes e como o DEV roda. |

---

# G2 — "Quantidade / multiplicador e peso na venda"

Automatiza os **4 fluxos de alto valor** pedidos pelo roteiro
(`roteiros/G2_quantidade_peso.md`, secao "Especificacao da automacao Python"):

- **Fluxo A (Cenario 1)** — UN 6x: `6*7891234` => TOTAL **R$ 30,00**.
- **Fluxo B (Cenario 2)** — KG 1,250: `1,250*7899` => TOTAL **R$ 7,50**.
- **Fluxo C (Cenario 4)** — somatorio RN07: `6*7891234` depois `7891234` somam na
  **mesma linha** (Qtd 7) => TOTAL **R$ 35,00** (nao duplica).
- **Fluxo D (Cenario 5)** — UN com fracao recusada, venda NAO cai: `2,5*7891234`
  mostra no status **"...nao aceita fracao"** e o TOTAL segue **R$ 0,00**; depois
  `6*7891234` => **R$ 30,00** (o erro nao derrubou a venda).

Cenarios 3, 6 e 7 do roteiro ficam para **validacao manual** (multiplicar erros no
pyautogui so aumenta a fragilidade). Tudo e **digitacao no leitor + Enter**:
**nenhum clique por pixel**.

## Seeds do G2 (o DEV prepara ANTES de rodar)

1. **Operador** `caixa1` / `1234` (OPERADOR) — criado via `UserService`/BCrypt,
   **nunca** SQL cru (§4.3). Reaproveite o `caixa1` ja seedado nas rodadas do G1;
   se nao existir, crie-o pelo caminho da app (mesmo login do G1).
2. **Produtos** via `tests/e2e/seed_g2.sql` (produtos nao tem senha, entao SQL de
   dev e aceitavel; ainda assim os INSERTs sao idempotentes):

   | Codigo | Descricao | Unidade | Preco | Estoque (folgado) |
   |---|---|---|---|---|
   | `7891234` | Refrigerante Lata 350ml | **UN** | R$ 5,00 | 500 un |
   | `7899` | Banana Prata (granel) | **KG** | R$ 6,00/kg | 200,000 kg |

   Rodar o seed a mao, por exemplo:
   ```
   docker exec -i db_scopel psql -U <user> -d <db> < tests/e2e/seed_g2.sql
   ```
   > Se preferir nao usar SQL, crie os dois produtos pela tela de cadastro de
   > produtos da app (ProductService) com os mesmos codigo/preco/unidade/estoque.

3. **Caixa de `caixa1` ABERTO** antes de rodar (abra uma vez pela UI: login caixa1
   -> abrir caixa, troco R$ 100,00). O script assume `CAIXA_JA_ABERTO = True`;
   abrir caixa pela UI a cada run e fragil e fica fora do escopo automatizado.

## Reset entre fluxos (por que F2)

Cada fluxo comeca com a venda **limpa**. O script re-aperta **F2** (menu "Vender"):
`MainController.onVender` re-navega para a tela de venda e o `initialize()` do
`SaleController` **sempre abre uma venda nova e vazia** (`openSale`). Logo, F2 zera
a venda **sem precisar de gerente** — diferente do G1, que resetava cancelando a
venda com supervisor.

## Como o DEV roda o G2

Com app aberto + janela maximizada + seeds + caixa de `caixa1` aberto:
```
python tests/e2e/g2_quantidade_peso.py
```
Mesmas regras do G1: nos 2 primeiros segundos o script espera o foco da janela;
**nao mexa no mouse/teclado** enquanto roda; **FAILSAFE** = mouse no canto
superior-esquerdo aborta; ao final imprime o RESUMO dos 4 fluxos. Qualquer falha
aborta com o passo que quebrou (ex.: `[A total 30,00] ...`) e codigo de saida != 0.

## Nota sobre o separador decimal no TOTAL

O rotulo TOTAL e montado por `"R$ " + BigDecimal`, entao a tela mostra **ponto**
(`R$ 30.00`), enquanto o roteiro fala em virgula (`R$ 30,00`) — e o Tesseract ainda
confunde `.`/`,`. O `g2_quantidade_peso.py` compara **valores monetarios**
unificando ambos os separadores (`_normalizar_valor`), ficando imune a isso. (Nao e
bug do app: e so a forma como o Java imprime o BigDecimal.)

---

# G1 — "Autorizacao por senha de supervisor"

## Por que SO 3 fluxos (e nao o roteiro inteiro)

pyautogui **digita/clica por coordenada e tempo**: muda resolucao, escala, fonte,
tema ou posicao da janela e **quebra**. E fragil de proposito manter so poucos
fluxos de ALTO valor. Os tres cobertos provam o essencial da autorizacao:

- **Fluxo A (Cenario 1)** — cancelar item com senha de gerente CORRETA: item some,
  TOTAL recalcula para R$ 0,00, status "Item cancelado".
- **Fluxo B (Cenario 4)** — operador `caixa2` (nao-gerente) e BARRADO: erro
  "Usuario nao e gerente", diálogo segue aberto; apos Esc, nada foi cancelado.
- **Fluxo C (Cenario 6b)** — desconto acima do teto (R$ 20 sobre subtotal R$ 100) +
  Esc: desconto NAO aplica, TOTAL continua R$ 100,00.

Os demais cenarios do roteiro (errar e acertar a senha, Esc no item, 5% direto,
20% aplicado, cancelar venda, teclado puro) sao validados **a mao** pelo roteiro.
Multiplicar cenarios no pyautogui so aumenta a fragilidade sem ganho proporcional.

O script e **teclado-first** (Tab / Enter / Esc / digitar), que combina com o
roteiro e e menos fragil que clicar por pixel. Nao ha nenhum clique por pixel.

## Pre-condicoes (o DEV prepara e confirma ANTES de rodar)

1. **App no ar** via `mvn javafx:run` (NAO o botao verde do IntelliJ — quebra pelos
   modulos JavaFX).
2. **Docker `db_scopel` de pe**, porta publicada (`-p 5432:5432`), `.env` configurado.
3. **Resolucao fixa e conhecida**: calibrado para **1920x1080, escala 100%**, tema/CSS
   padrao. Outra resolucao -> recalibre as regioes (ver abaixo).
4. **Janela do app MAXIMIZADA** e em posicao previsivel; nenhuma outra janela
   roubando foco; protetor de tela desligado.
5. **Seeds presentes** (criados pelo caminho correto — usuarios via
   `UserService`/BCrypt, **nunca** SQL cru):

   | Papel | Login | Senha | Role |
   |---|---|---|---|
   | Operador | `caixa1` | `1234` | OPERADOR |
   | Gerente | `gerente` | `gerente123` | GERENTE |
   | Operador extra | `caixa2` | `1234` | OPERADOR |

   Produto: `7891000100103` "Arroz Tipo 1 5kg", **R$ 25,00**, estoque **>= 20**.

   > As senhas acima sao **de seed do roteiro**, nao de producao. Nao use senha real.

6. **Caixa de `caixa1` ABERTO** antes de rodar (deixe o banco nesse estado). O
   script assume `CAIXA_JA_ABERTO = True` — abrir caixa pela UI e fragil e varia de
   tela, por isso fica fora do escopo automatizado.

## Dependencias (o DEV instala — este script NAO instala nada)

```
pip install pyautogui
pip install pytesseract pillow
```

Alem do pacote `pytesseract`, e preciso o **engine Tesseract OCR** instalado no SO
(no Windows, por exemplo, o instalador do UB-Mannheim) e no PATH, com o idioma
Portugues (`por`). Sem Tesseract, as verificacoes por OCR abortam com mensagem
clara.

## Verificacao por OCR (como o script "le" a tela, sem tocar no banco)

A conferencia e **100% visual**: o script captura **regioes fixas** da tela e usa
OCR (Tesseract via `pytesseract`) para ler o texto do **TOTAL**, do **status**, do
**titulo do diálogo** e da **linha de erro**, e para checar a **presenca/ausencia
da linha do produto** na tabela. **Nunca** consulta o banco.

As regioes estao no topo do `g1_senha_supervisor.py` como constantes
`REGIAO_*` no formato `(left, top, width, height)`, calibradas para 1920x1080.
**Se mudar a resolucao/escala/tema, recalibre**:

1. Tire um screenshot da tela de venda maximizada.
2. Meca o retangulo que envolve cada elemento (TOTAL, status, tabela, titulo do
   diálogo, linha de erro).
3. Ajuste as constantes `REGIAO_*`. Use `python -c "import pyautogui;
   print(pyautogui.position())"` para descobrir coordenadas sob o cursor.

Alternativa ao OCR (se preferir): casar por **imagem de referencia** com
`pyautogui.locateOnScreen(...)` usando capturas do TOTAL "R$ 0,00" vs "R$ 75,00".
Tambem depende da resolucao travada — por isso fragil de qualquer jeito.

## Como o DEV roda

Com tudo acima pronto (app aberto, janela maximizada, caixa de `caixa1` aberto):

```
python tests/e2e/g1_senha_supervisor.py
```

- Nos **2 primeiros segundos** o script espera para a janela do app ganhar foco.
  Deixe o app em primeiro plano e **nao mexa no mouse/teclado** enquanto roda.
- **FAILSAFE**: jogue o mouse para o **canto superior-esquerdo** da tela a qualquer
  momento para ABORTAR a automacao (escape de emergencia).
- Ao final o script imprime um **RESUMO** dizendo quais fluxos passaram. Qualquer
  falha **aborta com mensagem clara** indicando o passo que quebrou (ex.:
  `[A5 total 0,00] ...`) e o codigo de saida e != 0.

## Pontos mais frageis (onde ajustar primeiro se quebrar)

- **Contadores de Tab** (`TABS_*`): seguem a ordem dos nos do `sale.fxml`
  (`codeField -> itemsTable -> discountField -> ...`). Se a `TableView` capturar o
  foco de forma diferente na maquina do dev, a contagem muda — ajuste os `TABS_*`.
- **Regioes de OCR** (`REGIAO_*`): dependem de resolucao/escala/tema.
- **Esperas** (`ESPERA_*`, `TIMEOUT_*`): se a maquina for mais lenta, aumente.
- **Atalho da tela de venda** (`ATALHO_TELA_VENDA`, hoje `f2`): confira no menu.

> Lembrete do parecer do John: os rotulos "(F5)/(F6)/(F7)/(F10)" na tela de venda
> **nao funcionam** como atalho (F5-F8 estao presos pela barra principal). Por isso
> o script navega por **Tab/Enter/Esc**, nunca por esses F-keys.
