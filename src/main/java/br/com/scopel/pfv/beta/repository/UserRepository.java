package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.AccountSummary;
import br.com.scopel.pfv.beta.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLogin(String login);

    @Query("""
            select new br.com.scopel.pfv.beta.model.AccountSummary(u.id, u.name, u.login, u.role) from User u
            where u.active = true
            order by lower(u.name), u.id
            """)
    List<AccountSummary> findActiveAccounts();

}
