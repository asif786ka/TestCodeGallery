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
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.piczzamms.gallery.top.GalleryApplication;

/**
 * An implementation of interface <code>IImageList</code> which contains only
 * one image.
 */
public class UriImageList implements IImageList
{
	static final String		TAG	= "UriImageList";
	static final Boolean	LOG	= GalleryApplication.LOG;

	class FileCompare implements Comparator<File>
	{
		@Override
		public int compare(File lhs, File rhs)
		{
			long value = (rhs.lastModified() - lhs.lastModified());

			if (value == 0)
			{
				return 0;
			}
			if (value > 0)
			{
				return 1;
			}
			return -1;
		}
	};

	FileCompare			mFileCompare	= new FileCompare();
	ArrayList<UriImage>	mImageList;
	Uri					mInitialUri;

	public UriImageList(Context context, Uri uri)
	{
		mInitialUri = uri;
		mImageList = new ArrayList<UriImage>();

		File dir;
		try
		{
			dir = new File(new URI(uri.toString()));
		}
		catch (Exception ex)
		{
			Log.e(TAG, ex.getMessage());
			dir = null;
		}
		if (dir != null)
		{
			File[] files = dir.listFiles();

			if (files != null)
			{
				Arrays.sort(files, mFileCompare);

				for (File file : files)
				{
					if (file.isFile())
					{
						mImageList.add(new UriImage(this, context, file));

						if (LOG)
						{
							Log.d(TAG, "Found: " + file.getName() + ", lastModified=" + file.lastModified());
						}
					}
				}
			}
		}
	}

	@Override
	public boolean canDrag()
	{
		return true;
	}

	public void close()
	{
		mImageList.clear();
	}

	public HashMap<String, String> getBucketIds()
	{
		throw new UnsupportedOperationException();
	}

	public int getCount()
	{
		return mImageList.size();
	}

	public IImage getImageAt(int i)
	{
		return mImageList.get(i);
	}

	public IImage getImageForUri(Uri uri)
	{
		for (UriImage image : mImageList)
		{
			if (image.getImageUri().equals(uri))
			{
				return image;
			}
		}
		return null;
	}

	public int getImageIndex(IImage image)
	{
		return mImageList.indexOf(image);
	}

	String getSuffix(File file)
	{
		String name = file.getName();
		if ((name.charAt(0) == 'f') && Character.isDigit(name.charAt(1)) && Character.isDigit(name.charAt(2))
				&& name.charAt(3) == '_')
		{
			return name.substring(4);
		}
		return name;
	}

	public boolean isEmpty()
	{
		return mImageList.size() == 0;
	}

	@Override
	public void onDrag(int from_index, int to_index)
	{
		try
		{
			File dir = new File(new URI(mInitialUri.toString()));

			File[] files = dir.listFiles();
			Arrays.sort(files, mFileCompare);

			ArrayList<File> arrayFiles = new ArrayList<File>();
			for (File file : files)
			{
				arrayFiles.add(file);
			}
			File movingFile = arrayFiles.remove(from_index);
			if (to_index >= from_index)
			{
				to_index--; // list now one shorter
			}
			arrayFiles.add(to_index, movingFile);
			long time = System.currentTimeMillis() - 2000;
			for (File file : arrayFiles)
			{
				if (!file.setLastModified(time))
				{
					Log.e(TAG, "Failed to set time on " + file.getName());
				}
				time -= 2000;
			}
		}
		catch (Exception ex)
		{
			Log.e(TAG, ex.getMessage());
		}

	}

	public boolean removeImage(IImage image)
	{
		return removeImageAt(getImageIndex(image));
	}

	public boolean removeImageAt(int index)
	{
		if (index >= 0 && index < mImageList.size())
		{
			mImageList.remove(index);
			return true;
		}
		return false;
	}

	// void renameFile(File file, int pos)
	// {
	// String suffix = getSuffix(file);
	// String newName = String.format("f%02d_%s", pos, suffix);
	// String fullNewName = file.getParent() + File.separator + newName;
	// if (!file.renameTo(new File(fullNewName)))
	// {
	// Log.e(TAG, "Failed to rename " + file.getPath() + "->" + fullNewName);
	// }
	// else
	// {
	// Log.i(TAG, "Renamed: " + file.toString() + "->" + fullNewName);
	// }
	// }
}
