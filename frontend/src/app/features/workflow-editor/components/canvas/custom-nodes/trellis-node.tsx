import React, { memo, useState, useCallback } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { NodeIcon } from './node-icon-map';
import NodeActionToolbar from './node-action-toolbar';

interface TrellisNodeData {
  label: string;
  nodeType?: string;
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
  isPinned?: boolean;
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
  const displayName = data.label || typeDesc?.displayName || 'Unknown Node';
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
  const isIconOnly = aiInputs.length === 0;

  // Grow node height when there are many output handles so they don't overlap
  const MIN_HANDLE_SPACING = 26;
  const handleCount = Math.max(mainOutputs.length, mainInputs.length);
  const baseHeight = isIconOnly ? 68 : 52;
  const dynamicHeight = handleCount > 2 ? MIN_HANDLE_SPACING * (handleCount + 1) : undefined;
  const nodeStyle = dynamicHeight && dynamicHeight > baseHeight
    ? { height: dynamicHeight, minHeight: dynamicHeight } : undefined;

  const nodeContent = (
    <div className={`trellis-node action-node${statusClass}${selectedClass}${disabledClass}${hasAiHandles ? ' has-ai-handles' : ''}${isIconOnly ? ' icon-only' : ''}`} style={nodeStyle}>
      {/* Main input handles (left) */}
      {mainInputs.map((input, index) => (
        <React.Fragment key={`input-${input.name}`}>
          <Handle
            type="target"
            position={Position.Left}
            id={encodeHandleId(input.type, index)}
            style={{ top: `${((index + 1) / (mainInputs.length + 1)) * 100}%` }}
            className="trellis-handle"
          />
          {(data.nodeType === 'compareDatasets' || data.nodeType === 'merge') && (
            <span
              className="input-handle-label"
              style={{ top: `${((index + 1) / (mainInputs.length + 1)) * 100}%` }}
            >
              {input.displayName || input.name}
            </span>
          )}
        </React.Fragment>
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
          {icon ? <NodeIcon name={icon} size={isIconOnly ? 32 : 16} /> : (
            <svg viewBox="0 0 24 24" width={isIconOnly ? 32 : 16} height={isIconOnly ? 32 : 16} fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="3" />
            </svg>
          )}
        </div>
        {!isIconOnly && (
          <div className="node-title">
            <div className="node-name">{displayName}</div>
            {subtitle && <div className="node-subtitle">{subtitle}</div>}
          </div>
        )}
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

      {data.isPinned && (
        <div className="node-pin-badge" title="Output data is pinned">
          <svg viewBox="0 0 24 24" width="10" height="10" fill="currentColor" stroke="none">
            <path d="M12 2c-.5 0-1 .19-1.41.59L8.59 4.59 4.59 8.59C4.19 8.99 4 9.5 4 10c0 1.1.9 2 2 2h3l-3 8h2l3-8v6h2v-6l3 8h2l-3-8h3c1.1 0 2-.9 2-2 0-.5-.19-1-.59-1.41L16.41 4.59 14.41 2.59C14 2.19 13.5 2 13 2h-1z"/>
          </svg>
        </div>
      )}

      {/* Main output handles (right) */}
      {mainOutputs.map((output, index) => (
        <React.Fragment key={`output-${output.name}`}>
          <Handle
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
          {(data.nodeType === 'if' && mainOutputs.length >= 2 || data.nodeType === 'switch' || data.nodeType === 'loopOverItems' || data.nodeType === 'compareDatasets' || data.nodeType === 'removeDuplicates' || data.nodeType === 'guardrails') && (
            <span
              className="output-handle-label"
              style={{ top: `${((index + 1) / (mainOutputs.length + 1)) * 100}%` }}
            >
              {output.displayName || output.name}
            </span>
          )}
        </React.Fragment>
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

  const wrappedContent = isIconOnly ? (
    <div className="node-with-label">
      {nodeContent}
      <div className="node-label-below">{displayName}</div>
    </div>
  ) : nodeContent;

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
      <NodeActionToolbar nodeId={id} selected={!!selected} nearby={nearby} disabled={data.disabled} isSubNode={mainInputs.length === 0 && mainOutputs.length === 0} />
      {wrappedContent}
    </div>
  );
});

TrellisNode.displayName = 'TrellisNode';
export default TrellisNode;
