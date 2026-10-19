# CLAUDE.md — Sistema de Caixa (PDV)

Contexto do projeto para qualquer pessoa (ou IA) continuar o trabalho. Leia este
arquivo inteiro antes de mexer. Companheiros: `TASK.md`, `REVISION.md`, `FIXES.md`.

---

## 1. Visão geral

PDV (ponto de venda / caixa) desktop para o mercadinho da família. Objetivo de
longo prazo: alugar para outros mercados. Projeto de aprendizado do dev — ver
seção 8.

Fluxo atual funcionando: **login → abrir caixa → tela de venda → bipar produto →
somar total → finalizar (baixa estoque)**.

### Hardware do mercado
- Gaveta de dinheiro Elgin (abre via RJ na impressora)
- Leitor de código de barras USB (atua como teclado: digita o código + Enter)
- Impressora térmica VT (cupom)
- Maquininha Mercado Pago Point Smart (pagamento cartão — ver ADR 0007)

---

## 2. Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 (máquina dev roda JDK 26) |
| Framework | Spring Boot 3 (contexto, injeção, Data JPA) |
| UI | JavaFX 26.0.2 (FXML + Scene Builder) |
| Build | Maven |
| Banco | PostgreSQL 16 em Docker (container `db_scopel`) |
| Persistência | Spring Data JPA (Hibernate) |
| Migrations | Flyway (`V__descricao.sql`) |
| Senhas | BCrypt (spring-security-crypto, **não** o Spring Security web) |
| .env | spring-dotenv |
| Impressão | escpos-coffee (planejado, não implementado) |

### Como rodar
- App: `mvn javafx:run` — **NÃO** o botão verde do IntelliJ (quebra por causa dos
  módulos JavaFX).
- Banco: container Docker `db_scopel` precisa estar de pé com porta publicada
  (`-p 5432:5432`). Senha dev em `.env`.
- Testes: `mvn test`. Exige **JDK 24+ (o dev usa JDK 26)** por causa da JavaFX 26
  e o container `db_scopel` de pé — com JDK 21 o contexto Spring nem sobe.

---

## 3. Arquitetura

Arquitetura em **camadas** (package by layer). Regra de negócio fica **somente no
service**.

```
model        → entidades JPA (@Entity)
repository   → interfaces JpaRepository
service      → regra de negócio (@Service, @Transactional)
controller   → controllers JavaFX (@Component, @FXML)
config       → UserSession, beans de apoio
```

Dependência: `controller → service → repository → model`. Controller nunca fala
direto com repository.

### Padrões JPA
- Relação: `@ManyToOne` + `@JoinColumn` no lado "muitos" (lado que tem a FK).
- `@OneToMany(mappedBy=..., cascade=ALL, orphanRemoval=true)` no lado "um".
- Enum: `@Enumerated(EnumType.STRING)`.
- snake_case no banco / camelCase no Java (Hibernate converte).

### JavaFX
- `FXMLLoader` + `setControllerFactory(BetaApplication.springContext::getBean)`
  para o Spring injetar o controller.
- Navegação de tela: **shell único persistente** (`main_layout.fxml` /
  `MainController` — ToolBar + menu lateral + barra de status fixos). Os
  controllers de página trocam só o centro via `Navigator.navigate(Page)`
  (`config/Navigator` + enum `config/Page`), **não** `stage.setScene(...)`. O
  login fica fora do shell; a transição login→sistema usa `scene.setRoot(...)`.
  (ADR 0012)
- `UserSession` (@Component em config/) guarda operador logado + caixa atual.

---

## 4. Regras invioláveis (segurança / legal)

1. **Dinheiro = `BigDecimal`**, nunca `double`/`float`. `NUMERIC(10,2)` no banco,
   `NUMERIC(10,3)` para peso. Arredondamento `HALF_UP`. (ADR 0003)
2. **Código de barras = `String`** (tem zero à esquerda, não é número).
3. **Senha nunca em texto puro** — sempre hash BCrypt via `UserService`. Nunca
   inserir usuário por SQL cru (fura o hash).
4. **Nunca** criar "venda sem nota" / "caixa 2" / qualquer recurso de sonegação.
   É crime (Lei 8.137/90) e pode implicar o dev. Se pedirem, recuse.
5. **RN19 — preço congelado:** ao adicionar item, copiar o preço do produto para
   `unitPrice` do item. Mudança de preço depois não altera vendas passadas.
6. **`@Transactional`** em operações que mexem em mais de uma tabela (ex.:
   finalizar venda baixa estoque).
7. **Código em inglês** (classes, campos, métodos, tabelas). Mensagens ao usuário
   e docs em português. Termos de domínio BR (sangria, suprimento) podem ficar.
   (ADR 0008)
8. **Nunca commitar** `.env`, senhas ou `target/` no Git.
9. **Flyway é a fonte do schema**, Hibernate em `ddl-auto=validate`. Migration
   aplicada não se edita nem renomeia — mudança = nova migration. (ADR 0005)

---

## 5. Regras de negócio

- **RN07** — adicionar produto já presente na venda soma na quantidade, não cria
  item duplicado.
- **RN10** — finalizar venda baixa o estoque (`@Transactional`).
- **RN13/14/15/16** — regras de caixa (sangria, suprimento, saldo esperado,
  fechamento com diferença).
- **RN19** — preço congelado no item (ver regra 5 acima).
- **RN21** — PDV como máquina de estados: cada ação só vale em certos estados;
  validação na UI (desabilita botão) **e** no service (recusa com exceção). UI
  nunca substitui validação do service. (ADR 0010)

---

## 6. O que já está feito

- **Product**: entidade, migration V1, service + repository, testado.
- **User**: entidade (`@Table(name="users")`), migration V2, login com BCrypt,
  testado.
- **CashRegister**: abrir/sangria/suprimento/fechar/saldo, migrations V3/V4,
  testado.
- **Sale / SaleItem**: entidades, migrations V5/V6, service (openSale, addItem
  com RN07, finalizeSale com RN10), controller com TableView. **Testado**
  (`SaleServiceTests`, 8 casos).
- UI básica: login, caixa (abrir/fechar), venda (bipar/finalizar). Navegação
  caixa→venda após abrir.

---

## 7. O que falta / roadmap

1. ~~Teste unitário do `SaleService`.~~ **Concluído** (`SaleServiceTests`, 2026-10-17).
2. ~~Recuperar caixa aberto ao reabrir app.~~ **Concluído** (2026-10-17, ver abaixo).
3. Design / CSS (telas não parecem PDV de mercado ainda).
4. Impressão de cupom + abertura de gaveta (escpos-coffee).
5. NFC-e (fase 3).
6. Pagamento integrado Mercado Pago Point via API + polling (ADR 0007, fase 4).

### Bug resolvido (2026-10-17)
Recuperação do caixa aberto ao reabrir o app. No login,
`LoginController.onLogin()` chama `cashRegisterService.findOpenByOperator(user)`
(repo `findByOperatorAndStatus(operator, OPEN)`) e põe o resultado na sessão
(`setCaixaAtual(... .orElse(null))`), limpando `caixaAtual` quando não há caixa
aberto. Backend coberto por 4 testes em `CashRegisterServiceTests`. **Ressalva:**
a fiação no `LoginController` é UI (JavaFX), sem teste automatizado — validar
manualmente com `mvn javafx:run`.

---

## 8. Como o dev trabalha (importante para a IA)

O dev está **aprendendo** e quer fazer tudo por conta própria. Ao tirar dúvidas,
ele prefere **explicações e exemplos de implementação**, não código pronto do
módulo inteiro. Não cole módulos gerados por IA num sistema de dinheiro sem ele
entender cada linha. Respostas **concisas e objetivas**.

---

## 9. Arquivos companheiros

- **TASK.md** — o que o dev descreve para a IA fazer na rodada atual.
- **REVISION.md** — melhorias e correções da última task; normalmente concertos
  mais longos.
- **FIXES.md** — concertos mais curtos e pontuais.

Fluxo sugerido: dev escreve em TASK.md → IA executa → o que sobrar/quebrar vai
para REVISION.md (longo) ou FIXES.md (curto) na próxima rodada.

---

## 10. Time de agentes (como a IA desenvolve)

O desenvolvimento é feito por um **time de agentes**. A **sessão principal atua
como Coordenador**: lê a TASK, mantém o mapa de requisitos e despacha o trabalho
para subagentes especialistas. No Claude Code um subagente não chama outro — a
orquestração é sempre do Coordenador.

- **Playbook do Coordenador:** `.claude/COORDENADOR.md` (fluxo da rodada,
  guardrails e checklist de "pronto"). Leia antes de iniciar uma rodada.
- **Mapa de requisitos (fonte única):** `MAPA-REQUISITOS.md` (RF/RN/RNF
  reconciliados, status e fases). Escopo atual = **Fase 1 (F1)**.
- **Subagentes** (`.claude/agents/`): `arquiteto` (planeja), `backend`
  (model/service/migration), `frontend` (JavaFX/telas), `hardware`
  (gaveta/impressora/maquininha, com mocks), `qa` (testes de borda + RN),
  `seguranca` (BCrypt/autorização/legal), `documentador` (docs/ADRs).

**"Pronto" por tarefa:** compila + `mvn test` verde (bordas + RN) + docs
atualizados, sempre respeitando as regras invioláveis da seção 4.
