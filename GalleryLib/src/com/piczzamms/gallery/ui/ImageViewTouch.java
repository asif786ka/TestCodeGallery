package com.piczzamms.gallery.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;

import com.piczzamms.gallery.activities.ViewImage;
import com.piczzamms.gallery.util.MenuHelper;

public class ImageViewTouch extends ImageViewTouchBase
{
	private final ViewImage	mViewImage;
	private boolean			mEnableTrackballScroll;

	public ImageViewTouch(Context context)
	{
		super(context);
		mViewImage = (ViewImage) context;
	}

	public ImageViewTouch(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		mViewImage = (ViewImage) context;
	}

	public void setEnableTrackballScroll(boolean enable)
	{
		mEnableTrackballScroll = enable;
	}

	public void postTranslateCenter(float dx, float dy)
	{
		super.postTranslate(dx, dy);
		center(true, true);
	}

	private static final float	PAN_RATE	= 20;

	// This is the time we allow the dpad to change the image position again.
	private long				mNextChangePositionTime;

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if (mViewImage.mPaused)
			return false;

		// Don't respond to arrow keys if trackball scrolling is not enabled
		if (!mEnableTrackballScroll)
		{
			if ((keyCode >= KeyEvent.KEYCODE_DPAD_UP) && (keyCode <= KeyEvent.KEYCODE_DPAD_RIGHT))
			{
				return super.onKeyDown(keyCode, event);
			}
		}

		int current = mViewImage.mCurrentPosition;

		int nextImagePos = -2; // default no next image
		try
		{
			switch (keyCode)
			{
				case KeyEvent.KEYCODE_DPAD_CENTER:
				{
					// if (mViewImage.isPickIntent())
					// {
					// IImage img = mViewImage.getImageAt(mViewImage.mCurrentPosition);
					// mViewImage.setResult(ViewImage.RESULT_OK, new Intent().setData(img.getImageUri()));
					// mViewImage.finish();
					// }
					break;
				}
				case KeyEvent.KEYCODE_DPAD_LEFT:
				{
					if (getScale() <= 1F && event.getEventTime() >= mNextChangePositionTime)
					{
						nextImagePos = current - 1;
						mNextChangePositionTime = event.getEventTime() + 500;
					}
					else
					{
						panBy(PAN_RATE, 0);
						center(true, false);
					}
					return true;
				}
				case KeyEvent.KEYCODE_DPAD_RIGHT:
				{
					if (getScale() <= 1F && event.getEventTime() >= mNextChangePositionTime)
					{
						nextImagePos = current + 1;
						mNextChangePositionTime = event.getEventTime() + 500;
					}
					else
					{
						panBy(-PAN_RATE, 0);
						center(true, false);
					}
					return true;
				}
				case KeyEvent.KEYCODE_DPAD_UP:
				{
					panBy(0, PAN_RATE);
					center(false, true);
					return true;
				}
				case KeyEvent.KEYCODE_DPAD_DOWN:
				{
					panBy(0, -PAN_RATE);
					center(false, true);
					return true;
				}
				case KeyEvent.KEYCODE_DEL:
					MenuHelper.deletePhoto(mViewImage, mViewImage.mDeletePhotoRunnable, false);
					break;
			}
		}
		finally
		{
			if (nextImagePos >= 0 && nextImagePos < mViewImage.getCount())
			{
				synchronized (mViewImage)
				{
					mViewImage.setMode();
					mViewImage.setImage(nextImagePos, true);
				}
			}
			else if (nextImagePos != -2)
			{
				center(true, true);
			}
		}

		return super.onKeyDown(keyCode, event);
	}
}
