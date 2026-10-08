package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLogin(String login);

}
