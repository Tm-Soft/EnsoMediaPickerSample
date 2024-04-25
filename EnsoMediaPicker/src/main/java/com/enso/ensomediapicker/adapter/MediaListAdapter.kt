package com.enso.ensomediapicker.adapter

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.enso.ensoimagepicker.R
import com.enso.ensomediapicker.MediaSearchUtil
import com.enso.ensomediapicker.model.MediaInfo
import com.enso.ensomediapicker.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MediaListAdapter(
    private val isMultiSelect: Boolean,
    private val gridColumns: Int,
    private val totalSpacing: Int
) : ListAdapter<MediaInfo, MediaListAdapter.MediaViewHolder>(DiffCallback) {
    private var context: Context? = null
    private var listener: MediaListListener? = null
    private var itemHeight = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        context = context ?: parent.context
        val itemView = LayoutInflater.from(context).inflate(R.layout.item_media_grid, parent, false)
        itemHeight = ((parent.width - totalSpacing) / gridColumns)
        itemView.layoutParams.height = itemHeight
        return MediaViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val mediaInfo = getItem(holder.adapterPosition)
        holder.bind(mediaInfo)
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        holder.unbind()
    }

    fun getItemViewHeight(): Int {
        return itemHeight
    }

    fun setMediaListListener(listener: MediaListListener) {
        this.listener = listener
    }

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        private val videoGuideCardView: CardView = itemView.findViewById(R.id.cv_video_guide)
        private val extensionGuideCardView: CardView = itemView.findViewById(R.id.cv_extension_guide)
        private val videoDurationTextView: TextView = itemView.findViewById(R.id.tv_video_duration)
        private val extensionTextView: TextView = itemView.findViewById(R.id.tv_extension)
        private val dimView: View = itemView.findViewById(R.id.view_dim)
        private val flSelectedOrder: FrameLayout = itemView.findViewById(R.id.fl_selected_order)
        private val tvSelectedOrder: TextView = itemView.findViewById(R.id.tv_selected_order)

        private var guideJob: Job? = null

        init {
            itemView.setOnClickListener {
                listener?.onItemClick(adapterPosition, getItem(adapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener))
            }

            itemView.setOnLongClickListener {
                listener?.onItemLongClick(
                    adapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnLongClickListener false,
                    getItem(adapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnLongClickListener false)
                )
                true
            }

        }

        fun bind(mediaInfo: MediaInfo) {
            context?.let { context ->
                when (mediaInfo.mediaType) {
                    MediaType.VIDEO -> {
                        videoGuideCardView.isVisible = true
                        extensionGuideCardView.isVisible = false
                        guideJob?.cancel()
                        guideJob = CoroutineScope(Dispatchers.Main).launch {
                            videoDurationTextView.text = formatDuration(
                                MediaSearchUtil.getVideoDuration(
                                    context,
                                    mediaInfo.uri
                                )
                            )
                        }
                    }
                    else -> {
                        videoGuideCardView.isVisible = false
                        guideJob?.cancel()
                        guideJob = CoroutineScope(Dispatchers.Main).launch {
                            MediaSearchUtil.getFileExtensionFromUri(context, mediaInfo.uri)
                                ?.let { extension ->
                                extensionGuideCardView.isVisible = extension == "gif"
                                extensionTextView.text = if (extension == "gif") "GIF" else ""
                            }
                        }
                    }
                }
                Glide.with(context)
                    .load(mediaInfo.uri)
                    .override(thumbnailImageView.width/2, thumbnailImageView.height/2)
                    .skipMemoryCache(false)
                    .thumbnail(0.5f)
                    .centerCrop()
                    .into(thumbnailImageView)
            }

            if (isMultiSelect) {
                dimView.isVisible = mediaInfo.isSelect
                flSelectedOrder.isVisible = true
                tvSelectedOrder.text =
                    if (mediaInfo.isSelect) mediaInfo.selectIndex.toString() else ""

                val backgroundColor = if (mediaInfo.isSelect) SELECT_COLOR else UNSELECT_COLOR
                (flSelectedOrder.background as? GradientDrawable)?.setColor(backgroundColor)
            } else {
                flSelectedOrder.isVisible = false
            }
        }

        fun unbind() {
            guideJob?.cancel()
        }

        private suspend fun formatDuration(durationMillis: Long): String = withContext(Dispatchers.Default) {
            val totalSeconds = durationMillis / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60

            if (minutes >= 100) "99:99+" else String.format("%02d:%02d", minutes, seconds)
        }
    }

    interface MediaListListener {
        fun onItemClick(position: Int, mediaInfo: MediaInfo)
        fun onItemLongClick(position: Int, mediaInfo: MediaInfo)
    }

    companion object {
        private val SELECT_COLOR = Color.parseColor("#387DF7")
        private val UNSELECT_COLOR = Color.parseColor("#80505050")

        private val DiffCallback = object : DiffUtil.ItemCallback<MediaInfo>() {
            override fun areItemsTheSame(oldItem: MediaInfo, newItem: MediaInfo): Boolean {
                return oldItem.uri == newItem.uri
            }

            override fun areContentsTheSame(oldItem: MediaInfo, newItem: MediaInfo): Boolean {
                return oldItem == newItem
            }
        }
    }
}