package br.com.scopel.pfv.beta.web;

import br.com.scopel.pfv.beta.service.admin.HistoryPage;
import br.com.scopel.pfv.beta.service.admin.HistoryTab;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public final class AdminHistoryPage {

    private AdminHistoryPage() {
    }

    public static String render(HistoryPage page, LocalDate since, String search) {
        StringBuilder html = new StringBuilder(16 * 1024);
        html.append("<form class=\"card inline\" method=\"get\" action=\"/history\"><label>Histórico<select name=\"tab\">");
        for (HistoryTab tab : HistoryTab.values()) {
            html.append("<option value=\"").append(tab.name()).append("\"")
                    .append(tab == page.tab() ? " selected" : "").append(">")
                    .append(HtmlEscape.of(tab.title())).append("</option>");
        }
        html.append("</select></label>");
        if (page.tab().timed()) {
            html.append("<label>A partir de<input type=\"date\" name=\"since\" value=\"")
                    .append(since == null ? "" : since).append("\"></label>");
        }
        html.append("<label>Buscar<input type=\"text\" name=\"q\" value=\"").append(HtmlEscape.of(search))
                .append("\"></label><button>Filtrar</button></form>");
        html.append("<div class=\"table-wrap\"><table><thead><tr>");
        page.columns().forEach(column -> html.append("<th>").append(HtmlEscape.of(column)).append("</th>"));
        html.append("</tr></thead><tbody>");
        if (page.rows().isEmpty()) {
            html.append("<tr><td class=\"muted\" colspan=\"").append(Math.max(1, page.columns().size()))
                    .append("\">Nenhum registro.</td></tr>");
        }
        page.rows().forEach(row -> {
            html.append("<tr>");
            row.forEach(cell -> html.append("<td>").append(HtmlEscape.of(cell)).append("</td>"));
            html.append("</tr>");
        });
        html.append("</tbody></table></div>");
        html.append(pager(page, since, search));
        return html.toString();
    }

    private static String pager(HistoryPage page, LocalDate since, String search) {
        StringBuilder html = new StringBuilder("<div class=\"pager\">");
        if (page.page() > 0) {
            html.append("<a href=\"").append(url(page, since, search, page.page() - 1)).append("\">◀ Anterior</a>");
        }
        html.append("<span class=\"muted\">Página ").append(page.page() + 1).append(" de ")
                .append(page.lastPage() + 1).append(" — ").append(page.total()).append(" registro(s)</span>");
        if (page.page() < page.lastPage()) {
            html.append("<a href=\"").append(url(page, since, search, page.page() + 1)).append("\">Próxima ▶</a>");
        }
        return html.append("</div>").toString();
    }

    private static String url(HistoryPage page, LocalDate since, String search, int target) {
        return "/history?tab=" + page.tab().name() + "&amp;page=" + target
                + (since == null ? "" : "&amp;since=" + since)
                + (search == null || search.isBlank() ? "" : "&amp;q=" + URLEncoder.encode(search, StandardCharsets.UTF_8));
    }
}
