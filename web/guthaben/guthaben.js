// Kaufseite für overfit-Guthaben. Ohne Login: Aus der App kommt ein kurzlebiger Kauflink (#t=…), ohne ihn kauft man
// einen Guthaben-Code (z. B. als Geschenk). Der Checkout läuft über Paddle.js (Merchant of Record); gebucht wird erst
// per Webhook im Backend, die Erfolgsseite fragt nur den Stand ab.
(function () {
  'use strict';
  var API = String(window.OVERFIT_API || '').replace(/\/+$/, '');
  var PADDLE_JS = 'https://cdn.paddle.com/paddle/v2/paddle.js';

  var hash = new URLSearchParams(location.hash.slice(1));
  var query = new URLSearchParams(location.search);
  var token = hash.get('t') || '';
  var orderId = hash.get('order') || query.get('order') || '';
  var langParam = hash.get('lang') || query.get('lang');
  var de = langParam ? langParam === 'de' : (navigator.language || '').toLowerCase().indexOf('de') === 0;
  var lang = de ? 'de' : 'en';

  var T = de ? {
    loading: 'Lädt …', test: 'Testmodus – keine echte Zahlung', errTitle: 'Gerade nicht verfügbar',
    errSetup: 'Die Kaufseite ist noch nicht eingerichtet.', errOff: 'Guthaben im Web gibt es gerade nicht. In der App kannst du weiter mit eigenem Key oder Code arbeiten.',
    errNet: 'Der Server ist nicht erreichbar. Bitte später noch einmal versuchen.',
    creditTitle: 'Guthaben aufladen', creditSub: 'Das Guthaben landet direkt in deiner App. Kein Konto, kein Login.',
    codeTitle: 'Guthaben-Code kaufen', codeSub: 'Du bekommst einen Code, den du (oder wer ihn geschenkt bekommt) in der App unter Guthaben → „Code einlösen“ einlöst.',
    linkExpired: 'Der Kauflink aus der App ist abgelaufen (15 Minuten). Öffne in der App erneut „Im Web aufladen“ – oder kauf hier einen Code.',
    linkUsed: 'Mit diesem Kauflink wurde schon bezahlt. Für einen weiteren Kauf öffne in der App erneut „Im Web aufladen“ – oder kauf hier einen Code.',
    linkInvalid: 'Dieser Kauflink ist ungültig. Öffne in der App „Im Web aufladen“ – oder kauf hier einen Code.',
    toCode: 'Stattdessen Code kaufen (Geschenk)', toCredit: 'Zurück: auf mein Konto laden',
    gives: function (e) { return e + ' Guthaben'; },
    honest: 'Endpreise inkl. USt. Du zahlst 10\u00a0€ und bekommst 10\u00a0€ Guthaben, ohne Abzug. USt, Zahlungsgebühr und unsere Marge stecken im Aufschlag auf die Modellpreise (69\u00a0%), den die App nennt. Details unter „Wie setzen sich die Preise zusammen?“ (Link unten).',
    pay: 'Weiter zur Zahlung', payBusy: 'Einen Moment …', payFailed: 'Die Zahlung konnte nicht gestartet werden. Bitte noch einmal versuchen.',
    mor: 'Die Zahlung wickelt Paddle als Verkäufer (Merchant of Record) ab, inklusive Umsatzsteuer und Beleg per E-Mail. overfit sieht keine Zahlungsdaten.',
    waitTitle: 'Zahlung wird bestätigt …', waitText: 'Das dauert meist nur ein paar Sekunden.',
    paidTitle: 'Danke!', paidCredit: function (e) { return e + ' Guthaben sind auf deinem Konto. Zurück in der App ist der neue Stand da.'; },
    paidCode: function (e) { return 'Dein Code über ' + e + ' Guthaben. Einlösen in der App: Guthaben → „Code einlösen“. Den Code siehst du nur hier, heb ihn gut auf.'; },
    slowTitle: 'Noch nicht bestätigt', slowText: 'Die Zahlung ist noch nicht bestätigt. Lass die Seite offen oder lade sie gleich neu.',
    refundedTitle: 'Erstattet', refundedText: 'Dieser Kauf wurde erstattet.',
    copy: 'Code kopieren', copied: 'Kopiert', back: 'Zurück zur App',
    imprint: 'Impressum (folgt vor dem Start)', privacy: 'Datenschutz (folgt vor dem Start)', terms: 'AGB und Widerruf (folgt vor dem Start)'
  } : {
    loading: 'Loading …', test: 'Test mode – no real payment', errTitle: 'Not available right now',
    errSetup: 'This page is not set up yet.', errOff: 'Credit on the web is not available right now. In the app you can keep using your own key or a code.',
    errNet: 'The server cannot be reached. Please try again later.',
    creditTitle: 'Top up credit', creditSub: 'The credit goes straight into your app. No account, no login.',
    codeTitle: 'Buy a credit code', codeSub: 'You get a code that you (or whoever you give it to) redeem in the app under Credit → “Redeem code”.',
    linkExpired: 'The purchase link from the app has expired (15 minutes). Open “Top up on the web” in the app again – or buy a code here.',
    linkUsed: 'This purchase link has already been paid. For another purchase open “Top up on the web” in the app again – or buy a code here.',
    linkInvalid: 'This purchase link is not valid. Open “Top up on the web” in the app – or buy a code here.',
    toCode: 'Buy a code instead (gift)', toCredit: 'Back: top up my account',
    gives: function (e) { return e + ' credit'; },
    honest: 'Final prices incl. VAT. You pay €10 and get €10 of credit, no deductions. VAT, payment fee and our margin are in the markup on model prices (69 %) that the app shows. Details under “How are the prices made up?” (link below).',
    pay: 'Continue to payment', payBusy: 'One moment …', payFailed: 'The payment could not be started. Please try again.',
    mor: 'Paddle handles the payment as the seller (merchant of record), including VAT and an email receipt. overfit never sees payment details.',
    waitTitle: 'Confirming payment …', waitText: 'This usually takes a few seconds.',
    paidTitle: 'Thank you!', paidCredit: function (e) { return e + ' of credit is on your account. Back in the app you will see the new balance.'; },
    paidCode: function (e) { return 'Your code for ' + e + ' of credit. Redeem it in the app: Credit → “Redeem code”. The code is only shown here, keep it safe.'; },
    slowTitle: 'Not confirmed yet', slowText: 'The payment is not confirmed yet. Keep this page open or reload it in a moment.',
    refundedTitle: 'Refunded', refundedText: 'This purchase was refunded.',
    copy: 'Copy code', copied: 'Copied', back: 'Back to the app',
    imprint: 'Imprint (coming before launch)', privacy: 'Privacy (coming before launch)', terms: 'Terms and withdrawal (coming before launch)'
  };

  var $ = function (id) { return document.getElementById(id); };
  document.documentElement.lang = lang;
  document.title = 'overfit – ' + (de ? 'Guthaben' : 'Credit');
  document.querySelectorAll('[data-i18n]').forEach(function (e) {
    var v = T[e.getAttribute('data-i18n')];
    if (typeof v === 'string') e.textContent = v;
  });

  function euro(micro) {
    return new Intl.NumberFormat(de ? 'de-DE' : 'en-IE', { style: 'currency', currency: 'EUR', minimumFractionDigits: micro % 1000000 === 0 ? 0 : 2 })
      .format(micro / 1000000);
  }
  function show(id) {
    ['loading', 'error', 'shop', 'done'].forEach(function (s) { $(s).hidden = s !== id; });
  }
  function fail(text) { $('errText').textContent = text; show('error'); }
  function api(path, body) {
    var init = body === undefined ? {} : { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) };
    return fetch(API + path, init).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) {
        if (!r.ok) { var e = new Error((j.error && j.error.code) || ('http_' + r.status)); e.code = e.message; throw e; }
        return j;
      });
    });
  }

  if (!API) { fail(T.errSetup); return; }
  if (orderId) { trackOrder(orderId); return; }

  // ---- Shop -------------------------------------------------------------------------------------------------
  var session = null;
  var kind = 'code';
  var selected = null;

  api('/v1/web/session', { token: token || undefined }).then(function (s) {
    session = s;
    $('test').hidden = !s.testMode;
    if (!s.enabled) { fail(T.errOff); return; }
    var status = s.link && s.link.status;
    kind = status === 'valid' ? 'credit' : 'code';
    if (status && status !== 'valid') {
      $('linkNote').textContent = status === 'expired' ? T.linkExpired : status === 'used' ? T.linkUsed : T.linkInvalid;
      $('linkNote').hidden = false;
    }
    selected = (s.packages[1] || s.packages[0] || {}).id || null;
    renderShop();
    show('shop');
  }).catch(function () { fail(T.errNet); });

  function renderShop() {
    $('shopTitle').textContent = kind === 'credit' ? T.creditTitle : T.codeTitle;
    $('shopSub').textContent = kind === 'credit' ? T.creditSub : T.codeSub;
    var canCredit = session.link && session.link.status === 'valid';
    $('switch').hidden = !canCredit;
    $('switch').textContent = kind === 'credit' ? T.toCode : T.toCredit;
    var packs = $('packs');
    packs.textContent = '';
    session.packages.forEach(function (p) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'pack';
      b.setAttribute('role', 'radio');
      b.setAttribute('aria-checked', String(p.id === selected));
      b.dataset.id = p.id;
      var left = document.createElement('span');
      var price = document.createElement('div');
      price.className = 'price';
      price.textContent = euro(p.priceMicro);
      var gives = document.createElement('div');
      gives.className = 'gives';
      gives.textContent = T.gives(euro(p.creditMicro));
      left.appendChild(price);
      left.appendChild(gives);
      var dot = document.createElement('span');
      dot.className = 'dot';
      b.appendChild(left);
      b.appendChild(dot);
      b.addEventListener('click', function () { selected = p.id; renderShop(); });
      packs.appendChild(b);
    });
    $('pay').disabled = !selected;
  }

  $('switch').addEventListener('click', function () { kind = kind === 'credit' ? 'code' : 'credit'; renderShop(); });

  $('pay').addEventListener('click', function () {
    if (!selected) return;
    var btn = $('pay');
    btn.disabled = true;
    btn.textContent = T.payBusy;
    $('payError').hidden = true;
    api('/v1/web/checkout', { token: kind === 'credit' ? token : undefined, packageId: selected, kind: kind, locale: lang })
      .then(function (co) { return openCheckout(co); })
      .catch(function (e) {
        btn.disabled = false;
        btn.textContent = T.pay;
        var code = e && e.code;
        if (code === 'link_expired' || code === 'link_used' || code === 'link_invalid') {
          session.link = { status: code.slice(5) };
          kind = 'code';
          $('linkNote').textContent = code === 'link_expired' ? T.linkExpired : code === 'link_used' ? T.linkUsed : T.linkInvalid;
          $('linkNote').hidden = false;
          renderShop();
          return;
        }
        $('payError').textContent = T.payFailed;
        $('payError').hidden = false;
      });
  });

  function loadPaddle() {
    if (window.Paddle) return Promise.resolve(window.Paddle);
    return new Promise(function (resolve, reject) {
      var s = document.createElement('script');
      s.src = PADDLE_JS;
      s.onload = function () { window.Paddle ? resolve(window.Paddle) : reject(new Error('paddle')); };
      s.onerror = function () { reject(new Error('paddle')); };
      document.head.appendChild(s);
    });
  }

  function openCheckout(co) {
    var successUrl = location.origin + location.pathname + '?order=' + encodeURIComponent(co.orderId) + '&lang=' + lang;
    try { sessionStorage.setItem('overfit.order', co.orderId); } catch (e) { /* privat */ }
    return loadPaddle().then(function (Paddle) {
      if (co.client.environment === 'sandbox') Paddle.Environment.set('sandbox');
      Paddle.Initialize({
        token: co.client.clientToken,
        eventCallback: function (ev) {
          if (ev && ev.name === 'checkout.completed') {
            Paddle.Checkout.close();
            location.replace(successUrl);
          }
          if (ev && ev.name === 'checkout.closed') {
            $('pay').disabled = false;
            $('pay').textContent = T.pay;
          }
        }
      });
      Paddle.Checkout.open({
        transactionId: co.transactionId,
        settings: { displayMode: 'overlay', theme: matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light', locale: lang, successUrl: successUrl, allowLogout: false }
      });
    });
  }

  // ---- Erfolgsseite --------------------------------------------------------------------------------------------
  function trackOrder(id) {
    show('done');
    $('doneTitle').textContent = T.waitTitle;
    $('doneText').textContent = T.waitText;
    $('back').hidden = true;
    var tries = 0;
    (function poll() {
      api('/v1/web/orders/' + encodeURIComponent(id)).then(function (o) {
        $('test').hidden = !o.testMode;
        if (o.status === 'open') {
          if (++tries >= 30) { $('doneTitle').textContent = T.slowTitle; $('doneText').textContent = T.slowText; return; }
          setTimeout(poll, 2000);
          return;
        }
        if (o.status !== 'paid') { $('doneTitle').textContent = T.refundedTitle; $('doneText').textContent = T.refundedText; return; }
        $('doneTitle').textContent = T.paidTitle;
        if (o.kind === 'code') {
          $('doneText').textContent = T.paidCode(euro(o.creditMicro));
          $('codeValue').textContent = o.code || '';
          $('codeBox').hidden = !o.code;
          $('back').hidden = false;
        } else {
          $('doneText').textContent = T.paidCredit(euro(o.creditMicro));
          $('back').hidden = false;
        }
      }).catch(function () {
        if (++tries >= 30) { fail(T.errNet); return; }
        setTimeout(poll, 2000);
      });
    })();
  }

  $('copy').addEventListener('click', function () {
    var text = $('codeValue').textContent;
    (navigator.clipboard ? navigator.clipboard.writeText(text) : Promise.reject()).then(function () {
      $('copy').textContent = T.copied;
    }).catch(function () {
      var r = document.createRange(); r.selectNodeContents($('codeValue'));
      var sel = getSelection(); sel.removeAllRanges(); sel.addRange(r);
    });
  });
})();
