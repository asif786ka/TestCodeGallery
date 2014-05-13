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
import java.util.ArrayList;
import java.util.HashSet;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.piczzamms.gallery.R;
import com.piczzamms.gallery.data.ImageManager;
import com.piczzamms.gallery.data.ImageManager.DataLocation;
import com.piczzamms.gallery.data.parts.IImage;
import com.piczzamms.gallery.data.parts.IImageList;
import com.piczzamms.gallery.top.GalleryApplication;
import com.piczzamms.gallery.ui.GridViewPictures;
import com.piczzamms.gallery.util.MenuHelper;
import com.piczzamms.gallery.util.Util;


public class ImageGalleryRobo extends NoSearchActivity implements GridViewPictures.Listener
{
	class CreateContextMenuListener implements View.OnCreateContextMenuListener
	{
		public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
		{
			IImage image = getCurrentImage();

			if (image == null)
			{
				return;
			}

			boolean isImage = ImageManager.isImage(image);
			if (isImage)
			{
				menu.add(R.string.view).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener()
				{
					public boolean onMenuItemClick(MenuItem item)
					{

						onImageClicked(mGvs.getCurrentSelection(), false);
						return true;
					}
				});
			}

			menu.setHeaderTitle(isImage ? R.string.context_menu_header : R.string.video_context_menu_header);
			if ((mInclusion & (ImageManager.INCLUDE_IMAGES | ImageManager.INCLUDE_VIDEOS)) != 0)
			{
				MenuHelper.MenuItemsResult r = MenuHelper.addImageMenuItems(menu, MenuHelper.INCLUDE_ALL,
						ImageGalleryRobo.this, mHandler, mDeletePhotoRunnable, new MenuHelper.MenuInvoker()
						{
							public void run(MenuHelper.MenuCallback cb)
							{
								cb.run(getCurrentImageUri(), getCurrentImage());
								// mGvs.invalidateImage(mGvs.getCurrentSelection());
							}
						}, mUseRemoveText);

				if (r != null)
				{
					r.gettingReadyToOpen(menu, image);
				}
			}
		}
	}

	public static final String	TAG							= "ImageGallery";

	static final String			STATE_SCROLL_POSITION		= "scroll_position";
	static final String			STATE_SELECTED_INDEX		= "first_index";
	static final float			INVALID_POSITION			= -1f;

	public final static String	EXTRA_INTERNAL_MEDIA		= "internal_media";
	public final static String	EXTRA_INTERNAL_DATA			= "internal_data";
	public final static String	EXTRA_ADD_PICTURE			= "add_picture";
	public final static String	EXTRA_ADD_WITH_SQAURE_CROP	= "add_picture_with_square_crop";
	public final static String	EXTRA_EMPTY_TEXT			= "empty_text";
	public final static String	EXTRA_EMPTY_IMAGE_RESOURCE	= "empty_image_resource";
	public final static String	EXTRA_EMPTY_IMAGE_AS_BUTTON	= "empty_image_as_button";
	public final static String	EXTRA_SHOW_EMPTY_COUNT		= "show_empty_at_count";
	public final static String	EXTRA_ALT_FONT				= "alt_font";
	public final static String	EXTRA_ALT_FONT_SIZE			= "alt_font_size";
	public final static String	EXTRA_HORIZONTAL			= "horizontal";
	public final static String	EXTRA_TITLE					= "title";
	public final static String	EXTRA_INSTRUCTION_TEXT		= "instruction_text";
	public final static String	EXTRA_INSTRUCTION_COUNT		= "instruction_count";
	public final static String	EXTRA_DRAG					= "drag";
	public final static String	EXTRA_USE_REMOVE			= "use_remove_text";

	public final static String	EMPTY_PLUS_TEXT				= "PLUS";

	public static void SetHome(Class claz)
	{
		mHomeClassGalleryRobo = claz;
	}

	GalleryApplication			mApp;
	ImageManager.ImageListParam	mParam;
	int							mInclusion;
	boolean						mSortAscending			= true;
	View						mNoImagesView;
	int							mShowEmptyAtCount;
	Dialog						mMediaScanningDialog;
	View						mFooterOrganizeView;
	BroadcastReceiver			mReceiver;
	final Handler				mHandler				= new Handler();
	boolean						mLayoutComplete;
	boolean						mUseRemoveText;
	boolean						mDragEnable;
	GridViewPictures			mGvs;
	int							mSelectedIndex			= GridViewPictures.INDEX_NONE;
	float						mScrollPosition			= INVALID_POSITION;
	boolean						mConfigurationChanged;
	boolean						mHorizontal;
	static Class				mHomeClassGalleryRobo;
	TextView					mInstruction;
	String						mInstructionText;
	int							mInstructionCount;
	/** If not null we are allowed to add new pictures to the internal store */
	String						mAddPictureDir;
	Typeface					mAltFont;
	int							mAltFontSize;
	/** When adding a picture, allow the user to square crop the image first. */
	boolean						mAddPictureWithSquareCrop;

	final Runnable				mDeletePhotoRunnable	= new Runnable()
														{
															public void run()
															{
																onImageDelete();
															}
														};

	Animation					mFooterAppear;
	Animation					mFooterDisappear;
	int							mMovieOrientation;

	// Returns the image list parameter which contains the subset of image/video
	// we want.
	ImageManager.ImageListParam allImages(boolean storageAvailable)
	{
		if (!storageAvailable)
		{
			return ImageManager.getEmptyImageListParam();
		}
		else
		{
			Intent intent = getIntent();
			Uri uri = intent.getData();
			String bucketId;
			if (uri != null)
			{
				bucketId = ImageManager.getBucketId(uri.getPath());
			}
			else
			{
				bucketId = null;
			}
			DataLocation location = ImageManager.DataLocation.EXTERNAL;
			int sort = mSortAscending ? ImageManager.SORT_ASCENDING : ImageManager.SORT_DESCENDING;

			if (intent.getBooleanExtra(EXTRA_INTERNAL_DATA, false))
			{
				return ImageManager.getInternalImageListParam(uri, mInclusion, sort);
			}
			if (intent.getBooleanExtra(EXTRA_INTERNAL_MEDIA, false))
			{
				location = ImageManager.DataLocation.INTERNAL;
			}
			return ImageManager.getImageListParam(location, mInclusion, sort, bucketId);
		}
	}

	void cancelWaitDialog()
	{
		if (mMediaScanningDialog != null)
		{
			mMediaScanningDialog.cancel();
			mMediaScanningDialog = null;
		}
	}

	Spannable getAltEmpty(String altString)
	{
		int pos = altString.indexOf(EMPTY_PLUS_TEXT);
		if (pos >= 0)
		{
			SpannableStringBuilder ssb = new SpannableStringBuilder(altString);
			Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_add_picture);
			ssb.setSpan(new ImageSpan(this, icon), pos, pos + EMPTY_PLUS_TEXT.length(),
					Spannable.SPAN_INCLUSIVE_INCLUSIVE);
			return ssb;
		}
		return new SpannableString(altString);
	}

	Activity getContext()
	{
		return this;
	}

	IImage getCurrentImage()
	{
		return mGvs.getCurrentImage();
	}

	Uri getCurrentImageUri()
	{
		IImage image = getCurrentImage();
		if (image != null)
		{
			return image.getImageUri();
		}
		else
		{
			return null;
		}
	}

	String getShareMultipleMimeType()
	{
		final int FLAG_IMAGE = 1, FLAG_VIDEO = 2;
		int flag = 0;
		for (IImage image : mGvs.getMultiselect().getList())
		{
			flag |= ImageManager.isImage(image) ? FLAG_IMAGE : FLAG_VIDEO;
		}
		return flag == FLAG_IMAGE ? "image/*" : flag == FLAG_VIDEO ? "video/*" : "*/*";
	}

	String getTmpFile()
	{
		File dir = getApplicationContext().getCacheDir();
		String file = dir.getAbsolutePath() + File.separator + "cropped.jpg";
		return file;
	}

	void handleIntent()
	{
		Intent intent = getIntent();
		mAddPictureDir = intent.getStringExtra(EXTRA_ADD_PICTURE);
		mAddPictureWithSquareCrop = intent.getBooleanExtra(EXTRA_ADD_WITH_SQAURE_CROP, false);
		mDragEnable = intent.getBooleanExtra(EXTRA_DRAG, false);
		String altFont = intent.getStringExtra(EXTRA_ALT_FONT);
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
		mAltFontSize = intent.getIntExtra(EXTRA_ALT_FONT_SIZE, 0);
		mInstructionText = intent.getStringExtra(EXTRA_INSTRUCTION_TEXT);
		mInstructionCount = intent.getIntExtra(EXTRA_INSTRUCTION_COUNT, 3);
		mUseRemoveText = intent.getBooleanExtra(EXTRA_USE_REMOVE, false);
		mHorizontal = intent.getBooleanExtra(EXTRA_HORIZONTAL, false);
		mShowEmptyAtCount = intent.getIntExtra(EXTRA_SHOW_EMPTY_COUNT, 0);
		mMovieOrientation = intent.getIntExtra(MediaStore.EXTRA_SCREEN_ORIENTATION,
				ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
	}

	void hideFooter()
	{
		if (mFooterOrganizeView.getVisibility() != View.GONE)
		{
			mFooterOrganizeView.setVisibility(View.GONE);
			if (mFooterDisappear == null)
			{
				mFooterDisappear = AnimationUtils.loadAnimation(this, R.anim.footer_disappear);
			}
			mFooterOrganizeView.startAnimation(mFooterDisappear);
			RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mGvs.getLayoutParams();
			params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	void initEmptyImage()
	{
		Intent intent = getIntent();
		String emptyText = intent.getStringExtra(EXTRA_EMPTY_TEXT);
		TextView emptyTextView = (TextView) findViewById(R.id.no_pictures_text);
		int emptyResId = intent.getIntExtra(EXTRA_EMPTY_IMAGE_RESOURCE, 0);
		boolean emptyImageAsButton = intent.getBooleanExtra(EXTRA_EMPTY_IMAGE_AS_BUTTON, false);
		mNoImagesView = findViewById(R.id.no_images);

		if (emptyResId != 0)
		{
			ImageView imageView = (ImageView) findViewById(R.id.empty_image_view);

			if (emptyImageAsButton)
			{
				imageView.setVisibility(View.GONE);
				ImageButton button = (ImageButton) findViewById(R.id.empty_image_button);
				button.setVisibility(View.VISIBLE);
				button.setImageResource(emptyResId);
				button.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						mApp.onEmptyImageClicked();
					}
				});
			}
			else
			{
				imageView.setBackgroundResource(emptyResId);
			}
		}
		if (emptyText != null)
		{
			emptyTextView.setText(getAltEmpty(emptyText));
		}
		if (mAltFont != null)
		{
			emptyTextView.setTypeface(mAltFont);
		}
		if (mAltFontSize > 0)
		{
			emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mAltFontSize);
		}
		if (mAddPictureDir != null)
		{
			emptyTextView.setOnClickListener(new OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					MenuHelper.onPicturePicked(ImageGalleryRobo.this);
				}
			});
		}
	}

	void initializeFooterButtons()
	{
		ImageButton deleteButton = (ImageButton) findViewById(R.id.button_delete);
		deleteButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				onDeleteMultipleClicked();
			}
		});

		ImageButton shareButton = (ImageButton) findViewById(R.id.button_share);
		shareButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				onShareMultipleClicked();
			}
		});

		ImageButton closeButton = (ImageButton) findViewById(R.id.button_close);
		closeButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				multiselectModeClose();
			}
		});
	}

	boolean isEmpty()
	{
		return mNoImagesView.getVisibility() == View.VISIBLE;
	}

	boolean isImageType(String type)
	{
		return type.equals("vnd.android.cursor.dir/image") || type.equals("image/*");
	}

	boolean isInMultiSelectMode()
	{
		return mGvs.getMultiselect().isEnabled();
	}

	boolean isPickIntent()
	{
		String action = getIntent().getAction();
		return (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action));
	}

	boolean isVideoSelected()
	{
		IImage image = getCurrentImage();
		return (image != null) && ImageManager.isVideo(image);
	}

	boolean isVideoType(String type)
	{
		return type.equals("vnd.android.cursor.dir/video") || type.equals("video/*");
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	void multiselectModeClose()
	{
		if (mGvs.getMultiselect().isEnabled())
		{
			mGvs.getMultiselect().clear();
			mGvs.invalidate();
			hideFooter();

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			{
				invalidateOptionsMenu();
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	void multiselectModeOpen()
	{
		if (!mGvs.getMultiselect().isEnabled())
		{
			mGvs.getMultiselect().setEnabled();

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			{
				invalidateOptionsMenu();
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
			case MenuHelper.REQUEST_PICK_IMAGE:
			{
				if (mAddPictureDir != null && data != null && data.getData() != null)
				{
					Uri fromUri = data.getData();

					if (mAddPictureWithSquareCrop)
					{
						Intent intent = new Intent(getContext(), CropImage.class);
						intent.setAction(Intent.ACTION_PICK);
						intent.setData(fromUri);
						intent.putExtra(CropImage.EXTRA_SQUARE_CROP, true);
						intent.putExtra(CropImage.EXTRA_OUTPUT_FILE, getTmpFile());
						intent.putExtra(CropImage.EXTRA_OUTPUT_FORMAT, CompressFormat.JPEG.toString());
						intent.putExtra(EXTRA_ALT_FONT, getIntent().getStringExtra(EXTRA_ALT_FONT));
						startActivityForResult(intent, MenuHelper.REQUEST_CROP_IMAGE);
					}
					else
					{
						Util.saveImage(this, fromUri, mAddPictureDir);
						rebake();
					}
				}
				break;
			}
			case MenuHelper.REQUEST_CROP_IMAGE:
			{
				if (mAddPictureDir != null)
				{
					if (data != null)
					{
						Uri saveUri = data.getData();
						if (saveUri != null)
						{
							Util.saveImage(this, saveUri, mAddPictureDir);
						}
						else
						{
							String saveFilename = data.getStringExtra(CropImage.EXTRA_OUTPUT_FILE);
							if (saveFilename != null)
							{
								Util.saveImage(this, saveFilename, mAddPictureDir);
							}
						}
						rebake();

						if (data.getBooleanExtra(CropImage.EXTRA_ADD_ANOTHER, false))
						{
							MenuHelper.onPicturePicked(getContext());
						}
					}
				}
				break;
			}
			case MenuHelper.REQUEST_VIEW_IMAGE:
			{
				if (resultCode == ViewImage.RESULT_IMAGE_DELETED)
				{
					mGvs.setSelectedIndex(GridViewPictures.INDEX_NONE);
					rebake();
				}
			}
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		mConfigurationChanged = true;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		mApp = (GalleryApplication) getApplicationContext();

		// mApp.log("Image-Gallery CREATE: " + MemoryHelper.availableMB() + " MB");

		handleIntent();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		{

			getActionBar().setDisplayHomeAsUpEnabled(true);

		}
		setContentView(R.layout.image_gallery);

		initEmptyImage();

		mGvs = (GridViewPictures) findViewById(R.id.grid);
		mGvs.setListener(this);

		mFooterOrganizeView = findViewById(R.id.footer_organize);
		mFooterOrganizeView.setOnClickListener(Util.getNullOnClickListener());

		mInstruction = (TextView) findViewById(R.id.instruction);

		initializeFooterButtons();

		long limit;
		if (isPickIntent())
		{
			limit = getIntent().getLongExtra(MediaStore.EXTRA_SIZE_LIMIT, Long.MAX_VALUE);
		}
		else
		{
			limit = Long.MAX_VALUE;
			mGvs.setOnCreateContextMenuListener(new CreateContextMenuListener());
		}
		mGvs.setVideoSizeLimit(limit);

		setupInclusion();

	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		if (!isPickIntent())
		{
			MenuItem item;

			if (mAddPictureDir != null)
			{
				item = MenuHelper.addPickPicture(menu, this);
			}
			if (!isEmpty())
			{
				item = menu.add(Menu.NONE, MenuHelper.POSITION_MULTISELECT, MenuHelper.POSITION_MULTISELECT,
						R.string.multiselect);
				item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener()
				{
					public boolean onMenuItemClick(MenuItem item)
					{
						if (isInMultiSelectMode())
						{
							multiselectModeClose();
						}
						else
						{
							multiselectModeOpen();
						}
						return true;
					}
				});
				item.setIcon(R.drawable.ic_menu_multiselect_gallery);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				{
					item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
				}
				if (mGvs.isDragEnabled())
				{
					item = menu.add(Menu.NONE, MenuHelper.POSITION_IMAGE_DELETE, MenuHelper.POSITION_IMAGE_DELETE,
							(mUseRemoveText ? R.string.camera_remove : R.string.camera_delete));
					item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener()
					{
						public boolean onMenuItemClick(MenuItem item)
						{
							IImage image = getCurrentImage();
							if (image != null)
							{
								MenuHelper.deleteImage(getContext(), mDeletePhotoRunnable, image, mUseRemoveText);
							}
							return true;
						}
					});
					item.setIcon(android.R.drawable.ic_menu_delete);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
					{
						item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
					}
				}
			}
			item = menu.add(Menu.NONE, MenuHelper.POSITION_REFRESH, MenuHelper.POSITION_REFRESH, R.string.refresh);
			item.setIcon(R.drawable.ic_menu_refresh);
			item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener()
			{
				public boolean onMenuItemClick(MenuItem item)
				{
					refresh();
					return true;
				}
			});
		}
		return true;
	}

	void onDeleteMultipleClicked()
	{
		if (isInMultiSelectMode())
		{
			Runnable action = new Runnable()
			{
				public void run()
				{
					ArrayList<Uri> uriList = new ArrayList<Uri>();
					for (IImage image : mGvs.getMultiselect().getList())
					{
						uriList.add(image.getImageUri());
					}
					multiselectModeClose();
					Intent intent = new Intent(ImageGalleryRobo.this, DeleteImage.class);
					intent.putExtra("delete-uris", uriList);
					try
					{
						startActivity(intent);
					}
					catch (ActivityNotFoundException ex)
					{
						Log.e(TAG, "Delete images fail", ex);
					}
				}
			};
			MenuHelper.deleteMultiple(this, action);
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		// mApp.log("Image-Gallery DESTROY: " + MemoryHelper.availableMB() + " MB");
	}

	@Override
	public void onDrag(int from_index, int to_index)
	{
		rebake();
	}

	public void onImageClicked(int index, boolean fromKeypad)
	{
		/**
		 * In the multiselect mode, once the finger finishes tapping, we hide
		 * the selection box by setting the selected index to none. However, if
		 * we use the dpad center key, we will keep the selected index in order
		 * to show the the selection box. We do this because we have the
		 * multiselect marker on the images to indicate which of them are
		 * selected, so we don't need the selection box, but in the dpad case
		 * we still need the selection box to show as a "cursor".
		 */
		if (!fromKeypad && isInMultiSelectMode())
		{
			mGvs.setSelectedIndex(GridViewPictures.INDEX_NONE);
			// toggleMultiSelected(mGvs.getImageList().getImageAt(index));
		}
		else if (index >= 0 && index < mGvs.getImageList().getCount())
		{
			mSelectedIndex = index;
			mGvs.setSelectedIndex(index);

			IImage image = mGvs.getImageList().getImageAt(index);

			int mSelectedIndexGallery = mGvs.getSelectedIndex();

			if (isInMultiSelectMode())
			{
				toggleMultiSelected(image);
			}
			else
			{
				if (isPickIntent())
				{
					Intent result = new Intent(null, image.getImageUri());
					result.putExtra("selectedindexgallery", mSelectedIndexGallery);
					setResult(RESULT_OK, result);
					finish();
				}
				else
				{
					Intent intent;
					if (image.isVideo())
					{
						intent = new Intent(this, MovieView.class);
						intent.setData(image.getImageUri());
						intent.putExtra(MediaStore.EXTRA_SCREEN_ORIENTATION, mMovieOrientation);
						startActivity(intent);
					}
					else
					{
						intent = new Intent(this, ViewImage.class);
						intent.putExtra(ViewImage.KEY_IMAGE_LIST, mParam);
						intent.putExtra(EXTRA_USE_REMOVE, mUseRemoveText);
						intent.setData(image.getImageUri());
						startActivityForResult(intent, MenuHelper.REQUEST_VIEW_IMAGE);
					}
				}
			}
		}
	}

	void onImageDelete()
	{
		IImage image = mGvs.getCurrentImage();
		if (image != null && Util.deleteImage(this, image.getImageUri()))
		{
			mGvs.setSelectedIndex(GridViewPictures.INDEX_NONE);
			rebake();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode)
		{
			case KeyEvent.KEYCODE_DEL:
			{
				IImage image = getCurrentImage();
				if (image != null)
				{
					MenuHelper.deleteImage(this, mDeletePhotoRunnable, getCurrentImage(), mUseRemoveText);
				}
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	public void onLayoutComplete(boolean changed)
	{
		mLayoutComplete = true;

		mGvs.setSelectedIndex(mSelectedIndex);
		if (mScrollPosition == INVALID_POSITION)
		{
			if (mSortAscending)
			{
				mGvs.scrollTo(0, mGvs.getHeight());
			}
			else
			{
				mGvs.scrollToImage(0);
			}
		}
		else if (mConfigurationChanged)
		{
			mConfigurationChanged = false;
			mGvs.scrollTo(mScrollPosition);
			if (mGvs.getCurrentSelection() != GridViewPictures.INDEX_NONE)
			{
				mGvs.scrollToVisible(mSelectedIndex);
			}
		}
		else
		{
			mGvs.scrollTo(mScrollPosition);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int itemId = item.getItemId();

		if (itemId == android.R.id.home)
		{

			/*
			 * if (itemId == android.R.id.home)
			 * {
			 * return NavigationUtil.navigateUp(this, mHomeClassGalleryRobo);
			 * }
			 */

			Intent intent = new Intent();

			intent.putExtra("com.tipsolutions.callsnapfree.callsnap.notification", true);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			intent.setComponent(new ComponentName("com.tipsolutions.callsnapfree",
					"com.tipsolutions.callsnap.activity.CallSnap"));
			startActivity(intent);

		}
		return false;
	}

	@Override
	public void onPause()
	{
		super.onPause();

		if (mReceiver != null)
		{
			unregisterReceiver(mReceiver);
			mReceiver = null;
		}
		// mApp.log("Image-Gallery PAUSE1: " + MemoryHelper.availableMB() + " MB");

		mGvs.setImageList(null);

		if (mApp != null && mApp.getGalleryFlurry() != null)
		{
			// mApp.log("Image-Gallery PAUSE2: " + MemoryHelper.availableMB() + " MB");
			mApp.getGalleryFlurry().onGalleryExit(this);
		}
		// mApp.log("Image-Gallery PAUSE: " + MemoryHelper.availableMB() + " MB");
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		MenuItem item = menu.findItem(MenuHelper.POSITION_MULTISELECT);
		if (item != null)
		{
			if (isInMultiSelectMode())
			{
				item.setIcon(R.drawable.ic_menu_multiselect_gallery_on);
			}
			else
			{
				item.setIcon(R.drawable.ic_menu_multiselect_gallery);
			}
		}
		if (mParam != null && mParam.getUri() == null)
		{
			item = menu.findItem(MenuHelper.POSITION_REFRESH);
			if (item != null)
			{
				item.setVisible(false);
			}
		}
		return true;
	}

	@Override
	protected void onRestoreInstanceState(Bundle state)
	{
		super.onRestoreInstanceState(state);
		mScrollPosition = state.getFloat(STATE_SCROLL_POSITION, INVALID_POSITION);
		mSelectedIndex = state.getInt(STATE_SELECTED_INDEX, 0);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		// mApp.log("Image-Gallery RESUME: " + MemoryHelper.availableMB() + " MB");

		// mGvs.setSizeChoice(Integer.parseInt(mPrefs.getString("pref_gallery_size_key",
		// getString(R.string.default_value_pref_gallery_size))));
		mGvs.requestFocus();

		// install an intent filter to receive SD card related events.
		IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
		intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
		intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
		intentFilter.addDataScheme("file");

		mReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				String action = intent.getAction();
				if (action.equals(Intent.ACTION_MEDIA_MOUNTED))
				{
					// SD card available
					// TODO put up a "please wait" message
					// TODO also listen for the media scanner finished message
				}
				else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED))
				{
					rebake(true, false);
				}
				else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED))
				{
					rebake(false, true);
				}
				else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED))
				{
					rebake(false, false);
				}
				else if (action.equals(Intent.ACTION_MEDIA_EJECT))
				{
					rebake(true, false);
				}
			}
		};
		registerReceiver(mReceiver, intentFilter);
		rebake();
		setTitle();

		if (mApp != null && mApp.getGalleryFlurry() != null)
		{
			mApp.getGalleryFlurry().onGalleryEnter(this);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle state)
	{
		super.onSaveInstanceState(state);
		state.putFloat(STATE_SCROLL_POSITION, mScrollPosition);
		state.putInt(STATE_SELECTED_INDEX, mSelectedIndex);
	}

	public void onScroll(float scrollPosition)
	{
		mScrollPosition = scrollPosition;
	}

	private void onShareMultipleClicked()
	{
		if (isInMultiSelectMode())
		{
			HashSet<IImage> multiList = mGvs.getMultiselect().getList();

			if (multiList.size() > 1)
			{
				Intent intent = new Intent();
				intent.setAction(Intent.ACTION_SEND_MULTIPLE);

				String mimeType = getShareMultipleMimeType();
				intent.setType(mimeType);
				ArrayList<Parcelable> list = new ArrayList<Parcelable>();
				for (IImage image : multiList)
				{
					list.add(image.getImageUri());
				}
				intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, list);
				try
				{
					startActivity(Intent.createChooser(intent, getText(R.string.send_media_files)));
				}
				catch (android.content.ActivityNotFoundException ex)
				{
					Toast.makeText(this, R.string.no_way_to_share, Toast.LENGTH_SHORT).show();
				}
			}
			else if (multiList.size() == 1)
			{
				IImage image = multiList.iterator().next();
				Intent intent = new Intent();
				intent.setAction(Intent.ACTION_SEND);
				String mimeType = image.getMimeType();
				intent.setType(mimeType);
				intent.putExtra(Intent.EXTRA_STREAM, image.getImageUri());
				boolean isImage = ImageManager.isImage(image);
				try
				{
					startActivity(Intent.createChooser(intent, getText(isImage ? R.string.sendImage
							: R.string.sendVideo)));
				}
				catch (android.content.ActivityNotFoundException ex)
				{
					Toast.makeText(this, isImage ? R.string.no_way_to_share_image : R.string.no_way_to_share_video,
							Toast.LENGTH_SHORT).show();
				}
			}
		}
	}

	void rebake()
	{
		rebake(false, ImageManager.isMediaScannerScanning(getContentResolver()));
	}

	void rebake(boolean unmounted, boolean scanning)
	{
		cancelWaitDialog();

		if (scanning)
		{
			mMediaScanningDialog = ProgressDialog.show(this, null, getResources().getString(R.string.wait), true, true);
		}

		mParam = allImages(!unmounted && !scanning);
		IImageList allImages = ImageManager.makeImageList(this, mParam);

		if (mHorizontal)
		{
			mGvs.setHorizontal(true);
			mGvs.setNumColumns(4);
		}
		else
		{
			mGvs.setHorizontal(false);
			mGvs.setNumColumns(3);
		}
		mGvs.setImageList(allImages);
		mGvs.setDragEnabled(mDragEnable);

		showInstructions(allImages.getCount());
	}

	void refresh()
	{
		if (mParam != null && mParam.getUri() != null)
		{
			Intent intent = new Intent(Intent.ACTION_MEDIA_MOUNTED, mParam.getUri());
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			sendBroadcast(intent);
		}
	}

	void setTitle()
	{
		String title = getIntent().getStringExtra(EXTRA_TITLE);
		if (title != null)
		{
			setTitle(title);
		}
	}

	// According to the intent, setup what we include (image/video) in the
	// gallery and the title of the gallery.
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	void setupInclusion()
	{
		mInclusion = ImageManager.INCLUDE_IMAGES | ImageManager.INCLUDE_VIDEOS;

		Intent intent = getIntent();
		if (intent != null)
		{
			String type = intent.resolveType(this);
			String title = null;
			if (type != null)
			{
				if (isImageType(type))
				{
					mInclusion = ImageManager.INCLUDE_IMAGES;
					if (isPickIntent())
					{
						title = getString(R.string.pick_photos_gallery_title);
					}
					else
					{
						title = getString(R.string.photos_gallery_title);
					}
				}
				if (isVideoType(type))
				{
					mInclusion = ImageManager.INCLUDE_VIDEOS;
					if (isPickIntent())
					{
						title = getString(R.string.pick_videos_gallery_title);
					}
					else
					{
						title = getString(R.string.videos_gallery_title);
					}
				}
			}
			Bundle extras = intent.getExtras();
			String winTitle = (extras != null) ? extras.getString("windowTitle") : null;
			if (winTitle != null && winTitle.length() > 0)
			{
				title = winTitle;
			}
			if (extras != null)
			{
				mInclusion = (ImageManager.INCLUDE_IMAGES | ImageManager.INCLUDE_VIDEOS)
						& extras.getInt("mediaTypes", mInclusion);
			}
			if (extras != null && extras.getBoolean("pick-drm"))
			{
				mInclusion = ImageManager.INCLUDE_DRM_IMAGES;
			}
			if (title != null)
			{
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				{
					getActionBar().setTitle(title);
				}
			}
		}
	}

	void showFooter()
	{
		mFooterOrganizeView.setVisibility(View.VISIBLE);
		if (mFooterAppear == null)
		{
			mFooterAppear = AnimationUtils.loadAnimation(this, R.anim.footer_appear);
		}
		mFooterOrganizeView.startAnimation(mFooterAppear);

		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
				RelativeLayout.LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.ABOVE, R.id.footer_organize);
		params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		mGvs.setLayoutParams(params);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	void showInstructions(int count)
	{
		if (count > mShowEmptyAtCount)
		{
			if (mNoImagesView.getVisibility() != View.GONE)
			{
				mNoImagesView.setVisibility(View.GONE);

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				{
					invalidateOptionsMenu();
				}
			}
			if (mInstructionText != null)
			{
				if (count <= mInstructionCount)
				{
					mInstruction.setVisibility(View.VISIBLE);
					mInstruction.setText(mInstructionText);
					if (mAltFont != null)
					{
						mInstruction.setTypeface(mAltFont);
					}
					if (mAltFontSize > 0)
					{
						mInstruction.setTextSize(TypedValue.COMPLEX_UNIT_SP, mAltFontSize);
					}
					boolean lower = false;
					if (mHorizontal)
					{
						if (count > 1)
						{
							lower = true;
						}
					}
					else
					{
						if (count > 3)
						{
							lower = true;
						}
					}
					RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mInstruction.getLayoutParams();
					if (lower)
					{
						params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
								RelativeLayout.LayoutParams.WRAP_CONTENT);
						params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
						params.bottomMargin = (int) getResources().getDimension(R.dimen.instruction_bottom_margin);
						mInstruction.setLayoutParams(params);
					}
					else
					{
						params.addRule(RelativeLayout.CENTER_VERTICAL);
					}
				}
				else
				{
					mInstruction.setVisibility(View.GONE);
				}
			}
			else
			{
				mInstruction.setVisibility(View.GONE);
			}
		}
		else
		{
			RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

			if (count == 0)
			{
				params.addRule(RelativeLayout.CENTER_IN_PARENT);
			}
			else
			{
				params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
				params.bottomMargin = getResources().getDimensionPixelOffset(R.dimen.instruction_bottom_margin);
			}
			mNoImagesView.setLayoutParams(params);

			mInstruction.setVisibility(View.GONE);

			if (mNoImagesView.getVisibility() != View.VISIBLE)
			{
				mNoImagesView.setVisibility(View.VISIBLE);

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				{
					invalidateOptionsMenu();
				}
			}
			View gvs_container = findViewById(R.id.gvs_container);
			gvs_container.bringToFront();
		}
	}

	void toggleMultiSelected(IImage image)
	{
		int original = mGvs.getMultiselect().toggle(image);
		if (original == 0)
		{
			showFooter();
		}
		if (mGvs.getMultiselect().getList().size() == 0)
		{
			hideFooter();
		}
	}

}
