package com.crm.audit.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter @Builder
public class EntityTimelineResponse {
    private UUID              entityId;
    private String            entityType;
    private List<AuditEntryResponse> timeline;
}
