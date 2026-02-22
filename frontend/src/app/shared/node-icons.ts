import {
  Globe, Merge, ArrowRight, Split, Clock, Play, Webhook, Reply,
  UnfoldVertical, Route, Pen, Code,
  CalendarClock, ListFilter, GitCompare, Lock, CopyMinus, FileText,
  Layers, Repeat, FileCode, ListEnd, ArrowUpNarrowWide, Replace, Sigma,
  Table2, Timer, ClipboardList, FileInput,
  Bot, Brain, Link, ScanText, Calculator, Search, BookOpen,
  Database, HardDriveUpload, ShieldCheck, Tag,
  LucideIconData,
} from 'lucide-angular';

// Custom brand icons not available in Lucide — defined in LucideIconData format
const Openai: LucideIconData = [
  ['path', { d: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z' }],
  ['path', { d: 'M12 6v12' }],
  ['path', { d: 'M8 8l4 4 4-4' }],
];

const Anthropic: LucideIconData = [
  ['path', { d: 'M12 2L2 22h20L12 2z' }],
  ['path', { d: 'M12 8l5 10H7l5-10z' }],
];

const Google: LucideIconData = [
  ['circle', { cx: '12', cy: '12', r: '10' }],
  ['path', { d: 'M12 6a6 6 0 0 1 0 12 6 6 0 0 1-6-6h6V6z' }],
];

const Ollama: LucideIconData = [
  ['circle', { cx: '12', cy: '12', r: '10' }],
  ['circle', { cx: '12', cy: '12', r: '4' }],
];

const Mistral: LucideIconData = [
  ['rect', { x: '4', y: '4', width: '16', height: '16', rx: '2' }],
  ['path', { d: 'M8 8h8' }],
  ['path', { d: 'M8 12h8' }],
  ['path', { d: 'M8 16h8' }],
];

const Azure: LucideIconData = [
  ['path', { d: 'M6 21L13.5 3H17l-4 8h5L7 21h-1z' }],
];

const Tavily: LucideIconData = [
  ['circle', { cx: '11', cy: '11', r: '8' }],
  ['path', { d: 'm21 21-4.3-4.3' }],
  ['path', { d: 'M11 8v6' }],
  ['path', { d: 'M8 11h6' }],
];

const BalanceScale: LucideIconData = [
  ['path', { d: 'M12 3v19' }],
  ['path', { d: 'M5 8h14' }],
  ['path', { d: 'M3 16l2-8 2 8a4.5 4.5 0 0 1-4 0z' }],
  ['path', { d: 'M17 16l2-8 2 8a4.5 4.5 0 0 1-4 0z' }],
  ['circle', { cx: '12', cy: '3', r: '1' }],
];

const Mcp: LucideIconData = [
  ['path', { d: 'M4 6a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6z' }],
  ['path', { d: 'M13 15a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3z' }],
  ['path', { d: 'M9 8h3a3 3 0 0 1 3 3v3' }],
  ['path', { d: 'M15 11v3' }],
];

/**
 * All icons used as node icons across the application.
 * Includes standard Lucide icons and custom brand icons.
 * Add new node icons here — they'll be available in the palette,
 * parameter panel, and anywhere else that uses NODE_ICON_SET.
 */
export const NODE_ICON_SET = {
  Globe, Merge, ArrowRight, Split, Clock, Play, Webhook, Reply,
  UnfoldVertical, Route, Pen, Code,
  CalendarClock, ListFilter, GitCompare, Lock, CopyMinus, FileText,
  Layers, Repeat, FileCode, ListEnd, ArrowUpNarrowWide, Replace, Sigma,
  Table2, Timer, ClipboardList, FileInput,
  Bot, Brain, Link, ScanText, Calculator, Search, BookOpen,
  Database, HardDriveUpload, ShieldCheck,
  Openai, Anthropic, Google, Ollama, Mistral, Azure, Tavily, Mcp, BalanceScale, Tag,
};
