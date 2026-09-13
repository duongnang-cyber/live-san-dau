#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform sampler2D uVideo;
uniform samplerExternalOES uReplay;
uniform sampler2D uLabel;
uniform sampler2D uTransition;
uniform mat4 uReplayMatrix;
uniform bool uPlaying;
uniform int uStyle;
uniform float uEnterTime;
uniform float uExitTime;
varying vec2 vTextureCoord;
void main() {
  if (!uPlaying) {
    gl_FragColor = texture2D(uVideo, vTextureCoord);
    return;
  }
  vec2 replayUV = (uReplayMatrix * vec4(vTextureCoord, 0.0, 1.0)).xy;
  vec3 liveColor = texture2D(uVideo, vTextureCoord).rgb;
  vec3 replayColor = texture2D(uReplay, replayUV).rgb;
  vec3 color = replayColor;
  if (uStyle == 1) {
    float amount = smoothstep(0.0, 0.35, uEnterTime);
    if (uExitTime >= 0.0) amount *= 1.0 - smoothstep(0.0, 0.35, uExitTime);
    color = mix(liveColor, replayColor, amount);
  } else if (uStyle == 2) {
    vec3 graphic = texture2D(uTransition, vec2(vTextureCoord.x, 1.0 - vTextureCoord.y)).rgb;
    if (uEnterTime < 0.38) {
      color = mix(liveColor, graphic, smoothstep(0.0, 0.28, uEnterTime));
    } else {
      float diagonal = vTextureCoord.x * 0.72 + (1.0 - vTextureCoord.y) * 0.28;
      float wipe = smoothstep(-0.08, 0.08, (uEnterTime - 0.38) / 0.62 - diagonal);
      color = mix(graphic, replayColor, wipe);
    }
    if (uExitTime >= 0.0) {
      float diagonalOut = vTextureCoord.x * 0.72 + vTextureCoord.y * 0.28;
      float revealLive = smoothstep(-0.08, 0.08, uExitTime / 0.50 - diagonalOut);
      color = mix(color, liveColor, revealLive);
    }
  }
  vec2 topUV = vec2(vTextureCoord.x, 1.0 - vTextureCoord.y);
  if ((uStyle != 2 || uEnterTime >= 0.88) && topUV.x >= 0.84 && topUV.x <= 0.97 && topUV.y >= 0.03 && topUV.y <= 0.0855) {
    vec4 badge = texture2D(uLabel, vec2((topUV.x - 0.84) / 0.13, (topUV.y - 0.03) / 0.0555));
    color = badge.rgb + color * (1.0 - badge.a);
  }
  gl_FragColor = vec4(color, 1.0);
}
