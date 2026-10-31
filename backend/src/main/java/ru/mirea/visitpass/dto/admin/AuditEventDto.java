package ru.mirea.visitpass.dto.admin;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditEventDto(
    LocalDateTime createdAt,
    String eventType,
    String entityType,
    UUID entityUuid,
    String description,
    String correlationId,
    String username) {
}
