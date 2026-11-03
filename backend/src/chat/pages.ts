import type { Client } from './oauth';

// Anmeldeseite für die KI-Chat-Verbindung: schwarz-weiß, ohne Skripte, DE/EN.

type Lang = 'de' | 'en';

const T = {
  de: {
    title: 'Overfit verbinden',
    wants: (c: string) => `<b>${c}</b> möchte auf deine Overfit-Trainingsdaten zugreifen.`,
    redirect: (h: string) => `Danach geht es zurück zu ${h}.`,
    can: 'Der Chat kann Profil, Workouts, Trainings, Fortschritt und – falls freigegeben – Körperfotos lesen sowie Workouts und Notizen in deinen Eingang legen. Trennen jederzeit in der App.',
    label: 'Kopplungscode',
    hint: 'In der App: Profil → KI-Chat verbinden → Code anzeigen',
    allow: 'Verbinden',
    deny: 'Abbrechen',
    wrong: 'Code falsch oder abgelaufen. Hol dir in der App einen neuen.',
    errors: {
      unknown_client: 'Unbekannte Anwendung. Bitte die Verbindung im Chat neu starten.',
      bad_redirect: 'Ungültige Rücksprungadresse.',
      bad_request: 'Ungültige Anfrage.',
      rate_limited: 'Zu viele Versuche. Bitte in ein paar Minuten erneut probieren.',
    } as Record<string, string>,
  },
  en: {
    title: 'Connect Overfit',
    wants: (c: string) => `<b>${c}</b> wants to access your Overfit training data.`,
    redirect: (h: string) => `You will be sent back to ${h}.`,
    can: 'The chat can read your profile, templates, workouts, progress and – if allowed – body photos, and put templates and notes into your inbox. Disconnect any time in the app.',
    label: 'Pairing code',
    hint: 'In the app: Profile → Connect AI chat → Show code',
    allow: 'Connect',
    deny: 'Cancel',
    wrong: 'Wrong or expired code. Get a new one in the app.',
    errors: {
      unknown_client: 'Unknown application. Please restart the connection from the chat.',
      bad_redirect: 'Invalid redirect address.',
      bad_request: 'Invalid request.',
      rate_limited: 'Too many attempts. Please try again in a few minutes.',
    } as Record<string, string>,
  },
};

export function esc(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}

const CSS = `
:root{color-scheme:dark light;--bg:#000;--fg:#fff;--mute:#9a9a9a;--line:#2a2a2a}
@media (prefers-color-scheme:light){:root{--bg:#fff;--fg:#000;--mute:#666;--line:#e2e2e2}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.5 Inter,system-ui,-apple-system,sans-serif}
main{max-width:420px;margin:0 auto;padding:48px 16px}h1{font-size:28px;font-weight:600;margin:0 0 24px;letter-spacing:-.02em}
p{margin:0 0 16px}.mute{color:var(--mute);font-size:14px}hr{border:0;border-top:1px solid var(--line);margin:24px 0}
label{display:block;font-size:14px;color:var(--mute);margin-bottom:8px}
input[type=text]{width:100%;background:transparent;color:var(--fg);border:0;border-bottom:1px solid var(--fg);font:600 32px/1.2 ui-monospace,monospace;letter-spacing:.12em;padding:8px 0;text-transform:uppercase;outline:none}
.err{margin-top:12px;font-size:14px}.row{display:flex;gap:12px;margin-top:32px;align-items:center}
button{font:600 16px Inter,system-ui,sans-serif;border-radius:999px;padding:14px 28px;cursor:pointer}
.primary{background:var(--fg);color:var(--bg);border:0;flex:1}.ghost{background:transparent;color:var(--fg);border:0;text-decoration:underline}
`;

function headers(extraFormAction: string, status: number): ResponseInit {
  return {
    status,
    headers: {
      'content-type': 'text/html; charset=utf-8',
      'cache-control': 'no-store',
      'x-frame-options': 'DENY',
      'referrer-policy': 'no-referrer',
      'content-security-policy': `default-src 'none'; style-src 'unsafe-inline'; form-action 'self' ${extraFormAction}; frame-ancestors 'none'; base-uri 'none'`,
    },
  };
}

function shell(lang: Lang, body: string): string {
  return `<!doctype html><html lang="${lang}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex"><title>${T[lang].title}</title><style>${CSS}</style></head><body><main>${body}</main></body></html>`;
}

const FIELDS = ['response_type', 'client_id', 'redirect_uri', 'code_challenge', 'code_challenge_method', 'state', 'scope', 'resource'];

export function authorizePage(
  lang: Lang,
  o: { client: Client; params: URLSearchParams; redirectUri: string; error?: boolean },
  status = 200,
): Response {
  const t = T[lang];
  const host = new URL(o.redirectUri).host;
  const name = esc(o.client.name ?? host);
  const hidden = FIELDS.map((f) => {
    const v = f === 'redirect_uri' ? o.redirectUri : o.params.get(f);
    return v === null ? '' : `<input type="hidden" name="${f}" value="${esc(v)}">`;
  }).join('');
  const body = `<h1>${t.title}</h1>
<p>${t.wants(name)}</p><p class="mute">${esc(t.redirect(host))}</p><p class="mute">${t.can}</p><hr>
<form method="post" action="/oauth/authorize">${hidden}
<label for="c">${t.label}</label>
<input id="c" type="text" name="pairing_code" inputmode="text" autocomplete="one-time-code" autocapitalize="characters" spellcheck="false" maxlength="12" placeholder="XXXX-XXXX" autofocus required>
<p class="mute" style="margin-top:8px">${t.hint}</p>
${o.error ? `<p class="err" role="alert">${t.wrong}</p>` : ''}
<div class="row"><button class="primary" type="submit" name="action" value="allow">${t.allow}</button><button class="ghost" type="submit" name="action" value="deny" formnovalidate>${t.deny}</button></div>
</form>`;
  return new Response(shell(lang, body), headers(new URL(o.redirectUri).origin, status));
}

export function errorPage(lang: Lang, status: number, key: string): Response {
  const t = T[lang];
  return new Response(shell(lang, `<h1>${t.title}</h1><p>${esc(t.errors[key] ?? t.errors.bad_request)}</p>`), headers('', status));
}
