package com.jamesmorrisstudios.googleplaylibrary.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.AppCompatSpinner;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import com.android.vending.billing.IInAppBillingService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMatch;
import com.google.android.gms.games.multiplayer.turnbased.TurnBasedMatchConfig;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.Snapshots;
import com.jamesmorrisstudios.appbaselibrary.activities.BaseLauncherNoViewActivity;
import com.jamesmorrisstudios.appbaselibrary.fragments.SettingsFragment;
import com.jamesmorrisstudios.googleplaylibrary.R;
import com.jamesmorrisstudios.googleplaylibrary.dialogHelper.AchievementOverlayDialogRequest;
import com.jamesmorrisstudios.googleplaylibrary.dialogHelper.CompareProfilesRequest;
import com.jamesmorrisstudios.googleplaylibrary.dialogHelper.PlayerDetailsDialogRequest;
import com.jamesmorrisstudios.googleplaylibrary.dialogs.AchievementOverlayDialogBuilder;
import com.jamesmorrisstudios.googleplaylibrary.dialogs.PlayerDetailsDialogBuilder;
import com.jamesmorrisstudios.googleplaylibrary.fragments.AchievementFragment;
import com.jamesmorrisstudios.googleplaylibrary.fragments.BaseGooglePlayFragment;
import com.jamesmorrisstudios.googleplaylibrary.fragments.BaseGooglePlayMainFragment;
import com.jamesmorrisstudios.googleplaylibrary.fragments.GooglePlaySettingsFragment;
import com.jamesmorrisstudios.googleplaylibrary.fragments.LeaderboardFragment;
import com.jamesmorrisstudios.googleplaylibrary.fragments.LeaderboardMetaFragment;
import com.jamesmorrisstudios.googleplaylibrary.fragments.OnlineLoadGameFragment;
import com.jamesmorrisstudios.googleplaylibrary.fragments.PlayerPickerFragment;
import com.jamesmorrisstudios.googleplaylibrary.googlePlay.GooglePlay;
import com.jamesmorrisstudios.googleplaylibrary.googlePlay.GooglePlayCalls;
import com.jamesmorrisstudios.googleplaylibrary.house_ads.HouseAd;
import com.jamesmorrisstudios.googleplaylibrary.house_ads.HouseAdInterstitial;
import com.jamesmorrisstudios.googleplaylibrary.listAdapters.AchievementContainer;
import com.jamesmorrisstudios.googleplaylibrary.util.AdUsage;
import com.jamesmorrisstudios.googleplaylibrary.util.IabHelper;
import com.jamesmorrisstudios.googleplaylibrary.util.IabResult;
import com.jamesmorrisstudios.googleplaylibrary.util.Inventory;
import com.jamesmorrisstudios.googleplaylibrary.util.Purchase;
import com.jamesmorrisstudios.utilitieslibrary.Bus;
import com.jamesmorrisstudios.utilitieslibrary.Utils;
import com.jamesmorrisstudios.utilitieslibrary.app.AppUtil;
import com.jamesmorrisstudios.utilitieslibrary.preferences.Prefs;
import com.squareup.otto.Subscribe;
import com.chartboost.sdk.*;

import java.util.ArrayList;

/**
 * Created by James on 5/11/2015.
 */
public abstract class BaseAdLauncherActivity extends BaseLauncherNoViewActivity implements
        GooglePlaySettingsFragment.OnGooglePlaySettingsListener,
        GooglePlay.GameHelperListener,
        BaseGooglePlayFragment.OnGooglePlayListener,
        BaseGooglePlayMainFragment.OnGooglePlayListener,
        LeaderboardMetaFragment.OnLeaderboardMetaListener,
        LeaderboardFragment.OnLeaderboardListener,
        DialogInterface.OnDismissListener{

    private static final String TAG = "BaseAdLauncherActivity";
    private static final String REMOVE_ADS_SKU = "remove_ads_1";

    private final static int RC_LOOK_AT_MATCHES = 10000;
    private final static int RC_LOOK_AT_SNAPSHOTS = 10001;
    private final static int RC_SELECT_PLAYERS = 11000;

    private boolean playServicesEnabled = false;

    private AppCompatSpinner spinnerSpan;
    private AppCompatSpinner spinnerCollection;

    private boolean useAutoLock = false;

    //House ads
    private ArrayList<HouseAd> houseAdList = new ArrayList<>();

    private final Object busListener = new Object() {
        @Subscribe
        public void onSettingEvent(final GooglePlay.GooglePlayEvent event) {
            BaseAdLauncherActivity.this.onGooglePlayEvent(event);
        }
        @Subscribe
        public void onPlayerDetailsDialogRequest(PlayerDetailsDialogRequest request) {
                createPlayerDetailsDialog(request.player);
        }
        @Subscribe
        public void onAchievementOverlayDialogRequest(AchievementOverlayDialogRequest request) {
            createAchievementsOverlayDialog(request.item);
        }
        @Subscribe
        public void onCompareProfilesRequest(CompareProfilesRequest request) {
            loadCompareProfiles(request.player);
        }
    };

    protected IInAppBillingService mService;
    protected IabHelper mHelper;

    ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name,
                                       IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
        }
    };

    private ChartboostDelegate delegate = new ChartboostDelegate() {
        //Override the Chartboost delegate callbacks you wish to track and control

        public void didCompleteRewardedVideo(String location, int reward) {
            Log.v("Chartboost", "Reward: "+reward);
            rewardAdWatched(50);
        }

        public void didCacheRewardedVideo(String location) {
            rewardAdCached();
        }
    };

    /**
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onRestoreState(savedInstanceState);
        setContentView(R.layout.layout_main);
        Chartboost.startWithAppId(this, getChartBoostAppId(), getChartBoostSignature());
        Chartboost.setDelegate(delegate);
        Chartboost.onCreate(this);
        Chartboost.setAutoCacheAds(true);
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode == ConnectionResult.SUCCESS){
            playServicesEnabled = true;
        }
        if(playServicesEnabled) {
            if(AdUsage.isAlreadyRunning()) {
                //App is already open
                if(AdUsage.getAdsEnabled()) {
                    //Ads enabled
                    enableAds();
                } else {
                    //Ads disabled
                    disableAds();
                }
            } else {
                //First launch
                AdUsage.onCreate();
                //Check the cache if ads are enabled or not. If the user used root to change this we will
                //overwrite it after checking the IAP helper
                if(getCacheEnableAds()) {
                    //Ads Enabled
                    enableAds();
                } else {
                    //Ads Disabled
                    disableAds();
                }
            }
            startIABHelper();
            GooglePlay.getInstance().init(this, getGooglePlayClients());
            if(getPlayGamesEnabledPref()) {
                if (GooglePlay.getInstance().isFirstLaunch()) {
                    GooglePlay.getInstance().setup(this);
                    GooglePlay.getInstance().setHasLaunched();
                }
            }
        } else {
            //Ads still work without google play services but the user cant remove them with an IAP
            enableAds();
        }
        initOnCreate();
        initToolbarSpinners();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
    }

    //Not the override
    private void onRestoreState(Bundle bundle) {

    }

    protected abstract String getPublicKey();

    protected abstract int getGooglePlayClients();

    protected abstract boolean useInterstitial();

    protected abstract String getMopubAdId();

    protected abstract String getMopubAdIdFull();

    protected abstract String getChartBoostAppId();

    protected abstract String getChartBoostSignature();

    protected abstract void rewardAdWatched(int reward);

    protected abstract void rewardAdCached();

    private void startIABHelper() {
        //Start the IAP service connection
        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
        // compute your public key and store it in base64EncodedPublicKey
        mHelper = new IabHelper(this, getPublicKey());
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    Log.d(TAG, "Problem setting up In-app Billing: " + result);
                    mHelper = null;
                    return;
                }
                Log.d(TAG, "In app billing is setup and working: " + result);
                mHelper.queryInventoryAsync(false, mGotInventoryListener);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mHelper != null && !mHelper.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
        GooglePlay.getInstance().onActivityResult(requestCode, resultCode, data);
        if(requestCode == RC_LOOK_AT_MATCHES) {
            if(data == null || resultCode != Activity.RESULT_OK) {
                Bus.postEnum(GooglePlay.GooglePlayEvent.SELECT_LOAD_MATCH_ONLINE_FAIL);
            } else {
                loadMatchOnline((TurnBasedMatch)data.getParcelableExtra(Multiplayer.EXTRA_TURN_BASED_MATCH));
            }
        } else if(requestCode == RC_LOOK_AT_SNAPSHOTS) {
            if(data == null || resultCode != Activity.RESULT_OK) {
                Bus.postEnum(GooglePlay.GooglePlayEvent.SELECT_LOAD_MATCH_LOCAL_FAIL);
            } else {
                loadMatchLocal((SnapshotMetadata)data.getParcelableExtra(Snapshots.EXTRA_SNAPSHOT_METADATA));
            }
        } else if(requestCode >= RC_SELECT_PLAYERS && requestCode <= RC_SELECT_PLAYERS + 100) {
            if(data == null || resultCode != Activity.RESULT_OK) {
                Bus.postEnum(GooglePlay.GooglePlayEvent.SELECT_PLAYERS_ONLINE_FAIL);
            } else {
                selectPlayersOnline(data, requestCode - RC_SELECT_PLAYERS);
            }
        }
    }

    private void selectPlayersOnline(@NonNull Intent data, int variant) {
        Log.v("BaseActivity", "Select players online");

        // get the invitee list
        final ArrayList<String> invitees = data.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);

        // get automatch criteria
        Bundle autoMatchCriteria = null;

        int minAutoMatchPlayers = data.getIntExtra(Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
        int maxAutoMatchPlayers = data.getIntExtra(Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);

        if (minAutoMatchPlayers > 0) {
            autoMatchCriteria = RoomConfig.createAutoMatchCriteria(minAutoMatchPlayers, maxAutoMatchPlayers, 0);
        } else {
            autoMatchCriteria = null;
        }

        TurnBasedMatchConfig matchConfig = TurnBasedMatchConfig.builder()
                .setVariant(variant)
                .addInvitedPlayers(invitees)
                .setAutoMatchCriteria(autoMatchCriteria).build();

        GooglePlayCalls.getInstance().startMatchOnline(matchConfig);
    }

    private void loadMatchLocal(@Nullable SnapshotMetadata snapshotMetadata) {
        if(snapshotMetadata != null) {
            Log.v("Activity", "Load Local");
            GooglePlayCalls.getInstance().loadGameLocal(snapshotMetadata.getUniqueName());
        } else {
            Bus.postEnum(GooglePlay.GooglePlayEvent.SELECT_LOAD_MATCH_LOCAL_FAIL);
        }
    }

    private void loadMatchOnline(@Nullable TurnBasedMatch match) {
        if(match != null) {
            if (match.getData() == null) {
                //This is a rematch
                Log.v("Activity", "Load online Rematch");
            } else {
                //We are loading an existing match
                Log.v("Activity", "Load online Existing");
            }
        } else {
            Bus.postEnum(GooglePlay.GooglePlayEvent.SELECT_LOAD_MATCH_ONLINE_FAIL);
        }
    }

    // Get already purchased response
    private IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            if (result.isFailure()) {
                // handle error here
                Log.v("TAG", "Error checking inventory: " + result);
                if(!AdUsage.getAdsEnabled()) {
                    enableAds();
                    restartActivity();
                }
            } else {
                // does the user have the premium upgrade?
                if(inventory.hasPurchase(REMOVE_ADS_SKU)) {
                    Log.v("TAG", "onQueryInventoryFinished GOT A RESPONSE YES Premium");
                    if(AdUsage.getAdsEnabled()) {
                        disableAds();
                        restartActivity();
                    }
                } else {
                    Log.v("TAG", "onQueryInventoryFinished GOT A RESPONSE No Premium");
                    if(!AdUsage.getAdsEnabled()) {
                        enableAds();
                        restartActivity();
                    }
                }
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        Bus.register(busListener);
        if(playServicesEnabled) {
            if(getPlayGamesEnabledPref()) {
                if (!GooglePlay.getInstance().isSignedIn()) {
                    GooglePlay.getInstance().onStart(this);
                }
            }
        }
        Chartboost.onStart(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Chartboost.onResume(this);
        if(useInterstitial()){
            cacheInterstitial();
        }
        cacheRewardAd();
    }

    @Override
    public void onPause() {
        super.onPause();
        Chartboost.onPause(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        Bus.unregister(busListener);
        Chartboost.onStop(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(playServicesEnabled) {
            if (mService != null) {
                unbindService(mServiceConn);
            }
            if (mHelper != null) {
                mHelper.dispose();
            }
            mHelper = null;
        }
        Chartboost.onDestroy(this);
    }

    @Override
    public void onBackPressed() {
        // If an interstitial is on screen, close it.
        if (Chartboost.onBackPressed()) {
            return;
        } else {
            super.onBackPressed();
        }
    }

    private void enableAds() {
        Log.v("TAG", "Showing ads");
        Prefs.putBoolean(getResources().getString(R.string.settings_pref), "ENABLED", true);
        AdUsage.setAdsEnabled(true);

        if(useInterstitial()) {
            //Init house ads
            initHouseAd();
        }

        AdUsage.setMopubAdId(getMopubAdId());
        AdUsage.setMopubAdIdFull(getMopubAdIdFull());
    }

    private void initHouseAd() {
        //Read in all the house ad apps from the xml
        houseAdList.clear();
        TypedArray houseAdArray = getResources().obtainTypedArray(R.array.house_ads);
        for (int i = 0; i < houseAdArray.length(); i++) {
            int id = houseAdArray.getResourceId(i, 0);
            if (id > 0) {
                TypedArray item = getResources().obtainTypedArray(id);
                if (item.length() == 5) {
                    int idLogo = item.getResourceId(0, 0);
                    int idTitle = item.getResourceId(1, 0);
                    int idText = item.getResourceId(2, 0);
                    int idCost = item.getResourceId(3, 0);
                    int idPackage = item.getResourceId(4, 0);
                    if(!getString(R.string.app_package).equals(getString(idPackage))) {
                        houseAdList.add(new HouseAd(idLogo, idTitle, idText, idCost, idPackage));
                    }
                }
                item.recycle();
            }
        }
        houseAdArray.recycle();
    }

    private void disableAds() {
        Log.v("TAG", "Hiding ads");
        Prefs.putBoolean(getResources().getString(R.string.settings_pref), "ENABLED", false);
        AdUsage.setAdsEnabled(false);
    }

    /**
     * Called on settings change event
     */
    @Override
    public void onSettingsChanged() {
        super.onSettingsChanged();
    }

    @Override
    public void purchaseRemoveAds() {
        if(!AdUsage.getAdsEnabled()) {
            Utils.toastShort(getString(R.string.ads_removed));
            return;
        }
        if(mHelper == null) {
            Utils.toastShort(AppUtil.getContext().getString(R.string.unable_setup_iap));
            return;
        }
        if(Utils.getOrientationLock(this) == Utils.Orientation.UNDEFINED) {
            Utils.lockOrientationCurrent(this);
            useAutoLock = true;
        } else {
            useAutoLock = false;
        }
        mHelper.launchPurchaseFlow(this, REMOVE_ADS_SKU, 10001, new IabHelper.OnIabPurchaseFinishedListener() {
            public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
                if (result.isSuccess() && purchase.getSku().equals(REMOVE_ADS_SKU) && purchase.getDeveloperPayload().equals("REMOVE_ADS_PURCHASE_TOKEN")) {
                    Prefs.putString(getResources().getString(R.string.settings_pref), "ORDERID", purchase.getOrderId());
                    Utils.toastShort(getString(R.string.ads_removed));
                    disableAds();
                    restartActivity();
                } else {
                    // Handle error
                    Utils.toastShort(AppUtil.getContext().getString(R.string.failed_purchase));
                }
                if (useAutoLock) {
                    Utils.unlockOrientation(BaseAdLauncherActivity.this);
                }
            }
        }, "REMOVE_ADS_PURCHASE_TOKEN");
    }

    @Override
    public void testingConsumePurchase() {
        //Utils.toastShort("Testing consume purchase");
        //enableAds();
        //consumeItem();
    }

    public void consumeItem() {
        //if(mHelper != null) {
        //    mHelper.queryInventoryAsync(mReceivedInventoryListener);
        //}
    }

    IabHelper.QueryInventoryFinishedListener mReceivedInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            if (result.isFailure()) {
                // Handle failure
            } else {
                mHelper.consumeAsync(inventory.getPurchase(REMOVE_ADS_SKU), mConsumeFinishedListener);
            }
        }
    };

    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            if (result.isSuccess()) {
                restartActivity();
            } else {
                // handle error
            }
        }
    };

    private boolean getCacheEnableAds() {
        return Prefs.getBoolean(getResources().getString(R.string.settings_pref), "ENABLED", true);
    }

    public final void onGooglePlayEvent(GooglePlay.GooglePlayEvent event) {
        switch(event) {
            case SHOW_INTERSTITIAL:
                Log.v(TAG, "Showing interstitial ad");
                showInterstitialAd();
                break;
            case SHOW_REWARD_AD:
                Log.v(TAG, "Showing reward ad");
                showRewardAd();
                break;
        }
    }

    private void initToolbarSpinners() {
        spinnerSpan = (AppCompatSpinner) findViewById(R.id.leaderboard_span);
        spinnerCollection = (AppCompatSpinner) findViewById(R.id.leaderboard_collection);

        final ArrayAdapter spinnerTimesAdapter = ArrayAdapter.createFromResource(AppUtil.getContext(), R.array.leaderboard_span, R.layout.simple_drop_down_item);
        final ArrayAdapter spinnerCollectionAdapter = ArrayAdapter.createFromResource(AppUtil.getContext(), R.array.leaderboard_collection, R.layout.simple_drop_down_item);
        spinnerTimesAdapter.setDropDownViewResource(R.layout.simple_drop_down_item);
        spinnerCollectionAdapter.setDropDownViewResource(R.layout.simple_drop_down_item);

        spinnerSpan.setAdapter(spinnerTimesAdapter);
        spinnerCollection.setAdapter(spinnerCollectionAdapter);

        spinnerSpan.setSelection(GooglePlayCalls.getInstance().getLeaderboardSpan().ordinal(), false);
        spinnerCollection.setSelection(GooglePlayCalls.getInstance().getLeaderboardCollection().ordinal(), false);
    }

    @Override
    public void setLeaderboardSpinnerVisibility(boolean visible) {
        if(visible) {
            spinnerSpan.setVisibility(View.VISIBLE);
            spinnerCollection.setVisibility(View.VISIBLE);
            spinnerSpan.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    switch(spinnerSpan.getSelectedItemPosition()) {
                        case 0:
                            GooglePlayCalls.getInstance().setLeaderboardSpan(GooglePlay.Span.DAILY);
                            break;
                        case 1:
                            GooglePlayCalls.getInstance().setLeaderboardSpan(GooglePlay.Span.WEEKLY);
                            break;
                        case 2:
                            GooglePlayCalls.getInstance().setLeaderboardSpan(GooglePlay.Span.ALL_TIME);
                            break;
                        default:
                            GooglePlayCalls.getInstance().setLeaderboardSpan(GooglePlay.Span.ALL_TIME);
                            break;
                    }
                    Bus.postEnum(GooglePlay.GooglePlayEvent.LEADERBOARD_SPINNER_CHANGE);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
            spinnerCollection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    switch(spinnerCollection.getSelectedItemPosition()) {
                        case 0:
                            GooglePlayCalls.getInstance().setLeaderboardCollection(GooglePlay.Collection.SOCIAL);
                            break;
                        case 1:
                            GooglePlayCalls.getInstance().setLeaderboardCollection(GooglePlay.Collection.PUBLIC);
                            break;
                        default:
                            GooglePlayCalls.getInstance().setLeaderboardCollection(GooglePlay.Collection.PUBLIC);
                            break;
                    }
                    Bus.postEnum(GooglePlay.GooglePlayEvent.LEADERBOARD_SPINNER_CHANGE);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
        } else {
            spinnerSpan.setOnItemSelectedListener(null);
            spinnerCollection.setOnItemSelectedListener(null);
            spinnerSpan.setVisibility(View.GONE);
            spinnerCollection.setVisibility(View.GONE);
        }
    }

    @Override
    public void goToLeaderboard(String leaderboardId) {
        GooglePlayCalls.getInstance().clearLeaderboardsCache();
        loadLeaderboardFragment(leaderboardId);
    }

    private void showHouseInterstitial() {
        if(houseAdList == null || houseAdList.isEmpty()) {
            return;
        }
        int currentHouseAd = incrementCurrentHouseAd();
        FragmentManager fm = getSupportFragmentManager();
        HouseAdInterstitial houseAdInterstitial = new HouseAdInterstitial();
        houseAdInterstitial.setData(houseAdList.get(currentHouseAd));
        houseAdInterstitial.show(fm, "fragment_house_ad");
    }

    private int getCurrentHouseAd() {
        return Prefs.getInt(getString(R.string.settings_pref), "CurrentHouseAd", 0);
    }

    private int incrementCurrentHouseAd() {
        int currentHouseAd = getCurrentHouseAd() + 1;
        if(currentHouseAd >= houseAdList.size()) {
            currentHouseAd = 0;
        }
        Prefs.putInt(getString(R.string.settings_pref), "CurrentHouseAd", currentHouseAd);
        return currentHouseAd;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        Log.v(TAG, "House ad closed.");
    }

    /**
     * Show the interstitial ad if we have one loaded and retry if not
     */
    private void showInterstitialAd() {
        if(!useInterstitial()) {
            return;
        }
        //See if its too shown to show another ad
        if(!AdUsage.allowInterstitial()) {
            Log.v(TAG, "Not enough time since last shown an ad");
            return;
        }
        //Make sure we are using the interstitial ad and that its loaded
        Log.v(TAG, "Requested interstitial");
        if (hasCachedInterstitial()) {
            Chartboost.showInterstitial(CBLocation.LOCATION_GAMEOVER);
            AdUsage.updateAdShowTimeStamp();
        } else {
            Log.v(TAG, "No interstitial loaded. Showing house ad");
            cacheInterstitial();
            showHouseInterstitial();
            AdUsage.updateAdShowTimeStamp();
        }
    }

    private boolean hasCachedInterstitial() {
        return Chartboost.hasInterstitial(CBLocation.LOCATION_GAMEOVER);
    }

    private void cacheInterstitial() {
        Chartboost.cacheInterstitial(CBLocation.LOCATION_GAMEOVER);
    }

    private void cacheRewardAd() {
        Chartboost.cacheRewardedVideo(CBLocation.LOCATION_HOME_SCREEN);
    }

    private boolean hasCachedRewardAd() {
        return Chartboost.hasRewardedVideo(CBLocation.LOCATION_HOME_SCREEN);
    }

    protected final void showRewardAd() {
        Chartboost.showRewardedVideo(CBLocation.LOCATION_HOME_SCREEN);
    }

    private boolean getPlayGamesEnabledPref() {
        return Prefs.getBoolean(getString(R.string.settings_pref), "PlayGamesEnabled", true);
    }

    private void setPlayGamesEnabledPref(boolean playGamesEnabled) {
        Prefs.putBoolean(getString(R.string.settings_pref), "PlayGamesEnabled", playGamesEnabled);
    }

    /**
     * Gets the help fragment from the fragment manager.
     * Creates the fragment if it does not exist yet.
     *
     * @return The fragment
     */
    @Override @NonNull
    protected GooglePlaySettingsFragment getSettingsFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        GooglePlaySettingsFragment fragment = (GooglePlaySettingsFragment) fragmentManager.findFragmentByTag(SettingsFragment.TAG);
        if (fragment == null) {
            fragment = new GooglePlaySettingsFragment();
        }
        return fragment;
    }

    /**
     * Gets the achievements fragment from the fragment manager.
     * Creates the fragment if it does not exist yet.
     *
     * @return The fragment
     */
    @NonNull
    protected final AchievementFragment getAchievementFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        AchievementFragment fragment = (AchievementFragment) fragmentManager.findFragmentByTag(AchievementFragment.TAG);
        if (fragment == null) {
            fragment = new AchievementFragment();
        }
        return fragment;
    }

    /**
     * Loads the achievements fragment into the main view
     */
    protected final boolean loadAchievementFragment(String[] achievementIds) {
        if(isGooglePlayServicesEnabled()) {
            if (GooglePlay.getInstance().isSignedIn()) {
                AchievementFragment fragment = getAchievementFragment();
                fragment.setAchievementIds(achievementIds);
                loadFragment(fragment, AchievementFragment.TAG, true);
                getSupportFragmentManager().executePendingTransactions();
                return true;
            } else {
                if(!GooglePlay.getInstance().getHasSetup()) {
                    GooglePlay.getInstance().setup(this);
                }
                GooglePlay.getInstance().beginUserInitiatedSignIn();
            }
        } else {
            Utils.toastShort(AppUtil.getContext().getString(R.string.requires_google_play));
        }
        return false;
    }

    @NonNull
    protected final LeaderboardMetaFragment getLeaderboardMetaFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        LeaderboardMetaFragment fragment = (LeaderboardMetaFragment) fragmentManager.findFragmentByTag(LeaderboardMetaFragment.TAG);
        if (fragment == null) {
            fragment = new LeaderboardMetaFragment();
        }
        return fragment;
    }

    /**
     * Loads the leaderboard fragment into the main view
     */
    protected final boolean loadLeaderboardMetaFragment(String[] leaderboardIds) {
        if(isGooglePlayServicesEnabled()) {
            if (GooglePlay.getInstance().isSignedIn()) {
                LeaderboardMetaFragment fragment = getLeaderboardMetaFragment();
                fragment.setLeaderboardIds(leaderboardIds);
                loadFragment(fragment, LeaderboardMetaFragment.TAG, true);
                getSupportFragmentManager().executePendingTransactions();
                return true;
            } else {
                if(!GooglePlay.getInstance().getHasSetup()) {
                    GooglePlay.getInstance().setup(this);
                }
                GooglePlay.getInstance().beginUserInitiatedSignIn();
            }
        } else {
            Utils.toastShort(AppUtil.getContext().getString(R.string.requires_google_play));
        }
        return false;
    }

    @NonNull
    protected final LeaderboardFragment getLeaderboardFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        LeaderboardFragment fragment = (LeaderboardFragment) fragmentManager.findFragmentByTag(LeaderboardFragment.TAG);
        if (fragment == null) {
            fragment = new LeaderboardFragment();
        }
        return fragment;
    }

    /**
     * Loads the leaderboard fragment into the main view
     */
    protected final void loadLeaderboardFragment(String leaderboardId) {
        LeaderboardFragment fragment = getLeaderboardFragment();
        fragment.setLeaderboardId(leaderboardId);
        loadFragment(fragment, LeaderboardFragment.TAG, true);
        getSupportFragmentManager().executePendingTransactions();
    }

    @NonNull
    protected final OnlineLoadGameFragment getOnlineLoadGameFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        OnlineLoadGameFragment fragment = (OnlineLoadGameFragment) fragmentManager.findFragmentByTag(OnlineLoadGameFragment.TAG);
        if (fragment == null) {
            fragment = new OnlineLoadGameFragment();
        }
        return fragment;
    }

    /**
     * Loads the
     */
    protected final void loadOnlineLoadGameFragment() {
        if(isGooglePlayServicesEnabled()) {
            if (GooglePlay.getInstance().isSignedIn()) {
                OnlineLoadGameFragment fragment = getOnlineLoadGameFragment();
                loadFragment(fragment, OnlineLoadGameFragment.TAG, true);
                getSupportFragmentManager().executePendingTransactions();

                //startActivityForResult(Games.TurnBasedMultiplayer.getInboxIntent(GooglePlay.getInstance().getApiClient()), RC_LOOK_AT_MATCHES);
            } else {
                if(!GooglePlay.getInstance().getHasSetup()) {
                    GooglePlay.getInstance().setup(this);
                }
                GooglePlay.getInstance().beginUserInitiatedSignIn();
            }
        } else {
            Utils.toastShort(AppUtil.getContext().getString(R.string.requires_google_play));
        }
    }

    @NonNull
    protected final PlayerPickerFragment getPlayerPickerFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        PlayerPickerFragment fragment = (PlayerPickerFragment) fragmentManager.findFragmentByTag(PlayerPickerFragment.TAG);
        if (fragment == null) {
            fragment = new PlayerPickerFragment();
        }
        return fragment;
    }

    //Variant max of 100 (normally 1)
    protected final boolean loadPlayerPickerFragment(int minPlayers, int maxPlayers, boolean allowAutomatch, int variant) {
        if(isGooglePlayServicesEnabled()) {
            if (GooglePlay.getInstance().isSignedIn()) {
               //startActivityForResult(Games.TurnBasedMultiplayer.getSelectOpponentsIntent(GooglePlay.getInstance().getApiClient(), minPlayers, maxPlayers, allowAutomatch), RC_SELECT_PLAYERS + variant);
                PlayerPickerFragment fragment = getPlayerPickerFragment();
                loadFragment(fragment, PlayerPickerFragment.TAG, true);
                getSupportFragmentManager().executePendingTransactions();


                return true;
            } else {
                if(!GooglePlay.getInstance().getHasSetup()) {
                    GooglePlay.getInstance().setup(this);
                }
                GooglePlay.getInstance().beginUserInitiatedSignIn();
            }
        } else {
            Utils.toastShort(AppUtil.getContext().getString(R.string.requires_google_play));
        }
        return false;
    }

    protected final boolean loadOfflineInboxFragment() {
        if(isGooglePlayServicesEnabled()) {
            if (GooglePlay.getInstance().isSignedIn()) {
                startActivityForResult(Games.Snapshots.getSelectSnapshotIntent(GooglePlay.getInstance().getApiClient(), getString(R.string.select_save), false, true, Snapshots.DISPLAY_LIMIT_NONE), RC_LOOK_AT_SNAPSHOTS);
                return true;
            } else {
                if(!GooglePlay.getInstance().getHasSetup()) {
                    GooglePlay.getInstance().setup(this);
                }
                GooglePlay.getInstance().beginUserInitiatedSignIn();
            }
        } else {
            Utils.toastShort(AppUtil.getContext().getString(R.string.requires_google_play));
        }
        return false;
    }

    @Override
    protected void onBackToHome() {
        Log.v("BaseAdLauncherActivity", "Back To Home");
        GooglePlayCalls.getInstance().clearLeaderboardsMetaCache();
        GooglePlayCalls.getInstance().clearAchievementsCache();
        GooglePlayCalls.getInstance().clearLeaderboardsCache();
        GooglePlayCalls.getInstance().clearPlayersCache();
    }

    @Override
    public void onSignInFailed() {
        Log.v("Activity", "Sign in failed: ");// + GooglePlay.getInstance().getSignInError().getActivityResultCode()+" "+GooglePlay.getInstance().getSignInError().getServiceErrorCode());
        Bus.postEnum(GooglePlay.GooglePlayEvent.SIGN_IN_FAIL);
        setPlayGamesEnabledPref(false);
    }

    @Override
    public void onSignInSucceeded() {
        Log.v("Activity", "Sign in Succeeded");
        Bus.postEnum(GooglePlay.GooglePlayEvent.SIGN_IN_SUCCESS);
        //Games.setViewForPopups(GooglePlay.getInstance().getApiClient(), findViewById(R.id.toolbarContainer)); //TODO
        setPlayGamesEnabledPref(true);
    }

    @Override
    public final boolean isGooglePlayServicesEnabled() {
        return playServicesEnabled;
    }

    public final void loadCompareProfiles(@NonNull Player player) {
        Intent intent = Games.Players.getCompareProfileIntent(GooglePlay.getInstance().getApiClient(), player);
        startActivityForResult(intent, 2000);
    }

    public void createPlayerDetailsDialog(@NonNull Player player) {
        PlayerDetailsDialogBuilder builder = PlayerDetailsDialogBuilder.with(this);
        builder.setPlayer(player);
        builder.build().show();
    }

    public void createAchievementsOverlayDialog(@NonNull AchievementContainer item) {
        AchievementOverlayDialogBuilder builder = AchievementOverlayDialogBuilder.with(this);
        builder.setAchievement(item);
        builder.build().show();
    }

}
