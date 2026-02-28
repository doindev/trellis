import React, { memo, useMemo } from 'react';
import { type NodeProps } from '@xyflow/react';
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
  [key: string]: unknown;
}

const COLOR_MAP: Record<string, { bg: string; border: string; text: string }> = {
  yellow: { bg: 'hsl(48, 96%, 89%)', border: 'hsl(48, 60%, 70%)', text: 'hsl(40, 30%, 20%)' },
  blue:   { bg: 'hsl(210, 80%, 90%)', border: 'hsl(210, 50%, 72%)', text: 'hsl(210, 30%, 20%)' },
  green:  { bg: 'hsl(140, 60%, 88%)', border: 'hsl(140, 40%, 68%)', text: 'hsl(140, 30%, 18%)' },
  pink:   { bg: 'hsl(340, 80%, 92%)', border: 'hsl(340, 50%, 75%)', text: 'hsl(340, 30%, 22%)' },
  purple: { bg: 'hsl(270, 60%, 92%)', border: 'hsl(270, 40%, 74%)', text: 'hsl(270, 25%, 22%)' },
};

// Configure marked for safe inline rendering
marked.setOptions({ breaks: true, gfm: true });

function StickyNoteNode({ data, selected }: NodeProps) {
  const d = data as StickyNoteData;
  const params = d.nodeParameters || {};
  const content = params.content || '';
  const colorKey = params.color || 'yellow';
  const width = params.width || 200;
  const height = params.height || 150;
  const defaultColor = COLOR_MAP['yellow'];
  const colors = COLOR_MAP[colorKey] || defaultColor;

  const html = useMemo(() => {
    if (!content) return '';
    return marked.parse(content) as string;
  }, [content]);

  return (
    <div
      className={`sticky-note-node${selected ? ' selected' : ''}`}
      style={{
        width,
        height,
        backgroundColor: colors.bg,
        borderColor: selected ? 'hsl(247, 49%, 53%)' : colors.border,
        color: colors.text,
      }}
    >
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
