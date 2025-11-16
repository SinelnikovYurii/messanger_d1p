import React, { useState, useEffect } from 'react';
import userService from '../services/userService';
import chatService from '../services/chatService';
import { getAvatarUrl } from '../utils/avatarUtils';
import './UserSearchModal.css';

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
      <div className="shadow-xl rounded-2xl px-7 py-6 w-[440px] h-[520px] flex flex-col relative border border-[#400d0d]" style={{backgroundImage:'linear-gradient(135deg,#400d0d 0%,#e07a5f 100%)',backgroundColor:'#f5e6d6',color:'#f5e6d6'}}>
        <button
          onClick={onClose}
          className="absolute top-3 right-3 p-1 rounded-full hover:bg-[#F5F5DC] transition-colors"
          aria-label="Закрыть"
          style={{background:'none',border:'none'}}
        >
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M6 6L18 18" stroke="#F5F5DC" strokeWidth="2.5" strokeLinecap="round"/>
            <path d="M18 6L6 18" stroke="#F5F5DC" strokeWidth="2.5" strokeLinecap="round"/>
          </svg>
        </button>
        <h2 className="text-lg font-semibold mb-3 text-center" style={{color:'#f5e6d6'}}>
          {getModalTitle()}
        </h2>
        <input
          type="text"
          placeholder="Поиск пользователя..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full px-3 py-2 border border-[#B22222] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#B22222] mb-3 bg-[#FFF8F0] text-[#B22222] placeholder-[#B22222]"
          autoFocus
        />
        {mode === 'multiple' && selectedUsers.length > 0 && (
          <div className="mb-2">
            <div className="text-xs mb-1" style={{color:'#8B1A1A'}}>
              Выбрано: {selectedUsers.length}
            </div>
            <div className="flex flex-wrap gap-1 mb-1">
              {selectedUsers.map(user => (
                <span
                  key={user.id}
                  className="px-2 py-1 rounded-full text-xs"
                  style={{backgroundColor:'#B22222',color:'#F5F5DC'}}
                >
                  {user.username}
                </span>
              ))}
            </div>
            <button
              onClick={handleConfirmSelection}
              className="w-full px-3 py-1 rounded-lg text-xs transition-colors"
              style={{backgroundColor:'#B22222',color:'#F5F5DC'}}
            >
              Подтвердить
            </button>
          </div>
        )}
        <div className="flex-1 overflow-y-auto user-search-scrollbar" style={{color:'#f5e6d6'}}>
          {searchResults.length === 0 && searchQuery.length > 2 && (
            <div className="text-center py-6 text-sm" style={{color:'#B22222'}}>Пользователи не найдены</div>
          )}
          {searchResults.length === 0 && searchQuery.length <= 2 && searchQuery.length > 0 && (
            <div className="text-center py-6 text-sm" style={{color:'#B22222'}}>Введите минимум 3 символа для поиска</div>
          )}
          <ul className="space-y-2">
            {searchResults.map(user => (
              <li
                key={user.id}
                className="flex items-center justify-between p-2 rounded-xl shadow-sm border border-[#B22222]"
                style={{minHeight:'56px',maxHeight:'56px',cursor:'pointer',backgroundColor:'#FFF8F0',transition:'background-color 0.2s, box-shadow 0.2s',color:'#f5e6d6'}}
                onMouseEnter={e => {
                  e.currentTarget.style.backgroundColor = '#dab78c';
                  e.currentTarget.style.boxShadow = '0 4px 12px rgba(224, 122, 95, 0.3)';
                }}
                onMouseLeave={e => {
                  e.currentTarget.style.backgroundColor = '#FFF8F0';
                  e.currentTarget.style.boxShadow = '';
                }}
              >
                <div className="flex items-center">
                  <div className="w-9 h-9 bg-[#F5F5DC] rounded-full flex items-center justify-center mr-2 relative overflow-hidden border border-[#B22222]">
                    {user.profilePictureUrl ? (
                      <img
                        src={getAvatarUrl(user.profilePictureUrl)}
                        alt={user.username}
                        className="w-full h-full rounded-full object-cover"
                        onError={(e) => {
                          e.target.style.display = 'none';
                          e.target.parentElement.innerHTML = `<span style='color:#8B1A1A;font-weight:600'>${user.username.charAt(0).toUpperCase()}</span>`;
                        }}
                      />
                    ) : (
                      <span style={{color:'#8B1A1A',fontWeight:600}}>
                        {user.username.charAt(0).toUpperCase()}
                      </span>
                    )}
                    {user.isOnline && (
                      <div className="absolute -bottom-1 -right-1 w-2.5 h-2.5 rounded-full border-2 border-[#FFF8F0]" style={{backgroundColor:'#0b5d0b'}}></div>
                    )}
                  </div>
                  <div>
                    <div className="font-medium text-sm" style={{color:'#B22222'}}>{user.username}</div>
                    {(user.firstName || user.lastName) && (
                      <div className="text-xs" style={{color:'#8B1A1A'}}>
                        {user.firstName} {user.lastName}
                      </div>
                    )}
                    {user.friendshipStatus && (
                      <div className={`text-[10px] px-2 py-0.5 rounded-full inline-block mt-1`} style={{backgroundColor:'#F5F5DC',color:'#B22222'}}>
                        {getFriendshipStatusText(user.friendshipStatus)}
                      </div>
                    )}
                  </div>
                </div>
                <div className="flex gap-1">
                  {(mode === 'single' || mode === 'friends') && (
                    <>
                      {user.canStartChat && mode === 'single' && (
                        <button
                          onClick={() => handleStartChat(user)}
                          disabled={actionLoading[`chat_${user.id}`]}
                          className="px-3 py-1 rounded-lg text-xs font-semibold transition-all duration-200 shadow-sm"
                          style={{
                            backgroundColor: actionLoading[`chat_${user.id}`] ? '#B22222' : '#B22222',
                            color: '#FFF8F0',
                            border: 'none',
                            boxShadow: '0 1px 4px #b2222222',
                            cursor: actionLoading[`chat_${user.id}`] ? 'not-allowed' : 'pointer',
                          }}
                          onMouseEnter={e => {
                            if (!actionLoading[`chat_${user.id}`]) e.currentTarget.style.backgroundColor = '#8B1A1A';
                          }}
                          onMouseLeave={e => {
                            if (!actionLoading[`chat_${user.id}`]) e.currentTarget.style.backgroundColor = '#B22222';
                          }}
                        >
                          {actionLoading[`chat_${user.id}`] ? '...' : 'Написать'}
                        </button>
                      )}
                      {!user.friendshipStatus && (
                        <button
                          onClick={() => handleSendFriendRequest(user.id)}
                          disabled={actionLoading[`friend_${user.id}`]}
                          className="px-3 py-1 rounded-lg text-xs font-semibold transition-all duration-200 shadow-sm"
                          style={{
                            backgroundColor: actionLoading[`friend_${user.id}`] ? '#228B22' : '#228B22',
                            color: '#FFF8F0',
                            border: 'none',
                            boxShadow: '0 1px 4px #228b2222',
                            cursor: actionLoading[`friend_${user.id}`] ? 'not-allowed' : 'pointer',
                          }}
                          onMouseEnter={e => {
                            if (!actionLoading[`friend_${user.id}`]) e.currentTarget.style.backgroundColor = '#176d16';
                          }}
                          onMouseLeave={e => {
                            if (!actionLoading[`friend_${user.id}`]) e.currentTarget.style.backgroundColor = '#228B22';
                          }}
                        >
                          {actionLoading[`friend_${user.id}`] ? '...' : 'Добавить'}
                        </button>
                      )}
                      {user.friendshipStatus === 'ACCEPTED' && mode === 'friends' && (
                        <button
                          onClick={() => handleStartChat(user)}
                          disabled={actionLoading[`chat_${user.id}`]}
                          className="px-3 py-1 rounded-lg text-xs font-semibold transition-all duration-200 shadow-sm"
                          style={{
                            backgroundColor: actionLoading[`chat_${user.id}`] ? '#B22222' : '#B22222',
                            color: '#FFF8F0',
                            border: 'none',
                            boxShadow: '0 1px 4px #b2222222',
                            cursor: actionLoading[`chat_${user.id}`] ? 'not-allowed' : 'pointer',
                          }}
                          onMouseEnter={e => {
                            if (!actionLoading[`chat_${user.id}`]) e.currentTarget.style.backgroundColor = '#8B1A1A';
                          }}
                          onMouseLeave={e => {
                            if (!actionLoading[`chat_${user.id}`]) e.currentTarget.style.backgroundColor = '#B22222';
                          }}
                        >
                          {actionLoading[`chat_${user.id}`] ? '...' : 'Написать'}
                        </button>
                      )}
                    </>
                  )}
                  {mode === 'multiple' && (
                    <button
                      onClick={() => handleUserSelect(user)}
                      className="px-2 py-1 rounded text-xs transition-colors"
                      style={selectedUsers.find(u => u.id === user.id)
                        ? {backgroundColor:'#B22222',color:'#F5F5DC'}
                        : {backgroundColor:'#F5F5DC',color:'#B22222',border:'1px solid #B22222'}}
                    >
                      {selectedUsers.find(u => u.id === user.id) ? '✓' : 'Выбрать'}
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </div>
        {searchQuery.length === 0 && (
          <div className="text-center py-4 text-sm" style={{color:'#f5e6d6'}}>
            Начните печатать для поиска пользователей
          </div>
        )}
      </div>
    </div>
  );
};

export default UserSearchModal;
