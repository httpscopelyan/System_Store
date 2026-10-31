# Guia de uso — novidades do PDV

Plano de 2026-10-31 (`TASKs/IMPLEMENTATION-PLAN.md`).

**Avisos de estoque por e-mail**
1. Em *Config → Avisos de estoque* informe a margem de aviso, os e-mails (separados por vírgula), o servidor
   SMTP, a porta, o usuário e o remetente.
2. Coloque a senha do SMTP no arquivo `.env`, na linha `SMTP_PASSWORD=sua-senha`. Ela nunca é gravada no banco.
3. Use *Testar envio*. A aba *Lista d/ Compras* também tem *Enviar por e-mail agora*.

**Venda**
- Digite ou bipe o código (ou parte do nome) e tecle Enter: o foco vai para *Qtd*; digite a quantidade e Enter.
- `5*código` continua funcionando. Esc na quantidade cancela.
- Com 2+ letras ou dígitos aparece a lista de sugestões: setas, Tab/Enter ou mouse escolhem.
- Produto em KG troca o campo para *Peso (KG)* e mostra o valor antes de confirmar.

**Estoque (edição em lote)**
- Clique numa célula e use Shift+clique (faixa) ou Ctrl+clique (individual); F2 ou Enter editam e o valor vale
  para todas as células selecionadas da mesma coluna (pede confirmação). Preço, custo e lucro exigem gerente.
- A alça `⋮` no início da linha seleciona o produto inteiro.
- *Aceitar Sugestão* (com células de *Vr. Venda* selecionadas) aplica o preço sugerido pelo custo e margem padrão.

**Caixa**
- Sangria e suprimento exigem motivo. Para corrigir um erro use *Retificar* (pede gerente e motivo; o registro
  original continua visível).

**Testes**
- `mvn test` precisa do Docker `db_scopel` e de uma sessão com tela (os testes de tela usam JavaFX real). Em
  servidor sem tela use `-DargLine=-Djava.awt.headless=true` para pular esses testes.
