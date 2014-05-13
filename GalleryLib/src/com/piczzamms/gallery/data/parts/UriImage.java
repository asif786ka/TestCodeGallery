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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore.Video;

import com.piczzamms.gallery.data.BitmapManager;
import com.piczzamms.gallery.util.Util;

class UriImage implements IImage
{
	static final String	TAG	= "UriImage";

	final Uri			mUri;
	final File			mFile;
	final IImageList	mContainer;
	final Context		mCtx;

	final boolean		mIsVideo;

	public UriImage(IImageList container, Context context, File file)
	{
		mContainer = container;
		mCtx = context;
		mFile = file;
		mUri = Uri.fromFile(file);
		mIsVideo = isVideo(file);
	}

	public UriImage(IImageList container, Context context, Uri uri)
	{
		mContainer = container;
		mCtx = context;
		mFile = null;
		mUri = uri;
		mIsVideo = false;
	}

	public IImageList getContainer()
	{
		return mContainer;
	}

	ContentResolver getContentResolver()
	{
		return mCtx.getContentResolver();
	}

	public String getDataPath()
	{
		return mUri.getPath();
	}

	public long getDateTaken()
	{
		return 0;
	}

	public int getDegreesRotated()
	{
		return 0;
	}

	public Bitmap getFullSizeBitmap(int minSideLength, int maxNumberOfPixels)
	{
		return getFullSizeBitmap(minSideLength, maxNumberOfPixels, IImage.ROTATE_AS_NEEDED, IImage.NO_NATIVE);
	}

	public Bitmap getFullSizeBitmap(int minSideLength, int maxNumberOfPixels, boolean rotateAsNeeded)
	{
		return getFullSizeBitmap(minSideLength, maxNumberOfPixels, rotateAsNeeded, IImage.NO_NATIVE);
	}

	public Bitmap getFullSizeBitmap(int minSideLength, int maxNumOfPixels, boolean rotateAsNeeded, boolean useNative)
	{
		if (mIsVideo)
		{
			return ThumbnailUtils.createVideoThumbnail(mFile.getAbsolutePath(), Video.Thumbnails.MINI_KIND);
		}
		return Util.makeBitmap(mCtx, mUri, minSideLength, maxNumOfPixels, rotateAsNeeded, useNative);
	}

	public InputStream getFullSizeImageData()
	{
		return getInputStream();
	}

	public int getHeight()
	{
		if (mIsVideo)
		{
			return 0;
		}
		BitmapFactory.Options options = snifBitmapOptions();
		return (options != null) ? options.outHeight : 0;
	}

	public Uri getImageUri()
	{
		return mUri;
	}

	InputStream getInputStream()
	{
		try
		{
			if (mUri.getScheme().equals("file"))
			{
				return new java.io.FileInputStream(mUri.getPath());
			}
			else
			{
				return getContentResolver().openInputStream(mUri);
			}
		}
		catch (FileNotFoundException ex)
		{
			return null;
		}
	}

	public long getLastModified()
	{
		if (mFile != null)
		{
			return mFile.lastModified();
		}
		return 0;
	}

	public String getMimeType()
	{
		if (mIsVideo)
		{
			String ext = Util.getExt(mFile);
			if (ext.equals(".mp4"))
			{
				return "video/mp4";
			}
			else if (ext.equals(".3gp"))
			{
				return "video/3gpp";
			}
		}
		BitmapFactory.Options options = snifBitmapOptions();
		return (options != null && options.outMimeType != null) ? options.outMimeType : "";
	}

	public Bitmap getMiniThumbBitmap()
	{
		return getThumbBitmap(IImage.ROTATE_AS_NEEDED);
	}

	ParcelFileDescriptor getPFD()
	{
		return Util.getPFD(mCtx, mUri);
	}

	public Bitmap getThumbBitmap(boolean rotateAsNeeded)
	{
		return getFullSizeBitmap(THUMBNAIL_TARGET_SIZE, THUMBNAIL_MAX_NUM_PIXELS, rotateAsNeeded);
	}

	public String getTitle()
	{
		return mUri.toString();
	}

	public int getWidth()
	{
		if (mIsVideo)
		{
			return 0;
		}
		BitmapFactory.Options options = snifBitmapOptions();
		return (options != null) ? options.outWidth : 0;
	}

	public boolean isDrm()
	{
		return false;
	}

	public boolean isReadonly()
	{
		if (mFile != null)
		{
			return !mFile.canWrite();
		}
		return false;
	}

	@Override
	public boolean isVideo()
	{
		return mIsVideo;
	}

	boolean isVideo(File file)
	{
		String ext = Util.getExt(file);
		if (ext.equals(".mp4") || ext.equals(".3gp"))
		{
			return true;
		}
		return false;
	}

	public boolean rotateImageBy(int degrees)
	{
		return false;
	}

	BitmapFactory.Options snifBitmapOptions()
	{
		ParcelFileDescriptor input = getPFD();
		if (input == null)
			return null;
		try
		{
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapManager.instance().decodeFileDescriptor(input.getFileDescriptor(), options);
			return options;
		}
		finally
		{
			Util.closeSilently(input);
		}
	}
}
