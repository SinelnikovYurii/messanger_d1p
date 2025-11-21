package com.messenger.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.core.dto.MessageDto;
import com.messenger.core.model.Message;
import com.messenger.core.service.MessageService;
import com.messenger.core.service.OptimizedDataService;
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

class MessageControllerTest {
    @Mock
    private MessageService messageService;
    @Mock
    private OptimizedDataService optimizedDataService;
    @InjectMocks
    private MessageController messageController;
    private MockMvc mockMvc;

    public MessageControllerTest() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(messageController).build();
    }

    @Test
    void testSendMessage() throws Exception {
        MessageDto.SendMessageRequest request = new MessageDto.SendMessageRequest();
        request.setChatId(5L);
        request.setContent("Hello!");
        MessageDto response = new MessageDto();
        response.setId(10L);
        response.setContent("Hello!");
        when(messageService.sendMessage(anyLong(), any(MessageDto.SendMessageRequest.class))).thenReturn(response);
        mockMvc.perform(post("/api/messages")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hello!"));
    }

    @Test
    void testGetMessagesByChatId() throws Exception {
        MessageDto msg1 = new MessageDto(); msg1.setId(1L); msg1.setContent("msg1");
        MessageDto msg2 = new MessageDto(); msg2.setId(2L); msg2.setContent("msg2");
        when(optimizedDataService.getOptimizedChatMessages(eq(5L), anyLong(), eq(0), eq(50))).thenReturn(java.util.List.of(msg1, msg2));
        mockMvc.perform(get("/api/messages/chat/5")
                .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[1].id").value(2L));
    }
}
