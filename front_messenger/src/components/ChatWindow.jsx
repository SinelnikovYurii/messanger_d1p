import React, { useState, useEffect, useRef } from 'react';
import chatService from '../services/chatService';
import authService, { performX3DHHandshake } from '../services/authService';
import userService from '../services/userService';
import { ErrorHandler } from '../utils/errorHandler';
import {
  importPublicKey,
  deriveSessionKey,
  encryptMessage,
  decryptMessage,
  generateX3DHKeys
} from '../utils/crypto';
import { exportPrivateKey, importPrivateKeyFromFile } from '../utils/keyBackup';
import { DoubleRatchetManager } from '../utils/DoubleRatchetManager';

const ChatWindow = () => {
    const [newMessage, setNewMessage] = useState('');
    const [messages, setMessages] = useState([]);
    const [isConnected, setIsConnected] = useState(false);
    const [connectionError, setConnectionError] = useState(null);
    const [sessionKey, setSessionKey] = useState(null);
    const [partnerId, setPartnerId] = useState(null); // ID собеседника
    const [keyStatus, setKeyStatus] = useState(''); // Статус ключа E2EE
    const [x3dhKeys, setX3dhKeys] = useState(null);
    const [ratchetManager] = useState(() => new DoubleRatchetManager());
    const [ratchetReady, setRatchetReady] = useState(false);
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
        const handleMessage = async (message) => {
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
                try {
                  // Дешифруем сообщение через Double Ratchet
                  if (ratchetReady) {
                    const decrypted = await ratchetManager.decrypt(partnerId, JSON.parse(message.content));
                    setMessages(prev => [...prev, { ...message, content: decrypted }]);
                  } else {
                    setMessages(prev => [...prev, message]);
                  }
                } catch (e) {
                  setMessages(prev => [...prev, { ...message, content: '[Ошибка дешифрования]' }]);
                }
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

    // Генерация X3DH ключей при инициализации чата
    useEffect(() => {
      async function setupX3DH() {
        // Генерируем X3DH ключи для текущего пользователя (один раз)
        const keys = await generateX3DHKeys();
        setX3dhKeys(keys);
      }
      setupX3DH();
    }, []);

    // Установление сессионного ключа через X3DH handshake
    useEffect(() => {
      async function establishSessionKey() {
        if (!partnerId || !x3dhKeys) return;
        const session = await performX3DHHandshake(x3dhKeys, partnerId);
        setSessionKey(session);
      }
      establishSessionKey();
    }, [partnerId, x3dhKeys]);

    // Получение и установка сессионного ключа при старте чата
    useEffect(() => {
      async function setupE2EE() {
        // Получаем ID собеседника (например, из пропсов или выбранного чата)
        if (!partnerId) return;
        // Получаем публичный ключ собеседника
        const partnerPublicKeyRaw = await userService.getUserPublicKey(partnerId);
        const partnerPublicKey = await importPublicKey(partnerPublicKeyRaw);
        // Получаем приватный ключ из localStorage
        const privateKeyJwk = JSON.parse(localStorage.getItem('e2ee_privateKey'));
        const privateKey = await window.crypto.subtle.importKey('jwk', privateKeyJwk, { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveKey']);
        // Генерируем сессионный ключ
        const session = await deriveSessionKey(privateKey, partnerPublicKey);
        setSessionKey(session);
      }
      setupE2EE();
    }, [partnerId]);

    // Временная интеграция выбора собеседника для теста E2EE
    useEffect(() => {
      // Для теста: автоматически выбираем первого друга как собеседника
      async function selectDefaultPartner() {
        const friends = await userService.getFriends();
        if (friends && friends.length > 0) {
          setPartnerId(friends[0].id);
        }
      }
      selectDefaultPartner();
    }, []);

    // Визуализация статуса E2EE и ключа
    useEffect(() => {
      async function checkKeyStatus() {
        if (!partnerId) {
          setKeyStatus('Нет собеседника');
          return;
        }
        try {
          const partnerPublicKeyRaw = await userService.getUserPublicKey(partnerId);
          if (partnerPublicKeyRaw) {
            setKeyStatus('Ключ получен, чат защищён (E2EE)');
          } else {
            setKeyStatus('Нет публичного ключа собеседника — чат не защищён!');
          }
        } catch {
          setKeyStatus('Ошибка получения ключа — чат не защищён!');
        }
      }
      checkKeyStatus();
    }, [partnerId, sessionKey]);

    // Инициализация Double Ratchet после X3DH
    useEffect(() => {
      async function setupDoubleRatchet() {
        if (!partnerId || !sessionKey) return;
        // sessionKey — ArrayBuffer, нужен для Double Ratchet
        await ratchetManager.initSession(partnerId, sessionKey, true); // true — инициатор (Alice)
        setRatchetReady(true);
      }
      setupDoubleRatchet();
    }, [partnerId, sessionKey]);

    const handleSendMessage = async (e) => {
        e.preventDefault();

        if (!newMessage.trim()) {
            return;
        }

        if (!isConnected) {
            setConnectionError('Нет соединения с сервером');
            return;
        }

        if (!ratchetReady) {
            setConnectionError('Double Ratchet не готов');
            return;
        }

        try {
            // Шифруем сообщение через Double Ratchet
            const encrypted = await ratchetManager.encrypt(partnerId, newMessage.trim());
            chatService.sendChatMessage(JSON.stringify(encrypted), 1);
            setNewMessage('');
            setConnectionError(null);
        } catch (error) {
            console.error('ChatWindow: Error sending ratchet message:', error);
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
                <div className="mt-2 text-xs text-gray-500">
                    <span className={keyStatus.includes('защищён') ? 'text-green-600' : 'text-red-600'}>
                        {keyStatus}
                    </span>
                </div>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto p-4" style={{ backgroundImage: messages.length === 0 && isConnected ? "url('/chat_background_n.png')" : undefined, backgroundSize: '400px', backgroundRepeat: 'repeat', backgroundPosition: 'center' }}>
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

            {/* UI для экспорта/импорта приватного ключа */}
            <div className="bg-white border-t p-4 flex flex-col gap-2">
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
              <div className="flex gap-2 mt-2">
                <button
                  type="button"
                  className="bg-gray-200 px-3 py-1 rounded hover:bg-gray-300 text-xs"
                  onClick={async () => {
                    try {
                      const url = await exportPrivateKey();
                      const a = document.createElement('a');
                      a.href = url;
                      a.download = 'e2ee_private_key.json';
                      a.click();
                      URL.revokeObjectURL(url);
                    } catch (e) {
                      alert('Ошибка экспорта ключа: ' + e.message);
                    }
                  }}
                >
                  Экспортировать ключ
                </button>
                <label className="bg-gray-200 px-3 py-1 rounded hover:bg-gray-300 text-xs cursor-pointer">
                  Импортировать ключ
                  <input
                    type="file"
                    accept="application/json"
                    style={{ display: 'none' }}
                    onChange={async (e) => {
                      const file = e.target.files[0];
                      if (file) {
                        try {
                          await importPrivateKeyFromFile(file);
                          alert('Ключ успешно импортирован!');
                        } catch (e) {
                          alert('Ошибка импорта ключа: ' + e.message);
                        }
                      }
                    }}
                  />
                </label>
              </div>
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
