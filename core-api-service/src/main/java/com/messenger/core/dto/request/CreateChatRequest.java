package com.messenger.core.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class CreateChatRequest {
    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String type = "GROUP";

    @NotEmpty
    private List<Long> participantIds;
}
