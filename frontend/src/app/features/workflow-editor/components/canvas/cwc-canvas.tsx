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
import CwcNode from './custom-nodes/cwc-node';
import CwcTriggerNode from './custom-nodes/cwc-trigger-node';
import StickyNoteNode from './custom-nodes/sticky-note-node';
import CwcEdge from './custom-edges/cwc-edge';
import { calculateLayout } from './canvas-layout';

export interface CanvasActions {
  singleSelectedId: string | null;
  crayonMode: boolean;
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
  crayonMode: false,
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

export interface CwcCanvasProps {
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
  onNodeResize?: (nodeId: string, width: number, height: number) => void;
  triggerCleanUp?: number;
}

const GRID_SIZE = 16;
const MIN_ZOOM = 0.1;
const MAX_ZOOM = 4;
const CONNECTION_RADIUS = 60;

const nodeTypes: NodeTypes = {
  cwcNode: CwcNode as any,
  cwcTriggerNode: CwcTriggerNode as any,
  stickyNoteNode: StickyNoteNode as any,
};

const edgeTypes: EdgeTypes = {
  cwcEdge: CwcEdge as any,
};

function CwcCanvasInner({
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
  onNodeResize,
  triggerCleanUp,
}: CwcCanvasProps) {
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
  const onNodeResizeRef = useRef(onNodeResize);
  onNodeResizeRef.current = onNodeResize;
  const onConnectionsChangeRef = useRef(onConnectionsChange);
  onConnectionsChangeRef.current = onConnectionsChange;

  const [nodes, setNodes, handleNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, handleEdgesChange] = useEdgesState(initialEdges);
  const [ready, setReady] = useState(false);
  const [singleSelectedId, setSingleSelectedId] = useState<string | null>(null);
  const [crayonMode, setCrayonMode] = useState(false);
  const crayonSeqIndex = useRef(0);
  const initialFitDone = useRef(false);

  // Execute button: trigger node tracking and dropdown state
  const [selectedTriggerId, setSelectedTriggerId] = useState<string | null>(null);
  const [executeDropdownOpen, setExecuteDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const dropdownHideTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Right-mouse-button + scroll wheel → zoom
  const rightMouseDown = useRef(false);
  const scrolledDuringRightHold = useRef(false);

  useEffect(() => {
    const el = reactFlowWrapper.current;
    if (!el) return;

    const onMouseDown = (e: MouseEvent) => {
      if (e.button === 2) {
        rightMouseDown.current = true;
        scrolledDuringRightHold.current = false;
      }
    };
    const onMouseUp = (e: MouseEvent) => {
      if (e.button === 2) rightMouseDown.current = false;
    };
    const onWheel = (e: WheelEvent) => {
      if (!rightMouseDown.current) return;
      e.preventDefault();
      e.stopPropagation();
      scrolledDuringRightHold.current = true;
      const delta = -e.deltaY;
      const vp = getViewport();
      const zoomFactor = 1 + (delta > 0 ? 0.08 : -0.08);
      const newZoom = Math.min(Math.max(vp.zoom * zoomFactor, MIN_ZOOM), MAX_ZOOM);
      // Zoom toward cursor position
      const rect = el.getBoundingClientRect();
      const cx = e.clientX - rect.left;
      const cy = e.clientY - rect.top;
      const scale = newZoom / vp.zoom;
      setViewport({
        x: cx - (cx - vp.x) * scale,
        y: cy - (cy - vp.y) * scale,
        zoom: newZoom,
      });
    };
    const onContextMenu = (e: MouseEvent) => {
      if (scrolledDuringRightHold.current) {
        e.preventDefault();
        scrolledDuringRightHold.current = false;
      }
    };

    el.addEventListener('mousedown', onMouseDown);
    el.addEventListener('mouseup', onMouseUp);
    el.addEventListener('wheel', onWheel, { capture: true, passive: false });
    el.addEventListener('contextmenu', onContextMenu);
    window.addEventListener('mouseup', onMouseUp);

    return () => {
      el.removeEventListener('mousedown', onMouseDown);
      el.removeEventListener('mouseup', onMouseUp);
      el.removeEventListener('wheel', onWheel, true);
      el.removeEventListener('contextmenu', onContextMenu);
      window.removeEventListener('mouseup', onMouseUp);
    };
  }, [getViewport, setViewport]);

  // Minimap: hidden by default, shown while panning/dragging, auto-hides after interaction ends.
  // Tracks hover and panning separately to avoid race conditions.
  const [isMinimapVisible, setIsMinimapVisible] = useState(false);
  const minimapHideTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isMinimapHovered = useRef(false);
  const isPanning = useRef(false);

  const clearHideTimeout = useCallback(() => {
    if (minimapHideTimeout.current) {
      clearTimeout(minimapHideTimeout.current);
      minimapHideTimeout.current = null;
    }
  }, []);

  const scheduleHide = useCallback(() => {
    clearHideTimeout();
    minimapHideTimeout.current = setTimeout(() => {
      // Only hide if not hovered and not actively panning
      if (!isMinimapHovered.current && !isPanning.current) {
        setIsMinimapVisible(false);
      }
    }, 1200);
  }, [clearHideTimeout]);

  const showMinimap = useCallback(() => {
    clearHideTimeout();
    setIsMinimapVisible(true);
  }, [clearHideTimeout]);

  const onMoveStart = useCallback(() => {
    isPanning.current = true;
    showMinimap();
  }, [showMinimap]);

  const onMoveEnd = useCallback(() => {
    isPanning.current = false;
    scheduleHide();
  }, [scheduleHide]);

  const onMinimapMouseEnter = useCallback(() => {
    isMinimapHovered.current = true;
    showMinimap();
  }, [showMinimap]);

  const onMinimapMouseLeave = useCallback(() => {
    isMinimapHovered.current = false;
    scheduleHide();
  }, [scheduleHide]);

  // Safety net: if mouse leaves the entire canvas wrapper while panning, ensure minimap hides
  const onCanvasMouseLeave = useCallback(() => {
    if (isPanning.current) {
      isPanning.current = false;
      scheduleHide();
    }
  }, [scheduleHide]);

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => { clearHideTimeout(); };
  }, [clearHideTimeout]);

  // Derive trigger nodes from React Flow state
  const triggerNodes = useMemo(() =>
    nodes
      .filter(n => n.type === 'cwcTriggerNode')
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

  // Stable resize callback for sticky notes
  const handleStickyNoteResize = useCallback((nodeId: string, width: number, height: number) => {
    onNodeResizeRef.current?.(nodeId, width, height);
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
              ...(newNode.type === 'stickyNoteNode' ? { onResize: handleStickyNoteResize } : {}),
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
          || (prevData?.validationWarnings?.join() ?? '') !== (newData?.validationWarnings?.join() ?? '')
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
            ...(newNode.type === 'stickyNoteNode' ? { onResize: handleStickyNoteResize } : {}),
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
    crayonMode,
    executeFromNode: (nodeId) => onExecuteFromNodeRef.current?.(nodeId),
    toggleDisabled: (nodeId) => onToggleNodeDisabledRef.current?.(nodeId),
    deleteNode: (nodeId) => {
      onNodeDeleteRef.current?.(nodeId);
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
  }), [singleSelectedId, crayonMode, setNodes, setEdges, buildConnectionsMap, onNodeClick]);

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
        type: 'cwcEdge',
        data: { connectionType, isAi },
      }, eds));

      if (onConnectionsChange) {
        const newEdges = [...edges, {
          ...connection,
          type: 'cwcEdge',
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
      const rawData = event.dataTransfer.getData('application/cwc-node-type');
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

  const CRAYON_SEQUENCE = ['d', 's', 'd', 'j'];

  const onKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      // Crayon mode easter egg: D, S, D, J toggles on; Escape toggles off
      if (crayonMode && event.key === 'Escape') {
        setCrayonMode(false);
        crayonSeqIndex.current = 0;
        return;
      }
      const k = event.key.toLowerCase();
      if (k === CRAYON_SEQUENCE[crayonSeqIndex.current]) {
        crayonSeqIndex.current++;
        if (crayonSeqIndex.current === CRAYON_SEQUENCE.length) {
          setCrayonMode(true);
          crayonSeqIndex.current = 0;
        }
      } else {
        crayonSeqIndex.current = 0;
      }

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
    [nodes, onNodeDelete, zoomIn, zoomOut, setNodes, crayonMode]
  );

  const defaultEdgeOptions = useMemo(
    () => ({
      type: 'cwcEdge',
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
        className={crayonMode ? 'crayon-mode' : undefined}
        style={{ width: '100%', height: '100%', opacity: ready ? 1 : 0, transition: 'opacity 300ms ease' }}
        onKeyDown={onKeyDown}
        onMouseLeave={onCanvasMouseLeave}
        tabIndex={0}
      >
        {/* SVG filter for crayon mode easter egg */}
        <svg width="0" height="0" style={{ position: 'absolute' }}>
          <defs>
            <filter id="crayon-filter">
              <feTurbulence type="turbulence" baseFrequency="0.04" numOctaves={4} result="noise" seed="1" />
              <feDisplacementMap in="SourceGraphic" in2="noise" scale={3} />
            </filter>
          </defs>
        </svg>
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
          onMoveStart={onMoveStart}
          onMoveEnd={onMoveEnd}
          panOnScroll
          deleteKeyCode={null}
          proOptions={proOptions}
          className="cwc-flow"
          nodesDraggable={!readOnly}
          nodesConnectable={!readOnly}
          edgesReconnectable={!readOnly}
        >
          <Background variant={BackgroundVariant.Dots} gap={GRID_SIZE} size={1} color="hsl(0, 0%, 22%)" />
          <MiniMap
            nodeColor={(n) => {
              if (n.type === 'cwcTriggerNode') return 'hsl(7, 100%, 68%)';
              return 'hsl(247, 49%, 53%)';
            }}
            maskColor="rgba(0,0,0,0.5)"
            style={{ background: 'hsl(0, 0%, 13%)', transition: 'bottom 0.2s ease', bottom: drawerOffset ? drawerOffset + 10 : undefined }}
            position="bottom-left"
            className={isMinimapVisible ? 'minimap-visible' : 'minimap-hidden'}
            onMouseEnter={onMinimapMouseEnter}
            onMouseLeave={onMinimapMouseLeave}
          />
          <Panel position="bottom-left" className="canvas-controls-panel" style={drawerOffset ? { bottom: drawerOffset + 10, transition: 'bottom 0.2s ease' } : { transition: 'bottom 0.2s ease' }}>
            <button className="ctrl-btn" onClick={() => zoomOut()} title="Zoom Out (Ctrl+-)">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            </button>
            <span className="ctrl-zoom-label">{Math.round(zoom * 100)}%</span>
            <button className="ctrl-btn" onClick={() => zoomIn()} title="Zoom In (Ctrl++)">
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
            }} title="Fit View">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
              </svg>
            </button>
            {!readOnly && (
              <button className="ctrl-btn" onClick={onCleanUp} title="Clean Up (Ctrl+Shift+O)">
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

export default function CwcCanvas(props: CwcCanvasProps) {
  return (
    <ReactFlowProvider>
      <CwcCanvasInner {...props} />
    </ReactFlowProvider>
  );
}
