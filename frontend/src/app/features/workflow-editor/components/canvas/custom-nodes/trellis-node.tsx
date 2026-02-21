import React, { memo, useState, useCallback } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { NodeIcon } from './node-icon-map';
import NodeActionToolbar from './node-action-toolbar';

interface TrellisNodeData {
  label: string;
  typeDescription?: {
    displayName: string;
    icon: string;
    subtitle: string;
    inputs: Array<{ name: string; type: string; displayName?: string }>;
    outputs: Array<{ name: string; type: string; displayName?: string }>;
    isTrigger: boolean;
  };
  executionStatus?: 'success' | 'error' | 'running' | 'waiting';
  itemCount?: number;
  disabled?: boolean;
  readOnly?: boolean;
  onOutputHandleDoubleClick?: (handleId: string) => void;
  [key: string]: unknown;
}

const PROXIMITY_PX = 30;

// AI connection type colors and labels
const AI_HANDLE_STYLES: Record<string, { color: string; label: string }> = {
  ai_languageModel: { color: 'hsl(200, 80%, 55%)', label: 'Model' },
  ai_memory: { color: 'hsl(280, 70%, 60%)', label: 'Memory' },
  ai_tool: { color: 'hsl(35, 90%, 55%)', label: 'Tool' },
  ai_outputParser: { color: 'hsl(160, 60%, 45%)', label: 'Parser' },
  ai_embedding: { color: 'hsl(330, 70%, 55%)', label: 'Embedding' },
  ai_vectorStore: { color: 'hsl(15, 85%, 55%)', label: 'Vector Store' },
  ai_retriever: { color: 'hsl(50, 80%, 50%)', label: 'Retriever' },
  ai_agent: { color: 'hsl(200, 80%, 55%)', label: 'Agent' },
  ai_document: { color: 'hsl(120, 50%, 45%)', label: 'Document' },
  ai_textSplitter: { color: 'hsl(90, 60%, 45%)', label: 'Splitter' },
};

function isAiType(type: string): boolean {
  return type.startsWith('ai_');
}

function encodeHandleId(type: string, index: number): string {
  return `${type}:${index}`;
}

const TrellisNode = memo(({ id, data, selected }: NodeProps & { data: TrellisNodeData }) => {
  const typeDesc = data.typeDescription;
  const displayName = typeDesc?.displayName || data.label || 'Unknown Node';
  const subtitle = typeDesc?.subtitle || '';
  const icon = typeDesc?.icon || '';
  const allInputs = typeDesc?.inputs || [{ name: 'main', type: 'main' }];
  const allOutputs = typeDesc?.outputs || [{ name: 'main', type: 'main' }];
  const status = data.executionStatus;
  const itemCount = data.itemCount;
  const [nearby, setNearby] = useState(false);

  const onProximityEnter = useCallback(() => setNearby(true), []);
  const onProximityLeave = useCallback(() => setNearby(false), []);

  // Split inputs/outputs into main and AI
  const mainInputs = allInputs.filter(i => !isAiType(i.type));
  const aiInputs = allInputs.filter(i => isAiType(i.type));
  const mainOutputs = allOutputs.filter(o => !isAiType(o.type));
  const aiOutputs = allOutputs.filter(o => isAiType(o.type));

  const statusClass = status ? ` status-${status}` : '';
  const selectedClass = selected ? ' selected' : '';
  const disabledClass = data.disabled ? ' disabled' : '';
  const hasAiHandles = aiInputs.length > 0 || aiOutputs.length > 0;

  const nodeContent = (
    <div className={`trellis-node action-node${statusClass}${selectedClass}${disabledClass}${hasAiHandles ? ' has-ai-handles' : ''}`}>
      {/* Main input handles (left) */}
      {mainInputs.map((input, index) => (
        <Handle
          key={`input-${input.name}`}
          type="target"
          position={Position.Left}
          id={encodeHandleId(input.type, index)}
          style={{ top: `${((index + 1) / (mainInputs.length + 1)) * 100}%` }}
          className="trellis-handle"
        />
      ))}

      {/* AI output handles (top) — for sub-nodes like Chat Model */}
      {aiOutputs.length > 0 && (
        <div className="ai-handles-top">
          {aiOutputs.map((output, index) => {
            const style = AI_HANDLE_STYLES[output.type] || { color: 'hsl(0, 0%, 50%)', label: output.displayName || output.name };
            return (
              <div key={`ai-out-${output.name}`} className="ai-handle-wrapper">
                <Handle
                  type="source"
                  position={Position.Top}
                  id={encodeHandleId(output.type, index)}
                  className="trellis-handle ai-handle"
                  style={{ background: style.color, position: 'relative', transform: 'none', left: 'auto', top: 'auto' }}
                  onClick={(e) => {
                    e.stopPropagation();
                    data.onOutputHandleDoubleClick?.(encodeHandleId(output.type, index));
                  }}
                />
                <span className="ai-handle-label" style={{ color: style.color }}>{style.label}</span>
              </div>
            );
          })}
        </div>
      )}

      <div className="node-header">
        <div className="node-icon">
          {icon ? <NodeIcon name={icon} /> : (
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="3" />
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

      {/* Main output handles (right) */}
      {mainOutputs.map((output, index) => (
        <Handle
          key={`output-${output.name}`}
          type="source"
          position={Position.Right}
          id={encodeHandleId(output.type, index)}
          style={{ top: `${((index + 1) / (mainOutputs.length + 1)) * 100}%` }}
          className="trellis-handle"
          onClick={(e) => {
            e.stopPropagation();
            data.onOutputHandleDoubleClick?.(encodeHandleId(output.type, index));
          }}
        />
      ))}

      {/* AI input handles (bottom) — for parent nodes like Agent */}
      {aiInputs.length > 0 && (
        <div className="ai-handles-bottom">
          {aiInputs.map((input, index) => {
            const style = AI_HANDLE_STYLES[input.type] || { color: 'hsl(0, 0%, 50%)', label: input.displayName || input.name };
            return (
              <div key={`ai-in-${input.name}`} className="ai-handle-wrapper">
                <Handle
                  type="target"
                  position={Position.Bottom}
                  id={encodeHandleId(input.type, index)}
                  className="trellis-handle ai-handle"
                  style={{ background: style.color, position: 'relative', transform: 'none', left: 'auto', top: 'auto' }}
                  onClick={(e) => {
                    e.stopPropagation();
                    data.onOutputHandleDoubleClick?.(encodeHandleId(input.type, index));
                  }}
                />
                <span className="ai-handle-label" style={{ color: style.color }}>{style.label}</span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );

  if (data.readOnly) {
    return nodeContent;
  }

  return (
    <div
      className="node-proximity-wrapper"
      style={{ padding: PROXIMITY_PX, margin: -PROXIMITY_PX }}
      onMouseEnter={onProximityEnter}
      onMouseLeave={onProximityLeave}
    >
      <NodeActionToolbar nodeId={id} selected={!!selected} nearby={nearby} disabled={data.disabled} />
      {nodeContent}
    </div>
  );
});

TrellisNode.displayName = 'TrellisNode';
export default TrellisNode;
