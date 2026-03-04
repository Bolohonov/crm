package com.crm.user.entity;

public enum UserType {
    ADMIN,      // Администратор — владелец тенанта
    REGULAR,    // Обычный пользователь
    EMPLOYEE    // Сотрудник (приглашённый через InviteService)
}
