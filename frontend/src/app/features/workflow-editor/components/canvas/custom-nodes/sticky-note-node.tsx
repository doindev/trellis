import React, { memo, useCallback, useMemo, useState } from 'react';
import { type NodeProps, NodeResizeControl } from '@xyflow/react';
import { marked } from 'marked';

interface StickyNoteData {
  label: string;
  nodeParameters?: {
    content?: string;
    color?: string;
    width?: number;
    height?: number;
  };
  readOnly?: boolean;
  onResize?: (nodeId: string, width: number, height: number) => void;
  [key: string]: unknown;
}

const COLOR_MAP: Record<string, { bg: string; border: string; text: string }> = {
  yellow: { bg: 'hsl(48, 96%, 89%)', border: 'hsl(48, 60%, 70%)', text: 'hsl(40, 30%, 20%)' },
  blue:   { bg: 'hsl(210, 80%, 90%)', border: 'hsl(210, 50%, 72%)', text: 'hsl(210, 30%, 20%)' },
  green:  { bg: 'hsl(140, 60%, 88%)', border: 'hsl(140, 40%, 68%)', text: 'hsl(140, 30%, 18%)' },
  pink:   { bg: 'hsl(340, 80%, 92%)', border: 'hsl(340, 50%, 75%)', text: 'hsl(340, 30%, 22%)' },
  purple: { bg: 'hsl(270, 60%, 92%)', border: 'hsl(270, 40%, 74%)', text: 'hsl(270, 25%, 22%)' },
};

const MIN_WIDTH = 120;
const MIN_HEIGHT = 80;

// Configure marked for safe inline rendering
marked.setOptions({ breaks: true, gfm: true });

function StickyNoteNode({ id, data, selected }: NodeProps) {
  const d = data as StickyNoteData;
  const params = d.nodeParameters || {};
  const content = params.content || '';
  const colorKey = params.color || 'yellow';
  const width = params.width || 200;
  const height = params.height || 150;
  const defaultColor = COLOR_MAP['yellow'];
  const colors = COLOR_MAP[colorKey] || defaultColor;
  const readOnly = d.readOnly;

  // Local state for live resize — null when not dragging
  const [liveSize, setLiveSize] = useState<{ w: number; h: number } | null>(null);

  const html = useMemo(() => {
    if (!content) return '';
    return marked.parse(content) as string;
  }, [content]);

  const onResizeDrag = useCallback(
    (_event: unknown, params: { width: number; height: number }) => {
      setLiveSize({ w: params.width, h: params.height });
    },
    []
  );

  const onResizeEnd = useCallback(
    (_event: unknown, params: { width: number; height: number }) => {
      setLiveSize(null);
      d.onResize?.(id, Math.round(params.width), Math.round(params.height));
    },
    [id, d.onResize]
  );

  const displayWidth = liveSize ? liveSize.w : width;
  const displayHeight = liveSize ? liveSize.h : height;

  return (
    <div
      className={`sticky-note-node${selected ? ' selected' : ''}`}
      style={{
        width: displayWidth,
        height: displayHeight,
        backgroundColor: colors.bg,
        borderColor: selected ? 'hsl(247, 49%, 53%)' : colors.border,
        color: colors.text,
      }}
    >
      {selected && !readOnly && (
        <NodeResizeControl
          minWidth={MIN_WIDTH}
          minHeight={MIN_HEIGHT}
          position="bottom-right"
          style={{ background: 'transparent', border: 'none' }}
          onResize={onResizeDrag}
          onResizeEnd={onResizeEnd}
        >
          <div className="sticky-note-resize-handle" />
        </NodeResizeControl>
      )}
      {html ? (
        <div
          className="sticky-note-content"
          dangerouslySetInnerHTML={{ __html: html }}
        />
      ) : (
        <div className="sticky-note-content">
          <span className="sticky-note-placeholder">Double-click to edit...</span>
        </div>
      )}
    </div>
  );
}

export default memo(StickyNoteNode);
