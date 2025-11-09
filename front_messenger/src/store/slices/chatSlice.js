import { createSlice } from '@reduxjs/toolkit';

const chatSlice = createSlice({
    name: 'chat',
    initialState: {
        messages: [],
        users: [],
        currentChatUser: null,
        isConnected: false,
        loading: false
    },
    reducers: {
        addMessage: (state, action) => {
            state.messages.push(action.payload);
        },
        setMessages: (state, action) => {
            state.messages = action.payload;
        },
        setUsers: (state, action) => {
            state.users = action.payload;
        },
        setCurrentChatUser: (state, action) => {
            state.currentChatUser = action.payload;
        },
        setConnected: (state, action) => {
            state.isConnected = action.payload;
        },
        clearMessages: (state) => {
            state.messages = [];
        }
    }
});

export const { addMessage, setMessages, setUsers, setCurrentChatUser, setConnected, clearMessages } = chatSlice.actions;
export default chatSlice.reducer;