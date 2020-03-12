package org.securityed.securesms.notifications;


import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

import org.securityed.securesms.ConversationListActivity;
import org.securityed.securesms.R;
import org.securityed.securesms.database.RecipientDatabase;
import org.securityed.securesms.preferences.widgets.NotificationPrivacyPreference;
import org.securityed.securesms.util.TextSecurePreferences;

public class PendingMessageNotificationBuilder extends AbstractNotificationBuilder {

  public PendingMessageNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context, privacy);

    Intent intent = new Intent(context, ConversationListActivity.class);

    setSmallIcon(R.drawable.icon_notification);
    setColor(context.getResources().getColor(R.color.textsecure_primary )); //R.color.umd_red
    setCategory(NotificationCompat.CATEGORY_MESSAGE);

    setContentTitle(context.getString(R.string.MessageNotifier_you_may_have_new_messages));
    setContentText(context.getString(R.string.MessageNotifier_open_signal_to_check_for_recent_notifications));
    setTicker(context.getString(R.string.MessageNotifier_open_signal_to_check_for_recent_notifications));

    setContentIntent(PendingIntent.getActivity(context, 0, intent, 0));
    setAutoCancel(true);
    setAlarms(null, RecipientDatabase.VibrateState.DEFAULT);

    setOnlyAlertOnce(true);

    if (!NotificationChannels.supported()) {
      setPriority(TextSecurePreferences.getNotificationPriority(context));
    }
  }
}
