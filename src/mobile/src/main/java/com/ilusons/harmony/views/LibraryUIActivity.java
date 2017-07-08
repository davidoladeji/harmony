package com.ilusons.harmony.views;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.graphics.drawable.DrawerArrowDrawable;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.NativeExpressAdView;
import com.ilusons.harmony.BuildConfig;
import com.ilusons.harmony.MainActivity;
import com.ilusons.harmony.R;
import com.ilusons.harmony.SettingsActivity;
import com.ilusons.harmony.base.BaseUIActivity;
import com.ilusons.harmony.base.MusicService;
import com.ilusons.harmony.data.Music;
import com.ilusons.harmony.data.Stats;
import com.ilusons.harmony.ref.AndroidEx;
import com.ilusons.harmony.ref.IOEx;
import com.ilusons.harmony.ref.JavaEx;
import com.ilusons.harmony.ref.SPrefEx;
import com.ilusons.harmony.ref.StorageEx;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import co.mobiwise.materialintro.animation.MaterialIntroListener;
import co.mobiwise.materialintro.shape.Focus;
import co.mobiwise.materialintro.shape.FocusGravity;
import co.mobiwise.materialintro.view.MaterialIntroView;
import jonathanfinerty.once.Once;

// TODO: See below
// https://www.reddit.com/r/androidapps/comments/6lxp6q/do_you_know_any_android_music_player_or_playlist/

public class LibraryUIActivity extends BaseUIActivity {

    // Logger TAG
    private static final String TAG = LibraryUIActivity.class.getSimpleName();

    // Request codes
    private static final int REQUEST_FILE_PICK = 4684;
    private static final int REQUEST_EXPORT_LOCATION_PICK_SAF = 59;

    // Data
    RecyclerViewAdapter adapter;

    // UI
    private DrawerLayout drawer_layout;
    private boolean appBarIsExpanded = false;
    private CollapsingToolbarLayout collapse_toolbar;
    private AppBarLayout appBar_layout;
    private View root;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SearchView search_view;

    private AsyncTask<Void, Void, Collection<Music>> refreshTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // Set view
        setContentView(R.layout.library_ui_activity);

        // Set bar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setCollapsible(false);

        getSupportActionBar().setTitle(null);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        collapse_toolbar = (CollapsingToolbarLayout) findViewById(R.id.collapse_toolbar);
        collapse_toolbar.setEnabled(false);
        collapse_toolbar.setTitle(null);

        final DrawerArrowDrawable homeDrawable = new DrawerArrowDrawable(getSupportActionBar().getThemedContext());
        getSupportActionBar().setHomeAsUpIndicator(homeDrawable);

        appBar_layout = (AppBarLayout) findViewById(R.id.appBar_layout);
        appBar_layout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                appBarIsExpanded = (verticalOffset == 0);

                float percentage = ((float) Math.abs(verticalOffset) / appBarLayout.getTotalScrollRange());

                if (1.0f - percentage == 1f) {
                    homeDrawable.setVerticalMirror(true);
                } else if (1.0f - percentage == 0f) {
                    homeDrawable.setVerticalMirror(false);
                }
                homeDrawable.setProgress(1.0f - percentage);

                if ((collapse_toolbar.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(collapse_toolbar))) {

                } else {

                }
            }
        });
        appBar_layout.setExpanded(appBarIsExpanded, true);
        appBar_layout.animate();

        drawer_layout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer_layout.closeDrawer(GravityCompat.START);
        drawer_layout.closeDrawer(GravityCompat.END);

        // Set views
        root = findViewById(R.id.root);

        // Set recycler
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(11);
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);

        adapter = new RecyclerViewAdapter();
        recyclerView.setAdapter(adapter);

        // Set swipe to refresh
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        final SwipeRefreshLayout.OnRefreshListener swipeRefreshLayoutOnRefreshListener = new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (refreshTask != null && !refreshTask.isCancelled()) {
                    refreshTask.cancel(true);

                    try {
                        refreshTask.get();
                    } catch (Exception e) {
                        Log.w(TAG, e);
                    }
                }

                refreshTask = (new AsyncTask<Void, Void, Collection<Music>>() {
                    @Override
                    protected Collection<Music> doInBackground(Void... voids) {
                        return Music.loadCurrent(LibraryUIActivity.this);
                    }

                    @Override
                    protected void onPreExecute() {
                        swipeRefreshLayout.setRefreshing(true);
                    }

                    @Override
                    protected void onPostExecute(Collection<Music> data) {
                        adapter.setData(data);

                        swipeRefreshLayout.setRefreshing(false);

                        refreshTask = null;
                    }
                });

                refreshTask.execute();
            }
        };
        swipeRefreshLayout.setOnRefreshListener(swipeRefreshLayoutOnRefreshListener);

        // Load data
        swipeRefreshLayoutOnRefreshListener.onRefresh();

        // Set search

        findViewById(R.id.open).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent();
                i.setType("audio/*");
                i.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(i, REQUEST_FILE_PICK);

                drawer_layout.closeDrawer(GravityCompat.START);
            }
        });
        findViewById(R.id.open).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                infoDialog("Select and play any support media from local storage.");
                return true;
            }
        });

        findViewById(R.id.refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent musicServiceIntent = new Intent(LibraryUIActivity.this, MusicService.class);
                musicServiceIntent.setAction(MusicService.ACTION_LIBRARY_UPDATE);
                musicServiceIntent.putExtra(MusicService.KEY_LIBRARY_UPDATE_FORCE, true);
                startService(musicServiceIntent);

                drawer_layout.closeDrawer(GravityCompat.START);
            }
        });
        findViewById(R.id.refresh).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                infoDialog("Re-loads all library media and also scan for new and changed.");
                return true;
            }
        });

        findViewById(R.id.now_playing).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LibraryUIActivity.this, PlaybackUIDarkActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);

                drawer_layout.closeDrawer(GravityCompat.START);
            }
        });

        findViewById(R.id.sleep_timer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (MusicService.IsPremium) {
                    Intent intent = new Intent(LibraryUIActivity.this, SleepTimerActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                } else {
                    MusicService.showPremiumFeatureMessage(getApplicationContext());
                }

                drawer_layout.closeDrawer(GravityCompat.START);
            }
        });

        findViewById(R.id.settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LibraryUIActivity.this, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);

                drawer_layout.closeDrawer(GravityCompat.START);
            }
        });

        findViewById(R.id.exit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getMusicService() != null)
                    getMusicService().stop();

                System.exit(0);
            }
        });

        search_view = (SearchView) findViewById(R.id.search_view);

        search_view.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(query);

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);

                return true;
            }
        });

        // Set collapse items
        createUIFilters();
        createUISortMode();

        // Set right drawer
        drawer_layout.closeDrawer(GravityCompat.END);

        findViewById(R.id.load_library).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setFromLibrary();

                drawer_layout.closeDrawer(GravityCompat.END);
            }
        });
        findViewById(R.id.load_library).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                infoDialog("Loads all the library media into view playlist.");
                return true;
            }
        });

        findViewById(R.id.save_current).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCurrent();

                drawer_layout.closeDrawer(GravityCompat.END);
            }
        });
        findViewById(R.id.save_current).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                infoDialog("Saves the current view playlist as current playlist.");
                return true;
            }
        });

        findViewById(R.id.export_playlist).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, "Playlist.m3u");
                intent.setType("*/*");
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(intent, REQUEST_EXPORT_LOCATION_PICK_SAF);
                } else {
                    info("SAF not found!");
                }

                drawer_layout.closeDrawer(GravityCompat.END);
            }
        });

        // Set playlist(s)
        RecyclerView playlist_recyclerView = (RecyclerView) findViewById(R.id.playlist_recyclerView);
        playlist_recyclerView.setHasFixedSize(true);
        playlist_recyclerView.setItemViewCacheSize(5);
        playlist_recyclerView.setDrawingCacheEnabled(true);
        playlist_recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);

        PlaylistRecyclerViewAdapter playlistRecyclerViewAdapter = new PlaylistRecyclerViewAdapter();
        playlist_recyclerView.setAdapter(playlistRecyclerViewAdapter);

        final ArrayList<Pair<Long, String>> playlists = new ArrayList<>();
        Music.allPlaylist(getContentResolver(), new JavaEx.ActionTU<Long, String>() {
            @Override
            public void execute(Long id, String name) {
                playlists.add(new Pair<Long, String>(id, name));
            }
        });
        playlistRecyclerViewAdapter.setData(playlists);

        // Guide
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                showGuide();
            }
        });

    }

    @Override
    public void onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START);
        } else {
            // super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                if (appBarIsExpanded) {
                    CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) appBar_layout.getLayoutParams();
                    lp.height = getResources().getDisplayMetrics().heightPixels / 3;
                }
                appBar_layout.setExpanded(!appBarIsExpanded, true);
                if (!appBarIsExpanded)
                    onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult\nrequestCode=" + requestCode + "\nresultCode=" + resultCode + "\ndata=" + data);

        if (requestCode == REQUEST_FILE_PICK && resultCode == RESULT_OK) {
            Uri uri = Uri.parse(StorageEx.getPath(this, data.getData()));

            Intent i = new Intent(this, MusicService.class);

            i.setAction(MusicService.ACTION_OPEN);
            i.putExtra(MusicService.KEY_URI, uri.toString());

            startService(i);
        } else if (requestCode == REQUEST_EXPORT_LOCATION_PICK_SAF && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (data != null) {
                uri = data.getData();

                try {
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
                    FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());

                    exportCurrent(fileOutputStream, uri);

                    fileOutputStream.close();
                    pfd.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void OnMusicServicePlay() {
        super.OnMusicServicePlay();
    }

    @Override
    public void OnMusicServicePause() {
        super.OnMusicServicePause();
    }

    @Override
    public void OnMusicServiceStop() {
        super.OnMusicServiceStop();
    }

    @Override
    public void OnMusicServiceOpen(String uri) {
        if (SettingsActivity.getUIPlaybackAutoOpen(this))
            MainActivity.openPlaybackUIActivity(this);
    }

    @Override
    public void OnMusicServiceLibraryUpdateBegins() {
        swipeRefreshLayout.setRefreshing(true);

        info("Library update is in progress!", true);
    }

    @Override
    public void OnMusicServiceLibraryUpdated() {
        if (adapter != null)
            adapter.setData(Music.loadCurrent(LibraryUIActivity.this));

        swipeRefreshLayout.setRefreshing(false);

        info("Library updated!");
    }

    private void showGuide() {
        final String tag_release_notes = TAG + ".release_notes";

        if (!Once.beenDone(Once.THIS_APP_VERSION, tag_release_notes)) {
            SettingsActivity.showReleaseNotesDialog(this);

            Once.markDone(tag_release_notes);
        }

        final String tag_guide = TAG + ".guide";

        if (Once.beenDone(Once.THIS_APP_INSTALL, tag_guide))
            return;

        final MaterialIntroView.Builder guide_start = new MaterialIntroView.Builder(this)
                .setMaskColor(ContextCompat.getColor(this, R.color.translucent_accent))
                .setDelayMillis(500)
                .enableFadeAnimation(true)
                .enableDotAnimation(false)
                .setFocusType(Focus.MINIMUM)
                .setFocusGravity(FocusGravity.CENTER)
                .setTargetPadding(32)
                .dismissOnTouch(true)
                .enableIcon(true)
                .performClick(true)
                .setInfoText("Welcome! \n\nNow, tap anywhere on blue screen!")
                .setTarget(collapse_toolbar)
                .setUsageId(UUID.randomUUID().toString());

        final MaterialIntroView.Builder guide_ldrawer = new MaterialIntroView.Builder(this)
                .setMaskColor(ContextCompat.getColor(this, R.color.translucent_accent))
                .setDelayMillis(500)
                .enableFadeAnimation(true)
                .enableDotAnimation(false)
                .setFocusType(Focus.MINIMUM)
                .setFocusGravity(FocusGravity.CENTER)
                .setTargetPadding(32)
                .dismissOnTouch(true)
                .enableIcon(true)
                .performClick(true)
                .setInfoText("Left drawer.")
                .setTarget(findViewById(R.id.nav_layout))
                .setUsageId(UUID.randomUUID().toString());

        final MaterialIntroView.Builder guide_rdrawer = new MaterialIntroView.Builder(this)
                .setMaskColor(ContextCompat.getColor(this, R.color.translucent_accent))
                .setDelayMillis(500)
                .enableFadeAnimation(true)
                .enableDotAnimation(false)
                .setFocusType(Focus.MINIMUM)
                .setFocusGravity(FocusGravity.CENTER)
                .setTargetPadding(32)
                .dismissOnTouch(true)
                .enableIcon(true)
                .performClick(true)
                .setInfoText("Right drawer. Here library and playlist can be managed.")
                .setTarget(findViewById(R.id.nav_layout_right))
                .setUsageId(UUID.randomUUID().toString());

        final MaterialIntroView.Builder guide_recycler = new MaterialIntroView.Builder(this)
                .setMaskColor(ContextCompat.getColor(this, R.color.translucent_accent))
                .setDelayMillis(500)
                .enableFadeAnimation(true)
                .enableDotAnimation(false)
                .setFocusType(Focus.MINIMUM)
                .setFocusGravity(FocusGravity.CENTER)
                .setTargetPadding(32)
                .dismissOnTouch(true)
                .enableIcon(true)
                .performClick(true)
                .setInfoText("Playlist currently active.")
                .setTarget(recyclerView)
                .setUsageId(UUID.randomUUID().toString());

        final MaterialIntroView.Builder guide_search = new MaterialIntroView.Builder(this)
                .setMaskColor(ContextCompat.getColor(this, R.color.translucent_accent))
                .setDelayMillis(500)
                .enableFadeAnimation(true)
                .enableDotAnimation(false)
                .setFocusType(Focus.MINIMUM)
                .setFocusGravity(FocusGravity.CENTER)
                .setTargetPadding(32)
                .dismissOnTouch(true)
                .enableIcon(true)
                .performClick(true)
                .setInfoText("Here is little search bar, you can use it to find a library item. Also, swipe down for some more options!")
                .setTarget(search_view)
                .setUsageId(UUID.randomUUID().toString());

        final MaterialIntroView.Builder guide_final = new MaterialIntroView.Builder(this)
                .setMaskColor(ContextCompat.getColor(this, R.color.translucent_accent))
                .setDelayMillis(500)
                .enableFadeAnimation(true)
                .enableDotAnimation(false)
                .setFocusType(Focus.MINIMUM)
                .setFocusGravity(FocusGravity.CENTER)
                .setTargetPadding(32)
                .dismissOnTouch(true)
                .enableIcon(true)
                .performClick(true)
                .setInfoText("That's all! Now go play something!")
                .setTarget(collapse_toolbar)
                .setUsageId(UUID.randomUUID().toString());

        guide_final.setListener(new MaterialIntroListener() {
            @Override
            public void onUserClicked(String usageId) {
                try {
                    Once.markDone(tag_guide);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        guide_search.setListener(new MaterialIntroListener() {
            @Override
            public void onUserClicked(String usageId) {
                try {
                    guide_final.show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        guide_recycler.setListener(new MaterialIntroListener() {
            @Override
            public void onUserClicked(String usageId) {
                try {
                    guide_search.show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        guide_rdrawer.setListener(new MaterialIntroListener() {
            @Override
            public void onUserClicked(String usageId) {
                drawer_layout.closeDrawer(Gravity.END);

                try {
                    guide_recycler.show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        guide_ldrawer.setListener(new MaterialIntroListener() {
            @Override
            public void onUserClicked(String usageId) {
                drawer_layout.closeDrawer(Gravity.START);
                drawer_layout.openDrawer(Gravity.END);

                try {
                    guide_rdrawer.show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        guide_start.setListener(new MaterialIntroListener() {
            @Override
            public void onUserClicked(String usageId) {
                drawer_layout.openDrawer(Gravity.START);

                try {
                    guide_ldrawer.show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        try {
            guide_start.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void setCurrent() {
        ArrayList<Music> data = new ArrayList<Music>();

        for (Object o : adapter.dataFiltered) {
            if (o instanceof Music)
                data.add((Music) o);
        }

        Music.saveCurrent(LibraryUIActivity.this, data, true);

        info("Current playlist saved!");
    }

    private void exportCurrent(OutputStream os, Uri uri) {
        ArrayList<Music> data = new ArrayList<Music>();

        for (Object o : adapter.dataFiltered) {
            if (o instanceof Music)
                data.add((Music) o);
        }

        boolean result = true;

        //noinspection ConstantConditions
        String base = (new File(StorageEx.getPath(this, uri))).getParent();
        String nl = System.getProperty("line.separator");
        StringBuilder sb = new StringBuilder();
        for (Music music : data) {
            String url = IOEx.getRelativePath(base, music.Path);
            sb.append(url).append(nl);
        }

        try {
            IOUtils.write(sb.toString(), os, "utf-8");
        } catch (Exception e) {
            result = false;

            e.printStackTrace();
        }

        if (result)
            info("Current playlist exported!");
        else
            info("Export failed!", true);
    }

    private AsyncTask<Void, Void, Void> setFromTask = null;

    private void setFromLibrary() {
        if (setFromTask != null) {
            try {
                setFromTask.get(1, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Log.w(TAG, e);
            }
            setFromTask = null;
        }

        swipeRefreshLayout.setRefreshing(true);

        setFromTask = (new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onCancelled() {
                super.onCancelled();

                setFromTask = null;

                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                setFromTask = null;

                swipeRefreshLayout.setRefreshing(false);

                info("Loaded library!");
            }

            @Override
            protected Void doInBackground(Void... voids) {
                info("Do not refresh until library is fully loaded!", true);

                final ArrayList<Music> data = Music.loadAll(LibraryUIActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.setData(data);
                    }
                });

                return null;
            }
        });

        setFromTask.execute();
    }

    private void setFromPlaylist(String playlistId) {
        if (setFromTask != null) {
            try {
                setFromTask.get(1, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Log.w(TAG, e);
            }
            setFromTask = null;
        }

        final Collection<String> audioIds = Music.getAllAudioIdsInPlaylist(getContentResolver(), Long.parseLong(playlistId));

        if (audioIds.size() > 0) {
            swipeRefreshLayout.setRefreshing(true);

            setFromTask = (new AsyncTask<Void, Void, Void>() {
                @Override
                protected void onCancelled() {
                    super.onCancelled();

                    setFromTask = null;

                    swipeRefreshLayout.setRefreshing(false);
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);

                    setFromTask = null;

                    swipeRefreshLayout.setRefreshing(false);

                    info("Loaded playlist!");
                }

                @Override
                protected Void doInBackground(Void... voids) {
                    info("Do not refresh until this playlist is fully loaded!", true);

                    final ArrayList<Music> data = new ArrayList<>();
                    final JavaEx.ActionT<Music> action = new JavaEx.ActionT<Music>() {
                        @Override
                        public void execute(final Music music) {
                            data.add(music);
                        }
                    };
                    Music.getAllMusicForIds(LibraryUIActivity.this, audioIds, action);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.setData(data);
                        }
                    });

                    return null;
                }
            });

            setFromTask.execute();
        }
    }

    public class RecyclerViewAdapter extends RecyclerView.Adapter<ViewHolder> {

        private static final int ITEMS_PER_AD = 8;
        private AdListener lastAdListener = null;

        private final ArrayList<Music> data;
        private final ArrayList<Object> dataFiltered;

        private final SettingsActivity.UIStyle uiStyle;

        public RecyclerViewAdapter() {
            data = new ArrayList<>();
            dataFiltered = new ArrayList<>();

            uiStyle = SettingsActivity.getUIStyle(getApplicationContext());
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            int layoutId = -1;
            switch (uiStyle) {
                case LiteUI:
                    layoutId = R.layout.library_ui_lite_item;
                    break;

                case DarkUI:
                default:
                    layoutId = R.layout.library_ui_dark_item;
                    break;
            }

            View view = inflater.inflate(layoutId, parent, false);

            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final Object d = dataFiltered.get(position);
            final View v = holder.view;

            // Bind data to view here!

            // TODO: Fix ads later
            if (false && ((BuildConfig.DEBUG || !MusicService.IsPremium) && (d instanceof NativeExpressAdView && lastAdListener == null))) {

                CardView cv = (CardView) v.findViewById(R.id.cardView);

                final NativeExpressAdView adView = (NativeExpressAdView) d;

                try {
                    if (adView.getParent() == null) {

                        int w = (int) ((cv.getWidth() - cv.getPaddingLeft() - cv.getPaddingRight()) / getResources().getDisplayMetrics().density);
                        int h = (int) ((cv.getHeight() - cv.getPaddingTop() - cv.getPaddingBottom()) / getResources().getDisplayMetrics().density);

                        if (w > 280 && h > 80) {
                            adView.setAdUnitId(BuildConfig.AD_UNIT_ID_NE1);
                            adView.setAdSize(new AdSize(w, h));

                            cv.addView(adView, new CardView.LayoutParams(CardView.LayoutParams.WRAP_CONTENT, CardView.LayoutParams.WRAP_CONTENT));

                            lastAdListener = new AdListener() {
                                @Override
                                public void onAdLoaded() {
                                    super.onAdLoaded();

                                    lastAdListener = null;
                                }

                                @Override
                                public void onAdFailedToLoad(int errorCode) {

                                    lastAdListener = null;
                                }
                            };
                            adView.setAdListener(lastAdListener);

                            AdRequest adRequest;
                            if (BuildConfig.DEBUG) {
                                adRequest = new AdRequest.Builder()
                                        .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                                        .addTestDevice(AndroidEx.getDeviceIdHashed(LibraryUIActivity.this))
                                        .build();
                            } else {
                                adRequest = new AdRequest.Builder()
                                        .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                                        .build();
                            }

                            adView.loadAd(adRequest);

                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, e);
                }

            } else if (d instanceof Music) {

                final Music item = (Music) dataFiltered.get(position);

                final View root = v.findViewById(R.id.root);

                final ImageView cover = (ImageView) v.findViewById(R.id.cover);
                cover.setImageBitmap(null);
                // HACK: This animates aw well as reduces load on image view
                final int coverSize = Math.max(cover.getWidth(), cover.getHeight());
                (new AsyncTask<Void, Void, Bitmap>() {
                    @Override
                    protected Bitmap doInBackground(Void... voids) {
                        return item.getCover(LibraryUIActivity.this, coverSize);
                    }

                    @Override
                    protected void onPostExecute(Bitmap bitmap) {
                        TransitionDrawable d = new TransitionDrawable(new Drawable[]{
                                cover.getDrawable(),
                                new BitmapDrawable(getResources(), bitmap)
                        });

                        cover.setImageDrawable(d);

                        d.setCrossFadeEnabled(true);
                        d.startTransition(200);
                    }
                }).execute();

                TextView title = (TextView) v.findViewById(R.id.title);
                title.setText(item.Title);

                TextView artist = (TextView) v.findViewById(R.id.artist);
                artist.setText(item.Artist);

                TextView album = (TextView) v.findViewById(R.id.album);
                album.setText(item.Album);

                v.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent i = new Intent(LibraryUIActivity.this, MusicService.class);

                        i.setAction(MusicService.ACTION_OPEN);
                        i.putExtra(MusicService.KEY_URI, item.Path);

                        startService(i);

                        AnimatorSet as = new AnimatorSet();
                        as.playSequentially(
                                ObjectAnimator.ofArgb(root, "backgroundColor", ContextCompat.getColor(getApplicationContext(), R.color.transparent), ContextCompat.getColor(getApplicationContext(), R.color.accent), ContextCompat.getColor(getApplicationContext(), R.color.transparent))
                        );
                        as.setDuration(450);
                        as.setInterpolator(new LinearInterpolator());
                        as.setTarget(root);
                        as.start();
                    }
                });

                v.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {

                        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(LibraryUIActivity.this, R.style.AppTheme_AlertDialogStyle));
                        builder.setTitle("Select the action");
                        builder.setItems(new CharSequence[]{
                                "Remove"
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int itemIndex) {
                                switch (itemIndex) {
                                    case 0:
                                        removeData(item);
                                        break;
                                }
                            }
                        });
                        AlertDialog dialog = builder.create();
                        dialog.show();

                        return true;
                    }
                });

            }

        }

        @Override
        public int getItemCount() {
            return dataFiltered.size();
        }

        public void setData(Collection<Music> d) {
            data.clear();
            data.addAll(d);

            filter(null);
        }

        public void addData(Music d) {
            data.clear();
            data.add(0, d);
            dataFiltered.add(0, d);

            notifyDataSetChanged();
        }

        public void removeData(Music d) {
            data.clear();
            data.remove(d);
            dataFiltered.remove(d);

            notifyDataSetChanged();
        }

        public void clearData() {
            data.clear();

            filter(null);
        }

        public void filter(final String text) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {

                        // Filter
                        ArrayList<Music> items = new ArrayList<>();
                        synchronized (data) {
                            for (Music item : data) {
                                boolean f = true;

                                SharedPreferences spref = SPrefEx.get(LibraryUIActivity.this);

                                String ext = item.Path.substring(item.Path.lastIndexOf(".")).toLowerCase();
                                for (UIFilters uiFilter : getUIFilters(LibraryUIActivity.this)) {
                                    boolean uif = uiFilter == UIFilters.NoMP3 && ext.equals(".mp3")
                                            || uiFilter == UIFilters.NoM4A && ext.equals(".m4a")
                                            || uiFilter == UIFilters.NoFLAC && ext.equals(".flac")
                                            || uiFilter == UIFilters.NoOGG && ext.equals(".ogg")
                                            || uiFilter == UIFilters.NoWAV && ext.equals(".wav")
                                            || uiFilter == UIFilters.NoMP4 && ext.equals(".mp4")
                                            || uiFilter == UIFilters.NoMKV && ext.equals(".mkv")
                                            || uiFilter == UIFilters.NoAVI && ext.equals(".avi")
                                            || uiFilter == UIFilters.NoWEBM && ext.equals(".webm");

                                    if (uif) {
                                        f = false;
                                        break;
                                    }
                                }

                                f &= TextUtils.isEmpty(text) || text.length() < 1 || item.getTextDetailed().toLowerCase().contains(text);

                                if (f)
                                    items.add(item);
                            }
                        }

                        // Sort
                        // TODO: This could be heavy on ui, analyze
                        final UISortMode uiSortMode = getUISortMode(LibraryUIActivity.this);
                        switch (uiSortMode) {
                            case Title:
                                Collections.sort(items, new Comparator<Music>() {
                                    @Override
                                    public int compare(Music x, Music y) {
                                        return x.Title.compareToIgnoreCase(y.Title);
                                    }
                                });
                                break;
                            case Album:
                                Collections.sort(items, new Comparator<Music>() {
                                    @Override
                                    public int compare(Music x, Music y) {
                                        return x.Album.compareToIgnoreCase(y.Album);
                                    }
                                });
                                break;
                            case Artist:
                                Collections.sort(items, new Comparator<Music>() {
                                    @Override
                                    public int compare(Music x, Music y) {
                                        return x.Artist.compareToIgnoreCase(y.Artist);
                                    }
                                });
                                break;
                            case Played:
                                Collections.sort(items, new Comparator<Music>() {
                                    @Override
                                    public int compare(Music x, Music y) {
                                        Stats xs = Stats.get(LibraryUIActivity.this, x.Path);
                                        Stats ys = Stats.get(LibraryUIActivity.this, y.Path);

                                        return xs.Played.compareTo(ys.Played);
                                    }
                                });
                                break;
                            case Added:
                                Collections.sort(items, new Comparator<Music>() {
                                    @Override
                                    public int compare(Music x, Music y) {
                                        Stats xs = Stats.get(LibraryUIActivity.this, x.Path);
                                        Stats ys = Stats.get(LibraryUIActivity.this, y.Path);

                                        return xs.TimeAdded.compareTo(ys.TimeAdded);
                                    }
                                });
                                break;

                            case Default:
                            default:
                                break;
                        }

                        // Apply
                        dataFiltered.clear();
                        dataFiltered.addAll(items);

                        // Add ads
                        // TODO: Fix ads later
                        if (false && (BuildConfig.DEBUG || !MusicService.IsPremium)) {
                            final int n = Math.min(dataFiltered.size(), 7);
                            for (int i = 0; i <= n; i += ITEMS_PER_AD)
                                try {
                                    final NativeExpressAdView adView = new NativeExpressAdView(LibraryUIActivity.this);
                                    dataFiltered.add(i, adView);
                                } catch (Exception e) {
                                    Log.w(TAG, e);
                                }
                        }

                        (LibraryUIActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                notifyDataSetChanged();
                            }
                        });

                    } catch (Exception e) {
                        Log.w(TAG, e);
                    }
                }
            }).start();
        }

    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public View view;

        public ViewHolder(View view) {
            super(view);

            this.view = view;
        }

    }

    public class DrawerArrowAnimation {
        public class DrawerArrowDrawableToggle extends DrawerArrowDrawable {
            private final Activity mActivity;

            public DrawerArrowDrawableToggle(Activity activity, Context themedContext) {
                super(themedContext);
                mActivity = activity;
            }

            public void setPosition(float position) {
                if (position == 1f) {
                    setVerticalMirror(true);
                } else if (position == 0f) {
                    setVerticalMirror(false);
                }
                setProgress(position);
            }

            public float getPosition() {
                return getProgress();
            }
        }

    }

    public class PlaylistRecyclerViewAdapter extends RecyclerView.Adapter<PlaylistRecyclerViewAdapter.ViewHolder> {

        private final ArrayList<Pair<Long, String>> data;

        public PlaylistRecyclerViewAdapter() {
            data = new ArrayList<>();
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            View view = inflater.inflate(R.layout.library_ui_playlist_item, parent, false);

            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final Pair<Long, String> d = data.get(position);
            final View v = holder.view;

            TextView text = (TextView) v.findViewById(R.id.text);
            text.setText(d.second);

            v.setTag(d.first);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(LibraryUIActivity.this, R.style.AppTheme_AlertDialogStyle));
                    builder.setTitle("Are you sure?");
                    builder.setMessage("This will replace current playlist with selected one.");
                    builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            setFromPlaylist(Long.toString(d.first));

                            dialog.dismiss();

                            drawer_layout.closeDrawer(GravityCompat.END);
                        }
                    });
                    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();

                            drawer_layout.closeDrawer(GravityCompat.END);
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(LibraryUIActivity.this, R.style.AppTheme_AlertDialogStyle));
                    builder.setTitle("Select the action");
                    builder.setItems(new CharSequence[]{
                            "Delete"
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int item) {
                            switch (item) {
                                case 0:
                                    Music.deletePlaylist(getContentResolver(), d.first);
                                    removeData(d.first);
                                    break;
                            }
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();

                    return true;
                }
            });

        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public View view;

            public ViewHolder(View view) {
                super(view);

                this.view = view;
            }

        }

        public void setData(Collection<Pair<Long, String>> d) {
            data.clear();
            data.addAll(d);
            notifyDataSetChanged();
        }

        public void addData(Pair<Long, String> d) {
            data.add(d);

            notifyDataSetChanged();
        }

        public void removeData(Pair<Long, String> d) {
            data.remove(d);

            notifyDataSetChanged();
        }

        public void removeData(final Long id) {
            Pair<Long, String> d = null;
            for (Pair<Long, String> pair : data) {
                if (pair.first.equals(id)) {
                    d = pair;
                    break;
                }
            }
            if (d != null) {
                data.remove(d);
                notifyDataSetChanged();
            }
        }

    }


    //region UI filters

    public enum UIFilters {
        NoMP3("No MP3"),
        NoM4A("No M4A"),
        NoFLAC("No FLAC"),
        NoOGG("No OGG"),
        NoWAV("No WAV"),
        NoMP4("No MP4"),
        NoM4V("No M4V"),
        NoMKV("No MKV"),
        NoAVI("No AVI"),
        NoWEBM("No WEBM"),;

        private String friendlyName;

        UIFilters(String friendlyName) {
            this.friendlyName = friendlyName;
        }
    }

    public static final String TAG_SPREF_LIBRARY_UI_FILTERS = SPrefEx.TAG_SPREF + ".library_ui_filters";

    public static Set<UIFilters> getUIFilters(Context context) {
        Set<UIFilters> value = new HashSet<>();

        Set<String> values = new HashSet<>();
        for (String item : SPrefEx.get(context).getStringSet(TAG_SPREF_LIBRARY_UI_FILTERS, values)) {
            value.add(UIFilters.valueOf(item));
        }

        return value;
    }

    public static void setUIFilters(Context context, Set<UIFilters> value) {
        Set<String> values = new HashSet<>();
        for (UIFilters item : value) {
            values.add(String.valueOf(item));
        }

        SPrefEx.get(context)
                .edit()
                .putStringSet(TAG_SPREF_LIBRARY_UI_FILTERS, values)
                .apply();
    }

    private Spinner uiFilters_spinner;

    private void createUIFilters() {
        uiFilters_spinner = (Spinner) findViewById(R.id.uiFilters_spinner);

        UIFilters[] items = UIFilters.values();

        uiFilters_spinner.setAdapter(new ArrayAdapter<UIFilters>(this, 0, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CheckedTextView v = (CheckedTextView) convertView;

                if (v == null) {
                    v = new CheckedTextView(getContext(), null, android.R.style.TextAppearance_Material_Widget_TextView_SpinnerItem);
                    v.setTextColor(ContextCompat.getColor(getContext(), R.color.primary_text));
                    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
                    lp.setMargins(px, px, px, px);
                    v.setLayoutParams(lp);
                    v.setPadding(px, px, px, px);

                    v.setText("Filters");
                }

                return v;
            }

            @Override
            public View getDropDownView(final int position, View convertView, ViewGroup parent) {
                RelativeLayout v = (RelativeLayout) convertView;

                if (v == null) {
                    v = new RelativeLayout(getContext());
                    v.setLayoutParams(new ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    ));

                    Switch sv = new Switch(getContext());
                    sv.setTextColor(ContextCompat.getColor(getContext(), R.color.primary_text));
                    sv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
                    lp.setMargins(px, px, px, px);
                    sv.setLayoutParams(lp);
                    sv.setPadding(px, px, px, px);
                    sv.setMinWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 160, getResources().getDisplayMetrics()));

                    v.addView(sv);

                    sv.setText(getItem(position).friendlyName);
                    sv.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                            if (!compoundButton.isShown()) {
                                compoundButton.setChecked(!b);
                                return;
                            }

                            Set<UIFilters> value = getUIFilters(getContext());

                            UIFilters item = getItem(position);
                            try {
                                if (b)
                                    value.add(item);
                                else
                                    value.remove(item);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            setUIFilters(getContext(), value);

                            if (search_view != null)
                                adapter.filter("" + search_view.getQuery());
                        }
                    });
                }

                ((Switch) v.getChildAt(0)).setChecked(getUIFilters(getContext()).contains(getItem(position)));

                return v;
            }
        });

        uiFilters_spinner.post(new Runnable() {
            public void run() {
                uiFilters_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
            }
        });
    }

    //endregion

    //region UI sort mode

    public enum UISortMode {
        Default("Default"),
        Title("Title"),
        Album("Album"),
        Artist("Artist"),
        Played("Times Played"),
        Added("Added On"),;

        private String friendlyName;

        UISortMode(String friendlyName) {
            this.friendlyName = friendlyName;
        }
    }

    public static final String TAG_SPREF_LIBRARY_UI_SORT_MODE = SPrefEx.TAG_SPREF + ".library_ui_sort_mode";

    public static UISortMode getUISortMode(Context context) {
        return UISortMode.valueOf(SPrefEx.get(context).getString(TAG_SPREF_LIBRARY_UI_SORT_MODE, String.valueOf(UISortMode.Default)));
    }

    public static void setUISortMode(Context context, UISortMode value) {
        SPrefEx.get(context)
                .edit()
                .putString(TAG_SPREF_LIBRARY_UI_SORT_MODE, String.valueOf(value))
                .apply();
    }

    private Spinner uiSortMode_spinner;

    private void createUISortMode() {
        uiSortMode_spinner = (Spinner) findViewById(R.id.uiSortMode_spinner);

        UISortMode[] items = UISortMode.values();

        uiSortMode_spinner.setAdapter(new ArrayAdapter<UISortMode>(this, 0, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CheckedTextView text = (CheckedTextView) getDropDownView(position, convertView, parent);

                text.setText("Sorting: " + text.getText());

                return text;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                CheckedTextView text = (CheckedTextView) convertView;

                if (text == null) {
                    text = new CheckedTextView(getContext(), null, android.R.style.TextAppearance_Material_Widget_TextView_SpinnerItem);
                    text.setTextColor(ContextCompat.getColor(getContext(), R.color.primary_text));
                    text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
                    lp.setMargins(px, px, px, px);
                    text.setLayoutParams(lp);
                    text.setPadding(px, px, px, px);
                }

                text.setText(getItem(position).friendlyName);

                return text;
            }
        });

        int i = 0;
        UISortMode lastMode = getUISortMode(this);
        for (; i < items.length; i++)
            if (items[i] == lastMode)
                break;
        uiSortMode_spinner.setSelection(i, true);

        uiSortMode_spinner.post(new Runnable() {
            public void run() {
                uiSortMode_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                        setUISortMode(getApplicationContext(), (UISortMode) adapterView.getItemAtPosition(position));
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
            }
        });
    }

    //endregion

}
