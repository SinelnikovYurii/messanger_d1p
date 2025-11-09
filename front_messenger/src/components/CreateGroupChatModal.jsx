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
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 className="text-xl font-semibold mb-4">Создание группового чата</h2>

        {error && (
          <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
            {error}
          </div>
        )}

        <div className="mb-4">
          <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="chatName">
            Название чата*
          </label>
          <input
            type="text"
            id="chatName"
            value={chatName}
            onChange={(e) => setChatName(e.target.value)}
            className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
            placeholder="Введите название чата"
            disabled={loading}
          />
        </div>

        <div className="mb-4">
          <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="chatDescription">
            Описание чата
          </label>
          <textarea
            id="chatDescription"
            value={chatDescription}
            onChange={(e) => setChatDescription(e.target.value)}
            className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
            placeholder="Введите описание чата (необязательно)"
            rows="3"
            disabled={loading}
          />
        </div>

        <div className="mb-4">
          <label className="block text-gray-700 text-sm font-bold mb-2">
            Участники*
          </label>
          <div className="flex flex-wrap gap-2 mb-2">
            {selectedParticipants.map(participant => (
              <div key={participant.id} className="bg-blue-100 flex items-center rounded-full px-3 py-1">
                <span className="text-sm text-blue-800">{participant.username}</span>
                <button
                  onClick={() => removeParticipant(participant.id)}
                  className="ml-2 text-blue-600 hover:text-blue-800"
                  disabled={loading}
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
          <button
            type="button"
            onClick={() => setShowUserSearch(true)}
            className="bg-blue-500 hover:bg-blue-600 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline w-full"
            disabled={loading}
          >
            Добавить участников
          </button>
        </div>

        <div className="flex justify-end gap-2 mt-6">
          <button
            type="button"
            onClick={onClose}
            className="bg-gray-300 hover:bg-gray-400 text-gray-800 font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline"
            disabled={loading}
          >
            Отмена
          </button>
          <button
            type="button"
            onClick={handleCreateChat}
            className="bg-green-500 hover:bg-green-600 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline"
            disabled={loading}
          >
            {loading ? "Создание..." : "Создать чат"}
          </button>
        </div>
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
  );
};

export default CreateGroupChatModal;
