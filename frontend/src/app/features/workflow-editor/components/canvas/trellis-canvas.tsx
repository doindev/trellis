import React, { useCallback, useRef, useMemo, useState, useEffect } from 'react';
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
import TrellisEdge from './custom-edges/trellis-edge';

export interface TrellisCanvasProps {
  initialNodes: Node[];
  initialEdges: Edge[];
  isExecuting?: boolean;
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
}

const GRID_SIZE = 16;
const MIN_ZOOM = 0.1;
const MAX_ZOOM = 4;
const CONNECTION_RADIUS = 60;

const nodeTypes: NodeTypes = {
  trellisNode: TrellisNode as any,
  trellisTriggerNode: TrellisTriggerNode as any,
};

const edgeTypes: EdgeTypes = {
  trellisEdge: TrellisEdge as any,
};

function TrellisCanvasInner({
  initialNodes,
  initialEdges,
  isExecuting,
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
}: TrellisCanvasProps) {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const { fitView, zoomIn, zoomOut, screenToFlowPosition } = useReactFlow();
  const { zoom } = useViewport();
  const helperExposed = useRef(false);
  const [nodes, setNodes, handleNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, handleEdgesChange] = useEdgesState(initialEdges);
  const [ready, setReady] = useState(false);
  const initialFitDone = useRef(false);

  // Sync nodes from Angular without resetting viewport
  useEffect(() => {
    setNodes(initialNodes);
  }, [initialNodes, setNodes]);

  useEffect(() => {
    setEdges(initialEdges);
  }, [initialEdges, setEdges]);

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

  const onConnect = useCallback(
    (connection: Connection) => {
      setEdges((eds) => addEdge({ ...connection, type: 'trellisEdge' }, eds));
      if (onConnectionsChange) {
        const newEdges = [...edges, { ...connection, type: 'trellisEdge', id: `e-${connection.source}-${connection.target}` }];
        const connectionsMap: Record<string, any> = {};
        newEdges.forEach((e: any) => {
          if (!connectionsMap[e.source]) {
            connectionsMap[e.source] = { main: [[]] };
          }
          connectionsMap[e.source].main[0].push({
            node: e.target,
            type: 'main',
            index: 0,
          });
        });
        onConnectionsChange(connectionsMap);
      }
    },
    [edges, setEdges, onConnectionsChange]
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

  const onKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      if (event.key === 'Delete' || event.key === 'Backspace') {
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
    [nodes, onNodeDelete, zoomIn, zoomOut]
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
        onConnect={onConnect}
        onNodeClick={onNodeClickHandler}
        onNodeDoubleClick={onNodeDoubleClickHandler}
        onPaneClick={onPaneClickHandler}
        onNodeDragStop={onNodeDragStop}
        onDragOver={onDragOver}
        onDrop={onDrop}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        defaultEdgeOptions={defaultEdgeOptions}
        connectionLineType={ConnectionLineType.SmoothStep}
        connectionLineStyle={connectionLineStyle}
        connectionRadius={CONNECTION_RADIUS}
        defaultViewport={{ x: 0, y: 0, zoom: 1.2 }}
        minZoom={MIN_ZOOM}
        maxZoom={MAX_ZOOM}
        panOnScroll
        deleteKeyCode={null}
        proOptions={proOptions}
        className="trellis-flow"
      >
        <Background variant={BackgroundVariant.Dots} gap={GRID_SIZE} size={1} color="hsl(0, 0%, 22%)" />
        <MiniMap
          nodeColor={(n) => {
            if (n.type === 'trellisTriggerNode') return 'hsl(7, 100%, 68%)';
            return 'hsl(247, 49%, 53%)';
          }}
          maskColor="rgba(0,0,0,0.5)"
          style={{ background: 'hsl(0, 0%, 13%)' }}
          position="bottom-left"
        />
        <Panel position="bottom-left" className="canvas-controls-panel">
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
          <button className="ctrl-btn" onClick={() => fitView({ padding: 0.2, duration: 200 })} title="Fit view">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
            </svg>
          </button>
          <button className="ctrl-btn" title="Clean up">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="m16 22-1-4" /><path d="M19 13.99a1 1 0 0 0 1-1V12a2 2 0 0 0-2-2h-3a1 1 0 0 1-1-1V4a2 2 0 0 0-4 0v5a1 1 0 0 1-1 1H6a2 2 0 0 0-2 2v.99a1 1 0 0 0 1 1" /><path d="M5 14h14l1.973 6.767A1 1 0 0 1 20 22H4a1 1 0 0 1-.973-1.233z" /><path d="m8 22 1-4" />
            </svg>
          </button>
        </Panel>

        {nodes.length > 0 && (
          <div className="canvas-action-bar">
            <button className="canvas-execute-btn" onClick={onExecute} disabled={isExecuting}>
              <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" stroke="none">
                <polygon points="6 3 20 12 6 21 6 3" />
              </svg>
              Execute
            </button>
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
  );
}

export default function TrellisCanvas(props: TrellisCanvasProps) {
  return (
    <ReactFlowProvider>
      <TrellisCanvasInner {...props} />
    </ReactFlowProvider>
  );
}
