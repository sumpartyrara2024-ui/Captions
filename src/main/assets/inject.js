(function () {
  if (window.__capInit) return;
  window.__capInit = true;

  var CHUNK_SECONDS = 6;
  var MIN_RMS = 0.004;
  var OUT_RATE = 16000;

  function send(type, value) {
    try { Captions.event(type, String(value)); } catch (e) {}
  }

  function downsample(buf, inRate, outRate) {
    if (outRate >= inRate) return buf;
    var ratio = inRate / outRate;
    var n = Math.floor(buf.length / ratio);
    var out = new Float32Array(n);
    for (var i = 0; i < n; i++) {
      var start = Math.floor(i * ratio);
      var end = Math.floor((i + 1) * ratio);
      var sum = 0, cnt = 0;
      for (var j = start; j < end && j < buf.length; j++) { sum += buf[j]; cnt++; }
      out[i] = cnt ? sum / cnt : 0;
    }
    return out;
  }

  function toWavBase64(samples, rate) {
    var buffer = new ArrayBuffer(44 + samples.length * 2);
    var v = new DataView(buffer);
    function w(o, s) { for (var i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); }
    w(0, 'RIFF'); v.setUint32(4, 36 + samples.length * 2, true); w(8, 'WAVE'); w(12, 'fmt ');
    v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
    v.setUint32(24, rate, true); v.setUint32(28, rate * 2, true);
    v.setUint16(32, 2, true); v.setUint16(34, 16, true);
    w(36, 'data'); v.setUint32(40, samples.length * 2, true);
    for (var i = 0; i < samples.length; i++) {
      var s = Math.max(-1, Math.min(1, samples[i]));
      v.setInt16(44 + i * 2, s < 0 ? s * 0x8000 : s * 0x7FFF, true);
    }
    var bytes = new Uint8Array(buffer);
    var bin = '';
    var CH = 0x8000;
    for (var k = 0; k < bytes.length; k += CH) {
      bin += String.fromCharCode.apply(null, bytes.subarray(k, k + CH));
    }
    return btoa(bin);
  }

  var ctx = null;
  var hooked = new WeakSet();

  function flush(all) {
    var sum = 0;
    for (var i = 0; i < all.length; i++) sum += all[i] * all[i];
    var rms = Math.sqrt(sum / all.length);
    if (rms < MIN_RMS) { send('skipped', 'chunk too quiet'); return; }
    var pcm = downsample(all, ctx.sampleRate, OUT_RATE);
    try {
      Captions.audio(toWavBase64(pcm, OUT_RATE));
      send('sent', 'audio chunk');
    } catch (e) {
      send('error', e && e.message ? e.message : e);
    }
  }

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
      analyser.connect(ctx.destination);
      var data = new Uint8Array(analyser.fftSize);
      var silentTicks = 0;

      var proc = ctx.createScriptProcessor(4096, 1, 1);
      var mute = ctx.createGain();
      mute.gain.value = 0;
      src.connect(proc);
      proc.connect(mute);
      mute.connect(ctx.destination);

      var acc = [];
      var accLen = 0;
      proc.onaudioprocess = function (e) {
        if (video.paused || video.ended) return;
        var input = e.inputBuffer.getChannelData(0);
        acc.push(new Float32Array(input));
        accLen += input.length;
        if (accLen >= ctx.sampleRate * CHUNK_SECONDS) {
          var all = new Float32Array(accLen);
          var off = 0;
          for (var i = 0; i < acc.length; i++) { all.set(acc[i], off); off += acc[i].length; }
          acc = [];
          accLen = 0;
          flush(all);
        }
      };

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
