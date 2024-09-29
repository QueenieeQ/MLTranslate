/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.MLTranslator.voice_translation;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import java.util.ArrayList;
import java.util.List;
import nie.translator.MLTranslator.GeneralActivity;
import nie.translator.MLTranslator.Global;
import nie.translator.MLTranslator.R;
import nie.translator.MLTranslator.bluetooth.BluetoothCommunicator;
import nie.translator.MLTranslator.settings.SettingsActivity;
import nie.translator.MLTranslator.tools.CustomLocale;
import nie.translator.MLTranslator.tools.CustomServiceConnection;
import nie.translator.MLTranslator.tools.Tools;
import nie.translator.MLTranslator.tools.gui.peers.GuiPeer;
import nie.translator.MLTranslator.tools.services_communication.ServiceCommunicatorListener;
import nie.translator.MLTranslator.voice_translation._conversation_mode.PairingFragment;
import nie.translator.MLTranslator.voice_translation._conversation_mode._conversation.ConversationFragment;
import nie.translator.MLTranslator.voice_translation._conversation_mode._conversation.ConversationService;
import nie.translator.MLTranslator.voice_translation._conversation_mode._conversation.main.ConversationMainFragment;
import nie.translator.MLTranslator.voice_translation._conversation_mode.communication.ConversationBluetoothCommunicator;
import nie.translator.MLTranslator.bluetooth.Peer;

import nie.translator.MLTranslator.voice_translation._text_translation.TranslationFragment;
import nie.translator.MLTranslator.voice_translation._walkie_talkie_mode._walkie_talkie.WalkieTalkieFragment;
import nie.translator.MLTranslator.voice_translation._walkie_talkie_mode._walkie_talkie.WalkieTalkieService;


public class VoiceTranslationActivity extends GeneralActivity {
    //flags
    public static final int NORMAL_START = 0;
    public static final int FIRST_START = 1;
    //costants
    public static final int PAIRING_FRAGMENT = 0;
    public static final int CONVERSATION_FRAGMENT = 1;
    public static final int WALKIE_TALKIE_FRAGMENT = 2;
    public static final int TRANSLATION_FRAGMENT = 3;
    public static final int DEFAULT_FRAGMENT = TRANSLATION_FRAGMENT;
    public static final int NO_PERMISSIONS = -10;
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 2;
    public static String[] REQUIRED_PERMISSIONS;
    //objects
    private Global global;
    private Fragment fragment;
    private CoordinatorLayout fragmentContainer;
    private int currentFragment = -1;
    private boolean startingPairing = false;   //used to start Conversation Mode after bluetooth permissions are granted
    private ArrayList<Callback> clientsCallbacks = new ArrayList<>();
    private ArrayList<CustomServiceConnection> conversationServiceConnections = new ArrayList<>();
    private ArrayList<CustomServiceConnection> walkieTalkieServiceConnections = new ArrayList<>();
    private Handler mainHandler;  // handler that can be used to post to the main thread
    //variables
    private int connectionId = 1;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        global = (Global) getApplication();
        mainHandler = new Handler(Looper.getMainLooper());

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
            };
        }else{
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        }

        // Clean fragments (only if the app is recreated (When user disable permission))
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        // Remove previous fragments (case of the app was restarted after changed permission on android 6 and higher)
        List<Fragment> fragmentList = fragmentManager.getFragments();
        for (Fragment fragment : fragmentList) {
            if (fragment != null) {
                fragmentManager.beginTransaction().remove(fragment).commit();
            }
        }

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        fragmentContainer = findViewById(R.id.fragment_container);

        /*if (savedInstanceState != null) {
            //Restore the fragment's instance
            fragment = getSupportFragmentManager().getFragment(savedInstanceState, "myFragmentName");
        }*/
    }

    @Override
    protected void onStart() {
        super.onStart();
        // when we return to the app's gui based on the service that was saved in the last closure we choose which fragment to start
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        setFragment(sharedPreferences.getInt("fragment", DEFAULT_FRAGMENT));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    public void setFragment(int fragmentName) {
        switch (fragmentName) {
            case PAIRING_FRAGMENT: {
                // possible stop of the Conversation and WalkieTalkie Service
                stopConversationService();
                stopWalkieTalkieService();
                // possible setting of the fragment
                if (getCurrentFragment() != PAIRING_FRAGMENT) {
                    if (Tools.hasPermissions(this, REQUIRED_PERMISSIONS)) {
                        global.initializeBluetoothCommunicator();
                        if(global.getBluetoothCommunicator() != null && global.getBluetoothCommunicator().isBluetoothLeSupported()){
                            PairingFragment paringFragment = new PairingFragment();
                            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                            Bundle bundle = new Bundle();
                            paringFragment.setArguments(bundle);
                            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                            transaction.replace(R.id.fragment_container, paringFragment);
                            transaction.commit();
                            currentFragment = PAIRING_FRAGMENT;
                            saveFragment();
                            //fragment=paringFragment;
                        }else if(global.getBluetoothCommunicator().isBluetoothLeSupported()){
                            Toast.makeText(global, "Error with Bluetooth, please restart the app", Toast.LENGTH_SHORT).show();
                        }else{
                            Toast.makeText(global, R.string.error_missing_bluetooth_le, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        startingPairing = true;
                        requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
                    }
                }
                break;
            }
            case CONVERSATION_FRAGMENT: {
                // possible setting of the fragment
                if (getCurrentFragment() != CONVERSATION_FRAGMENT && global.getBluetoothCommunicator() != null) {
                    ConversationFragment conversationFragment = new ConversationFragment();
                    Bundle bundle = new Bundle();
                    bundle.putBoolean("firstStart", true);
                    conversationFragment.setArguments(bundle);
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                    transaction.replace(R.id.fragment_container, conversationFragment);
                    transaction.commit();
                    currentFragment = CONVERSATION_FRAGMENT;
                    saveFragment();
                    //fragment= conversationFragment;
                }else if(global.getBluetoothCommunicator() == null){
                    setFragment(DEFAULT_FRAGMENT);
                }
                break;
            }
            case WALKIE_TALKIE_FRAGMENT: {
                // possible setting of the fragment
                if (getCurrentFragment() != WALKIE_TALKIE_FRAGMENT) {
                    WalkieTalkieFragment walkieTalkieFragment = new WalkieTalkieFragment();
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    Bundle bundle = new Bundle();
                    bundle.putBoolean("firstStart", true);
                    walkieTalkieFragment.setArguments(bundle);
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                    transaction.replace(R.id.fragment_container, walkieTalkieFragment);
                    transaction.commit();
                    currentFragment = WALKIE_TALKIE_FRAGMENT;
                    saveFragment();
                    //fragment=walkieTalkieFragment;
                }
                break;
            }
            case TRANSLATION_FRAGMENT:{
                // possible stop of the Conversation and WalkieTalkie Service
                stopConversationService();
                stopWalkieTalkieService();
                // possible setting of the fragment
                if (getCurrentFragment() != TRANSLATION_FRAGMENT) {
                    TranslationFragment translationFragment = new TranslationFragment();
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    Bundle bundle = new Bundle();
                    translationFragment.setArguments(bundle);
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                    transaction.replace(R.id.fragment_container, translationFragment);
                    transaction.commit();
                    currentFragment = TRANSLATION_FRAGMENT;
                    saveFragment();
                    //fragment=paringFragment;
                }
                break;
            }
        }
    }

    public void saveFragment() {
        new Thread("saveFragment") {
            @Override
            public void run() {
                super.run();
                //save fragment
                SharedPreferences sharedPreferences = VoiceTranslationActivity.this.getSharedPreferences("default", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("fragment", getCurrentFragment());
                editor.apply();
            }
        }.start();
    }

    public int getCurrentFragment() {
        if (currentFragment != -1) {
            return currentFragment;
        } else {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment != null) {
                if (currentFragment.getClass().equals(PairingFragment.class)) {
                    return PAIRING_FRAGMENT;
                }
                if (currentFragment.getClass().equals(ConversationFragment.class)) {
                    return CONVERSATION_FRAGMENT;
                }
                if (currentFragment.getClass().equals(WalkieTalkieFragment.class)) {
                    return WALKIE_TALKIE_FRAGMENT;
                }
                if (currentFragment.getClass().equals(TranslationFragment.class)) {
                    return TRANSLATION_FRAGMENT;
                }
            }
        }
        return -1;
    }

    public int startSearch() {
        if(global.getBluetoothCommunicator() != null) {
            return global.getBluetoothCommunicator().startSearch();
        }else{
            return BluetoothCommunicator.ERROR;
        }
    }

    public int stopSearch(boolean tryRestoreBluetoothStatus) {
        if(global.getBluetoothCommunicator() != null) {
            return global.getBluetoothCommunicator().stopSearch(tryRestoreBluetoothStatus);
        }else{
            return BluetoothCommunicator.ERROR;
        }
    }

    public boolean isSearching() {
        if(global.getBluetoothCommunicator() != null) {
            return global.getBluetoothCommunicator().isSearching();
        }else{
            return false;
        }
    }

    public void connect(Peer peer) {
        stopSearch(false);
        if(global.getBluetoothCommunicator() != null) {
            global.getBluetoothCommunicator().connect(peer);
        }
    }

    public void acceptConnection(Peer peer) {
        if(global.getBluetoothCommunicator() != null) {
            global.getBluetoothCommunicator().acceptConnection(peer);
        }
    }

    public void rejectConnection(Peer peer) {
        if(global.getBluetoothCommunicator() != null) {
            global.getBluetoothCommunicator().rejectConnection(peer);
        }
    }

    public ArrayList<GuiPeer> getConnectedPeersList() {
        if(global.getBluetoothCommunicator() != null) {
            return global.getBluetoothCommunicator().getConnectedPeersList();
        }else{
            return new ArrayList<GuiPeer>();
        }
    }

    public ArrayList<Peer> getConnectingPeersList() {
        if(global.getBluetoothCommunicator() != null) {
            return global.getBluetoothCommunicator().getConnectingPeers();
        }else{
            return new ArrayList<Peer>();
        }
    }

    public void disconnect(Peer peer) {
        if(global.getBluetoothCommunicator() != null) {
            global.getBluetoothCommunicator().disconnect(peer);
        }
    }



    /*@Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //Save the fragment's instance
        getSupportFragmentManager().putFragment(outState, "myFragmentName", fragment);
    }*/

    /**
     * Handles user acceptance (or denial) of our permission request.
     */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_CODE_REQUIRED_PERMISSIONS) {
            return;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                //notifyMissingSearchPermission();
                Toast.makeText(global, R.string.error_missing_location_permissions, Toast.LENGTH_LONG).show();
                startingPairing = false;
                return;
            }
        }
        //bluetooth permissions are granted
        if(startingPairing) {
            startingPairing = false;
            setFragment(PAIRING_FRAGMENT);
        }
        //notifySearchPermissionGranted();

        /*if (!Tools.hasPermissions(this, REQUIRED_PERMISSIONS)) {

        }else{

        }*/
        //recreate();   // was called only if the grantResults were of length 0 or were neither PERMISSIONS_GRANTED nor PERMISSION_DENIED (I don't know what it is for anyway)
    }

    @Override
    public void onBackPressed() {
        DialogInterface.OnClickListener confirmExitListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                exitFromVoiceTranslation();
            }
        };

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment != null) {
            if (fragment instanceof ConversationFragment) {
                Fragment currentChildFragment = ((ConversationFragment) fragment).getCurrentFragment();
                if (currentChildFragment instanceof ConversationMainFragment) {
                    ConversationMainFragment conversationMainFragment = (ConversationMainFragment) currentChildFragment;
                    if (conversationMainFragment.isInputActive()) {
                        if (conversationMainFragment.isEditTextOpen()) {
                            conversationMainFragment.deleteEditText();
                        } else {
                            showConfirmExitDialog(confirmExitListener);
                        }
                    }
                } else {
                    showConfirmExitDialog(confirmExitListener);
                }
            } else if (fragment instanceof WalkieTalkieFragment) {
                WalkieTalkieFragment walkieTalkieFragment = (WalkieTalkieFragment) fragment;
                if (walkieTalkieFragment.isInputActive()) {
                    if (walkieTalkieFragment.isEditTextOpen()) {
                        walkieTalkieFragment.deleteEditText();
                    } else {
                        setFragment(DEFAULT_FRAGMENT);
                    }
                }
            } else if (fragment instanceof PairingFragment) {
                setFragment(DEFAULT_FRAGMENT);
            }else{
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    public void exitFromVoiceTranslation() {
        if (getConnectedPeersList().size() > 0 && global.getBluetoothCommunicator() != null) {
            global.getBluetoothCommunicator().disconnectFromAll();
        } else if(global.getBluetoothCommunicator() != null){
            setFragment(VoiceTranslationActivity.PAIRING_FRAGMENT);
        }else {  //if global.getBluetoothCommunicator() == null
            setFragment(VoiceTranslationActivity.DEFAULT_FRAGMENT);
        }
    }


    // services management

    public void startConversationService(final Notification notification, final Global.ResponseListener responseListener) {
        final Intent intent = new Intent(this, ConversationService.class);
        global.getLanguage(false, new Global.GetLocaleListener() {
            @Override
            public void onSuccess(CustomLocale result) {
                if(NotificationManagerCompat.from(VoiceTranslationActivity.this).areNotificationsEnabled()) {
                    intent.putExtra("notification", notification);
                }else{
                    Toast.makeText(VoiceTranslationActivity.this, getResources().getString(R.string.toast_missing_notification_permission), Toast.LENGTH_LONG).show();
                }
                startService(intent);
                responseListener.onSuccess();
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });

    }

    public void startWalkieTalkieService(final Notification notification, final Global.ResponseListener responseListener) {
        final Intent intent = new Intent(this, WalkieTalkieService.class);
        // initialization of the WalkieTalkieService
        global.getFirstLanguage(false, new Global.GetLocaleListener() {
            @Override
            public void onSuccess(CustomLocale result) {
                intent.putExtra("firstLanguage", result);
                global.getSecondLanguage(false, new Global.GetLocaleListener() {
                    @Override
                    public void onSuccess(CustomLocale result) {
                        intent.putExtra("secondLanguage", result);
                        if(NotificationManagerCompat.from(VoiceTranslationActivity.this).areNotificationsEnabled()) {
                            intent.putExtra("notification", notification);
                        }else{
                            Toast.makeText(VoiceTranslationActivity.this, getResources().getString(R.string.toast_missing_notification_permission), Toast.LENGTH_LONG).show();
                        }
                        startService(intent);
                        responseListener.onSuccess();
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        responseListener.onFailure(reasons, value);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public synchronized void connectToConversationService(final VoiceTranslationService.VoiceTranslationServiceCallback callback, final ServiceCommunicatorListener responseListener) {
        // possible start of ConversationService
        startConversationService(buildNotification(CONVERSATION_FRAGMENT), new Global.ResponseListener() {
            @Override
            public void onSuccess() {
                CustomServiceConnection conversationServiceConnection = new CustomServiceConnection(new ConversationService.ConversationServiceCommunicator(connectionId));
                connectionId++;
                conversationServiceConnection.addCallbacks(callback, responseListener);
                conversationServiceConnections.add(conversationServiceConnection);
                bindService(new Intent(VoiceTranslationActivity.this, ConversationService.class), conversationServiceConnection, BIND_ABOVE_CLIENT);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public synchronized void connectToWalkieTalkieService(final VoiceTranslationService.VoiceTranslationServiceCallback callback, final ServiceCommunicatorListener responseListener) {
        // possible start of WalkieTalkieService
        startWalkieTalkieService(buildNotification(WALKIE_TALKIE_FRAGMENT), new Global.ResponseListener() {
            @Override
            public void onSuccess() {
                CustomServiceConnection walkieTalkieServiceConnection = new CustomServiceConnection(new WalkieTalkieService.WalkieTalkieServiceCommunicator(connectionId));
                connectionId++;
                walkieTalkieServiceConnection.addCallbacks(callback, responseListener);
                walkieTalkieServiceConnections.add(walkieTalkieServiceConnection);
                bindService(new Intent(VoiceTranslationActivity.this, WalkieTalkieService.class), walkieTalkieServiceConnection, BIND_ABOVE_CLIENT);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public void disconnectFromConversationService(ConversationService.ConversationServiceCommunicator conversationServiceCommunicator) {
        int index = -1;
        boolean found = false;
        for (int i = 0; i < conversationServiceConnections.size() && !found; i++) {
            if (conversationServiceConnections.get(i).getServiceCommunicator().equals(conversationServiceCommunicator)) {
                index = i;
                found = true;
            }
        }
        if (index != -1) {
            CustomServiceConnection serviceConnection = conversationServiceConnections.remove(index);
            unbindService(serviceConnection);
            serviceConnection.onServiceDisconnected();
        }
    }

    public void disconnectFromWalkieTalkieService(WalkieTalkieService.WalkieTalkieServiceCommunicator walkieTalkieServiceCommunicator) {
        int index = -1;
        boolean found = false;
        for (int i = 0; i < walkieTalkieServiceConnections.size() && !found; i++) {
            if (walkieTalkieServiceConnections.get(i).getServiceCommunicator().equals(walkieTalkieServiceCommunicator)) {
                index = i;
                found = true;
            }
        }
        if (index != -1) {
            CustomServiceConnection serviceConnection = walkieTalkieServiceConnections.remove(index);
            unbindService(serviceConnection);
            serviceConnection.onServiceDisconnected();
        }
    }

    public void stopConversationService() {
        stopService(new Intent(this, ConversationService.class));
    }

    public void stopWalkieTalkieService() {
        stopService(new Intent(this, WalkieTalkieService.class));
    }

    //notification
    private Notification buildNotification(int clickAction) {
        String channelID = "service_background_notification";
        // creation of the click on the notification
        Intent resultIntent = new Intent(this, VoiceTranslationActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        // creation of the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelID);
        if (clickAction == CONVERSATION_FRAGMENT) {
            builder.setContentTitle(getString(R.string.title_fragment_conversation))
                    .setContentText(getString(R.string.conversation_mode_running))
                    .setContentIntent(resultPendingIntent)
                    .setSmallIcon(R.drawable.mic_icon)
                    .setOngoing(true)
                    .setChannelId(channelID)
                    .build();
        } else {
            builder.setContentTitle(getString(R.string.title_fragment_walkie_talkie))
                    .setContentText(getString(R.string.walkietalkie_mode_running))
                    .setContentIntent(resultPendingIntent)
                    .setSmallIcon(R.drawable.mic_icon)
                    .setOngoing(true)
                    .setChannelId(channelID)
                    .build();
        }
        return builder.build();
    }


    public void addCallback(Callback callback) {
        // in this way the listener will listen to both this activity and the communicator
        if(global.getBluetoothCommunicator() != null) {
            global.getBluetoothCommunicator().addCallback(callback);
        }
        clientsCallbacks.add(callback);
    }

    public void removeCallback(Callback callback) {
        if(global.getBluetoothCommunicator() != null) {
            global.getBluetoothCommunicator().removeCallback(callback);
        }
        clientsCallbacks.remove(callback);
    }

    private void notifyMissingSearchPermission() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientsCallbacks.size(); i++) {
                    clientsCallbacks.get(i).onMissingSearchPermission();
                }
            }
        });
    }

    private void notifySearchPermissionGranted() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientsCallbacks.size(); i++) {
                    clientsCallbacks.get(i).onSearchPermissionGranted();
                }
            }
        });
    }

    public CoordinatorLayout getFragmentContainer() {
        return fragmentContainer;
    }

    public static class Callback extends ConversationBluetoothCommunicator.Callback {
        public void onMissingSearchPermission() {
        }

        public void onSearchPermissionGranted() {
        }
    }
}
