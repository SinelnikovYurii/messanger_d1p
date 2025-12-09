
export async function exportPrivateKey() {
  const privateKeyJwk = localStorage.getItem('e2ee_privateKey');
  if (!privateKeyJwk) throw new Error('Приватный ключ не найден');
  // Можно сохранить как файл
  const blob = new Blob([privateKeyJwk], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  return url;
}

// Импорт приватного ключа пользователя (E2EE)
export async function importPrivateKeyFromFile(file) {
  const text = await file.text();
  // Проверка формата
  try {
    const jwk = JSON.parse(text);
    localStorage.setItem('e2ee_privateKey', JSON.stringify(jwk));
    return true;
  } catch {
    throw new Error('Некорректный формат ключа');
  }
}

