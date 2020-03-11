package org.securityed.securesms.education;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;


import org.securityed.securesms.BaseActionBarActivity;
import org.securityed.securesms.R;
import org.securityed.securesms.permissions.Permissions;
import org.securityed.securesms.util.TextSecurePreferences;

import java.util.Calendar;
import java.util.GregorianCalendar;


public class EducationActivity extends BaseActionBarActivity {

    Calendar launchTime = null;
    EducationalMessage em = null;
    TextView shortMessageView = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.education_activity);
        findViewById(R.id.education_more_button).setOnClickListener(v -> onTermsClicked());
        findViewById(R.id.welcome_continue_button).setOnClickListener(v -> onContinueClicked());
        shortMessageView = findViewById(R.id.educationMessageView);

    }

    @Override
    protected void onResume(){
        super.onResume();

        em = EducationalMessageManager.getShortMessage(this);
        shortMessageView.setText( em.getMessageString(this));

        launchTime = GregorianCalendar.getInstance();

        EducationalMessageManager.notifyStatServer(this, EducationalMessageManager.MESSAGE_SHOWN, EducationalMessageManager.getMessageShownLogEntry(
                TextSecurePreferences.getLocalNumber(this), "splash-screen",  EducationalMessageManager.OPENING_SCREEN_MESSAGE, em.getMessageName(), launchTime.getTime(), -1));

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
    }

    private void onTermsClicked() {
        //CommunicationActions.openBrowserLink(this, "https://signal.org/legal");

        Calendar exitTime = GregorianCalendar.getInstance();
        EducationalMessageManager.notifyStatServer(this, EducationalMessageManager.MESSAGE_SHOWN, EducationalMessageManager.getMessageShownLogEntry(
                TextSecurePreferences.getLocalNumber(this), "splash-screen-terms-exit",  EducationalMessageManager.OPENING_SCREEN_MESSAGE, em.getMessageName(), launchTime.getTime(), exitTime.getTimeInMillis()- launchTime.getTimeInMillis()));


        Intent intent = new Intent(this, LongEducationalMessageActivity.class);
        startActivity(intent);

    }

    private void onContinueClicked() {

        /*
        Permissions.with(this)
                //Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS,Manifest.permission.READ_PHONE_STATE
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                .ifNecessary()
                .withRationaleDialog(getString(R.string.RegistrationActivity_signal_needs_access_to_your_contacts_and_media_in_order_to_connect_with_friends),
                        R.drawable.ic_contacts_white_48dp, R.drawable.ic_folder_white_48dp)
                .onAnyResult(() -> {


                })
                .execute();
        */

        ///////////take this block out of onAnyResult and comment out .execute to disable permissions. probably. needs to be tested.
        EducationalMessageManager.unarmEducation(EducationActivity.this);

        Intent nextIntent = getIntent().getParcelableExtra("next_intent");

        if (nextIntent == null) {
            throw new IllegalStateException("Was not supplied a next_intent.");
        }

        Calendar exitTime = GregorianCalendar.getInstance();
        String log = EducationalMessageManager.getMessageShownLogEntry(
                TextSecurePreferences.getLocalNumber(this),
                "splash-screen-cont-exit",
                EducationalMessageManager.OPENING_SCREEN_MESSAGE,
                em.getMessageName(),
                launchTime.getTime(),
                exitTime.getTimeInMillis() - launchTime.getTimeInMillis());
        EducationalMessageManager.notifyStatServer(this, EducationalMessageManager.MESSAGE_SHOWN, log);


        startActivity(nextIntent);
        overridePendingTransition(R.anim.slide_from_end, R.anim.fade_scale_out);
        finish();
        //////////// end of block
    }
}

