package com.arashivision.sdk.demo.glide

import android.content.Context
import com.arashivision.sdkmedia.work.WorkWrapper
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.signature.ObjectKey
import java.io.InputStream

class WorkModelLoader internal constructor(private val mContext: Context) :
    ModelLoader<WorkWrapper, InputStream> {
    override fun buildLoadData(
        workWrapper: WorkWrapper,
        width: Int,
        height: Int,
        options: Options
    ): LoadData<InputStream> {
        println("workWrapper.identicalKey = " + workWrapper.identicalKey)
        val diskCacheKey: Key = ObjectKey(workWrapper.identicalKey)
        return LoadData(diskCacheKey, WorkDataFetcher(mContext, workWrapper))
    }

    override fun handles(workWrapper: WorkWrapper): Boolean {
        return true
    }
}
