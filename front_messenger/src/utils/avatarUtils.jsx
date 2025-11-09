/**
 * Утилиты для работы с аватарками
 * Используют публичный endpoint без JWT авторизации
 */

const GATEWAY_URL = 'http://localhost:8083';

/**
 * Получить полный URL аватарки
 * @param {string} profilePictureUrl - URL из профиля пользователя
 * @returns {string} - Полный URL для отображения
 */
export const getAvatarUrl = (profilePictureUrl) => {
  if (!profilePictureUrl) {
    return null;
  }

  // Если URL уже абсолютный - возвращаем как есть
  if (profilePictureUrl.startsWith('http://') || profilePictureUrl.startsWith('https://')) {
    return profilePictureUrl;
  }

  // ИСПРАВЛЕНИЕ: Все относительные пути формируем через gateway
  // Убираем лишние слэши
  const cleanPath = profilePictureUrl.startsWith('/') ? profilePictureUrl : `/${profilePictureUrl}`;

  return `${GATEWAY_URL}${cleanPath}`;
};

/**
 * Получить инициалы пользователя для fallback
 * @param {string} username - имя пользователя
 * @returns {string} - инициалы (1-2 символа)
 */
export const getUserInitials = (username) => {
  if (!username) return 'U';

  const parts = username.trim().split(' ');
  if (parts.length >= 2) {
    return (parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
  }

  return username.charAt(0).toUpperCase();
};

/**
 * НОВОЕ: Получить аватарку для чата
 * @param {object} chat - объект чата
 * @param {object} currentUser - текущий пользователь
 * @returns {string|null} - URL аватарки или null
 */
export const getChatAvatarUrl = (chat, currentUser) => {
  if (!chat) return null;

  // Для групповых чатов используем аватарку группы
  if (chat.chatType === 'GROUP') {
    return getAvatarUrl(chat.chatAvatarUrl);
  }

  // Для личных чатов используем аватарку собеседника
  if (chat.chatType === 'PRIVATE' && chat.participants) {
    const otherParticipant = chat.participants.find(p => p.id !== currentUser?.id);
    if (otherParticipant) {
      return getAvatarUrl(otherParticipant.profilePictureUrl);
    }
  }

  // Fallback на аватарку чата
  return getAvatarUrl(chat.chatAvatarUrl);
};

/**
 * НОВОЕ: Получить инициалы для чата
 * @param {object} chat - объект чата
 * @param {object} currentUser - текущий пользователь
 * @returns {string} - инициалы для отображения
 */
export const getChatInitials = (chat, currentUser) => {
  if (!chat) return 'C';

  // Для групповых чатов - первая буква названия
  if (chat.chatType === 'GROUP') {
    return chat.chatName ? chat.chatName.charAt(0).toUpperCase() : 'G';
  }

  // Для личных чатов - инициалы собеседника
  if (chat.chatType === 'PRIVATE' && chat.participants) {
    const otherParticipant = chat.participants.find(p => p.id !== currentUser?.id);
    if (otherParticipant) {
      return getUserInitials(otherParticipant.username);
    }
  }

  return 'C';
};

/**
 * Компонент Avatar для отображения аватарки с fallback
 */
export const AvatarImage = ({ user, size = 'md', className = '' }) => {
  const sizeClasses = {
    sm: 'w-8 h-8 text-sm',
    md: 'w-12 h-12 text-base',
    lg: 'w-16 h-16 text-xl',
    xl: 'w-24 h-24 text-3xl'
  };

  const avatarUrl = getAvatarUrl(user?.profilePictureUrl);
  const initials = getUserInitials(user?.username);

  return (
    <div className={`${sizeClasses[size]} bg-blue-500 rounded-full flex items-center justify-center overflow-hidden ${className}`}>
      {avatarUrl ? (
        <img
          src={avatarUrl}
          alt={user?.username || 'User'}
          className="w-full h-full object-cover"
          onError={(e) => {
            // При ошибке загрузки показываем инициалы
            e.target.style.display = 'none';
            if (e.target.nextSibling) {
              e.target.nextSibling.style.display = 'flex';
            }
          }}
        />
      ) : null}
      <span
        className="text-white font-semibold w-full h-full flex items-center justify-center"
        style={{ display: avatarUrl ? 'none' : 'flex' }}
      >
        {initials}
      </span>
    </div>
  );
};

export default {
  getAvatarUrl,
  getUserInitials,
  getChatAvatarUrl,
  getChatInitials,
  AvatarImage
};
