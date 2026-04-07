package com.newproject.pricing.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class PriceRequest {
    @NotNull
    private Long productId;
    private String variantKey;
    @NotNull
    private BigDecimal amount;
    @NotNull
    private String currency;
    @NotNull
    private Boolean active;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getVariantKey() { return variantKey; }
    public void setVariantKey(String variantKey) { this.variantKey = variantKey; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
