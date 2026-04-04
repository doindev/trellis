package io.cwc.util;

/**
 * Generates a self-contained HTML page for MCP API token management.
 * Served at {mcp-endpoint-url}/token-management.
 */
public final class McpTokenPageGenerator {

    private McpTokenPageGenerator() {}

    public static String generate(String endpointPath) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>MCP Token Management</title>
            <style>
              * { box-sizing: border-box; margin: 0; padding: 0; }
              body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f7; color: #1a1a2e; padding: 32px; }
              .container { max-width: 700px; margin: 0 auto; }
              h1 { font-size: 24px; margin-bottom: 4px; }
              .subtitle { color: #888; font-size: 14px; margin-bottom: 24px; }
              .card { background: #fff; border-radius: 8px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 16px; }
              .card h2 { font-size: 16px; margin-bottom: 16px; }
              .form-row { display: flex; gap: 12px; margin-bottom: 12px; }
              .form-row > * { flex: 1; }
              label { display: block; font-size: 12px; font-weight: 600; color: #666; margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.5px; }
              input, select { width: 100%%; padding: 8px 12px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; }
              input:focus, select:focus { outline: none; border-color: #4fc3f7; box-shadow: 0 0 0 2px rgba(79,195,247,0.2); }
              button { padding: 8px 16px; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; }
              .btn-primary { background: #1a73e8; color: #fff; }
              .btn-primary:hover { background: #1557b0; }
              .btn-primary:disabled { background: #ccc; cursor: default; }
              .btn-danger { background: none; color: #d32f2f; border: 1px solid #d32f2f; padding: 4px 10px; font-size: 12px; }
              .btn-danger:hover { background: #fce4ec; }
              .btn-copy { background: #e8eaf6; color: #3949ab; padding: 4px 10px; font-size: 12px; }
              .token-list { display: flex; flex-direction: column; gap: 8px; }
              .token-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; background: #fafafa; border-radius: 6px; border: 1px solid #eee; }
              .token-info { flex: 1; }
              .token-name { font-weight: 600; font-size: 14px; }
              .token-meta { font-size: 12px; color: #888; margin-top: 2px; }
              .token-prefix { font-family: monospace; background: #f0f0f0; padding: 1px 6px; border-radius: 3px; }
              .badge-expired { background: #fce4ec; color: #c62828; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
              .new-token-box { background: #e8f5e9; border: 1px solid #a5d6a7; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
              .new-token-box .warning { color: #e65100; font-weight: 600; font-size: 13px; margin-bottom: 8px; }
              .new-token-value { display: flex; gap: 8px; align-items: center; }
              .new-token-value code { flex: 1; font-size: 13px; background: #fff; padding: 8px 12px; border-radius: 4px; border: 1px solid #c8e6c9; word-break: break-all; }
              .empty { text-align: center; color: #888; padding: 24px; }
              .error { color: #d32f2f; font-size: 13px; margin-top: 8px; }
              .limit-info { font-size: 12px; color: #888; margin-top: 8px; }
            </style>
            </head>
            <body>
            <div class="container">
              <h1>MCP API Token Management</h1>
              <p class="subtitle">Endpoint: <strong>/mcp/%s</strong></p>

              <div id="newTokenBox" class="new-token-box" style="display:none">
                <div class="warning">This is the only time this token value will be shown. Copy it now.</div>
                <div class="new-token-value">
                  <code id="newTokenValue"></code>
                  <button class="btn-copy" onclick="copyToken()">Copy</button>
                </div>
              </div>

              <div class="card">
                <h2>Create Token</h2>
                <div class="form-row">
                  <div>
                    <label>Token Name</label>
                    <input type="text" id="tokenName" placeholder="My IDE token" maxlength="100">
                  </div>
                  <div>
                    <label>Expires</label>
                    <select id="tokenExpiry">
                      <option value="0">Never</option>
                      <option value="7">7 days</option>
                      <option value="30" selected>30 days</option>
                      <option value="60">60 days</option>
                      <option value="90">90 days</option>
                      <option value="180">6 months</option>
                      <option value="365">1 year</option>
                    </select>
                  </div>
                </div>
                <button class="btn-primary" id="createBtn" onclick="createToken()">Create Token</button>
                <div id="createError" class="error" style="display:none"></div>
                <div class="limit-info">Maximum 5 tokens per endpoint.</div>
              </div>

              <div class="card">
                <h2>Your Tokens</h2>
                <div id="tokenList" class="token-list">
                  <div class="empty">Loading...</div>
                </div>
              </div>
            </div>

            <script>
              const ENDPOINT_PATH = '%s';
              let endpointId = null;

              async function init() {
                // Find the endpoint ID from the MCP settings
                try {
                  const res = await fetch('/api/settings/mcp');
                  const data = await res.json();
                  const ep = data.endpoints?.find(e => e.path === ENDPOINT_PATH);
                  if (ep) endpointId = ep.id;
                } catch(e) { console.error('Failed to load MCP settings', e); }
                loadTokens();
              }

              async function loadTokens() {
                const list = document.getElementById('tokenList');
                try {
                  const url = endpointId ? '/api/mcp-tokens?endpointId=' + endpointId : '/api/mcp-tokens';
                  const res = await fetch(url);
                  const tokens = await res.json();

                  if (tokens.length === 0) {
                    list.innerHTML = '<div class="empty">No tokens yet. Create one above.</div>';
                    return;
                  }

                  list.innerHTML = tokens.map(t => `
                    <div class="token-item">
                      <div class="token-info">
                        <div class="token-name">${esc(t.name)}</div>
                        <div class="token-meta">
                          <span class="token-prefix">${esc(t.tokenPrefix)}</span>
                          &middot; Created ${formatDate(t.createdAt)}
                          ${t.expiresAt ? '&middot; Expires ' + formatDate(t.expiresAt) : '&middot; Never expires'}
                          ${t.expired ? ' <span class="badge-expired">EXPIRED</span>' : ''}
                        </div>
                      </div>
                      <button class="btn-danger" onclick="deleteToken('${t.id}', '${esc(t.name)}')">Delete</button>
                    </div>
                  `).join('');
                } catch(e) {
                  list.innerHTML = '<div class="empty">Failed to load tokens.</div>';
                }
              }

              async function createToken() {
                const name = document.getElementById('tokenName').value.trim();
                const days = parseInt(document.getElementById('tokenExpiry').value);
                const errEl = document.getElementById('createError');
                errEl.style.display = 'none';

                if (!name) { errEl.textContent = 'Token name is required.'; errEl.style.display = 'block'; return; }
                if (!endpointId) { errEl.textContent = 'Could not determine endpoint ID.'; errEl.style.display = 'block'; return; }

                document.getElementById('createBtn').disabled = true;
                try {
                  const res = await fetch('/api/mcp-tokens', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({ name, endpointId, expirationDays: days || null })
                  });
                  if (!res.ok) {
                    const err = await res.json();
                    throw new Error(err.message || err.error || 'Failed to create token');
                  }
                  const data = await res.json();

                  // Show the token value (one-time display)
                  document.getElementById('newTokenValue').textContent = data.token;
                  document.getElementById('newTokenBox').style.display = 'block';
                  document.getElementById('tokenName').value = '';
                  loadTokens();
                } catch(e) {
                  errEl.textContent = e.message;
                  errEl.style.display = 'block';
                } finally {
                  document.getElementById('createBtn').disabled = false;
                }
              }

              function copyToken() {
                const val = document.getElementById('newTokenValue').textContent;
                navigator.clipboard.writeText(val).then(() => {
                  const btn = document.querySelector('#newTokenBox .btn-copy');
                  btn.textContent = 'Copied!';
                  setTimeout(() => btn.textContent = 'Copy', 2000);
                });
              }

              async function deleteToken(id, name) {
                if (!confirm('Delete token "' + name + '"? This cannot be undone.')) return;
                await fetch('/api/mcp-tokens/' + id, { method: 'DELETE' });
                loadTokens();
              }

              function formatDate(iso) {
                if (!iso) return '';
                return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
              }

              function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

              init();
            </script>
            </body>
            </html>
            """.formatted(endpointPath, endpointPath);
    }
}
