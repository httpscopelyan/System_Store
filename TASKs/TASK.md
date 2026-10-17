# TASK.md

> Leia `CLAUDE.md` antes de começar (regras invioláveis, arquitetura, como o dev
> trabalha). Esta task é **só de UI/navegação** — não mexa em regra de negócio,
> service nem migration.

## Objetivo
Criar a **tela inicial (menu)** do PDV em JavaFX, copiando o layout da imagem de
referência anexa, mas contendo **apenas as funções que já existem no sistema**.
A tela de login **não** faz parte desta task (o dev faz).

## Dependência
- **Anexo:** `inicio-referencia.png` — tela inicial do sistema Yzidro ERP. Use-a
  como referência de **layout e disposição** (não de cores nem de marca; a
  identidade é "Mercado Scopel"). Estrutura a copiar:
  1. **Barra superior de ícones** (ToolBar horizontal) com botões de acesso rápido.
  2. **Menu lateral esquerdo** (lista/árvore de funções, agrupada).
  3. **Área central** grande com a logo/marca da loja ao fundo.
  4. **Barra de status inferior**: data/hora · usuário · grupo (papel) · empresa ·
     terminal (ex.: `CAIXA 01`).

## Contexto do sistema (o que existe hoje)
Telas/fluxos já prontos (FXML + controllers Spring):
- `sale.fxml` → frente de caixa (venda).
- `caixa.fxml` → abertura/fechamento de caixa (sangria/suprimento).
- `create_product.fxml` → cadastro de produto.

Regras relevantes para a navegação:
- Só é possível **vender com um caixa aberto** (RN21 — máquina de estados). O
  `UserSession` guarda `loggedUser` e `caixaAtual`.
- Papéis: `OPERADOR` e `GERENTE` (`loggedUser.getRole()`).

## Passos / requisitos
1. Criar `inicio.fxml` + `InicioController` (anotado com `@Component`, injetado
   via `setControllerFactory(BetaApplication.springContext::getBean)` no
   FXMLLoader, como os outros controllers). Criar/estender o `styles.css`.
2. Layout em **BorderPane**:
   - **top:** `ToolBar` com botões-ícone para as funções existentes
     (**Vender**, **Caixa**, **Produtos**). Mantenha o visual de barra de ícones
     da referência; **não** crie ícones que levem a telas inexistentes.
   - **left:** menu vertical (VBox de botões ou TreeView) agrupado:
     - **Operação:** Vender, Caixa
     - **Cadastros:** Produtos
     - **(desabilitados/placeholder, para depois):** Usuários, Relatórios — exiba
       como desabilitados (opacos), sem ação.
   - **center:** `StackPane` com a logo/nome "Mercado Scopel" ao fundo e um texto
     de status (ex.: "Caixa aberto" / "Caixa fechado").
   - **bottom:** barra de status com data/hora, nome do usuário logado, papel e
     terminal, lidos do `UserSession`.
3. **Ações dos botões:**
   - **Vender:** se houver caixa aberto (`userSession.getCaixaAtual() != null`),
     navega para `sale.fxml`. Se não houver, navega para `caixa.fxml` (abrir caixa
     primeiro). Não duplique a validação de negócio — apenas roteie a tela.
   - **Caixa:** navega para `caixa.fxml`.
   - **Produtos:** navega para `create_product.fxml`.
4. Navegação de tela com o padrão já usado no projeto:
   `Stage stage = (Stage) algumCampo.getScene().getWindow(); stage.setScene(novaCena);`
5. **Visibilidade por papel:** o grupo/botão **Usuários** só aparece para
   `GERENTE`. Para `OPERADOR`, oculte-o.
6. Atalhos de teclado (opcional, se simples): F2 = Vender, F3 = Caixa,
   F4 = Produtos.

## Restrições
- Respostas concisas e objetivas. Explique o que fez; **não** reescreva services,
  migrations ou regras de negócio.
- Código em inglês (classes, campos, métodos); textos de UI em português.
- Controllers são beans Spring — nada de `new Controller()`.
- Não invente telas: só os 3 destinos que já existem (`sale`, `caixa`,
  `create_product`). Usuários/Relatórios ficam como placeholder desabilitado.
- Rodar com `mvn javafx:run`.

## Critério de pronto
- App abre na `inicio.fxml` após o login, com barra de ícones, menu lateral,
  área central com a marca e barra de status preenchida com os dados do
  `UserSession`.
- Os botões Vender / Caixa / Produtos navegam corretamente.
- Vender sem caixa aberto leva para a tela de caixa; com caixa aberto, leva para a
  venda.
- Usuários/Relatórios aparecem desabilitados; Usuários some para OPERADOR.
