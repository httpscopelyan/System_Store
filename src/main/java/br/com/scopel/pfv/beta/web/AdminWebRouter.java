package br.com.scopel.pfv.beta.web;

import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.admin.AdminAccountService;
import br.com.scopel.pfv.beta.service.admin.AdminCashGroupService;
import br.com.scopel.pfv.beta.service.admin.AdminHistoryService;
import br.com.scopel.pfv.beta.service.admin.HistoryPage;
import br.com.scopel.pfv.beta.service.admin.HistoryTab;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

@Component
public class AdminWebRouter {

    private final AdminHistoryService historyService;
    private final AdminAccountService accountService;
    private final AdminCashGroupService cashGroupService;

    public AdminWebRouter(AdminHistoryService historyService, AdminAccountService accountService,
                          AdminCashGroupService cashGroupService) {
        this.historyService = historyService;
        this.accountService = accountService;
        this.cashGroupService = cashGroupService;
    }

    public WebResponse route(WebRequest request, WebSessionStore.WebSession session, User actor) {
        boolean post = "POST".equals(request.method());
        return switch (request.path()) {
            case "/" -> WebResponse.redirect("/history");
            case "/history" -> history(request, session, actor);
            case "/permissions" -> post ? action(session, "/permissions", () -> savePermissions(request, actor))
                    : permissions(session, actor);
            case "/accounts" -> accounts(session, actor);
            case "/accounts/create" -> post ? action(session, "/accounts", () -> {
                accountService.create(actor, request.param("name"), request.param("password"),
                        roleOf(request.param("role")));
                return "Conta criada.";
            }) : notFound();
            case "/accounts/role" -> post ? action(session, "/accounts", () -> {
                accountService.changeRole(actor, request.longParam("userId"), roleOf(request.param("role")));
                return "Perfil alterado. Revise as permissões da conta.";
            }) : notFound();
            case "/accounts/password" -> post ? action(session, "/accounts", () -> {
                accountService.resetPassword(actor, request.longParam("userId"), request.param("password"));
                return "Senha redefinida.";
            }) : notFound();
            case "/accounts/active" -> post ? action(session, "/accounts", () -> {
                boolean active = Boolean.parseBoolean(request.param("active"));
                accountService.setActive(actor, request.longParam("userId"), active);
                return active ? "Conta reativada." : "Conta inativada.";
            }) : notFound();
            case "/accounts/delete" -> post ? action(session, "/accounts", () -> {
                if (!"yes".equals(request.param("confirm"))) {
                    throw new IllegalArgumentException("Marque a confirmação para excluir.");
                }
                accountService.delete(actor, request.longParam("userId"));
                return "Conta excluída.";
            }) : notFound();
            case "/cash-groups" -> cashGroups(session, actor);
            case "/cash-groups/save" -> post ? action(session, "/cash-groups", () -> {
                cashGroupService.save(actor, request.longParam("groupId"), request.param("name"),
                        longSet(request.params("member")));
                return "Caixa unificado salvo.";
            }) : notFound();
            case "/cash-groups/dissolve" -> post ? action(session, "/cash-groups", () -> {
                cashGroupService.dissolve(actor, request.longParam("groupId"));
                return "Caixa unificado desfeito.";
            }) : notFound();
            default -> notFound();
        };
    }

    private WebResponse history(WebRequest request, WebSessionStore.WebSession session, User actor) {
        HistoryTab tab = HistoryTab.AUDIT;
        try {
            tab = HistoryTab.valueOf(request.param("tab"));
        } catch (IllegalArgumentException keepDefault) {
        }
        LocalDate since = null;
        try {
            since = request.param("since").isBlank() ? null : LocalDate.parse(request.param("since"));
        } catch (RuntimeException invalidDate) {
            session.flash("err:Data inválida; mostrando tudo.");
        }
        int pageNumber = request.longParam("page") == null ? 0 : (int) Math.min(request.longParam("page"), 100_000);
        String search = request.param("q");
        try {
            HistoryPage page = historyService.page(actor, tab, since == null || !tab.timed() ? null : since.atStartOfDay(),
                    search, pageNumber);
            return render(session, "/history", "Histórico — " + tab.title(), AdminHistoryPage.render(page, since, search));
        } catch (RuntimeException failure) {
            session.flash("err:Não foi possível carregar o histórico.");
            return render(session, "/history", "Histórico", "");
        }
    }

    private WebResponse permissions(WebSessionStore.WebSession session, User actor) {
        return render(session, "/permissions", "Permissões por conta",
                AdminManagementPages.permissions(accountService.accounts(actor), session.csrfToken()));
    }

    private WebResponse accounts(WebSessionStore.WebSession session, User actor) {
        return render(session, "/accounts", "Contas e acessos",
                AdminManagementPages.accounts(accountService.accounts(actor), session.csrfToken()));
    }

    private WebResponse cashGroups(WebSessionStore.WebSession session, User actor) {
        return render(session, "/cash-groups", "Caixa unificado",
                AdminManagementPages.cashGroups(cashGroupService.groups(actor), accountService.accounts(actor),
                        session.csrfToken()));
    }

    private String savePermissions(WebRequest request, User actor) {
        Set<Permission> granted = new HashSet<>();
        for (String name : request.params("permission")) {
            granted.add(Permission.valueOf(name));
        }
        accountService.setPermissions(actor, request.longParam("userId"), granted);
        return "Permissões salvas. Valem no próximo login da pessoa.";
    }

    private WebResponse action(WebSessionStore.WebSession session, String backTo, Supplier<String> work) {
        try {
            session.flash("ok:" + work.get());
        } catch (RuntimeException failure) {
            session.flash("err:" + (failure.getMessage() == null ? "Não foi possível concluir a operação."
                    : failure.getMessage()));
        }
        return WebResponse.redirect(backTo);
    }

    private WebResponse render(WebSessionStore.WebSession session, String nav, String title, String body) {
        String flash = session.takeFlash();
        boolean error = flash != null && flash.startsWith("err:");
        String shown = flash == null ? null : flash.replaceFirst("^(ok|err):", "");
        return WebResponse.html(AdminPageLayout.page(title, nav, session.csrfToken(), shown, error, body));
    }

    private WebResponse notFound() {
        return WebResponse.text(404, "Não encontrado");
    }

    private User.Role roleOf(String name) {
        try {
            return User.Role.valueOf(name);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Perfil inválido.");
        }
    }

    private Set<Long> longSet(java.util.List<String> values) {
        Set<Long> numbers = new HashSet<>();
        for (String value : values) {
            try {
                numbers.add(Long.parseLong(value.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return numbers;
    }
}
