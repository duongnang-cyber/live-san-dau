precision mediump float;
uniform sampler2D uVideo;
uniform sampler2D uBoard;
uniform sampler2D uTicker;
uniform bool uShowBoard;
uniform bool uShowTicker;
varying vec2 vTextureCoord;
void main() {
  vec4 video = texture2D(uVideo, vTextureCoord);
  vec2 topUV = vec2(vTextureCoord.x, 1.0 - vTextureCoord.y);
  vec4 board = vec4(0.0);
  if (uShowBoard) board = texture2D(uBoard, topUV);
  // Android Bitmap pixels are premultiplied; do not multiply RGB by alpha twice.
  vec3 color = board.rgb + video.rgb * (1.0 - board.a);
  if (uShowTicker) {
    vec4 ticker = texture2D(uTicker, topUV);
    color = ticker.rgb + color * (1.0 - ticker.a);
  }
  gl_FragColor = vec4(color, 1.0);
}
