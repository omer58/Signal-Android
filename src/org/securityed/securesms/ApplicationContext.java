/*
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.securityed.securesms;

import android.annotation.SuppressLint;

import androidx.camera.camera2.Camera2AppConfig;
import androidx.camera.core.CameraX;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.multidex.MultiDexApplication;

import com.google.android.gms.security.ProviderInstaller;

import org.conscrypt.Conscrypt;
import org.securityed.securesms.education.EducationalMessage;
import org.signal.aesgcmprovider.AesGcmProvider;
import org.securityed.securesms.components.TypingStatusRepository;
import org.securityed.securesms.components.TypingStatusSender;
import org.securityed.securesms.database.DatabaseFactory;
import org.securityed.securesms.database.helpers.SQLCipherOpenHelper;
import org.securityed.securesms.dependencies.ApplicationDependencies;
import org.securityed.securesms.dependencies.ApplicationDependencyProvider;
import org.securityed.securesms.education.EducationalMessageManager;
import org.securityed.securesms.gcm.FcmJobService;
import org.securityed.securesms.jobmanager.JobManager;
import org.securityed.securesms.jobmanager.JobMigrator;
import org.securityed.securesms.jobmanager.impl.JsonDataSerializer;
import org.securityed.securesms.jobs.CreateSignedPreKeyJob;
import org.securityed.securesms.jobs.FastJobStorage;
import org.securityed.securesms.jobs.FcmRefreshJob;
import org.securityed.securesms.jobs.JobManagerFactories;
import org.securityed.securesms.jobs.MultiDeviceContactUpdateJob;
import org.securityed.securesms.jobs.PushNotificationReceiveJob;
import org.securityed.securesms.jobs.RefreshUnidentifiedDeliveryAbilityJob;
import org.securityed.securesms.logging.AndroidLogger;
import org.securityed.securesms.logging.CustomSignalProtocolLogger;
import org.securityed.securesms.logging.Log;
import org.securityed.securesms.logging.PersistentLogger;
import org.securityed.securesms.logging.UncaughtExceptionLogger;
import org.securityed.securesms.migrations.ApplicationMigrations;
import org.securityed.securesms.notifications.MessageNotifier;
import org.securityed.securesms.notifications.NotificationChannels;
import org.securityed.securesms.providers.BlobProvider;
import org.securityed.securesms.push.SignalServiceNetworkAccess;
import org.securityed.securesms.service.DirectoryRefreshListener;
import org.securityed.securesms.service.ExpiringMessageManager;
import org.securityed.securesms.service.IncomingMessageObserver;
import org.securityed.securesms.service.KeyCachingService;
import org.securityed.securesms.service.LocalBackupListener;
import org.securityed.securesms.revealable.ViewOnceMessageManager;
import org.securityed.securesms.service.RotateSenderCertificateListener;
import org.securityed.securesms.service.RotateSignedPreKeyListener;
import org.securityed.securesms.service.UpdateApkRefreshListener;
import org.securityed.securesms.util.TextSecurePreferences;
import org.securityed.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;
import org.w3c.dom.Text;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnectionFactory.InitializationOptions;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;

import java.security.Security;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends MultiDexApplication implements DefaultLifecycleObserver {

  private static final String TAG = ApplicationContext.class.getSimpleName();

  private ExpiringMessageManager   expiringMessageManager;
  private ViewOnceMessageManager   viewOnceMessageManager;
  private TypingStatusRepository   typingStatusRepository;
  private TypingStatusSender       typingStatusSender;
  private JobManager               jobManager;
  private IncomingMessageObserver  incomingMessageObserver;
  private PersistentLogger         persistentLogger;


  private ApplicationTimeTracker   applicationTimeTracker;

  private volatile boolean isAppVisible;

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "onCreate()");
    initializeSecurityProvider();
    initializeLogging();
    initializeCrashHandling();
    initializeFirstEverAppLaunch();
    initializeAppDependencies();
    initializeJobManager();
    initializeApplicationMigrations();
    initializeMessageRetrieval();
    initializeExpiringMessageManager();
    initializeRevealableMessageManager();
    initializeTypingStatusRepository();
    initializeTypingStatusSender();
    initializeGcmCheck();
    initializeSignedPreKeyCheck();
    initializePeriodicTasks();
    initializeCircumvention();
    initializeWebRtc();
    initializePendingMessages();
    initializeUnidentifiedDeliveryAbilityRefresh();
    initializeBlobProvider();
    initializeCameraX();
    NotificationChannels.create(this);
    ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    jobManager.beginJobLoop();
  }

  @Override
  public void onStart(@NonNull LifecycleOwner owner) {
    isAppVisible = true;

    executePendingContactSync();
    KeyCachingService.onAppForegrounded(this);

    TextSecurePreferences.incrementAppOpenTime(this);
    EducationalMessageManager.sendUnsentLogs(this);

    applicationTimeTracker = new ApplicationTimeTracker(); // sets open date automatically.
    EducationalMessageManager.notifyStatServer(this,
            EducationalMessageManager.MESSAGE_EXCHANGE,
            EducationalMessageManager.getMessageExchangeLogEntry( TextSecurePreferences.getLocalNumber(this), false, "app-open", applicationTimeTracker.getOpenDate()));


  }

  @Override
  public void onResume(LifecycleOwner owner){
    Log.i(TAG, "App is now visible.");
  }




  @Override
  public void onPause(LifecycleOwner owner){

    Log.i(TAG, "App is no longer visible.");
  }


  @Override
  public void onStop(@NonNull LifecycleOwner owner) {
    isAppVisible = false;
    KeyCachingService.onAppBackgrounded(this);
    MessageNotifier.setVisibleThread(-1);


    //state control

    if( EducationalMessageManager.isTimeForShortMessage(this, EducationalMessageManager.OPENING_SCREEN_MESSAGE)
            && !EducationalMessageManager.isEducationArmed(this) ){
      EducationalMessageManager.armEducation(this);
    }


    if( TextSecurePreferences.getWasTooltipShown(this) && !TextSecurePreferences.getWasTooltipDismissed(this)){

      //this means that the tooltip was still not dismissed so we don't want to reset anything.

      //unless it's been too long then we'll dismiss it for them.
      String log = TextSecurePreferences.getSavedTooltipShownLog(this);

      String name = log.split("_")[3];
      Long prevMS = Long.parseLong(log.split( "_")[5]);
      String prevDate = log.split("_")[4];

      Long currentTime = GregorianCalendar.getInstance().getTimeInMillis();

      EducationalMessage em = EducationalMessageManager.getEducationalMessageFromName(name);


      if( currentTime - prevMS > EducationalMessageManager.MAX_DISPLAY_TIME_TOOLTIP){

        Long elapsedTimeSoFar = TextSecurePreferences.getTotalTooltipTime( this);

        String dismissLog = EducationalMessageManager.getMessageShownLogEntry( TextSecurePreferences.getLocalNumber(this),"conversationListAutoDismissed",
                EducationalMessageManager.TOOL_TIP_MESSAGE, em.getMessageName(),  prevDate + "_" + prevMS, elapsedTimeSoFar);

        EducationalMessageManager.notifyStatServer(this, EducationalMessageManager.MESSAGE_SHOWN, dismissLog);


        TextSecurePreferences.setWasTooltipDismissed(this, true);
        TextSecurePreferences.setWasTooltipShown(this, false);
        TextSecurePreferences.armTooltip(this);

      }


    }else{

      TextSecurePreferences.setWasTooltipShown(this, false);
      TextSecurePreferences.armTooltip(this);

    }

    TextSecurePreferences.setWasConversationShownOnce(this, false);


    applicationTimeTracker.setCloseDate(GregorianCalendar.getInstance().getTime());

    String screenTimeLog = applicationTimeTracker.getScreenTimeLog();

    EducationalMessageManager.notifyStatServer(this, EducationalMessageManager.MESSAGE_EXCHANGE,
            EducationalMessageManager.getMessageExchangeLogEntry( TextSecurePreferences.getLocalNumber(this), false, "app-close", applicationTimeTracker.getOpenDate()) + screenTimeLog);





  }

  public JobManager getJobManager() {
    return jobManager;
  }

  public ExpiringMessageManager getExpiringMessageManager() {
    return expiringMessageManager;
  }

  public ViewOnceMessageManager getViewOnceMessageManager() {
    return viewOnceMessageManager;
  }

  public TypingStatusRepository getTypingStatusRepository() {
    return typingStatusRepository;
  }

  public TypingStatusSender getTypingStatusSender() {
    return typingStatusSender;
  }

  public boolean isAppVisible() {
    return isAppVisible;
  }

  public PersistentLogger getPersistentLogger() {
    return persistentLogger;
  }

  public void addToConversationListTimeElapsed(long timeElapsed){

    applicationTimeTracker.addToConversationListTimeElapsed( timeElapsed);
  }

  public void addToConversationTimeElapsed(long timeElapsed){

    applicationTimeTracker.addToConversationTimeElapsed(timeElapsed);
  }

  private void initializeSecurityProvider() {
    try {
      Class.forName("org.signal.aesgcmprovider.AesGcmCipher");
    } catch (ClassNotFoundException e) {
      Log.e(TAG, "Failed to find AesGcmCipher class");
      throw new ProviderInitializationException();
    }

    int aesPosition = Security.insertProviderAt(new AesGcmProvider(), 1);
    Log.i(TAG, "Installed AesGcmProvider: " + aesPosition);

    if (aesPosition < 0) {
      Log.e(TAG, "Failed to install AesGcmProvider()");
      throw new ProviderInitializationException();
    }

    int conscryptPosition = Security.insertProviderAt(Conscrypt.newProvider(), 2);
    Log.i(TAG, "Installed Conscrypt provider: " + conscryptPosition);

    if (conscryptPosition < 0) {
      Log.w(TAG, "Did not install Conscrypt provider. May already be present.");
    }
  }

  private void initializeLogging() {
    persistentLogger = new PersistentLogger(this);
    org.securityed.securesms.logging.Log.initialize(new AndroidLogger(), persistentLogger);

    SignalProtocolLoggerProvider.setProvider(new CustomSignalProtocolLogger());
  }

  private void initializeCrashHandling() {
    final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionLogger(originalHandler));
  }

  private void initializeJobManager() {
    this.jobManager = new JobManager(this, new JobManager.Configuration.Builder()
                                                                       .setDataSerializer(new JsonDataSerializer())
                                                                       .setJobFactories(JobManagerFactories.getJobFactories(this))
                                                                       .setConstraintFactories(JobManagerFactories.getConstraintFactories(this))
                                                                       .setConstraintObservers(JobManagerFactories.getConstraintObservers(this))
                                                                       .setJobStorage(new FastJobStorage(DatabaseFactory.getJobDatabase(this)))
                                                                       .setJobMigrator(new JobMigrator(TextSecurePreferences.getJobManagerVersion(this), JobManager.CURRENT_VERSION, JobManagerFactories.getJobMigrations(this)))
                                                                       .build());
  }

  private void initializeApplicationMigrations() {
    ApplicationMigrations.onApplicationCreate(this, jobManager);
  }

  public void initializeMessageRetrieval() {
    this.incomingMessageObserver = new IncomingMessageObserver(this);
  }

  private void initializeAppDependencies() {
    ApplicationDependencies.init(this, new ApplicationDependencyProvider(this, new SignalServiceNetworkAccess(this)));
  }

  private void initializeFirstEverAppLaunch() {
    if (TextSecurePreferences.getFirstInstallVersion(this) == -1) {
      if (!SQLCipherOpenHelper.databaseFileExists(this)) {
        Log.i(TAG, "First ever app launch!");

        TextSecurePreferences.setAppMigrationVersion(this, ApplicationMigrations.CURRENT_VERSION);
        TextSecurePreferences.setJobManagerVersion(this, JobManager.CURRENT_VERSION);
      }

      Log.i(TAG, "Setting first install version to " + BuildConfig.CANONICAL_VERSION_CODE);
      TextSecurePreferences.setFirstInstallVersion(this, BuildConfig.CANONICAL_VERSION_CODE);
    }
  }

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this)) {
      long nextSetTime = TextSecurePreferences.getFcmTokenLastSetTime(this) + TimeUnit.HOURS.toMillis(6);

      if (TextSecurePreferences.getFcmToken(this) == null || nextSetTime <= System.currentTimeMillis()) {
        this.jobManager.add(new FcmRefreshJob());
      }
    }
  }

  private void initializeSignedPreKeyCheck() {
    if (!TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      jobManager.add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
  }

  private void initializeRevealableMessageManager() {
    this.viewOnceMessageManager = new ViewOnceMessageManager(this);
  }

  private void initializeTypingStatusRepository() {
    this.typingStatusRepository = new TypingStatusRepository();
  }

  private void initializeTypingStatusSender() {
    this.typingStatusSender = new TypingStatusSender(this);
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);
    LocalBackupListener.schedule(this);
    RotateSenderCertificateListener.schedule(this);

    if (BuildConfig.PLAY_STORE_DISABLED) {
      UpdateApkRefreshListener.schedule(this);
    }
  }

  private void initializeWebRtc() {
    try {
      Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
        add("Moto G5");
        add("Moto G (5S) Plus");
        add("Moto G4");
        add("TA-1053");
        add("Mi A1");
        add("E5823"); // Sony z5 compact
        add("Redmi Note 5");
        add("FP2"); // Fairphone FP2
        add("MI 5");
      }};

      Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
        add("Pixel");
        add("Pixel XL");
      }};

      if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
      }

      if (!OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
        WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
      }

      PeerConnectionFactory.initialize(InitializationOptions.builder(this).createInitializationOptions());
    } catch (UnsatisfiedLinkError e) {
      Log.w(TAG, e);
    }
  }

  @SuppressLint("StaticFieldLeak")
  private void initializeCircumvention() {
    AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        if (new SignalServiceNetworkAccess(ApplicationContext.this).isCensored(ApplicationContext.this)) {
          try {
            ProviderInstaller.installIfNeeded(ApplicationContext.this);
          } catch (Throwable t) {
            Log.w(TAG, t);
          }
        }
        return null;
      }
    };

    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void executePendingContactSync() {
    if (TextSecurePreferences.needsFullContactSync(this)) {
      ApplicationContext.getInstance(this).getJobManager().add(new MultiDeviceContactUpdateJob(true));
    }
  }

  private void initializePendingMessages() {
    if (TextSecurePreferences.getNeedsMessagePull(this)) {
      Log.i(TAG, "Scheduling a message fetch.");
      if (Build.VERSION.SDK_INT >= 26) {
        FcmJobService.schedule(this);
      } else {
        ApplicationContext.getInstance(this).getJobManager().add(new PushNotificationReceiveJob(this));
      }
      TextSecurePreferences.setNeedsMessagePull(this, false);
    }
  }

  private void initializeUnidentifiedDeliveryAbilityRefresh() {
    if (TextSecurePreferences.isMultiDevice(this) && !TextSecurePreferences.isUnidentifiedDeliveryEnabled(this)) {
      jobManager.add(new RefreshUnidentifiedDeliveryAbilityJob());
    }
  }

  private void initializeBlobProvider() {
    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
      BlobProvider.getInstance().onSessionStart(this);
    });
  }

  @SuppressLint("RestrictedApi")
  private void initializeCameraX() {
    if (Build.VERSION.SDK_INT >= 21) {
      new Thread(() -> {
        try {
          CameraX.init(this, Camera2AppConfig.create(this));
        } catch (Throwable t) {
          Log.w(TAG, "Failed to initialize CameraX.");
        }
      }, "signal-camerax-initialization").start();
    }
  }

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(base, TextSecurePreferences.getLanguage(base)));
  }

  private static class ProviderInitializationException extends RuntimeException {
  }
}
