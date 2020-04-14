package org.securityed.securesms.components.reminder;

import android.content.Context;

import android.content.Intent;
import android.util.Log;
import android.view.View.OnClickListener;

import org.securityed.securesms.R;
import org.securityed.securesms.RegistrationActivity;
import org.securityed.securesms.education.EducationalMessage;
import org.securityed.securesms.education.EducationalMessageManager;
import org.securityed.securesms.education.LongEducationalMessageActivity;
import org.securityed.securesms.util.EducationalUtil;
import org.securityed.securesms.util.TextSecurePreferences;
import org.w3c.dom.Text;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class E2EEReminder extends Reminder {

    private Calendar time;



    public E2EEReminder(Context context) {

        boolean activated = TextSecurePreferences.getWasTooltipShown(context);

        if( activated){

            //recover what was in the previous message. and display that. also recover the time it was originally shown.

            String log = TextSecurePreferences.getSavedTooltipShownLog(context);

            String name = log.split("_")[3];
            Long prevMS = Long.parseLong(log.split( "_")[5]);
            String prevDate = log.split("_")[4];

            time = GregorianCalendar.getInstance();


            Log.d("message version", name);
            Log.d("message init time", "time: " + prevMS);
            Log.d("message init date", prevDate);

            EducationalMessage em = EducationalMessageManager.getEducationalMessageFromName(name);


            this.title = "";
            this.text  = em.getMessageString(context);



            final OnClickListener okListener = v -> {

                // fix the time.
                TextSecurePreferences.setWasTooltipDismissed(context, true);

                Long elapsedTimeSoFar = TextSecurePreferences.getTotalTooltipTime( context);

                String okLog = EducationalMessageManager.getMessageShownLogEntry( TextSecurePreferences.getLocalNumber(context),"conversationListLearnMore",
                        EducationalMessageManager.TOOL_TIP_MESSAGE, em.getMessageName(),  prevDate + "_" + prevMS,   GregorianCalendar.getInstance().getTimeInMillis() - time.getTimeInMillis() + elapsedTimeSoFar);

                EducationalMessageManager.notifyStatServer(context, EducationalMessageManager.MESSAGE_SHOWN, okLog);




                Intent intent = new Intent(context, LongEducationalMessageActivity.class);
                context.startActivity(intent);
            };


            final OnClickListener dismissListener = v -> {

                Long elapsedTimeSoFar = TextSecurePreferences.getTotalTooltipTime( context);

                String dismissLog = EducationalMessageManager.getMessageShownLogEntry( TextSecurePreferences.getLocalNumber(context),"conversationListDismissed",
                        EducationalMessageManager.TOOL_TIP_MESSAGE, em.getMessageName(),  prevDate + "_" + prevMS,   GregorianCalendar.getInstance().getTimeInMillis() - time.getTimeInMillis() + elapsedTimeSoFar);

                EducationalMessageManager.notifyStatServer(context, EducationalMessageManager.MESSAGE_SHOWN, dismissLog);


                TextSecurePreferences.setWasTooltipDismissed(context, true);

            };
            setOkListener(okListener);
            setDismissListener(dismissListener);


        } else {

            TextSecurePreferences.setWasTooltipShown(context, true);
            TextSecurePreferences.resetTotalTooltipTime(context);


            EducationalMessage em = EducationalMessageManager.getShortMessage(context);


            this.title = "";
            this.text  = em.getMessageString(context);

            time = GregorianCalendar.getInstance();


            String log = EducationalMessageManager.getMessageShownLogEntry( TextSecurePreferences.getLocalNumber(context),"conversationList",
                    EducationalMessageManager.TOOL_TIP_MESSAGE, em.getMessageName(), time.getTime(), -1 );

            EducationalMessageManager.notifyStatServer(context, EducationalMessageManager.MESSAGE_SHOWN, log);


            TextSecurePreferences.setSavedTooltipShownLog( context, log);

            TextSecurePreferences.setLastMessageShownTime( context, time.getTimeInMillis());




            final OnClickListener okListener = v -> {

                // fix the time.

                TextSecurePreferences.setWasTooltipDismissed(context, true);

                String okLog = EducationalMessageManager.getMessageShownLogEntry( TextSecurePreferences.getLocalNumber(context),"conversationListLearnMore",
                        EducationalMessageManager.TOOL_TIP_MESSAGE, em.getMessageName(), time.getTime(),   GregorianCalendar.getInstance().getTimeInMillis() - time.getTimeInMillis());

                EducationalMessageManager.notifyStatServer(context, EducationalMessageManager.MESSAGE_SHOWN, okLog);


                Intent intent = new Intent(context, LongEducationalMessageActivity.class);
                context.startActivity(intent);
            };


            final OnClickListener dismissListener = v -> {

                TextSecurePreferences.setWasTooltipDismissed(context, true);


                String dismissLog = EducationalMessageManager.getMessageShownLogEntry( TextSecurePreferences.getLocalNumber(context),"conversationListDismiss",
                        EducationalMessageManager.TOOL_TIP_MESSAGE, em.getMessageName(), time.getTime(), GregorianCalendar.getInstance().getTimeInMillis() - time.getTimeInMillis() );

                EducationalMessageManager.notifyStatServer(context, EducationalMessageManager.MESSAGE_SHOWN, dismissLog);

            };
            setOkListener(okListener);
            setDismissListener(dismissListener);

        }


    }

    @Override
    public boolean isDismissable() {
        return true;
    }

    public static boolean isEligible(Context context) {
        return !TextSecurePreferences.isPushRegistered(context);
    }


}
