package com.crm.user.dto;

import lombok.Setter;
import lombok.Getter;
import lombok.Builder;
import java.util.UUID;

@Getter
@Setter
@Builder
public class RoleRef {
    private UUID   id;
    private String code;
    private String name;
    private String color;
}
