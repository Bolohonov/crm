package com.crm.task;

import com.crm.common.TestSecurityUtils;
import com.crm.common.exception.AppException;
import com.crm.task.controller.TaskController;
import com.crm.task.dto.TaskDto;
import com.crm.task.service.TaskService;
import com.crm.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
@DisplayName("TaskController")
class TaskControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TaskService taskService;

    private MockedStatic<TenantContext> tenantMock;

    @BeforeEach
    void setUp() {
        tenantMock = Mockito.mockStatic(TenantContext.class);
        tenantMock.when(TenantContext::get).thenReturn(TestSecurityUtils.testTenant());
        tenantMock.when(TenantContext::getCurrentSchema)
                  .thenReturn(TestSecurityUtils.TEST_SCHEMA);
    }

    @AfterEach
    void tearDown() { tenantMock.close(); }

    // ── Builders ──────────────────────────────────────────────────
    private TaskDto.TaskResponse task(UUID id, String priority) {
        return TaskDto.TaskResponse.builder()
            .id(id)
            .title("Тестовая задача")
            .statusCode("NEW")
            .statusName("Новая")
            .priority(priority)
            .createdAt(Instant.now())
            .build();
    }

    private TaskDto.CommentResponse comment(UUID taskId) {
        return TaskDto.CommentResponse.builder()
            .id(UUID.randomUUID())
            .taskId(taskId)
            .content("Тестовый комментарий")
            .authorName("Иван Петров")
            .createdAt(Instant.now())
            .build();
    }

    // ── GET /tasks ────────────────────────────────────────────────
    @Nested @DisplayName("GET /tasks")
    class ListTasks {

        @Test
        @WithMockUser
        @DisplayName("возвращает список задач — 200")
        void list_success() throws Exception {
            when(taskService.list(any())).thenReturn(
                TaskDto.PageResponse.builder()
                    .content(List.of(
                        task(UUID.randomUUID(), "HIGH"),
                        task(UUID.randomUUID(), "MEDIUM")
                    ))
                    .page(0).size(20).totalElements(2).totalPages(1)
                    .build()
            );

            mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @WithMockUser
        @DisplayName("фильтр по assigneeId передаётся в сервис")
        void list_filterByAssignee() throws Exception {
            UUID assigneeId = UUID.randomUUID();
            when(taskService.list(any())).thenReturn(
                TaskDto.PageResponse.builder().content(List.of())
                    .page(0).size(20).totalElements(0).totalPages(0).build()
            );

            mockMvc.perform(get("/tasks").param("assigneeId", assigneeId.toString()))
                .andExpect(status().isOk());

            verify(taskService).list(argThat(req ->
                assigneeId.equals(req.getAssigneeId())
            ));
        }
    }

    // ── GET /tasks/today ──────────────────────────────────────────
    @Nested @DisplayName("GET /tasks/today")
    class TodayTasks {

        @Test
        @WithMockUser
        @DisplayName("задачи на сегодня для текущего пользователя")
        void today_forCurrentUser() throws Exception {
            when(taskService.getToday(any())).thenReturn(List.of(
                task(UUID.randomUUID(), "CRITICAL"),
                task(UUID.randomUUID(), "HIGH")
            ));

            mockMvc.perform(get("/tasks/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @WithMockUser
        @DisplayName("задачи на сегодня для конкретного исполнителя")
        void today_forSpecificAssignee() throws Exception {
            UUID assigneeId = UUID.randomUUID();
            when(taskService.getToday(assigneeId)).thenReturn(List.of());

            mockMvc.perform(get("/tasks/today")
                    .param("assigneeId", assigneeId.toString()))
                .andExpect(status().isOk());

            verify(taskService).getToday(assigneeId);
        }
    }

    // ── GET /tasks/calendar ───────────────────────────────────────
    @Nested @DisplayName("GET /tasks/calendar")
    class CalendarTasks {

        @Test
        @WithMockUser
        @DisplayName("возвращает задачи в диапазоне дат")
        void calendar_success() throws Exception {
            Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
            Instant to   = Instant.now().plus(7, ChronoUnit.DAYS);

            when(taskService.getCalendar(any())).thenReturn(List.of(
                TaskDto.CalendarEvent.builder()
                    .taskId(UUID.randomUUID())
                    .title("Встреча с клиентом")
                    .priority("HIGH")
                    .start(Instant.now())
                    .build()
            ));

            mockMvc.perform(get("/tasks/calendar")
                    .param("from", from.toString())
                    .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Встреча с клиентом"));
        }
    }

    // ── POST /tasks ───────────────────────────────────────────────
    @Nested @DisplayName("POST /tasks")
    class CreateTask {

        @Test
        @WithMockUser
        @DisplayName("создание задачи — 201")
        void create_success() throws Exception {
            UUID newId = UUID.randomUUID();
            when(taskService.create(any(), any())).thenReturn(task(newId, "MEDIUM"));

            var req = new TaskDto.CreateRequest();
            req.setTitle("Новая задача для теста");
            req.setScheduledAt(Instant.now().plus(1, ChronoUnit.DAYS));

            mockMvc.perform(post("/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(newId.toString()))
                .andExpect(jsonPath("$.data.title").value("Тестовая задача"));
        }

        @Test
        @WithMockUser
        @DisplayName("заголовок пустой — 400")
        void create_emptyTitle() throws Exception {
            var req = new TaskDto.CreateRequest();
            req.setTitle("");   // @NotBlank должен сработать

            mockMvc.perform(post("/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ── PATCH /tasks/{id}/assign ──────────────────────────────────
    @Nested @DisplayName("PATCH /tasks/{id}/assign")
    class AssignTask {

        @Test
        @WithMockUser
        @DisplayName("успешное назначение исполнителя")
        void assign_success() throws Exception {
            UUID taskId     = UUID.randomUUID();
            UUID assigneeId = UUID.randomUUID();
            doNothing().when(taskService).assign(taskId, assigneeId);

            mockMvc.perform(patch("/tasks/{id}/assign", taskId)
                    .param("assigneeId", assigneeId.toString()))
                .andExpect(status().isOk());

            verify(taskService).assign(taskId, assigneeId);
        }

        @Test
        @WithMockUser
        @DisplayName("задача не найдена — 404")
        void assign_taskNotFound() throws Exception {
            UUID taskId     = UUID.randomUUID();
            UUID assigneeId = UUID.randomUUID();
            doThrow(AppException.notFound("Task"))
                .when(taskService).assign(taskId, assigneeId);

            mockMvc.perform(patch("/tasks/{id}/assign", taskId)
                    .param("assigneeId", assigneeId.toString()))
                .andExpect(status().isNotFound());
        }
    }

    // ── Комментарии ───────────────────────────────────────────────
    @Nested @DisplayName("Comments /tasks/{id}/comments")
    class Comments {

        @Test
        @WithMockUser
        @DisplayName("GET comments — список комментариев задачи")
        void getComments_success() throws Exception {
            UUID taskId = UUID.randomUUID();
            when(taskService.getComments(taskId)).thenReturn(List.of(
                comment(taskId), comment(taskId)
            ));

            mockMvc.perform(get("/tasks/{id}/comments", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].content").value("Тестовый комментарий"));
        }

        @Test
        @WithMockUser
        @DisplayName("POST comment — добавление комментария — 201")
        void addComment_success() throws Exception {
            UUID taskId = UUID.randomUUID();
            when(taskService.addComment(eq(taskId), any(), any()))
                .thenReturn(comment(taskId));

            var req = new TaskDto.CommentRequest();
            req.setContent("Новый комментарий к задаче");

            mockMvc.perform(post("/tasks/{id}/comments", taskId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").isNotEmpty());
        }

        @Test
        @WithMockUser
        @DisplayName("POST comment — пустой контент — 400")
        void addComment_emptyContent() throws Exception {
            UUID taskId = UUID.randomUUID();
            var req = new TaskDto.CommentRequest();
            req.setContent("");

            mockMvc.perform(post("/tasks/{id}/comments", taskId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("DELETE comment — удаление — 200")
        void deleteComment_success() throws Exception {
            UUID taskId    = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();
            doNothing().when(taskService).deleteComment(eq(commentId), any());

            mockMvc.perform(delete("/tasks/{taskId}/comments/{commentId}", taskId, commentId))
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser
        @DisplayName("DELETE comment чужой — 403")
        void deleteComment_notOwner() throws Exception {
            UUID taskId    = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();
            doThrow(AppException.forbidden("Комментарий принадлежит другому пользователю"))
                .when(taskService).deleteComment(eq(commentId), any());

            mockMvc.perform(delete("/tasks/{taskId}/comments/{commentId}", taskId, commentId))
                .andExpect(status().isForbidden());
        }
    }
}
