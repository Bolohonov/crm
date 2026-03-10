package com.crm.user.dto;

import com.crm.user.entity.UserStatus;
import lombok.Setter;
import lombok.Getter;

/** Блокировка / разблокировка */
@Getter
@Setter
public class SetStatusRequest {
    private UserStatus status;
    private String reason;
}
