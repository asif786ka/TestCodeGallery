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

package com.piczzamms.gallery.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;

import com.piczzamms.gallery.R;
import com.piczzamms.gallery.util.MovieViewControl;


/**
 * This activity plays a video from a specified URI.
 */
public class MovieView extends NoSearchActivity
{
	static final String	TAG				= "MovieView";

	MovieViewControl	mControl;
	boolean				mFinishOnCompletion;
	boolean				mResumed		= false;		// Whether this activity has been resumed.
	boolean				mFocused		= false;		// Whether this window has focus.
	boolean				mControlResumed	= false;		// Whether the MovieViewControl is resumed.

	void handleIntent()
	{
		View rootView = findViewById(R.id.root);

		Intent intent = getIntent();
		mControl = new MovieViewControl(rootView, this, intent.getData())
		{
			@Override
			public void onCompletion()
			{
				if (mFinishOnCompletion)
				{
					finish();
				}
			}
		};
		if (intent.hasExtra(MediaStore.EXTRA_SCREEN_ORIENTATION))
		{
			int orientation = intent.getIntExtra(MediaStore.EXTRA_SCREEN_ORIENTATION,
					ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			if (orientation != getRequestedOrientation())
			{
				setRequestedOrientation(orientation);
			}
		}
		mFinishOnCompletion = intent.getBooleanExtra(MediaStore.EXTRA_FINISH_ON_COMPLETION, true);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		setContentView(R.layout.movie_view);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mResumed = false;
		if (mControlResumed)
		{
			mControl.onPause();
			mControlResumed = false;
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		handleIntent();
		mResumed = true;
		if (mFocused && mResumed && !mControlResumed)
		{
			mControl.onResume();
			mControlResumed = true;
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		mFocused = hasFocus;
		if (mFocused && mResumed && !mControlResumed)
		{
			mControl.onResume();
			mControlResumed = true;
		}
	}
}
