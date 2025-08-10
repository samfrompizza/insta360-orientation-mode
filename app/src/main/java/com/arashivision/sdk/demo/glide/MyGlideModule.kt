package com.arashivision.sdk.demo.glide

import android.content.Context
import com.arashivision.sdkmedia.work.WorkWrapper
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import java.io.InputStream

@GlideModule
class MyGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        super.registerComponents(context, glide, registry)
        registry.prepend(
            WorkWrapper::class.java,
            InputStream::class.java,
            WorkModelLoaderFactory(context)
        )
    }
}
