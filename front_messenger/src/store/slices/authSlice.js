import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import authService from '../../services/authService';
import { login as loginApi, register as registerApi } from '../../services/api';

export const login = createAsyncThunk(
    'auth/login',
    async (credentials, { rejectWithValue }) => {
        try {
            const data = await loginApi(credentials);

            // Сохраняем данные через AuthService
            if (data.token && data.user) {
                authService.setAuth(data.token, data.user);
            }

            return data;
        } catch (error) {
            return rejectWithValue(error.response?.data?.message || error.message || 'Login failed');
        }
    }
);

export const register = createAsyncThunk(
    'auth/register',
    async (userData, { rejectWithValue }) => {
        try {
            const data = await registerApi(userData);
            return data;
        } catch (error) {
            return rejectWithValue(error.response?.data?.message || error.message || 'Registration failed');
        }
    }
);

export const validateToken = createAsyncThunk(
    'auth/validateToken',
    async (_, { rejectWithValue }) => {
        try {
            const isValid = await authService.validateAuthToken();
            if (isValid) {
                const userData = authService.getUserData();
                return {
                    user: userData,
                    token: localStorage.getItem('token'),
                    isValid: true
                };
            } else {
                throw new Error('Token validation failed');
            }
        } catch (error) {
            return rejectWithValue(error.message || 'Token validation failed');
        }
    }
);

const authSlice = createSlice({
    name: 'auth',
    initialState: {
        user: authService.getUserData(),
        token: localStorage.getItem('token'),
        isAuthenticated: authService.hasToken() && authService.getUserData() !== null,
        loading: false,
        error: null
    },
    reducers: {
        logout: (state) => {
            authService.logout();
            state.user = null;
            state.token = null;
            state.isAuthenticated = false;
        },
        clearError: (state) => {
            state.error = null;
        },
        setAuthFromService: (state) => {
            // Синхронизируем Redux state с AuthService
            state.user = authService.getUserData();
            state.token = localStorage.getItem('token');
            state.isAuthenticated = authService.hasToken() && authService.getUserData() !== null;
        }
    },
    extraReducers: (builder) => {
        builder
            // Login
            .addCase(login.pending, (state) => {
                state.loading = true;
                state.error = null;
            })
            .addCase(login.fulfilled, (state, action) => {
                state.loading = false;
                state.user = action.payload.user;
                state.token = action.payload.token;
                state.isAuthenticated = true;
                state.error = null;
            })
            .addCase(login.rejected, (state, action) => {
                state.loading = false;
                state.error = action.payload;
                state.isAuthenticated = false;
                state.user = null;
                state.token = null;
            })
            // Register
            .addCase(register.pending, (state) => {
                state.loading = true;
                state.error = null;
            })
            .addCase(register.fulfilled, (state) => {
                state.loading = false;
                state.error = null;
                // После регистрации пользователь не аутентифицирован
                // Он должен сначала залогиниться
            })
            .addCase(register.rejected, (state, action) => {
                state.loading = false;
                state.error = action.payload;
            })
            // Token validation
            .addCase(validateToken.pending, (state) => {
                state.loading = true;
            })
            .addCase(validateToken.fulfilled, (state, action) => {
                state.loading = false;
                state.user = action.payload.user;
                state.token = action.payload.token;
                state.isAuthenticated = true;
                state.error = null;
            })
            .addCase(validateToken.rejected, (state, action) => {
                state.loading = false;
                state.isAuthenticated = false;
                state.user = null;
                state.token = null;
                state.error = action.payload;
            });
    }
});

export const { logout, clearError, setAuthFromService } = authSlice.actions;
export default authSlice.reducer;