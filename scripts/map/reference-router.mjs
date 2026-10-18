// Reference router: itmo-map-web web/public/router.js (the owner's port of the ITMO campus app), copied unchanged
// below this header. RouterParityTest compares the Kotlin port with what this file computes.

// Port of the routing logic from the ITMO campus app (see re/notes/routing.md).

export const PartType = { floor: 'floor', stairsUp: 'stairsUp', stairsDown: 'stairsDown', outdoor: 'outdoor' };

export class Router {
  constructor(transitionGeoJson) {
    this.all = transitionGeoJson.features;
    this.byId = new Map(this.all.map(p => [p.properties.id, p]));
    this.points = this.all.filter(p => p.properties.access !== 'private');
  }

  // Original: first node in file order with area_id == unit id.
  nodeForUnit(unitId) {
    return this.all.find(p => String(p.properties.area_id) === String(unitId));
  }

  path(start, end, filter) {
    const pts = filter ? this.points.filter(filter) : this.points;
    const graph = new Map();
    for (const p of pts) {
      const edges = new Map();
      for (const t of p.properties.transitions) {
        const touchesEnds = [p.properties.id, t.id].some(id => id === start || id === end);
        edges.set(t.id, touchesEnds ? 0 : Math.round(t.weight * t.distance));
      }
      graph.set(p.properties.id, edges);
    }
    const ids = dijkstra(graph, start, end);
    const allowed = new Set(this.points.map(p => p.properties.id));
    if (ids.some(id => !allowed.has(id))) throw new Error('path goes through a private node');
    return ids.map(id => this.byId.get(id));
  }

  findRoutes(fromUnitId, toUnitId) {
    const from = this.nodeForUnit(fromUnitId);
    const to = this.nodeForUnit(toUnitId);
    if (!from || !to) return [];
    let candidates;
    try {
      candidates = [
        { characteristics: [], nodes: this.path(from.properties.id, to.properties.id) },
        { characteristics: ['indoor'], nodes: this.path(from.properties.id, to.properties.id, p => p.properties.outdoor !== true) },
      ];
    } catch {
      return [];
    }
    const routes = [];
    for (const c of candidates.filter(c => c.nodes.length > 0)) {
      const route = new Route(c.nodes, c.characteristics);
      route.shrink(fromUnitId, toUnitId);
      if (!routes.some(r => r.key() === route.key())) routes.push(route);
    }
    routes.sort((a, b) => a.weight() - b.weight());
    routes[0]?.characteristics.push('recommended');
    return routes;
  }
}

// dijkstrajs port: no visited set, full traversal, empty result if unreachable or start == end.
function dijkstra(graph, s, d) {
  const predecessors = new Map();
  const costs = new Map([[s, 0]]);
  const open = [{ value: s, cost: 0 }];
  while (open.length) {
    const { value: u, cost: costU } = open.shift();
    for (const [v, costE] of graph.get(u) ?? []) {
      const total = costU + costE;
      const costV = costs.get(v);
      if (costV === undefined || costV > total) {
        costs.set(v, total);
        predecessors.set(v, u);
        insertSorted(open, { value: v, cost: total });
      }
    }
  }
  if (!costs.has(d)) return [];
  const nodes = [];
  for (let u = d; u !== undefined; u = predecessors.get(u)) nodes.push(u);
  return nodes.length === 1 ? [] : nodes.reverse();
}

function insertSorted(queue, item) {
  let i = queue.length;
  while (i > 0 && queue[i - 1].cost > item.cost) i--;
  queue.splice(i, 0, item);
}

function transitionBetween(from, to) {
  return from.properties.transitions.find(t => t.id === to.properties.id);
}

export class Route {
  constructor(nodes, characteristics) {
    this.path = nodes.slice(1).map((to, i) => ({ from: nodes[i], to }));
    this.characteristics = characteristics;
  }

  // Intended behaviour of shrinkPath (the original removes every other edge due to an index bug).
  shrink(fromUnitId, toUnitId) {
    const inArea = (t, id) => String(t.from.properties.area_id) === String(id);
    while (this.path.length && inArea(this.path[0], fromUnitId)) this.path.shift();
    while (this.path.length > 1 && inArea(this.path.at(-1), toUnitId)) this.path.pop();
  }

  key() {
    return this.path.map(t => t.from.properties.id).concat(this.path.at(-1)?.to.properties.id).join(',');
  }

  distance() {
    return this.path.reduce((acc, t) => acc + (transitionBetween(t.from, t.to)?.distance ?? 0), 0);
  }

  weight() {
    return this.path.reduce((acc, t) => {
      const tr = transitionBetween(t.from, t.to);
      return acc + (tr ? tr.weight * tr.distance : 0);
    }, 0);
  }

  parts() {
    const parts = [];
    for (const t of this.path) {
      const type = partType(t);
      const last = parts.at(-1);
      if (last && last.type === type) last.transitions.push(t);
      else parts.push({ type, transitions: [t] });
    }
    for (const p of parts) p.level = p.transitions.at(-1).to.properties.level;
    return parts;
  }

  geoJson(level, activeStep) {
    const visible = lvl => (activeStep == null || lvl === level ? 'true' : 'false');
    const state = lvl => (lvl === level ? 'active' : 'inactive');
    const point = (type, node) => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: node.geometry.coordinates.slice(0, 2) },
      properties: { type, visible: visible(node.properties.level), state: state(node.properties.level), level: String(node.properties.level) },
    });
    const lines = this.parts().map((part, i) => ({
      type: 'Feature',
      geometry: {
        type: 'LineString',
        coordinates: part.transitions.map(t => t.from.geometry.coordinates.slice(0, 2)).concat([part.transitions.at(-1).to.geometry.coordinates.slice(0, 2)]),
      },
      properties: {
        type: 'path', partType: part.type, level: String(part.level),
        visible: visible(part.level), state: state(part.level),
        step: activeStep === i ? 'current' : 'other',
      },
    }));
    return {
      type: 'FeatureCollection',
      features: [point('start-point', this.path[0].from), point('end-point', this.path.at(-1).to), ...lines],
    };
  }
}

function partType(t) {
  const tr = transitionBetween(t.from, t.to);
  if (tr?.type === 'stairs') return tr.weight > 1 ? PartType.stairsUp : PartType.stairsDown;
  if (t.to.properties.outdoor === true) return PartType.outdoor;
  return PartType.floor;
}

export function headingText(part) {
  switch (part.type) {
    case PartType.floor: return `${part.level + 1} этаж`;
    case PartType.stairsUp: return 'Подъем';
    case PartType.stairsDown: return 'Спуск';
    case PartType.outdoor: return 'Двор';
  }
}

export function instructionText(part, next) {
  const lvl = part.level + 1;
  const isStairs = p => p.type === PartType.stairsUp || p.type === PartType.stairsDown;
  switch (part.type) {
    case PartType.floor: {
      const s = `Иди по ${lvl} этажу`;
      if (!next) return `${s} до места назначения`;
      if (isStairs(next)) return `${s} до лестницы`;
      if (next.type === PartType.outdoor) return `${s} до выхода на улицу`;
      return s;
    }
    case PartType.stairsUp: return `Поднимайся по лестнице до ${lvl} этажа`;
    case PartType.stairsDown: return `Спускайся по лестнице до ${lvl} этажа`;
    case PartType.outdoor: {
      if (!next) return 'Иди по улице до места назначения';
      if (next.type === PartType.floor) return 'Иди по улице до входа в здание';
      if (isStairs(next)) return 'Иди по улице до лестницы';
      return 'Иди по улице';
    }
  }
}
