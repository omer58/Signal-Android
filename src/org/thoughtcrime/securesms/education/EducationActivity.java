package org.thoughtcrime.securesms.education;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class EducationActivity extends BaseActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.education_activity);
        findViewById(R.id.education_more_button).setOnClickListener(v -> onTermsClicked());
        findViewById(R.id.welcome_continue_button).setOnClickListener(v -> onContinueClicked());
        TextView shortMessageView = findViewById(R.id.educationMessageView);
        shortMessageView.setText( EducationalMessageManager.getShortMessage(this));

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
    }

    private void onTermsClicked() {
        CommunicationActions.openBrowserLink(this, "https://signal.org/legal");
    }

    private void onContinueClicked() {
        Permissions.with(this)
                .request(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE)
                .ifNecessary()
                .withRationaleDialog(getString(R.string.RegistrationActivity_signal_needs_access_to_your_contacts_and_media_in_order_to_connect_with_friends),
                        R.drawable.ic_contacts_white_48dp, R.drawable.ic_folder_white_48dp)
                .onAnyResult(() -> {
                    TextSecurePreferences.unarmEducation(EducationActivity.this);

                    Intent nextIntent = getIntent().getParcelableExtra("next_intent");

                    if (nextIntent == null) {
                        throw new IllegalStateException("Was not supplied a next_intent.");
                    }

                    startActivity(nextIntent);
                    overridePendingTransition(R.anim.slide_from_end, R.anim.fade_scale_out);
                    finish();
                })
                .execute();
    }
}

