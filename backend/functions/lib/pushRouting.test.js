'use strict';

const { resolvePushRecipientId, isPushAllowed, selectSendableTokens } = require('./pushRouting');

const challenge = { challenger_user_id: 'challenger-1', witness_user_id: 'witness-1' };

describe('resolvePushRecipientId', () => {
  test('a completed day goes to the witness — the one person allowed to see it', () => {
    expect(resolvePushRecipientId({ type: 'day_complete', challenge })).toBe('witness-1');
  });

  test('cheers and nudges go back to the challenger', () => {
    expect(resolvePushRecipientId({ type: 'cheer', challenge })).toBe('challenger-1');
    expect(resolvePushRecipientId({ type: 'nudge', challenge })).toBe('challenger-1');
  });

  test('a recap goes to the challenger it is about', () => {
    expect(resolvePushRecipientId({ type: 'recap_ready', challenge })).toBe('challenger-1');
  });

  test('returns null when the challenge has no witness yet, rather than an undefined recipient', () => {
    expect(resolvePushRecipientId({ type: 'day_complete', challenge: { challenger_user_id: 'c', witness_user_id: null } })).toBeNull();
  });
});

describe('isPushAllowed', () => {
  test('a nudge is suppressed when the challenger turned "let X nudge me" off', () => {
    expect(isPushAllowed({ type: 'nudge', recipientProfile: { notif_allow_nudge: false } })).toBe(false);
  });

  test('a nudge is allowed when the toggle is on', () => {
    expect(isPushAllowed({ type: 'nudge', recipientProfile: { notif_allow_nudge: true } })).toBe(true);
  });

  test('the nudge toggle does not silence cheers — it is scoped to nudges only', () => {
    expect(isPushAllowed({ type: 'cheer', recipientProfile: { notif_allow_nudge: false } })).toBe(true);
  });

  test('a missing profile blocks the send rather than guessing a preference', () => {
    expect(isPushAllowed({ type: 'nudge', recipientProfile: null })).toBe(false);
  });

  test('a profile with no stored preference defaults to allowing nudges, matching profile creation', () => {
    expect(isPushAllowed({ type: 'nudge', recipientProfile: {} })).toBe(true);
  });
});

describe('selectSendableTokens', () => {
  test('de-duplicates tokens so a device registered twice is not notified twice', () => {
    expect(selectSendableTokens({ fcm_tokens: ['a', 'b', 'a'] })).toEqual(['a', 'b']);
  });

  test('drops blank and non-string entries left by a partial write', () => {
    expect(selectSendableTokens({ fcm_tokens: ['a', '', null, 42] })).toEqual(['a']);
  });

  test('returns nothing for a profile that never registered a device', () => {
    expect(selectSendableTokens({})).toEqual([]);
    expect(selectSendableTokens(null)).toEqual([]);
  });
});
