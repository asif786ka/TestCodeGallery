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

import java.util.Random;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.widget.Toast;

import com.piczzamms.gallery.R;
import com.piczzamms.gallery.data.ImageManager;
import com.piczzamms.gallery.data.parts.IImage;
import com.piczzamms.gallery.data.parts.IImageList;
import com.piczzamms.gallery.top.GalleryApplication;
import com.piczzamms.gallery.ui.ActionMenuButton;
import com.piczzamms.gallery.ui.ImageViewTouch;
import com.piczzamms.gallery.ui.ImageViewTouchBase;
import com.piczzamms.gallery.ui.RotateBitmap;
import com.piczzamms.gallery.util.GetterHandler;
import com.piczzamms.gallery.util.ImageGetter;
import com.piczzamms.gallery.util.ImageGetterCallback;
import com.piczzamms.gallery.util.MenuHelper;
import com.piczzamms.gallery.util.Util;




// This activity can display a whole picture and navigate them in a specific
// gallery. It has two modes: normal mode and slide show mode. In normal mode
// the user view one image at a time, and can click "previous" and "next"
// button to see the previous or next image. In slide show mode it shows one
// image after another, with some transition effect.
public class ViewImage extends NoSearchActivity implements View.OnClickListener
{
	// This is a cache for Bitmap displayed in ViewImage (normal mode, thumb only).
	class BitmapCache implements ImageViewTouchBase.Recycler
	{
		final Entry[]	mCache;

		public BitmapCache(int size)
		{
			mCache = new Entry[size];
			for (int i = 0; i < mCache.length; i++)
			{
				mCache[i] = new Entry();
			}
		}

		// Recycle all bitmaps in the cache and clear the cache.
		public synchronized void clear()
		{
			for (Entry e : mCache)
			{
				if (e.mBitmap != null)
				{
					e.mBitmap.recycle();
				}
				e.clear();
			}
		}

		// Given the position, find the associated entry. Returns null if there is
		// no such entry.
		Entry findEntry(int pos)
		{
			for (Entry e : mCache)
			{
				if (pos == e.mPos)
				{
					return e;
				}
			}
			return null;
		}

		// Returns the thumb bitmap if we have it, otherwise return null.
		public synchronized Bitmap getBitmap(int pos)
		{
			Entry e = findEntry(pos);
			if (e != null)
			{
				return e.mBitmap;
			}
			return null;
		}

		// Returns whether the bitmap is in the cache.
		public synchronized boolean hasBitmap(int pos)
		{
			Entry e = findEntry(pos);
			return (e != null);
		}

		public synchronized void put(int pos, Bitmap bitmap)
		{
			// First see if we already have this entry.
			if (findEntry(pos) != null)
			{
				return;
			}

			// Find the best entry we should replace.
			// See if there is any empty entry.
			// Otherwise assuming sequential access, kick out the entry with the
			// greatest distance.
			Entry best = null;
			int maxDist = -1;
			for (Entry e : mCache)
			{
				if (e.mPos == -1)
				{
					best = e;
					break;
				}
				else
				{
					int dist = Math.abs(pos - e.mPos);
					if (dist > maxDist)
					{
						maxDist = dist;
						best = e;
					}
				}
			}

			// Recycle the image being kicked out.
			// This only works because our current usage is sequential, so we
			// do not happen to recycle the image being displayed.
			if (best.mBitmap != null)
			{
				best.mBitmap.recycle();
			}

			best.mPos = pos;
			best.mBitmap = bitmap;
		}

		// Recycle the bitmap if it's not in the cache.
		// The input must be non-null.
		public synchronized void recycle(Bitmap b)
		{
			for (Entry e : mCache)
			{
				if (e.mPos != -1)
				{
					if (e.mBitmap == b)
					{
						return;
					}
				}
			}
			b.recycle();
		}

	}

	static class Entry
	{
		int		mPos;
		Bitmap	mBitmap;

		public Entry()
		{
			clear();
		}

		public void clear()
		{
			mPos = -1;
			mBitmap = null;
		}
	}

	class MyGestureListener implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener,
			OnScaleGestureListener
	{
		@Override
		public boolean onDoubleTap(MotionEvent e)
		{
			if (mPaused)
			{
				return false;
			}
			ImageViewTouch imageView = mImageView;

			// Switch between the original scale and 3x scale.
			if (imageView.getScale() > 2F)
			{
				mImageView.zoomTo(1f);
			}
			else
			{
				mImageView.zoomToPoint(3f, e.getX(), e.getY());
			}
			return true;
		}

		@Override
		public boolean onDoubleTapEvent(MotionEvent e)
		{
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e)
		{
			return false;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
		{
			if (mPaused)
			{
				return false;
			}
			if (velocityX > 0)
			{
				moveNextOrPrevious(-1);
			}
			else if (velocityX < 0)
			{
				moveNextOrPrevious(1);
			}
			return true;
		}

		@Override
		public void onLongPress(MotionEvent e)
		{
		}

		@Override
		public boolean onScale(ScaleGestureDetector detector)
		{
			if (mPaused)
			{
				return false;
			}
			float scale = detector.getScaleFactor();

			if (scale > 1)
			{
				mImageView.zoomIn(scale);
			}
			else
			{
				mImageView.zoomOut(scale);
			}
			return true;
		}

		@Override
		public boolean onScaleBegin(ScaleGestureDetector detector)
		{
			return true;
		}

		@Override
		public void onScaleEnd(ScaleGestureDetector detector)
		{
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
		{
			if (mPaused)
			{
				return false;
			}
			if (mImageView.getScale() > 1F)
			{
				mImageView.postTranslateCenter(-distanceX, -distanceY);
			}
			return true;
		}

		@Override
		public void onShowPress(MotionEvent e)
		{
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e)
		{
			if (mPaused)
			{
				return false;
			}
			showOnScreenControls();
			scheduleDismissOnScreenControls();
			return true;
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e)
		{
			if (mPaused)
			{
				return false;
			}
			setMode();
			return true;
		}
	}

	static final String	STATE_URI		= "uri";
	static final String	TAG				= "ViewImage";

	static final int[]	sOrderAdjacents	= new int[] {
			0, 1, -1					};

	static int getPreferencesInteger(SharedPreferences prefs, String key, int defaultValue)
	{
		String value = prefs.getString(key, null);
		try
		{
			return value == null ? defaultValue : Integer.parseInt(value);
		}
		catch (NumberFormatException ex)
		{
			Log.e(TAG, "couldn't parse preference: " + value, ex);
			return defaultValue;
		}
	}

	ImageGetter					mGetter;

	Uri							mSavedUri;
	public boolean				mPaused							= true;

	// boolean mShowControls = true;

	final GetterHandler			mHandler						= new GetterHandler();
	final Random				mRandom							= new Random(System.currentTimeMillis());
	int[]						mShuffleOrder;
	boolean						mUseShuffleOrder				= false;
	boolean						mFullScreenInNormalMode;
	boolean						mShowActionIcons;
	boolean						mUseRemoveText;
	View						mActionIconPanel;
	public int					mCurrentPosition				= 0;
	// represents which style animation to use
	int							mAnimationIndex;

	public static final String	KEY_IMAGE_LIST					= "image_list";
	public static int			RESULT_IMAGE_DELETED			= 101;
	static final String			STATE_SHOW_CONTROLS				= "show_controls";
	static final long			SHOW_CONTROLS_LENGTH			= 4000;

	IImageList					mAllImages;
	ImageManager.ImageListParam	mParam;
	GalleryApplication			mApp;
	GestureDetector				mGestureDetector;
	ScaleGestureDetector		mScaleDetector;
	// The image view displayed for normal mode.
	ImageViewTouch				mImageView;
	// This is the cache for thumbnail bitmaps.
	BitmapCache					mCache;
	public static final String		SHARE_GOOGLEPLAY_LINK		= "https://play.google.com/store/apps/details?id=com.piczzamms.snap";

	final Runnable				mDismissOnScreenControlRunner	= new Runnable()
																{
																	public void run()
																	{
																		// hideOnScreenControls();
																	}
																};

	public Runnable				mDeletePhotoRunnable			= new Runnable()
																{
																	public void run()
																	{
																		onImageDeleted();
																	}
																};

	void animStopLeft(View target)
	{
		Animation a = new ScaleAnimation(1.0f, 0.8f, 1.0f, 1.0f, target.getWidth() - 1, 0);
		a.setDuration(1000);
		a.setStartOffset(0);
		a.setInterpolator(AnimationUtils.loadInterpolator(this, android.R.anim.overshoot_interpolator));
		target.startAnimation(a);
	}

	void animStopRight(View target)
	{
		Animation a = new ScaleAnimation(1.0f, 0.8f, 1.0f, 1.0f);
		a.setDuration(1000);
		a.setStartOffset(0);
		a.setInterpolator(AnimationUtils.loadInterpolator(this, android.R.anim.overshoot_interpolator));
		target.startAnimation(a);
	}

	IImageList buildImageListFromUri(Uri uri)
	{
		int sort = ImageManager.SORT_ASCENDING;
		return ImageManager.makeImageList(this, uri, sort);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent m)
	{
		if (mPaused)
		{
			return true;
		}
		if (mActionIconPanel.getVisibility() == View.VISIBLE)
		{
			scheduleDismissOnScreenControls();
		}
		return super.dispatchTouchEvent(m);
	}

	void generateShuffleOrder()
	{
		if (mShuffleOrder == null || mShuffleOrder.length != mAllImages.getCount())
		{
			mShuffleOrder = new int[mAllImages.getCount()];
			for (int i = 0, n = mShuffleOrder.length; i < n; i++)
			{
				mShuffleOrder[i] = i;
			}
		}

		for (int i = mShuffleOrder.length - 1; i >= 0; i--)
		{
			int r = mRandom.nextInt(i + 1);
			if (r != i)
			{
				int tmp = mShuffleOrder[r];
				mShuffleOrder[r] = mShuffleOrder[i];
				mShuffleOrder[i] = tmp;
			}
		}
	}

	public int getCount()
	{
		return mAllImages.getCount();
	}

	Uri getCurrentUri()
	{
		if (mAllImages.getCount() == 0)
		{
			return null;
		}
		IImage image = mAllImages.getImageAt(mCurrentPosition);
		if (image == null)
		{
			return null;
		}
		return image.getImageUri();
	}

	public IImage getImageAt(int pos)
	{
		return mAllImages.getImageAt(pos);
	}

	void hideOnScreenControls()
	{
		if (mShowActionIcons && mActionIconPanel.getVisibility() == View.VISIBLE)
		{
			Animation animation = new AlphaAnimation(1, 0);
			animation.setDuration(500);
			mActionIconPanel.startAnimation(animation);
			mActionIconPanel.setVisibility(View.INVISIBLE);
		}
	}

	boolean init(Uri uri)
	{
		if (uri == null)
		{
			return false;
		}
		mAllImages = (mParam == null) ? buildImageListFromUri(uri) : ImageManager.makeImageList(this, mParam);
		IImage image = mAllImages.getImageForUri(uri);
		if (image == null)
		{
			return false;
		}
		mCurrentPosition = mAllImages.getImageIndex(image);
		return true;
	}

	// public boolean isPickIntent()
	// {
	// String action = getIntent().getAction();
	// return (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action));
	// }

	void loadNextImage(final int requestedPos, final long delay, final boolean firstCall)
	{
		if (firstCall && mUseShuffleOrder)
		{
			generateShuffleOrder();
		}

		final long targetDisplayTime = System.currentTimeMillis() + delay;

		ImageGetterCallback cb = new ImageGetterCallback()
		{
			public void completed()
			{
			}

			public int fullImageSizeToUse(int pos, int offset)
			{
				return 480; // TODO compute this
			}

			public void imageLoaded(final int pos, final int offset, final RotateBitmap bitmap, final boolean isThumb)
			{
			}

			public int[] loadOrder()
			{
				return new int[] {
					0 };
			}

			public boolean wantsFullImage(int pos, int offset)
			{
				return false;
			}

			public boolean wantsThumbnail(int pos, int offset)
			{
				return true;
			}
		};
		// Could be null if we're stopping a slide show in the course of pausing
		if (mGetter != null)
		{
			int pos = requestedPos;
			if (mShuffleOrder != null)
			{
				pos = mShuffleOrder[pos];
			}
			mGetter.setPosition(pos, cb, mAllImages, mHandler);
		}
	}

	void makeGetter()
	{
		mGetter = new ImageGetter(getContentResolver());
	}

	Animation makeInAnimation(int id)
	{
		Animation inAnimation = AnimationUtils.loadAnimation(this, id);
		return inAnimation;
	}

	Animation makeOutAnimation(int id)
	{
		Animation outAnimation = AnimationUtils.loadAnimation(this, id);
		return outAnimation;
	}

	void moveNextOrPrevious(int delta)
	{
		int nextImagePos = mCurrentPosition + delta;
		if ((0 <= nextImagePos) && (nextImagePos < mAllImages.getCount()))
		{
			setImage(nextImagePos, false);
		}
		else
		{
			if (nextImagePos >= mAllImages.getCount())
			{
				animStopRight(mImageView);
			}
			else
			{
				animStopLeft(mImageView);
			}
			scheduleDismissOnScreenControls();
		}
	}

	public void onClick(View v)
	{
		final int id = v.getId();

		if (id == R.id.discard)
		{
			MenuHelper.deletePhoto(this, mDeletePhotoRunnable, mUseRemoveText);
		}
		else if (id == R.id.play)
		{
			startPlayVideoActivity();
		}
		else if (id == R.id.share)
		{
			IImage image = mAllImages.getImageAt(mCurrentPosition);
			if (MenuHelper.isWhiteListUri(image.getImageUri()))
			{
				startShareMediaActivity(image);
			}
		}
		else if (id == R.id.info)
		{
			IImage image = mAllImages.getImageAt(mCurrentPosition);
			MenuHelper.showDetails(this, mHandler, image.getImageUri(), image);
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void onCreate(Bundle instanceState)
	{
		super.onCreate(instanceState);

		mApp = (GalleryApplication) getApplicationContext();
		//mApp.log("View-Image CREATE, memory=" + MemoryHelper.availableMB());

		Intent intent = getIntent();
		mFullScreenInNormalMode = intent.getBooleanExtra(MediaStore.EXTRA_FULL_SCREEN, true);
		mShowActionIcons = intent.getBooleanExtra(MediaStore.EXTRA_SHOW_ACTION_ICONS, true);
		mUseRemoveText = intent.getBooleanExtra(ImageGallery.EXTRA_USE_REMOVE, false);

		setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.view_image);

		mImageView = (ImageViewTouch) findViewById(R.id.image);
		mImageView.setEnableTrackballScroll(true);
		mCache = new BitmapCache(3);
		mImageView.setRecycler(mCache);

		makeGetter();

		mAnimationIndex = -1;

		mActionIconPanel = findViewById(R.id.action_icon_panel);

		mParam = getIntent().getParcelableExtra(KEY_IMAGE_LIST);

		if (instanceState != null)
		{
			mSavedUri = instanceState.getParcelable(STATE_URI);
			// mShowControls = instanceState.getBoolean(STATE_SHOW_CONTROLS, true);
		}
		else
		{
			mSavedUri = getIntent().getData();
		}
		int[] ids = {
				R.id.attach, R.id.cancel, R.id.play, R.id.share, R.id.discard, R.id.info };

		for (int id : ids)
		{
			View view = mActionIconPanel.findViewById(id);
			view.setOnClickListener(this);
		}
		if (mUseRemoveText)
		{
			ActionMenuButton btn = (ActionMenuButton) findViewById(R.id.discard);
			btn.setText(R.string.camera_remove);
		}
		if (mFullScreenInNormalMode)
		{
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		// if (mShowActionIcons)
		// {
		// mActionIconPanel.setVisibility(View.VISIBLE);
		// }
		setupOnScreenControls(findViewById(R.id.rootLayout), mImageView);
		setResult(RESULT_OK);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		//mApp.log("View-Image DESTROY, memory=" + MemoryHelper.availableMB());
	}

	void onImageDeleted()
	{
		if (Util.deleteImage(this, getCurrentUri()))
		{
			setResult(RESULT_IMAGE_DELETED);
			finish();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle b)
	{
		super.onSaveInstanceState(b);
		b.putParcelable(STATE_URI, mAllImages.getImageAt(mCurrentPosition).getImageUri());
	}

	@Override
	public void onStart()
	{
		super.onStart();

		//mApp.log("View-Image START, memory=" + MemoryHelper.availableMB());

		mPaused = false;

		if (!init(mSavedUri))
		{
			Log.w(TAG, "init failed: " + mSavedUri);
			finish();
			return;
		}

		// normally this will never be zero but if one "backs" into this
		// activity after removing the sdcard it could be zero. in that
		// case just "finish" since there's nothing useful that can happen.
		int count = mAllImages.getCount();
		if (count == 0)
		{
			finish();
			return;
		}
		else if (count <= mCurrentPosition)
		{
			mCurrentPosition = count - 1;
		}

		if (mGetter == null)
		{
			makeGetter();
		}
		setImage(mCurrentPosition, false);
		// mShowControls = false;
	}

	@Override
	public void onStop()
	{
		super.onStop();
		mPaused = true;

		// mGetter could be null if we call finish() and leave early in
		// onStart().
		if (mGetter != null)
		{
			mGetter.cancelCurrent();
			mGetter.stop();
			mGetter = null;
		}
		setMode();

		// removing all callback in the message queue
		mHandler.removeAllGetterCallbacks();

		if (mAllImages != null)
		{
			mSavedUri = getCurrentUri();
			mAllImages.close();
			mAllImages = null;
		}

		// hideOnScreenControls();
		mImageView.clear();
		mCache.clear();

		//mApp.log("View-Image STOP, memory=" + MemoryHelper.availableMB());
	}

	void scheduleDismissOnScreenControls()
	{
		mHandler.removeCallbacks(mDismissOnScreenControlRunner);
		mHandler.postDelayed(mDismissOnScreenControlRunner, SHOW_CONTROLS_LENGTH);
	}

	public void setImage(int pos, boolean showControls)
	{
		mCurrentPosition = pos;

		Bitmap b = mCache.getBitmap(pos);
		if (b != null)
		{
			IImage image = mAllImages.getImageAt(pos);
			mImageView.setImageRotateBitmapResetBase(new RotateBitmap(b, image.getDegreesRotated()), true);
		}
		// Could be null if we're stopping a slide show in the course of pausing
		if (mGetter != null)
		{
			ImageGetterCallback cb = new ImageGetterCallback()
			{
				public void completed()
				{
				}

				public int fullImageSizeToUse(int pos, int offset)
				{
					// this number should be bigger so that we can zoom. we may
					// need to get fancier and read in the fuller size image as the
					// user starts to zoom.
					// Originally the value is set to 480 in order to avoid OOM.
					// Now we set it to 2048 because of using
					// native memory allocation for Bitmaps.
					final int imageViewSize = 2048;
					return imageViewSize;
				}

				public void imageLoaded(int pos, int offset, RotateBitmap bitmap, boolean isThumb)
				{
					// shouldn't get here after onPause()

					// We may get a result from a previous request. Ignore it.
					if (pos != mCurrentPosition)
					{
						bitmap.recycle();
						return;
					}
					if (isThumb)
					{
						mCache.put(pos + offset, bitmap.getBitmap());
					}
					if (offset == 0)
					{
						// isThumb: We always load thumb bitmap first, so we will
						// reset the supp matrix for then thumb bitmap, and keep
						// the supp matrix when the full bitmap is loaded.
						mImageView.setImageRotateBitmapResetBase(bitmap, isThumb);
					}
				}

				public int[] loadOrder()
				{
					return sOrderAdjacents;
				}

				public boolean wantsFullImage(int pos, int offset)
				{
					return offset == 0;
				}

				public boolean wantsThumbnail(int pos, int offset)
				{
					return !mCache.hasBitmap(pos + offset);
				}
			};
			mGetter.setPosition(pos, cb, mAllImages, mHandler);
		}
		updateActionIcons();
		// if (showControls)
		// {
		showOnScreenControls();
		// }
		scheduleDismissOnScreenControls();
	}

	public void setMode()
	{
		Window win = getWindow();

		win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if (mFullScreenInNormalMode)
		{
			win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		else
		{
			win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		if (mGetter != null)
		{
			mGetter.cancelCurrent();
		}
		// if (mShowActionIcons)
		// {
		// Animation animation = new AlphaAnimation(0F, 1F);
		// animation.setDuration(500);
		// mActionIconPanel.setAnimation(animation);
		// mActionIconPanel.setVisibility(View.VISIBLE);
		// }
		mShuffleOrder = null;

		// mGetter null is a proxy for being paused
		if (mGetter != null)
		{
			setImage(mCurrentPosition, false);
		}
	}

	void setupOnScreenControls(View rootView, View ownerView)
	{
		setupOnTouchListeners(rootView);
	}

	void setupOnTouchListeners(View rootView)
	{
		MyGestureListener listener = new MyGestureListener();
		mGestureDetector = new GestureDetector(this, listener);
		mScaleDetector = new ScaleGestureDetector(this, listener);

		// If the user touches anywhere on the panel (including the
		// next/prev button). We show the on-screen controls. In addition
		// to that, if the touch is not on the prev/next button, we
		// pass the event to the gesture detector to detect double tap.
		final OnTouchListener buttonListener = new OnTouchListener()
		{
			public boolean onTouch(View v, MotionEvent event)
			{
				scheduleDismissOnScreenControls();
				return false;
			}
		};

		OnTouchListener rootListener = new OnTouchListener()
		{
			public boolean onTouch(View v, MotionEvent event)
			{
				buttonListener.onTouch(v, event);
				mScaleDetector.onTouchEvent(event);
				mGestureDetector.onTouchEvent(event);

				// We do not use the return value of
				// mGestureDetector.onTouchEvent because we will not receive
				// the "up" event if we return false for the "down" event.
				return true;
			}
		};
		rootView.setOnTouchListener(rootListener);
	}

	void showOnScreenControls()
	{
		if (mPaused)
		{
			return;
		}
		// If the view has not been attached to the window yet, the
		// zoomButtonControls will not able to show up. So delay it until the
		// view has attached to window.
		if (mActionIconPanel.getWindowToken() == null)
		{
			mHandler.postGetterCallback(new Runnable()
			{
				public void run()
				{
					showOnScreenControls();
				}
			});
			return;
		}
		if (mShowActionIcons && mActionIconPanel.getVisibility() != View.VISIBLE)
		{
			Animation animation = new AlphaAnimation(0, 1);
			animation.setDuration(500);
			mActionIconPanel.startAnimation(animation);
			mActionIconPanel.setVisibility(View.VISIBLE);
		}
	}

	void startPlayVideoActivity()
	{
		IImage image = mAllImages.getImageAt(mCurrentPosition);

		Intent intent = new Intent(this, MovieView.class);
		intent.setData(image.getImageUri());
		startActivity(intent);
	}

	void startShareMediaActivity(IImage image)
	{
		boolean isVideo = image.isVideo();
		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_SEND);
		intent.setType(image.getMimeType());
		intent.putExtra(Intent.EXTRA_STREAM, image.getImageUri());
		intent.putExtra(android.content.Intent.EXTRA_TEXT,
				getShareTextShort());
		try
		{
			startActivity(Intent.createChooser(intent, getText(isVideo ? R.string.sendVideo : R.string.sendImage)));
		}
		catch (android.content.ActivityNotFoundException ex)
		{
			Toast.makeText(this, isVideo ? R.string.no_way_to_share_image : R.string.no_way_to_share_video,
					Toast.LENGTH_SHORT).show();
		}
	}
	
	String getShareTextShort() {
		StringBuffer sbuf = new StringBuffer();
		sbuf.append(mApp.getString(R.string.share_text_short));
		sbuf.append(System.getProperty("line.separator"));
		sbuf.append(SHARE_GOOGLEPLAY_LINK);
		return sbuf.toString();
	}

	void updateActionIcons()
	{
		IImage image = mAllImages.getImageAt(mCurrentPosition);
		View panel = mActionIconPanel;

		int viewAttach = View.GONE;
		int viewCancel = View.GONE;
		int viewDiscard = View.GONE;
		int viewPlay = View.GONE;
		int viewShare = View.GONE;
		int viewInfo = View.VISIBLE;

		if (mShowActionIcons && MenuHelper.isWhiteListUri(mSavedUri))
		{
			// if (isPickIntent())
			// {
			// viewAttach = View.VISIBLE;
			// viewCancel = View.VISIBLE;
			// }
			// else
			{
				viewDiscard = View.VISIBLE;
				viewPlay = View.VISIBLE;
				viewShare = View.VISIBLE;
			}
		}
		else
		{
			viewDiscard = View.VISIBLE;
		}
		if (image.isVideo())
		{
			viewPlay = View.VISIBLE;
		}
		else
		{
			viewPlay = View.GONE;
		}
		panel.findViewById(R.id.play).setVisibility(viewPlay);
		panel.findViewById(R.id.attach).setVisibility(viewAttach);
		panel.findViewById(R.id.cancel).setVisibility(viewCancel);
		panel.findViewById(R.id.discard).setVisibility(viewDiscard);
		panel.findViewById(R.id.share).setVisibility(viewShare);
		panel.findViewById(R.id.info).setVisibility(viewInfo);
	}
}
