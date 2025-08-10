package com.arashivision.sdk.demo.ui.album

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.coroutineScope
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.base.BaseFragment
import com.arashivision.sdk.demo.base.EventStatus
import com.arashivision.sdk.demo.databinding.FragmentAlbumBinding
import com.arashivision.sdk.demo.ui.album.adapter.AlbumAdapter
import com.arashivision.sdk.demo.ui.play.WorkPlayActivity
import com.arashivision.sdkmedia.work.WorkWrapper
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlbumFragment : BaseFragment<FragmentAlbumBinding, AlbumViewModel>() {
    private val logger: Logger = XLog.tag(AlbumFragment::class.java.simpleName).build()

    private var adapter: AlbumAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getAllWorks(false)
    }

    override fun initView() {
        super.initView()
        adapter = AlbumAdapter()
        binding.gvAlbum.setAdapter(adapter)

        binding.tabSource.addTab(binding.tabSource.newTab().setText(R.string.album_tab_source_camera))
        binding.tabSource.addTab(binding.tabSource.newTab().setText(R.string.album_tab_source_local))

        binding.tabType.addTab(binding.tabType.newTab().setText(R.string.album_tab_type_all))
        binding.tabType.addTab(binding.tabType.newTab().setText(R.string.album_tab_type_image))
        binding.tabType.addTab(binding.tabType.newTab().setText(R.string.album_tab_type_video))
    }

    override fun initListener() {
        super.initListener()
        adapter?.setItemClickListener { _, data, _ ->
            activity?.let { WorkPlayActivity.launch(it, data) }
        }

        adapter?.setItemLongClickListener { view, data, _ ->
            showMenu(view, data)
        }

        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.getAllWorks(true) }

        binding.tabSource.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.tabType.selectTab(binding.tabType.getTabAt(0))
                updateUi()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })
        binding.tabType.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateUi()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })
    }

    private fun updateUi() {
        val cameraWorks: List<WorkWrapper> = viewModel.mCameraWorks
        val localWorks: List<WorkWrapper> = viewModel.mLocalWorks

        val targetWorks: MutableList<WorkWrapper> = ArrayList()
        when (binding.tabSource.selectedTabPosition) {
            0 -> when (binding.tabType.selectedTabPosition) {
                0 -> targetWorks.addAll(cameraWorks)
                1 -> targetWorks.addAll(cameraWorks.filter { it.isPhoto })
                2 -> targetWorks.addAll(cameraWorks.filter { it.isVideo })
            }
            1 -> when (binding.tabType.selectedTabPosition) {
                0 -> targetWorks.addAll(localWorks)
                1 -> targetWorks.addAll(localWorks.filter { it.isPhoto })
                2 -> targetWorks.addAll(localWorks.filter { it.isVideo })
            }
        }
        if (targetWorks.isEmpty()) {
            binding.gvAlbum.visibility = View.GONE
            binding.ivEmpty.visibility = View.VISIBLE
        } else {
            binding.ivEmpty.visibility = View.GONE
            binding.gvAlbum.visibility = View.VISIBLE
        }
        adapter?.setData(targetWorks)
    }

    override fun onEvent(event: BaseEvent?) {
        super.onEvent(event)
        // 读取相机相册事件
        when (event) {
            is AlbumEvent.AlbumGetWorkWEvent -> {
                logger.d("     status:" + event.status + "   cameraWorks=" + event.cameraWorks + "   localWorks=" + event.localWorks)
                when (event.status) {
                    EventStatus.START -> showLoading(R.string.album_loading_works)
                    EventStatus.SUCCESS -> {
                        hideLoading()
                        binding.swipeRefreshLayout.isRefreshing = false
                        updateUi()
                    }
                    else -> {}
                }
            }

            // 删除事件
            is AlbumEvent.AlbumDeleteCameraFileEvent -> {
                logger.d("     status:" + event.status)
                when (event.status) {
                    EventStatus.START -> showLoading(R.string.album_deleting_works)
                    EventStatus.FAILED -> {
                        hideLoading()
                        toast(R.string.album_deleting_works_failed)
                    }
                    EventStatus.SUCCESS -> {
                        hideLoading()
                        toast(R.string.album_deleting_works_success)
                        updateUi()
                    }
                    else -> {}
                }
            }

            // 下载事件
            is AlbumEvent.AlbumDownloadCameraFileEvent -> {
                logger.d("     status:" + event.status)
                when (event.status) {
                    EventStatus.START -> showLoading()
                    EventStatus.FAILED -> {
                        hideLoading()
                        toast(R.string.album_downloading_works_failed)
                    }
                    EventStatus.SUCCESS -> {
                        hideLoading()
                        toast(R.string.album_downloading_works_success)
                        viewModel.getAllWorks(true)
                    }

                    EventStatus.PROGRESS -> {
                        showLoading(getString(R.string.album_downloading_works, event.index + 1, event.progress, event.speed))
                    }
                }
            }
        }
    }

    private fun showMenu(anchorView: View, workWrapper: WorkWrapper) {
        val popupMenu = PopupMenu(anchorView.context, anchorView)
        popupMenu.menuInflater.inflate(R.menu.menu_album, popupMenu.menu)
        // 本地文件或已下载文件，不可下载，下载选项禁用
        popupMenu.menu.findItem(R.id.menu_download).setEnabled(viewModel.canDownload(workWrapper))
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_download -> viewModel.downloadWorkWrapper(workWrapper)
                R.id.menu_delete -> viewModel.deleteFile(workWrapper)
                R.id.menu_share -> {
                    // TODO 待做
                }
            }
            false
        }
        popupMenu.show()
    }
}
