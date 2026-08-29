'use strict';

/**
 * Decides what an account-deletion request means for one challenge (docs/PRIVACY_POLICY.md
 * "Data retention & deletion"): the challenger's own challenge (and everything under it) is
 * theirs to delete; a challenge they only witness must survive, with just their identity
 * removed from it.
 *
 * @param {{challenger_user_id: string, witness_user_id: string}} challenge
 * @param {string} requesterId
 * @returns {'cascade_delete' | 'clear_witness' | 'ignore'}
 */
function classifyChallengeForDeletion(challenge, requesterId) {
  if (challenge.challenger_user_id === requesterId) return 'cascade_delete';
  if (challenge.witness_user_id === requesterId) return 'clear_witness';
  return 'ignore';
}

module.exports = { classifyChallengeForDeletion };
