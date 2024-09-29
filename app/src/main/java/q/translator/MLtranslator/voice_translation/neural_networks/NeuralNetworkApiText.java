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

package nie.translator.MLTranslator.voice_translation.neural_networks;

import java.io.Serializable;
import nie.translator.MLTranslator.tools.CustomLocale;

public class NeuralNetworkApiText implements Serializable {
    private String text;
    private CustomLocale language;

    public NeuralNetworkApiText(String text, CustomLocale language){
        this.text=text;
        this.language=language;
    }

    public NeuralNetworkApiText(String text){
        this.text=text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public CustomLocale getLanguage() {
        return language;
    }

    public void setLanguage(CustomLocale language) {
        this.language = language;
    }
}
