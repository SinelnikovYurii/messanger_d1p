import React, { useEffect } from 'react';
import FriendsManager from '../components/FriendsManager';
import Navigation from '../components/Navigation';
import { useNavigate } from 'react-router-dom';
import chatService from '../services/chatService';

const FriendsPage = () => {
  const navigate = useNavigate();

  // Инициализация WebSocket соединения при монтировании компонента
  useEffect(() => {
    const initializeWebSocket = async () => {
      const token = localStorage.getItem('token');
      if (!token) {
        console.log('No token available for WebSocket connection');
        return;
      }

      // Подключаемся только если еще не подключены
      if (!chatService.isConnected()) {
        try {
          console.log('Initializing WebSocket connection from FriendsPage...');
          await chatService.connect(token);
          console.log('WebSocket connected successfully');
        } catch (error) {
          console.error('WebSocket connection failed:', error);
        }
      } else {
        console.log('WebSocket already connected');
      }
    };

    initializeWebSocket();
  }, []);

  const handleStartChat = async (user) => {
    try {
      // Создаем приватный чат с пользователем
      const chat = await chatService.createPrivateChat(user.id);
      // Переходим на страницу чата
      navigate('/chat', {
        state: {
          selectedChat: chat,
          user: user
        }
      });
    } catch (error) {
      console.error('Ошибка создания чата:', error);
    }
  };

  return (
    <div className="flex flex-col h-screen">
      <Navigation />
      <div className="flex-1 bg-gray-50 overflow-auto">
        <FriendsManager onStartChat={handleStartChat} />
      </div>
    </div>
  );
};

export default FriendsPage;
