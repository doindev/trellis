import React, { useCallback, useRef, useMemo, useState, useEffect } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  useReactFlow,
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
  type OnNodesChange,
  type OnEdgesChange,
} from '@xyflow/react';
import TrellisNode from './custom-nodes/trellis-node';
import TrellisTriggerNode from './custom-nodes/trellis-trigger-node';
import TrellisEdge from './custom-edges/trellis-edge';

export interface TrellisCanvasProps {
  initialNodes: Node[];
  initialEdges: Edge[];
  onNodeClick?: (nodeId: string) => void;
  onPaneClick?: () => void;
  onNodeAdd?: (type: string, position: { x: number; y: number }, displayName: string, version: number) => void;
  onNodeDelete?: (nodeId: string) => void;
  onNodesChange?: (positions: Record<string, [number, number]>) => void;
  onConnectionsChange?: (connections: any) => void;
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
  onNodeClick,
  onPaneClick,
  onNodeAdd,
  onNodeDelete,
  onNodesChange: onNodesPositionChange,
  onConnectionsChange,
}: TrellisCanvasProps) {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const { fitView, screenToFlowPosition } = useReactFlow();
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

  // Fit view only once on initial load when nodes are present
  useEffect(() => {
    if (!initialFitDone.current && initialNodes.length > 0) {
      initialFitDone.current = true;
      // Small delay to ensure React Flow has rendered the nodes
      requestAnimationFrame(() => {
        fitView({ padding: 0.2, duration: 0 });
        setReady(true);
      });
    } else if (initialNodes.length === 0) {
      setReady(true);
    }
  }, [initialNodes, fitView]);

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

  const onPaneClickHandler = useCallback(() => {
    onPaneClick?.();
  }, [onPaneClick]);

  const onNodeDragStop = useCallback(
    (_event: React.MouseEvent, _node: Node, draggedNodes: Node[]) => {
      if (onNodesPositionChange) {
        const positions: Record<string, [number, number]> = {};
        draggedNodes.forEach((n) => {
          positions[n.id] = [n.position.x, n.position.y];
        });
        onNodesPositionChange(positions);
      }
    },
    [onNodesPositionChange]
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
      }
    },
    [nodes, onNodeDelete]
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
        snapToGrid
        snapGrid={[GRID_SIZE, GRID_SIZE]}
        minZoom={MIN_ZOOM}
        maxZoom={MAX_ZOOM}
        panOnScroll
        deleteKeyCode={null}
        proOptions={proOptions}
        className="trellis-flow"
      >
        <Background variant={BackgroundVariant.Dots} gap={GRID_SIZE} size={1} color="hsl(0, 0%, 22%)" />
        <Controls
          showZoom={true}
          showFitView={true}
          showInteractive={false}
          position="bottom-right"
        />
        <MiniMap
          nodeColor={(n) => {
            if (n.type === 'trellisTriggerNode') return 'hsl(7, 100%, 68%)';
            return 'hsl(247, 49%, 53%)';
          }}
          maskColor="rgba(0,0,0,0.5)"
          style={{ background: 'hsl(0, 0%, 13%)' }}
          position="bottom-left"
        />
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
