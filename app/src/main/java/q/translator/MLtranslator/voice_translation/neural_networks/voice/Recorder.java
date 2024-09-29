/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package nie.translator.MLTranslator.voice_translation.neural_networks.voice;

import static android.media.AudioManager.GET_DEVICES_INPUTS;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import nie.translator.MLTranslator.Global;
import nie.translator.MLTranslator.voice_translation._conversation_mode._conversation.ConversationService;


/**
 * Continuously records audio and notifies the {@link Recorder.Callback} when voice (or any
 * sound) is heard. Furthermore, when it calls the onVoice method, it passes a buffer obtained from the AudioRecord,
 * and the class that implements the onVoice method (BaseActivity) will perform the recognition of google precisely using the buffer passed,
 * consequently the input to the API of google comes from the AudioRecord of this class.
 *
 *
 * <p>The recorded audio format is always {@link AudioFormat#ENCODING_PCM_16BIT} and
 * {@link AudioFormat#CHANNEL_IN_MONO}. This class will automatically pick the right sample rate
 * for the device. Use {@link #getSampleRate()} to getGroup the selected value.</p>
 */
public class Recorder {
    private final Global global;
    private boolean isRecording;
    public static final int[] SAMPLE_RATE_CANDIDATES = new int[]{16000};
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_FLOAT;   //original: AudioFormat.ENCODING_PCM_16BIT
    public static final int MAX_AMPLITUDE_THRESHOLD = 15000;
    public static final int DEFAULT_AMPLITUDE_THRESHOLD = 2000; //original: 1500
    public static final int MIN_AMPLITUDE_THRESHOLD = 400;
    public static final int MAX_SPEECH_TIMEOUT_MILLIS = 5000;
    public static final int DEFAULT_SPEECH_TIMEOUT_MILLIS = 1300; //original: 2000
    public static final int MIN_SPEECH_TIMEOUT_MILLIS = 100;
    public static final int MAX_PREV_VOICE_DURATION = 1800;
    public static final int DEFAULT_PREV_VOICE_DURATION = 1300;
    public static final int MIN_PREV_VOICE_DURATION = 100;
    private static final int MAX_SPEECH_LENGTH_MILLIS = 29 * 1000; //original: 30 * 1000
    private final Callback mCallback;
    private int sampleRate;
    @Nullable
    private final AudioRecord mAudioRecord;
    private Thread mThread;
    private int mPrevBufferMaxSize;   //the size of the mPrevBuffer (It depends on the settings of the app (prevVoiceDuration))
    private float[] mBuffer;
    private int readSize;   //must be smaller than mBuffer.length or the circular mBuffer array will not work
    private int headIndex;
    private int tailIndex;
    private int startVoiceIndex;
    /**
     * The timestamp of the last time that voice is heard.
     */
    private long mLastVoiceHeardMillis = Long.MAX_VALUE;
    /**
     * The timestamp when the current voice is started.
     */
    private long mVoiceStartedMillis;

    private final boolean useBluetoothHeadset;
    private AudioDeviceInfo connectedBleHeadset = null;
    private AudioDeviceCallback audioDeviceCallback;
    AudioManager audioManager;


    public Recorder(Global global, boolean useBluetoothHeadset, @NonNull Callback callback, @Nullable ConversationService.BluetoothHeadsetCallback bluetoothHeadsetCallback) {
        this.useBluetoothHeadset = useBluetoothHeadset;
        this.global = global;
        headIndex = 0;
        tailIndex = 0;
        global.getMicSensitivity();
        global.getSpeechTimeout();
        global.getPrevVoiceDuration();
        mCallback = callback;
        mCallback.setRecorder(this);

        // Try to create a new recording session.
        mAudioRecord = createAudioRecord();
        if (mAudioRecord == null) {
            //throw new RuntimeException("Cannot instantiate Recorder");
            Log.e("Recorder error", "Cannot instantiate Recorder");
        }

        //initialize the bluetooth headset mic management
        if(useBluetoothHeadset) {
            this.audioManager = (AudioManager) global.getSystemService(Context.AUDIO_SERVICE);
            boolean success = setBLEHeadsetConnection();
            if(success) {
                if (bluetoothHeadsetCallback != null) {
                    bluetoothHeadsetCallback.onScoAudioConnected();
                }
            }
            this.audioDeviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    if(connectedBleHeadset == null) {
                        boolean success = setBLEHeadsetConnection();
                        if (success) {
                            if (bluetoothHeadsetCallback != null) {
                                bluetoothHeadsetCallback.onScoAudioConnected();
                            }
                        }
                    }
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    boolean found = false;
                    for (AudioDeviceInfo removedDevice : removedDevices) {
                        if (removedDevice.equals(connectedBleHeadset)){
                            found = true;
                            break;
                        }
                    }
                    if(found) {
                        connectedBleHeadset = null;
                        audioManager.stopBluetoothSco();
                        if (bluetoothHeadsetCallback != null) {
                            bluetoothHeadsetCallback.onScoAudioDisconnected();
                        }
                    }
                }
            };
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null);
        }
    }

    /**
     * Starts recording audio.
     *
     * <p>The caller is responsible for calling {@link #stop()} later.</p>
     */
    public void start() {
        // Stop recording if it is currently ongoing.
        stop();
        // Start recording.
        if(mAudioRecord != null) {
            mAudioRecord.startRecording();  // here doesn't work with callback
        }
        // Start processing the captured audio.
        mThread = new Thread(new ProcessVoice(), "processVoice");
        mThread.start();
    }

    /**
     * Stops recording audio.
     */
    public void stop() {
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
        if(mAudioRecord != null) {
            mAudioRecord.stop();
        }
        //mBuffer = null;
        dismiss();
        headIndex = 0;
        tailIndex = 0;
    }

    /**
     * Dismisses the currently ongoing utterance.
     */
    public void dismiss() {
        if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            //mCallback.onVoiceEnd();
        }
    }

    public void end() {
        //convert the relevant portion of the circular mBuffer to a normal array
        int voiceLength = getMBufferRangeSize(startVoiceIndex, tailIndex);
        float[] data = new float[voiceLength];
        int circularIndex = startVoiceIndex;
        for(int i=0; i<voiceLength; i++){
            data[i] = mBuffer[circularIndex];
            if (circularIndex < mBuffer.length-1){
                circularIndex++;
            }else{
                circularIndex = 0;
            }
        }
        mCallback.onVoice(data, voiceLength);
        //reset relevant variables
        startVoiceIndex = 0;  //is not necessary
        mLastVoiceHeardMillis = Long.MAX_VALUE;
        mCallback.onVoiceEnd();
    }

    public void destroy(){
        if(useBluetoothHeadset) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
            if(connectedBleHeadset != null){
                audioManager.stopBluetoothSco();
            }
        }
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            //mAudioRecord = null;
        }
    }

    /**
     * Retrieves the sample rate currently used to record audio.
     *
     * @return The sample rate of recorded audio.
     */
    public int getSampleRate() {
        if (mAudioRecord != null) {
            return mAudioRecord.getSampleRate();
        }
        return 0;
    }

    /**
     * Creates a new {@link AudioRecord}.
     *
     * @return A newly created {@link AudioRecord}, or null if it cannot be created (missing
     * permissions?).
     */
    private AudioRecord createAudioRecord() {
        for (int sampleRate : SAMPLE_RATE_CANDIDATES) {
            final int minSizeInBytes = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING);
            if (minSizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                continue;
            }
            this.sampleRate = sampleRate;
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, CHANNEL, ENCODING, minSizeInBytes);   //the option MIC produce better result than the option VOICE_RECOGNITION
            //audioRecord.setPreferredDevice()
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                readSize = (minSizeInBytes/4)*2;
                mBuffer = new float[((MAX_SPEECH_LENGTH_MILLIS+1000)/1000)*sampleRate];  //the buffer size will be larger (by one second) than the audio data of duration MAX_SPEECH_LENGTH_MILLIS
                return audioRecord;
            } else {
                audioRecord.release();
            }
        }
        return null;
    }

    /**
     * Continuously processes the captured audio and notifies {@link #mCallback} of corresponding
     * events.
     * Always call the isHearing voice method and if it returns true and the time span from the last listening of the voice is greater than a tot (MAX_VALUE)
     * then call the onVoiceStarted method and then onVoice, otherwise only onVoice.
     */
    private class ProcessVoice implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                if(mAudioRecord != null) {
                    int prevVoiceLength = (global.getPrevVoiceDuration() / 1000) * sampleRate;
                    int size;
                    int oldTailIndex = tailIndex;
                    boolean jumped;
                    if (tailIndex + readSize < mBuffer.length) {
                        size = mAudioRecord.read(mBuffer, tailIndex, readSize, AudioRecord.READ_BLOCKING);
                        tailIndex = tailIndex + size;
                        jumped = false;
                    } else {
                        size = mAudioRecord.read(mBuffer, tailIndex, mBuffer.length - tailIndex, AudioRecord.READ_BLOCKING);
                        tailIndex = 0;
                        int size2 = mAudioRecord.read(mBuffer, tailIndex, readSize - size, AudioRecord.READ_BLOCKING);
                        tailIndex = size2;
                        size = size + size2;
                        jumped = true;
                    }
                    if ((oldTailIndex < headIndex && tailIndex > headIndex) || (oldTailIndex > headIndex && tailIndex > headIndex && jumped)) {  //if we overwrote the oldest data
                        headIndex = tailIndex + 1;  //we adjust the headIndex accordingly
                    }
                    final long now = System.currentTimeMillis();
                    if (isHearingVoice(mBuffer, oldTailIndex, tailIndex)) {
                        if (mLastVoiceHeardMillis == Long.MAX_VALUE) {    // use Long's maximum limit to indicate that we have no voice
                            mVoiceStartedMillis = now;
                            mCallback.onVoiceStart();
                            if (getMBufferSize() > prevVoiceLength) {
                                if (tailIndex - prevVoiceLength >= 0) {
                                    startVoiceIndex = tailIndex - prevVoiceLength;
                                } else {
                                    startVoiceIndex = mBuffer.length + (tailIndex - prevVoiceLength);  //we do a jump
                                }
                            } else {
                                startVoiceIndex = headIndex;
                            }
                        }
                        mLastVoiceHeardMillis = now;
                        if (now - (mVoiceStartedMillis - global.getPrevVoiceDuration()) > MAX_SPEECH_LENGTH_MILLIS) {
                            end();
                        }
                    } else if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
                        if (now - mLastVoiceHeardMillis > global.getSpeechTimeout()) {
                            end();
                        }
                    }
                }
            }
        }
    }

    private int getMBufferSize(){
        return getMBufferRangeSize(headIndex, tailIndex);
    }

    private int getMBufferRangeSize(int begin, int end){
        if(begin <= end){
            return end - begin;
        }else{    //(begin > end)
            return (mBuffer.length-begin) + end;
        }
    }

    private boolean isHearingVoice(byte[] buffer, int size) {   //old method to measure threshold (not used)
        for (int i = 0; i < size - 1; i += 2) {
            // The buffer has LINEAR16 (2 bytes) in little endian.
            // Therefore, to take the integer value at position i, we convert the (i+1)-th byte into an integer (positive),
            // shift it to the left by 8 bits and add to it the absolute integer value of the i-th byte
            int s = buffer[i + 1];
            if (s < 0) s *= -1;
            s <<= 8;
            s += Math.abs(buffer[i]);
            //if the value is grater than the threshold the method returns true
            int amplitudeThreshold = global.getAmplitudeThreshold();
            if (s > amplitudeThreshold) {
                return true;
            }
        }
        return false;
    }

    private boolean isHearingVoice(float[] buffer, int begin, int end) {
        // We iterate circlularly the mBuffer from the begin index to the end index, and if one of the values exceed the threshold the method returns true.
        // Also The range with the old ENCODING_PCM_16BIT was [-32768, 32767], while now with the new ENCODING_PCM_FLOAT it is [-1, 1],
        // so to convert the values of the new range into those of the old range (the threshold is based on the old values) I have to multiply them by 32767.
        int numberOfThreshold = 15;
        int count = begin;
        while (count != end){
            float s = Math.abs(buffer[count]) * 32767;
            int amplitudeThreshold = global.getAmplitudeThreshold();
            if (s > amplitudeThreshold) {
                numberOfThreshold--;
                //return true;
            }
            if (count < buffer.length-1){
                count++;
            }else{
                count = 0;
            }
        }
        if(numberOfThreshold <= 0){
            return true;
        }else{
            return false;
        }
        //return false;
    }

    public boolean isOnHeadsetSco(){
        return connectedBleHeadset != null;
    }

    private boolean setBLEHeadsetConnection(){
        AudioDeviceInfo[] allDeviceInfo = audioManager.getDevices(GET_DEVICES_INPUTS);
        for (AudioDeviceInfo device : allDeviceInfo) {
            int deviceType = device.getType();
            if (deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                if (mAudioRecord != null) {
                    audioManager.startBluetoothSco();
                    connectedBleHeadset = device;
                }
                return true;
            }
            if(deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET || deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP){  //untested
                if(mAudioRecord != null) {
                    boolean success = mAudioRecord.setPreferredDevice(device);
                    if (success) {
                        connectedBleHeadset = device;
                        return true;
                    }
                }
            }
        }
        return false;
    }


    public static abstract class Callback {
        private Recorder recorder;

        void setRecorder(Recorder recorder) {
            this.recorder = recorder;
        }

        /**
         * Called when the recorder starts hearing voice.
         */
        public void onVoiceStart() {
            if (recorder != null) {
                recorder.isRecording = true;
            }
            Log.e("recorder","onVoiceStart");
        }

        /**
         * Called when the recorder is hearing voice.
         *
         * @param data The audio data in {@link AudioFormat#ENCODING_PCM_16BIT}.
         * @param size The peersSize of the actual data in {@code data}.
         */
        public void onVoice(@NonNull float[] data, int size) {
            Log.e("recorder","onVoice");
        }

        /**
         * Called when the recorder stops hearing voice.
         */
        public void onVoiceEnd() {
            if (recorder != null) {
                recorder.isRecording = false;
            }
            Log.e("recorder","onVoiceEnd");
        }
    }

    public static abstract class SimpleCallback extends Callback {
        @Override
        void setRecorder(Recorder recorder) {
            super.setRecorder(recorder);
        }

        @Override
        public void onVoiceStart() {
        }

        @Override
        public void onVoice(@NonNull float[] data, int size) {
        }

        @Override
        public void onVoiceEnd() {
        }
    }
}
