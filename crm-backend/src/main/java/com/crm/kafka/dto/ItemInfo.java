package com.crm.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Setter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemInfo {
    /**
     * SKU товара в магазине — ищем совпадение в таблице products.
     * Если SKU не найден — позиция создаётся с нулевым productId и
     * сохранённым именем товара.
     */
    private String sku;
    /** Название товара на момент заказа (денормализованное). */
    private String name;
    private BigDecimal quantity;
    /** Цена на момент заказа (из магазина). */
    private BigDecimal price;
}
