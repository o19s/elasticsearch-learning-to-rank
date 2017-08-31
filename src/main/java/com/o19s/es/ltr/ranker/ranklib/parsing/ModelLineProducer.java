/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.ranker.ranklib.parsing;

import com.o19s.es.ltr.ranker.ranklib.learning.RankLibError;

/**
 * Created by doug on 5/24/17.
 */
public class ModelLineProducer {

    private StringBuilder model = new StringBuilder();

    public interface LineConsumer {
        void nextLine(StringBuilder model, boolean maybeEndEns);
    }

    public StringBuilder getModel() {
        return model;
    }

    private boolean readUntil(char[] fullTextChar, int beginOfLineCursor, int endOfLineCursor, StringBuilder model) {

        // read line in, scan for probable Ensemble

        boolean ens = true;

        if (fullTextChar[beginOfLineCursor] != '#') {
            for (int j = beginOfLineCursor; j <= endOfLineCursor; j++) {
                model.append(fullTextChar[j]);
            }
        }

        // dumb quick hack to see if the reader should check for ensemble tag
        if (endOfLineCursor > 3) {
            ens = (   fullTextChar[endOfLineCursor - 9] == '/' &&
                      fullTextChar[endOfLineCursor - 2] == 'l' &&
                      fullTextChar[endOfLineCursor - 1] == 'e' &&
                      fullTextChar[endOfLineCursor]     == '>');
        }
        return ens;
    }


    public void parse(String fullText, LineConsumer modelConsumer) {


        int CARRIAGE_RETURN = 13;
        int LINE_FEED = 10;

        try {
            String content = "";
            // StringBuilder model = new StringBuilder();

            char[] fullTextChar = fullText.toCharArray();

            int beginOfLineCursor = 0;
            for (int i = 0; i < fullTextChar.length; i++) {
                int charNum = fullTextChar[i];
                if (charNum == CARRIAGE_RETURN || charNum == LINE_FEED) {

                    // NEWLINE, read beginOfLineCursor -> i
                    if (fullTextChar[beginOfLineCursor] != '#') {
                        int eolCursor = i;
                        while (eolCursor > beginOfLineCursor && fullTextChar[eolCursor] <= 32) {
                            eolCursor--;
                        }

                        boolean ens = readUntil(fullTextChar, beginOfLineCursor, eolCursor, model);

                        modelConsumer.nextLine(model, ens);
                    }

                    // readahead this new line up to the next space
                    while (charNum <= 32 & i < fullTextChar.length) {
                        charNum = fullTextChar[i];
                        beginOfLineCursor = i;
                        i++;
                    }
                }
            }


            boolean ens = readUntil(fullTextChar, beginOfLineCursor, fullTextChar.length - 1, model);


            modelConsumer.nextLine(model, ens);


        }
        catch(Exception ex)
        {
            throw RankLibError.create("Error in model loading ", ex);
        }
    }
}
