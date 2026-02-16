import React from 'react';
import {
  getSmoothStepPath,
  BaseEdge,
  type EdgeProps,
} from '@xyflow/react';

interface TrellisEdgeData {
  animated?: boolean;
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

  return (
    <BaseEdge
      id={id}
      path={edgePath}
      markerEnd={markerEnd}
      style={{
        stroke: 'hsl(0, 0%, 24%)',
        strokeWidth: 2,
        ...style,
        ...(isAnimated ? { stroke: 'hsl(0, 0%, 46%)', strokeDasharray: '5 5', animation: 'dashdraw 0.5s linear infinite' } : {}),
      }}
    />
  );
}
