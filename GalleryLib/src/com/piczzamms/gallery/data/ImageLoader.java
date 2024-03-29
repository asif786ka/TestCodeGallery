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

import java.util.ArrayList;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.os.Handler;

import com.piczzamms.gallery.data.parts.IImage;

/**
 * A dedicated decoding thread used by ImageGallery.
 */
public class ImageLoader
{
	public interface LoadedCallback
	{
		public void run(Bitmap result);
	}

	class WorkerThread implements Runnable
	{
		// Pick off items on the queue, one by one, and compute their bitmap.
		// Place the resulting bitmap in the cache, then call back by executing
		// the given runnable so things can get updated appropriately.
		public void run()
		{
			while (true)
			{
				WorkItem workItem = null;
				synchronized (mQueue)
				{
					if (mDone)
					{
						break;
					}
					if (!mQueue.isEmpty())
					{
						workItem = mQueue.remove(0);
					}
					else
					{
						try
						{
							mQueue.wait();
						}
						catch (InterruptedException ex)
						{
							// ignore the exception
						}
						continue;
					}
				}
				final Bitmap b = workItem.mImage.getMiniThumbBitmap();

				if (workItem.mOnLoadedRunnable != null)
				{
					workItem.mOnLoadedRunnable.run(b);
				}
			}
		}
	}

	static class WorkItem
	{
		IImage			mImage;
		LoadedCallback	mOnLoadedRunnable;
		int				mTag;

		WorkItem(IImage image, LoadedCallback onLoadedRunnable, int tag)
		{
			mImage = image;
			mOnLoadedRunnable = onLoadedRunnable;
			mTag = tag;
		}
	}

	@SuppressWarnings("unused")
	static final String			TAG		= "ImageLoader";
	// Queue of work to do in the worker thread. The work is done in order.
	final ArrayList<WorkItem>	mQueue	= new ArrayList<WorkItem>();

	// the worker thread and a done flag so we know when to exit
	boolean						mDone;

	Thread						mDecodeThread;

	ContentResolver				mCr;

	public ImageLoader(ContentResolver cr, Handler handler)
	{
		mCr = cr;
		start();
	}

	public boolean cancel(final IImage image)
	{
		synchronized (mQueue)
		{
			int index = findItem(image);
			if (index >= 0)
			{
				mQueue.remove(index);
				return true;
			}
			else
			{
				return false;
			}
		}
	}

	// Clear the queue. Returns an array of tags that were in the queue.
	public int[] clearQueue()
	{
		synchronized (mQueue)
		{
			int n = mQueue.size();
			int[] tags = new int[n];
			for (int i = 0; i < n; i++)
			{
				tags[i] = mQueue.get(i).mTag;
			}
			mQueue.clear();
			return tags;
		}
	}

	// The caller should hold mQueue lock.
	int findItem(IImage image)
	{
		for (int i = 0; i < mQueue.size(); i++)
		{
			if (mQueue.get(i).mImage == image)
			{
				return i;
			}
		}
		return -1;
	}

	public void getBitmap(IImage image, LoadedCallback imageLoadedRunnable, int tag)
	{
		if (mDecodeThread == null)
		{
			start();
		}
		synchronized (mQueue)
		{
			WorkItem w = new WorkItem(image, imageLoadedRunnable, tag);
			mQueue.add(w);
			mQueue.notifyAll();
		}
	}

	void start()
	{
		if (mDecodeThread != null)
		{
			return;
		}
		mDone = false;
		Thread t = new Thread(new WorkerThread());
		t.setName("image-loader");
		mDecodeThread = t;
		t.start();
	}

	public void stop()
	{
		synchronized (mQueue)
		{
			mDone = true;
			mQueue.notifyAll();
		}
		if (mDecodeThread != null)
		{
			try
			{
				Thread t = mDecodeThread;
				BitmapManager.instance().cancelThreadDecoding(t, mCr);
				t.join();
				mDecodeThread = null;
			}
			catch (InterruptedException ex)
			{
				// so now what?
			}
		}
	}
}
