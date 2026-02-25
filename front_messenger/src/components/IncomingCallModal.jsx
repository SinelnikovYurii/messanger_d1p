import React, { useEffect, useRef } from 'react';

function useRingtone() {
    const ctxRef = useRef(null);
    const intervalRef = useRef(null);
    useEffect(() => {
        const play = () => {
            try {
                const ctx = new (window.AudioContext || window.webkitAudioContext)();
                ctxRef.current = ctx;
                const beep = (freq, start, dur) => {
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    osc.connect(gain); gain.connect(ctx.destination);
                    osc.frequency.value = freq; osc.type = 'sine';
                    gain.gain.setValueAtTime(0.25, ctx.currentTime + start);
                    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + start + dur);
                    osc.start(ctx.currentTime + start);
                    osc.stop(ctx.currentTime + start + dur);
                };
                const ring = () => { beep(880, 0, 0.15); beep(660, 0.2, 0.15); };
                ring();
                intervalRef.current = setInterval(ring, 2000);
            } catch (_) {}
        };
        play();
        return () => { clearInterval(intervalRef.current); ctxRef.current?.close(); };
    }, []);
}

const IncomingCallModal = ({ caller, callType = 'video', onAccept, onReject }) => {
    useRingtone();
    const isVideo = callType === 'video';

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center"
            style={{ backgroundColor: 'rgba(26,0,0,0.75)', backdropFilter: 'blur(4px)' }}>
            <div className="flex flex-col items-center gap-6 rounded-2xl px-10 py-8 shadow-2xl min-w-[300px]"
                style={{
                    background: 'linear-gradient(160deg, #3d0000 0%, #8B1A1A 100%)',
                    border: '2px solid #B22222',
                }}>

                {/* Аватар */}
                <div className="w-24 h-24 rounded-full flex items-center justify-center text-4xl font-bold shadow-xl"
                    style={{ backgroundColor: '#B22222', color: '#F5F5DC', border: '3px solid rgba(245,245,220,0.3)' }}>
                    {caller?.name?.charAt(0)?.toUpperCase() || '?'}
                </div>

                {/* Имя и тип */}
                <div className="text-center">
                    <p className="text-xl font-bold mb-1" style={{ color: '#F5F5DC' }}>
                        {caller?.name || 'Неизвестный'}
                    </p>
                    <p className="text-sm" style={{ color: '#FFDAB9' }}>
                        {isVideo ? '📹 Входящий видеозвонок' : '📞 Входящий аудиозвонок'}
                    </p>
                </div>

                {/* Кнопки */}
                <div className="flex gap-10 mt-2">
                    {/* Отклонить */}
                    <div className="flex flex-col items-center gap-2">
                        <button onClick={onReject}
                            className="w-16 h-16 rounded-full flex items-center justify-center transition-all shadow-lg"
                            style={{ backgroundColor: '#4a0000', border: '2px solid #B22222' }}
                            title="Отклонить">
                            <svg className="w-7 h-7" fill="currentColor" viewBox="0 0 24 24" style={{ color: '#F5F5DC' }}>
                                <path d="M6.6 10.8c1.4 2.8 3.8 5.1 6.6 6.6l2.2-2.2c.3-.3.7-.4 1-.2 1.1.4 2.3.6 3.6.6.6 0 1 .4 1 1V20c0 .6-.4 1-1 1C9.7 21 3 14.3 3 6c0-.6.4-1 1-1h3.5c.6 0 1 .4 1 1 0 1.3.2 2.5.6 3.6.1.3 0 .7-.2 1L6.6 10.8z"/>
                            </svg>
                        </button>
                        <span className="text-xs" style={{ color: '#FFDAB9' }}>Отклонить</span>
                    </div>

                    {/* Принять */}
                    <div className="flex flex-col items-center gap-2">
                        <button onClick={onAccept}
                            className="w-16 h-16 rounded-full flex items-center justify-center transition-all shadow-lg"
                            style={{ backgroundColor: '#B22222', border: '2px solid #F5F5DC' }}
                            title="Принять">
                            {isVideo ? (
                                <svg className="w-7 h-7" fill="currentColor" viewBox="0 0 24 24" style={{ color: '#F5F5DC' }}>
                                    <path d="M17 10.5V7a1 1 0 00-1-1H4a1 1 0 00-1 1v10a1 1 0 001 1h12a1 1 0 001-1v-3.5l4 4v-11l-4 4z"/>
                                </svg>
                            ) : (
                                <svg className="w-7 h-7" fill="currentColor" viewBox="0 0 24 24" style={{ color: '#F5F5DC' }}>
                                    <path d="M6.6 10.8c1.4 2.8 3.8 5.1 6.6 6.6l2.2-2.2c.3-.3.7-.4 1-.2 1.1.4 2.3.6 3.6.6.6 0 1 .4 1 1V20c0 .6-.4 1-1 1C9.7 21 3 14.3 3 6c0-.6.4-1 1-1h3.5c.6 0 1 .4 1 1 0 1.3.2 2.5.6 3.6.1.3 0 .7-.2 1L6.6 10.8z"/>
                                </svg>
                            )}
                        </button>
                        <span className="text-xs" style={{ color: '#FFDAB9' }}>Принять</span>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default IncomingCallModal;
