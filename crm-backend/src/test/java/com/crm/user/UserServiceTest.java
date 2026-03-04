package com.crm.user;

import com.crm.common.TestSecurityUtils;
import com.crm.common.exception.AppException;
import com.crm.rbac.repository.RoleRepository;
import com.crm.rbac.repository.UserRoleRepository;
import com.crm.tenant.TenantContext;
import com.crm.user.dto.UserDto;
import com.crm.user.entity.User;
import com.crm.user.entity.UserStatus;
import com.crm.user.entity.UserType;
import com.crm.user.repository.UserRepository;
import com.crm.user.service.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService (unit)")
class UserServiceTest {

    @Mock UserRepository     userRepository;
    @Mock RoleRepository     roleRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock PasswordEncoder    passwordEncoder;

    @InjectMocks UserService userService;

    private MockedStatic<TenantContext> tenantMock;

    @BeforeEach
    void setUp() {
        tenantMock = mockStatic(TenantContext.class);
        tenantMock.when(TenantContext::get).thenReturn(TestSecurityUtils.testTenant());
    }

    @AfterEach
    void tearDown() { tenantMock.close(); }

    // ── setStatus ─────────────────────────────────────────────────
    @Nested @DisplayName("setStatus()")
    class SetStatus {

        @Test
        @DisplayName("блокировка другого пользователя — успех")
        void block_otherUser_success() {
            UUID targetId = UUID.randomUUID();
            UUID actorId  = TestSecurityUtils.ADMIN_ID;

            User target = TestSecurityUtils.managerUser();
            target.setId(targetId);

            when(userRepository.findById(targetId))
                .thenReturn(Optional.of(target));
            doNothing().when(userRepository)
                .updateStatus(any(), any(), any());

            assertThatCode(() ->
                userService.setStatus(targetId, UserStatus.BLOCKED, actorId)
            ).doesNotThrowAnyException();

            verify(userRepository).updateStatus(eq(targetId), eq("BLOCKED"), any());
        }

        @Test
        @DisplayName("попытка заблокировать себя — AppException 400")
        void block_self_throwsBadRequest() {
            UUID selfId = TestSecurityUtils.ADMIN_ID;

            User self = TestSecurityUtils.adminUser();
            when(userRepository.findById(selfId))
                .thenReturn(Optional.of(self));

            assertThatThrownBy(() ->
                userService.setStatus(selfId, UserStatus.BLOCKED, selfId)
            )
            .isInstanceOf(AppException.class)
            .hasMessageContaining("собственный");
        }

        @Test
        @DisplayName("попытка заблокировать ADMIN — AppException 403")
        void block_admin_throwsForbidden() {
            UUID actorId  = TestSecurityUtils.MANAGER_ID;
            UUID targetId = TestSecurityUtils.ADMIN_ID;

            User target = TestSecurityUtils.adminUser(); // ADMIN
            when(userRepository.findById(targetId))
                .thenReturn(Optional.of(target));

            assertThatThrownBy(() ->
                userService.setStatus(targetId, UserStatus.BLOCKED, actorId)
            )
            .isInstanceOf(AppException.class);
        }

        @Test
        @DisplayName("пользователь не из тенанта — AppException 404")
        void block_wrongTenant_notFound() {
            UUID targetId = UUID.randomUUID();

            // Пользователь существует, но принадлежит другому тенанту
            User alienUser = TestSecurityUtils.managerUser();
            alienUser.setId(targetId);
            alienUser.setTenantId(UUID.randomUUID()); // другой тенант

            when(userRepository.findById(targetId))
                .thenReturn(Optional.of(alienUser));

            assertThatThrownBy(() ->
                userService.setStatus(targetId, UserStatus.BLOCKED, TestSecurityUtils.ADMIN_ID)
            )
            .isInstanceOf(AppException.class);
        }
    }

    // ── selfChangePassword ────────────────────────────────────────
    @Nested @DisplayName("selfChangePassword()")
    class SelfChangePassword {

        @Test
        @DisplayName("правильный текущий пароль — успешная смена")
        void correctCurrentPassword_success() {
            UUID userId = TestSecurityUtils.ADMIN_ID;
            User user   = TestSecurityUtils.adminUser();
            user.setPasswordHash("$2a$hashed_old");

            when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass123!", "$2a$hashed_old"))
                .thenReturn(true);
            when(passwordEncoder.encode("NewPass456!"))
                .thenReturn("$2a$hashed_new");

            assertThatCode(() ->
                userService.selfChangePassword(userId, "OldPass123!", "NewPass456!")
            ).doesNotThrowAnyException();

            verify(userRepository).updatePassword(userId, "$2a$hashed_new");
        }

        @Test
        @DisplayName("неверный текущий пароль — AppException 400")
        void wrongCurrentPassword_throwsBadRequest() {
            UUID userId = TestSecurityUtils.ADMIN_ID;
            User user   = TestSecurityUtils.adminUser();
            user.setPasswordHash("$2a$hashed_old");

            when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPass!", "$2a$hashed_old"))
                .thenReturn(false);

            assertThatThrownBy(() ->
                userService.selfChangePassword(userId, "WrongPass!", "NewPass456!")
            )
            .isInstanceOf(AppException.class)
            .hasMessageContaining("неверен");

            verify(userRepository, never()).updatePassword(any(), any());
        }
    }

    // ── deactivate ────────────────────────────────────────────────
    @Nested @DisplayName("deactivate()")
    class Deactivate {

        @Test
        @DisplayName("деактивация другого пользователя — отзываются роли")
        void deactivate_success_rolesRevoked() {
            UUID targetId = UUID.randomUUID();
            UUID actorId  = TestSecurityUtils.ADMIN_ID;

            User target = TestSecurityUtils.managerUser();
            target.setId(targetId);

            when(userRepository.findById(targetId))
                .thenReturn(Optional.of(target));

            assertThatCode(() ->
                userService.deactivate(targetId, actorId)
            ).doesNotThrowAnyException();

            verify(userRepository).updateStatus(eq(targetId), eq("BLOCKED"), any());
            verify(userRoleRepository).deleteAllByUserId(targetId);
        }

        @Test
        @DisplayName("деактивация себя — запрещено 400")
        void deactivate_self_throwsBadRequest() {
            UUID selfId = TestSecurityUtils.ADMIN_ID;
            User self = TestSecurityUtils.adminUser();

            when(userRepository.findById(selfId))
                .thenReturn(Optional.of(self));

            assertThatThrownBy(() ->
                userService.deactivate(selfId, selfId)
            )
            .isInstanceOf(AppException.class)
            .hasMessageContaining("себя");

            verify(userRepository, never()).updateStatus(any(), any(), any());
            verify(userRoleRepository, never()).deleteAllByUserId(any());
        }
    }

    // ── list / поиск ──────────────────────────────────────────────
    @Nested @DisplayName("list()")
    class ListUsers {

        @Test
        @DisplayName("поиск по имени — фильтрует пользователей")
        void search_byName() {
            User ivan = TestSecurityUtils.adminUser(); // firstName = "Иван"
            User anna = TestSecurityUtils.managerUser(); // firstName = "Анна"

            when(userRepository.findAllByTenantId(TestSecurityUtils.TENANT_ID))
                .thenReturn(List.of(ivan, anna));
            when(userRoleRepository.findByUserId(any()))
                .thenReturn(List.of());

            UserDto.PageResponse result = userService.list(0, 20, "Иван");

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getFirstName()).isEqualTo("Иван");
        }

        @Test
        @DisplayName("без фильтра — возвращает всех пользователей тенанта")
        void list_noFilter_returnsAll() {
            when(userRepository.findAllByTenantId(TestSecurityUtils.TENANT_ID))
                .thenReturn(List.of(
                    TestSecurityUtils.adminUser(),
                    TestSecurityUtils.managerUser()
                ));
            when(userRoleRepository.findByUserId(any()))
                .thenReturn(List.of());

            UserDto.PageResponse result = userService.list(0, 20, null);

            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("пагинация — page=1, size=1")
        void list_pagination() {
            when(userRepository.findAllByTenantId(TestSecurityUtils.TENANT_ID))
                .thenReturn(List.of(
                    TestSecurityUtils.adminUser(),
                    TestSecurityUtils.managerUser()
                ));
            when(userRoleRepository.findByUserId(any()))
                .thenReturn(List.of());

            UserDto.PageResponse page0 = userService.list(0, 1, null);
            UserDto.PageResponse page1 = userService.list(1, 1, null);

            assertThat(page0.getContent()).hasSize(1);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            assertThat(page1.getContent()).hasSize(1);
        }
    }
}
