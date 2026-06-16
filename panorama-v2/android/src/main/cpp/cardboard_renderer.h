#ifndef PANORAMA_CARDBOARD_RENDERER_H_
#define PANORAMA_CARDBOARD_RENDERER_H_

#include <GLES2/gl2.h>
#include <jni.h>

#include "cardboard.h"
#include "sphere_mesh.h"
#include "util.h"

namespace cardboard_jni {

// Drives the VR split-screen render through the Cardboard SDK: head tracking,
// per-eye lens distortion, and the distortion pass. Renders the equirect sphere
// (sampling the video OES texture) into one wide FBO (left half = left eye,
// right half = right eye), then CardboardDistortionRenderer_renderEyeToDisplay
// warps both halves to the display.
//
// All methods run on the GLSurfaceView's GL thread except the ctor (constructed
// on the same thread the JNI call arrives on; Cardboard_initializeAndroid only
// needs the JavaVM + context). The OES texture id is created by Kotlin (it owns
// the SurfaceTexture handed to ExoPlayer) and injected via SetOesTextureId.
class CardboardRenderer {
 public:
  CardboardRenderer(JavaVM* vm, jobject context);
  ~CardboardRenderer();

  void OnSurfaceCreated();
  void SetScreenParams(int width, int height);
  void SetOesTextureId(int texture_id) { oes_texture_id_ = texture_id; }
  void SetStMatrix(const float* m);  // 16 floats, column-major
  void OnDrawFrame();
  void OnPause();
  void OnResume();
  void ScanQrCode();

 private:
  bool UpdateDeviceParams();
  void GlSetup();
  void GlTeardown();
  Matrix4x4 GetPose();

  CardboardHeadTracker* head_tracker_ = nullptr;
  CardboardLensDistortion* lens_distortion_ = nullptr;
  CardboardDistortionRenderer* distortion_renderer_ = nullptr;

  SphereMesh sphere_;
  bool sphere_ready_ = false;

  GLuint framebuffer_ = 0;
  GLuint fbo_color_texture_ = 0;
  GLuint depth_render_buffer_ = 0;

  CardboardEyeTextureDescription left_eye_texture_description_{};
  CardboardEyeTextureDescription right_eye_texture_description_{};

  float eye_matrices_[2][16];
  float projection_matrices_[2][16];
  float st_matrix_[16];

  GLuint oes_texture_id_ = 0;
  int screen_width_ = 0;
  int screen_height_ = 0;
  bool screen_params_changed_ = false;
  bool device_params_changed_ = true;
  int last_device_params_count_ = 0;
};

}  // namespace cardboard_jni

#endif  // PANORAMA_CARDBOARD_RENDERER_H_
