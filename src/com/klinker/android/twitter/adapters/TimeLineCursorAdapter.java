package com.klinker.android.twitter.adapters;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.klinker.android.twitter.R;
import com.klinker.android.twitter.data.App;
import com.klinker.android.twitter.data.sq_lite.DMDataSource;
import com.klinker.android.twitter.data.sq_lite.HomeSQLiteHelper;
import com.klinker.android.twitter.manipulations.ExpansionAnimation;
import com.klinker.android.twitter.manipulations.widgets.NetworkedCacheableImageView;
import com.klinker.android.twitter.settings.AppSettings;
import com.klinker.android.twitter.ui.BrowserActivity;
import com.klinker.android.twitter.ui.compose.ComposeActivity;
import com.klinker.android.twitter.ui.profile_viewer.ProfilePager;
import com.klinker.android.twitter.ui.tweet_viewer.TweetPager;
import com.klinker.android.twitter.manipulations.PhotoViewerDialog;
import com.klinker.android.twitter.utils.EmojiUtils;
import com.klinker.android.twitter.utils.HtmlUtils;
import com.klinker.android.twitter.utils.ImageUtils;
import com.klinker.android.twitter.utils.Utils;
import com.klinker.android.twitter.utils.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

import twitter4j.DirectMessage;
import twitter4j.MediaEntity;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import uk.co.senab.bitmapcache.BitmapLruCache;
import uk.co.senab.bitmapcache.CacheableBitmapDrawable;

public class TimeLineCursorAdapter extends CursorAdapter {

    public Cursor cursor;
    public AppSettings settings;
    public Context context;
    private final LayoutInflater inflater;
    private boolean isDM = false;
    private SharedPreferences sharedPrefs;
    private int cancelButton;
    private int border;

    private Handler[] mHandlers;
    private Handler emojiHandler;
    private int currHandler;

    public boolean hasKeyboard = false;

    private int layout;
    private XmlResourceParser addonLayout = null;
    private Resources res;
    private boolean talonLayout;
    private BitmapLruCache mCache;

    private ColorDrawable transparent;

    public java.text.DateFormat dateFormatter;
    public java.text.DateFormat timeFormatter;

    public static class ViewHolder {
        public TextView name;
        public TextView screenTV;
        public ImageView profilePic;
        public TextView tweet;
        public TextView time;
        public TextView retweeter;
        public EditText reply;
        public ImageButton favorite;
        public ImageButton retweet;
        public TextView favCount;
        public TextView retweetCount;
        public LinearLayout expandArea;
        public ImageButton replyButton;
        public ImageView image;
        public LinearLayout background;
        public TextView charRemaining;
        public ImageView playButton;
        public ImageButton quoteButton;
        public ImageButton shareButton;
        //public Bitmap tweetPic;

        public long tweetId;
        public boolean isFavorited;
        public String screenName;
        public String picUrl;
        public String retweeterName;

    }

    public TimeLineCursorAdapter(Context context, Cursor cursor, boolean isDM) {
        super(context, cursor, 0);

        this.cursor = cursor;
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.isDM = isDM;
        
        settings = AppSettings.getInstance(context);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{R.attr.cancelButton});
        cancelButton = a.getResourceId(0, 0);
        a.recycle();

        talonLayout = settings.layout == AppSettings.LAYOUT_TALON;

        if (settings.addonTheme) {
            try {
                res = context.getPackageManager().getResourcesForApplication(settings.addonThemePackage);
                addonLayout = res.getLayout(res.getIdentifier("tweet", "layout", settings.addonThemePackage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        layout = talonLayout ? R.layout.tweet : R.layout.tweet_hangout;

        TypedArray b;
        if (settings.roundContactImages) {
            b = context.getTheme().obtainStyledAttributes(new int[]{R.attr.circleBorder});
        } else {
            b = context.getTheme().obtainStyledAttributes(new int[]{R.attr.squareBorder});
        }
        border = b.getResourceId(0, 0);
        b.recycle();

        mCache = App.getInstance(context).getBitmapCache();

        dateFormatter = android.text.format.DateFormat.getDateFormat(context);
        timeFormatter = android.text.format.DateFormat.getTimeFormat(context);
        if (settings.militaryTime) {
            timeFormatter = new SimpleDateFormat("kk:mm");
        }

        transparent = new ColorDrawable(android.R.color.transparent);

        mHandlers = new Handler[4];
        for (int i = 0; i < 4; i++) {
            mHandlers[i] = new Handler();
        }
        emojiHandler = new Handler();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        View v = null;
        final ViewHolder holder = new ViewHolder();
        if (settings.addonTheme) {
            try {
                Context viewContext = null;

                if (res == null) {
                    res = context.getPackageManager().getResourcesForApplication(settings.addonThemePackage);
                }

                try {
                    viewContext = context.createPackageContext(settings.addonThemePackage, Context.CONTEXT_IGNORE_SECURITY);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (res != null && viewContext != null) {
                    int id = res.getIdentifier("tweet", "layout", settings.addonThemePackage);
                    v = LayoutInflater.from(viewContext).inflate(res.getLayout(id), null);

                    holder.name = (TextView) v.findViewById(res.getIdentifier("name", "id", settings.addonThemePackage));
                    holder.screenTV = (TextView) v.findViewById(res.getIdentifier("screenname", "id", settings.addonThemePackage));
                    holder.profilePic = (ImageView) v.findViewById(res.getIdentifier("profile_pic", "id", settings.addonThemePackage));
                    holder.time = (TextView) v.findViewById(res.getIdentifier("time", "id", settings.addonThemePackage));
                    holder.tweet = (TextView) v.findViewById(res.getIdentifier("tweet", "id", settings.addonThemePackage));
                    holder.reply = (EditText) v.findViewById(res.getIdentifier("reply", "id", settings.addonThemePackage));
                    holder.favorite = (ImageButton) v.findViewById(res.getIdentifier("favorite", "id", settings.addonThemePackage));
                    holder.retweet = (ImageButton) v.findViewById(res.getIdentifier("retweet", "id", settings.addonThemePackage));
                    holder.favCount = (TextView) v.findViewById(res.getIdentifier("fav_count", "id", settings.addonThemePackage));
                    holder.retweetCount = (TextView) v.findViewById(res.getIdentifier("retweet_count", "id", settings.addonThemePackage));
                    holder.expandArea = (LinearLayout) v.findViewById(res.getIdentifier("expansion", "id", settings.addonThemePackage));
                    holder.replyButton = (ImageButton) v.findViewById(res.getIdentifier("reply_button", "id", settings.addonThemePackage));
                    holder.image = (ImageView) v.findViewById(res.getIdentifier("image", "id", settings.addonThemePackage));
                    holder.retweeter = (TextView) v.findViewById(res.getIdentifier("retweeter", "id", settings.addonThemePackage));
                    holder.background = (LinearLayout) v.findViewById(res.getIdentifier("background", "id", settings.addonThemePackage));
                    holder.charRemaining = (TextView) v.findViewById(res.getIdentifier("char_remaining", "id", settings.addonThemePackage));
                    holder.playButton = (ImageView) v.findViewById(res.getIdentifier("play_button", "id", settings.addonThemePackage));
                    try {
                        holder.quoteButton = (ImageButton) v.findViewById(res.getIdentifier("quote_button", "id", settings.addonThemePackage));
                        holder.shareButton = (ImageButton) v.findViewById(res.getIdentifier("share_button", "id", settings.addonThemePackage));
                    } catch (Exception e) {
                        // they don't exist because the theme was made before they were added
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
                v = inflater.inflate(layout, viewGroup, false);

                holder.name = (TextView) v.findViewById(R.id.name);
                holder.screenTV = (TextView) v.findViewById(R.id.screenname);
                holder.profilePic = (ImageView) v.findViewById(R.id.profile_pic);
                holder.time = (TextView) v.findViewById(R.id.time);
                holder.tweet = (TextView) v.findViewById(R.id.tweet);
                holder.reply = (EditText) v.findViewById(R.id.reply);
                holder.favorite = (ImageButton) v.findViewById(R.id.favorite);
                holder.retweet = (ImageButton) v.findViewById(R.id.retweet);
                holder.favCount = (TextView) v.findViewById(R.id.fav_count);
                holder.retweetCount = (TextView) v.findViewById(R.id.retweet_count);
                holder.expandArea = (LinearLayout) v.findViewById(R.id.expansion);
                holder.replyButton = (ImageButton) v.findViewById(R.id.reply_button);
                holder.image = (NetworkedCacheableImageView) v.findViewById(R.id.image);
                holder.retweeter = (TextView) v.findViewById(R.id.retweeter);
                holder.background = (LinearLayout) v.findViewById(R.id.background);
                holder.charRemaining = (TextView) v.findViewById(R.id.char_remaining);
                holder.playButton = (NetworkedCacheableImageView) v.findViewById(R.id.play_button);
                try {
                    holder.quoteButton = (ImageButton) v.findViewById(R.id.quote_button);
                    holder.shareButton = (ImageButton) v.findViewById(R.id.share_button);
                } catch (Exception x) {
                    // theme was made before they were added
                }

            }
        } else {
            v = inflater.inflate(layout, viewGroup, false);

            holder.name = (TextView) v.findViewById(R.id.name);
            holder.screenTV = (TextView) v.findViewById(R.id.screenname);
            holder.profilePic = (ImageView) v.findViewById(R.id.profile_pic);
            holder.time = (TextView) v.findViewById(R.id.time);
            holder.tweet = (TextView) v.findViewById(R.id.tweet);
            holder.reply = (EditText) v.findViewById(R.id.reply);
            holder.favorite = (ImageButton) v.findViewById(R.id.favorite);
            holder.retweet = (ImageButton) v.findViewById(R.id.retweet);
            holder.favCount = (TextView) v.findViewById(R.id.fav_count);
            holder.retweetCount = (TextView) v.findViewById(R.id.retweet_count);
            holder.expandArea = (LinearLayout) v.findViewById(R.id.expansion);
            holder.replyButton = (ImageButton) v.findViewById(R.id.reply_button);
            holder.image = (NetworkedCacheableImageView) v.findViewById(R.id.image);
            holder.retweeter = (TextView) v.findViewById(R.id.retweeter);
            holder.background = (LinearLayout) v.findViewById(R.id.background);
            holder.charRemaining = (TextView) v.findViewById(R.id.char_remaining);
            holder.playButton = (NetworkedCacheableImageView) v.findViewById(R.id.play_button);
            try {
                holder.quoteButton = (ImageButton) v.findViewById(R.id.quote_button);
                holder.shareButton = (ImageButton) v.findViewById(R.id.share_button);
            } catch (Exception x) {
                // theme was made before they were added
            }
        }

        // sets up the font sizes
        holder.tweet.setTextSize(settings.textSize);
        holder.screenTV.setTextSize(settings.textSize - 2);
        holder.name.setTextSize(settings.textSize + 4);
        holder.time.setTextSize(settings.textSize - 3);
        holder.retweeter.setTextSize(settings.textSize - 3);
        holder.favCount.setTextSize(settings.textSize + 1);
        holder.retweetCount.setTextSize(settings.textSize + 1);
        holder.reply.setTextSize(settings.textSize);

        v.setTag(holder);

        return v;
    }

    @Override
    public void bindView(final View view, Context mContext, final Cursor cursor) {
        final ViewHolder holder = (ViewHolder) view.getTag();

        if (holder.expandArea.getVisibility() == View.VISIBLE) {
            removeExpansionNoAnimation(holder);
            holder.retweetCount.setText("");
            holder.favCount.setText("");
            holder.reply.setText("");
            holder.retweet.clearColorFilter();
            holder.favorite.clearColorFilter();
        }

        final long id = cursor.getLong(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_TWEET_ID));
        holder.tweetId = id;
        final String profilePic = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_PRO_PIC));
        String tweetTexts = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_TEXT));
        final String name = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_NAME));
        final String screenname = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_SCREEN_NAME));
        final String picUrl = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_PIC_URL));
        holder.picUrl = picUrl;
        final long longTime = cursor.getLong(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_TIME));
        final String otherUrl = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_URL));
        final String users = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_USERS));
        final String hashtags = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_HASHTAGS));

        String retweeter;
        try {
            retweeter = cursor.getString(cursor.getColumnIndex(HomeSQLiteHelper.COLUMN_RETWEETER));
        } catch (Exception e) {
            retweeter = "";
        }

        final String tweetText = tweetTexts;

        if(!settings.reverseClickActions) {
            final String fRetweeter = retweeter;
            if (!isDM) {
                holder.background.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        String link;

                        boolean displayPic = !holder.picUrl.equals("") && !holder.picUrl.contains("youtube");
                        if (displayPic) {
                            link = holder.picUrl;
                        } else {
                            link = otherUrl.split("  ")[0];
                        }

                        Intent viewTweet = new Intent(context, TweetPager.class);
                        viewTweet.putExtra("name", name);
                        viewTweet.putExtra("screenname", screenname);
                        viewTweet.putExtra("time", longTime);
                        viewTweet.putExtra("tweet", tweetText);
                        viewTweet.putExtra("retweeter", fRetweeter);
                        viewTweet.putExtra("webpage", link);
                        viewTweet.putExtra("other_links", otherUrl);
                        viewTweet.putExtra("picture", displayPic);
                        viewTweet.putExtra("tweetid", holder.tweetId);
                        viewTweet.putExtra("proPic", profilePic);
                        viewTweet.putExtra("users", users);
                        viewTweet.putExtra("hashtags", hashtags);

                        context.startActivity(viewTweet);

                        return true;
                    }
                });
            }

            if (!isDM) {
                holder.background.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (holder.expandArea.getVisibility() == View.GONE) {
                            addExpansion(holder, screenname, users, otherUrl.split("  "), holder.picUrl, id);
                        } else {
                            removeExpansionWithAnimation(holder);
                            removeKeyboard(holder);
                        }
                    }
                });
            }
        } else {
            final String fRetweeter = retweeter;
            if (!isDM) {
                holder.background.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String link = "";

                        boolean displayPic = !holder.picUrl.equals("") && !holder.picUrl.contains("youtube");
                        if (displayPic) {
                            link = holder.picUrl;
                        } else {
                            link = otherUrl.split("  ")[0];
                        }

                        Intent viewTweet = new Intent(context, TweetPager.class);
                        viewTweet.putExtra("name", name);
                        viewTweet.putExtra("screenname", screenname);
                        viewTweet.putExtra("time", longTime);
                        viewTweet.putExtra("tweet", tweetText);
                        viewTweet.putExtra("retweeter", fRetweeter);
                        viewTweet.putExtra("webpage", link);
                        viewTweet.putExtra("picture", displayPic);
                        viewTweet.putExtra("other_links", otherUrl);
                        viewTweet.putExtra("tweetid", holder.tweetId);
                        viewTweet.putExtra("proPic", profilePic);
                        viewTweet.putExtra("users", users);
                        viewTweet.putExtra("hashtags", hashtags);

                        context.startActivity(viewTweet);
                    }
                });
            }

            if (!isDM) {
                holder.background.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        if (holder.expandArea.getVisibility() == View.GONE) {
                            addExpansion(holder, screenname, users, otherUrl.split("  "), holder.picUrl, id);
                        } else {
                            removeExpansionWithAnimation(holder);
                            removeKeyboard(holder);
                        }

                        return true;
                    }
                });
            }
        }

        if (isDM) {
            holder.background.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);

                    builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            new DeleteTweet().execute("" + holder.tweetId);
                        }
                    });

                    builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });

                    builder.setTitle(R.string.delete_direct_message);

                    AlertDialog dialog = builder.create();
                    dialog.show();

                    return true;
                }
            });

            if (otherUrl != null && !otherUrl.equals("")) {
                holder.background.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent browser = new Intent(context, BrowserActivity.class);
                        browser.putExtra("url", otherUrl);
                        context.startActivity(browser);
                    }
                });
            }
        }

        holder.profilePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent viewProfile = new Intent(context, ProfilePager.class);
                viewProfile.putExtra("name", name);
                viewProfile.putExtra("screenname", screenname);
                viewProfile.putExtra("proPic", profilePic);
                viewProfile.putExtra("tweetid", holder.tweetId);
                viewProfile.putExtra("retweet", holder.retweeter.getVisibility() == View.VISIBLE);
                viewProfile.putExtra("long_click", false);

                context.startActivity(viewProfile);
            }
        });

        holder.profilePic.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View view) {

                Intent viewProfile = new Intent(context, ProfilePager.class);
                viewProfile.putExtra("name", name);
                viewProfile.putExtra("screenname", screenname);
                viewProfile.putExtra("proPic", profilePic);
                viewProfile.putExtra("tweetid", holder.tweetId);
                viewProfile.putExtra("retweet", holder.retweeter.getVisibility() == View.VISIBLE);
                viewProfile.putExtra("long_click", true);

                context.startActivity(viewProfile);

                return false;
            }
        });

        if (!settings.showBoth) {
            holder.name.setText(settings.displayScreenName ? "@" + screenname : name);
        } else {
            if (holder.screenTV.getVisibility() == View.GONE) {
                holder.screenTV.setVisibility(View.VISIBLE);
            }
            holder.name.setText(name);
            holder.screenTV.setText("@" + screenname);
        }

        if (!settings.absoluteDate) {
            holder.time.setText(Utils.getTimeAgo(longTime, context));
        } else {
            Date date = new Date(longTime);
            holder.time.setText(timeFormatter.format(date).replace("24:", "00:") + ", " + dateFormatter.format(date));
        }

        /*if (tweetText.contains("<font")) {
            if (settings.addonTheme) {
                holder.tweet.setText(Html.fromHtml(tweetText.replaceAll("FF8800", settings.accentColor).replaceAll("\n", "<br/>")));
            } else {
                holder.tweet.setText(Html.fromHtml(tweetText.replaceAll("\n", "<br/>")));
            }
        } else {
            holder.tweet.setText(tweetText);
        }*/
        holder.tweet.setText(tweetText);
        //TextUtils.linkifyText(context, holder.tweet, holder.background, false);
        emojiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (id == holder.tweetId) {
                    if (settings.addonTheme) {
                        holder.tweet.setText(TextUtils.colorText(tweetText, settings.accentInt));
                    } else {
                        holder.tweet.setText(TextUtils.colorText(tweetText, context.getResources().getColor(R.color.app_color)));
                    }
                }
            }
        }, 500);

        if(settings.inlinePics && holder.picUrl != null) {
            if (holder.picUrl.equals("")) {
                if (holder.image.getVisibility() != View.GONE) {
                    holder.image.setVisibility(View.GONE);
                }

                if (holder.playButton.getVisibility() == View.VISIBLE) {
                    holder.playButton.setVisibility(View.GONE);
                }
            } else {
                if (holder.image.getVisibility() == View.GONE) {
                    holder.image.setVisibility(View.VISIBLE);
                }

                if (holder.picUrl.contains("youtube")) {
                    if (holder.playButton.getVisibility() == View.GONE) {
                        holder.playButton.setVisibility(View.VISIBLE);
                    }

                    final String fRetweeter = retweeter;

                    holder.image.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            String link;

                            boolean displayPic = !holder.picUrl.equals("") && !holder.picUrl.contains("youtube");
                            if (displayPic) {
                                link = holder.picUrl;
                            } else {
                                link = otherUrl.split("  ")[0];
                            }

                            Intent viewTweet = new Intent(context, TweetPager.class);
                            viewTweet.putExtra("name", name);
                            viewTweet.putExtra("screenname", screenname);
                            viewTweet.putExtra("time", longTime);
                            viewTweet.putExtra("tweet", tweetText);
                            viewTweet.putExtra("retweeter", fRetweeter);
                            viewTweet.putExtra("webpage", link);
                            viewTweet.putExtra("other_links", otherUrl);
                            viewTweet.putExtra("picture", displayPic);
                            viewTweet.putExtra("tweetid", holder.tweetId);
                            viewTweet.putExtra("proPic", profilePic);
                            viewTweet.putExtra("users", users);
                            viewTweet.putExtra("hashtags", hashtags);
                            viewTweet.putExtra("clicked_youtube", true);

                            context.startActivity(viewTweet);
                        }
                    });

                    holder.image.setImageDrawable(null);

                    mHandlers[currHandler].removeCallbacksAndMessages(null);
                    mHandlers[currHandler].postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (holder.tweetId == id) {
                                ImageUtils.loadImage(context, holder.image, holder.picUrl, mCache);
                            }
                        }
                    }, 350);
                    currHandler++;

                    if (currHandler == 4) {
                        currHandler = 0;
                    }


                } else {
                    if (holder.playButton.getVisibility() == View.VISIBLE) {
                        holder.playButton.setVisibility(View.GONE);
                    }

                    holder.image.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            context.startActivity(new Intent(context, PhotoViewerDialog.class).putExtra("url", holder.picUrl));
                        }
                    });

                    holder.image.setImageDrawable(null);

                    mHandlers[currHandler].removeCallbacksAndMessages(null);
                    mHandlers[currHandler].postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (holder.tweetId == id) {
                                ImageUtils.loadImage(context, holder.image, holder.picUrl, mCache);
                            }
                        }
                    }, 350);
                    currHandler++;

                    if (currHandler == 4) {
                        currHandler = 0;
                    }
                }
            }
        }


        if (retweeter.length() > 0 && !isDM) {
            String text = context.getResources().getString(R.string.retweeter);
            //holder.retweeter.setText(settings.displayScreenName ? text + retweeter : text.substring(0, text.length() - 2) + " " + name);
            holder.retweeter.setText(text + retweeter);
            holder.retweeterName = retweeter;
            holder.retweeter.setVisibility(View.VISIBLE);
        } else if (holder.retweeter.getVisibility() == View.VISIBLE) {
            holder.retweeter.setVisibility(View.GONE);
        }

        if (settings.useEmoji && (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || EmojiUtils.ios)) {
            String text = holder.tweet.getText().toString();
            if (EmojiUtils.emojiPattern.matcher(text).find()) {
                emojiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (holder.tweetId == id) {
                            final Spannable span = EmojiUtils.getSmiledText(context, holder.tweet.getText());
                            holder.tweet.setText(span);
                        }
                    }
                }, 350);
            }
        }


    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (!cursor.moveToPosition(cursor.getCount() - 1 - position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }

        View v;
        if (convertView == null) {

            v = newView(context, cursor, parent);

        } else {
            v = convertView;

            final ViewHolder holder = (ViewHolder) v.getTag();

            holder.profilePic.setImageDrawable(context.getResources().getDrawable(border));
            holder.image.setVisibility(View.GONE);
        }

        bindView(v, context, cursor);

        return v;
    }

    public void removeExpansionWithAnimation(ViewHolder holder) {
        ExpansionAnimation expandAni = new ExpansionAnimation(holder.expandArea, 450);
        holder.expandArea.startAnimation(expandAni);
    }

    public void removeExpansionNoAnimation(ViewHolder holder) {
        ExpansionAnimation expandAni = new ExpansionAnimation(holder.expandArea, 10);
        holder.expandArea.startAnimation(expandAni);
    }

    public void addExpansion(final ViewHolder holder, String screenname, String users, final String[] otherLinks, final String webpage, final long tweetId) {
        if (isDM) {
            holder.retweet.setVisibility(View.GONE);
            holder.retweetCount.setVisibility(View.GONE);
            holder.favCount.setVisibility(View.GONE);
            holder.favorite.setVisibility(View.GONE);
        } else {
            holder.retweet.setVisibility(View.VISIBLE);
            holder.retweetCount.setVisibility(View.VISIBLE);
            holder.favCount.setVisibility(View.VISIBLE);
            holder.favorite.setVisibility(View.VISIBLE);
        }

        holder.replyButton.setVisibility(View.GONE);
        holder.charRemaining.setVisibility(View.GONE);

        holder.screenName = screenname;


        // used to find the other names on a tweet... could be optimized i guess, but only run when button is pressed
        if (!isDM) {
            String text = holder.tweet.getText().toString();
            String extraNames = "";

            if (text.contains("@")) {
                for (String s : users.split("  ")) {
                    if (!s.equals(settings.myScreenName) && !extraNames.contains(s) && !s.equals(screenname)) {
                        extraNames += "@" + s + " ";
                    }
                }
            }

            try {
                if (holder.retweeter.getVisibility() == View.VISIBLE && !extraNames.contains(holder.retweeterName)) {
                    extraNames += "@" + holder.retweeterName + " ";
                }
            } catch (NullPointerException e) {

            }

            if (!screenname.equals(settings.myScreenName)) {
                holder.reply.setText("@" + screenname + " " + extraNames);
            } else {
                holder.reply.setText(extraNames);
            }
        }

        holder.reply.setSelection(holder.reply.getText().length());

        if (holder.favCount.getText().toString().length() <= 2) {
            holder.favCount.setText("");
            holder.retweetCount.setText("");
        }

        ExpansionAnimation expandAni = new ExpansionAnimation(holder.expandArea, 450);
        holder.expandArea.startAnimation(expandAni);

        if (holder.favCount.getText().toString().equals("")) {
            getCounts(holder, tweetId);
        }

        holder.favorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new FavoriteStatus(holder, holder.tweetId).execute();
            }
        });

        holder.retweet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new RetweetStatus(holder, holder.tweetId).execute();
            }
        });

        holder.retweet.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                new AlertDialog.Builder(context)
                        .setTitle(context.getResources().getString(R.string.remove_retweet))
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                new RemoveRetweet(holder.tweetId).execute();
                            }
                        })
                        .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .create()
                        .show();
                return false;
            }

            class RemoveRetweet extends AsyncTask<String, Void, Boolean> {

                private long tweetId;

                public RemoveRetweet(long tweetId) {
                    this.tweetId = tweetId;
                }

                protected void onPreExecute() {
                    holder.retweet.clearColorFilter();

                    Toast.makeText(context, context.getResources().getString(R.string.removing_retweet), Toast.LENGTH_SHORT).show();
                }

                protected Boolean doInBackground(String... urls) {
                    try {
                        Twitter twitter =  Utils.getTwitter(context, settings);
                        ResponseList<twitter4j.Status> retweets = twitter.getRetweets(tweetId);
                        for (twitter4j.Status retweet : retweets) {
                            if(retweet.getUser().getId() == settings.myId)
                                twitter.destroyStatus(retweet.getId());
                        }
                        return true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }

                protected void onPostExecute(Boolean deleted) {
                    try {
                        if (deleted) {
                            Toast.makeText(context, context.getResources().getString(R.string.success), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, context.getResources().getString(R.string.error), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        // user has gone away from the window
                    }
                }
            }
        });

        holder.replyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new ReplyToStatus(holder, holder.tweetId).execute();
            }
        });

        holder.reply.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                hasKeyboard = b;
            }
        });

        holder.charRemaining.setText(140 - holder.reply.getText().length() + "");

        holder.reply.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                holder.charRemaining.setText(140 - holder.reply.getText().length() + "");
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        holder.reply.requestFocus();
        removeKeyboard(holder);
        holder.reply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeExpansionWithAnimation(holder);

                Intent compose = new Intent(context, ComposeActivity.class);
                String string = holder.reply.getText().toString();
                try{
                    compose.putExtra("user", string.substring(0, string.length() - 1));
                } catch (Exception e) {
                    
                }
                compose.putExtra("id", holder.tweetId);
                context.startActivity(compose);
            }
        });

        final String name = screenname;

        try {
            holder.shareButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent=new Intent(android.content.Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    String text = holder.tweet.getText().toString();

                    text = HtmlUtils.removeColorHtml(text, settings);
                    text = restoreLinks(text);

                    if (!settings.preferRT) {
                        text = "\"@" + name + ": " + text + "\" ";
                    } else {
                        text = " RT @" + name + ": " + text;
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                    intent.putExtra(Intent.EXTRA_TEXT, text);
                    context.startActivity(Intent.createChooser(intent, context.getResources().getString(R.string.menu_share)));
                }

                public String restoreLinks(String text) {
                    String full = text;

                    String[] split = text.split(" ");

                    boolean changed = false;

                    if (otherLinks.length > 0) {
                        for (int i = 0; i < split.length; i++) {
                            String s = split[i];

                            if (s.contains("http") && s.contains("...")) { // we know the link is cut off
                                String f = s.replace("...", "").replace("http", "");

                                for (int x = 0; x < otherLinks.length; x++) {
                                    Log.v("recreating_links", "other link first: " + otherLinks[x]);
                                    if (otherLinks[x].contains(f)) {
                                        changed = true;
                                        f = otherLinks[x];
                                        break;
                                    }
                                }

                                if (changed) {
                                    split[i] = f;
                                } else {
                                    split[i] = s;
                                }
                            } else {
                                split[i] = s;
                            }

                        }
                    }

                    Log.v("talon_picture", ":" + webpage + ":");

                    if (!webpage.equals("")) {
                        for (int i = 0; i < split.length; i++) {
                            String s = split[i];

                            Log.v("talon_picture_", s);

                            if (s.contains("http") && s.contains("...")) { // we know the link is cut off
                                split[i] = webpage;
                                changed = true;
                                Log.v("talon_picture", split[i]);
                            }
                        }
                    }



                    if(changed) {
                        full = "";
                        for (String p : split) {
                            full += p + " ";
                        }

                        full = full.substring(0, full.length() - 1);
                    }

                    return full;
                }
            });


            holder.quoteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent=new Intent(context, ComposeActivity.class);
                    intent.setType("text/plain");
                    String text = holder.tweet.getText().toString();

                    text = HtmlUtils.removeColorHtml(text, settings);
                    text = restoreLinks(text);

                    if (!settings.preferRT) {
                        text = "\"@" + name + ": " + text + "\" ";
                    } else {
                        text = " RT @" + name + ": " + text;
                    }
                    intent.putExtra("user", text);
                    context.startActivity(intent);
                }

                public String restoreLinks(String text) {
                    String full = text;

                    String[] split = text.split(" ");

                    boolean changed = false;

                    if (otherLinks.length > 0) {
                        for (int i = 0; i < split.length; i++) {
                            String s = split[i];

                            if (s.contains("http") && s.contains("...")) { // we know the link is cut off
                                String f = s.replace("...", "").replace("http", "");

                                for (int x = 0; x < otherLinks.length; x++) {
                                    Log.v("recreating_links", "other link first: " + otherLinks[x]);
                                    if (otherLinks[x].contains(f)) {
                                        changed = true;
                                        f = otherLinks[x];
                                        break;
                                    }
                                }

                                if (changed) {
                                    split[i] = f;
                                } else {
                                    split[i] = s;
                                }
                            } else {
                                split[i] = s;
                            }

                        }
                    }

                    Log.v("talon_picture", ":" + webpage + ":");

                    if (!webpage.equals("")) {
                        for (int i = 0; i < split.length; i++) {
                            String s = split[i];

                            Log.v("talon_picture_", s);

                            if (s.contains("http") && s.contains("...")) { // we know the link is cut off
                                split[i] = webpage;
                                changed = true;
                                Log.v("talon_picture", split[i]);
                            }
                        }
                    }



                    if(changed) {
                        full = "";
                        for (String p : split) {
                            full += p + " ";
                        }

                        full = full.substring(0, full.length() - 1);
                    }

                    return full;
                }
            });
        } catch (Exception e) {
            // theme made before these were implemented
        }
        if (settings.addonTheme) {
            try {
                Resources resourceAddon = context.getPackageManager().getResourcesForApplication(settings.addonThemePackage);
                int back = resourceAddon.getIdentifier("reply_entry_background", "drawable", settings.addonThemePackage);
                holder.reply.setBackgroundDrawable(resourceAddon.getDrawable(back));
            } catch (Exception e) {
                // theme does not include a reply entry box
            }
        }
    }

    public void removeKeyboard(ViewHolder holder) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(holder.reply.getWindowToken(), 0);
    }

    class DeleteTweet extends AsyncTask<String, Void, Boolean> {

        protected Boolean doInBackground(String... urls) {
            Twitter twitter = Utils.getTwitter(context, settings);

            try {
                long tweetId = Long.parseLong(urls[0]);

                DMDataSource source = new DMDataSource(context);
                source.open();
                source.deleteTweet(tweetId);
                source.close();

                twitter.destroyDirectMessage(tweetId);

                return true;
            } catch (TwitterException e) {
                e.printStackTrace();
                return false;
            }
        }

        protected void onPostExecute(Boolean deleted) {
            if (deleted) {
                Toast.makeText(context, context.getResources().getString(R.string.deleted_tweet), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, context.getResources().getString(R.string.error_deleting), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void getFavoriteCount(final ViewHolder holder, final long tweetId) {

        Thread getCount = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Twitter twitter =  Utils.getTwitter(context, settings);
                    final Status status;
                    if (holder.retweeter.getVisibility() != View.GONE) {
                        status = twitter.showStatus(holder.tweetId).getRetweetedStatus();
                    } else {
                        status = twitter.showStatus(tweetId);
                    }

                    if (status != null && holder.tweetId == tweetId) {
                        ((Activity)context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                holder.favCount.setText(" " + status.getFavoriteCount());

                                if (status.isFavorited()) {
                                    TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{R.attr.favoritedButton});
                                    int resource = a.getResourceId(0, 0);
                                    a.recycle();

                                    if (!settings.addonTheme) {
                                        holder.favorite.setColorFilter(context.getResources().getColor(R.color.app_color));
                                    } else {
                                        holder.favorite.setColorFilter(settings.accentInt);
                                    }

                                    holder.favorite.setImageDrawable(context.getResources().getDrawable(resource));
                                    holder.isFavorited = true;
                                } else {
                                    TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{R.attr.notFavoritedButton});
                                    int resource = a.getResourceId(0, 0);
                                    a.recycle();

                                    holder.favorite.setImageDrawable(context.getResources().getDrawable(resource));
                                    holder.isFavorited = false;

                                    holder.favorite.clearColorFilter();
                                }
                            }
                        });
                    }

                } catch (Exception e) {

                }
            }
        });

        getCount.setPriority(7);
        getCount.start();
    }

    public void getCounts(final ViewHolder holder, final long tweetId) {

        Thread getCount = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Twitter twitter =  Utils.getTwitter(context, settings);
                    final Status status;
                    if (holder.retweeter.getVisibility() != View.GONE) {
                        status = twitter.showStatus(holder.tweetId).getRetweetedStatus();
                    } else {
                        status = twitter.showStatus(tweetId);
                    }

                    if (status != null && holder.tweetId == tweetId) {
                        ((Activity)context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                holder.favCount.setText(" " + status.getFavoriteCount());
                                holder.retweetCount.setText(" " + status.getRetweetCount());

                                if (status.isFavorited()) {
                                    TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{R.attr.favoritedButton});
                                    int resource = a.getResourceId(0, 0);
                                    a.recycle();

                                    if (!settings.addonTheme) {
                                        holder.favorite.setColorFilter(context.getResources().getColor(R.color.app_color));
                                    } else {
                                        holder.favorite.setColorFilter(settings.accentInt);
                                    }

                                    holder.favorite.setImageDrawable(context.getResources().getDrawable(resource));
                                    holder.isFavorited = true;
                                } else {
                                    TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{R.attr.notFavoritedButton});
                                    int resource = a.getResourceId(0, 0);
                                    a.recycle();

                                    holder.favorite.setImageDrawable(context.getResources().getDrawable(resource));
                                    holder.isFavorited = false;

                                    holder.favorite.clearColorFilter();
                                }

                                if (status.isRetweetedByMe()) {
                                    if (!settings.addonTheme) {
                                        holder.retweet.setColorFilter(context.getResources().getColor(R.color.app_color));
                                    } else {
                                        holder.retweet.setColorFilter(settings.accentInt);
                                    }
                                } else {
                                    holder.retweet.clearColorFilter();
                                }
                            }
                        });
                    }

                } catch (Exception e) {

                }
            }
        });

        getCount.setPriority(7);
        getCount.start();
    }

    public void getRetweetCount(final ViewHolder holder, final long tweetId) {

        Thread getRetweetCount = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Twitter twitter =  Utils.getTwitter(context, settings);
                    twitter4j.Status status = twitter.showStatus(holder.tweetId);
                    final boolean retweetedByMe = status.isRetweetedByMe();
                    final String count = "" + status.getRetweetCount();
                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (tweetId == holder.tweetId) {
                                if (retweetedByMe) {
                                    if (!settings.addonTheme) {
                                        holder.retweet.setColorFilter(context.getResources().getColor(R.color.app_color));
                                    } else {
                                        holder.retweet.setColorFilter(settings.accentInt);
                                    }
                                } else {
                                    holder.retweet.clearColorFilter();
                                }
                                if (count != null) {
                                    holder.retweetCount.setText(" " + count);
                                }
                            }
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        getRetweetCount.setPriority(7);
        getRetweetCount.start();
    }

    class FavoriteStatus extends AsyncTask<String, Void, String> {

        private ViewHolder holder;
        private long tweetId;

        public FavoriteStatus(ViewHolder holder, long tweetId) {
            this.holder = holder;
            this.tweetId = tweetId;
        }

        protected void onPreExecute() {
            if (!holder.isFavorited) {
                Toast.makeText(context, context.getResources().getString(R.string.favoriting_status), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, context.getResources().getString(R.string.removing_favorite), Toast.LENGTH_SHORT).show();
            }
        }

        protected String doInBackground(String... urls) {
            try {
                Twitter twitter =  Utils.getTwitter(context, settings);
                if (holder.isFavorited) {
                    twitter.destroyFavorite(tweetId);
                } else {
                    twitter.createFavorite(tweetId);
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        protected void onPostExecute(String count) {
            Toast.makeText(context, context.getResources().getString(R.string.success), Toast.LENGTH_SHORT).show();
            getFavoriteCount(holder, tweetId);
        }
    }

    class RetweetStatus extends AsyncTask<String, Void, String> {

        private ViewHolder holder;
        private long tweetId;

        public RetweetStatus(ViewHolder holder, long tweetId) {
            this.holder = holder;
            this.tweetId = tweetId;
        }

        protected void onPreExecute() {
            Toast.makeText(context, context.getResources().getString(R.string.retweeting_status), Toast.LENGTH_SHORT).show();
        }

        protected String doInBackground(String... urls) {
            try {
                Twitter twitter =  Utils.getTwitter(context, settings);
                twitter.retweetStatus(tweetId);
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        protected void onPostExecute(String count) {
            Toast.makeText(context, context.getResources().getString(R.string.retweet_success), Toast.LENGTH_SHORT).show();
            getRetweetCount(holder, tweetId);
        }
    }

    class ReplyToStatus extends AsyncTask<String, Void, Boolean> {

        private ViewHolder holder;
        private long tweetId;
        private boolean dontgo = false;

        public ReplyToStatus(ViewHolder holder, long tweetId) {
            this.holder = holder;
            this.tweetId = tweetId;
        }

        protected void onPreExecute() {
            if (Integer.parseInt(holder.charRemaining.getText().toString()) >= 0) {
                removeExpansionWithAnimation(holder);
                removeKeyboard(holder);
            } else {
                dontgo = true;
            }
        }

        protected Boolean doInBackground(String... urls) {
            try {
                if (!dontgo) {
                    Twitter twitter =  Utils.getTwitter(context, settings);

                    if (!isDM) {
                        twitter4j.StatusUpdate reply = new twitter4j.StatusUpdate(holder.reply.getText().toString());
                        reply.setInReplyToStatusId(tweetId);

                        twitter.updateStatus(reply);
                    } else {
                        String screenName = holder.screenName;
                        String message = holder.reply.getText().toString();
                        DirectMessage dm = twitter.sendDirectMessage(screenName, message);
                    }


                    return true;
                }
            } catch (Exception e) {

            }

            return false;
        }

        protected void onPostExecute(Boolean finished) {
             if (finished) {
                 Toast.makeText(context, context.getResources().getString(R.string.tweet_success), Toast.LENGTH_SHORT).show();
             } else {
                 if (dontgo) {
                     Toast.makeText(context, context.getResources().getString(R.string.tweet_to_long), Toast.LENGTH_SHORT).show();
                 } else {
                     Toast.makeText(context, context.getResources().getString(R.string.error_sending_tweet), Toast.LENGTH_SHORT).show();
                 }
             }
        }
    }

    class GetImage extends AsyncTask<String, Void, String> {

        private ViewHolder holder;
        private long tweetId;

        public GetImage(ViewHolder holder, long tweetId) {
            this.holder = holder;
            this.tweetId = tweetId;
        }

        protected String doInBackground(String... urls) {
            try {
                Twitter twitter =  Utils.getTwitter(context, settings);
                twitter4j.Status status = twitter.showStatus(tweetId);

                MediaEntity[] entities = status.getMediaEntities();



                return entities[0].getMediaURL();
            } catch (Exception e) {
                return null;
            }
        }

        protected void onPostExecute(String url) {

        }
    }

    static class UpdateTextViewListener
            implements NetworkedCacheableImageView.OnImageLoadedListener {

        @Override
        public void onImageLoaded(CacheableBitmapDrawable result) {

        }
    }
}
