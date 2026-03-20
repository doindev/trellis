/**
 * Canvas auto-layout using the Dagre graph layout algorithm.
 * Mirrors the n8n "Tidy up" feature: left-to-right DAG layout,
 * connected-component detection, vertical stacking of disconnected subgraphs,
 * and grid-snapped positions anchored to the original bounding box.
 *
 * AI-aware: AI sub-nodes (chat models, tools, memory) are positioned in a
 * horizontal row centered below their parent node instead of in the main LR flow.
 *
 * Fan-out aware: when a node has multiple output handles, targets are ordered
 * vertically to match their handle indices (top handle → top target), avoiding
 * edge crossings.
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

const AI_SUB_NODE_GAP_Y = 64; // vertical gap: parent bottom → sub-node top
const AI_SUB_NODE_GAP_X = 48; // horizontal gap between sub-nodes in the row

/** Sort priority for AI connection types — lower = further left in the sub-node row */
const AI_TYPE_ORDER: Record<string, number> = {
  ai_languageModel: 0,
  ai_memory: 1,
  ai_tool: 2,
  ai_outputParser: 3,
  ai_embedding: 4,
  ai_vectorStore: 5,
  ai_retriever: 6,
  ai_agent: 7,
};

interface BoundingBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface PositionEntry {
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
 * Extract the AI connection type from a sourceHandle string.
 * Handles like "ai_tool:0", "ai_languageModel:0", etc.
 * Returns the type prefix (e.g. "ai_tool") or null if not an AI handle.
 */
function getAiType(sourceHandle: string | null | undefined): string | null {
  if (!sourceHandle || !sourceHandle.startsWith('ai_')) return null;
  const colonIdx = sourceHandle.indexOf(':');
  return colonIdx > 0 ? sourceHandle.substring(0, colonIdx) : sourceHandle;
}

/**
 * Extract the numeric index from a handle string like "main:1" or "ai_tool:0".
 */
function parseHandleIndex(handle: string | null | undefined): number {
  if (!handle) return 0;
  const colonIdx = handle.lastIndexOf(':');
  if (colonIdx < 0) return 0;
  return parseInt(handle.substring(colonIdx + 1), 10) || 0;
}

/**
 * After Dagre layout, fix fan-out crossings so that the vertical order of
 * target nodes matches the source handle index order (handle 0 on top,
 * handle 1 below, etc.). This prevents connector lines from crossing each other.
 *
 * For each fan-out node, the existing y-position "slots" assigned by Dagre are
 * redistributed among the targets in handle-index order. Each target's entire
 * downstream subtree is shifted by the same delta.
 */
function fixFanOutCrossings(
  positions: Record<string, PositionEntry>,
  mainEdges: Edge[],
): void {
  // Build outgoing edge map: sourceId → [{targetId, handleIndex}]
  const outgoing = new Map<string, Array<{ targetId: string; handleIndex: number }>>();
  for (const edge of mainEdges) {
    const idx = parseHandleIndex(edge.sourceHandle);
    if (!outgoing.has(edge.source)) outgoing.set(edge.source, []);
    outgoing.get(edge.source)!.push({ targetId: edge.target, handleIndex: idx });
  }

  // Build forward adjacency for subtree detection
  const forwardAdj = new Map<string, string[]>();
  for (const edge of mainEdges) {
    if (!forwardAdj.has(edge.source)) forwardAdj.set(edge.source, []);
    forwardAdj.get(edge.source)!.push(edge.target);
  }

  // Collect fan-out nodes (multiple distinct targets) sorted left-to-right
  const fanOutNodes = [...outgoing.entries()]
    .filter(([id, targets]) => {
      if (!positions[id]) return false;
      const uniqueTargets = new Set(targets.map((t) => t.targetId));
      return uniqueTargets.size > 1;
    })
    .sort((a, b) => (positions[a[0]]?.x ?? 0) - (positions[b[0]]?.x ?? 0));

  for (const [, targets] of fanOutNodes) {
    // Deduplicate targets: keep lowest handle index per target
    const targetMap = new Map<string, number>();
    for (const t of targets) {
      if (
        positions[t.targetId] &&
        (!targetMap.has(t.targetId) || t.handleIndex < targetMap.get(t.targetId)!)
      ) {
        targetMap.set(t.targetId, t.handleIndex);
      }
    }

    const targetList = [...targetMap.entries()].map(([id, handleIndex]) => ({
      targetId: id,
      handleIndex,
      yCenter: positions[id].y + positions[id].height / 2,
    }));

    if (targetList.length <= 1) continue;

    // Skip if all targets share the same handle index (no ordering preference)
    const uniqueHandles = new Set(targetList.map((t) => t.handleIndex));
    if (uniqueHandles.size <= 1) continue;

    // Desired order: sorted by handle index (top to bottom)
    const byHandle = [...targetList].sort((a, b) => a.handleIndex - b.handleIndex);
    // Current order: sorted by y position (top to bottom)
    const byY = [...targetList].sort((a, b) => a.yCenter - b.yCenter);

    // Already correct — nothing to fix
    if (byHandle.every((t, i) => t.targetId === byY[i].targetId)) continue;

    // Available y-center slots from current Dagre positions (sorted ascending)
    const ySlots = byY.map((t) => t.yCenter);

    // Compute non-overlapping subtrees (first-claimed wins for shared nodes)
    const claimed = new Set<string>();
    const subtreeDeltas: Array<{ subtree: Set<string>; delta: number }> = [];

    for (let i = 0; i < byHandle.length; i++) {
      const targetId = byHandle[i].targetId;
      const currentYCenter = positions[targetId].y + positions[targetId].height / 2;
      const delta = ySlots[i] - currentYCenter;

      // BFS to find all downstream nodes belonging to this branch
      const subtree = new Set<string>();
      const queue = [targetId];
      while (queue.length > 0) {
        const id = queue.shift()!;
        if (subtree.has(id) || claimed.has(id)) continue;
        subtree.add(id);
        const neighbors = forwardAdj.get(id);
        if (neighbors) {
          for (const next of neighbors) {
            if (!subtree.has(next) && !claimed.has(next)) {
              queue.push(next);
            }
          }
        }
      }

      for (const id of subtree) claimed.add(id);
      subtreeDeltas.push({ subtree, delta });
    }

    // Apply all deltas for this fan-out
    for (const { subtree, delta } of subtreeDeltas) {
      if (Math.abs(delta) < 0.5) continue;
      for (const nodeId of subtree) {
        if (positions[nodeId]) {
          positions[nodeId].y += delta;
        }
      }
    }
  }
}

/**
 * Calculate an auto-layout for all provided nodes.
 * Returns a map of node ID → new [x, y] position (grid-snapped).
 *
 * AI sub-nodes are arranged in a horizontal row below their parent node
 * instead of being placed in the main left-to-right flow.
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

  // ── 2. Classify edges into main edges and AI edges ──
  const mainEdges: Edge[] = [];
  const aiEdges: Edge[] = [];
  for (const edge of edges) {
    if (getAiType(edge.sourceHandle) !== null) {
      aiEdges.push(edge);
    } else {
      mainEdges.push(edge);
    }
  }

  // ── 3. Build parent→sub-node mapping ──
  // For AI edges: source = sub-node (top handle), target = parent (bottom handle)
  // The targetHandle index corresponds to the visual left-to-right position of the
  // handle on the parent's bottom (e.g. ai_languageModel:0, ai_memory:1, ai_tool:2).
  const parentToSubNodes = new Map<
    string,
    Array<{ subNodeId: string; aiType: string; targetHandleIndex: number }>
  >();
  const subNodeIds = new Set<string>();

  for (const edge of aiEdges) {
    const aiType = getAiType(edge.sourceHandle)!;
    const parentId = edge.target;
    const subNodeId = edge.source;
    const targetHandleIndex = parseHandleIndex(edge.targetHandle);
    subNodeIds.add(subNodeId);

    if (!parentToSubNodes.has(parentId)) {
      parentToSubNodes.set(parentId, []);
    }
    // Avoid duplicates (same sub-node connected multiple times)
    const existing = parentToSubNodes.get(parentId)!;
    if (!existing.some((s) => s.subNodeId === subNodeId)) {
      existing.push({ subNodeId, aiType, targetHandleIndex });
    }
  }

  // Sort sub-nodes left-to-right by target handle index on the parent, so the
  // sub-node below handle position 0 is leftmost and connector lines don't cross.
  const nodeById: Record<string, Node> = {};
  for (const node of nodes) {
    nodeById[node.id] = node;
  }

  for (const [, subs] of parentToSubNodes) {
    subs.sort((a, b) => {
      // Primary: target handle index determines left-to-right visual order
      if (a.targetHandleIndex !== b.targetHandleIndex) return a.targetHandleIndex - b.targetHandleIndex;
      // Fallback: AI type order
      const orderA = AI_TYPE_ORDER[a.aiType] ?? 99;
      const orderB = AI_TYPE_ORDER[b.aiType] ?? 99;
      if (orderA !== orderB) return orderA - orderB;
      // Same type and index — sort by original x position
      const posA = nodeById[a.subNodeId]?.position.x ?? 0;
      const posB = nodeById[b.subNodeId]?.position.x ?? 0;
      return posA - posB;
    });
  }

  // ── 4. Compute sub-node cluster dimensions and inflate parent nodes ──
  const parentInflation = new Map<
    string,
    { clusterWidth: number; clusterHeight: number; inflatedWidth: number; inflatedHeight: number }
  >();

  for (const [parentId, subs] of parentToSubNodes) {
    const parentNode = nodeById[parentId];
    if (!parentNode) continue;

    const parentDims = getNodeDimensions(parentNode);
    let clusterWidth = 0;
    let clusterHeight = 0;

    for (let i = 0; i < subs.length; i++) {
      const subNode = nodeById[subs[i].subNodeId];
      if (!subNode) continue;
      const subDims = getNodeDimensions(subNode);
      clusterWidth += subDims.width;
      if (i > 0) clusterWidth += AI_SUB_NODE_GAP_X;
      clusterHeight = Math.max(clusterHeight, subDims.height);
    }

    const inflatedWidth = Math.max(parentDims.width, clusterWidth);
    const inflatedHeight = parentDims.height + AI_SUB_NODE_GAP_Y + clusterHeight;

    parentInflation.set(parentId, { clusterWidth, clusterHeight, inflatedWidth, inflatedHeight });
  }

  // ── 5. Build parent graph for component detection (main nodes only) ──
  const mainNodes = nodes.filter((n) => !subNodeIds.has(n.id));

  const parentGraph = new dagre.graphlib.Graph();
  parentGraph.setGraph({});
  parentGraph.setDefaultEdgeLabel(() => ({}));

  // Sort: root nodes (no incoming main edges) first, then by position
  const mainTargetIds = new Set(mainEdges.map((e) => e.target));
  const sorted = [...mainNodes].sort((a, b) => {
    const aRoot = !mainTargetIds.has(a.id);
    const bRoot = !mainTargetIds.has(b.id);
    if (aRoot !== bRoot) return aRoot ? -1 : 1;
    const dy = a.position.y - b.position.y;
    return dy !== 0 ? dy : a.position.x - b.position.x;
  });

  for (const node of sorted) {
    const inflation = parentInflation.get(node.id);
    if (inflation) {
      // Use inflated dimensions so Dagre reserves space for sub-nodes
      parentGraph.setNode(node.id, {
        width: inflation.inflatedWidth,
        height: inflation.inflatedHeight,
      });
    } else {
      const dims = getNodeDimensions(node);
      parentGraph.setNode(node.id, { width: dims.width, height: dims.height });
    }
  }

  for (const edge of mainEdges) {
    if (parentGraph.hasNode(edge.source) && parentGraph.hasNode(edge.target)) {
      parentGraph.setEdge(edge.source, edge.target);
    }
  }

  // ── 6. Detect connected components (disconnected subgraphs) ──
  const components = dagre.graphlib.alg.components(parentGraph);

  const sortedIds = sorted.map((n) => n.id);

  const subgraphResults: Array<{
    positions: Record<string, PositionEntry>;
    bb: BoundingBox;
  }> = [];

  for (const compNodeIds of components) {
    // ── 7. Create subgraph for this component ──
    const subGraph = new dagre.graphlib.Graph();
    subGraph.setGraph({
      rankdir: 'LR',
      edgesep: NODE_Y_SPACING,
      nodesep: NODE_Y_SPACING,
      ranksep: NODE_X_SPACING,
    });
    subGraph.setDefaultEdgeLabel(() => ({}));

    const compSet = new Set(compNodeIds);

    // Add main nodes in the pre-sorted order with inflated dims where needed
    for (const id of sortedIds) {
      if (!compSet.has(id)) continue;
      const label = parentGraph.node(id);
      subGraph.setNode(id, { width: label.width, height: label.height });
    }

    // Add only main edges within this component
    for (const edge of mainEdges) {
      if (subGraph.hasNode(edge.source) && subGraph.hasNode(edge.target)) {
        subGraph.setEdge(edge.source, edge.target);
      }
    }

    // ── 8. Apply Dagre layout (main nodes only) ──
    dagre.layout(subGraph, { disableOptimalOrderHeuristic: true });

    // Convert Dagre centre coordinates → top-left, using ACTUAL node dims (not inflated)
    const positions: Record<string, PositionEntry> = {};
    for (const nodeId of subGraph.nodes()) {
      const dn = subGraph.node(nodeId);
      const actualNode = nodeById[nodeId];
      const actualDims = actualNode ? getNodeDimensions(actualNode) : { width: dn.width, height: dn.height };
      const inflation = parentInflation.get(nodeId);

      // Dagre centers the node in the inflated box. We want the parent node
      // at the top-center of that box, so adjust y for inflated parents.
      const x = dn.x - actualDims.width / 2;
      const y = inflation
        ? dn.y - dn.height / 2  // Parent sits at the top of the inflated box
        : dn.y - actualDims.height / 2;

      positions[nodeId] = { x, y, width: actualDims.width, height: actualDims.height };
    }

    // ── 8b. Fix fan-out crossings ──
    // Reorder targets of multi-output nodes so their vertical order matches
    // source handle indices, preventing connector lines from crossing.
    fixFanOutCrossings(positions, mainEdges);

    // ── 9. Position sub-nodes below parents (using crossing-fixed positions) ──
    for (const nodeId of subGraph.nodes()) {
      const inflation = parentInflation.get(nodeId);
      if (!inflation || !parentToSubNodes.has(nodeId)) continue;

      const pos = positions[nodeId];
      const subs = parentToSubNodes.get(nodeId)!;
      const parentCenterX = pos.x + pos.width / 2;
      const subRowTop = pos.y + pos.height + AI_SUB_NODE_GAP_Y;

      const startX = parentCenterX - inflation.clusterWidth / 2;
      let curX = startX;

      for (const sub of subs) {
        const subNode = nodeById[sub.subNodeId];
        if (!subNode) continue;
        const subDims = getNodeDimensions(subNode);

        positions[sub.subNodeId] = {
          x: curX,
          y: subRowTop,
          width: subDims.width,
          height: subDims.height,
        };

        curX += subDims.width + AI_SUB_NODE_GAP_X;
      }
    }

    // Bounding box includes both parent and sub-node positions
    subgraphResults.push({
      positions,
      bb: compositeBoundingBox(Object.values(positions)),
    });
  }

  // ── 10. Arrange multiple subgraphs vertically ──
  let allPositions: Record<string, PositionEntry> = {};

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

  // ── 11. Anchor adjustment (keep layout origin near original position) ──
  const bbAfter = compositeBoundingBox(Object.values(allPositions));
  const anchorX = bbAfter.x - bbBefore.x;
  const anchorY = bbAfter.y - bbBefore.y;

  // ── 12. Build final grid-snapped positions ──
  const result: Record<string, [number, number]> = {};
  for (const [nodeId, pos] of Object.entries(allPositions)) {
    result[nodeId] = [snapToGrid(pos.x - anchorX), snapToGrid(pos.y - anchorY)];
  }

  return result;
}
