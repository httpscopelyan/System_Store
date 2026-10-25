# Roteiro E2E — G1: Autorização por senha de supervisor

> Parecer de usabilidade real escrito pelo John (operador de caixa).
> Funcionalidade: ao tentar uma ação sensível na tela de venda, abre um diálogo
> modal pedindo LOGIN + SENHA de um gerente. O operador **continua logado** — o
> gerente só autoriza aquela ação, não troca de usuário.
>
> Requisitos cobertos: RF18 (cancelar item), RF21 (cancelar venda),
> RF20/RN08 (desconto acima do teto de 10%). Teclado-first: RNF03. Total
> legível: RNF04.

---

## Como funciona na prática (o que o caixa vê)

- O **diálogo de gerente** (`manager_auth.fxml`) abre no centro da tela, por cima
  de tudo (modal). Tem: campo **Login**, campo **Senha**, uma linha de erro em
  vermelho (vazia no começo), e dois botões: **"Cancelar (Esc)"** e
  **"Autorizar (Enter)"**.
- Ao abrir, o cursor **já pisca no campo Login** (não precisa clicar).
- **Enter** = autoriza (funciona tanto digitando a senha e apertando Enter quanto
  pelo botão padrão "Autorizar").
- **Esc** = cancela e fecha o diálogo sem aplicar nada.
- Se a credencial estiver errada, aparece a mensagem em vermelho **e o diálogo
  NÃO fecha** — a senha é limpa e o cursor volta pro campo Senha pra tentar de
  novo. Mensagens possíveis: "Senha incorreta", "Usuário não é gerente",
  "Usuário não encontrado".

### Campos da tela de venda usados nestes cenários
- **Código de barras** (`codeField`): bipa/digita o código + Enter → adiciona item.
- **Desconto R$** (`discountField`): valor do desconto **em reais** (NÃO é %).
- **Código p/ cancelar item** (`cancelItemCodeField`): digita o código do produto
  que quer tirar da venda, depois aciona "Cancelar item".
- **Motivo do cancelamento** (`cancelReasonField`): texto livre, usado ao
  "Cancelar venda".
- **TOTAL** grande no canto (RNF04) e a **Prévia do cupom** (só leitura, avisa
  "NÃO É DOCUMENTO FISCAL").

> **ATENÇÃO (achado de usabilidade, ver parecer no fim):** os rótulos "(F5)",
> "(F6)", "(F7)", "(F9)" ao lado dos campos de desconto/cancelamento **não
> funcionam** como atalho na tela de venda — F5/F6/F7/F8 estão presos pela barra
> principal para navegar (Compras/Clientes/Fornecedores/Relatórios) e F10
> (Finalizar) é só rótulo, não tem atalho. Por isso o roteiro navega com
> **Tab / Shift+Tab** entre campos e botões e **Enter/Espaço** para acionar botão.

---

## Pré-condições comuns (valem para todos os cenários)

Dados de seed que precisam existir **antes** de rodar (criados pelo caminho
correto — usuários via `UserService`/BCrypt, nunca SQL cru):

| Papel | Login | Senha | Observação |
|---|---|---|---|
| Operador | `caixa1` | `1234` | role OPERADOR |
| Gerente | `gerente` | `gerente123` | role GERENTE |
| Operador extra | `caixa2` | `1234` | role OPERADOR — usado no cenário "não é gerente" |

Produto de seed para bipar:

| Código | Descrição | Preço | Estoque |
|---|---|---|---|
| `7891000100103` | Arroz Tipo 1 5kg | R$ 25,00 | ≥ 20 |

Estado inicial de cada cenário (salvo indicação): **operador `caixa1` logado**,
**caixa aberto** (troco inicial R$ 100,00) e **na tela de Venda** (menu F2 /
botão Vender).

> Por que o preço R$ 25,00? Facilita conferir o teto: bipando **4 unidades** o
> subtotal fica **R$ 100,00**, então o teto de 10% é **R$ 10,00** — desconto de
> R$ 5,00 está dentro, R$ 20,00 está acima. Isso deixa os cenários de desconto
> objetivos.

---

## Cenário 1 — Cancelar item com senha de gerente CORRETA (caminho feliz)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Bipo o Arroz (`7891000100103`) 3 vezes (Enter a cada bipada) | 3 itens somam na mesma linha "Arroz Tipo 1 5kg", Qtd 3, TOTAL **R$ 75,00**. Status: "Item adicionado" / cupom atualizado. |
| 2 | Dou **Tab** até o campo "Código p/ cancelar item" e digito `7891000100103` | O código fica no campo; nada mudou na tabela ainda. |
| 3 | **Tab** até o botão "Cancelar item" e aperto **Enter/Espaço** | Abre o diálogo de gerente, cursor piscando no campo Login, linha de erro vazia. A venda por trás fica intocada. |
| 4 | Digito login `gerente`, **Tab**, digito senha `gerente123` | Os campos aceitam o texto; a senha aparece como bolinhas. |
| 5 | Aperto **Enter** | Diálogo fecha. O item Arroz **some da tabela**, TOTAL volta para **R$ 0,00**, campo de cancelar item fica limpo. Status: **"Item cancelado"**. |
| 6 | Confiro o TOTAL e a prévia do cupom | TOTAL legível de longe mostrando R$ 0,00; cupom sem o item. O operador segue sendo `caixa1` (não trocou de usuário). |

---

## Cenário 2 — Cancelar item e ERRAR a senha no diálogo

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Bipo o Arroz 2 vezes | Linha Arroz, Qtd 2, TOTAL **R$ 50,00**. |
| 2 | Tab até "Código p/ cancelar item", digito `7891000100103`, Tab até "Cancelar item", Enter | Abre o diálogo de gerente (foco no Login). |
| 3 | Login `gerente`, senha **errada** `9999`, aperto **Enter** | Aparece em vermelho **"Senha incorreta"**. O diálogo **continua aberto**, a senha é apagada e o cursor volta pro campo Senha. |
| 4 | Digito a senha certa `gerente123` e Enter | Diálogo fecha, item some, TOTAL **R$ 0,00**, status "Item cancelado". |
| 5 | (Verificação do "erro não aplica nada") Antes do passo 4, o item seguia na tabela com Qtd 2 e TOTAL R$ 50,00 | Confirmado: enquanto a senha estava errada, **nada foi cancelado**. |

---

## Cenário 3 — Cancelar item e apertar Esc (desistir)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Bipo o Arroz 2 vezes | Linha Arroz, Qtd 2, TOTAL **R$ 50,00**. |
| 2 | Tab até "Código p/ cancelar item", digito `7891000100103`, Tab até "Cancelar item", Enter | Abre o diálogo de gerente. |
| 3 | Aperto **Esc** (ou o botão "Cancelar (Esc)") | Diálogo fecha **sem aplicar nada**. |
| 4 | Olho a tabela e o total | Item Arroz **continua** na tabela, Qtd 2, TOTAL **R$ 50,00** intacto. Status: "Cancelamento de item não autorizado" (apenas aviso; a venda está igual). |

---

## Cenário 4 — Tentar autorizar com um usuário OPERADOR (não-gerente)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Bipo o Arroz 1 vez | Linha Arroz, Qtd 1, TOTAL **R$ 25,00**. |
| 2 | Tab até "Código p/ cancelar item", digito `7891000100103`, Tab até "Cancelar item", Enter | Abre o diálogo de gerente. |
| 3 | Digito login `caixa2` (operador) e senha `1234`, Enter | Aparece em vermelho **"Usuário não é gerente"**. Diálogo **continua aberto**, senha limpa, cursor no campo Senha. |
| 4 | Aperto **Esc** para desistir | Diálogo fecha; item Arroz **continua** na venda, TOTAL **R$ 25,00**. Nada foi cancelado por um operador. |

> Observação de caixa: é exatamente o que a gente espera — um colega operador NÃO
> consegue liberar o cancelamento; precisa chamar o gerente de verdade.

---

## Cenário 5 — Desconto de 5% (DENTRO do teto) aplica direto, sem pedir gerente

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Bipo o Arroz 4 vezes | Linha Arroz, Qtd 4, TOTAL/subtotal **R$ 100,00**. |
| 2 | Tab até "Desconto R$", digito `5` (= R$ 5,00, que é 5% de R$ 100,00) | Valor no campo. |
| 3 | Tab até "Aplicar desconto", Enter | **Não abre** diálogo de gerente. Desconto aplicado na hora. Status: **"Desconto aplicado"**. TOTAL cai para **R$ 95,00**. |
| 4 | Confiro o cupom | Prévia mostra o desconto e o novo total R$ 95,00. |

---

## Cenário 6 — Desconto de 20% (ACIMA do teto): abre o diálogo

### 6a — com gerente correto, aplica
| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Bipo o Arroz 4 vezes | Subtotal **R$ 100,00**. |
| 2 | Tab até "Desconto R$", digito `20` (= R$ 20,00 = 20% > teto de 10%) | Valor no campo. |
| 3 | Tab até "Aplicar desconto", Enter | **Abre** o diálogo de gerente (porque passou dos 10%). |
| 4 | Login `gerente`, senha `gerente123`, Enter | Diálogo fecha. Status "Desconto aplicado". TOTAL cai para **R$ 80,00**. |

### 6b — cancelado (Esc), NÃO aplica
| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Bipo o Arroz 4 vezes | Subtotal **R$ 100,00**. |
| 2 | Desconto `20`, aciono "Aplicar desconto" | Abre o diálogo de gerente. |
| 3 | Aperto **Esc** | Diálogo fecha. Status: "Desconto acima do limite: não autorizado". TOTAL **continua R$ 100,00** — desconto **não** foi aplicado. |

---

## Cenário 7 — Cancelar a venda inteira com gerente

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Bipo o Arroz 3 vezes | Linha Arroz, Qtd 3, TOTAL **R$ 75,00**. |
| 2 | Tab até "Motivo do cancelamento", digito `cliente desistiu` | Motivo no campo. |
| 3 | Tab até "Cancelar venda", Enter | Abre o diálogo de gerente. |
| 4 | Login `gerente`, senha `gerente123`, Enter | Diálogo fecha. Status **"Venda cancelada"**. A tabela esvazia, TOTAL volta a **R$ 0,00** e **já abre uma nova venda** pronta pra bipar o próximo cliente, com o cursor no código de barras. |
| 5 | (Legal/auditoria) A venda cancelada **não some do sistema** | A venda fica registrada como CANCELADA com o motivo (RN17) — não é apagada. Conferir no Histórico/relatório, não na tela de venda. |

> Variações que valem testar depois (mesma lógica do item):
> - Cancelar venda e **errar a senha** → "Senha incorreta", diálogo aberto, venda segue.
> - Cancelar venda e **Esc** → status "Cancelamento de venda não autorizado", venda intacta.

---

## Cenário 8 — Tudo só pelo teclado (RNF03)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Entro na venda e faço TODO o cenário 1 (cancelar item com gerente) **sem encostar no mouse**: Enter pra bipar, Tab/Shift+Tab pra andar entre campos e botões, Enter/Espaço pra acionar botão, dentro do diálogo Login→Tab→Senha→Enter | Consigo concluir do começo ao fim no teclado. O item é cancelado. |
| 2 | Refaço e no diálogo uso **Esc** | O diálogo fecha no Esc; a venda segue intacta. |

> **Achado:** o fluxo é 100% possível só no teclado, **mas** depende de Tab, não
> dos atalhos F que estão escritos na tela (ver parecer). Isso cansa e confunde
> num caixa com fila. Ver parecer.

---

## Parecer de usabilidade real (John, operador)

1. **Atalhos F prometidos e não cumpridos (prioridade ALTA de UX).** Os campos da
   tela de venda mostram "Desconto R$ (F5)", "Cliente ID (F9)", "Código p/
   cancelar item (F6)", "Motivo do cancelamento (F7)" e "Finalizar Venda (F10)",
   mas na prática: F5/F6/F7/F8 estão capturados pela barra principal
   (`MainController`) para navegar entre telas (Compras/Clientes/Fornecedores/
   Relatórios) e F10 **não tem atalho nenhum**. Resultado no balcão: o caixa
   aperta F5 esperando focar o desconto e **cai na tela de Compras**, perdendo a
   venda de vista. **Pedido:** ou ligar esses F-keys de verdade na tela de venda
   (focar o campo certo / acionar o botão), ou **remover os rótulos (Fx)** para
   não mentir pro operador. Hoje o único caminho confiável é Tab.
2. **Diálogo de gerente está bom e seguro.** Foco já no login, Enter autoriza,
   Esc cancela, erro inline sem fechar, senha limpa a cada erro, operador não é
   trocado. É o comportamento que um caixa espera de "chamar o gerente".
3. **Desconto é em R$, não em %.** A etiqueta "Desconto R$" é honesta, mas o
   treinamento precisa deixar claro que o teto de 10% é calculado sobre o
   subtotal — o caixa digita o valor em reais. Sugiro, no futuro, um aviso na
   tela do tipo "teto 10% = R$ X,XX" para o operador não ter que fazer conta de
   cabeça.
4. **Mensagem de "desistiu" é ambígua.** Ao dar Esc no cancelamento de item, o
   status diz "Cancelamento de item não autorizado". Pro caixa, "não autorizado"
   soa como "o gerente barrou", quando na verdade foi ele mesmo que desistiu.
   Sugiro algo como "Cancelamento não concluído" quando foi Esc. (Não bloqueia o
   G1; é ajuste de texto.)
5. **Falta pista visual de que vai precisar de gerente.** No desconto acima do
   teto e no cancelar item, o diálogo aparece "do nada". Seria bom o botão já
   avisar (ex.: "Cancelar item (gerente)") pro caixa saber que vai ter que chamar
   alguém antes de prometer pro cliente.

Status sugerido do G1: **funcional e aprovado no comportamento de autorização**;
pendência de UX nos atalhos de teclado (item 1) a levar ao Coordenador.

---

## Especificação da automação Python (pyautogui) — PARA O DEV AUTORIZAR DEPOIS

> **Não é script. É o pedido.** O John **não escreve nem roda** isto. O
> Coordenador leva ao dev para o Senior Backend construir, e só roda com
> autorização do dev (abrir o app desktop é ação de ambiente).

### Natureza e limites
- pyautogui **clica/digita por coordenada e tempo** — é **frágil**: muda
  resolução, tema, fonte ou posição de janela e quebra. Portanto cobrir **poucos
  fluxos de alto valor**, não o roteiro inteiro.
- Cobrir: **Cenário 1 (caminho feliz — cancelar item com gerente)** + **Cenário 4
  (operador não-gerente é barrado)** + **Cenário 6b (desconto acima do teto,
  Esc não aplica)**. Esses três provam: autoriza, barra quem não é gerente, e
  "cancelar não aplica nada".
- Preferir **teclado** (Tab/Enter/Esc/digitação) a cliques por pixel — combina com
  o roteiro e é menos frágil. Clicar por imagem só se não houver jeito.

### Pré-condições de ambiente (o dev prepara e confirma)
- App rodando via `mvn javafx:run` (NÃO o botão verde do IntelliJ).
- Docker `db_scopel` de pé com porta publicada; `.env` configurado.
- **Resolução de tela conhecida e fixa** (ex.: 1920×1080, 100% de escala),
  janela do app **maximizada** e em posição previsível; tema/CSS padrão.
- Nada de outra janela roubando foco; protetor de tela desligado.
- Seeds presentes (ver tabela abaixo), criados pelo caminho correto (usuários via
  `UserService`/BCrypt).

### Dados de seed necessários
- Usuário **operador** `caixa1` / `1234` (OPERADOR).
- Usuário **gerente** `gerente` / `gerente123` (GERENTE).
- Usuário **operador extra** `caixa2` / `1234` (OPERADOR) — para o cenário "não é
  gerente".
- **Produto** `7891000100103` "Arroz Tipo 1 5kg" preço R$ 25,00, estoque ≥ 20.
- Caixa fechado no começo (o script abre com R$ 100,00) **ou** já aberto para
  `caixa1` — definir qual e deixar o banco nesse estado antes de rodar.

### O que o script deve exercitar e VERIFICAR, passo a passo

**Fluxo A — caminho feliz (Cenário 1):**
1. Login: digitar `caixa1` / `1234`, Enter. *Verificar:* chegou na tela inicial/
   venda (ex.: achar na tela o texto "Ponto de Venda" ou o título "Venda").
2. Abrir caixa com troco R$ 100,00 se necessário. *Verificar:* caixa aberto
   (tela de venda liberada / sem aviso de "caixa fechado").
3. Ir para Venda (F2). Bipar `7891000100103` + Enter, 3 vezes. *Verificar:* a
   tabela tem a linha "Arroz" com Qtd 3 e o TOTAL mostra **R$ 75,00** (ler o
   texto do rótulo de total).
4. Tab até "Código p/ cancelar item", digitar `7891000100103`; Tab até "Cancelar
   item"; Enter. *Verificar:* abriu uma janela nova/modal com o título
   "Autorização de gerente" (achar o texto na tela).
5. Digitar `gerente`, Tab, `gerente123`, Enter. *Verificar (os três ao mesmo
   tempo):* (a) o diálogo fechou; (b) a tabela de itens ficou **vazia**; (c) o
   TOTAL virou **R$ 0,00**; (d) o status mostra **"Item cancelado"**.

**Fluxo B — operador não-gerente é barrado (Cenário 4):**
1. Mesma preparação (bipar 1 Arroz → TOTAL R$ 25,00).
2. Abrir o diálogo de cancelar item. Digitar `caixa2` / `1234`, Enter.
   *Verificar:* o diálogo **continua aberto** (título ainda na tela) e aparece o
   texto **"Usuário não é gerente"**.
3. Apertar Esc. *Verificar:* diálogo fechou; o item Arroz **ainda está** na
   tabela e TOTAL **continua R$ 25,00** (nada foi cancelado).

**Fluxo C — desconto acima do teto, Esc não aplica (Cenário 6b):**
1. Bipar 4 Arroz → subtotal **R$ 100,00**.
2. Tab até "Desconto R$", digitar `20`; Tab até "Aplicar desconto"; Enter.
   *Verificar:* abriu o diálogo "Autorização de gerente".
3. Apertar Esc. *Verificar:* diálogo fechou e o TOTAL **continua R$ 100,00**
   (desconto NÃO aplicado); status "Desconto acima do limite: não autorizado".

### Como verificar sem ler o banco
- A conferência é **visual/pela tela**: comparar o texto do rótulo de TOTAL, a
  presença/ausência da linha na tabela, o texto de status e o título do diálogo.
- Técnica sugerida ao dev: casar por **imagem/região de referência** (capturas do
  TOTAL "R$ 0,00" vs "R$ 75,00") ou OCR de uma região fixa. Tudo depende da
  resolução travada — por isso frágil.
- Entre cada passo, **esperas explícitas** (o app demora a responder; sleeps
  curtos entre ações). Falha de localização = abortar com mensagem clara de qual
  passo quebrou.

### Fora de escopo da automação (fazer manual pelo roteiro)
- Cenários 2 (errar senha e depois acertar), 3 (Esc no item), 5 (5% direto),
  6a (20% aplicado), 7 (cancelar venda), 8 (teclado puro) — validados à mão. São
  importantes, mas multiplicar cenários no pyautogui aumenta a fragilidade sem
  ganho proporcional.
