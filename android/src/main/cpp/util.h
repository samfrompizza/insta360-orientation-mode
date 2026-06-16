#ifndef PANORAMA_CARDBOARD_UTIL_H_
#define PANORAMA_CARDBOARD_UTIL_H_

#include <GLES2/gl2.h>

#include <array>
#include <cstdint>

#include <android/log.h>

#define LOG_TAG "CardboardJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define CHECKGL(label) CheckGlError(__FILE__, __LINE__, label)

namespace cardboard_jni {

// Column-major 4x4 matrix, matching OpenGL ES conventions. Ported from the
// Cardboard hello-cardboard sample's util.h (only the parts we use).
struct Matrix4x4 {
  float m[4][4];

  // Returns this * right.
  Matrix4x4 operator*(const Matrix4x4& right) const;

  // Flattened column-major array suitable for glUniformMatrix4fv.
  std::array<float, 16> ToGlArray() const;

  static Matrix4x4 Identity();
  static Matrix4x4 Translation(float x, float y, float z);
  // Standard GL perspective projection (column-major). fov_y in radians.
  static Matrix4x4 Perspective(float fov_y, float aspect, float near, float far);
};

// Reads a column-major 16-float GL array (e.g. from the Cardboard SDK) into a
// Matrix4x4.
Matrix4x4 GetMatrixFromGlArray(const float* vec);

// Quaternion in (x, y, z, w) layout — the order CardboardHeadTracker_getPose
// writes its `orientation` output.
struct Quatf {
  float x, y, z, w;
  static Quatf FromXYZW(const float* q);
  Matrix4x4 ToMatrix() const;

  Quatf Normalized() const;
  Quatf Conjugate() const;                 // inverse for a unit quaternion
  Quatf operator*(const Quatf& r) const;   // Hamilton product (this then r? see .cc)
  // Scales the rotation angle by `s` about the same axis (s>1 amplifies, s<1 damps).
  Quatf ScaledAngle(float s) const;
};

// CLOCK_BOOTTIME in nanoseconds, for the head-pose prediction timestamp.
int64_t GetBootTimeNano();

// Compiles a shader; returns 0 and logs on failure.
GLuint LoadGLShader(GLenum type, const char* shader_source);

// Logs (does not crash) on a pending GL error.
void CheckGlError(const char* file, int line, const char* label);

}  // namespace cardboard_jni

#endif  // PANORAMA_CARDBOARD_UTIL_H_
