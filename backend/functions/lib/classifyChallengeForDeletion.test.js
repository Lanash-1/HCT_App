'use strict';

const { classifyChallengeForDeletion } = require('./classifyChallengeForDeletion');

describe('classifyChallengeForDeletion', () => {
  test('cascade-deletes a challenge the requester is the challenger of', () => {
    const challenge = { challenger_user_id: 'user-1', witness_user_id: 'user-2' };

    expect(classifyChallengeForDeletion(challenge, 'user-1')).toBe('cascade_delete');
  });

  test('clears the witness rather than deleting a challenge the requester only witnesses', () => {
    const challenge = { challenger_user_id: 'user-1', witness_user_id: 'user-2' };

    expect(classifyChallengeForDeletion(challenge, 'user-2')).toBe('clear_witness');
  });

  test('ignores a challenge the requester has no relationship to', () => {
    const challenge = { challenger_user_id: 'user-1', witness_user_id: 'user-2' };

    expect(classifyChallengeForDeletion(challenge, 'user-3')).toBe('ignore');
  });
});
