import React, { useState, useRef, useCallback, useContext } from 'react';
import {
  getSmoothStepPath,
  BaseEdge,
  EdgeLabelRenderer,
  type EdgeProps,
} from '@xyflow/react';
import { CanvasActionsContext } from '../trellis-canvas';

interface TrellisEdgeData {
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

export default function TrellisEdge({
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
}: EdgeProps & { data?: TrellisEdgeData }) {
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

  let strokeColor = 'hsl(0, 0%, 24%)';
  if (selected) strokeColor = 'hsl(7, 100%, 68%)';
  else if (status === 'success') strokeColor = 'hsl(147, 60%, 40%)';
  else if (status === 'error') strokeColor = 'hsl(355, 83%, 52%)';
  else if (isAi) strokeColor = AI_EDGE_COLORS[connectionType] || 'hsl(200, 80%, 55%)';

  return (
    <>
      {/* Wrap in <g> so mouse events on BaseEdge's interaction path bubble up */}
      <g onMouseEnter={showToolbar} onMouseLeave={hideToolbar}>
        <BaseEdge
          id={id}
          path={edgePath}
          markerEnd={markerEnd}
          style={{
            stroke: strokeColor,
            strokeWidth: 2,
            transition: 'stroke 0.15s ease',
            ...style,
            ...(isAi && !isAnimated ? { strokeDasharray: '6 3' } : {}),
            ...(isAnimated ? { stroke: isAi ? strokeColor : 'hsl(147, 60%, 50%)', strokeDasharray: '5 5', animation: 'dashdraw 0.5s linear infinite' } : {}),
          }}
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
