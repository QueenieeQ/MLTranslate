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

package nie.translator.MLTranslator.voice_translation._walkie_talkie_mode._walkie_talkie;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import java.util.ArrayList;

import nie.translator.MLTranslator.Global;
import nie.translator.MLTranslator.tools.CustomLocale;
import nie.translator.MLTranslator.tools.ErrorCodes;
import nie.translator.MLTranslator.tools.TTS;
import nie.translator.MLTranslator.tools.Tools;
import nie.translator.MLTranslator.tools.gui.messages.GuiMessage;
import nie.translator.MLTranslator.voice_translation.VoiceTranslationService;
import nie.translator.MLTranslator.bluetooth.Message;
import nie.translator.MLTranslator.bluetooth.Peer;
import nie.translator.MLTranslator.voice_translation.neural_networks.NeuralNetworkApiResult;
import nie.translator.MLTranslator.voice_translation.neural_networks.translation.Translator;
import nie.translator.MLTranslator.voice_translation.neural_networks.voice.Recognizer;
import nie.translator.MLTranslator.voice_translation.neural_networks.voice.RecognizerMultiListener;
import nie.translator.MLTranslator.voice_translation.neural_networks.voice.Recorder;


public class WalkieTalkieService extends VoiceTranslationService {
    //properties
    public static final int SPEECH_BEAM_SIZE = 2;
    public static final int TRANSLATOR_BEAM_SIZE = 1;

    // commands
    public static final int CHANGE_FIRST_LANGUAGE = 22;
    public static final int CHANGE_SECOND_LANGUAGE = 23;
    public static final int GET_FIRST_LANGUAGE = 24;
    public static final int GET_SECOND_LANGUAGE = 25;

    // callbacks
    public static final int ON_FIRST_LANGUAGE = 22;
    public static final int ON_SECOND_LANGUAGE = 23;
    private RecognizerMultiListener speechRecognizerCallback;

    // objects
    private Translator translator;
    private Recognizer speechRecognizer;
    private CustomLocale firstLanguage;
    private CustomLocale secondLanguage;
    private Translator.TranslateListener firstResultTranslateListener;
    private Translator.TranslateListener secondResultTranslateListener;


    @Override
    public void onCreate() {
        super.onCreate();
        translator = ((Global) getApplication()).getTranslator();
        speechRecognizer = ((Global) getApplication()).getSpeechRecognizer();
        clientHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(android.os.Message message) {
                int command = message.getData().getInt("command", -1);
                if (command != -1) {
                    if (!WalkieTalkieService.super.executeCommand(command, message.getData())) {
                        switch (command) {
                            case CHANGE_FIRST_LANGUAGE:
                                CustomLocale newFirstLanguage = (CustomLocale) message.getData().getSerializable("language");
                                if (!firstLanguage.equals(newFirstLanguage)) {
                                    firstLanguage = newFirstLanguage;
                                }
                                break;
                            case CHANGE_SECOND_LANGUAGE:
                                CustomLocale newSecondLanguage = (CustomLocale) message.getData().getSerializable("language");
                                if (!secondLanguage.equals(newSecondLanguage)) {
                                    secondLanguage = newSecondLanguage;
                                }
                                break;
                            case GET_FIRST_LANGUAGE:
                                Bundle bundle = new Bundle();
                                bundle.putInt("callback", ON_FIRST_LANGUAGE);
                                bundle.putSerializable("language", firstLanguage);
                                WalkieTalkieService.super.notifyToClient(bundle);
                                break;
                            case GET_SECOND_LANGUAGE:
                                Bundle bundle2 = new Bundle();
                                bundle2.putInt("callback", ON_SECOND_LANGUAGE);
                                bundle2.putSerializable("language", secondLanguage);
                                WalkieTalkieService.super.notifyToClient(bundle2);
                                break;
                            case RECEIVE_TEXT:
                                String text = message.getData().getString("text", null);
                                if (text != null) {
                                    //we stop speech recognition
                                    stopVoiceRecorder();
                                    notifyMicProgrammaticallyStopped();   // we notify the client
                                    translator.detectLanguage(new NeuralNetworkApiResult(text), true, new Translator.DetectLanguageListener() {
                                        @Override
                                        public void onDetectedText(final NeuralNetworkApiResult result) {
                                            // here the result returns with the same language as the text sent from the Fragment editText
                                            if (result.getLanguage().equalsLanguage(firstLanguage)) {
                                                translator.translate(result.getText(), firstLanguage, secondLanguage, TRANSLATOR_BEAM_SIZE, false, firstResultTranslateListener);
                                            } else {
                                                translator.translate(result.getText(), secondLanguage, firstLanguage, TRANSLATOR_BEAM_SIZE, false, secondResultTranslateListener);
                                            }
                                        }

                                        @Override
                                        public void onFailure(int[] reasons, long value) {
                                            WalkieTalkieService.super.notifyError(reasons,value);
                                        }
                                    });
                                }
                                break;
                        }
                    }
                }
                return false;
            }
        });
        mVoiceCallback = new Recorder.SimpleCallback() {
            @Override
            public void onVoiceStart() {
                super.onVoiceStart();
                // we notify the client
                WalkieTalkieService.super.notifyVoiceStart();
            }

            @Override
            public void onVoice(@NonNull float[] data, int size) {
                super.onVoice(data,size);
                //we stop speech recognition
                stopVoiceRecorder();
                notifyMicProgrammaticallyStopped();   // we notify the client
                // we start the speech recognition in both languages
                speechRecognizer.recognize(data, SPEECH_BEAM_SIZE, firstLanguage.getCode(), secondLanguage.getCode());
            }

            @Override
            public void onVoiceEnd() {
                super.onVoiceEnd();
                // we notify the client
                WalkieTalkieService.super.notifyVoiceEnd();
            }
        };
        speechRecognizerCallback = new RecognizerMultiListener() {
            @Override
            public void onSpeechRecognizedResult(String text1, String languageCode1, double confidenceScore1, String text2, String languageCode2, double confidenceScore2) {
                NeuralNetworkApiResult firstResult = new NeuralNetworkApiResult(text1, confidenceScore1, true);
                NeuralNetworkApiResult secondResult = new NeuralNetworkApiResult(text2, confidenceScore2, true);
                compareResults(firstResult, secondResult);
            }

            @Override
            public void onError(int[] reasons, long value) {

                //we restart the mic here
                startVoiceRecorder();
                notifyMicProgrammaticallyStarted();
            }
        };
        firstResultTranslateListener = new Translator.TranslateListener() {
            @Override
            public void onTranslatedText(String text, long resultID, boolean isFinal, CustomLocale languageOfText) {
                ((Global) getApplication()).getTTSLanguages(true, new Global.GetLocalesListListener() {
                    @Override
                    public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                        if(isFinal && CustomLocale.containsLanguage(ttsLanguages, languageOfText)) { // check if the text can be speak
                            speak(text, languageOfText);
                        }
                        GuiMessage message = new GuiMessage(new Message(WalkieTalkieService.this, text), resultID, true, isFinal);
                        WalkieTalkieService.super.notifyMessage(message);
                        // we save every new message in the exchanged messages so that the fragment can restore them
                        WalkieTalkieService.super.addOrUpdateMessage(message);
                        //if the tts is not active we restart the mic here
                        if(tts == null || !CustomLocale.containsLanguage(ttsLanguages, languageOfText) || !tts.isActive() || isAudioMute){
                            startVoiceRecorder();
                            notifyMicProgrammaticallyStarted();
                        }
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        //never called in this case
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                WalkieTalkieService.super.notifyError(reasons,value);
                //if the tts is not active we restart the mic here
                if(tts == null || !tts.isActive() || isAudioMute){
                    startVoiceRecorder();
                    notifyMicProgrammaticallyStarted();
                }
            }
        };
        secondResultTranslateListener = new Translator.TranslateListener() {
            @Override
            public void onTranslatedText(String text, long resultID, boolean isFinal, CustomLocale languageOfText) {
                ((Global) getApplication()).getTTSLanguages(true, new Global.GetLocalesListListener() {
                    @Override
                    public void onSuccess(ArrayList<CustomLocale> ttsLanguages) {
                        if(isFinal && CustomLocale.containsLanguage(ttsLanguages, languageOfText)) { // check if the text can be speak
                            speak(text, languageOfText);
                        }
                        GuiMessage message = new GuiMessage(new Message(WalkieTalkieService.this, text), resultID, false, isFinal);
                        WalkieTalkieService.super.notifyMessage(message);
                        // we save every new message in the exchanged messages so that the fragment can restore them
                        WalkieTalkieService.super.addOrUpdateMessage(message);
                        //if the tts is not active we restart the mic here
                        if(tts == null || !CustomLocale.containsLanguage(ttsLanguages, languageOfText) || !tts.isActive() || isAudioMute){
                            startVoiceRecorder();
                            notifyMicProgrammaticallyStarted();
                        }
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        //never called in this case
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                WalkieTalkieService.super.notifyError(reasons,value);
                //if the tts is not active we restart the mic here
                if(tts == null || !tts.isActive() || isAudioMute){
                    startVoiceRecorder();
                    notifyMicProgrammaticallyStarted();
                }
            }
        };
        //voice recorder initialization
        initializeVoiceRecorder();
    }

    public void initializeVoiceRecorder(){
        if (Tools.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            //voice recorder initialization
            super.mVoiceRecorder = new Recorder((Global) getApplication(), false, mVoiceCallback, null);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final CustomLocale finalFirstLanguage = this.firstLanguage;
        final CustomLocale finalSecondLanguage = this.secondLanguage;

        //getGroup the languages
        firstLanguage = (CustomLocale) intent.getSerializableExtra("firstLanguage");
        secondLanguage = (CustomLocale) intent.getSerializableExtra("secondLanguage");

        if(finalFirstLanguage==null || finalSecondLanguage==null ) {  //se è il primo avvio
            //we attach the speech recognition callback
            speechRecognizer.addMultiCallback(speechRecognizerCallback);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        //disconnect speechRecognizerCallback
        speechRecognizer.removeMultiCallback(speechRecognizerCallback);
        super.onDestroy();
    }

    private void compareResults(NeuralNetworkApiResult firstResult, NeuralNetworkApiResult secondResult) {
        translator.detectLanguage(firstResult, secondResult, false, new Translator.DetectMultiLanguageListener() {
            @Override
            public void onDetectedText(NeuralNetworkApiResult firstResult, NeuralNetworkApiResult secondResult, int message) {
                if (message == ErrorCodes.BOTH_RESULTS_SUCCESS){  // if both results languages were found
                    if(firstResult.getText().equals(Recognizer.UNDEFINED_TEXT) && !secondResult.getText().equals(Recognizer.UNDEFINED_TEXT)){
                        translate(secondResult.getText(), secondLanguage, firstLanguage, TRANSLATOR_BEAM_SIZE, false, secondResultTranslateListener);
                        return;
                    }
                    if(secondResult.getText().equals(Recognizer.UNDEFINED_TEXT) && !firstResult.getText().equals(Recognizer.UNDEFINED_TEXT)){
                        translate(firstResult.getText(), firstLanguage, secondLanguage, TRANSLATOR_BEAM_SIZE, false, firstResultTranslateListener);
                        return;
                    }

                    if (firstResult.getLanguage().equalsLanguage(firstLanguage)) {
                        if (secondResult.getLanguage().equalsLanguage(secondLanguage)) {  // if both have recognized their respective language
                            compareResultsConfidence(firstResult, secondResult);
                        } else {  // if only the first result language is recognized
                            translate(firstResult.getText(), firstLanguage, secondLanguage, TRANSLATOR_BEAM_SIZE, false, firstResultTranslateListener);
                        }
                    } else {
                        if (secondResult.getLanguage().equalsLanguage(secondLanguage)) {  // if only the second result language is recognized
                            translate(secondResult.getText(), secondLanguage, firstLanguage, TRANSLATOR_BEAM_SIZE, false, secondResultTranslateListener);
                        } else {  // if neither of them recognized his language but another (or none)
                            compareResultsConfidence(firstResult, secondResult);
                        }
                    }
                }else if(message == ErrorCodes.SECOND_RESULT_FAIL){  // if only the first result language was found
                    translate(firstResult.getText(), firstLanguage, secondLanguage, TRANSLATOR_BEAM_SIZE, false, firstResultTranslateListener);
                }else if(message == ErrorCodes.FIRST_RESULT_FAIL){   // if only the second result language was found
                    translate(secondResult.getText(), secondLanguage, firstLanguage, TRANSLATOR_BEAM_SIZE, false, secondResultTranslateListener);
                }
                // if both results languages were not found the onFailure method is called
            }

            @Override
            public void onFailure(int[] reasons, long value) {  // if both results languages were not found
                compareResultsConfidence(firstResult, secondResult);
            }
        });
    }

    private void translate(final String textToTranslate, final CustomLocale languageInput, final CustomLocale languageOutput, int beamSize, boolean saveResults, final Translator.TranslateListener responseListener){
        if(!isMetaText(textToTranslate)) {
            translator.translate(textToTranslate, languageInput, languageOutput, beamSize, saveResults, responseListener);
        }else{
            //we restart the mic here
            startVoiceRecorder();
            notifyMicProgrammaticallyStarted();
        }
    }

    private void compareResultsConfidence(NeuralNetworkApiResult firstResult, NeuralNetworkApiResult secondResult) {
        if (firstResult.getConfidenceScore() >= secondResult.getConfidenceScore()) {
            translate(firstResult.getText(), firstLanguage, secondLanguage, TRANSLATOR_BEAM_SIZE, false, firstResultTranslateListener);
        } else {
            translate(secondResult.getText(), secondLanguage, firstLanguage, TRANSLATOR_BEAM_SIZE, false, secondResultTranslateListener);
        }
    }

    public static class WalkieTalkieServiceCommunicator extends VoiceTranslationServiceCommunicator {
        private ArrayList<LanguageListener> firstLanguageListeners = new ArrayList<>();
        private ArrayList<LanguageListener> secondLanguageListeners = new ArrayList<>();

        public WalkieTalkieServiceCommunicator(int id) {
            super(id);
            super.serviceHandler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(android.os.Message msg) {
                    msg.getData().setClassLoader(Peer.class.getClassLoader());
                    int callbackMessage = msg.getData().getInt("callback", -1);
                    Bundle data= msg.getData();
                    if(!executeCallback(callbackMessage,data)){
                        switch (callbackMessage){
                            case ON_FIRST_LANGUAGE:{
                                CustomLocale language = (CustomLocale) data.getSerializable("language");
                                while (firstLanguageListeners.size() > 0) {
                                    firstLanguageListeners.remove(0).onLanguage(language);
                                }
                                break;
                            }
                            case ON_SECOND_LANGUAGE:{
                                CustomLocale language = (CustomLocale) data.getSerializable("language");
                                while (secondLanguageListeners.size() > 0) {
                                    secondLanguageListeners.remove(0).onLanguage(language);
                                }
                                break;
                            }
                        }
                    }
                    return true;
                }
            });
        }

        public void getFirstLanguage(LanguageListener responseListener){
            firstLanguageListeners.add(responseListener);
            if (firstLanguageListeners.size() == 1) {
                Bundle bundle = new Bundle();
                bundle.putInt("command", GET_FIRST_LANGUAGE);
                super.sendToService(bundle);
            }
        }

        public void getSecondLanguage(LanguageListener responseListener){
            secondLanguageListeners.add(responseListener);
            if (secondLanguageListeners.size() == 1) {
                Bundle bundle = new Bundle();
                bundle.putInt("command", GET_SECOND_LANGUAGE);
                super.sendToService(bundle);
            }
        }

        public void changeFirstLanguage(CustomLocale language) {
            Bundle bundle = new Bundle();
            bundle.putInt("command", WalkieTalkieService.CHANGE_FIRST_LANGUAGE);
            bundle.putSerializable("language", language);
            super.sendToService(bundle);
        }

        public void changeSecondLanguage(CustomLocale language) {
            Bundle bundle = new Bundle();
            bundle.putInt("command", WalkieTalkieService.CHANGE_SECOND_LANGUAGE);
            bundle.putSerializable("language", language);
            super.sendToService(bundle);
        }
    }

    public interface LanguageListener{
        void onLanguage(CustomLocale language);
    }
}