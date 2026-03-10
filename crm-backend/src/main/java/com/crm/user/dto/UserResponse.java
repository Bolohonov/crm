package com.crm.user.dto;

import lombok.Setter;
import lombok.Getter;
import lombok.Builder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
public class UserResponse {
    private UUID   id;
    private String email;
    private String firstName;
    private String lastName;
    private String middleName;
    private String phone;
    private String avatarUrl;
    private String userType;
    private String status;
    private boolean emailVerified;
    private Instant createdAt;
    private Instant lastLoginAt;
    /** Назначенные роли (только code + name + color) */
    private List<RoleRef> roles;
}
