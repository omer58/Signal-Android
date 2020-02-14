package org.securityed.securesms.education;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import org.securityed.securesms.BaseActionBarActivity;
import org.securityed.securesms.R;
import org.securityed.securesms.permissions.Permissions;
import org.securityed.securesms.util.DynamicLanguage;
import org.securityed.securesms.util.DynamicTheme;
import org.securityed.securesms.util.TextSecurePreferences;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class LongEducationalMessageActivity extends BaseActionBarActivity {


    private final DynamicTheme dynamicTheme    = new DynamicTheme();
    private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

    private AlarmManager alarmMgr;
    private PendingIntent alarmIntent;

    private Date openDate;



    public void onPreCreate() {
        dynamicTheme.onCreate(this);
        dynamicLanguage.onCreate(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onPreCreate();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.more_about_e2ee);


        Log.d("longactivity", "onCreate()");


        setContentView(R.layout.long_message_activity);
        overridePendingTransition(R.anim.slide_from_end, R.anim.slide_to_start);



        // this is notification code.
        Context context = getApplicationContext();
        alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AppUseNotifierService.class);
        alarmIntent = PendingIntent.getService(context, 0, intent, 0);

        // Set the alarm to start at 8:30 a.m.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 8);
        calendar.set(Calendar.MINUTE, 30);


        // don't do it for now.
        //alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
        //        SystemClock.elapsedRealtime() +
        //                15 * 1000, alarmIntent);


    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            overridePendingTransition(R.anim.slide_from_start, R.anim.slide_to_end);
            //public static String getMessageShownLogEntry(String phoneNumber, String screen, int messageType, String version, Date date, long timeElapsed){
        }
        super.onPause();

        Log.d("longactivity", "onPause()");

        String log = EducationalMessageManager.getMessageShownLogEntry(TextSecurePreferences.getLocalNumber(getApplicationContext()), "long-message", EducationalMessageManager.LONG_MESSAGE_SCREEN, "na", openDate, GregorianCalendar.getInstance().getTime().getTime() - openDate.getTime());
        EducationalMessageManager.notifyStatServer(getApplicationContext(), EducationalMessageManager.MESSAGE_SHOWN, log);
    }

    @Override
    public void onResume() {
        super.onResume();
        dynamicTheme.onResume(this);
        dynamicLanguage.onResume(this);

        openDate =  GregorianCalendar.getInstance().getTime();
        String log = EducationalMessageManager.getMessageShownLogEntry(TextSecurePreferences.getLocalNumber(getApplicationContext()), "long-message", EducationalMessageManager.LONG_MESSAGE_SCREEN, "na", openDate, -1);
        EducationalMessageManager.notifyStatServer(getApplicationContext(), EducationalMessageManager.MESSAGE_SHOWN, log);

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: finish(); return true;
        }

        return false;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
    }


}

