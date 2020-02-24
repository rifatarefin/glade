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
    private static CharacterUtils instance;

    private CharacterUtils() {}

    public static CharacterUtils getInstance() {
        if (instance == null) {
            instance = new CharacterUtils();
        }
        return instance;
    }

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

    private Integer numberOfCharacters;
	private List<CharacterGeneralization> generalizations;

    public void init(int numberOfCharacters) {
        this.numberOfCharacters = numberOfCharacters;

        List<CharacterGeneralization> generalizations = new ArrayList<>();
        List<Character> allCharacters = new ArrayList<>();

        for (char c = 0; c < numberOfCharacters; c++) {
            allCharacters.add(c);
        }
        for (char c : allCharacters) {
            List<Character> curC = Utils.getList(c);
            generalizations.add(new CharacterGeneralization(allCharacters, curC, curC));
        }
        this.generalizations = Collections.unmodifiableList(generalizations);
    }

	public List<CharacterGeneralization> getGeneralizations() {
		return generalizations;
	}

    public int getNumberOfCharacters() {
        return numberOfCharacters;
    }
}
