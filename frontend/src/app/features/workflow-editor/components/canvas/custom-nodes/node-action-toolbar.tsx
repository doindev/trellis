import React, { memo, useState, useRef, useEffect, useContext } from 'react';
import { NodeToolbar, Position } from '@xyflow/react';
import { CanvasActionsContext } from '../cwc-canvas';

interface NodeActionToolbarProps {
  nodeId: string;
  selected: boolean;
  nearby: boolean;
  disabled?: boolean;
  isSubNode?: boolean;
}

function NodeActionToolbar({ nodeId, selected, nearby, disabled, isSubNode }: NodeActionToolbarProps) {
  const ctx = useContext(CanvasActionsContext);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const isVisible = nearby || selected;

  // Close menu on outside click
  useEffect(() => {
    if (!menuOpen) return;
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [menuOpen]);

  // Close menu when toolbar hides
  useEffect(() => {
    if (!isVisible) setMenuOpen(false);
  }, [isVisible]);

  const ensureSelected = () => {
    if (!selected) {
      ctx.selectNode(nodeId);
    }
  };

  const stop = (fn: () => void) => (e: React.MouseEvent) => {
    e.stopPropagation();
    ensureSelected();
    fn();
  };

  const menuAction = (fn: () => void) => (e: React.MouseEvent) => {
    e.stopPropagation();
    ensureSelected();
    setMenuOpen(false);
    fn();
  };

  return (
    <NodeToolbar isVisible={isVisible} position={Position.Top} align="center" offset={8}>
      <div className="node-action-toolbar" onClick={(e) => { e.stopPropagation(); ensureSelected(); }}>
        {!isSubNode && (
          <button className="nat-btn" title="Execute from this node" onClick={stop(() => ctx.executeFromNode(nodeId))}>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" stroke="none">
              <polygon points="6 3 20 12 6 21 6 3" />
            </svg>
          </button>
        )}
        <button
          className={`nat-btn${disabled ? ' nat-active' : ''}`}
          title={disabled ? 'Enable node' : 'Disable node'}
          onClick={stop(() => ctx.toggleDisabled(nodeId))}
        >
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M18.36 6.64a9 9 0 1 1-12.73 0" />
            <line x1="12" y1="2" x2="12" y2="12" />
          </svg>
        </button>
        <button className="nat-btn nat-delete" title="Delete node" onClick={stop(() => ctx.deleteNode(nodeId))}>
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="3 6 5 6 21 6" />
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          </svg>
        </button>
        <div className="nat-more" ref={menuRef}>
          <button
            className={`nat-btn${menuOpen ? ' nat-active' : ''}`}
            title="More actions"
            onClick={stop(() => setMenuOpen(!menuOpen))}
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" stroke="none">
              <circle cx="12" cy="5" r="1.5" />
              <circle cx="12" cy="12" r="1.5" />
              <circle cx="12" cy="19" r="1.5" />
            </svg>
          </button>
          {menuOpen && (
            <div className="nat-menu">
              <button className="nat-menu-item" onClick={menuAction(() => ctx.openNode(nodeId))}>
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
                  <polyline points="15 3 21 3 21 9" />
                  <line x1="10" y1="14" x2="21" y2="3" />
                </svg>
                Open
              </button>
              <button className="nat-menu-item" onClick={menuAction(() => ctx.renameNode(nodeId))}>
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                </svg>
                Rename
              </button>
              <button className="nat-menu-item" onClick={menuAction(() => ctx.duplicateNode(nodeId))}>
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect width="14" height="14" x="8" y="8" rx="2" ry="2" />
                  <path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" />
                </svg>
                Duplicate
              </button>
              <button className="nat-menu-item" onClick={menuAction(() => ctx.copyNode(nodeId))}>
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
                  <rect width="8" height="4" x="8" y="2" rx="1" ry="1" />
                </svg>
                Copy
              </button>
              <div className="nat-menu-divider" />
              <button className="nat-menu-item" onClick={menuAction(() => ctx.toggleDisabled(nodeId))}>
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M18.36 6.64a9 9 0 1 1-12.73 0" />
                  <line x1="12" y1="2" x2="12" y2="12" />
                </svg>
                {disabled ? 'Activate' : 'Deactivate'}
              </button>
              <div className="nat-menu-divider" />
              <button className="nat-menu-item" onClick={menuAction(() => ctx.selectAll())}>
                Select All
              </button>
              <button className="nat-menu-item" onClick={menuAction(() => ctx.deselectAll())}>
                Deselect All
              </button>
            </div>
          )}
        </div>
      </div>
    </NodeToolbar>
  );
}

export default memo(NodeActionToolbar);
