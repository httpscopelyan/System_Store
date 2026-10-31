package br.com.scopel.pfv.beta.service.alert;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class AlertRecipients {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]+$");
    private static final String SEPARATORS = "[,;\\s]+";

    private AlertRecipients() {
    }

    public static List<String> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> recipients = new LinkedHashSet<>();
        for (String candidate : text.trim().split(SEPARATORS)) {
            if (candidate.isEmpty()) {
                continue;
            }
            if (!EMAIL.matcher(candidate).matches()) {
                throw new IllegalArgumentException("E-mail inválido: " + candidate);
            }
            recipients.add(candidate);
        }
        return new ArrayList<>(recipients);
    }
}
