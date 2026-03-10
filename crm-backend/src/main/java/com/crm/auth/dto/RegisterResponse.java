package com.crm.auth.dto;

import lombok.Setter;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private String message;
    private String email;
}
