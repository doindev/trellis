import React from 'react';
import {
  getSmoothStepPath,
  BaseEdge,
  type EdgeProps,
} from '@xyflow/react';

interface TrellisEdgeData {
  animated?: boolean;
  status?: 'success' | 'error';
  connectionType?: string;
  isAi?: boolean;
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
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  selected,
  style = {},
  markerEnd,
  data,
}: EdgeProps & { data?: TrellisEdgeData }) {
  const [edgePath] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 8,
  });

  const isAnimated = data?.animated || false;
  const status = data?.status;
  const isAi = data?.isAi || false;
  const connectionType = data?.connectionType || 'main';

  let strokeColor = 'hsl(0, 0%, 24%)';
  if (selected) strokeColor = 'hsl(7, 100%, 68%)';
  else if (status === 'success') strokeColor = 'hsl(147, 60%, 40%)';
  else if (status === 'error') strokeColor = 'hsl(355, 83%, 52%)';
  else if (isAi) strokeColor = AI_EDGE_COLORS[connectionType] || 'hsl(200, 80%, 55%)';

  return (
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
        ...(isAnimated ? { stroke: isAi ? strokeColor : 'hsl(0, 0%, 46%)', strokeDasharray: '5 5', animation: 'dashdraw 0.5s linear infinite' } : {}),
      }}
    />
  );
}
