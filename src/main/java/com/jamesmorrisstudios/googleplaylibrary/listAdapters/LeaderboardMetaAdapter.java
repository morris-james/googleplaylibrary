package com.jamesmorrisstudios.googleplaylibrary.listAdapters;

import android.support.annotation.NonNull;
import android.view.View;

import com.jamesmorrisstudios.appbaselibrary.listAdapters.BaseRecycleAdapter;
import com.jamesmorrisstudios.appbaselibrary.listAdapters.BaseRecycleViewHolder;
import com.jamesmorrisstudios.googleplaylibrary.R;

/**
 * Created by James on 6/6/2015.
 */
public class LeaderboardMetaAdapter extends BaseRecycleAdapter {

    public LeaderboardMetaAdapter(int headerMode, OnItemClickListener mListener) {
        super(headerMode, mListener);
    }

    @Override
    protected BaseRecycleViewHolder getViewHolder(@NonNull View view, boolean b, BaseRecycleViewHolder.cardClickListener cardClickListener) {
        return new LeaderboardMetaViewHolder(view, b, cardClickListener);
    }

    @Override
    protected int getHeaderResId() {
        return R.layout.leaderboard_meta_item;
    }

    @Override
    protected int getItemResId() {
        return R.layout.leaderboard_meta_item;
    }

}
