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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.piczzamms.gallery.R;
import com.piczzamms.gallery.data.ImageManager;
import com.piczzamms.gallery.data.parts.IImage;
import com.piczzamms.gallery.data.parts.IImageList;
import com.piczzamms.gallery.top.GalleryApplication;
import com.piczzamms.gallery.ui.CropImageView;
import com.piczzamms.gallery.ui.HighlightView;
import com.piczzamms.gallery.util.MenuHelper;
import com.piczzamms.gallery.util.Util;

/**
 * The activity can crop specific region of interest from an image.
 */
public class CropImage extends MonitoredActivity
{
	static final String			TAG					= "CropImage";
	public static final String	EXTRA_SQUARE_CROP	= "square_crop";
	public static final String	EXTRA_ADD_ANOTHER	= "add_another";
	public static final String	EXTRA_OUTPUT_FILE	= "output_file";
	public static final String	EXTRA_OUTPUT_FORMAT	= "output_format";

	class CropAreaDetection implements Runnable
	{
		@SuppressWarnings("hiding")
		float	mScale	= 1F;
		Matrix	mImageMatrix;

		// Create a default HightlightView if we found no face in the
		// picture.
		void makeDefault()
		{
			HighlightView hv = new HighlightView(mImageView);

			int width = mBitmap.getWidth();
			int height = mBitmap.getHeight();

			Rect imageRect = new Rect(0, 0, width, height);

			int cropWidth = Math.min(width, height) * 9 / 10;
			int cropHeight = cropWidth;

			if (mAspectX != 0 && mAspectY != 0)
			{
				if (mAspectX > mAspectY)
				{
					cropHeight = cropWidth * mAspectY / mAspectX;
				}
				else
				{
					cropWidth = cropHeight * mAspectX / mAspectY;
				}
			}
			else if (mSquareCrop)
			{
				mAspectX = 1;
				mAspectY = 1;
			}
			int x = (width - cropWidth) / 2;
			int y = (height - cropHeight) / 2;

			RectF cropRect = new RectF(x, y, x + cropWidth, y + cropHeight);
			hv.setup(mImageMatrix, imageRect, cropRect, mAspectX != 0 && mAspectY != 0);
			mImageView.add(hv);
		}

		public void run()
		{
			mImageMatrix = mImageView.getImageMatrix();

			mScale = 1.0F / mScale;

			mHandler.post(new Runnable()
			{
				public void run()
				{
					makeDefault();

					mImageView.invalidate();
					if (mImageView.getHighlightViews().size() == 1)
					{
						mCrop = mImageView.getHighlightViews().get(0);
						mCrop.setFocus(true);
					}
				}
			});
		}
	}

	CropAreaDetection		mRunFaceDetection	= new CropAreaDetection();

	// These are various options can be specified in the intent.
	Bitmap.CompressFormat	mOutputFormat		= Bitmap.CompressFormat.JPEG;	// only used with mSaveUri
	Uri						mSaveUri;
	String					mSaveFilename;
	int						mAspectX, mAspectY;
	final Handler			mHandler			= new Handler();

	// These options specifiy the output image size and whether we should
	// scale the output to fit it (or just crop it).
	int						mOutputX, mOutputY;
	boolean					mScale;
	boolean					mScaleUp			= true;
	boolean					mSaving;
	boolean					mSquareCrop;
	CropImageView			mImageView;
	ContentResolver			mContentResolver;
	GalleryApplication		mApp;
	Bitmap					mBitmap;
	HighlightView			mCrop;
	IImageList				mAllImages;
	IImage					mImage;
	Typeface				mAltFont;

	public boolean isSaving()
	{
		return mSaving;
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		if (getApplicationContext() instanceof GalleryApplication)
		{
			mApp = (GalleryApplication) getApplicationContext();
		}
		mContentResolver = getContentResolver();

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.crop_image);

		mImageView = (CropImageView) findViewById(R.id.image);

		MenuHelper.showStorageToast(this);

		Intent intent = getIntent();
		Bundle extras = intent.getExtras();

		if (extras != null)
		{
			mSaveUri = (Uri) extras.getParcelable(MediaStore.EXTRA_OUTPUT);
			mSaveFilename = extras.getString(EXTRA_OUTPUT_FILE);
			String outputFormatString = extras.getString(EXTRA_OUTPUT_FORMAT);
			if (outputFormatString != null)
			{
				mOutputFormat = Bitmap.CompressFormat.valueOf(outputFormatString);
			}
			mSquareCrop = extras.getBoolean(EXTRA_SQUARE_CROP, false);
			mAspectX = extras.getInt("aspectX");
			mAspectY = extras.getInt("aspectY");
			mOutputX = extras.getInt("outputX");
			mOutputY = extras.getInt("outputY");
			mScale = extras.getBoolean("scale", true);
			mScaleUp = extras.getBoolean("scaleUpIfNeeded", true);
			mBitmap = (Bitmap) extras.getParcelable("data");
		}
		String altFont = intent.getStringExtra(ImageGallery.EXTRA_ALT_FONT);
		if (altFont != null)
		{
			try
			{
				mAltFont = Typeface.createFromAsset(getAssets(), altFont);
			}
			catch (Exception ex)
			{
				mApp.err(ex.getMessage());
			}
		}
		if (mBitmap == null)
		{
			Uri target = intent.getData();

			mAllImages = ImageManager.makeImageList(this, target, ImageManager.SORT_ASCENDING);
			mImage = mAllImages.getImageForUri(target);
			if (mImage != null)
			{
				// Don't read in really large bitmaps. Use the (big) thumbnail
				// instead.
				// TODO when saving the resulting bitmap use the
				// decode/crop/encode api so we don't lose any resolution.
				mBitmap = mImage.getThumbBitmap(IImage.ROTATE_AS_NEEDED);

				if (mBitmap == null)
				{
					mApp.err("BITMAP returned null for thumb " + target.toString());
				}
			}
			else
			{
				mApp.err("No image for URI " + target.toString());
			}
		}

		if (mBitmap == null)
		{
			mApp.err("CROP ABORT, NO BITMAP!!!!!!");
			finish();
			return;
		}

		// Make UI fullscreen.
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		Button btn;
		btn = (Button) findViewById(R.id.cancel);
		if (mAltFont != null)
		{
			btn.setTypeface(mAltFont);
		}
		btn.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				setResult(RESULT_CANCELED);
				finish();
			}
		});

		btn = (Button) findViewById(R.id.add);
		if (mAltFont != null)
		{
			btn.setTypeface(mAltFont);
		}
		btn.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				onSaveClicked(false);
			}
		});

		btn = (Button) findViewById(R.id.add_more);
		if (mAltFont != null)
		{
			btn.setTypeface(mAltFont);
		}
		btn.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				onSaveClicked(true);
			}
		});
		startCropAreaDetection();
	}

	@Override
	protected void onDestroy()
	{
		if (mAllImages != null)
		{
			mAllImages.close();
		}
		super.onDestroy();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
	}

	void onSaveClicked(final boolean add_another)
	{
		// TODO this code needs to change to use the decode/crop/encode single
		// step api so that we don't require that the whole (possibly large)
		// bitmap doesn't have to be read into memory
		if (mCrop == null)
		{
			return;
		}

		if (mSaving)
			return;
		mSaving = true;

		Bitmap croppedImage;

		// If the output is required to a specific size, create an new image
		// with the cropped image in the center and the extra space filled.
		if (mOutputX != 0 && mOutputY != 0 && !mScale)
		{
			// Don't scale the image but instead fill it so it's the
			// required dimension
			croppedImage = Bitmap.createBitmap(mOutputX, mOutputY, Bitmap.Config.RGB_565);
			Canvas canvas = new Canvas(croppedImage);

			Rect srcRect = mCrop.getCropRect();
			Rect dstRect = new Rect(0, 0, mOutputX, mOutputY);

			int dx = (srcRect.width() - dstRect.width()) / 2;
			int dy = (srcRect.height() - dstRect.height()) / 2;

			// If the srcRect is too big, use the center part of it.
			srcRect.inset(Math.max(0, dx), Math.max(0, dy));

			// If the dstRect is too big, use the center part of it.
			dstRect.inset(Math.max(0, -dx), Math.max(0, -dy));

			// Draw the cropped bitmap in the center
			canvas.drawBitmap(mBitmap, srcRect, dstRect, null);

			// Release bitmap memory as soon as possible
			mImageView.clear();
			mBitmap.recycle();
		}
		else
		{
			Rect r = mCrop.getCropRect();

			int width = r.width();
			int height = r.height();

			// If we are circle cropping, we want alpha channel, which is the
			// third param here.
			croppedImage = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

			Canvas canvas = new Canvas(croppedImage);
			Rect dstRect = new Rect(0, 0, width, height);
			canvas.drawBitmap(mBitmap, r, dstRect, null);

			// Release bitmap memory as soon as possible
			mImageView.clear();
			mBitmap.recycle();

			// If the required dimension is specified, scale the image.
			if (mOutputX != 0 && mOutputY != 0 && mScale)
			{
				croppedImage = Util.transform(new Matrix(), croppedImage, mOutputX, mOutputY, mScaleUp,
						Util.RECYCLE_INPUT);
			}
		}
		mImageView.setImageBitmapResetBase(croppedImage, true);
		mImageView.center(true, true);
		mImageView.getHighlightViews().clear();

		if (mApp != null && mApp.getGalleryFlurry() != null)
		{
			mApp.getGalleryFlurry().onAddPicture(this, croppedImage.getWidth(), croppedImage.getHeight());
		}
		// Return the cropped image directly or save it to the specified URI.
		Bundle myExtras = getIntent().getExtras();
		if (myExtras != null && (myExtras.getParcelable("data") != null || myExtras.getBoolean("return-data")))
		{
			Bundle extras = new Bundle();
			extras.putParcelable("data", croppedImage);
			extras.putBoolean(EXTRA_ADD_ANOTHER, add_another);
			setResult(RESULT_OK, (new Intent()).setAction("inline-data").putExtras(extras));
			finish();
		}
		else
		{
			final Bitmap b = croppedImage;
			final int msdId = R.string.savingImage;
			Util.startBackgroundJob(this, null, getResources().getString(msdId), new Runnable()
			{
				public void run()
				{
					saveOutput(b, add_another);
				}
			}, mHandler);
		}
	}

	void saveOutput(Bitmap croppedImage, boolean add_another)
	{
		if (mSaveUri != null || mSaveFilename != null)
		{
			OutputStream outputStream = null;
			try
			{
				if (mSaveUri != null)
				{
					outputStream = mContentResolver.openOutputStream(mSaveUri);
				}
				else if (mSaveFilename != null)
				{
					File file = new File(mSaveFilename);
					outputStream = new FileOutputStream(file);
				}
				if (outputStream != null)
				{
					croppedImage.compress(mOutputFormat, 75, outputStream);
				}
			}
			catch (IOException ex)
			{
				if (mSaveUri != null)
				{
					Log.e(TAG, "Cannot save file to: " + mSaveUri, ex);
				}
				else if (mSaveFilename != null)
				{
					Log.e(TAG, "Cannot save file to: " + mSaveFilename, ex);
				}
			}
			finally
			{
				Util.closeSilently(outputStream);
			}
			Bundle extras = new Bundle();
			Intent intent = new Intent();
			intent.setData(mSaveUri);
			intent.putExtra(EXTRA_OUTPUT_FILE, mSaveFilename);
			extras.putBoolean(EXTRA_ADD_ANOTHER, add_another);
			intent.putExtras(extras);
			setResult(RESULT_OK, intent);
		}
		else
		{
			Bundle extras = new Bundle();
			extras.putString("rect", mCrop.getCropRect().toString());

			File oldPath = new File(mImage.getDataPath());
			File directory = new File(oldPath.getParent());

			int x = 0;
			String fileName = oldPath.getName();
			fileName = fileName.substring(0, fileName.lastIndexOf("."));

			// Try file-1.jpg, file-2.jpg, ... until we find a filename which
			// does not exist yet.
			while (true)
			{
				x += 1;
				String candidate = directory.toString() + "/" + fileName + "-" + x + ".jpg";
				boolean exists = (new File(candidate)).exists();
				if (!exists)
				{
					break;
				}
			}

			try
			{
				int[] degree = new int[1];
				Uri newUri = ImageManager.addImage(mContentResolver, mImage.getTitle(), mImage.getDateTaken(), null,
						directory.toString(), fileName + "-" + x + ".jpg", croppedImage, null, degree);

				setResult(RESULT_OK, new Intent().setAction(newUri.toString()).putExtras(extras));
			}
			catch (Exception ex)
			{
				// basically ignore this or put up
				// some ui saying we failed
				Log.e(TAG, "store image fail, continue anyway", ex);
			}
		}

		final Bitmap b = croppedImage;
		mHandler.post(new Runnable()
		{
			public void run()
			{
				mImageView.clear();
				b.recycle();
			}
		});

		finish();
	}

	public void setCropView(HighlightView view)
	{
		mCrop = view;
	}

	void startCropAreaDetection()
	{
		if (isFinishing())
		{
			return;
		}
		mImageView.setImageBitmapResetBase(mBitmap, true);

		Runnable runCropAreaDetection = new Runnable()
		{
			public void run()
			{
				final CountDownLatch latch = new CountDownLatch(1);
				final Bitmap b = (mImage != null) ? mImage.getFullSizeBitmap(IImage.UNCONSTRAINED, 1024 * 1024)
						: mBitmap;
				mHandler.post(new Runnable()
				{
					public void run()
					{
						if (b != mBitmap && b != null)
						{
							mImageView.setImageBitmapResetBase(b, true);
							mBitmap.recycle();
							mBitmap = b;
						}
						if (mImageView.getScale() == 1F)
						{
							mImageView.center(true, true);
						}
						latch.countDown();
					}
				});
				try
				{
					latch.await();
				}
				catch (InterruptedException e)
				{
					throw new RuntimeException(e);
				}
				mRunFaceDetection.run();
			}
		};
		Util.startBackgroundJob(this, null, getResources().getString(R.string.runningFaceDetection),
				runCropAreaDetection, mHandler);
	}
}