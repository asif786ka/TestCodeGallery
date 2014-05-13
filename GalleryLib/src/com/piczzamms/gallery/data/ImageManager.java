/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.piczzamms.gallery.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;

import com.piczzamms.gallery.data.parts.IImage;
import com.piczzamms.gallery.data.parts.IImageList;
import com.piczzamms.gallery.data.parts.ImageList;
import com.piczzamms.gallery.data.parts.ImageListUber;
import com.piczzamms.gallery.data.parts.SingleImageList;
import com.piczzamms.gallery.data.parts.UriImageList;
import com.piczzamms.gallery.data.parts.VideoList;
import com.piczzamms.gallery.util.Util;

/**
 * ImageManager is used to retrieve and store images
 * in the media content provider.
 */
public class ImageManager
{
	// Location
	public static enum DataLocation
	{
		NONE, INTERNAL, EXTERNAL, INTERNAL_DATA, ALL
	}

	private static class EmptyImageList implements IImageList
	{
		@Override
		public boolean canDrag()
		{
			return false;
		}

		public void close()
		{
		}

		public HashMap<String, String> getBucketIds()
		{
			return new HashMap<String, String>();
		}

		public int getCount()
		{
			return 0;
		}

		public IImage getImageAt(int i)
		{
			return null;
		}

		public IImage getImageForUri(Uri uri)
		{
			return null;
		}

		public int getImageIndex(IImage image)
		{
			throw new UnsupportedOperationException();
		}

		public boolean isEmpty()
		{
			return true;
		}

		@Override
		public void onDrag(int from_index, int to_index)
		{
		}

		public boolean removeImage(IImage image)
		{
			return false;
		}

		public boolean removeImageAt(int i)
		{
			return false;
		}
	}

	// ImageListParam specifies all the parameters we need to create an image
	// list (we also need a ContentResolver).
	public static class ImageListParam implements Parcelable
	{
		public DataLocation						mLocation;
		public int								mInclusion;
		public int								mSort;
		public String							mBucketId;

		/**
		 * If mLocation is INTERNAL_DATA then this uri refers to a directory filed with images.
		 * Otherwise, it will be a single image.
		 */
		public Uri								mUri;

		// This is only used if we are creating an empty image list.
		public boolean							mIsEmptyImageList;

		public static final Parcelable.Creator	CREATOR	= new Parcelable.Creator()
														{
															public ImageListParam createFromParcel(Parcel in)
															{
																return new ImageListParam(in);
															}

															public ImageListParam[] newArray(int size)
															{
																return new ImageListParam[size];
															}
														};

		public ImageListParam()
		{
		}

		private ImageListParam(Parcel in)
		{
			mLocation = DataLocation.values()[in.readInt()];
			mInclusion = in.readInt();
			mSort = in.readInt();
			mBucketId = in.readString();
			mUri = in.readParcelable(null);
			mIsEmptyImageList = (in.readInt() != 0);
		}

		public int describeContents()
		{
			return 0;
		}

		public String toString()
		{
			return String.format("ImageListParam{loc=%s,inc=%d,sort=%d," + "bucket=%s,empty=%b,single=%s}", mLocation,
					mInclusion, mSort, mBucketId, mIsEmptyImageList, mUri);
		}

		public void writeToParcel(Parcel out, int flags)
		{
			out.writeInt(mLocation.ordinal());
			out.writeInt(mInclusion);
			out.writeInt(mSort);
			out.writeString(mBucketId);
			out.writeParcelable(mUri, flags);
			out.writeInt(mIsEmptyImageList ? 1 : 0);
		}

		public Uri getUri()
		{
			return mUri;
		}
	}

	private static final String	TAG							= "ImageManager";

	private static final Uri	STORAGE_URI					= Images.Media.EXTERNAL_CONTENT_URI;

	private static final Uri	THUMB_URI					= Images.Thumbnails.EXTERNAL_CONTENT_URI;

	private static final Uri	VIDEO_STORAGE_URI			= Uri.parse("content://media/external/video/media");
	// Inclusion
	public static final int		INCLUDE_IMAGES				= (1 << 0);
	public static final int		INCLUDE_DRM_IMAGES			= (1 << 1);

	public static final int		INCLUDE_VIDEOS				= (1 << 2);
	// Sort
	public static final int		SORT_ASCENDING				= 1;

	public static final int		SORT_DESCENDING				= 2;
	public static final String	CAMERA_IMAGE_BUCKET_NAME	= Environment.getExternalStorageDirectory().toString()
																	+ "/DCIM/Camera";

	public static final String	CAMERA_IMAGE_BUCKET_ID		= getBucketId(CAMERA_IMAGE_BUCKET_NAME);

	//
	// Stores a bitmap or a jpeg byte array to a file (using the specified
	// directory and filename). Also add an entry to the media store for
	// this picture. The title, dateTaken, location are attributes for the
	// picture. The degree is a one element array which returns the orientation
	// of the picture.
	//
	public static Uri addImage(ContentResolver cr, String title, long dateTaken, Location location, String directory,
			String filename, Bitmap source, byte[] jpegData, int[] degree)
	{
		// We should store image data earlier than insert it to ContentProvider, otherwise
		// we may not be able to generate thumbnail in time.
		OutputStream outputStream = null;
		String filePath = directory + "/" + filename;
		try
		{
			File dir = new File(directory);
			if (!dir.exists())
				dir.mkdirs();
			File file = new File(directory, filename);
			outputStream = new FileOutputStream(file);
			if (source != null)
			{
				source.compress(CompressFormat.JPEG, 75, outputStream);
				// source.compress(CompressFormat.PNG, 50, outputStream);
				degree[0] = 0;
			}
			else
			{
				outputStream.write(jpegData);
				degree[0] = getExifOrientation(filePath);
			}
		}
		catch (FileNotFoundException ex)
		{
			Log.w(TAG, ex);
			return null;
		}
		catch (IOException ex)
		{
			Log.w(TAG, ex);
			return null;
		}
		finally
		{
			Util.closeSilently(outputStream);
		}

		ContentValues values = new ContentValues(7);
		values.put(Images.Media.TITLE, title);

		// That filename is what will be handed to Gmail when a user shares a
		// photo. Gmail gets the name of the picture attachment from the
		// "DISPLAY_NAME" field.
		values.put(Images.Media.DISPLAY_NAME, filename);
		values.put(Images.Media.DATE_TAKEN, dateTaken);
		// values.put(Images.Media.MIME_TYPE, "image/jpeg");
		values.put(Images.Media.MIME_TYPE, "image/png");
		values.put(Images.Media.ORIENTATION, degree[0]);
		values.put(Images.Media.DATA, filePath);

		if (location != null)
		{
			values.put(Images.Media.LATITUDE, location.getLatitude());
			values.put(Images.Media.LONGITUDE, location.getLongitude());
		}
		return cr.insert(STORAGE_URI, values);
	}

	private static boolean checkFsWritable()
	{
		// Create a temporary file to see whether a volume is really writeable.
		// It's important not to put it in the root directory which may have a
		// limit on the number of files.
		String directoryName = Environment.getExternalStorageDirectory().toString() + "/DCIM";
		File directory = new File(directoryName);
		if (!directory.isDirectory())
		{
			if (!directory.mkdirs())
			{
				return false;
			}
		}
		File f = new File(directoryName, ".probe");
		try
		{
			// Remove stale file if any
			if (f.exists())
			{
				f.delete();
			}
			if (!f.createNewFile())
			{
				return false;
			}
			f.delete();
			return true;
		}
		catch (IOException ex)
		{
			return false;
		}
	}

	/**
	 * @return true if the mimetype is a video mimetype.
	 */
	/*
	 * This is commented out because isVideo is not calling this now.
	 * public static boolean isVideoMimeType(String mimeType) {
	 * return mimeType.startsWith("video/");
	 * }
	 */

	/**
	 * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
	 * imported. This is a temporary fix for bug#1655552.
	 */
	public static void ensureOSXCompatibleFolder()
	{
		File nnnAAAAA = new File(Environment.getExternalStorageDirectory().toString() + "/DCIM/100ANDRO");
		if ((!nnnAAAAA.exists()) && (!nnnAAAAA.mkdir()))
		{
			Log.e(TAG, "create NNNAAAAA file: " + nnnAAAAA.getPath() + " failed");
		}
	}

	/**
	 * Matches code in MediaProvider.computeBucketValues. Should be a common
	 * function.
	 */
	public static String getBucketId(String path)
	{
		return String.valueOf(path.toLowerCase().hashCode());
	}

	public static ImageListParam getEmptyImageListParam()
	{
		ImageListParam param = new ImageListParam();
		param.mIsEmptyImageList = true;
		return param;
	}

	public static int getExifOrientation(String filepath)
	{
		int degree = 0;
		ExifInterface exif = null;
		try
		{
			exif = new ExifInterface(filepath);
		}
		catch (IOException ex)
		{
			Log.e(TAG, "cannot read exif", ex);
		}
		if (exif != null)
		{
			int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1);
			if (orientation != -1)
			{
				// We only recognize a subset of orientation tag values.
				switch (orientation)
				{
					case ExifInterface.ORIENTATION_ROTATE_90:
						degree = 90;
						break;
					case ExifInterface.ORIENTATION_ROTATE_180:
						degree = 180;
						break;
					case ExifInterface.ORIENTATION_ROTATE_270:
						degree = 270;
						break;
				}
			}
		}
		return degree;
	}

	public static ImageListParam getImageListParam(DataLocation location, int inclusion, int sort, String bucketId)
	{
		ImageListParam param = new ImageListParam();
		param.mLocation = location;
		param.mInclusion = inclusion;
		param.mSort = sort;
		param.mBucketId = bucketId;
		return param;
	}

	public static ImageListParam getInternalImageListParam(Uri uri, int inclusion, int sort)
	{
		ImageListParam param = new ImageListParam();
		param.mLocation = DataLocation.INTERNAL_DATA;
		param.mInclusion = inclusion;
		param.mSort = sort;
		param.mUri = uri;
		return param;
	}

	public static ImageListParam getSingleImageListParam(Uri uri)
	{
		ImageListParam param = new ImageListParam();
		param.mUri = uri;
		return param;
	}

	public static boolean hasStorage()
	{
		return hasStorage(true);
	}

	public static boolean hasStorage(boolean requireWriteAccess)
	{
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state))
		{
			if (requireWriteAccess)
			{
				boolean writable = checkFsWritable();
				return writable;
			}
			else
			{
				return true;
			}
		}
		else if (!requireWriteAccess && Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
		{
			return true;
		}
		return false;
	}

	/**
	 * @return true if the image is an image.
	 */
	public static boolean isImage(IImage image)
	{
		return isImageMimeType(image.getMimeType());
	}

	/**
	 * @return true if the mimetype is an image mimetype.
	 */
	public static boolean isImageMimeType(String mimeType)
	{
		return mimeType.startsWith("image/");
	}

	public static boolean isMediaScannerScanning(ContentResolver cr)
	{
		boolean result = false;
		Cursor cursor = query(cr, MediaStore.getMediaScannerUri(), new String[] {
			MediaStore.MEDIA_SCANNER_VOLUME }, null, null, null);
		if (cursor != null)
		{
			if (cursor.getCount() == 1)
			{
				cursor.moveToFirst();
				result = "external".equals(cursor.getString(0));
			}
			cursor.close();
		}
		return result;
	}

	public static boolean isSingleImageMode(String uriString)
	{
		return !uriString.startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())
				&& !uriString.startsWith(MediaStore.Images.Media.INTERNAL_CONTENT_URI.toString());
	}

	/**
	 * @return true if the image is a video.
	 */
	public static boolean isVideo(IImage image)
	{
		return (image.isVideo());
	}

	public static IImageList makeEmptyImageList()
	{
		return makeImageList(null, getEmptyImageListParam());
	}

	public static IImageList makeImageList(Context context, DataLocation location, int inclusion, int sort,
			String bucketId)
	{
		ImageListParam param = getImageListParam(location, inclusion, sort, bucketId);
		return makeImageList(context, param);
	}

	// This is the factory function to create an image list.
	public static IImageList makeImageList(Context context, ImageListParam param)
	{
		DataLocation location = param.mLocation;
		boolean isEmptyImageList = param.mIsEmptyImageList;

		if (isEmptyImageList || context == null)
		{
			return new EmptyImageList();
		}
		int inclusion = param.mInclusion;
		int sort = param.mSort;
		String bucketId = param.mBucketId;
		Uri uri = param.mUri;
		ContentResolver cr = context.getContentResolver();

		if (uri != null)
		{
			if (location != DataLocation.INTERNAL_DATA)
			{
				return new SingleImageList(context, uri);
			}
			else
			{
				return new UriImageList(context, uri);
			}
		}

		// false ==> don't require write access
		boolean haveSdCard = hasStorage(false);

		// use this code to merge videos and stills into the same list
		ArrayList<IImageList> l = new ArrayList<IImageList>();

		if (haveSdCard && location != DataLocation.INTERNAL)
		{
			if ((inclusion & INCLUDE_IMAGES) != 0)
			{
				// if (location == DataLocation.INTERNAL_DATA && uri != null)
				// {
				// l.add(new UriImageList(cr, uri));
				// }
				// else
				{
					l.add(new ImageList(cr, STORAGE_URI, sort, bucketId));
				}
			}
			if ((inclusion & INCLUDE_VIDEOS) != 0)
			{
				l.add(new VideoList(cr, VIDEO_STORAGE_URI, sort, bucketId));
			}
		}
		if (location == DataLocation.INTERNAL || location == DataLocation.ALL)
		{
			if ((inclusion & INCLUDE_IMAGES) != 0)
			{
				l.add(new ImageList(cr, Images.Media.INTERNAL_CONTENT_URI, sort, bucketId));
			}
			if ((inclusion & INCLUDE_DRM_IMAGES) != 0)
			{
				// Not implemented yet
				// l.add(new DrmImageList(cr, DrmStore.Images.CONTENT_URI, sort, bucketId));
			}
		}

		// Optimization: If some of the lists are empty, remove them.
		// If there is only one remaining list, return it directly.
		Iterator<IImageList> iter = l.iterator();
		while (iter.hasNext())
		{
			IImageList sublist = iter.next();
			if (sublist.isEmpty())
			{
				sublist.close();
				iter.remove();
			}
		}

		if (l.size() == 1)
		{
			IImageList list = l.get(0);
			return list;
		}

		ImageListUber uber = new ImageListUber(l.toArray(new IImageList[l.size()]), sort);
		return uber;
	}

	// This is a convenience function to create an image list from a Uri.
	public static IImageList makeImageList(Context context, Uri uri, int sort)
	{
		ContentResolver cr = context.getContentResolver();
		String uriString = (uri != null) ? uri.toString() : "";

		// TODO: we need to figure out whether we're viewing
		// DRM images in a better way. Is there a constant
		// for content://drm somewhere??

		if (uriString.startsWith("content://drm"))
		{
			return makeImageList(context, DataLocation.ALL, INCLUDE_DRM_IMAGES, sort, null);
		}
		else if (uriString.startsWith("content://media/external/video"))
		{
			return makeImageList(context, DataLocation.EXTERNAL, INCLUDE_VIDEOS, sort, null);
		}
		else if (isSingleImageMode(uriString))
		{
			return makeSingleImageList(context, uri);
		}
		else
		{
			String bucketId = uri.getQueryParameter("bucketId");
			return makeImageList(context, DataLocation.ALL, INCLUDE_IMAGES, sort, bucketId);
		}
	}

	public static IImageList makeSingleImageList(Context context, Uri uri)
	{
		return makeImageList(context, getSingleImageListParam(uri));
	}

	static Cursor query(ContentResolver resolver, Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder)
	{
		try
		{
			if (resolver == null)
			{
				return null;
			}
			return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
		}
		catch (UnsupportedOperationException ex)
		{
			return null;
		}
	}
}
