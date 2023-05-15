import ws from 'k6/ws';
import {check} from 'k6';
import {randomString, randomIntBetween} from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import msgpack from 'https://cdnjs.cloudflare.com/ajax/libs/msgpack-lite/0.1.26/msgpack.min.js';
import {SharedArray} from 'k6/data';


// export const options = {
//   scenarios: {
//     load_testing: {
//       executor: "ramping-vus",
//       startVus: 150,
//       stages: [
//         { duration: "20s", target: 150 },
//       ],
//     },
//   },
// };

const VIRTUAL_USERS = 2000;
const CHANGES_PER_USER = 1;

// export const options = {
//   discardResponseBodies: true,
//   scenarios: {
//     contacts: {
//       executor: 'constant-arrival-rate',
//
//       // Our test should last 30 seconds in total
//       duration: '32s',
//
//       // It should start 30 iterations per `timeUnit`. Note that iterations starting points
//       // will be evenly spread across the `timeUnit` period.
//       rate: 200,
//
//       // It should start `rate` iterations per second
//       timeUnit: '8s',
//
//       // It should preallocate 2 VUs before starting the test
//       preAllocatedVUs: 100,
//
//       // It is allowed to spin up to 50 maximum VUs to sustain the defined
//       // constant arrival rate.
//       maxVUs: VIRTUAL_USERS
//     }
//   }
// }

export let options = {
    iterations: VIRTUAL_USERS,
    vus: VIRTUAL_USERS,
    discardResponseBodies: true,
}

// export const options = {
//  discardResponseBodies: true,
//  scenarios: {
//    contacts: {
//      executor: 'shared-iterations',
//      vus: 9,
//      iterations: VIRTUAL_USERS,
//      maxDuration: '29s',
//    },
//   },
// };
// export const options = {
//  vus: VIRTUAL_USERS,
//  iterations: VIRTUAL_USERS,
//  noVUConnectionReuse: true
// };

const comparePath = (a, b) => {
    const m = Math.min(a.length, b.length);
    for (let i = 0; i < m; i++) {
        if (a[i].a !== b[i].a) {
            return a[i].a ? 1 : -1;
        }
        if (a[i].b !== b[i].b) {
            return a[i].b < b[i].b ? -1 : 1;
        }
    }
    if (a.length === b.length) {
        return 0;
    }
    if (a.length === m) {
        return b[m].a ? -1 : 1;
    }
    return a[m].a ? 1 : -1;
};

const generateData = () => {
    let events = [];
    for (let k = 0; k < VIRTUAL_USERS * CHANGES_PER_USER; k++) {
        const path = [];
        const pathLength = randomIntBetween(1, 20);
        for (let i = 0; i < pathLength; i++) {
            const a = randomIntBetween(1, 3) == 2;
            const b = randomIntBetween(1, 1000);
            path.splice(path.length, 0, {a: a, b: b});
        }
        path.splice(path.length, 0, {a: 0, b: k});
        events.splice(events.length, 0, {
            a: path,
            b: randomString(1).charAt(0)
        });
    }
    events.sort((a, b) => {
        return comparePath(a.a, b.a);
    });
    return events;
};

let data = new SharedArray('arr', generateData);


export default function () {
    const url = 'ws://127.0.0.1:8001/documents';
    // const params = { tags: { my_tag: 'hello' } };

    let receivedChanges = [];

    const params = {
        headers: {'X-Request-Time': new Date(Date.now()).getTime()},
        tags: {k6test: 'yes'},
    };

    // console.log(`exec http.get at ${new Date(Date.now()).toLocaleString()}`)
    // const start = new Date().getTime();

    const res = ws.connect(url, params, function (socket) {
        socket.on('open', () => {

            socket.setTimeout(() => {
                socket.sendBinary(msgpack.encode({
                    type: 'CONNECT'
                }).buffer);
                const connectionId = __VU;
                const changes = [];
                for (let i = 0; i < VIRTUAL_USERS * CHANGES_PER_USER; i++) {
                    if (i % connectionId === 0) {
                        changes.splice(changes.length, 0, data[i]);
                    }
                }
                const sendMessageTimeout = randomIntBetween(500, 3000);
                socket.setTimeout(() => {
                    // console.log('Sending ', JSON.stringify(changes));
                    socket.sendBinary(msgpack.encode({
                        type: 'CHANGES',
                        payload: changes
                    }).buffer);
                }, sendMessageTimeout);
            }, randomIntBetween(1, 2));

            // console.log(`${new Date().getTime() - start} ms`);


            const binarySearch = (path) => {
                for (const d of data) {
                    if (comparePath(path, d.a) === 0) {
                        return true;
                    }
                }
                return false;
                // let low = 0;
                // let high = data.length - 1;
                // while (low <= high) {
                //   let mid = parseInt(low + (high - low) / 2);
                //   // console.log(data[mid]);
                //   const c = comparePath(path, data[mid].a);
                //   if (c === 0) {
                //     return true;
                //   }
                //   if (c < 0) {
                //     high = mid - 1;
                //   }
                //   else {
                //     low = mid + 1;
                //   }
                // }
                // return false;
            };

            socket.on('binaryMessage', function (message) {
                // // console.log('Received message ', message);
                // const eventPayload = msgpack.decode(new Uint8Array(message));
                //
                // if (eventPayload.responseType === 'ADD') {
                //   const addedChange = eventPayload.payload;
                //   // console.log(JSON.stringify(addedChange));
                //   if (binarySearch(addedChange.a)) {
                //     receivedChanges = receivedChanges.filter(d => comparePath(d.a, addedChange.a) !== 0);
                //     console.log('Detected');
                //     receivedChanges.splice(receivedChanges.length, 0, addedChange);
                //   }
                // }
            });

            socket.setTimeout(() => {
                receivedChanges.sort((a, b) => {
                    return comparePath(a.a, b.a);
                });
                // console.log(`${__VU} received ${receivedChanges.length} changes`);
                // check(receivedChanges, {
                //   'all changes received': c => c.length === data.length
                // })
                socket.close();
            }, 8 * 1000);
        });
    });
    // console.log(res)
    check(res, {'status is 101': (r) => r && r.status === 101});
};
