'use strict';

const MAX_BODY_LENGTH = 140;
const DEFAULT_ACTOR_NAME = 'Your challenger';

/**
 * One channel per event type (docs/TECH_STACK.md §4) so the client can route each push to the
 * right in-app surface, and so a user can mute nudges without muting cheers.
 *
 * Kept in sync with PushChannels.kt on the client — the ids are the contract between them.
 */
const PUSH_CHANNELS = {
  day_complete: 'belay_day_complete',
  cheer: 'belay_cheer',
  nudge: 'belay_nudge',
  recap_ready: 'belay_recap_ready',
};

const TITLES = {
  day_complete: (actorName) => `${actorName} finished the day`,
  cheer: (actorName) => `${actorName} cheered you on`,
  nudge: (actorName) => `${actorName} nudged you`,
  recap_ready: () => 'Your weekly recap is ready',
};

const DEFAULT_BODIES = {
  day_complete: 'Every habit checked off.',
  recap_ready: 'See how your week went.',
};

function truncate(text) {
  return text.length <= MAX_BODY_LENGTH ? text : `${text.slice(0, MAX_BODY_LENGTH - 1)}…`;
}

/**
 * Builds the FCM payload for one event. Pure — the send itself lives in index.js.
 *
 * The body of a cheer/nudge is the witness's own typed message (docs/TECH_STACK.md §12), never
 * canned tone copy, so it is passed through rather than generated here.
 *
 * @param {{type: string, actorName?: string, challengeId: string, message?: string}} event
 * @returns {object} an FCM message minus the `token` field, which the caller fills per device
 */
function buildPushMessage({ type, actorName, challengeId, message }) {
  const title = TITLES[type];
  if (!title) throw new Error(`Unknown push type: ${type}`);

  const name = typeof actorName === 'string' && actorName.trim().length > 0 ? actorName : DEFAULT_ACTOR_NAME;
  const body = typeof message === 'string' && message.trim().length > 0 ? message : DEFAULT_BODIES[type] || '';

  return {
    notification: { title: title(name), body: truncate(body) },
    // The data block is what the client reads to deep-link into the right screen; a
    // notification-only push would land the user on whatever tab was last open.
    data: { type, challenge_id: challengeId },
    android: { notification: { channelId: PUSH_CHANNELS[type] }, priority: type === 'nudge' ? 'high' : 'normal' },
  };
}

module.exports = { buildPushMessage, PUSH_CHANNELS, MAX_BODY_LENGTH };
