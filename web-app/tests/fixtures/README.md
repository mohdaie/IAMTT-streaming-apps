`dolby.mkv.base64` is a generated, three-second black video with a 440 Hz sine
wave only in the centre channel of a Dolby Digital Plus 5.1 soundtrack. It
contains no movie content. Reproduce it with FFmpeg:

```sh
ffmpeg -f lavfi -i color=c=black:size=32x32:rate=10 \
  -f lavfi -i 'aevalsrc=0|0|0.12*sin(2*PI*440*t)|0|0|0:s=48000:d=3:channel_layout=5.1' \
  -t 3 -c:v libx264 -preset ultrafast -g 10 -c:a eac3 -b:a 192k \
  -cluster_time_limit 1000 dolby.mkv
base64 -w0 dolby.mkv > dolby.mkv.base64
```
