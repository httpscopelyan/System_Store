package br.com.scopel.pfv.beta.service.admin;

import java.util.List;

public record HistoryPage(HistoryTab tab, List<String> columns, List<List<String>> rows, long total, int page,
                          int pageSize) {

    public int lastPage() {
        return total == 0 ? 0 : (int) ((total - 1) / pageSize);
    }
}
