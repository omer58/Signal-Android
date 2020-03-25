package org.securityed.securesms.conversation;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.securityed.securesms.BindableConversationItem;
import org.securityed.securesms.R;
import org.securityed.securesms.VerifyIdentityActivity;
import org.securityed.securesms.crypto.IdentityKeyParcelable;
import org.securityed.securesms.database.IdentityDatabase;
import org.securityed.securesms.database.IdentityDatabase.IdentityRecord;
import org.securityed.securesms.database.model.MessageRecord;
import org.securityed.securesms.education.EducationalMessage;
import org.securityed.securesms.education.EducationalMessageManager;
import org.securityed.securesms.education.LongEducationalMessageActivity;
import org.securityed.securesms.logging.Log;
import org.securityed.securesms.mms.GlideRequests;
import org.securityed.securesms.recipients.LiveRecipient;
import org.securityed.securesms.recipients.Recipient;
import org.securityed.securesms.recipients.RecipientForeverObserver;
import org.securityed.securesms.util.DateUtils;
import org.securityed.securesms.util.ExpirationUtil;
import org.securityed.securesms.util.GroupUtil;
import org.securityed.securesms.util.IdentityUtil;
import org.securityed.securesms.util.TextSecurePreferences;
import org.securityed.securesms.util.concurrent.ListenableFuture;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class ConversationUpdateItem extends LinearLayout
    implements RecipientForeverObserver, BindableConversationItem
{
  private static final String TAG = ConversationUpdateItem.class.getSimpleName();

  private Set<MessageRecord> batchSelected;

  private ImageView     icon;
  private TextView      title;
  private TextView      body;
  private TextView      date;
  private LiveRecipient sender;
  private MessageRecord messageRecord;
  private Locale        locale;

  public ConversationUpdateItem(Context context) {
    super(context);
  }

  public ConversationUpdateItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.icon  = findViewById(R.id.conversation_update_icon);
    this.title = findViewById(R.id.conversation_update_title);
    this.body  = findViewById(R.id.conversation_update_body);
    this.date  = findViewById(R.id.conversation_update_date);

    this.setOnClickListener(new InternalClickListener(null));
  }

  @Override
  public void bind(@NonNull MessageRecord           messageRecord,
                   @NonNull Optional<MessageRecord> previousMessageRecord,
                   @NonNull Optional<MessageRecord> nextMessageRecord,
                   @NonNull GlideRequests           glideRequests,
                   @NonNull Locale                  locale,
                   @NonNull Set<MessageRecord>      batchSelected,
                   @NonNull Recipient               conversationRecipient,
                   @Nullable String                 searchQuery,
                            boolean                 pulseUpdate)
  {
    this.batchSelected = batchSelected;

    bind(messageRecord, locale);
  }

  @Override
  public void setEventListener(@Nullable EventListener listener) {
    // No events to report yet
  }

  @Override
  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  private void bind(@NonNull MessageRecord messageRecord, @NonNull Locale locale) {
    if (this.sender != null) {
      this.sender.removeForeverObserver(this);
    }

    if (this.messageRecord != null && messageRecord.isGroupAction()) {
      GroupUtil.getDescription(getContext(), messageRecord.getBody()).removeObserver(this);
    }

    this.messageRecord = messageRecord;
    this.sender        = messageRecord.getIndividualRecipient().live();
    this.locale        = locale;

    this.sender.observeForever(this);

    if (this.messageRecord != null && messageRecord.isGroupAction()) {
      GroupUtil.getDescription(getContext(), messageRecord.getBody()).addObserver(this);
    }

    present(messageRecord);
  }

  private void present(MessageRecord messageRecord) {
    if      (messageRecord.isGroupAction())           setGroupRecord(messageRecord);
    else if (messageRecord.isCallLog())               setCallRecord(messageRecord);
    else if (messageRecord.isJoined())                setJoinedRecord(messageRecord);
    else if (messageRecord.isExpirationTimerUpdate()) setTimerRecord(messageRecord);
    else if (messageRecord.isEndSession())            setEndSessionRecord(messageRecord);
    else if (messageRecord.isIdentityUpdate())        setIdentityRecord(messageRecord);
    else if (messageRecord.isEducationalMessage())    setEducationalRecord(messageRecord);
    else if (messageRecord.isIdentityVerified() ||
             messageRecord.isIdentityDefault())       setIdentityVerifyUpdate(messageRecord); //this does block the view from showing if commented out.
    else                                              throw new AssertionError("Neither group nor log nor joined.");

    if (batchSelected.contains(messageRecord)) setSelected(true);
    else                                       setSelected(false);

    if( messageRecord.isEducationalMessage()){
      Log.d("conversation update item", "is called!!!!!!!!!!!");

      TextSecurePreferences.incrementTimesSeen(getContext(), messageRecord.getTimestamp());
    }

  }

  private void setCallRecord(MessageRecord messageRecord) {
    if      (messageRecord.isIncomingCall()) icon.setImageResource(R.drawable.ic_call_received_grey600_24dp);
    else if (messageRecord.isOutgoingCall()) icon.setImageResource(R.drawable.ic_call_made_grey600_24dp);
    else                                     icon.setImageResource(R.drawable.ic_call_missed_grey600_24dp);

    body.setText(messageRecord.getDisplayBody(getContext()));
    date.setText(DateUtils.getExtendedRelativeTimeSpanString(getContext(), locale, messageRecord.getDateReceived()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(View.VISIBLE);
  }

  private void setTimerRecord(final MessageRecord messageRecord) {
    if (messageRecord.getExpiresIn() > 0) {
      icon.setImageResource(R.drawable.ic_timer);
      icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    } else {
      icon.setImageResource(R.drawable.ic_timer_disabled);
      icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    }

    title.setText(ExpirationUtil.getExpirationDisplayValue(getContext(), (int)(messageRecord.getExpiresIn() / 1000)));
    body.setText(messageRecord.getDisplayBody(getContext()));

    title.setVisibility(VISIBLE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }

  private void setIdentityRecord(final MessageRecord messageRecord) {
    icon.setImageResource(R.drawable.ic_security_white_24dp);
    icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    body.setText(messageRecord.getDisplayBody(getContext()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }

  private void setIdentityVerifyUpdate(final MessageRecord messageRecord) {
    if (messageRecord.isIdentityVerified()) icon.setImageResource(R.drawable.ic_check_white_24dp);
    else                                    icon.setImageResource(R.drawable.ic_info_outline_white_24dp);

    icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    body.setText( messageRecord.getDisplayBody(getContext()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }

  private void setGroupRecord(MessageRecord messageRecord) {
    icon.setImageResource(R.drawable.ic_group_grey600_24dp);
    icon.clearColorFilter();

    body.setText(messageRecord.getDisplayBody(getContext()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }

  private void setJoinedRecord(MessageRecord messageRecord) {
    icon.setImageResource(R.drawable.ic_favorite_grey600_24dp);
    icon.clearColorFilter();
    body.setText(messageRecord.getDisplayBody(getContext()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }

  private void setEndSessionRecord(MessageRecord messageRecord) {
    icon.setImageResource(R.drawable.ic_refresh_white_24dp);
    icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    body.setText( messageRecord.getDisplayBody(getContext()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }

  // Omer added this. Handles the educational messages
  private void setEducationalRecord(MessageRecord messageRecord){

    icon.setImageResource(R.drawable.ic_check_white_24dp);

    icon.setColorFilter(new PorterDuffColorFilter(Color.parseColor("#757575"), PorterDuff.Mode.MULTIPLY));
    CharSequence learnMore = (Html.fromHtml("<b> Tap here to learn more.</b>"));
    CharSequence shortMessage = TextUtils.concat(messageRecord.getDisplayBody(getContext()), "\n", learnMore);
    body.setText( shortMessage);
    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);


  }
  
  @Override
  public void onRecipientChanged(@NonNull Recipient recipient) {
    present(messageRecord);
  }

  @Override
  public void setOnClickListener(View.OnClickListener l) {
    super.setOnClickListener(new InternalClickListener(l));
  }

  @Override
  public void unbind() {
    if (sender != null) {
      sender.removeForeverObserver(this);
    }
    if (this.messageRecord != null && messageRecord.isGroupAction()) {
      GroupUtil.getDescription(getContext(), messageRecord.getBody()).removeObserver(this);
    }
  }

  private class InternalClickListener implements View.OnClickListener {

    @Nullable private final View.OnClickListener parent;

    InternalClickListener(@Nullable View.OnClickListener parent) {
      this.parent = parent;
    }

    @Override
    public void onClick(View v) {
      if ( messageRecord.isEducationalMessage()){


        //String phoneNumber, String screen, int messageType, String version, Date date, long timeElapsed
        Long sentDate = messageRecord.getTimestamp();
        Long now = GregorianCalendar.getInstance().getTimeInMillis();
        EducationalMessage em = EducationalMessageManager.getEducationalMessageFromBody(getContext(), messageRecord.getBody());
        EducationalMessageManager.notifyStatServer(getContext(), EducationalMessageManager.MESSAGE_SHOWN,
                EducationalMessageManager.getMessageShownLogEntry(TextSecurePreferences.getLocalNumber(getContext()), "conversation_to_long", EducationalMessageManager.IN_CONVERSATION_MESSAGE, em.getMessageName(), new Date( messageRecord.getTimestamp()), now-sentDate) + "_" + TextSecurePreferences.getTimesSeen(getContext(), messageRecord.getTimestamp()));


        Intent educationIntent = new Intent(getContext(), LongEducationalMessageActivity.class);
        getContext().startActivity(educationIntent);
      }


      if ((!messageRecord.isIdentityUpdate()  &&
           !messageRecord.isIdentityDefault() &&
           !messageRecord.isIdentityVerified()) ||
          !batchSelected.isEmpty())
      {
        if (parent != null) parent.onClick(v);
        return;
      }

      final Recipient sender = ConversationUpdateItem.this.sender.get();

      IdentityUtil.getRemoteIdentityKey(getContext(), sender).addListener(new ListenableFuture.Listener<Optional<IdentityRecord>>() {
        @Override
        public void onSuccess(Optional<IdentityRecord> result) {
          if (result.isPresent()) {
            Intent intent = new Intent(getContext(), VerifyIdentityActivity.class);
            intent.putExtra(VerifyIdentityActivity.RECIPIENT_EXTRA, sender.getId());
            intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(result.get().getIdentityKey()));
            intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, result.get().getVerifiedStatus() == IdentityDatabase.VerifiedStatus.VERIFIED);

            getContext().startActivity(intent);
          }
        }

        @Override
        public void onFailure(ExecutionException e) {
          Log.w(TAG, e);
        }
      });
    }
  }

}
