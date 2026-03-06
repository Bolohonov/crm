package com.crm.customer.service;

import com.crm.common.exception.AppException;
import com.crm.customer.dto.CustomerDto;
import com.crm.customer.entity.*;
import com.crm.customer.repository.*;
import com.crm.rbac.config.Permissions;
import com.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Сервис управления клиентами.
 *
 * Особенности:
 * - Все запросы идут в схему тенанта (search_path уже установлен)
 * - Поиск через PostgreSQL TSVECTOR (fts_name) — быстро без LIKE '%..%'
 * - Данные разделены: customers + personal_data/org_data (join в маппере)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerPersonalDataRepository personalDataRepository;
    private final CustomerOrgDataRepository orgDataRepository;

    private static final int MAX_PAGE_SIZE = 100;

    // ----------------------------------------------------------------
    //  Список и поиск
    // ----------------------------------------------------------------

    @PreAuthorize("@sec.has('" + Permissions.CUSTOMER_VIEW + "')")
    public CustomerDto.PageResponse search(CustomerDto.SearchRequest req) {
        int size   = Math.min(req.getSize(), MAX_PAGE_SIZE);
        int offset = req.getPage() * size;

        List<Customer> customers;
        long total;

        if (req.getQuery() != null && !req.getQuery().isBlank()) {
            var personal = customerRepository.searchPersonal(req.getQuery(), size, offset);
            var orgs     = customerRepository.searchOrg(req.getQuery(), size, offset);
            customers = merge(personal, orgs, size);
            total = customers.size();
        } else {
            String type   = req.getType()   != null ? req.getType().name()   : null;
            String status = req.getStatus() != null ? req.getStatus()         : null;
            customers = customerRepository.findAll(type, status, size, offset);
            total     = customerRepository.countAll(type, status);
        }

        // FIX N+1: грузим все personal/org данные двумя запросами вместо 2*N
        List<UUID> ids = customers.stream().map(Customer::getId).toList();

        Map<UUID, CustomerPersonalData> personalMap = ids.isEmpty()
                ? Map.of()
                : personalDataRepository.findAllByCustomerIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(
                        CustomerPersonalData::getCustomerId, pd -> pd));

        Map<UUID, CustomerOrgData> orgMap = ids.isEmpty()
                ? Map.of()
                : orgDataRepository.findAllByCustomerIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(
                        CustomerOrgData::getCustomerId, od -> od));

        List<CustomerDto.CustomerResponse> content = customers.stream()
                .map(c -> toResponse(c, personalMap.get(c.getId()), orgMap.get(c.getId())))
                .toList();

        return CustomerDto.PageResponse.builder()
                .content(content)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / size))
                .page(req.getPage())
                .size(size)
                .build();
    }

    // ----------------------------------------------------------------
    //  Получение по ID
    // ----------------------------------------------------------------

    @PreAuthorize("@sec.has('" + Permissions.CUSTOMER_VIEW + "')")
    public CustomerDto.CustomerResponse getById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Клиент"));
        return toResponse(customer);
    }

    // ----------------------------------------------------------------
    //  Создание
    // ----------------------------------------------------------------

    @PreAuthorize("@sec.has('" + Permissions.CUSTOMER_CREATE + "')")
    @Transactional
    public CustomerDto.CustomerResponse create(CustomerDto.CreateRequest req, User currentUser) {
        validateCreateRequest(req);

        Customer customer = Customer.builder()
                .customerType(req.getCustomerType())
                .status(req.getStatus() != null ? req.getStatus() : "NEW")
                .createdBy(currentUser.getId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        customer = customerRepository.save(customer);

        // Сохраняем персональные данные
        if (req.getPersonalData() != null &&
                req.getCustomerType() != CustomerType.LEGAL_ENTITY) {
            savePersonalData(customer.getId(), req.getPersonalData());
        }

        // Сохраняем данные организации
        if (req.getOrgData() != null &&
                req.getCustomerType() != CustomerType.INDIVIDUAL) {
            saveOrgData(customer.getId(), req.getOrgData());
        }

        log.info("Customer created: {} type={}", customer.getId(), customer.getCustomerType());
        return toResponse(customer);
    }

    // ----------------------------------------------------------------
    //  Обновление
    // ----------------------------------------------------------------

    @PreAuthorize("@sec.has('" + Permissions.CUSTOMER_EDIT + "')")
    @Transactional
    public CustomerDto.CustomerResponse update(UUID id, CustomerDto.UpdateRequest req) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Клиент"));

        if (req.getStatus() != null) {
            customerRepository.updateStatus(id, req.getStatus());
        }

        if (req.getPersonalData() != null) {
            var existing = personalDataRepository.findByCustomerId(id);
            if (existing.isPresent()) {
                var pd = existing.get();
                applyPersonalData(pd, req.getPersonalData());
                personalDataRepository.update(pd);
            } else {
                savePersonalData(id, req.getPersonalData());
            }
        }

        if (req.getOrgData() != null) {
            var existing = orgDataRepository.findByCustomerId(id);
            if (existing.isPresent()) {
                var od = existing.get();
                applyOrgData(od, req.getOrgData());
                orgDataRepository.update(od);
            } else {
                saveOrgData(id, req.getOrgData());
            }
        }

        log.info("Customer updated: {}", id);
        return toResponse(customerRepository.findById(id).orElseThrow());
    }

    // ----------------------------------------------------------------
    //  Удаление
    // ----------------------------------------------------------------

    @PreAuthorize("@sec.has('" + Permissions.CUSTOMER_DELETE + "')")
    @Transactional
    public void delete(UUID id) {
        if (!customerRepository.existsById(id)) {
            throw AppException.notFound("Клиент");
        }
        // personal_data и org_data удалятся каскадом (ON DELETE CASCADE в БД)
        customerRepository.deleteById(id);
        log.info("Customer deleted: {}", id);
    }

    // ----------------------------------------------------------------
    //  Маппинг
    // ----------------------------------------------------------------

    // Для getById — старый вариант с запросами в БД
    private CustomerDto.CustomerResponse toResponse(Customer customer) {
        var pd = personalDataRepository.findByCustomerId(customer.getId()).orElse(null);
        var od = orgDataRepository.findByCustomerId(customer.getId()).orElse(null);
        return toResponse(customer, pd, od);
    }

    // Для search() — данные уже загружены batch-запросом
    private CustomerDto.CustomerResponse toResponse(Customer customer,
                                                    CustomerPersonalData pd,
                                                    CustomerOrgData od) {
        var builder = CustomerDto.CustomerResponse.builder()
                .id(customer.getId())
                .customerType(customer.getCustomerType())
                .status(customer.getStatus())
                .createdBy(customer.getCreatedBy())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt());

        if (pd != null) {
            String fullName = buildFullName(pd.getLastName(), pd.getFirstName(), pd.getMiddleName());
            builder.personalData(CustomerDto.PersonalDataResponse.builder()
                    .firstName(pd.getFirstName())
                    .lastName(pd.getLastName())
                    .middleName(pd.getMiddleName())
                    .fullName(fullName)
                    .phone(pd.getPhone())
                    .address(pd.getAddress())
                    .position(pd.getPosition())
                    .build());
            builder.displayName(fullName);
            builder.displayContact(pd.getPhone());
        }

        if (od != null) {
            builder.orgData(CustomerDto.OrgDataResponse.builder()
                    .orgName(od.getOrgName())
                    .legalFormId(od.getLegalFormId())
                    .inn(od.getInn())
                    .kpp(od.getKpp())
                    .ogrn(od.getOgrn())
                    .address(od.getAddress())
                    .build());
            if (customer.getCustomerType() == CustomerType.LEGAL_ENTITY) {
                builder.displayName(od.getOrgName());
                builder.displayContact("ИНН: " + od.getInn());
            }
        }

        return builder.build();
    }

    // ----------------------------------------------------------------
    //  Приватные вспомогательные методы
    // ----------------------------------------------------------------

    private void validateCreateRequest(CustomerDto.CreateRequest req) {
        boolean needPersonal = req.getCustomerType() != CustomerType.LEGAL_ENTITY;
        boolean needOrg      = req.getCustomerType() != CustomerType.INDIVIDUAL;

        if (needPersonal && req.getPersonalData() == null) {
            throw AppException.badRequest("PERSONAL_DATA_REQUIRED",
                    "Для физлица/ИП необходимы персональные данные");
        }
        if (needOrg && req.getOrgData() == null) {
            throw AppException.badRequest("ORG_DATA_REQUIRED",
                    "Для юрлица/ИП необходимы реквизиты организации");
        }
    }

    private void savePersonalData(UUID customerId, CustomerDto.PersonalDataRequest req) {
        var pd = CustomerPersonalData.builder()
                .customerId(customerId)
                .updatedAt(Instant.now())
                .build();
        applyPersonalData(pd, req);
        personalDataRepository.save(pd);
    }

    private void saveOrgData(UUID customerId, CustomerDto.OrgDataRequest req) {
        var od = CustomerOrgData.builder()
                .customerId(customerId)
                .updatedAt(Instant.now())
                .build();
        applyOrgData(od, req);
        orgDataRepository.save(od);
    }

    private void applyPersonalData(CustomerPersonalData pd, CustomerDto.PersonalDataRequest req) {
        pd.setFirstName(req.getFirstName());
        pd.setLastName(req.getLastName());
        pd.setMiddleName(req.getMiddleName());
        pd.setPhone(req.getPhone());
        pd.setAddress(req.getAddress());
        pd.setPosition(req.getPosition());
        pd.setUpdatedAt(Instant.now());
    }

    private void applyOrgData(CustomerOrgData od, CustomerDto.OrgDataRequest req) {
        od.setOrgName(req.getOrgName());
        od.setLegalFormId(req.getLegalFormId());
        od.setInn(req.getInn());
        od.setKpp(req.getKpp());
        od.setOgrn(req.getOgrn());
        od.setAddress(req.getAddress());
        od.setUpdatedAt(Instant.now());
    }

    private String buildFullName(String last, String first, String middle) {
        StringBuilder sb = new StringBuilder();
        if (last  != null) sb.append(last).append(" ");
        if (first != null) sb.append(first);
        if (middle != null && !middle.isBlank()) sb.append(" ").append(middle);
        return sb.toString().trim();
    }

    /**
     * Объединяем результаты FTS по физлицам и юрлицам,
     * убираем дубли (SOLE_TRADER может попасть в оба), обрезаем до size.
     */
    private List<Customer> merge(List<Customer> a, List<Customer> b, int size) {
        Map<UUID, Customer> map = new LinkedHashMap<>();
        a.forEach(c -> map.put(c.getId(), c));
        b.forEach(c -> map.putIfAbsent(c.getId(), c));
        return map.values().stream().limit(size).toList();
    }
}
