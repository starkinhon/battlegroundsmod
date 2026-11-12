#version 150

// Halos, the eyes' glow and the pillar of light (GLOW_V of the preview). UV0 = the sphere/cylinder's uv.
in vec3 Position;
in vec2 UV0;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// scene space (the preview's: the summoner at the origin looking +Z) -> camera-relative world
uniform mat4 SceneMat;
uniform mat4 ModelMat;
out vec3 vN; out vec3 vW; out vec2 vUv;
void main(){ vUv = UV0; vec4 w = ModelMat * vec4(Position, 1.0); vW = w.xyz; vN = normalize(mat3(ModelMat) * Normal);
  gl_Position = ProjMat * ModelViewMat * SceneMat * w; }
