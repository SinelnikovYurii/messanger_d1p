import React, { useState, useEffect } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { logout } from '../store/slices/authSlice';
import userService from '../services/userService';
import authService from '../services/authService';
import keyBackupService from '../services/keyBackupService';
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

  // KEK (ключи шифрования)
  const [showKekSection, setShowKekSection] = useState(false);
  const [kekOldPassword, setKekOldPassword] = useState('');
  const [kekNewPassword, setKekNewPassword] = useState('');
  const [kekNewPasswordConfirm, setKekNewPasswordConfirm] = useState('');
  const [kekLoading, setKekLoading] = useState(false);
  const [kekError, setKekError] = useState('');
  const [kekSuccess, setKekSuccess] = useState('');

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
      setShowKekSection(false);
      setKekOldPassword('');
      setKekNewPassword('');
      setKekNewPasswordConfirm('');
      setKekError('');
      setKekSuccess('');
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

  // Обновление KEK-пароля: перешифровываем ключи новым паролем
  const handleKekUpdate = async (e) => {
    e.preventDefault();
    setKekError('');
    setKekSuccess('');

    if (kekNewPassword.length < 8) {
      setKekError('Новый пароль должен быть не менее 8 символов');
      return;
    }
    if (kekNewPassword !== kekNewPasswordConfirm) {
      setKekError('Новые пароли не совпадают');
      return;
    }

    setKekLoading(true);
    try {
      // Если есть старый бекап — проверяем старый пароль (восстанавливаем ключи)
      if (kekOldPassword) {
        await keyBackupService.downloadAndRestoreKeys(kekOldPassword);
      }
      // Шифруем текущие ключи новым паролем и загружаем
      await keyBackupService.uploadKeyBackup(kekNewPassword);
      sessionStorage.setItem('kek_password', kekNewPassword);
      setKekSuccess('Пароль ключей успешно обновлён!');
      setKekOldPassword('');
      setKekNewPassword('');
      setKekNewPasswordConfirm('');
      setTimeout(() => setKekSuccess(''), 4000);
    } catch (err) {
      if (err.message?.includes('Неверный пароль')) {
        setKekError('Неверный текущий пароль шифрования ключей');
      } else {
        setKekError(err.message || 'Ошибка обновления пароля ключей');
      }
    } finally {
      setKekLoading(false);
    }
  };


  const handleEditClick = (e) => {
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

          {/* ── Секция E2EE ключей ── */}
          {!isEditing && (
            <div className="mt-4 border-t pt-4" style={{ borderColor: 'rgba(245,230,214,0.3)' }}>
              <button
                type="button"
                onClick={() => { setShowKekSection(v => !v); setKekError(''); setKekSuccess(''); }}
                className="w-full text-left text-sm font-semibold flex items-center gap-2"
                style={{ background: 'none', border: 'none', color: '#F5F5DC', cursor: 'pointer' }}
              >
                <span>🔑 Управление ключами E2EE</span>
                <span>{showKekSection ? '▲' : '▼'}</span>
              </button>
              {showKekSection && (
                <div className="mt-3 space-y-3">
                  <p className="text-xs" style={{ color: 'rgba(245,230,214,0.8)' }}>
                    Здесь вы можете обновить пароль шифрования ключей или создать новый бекап.
                    Сервер не видит этот пароль.
                  </p>

                  {/* Обновить KEK-пароль */}
                  <form onSubmit={handleKekUpdate} className="space-y-2">
                    <div>
                      <label className="block text-xs font-medium mb-1" style={{ color: '#F5F5DC' }}>
                        Текущий пароль ключей (если есть бекап на сервере)
                      </label>
                      <input
                        type="password"
                        value={kekOldPassword}
                        onChange={e => setKekOldPassword(e.target.value)}
                        className="w-full px-3 py-1.5 rounded-lg text-sm"
                        style={{ background: '#F5DEB3', color: '#520808', border: '1px solid #B22222' }}
                        placeholder="Текущий пароль ключей"
                        disabled={kekLoading}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium mb-1" style={{ color: '#F5F5DC' }}>
                        Новый пароль ключей
                      </label>
                      <input
                        type="password"
                        value={kekNewPassword}
                        onChange={e => setKekNewPassword(e.target.value)}
                        required
                        minLength={8}
                        className="w-full px-3 py-1.5 rounded-lg text-sm"
                        style={{ background: '#F5DEB3', color: '#520808', border: '1px solid #B22222' }}
                        placeholder="Минимум 8 символов"
                        disabled={kekLoading}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium mb-1" style={{ color: '#F5F5DC' }}>
                        Повторите новый пароль ключей
                      </label>
                      <input
                        type="password"
                        value={kekNewPasswordConfirm}
                        onChange={e => setKekNewPasswordConfirm(e.target.value)}
                        required
                        minLength={8}
                        className="w-full px-3 py-1.5 rounded-lg text-sm"
                        style={{ background: '#F5DEB3', color: '#520808', border: '1px solid #B22222' }}
                        placeholder="Повторите пароль"
                        disabled={kekLoading}
                      />
                    </div>
                    <button
                      type="submit"
                      disabled={kekLoading || kekNewPassword.length < 8}
                      className="w-full py-1.5 rounded-lg text-sm font-semibold"
                      style={{ background: '#B22222', color: '#F5F5DC', border: 'none' }}
                    >
                      {kekLoading ? 'Обновление...' : 'Обновить пароль ключей и бекап'}
                    </button>
                  </form>

                  {kekError && (
                    <div className="text-xs text-center px-2 py-1 rounded" style={{ background: 'rgba(178,34,34,0.2)', color: '#FFDAB9' }}>
                      {kekError}
                    </div>
                  )}
                  {kekSuccess && (
                    <div className="text-xs text-center px-2 py-1 rounded" style={{ background: 'rgba(34,139,34,0.2)', color: '#90EE90' }}>
                      {kekSuccess}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

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
