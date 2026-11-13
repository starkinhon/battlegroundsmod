#version 150

// Hashirama's Mokuton wood (client/fx/HsWood), the approved page's WOOD_V and WOODS_V (forja/hashirama-boss/hashirama.js)
// in one shader. Geometry comes in its own space (page units); Model puts it camera-relative in blocks.
// Mode 0 = tube (the growing wood): Color.rg = u along the tube (16 bits), UV0 = (around, along) in page units,
//   UV1.xy/UV2.x = vertex minus its ring center x1024, so the tip can grow out of the center like the page.
// Mode 1 = carved surface (statue, fan, dragon head): Color.rgb = grain direction, Color.a = occlusion.
// Mode 2 = flat color (eyes, sockets): Color.rgb.
// Skin = 1: the geometry is in the dragon's body space (x, y round the axis, z along the body) and is laid on the trail
//   (Sampler0, RailN x 3 float texels: row 0 trail points, row 1 normals, row 2 binormals), the page's updateSkin.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 Model;
uniform float Mode;
uniform float Grow;
uniform float Tip;
uniform float Skin;
uniform float RailN;
uniform float BodyLen;
uniform sampler2D Sampler0;

out vec3 vN;
out vec3 vP;
out vec3 vV;
out vec3 vD;
out vec2 vB;
out float vU;
out float vK;
out float vO;
out vec4 vCol;

void main() {
    vec3 P = Position;
    vU = 0.;
    vK = 1.;
    vO = 1.;
    vD = vec3(0., 1., 0.);
    vB = UV0;
    vCol = Color;
    if (Mode < 0.5) {
        float u = (floor(Color.r * 255. + 0.5) * 256. + floor(Color.g * 255. + 0.5)) / 65535. * 2.;
        vec3 off = vec3(float(UV1.x), float(UV1.y), float(UV2.x)) / 1024.;
        vec3 C = P - off;
        float k = clamp((Grow - u) / max(Tip, 1e-4), 0., 1.);
        vK = k;
        P = C + off * sqrt(k);
        vU = u;
    } else if (Mode < 1.5 && Mode > 0.5) {
        vec3 d = Color.rgb * 2. - 1.;
        vD = dot(d, d) > 0.01 ? d : vec3(0., 1., 0.);
        vO = Color.a;
    }
    vec3 nrm = Normal;
    if (Skin > 0.5) {
        int n = int(RailN + 0.5);
        float uu = P.z / BodyLen, cu = clamp(uu, 0., 1.), f = cu * float(n - 1);
        int i = min(n - 2, int(floor(f)));
        float w = f - float(i);
        vec3 pi = texelFetch(Sampler0, ivec2(i, 0), 0).xyz, pj = texelFetch(Sampler0, ivec2(i + 1, 0), 0).xyz;
        vec3 T = pj - pi;
        float tl = max(length(T), 1e-5);
        T /= tl;
        float over = (uu - cu) * float(n - 1) * tl;
        vec3 Nr = mix(texelFetch(Sampler0, ivec2(i, 1), 0).xyz, texelFetch(Sampler0, ivec2(i + 1, 1), 0).xyz, w);
        vec3 Br = mix(texelFetch(Sampler0, ivec2(i, 2), 0).xyz, texelFetch(Sampler0, ivec2(i + 1, 2), 0).xyz, w);
        vec3 c = mix(pi, pj, w) + T * over;
        P = c + Nr * P.x + Br * P.y;
        nrm = Nr * Normal.x + Br * Normal.y + T * Normal.z;
    }
    vP = Position;
    vN = normalize(mat3(Model) * nrm);
    vec4 w = Model * vec4(P, 1.);
    vV = -w.xyz;
    gl_Position = ProjMat * ModelViewMat * w;
}
