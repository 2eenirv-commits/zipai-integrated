package com.onrender.zipai.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onrender.zipai.dto.chat.ChatRequest;
import com.onrender.zipai.dto.chat.ChatResponse;
import com.onrender.zipai.service.ChatService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/api/chat")
    public ChatResponse chat(@RequestBody(required = false) ChatRequest request) {
        return chatService.reply(request == null ? null : request.message());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ChatResponse handleException(Exception exception) {
        return new ChatResponse(
                "챗봇 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
                null,
                null);
    }
}
