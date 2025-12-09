import React, { useEffect, useState } from 'react';
import userService from '../services/userService';
import authService from '../services/authService';
import chatService from '../services/chatService';
import { ErrorHandler } from '../utils/errorHandler';

const UserList = () => {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        let isMounted = true; // Флаг для предотвращения обновления после размонтирования

        const fetchUsers = async () => {
            // Проверяем готовность к API запросам через AuthService
            if (!authService.isReadyForApiCalls()) {
                console.log('UserList: Not ready for API calls, skipping fetch');
                if (isMounted) {
                    setLoading(false);
                    setError('Ожидание авторизации...');
                }
                return;
            }

            try {
                if (isMounted) {
                    setLoading(true);
                    setError(null);
                }

                const currentUser = authService.getUserData();
                const usersData = await userService.getAllUsers();

                if (isMounted) {
                    // Фильтруем текущего пользователя из списка
                    const filteredUsers = usersData.filter(user =>
                        currentUser && user.id !== currentUser.id
                    );
                    setUsers(filteredUsers);
                    setError(null);
                }
            } catch (error) {
                console.error('Failed to fetch users:', error);

                if (isMounted) {
                    // Используем ErrorHandler для определения типа ошибки
                    const errorMessage = ErrorHandler.getErrorMessage(error);

                    // Не показываем ошибку если это 401 - AuthService обработает
                    if (error.response?.status !== 401) {
                        setError(errorMessage);
                    }
                }
            } finally {
                if (isMounted) {
                    setLoading(false);
                }
            }
        };

        // Начальная загрузка с небольшой задержкой для стабилизации приложения
        const initialFetch = () => {
            setTimeout(() => {
                if (isMounted) {
                    fetchUsers();
                }
            }, 100);
        };

        initialFetch();

        // Подписываемся на изменения авторизации
        const unsubscribeAuth = authService.addListener((isAuthenticated) => {
            if (isAuthenticated && isMounted) {
                console.log('UserList: Auth status changed to authenticated, fetching users');
                fetchUsers();
            } else if (!isAuthenticated && isMounted) {
                console.log('UserList: Auth status changed to unauthenticated, clearing users');
                setUsers([]);
                setError(null);
                setLoading(false);
            }
        });

        // Периодическое обновление списка пользователей
        const interval = setInterval(() => {
            if (isMounted && authService.isReadyForApiCalls()) {
                fetchUsers();
            }
        }, 30000);

        // Подписка на события онлайн/оффлайн через WebSocket
        const handleUserStatus = (message) => {
            if (message.type === 'USER_ONLINE' || message.type === 'USER_OFFLINE') {
                setUsers(prevUsers => prevUsers.map(user =>
                    user.id === message.userId ? { ...user, isOnline: message.type === 'USER_ONLINE' } : user
                ));
            }
        };
        const unsubscribeMessage = chatService.onMessage(handleUserStatus);

        // Подписка на reconnect WebSocket
        const reconnectHandler = {
            onConnect: () => {
                fetchUsers();
            }
        };
        chatService.onConnection(reconnectHandler);

        return () => {
            isMounted = false;
            unsubscribeAuth();
            clearInterval(interval);
            unsubscribeMessage();
            chatService.removeConnectionHandler(reconnectHandler);
        };
    }, []);

    if (loading) {
        return (
            <div className="w-64 bg-white border-r h-full overflow-y-auto">
                <div className="p-4 border-b">
                    <h2 className="text-lg font-semibold">Пользователи онлайн</h2>
                </div>
                <div className="flex items-center justify-center h-32">
                    <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600"></div>
                </div>
            </div>
        );
    }

    return (
        <div className="w-64 bg-white border-r h-full overflow-y-auto">
            <div className="p-4 border-b">
                <h2 className="text-lg font-semibold">Пользователи онлайн</h2>
                <p className="text-sm text-gray-500">{users.length} пользователей</p>
            </div>
            <div className="p-2">
                {error && (
                    <div className="p-4 text-center text-red-500 text-sm">
                        {error}
                    </div>
                )}

                {users.map((user) => (
                    <div
                        key={user.id}
                        className="p-3 hover:bg-gray-100 rounded cursor-pointer flex items-center transition-colors"
                    >
                        <div className={`w-3 h-3 rounded-full mr-3 ${user.isOnline ? 'bg-green-500' : 'bg-gray-400'}`}></div>
                        <div className="flex-1">
                            <div className="font-medium">{user.username}</div>
                            {user.email && (
                                <div className="text-sm text-gray-500">{user.email}</div>
                            )}
                            {(user.firstName || user.lastName) && (
                                <div className="text-xs text-gray-400">
                                    {user.firstName} {user.lastName}
                                </div>
                            )}
                        </div>
                        {user.isOnline && (
                            <div className="text-xs text-green-600 font-medium">В сети</div>
                        )}
                    </div>
                ))}

                {users.length === 0 && !error && (
                    <div className="p-4 text-center text-gray-500">
                        Нет пользователей онлайн
                    </div>
                )}
            </div>
        </div>
    );
};

export default UserList;