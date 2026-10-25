# Roteiro E2E — G2: Quantidade / multiplicador e peso na venda

> Parecer de usabilidade real escrito pelo John (operador de caixa).
> Funcionalidade: no campo do leitor da tela de venda, o operador pode digitar
> **`quantidade*código`** e dar Enter para vender várias unidades de uma vez
> (produto UN) ou digitar o **peso** (produto KG) sem ter que bipar item por item.
> Bipe simples (só o código) continua valendo 1 unidade, como sempre.
>
> Requisitos cobertos: RF17/RN05 (quantidade/multiplicador e peso fracionário),
> RN07 (soma item repetido), RF16 (código digitado). Teclado-first: RNF03.
> Total legível: RNF04.

---

## Como funciona na prática (o que o caixa vê)

- Tudo acontece no **mesmo campo do leitor** (`codeField`, rótulo "Código de
  barras", prompt "Bipe ou digite o código"). O cursor **já pisca nele** ao entrar
  na venda e **volta pra ele depois de cada bipada ou erro** (RNF03).
- Formatos aceitos ao dar **Enter**:
  - **`6*7891234`** → adiciona **6 unidades** do produto de código `7891234`.
  - **`1,250*7899`** → adiciona **1,250 kg** do produto KG de código `7899`
    (vírgula pt-BR como separador decimal).
  - **`7891234`** (só o código, sem `*`) → **1 unidade** — o bipe de sempre,
    intacto.
- **Produto UN recusa fração** ("Produto vendido por unidade não aceita fração").
  **Produto KG aceita até 3 casas** decimais.
- **Erros aparecem no `statusLabel`** (a linha de status da venda), em português,
  **sem derrubar a venda** — o item errado simplesmente não entra, o campo do
  leitor é limpo e o cursor volta pra ele pra tentar de novo.
- O **TOTAL** grande no canto (RNF04) recalcula a cada item que entra.

### Mensagens de erro (exatamente o que aparece)
| Situação | O que eu digito | Mensagem no status |
|---|---|---|
| Quantidade vazia antes do `*` | `*7891234` | **Informe a quantidade antes do \*** |
| Sem código depois do `*` | `6*` | **Informe o código do produto** |
| Dois `*` | `6*7*8` | **Formato inválido: use quantidade\*código** |
| Quantidade não numérica | `abc*7891234` | **Quantidade inválida** |
| Quantidade zero ou negativa | `0*7891234` / `-1*7891234` | **Quantidade deve ser maior que zero** |
| UN com fração | `2,5*7891234` | **Produto vendido por unidade não aceita fração** |
| KG com mais de 3 casas | `1,2345*7899` | **Peso aceita no máximo 3 casas decimais** |
| Código inexistente | `6*0000000` | **Produto não encontrado** |

---

## Pré-condições comuns (valem para todos os cenários)

Dados de seed que precisam existir **antes** de rodar (produtos criados pelo
caminho correto — `ProductService`, nunca SQL cru):

| Código | Descrição | Unidade | Preço | Estoque |
|---|---|---|---|---|
| `7891234` | Refrigerante Lata 350ml | **UN** | R$ 5,00 | ≥ 50 un |
| `7899` | Banana Prata (granel) | **KG** | R$ 6,00 / kg | ≥ 20,000 kg |

Usuário operador de seed (criado via `UserService`/BCrypt):

| Papel | Login | Senha |
|---|---|---|
| Operador | `caixa1` | `1234` |

Estado inicial de cada cenário (salvo indicação): **operador `caixa1` logado**,
**caixa aberto** (troco inicial R$ 100,00) e **na tela de Venda** (menu F2 /
botão Vender), com o cursor piscando no campo "Código de barras".

> Por que esses preços? Fazem a conta fechar redonda e fácil de conferir de
> longe: UN a R$ 5,00 → 6 unidades = **R$ 30,00**; KG a R$ 6,00/kg → 1,250 kg =
> **R$ 7,50**. Deixa os cenários objetivos para quem está validando no balcão.

---

## Cenário 1 — Fluxo feliz UN: vender 6 unidades de uma vez (multiplicador)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Entro na venda; o cursor já está no campo "Código de barras" | Campo focado, venda vazia, TOTAL **R$ 0,00**. |
| 2 | Digito `6*7891234` e aperto **Enter** | Entra **uma** linha "Refrigerante Lata 350ml", **Qtd 6**, preço unit. R$ 5,00, total da linha R$ 30,00. TOTAL grande mostra **R$ 30,00**. O campo do leitor fica limpo e o cursor volta pra ele. Status sem erro. |
| 3 | Confiro a prévia do cupom | O cupom mostra o item com quantidade 6 e o total R$ 30,00. |

> Ganho de caixa: 6 refrigerantes num Enter só, em vez de 6 bipadas. É o que a
> gente faz o dia inteiro com fardo/engradado.

---

## Cenário 2 — Fluxo feliz KG: vender 1,250 kg (peso digitado)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Entro na venda, cursor no leitor | Venda vazia, TOTAL **R$ 0,00**. |
| 2 | Digito `1,250*7899` e aperto **Enter** (vírgula pt-BR) | Entra uma linha "Banana Prata (granel)", **Qtd 1,250** (kg), preço unit. R$ 6,00, total da linha **R$ 7,50**. TOTAL mostra **R$ 7,50**. Campo limpo, cursor de volta. |
| 3 | Confiro a prévia do cupom | Mostra o peso 1,250 e o total R$ 7,50. |

> Observação: o separador é **vírgula** (como no teclado/etiqueta brasileira),
> não ponto. Ver parecer sobre testar ponto.

---

## Cenário 3 — Bipe simples sem `*` continua 1 unidade (nada quebrou)

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Digito só `7891234` e Enter (como o leitor USB faz: código + Enter) | Entra "Refrigerante Lata 350ml", **Qtd 1**, TOTAL **R$ 5,00**. Comportamento idêntico ao de antes do G2. |
| 2 | Digito só `7899` e Enter | Entra "Banana Prata (granel)", **Qtd 1,000** (kg), TOTAL sobe para **R$ 11,00** (5,00 + 6,00). |

> O ponto-chave deste cenário: a mudança do G2 **não pode ter quebrado o bipe
> normal**. Leitor que manda código + Enter tem que continuar funcionando exatamente
> como antes.

---

## Cenário 4 — Somatório RN07: multiplicador + bipe simples somam na mesma linha

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Digito `6*7891234` e Enter | Linha "Refrigerante Lata 350ml", **Qtd 6**, TOTAL **R$ 30,00**. |
| 2 | Digito só `7891234` e Enter (o mesmo produto, bipe simples) | **Não cria linha nova:** a mesma linha vira **Qtd 7**, TOTAL **R$ 35,00** (RN07). |
| 3 | Digito `2*7891234` e Enter | Continua a mesma linha, **Qtd 9**, TOTAL **R$ 45,00**. |
| 4 | Confiro a tabela | Só **uma** linha de Refrigerante com Qtd 9. Não há item duplicado. |

---

## Cenário 5 — UN com fração é recusada, venda NÃO cai

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Com a venda vazia, digito `2,5*7891234` e Enter (UN a R$ 5,00) | **Nada entra na tabela.** Status mostra **"Produto vendido por unidade não aceita fração"**. TOTAL continua **R$ 0,00**. Campo do leitor limpo, cursor de volta. |
| 2 | Logo em seguida digito `6*7891234` e Enter | A venda **segue viva**: entra normalmente a linha com Qtd 6, TOTAL **R$ 30,00**. O erro anterior não derrubou nem travou a venda. |

> Prova do que importa: errar a digitação **não** cancela a venda nem trava o
> caixa — é só uma mensagem e tenta de novo.

---

## Cenário 6 — Entradas malformadas: cada erro mostra a mensagem PT e a venda segue

Faça um a um, com a venda vazia. Depois de cada erro, confirme que **nada entrou
na tabela**, o **TOTAL continua R$ 0,00**, o campo foi limpo e o cursor voltou.

| # | O que eu digito e Enter | Resultado esperado (status) |
|---|---|---|
| 1 | `*7891234` (quantidade vazia antes do `*`) | **"Informe a quantidade antes do \*"** — nada entra. |
| 2 | `6*` (sem código depois do `*`) | **"Informe o código do produto"** — nada entra. |
| 3 | `6*7*8` (dois `*`) | **"Formato inválido: use quantidade\*código"** — nada entra. |
| 4 | `abc*7891234` (quantidade não numérica) | **"Quantidade inválida"** — nada entra. |
| 5 | `0*7891234` (quantidade zero) | **"Quantidade deve ser maior que zero"** — nada entra. |
| 6 | `-1*7891234` (quantidade negativa) | **"Quantidade deve ser maior que zero"** — nada entra. |
| 7 | (Fechamento) Depois dos 6 erros, digito `3*7891234` e Enter | A venda **nunca caiu** durante os erros: agora entra a linha Qtd 3, TOTAL **R$ 15,00**. |

> Cada mensagem é em português e clara pro operador. Nenhuma delas fecha ou zera a
> venda — só recusa a entrada errada.

---

## Cenário 7 — Foco volta sempre ao leitor (RNF03) e o teclado resolve tudo

| # | O que eu faço | Resultado esperado |
|---|---|---|
| 1 | Entro na venda **sem tocar no mouse**. Digito `6*7891234`, Enter; `1,250*7899`, Enter; `7891234`, Enter | Depois de **cada** Enter o cursor **volta sozinho** para o campo "Código de barras" — dá pra emendar a próxima digitação sem clicar em nada. A venda monta: 7 refrigerantes + 1,250 kg banana. |
| 2 | Provoco um erro (`2,5*7891234`, Enter) | O cursor **também volta** para o leitor depois do erro; sigo digitando o próximo item direto. |
| 3 | Clico com o mouse numa linha da tabela de propósito e volto a digitar | O foco **retorna** ao campo de código (foco perpétuo, RNF03): o próximo bipe cai no lugar certo mesmo tendo clicado fora. |

---

## Parecer de usabilidade real (John, operador)

1. **O multiplicador é um ganho enorme de velocidade.** `6*código` resolve fardo,
   engradado e dúzia num Enter só. É exatamente o que o mercado faz. Alinhado.
2. **Peso por KG funciona e a vírgula é a certa.** Digitar `1,250*7899` bate com a
   etiqueta e o teclado brasileiro. **Achado:** vale confirmar na validação manual
   se o operador que digitar **ponto** (`1.250`) por costume de teclado numérico
   recebe erro claro ou um valor errado — sugiro aceitar ponto como sinônimo de
   vírgula no futuro, pra não punir o hábito. (Não bloqueia o G2.)
3. **A sintaxe `quantidade*código` precisa de treino.** No balcão o caixa pensa
   "quantidade primeiro, depois o código" — tudo bem, mas **não há nenhuma pista na
   tela** de que dá pra usar `*`. O prompt diz só "Bipe ou digite o código".
   **Sugestão:** um lembrete discreto (ex.: prompt "Bipe, ou digite qtd\*código")
   ou uma linha de ajuda. Hoje, quem não foi treinado não descobre sozinho.
4. **Falta o atalho F4 que o roteiro de atalhos (G5) prometia para quantidade.** O
   mecanismo entregue é digitar no próprio leitor — e funciona bem —, mas a tabela
   de requisitos e alguns fabricantes falam em **tecla de quantidade (F4)**. Como o
   G2 resolve por digitação no leitor, **não ligar F4 é aceitável** e evita UI
   morta; só registro para o Coordenador reconciliar o MAPA (F4 não é mais
   necessário para quantidade).
5. **Mensagens de erro estão ótimas.** Claras, em PT, e — o mais importante pra um
   caixa com fila — **não derrubam a venda**. Errou a digitação, aparece o aviso,
   tenta de novo. É o comportamento certo.
6. **KG mostrando "1,000" para 1 unidade bipada.** No cenário 3, bipar o produto KG
   sem `*` mostra Qtd **1,000**. Correto tecnicamente (3 casas), mas confirmar na
   tela se fica legível/claro pro operador que são "1,000 kg". Pequeno ponto de
   leitura, não bug.

Status sugerido do G2: **funcional e aprovado no comportamento** (multiplicador,
peso, somatório, recusa de fração, erros sem derrubar a venda, foco no leitor);
pendência de **validação manual na tela** (`mvn javafx:run`, JDK 26) e os pontos
de UX 2/3/6 a levar ao Coordenador.

---

## Especificação da automação Python (pyautogui) — PARA O DEV AUTORIZAR DEPOIS

> **Não é script. É o pedido.** O John **não escreve nem roda** isto. O
> Coordenador leva ao dev para o Senior Backend construir, e só roda com
> autorização do dev (abrir o app desktop é ação de ambiente).

### Natureza e limites
- pyautogui **digita/clica por coordenada e tempo** — é **frágil**: muda
  resolução, escala, fonte, tema ou posição da janela e quebra. Por isso cobrir
  **poucos fluxos de alto valor**, não o roteiro inteiro.
- Cobrir: **Cenário 1 (UN 6×)** + **Cenário 2 (KG 1,250)** + **Cenário 4
  (somatório RN07)** + **Cenário 5 (UN com fração recusada, venda não cai)**.
  Esses quatro provam o essencial: multiplica, pesa, soma na mesma linha e recusa
  fração sem derrubar a venda.
- Tudo é **digitação no campo do leitor + Enter** — este fluxo é naturalmente
  teclado-first, o que combina com o roteiro e é bem menos frágil que clicar por
  pixel. **Nenhum clique por pixel.**

### Pré-condições de ambiente (o dev prepara e confirma)
- App rodando via `mvn javafx:run` (NÃO o botão verde do IntelliJ).
- Docker `db_scopel` de pé com porta publicada; `.env` configurado.
- **Resolução de tela conhecida e fixa** (ex.: 1920×1080, escala 100%), janela do
  app **maximizada** e em posição previsível; tema/CSS padrão (mesma calibração do
  G1 — reaproveitar as regiões de OCR do `g1_senha_supervisor.py` para TOTAL e
  status, pois são os mesmos rótulos da tela de venda).
- Nenhuma outra janela roubando foco; protetor de tela desligado.
- **Operador `caixa1` logado e caixa ABERTO** antes de rodar (deixar o banco nesse
  estado — abrir caixa pela UI é frágil, fica fora do escopo automatizado, igual ao
  G1 com `CAIXA_JA_ABERTO = True`).
- Seeds presentes (ver abaixo), produtos criados via `ProductService`.

### Dados de seed necessários
- Usuário **operador** `caixa1` / `1234` (OPERADOR).
- Produto **UN** `7891234` "Refrigerante Lata 350ml", preço **R$ 5,00**, estoque
  **≥ 50**.
- Produto **KG** `7899` "Banana Prata (granel)", preço **R$ 6,00/kg**, estoque
  **≥ 20,000 kg**.
- **Importante:** o script faz **várias adições**; garantir estoque folgado para
  não esbarrar em recusa por falta (o foco do G2 não é estoque).

### O que o script deve exercitar e VERIFICAR, passo a passo

**Fluxo A — UN 6× (Cenário 1):**
1. Garantir que está na tela de Venda com o campo de código focado.
2. Digitar `6*7891234` e Enter. *Verificar:* o TOTAL mostra **R$ 30,00** (ler o
   texto do rótulo de total por OCR). Opcional: a tabela tem **uma** linha e o
   campo do leitor está vazio.

**Fluxo B — KG 1,250 (Cenário 2):** *(venda nova/limpa — reiniciar a tela de venda
ou rodar num estado zerado)*
1. Digitar `1,250*7899` e Enter. *Verificar:* TOTAL mostra **R$ 7,50**.

**Fluxo C — somatório RN07 (Cenário 4):** *(venda nova/limpa)*
1. Digitar `6*7891234` e Enter. *Verificar:* TOTAL **R$ 30,00**.
2. Digitar `7891234` e Enter. *Verificar:* TOTAL **R$ 35,00** e a tabela continua
   com **uma só** linha (não duplicou) — Qtd passou a 7.

**Fluxo D — UN com fração recusada, venda não cai (Cenário 5):** *(venda nova/limpa)*
1. Digitar `2,5*7891234` e Enter. *Verificar (os dois juntos):* (a) o status
   mostra **"Produto vendido por unidade não aceita fração"**; (b) o TOTAL continua
   **R$ 0,00** (nada entrou).
2. Digitar `6*7891234` e Enter. *Verificar:* a venda seguia viva — agora o TOTAL
   vira **R$ 30,00** (o erro não derrubou a venda).

### Como verificar sem ler o banco
- Conferência **100% visual/pela tela**: comparar o texto do rótulo de **TOTAL**, o
  texto de **status**, e a presença/contagem de linhas na tabela. **Nunca** consulta
  o banco.
- Reaproveitar a técnica e as regiões do `g1_senha_supervisor.py` (OCR Tesseract em
  regiões fixas, ou casar por imagem de referência das capturas de TOTAL). Tudo
  depende da resolução travada — por isso frágil.
- Entre cada passo, **esperas explícitas** (o app demora a responder). Falha de
  leitura = abortar com mensagem clara dizendo qual passo/fluxo quebrou (ex.:
  `[A total 30,00] ...`), código de saída ≠ 0.
- **Reset entre fluxos:** cada fluxo começa com a venda **limpa**. Como não há um
  "nova venda" trivial por teclado garantido, combinar com o dev a forma de zerar
  (reentrar na tela de venda pelo menu, que no `initialize` abre uma venda vazia) —
  ou rodar um fluxo por execução do script.

### Fora de escopo da automação (fazer manual pelo roteiro)
- Cenário 3 (bipe simples sem `*`), Cenário 6 (todos os malformados) e Cenário 7
  (foco no leitor / mouse) — validados **à mão**. Multiplicar cenários de erro no
  pyautogui aumenta a fragilidade (depende muito de ler o texto exato do status por
  OCR) sem ganho proporcional; a leitura de UMA mensagem de erro (Fluxo D) já prova
  o padrão "erro não derruba a venda".
