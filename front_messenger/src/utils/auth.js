export const setToken = (token) => {
    localStorage.setItem('token', token);
};

export const getToken = () => {
    return localStorage.getItem('token');
};

export const removeToken = () => {
    localStorage.removeItem('token');
};

export const isTokenValid = async () => {
    const token = getToken();
    if (!token) return false;

    try {
        const response = await fetch('http://localhost:8080/auth/validate', {
            headers: {
                'Authorization': `Bearer ${token}`,
            },
        });

        return response.ok;
    } catch (error) {
        console.error('Token validation error:', error);
        return false;
    }
};