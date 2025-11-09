import React, { useState, useEffect } from 'react';
import userService from '../services/userService';
import chatService from '../services/chatService';
import { getAvatarUrl } from '../utils/avatarUtils';

const UserSearchModal = ({ isOpen, onClose, onUserSelect, mode = 'chat' }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedUsers, setSelectedUsers] = useState([]);
  const [actionLoading, setActionLoading] = useState({});

  useEffect(() => {
    if (searchQuery.length > 2) {
      searchUsers();
    } else {
      setSearchResults([]);
    }
  }, [searchQuery]);

  const searchUsers = async () => {
    try {
      setLoading(true);
      const results = await userService.searchUsers(searchQuery);
      setSearchResults(results);
    } catch (error) {
      console.error('Ошибка поиска пользователей:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleUserSelect = (user) => {
    if (mode === 'single' || mode === 'friends') {
      onUserSelect(user);
      onClose();
    } else {
      // Для группового выбора
      if (selectedUsers.find(u => u.id === user.id)) {
        setSelectedUsers(selectedUsers.filter(u => u.id !== user.id));
      } else {
        setSelectedUsers([...selectedUsers, user]);
      }
    }
  };

  const handleStartChat = async (user) => {
    try {
      setActionLoading({ ...actionLoading, [`chat_${user.id}`]: true });
      const chat = await chatService.createPrivateChat(user.id);
      onUserSelect(chat);
      onClose();
    } catch (error) {
      console.error('Ошибка создания чата:', error);
    } finally {
      setActionLoading({ ...actionLoading, [`chat_${user.id}`]: false });
    }
  };

  const handleSendFriendRequest = async (userId) => {
    try {
      setActionLoading({ ...actionLoading, [`friend_${userId}`]: true });
      await userService.sendFriendRequest(userId);
      // Обновляем результаты поиска
      await searchUsers();
      // Уведомляем родительский компонент об изменении (для обновления списка исходящих запросов)
      if (onUserSelect) {
        onUserSelect('refresh');
      }
    } catch (error) {
      console.error('Ошибка отправки запроса дружбы:', error);
    } finally {
      setActionLoading({ ...actionLoading, [`friend_${userId}`]: false });
    }
  };

  const handleConfirmSelection = () => {
    onUserSelect(selectedUsers);
    onClose();
    setSelectedUsers([]);
  };

  const getFriendshipStatusText = (status) => {
    switch (status) {
      case 'PENDING': return 'Запрос отправлен';
      case 'ACCEPTED': return 'Друзья';
      case 'REJECTED': return 'Отклонен';
      case 'BLOCKED': return 'Заблокирован';
      default: return null;
    }
  };

  const getFriendshipStatusColor = (status) => {
    switch (status) {
      case 'PENDING': return 'text-yellow-600 bg-yellow-50';
      case 'ACCEPTED': return 'text-green-600 bg-green-50';
      case 'REJECTED': return 'text-red-600 bg-red-50';
      case 'BLOCKED': return 'text-red-800 bg-red-100';
      default: return '';
    }
  };

  const getModalTitle = () => {
    switch (mode) {
      case 'friends': return 'Найти новых друзей';
      case 'single': return 'Найти пользователя';
      case 'multiple': return 'Выбрать пользователей';
      default: return 'Поиск пользователей';
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-full max-w-md max-h-96 overflow-hidden">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-semibold">
            {getModalTitle()}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-500 hover:text-gray-700 text-2xl"
          >
            ✕
          </button>
        </div>

        <input
          type="text"
          placeholder="Введите имя пользователя..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:border-blue-500 mb-4"
          autoFocus
        />

        {mode === 'multiple' && selectedUsers.length > 0 && (
          <div className="mb-4">
            <div className="text-sm text-gray-600 mb-2">
              Выбрано: {selectedUsers.length}
            </div>
            <div className="flex flex-wrap gap-2 mb-2">
              {selectedUsers.map(user => (
                <span
                  key={user.id}
                  className="bg-blue-100 text-blue-800 px-2 py-1 rounded-full text-sm"
                >
                  {user.username}
                </span>
              ))}
            </div>
            <button
              onClick={handleConfirmSelection}
              className="w-full bg-blue-500 text-white px-4 py-2 rounded-md hover:bg-blue-600"
            >
              Подтвердить выбор
            </button>
          </div>
        )}

        <div className="max-h-64 overflow-y-auto">
          {loading && (
            <div className="text-center py-4">
              <div className="inline-block animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600 mb-2"></div>
              <div className="text-gray-500">Поиск...</div>
            </div>
          )}

          {!loading && searchResults.length === 0 && searchQuery.length > 2 && (
            <div className="text-center py-4 text-gray-500">
              Пользователи не найдены
            </div>
          )}

          {!loading && searchQuery.length <= 2 && searchQuery.length > 0 && (
            <div className="text-center py-4 text-gray-500">
              Введите минимум 3 символа для поиска
            </div>
          )}

          {searchResults.map(user => (
            <div
              key={user.id}
              className="flex items-center justify-between p-3 hover:bg-gray-50 rounded-md"
            >
              <div className="flex items-center">
                <div className="w-10 h-10 bg-gray-300 rounded-full flex items-center justify-center mr-3 relative">
                  {user.profilePictureUrl ? (
                    <img
                      src={getAvatarUrl(user.profilePictureUrl)}
                      alt={user.username}
                      className="w-full h-full rounded-full object-cover"
                      onError={(e) => {
                        e.target.style.display = 'none';
                        e.target.parentElement.innerHTML = `<span class="text-gray-600 font-semibold">${user.username.charAt(0).toUpperCase()}</span>`;
                      }}
                    />
                  ) : (
                    <span className="text-gray-600 font-semibold">
                      {user.username.charAt(0).toUpperCase()}
                    </span>
                  )}
                  {user.isOnline && (
                    <div className="absolute -bottom-1 -right-1 w-3 h-3 bg-green-500 border-2 border-white rounded-full"></div>
                  )}
                </div>
                <div>
                  <div className="font-medium">{user.username}</div>
                  {(user.firstName || user.lastName) && (
                    <div className="text-sm text-gray-500">
                      {user.firstName} {user.lastName}
                    </div>
                  )}
                  {user.friendshipStatus && (
                    <div className={`text-xs px-2 py-1 rounded-full inline-block mt-1 ${getFriendshipStatusColor(user.friendshipStatus)}`}>
                      {getFriendshipStatusText(user.friendshipStatus)}
                    </div>
                  )}
                </div>
              </div>

              <div className="flex gap-2">
                {(mode === 'single' || mode === 'friends') && (
                  <>
                    {user.canStartChat && mode === 'single' && (
                      <button
                        onClick={() => handleStartChat(user)}
                        disabled={actionLoading[`chat_${user.id}`]}
                        className="bg-blue-500 text-white px-3 py-1 rounded text-sm hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {actionLoading[`chat_${user.id}`] ? '...' : 'Написать'}
                      </button>
                    )}
                    {!user.friendshipStatus && (
                      <button
                        onClick={() => handleSendFriendRequest(user.id)}
                        disabled={actionLoading[`friend_${user.id}`]}
                        className="bg-green-500 text-white px-3 py-1 rounded text-sm hover:bg-green-600 disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {actionLoading[`friend_${user.id}`] ? '...' : 'Добавить в друзья'}
                      </button>
                    )}
                    {user.friendshipStatus === 'ACCEPTED' && mode === 'friends' && (
                      <button
                        onClick={() => handleStartChat(user)}
                        disabled={actionLoading[`chat_${user.id}`]}
                        className="bg-blue-500 text-white px-3 py-1 rounded text-sm hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {actionLoading[`chat_${user.id}`] ? '...' : 'Написать'}
                      </button>
                    )}
                  </>
                )}

                {mode === 'multiple' && (
                  <button
                    onClick={() => handleUserSelect(user)}
                    className={`px-3 py-1 rounded text-sm ${
                      selectedUsers.find(u => u.id === user.id)
                        ? 'bg-blue-500 text-white'
                        : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                    }`}
                  >
                    {selectedUsers.find(u => u.id === user.id) ? 'Выбран' : 'Выбрать'}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>

        {searchQuery.length === 0 && (
          <div className="text-center py-4 text-gray-400">
            Начните печатать для поиска пользователей
          </div>
        )}
      </div>
    </div>
  );
};

export default UserSearchModal;
