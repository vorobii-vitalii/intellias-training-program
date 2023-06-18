import ws from 'k6/ws';
import {check} from 'k6';
import {randomString, randomIntBetween} from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import msgpack from 'https://cdnjs.cloudflare.com/ajax/libs/msgpack-lite/0.1.26/msgpack.min.js';
import pako from 'https://cdnjs.cloudflare.com/ajax/libs/pako/2.0.4/pako.min.js';
import {SharedArray} from 'k6/data';

const VIRTUAL_USERS = 1000;
const CHANGES_PER_USER = 0;

export const options = {
    iterations: VIRTUAL_USERS,
    vus: VIRTUAL_USERS,
    discardResponseBodies: true,
};

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
        const pathLength = randomIntBetween(1, 300);
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
    const url = 'ws://127.0.0.1:10000/documents';

    const params = {
        headers: {'X-Request-Time': new Date(Date.now()).getTime()},
        tags: {k6test: 'yes'},
    };

    const res = ws.connect(url, params, function (socket) {
        socket.on('error', e => {
            console.log('Error ', e);
        });
        socket.on('open', () => {

            socket.sendBinary(msgpack.encode({
                type: 'CONNECT',
                batchSize: 2000
            }).buffer);
            const connectionId = __VU;
            const changes = [];
            for (let i = connectionId; i < VIRTUAL_USERS * CHANGES_PER_USER; i += VIRTUAL_USERS) {
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

            socket.on('binaryMessage', function (message) {
            });

            socket.setTimeout(() => {
                socket.close();
            }, 10 * 1000);
        });
    });
    check(res, {
        'status is 101': (r) => r && r.status === 101
    });
};
