import React from 'react';
import {
  getSmoothStepPath,
  BaseEdge,
  type EdgeProps,
} from '@xyflow/react';

interface TrellisEdgeData {
  animated?: boolean;
  status?: 'success' | 'error';
  [key: string]: unknown;
}

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

  let strokeColor = 'hsl(0, 0%, 24%)';
  if (selected) strokeColor = 'hsl(7, 100%, 68%)';
  else if (status === 'success') strokeColor = 'hsl(147, 60%, 40%)';
  else if (status === 'error') strokeColor = 'hsl(355, 83%, 52%)';

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
        ...(isAnimated ? { stroke: 'hsl(0, 0%, 46%)', strokeDasharray: '5 5', animation: 'dashdraw 0.5s linear infinite' } : {}),
      }}
    />
  );
}
