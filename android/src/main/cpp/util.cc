#include "util.h"

#include <time.h>

#include <algorithm>
#include <cmath>
#include <vector>

namespace cardboard_jni {

namespace {
constexpr int64_t kNanosInSeconds = 1000000000;
}  // namespace

Matrix4x4 Matrix4x4::Identity() {
  Matrix4x4 r{};
  for (int i = 0; i < 4; ++i) r.m[i][i] = 1.0f;
  return r;
}

Matrix4x4 Matrix4x4::Translation(float x, float y, float z) {
  Matrix4x4 r = Identity();
  // Column-major: translation lives in the last column (m[3][*]).
  r.m[3][0] = x;
  r.m[3][1] = y;
  r.m[3][2] = z;
  return r;
}

Matrix4x4 Matrix4x4::Perspective(float fov_y, float aspect, float near,
                                 float far) {
  Matrix4x4 r{};  // zero-initialised
  const float f = 1.0f / std::tan(fov_y * 0.5f);
  r.m[0][0] = f / aspect;
  r.m[1][1] = f;
  r.m[2][2] = (far + near) / (near - far);
  r.m[2][3] = -1.0f;
  r.m[3][2] = (2.0f * far * near) / (near - far);
  // column-major: m[col][row]; the -1 that copies z into w is m[2][3].
  return r;
}

Matrix4x4 Matrix4x4::operator*(const Matrix4x4& right) const {
  Matrix4x4 result{};
  for (int col = 0; col < 4; ++col) {
    for (int row = 0; row < 4; ++row) {
      float sum = 0.0f;
      for (int k = 0; k < 4; ++k) {
        // this[k][row] * right[col][k] in column-major storage.
        sum += m[k][row] * right.m[col][k];
      }
      result.m[col][row] = sum;
    }
  }
  return result;
}

std::array<float, 16> Matrix4x4::ToGlArray() const {
  std::array<float, 16> a{};
  for (int col = 0; col < 4; ++col) {
    for (int row = 0; row < 4; ++row) {
      a[col * 4 + row] = m[col][row];
    }
  }
  return a;
}

Matrix4x4 GetMatrixFromGlArray(const float* vec) {
  Matrix4x4 r{};
  for (int col = 0; col < 4; ++col) {
    for (int row = 0; row < 4; ++row) {
      r.m[col][row] = vec[col * 4 + row];
    }
  }
  return r;
}

Quatf Quatf::FromXYZW(const float* q) { return Quatf{q[0], q[1], q[2], q[3]}; }

Quatf Quatf::Normalized() const {
  const float n = std::sqrt(x * x + y * y + z * z + w * w);
  if (n < 1e-8f) return Quatf{0.f, 0.f, 0.f, 1.f};
  return Quatf{x / n, y / n, z / n, w / n};
}

Quatf Quatf::Conjugate() const { return Quatf{-x, -y, -z, w}; }

// Hamilton product: (*this) composed with r, i.e. result rotates by `r` then by `*this`.
Quatf Quatf::operator*(const Quatf& r) const {
  return Quatf{
      w * r.x + x * r.w + y * r.z - z * r.y,
      w * r.y - x * r.z + y * r.w + z * r.x,
      w * r.z + x * r.y - y * r.x + z * r.w,
      w * r.w - x * r.x - y * r.y - z * r.z,
  };
}

Quatf Quatf::ScaledAngle(float s) const {
  // Decompose to axis-angle, scale the angle, recompose. Clamp w for acos safety.
  Quatf q = Normalized();
  // A quaternion and its negation are the same rotation; force the short arc (w >= 0) so the
  // recovered angle stays in [0, pi]. Without this, a w<0 input yields a >180° angle whose scaling
  // moves the OPPOSITE way (e.g. *2 looked like *0.5 for some frames).
  if (q.w < 0.f) q = Quatf{-q.x, -q.y, -q.z, -q.w};
  float cw = q.w < -1.f ? -1.f : (q.w > 1.f ? 1.f : q.w);
  const float angle = 2.0f * std::acos(cw);
  const float sin_half = std::sqrt(std::max(0.0f, 1.0f - cw * cw));
  if (sin_half < 1e-6f) return Quatf{0.f, 0.f, 0.f, 1.f};  // ~no rotation
  const float ax = q.x / sin_half, ay = q.y / sin_half, az = q.z / sin_half;
  const float new_half = (angle * s) * 0.5f;
  const float sh = std::sin(new_half);
  return Quatf{ax * sh, ay * sh, az * sh, std::cos(new_half)};
}

Matrix4x4 Quatf::ToMatrix() const {
  Matrix4x4 r = Matrix4x4::Identity();
  const float xx = x * x, yy = y * y, zz = z * z;
  const float xy = x * y, xz = x * z, yz = y * z;
  const float wx = w * x, wy = w * y, wz = w * z;
  // Column-major rotation matrix from a unit quaternion.
  r.m[0][0] = 1.0f - 2.0f * (yy + zz);
  r.m[0][1] = 2.0f * (xy + wz);
  r.m[0][2] = 2.0f * (xz - wy);
  r.m[1][0] = 2.0f * (xy - wz);
  r.m[1][1] = 1.0f - 2.0f * (xx + zz);
  r.m[1][2] = 2.0f * (yz + wx);
  r.m[2][0] = 2.0f * (xz + wy);
  r.m[2][1] = 2.0f * (yz - wx);
  r.m[2][2] = 1.0f - 2.0f * (xx + yy);
  return r;
}

int64_t GetBootTimeNano() {
  struct timespec res;
  clock_gettime(CLOCK_BOOTTIME, &res);
  return (res.tv_sec * kNanosInSeconds) + res.tv_nsec;
}

GLuint LoadGLShader(GLenum type, const char* shader_source) {
  GLuint shader = glCreateShader(type);
  glShaderSource(shader, 1, &shader_source, nullptr);
  glCompileShader(shader);
  GLint compile_status = 0;
  glGetShaderiv(shader, GL_COMPILE_STATUS, &compile_status);
  if (compile_status == 0) {
    GLint info_len = 0;
    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &info_len);
    if (info_len > 0) {
      std::vector<char> info(info_len);
      glGetShaderInfoLog(shader, info_len, nullptr, info.data());
      LOGE("Could not compile shader %d: %s", type, info.data());
    }
    glDeleteShader(shader);
    return 0;
  }
  return shader;
}

void CheckGlError(const char* file, int line, const char* label) {
  const GLenum error = glGetError();
  if (error != GL_NO_ERROR) {
    LOGE("GL error @ %s:%d (%s): 0x%x", file, line, label, error);
  }
}

}  // namespace cardboard_jni
