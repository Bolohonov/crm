package com.crm.user.entity;

public enum UserStatus {
    PENDING,  // Зарегистрирован, email не подтверждён
    ACTIVE,   // Активен, может входить в систему
    BLOCKED   // Заблокирован администратором
}
