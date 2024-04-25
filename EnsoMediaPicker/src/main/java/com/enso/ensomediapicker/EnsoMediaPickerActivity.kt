package com.enso.ensomediapicker

import android.annotation.SuppressLint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.enso.ensoimagepicker.databinding.ActivityEnsoMediaPickerBinding
import com.enso.ensomediapicker.adapter.MediaListAdapter
import com.enso.ensomediapicker.adapter.SelectMediaListAdapter
import com.enso.ensomediapicker.model.MediaInfo
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EnsoMediaPickerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEnsoMediaPickerBinding
    private lateinit var mediaListAdapter: MediaListAdapter
    private lateinit var selectMediaListAdapter: SelectMediaListAdapter
    private val selectedMediaUris = MutableStateFlow<List<Uri>>(emptyList())

    private var mediaGridLayoutManager: GridLayoutManager? = null
    private var selectMediaLinearLayoutManager: LinearLayoutManager? = null

    private lateinit var bottomSheetSelectedBehavior: BottomSheetBehavior<LinearLayout>

    private var isMultiSelect: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnsoMediaPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUi()
        initListeners()

        loadMediaList(savedInstanceState)
        loadSelectMediaPreviewListFromBundle(savedInstanceState)

        bottomSheetSelectedBehavior.state = savedInstanceState?.getInt(BOTTOM_SHEET_STATE, BottomSheetBehavior.STATE_COLLAPSED) ?: BottomSheetBehavior.STATE_COLLAPSED
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mediaGridLayoutManager?.let { outState.putInt(MEDIA_SCROLL_STATE, it.findFirstVisibleItemPosition()) }
        selectMediaLinearLayoutManager?.let { outState.putInt(SELECT_MEDIA_SCROLL_STATE, it.findFirstVisibleItemPosition()) }
        outState.putParcelableArrayList(MEDIA_LIST, ArrayList(mediaListAdapter.currentList))
        outState.putParcelable(MEDIA_SCROLL_STATE, binding.rvMediaList.layoutManager?.onSaveInstanceState())
        outState.putInt(BOTTOM_SHEET_STATE, bottomSheetSelectedBehavior.state)
        outState.putParcelableArrayList(SELECTED_MEDIA_LIST, ArrayList(selectedMediaUris.value))

        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        Glide.get(this@EnsoMediaPickerActivity).clearMemory()
        super.onDestroy()
    }

    private fun setupUi() {
        binding.apply {
            bottomSheetSelectedBehavior = BottomSheetBehavior.from(llBottomSheetSelected)
            bottomSheetSelectedBehavior.addBottomSheetCallback(object :
                BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_DRAGGING && selectMediaListAdapter.itemCount > 0) {
                        Log.d("whk__" , "${selectMediaListAdapter.itemCount}")
                        bottomSheetSelectedBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val bottomSheetVisibleHeight = slideOffset * llBottomSheetSelected.height
                    val contentFrameParams = llRoot.layoutParams
                    contentFrameParams.height = resources.displayMetrics.heightPixels - bottomSheetVisibleHeight.toInt()
                    llRoot.layoutParams = contentFrameParams

                    if (selectedMediaUris.value.isNotEmpty()) {
                        mediaGridLayoutManager?.findLastVisibleItemPosition()
                            ?.let { lastVisiblePosition ->
                                binding.root.post {
                                    if (mediaListAdapter.itemCount == lastVisiblePosition + 1) {
                                        binding.rvMediaList.smoothScrollToPosition(mediaListAdapter.itemCount - 1)
                                    }
                                }
                            }
                    }
                }
            })

            rvMediaList.apply {
                var gridColumns = 3
                val minGridSize = 400
                val screenWidth = resources.displayMetrics.widthPixels

                if ((screenWidth - GRID_SPACING) / minGridSize > gridColumns) {
                    gridColumns = (screenWidth - GRID_SPACING) / minGridSize
                }

                mediaListAdapter = MediaListAdapter(
                    isMultiSelect = true,
                    gridColumns = gridColumns,
                    totalSpacing = (gridColumns - 1) * GRID_SPACING
                )
                mediaGridLayoutManager = GridLayoutManager(this@EnsoMediaPickerActivity, gridColumns)
                layoutManager = mediaGridLayoutManager
                itemAnimator = null
                adapter = mediaListAdapter
                addItemDecoration(GridSpacingItemDecoration(gridColumns, GRID_SPACING))
            }

            rvSelectedMediaList.apply {
                selectMediaListAdapter = SelectMediaListAdapter()
                selectMediaLinearLayoutManager = LinearLayoutManager(this@EnsoMediaPickerActivity, RecyclerView.HORIZONTAL, false)
                layoutManager = selectMediaLinearLayoutManager
                adapter = selectMediaListAdapter
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initListeners() {
        with (binding) {
            flCloseButton.setOnClickListener {
                finish()
            }

            mediaListAdapter.setMediaListListener(object : MediaListAdapter.MediaListListener {
                override fun onItemClick(position: Int, mediaInfo: MediaInfo) {
                    selectMediaFromMediaList(position, mediaInfo)
                }

                override fun onItemLongClick(position: Int, mediaInfo: MediaInfo) {
                    isMultiSelect = true
                    autoSelectStartPosition = position
                    autoSelectorStartPositionState = mediaInfo.isSelect
                    binding.rvMediaList.onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0))
                }
            })

            selectMediaListAdapter.setSelectMediaListener(object : SelectMediaListAdapter.SelectMediaListener {
                override fun onItemClick(uri: Uri) {
                    selectMediaFromSelectedList(uri)
                }
            })

            rvMediaList.setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    when (event?.action) {
                        MotionEvent.ACTION_UP -> {
                            if (isMultiSelect) {
                                isMultiSelect = false
                                autoSelectStartPosition = -1
                                lastAutoFocusPosition = -1
                                Log.d("whk__", "종료")
                            }
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (isMultiSelect) {
                                if (event.y <= mediaListAdapter.getItemViewHeight() && rvMediaList.canScrollVertically(-1)) {
                                    autoScrollByY(mediaListAdapter.getItemViewHeight() * -1)
                                } else if (event.y >= (rvMediaList.height - mediaListAdapter.getItemViewHeight()) && binding.rvMediaList.canScrollVertically(1)) {
                                    autoScrollByY(mediaListAdapter.getItemViewHeight())
                                }

                                rvMediaList.findChildViewUnder(event.x, event.y)?.let { view ->
                                    val focusPosition = rvMediaList.getChildAdapterPosition(view)
                                    if (lastAutoFocusPosition != focusPosition) {
                                        if (autoSelectStartPosition > focusPosition) { // 터치를 위로 이동 했을 때
                                            if (lastAutoFocusPosition > focusPosition) {
                                                for (position in lastAutoFocusPosition downTo focusPosition) {
                                                    val mediaInfo = mediaListAdapter.currentList[position]
                                                    if (autoSelectorStartPositionState == mediaInfo.isSelect) {
                                                        selectMediaFromMediaList(position, mediaInfo)
                                                    }
                                                }
                                            } else {
                                                for (position in lastAutoFocusPosition .. focusPosition) {
                                                    val mediaInfo = mediaListAdapter.currentList[position]
                                                    if (autoSelectorStartPositionState != mediaInfo.isSelect) {
                                                        selectMediaFromMediaList(position, mediaInfo)
                                                    }
                                                }
                                            }
                                        } else if (autoSelectStartPosition < focusPosition) { // 터치를 아래로 이동 했을 때
                                            if (lastAutoFocusPosition < focusPosition) {
                                                for (position in lastAutoFocusPosition..focusPosition) {
                                                    val mediaInfo = mediaListAdapter.currentList[position]
                                                    if (autoSelectorStartPositionState == mediaInfo.isSelect) {
                                                        selectMediaFromMediaList(position, mediaInfo)
                                                    }
                                                }
                                            } else {
                                                for (position in lastAutoFocusPosition downTo focusPosition) {
                                                    val mediaInfo = mediaListAdapter.currentList[position]
                                                    if (autoSelectorStartPositionState != mediaInfo.isSelect) {
                                                        selectMediaFromMediaList(position, mediaInfo)
                                                    }
                                                }
                                            }
                                        } else {
                                            if (lastAutoFocusPosition > 0) {
                                                if (lastAutoFocusPosition - focusPosition < 0) {
                                                    for (position in lastAutoFocusPosition until focusPosition) {
                                                        Log.d("whk__", "position : $position")
                                                        val mediaInfo = mediaListAdapter.currentList[position]
                                                        if (autoSelectorStartPositionState != mediaInfo.isSelect) {
                                                            selectMediaFromMediaList(position, mediaInfo)
                                                        }
                                                    }
                                                } else {
                                                    for (position in focusPosition+1 ..  lastAutoFocusPosition) {
                                                        val mediaInfo = mediaListAdapter.currentList[position]
                                                        if (autoSelectorStartPositionState != mediaInfo.isSelect) {
                                                            selectMediaFromMediaList(position, mediaInfo)
                                                        }
                                                    }
                                                }
                                            }

                                            Log.d("whk__", "후헤헿")
                                            if (autoSelectorStartPositionState == mediaListAdapter.currentList[focusPosition].isSelect) {
                                                selectMediaFromMediaList(focusPosition, mediaListAdapter.currentList[focusPosition])
                                            }
                                        }

                                        lastAutoFocusPosition = focusPosition
                                    }
                                }
                                return true
                            }
                        }
                    }

                    return false
                }

            })
        }
    }

    private var autoSelectStartPosition = -1
    private var autoSelectorStartPositionState = false
    private var lastAutoFocusPosition = -1
    private var isAutoScrollEnabled = true

    private fun autoScrollByY(y: Int) {
        if (isAutoScrollEnabled) {
            isAutoScrollEnabled = false
            binding.rvMediaList.smoothScrollBy(0, y, null, 600)
            lifecycleScope.launch {
                delay(400)
                isAutoScrollEnabled = true
            }
        }
    }

    private fun loadSelectMediaPreviewListFromBundle(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            val selectMediaList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState?.getParcelableArrayList(SELECTED_MEDIA_LIST, Uri::class.java)
            } else {
                savedInstanceState?.getParcelableArrayList<Uri>(SELECTED_MEDIA_LIST)
            }

            if (selectMediaList != null) {
                selectedMediaUris.value = selectMediaList
                selectMediaListAdapter.submitList(selectMediaList) {
                    binding.rvSelectedMediaList.post {
                        restoreSelectMediaListScrollPosition(savedInstanceState)
                    }
                }
            }
        }
    }

    private fun loadMediaList(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            val mediaList = getMediaList(savedInstanceState)
            mediaListAdapter.submitList(mediaList) {
                binding.rvMediaList.post {
                    restoreMediaListScrollPosition(savedInstanceState)
                }
            }
        }
    }

    private suspend fun getMediaList(savedInstanceState: Bundle?): List<MediaInfo>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState?.getParcelableArrayList(MEDIA_LIST, MediaInfo::class.java)?.let { mediaInfoList ->
                updateSelectedMediaUris(mediaInfoList)
                mediaInfoList
            } ?: MediaSearchUtil.getMediaFromGallery(this@EnsoMediaPickerActivity, null)
        } else {
            savedInstanceState?.getParcelableArrayList<MediaInfo>(MEDIA_LIST)?.let { mediaInfoList ->
                updateSelectedMediaUris(mediaInfoList)
                mediaInfoList
            } ?: MediaSearchUtil.getMediaFromGallery(this@EnsoMediaPickerActivity, null)
        }
    }

    private fun updateSelectedMediaUris(mediaInfoList: List<MediaInfo>) {
        mediaInfoList.filter { it.isSelect }.forEach { mediaInfo ->
            mediaInfo.selectIndex?.let { selectIndex ->
                val currentList = selectedMediaUris.value.toMutableList()
                if (currentList.size >= selectIndex) {
                    currentList.add(0, mediaInfo.uri)
                } else {
                    currentList.add(mediaInfo.uri)
                }
                selectedMediaUris.value = currentList
            }
        }
    }

    private fun restoreMediaListScrollPosition(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState?.getParcelable(MEDIA_SCROLL_STATE, Parcelable::class.java)?.let { scrollPosition ->
                binding.rvMediaList.layoutManager?.onRestoreInstanceState(scrollPosition)
            }
        } else {
            (savedInstanceState?.getParcelable(MEDIA_SCROLL_STATE) as? Parcelable)?.let {
                binding.rvMediaList.layoutManager?.onRestoreInstanceState(it)
            }
        }
    }

    private fun restoreSelectMediaListScrollPosition(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState?.getParcelable(SELECT_MEDIA_SCROLL_STATE, Parcelable::class.java)?.let { scrollPosition ->
                binding.rvSelectedMediaList.layoutManager?.onRestoreInstanceState(scrollPosition)
            }
        } else {
            (savedInstanceState?.getParcelable(SELECT_MEDIA_SCROLL_STATE) as? Parcelable)?.let {
                binding.rvSelectedMediaList.layoutManager?.onRestoreInstanceState(it)
            }
        }
    }

    private fun selectMediaFromMediaList(position: Int, mediaInfo: MediaInfo) {
        val currentList = selectedMediaUris.value.toMutableList()
        if (mediaInfo.isSelect) {
            currentList.remove(mediaInfo.uri)
        } else {
            if (currentList.size >= MAX_SELECT_COUNT) {
                Toast.makeText(this, "최대 ${MAX_SELECT_COUNT}개까지 선택 가능합니다.", Toast.LENGTH_SHORT).show()
                return
            }
            currentList.add(mediaInfo.uri)
        }
        selectedMediaUris.value = currentList

        lifecycleScope.launch {
            mediaListAdapter.submitList(updateMediaList(position, mediaInfo))

            selectedMediaUris.value.let { selectedUris ->
                selectMediaListAdapter.submitList(selectedUris) {
                    binding.rvSelectedMediaList.post {
                        val scrollToPosition = selectMediaListAdapter.itemCount - 1
                        if (scrollToPosition >= 0) {
                            binding.rvSelectedMediaList.smoothScrollToPosition(scrollToPosition)
                        }

                        updateSelectedPreviewBottomSheet()
                    }
                }
            }
        }
    }

    private fun selectMediaFromSelectedList(uri: Uri) {
        val currentList = selectedMediaUris.value.toMutableList()
        currentList.remove(uri)
        selectedMediaUris.value = currentList

        lifecycleScope.launch {
            val mediaInfo = mediaListAdapter.currentList.find { it.uri == uri } ?: return@launch
            val position = mediaListAdapter.currentList.indexOfFirst { it.uri == uri }
            if (position >= 0) {
                mediaListAdapter.submitList(updateMediaList(position, mediaInfo.copy(isSelect = false)))
            }

            selectedMediaUris.value.let { selectedUris ->
                selectMediaListAdapter.submitList(selectedUris) {
                    binding.rvSelectedMediaList.post {
                        updateSelectedPreviewBottomSheet()
                    }
                }
            }
        }
    }

    private fun updateSelectedPreviewBottomSheet() {
        if (selectMediaListAdapter.itemCount > 0) {
            bottomSheetSelectedBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        } else {
            bottomSheetSelectedBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private suspend fun updateMediaList(position: Int, mediaInfo: MediaInfo): List<MediaInfo> {
        return withContext(Dispatchers.Default) {
            val currentList = mediaListAdapter.currentList.toMutableList()
            if (position >= 0 && position < currentList.size) {
                currentList[position] = mediaInfo.copy(isSelect = !mediaInfo.isSelect)
            }

            val selectedUris = selectedMediaUris.value
            currentList.map { media ->
                val selectIndex = selectedUris.indexOf(media.uri)
                if (selectIndex != -1) {
                    media.copy(isSelect = true, selectIndex = selectIndex + 1)
                } else {
                    media.copy(isSelect = false, selectIndex = null)
                }
            }
        }
    }

    inner class GridSpacingItemDecoration(private val spanCount: Int, private val spacing: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            val column = position % spanCount

            outRect.left = if (column == 0) 0 else spacing / 2
            outRect.right = if (column == spanCount - 1) 0 else spacing / 2
            outRect.top = if (position < spanCount) 0 else spacing
            outRect.bottom = 0
        }
    }

    companion object {
        private const val MEDIA_SCROLL_STATE = "MEDIA_SCROLL_STATE"
        private const val SELECT_MEDIA_SCROLL_STATE = "SELECT_MEDIA_SCROLL_STATE"
        private const val MEDIA_LIST = "MEDIA_LIST"
        private const val SELECTED_MEDIA_LIST = "SELECTED_MEDIA_LIST"
        private const val BOTTOM_SHEET_STATE = "BOTTOM_SHEET_STATE"
        private const val MAX_SELECT_COUNT = 99
        private const val GRID_SPACING = 10
    }
}