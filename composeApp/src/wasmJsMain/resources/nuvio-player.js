/*
 * Nuvio Web player: a thin layer over the HTML5 <video> element and hls.js,
 * driven by PlayerEngine.web.kt. Every function takes the <video> element.
 */
(function () {
  "use strict";

  function isHls(url, streamType) {
    if (streamType && /hls|m3u8/i.test(streamType)) return true;
    try {
      var path = new URL(url, location.href).pathname.toLowerCase();
      return path.endsWith(".m3u8") || path.endsWith(".m3u");
    } catch (e) {
      return /\.m3u8?(\?|$)/i.test(url);
    }
  }

  function state(video) {
    if (!video.__nuvio) video.__nuvio = { hls: null, error: null, subtitleBlobs: [], externalTrack: null, ended: false };
    return video.__nuvio;
  }

  function destroyHls(video) {
    var s = state(video);
    if (s.hls) {
      try { s.hls.destroy(); } catch (e) {}
      s.hls = null;
    }
  }

  var NuvioPlayer = {
    create: function () {
      var video = document.createElement("video");
      video.setAttribute("playsinline", "");
      video.setAttribute("preload", "auto");
      video.style.width = "100%";
      video.style.height = "100%";
      video.style.background = "#000";
      video.style.objectFit = "contain";
      video.style.display = "block";
      var s = state(video);
      video.addEventListener("error", function () {
        var err = video.error;
        s.error = err ? ("Media error " + err.code + (err.message ? ": " + err.message : "")) : "Media error";
      });
      video.addEventListener("ended", function () { s.ended = true; });
      video.addEventListener("playing", function () { s.ended = false; });
      return video;
    },

    load: function (video, url, streamType, startSeconds, autoplay) {
      var s = state(video);
      destroyHls(video);
      s.error = null;
      s.ended = false;
      var start = startSeconds > 0 ? startSeconds : 0;
      var useHls = isHls(url, streamType) && window.Hls && window.Hls.isSupported() &&
        !video.canPlayType("application/vnd.apple.mpegurl");
      if (useHls) {
        var hls = new window.Hls({ startPosition: start > 0 ? start : -1, enableWorker: true });
        s.hls = hls;
        hls.on(window.Hls.Events.ERROR, function (_, data) {
          if (!data || !data.fatal) return;
          if (data.type === window.Hls.ErrorTypes.MEDIA_ERROR) {
            try { hls.recoverMediaError(); return; } catch (e) {}
          }
          s.error = "HLS error: " + (data.details || data.type);
        });
        hls.loadSource(url);
        hls.attachMedia(video);
        if (autoplay) {
          hls.on(window.Hls.Events.MANIFEST_PARSED, function () { video.play().catch(function () {}); });
        }
      } else {
        video.src = url;
        if (start > 0) {
          video.addEventListener("loadedmetadata", function once() {
            video.removeEventListener("loadedmetadata", once);
            try { video.currentTime = start; } catch (e) {}
          });
        }
        video.load();
        if (autoplay) video.play().catch(function () {});
      }
    },

    release: function (video) {
      destroyHls(video);
      var s = state(video);
      s.subtitleBlobs.forEach(function (u) { URL.revokeObjectURL(u); });
      s.subtitleBlobs = [];
      try { video.pause(); } catch (e) {}
      video.removeAttribute("src");
      try { video.load(); } catch (e) {}
    },

    play: function (video) { video.play().catch(function () {}); },
    pause: function (video) { video.pause(); },
    seekTo: function (video, seconds) { try { video.currentTime = Math.max(0, seconds); } catch (e) {} },
    setRate: function (video, rate) { video.playbackRate = rate; },
    setMuted: function (video, muted) { video.muted = muted; },
    setFit: function (video, fit) { video.style.objectFit = fit; },

    /** "loading|playing|ended|durationMs|positionMs|bufferedMs|rate|width|height" */
    snapshot: function (video) {
      var s = state(video);
      var duration = isFinite(video.duration) ? video.duration : 0;
      var buffered = 0;
      try {
        for (var i = 0; i < video.buffered.length; i++) {
          if (video.buffered.start(i) <= video.currentTime && video.buffered.end(i) >= video.currentTime) {
            buffered = video.buffered.end(i);
          }
        }
      } catch (e) {}
      var loading = video.readyState < 3 && !video.paused && !s.ended;
      if (video.readyState < 1) loading = true;
      return [
        loading ? 1 : 0,
        (!video.paused && !s.ended) ? 1 : 0,
        s.ended ? 1 : 0,
        Math.round(duration * 1000),
        Math.round(video.currentTime * 1000),
        Math.round(buffered * 1000),
        video.playbackRate,
        video.videoWidth || 0,
        video.videoHeight || 0
      ].join("|");
    },

    takeError: function (video) {
      var s = state(video);
      var e = s.error;
      s.error = null;
      return e;
    },

    /** JSON array of {id,label,lang,selected} */
    audioTracks: function (video) {
      var s = state(video);
      var out = [];
      if (s.hls && s.hls.audioTracks) {
        s.hls.audioTracks.forEach(function (t, i) {
          out.push({ id: String(i), label: t.name || t.lang || ("Audio " + (i + 1)), lang: t.lang || "", selected: i === s.hls.audioTrack });
        });
      } else if (video.audioTracks) {
        for (var i = 0; i < video.audioTracks.length; i++) {
          var t = video.audioTracks[i];
          out.push({ id: String(i), label: t.label || t.language || ("Audio " + (i + 1)), lang: t.language || "", selected: !!t.enabled });
        }
      }
      return JSON.stringify(out);
    },

    selectAudioTrack: function (video, index) {
      var s = state(video);
      if (s.hls) { s.hls.audioTrack = index; return; }
      if (video.audioTracks) {
        for (var i = 0; i < video.audioTracks.length; i++) video.audioTracks[i].enabled = (i === index);
      }
    },

    /** JSON array of {id,label,lang,selected} for text tracks. */
    subtitleTracks: function (video) {
      var out = [];
      for (var i = 0; i < video.textTracks.length; i++) {
        var t = video.textTracks[i];
        if (t.kind !== "subtitles" && t.kind !== "captions") continue;
        out.push({ id: String(i), label: t.label || t.language || ("Subtitle " + (i + 1)), lang: t.language || "", selected: t.mode === "showing" });
      }
      return JSON.stringify(out);
    },

    selectSubtitleTrack: function (video, index) {
      for (var i = 0; i < video.textTracks.length; i++) {
        video.textTracks[i].mode = (i === index) ? "showing" : "disabled";
      }
    },

    /** Adds a WebVTT subtitle from text, selects it and returns its text track index. */
    addVttSubtitle: function (video, vttText, label, lang) {
      var s = state(video);
      if (s.externalTrack) { s.externalTrack.remove(); s.externalTrack = null; }
      var blobUrl = URL.createObjectURL(new Blob([vttText], { type: "text/vtt" }));
      s.subtitleBlobs.push(blobUrl);
      var track = document.createElement("track");
      track.kind = "subtitles";
      track.label = label || "External";
      track.srclang = lang || "und";
      track.src = blobUrl;
      track.default = true;
      video.appendChild(track);
      s.externalTrack = track;
      var index = -1;
      for (var i = 0; i < video.textTracks.length; i++) {
        if (video.textTracks[i] === track.track) index = i;
      }
      NuvioPlayer.selectSubtitleTrack(video, index);
      return index;
    },

    clearExternalSubtitle: function (video) {
      var s = state(video);
      if (s.externalTrack) { s.externalTrack.remove(); s.externalTrack = null; }
    },

    /** Applies a delay (ms) to the cues of the showing text track. */
    shiftSubtitles: function (video, deltaMs) {
      var d = deltaMs / 1000;
      for (var i = 0; i < video.textTracks.length; i++) {
        var t = video.textTracks[i];
        if (t.mode !== "showing" || !t.cues) continue;
        for (var j = 0; j < t.cues.length; j++) {
          t.cues[j].startTime += d;
          t.cues[j].endTime += d;
        }
      }
    },

    fullscreen: function (enabled) {
      try {
        if (enabled && !document.fullscreenElement) document.documentElement.requestFullscreen().catch(function () {});
        if (!enabled && document.fullscreenElement) document.exitFullscreen().catch(function () {});
      } catch (e) {}
    }
  };

  window.NuvioPlayer = NuvioPlayer;
})();
