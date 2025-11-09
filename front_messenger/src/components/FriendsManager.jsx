import React, { useState, useEffect } from 'react';
import userService from '../services/userService';
import UserSearchModal from './UserSearchModal';
import chatService from '../services/chatService';

const FriendsManager = ({ onStartChat }) => {
  const [activeTab, setActiveTab] = useState('friends');
  const [friends, setFriends] = useState([]);
  const [incomingRequests, setIncomingRequests] = useState([]);
  const [outgoingRequests, setOutgoingRequests] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showSearchModal, setShowSearchModal] = useState(false);
  const [error, setError] = useState(null);

  // Загружаем все данные при монтировании компонента
  useEffect(() => {
    loadAllData();

    // Подписываемся на WebSocket уведомления через единый chatService
    console.log('Subscribing to friend notifications via chatService');
    const unsubscribe = chatService.onMessage(handleWebSocketMessage);

    // Очистка при размонтировании
    return () => {
      console.log('Unsubscribing from friend notifications');
      unsubscribe();
    };
  }, []);

  // Обработка WebSocket сообщений
  const handleWebSocketMessage = (message) => {
    console.log('Received WebSocket message in FriendsManager:', message);

    // Обрабатываем уведомления о запросах в друзья
    if (message.type === 'FRIEND_REQUEST_RECEIVED') {
      console.log('New friend request received:', message);
      // Автоматически обновляем входящие запросы
      loadIncomingRequests();
      setError(null);
    } else if (message.type === 'FRIEND_REQUEST_SENT') {
      console.log('Friend request sent:', message);
      // Обновляем исходящие запросы - мы отправили новый запрос
      loadOutgoingRequests();
      setError(null);
    } else if (message.type === 'FRIEND_REQUEST_ACCEPTED') {
      console.log('Friend request accepted:', message);
      // Обновляем все данные - запрос переместился в друзья
      loadAllData();
      setError(null);
    } else if (message.type === 'FRIEND_REQUEST_REJECTED') {
      console.log('Friend request rejected:', message);
      // Обновляем исходящие запросы - наш запрос был отклонен
      loadOutgoingRequests();
      // Обновляем также друзей на случай изменений
      loadFriends();
      setError(null);
    }
  };

  // Загрузить список друзей
  const loadFriends = async () => {
    try {
      const friendsData = await userService.getFriends();
      setFriends(friendsData);
      console.log('Список друзей обновлен:', friendsData);
    } catch (error) {
      console.error('Ошибка загрузки списка друзей:', error);
    }
  };

  // Загрузка данных при переключении вкладок
  useEffect(() => {
    loadTabData();
  }, [activeTab]);

  // Загружает данные для всех вкладок одновременно
  const loadAllData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [friendsData, incomingData, outgoingData] = await Promise.all([
        userService.getFriends(),
        userService.getIncomingFriendRequests(),
        userService.getOutgoingFriendRequests()
      ]);

      console.log("Данные о друзьях:", friendsData);

      // Подробный вывод структуры входящих запросов
      console.log("Входящие запросы:", incomingData);
      if (incomingData && incomingData.length > 0) {
        console.log("Структура первого входящего запроса (все поля):");
        for (const key in incomingData[0]) {
          console.log(`Поле '${key}':`, incomingData[0][key]);
        }
      }

      console.log("Исходящие запросы:", outgoingData);

      setFriends(friendsData);
      setIncomingRequests(incomingData);
      setOutgoingRequests(outgoingData);
    } catch (error) {
      console.error('Ошибка загрузки данных:', error);
      setError('Не удалось загрузить данные');

      // Проверяем, не связана ли ошибка с авторизацией
      if (error.response && (error.response.status === 401 || error.response.status === 403)) {
        // Обработка ошибки авторизации без перенаправления
        console.log('Ошибка авторизации, но не перенаправляем на логин');
      }
    } finally {
      setLoading(false);
    }
  };

  // Загружает данные только для текущей активной вкладки
  const loadTabData = async () => {
    setLoading(true);
    setError(null);
    try {
      switch (activeTab) {
        case 'friends':
          const friendsData = await userService.getFriends();
          setFriends(friendsData);
          break;
        case 'incoming':
          await loadIncomingRequests();
          break;
        case 'outgoing':
          await loadOutgoingRequests();
          break;
      }
    } catch (error) {
      console.error('Ошибка загрузки данных для вкладки:', error);
      setError('Не удалось загрузить данные');
    } finally {
      setLoading(false);
    }
  };

  // Загрузить входящие запросы
  const loadIncomingRequests = async () => {
    try {
      const incomingData = await userService.getIncomingFriendRequests();
      setIncomingRequests(incomingData);
      console.log('Входящие запросы обновлены:', incomingData);
    } catch (error) {
      console.error('Ошибка загрузки входящих запросов:', error);
    }
  };

  // Загрузить исходящие запросы
  const loadOutgoingRequests = async () => {
    try {
      const outgoingData = await userService.getOutgoingFriendRequests();
      setOutgoingRequests(outgoingData);
      console.log('Исходящие запросы обновлены:', outgoingData);
    } catch (error) {
      console.error('Ошибка загрузки исходящих запросов:', error);
    }
  };

  const handleAcceptFriendRequest = async (request) => {
    try {
      // Детальное логирование структуры объекта запроса
      console.log('FriendsManager: Весь объект запроса дружбы (accept):', JSON.stringify(request));

      // Проверяем все возможные поля, которые могут содержать ID запроса дружбы
      const requestId = request.friendshipId || request.requestId || request.friendship_id ||
                       request.friendRequest?.id || request.friendship?.id || request.request?.id ||
                       request.sender_id || request.senderId || request.userId || request.id;

      console.log('FriendsManager: Используемый ID запроса дружбы:', requestId);
      console.log('FriendsManager: Тип ID:', typeof requestId);
      console.log('FriendsManager: Отправляем данные:', JSON.stringify({ requestId, accept: true }));

      // Фиксируем точное время запроса для анализа задержек
      const startTime = Date.now();

      // Вызываем API сервиса с правильным ID
      const response = await userService.respondToFriendRequest(requestId, true);

      // Фиксируем время ответа
      const endTime = Date.now();
      console.log(`FriendsManager: Время запроса: ${endTime - startTime}ms`);

      console.log('FriendsManager: Ответ сервера:', response);

      await loadAllData(); // Обновляем все данные после принятия запроса
      setError(null);
    } catch (error) {
      console.error('FriendsManager: Детали ошибки принятия запроса:', error);
      if (error.response) {
        console.error('FriendsManager: Статус ошибки:', error.response.status);
        console.error('FriendsManager: Данные ошибки:', error.response.data);
      }
      setError('Не удалось принять запрос дружбы. Возможно, запрос был удален или уже обработан.');
    }
  };

  const handleRejectFriendRequest = async (request) => {
    try {
      // Детальное логирование структуры объекта запроса
      console.log('FriendsManager: Весь объект запроса дружбы (reject):', JSON.stringify(request));

      // Проверяем все возможные поля, которые могут содержать ID запроса дружбы
      const requestId = request.friendshipId || request.requestId || request.friendship_id ||
                       request.friendRequest?.id || request.friendship?.id || request.request?.id ||
                       request.sender_id || request.senderId || request.userId || request.id;

      console.log('FriendsManager: Используемый ID для отклонения запроса дружбы:', requestId);
      console.log('FriendsManager: Тип ID:', typeof requestId);
      console.log('FriendsManager: Отправляем данные для отклонения:', JSON.stringify({ requestId, accept: false }));

      // Фиксируем точное время запроса для анализа задержек
      const startTime = Date.now();

      // Вызываем API сервиса с правильным ID
      const response = await userService.respondToFriendRequest(requestId, false);

      // Фиксируем время ответа
      const endTime = Date.now();
      console.log(`FriendsManager: Время запроса: ${endTime - startTime}ms`);

      console.log('FriendsManager: Ответ сервера при отклонении:', response);

      await loadAllData(); // Обновляем все данные
      setError(null);
    } catch (error) {
      console.error('FriendsManager: Детали ошибки отклонения запроса:', error);
      if (error.response) {
        console.error('FriendsManager: Статус ошибки:', error.response.status);
        console.error('FriendsManager: Данные ошибки:', error.response.data);
      }
      setError('Не удалось отклонить запрос дружбы');
    }
  };

  const handleRemoveFriend = async (friendId) => {
    if (window.confirm('Вы уверены, что хотите удалить этого пользователя из друзей?')) {
      try {
        await userService.removeFriend(friendId);
        await loadAllData(); // Обновляем все данные
        setError(null);
      } catch (error) {
        console.error('Ошибка удаления друга:', error);
        setError('Не удалось удалить пользователя из друзей');
      }
    }
  };

  const handleStartChat = (user) => {
    if (onStartChat) {
      onStartChat(user);
    }
  };

  const renderUserCard = (user, actions) => {
    // Формируем правильный URL для аватарки
    const avatarUrl = user.profilePictureUrl
      ? `http://localhost:8083${user.profilePictureUrl}`
      : null;

    return (
      <div key={user.id} className="flex flex-col sm:flex-row sm:items-center justify-between p-4 bg-white rounded-lg shadow-sm border hover:shadow-md transition-shadow gap-4">
        <div className="flex items-center flex-1 min-w-0">
          <div className="w-12 h-12 bg-gray-300 rounded-full flex items-center justify-center mr-4 relative flex-shrink-0">
            {avatarUrl ? (
              <img
                src={avatarUrl}
                alt={user.username}
                className="w-full h-full rounded-full object-cover"
                onError={(e) => {
                  console.log('Avatar load error for:', avatarUrl);
                  e.target.style.display = 'none';
                  e.target.nextSibling.style.display = 'flex';
                }}
              />
            ) : null}
            <span
              className="text-gray-600 font-semibold text-lg w-full h-full flex items-center justify-center"
              style={{ display: avatarUrl ? 'none' : 'flex' }}
            >
              {user.username.charAt(0).toUpperCase()}
            </span>
            {/* Индикатор онлайн статуса */}
            {user.isOnline && (
              <div className="absolute -bottom-1 -right-1 w-4 h-4 bg-green-500 border-2 border-white rounded-full"></div>
            )}
          </div>
          <div className="min-w-0 flex-1">
            <div className="font-medium text-lg truncate">{user.username}</div>
            {(user.firstName || user.lastName) && (
              <div className="text-gray-600 truncate">
                {user.firstName} {user.lastName}
              </div>
            )}
            <div className="flex items-center mt-1">
              <span className="text-sm text-gray-500 truncate">
                {user.isOnline ? (
                  <span className="text-green-600 font-medium">В сети</span>
                ) : (
                  user.lastSeen ? `Был(а) в сети ${new Date(user.lastSeen).toLocaleString()}` : 'Не в сети'
                )}
              </span>
            </div>
          </div>
        </div>
        <div className="flex gap-2 flex-shrink-0 justify-end sm:justify-start">
          {actions}
        </div>
      </div>
    );
  };

  return (
    <div className="w-full h-full p-4 sm:p-6">
      <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center mb-6 gap-4">
        <h1 className="text-2xl font-bold">Управление друзьями</h1>
        <button
          onClick={() => setShowSearchModal(true)}
          className="bg-blue-500 text-white px-4 py-2 rounded-lg hover:bg-blue-600 transition-colors flex items-center justify-center gap-2 w-full sm:w-auto"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <span className="whitespace-nowrap">Найти пользователей</span>
        </button>
      </div>

      {/* Ошибки */}
      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
          {error}
        </div>
      )}

      {/* Карточки-статистика вместо табов */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        <button
          onClick={() => setActiveTab('friends')}
          className={`p-4 rounded-lg border-2 transition-all duration-200 ${
            activeTab === 'friends'
              ? 'border-blue-500 bg-blue-50 shadow-md'
              : 'border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm'
          }`}
        >
          <div className="flex items-center justify-between">
            <div className="text-left">
              <div className={`text-2xl font-bold ${activeTab === 'friends' ? 'text-blue-600' : 'text-gray-900'}`}>
                {friends.length}
              </div>
              <div className={`text-sm ${activeTab === 'friends' ? 'text-blue-600' : 'text-gray-600'}`}>
                Друзья
              </div>
            </div>
            <div className={`p-2 rounded-full ${activeTab === 'friends' ? 'bg-blue-100' : 'bg-gray-100'}`}>
              <svg className={`w-6 h-6 ${activeTab === 'friends' ? 'text-blue-600' : 'text-gray-400'}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
            </div>
          </div>
        </button>

        <button
          onClick={() => setActiveTab('incoming')}
          className={`p-4 rounded-lg border-2 transition-all duration-200 ${
            activeTab === 'incoming'
              ? 'border-green-500 bg-green-50 shadow-md'
              : 'border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm'
          }`}
        >
          <div className="flex items-center justify-between">
            <div className="text-left">
              <div className={`text-2xl font-bold ${activeTab === 'incoming' ? 'text-green-600' : 'text-gray-900'}`}>
                {incomingRequests.length}
              </div>
              <div className={`text-sm ${activeTab === 'incoming' ? 'text-green-600' : 'text-gray-600'}`}>
                Входящие
              </div>
            </div>
            <div className={`p-2 rounded-full ${activeTab === 'incoming' ? 'bg-green-100' : 'bg-gray-100'} relative`}>
              <svg className={`w-6 h-6 ${activeTab === 'incoming' ? 'text-green-600' : 'text-gray-400'}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
              </svg>
              {incomingRequests.length > 0 && (
                <div className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white text-xs rounded-full flex items-center justify-center">
                  {incomingRequests.length}
                </div>
              )}
            </div>
          </div>
        </button>

        <button
          onClick={() => setActiveTab('outgoing')}
          className={`p-4 rounded-lg border-2 transition-all duration-200 ${
            activeTab === 'outgoing'
              ? 'border-yellow-500 bg-yellow-50 shadow-md'
              : 'border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm'
          }`}
        >
          <div className="flex items-center justify-between">
            <div className="text-left">
              <div className={`text-2xl font-bold ${activeTab === 'outgoing' ? 'text-yellow-600' : 'text-gray-900'}`}>
                {outgoingRequests.length}
              </div>
              <div className={`text-sm ${activeTab === 'outgoing' ? 'text-yellow-600' : 'text-gray-600'}`}>
                Исходящие
              </div>
            </div>
            <div className={`p-2 rounded-full ${activeTab === 'outgoing' ? 'bg-yellow-100' : 'bg-gray-100'}`}>
              <svg className={`w-6 h-6 ${activeTab === 'outgoing' ? 'text-yellow-600' : 'text-gray-400'}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
              </svg>
            </div>
          </div>
        </button>
      </div>

      {/* Содержимое */}
      {loading ? (
        <div className="text-center py-8">
          <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
          <div className="text-gray-500 mt-2">Загрузка...</div>
        </div>
      ) : (
        <div className="space-y-4">
          {/* Список друзей */}
          {activeTab === 'friends' && (
            <>
              {friends.length === 0 ? (
                <div className="text-center py-8">
                  <div className="text-gray-500 mb-4">У вас пока нет друзей</div>
                  <button
                    onClick={() => setShowSearchModal(true)}
                    className="bg-blue-500 text-white px-6 py-2 rounded-lg hover:bg-blue-600 transition-colors"
                  >
                    Найти друзей
                  </button>
                </div>
              ) : (
                friends.map(friend =>
                  renderUserCard(friend, (
                    <>
                      <button
                        onClick={() => handleStartChat(friend)}
                        className="bg-blue-500 text-white px-3 sm:px-4 py-2 rounded hover:bg-blue-600 transition-colors text-sm sm:text-base whitespace-nowrap"
                      >
                        Написать
                      </button>
                      <button
                        onClick={() => handleRemoveFriend(friend.id)}
                        className="bg-red-500 text-white px-3 sm:px-4 py-2 rounded hover:bg-red-600 transition-colors text-sm sm:text-base whitespace-nowrap"
                      >
                        Удалить
                      </button>
                    </>
                  ))
                )
              )}
            </>
          )}

          {/* Входящие запросы */}
          {activeTab === 'incoming' && (
            <>
              {incomingRequests.length === 0 ? (
                <div className="text-center py-8 text-gray-500">
                  Нет входящих запросов
                </div>
              ) : (
                incomingRequests.map(request =>
                  renderUserCard(request, (
                    <>
                      <button
                        onClick={() => handleAcceptFriendRequest(request)}
                        className="bg-green-500 text-white px-3 sm:px-4 py-2 rounded hover:bg-green-600 transition-colors text-sm sm:text-base whitespace-nowrap"
                      >
                        Принять
                      </button>
                      <button
                        onClick={() => handleRejectFriendRequest(request)}
                        className="bg-red-500 text-white px-3 sm:px-4 py-2 rounded hover:bg-red-600 transition-colors text-sm sm:text-base whitespace-nowrap"
                      >
                        Отклонить
                      </button>
                    </>
                  ))
                )
              )}
            </>
          )}

          {/* Исходящие запросы */}
          {activeTab === 'outgoing' && (
            <>
              {outgoingRequests.length === 0 ? (
                <div className="text-center py-8 text-gray-500">
                  Нет исходящих запросов
                </div>
              ) : (
                outgoingRequests.map(request =>
                  renderUserCard(request, (
                    <div className="text-yellow-600 font-medium bg-yellow-50 px-3 py-2 rounded text-sm sm:text-base whitespace-nowrap">
                      Ожидает ответа
                    </div>
                  ))
                )
              )}
            </>
          )}
        </div>
      )}

      {/* Модальное окно поиска */}
      <UserSearchModal
        isOpen={showSearchModal}
        onClose={() => setShowSearchModal(false)}
        onUserSelect={(data) => {
          // Если передана строка 'refresh', просто обновляем данные
          if (data === 'refresh') {
            loadAllData();
          } else {
            // Иначе закрываем модалку и обновляем данные
            setShowSearchModal(false);
            loadAllData();
          }
        }}
        mode="friends"
      />
    </div>
  );
};

export default FriendsManager;
