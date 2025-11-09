import React, { useState, useEffect } from 'react';
import { useSelector } from 'react-redux';
import Navigation from '../components/Navigation';
import EnhancedChatWindow from '../components/EnhancedChatWindow';
import chatService from '../services/chatService';
import CreateGroupChatModal from '../components/CreateGroupChatModal';
import UserSearchModal from '../components/UserSearchModal';

const ChatPage = () => {
    const [chats, setChats] = useState([]);
    const [selectedChat, setSelectedChat] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [showCreateGroupModal, setShowCreateGroupModal] = useState(false);
    const [showCreatePrivateChatModal, setShowCreatePrivateChatModal] = useState(false);
    const [unreadCounts, setUnreadCounts] = useState({});
    const { isAuthenticated, user } = useSelector(state => state.auth);

    const addfriend_icon = 'addfriend_icon.png';
    // Загружаем чаты при монтировании компонента
    useEffect(() => {
        if (isAuthenticated) {
            loadChats();
            setupWebSocketListeners();
        }
    }, [isAuthenticated]);

    // Настраиваем слушатели событий WebSocket
    const setupWebSocketListeners = () => {
        // Слушаем события чата (создание, обновление и т.д.)
        const unsubscribeChatEvents = chatService.onChatEvent(handleChatEvent);

        // Слушаем сообщения для обновления счетчиков непрочитанных сообщений
        const unsubscribeMessages = chatService.onMessage(handleNewMessage);

        return () => {
            unsubscribeChatEvents();
            unsubscribeMessages();
        };
    };

    // Обработчик событий чата
    const handleChatEvent = (event) => {
        console.log('Chat event received in ChatPage:', event);

        // Обновляем список чатов при различных событиях
        if (['CHAT_CREATED', 'PARTICIPANTS_ADDED', 'PARTICIPANT_REMOVED',
            'PARTICIPANT_LEFT', 'CREATOR_CHANGED'].includes(event.eventType)) {
            loadChats();
        }
    };

    // Обработчик новых сообщений для обновления счетчиков непрочитанных
    const handleNewMessage = (message) => {
        // Если это сообщение из чата, который сейчас не открыт
        if (message.chatId && (!selectedChat || message.chatId !== selectedChat.id)) {
            setUnreadCounts(prev => ({
                ...prev,
                [message.chatId]: (prev[message.chatId] || 0) + 1
            }));

            // Обновляем список чатов, чтобы последнее сообщение отображалось
            loadChats();
        }
    };

    // Загрузка списка чатов пользователя
    const loadChats = async () => {
        try {
            setLoading(true);
            setError(null);
            const userChats = await chatService.getUserChats();
            setChats(userChats);
            console.log('Loaded chats:', userChats);
        } catch (error) {
            console.error('Error loading chats:', error);
            setError('Не удалось загрузить список чатов');
        } finally {
            setLoading(false);
        }
    };

    // Обработчик выбора чата
    const handleSelectChat = (chat) => {
        setSelectedChat(chat);

        // Сбрасываем счетчик непрочитанных сообщений для этого чата
        setUnreadCounts(prev => ({
            ...prev,
            [chat.id]: 0
        }));
    };

    // Создание приватного чата
    const handleCreatePrivateChat = async (user) => {
        try {
            setLoading(true);
            const newChat = await chatService.createPrivateChat(user.id);
            await loadChats(); // Обновляем список чатов
            setSelectedChat(newChat); // Выбираем новый чат
            setShowCreatePrivateChatModal(false);
        } catch (error) {
            console.error('Error creating private chat:', error);
            setError('Не удалось создать приватный чат');
        } finally {
            setLoading(false);
        }
    };

    // Обработчик успешного создания группового чата
    const handleGroupChatCreated = async (newChat) => {
        await loadChats(); // Обновляем список чатов
        setSelectedChat(newChat); // Выбираем новый чат
    };

    // Получаем имя чата для отображения
    const getChatDisplayName = (chat) => {
        if (!chat) return '';

        if (chat.chatType === 'GROUP') {
            return chat.chatName;
        } else {
            // Для приватного чата показываем имя собеседника
            const otherParticipant = chat.participants?.find(p => p.id !== user?.id);
            return otherParticipant ? otherParticipant.username : 'Приватный чат';
        }
    };

    return (
        <div className="flex flex-col h-screen bg-gray-50">
            <Navigation />

            <div className="flex flex-1 overflow-hidden">
                {/* Боковая панель с чатами */}
                <div className="w-1/4 bg-white border-r border-gray-200 flex flex-col">
                    <div className="p-4 border-b border-gray-200 flex justify-between items-center">
                        <h2 className="text-xl font-semibold">Чаты</h2>
                        <div className="space-x-2">
                            <button
                                onClick={() => setShowCreatePrivateChatModal(true)}
                                className="bg-blue-500 hover:bg-blue-600 text-white p-2 rounded"
                                title="Новый приватный чат"
                            >
                                👤+
                            </button>
                            <button
                                onClick={() => setShowCreateGroupModal(true)}
                                className="bg-green-500 hover:bg-green-600 text-white p-2 rounded"
                                title="Новый групповой чат"
                            >
                                <img src={logout_icon} alt="" className="w-5 h-5 opacity-90 group-hover:opacity-100" draggable="false" />
                            </button>
                        </div>
                    </div>

                    {/* Список чатов */}
                    <div className="flex-1 overflow-y-auto">
                        {loading && chats.length === 0 ? (
                            <div className="flex items-center justify-center h-full">
                                <p className="text-gray-500">Загрузка чатов...</p>
                            </div>
                        ) : error ? (
                            <div className="p-4 text-center">
                                <p className="text-red-500">{error}</p>
                                <button
                                    onClick={loadChats}
                                    className="mt-2 text-blue-500 hover:text-blue-700"
                                >
                                    Попробовать снова
                                </button>
                            </div>
                        ) : chats.length === 0 ? (
                            <div className="p-4 text-center">
                                <p className="text-gray-500">У вас пока нет чатов</p>
                                <p className="text-gray-500 text-sm mt-1">Создайте новый чат, нажав на кнопки выше</p>
                            </div>
                        ) : (
                            <div className="divide-y divide-gray-100">
                                {chats.map(chat => (
                                    <div
                                        key={chat.id}
                                        className={`p-4 hover:bg-gray-50 cursor-pointer ${
                                            selectedChat && selectedChat.id === chat.id ? 'bg-blue-50' : ''
                                        }`}
                                        onClick={() => handleSelectChat(chat)}
                                    >
                                        <div className="flex justify-between items-start">
                                            <div>
                                                <h3 className="font-medium">
                                                    {getChatDisplayName(chat)}
                                                    {chat.chatType === 'GROUP' && (
                                                        <span className="text-xs text-gray-500 ml-2">
                                                            ({chat.participants?.length || 0})
                                                        </span>
                                                    )}
                                                </h3>
                                                {chat.lastMessage && (
                                                    <p className="text-sm text-gray-500 truncate mt-1" style={{ maxWidth: '240px' }}>
                                                        {chat.lastMessage.messageType === 'SYSTEM' ? (
                                                            <i>{chat.lastMessage.content}</i>
                                                        ) : (
                                                            <>
                                                                {chat.lastMessage.sender?.username === user?.username
                                                                    ? 'Вы: '
                                                                    : chat.lastMessage.sender?.username
                                                                        ? `${chat.lastMessage.sender.username}: `
                                                                        : ''
                                                                }
                                                                {chat.lastMessage.content}
                                                            </>
                                                        )}
                                                    </p>
                                                )}
                                            </div>
                                            <div className="flex flex-col items-end">
                                                {chat.lastMessageAt && (
                                                    <span className="text-xs text-gray-500">
                                                        {new Date(chat.lastMessageAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                                    </span>
                                                )}
                                                {unreadCounts[chat.id] > 0 && (
                                                    <span className="bg-red-500 text-white text-xs rounded-full px-2 py-1 mt-1">
                                                        {unreadCounts[chat.id]}
                                                    </span>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>

                {/* Окно чата */}
                <div className="flex-1 flex flex-col">
                    <EnhancedChatWindow
                        selectedChat={selectedChat}
                        onChatUpdate={(updatedChat) => {
                            if (!updatedChat) {
                                setSelectedChat(null);
                                loadChats();
                            } else {
                                // ИСПРАВЛЕНИЕ: Обновляем выбранный чат с актуальными данными
                                setSelectedChat(updatedChat);
                                // Обновляем чат в списке, сохраняя информацию о статусах участников
                                setChats(prev =>
                                    prev.map(c => c.id === updatedChat.id ? { ...c, ...updatedChat } : c)
                                );
                            }
                        }}
                    />
                </div>
            </div>

            {/* Модальное окно создания группового чата */}
            <CreateGroupChatModal
                isOpen={showCreateGroupModal}
                onClose={() => setShowCreateGroupModal(false)}
                onChatCreated={handleGroupChatCreated}
            />

            {/* Модальное окно для выбора пользователя для приватного чата */}
            <UserSearchModal
                isOpen={showCreatePrivateChatModal}
                onClose={() => setShowCreatePrivateChatModal(false)}
                onUserSelect={(users) => {
                    if (users && users.length > 0) {
                        handleCreatePrivateChat(users[0]);
                    }
                }}
                mode="single"
                title="Начать чат с пользователем"
            />
        </div>
    );
};

export default ChatPage;