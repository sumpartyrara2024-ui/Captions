(function () {
  if (window.__capInit) return;
  window.__capInit = true;

  function send(type, value) {
    try { Captions.event(type, String(value)); } catch (e) {}
  }

  var ctx = null;
  var hooked = new WeakSet();

  function hook(video) {
    if (hooked.has(video)) return;
    hooked.add(video);
    try {
      var AC = window.AudioContext || window.webkitAudioContext;
      ctx = ctx || new AC();
      var src = ctx.createMediaElementSource(video);
      var analyser = ctx.createAnalyser();
      analyser.fftSize = 1024;
      src.connect(analyser);
      analyser.connect(ctx.destination); // keep sound audible
      var data = new Uint8Array(analyser.fftSize);
      var silentTicks = 0;

      setInterval(function () {
        if (ctx.state === 'suspended') { try { ctx.resume(); } catch (e) {} }
        if (video.paused || video.ended) { silentTicks = 0; return; }
        analyser.getByteTimeDomainData(data);
        var peak = 0;
        for (var i = 0; i < data.length; i++) {
          var d = Math.abs(data[i] - 128);
          if (d > peak) peak = d;
        }
        send('audio level', peak + ' / 128');
        if (peak === 0) {
          silentTicks++;
          if (silentTicks >= 6) {
            send('warning', 'playing but audio is silent (cross-origin or DRM?)');
          }
        } else {
          silentTicks = 0;
        }
      }, 500);

      send('hooked', 'video found, listening');
    } catch (e) {
      send('error', e && e.message ? e.message : e);
    }
  }

  function scan() {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) hook(vids[i]);
  }

  scan();
  setInterval(scan, 1500);
  send('inject', 'ready');
})();
