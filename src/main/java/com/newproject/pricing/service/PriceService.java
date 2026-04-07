package com.newproject.pricing.service;

import com.newproject.pricing.domain.Price;
import com.newproject.pricing.dto.PriceRequest;
import com.newproject.pricing.dto.PriceResolutionItemRequest;
import com.newproject.pricing.dto.PriceResolutionItemResponse;
import com.newproject.pricing.dto.PriceResolutionRequest;
import com.newproject.pricing.dto.PriceResolutionResponse;
import com.newproject.pricing.dto.PriceResponse;
import com.newproject.pricing.events.EventPublisher;
import com.newproject.pricing.exception.NotFoundException;
import com.newproject.pricing.repository.PriceRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceService {
    public static final String DEFAULT_PRICE_LIST = "DEFAULT";

    private final PriceRepository priceRepository;
    private final EventPublisher eventPublisher;

    public PriceService(PriceRepository priceRepository, EventPublisher eventPublisher) {
        this.priceRepository = priceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PriceResponse create(PriceRequest request) {
        Price price = new Price();
        applyRequest(price, request, request.getProductId(), request.getVariantKey());
        Price saved = priceRepository.save(price);
        eventPublisher.publish("PRICE_CREATED", "price", saved.getId().toString(), toResponse(saved));
        return toResponse(saved);
    }

    @Transactional
    public PriceResponse update(Long productId, PriceRequest request) {
        Price price = findDefaultScoped(productId, "");
        applyRequest(price, request, productId, "");
        Price saved = priceRepository.save(price);
        eventPublisher.publish("PRICE_UPDATED", "price", saved.getId().toString(), toResponse(saved));
        return toResponse(saved);
    }

    @Transactional
    public PriceResponse updateVariant(Long productId, String variantKey, PriceRequest request) {
        Price price = findDefaultScoped(productId, variantKey);
        applyRequest(price, request, productId, variantKey);
        Price saved = priceRepository.save(price);
        eventPublisher.publish("PRICE_UPDATED", "price", saved.getId().toString(), toResponse(saved));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PriceResponse get(Long productId) {
        return toResponse(findDefaultScoped(productId, ""));
    }

    @Transactional(readOnly = true)
    public PriceResponse getVariant(Long productId, String variantKey) {
        return toResponse(findDefaultScoped(productId, variantKey));
    }

    @Transactional(readOnly = true)
    public List<PriceResponse> list() {
        return priceRepository.findAll().stream()
            .sorted(Comparator
                .comparing(Price::getProductId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(price -> normalizeVariantKey(price.getVariantKey()), Comparator.nullsLast(String::compareTo))
                .thenComparing(price -> normalizePriceListCode(price.getPriceListCode()), Comparator.nullsLast(String::compareTo))
                .thenComparing(price -> normalizeCustomerGroupCode(price.getCustomerGroupCode()), Comparator.nullsLast(String::compareTo))
                .thenComparing(Price::getPriority, Comparator.nullsLast(Integer::compareTo)).reversed())
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PriceResponse getRule(Long id) {
        return toResponse(findRequired(id));
    }

    @Transactional
    public PriceResponse updateRule(Long id, PriceRequest request) {
        Price price = findRequired(id);
        applyRequest(price, request, request.getProductId(), request.getVariantKey());
        Price saved = priceRepository.save(price);
        eventPublisher.publish("PRICE_UPDATED", "price", saved.getId().toString(), toResponse(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long productId) {
        deleteDefaultScoped(productId, "");
    }

    @Transactional
    public void deleteVariant(Long productId, String variantKey) {
        deleteDefaultScoped(productId, variantKey);
    }

    @Transactional
    public void deleteRule(Long id) {
        Price price = findRequired(id);
        priceRepository.delete(price);
        eventPublisher.publish("PRICE_DELETED", "price", price.getId().toString(), null);
    }

    @Transactional(readOnly = true)
    public PriceResolutionResponse resolve(PriceResolutionRequest request) {
        PriceResolutionResponse response = new PriceResolutionResponse();
        String requestPriceListCode = normalizePriceListCode(request != null ? request.getPriceListCode() : null);
        String requestCustomerGroupCode = normalizeCustomerGroupCode(request != null ? request.getCustomerGroupCode() : null);
        String requestCurrency = normalizeCurrency(request != null ? request.getCurrency() : null);
        OffsetDateTime at = request != null && request.getAt() != null ? request.getAt() : OffsetDateTime.now();

        response.setPriceListCode(requestPriceListCode);
        response.setCustomerGroupCode(requestCustomerGroupCode);

        List<PriceResolutionItemRequest> items = request != null && request.getItems() != null ? request.getItems() : List.of();
        List<Long> productIds = items.stream()
            .map(PriceResolutionItemRequest::getProductId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (productIds.isEmpty()) {
            return response;
        }

        Map<Long, List<Price>> pricesByProductId = priceRepository.findAllByProductIdIn(productIds).stream()
            .collect(Collectors.groupingBy(Price::getProductId, LinkedHashMap::new, Collectors.toList()));

        response.setItems(items.stream()
            .map(item -> resolveItem(item, pricesByProductId.getOrDefault(item.getProductId(), List.of()), requestPriceListCode, requestCustomerGroupCode, requestCurrency, at))
            .collect(Collectors.toList()));
        return response;
    }

    private PriceResolutionItemResponse resolveItem(
        PriceResolutionItemRequest item,
        List<Price> candidates,
        String requestPriceListCode,
        String requestCustomerGroupCode,
        String requestCurrency,
        OffsetDateTime at
    ) {
        PriceResolutionItemResponse response = new PriceResolutionItemResponse();
        if (item == null) {
            return response;
        }
        String requestedVariantKey = normalizeVariantKey(item.getVariantKey());
        response.setProductId(item.getProductId());
        response.setVariantKey(requestedVariantKey);
        response.setQuantity(item.getQuantity());

        List<Price> scoped = candidates.stream()
            .filter(price -> isEligible(price, requestPriceListCode, requestCustomerGroupCode, requestCurrency, at))
            .collect(Collectors.toList());

        Optional<Price> match = pickBestMatch(scoped, requestedVariantKey, requestPriceListCode, requestCustomerGroupCode);
        Price resolved = match.orElseGet(() -> fallbackBasePrice(candidates, requestedVariantKey, requestCurrency, at));
        if (resolved == null) {
            return response;
        }

        response.setAmount(resolved.getAmount());
        response.setCompareAtAmount(resolveCompareAtAmount(candidates, resolved, requestCurrency, at));
        response.setCurrency(resolved.getCurrency());
        response.setPriceListCode(normalizePriceListCode(resolved.getPriceListCode()));
        response.setCustomerGroupCode(normalizeCustomerGroupCode(resolved.getCustomerGroupCode()));
        response.setPriority(resolved.getPriority());
        response.setSourcePriceId(resolved.getId());
        return response;
    }

    private Optional<Price> pickBestMatch(
        Collection<Price> candidates,
        String requestedVariantKey,
        String requestPriceListCode,
        String requestCustomerGroupCode
    ) {
        List<Price> exactVariant = candidates.stream()
            .filter(price -> requestedVariantKey.equals(normalizeVariantKey(price.getVariantKey())))
            .collect(Collectors.toList());
        if (!exactVariant.isEmpty()) {
            return exactVariant.stream().max(ruleComparator(requestPriceListCode, requestCustomerGroupCode));
        }

        List<Price> baseVariant = candidates.stream()
            .filter(price -> normalizeVariantKey(price.getVariantKey()).isEmpty())
            .collect(Collectors.toList());
        return baseVariant.stream().max(ruleComparator(requestPriceListCode, requestCustomerGroupCode));
    }

    private Price fallbackBasePrice(List<Price> candidates, String requestedVariantKey, String requestCurrency, OffsetDateTime at) {
        return candidates.stream()
            .filter(price -> isEligible(price, DEFAULT_PRICE_LIST, null, requestCurrency, at))
            .filter(price -> requestedVariantKey.equals(normalizeVariantKey(price.getVariantKey())) || normalizeVariantKey(price.getVariantKey()).isEmpty())
            .max(ruleComparator(DEFAULT_PRICE_LIST, null))
            .orElse(null);
    }

    private BigDecimal resolveCompareAtAmount(List<Price> candidates, Price resolved, String requestCurrency, OffsetDateTime at) {
        if (resolved.getCompareAtAmount() != null) {
            return resolved.getCompareAtAmount();
        }
        if (resolved.getProductId() == null) {
            return null;
        }
        Price base = candidates.stream()
            .filter(price -> isEligible(price, DEFAULT_PRICE_LIST, null, requestCurrency, at))
            .filter(price -> normalizeVariantKey(price.getVariantKey()).equals(normalizeVariantKey(resolved.getVariantKey())))
            .max(ruleComparator(DEFAULT_PRICE_LIST, null))
            .orElse(null);
        if (base != null && base.getAmount() != null && resolved.getAmount() != null && base.getAmount().compareTo(resolved.getAmount()) > 0) {
            return base.getAmount();
        }
        return null;
    }

    private Comparator<Price> ruleComparator(String requestPriceListCode, String requestCustomerGroupCode) {
        return Comparator
            .comparingInt((Price price) -> normalizeVariantKey(price.getVariantKey()).isEmpty() ? 0 : 1)
            .thenComparingInt(price -> normalizePriceListCode(price.getPriceListCode()).equals(requestPriceListCode) ? 1 : 0)
            .thenComparingInt(price -> normalizeCustomerGroupCode(price.getCustomerGroupCode()) != null
                && normalizeCustomerGroupCode(price.getCustomerGroupCode()).equals(requestCustomerGroupCode) ? 1 : 0)
            .thenComparing(price -> price.getPriority() != null ? price.getPriority() : 0)
            .thenComparing(Price::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private boolean isEligible(Price price, String requestPriceListCode, String requestCustomerGroupCode, String requestCurrency, OffsetDateTime at) {
        if (price == null || !Boolean.TRUE.equals(price.getActive()) || price.getAmount() == null) {
            return false;
        }
        if (requestCurrency != null && price.getCurrency() != null && !requestCurrency.equalsIgnoreCase(price.getCurrency())) {
            return false;
        }
        if (price.getStartsAt() != null && price.getStartsAt().isAfter(at)) {
            return false;
        }
        if (price.getEndsAt() != null && price.getEndsAt().isBefore(at)) {
            return false;
        }
        String priceListCode = normalizePriceListCode(price.getPriceListCode());
        if (requestPriceListCode != null && !requestPriceListCode.equals(priceListCode) && !DEFAULT_PRICE_LIST.equals(priceListCode)) {
            return false;
        }
        String priceCustomerGroupCode = normalizeCustomerGroupCode(price.getCustomerGroupCode());
        return priceCustomerGroupCode == null || Objects.equals(priceCustomerGroupCode, requestCustomerGroupCode);
    }

    private void deleteDefaultScoped(Long productId, String variantKey) {
        Price price = findDefaultScoped(productId, variantKey);
        priceRepository.delete(price);
        eventPublisher.publish("PRICE_DELETED", "price", price.getId().toString(), null);
    }

    private Price findDefaultScoped(Long productId, String variantKey) {
        return priceRepository.findFirstByProductIdAndVariantKeyAndPriceListCodeAndCustomerGroupCodeIsNullOrderByPriorityDescUpdatedAtDesc(
                productId,
                normalizeVariantKey(variantKey),
                DEFAULT_PRICE_LIST
            )
            .orElseThrow(() -> new NotFoundException("Price not found"));
    }

    private Price findRequired(Long id) {
        return priceRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Price not found"));
    }

    private void applyRequest(Price price, PriceRequest request, Long productId, String variantKey) {
        price.setProductId(productId);
        price.setVariantKey(normalizeVariantKey(variantKey != null ? variantKey : request.getVariantKey()));
        price.setPriceListCode(normalizePriceListCode(request.getPriceListCode()));
        price.setCustomerGroupCode(normalizeCustomerGroupCode(request.getCustomerGroupCode()));
        price.setCompareAtAmount(request.getCompareAtAmount());
        price.setAmount(request.getAmount());
        price.setCurrency(normalizeCurrency(request.getCurrency()));
        price.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        price.setStartsAt(request.getStartsAt());
        price.setEndsAt(request.getEndsAt());
        price.setActive(request.getActive());
        price.setUpdatedAt(OffsetDateTime.now());
    }

    private PriceResponse toResponse(Price price) {
        PriceResponse response = new PriceResponse();
        response.setId(price.getId());
        response.setProductId(price.getProductId());
        response.setVariantKey(normalizeVariantKey(price.getVariantKey()));
        response.setPriceListCode(normalizePriceListCode(price.getPriceListCode()));
        response.setCustomerGroupCode(normalizeCustomerGroupCode(price.getCustomerGroupCode()));
        response.setCompareAtAmount(price.getCompareAtAmount());
        response.setAmount(price.getAmount());
        response.setCurrency(price.getCurrency());
        response.setPriority(price.getPriority());
        response.setStartsAt(price.getStartsAt());
        response.setEndsAt(price.getEndsAt());
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

    private String normalizePriceListCode(String priceListCode) {
        if (priceListCode == null || priceListCode.isBlank()) {
            return DEFAULT_PRICE_LIST;
        }
        return priceListCode.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private String normalizeCustomerGroupCode(String customerGroupCode) {
        if (customerGroupCode == null || customerGroupCode.isBlank()) {
            return null;
        }
        return customerGroupCode.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return null;
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
