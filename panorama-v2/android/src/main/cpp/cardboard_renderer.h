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
  // Writes the current head-tracker orientation as a quaternion (x,y,z,w) into out4. This is the
  // same pose that drives the rendered view, so the off-screen arrow can stay in lock-step with it.
  void PoseQuat(float* out4);
  // Per-eye viewport tuning. eye_scale in (0,1] shrinks each eye within its half (1 = full half);
  // eye_gap in [0,1) pushes the two halves apart from the centre as a fraction of half-width.
  void SetVrParams(float eye_scale, float eye_gap);
  // Head-turn gain about the entry pose: >1 amplifies, <1 damps. 1 = raw Cardboard tracking.
  void SetSensitivity(float s) { sensitivity_ = s; }
  // When true, render one full-screen view (no stereo split, no lens distortion). Switching modes
  // re-anchors the sensitivity pivot so "forward" is wherever the head is on entering the new mode.
  void SetMonoMode(bool mono) {
    if (mono != mono_) ref_pose_set_ = false;
    mono_ = mono;
  }
  // Mono screen orientation chosen by the user (true = portrait, false = landscape). VR ignores it.
  void SetMonoPortrait(bool portrait) { mono_portrait_ = portrait; }

 private:
  bool UpdateDeviceParams();
  void GlSetup();
  void GlTeardown();
  Matrix4x4 GetPose();
  // Screen orientation to query the head pose in: VR is locked landscape; mono follows the live
  // aspect ratio (portrait when taller than wide) so the view never sits 90° off.
  CardboardViewportOrientation PoseOrientation() const;

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

  float eye_scale_ = 1.0f;  // per-eye viewport size within its half (1 = full)
  float eye_gap_ = 0.0f;    // extra split between the two halves, fraction of half-width

  float sensitivity_ = 1.0f;       // head-turn gain about the entry pose
  Quatf ref_pose_{0.f, 0.f, 0.f, 1.f};  // entry orientation (sensitivity pivot)
  bool ref_pose_set_ = false;
  bool mono_ = false;          // full-screen single view vs split-screen stereo
  bool mono_portrait_ = true;  // mono screen orientation (portrait vs landscape)

  GLuint oes_texture_id_ = 0;
  int screen_width_ = 0;
  int screen_height_ = 0;
  bool screen_params_changed_ = false;
  bool device_params_changed_ = true;
  int last_device_params_count_ = 0;
};

}  // namespace cardboard_jni

#endif  // PANORAMA_CARDBOARD_RENDERER_H_
