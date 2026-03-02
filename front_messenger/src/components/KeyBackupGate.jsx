/**
 * KeyBackupGate.jsx
 *
 * Компонент-«шлюз» для KEK (Key Encryption Key).
 * Показывается один раз за сессию (данные хранятся в sessionStorage).
 *
 * Сценарии:
 *  1. KEK уже разблокирован в этой сессии → сразу пропускаем (children)
 *  2. Бекап есть на сервере → просим ввести KEK-пароль
 *  3. Бекапа нет → предлагаем создать новый KEK-пароль
 *  4. Пользователь нажимает «Пропустить» → пропускаем без KEK
 */

import React, { useState, useEffect } from 'react';
import keyBackupService from '../services/keyBackupService';
import { hasLocalE2EEKeys } from '../utils/keyEncryption';

const GATE_CHECKED_KEY = 'kek_gate_checked'; // sessionStorage — очищается при закрытии вкладки

const KeyBackupGate = ({ children }) => {
    // Синхронная проверка прямо при инициализации — без задержки useEffect
    const [gateState, setGateState] = useState(() => {
        if (sessionStorage.getItem(GATE_CHECKED_KEY)) return 'done';
        if (sessionStorage.getItem('kek_password')) {
            sessionStorage.setItem(GATE_CHECKED_KEY, '1');
            return 'done';
        }
        return 'checking';
    });
    const [kekPassword, setKekPassword] = useState('');
    const [kekPasswordConfirm, setKekPasswordConfirm] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    useEffect(() => {
        // Если уже определились синхронно — не делаем async запрос
        if (gateState !== 'checking') return;

        // Проверяем есть ли бекап на сервере
        const check = async () => {
            try {
                const hasBackup = await keyBackupService.hasServerBackup();
                if (hasBackup) {
                    setGateState('unlock');
                } else {
                    setGateState('create');
                }
            } catch (e) {
                console.warn('[KeyBackupGate] Could not check server backup:', e.message);
                // При ошибке сети — пропускаем шлюз
                sessionStorage.setItem(GATE_CHECKED_KEY, '1');
                setGateState('done');
            }
        };
        check();
    }, [gateState]);

    const handleSkip = () => {
        sessionStorage.setItem(GATE_CHECKED_KEY, '1');
        setGateState('done');
    };

    // Расшифровать ключи существующим KEK-паролем
    const handleUnlock = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            await keyBackupService.downloadAndRestoreKeys(kekPassword);
            sessionStorage.setItem('kek_password', kekPassword);
            sessionStorage.setItem(GATE_CHECKED_KEY, '1');
            setSuccess('✓ Ключи восстановлены! Перезагрузка...');
            // Перезагружаем страницу чтобы EnhancedChatWindow подхватил восстановленные ключи
            setTimeout(() => window.location.reload(), 900);
        } catch (err) {
            if (err.message?.includes('Неверный пароль')) {
                setError('Неверный пароль шифрования ключей');
            } else {
                setError(err.message || 'Ошибка расшифровки');
            }
        } finally {
            setLoading(false);
        }
    };

    // Создать новый бекап с новым KEK-паролем
    const handleCreate = async (e) => {
        e.preventDefault();
        setError('');
        if (kekPassword.length < 8) {
            setError('Минимум 8 символов');
            return;
        }
        if (kekPassword !== kekPasswordConfirm) {
            setError('Пароли не совпадают');
            return;
        }
        setLoading(true);
        try {
            // Ждём появления локальных E2EE-ключей (генерируются при открытии чата)
            // Максимум 15 секунд
            let hasKeys = hasLocalE2EEKeys();
            if (!hasKeys) {
                setSuccess('Ожидание генерации ключей...');
                for (let i = 0; i < 30; i++) {
                    await new Promise(r => setTimeout(r, 500));
                    hasKeys = hasLocalE2EEKeys();
                    if (hasKeys) break;
                }
                setSuccess('');
            }

            if (!hasKeys) {
                // Ключей нет — открываем чат чтобы сгенерировались, потом попробуем снова
                setError('Откройте любой чат чтобы сгенерировались ключи, затем повторите.');
                setLoading(false);
                return;
            }

            await keyBackupService.uploadKeyBackup(kekPassword);
            sessionStorage.setItem('kek_password', kekPassword);
            sessionStorage.setItem(GATE_CHECKED_KEY, '1');
            setSuccess('✓ Бекап ключей создан!');
            setTimeout(() => setGateState('done'), 800);
        } catch (err) {
            setError(err.message || 'Ошибка создания бекапа');
        } finally {
            setLoading(false);
        }
    };

    // Всё готово или ещё проверяем (в фоне) — рендерим дочерний контент
    if (gateState === 'checking' || gateState === 'done') return children;

    const inputStyle = {
        background: '#F5DEB3',
        color: '#520808',
        border: '2px solid #B22222',
        width: '100%',
        padding: '8px 12px',
        borderRadius: '8px',
        outline: 'none',
    };

    const btnPrimary = {
        background: '#B22222',
        color: '#fff',
        border: 'none',
        borderRadius: '8px',
        padding: '8px 0',
        width: '100%',
        fontWeight: 'bold',
        fontSize: '14px',
        cursor: loading ? 'not-allowed' : 'pointer',
        opacity: loading ? 0.7 : 1,
    };

    const btnSkip = {
        background: 'none',
        border: 'none',
        color: '#B22222',
        fontSize: '13px',
        textDecoration: 'underline',
        cursor: 'pointer',
        marginTop: '8px',
        width: '100%',
    };

    return (
        <>
            {children}
            {/* Overlay поверх чата */}
            <div style={{
                position: 'fixed', inset: 0,
                background: 'rgba(0,0,0,0.55)',
                zIndex: 9999,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
            }}>
                <div style={{
                    background: '#FFDAB9',
                    borderRadius: '16px',
                    padding: '32px 28px',
                    width: '100%',
                    maxWidth: '400px',
                    boxShadow: '0 8px 32px rgba(0,0,0,0.25)',
                }}>
                    {gateState === 'unlock' && (
                        <>
                            <h2 style={{ color: '#520808', textAlign: 'center', marginBottom: '8px', fontSize: '20px', fontWeight: 'bold' }}>
                                Ввод пароля ключей
                            </h2>
                            <p style={{ color: '#B22222', fontSize: '13px', textAlign: 'center', marginBottom: '20px' }}>
                                На сервере найден зашифрованный бекап ваших E2EE-ключей.<br />
                                Введите пароль шифрования для восстановления истории чатов.
                            </p>
                            <form onSubmit={handleUnlock} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                <div>
                                    <label style={{ display: 'block', fontSize: '13px', fontWeight: '500', color: '#520808', marginBottom: '4px' }}>
                                        Пароль шифрования ключей
                                    </label>
                                    <input
                                        type="password"
                                        required
                                        style={inputStyle}
                                        placeholder="Пароль от ключей шифрования"
                                        value={kekPassword}
                                        onChange={e => setKekPassword(e.target.value)}
                                        disabled={loading}
                                        autoFocus
                                    />
                                    <p style={{ fontSize: '11px', color: '#B22222', marginTop: '3px' }}>
                                        Это не пароль от аккаунта. Сервер никогда не видит этот пароль.
                                    </p>
                                </div>
                                {error && (
                                    <div style={{ background: '#F5DEB3', color: '#B22222', border: '1px solid #B22222', borderRadius: '8px', padding: '8px 12px', fontSize: '13px' }}>
                                        {error}
                                    </div>
                                )}
                                {success && (
                                    <div style={{ background: '#d4edda', color: '#155724', borderRadius: '8px', padding: '8px 12px', fontSize: '13px' }}>
                                        {success}
                                    </div>
                                )}
                                <button type="submit" style={btnPrimary} disabled={loading}>
                                    {loading ? 'Расшифровка...' : 'Восстановить ключи'}
                                </button>
                                <button type="button" style={btnSkip} onClick={handleSkip}>
                                    Пропустить (история чатов может быть недоступна)
                                </button>
                            </form>
                        </>
                    )}

                    {gateState === 'create' && (
                        <>
                            <h2 style={{ color: '#520808', textAlign: 'center', marginBottom: '8px', fontSize: '20px', fontWeight: 'bold' }}>
                                Защита ключей шифрования
                            </h2>
                            <p style={{ color: '#B22222', fontSize: '13px', textAlign: 'center', marginBottom: '8px' }}>
                                Создайте пароль для защиты ваших E2EE-ключей.<br />
                                Ключи зашифруются и сохранятся на сервере.
                            </p>
                            <div style={{ background: '#F5DEB3', border: '1px solid #B22222', borderRadius: '8px', padding: '8px 12px', fontSize: '11px', color: '#520808', marginBottom: '16px' }}>
                                <strong>Запомните этот пароль!</strong> Сервер его не знает. Если забудете — историю переписки восстановить невозможно.
                            </div>
                            <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                <div>
                                    <label style={{ display: 'block', fontSize: '13px', fontWeight: '500', color: '#520808', marginBottom: '4px' }}>
                                        Пароль шифрования ключей
                                    </label>
                                    <input
                                        type="password"
                                        required
                                        minLength={8}
                                        style={inputStyle}
                                        placeholder="Минимум 8 символов"
                                        value={kekPassword}
                                        onChange={e => setKekPassword(e.target.value)}
                                        disabled={loading}
                                        autoFocus
                                    />
                                </div>
                                <div>
                                    <label style={{ display: 'block', fontSize: '13px', fontWeight: '500', color: '#520808', marginBottom: '4px' }}>
                                        Повторите пароль
                                    </label>
                                    <input
                                        type="password"
                                        required
                                        minLength={8}
                                        style={inputStyle}
                                        placeholder="Повторите пароль"
                                        value={kekPasswordConfirm}
                                        onChange={e => setKekPasswordConfirm(e.target.value)}
                                        disabled={loading}
                                    />
                                </div>
                                {error && (
                                    <div style={{ background: '#F5DEB3', color: '#B22222', border: '1px solid #B22222', borderRadius: '8px', padding: '8px 12px', fontSize: '13px' }}>
                                        {error}
                                    </div>
                                )}
                                {success && (
                                    <div style={{ background: '#d4edda', color: '#155724', borderRadius: '8px', padding: '8px 12px', fontSize: '13px' }}>
                                        {success}
                                    </div>
                                )}
                                <button
                                    type="submit"
                                    style={{ ...btnPrimary, opacity: (loading || kekPassword.length < 8) ? 0.6 : 1, cursor: (loading || kekPassword.length < 8) ? 'not-allowed' : 'pointer' }}
                                    disabled={loading || kekPassword.length < 8}
                                >
                                    {loading ? 'Сохранение...' : 'Создать бекап ключей'}
                                </button>
                                <button type="button" style={btnSkip} onClick={handleSkip}>
                                    Пропустить (ключи не будут сохранены на сервере)
                                </button>
                            </form>
                        </>
                    )}
                </div>
            </div>
        </>
    );
};

export default KeyBackupGate;
