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
- **Fechamento (1.2.21):** não há mais botão único. No painel *Conferência do Fechamento* (role a coluna da
  direita) o sistema mostra o *Esperado* de Dinheiro, PIX, Crédito e Débito; o operador digita o *Contado* de cada
  um (0 se não houve) e vê a diferença na hora, em vermelho quando não confere. Só então clica em *Fechar Caixa*.
  O cupom do fechamento sai sozinho, com esperado | contado | diferença por forma. *Imprimir Movimentação (F7)*
  imprime todas as entradas e saídas do caixa mostrado.
- **Reabertura da Dayana (1.2.21):** na primeira abertura da 1.2.21 o último caixa fechado da Dayana é reaberto com
  todas as movimentações. O fechamento anterior fica salvo em `backups/caixa-<id>-fechamento-com-erro-*.txt` (pasta do
  programa) e na tabela `cash_register_reopening`. Roda uma única vez.

**Conta DEV e Administração (Yan)**
- A conta *Yan* tem o perfil **DEV**: usa o PDV normal e, no menu *Mais*, o bloco **Administração** (só a conta DEV vê).
- *Histórico*: escolha a tabela (auditoria geral, vendas, itens vendidos, pagamentos, alterações de estoque, caixas,
  movimentações de caixa, cobranças, reaberturas, compras, contas a pagar/receber, produtos, clientes, fornecedores),
  filtre por data e busque por texto. Mostra quem fez, quando, o quê e os valores. 100 linhas por página.
- *Permissões*: escolha a conta e marque o que ela pode (cadastrar produtos, ver custo/margem/estoque mínimo no cadastro,
  editar dados dos produtos, alterar preços, alterar quantidade em estoque). *Restaurar padrão do perfil* recarrega o
  padrão. Vale no próximo login da pessoa.
- *Contas*: criar (senha mín. 6), mudar perfil (Operador/Gerente), redefinir senha, inativar/reativar e excluir. Conta
  com histórico só pode ser inativada (o banco impede a exclusão). A conta DEV não pode ser limitada nem removida.
- *Caixa unificado*: crie um grupo com 2+ contas (ex.: Ana + Dayana, ou Dayana + Lígia) para dividirem o mesmo caixa;
  uma conta participa de um grupo só. *Desfazer* devolve a cada conta o caixa próprio.
- **Ana Clara no cadastro de produto** vê só: código, nome, preço de venda, unidade, quantidade, categoria e situação.
  Custo, margem e estoque mínimo ficam ocultos (e o serviço recusa se vierem preenchidos).

**Painel de administração pela web**
- Desligado por padrão. No `.env`: `ADMIN_WEB_ENABLED=true` (opcional `ADMIN_WEB_PORT=8443`) e reinicie o sistema. A aba
  *Acesso web* da Administração mostra o endereço. Só a conta DEV entra (usuário e senha); sessão expira em 30 min sem uso;
  5 senhas erradas bloqueiam o endereço por 15 min; toda entrada e falha ficam na auditoria.
- Por padrão escuta só em `127.0.0.1` (este computador). Para a rede da loja use `ADMIN_WEB_BIND=0.0.0.0`.
- **Pela internet:** use HTTPS. Opção 1 (recomendada): túnel com HTTPS (Cloudflare Tunnel ou Tailscale Funnel) apontando
  para `http://127.0.0.1:8443`, com `ADMIN_WEB_SECURE_COOKIE=true` e `ADMIN_WEB_TRUST_FORWARDED_FOR=true`. Opção 2: certificado
  `.p12` em `ADMIN_WEB_KEYSTORE` + `ADMIN_WEB_KEYSTORE_PASSWORD` (a porta passa a ser HTTPS). Nunca abra a porta em HTTP puro
  no roteador: a senha da conta DEV trafegaria sem proteção.

**Testes**
- `mvn test` precisa do Docker `db_scopel` e de uma sessão com tela (os testes de tela usam JavaFX real). Em
  servidor sem tela use `-DargLine=-Djava.awt.headless=true` para pular esses testes.
