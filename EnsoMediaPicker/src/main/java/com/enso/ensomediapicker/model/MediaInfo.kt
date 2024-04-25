package com.enso.ensomediapicker.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class MediaInfo(
    val uri: Uri,
    val isSelect: Boolean,
    val selectIndex: Int?,
    val mediaType: MediaType,
) : Parcelable