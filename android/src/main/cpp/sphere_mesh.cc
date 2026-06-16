#include "sphere_mesh.h"

#include <GLES2/gl2ext.h>

#include <cmath>
#include <cstdint>
#include <vector>

#include "util.h"

namespace cardboard_jni {

namespace {

constexpr int kStacks = 64;   // latitude divisions
constexpr int kSlices = 128;  // longitude divisions
constexpr float kRadius = 50.0f;
constexpr float kPi = 3.14159265358979323846f;

// Vertex shader: applies MVP to position and the SurfaceTexture transform to UV.
const char* kVertexShader = R"glsl(
attribute vec3 a_Position;
attribute vec2 a_UV;
uniform mat4 u_MVP;
uniform mat4 u_StMatrix;
varying vec2 v_UV;
void main() {
  gl_Position = u_MVP * vec4(a_Position, 1.0);
  v_UV = (u_StMatrix * vec4(a_UV, 0.0, 1.0)).xy;
}
)glsl";

// Fragment shader: samples the video OES external texture.
const char* kFragmentShader = R"glsl(
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES u_Texture;
varying vec2 v_UV;
void main() {
  gl_FragColor = texture2D(u_Texture, v_UV);
}
)glsl";

}  // namespace

SphereMesh::~SphereMesh() {
  if (program_) glDeleteProgram(program_);
  if (vbo_positions_) glDeleteBuffers(1, &vbo_positions_);
  if (vbo_uvs_) glDeleteBuffers(1, &vbo_uvs_);
  if (ibo_) glDeleteBuffers(1, &ibo_);
}

bool SphereMesh::Init() {
  // --- program ---
  const GLuint vs = LoadGLShader(GL_VERTEX_SHADER, kVertexShader);
  const GLuint fs = LoadGLShader(GL_FRAGMENT_SHADER, kFragmentShader);
  if (vs == 0 || fs == 0) return false;
  program_ = glCreateProgram();
  glAttachShader(program_, vs);
  glAttachShader(program_, fs);
  glLinkProgram(program_);
  GLint linked = 0;
  glGetProgramiv(program_, GL_LINK_STATUS, &linked);
  glDeleteShader(vs);
  glDeleteShader(fs);
  if (!linked) {
    LOGE("Sphere program link failed");
    glDeleteProgram(program_);
    program_ = 0;
    return false;
  }
  a_position_ = glGetAttribLocation(program_, "a_Position");
  a_uv_ = glGetAttribLocation(program_, "a_UV");
  u_mvp_ = glGetUniformLocation(program_, "u_MVP");
  u_st_matrix_ = glGetUniformLocation(program_, "u_StMatrix");
  u_texture_ = glGetUniformLocation(program_, "u_Texture");

  // --- mesh: equirect UV sphere, inward-facing ---
  std::vector<float> positions;
  std::vector<float> uvs;
  std::vector<uint16_t> indices;
  positions.reserve((kStacks + 1) * (kSlices + 1) * 3);
  uvs.reserve((kStacks + 1) * (kSlices + 1) * 2);

  for (int stack = 0; stack <= kStacks; ++stack) {
    const float v = static_cast<float>(stack) / kStacks;  // 0..1 top->bottom
    const float phi = v * kPi;                            // 0..pi (latitude)
    const float sin_phi = std::sin(phi);
    const float cos_phi = std::cos(phi);
    for (int slice = 0; slice <= kSlices; ++slice) {
      const float u = static_cast<float>(slice) / kSlices;  // 0..1 longitude
      const float theta = u * 2.0f * kPi;
      const float sin_theta = std::sin(theta);
      const float cos_theta = std::cos(theta);
      // Point on the sphere.
      positions.push_back(kRadius * sin_phi * sin_theta);  // x
      positions.push_back(kRadius * cos_phi);              // y
      positions.push_back(-kRadius * sin_phi * cos_theta); // z
      // Equirect UV: u = longitude, v = latitude. The OES video frame arrives V-flipped (texture
      // origin at the bottom), so the top of the sphere (phi=0) samples from 1-v to render the
      // panorama right-side-up instead of upside down.
      uvs.push_back(u);
      uvs.push_back(1.0f - v);
    }
  }

  const int stride = kSlices + 1;
  for (int stack = 0; stack < kStacks; ++stack) {
    for (int slice = 0; slice < kSlices; ++slice) {
      const uint16_t a = static_cast<uint16_t>(stack * stride + slice);
      const uint16_t b = static_cast<uint16_t>(a + stride);
      // Inward-facing winding (camera sits inside the sphere).
      indices.push_back(a);
      indices.push_back(static_cast<uint16_t>(a + 1));
      indices.push_back(b);
      indices.push_back(b);
      indices.push_back(static_cast<uint16_t>(a + 1));
      indices.push_back(static_cast<uint16_t>(b + 1));
    }
  }
  index_count_ = static_cast<int>(indices.size());

  glGenBuffers(1, &vbo_positions_);
  glBindBuffer(GL_ARRAY_BUFFER, vbo_positions_);
  glBufferData(GL_ARRAY_BUFFER, positions.size() * sizeof(float),
               positions.data(), GL_STATIC_DRAW);

  glGenBuffers(1, &vbo_uvs_);
  glBindBuffer(GL_ARRAY_BUFFER, vbo_uvs_);
  glBufferData(GL_ARRAY_BUFFER, uvs.size() * sizeof(float), uvs.data(),
               GL_STATIC_DRAW);

  glGenBuffers(1, &ibo_);
  glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo_);
  glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.size() * sizeof(uint16_t),
               indices.data(), GL_STATIC_DRAW);

  glBindBuffer(GL_ARRAY_BUFFER, 0);
  glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
  CHECKGL("SphereMesh::Init");
  return true;
}

void SphereMesh::Draw(const float* mvp, const float* st_matrix,
                      GLuint oes_texture_id) {
  glUseProgram(program_);
  glUniformMatrix4fv(u_mvp_, 1, GL_FALSE, mvp);
  glUniformMatrix4fv(u_st_matrix_, 1, GL_FALSE, st_matrix);

  glActiveTexture(GL_TEXTURE0);
  glBindTexture(GL_TEXTURE_EXTERNAL_OES, oes_texture_id);
  glUniform1i(u_texture_, 0);

  glBindBuffer(GL_ARRAY_BUFFER, vbo_positions_);
  glEnableVertexAttribArray(a_position_);
  glVertexAttribPointer(a_position_, 3, GL_FLOAT, GL_FALSE, 0, nullptr);

  glBindBuffer(GL_ARRAY_BUFFER, vbo_uvs_);
  glEnableVertexAttribArray(a_uv_);
  glVertexAttribPointer(a_uv_, 2, GL_FLOAT, GL_FALSE, 0, nullptr);

  glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo_);
  glDrawElements(GL_TRIANGLES, index_count_, GL_UNSIGNED_SHORT, nullptr);

  glDisableVertexAttribArray(a_position_);
  glDisableVertexAttribArray(a_uv_);
  glBindBuffer(GL_ARRAY_BUFFER, 0);
  glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
  CHECKGL("SphereMesh::Draw");
}

}  // namespace cardboard_jni
