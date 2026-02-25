import React, { useEffect, useRef, useState, useCallback } from 'react';
import webRTCService from '../services/WebRTCService';
import { getAvatarUrl } from '../utils/avatarUtils';

/* ─── Хук: анализ уровня звука из MediaStream ─────────────────────────────
   Возвращает число 0..1 (~60 fps), обновляет state только при изменении.
   isSpeaking = true когда уровень > threshold.
*/
function useVoiceActivity(stream, { threshold = 0.015, smoothing = 0.8 } = {}) {
    const [isSpeaking, setIsSpeaking] = useState(false);
    const [volume, setVolume]         = useState(0);        // 0..1
    const rafRef    = useRef(null);
    const ctxRef    = useRef(null);
    const analyserRef = useRef(null);
    const dataRef   = useRef(null);

    useEffect(() => {
        if (!stream) return;

        try {
            const ctx      = new (window.AudioContext || window.webkitAudioContext)();
            const source   = ctx.createMediaStreamSource(stream);
            const analyser = ctx.createAnalyser();
            analyser.fftSize       = 256;
            analyser.smoothingTimeConstant = smoothing;
            source.connect(analyser);

            ctxRef.current    = ctx;
            analyserRef.current = analyser;
            dataRef.current   = new Uint8Array(analyser.frequencyBinCount);

            const tick = () => {
                analyser.getByteFrequencyData(dataRef.current);
                const sum = dataRef.current.reduce((a, b) => a + b, 0);
                const avg = sum / dataRef.current.length / 255;   // 0..1
                setVolume(avg);
                setIsSpeaking(avg > threshold);
                rafRef.current = requestAnimationFrame(tick);
            };
            rafRef.current = requestAnimationFrame(tick);
        } catch (_) {}

        return () => {
            cancelAnimationFrame(rafRef.current);
            analyserRef.current?.disconnect();
            ctxRef.current?.close();
            setIsSpeaking(false);
            setVolume(0);
        };
    }, [stream, threshold, smoothing]);

    return { isSpeaking, volume };
}

/* ─── Компонент аватарки с индикатором речи ────────────────────────────── */
function PeerAvatar({ name, avatarUrl, isSpeaking, volume, size = 'lg' }) {
    const sizes = {
        lg:  { outer: 'w-36 h-36', text: 'text-6xl',  ring: 148, stroke: 4 },
        xl:  { outer: 'w-44 h-44', text: 'text-7xl',  ring: 180, stroke: 5 },
    };
    const s = sizes[size] || sizes.lg;
    // Радиус SVG-кольца
    const r      = s.ring / 2 - s.stroke;
    const circ   = 2 * Math.PI * r;
    // Прогресс кольца: от 0 (тихо) до 1 (громко)
    const progress = isSpeaking ? Math.min(volume * 6, 1) : 0;
    const dash     = circ * progress;

    return (
        <div className="relative flex items-center justify-center" style={{ width: s.ring, height: s.ring }}>
            {/* SVG пульсирующее кольцо */}
            <svg className="absolute inset-0" width={s.ring} height={s.ring}
                style={{ transform: 'rotate(-90deg)' }}>
                {/* Фоновое кольцо */}
                <circle cx={s.ring/2} cy={s.ring/2} r={r}
                    fill="none"
                    stroke="rgba(245,245,220,0.15)"
                    strokeWidth={s.stroke} />
                {/* Активное кольцо */}
                <circle cx={s.ring/2} cy={s.ring/2} r={r}
                    fill="none"
                    stroke={isSpeaking ? '#F5F5DC' : 'rgba(245,245,220,0.3)'}
                    strokeWidth={s.stroke}
                    strokeDasharray={`${dash} ${circ}`}
                    strokeLinecap="round"
                    style={{ transition: 'stroke-dasharray 0.08s ease, stroke 0.2s ease' }}
                />
            </svg>

            {/* Внешнее мягкое свечение при речи */}
            {isSpeaking && (
                <div className="absolute inset-0 rounded-full"
                    style={{
                        boxShadow: `0 0 ${12 + volume * 24}px ${4 + volume * 8}px rgba(245,220,180,0.25)`,
                        borderRadius: '50%',
                        transition: 'box-shadow 0.1s ease',
                    }} />
            )}

            {/* Сам аватар */}
            <div className={`${s.outer} rounded-full overflow-hidden flex items-center justify-center font-bold shadow-xl`}
                style={{
                    backgroundColor: '#B22222',
                    color: '#F5F5DC',
                    border: `3px solid ${isSpeaking ? 'rgba(245,245,220,0.6)' : 'rgba(245,245,220,0.2)'}`,
                    transition: 'border-color 0.2s ease',
                    flexShrink: 0,
                }}>
                {avatarUrl ? (
                    <img src={avatarUrl} alt={name}
                        className="w-full h-full object-cover"
                        onError={e => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex'; }} />
                ) : null}
                <span className={`${s.text} w-full h-full flex items-center justify-center`}
                    style={{ display: avatarUrl ? 'none' : 'flex' }}>
                    {name?.charAt(0)?.toUpperCase() || '?'}
                </span>
            </div>
        </div>
    );
}

/* ─── Основной компонент CallWindow ──────────────────────────────────────── */
const CallWindow = ({ callId, peerId, peerName, peerAvatar, callType = 'video', localStream, onHangUp }) => {
    const localVideoRef  = useRef(null);
    const remoteVideoRef = useRef(null);
    const localAudioRef  = useRef(null);
    const remoteAudioRef = useRef(null);

    const [isMuted, setIsMuted]         = useState(false);
    const [cameraOff, setCameraOff]     = useState(false);
    const [duration, setDuration]       = useState(0);
    const [status, setStatus]           = useState('Соединение...');
    const [remoteStream, setRemoteStream] = useState(null);
    const timerRef = useRef(null);

    // Голосовая активность собеседника (по удалённому аудиострим)
    const { isSpeaking, volume } = useVoiceActivity(remoteStream, { threshold: 0.012 });

    const resolvedAvatarUrl = getAvatarUrl(peerAvatar);

    // Локальный стрим → элементы
    useEffect(() => {
        if (localStream) {
            if (localVideoRef.current) localVideoRef.current.srcObject = localStream;
            if (localAudioRef.current) localAudioRef.current.srcObject = localStream;
        }
    }, [localStream]);

    // Подписка на события WebRTC
    useEffect(() => {
        const offStream = webRTCService.on('remoteStream', ({ stream }) => {
            setRemoteStream(stream);
            if (remoteVideoRef.current) remoteVideoRef.current.srcObject = stream;
            if (remoteAudioRef.current) remoteAudioRef.current.srcObject = stream;
            setStatus('В звонке');
            timerRef.current = setInterval(() => setDuration(d => d + 1), 1000);
        });
        const offConnected = webRTCService.on('connected', () => setStatus('В звонке'));
        const offEnded = webRTCService.on('callEnded', () => {
            setStatus('Завершён');
            clearInterval(timerRef.current);
            onHangUp?.();
        });
        return () => {
            offStream(); offConnected(); offEnded();
            clearInterval(timerRef.current);
        };
    }, [onHangUp]);

    const handleHangUp = useCallback(() => { webRTCService.hangUp(peerId); onHangUp?.(); }, [peerId, onHangUp]);
    const handleMute   = () => setIsMuted(webRTCService.toggleMute());
    const handleCamera = () => setCameraOff(webRTCService.toggleCamera());
    const formatTime   = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

    const isVideo = callType === 'video';

    return (
        <div className="fixed inset-0 z-50 flex flex-col" style={{ backgroundColor: '#1a0000' }}>
            {/* Скрытые аудио */}
            <audio ref={localAudioRef}  autoPlay muted style={{ display: 'none' }} />
            <audio ref={remoteAudioRef} autoPlay       style={{ display: 'none' }} />

            {/* ── Видеорежим ── */}
            {isVideo ? (
                <video ref={remoteVideoRef} autoPlay playsInline
                    className="absolute inset-0 w-full h-full object-cover" />
            ) : (
                /* ── Аудиорежим: аватарка + индикатор речи ── */
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-5"
                    style={{ background: 'linear-gradient(160deg, #3d0000 0%, #8B1A1A 50%, #3d0000 100%)' }}>

                    <PeerAvatar
                        name={peerName}
                        avatarUrl={resolvedAvatarUrl}
                        isSpeaking={isSpeaking}
                        volume={volume}
                        size="lg"
                    />

                    {/* Имя */}
                    <div className="text-center mt-2">
                        <p className="text-2xl font-bold" style={{ color: '#F5F5DC' }}>{peerName}</p>

                        {/* Статус / индикатор речи */}
                        <div className="flex items-center justify-center gap-2 mt-2" style={{ minHeight: 28 }}>
                            {isSpeaking ? (
                                /* Анимированные полоски "говорит" */
                                <div className="flex items-end gap-[3px]" style={{ height: 20 }}>
                                    {[0.6, 1, 0.75, 1, 0.6].map((h, i) => (
                                        <div key={i}
                                            style={{
                                                width: 4,
                                                height: `${Math.max(4, h * (6 + volume * 18))}px`,
                                                backgroundColor: '#FFDAB9',
                                                borderRadius: 2,
                                                animation: `voiceBar 0.${5 + i}s ease-in-out infinite alternate`,
                                                animationDelay: `${i * 0.08}s`,
                                                transition: 'height 0.07s ease',
                                            }} />
                                    ))}
                                    <span className="text-sm ml-2" style={{ color: '#FFDAB9' }}>Говорит...</span>
                                </div>
                            ) : (
                                <p className="text-sm" style={{ color: '#FFDAB9' }}>
                                    {status}{status === 'В звонке' ? ` · ${formatTime(duration)}` : ''}
                                </p>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* ── Шапка ── */}
            <div className="relative z-10 flex items-center px-6 pt-5 pb-4"
                style={{ background: 'linear-gradient(to bottom, rgba(139,26,26,0.9) 0%, transparent 100%)' }}>
                {/* Мини-аватар в шапке */}
                <div className="w-10 h-10 rounded-full overflow-hidden flex items-center justify-center mr-3 flex-shrink-0 font-bold text-base"
                    style={{ backgroundColor: '#B22222', color: '#F5F5DC', border: '2px solid rgba(245,245,220,0.4)' }}>
                    {resolvedAvatarUrl ? (
                        <img src={resolvedAvatarUrl} alt={peerName} className="w-full h-full object-cover"
                            onError={e => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex'; }} />
                    ) : null}
                    <span style={{ display: resolvedAvatarUrl ? 'none' : 'flex' }}
                        className="w-full h-full items-center justify-center">
                        {peerName?.charAt(0)?.toUpperCase() || '?'}
                    </span>
                </div>
                <div>
                    <p className="font-bold text-base leading-tight" style={{ color: '#F5F5DC' }}>{peerName}</p>
                    <p className="text-xs" style={{ color: '#FFDAB9' }}>
                        {isVideo
                            ? (status === 'В звонке' ? `📹 ${formatTime(duration)}` : `📹 ${status}`)
                            : (status === 'В звонке' ? `📞 ${formatTime(duration)}` : `📞 ${status}`)
                        }
                    </p>
                </div>
            </div>

            {/* ── PiP локальное видео ── */}
            {isVideo && (
                <div className="absolute top-20 right-4 z-20 w-32 h-48 rounded-xl overflow-hidden shadow-2xl"
                    style={{ border: '2px solid #B22222' }}>
                    <video ref={localVideoRef} autoPlay playsInline muted className="w-full h-full object-cover" />
                    {cameraOff && (
                        <div className="absolute inset-0 flex items-center justify-center"
                            style={{ backgroundColor: '#8B1A1A' }}>
                            <span className="text-xs" style={{ color: '#F5F5DC' }}>Камера выкл.</span>
                        </div>
                    )}
                </div>
            )}

            {/* ── Панель управления ── */}
            <div className="absolute bottom-0 left-0 right-0 z-10 flex items-center justify-center gap-5 pb-10 pt-8"
                style={{ background: 'linear-gradient(to top, rgba(139,26,26,0.92) 0%, transparent 100%)' }}>

                {/* Микрофон */}
                <button onClick={handleMute}
                    className="w-14 h-14 rounded-full flex items-center justify-center transition-all shadow-lg"
                    style={{
                        backgroundColor: isMuted ? '#B22222' : 'rgba(245,245,220,0.18)',
                        border: `2px solid ${isMuted ? '#F5F5DC' : 'rgba(245,245,220,0.4)'}`,
                    }}
                    title={isMuted ? 'Включить микрофон' : 'Выключить микрофон'}>
                    {isMuted ? (
                        <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24" style={{ color: '#F5F5DC' }}>
                            <path d="M19 11h-1.7c0 .74-.16 1.43-.43 2.05l1.23 1.23c.56-.98.9-2.09.9-3.28zm-4.02.17c0-.06.02-.11.02-.17V5c0-1.66-1.34-3-3-3S9 3.34 9 5v.18l5.98 5.99zM4.27 3L3 4.27l6.01 6.01V11c0 1.66 1.33 3 2.99 3 .22 0 .44-.03.65-.08l1.66 1.66c-.71.33-1.5.52-2.31.52-2.76 0-5.3-2.1-5.3-5.1H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c.91-.13 1.77-.45 2.54-.9L19.73 21 21 19.73 4.27 3z"/>
                        </svg>
                    ) : (
                        <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24" style={{ color: '#F5F5DC' }}>
                            <path d="M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z"/>
                        </svg>
                    )}
                </button>

                {/* Завершить */}
                <button onClick={handleHangUp}
                    className="w-16 h-16 rounded-full flex items-center justify-center transition-all shadow-xl"
                    style={{ backgroundColor: '#8B1A1A', border: '3px solid #F5F5DC' }}
                    title="Завершить звонок">
                    <svg className="w-8 h-8" fill="currentColor" viewBox="0 0 24 24" style={{ color: '#F5F5DC' }}>
                        <path d="M6.6 10.8c1.4 2.8 3.8 5.1 6.6 6.6l2.2-2.2c.3-.3.7-.4 1-.2 1.1.4 2.3.6 3.6.6.6 0 1 .4 1 1V20c0 .6-.4 1-1 1C9.7 21 3 14.3 3 6c0-.6.4-1 1-1h3.5c.6 0 1 .4 1 1 0 1.3.2 2.5.6 3.6.1.3 0 .7-.2 1L6.6 10.8z"/>
                    </svg>
                </button>

                {/* Камера */}
                {isVideo && (
                    <button onClick={handleCamera}
                        className="w-14 h-14 rounded-full flex items-center justify-center transition-all shadow-lg"
                        style={{
                            backgroundColor: cameraOff ? '#B22222' : 'rgba(245,245,220,0.18)',
                            border: `2px solid ${cameraOff ? '#F5F5DC' : 'rgba(245,245,220,0.4)'}`,
                        }}
                        title={cameraOff ? 'Включить камеру' : 'Выключить камеру'}>
                        {cameraOff ? (
                            <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24" style={{ color: '#F5F5DC' }}>
                                <path d="M3.27 2L2 3.27 4.73 6C4.28 6.16 4 6.57 4 7v10c0 .55.45 1 1 1h11.73l2 2L20.73 18.73 3.27 2zM17 10.5V7c0-.55-.45-1-1-1H6.27l9 9H16c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"/>
                            </svg>
                        ) : (
                            <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24" style={{ color: '#F5F5DC' }}>
                                <path d="M17 10.5V7a1 1 0 00-1-1H4a1 1 0 00-1 1v10a1 1 0 001 1h12a1 1 0 001-1v-3.5l4 4v-11l-4 4z"/>
                            </svg>
                        )}
                    </button>
                )}
            </div>
        </div>
    );
};

export default CallWindow;
