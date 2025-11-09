package com.messenger.core.service;

import com.messenger.core.dto.UserDto;
import com.messenger.core.model.Friendship;
import com.messenger.core.model.User;
import com.messenger.core.repository.FriendshipRepository;
import com.messenger.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

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

        Friendship savedFriendship = friendshipRepository.save(friendship);

        // Отправляем уведомление через Kafka получателю запроса
        sendFriendRequestNotification(savedFriendship);
    }

    /**
     * Принять запрос дружбы
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
        Friendship savedFriendship = friendshipRepository.save(friendship);

        // Отправляем уведомление отправителю запроса о том, что запрос принят
        sendFriendRequestAcceptedNotification(savedFriendship);
    }

    /**
     * Отклонить запрос дружбы
     * @param requesterId ID пользователя, отправившего запрос (с фронтенда это приходит как requestId)
     * @param userId ID текущего пользователя, который отклоняет запрос
     */
    public void rejectFriendRequest(Long requesterId, Long userId) {
        // Ищем запрос дружбы по ID отправителя (requester) и получателя (receiver) со статусом PENDING
        Friendship friendship = friendshipRepository
                .findByRequesterId_IdAndReceiverId_IdAndStatus(requesterId, userId, Friendship.FriendshipStatus.PENDING)
                .orElseThrow(() -> new RuntimeException("Запрос дружбы не найден. Отправитель ID: " +
                        requesterId + ", Получатель ID: " + userId));

        // Дополнительная проверка на всякий случай
        if (!friendship.getReceiver().getId().equals(userId)) {
            throw new IllegalArgumentException("У вас нет прав на отклонение этого запроса");
        }

        if (friendship.getStatus() != Friendship.FriendshipStatus.PENDING) {
            throw new IllegalStateException("Запрос уже обработан");
        }

        friendship.setStatus(Friendship.FriendshipStatus.REJECTED);
        Friendship savedFriendship = friendshipRepository.save(friendship);

        // Отправляем уведомление отправителю запроса о том, что запрос отклонен
        sendFriendRequestRejectedNotification(savedFriendship);
    }

    /**
     * Отправить уведомление о новом запросе в друзья через Kafka
     */
    private void sendFriendRequestNotification(Friendship friendship) {
        try {
            // Уведомление получателю о новом запросе
            Map<String, Object> notificationToReceiver = new HashMap<>();
            notificationToReceiver.put("type", "FRIEND_REQUEST_RECEIVED");
            notificationToReceiver.put("recipientId", friendship.getReceiver().getId());
            notificationToReceiver.put("senderId", friendship.getRequester().getId());
            notificationToReceiver.put("senderUsername", friendship.getRequester().getUsername());
            notificationToReceiver.put("senderFirstName", friendship.getRequester().getFirstName());
            notificationToReceiver.put("senderLastName", friendship.getRequester().getLastName());
            notificationToReceiver.put("senderProfilePictureUrl", friendship.getRequester().getProfilePictureUrl());
            notificationToReceiver.put("friendshipId", friendship.getId());
            notificationToReceiver.put("timestamp", System.currentTimeMillis());

            kafkaTemplate.send("websocket-notifications", notificationToReceiver);
            log.info("Отправлено уведомление о запросе в друзья получателю: от {} к {}",
                friendship.getRequester().getId(), friendship.getReceiver().getId());

            // Уведомление отправителю о том, что запрос успешно отправлен
            Map<String, Object> notificationToSender = new HashMap<>();
            notificationToSender.put("type", "FRIEND_REQUEST_SENT");
            notificationToSender.put("recipientId", friendship.getRequester().getId());
            notificationToSender.put("receiverId", friendship.getReceiver().getId());
            notificationToSender.put("receiverUsername", friendship.getReceiver().getUsername());
            notificationToSender.put("receiverFirstName", friendship.getReceiver().getFirstName());
            notificationToSender.put("receiverLastName", friendship.getReceiver().getLastName());
            notificationToSender.put("receiverProfilePictureUrl", friendship.getReceiver().getProfilePictureUrl());
            notificationToSender.put("friendshipId", friendship.getId());
            notificationToSender.put("timestamp", System.currentTimeMillis());

            kafkaTemplate.send("websocket-notifications", notificationToSender);
            log.info("Отправлено уведомление о запросе в друзья отправителю: от {} к {}",
                friendship.getRequester().getId(), friendship.getReceiver().getId());
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления о запросе в друзья", e);
        }
    }

    /**
     * Отправить уведомление о принятии запроса в друзья
     */
    private void sendFriendRequestAcceptedNotification(Friendship friendship) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "FRIEND_REQUEST_ACCEPTED");
            notification.put("recipientId", friendship.getRequester().getId());
            notification.put("acceptedByUserId", friendship.getReceiver().getId());
            notification.put("acceptedByUsername", friendship.getReceiver().getUsername());
            notification.put("acceptedByFirstName", friendship.getReceiver().getFirstName());
            notification.put("acceptedByLastName", friendship.getReceiver().getLastName());
            notification.put("acceptedByProfilePictureUrl", friendship.getReceiver().getProfilePictureUrl());
            notification.put("friendshipId", friendship.getId());
            notification.put("timestamp", System.currentTimeMillis());

            kafkaTemplate.send("websocket-notifications", notification);
            log.info("Отправлено уведомление о принятии запроса в друзья: {} принял запрос от {}",
                friendship.getReceiver().getId(), friendship.getRequester().getId());
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления о принятии запроса в друзья", e);
        }
    }

    /**
     * Отправить уведомление об отклонении запроса в друзья
     */
    private void sendFriendRequestRejectedNotification(Friendship friendship) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "FRIEND_REQUEST_REJECTED");
            notification.put("recipientId", friendship.getRequester().getId());
            notification.put("rejectedByUserId", friendship.getReceiver().getId());
            notification.put("rejectedByUsername", friendship.getReceiver().getUsername());
            notification.put("friendshipId", friendship.getId());
            notification.put("timestamp", System.currentTimeMillis());

            kafkaTemplate.send("websocket-notifications", notification);
            log.info("Отправлено уведомление об отклонении запроса в друзья: {} отклонил запрос от {}",
                friendship.getReceiver().getId(), friendship.getRequester().getId());
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления об отклонении запроса в друзья", e);
        }
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
