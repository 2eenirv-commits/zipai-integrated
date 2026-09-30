package com.onrender.zipai.dto.chat;

public record ChatResponse(
        String message,
        String actionLabel,
        String actionUrl) {
}
