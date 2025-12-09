import React, { useState, useEffect, useRef, useCallback, useLayoutEffect } from 'react';
import { useSelector } from 'react-redux';
import chatService from '../services/chatService';
import { performX3DHHandshake } from '../services/authService';
import UserSearchModal from './UserSearchModal';
import FileUpload, { useDragAndDrop } from './FileUpload';
import { generateX3DHKeys, encryptMessage, decryptMessage } from '../utils/crypto';
import { DoubleRatchetManager } from '../utils/DoubleRatchetManager';
import { saveSessionKey, loadSessionKey } from '../utils/sessionKeyStorage';
import './EnhancedChatWindow.css';

// Вспомогательная функция для получения правильного ключа хранилища (должна совпадать с sessionKeyStorage.js)
function getStorageKey(userId1, userId2) {
    const ids = [String(userId1), String(userId2)].sort((a, b) => a.localeCompare(b));
    const [id1, id2] = ids;
    return `e2ee_session_${id1}_${id2}`;
}

const ChatWindow = ({ selectedChat, onChatUpdate }) => {
    const [newMessage, setNewMessage] = useState('');
    const [messages, setMessages] = useState([]);
    const [showChatInfo, setShowChatInfo] = useState(false);
    const [showAddParticipants, setShowAddParticipants] = useState(false);
    const [chatInfo, setChatInfo] = useState(null);
    const [isLoadingMessages, setIsLoadingMessages] = useState(false);
    const [isLoadingOlderMessages, setIsLoadingOlderMessages] = useState(false);
    const [hasMoreMessages, setHasMoreMessages] = useState(true);
    const [currentPage, setCurrentPage] = useState(0);
    const messagesEndRef = useRef(null);
    const messagesContainerRef = useRef(null);
    const lastLoadedChatId = useRef(null);
    const scrollTimeoutRef = useRef(null);
    const loadingTimeoutRef = useRef(null); // Для отмены загрузки при быстром переключении
    const isInitialScrollDone = useRef(false); // Ref вместо state для избежания лишних рендеров
    const markAsReadTimeoutRef = useRef(null); // Таймаут для отметки сообщений как прочитанных
    const sessionKeyRef = useRef(null); // Реф для хранения актуального sessionKey и избежания stale closure
    const { user } = useSelector(state => state.auth);
    const fileUploadRef = useRef(null);
    const pendingReadMapRef = useRef(new Map()); // Буфер для MESSAGE_READ до появления сообщения

    // E2EE состояние
    const [x3dhKeys, setX3dhKeys] = useState(null);
    const [sessionKey, setSessionKey] = useState(null);
    const [partnerId, setPartnerId] = useState(null);
    const [ratchetManager] = useState(() => new DoubleRatchetManager());
    const [ratchetReady, setRatchetReady] = useState(false);
    const [keyStatus, setKeyStatus] = useState('Инициализация E2EE...');
    const [e2eeReady, setE2eeReady] = useState(false);

    const addfriend_icon = 'addfriend.png';
    const info_icon = 'info.png';
    const PAGE_SIZE = 30;
    const TOP_LAZY_LOAD_THRESHOLD = 200;
    const BOTTOM_STICKY_THRESHOLD = 120;

    // Состояние для анимации перехода между чатами
    const [chatTransitionStage, setChatTransitionStage] = useState('idle'); // 'fadeOut' | 'fadeIn' | 'idle'

    // Флаг для предотвращения бесконечной перезагрузки
    const hasReloadedAfterKeyRef = useRef(false);

    // Функция получения ID текущего пользователя - оборачиваем в useCallback
    const getCurrentUserId = useCallback(() => {
        return user?.id || null;
    }, [user?.id]);

    // Универсальная функция прокрутки вниз
    const scrollToBottom = useCallback((smooth = false) => {
        if (scrollTimeoutRef.current) {
            clearTimeout(scrollTimeoutRef.current);
        }

        const scroll = () => {
            if (messagesContainerRef.current) {
                const container = messagesContainerRef.current;
                const scrollOptions = {
                    top: container.scrollHeight,
                    behavior: smooth ? 'smooth' : 'auto'
                };
                container.scrollTo(scrollOptions);
                shouldAutoScrollRef.current = true;
            }
        };

        // Выполняем прокрутку после рендеринга
        requestAnimationFrame(() => {
            requestAnimationFrame(scroll);
        });
    }, []);

    // Функция загрузки сообщений - обернута в useCallback
    const loadChatMessages = useCallback(async () => {
        if (!selectedChat) return;

        // Защита от множественных одновременных загрузок
        if (isLoadingMessages) {
            console.log('Already loading messages, skipping...');
            return;
        }

        // Флаг отмены для асинхронных операций
        const cancelledRef = { cancelled: false };
        loadChatMessages.cancelledRef = cancelledRef;

        try {
            setIsLoadingMessages(true);
            setCurrentPage(0);
            setHasMoreMessages(true);

            console.log(`Loading initial messages for chat ${selectedChat.id}`);
            console.log(`[E2EE] Session key status: ${sessionKey ? 'READY' : 'NOT READY'}`);

            const chatMessages = await chatService.getChatMessages(selectedChat.id, 0, PAGE_SIZE);
            if (loadChatMessages.cancelledRef !== cancelledRef || cancelledRef.cancelled) return;

            console.log(`Loaded ${chatMessages.length} messages`);

            // Проверяем, что чат все еще актуален (не переключились на другой)
            if (selectedChat.id !== lastLoadedChatId.current) {
                console.log('Chat changed during loading, discarding messages');
                return;
            }

            if (chatMessages.length < PAGE_SIZE) {
                setHasMoreMessages(false);
            }

            // Сообщения приходят от НОВЫХ к СТАРЫМ (DESC), реверсируем для отображения
            const reversed = chatMessages.reverse();

            // Дешифруем сообщения, если E2EE готов
            const currentSessionKey = sessionKeyRef.current || sessionKey; // Используем актуальный ключ из ref
            const decryptedMessages = await Promise.all(reversed.map(async (m) => {
                let content = m.content;
                let originalContent = null;

                if (e2eeReady && currentSessionKey && content && content.includes('iv') && content.includes('ciphertext')) {
                    // Сохраняем оригинальный контент для позднего дешифрования
                    originalContent = content;

                    try {
                        // Логируем sessionKey для диагностики
                        try {
                            const exported = await window.crypto.subtle.exportKey('raw', currentSessionKey);
                            const bytes = new Uint8Array(exported);
                            const preview = Array.from(bytes.slice(0, 20)).map(b => b.toString(16).padStart(2, '0')).join('');
                            console.log('[E2EE][decrypt] Session key preview:', preview + '...');
                        } catch (e) {
                            console.log('[E2EE][decrypt] Could not export session key for preview');
                        }
                        const encryptedData = JSON.parse(content);
                        content = await decryptMessage(currentSessionKey, encryptedData);
                        originalContent = null; // Если расшифровано успешно, не сохраняем оригинал
                        console.log('[E2EE] Message decrypted from DB using X3DH + AES-GCM');
                    } catch (e) {
                        // ИСПРАВЛЕНИЕ: Не пытаемся регенерировать ключ для групповых чатов!
                        // Групповой ключ - это ОБЩИЙ ключ, который должен быть одинаковым у всех участников.
                        // Регенерация случайного ключа не поможет расшифровать старые сообщения.

                        const participants = selectedChat?.participants || [];
                        const isGroupChat = participants.length > 2;

                        if (e.name === 'OperationError') {
                            if (isGroupChat) {
                                console.error('[E2EE] OperationError при дешифровании группового сообщения');
                                console.error('[E2EE] Ключ шифрования не совпадает с ключом, которым было зашифровано сообщение');
                                console.error('[E2EE] Для групповых чатов ключ должен синхронизироваться между участниками');
                                content = '[Ошибка: неверный ключ шифрования]';
                                originalContent = null;
                            } else {
                                // Для приватных чатов можем попробовать перегенерировать через X3DH
                                console.warn('[E2EE] OperationError при дешифровании, пробуем перегенерировать ключ через X3DH...');
                                try {
                                    const myUserId = getCurrentUserId();
                                    const newSessionKey = await performX3DHHandshake(x3dhKeys, partnerId, myUserId);
                                    await saveSessionKey(myUserId, partnerId, newSessionKey);
                                    console.log('[E2EE] New private session key generated via X3DH');

                                    await ratchetManager.initSession(partnerId, newSessionKey);
                                    setSessionKey(newSessionKey);
                                    setRatchetReady(true);
                                    setE2eeReady(true);

                                    // Повторяем попытку дешифрования
                                    const exported = await window.crypto.subtle.exportKey('raw', newSessionKey);
                                    const bytes = new Uint8Array(exported);
                                    const preview = Array.from(bytes.slice(0, 20)).map(b => b.toString(16).padStart(2, '0')).join('');
                                    console.log('[E2EE][retry] New session key preview:', preview + '...');
                                    const encryptedData = JSON.parse(content);
                                    content = await decryptMessage(newSessionKey, encryptedData);
                                    originalContent = null; // Если расшифровано успешно при повторе, не сохраняем
                                    console.log('[E2EE] Message decrypted after X3DH key regeneration');
                                } catch (retryError) {
                                    console.warn('[E2EE] Повторная попытка дешифрования не удалась:', retryError);
                                    content = '[Ошибка дешифрования]';
                                    originalContent = null;
                                }
                            }
                        } else {
                            console.warn('[E2EE] Failed to decrypt message from DB:', e);
                            content = '[Ошибка дешифрования]';
                            originalContent = null;
                        }
                    }
                } else if (!e2eeReady && content && content.includes('iv') && content.includes('ciphertext')) {
                    // Session key ещё не готов, показываем placeholder
                    console.log('[E2EE] Session key not ready yet, showing placeholder');
                    originalContent = content; // Сохраняем оригинальный контент для позднего дешифрования
                    content = 'Инициализация шифрования...';
                }
                return { ...m, content, originalContent };
            }));

            if (loadChatMessages.cancelledRef !== cancelledRef || cancelledRef.cancelled) return;

            // Применяем накопленные read-инкременты, если есть
            const withPendingReads = decryptedMessages.map(m => {
                const inc = pendingReadMapRef.current.get(String(m.id));
                return inc ? { ...m, readCount: (m.readCount || 0) + inc } : m;
            });
            setMessages(withPendingReads);
        } catch (error) {
            if (loadChatMessages.cancelledRef !== cancelledRef || cancelledRef.cancelled) return;
            console.error('Ошибка загрузки сообщений:', error);
            setMessages([]);
        } finally {
            if (loadChatMessages.cancelledRef !== cancelledRef || cancelledRef.cancelled) return;
            setIsLoadingMessages(false);
        }
    }, [selectedChat, isLoadingMessages, PAGE_SIZE, sessionKey, e2eeReady, x3dhKeys, partnerId, ratchetManager, getCurrentUserId]);

    // Функция загрузки информации о чате - обернута в useCallback
    const loadChatInfo = useCallback(async () => {
        if (!selectedChat) return;
        try {
            const info = await chatService.getChatInfo(selectedChat.id);
            // Используем только свежие статусы участников из info
            setChatInfo(info);
            // Уведомляем родительский компонент об обновлении чата АСИНХРОННО
            if (onChatUpdate) {
                // Отложенный вызов чтобы избежать проблем с setState во время рендеринга
                Promise.resolve().then(() => onChatUpdate(info));
            }
        } catch (error) {
            console.error('Ошибка загрузки информации о чате:', error);
        }
    }, [selectedChat, onChatUpdate]);


    // Effect для загрузки сообщений при смене чата
    useEffect(() => {
        if (!selectedChat) {
            // Очищаем состояние если чат не выбран
            setMessages([]);
            setChatInfo(null);
            lastLoadedChatId.current = null;
            isInitialScrollDone.current = false;
            // ОЧИСТКА DoubleRatchetManager и sessionKey
            if (partnerId && ratchetManager) {
                ratchetManager.clearSession(partnerId);
            }
            setSessionKey(null);
            setRatchetReady(false);
            setPartnerId(null);
            return;
        }

        // Загружаем сообщения только если чат изменился
        if (selectedChat.id !== lastLoadedChatId.current) {
            // ОЧИСТКА DoubleRatchetManager и sessionKey при смене чата
            if (partnerId && ratchetManager) {
                ratchetManager.clearSession(partnerId);
            }
            setSessionKey(null);
            setRatchetReady(false);
            setPartnerId(null);
            // Отменяем предыдущие загрузки
            if (loadingTimeoutRef.current) {
                clearTimeout(loadingTimeoutRef.current);
            }
            // СРАЗУ очищаем старые сообщения для предотвращения мерцания
            setMessages([]);
            isInitialScrollDone.current = false;
            lastLoadedChatId.current = selectedChat.id;
            // ИСПРАВЛЕНИЕ: Сначала используем данные из selectedChat (если они есть)
            if (selectedChat.participants) {
                setChatInfo(selectedChat);
            }
            // Запускаем загрузку
            loadChatMessages();
            // Загружаем полную информацию о чате, но не перезаписываем статусы
            loadChatInfo();
        }
    }, [selectedChat, loadChatMessages, loadChatInfo, partnerId, ratchetManager]);


    // Функция отметки сообщений как прочитанных
    const markChatAsRead = useCallback(async () => {
        if (!selectedChat) return;

        try {
            await chatService.markAllChatMessagesAsRead(selectedChat.id);
            console.log(`Marked all messages as read in chat ${selectedChat.id}`);
        } catch (error) {
            console.error('Ошибка отметки сообщений как прочитанных:', error);
        }
    }, [selectedChat]);

    // Автоматическая отметка сообщений как прочитанных при просмотре чата
    useEffect(() => {
        if (messages.length > 0 && selectedChat && isInitialScrollDone.current) {
            // Отменяем предыдущий таймаут
            if (markAsReadTimeoutRef.current) {
                clearTimeout(markAsReadTimeoutRef.current);
            }

            // Отмечаем сообщения как прочитанные с небольшой задержкой
            // чтобы пользователь успел их увидеть
            markAsReadTimeoutRef.current = setTimeout(() => {
                markChatAsRead();
            }, 1000); // Задержка 1 секунда
        }

        return () => {
            if (markAsReadTimeoutRef.current) {
                clearTimeout(markAsReadTimeoutRef.current);
            }
        };
    }, [messages.length, selectedChat, markChatAsRead]);

    // ==================== E2EE ИНИЦИАЛИЗАЦИЯ ====================

    // Генерация X3DH ключей при монтировании компонента
    useEffect(() => {
        async function setupX3DH() {
            try {
                const myUserId = getCurrentUserId();
                if (!myUserId) {
                    console.error('[E2EE] User ID not available');
                    setKeyStatus('Ошибка: нет ID пользователя');
                    return;
                }

                // Пытаемся загрузить сохраненные ключи из localStorage
                const savedKeysJson = localStorage.getItem(`x3dh_keys_${myUserId}`);

                if (savedKeysJson) {
                    try {
                        const savedKeys = JSON.parse(savedKeysJson);
                        console.log('[E2EE] Found saved X3DH keys in localStorage');

                        // ВАЖНО: Проверяем актуальность ключей на сервере
                        try {
                            const { default: userService } = await import('../services/userService');
                            const serverBundle = await userService.getPreKeyBundle(myUserId);

                            if (serverBundle.identityKey !== savedKeys.identityPublic) {
                                console.warn('[E2EE] Local X3DH keys DO NOT MATCH server keys!');
                                console.log('[E2EE] Server identity key:', serverBundle.identityKey?.substring(0, 20) + '...');
                                console.log('[E2EE] Local identity key:', savedKeys.identityPublic?.substring(0, 20) + '...');
                                console.log('[E2EE] Regenerating X3DH keys to match server...');

                                // Удаляем несовместимые ключи
                                localStorage.removeItem(`x3dh_keys_${myUserId}`);

                                // Очищаем все session keys т.к. они основаны на старых X3DH ключах
                                Object.keys(localStorage).forEach(key => {
                                    if (key.startsWith('e2ee_session_')) {
                                        localStorage.removeItem(key);
                                    }
                                });

                                // Форсируем перегенерацию (пропускаем восстановление)
                                throw new Error('Keys mismatch - regenerating');
                            } else {
                                console.log('[E2EE] ✓ Local X3DH keys match server keys');
                            }
                        } catch (serverCheckError) {
                            if (serverCheckError.message === 'Keys mismatch - regenerating') {
                                throw serverCheckError; // Перебрасываем для регенерации
                            }
                            console.warn('[E2EE] Could not verify keys with server:', serverCheckError.message);
                            // Продолжаем использовать локальные ключи
                        }

                        // Импортируем ключи обратно в CryptoKey формат
                        const identityKeyPair = {
                            publicKey: await window.crypto.subtle.importKey(
                                'raw',
                                Uint8Array.from(atob(savedKeys.identityPublic), c => c.charCodeAt(0)),
                                { name: 'ECDH', namedCurve: 'P-256' },
                                true,
                                []
                            ),
                            privateKey: await window.crypto.subtle.importKey(
                                'jwk',
                                JSON.parse(savedKeys.identityPrivate),
                                { name: 'ECDH', namedCurve: 'P-256' },
                                true,
                                ['deriveKey', 'deriveBits']
                            )
                        };

                        const signedPreKeyPair = {
                            publicKey: await window.crypto.subtle.importKey(
                                'raw',
                                Uint8Array.from(atob(savedKeys.signedPrePublic), c => c.charCodeAt(0)),
                                { name: 'ECDH', namedCurve: 'P-256' },
                                true,
                                []
                            ),
                            privateKey: await window.crypto.subtle.importKey(
                                'jwk',
                                JSON.parse(savedKeys.signedPrePrivate),
                                { name: 'ECDH', namedCurve: 'P-256' },
                                true,
                                ['deriveKey', 'deriveBits']
                            )
                        };

                        const keys = {
                            identityKeyPair,
                            signedPreKeyPair,
                            oneTimePreKeys: [] // Пока не используем
                        };

                        setX3dhKeys(keys);
                        console.log('[E2EE] X3DH keys restored from localStorage');
                        return;
                    } catch (e) {
                        console.warn('[E2EE] Failed to restore saved keys, generating new:', e);
                        localStorage.removeItem(`x3dh_keys_${myUserId}`);
                    }
                }

                // Генерируем новые ключи если сохраненных нет
                console.log('[E2EE] Generating new X3DH keys...');
                const keys = await generateX3DHKeys();

                // Экспортируем публичные ключи для отправки на сервер
                const { exportX3DHBundle } = await import('../utils/crypto');
                const bundle = await exportX3DHBundle(keys);

                // Публикуем ключи на сервере
                const { default: userService } = await import('../services/userService');
                await userService.savePreKeyBundle(
                    myUserId,
                    bundle.identityKey,
                    bundle.signedPreKey,
                    bundle.oneTimePreKeys,
                    null // signature пока не используем
                );

                // Сохраняем приватные ключи локально
                const identityPrivateJwk = await window.crypto.subtle.exportKey('jwk', keys.identityKeyPair.privateKey);
                const signedPrePrivateJwk = await window.crypto.subtle.exportKey('jwk', keys.signedPreKeyPair.privateKey);

                const keysToSave = {
                    identityPublic: bundle.identityKey,
                    identityPrivate: JSON.stringify(identityPrivateJwk),
                    signedPrePublic: bundle.signedPreKey,
                    signedPrePrivate: JSON.stringify(signedPrePrivateJwk)
                };

                localStorage.setItem(`x3dh_keys_${myUserId}`, JSON.stringify(keysToSave));

                // ВАЖНО: Очищаем все старые session keys, т.к. X3DH ключи изменились
                const keysToRemove = [];
                Object.keys(localStorage).forEach(key => {
                    if (key.startsWith('e2ee_session_')) {
                        keysToRemove.push(key);
                        localStorage.removeItem(key);
                    }
                });

                if (keysToRemove.length > 0) {
                    console.log('[E2EE] Cleared', keysToRemove.length, 'old session keys (X3DH keys changed)');
                }

                setX3dhKeys(keys);
                console.log('[E2EE] X3DH keys generated and published to server');
            } catch (error) {
                console.error('[E2EE] Failed to setup X3DH keys:', error);
                setKeyStatus('Ошибка генерации ключей');
            }
        }
        setupX3DH();
    }, [getCurrentUserId]);

    // Определение ID собеседника (для приватных чатов) или чата (для групповых)
    useEffect(() => {
        if (!selectedChat || !user) {
            setPartnerId(null);
            setRatchetReady(false);
            console.log('[E2EE] Нет выбранного чата или пользователя');
            return;
        }

        // Определяем тип чата по количеству участников (более надежно чем isGroup флаг)
        const participants = selectedChat.participants || [];
        const isGroupChat = participants.length > 2; // 2+ участника = групповой

        console.log('[E2EE] Chat analysis - participants:', participants.length, 'isGroupFlag:', selectedChat.isGroup, 'determinedAsGroup:', isGroupChat);

        if (!isGroupChat && participants.length === 2) {
            // Приватный чат - ищем второго участника
            const partner = participants.find(p => p.id !== user.id);
            if (partner) {
                setPartnerId(partner.id);
                console.log('[E2EE] Partner ID set (private chat):', partner.id);
            } else {
                setPartnerId(null);
                console.log('[E2EE] Partner not found in participants:', participants);
            }
        } else if (isGroupChat) {
            // Групповой чат - используем chatId
            setPartnerId(selectedChat.id);
            console.log('[E2EE] Group chat ID set:', selectedChat.id, 'with', participants.length, 'participants');
        } else {
            // Одиночный чат (только один участник - сам с собой)?
            setPartnerId(null);
            console.log('[E2EE] Unknown chat type, participants:', participants.length);
        }
    }, [selectedChat, user]);

    // Установление сессионного ключа через X3DH handshake (для приватных) или генерацию (для групповых)
    useEffect(() => {
        if (!selectedChat || !x3dhKeys) {
            setSessionKey(null);
            setRatchetReady(false);
            console.log('[E2EE] Нет selectedChat или x3dhKeys', { selectedChat: selectedChat?.id, x3dhKeys: !!x3dhKeys });
            return;
        }

        async function establishSessionKey() {
            try {
                const myUserId = getCurrentUserId();
                // Определяем тип чата по количеству участников (более надежно)
                const participants = selectedChat?.participants || [];
                const isGroupChat = participants.length > 2;

                // ВАЖНО: Для групповых чатов используем chatId, для приватных - partnerId
                const targetId = isGroupChat ? selectedChat.id : partnerId;

                if (!targetId) {
                    console.warn('[E2EE] Target ID not ready', { isGroupChat, chatId: selectedChat.id, partnerId });
                    return;
                }

                console.log('[E2EE] Initializing E2EE for', isGroupChat ? 'group chat' : 'private chat', ':', targetId, 'myUserId:', myUserId, 'participants:', participants.length);

                let session;

                if (isGroupChat) {
                    //Используется только для тестирования.
                    console.log('[E2EE] Using DETERMINISTIC group key (NOT SECURE - for testing only!)');
                    console.log('[E2EE] Generating deterministic key for group chat:', targetId);

                    // Создаем детерминированный ключ из chatId
                    const keyMaterial = `group-chat-key-${targetId}`;
                    const keyMaterialBytes = new TextEncoder().encode(keyMaterial);

                    // Используем SHA-256 для генерации 256-битного ключа
                    const keyHash = await window.crypto.subtle.digest('SHA-256', keyMaterialBytes);

                    // Импортируем как AES-GCM ключ
                    session = await window.crypto.subtle.importKey(
                        'raw',
                        keyHash,
                        { name: 'AES-GCM' },
                        true,
                        ['encrypt', 'decrypt']
                    );

                    // Логируем первые байты для отладки
                    const keyBytes = new Uint8Array(keyHash);
                    const keyPreview = Array.from(keyBytes.slice(0, 20)).map(b => b.toString(16).padStart(2, '0')).join('');
                    console.log('[E2EE] ✓ Deterministic group key preview:', keyPreview + '...');
                    console.log('[E2EE] All participants of chat', targetId, 'will have THE SAME key');
                } else {
                    // Для приватных чатов: используем X3DH handshake
                    session = await loadSessionKey(myUserId, targetId);

                    if (session) {
                        // Проверяем, что sessionKey действительно для этой пары
                        const keyName = getStorageKey(myUserId, targetId);
                        if (!localStorage.getItem(keyName)) {
                            console.warn('[E2EE] Session key в хранилище не найден для пары:', keyName);
                            session = null;
                        } else {
                            console.log('[E2EE] ✓ Session key найден в хранилище для пары:', keyName);
                        }
                    }

                    if (!session) {
                        // Создаем новый session key через X3DH handshake для этой пары
                        console.log('[E2EE] No saved key found, performing X3DH handshake with partner:', targetId);
                        session = await performX3DHHandshake(x3dhKeys, targetId, myUserId);
                        await saveSessionKey(myUserId, targetId, session);
                        console.log('[E2EE] ✓ New session key created and saved for pair:', myUserId, targetId);
                    } else {
                        console.log('[E2EE] ✓ Loaded existing session key from storage for pair:', myUserId, targetId);
                    }
                }

                setSessionKey(session);

                // Инициализируем Double Ratchet менеджер с session key
                await ratchetManager.initSession(targetId, session);
                setRatchetReady(true);
                setKeyStatus('🔒 Чат защищён (E2EE + Forward Secrecy)');
                console.log('[E2EE] ✓ Session ready for encryption/decryption');
            } catch (error) {
                console.error('[E2EE] E2EE initialization failed:', error);
                setKeyStatus('Ошибка установления сессии');
                setRatchetReady(false);
            }
        }
        establishSessionKey();
    }, [x3dhKeys, ratchetManager, getCurrentUserId, selectedChat, partnerId]);

    // Синхронизируем sessionKeyRef с sessionKey для избежания stale closure
    useEffect(() => {
        sessionKeyRef.current = sessionKey;
        console.log('[E2EE] sessionKeyRef synchronized with sessionKey');
    }, [sessionKey]);

    // Обновляем сообщения с placeholder'ами когда sessionKey становится готовым
    useEffect(() => {
        if (!sessionKey || !selectedChat || messages.length === 0 || !ratchetReady) {
            hasReloadedAfterKeyRef.current = false;
            return;
        }

        // Проверяем есть ли сообщения с placeholder'ом инициализации
        const messagesWithPlaceholders = messages.filter(m =>
            (m.content === 'Инициализация шифрования...' ||
             m.content === '[Ожидание ключа шифрования]') && // Для обратной совместимости
            m.originalContent
        );

        if (messagesWithPlaceholders.length > 0 && !isLoadingMessages && !hasReloadedAfterKeyRef.current) {
            console.log('[E2EE] Session key is ready, decrypting', messagesWithPlaceholders.length, 'messages with placeholders');
            hasReloadedAfterKeyRef.current = true;

            // Дешифруем все placeholder сообщения асинхронно
            (async () => {
                for (const placeholderMsg of messagesWithPlaceholders) {
                    try {
                        const encryptedData = JSON.parse(placeholderMsg.originalContent);
                        const decrypted = await decryptMessage(sessionKey, encryptedData);

                        // Обновляем сообщение с дешифрованным контентом
                        setMessages(prev => prev.map(msg =>
                            msg.id === placeholderMsg.id
                                ? { ...msg, content: decrypted, originalContent: null }
                                : msg
                        ));
                        console.log('[E2EE] Message decrypted after session ready:', placeholderMsg.id);
                    } catch (e) {
                        console.warn('[E2EE] Failed to decrypt placeholder message:', placeholderMsg.id, e);
                        // Показываем сообщение об ошибке
                        setMessages(prev => prev.map(msg =>
                            msg.id === placeholderMsg.id
                                ? { ...msg, content: 'Ошибка дешифрования', originalContent: null }
                                : msg
                        ));
                    }
                }
            })();
        }
    }, [sessionKey, selectedChat, messages, ratchetReady, isLoadingMessages]);

    // Отдельный эффект для прокрутки после загрузки сообщений
    useLayoutEffect(() => {
        if (messages.length > 0 && !isLoadingMessages && !isInitialScrollDone.current) {
            if (messagesContainerRef.current) {
                const container = messagesContainerRef.current;
                container.scrollTop = container.scrollHeight;
                isInitialScrollDone.current = true;
                // Сразу отмечаем как прочитанные
                markChatAsRead();
            }
        }
    }, [messages.length, isLoadingMessages, markChatAsRead]);

    // Effect для прокрутки при получении новых сообщений (не при первой загрузке)
    useEffect(() => {
        if (messages.length > 0 && !isLoadingMessages && isInitialScrollDone.current) {
            const container = messagesContainerRef.current;
            if (!container) return;
            const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
            shouldAutoScrollRef.current = distanceFromBottom <= BOTTOM_STICKY_THRESHOLD;

            if (shouldAutoScrollRef.current) {
                scrollToBottom(true);
            }
        }
    }, [messages.length, isLoadingMessages, scrollToBottom]);

    // Для восстановления позиции первого видимого сообщения
    const messageRefs = useRef({});
    const firstVisibleMessageIdRef = useRef(null);
    const shouldAutoScrollRef = useRef(true);

    // Функция для загрузки старых сообщений (при прокрутке вверх)
    const loadOlderMessages = useCallback(async () => {
        if (!selectedChat || isLoadingOlderMessages || !hasMoreMessages || isLoadingMessages) {
            return;
        }

        try {
            setIsLoadingOlderMessages(true);
            const nextPage = currentPage + 1;
            const container = messagesContainerRef.current;
            if (!container) return;

            // Блокируем автопрокрутку вниз при загрузке старых сообщений
            shouldAutoScrollRef.current = false;

            // Сохраняем id первого видимого сообщения
            const messageElements = Object.values(messageRefs.current);
            let firstVisibleId = null;
            for (let i = 0; i < messageElements.length; i++) {
                const el = messageElements[i];
                if (el && el.getBoundingClientRect().top >= container.getBoundingClientRect().top) {
                    firstVisibleId = el.dataset.messageId;
                    break;
                }
            }
            firstVisibleMessageIdRef.current = firstVisibleId;
            const prevScrollHeight = container.scrollHeight;
            const prevScrollTop = container.scrollTop;

            const olderMessages = await chatService.getChatMessages(selectedChat.id, nextPage, PAGE_SIZE);
            if (olderMessages.length === 0 || olderMessages.length < PAGE_SIZE) {
                setHasMoreMessages(false);
            }
            if (olderMessages.length > 0) {
                const reversedOlderMessages = [...olderMessages].reverse();

                // Дешифруем старые сообщения, если E2EE готов
                const decryptedOlderMessages = await Promise.all(reversedOlderMessages.map(async (m) => {
                    let content = m.content;
                    let originalContent = null;

                    // Проверяем, является ли контент зашифрованным
                    const isEncrypted = content && typeof content === 'string' &&
                                       content.includes('iv') && content.includes('ciphertext');

                    if (isEncrypted) {
                        // Сохраняем оригинальный зашифрованный контент для позднего дешифрования
                        originalContent = content;

                        if (!sessionKey) {
                            // Session key еще не готов
                            console.log('[E2EE] Session key not ready for old message:', m.id);
                            content = 'Инициализация шифрования...';
                        } else {
                            // Пытаемся дешифровать
                            try {
                                const encryptedData = JSON.parse(content);
                                content = await decryptMessage(sessionKey, encryptedData);
                                originalContent = null; // Если расшифровано успешно, не сохраняем оригинал
                                console.log('[E2EE] ✓ Old message', m.id, 'decrypted');
                            } catch (e) {
                                const participants = selectedChat?.participants || [];
                                const isGroupChat = participants.length > 2;

                                if (e.name === 'OperationError') {
                                    // Ключ не подходит
                                    console.warn('[E2EE] Old message encrypted with different key:', m.id);
                                    content = isGroupChat
                                        ? 'Зашифровано устаревшим ключом группы'
                                        : 'Зашифровано другим ключом';
                                    originalContent = null;
                                } else {
                                    console.error('[E2EE] Unexpected error decrypting old message:', e);
                                    content = 'Ошибка дешифрования';
                                    originalContent = null;
                                }
                            }
                        }
                    }

                    return { ...m, content, originalContent };
                }));

                setMessages(prev => {
                    const existingIds = new Set(prev.map(m => m.id));
                    const newMessages = decryptedOlderMessages.filter( m => !existingIds.has(m.id));
                    return [...newMessages, ...prev];
                });
                setCurrentPage(nextPage);

                // ИСПРАВЛЕНИЕ: Используем более надежный метод восстановления позиции
                requestAnimationFrame(() => {
                    requestAnimationFrame(() => {
                        if (messagesContainerRef.current) {
                            const heightDiff = messagesContainerRef.current.scrollHeight - prevScrollHeight;
                            messagesContainerRef.current.scrollTop = prevScrollTop + heightDiff;
                        }
                    });
                });
            }
        } catch (error) {
            console.error('Ошибка загрузки старых сообщений:', error);
        } finally {
            setIsLoadingOlderMessages(false);
        }
    }, [selectedChat, isLoadingOlderMessages, hasMoreMessages, isLoadingMessages, currentPage, PAGE_SIZE, sessionKey]);

    // Обработчик скролла для lazy load
    const handleScroll = useCallback(() => {
        const container = messagesContainerRef.current;
        if (!container || isLoadingOlderMessages || !hasMoreMessages || isLoadingMessages) return;

        // Обновляем флаг автопрокрутки: если пользователь внизу чата, включаем автопрокрутку
        const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
        shouldAutoScrollRef.current = distanceFromBottom <= BOTTOM_STICKY_THRESHOLD;

        // Загружаем старые сообщения если прокрутили вверх
        if (container.scrollTop < TOP_LAZY_LOAD_THRESHOLD) {
            // Находим первый видимый элемент для восстановления позиции
            for (let i = 0; i < messages.length; i++) {
                const msg = messages[i];
                const el = messageRefs.current[msg.id];
                if (el) {
                    const rect = el.getBoundingClientRect();
                    const containerRect = container.getBoundingClientRect();
                    if (rect.bottom > containerRect.top) {
                        firstVisibleMessageIdRef.current = msg.id;
                        break;
                    }
                }
            }
            console.log('Triggering lazy load, first visible message:', firstVisibleMessageIdRef.current);
            loadOlderMessages();
        }
    }, [isLoadingOlderMessages, hasMoreMessages, isLoadingMessages, loadOlderMessages, messages]);

    // Периодический запрос статуса участников чата
    useEffect(() => {
        if (!selectedChat || !selectedChat.id) return;
        const interval = setInterval(async () => {
            try {
                // Предполагается, что chatService.getChatInfo возвращает актуальную информацию о чате и участниках
                const info = await chatService.getChatInfo(selectedChat.id);
                if (info && info.participants) {
                    setChatInfo(prevInfo => {
                        // Обновляем только статусы участников, не трогая остальные поля
                        if (!prevInfo) return info;
                        const updatedParticipants = prevInfo.participants.map(participant => {
                            const fresh = info.participants.find(p => p.id === participant.id);
                            return fresh ? { ...participant, isOnline: fresh.isOnline, lastSeen: fresh.lastSeen } : participant;
                        });
                        return { ...prevInfo, participants: updatedParticipants };
                    });
                }
            } catch (e) {
                console.warn('Ошибка при обновлении статуса участников:', e);
            }
        }, 30000); // 30 секунд
        return () => clearInterval(interval);
    }, [selectedChat]);

    // Сброс e2eeReady при смене чата
    useEffect(() => {
        if (selectedChat && selectedChat.id !== lastLoadedChatId.current) {
            setE2eeReady(false);
        }
    }, [selectedChat]);

    // После успешной инициализации sessionKey и ratchetManager
    useEffect(() => {
        if (sessionKey && ratchetReady) {
            setE2eeReady(true);
        }
    }, [sessionKey, ratchetReady]);

    // Очищаем и перезагружаем при смене partnerId
    useEffect(() => {
        if (!selectedChat) return;

        // Если partnerId изменился и у нас уже есть загруженные сообщения,
        // нужно очистить sessionKey и перезагрузить
        const participants = selectedChat.participants || [];
        const isGroupChat = participants.length > 2;

        // Для групповых чатов partnerId должен быть chatId
        const expectedPartnerId = isGroupChat ? selectedChat.id : partnerId;

        if (partnerId !== expectedPartnerId && messages.length > 0) {
            console.log('[E2EE] Partner ID mismatch detected, clearing session key and reloading');
            console.log('[E2EE] Expected:', expectedPartnerId, 'Got:', partnerId, 'IsGroup:', isGroupChat);
            // Очищаем старый ключ
            setSessionKey(null);
            setE2eeReady(false);
            hasReloadedAfterKeyRef.current = false;
        }
    }, [partnerId, selectedChat, messages.length]);

    // Очистка таймаутов при размонтировании
    useEffect(() => {
        const scrollTimeout = scrollTimeoutRef.current;
        const loadingTimeout = loadingTimeoutRef.current;

        return () => {
            if (scrollTimeout) {
                clearTimeout(scrollTimeout);
            }
            if (loadingTimeout) {
                clearTimeout(loadingTimeout);
            }
        };
    }, []);

    // Подписываемся на события чата
    useEffect(() => {
        // Обработчик событий чата от WebSocket/Kafka
        const handleChatEvent = (event) => {
            console.log('Received chat event:', event);

            if (!selectedChat || event.chatId !== selectedChat.id) {
                return; // Событие не для текущего чата
            }

            switch (event.eventType) {
                default:
                    console.log('Unhandled chat event type:', event.eventType);
            }
        };

        // Обработчик всех WebSocket сообщений
        const handleWebSocketMessage = (message) => {
            console.log('Received chat message in ChatWindow:', message);

            // Обрабатываем изменение онлайн-статуса пользователей
            if (message.type === 'USER_ONLINE' || message.type === 'USER_OFFLINE') {
                const isOnline = message.type === 'USER_ONLINE';
                const userId = message.userId;

                console.log(`[ONLINE-STATUS] User ${message.username} (ID: ${userId}) is now ${isOnline ? 'ONLINE' : 'OFFLINE'}`);
                console.log(`[ONLINE-STATUS] lastSeen from message:`, message.lastSeen);

                // Обновляем информацию о чате, чтобы получить актуальный статус участников
                setChatInfo(prevInfo => {
                    if (!prevInfo || !prevInfo.participants) return prevInfo;

                    // Обновляем статус конкретного пользователя
                    const updatedParticipants = prevInfo.participants.map(participant => {
                        if (participant.id === userId) {
                            return {
                                ...participant,
                                isOnline: isOnline,
                                lastSeen: isOnline ? null : (message.lastSeen || new Date().toISOString())
                            };
                        }
                        return participant;
                    });

                    const updatedInfo = {
                        ...prevInfo,
                        participants: updatedParticipants
                    };

                    // Уведомляем родительский компонент об обновлении АСИНХРОННО
                    // чтобы избежать ошибки React о setState во время рендеринга другого компонента
                    Promise.resolve().then(() => {
                        if (onChatUpdate) {
                            onChatUpdate(updatedInfo);
                        }
                    });

                    return updatedInfo;
                });

                return;
            }

            // Обрабатываем уведомления о прочтении сообщений
            if (message.type === 'MESSAGE_READ') {


                const incomingMessageId = message.messageId ?? message.id ?? message.message_id;


                // ИСПРАВЛЕНИЕ: Обновляем readCount только если текущий пользователь - автор сообщения
                if (message.senderId === getCurrentUserId()) {


                    const msgIdKey = String(incomingMessageId);
                    let applied = false;

                    setMessages(prev => {
                        // ОТЛАДКА: Выводим все id сообщений в state
                        console.log('[MESSAGE_READ] All message IDs in state:', prev.map(m => ({
                            id: m.id,
                            normalized: String(m.id ?? m.messageId ?? m.message_id),
                            content: typeof m.content === 'string' ? m.content.substring(0, 30) : '[Object]',
                            sender: m.sender?.id
                        })));
                        console.log('[MESSAGE_READ] Looking for normalized key:', msgIdKey);

                        const updated = prev.map(msg => {
                            // Нормализуем id для сравнения: используем String для устойчивости к типам
                            const msgIdNormalized = String(msg.id ?? msg.messageId ?? msg.message_id);


                            // используем нормализованное сравнение идентификаторов
                            if (msgIdNormalized === msgIdKey) {
                                applied = true;
                                const newReadCount = (msg.readCount || 0) + 1;
                                console.log('[MESSAGE_READ] Updating message:', msg.id, 'readCount:', (msg.readCount || 0), '->', newReadCount);
                                console.log('[MESSAGE_READ] Reader:', message.readerUsername);
                                return {
                                    ...msg,
                                    readCount: newReadCount,
                                    lastReadBy: message.readerUsername
                                };
                            }
                            return msg;
                        });

                        if (applied) {
                            console.log('[MESSAGE_READ] State updated successfully!');
                        }

                        return updated;
                    });

                    // Если пока нет сообщения с этим id (например, оптимистичное ещё не заменено)
                    if (!applied) {
                        const current = pendingReadMapRef.current.get(msgIdKey) || 0;
                        pendingReadMapRef.current.set(msgIdKey, current + 1);
                        console.log('[MESSAGE_READ] Stored pending read increment for messageId=', msgIdKey, ' -> ', current + 1);
                        console.log('[MESSAGE_READ] Message not found in state! Check if it was loaded or has different ID');
                    }
                } else {
                    console.log('[MESSAGE_READ] Not my message (senderId:', message.senderId, '!==', getCurrentUserId(), ') - ignoring');
                }
                return;
            }

            // Обрабатываем только сообщения чата
            if (message.type === 'CHAT_MESSAGE' && message.chatId === selectedChat?.id) {
                console.log('[CHAT_MESSAGE] Received:', message);

                // Дешифруем сообщение асинхронно и обновляем state
                (async () => {
                    let decryptedContent = message.content || '';

                    // ИСПРАВЛЕНИЕ: Проверяем, является ли контент зашифрованным JSON
                    let isEncryptedMessage = false;
                    try {
                        if (message.content && typeof message.content === 'string') {
                            const parsed = JSON.parse(message.content);
                            isEncryptedMessage = parsed.iv && parsed.ciphertext;
                        }
                    } catch (e) {
                        // Не JSON или не зашифрованное - это нормально
                        isEncryptedMessage = false;
                    }

                    // Пытаемся дешифровать только если это зашифрованное сообщение
                    if (isEncryptedMessage) {
                        const currentSessionKey = sessionKeyRef.current;

                        // ВАЖНО: Проверяем готовность ключа
                        if (!currentSessionKey || !e2eeReady || !ratchetReady) {
                            console.warn('[E2EE] Received encrypted message but key not ready yet - showing placeholder');

                            // НЕ ВЫХОДИМ без обновления! Показываем placeholder и обновляем state
                            // чтобы заменить оптимистичное сообщение на реальное с сервера
                            decryptedContent = 'Инициализация шифрования...';
                        } else {
                            try {
                                const encryptedData = JSON.parse(message.content);
                                decryptedContent = await decryptMessage(currentSessionKey, encryptedData);
                                console.log('[E2EE] ✓ WebSocket message decrypted:', decryptedContent.substring(0, 50));
                            } catch (decryptError) {
                                console.error('[E2EE] Failed to decrypt WebSocket message:', decryptError);

                                if (decryptError.name === 'OperationError') {
                                    const participants = selectedChat?.participants || [];
                                    const isGroupChat = participants.length > 2;
                                    decryptedContent = isGroupChat
                                        ? 'Зашифровано устаревшим ключом группы'
                                        : 'Зашифровано другим ключом';
                                } else {
                                    decryptedContent = 'Ошибка дешифрования';
                                }
                            }
                        }
                    }
                    // Если не зашифровано, используем как есть (decryptedContent уже = message.content)

                    // Теперь обновляем state с дешифрованным содержимым
                    setMessages(prev => {
                        const senderId = message.senderId || message.userId;
                        const incomingId = message.id ?? message.messageId ?? message.message_id ?? `ws-${Date.now()}`;
                        const incomingIdKey = String(incomingId);
                        console.log('[CHAT_MESSAGE] Normalized incoming ID:', incomingIdKey);
                        console.log('[CHAT_MESSAGE] SenderId:', senderId, 'Current user:', getCurrentUserId());
                        const isRealId = !String(incomingId).startsWith('ws-') && !String(incomingId).startsWith('temp-');
                        const withoutOptimisticAndTemp = prev.filter(msg => {
                            // Удаляем оптимистичные сообщения того же отправителя и с тем же расшифрованным контентом
                            if (msg.isOptimistic && (msg.sender?.id === senderId || String(msg.sender?.id) === String(senderId))) {
                                // Сравниваем по расшифрованному контенту
                                const sameContent = msg.content && decryptedContent && msg.content === decryptedContent;
                                const sameFile = msg.fileUrl && message.fileUrl && msg.fileUrl === message.fileUrl;
                                if (sameContent || sameFile) {
                                    return false;
                                }
                            }
                            // Если пришел реальный id, заменяем временный ws-... id
                            if (isRealId && String(msg.id).startsWith('ws-')) {
                                const msgSenderId = msg.sender?.id || msg.senderId;
                                const sameContent = msg.content === decryptedContent;
                                const sameSender = String(msgSenderId) === String(senderId);
                                if (sameContent && sameSender) {
                                    console.log('[CHAT_MESSAGE] Replacing temporary message with real ID:', {
                                        tempId: msg.id,
                                        realId: incomingIdKey,
                                        content: msg.content
                                    });
                                    return false;
                                }
                            }
                            return true;
                        });
                        if (!message.content && !message.fileUrl) {
                            console.log('[CHAT_MESSAGE] Ignoring empty message:', message);
                            return withoutOptimisticAndTemp;
                        }
                        const exists = withoutOptimisticAndTemp.some(msg => {
                            const msgIdNormalized = String(msg.id ?? msg.messageId ?? msg.message_id);
                            if (msgIdNormalized === incomingIdKey && !msgIdNormalized.startsWith('ws-') && !msgIdNormalized.startsWith('temp-')) {
                                return true;
                            }
                            if (message.fileUrl && msg.fileUrl === message.fileUrl) return true;
                            return false;
                        });
                        if (exists) {
                            console.log('[CHAT_MESSAGE] Message with same real ID already exists, skipping duplicate:', message);
                            return withoutOptimisticAndTemp;
                        }
                        const newMessage = {
                            id: incomingId,
                            content: decryptedContent, // Используем дешифрованное содержимое или placeholder
                            // Сохраняем оригинальный зашифрованный контент для дешифрования когда ключ будет готов
                            originalContent: isEncryptedMessage ? message.content : null,
                            chatId: message.chatId,
                            messageType: message.messageType || 'TEXT',
                            createdAt: message.timestamp || message.createdAt || new Date().toISOString(),
                            sender: {
                                id: senderId,
                                username: message.senderUsername || message.username || 'Пользователь'
                            },
                            fileUrl: message.fileUrl || null,
                            fileName: message.fileName || null,
                            fileSize: message.fileSize || null,
                            mimeType: message.mimeType || null,
                            thumbnailUrl: message.thumbnailUrl || null
                        };
                        const inc = pendingReadMapRef.current.get(incomingIdKey);
                        if (inc) {
                            newMessage.readCount = (newMessage.readCount || 0) + inc;
                            pendingReadMapRef.current.delete(incomingIdKey);
                            console.log('[CHAT_MESSAGE] Applied pending read increments to new message id=', newMessage.id, ' +', inc);
                        }
                        console.log('[CHAT_MESSAGE] Adding new message:', {
                            id: newMessage.id,
                            content: newMessage.content,
                            readCount: newMessage.readCount,
                            hasPendingReads: !!inc
                        });
                        return [...withoutOptimisticAndTemp, newMessage];
                    });
                })();
            }
        }
        // Подписываемся на события чата
        const unsubscribeChatEvents = chatService.onChatEvent(handleChatEvent);
        // Подписываемся на все WebSocket сообщения
        const unsubscribeMessages = chatService.onMessage(handleWebSocketMessage);
        return () => {
            if (typeof unsubscribeMessages === 'function') unsubscribeMessages();
            // Отписываемся при размонтировании компонента
            if (typeof unsubscribeChatEvents === 'function') unsubscribeChatEvents();
        };
    }, [selectedChat?.id, selectedChat, loadChatInfo, getCurrentUserId, e2eeReady, ratchetReady, onChatUpdate]);

    // После рендера сообщений восстанавливаем scroll к первому видимому сообщению
    useLayoutEffect(() => {
        // ИСПРАВЛЕНИЕ: Не прокручиваем если идет загрузка старых сообщений
        if (isLoadingOlderMessages) {
            return;
        }

        // После рендера сообщений восстанавлием scroll к первому видимому сообщению
        if (firstVisibleMessageIdRef.current && messagesContainerRef.current) {
            const el = messageRefs.current[firstVisibleMessageIdRef.current];
            if (el) {
                const container = messagesContainerRef.current;
                container.scrollTop = el.offsetTop - container.offsetTop;
                console.log('Restored scroll to first visible message:', firstVisibleMessageIdRef.current);
            }
            firstVisibleMessageIdRef.current = null;
            return;
        }

        // ИСПРАВЛЕНИЕ: Прокручиваем вниз только если пользователь находится внизу чата
        if (shouldAutoScrollRef.current && !isLoadingOlderMessages) {
            scrollToBottom();
        }
    }, [messages, scrollToBottom, isLoadingOlderMessages]);

    // Функция отправки сообщения
    const handleSendMessage = async (e) => {
        e.preventDefault();
        if (newMessage.trim() && selectedChat) {
            // Проверяем состояние WebSocket соединения
            if (!chatService.isConnected()) {
                console.error('WebSocket is not connected');
                alert('Нет соединения с сервером. Попробуйте позже.');
                return;
            }

            // Проверяем готовность E2EE
            if (!sessionKey) {
                console.error('E2EE not ready - session key not established');
                alert('Шифрование не готово. Подождите инициализации E2EE.');
                return;
            }

            try {
                // Шифруем сообщение через X3DH session key с AES-GCM
                console.log('[E2EE] Encrypting message with X3DH + AES-GCM');
                const encrypted = await encryptMessage(sessionKey, newMessage.trim());
                const encryptedContent = JSON.stringify(encrypted);
                console.log('[E2EE] ✓ Message encrypted successfully');

                // Отправляем зашифрованное сообщение через WebSocket
                chatService.sendChatMessage(encryptedContent, selectedChat.id);

                // Добавляем локально оптимистичное обновление (дешифрованное для отображения)
                const optimisticMessage = {
                    id: `temp-${Date.now()}`,
                    content: newMessage.trim(), // Локально показываем расшифрованное
                    chatId: selectedChat.id,
                    messageType: 'TEXT',
                    sentAt: new Date().toISOString(),
                    sender: {
                        id: getCurrentUserId(),
                        username: 'Вы'
                    },
                    isOptimistic: true // Флаг оптимистичного обновления
                };

                setMessages(prev => [...prev, optimisticMessage]);
                setNewMessage('');
            } catch (error) {
                console.error('Ошибка отправки сообщения:', error);
                alert('Ошибка отправки сообщения: ' + error.message);
            }
        }
    };

    const handleAddParticipants = async (selectedUsers) => {
        if (!selectedChat || selectedChat.chatType !== 'GROUP') return;

        try {
            const userIds = selectedUsers.map(user => user.id);
            const updatedChat = await chatService.addParticipants(selectedChat.id, userIds);
            setChatInfo(updatedChat);
            // Уведомляем родительский компонент об обновлении АСИНХРОННО
            if (onChatUpdate) {
                Promise.resolve().then(() => onChatUpdate(updatedChat));
            }

            // Показываем системное сообщение локально
            const systemMessage = {
                id: Date.now(),
                content: `Добавлены новые участники: ${selectedUsers.map(u => u.username).join(', ')}`,
                messageType: 'SYSTEM',
                createdAt: new Date().toISOString(),
                sender: null
            };
            setMessages(prev => [...prev, systemMessage]);
        } catch (error) {
            console.error('Ошибка добавления участников:', error);
            alert('Ошибка добавления участников');
        }
    };

    const handleLeaveChat = async () => {
        if (!selectedChat || selectedChat.chatType !== 'GROUP') return;

        if (window.confirm('Вы уверены, что хотите покинуть этот чат?')) {
            try {
                await chatService.leaveChat(selectedChat.id);
                // Уведомляем родительский компонент об обновлении АСИНХРОННО
                if (onChatUpdate) {
                    Promise.resolve().then(() => onChatUpdate(null));
                }
            } catch (error) {
                console.error('Ошибка при выходе из чата:', error);
                alert('Ошибка при выходе из чата');
            }
        }
    };

    const getChatTitle = () => {
        if (!selectedChat) return 'Выберите чат';

        if (selectedChat.chatType === 'GROUP') {
            return selectedChat.chatName || 'Групповой чат';
        } else {
            // Для приватного чата показываем имя собеседника
            const participants = chatInfo?.participants || selectedChat.participants || [];
            const otherParticipant = participants.find(p => p.id !== getCurrentUserId());
            return otherParticipant ? otherParticipant.username : 'Приватный чат';
        }
    };

    // Получить собеседника для приватного чата
    const getOtherParticipant = () => {
        if (!selectedChat || selectedChat.chatType === 'GROUP') return null;
        const participants = chatInfo?.participants || selectedChat.participants || [];
        return participants.find(p => p.id !== getCurrentUserId());
    };

    // Форматирование времени последнего посещения
    const formatLastSeen = (lastSeen) => {
        if (!lastSeen) {
            console.log('[formatLastSeen] No lastSeen provided');
            return 'давно не был(а) в сети';
        }

        try {
            console.log('[formatLastSeen] Raw lastSeen value:', lastSeen, 'Type:', typeof lastSeen);

            let lastSeenDate;

            if (typeof lastSeen === 'string') {
                // формат LocalDateTime от Java (2025-11-09T15:30:25),
                if (lastSeen.match(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/)) {
                    lastSeenDate = new Date(lastSeen);
                    console.log('[formatLastSeen] Parsed as LocalDateTime:', lastSeenDate);
                } else {
                    lastSeenDate = new Date(lastSeen);
                    console.log('[formatLastSeen] Parsed as standard date:', lastSeenDate);
                }
            } else if (lastSeen instanceof Date) {
                lastSeenDate = lastSeen;
                console.log('[formatLastSeen] Already a Date object:', lastSeenDate);
            } else if (Array.isArray(lastSeen)) {
                console.log('[formatLastSeen] LocalDateTime array format:', lastSeen);

                if (lastSeen.length >= 6) {
                    const [year, month, day, hour, minute, second] = lastSeen;
                    // Месяц в JavaScript начинается с 0, а в Java с 1
                    lastSeenDate = new Date(year, month - 1, day, hour, minute, second);
                    console.log('[formatLastSeen] Converted from array to date:', lastSeenDate);
                } else {
                    console.error('[formatLastSeen] Invalid array length:', lastSeen.length);
                    return 'давно не был(а) в сети';
                }
            } else if (typeof lastSeen === 'object' && lastSeen !== null) {
                // Если это объект с полями (например, от Java LocalDateTime)
                console.warn('[formatLastSeen] Unknown object format:', lastSeen);
                return 'давно не был(а) в сети';
            } else {
                console.warn('[formatLastSeen] Unknown lastSeen format:', lastSeen);
                return 'давно не был(а) в сети';
            }

            if (isNaN(lastSeenDate.getTime())) {
                console.error('[formatLastSeen] Invalid date after parsing:', lastSeen);
                return 'давно не был(а) в сети';
            }

            const now = new Date();
            const diffMs = now - lastSeenDate;
            const diffMinutes = Math.floor(diffMs / 60000);
            const diffHours = Math.floor(diffMs / 3600000);
            const diffDays = Math.floor(diffMs / 86400000);

            console.log('[formatLastSeen] Time diff:', {
                diffMinutes,
                diffHours,
                diffDays,
                lastSeenDate: lastSeenDate.toISOString(),
                now: now.toISOString()
            });

            if (diffMinutes < 1) {
                return 'только что';
            } else if (diffMinutes < 60) {
                return `${diffMinutes} ${diffMinutes === 1 ? 'минуту' : diffMinutes < 5 ? 'минуты' : 'минут'} назад`;
            } else if (diffHours < 24) {
                return `${diffHours} ${diffHours === 1 ? 'час' : diffHours < 5 ? 'часа' : 'часов'} назад`;
            } else if (diffDays === 1) {
                return 'вчера в ' + lastSeenDate.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
            } else if (diffDays < 7) {
                return `${diffDays} ${diffDays === 1 ? 'день' : diffDays < 5 ? 'дня' : 'дней'} назад`;
            } else {
                return lastSeenDate.toLocaleDateString('ru-RU', {
                    day: 'numeric',
                    month: 'long',
                    hour: '2-digit',
                    minute: '2-digit'
                });
            }
        } catch (error) {
            console.error('[formatLastSeen] Error formatting last seen:', error, 'Value:', lastSeen);
            return 'давно не был(а) в сети';
        }
    };

    const formatTime = (timestamp) => {
        if (!timestamp) return '';
        try {
            const date = new Date(timestamp);
            if (isNaN(date.getTime())) {
                return '';
            }
            return date.toLocaleTimeString('ru-RU', {
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (error) {
            console.error('Error formatting time:', error, timestamp);
            return '';
        }
    };

    const formatFileSize = (bytes) => {
        if (!bytes) return '0 B';

        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(1024));

        if (i === 0) return bytes + ' ' + sizes[i];

        return (bytes / Math.pow(1024, i)).toFixed(2) + ' ' + sizes[i];
    };

    // Drag and drop handler
    const { isDragging, dragHandlers } = useDragAndDrop((file) => {
        // Передаем файл напрямую в FileUpload через ref
        if (fileUploadRef.current) {
            fileUploadRef.current.selectFile(file);
        }
    });

    // Добавляем логирование для отладки
    useEffect(() => {
        console.log('isDragging state changed:', isDragging);
    }, [isDragging]);

    // Запуск анимации перехода между чатами
    useEffect(() => {
        if (!selectedChat) return;
        setChatTransitionStage('fadeOut');
        const timer = setTimeout(() => {
            setChatTransitionStage('fadeIn');
        }, 250);
        return () => clearTimeout(timer);
    }, [selectedChat]);

    // Новый эффект для отслеживания загрузки изображений
useEffect(() => {
    if (!messagesContainerRef.current) return;
    const container = messagesContainerRef.current;
    const images = container.querySelectorAll('img');
    let loadedCount = 0;
    if (images.length === 0) {
        if (shouldAutoScrollRef.current) scrollToBottom(false);
        return;
    }
    const handleImgLoad = () => {
        loadedCount++;
        if (loadedCount === images.length) {
            if (shouldAutoScrollRef.current) scrollToBottom(false);
        }
    };
    images.forEach(img => {
        if (img.complete) {
            loadedCount++;
        } else {
            img.addEventListener('load', handleImgLoad);
            img.addEventListener('error', handleImgLoad);
        }
    });
    if (loadedCount === images.length) {
        if (shouldAutoScrollRef.current) scrollToBottom(false);
    }
    return () => {
        images.forEach(img => {
            img.removeEventListener('load', handleImgLoad);
            img.removeEventListener('error', handleImgLoad);
        });
    };
}, [messages.length, selectedChat?.id, scrollToBottom]);

    // Функция для рендеринга контента сообщения (с дешифровкой если нужно)
    const renderMessageContent = (message) => {
        let content = message.content;

        // Если content - строка, просто отображаем
        if (typeof content === 'string') {
            // Если это строка с JSON-шифром, пробуем дешифровать
            if (content.includes('iv') && content.includes('ciphertext')) {
                try {
                    const encrypted = JSON.parse(content);
                    if (encrypted.iv && encrypted.ciphertext) {
                        return <DecryptedContent encrypted={encrypted} sessionKey={sessionKey} />;
                    }
                } catch (e) {
                    // Не JSON, показываем как есть
                }
            }
            return content;
        }
        // Если content - объект, показываем заглушку
        return '[Зашифровано]';
    };

    // Компонент для дешифрования контента
    const DecryptedContent = ({ encrypted, sessionKey }) => {
        const [decrypted, setDecrypted] = useState('[Расшифровка...]');
        useEffect(() => {
            async function decrypt() {
                if (!sessionKey) {
                    setDecrypted('[Ожидание ключа]');
                    return;
                }
                try {
                    const text = await decryptMessage(sessionKey, encrypted);
                    setDecrypted(text);
                } catch (error) {
                    console.error('[E2EE] Failed to decrypt message:', error);
                    setDecrypted('[Невозможно расшифровать - старое сообщение]');
                }
            }
            decrypt();
        }, [encrypted, sessionKey]);

        return <>{decrypted}</>;
    };

    if (!selectedChat) {
        return (
            <div className="flex-1 flex items-center justify-center bg-gray-50" style={{backgroundImage: "url('/chat_background_n.png')", backgroundSize: '400px', backgroundRepeat: 'repeat', backgroundPosition: 'center'}}>
                <div className="text-center px-6 py-5" style={{border: '2px solid #F5F5DC', borderRadius: '18px', backgroundColor: '#F5F5DC', boxShadow: '0 2px 12px rgba(178,34,34,0.08)', display: 'inline-block'}}>
                    <div className="text-lg mb-2" style={{color: '#B22222'}}>Выберите чат для начала общения</div>
                    <div style={{color: '#B22222'}}>Или найдите пользователей для создания нового чата</div>
                </div>
            </div>
        );
    }

    return (
        <>
            {/* Весь основной JSX внутри одного фрагмента */}
            <div className="flex-1 flex flex-col" {...dragHandlers} style={{ position: 'relative', backgroundColor: 'rgb(93 10 22 / 88%)' }}>
                {/* Заголовок чата */}
                <div className="border-b" style={{ padding: '14px', backgroundColor: '#8B1A1A', borderColor: '#B22222' }}>
                    <div className="flex items-center justify-between">
                        <div className="flex flex-col">
                            <div className="flex items-center">
                                <h2 className="text-lg font-bold" style={{ color: '#F5F5DC', letterSpacing: '1px' }}>{getChatTitle()}</h2>
                            </div>
                            {selectedChat.chatType === 'GROUP' && chatInfo && (
                                <span className="text-sm mt-1" style={{ color: '#F5F5DC' }}>
                                    ({chatInfo.participants?.length || 0} участников)
                                </span>
                            )}
                            {/* Статус онлайн / последний визит для приватных чатов */}
                            {selectedChat.chatType !== 'GROUP' && (() => {
                                const otherParticipant = getOtherParticipant();
                                if (otherParticipant) {
                                    return (
                                        <>
                                            <div className="flex items-center mt-1">
                                                {otherParticipant.isOnline ? (
                                                    <>
                                                        <div className="w-2 h-2 rounded-full mr-2" style={{ backgroundColor: '#228B22' }}></div>
                                                        <span className="text-sm" style={{ color: '#228B22' }}>в сети</span>
                                                    </>
                                                ) : (
                                                    <span className="text-sm" style={{ color: '#F5F5DC' }}>
                                                        {otherParticipant.lastSeen
                                                            ? `был(а) ${formatLastSeen(otherParticipant.lastSeen)}`
                                                            : 'был(а) давно'}
                                                    </span>
                                                )}
                                            </div>
                                            {/* E2EE статус */}
                                            <div className="flex items-center mt-1">
                                                {ratchetReady ? (
                                                    <>
                                                        <span className="text-xs" style={{ color: '#90EE90' }}>Защищено (E2EE)</span>
                                                    </>
                                                ) : (
                                                    <span className="text-xs" style={{ color: '#FFA07A' }}>{keyStatus}</span>
                                                )}
                                            </div>
                                        </>
                                    );
                                }
                                return null;
                            })()}
                        </div>
                        <div className="flex items-center gap-2">
                            {selectedChat.chatType === 'GROUP' && (
                                <>
                                    <button
                                        onClick={() => setShowAddParticipants(true)}
                                        className="p-2 rounded-full"
                                        style={{ backgroundColor: '#F5F5DC', color: '#B22222' }}
                                        title="Добавить участников"
                                    >
                                        <img src={addfriend_icon} alt="" className="w-5 h-5 opacity-90 group-hover:opacity-100" draggable="false" />
                                    </button>
                                    <button
                                        onClick={handleLeaveChat}
                                        className="p-2 rounded-full"
                                        style={{ backgroundColor: '#F5F5DC', color: '#B22222' }}
                                        title="Покинуть чат"
                                    >
                                        Выйти
                                    </button>
                                </>
                            )}
                            <button
                                onClick={() => setShowChatInfo(!showChatInfo)}
                                className="p-2 rounded-full"
                                style={{ backgroundColor: '#F5F5DC', color: '#B22222' }}
                                title="Информация о чате"
                            >
                                <img src={info_icon} alt="" className="w-5 h-5 opacity-90 group-hover:opacity-100" draggable="false" />
                            </button>
                        </div>
                    </div>
                </div>

                {/* Информация о чате */}
                {showChatInfo && chatInfo && (
                    <div className="border-b p-4" style={{ backgroundColor: '#FFF8F0', borderColor: '#B22222' }}>
                        <h3 className="font-bold mb-2" style={{ color: '#B22222' }}>Участники чата:</h3>
                        <div className="flex flex-wrap gap-2">
                            {chatInfo.participants?.map(participant => (
                                <div key={participant.id} className="flex items-center rounded-full px-3 py-1 border" style={{ backgroundColor: '#F5F5DC', borderColor: '#B22222' }}>
                                    <div className="w-2 h-2 rounded-full mr-2" style={{ backgroundColor: participant.isOnline ? '#228B22' : '#B22222' }}></div>
                                    <span className="text-sm" style={{ color: '#222' }}>{participant.username}</span>
                                </div>
                            ))}
                        </div>
                        {selectedChat.chatDescription && (
                            <div className="mt-3">
                                <div className="text-sm font-bold" style={{ color: '#B22222' }}>Описание:</div>
                                <div className="text-sm" style={{ color: '#444' }}>{selectedChat.chatDescription}</div>
                            </div>
                        )}
                    </div>
                )}

                {/* Список сообщений с анимацией перехода */}
                <div
                    className={`flex-1 overflow-y-auto p-4 relative chat-transition-anim enhanced-chat-scrollbar ${chatTransitionStage}`}
                    ref={messagesContainerRef}
                    onScroll={handleScroll}
                    style={{
                        backgroundImage: "url('/chat_background_n.png')",
                        backgroundSize: '400px',
                        backgroundRepeat: 'repeat',
                        backgroundPosition: 'center',
                    }}
                >
                    <div className="space-y-4" style={{ position: 'relative', zIndex: 2 }}>
                        {/* Сообщение если больше нет старых сообщений */}
                        {!hasMoreMessages && messages.length > 0 && !isLoadingMessages && (
                            <div className="flex justify-center py-2">
                                <div className="text-xs text-gray-400">
                                    Это начало переписки
                                </div>
                            </div>
                        )}

                        {messages && messages.map((message, idx) => (
                            <div
                                key={message.id || idx}
                                ref={el => { if (el) messageRefs.current[message.id] = el; }}
                                data-message-id={message.id}
                                className={`flex ${message.sender?.id === getCurrentUserId() ? 'justify-end' : ''}`}
                            >
                                <div
                                    className={`max-w-xs lg:max-w-md px-4 py-2 rounded-lg`}
                                    style={{
                                        backgroundColor: message.messageType === 'SYSTEM'
                                            ? '#FFDAB9'
                                            : message.sender?.id === getCurrentUserId()
                                                ? '#520808'
                                                : '#F5DEB3',


                                        color: message.messageType === 'SYSTEM'
                                            ? '#B22222' : message.sender?.id === getCurrentUserId()
                                                ?'#ffffff' : '#222',

                                        border: message.messageType === 'SYSTEM'
                                            ? '1px solid #B22222'
                                            : message.sender?.id === getCurrentUserId()
                                                ? 'none'
                                                : '1px solid #EAD6C4',
                                        boxShadow: '0 2px 8px rgba(178,34,34,0.08)'
                                    }}
                                >
                                    {message.sender && selectedChat.chatType === 'GROUP' && message.sender.id !== getCurrentUserId() && (
                                        <div className="text-xs font-medium mb-1 opacity-75">
                                            {message.sender.username}
                                        </div>
                                    )}
                                    {/* Рендеринг файлов */}
                                    {message.messageType === 'IMAGE' && message.fileUrl && (
                                        <div className="mb-2">
                                            <img
                                                src={chatService.getFileUrl(message.fileUrl)}
                                                alt={message.fileName || 'Image'}
                                                className="max-w-full rounded cursor-pointer hover:opacity-90"
                                                onClick={() => window.open(chatService.getFileUrl(message.fileUrl), '_blank')}
                                            />
                                        </div>
                                    )}
                                    {message.messageType === 'FILE' && message.fileUrl && (
                                        <div className="mb-2 p-3 bg-white bg-opacity-20 rounded">
                                            <a
                                                href={chatService.getFileUrl(message.fileUrl)}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                className="flex items-center hover:underline"
                                            >
                                                <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                                </svg>
                                                <div>
                                                    <div className="text-sm font-medium">{message.fileName || 'Файл'}</div>
                                                    {message.fileSize && (
                                                        <div className="text-xs opacity-75">
                                                            {formatFileSize(message.fileSize)}
                                                        </div>
                                                    )}
                                                </div>
                                            </a>
                                        </div>
                                    )}
                                    <div className="break-words">
                                        {renderMessageContent(message)}
                                    </div>
                                    <div className={`text-xs mt-1 flex items-center gap-1 ${
                                        message.messageType === 'SYSTEM' || message.sender?.id === getCurrentUserId()
                                            ? 'opacity-75'
                                            : 'opacity-60'
                                    }`}>
                                        <span>{formatTime(message.createdAt || message.sentAt)}</span>
                                        {message.isEdited && <span>(изм.)</span>}

                                        {/* Индикатор статуса прочитания для своих сообщений */}
                                        {message.sender?.id === getCurrentUserId() && message.messageType !== 'SYSTEM' && (
                                            <span className="ml-1" title={
                                                message.readCount > 0
                                                    ? `Прочитано ${message.readCount} ${message.readCount === 1 ? 'пользователем' : 'пользователями'}`
                                                    : 'Отправлено'
                                            }>
                                                {message.readCount > 0 ? (
                                                    // Двойная галочка (прочитано) - улучшенный дизайн
                                                    <svg className="w-4 h-4 inline" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24">
                                                        <path d="M5 13l4 4L19 7" strokeLinecap="round" strokeLinejoin="round"/>
                                                        <path d="M9 13l4 4L23 7" strokeLinecap="round" strokeLinejoin="round" opacity="0.9"/>
                                                    </svg>
                                                ) : (
                                                    // Одна галочка (отправлено) - улучшенный дизайн
                                                    <svg className="w-4 h-4 inline opacity-70" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24">
                                                        <path d="M5 13l4 4L19 7" strokeLinecap="round" strokeLinejoin="round"/>
                                                    </svg>
                                                )}
                                            </span>
                                        )}
                                    </div>
                                </div>
                            </div>
                        ))}
                        <div ref={messagesEndRef} />
                    </div>
                </div>

                {/* Форма отправки сообщений */}
                <div className="border-t p-4" style={{ backgroundColor: '#F5F5DC', borderColor: '#B22222' }}>
                    <form onSubmit={handleSendMessage} className="flex gap-2">
                        <FileUpload
                            ref={fileUploadRef}
                            chatId={selectedChat.id}
                            sessionKey={sessionKey}
                            onFileUploaded={(message) => {
                                // НЕ добавляем сообщение локально - оно придет через WebSocket
                                // Просто логируем успешную загрузку
                                console.log('File uploaded successfully, message will arrive via WebSocket:', message);
                            }}
                        />
                        <input
                            type="text"
                            value={newMessage}
                            onChange={(e) => setNewMessage(e.target.value)}
                            placeholder="Введите сообщение..."
                            className="flex-1 px-3 py-2 rounded-md focus:outline-none"
                            style={{ border: '1px solid #B22222', backgroundColor: '#FFF8F0', color: '#222' }}
                        />
                        <button
                            type="submit"
                            disabled={!newMessage.trim()}
                            className="px-6 py-2 rounded-md font-bold"
                            style={{ backgroundColor: newMessage.trim() ? '#B22222' : '#F5F5DC', color: newMessage.trim() ? '#F5F5DC' : '#B22222', border: 'none' }}
                        >
                            Отправить
                        </button>
                    </form>
                </div>

                {/* Оверлей для drag-and-drop - теперь покрывает весь компонент чата */}
                {isDragging && (
                    <div
                        className="absolute inset-0 flex items-center justify-center z-50"
                        style={{
                            backgroundColor: 'rgba(59, 130, 246, 0.15)',
                            backdropFilter: 'blur(8px)',
                            pointerEvents: 'none'
                        }}
                    >
                        <div className="bg-white rounded-2xl p-12 shadow-2xl border-4 border-dashed border-red-950 drag-pulse">
                            <div className="text-center">
                                <div className="mb-6 drag-bounce">
                                    <svg
                                        className="w-24 h-24 mx-auto text-red-500"
                                        fill="none"
                                        stroke="currentColor"
                                        viewBox="0 0 24 24"
                                    >
                                        <path
                                            strokeLinecap="round"
                                            strokeLinejoin="round"
                                            strokeWidth={2}
                                            d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
                                        />
                                    </svg>
                                </div>
                                <p className="text-2xl font-bold text-gray-800 mb-2">
                                    Отпустите файл здесь
                                </p>
                                <p className="text-sm text-brown-600">
                                    Поддерживаются изображения, документы и другие файлы
                                </p>
                            </div>
                        </div>
                    </div>
                )}
            </div>

            {/* Модальное окно добавления участников */}
            <UserSearchModal
                isOpen={showAddParticipants}
                onClose={() => setShowAddParticipants(false)}
                onUserSelect={handleAddParticipants}
                mode="multiple"
            />

        </>
    );
};

export default ChatWindow;
