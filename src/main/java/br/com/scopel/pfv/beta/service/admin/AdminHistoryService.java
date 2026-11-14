package br.com.scopel.pfv.beta.service.admin;

import br.com.scopel.pfv.beta.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminHistoryService {

    public static final int PAGE_SIZE = 100;

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final JdbcTemplate jdbc;

    public AdminHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public HistoryPage page(User actor, HistoryTab tab, LocalDateTime since, String search, int pageNumber) {
        AdminAccess.requireDev(actor);
        if (tab == null) {
            throw new IllegalArgumentException("Escolha o histórico.");
        }
        int pageIndex = Math.max(pageNumber, 0);
        List<Object> filters = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (tab.timed() && since != null) {
            where.append(" AND q.\"Data/hora\" >= ?");
            filters.add(Timestamp.valueOf(since));
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND q::text ILIKE ?");
            filters.add("%" + escapeLike(search.trim()) + "%");
        }
        String filtered = "SELECT q.* FROM (" + tab.sql() + ") q" + where;
        Long total = jdbc.queryForObject("SELECT count(*) FROM (" + filtered + ") counted", Long.class,
                filters.toArray());
        List<Object> pageArguments = new ArrayList<>(filters);
        pageArguments.add(PAGE_SIZE);
        pageArguments.add((long) pageIndex * PAGE_SIZE);
        List<String> columns = new ArrayList<>();
        List<List<String>> rows = jdbc.query(filtered + " ORDER BY " + tab.orderBy() + " LIMIT ? OFFSET ?",
                resultSet -> {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    for (int column = 1; column <= columnCount; column++) {
                        columns.add(metaData.getColumnLabel(column));
                    }
                    List<List<String>> collected = new ArrayList<>();
                    while (resultSet.next()) {
                        List<String> row = new ArrayList<>(columnCount);
                        for (int column = 1; column <= columnCount; column++) {
                            row.add(format(resultSet.getObject(column)));
                        }
                        collected.add(row);
                    }
                    return collected;
                }, pageArguments.toArray());
        return new HistoryPage(tab, columns, rows, total == null ? 0 : total, pageIndex, PAGE_SIZE);
    }

    private String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(TIMESTAMP);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return value.toString();
    }

    private String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
