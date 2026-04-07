package com.newproject.pricing.service;

import com.newproject.pricing.domain.Price;
import com.newproject.pricing.dto.PriceRequest;
import com.newproject.pricing.dto.PriceResponse;
import com.newproject.pricing.events.EventPublisher;
import com.newproject.pricing.exception.BadRequestException;
import com.newproject.pricing.exception.NotFoundException;
import com.newproject.pricing.repository.PriceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceService {
    private final PriceRepository priceRepository;
    private final EventPublisher eventPublisher;

    public PriceService(PriceRepository priceRepository, EventPublisher eventPublisher) {
        this.priceRepository = priceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PriceResponse create(PriceRequest request) {
        String variantKey = normalizeVariantKey(request.getVariantKey());
        priceRepository.findByProductIdAndVariantKey(request.getProductId(), variantKey)
            .ifPresent(existing -> { throw new BadRequestException("Price already exists for product scope"); });

        Price price = new Price();
        applyRequest(price, request, request.getProductId(), variantKey);
        Price saved = priceRepository.save(price);
        eventPublisher.publish("PRICE_CREATED", "price", saved.getId().toString(), toResponse(saved));
        return toResponse(saved);
    }

    @Transactional
    public PriceResponse update(Long productId, PriceRequest request) {
        return updateScoped(productId, "", request);
    }

    @Transactional
    public PriceResponse updateVariant(Long productId, String variantKey, PriceRequest request) {
        return updateScoped(productId, variantKey, request);
    }

    @Transactional(readOnly = true)
    public PriceResponse get(Long productId) {
        return getScoped(productId, "");
    }

    @Transactional(readOnly = true)
    public PriceResponse getVariant(Long productId, String variantKey) {
        return getScoped(productId, variantKey);
    }

    @Transactional(readOnly = true)
    public List<PriceResponse> list() {
        return priceRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public void delete(Long productId) {
        deleteScoped(productId, "");
    }

    @Transactional
    public void deleteVariant(Long productId, String variantKey) {
        deleteScoped(productId, variantKey);
    }

    private PriceResponse updateScoped(Long productId, String variantKey, PriceRequest request) {
        Price price = findRequired(productId, variantKey);
        applyRequest(price, request, productId, normalizeVariantKey(variantKey));
        Price saved = priceRepository.save(price);
        eventPublisher.publish("PRICE_UPDATED", "price", saved.getId().toString(), toResponse(saved));
        return toResponse(saved);
    }

    private PriceResponse getScoped(Long productId, String variantKey) {
        return toResponse(findRequired(productId, variantKey));
    }

    private void deleteScoped(Long productId, String variantKey) {
        Price price = findRequired(productId, variantKey);
        priceRepository.delete(price);
        eventPublisher.publish("PRICE_DELETED", "price", price.getId().toString(), null);
    }

    private Price findRequired(Long productId, String variantKey) {
        return priceRepository.findByProductIdAndVariantKey(productId, normalizeVariantKey(variantKey))
            .orElseThrow(() -> new NotFoundException("Price not found"));
    }

    private void applyRequest(Price price, PriceRequest request, Long productId, String variantKey) {
        price.setProductId(productId);
        price.setVariantKey(variantKey);
        price.setAmount(request.getAmount());
        price.setCurrency(request.getCurrency());
        price.setActive(request.getActive());
        price.setUpdatedAt(OffsetDateTime.now());
    }

    private PriceResponse toResponse(Price price) {
        PriceResponse response = new PriceResponse();
        response.setId(price.getId());
        response.setProductId(price.getProductId());
        response.setVariantKey(price.getVariantKey());
        response.setAmount(price.getAmount());
        response.setCurrency(price.getCurrency());
        response.setActive(price.getActive());
        response.setUpdatedAt(price.getUpdatedAt());
        return response;
    }

    private String normalizeVariantKey(String variantKey) {
        if (variantKey == null) {
            return "";
        }
        String trimmed = variantKey.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }
}
