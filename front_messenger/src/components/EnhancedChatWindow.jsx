import React, { useState, useEffect, useRef, useCallback, useLayoutEffect } from 'react';
import { useSelector } from 'react-redux';
import chatService from '../services/chatService';
import UserSearchModal from './UserSearchModal';
import FileUpload, { useDragAndDrop } from './FileUpload';
import './EnhancedChatWindow.css';

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
    const [unreadCount, setUnreadCount] = useState(0); // Счетчик непрочитанных сообщений
    const [onlineStatusVersion, setOnlineStatusVersion] = useState(0); // Для принудительного обновления UI
    const messagesEndRef = useRef(null);
    const messagesContainerRef = useRef(null);
    const lastLoadedChatId = useRef(null);
    const scrollTimeoutRef = useRef(null);
    const loadingTimeoutRef = useRef(null); // Для отмены загрузки при быстром переключении
    const isInitialScrollDone = useRef(false); // Ref вместо state для избежания лишних рендеров
    const markAsReadTimeoutRef = useRef(null); // Таймаут для отметки сообщений как прочитанных
    const { user } = useSelector(state => state.auth);
    const fileUploadRef = useRef(null);
    const pendingReadMapRef = useRef(new Map()); // Буфер для MESSAGE_READ до появления сообщения

    const addfriend_icon = 'addfriend.png';
    const info_icon = 'info.png';
    const PAGE_SIZE = 30;
    const TOP_LAZY_LOAD_THRESHOLD = 200;
    const BOTTOM_STICKY_THRESHOLD = 120;

    // Состояние для анимации перехода между чатами
    const [chatTransitionStage, setChatTransitionStage] = useState('idle'); // 'fadeOut' | 'fadeIn' | 'idle'
    const prevChatIdRef = useRef(null);

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

        try {
            setIsLoadingMessages(true);
            setCurrentPage(0);
            setHasMoreMessages(true);

            console.log(`Loading initial messages for chat ${selectedChat.id}`);

            const chatMessages = await chatService.getChatMessages(selectedChat.id, 0, PAGE_SIZE);
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
            // Применяем накопленные read-инкременты, если есть
            const withPendingReads = reversed.map(m => {
                const inc = pendingReadMapRef.current.get(String(m.id));
                return inc ? { ...m, readCount: (m.readCount || 0) + inc } : m;
            });
            setMessages(withPendingReads);
        } catch (error) {
            console.error('Ошибка загрузки сообщений:', error);
            setMessages([]);
        } finally {
            setIsLoadingMessages(false);
        }
    }, [selectedChat, isLoadingMessages, PAGE_SIZE]);

    // Функция загрузки информации о чате - обернута в useCallback
    const loadChatInfo = useCallback(async () => {
        if (!selectedChat) return;
        try {
            const info = await chatService.getChatInfo(selectedChat.id);


            // Приоритет отдаем статусам из selectedChat (они актуальнее, т.к. обновляются через WebSocket)
            const mergedInfo = {
                ...info,
                participants: info.participants?.map(serverParticipant => {
                    // Ищем соответствующего участника in selectedChat
                    const cachedParticipant = selectedChat.participants?.find(p => p.id === serverParticipant.id);

                    // Если есть кешированные данные, используем их статусы (они актуальнее)
                    if (cachedParticipant && (cachedParticipant.isOnline !== undefined || cachedParticipant.lastSeen)) {
                        return {
                            ...serverParticipant,
                            isOnline: cachedParticipant.isOnline !== undefined ? cachedParticipant.isOnline : serverParticipant.isOnline,
                            lastSeen: cachedParticipant.lastSeen || serverParticipant.lastSeen
                        };
                    }

                    return serverParticipant;
                })
            };

            setChatInfo(mergedInfo);
            // Уведомляем родительский компонент об обновлении чата
            if (onChatUpdate) onChatUpdate(mergedInfo);
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
            setUnreadCount(0);
            return;
        }

        // Загружаем сообщения только если чат изменился
        if (selectedChat.id !== lastLoadedChatId.current) {
            // Отменяем предыдущие загрузки
            if (loadingTimeoutRef.current) {
                clearTimeout(loadingTimeoutRef.current);
            }

            // СРАЗУ очищаем старые сообщения для предотвращения мерцания
            setMessages([]);
            isInitialScrollDone.current = false;
            lastLoadedChatId.current = selectedChat.id;

            // ИСПРАВЛЕНИЕ: Сначала используем данные из selectedChat (если они есть)
            // чтобы сохранить актуальные статусы участников
            if (selectedChat.participants) {
                setChatInfo(selectedChat);
            }

            // Запускаем загрузку
            loadChatMessages();
            // Загружаем полную информацию о чате, но не перезаписываем статусы
            loadChatInfo();

            // Загружаем количество непрочитанных сообщений
            loadUnreadCount();
        }
    }, [selectedChat, loadChatMessages, loadChatInfo]);

    // Функция загрузки количества непрочитанных сообщений
    const loadUnreadCount = useCallback(async () => {
        if (!selectedChat) return;

        try {
            const count = await chatService.getUnreadMessagesCount(selectedChat.id);
            setUnreadCount(count);
            console.log(`Unread messages count for chat ${selectedChat.id}: ${count}`);
        } catch (error) {
            console.error('Ошибка загрузки количества непрочитанных:', error);
        }
    }, [selectedChat]);

    // Функция отметки сообщений как прочитанных
    const markChatAsRead = useCallback(async () => {
        if (!selectedChat) return;

        try {
            await chatService.markAllChatMessagesAsRead(selectedChat.id);
            setUnreadCount(0);
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

            // ИСПРАВЛЕНИЕ: Блокируем автопрокрутку вниз при загрузке старых сообщений
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
                setMessages(prev => {
                    const existingIds = new Set(prev.map(m => m.id));
                    const newMessages = reversedOlderMessages.filter(m => !existingIds.has(m.id));
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
    }, [selectedChat, isLoadingOlderMessages, hasMoreMessages, isLoadingMessages, currentPage, PAGE_SIZE]);

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

    // Очистка таймаутов при размонтировании
    useEffect(() => {
        return () => {
            if (scrollTimeoutRef.current) {
                clearTimeout(scrollTimeoutRef.current);
            }
            if (loadingTimeoutRef.current) {
                clearTimeout(loadingTimeoutRef.current);
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
                                // Используем lastSeen из сообщения (он уже актуальный из БД)
                                lastSeen: message.lastSeen || (isOnline ? null : new Date().toISOString())
                            };
                        }
                        return participant;
                    });

                    const updatedInfo = {
                        ...prevInfo,
                        participants: updatedParticipants
                    };

                    // Уведомляем родительский компонент об обновлении
                    if (onChatUpdate) {
                        onChatUpdate(updatedInfo);
                    }

                    return updatedInfo;
                });

                // Принудительно обновляем UI для перерисовки времени
                setOnlineStatusVersion(prev => prev + 1);

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
                            content: m.content?.substring(0, 30),
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

                setMessages(prev => {
                    const senderId = message.senderId || message.userId;

                    // Нормализуем входящий id — поддерживаем несколько вариантов, которые может присылать сервер
                    const incomingId = message.id ?? message.messageId ?? message.message_id ?? `ws-${Date.now()}`;
                    const incomingIdKey = String(incomingId);

                    console.log('[CHAT_MESSAGE] Normalized incoming ID:', incomingIdKey);
                    console.log('[CHAT_MESSAGE] SenderId:', senderId, 'Current user:', getCurrentUserId());

                    // ИСПРАВЛЕНИЕ: Если приходит сообщение с реальным ID (не ws-*), ищем и удаляем временные сообщения
                    const isRealId = !String(incomingId).startsWith('ws-') && !String(incomingId).startsWith('temp-');

                    const withoutOptimisticAndTemp = prev.filter(msg => {
                        // Удаляем оптимистичные сообщения того же отправителя и с тем же контентом / файлом
                        if (msg.isOptimistic && (msg.sender?.id === senderId || String(msg.sender?.id) === String(senderId))) {
                            const sameContent = msg.content && message.content && msg.content === message.content;
                            const sameFile = msg.fileUrl && message.fileUrl && msg.fileUrl === message.fileUrl;
                            if (sameContent || sameFile) {
                                return false;
                            }
                        }

                        // НОВОЕ: Если приходит сообщение с реальным ID, удаляем временные ws-* с тем же контентом
                        if (isRealId && String(msg.id).startsWith('ws-')) {
                            const msgSenderId = msg.sender?.id || msg.senderId;
                            const sameContent = msg.content === message.content;
                            const sameSender = String(msgSenderId) === String(senderId);

                            if (sameContent && sameSender) {
                                console.log('[CHAT_MESSAGE] Replacing temporary message with real ID:', {
                                    tempId: msg.id,
                                    realId: incomingIdKey,
                                    content: msg.content
                                });
                                return false; // Удаляем временное сообщение
                            }
                        }

                        return true;
                    });

                    if (!message.content && !message.fileUrl) {
                        console.log('[CHAT_MESSAGE] Ignoring empty message:', message);
                        return withoutOptimisticAndTemp;
                    }

                    // Проверяем, не существует ли уже сообщение с ТАКИМ ЖЕ реальным ID
                    const exists = withoutOptimisticAndTemp.some(msg => {
                        const msgIdNormalized = String(msg.id ?? msg.messageId ?? msg.message_id);
                        // Проверяем только точное совпадение ID (не временных)
                        if (msgIdNormalized === incomingIdKey && !msgIdNormalized.startsWith('ws-') && !msgIdNormalized.startsWith('temp-')) {
                            return true;
                        }
                        // Проверяем по файлам
                        if (message.fileUrl && msg.fileUrl === message.fileUrl) return true;
                        return false;
                    });

                    if (exists) {
                        console.log('[CHAT_MESSAGE] Message with same real ID already exists, skipping duplicate:', message);
                        return withoutOptimisticAndTemp;
                    }

                    const newMessage = {
                        id: incomingId,
                        content: message.content || '',
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

                    // Применяем накопленный readCount, если MESSAGE_READ пришёл раньше (используем нормализованный ключ)
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
            }
        };

        // Подписываемся на события чата
        const unsubscribeChatEvents = chatService.onChatEvent(handleChatEvent);

        // Подписываемся на все WebSocket сообщения
        const unsubscribeMessages = chatService.onMessage(handleWebSocketMessage);

        return () => {
            // Отписываемся при размонтировании компонента
            unsubscribeChatEvents();
            unsubscribeMessages();
        };
    }, [selectedChat?.id, loadChatInfo, getCurrentUserId]); // ИСПРАВЛЕНО: Добавляем getCurrentUserId в зависимости


    // После рендера сообщений восстанавливаем scroll к первому видимому сообщению
    useLayoutEffect(() => {
        // ИСПРАВЛЕНИЕ: Не прокручиваем если идет загрузка старых сообщений
        if (isLoadingOlderMessages) {
            return;
        }

        // После рендера сообщений восстанавливаем scroll к первому видимому сообщению
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

            try {
                // Отправляем сообщение через WebSocket
                chatService.sendChatMessage(newMessage.trim(), selectedChat.id);

                // Добавляем локально оптимистичное обновление
                const optimisticMessage = {
                    id: `temp-${Date.now()}`,
                    content: newMessage.trim(),
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
                alert('Ошибка отправки сообщения');
            }
        }
    };

    const handleAddParticipants = async (selectedUsers) => {
        if (!selectedChat || selectedChat.chatType !== 'GROUP') return;

        try {
            const userIds = selectedUsers.map(user => user.id);
            const updatedChat = await chatService.addParticipants(selectedChat.id, userIds);
            setChatInfo(updatedChat);
            onChatUpdate && onChatUpdate(updatedChat);

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
                onChatUpdate && onChatUpdate(null); // Уведомляем родительский компонент
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
    }, [selectedChat?.id]);

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
}, [messages.length, selectedChat?.id]);

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
                                    <div className="break-words">{message.content}</div>
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
