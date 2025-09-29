package com.messenger.core.service;

import com.messenger.core.dto.MessageDto;
import com.messenger.core.model.Chat;
import com.messenger.core.model.Message;
import com.messenger.core.model.User;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.MessageRepository;
import com.messenger.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Отправить сообщение в чат
     */
    public MessageDto sendMessage(Long userId, MessageDto.SendMessageRequest request) {
        User sender = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        Chat chat = chatRepository.findById(request.getChatId())
            .orElseThrow(() -> new RuntimeException("Чат не найден"));

        // Проверяем, что пользователь является участником чата
        boolean isParticipant = chat.getParticipants().stream()
            .anyMatch(p -> p.getId().equals(userId));

        if (!isParticipant) {
            throw new IllegalArgumentException("Вы не являетесь участником этого чата");
        }

        Message message = new Message();
        message.setContent(request.getContent());
        message.setMessageType(request.getMessageType());
        message.setSender(sender);
        message.setChat(chat);

        // Если это ответ на другое сообщение
        if (request.getReplyToMessageId() != null) {
            Message replyToMessage = messageRepository.findById(request.getReplyToMessageId())
                .orElse(null);
            message.setReplyToMessage(replyToMessage);
        }

        Message savedMessage = messageRepository.save(message);

        // Обновляем время последнего сообщения в чате
        chat.setLastMessageAt(LocalDateTime.now());
        chatRepository.save(chat);

        // Отправляем уведомление через Kafka
        notifyAboutNewMessage(savedMessage);

        return convertToDto(savedMessage);
    }

    /**
     * Получить сообщения чата
     */
    @Transactional(readOnly = true)
    public List<MessageDto> getChatMessages(Long chatId, Long userId, int page, int size) {
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("Чат не найден"));

        // Проверяем доступ к чату
        boolean isParticipant = chat.getParticipants().stream()
            .anyMatch(p -> p.getId().equals(userId));

        if (!isParticipant) {
            throw new IllegalArgumentException("У вас нет доступа к этому чату");
        }

        Pageable pageable = PageRequest.of(page, size);
        List<Message> messages = messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, pageable);

        return messages.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * Редактировать сообщение
     */
    public MessageDto editMessage(Long messageId, Long userId, MessageDto.EditMessageRequest request) {
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new RuntimeException("Сообщение не найдено"));

        if (!message.getSender().getId().equals(userId)) {
            throw new IllegalArgumentException("Вы можете редактировать только свои сообщения");
        }

        if (message.getIsDeleted()) {
            throw new IllegalStateException("Нельзя редактировать удаленное сообщение");
        }

        message.setContent(request.getContent());
        message.setIsEdited(true);

        Message savedMessage = messageRepository.save(message);

        // Уведомляем об изменении сообщения
        notifyAboutMessageUpdate(savedMessage, "EDITED");

        return convertToDto(savedMessage);
    }

    /**
     * Удалить сообщение
     */
    public void deleteMessage(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new RuntimeException("Сообщение не найдено"));

        if (!message.getSender().getId().equals(userId)) {
            throw new IllegalArgumentException("Вы можете удалять только свои сообщения");
        }

        message.setIsDeleted(true);
        message.setContent("[Сообщение удалено]");

        messageRepository.save(message);

        // Уведомляем об удалении сообщения
        notifyAboutMessageUpdate(message, "DELETED");
    }

    /**
     * Поиск сообщений в чате
     */
    @Transactional(readOnly = true)
    public List<MessageDto> searchMessagesInChat(Long chatId, Long userId, String query, int page, int size) {
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("Чат не найден"));

        boolean isParticipant = chat.getParticipants().stream()
            .anyMatch(p -> p.getId().equals(userId));

        if (!isParticipant) {
            throw new IllegalArgumentException("У вас нет доступа к этому чату");
        }

        Pageable pageable = PageRequest.of(page, size);
        List<Message> messages = messageRepository.searchMessagesInChat(chatId, query, pageable);

        return messages.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * Уведомить о новом сообщении через Kafka
     */
    private void notifyAboutNewMessage(Message message) {
        Map<String, Object> notification = Map.of(
            "type", "NEW_MESSAGE",
            "messageId", message.getId(),
            "chatId", message.getChat().getId(),
            "senderId", message.getSender().getId(),
            "content", message.getContent(),
            "messageType", message.getMessageType(),
            "timestamp", message.getCreatedAt()
        );

        kafkaTemplate.send("chat-messages", notification);
    }

    /**
     * Уведомить об изменении сообщения
     */
    private void notifyAboutMessageUpdate(Message message, String eventType) {
        Map<String, Object> notification = Map.of(
            "type", "MESSAGE_UPDATE",
            "messageId", message.getId(),
            "chatId", message.getChat().getId(),
            "eventType", eventType,
            "timestamp", LocalDateTime.now()
        );

        kafkaTemplate.send("chat-messages", notification);
    }

    /**
     * Конвертировать Message в MessageDto
     */
    public MessageDto convertToDto(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setContent(message.getContent());
        dto.setMessageType(message.getMessageType());
        dto.setIsEdited(message.getIsEdited());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setUpdatedAt(message.getUpdatedAt());
        dto.setChatId(message.getChat().getId());

        if (message.getSender() != null) {
            dto.setSender(userService.convertToDto(message.getSender()));
        }

        if (message.getReplyToMessage() != null) {
            dto.setReplyToMessage(convertToDto(message.getReplyToMessage()));
        }

        return dto;
    }
}
