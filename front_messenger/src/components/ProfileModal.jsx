import React, { useState, useEffect } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { logout } from '../store/slices/authSlice';
import userService from '../services/userService';
import authService from '../services/authService';
import { getAvatarUrl, getUserInitials } from '../utils/avatarUtils';

const ProfileModal = ({ isOpen, onClose }) => {
  const { user } = useSelector(state => state.auth);
  const dispatch = useDispatch();

  const [isEditing, setIsEditing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [avatarFile, setAvatarFile] = useState(null);
  const [avatarPreview, setAvatarPreview] = useState(null);

  const [formData, setFormData] = useState({
    username: '',
    email: '',
    bio: ''
  });

  // ИСПРАВЛЕНИЕ: Убираем user из зависимостей, используем только isOpen
  useEffect(() => {
    if (isOpen) {
      // Получаем актуальные данные пользователя при каждом открытии
      const currentUser = authService.getUserData();
      if (currentUser) {
        setFormData({
          username: currentUser.username || '',
          email: currentUser.email || '',
          bio: currentUser.bio || ''
        });
      }
      // Сбрасываем состояние при открытии модального окна
      setIsEditing(false);
      setError(null);
      setSuccess(false);
      setAvatarFile(null);
      setAvatarPreview(null);
    }
  }, [isOpen]); // Только isOpen в зависимостях!

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!isEditing) {
      console.warn('Форма отправлена, но режим редактирования не активен');
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(false);

    try {
      let avatarUrl = user?.profilePictureUrl;

      // Сначала загружаем аватар, если он был выбран
      if (avatarFile) {
        console.log('Загрузка аватара:', avatarFile.name);
        const avatarResponse = await userService.uploadAvatar(avatarFile);
        avatarUrl = avatarResponse.avatarUrl;
        console.log('Аватар успешно загружен:', avatarUrl);
      }

      // Затем обновляем остальные данные профиля
      const profileData = {
        ...formData,
        profilePictureUrl: avatarUrl
      };

      console.log('Отправка данных профиля:', profileData);
      const updatedUser = await userService.updateProfile(profileData);
      console.log('Профиль успешно обновлен:', updatedUser);

      // ИСПРАВЛЕНИЕ: Обновляем localStorage один раз с корректными данными
      const currentUser = authService.getUserData();
      const newUserData = {
        ...currentUser,
        username: updatedUser.username || currentUser.username,
        email: updatedUser.email || currentUser.email,
        bio: updatedUser.bio !== undefined ? updatedUser.bio : currentUser.bio,
        profilePictureUrl: updatedUser.profilePictureUrl || currentUser.profilePictureUrl,
        id: currentUser.id
      };

      // Обновляем localStorage ОДИН РАЗ
      localStorage.setItem('user', JSON.stringify(newUserData));

      // ИСПРАВЛЕНИЕ: Не используем setTimeout - обновляем синхронно
      dispatch({ type: 'auth/setAuthFromService' });

      setSuccess(true);
      setIsEditing(false);
      setAvatarFile(null);
      setAvatarPreview(null);

      // Обновляем локальное состояние формы
      setFormData({
        username: newUserData.username || '',
        email: newUserData.email || '',
        bio: newUserData.bio || ''
      });

      setTimeout(() => {
        setSuccess(false);
      }, 3000);
    } catch (err) {
      console.error('Ошибка обновления профиля:', err);

      if (err.response?.status === 401 || err.response?.status === 403) {
        setError('Сессия истекла. Пожалуйста, войдите снова.');
        setTimeout(() => {
          dispatch(logout());
        }, 2000);
      } else {
        setError(err.response?.data?.error || err.response?.data?.message || err.message || 'Не удалось обновить профиль');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleEditClick = (e) => {
    e.preventDefault();
    e.stopPropagation();
    console.log('Переключение в режим редактирования');
    setIsEditing(true);
  };

  const handleCancel = () => {
    const currentUser = authService.getUserData();
    setFormData({
      username: currentUser?.username || '',
      email: currentUser?.email || '',
      bio: currentUser?.bio || ''
    });
    setIsEditing(false);
    setError(null);
    setAvatarFile(null);
    setAvatarPreview(null);
  };

  const handleAvatarChange = (e) => {
    const file = e.target.files[0];

    if (file) {
      // НОВОЕ: Проверка размера файла (10 МБ = 10 * 1024 * 1024 байт)
      const maxSize = 10 * 1024 * 1024; // 10 МБ
      if (file.size > maxSize) {
        setError(`Размер файла не должен превышать 10 МБ. Выбранный файл: ${(file.size / 1024 / 1024).toFixed(2)} МБ`);
        setAvatarFile(null);
        setAvatarPreview(null);
        e.target.value = ''; // Очищаем input
        return;
      }

      // Проверка типа файла
      if (!file.type.startsWith('image/')) {
        setError('Пожалуйста, выберите файл изображения (JPG, PNG, GIF и т.д.)');
        setAvatarFile(null);
        setAvatarPreview(null);
        e.target.value = '';
        return;
      }

      // Очищаем ошибку если была
      setError(null);
      setAvatarFile(file);

      const reader = new FileReader();
      reader.onloadend = () => {
        setAvatarPreview(reader.result);
      };
      reader.readAsDataURL(file);
    } else {
      setAvatarFile(null);
      setAvatarPreview(null);
    }
  };

  if (!isOpen) return null;

  const currentUser = user || authService.getUserData();
  const avatarUrl = getAvatarUrl(currentUser?.profilePictureUrl);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="shadow-xl rounded-2xl px-7 py-6 w-[440px] max-h-[90vh] flex flex-col relative border border-[#400d0d]" style={{backgroundImage:'linear-gradient(135deg,#400d0d 0%,#e07a5f 100%)',backgroundColor:'#f5e6d6',color:'#f5e6d6'}}>
        {/* Заголовок */}
        <button
          onClick={onClose}
          className="absolute top-3 right-3 p-1 rounded-full hover:bg-[#F5F5DC] transition-colors"
          aria-label="Закрыть"
          style={{background:'none',border:'none'}}
        >
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M6 6L18 18" stroke="#F5F5DC" strokeWidth="2.5" strokeLinecap="round"/>
            <path d="M18 6L6 18" stroke="#F5F5DC" strokeWidth="2.5" strokeLinecap="round"/>
          </svg>
        </button>
        <h2 className="text-lg font-semibold mb-3 text-center" style={{color:'#f5e6d6'}}>
          {isEditing ? 'Редактировать профиль' : 'Мой профиль'}
        </h2>
        {/* Аватар */}
        <div className="flex flex-col items-center mb-6">
          <div className="w-24 h-24 bg-[#F5F5DC] rounded-full flex items-center justify-center mb-3 overflow-hidden border-2 border-[#B22222]">
            {avatarPreview ? (
              <img
                src={avatarPreview}
                alt={formData.username}
                className="w-full h-full rounded-full object-cover"
              />
            ) : avatarUrl ? (
              <img
                src={avatarUrl}
                alt={formData.username}
                className="w-full h-full rounded-full object-cover"
                onError={(e) => {
                  e.target.style.display = 'none';
                  if (e.target.nextSibling) {
                    e.target.nextSibling.style.display = 'flex';
                  }
                }}
              />
            ) : null}
            <span
              className="font-semibold text-3xl w-full h-full flex items-center justify-center"
              style={{color:'#B22222',display: (avatarPreview || avatarUrl) ? 'none' : 'flex'}}
            >
              {getUserInitials(formData.username)}
            </span>
          </div>
          {isEditing && (
            <div className="flex gap-2">
              <input
                type="file"
                accept="image/*"
                onChange={handleAvatarChange}
                className="hidden"
                id="avatar-upload"
              />
              <label
                htmlFor="avatar-upload"
                className="cursor-pointer text-sm text-[#B22222] hover:text-[#8B1A1A]"
              >
                Изменить фото
              </label>
              {avatarFile && (
                <button
                  type="button"
                  onClick={() => {
                    setAvatarFile(null);
                    setAvatarPreview(null);
                  }}
                  className="text-sm text-[#B22222] hover:text-[#8B1A1A]"
                >
                  Отменить
                </button>
              )}
            </div>
          )}
        </div>
        {/* Форма */}
        <form onSubmit={handleSubmit} className="flex-1 flex flex-col">
          <div className="mb-4">
            <label className="block text-sm font-medium mb-1" style={{color:'#F5F5DC'}}>
              ID пользователя
            </label>
            <input
              type="text"
              value={currentUser?.id || ''}
              disabled
              className="w-full px-3 py-2 border border-[#B22222] rounded-lg bg-[#FFF8F0] text-[#B22222]"
            />
          </div>
          <div className="mb-4">
            <label className="block text-sm font-medium mb-1" style={{color:'#F5F5DC'}}>
              Имя пользователя
            </label>
            <input
              type="text"
              name="username"
              value={formData.username}
              onChange={handleChange}
              disabled={!isEditing}
              className="w-full px-3 py-2 border border-[#B22222] rounded-lg bg-[#FFF8F0] text-[#B22222]"
              required
            />
          </div>
          <div className="mb-4">
            <label className="block text-sm font-medium mb-1" style={{color:'#F5F5DC'}}>
              Email
            </label>
            <input
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              disabled={!isEditing}
              className="w-full px-3 py-2 border border-[#B22222] rounded-lg bg-[#FFF8F0] text-[#B22222]"
              required
            />
          </div>
          <div className="mb-4">
            <label className="block text-sm font-medium mb-1" style={{color:'#F5F5DC'}}>
              О себе
            </label>
            <textarea
              name="bio"
              value={formData.bio}
              onChange={handleChange}
              disabled={!isEditing}
              rows={3}
              className="w-full px-3 py-2 border border-[#B22222] rounded-lg bg-[#FFF8F0] text-[#B22222]"
            />
          </div>
          {/* Кнопки */}
          <div className="flex gap-2 mt-2 justify-center">
            {isEditing ? (
              <>
                <button
                  type="submit"
                  disabled={loading}
                  className="px-5 py-2 rounded-lg font-semibold transition-colors"
                  style={{backgroundColor:'#B22222',color:'#F5F5DC',border:'none'}}
                >
                  {loading ? 'Сохраняю...' : 'Сохранить'}
                </button>
                <button
                  type="button"
                  onClick={handleCancel}
                  className="px-5 py-2 rounded-lg font-semibold transition-colors"
                  style={{backgroundColor:'#F5F5DC',color:'#B22222',border:'1px solid #B22222'}}
                >
                  Отмена
                </button>
              </>
            ) : (
              <button
                type="button"
                onClick={handleEditClick}
                className="px-5 py-2 rounded-lg font-semibold transition-colors"
                style={{backgroundColor:'#B22222',color:'#F5F5DC',border:'none'}}
              >
                Редактировать
              </button>
            )}
          </div>
          {/* Сообщения об ошибке/успехе */}
          {error && <div className="mt-3 text-center text-sm" style={{color:'#B22222'}}>{error}</div>}
          {success && <div className="mt-3 text-center text-sm" style={{color:'#228B22'}}>
            Профиль успешно обновлен!
          </div>}
        </form>
      </div>
    </div>
  );
};

export default ProfileModal;
