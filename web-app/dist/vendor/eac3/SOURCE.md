# Dolby decoder sources and rebuilding

This unmodified decoder comes from audiojs/decode, commit
`60d3ccb7c8c77ec0ff7f2a7cae1fe4ec61ab301f`, package `@audio/decode-eac3` 1.0.0.
Upstream source: https://github.com/audiojs/decode/tree/60d3ccb7c8c77ec0ff7f2a7cae1fe4ec61ab301f/packages/decode-eac3

The FFmpeg libavcodec/libavutil source used by the build is the upstream
`lib/ffmpeg` submodule at commit `3978a28d5bdded4ce7eff2535c920326b5c8f2fc`.
Complete source archive: https://github.com/FFmpeg/FFmpeg/archive/3978a28d5bdded4ce7eff2535c920326b5c8f2fc.tar.gz
Source tree: https://github.com/FFmpeg/FFmpeg/tree/3978a28d5bdded4ce7eff2535c920326b5c8f2fc

The JavaScript wrapper, C shim (`src/eac3_glue.c`), build recipe (`build.sh`),
LGPL license (`LICENSE.ffmpeg`) and wrapper license (`LICENSE`) accompany this
distribution. The sole filename change is decode-eac3.js to decode-eac3.mjs.
No GPL components are enabled. FFmpeg is LGPL-2.1-or-later.

To rebuild with Emscripten activated:

```sh
git clone https://github.com/audiojs/decode.git
cd decode
git checkout 60d3ccb7c8c77ec0ff7f2a7cae1fe4ec61ab301f
git submodule update --init -- lib/ffmpeg
cd packages/decode-eac3
bash build.sh
```

Replace `src/eac3.wasm.js` with the resulting file to use a modified decoder.
The module is loaded separately by dynamic import, and is not embedded in IAMTT
application code. It can be replaced without rebuilding IAMTT.
