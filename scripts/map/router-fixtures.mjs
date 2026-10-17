// Router parity fixtures for :shared:feature-map (docs/features/map.md "Router").
//
//   node scripts/map/router-fixtures.mjs                      synthetic graph + expected routes -> test resources
//   node scripts/map/router-fixtures.mjs --graphs <dir> <out> real graphs (<id>.json, e.g. itmo-map-web
//                                                             web/public/graph) -> expected routes, never committed
//
// The synthetic graph is generated here so that ITMO's plans never enter this repository.
import { readFileSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Router } from './reference-router.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const resources = join(here, '../../shared/feature-map/src/androidHostTest/resources/router');

function expected(graph, pairs) {
  const router = new Router(graph);
  return pairs.map(([from, to]) => ({
    from, to,
    routes: router.findRoutes(from, to).map(r => ({
      key: r.key(),
      characteristics: r.characteristics,
      parts: r.parts().map(p => ({ type: p.type, level: p.level, edges: p.transitions.length })),
      distance: Math.round(r.distance() * 1000) / 1000,
    })),
  }));
}

// Three floors of a 6 x 4 grid: rooms on rows 0 and 3 (two cells per room on row 3), a corridor on rows 1-2,
// stairs at both ends (up costs 20 per metre, down 1), a yard that shortcuts floor 0, a private shortcut on floor 1.
function synthetic() {
  const W = 6, H = 4, features = [];
  const id = (l, x, y) => l * 100 + y * W + x;
  const area = (l, x, y) => (y === 0 ? 1000 + l * 10 + x : y === 3 ? 2000 + l * 10 + Math.floor(x / 2) : 900 + l);
  const nodes = new Map();
  for (let l = 0; l < 3; l++) for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const p = { id: id(l, x, y), area_id: area(l, x, y), level: l, transitions: [] };
    nodes.set(p.id, { type: 'Feature', geometry: { type: 'Point', coordinates: [30 + x * 1e-4, 60 + y * 1e-4, l] }, properties: p });
  }
  const link = (a, b, distance, type = 'straight', up = 5, down = up) => {
    nodes.get(a).properties.transitions.push({ id: b, distance, type, weight: up });
    nodes.get(b).properties.transitions.push({ id: a, distance, type, weight: down });
  };
  for (let l = 0; l < 3; l++) for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const corridor = y === 1 || y === 2;
    // Equal lengths on purpose: many shortest paths, so the tie-breaking is tested too.
    if (x + 1 < W && (corridor || (y === 3 && x % 2 === 0))) link(id(l, x, y), id(l, x + 1, y), 3);
    if (y + 1 < H) link(id(l, x, y), id(l, x, y + 1), y === 1 ? 2 : 2.5, 'straight', y === 1 ? 5 : 1);
  }
  for (let l = 0; l < 2; l++) for (const x of [0, W - 1]) link(id(l, x, 1), id(l + 1, x, 1), 5, 'stairs', 20, 1);
  let next = 400;
  const yard = [];
  for (let i = 0; i < 3; i++) {
    const p = { id: next++, area_id: 950, level: 0, outdoor: true, transitions: [] };
    nodes.set(p.id, { type: 'Feature', geometry: { type: 'Point', coordinates: [30 + i * 2e-4, 59.999, 0] }, properties: p });
    yard.push(p.id);
  }
  link(id(0, 0, 0), yard[0], 1, 'straight', 1);
  link(yard[0], yard[1], 1, 'straight', 1);
  link(yard[1], yard[2], 1, 'straight', 1);
  link(yard[2], id(0, W - 1, 0), 1, 'straight', 1);
  const secret = { id: next++, area_id: 960, level: 1, access: 'private', transitions: [] };
  nodes.set(secret.id, { type: 'Feature', geometry: { type: 'Point', coordinates: [30.0003, 60.0005, 1] }, properties: secret });
  link(id(1, 1, 0), secret.id, 0.5, 'straight', 1);
  link(secret.id, id(1, 4, 0), 0.5, 'straight', 1);
  for (const f of nodes.values()) features.push(f);
  return { type: 'FeatureCollection', name: 'transition', features };
}

function areas(graph) {
  return [...new Set(graph.features.filter(f => f.properties.access !== 'private').map(f => f.properties.area_id))];
}

function pairsOf(graph, count, seed) {
  const list = areas(graph);
  let s = seed;
  const rnd = () => (s = (s * 1103515245 + 12345) % 2147483648) / 2147483648;
  return Array.from({ length: count }, () => [list[Math.floor(rnd() * list.length)], list[Math.floor(rnd() * list.length)]]);
}

const args = process.argv.slice(2);
if (args[0] === '--graphs') {
  const [, dir, out] = args;
  const result = {};
  for (const file of readdirSync(dir).filter(f => f.endsWith('.json')).sort()) {
    const graph = JSON.parse(readFileSync(join(dir, file), 'utf8'));
    result[file.replace('.json', '')] = expected(graph, pairsOf(graph, 60, 7));
  }
  writeFileSync(out, JSON.stringify(result));
  console.log(`wrote ${out}`);
} else {
  const graph = synthetic();
  const all = areas(graph);
  // Every third pair keeps the fixture small; the unknown room and the private point are always in.
  const pairs = all.flatMap(a => all.map(b => [a, b])).filter((_, i) => i % 3 === 0).concat([[1000, 9999], [960, 1000]]);
  mkdirSync(resources, { recursive: true });
  writeFileSync(join(resources, 'synthetic-graph.json'), JSON.stringify(graph));
  writeFileSync(join(resources, 'synthetic-routes.json'), JSON.stringify(expected(graph, pairs)));
  console.log(`${pairs.length} pairs -> ${resources}`);
}
