package com.crm.product.service;
import com.crm.product.dto.ProductPageResponse;
import com.crm.product.dto.ProductSearchRequest;
import com.crm.product.dto.ProductUpdateRequest;
import com.crm.product.dto.ProductCreateRequest;

import com.crm.product.dto.ProductResponse;

import com.crm.common.exception.AppException;
import com.crm.product.entity.Product;
import com.crm.product.repository.ProductRepository;
import com.crm.rbac.config.Permissions;
import com.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @PreAuthorize("@sec.has('" + Permissions.PRODUCT_VIEW + "')")
    public ProductPageResponse search(ProductSearchRequest req) {
        int size   = Math.min(req.getSize(), 100);
        int offset = req.getPage() * size;

        var products = productRepository.search(
            req.isOnlyActive(),
            req.getCategoryId() != null ? req.getCategoryId().toString() : null,
            req.getQuery(),
            size, offset
        );
        long total = productRepository.countSearch(
            req.isOnlyActive(),
            req.getCategoryId() != null ? req.getCategoryId().toString() : null,
            req.getQuery()
        );

        return ProductPageResponse.builder()
            .content(products.stream().map(this::toResponse).toList())
            .totalElements(total)
            .totalPages((int) Math.ceil((double) total / size))
            .page(req.getPage()).size(size)
            .build();
    }

    @PreAuthorize("@sec.has('" + Permissions.PRODUCT_VIEW + "')")
    public ProductResponse getById(UUID id) {
        return toResponse(find(id));
    }

    @PreAuthorize("@sec.has('" + Permissions.PRODUCT_MANAGE + "')")
    @Transactional
    public ProductResponse create(ProductCreateRequest req, User author) {
        // Уникальность артикула
        if (req.getSku() != null && productRepository.findBySku(req.getSku()).isPresent()) {
            throw AppException.conflict("SKU_EXISTS", "Товар с таким артикулом уже существует");
        }

        Product product = Product.builder()
            .name(req.getName()).description(req.getDescription())
            .sku(req.getSku()).price(req.getPrice()).unit(req.getUnit())
            .categoryId(req.getCategoryId()).isActive(req.isActive())
            .createdBy(author.getId())
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();

        product = productRepository.save(product);
        log.info("Product created: {} sku={}", product.getId(), product.getSku());
        return toResponse(product);
    }

    @PreAuthorize("@sec.has('" + Permissions.PRODUCT_MANAGE + "')")
    @Transactional
    public ProductResponse update(UUID id, ProductUpdateRequest req) {
        Product product = find(id);

        if (req.getName()        != null) product.setName(req.getName());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getSku()         != null) product.setSku(req.getSku());
        if (req.getPrice()       != null) product.setPrice(req.getPrice());
        if (req.getUnit()        != null) product.setUnit(req.getUnit());
        if (req.getCategoryId()  != null) product.setCategoryId(req.getCategoryId());
        if (req.getIsActive()    != null) product.setActive(req.getIsActive());
        product.setUpdatedAt(Instant.now());

        return toResponse(productRepository.save(product));
    }

    @PreAuthorize("@sec.has('" + Permissions.PRODUCT_MANAGE + "')")
    public void setActive(UUID id, boolean active) {
        if (!productRepository.existsById(id)) throw AppException.notFound("Товар");
        productRepository.setActive(id, active);
    }

    @PreAuthorize("@sec.has('" + Permissions.PRODUCT_MANAGE + "')")
    @Transactional
    public void delete(UUID id) {
        if (!productRepository.existsById(id)) throw AppException.notFound("Товар");
        productRepository.deleteById(id);
    }

    private Product find(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> AppException.notFound("Товар"));
    }

    private ProductResponse toResponse(Product p) {
        return ProductResponse.builder()
            .id(p.getId()).name(p.getName()).description(p.getDescription())
            .sku(p.getSku()).price(p.getPrice()).unit(p.getUnit())
            .categoryId(p.getCategoryId()).isActive(p.isActive())
            .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
            .build();
    }
}
