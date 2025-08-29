package com.messenger.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.gateway.model.Chat;
import com.messenger.gateway.model.DTO.MessageDto;
import com.messenger.gateway.model.Message;
import com.messenger.gateway.model.User;
import com.messenger.gateway.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserService userService;
    private final ChatService chatService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public List<MessageDto> getChatMessages(Long chatId) {
        return messageRepository.findMessagesByChatId(chatId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public MessageDto sendMessage(Long chatId, Long senderId, String content) {
        try {
            Message message = new Message();
            message.setChat(new Chat());
            message.getChat().setId(chatId);
            message.setSender(new User());
            message.getSender().setId(senderId);
            message.setContent(content);

            Message savedMessage = messageRepository.save(message);


            try {
                kafkaTemplate.send("chat-messages", chatId.toString(), savedMessage);
                log.info("Message sent to Kafka: chatId={}, messageId={}", chatId, savedMessage.getId());
            } catch (Exception e) {
                log.error("Failed to send message to Kafka, but message saved to DB", e);
            }

            return convertToDto(savedMessage);
        } catch (Exception e) {
            log.error("Error sending message", e);
            throw new RuntimeException("Failed to send message", e);
        }
    }

    private MessageDto convertToDto(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setSenderId(message.getSender().getId());
        dto.setChatId(message.getChat().getId());
        dto.setContent(message.getContent());
        dto.setTimestamp(message.getTimestamp());
        dto.setIsRead(message.getIsRead());
        return dto;
    }
}