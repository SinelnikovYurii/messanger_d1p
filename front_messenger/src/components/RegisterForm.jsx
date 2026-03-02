import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import authService from '../services/authService';
import keyBackupService from '../services/keyBackupService';
import { generateX3DHKeys, exportX3DHBundleWithSignature } from '../utils/crypto';
import userService from '../services/userService';

const RegisterForm = ({ setIsAuthenticated }) => {
    const [credentials, setCredentials] = useState({
        username: '',
        email: '',
        password: '',
        confirmPassword: '',
        kekPassword: '',
        kekPasswordConfirm: '',
    });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (credentials.password !== credentials.confirmPassword) {
            setError('Пароли от аккаунта не совпадают');
            return;
        }

        if (credentials.kekPassword.length < 8) {
            setError('Пароль шифрования ключей должен быть не менее 8 символов');
            return;
        }

        if (credentials.kekPassword !== credentials.kekPasswordConfirm) {
            setError('Пароли шифрования ключей не совпадают');
            return;
        }

        setLoading(true);

        try {
            const { confirmPassword, kekPassword, kekPasswordConfirm, ...data } = credentials;

            // 1. Регистрация аккаунта
            const response = await api.post('/auth/register', data);


            // Если сервер вернул токен — сразу входим и генерируем ключи
            if (response.data?.token && response.data?.user) {
                const { token, user } = response.data;

                authService.setAuth(token, user);
                const userId = user?.id;

                if (userId) {
                    // 2. Генерируем X3DH ключи
                    const x3dhKeys = await generateX3DHKeys();
                    const bundle = await exportX3DHBundleWithSignature(x3dhKeys);

                    // Сохраняем приватный ключ в localStorage
                    const privateJwk = await window.crypto.subtle.exportKey('jwk', x3dhKeys.identityKeyPair.privateKey);
                    localStorage.setItem('e2ee_privateKey', JSON.stringify(privateJwk));

                    // Сохраняем X3DH ключи
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

                    // 3. Шифруем ключи KEK-паролем и загружаем бекап
                    await keyBackupService.uploadKeyBackup(kekPassword);
                    // Сохраняем KEK-пароль в sessionStorage для автоматических обновлений
                    sessionStorage.setItem('kek_password', kekPassword);

                    console.log('[Register] ✓ Keys generated, uploaded and backed up');
                }

                if (setIsAuthenticated) setIsAuthenticated(true);
                navigate('/chat');
            } else {
                // Сервер не вернул токен — переходим на логин
                navigate('/login');
            }
        } catch (err) {
            console.error('[Register] Error:', err);
            setError(err.response?.data?.error || err.message || 'Ошибка регистрации');
        } finally {
            setLoading(false);
        }
    };

    const inputStyle = {
        background: '#F5DEB3',
        color: '#520808',
        border: '2px solid #B22222',
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
                <div className="rounded-xl shadow-2xl p-8" style={{ background: '#FFDAB9' }}>
                    <h2 className="text-center text-2xl font-bold mb-2" style={{ color: '#520808' }}>
                        Регистрация
                    </h2>
                    <form className="space-y-5" onSubmit={handleSubmit}>
                        <div className="space-y-4">
                            {/* Поля аккаунта */}
                            {[
                                { id: 'username', label: 'Имя пользователя', type: 'text', placeholder: 'Имя пользователя', autoComplete: 'username' },
                                { id: 'email', label: 'Email', type: 'email', placeholder: 'Email', autoComplete: 'email' },
                                { id: 'password', label: 'Пароль от аккаунта', type: 'password', placeholder: 'Пароль', autoComplete: 'new-password' },
                                { id: 'confirmPassword', label: 'Повторите пароль от аккаунта', type: 'password', placeholder: 'Повторите пароль', autoComplete: 'new-password' },
                            ].map(({ id, label, type, placeholder, autoComplete }) => (
                                <div key={id}>
                                    <label htmlFor={id} className="block text-sm font-medium mb-1" style={{ color: '#520808' }}>
                                        {label}
                                    </label>
                                    <input
                                        id={id}
                                        name={id}
                                        type={type}
                                        autoComplete={autoComplete}
                                        required
                                        className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                        style={inputStyle}
                                        placeholder={placeholder}
                                        value={credentials[id]}
                                        onChange={(e) => setCredentials({ ...credentials, [id]: e.target.value })}
                                        disabled={loading}
                                    />
                                </div>
                            ))}

                            {/* Разделитель */}
                            <div className="pt-2 pb-1">
                                <div className="flex items-center gap-2">
                                    <div className="flex-1 h-px" style={{ background: '#B22222', opacity: 0.3 }} />
                                    <span className="text-xs font-semibold" style={{ color: '#B22222' }}>
                                        Защита ключей E2EE
                                    </span>
                                    <div className="flex-1 h-px" style={{ background: '#B22222', opacity: 0.3 }} />
                                </div>
                                <p className="text-xs mt-2" style={{ color: '#520808' }}>
                                    Отдельный пароль для шифрования ваших ключей переписки.
                                    Сервер никогда не видит этот пароль.
                                </p>
                            </div>

                            {/* KEK пароль */}
                            <div>
                                <label htmlFor="kekPassword" className="block text-sm font-medium mb-1" style={{ color: '#520808' }}>
                                    Пароль шифрования ключей
                                </label>
                                <input
                                    id="kekPassword"
                                    name="kekPassword"
                                    type="password"
                                    required
                                    minLength={8}
                                    className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                    style={inputStyle}
                                    placeholder="Минимум 8 символов"
                                    value={credentials.kekPassword}
                                    onChange={(e) => setCredentials({ ...credentials, kekPassword: e.target.value })}
                                    disabled={loading}
                                />
                            </div>
                            <div>
                                <label htmlFor="kekPasswordConfirm" className="block text-sm font-medium mb-1" style={{ color: '#520808' }}>
                                    Повторите пароль шифрования ключей
                                </label>
                                <input
                                    id="kekPasswordConfirm"
                                    name="kekPasswordConfirm"
                                    type="password"
                                    required
                                    minLength={8}
                                    className="block w-full px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                                    style={inputStyle}
                                    placeholder="Повторите пароль"
                                    value={credentials.kekPasswordConfirm}
                                    onChange={(e) => setCredentials({ ...credentials, kekPasswordConfirm: e.target.value })}
                                    disabled={loading}
                                />
                            </div>

                            {/* Предупреждение */}
                            <div className="px-3 py-2 rounded-lg text-xs" style={{ background: '#F5DEB3', color: '#520808', border: '1px solid #B22222' }}>
                                <strong>Запомните пароль шифрования ключей!</strong> Если забудете — историю переписки восстановить невозможно.
                            </div>
                        </div>

                        {error && (
                            <div className="px-4 py-3 rounded-lg text-sm shadow-sm border mb-2"
                                style={{ background: '#FFDAB9', color: '#B22222', borderColor: '#B22222' }}>
                                {error}
                            </div>
                        )}

                        <div>
                            <button
                                type="submit"
                                disabled={loading}
                                className="w-full flex justify-center py-2 px-4 text-sm font-bold rounded-lg transition-colors"
                                style={{ background: '#B22222', color: '#fff', border: 'none', boxShadow: '0 2px 8px rgba(178,34,34,0.12)' }}
                                onMouseOver={e => { if (!loading) e.currentTarget.style.background = '#520808'; }}
                                onMouseOut={e => { e.currentTarget.style.background = '#B22222'; }}
                            >
                                {loading ? (
                                    <><div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>Регистрация...</>
                                ) : 'Зарегистрироваться'}
                            </button>
                        </div>
                    </form>
                    <p className="text-center mt-6 text-sm">
                        Уже есть аккаунт?{' '}
                        <a href="/login" className="font-medium underline" style={{ color: '#B22222' }}>
                            Войти
                        </a>
                    </p>
                </div>
            </div>
        </div>
    );
};

export default RegisterForm;



