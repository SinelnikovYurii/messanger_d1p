import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { authApi } from '../services/api';
import authService from '../services/authService';
import { setAuthFromService } from '../store/slices/authSlice';
import keyBackupService from '../services/keyBackupService';
import { generateX3DHKeys, exportX3DHBundleWithSignature } from '../utils/crypto';
import userService from '../services/userService';

const LoginForm = ({ setIsAuthenticated }) => {
    const [credentials, setCredentials] = useState({ username: '', password: '' });
    const [kekPassword, setKekPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    // 'login' | 'kek' | 'kek_new' — шаг формы
    const [step, setStep] = useState('login');
    const [pendingAuth, setPendingAuth] = useState(null); // { token, userData }
    const [kekError, setKekError] = useState('');
    const [kekLoading, setKekLoading] = useState(false);

    const navigate = useNavigate();
    const dispatch = useDispatch();

    // Шаг 1 — обычная авторизация
    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            const response = await authApi.post('/auth/login', credentials);

            if (response.status === 200 && response.data?.token) {
                const responseData = response.data;

                // Обрабатываем данные пользователя
                let userData;
                if (responseData.user && typeof responseData.user === 'object') {
                    userData = responseData.user;
                } else if (responseData.userId || responseData.username) {
                    userData = {
                        ...(responseData.userId && { id: responseData.userId }),
                        ...(responseData.username && { username: responseData.username }),
                        ...(responseData.email && { email: responseData.email })
                    };
                } else {
                    userData = { username: credentials.username };
                }

                // Временно сохраняем авторизацию чтобы запросы к API работали
                authService.setAuth(responseData.token, userData);
                dispatch(setAuthFromService());

                // Проверяем, есть ли бекап ключей на сервере
                const hasBackup = await keyBackupService.hasServerBackup();
                setPendingAuth({ token: responseData.token, userData });

                if (hasBackup) {
                    // Предлагаем ввести KEK-пароль для восстановления ключей
                    setStep('kek');
                } else {
                    // Первый вход на этом устройстве и нет бекапа — предлагаем создать KEK-пароль
                    setStep('kek_new');
                }
            } else {
                setError('Ошибка входа. Проверьте логин и пароль.');
            }
        } catch (err) {
            // Очищаем временную авторизацию при ошибке входа
            authService.clearAuth();

            let errorMessage = 'Ошибка входа. Проверьте логин и пароль.';
            if (err.response?.status === 401) errorMessage = 'Неверный логин или пароль';
            else if (err.response?.status === 403) errorMessage = 'Доступ запрещен';
            else if (err.response?.status >= 500) errorMessage = 'Ошибка сервера. Попробуйте позже';
            else if (!err.response) errorMessage = 'Ошибка соединения. Проверьте интернет-подключение';
            else if (err.response?.data?.error || err.response?.data?.message)
                errorMessage = err.response.data.error || err.response.data.message;

            setError(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    // Шаг 2а — восстановление ключей существующим KEK-паролем
    const handleKekSubmit = async (e) => {
        e.preventDefault();
        setKekError('');
        setKekLoading(true);

        try {
            const restored = await keyBackupService.downloadAndRestoreKeys(kekPassword);
            if (!restored) {
                setKekError('Не удалось загрузить бекап ключей с сервера');
                return;
            }
            // Сохраняем KEK-пароль в sessionStorage для автоматических обновлений
            sessionStorage.setItem('kek_password', kekPassword);
            // Ключи восстановлены — завершаем вход
            finishLogin();
        } catch (err) {
            if (err.message?.includes('Неверный пароль')) {
                setKekError('Неверный пароль шифрования ключей');
            } else {
                setKekError(err.message || 'Ошибка расшифровки ключей');
            }
        } finally {
            setKekLoading(false);
        }
    };

    // Шаг 2б — создание нового KEK-пароля (первый вход или нет бекапа)
    const handleKekNewSubmit = async (e) => {
        e.preventDefault();
        setKekError('');
        setKekLoading(true);

        try {
            const { userData } = pendingAuth;
            const userId = userData?.id;

            if (!userId) {
                setKekError('Не удалось определить ID пользователя');
                return;
            }

            // Проверяем, есть ли уже локальные ключи
            const hasLocal = keyBackupService.hasLocalKeys();

            if (!hasLocal) {
                // Генерируем новые X3DH ключи
                const x3dhKeys = await generateX3DHKeys();
                const bundle = await exportX3DHBundleWithSignature(x3dhKeys);

                // Сохраняем приватный ключ в localStorage
                const privateJwk = await window.crypto.subtle.exportKey('jwk', x3dhKeys.identityKeyPair.privateKey);
                localStorage.setItem('e2ee_privateKey', JSON.stringify(privateJwk));

                // Сохраняем все ключи X3DH
                const x3dhKeysData = {
                    identityKeyPair: {
                        privateKey: await window.crypto.subtle.exportKey('jwk', x3dhKeys.identityKeyPair.privateKey),
                        publicKey: await window.crypto.subtle.exportKey('jwk', x3dhKeys.identityKeyPair.publicKey),
                    },
                    signedPreKeyPair: {
                        privateKey: await window.crypto.subtle.exportKey('jwk', x3dhKeys.signedPreKeyPair.privateKey),
                        publicKey: await window.crypto.subtle.exportKey('jwk', x3dhKeys.signedPreKeyPair.publicKey),
                    },
                };
                localStorage.setItem(`x3dh_keys_${userId}`, JSON.stringify(x3dhKeysData));

                // Отправляем публичные ключи на сервер
                await userService.savePreKeyBundle(
                    userId,
                    bundle.identityKey,
                    bundle.signedPreKey,
                    bundle.oneTimePreKeys,
                    bundle.signedPreKeySignature
                );
            }

                // Шифруем и сохраняем бекап на сервере
                await keyBackupService.uploadKeyBackup(kekPassword);
                // Сохраняем KEK-пароль в sessionStorage для автоматических обновлений
                sessionStorage.setItem('kek_password', kekPassword);
                finishLogin();
        } catch (err) {
            console.error('[Login] Error creating KEK backup:', err);
            setKekError(err.message || 'Ошибка создания бекапа ключей');
        } finally {
            setKekLoading(false);
        }
    };

    // Пропустить ввод KEK (без резервного копирования ключей)
    const handleSkipKek = () => {
        finishLogin();
    };

    const finishLogin = () => {
        if (setIsAuthenticated) setIsAuthenticated(true);
        setTimeout(() => navigate('/chat'), 0);
    };

    const inputStyle = {
        background: '#F5DEB3',
        color: '#520808',
        border: `2px solid #B22222`,
    };

    // ── Шаг KEK: восстановление ключей ────────────────────────────────────
    if (step === 'kek') {
        return (
            <div className="min-h-screen flex items-center justify-center"
                style={{ backgroundImage: "url('/chat_background_n.png')", backgroundSize: '400px', backgroundRepeat: 'repeat' }}>
                <div className="max-w-md w-full">
                    <div className="rounded-xl shadow-2xl p-8" style={{ background: '#FFDAB9' }}>
                        <h2 className="text-center text-2xl font-bold mb-2" style={{ color: '#520808' }}>
                            🔐 Ввод пароля ключей
                        </h2>
                        <p className="text-center text-sm mb-6" style={{ color: '#B22222' }}>
                            На сервере найден зашифрованный бекап ваших ключей E2EE.<br />
                            Введите пароль шифрования ключей для их восстановления.
                        </p>
                        <form className="space-y-5" onSubmit={handleKekSubmit}>
                            <div>
                                <label className="block text-sm font-medium mb-1" style={{ color: '#520808' }}>
                                    Пароль шифрования ключей
                                </label>
                                <input
                                    type="password"
                                    required
                                    className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                    style={inputStyle}
                                    placeholder="Пароль от ключей шифрования"
                                    value={kekPassword}
                                    onChange={e => setKekPassword(e.target.value)}
                                    disabled={kekLoading}
                                    autoFocus
                                />
                                <p className="text-xs mt-1" style={{ color: '#B22222' }}>
                                    Это не пароль от аккаунта. Сервер никогда не видит этот пароль.
                                </p>
                            </div>
                            {kekError && (
                                <div className="px-4 py-3 rounded-lg text-sm border"
                                    style={{ background: '#FFDAB9', color: '#B22222', borderColor: '#B22222' }}>
                                    {kekError}
                                </div>
                            )}
                            <button type="submit" disabled={kekLoading}
                                className="w-full flex justify-center py-2 px-4 text-sm font-bold rounded-lg"
                                style={{ background: '#B22222', color: '#fff' }}>
                                {kekLoading ? 'Расшифровка...' : 'Восстановить ключи'}
                            </button>
                            <button type="button" onClick={handleSkipKek}
                                className="w-full text-center text-sm underline mt-2"
                                style={{ color: '#B22222', background: 'none', border: 'none' }}>
                                Пропустить (история чатов будет недоступна)
                            </button>
                        </form>
                    </div>
                </div>
            </div>
        );
    }

    // ── Шаг KEK_NEW: создание нового пароля ключей ────────────────────────
    if (step === 'kek_new') {
        return (
            <div className="min-h-screen flex items-center justify-center"
                style={{ backgroundImage: "url('/chat_background_n.png')", backgroundSize: '400px', backgroundRepeat: 'repeat' }}>
                <div className="max-w-md w-full">
                    <div className="rounded-xl shadow-2xl p-8" style={{ background: '#FFDAB9' }}>
                        <h2 className="text-center text-2xl font-bold mb-2" style={{ color: '#520808' }}>
                            🔑 Защита ключей шифрования
                        </h2>
                        <p className="text-center text-sm mb-2" style={{ color: '#B22222' }}>
                            Придумайте пароль для защиты ваших ключей E2EE.<br />
                            Этот пароль шифрует ключи перед отправкой на сервер.
                        </p>
                        <div className="mb-4 px-3 py-2 rounded-lg text-xs" style={{ background: '#F5DEB3', color: '#520808', border: '1px solid #B22222' }}>
                            ⚠️ <strong>Важно!</strong> Сервер не знает этот пароль. Если вы его забудете — ключи восстановить невозможно и история чатов будет потеряна.
                        </div>
                        <form className="space-y-5" onSubmit={handleKekNewSubmit}>
                            <div>
                                <label className="block text-sm font-medium mb-1" style={{ color: '#520808' }}>
                                    Пароль шифрования ключей
                                </label>
                                <input
                                    type="password"
                                    required
                                    minLength={8}
                                    className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                    style={inputStyle}
                                    placeholder="Минимум 8 символов"
                                    value={kekPassword}
                                    onChange={e => setKekPassword(e.target.value)}
                                    disabled={kekLoading}
                                    autoFocus
                                />
                            </div>
                            {kekError && (
                                <div className="px-4 py-3 rounded-lg text-sm border"
                                    style={{ background: '#FFDAB9', color: '#B22222', borderColor: '#B22222' }}>
                                    {kekError}
                                </div>
                            )}
                            <button type="submit" disabled={kekLoading || kekPassword.length < 8}
                                className="w-full flex justify-center py-2 px-4 text-sm font-bold rounded-lg"
                                style={{ background: kekPassword.length >= 8 ? '#B22222' : '#ccc', color: '#fff' }}>
                                {kekLoading ? 'Сохранение...' : 'Создать бекап ключей'}
                            </button>
                            <button type="button" onClick={handleSkipKek}
                                className="w-full text-center text-sm underline mt-2"
                                style={{ color: '#B22222', background: 'none', border: 'none' }}>
                                Пропустить (ключи не будут сохранены на сервере)
                            </button>
                        </form>
                    </div>
                </div>
            </div>
        );
    }

    // ── Шаг 1: обычная форма входа ────────────────────────────────────────
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
                <div className="rounded-xl shadow-2xl p-8" style={{ background: '#FFDAB9' }}>
                    <h2 className="text-center text-2xl font-bold mb-2" style={{ color: '#520808' }}>
                        Вход в аккаунт
                    </h2>
                    <p className="text-center text-sm mb-6" style={{ color: '#B22222' }}>
                        Или{' '}
                        <button onClick={() => navigate('/register')}
                            className="font-medium underline" style={{ color: '#B22222' }} type="button">
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
                                    id="username" name="username" type="text" autoComplete="username" required
                                    className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                    style={{ ...inputStyle, border: `2px solid ${loading ? '#FFDAB9' : '#B22222'}` }}
                                    placeholder="Имя пользователя"
                                    value={credentials.username}
                                    onChange={(e) => setCredentials({ ...credentials, username: e.target.value })}
                                    disabled={loading}
                                />
                            </div>
                            <div>
                                <label htmlFor="password" className="block text-sm font-medium mb-1" style={{ color: '#520808' }}>
                                    Пароль
                                </label>
                                <input
                                    id="password" name="password" type="password" autoComplete="current-password" required
                                    className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                    style={{ ...inputStyle, border: `2px solid ${loading ? '#FFDAB9' : '#B22222'}` }}
                                    placeholder="Пароль"
                                    value={credentials.password}
                                    onChange={(e) => setCredentials({ ...credentials, password: e.target.value })}
                                    disabled={loading}
                                />
                            </div>
                        </div>

                        {error && (
                            <div className="px-4 py-3 rounded-lg text-sm shadow-sm border mb-2"
                                style={{ background: '#FFDAB9', color: '#B22222', borderColor: '#B22222' }}>
                                {error}
                            </div>
                        )}

                        <div>
                            <button type="submit" disabled={loading}
                                className="w-full flex justify-center py-2 px-4 text-sm font-bold rounded-lg transition-colors"
                                style={{ background: '#B22222', color: '#fff', border: 'none', boxShadow: '0 2px 8px rgba(178,34,34,0.12)' }}
                                onMouseOver={e => { if (!loading) e.currentTarget.style.background = '#520808'; }}
                                onMouseOut={e => { e.currentTarget.style.background = '#B22222'; }}>
                                {loading ? (
                                    <><div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>Вход...</>
                                ) : 'Войти'}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
};

export default LoginForm;
