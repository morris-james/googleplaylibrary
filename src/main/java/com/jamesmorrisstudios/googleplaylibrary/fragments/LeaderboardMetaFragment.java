package com.jamesmorrisstudios.googleplaylibrary.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.jamesmorrisstudios.appbaselibrary.fragments.BaseRecycleListFragment;
import com.jamesmorrisstudios.appbaselibrary.listAdapters.BaseRecycleAdapter;
import com.jamesmorrisstudios.appbaselibrary.listAdapters.BaseRecycleContainer;
import com.jamesmorrisstudios.googleplaylibrary.R;
import com.jamesmorrisstudios.googleplaylibrary.googlePlay.GooglePlay;
import com.jamesmorrisstudios.googleplaylibrary.googlePlay.GooglePlayCalls;
import com.jamesmorrisstudios.googleplaylibrary.googlePlay.LeaderboardMetaItem;
import com.jamesmorrisstudios.googleplaylibrary.listAdapters.LeaderboardMetaAdapter;
import com.jamesmorrisstudios.googleplaylibrary.listAdapters.LeaderboardMetaContainer;
import com.jamesmorrisstudios.utilitieslibrary.Bus;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;

/**
 * Created by James on 6/6/2015.
 */
public class LeaderboardMetaFragment extends BaseRecycleListFragment {
    public static final String TAG = "LeaderboardMetaFragment";
    private OnLeaderboardMetaListener leaderboardMetaListener;

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Bus.register(this);
    }

    public void onDestroy() {
        super.onDestroy();
        Bus.unregister(this);
    }

    /**
     * @param activity Activity to attach to
     */
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        try {
            leaderboardMetaListener = (OnLeaderboardMetaListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnLeaderboardMetaListener");
        }
    }

    /**
     * Detach from activity
     */
    @Override
    public void onDetach() {
        super.onDetach();
        leaderboardMetaListener = null;
    }

    @Override
    protected BaseRecycleAdapter getAdapter(int i, @NonNull BaseRecycleAdapter.OnItemClickListener onItemClickListener) {
        return new LeaderboardMetaAdapter(i, onItemClickListener);
    }

    @Override
    protected void startDataLoad(boolean forceRefresh) {
        Log.v("LeaderboardMetaFragment", "Start data load");
        GooglePlayCalls.getInstance().loadLeaderboardsMeta(forceRefresh, getResources().getStringArray(R.array.leaderboard_ids));
    }

    private void applyData() {
        ArrayList<BaseRecycleContainer> data = new ArrayList<>();
        if(GooglePlayCalls.getInstance().hasLeaderboardsMeta()) {
            ArrayList<LeaderboardMetaItem> items = GooglePlayCalls.getInstance().getLeaderboardsMeta();
            for(LeaderboardMetaItem item : items) {
                data.add(new LeaderboardMetaContainer(item));
            }
        }
        applyData(data);
    }

    @Override
    protected void itemClick(@NonNull BaseRecycleContainer baseRecycleContainer) {

    }

    @Override
    public void onBack() {
        leaderboardMetaListener.setLeaderboardSpinnerVisibility(false);
        //GooglePlayCalls.getInstance().clearLeaderboardsMetaCache();// TODO remove this
    }

    @Subscribe
    public void onGooglePlayEvent(GooglePlay.GooglePlayEvent event) {
        switch(event) {
            case LEADERBOARD_SPINNER_CHANGE:
                Log.v("LeaderboardMetaFragment", "Spinner change");
                startRefresh(false);
                break;
            case LEADERBOARDS_META_READY:
                applyData();
                break;
            case LEADERBOARDS_META_FAIL:
                applyData();
                break;
        }
    }

    @Override
    public boolean showToolbarTitle() {
        return false;
    }

    @Override
    protected void afterViewCreated() {
        setEnablePullToRefresh(true);
        leaderboardMetaListener.setLeaderboardSpinnerVisibility(true);
    }

    public interface OnLeaderboardMetaListener {

        void setLeaderboardSpinnerVisibility(boolean visible);

    }

}