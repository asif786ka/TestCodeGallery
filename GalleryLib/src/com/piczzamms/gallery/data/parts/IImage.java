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

import java.io.InputStream;

import android.graphics.Bitmap;
import android.net.Uri;

/**
 * The interface of all images used in gallery.
 */
public interface IImage
{
	static final int			THUMBNAIL_TARGET_SIZE		= 320;
	static final int			MINI_THUMB_TARGET_SIZE		= 96;
	static final int			THUMBNAIL_MAX_NUM_PIXELS	= 512 * 384;
	static final int			MINI_THUMB_MAX_NUM_PIXELS	= 128 * 128;
	static final int			UNCONSTRAINED				= -1;

	public static final boolean	ROTATE_AS_NEEDED			= true;
	public static final boolean	NO_ROTATE					= false;
	public static final boolean	USE_NATIVE					= true;
	public static final boolean	NO_NATIVE					= false;

	/** Get the input stream associated with a given full size image. */
	public abstract InputStream getFullSizeImageData();

	/** Get the image list which contains this image. */
	public abstract IImageList getContainer();

	/** Get the path of the (full size) image data. */
	public abstract String getDataPath();

	// Get metadata of the image
	public abstract long getDateTaken();

	public abstract long getLastModified();

	public abstract int getDegreesRotated();

	/** Get the bitmap for the full size image. */
	public abstract Bitmap getFullSizeBitmap(int minSideLength, int maxNumberOfPixels);

	public abstract Bitmap getFullSizeBitmap(int minSideLength, int maxNumberOfPixels, boolean rotateAsNeeded,
			boolean useNative);

	public abstract int getHeight();

	public abstract Uri getImageUri();

	public abstract String getMimeType();

	// Get the bitmap of the mini thumbnail.
	public abstract Bitmap getMiniThumbBitmap();

	// Get the bitmap of the medium thumbnail
	public abstract Bitmap getThumbBitmap(boolean rotateAsNeeded);

	// Get the title of the image
	public abstract String getTitle();

	public abstract int getWidth();

	public abstract boolean isDrm();

	// Get property of the image
	public abstract boolean isReadonly();

	public abstract boolean isVideo();

	// Rotate the image
	public abstract boolean rotateImageBy(int degrees);

}
