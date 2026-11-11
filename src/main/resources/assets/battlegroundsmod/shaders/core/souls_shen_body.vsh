#version 150

// Shenlong's body, head, jaw, legs, spikes and whiskers (client/fx/ShenlongScene). Generated from the preview's
// CREATURE_V. Vanilla entity layout carries the preview's attributes:
//   UV0 = aP.xy, UV2.x = aP.z * 1000, Color.g = region / 15, UV1.x = arc * 64, UV2.y = radius * 1000.
// ModelMat moves a part into scene space (identity for the parts rebuilt every frame); ArcFix > -500 replaces the
// arc (the head, the jaw and the legs, drawn from one buffer each); Mirror -1 for a leg drawn mirrored.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// scene space (the preview's: the summoner at the origin looking +Z) -> camera-relative world
uniform mat4 SceneMat;
uniform mat4 ModelMat;
uniform float ArcFix;
uniform float Mirror;
out vec3 vN; out vec3 vW; out vec3 vP; out vec4 vR; out float vMirror;
void main(){
  vec4 w = ModelMat * vec4(Position, 1.0); vW = w.xyz; vN = normalize(mat3(ModelMat) * Normal);
  vP = vec3(UV0, float(UV2.x) / 1000.0);
  float arc = ArcFix > -500.0 ? ArcFix : float(UV1.x) / 64.0;
  vR = vec4(floor(Color.g * 15.0 + 0.5), float(UV2.y) / 1000.0, arc, 0.0);
  vMirror = Mirror;
  gl_Position = ProjMat * ModelViewMat * SceneMat * w;
}
