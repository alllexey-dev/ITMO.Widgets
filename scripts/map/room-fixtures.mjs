// Room matching cases for :shared:feature-map (docs/features/map.md "Room mapping").
//
//   node scripts/map/room-fixtures.mjs <lessons.tsv> <rooms dir>
//
// <lessons.tsv>: an aggregate of Backend's synced lessons, `building_id, main_building_id, building, room, ...`
// tab-separated (no user data). <rooms dir>: plan rooms as `<id>.json` (itmo-map-web web/public/units or the mirror's
// rooms files). Writes every distinct timetable room of a building with a plan and the plan room ids that
// reference-rooms.mjs accepts for it (two where ITMO gave two rooms one label). Ids are those of this rooms dir, so
// RoomMatcherParityTest must read the same one; only timetable text and ids enter the repository, never the plans.
import { readFileSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { matchRooms } from './reference-rooms.mjs';

const [tsv, roomsDir] = process.argv.slice(2);
if (!tsv || !roomsDir) throw new Error('usage: room-fixtures.mjs <lessons.tsv> <rooms dir>');

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '../../shared/feature-map/src/androidHostTest/resources/rooms/timetable-rooms.json');

const plans = new Map(readdirSync(roomsDir).filter(f => /^\d+\.json$/.test(f))
  .map(f => [Number(f.slice(0, -5)), JSON.parse(readFileSync(join(roomsDir, f), 'utf8'))]));

const seen = new Set();
const cases = [];
for (const line of readFileSync(tsv, 'utf8').split('\n')) {
  const [bid, mid, , room] = line.split('\t');
  if (!room || room === '<NULL>') continue;
  const plan = [mid, bid].map(Number).find(id => plans.has(id));
  if (plan === undefined || seen.has(`${plan}\t${room}`)) continue;
  seen.add(`${plan}\t${room}`);
  const { rooms, approximate } = matchRooms(plans.get(plan), room);
  cases.push({ building: plan, room, ids: rooms.map(u => Number(u.id)), approximate: rooms.length > 0 && approximate });
}
cases.sort((a, b) => a.building - b.building || a.room.localeCompare(b.room, 'ru', { numeric: true }));

mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, `[\n${cases.map(c => JSON.stringify(c)).join(',\n')}\n]\n`);
const missing = cases.filter(c => !c.ids.length).map(c => `${c.building} ${c.room}`);
console.log(`${cases.length} rooms, ${cases.length - missing.length} on the plans; not on the plans: ${missing.join(', ')}`);
