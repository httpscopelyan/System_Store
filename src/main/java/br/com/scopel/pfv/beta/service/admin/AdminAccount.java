package br.com.scopel.pfv.beta.service.admin;

import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.model.User;

import java.util.Set;

public record AdminAccount(Long id, String name, String login, User.Role role, boolean active, Long cashGroupId,
                           String cashGroupName, Set<Permission> permissions, boolean protectedAccount) {
}
