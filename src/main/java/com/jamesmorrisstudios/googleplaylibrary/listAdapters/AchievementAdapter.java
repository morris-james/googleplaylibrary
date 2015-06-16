package com.jamesmorrisstudios.googleplaylibrary.listAdapters;

import android.support.annotation.NonNull;
import android.view.View;

import com.google.android.gms.common.images.ImageManager;
import com.jamesmorrisstudios.appbaselibrary.listAdapters.BaseRecycleAdapter;
import com.jamesmorrisstudios.appbaselibrary.listAdapters.BaseRecycleViewHolder;
import com.jamesmorrisstudios.googleplaylibrary.R;
import com.jamesmorrisstudios.utilitieslibrary.app.AppUtil;

/**
 * Created by James on 5/27/2015.
 */
public class AchievementAdapter extends BaseRecycleAdapter {
    private ImageManager imageManager;

    public AchievementAdapter(int headerMode, OnItemClickListener mListener) {
        super(headerMode, mListener);
        imageManager = ImageManager.create(AppUtil.getContext());
    }

    @Override
    protected BaseRecycleViewHolder getViewHolder(@NonNull View view, boolean b, BaseRecycleViewHolder.cardClickListener cardClickListener) {
        return new AchievementViewHolder(view, b, cardClickListener, imageManager);
    }

    @Override
    protected int getHeaderResId() {
        return R.layout.achievement_header;
    }

    @Override
    protected int getItemResId() {
        return R.layout.achievement_item;
    }
}