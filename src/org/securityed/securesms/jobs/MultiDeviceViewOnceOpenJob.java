package org.securityed.securesms.jobs;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.securityed.securesms.crypto.UnidentifiedAccessUtil;
import org.securityed.securesms.database.MessagingDatabase.SyncMessageId;
import org.securityed.securesms.dependencies.ApplicationDependencies;
import org.securityed.securesms.jobmanager.Data;
import org.securityed.securesms.jobmanager.Job;
import org.securityed.securesms.jobmanager.impl.NetworkConstraint;
import org.securityed.securesms.logging.Log;
import org.securityed.securesms.recipients.Recipient;
import org.securityed.securesms.recipients.RecipientId;
import org.securityed.securesms.util.JsonUtils;
import org.securityed.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class MultiDeviceViewOnceOpenJob extends BaseJob {

  public static final String KEY = "MultiDeviceRevealUpdateJob";

  private static final String TAG = Log.tag(MultiDeviceViewOnceOpenJob.class);

  private static final String KEY_MESSAGE_ID = "message_id";

  private SerializableSyncMessageId messageId;

  public MultiDeviceViewOnceOpenJob(SyncMessageId messageId) {
    this(new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
         messageId);
  }

  private MultiDeviceViewOnceOpenJob(@NonNull Parameters parameters, @NonNull SyncMessageId syncMessageId) {
    super(parameters);
    this.messageId = new SerializableSyncMessageId(syncMessageId.getRecipientId().serialize(), syncMessageId.getTimetamp());
  }

  @Override
  public @NonNull Data serialize() {
    String serialized;

    try {
      serialized = JsonUtils.toJson(messageId);
    } catch (IOException e) {
      throw new AssertionError(e);
    }

    return new Data.Builder().putString(KEY_MESSAGE_ID, serialized).build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device...");
      return;
    }

    SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
    Recipient                  recipient     = Recipient.resolved(RecipientId.from(messageId.recipientId));
    ViewOnceOpenMessage        openMessage   = new ViewOnceOpenMessage(recipient.requireAddress().serialize(), messageId.timestamp);

    messageSender.sendMessage(SignalServiceSyncMessage.forViewOnceOpen(openMessage), UnidentifiedAccessUtil.getAccessForSync(context));
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {

  }

  private static class SerializableSyncMessageId implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty
    private final String recipientId;

    @JsonProperty
    private final long   timestamp;

    private SerializableSyncMessageId(@JsonProperty("recipientId") String recipientId, @JsonProperty("timestamp") long timestamp) {
      this.recipientId = recipientId;
      this.timestamp   = timestamp;
    }
  }

  public static final class Factory implements Job.Factory<MultiDeviceViewOnceOpenJob> {
    @Override
    public @NonNull MultiDeviceViewOnceOpenJob create(@NonNull Parameters parameters, @NonNull Data data) {
      SerializableSyncMessageId messageId;

      try {
        messageId = JsonUtils.fromJson(data.getString(KEY_MESSAGE_ID), SerializableSyncMessageId.class);
      } catch (IOException e) {
        throw new AssertionError(e);
      }

      SyncMessageId syncMessageId = new SyncMessageId(RecipientId.from(messageId.recipientId), messageId.timestamp);

      return new MultiDeviceViewOnceOpenJob(parameters, syncMessageId);
    }
  }
}
