import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Input from '../components/Input';
import Button from '../components/Button';
import { useAuth } from '../hooks/useAuth.jsx';
import './css/ProfilePage.css';

const ProfilePage = () => {
    const { user, updateProfile } = useAuth();
    const navigate = useNavigate();
    const [editing, setEditing] = useState(false);
    const [formData, setFormData] = useState({
        username: user?.username || '',
        email: user?.email || ''
    });

    const handleEdit = () => {
        setEditing(true);
    };

    const handleSave = async () => {
        try {
            await updateProfile(formData);
            setEditing(false);
        } catch (error) {
            console.error('Ошибка обновления профиля:', error);
        }
    };

    const handleChange = (e) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };

    return (
        <div className="profile-page">
            <div className="profile-container">
                <div className="profile-header">
                    <h2>Профиль</h2>
                    <button onClick={() => navigate('/')} className="back-button">
                        Назад к чатам
                    </button>
                </div>

                <div className="profile-content">
                    <div className="profile-field">
                        <label>ID пользователя:</label>
                        <span>{user?.id}</span>
                    </div>

                    <Input
                        label="Имя пользователя"
                        name="username"
                        value={formData.username}
                        onChange={handleChange}
                        disabled={!editing}
                    />

                    <Input
                        label="Email"
                        name="email"
                        type="email"
                        value={formData.email}
                        onChange={handleChange}
                        disabled={!editing}
                    />

                    <div className="profile-actions">
                        {editing ? (
                            <>
                                <Button onClick={handleSave} variant="primary">
                                    Сохранить
                                </Button>
                                <Button onClick={() => setEditing(false)} variant="secondary">
                                    Отмена
                                </Button>
                            </>
                        ) : (
                            <Button onClick={handleEdit} variant="primary">
                                Редактировать
                            </Button>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ProfilePage;