package br.com.scopel.pfv.beta.service.cash;

import java.util.List;

public record CashOverview(List<CashMovementRow> rows, CashSummary summary) {
}
