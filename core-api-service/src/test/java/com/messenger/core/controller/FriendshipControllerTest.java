package com.messenger.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.core.model.Friendship;
import com.messenger.core.service.FriendshipService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;

class FriendshipControllerTest {
    @Mock
    private FriendshipService friendshipService;
    @InjectMocks
    private FriendshipController friendshipController;
    private MockMvc mockMvc;

    public FriendshipControllerTest() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(friendshipController).build();
    }

    @Test
    void testSendFriendRequest() throws Exception {
        doNothing().when(friendshipService).sendFriendRequest(anyLong(), anyLong());
        mockMvc.perform(post("/api/friends/request")
                .header("x-user-id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of("userId", 2L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testRespondToFriendRequestAccept() throws Exception {
        doNothing().when(friendshipService).acceptFriendRequest(anyLong(), anyLong());
        mockMvc.perform(post("/api/friends/respond")
                .header("x-user-id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of("requestId", 5L, "accept", true)))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Запрос дружбы принят"));
    }

    @Test
    void testRespondToFriendRequestReject() throws Exception {
        doNothing().when(friendshipService).rejectFriendRequest(anyLong(), anyLong());
        mockMvc.perform(post("/api/friends/respond")
                .header("x-user-id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of("requestId", 5L, "accept", false)))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Запрос дружбы отклонен"));
    }

    @Test
    void testGetIncomingFriendRequests() throws Exception {
        when(friendshipService.getIncomingFriendRequests(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/friends/incoming")
                .header("x-user-id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetOutgoingFriendRequests() throws Exception {
        when(friendshipService.getOutgoingFriendRequests(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/friends/outgoing")
                .header("x-user-id", "1"))
                .andExpect(status().isOk());
    }
}
