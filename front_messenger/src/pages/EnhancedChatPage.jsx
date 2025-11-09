import React, { useState, useEffect } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { useLocation } from 'react-router-dom';
import { logout } from '../store/slices/authSlice';
import chatService from '../services/chatService';
import EnhancedChatWindow from '../components/EnhancedChatWindow';
import UserSearchModal from '../components/UserSearchModal';
import CreateGroupChatModal from '../components/CreateGroupChatModal';
import FriendsManager from '../components/FriendsManager';
import ProfileModal from '../components/ProfileModal';
import { getChatAvatarUrl, getChatInitials } from '../utils/avatarUtils';

const ChatPage = () => {
  const [chats, setChats] = useState([]);
  const [selectedChat, setSelectedChat] = useState(null);
  const [showUserSearch, setShowUserSearch] = useState(false);
  const [showCreateGroup, setShowCreateGroup] = useState(false);
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [activeTab, setActiveTab] = useState('chats');
  const [loading, setLoading] = useState(true);
  const [isWebSocketConnected, setIsWebSocketConnected] = useState(false);
  const [connectionError, setConnectionError] = useState(null);
  const [isInitializing, setIsInitializing] = useState(true); // Новый флаг для инициализации

  const { user } = useSelector(state => state.auth);
  const dispatch = useDispatch();
  const location = useLocation();


  const token = localStorage.getItem('token');

  const logout_icon = 'logout.png';
  const profile_icon = 'profile.png';
  const group_icon = 'group.png';

  // Обрабатываем переход из FriendsPage с выбранным чатом
  useEffect(() => {
    if (location.state?.selectedChat) {
      console.log('Received chat from navigation:', location.state.selectedChat);
      const chat = location.state.selectedChat;

      // Проверяем, есть ли уже этот чат в списке
      setChats(prevChats => {
        const chatExists = prevChats.find(c => c.id === chat.id);
        if (!chatExists) {
          return [chat, ...prevChats];
        }
        return prevChats;
      });

      // Устанавливаем выбранный чат
      setSelectedChat(chat);

      // Переключаемся на вкладку чатов
      setActiveTab('chats');

      // Очищаем state чтобы не применять повторно при следующем рендере
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  // Инициализация WebSocket соединения
  useEffect(() => {
    const initializeWebSocket = async () => {
      if (!token) {
        console.log('No token available for WebSocket connection');
        setIsInitializing(false);
        return;
      }

      try {
        console.log('Initializing WebSocket connection...');
        setIsInitializing(true);
        await chatService.connect(token);
        setIsWebSocketConnected(true);
        setConnectionError(null);
        console.log('WebSocket connected successfully');

        // ИСПРАВЛЕНИЕ: Загружаем чаты только после успешного подключения WebSocket
        await loadChats();
      } catch (error) {
        console.error('WebSocket connection failed:', error);
        setIsWebSocketConnected(false);
        setConnectionError('Ошибка подключения к серверу сообщений');
      } finally {
        setIsInitializing(false);
      }
    };

    // Обработчики WebSocket соединения
    const connectionHandlers = {
      onConnect: () => {
        console.log('WebSocket reconnected');
        setIsWebSocketConnected(true);
        setConnectionError(null);
      },
      onError: (error) => {
        console.error('WebSocket error:', error);
        setIsWebSocketConnected(false);
        setConnectionError('Ошибка соединения с сервером');
      },
      onClose: (event) => {
        console.log('WebSocket connection closed', event);
        setIsWebSocketConnected(false);
        if (event.code !== 1000) {
          setConnectionError('Соединение потеряно, переподключение...');
        }
      }
    };

    chatService.onConnection(connectionHandlers);

    // Инициализируем WebSocket
    initializeWebSocket();

    // Очистка при размонтировании
    return () => {
      console.log('Cleaning up WebSocket connection');
      chatService.removeConnectionHandler(connectionHandlers);
      chatService.disconnect();
      setIsWebSocketConnected(false);
    };
  }, [token]);

  // НОВОЕ: Обработчик входящих сообщений для обновления списка чатов
  useEffect(() => {
    if (!isWebSocketConnected) return;

    // Генерируем уникальный ID для этого обработчика
    const handlerId = `ChatPage-${Date.now()}`;
    console.log(`[ChatPage] Registering message handler: ${handlerId}`);

    const handleIncomingMessage = (message) => {
      console.log(`[ChatPage][${handlerId}] Received message:`, message);

      // Обрабатываем новое сообщение в чате
      if (message.type === 'CHAT_MESSAGE' || message.type === 'MESSAGE') {
        const chatId = message.chatId;

        // ВАЖНО: Игнорируем временные оптимистичные сообщения (с id: null)
        // Они обрабатываются только в EnhancedChatWindow
        const messageId = message.id || message.messageId;
        if (!messageId) {
          console.log(`[ChatPage][${handlerId}] ⏭️ Skipping optimistic message (id: null)`);
          return;
        }

        // Извлекаем данные сообщения из правильной структуры
        const senderId = message.senderId || message.userId;
        const senderUsername = message.senderUsername || message.username || 'Пользователь';
        const content = message.content || '';
        const timestamp = message.timestamp || message.createdAt || new Date().toISOString();
        const messageType = message.messageType || 'TEXT';
        const fileUrl = message.fileUrl;
        const fileName = message.fileName;

        if (!chatId) {
          console.warn(`[ChatPage][${handlerId}] Received message without chatId:`, message);
          return;
        }

        // Создаем объект сообщения для lastMessage
        const messageData = {
          id: messageId,
          content: content,
          sender: {
            id: senderId,
            username: senderUsername
          },
          timestamp: timestamp,
          messageType: messageType,
          fileUrl: fileUrl,
          fileName: fileName
        };

        setChats(prevChats => {
          const chatIndex = prevChats.findIndex(c => c.id === chatId);

          if (chatIndex === -1) {
            // Чат не найден в списке - возможно, это новый чат
            console.log(`[ChatPage][${handlerId}] Chat not found in list, may need to reload chats`);
            return prevChats;
          }

          const updatedChats = [...prevChats];
          const chat = { ...updatedChats[chatIndex] };

          // Обновляем последнее сообщение ТОЛЬКО если это не наше собственное сообщение
          // или если чат не выбран (чтобы не дублировать обновление из EnhancedChatWindow)
          const isOurMessage = senderId === user?.id;
          const isChatSelected = selectedChat?.id === chatId;

          console.log(`[ChatPage][${handlerId}] Message details:`, {
            messageId,
            chatId,
            senderId,
            currentUserId: user?.id,
            isOurMessage,
            isChatSelected,
            currentUnreadCount: chat.unreadCount
          });

          // Обновляем lastMessage и lastMessageAt для всех сообщений
          chat.lastMessage = messageData;
          chat.lastMessageAt = timestamp;

          // Увеличиваем счетчик непрочитанных ТОЛЬКО если:
          // 1. Это не наше сообщение (мы не отправитель)
          // 2. И чат НЕ выбран в данный момент (мы не смотрим на него)
          if (!isOurMessage && !isChatSelected) {
            chat.unreadCount = (chat.unreadCount || 0) + 1;
            console.log(`[ChatPage][${handlerId}] ✅ Incrementing unread count for chat ${chatId}: ${chat.unreadCount - 1} -> ${chat.unreadCount}`);
          } else {
            console.log(`[ChatPage][${handlerId}] ⏭️ Skipping unread increment - isOurMessage: ${isOurMessage}, isChatSelected: ${isChatSelected}`);
          }

          // Удаляем чат из текущей позиции
          updatedChats.splice(chatIndex, 1);
          // Добавляем в начало списка
          updatedChats.unshift(chat);

          return updatedChats;
        });
      }

      // Обрабатываем событие прочтения сообщений
      if (message.type === 'MESSAGE_READ') {
        const chatId = message.chatId || message.content?.chatId;
        const readByUserId = message.content?.userId || message.userId || message.readerId;

        console.log(`[ChatPage][${handlerId}] MESSAGE_READ event:`, { chatId, readByUserId, currentUserId: user?.id });

        // Если это мы прочитали сообщения, сбрасываем счетчик
        if (readByUserId === user?.id && chatId) {
          setChats(prevChats =>
            prevChats.map(chat =>
              chat.id === chatId
                ? { ...chat, unreadCount: 0 }
                : chat
            )
          );
          console.log(`[ChatPage][${handlerId}] ✅ Reset unread count for chat ${chatId}`);
        }
      }
    };

    // Подписываемся на сообщения
    const unsubscribe = chatService.onMessage(handleIncomingMessage);

    // Отписываемся при размонтировании
    return () => {
      console.log(`[ChatPage] Unregistering message handler: ${handlerId}`);
      if (unsubscribe) {
        unsubscribe();
      }
    };
  }, [isWebSocketConnected, user?.id, selectedChat?.id]);

  // НОВОЕ: Сбрасываем счетчик непрочитанных при выборе чата
  useEffect(() => {
    if (selectedChat) {
      setChats(prevChats =>
        prevChats.map(chat =>
          chat.id === selectedChat.id
            ? { ...chat, unreadCount: 0 }
            : chat
        )
      );
    }
  }, [selectedChat?.id]);

  const loadChats = async () => {
    try {
      setLoading(true);
      const userChats = await chatService.getUserChats();
      setChats(userChats);
    } catch (error) {
      console.error('Ошибка загрузки чатов:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleUserSelect = (chatOrUser) => {
    if (chatOrUser.chatType) {
      // Это чат
      setSelectedChat(chatOrUser);
      if (!chats.find(c => c.id === chatOrUser.id)) {
        setChats([chatOrUser, ...chats]);
      }
    } else {
      // Это пользователь, создаем приватный чат
      // Логика уже обработана в UserSearchModal
    }
  };

  const handleChatCreated = (newChat) => {
    setChats([newChat, ...chats]);
    setSelectedChat(newChat);
  };

  const handleChatUpdate = (updatedChat) => {
    if (updatedChat === null) {
      // Пользователь покинул чат
      setChats(chats.filter(c => c.id !== selectedChat.id));
      setSelectedChat(null);
    } else {
      // Чат обновлен
      setChats(chats.map(c => c.id === updatedChat.id ? updatedChat : c));
      setSelectedChat(updatedChat);
    }
  };

  // Обработчик для кнопки "Написать" из вкладки Друзья
  const handleStartChatFromFriends = async (friend) => {
    try {
      console.log('Starting chat with friend:', friend);
      // Создаем или получаем приватный чат с другом
      const chat = await chatService.createPrivateChat(friend.id);

      // Добавляем чат в список если его там нет
      setChats(prevChats => {
        const chatExists = prevChats.find(c => c.id === chat.id);
        if (!chatExists) {
          return [chat, ...prevChats];
        }
        return prevChats;
      });

      // Выбираем этот чат
      setSelectedChat(chat);

      // Переключаемся на вкладку чатов
      setActiveTab('chats');
    } catch (error) {
      console.error('Ошибка создания чата с другом:', error);
      alert('Не удалось создать чат. Попробуйте позже.');
    }
  };

  const formatLastMessage = (chat) => {
    if (!chat.lastMessage) return 'Нет сообщений';

    const message = chat.lastMessage;

    // Обработка системных сообщений
    if (message.messageType === 'SYSTEM') {
      return message.content || 'Системное сообщение';
    }

    // Обработка файловых сообщений
    if (message.messageType === 'FILE' || message.fileUrl) {
      const fileName = message.fileName || 'Файл';
      return `${message.sender?.username || 'Неизвестный'}: 📎 ${fileName}`;
    }

    // Обработка текстовых сообщений
    const senderName = message.sender?.username || 'Неизвестный';
    const content = message.content || '';

    if (content.length > 30) {
      return `${senderName}: ${content.substring(0, 30)}...`;
    }

    return content ? `${senderName}: ${content}` : `${senderName}: (пустое сообщение)`;
  };

  const formatLastMessageTime = (timestamp) => {
    if (!timestamp) return '';

    const date = new Date(timestamp);
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const messageDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());

    if (messageDate.getTime() === today.getTime()) {
      return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    } else {
      return date.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' });
    }
  };

  const getChatTitle = (chat) => {
    if (chat.chatType === 'GROUP') {
      return chat.chatName || 'Групповой чат';
    } else {
      // Для приватного чата показываем имя собеседника
      const otherParticipant = chat.participants?.find(p => p.id !== user?.id);
      return otherParticipant ? otherParticipant.username : 'Приватный чат';
    }
  };

  const handleLogout = () => {
    dispatch(logout());
    // Дополнительная логика выхода, если необходима
  };

  // Показываем загрузку при инициализации WebSocket
  if (isInitializing) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-100">
        <div className="text-center">
          <div className="mb-4">
            <svg className="animate-spin h-12 w-12 mx-auto text-blue-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
          </div>
          <div className="text-lg font-medium text-gray-700 mb-2">
            Подключение к серверу сообщений...
          </div>
          <div className="text-sm text-gray-500">
            Пожалуйста, подождите
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen bg-gray-100">
      {/* Боковая панель */}
      <div className="w-[31rem] bg-white border-r border-gray-200 flex flex-col">
        {/* Заголовок */}
        <div className="p-4 border-b border-gray-200 bg-blue-50">
          <div className="flex items-center justify-between">
            <div className="flex items-center">
              <div className="w-10 h-10 bg-blue-500 rounded-full flex items-center justify-center mr-3 overflow-hidden">
                {user?.profilePictureUrl ? (
                  <img
                    src={`http://localhost:8083${user.profilePictureUrl}`}
                    alt={user.username}
                    className="w-full h-full rounded-full object-cover"
                    onError={(e) => {
                      e.target.style.display = 'none';
                      e.target.nextSibling.style.display = 'flex';
                    }}
                  />
                ) : null}
                <span
                  className="text-white font-semibold text-lg w-full h-full flex items-center justify-center"
                  style={{ display: user?.profilePictureUrl ? 'none' : 'flex' }}
                >
                  {user?.username?.charAt(0)?.toUpperCase() || 'U'}
                </span>
              </div>
              <div>
                <h1 className="text-lg font-semibold text-gray-800">
                  {user?.username || 'Пользователь'}
                </h1>
                <div className="flex items-center">
                  <div className={`w-2 h-2 rounded-full mr-2 ${isWebSocketConnected ? 'bg-green-500' : 'bg-red-500'}`}></div>
                  <span className="text-sm text-gray-600">
                    {isWebSocketConnected ? 'В сети' : 'Подключение...'}
                  </span>
                  {connectionError && (
                    <span className="text-xs text-red-500 ml-2">({connectionError})</span>
                  )}
                </div>
              </div>
            </div>
            <div className="flex items-center space-x-2">
              <button
                onClick={() => setShowProfileModal(true)}
                className="p-2 text-gray-600 hover:text-gray-800 hover:bg-gray-200 rounded-full"
                title="Настройки профиля"
              >
                <img src={profile_icon} alt="" className="w-5 h-5 opacity-90 group-hover:opacity-100" draggable="false" />
              </button>
              <button
                onClick={handleLogout}
                className="p-2 text-gray-600 hover:text-gray-800 hover:bg-gray-200 rounded-full"
                title="Выйти"
              >
                <img src={logout_icon} alt="" className="w-5 h-5 opacity-90 group-hover:opacity-100" draggable="false" />
              </button>
            </div>
          </div>
        </div>

        {/* Табы */}
        <div className="flex border-b border-gray-200">
          <button
            onClick={() => setActiveTab('chats')}
            className={`flex-1 px-4 py-3 text-sm font-medium ${
              activeTab === 'chats'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            Чаты
          </button>
          <button
            onClick={() => setActiveTab('friends')}
            className={`flex-1 px-4 py-3 text-sm font-medium ${
              activeTab === 'friends'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            Друзья
          </button>
        </div>

        {/* Кнопки действий */}
        <div className="p-4 border-b border-gray-200 space-y-2">
          <button
            onClick={() => setShowUserSearch(true)}
            className="w-full bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600"
          >
            Найти пользователя
          </button>
          <button
            onClick={() => setShowCreateGroup(true)}
            className="w-full bg-green-500 text-white px-4 py-2 rounded hover:bg-green-600"
          >
            Создать групповой чат
          </button>
        </div>

        {/* Содержимое в зависимости от активного таба */}
        <div className="flex-1 overflow-y-auto">
          {activeTab === 'chats' && (
            <>
              {loading && (
                <div className="p-4 text-center text-gray-500">
                  Загрузка чатов...
                </div>
              )}

              {!loading && chats.length === 0 && (
                <div className="p-4 text-center text-gray-500">
                  <div className="mb-2">У вас пока нет чатов</div>
                  <div className="text-sm">Найдите пользователей для начала общения</div>
                </div>
              )}

              {!loading && chats.map(chat => {
                const chatAvatarUrl = getChatAvatarUrl(chat, user);
                const chatInitials = getChatInitials(chat, user);
                // Получаем количество непрочитанных сообщений
                const unreadCount = chat.unreadCount || 0;

                return (
                  <div
                    key={chat.id}
                    onClick={() => setSelectedChat(chat)}
                    className={`p-4 border-b border-gray-100 cursor-pointer hover:bg-gray-50 ${
                      selectedChat?.id === chat.id ? 'bg-blue-50 border-blue-200' : ''
                    }`}
                  >
                    <div className="flex items-center">
                      {/* ОБНОВЛЕНО: Аватарка чата (для личных - аватарка собеседника) */}
                      <div className="w-12 h-12 bg-blue-500 rounded-full flex items-center justify-center mr-3 overflow-hidden flex-shrink-0 relative">
                        {chatAvatarUrl ? (
                          <img
                            src={chatAvatarUrl}
                            alt={getChatTitle(chat)}
                            className="w-full h-full rounded-full object-cover"
                            onError={(e) => {
                              console.log('Avatar load error for:', chatAvatarUrl);
                              e.target.style.display = 'none';
                              if (e.target.nextSibling) {
                                e.target.nextSibling.style.display = 'flex';
                              }
                            }}
                          />
                        ) : null}
                        <span
                          className="text-white font-semibold w-full h-full flex items-center justify-center"
                          style={{ display: chatAvatarUrl ? 'none' : 'flex' }}
                        >
                          {chat.chatType === 'GROUP' ? (
                            <img src={group_icon} alt="" className="w-6 h-6" draggable="false" />
                          ) : (
                            chatInitials
                          )}
                        </span>
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex justify-between items-center">
                          <div className="font-medium truncate text-gray-900">
                            {getChatTitle(chat)}
                          </div>
                          <div className="text-xs text-gray-500 ml-2 flex-shrink-0">
                            {formatLastMessageTime(chat.lastMessageAt)}
                          </div>
                        </div>
                        <div className="text-sm truncate flex items-center text-gray-500">
                          {formatLastMessage(chat)}
                          {/* Дополнительный индикатор непрочитанных сообщений */}
                          {unreadCount > 0 && (
                            <span className="ml-2 bg-blue-500 text-white text-xs px-2 py-0.5 rounded-full flex-shrink-0">
                              {unreadCount}
                            </span>
                          )}
                        </div>
                        {chat.chatType === 'GROUP' && (
                          <div className="text-xs text-gray-400">
                            {chat.participants?.length || 0} участников
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </>
          )}

          {activeTab === 'friends' && (
            <FriendsManager onStartChat={handleStartChatFromFriends} />
          )}
        </div>
      </div>

      {/* Основная область чата */}
      <EnhancedChatWindow
        selectedChat={selectedChat}
        onChatUpdate={handleChatUpdate}
      />

      {/* Модальные окна */}
      <UserSearchModal
        isOpen={showUserSearch}
        onClose={() => setShowUserSearch(false)}
        onUserSelect={handleUserSelect}
        mode="single"
      />

      <CreateGroupChatModal
        isOpen={showCreateGroup}
        onClose={() => setShowCreateGroup(false)}
        onChatCreated={handleChatCreated}
      />

      <ProfileModal
        isOpen={showProfileModal}
        onClose={() => setShowProfileModal(false)}
        user={user}
      />
    </div>
  );
};

export default ChatPage;
