package com.crm.tenant;

public class TenantProvisioningException extends RuntimeException {

    public TenantProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }

    public TenantProvisioningException(String message) {
        super(message);
    }
}
