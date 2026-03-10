package com.crm.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

/** Приглашение нового пользователя в тенант */
@Getter
@Setter
public class InviteRequest {
    @NotBlank @Email
    private String email;
    @NotBlank @Size(max = 100)
    private String firstName;
    @NotBlank @Size(max = 100)
    private String lastName;
    @Size(max = 100)
    private String middleName;
    @Size(max = 20)
    private String phone;
    /** Список roleId для назначения (опционально) */
    private List<UUID> roleIds;
}
