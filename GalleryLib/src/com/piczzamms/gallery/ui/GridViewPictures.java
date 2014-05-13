package com.piczzamms.gallery.ui;

import java.util.HashMap;
import java.util.HashSet;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import com.piczzamms.gallery.R;
import com.piczzamms.gallery.data.ImageManager;
import com.piczzamms.gallery.data.parts.IImage;
import com.piczzamms.gallery.data.parts.IImageList;
import com.piczzamms.gallery.util.MenuHelper;


public class GridViewPictures extends View
{
	class DragData
	{
		int		mDragCurX;
		int		mDragCurY;
		boolean	mDragEnabled;
		int		mDragIndex	= -1;
		Bitmap	mDragMoving;

		void dragDone(MotionEvent ev)
		{
			if (mDragIndex != mCurrentSelection)
			{
				if (mAllImages != null)
				{
					mAllImages.onDrag(mCurrentSelection, mDragIndex);
				}
				mListener.onDrag(mCurrentSelection, mDragIndex);
			}
			mDragIndex = -1;

			if (mDragMoving != null)
			{
				mDragMoving.recycle();
				mDragMoving = null;
			}
		}

		void dragMove(MotionEvent ev)
		{
			int index = computeSelectedIndex(ev.getX(), ev.getY());
			if (index >= 0)
			{
				if (index >= getImageCount())
				{
					mDragIndex = getImageCount();
				}
				else
				{
					mDragIndex = index;
				}
			}
			mDragCurX = (int) ev.getX();
			mDragCurY = (int) ev.getY();
		}

		void dragStart(MotionEvent ev, int index)
		{
			mDragIndex = index;
			mDragCurX = (int) ev.getX();
			mDragCurY = (int) ev.getY();
			invalidate();
		}

		void draw(Canvas canvas)
		{
			if (mDragIndex < 0)
			{
				return;
			}
			final int w = mCellSize;
			final int h = mCellSize;

			/** Draw target selector */
			int row = mDragIndex / mNumCols;
			int col = mDragIndex - (row * mNumCols);

			int colPos = col * mBlockSize;
			int rowTop = row * mBlockSize;
			if (mHorizontal)
			{
				canvas.drawBitmap(mOutline[OUTLINE_DROP_TARGET], rowTop, colPos, null);
			}
			else
			{
				canvas.drawBitmap(mOutline[OUTLINE_DROP_TARGET], colPos, rowTop, null);
			}
			/** Drag Image */
			if (mDragMoving == null)
			{
				IImage image = getCurrentImage();
				Bitmap moving = image.getThumbBitmap(false);
				mDragMoving = Bitmap.createScaledBitmap(moving, w, h, false);
				moving.recycle();
			}
			Paint paint = new Paint();
			paint.setShadowLayer(5.0f, 10.0f, 10.0f, Color.BLACK);
			canvas.drawBitmap(mDragMoving, mDragCurX - w / 2, mDragCurY - w / 2, paint);
		}

		public boolean onTouchEvent(MotionEvent ev)
		{
			switch (ev.getAction())
			{
				case MotionEvent.ACTION_DOWN:
					mCurrentPressState |= TAPPING_FLAG;
					invalidate();
					break;
				case MotionEvent.ACTION_UP:
					mCurrentPressState &= ~TAPPING_FLAG;
					invalidate();

					if (mDragIndex >= 0)
					{
						dragDone(ev);
						return true;
					}
					break;
				case MotionEvent.ACTION_MOVE:
					if (mDragIndex >= 0)
					{
						dragMove(ev);
						invalidate();
						return true;
					}
					break;
			}
			mGestureDetector.onTouchEvent(ev);
			// Consume all events
			return true;
		}
	}

	public static interface Listener
	{
		public void onDrag(int from_index, int to_index);

		public void onImageClicked(int index, boolean fromKeypad);

		public void onLayoutComplete(boolean changed);

		/**
		 * Invoked when the <code>GridViewSpecial</code> scrolls.
		 * 
		 * @param scrollPosition
		 *        the position of the scroller in the range
		 *        [0, 1], when 0 means on the top and 1 means on the buttom
		 */
		public void onScroll(float scrollPosition);
	}

	public class Multiselect
	{
		HashSet<IImage>	mMultiSelected;
		Drawable		mMultiSelectFalse;
		Drawable		mMultiSelectTrue;

		Multiselect()
		{
			mMultiSelectTrue = getResources().getDrawable(R.drawable.btn_check_buttonless_on);
			mMultiSelectFalse = getResources().getDrawable(R.drawable.btn_check_buttonless_off);
		}

		public void clear()
		{
			if (mMultiSelected != null)
			{
				mMultiSelected.clear();
				mMultiSelected = null;
			}
		}

		void draw(Canvas canvas)
		{
			int startRow;
			int endRow;
			int startIndex;
			int endIndex;

			if (mHorizontal)
			{
				// Calculate visible region according to scroll position.
				startRow = (getScrollX() - mCellSpacing) / mBlockSize;
				endRow = (getScrollX() + getWidth() - mCellSpacing - 1) / mBlockSize + 1;
			}
			else
			{
				// Calculate visible region according to scroll position.
				startRow = (getScrollY() - mCellSpacing) / mBlockSize;
				endRow = (getScrollY() + getHeight() - mCellSpacing - 1) / mBlockSize + 1;
			}
			// Limit startRow and endRow to the valid range.
			// Make sure we handle the mRows == 0 case right.
			startRow = Math.max(Math.min(startRow, mNumRows - 1), 0);
			endRow = Math.max(Math.min(endRow, mNumRows), 0);

			startIndex = startRow * mNumCols;
			endIndex = Math.min(endRow * mNumCols, getImageCount());

			int colPos;
			int rowPos;
			int off = 0;

			colPos = mCellSpacing;
			rowPos = startRow * mBlockSize;

			for (int i = startIndex; i < endIndex; i++)
			{
				IImage image = mAllImages.getImageAt(i);

				if (mHorizontal)
				{
					draw(canvas, image, rowPos, colPos, mCellSize, mCellSize);
				}
				else
				{
					draw(canvas, image, colPos, rowPos, mCellSize, mCellSize);
				}
				// Calculate next position
				off += 1;
				if (off == mNumCols)
				{
					colPos = mCellSpacing;
					rowPos += mBlockSize;
					off = 0;
				}
				else
				{
					colPos += mCellSize + mCellSpacing;
				}
			}
		}

		void draw(Canvas canvas, IImage image, int xPos, int yPos, int w, int h)
		{
			Drawable checkBox = mMultiSelected.contains(image) ? mMultiSelectTrue : mMultiSelectFalse;
			int width = checkBox.getIntrinsicWidth();
			int height = checkBox.getIntrinsicHeight();
			int left = 5 + xPos;
			int top = h - height - 5 + yPos;
			mSrcRect.set(left, top, left + width, top + height);
			checkBox.setBounds(mSrcRect);
			checkBox.draw(canvas);
		}

		public HashSet<IImage> getList()
		{
			return mMultiSelected;
		}

		public boolean isEnabled()
		{
			return mMultiSelected != null;
		}

		public void setEnabled()
		{
			if (mMultiSelected == null)
			{
				mMultiSelected = new HashSet<IImage>();
			}
		}

		public int toggle(IImage image)
		{
			int original = mMultiSelected.size();
			if (!mMultiSelected.add(image))
			{
				mMultiSelected.remove(image);
			}
			return original;
		}
	}

	// In MyGestureDetector we have to check canHandleEvent() because
	// GestureDetector could queue events and fire them later. At that time
	// stop() may have already been called and we can't handle the events.
	class MyGestureDetector extends SimpleOnGestureListener
	{
		AudioManager	mAudioManager;

		@Override
		public boolean onDown(MotionEvent e)
		{
			if (mScroller != null && !mScroller.isFinished())
			{
				mScroller.forceFinished(true);
				return false;
			}
			int index = computeSelectedIndex(e.getX(), e.getY());
			if (index >= 0 && index < getImageCount())
			{
				setSelectedIndex(index);
			}
			else
			{
				setSelectedIndex(INDEX_NONE);
			}
			return true;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
		{
			setSelectedIndex(INDEX_NONE);
			mScroller = new Scroller(getContext());

			if (mHorizontal)
			{
				if (velocityX > MAX_FLING_VELOCITY)
				{
					velocityX = MAX_FLING_VELOCITY;
				}
				else if (velocityX < -MAX_FLING_VELOCITY)
				{
					velocityX = -MAX_FLING_VELOCITY;
				}
				mScroller.fling(getScrollX(), 0, -(int) velocityX, 0, 0, mMaxScrollPos, 0, 0);
			}
			else
			{
				if (velocityY > MAX_FLING_VELOCITY)
				{
					velocityY = MAX_FLING_VELOCITY;
				}
				else if (velocityY < -MAX_FLING_VELOCITY)
				{
					velocityY = -MAX_FLING_VELOCITY;
				}
				mScroller.fling(0, getScrollY(), 0, -(int) velocityY, 0, 0, 0, mMaxScrollPos);
			}
			computeScroll();

			return true;
		}

		@Override
		public void onLongPress(MotionEvent e)
		{
			if (canDrag())
			{
				int index = computeSelectedIndex(e.getX(), e.getY());
				if (index >= 0 && index < getImageCount())
				{
					setSelectedIndex(index);
					mDragData.dragStart(e, index);
				}
				else
				{
					setSelectedIndex(INDEX_NONE);
				}
			}
			else
			{
				invalidate();
				GridViewPictures.this.onLongPress();
			}
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
		{
			setSelectedIndex(INDEX_NONE);
			if (mHorizontal)
			{
				scrollBy((int) distanceX, 0);
			}
			else
			{
				scrollBy(0, (int) distanceY);
			}
			invalidate();
			return true;
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e)
		{
			int index = computeSelectedIndex(e.getX(), e.getY());
			if (index >= 0 && index < getImageCount())
			{
				// Play click sound.
				if (mAudioManager == null)
				{
					mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
				}
				mAudioManager.playSoundEffect(AudioManager.FX_KEY_CLICK);
				mListener.onImageClicked(index, false);
				return true;
			}
			return false;
		}
	}

	final static String			TAG					= "GridViewPictures";

	static final int			TAPPING_FLAG		= 1;
	static final int			CLICKING_FLAG		= 2;

	public static final int		INDEX_NONE			= -1;

	static final float			MAX_FLING_VELOCITY	= 2500;

	static final int			OUTLINE_EMPTY		= 0;
	static final int			OUTLINE_PRESSED		= 1;
	static final int			OUTLINE_SELECTED	= 2;
	static final int			OUTLINE_DROP_TARGET	= 3;

	IImageList					mAllImages			= ImageManager.makeEmptyImageList();
	int							mBlockSize;
	HashMap<Integer, Bitmap>	mCache				= new HashMap<Integer, Bitmap>();
	int							mCellSize;
	int							mCellSpacing;
	int							mCellBorderSize;
	int							mCurrentPressState	= 0;
	int							mCurrentSelection	= INDEX_NONE;
	DragData					mDragData			= new DragData();
	Rect						mDstRect			= new Rect();
	GestureDetector				mGestureDetector;
	final Handler				mHandler			= new Handler();
	boolean						mHorizontal;
	Listener					mListener;
	Runnable					mLongPressCallback;
	int							mMaxScrollPos;
	Bitmap						mMissingImageThumbnailBitmap;
	Bitmap						mMissingVideoThumbnailBitmap;
	Multiselect					mMultiselect		= new Multiselect();
	int							mNumCols;
	int							mNumRows;
	Paint						mPaintBorder;
	Bitmap						mOutline[]			= new Bitmap[4];
	Scroller					mScroller;
	Rect						mSrcRect			= new Rect();
	long						mVideoSizeLimit		= Long.MAX_VALUE;
	Drawable					mVideoOverlay;
	Drawable					mVideoMmsErrorOverlay;

	public GridViewPictures(Context context)
	{
		this(context, null, 0);
	}

	public GridViewPictures(Context context, AttributeSet attrs)
	{
		this(context, attrs, 0);
	}

	public GridViewPictures(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);

		mGestureDetector = new GestureDetector(context, new MyGestureDetector());

		setFocusableInTouchMode(true);
		TypedArray a;

		if (attrs != null)
		{
			a = context.obtainStyledAttributes(attrs, R.styleable.GalleryLayout, 0, 0);
			mNumCols = a.getInteger(R.styleable.GalleryLayout_numColumns, 3);
			mCellSpacing = (int) a.getDimension(R.styleable.GalleryLayout_intercellSpacing, 8);
			mHorizontal = (0 == a.getInt(R.styleable.GalleryLayout_orientation, 1));
			mDragData.mDragEnabled = (a.getBoolean(R.styleable.GalleryLayout_dragEnabled, false));
			a.recycle();

			if (mNumCols <= 0)
			{
				Log.e(TAG, "Bad numcols " + mNumCols);
				mNumCols = 1;
			}
		}
		else
		{
			mNumCols = 3;
			mCellSpacing = 5;
			mHorizontal = false;
		}
		mCellBorderSize = mCellSpacing / 2;
		mPaintBorder = new Paint();
		mPaintBorder.setColor(Color.WHITE);
		mPaintBorder.setStyle(Style.FILL);

		init();

		if (attrs != null)
		{
			a = context.obtainStyledAttributes(R.styleable.View);
			initializeScrollbars(a);
			a.recycle();
		}
	}

	boolean canDrag()
	{
		return mAllImages != null && mDragData != null && mAllImages.canDrag() && mDragData.mDragEnabled;
	}

	void clearCache()
	{
		for (Bitmap bitmap : mCache.values())
		{
			bitmap.recycle();
		}
		mCache.clear();
	}

	@Override
	protected int computeHorizontalScrollRange()
	{
		if (mHorizontal)
		{
			return mMaxScrollPos;
		}
		return super.computeHorizontalScrollRange();
	}

	@Override
	public void computeScroll()
	{
		if (mScroller != null)
		{
			boolean more = mScroller.computeScrollOffset();

			if (mHorizontal)
			{
				scrollTo(mScroller.getCurrX(), 0);
			}
			else
			{
				scrollTo(0, mScroller.getCurrY());
			}
			if (more)
			{
				invalidate(); // So we draw again
			}
			else
			{
				mScroller = null;
			}
		}
		else
		{
			super.computeScroll();
		}
	}

	// Inverse of getRectForPosition: from screen coordinate to image position.
	int computeSelectedIndex(float xFloat, float yFloat)
	{
		int x = (int) xFloat;
		int y = (int) yFloat;
		int row, col;
		/*
		 * Note: if horizontal, rows run vertically. That is, starting from upper-left going down,
		 * until the next column which is to the right of this.
		 */
		if (mHorizontal)
		{
			row = (getScrollX() + x) / mBlockSize;
			col = Math.min(mNumCols - 1, y / mBlockSize);
		}
		else
		{
			row = (getScrollY() + y) / mBlockSize;
			col = Math.min(mNumCols - 1, x / mBlockSize);
		}
		return (row * mNumCols) + col;
	}

	@Override
	protected int computeVerticalScrollRange()
	{
		if (mHorizontal)
		{
			return super.computeVerticalScrollRange();
		}
		return mMaxScrollPos;
	}

	void drawCell(Canvas canvas, final int row, final int col, final int x, final int y, final int x2, final int y2)
	{
		final int innerX = x + mCellBorderSize;
		final int innerY = y + mCellBorderSize;
		final int innerX2 = x2 - mCellSpacing;
		final int innerY2 = y2 - mCellSpacing;
		final int innerW = innerX2 - innerX + 1;
		final int innerH = innerY2 - innerY + 1;
		final int imagePos = row * mNumCols + col;

		if (imagePos < 0 || imagePos >= getImageCount())
		{
			return;
		}
		canvas.drawRect(x, y, innerX2 + mCellBorderSize, innerY2 + mCellBorderSize, mPaintBorder);

		IImage image = mAllImages.getImageAt(imagePos);

		Bitmap bitmap = getImageBitmap(image, imagePos);

		if (bitmap != null)
		{
			mDstRect.set(innerX, innerY, innerX2, innerY2);

			final int bw = bitmap.getWidth();
			final int bh = bitmap.getHeight();
			int left;
			int top;
			int right;
			int bottom;

			if (bw > bh)
			{
				int diff = bw - bh;
				left = diff / 2;
				top = 0;
				bottom = bh - 1;
				right = bh - 1;
			}
			else
			{
				int diff = bh - bw;
				left = 0;
				right = bw - 1;
				top = diff / 2;
				bottom = bw - 1;
			}
			mSrcRect.set(left, top, right, bottom);
			canvas.drawBitmap(bitmap, mSrcRect, mDstRect, null);
		}
		else
		{
			/** If the thumbnail cannot be drawn, put up an error icon instead */
			Bitmap error = getErrorBitmap(image);
			int width = error.getWidth();
			int height = error.getHeight();
			mSrcRect.set(0, 0, width, height);
			int left = (innerW - width) / 2 + innerX;
			int top = (innerH - height) / 2 + innerY;
			mDstRect.set(left, top, left + width, top + height);
			canvas.drawBitmap(error, mSrcRect, mDstRect, null);
		}
		if (image.isVideo())
		{

			Drawable overlay = null;
			long size = MenuHelper.getImageFileSize(image);
			if (size >= 0 && size <= mVideoSizeLimit)
			{
				if (mVideoOverlay == null)
				{
					mVideoOverlay = getResources().getDrawable(R.drawable.ic_gallery_video_overlay);
				}
				overlay = mVideoOverlay;
			}
			else
			{
				if (mVideoMmsErrorOverlay == null)
				{
					mVideoMmsErrorOverlay = getResources().getDrawable(R.drawable.ic_error_mms_video_overlay);
				}
				overlay = mVideoMmsErrorOverlay;
				Paint paint = new Paint();
				paint.setARGB(0x80, 0x00, 0x00, 0x00);
				canvas.drawRect(innerX, innerY, innerX + innerW, innerY + innerH, paint);
			}
			int width = overlay.getIntrinsicWidth();
			int height = overlay.getIntrinsicHeight();
			int left = (innerW - width) / 2 + innerX;
			int top = (innerH - height) / 2 + innerY;
			mSrcRect.set(left, top, left + width, top + height);
			overlay.setBounds(mSrcRect);
			overlay.draw(canvas);
		}
	}

	/**
	 * 
	 * @param canvas
	 */
	void drawHorizontal(Canvas canvas)
	{
		int x;
		int xAdjusted;
		int y;
		int x2;
		int x2Adjusted;
		int y2;

		for (int row = 0; row < mNumRows; row++)
		{
			/*
			 * Note: I think the canvas itself is already being scrolled by this getScrollX()
			 * amount. Therefore, we draw in the same place in the canvas irregardless of getScrollX(),
			 * but we don't want to draw if it is offscreen. How confusing!
			 */
			x = row * mBlockSize;
			x2 = x + mBlockSize - 1;
			xAdjusted = x - getScrollX();
			x2Adjusted = x2 - getScrollX();

			if (x2Adjusted >= 0 && xAdjusted < getWidth())
			{
				for (int col = 0; col < mNumCols; col++)
				{
					y = col * mBlockSize;
					y2 = y + mBlockSize - 1;

					if (y2 >= 0 && y < getHeight())
					{
						drawCell(canvas, row, col, x, y, x2, y2);
					}
				}
			}
		}
	}

	void drawSelection(Canvas canvas)
	{
		if (mCurrentSelection == INDEX_NONE)
		{
			return;
		}
		int row = mCurrentSelection / mNumCols;
		int col = mCurrentSelection % mNumCols;

		int colPos = col * mBlockSize;
		int rowTop = row * mBlockSize;

		int type = OUTLINE_SELECTED;
		if (mCurrentPressState != 0)
		{
			type = OUTLINE_PRESSED;
		}
		if (mHorizontal)
		{
			canvas.drawBitmap(mOutline[type], rowTop, colPos, null);
		}
		else
		{
			canvas.drawBitmap(mOutline[type], colPos, rowTop, null);
		}
	}

	void drawVertical(Canvas canvas)
	{
		int x;
		int y;
		int yAdjusted;
		int x2;
		int y2;
		int y2Adjusted;

		for (int row = 0; row < mNumRows; row++)
		{
			y = row * mBlockSize;
			y2 = y + mBlockSize - 1;
			yAdjusted = y - getScrollY();
			y2Adjusted = y2 - getScrollY();

			if (y2Adjusted >= 0 || yAdjusted < getHeight())
			{
				for (int col = 0; col < mNumCols; col++)
				{
					x = col * mBlockSize;
					x2 = x + mBlockSize - 1;

					if (x2 >= 0 || x < getWidth())
					{
						drawCell(canvas, row, col, x, y, x2, y2);
					}
				}
			}
		}
	}

	void ensureVisible(int pos)
	{
		Rect r = getRectForPosition(pos);

		if (mHorizontal)
		{
			int left = getScrollX();
			int right = left + getWidth();

			if (r.right > right)
			{
				mScroller = new Scroller(getContext());
				mScroller.startScroll(getScrollX(), getScrollY(), r.right - getWidth() - getScrollX(), 0, 200);
			}
			else if (r.left < left)
			{
				mScroller = new Scroller(getContext());
				mScroller.startScroll(getScrollX(), getScrollY(), r.left - getScrollX(), 0, 200);
			}
		}
		else
		{
			int top = getScrollY();
			int bot = top + getHeight();

			if (r.bottom > bot)
			{
				mScroller = new Scroller(getContext());
				mScroller.startScroll(getScrollX(), getScrollY(), 0, r.bottom - getHeight() - getScrollY(), 200);
			}
			else if (r.top < top)
			{
				mScroller = new Scroller(getContext());
				mScroller.startScroll(getScrollX(), getScrollY(), 0, r.top - getScrollY(), 200);
			}
		}
		if (mScroller != null)
		{
			computeScroll();
		}
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	void fixScrollPos()
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
		{
			if (mHorizontal)
			{
				// Put mScrollX in the valid range. This matters if mMaxScrollY is
				// changed. For example, orientation changed from portrait to landscape.
				setScrollX(Math.max(0, Math.min(mMaxScrollPos, getScrollX())));
			}
			else
			{
				// Put mScrollY in the valid range. This matters if mMaxScrollY is
				// changed. For example, orientation changed from portrait to landscape.
				setScrollY(Math.max(0, Math.min(mMaxScrollPos, getScrollY())));
			}
		}
	}

	void generateOutlineBitmap()
	{
		final int w = mCellSize;
		final int h = mCellSize;

		for (int i = 0; i < mOutline.length; i++)
		{
			mOutline[i] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		}
		Drawable cellOutline;
		cellOutline = getResources().getDrawable(R.drawable.gallery_thumb);
		cellOutline.setBounds(0, 0, w, h);
		Canvas canvas = new Canvas();

		canvas.setBitmap(mOutline[OUTLINE_EMPTY]);
		cellOutline.setState(EMPTY_STATE_SET);
		cellOutline.draw(canvas);

		canvas.setBitmap(mOutline[OUTLINE_PRESSED]);
		cellOutline.setState(PRESSED_ENABLED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET);
		cellOutline.draw(canvas);

		canvas.setBitmap(mOutline[OUTLINE_SELECTED]);
		cellOutline.setState(ENABLED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET);
		cellOutline.draw(canvas);

		canvas.setBitmap(mOutline[OUTLINE_DROP_TARGET]);
		int width = 4;
		Paint paint = new Paint();
		paint.setStrokeWidth(width);
		paint.setColor(getResources().getColor(R.color.drop_target));
		canvas.drawLine(width / 2, 0, width / 2, h - 1, paint);
	}

	public IImage getCurrentImage()
	{
		int currentSelection = getCurrentSelection();
		if (currentSelection < 0 || currentSelection >= getImageCount())
		{
			return null;
		}
		else
		{
			return mAllImages.getImageAt(currentSelection);
		}
	}

	public int getCurrentSelection()
	{
		return mCurrentSelection;
	}

	public Bitmap getErrorBitmap(IImage image)
	{
		if (image.isVideo())
		{
			if (mMissingVideoThumbnailBitmap == null)
			{
				mMissingVideoThumbnailBitmap = BitmapFactory.decodeResource(getResources(),
						R.drawable.ic_missing_thumbnail_video);
			}
			return mMissingVideoThumbnailBitmap;
		}
		else
		{
			if (mMissingImageThumbnailBitmap == null)
			{
				mMissingImageThumbnailBitmap = BitmapFactory.decodeResource(getResources(),
						R.drawable.ic_missing_thumbnail_picture);
			}
			return mMissingImageThumbnailBitmap;
		}

	}

	Bitmap getImageBitmap(IImage image, int imagePos)
	{
		Bitmap bitmap = mCache.get(imagePos);
		if (bitmap != null)
		{
			return bitmap;
		}
		if (image != null)
		{
			bitmap = image.getMiniThumbBitmap();

			if (bitmap != null)
			{
				mCache.put(imagePos, bitmap);
			}
		}
		return bitmap;
	}

	public int getImageCount()
	{
		if (mAllImages == null)
		{
			return 0;
		}
		return mAllImages.getCount();
	}

	public IImageList getImageList()
	{
		return mAllImages;
	}

	public Multiselect getMultiselect()
	{
		return mMultiselect;
	}

	// Return the rectange for the thumbnail in the given position.
	Rect getRectForPosition(int pos)
	{
		int row = pos / mNumCols;
		int col = pos - (row * mNumCols);
		int top;
		int left;

		if (mHorizontal)
		{
			top = col * mBlockSize;
			left = row * mBlockSize;
		}
		else
		{
			left = col * mBlockSize;
			top = row * mBlockSize;
		}
		return new Rect(left, top, left + mCellSize + mCellSpacing, top + mCellSize + mCellSpacing);
	}

	public int getSelectedIndex()
	{
		return mCurrentSelection;
	}

	public long getVideoSizeLimit()
	{
		return mVideoSizeLimit;
	}

	void init()
	{
		if (mHorizontal)
		{
			setHorizontalScrollBarEnabled(true);
			setVerticalScrollBarEnabled(false);
		}
		else
		{
			setHorizontalScrollBarEnabled(false);
			setVerticalScrollBarEnabled(true);
		}
	}

	void initValues(int altSize, int mainSize)
	{
		mCellSize = (altSize - mCellSpacing * (mNumCols - 1)) / mNumCols;
		mBlockSize = mCellSpacing + mCellSize;
		mNumRows = (getImageCount() + mNumCols - 1) / mNumCols;
		mMaxScrollPos = mNumRows * mBlockSize - mainSize;
	}

	public boolean isDragEnabled()
	{
		return mDragData.mDragEnabled;
	}

	@Override
	public void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);

		canvas.drawColor(Color.TRANSPARENT);

		if (mHorizontal)
		{
			drawHorizontal(canvas);
		}
		else
		{
			drawVertical(canvas);
		}
		if (mMultiselect.isEnabled())
		{
			mMultiselect.draw(canvas);
		}
		drawSelection(canvas);
		mDragData.draw(canvas);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		int sel = mCurrentSelection;
		if (sel != INDEX_NONE)
		{
			switch (keyCode)
			{
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					if (sel != getImageCount() - 1 && (sel % mNumCols < mNumCols - 1))
					{
						sel += 1;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_LEFT:
					if (sel > 0 && (sel % mNumCols != 0))
					{
						sel -= 1;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_UP:
					if (sel >= mNumCols)
					{
						sel -= mNumCols;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_DOWN:
					sel = Math.min(getImageCount() - 1, sel + mNumCols);
					break;
				case KeyEvent.KEYCODE_DPAD_CENTER:
					if (event.getRepeatCount() == 0)
					{
						mCurrentPressState |= CLICKING_FLAG;

						if (mLongPressCallback == null)
						{
							mLongPressCallback = new Runnable()
							{
								@Override
								public void run()
								{
									onLongPress();
								}
							};
						}
						mHandler.postDelayed(mLongPressCallback, ViewConfiguration.getLongPressTimeout());
					}
					break;
				default:
					return super.onKeyDown(keyCode, event);
			}
		}
		else
		{
			switch (keyCode)
			{
				case KeyEvent.KEYCODE_DPAD_RIGHT:
				case KeyEvent.KEYCODE_DPAD_LEFT:
				case KeyEvent.KEYCODE_DPAD_UP:
				case KeyEvent.KEYCODE_DPAD_DOWN:
					int startRow = (getScrollY() - mCellSpacing) / mBlockSize;
					int topPos = startRow * mNumCols;
					Rect r = getRectForPosition(topPos);
					if (r.top < getScrollY())
					{
						topPos += mNumCols;
					}
					topPos = Math.min(getImageCount() - 1, topPos);
					sel = topPos;
					break;
				default:
					return super.onKeyDown(keyCode, event);
			}
		}
		setSelectedIndex(sel);
		return true;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
		{
			mCurrentPressState &= ~CLICKING_FLAG;
			invalidate();

			if (mLongPressCallback != null)
			{
				// The keyUp doesn't get called when the longpress menu comes up. We
				// only get here when the user lets go of the center key before the
				// longpress menu comes up.
				mHandler.removeCallbacks(mLongPressCallback);
			}
			// open the photo
			mListener.onImageClicked(mCurrentSelection, true);
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public void onLayout(boolean changed, int left, int top, int right, int bottom)
	{
		super.onLayout(changed, left, top, right, bottom);

		if (mHorizontal)
		{
			initValues(bottom - top, right - left);
		}
		else
		{
			initValues(right - left, bottom - top);
		}
		fixScrollPos();

		generateOutlineBitmap();

		mListener.onLayoutComplete(changed);
	}

	void onLongPress()
	{
		mCurrentPressState &= ~CLICKING_FLAG;
		showContextMenu();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		int width;
		int height;

		if (widthMode == MeasureSpec.EXACTLY)
		{
			width = widthSize;
		}
		else
		{
			width = 0;
		}
		if (heightMode == MeasureSpec.EXACTLY)
		{
			height = heightSize;
		}
		else
		{
			height = 0;
		}
		final int imageCount = getImageCount();
		final int rowCount = imageCount / mNumCols + imageCount % mNumCols > 0 ? 1 : 0;

		int cellSize;

		if (width == 0 && height == 0)
		{
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
		else if (width == 0)
		{
			if (mHorizontal)
			{
				cellSize = (height - mCellSpacing * mNumCols) / mNumCols;
				width = cellSize * rowCount + mCellSpacing * rowCount;
			}
			else
			{
				cellSize = (height - mCellSpacing * rowCount) / rowCount;
				width = cellSize * mNumCols + mCellSpacing * mNumCols;
			}
			if (widthMode == MeasureSpec.AT_MOST)
			{
				if (widthSize > 0)
				{
					width = Math.min(width, widthSize);
				}
			}
		}
		else if (height == 0)
		{
			if (mHorizontal)
			{
				cellSize = (width - mCellSpacing * rowCount) / rowCount;
				height = cellSize * mNumCols + mCellSpacing * mNumCols;
			}
			else
			{
				cellSize = (width - mCellSpacing * mNumCols) / mNumCols;
				height = cellSize * rowCount + mCellSpacing * rowCount;
			}
			if (heightMode == MeasureSpec.AT_MOST)
			{
				if (heightSize > 0)
				{
					height = Math.min(height, heightSize);
				}
			}
		}
		setMeasuredDimension(width, height);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev)
	{
		return mDragData.onTouchEvent(ev);
	}

	@Override
	public void scrollBy(int x, int y)
	{
		scrollTo(getScrollX() + x, getScrollY() + y);
	}

	public void scrollTo(float scrollPosition)
	{
		if (mHorizontal)
		{
			scrollTo(Math.round(scrollPosition * mMaxScrollPos), 0);
		}
		else
		{
			scrollTo(0, Math.round(scrollPosition * mMaxScrollPos));
		}
	}

	@Override
	public void scrollTo(int x, int y)
	{
		if (mHorizontal)
		{
			x = Math.max(Math.min(mMaxScrollPos, x), 0);

			if (mListener != null)
			{
				mListener.onScroll((float) x / mMaxScrollPos);
			}
		}
		else
		{
			y = Math.max(0, Math.min(mMaxScrollPos, y));

			if (mListener != null)
			{
				mListener.onScroll((float) y / mMaxScrollPos);
			}
		}
		super.scrollTo(x, y);
	}

	public void scrollToImage(int index)
	{
		Rect r = getRectForPosition(index);

		if (mHorizontal)
		{
			scrollTo(r.left, 0);
		}
		else
		{
			scrollTo(0, r.top);
		}
	}

	public void scrollToVisible(int index)
	{
		Rect r = getRectForPosition(index);

		if (mHorizontal)
		{
			int left = getScrollX();
			int right = getScrollX() + getWidth();
			if (r.right > right)
			{
				scrollTo(r.right - getWidth(), 0);
			}
			else if (r.left < left)
			{
				scrollTo(r.left, 0);
			}
		}
		else
		{
			int top = getScrollY();
			int bottom = getScrollY() + getHeight();
			if (r.bottom > bottom)
			{
				scrollTo(0, r.bottom - getHeight());
			}
			else if (r.top < top)
			{
				scrollTo(0, r.top);
			}
		}
	}

	public void setDragEnabled(boolean drag)
	{
		mDragData.mDragEnabled = drag;
	}

	public void setHorizontal(boolean horizontal)
	{
		mHorizontal = horizontal;
		requestLayout();
	}

	public void setImageList(IImageList list)
	{
		if (mAllImages != null)
		{
			mAllImages.close();
		}
		mAllImages = list;
		mCache.clear();
		requestLayout();
	}

	public void setListener(Listener listener)
	{
		mListener = listener;
	}

	public void setNumColumns(int numcols)
	{
		mNumCols = numcols;
		requestLayout();
	}

	public void setSelectedIndex(int index)
	{
		if (mCurrentSelection == index)
		{
			return;
		}
		mCurrentSelection = Math.min(index, getImageCount() - 1);

		if (mCurrentSelection != INDEX_NONE)
		{
			ensureVisible(mCurrentSelection);
		}
		invalidate();
	}

	public void setVideoSizeLimit(long limit)
	{
		mVideoSizeLimit = limit;
	}
}
