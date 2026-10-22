#version 150

// Full-screen pass for the mod's screen effects: Position.xy goes from 0 to 1 across the screen.
in vec3 Position;

out vec2 uv;

void main() {
    uv = Position.xy;
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
}
