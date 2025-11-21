package com.messenger.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messenger.core.dto.UserDto;
import com.messenger.core.model.User;
import com.messenger.core.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {
    @Mock
    private UserService userService;
    @InjectMocks
    private UserController userController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
    }

    @Test
    void testGetAllUsers() throws Exception {
        UserDto userDto = new UserDto();
        userDto.setId(1L);
        when(userService.getAllUsers(1L)).thenReturn(List.of(userDto));
        mockMvc.perform(get("/api/users/all").header("x-user-id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    void testGetFriends() throws Exception {
        UserDto userDto = new UserDto();
        userDto.setId(2L);
        when(userService.getFriends(1L)).thenReturn(List.of(userDto));
        mockMvc.perform(get("/api/users/friends").header("x-user-id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2L));
    }

    @Test
    void testSearchUsers() throws Exception {
        UserDto.UserSearchResult result = new UserDto.UserSearchResult();
        result.setId(3L);
        when(userService.searchUsers(eq("test"), eq(1L))).thenReturn(List.of(result));
        mockMvc.perform(get("/api/users/search").param("query", "test").header("x-user-id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3L));
    }

    @Test
    void testUpdateProfile() throws Exception {
        UserDto.UpdateProfileRequest req = new UserDto.UpdateProfileRequest();
        req.setUsername("newname");
        UserDto updated = new UserDto();
        updated.setUsername("newname");
        when(userService.updateProfile(eq(1L), any())).thenReturn(updated);
        mockMvc.perform(put("/api/users/profile")
                .header("x-user-id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newname"));
    }

    @Test
    void testGetCurrentUserProfile() throws Exception {
        UserDto userDto = new UserDto();
        userDto.setId(1L);
        when(userService.getUserInfo(1L, 1L)).thenReturn(userDto);
        mockMvc.perform(get("/api/users/profile").header("x-user-id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void testGetUserDataInternal() throws Exception {
        UserDto userDto = new UserDto();
        userDto.setId(7L);
        when(userService.getUserInfo(7L, 7L)).thenReturn(userDto);
        mockMvc.perform(get("/api/users/7/internal").header("X-Internal-Service", "websocket-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7L));
    }

    // uploadAvatar и updateOnlineStatus требуют мокирования MultipartFile и специфических заголовков,
    // их тесты можно добавить отдельно при необходимости.
}

