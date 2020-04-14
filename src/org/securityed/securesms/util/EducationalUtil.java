package org.securityed.securesms.util;


import android.content.Context;
import android.os.AsyncTask;

import org.securityed.securesms.R;
import org.securityed.securesms.database.DatabaseFactory;
import org.securityed.securesms.database.GroupDatabase;
import org.securityed.securesms.database.IdentityDatabase.IdentityRecord;
import org.securityed.securesms.database.SmsDatabase;
import org.securityed.securesms.education.EducationalMessage;
import org.securityed.securesms.education.EducationalMessageManager;
import org.securityed.securesms.logging.Log;
import org.securityed.securesms.recipients.Recipient;
import org.securityed.securesms.sms.OutgoingEducationalMessage;
import org.securityed.securesms.sms.OutgoingTextMessage;
import org.securityed.securesms.util.concurrent.ListenableFuture;
import org.securityed.securesms.util.concurrent.SettableFuture;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class EducationalUtil {

    private static final String TAG = IdentityUtil.class.getSimpleName();

    public static ListenableFuture<Optional<IdentityRecord>> getRemoteIdentityKey(final Context context, final Recipient recipient) {
        final SettableFuture<Optional<IdentityRecord>> future = new SettableFuture<>();

        new AsyncTask<Recipient, Void, Optional<IdentityRecord>>() {
            @Override
            protected Optional<IdentityRecord> doInBackground(Recipient... recipient) {
                return DatabaseFactory.getIdentityDatabase(context)
                        .getIdentity(recipient[0].getId());
            }

            @Override
            protected void onPostExecute(Optional<IdentityRecord> result) {
                future.set(result);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient);

        return future;
    }

    //gets it's own message from EducationalMessageManager and sends it.
    public static void sendEducationalMessage(Context context, Recipient recipient, boolean verified, boolean remote)
    {
        long                 time          = System.currentTimeMillis();
        SmsDatabase          smsDatabase   = DatabaseFactory.getSmsDatabase(context);
        GroupDatabase        groupDatabase = DatabaseFactory.getGroupDatabase(context);
        GroupDatabase.Reader reader        = groupDatabase.getGroups();

        GroupDatabase.GroupRecord groupRecord;

        boolean isChecked = false;

        OutgoingTextMessage outgoing;

        //if (true) outgoing = new OutgoingIdentityVerifiedMessage(getRecipient());
        //else          outgoing = new OutgoingIdentityDefaultMessage(getRecipient());

        EducationalMessage em = EducationalMessageManager.getShortMessage(context);

        outgoing = new OutgoingEducationalMessage(recipient);
        outgoing = outgoing.withBody(em.getMessageString(context));

        long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

        Log.i(TAG, "Inserting verified outbox...");
        DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(threadId, outgoing, false, time, null, false);
        Calendar c = GregorianCalendar.getInstance();
        EducationalMessageManager.notifyStatServer(context, EducationalMessageManager.MESSAGE_SHOWN, EducationalMessageManager.getMessageShownLogEntry( TextSecurePreferences.getLocalNumber(context), "inConversation", EducationalMessageManager.IN_CONVERSATION_MESSAGE,
                em.getMessageName(), c.getTime(), -1));

        TextSecurePreferences.setLastMessageShownTime( context, c.getTime().getTime());


        /*

        // this loop deals with groups the the recipient is in... I think
        while ((groupRecord = reader.getNext()) != null) {

            if (groupRecord.getMembers().contains(recipient.getId()) && groupRecord.isActive() && !groupRecord.isMms()) {
                SignalServiceGroup group = new SignalServiceGroup(groupRecord.getId());

                if (remote) {
                    IncomingTextMessage incoming = new IncomingTextMessage(recipient.getId(), 1, time, null, Optional.of(group), 0, false);

                    if (verified) incoming = new IncomingIdentityVerifiedMessage(incoming);
                    else          incoming = new IncomingIdentityDefaultMessage(incoming);

                    smsDatabase.insertMessageInbox(incoming);
                } else {
                    RecipientId         recipientId    = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(GroupUtil.getEncodedId(group.getGroupId(), false));
                    Recipient           groupRecipient = Recipient.resolved(recipientId);
                    long                threadId       = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
                    OutgoingTextMessage outgoing ;

                    if (verified) outgoing = new OutgoingIdentityVerifiedMessage(recipient);
                    else          outgoing = new OutgoingIdentityDefaultMessage(recipient);

                    DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(threadId, outgoing, false, time, null);
                }
            }
        }

        //this deals with the single person chat.
        if (remote) {
            IncomingTextMessage incoming = new IncomingTextMessage(recipient.getId(), 1, time, null, Optional.absent(), 0, false);

            if (verified) incoming = new IncomingIdentityVerifiedMessage(incoming);
            else          incoming = new IncomingIdentityDefaultMessage(incoming);

            Log.i(TAG, "Inserting verified outbox, is remote...");

            smsDatabase.insertMessageInbox(incoming);
        } else {
            OutgoingTextMessage outgoing;

            if (verified) outgoing = new OutgoingIdentityVerifiedMessage(recipient);
            else          outgoing = new OutgoingIdentityDefaultMessage(recipient);

            long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

            Log.i(TAG, "Inserting verified outbox...");
            DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(threadId, outgoing, false, time, null);
        }

         */
    }


}

