#version 150

// The storm sky (SKY_V of the preview): a dome round the camera, pushed to the far plane so it only covers the sky.
in vec3 Position;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 SceneRot;
out vec3 vD;
void main(){ vD = normalize(Position); vec4 p = ProjMat * ModelViewMat * SceneRot * vec4(Position, 1.0); gl_Position = p.xyww; }
