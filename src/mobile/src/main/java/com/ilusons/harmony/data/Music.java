package com.ilusons.harmony.data;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.ilusons.harmony.ref.CacheEx;
import com.ilusons.harmony.ref.IOEx;
import com.ilusons.harmony.ref.ImageEx;
import com.ilusons.harmony.ref.JavaEx;
import com.ilusons.harmony.ref.LyricsEx;
import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;

import org.apache.commons.io.FileUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

public class Music {

    // Logger TAG
    private static final String TAG = Music.class.getSimpleName();

    public static final String KEY_CACHE_DIR_COVER = "covers";
    public static final String KEY_CACHE_DIR_LYRICS = "lyrics";

    public static final String KEY_CACHE_KEY_LIBRARY = "library.index";

    public String Title = "";
    public String Artist = "";
    public String Album = "";
    public Integer Length = -1;
    public String Path;

    @Override
    public boolean equals(Object obj) {
        Music other = (Music) obj;

        if (other == null)
            return false;

        if (Path.equals(other.Path))
            return true;

        return false;
    }

    public String getText() {
        return TextUtils.isEmpty(Artist) ? Title : Artist + " - " + Title;
    }

    public String getTextDetailed() {
        StringBuilder sb = new StringBuilder();

        String nl = System.getProperty("line.separator");

        sb.append(Title);
        sb.append(nl);
        sb.append(Artist);
        sb.append(nl);
        sb.append(Album);

        return sb.toString();
    }

    public boolean hasVideo() {
        return !TextUtils.isEmpty(Path) && Path.toLowerCase().contains(".mp4");
    }

    public Bitmap getCover(final Context context, int size) {
        Bitmap result;

        String key = Path;
        if (size > 0)
            key = key + size;

        // Load from cache
        result = CacheEx.getInstance().getBitmap(key);

        if (result != null)
            return result;

        // File
        File file = IOEx.getDiskCacheFile(context, KEY_CACHE_DIR_COVER, Path);

        // Load from cache folder
        if (file.exists())
            result = BitmapFactory.decodeFile(file.getAbsolutePath());

        // Re-sample
        if (result != null) {
            try {
                Bitmap.Config config = result.getConfig();
                if (config == null) {
                    config = Bitmap.Config.ARGB_8888;
                }
                result = result.copy(config, false);
            } catch (Exception e) {
                Log.w(TAG, e);
            }

            // Re-size
            if (result != null && size > 0) {
                size = Math.min(size, Math.max(result.getWidth(), result.getHeight()));

                result = Bitmap.createScaledBitmap(result, size, size, true);
            }

            // Put in cache
            CacheEx.getInstance().putBitmap(key, result);
        }

        return result;
    }

    public Bitmap getCover(final Context context) {
        return getCover(context, -1);
    }

    private static AsyncTask<Object, Object, Bitmap> getCoverOrDownloadTask = null;

    public static void getCoverOrDownload(final Context context, final int size, final Music data, final JavaEx.ActionT<Bitmap> onResult) {
        if (getCoverOrDownloadTask != null) {
            getCoverOrDownloadTask.cancel(true);
            try {
                getCoverOrDownloadTask.get(1, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Log.w(TAG, e);
            }
            getCoverOrDownloadTask = null;
        }
        getCoverOrDownloadTask = (new AsyncTask<Object, Object, Bitmap>() {
            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (context == null)
                    return;

                if (onResult != null)
                    onResult.execute(bitmap);
            }

            @Override
            protected Bitmap doInBackground(Object... objects) {
                if (isCancelled())
                    throw new CancellationException();

                Bitmap result = data.getCover(context, size);

                // File
                File file = IOEx.getDiskCacheFile(context, KEY_CACHE_DIR_COVER, data.Path);

                if (isCancelled())
                    throw new CancellationException();

                // Download and cache to folder then load
                if (result == null) {
                    try {
                        URL url = new URL(String.format(
                                "https://itunes.apple.com/search?term=%s&entity=song&media=music",
                                URLEncoder.encode(data.getText(), "UTF-8")));

                        Connection connection = Jsoup.connect(url.toExternalForm())
                                .timeout(3 * 1000)
                                .ignoreContentType(true);

                        Document document = connection.get();

                        JsonObject response = new JsonParser().parse(document.text()).getAsJsonObject();

                        JsonArray results = response.getAsJsonArray("results");

                        String downloadUrl = results
                                .get(0)
                                .getAsJsonObject()
                                .get("artworkUrl60")
                                .getAsString()
                                .replace("60x60bb.jpg", "1000x1000bb.jpg");

                        BufferedInputStream in = null;
                        FileOutputStream out = null;
                        try {
                            in = new BufferedInputStream(new URL(downloadUrl).openStream());
                            out = new FileOutputStream(file.getAbsoluteFile());

                            final byte data[] = new byte[1024];
                            int count;
                            while ((count = in.read(data, 0, 1024)) != -1) {
                                out.write(data, 0, count);
                            }
                        } finally {
                            if (in != null) {
                                in.close();
                            }
                            if (out != null) {
                                out.close();
                            }
                        }

                    } catch (Exception e) {
                        Log.w(TAG, e);
                    }

                    if (file.exists())
                        result = BitmapFactory.decodeFile(file.getAbsolutePath());

                    // Refresh once more
                    if (result == null) {
                        data.refresh(context);

                        result = data.getCover(context, size);
                    }

                    // Resample
                    if (result != null) {
                        try {
                            Bitmap.Config config = result.getConfig();
                            if (config == null) {
                                config = Bitmap.Config.ARGB_8888;
                            }
                            result = result.copy(config, false);
                        } catch (Exception e) {
                            Log.w(TAG, e);
                        }

                        // Put in cache
                        CacheEx.getInstance().putBitmap(data.Path, result);
                    }

                }

                return result;
            }
        });
        getCoverOrDownloadTask.execute();
    }

    public static void putCover(Context context, Music data, Bitmap bmp) {
        IOEx.putBitmapInDiskCache(context, KEY_CACHE_DIR_COVER, data.Path, bmp);
    }

    public String getLyrics(final Context context) {
        String result;

        // Load from cache
        result = (String) CacheEx.getInstance().get(KEY_CACHE_DIR_LYRICS + Path);

        if (result != null)
            return result;

        // File
        File file = IOEx.getDiskCacheFile(context, KEY_CACHE_DIR_LYRICS, Path);

        // Load from cache folder
        if (file.exists()) try {
            result = FileUtils.readFileToString(file, Charset.defaultCharset());
        } catch (Exception e) {
            Log.w(TAG, e);
        }

        if (result != null) {
            // Put in cache
            CacheEx.getInstance().put(KEY_CACHE_DIR_LYRICS + Path, result);
        }

        return result;
    }

    private static AsyncTask<Void, Void, String> getLyricsOrDownloadTask = null;

    public static void getLyricsOrDownload(final Context context, final Music data, final JavaEx.ActionT<String> onResult) {
        if (getLyricsOrDownloadTask != null) {
            getLyricsOrDownloadTask.cancel(true);
            try {
                getLyricsOrDownloadTask.get(1, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Log.w(TAG, e);
            }
            getLyricsOrDownloadTask = null;
        }
        getLyricsOrDownloadTask = (new AsyncTask<Void, Void, String>() {
            @Override
            protected void onPostExecute(String result) {
                if (context == null)
                    return;

                if (onResult != null)
                    onResult.execute(result);
            }

            @Override
            protected String doInBackground(Void... Voids) {
                String result = data.getLyrics(context);

                if (!TextUtils.isEmpty(result))
                    return result;

                // Refresh once more
                if (result == null) {
                    data.refresh(context);

                    result = data.getLyrics(context);

                    if (!TextUtils.isEmpty(result))
                        return result;
                }

                try {
                    if (isCancelled())
                        throw new CancellationException();

                    ArrayList<LyricsEx.Lyrics> results = LyricsEx.GeniusApi.get(data.getText());

                    if (!(results == null || results.size() == 0))
                        result = results.get(0).Content;

                    if (isCancelled())
                        throw new CancellationException();

                    data.putLyrics(context, result);
                } catch (Exception e) {
                    Log.w(TAG, e);
                }

                if (TextUtils.isEmpty(result)) {
                    result = "";

                    data.putLyrics(context, "");
                }

                return result;
            }
        });
        getLyricsOrDownloadTask.execute();
    }

    public void putLyrics(Context context, String content) {
        // File
        File file = IOEx.getDiskCacheFile(context, KEY_CACHE_DIR_LYRICS, Path);

        try {
            FileUtils.writeStringToFile(file, content, Charset.defaultCharset());
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

    public static Music decode(Context context, String path, boolean fastMode, Music data) {
        if (data == null)
            data = new Music();

        // HACK: Calling the devil
        System.gc();
        Runtime.getRuntime().gc();

        // Metadata from system
        if (path.toLowerCase().startsWith("content") && path.toLowerCase().contains("audio")) {
            Uri contentUri = Uri.parse(path);
            Cursor cursor = null;

            try {
                String[] projection = {
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.IS_MUSIC,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DURATION
                };

                CursorLoader loader = new CursorLoader(context, contentUri, projection, null, null, null);

                cursor = loader.loadInBackground();

                cursor.moveToFirst();

                int isMusic = 1;
                try {
                    isMusic = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC));
                } catch (Exception e) {
                    // Eat
                }

                if (isMusic != 0) {

                    try {
                        data.Title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                    } catch (Exception e) {
                        // Eat
                    }
                    try {
                        data.Artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                    } catch (Exception e) {
                        // Eat
                    }
                    try {
                        data.Album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
                    } catch (Exception e) {
                        // Eat
                    }
                    try {
                        data.Length = (int) cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                    } catch (Exception e) {
                        // Eat
                    }

                    path = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))).getPath();

                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    try {
                        mmr.setDataSource(path);

                        byte[] cover = mmr.getEmbeddedPicture();
                        if (cover != null && cover.length > 0) {
                            Bitmap bmp = ImageEx.decodeBitmap(cover, 256, 256);
                            if (bmp != null)
                                putCover(context, data, bmp);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "metadata from system - getEmbeddedPicture", e);
                    } finally {
                        mmr.release();
                    }

                }
            } catch (Exception e) {
                Log.w(TAG, "metadata from system", e);
            } finally {
                if (cursor != null)
                    cursor.close();
            }
        }

        if (path.toLowerCase().startsWith("content") && path.toLowerCase().contains("video")) {
            Uri contentUri = Uri.parse(path);
            Cursor cursor = null;

            try {
                String[] projection = {
                        MediaStore.Video.Media.DATA,
                        MediaStore.Video.Media.TITLE,
                        MediaStore.Video.Media.ARTIST,
                        MediaStore.Video.Media.ALBUM,
                        MediaStore.Video.Media.DURATION
                };

                CursorLoader loader = new CursorLoader(context, contentUri, projection, null, null, null);

                cursor = loader.loadInBackground();

                cursor.moveToFirst();

                try {
                    data.Title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE));
                } catch (Exception e) {
                    // Eat
                }
                try {
                    data.Artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST));
                } catch (Exception e) {
                    // Eat
                }
                try {
                    data.Album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ALBUM));
                } catch (Exception e) {
                    // Eat
                }
                try {
                    data.Length = (int) cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION));
                } catch (Exception e) {
                    // Eat
                }

                path = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))).getPath();

                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                try {
                    mmr.setDataSource(path);

                    Bitmap bmp = mmr.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (bmp != null)
                        putCover(context, data, bmp);
                } catch (Exception e) {
                    Log.w(TAG, "metadata from system - getEmbeddedPicture", e);
                } finally {
                    mmr.release();
                }


            } catch (Exception e) {
                Log.w(TAG, "metadata from system", e);
            } finally {
                if (cursor != null)
                    cursor.close();
            }
        }

        // Metadata from tags for mp3 only
        if (!fastMode && !path.toLowerCase().startsWith("content") && path.toLowerCase().endsWith(".mp3")) {
            File file = new File(path);

            // HACK: Only scan for files < 42mb
            // HACK: This tags decoder is inefficient for android, takes too much memory
            if (file.length() <= 42 * 1024 * 1024) {
                try {
                    Mp3File mp3file = new Mp3File(file.getAbsoluteFile());

                    if (mp3file.hasId3v2Tag()) {
                        ID3v2 tags = mp3file.getId3v2Tag();

                        data.Title = tags.getTitle();
                        data.Artist = tags.getArtist();
                        data.Album = tags.getAlbum();
                        data.Length = tags.getLength();

                        if (data.getCover(context) == null) {
                            byte[] cover = tags.getAlbumImage();
                            if (cover != null && cover.length > 0) {
                                Bitmap bmp = ImageEx.decodeBitmap(cover, 256, 256);

                                if (bmp != null)
                                    putCover(context, data, bmp);
                            }
                        }

                        data.putLyrics(context, LyricsEx.getLyrics(tags));
                    }

                    if (TextUtils.isEmpty(data.Title) && mp3file.hasId3v1Tag()) {
                        ID3v1 tags = mp3file.getId3v1Tag();

                        data.Title = tags.getTitle();
                        data.Artist = tags.getArtist();
                        data.Album = tags.getAlbum();
                    }

                } catch (Exception e) {
                    Log.w(TAG, "metadata from tags", e);
                }
            }
        }

        if (TextUtils.isEmpty(data.Title)) {
            data.Title = (new File(path)).getName().replaceFirst("[.][^.]+$", "");
        }

        data.Path = path;

        Log.d(TAG, "added to library\n" + path);

        // HACK: Calling the devil
        System.gc();
        Runtime.getRuntime().gc();

        return data;
    }

    public void refresh(Context context) {
        Music.decode(context, Path, true, this);
    }

    public static Music load(Context context, String path) {
        ArrayList<Music> all = load(context);

        Music m = null;

        for (Music item : all) {
            if (item.Path.equalsIgnoreCase(path)) {
                m = item;
                break;
            }
        }

        if (m == null)
            m = decode(context, path, false, null);

        return m;
    }

    public static ArrayList<Music> load(Context context) {
        ArrayList<Music> result = new ArrayList<>();

        File cacheFile = IOEx.getDiskCacheFile(context, KEY_CACHE_KEY_LIBRARY);
        if (!cacheFile.exists())
            return result;

        try {
            String json;
            json = FileUtils.readFileToString(cacheFile, "utf-8");

            Gson serializer = getSerializer();

            result.addAll(Arrays.asList(serializer.fromJson(json, Music[].class)));
        } catch (Exception e) {
            e.printStackTrace();

            return result;
        }

        return result;
    }

    public static void save(Context context, ArrayList<Music> data) {

        // TODO: Sort playlist better
        Collections.sort(data, new Comparator<Music>() {
            @Override
            public int compare(Music x, Music y) {
                return x.getText().compareTo(y.getText());
            }
        });

        Gson serializer = getSerializer();

        String json = serializer.toJson(data.toArray(), Music[].class);

        File cacheFile = IOEx.getDiskCacheFile(context, KEY_CACHE_KEY_LIBRARY);
        try {
            FileUtils.writeStringToFile(cacheFile, json, "utf-8", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void reset(Context context) {
        File cacheFile = IOEx.getDiskCacheFile(context, KEY_CACHE_KEY_LIBRARY);
        if (cacheFile.exists())
            cacheFile.delete();
    }

    static Gson getSerializer() {
        GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(Music.class, new Serializer());
        gsonBuilder.registerTypeAdapter(Music.class, new Deserializer());

        Gson gson = gsonBuilder.create();

        return gson;
    }

    static class Serializer implements JsonSerializer<Music> {

        @Override
        public JsonElement serialize(final Music data, final Type type, final JsonSerializationContext context) {
            JsonObject result = new JsonObject();

            result.add("Title", new JsonPrimitive(data.Title));
            result.add("Artist", new JsonPrimitive(TextUtils.isEmpty(data.Artist) ? "" : data.Artist));
            result.add("Album", new JsonPrimitive(TextUtils.isEmpty(data.Album) ? "" : data.Album));
            result.add("Length", new JsonPrimitive(data.Length));
            result.add("Path", new JsonPrimitive(data.Path));

            return result;
        }

    }

    static class Deserializer implements JsonDeserializer<Music> {

        @Override
        public Music deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Music result = new Music();

            JsonObject data = json.getAsJsonObject();

            result.Title = data.get("Title").getAsString();
            result.Artist = data.get("Artist").getAsString();
            result.Album = data.get("Album").getAsString();
            result.Path = data.get("Path").getAsString();
            result.Length = data.get("Length").getAsInt();

            return result;
        }
    }

}
