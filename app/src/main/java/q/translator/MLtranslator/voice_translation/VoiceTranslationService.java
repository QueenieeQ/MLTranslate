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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Toast;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import nie.translator.MLTranslator.GeneralService;
import nie.translator.MLTranslator.R;
import nie.translator.MLTranslator.bluetooth.tools.Timer;
import nie.translator.MLTranslator.tools.CustomLocale;
import nie.translator.MLTranslator.tools.TTS;
import nie.translator.MLTranslator.tools.Tools;
import nie.translator.MLTranslator.tools.gui.messages.GuiMessage;
import nie.translator.MLTranslator.tools.services_communication.ServiceCallback;
import nie.translator.MLTranslator.tools.services_communication.ServiceCommunicator;
import nie.translator.MLTranslator.voice_translation.neural_networks.voice.RecognizerListener;
import nie.translator.MLTranslator.voice_translation.neural_networks.voice.Recorder;


public abstract class VoiceTranslationService extends GeneralService {
    // commands
    public static final int GET_ATTRIBUTES = 6;
    public static final int START_MIC = 0;
    public static final int STOP_MIC = 1;
    public static final int START_SOUND = 2;
    public static final int STOP_SOUND = 3;
    public static final int SET_EDIT_TEXT_OPEN = 7;
    public static final int RECEIVE_TEXT = 4;
    // callbacks
    public static final int ON_ATTRIBUTES = 5;
    public static final int ON_VOICE_STARTED = 0;
    public static final int ON_VOICE_ENDED = 1;
    public static final int ON_MIC_PROGRAMMATICALLY_STARTED = 10;
    public static final int ON_MIC_PROGRAMMATICALLY_STOPPED = 11;
    public static final int ON_MESSAGE = 2;
    public static final int ON_CONNECTED_BLUETOOTH_HEADSET = 15;
    public static final int ON_DISCONNECTED_BLUETOOTH_HEADSET = 16;
    public static final int ON_STOPPED = 6;

    // permissions
    public static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 3;
    public static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.RECORD_AUDIO,
    };

    // errors
    public static final int MISSING_MIC_PERMISSION = 400;

    // objects
    Notification notification;
    protected Recorder.Callback mVoiceCallback;
    protected Handler clientHandler;
    protected Recorder mVoiceRecorder;
    protected UtteranceProgressListener ttsListener;
    @Nullable
    protected TTS tts;
    protected Handler mainHandler;
    private static final long WAKELOCK_TIMEOUT = 600 * 1000L;  // 10 minutes, so if the service stopped without calling onDestroyed the wakeLock would still be released within 10 minutes (the wakeLock will be reacquired before the 10 minutes if the service is still running)
    private Timer wakeLockTimer;  // to reactivate the timer every 10 minutes, so as long as the service is active the wakelock will never expire
    private PowerManager.WakeLock screenWakeLock;

    // variables
    private ArrayList<GuiMessage> messages = new ArrayList<>(); // messages exchanged since the beginning of the service
    protected boolean isMicMute = false;
    protected boolean isAudioMute = false;
    protected boolean isEditTextOpen = false;
    protected int utterancesCurrentlySpeaking = 0;
    protected final Object mLock = new Object();


    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(this.getMainLooper());
        //reset translator last input and last output text
        /*if(((Global) getApplication())!=null){
            Translator translator = ((Global) getApplication()).getTranslator();
            if(translator != null){
                translator.resetLastInputOutput();
            }
        }*/
        // wake lock initialization (to keep the process active when the phone is on standby)
        acquireWakeLock();
        // tts initialization
        ttsListener = new UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
            }

            @Override
            public void onDone(String s) {
                synchronized (mLock) {
                    if (utterancesCurrentlySpeaking > 0) {
                        utterancesCurrentlySpeaking--;
                    }
                    if (!isMicMute && utterancesCurrentlySpeaking == 0) {
                        /*
                        // start the task because this thread is not allowed to start the Recorder
                        StartVoiceRecorderTask startVoiceRecorderTask = new StartVoiceRecorderTask();
                        startVoiceRecorderTask.execute(VoiceTranslationService.this);*/
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (shouldStopMicDuringTTS()) {
                                    startVoiceRecorder();
                                    notifyMicProgrammaticallyStarted();
                                }
                            }
                        }, 500);  //4000
                    }
                }
            }

            @Override
            public void onError(String s) {
            }
        };

        initializeTTS();
    }

    private void initializeTTS() {
        tts = new TTS(this, new TTS.InitListener() {  // tts initialization (to be improved, automatic package installation)
            @Override
            public void onInit() {
                if(tts != null) {
                    tts.setOnUtteranceProgressListener(ttsListener);
                }
            }

            @Override
            public void onError(int reason) {
                tts = null;
                notifyError(new int[]{reason}, -1);
                isAudioMute = true;
            }
        });
    }

    public abstract void initializeVoiceRecorder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (notification == null) {
            notification = intent.getParcelableExtra("notification");
        }
        if (notification != null) {
            startForeground(11, notification);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    protected boolean isAudioMute() {
        return isAudioMute;
    }

    // voice recorder

    /*private static class StartVoiceRecorderTask extends AsyncTask<VoiceTranslationService, Void, VoiceTranslationService> {
        @Override
        protected VoiceTranslationService doInBackground(VoiceTranslationService... voiceTranslationServices) {
            if (voiceTranslationServices.length > 0) {
                return voiceTranslationServices[0];
            }
            return null;
        }

        @Override
        protected void onPostExecute(VoiceTranslationService voiceTranslationService) {
            super.onPostExecute(voiceTranslationService);
            if (voiceTranslationService != null) {
                voiceTranslationService.startVoiceRecorder();
            }
        }
    }*/

    public void startVoiceRecorder() {
        if (!Tools.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            notifyError(new int[]{MISSING_MIC_PERMISSION}, -1);
        } else {
            if(mVoiceRecorder == null){
                initializeVoiceRecorder();
            }
            if (mVoiceRecorder != null && !isMicMute) {
                mVoiceRecorder.start();
            }
        }
    }

    public void stopVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
        }
    }

    public void endVoice(){
        if(mVoiceRecorder != null){
            mVoiceRecorder.end();
        }
    }

    protected int getVoiceRecorderSampleRate() {
        if (mVoiceRecorder != null) {
            return mVoiceRecorder.getSampleRate();
        } else {
            return 0;
        }
    }

    protected boolean isMetaText(String text){
        //returns true if one of the first 3 characters is a '[' or a '('
        if(text.length() >= 3) {
            return (text.charAt(0) == '[' || text.charAt(0) == '(') || (text.charAt(1) == '[' || text.charAt(1) == '(') || (text.charAt(2) == '[' || text.charAt(2) == '(');
        }else{
            return false;
        }
    }

    // tts

    public synchronized void speak(String result, CustomLocale language) {
        synchronized (mLock) {
            if (tts != null && tts.isActive() && !isAudioMute) {
                utterancesCurrentlySpeaking++;
                if (shouldStopMicDuringTTS()) {
                    stopVoiceRecorder();
                    notifyMicProgrammaticallyStopped();   // we notify the client
                }
                if (tts.getVoice() != null && language.equals(new CustomLocale(tts.getVoice().getLocale()))) {
                    tts.speak(result, TextToSpeech.QUEUE_ADD, null, "c01");
                } else {
                    tts.setLanguage(language,this);
                    tts.speak(result, TextToSpeech.QUEUE_ADD, null, "c01");
                }
            }
        }
    }

    protected boolean shouldStopMicDuringTTS() {
        return true;
    }

    protected boolean isBluetoothHeadsetConnected() {
        return false;
    }

    // messages

    protected void addOrUpdateMessage(GuiMessage message) {
        if(message.getMessageID() != -1) {
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).getMessageID() == message.getMessageID()) {
                    messages.set(i, message);
                    return;
                }
            }
        }
        messages.add(message);
    }

    // other

    private void startWakeLockReactivationTimer(final Timer.Callback callback) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                wakeLockTimer = new Timer(WAKELOCK_TIMEOUT - 10000);  //si riattiva 10 secondi prima cosi da essere sicuri che non ci siano istanti in cui il wakelock è rilasciato
                wakeLockTimer.setCallback(callback);
                wakeLockTimer.start();
            }
        });
    }

    private void resetWakeLockReactivationTimer() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (wakeLockTimer != null) {
                    wakeLockTimer.cancel();
                    wakeLockTimer = null;
                }
            }
        });
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        screenWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "speechGoogleUserEdition:screenWakeLock");
        screenWakeLock.acquire(WAKELOCK_TIMEOUT);
        android.util.Log.i("performance", "WakeLock acquired");
        startWakeLockReactivationTimer(new Timer.Callback() {
            @Override
            public void onFinished() {
                acquireWakeLock();
            }
        });
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        // Stop listening to voice
        stopVoiceRecorder();
        mVoiceRecorder.destroy();
        mVoiceRecorder = null;
        //stop tts
        if(tts != null) {
            tts.stop();
            tts.shutdown();
        }
        //stop foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        //reset translator last input and last output text
        /*if(((Global) getApplication())!=null){
            Translator translator = ((Global) getApplication()).getTranslator();
            if(translator != null){
                translator.resetLastInputOutput();
            }
        }*/
        //release wake lock
        resetWakeLockReactivationTimer();
        if (screenWakeLock != null) {
            while (screenWakeLock.isHeld()) {
                screenWakeLock.release();
                android.util.Log.i("performance", "WakeLock released");
            }
            screenWakeLock = null;
        }
    }

    // communication

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new Messenger(clientHandler).getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    protected boolean executeCommand(int command, Bundle data) {
        if (!super.executeCommand(command, data)) {
            switch (command) {
                case START_MIC:
                    isMicMute = false;
                    startVoiceRecorder();
                    return true;
                case STOP_MIC:
                    if (data.getBoolean("permanent")) {
                        isMicMute = true;
                        endVoice();
                    }
                    stopVoiceRecorder();
                    return true;
                case START_SOUND:
                    isAudioMute = false;
                    if (tts != null && !tts.isActive()) {
                        initializeTTS();
                    }
                    return true;
                case STOP_SOUND:
                    isAudioMute = true;
                    if (utterancesCurrentlySpeaking > 0) {
                        utterancesCurrentlySpeaking = 0;
                        if(tts != null) {
                            tts.stop();
                        }
                        ttsListener.onDone("");
                    }
                    return true;
                case SET_EDIT_TEXT_OPEN:
                    isEditTextOpen = data.getBoolean("value");
                    return true;
                case GET_ATTRIBUTES:
                    Bundle bundle = new Bundle();
                    bundle.putInt("callback", ON_ATTRIBUTES);
                    bundle.putParcelableArrayList("messages", messages);
                    bundle.putBoolean("isMicMute", isMicMute);
                    bundle.putBoolean("isAudioMute", isAudioMute);
                    bundle.putBoolean("isTTSError", tts == null);
                    bundle.putBoolean("isEditTextOpen", isEditTextOpen);
                    bundle.putBoolean("isBluetoothHeadsetConnected", isBluetoothHeadsetConnected());
                    super.notifyToClient(bundle);
                    return true;
            }
            return false;
        }
        return true;
    }

    public void notifyMessage(GuiMessage message) {
        Bundle bundle = new Bundle();
        bundle.putInt("callback", ON_MESSAGE);
        bundle.putParcelable("message", message);
        super.notifyToClient(bundle);
    }

    protected void notifyVoiceStart() {
        Bundle bundle = new Bundle();
        bundle.clear();
        bundle.putInt("callback", ON_VOICE_STARTED);
        super.notifyToClient(bundle);
    }

    protected void notifyVoiceEnd() {
        Bundle bundle = new Bundle();
        bundle.clear();
        bundle.putInt("callback", ON_VOICE_ENDED);
        super.notifyToClient(bundle);
    }

    protected void notifyMicProgrammaticallyStarted() {
        Bundle bundle = new Bundle();
        bundle.clear();
        bundle.putInt("callback", ON_MIC_PROGRAMMATICALLY_STARTED);
        super.notifyToClient(bundle);
    }

    protected void notifyMicProgrammaticallyStopped() {
        Bundle bundle = new Bundle();
        bundle.clear();
        bundle.putInt("callback", ON_MIC_PROGRAMMATICALLY_STOPPED);
        super.notifyToClient(bundle);
    }

    public void notifyError(int[] reasons, long value) {
        super.notifyError(reasons, value);
        if (mVoiceRecorder != null) {
            mVoiceCallback.onVoiceEnd();
        }
    }

    // connection with clients
    public static abstract class VoiceTranslationServiceCommunicator extends ServiceCommunicator {
        private ArrayList<VoiceTranslationServiceCallback> clientCallbacks = new ArrayList<>();
        private ArrayList<AttributesListener> attributesListeners = new ArrayList<>();

        protected VoiceTranslationServiceCommunicator(int id) {
            super(id);
        }

        protected boolean executeCallback(int callback, Bundle data) {
            if (callback != -1) {
                switch (callback) {
                    case ON_ATTRIBUTES: {
                        ArrayList<GuiMessage> messages = data.getParcelableArrayList("messages");
                        boolean isMicMute = data.getBoolean("isMicMute");
                        boolean isAudioMute = data.getBoolean("isAudioMute");
                        boolean isTTSError = data.getBoolean("isTTSError");
                        boolean isEditTextOpen = data.getBoolean("isEditTextOpen");
                        boolean isBluetoothHeadsetConnected = data.getBoolean("isBluetoothHeadsetConnected");
                        while (attributesListeners.size() > 0) {
                            attributesListeners.remove(0).onSuccess(messages, isMicMute, isAudioMute, isTTSError, isEditTextOpen, isBluetoothHeadsetConnected);
                        }
                        return true;
                    }
                    case ON_VOICE_STARTED: {
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onVoiceStarted();
                        }
                        return true;
                    }
                    case ON_VOICE_ENDED: {
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onVoiceEnded();
                        }
                        return true;
                    }
                    case ON_MIC_PROGRAMMATICALLY_STARTED: {
                        for (int i = 0; i < clientCallbacks.size(); i++){
                            clientCallbacks.get(i).onMicProgrammaticallyStarted();
                        }
                        return true;
                    }
                    case ON_MIC_PROGRAMMATICALLY_STOPPED: {
                        for (int i = 0; i < clientCallbacks.size(); i++){
                            clientCallbacks.get(i).onMicProgrammaticallyStopped();
                        }
                        return true;
                    }
                    case ON_MESSAGE: {
                        GuiMessage message = data.getParcelable("message");
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onMessage(message);
                        }
                        return true;
                    }
                    case ON_CONNECTED_BLUETOOTH_HEADSET: {
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onBluetoothHeadsetConnected();
                        }
                        return true;
                    }
                    case ON_DISCONNECTED_BLUETOOTH_HEADSET: {
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onBluetoothHeadsetDisconnected();
                        }
                        return true;
                    }
                    case ON_ERROR: {
                        int[] reasons = data.getIntArray("reasons");
                        long value = data.getLong("value");
                        for (int i = 0; i < clientCallbacks.size(); i++) {
                            clientCallbacks.get(i).onError(reasons, value);
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void initializeCommunication(Messenger serviceMessenger) {
            super.initializeCommunication(serviceMessenger);
            // we send our serviceMessenger which the service will use to communicate with us
            Bundle bundle = new Bundle();
            bundle.putInt("command", INITIALIZE_COMMUNICATION);
            bundle.putParcelable("messenger", new Messenger(serviceHandler));
            super.sendToService(bundle);
        }

        // commands

        public void getAttributes(AttributesListener responseListener) {
            attributesListeners.add(responseListener);
            if (attributesListeners.size() == 1) {
                Bundle bundle = new Bundle();
                bundle.putInt("command", GET_ATTRIBUTES);
                super.sendToService(bundle);
            }
        }

        public void startMic() {
            Bundle bundle = new Bundle();
            bundle.putInt("command", START_MIC);
            super.sendToService(bundle);
        }

        public void stopMic(boolean permanent) {  //permanent=true se l' ha settato l' utente anzichè essere un automatismo
            Bundle bundle = new Bundle();
            bundle.putInt("command", STOP_MIC);
            bundle.putBoolean("permanent", permanent);
            super.sendToService(bundle);
        }

        public void startSound() {
            Bundle bundle = new Bundle();
            bundle.putInt("command", START_SOUND);
            super.sendToService(bundle);
        }

        public void stopSound() {
            Bundle bundle = new Bundle();
            bundle.putInt("command", STOP_SOUND);
            super.sendToService(bundle);
        }

        public void setEditTextOpen(boolean editTextOpen) {
            Bundle bundle = new Bundle();
            bundle.putInt("command", SET_EDIT_TEXT_OPEN);
            bundle.putBoolean("value", editTextOpen);
            super.sendToService(bundle);
        }

        public void receiveText(String text) {
            Bundle bundle = new Bundle();
            bundle.putInt("command", RECEIVE_TEXT);
            bundle.putString("text", text);
            super.sendToService(bundle);
        }

        public void addCallback(ServiceCallback callback) {
            clientCallbacks.add((VoiceTranslationServiceCallback) callback);
        }

        public int removeCallback(ServiceCallback callback) {
            clientCallbacks.remove(callback);
            return clientCallbacks.size();
        }
    }

    public static abstract class VoiceTranslationServiceCallback extends ServiceCallback {
        public void onVoiceStarted() {
        }

        public void onVoiceEnded() {
        }

        public void onMicProgrammaticallyStarted(){
        }

        public void onMicProgrammaticallyStopped(){
        }

        public void onMessage(GuiMessage message) {
        }

        public void onBluetoothHeadsetConnected() {
        }

        public void onBluetoothHeadsetDisconnected() {
        }
    }

    public interface AttributesListener {
        void onSuccess(ArrayList<GuiMessage> messages, boolean isMicMute, boolean isAudioMute, boolean isTTSError, boolean isEditTextOpen, boolean isBluetoothHeadsetConnected);
    }

    protected abstract class VoiceTranslationServiceRecognizerListener implements RecognizerListener {
        @Override
        public void onError(int[] reasons, long value) {
            notifyError(reasons, value);
        }
    }
}
