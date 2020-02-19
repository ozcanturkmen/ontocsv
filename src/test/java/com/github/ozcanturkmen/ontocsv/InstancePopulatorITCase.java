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

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.joining;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

/**
 * Integration testing for {@link InstancePopulator}
 */
public class InstancePopulatorITCase {
    @Test
    public void testProcess(){
        Optional<InstancePopulator> populator = InstancePopulator.create("src", "test", "resources");
        Assert.assertTrue(populator.isPresent());
        populator.ifPresent(p -> {
            int instancesCreated = p.process();
            // should create 6 new instances according to test input files
            Assert.assertEquals(instancesCreated,6);

            try(
                Stream<String> generatedOntologyFile = Files.lines(Paths.get("generated.owl"));
                Stream<String> skippedRecordsFile = Files.lines(Paths.get("skipped.txt"))
            ) {
                // should produce a generated.owl file in current directory
                long exists = generatedOntologyFile.limit(1).collect(counting()).longValue();
                Assert.assertTrue(exists == 1L);

                // should produce a skipped.txt file in current directory, containing a single line
                String lineRead = skippedRecordsFile.limit(1).flatMap(l -> Arrays.stream(l.split(","))).collect(joining(" "));
                Assert.assertEquals(lineRead, "This line will be skipped");

            } catch (Exception e){
                e.printStackTrace();
            }
            
        });
        
    }
}