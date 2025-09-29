package com.messenger.core.controller;

import com.messenger.core.dto.UserDto;
import com.messenger.core.model.User;
import com.messenger.core.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/all")
    // Явно указываем, что доступ к этому эндпоинту имеют пользователи с ролью ROLE_USER
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<List<UserDto>> getAllUsers(HttpServletRequest request) {
        log.info("Получен запрос на получение всех пользователей");

        // Получаем ID пользователя из заголовка запроса
        String userIdHeader = request.getHeader("x-user-id");
        log.info("ID пользователя из заголовка: {}", userIdHeader);

        if (userIdHeader == null) {
            log.error("Не указан ID пользователя в заголовке запроса");
            return ResponseEntity.badRequest().build();
        }

        try {
            Long currentUserId = Long.parseLong(userIdHeader);
            return ResponseEntity.ok(userService.getAllUsers(currentUserId));
        } catch (NumberFormatException e) {
            log.error("Некорректный формат ID пользователя: {}", userIdHeader);
            return ResponseEntity.badRequest().build();
        }
    }

    // ...другие методы контроллера...
}
