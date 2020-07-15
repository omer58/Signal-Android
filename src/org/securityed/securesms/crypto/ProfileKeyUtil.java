package org.securityed.securesms.crypto;


import android.content.Context;
import androidx.annotation.NonNull;

import org.securityed.securesms.util.Base64;
import org.securityed.securesms.util.TextSecurePreferences;
import org.securityed.securesms.util.Util;

import java.io.IOException;

public class ProfileKeyUtil {

  public static synchronized boolean hasProfileKey(@NonNull Context context) {
    return TextSecurePreferences.getProfileKey(context) != null;
  }

  public static synchronized @NonNull byte[] getProfileKey(@NonNull Context context) {
    try {
      String encodedProfileKey = TextSecurePreferences.getProfileKey(context);

      if (encodedProfileKey == null) {
        encodedProfileKey = Util.getSecret(32);
        TextSecurePreferences.setProfileKey(context, encodedProfileKey);
      }

      return Base64.decode(encodedProfileKey);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static synchronized @NonNull byte[] rotateProfileKey(@NonNull Context context) {
    TextSecurePreferences.setProfileKey(context, null);
    return getProfileKey(context);
  }
}