import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../services/api';

const RegisterForm = ({ setIsAuthenticated }) => {
    const [credentials, setCredentials] = useState({
        username: '',
        email: '',
        password: '',
        confirmPassword: '',
    });
    const [error, setError] = useState('');

    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (credentials.password !== credentials.confirmPassword) {
            setError('Пароли не совпадают');
            return;
        }

        try {
            const { confirmPassword, ...data } = credentials;
            const response = await authApi.post('/auth/register', data);
            if (response.status === 200) {
                // После успешной регистрации просто перенаправляем на страницу логина
                // без установки токена и флага аутентификации
                navigate('/login');
            }
        } catch (err) {
            setError(err.response?.data?.message || 'Ошибка регистрации');
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
                        Регистрация
                    </h2>
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
                                        border: `2px solid #B22222`,
                                    }}
                                    placeholder="Имя пользователя"
                                    value={credentials.username}
                                    onChange={(e) => setCredentials({ ...credentials, username: e.target.value })}
                                    onFocus={e => e.target.style.borderColor = '#B22222'}
                                    onBlur={e => e.target.style.borderColor = '#B22222'}
                                />
                            </div>
                            <div>
                                <label htmlFor="email" className="block text-sm font-medium mb-1" style={{ color: '#520808' }}>
                                    Email
                                </label>
                                <input
                                    id="email"
                                    name="email"
                                    type="email"
                                    autoComplete="email"
                                    required
                                    className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                    style={{
                                        background: '#F5DEB3',
                                        color: '#520808',
                                        border: `2px solid #B22222`,
                                    }}
                                    placeholder="Email"
                                    value={credentials.email}
                                    onChange={(e) => setCredentials({ ...credentials, email: e.target.value })}
                                    onFocus={e => e.target.style.borderColor = '#B22222'}
                                    onBlur={e => e.target.style.borderColor = '#B22222'}
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
                                    autoComplete="new-password"
                                    required
                                    className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                    style={{
                                        background: '#F5DEB3',
                                        color: '#520808',
                                        border: `2px solid #B22222`,
                                    }}
                                    placeholder="Пароль"
                                    value={credentials.password}
                                    onChange={(e) => setCredentials({ ...credentials, password: e.target.value })}
                                    onFocus={e => e.target.style.borderColor = '#B22222'}
                                    onBlur={e => e.target.style.borderColor = '#B22222'}
                                />
                            </div>
                            <div>
                                <label htmlFor="confirmPassword" className="block text-sm font-medium mb-1" style={{ color: '#520808' }}>
                                    Повторите пароль
                                </label>
                                <input
                                    id="confirmPassword"
                                    name="confirmPassword"
                                    type="password"
                                    autoComplete="new-password"
                                    required
                                    className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                    style={{
                                        background: '#F5DEB3',
                                        color: '#520808',
                                        border: `2px solid #B22222`,
                                    }}
                                    placeholder="Повторите пароль"
                                    value={credentials.confirmPassword}
                                    onChange={(e) => setCredentials({ ...credentials, confirmPassword: e.target.value })}
                                    onFocus={e => e.target.style.borderColor = '#B22222'}
                                    onBlur={e => e.target.style.borderColor = '#B22222'}
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
                                className="w-full flex justify-center py-2 px-4 text-sm font-bold rounded-lg transition-colors"
                                style={{
                                    background: '#B22222',
                                    color: '#fff',
                                    border: 'none',
                                    boxShadow: '0 2px 8px rgba(178,34,34,0.12)',
                                }}
                                onMouseOver={e => e.target.style.background = '#520808'}
                                onMouseOut={e => e.target.style.background = '#B22222'}
                            >
                                Зарегистрироваться
                            </button>
                        </div>
                    </form>
                    <p className="text-center mt-6 text-sm">
                        Уже есть аккаунт?{' '}
                        <a
                            href="/login"
                            className="font-medium underline"
                            style={{ color: '#B22222' }}
                        >
                            Войти
                        </a>
                    </p>
                </div>
            </div>
        </div>
    );
};

export default RegisterForm;