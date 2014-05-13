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

package com.piczzamms.gallery.activities;

import java.util.ArrayList;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;

import com.piczzamms.gallery.R;
import com.piczzamms.gallery.util.Util;


public class DeleteImage extends NoSearchActivity
{
	static final String	TAG					= "DeleteImage";
	ProgressBar			mProgressBar;
	ArrayList<Uri>		mUriList;								// a list of image uri
	int					mIndex				= 0;				// next image to delete
	final Handler		mHandler			= new Handler();
	final Runnable		mDeleteNextRunnable	= new Runnable()
											{
												public void run()
												{
													deleteNext();
												}
											};
	ContentResolver		mContentResolver;
	boolean				mPausing;

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		mUriList = intent.getParcelableArrayListExtra("delete-uris");
		if (mUriList == null)
		{
			finish();
		}
		setContentView(R.layout.delete_image);
		mProgressBar = (ProgressBar) findViewById(R.id.delete_progress);
		mContentResolver = getContentResolver();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		mPausing = false;
		mHandler.post(mDeleteNextRunnable);
	}

	private void deleteNext()
	{
		if (mPausing)
		{
			return;
		}
		if (mIndex >= mUriList.size())
		{
			finish();
			return;
		}
		Uri uri = mUriList.get(mIndex++);
		// The max progress value of the bar is set to 10000 in the xml file.
		mProgressBar.setProgress(mIndex * 10000 / mUriList.size());
		// mContentResolver.delete(uri, null, null);
		Util.deleteImage(this, uri);
		mHandler.post(mDeleteNextRunnable);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		mPausing = true;
	}
}
