package com.newproject.pricing.controller;

import com.newproject.pricing.dto.PriceRequest;
import com.newproject.pricing.dto.PriceResponse;
import com.newproject.pricing.service.PriceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pricing")
public class PriceController {
    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping
    public List<PriceResponse> list() {
        return priceService.list();
    }

    @GetMapping("/{productId}")
    public PriceResponse get(@PathVariable Long productId) {
        return priceService.get(productId);
    }

    @GetMapping("/{productId}/variants/{variantKey}")
    public PriceResponse getVariant(@PathVariable Long productId, @PathVariable String variantKey) {
        return priceService.getVariant(productId, variantKey);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PriceResponse create(@Valid @RequestBody PriceRequest request) {
        return priceService.create(request);
    }

    @PutMapping("/{productId}")
    public PriceResponse update(@PathVariable Long productId, @Valid @RequestBody PriceRequest request) {
        return priceService.update(productId, request);
    }

    @PutMapping("/{productId}/variants/{variantKey}")
    public PriceResponse updateVariant(@PathVariable Long productId, @PathVariable String variantKey, @Valid @RequestBody PriceRequest request) {
        return priceService.updateVariant(productId, variantKey, request);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long productId) {
        priceService.delete(productId);
    }

    @DeleteMapping("/{productId}/variants/{variantKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVariant(@PathVariable Long productId, @PathVariable String variantKey) {
        priceService.deleteVariant(productId, variantKey);
    }
}
