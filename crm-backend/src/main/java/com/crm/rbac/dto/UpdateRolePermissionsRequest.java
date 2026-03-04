package com.crm.rbac.dto;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.UUID;
@Data
public class UpdateRolePermissionsRequest {
    @NotNull private List<UUID> permissionIds;
}
