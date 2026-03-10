package com.crm.user.dto;

import lombok.Setter;
import lombok.Getter;
import lombok.Builder;
import java.util.UUID;

/** Ответ на повторную отправку инвайта */
@Getter
@Setter
@Builder
public class ResendInviteResponse {
    private UUID   userId;
    private String email;
    private String message;
}
