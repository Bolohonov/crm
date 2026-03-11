
package com.crm.common.config;
import lombok.Setter;
import lombok.Getter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Типизированные настройки приложения из application.yml (секция app.*).
 * Инжектируются через конструктор, не через @Value.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl;
    private String frontendUrl;
    private String internalToken;

    private Jwt jwt = new Jwt();
    private Email email = new Email();
    private Tenant tenant = new Tenant();
    private Cors cors = new Cors();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenExpiration = 900;       // 15 мин
        private long refreshTokenExpiration = 604800;   // 7 дней
    }

    @Getter
    @Setter
    public static class Email {
        private String from;
        private String fromName;
        private long verificationTokenExpiration = 86400;  // 24 часа
        private long inviteTokenExpiration = 259200;       // 3 дня
    }

    @Getter
    @Setter
    public static class Tenant {
        private String schemaPrefix = "tenant_";
        private int freePlanUserLimit = 3;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }
}
