package com.newproject.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.newproject.pricing.domain.Price;
import com.newproject.pricing.dto.PriceResolutionItemRequest;
import com.newproject.pricing.dto.PriceResolutionRequest;
import com.newproject.pricing.dto.PriceResolutionResponse;
import com.newproject.pricing.events.EventPublisher;
import com.newproject.pricing.repository.PriceRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceServiceResolutionTest {

    @Test
    void resolvePrefersCustomerGroupAndPriorityRule() {
        PriceRepository repository = mock(PriceRepository.class);
        PriceService service = new PriceService(repository, mock(EventPublisher.class));

        Price base = price(1L, 1001L, "", "DEFAULT", null, new BigDecimal("19.90"), 0);
        Price vip = price(2L, 1001L, "", "DEFAULT", "VIP", new BigDecimal("14.90"), 5);
        Price vipSale = price(3L, 1001L, "", "SPRING", "VIP", new BigDecimal("12.90"), 10);

        when(repository.findAllByProductIdIn(List.of(1001L))).thenReturn(List.of(base, vip, vipSale));

        PriceResolutionRequest request = new PriceResolutionRequest();
        request.setCustomerGroupCode("VIP");
        request.setPriceListCode("SPRING");
        request.setItems(List.of(item(1001L, "")));

        PriceResolutionResponse response = service.resolve(request);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getAmount()).isEqualByComparingTo("12.90");
        assertThat(response.getItems().get(0).getPriceListCode()).isEqualTo("SPRING");
        assertThat(response.getItems().get(0).getCustomerGroupCode()).isEqualTo("VIP");
        assertThat(response.getItems().get(0).getCompareAtAmount()).isEqualByComparingTo("19.90");
    }

    @Test
    void resolveFallsBackToBaseWhenNoCustomerSpecificRuleExists() {
        PriceRepository repository = mock(PriceRepository.class);
        PriceService service = new PriceService(repository, mock(EventPublisher.class));

        Price base = price(1L, 1002L, "SIZE_L", "DEFAULT", null, new BigDecimal("29.90"), 0);
        when(repository.findAllByProductIdIn(List.of(1002L))).thenReturn(List.of(base));

        PriceResolutionRequest request = new PriceResolutionRequest();
        request.setCustomerGroupCode("WHOLESALE");
        request.setItems(List.of(item(1002L, "SIZE_L")));

        PriceResolutionResponse response = service.resolve(request);

        assertThat(response.getItems().get(0).getAmount()).isEqualByComparingTo("29.90");
        assertThat(response.getItems().get(0).getCustomerGroupCode()).isNull();
    }

    private PriceResolutionItemRequest item(Long productId, String variantKey) {
        PriceResolutionItemRequest item = new PriceResolutionItemRequest();
        item.setProductId(productId);
        item.setVariantKey(variantKey);
        item.setQuantity(1);
        return item;
    }

    private Price price(Long id, Long productId, String variantKey, String priceListCode, String customerGroupCode, BigDecimal amount, int priority) {
        Price price = new Price();
        price.setId(id);
        price.setProductId(productId);
        price.setVariantKey(variantKey);
        price.setPriceListCode(priceListCode);
        price.setCustomerGroupCode(customerGroupCode);
        price.setAmount(amount);
        price.setCurrency("EUR");
        price.setPriority(priority);
        price.setActive(true);
        price.setUpdatedAt(OffsetDateTime.parse("2026-04-07T10:15:30Z"));
        return price;
    }
}
