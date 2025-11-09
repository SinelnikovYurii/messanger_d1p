package com.messenger.core.service;

import com.messenger.core.dto.MessageDto;
import com.messenger.core.dto.MessageReadStatusDto;
import com.messenger.core.model.Chat;
import com.messenger.core.model.Message;
import com.messenger.core.model.MessageReadStatus;
import com.messenger.core.model.User;
import com.messenger.core.repository.ChatRepository;
import com.messenger.core.repository.MessageRepository;
import com.messenger.core.repository.MessageReadStatusRepository;
import com.messenger.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
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
    private final MessageReadStatusRepository messageReadStatusRepository;

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

        // Устанавливаем поля файла если есть
        if (request.getFileUrl() != null) {
            message.setFileUrl(request.getFileUrl());
            message.setFileName(request.getFileName());
            message.setFileSize(request.getFileSize());
            message.setMimeType(request.getMimeType());
            message.setThumbnailUrl(request.getThumbnailUrl());
        }

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
        // Сначала проверяем, является ли пользователь участником чата
        if (!chatRepository.isUserParticipant(chatId, userId)) {
            throw new IllegalArgumentException("У вас нет доступа к этому чату");
        }

        // Проверяем существование чата
        if (!chatRepository.existsById(chatId)) {
            throw new RuntimeException("Чат не найден");
        }

        Pageable pageable = PageRequest.of(page, size);
        // Используем оптимизированный запрос с предварительной загрузкой отправителей
        List<Message> messages = messageRepository.findByChatIdOrderByCreatedAtDescWithSender(chatId, pageable);

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
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "CHAT_MESSAGE"); // ИСПРАВЛЕНО: изменили с NEW_MESSAGE на CHAT_MESSAGE
        notification.put("id", message.getId()); // ИСПРАВЛЕНО: добавили поле id
        notification.put("messageId", message.getId());
        notification.put("chatId", message.getChat().getId());
        notification.put("senderId", message.getSender().getId());
        notification.put("senderUsername", message.getSender().getUsername());
        notification.put("content", message.getContent());
        notification.put("messageType", message.getMessageType().toString());
        notification.put("timestamp", message.getCreatedAt());

        // Добавляем метаданные файлов если есть
        if (message.getFileUrl() != null) {
            notification.put("fileUrl", message.getFileUrl());
            notification.put("fileName", message.getFileName());
            notification.put("fileSize", message.getFileSize());
            notification.put("mimeType", message.getMimeType());
            notification.put("thumbnailUrl", message.getThumbnailUrl());
        }

        kafkaTemplate.send("chat-messages", message.getChat().getId().toString(), notification);
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

        // ИСПРАВЛЕНО: Отправляем с chatId в качестве ключа для правильной маршрутизации
        kafkaTemplate.send("chat-messages", message.getChat().getId().toString(), notification);
    }

    /**
     * Уведомить о прочтении сообщения через Kafka
     */
    private void notifyAboutMessageRead(Message message, User reader) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "MESSAGE_READ");
        notification.put("messageId", message.getId());
        notification.put("chatId", message.getChat().getId());
        notification.put("readerId", reader.getId());
        notification.put("readerUsername", reader.getUsername());
        notification.put("senderId", message.getSender().getId());
        notification.put("timestamp", LocalDateTime.now());

        kafkaTemplate.send("chat-messages", message.getChat().getId().toString(), notification);
    }

    /**
     * Получить количество непрочитанных сообщений в чате
     */
    @Transactional(readOnly = true)
    public long getUnreadMessagesCount(Long userId, Long chatId) {
        return messageReadStatusRepository.countUnreadMessagesInChat(chatId, userId);
    }

    /**
     * Получить статусы прочтения для сообщения
     */
    @Transactional(readOnly = true)
    public List<MessageReadStatusDto> getMessageReadStatuses(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new RuntimeException("Сообщение не найдено"));

        // Проверяем доступ
        Chat chat = message.getChat();
        boolean isParticipant = chat.getParticipants().stream()
            .anyMatch(p -> p.getId().equals(userId));

        if (!isParticipant) {
            throw new IllegalArgumentException("У вас нет доступа к этому сообщению");
        }

        List<MessageReadStatus> readStatuses = messageReadStatusRepository.findByMessageId(messageId);

        return readStatuses.stream()
            .map(this::convertReadStatusToDto)
            .collect(Collectors.toList());
    }

    /**
     * Отметить сообщения как прочитанные
     */
    public void markMessagesAsRead(Long userId, List<Long> messageIds) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        List<Message> messages = messageRepository.findAllById(messageIds);

        for (Message message : messages) {
            // Не отмечаем свои собственные сообщения как прочитанные
            if (message.getSender().getId().equals(userId)) {
                continue;
            }

            // Проверяем, не прочитано ли уже
            if (!messageReadStatusRepository.existsByMessageIdAndUserId(message.getId(), userId)) {
                MessageReadStatus readStatus = new MessageReadStatus();
                readStatus.setMessage(message);
                readStatus.setUser(user);
                messageReadStatusRepository.save(readStatus);

                // Отправляем уведомление о прочтении
                notifyAboutMessageRead(message, user);
            }
        }
    }

    /**
     * Отметить все сообщения в чате как прочитанные
     */
    public void markAllChatMessagesAsRead(Long userId, Long chatId) {
        // Проверяем доступ к чату
        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("Чат не найден"));

        boolean isParticipant = chat.getParticipants().stream()
            .anyMatch(p -> p.getId().equals(userId));

        if (!isParticipant) {
            throw new IllegalArgumentException("У вас нет доступа к этому чату");
        }

        List<Long> unreadMessageIds = messageReadStatusRepository.findUnreadMessageIdsInChat(chatId, userId);

        if (!unreadMessageIds.isEmpty()) {
            markMessagesAsRead(userId, unreadMessageIds);
        }
    }

    /**
     * Конвертировать Message в MessageDto
     */
    public MessageDto convertToDto(Message message) {
        return convertToDto(message, null, false);
    }

    /**
     * Конвертировать Message в MessageDto с информацией о прочтении
     */
    public MessageDto convertToDto(Message message, Long currentUserId, boolean includeReadStatuses) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setContent(message.getContent());
        dto.setMessageType(message.getMessageType());
        dto.setIsEdited(message.getIsEdited());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setUpdatedAt(message.getUpdatedAt());
        dto.setChatId(message.getChat().getId());

        // Добавляем поля файлов
        dto.setFileUrl(message.getFileUrl());
        dto.setFileName(message.getFileName());
        dto.setFileSize(message.getFileSize());
        dto.setMimeType(message.getMimeType());
        dto.setThumbnailUrl(message.getThumbnailUrl());

        if (message.getSender() != null) {
            dto.setSender(userService.convertToDto(message.getSender()));
        }

        if (message.getReplyToMessage() != null) {
            dto.setReplyToMessage(convertToDto(message.getReplyToMessage()));
        }

        // Добавляем информацию о прочтении
        if (currentUserId != null) {
            // Проверяем, прочитано ли сообщение текущим пользователем
            boolean isRead = messageReadStatusRepository.existsByMessageIdAndUserId(message.getId(), currentUserId);
            dto.setIsReadByCurrentUser(isRead);

            if (isRead) {
                messageReadStatusRepository.findByMessageIdAndUserId(message.getId(), currentUserId)
                    .ifPresent(status -> dto.setReadAt(status.getReadAt()));
            }
        }

        // Количество прочитавших
        long readCount = messageReadStatusRepository.countByMessageId(message.getId());
        dto.setReadCount((int) readCount);

        // Подробный список прочитавших (если запрошено)
        if (includeReadStatuses) {
            List<MessageReadStatus> readStatuses = messageReadStatusRepository.findByMessageId(message.getId());
            List<MessageReadStatusDto> readByDtos = readStatuses.stream()
                .map(this::convertReadStatusToDto)
                .collect(Collectors.toList());
            dto.setReadBy(readByDtos);
        }

        return dto;
    }

    /**
     * Конвертировать MessageReadStatus в DTO
     */
    private MessageReadStatusDto convertReadStatusToDto(MessageReadStatus readStatus) {
        MessageReadStatusDto dto = new MessageReadStatusDto();
        dto.setId(readStatus.getId());
        dto.setMessageId(readStatus.getMessage().getId());
        dto.setUser(userService.convertToDto(readStatus.getUser()));
        dto.setReadAt(readStatus.getReadAt());
        return dto;
    }
}
