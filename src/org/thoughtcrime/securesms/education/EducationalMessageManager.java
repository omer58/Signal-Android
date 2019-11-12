package org.thoughtcrime.securesms.education;

import android.content.Context;
import android.util.Log;

import androidx.core.widget.EdgeEffectCompat;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.Date;


/**
 *
 * This class implements the logic of when and where to show the educational messages.
 * Each UI controller for the X places we can possibly show messages call isTimeForShortMessage()
 * followed by getShortMessage() if the initial call returns true.
 *
 * @author Omer
 */

public class EducationalMessageManager {

    //message place codes.
    public static final int IN_CONVERSATION_MESSAGE = 0;
    public static final int TOOL_TIP_MESSAGE = 1;
    public static final int OPENING_SCREEN_MESSAGE = 2;

    public static final String MESSAGE_SHOWN = "message_shown";
    public static final String MESSAGE_EXCHANGE = "message_exchange";



    //private static final int[] shortMessages = {R.string.short_v1, R.string.short_v2, R.string.short_v3, R.string.short_v4, R.string.short_v5};
    private static final EducationalMessage[] shortMessages =
            {new EducationalMessage(R.string.short_v1, "short-v1"),
            new EducationalMessage(R.string.short_v2, "short-v2"),
            new EducationalMessage(R.string.short_v3, "short-v3"),
            new EducationalMessage(R.string.short_v4, "short-v4"),
            new EducationalMessage(R.string.short_v5, "short-v5")};

    private static final int[] mediumMessages = {R.string.mid_v1, R.string.mid_v2};
    private static final int longMessage = R.string.longMessage;


    public static boolean isTimeForShortMessage(Context context, int messagePlaceCode){
        switch(messagePlaceCode){
        case IN_CONVERSATION_MESSAGE:
            return false;
        case TOOL_TIP_MESSAGE:
            return true;
        case OPENING_SCREEN_MESSAGE:
            //migrate this function definition into here.
            return TextSecurePreferences.hasNotSeenEducationalMessageInAWhile(context);
        default:
            return false;
        }

    }

    public static String getMessageShownLogEntry(String phoneNumber, String screen, int messageType, String version, Date date, long timeElapsed){
        return phoneNumber + "_" + screen + "_" + messageType + "_" + version + "_" + date.toString() + "_" + timeElapsed;
    }

    public static String getMessageExchangeLogEntry(String phoneNumber, Boolean sent, String messageType, Date date){
        return phoneNumber + "_" + sent + "_" + messageType + "_" + date.toString();
    }

    public static void notifyStatServer(Context context, String logType, String log){

        RequestQueue queue = Volley.newRequestQueue(context);
        String url = "https://akgul.cs.umd.edu/post_stats/?" + logType + "=" + log;

        StringRequest stringRequest = new StringRequest(StringRequest.Method.GET, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {

                Log.d("request sent to server", response);

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

                Log.d("uuuhhhhhh", error.toString());

            }
        });

        queue.add(stringRequest);
    }


    //these are what we use to make sure the welcome educational message only appears on app launch
    //and not with every activity launch.
    //If it's time, the onClose method arms the message
    //With the next run the message is displayed and message disarmed so we don't show it again.
    //NOT a school shooting joke.
    public static boolean isEducationArmed(Context context){
        return TextSecurePreferences.isEducationArmed(context);
    }
    public static void armEducation(Context context){
        TextSecurePreferences.armEducation(context);
    }

    public static void unarmEducation(Context context){
        TextSecurePreferences.unarmEducation(context);
    }
    public static String getShortMessageString(Context context){
        return getShortMessage(context).getMessageString(context);
    }

    public static int getShortMessageID(Context context){
        return getShortMessage(context).getStringID();
    }

    public static EducationalMessage getShortMessage(Context context){
        int times_opened = TextSecurePreferences.incrementLastMessageSeen(context);
        return shortMessages[times_opened % shortMessages.length];
    }

    public static String getMidMessage(Context context){
        return getString(context, mediumMessages[0]);
    }

    public static String getLongMessage(Context context){
        return getString(context, longMessage);
    }

    private static String getString(Context context, int stringID){
        return context.getResources().getString(stringID);
    }
}
