package com.arashivision.sdk.demo.util

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.ParameterizedType

object ViewBindingUtils {

    @Suppress("UNCHECKED_CAST")
    fun <T> createBinding(cls: Class<*>, layoutInflater: LayoutInflater?, index: Int, viewGroup: ViewGroup?): T {
        try {
            val tClass = getParameterizedTypeClass(cls, index)
            return viewGroup?.let {
                val method = tClass.getMethod(
                    "inflate",
                    LayoutInflater::class.java,
                    ViewGroup::class.java,
                    Boolean::class.javaPrimitiveType
                )
                method.invoke(null, layoutInflater, viewGroup, false) as T
            } ?: run {
                val method = tClass.getMethod("inflate", LayoutInflater::class.java)
                method.invoke(null, layoutInflater) as T

            }
        } catch (e: NoSuchMethodException) {
            throw RuntimeException(e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException(e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : ViewModel> createViewModel(owner: ViewModelStoreOwner, index: Int): T {
        try {
            val tClass = getParameterizedTypeClass(owner.javaClass, index) as Class<T>
            return ViewModelProvider(owner)[tClass]
        } catch (e: Exception) {
            throw RuntimeException(e.message)
        }
    }

    private fun getParameterizedTypeClass(cls: Class<*>, index: Int): Class<*> {
        val parameterizedType = cls.genericSuperclass as ParameterizedType
        val actualTypeArguments = parameterizedType.actualTypeArguments
        val tClass = actualTypeArguments[index] as Class<*>
        return tClass
    }
}
