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

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class OracleUtils {
	public interface Oracle {
		public String execute(String query);
	}
	
	public interface DiscriminativeOracle{
		// public FileWriter fileWriter = new FileWriter("current_input.txt",false)
		// public void tempFile() throws Exception;
		public abstract boolean query(String query) throws IOException;
	}
	
	public static interface Wrapper {
		public abstract String wrap(String input);
	}
	
	public static class IdentityWrapper implements Wrapper {
		@Override
		public String wrap(String input) {
			return input;
		}
	}
	
	public static class WrappedOracle implements Oracle {
		private final Oracle oracle;
		private final Wrapper wrapper;
		
		public WrappedOracle(Oracle oracle, Wrapper wrapper) {
			this.oracle = oracle;
			this.wrapper = wrapper;
		}
		
		@Override
		public String execute(String query) {
			return this.oracle.execute(this.wrapper.wrap(query));
		}
	}
	
    public static class WrappedDiscriminativeOracle implements DiscriminativeOracle {
        private final DiscriminativeOracle oracle;
        private final Wrapper wrapper;

        public WrappedDiscriminativeOracle(DiscriminativeOracle oracle, Wrapper wrapper) {
                this.oracle = oracle;
                this.wrapper = wrapper;
        }

        @Override
        public boolean query(String query) throws IOException {
                return this.oracle.query(this.wrapper.wrap(query));
        }
    }
}
