#include <jni.h>

#include "cardboard_renderer.h"
#include "util.h"

using cardboard_jni::CardboardRenderer;

namespace {
JavaVM* g_vm = nullptr;

inline CardboardRenderer* Native(jlong ptr) {
  return reinterpret_cast<CardboardRenderer*>(ptr);
}
}  // namespace

#define JNI_METHOD(return_type, method_name) \
  extern "C" JNIEXPORT return_type JNICALL   \
      Java_com_panorama_android_gl_CardboardVrView_##method_name

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
  g_vm = vm;
  return JNI_VERSION_1_6;
}

JNI_METHOD(jlong, nativeOnCreate)(JNIEnv* /*env*/, jobject /*obj*/, jobject context) {
  // Cardboard_initializeAndroid runs synchronously inside this JNI frame (the ctor completes before
  // we return), so the incoming local ref is valid for the whole call and the SDK keeps its own
  // reference — no NewGlobalRef needed (matches the SDK's hello_cardboard sample).
  auto* renderer = new CardboardRenderer(g_vm, context);
  return reinterpret_cast<jlong>(renderer);
}

JNI_METHOD(void, nativeOnDestroy)(JNIEnv* /*env*/, jobject /*obj*/, jlong ptr) {
  delete Native(ptr);
}

JNI_METHOD(void, nativeOnSurfaceCreated)(JNIEnv* /*env*/, jobject /*obj*/, jlong ptr) {
  Native(ptr)->OnSurfaceCreated();
}

JNI_METHOD(void, nativeSetScreenParams)
(JNIEnv* /*env*/, jobject /*obj*/, jlong ptr, jint width, jint height) {
  Native(ptr)->SetScreenParams(width, height);
}

JNI_METHOD(void, nativeSetOesTextureId)
(JNIEnv* /*env*/, jobject /*obj*/, jlong ptr, jint texture_id) {
  Native(ptr)->SetOesTextureId(texture_id);
}

JNI_METHOD(void, nativeSetStMatrix)
(JNIEnv* env, jobject /*obj*/, jlong ptr, jfloatArray matrix) {
  jfloat* m = env->GetFloatArrayElements(matrix, nullptr);
  Native(ptr)->SetStMatrix(m);
  env->ReleaseFloatArrayElements(matrix, m, JNI_ABORT);
}

JNI_METHOD(void, nativeOnDrawFrame)(JNIEnv* /*env*/, jobject /*obj*/, jlong ptr) {
  Native(ptr)->OnDrawFrame();
}

JNI_METHOD(void, nativeOnPause)(JNIEnv* /*env*/, jobject /*obj*/, jlong ptr) {
  Native(ptr)->OnPause();
}

JNI_METHOD(void, nativeOnResume)(JNIEnv* /*env*/, jobject /*obj*/, jlong ptr) {
  Native(ptr)->OnResume();
}

JNI_METHOD(void, nativeScanQrCode)(JNIEnv* /*env*/, jobject /*obj*/, jlong ptr) {
  Native(ptr)->ScanQrCode();
}
