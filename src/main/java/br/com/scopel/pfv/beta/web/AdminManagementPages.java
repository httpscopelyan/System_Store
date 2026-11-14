package br.com.scopel.pfv.beta.web;

import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.admin.AdminAccount;
import br.com.scopel.pfv.beta.service.admin.CashGroupView;

import java.util.List;

public final class AdminManagementPages {

    private AdminManagementPages() {
    }

    public static String login() {
        return "<form class=\"card login\" method=\"post\" action=\"/login\" autocomplete=\"off\">"
                + "<label>Usuário<input type=\"text\" name=\"login\" required autofocus></label><br>"
                + "<label>Senha<input type=\"password\" name=\"password\" required></label><br>"
                + "<button>Entrar</button></form>";
    }

    public static String permissions(List<AdminAccount> accounts, String csrf) {
        StringBuilder html = new StringBuilder("<p class=\"muted\">As alterações valem no próximo login da pessoa.</p><div class=\"grid\">");
        for (AdminAccount account : accounts) {
            html.append("<form class=\"card\" method=\"post\" action=\"/permissions\">")
                    .append(AdminPageLayout.csrfField(csrf))
                    .append("<input type=\"hidden\" name=\"userId\" value=\"").append(account.id()).append("\">")
                    .append("<h2>").append(HtmlEscape.of(account.name())).append(" <span class=\"muted\">(")
                    .append(account.role()).append(account.active() ? "" : ", inativa").append(")</span></h2>");
            for (Permission permission : Permission.values()) {
                boolean granted = account.protectedAccount() || account.permissions().contains(permission);
                html.append("<label class=\"check\"><input type=\"checkbox\" name=\"permission\" value=\"")
                        .append(permission.name()).append("\"").append(granted ? " checked" : "")
                        .append(account.protectedAccount() ? " disabled" : "").append("> ")
                        .append(HtmlEscape.of(permission.label())).append("</label>");
            }
            html.append(account.protectedAccount()
                    ? "<p class=\"muted\">Conta DEV: acesso total.</p>"
                    : "<br><button>Salvar permissões</button>");
            html.append("</form>");
        }
        return html.append("</div>").toString();
    }

    public static String accounts(List<AdminAccount> accounts, String csrf) {
        StringBuilder html = new StringBuilder();
        html.append("<form class=\"card inline\" method=\"post\" action=\"/accounts/create\">")
                .append(AdminPageLayout.csrfField(csrf))
                .append("<h2 style=\"width:100%\">Nova conta</h2>")
                .append("<label>Nome (usado para entrar)<input type=\"text\" name=\"name\" maxlength=\"30\" required></label>")
                .append("<label>Senha (mín. 6)<input type=\"password\" name=\"password\" minlength=\"6\" required></label>")
                .append("<label>Perfil<select name=\"role\"><option>OPERADOR</option><option>GERENTE</option></select></label>")
                .append("<button>Criar conta</button></form>");
        html.append("<div class=\"grid\">");
        for (AdminAccount account : accounts) {
            html.append("<div class=\"card\"><h2>").append(HtmlEscape.of(account.name()))
                    .append(" <span class=\"muted\">(").append(account.role())
                    .append(account.active() ? "" : ", inativa").append(")</span></h2>")
                    .append("<p class=\"muted\">Caixa unificado: ")
                    .append(account.cashGroupName() == null ? "—" : HtmlEscape.of(account.cashGroupName())).append("</p>");
            html.append(passwordForm(account, csrf));
            if (!account.protectedAccount()) {
                html.append(roleForm(account, csrf)).append(activeForm(account, csrf)).append(deleteForm(account, csrf));
            }
            html.append("</div>");
        }
        return html.append("</div>").toString();
    }

    public static String cashGroups(List<CashGroupView> groups, List<AdminAccount> accounts, String csrf) {
        StringBuilder html = new StringBuilder("<p class=\"muted\">As contas marcadas compartilham o mesmo caixa. "
                + "Cada conta participa de um só caixa unificado.</p><div class=\"grid\">");
        html.append(groupForm(null, accounts, csrf));
        for (CashGroupView group : groups) {
            html.append(groupForm(group, accounts, csrf));
        }
        return html.append("</div>").toString();
    }

    private static String groupForm(CashGroupView group, List<AdminAccount> accounts, String csrf) {
        StringBuilder html = new StringBuilder("<div class=\"card\"><form method=\"post\" action=\"/cash-groups/save\">")
                .append(AdminPageLayout.csrfField(csrf));
        if (group != null) {
            html.append("<input type=\"hidden\" name=\"groupId\" value=\"").append(group.id()).append("\">");
        }
        html.append("<h2>").append(group == null ? "Novo caixa unificado" : HtmlEscape.of(group.name())).append("</h2>")
                .append("<label>Nome<input type=\"text\" name=\"name\" maxlength=\"60\" required value=\"")
                .append(group == null ? "" : HtmlEscape.of(group.name())).append("\"></label><br>");
        for (AdminAccount account : accounts) {
            if (!account.active()) {
                continue;
            }
            boolean member = group != null && group.memberIds().contains(account.id());
            boolean elsewhere = account.cashGroupId() != null && (group == null || !account.cashGroupId().equals(group.id()));
            html.append("<label class=\"check\"><input type=\"checkbox\" name=\"member\" value=\"").append(account.id())
                    .append("\"").append(member ? " checked" : "").append("> ").append(HtmlEscape.of(account.name()))
                    .append(elsewhere ? " <span class=\"muted\">(hoje em: " + HtmlEscape.of(account.cashGroupName()) + ")</span>" : "")
                    .append("</label>");
        }
        html.append("<br><button>Salvar</button></form>");
        if (group != null) {
            html.append("<form method=\"post\" action=\"/cash-groups/dissolve\" style=\"margin-top:8px\">")
                    .append(AdminPageLayout.csrfField(csrf))
                    .append("<input type=\"hidden\" name=\"groupId\" value=\"").append(group.id()).append("\">")
                    .append("<button class=\"danger\">Desfazer este caixa unificado</button></form>");
        }
        return html.append("</div>").toString();
    }

    private static String passwordForm(AdminAccount account, String csrf) {
        return "<form class=\"inline\" method=\"post\" action=\"/accounts/password\">" + AdminPageLayout.csrfField(csrf)
                + hidden(account) + "<label>Nova senha<input type=\"password\" name=\"password\" minlength=\"6\" required></label>"
                + "<button class=\"quiet\">Redefinir senha</button></form>";
    }

    private static String roleForm(AdminAccount account, String csrf) {
        String operador = account.role() == User.Role.OPERADOR ? " selected" : "";
        String gerente = account.role() == User.Role.GERENTE ? " selected" : "";
        return "<form class=\"inline\" style=\"margin-top:8px\" method=\"post\" action=\"/accounts/role\">"
                + AdminPageLayout.csrfField(csrf) + hidden(account)
                + "<label>Perfil<select name=\"role\"><option" + operador + ">OPERADOR</option><option" + gerente
                + ">GERENTE</option></select></label><button class=\"quiet\">Mudar perfil</button></form>";
    }

    private static String activeForm(AdminAccount account, String csrf) {
        return "<form class=\"inline\" style=\"margin-top:8px\" method=\"post\" action=\"/accounts/active\">"
                + AdminPageLayout.csrfField(csrf) + hidden(account)
                + "<input type=\"hidden\" name=\"active\" value=\"" + !account.active() + "\">"
                + "<button class=\"quiet\">" + (account.active() ? "Inativar conta" : "Reativar conta") + "</button></form>";
    }

    private static String deleteForm(AdminAccount account, String csrf) {
        return "<form class=\"inline\" style=\"margin-top:8px\" method=\"post\" action=\"/accounts/delete\">"
                + AdminPageLayout.csrfField(csrf) + hidden(account)
                + "<label class=\"check\"><input type=\"checkbox\" name=\"confirm\" value=\"yes\" required> confirmo a exclusão</label>"
                + "<button class=\"danger\">Excluir</button></form>";
    }

    private static String hidden(AdminAccount account) {
        return "<input type=\"hidden\" name=\"userId\" value=\"" + account.id() + "\">";
    }
}
