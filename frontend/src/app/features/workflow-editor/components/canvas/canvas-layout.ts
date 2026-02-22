/**
 * Canvas auto-layout using the Dagre graph layout algorithm.
 * Mirrors the n8n "Tidy up" feature: left-to-right DAG layout,
 * connected-component detection, vertical stacking of disconnected subgraphs,
 * and grid-snapped positions anchored to the original bounding box.
 */
// @ts-ignore — dagre uses `export default` in its ESM bundle
import dagre from '@dagrejs/dagre';
import type { Node, Edge } from '@xyflow/react';

const GRID_SIZE = 16;
const NODE_X_SPACING = GRID_SIZE * 8;   // 128px — horizontal gap between ranks
const NODE_Y_SPACING = GRID_SIZE * 6;   // 96px  — vertical gap between nodes on the same rank
const SUBGRAPH_SPACING = GRID_SIZE * 8; // 128px — gap between disconnected subgraphs
const DEFAULT_NODE_WIDTH = 160;
const DEFAULT_NODE_HEIGHT = 50;

interface BoundingBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

function snapToGrid(val: number): number {
  return Math.round(val / GRID_SIZE) * GRID_SIZE;
}

function getNodeDimensions(node: Node): { width: number; height: number } {
  const w = (node as any).measured?.width ?? (node as any).width ?? DEFAULT_NODE_WIDTH;
  const h = (node as any).measured?.height ?? (node as any).height ?? DEFAULT_NODE_HEIGHT;
  return { width: w > 0 ? w : DEFAULT_NODE_WIDTH, height: h > 0 ? h : DEFAULT_NODE_HEIGHT };
}

function compositeBoundingBox(
  boxes: Array<{ x: number; y: number; width: number; height: number }>,
): BoundingBox {
  if (boxes.length === 0) return { x: 0, y: 0, width: 0, height: 0 };
  let minX = Infinity,
    minY = Infinity,
    maxX = -Infinity,
    maxY = -Infinity;
  for (const b of boxes) {
    minX = Math.min(minX, b.x);
    minY = Math.min(minY, b.y);
    maxX = Math.max(maxX, b.x + b.width);
    maxY = Math.max(maxY, b.y + b.height);
  }
  return { x: minX, y: minY, width: maxX - minX, height: maxY - minY };
}

/**
 * Calculate an auto-layout for all provided nodes.
 * Returns a map of node ID → new [x, y] position (grid-snapped).
 */
export function calculateLayout(
  nodes: Node[],
  edges: Edge[],
): Record<string, [number, number]> {
  if (nodes.length === 0) return {};

  // ── 1. Record original bounding box (anchor reference) ──
  const beforeBoxes = nodes.map((n) => {
    const dims = getNodeDimensions(n);
    return { x: n.position.x, y: n.position.y, width: dims.width, height: dims.height };
  });
  const bbBefore = compositeBoundingBox(beforeBoxes);

  // ── 2. Build parent graph for component detection ──
  const parentGraph = new dagre.graphlib.Graph();
  parentGraph.setGraph({});
  parentGraph.setDefaultEdgeLabel(() => ({}));

  // Sort: root nodes (no incoming edges) first, then by position (top→bottom, left→right)
  const targetIds = new Set(edges.map((e) => e.target));
  const sorted = [...nodes].sort((a, b) => {
    const aRoot = !targetIds.has(a.id);
    const bRoot = !targetIds.has(b.id);
    if (aRoot !== bRoot) return aRoot ? -1 : 1;
    const dy = a.position.y - b.position.y;
    return dy !== 0 ? dy : a.position.x - b.position.x;
  });

  const nodeById: Record<string, Node> = {};
  for (const node of sorted) {
    const dims = getNodeDimensions(node);
    parentGraph.setNode(node.id, { width: dims.width, height: dims.height });
    nodeById[node.id] = node;
  }

  for (const edge of edges) {
    if (parentGraph.hasNode(edge.source) && parentGraph.hasNode(edge.target)) {
      parentGraph.setEdge(edge.source, edge.target);
    }
  }

  // ── 3. Detect connected components (disconnected subgraphs) ──
  const components = dagre.graphlib.alg.components(parentGraph);

  // Preserve the sorted insertion order within each component
  const sortedIds = sorted.map((n) => n.id);

  const subgraphResults: Array<{
    positions: Record<string, { x: number; y: number; width: number; height: number }>;
    bb: BoundingBox;
  }> = [];

  for (const compNodeIds of components) {
    // ── 4. Create subgraph for this component ──
    const subGraph = new dagre.graphlib.Graph();
    subGraph.setGraph({
      rankdir: 'LR',
      edgesep: NODE_Y_SPACING,
      nodesep: NODE_Y_SPACING,
      ranksep: NODE_X_SPACING,
    });
    subGraph.setDefaultEdgeLabel(() => ({}));

    const compSet = new Set(compNodeIds);

    // Add nodes in the pre-sorted order
    for (const id of sortedIds) {
      if (!compSet.has(id)) continue;
      const label = parentGraph.node(id);
      subGraph.setNode(id, { width: label.width, height: label.height });
    }

    // Add edges within this component
    for (const edge of edges) {
      if (subGraph.hasNode(edge.source) && subGraph.hasNode(edge.target)) {
        subGraph.setEdge(edge.source, edge.target);
      }
    }

    // ── 5. Apply Dagre layout ──
    dagre.layout(subGraph, { disableOptimalOrderHeuristic: true });

    // Convert Dagre centre coordinates → top-left
    const positions: Record<string, { x: number; y: number; width: number; height: number }> = {};
    for (const nodeId of subGraph.nodes()) {
      const dn = subGraph.node(nodeId);
      positions[nodeId] = {
        x: dn.x - dn.width / 2,
        y: dn.y - dn.height / 2,
        width: dn.width,
        height: dn.height,
      };
    }

    subgraphResults.push({
      positions,
      bb: compositeBoundingBox(Object.values(positions)),
    });
  }

  // ── 6. Arrange multiple subgraphs vertically ──
  let allPositions: Record<string, { x: number; y: number; width: number; height: number }> = {};

  if (subgraphResults.length === 1) {
    allPositions = subgraphResults[0].positions;
  } else {
    const vertGraph = new dagre.graphlib.Graph();
    vertGraph.setGraph({
      rankdir: 'TB',
      align: 'UL',
      edgesep: SUBGRAPH_SPACING,
      nodesep: SUBGRAPH_SPACING,
      ranksep: SUBGRAPH_SPACING,
    });
    vertGraph.setDefaultEdgeLabel(() => ({}));

    for (let i = 0; i < subgraphResults.length; i++) {
      const bb = subgraphResults[i].bb;
      vertGraph.setNode(i.toString(), { width: bb.width, height: bb.height });
    }

    dagre.layout(vertGraph, { disableOptimalOrderHeuristic: true });

    for (let i = 0; i < subgraphResults.length; i++) {
      const vn = vertGraph.node(i.toString());
      const subBb = subgraphResults[i].bb;

      // Dagre centre → top-left offset, relative to subgraph's own bounding box
      const offsetX = vn.x - vn.width / 2 - subBb.x;
      const offsetY = vn.y - vn.height / 2 - subBb.y;

      for (const [nodeId, pos] of Object.entries(subgraphResults[i].positions)) {
        allPositions[nodeId] = {
          x: pos.x + offsetX,
          y: pos.y + offsetY,
          width: pos.width,
          height: pos.height,
        };
      }
    }
  }

  // ── 7. Anchor adjustment (keep layout origin near original position) ──
  const bbAfter = compositeBoundingBox(Object.values(allPositions));
  const anchorX = bbAfter.x - bbBefore.x;
  const anchorY = bbAfter.y - bbBefore.y;

  // ── 8. Build final grid-snapped positions ──
  const result: Record<string, [number, number]> = {};
  for (const [nodeId, pos] of Object.entries(allPositions)) {
    result[nodeId] = [snapToGrid(pos.x - anchorX), snapToGrid(pos.y - anchorY)];
  }

  return result;
}
