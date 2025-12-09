
// Генерация пары ключей (ECDH)
export async function generateKeyPair() {
  const keyPair = await window.crypto.subtle.generateKey(
    {
      name: 'ECDH',
      namedCurve: 'P-256',
    },
    true,
    ['deriveKey', 'deriveBits']
  );
  return keyPair;
}

// Экспорт публичного ключа в формат для отправки на сервер
export async function exportPublicKey(key) {
  const exported = await window.crypto.subtle.exportKey('raw', key);
  return btoa(String.fromCharCode(...new Uint8Array(exported)));
}

// Импорт публичного ключа с сервера (JWK или raw Base64)
export async function importPublicKey(key) {
  console.log('[CRYPTO] importPublicKey called with:', {
    type: typeof key,
    isObject: typeof key === 'object',
    hasKty: key?.kty,
    keyPreview: typeof key === 'string' ? key.substring(0, 50) + '...' : key
  });

  if (typeof key === 'object' && key.kty === 'EC') {
    // JWK
    console.log('[CRYPTO] Importing as JWK format');
    return await window.crypto.subtle.importKey(
      'jwk',
      key,
      {
        name: 'ECDH',
        namedCurve: 'P-256',
      },
      true,
      []
    );
  } else if (typeof key === 'string') {
    // raw Base64
    console.log('[CRYPTO] Importing as raw Base64 format, length:', key.length);
    try {
      const binary = Uint8Array.from(atob(key), c => c.charCodeAt(0));
      console.log('[CRYPTO] Binary length:', binary.length, 'Expected: 65 (uncompressed) or 33 (compressed)');
      console.log('[CRYPTO] First byte (compression flag):', binary[0], 'Expected: 0x04 for uncompressed');

      return await window.crypto.subtle.importKey(
        'raw',
        binary,
        {
          name: 'ECDH',
          namedCurve: 'P-256',
        },
        true,
        []
      );
    } catch (error) {
      console.error('[CRYPTO] Failed to import raw key:', error);
      console.error('[CRYPTO] Key details:', {
        length: key.length,
        first20chars: key.substring(0, 20)
      });
      throw error;
    }
  } else {
    console.error('[CRYPTO] Unknown key format:', key);
    throw new Error('Unknown public key format');
  }
}

// Генерация сессионного ключа (ECDH)
export async function deriveSessionKey(privateKey, publicKey) {
  return await window.crypto.subtle.deriveKey(
    {
      name: 'ECDH',
      public: publicKey,
    },
    privateKey,
    {
      name: 'AES-GCM',
      length: 256,
    },
    true,
    ['encrypt', 'decrypt']
  );
}

// Шифрование сообщения
export async function encryptMessage(sessionKey, message) {
  const iv = window.crypto.getRandomValues(new Uint8Array(12));
  const encoded = new TextEncoder().encode(message);
  const ciphertext = await window.crypto.subtle.encrypt(
    {
      name: 'AES-GCM',
      iv,
    },
    sessionKey,
    encoded
  );
  return {
    iv: btoa(String.fromCharCode(...iv)),
    ciphertext: btoa(String.fromCharCode(...new Uint8Array(ciphertext))),
  };
}

// Дешифрование сообщения
export async function decryptMessage(sessionKey, encrypted) {
  const iv = Uint8Array.from(atob(encrypted.iv), c => c.charCodeAt(0));
  const ciphertext = Uint8Array.from(atob(encrypted.ciphertext), c => c.charCodeAt(0));
  const decrypted = await window.crypto.subtle.decrypt(
    {
      name: 'AES-GCM',
      iv,
    },
    sessionKey,
    ciphertext
  );
  return new TextDecoder().decode(decrypted);
}

// Генерация X3DH ключей
export async function generateX3DHKeys() {
  // Identity key
  const identityKeyPair = await window.crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveKey', 'deriveBits']
  );
  // Signed prekey
  const signedPreKeyPair = await window.crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveKey', 'deriveBits']
  );
  // One-time prekeys (например, 10 штук)
  const oneTimePreKeys = [];
  for (let i = 0; i < 10; i++) {
    const kp = await window.crypto.subtle.generateKey(
      { name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveKey', 'deriveBits']
    );
    oneTimePreKeys.push(kp);
  }
  return {
    identityKeyPair,
    signedPreKeyPair,
    oneTimePreKeys
  };
}

// Подпись signedPreKey публичным ключом identityKey
export async function signPreKey(identityKeyPair, signedPreKeyPair) {
  const data = await window.crypto.subtle.exportKey('raw', signedPreKeyPair.publicKey);
  return await window.crypto.subtle.sign(
    { name: 'ECDSA', hash: { name: 'SHA-256' } },
    identityKeyPair.privateKey,
    data
  );
}

// Экспорт X3DH ключей для отправки на сервер (с подписью)
export async function exportX3DHBundleWithSignature(keys) {
  const identityKey = await window.crypto.subtle.exportKey('raw', keys.identityKeyPair.publicKey);
  const signedPreKey = await window.crypto.subtle.exportKey('raw', keys.signedPreKeyPair.publicKey);
  const oneTimePreKeys = await Promise.all(
    keys.oneTimePreKeys.map(async kp => btoa(String.fromCharCode(...new Uint8Array(await window.crypto.subtle.exportKey('raw', kp.publicKey)))) )
  );
  // Подпись
  const signature = await signPreKey(keys.identityKeyPair, keys.signedPreKeyPair);
  return {
    identityKey: btoa(String.fromCharCode(...new Uint8Array(identityKey))),
    signedPreKey: btoa(String.fromCharCode(...new Uint8Array(signedPreKey))),
    oneTimePreKeys: JSON.stringify(oneTimePreKeys),
    signedPreKeySignature: btoa(String.fromCharCode(...new Uint8Array(signature)))
  };
}

// Экспорт X3DH ключей для отправки на сервер
export async function exportX3DHBundle(keys) {
  const identityKey = await window.crypto.subtle.exportKey('raw', keys.identityKeyPair.publicKey);
  const signedPreKey = await window.crypto.subtle.exportKey('raw', keys.signedPreKeyPair.publicKey);
  const oneTimePreKeys = await Promise.all(
    keys.oneTimePreKeys.map(async kp => btoa(String.fromCharCode(...new Uint8Array(await window.crypto.subtle.exportKey('raw', kp.publicKey)))) )
  );
  return {
    identityKey: btoa(String.fromCharCode(...new Uint8Array(identityKey))),
    signedPreKey: btoa(String.fromCharCode(...new Uint8Array(signedPreKey))),
    oneTimePreKeys: JSON.stringify(oneTimePreKeys)
  };
}
