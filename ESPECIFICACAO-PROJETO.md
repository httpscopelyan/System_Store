# Especificação do Projeto — Sistema de Caixa (PDV) do Mercadinho

> Documento de contexto completo. Objetivo: permitir que um agente (ou qualquer
> pessoa) entenda **o que é o projeto, como ele é construído e onde ele está
> agora**, sem precisar da conversa original. Companheiro de `CLAUDE.md`,
> `TASK.md`, `REVISION.md`, `FIXES.md`.
>
> Última atualização do documento: 2026-10-17.

---

## 1. Visão geral

### 1.1 O que é
Sistema de **PDV (ponto de venda / frente de caixa)** desktop para o mercadinho da
família. É um aplicativo de balcão: o operador faz login, abre o caixa, registra
vendas bipando produtos, recebe o pagamento e fecha o caixa no fim do expediente.

### 1.2 Objetivos
- **Curto prazo:** substituir o controle manual do mercadinho por um caixa digital
  confiável.
- **Longo prazo:** amadurecer o produto para **alugar/licenciar** para outros
  mercados pequenos.

### 1.3 Público / perfis de uso
- **Operador (caixa):** opera a venda e o caixa no dia a dia.
- **Gerente:** tudo do operador + administração (usuários, futuramente relatórios).

### 1.4 Contexto do desenvolvedor
Projeto de **aprendizado** — é o primeiro projeto Java do dev (vem de
Node.js/TypeScript). Por isso o dev **implementa tudo por conta própria**: ao tirar
dúvidas, prefere **explicações e exemplos de implementação**, não o código pronto
do módulo inteiro. Esse ponto é importante para qualquer agente: ajudar a entender
e orientar, não entregar o sistema mastigado — sobretudo por ser um sistema que
lida com dinheiro.

---

## 2. Hardware do ambiente de operação

| Equipamento | Papel | Observação |
|---|---|---|
| Gaveta de dinheiro Elgin | Guardar o dinheiro | Abre por sinal RJ enviado pela impressora |
| Leitor de código de barras USB | Bipar produtos | Atua como teclado: "digita" o código + Enter |
| Impressora térmica VT (bobina 80mm) | Cupom | Também aciona a gaveta |
| Maquininha Mercado Pago Point Smart | Pagamento cartão | Integrável por API (ver ADR 0007); já existe no mercado |
| PC desktop | Rodar o sistema | Atrás do roteador, **sem IP público** |

---

## 3. Stack tecnológica

| Camada | Tecnologia | Notas |
|---|---|---|
| Linguagem | **Java 21** | Máquina de dev roda JDK 26 |
| Framework | **Spring Boot 3** | Contexto, injeção de dependência, Data JPA |
| Interface | **JavaFX 26.0.2** | FXML + Scene Builder |
| Build | **Maven** | App roda com `mvn javafx:run` |
| Banco | **PostgreSQL 16** em **Docker** | Container `db_scopel` |
| Persistência | **Spring Data JPA (Hibernate)** | `ddl-auto=validate` |
| Migrations | **Flyway** | Única fonte do schema (`V__descricao.sql`) |
| Senhas | **BCrypt** (spring-security-crypto) | **Não** é o Spring Security web |
| Variáveis de ambiente | **spring-dotenv** | Lê `.env` |
| Impressão (planejado) | **escpos-coffee** | Cupom + gaveta, ainda não implementado |

### 3.1 Como rodar
- **App:** `mvn javafx:run` — **nunca** o botão verde do IntelliJ (quebra por causa
  dos módulos JavaFX: "JavaFX runtime components are missing").
- **Banco:** container Docker `db_scopel` de pé, com porta publicada
  (`-p 5432:5432`). Senha de dev no `.env` (`ljdwyscopel`, **somente dev**).
- **Testes:** `mvn test`.

---

## 4. Arquitetura

### 4.1 Estilo
Arquitetura **em camadas** (package by layer). Regra de negócio fica
**exclusivamente no service**.

```
model        → entidades JPA (@Entity)
repository   → interfaces JpaRepository
service      → regra de negócio (@Service, @Transactional)
controller   → controllers JavaFX (@Component, @FXML)
config       → UserSession e beans de apoio
```

Direção da dependência: `controller → service → repository → model`.
O controller **nunca** acessa o repository direto.

### 4.2 Padrões JPA adotados
- Relação N:1 com `@ManyToOne` + `@JoinColumn` no lado "muitos" (que carrega a FK).
- Relação 1:N com `@OneToMany(mappedBy=..., cascade=ALL, orphanRemoval=true)`.
- Enums persistidos com `@Enumerated(EnumType.STRING)`.
- `snake_case` no banco / `camelCase` no Java (Hibernate converte).

### 4.3 Integração Spring + JavaFX
- Classe `BetaApplication` faz a ponte Spring ↔ JavaFX; o `springContext` é
  `public static` para os controllers poderem usá-lo.
- Cada controller é um **bean Spring** (`@Component`). O FXML é carregado com
  `FXMLLoader` + `setControllerFactory(BetaApplication.springContext::getBean)`,
  para o Spring injetar as dependências no controller.
- Navegação entre telas:
  `Stage stage = (Stage) algumCampo.getScene().getWindow(); stage.setScene(novaCena);`
- **`UserSession`** (`@Component` em `config/`) é um singleton que guarda o
  **operador logado** (`loggedUser`) e o **caixa atual** (`caixaAtual`).

---

## 5. Modelo de dados

### 5.1 Product (migration V1)
| Campo | Tipo Java | Tipo banco | Observação |
|---|---|---|---|
| id | Long | BIGINT PK | |
| code | String | único | **código de barras — sempre String** (tem zero à esquerda) |
| description | String | | |
| price | BigDecimal | NUMERIC(10,2) | preço de venda |
| quantity | BigDecimal | NUMERIC(10,3) | estoque (3 casas p/ peso) |
| unit | enum `Unidade` {UN, KG} | STRING | `@Enumerated(STRING)` |
| active | Boolean | | |

### 5.2 User (migration V2)
`@Table(name="users")` — `user` é palavra reservada no Postgres.
| Campo | Tipo | Observação |
|---|---|---|
| id | Long | PK |
| name | String | |
| login | String | único |
| password | String | **hash BCrypt** (length 80) |
| role | enum `Role` {OPERADOR, GERENTE} | STRING |
| active | Boolean | |

### 5.3 CashRegister / caixa (migration V3)
| Campo | Tipo | Observação |
|---|---|---|
| id | Long | PK |
| operator | User (`@ManyToOne`) | `operator_id BIGINT NOT NULL REFERENCES users(id)` |
| openingAmount | BigDecimal | valor inicial (fundo de troco) |
| openedAt | LocalDateTime | |
| status | enum {OPEN, CLOSED} | STRING |
| expectedAmount | BigDecimal (nullable) | preenchido no fechamento |
| closingAmount | BigDecimal (nullable) | valor contado no fechamento |
| difference | BigDecimal (nullable) | contado − esperado |
| closedAt | LocalDateTime (nullable) | |

### 5.4 CashMovement / movimentação (migration V4)
| Campo | Tipo | Observação |
|---|---|---|
| id | Long | PK |
| cashRegister | CashRegister (`@ManyToOne`, `@JoinColumn cash_register_id`) | |
| type | enum {SANGRIA, SUPRIMENTO} | STRING |
| amount | BigDecimal | |
| reason | String | motivo |
| createdAt | LocalDateTime | |

### 5.5 Sale / venda (migration V5)
| Campo | Tipo | Observação |
|---|---|---|
| id | Long | PK |
| operator | User (`@ManyToOne`) | |
| cashRegister | CashRegister (`@ManyToOne`) | venda pertence a um caixa |
| createdAt | LocalDateTime | |
| total | BigDecimal | |
| status | enum {ABERTA, EM_PAGAMENTO, FINALIZADA, CANCELADA} | STRING | `EM_PAGAMENTO` acrescentado na V7 (RN21) |
| items | List\<SaleItem\> (`@OneToMany mappedBy="sale" cascade=ALL orphanRemoval=true`) | |

### 5.6 SaleItem / item da venda (migration V6)
| Campo | Tipo | Observação |
|---|---|---|
| id | Long | PK |
| sale | Sale (`@ManyToOne`) | |
| product | Product (`@ManyToOne`) | |
| quantity | BigDecimal | |
| unitPrice | BigDecimal | **preço congelado** (RN19) — cópia do preço do produto no momento |
| totalPrice | BigDecimal | quantity × unitPrice |

### 5.7 Payment / pagamento (migration V7)
Um pagamento por venda nesta fase (Opção A; evolui para 1:N no RN18/F4 — ver ADR 0011).
A V7 também faz `ALTER sale.status TYPE VARCHAR(20)` para caber `EM_PAGAMENTO`.
| Campo | Tipo Java | Tipo banco | Observação |
|---|---|---|---|
| id | Long | BIGINT PK | |
| sale | Sale (`@ManyToOne`, `@JoinColumn sale_id`) | BIGINT NOT NULL | venda paga |
| method | enum `PaymentMethod` {DINHEIRO, CARTAO, PIX} | STRING | F1 só processa DINHEIRO |
| amountReceived | BigDecimal | NUMERIC(10,2) | valor recebido |
| change | BigDecimal | NUMERIC(10,2) | troco (HALF_UP); só DINHEIRO |
| createdAt | LocalDateTime | | |

> Migrations do Flyway: **V1** Product, **V2** User, **V3** CashRegister,
> **V4** CashMovement, **V5** Sale, **V6** SaleItem, **V7** Payment (+ `ALTER sale.status`).

---

## 6. Requisitos funcionais (RF)

### Autenticação e acesso
- **RF01** — O sistema deve autenticar o usuário por **login + senha**, comparando
  a senha informada com o **hash BCrypt** armazenado.
- **RF02** — O sistema deve reconhecer o **papel** do usuário (OPERADOR/GERENTE) e
  restringir funções administrativas ao gerente.

### Caixa
- **RF03** — Abrir caixa informando o **valor inicial** (fundo de troco),
  registrando operador e data/hora.
- **RF04** — Registrar **sangria** (retirada de dinheiro) com valor e motivo.
- **RF05** — Registrar **suprimento** (entrada de dinheiro) com valor e motivo.
- **RF06** — Consultar o **saldo atual** do caixa.
- **RF07** — **Fechar caixa**: informar o valor contado, calcular o valor esperado
  e a **diferença** (contado − esperado), registrando data/hora do fechamento.

### Produtos
- **RF08** — Cadastrar produto (código de barras, descrição, preço, quantidade,
  unidade UN/KG, ativo/inativo).
- **RF09** — Localizar produto pelo **código de barras** (usado ao bipar).
- **RF10** *(futuro)* — Listar, editar e inativar produtos; farol de estoque
  (disponível / abaixo do mínimo / indisponível).

### Venda
- **RF11** — Abrir venda vinculada ao **operador logado** e ao **caixa aberto**.
- **RF12** — Adicionar item **bipando o código de barras**.
- **RF13** — Ao bipar um produto que já está na venda, **somar à quantidade** do
  item existente em vez de criar linha duplicada (RN07).
- **RF14** — Calcular o **total do item** e o **total da venda** em tempo real.
- **RF15** — **Finalizar** a venda, **baixando o estoque** dos produtos (RN10).
- **RF16** *(futuro)* — Tela de pagamento: forma (dinheiro/cartão/Pix), valor
  recebido e **troco**; cancelar venda.

### Interface / navegação
- **RF17** — Tela **inicial (menu)** com acesso às funções conforme o papel
  (Vender, Caixa, Produtos; Usuários/Relatórios para gerente).

### Futuros (fases posteriores)
- **RF18** — Impressão de **cupom** térmico e **abertura da gaveta** (escpos-coffee).
- **RF19** — Emissão fiscal **NFC-e / CF-e SAT**.
- **RF20** — **Pagamento integrado** na maquininha Mercado Pago Point via API
  (com polling de status, sem webhook — ADR 0007).

---

## 7. Requisitos não funcionais (RNF)

- **RNF01 — Precisão monetária:** todo valor em dinheiro usa **BigDecimal**
  (Java) e **NUMERIC** (banco); arredondamento **HALF_UP**. Ponto flutuante
  (`double`/`float`) é proibido para dinheiro.
- **RNF02 — Integridade transacional:** operações que tocam mais de uma tabela
  (ex.: finalizar venda + baixar estoque) rodam dentro de **`@Transactional`**.
- **RNF03 — Segurança de credenciais:** senhas **nunca** em texto puro (sempre
  BCrypt, feito no service); `.env`, senhas e `target/` **nunca** no Git.
- **RNF04 — Conformidade legal:** o sistema **não** pode ter nenhum recurso de
  "venda sem nota" / "caixa 2" (sonegação é crime — Lei 8.137/90, e pode implicar
  o desenvolvedor).
- **RNF05 — Schema versionado:** o banco é controlado por **Flyway**; o Hibernate
  fica em `validate` (só confere, nunca altera). Migration aplicada não se edita
  nem renomeia.
- **RNF06 — Idioma do código:** classes, campos, métodos e tabelas em **inglês**;
  textos de UI e documentação em **português**. Termos de domínio BR (sangria,
  suprimento, NFC-e) podem permanecer.
- **RNF07 — Usabilidade / velocidade:** operação **orientada a teclado** (o leitor
  age como teclado); o campo de código de barras deve manter o **foco** na tela de
  venda; resposta imediata ao bipar.
- **RNF08 — Manutenibilidade:** arquitetura em camadas, regra de negócio só no
  service, nomes consistentes; decisões relevantes registradas em **ADRs**.
- **RNF09 — Portabilidade / reprodutibilidade:** roda via `mvn javafx:run`; banco
  em Docker; configuração por `.env`, replicável em outra máquina.
- **RNF10 — Operação offline-tolerante:** o PDV roda local, atrás do roteador, sem
  IP público (impacta a integração de pagamento, que usa polling).

---

## 8. Regras de negócio (RN)

- **RN01 — Dinheiro exato:** valores monetários em BigDecimal/NUMERIC, HALF_UP
  (origem do RNF01). Operações com `.add()/.subtract()/.multiply()` e comparação
  com `.compareTo()`.
- **RN07 — Agrupamento de item:** bipar um produto já presente na venda **soma na
  quantidade** do item existente; não cria item duplicado. *(Implementado)*
- **RN09 — Venda gravada só após o pagamento:** a venda só vira `FINALIZADA`
  dentro do `SaleService.pay` (`@Transactional`), que grava o `Payment`, baixa o
  estoque e exige recebido ≥ total. *(Implementado para DINHEIRO; testado)*
- **RN10 — Baixa de estoque:** ao **finalizar** a venda, o estoque de cada produto
  é reduzido pela quantidade vendida, dentro de transação
  (`product.setQuantity(product.getQuantity().subtract(item.getQuantity()))` +
  `productRepository.save(product)`). *(Implementado)*
- **RN13 — Abertura de caixa:** só se abre caixa com valor inicial; o caixa aberto
  fica vinculado ao operador. *(Implementado)*
- **RN14 — Movimentações:** sangria reduz e suprimento aumenta o saldo em dinheiro
  esperado do caixa; ambas exigem valor (e motivo). *(Implementado)*
- **RN15 — Saldo esperado:** o saldo esperado do caixa = valor inicial + entradas
  (vendas em dinheiro + suprimentos) − saídas (sangrias). *(Base implementada no
  cálculo do caixa)*
- **RN16 — Fechamento com diferença:** no fechamento, informa-se o valor contado;
  o sistema calcula `diferença = contado − esperado` e registra. É obrigatório
  definir o `expectedAmount` antes de calcular a diferença. *(Implementado)*
- **RN19 — Preço congelado:** ao adicionar um item, o preço do produto é **copiado**
  para o `unitPrice` do item. Alterar o preço do produto depois **não** altera
  vendas já registradas. *(Implementado)*
- **RN21 — Máquina de estados:** o fluxo do caixa é uma máquina de estados
  (CAIXA_FECHADO → CAIXA_ABERTO → VENDA_ABERTA → EM_PAGAMENTO → ...). Cada ação só
  é válida em certos estados. Validação em **dois níveis**: a UI desabilita botões
  fora do estado (conveniência) **e** o service recusa a ação fora do estado com
  exceção específica (segurança). A UI desabilitar **nunca** substitui o service
  validar. *(Parcialmente aplicado; princípio a manter)*

---

## 9. Fluxos principais

### 9.1 Login
Usuário informa login + senha → `UserService.login` busca por login
(`findByLogin`), compara a senha com `passwordEncoder.matches(...)`; se falhar,
lança exceção (`UserNotFindException` / `InvalidPasswordException`); se ok, retorna
o `User` e ele é posto na `UserSession`.

```java
public User login(String login, String rawPassword) {
    User user = repository.findByLogin(login)
        .orElseThrow(() -> new UserNotFindException());
    if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
        throw new InvalidPasswordException();
    }
    return user;
}
```

### 9.2 Caixa
Abrir (valor inicial) → operações do dia (sangria/suprimento) → fechar (valor
contado → esperado → diferença). `CashRegisterService`:
open / supplement / withdrawal / close / currentBalance. O `close` exige
`setExpectedAmount(...)` antes de calcular a diferença.

### 9.3 Venda (fluxo que já funciona ponta a ponta)
Login → abrir caixa → **navega para a venda** → bipar produto (precisa estar
cadastrado) → total soma → finalizar → baixa estoque.
`SaleService`: `openSale(operator, caixaAtual)`, `addItem` (RN07 + RN19),
`finalizeSale` (`@Transactional`, RN10).
`SaleController`: `TableView<SaleItemRow>` com `ObservableList`; `initialize()`
configura `PropertyValueFactory` e abre a venda; `onAddItem` (bipar); `onFinalize`.

### 9.4 Pagamento (dinheiro — implementado; demais formas futuras)
Ao finalizar a venda, o `SaleController.onFinalize` chama
`SaleService.goToPayment` (ABERTA + itens → `EM_PAGAMENTO`) e navega para
`pagamento.fxml`. Na tela, o operador informa o valor recebido; o troco é
recalculado a cada digitação via `SaleService.calculateChange` (HALF_UP). Ao
confirmar, `SaleService.pay` (`@Transactional`) valida que a venda está
`EM_PAGAMENTO`, aceita **somente DINHEIRO**, exige recebido ≥ total, grava o
`Payment`, baixa o estoque e marca a venda `FINALIZADA` (RN09). Formas CARTAO/PIX,
pagamento múltiplo (RN18), impressão de cupom e NFC-e/CF-e ficam para fases
seguintes.

---

## 10. O que já foi construído (manualmente)

Status por módulo:

- **Product** — entidade + migration V1 + repository + service. **Testado.**
- **User** — entidade (`users`) + migration V2 + login com BCrypt. **Testado.**
- **CashRegister / CashMovement** — entidades + migrations V3/V4 + service
  (abrir/sangria/suprimento/fechar/saldo + `findOpenByOperator`). **Testado**
  (inclui 4 casos de `findOpenByOperator` em `CashRegisterServiceTests` —
  2026-10-17, ver FIXES.md).
- **Sale / SaleItem** — entidades + migrations V5/V6 + service (openSale, addItem
  com RN07, finalizeSale com RN10) + controller com TableView. **Testado**
  (`SaleServiceTests`, 8 casos: openSale, preço congelado RN19, agrupamento RN07,
  produto inexistente, preço alterado depois, KG fracionário, baixa de estoque
  RN10, soma de produtos). Ajuste de arredondamento HALF_UP (RN01) aplicado em
  `addItem` e `calculateTotal` (ver FIXES.md, 2026-10-17).
- **Payment / pagamento em dinheiro** — entidade `Payment` + `PaymentRepository` +
  migration V7 (+ `ALTER sale.status` para `EM_PAGAMENTO`). `SaleService` ganhou
  `goToPayment`, `calculateChange` (troco HALF_UP) e `pay` (`@Transactional`:
  valida `EM_PAGAMENTO`, só DINHEIRO, recebido ≥ total; grava `Payment` + baixa
  estoque + FINALIZADA — RN09), além de guarda de estado no `addItem` (só ABERTA).
  **Service testado** (9 casos novos em `SaleServiceTests`: goToPayment, pay
  feliz/exato/insuficiente, forma inválida, estado inválido, calculateChange,
  addItem fora de ABERTA). Suíte total 36 testes verde (JDK 26); Segurança aprovou.
  A **UI** (`pagamento.fxml` + `PaymentController`, total/troco grandes) **não tem
  teste automatizado** — validar com `mvn javafx:run`. Ver `REVISION.md`.
- **UI básica** — telas de login, caixa (abrir/fechar na mesma tela) e venda
  (bipar/finalizar). Após abrir o caixa, navega para a venda. O **login agora
  recupera o caixa aberto do operador** (`findOpenByOperator`) e o repõe na
  `UserSession`, permitindo fechar um caixa OPEN mesmo após reiniciar o app. O
  **finalizar da venda agora leva à tela de pagamento** (dinheiro + troco);
  `UserSession` passou a guardar também a `vendaAtual`.
- **Documentação** — ADRs registradas; `CLAUDE.md` + arquivos de contexto
  (`TASK.md`, `REVISION.md`, `FIXES.md`).
- **Design (em andamento)** — mockup visual de **login** e **menu inicial**; a
  próxima tela a ganhar o layout "de mercado" é a tela inicial (copiando a
  disposição de um ERP de referência: barra de ícones + menu lateral + área
  central + barra de status), por enquanto só com as funções existentes (Vender,
  Caixa, Produtos; Usuários/Relatórios como placeholder).

**Fluxo operacional atual:** funciona de ponta a ponta — login → abrir caixa →
venda → bipar → somar → finalizar → baixa de estoque.

---

## 11. Erros enfrentados e soluções (histórico — útil para não repetir)

| Sintoma | Causa | Solução |
|---|---|---|
| "JavaFX runtime components are missing" | Rodou pelo botão verde do IntelliJ | Rodar com `mvn javafx:run` |
| "password authentication failed for user postgres" | Container Docker parado / `.env` não carregado | Subir `db_scopel`; conferir `.env` |
| Porta do Postgres não publicada (`5432/tcp`) | Container sem `-p` | Recriar com `-p 5432:5432` |
| "Found more than one migration with version 3" | Dois arquivos V3 | Apagar o V3 duplicado |
| "Migration checksum mismatch" / "non-empty schema but no history table" | Dropou só algumas tabelas | Dropar **todas** as tabelas + `flyway_schema_history`, ou recriar o container |
| "Location is not set" | Caminho do recurso FXML errado | Corrigir o path do FXML |
| "No qualifying bean ...Controller" | Controller sem `@Component` ou sem `setControllerFactory` | Anotar `@Component` e usar a controller factory do Spring |
| Erro com a tabela `user` | `user` é palavra reservada no Postgres | `@Table(name="users")` + `CREATE TABLE users` |
| `difference` nulo no fechamento (NPE no teste) | Faltou `setExpectedAmount` antes de calcular | Definir `expectedAmount` no `close` |
| `cash_register_id` nulo na venda | Caixa não aberto / não posto na sessão antes da venda | Garantir caixa aberto na `UserSession` antes de abrir a venda |
| Navegação da venda quebrava com `RuntimeException` vazia | Carregava o FXML errado (o próprio) e o catch relançava exceção vazia escondendo o erro | Apontar para `sale.fxml` e não engolir a exceção original |

---

## 12. Bugs conhecidos (em aberto)

- **~~Caixa aberto não recuperado ao reabrir o app~~ — RESOLVIDO (2026-10-17):**
  antes, ao reiniciar, o `caixaAtual` em memória voltava a `null`, impedindo fechar
  um caixa que estava OPEN no banco.
  - **Solução aplicada:** no login, `LoginController.onLogin()` chama
    `cashRegisterService.findOpenByOperator(loggedUser)` (delega ao repo
    `findByOperatorAndStatus(operator, OPEN)`) e põe o caixa OPEN na `UserSession`
    (`setCaixaAtual(... .orElse(null))`), limpando `caixaAtual` quando não há caixa
    aberto — evita herdar caixa de login anterior no singleton. Backend coberto por
    4 testes em `CashRegisterServiceTests` (presente, vazio, pós-fechamento,
    isolamento por operador RN13). Ver `FIXES.md`.
  - ~~**Contorno manual:** `UPDATE cash_register SET status='CLOSED' WHERE status='OPEN';`~~
  - **Ressalva de validação:** a fiação no `LoginController` é UI (JavaFX) e **não
    tem teste automatizado**. Requer validação manual do dev via `mvn javafx:run`:
    logar com operador que tem caixa OPEN e confirmar que a tela de venda reconhece
    o caixa.
- **~~`UserServiceTests.loginWithCorrectPassword` falha com `UserDuplicatedException`~~ — RESOLVIDO (2026-10-17):**
  era data-dependente (quebrava contra banco com dados de execução anterior).
  Corrigido com `@Transactional` + logins/códigos únicos (UUID) em `UserServiceTests`
  e `BetaApplicationTests`. Suíte 100% verde (23 testes). Ver `FIXES.md`.
- **~~Dívida técnica — `SaleService.finalizeSale` finaliza sem pagamento (apontada
  pela Segurança, 2026-10-17)~~ — RESOLVIDA (2026-10-17):** `finalizeSale` tornou-se
  **privado** e passou a ser chamado **somente por `pay`** (que grava o `Payment`
  antes). Não existe mais caminho público que finalize uma venda sem `Payment` —
  o atalho foi eliminado. O teste que chamava `finalizeSale` direto foi convertido
  para a via pública (`openSale → addItem → goToPayment → pay`), mantendo a cobertura
  de baixa de estoque (UN + KG). Suíte 36 testes, 0 falhas. Ver `FIXES.md`.
- **UI de pagamento sem teste automatizado:** `PaymentController` + `pagamento.fxml`
  são JavaFX e não têm cobertura — validar manualmente via `mvn javafx:run`
  (finalizar venda, digitar valor recebido, conferir troco e FINALIZADA).
- **Ambiente de teste exige JDK 24+ e Postgres de pé:** rodar `mvn test` precisa de
  **JDK 24+ (o dev usa JDK 26)** por causa da JavaFX 26 e do container `db_scopel`
  no ar. Com JDK 21 o contexto Spring nem sobe.

---

## 13. Roadmap (o que falta)

1. ~~Teste unitário do `SaleService`.~~ **Concluído** (`SaleServiceTests`, 8 casos
   verdes — 2026-10-17).
2. ~~Recuperar caixa aberto ao reabrir o app (resolver o bug conhecido).~~
   **Concluído** (2026-10-17): login recupera o caixa OPEN via
   `findOpenByOperator`. Falta validação manual da UI (ver seção 12).
3. **Design** das telas (em andamento): tela inicial → venda → pagamento → login →
   telas de gestão.
4. Tela de **pagamento** (forma de pagamento + troco) — **feita para DINHEIRO**
   (2026-10-17): service `goToPayment`/`calculateChange`/`pay` testado + tela
   `pagamento.fxml`. Faltam CARTAO/PIX, pagamento múltiplo (RN18) e impressão
   (fases seguintes); UI a validar via `mvn javafx:run`.
5. **Impressão** de cupom + **abertura da gaveta** (escpos-coffee).
6. **Fiscal**: NFC-e / CF-e SAT (fase 3).
7. **Pagamento integrado** Mercado Pago Point via API + polling (fase 4).
8. Telas de gestão: cadastro/consulta de produtos completo, cadastro de usuários,
   relatórios.

---

## 14. Decisões de arquitetura (ADRs)

- **0003 — BigDecimal para dinheiro:** NUMERIC(10,2) p/ dinheiro, NUMERIC(10,3)
  p/ peso, HALF_UP. (Base da RN01.)
- **0005 — Flyway + Hibernate `validate`:** Flyway é a fonte única do schema;
  Hibernate só valida. Migration aplicada não se edita/renomeia.
- **0007 — Pagamento via API Mercado Pago Point:** usa a maquininha existente,
  integração HTTP/JSON, **polling** (sem IP público para webhook); abstração
  `MeioPagamento` para trocar implementação depois. TEF clássico adiado.
- **0008 — Idioma do código em inglês:** código em inglês; UI/docs em português;
  evitar renomear após migration aplicada.
- **0010 — PDV como máquina de estados:** validação em dois níveis (UI + service).
  (Base da RN21.) Atualizada em 2026-10-17: acrescentado o estado **`EM_PAGAMENTO`**
  entre VENDA_ABERTA e FINALIZADA. Transições protegidas no service: `goToPayment`
  (ABERTA + itens → EM_PAGAMENTO), `pay` (EM_PAGAMENTO → FINALIZADA) e guarda no
  `addItem` (só em ABERTA), cada uma recusando a ação fora do estado com exceção.
- **0011 — Persistência de pagamento como entidade `Payment`:** o pagamento é
  modelado como entidade própria (tabela `payment`), não como campos soltos na
  `Sale`. **Opção A** adotada em F1: **um `Payment` por venda** (DINHEIRO), com
  `method`, `amountReceived`, `change` e `createdAt`. Motivação: dar base para a
  emissão fiscal (RF49), relatórios de vendas por forma de pagamento (RF45) e
  garantir a RN09 (venda só finaliza com pagamento gravado). Evolui para **1:N**
  (pagamento múltiplo) no RN18 / F4 sem reescrever o modelo — a `Sale` passa a ter
  vários `Payment`. Troco só se aplica a DINHEIRO.

---

## 15. Convenções e como trabalhar no projeto

- **Regra de negócio só no service**; controller orquestra, repository só persiste.
- **Dinheiro sempre BigDecimal**; **código de barras sempre String**.
- **Senha sempre via service (BCrypt)** — nunca inserir usuário por SQL cru (fura
  o hash).
- **Mudança de schema = nova migration** (V7, V8...), nunca editar uma já aplicada.
- **Nunca** implementar venda sem nota / caixa 2.
- **Nunca** commitar `.env`, senhas ou `target/`.
- **Código em inglês**, UI em português.
- **Rodar com `mvn javafx:run`.**
- O dev aprende fazendo: um agente deve **explicar e orientar**, propor exemplos e
  revisar o raciocínio — não entregar módulos inteiros prontos sem o dev entender,
  ainda mais em partes que lidam com dinheiro.

### Arquivos de contexto companheiros
- **CLAUDE.md** — contexto operacional resumido para o dia a dia.
- **TASK.md** — o que o dev descreve para a IA fazer na rodada atual.
- **REVISION.md** — melhorias e correções longas da última task.
- **FIXES.md** — correções curtas e pontuais.

---

## 16. Glossário

- **PDV / frente de caixa:** tela onde a venda acontece (bipar, somar, finalizar).
- **Sangria:** retirada de dinheiro do caixa.
- **Suprimento:** entrada de dinheiro no caixa (ex.: reforço de troco).
- **Saldo esperado:** quanto deveria haver no caixa segundo o sistema.
- **Diferença:** valor contado no fechamento menos o esperado (sobra/falta).
- **Preço congelado (RN19):** preço gravado no item no momento da venda.
- **NFC-e / CF-e SAT:** documentos fiscais eletrônicos do varejo (fase futura).
- **Polling:** consultar repetidamente o status de um pagamento (em vez de webhook).
