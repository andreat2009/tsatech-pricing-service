package com.newproject.pricing.repository;

import com.newproject.pricing.domain.Price;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRepository extends JpaRepository<Price, Long> {
    Optional<Price> findByProductId(Long productId);
}
