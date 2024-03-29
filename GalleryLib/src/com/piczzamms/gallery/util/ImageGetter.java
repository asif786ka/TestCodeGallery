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

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.os.Process;

import com.piczzamms.gallery.activities.ImageGallery;
import com.piczzamms.gallery.data.BitmapManager;
import com.piczzamms.gallery.data.parts.IImage;
import com.piczzamms.gallery.data.parts.IImageList;
import com.piczzamms.gallery.ui.RotateBitmap;

/*
 * Here's the loading strategy. For any given image, load the thumbnail
 * into memory and post a callback to display the resulting bitmap.
 * 
 * Then proceed to load the full image bitmap. Three things can
 * happen at this point:
 * 
 * 1. the image fails to load because the UI thread decided
 * to move on to a different image. This "cancellation" happens
 * by virtue of the UI thread closing the stream containing the
 * image being decoded. BitmapFactory.decodeStream returns null
 * in this case.
 * 
 * 2. the image loaded successfully. At that point we post
 * a callback to the UI thread to actually show the bitmap.
 * 
 * 3. when the post runs it checks to see if the image that was
 * loaded is still the one we want. The UI may have moved on
 * to some other image and if so we just drop the newly loaded
 * bitmap on the floor.
 */

public class ImageGetter
{

	@SuppressWarnings("unused")
	static final String	TAG					= ImageGallery.TAG;

	// The thread which does the work.
	Thread				mGetterThread;

	// The current request serial number.
	// This is increased by one each time a new job is assigned.
	// It is only written in the main thread.
	int					mCurrentSerial;

	// The base position that's being retrieved. The actual images retrieved
	// are this base plus each of the offets. -1 means there is no current
	// request needs to be finished.
	int					mCurrentPosition	= -1;

	// The callback to invoke for each image.
	ImageGetterCallback	mCB;
	// The image list for the images.
	IImageList			mImageList;

	// The handler to do callback.
	GetterHandler		mHandler;

	// True if we want to cancel the current loading.
	volatile boolean	mCancel				= true;

	// True if the getter thread is idle waiting.
	boolean				mIdle				= false;

	// True when the getter thread should exit.
	boolean				mDone				= false;

	ContentResolver		mCr;

	class ImageGetterRunnable implements Runnable
	{

		Runnable callback(final int position, final int offset, final boolean isThumb, final RotateBitmap bitmap,
				final int requestSerial)
		{
			return new Runnable()
			{
				public void run()
				{
					// check for inflight callbacks that aren't applicable
					// any longer before delivering them
					if (requestSerial == mCurrentSerial)
					{
						mCB.imageLoaded(position, offset, bitmap, isThumb);
					}
					else if (bitmap != null)
					{
						bitmap.recycle();
					}
				}
			};
		}

		Runnable completedCallback(final int requestSerial)
		{
			return new Runnable()
			{
				public void run()
				{
					if (requestSerial == mCurrentSerial)
					{
						mCB.completed();
					}
				}
			};
		}

		public void run()
		{
			// Lower the priority of this thread to avoid competing with
			// the UI thread.
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

			while (true)
			{
				synchronized (ImageGetter.this)
				{
					while (mCancel || mDone || mCurrentPosition == -1)
					{
						if (mDone)
							return;
						mIdle = true;
						ImageGetter.this.notify();
						try
						{
							ImageGetter.this.wait();
						}
						catch (InterruptedException ex)
						{
							// ignore
						}
						mIdle = false;
					}
				}

				executeRequest();

				synchronized (ImageGetter.this)
				{
					mCurrentPosition = -1;
				}
			}
		}

		void executeRequest()
		{
			int imageCount = mImageList.getCount();

			int[] order = mCB.loadOrder();
			for (int i = 0; i < order.length; i++)
			{
				if (mCancel)
				{
					return;
				}
				int offset = order[i];
				int imageNumber = mCurrentPosition + offset;
				if (imageNumber >= 0 && imageNumber < imageCount)
				{
					if (!mCB.wantsThumbnail(mCurrentPosition, offset))
					{
						continue;
					}

					IImage image = mImageList.getImageAt(imageNumber);
					if (image == null)
						continue;
					if (mCancel)
					{
						return;
					}

					Bitmap b = image.getThumbBitmap(IImage.NO_ROTATE);
					if (b == null)
						continue;
					if (mCancel)
					{
						b.recycle();
						return;
					}

					Runnable cb = callback(mCurrentPosition, offset, true,
							new RotateBitmap(b, image.getDegreesRotated()), mCurrentSerial);
					mHandler.postGetterCallback(cb);
				}
			}

			for (int i = 0; i < order.length; i++)
			{
				if (mCancel)
				{
					return;
				}
				int offset = order[i];
				int imageNumber = mCurrentPosition + offset;
				if (imageNumber >= 0 && imageNumber < imageCount)
				{
					if (!mCB.wantsFullImage(mCurrentPosition, offset))
					{
						continue;
					}

					IImage image = mImageList.getImageAt(imageNumber);
					if (image == null)
						continue;
					if (image.isVideo())
						continue;
					if (mCancel)
					{
						return;
					}

					int sizeToUse = mCB.fullImageSizeToUse(mCurrentPosition, offset);
					Bitmap b = image.getFullSizeBitmap(sizeToUse, 3 * 1024 * 1024, IImage.NO_ROTATE, IImage.USE_NATIVE);

					if (b == null)
						continue;
					if (mCancel)
					{
						b.recycle();
						return;
					}

					RotateBitmap rb = new RotateBitmap(b, image.getDegreesRotated());

					Runnable cb = callback(mCurrentPosition, offset, false, rb, mCurrentSerial);
					mHandler.postGetterCallback(cb);
				}
			}
			mHandler.postGetterCallback(completedCallback(mCurrentSerial));

		}
	}

	public ImageGetter(ContentResolver cr)
	{
		mCr = cr;
		mGetterThread = new Thread(new ImageGetterRunnable());
		mGetterThread.setName("ImageGettter");
		mGetterThread.start();
	}

	// Cancels current loading (without waiting).
	public synchronized void cancelCurrent()
	{
		Util.Assert(mGetterThread != null);
		mCancel = true;
		BitmapManager.instance().cancelThreadDecoding(mGetterThread, mCr);
	}

	// Cancels current loading (with waiting).
	private synchronized void cancelCurrentAndWait()
	{
		cancelCurrent();
		while (mIdle != true)
		{
			try
			{
				wait();
			}
			catch (InterruptedException ex)
			{
				// ignore.
			}
		}
	}

	// Stops this image getter.
	public void stop()
	{
		synchronized (this)
		{
			cancelCurrentAndWait();
			mDone = true;
			notify();
		}
		try
		{
			mGetterThread.join();
		}
		catch (InterruptedException ex)
		{
			// Ignore the exception
		}
		mGetterThread = null;
	}

	public synchronized void setPosition(int position, ImageGetterCallback cb, IImageList imageList,
			GetterHandler handler)
	{
		// Cancel the previous request.
		cancelCurrentAndWait();

		// Set new data.
		mCurrentPosition = position;
		mCB = cb;
		mImageList = imageList;
		mHandler = handler;
		mCurrentSerial += 1;

		// Kick-start the current request.
		mCancel = false;
		BitmapManager.instance().allowThreadDecoding(mGetterThread);
		notify();
	}
}
