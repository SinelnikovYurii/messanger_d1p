package com.messenger.core.service;

import com.messenger.core.dto.MessageDto;
import com.messenger.core.dto.UserDto;
import com.messenger.core.dto.request.SendMessageRequest;
import com.messenger.core.model.Chat;
import com.messenger.core.model.Message;
import com.messenger.core.model.User;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.MessageRepository;
import com.messenger.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional(readOnly = true)
    public Page<MessageDto> getChatMessages(Long chatId, Long userId, Pageable pageable) {
        // Проверяем доступ к чату
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        boolean hasAccess = chat.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(userId));
        if (!hasAccess) {
            throw new RuntimeException("Access denied");
        }

        return messageRepository.findByChatIdOrderBySentAtDesc(chatId, pageable)
                .map(this::convertToDto);
    }

    @Transactional
    public MessageDto sendMessage(SendMessageRequest request, Long senderId) {
        // Проверяем доступ к чату
        Chat chat = chatRepository.findById(request.getChatId())
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        boolean hasAccess = chat.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(senderId));
        if (!hasAccess) {
            throw new RuntimeException("Access denied to chat");
        }

        // Создаем сообщение
        Message message = new Message();
        message.setContent(request.getContent());
        message.setType(Message.MessageType.valueOf(request.getType()));
        message.setSender(sender);
        message.setChat(chat);
        message.setStatus(Message.MessageStatus.SENT);

        Message savedMessage = messageRepository.save(message);

        // Отправляем уведомление через Kafka
        try {
            String kafkaMessage = String.format(
                "{\"chatId\": %d, \"messageId\": %d, \"senderId\": %d, \"content\": \"%s\", \"type\": \"%s\"}",
                chat.getId(), savedMessage.getId(), senderId, request.getContent(), request.getType()
            );
            kafkaTemplate.send("chat-messages", kafkaMessage);
        } catch (Exception e) {
            // Логируем ошибку, но не прерываем выполнение
            System.err.println("Failed to send Kafka message: " + e.getMessage());
        }

        return convertToDto(savedMessage);
    }

    @Transactional
    public MessageDto editMessage(Long messageId, String newContent, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSender().getId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        message.setContent(newContent);
        message.setIsEdited(true);
        message.setEditedAt(java.time.LocalDateTime.now());

        Message savedMessage = messageRepository.save(message);
        return convertToDto(savedMessage);
    }

    @Transactional
    public void deleteMessage(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSender().getId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        messageRepository.delete(message);
    }

    @Transactional
    public void markMessagesAsRead(Long chatId, Long userId) {
        List<Message> messages = messageRepository.findByChatIdOrderBySentAtAsc(chatId);

        messages.stream()
                .filter(m -> !m.getSender().getId().equals(userId))
                .filter(m -> m.getStatus() != Message.MessageStatus.READ)
                .forEach(m -> {
                    m.setStatus(Message.MessageStatus.READ);
                    messageRepository.save(m);
                });
    }

    @Transactional(readOnly = true)
    public List<MessageDto> searchMessagesInChat(Long chatId, String query, Long userId) {
        // Проверяем доступ к чату
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        boolean hasAccess = chat.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(userId));
        if (!hasAccess) {
            throw new RuntimeException("Access denied");
        }

        return messageRepository.searchInChat(chatId, query).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public MessageDto convertToDto(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setContent(message.getContent());
        dto.setType(message.getType());
        dto.setSentAt(message.getSentAt());
        dto.setChatId(message.getChat().getId());
        dto.setIsEdited(message.getIsEdited());
        dto.setEditedAt(message.getEditedAt());
        dto.setStatus(message.getStatus());

        // Конвертируем отправителя
        UserDto senderDto = new UserDto();
        senderDto.setId(message.getSender().getId());
        senderDto.setUsername(message.getSender().getUsername());
        senderDto.setDisplayName(message.getSender().getDisplayName());
        senderDto.setAvatarUrl(message.getSender().getAvatarUrl());
        dto.setSender(senderDto);

        return dto;
    }
}
