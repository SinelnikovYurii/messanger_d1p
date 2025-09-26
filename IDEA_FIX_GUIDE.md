# Инструкции по исправлению ошибок компиляции в IntelliJ IDEA

## 1. Обновление Maven проекта
1. Откройте проект в IntelliJ IDEA
2. Нажмите Ctrl+Shift+O или File -> Reload Gradle/Maven Projects
3. В правой панели Maven нажмите кнопку "Refresh" (↻)

## 2. Очистка и пересборка
1. В IntelliJ IDEA: Build -> Clean Project
2. Затем: Build -> Rebuild Project

## 3. Если проблемы с зависимостями продолжаются:
1. File -> Invalidate Caches and Restart
2. Выберите "Invalidate and Restart"

## 4. Проверка Java версии
1. File -> Project Structure -> Project Settings -> Project
2. Убедитесь, что Project SDK установлен на Java 17 или выше
3. Project language level тоже должен быть 17+

## 5. Очистка .m2 репозитория (если нужно)
Удалите папку: C:\Users\{ваше_имя}\.m2\repository\org\springframework
Затем обновите Maven проект снова.

## 6. Если Maven Wrapper не работает
Попробуйте установить Maven глобально:
1. Скачайте Apache Maven с https://maven.apache.org/download.cgi
2. Добавьте в PATH переменную окружения
3. Перезапустите IntelliJ IDEA
