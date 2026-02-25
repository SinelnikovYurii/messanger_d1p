import SimplePeer from 'simple-peer';
import chatService from './chatService';
import api from './api';

/**
 * WebRTCService — управляет WebRTC P2P звонками (1-1).
 *
 * Взаимодействие:
 *   - Сигналинг (SDP/ICE) передаётся через существующий WebSocket (chatService).
 *   - TURN credentials запрашиваются с бэка при старте каждого звонка.
 *   - Входящий звонок → событие 'incomingCall'
 *   - Звонок принят  → событие 'callAccepted'
 *   - Звонок завершён → событие 'callEnded'
 *   - Удалённый стрим → событие 'remoteStream'
 */
class WebRTCService {
    constructor() {
        this._peer = null;
        this._localStream = null;
        this._callId = null;
        this._callType = 'video'; // 'video' | 'audio'
        this._isCaller = false;
        this._handlers = {}; // eventName → Set<callback>

        // Подписываемся на WebSocket сообщения сигналинга
        this._wsUnsubscribe = chatService.onMessage(this._handleSignal.bind(this));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Инициировать исходящий звонок.
     * @param {number} targetUserId
     * @param {'video'|'audio'} callType
     * @returns {Promise<MediaStream>} локальный MediaStream
     */
    async initiateCall(targetUserId, callType = 'video') {
        if (this._peer) {
            console.warn('[WebRTC] Already in a call');
            return;
        }

        this._callType = callType;
        this._isCaller = true;
        this._callId = crypto.randomUUID();

        const stream = await this._getUserMedia(callType);
        this._localStream = stream;

        const iceServers = await this._fetchIceServers();

        this._peer = new SimplePeer({
            initiator: true,
            trickle: true,
            stream,
            config: { iceServers },
        });

        this._bindPeerEvents(targetUserId);

        console.log('[WebRTC] Initiated call to user', targetUserId, 'callId:', this._callId);
        return stream;
    }

    /**
     * Ответить на входящий звонок.
     * @param {string} callId
     * @param {string} sdpOffer — SDP offer от звонящего
     * @param {number} callerId
     * @param {'video'|'audio'} callType
     * @returns {Promise<MediaStream>} локальный MediaStream
     */
    async answerCall(callId, sdpOffer, callerId, callType = 'video') {
        if (this._peer) {
            console.warn('[WebRTC] Already in a call, sending BUSY');
            this._sendSignal({ type: 'CALL_BUSY', callId, targetUserId: callerId });
            return;
        }

        this._callId = callId;
        this._callType = callType;
        this._isCaller = false;

        const stream = await this._getUserMedia(callType);
        this._localStream = stream;

        const iceServers = await this._fetchIceServers();

        this._peer = new SimplePeer({
            initiator: false,
            trickle: true,
            stream,
            config: { iceServers },
        });

        this._bindPeerEvents(callerId);

        // Подаём полученный offer в SimplePeer
        this._peer.signal(sdpOffer);

        console.log('[WebRTC] Answering call', callId, 'from user', callerId);
        return stream;
    }

    /**
     * Завершить текущий звонок.
     * @param {number} targetUserId — кому отправить CALL_END
     */
    hangUp(targetUserId) {
        if (!this._peer && !this._callId) return;

        this._sendSignal({
            type: 'CALL_END',
            callId: this._callId,
            targetUserId,
        });

        this._cleanup();
        console.log('[WebRTC] Hung up, callId:', this._callId);
    }

    /**
     * Отклонить входящий звонок.
     * @param {string} callId
     * @param {number} callerId
     */
    rejectCall(callId, callerId) {
        this._sendSignal({
            type: 'CALL_REJECT',
            callId,
            targetUserId: callerId,
        });
        console.log('[WebRTC] Rejected call', callId);
    }

    /**
     * Переключить микрофон.
     */
    toggleMute() {
        if (!this._localStream) return false;
        const audioTrack = this._localStream.getAudioTracks()[0];
        if (audioTrack) {
            audioTrack.enabled = !audioTrack.enabled;
            return !audioTrack.enabled; // true = muted
        }
        return false;
    }

    /**
     * Переключить камеру.
     */
    toggleCamera() {
        if (!this._localStream) return false;
        const videoTrack = this._localStream.getVideoTracks()[0];
        if (videoTrack) {
            videoTrack.enabled = !videoTrack.enabled;
            return !videoTrack.enabled; // true = cameraOff
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Event bus
    // ──────────────────────────────────────────────────────────────────────────

    on(event, callback) {
        if (!this._handlers[event]) this._handlers[event] = new Set();
        this._handlers[event].add(callback);
        return () => this._handlers[event]?.delete(callback);
    }

    _emit(event, data) {
        this._handlers[event]?.forEach(cb => {
            try { cb(data); } catch (e) { console.error('[WebRTC] Handler error:', e); }
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WebSocket signal routing
    // ──────────────────────────────────────────────────────────────────────────

    _handleSignal(message) {
        switch (message.type) {
            case 'CALL_OFFER':
                this._emit('incomingCall', {
                    callId: message.callId,
                    callerId: message.userId,
                    callerName: message.username,
                    callerAvatar: message.avatarUrl || message.profilePictureUrl || null,
                    callType: message.callType || 'video',
                    sdpOffer: message.sdp ? { type: message.sdpType || 'offer', sdp: message.sdp } : null,
                });
                break;

            case 'CALL_ANSWER':
                if (this._peer && message.sdp) {
                    this._peer.signal({ type: message.sdpType || 'answer', sdp: message.sdp });
                    this._emit('callAccepted', { callId: message.callId });
                }
                break;

            case 'ICE_CANDIDATE':
                if (this._peer && message.candidate) {
                    try {
                        const candidate = typeof message.candidate === 'string'
                            ? JSON.parse(message.candidate)
                            : message.candidate;
                        this._peer.signal({ candidate });
                    } catch (e) {
                        console.error('[WebRTC] Failed to parse ICE candidate:', e);
                    }
                }
                break;

            case 'CALL_REJECT':
                this._emit('callRejected', { callId: message.callId });
                this._cleanup();
                break;

            case 'CALL_END':
                this._emit('callEnded', { callId: message.callId });
                this._cleanup();
                break;

            case 'CALL_BUSY':
                this._emit('callBusy', { callId: message.callId });
                this._cleanup();
                break;

            case 'CALL_MISSED':
                this._emit('callMissed', { callId: message.callId, userId: message.userId });
                break;

            case 'CALL_RINGING':
                this._emit('callRinging', { callId: message.callId });
                break;

            default:
                break;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SimplePeer events
    // ──────────────────────────────────────────────────────────────────────────

    _bindPeerEvents(peerId) {
        this._peer.on('signal', data => {
            // SimplePeer вызывает 'signal' для SDP offer/answer и ICE candidates
            if (data.type === 'offer' || data.type === 'answer') {
                this._sendSignal({
                    type: data.type === 'offer' ? 'CALL_OFFER' : 'CALL_ANSWER',
                    callId: this._callId,
                    targetUserId: peerId,
                    sdp: data.sdp,
                    sdpType: data.type,
                    callType: this._callType,
                });
            } else if (data.candidate) {
                this._sendSignal({
                    type: 'ICE_CANDIDATE',
                    callId: this._callId,
                    targetUserId: peerId,
                    candidate: JSON.stringify(data.candidate),
                    sdpMid: data.candidate.sdpMid,
                    sdpMLineIndex: data.candidate.sdpMLineIndex,
                });
            }
        });

        this._peer.on('stream', remoteStream => {
            console.log('[WebRTC] Got remote stream');
            this._emit('remoteStream', { stream: remoteStream });
        });

        this._peer.on('connect', () => {
            console.log('[WebRTC] P2P data channel connected');
            this._emit('connected', { callId: this._callId });
        });

        this._peer.on('close', () => {
            console.log('[WebRTC] Peer connection closed');
            this._emit('callEnded', { callId: this._callId });
            this._cleanup();
        });

        this._peer.on('error', err => {
            console.error('[WebRTC] Peer error:', err);
            this._emit('error', { error: err, callId: this._callId });
            this._cleanup();
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    async _getUserMedia(callType) {
        const constraints = callType === 'video'
            ? { video: { width: { ideal: 1280 }, height: { ideal: 720 } }, audio: true }
            : { video: false, audio: true };

        try {
            return await navigator.mediaDevices.getUserMedia(constraints);
        } catch (err) {
            console.error('[WebRTC] getUserMedia failed:', err);
            throw err;
        }
    }

    async _fetchIceServers() {
        try {
            const response = await api.get('/api/turn/credentials');
            const { urls, username, credential } = response.data;
            return [
                { urls: 'stun:stun.l.google.com:19302' }, // fallback public STUN
                { urls, username, credential },            // наш TURN
            ];
        } catch (err) {
            console.warn('[WebRTC] Failed to fetch TURN credentials, using public STUN only:', err);
            return [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' },
            ];
        }
    }

    _sendSignal(payload) {
        chatService.sendWebSocketMessage(payload);
    }

    _cleanup() {
        if (this._peer) {
            this._peer.destroy();
            this._peer = null;
        }
        if (this._localStream) {
            this._localStream.getTracks().forEach(t => t.stop());
            this._localStream = null;
        }
        this._callId = null;
        this._isCaller = false;
    }

    destroy() {
        this._cleanup();
        if (this._wsUnsubscribe) {
            this._wsUnsubscribe();
            this._wsUnsubscribe = null;
        }
    }
}

// Singleton
const webRTCService = new WebRTCService();
export default webRTCService;
export { WebRTCService };
