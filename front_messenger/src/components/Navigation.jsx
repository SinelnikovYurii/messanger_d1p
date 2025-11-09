import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import authService from '../services/authService';
import { getAvatarUrl, getUserInitials } from '../utils/avatarUtils';

const Navigation = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [user, setUser] = useState(() => {
    // ИСПРАВЛЕНИЕ: Инициализируем состояние данными из authService сразу при создании компонента
    return authService.getUserData();
  });

  useEffect(() => {
    // Получаем данные пользователя через AuthService при монтировании
    const userData = authService.getUserData();
    console.log('Navigation: Initial user data loaded:', userData);

    // Дополнительная диагностика
    console.log('Navigation: localStorage.user =', localStorage.getItem('user'));
    console.log('Navigation: localStorage.token =', localStorage.getItem('token') ? 'EXISTS' : 'NULL');

    // Обновляем состояние если данные есть
    if (userData) {
      setUser(userData);
    }

    // Подписываемся на изменения авторизации
    const unsubscribe = authService.addListener((isAuthenticated) => {
      console.log('Navigation: Auth state changed:', isAuthenticated);
      if (isAuthenticated) {
        const updatedUserData = authService.getUserData();
        console.log('Navigation: Updated user data:', updatedUserData);
        setUser(updatedUserData);
      } else {
        console.log('Navigation: User logged out');
        setUser(null);
      }
    });

    // Также слушаем прямые изменения в localStorage
    const handleStorageChange = (e) => {
      if (e.key === 'user') {
        console.log('Navigation: Storage changed, reloading user data');
        const updatedUserData = authService.getUserData();
        setUser(updatedUserData);
      }
    };

    const handleAuthChange = () => {
      console.log('Navigation: authChange event received');
      const updatedUserData = authService.getUserData();
      setUser(updatedUserData);
    };

    window.addEventListener('storage', handleStorageChange);
    window.addEventListener('authChange', handleAuthChange);

    return () => {
      unsubscribe();
      window.removeEventListener('storage', handleStorageChange);
      window.removeEventListener('authChange', handleAuthChange);
    };
  }, []);

  const handleLogout = () => {
    // Используем AuthService для безопасного логаута
    authService.logout();
  };

  const navItems = [
    {
      path: '/chat',
      label: 'Чаты',
      icon: (
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
        </svg>
      )
    },
    {
      path: '/friends',
      label: 'Друзья',
      icon: (
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
        </svg>
      )
    }
  ];

  return (
    <nav className="bg-white border-b border-gray-200 px-4 py-2">
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-2 sm:space-x-4 min-w-0 flex-1">
          <h1 className="text-lg sm:text-xl font-bold text-gray-800 truncate">Мессенджер</h1>
          <div className="hidden sm:flex space-x-1">
            {navItems.map((item) => (
              <button
                key={item.path}
                onClick={() => navigate(item.path)}
                className={`flex items-center space-x-2 px-3 sm:px-4 py-2 rounded-lg transition-colors ${
                  location.pathname === item.path
                    ? 'bg-blue-500 text-white'
                    : 'text-gray-600 hover:bg-gray-100'
                }`}
              >
                {item.icon}
                <span className="whitespace-nowrap">{item.label}</span>
              </button>
            ))}
          </div>

          {/* Мобильная навигация */}
          <div className="flex sm:hidden space-x-1">
            {navItems.map((item) => (
              <button
                key={item.path}
                onClick={() => navigate(item.path)}
                className={`flex items-center justify-center p-2 rounded-lg transition-colors ${
                  location.pathname === item.path
                    ? 'bg-blue-500 text-white'
                    : 'text-gray-600 hover:bg-gray-100'
                }`}
                title={item.label}
              >
                {item.icon}
              </button>
            ))}
          </div>
        </div>

        <div className="flex items-center space-x-2 sm:space-x-4 flex-shrink-0">
          {user && (
            <div className="flex items-center space-x-2">
              <div className="w-8 h-8 bg-blue-500 rounded-full flex items-center justify-center overflow-hidden">
                {getAvatarUrl(user.profilePictureUrl) ? (
                  <img
                    src={getAvatarUrl(user.profilePictureUrl)}
                    alt={user.username}
                    className="w-full h-full object-cover"
                    onError={(e) => {
                      e.target.style.display = 'none';
                      if (e.target.nextSibling) {
                        e.target.nextSibling.style.display = 'flex';
                      }
                    }}
                  />
                ) : null}
                <span
                  className="text-white text-sm font-medium w-full h-full flex items-center justify-center"
                  style={{ display: getAvatarUrl(user.profilePictureUrl) ? 'none' : 'flex' }}
                >
                  {getUserInitials(user.username)}
                </span>
              </div>
              <span className="text-gray-700 hidden sm:block truncate max-w-32">
                {user.username || 'Пользователь'}
              </span>
            </div>
          )}

          <button
            onClick={handleLogout}
            className="flex items-center space-x-1 text-gray-600 hover:text-red-600 transition-colors p-2 sm:px-3 sm:py-2 rounded-lg hover:bg-gray-100"
            title="Выход"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
            </svg>
            <span className="hidden sm:inline">Выход</span>
          </button>
        </div>
      </div>
    </nav>
  );
};

export default Navigation;
