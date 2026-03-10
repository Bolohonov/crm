package com.crm.auth.dto;

import lombok.Setter;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String middleName;
    private String phone;
    private String avatarUrl;
    private String userType;
    private UUID tenantId;
}
