package com.crm.rbac.dto;

import lombok.Setter;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class SetUserRolesRequest {
    private List<UUID> roleIds;
}
