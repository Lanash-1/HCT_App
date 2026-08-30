'use strict';

/**
 * Who a given push goes to, and whether it may be sent at all.
 *
 * Kept pure and separate from buildPushMessage so the "a miss is visible to exactly one person"
 * rule (PRD §7) is expressed in one testable place: a challenger's day state only ever addresses
 * that challenge's single witness, and never anyone else.
 */

/** @returns {string|null} the recipient's user id, or null when there is nobody to send to */
function resolvePushRecipientId({ type, challenge }) {
  if (!challenge) return null;
  const recipient = type === 'day_complete' ? challenge.witness_user_id : challenge.challenger_user_id;
  return recipient || null;
}

/**
 * The Profile "let X nudge me" toggle (PRD §5.5) is enforced here, server-side — a witness's
 * client can't be trusted to respect a preference stored on someone else's profile.
 */
function isPushAllowed({ type, recipientProfile }) {
  if (!recipientProfile) return false;
  if (type !== 'nudge') return true;
  return recipientProfile.notif_allow_nudge !== false;
}

/** Device tokens worth attempting, de-duplicated and cleaned of partial-write junk. */
function selectSendableTokens(recipientProfile) {
  const tokens = (recipientProfile && recipientProfile.fcm_tokens) || [];
  if (!Array.isArray(tokens)) return [];
  return [...new Set(tokens.filter((token) => typeof token === 'string' && token.trim().length > 0))];
}

module.exports = { resolvePushRecipientId, isPushAllowed, selectSendableTokens };
