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

import java.util.Objects;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link Builder#build()}
 */
public class InstancePopulatorTest {

    @Test
    public void testBuild() {
        // No Configurator, no OWL2 correction, default spec
        Assert.assertTrue(
            Objects.hashCode(
                new InstancePopulator.Builder()
                    .withPath("src","test","resources")
                    .build()
            ) > 0);

        // With Configurator, no OWL2 correction, default spec
        Assert.assertTrue(
            Objects.hashCode(
                new InstancePopulator.Builder()
                    .withConfigurator("config.yml")
                    .withPath("src","test","resources")
                    .build()
            ) > 0);

        // With Configurator, with OWL2 correction, default spec
        Assert.assertTrue(
            Objects.hashCode(
                new InstancePopulator.Builder()
                    .withConfigurator("config.yml")
                    .withOWL2Correction()
                    .withPath("src","test","resources")
                    .build()
            ) > 0);

        // With Configurator, with OWL2 correction, with custom spec (OWL_DL_MEM)
        Assert.assertTrue(
            Objects.hashCode(
                new InstancePopulator.Builder()
                    .withConfigurator("config.yml")
                    .withOWL2Correction()
                    .withSpec(InstancePopulator.Builder.OWL_DL_MEM)
                    .withPath("src","test","resources")
                    .build()
            ) > 0);

        // No Configurator, with OWL2 correction, with custom spec (OWL_DL_MEM)
        Assert.assertTrue(
            Objects.hashCode(
                new InstancePopulator.Builder()
                    .withOWL2Correction()
                    .withSpec(InstancePopulator.Builder.OWL_DL_MEM)
                    .withPath("src","test","resources")
                    .build()
            ) > 0);
    }

    /**
     * Unit tests for {@link Builder#build()} that should throw various IllegalArgumentExceptions
     */
    @Test
    public void testBuildExceptions() {

        // When path is inexistent or inaccessible
        try{
            new InstancePopulator.Builder()
                    .withPath("src","test","resources", "does_not_exist")
                    .build();
        } catch (Exception e){
            Assert.assertTrue(e instanceof IllegalArgumentException);
            Assert.assertTrue(e.getMessage().startsWith("Inexistent or inaccessible directory"));
        }
        
        // When no ontology file is present in the path
        try{
            new InstancePopulator.Builder()
                    .withPath("src","test","resources", "directoryMissingOntologyFile")
                    .build();
        } catch (Exception e){
            Assert.assertTrue(e instanceof IllegalArgumentException);
            Assert.assertTrue(e.getMessage().startsWith("OWL ontology file not found"));
        }

        // When no csv file is present in the path
        try{
            new InstancePopulator.Builder()
                    .withPath("src","test","resources", "directoryMissingCSVFiles")
                    .build();
        } catch (Exception e){
            Assert.assertTrue(e instanceof IllegalArgumentException);
            Assert.assertTrue(e.getMessage().startsWith("CSV classes and instances files not found"));
        }

        // When classes or instances csv file is missing
        try{
            new InstancePopulator.Builder()
                .withPath("src","test","resources", "directoryMissingSomeCSVFile")
                .build();
        } catch (Exception e){
            Assert.assertTrue(e instanceof IllegalArgumentException);
            Assert.assertEquals(e.getMessage(), "Specified path must contain both class names and instance names csv files");
        }

       
        // When configuration file cannot be found
        try{
            new InstancePopulator.Builder()
                .withConfigurator("doesnotexist.yml")
                .withPath("src","test","resources")
                .build();
        } catch (Exception e){
            Assert.assertTrue(e instanceof IllegalArgumentException);
            Assert.assertTrue(e.getMessage().startsWith("Inexistent or inaccessible YAML"));
        }
         
        // When configuration is invalid (mapping error)
        try{
            new InstancePopulator.Builder()
                .withConfigurator("invalid.yml")
                .withPath("src","test","resources")
                .build();
        } catch (Exception e){
            Assert.assertTrue(e instanceof IllegalArgumentException);
            Assert.assertTrue(e.getMessage().startsWith("Invalid configuration while mapping"));
        }

        // When configuration is invalid (parsing error)
        try{
            new InstancePopulator.Builder()
                .withConfigurator("invalid2.yml")
                .withPath("src","test","resources")
                .build();
        } catch (Exception e){
            Assert.assertTrue(e instanceof IllegalArgumentException);
            Assert.assertTrue(e.getMessage().startsWith("Invalid configuration while parsing"));
        }

        // When owl file is malformed
        try{
            new InstancePopulator.Builder()
                .withPath("src","test","resources","invalidOWL")
                .build();
        } catch (Exception e){
            Assert.assertTrue(e instanceof IllegalArgumentException);
            Assert.assertTrue(e.getMessage().startsWith("Malformed ontology file"));
        }
    }

    /**
     * Unit tests for: 
     * {@link InstancePopulator#getInitialTransformationFunction()}
     * {@link InstancePopulator#applyTransformations(String instance)}
     */
    @Test
    public void testTransformations(){
        InstancePopulator populator = new InstancePopulator.Builder()
            .withConfigurator("config.yml")
            .withPath("src","test","resources")
            .build();

        
        /**
         * Testing {@link InstancePopulator#getInitialTransformationFunction()}
         * Should trim() and toUpperCase() 
         */
        Function<String,String> f = populator.getInitialTransformationFunction();
        Assert.assertEquals(f.apply("   ~öZcAn~ "),"~ÖZCAN~");

        /**
         * Testing {@link InstancePopulator#applyTransformations(String instance)}
         * 1. should trim() and toUpperCase() 
         * 2. should remove hashes because shouldRemoveAnchors = true
         * 3. should remove parantheses and double quotes
         */
        Assert.assertEquals(populator.applyTransformations("   )))#!\"öZcAn\"~#((( "),"!ÖZCAN~");
    }
}
