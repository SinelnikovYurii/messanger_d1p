package com.messenger.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.core.dto.ChatDto;
import com.messenger.core.service.ChatService;
import com.messenger.core.service.OptimizedDataService;
import com.messenger.core.config.JwtAuthenticationFilter;
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

class ChatControllerTest {
    @Mock
    private ChatService chatService;
    @Mock
    private OptimizedDataService optimizedDataService;
    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @InjectMocks
    private ChatController chatController;
    private MockMvc mockMvc;

    public ChatControllerTest() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(chatController).build();
    }

    @Test
    void testGetChatById() throws Exception {
        ChatDto chatDto = new ChatDto();
        chatDto.setId(1L);
        chatDto.setChatName("Test Chat");
        when(chatService.getChatInfo(eq(1L), anyLong())).thenReturn(chatDto);
        mockMvc.perform(get("/api/chats/1").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatName").value("Test Chat"));
    }

    @Test
    void testCreateChat() throws Exception {
        ChatDto chatDto = new ChatDto();
        chatDto.setChatName("New Chat");
        when(chatService.createGroupChat(anyLong(), any())).thenReturn(chatDto);
        ChatDto.CreateChatRequest request = new ChatDto.CreateChatRequest();
        request.setChatName("New Chat");
        mockMvc.perform(post("/api/chats/group")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatName").value("New Chat"));
    }

    @Test
    void testGetChatById_notFound() throws Exception {
        when(chatService.getChatInfo(eq(999L), anyLong())).thenThrow(new RuntimeException("Chat not found"));
        mockMvc.perform(get("/api/chats/999").header("X-User-Id", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateChat_invalidData() throws Exception {
        ChatDto.CreateChatRequest request = new ChatDto.CreateChatRequest();
        // Не указано имя чата
        mockMvc.perform(post("/api/chats/group")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreatePrivateChat() throws Exception {
        ChatDto chatDto = new ChatDto();
        chatDto.setChatName("Private Chat");
        when(chatService.createPrivateChat(anyLong(), anyLong())).thenReturn(chatDto);
        ChatDto.CreatePrivateChatRequest request = new ChatDto.CreatePrivateChatRequest();
        request.setParticipantId(2L);
        mockMvc.perform(post("/api/chats/private")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatName").value("Private Chat"));
    }

    @Test
    void testAddParticipant() throws Exception {
        doNothing().when(chatService).addParticipant(anyLong(), anyLong());
        // Передаем userIds как JSON-массив в теле запроса
        mockMvc.perform(post("/api/chats/1/participants")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[2]"))
                .andExpect(status().isOk());
        // Если addParticipant возвращает ChatDto, используйте:
        // ChatDto chatDto = new ChatDto();
        // chatDto.setChatName("Test Chat");
        // when(chatService.addParticipant(anyLong(), anyLong())).thenReturn(chatDto);
        // mockMvc.perform(post("/api/chats/1/participants")
        //         .header("X-User-Id", "1")
        //         .contentType(MediaType.APPLICATION_JSON)
        //         .content("[2]"))
        //         .andExpect(status().isOk())
        //         .andExpect(jsonPath("$.chatName").value("Test Chat"));
    }

    @Test
    void testRemoveParticipant() throws Exception {
        ChatDto chatDto = new ChatDto();
        chatDto.setChatName("Test Chat");
        when(chatService.removeParticipant(anyLong(), anyLong(), anyLong())).thenReturn(chatDto);
        mockMvc.perform(delete("/api/chats/1/participants/2")
                .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatName").value("Test Chat"));
    }

    @Test
    void testLeaveChat() throws Exception {
        doNothing().when(chatService).leaveChat(anyLong(), anyLong());
        mockMvc.perform(delete("/api/chats/1/leave")
                .header("X-User-Id", "2"))
                .andExpect(status().isOk());
    }

    @Test
    void testSearchChats() throws Exception {
        ChatDto chatDto = new ChatDto();
        chatDto.setChatName("Test Chat");
        when(chatService.searchChats(eq("Test"), anyLong())).thenReturn(java.util.List.of(chatDto));
        mockMvc.perform(get("/api/chats/search")
                .header("X-User-Id", "1")
                .param("query", "Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chatName").value("Test Chat"));
    }

    @Test
    void testServiceException() throws Exception {
        when(chatService.getChatInfo(eq(1L), anyLong())).thenThrow(new RuntimeException("Internal error"));
        mockMvc.perform(get("/api/chats/1").header("X-User-Id", "1"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testWithValidUserIdHeader() throws Exception {
        // Проверяем, что контроллер корректно работает с заголовком X-User-Id
        ChatDto chatDto = new ChatDto();
        chatDto.setId(1L);
        chatDto.setChatName("Test Chat");
        when(chatService.getChatInfo(eq(1L), anyLong())).thenReturn(chatDto);
        mockMvc.perform(get("/api/chats/1").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatName").value("Test Chat"));
    }
}
