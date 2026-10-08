package br.com.scopel.pfv.beta.model;


import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    public enum Role {
        OPERADOR,
        GERENTE
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

    public Role getRole() {
        return role;
    }

    public Boolean getActive() {
        return active;
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
}
