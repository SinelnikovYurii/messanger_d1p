package com.messenger.gateway.service;

import com.messenger.gateway.model.Chat;
import com.messenger.gateway.model.DTO.ChatDto;
import com.messenger.gateway.model.DTO.CreateChatRequest;
import com.messenger.gateway.model.User;
import com.messenger.gateway.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final UserService userService;

    public List<ChatDto> getUserChats(Long userId) {
        return chatRepository.findChatsByUserId(userId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public ChatDto createChat(CreateChatRequest request) {
        Chat chat = new Chat();
        chat.setName(request.getName());
        chat.setIsGroup(request.getIsGroup());

        List<User> participants = request.getParticipantIds().stream()
                .map(userService::getUserEntityById)
                .collect(Collectors.toList());
        chat.setParticipants(participants);

        Chat savedChat = chatRepository.save(chat);
        return convertToDto(savedChat);
    }

    public ChatDto getChatById(Long id) {
        return chatRepository.findById(id)
                .map(this::convertToDto)
                .orElse(null);
    }

    private ChatDto convertToDto(Chat chat) {
        ChatDto dto = new ChatDto();
        dto.setId(chat.getId());
        dto.setName(chat.getName());
        dto.setIsGroup(chat.getIsGroup());
        dto.setCreatedAt(chat.getCreatedAt());

        return dto;
    }
}
