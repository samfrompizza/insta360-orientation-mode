package com.arashivision.sdk.demo.ui.play.dialog

import android.annotation.SuppressLint
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseDialog
import com.arashivision.sdkcamera.camera.model.RecordResolution

class ExportDialog : BaseDialog() {

    private var tvTitle: TextView? = null
    private var tvOperate: TextView? = null
    private var tvProgress: TextView? = null
    private var tvMessage: TextView? = null
    private var progressBar: ProgressBar? = null

    private var progress: Int = 0

    private var onCancel: (() -> Unit)? = null

    override fun layoutResId(): Int {
        return R.layout.dialog_exporting
    }

    @SuppressLint("SetTextI18n")
    override fun initView(view: View) {
        tvTitle = view.findViewById(R.id.tv_title)
        tvOperate = view.findViewById(R.id.tv_operate)
        tvProgress = view.findViewById(R.id.tv_progress)
        progressBar = view.findViewById(R.id.progressBar)
        tvMessage = view.findViewById(R.id.tv_message)

        progressBar?.progress = 0
        tvProgress?.text = "$progress%"
        tvTitle?.text = getString(R.string.export_dialog_exporting)
    }

    override fun initListener() {
        tvOperate?.setOnClickListener {
            dismissAllowingStateLoss()
            if (progress == -1 || progress == 100) {
                // TODO 待做
            } else {
                dismissAllowingStateLoss()
                onCancel?.invoke()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun setProgress(progress: Int) {
        this.progress = progress
        tvProgress?.text = "$progress%"
        progressBar?.progress = progress
    }

    fun setSuccess(msg: String) {
        progress = 100
        tvTitle?.text = getString(R.string.export_dialog_export_success)
        tvOperate?.text = getString(R.string.dialog_positive_text)

        tvProgress?.visibility = View.INVISIBLE
        progressBar?.visibility = View.INVISIBLE
        tvMessage?.visibility = View.VISIBLE

        tvMessage?.text = msg
    }

    fun setError(msg: String) {
        progress = -1
        tvTitle?.text = getString(R.string.export_dialog_export_failed)
        tvOperate?.text = getString(R.string.dialog_positive_text)

        tvProgress?.visibility = View.INVISIBLE
        progressBar?.visibility = View.INVISIBLE
        tvMessage?.visibility = View.VISIBLE
        tvMessage?.text = msg
    }

    fun setOnCancelExportListener(onCancel: () -> Unit) {
        this.onCancel = onCancel
    }
}