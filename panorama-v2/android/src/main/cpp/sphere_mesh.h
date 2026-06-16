#ifndef PANORAMA_CARDBOARD_SPHERE_MESH_H_
#define PANORAMA_CARDBOARD_SPHERE_MESH_H_

#include <GLES2/gl2.h>

namespace cardboard_jni {

// An equirectangular UV sphere viewed from the inside, textured by the video's
// OES external texture. Self-contained (the SDK sample builds its own meshes the
// same way); does not reuse :core's JVM-side EquirectProjection.
class SphereMesh {
 public:
  SphereMesh() = default;
  ~SphereMesh();

  // Compiles the samplerExternalOES program and uploads the sphere VBO/IBO.
  // Must run on the GL thread with a current context. Returns false on failure.
  bool Init();

  // Draws the sphere sampling `oes_texture_id`. `mvp` is a column-major 16-float
  // model-view-projection; `st_matrix` is the SurfaceTexture transform (4x4)
  // applied to UVs so the decoder's transform (incl. any flip) is respected.
  void Draw(const float* mvp, const float* st_matrix, GLuint oes_texture_id);

 private:
  GLuint program_ = 0;
  GLint a_position_ = -1;
  GLint a_uv_ = -1;
  GLint u_mvp_ = -1;
  GLint u_st_matrix_ = -1;
  GLint u_texture_ = -1;

  GLuint vbo_positions_ = 0;
  GLuint vbo_uvs_ = 0;
  GLuint ibo_ = 0;
  int index_count_ = 0;
};

}  // namespace cardboard_jni

#endif  // PANORAMA_CARDBOARD_SPHERE_MESH_H_
