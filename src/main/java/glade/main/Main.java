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

package glade.main;


import glade.grammar.fuzz.GrammarFuzzer.GrammarMutationSampler;
import glade.grammar.fuzz.GrammarFuzzer.SampleParameters;
import glade.grammar.GrammarUtils.Grammar;
import glade.grammar.synthesize.GrammarSynthesis;
import glade.util.CharacterUtils;
import glade.util.Log;
import glade.util.OracleUtils.DiscriminativeOracle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Help.Ansi;


@Command(name = "glade", mixinStandardHelpOptions = true, version = "1.0",
    subcommands = {Learn.class, Fuzz.class, Print.class})
public class Main implements Callable<Integer> {

    @Option(names = {"--log"}, defaultValue = "INFO", description = "logging level")
    private Log.Level level;

    @Override
    public Integer call() {
        initGlade();
        Log.debug("Starting command glade");
        System.err.println("Please invoke a subcommand");
        CommandLine cmd = new CommandLine(this);
        cmd.usage(System.err);
        return cmd.getCommandSpec().exitCodeOnInvalidInput();
    }

    public void initGlade() {
        Log.init(System.out, level);
    }

    public static void main(String ... args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}

@Command(name = "learn", description = "Learn grammar")
class Learn implements Callable<Integer> {

    @ParentCommand
    private Main parent;

    @Parameters(description = {"Each {} in command will be substituted with query.",
        "Whenever {} is not in command, the query is sent to standard input."})
    private String command;

    @Option(names = {"-o", "--output"}, description = "output, grammar file")
    private String outputFile;

    @Option(names = {"-i", "--input"}, defaultValue = "inputs", description = "folder with seed inputs")
    private Path inputFolder;

    @Option(names = {"-l", "--length"}, description = "max length of an input")
    private Integer maxLength;

    @Option(names = {"-f", "--fixed_length"}, description = "fixed length of an input")
    private Integer fixedLength;

    @Option(names = {"-c", "--characters"}, defaultValue = "128", description = "number of characters in input alphabet")
    private int numberOfCharacters;

    @Override
    public Integer call() {
        Log.debug("Starting subcommand learn");
        parent.initGlade();
        CharacterUtils.getInstance().init(numberOfCharacters);
        List<String> seedInputs = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(inputFolder)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    Log.debug("Reading seed input from " + p);
                    String seed = new String(Files.readAllBytes(p));

                    StringBuilder sb = new StringBuilder();
                    seed.chars().forEach(c -> sb.append(String.format("%02x", c)));
                    Log.info("Adding new seed input: " + seed + " (hex: " + sb.toString() + ")");
                    seedInputs.add(seed);
                } catch (IOException e) {
                    throw new IllegalStateException("Error when reading seed input from file: " + p, e);
                }
            });
        } catch (IOException e) {
            System.err.println("Error when reading from input folder: " + inputFolder);
            return new CommandLine(this).getCommandSpec().exitCodeOnInvalidInput();
        }
        if (seedInputs.isEmpty()) {
            System.err.println("Error: input folder is empty");
            return new CommandLine(this).getCommandSpec().exitCodeOnInvalidInput();
        }
        Log.debug("Creating oracle");
        DiscriminativeOracle oracle = new Oracle(command, maxLength, fixedLength);
        Log.info("Learning grammar");
        Grammar grammar = GrammarSynthesis.getGrammarMultiple(seedInputs, oracle);
        if(outputFile == null) {
            outputFile = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm").format(LocalDateTime.now()) + ".gram";
        }
        Log.info("Saving grammar to " + outputFile);
        GrammarDataUtils.saveGrammar(outputFile, grammar);
        return 0;
    }
}

@CommandLine.Command(name = "fuzz", description = "Fuzz using grammar")
class Fuzz implements Callable<Integer> {

    @ParentCommand
    private Main parent;

    @Parameters
    private String command;

    @Option(names = {"-i", "--input"}, required = true, description = "input grammar")
    private String input;

    @Option(names = {"-c", "--count"}, defaultValue = "15", description = "number of generated inputs")
    private int count;

    @Option(names = {"-l", "--length"}, defaultValue = "256", description = "max length of an input")
    private Integer maxLength;

    @Option(names = {"-f", "--fixed_length"}, description = "fixed length of an input")
    private Integer fixedLength;

    @Option(names = {"-s", "--seed"}, defaultValue = "0", description = "seed for random number generator")
    private int seed;

    @Option(names = {"-m", "--mutations"}, defaultValue = "40", description = "number of mutations to seed input")
    private int numMut;

    @Option(names = {"-d", "--distribution"}, split = "," ,defaultValue = "0.2,0.2,0.2,0.4",
        description = "multinomial distribution of repetitions")
    private double[] distribution;

    @Option(names = {"-r", "--recursion"}, defaultValue = "0.2", description = "probability of using recursive production")
    private double recursionProbability;

    @Option(names = {"-h", "--hex"}, description = "print in hexadecimal")
    private boolean isHex;

    @Override
    public Integer call() {
        Log.debug("Starting subcommand fuzz");
        parent.initGlade();

        Log.debug("Creating oracle");
        DiscriminativeOracle oracle = new Oracle(command, maxLength, fixedLength);

        Log.info("Loading grammar from " + input);
        Grammar grammar = GrammarDataUtils.loadGrammar(input);

        Log.debug("Creating samples");
        Iterable<String> samples = new GrammarMutationSampler(grammar,
            new SampleParameters(distribution, recursionProbability, 1, 200),
            maxLength, numMut, new Random(seed));

        int pass = 0;
        int processed = 0;
        for(String sample : samples) {
            System.out.print("Input: ");
            if (isHex) {
                sample.chars().forEach(c -> System.out.printf("%02x", c));
            } else {
                System.out.print(sample);
            }
            System.out.println();
            if(oracle.query(sample)) {
                System.out.println(Ansi.AUTO.string("@|green pass|@"));
                pass++;
            } else {
                System.out.println(Ansi.AUTO.string("@|red fail|@"));
            }
            System.out.println();

            processed++;
            if(processed >= count) {
                break;
            }
        }
        System.out.println("Pass rate: " + (float) pass / count);
        return 0;
    }
}

@Command(name = "print", description = "Print grammar")
class Print implements Callable<Integer> {

    @ParentCommand
    private Main parent;

    @Parameters
    private String grammar;

    @Option(names = {"-h", "--hex"}, description = "print in hexadecimal")
    private boolean isHex;

    @Option(names = {"-c", "--characters"}, defaultValue = "128", description = "number of characters in input alphabet")
    private int numberOfCharacters;

    @Override
    public Integer call() {
        Log.debug("Starting subcommand print");
        parent.initGlade();
        CharacterUtils.getInstance().init(numberOfCharacters);
        Grammar grammar = GrammarDataUtils.loadGrammar(this.grammar);
        System.out.println(Ansi.AUTO.string(grammar.node.toPrettyString(isHex)));
        return 0;
    }
}

class Oracle implements DiscriminativeOracle {
    private String command;
    private Integer maxLength;
    private Integer fixedLength;

    Oracle(String command, Integer maxLength, Integer fixedLength) {
        this.command = command;
        this.maxLength = maxLength;
        this.fixedLength = fixedLength;
    }

    public boolean query(String query) {
        if (maxLength != null && query.length() > maxLength) {
            return false;
        }
        if (fixedLength != null && query.length() != fixedLength) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        query.chars().forEach(c -> sb.append(String.format("%02x", c)));
        Log.debug("Oracle input: " + query + " (hex: " + sb.toString() + ")");
        try {
            Process p;
            if (command.contains("{}")) {
                if (query.contains("\0")) { // Command arguments can't contain null bytes.
                    Log.debug("Oracle failed because the query contains a null byte.");
                    return false;
                }
                p = Runtime.getRuntime().exec(command.replace("{}", query));
            } else {
                p = Runtime.getRuntime().exec(command);
                for (char c : query.toCharArray()) {
                    p.getOutputStream().write(c);
                }
                p.getOutputStream().close();
            }

            Log.debug("Oracle output: " + new BufferedReader(new InputStreamReader(p.getInputStream()))
                .lines().collect(Collectors.joining("\n")));

            p.waitFor();

            Log.debug("Oracle exit value: " + p.exitValue());
            return p.exitValue() == 0;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
