import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { authApi } from '../services/api';
import authService from '../services/authService';
import { setAuthFromService } from '../store/slices/authSlice';

const LoginForm = ({ setIsAuthenticated }) => {
    const [credentials, setCredentials] = useState({ username: '', password: '' });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const navigate = useNavigate();
    const dispatch = useDispatch();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        console.log('Attempting login with credentials:', { username: credentials.username, password: '***' });

        try {
            const response = await authApi.post('/auth/login', credentials);
            console.log('Login response status:', response.status);
            console.log('Full server response:', response.data);

            if (response.status === 200 && response.data) {
                const responseData = response.data;

                // Проверяем наличие токена
                if (!responseData.token) {
                    throw new Error('Токен не получен от сервера');
                }

                // Обрабатываем данные пользователя
                let userData = null;

                if (responseData.user && typeof responseData.user === 'object') {
                    // Полный объект пользователя от нового API
                    userData = responseData.user;
                    console.log('Using full user object from server:', userData);
                } else if (responseData.userId || responseData.username) {
                    // Старый формат API - создаем объект пользователя
                    userData = {
                        ...(responseData.userId && { id: responseData.userId }),
                        ...(responseData.username && { username: responseData.username }),
                        ...(responseData.email && { email: responseData.email })
                    };
                    console.log('Created user object from legacy format:', userData);
                } else {
                    // Крайний случай - создаем минимальный объект из credentials
                    userData = {
                        username: credentials.username
                    };
                    console.log('Created minimal user object from credentials:', userData);
                }

                // Используем AuthService для сохранения данных
                authService.setAuth(responseData.token, userData);
                console.log('Authentication data saved via AuthService');

                // ИСПРАВЛЕНИЕ: Синхронизируем Redux store с authService
                dispatch(setAuthFromService());
                console.log('Redux store synchronized with authService');

                // Обновляем состояние напрямую для немедленной синхронизации
                if (setIsAuthenticated) {
                    setIsAuthenticated(true);
                }

                // Используем setTimeout чтобы дать React время обновить состояние перед навигацией
                setTimeout(() => {
                    console.log('Authentication successful, navigating to chat');
                    navigate('/chat');
                }, 0);
            } else {
                throw new Error('Неверный ответ сервера');
            }
        } catch (err) {
            console.error('Login error details:', err);
            console.error('Error response:', err.response?.data);

            // Определяем тип ошибки для пользователя
            let errorMessage = 'Ошибка входа. Проверьте логин и пароль.';

            if (err.response?.status === 401) {
                errorMessage = 'Неверный логин или пароль';
            } else if (err.response?.status === 403) {
                errorMessage = 'Доступ запрещен. Обратитесь к администратору';
            } else if (err.response?.status >= 500) {
                errorMessage = 'Ошибка сервера. Попробуйте позже';
            } else if (err.code === 'NETWORK_ERROR' || !err.response) {
                errorMessage = 'Ошибка соединения. Проверьте интернет-подключение';
            } else if (err.response?.data?.error || err.response?.data?.message) {
                errorMessage = err.response.data.error || err.response.data.message;
            }

            setError(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-md w-full space-y-8">
                <div>
                    <h2 className="mt-6 text-center text-3xl font-extrabold text-gray-900">
                        Вход в аккаунт
                    </h2>
                    <p className="mt-2 text-center text-sm text-gray-600">
                        Или{' '}
                        <button
                            onClick={() => navigate('/register')}
                            className="font-medium text-blue-600 hover:text-blue-500"
                        >
                            создайте новый аккаунт
                        </button>
                    </p>
                </div>
                <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
                    <div className="rounded-md shadow-sm -space-y-px">
                        <div>
                            <label htmlFor="username" className="sr-only">
                                Имя пользователя
                            </label>
                            <input
                                id="username"
                                name="username"
                                type="text"
                                autoComplete="username"
                                required
                                className="appearance-none rounded-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 rounded-t-md focus:outline-none focus:ring-blue-500 focus:border-blue-500 focus:z-10 sm:text-sm"
                                placeholder="Имя пользователя"
                                value={credentials.username}
                                onChange={(e) => setCredentials({...credentials, username: e.target.value})}
                                disabled={loading}
                            />
                        </div>
                        <div>
                            <label htmlFor="password" className="sr-only">
                                Пароль
                            </label>
                            <input
                                id="password"
                                name="password"
                                type="password"
                                autoComplete="current-password"
                                required
                                className="appearance-none rounded-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 rounded-b-md focus:outline-none focus:ring-blue-500 focus:border-blue-500 focus:z-10 sm:text-sm"
                                placeholder="Пароль"
                                value={credentials.password}
                                onChange={(e) => setCredentials({...credentials, password: e.target.value})}
                                disabled={loading}
                            />
                        </div>
                    </div>

                    {error && (
                        <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded">
                            {error}
                        </div>
                    )}

                    <div>
                        <button
                            type="submit"
                            disabled={loading}
                            className="group relative w-full flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {loading ? (
                                <>
                                    <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                                    Вход...
                                </>
                            ) : (
                                'Войти'
                            )}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default LoginForm;
