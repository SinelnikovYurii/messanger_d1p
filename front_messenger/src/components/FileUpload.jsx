import React, { useState, useRef, forwardRef, useImperativeHandle } from 'react';
import chatService from '../services/chatService';

const FileUpload = forwardRef(({ chatId, onFileUploaded }, ref) => {
    const [uploading, setUploading] = useState(false);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [selectedFile, setSelectedFile] = useState(null);
    const [showPreview, setShowPreview] = useState(false);
    const [caption, setCaption] = useState('');
    const fileInputRef = useRef(null);

    // Экспортируем метод для внешнего использования
    useImperativeHandle(ref, () => ({
        selectFile: (file) => {
            setSelectedFile(file);
            setShowPreview(true);
        }
    }));

    const handleFileSelect = (event) => {
        const file = event.target.files[0];
        if (file) {
            setSelectedFile(file);
            setShowPreview(true);
        }
    };

    const handleUpload = async () => {
        if (!selectedFile || !chatId) return;

        try {
            setUploading(true);
            const message = await chatService.uploadFile(selectedFile, chatId, caption || null);

            // Уведомляем родительский компонент
            if (onFileUploaded) {
                onFileUploaded(message);
            }

            // Сбрасываем состояние
            setSelectedFile(null);
            setShowPreview(false);
            setCaption('');
            setUploadProgress(0);
            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }
        } catch (error) {
            console.error('Ошибка загрузки файла:', error);
            alert('Ошибка загрузки файла: ' + (error.response?.data?.message || error.message));
        } finally {
            setUploading(false);
        }
    };

    const handleCancel = () => {
        setSelectedFile(null);
        setShowPreview(false);
        setCaption('');
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
    };

    const formatFileSize = (bytes) => {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
    };

    const getFileIcon = (file) => {
        if (file.type.startsWith('image/')) return '🖼️';
        if (file.type.startsWith('video/')) return '🎥';
        if (file.type.startsWith('audio/')) return '🎵';
        if (file.type.includes('pdf')) return '📄';
        if (file.type.includes('word')) return '📝';
        if (file.type.includes('excel') || file.type.includes('sheet')) return '📊';
        if (file.type.includes('zip') || file.type.includes('rar')) return '📦';
        return '';
    };

    return (
        <div className="relative">
            <input
                ref={fileInputRef}
                type="file"
                onChange={handleFileSelect}
                className="hidden"
                accept="image/*,video/*,audio/*,.pdf,.doc,.docx,.xls,.xlsx,.txt,.zip,.rar"
            />

            <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading}
                className="p-2 text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded-full transition-colors disabled:opacity-50"
                title="Прикрепить файл"
            >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" />
                </svg>
            </button>

            {/* Модальное окно предпросмотра */}
            {showPreview && selectedFile && (
                <div className="fixed inset-0 flex items-center justify-center z-50" style={{background: 'rgba(255,218,185,0.7)'}}> {/* #FFDAB9 с прозрачностью */}
                    <div className="rounded-lg p-6 max-w-lg w-full mx-4" style={{background: '#FFDAB9', color: '#520808', boxShadow: '0 4px 32px rgba(82,8,8,0.12)'}}>
                        <h3 className="text-lg font-semibold mb-4" style={{color: '#520808'}}>Отправить файл</h3>

                        {/* Предпросмотр файла */}
                        <div className="mb-4">
                            {selectedFile.type.startsWith('image/') ? (
                                <img
                                    src={URL.createObjectURL(selectedFile)}
                                    alt="Preview"
                                    className="max-w-full max-h-64 mx-auto rounded"
                                    style={{border: '2px solid #B22222'}}
                                />
                            ) : (
                                <div className="flex items-center justify-center p-8 rounded" style={{background: '#F5DEB3'}}>
                                    <div className="text-center">
                                        <div className="text-5xl mb-2" style={{color: '#520808'}}>{getFileIcon(selectedFile)}</div>
                                        <div className="text-sm font-medium" style={{color: '#520808'}}>{selectedFile.name}</div>
                                        <div className="text-xs" style={{color: '#B22222'}}>{formatFileSize(selectedFile.size)}</div>
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Подпись к файлу */}
                        <div className="mb-4">
                            <label className="block text-sm font-medium mb-2" style={{color: '#520808'}}>Подпись (необязательно)</label>
                            <input
                                type="text"
                                value={caption}
                                onChange={(e) => setCaption(e.target.value)}
                                placeholder="Добавьте подпись к файлу..."
                                className="w-full px-3 py-2 rounded-lg focus:outline-none focus:ring-2"
                                style={{background: '#F5DEB3', color: '#520808', border: '2px solid #B22222'}}
                                disabled={uploading}
                            />
                        </div>

                        {/* Прогресс загрузки */}
                        {uploading && (
                            <div className="mb-4">
                                <div className="rounded-full h-2" style={{background: '#F5DEB3'}}>
                                    <div
                                        className="h-2 rounded-full transition-all duration-300"
                                        style={{width: `${uploadProgress}%`, background: '#B22222'}}
                                    />
                                </div>
                                <div className="text-sm text-center mt-1" style={{color: '#B22222'}}>
                                    Загрузка... {uploadProgress}%
                                </div>
                            </div>
                        )}

                        {/* Кнопки действий */}
                        <div className="flex justify-end space-x-2">
                            <button
                                onClick={handleCancel}
                                disabled={uploading}
                                className="px-4 py-2 rounded transition-colors"
                                style={{background: '#F5DEB3', color: '#520808', border: '2px solid #B22222'}}
                            >
                                Отмена
                            </button>
                            <button
                                onClick={handleUpload}
                                disabled={uploading}
                                className="px-4 py-2 rounded font-bold transition-colors"
                                style={{background: '#B22222', color: '#fff', border: 'none'}}
                                onMouseOver={e => e.target.style.background = '#520808'}
                                onMouseOut={e => e.target.style.background = '#B22222'}
                            >
                                {uploading ? 'Отправка...' : 'Отправить'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
});

// Экспортируем также drag handlers для использования в родительском компоненте
export const useDragAndDrop = (onFileDrop) => {
    const [isDragging, setIsDragging] = useState(false);
    const dragCounter = useRef(0);
    const dragTimeout = useRef(null);

    const handleDragEnter = (e) => {
        e.preventDefault();
        e.stopPropagation();

        // Отменяем таймаут если он был
        if (dragTimeout.current) {
            clearTimeout(dragTimeout.current);
            dragTimeout.current = null;
        }

        dragCounter.current++;
        console.log('DragEnter - counter:', dragCounter.current);

        if (e.dataTransfer.items && e.dataTransfer.items.length > 0) {
            if (!isDragging) {
                console.log('Setting isDragging to TRUE');
                setIsDragging(true);
            }
        }
    };

    const handleDragLeave = (e) => {
        e.preventDefault();
        e.stopPropagation();
        dragCounter.current--;
        console.log('DragLeave - counter:', dragCounter.current);

        // Используем таймаут для сброса состояния, чтобы избежать мерцания
        if (dragCounter.current === 0) {
            dragTimeout.current = setTimeout(() => {
                console.log('Setting isDragging to FALSE (delayed)');
                setIsDragging(false);
            }, 50); // Небольшая задержка 50ms
        }
    };

    const handleDragOver = (e) => {
        e.preventDefault();
        e.stopPropagation();
    };

    const handleDrop = (e) => {
        e.preventDefault();
        e.stopPropagation();
        console.log('Drop - resetting state');

        // Отменяем таймаут
        if (dragTimeout.current) {
            clearTimeout(dragTimeout.current);
            dragTimeout.current = null;
        }

        setIsDragging(false);
        dragCounter.current = 0;

        const files = e.dataTransfer.files;
        if (files && files.length > 0) {
            console.log('File dropped:', files[0].name);
            onFileDrop(files[0]);
        }
    };

    return {
        isDragging,
        dragHandlers: {
            onDragEnter: handleDragEnter,
            onDragLeave: handleDragLeave,
            onDragOver: handleDragOver,
            onDrop: handleDrop
        }
    };
};

export default FileUpload;
