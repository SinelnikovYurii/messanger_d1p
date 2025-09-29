package com.messenger.core.service;

import com.messenger.core.dto.UserDto;
import com.messenger.core.model.Friendship;
import com.messenger.core.model.User;
import com.messenger.core.repository.FriendshipRepository;
import com.messenger.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * Отправить запрос дружбы
     */
    public void sendFriendRequest(Long requesterId, Long receiverId) {
        if (requesterId.equals(receiverId)) {
            throw new IllegalArgumentException("Нельзя отправить запрос дружбы самому себе");
        }

        User requester = userRepository.findById(requesterId)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        User receiver = userRepository.findById(receiverId)
            .orElseThrow(() -> new RuntimeException("Получатель не найден"));

        // Проверяем, есть ли уже связь между пользователями
        Optional<Friendship> existingFriendship = friendshipRepository
            .findFriendshipBetweenUsers(requesterId, receiverId);

        if (existingFriendship.isPresent()) {
            Friendship friendship = existingFriendship.get();
            if (friendship.getStatus() == Friendship.FriendshipStatus.PENDING) {
                throw new IllegalStateException("Запрос дружбы уже отправлен");
            } else if (friendship.getStatus() == Friendship.FriendshipStatus.ACCEPTED) {
                throw new IllegalStateException("Пользователи уже друзья");
            } else if (friendship.getStatus() == Friendship.FriendshipStatus.BLOCKED) {
                throw new IllegalStateException("Один из пользователей заблокирован");
            }
        }

        // Создаем новый запрос дружбы
        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setReceiver(receiver);
        friendship.setStatus(Friendship.FriendshipStatus.PENDING);

        friendshipRepository.save(friendship);
    }

    /**
     * Принять запрос дружбы
     * @param requesterId ID пользователя, отправившего запрос (с фронтенда это приходит как requestId)
     * @param userId ID текущего пользователя, который принимает запрос
     */
    @Transactional
    public void acceptFriendRequest(Long requesterId, Long userId) {
        // Ищем запрос дружбы по ID отправителя (requester) и получателя (receiver) со статусом PENDING
        Friendship friendship = friendshipRepository
                .findByRequesterId_IdAndReceiverId_IdAndStatus(requesterId, userId, Friendship.FriendshipStatus.PENDING)
                .orElseThrow(() -> new RuntimeException("Запрос дружбы не найден. Отправитель ID: " +
                        requesterId + ", Получатель ID: " + userId));

        // Дополнительная проверка на всякий случай
        if (!friendship.getReceiver().getId().equals(userId)) {
            throw new IllegalArgumentException("У вас нет прав на принятие этого запроса");
        }

        if (friendship.getStatus() != Friendship.FriendshipStatus.PENDING) {
            throw new IllegalStateException("Запрос уже обработан");
        }

        friendship.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }

    /**
     * Отклонить запрос дружбы
     */
    public void rejectFriendRequest(Long requestId, Long userId) {
        Friendship friendship = friendshipRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Запрос дружбы не найден"));

        if (!friendship.getReceiver().getId().equals(userId)) {
            throw new IllegalArgumentException("У вас нет прав на отклонение этого запроса");
        }

        if (friendship.getStatus() != Friendship.FriendshipStatus.PENDING) {
            throw new IllegalStateException("Запрос уже обработан");
        }

        friendship.setStatus(Friendship.FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
    }

    /**
     * Получить всех друзей пользователя
     */
    @Transactional(readOnly = true)
    public List<UserDto> getFriends(Long userId) {
        List<User> friends = userRepository.findFriendsByUserId(userId);
        return friends.stream()
            .map(userService::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * Получить входящие запросы дружбы
     */
    @Transactional(readOnly = true)
    public List<UserDto> getIncomingFriendRequests(Long userId) {
        List<Friendship> friendships = friendshipRepository
            .findIncomingFriendRequests(userId, Friendship.FriendshipStatus.PENDING);

        return friendships.stream()
            .map(f -> userService.convertToDto(f.getRequester()))
            .collect(Collectors.toList());
    }

    /**
     * Получить исходящие запросы дружбы
     */
    @Transactional(readOnly = true)
    public List<UserDto> getOutgoingFriendRequests(Long userId) {
        List<Friendship> friendships = friendshipRepository
            .findOutgoingFriendRequests(userId, Friendship.FriendshipStatus.PENDING);

        return friendships.stream()
            .map(f -> userService.convertToDto(f.getReceiver()))
            .collect(Collectors.toList());
    }

    /**
     * Проверить статус дружбы между пользователями
     */
    @Transactional(readOnly = true)
    public Optional<Friendship.FriendshipStatus> getFriendshipStatus(Long user1Id, Long user2Id) {
        return friendshipRepository.getFriendshipStatus(user1Id, user2Id);
    }

    /**
     * Удалить из друзей
     */
    public void removeFriend(Long userId, Long friendId) {
        Friendship friendship = friendshipRepository
            .findFriendshipBetweenUsers(userId, friendId)
            .orElseThrow(() -> new RuntimeException("Дружеская связь не найдена"));

        if (friendship.getStatus() != Friendship.FriendshipStatus.ACCEPTED) {
            throw new IllegalStateException("Пользователи не являются друзьями");
        }

        friendshipRepository.delete(friendship);
    }
}
