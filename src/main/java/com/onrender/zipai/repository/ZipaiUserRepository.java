package com.onrender.zipai.repository;

import com.onrender.zipai.domain.ZipaiUser;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface ZipaiUserRepository extends CrudRepository<ZipaiUser, Long> {
    Optional<ZipaiUser> findByUsernameIgnoreCase(String username);
    Optional<ZipaiUser> findByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
