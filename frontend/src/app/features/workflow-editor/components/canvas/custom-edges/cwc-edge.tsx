import React, { memo, useState, useRef, useCallback, useContext, useMemo } from 'react';
import {
  getSmoothStepPath,
  BaseEdge,
  EdgeLabelRenderer,
  type EdgeProps,
} from '@xyflow/react';
import { CanvasActionsContext } from '../cwc-canvas';

/**
 * Subdivide straight segments of an SVG path and add perpendicular wobble
 * for a hand-drawn / crayon look. Curves (Q/C) are left intact.
 */
function jitterPath(path: string, seed: number, amount: number = 2.5, step: number = 18): string {
  let s = Math.abs(seed) | 1;
  const rand = () => {
    s = (s * 1103515245 + 12345) & 0x7fffffff;
    return (s / 0x7fffffff) * 2 - 1; // -1 to 1
  };

  // Tokenise: split into commands (M, L, Q, C, etc.) with their coordinate groups
  const tokens = path.match(/[MLHVCSQTAZmlhvcsqtaz][^MLHVCSQTAZmlhvcsqtaz]*/g);
  if (!tokens) return path;

  let cx = 0, cy = 0; // current point
  const parts: string[] = [];

  for (const tok of tokens) {
    const cmd = tok[0];
    const nums = tok.slice(1).trim().match(/-?\d+\.?\d*/g)?.map(Number) || [];

    if (cmd === 'M') {
      cx = nums[0]; cy = nums[1];
      parts.push(`M${cx},${cy}`);
    } else if (cmd === 'L') {
      const tx = nums[0], ty = nums[1];
      const dx = tx - cx, dy = ty - cy;
      const len = Math.sqrt(dx * dx + dy * dy);
      const segments = Math.max(1, Math.round(len / step));
      // Perpendicular direction
      const px = len > 0 ? -dy / len : 0;
      const py = len > 0 ? dx / len : 0;

      for (let i = 1; i <= segments; i++) {
        const t = i / segments;
        const wobble = i < segments ? rand() * amount : 0; // no wobble on final point
        const x = cx + dx * t + px * wobble;
        const y = cy + dy * t + py * wobble;
        parts.push(`L${x.toFixed(1)},${y.toFixed(1)}`);
      }
      cx = tx; cy = ty;
    } else {
      // Q, C, etc. — keep as-is, just lightly jitter control points
      const jittered = tok.replace(/-?\d+\.?\d*/g, (m) => {
        return (parseFloat(m) + rand() * (amount * 0.3)).toFixed(1);
      });
      parts.push(jittered);
      // Update current point to last coordinate pair
      if (nums.length >= 2) {
        cx = nums[nums.length - 2];
        cy = nums[nums.length - 1];
      }
    }
  }

  return parts.join(' ');
}

interface CwcEdgeData {
  animated?: boolean;
  status?: 'success' | 'error';
  connectionType?: string;
  isAi?: boolean;
  readOnly?: boolean;
  [key: string]: unknown;
}

// AI connection type colors
const AI_EDGE_COLORS: Record<string, string> = {
  ai_languageModel: 'hsl(200, 80%, 55%)',
  ai_memory: 'hsl(280, 70%, 60%)',
  ai_tool: 'hsl(35, 90%, 55%)',
  ai_outputParser: 'hsl(160, 60%, 45%)',
  ai_embedding: 'hsl(330, 70%, 55%)',
  ai_vectorStore: 'hsl(15, 85%, 55%)',
  ai_retriever: 'hsl(50, 80%, 50%)',
  ai_agent: 'hsl(200, 80%, 55%)',
};

function CwcEdgeComponent({
  id,
  source,
  target,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  sourceHandleId,
  targetHandleId,
  selected,
  style = {},
  markerEnd,
  data,
}: EdgeProps & { data?: CwcEdgeData }) {
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 8,
  });

  const ctx = useContext(CanvasActionsContext);

  // Crayon mode: jitter the path for a hand-drawn look
  const renderedPath = useMemo(() => {
    if (!ctx.crayonMode) return edgePath;
    const seed = Math.round(sourceX * 73 + sourceY * 137 + targetX * 251 + targetY * 389);
    return jitterPath(edgePath, seed);
  }, [ctx.crayonMode, edgePath, sourceX, sourceY, targetX, targetY]);

  const [hovered, setHovered] = useState(false);
  const hideTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToolbar = useCallback(() => {
    if (hideTimeout.current) {
      clearTimeout(hideTimeout.current);
      hideTimeout.current = null;
    }
    setHovered(true);
  }, []);

  const hideToolbar = useCallback(() => {
    hideTimeout.current = setTimeout(() => setHovered(false), 300);
  }, []);

  const isAnimated = data?.animated || false;
  const status = data?.status;
  const isAi = data?.isAi || false;
  const connectionType = data?.connectionType || 'main';
  const readOnly = data?.readOnly || false;

  const strokeColor = useMemo(() => {
    if (selected) return 'hsl(7, 100%, 68%)';
    if (isAnimated && !isAi) return 'hsl(147, 60%, 50%)';
    if (status === 'success') return 'hsl(147, 60%, 40%)';
    if (status === 'error') return 'hsl(355, 83%, 52%)';
    if (isAi) return AI_EDGE_COLORS[connectionType] || 'hsl(200, 80%, 55%)';
    return 'hsl(0, 0%, 24%)';
  }, [selected, status, isAi, isAnimated, connectionType]);

  const edgeStyle = useMemo(() => ({
    stroke: strokeColor,
    strokeWidth: 2,
    transition: 'stroke 0.3s ease',
    ...style,
    ...(isAi && !isAnimated ? { strokeDasharray: '6 3' } : {}),
  }), [strokeColor, isAi, isAnimated, style]);

  // Build CSS class string for animation (CSS-driven, not inline style)
  const className = isAnimated ? 'cwc-edge-animated' : '';

  return (
    <>
      <g onMouseEnter={showToolbar} onMouseLeave={hideToolbar}>
        <BaseEdge
          id={id}
          path={renderedPath}
          markerEnd={markerEnd}
          style={edgeStyle}
          className={className}
        />
      </g>
      {!readOnly && (
        <EdgeLabelRenderer>
          <div
            className={`edge-action-toolbar nodrag nopan${hovered ? ' visible' : ''}`}
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
              pointerEvents: hovered ? 'all' : 'none',
            }}
            onMouseEnter={showToolbar}
            onMouseLeave={hideToolbar}
          >
            <button
              className="eat-btn"
              title="Add node"
              onClick={(e) => {
                e.stopPropagation();
                ctx.insertNodeOnEdge?.(id, source, target, sourceHandleId || '', targetHandleId || '');
              }}
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            </button>
            <button
              className="eat-btn eat-delete"
              title="Delete connection"
              onClick={(e) => {
                e.stopPropagation();
                ctx.deleteEdge?.(id);
              }}
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
              </svg>
            </button>
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

export default memo(CwcEdgeComponent, (prev, next) => {
  return (
    prev.sourceX === next.sourceX &&
    prev.sourceY === next.sourceY &&
    prev.targetX === next.targetX &&
    prev.targetY === next.targetY &&
    prev.selected === next.selected &&
    prev.data?.animated === next.data?.animated &&
    prev.data?.status === next.data?.status &&
    prev.data?.readOnly === next.data?.readOnly
  );
});
