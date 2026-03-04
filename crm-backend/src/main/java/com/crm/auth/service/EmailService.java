package com.crm.auth.service;

import com.crm.auth.entity.EmailVerification;
import com.crm.auth.entity.EmailVerificationType;
import com.crm.auth.repository.EmailVerificationRepository;
import com.crm.common.config.AppProperties;
import com.crm.common.exception.AppException;
import com.crm.user.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.UUID;

/**
 * Сервис отправки email-уведомлений.
 *
 * Все отправки асинхронны (@Async) — не блокируют HTTP-запрос.
 * Шаблоны — Thymeleaf HTML в resources/templates/email/
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailVerificationRepository verificationRepository;
    private final AppProperties appProperties;

    // ----------------------------------------------------------------
    //  Публичные методы отправки
    // ----------------------------------------------------------------

    /**
     * Прямое письмо-приглашение пользователю со ссылкой для установки пароля.
     */
    @Async
    public void sendInviteEmail(String toEmail, String firstName, String token) {
        String inviteUrl = appProperties.getFrontendUrl()
            + "/auth/accept-invite?token=" + token;

        Context ctx = new Context();
        ctx.setVariable("userName", firstName);
        ctx.setVariable("inviteUrl", inviteUrl);
        ctx.setVariable("expireDays", 7);

        sendHtml(toEmail, "Вас пригласили в CRM Cloud", "email/invite-user", ctx);
        log.info("Invite email sent to: {}", toEmail);
    }

    /**
     * Письмо подтверждения регистрации для ADMIN пользователя.
     */
    @Async
    @Transactional
    public void sendRegistrationConfirmation(User user) {
        String token = createVerificationToken(user.getId(), EmailVerificationType.REGISTRATION,
            appProperties.getEmail().getVerificationTokenExpiration());

        String confirmUrl = appProperties.getFrontendUrl()
            + "/auth/verify?token=" + token;

        Context ctx = new Context();
        ctx.setVariable("userName", user.getFirstName());
        ctx.setVariable("confirmUrl", confirmUrl);
        ctx.setVariable("expiresHours", 24);

        sendHtml(
            user.getEmail(),
            "Подтверждение регистрации в CRM Cloud",
            "email/registration-confirm",
            ctx
        );

        log.info("Registration confirmation sent to: {}", user.getEmail());
    }

    /**
     * Инвайт обычному пользователю — отправляется администратору для подтверждения,
     * затем отправляется и самому пользователю.
     */
    @Async
    @Transactional
    public void sendInviteToAdmin(User regularUser, User adminUser) {
        String token = createVerificationToken(regularUser.getId(), EmailVerificationType.INVITE,
            appProperties.getEmail().getInviteTokenExpiration());

        String approveUrl = appProperties.getFrontendUrl()
            + "/admin/invite-approve?token=" + token;

        String rejectUrl = appProperties.getFrontendUrl()
            + "/admin/invite-reject?token=" + token;

        Context ctx = new Context();
        ctx.setVariable("adminName", adminUser.getFirstName());
        ctx.setVariable("userName", regularUser.getFullName());
        ctx.setVariable("userEmail", regularUser.getEmail());
        ctx.setVariable("approveUrl", approveUrl);
        ctx.setVariable("rejectUrl", rejectUrl);

        sendHtml(
            adminUser.getEmail(),
            "Запрос на доступ к CRM Cloud",
            "email/invite-admin",
            ctx
        );

        log.info("Invite request sent to admin: {} for user: {}",
            adminUser.getEmail(), regularUser.getEmail());
    }

    /**
     * Уведомление пользователю о принятии инвайта.
     */
    @Async
    public void sendInviteApproved(User regularUser, String loginUrl) {
        Context ctx = new Context();
        ctx.setVariable("userName", regularUser.getFirstName());
        ctx.setVariable("loginUrl", loginUrl);

        sendHtml(
            regularUser.getEmail(),
            "Ваш доступ к CRM Cloud одобрен",
            "email/invite-approved",
            ctx
        );
    }

    /**
     * Письмо для сброса пароля.
     */
    @Async
    @Transactional
    public void sendPasswordReset(User user) {
        String token = createVerificationToken(user.getId(), EmailVerificationType.PASSWORD_RESET,
            3600); // 1 час

        String resetUrl = appProperties.getFrontendUrl()
            + "/auth/reset-password?token=" + token;

        Context ctx = new Context();
        ctx.setVariable("userName", user.getFirstName());
        ctx.setVariable("resetUrl", resetUrl);

        sendHtml(
            user.getEmail(),
            "Сброс пароля CRM Cloud",
            "email/password-reset",
            ctx
        );
    }

    /**
     * Валидирует токен верификации и возвращает userId.
     */
    @Transactional
    public UUID validateVerificationToken(String token) {
        EmailVerification verification = verificationRepository.findByToken(token)
            .orElseThrow(() -> AppException.badRequest("INVALID_TOKEN", "Токен недействителен"));

        if (!verification.isValid()) {
            throw AppException.badRequest("TOKEN_EXPIRED", "Срок действия токена истёк");
        }

        verificationRepository.markAsUsed(verification.getId());
        return verification.getUserId();
    }

    /**
     * Очистка устаревших токенов — каждую ночь в 02:00.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredVerifications() {
        verificationRepository.deleteExpiredAndUsed(Instant.now());
        log.info("Expired email verifications cleanup completed");
    }

    // ----------------------------------------------------------------
    //  Приватные вспомогательные методы
    // ----------------------------------------------------------------

    private String createVerificationToken(UUID userId, EmailVerificationType type,
                                           long expirationSeconds) {
        String token = UUID.randomUUID().toString().replace("-", "") +
                       UUID.randomUUID().toString().replace("-", "");

        EmailVerification verification = EmailVerification.builder()
            .userId(userId)
            .token(token)
            .type(type)
            .expiresAt(Instant.now().plusSeconds(expirationSeconds))
            .used(false)
            .createdAt(Instant.now())
            .build();

        verificationRepository.save(verification);
        return token;
    }

    private void sendHtml(String to, String subject, String templateName, Context ctx) {
        try {
            String html = templateEngine.process(templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(
                appProperties.getEmail().getFrom(),
                appProperties.getEmail().getFromName()
            );
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);

        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to: {}", to, e);
            // Не пробрасываем исключение — email-ошибка не должна ломать бизнес-логику
            // В prod стоит добавить retry механизм или очередь (RabbitMQ/Kafka)
        }
    }
}
