import React, { memo, useState, useCallback } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { NodeIcon } from './node-icon-map';
import NodeActionToolbar from './node-action-toolbar';

interface TrellisTriggerNodeData {
  label: string;
  nodeType?: string;
  nodeParameters?: Record<string, any>;
  typeDescription?: {
    displayName: string;
    icon: string;
    subtitle: string;
    outputs: Array<{ name: string; type: string }>;
  };
  executionStatus?: 'success' | 'error' | 'running' | 'waiting';
  itemCount?: number;
  disabled?: boolean;
  readOnly?: boolean;
  onOutputHandleDoubleClick?: (handleId: string) => void;
  [key: string]: unknown;
}

const PROXIMITY_PX = 30;

function encodeHandleId(type: string, index: number): string {
  return `${type}:${index}`;
}

const TrellisTriggerNode = memo(({ id, data, selected }: NodeProps & { data: TrellisTriggerNodeData }) => {
  const typeDesc = data.typeDescription;
  const displayName = data.label || typeDesc?.displayName || 'Trigger';
  const subtitle = typeDesc?.subtitle || '';
  const icon = typeDesc?.icon || '';
  const outputs = typeDesc?.outputs || [{ name: 'main', type: 'main' }];
  const status = data.executionStatus;
  const itemCount = data.itemCount;
  const [nearby, setNearby] = useState(false);

  const onProximityEnter = useCallback(() => setNearby(true), []);
  const onProximityLeave = useCallback(() => setNearby(false), []);

  const statusClass = status ? ` status-${status}` : '';
  const selectedClass = selected ? ' selected' : '';
  const disabledClass = data.disabled ? ' disabled' : '';

  const nodeContent = (
    <div className={`trellis-node trigger-node icon-only${statusClass}${selectedClass}${disabledClass}`}>
      <div className="node-header">
        <div className="node-icon trigger">
          {icon ? <NodeIcon name={icon} size={32} /> : (
            <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" strokeWidth="2">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
            </svg>
          )}
        </div>
      </div>

      {status && (
        <div className={`node-status-badge ${status}`}>
          {status === 'success' && (
            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="3">
              <polyline points="20 6 9 17 4 12" />
            </svg>
          )}
          {status === 'error' && (
            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="3">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          )}
          {status === 'running' && (
            <div className="running-spinner" />
          )}
          {itemCount !== undefined && itemCount >= 0 && (
            <span className="item-count">{itemCount}</span>
          )}
        </div>
      )}

      {outputs.map((output, index) => (
        <React.Fragment key={`output-${output.name}`}>
          <Handle
            type="source"
            position={Position.Right}
            id={encodeHandleId(output.type, index)}
            style={{ top: `${((index + 1) / (outputs.length + 1)) * 100}%` }}
            className="trellis-handle"
            onClick={(e) => {
              e.stopPropagation();
              data.onOutputHandleDoubleClick?.(encodeHandleId(output.type, index));
            }}
          />
          {data.nodeType === 'webhook' && data.nodeParameters?.['httpMethod'] && (
            <span
              className="output-handle-label"
              style={{ top: `${((index + 1) / (outputs.length + 1)) * 100}%` }}
            >
              {data.nodeParameters['httpMethod']}
            </span>
          )}
        </React.Fragment>
      ))}
    </div>
  );

  const wrappedContent = (
    <div className="node-with-label">
      {nodeContent}
      <div className="node-label-below">{displayName}</div>
    </div>
  );

  if (data.readOnly) {
    return wrappedContent;
  }

  return (
    <div
      className="node-proximity-wrapper"
      style={{ padding: PROXIMITY_PX, margin: -PROXIMITY_PX }}
      onMouseEnter={onProximityEnter}
      onMouseLeave={onProximityLeave}
    >
      <NodeActionToolbar nodeId={id} selected={!!selected} nearby={nearby} disabled={data.disabled} />
      {wrappedContent}
    </div>
  );
});

TrellisTriggerNode.displayName = 'TrellisTriggerNode';
export default TrellisTriggerNode;
