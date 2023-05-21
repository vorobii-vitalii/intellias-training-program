import ws from 'k6/ws';
import {check} from 'k6';
import {randomString, randomIntBetween} from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import msgpack from 'https://cdnjs.cloudflare.com/ajax/libs/msgpack-lite/0.1.26/msgpack.min.js';
import {SharedArray} from 'k6/data';

const VIRTUAL_USERS = 2000;
const CHANGES_PER_USER = 1;

export let options = {
    iterations: VIRTUAL_USERS,
    vus: VIRTUAL_USERS,
    discardResponseBodies: true,
}
const comparePath = (a, b) => {
    const aLength = a.directions.length;
    const bLength = b.directions.length;
    const m = Math.min(aLength, bLength);
    for (let i = 0; i < m; i++) {
        if (a.directions[i] !== b.directions[i]) {
            return a.directions[i] ? 1 : -1;
        }
        if (a.disambiguators[i]  !== b.disambiguators[i] ) {
            return a.disambiguators[i] < b.disambiguators[i] ? -1 : 1;
        }
    }
    if (aLength === bLength) {
        return 0;
    }
    if (aLength === m) {
        return b.disambiguators[m] ? -1 : 1;
    }
    return a.disambiguators[m] ? 1 : -1;
};

const generateData = () => {
    let events = [];
    for (let k = 0; k < VIRTUAL_USERS * CHANGES_PER_USER; k++) {
        const directions = [];
        const disambiguators = [];
        const pathLength = randomIntBetween(1, 20);
        for (let i = 0; i < pathLength; i++) {
            const a = randomIntBetween(1, 3) == 2;
            const b = randomIntBetween(1, 1000);
            directions.splice(directions.length, 0, a);
            disambiguators.splice(disambiguators.length, 0, b);
        }
        directions.splice(directions.length, 0, false);
        disambiguators.splice(disambiguators.length, 0, k);
        events.splice(events.length, 0, {
            treePath: {
                directions: directions,
                disambiguators: disambiguators
            },
            character: randomString(1).charAt(0)
        });
    }
    events.sort((a, b) => {
        return comparePath(a.treePath, b.treePath);
    });
    return events;
};

let data = new SharedArray('arr', generateData);


export default function () {
    const url = 'ws://127.0.0.1:8001/documents';

    let receivedChanges = [];

    const params = {
        headers: {'X-Request-Time': new Date(Date.now()).getTime()},
        tags: {k6test: 'yes'},
    };

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

                socket.setInterval(() => {
                    socket.sendBinary(msgpack.encode({
                        type: 'PING',
                    }).buffer);
                }, 2000);

            }, randomIntBetween(1, 2));


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
                    return comparePath(a.treePath, b.treePath);
                });
                // console.log(`${__VU} received ${receivedChanges.length} changes`);
                // check(receivedChanges, {
                //   'all changes received': c => c.length === data.length
                // })
                socket.close();
            }, 6 * 1000);
        });
    });
    // console.log(res)
    check(res, {'status is 101': (r) => r && r.status === 101});
};
