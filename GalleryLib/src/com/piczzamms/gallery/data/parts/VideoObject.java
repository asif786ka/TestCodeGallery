/*
 * Copyright (C) 2009 The Android Open Source Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.piczzamms.gallery.data.parts;

import java.io.IOException;
import java.io.InputStream;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.util.Log;

import com.piczzamms.gallery.data.BitmapManager;

/**
 * Represents a particular video and provides access to the underlying data and
 * two thumbnail bitmaps as well as other information such as the id, and the
 * path to the actual video data.
 */
public class VideoObject extends BaseImage implements IImage
{
	private static final String	TAG	= "VideoObject";

	/**
	 * Constructor.
	 * 
	 * @param id
	 *        the image id of the image
	 * @param cr
	 *        the content resolver
	 */
	protected VideoObject(BaseImageList container, ContentResolver cr, long id, int index, Uri uri, String dataPath,
			String mimeType, long dateTaken, long lastModified, String title)
	{
		super(container, cr, id, index, uri, dataPath, mimeType, dateTaken, lastModified, title);
	}

	@Override
	public boolean equals(Object other)
	{
		if (other == null || !(other instanceof VideoObject))
			return false;
		return getImageUri().equals(((VideoObject) other).getImageUri());
	}

	@Override
	public int hashCode()
	{
		return getImageUri().toString().hashCode();
	}

	@Override
	public Bitmap getFullSizeBitmap(int minSideLength, int maxNumberOfPixels, boolean rotateAsNeeded, boolean useNative)
	{
		return ThumbnailUtils.createVideoThumbnail(mDataPath, Video.Thumbnails.MINI_KIND);
	}

	@Override
	public InputStream getFullSizeImageData()
	{
		try
		{
			InputStream input = mContentResolver.openInputStream(getImageUri());
			return input;
		}
		catch (IOException ex)
		{
			return null;
		}
	}

	@Override
	public int getHeight()
	{
		return 0;
	}

	@Override
	public int getWidth()
	{
		return 0;
	}

	@Override
	public boolean isVideo()
	{
		return true;
	}

	public boolean isReadonly()
	{
		return false;
	}

	public boolean isDrm()
	{
		return false;
	}

	public boolean rotateImageBy(int degrees)
	{
		return false;
	}

	public Bitmap getThumbBitmap(boolean rotateAsNeeded)
	{
		return getFullSizeBitmap(THUMBNAIL_TARGET_SIZE, THUMBNAIL_MAX_NUM_PIXELS);
	}

	@Override
	public Bitmap getMiniThumbBitmap()
	{
		try
		{
			long id = mId;
			return BitmapManager.instance()
					.getThumbnail(mContentResolver, id, Images.Thumbnails.MICRO_KIND, null, true);
		}
		catch (Throwable ex)
		{
			Log.e(TAG, "miniThumbBitmap got exception", ex);
			return null;
		}
	}

	@Override
	public String toString()
	{
		return new StringBuilder("VideoObject").append(mId).toString();
	}
}
