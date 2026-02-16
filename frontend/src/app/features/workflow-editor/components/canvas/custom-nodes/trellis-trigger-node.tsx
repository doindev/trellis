import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';

interface TrellisTriggerNodeData {
  label: string;
  typeDescription?: {
    displayName: string;
    icon: string;
    subtitle: string;
    outputs: Array<{ name: string; type: string }>;
  };
  executionStatus?: 'success' | 'error' | 'running' | 'waiting';
  itemCount?: number;
  disabled?: boolean;
  [key: string]: unknown;
}

const TrellisTriggerNode = memo(({ data, selected }: NodeProps & { data: TrellisTriggerNodeData }) => {
  const typeDesc = data.typeDescription;
  const displayName = typeDesc?.displayName || data.label || 'Trigger';
  const subtitle = typeDesc?.subtitle || '';
  const icon = typeDesc?.icon || '';
  const outputs = typeDesc?.outputs || [{ name: 'main', type: 'main' }];
  const status = data.executionStatus;
  const itemCount = data.itemCount;

  const statusClass = status ? ` status-${status}` : '';
  const selectedClass = selected ? ' selected' : '';
  const disabledClass = data.disabled ? ' disabled' : '';

  return (
    <div className={`trellis-node trigger-node${statusClass}${selectedClass}${disabledClass}`}>
      <div className="node-header">
        <div className="node-icon trigger">
          {icon || (
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
            </svg>
          )}
        </div>
        <div className="node-title">
          <div className="node-name">{displayName}</div>
          {subtitle && <div className="node-subtitle">{subtitle}</div>}
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
        <Handle
          key={`output-${output.name}`}
          type="source"
          position={Position.Right}
          id={output.name}
          style={{ top: `${((index + 1) / (outputs.length + 1)) * 100}%` }}
          className="trellis-handle"
        />
      ))}
    </div>
  );
});

TrellisTriggerNode.displayName = 'TrellisTriggerNode';
export default TrellisTriggerNode;
