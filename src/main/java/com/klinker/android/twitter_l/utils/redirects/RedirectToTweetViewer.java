package com.klinker.android.twitter_l.utils.redirects;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.klinker.android.twitter_l.ui.tweet_viewer.TweetActivity;

public class RedirectToTweetViewer extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent tweet = new Intent(this, TweetActivity.class);
        tweet.putExtras(getIntent());
        tweet.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        finish();
        overridePendingTransition(0,0);

        startActivity(tweet);
    }
}