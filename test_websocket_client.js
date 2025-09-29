const WebSocket = require('ws');

console.log('Testing WebSocket connection with token...');

// Тестируем подключение с токеном
const testToken = 'eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsInVzZXJJZCI6MSwiZXhwIjoxNzM3OTg1MjAwfQ.test_token_for_testing';

const ws = new WebSocket(`ws://localhost:8092/ws/chat?token=${testToken}`);

ws.on('open', function open() {
    console.log('✅ WebSocket connection opened successfully!');

    // Отправляем тестовое сообщение
    ws.send(JSON.stringify({
        type: 'TEST_MESSAGE',
        content: 'Hello from test client!'
    }));
});

ws.on('message', function message(data) {
    console.log('📨 Received message:', data.toString());
});

ws.on('close', function close(code, reason) {
    console.log(`❌ Connection closed: Code ${code}, Reason: ${reason}`);
});

ws.on('error', function error(err) {
    console.log('🚨 WebSocket error:', err.message);
});

// Закрываем соединение через 5 секунд
setTimeout(() => {
    console.log('Closing test connection...');
    ws.close();
    process.exit(0);
}, 5000);
