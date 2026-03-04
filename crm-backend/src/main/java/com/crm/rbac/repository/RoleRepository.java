package com.crm.rbac.repository;

import com.crm.rbac.entity.Role;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends CrudRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    List<Role> findAll();

    @Query("""
        SELECT r.*
        FROM roles r
        INNER JOIN user_roles ur ON ur.role_id = r.id
        WHERE ur.user_id = :userId
        """)
    List<Role> findRolesByUserId(UUID userId);

    boolean existsByCode(String code);

    @Modifying
    @Query("DELETE FROM roles WHERE id = :id AND is_system = false")
    void deleteNonSystemById(UUID id);
}
