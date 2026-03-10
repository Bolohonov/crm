
package com.crm.rbac.dto;
import lombok.Setter;
import lombok.Getter;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
@Getter
@Setter
public class UpdateRolePermissionsRequest {
    @NotNull private List<UUID> permissionIds;
}
