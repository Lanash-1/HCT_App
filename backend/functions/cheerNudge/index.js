'use strict';

/**
 * BasicIO (HTTP) function invoked by the Android client to send a cheer or nudge
 * (docs/DATA_MODEL.md `interactions`). Re-enforces message validation and the 1/day nudge
 * rate limit server-side — the client (InteractionRepository.kt) does the same checks for
 * immediate UX feedback, but this function is the authoritative check.
 *
 * Expects arguments: challenge_id, from_user_id, type ('cheer'|'nudge'), message.
 * Expects a Data Store `interactions` table matching docs/DATA_MODEL.md — must be created in
 * the Catalyst console before this function can run for real.
 */

const catalyst = require('zcatalyst-sdk-node');
const { validateMessage } = require('./lib/validateMessage');

function todayDateString() {
  return new Date().toISOString().slice(0, 10); // YYYY-MM-DD
}

function respondError(basicIO, context, statusCode, reason) {
  basicIO.setStatus(statusCode);
  basicIO.write(JSON.stringify({ status: 'error', reason }));
  context.close();
}

module.exports = async (context, basicIO) => {
  try {
    const challengeId = basicIO.getArgument('challenge_id');
    const fromUserId = basicIO.getArgument('from_user_id');
    const type = basicIO.getArgument('type');
    const message = basicIO.getArgument('message');

    if (type !== 'cheer' && type !== 'nudge') {
      return respondError(basicIO, context, 400, 'INVALID_TYPE');
    }

    const messageError = validateMessage(message);
    if (messageError) {
      return respondError(basicIO, context, 400, messageError);
    }

    const app = catalyst.initialize(context);
    const zcql = app.zcql();
    const datastore = app.datastore();
    const date = todayDateString();

    if (type === 'nudge') {
      const existing = await zcql.executeZCQLQuery(
        `SELECT ROWID FROM interactions WHERE challenge_id = ${challengeId} AND type = 'nudge' AND date = '${date}'`
      );
      if (existing.length > 0) {
        return respondError(basicIO, context, 409, 'ALREADY_NUDGED_TODAY');
      }
    }

    const inserted = await datastore.table('interactions').insertRow({
      challenge_id: challengeId,
      from_user_id: fromUserId,
      type,
      date,
      message,
    });

    basicIO.write(JSON.stringify({ status: 'success', interaction: inserted }));
    context.close();
  } catch (err) {
    console.error('cheerNudge failed', err);
    respondError(basicIO, context, 500, 'INTERNAL_ERROR');
  }
};
