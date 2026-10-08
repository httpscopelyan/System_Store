package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.UserDuplicatedException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder; 

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public User findLogin(String login) {
        return repository.findByLogin(login)
                .orElseThrow(() -> new UserNotFindException());
    }

    public User save(User newUser) {
        if (repository.findByLogin(newUser.getLogin()).isPresent()) {
            throw new UserDuplicatedException();
        }
        String hash = passwordEncoder.encode(newUser.getPassword());
        newUser.setPassword(hash);

        return repository.save(newUser);
    }

    public User login(String login, String rawPassword) {
        User user = repository.findByLogin(login)
                .orElseThrow(() -> new UserNotFindException());

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidPasswordException();
        }
        return user;
    }

    public User modificationRole(String login, User.Role role) {
        User n = repository.findByLogin(login)
                .orElseThrow(() -> new UserNotFindException());
        n.setRole(role);
        return repository.save(n);
    }
}