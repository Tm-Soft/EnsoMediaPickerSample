package com.enso.ensomediapicker.adapter

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.bumptech.glide.Glide
import com.enso.ensoimagepicker.R

internal class SelectMediaListAdapter : ListAdapter<Uri, SelectMediaListAdapter.MediaSelectViewHolder>(
    diffCallback
) {
    private var context: Context? = null

    private var listener: SelectMediaListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaSelectViewHolder {
        context = context ?: parent.context
        val itemView = View.inflate(context, R.layout.item_media_select, null)
        return MediaSelectViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MediaSelectViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MediaSelectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                listener?.onItemClick(
                    getItem(adapterPosition.takeIf { it != NO_POSITION } ?: return@setOnClickListener)
                )
            }
        }

        private val ivSelectThumbnail: ImageView = itemView.findViewById(R.id.iv_select_thumbnail)

        fun bind(uri: Uri) {
            context?.let { context ->
                Glide.with(context)
                    .load(uri)
                    .skipMemoryCache(false)
                    .centerCrop()
                    .into(ivSelectThumbnail)
            }
        }
    }

    fun setSelectMediaListener(listener: SelectMediaListener) {
        this.listener = listener
    }

    interface SelectMediaListener {
        fun onItemClick(uri: Uri)
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<Uri>() {
            override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean {
                return oldItem == newItem
            }
        }
    }
}