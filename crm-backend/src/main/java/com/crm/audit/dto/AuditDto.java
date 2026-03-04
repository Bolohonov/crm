package com.crm.audit.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuditDto {

    @Getter @Builder
    public static class AuditEntryResponse {
        private UUID              id;
        private String            entityType;
        private UUID              entityId;
        private String            action;
        private String            actionLabel;      // человекочитаемая метка
        private UUID              actorId;
        private String            actorName;
        private Map<String,Object> changes;
        private String            comment;
        private Instant           createdAt;
    }

    @Getter @Builder
    public static class EntityTimelineResponse {
        private UUID              entityId;
        private String            entityType;
        private List<AuditEntryResponse> timeline;
    }

    @Getter @Builder
    public static class StatusChangeRequest {
        private UUID   newStatusId;
        private String comment;
    }
}
