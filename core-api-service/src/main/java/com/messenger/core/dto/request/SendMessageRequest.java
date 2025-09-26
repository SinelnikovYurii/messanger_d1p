package com.messenger.core.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class SendMessageRequest {
    @NotNull
    private Long chatId;

    @NotBlank
    private String content;

    private String type = "TEXT";
}
