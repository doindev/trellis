import React, { useCallback, useRef, useMemo, useState, useEffect, createContext } from 'react';
import {
  ReactFlow,
  Background,
  MiniMap,
  Panel,
  useNodesState,
  useEdgesState,
  useReactFlow,
  useViewport,
  ReactFlowProvider,
  addEdge,
  BackgroundVariant,
  MarkerType,
  ConnectionLineType,
  type Connection,
  type Node,
  type Edge,
  type NodeTypes,
  type EdgeTypes,
} from '@xyflow/react';
import TrellisNode from './custom-nodes/trellis-node';
import TrellisTriggerNode from './custom-nodes/trellis-trigger-node';
import StickyNoteNode from './custom-nodes/sticky-note-node';
import TrellisEdge from './custom-edges/trellis-edge';
import { calculateLayout } from './canvas-layout';

export interface CanvasActions {
  singleSelectedId: string | null;
  executeFromNode: (nodeId: string) => void;
  toggleDisabled: (nodeId: string) => void;
  deleteNode: (nodeId: string) => void;
  openNode: (nodeId: string) => void;
  duplicateNode: (nodeId: string) => void;
  copyNode: (nodeId: string) => void;
  renameNode: (nodeId: string) => void;
  selectNode: (nodeId: string) => void;
  selectAll: () => void;
  deselectAll: () => void;
  deleteEdge?: (edgeId: string) => void;
  insertNodeOnEdge?: (edgeId: string, source: string, target: string, sourceHandle: string, targetHandle: string) => void;
}

export const CanvasActionsContext = createContext<CanvasActions>({
  singleSelectedId: null,
  executeFromNode: () => {},
  toggleDisabled: () => {},
  deleteNode: () => {},
  openNode: () => {},
  duplicateNode: () => {},
  copyNode: () => {},
  renameNode: () => {},
  selectNode: () => {},
  selectAll: () => {},
  deselectAll: () => {},
});

export interface TrellisCanvasProps {
  initialNodes: Node[];
  initialEdges: Edge[];
  isExecuting?: boolean;
  readOnly?: boolean;
  drawerOffset?: number;
  onNodeClick?: (nodeId: string) => void;
  onNodeDoubleClick?: (nodeId: string) => void;
  onPaneClick?: () => void;
  onNodeAdd?: (type: string, position: { x: number; y: number }, displayName: string, version: number) => void;
  onNodeDelete?: (nodeId: string) => void;
  onNodesChange?: (positions: Record<string, [number, number]>) => void;
  onConnectionsChange?: (connections: any) => void;
  onExecute?: () => void;
  onStopExecution?: () => void;
  onViewportHelperReady?: (helper: { getViewportCenter: () => { x: number; y: number } }) => void;
  onOutputHandleDoubleClick?: (nodeId: string, handleId: string) => void;
  onToggleNodeDisabled?: (nodeId: string) => void;
  onDuplicateNode?: (nodeId: string) => void;
  onExecuteFromNode?: (nodeId: string) => void;
  onCopyNode?: (nodeId: string) => void;
  onInsertNodeOnEdge?: (edgeInfo: { sourceNodeId: string; targetNodeId: string; sourceHandle: string; targetHandle: string }) => void;
  triggerCleanUp?: number;
}

const GRID_SIZE = 16;
const MIN_ZOOM = 0.1;
const MAX_ZOOM = 4;
const CONNECTION_RADIUS = 60;

const nodeTypes: NodeTypes = {
  trellisNode: TrellisNode as any,
  trellisTriggerNode: TrellisTriggerNode as any,
  stickyNoteNode: StickyNoteNode as any,
};

const edgeTypes: EdgeTypes = {
  trellisEdge: TrellisEdge as any,
};

function TrellisCanvasInner({
  initialNodes,
  initialEdges,
  isExecuting,
  readOnly,
  drawerOffset,
  onNodeClick,
  onNodeDoubleClick,
  onPaneClick,
  onNodeAdd,
  onNodeDelete,
  onNodesChange: onNodesPositionChange,
  onConnectionsChange,
  onExecute,
  onStopExecution,
  onViewportHelperReady,
  onOutputHandleDoubleClick,
  onToggleNodeDisabled,
  onDuplicateNode,
  onExecuteFromNode,
  onCopyNode,
  onInsertNodeOnEdge,
  triggerCleanUp,
}: TrellisCanvasProps) {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const { fitView, getViewport, setViewport, zoomIn, zoomOut, screenToFlowPosition } = useReactFlow();
  const { zoom } = useViewport();
  const helperExposed = useRef(false);
  const outputHandleDoubleClickRef = useRef(onOutputHandleDoubleClick);
  outputHandleDoubleClickRef.current = onOutputHandleDoubleClick;
  const onNodeDeleteRef = useRef(onNodeDelete);
  onNodeDeleteRef.current = onNodeDelete;
  const onNodeDoubleClickRef = useRef(onNodeDoubleClick);
  onNodeDoubleClickRef.current = onNodeDoubleClick;
  const onToggleNodeDisabledRef = useRef(onToggleNodeDisabled);
  onToggleNodeDisabledRef.current = onToggleNodeDisabled;
  const onDuplicateNodeRef = useRef(onDuplicateNode);
  onDuplicateNodeRef.current = onDuplicateNode;
  const onExecuteFromNodeRef = useRef(onExecuteFromNode);
  onExecuteFromNodeRef.current = onExecuteFromNode;
  const onCopyNodeRef = useRef(onCopyNode);
  onCopyNodeRef.current = onCopyNode;
  const onInsertNodeOnEdgeRef = useRef(onInsertNodeOnEdge);
  onInsertNodeOnEdgeRef.current = onInsertNodeOnEdge;
  const onConnectionsChangeRef = useRef(onConnectionsChange);
  onConnectionsChangeRef.current = onConnectionsChange;

  const [nodes, setNodes, handleNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, handleEdgesChange] = useEdgesState(initialEdges);
  const [ready, setReady] = useState(false);
  const [singleSelectedId, setSingleSelectedId] = useState<string | null>(null);
  const initialFitDone = useRef(false);

  // Execute button: trigger node tracking and dropdown state
  const [selectedTriggerId, setSelectedTriggerId] = useState<string | null>(null);
  const [executeDropdownOpen, setExecuteDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const dropdownHideTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Minimap: hidden by default, shown while panning or hovering the minimap
  const [isMinimapVisible, setIsMinimapVisible] = useState(false);
  const minimapHideTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showMinimap = useCallback(() => {
    if (minimapHideTimeout.current) {
      clearTimeout(minimapHideTimeout.current);
      minimapHideTimeout.current = null;
    }
    setIsMinimapVisible(true);
  }, []);

  const hideMinimap = useCallback(() => {
    minimapHideTimeout.current = setTimeout(() => {
      setIsMinimapVisible(false);
    }, 1000);
  }, []);

  // Derive trigger nodes from React Flow state
  const triggerNodes = useMemo(() =>
    nodes
      .filter(n => n.type === 'trellisTriggerNode')
      .map(n => ({ id: n.id, name: (n.data as any).label as string })),
    [nodes]
  );

  // Resolve active trigger (fallback to first if selected no longer exists)
  const activeTriggerId = useMemo(() => {
    if (triggerNodes.length === 0) return null;
    if (selectedTriggerId && triggerNodes.some(t => t.id === selectedTriggerId)) {
      return selectedTriggerId;
    }
    return triggerNodes[0].id;
  }, [triggerNodes, selectedTriggerId]);

  const activeTriggerName = useMemo(() =>
    triggerNodes.find(t => t.id === activeTriggerId)?.name || '',
    [triggerNodes, activeTriggerId]
  );

  // Close execute dropdown on outside click or after mouse leaves
  useEffect(() => {
    if (!executeDropdownOpen) return;
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as HTMLElement)) {
        setExecuteDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => {
      document.removeEventListener('mousedown', handler);
      if (dropdownHideTimeout.current) {
        clearTimeout(dropdownHideTimeout.current);
        dropdownHideTimeout.current = null;
      }
    };
  }, [executeDropdownOpen]);

  const onDropdownMouseEnter = useCallback(() => {
    if (dropdownHideTimeout.current) {
      clearTimeout(dropdownHideTimeout.current);
      dropdownHideTimeout.current = null;
    }
  }, []);

  const onDropdownMouseLeave = useCallback(() => {
    dropdownHideTimeout.current = setTimeout(() => {
      setExecuteDropdownOpen(false);
    }, 600);
  }, []);

  // Track single node selection
  const onSelectionChange = useCallback(({ nodes: selectedNodes }: { nodes: Node[] }) => {
    setSingleSelectedId(selectedNodes.length === 1 ? selectedNodes[0].id : null);
  }, []);

  // Sync nodes from Angular — only update nodes whose data actually changed.
  // Merges with React Flow's internal node state (measured, width, height) to avoid flicker.
  useEffect(() => {
    setNodes(prevNodes => {
      const prevMap = new Map(prevNodes.map(n => [n.id, n]));
      let changed = false;

      const result = initialNodes.map(newNode => {
        const prev = prevMap.get(newNode.id);
        const newData = newNode.data as any;
        const prevData = prev?.data as any;

        // Brand-new node — no previous state to preserve
        if (!prev) {
          changed = true;
          return {
            ...newNode,
            data: {
              ...newData,
              onOutputHandleDoubleClick: (handleId: string) => {
                outputHandleDoubleClickRef.current?.(newNode.id, handleId);
              },
            },
          };
        }

        // Check if anything meaningful changed for this node
        const dataChanged =
             prev.type !== newNode.type
          || prev.position?.x !== newNode.position?.x
          || prev.position?.y !== newNode.position?.y
          || prev.selected !== newNode.selected
          || prevData?.executionStatus !== newData?.executionStatus
          || prevData?.itemCount !== newData?.itemCount
          || prevData?.label !== newData?.label
          || prevData?.disabled !== newData?.disabled
          || prevData?.isPinned !== newData?.isPinned
          || (newNode.type === 'stickyNoteNode' && prevData?.nodeParameters !== newData?.nodeParameters);

        // Reuse previous node object if nothing changed (preserves referential equality for memo)
        if (!dataChanged) return prev;

        changed = true;
        // Merge: keep React Flow internals (measured, width, height) from prev,
        // override with Angular's updated fields
        return {
          ...prev,
          ...newNode,
          data: {
            ...newData,
            onOutputHandleDoubleClick: prevData?.onOutputHandleDoubleClick
              || ((handleId: string) => {
                outputHandleDoubleClickRef.current?.(newNode.id, handleId);
              }),
          },
        };
      });

      // Check for removed nodes
      if (!changed && prevNodes.length !== initialNodes.length) {
        changed = true;
      }

      return changed ? result : prevNodes;
    });
  }, [initialNodes, setNodes]);

  // Sync edges — only update edges whose data actually changed
  useEffect(() => {
    setEdges(prevEdges => {
      const prevMap = new Map(prevEdges.map(e => [e.id, e]));
      let changed = false;

      const result = initialEdges.map(newEdge => {
        const prev = prevMap.get(newEdge.id);
        const newData = newEdge.data as any;
        const prevData = prev?.data as any;

        if (!prev) {
          changed = true;
          return { ...newEdge, data: { ...newData, readOnly } };
        }

        const dataChanged =
             prevData?.animated !== newData?.animated
          || prevData?.status !== newData?.status
          || prevData?.readOnly !== (newData?.readOnly ?? readOnly);

        if (!dataChanged) return prev;

        changed = true;
        // Merge with previous edge to preserve React Flow internals
        return { ...prev, ...newEdge, data: { ...newData, readOnly } };
      });

      if (!changed && prevEdges.length !== initialEdges.length) {
        changed = true;
      }

      return changed ? result : prevEdges;
    });
  }, [initialEdges, setEdges, readOnly]);

  // Mark ready once on initial load so the canvas fades in
  useEffect(() => {
    if (!initialFitDone.current) {
      initialFitDone.current = true;
      requestAnimationFrame(() => {
        setReady(true);
      });
    }
  }, [initialNodes]);

  // Expose viewport helper to Angular wrapper once
  useEffect(() => {
    if (!helperExposed.current && reactFlowWrapper.current && onViewportHelperReady) {
      helperExposed.current = true;
      onViewportHelperReady({
        getViewportCenter: () => {
          const el = reactFlowWrapper.current;
          if (!el) return { x: 0, y: 0 };
          const rect = el.getBoundingClientRect();
          return screenToFlowPosition({
            x: rect.left + rect.width / 2,
            y: rect.top + rect.height / 2,
          });
        },
      });
    }
  }, [onViewportHelperReady, screenToFlowPosition]);

  // Decode a handle ID like "main:0" or "ai_languageModel:0" into { type, index }
  const decodeHandleId = useCallback((handleId: string | null): { type: string; index: number } => {
    if (!handleId) return { type: 'main', index: 0 };
    const sep = handleId.lastIndexOf(':');
    if (sep < 0) return { type: handleId, index: 0 };
    return { type: handleId.substring(0, sep), index: parseInt(handleId.substring(sep + 1), 10) || 0 };
  }, []);

  // Build connections map from edges (shared helper)
  const buildConnectionsMap = useCallback((edgeList: Edge[]) => {
    const connectionsMap: Record<string, any> = {};
    edgeList.forEach((e: any) => {
      const src = decodeHandleId(e.sourceHandle);
      const tgt = decodeHandleId(e.targetHandle);
      const cType = src.type || 'main';
      if (!connectionsMap[e.source]) {
        connectionsMap[e.source] = {};
      }
      if (!connectionsMap[e.source][cType]) {
        connectionsMap[e.source][cType] = [[]];
      }
      while (connectionsMap[e.source][cType].length <= src.index) {
        connectionsMap[e.source][cType].push([]);
      }
      connectionsMap[e.source][cType][src.index].push({
        node: e.target,
        type: cType,
        index: tgt.index,
      });
    });
    return connectionsMap;
  }, [decodeHandleId]);

  // Context value for node action toolbar
  const canvasActionsValue = useMemo<CanvasActions>(() => ({
    singleSelectedId,
    executeFromNode: (nodeId) => onExecuteFromNodeRef.current?.(nodeId),
    toggleDisabled: (nodeId) => onToggleNodeDisabledRef.current?.(nodeId),
    deleteNode: (nodeId) => {
      if (window.confirm('Are you sure you want to delete this node?')) {
        onNodeDeleteRef.current?.(nodeId);
      }
    },
    openNode: (nodeId) => onNodeDoubleClickRef.current?.(nodeId),
    duplicateNode: (nodeId) => onDuplicateNodeRef.current?.(nodeId),
    copyNode: (nodeId) => onCopyNodeRef.current?.(nodeId),
    renameNode: (nodeId) => onNodeDoubleClickRef.current?.(nodeId),
    selectNode: (nodeId) => {
      setNodes(nds => nds.map(n => ({ ...n, selected: n.id === nodeId })));
      onNodeClick?.(nodeId);
    },
    selectAll: () => setNodes(nds => nds.map(n => ({ ...n, selected: true }))),
    deselectAll: () => setNodes(nds => nds.map(n => ({ ...n, selected: false }))),
    deleteEdge: (edgeId) => {
      setEdges(eds => {
        const remaining = eds.filter(e => e.id !== edgeId);
        onConnectionsChangeRef.current?.(buildConnectionsMap(remaining));
        return remaining;
      });
    },
    insertNodeOnEdge: (edgeId, source, target, sourceHandle, targetHandle) => {
      onInsertNodeOnEdgeRef.current?.({
        sourceNodeId: source,
        targetNodeId: target,
        sourceHandle: sourceHandle,
        targetHandle: targetHandle,
      });
    },
  }), [singleSelectedId, setNodes, setEdges, buildConnectionsMap, onNodeClick]);

  const isValidConnection = useCallback((connection: Edge | Connection): boolean => {
    const source = decodeHandleId(connection.sourceHandle as string | null);
    const target = decodeHandleId(connection.targetHandle as string | null);
    return source.type === target.type;
  }, [decodeHandleId]);

  const onConnect = useCallback(
    (connection: Connection) => {
      const sourceInfo = decodeHandleId(connection.sourceHandle as string | null);
      const targetInfo = decodeHandleId(connection.targetHandle as string | null);
      // Only allow connections where types match
      if (sourceInfo.type !== targetInfo.type) return;

      const connectionType = sourceInfo.type;
      const isAi = connectionType.startsWith('ai_');

      setEdges((eds) => addEdge({
        ...connection,
        type: 'trellisEdge',
        data: { connectionType, isAi },
      }, eds));

      if (onConnectionsChange) {
        const newEdges = [...edges, {
          ...connection,
          type: 'trellisEdge',
          id: `e-${connection.source}-${connectionType}-${connection.target}`,
        }];
        onConnectionsChange(buildConnectionsMap(newEdges));
      }
    },
    [edges, setEdges, onConnectionsChange, decodeHandleId, buildConnectionsMap]
  );

  const onNodeClickHandler = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      onNodeClick?.(node.id);
    },
    [onNodeClick]
  );

  const onNodeDoubleClickHandler = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      onNodeDoubleClick?.(node.id);
    },
    [onNodeDoubleClick]
  );

  const onPaneClickHandler = useCallback(() => {
    onPaneClick?.();
  }, [onPaneClick]);

  const snapToGrid = (val: number) => Math.round(val / GRID_SIZE) * GRID_SIZE;

  const onNodeDragStop = useCallback(
    (_event: React.MouseEvent, _node: Node, draggedNodes: Node[]) => {
      const positions: Record<string, [number, number]> = {};
      draggedNodes.forEach((n) => {
        positions[n.id] = [snapToGrid(n.position.x), snapToGrid(n.position.y)];
      });
      // Snap the nodes visually in React Flow state
      setNodes((nds) =>
        nds.map((n) =>
          positions[n.id]
            ? { ...n, position: { x: positions[n.id][0], y: positions[n.id][1] } }
            : n
        )
      );
      onNodesPositionChange?.(positions);
    },
    [onNodesPositionChange, setNodes]
  );

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      const rawData = event.dataTransfer.getData('application/trellis-node-type');
      if (!rawData) return;

      try {
        const nodeData = JSON.parse(rawData);
        // Use screenToFlowPosition for accurate drop coordinates at any zoom/pan
        const position = screenToFlowPosition({
          x: event.clientX,
          y: event.clientY,
        });

        onNodeAdd?.(nodeData.type, { x: position.x - 75, y: position.y - 25 }, nodeData.displayName, nodeData.version);
      } catch (e) {
        console.error('Failed to parse dropped node data', e);
      }
    },
    [onNodeAdd, screenToFlowPosition]
  );

  const onCleanUp = useCallback(() => {
    const positions = calculateLayout(nodes, edges);
    if (Object.keys(positions).length === 0) return;

    // Apply new positions to React Flow state
    setNodes((nds) =>
      nds.map((n) =>
        positions[n.id]
          ? { ...n, position: { x: positions[n.id][0], y: positions[n.id][1] } }
          : n
      )
    );

    // Notify Angular of the position changes
    onNodesPositionChange?.(positions);

    // Fit viewport after layout settles, adjusting for drawer offset
    setTimeout(() => {
      fitView({ padding: 0.2, duration: 300 });
      if (drawerOffset && drawerOffset > 0) {
        // After fitView centers in the full canvas, shift up by half the drawer height
        // to center in the visible portion above the drawer
        setTimeout(() => {
          const vp = getViewport();
          setViewport({ x: vp.x, y: vp.y - drawerOffset / 2, zoom: vp.zoom }, { duration: 200 });
        }, 320);
      }
    }, 50);
  }, [nodes, edges, setNodes, onNodesPositionChange, fitView, drawerOffset, getViewport, setViewport]);

  // Allow Angular to trigger clean-up via prop change
  useEffect(() => {
    if (triggerCleanUp && triggerCleanUp > 0) {
      // Delay slightly to ensure nodes/edges are fully synced from props
      setTimeout(() => onCleanUp(), 100);
    }
  }, [triggerCleanUp]); // eslint-disable-line react-hooks/exhaustive-deps

  const onKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key === 'a') {
        event.preventDefault();
        setNodes((nds) => nds.map((n) => ({ ...n, selected: true })));
      } else if (!readOnly && (event.key === 'Delete' || event.key === 'Backspace')) {
        const selectedNodes = nodes.filter((n) => n.selected);
        selectedNodes.forEach((n) => onNodeDelete?.(n.id));
      } else if (event.key === '-' || event.key === '_') {
        event.preventDefault();
        zoomOut();
      } else if (event.key === '+' || event.key === '=') {
        event.preventDefault();
        zoomIn();
      }
    },
    [nodes, onNodeDelete, zoomIn, zoomOut, setNodes]
  );

  const defaultEdgeOptions = useMemo(
    () => ({
      type: 'trellisEdge',
      animated: false,
      markerEnd: {
        type: MarkerType.ArrowClosed,
        width: 16,
        height: 16,
        color: 'hsl(0, 0%, 30%)',
      },
    }),
    []
  );

  const connectionLineStyle = useMemo(
    () => ({
      stroke: 'hsl(0, 0%, 46%)',
      strokeWidth: 2,
    }),
    []
  );

  const proOptions = useMemo(() => ({ hideAttribution: true }), []);

  return (
    <CanvasActionsContext.Provider value={canvasActionsValue}>
      <div
        ref={reactFlowWrapper}
        style={{ width: '100%', height: '100%', opacity: ready ? 1 : 0, transition: 'opacity 300ms ease' }}
        onKeyDown={onKeyDown}
        tabIndex={0}
      >
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={handleNodesChange}
          onEdgesChange={handleEdgesChange}
          onConnect={readOnly ? undefined : onConnect}
          isValidConnection={isValidConnection}
          onNodeClick={onNodeClickHandler}
          onNodeDoubleClick={onNodeDoubleClickHandler}
          onPaneClick={onPaneClickHandler}
          onNodeDragStop={readOnly ? undefined : onNodeDragStop}
          onDragOver={readOnly ? undefined : onDragOver}
          onDrop={readOnly ? undefined : onDrop}
          onSelectionChange={onSelectionChange}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          defaultEdgeOptions={defaultEdgeOptions}
          connectionLineType={ConnectionLineType.SmoothStep}
          connectionLineStyle={connectionLineStyle}
          connectionRadius={CONNECTION_RADIUS}
          defaultViewport={{ x: 0, y: 0, zoom: 1.2 }}
          minZoom={MIN_ZOOM}
          maxZoom={MAX_ZOOM}
          onMoveStart={showMinimap}
          onMoveEnd={hideMinimap}
          panOnScroll
          deleteKeyCode={null}
          proOptions={proOptions}
          className="trellis-flow"
          nodesDraggable={!readOnly}
          nodesConnectable={!readOnly}
          edgesReconnectable={!readOnly}
        >
          <Background variant={BackgroundVariant.Dots} gap={GRID_SIZE} size={1} color="hsl(0, 0%, 22%)" />
          <MiniMap
            nodeColor={(n) => {
              if (n.type === 'trellisTriggerNode') return 'hsl(7, 100%, 68%)';
              return 'hsl(247, 49%, 53%)';
            }}
            maskColor="rgba(0,0,0,0.5)"
            style={{ background: 'hsl(0, 0%, 13%)', transition: 'bottom 0.2s ease', bottom: drawerOffset ? drawerOffset + 10 : undefined }}
            position="bottom-left"
            className={isMinimapVisible ? 'minimap-visible' : 'minimap-hidden'}
            onMouseEnter={showMinimap}
            onMouseLeave={hideMinimap}
          />
          <Panel position="bottom-left" className="canvas-controls-panel" style={drawerOffset ? { bottom: drawerOffset + 10, transition: 'bottom 0.2s ease' } : { transition: 'bottom 0.2s ease' }}>
            <button className="ctrl-btn" onClick={() => zoomOut()} title="Zoom out">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            </button>
            <span className="ctrl-zoom-label">{Math.round(zoom * 100)}%</span>
            <button className="ctrl-btn" onClick={() => zoomIn()} title="Zoom in">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            </button>
            <button className="ctrl-btn" onClick={() => {
              fitView({ padding: 0.2, duration: 200 });
              if (drawerOffset && drawerOffset > 0) {
                setTimeout(() => {
                  const vp = getViewport();
                  setViewport({ x: vp.x, y: vp.y - drawerOffset / 2, zoom: vp.zoom }, { duration: 150 });
                }, 220);
              }
            }} title="Fit view">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
              </svg>
            </button>
            {!readOnly && (
              <button className="ctrl-btn" onClick={onCleanUp} title="Clean up">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="m16 22-1-4" /><path d="M19 13.99a1 1 0 0 0 1-1V12a2 2 0 0 0-2-2h-3a1 1 0 0 1-1-1V4a2 2 0 0 0-4 0v5a1 1 0 0 1-1 1H6a2 2 0 0 0-2 2v.99a1 1 0 0 0 1 1" /><path d="M5 14h14l1.973 6.767A1 1 0 0 1 20 22H4a1 1 0 0 1-.973-1.233z" /><path d="m8 22 1-4" />
                </svg>
              </button>
            )}
          </Panel>

          {triggerNodes.length > 0 && !readOnly && (
            <div className="canvas-action-bar" style={drawerOffset ? { bottom: drawerOffset + 16 } : undefined}>
              {triggerNodes.length === 1 ? (
                <button
                  className="canvas-execute-btn"
                  onClick={() => onExecuteFromNode?.(triggerNodes[0].id)}
                  disabled={isExecuting}
                >
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" stroke="none">
                    <polygon points="6 3 20 12 6 21 6 3" />
                  </svg>
                  Execute
                </button>
              ) : (
                <div
                  className="canvas-execute-split"
                  ref={dropdownRef}
                  onMouseEnter={onDropdownMouseEnter}
                  onMouseLeave={onDropdownMouseLeave}
                >
                  <button
                    className="canvas-execute-btn canvas-execute-main"
                    onClick={() => activeTriggerId && onExecuteFromNode?.(activeTriggerId)}
                    disabled={isExecuting}
                  >
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" stroke="none">
                      <polygon points="6 3 20 12 6 21 6 3" />
                    </svg>
                    Execute from {activeTriggerName}
                  </button>
                  <button
                    className="canvas-execute-btn canvas-execute-chevron"
                    onClick={() => setExecuteDropdownOpen(!executeDropdownOpen)}
                    disabled={isExecuting}
                  >
                    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="6 9 12 15 18 9" />
                    </svg>
                  </button>
                  {executeDropdownOpen && (
                    <div className="canvas-execute-dropdown">
                      {triggerNodes.map(t => (
                        <button
                          key={t.id}
                          className={`canvas-execute-dropdown-item${t.id === activeTriggerId ? ' active' : ''}`}
                          onClick={() => {
                            setSelectedTriggerId(t.id);
                            setExecuteDropdownOpen(false);
                          }}
                        >
                          Execute from {t.name}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}
              {isExecuting && (
                <button className="canvas-stop-btn" onClick={onStopExecution}>
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" stroke="none">
                    <rect x="4" y="4" width="16" height="16" rx="2" />
                  </svg>
                </button>
              )}
            </div>
          )}
        </ReactFlow>
      </div>
    </CanvasActionsContext.Provider>
  );
}

export default function TrellisCanvas(props: TrellisCanvasProps) {
  return (
    <ReactFlowProvider>
      <TrellisCanvasInner {...props} />
    </ReactFlowProvider>
  );
}
