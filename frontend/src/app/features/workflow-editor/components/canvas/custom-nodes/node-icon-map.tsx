import React from 'react';

const ICON_MAP: Record<string, React.ReactNode> = {
  'globe': <><circle cx="12" cy="12" r="10" /><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20" /><path d="M2 12h20" /></>,
  'merge': <><path d="m8 6 4-4 4 4" /><path d="M12 2v10.3a4 4 0 0 1-1.172 2.872L4 22" /><path d="m20 22-5-5" /></>,
  'arrow-right': <><path d="M5 12h14" /><path d="m12 5 7 7-7 7" /></>,
  'split': <><path d="M16 3h5v5" /><path d="M8 3H3v5" /><path d="M12 22v-8.3a4 4 0 0 0-1.172-2.872L3 3" /><path d="m15 9 6-6" /></>,
  'clock': <><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></>,
  'play': <><polygon points="6 3 20 12 6 21 6 3" /></>,
  'webhook': <><path d="M18 16.98h-5.99c-1.1 0-1.95.94-2.48 1.9A4 4 0 0 1 2 17c.01-.7.2-1.4.57-2" /><path d="m6 17 3.13-5.78c.53-.97.1-2.18-.5-3.1a4 4 0 1 1 6.89-4.06" /><path d="m12 6 3.13 5.73C15.66 12.7 16.9 13 18 13a4 4 0 0 1 0 8" /></>,
  'reply': <><polyline points="9 17 4 12 9 7" /><path d="M20 18v-2a4 4 0 0 0-4-4H4" /></>,
  'unfold-vertical': <><path d="M12 22v-6" /><path d="M12 8V2" /><path d="M4 12H2" /><path d="M10 12H8" /><path d="M16 12h-2" /><path d="M22 12h-2" /><path d="m15 19-3 3-3-3" /><path d="m15 5-3-3-3 3" /></>,
  'route': <><circle cx="6" cy="19" r="3" /><path d="M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15" /><circle cx="18" cy="5" r="3" /></>,
  'signpost': <><path d="M12 3v18" /><path d="M12 5h7l2 2-2 2h-7z" /><path d="M12 12H5l-2 2 2 2h7z" /></>,
  'pen': <><path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z" /></>,
  'code': <><polyline points="16 18 22 12 16 6" /><polyline points="8 6 2 12 8 18" /></>,

  // Flow nodes
  'list-filter': <><path d="M2 5h20" /><path d="M6 12h12" /><path d="M9 19h6" /></>,
  'repeat': <><path d="m17 2 4 4-4 4" /><path d="M3 11v-1a4 4 0 0 1 4-4h14" /><path d="m7 22-4-4 4-4" /><path d="M21 13v1a4 4 0 0 1-4 4H3" /></>,
  'arrow-up-narrow-wide': <><path d="m3 8 4-4 4 4" /><path d="M7 4v16" /><path d="M11 12h4" /><path d="M11 16h7" /><path d="M11 20h10" /></>,
  'list-end': <><path d="M16 5H3" /><path d="M16 12H3" /><path d="M9 19H3" /><path d="m16 16-3 3 3 3" /><path d="M21 5v12a2 2 0 0 1-2 2h-6" /></>,
  'copy-minus': <><line x1="12" x2="18" y1="15" y2="15" /><rect width="14" height="14" x="8" y="8" rx="2" ry="2" /><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" /></>,

  // Data Transformation nodes
  'layers': <><path d="M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z" /><path d="M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12" /><path d="M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17" /></>,
  'sigma': <><path d="M18 7V5a1 1 0 0 0-1-1H6.5a.5.5 0 0 0-.4.8l4.5 6a2 2 0 0 1 0 2.4l-4.5 6a.5.5 0 0 0 .4.8H17a1 1 0 0 0 1-1v-2" /></>,
  'calendar-clock': <><path d="M16 14v2.2l1.6 1" /><path d="M16 2v4" /><path d="M21 7.5V6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h3.5" /><path d="M3 10h5" /><path d="M8 2v4" /><circle cx="16" cy="16" r="6" /></>,
  'hash': <><line x1="4" x2="20" y1="9" y2="9" /><line x1="4" x2="20" y1="15" y2="15" /><line x1="10" x2="8" y1="3" y2="21" /><line x1="16" x2="14" y1="3" y2="21" /></>,
  'lock': <><rect width="18" height="11" x="3" y="11" rx="2" ry="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></>,
  'file-code': <><path d="M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z" /><path d="M14 2v5a1 1 0 0 0 1 1h5" /><path d="M10 12.5 8 15l2 2.5" /><path d="m14 12.5 2 2.5-2 2.5" /></>,
  'file-text': <><path d="M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z" /><path d="M14 2v5a1 1 0 0 0 1 1h5" /><path d="M10 9H8" /><path d="M16 13H8" /><path d="M16 17H8" /></>,
  'git-compare': <><circle cx="18" cy="18" r="3" /><circle cx="6" cy="6" r="3" /><path d="M13 6h3a2 2 0 0 1 2 2v7" /><path d="M11 18H8a2 2 0 0 1-2-2V9" /></>,
  'replace': <><path d="M14 4a1 1 0 0 1 1-1" /><path d="M15 10a1 1 0 0 1-1-1" /><path d="M21 4a1 1 0 0 0-1-1" /><path d="M21 9a1 1 0 0 1-1 1" /><path d="m3 7 3 3 3-3" /><path d="M6 10V5a2 2 0 0 1 2-2h2" /><rect x="3" y="14" width="7" height="7" rx="1" /></>,

  // Core nodes
  'table-2': <><path d="M9 3H5a2 2 0 0 0-2 2v4m6-6h10a2 2 0 0 1 2 2v4M9 3v18m0 0h10a2 2 0 0 0 2-2V9M9 21H5a2 2 0 0 1-2-2V9m0 0h18" /></>,

  // Human in the Loop nodes
  'timer': <><line x1="10" x2="14" y1="2" y2="2" /><line x1="12" x2="15" y1="14" y2="11" /><circle cx="12" cy="14" r="8" /></>,
  'clipboard-list': <><rect width="8" height="4" x="8" y="2" rx="1" ry="1" /><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" /><path d="M12 11h4" /><path d="M12 16h4" /><path d="M8 11h.01" /><path d="M8 16h.01" /></>,
  'file-input': <><path d="M4 22h14a2 2 0 0 0 2-2V7l-5-5H6a2 2 0 0 0-2 2v4" /><path d="M14 2v4a2 2 0 0 0 2 2h4" /><path d="M2 15h10" /><path d="m9 18 3-3-3-3" /></>,

  // Database / Vector Stores
  'database': <><ellipse cx="12" cy="5" rx="9" ry="3" /><path d="M3 5V19A9 3 0 0 0 21 19V5" /><path d="M3 12A9 3 0 0 0 21 12" /></>,
  'hard-drive-upload': <><path d="m16 6-4-4-4 4" /><path d="M12 2v8" /><rect width="20" height="8" x="2" y="14" rx="2" /><path d="M6 18h.01" /><path d="M10 18h.01" /></>,

  // AI nodes
  'bot': <><path d="M12 8V4H8" /><rect width="16" height="12" x="4" y="8" rx="2" /><path d="M2 14h2" /><path d="M20 14h2" /><path d="M15 13v2" /><path d="M9 13v2" /></>,
  'brain': <><path d="M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z" /><path d="M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z" /><path d="M15 13a4.5 4.5 0 0 1-3-4 4.5 4.5 0 0 1-3 4" /><path d="M17.599 6.5a3 3 0 0 0 .399-1.375" /><path d="M6.003 5.125A3 3 0 0 0 6.401 6.5" /><path d="M3.477 10.896a4 4 0 0 1 .585-.396" /><path d="M19.938 10.5a4 4 0 0 1 .585.396" /><path d="M6 18a4 4 0 0 1-1.967-.516" /><path d="M19.967 17.484A4 4 0 0 1 18 18" /></>,
  'link': <><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" /><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" /></>,
  'scan-text': <><path d="M3 7V5a2 2 0 0 1 2-2h2" /><path d="M17 3h2a2 2 0 0 1 2 2v2" /><path d="M21 17v2a2 2 0 0 1-2 2h-2" /><path d="M7 21H5a2 2 0 0 1-2-2v-2" /><path d="M7 8h8" /><path d="M7 12h10" /><path d="M7 16h6" /></>,
  'calculator': <><rect width="16" height="20" x="4" y="2" rx="2" /><line x1="8" x2="16" y1="6" y2="6" /><line x1="16" x2="16" y1="14" y2="18" /><path d="M16 10h.01" /><path d="M12 10h.01" /><path d="M8 10h.01" /><path d="M12 14h.01" /><path d="M8 14h.01" /><path d="M12 18h.01" /><path d="M8 18h.01" /></>,
  'search': <><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /></>,
  'book-open': <><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" /><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" /></>,
  'openai': <><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z" /><path d="M12 6v12" /><path d="M8 8l4 4 4-4" /></>,
  'anthropic': <><path d="M12 2L2 22h20L12 2z" /><path d="M12 8l5 10H7l5-10z" /></>,
  'google': <><circle cx="12" cy="12" r="10" /><path d="M12 6a6 6 0 0 1 0 12 6 6 0 0 1-6-6h6V6z" /></>,
  'ollama': <><circle cx="12" cy="12" r="10" /><circle cx="12" cy="12" r="4" /></>,
  'mistral': <><rect x="4" y="4" width="16" height="16" rx="2" /><path d="M8 8h8" /><path d="M8 12h8" /><path d="M8 16h8" /></>,
  'azure': <><path d="M6 21L13.5 3H17l-4 8h5L7 21h-1z" /></>,
  'tavily': <><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /><path d="M11 8v6" /><path d="M8 11h6" /></>,
  'mcp': <><path d="M4 6a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6z" /><path d="M13 15a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3z" /><path d="M9 8h3a3 3 0 0 1 3 3v3" /><path d="M15 11v3" /></>,
  'shieldCheck': <><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" /><path d="m9 12 2 2 4-4" /></>,
  'balance-scale': <><path d="M12 3v19" /><path d="M5 8h14" /><path d="M3 16l2-8 2 8a4.5 4.5 0 0 1-4 0z" /><path d="M17 16l2-8 2 8a4.5 4.5 0 0 1-4 0z" /><circle cx="12" cy="3" r="1" /></>,
  'tag': <><path d="M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z" /><circle cx="7.5" cy="7.5" r=".5" fill="currentColor" /></>,

  // Database nodes
  'postgres': <><ellipse cx="12" cy="5" rx="9" ry="3" /><path d="M3 5v6c0 1.66 4.03 3 9 3s9-1.34 9-3V5" /><path d="M3 11v6c0 1.66 4.03 3 9 3s9-1.34 9-3v-6" /></>,
  'mysql': <><ellipse cx="12" cy="5" rx="9" ry="3" /><path d="M3 5v14c0 1.66 4.03 3 9 3s9-1.34 9-3V5" /><path d="M3 12c0 1.66 4.03 3 9 3s9-1.34 9-3" /></>,
  'oracle': <><ellipse cx="12" cy="12" rx="10" ry="8" /><ellipse cx="12" cy="12" rx="6" ry="4" /></>,
  'mongo': <><path d="M12 2C10 4 7 7 7 12c0 3.5 2.5 6 5 8" /><path d="M12 2c2 2 5 5 5 10 0 3.5-2.5 6-5 8" /><path d="M12 22v-2" /><line x1="12" y1="10" x2="12" y2="14" /></>,
  'redis': <><path d="M12 2L2 7l10 5 10-5-10-5z" /><path d="M2 17l10 5 10-5" /><path d="M2 12l10 5 10-5" /></>,
  'neo4j': <><circle cx="12" cy="6" r="3" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="18" r="3" /><line x1="12" y1="9" x2="6" y2="15" /><line x1="12" y1="9" x2="18" y2="15" /><line x1="6" y1="18" x2="18" y2="18" /></>,

  // Analytics
  'datadog': <><circle cx="12" cy="12" r="10" /><circle cx="9" cy="10" r="1.5" /><circle cx="15" cy="10" r="1.5" /><path d="M8 15c1.5 2 6.5 2 8 0" /></>,
};

export function NodeIcon({ name, size = 16 }: { name: string; size?: number }) {
  const paths = ICON_MAP[name];
  if (!paths) return null;

  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      {paths}
    </svg>
  );
}
