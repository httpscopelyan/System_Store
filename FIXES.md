# FIXES — correções curtas e pontuais

Registro de concertos curtos por rodada. Correções longas/estruturais ficam em
`REVISION.md`.

---

## 2026-10-17

- **Arredondamento monetário no `SaleService` (RN01):** `addItem` (nos dois ramos —
  item novo e item já presente/agrupado) e `calculateTotal` passaram a aplicar
  `setScale(2, RoundingMode.HALF_UP)`, garantindo dinheiro com 2 casas e
  arredondamento HALF_UP. Revisado e aprovado pela Segurança. Coberto por
  `SaleServiceTests`.
- **Isolamento de testes data-dependentes:** `UserServiceTests` e
  `BetaApplicationTests` gravavam dados fixos no `db_scopel` e quebravam em
  re-execuções. Adicionado `@Transactional` (rollback por teste) + logins/códigos
  de produto únicos via UUID. Apenas classes de teste alteradas; os services
  (`UserService`, `ProductService`) continuam lançando em duplicado, como esperado.
  Suíte 100% verde: `mvn test` (JDK 26) → 23 testes, 0 falhas, 0 erros.
- **Recuperação do caixa aberto no login (bug "caixa aberto não recuperado ao
  reabrir o app"):** novo `CashRegisterService.findOpenByOperator(User)` →
  `Optional<CashRegister>`, delegando ao repo já existente
  `findByOperatorAndStatus(operator, OPEN)`. No `LoginController.onLogin()`, após o
  login, `userSession.setCaixaAtual(cashRegisterService.findOpenByOperator(loggedUser).orElse(null))`
  recupera o caixa OPEN do operador e limpa `caixaAtual` quando não há (evita
  herdar caixa de login anterior no singleton `UserSession`). Coberto por 4 testes
  em `CashRegisterServiceTests` (presente, vazio, pós-fechamento, isolamento por
  operador RN13). Suíte verde: `mvn test` (JDK 26) → 27 testes, 0 falhas, 0 erros.
  **Ressalva:** a fiação no `LoginController` é UI (JavaFX) e não tem teste
  automatizado — validar manualmente via `mvn javafx:run` (logar com operador que
  tem caixa OPEN e confirmar que a venda reconhece o caixa).
- **`SaleService.finalizeSale` tornado privado (fecha atalho de finalização sem
  pagamento):** resolve a dívida técnica apontada pela Segurança. `finalizeSale`
  deixou de ser público e agora é chamado **apenas por `pay`**, que grava o
  `Payment` antes de finalizar e baixar estoque — não resta caminho público que
  finalize venda sem `Payment`. O teste que chamava `finalizeSale` direto foi
  convertido para a via pública (`openSale → addItem → goToPayment → pay`),
  preservando a cobertura de baixa de estoque (UN + KG). Nenhuma classe de produção
  além do `SaleService` foi tocada. Suíte verde: `mvn test` (JDK 26) → 36 testes,
  0 falhas, 0 erros.
