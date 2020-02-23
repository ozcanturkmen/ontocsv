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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.JenaException;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDFS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InstancePopulator class provides the methods to populate an OWL ontology (in RDF/XML format)
 * with instances provided via a CSV (comma separated values) file.
 * <p>
 * Class and instance names are expected to be provided as separate csv files.
 * <p>
 * The class makes use of the Apache JENA library to create an in-memory ontology model.
 * It creates the instances for the given classes as {@link OWL2#NamedIndividual}s within the ontology.
 * 
 * @author      Özcan Türkmen
 * @version     1.0
 */
public class InstancePopulator {

    private enum FileType { OWL, CSV, OTHER }

    private static final Logger logger = LoggerFactory.getLogger(InstancePopulator.class);

    /**
     * Static inner class used by Jackson {@link ObjectMapper} 
     * during deserialization of the key-value pairs 
     * found within the transformations configuration file (YAML).
     * <p>
     * When configured, those transformation rules are applied
     * in the exact order they are defined, 
     * to each instance name parsed from the input csv file.
     * <p>
     * Configurable instance name transformations consist of following String manipulation methods:
     * 
     * <ul>
     * <li>Trim</li>
     * <li>Uppercase</li>
     * <li>Lowercase</li>
     * <li>ReplaceAll</li>
     * </ul>
     * 
     * @since 1.0
     */
    public static class Configurator {

        private boolean trim;
        private String casing;
        private List<Configurator.Transformation> transformations;
    
        boolean getTrim() {
            return trim;
        }
    
        void setTrim(boolean trim) {
            this.trim = trim;
        }
    
        String getCasing() {
            return casing;
        }
    
        void setCasing(String casing) {
            this.casing = casing;
        }
        
        List<Configurator.Transformation> getTransformations() {
            return transformations;
        }
    
        void setTransformations(List<Configurator.Transformation> transformations) {
            this.transformations = transformations;
        }

        /**
         * Static inner class encapsulating the {@code pattern} and {@code replacement} parameters 
         * for a transformation consisting of a {@link String#replaceAll(String, String)} operation. 
         * 
         * @since 1.0
         */
        public static class Transformation {

            private String pattern;
            private String replacement;

            String getPattern() {
                return pattern;
            }

            void setPattern(String pattern) {
                this.pattern = pattern;
            }

            String getReplacement() {
                return replacement;
            }

            void setReplacement(String replacement) {
                this.replacement = replacement;
            }
        }
    }

    /**
     * Static inner class which is used to create {@code InstancePopulator} instances 
     * in compliance with the Builder design pattern.
     * 
     * @since 1.0
     */
    public static class Builder {

        public static final OntModelSpec RDFS_MEM = OntModelSpec.RDFS_MEM;
        public static final OntModelSpec OWL_LITE_MEM = OntModelSpec.OWL_LITE_MEM;
        public static final OntModelSpec OWL_DL_MEM = OntModelSpec.OWL_DL_MEM;
        public static final OntModelSpec OWL_MEM = OntModelSpec.OWL_MEM;
        
        private Optional<Configurator> transformationConfigurator;
        private Path ontologyFilePath;
        private Path csvClassesFilePath;
        private Path csvInstancesFilePath;
        private boolean owl2Correction;
        private OntModelSpec ontModelSpec;
        private OntModel model;
        private String nsPrefixURI;

        public Builder() {
            this.ontModelSpec = OWL_DL_MEM;
            this.transformationConfigurator = Optional.empty();
        }

        /**
         * Checks whether input csv files are provided within the specified path.
         * 
         * @return boolean, flag indicating whether input files are provided or not
         */
        private boolean pathOK(){
            return ontologyFilePath != null && csvClassesFilePath != null && csvInstancesFilePath != null;
        };
        
        /**
         * Sets a flag that optionally triggers the OWL2 individuals preprocessing 
         * of existing OWL2 individuals within the original ontology.
         * <p>
         * Preprocessing consists of a correction operation 
         * which first removes the existing OWL2 individuals (if any) 
         * from the in-memory model created from the source ontology,
         * and then create and add them again to the model as {@link OWL2#NamedIndividual}s.
         * <p>
         * This option is provided by {@code InstancePopulator} library
         * because {@code Jena} {@code OntModel} does not have proper OWL2 support.  
         * 
         * @return {@code Builder}, for method chaining
         */
        public Builder withOWL2Correction() {
            this.owl2Correction = true;
            return this;
        }

        /**
         * Sets {@link OntModelSpec} for {@code Jena} {@link OntModel}.
         * 
         * Acceptable Jena ontology model specifications: 
         * 
         * <ul>
         * <li>RDFS_MEM</li>
         * <li>OWL_LITE_MEM</li>
         * <li>OWL_DL_MEM</li>
         * <li>OWL_MEM</li>
         * </ul>
         * 
         * @param spec {@link OntModelSpec}, specification for Jena ontology model
         * 
         * @return  {@code Builder}, for method chaining
         */
        public Builder withSpec(OntModelSpec spec) {
            this.ontModelSpec = spec;
            return this;
        }

        /**
         * Configures transformation rules that will be applied to each parsed instance names by this {@code InstancePopulator}.
         * Rules are configured via a YAML settings file.
         * 
         * @param configuratorYaml string, otpional YAML file that defines transformation rules for this {@code InstancePopulator}
         * 
         * @return {@code Builder}, for method chainings
         */
        public Builder withConfigurator(String configuratorYaml){
            try(
                Reader cfg = new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(configuratorYaml))
            ){
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                this.transformationConfigurator = Optional.ofNullable(mapper.readValue(cfg, Configurator.class));
                return this;
            } catch (JsonMappingException jmpex){
                throw new IllegalArgumentException("Invalid configuration while mapping in " + configuratorYaml + ": " + jmpex.getMessage());
            } catch (JsonProcessingException jpex){
                throw new IllegalArgumentException("Invalid configuration while parsing in " + configuratorYaml + ": " + jpex.getMessage());
            } catch (IOException ioex){
                throw new IllegalArgumentException("Cannot read YAML configuration file " + configuratorYaml + ": " + ioex.getMessage());
            } catch (Exception e){
                throw new IllegalArgumentException("Inexistent or inaccessible YAML configuration file " + configuratorYaml);
            }
        }

        /**
         * Specifies the path of directory that contains the required input files 
         * (The original ontology file, the class names csv input file, and the instance names csv input file)
         * 
         * @param path              string, path of directory to find input .owl and .csv files
         * @param otherPathParts    string, optional parts of the directory path to find the input files
         * 
         * @return {@code Builder}, for method chainings
         */
        public Builder withPath(String path, String... otherPathParts) {
            try ( Stream<Path> files = Files.list(Paths.get(path, otherPathParts)).filter(Files::isRegularFile) ) {
                Map<InstancePopulator.FileType, List<Path>> fileMap = 
                    files.collect(
                        groupingBy(p -> {
                            if (p.toString().toLowerCase().endsWith(".owl")) return FileType.OWL;
                            else if (p.toString().toLowerCase().endsWith(".csv")) return FileType.CSV;
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

                this.ontologyFilePath = fileMap.get(FileType.OWL).get(0);
                this.csvClassesFilePath = fileMap.get(FileType.CSV).get(0);
                this.csvInstancesFilePath = fileMap.get(FileType.CSV).get(1);
                return this;

            } catch (IOException e) {
                logger.error(e.getMessage());
                throw new IllegalArgumentException("Inexistent or inaccessible directory");
            }
        }

        /**
         * Builds an {@code InstancePopulator} instance 
         * using defaults and/or provided arguments.
         * 
         * @return {@code InstancePopulator}, instance
         */
        public InstancePopulator build() {
            if (!this.pathOK()) this.withPath("");
            OntModel ontologyModel = ModelFactory.createOntologyModel(this.ontModelSpec, null);
            InputStream in = FileManager.get().open(this.ontologyFilePath.toString());
            if (in == null) {
                throw new IllegalArgumentException("Cannot read ontology file " + this.ontologyFilePath.toString());
            }
            try{
                ontologyModel.read(in, null);
            } catch (JenaException e){
                throw new IllegalArgumentException("Malformed ontology file " + this.ontologyFilePath.toString() + e.getMessage());
            }
            logger.info("Ontology " + this.ontologyFilePath.toString() + " loaded into memory");
            this.model = ontologyModel;
            this.nsPrefixURI = ontologyModel.getNsPrefixURI("");
            return new InstancePopulator(this);
        }
    }
    
    private final Path csvClassesFilePath;
    private final Path csvInstancesFilePath;
    private final boolean owl2Correction;
    private final String nsPrefixURI;
    private final OntModel model;
    private final Optional<Configurator> transformationConfigurator;
    private final Function<String, String> initialTransformationFunc; 
    private final boolean shouldRemoveAnchors;

    // Predifined Functions that will be applied in chains, regarding Configurator transformation rules
    private static Function<String, String> identityFunction = Function.identity();
    private static Function<String, String> trimFunction = s -> s.trim();
    private static Function<String, String> lowerCaseFunction = s -> s.toLowerCase();
    private static Function<String, String> upperCaseFunction = s -> s.toUpperCase();
    private static BiFunction<String, Configurator.Transformation, String> replacer = (s, t) -> s.replaceAll(t.pattern, t.replacement);

    /**
     * Private constructor accessed through {@link Builder#build()} static factory method. 
     * 
     * @param   builder   {@code Builder} to get the instantiation parameters from 
     */
    private InstancePopulator(Builder builder) {
        this.csvClassesFilePath = builder.csvClassesFilePath;
        this.csvInstancesFilePath = builder.csvInstancesFilePath;
        this.model = builder.model;
        this.nsPrefixURI = builder.nsPrefixURI;
        this.owl2Correction = builder.owl2Correction;
        this.transformationConfigurator = builder.transformationConfigurator;
        this.initialTransformationFunc = getInitialTransformationFunction();
        this.shouldRemoveAnchors = this.nsPrefixURI != null && this.nsPrefixURI.endsWith("#") ? true : false;
    }

    /**
     * Constructs the initial transformation function 
     * as a chain of some predefined String functions (such as trim, upperCase, and lowerCase)
     * that will be applied to each parsed instance name
     * 
     * @return {@link Function}, nitial transformation function that takes a {@code String} and returns another {@code String} 
     */
    Function<String,String> getInitialTransformationFunction() {

        Function<String, String> func = InstancePopulator.identityFunction;

        if (!this.transformationConfigurator.isPresent()) return func;

        List<Function<String, String>> functions = new ArrayList<>();

        if (this.transformationConfigurator.get().getTrim()){
            functions.add(InstancePopulator.trimFunction);
        }

        if (this.transformationConfigurator.get().getCasing().equalsIgnoreCase("upper")){
            functions.add(InstancePopulator.upperCaseFunction);
        } else if (this.transformationConfigurator.get().getCasing().equalsIgnoreCase("lower")){
            functions.add(InstancePopulator.lowerCaseFunction);
        }

        for (Function<String,String> f: functions){
            // Chain functions
            func = func.andThen(f);
        }

        return func;
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
     * Helps proper handling of existing OWL2 individuals
     * if any is present by the time the original ontology is loaded into memory.
     * <p>
     * Provides a simple workaround for {@code Jena} {@code OntModel}s insufficient OWL2 support.
     * @see <a href="https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/ontology/OntModel.html">Jena OntModel</a>
     */
    void correctOWL2() {
        
        Map<String, List<String>> foundInstances = new HashMap<>();

            // Remove existing instances from model, but collect them in the map
            this.model
                .listIndividuals()
                .forEachRemaining(indv -> {
                    String className = indv.getOntClass().getLocalName();
                    if (!foundInstances.containsKey(className)) foundInstances.put(className, new ArrayList<String>());
                    foundInstances.get(className).add(indv.getLocalName());
                    this.model.removeAll(indv.asResource(), null, null);
                    //this.model.removeAll(indv.asResource(), OWL2.NamedIndividual, indv.getLocalName());
                });
                
            // Add instances within the map to the model, using OWL2.NamedIndividual RDF type
            for (String key : foundInstances.keySet()){
                foundInstances.get(key).forEach(p -> {
                    Individual ind = this.model.getOntClass(this.nsPrefixURI + key).createIndividual(this.nsPrefixURI + p);
                    ind.addRDFType(OWL2.NamedIndividual);
                    this.model.add(this.model.createStatement(ind, RDFS.label, p)); 
                });
            }
    }

    /**
     * Applies transformation rules to parsed instance names 
     * in exact order they are defined by {@link Configurator}.
     * <p>
     * This method has no effect if no {@link Configurator} 
     * has been configured with this {@code InstancePopulator}.
     * 
     * @param instance  String, parsed instance name to transform and further process
     * 
     * @return          String, transformed instance name
     */
    String applyTransformations(String instance) {

        String transformed = instance;

        // If nsPrefixURI ends with a '#' character, all '#' characters should be removed from instance names (if any)
        if (this.shouldRemoveAnchors){
            transformed = transformed.replaceAll("#+", "");
        }
       
        // Do nothing further if there is no transformationConfigurator (short-circuit statement)
        if (!this.transformationConfigurator.isPresent()) return transformed;

        transformed = this.initialTransformationFunc.apply(transformed);

        for (Configurator.Transformation transformation: this.transformationConfigurator.get().getTransformations()){
            // apply functions in chain
            transformed = InstancePopulator.replacer.apply(transformed, transformation);
        }

        return transformed;

    }

    /**
     * Processes csv files, creates individuals from parsed csv records, and adds them to the ontology.
     * <p>
     * In order to not override the original ontology file, 
     * it produces a new ontology file ({@code generated.owl}) 
     * which will contain the newly introduced instances.
     * <p>
     * Accumulates the skipped csv lines in a {@code skipped.txt} file, 
     * and reports the number of created instances.
     * 
     * @return int  Number of created individuals  
     */
    public int process() {

            try (
                // Input files
                Stream<String> classesStream = 
                    Files.lines(this.csvClassesFilePath, Charset.forName("UTF-8"));
                Stream<String> instancesStream = 
                    Files.readAllLines(this.csvInstancesFilePath, Charset.forName("UTF-8")).parallelStream();
                // Output files
                PrintWriter skippedRecordsWriter = 
                    new PrintWriter(Paths.get("skipped.txt").toFile(), "UTF-8");
                FileWriter ontologyFileWriter = 
                    new FileWriter(Paths.get("generated.owl").toFile())
            ) {
                // If owl2Correction is true, remove existing instances from the model in memory (if any), 
                // and re-create them using RDF type OWL2.NamedIndividual

                if (this.owl2Correction) this.correctOWL2();

                // Read class names to a List<String>
                List<String> classes = 
                classesStream
                    .filter(line -> line.trim().length() > 0)
                    .limit(1)
                    .flatMap(line -> Arrays.stream(line.split(",")))
                    .collect(toList());

                // Add an individual to the model for each parsed instance within the instances csv file

                // Initialize a map as a container for RDF statements
                Map<String, List<Statement>> instances = new HashMap<>();
                
                instancesStream
                    .map(line -> line.split(","))
                    .forEach(line -> {
                        // Skip line if number of instances is greater than the number of classes
                        if (line.length > classes.size()) {
                            // Report that line in skipped.txt
                            skippedRecordsWriter.println(String.join(",", line));
                            return;
                        }
                        // Create statement from instance name
                        for (int index = 0; index < line.length; index++) {
                            String className = classes.get(index).trim();
                            String instanceName = line[index];
                            // Skip if parsed instance name is empty
                            if (instanceName.trim().length() == 0) continue;
                            String normalizedInstanceName = this.applyTransformations(instanceName);
                            // Skip if transformed instance name is empty
                            if (normalizedInstanceName.trim().length() == 0) continue;
                            if (!instances.containsKey(className)) instances.put(className, new ArrayList<Statement>());
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

}