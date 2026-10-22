package com.example.ccbilling.repository;

import com.example.ccbilling.domain.BinConfiguration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BinConfigurationRepository extends JpaRepository<BinConfiguration, Long> {

    Optional<BinConfiguration> findByBinCode(String binCode);

    boolean existsByBinCode(String binCode);
}
