package com.messenger.gateway.model.DTO;

import lombok.Data;
import java.util.List;

@Data
public class CreateChatRequest {
    private String name;
    private Boolean isGroup;
    private List<Long> participantIds;
}