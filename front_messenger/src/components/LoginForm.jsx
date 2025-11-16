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
        <div
            className="min-h-screen flex items-center justify-center"
            style={{
                backgroundImage: "url('/chat_background_n.png')",
                backgroundSize: '400px',
                backgroundRepeat: 'repeat',
                backgroundPosition: 'center',
            }}
        >
            <div className="max-w-md w-full">
                <div
                    className="rounded-xl shadow-2xl p-8"
                    style={{ background: '#FFDAB9' }}
                >
                    <h2
                        className="text-center text-2xl font-bold mb-2"
                        style={{ color: '#520808' }}
                    >
                        Вход в аккаунт
                    </h2>
                    <p className="text-center text-sm mb-6" style={{ color: '#B22222' }}>
                        Или{' '}
                        <button
                            onClick={() => navigate('/register')}
                            className="font-medium underline"
                            style={{ color: '#B22222' }}
                            type="button"
                        >
                            создайте новый аккаунт
                        </button>
                    </p>
                    <form className="space-y-5" onSubmit={handleSubmit}>
                        <div className="space-y-4">
                            <div>
                                <label htmlFor="username" className="block text-sm font-medium mb-1" style={{ color: '#520808' }}>
                                    Имя пользователя
                                </label>
                                <input
                                    id="username"
                                    name="username"
                                    type="text"
                                    autoComplete="username"
                                    required
                                    className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                    style={{
                                        background: '#F5DEB3',
                                        color: '#520808',
                                        border: `2px solid ${loading ? '#FFDAB9' : '#B22222'}`,
                                    }}
                                    placeholder="Имя пользователя"
                                    value={credentials.username}
                                    onChange={(e) => setCredentials({ ...credentials, username: e.target.value })}
                                    disabled={loading}
                                    onFocus={e => e.target.style.borderColor = '#B22222'}
                                    onBlur={e => e.target.style.borderColor = loading ? '#FFDAB9' : '#B22222'}
                                />
                            </div>
                            <div>
                                <label htmlFor="password" className="block text-sm font-medium mb-1" style={{ color: '#520808' }}>
                                    Пароль
                                </label>
                                <input
                                    id="password"
                                    name="password"
                                    type="password"
                                    autoComplete="current-password"
                                    required
                                    className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                    style={{
                                        background: '#F5DEB3',
                                        color: '#520808',
                                        border: `2px solid ${loading ? '#FFDAB9' : '#B22222'}`,
                                    }}
                                    placeholder="Пароль"
                                    value={credentials.password}
                                    onChange={(e) => setCredentials({ ...credentials, password: e.target.value })}
                                    disabled={loading}
                                    onFocus={e => e.target.style.borderColor = '#B22222'}
                                    onBlur={e => e.target.style.borderColor = loading ? '#FFDAB9' : '#B22222'}
                                />
                            </div>
                        </div>

                        {error && (
                            <div
                                className="px-4 py-3 rounded-lg text-sm shadow-sm border mb-2"
                                style={{ background: '#FFDAB9', color: '#B22222', borderColor: '#B22222' }}
                            >
                                {error}
                            </div>
                        )}

                        <div>
                            <button
                                type="submit"
                                disabled={loading}
                                className="w-full flex justify-center py-2 px-4 text-sm font-bold rounded-lg transition-colors"
                                style={{
                                    background: loading ? '#B22222' : '#B22222',
                                    color: '#fff',
                                    border: 'none',
                                    boxShadow: '0 2px 8px rgba(178,34,34,0.12)',
                                }}
                                onMouseOver={e => e.target.style.background = '#520808'}
                                onMouseOut={e => e.target.style.background = '#B22222'}
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
        </div>
    );
};

export default LoginForm;
