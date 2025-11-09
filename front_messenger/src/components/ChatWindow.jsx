import React, { useState, useEffect, useRef } from 'react';
import chatService from '../services/chatService';
import authService from '../services/authService';
import { ErrorHandler } from '../utils/errorHandler';

const ChatWindow = () => {
    const [newMessage, setNewMessage] = useState('');
    const [messages, setMessages] = useState([]);
    const [isConnected, setIsConnected] = useState(false);
    const [connectionError, setConnectionError] = useState(null);
    const messagesEndRef = useRef(null);
    const isMountedRef = useRef(true);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    useEffect(() => {
        isMountedRef.current = true;

        const connectWebSocket = async () => {
            // Проверяем готовность к подключению через AuthService
            if (!authService.isReadyForApiCalls()) {
                console.log('ChatWindow: Not ready for WebSocket connection');
                if (isMountedRef.current) {
                    setConnectionError('Ожидание авторизации...');
                }
                return;
            }

            const token = localStorage.getItem('token');
            if (!token) {
                console.log('ChatWindow: No token found for WebSocket');
                if (isMountedRef.current) {
                    setConnectionError('Токен авторизации не найден');
                }
                return;
            }

            try {
                console.log('ChatWindow: Attempting WebSocket connection');
                if (isMountedRef.current) {
                    setConnectionError(null);
                }

                await chatService.connect(token);

                if (isMountedRef.current) {
                    console.log('ChatWindow: WebSocket connected successfully');
                    setIsConnected(true);
                    setConnectionError(null);
                }
            } catch (error) {
                console.error('ChatWindow: WebSocket connection error:', error);
                if (isMountedRef.current) {
                    const errorMessage = ErrorHandler.getErrorMessage(error);
                    setConnectionError(`Ошибка подключения: ${errorMessage}`);
                    setIsConnected(false);
                }
            }
        };

        // Подписываемся на сообщения
        const handleMessage = (message) => {
            console.log('ChatWindow: Received message:', message);

            // Получаем ID текущего пользователя
            const currentUser = authService.getUserData();
            const currentUserId = currentUser?.id;

            // Игнорируем сообщения от самого себя (echo prevention)
            if (message.userId === currentUserId || message.senderId === currentUserId) {
                console.log('ChatWindow: Ignoring echo message from self:', message);
                return;
            }

            if (isMountedRef.current) {
                console.log('ChatWindow: Adding message from other user:', message);
                setMessages(prev => [...prev, message]);
            }
        };

        const unsubscribeMessages = chatService.onMessage(handleMessage);

        // Обработчики подключения WebSocket
        const connectionHandlers = {
            onError: (error) => {
                console.error('ChatWindow: WebSocket error:', error);
                if (isMountedRef.current) {
                    const errorMessage = ErrorHandler.getErrorMessage(error);
                    setConnectionError(`Ошибка соединения: ${errorMessage}`);
                    setIsConnected(false);
                }
            },
            onClose: (event) => {
                console.log('ChatWindow: WebSocket connection closed', event);
                if (isMountedRef.current) {
                    setIsConnected(false);
                    if (event.code !== 1000) { // Не нормальное закрытие
                        setConnectionError('Соединение потеряно, переподключение...');
                    }
                }
            },
            onConnect: () => {
                console.log('ChatWindow: WebSocket reconnected');
                if (isMountedRef.current) {
                    setIsConnected(true);
                    setConnectionError(null);
                }
            }
        };

        chatService.onConnection(connectionHandlers);

        // Подписываемся на изменения авторизации
        const unsubscribeAuth = authService.addListener((isAuthenticated) => {
            if (isAuthenticated && isMountedRef.current) {
                console.log('ChatWindow: Auth status changed to authenticated, connecting WebSocket');
                connectWebSocket();
            } else if (!isAuthenticated && isMountedRef.current) {
                console.log('ChatWindow: Auth status changed to unauthenticated, disconnecting WebSocket');
                chatService.disconnect();
                setIsConnected(false);
                setMessages([]);
                setConnectionError('Не авторизован');
            }
        });

        // Начальное подключение с небольшой задержкой
        setTimeout(() => {
            if (isMountedRef.current) {
                connectWebSocket();
            }
        }, 100);

        // Очистка при размонтировании
        return () => {
            isMountedRef.current = false;
            console.log('ChatWindow: Cleaning up WebSocket connection');

            if (typeof unsubscribeMessages === 'function') {
                unsubscribeMessages();
            }

            unsubscribeAuth();
            chatService.removeConnectionHandler(connectionHandlers);
            chatService.disconnect();
            setIsConnected(false);
        };
    }, []);

    const handleSendMessage = (e) => {
        e.preventDefault();

        if (!newMessage.trim()) {
            return;
        }

        if (!isConnected) {
            setConnectionError('Нет соединения с сервером');
            return;
        }

        const currentUser = authService.getUserData();

        try {
            // Используем sendChatMessage с chatId для правильной работы с исправленным SessionManager
            chatService.sendChatMessage(newMessage.trim(), 1);

            // НЕ добавляем сообщение в локальный стейт - оно придет через WebSocket от других участников
            setNewMessage('');
            setConnectionError(null);
        } catch (error) {
            console.error('ChatWindow: Error sending message:', error);
            const errorMessage = ErrorHandler.getErrorMessage(error);
            setConnectionError(`Ошибка отправки: ${errorMessage}`);
        }
    };

    return (
        <div className="flex flex-col h-full bg-gray-100">
            {/* Header */}
            <div className="bg-white shadow-sm p-4">
                <h1 className="text-xl font-semibold">Чат</h1>
                <div className="flex items-center mt-1">
                    <div className={`w-3 h-3 rounded-full mr-2 ${isConnected ? 'bg-green-500' : 'bg-red-500'}`}></div>
                    <span className="text-sm text-gray-600">
                        {isConnected ? 'Подключено' : 'Отключено'}
                    </span>
                    {connectionError && (
                        <span className="text-xs text-red-500 ml-2">({connectionError})</span>
                    )}
                </div>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto p-4">
                {connectionError && !isConnected ? (
                    <div className="flex items-center justify-center h-full">
                        <div className="text-center">
                            <div className="text-red-500 mb-2 text-2xl">⚠️</div>
                            <div className="text-gray-600 mb-2">{connectionError}</div>
                            {authService.isReadyForApiCalls() && (
                                <div className="text-sm text-gray-500">
                                    Попытка переподключения...
                                </div>
                            )}
                        </div>
                    </div>
                ) : messages.length === 0 ? (
                    <div className="flex items-center justify-center h-full text-gray-500">
                        {isConnected ? 'Нет сообщений. Начните переписку!' : 'Подключение к серверу...'}
                    </div>
                ) : (
                    <div className="space-y-4">
                        {messages.map((message, index) => (
                            <div key={index} className="bg-white rounded-lg p-3 shadow-sm">
                                <div className="flex justify-between items-start">
                                    <div>
                                        <div className="font-medium text-sm text-blue-600">
                                            {message.sender || 'Неизвестный'}
                                        </div>
                                        <div className="text-gray-800 mt-1">
                                            {message.content}
                                        </div>
                                    </div>
                                    <div className="text-xs text-gray-500">
                                        {new Date(message.timestamp).toLocaleTimeString('ru-RU')}
                                    </div>
                                </div>
                            </div>
                        ))}
                        <div ref={messagesEndRef} />
                    </div>
                )}
            </div>

            {/* Message Input */}
            <div className="bg-white border-t p-4">
                <form onSubmit={handleSendMessage} className="flex space-x-2">
                    <input
                        type="text"
                        value={newMessage}
                        onChange={(e) => setNewMessage(e.target.value)}
                        placeholder={isConnected ? "Введите сообщение..." : "Ожидание подключения..."}
                        className="flex-1 border border-gray-300 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
                        disabled={!isConnected}
                        maxLength={1000}
                    />
                    <button
                        type="submit"
                        disabled={!newMessage.trim() || !isConnected}
                        className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                        Отправить
                    </button>
                </form>
                {connectionError && (
                    <div className="text-xs text-red-500 mt-1">
                        {connectionError}
                    </div>
                )}
            </div>
        </div>
    );
};

export default ChatWindow;
