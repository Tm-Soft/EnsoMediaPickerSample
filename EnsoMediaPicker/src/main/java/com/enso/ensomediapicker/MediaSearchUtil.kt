package com.enso.ensomediapicker

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.enso.ensomediapicker.model.MediaInfo
import com.enso.ensomediapicker.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object MediaSearchUtil {
    /**
     * 갤러리에 있는 이미지의 개수를 가져오는 함수
     * return Int
     */
    suspend fun getImageCount(context: Context): Int {
        return withContext(Dispatchers.IO) {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = null
            val selectionArgs = null
            val sortOrder = null

            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            val count = cursor?.count ?: 0
            cursor?.close()
            count
        }
    }

    /**
     * 갤러리에서 이미지들을 가져오는 함수
     * return List<Uri>
     */
    suspend fun getMediaFromGallery(
        context: Context,
        findMediaType: MediaType?
    ): List<MediaInfo>? {
        return withContext(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE
            )
            val selection = when (findMediaType) {
                MediaType.IMAGE -> "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
                MediaType.VIDEO -> "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
                else -> "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? or ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
            }
            val selectionArgs = when (findMediaType) {
                MediaType.IMAGE -> arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
                MediaType.VIDEO -> arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
                else -> arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                )
            }
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

            val cursor = context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val mediaTypeColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

                generateSequence { if (cursor.moveToNext()) cursor else null }
                    .map {
                        val mediaType = cursor.getInt(mediaTypeColumnIndex)
                        val contentUri = when (mediaType) {
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else -> null
                        } ?: return@map null

                        MediaInfo(
                            uri = ContentUris.withAppendedId(contentUri, cursor.getLong(idColumnIndex)),
                            isSelect = false,
                            selectIndex = null,
                            mediaType = when (mediaType) {
                                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaType.IMAGE
                                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaType.VIDEO
                                else -> MediaType.UNKNOWN
                            }
                        )
                    }
                    .filterNotNull()
                    .toList()
            }
        }
    }

    /**
     * Uri 로 실제 경로를 찾는 함수
     * return String
     */
    fun getRealPathFromUri(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return it.getString(columnIndex)
            }
        }
        return null
    }

    suspend fun getFileExtensionFromUri(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            MimeTypeMap
                .getSingleton()
                .getExtensionFromMimeType(context.contentResolver.getType(uri))
        }
    }

    suspend fun getVideoDuration(context: Context, videoUri: Uri): Long {
        return withContext(Dispatchers.Default) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationString?.toLongOrNull() ?: 0L
            retriever.release()
            duration
        }
    }
}