package br.com.scopel.pfv.beta.model;


import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

    public enum Role {
        OPERADOR,
        GERENTE,
        DEV
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String name;
    @Column(nullable = false, unique = true)
    private String login;
    @Column(nullable = false, length = 80)
    private String password;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private Role role;
    @Column(nullable = false)
    private Boolean active = true;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cash_group_id")
    private CashGroup cashGroup;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permission", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new HashSet<>();

    @PrePersist
    void applyDefaultPermissionsWhenMissing() {
        if (permissions.isEmpty() && role != null) {
            permissions.addAll(br.com.scopel.pfv.beta.service.PermissionDefaults.forAccount(login, name, role));
        }
    }

    // getters

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public boolean hasManagerAccess() {
        return role == Role.GERENTE || role == Role.DEV;
    }

    public Role getRole() {
        return role;
    }

    public Boolean getActive() {
        return active;
    }

    public CashGroup getCashGroup() {
        return cashGroup;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(Permission permission) {
        return role == Role.DEV || permissions.contains(permission);
    }


    //setters

    public void setId(Long id) {
        this.id = id;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setCashGroup(CashGroup cashGroup) {
        this.cashGroup = cashGroup;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }
}
