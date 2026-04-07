package com.newproject.pricing.repository;

import com.newproject.pricing.domain.Price;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRepository extends JpaRepository<Price, Long> {
    Optional<Price> findFirstByProductIdAndVariantKeyAndPriceListCodeAndCustomerGroupCodeIsNullOrderByPriorityDescUpdatedAtDesc(
        Long productId, String variantKey, String priceListCode
    );

    List<Price> findAllByProductId(Long productId);

    List<Price> findAllByProductIdIn(Collection<Long> productIds);
}
