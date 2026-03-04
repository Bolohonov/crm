package com.crm.dashboard.dto;

import java.time.Instant;

public record RecentActivityDto(
    String  id,
    String  type,
    String  description,
    String  entityId,
    String  entityType,
    Instant createdAt,
    String  userName
) {}
