import React, { useState, useEffect } from 'react';
import chatService from '../services/chatService';
import userService from '../services/userService';
import UserSearchModal from './UserSearchModal';
import { useSelector } from 'react-redux';

const CreateGroupChatModal = ({ isOpen, onClose, onChatCreated }) => {
  const [chatName, setChatName] = useState('');
  const [chatDescription, setChatDescription] = useState('');
  const [selectedParticipants, setSelectedParticipants] = useState([]);
  const [friends, setFriends] = useState([]);
  const [showUserSearch, setShowUserSearch] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const currentUser = useSelector(state => state.auth.user);

  useEffect(() => {
    if (isOpen) {
      loadFriends();
      resetForm();
    }
  }, [isOpen]);

  const loadFriends = async () => {
    try {
      setLoading(true);
      setError(null);
      const friendsData = await userService.getFriends();
      setFriends(friendsData);
    } catch (error) {
      console.error('Ошибка загрузки друзей:', error);
      setError('Не удалось загрузить список друзей');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateChat = async () => {
    if (!chatName.trim()) {
      setError('Введите название чата');
      return;
    }

    if (selectedParticipants.length === 0) {
      setError('Выберите хотя бы одного участника');
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const chatData = {
        chatName: chatName.trim(),
        chatDescription: chatDescription.trim(),
        participantIds: selectedParticipants.map(p => p.id)
      };

      // Создаем чат через API сервис
      const newChat = await chatService.createGroupChat(chatData);

      // Уведомляем родительский компонент об успешном создании чата
      onChatCreated(newChat);

      // Закрываем модальное окно
      onClose();

      // Сбрасываем форму
      resetForm();

      console.log('Групповой чат успешно создан:', newChat);
    } catch (error) {
      console.error('Ошибка создания чата:', error);
      setError(error.response?.data?.message || 'Произошла ошибка при создании чата');
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setChatName('');
    setChatDescription('');
    setSelectedParticipants([]);
    setError(null);
  };

  const handleAddParticipants = (users) => {
    const newParticipants = [...selectedParticipants];
    users.forEach(user => {
      if (!newParticipants.find(p => p.id === user.id)) {
        newParticipants.push(user);
      }
    });
    setSelectedParticipants(newParticipants);
    setShowUserSearch(false);
  };

  const removeParticipant = (userId) => {
    setSelectedParticipants(selectedParticipants.filter(p => p.id !== userId));
  };

  const toggleFriendSelection = (friend) => {
    if (selectedParticipants.find(p => p.id === friend.id)) {
      removeParticipant(friend.id);
    } else {
      setSelectedParticipants([...selectedParticipants, friend]);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="shadow-xl rounded-2xl px-7 py-6 w-[440px] max-h-[90vh] flex flex-col relative border border-[#400d0d]" style={{backgroundImage:'linear-gradient(135deg,#400d0d 0%,#e07a5f 100%)',backgroundColor:'#f5e6d6',color:'#f5e6d6'}}>
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
        <h2 className="text-lg font-semibold mb-3 text-center" style={{color:'#f5e6d6'}}>Создание группового чата</h2>
        {error && (
          <div className="text-center text-sm mb-4" style={{color:'#B22222',background:'#FFF8F0',border:'1px solid #B22222',borderRadius:'10px',padding:'8px'}}>
            {error}
          </div>
        )}
        <div className="mb-4">
          <label className="block text-sm font-medium mb-1" style={{color:'#F5F5DC'}} htmlFor="chatName">
            Название чата*
          </label>
          <input
            type="text"
            id="chatName"
            value={chatName}
            onChange={(e) => setChatName(e.target.value)}
            className="w-full px-3 py-2 border border-[#B22222] rounded-lg bg-[#FFF8F0] text-[#B22222]"
            placeholder="Введите название чата"
            disabled={loading}
          />
        </div>
        <div className="mb-4">
          <label className="block text-sm font-medium mb-1" style={{color:'#F5F5DC'}} htmlFor="chatDescription">
            Описание чата
          </label>
          <textarea
            id="chatDescription"
            value={chatDescription}
            onChange={(e) => setChatDescription(e.target.value)}
            className="w-full px-3 py-2 border border-[#B22222] rounded-lg bg-[#FFF8F0] text-[#B22222]"
            placeholder="Введите описание чата (необязательно)"
            rows="3"
            disabled={loading}
          />
        </div>
        <div className="mb-4">
          <label className="block text-sm font-medium mb-1" style={{color:'#F5F5DC'}}>
            Участники*
          </label>
          <div className="flex flex-wrap gap-2 mb-2">
            {selectedParticipants.map(participant => (
              <div key={participant.id} className="flex items-center rounded-full px-3 py-1 border" style={{background:'#F5F5DC',border:'2px solid #B22222'}}>
                <span className="text-sm" style={{color:'#B22222'}}>{participant.username}</span>
                <button
                  onClick={() => removeParticipant(participant.id)}
                  className="ml-2 text-[#B22222] hover:text-[#8B1A1A] text-lg"
                  disabled={loading}
                  style={{background:'none',border:'none'}}
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
          <button
            type="button"
            onClick={() => setShowUserSearch(true)}
            className="px-5 py-2 rounded-lg font-semibold transition-colors w-full"
            style={{backgroundColor:'#B22222',color:'#F5F5DC',border:'none'}}
            disabled={loading}
          >
            Добавить участников
          </button>
        </div>
        <div className="flex justify-center gap-2 mt-6">
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2 rounded-lg font-semibold transition-colors"
            style={{backgroundColor:'#F5F5DC',color:'#B22222',border:'1px solid #B22222'}}
            disabled={loading}
          >
            Отмена
          </button>
          <button
            type="button"
            onClick={handleCreateChat}
            className="px-5 py-2 rounded-lg font-semibold transition-colors"
            style={{backgroundColor:'#B22222',color:'#F5F5DC',border:'none'}}
            disabled={loading}
          >
            {loading ? "Создание..." : "Создать чат"}
          </button>
        </div>
        {showUserSearch && (
          <UserSearchModal
            isOpen={showUserSearch}
            onClose={() => setShowUserSearch(false)}
            onUserSelect={handleAddParticipants}
            mode="multiple"
            title="Добавление участников чата"
            initialSelectedUsers={selectedParticipants}
          />
        )}
      </div>
    </div>
  );
};

export default CreateGroupChatModal;
