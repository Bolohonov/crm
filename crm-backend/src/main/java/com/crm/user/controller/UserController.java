package com.crm.user.controller;
import com.crm.user.dto.UserPageResponse;

import com.crm.user.dto.AcceptInviteRequest;
import com.crm.user.dto.ChangePasswordRequest;
import com.crm.user.dto.InviteRequest;
import com.crm.user.dto.SelfChangePasswordRequest;
import com.crm.user.dto.SetStatusRequest;
import com.crm.user.dto.UpdateProfileRequest;
import com.crm.user.dto.UserResponse;

import com.crm.common.response.ApiResponse;
import com.crm.rbac.config.Permissions;
import com.crm.user.entity.User;
import com.crm.user.entity.UserStatus;
import com.crm.user.service.InviteService;
import com.crm.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Управление пользователями внутри тенанта.
 *
 * GET    /api/v1/users                    — список пользователей [USER_VIEW]
 * GET    /api/v1/users/{id}               — профиль пользователя [USER_VIEW]
 * PUT    /api/v1/users/{id}               — обновить профиль [USER_MANAGE]
 * PATCH  /api/v1/users/{id}/status        — блокировка/разблокировка [USER_MANAGE]
 * PATCH  /api/v1/users/{id}/password      — сменить пароль [USER_MANAGE]
 * POST   /api/v1/users/{id}/resend-invite — повторная отправка инвайта [USER_MANAGE]
 * DELETE /api/v1/users/{id}               — деактивировать [USER_MANAGE]
 * PATCH  /api/v1/users/me/password        — сменить собственный пароль
 *
 * POST   /api/v1/users/invite             — пригласить пользователя [USER_MANAGE]
 * POST   /api/v1/users/accept-invite      — принять приглашение (публичный)
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService   userService;
    private final InviteService inviteService;

    // ── Список пользователей ──────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.USER_VIEW + "')")
    public ResponseEntity<ApiResponse<UserPageResponse>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String q) {

        return ResponseEntity.ok(ApiResponse.ok(userService.list(page, size, q)));
    }

    // ── Профиль пользователя ──────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.USER_VIEW + "') or #id == authentication.principal.id")
    public ResponseEntity<ApiResponse<UserResponse>> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.ok(userService.getById(id)));
    }

    // ── Обновить профиль ──────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "') or #id == authentication.principal.id")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProfileRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(id, request)));
    }

    // ── Изменить статус (блокировка) ──────────────────────────────
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @PathVariable UUID id,
            @RequestBody SetStatusRequest request,
            @AuthenticationPrincipal User currentUser) {

        userService.setStatus(id, request.getStatus(), currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── Смена пароля администратором ──────────────────────────────
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable UUID id,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(id, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── Собственная смена пароля ──────────────────────────────────
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> selfChangePassword(
            @Valid @RequestBody SelfChangePasswordRequest request,
            @AuthenticationPrincipal User currentUser) {

        userService.selfChangePassword(
            currentUser.getId(),
            request.getCurrentPassword(),
            request.getNewPassword()
        );
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── Деактивировать пользователя ───────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        userService.deactivate(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── Пригласить пользователя ───────────────────────────────────
    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
    public ResponseEntity<ApiResponse<UserResponse>> invite(
            @Valid @RequestBody InviteRequest request) {

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok(inviteService.invite(request)));
    }

    // ── Принять приглашение (публичный — без авторизации) ─────────
    @PostMapping("/accept-invite")
    public ResponseEntity<ApiResponse<Void>> acceptInvite(
            @Valid @RequestBody AcceptInviteRequest request) {

        inviteService.acceptInvite(request.getToken(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── Повторная отправка приглашения ────────────────────────────
    @PostMapping("/{id}/resend-invite")
    @PreAuthorize("hasAuthority('" + Permissions.USER_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> resendInvite(
            @PathVariable UUID id) {

        inviteService.resendInvite(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
