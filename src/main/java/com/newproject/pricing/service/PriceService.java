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
        priceRepository.findByProductId(request.getProductId())
            .ifPresent(existing -> { throw new BadRequestException("Price already exists for product"); });

        Price price = new Price();
        applyRequest(price, request);
        price.setUpdatedAt(OffsetDateTime.now());

        Price saved = priceRepository.save(price);
        eventPublisher.publish("PRICE_CREATED", "price", saved.getId().toString(), toResponse(saved));
        return toResponse(saved);
    }

    @Transactional
    public PriceResponse update(Long productId, PriceRequest request) {
        Price price = priceRepository.findByProductId(productId)
            .orElseThrow(() -> new NotFoundException("Price not found"));

        applyRequest(price, request);
        price.setUpdatedAt(OffsetDateTime.now());

        Price saved = priceRepository.save(price);
        eventPublisher.publish("PRICE_UPDATED", "price", saved.getId().toString(), toResponse(saved));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PriceResponse get(Long productId) {
        Price price = priceRepository.findByProductId(productId)
            .orElseThrow(() -> new NotFoundException("Price not found"));
        return toResponse(price);
    }

    @Transactional(readOnly = true)
    public List<PriceResponse> list() {
        return priceRepository.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public void delete(Long productId) {
        Price price = priceRepository.findByProductId(productId)
            .orElseThrow(() -> new NotFoundException("Price not found"));
        priceRepository.delete(price);
        eventPublisher.publish("PRICE_DELETED", "price", price.getId().toString(), null);
    }

    private void applyRequest(Price price, PriceRequest request) {
        price.setProductId(request.getProductId());
        price.setAmount(request.getAmount());
        price.setCurrency(request.getCurrency());
        price.setActive(request.getActive());
    }

    private PriceResponse toResponse(Price price) {
        PriceResponse response = new PriceResponse();
        response.setId(price.getId());
        response.setProductId(price.getProductId());
        response.setAmount(price.getAmount());
        response.setCurrency(price.getCurrency());
        response.setActive(price.getActive());
        response.setUpdatedAt(price.getUpdatedAt());
        return response;
    }
}
