package com.crm.task.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TaskDto {

    // ================================================================
    //  Запросы
    // ================================================================

    @Data
    public static class CreateRequest {
        @NotBlank(message = "Название задачи обязательно")
        @Size(max = 512)
        private String title;

        @Size(max = 4096)
        private String description;

        @NotNull(message = "Тип задачи обязателен")
        private UUID taskTypeId;

        @NotNull(message = "Статус задачи обязателен")
        private UUID statusId;

        private UUID assigneeId;
        private UUID customerId;
        private Instant scheduledAt;
    }

    @Data
    public static class UpdateRequest {
        @Size(max = 512)
        private String title;

        @Size(max = 4096)
        private String description;

        private UUID taskTypeId;
        private UUID statusId;
        private UUID assigneeId;
        private UUID customerId;
        private Instant scheduledAt;
    }

    @Data
    public static class FilterRequest {
        private UUID assigneeId;
        private UUID statusId;
        private UUID typeId;
        private UUID customerId;
        private int page = 0;
        private int size = 20;
    }

    @Data
    public static class CalendarRequest {
        @NotNull
        private Instant from;
        @NotNull
        private Instant to;
        private UUID assigneeId;
    }

    @Data
    public static class CommentRequest {
        @NotBlank
        @Size(max = 4096)
        private String content;
    }

    // ================================================================
    //  Ответы
    // ================================================================

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TaskResponse {
        private UUID id;
        private String title;
        private String description;

        // Обогащённые данные из словарей
        private UUID taskTypeId;
        private String taskTypeName;
        private String taskTypeColor;

        private UUID statusId;
        private String statusName;
        private String statusCode;
        private String statusColor;

        // Участники
        private UUID authorId;
        private String authorName;
        private UUID assigneeId;
        private String assigneeName;

        // Клиент
        private UUID customerId;
        private String customerName;

        // Время
        private Instant scheduledAt;
        private Instant completedAt;
        private Instant createdAt;
        private Instant updatedAt;

        // Флаги
        private boolean overdue;
        private boolean dueToday;

        // Комментарии
        private List<CommentResponse> comments;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CommentResponse {
        private UUID id;
        private UUID authorId;
        private String authorName;
        private String content;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PageResponse {
        private List<TaskResponse> content;
        private long totalElements;
        private int totalPages;
        private int page;
        private int size;
    }

    /** Облегчённый объект для календаря — без описания и комментариев */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CalendarEvent {
        private UUID id;
        private String title;
        private Instant scheduledAt;
        private Instant completedAt;
        private String statusCode;
        private String statusColor;
        private String typeColor;
        private UUID assigneeId;
        private String assigneeName;
        private boolean overdue;
    }

    @lombok.Data
    public static class ChangeStatusRequest {
        @jakarta.validation.constraints.NotNull
        private java.util.UUID statusId;
        @jakarta.validation.constraints.Size(max = 1000)
        private String comment;
    }
}
