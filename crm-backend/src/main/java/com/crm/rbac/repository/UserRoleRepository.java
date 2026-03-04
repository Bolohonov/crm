package com.crm.rbac.repository;

import com.crm.rbac.entity.UserRole;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends CrudRepository<UserRole, UUID> {

    @Query("SELECT * FROM user_roles WHERE user_id = :userId")
    List<UserRole> findByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM user_roles WHERE user_id = :userId AND role_id = :roleId")
    void deleteByUserIdAndRoleId(UUID userId, UUID roleId);

    @Modifying
    @Query("DELETE FROM user_roles WHERE user_id = :userId")
    void deleteAllByUserId(UUID userId);

    @Query("SELECT EXISTS(SELECT 1 FROM user_roles WHERE user_id = :userId AND role_id = :roleId)")
    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);
}
