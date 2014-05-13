package com.piczzamms.gallery.top;

import android.content.Context;

public interface IGalleryFlurry
{
	public void onAddPicture(Context context, int width, int height);

	public void onGalleryEnter(Context context);

	public void onGalleryExit(Context context);
}
