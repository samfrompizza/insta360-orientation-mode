#include "cardboard_renderer.h"

#include <array>

namespace cardboard_jni {

namespace {
constexpr float kZNear = 0.1f;
constexpr float kZFar = 100.0f;
constexpr float kMonoFovYRad = 1.3962634f;  // ~80° vertical FOV for the mono view
// Head-pose prediction horizon (motion-to-photon); matches the SDK sample.
constexpr int64_t kPredictionTimeNanos = 50000000;  // 50 ms
}  // namespace

CardboardRenderer::CardboardRenderer(JavaVM* vm, jobject context) {
  Cardboard_initializeAndroid(vm, context);
  head_tracker_ = CardboardHeadTracker_create();
  CardboardHeadTracker_setLowPassFilter(head_tracker_, 6);
  for (auto& m : eye_matrices_)
    for (float& f : m) f = 0.0f;
  for (auto& m : projection_matrices_)
    for (float& f : m) f = 0.0f;
  const std::array<float, 16> identity = Matrix4x4::Identity().ToGlArray();
  for (int i = 0; i < 16; ++i) st_matrix_[i] = identity[i];
}

CardboardRenderer::~CardboardRenderer() {
  GlTeardown();
  if (distortion_renderer_) CardboardDistortionRenderer_destroy(distortion_renderer_);
  if (lens_distortion_) CardboardLensDistortion_destroy(lens_distortion_);
  if (head_tracker_) CardboardHeadTracker_destroy(head_tracker_);
}

void CardboardRenderer::OnSurfaceCreated() {
  sphere_ready_ = sphere_.Init();
  if (!sphere_ready_) LOGE("Sphere mesh init failed");
  // A fresh GL context invalidates the FBO; rebuild lazily next draw.
  framebuffer_ = 0;
  fbo_color_texture_ = 0;
  depth_render_buffer_ = 0;
  device_params_changed_ = true;
}

void CardboardRenderer::SetScreenParams(int width, int height) {
  screen_width_ = width;
  screen_height_ = height;
  screen_params_changed_ = true;
}

void CardboardRenderer::SetStMatrix(const float* m) {
  for (int i = 0; i < 16; ++i) st_matrix_[i] = m[i];
}

void CardboardRenderer::OnPause() {
  if (head_tracker_) CardboardHeadTracker_pause(head_tracker_);
}

void CardboardRenderer::OnResume() {
  if (head_tracker_) CardboardHeadTracker_resume(head_tracker_);
  // Device params may have changed while paused (e.g. a QR scan).
  device_params_changed_ = true;
  // NOTE: do NOT reset the sensitivity pivot here — OnResume is called on every Compose recompose
  // (the view's update {} runs each frame), which would re-anchor ref_pose_ constantly and make the
  // measured head delta ~0, neutralising sensitivity. The pivot is set once on the first frame.
}

void CardboardRenderer::ScanQrCode() {
  CardboardQrCode_scanQrCodeAndSaveDeviceParams();
}

void CardboardRenderer::GlTeardown() {
  if (framebuffer_) {
    glDeleteFramebuffers(1, &framebuffer_);
    framebuffer_ = 0;
  }
  if (fbo_color_texture_) {
    glDeleteTextures(1, &fbo_color_texture_);
    fbo_color_texture_ = 0;
  }
  if (depth_render_buffer_) {
    glDeleteRenderbuffers(1, &depth_render_buffer_);
    depth_render_buffer_ = 0;
  }
}

void CardboardRenderer::GlSetup() {
  GlTeardown();

  glGenTextures(1, &fbo_color_texture_);
  glBindTexture(GL_TEXTURE_2D, fbo_color_texture_);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
  glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, screen_width_, screen_height_, 0,
               GL_RGB, GL_UNSIGNED_BYTE, nullptr);

  glGenRenderbuffers(1, &depth_render_buffer_);
  glBindRenderbuffer(GL_RENDERBUFFER, depth_render_buffer_);
  glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, screen_width_,
                        screen_height_);

  glGenFramebuffers(1, &framebuffer_);
  glBindFramebuffer(GL_FRAMEBUFFER, framebuffer_);
  glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                         fbo_color_texture_, 0);
  glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                            GL_RENDERBUFFER, depth_render_buffer_);
  glBindFramebuffer(GL_FRAMEBUFFER, 0);
  CHECKGL("GlSetup");

  // Both eyes sample halves of the one wide FBO texture.
  left_eye_texture_description_.texture = fbo_color_texture_;
  left_eye_texture_description_.left_u = 0.0f;
  left_eye_texture_description_.right_u = 0.5f;
  left_eye_texture_description_.top_v = 1.0f;
  left_eye_texture_description_.bottom_v = 0.0f;

  right_eye_texture_description_.texture = fbo_color_texture_;
  right_eye_texture_description_.left_u = 0.5f;
  right_eye_texture_description_.right_u = 1.0f;
  right_eye_texture_description_.top_v = 1.0f;
  right_eye_texture_description_.bottom_v = 0.0f;
}

bool CardboardRenderer::UpdateDeviceParams() {
  const int count = CardboardQrCode_getDeviceParamsChangedCount();
  if (count != last_device_params_count_) {
    device_params_changed_ = true;
    last_device_params_count_ = count;
  }
  if (!device_params_changed_ && !screen_params_changed_) return true;
  if (screen_width_ == 0 || screen_height_ == 0) return false;

  // Resolve the device params to build lens distortion from. Prefer a QR-scanned profile if one
  // was saved; otherwise fall back to the built-in Cardboard V1 profile so we render a real stereo
  // view instead of black. We deliberately do NOT persist the V1 fallback: saveDeviceParams expects
  // a QR *URI* (it hands the bytes to CardboardParamsUtils.saveParamsFromUri, which resolves them as
  // a URL), so feeding it raw decoded params just fails every frame and never writes the file.
  uint8_t* saved = nullptr;
  int saved_size = 0;
  CardboardQrCode_getSavedDeviceParams(&saved, &saved_size);

  const uint8_t* params = saved;
  int params_size = saved_size;
  if (saved_size == 0) {
    // getCardboardV1DeviceParams returns a pointer into a static vector — it lives for the process
    // lifetime and must NOT be destroyed (unlike the saved-params buffer, which is heap-allocated).
    uint8_t* v1 = nullptr;
    int v1_size = 0;
    CardboardQrCode_getCardboardV1DeviceParams(&v1, &v1_size);
    if (v1_size == 0) {
      CardboardQrCode_destroy(saved);
      return false;
    }
    params = v1;
    params_size = v1_size;
  }

  if (lens_distortion_) {
    CardboardLensDistortion_destroy(lens_distortion_);
    lens_distortion_ = nullptr;
  }
  lens_distortion_ = CardboardLensDistortion_create(params, params_size,
                                                    screen_width_, screen_height_);
  CardboardQrCode_destroy(saved);

  GlSetup();

  if (distortion_renderer_) {
    CardboardDistortionRenderer_destroy(distortion_renderer_);
    distortion_renderer_ = nullptr;
  }
  const CardboardOpenGlEsDistortionRendererConfig config{kGlTexture2D};
  distortion_renderer_ = CardboardOpenGlEs2DistortionRenderer_create(&config);

  CardboardMesh left_mesh;
  CardboardMesh right_mesh;
  CardboardLensDistortion_getDistortionMesh(lens_distortion_, kLeft, &left_mesh);
  CardboardLensDistortion_getDistortionMesh(lens_distortion_, kRight, &right_mesh);
  CardboardDistortionRenderer_setMesh(distortion_renderer_, &left_mesh, kLeft);
  CardboardDistortionRenderer_setMesh(distortion_renderer_, &right_mesh, kRight);

  CardboardLensDistortion_getEyeFromHeadMatrix(lens_distortion_, kLeft,
                                               eye_matrices_[0]);
  CardboardLensDistortion_getEyeFromHeadMatrix(lens_distortion_, kRight,
                                               eye_matrices_[1]);
  CardboardLensDistortion_getProjectionMatrix(lens_distortion_, kLeft, kZNear,
                                              kZFar, projection_matrices_[0]);
  CardboardLensDistortion_getProjectionMatrix(lens_distortion_, kRight, kZNear,
                                              kZFar, projection_matrices_[1]);

  screen_params_changed_ = false;
  device_params_changed_ = false;
  return true;
}

CardboardViewportOrientation CardboardRenderer::PoseOrientation() const {
  // VR is always landscape-locked. Mono follows the user's explicit portrait/landscape choice
  // (the screen is locked to it too), so the pose frame matches the screen — no 90° rotation.
  if (!mono_) return kLandscapeLeft;
  return mono_portrait_ ? kPortrait : kLandscapeLeft;
}

Quatf CardboardRenderer::CurrentPoseQuat() {
  std::array<float, 4> out_orientation{};
  std::array<float, 3> out_position{};
  CardboardHeadTracker_getPose(
      head_tracker_, GetBootTimeNano() + kPredictionTimeNanos, PoseOrientation(),
      out_position.data(), out_orientation.data());
  const Quatf current = Quatf::FromXYZW(out_orientation.data()).Normalized();

  // Sensitivity scales the head turn measured from the entry pose: delta = ref^-1 * current,
  // scaled about its axis, then re-anchored as ref * scaledDelta. At s==1 this is exactly current.
  if (!ref_pose_set_) {
    ref_pose_ = current;
    ref_pose_set_ = true;
  }
  if (sensitivity_ == 1.0f) return current;
  const Quatf delta = ref_pose_.Conjugate() * current;
  return (ref_pose_ * delta.ScaledAngle(sensitivity_)).Normalized();
}

Matrix4x4 CardboardRenderer::GetPose() { return CurrentPoseQuat().ToMatrix(); }

void CardboardRenderer::PoseQuat(float* out4) {
  // Export the SAME sensitivity-adjusted pose the view renders with, so the arrow (which reads this
  // via gazeRef) is computed from the exact orientation on screen.
  const Quatf q = CurrentPoseQuat();
  out4[0] = q.x;
  out4[1] = q.y;
  out4[2] = q.z;
  out4[3] = q.w;
}

void CardboardRenderer::SetVrParams(float eye_scale, float eye_gap) {
  eye_scale_ = eye_scale;
  eye_gap_ = eye_gap;
}

void CardboardRenderer::OnDrawFrame() {
  // Mono needs no lens profile; only the stereo path depends on device params.
  if (!mono_ && !UpdateDeviceParams()) {
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    return;
  }
  if (!sphere_ready_) return;

  const Matrix4x4 head_view = GetPose();

  if (mono_) {
    // Mono: draw the sphere once, straight to the display, with a plain perspective.
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glEnable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_SCISSOR_TEST);
    glViewport(0, 0, screen_width_, screen_height_);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    const float aspect =
        screen_height_ > 0 ? static_cast<float>(screen_width_) / screen_height_ : 1.0f;
    const Matrix4x4 projection =
        Matrix4x4::Perspective(kMonoFovYRad, aspect, /*near=*/kZNear, /*far=*/kZFar);
    const Matrix4x4 mvp = projection * head_view;
    const std::array<float, 16> mvp_gl = mvp.ToGlArray();
    sphere_.Draw(mvp_gl.data(), st_matrix_, oes_texture_id_);
    CHECKGL("OnDrawFrame(mono)");
    return;
  }

  glBindFramebuffer(GL_FRAMEBUFFER, framebuffer_);
  glEnable(GL_DEPTH_TEST);
  glDisable(GL_CULL_FACE);  // sphere is viewed from inside; keep all faces
  glDisable(GL_SCISSOR_TEST);
  glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
  glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

  const int half = screen_width_ / 2;
  // Each eye is drawn into a scaled box centred in its half, then the two halves are pushed apart by
  // eye_gap_ (as a fraction of half-width). eye_scale_ shrinks the box within its half.
  const int vw = static_cast<int>(half * eye_scale_);
  const int vh = static_cast<int>(screen_height_ * eye_scale_);
  const int gap = static_cast<int>(half * eye_gap_);
  const int cy = (screen_height_ - vh) / 2;
  for (int eye = 0; eye < 2; ++eye) {
    // Centre the box in its half, then shift left/right halves outward by the gap.
    const int half_origin = (eye == kLeft) ? 0 : half;
    const int cx = half_origin + (half - vw) / 2 + (eye == kLeft ? -gap : gap);
    glViewport(cx, cy, vw, vh);
    const Matrix4x4 eye_matrix = GetMatrixFromGlArray(eye_matrices_[eye]);
    const Matrix4x4 projection = GetMatrixFromGlArray(projection_matrices_[eye]);
    const Matrix4x4 mvp = projection * (eye_matrix * head_view);
    const std::array<float, 16> mvp_gl = mvp.ToGlArray();
    sphere_.Draw(mvp_gl.data(), st_matrix_, oes_texture_id_);
  }

  CardboardDistortionRenderer_renderEyeToDisplay(
      distortion_renderer_, /*target=*/0, /*x=*/0, /*y=*/0, screen_width_,
      screen_height_, &left_eye_texture_description_,
      &right_eye_texture_description_);
  CHECKGL("OnDrawFrame");
}

}  // namespace cardboard_jni
