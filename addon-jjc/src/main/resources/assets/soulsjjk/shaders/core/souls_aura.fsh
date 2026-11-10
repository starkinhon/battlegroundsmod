#version 150

// Writes the aura mask: the aura's color (already faded and scaled) and, in alpha, how far its glow reaches on
// screen. Skin pixels that are see-through (transparent hair pixels, empty outer layer) is left out so the outline follows the shape.
uniform sampler2D Sampler0;
uniform vec4 AuraColor;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord).a < 0.1) {
        discard;
    }
    fragColor = AuraColor;
}
