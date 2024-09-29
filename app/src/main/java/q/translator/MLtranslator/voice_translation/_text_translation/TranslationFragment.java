package nie.translator.MLTranslator.voice_translation._text_translation;

import android.animation.Animator;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

import nie.translator.MLTranslator.Global;
import nie.translator.MLTranslator.R;
import nie.translator.MLTranslator.bluetooth.Message;
import nie.translator.MLTranslator.tools.CustomLocale;
import nie.translator.MLTranslator.tools.ErrorCodes;
import nie.translator.MLTranslator.tools.Tools;
import nie.translator.MLTranslator.tools.gui.AnimatedTextView;
import nie.translator.MLTranslator.tools.gui.GuiTools;
import nie.translator.MLTranslator.tools.gui.LanguageListAdapter;
import nie.translator.MLTranslator.tools.gui.animations.CustomAnimator;
import nie.translator.MLTranslator.tools.gui.messages.GuiMessage;
import nie.translator.MLTranslator.voice_translation.VoiceTranslationActivity;
import nie.translator.MLTranslator.voice_translation.neural_networks.translation.Translator;

public class TranslationFragment extends Fragment {
    private VoiceTranslationActivity activity;
    private Global global;
    private Translator.TranslateListener translateListener;
    private TextWatcher textWatcher;

    //TranslatorFragment's GUI
    private MaterialButton translateButton;
    private FloatingActionButton walkieTalkieButton;
    private FloatingActionButton conversationButton;
    private EditText inputText;
    private TextView outputText;
    private LinearLayout firstLanguageSelector;
    private LinearLayout secondLanguageSelector;
    private AppCompatImageButton invertLanguagesButton;
    private CustomAnimator animator = new CustomAnimator();
    private Animator colorAnimator = null;
    private int activatedColor = R.color.primary_dark;
    private int deactivatedColor = R.color.gray;

    //languageListDialog
    private LanguageListAdapter listView;
    private ListView listViewGui;
    private ProgressBar progressBar;
    private ImageButton reloadButton;
    private AlertDialog dialog;
    public static final int BEAM_SIZE = 1;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_translation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        firstLanguageSelector = view.findViewById(R.id.firstLanguageSelector);
        secondLanguageSelector = view.findViewById(R.id.secondLanguageSelector);
        invertLanguagesButton = view.findViewById(R.id.invertLanguages);
        translateButton = view.findViewById(R.id.buttonTranslate);
        walkieTalkieButton = view.findViewById(R.id.button);
        conversationButton = view.findViewById(R.id.button2);
        inputText = view.findViewById(R.id.multiAutoCompleteTextView);
        outputText = view.findViewById(R.id.multiAutoCompleteTextView2);
        //outputText.setMovementMethod(new ScrollingMovementMethod());
    }

    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (VoiceTranslationActivity) requireActivity();
        global = (Global) activity.getApplication();
        Toolbar toolbar = activity.findViewById(R.id.toolbaMLTranslator);
        activity.setActionBar(toolbar);
        // setting of the selected languages
        global.getFirstLanguage(true, new Global.GetLocaleListener() {
            @Override
            public void onSuccess(CustomLocale result) {
                setFirstLanguage(result);
            }
            @Override
            public void onFailure(int[] reasons, long value) {

            }
        });
        global.getSecondLanguage(true, new Global.GetLocaleListener() {
            @Override
            public void onSuccess(CustomLocale result) {
                setSecondLanguage(result);
            }
            @Override
            public void onFailure(int[] reasons, long value) {

            }
        });
        walkieTalkieButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                activity.setFragment(VoiceTranslationActivity.WALKIE_TALKIE_FRAGMENT);
            }
        });
        conversationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                activity.setFragment(VoiceTranslationActivity.PAIRING_FRAGMENT);
            }
        });
        translateListener = new Translator.TranslateListener() {
            @Override
            public void onTranslatedText(String text, long resultID, boolean isFinal, CustomLocale languageOfText) {
                outputText.setText(text);
                if(isFinal){
                    activateTranslationButton();
                }
            }

            @Override
            public void onFailure(int[] reasons, long value) {

            }
        };
        translateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = inputText.getText().toString();

                /*if(text.length() <= 0){   //test code
                    text = "Also unlike 2014, there aren’t nearly as many loopholes. You can’t just buy a 150-watt incandescent or a three-way bulb, the ban covers any normal bulb that generates less than 45 lumens per watt, which pretty much rules out both incandescent and halogen tech in their entirety.";
                    inputText.setText(text);
                }*/

                if(!text.isEmpty()) {
                    String finalText = text;
                    global.getFirstAndSecondLanguages(true, new Global.GetTwoLocaleListener() {
                        @Override
                        public void onSuccess(CustomLocale firstLanguage, CustomLocale secondLanguage) {
                            //we deactivate translate button
                            deactivateTranslationButton();
                            //we start the translation
                            global.getTranslator().translate(finalText, firstLanguage, secondLanguage, BEAM_SIZE, true);
                        }

                        @Override
                        public void onFailure(int[] reasons, long value) {

                        }
                    });
                }
            }
        });
    }

    public void onStart() {
        super.onStart();
        GuiMessage lastInputText = global.getTranslator().getLastInputText();
        GuiMessage lastOutputText = global.getTranslator().getLastOutputText();
        //we restore the last input and output text
        if(lastInputText != null){
            inputText.setText(lastInputText.getMessage().getText());
        }
        if(lastOutputText != null){
            outputText.setText(lastOutputText.getMessage().getText());
        }
        //we attach the translate listener
        global.getTranslator().addCallback(translateListener);
        //we attach the click listener for the language selectors
        firstLanguageSelector.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLanguageListDialog(1);
            }
        });
        secondLanguageSelector.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLanguageListDialog(2);
            }
        });
        invertLanguagesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                global.getFirstAndSecondLanguages(true, new Global.GetTwoLocaleListener() {
                    @Override
                    public void onSuccess(CustomLocale language1, CustomLocale language2) {
                        setFirstLanguage(language2);
                        setSecondLanguage(language1);
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {

                    }
                });
            }
        });
        textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if(global.getTranslator() != null){
                    global.getTranslator().setLastInputText(new GuiMessage(new Message(global, s.toString()), true, true));
                }
            }
        };
        inputText.addTextChangedListener(textWatcher);
        //we set the option to not compress ui when the keyboard is shown
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        //we restore the translation button state based on the translation status
        if(global.getTranslator().isTranslating()){
            deactivateTranslationButton();
        }else{
            activateTranslationButton();
        }
    }

    private void activateTranslationButton(){
        if(colorAnimator != null){
            colorAnimator.cancel();
        }
        if(!translateButton.isActivated()) {
            colorAnimator = animator.createAnimatorColor(translateButton, GuiTools.getColorStateList(activity, deactivatedColor).getDefaultColor(), GuiTools.getColorStateList(activity, activatedColor).getDefaultColor(), activity.getResources().getInteger(R.integer.durationShort));
            colorAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(@NonNull Animator animation) {
                }

                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    translateButton.setActivated(true);
                    colorAnimator = null;
                }

                @Override
                public void onAnimationCancel(@NonNull Animator animation) {
                }

                @Override
                public void onAnimationRepeat(@NonNull Animator animation) {
                }
            });
            colorAnimator.start();
        }else{
            translateButton.setBackgroundColor(GuiTools.getColorStateList(activity, activatedColor).getDefaultColor());
        }
    }

    private void deactivateTranslationButton(){
        if(colorAnimator != null){
            colorAnimator.cancel();
        }
        if(translateButton.isActivated()) {
            translateButton.setActivated(false);
            colorAnimator = animator.createAnimatorColor(translateButton, GuiTools.getColorStateList(activity, activatedColor).getDefaultColor(), GuiTools.getColorStateList(activity, deactivatedColor).getDefaultColor(), activity.getResources().getInteger(R.integer.durationShort));
            colorAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(@NonNull Animator animation) {
                }

                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    colorAnimator = null;
                }

                @Override
                public void onAnimationCancel(@NonNull Animator animation) {
                }

                @Override
                public void onAnimationRepeat(@NonNull Animator animation) {
                }
            });
            colorAnimator.start();
        }else{
            translateButton.setBackgroundColor(GuiTools.getColorStateList(activity, deactivatedColor).getDefaultColor());
        }
    }

    private void showLanguageListDialog(final int languageNumber) {
        //when the dialog is shown at the beginning the loading is shown, then once the list of languages​is obtained (within the showList)
        //the loading is replaced with the list of languages
        String title = "";
        switch (languageNumber) {
            case 1: {
                title = global.getResources().getString(R.string.dialog_select_first_language);
                break;
            }
            case 2: {
                title = global.getResources().getString(R.string.dialog_select_second_language);
                break;
            }
        }

        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_languages, null);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(title);

        dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 16), 0, 0);
        dialog.show();

        listViewGui = editDialogLayout.findViewById(R.id.list_view_dialog);
        progressBar = editDialogLayout.findViewById(R.id.progressBar3);
        reloadButton = editDialogLayout.findViewById(R.id.reloadButton);

        Global.GetLocaleListener listener = new Global.GetLocaleListener() {
            @Override
            public void onSuccess(final CustomLocale result) {
                reloadButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showList(languageNumber, result);
                    }
                });
                showList(languageNumber, result);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                onFailureShowingList(reasons, value);
            }
        };

        switch (languageNumber) {
            case 1: {
                global.getFirstLanguage(false, listener);
                break;
            }
            case 2: {
                global.getSecondLanguage(false, listener);
                break;
            }
        }
    }

    private void showList(final int languageNumber, final CustomLocale selectedLanguage) {
        reloadButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        global.getLanguages(true, true, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(final ArrayList<CustomLocale> languages) {
                progressBar.setVisibility(View.GONE);
                listViewGui.setVisibility(View.VISIBLE);

                listView = new LanguageListAdapter(activity, false, languages, selectedLanguage);
                listViewGui.setAdapter(listView);
                listViewGui.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                        if (languages.contains((CustomLocale) listView.getItem(position))) {
                            switch (languageNumber) {
                                case 1: {
                                    setFirstLanguage((CustomLocale) listView.getItem(position));
                                    break;
                                }
                                case 2: {
                                    setSecondLanguage((CustomLocale) listView.getItem(position));
                                    break;
                                }
                            }
                        }
                        dialog.dismiss();
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                onFailureShowingList(reasons, value);
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        firstLanguageSelector.setOnClickListener(null);
        secondLanguageSelector.setOnClickListener(null);
        invertLanguagesButton.setOnClickListener(null);
        inputText.removeTextChangedListener(textWatcher);
        //we detach the translate listener
        global.getTranslator().removeCallback(translateListener);
    }

    private void setFirstLanguage(CustomLocale language) {
        // save firstLanguage selected
        global.setFirstLanguage(language);
        // change language displayed
        ((AnimatedTextView) firstLanguageSelector.findViewById(R.id.firstLanguageName)).setText(language.getDisplayNameWithoutTTS(), true);
    }

    private void setSecondLanguage(CustomLocale language) {
        // save secondLanguage selected
        global.setSecondLanguage(language);
        // change language displayed
        ((AnimatedTextView) secondLanguageSelector.findViewById(R.id.secondLanguageName)).setText(language.getDisplayNameWithoutTTS(), true);
    }

    private void onFailureShowingList(int[] reasons, long value) {
        progressBar.setVisibility(View.GONE);
        reloadButton.setVisibility(View.VISIBLE);
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.MISSED_ARGUMENT:
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    Toast.makeText(activity, getResources().getString(R.string.error_internet_lack_loading_languages), Toast.LENGTH_LONG).show();
                    break;
                default:
                    activity.onError(aReason, value);
                    break;
            }
        }
    }
}
