package com.crm.customer.repository;

import com.crm.customer.entity.CustomerOrgData;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerOrgDataRepository extends CrudRepository<CustomerOrgData, UUID> {

    Optional<CustomerOrgData> findByCustomerId(UUID customerId);

    @Modifying
    @Query("""
        UPDATE customer_org_data
        SET org_name     = :#{#data.orgName},
            legal_form_id = :#{#data.legalFormId},
            inn          = :#{#data.inn},
            kpp          = :#{#data.kpp},
            ogrn         = :#{#data.ogrn},
            address      = :#{#data.address},
            updated_at   = NOW()
        WHERE customer_id = :#{#data.customerId}
        """)
    void update(CustomerOrgData data);
}
