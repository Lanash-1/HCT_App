'use strict';

const MAX_MESSAGE_LENGTH = 140;

/**
 * Mirrors the validation in app/src/main/java/com/codigitech/belay/data/repository/InteractionRepository.kt —
 * this is the server-authoritative copy (docs/DATA_MODEL.md: enforced in the Catalyst Function,
 * not just client-side).
 *
 * @param {unknown} message
 * @returns {'BLANK'|'TOO_LONG'|null}
 */
function validateMessage(message) {
  if (typeof message !== 'string' || message.trim().length === 0) return 'BLANK';
  if (message.length > MAX_MESSAGE_LENGTH) return 'TOO_LONG';
  return null;
}

module.exports = { validateMessage, MAX_MESSAGE_LENGTH };
