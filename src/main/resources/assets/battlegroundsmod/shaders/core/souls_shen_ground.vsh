#version 150

// The balls' glow and the lightning's flash on the real ground (GROUND_V of the preview), a disc just above it.
in vec3 Position;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// scene space (the preview's: the summoner at the origin looking +Z) -> camera-relative world
uniform mat4 SceneMat;
out vec3 vW;
void main(){ vW = Position; gl_Position = ProjMat * ModelViewMat * SceneMat * vec4(Position, 1.0); }
