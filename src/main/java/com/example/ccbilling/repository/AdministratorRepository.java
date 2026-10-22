package com.example.ccbilling.repository;

import com.example.ccbilling.domain.Administrator;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministratorRepository extends JpaRepository<Administrator, Long> {

    Optional<Administrator> findByUsername(String username);

    boolean existsByUsername(String username);
}
