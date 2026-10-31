package ru.mirea.visitpass.security;

import java.util.UUID;

public record JwtPrincipal(UUID id, String email) {
}
