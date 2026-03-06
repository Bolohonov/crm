package com.crm.customer.repository;

import com.crm.customer.entity.CustomerPersonalData;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerPersonalDataRepository extends CrudRepository<CustomerPersonalData, UUID> {

    Optional<CustomerPersonalData> findByCustomerId(UUID customerId);

    @Query("SELECT * FROM customer_personal_data WHERE customer_id IN (:ids)")
    List<CustomerPersonalData> findAllByCustomerIdIn(@Param("ids") List<UUID> ids);

    @Modifying
    @Query("""
        UPDATE customer_personal_data
        SET first_name = :#{#data.firstName},
            last_name  = :#{#data.lastName},
            middle_name = :#{#data.middleName},
            phone      = :#{#data.phone},
            address    = :#{#data.address},
            position   = :#{#data.position},
            updated_at = NOW()
        WHERE customer_id = :#{#data.customerId}
        """)
    void update(CustomerPersonalData data);
}
