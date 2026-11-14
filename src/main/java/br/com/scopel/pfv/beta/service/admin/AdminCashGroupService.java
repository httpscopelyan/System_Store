package br.com.scopel.pfv.beta.service.admin;

import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
import br.com.scopel.pfv.beta.model.CashGroup;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.CashGroupRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminCashGroupService {

    private static final int MAX_NAME_LENGTH = 60;

    private final CashGroupRepository groups;
    private final UserRepository users;
    private final AuditLogService audit;

    public AdminCashGroupService(CashGroupRepository groups, UserRepository users, AuditLogService audit) {
        this.groups = groups;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CashGroupView> groups(User actor) {
        AdminAccess.requireDev(actor);
        List<User> everyone = users.findAllByOrderByNameAsc();
        return groups.findAllByOrderByNameAsc().stream()
                .map(group -> new CashGroupView(group.getId(), group.getName(), everyone.stream()
                        .filter(user -> user.getCashGroup() != null && user.getCashGroup().getId().equals(group.getId()))
                        .map(User::getId)
                        .collect(Collectors.toList())))
                .toList();
    }

    @Transactional
    public CashGroup save(User actor, Long groupId, String name, Set<Long> memberIds) {
        AdminAccess.requireDev(actor);
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("O nome do caixa deve ter de 1 a " + MAX_NAME_LENGTH + " caracteres.");
        }
        Set<Long> members = memberIds == null ? Set.of() : memberIds;
        if (members.size() < 2) {
            throw new IllegalArgumentException("Escolha pelo menos duas contas para dividir o caixa.");
        }
        CashGroup group = groupId == null ? null : groups.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Caixa unificado não encontrado."));
        boolean nameTaken = groups.findAllByOrderByNameAsc().stream()
                .anyMatch(other -> other.getName().equalsIgnoreCase(normalizedName)
                        && (group == null || !other.getId().equals(group.getId())));
        if (nameTaken) {
            throw new IllegalArgumentException("Já existe um caixa unificado com esse nome.");
        }
        List<User> selected = users.findAllById(members);
        if (selected.size() != members.size()) {
            throw new IllegalArgumentException("Alguma conta escolhida não existe mais.");
        }
        CashGroup saved = group == null ? groups.save(new CashGroup(normalizedName)) : group;
        saved.setName(normalizedName);
        groups.save(saved);
        for (User user : users.findAllByOrderByNameAsc()) {
            boolean isMember = members.contains(user.getId());
            boolean inGroup = user.getCashGroup() != null && user.getCashGroup().getId().equals(saved.getId());
            if (isMember && !inGroup) {
                user.setCashGroup(saved);
                users.save(user);
            } else if (!isMember && inGroup) {
                user.setCashGroup(null);
                users.save(user);
            }
        }
        audit.record(actor, AuditAction.CASH_GROUP_CHANGED, AuditEntityType.CASH_GROUP, saved.getId(),
                "Caixa unificado " + normalizedName + " definido com "
                        + selected.stream().map(User::getLogin).sorted().collect(Collectors.joining(", ")),
                null, normalizedName);
        return saved;
    }

    @Transactional
    public void dissolve(User actor, Long groupId) {
        AdminAccess.requireDev(actor);
        CashGroup group = groups.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Caixa unificado não encontrado."));
        for (User user : users.findAllByOrderByNameAsc()) {
            if (user.getCashGroup() != null && user.getCashGroup().getId().equals(groupId)) {
                user.setCashGroup(null);
                users.save(user);
            }
        }
        groups.delete(group);
        audit.record(actor, AuditAction.CASH_GROUP_CHANGED, AuditEntityType.CASH_GROUP, groupId,
                "Caixa unificado " + group.getName() + " desfeito; cada conta volta a ter o próprio caixa",
                group.getName(), null);
    }
}
