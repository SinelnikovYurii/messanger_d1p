import api from './api';

class ChatService {
    constructor() {
        this.socket = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectInterval = 3000;
        this.messageHandlers = new Set();
        this.connectionHandlers = [];
        this.chatEventHandlers = []; // Добавляем обработчики событий чата
        this.isAuthenticated = false;
        this.currentToken = null;
    }

    connect(token) {
        return new Promise((resolve, reject) => {
            // ИСПРАВЛЕНИЕ: Закрываем существующее соединение перед созданием нового
            if (this.socket && this.socket.readyState !== WebSocket.CLOSED) {
                console.log('[ChatService] Closing existing WebSocket before reconnect');
                this.socket.close();
                this.socket = null;
            }

            this.currentToken = token;
            this.isAuthenticated = false;

            // Небольшая задержка перед новым соединением для корректного закрытия старого
            setTimeout(() => {
                // Подключаемся через Gateway на порту 8083
                const wsUrl = `ws://localhost:8083/ws/chat?token=${token}`;
                console.log('Connecting to WebSocket:', wsUrl);

                this.socket = new WebSocket(wsUrl);

                this.socket.onopen = () => {
                    console.log('WebSocket connected successfully');
                    this.isAuthenticated = true;
                    this.reconnectAttempts = 0;
                    this.connectionHandlers.forEach(handler => {
                        if (handler.onConnect) handler.onConnect();
                    });
                    resolve(); // Сразу резолвим при успешном подключении
                };

                this.socket.onmessage = (event) => {
                    try {
                        const message = JSON.parse(event.data);
                        console.log('Received WebSocket message:', message);

                        // Обрабатываем различные типы сообщений
                        if (message.type === 'AUTH_SUCCESS') {
                            console.log('Authentication confirmed:', message);
                            return; // Не передаем дальше
                        } else if (message.type === 'SYSTEM_MESSAGE') {
                            console.log('System message:', message.content);
                            return; // Не передаем дальше
                        } else if (message.type === 'MESSAGE_SENT') {
                            console.log('Message sent confirmation:', message.content);
                            return; // Не передаем дальше - это только подтверждение
                        } else if (message.type === 'PONG') {
                            console.log('Received pong');
                            return; // Не передаем дальше
                        } else if (message.type === 'ERROR') {
                            console.error('WebSocket error:', message.content);
                            this.connectionHandlers.forEach(handler => {
                                if (handler.onError) handler.onError(new Error(message.content));
                            });
                            return;
                        } else if (message.type === 'CHAT_EVENT') {
                            // Обрабатываем события чата, отправленные через Kafka
                            console.log('Chat event received:', message);
                            this.chatEventHandlers.forEach(handler => handler(message));
                            return;
                        } else if (message.type === 'FRIEND_REQUEST_RECEIVED' ||
                                   message.type === 'FRIEND_REQUEST_SENT' ||
                                   message.type === 'FRIEND_REQUEST_ACCEPTED' ||
                                   message.type === 'FRIEND_REQUEST_REJECTED') {
                            // Обрабатываем уведомления о друзьях
                            console.log('Friend notification received:', message);
                            this.messageHandlers.forEach(handler => handler(message));
                            return;
                        }

                        // Передаем значимые сообщения обработчикам
                        if (message.type === 'CHAT_MESSAGE' ||
                            message.type === 'MESSAGE' ||
                            message.type === 'MESSAGE_READ' ||
                            message.type === 'USER_ONLINE' ||
                            message.type === 'USER_OFFLINE') {
                            this.messageHandlers.forEach(handler => handler(message));
                        }
                    } catch (error) {
                        console.error('Error parsing WebSocket message:', error);
                        // Если не JSON, то передаем как текст
                        const textMessage = {
                            type: 'TEXT',
                            content: event.data,
                            timestamp: new Date().toISOString()
                        };
                        this.messageHandlers.forEach(handler => handler(textMessage));
                    }
                };

                this.socket.onerror = (error) => {
                    console.error('WebSocket error:', error);
                    this.connectionHandlers.forEach(handler => {
                        if (handler.onError) handler.onError(error);
                    });
                    if (!this.isAuthenticated) {
                        reject(error);
                    }
                };

                this.socket.onclose = (event) => {
                    console.log('WebSocket connection closed', event);
                    this.isAuthenticated = false;
                    this.connectionHandlers.forEach(handler => {
                        if (handler.onClose) handler.onClose(event);
                    });

                    // Автоматическое переподключение только если была успешная аутентификация
                    if (this.reconnectAttempts < this.maxReconnectAttempts && this.currentToken) {
                        this.reconnectAttempts++;
                        console.log(`Attempting to reconnect... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
                        setTimeout(() => {
                            this.connect(this.currentToken).catch(() => {});
                        }, this.reconnectInterval);
                    }
                };

                // Таймаут на случай если соединение зависнет
                setTimeout(() => {
                    if (!this.isAuthenticated && this.socket && this.socket.readyState !== WebSocket.OPEN) {
                        console.error('WebSocket connection timeout');
                        reject(new Error('Connection timeout'));
                    }
                }, 5000);
            }, 100); // Задержка 100ms перед созданием нового соединения
        });
    }

    disconnect() {
        this.currentToken = null;
        this.isAuthenticated = false;
        if (this.socket) {
            this.socket.close();
            this.socket = null;
        }
    }

    sendWebSocketMessage(message) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN && this.isAuthenticated) {
            this.socket.send(JSON.stringify(message));
            return true;
        } else {
            console.error('WebSocket is not connected or not authenticated');
            console.log('Socket state:', {
                exists: !!this.socket,
                readyState: this.socket?.readyState,
                isAuthenticated: this.isAuthenticated,
                readyStateText: this.getReadyStateText()
            });
            return false;
        }
    }

    getReadyStateText() {
        if (!this.socket) return 'No socket';
        switch (this.socket.readyState) {
            case WebSocket.CONNECTING: return 'CONNECTING';
            case WebSocket.OPEN: return 'OPEN';
            case WebSocket.CLOSING: return 'CLOSING';
            case WebSocket.CLOSED: return 'CLOSED';
            default: return 'UNKNOWN';
        }
    }

    sendChatMessage(content, chatId) {
        const message = {
            type: 'CHAT_MESSAGE',
            content: content,
            chatId: chatId,
            timestamp: new Date().toISOString()
        };
        const sent = this.sendWebSocketMessage(message);
        if (!sent) {
            throw new Error('Не удалось отправить сообщение: WebSocket не подключен');
        }
    }

    ping() {
        const message = {
            type: 'PING'
        };
        this.sendWebSocketMessage(message);
    }

    onMessage(handler) {
        if (!this.messageHandlers.has(handler)) {
            this.messageHandlers.add(handler);
        }
        // Возвращаем функцию для отписки
        return () => {
            this.removeMessageHandler(handler);
        };
    }

    onConnection(handlers) {
        this.connectionHandlers.push(handlers);
    }

    // Добавляем обработчик событий чата
    onChatEvent(handler) {
        this.chatEventHandlers.push(handler);
        // Возвращаем функцию для отписки
        return () => {
            this.removeChatEventHandler(handler);
        };
    }

    removeMessageHandler(handler) {
        this.messageHandlers.delete(handler);
    }

    removeConnectionHandler(handler) {
        const index = this.connectionHandlers.indexOf(handler);
        if (index > -1) {
            this.connectionHandlers.splice(index, 1);
        }
    }

    removeChatEventHandler(handler) {
        const index = this.chatEventHandlers.indexOf(handler);
        if (index > -1) {
            this.chatEventHandlers.splice(index, 1);
        }
    }

    isConnected() {
        return this.socket && this.socket.readyState === WebSocket.OPEN;
    }

    // API методы для работы с чатами
    async getUserChats() {
        const response = await api.get('/api/chats');
        return response.data;
    }

    async createPrivateChat(participantId) {
        const response = await api.post('/api/chats/private', {
            participantId
        });
        return response.data;
    }

    async createGroupChat(chatData) {
        const response = await api.post('/api/chats/group', {
            chatName: chatData.chatName,
            chatType: 'GROUP',
            chatDescription: chatData.chatDescription,
            participantIds: chatData.participantIds
        });
        return response.data;
    }

    async addParticipants(chatId, userIds) {
        const response = await api.post(`/api/chats/${chatId}/participants`, userIds);
        return response.data;
    }

    async leaveChat(chatId) {
        const response = await api.delete(`/api/chats/${chatId}/leave`);
        return response.data;
    }

    async getChatInfo(chatId) {
        const response = await api.get(`/api/chats/${chatId}`);
        return response.data;
    }

    async removeParticipant(chatId, userId) {
        const response = await api.delete(`/api/chats/${chatId}/participants/${userId}`);
        return response.data;
    }

    async getChatMessages(chatId, page = 0, size = 50) {
        const response = await api.get(`/api/messages/chat/${chatId}?page=${page}&size=${size}`);
        return response.data;
    }

    // API методы для работы с файлами
    async uploadFile(file, chatId, caption = null) {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('chatId', chatId);
        if (caption) {
            formData.append('caption', caption);
        }

        // Получаем токен из localStorage
        const token = localStorage.getItem('token');

        const response = await api.post('/api/files/upload', formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
                // Явно добавляем токен авторизации
                'Authorization': token ? `Bearer ${token}` : ''
            },
            onUploadProgress: (progressEvent) => {
                const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
                console.log('Upload progress:', percentCompleted + '%');
            }
        });
        return response.data;
    }

    getFileUrl(fileUrl) {
        // Возвращаем полный URL для доступа к файлу с токеном в query параметрах для изображений
        if (fileUrl && fileUrl.startsWith('/api/files/')) {
            const token = localStorage.getItem('token');
            // Для GET запросов файлов добавляем токен как query параметр
            return `http://localhost:8083${fileUrl}${token ? '?token=' + token : ''}`;
        }
        return fileUrl;
    }

    // ========== API методы для работы со статусами прочтения ==========

    /**
     * Отметить конкретные сообщения как прочитанные
     * @param {Array<number>} messageIds - массив ID сообщений
     */
    async markMessagesAsRead(messageIds) {
        try {
            const response = await api.post('/api/messages/read', messageIds);
            return response.data;
        } catch (error) {
            console.error('Ошибка отметки сообщений как прочитанных:', error);
            throw error;
        }
    }

    /**
     * Отметить все сообщения в чате как прочитанные
     * @param {number} chatId - ID чата
     */
    async markAllChatMessagesAsRead(chatId) {
        try {
            const response = await api.post(`/api/messages/chat/${chatId}/read-all`);
            return response.data;
        } catch (error) {
            console.error('Ошибка отметки всех сообщений как прочитанных:', error);
            throw error;
        }
    }

    /**
     * Получить количество непрочитанных сообщений в чате
     * @param {number} chatId - ID чата
     * @returns {Promise<number>} количество непрочитанных сообщений
     */
    async getUnreadMessagesCount(chatId) {
        try {
            const response = await api.get(`/api/messages/chat/${chatId}/unread-count`);
            return response.data;
        } catch (error) {
            console.error('Ошибка получения количества непрочитанных сообщений:', error);
            throw error;
        }
    }

    /**
     * Получить статусы прочтения для конкретного сообщения
     * @param {number} messageId - ID сообщения
     * @returns {Promise<Array>} список пользователей, прочитавших сообщение
     */
    async getMessageReadStatuses(messageId) {
        try {
            const response = await api.get(`/api/messages/${messageId}/read-status`);
            return response.data;
        } catch (error) {
            console.error('Ошибка получения статусов прочтения:', error);
            throw error;
        }
    }
}

// Создаем единственный экземпляр сервиса
const chatService = new ChatService();

// Экспортируем и класс, и экземпляр
export { ChatService };
export default chatService;
