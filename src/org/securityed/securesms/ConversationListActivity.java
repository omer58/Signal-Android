/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.securityed.securesms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.TooltipCompat;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.securityed.securesms.color.MaterialColor;
import org.securityed.securesms.components.RatingManager;
import org.securityed.securesms.components.SearchToolbar;
import org.securityed.securesms.components.TooltipPopup;
import org.securityed.securesms.contacts.avatars.ContactColors;
import org.securityed.securesms.contacts.avatars.GeneratedContactPhoto;
import org.securityed.securesms.contacts.avatars.ProfileContactPhoto;
import org.securityed.securesms.conversation.ConversationActivity;
import org.securityed.securesms.database.Address;
import org.securityed.securesms.database.DatabaseFactory;
import org.securityed.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.securityed.securesms.lock.RegistrationLockDialog;
import org.securityed.securesms.mms.GlideApp;
import org.securityed.securesms.notifications.MarkReadReceiver;
import org.securityed.securesms.notifications.MessageNotifier;
import org.securityed.securesms.permissions.Permissions;
import org.securityed.securesms.recipients.Recipient;
import org.securityed.securesms.search.SearchFragment;
import org.securityed.securesms.service.KeyCachingService;
import org.securityed.securesms.util.DynamicLanguage;
import org.securityed.securesms.util.DynamicNoActionBarTheme;
import org.securityed.securesms.util.DynamicTheme;
import org.securityed.securesms.util.TextSecurePreferences;
import org.securityed.securesms.util.concurrent.SimpleTask;
import org.securityed.securesms.education.*;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

public class ConversationListActivity extends PassphraseRequiredActionBarActivity
    implements ConversationListFragment.ConversationSelectedListener
{
  @SuppressWarnings("unused")
  private static final String TAG = ConversationListActivity.class.getSimpleName();

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ConversationListFragment conversationListFragment;
  private SearchFragment           searchFragment;
  private SearchToolbar            searchToolbar;
  private ImageView                searchAction;
  private ViewGroup                fragmentContainer;

  private Calendar launchTime;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @SuppressLint("StaticFieldLeak")
  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    setContentView(R.layout.conversation_list_activity);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    searchToolbar            = findViewById(R.id.search_toolbar);
    //searchAction             = findViewById(R.id.search_action);
    fragmentContainer        = findViewById(R.id.fragment_container);
    conversationListFragment = initFragment(R.id.fragment_container, new ConversationListFragment(), dynamicLanguage.getCurrentLocale());

    initializeSearchListener();

    //RatingManager.showRatingDialogIfNecessary(this);
    RegistrationLockDialog.showReminderIfNecessary(this);

    //TooltipCompat.setTooltipText(searchAction, getText(R.string.SearchToolbar_search_for_conversations_contacts_and_messages));

    Context c = this;

    try{
      new AsyncTask<Recipient, Void, Void>() {
        @Override
        protected Void doInBackground(Recipient... params) {
          SystemClock.sleep(1000);
          synchronized (SESSION_LOCK) {
            if(EducationalMessageManager.isTimeForShortMessage(c, EducationalMessageManager.TOOL_TIP_MESSAGE)){

              Calendar time = GregorianCalendar.getInstance();
              EducationalMessage educationalMessage = EducationalMessageManager.getShortMessage(c);

              final Address localAddress = Address.fromSerialized(TextSecurePreferences.getLocalNumber(c));
              localAddress.toPhoneString();

              Log.d("ConversationList", "tooltip init.");

              TooltipPopup.Builder ttpb = TooltipPopup.forTarget(searchToolbar)
                      .setBackgroundTint(getResources().getColor(R.color.core_blue))
                      .setTextColor(getResources().getColor(R.color.core_white))
                      .setText( R.string.dummy)// educationalMessage.getStringID())
                      .setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                  long timePassedInMs = GregorianCalendar.getInstance().getTimeInMillis()-time.getTimeInMillis();

                  EducationalMessageManager.notifyStatServer(c, EducationalMessageManager.MESSAGE_SHOWN,
                          EducationalMessageManager.getMessageShownLogEntry( TextSecurePreferences.getLocalNumber(c),"conversationList",
                                  EducationalMessageManager.TOOL_TIP_MESSAGE, educationalMessage.getMessageName(), time.getTime(), timePassedInMs ));

                }
              });

              runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  TooltipPopup tooltip = ttpb.show(TooltipPopup.POSITION_MIDDLE);
                  Log.d("after show: ", "" + tooltip.getContentView().getWidth());

                }
              });


            }
          }
          return null;
        }
      };//.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR); this disables the blue tooltip.


    } catch ( RuntimeException e){

      Log.d("exception", "couldn't send tooltip");

    }




  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);

    SimpleTask.run(getLifecycle(), Recipient::self, this::initializeProfileIcon);

    launchTime = GregorianCalendar.getInstance();

  }

  @Override
  public void onPause(){
    super.onPause();

    Long timeElapsed = GregorianCalendar.getInstance().getTimeInMillis() - launchTime.getTimeInMillis();

    if( TextSecurePreferences.getWasTooltipShown(this) && !TextSecurePreferences.getWasTooltipDismissed(this)){

      //save timeElapsed

      Log.d("additional time spent", "t:" + timeElapsed);

      TextSecurePreferences.addToTotalTooltipTime(this, timeElapsed);
    }


  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.text_secure_normal, menu);

    menu.findItem(R.id.menu_invite).setVisible(false); // we don't need no new invites!
    menu.findItem(R.id.menu_clear_passphrase).setVisible(!TextSecurePreferences.isPasswordDisabled(this));

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  private void initializeSearchListener() {
    /*searchAction.setOnClickListener(v -> {
      Permissions.with(this)
                 .request(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                 .ifNecessary()
                 .onAllGranted(() -> searchToolbar.display(searchAction.getX() + (searchAction.getWidth() / 2),
                                                           searchAction.getY() + (searchAction.getHeight() / 2)))
                 .withPermanentDenialDialog(getString(R.string.ConversationListActivity_signal_needs_contacts_permission_in_order_to_search_your_contacts_but_it_has_been_permanently_denied))
                 .execute();
    });*/

    searchToolbar.setListener(new SearchToolbar.SearchListener() {
      @Override
      public void onSearchTextChange(String text) {
        String trimmed = text.trim();

        if (trimmed.length() > 0) {
          if (searchFragment == null) {
            searchFragment = SearchFragment.newInstance(dynamicLanguage.getCurrentLocale());
            getSupportFragmentManager().beginTransaction()
                                       .add(R.id.fragment_container, searchFragment, null)
                                       .commit();
          }
          searchFragment.updateSearchQuery(trimmed);
        } else if (searchFragment != null) {
          getSupportFragmentManager().beginTransaction()
                                     .remove(searchFragment)
                                     .commit();
          searchFragment = null;
        }
      }

      @Override
      public void onSearchClosed() {
        if (searchFragment != null) {
          getSupportFragmentManager().beginTransaction()
                                     .remove(searchFragment)
                                     .commit();
          searchFragment = null;
        }
      }
    });
  }

  private void initializeProfileIcon(@NonNull Recipient recipient) {
    ImageView     icon          = findViewById(R.id.toolbar_icon);
    String        name          = Optional.fromNullable(recipient.getName()).or(Optional.fromNullable(TextSecurePreferences.getProfileName(this))).or("");
    MaterialColor fallbackColor = recipient.getColor();

    if (fallbackColor == ContactColors.UNKNOWN_COLOR && !TextUtils.isEmpty(name)) {
      fallbackColor = ContactColors.generateFor(name);
    }

    Drawable fallback = new GeneratedContactPhoto(name, R.drawable.ic_profile_default).asDrawable(this, fallbackColor.toAvatarColor(this));

    GlideApp.with(this)
            .load(new ProfileContactPhoto(recipient.requireAddress(), String.valueOf(TextSecurePreferences.getProfileAvatarId(this))))
            .error(fallback)
            .circleCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(icon);

    icon.setImageResource(R.drawable.ic_profile_default);

    String avatarId = String.valueOf(TextSecurePreferences.getProfileAvatarId(this));

    Log.d("init profile pic id", avatarId);

    icon.setOnClickListener(v -> handleDisplaySettings());
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case R.id.menu_new_group:         createGroup();           return true;
    case R.id.menu_settings:          handleDisplaySettings(); return true;
    case R.id.menu_clear_passphrase:  handleClearPassphrase(); return true;
    case R.id.menu_mark_all_read:     handleMarkAllRead();     return true;
    case R.id.menu_invite:            handleInvite();          return true;
    case R.id.menu_help:              handleHelp();            return true;
    }

    return false;
  }

  @Override
  public void onCreateConversation(long threadId, Recipient recipient, int distributionType, long lastSeen) {
    openConversation(threadId, recipient, distributionType, lastSeen, -1);
  }

  public void openConversation(long threadId, Recipient recipient, int distributionType, long lastSeen, int startingPosition) {
    searchToolbar.clearFocus();

    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENT_EXTRA, recipient.getId());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);
    intent.putExtra(ConversationActivity.TIMING_EXTRA, System.currentTimeMillis());
    intent.putExtra(ConversationActivity.LAST_SEEN_EXTRA, lastSeen);
    intent.putExtra(ConversationActivity.STARTING_POSITION_EXTRA, startingPosition);

    startActivity(intent);
    overridePendingTransition(R.anim.slide_from_end, R.anim.fade_scale_out);
  }

  @Override
  public void onSwitchToArchive() {
    Intent intent = new Intent(this, ConversationListArchiveActivity.class);
    startActivity(intent);
  }

  @Override
  public void onBackPressed() {
    if (searchToolbar.isVisible()) searchToolbar.collapse();
    else                           super.onBackPressed();
  }

  private void createGroup() {
    Intent intent = new Intent(this, GroupCreateActivity.class);
    startActivity(intent);
  }

  private void handleDisplaySettings() {
    Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
    startActivity(preferencesIntent);
  }

  private void handleClearPassphrase() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    startService(intent);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleMarkAllRead() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        Context                 context    = ConversationListActivity.this;
        List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setAllThreadsRead();

        MessageNotifier.updateNotification(context);
        MarkReadReceiver.process(context, messageIds);

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleInvite() {
    startActivity(new Intent(this, InviteActivity.class));
  }

  private void handleHelp() {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://support.signal.org")));
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, R.string.ConversationListActivity_there_is_no_browser_installed_on_your_device, Toast.LENGTH_LONG).show();
    }
  }
}
