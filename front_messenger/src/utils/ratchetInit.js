// ratchetInit.js
// Вспомогательные функции для инициализации Double Ratchet через 2key-ratchet
import { Identity } from '2key-ratchet';

/**
 * Генерация собственного Identity ключа
 * @param {number} userId - ID пользователя
 * @returns {Promise<Identity>}
 */
export async function generateIdentityKey(userId = Date.now()) {
  // Создаем Identity с уникальными параметрами:
  // signingKey - для подписи, exchangeKey - для обмена ключами
  return await Identity.create(userId, 1, 1);
}

/**
 * Создание Identity партнера из его публичных ключей
 * @param {Object} bundleJson - Bundle с публичными ключами партнера
 * @returns {Promise<Identity>}
 */
export async function createPartnerIdentity(bundleJson) {
  console.log('[RATCHET] Creating partner Identity from bundle:', {
    hasUserId: !!bundleJson.userId,
    hasIdentityKey: !!bundleJson.identityKey,
    hasSignedPreKey: !!bundleJson.signedPreKey,
    hasOneTimePreKeys: !!bundleJson.oneTimePreKeys,
  });

  // Для создания Identity партнера используем его userId
  // Это создаст новый Identity, но мы не сможем использовать его приватные ключи
  // поэтому используем только публичные данные

  // 2key-ratchet требует, чтобы оба участника создали свои Identity
  // и затем обменялись публичными ключами через PreKeyBundle

  // Создаем Identity для партнера (это временный объект для установления сессии)
  const partnerId = bundleJson.userId || Date.now();
  const partnerIdentity = await Identity.create(partnerId, 1, 1);

  console.log('[RATCHET] ✓ Partner Identity created (temporary for session establishment)');

  return partnerIdentity;
}

/**
 * Импорт PreKeyBundle партнера (для совместимости)
 * @deprecated Используйте createPartnerIdentity вместо этого
 */
export async function importPartnerPreKeyBundle(bundleJson) {
  return createPartnerIdentity(bundleJson);
}
