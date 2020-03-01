// Copyright 2015-2016 Stanford University
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package glade.util;

import java.util.*;

public class CharacterUtils {

    public static class CharacterGeneralization {
		public final Set<Character> triggers;
		public final List<Character> characters;
		public final List<Character> checks;
		public CharacterGeneralization(Collection<Character> triggers, Collection<Character> characters, Collection<Character> checks) {
			this.triggers = new HashSet<>(triggers);
			this.characters = new ArrayList<>(characters);
			this.checks = new ArrayList<>(checks);
		}
	}

    private static InputAlphabet inputAlphabet;
    private static List<CharacterGeneralization> generalizations;
    private static boolean isInitialized = false;

    // TODO add here logic for ASCII from old GLADE implementation
    public static void init(InputAlphabet inputAlphabet) {
        if (isInitialized) {
            throw new IllegalStateException("\"CharacterUtils\" are already initialized.");
        }
        isInitialized = true;

        CharacterUtils.inputAlphabet = inputAlphabet;

        List<CharacterGeneralization> generalizations = new ArrayList<>();
        List<Character> allCharacters = new ArrayList<>();

        for (char c = 0; c < getNumberOfCharacters(); c++) {
            allCharacters.add(c);
        }
        for (char c : allCharacters) {
            List<Character> curC = Utils.getList(c);
            generalizations.add(new CharacterGeneralization(allCharacters, curC, curC));
        }
        CharacterUtils.generalizations = Collections.unmodifiableList(generalizations);
    }

	public static List<CharacterGeneralization> getGeneralizations() {
        checkIfInitialized();
		return generalizations;
	}

    public static int getNumberOfCharacters() {
        checkIfInitialized();
        return inputAlphabet.numberOfCharacters;
    }

    public static String queryToAnsiString(String input) {
        checkIfInitialized();
        StringBuilder sb = new StringBuilder();
        for (char ch: input.toCharArray()) {
            sb.append(queryCharToAnsiString(ch));
        }
        return sb.toString();
    }

    public static String queryCharToAnsiString(char ch) {
        checkIfInitialized();
        switch (inputAlphabet) {
            case ASCII:
                if (Character.isISOControl(ch)) {
                    return String.format("@|magenta \\x%02x|@", (int) ch);
                }
                return "@|yellow " + ch + "|@";
            case BYTE:
                return String.format("@|yellow %02x|@", (int) ch);
            default:
                throw new IllegalStateException("This method doesn't support used input alphabet.");
        }
    }

    private static void checkIfInitialized() {
        if (!isInitialized) {
            throw new IllegalStateException("\"CharacterUtils\" are not initialized.");
        }
    }

    public static InputAlphabet getInputAlphabet() {
        return inputAlphabet;
    }

    public enum InputAlphabet {
        ASCII(128), BYTE(256);
        public final int numberOfCharacters;

        InputAlphabet(int numberOfCharacters) {
            this.numberOfCharacters = numberOfCharacters;
        }
    }
}
