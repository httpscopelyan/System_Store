package br.com.scopel.pfv.beta.web;

public final class AdminPageLayout {

    public static final String STYLESHEET = """
            :root { --bg:#f4f5f7; --card:#fff; --text:#1d2430; --muted:#5d6878; --line:#d9dee6; --brand:#1f5fbf;
                    --ok:#1b7a43; --danger:#b3261e; }
            @media (prefers-color-scheme: dark) {
              :root { --bg:#14181f; --card:#1d232d; --text:#e8ecf2; --muted:#9aa6b6; --line:#2d3644; --brand:#6ea3ff;
                      --ok:#52c283; --danger:#ff7b72; }
            }
            * { box-sizing: border-box; }
            body { margin:0; font:15px/1.45 system-ui, "Segoe UI", sans-serif; background:var(--bg); color:var(--text); }
            header { background:var(--card); border-bottom:1px solid var(--line); padding:10px 16px; display:flex;
                     flex-wrap:wrap; gap:8px 16px; align-items:center; }
            header strong { margin-right:8px; }
            nav { display:flex; flex-wrap:wrap; gap:4px; flex:1; }
            nav a { padding:6px 12px; border-radius:6px; color:var(--text); text-decoration:none; }
            nav a.active, nav a:hover { background:var(--brand); color:#fff; }
            main { padding:16px; max-width:1500px; margin:0 auto; }
            h1 { font-size:20px; margin:0 0 12px; }
            h2 { font-size:16px; margin:0 0 8px; }
            .card { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:14px; margin-bottom:14px; }
            .flash { padding:10px 14px; border-radius:8px; margin-bottom:14px; border:1px solid var(--line); background:var(--card); }
            .flash.error { border-color:var(--danger); color:var(--danger); }
            .flash.ok { border-color:var(--ok); color:var(--ok); }
            form.inline { display:flex; flex-wrap:wrap; gap:8px; align-items:end; }
            label { display:flex; flex-direction:column; gap:3px; font-size:13px; color:var(--muted); }
            label.check { flex-direction:row; align-items:center; gap:6px; color:var(--text); font-size:15px; }
            input[type=text], input[type=password], input[type=date], select { padding:7px 9px; border:1px solid var(--line);
                border-radius:6px; background:var(--bg); color:var(--text); font:inherit; min-width:150px; }
            button { padding:7px 14px; border:0; border-radius:6px; background:var(--brand); color:#fff; font:inherit; cursor:pointer; }
            button.danger { background:var(--danger); }
            button.quiet { background:transparent; color:var(--text); border:1px solid var(--line); }
            .table-wrap { overflow:auto; border:1px solid var(--line); border-radius:8px; background:var(--card); }
            table { border-collapse:collapse; width:100%; font-size:13px; }
            th, td { padding:6px 10px; text-align:left; border-bottom:1px solid var(--line); white-space:nowrap; }
            th { position:sticky; top:0; background:var(--card); }
            td.wrap { white-space:normal; min-width:240px; }
            .muted { color:var(--muted); }
            .pager { display:flex; gap:12px; align-items:center; margin-top:10px; }
            .pager a { color:var(--brand); }
            .grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(320px, 1fr)); gap:14px; }
            .login { max-width:360px; margin:12vh auto; }
            @media (max-width:600px) { main { padding:10px; } input[type=text], input[type=password], select { min-width:0; width:100%; } }
            """;

    private AdminPageLayout() {
    }

    public static String page(String title, String activeNav, String csrfToken, String flash, boolean flashError,
                              String body) {
        StringBuilder html = new StringBuilder(body.length() + 2048);
        html.append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<meta name=\"robots\" content=\"noindex, nofollow\">")
                .append("<title>").append(HtmlEscape.of(title)).append(" — Administração</title>")
                .append("<link rel=\"stylesheet\" href=\"/admin.css\"></head><body>");
        if (csrfToken != null) {
            html.append("<header><strong>SCOPEL STORE · Administração</strong><nav>")
                    .append(link("/history", "Histórico", activeNav))
                    .append(link("/permissions", "Permissões", activeNav))
                    .append(link("/accounts", "Contas", activeNav))
                    .append(link("/cash-groups", "Caixa unificado", activeNav))
                    .append("</nav><form method=\"post\" action=\"/logout\">")
                    .append(csrfField(csrfToken)).append("<button class=\"quiet\">Sair</button></form></header>");
        }
        html.append("<main>");
        if (flash != null && !flash.isBlank()) {
            html.append("<div class=\"flash ").append(flashError ? "error" : "ok").append("\">")
                    .append(HtmlEscape.of(flash)).append("</div>");
        }
        html.append("<h1>").append(HtmlEscape.of(title)).append("</h1>").append(body).append("</main></body></html>");
        return html.toString();
    }

    public static String csrfField(String csrfToken) {
        return "<input type=\"hidden\" name=\"_csrf\" value=\"" + HtmlEscape.of(csrfToken) + "\">";
    }

    private static String link(String href, String label, String activeNav) {
        return "<a href=\"" + href + "\"" + (href.equals(activeNav) ? " class=\"active\"" : "") + ">" + label + "</a>";
    }
}
