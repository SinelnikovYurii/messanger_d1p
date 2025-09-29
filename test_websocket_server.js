const WebSocket = require('ws');
const http = require('http');
const url = require('url');

// Создаем HTTP сервер
const server = http.createServer();

// Создаем WebSocket сервер
const wss = new WebSocket.Server({
    server,
    path: '/chat'
});

wss.on('connection', function connection(ws, request) {
    const parameters = url.parse(request.url, true);
    const token = parameters.query.token;

    console.log('WebSocket connection established');
    console.log('Request URL:', request.url);
    console.log('Token from URL:', token ? token.substring(0, 20) + '...' : 'NO TOKEN');

    if (!token) {
        console.log('No token provided, closing connection');
        ws.close(1002, 'No authentication token provided');
        return;
    }

    // Простая проверка токена (для тестирования)
    if (token.length < 10) {
        console.log('Invalid token, closing connection');
        ws.close(1002, 'Invalid authentication token');
        return;
    }

    console.log('Token validated successfully');

    // Отправляем сообщение об успешной аутентификации
    ws.send(JSON.stringify({
        type: 'AUTH_SUCCESS',
        content: 'Authentication successful',
        timestamp: new Date().toISOString()
    }));

    ws.on('message', function message(data) {
        console.log('Received message:', data.toString());

        // Эхо сообщение обратно
        ws.send(JSON.stringify({
            type: 'ECHO',
            content: 'Echo: ' + data.toString(),
            timestamp: new Date().toISOString()
        }));
    });

    ws.on('close', function close() {
        console.log('WebSocket connection closed');
    });

    ws.on('error', function error(err) {
        console.log('WebSocket error:', err);
    });
});

// Запускаем сервер на порту 8092
server.listen(8092, function listening() {
    console.log('Test WebSocket server started on port 8092');
    console.log('Connect to: ws://localhost:8092/chat?token=your_token_here');
});
