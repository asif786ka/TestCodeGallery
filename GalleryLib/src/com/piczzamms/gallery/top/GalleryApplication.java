package com.piczzamms.gallery.top;

import android.app.Application;
import android.util.Log;

public class GalleryApplication extends Application
{
	public static final Boolean	LOG	= false;
	public static final String	TAG	= "Gallery";

	protected IGalleryFlurry	mGalleryFlurry;

	@Override
	public void onCreate()
	{
		super.onCreate();
	}

	public IGalleryFlurry getGalleryFlurry()
	{
		return mGalleryFlurry;
	}

	public void onEmptyImageClicked()
	{
	}

	public void err(String msg)
	{
		Log.e(TAG, msg);
	}

	public void log(String msg)
	{
		if (LOG)
		{
			Log.i(TAG, msg);
		}
	}
}
