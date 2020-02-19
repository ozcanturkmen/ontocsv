/**
 * This file is part of the OntoCSV software.
 * Copyright (c) 2020, Özcan Türkmen.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.github.ozcanturkmen.ontocsv;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.util.FileManager;
import org.apache.jena.ontology.Individual;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InstancePopulator class provides the methods to populate an OWL ontology (in RDF/XML format)
 * with instances provided via CSV (comma separated values) files.
 * <p>
 * Class and instance names are expected to be provided as separate csv files.
 * <p>
 * The class makes use of the Apache JENA library to create an in-memory ontology model
 * using {@link OntModelSpec#OWL_DL_MEM} specification.
 * It creates the instances for the given classes as named individuals within the ontology.
 * 
 * @author      Özcan Türkmen
 * @version     1.0
 */
public class InstancePopulator {

    private enum FileType { OWL, CSV, OTHER }

    private static final Logger logger = LoggerFactory.getLogger(InstancePopulator.class);

    private final Path ontologyFilePath;
    private final Path csvClassesFilePath;
    private final Path csvInstancesFilePath;
    private final String nsPrefixURI;
    private final OntModel model;

    /**
     * Private constructor accessed through {@link #create} static factory method. 
     * 
     * @param   fileMap   {@link Map} of {@link List} of file {@link Path}s mapped using {@link FileType} keys
     * @see     {@link FileType} 
     * @see     {@link #create}
     * @see     {@link Path}
     */
    private InstancePopulator(Map<FileType, List<Path>> fileMap) {
        this.ontologyFilePath = fileMap.get(FileType.OWL).get(0);
        this.csvClassesFilePath = fileMap.get(FileType.CSV).get(0);
        this.csvInstancesFilePath = fileMap.get(FileType.CSV).get(1);
        this.model = this.getOntology();
        this.nsPrefixURI = this.model.getNsPrefixURI("");
    }

    /**
     * Creates an {@link OntModel.OWL_DL_MEM} ontology model, 
     * and reads the ontology specified by {@code #ontologyFilePath} field into that model.
     * 
     * @return      {@link OntModel} created ontology model
     * @throws      {@code IllegalArgumentException} if the ontology file cannot be read
     * @see         {@link ModelFactory#createOntologyModel}
     */
    private OntModel getOntology() {
        OntModel ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, null);
        InputStream in = FileManager.get().open(this.ontologyFilePath.toString());
        if (in == null) {
            throw new IllegalArgumentException("Cannot read ontology file " + this.ontologyFilePath.toString());
        }
        ontologyModel.read(in, null);
        logger.info("Ontology " + this.ontologyFilePath.toString() + " loaded into memory");
        return ontologyModel;
    }

    /**
     * Creates a custom {@link Collector} which uses a sorted {@code List} of type {@code T} as supplier.
     * Sort logic is imposed by the {@code c} parameter which denotes a {@link Comparator} expression. 
     * 
     * @param <T>   type of {@linkplain Collection} to be used as the sorted container for the {@link Collector}
     * @param c     {@link Comparator}   comparator to be used when sorting {@linkplain List<T>}
     * @return      {@link Collector} of {@code T}
     */
    private static <T> Collector<T, ?, List<T>> toSortedList(Comparator<? super T> c) {
        return collectingAndThen(
            toCollection(ArrayList::new), l -> { l.sort(c); return l; }
        );
    }

    /**
     * Handles lines that contains double quotes.
     * Trims the line, removes double quotes, and replaces the first comma with a tilda (~) if it was placed within double quotes.
     * 
     * @param   line    a single line read from input csv file
     * @return          normalized line
     */
    protected static String evaluateLine(String line) {
        if (line == null || line.isEmpty()) return "";
        if (!line.contains("\"")) return line.trim();
        StringBuilder sb = new StringBuilder(line.trim());
        int count = 0, fromIndex = 0, prevIndex = -2;
        while ((fromIndex = line.indexOf("\"", fromIndex)) != -1) {
            if (count % 2 == 0) prevIndex = fromIndex; 
            else {
                int indexOfComma = line.indexOf(",", prevIndex);
                if ((indexOfComma >= 0) && indexOfComma < fromIndex) sb.setCharAt(indexOfComma, '~');
            }
            fromIndex++;
            count++;
        }
        return sb.toString().replace("\"", "");
    }

    /**
     * Normalizes instance name by evaluating common punctuation marks.
     * Spaces are replaced with underscores, the string gets trimmed and converted to lowercase letters.
     * 
     * @param   instanceName    name of an ontology class instance read from a csv input file
     * @return                  normalized instance name
     */
    protected static String getNormalizedInstanceName(String instanceName) {
        if (instanceName == null || instanceName.isEmpty()) return "";
        return instanceName
            .trim()
            .toLowerCase()
            // Remove double quotes and right parentheses
            .replaceAll("[\")]", "")
            // Replace slashes, dashes, dots, commas, equal signs, left parantheses, single quotes with underscores
            .replaceAll("(\\s)*(/|-|\\.|\\,|'|\\(|=)+(\\s)*", "_")
            // Replace ampersands with _and_
            .replaceAll("(\\s)*&+(\\s)*", "_and_")
            // Replace spaces with underscores
            .replaceAll("(\\s)+", "_");
    }
    
    /**
     * Factory method to create an {@code InstancePopulator} instance 
     * using .owl and .csv file assets found in current working directory.
     * 
     * @return                  {@link Optional} of {@link InstancePopulator}
     * @see                     #create(String path, String... otherPathParts)
     */
    public static Optional<InstancePopulator> create() {
        // Expects to find the assets in current working directory
        return create("");
    }

    /**
     * Factory method with path parameter to create an {@code InstancePopulator} instance.
     * <p>
     * Provide the path of directory using {@code path} and {@code otherPathParts} parameters.
     * Method inspects that directory to automatically identify the ontology file (must be an .owl file),
     * along with two csv files, one meant for class names, and the other for instances. 
     * <p>
     * Among the csv files, the smallest in size will be assumed as the input file for class names.
     * 
     * @param   path            string, path to ontology and csv input files
     * @param   otherPathParts  string, remaining parts of the path (if any) as varargs
     * @return                  {@link Optional} of {@link InstancePopulator}
     * @see                     Optional
     */
    public static Optional<InstancePopulator> create(String path, String... otherPathParts) {
        try (Stream<Path> filesInCurrentDirectory = 
            Files.list(Paths.get(path, otherPathParts)).filter(Files::isRegularFile)
        ) {
            Map<InstancePopulator.FileType, List<Path>> fileMap = 
                filesInCurrentDirectory
                    .collect(
                        groupingBy(p -> {
                            if (p.toString().endsWith(".owl")) return FileType.OWL;
                            else if (p.toString().endsWith(".csv")) return FileType.CSV;
                            else return FileType.OTHER;
                        }, 
                        toSortedList(Comparator.comparing(p -> p.toFile().length())))
                    );

            if (!fileMap.containsKey(FileType.OWL) || fileMap.get(FileType.OWL).isEmpty()) {
                throw new IllegalArgumentException(
                    "OWL ontology file not found in specified path (current directry if none specified)");
            }

            if (!fileMap.containsKey(FileType.CSV) || fileMap.get(FileType.CSV).isEmpty()) {
                throw new IllegalArgumentException(
                    "CSV classes and instances files not found in specified path (current directry if none specified)");

            } else if (fileMap.get(FileType.CSV).size() < 2) {
                throw new IllegalArgumentException(
                    "Specified path must contain both class names and instance names csv files");
            }
            
            return Optional.of(new InstancePopulator(fileMap));

        } catch (IOException e) {
            logger.error(e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Creates individuals, and adds them to the ontology.
     * <p>
     * In order to not override the original ontology file, 
     * it produces a new ontology file ({@code generated.owl}) 
     * which will contain the newly introduced instances.
     * <p>
     * Accumulates the skipped csv lines in a {@code skipped.txt} file, 
     * and reports the number of processes instances.
     * <p>
     * Between the instantiation of the InstancePopulator 
     * and the invocation of this {@code process} method,
     * there should be a {@link Optional#ifPresent} check
     * to make sure that the instantiation was successful.
     * 
     * @return int  Number of created individuals  
     */
    public int process() {
        try (
            PrintWriter skippedRecordsWriter = 
                new PrintWriter(Paths.get(".","skipped.txt").toFile(), "UTF-8");
            FileWriter ontologyFileWriter = 
                new FileWriter(Paths.get(".","generated.owl").toFile());
            Stream<String> classesStream = 
                Files.lines(this.csvClassesFilePath, Charset.forName("UTF-8"));
            Stream<String> instancesStream = 
                Files.readAllLines(this.csvInstancesFilePath, Charset.forName("UTF-8")).parallelStream()
        ) {
            // Read class names
            List<String> classes = 
                classesStream
                    .filter(line -> line.trim().length() > 0)
                    .limit(1)
                    .flatMap(line -> Arrays.stream(evaluateLine(line).split(",")))
                    .collect(toList());

            // Initialize a map as a container for RDF statements
            Map<String, List<Statement>> instances = new HashMap<>();
                
            instancesStream
                .map(line -> evaluateLine(line).split(","))
                .forEach(line -> {
                    // Skip line if number of instances is greater than the number of classes
                    if (line.length > classes.size()) {
                        // Report that line in skipped.txt
                        skippedRecordsWriter.println(String.join(",", line));
                        return;
                    }
                    // Create statement from instance name
                    for (int index = 0; index < line.length; index++) {
                        String className = classes.get(index).replace('~', ',');
                        String instanceName = line[index].trim().replace('~', ',');
                        String normalizedInstanceName = getNormalizedInstanceName(instanceName);
                        if (!instances.containsKey(className)) instances.put(className, new ArrayList<Statement>());
                        if (instanceName.length() == 0) continue;
                        // Create class instance (individual)
                        OntClass classToAdd = this.model.getOntClass(this.nsPrefixURI + className);
                        if (classToAdd == null) continue;
                        Individual individual = classToAdd.createIndividual(this.nsPrefixURI + normalizedInstanceName);
                        individual.addRDFType(OWL2.NamedIndividual);
                        // Add RDF label
                        Statement stmt = model.createStatement(individual, RDFS.label,instanceName);
                        instances.get(className).add(stmt);
                    }
                });

            int instanceCount = 0;

            for (String key : instances.keySet()) {
                // Bulk add all instances to the model
                List<Statement> instancesToBeCreated = instances.get(key);
                this.model.add(instancesToBeCreated);
                instanceCount += instancesToBeCreated.size();
            }

            // Persist new individuals
            this.model.write(ontologyFileWriter);

            // Report processing result
            logger.info(instanceCount + " new individuals added to ontology");

            return instanceCount;

        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * A demo runner that demonstrates the usage 
     * in case the input .owl and .csv files 
     * are put in the current working directory.
     * 
     * @param   args    optional execution parameters (that actually are NOT evaluated)
     */
    public static void main(String... args) {
        Optional<InstancePopulator> populator = InstancePopulator.create();
        populator.ifPresent(p -> p.process());
    }

}