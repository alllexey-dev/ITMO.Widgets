// Reference room matching: itmo-map-web web/public/rooms.js, copied unchanged below this header.
// RoomMatcherParityTest checks that the Kotlin RoomMatcher finds a room this file accepts.

// Finds a map unit by MyITMO's room text or ISU room id (deep links from ITMO.Widgets). No DOM, so node can test it.
// ITMO.Widgets keeps a copy as scripts/map/reference-rooms.mjs: its RoomMatcher must agree with this file.

const LATIN = { a: 'а', b: 'б', v: 'в' };
const IGNORED_TAGS = /\s*\((усл|присв|архив)\)/g;
const NUMBER = /(?<![\d№])(\d{3,4}[а-я]?(?:\/\d+)?)(?!\d)/;

const clean = s => String(s ?? '').toLowerCase().replace(/ё/g, 'е').replace(/\s+/g, ' ').trim();
const isRoom = u => u.class === 'room';

// "3107 а", "1223a", "Lemon Classroom (1419)", "262 (усл)" -> "3107а", "1223а", "1419", "262".
// "201 (составное)" is a different ISU room than "201", so it keeps a mark; "1216/0" is the same hall as "1216".
export function roomKey(text) {
  const s = clean(text).replace(/[abv]/g, c => LATIN[c]).replace(/(\d) ([а-я])(?![а-я])/g, '$1$2');
  const m = s.replace(IGNORED_TAGS, '').match(NUMBER);
  return m ? m[1].replace(/\/0$/, '') + (s.includes('составное') ? '+' : '') : null;
}

const byKey = (rooms, key) => rooms.filter(u => roomKey(u.ref ?? u.name) === key || roomKey(u.name) === key);

// Every room the text can mean, best first: one for most texts, two where ITMO gave two ISU rooms one label
// (Биржа 206, Грива 260). approximate: "1224в" is not on the plan, so these are the rooms of "1224".
export function matchRooms(units, text) {
  const rooms = units.filter(isRoom);
  const key = roomKey(text);
  if (!key) {
    const name = clean(text);
    return { rooms: name ? rooms.filter(u => clean(u.name) === name) : [], approximate: false };
  }
  const exact = byKey(rooms, key);
  if (exact.length || !/\d[а-я]\+?$/.test(key)) return { rooms: exact, approximate: false };
  return { rooms: byKey(rooms, key.replace(/[а-я](\+?)$/, '$1')), approximate: true };
}

export const findIsuRoom = (units, isuId) => units.find(u => u['isu:room_id'] === String(isuId));
