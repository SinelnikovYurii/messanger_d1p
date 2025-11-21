package com.messenger.core.service;

import com.messenger.core.model.User;
import com.messenger.core.model.Friendship;
import com.messenger.core.repository.FriendshipRepository;
import com.messenger.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FriendshipServiceTest {
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks
    private FriendshipService friendshipService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        doReturn(mock(CompletableFuture.class)).when(kafkaTemplate).send(anyString(), any());
    }

    @Test
    void testSendFriendRequest_success() {
        User requester = new User(); requester.setId(1L);
        User receiver = new User(); receiver.setId(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(friendshipRepository.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.empty());
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(inv -> inv.getArgument(0));
        friendshipService.sendFriendRequest(1L, 2L);
        verify(friendshipRepository, times(1)).save(any(Friendship.class));
    }

    @Test
    void testSendFriendRequest_self() {
        assertThrows(IllegalArgumentException.class, () -> friendshipService.sendFriendRequest(1L, 1L));
    }

    @Test
    void testSendFriendRequest_alreadyPending() {
        User requester = new User(); requester.setId(1L);
        User receiver = new User(); receiver.setId(2L);
        Friendship friendship = new Friendship();
        friendship.setStatus(Friendship.FriendshipStatus.PENDING);
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(friendshipRepository.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));
        assertThrows(IllegalStateException.class, () -> friendshipService.sendFriendRequest(1L, 2L));
    }

    @Test
    void testSendFriendRequest_alreadyFriends() {
        User requester = new User(); requester.setId(1L);
        User receiver = new User(); receiver.setId(2L);
        Friendship friendship = new Friendship();
        friendship.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(friendshipRepository.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));
        assertThrows(IllegalStateException.class, () -> friendshipService.sendFriendRequest(1L, 2L));
    }

    @Test
    void testSendFriendRequest_blocked() {
        User requester = new User(); requester.setId(1L);
        User receiver = new User(); receiver.setId(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(friendshipRepository.isBlocked(1L, 2L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> friendshipService.sendFriendRequest(1L, 2L));
    }

    @Test
    void testSendFriendRequest_userNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> friendshipService.sendFriendRequest(1L, 2L));
    }

    @Test
    void testAcceptFriendRequest_success() {
        User requester = new User(); requester.setId(1L);
        User receiver = new User(); receiver.setId(2L);
        Friendship friendship = new Friendship();
        friendship.setId(10L);
        friendship.setStatus(Friendship.FriendshipStatus.PENDING);
        friendship.setRequester(requester);
        friendship.setReceiver(receiver);
        when(friendshipRepository.findByRequesterId_IdAndReceiverId_IdAndStatus(1L, 2L, Friendship.FriendshipStatus.PENDING))
            .thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(inv -> inv.getArgument(0));
        friendshipService.acceptFriendRequest(1L, 2L);
        assertEquals(Friendship.FriendshipStatus.ACCEPTED, friendship.getStatus());
    }

    @Test
    void testAcceptFriendRequest_notFound() {
        when(friendshipRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> friendshipService.acceptFriendRequest(999L, 2L));
    }

    @Test
    void testRejectFriendRequest_success() {
        User requester = new User(); requester.setId(1L);
        User receiver = new User(); receiver.setId(2L);
        Friendship friendship = new Friendship();
        friendship.setId(10L);
        friendship.setStatus(Friendship.FriendshipStatus.PENDING);
        friendship.setRequester(requester);
        friendship.setReceiver(receiver);
        when(friendshipRepository.findByRequesterId_IdAndReceiverId_IdAndStatus(1L, 2L, Friendship.FriendshipStatus.PENDING))
            .thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(any(Friendship.class))).thenAnswer(inv -> inv.getArgument(0));
        friendshipService.rejectFriendRequest(1L, 2L);
        assertEquals(Friendship.FriendshipStatus.REJECTED, friendship.getStatus());
    }

    @Test
    void testDeleteFriend_success() {
        User requester = new User(); requester.setId(2L);
        User receiver = new User(); receiver.setId(3L);
        Friendship friendship = new Friendship();
        friendship.setId(1L);
        friendship.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        friendship.setRequester(requester);
        friendship.setReceiver(receiver);
        when(friendshipRepository.findById(1L)).thenReturn(Optional.of(friendship));
        doNothing().when(friendshipRepository).delete(friendship);
        friendshipService.deleteFriend(1L, 2L);
        verify(friendshipRepository, times(1)).delete(friendship);
    }

    @Test
    void testGetFriends_success() {
        User user = new User(); user.setId(1L);
        User friend = new User(); friend.setId(2L);
        when(userRepository.findFriendsByUserId(1L)).thenReturn(List.of(friend));
        when(userService.convertToDto(friend)).thenReturn(mock(com.messenger.core.dto.UserDto.class));
        List<com.messenger.core.dto.UserDto> friends = friendshipService.getFriends(1L);
        assertEquals(1, friends.size());
    }

    @Test
    void testGetIncomingFriendRequests_success() {
        Friendship f1 = new Friendship(); f1.setRequester(new User());
        Friendship f2 = new Friendship(); f2.setRequester(new User());
        when(friendshipRepository.findIncomingFriendRequests(1L, Friendship.FriendshipStatus.PENDING)).thenReturn(java.util.List.of(f1, f2));
        when(userService.convertToDto(any(User.class))).thenReturn(mock(com.messenger.core.dto.UserDto.class));
        var result = friendshipService.getIncomingFriendRequests(1L);
        assertEquals(2, result.size());
    }

    @Test
    void testGetOutgoingFriendRequests_success() {
        Friendship f1 = new Friendship(); f1.setReceiver(new User());
        Friendship f2 = new Friendship(); f2.setReceiver(new User());
        when(friendshipRepository.findOutgoingFriendRequests(1L, Friendship.FriendshipStatus.PENDING)).thenReturn(java.util.List.of(f1, f2));
        when(userService.convertToDto(any(User.class))).thenReturn(mock(com.messenger.core.dto.UserDto.class));
        var result = friendshipService.getOutgoingFriendRequests(1L);
        assertEquals(2, result.size());
    }

    @Test
    void testGetFriendshipStatus_success() {
        when(friendshipRepository.getFriendshipStatus(1L, 2L)).thenReturn(Optional.of(Friendship.FriendshipStatus.ACCEPTED));
        var status = friendshipService.getFriendshipStatus(1L, 2L);
        assertTrue(status.isPresent());
        assertEquals(Friendship.FriendshipStatus.ACCEPTED, status.get());
    }

    @Test
    void testRemoveFriend_success() {
        Friendship friendship = new Friendship();
        friendship.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));
        doNothing().when(friendshipRepository).delete(friendship);
        friendshipService.removeFriend(1L, 2L);
        verify(friendshipRepository, times(1)).delete(friendship);
    }

    @Test
    void testRemoveFriend_notFound() {
        when(friendshipRepository.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> friendshipService.removeFriend(1L, 2L));
    }

    @Test
    void testRemoveFriend_notFriends() {
        Friendship friendship = new Friendship();
        friendship.setStatus(Friendship.FriendshipStatus.PENDING);
        when(friendshipRepository.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));
        assertThrows(IllegalStateException.class, () -> friendshipService.removeFriend(1L, 2L));
    }
}
