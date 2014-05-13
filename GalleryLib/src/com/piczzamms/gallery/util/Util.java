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

package com.piczzamms.gallery.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import com.piczzamms.gallery.activities.ImageGallery;
import com.piczzamms.gallery.activities.MonitoredActivity;
import com.piczzamms.gallery.data.BitmapManager;
import com.piczzamms.gallery.data.parts.IImage;
import com.piczzamms.gallery.top.GalleryApplication;

/**
 * Collection of utility functions used in this package.
 */
public class Util
{
	static class BackgroundJob extends MonitoredActivity.LifeCycleAdapter implements Runnable
	{

		private final MonitoredActivity	mActivity;
		private final ProgressDialog	mDialog;
		private final Runnable			mJob;
		private final Handler			mHandler;
		private final Runnable			mCleanupRunner	= new Runnable()
														{
															public void run()
															{
																mActivity.removeLifeCycleListener(BackgroundJob.this);
																if (mDialog.getWindow() != null)
																	mDialog.dismiss();
															}
														};

		public BackgroundJob(MonitoredActivity activity, Runnable job, ProgressDialog dialog, Handler handler)
		{
			mActivity = activity;
			mDialog = dialog;
			mJob = job;
			mActivity.addLifeCycleListener(this);
			mHandler = handler;
		}

		@Override
		public void onActivityDestroyed(MonitoredActivity activity)
		{
			// We get here only when the onDestroyed being called before
			// the mCleanupRunner. So, run it now and remove it from the queue
			mCleanupRunner.run();
			mHandler.removeCallbacks(mCleanupRunner);
		}

		@Override
		public void onActivityStarted(MonitoredActivity activity)
		{
			mDialog.show();
		}

		@Override
		public void onActivityStopped(MonitoredActivity activity)
		{
			mDialog.hide();
		}

		public void run()
		{
			try
			{
				mJob.run();
			}
			finally
			{
				mHandler.post(mCleanupRunner);
			}
		}
	}

	static final String				TAG					= ImageGallery.TAG + "-Util";
	public static final int			DIRECTION_LEFT		= 0;
	public static final int			DIRECTION_RIGHT		= 1;
	public static final int			DIRECTION_UP		= 2;
	public static final int			DIRECTION_DOWN		= 3;

	public static final String		PICTURE_PREFIX		= "Picture_";

	private static OnClickListener	sNullOnClickListener;

	// Whether we should recycle the input (unless the output is the input).
	public static final boolean		RECYCLE_INPUT		= true;

	public static final boolean		NO_RECYCLE_INPUT	= false;

	public static void Assert(boolean cond)
	{
		if (!cond)
		{
			throw new AssertionError();
		}
	}

	public static void closeSilently(Closeable c)
	{
		if (c == null)
			return;
		try
		{
			c.close();
		}
		catch (Throwable t)
		{
			// do nothing
		}
	}

	public static void closeSilently(ParcelFileDescriptor c)
	{
		if (c == null)
		{
			return;
		}
		try
		{
			c.close();
		}
		catch (Throwable t)
		{
			// do nothing
		}
	}

	private static int computeInitialSampleSize(BitmapFactory.Options options, int minSideLength, int maxNumOfPixels)
	{
		double w = options.outWidth;
		double h = options.outHeight;

		int lowerBound = (maxNumOfPixels == IImage.UNCONSTRAINED) ? 1 : (int) Math.ceil(Math.sqrt(w * h
				/ maxNumOfPixels));
		int upperBound = (minSideLength == IImage.UNCONSTRAINED) ? 128 : (int) Math.min(Math.floor(w / minSideLength),
				Math.floor(h / minSideLength));

		if (upperBound < lowerBound)
		{
			// return the larger one when there is no overlapping zone.
			return lowerBound;
		}

		if ((maxNumOfPixels == IImage.UNCONSTRAINED) && (minSideLength == IImage.UNCONSTRAINED))
		{
			return 1;
		}
		else if (minSideLength == IImage.UNCONSTRAINED)
		{
			return lowerBound;
		}
		else
		{
			return upperBound;
		}
	}

	/*
	 * Compute the sample size as a function of minSideLength
	 * and maxNumOfPixels.
	 * minSideLength is used to specify that minimal width or height of a
	 * bitmap.
	 * maxNumOfPixels is used to specify the maximal size in pixels that is
	 * tolerable in terms of memory usage.
	 * 
	 * The function returns a sample size based on the constraints.
	 * Both size and minSideLength can be passed in as IImage.UNCONSTRAINED,
	 * which indicates no care of the corresponding constraint.
	 * The functions prefers returning a sample size that
	 * generates a smaller bitmap, unless minSideLength = IImage.UNCONSTRAINED.
	 * 
	 * Also, the function rounds up the sample size to a power of 2 or multiple
	 * of 8 because BitmapFactory only honors sample size this way.
	 * For example, BitmapFactory downsamples an image by 2 even though the
	 * request is 3. So we round up the sample size to avoid OOM.
	 */
	public static int computeSampleSize(BitmapFactory.Options options, int minSideLength, int maxNumOfPixels)
	{
		int initialSize = computeInitialSampleSize(options, minSideLength, maxNumOfPixels);

		int roundedSize;
		if (initialSize <= 8)
		{
			roundedSize = 1;
			while (roundedSize < initialSize)
			{
				roundedSize <<= 1;
			}
		}
		else
		{
			roundedSize = (initialSize + 7) / 8 * 8;
		}

		return roundedSize;
	}

	public static int CopyStream(InputStream input, OutputStream output) throws IOException
	{
		final int DEFAULT_BUFFER_SIZE = 2048;
		byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
		int count = 0;
		int n = 0;
		while (-1 != (n = input.read(buffer)))
		{
			output.write(buffer, 0, n);
			count += n;
		}
		return count;
	}

	// Returns Options that set the puregeable flag for Bitmap decode.
	public static BitmapFactory.Options createNativeAllocOptions()
	{
		return new BitmapFactory.Options();
	}

	public static boolean deleteImage(Context context, Uri uri)
	{
		if (uri == null)
		{
			return false;
		}
		File file;
		boolean flag = false;

		try
		{
			file = new File(new URI(uri.toString()));
			flag = file.delete();
		}
		catch (Exception e)
		{
			try
			{
				Cursor c = Media.query(context.getContentResolver(), uri, null, null, null, null);
				int dataIndex = c.getColumnIndex(Media.DATA);

				if (dataIndex >= 0)
				{
					if (c.moveToFirst())
					{
						String filename = c.getString(dataIndex);
						file = new File(filename);
						flag = file.delete();
					}
				}
				c.close();
			}
			catch (Exception ex)
			{
				Log.e(TAG, ex.getMessage());
			}
		}
		return flag;
	}

	public static boolean equals(String a, String b)
	{
		// return true if both string are null or the content equals
		return a == b || a.equals(b);
	}

	public static void fileCopy(File src, File dst) throws IOException
	{
		InputStream in = new FileInputStream(src);
		OutputStream out = new FileOutputStream(dst);
		fileCopy(in, out);
		in.close();
		out.close();
	}

	public static void fileCopy(InputStream in, OutputStream out) throws IOException
	{
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0)
		{
			out.write(buf, 0, len);
		}
	}

	public static File genTmpFile(Context ctx, String name)
	{
		File dir = ctx.getCacheDir();
		dir.mkdirs();
		return new File(dir, name);
	}

	public static String getExt(File file)
	{
		int pos = file.getName().lastIndexOf('.');
		if (pos >= 0)
		{
			return file.getName().substring(pos);
		}
		return "";
	}

	public static synchronized OnClickListener getNullOnClickListener()
	{
		if (sNullOnClickListener == null)
		{
			sNullOnClickListener = new OnClickListener()
			{
				public void onClick(View v)
				{
				}
			};
		}
		return sNullOnClickListener;
	}

	public static ParcelFileDescriptor getPFD(Context ctx, Uri uri)
	{
		try
		{
			if (uri.getScheme().equals("file"))
			{
				String path = uri.getPath();
				return ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
			}
			else
			{
				return ctx.getContentResolver().openFileDescriptor(uri, "r");
			}
		}
		catch (FileNotFoundException ex)
		{
			return null;
		}
	}

	public static <T> int indexOf(T[] array, T s)
	{
		for (int i = 0; i < array.length; i++)
		{
			if (array[i].equals(s))
			{
				return i;
			}
		}
		return -1;
	}

	public static Bitmap makeBitmap(Context ctx, Uri uri)
	{
		return makeBitmap(ctx, uri, IImage.UNCONSTRAINED, 3 * 1024 * 1024, IImage.NO_ROTATE, IImage.USE_NATIVE);
	}

	public static Bitmap makeBitmap(Context ctx, Uri uri, int minSideLength, int maxNumOfPixels,
			boolean rotateAsNeeded, boolean useNative)
	{
		try
		{
			ParcelFileDescriptor pfdInput = getPFD(ctx, uri);
			Bitmap b = Util.makeBitmap(minSideLength, maxNumOfPixels, pfdInput, useNative);
			if (b == null && uri != null)
			{
				Util.closeSilently(pfdInput);
				/*
				 * Try copying the bitmap to a temporary file.
				 */
				StringBuffer sbuf = new StringBuffer();
				sbuf.append("tmp_");
				sbuf.append(System.currentTimeMillis());
				String fileName = sbuf.toString();
				File tmpFile = genTmpFile(ctx, fileName);

				Log.e(TAG, "Failed to get image from " + uri.toString() + ", will try " + tmpFile.toString());

				try
				{
					InputStream is = null;
					is = ctx.getContentResolver().openInputStream(uri);
					OutputStream os = new FileOutputStream(tmpFile);
					CopyStream(is, os);
					is.close();
					os.close();

					Uri altUri = Uri.fromFile(tmpFile);
					pfdInput = ctx.getContentResolver().openFileDescriptor(altUri, "r");
					b = Util.makeBitmap(minSideLength, maxNumOfPixels, pfdInput, useNative);

					tmpFile.delete();
				}
				catch (Exception ex)
				{
					GalleryApplication app = (GalleryApplication) ctx.getApplicationContext();
					app.err("Exception: " + ex.getMessage());
					ex.printStackTrace();
				}
			}
			return b;
		}
		catch (Exception ex)
		{
			Log.e(TAG, "got exception decoding bitmap ", ex);
			return null;
		}
	}

	public static Bitmap makeBitmap(int minSideLength, int maxNumOfPixels, ParcelFileDescriptor pfd, boolean useNative)
	{
		BitmapFactory.Options options = null;
		if (useNative)
		{
			options = createNativeAllocOptions();
		}
		return makeBitmap(minSideLength, maxNumOfPixels, null, null, pfd, options);
	}

	/**
	 * Make a bitmap from a given Uri.
	 * 
	 * @param uri
	 */
	public static Bitmap makeBitmap(int minSideLength, int maxNumOfPixels, Uri uri, ContentResolver cr,
			boolean useNative)
	{
		ParcelFileDescriptor input = null;
		try
		{
			input = cr.openFileDescriptor(uri, "r");

			BitmapFactory.Options options = null;
			if (useNative)
			{
				options = createNativeAllocOptions();
			}
			return makeBitmap(minSideLength, maxNumOfPixels, uri, cr, input, options);
		}
		catch (IOException ex)
		{
			return null;
		}
		finally
		{
			closeSilently(input);
		}
	}

	public static Bitmap makeBitmap(int minSideLength, int maxNumOfPixels, Uri uri, ContentResolver cr,
			ParcelFileDescriptor pfd, BitmapFactory.Options options)
	{
		try
		{
			if (pfd == null)
			{
				pfd = makeInputStream(uri, cr);
			}
			if (pfd == null)
			{
				Log.e(TAG, "PDF was NULL");
				return null;
			}
			if (options == null)
			{
				options = new BitmapFactory.Options();
			}
			FileDescriptor fd = pfd.getFileDescriptor();
			options.inJustDecodeBounds = true;
			BitmapManager.instance().decodeFileDescriptor(fd, options);
			if (options.mCancel || options.outWidth == -1 || options.outHeight == -1)
			{
				Log.e(TAG, "Could not get size " + options.mCancel + ", " + options.outWidth + ", " + options.outHeight);
				return null;
			}
			options.inSampleSize = computeSampleSize(options, minSideLength, maxNumOfPixels);
			options.inJustDecodeBounds = false;

			options.inDither = false;
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			Bitmap bm = BitmapManager.instance().decodeFileDescriptor(fd, options);
			if (bm == null)
			{
				Log.e(TAG, "For some reason returning NULL");
			}
			return bm;
		}
		catch (OutOfMemoryError ex)
		{
			Log.e(TAG, "Got oom exception ", ex);
			return null;
		}
		finally
		{
			closeSilently(pfd);
		}
	}

	public static Bitmap makeBitmap(Uri uri, ContentResolver cr)
	{
		try
		{
			ParcelFileDescriptor pfd = makeInputStream(uri, cr);
			if (pfd == null)
			{
				return null;
			}
			BitmapFactory.Options options = new BitmapFactory.Options();
			FileDescriptor fd = pfd.getFileDescriptor();
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			return BitmapManager.instance().decodeFileDescriptor(fd, options);
		}
		catch (OutOfMemoryError ex)
		{
			Log.e(TAG, ex.getMessage());
			return null;
		}
	}

	static ParcelFileDescriptor makeInputStream(Uri uri, ContentResolver cr)
	{
		if (cr == null)
		{
			Log.e(TAG, "Content resolver was NULL??");
			return null;
		}
		if (uri == null)
		{
			Log.e(TAG, "URI was NULL??");
			return null;
		}
		try
		{
			return cr.openFileDescriptor(uri, "r");
		}
		catch (IOException ex)
		{
			return null;
		}
	}

	// Rotates the bitmap by the specified degree.
	// If a new bitmap is created, the original bitmap is recycled.
	public static Bitmap rotate(Bitmap b, int degrees)
	{
		if (degrees != 0 && b != null)
		{
			Matrix m = new Matrix();
			m.setRotate(degrees, (float) b.getWidth() / 2, (float) b.getHeight() / 2);
			try
			{
				Bitmap b2 = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
				if (b != b2)
				{
					b.recycle();
					b = b2;
				}
			}
			catch (OutOfMemoryError ex)
			{
				// We have no memory to rotate. Return the original bitmap.
			}
		}
		return b;
	}

	public static void saveImage(Context context, String inputFilename, String outputDir)
	{
		try
		{
			File input = new File(inputFilename);
			FileInputStream fromFileStream = new FileInputStream(input);

			String toFilename = outputDir + File.separator + PICTURE_PREFIX + Long.toString(System.currentTimeMillis());
			File toFile = new File(toFilename);
			FileOutputStream toFileStream = new FileOutputStream(toFile);
			fileCopy(fromFileStream, toFileStream);

			toFileStream.close();
			fromFileStream.close();
		}
		catch (Exception ex)
		{
			Log.e(TAG, ex.getMessage());
		}
	}

	public static void saveImage(Context context, Uri uri, String outputDir)
	{
		try
		{
			Cursor c = Media.query(context.getContentResolver(), uri, null, null, null, null);
			int dataIndex = c.getColumnIndex(Media.DATA);

			if (dataIndex >= 0)
			{
				if (c.moveToFirst())
				{
					String fromFilename = c.getString(dataIndex);
					File fromFile = new File(fromFilename);
					String toFilename = outputDir + File.separator + fromFile.getName();
					File toFile = new File(toFilename);
					fileCopy(fromFile, toFile);
				}
			}
			else
			{
				ParcelFileDescriptor pfd = makeInputStream(uri, context.getContentResolver());
				FileDescriptor fd = pfd.getFileDescriptor();
				FileInputStream fromFileStream = new FileInputStream(fd);

				String toFilename = outputDir + File.separator + PICTURE_PREFIX
						+ Long.toString(System.currentTimeMillis());
				File toFile = new File(toFilename);
				FileOutputStream toFileStream = new FileOutputStream(toFile);
				fileCopy(fromFileStream, toFileStream);

				pfd.close();
				toFileStream.close();
			}
			c.close();
		}
		catch (Exception ex)
		{
			Log.e(TAG, ex.getMessage());
		}
	}

	public static void startBackgroundJob(MonitoredActivity activity, String title, String message, Runnable job,
			Handler handler)
	{
		// Make the progress dialog uncancelable, so that we can gurantee
		// the thread will be done before the activity getting destroyed.
		ProgressDialog dialog = ProgressDialog.show(activity, title, message, true, false);
		new Thread(new BackgroundJob(activity, job, dialog, handler)).start();
	}

	public static Bitmap transform(Matrix scaler, Bitmap source, int targetWidth, int targetHeight, boolean scaleUp,
			boolean recycle)
	{
		int deltaX = source.getWidth() - targetWidth;
		int deltaY = source.getHeight() - targetHeight;
		if (!scaleUp && (deltaX < 0 || deltaY < 0))
		{
			/*
			 * In this case the bitmap is smaller, at least in one dimension,
			 * than the target. Transform it by placing as much of the image
			 * as possible into the target and leaving the top/bottom or
			 * left/right (or both) black.
			 */
			Bitmap b2 = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
			Canvas c = new Canvas(b2);

			int deltaXHalf = Math.max(0, deltaX / 2);
			int deltaYHalf = Math.max(0, deltaY / 2);
			Rect src = new Rect(deltaXHalf, deltaYHalf, deltaXHalf + Math.min(targetWidth, source.getWidth()),
					deltaYHalf + Math.min(targetHeight, source.getHeight()));
			int dstX = (targetWidth - src.width()) / 2;
			int dstY = (targetHeight - src.height()) / 2;
			Rect dst = new Rect(dstX, dstY, targetWidth - dstX, targetHeight - dstY);
			c.drawBitmap(source, src, dst, null);
			if (recycle)
			{
				source.recycle();
			}
			return b2;
		}
		float bitmapWidthF = source.getWidth();
		float bitmapHeightF = source.getHeight();

		float bitmapAspect = bitmapWidthF / bitmapHeightF;
		float viewAspect = (float) targetWidth / targetHeight;

		if (bitmapAspect > viewAspect)
		{
			float scale = targetHeight / bitmapHeightF;
			if (scale < .9F || scale > 1F)
			{
				scaler.setScale(scale, scale);
			}
			else
			{
				scaler = null;
			}
		}
		else
		{
			float scale = targetWidth / bitmapWidthF;
			if (scale < .9F || scale > 1F)
			{
				scaler.setScale(scale, scale);
			}
			else
			{
				scaler = null;
			}
		}

		Bitmap b1;
		if (scaler != null)
		{
			// this is used for minithumb and crop, so we want to filter here.
			b1 = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), scaler, true);
		}
		else
		{
			b1 = source;
		}

		if (recycle && b1 != source)
		{
			source.recycle();
		}

		int dx1 = Math.max(0, b1.getWidth() - targetWidth);
		int dy1 = Math.max(0, b1.getHeight() - targetHeight);

		Bitmap b2 = Bitmap.createBitmap(b1, dx1 / 2, dy1 / 2, targetWidth, targetHeight);

		if (b2 != b1)
		{
			if (recycle || b1 != source)
			{
				b1.recycle();
			}
		}

		return b2;
	}

	private Util()
	{
	}
}
