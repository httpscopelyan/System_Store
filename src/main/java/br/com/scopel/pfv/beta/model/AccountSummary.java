package br.com.scopel.pfv.beta.model;

public record AccountSummary(Long id, String name, String login, User.Role role) {
}
