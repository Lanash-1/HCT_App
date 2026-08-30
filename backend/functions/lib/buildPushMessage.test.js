'use strict';

const { buildPushMessage, PUSH_CHANNELS } = require('./buildPushMessage');

describe('buildPushMessage', () => {
  test('a completed day tells the witness who finished', () => {
    const message = buildPushMessage({ type: 'day_complete', actorName: 'Arun', challengeId: 'c1' });

    expect(message.notification.title).toBe('Arun finished the day');
    expect(message.data.type).toBe('day_complete');
    expect(message.data.challenge_id).toBe('c1');
    expect(message.android.notification.channelId).toBe(PUSH_CHANNELS.day_complete);
  });

  test('a cheer carries the witness-typed message as the body, not canned copy', () => {
    const message = buildPushMessage({ type: 'cheer', actorName: 'Meera', challengeId: 'c1', message: 'Ten days!' });

    expect(message.notification.title).toBe('Meera cheered you on');
    expect(message.notification.body).toBe('Ten days!');
    expect(message.android.notification.channelId).toBe(PUSH_CHANNELS.cheer);
  });

  test('a nudge is titled as a nudge and routed to its own channel', () => {
    const message = buildPushMessage({ type: 'nudge', actorName: 'Meera', challengeId: 'c1', message: "Don't forget!" });

    expect(message.notification.title).toBe('Meera nudged you');
    expect(message.notification.body).toBe("Don't forget!");
    expect(message.android.notification.channelId).toBe(PUSH_CHANNELS.nudge);
  });

  test('a ready recap points at the recap screen', () => {
    const message = buildPushMessage({ type: 'recap_ready', challengeId: 'c1' });

    expect(message.notification.title).toBe('Your weekly recap is ready');
    expect(message.android.notification.channelId).toBe(PUSH_CHANNELS.recap_ready);
  });

  test('falls back to a neutral actor name rather than rendering "undefined finished the day"', () => {
    const message = buildPushMessage({ type: 'day_complete', actorName: undefined, challengeId: 'c1' });

    expect(message.notification.title).toBe('Your challenger finished the day');
  });

  test('rejects an unknown event type instead of sending an empty notification', () => {
    expect(() => buildPushMessage({ type: 'not_a_real_event', challengeId: 'c1' })).toThrow(/unknown push type/i);
  });

  test('truncates an over-long body so the tray notification stays readable', () => {
    const message = buildPushMessage({ type: 'cheer', actorName: 'Meera', challengeId: 'c1', message: 'x'.repeat(200) });

    expect(message.notification.body.length).toBeLessThanOrEqual(140);
  });
});
