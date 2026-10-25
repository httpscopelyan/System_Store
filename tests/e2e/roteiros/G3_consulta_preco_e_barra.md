# Roteiro E2E — G3: Consulta de preço rápida (F1) + barra "Último item"

> Parecer de usabilidade real escrito pelo John (operador de caixa).
> Duas funcionalidades de UI, já aprovadas no QA (139 testes verdes):
>
> **Entrega 1 — Consulta de preço rápida (F1):** uma tela dedicada
> (`price_check.fxml`) que o caixa abre com **F1** de qualquer lugar para
> consultar o preço de um produto **sem vender nada** (só leitura). Campo de
> código no topo (foco inicial), busca por nome opcional, resultado grande:
> descrição + **PREÇO grande** + unidade, e o aviso **"SÓ CONSULTA — NÃO É
> VENDA"**. **RN21:** F1 só abre se NÃO houver venda em andamento.
>
> **Entrega 2 — Barra "ÚLTIMO ITEM":** um visor grande na tela de venda, entre o
> campo de código e a tabela, mostrando **nome + preço unitário** do produto
> recém-bipado, para o caixa confirmar de relance o que acabou de passar.
>
> Requisitos cobertos: consulta de preço (fluxo comum de PDV de mercado), RN21
> (máquina de estados — ação só vale em certos estados), RN19 (preço do produto),
> só-leitura (não abre venda / não baixa estoque). Teclado-first: RNF03. Total/
> preço legível de longe: RNF04.

---

## Como funciona na prática (o que o caixa vê)

### Consulta de preço (F1)
- **F1 abre a tela de consulta** (`price_check.fxml`) de qualquer tela do sistema,
  **desde que não haja venda em andamento** (ver RN21 abaixo).
- Ao abrir, o cursor **já pisca no campo de código** no topo (não precisa clicar,
  RNF03). É o mesmo gesto do leitor: **bipar/digitar o código + Enter**.
- O resultado vem **grande**: linha de **descrição**, o **PREÇO em destaque** (o
  elemento mais proeminente da tela, RNF04) e a **unidade** (UN ou KG — no caso do
  KG o preço é por quilo). No alto fica fixo o aviso **"SÓ CONSULTA — NÃO É
  VENDA"**.
- Tem uma **busca por nome opcional** (campo separado), para quando o produto não
  tem código à mão ou a etiqueta não bipa.
- **Produto inexistente → "Produto não encontrado"** (mensagem em português, sem
  travar a tela; o campo é limpo e o cursor volta pra ele pra tentar de novo).
- **Esc** (ou o botão **"Voltar"**) volta para a tela anterior.
- É **SÓ LEITURA**: a consulta **não abre venda, não adiciona item e não baixa
  estoque**. Nada do que o caixa digita aqui vira venda.

### Barra "ÚLTIMO ITEM" (tela de venda)
- Fica **entre o campo de código e a tabela** de itens. Mostra, grande, o **nome +
  preço unitário** do produto **recém-bipado**.
- **Atualiza a cada item adicionado** (o último que entrou).
- **Estado inicial / venda sem itens → placeholder "—"**.
- Ao **cancelar um item**, a barra passa a refletir o **último item restante** (ou
  **"—"** se a venda esvaziou).
- Ao **cancelar a venda / abrir nova venda**, a barra **limpa** (volta a "—").
- **Não recebe foco**: o foco segue sempre no campo de código (RNF03). A barra é só
  um visor de confirmação.
- O **TOTAL** continua sendo o elemento **mais proeminente** da tela de venda — a
  barra do último item é destaque secundário, não compete com o total.

---

## Pré-condições comuns (valem para todos os cenários)

Dados de seed que precisam existir **antes** de rodar (produtos criados pelo
caminho correto — `ProductService`, nunca SQL cru). **Reaproveita os seeds do
G2:**

| Código | Descrição | Unidade | Preço | Estoque |
|---|---|---|---|---|
| `7891234` | Refrigerante Lata 350ml | **UN** | R$ 5,00 | ≥ 50 un |
| `7899` | Banana Prata (granel) | **KG** | R$ 6,00 / kg | ≥ 20,000 kg |

Usuário operador de seed (criado via `UserService`/BCrypt):

| Papel | Login | Senha |
|---|---|---|
| Operador | `caixa1` | `1234` |

Estado inicial de cada cenário (salvo indicação): **operador `caixa1` logado**,
**caixa aberto** (troco inicial R$ 100,00). Para os cenários de consulta começo na
**tela inicial (menu)** ou na tela de venda; para os da barra começo **na tela de
Venda** (menu F2 / botão Vender), com o cursor piscando no campo "Código de
barras".

> Por que esses produtos? Já são os do G2 e fazem a conta fechar redonda:
> UN a R$ 5,00 e KG a R$ 6,00/kg. Deixa a consulta objetiva (o caixa sabe de
> cabeça o preço esperado) e a barra fácil de conferir de longe.

---

# PARTE A — Consulta de preço rápida (F1)

## Cenário A1 — F1 abre a consulta e mostra o preço de um código existente (UN)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Estou na tela inicial (menu), **sem venda aberta**. Aperto **F1** | Abre a tela de consulta de preço. O cursor **já pisca no campo de código** no topo. Fixo no alto: aviso **"SÓ CONSULTA — NÃO É VENDA"**. Nenhum produto mostrado ainda. |
| 2 | Bipo/digito `7891234` e aperto **Enter** | Aparece grande: descrição **"Refrigerante Lata 350ml"**, **PREÇO R$ 5,00** em destaque, unidade **UN**. O campo de código é limpo e o cursor volta pra ele. |
| 3 | Confiro que não virou venda | **Nenhuma** venda foi aberta, **nenhum** item adicionado, estoque **não** mudou. É só leitura. |

## Cenário A2 — Consultar um produto por KG (preço por quilo)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Na tela de consulta, digito `7899` e Enter | Aparece **"Banana Prata (granel)"**, **PREÇO R$ 6,00**, unidade **KG** (preço por quilo). Campo limpo, cursor de volta. |
| 2 | Confiro a unidade | Fica claro na tela que é **por kg** (o caixa não confunde com preço unitário fechado). Nada entrou em venda. |

## Cenário A3 — Código inexistente mostra "Produto não encontrado" (sem travar)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Na tela de consulta, digito `0000000` e Enter | Aparece a mensagem em português **"Produto não encontrado"**. A tela **não trava**: o campo de código é limpo e o cursor volta pra ele. |
| 2 | Logo em seguida digito `7891234` e Enter | A consulta **segue viva**: mostra normalmente o Refrigerante a R$ 5,00. O erro anterior não atrapalhou. |

## Cenário A4 — Busca por nome (quando não dá pra bipar o código)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Na tela de consulta, vou ao campo de **busca por nome** e digito `Refrigerante` (ou parte, ex.: `Refri`), aperto **Enter** | Encontra e mostra **"Refrigerante Lata 350ml"**, **R$ 5,00**, UN — do mesmo jeito grande da busca por código. |
| 2 | Busco por nome um texto que não existe, ex.: `Chocolate`, Enter | Mostra **"Produto não encontrado"**, sem travar. |

> Observação de caixa: a busca por nome salva quando a etiqueta rasgou e o código
> de barras não bipa — dá pra achar o produto pelo nome e dizer o preço pro cliente
> na hora.

## Cenário A5 — Esc (ou "Voltar") volta à tela anterior

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Entro na consulta pela tela inicial (F1), consulto `7891234` | Vejo o preço. |
| 2 | Aperto **Esc** | A consulta fecha e **volto para a tela anterior** (a inicial). Nada de venda foi criado. |
| 3 | Repito, mas no lugar do Esc aciono o botão **"Voltar"** | Mesmo efeito: volto à tela anterior. |

---

## RN21 — F1 só abre a consulta quando NÃO há venda em andamento

> O PDV é uma máquina de estados: a consulta de preço não pode "sair de cima" de
> uma venda já em montagem. A validação é na UI **e** no service.

## Cenário A6 — F1 BLOQUEADO com venda que já tem itens (conferir a dica)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Entro na tela de **Venda** e bipo `7891234` (Enter) — a venda agora tem 1 item, TOTAL **R$ 5,00** | Item na tabela; barra "ÚLTIMO ITEM" mostra o Refrigerante (ver Parte B). |
| 2 | Aperto **F1** | **NÃO navega** para a consulta — a tela de venda **permanece**. Na **barra de status** aparece a dica **"Finalize ou cancele a venda para consultar preço (F1)"**, por **~4 segundos**, e depois some sozinha. |
| 3 | Confiro a venda | A venda segue **intacta** (1 item, TOTAL R$ 5,00). F1 não cancelou nem alterou nada; só recusou abrir a consulta. |

## Cenário A7 — F1 BLOQUEADO com venda em pagamento

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Com a venda montada (1+ itens), sigo para o pagamento (a venda entra em **EM_PAGAMENTO**) | Estou na tela/fluxo de pagamento. |
| 2 | Aperto **F1** | Igual ao A6: **não abre** a consulta; aparece a dica **"Finalize ou cancele a venda para consultar preço (F1)"** na barra de status por ~4s. A venda em pagamento não é afetada. |

## Cenário A8 — F1 LIBERADO sem venda ou com venda aberta vazia

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Estou na tela inicial, **sem nenhuma venda** criada. Aperto **F1** | Abre a consulta **normalmente** (cursor no código, aviso "SÓ CONSULTA"). |
| 2 | Volto (Esc), entro na tela de **Venda** mas **não bipo nada** (venda aberta e **vazia**, TOTAL R$ 0,00). Aperto **F1** | Abre a consulta **normalmente** — venda vazia não bloqueia. **Nenhuma** dica de "finalize ou cancele" aparece. |
| 3 | Consulto `7891234`, aperto **Esc** | Vejo o preço, volto à venda vazia, que segue pronta para bipar o primeiro item. |

> O que importa aqui pro caixa: posso consultar preço à vontade **antes** de
> começar a bipar ou com a venda ainda zerada. Depois que o primeiro item entrou,
> o sistema me pede pra finalizar ou cancelar primeiro — e **avisa** o porquê, em
> vez de só não fazer nada.

---

# PARTE B — Barra "ÚLTIMO ITEM" (tela de venda)

> Estado inicial destes cenários: **na tela de Venda**, venda vazia, cursor no
> campo "Código de barras", barra "ÚLTIMO ITEM" mostrando o placeholder **"—"**.

## Cenário B1 — Bipar item mostra nome + preço na barra; bipar outro atualiza

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Entro na venda | Barra "ÚLTIMO ITEM" mostra **"—"** (placeholder). TOTAL **R$ 0,00**. |
| 2 | Bipo `7891234` (Enter) | Entra o Refrigerante na tabela. A barra passa a mostrar grande **"Refrigerante Lata 350ml — R$ 5,00"** (nome + preço unitário). TOTAL **R$ 5,00**. |
| 3 | Bipo `7899` (Enter) | Entra a Banana. A barra **atualiza** para **"Banana Prata (granel) — R$ 6,00"** (o último que entrou). TOTAL sobe para **R$ 11,00**. |
| 4 | Bipo `7891234` de novo (Enter) | RN07: soma na linha do Refrigerante (Qtd 2). A barra volta a mostrar **"Refrigerante Lata 350ml — R$ 5,00"** (foi o último bipado). TOTAL **R$ 16,00**. |

## Cenário B2 — Cancelar item reflete o último restante (ou "—" se esvaziou)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Com Refrigerante + Banana na venda (barra mostrando "Banana... R$ 6,00"), cancelo a **Banana** (via "Código p/ cancelar item" + gerente, como no G1) | A Banana sai da tabela. A barra passa a refletir o **último item restante**: **"Refrigerante Lata 350ml — R$ 5,00"**. TOTAL cai para o do Refrigerante. |
| 2 | Cancelo também o **Refrigerante** (venda fica vazia) | A barra volta ao placeholder **"—"** (não sobrou item). TOTAL **R$ 0,00**. |

> Ponto de caixa: a barra sempre diz "o que está valendo agora". Quando cancelo o
> último que passei, ela não pode continuar mostrando um item que já saiu.

## Cenário B3 — Cancelar venda / nova venda limpa a barra

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Bipo 2 ou 3 itens (barra mostrando o último) | Barra com o último item; TOTAL somado. |
| 2 | Cancelo a **venda inteira** (com gerente, G1) | A tabela esvazia, **abre uma nova venda**, e a barra **limpa** para **"—"**. TOTAL **R$ 0,00**. |
| 3 | Bipo o primeiro item da nova venda | A barra volta a mostrar normalmente o item recém-bipado. |

## Cenário B4 — A barra NÃO rouba o foco e o TOTAL continua dominante

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Bipo vários itens em sequência, **sem tocar no mouse** | Depois de **cada** Enter o cursor **continua no campo "Código de barras"** — a barra do último item **nunca** recebe foco; emendo a próxima bipada direto (RNF03). |
| 2 | Olho a tela de longe (a distância de um balcão) | O **TOTAL** é o maior/mais proeminente elemento da tela; a barra do último item é destaque **secundário** — ajuda a conferir o item, mas não compete nem "engole" o total. |

---

## Parecer de usabilidade real (John, operador)

1. **F1 para consulta de preço é exatamente o que falta num PDV de mercado.** O
   caixa é parado o tempo todo com "quanto é isso?". Ter uma tela dedicada, que
   abre com uma tecla, bipou → preço grande, e **não vira venda**, é o
   comportamento certo. O aviso "SÓ CONSULTA — NÃO É VENDA" evita o erro clássico
   de achar que o produto entrou na conta. Aprovado.
2. **A trava do RN21 com a dica explicando o porquê está ótima.** O pior seria
   apertar F1 no meio de uma venda e **não acontecer nada** (o caixa fica achando
   que o teclado falhou). A dica **"Finalize ou cancele a venda para consultar
   preço (F1)"** por ~4s resolve: diz o que fazer. Só registro para confirmar na
   validação manual que 4s é tempo suficiente para ler sem sumir cedo demais.
3. **Liberar F1 com a venda vazia é a escolha certa.** O caixa costuma consultar
   preço **antes** de começar a bipar; bloquear só depois do primeiro item bate
   com o uso real.
4. **Busca por nome salva o dia quando a etiqueta não bipa.** Bom que exista.
   **Sugestão (não bloqueia):** confirmar na tela se, quando o nome casar com
   **vários** produtos, a consulta deixa claro qual está mostrando (ou lista) — no
   mercado tem muito "Refrigerante" de marca/sabor diferente. Para os seeds atuais
   (um só "Refrigerante") não dá pra ver isso; levar ao Coordenador como ponto a
   validar com catálogo real.
5. **A barra "ÚLTIMO ITEM" dá segurança na bipagem.** Confirmar de relance "passou
   o Refrigerante a R$ 5,00" sem ter que caçar a última linha da tabela é um ganho
   real, ainda mais com fila. Atualizar no cancelamento (mostrar o que sobrou) e
   limpar na nova venda é o comportamento coerente.
6. **Foco e proeminência corretos.** A barra **não** rouba o foco (segue no
   código) e **não** compete com o TOTAL — exatamente o que um caixa precisa:
   total manda, item é conferência. **Ponto a confirmar na tela (RNF04):** ver de
   longe se a barra, por ser grande, não acaba disputando atenção com o total;
   ajuste de tamanho/cor se necessário (não bug).
7. **Consistência de preço por KG.** Na consulta, o produto KG mostra "R$ 6,00"
   **por kg** — confirmar na tela que fica evidente que é por quilo (e não um
   preço fechado), igual ao cuidado que já levantei no G2 com o "1,000 kg".

Status sugerido do G3: **funcional e aprovado no comportamento** (F1 abre/bloqueia
por RN21 com dica, consulta por código e por nome, "não encontrado" sem travar,
só-leitura, barra atualiza/limpa e não rouba foco, total dominante); pendência de
**validação manual na tela** (`mvn javafx:run`, JDK 26) e os pontos de UX 2/4/6/7 a
levar ao Coordenador.

---

## Especificação da automação Python (pyautogui) — PARA O DEV AUTORIZAR DEPOIS

> **Não é script. É o pedido.** O John **não escreve nem roda** isto. O
> Coordenador leva ao dev para o Senior Backend construir, e só roda com
> autorização do dev (abrir o app desktop é ação de ambiente).
>
> **Pré-requisito de ambiente AINDA PENDENTE:** esta automação **reutiliza a mesma
> infra de OCR dos scripts do G1/G2** (Tesseract OCR em regiões fixas de tela para
> ler TOTAL, status e os rótulos grandes). Portanto depende do **mesmo pré-
> requisito do dev: Tesseract OCR instalado e calibrado**, que **continua
> pendente**. Enquanto o Tesseract não estiver instalado/validado pelo dev, o G3
> (assim como G1/G2) **só roda à mão** pelo roteiro acima.

### Natureza e limites
- pyautogui **digita/clica por coordenada e tempo** — é **frágil**: muda
  resolução, escala, fonte, tema ou posição da janela e quebra. Cobrir **poucos
  fluxos de alto valor**, não o roteiro inteiro.
- Cobrir: **Cenário A1 (F1 abre + consulta código → preço)** + **Cenário A3
  (inexistente → "Produto não encontrado", sem travar)** + **Cenário A6 (RN21: F1
  bloqueado com venda em itens, conferir a dica)** + **Cenário B1 (barra mostra e
  atualiza o último item)**. Esses quatro provam o essencial: F1 abre e lê o
  preço, erro não trava, a trava de estado com a dica funciona, e a barra atualiza.
- Tudo é **teclado-first** (F1, digitar no campo + Enter, Esc) — combina com o
  roteiro e é bem menos frágil que clicar por pixel. **Nenhum clique por pixel.**

### Pré-condições de ambiente (o dev prepara e confirma)
- **Tesseract OCR instalado e no PATH** (pré-requisito compartilhado G1/G2/G3 —
  **pendente**). Sem ele, o script não consegue verificar nada e não deve rodar.
- App rodando via `mvn javafx:run` (NÃO o botão verde do IntelliJ).
- Docker `db_scopel` de pé com porta publicada; `.env` configurado.
- **Resolução de tela conhecida e fixa** (ex.: 1920×1080, escala 100%), janela do
  app **maximizada** e em posição previsível; tema/CSS padrão. **Reaproveitar as
  regiões de OCR do `g1_senha_supervisor.py` / `g2_quantidade_peso.py`** para
  TOTAL e status (mesmos rótulos da tela de venda); calibrar **duas regiões novas**
  do G3: o **PREÇO grande** e a linha de **descrição** da tela de consulta, e a
  **barra "ÚLTIMO ITEM"** da tela de venda.
- Nenhuma outra janela roubando foco; protetor de tela desligado.
- **Operador `caixa1` logado e caixa ABERTO** antes de rodar (deixar o banco nesse
  estado — abrir caixa pela UI é frágil, fica fora do escopo, igual ao G1/G2 com
  `CAIXA_JA_ABERTO = True`).
- Seeds presentes (ver abaixo), produtos criados via `ProductService`.

### Dados de seed necessários
- Usuário **operador** `caixa1` / `1234` (OPERADOR).
- Produto **UN** `7891234` "Refrigerante Lata 350ml", preço **R$ 5,00**, estoque
  **≥ 50**.
- Produto **KG** `7899` "Banana Prata (granel)", preço **R$ 6,00/kg**, estoque
  **≥ 20,000 kg**.

### O que o script deve exercitar e VERIFICAR, passo a passo

**Fluxo A — F1 abre a consulta e lê o preço (Cenário A1):** *(estado inicial: tela
inicial/menu, SEM venda em andamento)*
1. Apertar **F1**. *Verificar:* a tela de consulta está visível (ler por OCR o
   aviso **"SÓ CONSULTA — NÃO É VENDA"** numa região fixa do topo).
2. Digitar `7891234` e Enter. *Verificar (os dois juntos):* a região do **PREÇO
   grande** lê **"R$ 5,00"** e a descrição lê **"Refrigerante Lata 350ml"**.
3. Apertar **Esc**. *Verificar:* voltou à tela anterior (o aviso "SÓ CONSULTA"
   sumiu da tela).

**Fluxo B — inexistente não trava (Cenário A3):** *(na tela de consulta)*
1. Abrir a consulta (F1). Digitar `0000000` e Enter. *Verificar:* a tela mostra
   **"Produto não encontrado"** (OCR da região de mensagem).
2. Digitar `7891234` e Enter. *Verificar:* a consulta seguiu viva — a região do
   PREÇO agora lê **"R$ 5,00"** (o erro anterior não travou).

**Fluxo C — RN21: F1 bloqueado com venda em itens (Cenário A6):** *(ir para a tela
de Venda com venda vazia)*
1. Ir para Venda (menu/F2). Digitar `7891234` e Enter. *Verificar:* o TOTAL lê
   **"R$ 5,00"** (há item na venda).
2. Apertar **F1**. *Verificar (os dois juntos):* (a) a consulta **NÃO** abriu — o
   aviso "SÓ CONSULTA" **não** aparece e o TOTAL "R$ 5,00" **continua** visível
   (ainda na tela de venda); (b) a **barra de status** lê **"Finalize ou cancele a
   venda para consultar preço (F1)"**.
   - *Nota de timing:* a dica some em ~4s; o script deve capturar/ler o status
     **logo após** o F1, dentro dessa janela, com espera curta antes da leitura.

**Fluxo D — barra "ÚLTIMO ITEM" atualiza (Cenário B1):** *(venda nova/limpa)*
1. Entrar na venda vazia. *Verificar:* a barra lê o placeholder **"—"**.
2. Digitar `7891234` e Enter. *Verificar:* a barra lê **"Refrigerante Lata 350ml —
   R$ 5,00"** e o TOTAL lê **"R$ 5,00"**.
3. Digitar `7899` e Enter. *Verificar:* a barra **mudou** para **"Banana Prata
   (granel) — R$ 6,00"** e o TOTAL lê **"R$ 11,00"**.

### Como verificar sem ler o banco
- Conferência **100% visual/pela tela**: OCR do **PREÇO grande** e da **descrição**
  (consulta), do **TOTAL**, do **status** e da **barra "ÚLTIMO ITEM"** (venda).
  **Nunca** consulta o banco.
- Reaproveitar a técnica e as regiões dos scripts do G1/G2 (OCR Tesseract em
  regiões fixas). Tudo depende da resolução travada **e do Tesseract instalado** —
  por isso frágil e por isso bloqueado até o dev preparar o ambiente.
- Entre cada passo, **esperas explícitas** (o app demora a responder), com atenção
  especial ao **Fluxo C** (ler o status dentro da janela de ~4s da dica). Falha de
  leitura = abortar com mensagem clara dizendo qual passo/fluxo quebrou (ex.:
  `[A preço R$ 5,00] ...`), código de saída ≠ 0.
- **Reset entre fluxos:** cada fluxo da venda começa com a venda **limpa**; como no
  G2, combinar com o dev a forma de zerar (reentrar na tela de venda pelo menu, que
  no `initialize` abre uma venda vazia) — ou rodar um fluxo por execução.

### Fora de escopo da automação (fazer manual pelo roteiro)
- Cenários A2 (KG), A4 (busca por nome), A5 (Esc/Voltar detalhado), A7 (F1 em
  pagamento), A8 (F1 liberado vazio), B2 (cancelar item reflete restante), B3
  (cancelar venda limpa a barra) e B4 (foco/proeminência) — validados **à mão**.
  B2/B3 dependem do diálogo de gerente (G1) e multiplicam a fragilidade; A4/A7/A8
  são variações de estado melhores conferidas pela pessoa. A automação cobre o
  núcleo: F1 abre e lê preço, erro não trava, RN21 bloqueia com a dica, e a barra
  atualiza.
