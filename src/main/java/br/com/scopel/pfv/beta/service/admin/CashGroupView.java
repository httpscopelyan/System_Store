package br.com.scopel.pfv.beta.service.admin;

import java.util.List;

public record CashGroupView(Long id, String name, List<Long> memberIds) {
}
