import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../services/api';

const RegisterForm = ({ setIsAuthenticated }) => {
    const [credentials, setCredentials] = useState({
        username: '',
        email: '',
        password: '',
        confirmPassword: '',
    });
    const [error, setError] = useState('');

    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (credentials.password !== credentials.confirmPassword) {
            setError('Passwords do not match');
            return;
        }

        try {
            const { confirmPassword, ...data } = credentials;
            const response = await authApi.post('/auth/register', data);
            if (response.status === 200) {
                // После успешной регистрации просто перенаправляем на страницу логина
                // без установки токена и флага аутентификации
                navigate('/login');
            }
        } catch (err) {
            setError(err.response?.data?.message || 'Registration failed');
        }
    };

    return (
        <div style={{ padding: '20px', maxWidth: '400px', margin: 'auto' }}>
            <h2>Register</h2>
            <form onSubmit={handleSubmit}>
                <input
                    type="text"
                    placeholder="Username"
                    value={credentials.username}
                    onChange={(e) => setCredentials({...credentials, username: e.target.value})}
                    style={{
                        width: '100%',
                        padding: '10px',
                        margin: '8px 0',
                        border: '1px solid #ccc',
                        borderRadius: '4px',
                        outline: 'none',
                    }}
                    onFocus={(e) => e.target.style.border = '2px solid #007bff'}
                    onBlur={(e) => e.target.style.border = '1px solid #ccc'}
                />
                <input
                    type="email"
                    placeholder="Email"
                    value={credentials.email}
                    onChange={(e) => setCredentials({...credentials, email: e.target.value})}
                    style={{
                        width: '100%',
                        padding: '10px',
                        margin: '8px 0',
                        border: '1px solid #ccc',
                        borderRadius: '4px',
                        outline: 'none',
                    }}
                    onFocus={(e) => e.target.style.border = '2px solid #007bff'}
                    onBlur={(e) => e.target.style.border = '1px solid #ccc'}
                />
                <input
                    type="password"
                    placeholder="Password"
                    value={credentials.password}
                    onChange={(e) => setCredentials({...credentials, password: e.target.value})}
                    style={{
                        width: '100%',
                        padding: '10px',
                        margin: '8px 0',
                        border: '1px solid #ccc',
                        borderRadius: '4px',
                        outline: 'none',
                    }}
                    onFocus={(e) => e.target.style.border = '2px solid #007bff'}
                    onBlur={(e) => e.target.style.border = '1px solid #ccc'}
                />
                <input
                    type="password"
                    placeholder="Confirm Password"
                    value={credentials.confirmPassword}
                    onChange={(e) => setCredentials({...credentials, confirmPassword: e.target.value})}
                    style={{
                        width: '100%',
                        padding: '10px',
                        margin: '8px 0',
                        border: '1px solid #ccc',
                        borderRadius: '4px',
                        outline: 'none',
                    }}
                    onFocus={(e) => e.target.style.border = '2px solid #007bff'}
                    onBlur={(e) => e.target.style.border = '1px solid #ccc'}
                />
                <button
                    type="submit"
                    style={{
                        padding: '12px',
                        width: '100%',
                        backgroundColor: '#007bff',
                        color: 'white',
                        border: 'none',
                        borderRadius: '4px',
                        cursor: 'pointer',
                    }}
                >
                    Register
                </button>
                {error && <p style={{ color: 'red', marginTop: '10px' }}>{error}</p>}
            </form>
            <p style={{ marginTop: '15px' }}>
                Already have an account? <a href="/login">Login</a>
            </p>
        </div>
    );
};

export default RegisterForm;