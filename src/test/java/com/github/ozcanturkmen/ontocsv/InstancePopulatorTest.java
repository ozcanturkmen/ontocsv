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

import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link InstancePopulator}
 * <p>
 * Created by @ot on 19.02.2020
 */
public class InstancePopulatorTest {

    @Test
    public void testCreate() {
        Optional<InstancePopulator> populator = InstancePopulator.create("wrong_path");
        Assert.assertTrue(!populator.isPresent());
        populator = InstancePopulator.create("src", "test", "resources");
        Assert.assertTrue(populator.isPresent());
    }

    @Test
    public void testCreateExceptions() {

        // When no ontology file is present in the path
        try{
            InstancePopulator.create("src", "test", "resources","directoryMissingOntologyFile");
        } catch (Exception e){
            Assert.assertTrue(e.getMessage().startsWith("OWL ontology file not found"));
        }

        // When no csv file is present in the path
        try{
            InstancePopulator.create("src", "test", "resources","directoryMissingCSVFiles");
        } catch (Exception e){
            Assert.assertTrue(e.getMessage().startsWith("CSV classes and instances files not found"));
        }

        // When either the classes or the instances csv file is not present in the path
        try{
            InstancePopulator.create("src", "test", "resources","directoryMissingSomeCSVFile");
        } catch (Exception e){
            Assert.assertEquals(e.getMessage(), "Specified path must contain both class names and instance names csv files");
        }
    }

    @Test
    public void testEvaluateLine() {
        // Should return empty string if line == null or line.isEmpty 
        Assert.assertEquals(InstancePopulator.evaluateLine(null),"");
        Assert.assertEquals(InstancePopulator.evaluateLine(""),"");
        Assert.assertEquals(InstancePopulator.evaluateLine("     "),"");
        // Should return trimmed line if the line does not contain any double quotes
        Assert.assertEquals(InstancePopulator.evaluateLine(" a line     "),"a line");
        // Should remove double quotes, and replace the first comma with a tilda (~) if it was placed within double quotes
        Assert.assertEquals(InstancePopulator.evaluateLine("\"\"\""),"");
        Assert.assertEquals(InstancePopulator.evaluateLine("\"a\",b"),"a,b");
        Assert.assertEquals(InstancePopulator.evaluateLine("\"a\",\",b"),"a,,b");
        Assert.assertEquals(InstancePopulator.evaluateLine("\"a\",\",\""),"a,~");
        Assert.assertEquals(InstancePopulator.evaluateLine("a, and \"b\" c"),"a, and b c");
        Assert.assertEquals(InstancePopulator.evaluateLine("\"a,b c,d\""),"a~b c,d");
        Assert.assertEquals(InstancePopulator.evaluateLine("\"1\" \"2,3\" \"4,5\" \"6,7,8,9\""),"1 2~3 4~5 6~7,8,9");
    }

    @Test
    public void testGetNormalizedInstanceName() {
        // Should return empty string if parameter is null or empty 
        Assert.assertEquals(InstancePopulator.getNormalizedInstanceName(null),"");
        Assert.assertEquals(InstancePopulator.getNormalizedInstanceName(""),"");
        Assert.assertEquals(InstancePopulator.getNormalizedInstanceName("     "),"");
        // Should remove double quotes and right parentheses
        Assert.assertEquals(InstancePopulator.getNormalizedInstanceName("))\"\"))A))\"\"Bb))\""),"abb");
        // Should replace all slashes, dashes, dots, commas, equal signs, 
        // left parantheses, single quotes, and spaces with underscores
        Assert.assertEquals(InstancePopulator.getNormalizedInstanceName("====a.b,c/d-e=f(g)'h ' i"),"_a_b_c_d_e_f_g_h_i");
        // Should replace all ampersands with _and_
        Assert.assertEquals(InstancePopulator.getNormalizedInstanceName("A&B    &c"),"a_and_b_and_c");
    }
}
