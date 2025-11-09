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
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md max-h-[90vh] overflow-y-auto">
        {/* Заголовок */}
        <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex justify-between items-center">
          <h2 className="text-xl font-semibold text-gray-800">
            {isEditing ? 'Редактировать профиль' : 'Мой профиль'}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-2xl leading-none"
          >
            ×
          </button>
        </div>

        {/* Содержимое */}
        <div className="px-6 py-4">
          {/* Аватар */}
          <div className="flex flex-col items-center mb-6">
            <div className="w-24 h-24 bg-blue-500 rounded-full flex items-center justify-center mb-3 overflow-hidden">
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
                className="text-white font-semibold text-3xl w-full h-full flex items-center justify-center"
                style={{ display: (avatarPreview || avatarUrl) ? 'none' : 'flex' }}
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
                  className="cursor-pointer text-sm text-blue-500 hover:text-blue-600"
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
                    className="text-sm text-red-500 hover:text-red-600"
                  >
                    Отменить
                  </button>
                )}
              </div>
            )}
          </div>

          {/* Форма */}
          <form onSubmit={handleSubmit}>
            {/* ID пользователя */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                ID пользователя
              </label>
              <input
                type="text"
                value={currentUser?.id || ''}
                disabled
                className="w-full px-3 py-2 border border-gray-300 rounded-md bg-gray-50 text-gray-500"
              />
            </div>

            {/* Имя пользователя */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Имя пользователя
              </label>
              <input
                type="text"
                name="username"
                value={formData.username}
                onChange={handleChange}
                disabled={!isEditing}
                className={`w-full px-3 py-2 border border-gray-300 rounded-md ${
                  isEditing ? 'bg-white' : 'bg-gray-50 text-gray-500'
                }`}
                required
              />
            </div>

            {/* Email */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Email
              </label>
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                disabled={!isEditing}
                className={`w-full px-3 py-2 border border-gray-300 rounded-md ${
                  isEditing ? 'bg-white' : 'bg-gray-50 text-gray-500'
                }`}
                required
              />
            </div>

            {/* О себе */}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                О себе
              </label>
              <textarea
                name="bio"
                value={formData.bio}
                onChange={handleChange}
                disabled={!isEditing}
                rows={3}
                className={`w-full px-3 py-2 border border-gray-300 rounded-md ${
                  isEditing ? 'bg-white' : 'bg-gray-50 text-gray-500'
                }`}
                placeholder="Расскажите о себе..."
              />
            </div>

            {/* Сообщения об ошибках и успехе */}
            {error && (
              <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-md text-red-600 text-sm">
                {error}
              </div>
            )}

            {success && (
              <div className="mb-4 p-3 bg-green-50 border border-green-200 rounded-md text-green-600 text-sm">
                Профиль успешно обновлен!
              </div>
            )}

            {/* Кнопки действий */}
            <div className="flex gap-2 mt-6">
              {isEditing ? (
                <>
                  <button
                    type="submit"
                    disabled={loading}
                    className={`flex-1 bg-blue-500 text-white px-4 py-2 rounded-md hover:bg-blue-600 ${
                      loading ? 'opacity-50 cursor-not-allowed' : ''
                    }`}
                  >
                    {loading ? 'Сохранение...' : 'Сохранить'}
                  </button>
                  <button
                    type="button"
                    onClick={handleCancel}
                    disabled={loading}
                    className="flex-1 bg-gray-300 text-gray-700 px-4 py-2 rounded-md hover:bg-gray-400"
                  >
                    Отмена
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  onClick={handleEditClick}
                  className="flex-1 bg-blue-500 text-white px-4 py-2 rounded-md hover:bg-blue-600"
                >
                  Редактировать
                </button>
              )}
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};

export default ProfileModal;
