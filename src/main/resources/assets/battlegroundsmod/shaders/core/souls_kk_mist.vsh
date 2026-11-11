#version 150

// The chakra mist round the Susanoo's feet (forja/kakashi-kit MIST_F): the box from BoxMin to BoxMax in the page's
// world units, raymarched from the camera (CamObj, in those same units). Model as in souls_kk_sus.
in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 Model;
uniform vec3 BoxMin;
uniform vec3 BoxMax;

out vec3 vW;

void main() {
    vW = mix(BoxMin, BoxMax, Position * 0.5 + 0.5);
    gl_Position = ProjMat * ModelViewMat * Model * vec4(vW, 1.);
}
