import ws from 'k6/ws';
import { check } from 'k6';
import { randomString, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import msgpack from 'https://cdnjs.cloudflare.com/ajax/libs/msgpack-lite/0.1.26/msgpack.min.js';

export const options = {
  scenarios: {
    load_testing: {
      executor: "ramping-vus",
      startVus: 100,
      stages: [
        { duration: "30s", target: 100 },
      ],
    },
  },
};

export function setup() {
  let events = [];
  for (let k = 0; k < 2; k++) {
    const changes = [];
    for (let j = 0; j < 10; j++) {
      const path = [];
      const pathLength = randomIntBetween(1, 100);
      for (let i = 0; i < pathLength; i++) {
        const a = randomIntBetween(1, 3) == 2;
        const b = randomIntBetween(1, 1000);
        path.splice(path.length, 0, {a: a, b: b});
      }
      changes.splice(changes.length, 0, {
        a: path,
        b: randomString(1).charAt(0)
      });
    }
    events.splice(events.length, 0, JSON.stringify({
      type: 'CHANGES',
      payload: changes
    }));
  }
  return events;
}

export default function () {
  const url = 'ws://localhost:8001/documents';
  const params = { tags: { my_tag: 'hello' } };

  const res = ws.connect(url, params, function (socket) {
    socket.on('open', () => {
      socket.sendBinary(msgpack.encode({
        type: 'CONNECT'
      }).buffer);
      let events = [];
      for (let k = 0; k < 6; k++) {
        const changes = [];
        for (let j = 0; j < 10; j++) {
          const path = [];
          const pathLength = randomIntBetween(1, 100);
          for (let i = 0; i < pathLength; i++) {
            const a = randomIntBetween(1, 3) == 2;
            const b = randomIntBetween(1, 1000);
            path.splice(path.length, 0, {a: a, b: b});
          }
          const change = {
            a: path,
            b: randomString(1).charAt(0)
          };
          changes.splice(changes.length, 0, change);
        }
        events.splice(events.length, 0, msgpack.encode({
          type: 'CHANGES',
          payload: changes
        }).buffer);
      }
      let timeout = 1000;
      for (const event of events) {
        const t = timeout;
        socket.setTimeout(() => {
          socket.send(event);
        }, t);
        timeout += 1000;
      }
    });
    socket.setTimeout(() => socket.close(), 8 * 1000);

  });
  check(res, { 'status is 101': (r) => r && r.status === 101 });
}