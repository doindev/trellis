import React, { useCallback, useRef, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  addEdge,
  BackgroundVariant,
  type Connection,
  type Node,
  type Edge,
  type NodeTypes,
  type EdgeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import TrellisNode from './custom-nodes/trellis-node';
import TrellisTriggerNode from './custom-nodes/trellis-trigger-node';
import TrellisEdge from './custom-edges/trellis-edge';

export interface TrellisCanvasProps {
  initialNodes: Node[];
  initialEdges: Edge[];
  onNodeClick?: (nodeId: string) => void;
  onNodeAdd?: (type: string, position: { x: number; y: number }, displayName: string, version: number) => void;
  onNodeDelete?: (nodeId: string) => void;
  onNodesChange?: (positions: Record<string, [number, number]>) => void;
  onConnectionsChange?: (connections: any) => void;
}

const nodeTypes: NodeTypes = {
  trellisNode: TrellisNode as any,
  trellisTriggerNode: TrellisTriggerNode as any,
};

const edgeTypes: EdgeTypes = {
  trellisEdge: TrellisEdge as any,
};

export default function TrellisCanvas({
  initialNodes,
  initialEdges,
  onNodeClick,
  onNodeAdd,
  onNodeDelete,
  onNodesChange: onNodesPositionChange,
  onConnectionsChange,
}: TrellisCanvasProps) {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [nodes, setNodes, handleNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, handleEdgesChange] = useEdgesState(initialEdges);

  // Sync when initialNodes/initialEdges change
  React.useEffect(() => {
    setNodes(initialNodes);
  }, [initialNodes, setNodes]);

  React.useEffect(() => {
    setEdges(initialEdges);
  }, [initialEdges, setEdges]);

  const onConnect = useCallback(
    (connection: Connection) => {
      setEdges((eds) => addEdge({ ...connection, type: 'trellisEdge' }, eds));
      if (onConnectionsChange) {
        // Build connections map from edges
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
        const bounds = reactFlowWrapper.current?.getBoundingClientRect();
        if (!bounds) return;

        const position = {
          x: event.clientX - bounds.left - 75,
          y: event.clientY - bounds.top - 25,
        };

        onNodeAdd?.(nodeData.type, position, nodeData.displayName, nodeData.version);
      } catch (e) {
        console.error('Failed to parse dropped node data', e);
      }
    },
    [onNodeAdd]
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
    }),
    []
  );

  return (
    <div ref={reactFlowWrapper} style={{ width: '100%', height: '100%' }} onKeyDown={onKeyDown} tabIndex={0}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={handleNodesChange}
        onEdgesChange={handleEdgesChange}
        onConnect={onConnect}
        onNodeClick={onNodeClickHandler}
        onNodeDragStop={onNodeDragStop}
        onDragOver={onDragOver}
        onDrop={onDrop}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        defaultEdgeOptions={defaultEdgeOptions}
        fitView
        snapToGrid
        snapGrid={[16, 16]}
        deleteKeyCode={['Delete', 'Backspace']}
        className="trellis-flow"
      >
        <Background variant={BackgroundVariant.Dots} gap={16} size={1} color="hsl(0, 0%, 30%)" />
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
