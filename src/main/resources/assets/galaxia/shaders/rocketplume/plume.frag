#version 330 core

in vec3 fragNormal;
out vec4 fragColor;

const vec3 lightDir = normalize(vec3(1.0, 2.0, 1.0));

void main() {
    float diff = max(dot(fragNormal, lightDir), 0.0);
    float shade = 0.6 + 0.1 * diff; // 0.3 ambient, 0.7 diffuse
    fragColor = vec4(vec3(shade), 1.0);
}
