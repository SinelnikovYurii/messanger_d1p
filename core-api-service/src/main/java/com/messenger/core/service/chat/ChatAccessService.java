package com.messenger.core.service.chat;

import com.messenger.core.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Сервис проверки прав доступа к чатам.
 * Инкапсулирует бизнес-правила доступа, чтобы контроллеры
 * не зависели от репозиториев напрямую.
 */
@Service
@RequiredArgsConstructor
public class ChatAccessService {

    private final ChatRepository chatRepository;

    /**
     * Проверяет, является ли пользователь участником чата.
     * Бросает исключение если доступ запрещён.
     *
     * @param chatId ID чата
     * @param userId ID пользователя
     * @throws IllegalArgumentException если пользователь не является участником чата
     */
    public void verifyParticipant(Long chatId, Long userId) {
        if (!chatRepository.isUserParticipant(chatId, userId)) {
            throw new IllegalArgumentException(
                    "Доступ запрещён: пользователь " + userId + " не является участником чата " + chatId);
        }
    }
}
