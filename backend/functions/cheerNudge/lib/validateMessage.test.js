'use strict';

const { validateMessage } = require('./validateMessage');

describe('validateMessage', () => {
  test('accepts a normal message', () => {
    expect(validateMessage('Nice work!')).toBeNull();
  });

  test('rejects a blank message', () => {
    expect(validateMessage('')).toBe('BLANK');
  });

  test('rejects a whitespace-only message', () => {
    expect(validateMessage('   ')).toBe('BLANK');
  });

  test('rejects a message over 140 characters', () => {
    expect(validateMessage('x'.repeat(141))).toBe('TOO_LONG');
  });

  test('accepts a message of exactly 140 characters', () => {
    expect(validateMessage('x'.repeat(140))).toBeNull();
  });

  test('rejects a non-string message', () => {
    expect(validateMessage(undefined)).toBe('BLANK');
    expect(validateMessage(null)).toBe('BLANK');
  });
});
