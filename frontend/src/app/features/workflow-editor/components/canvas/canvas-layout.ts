/**
 * Canvas auto-layout using the Dagre graph layout algorithm.
 * Mirrors the n8n "Tidy up" feature: left-to-right DAG layout,
 * connected-component detection, vertical stacking of disconnected subgraphs,
 * and grid-snapped positions anchored to the original bounding box.
 *
 * AI-aware: AI sub-nodes (chat models, tools, memory) are positioned in a
 * horizontal row centered below their parent node instead of in the main LR flow.
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
  const parentToSubNodes = new Map<string, Array<{ subNodeId: string; aiType: string }>>();
  const subNodeIds = new Set<string>();

  for (const edge of aiEdges) {
    const aiType = getAiType(edge.sourceHandle)!;
    const parentId = edge.target;
    const subNodeId = edge.source;
    subNodeIds.add(subNodeId);

    if (!parentToSubNodes.has(parentId)) {
      parentToSubNodes.set(parentId, []);
    }
    // Avoid duplicates (same sub-node connected multiple times)
    const existing = parentToSubNodes.get(parentId)!;
    if (!existing.some((s) => s.subNodeId === subNodeId)) {
      existing.push({ subNodeId, aiType });
    }
  }

  // Sort sub-nodes by AI type order, then by original x position for stability
  const nodeById: Record<string, Node> = {};
  for (const node of nodes) {
    nodeById[node.id] = node;
  }

  for (const [, subs] of parentToSubNodes) {
    subs.sort((a, b) => {
      const orderA = AI_TYPE_ORDER[a.aiType] ?? 99;
      const orderB = AI_TYPE_ORDER[b.aiType] ?? 99;
      if (orderA !== orderB) return orderA - orderB;
      // Same type — sort by original x position
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
    positions: Record<string, { x: number; y: number; width: number; height: number }>;
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
    const positions: Record<string, { x: number; y: number; width: number; height: number }> = {};
    for (const nodeId of subGraph.nodes()) {
      const dn = subGraph.node(nodeId);
      const actualNode = nodeById[nodeId];
      const actualDims = actualNode ? getNodeDimensions(actualNode) : { width: dn.width, height: dn.height };
      const inflation = parentInflation.get(nodeId);

      // Dagre centers the node in the inflated box. We want the parent node
      // at the top-center of that box, so adjust y for inflated parents.
      let x = dn.x - actualDims.width / 2;
      let y: number;
      if (inflation) {
        // Parent sits at the top of the inflated box
        y = dn.y - dn.height / 2;
      } else {
        y = dn.y - actualDims.height / 2;
      }

      positions[nodeId] = {
        x,
        y,
        width: actualDims.width,
        height: actualDims.height,
      };

      // ── 9. Position sub-nodes below the parent ──
      if (inflation && parentToSubNodes.has(nodeId)) {
        const subs = parentToSubNodes.get(nodeId)!;
        const parentCenterX = dn.x; // Dagre center x
        const subRowTop = y + actualDims.height + AI_SUB_NODE_GAP_Y;

        // Calculate starting x to center the cluster below the parent
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
    }

    // Bounding box includes both parent and sub-node positions
    subgraphResults.push({
      positions,
      bb: compositeBoundingBox(Object.values(positions)),
    });
  }

  // ── 10. Arrange multiple subgraphs vertically ──
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
